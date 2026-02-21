package com.werchat.channels;

import com.google.gson.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.integration.papi.PAPIIntegration;
import com.werchat.storage.PlayerDataManager;

import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.util.logging.Level;

/**
 * Manages all chat channels
 */
public class ChannelManager {

    private static final long SAVE_DEBOUNCE_SECONDS = 20;
    private static final String DEFAULT_CHANNEL_FORMAT = "[{nick}] {sender}: {msg}";

    private final WerchatPlugin plugin;
    private final Map<String, Channel> channels;
    private final Gson gson;
    private final ScheduledExecutorService saveExecutor;
    private final Set<UUID> motdShownThisLogin;
    private final PAPIIntegration papi;
    private final Object saveStateLock = new Object();
    private ScheduledFuture<?> pendingSaveTask;
    private boolean suppressDirtyNotifications;
    private volatile String lastKnownChannelsSignature = "";
    private volatile String lastKnownMembersSignature = "";
    private Channel defaultChannel;

    private static final class ChannelSaveSnapshot {
        private final String name;
        private final String nick;
        private final String colorHex;
        private final String messageColorHex;
        private final String format;
        private final int distance;
        private final Set<String> worlds;
        private final String password;
        private final String quickChatSymbol;
        private final boolean quickChatEnabled;
        private final boolean isDefault;
        private final boolean autoJoin;
        private final String description;
        private final boolean descriptionEnabled;
        private final String motd;
        private final boolean motdEnabled;
        private final UUID owner;
        private final Set<UUID> moderators;
        private final Set<UUID> members;
        private final Set<UUID> banned;
        private final Set<UUID> muted;

        private ChannelSaveSnapshot(Channel channel) {
            this.name = channel.getName();
            this.nick = channel.getNick();
            this.colorHex = channel.getColorHex();
            this.messageColorHex = channel.hasMessageColor() ? channel.getMessageColorHex() : "";
            this.format = channel.getFormat();
            this.distance = channel.getDistance();
            this.worlds = channel.getWorlds();
            this.password = channel.getPassword();
            this.quickChatSymbol = channel.hasQuickChatSymbol() ? channel.getQuickChatSymbol() : "";
            this.quickChatEnabled = channel.isQuickChatEnabled();
            this.isDefault = channel.isDefault();
            this.autoJoin = channel.isAutoJoin();
            this.description = channel.getDescription();
            this.descriptionEnabled = channel.isDescriptionEnabled();
            this.motd = channel.getMotd();
            this.motdEnabled = channel.isMotdEnabled();
            this.owner = channel.getOwner();
            this.moderators = channel.getModerators();
            this.members = channel.getMembers();
            this.banned = channel.getBanned();
            this.muted = channel.getMuted();
        }
    }

    public ChannelManager(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.channels = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor();
        this.motdShownThisLogin = Collections.synchronizedSet(new HashSet<>());
        this.papi = PAPIIntegration.register(plugin);
        this.suppressDirtyNotifications = false;
    }

