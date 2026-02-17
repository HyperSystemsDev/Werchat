package com.werchat.channels;

import com.google.gson.*;
import com.werchat.WerchatPlugin;
import com.werchat.storage.PlayerDataManager;

import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages all chat channels
 */
public class ChannelManager {

    private static final long SAVE_DEBOUNCE_SECONDS = 20;

    private final WerchatPlugin plugin;
    private final Map<String, Channel> channels;
    private final Gson gson;
    private final ScheduledExecutorService saveExecutor;
    private final Object saveStateLock = new Object();
    private ScheduledFuture<?> pendingSaveTask;
    private boolean suppressDirtyNotifications;
    private Channel defaultChannel;

    public ChannelManager(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.channels = new HashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor();
        this.suppressDirtyNotifications = false;
    }

    public void loadChannels() {
        suppressDirtyNotifications = true;

        Path dataDir = plugin.getDataDirectory();
        Path channelsFile = dataDir.resolve("channels.json");
        Path membersFile = dataDir.resolve("channel-members.json");

        try {
            Files.createDirectories(dataDir);

            if (Files.exists(channelsFile)) {
                String json = Files.readString(channelsFile);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                boolean hasEmbeddedMembers = false;

                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    Channel ch = deserializeChannel(obj);
                    if (ch != null) {
                        // Backward compat: migrate embedded member data from old format
                        if (obj.has("members") || obj.has("moderators") || obj.has("banned") || obj.has("muted")) {
                            hasEmbeddedMembers = true;
                            loadEmbeddedMembers(ch, obj);
                        }
                        registerChannel(ch);
                        if (ch.isDefault()) defaultChannel = ch;
                    }
                }

                // Load members from separate file (if it exists)
                if (Files.exists(membersFile)) {
                    loadMembers(dataDir);
                } else if (hasEmbeddedMembers) {
                    // First run after migration: save to split the files
                    plugin.getLogger().at(Level.INFO).log("Migrating member data to channel-members.json");
                }

                plugin.getLogger().at(Level.INFO).log("Loaded %d channels from file", channels.size());
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to load channels: %s", e.getMessage());
        }

        // Create defaults if none loaded
        if (channels.isEmpty()) {
            createDefaultChannels();
        }

        // Ensure we have a default
        if (defaultChannel == null && !channels.isEmpty()) {
            defaultChannel = channels.values().iterator().next();
            defaultChannel.setDefault(true);
        }

        // Honor configured default channel name when present
        String configuredDefaultName = plugin.getConfig().getDefaultChannelName();
        if (configuredDefaultName != null && !configuredDefaultName.isBlank()) {
            Channel configuredDefault = getChannel(configuredDefaultName);
            if (configuredDefault != null) {
                setDefaultChannel(configuredDefault);
            } else {
                plugin.getLogger().at(Level.WARNING).log(
                    "Configured default channel '%s' was not found; using '%s'",
                    configuredDefaultName,
                    defaultChannel != null ? defaultChannel.getName() : "none"
                );
            }
        }

        suppressDirtyNotifications = false;

        // Always save to ensure both files exist and old format gets migrated
        saveChannels();
    }

    private void loadEmbeddedMembers(Channel ch, JsonObject obj) {
        if (obj.has("owner") && !obj.get("owner").isJsonNull()) {
            ch.setOwner(UUID.fromString(obj.get("owner").getAsString()));
        }
        if (obj.has("moderators")) {
            for (JsonElement el : obj.getAsJsonArray("moderators")) {
                ch.addModerator(UUID.fromString(el.getAsString()));
            }
        }
        if (obj.has("members")) {
            for (JsonElement el : obj.getAsJsonArray("members")) {
                ch.addMember(UUID.fromString(el.getAsString()));
            }
        }
        if (obj.has("banned")) {
            for (JsonElement el : obj.getAsJsonArray("banned")) {
                ch.ban(UUID.fromString(el.getAsString()));
            }
        }
        if (obj.has("muted")) {
            for (JsonElement el : obj.getAsJsonArray("muted")) {
                ch.mute(UUID.fromString(el.getAsString()));
            }
        }
    }