    public boolean loadChannels() {
        suppressDirtyNotifications = true;

        Map<String, Channel> previousChannels = new HashMap<>(channels);
        Channel previousDefault = defaultChannel;

        // Clear in-memory state so reloads reflect disk exactly.
        for (Channel existing : channels.values()) {
            existing.setChangeListener(null);
        }
        channels.clear();
        defaultChannel = null;

        Path dataDir = plugin.getDataDirectory();
        Path channelsFile = dataDir.resolve("channels.json");
        Path membersFile = dataDir.resolve("channel-members.json");
        boolean hadChannelsFile = false;
        boolean loadFailed = false;

        try {
            Files.createDirectories(dataDir);
            hadChannelsFile = Files.exists(channelsFile);

            if (hadChannelsFile) {
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
            loadFailed = true;
            plugin.getLogger().at(Level.WARNING).log("Failed to load channels: %s", e.getMessage());
        }

        if (loadFailed && !previousChannels.isEmpty()) {
            channels.clear();
            for (Channel previous : previousChannels.values()) {
                previous.setChangeListener(this::markDirty);
                channels.put(previous.getName().toLowerCase(), previous);
            }
            defaultChannel = previousDefault;
            suppressDirtyNotifications = false;
            plugin.getLogger().at(Level.WARNING).log("Keeping previous in-memory channel data after load failure");
            return false;
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

        if (normalizeAutoGeneratedNicks()) {
            plugin.getLogger().at(Level.INFO).log("Normalized default channel nick collisions");
        }

        suppressDirtyNotifications = false;

        // Save when load succeeded, or when creating first-run defaults.
        if (!loadFailed || !hadChannelsFile) {
            saveChannels();
        } else {
            plugin.getLogger().at(Level.WARNING).log("Skipped channel save because channels.json failed to parse");
        }
        return !loadFailed;
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
        global.setFormat(DEFAULT_CHANNEL_FORMAT);
        global.setDistance(0);
        global.setDefault(true);
        global.setAutoJoin(true);
        global.setQuickChatSymbol("!");
        global.setDescription("Server-wide chat visible to everyone.");
        global.setDescriptionEnabled(true);
        registerChannel(global);
        defaultChannel = global;

        Channel local = new Channel("Local");
        local.setNick("Local");
        local.setColor(Color.GRAY);
        local.setFormat(DEFAULT_CHANNEL_FORMAT);
        local.setDistance(100);
        local.setAutoJoin(true);
        local.setDescription("Nearby chat based on distance.");
        local.setDescriptionEnabled(true);
        registerChannel(local);

        Channel trade = new Channel("Trade");
        trade.setNick("Trade");
        trade.setColor(new Color(255, 215, 0));
        trade.setFormat(DEFAULT_CHANNEL_FORMAT);
        trade.setAutoJoin(true);
        trade.setQuickChatSymbol("~");
        trade.setDescription("Buying and selling channel.");
        trade.setDescriptionEnabled(true);
        registerChannel(trade);

        Channel support = new Channel("Support");
        support.setNick("Support");
        support.setColor(Color.GREEN);
        support.setFormat(DEFAULT_CHANNEL_FORMAT);
        support.setAutoJoin(true);
        support.setDescription("Help and support channel.");
        support.setDescriptionEnabled(true);
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

    /**
     * Cancels any queued debounced save without writing current in-memory state to disk.
     * Used by reload flows where disk should remain source-of-truth.
     */
    public void discardPendingSave() {
        synchronized (saveStateLock) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }
        }
    }

    /**
     * Returns true when channel data files changed on disk since Werchat's last load/save snapshot.
     */
    public boolean hasExternalChannelDataEdits() {
        Path dataDir = plugin.getDataDirectory();
        String currentChannelsSig = computeFileSignature(dataDir.resolve("channels.json"));
        String currentMembersSig = computeFileSignature(dataDir.resolve("channel-members.json"));
        synchronized (saveStateLock) {
            if (lastKnownChannelsSignature.isEmpty() && lastKnownMembersSignature.isEmpty()) {
                // Baseline not initialized yet; initialize now and treat as no external edits.
                lastKnownChannelsSignature = currentChannelsSig;
                lastKnownMembersSignature = currentMembersSig;
                return false;
            }
            return !Objects.equals(lastKnownChannelsSignature, currentChannelsSig)
                || !Objects.equals(lastKnownMembersSignature, currentMembersSig);
        }
    }

    public void flushPendingSaveNow() {
        synchronized (saveStateLock) {
            if (pendingSaveTask != null) {
                pendingSaveTask.cancel(false);
                pendingSaveTask = null;
            }
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

            List<ChannelSaveSnapshot> snapshots = snapshotChannelsForSave();

            // Save channel settings
            Path channelsFile = dataDir.resolve("channels.json");
            JsonArray arr = new JsonArray();
            for (ChannelSaveSnapshot snapshot : snapshots) {
                arr.add(serializeChannel(snapshot));
            }
            Files.writeString(channelsFile, gson.toJson(arr));

            // Save member data separately
            saveMembers(dataDir, snapshots);
            refreshKnownFileSignatures(dataDir);

            plugin.getLogger().at(Level.INFO).log("Saved %d channels", snapshots.size());
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to save channels: %s", e.getMessage());
        }
    }

    private List<ChannelSaveSnapshot> snapshotChannelsForSave() {
        List<ChannelSaveSnapshot> snapshots = new ArrayList<>();
        for (Channel channel : channels.values()) {
            snapshots.add(new ChannelSaveSnapshot(channel));
        }
        snapshots.sort(Comparator.comparing(snapshot -> snapshot.name.toLowerCase(Locale.ROOT)));
        return snapshots;
    }

    private void saveMembers(Path dataDir, List<ChannelSaveSnapshot> snapshots) throws IOException {
        Path membersFile = dataDir.resolve("channel-members.json");
        JsonObject root = new JsonObject();

        for (ChannelSaveSnapshot snapshot : snapshots) {
            JsonObject chData = new JsonObject();
            chData.add("owner", serializeUuidWithName(snapshot.owner));
            chData.add("moderators", serializeUuidSetWithNames(snapshot.moderators));
            chData.add("members", serializeUuidSetWithNames(snapshot.members));
            chData.add("banned", serializeUuidSetWithNames(snapshot.banned));
            chData.add("muted", serializeUuidSetWithNames(snapshot.muted));
            root.add(snapshot.name, chData);
        }

        Files.writeString(membersFile, gson.toJson(root));
    }

    private JsonObject serializeUuidSetWithNames(Set<UUID> uuids) {
        JsonObject obj = new JsonObject();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        List<UUID> sorted = new ArrayList<>(uuids);
        sorted.sort(Comparator.comparing(UUID::toString));
        for (UUID id : sorted) {
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

    private JsonObject serializeChannel(ChannelSaveSnapshot snapshot) {
        JsonObject obj = new JsonObject();

        // Identity
        obj.addProperty("name", snapshot.name);
        obj.addProperty("nick", snapshot.nick);

        // Appearance
        obj.addProperty("color", snapshot.colorHex);
        obj.addProperty("messageColor", snapshot.messageColorHex);
        obj.addProperty("format", snapshot.format);

        // Behavior
        obj.addProperty("distance", snapshot.distance);
        JsonArray worldsArr = new JsonArray();
        List<String> sortedWorlds = new ArrayList<>(snapshot.worlds);
        sortedWorlds.sort(String.CASE_INSENSITIVE_ORDER);
        for (String world : sortedWorlds) {
            worldsArr.add(world);
        }
        obj.add("worlds", worldsArr);
        obj.addProperty("password", snapshot.password);
        obj.addProperty("quickChatSymbol", snapshot.quickChatSymbol);
        obj.addProperty("quickChatEnabled", snapshot.quickChatEnabled);
        obj.addProperty("isDefault", snapshot.isDefault);
        obj.addProperty("autoJoin", snapshot.autoJoin);
        obj.addProperty("description", snapshot.description);
        obj.addProperty("descriptionEnabled", snapshot.descriptionEnabled);
        obj.addProperty("motd", snapshot.motd);
        obj.addProperty("motdEnabled", snapshot.motdEnabled);

        return obj;
    }

    private void refreshKnownFileSignatures(Path dataDir) {
        String channelsSig = computeFileSignature(dataDir.resolve("channels.json"));
        String membersSig = computeFileSignature(dataDir.resolve("channel-members.json"));
        synchronized (saveStateLock) {
            lastKnownChannelsSignature = channelsSig;
            lastKnownMembersSignature = membersSig;
        }
    }

    private String computeFileSignature(Path file) {
        try {
            if (!Files.exists(file)) {
                return "<missing>";
            }
            byte[] bytes = Files.readAllBytes(file);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "<error>";
        }
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
            if (obj.has("description") && !obj.get("description").isJsonNull()) {
                ch.setDescription(obj.get("description").getAsString());
            }
            if (obj.has("descriptionEnabled")) {
                ch.setDescriptionEnabled(obj.get("descriptionEnabled").getAsBoolean());
            }
            if (obj.has("motd") && !obj.get("motd").isJsonNull()) {
                ch.setMotd(obj.get("motd").getAsString());
            }
            if (obj.has("motdEnabled")) {
                ch.setMotdEnabled(obj.get("motdEnabled").getAsBoolean());
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
        if (channel == null || channel.getName() == null || channel.getName().isBlank()) {
            return false;
        }
        if (channels.containsKey(channel.getName().toLowerCase())) return false;

        channel.setChangeListener(this::markDirty);
        channels.put(channel.getName().toLowerCase(), channel);
        markDirty();
        return true;
    }

    public boolean unregisterChannel(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
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
        if (name == null || name.isBlank()) {
            return null;
        }
        Channel channel = channels.get(name.toLowerCase());
        if (channel != null) return channel;
        for (Channel ch : channels.values()) {
            String nick = ch.getNick();
            if (nick != null && nick.equalsIgnoreCase(name)) return ch;
        }
        return null;
    }

    /**
     * Find channel by name, nick, or partial match (prefix)
     */
    public Channel findChannel(String input) {
        if (input == null || input.isBlank()) return null;
        String lower = input.toLowerCase();

        // Exact name match
        Channel exact = channels.get(lower);
        if (exact != null) return exact;

        // Exact nick match
        for (Channel ch : channels.values()) {
            String nick = ch.getNick();
            if (nick != null && nick.equalsIgnoreCase(input)) return ch;
        }

        // Prefix match on name
        for (Channel ch : channels.values()) {
            if (ch.getName().toLowerCase().startsWith(lower)) return ch;
        }

        // Prefix match on nick
        for (Channel ch : channels.values()) {
            String nick = ch.getNick();
            if (nick != null && nick.toLowerCase().startsWith(lower)) return ch;
        }

        return null;
    }

    /**
     * Find a channel whose quickChatSymbol matches the start of the message.
     * Returns null if no match found.
     */
    public Channel findChannelByQuickChatSymbol(String message) {
        if (message == null || message.isEmpty()) return null;
        Channel bestMatch = null;
        int bestMatchLength = -1;
        for (Channel ch : channels.values()) {
            if (!ch.hasQuickChatSymbol()) {
                continue;
            }
            String symbol = ch.getQuickChatSymbol();
            if (!message.startsWith(symbol)) {
                continue;
            }

            int symbolLength = symbol.length();
            if (symbolLength > bestMatchLength) {
                bestMatch = ch;
                bestMatchLength = symbolLength;
                continue;
            }

            if (symbolLength == bestMatchLength && bestMatch != null
                && ch.getName().compareToIgnoreCase(bestMatch.getName()) < 0) {
                bestMatch = ch;
            }
        }
        return bestMatch;
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
        if (name == null || name.isBlank()) {
            return null;
        }
        if (channelExists(name)) return null;
        Channel channel = new Channel(name);
        channel.setNick(generateUniqueAutoNick(name, collectUsedNicksLower()));
        channel.setOwner(creator);
        channel.addModerator(creator);
        registerChannel(channel);
        return channel;
    }

    private Set<String> collectUsedNicksLower() {
        Set<String> used = new HashSet<>();
        for (Channel channel : channels.values()) {
            String nick = channel.getNick();
            if (nick != null && !nick.isBlank()) {
                used.add(nick.toLowerCase(Locale.ROOT));
            }
        }
        return used;
    }

    private String getAutoNickBase(String channelName) {
        if (channelName == null) {
            return "c";
        }
        String trimmed = channelName.trim();
        if (trimmed.isEmpty()) {
            return "c";
        }
        return trimmed.substring(0, 1).toLowerCase(Locale.ROOT);
    }

    private String generateUniqueAutoNick(String channelName, Set<String> usedNicksLower) {
        String base = getAutoNickBase(channelName);
        String candidate = base;
        int suffix = 2;

        while (usedNicksLower.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "-" + suffix;
            suffix++;
        }

        usedNicksLower.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    private boolean isAutoNickCandidate(Channel channel) {
        String nick = channel.getNick();
        if (nick == null || nick.isBlank()) {
            return true;
        }
        return nick.equalsIgnoreCase(getAutoNickBase(channel.getName()));
    }

    private boolean normalizeAutoGeneratedNicks() {
        List<Channel> orderedChannels = new ArrayList<>(channels.values());
        orderedChannels.sort(Comparator.comparing(channel -> channel.getName().toLowerCase(Locale.ROOT)));

        Set<String> usedNicksLower = new HashSet<>();
        List<Channel> autoNickChannels = new ArrayList<>();

        for (Channel channel : orderedChannels) {
            if (isAutoNickCandidate(channel)) {
                autoNickChannels.add(channel);
                continue;
            }

            String nick = channel.getNick();
            if (nick != null && !nick.isBlank()) {
                usedNicksLower.add(nick.toLowerCase(Locale.ROOT));
            }
        }

        boolean changed = false;
        for (Channel channel : autoNickChannels) {
            String normalizedNick = generateUniqueAutoNick(channel.getName(), usedNicksLower);
            String currentNick = channel.getNick();
            if (currentNick == null || !normalizedNick.equalsIgnoreCase(currentNick)) {
                channel.setNick(normalizedNick);
                changed = true;
            }
        }

        return changed;
    }

    public void resetMotdSession(UUID playerId) {
        if (playerId != null) {
            motdShownThisLogin.remove(playerId);
        }
    }

    public void sendChannelMotd(UUID playerId, Channel channel) {
        if (playerId == null || channel == null) {
            return;
        }
        if (motdShownThisLogin.contains(playerId)) {
            return;
        }
        if (!channel.isMember(playerId)) {
            return;
        }
        if (!plugin.getConfig().isChannelMotdSystemEnabled()) {
            return;
        }
        if (!channel.isMotdEnabled() || !channel.hasMotd()) {
            return;
        }

        PlayerDataManager playerData = plugin.getPlayerDataManager();
        if (playerData == null) {
            return;
        }
        PlayerRef player = playerData.getOnlinePlayer(playerId);
        if (player == null) {
            return;
        }

        String motdNick = applyPapi(player, channel.getNick());
        String motdText = applyPapi(player, channel.getMotd());

        player.sendMessage(Message.join(
            Message.raw("[" + motdNick + " MOTD] ").color(channel.getColorHex()),
            Message.raw(motdText).color(channel.getEffectiveMessageColorHex())
        ));
        motdShownThisLogin.add(playerId);
    }

    private String applyPapi(PlayerRef player, String text) {
        if (text == null || text.isEmpty() || player == null || papi == null) {
            return text == null ? "" : text;
        }
        try {
            String resolved = papi.setPlaceholders(player, text);
            return resolved == null ? text : resolved;
        } catch (Throwable ignored) {
            return text;
        }
    }

    public boolean renameChannel(String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
            return false;
        }
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
        if (name == null || name.isBlank()) {
            return false;
        }
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