    private void createDefaultChannels() {
        Channel global = new Channel("Global");
        global.setNick("Global");
        global.setColor(Color.WHITE);
        global.setFormat("{nick} {sender}: {msg}");
        global.setDistance(0);
        global.setDefault(true);
        global.setAutoJoin(true);
        global.setQuickChatSymbol("!");
        registerChannel(global);
        defaultChannel = global;

        Channel local = new Channel("Local");
        local.setNick("Local");
        local.setColor(Color.GRAY);
        local.setFormat("{nick} {sender}: {msg}");
        local.setDistance(100);
        local.setAutoJoin(true);
        registerChannel(local);

        Channel trade = new Channel("Trade");
        trade.setNick("Trade");
        trade.setColor(new Color(255, 215, 0));
        trade.setFormat("{nick} {sender}: {msg}");
        trade.setAutoJoin(true);
        trade.setQuickChatSymbol("~");
        registerChannel(trade);

        Channel support = new Channel("Support");
        support.setNick("Support");
        support.setColor(Color.GREEN);
        support.setFormat("{nick} {sender}: {msg}");
        support.setAutoJoin(true);
        registerChannel(support);
    }

    public void markDirty() {
        if (suppressDirtyNotifications) {
            return;
        }

        synchronized (saveStateLock) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
            }
            pendingSaveTask = saveExecutor.schedule(this::flushDebouncedSave, SAVE_DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void flushDebouncedSave() {
        synchronized (saveStateLock) {
            pendingSaveTask = null;
        }

        saveChannels();
    }

    public void shutdownDebouncedSaver() {
        synchronized (saveStateLock) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }
        }

        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void saveChannels() {
        try {
            Path dataDir = plugin.getDataDirectory();
            Files.createDirectories(dataDir);

            // Save channel settings
            Path channelsFile = dataDir.resolve("channels.json");
            JsonArray arr = new JsonArray();
            for (Channel ch : channels.values()) {
                arr.add(serializeChannel(ch));
            }
            Files.writeString(channelsFile, gson.toJson(arr));

            // Save member data separately
            saveMembers(dataDir);

            plugin.getLogger().at(Level.INFO).log("Saved %d channels", channels.size());
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to save channels: %s", e.getMessage());
        }
    }

    private void saveMembers(Path dataDir) throws IOException {
        Path membersFile = dataDir.resolve("channel-members.json");
        JsonObject root = new JsonObject();

        for (Channel ch : channels.values()) {
            JsonObject chData = new JsonObject();
            chData.add("owner", serializeUuidWithName(ch.getOwner()));
            chData.add("moderators", serializeUuidSetWithNames(ch.getModerators()));
            chData.add("members", serializeUuidSetWithNames(ch.getMembers()));
            chData.add("banned", serializeUuidSetWithNames(ch.getBanned()));
            chData.add("muted", serializeUuidSetWithNames(ch.getMuted()));
            root.add(ch.getName(), chData);
        }

        Files.writeString(membersFile, gson.toJson(root));
    }

    private JsonObject serializeUuidSetWithNames(Set<UUID> uuids) {
        JsonObject obj = new JsonObject();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        for (UUID id : uuids) {
            String name = pdm != null ? pdm.getKnownName(id) : "";
            obj.addProperty(id.toString(), name);
        }
        return obj;
    }

    private JsonElement serializeUuidWithName(UUID uuid) {
        if (uuid == null) return JsonNull.INSTANCE;
        JsonObject obj = new JsonObject();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        String name = pdm != null ? pdm.getKnownName(uuid) : "";
        obj.addProperty(uuid.toString(), name);
        return obj;
    }

    private void loadMembers(Path dataDir) {
        Path membersFile = dataDir.resolve("channel-members.json");
        if (!Files.exists(membersFile)) return;

        try {
            String json = Files.readString(membersFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                Channel ch = getChannel(entry.getKey());
                if (ch == null) continue;

                JsonObject chData = entry.getValue().getAsJsonObject();

                if (chData.has("owner") && !chData.get("owner").isJsonNull()) {
                    JsonObject ownerObj = chData.getAsJsonObject("owner");
                    for (String key : ownerObj.keySet()) {
                        ch.setOwner(UUID.fromString(key));
                    }
                }

                if (chData.has("moderators")) {
                    for (String key : chData.getAsJsonObject("moderators").keySet()) {
                        ch.addModerator(UUID.fromString(key));
                    }
                }

                if (chData.has("members")) {
                    for (String key : chData.getAsJsonObject("members").keySet()) {
                        ch.addMember(UUID.fromString(key));
                    }
                }

                if (chData.has("banned")) {
                    for (String key : chData.getAsJsonObject("banned").keySet()) {
                        ch.ban(UUID.fromString(key));
                    }
                }

                if (chData.has("muted")) {
                    for (String key : chData.getAsJsonObject("muted").keySet()) {
                        ch.mute(UUID.fromString(key));
                    }
                }
            }

            plugin.getLogger().at(Level.INFO).log("Loaded channel members from channel-members.json");
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to load channel members: %s", e.getMessage());
        }
    }

    private JsonObject serializeChannel(Channel ch) {
        JsonObject obj = new JsonObject();

        // Identity
        obj.addProperty("name", ch.getName());
        obj.addProperty("nick", ch.getNick());

        // Appearance
        obj.addProperty("color", ch.getColorHex());
        obj.addProperty("messageColor", ch.hasMessageColor() ? ch.getMessageColorHex() : "");
        obj.addProperty("format", ch.getFormat());

        // Behavior
        obj.addProperty("distance", ch.getDistance());
        JsonArray worldsArr = new JsonArray();
        for (String w : ch.getWorlds()) worldsArr.add(w);
        obj.add("worlds", worldsArr);
        obj.addProperty("password", ch.getPassword());
        obj.addProperty("quickChatSymbol", ch.hasQuickChatSymbol() ? ch.getQuickChatSymbol() : "");
        obj.addProperty("quickChatEnabled", ch.isQuickChatEnabled());
        obj.addProperty("isDefault", ch.isDefault());
        obj.addProperty("autoJoin", ch.isAutoJoin());
        obj.addProperty("focusable", ch.isFocusable());
        obj.addProperty("verbose", ch.isVerbose());

        return obj;
    }

    private Channel deserializeChannel(JsonObject obj) {
        try {
            String name = obj.get("name").getAsString();
            Channel ch = new Channel(name);
            ch.setNick(obj.get("nick").getAsString());

            String hex = obj.get("color").getAsString().replace("#", "");
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            ch.setColor(new Color(r, g, b));

            ch.setFormat(obj.get("format").getAsString());
            ch.setDistance(obj.get("distance").getAsInt());
            if (obj.has("password") && !obj.get("password").isJsonNull()) {
                ch.setPassword(obj.get("password").getAsString());
            }
            ch.setDefault(obj.get("isDefault").getAsBoolean());
            ch.setAutoJoin(obj.get("autoJoin").getAsBoolean());
            if (obj.has("focusable")) {
                ch.setFocusable(obj.get("focusable").getAsBoolean());
            }
            if (obj.has("verbose")) {
                ch.setVerbose(obj.get("verbose").getAsBoolean());
            }

            if (obj.has("messageColor") && !obj.get("messageColor").isJsonNull()) {
                String msgColorStr = obj.get("messageColor").getAsString();
                if (!msgColorStr.isEmpty()) {
                    String msgHex = msgColorStr.replace("#", "");
                    int mr = Integer.parseInt(msgHex.substring(0, 2), 16);
                    int mg = Integer.parseInt(msgHex.substring(2, 4), 16);
                    int mb = Integer.parseInt(msgHex.substring(4, 6), 16);
                    ch.setMessageColor(new Color(mr, mg, mb));
                }
            }

            if (obj.has("quickChatSymbol") && !obj.get("quickChatSymbol").isJsonNull()) {
                String qcs = obj.get("quickChatSymbol").getAsString();
                if (!qcs.isEmpty()) {
                    ch.setQuickChatSymbol(qcs);
                }
            }

            if (obj.has("quickChatEnabled")) {
                ch.setQuickChatEnabled(obj.get("quickChatEnabled").getAsBoolean());
            } else {
                // Backward compat: enable if channel already has a symbol
                ch.setQuickChatEnabled(ch.hasQuickChatSymbol());
            }

            if (obj.has("worlds") && obj.get("worlds").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("worlds")) {
                    ch.addWorld(el.getAsString());
                }
            } else if (obj.has("world") && !obj.get("world").isJsonNull()) {
                // Backward compat: migrate single world string to set
                String worldStr = obj.get("world").getAsString();
                if (!worldStr.isEmpty()) {
                    ch.addWorld(worldStr);
                }
            }

            return ch;
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to load channel: %s", e.getMessage());
            return null;
        }
    }

    public boolean registerChannel(Channel channel) {
        if (channels.containsKey(channel.getName().toLowerCase())) return false;

        channel.setChangeListener(this::markDirty);
        channels.put(channel.getName().toLowerCase(), channel);
        markDirty();
        return true;
    }

    public boolean unregisterChannel(String name) {
        Channel channel = channels.remove(name.toLowerCase());
        if (channel != null) {
            channel.setChangeListener(null);
        }
        if (channel != null && channel == defaultChannel) {
            defaultChannel = channels.values().stream()
                    .filter(Channel::isDefault).findFirst()
                    .orElse(channels.values().stream().findFirst().orElse(null));
        }
        if (channel != null) {
            markDirty();
        }
        return channel != null;
    }

    public Channel getChannel(String name) {
        Channel channel = channels.get(name.toLowerCase());
        if (channel != null) return channel;
        for (Channel ch : channels.values()) {
            if (ch.getNick().equalsIgnoreCase(name)) return ch;
        }
        return null;
    }

    /**
     * Find channel by name, nick, or partial match (prefix)
     */
    public Channel findChannel(String input) {
        if (input == null || input.isEmpty()) return null;
        String lower = input.toLowerCase();

        // Exact name match
        Channel exact = channels.get(lower);
        if (exact != null) return exact;

        // Exact nick match
        for (Channel ch : channels.values()) {
            if (ch.getNick().equalsIgnoreCase(input)) return ch;
        }

        // Prefix match on name
        for (Channel ch : channels.values()) {
            if (ch.getName().toLowerCase().startsWith(lower)) return ch;
        }

        // Prefix match on nick
        for (Channel ch : channels.values()) {
            if (ch.getNick().toLowerCase().startsWith(lower)) return ch;
        }

        return null;
    }

    /**
     * Find a channel whose quickChatSymbol matches the start of the message.
     * Returns null if no match found.
     */
    public Channel findChannelByQuickChatSymbol(String message) {
        if (message == null || message.isEmpty()) return null;
        for (Channel ch : channels.values()) {
            if (ch.hasQuickChatSymbol() && message.startsWith(ch.getQuickChatSymbol())) {
                return ch;
            }
        }
        return null;
    }

    public Channel getDefaultChannel() { return defaultChannel; }
    public void setDefaultChannel(Channel channel) {
        if (defaultChannel != null) defaultChannel.setDefault(false);
        channel.setDefault(true);
        defaultChannel = channel;
        markDirty();
    }

    public Collection<Channel> getAllChannels() { return Collections.unmodifiableCollection(channels.values()); }
    public int getChannelCount() { return channels.size(); }
    public boolean channelExists(String name) { return getChannel(name) != null; }

    public Channel createChannel(String name, UUID creator) {
        if (channelExists(name)) return null;
        Channel channel = new Channel(name);
        channel.setOwner(creator);
        channel.addModerator(creator);
        registerChannel(channel);
        return channel;
    }

    public boolean renameChannel(String oldName, String newName) {
        Channel channel = getChannel(oldName);
        if (channel == null) return false;
        if (channelExists(newName)) return false;

        channels.remove(oldName.toLowerCase());
        channel.setName(newName);
        channels.put(newName.toLowerCase(), channel);
        markDirty();
        return true;
    }

    public boolean deleteChannel(String name) {
        Channel channel = getChannel(name);
        if (channel == null || channels.size() <= 1) return false;
        return unregisterChannel(name);
    }

    public List<Channel> getPlayerChannels(UUID playerId) {
        List<Channel> playerChannels = new ArrayList<>();
        for (Channel channel : channels.values()) {
            if (channel.isMember(playerId)) playerChannels.add(channel);
        }
        return playerChannels;
    }
}
