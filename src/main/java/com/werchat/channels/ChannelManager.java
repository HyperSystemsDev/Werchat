package com.werchat.channels;

import com.google.gson.*;
import com.werchat.WerchatPlugin;

import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all chat channels
 */
public class ChannelManager {

    private final WerchatPlugin plugin;
    private final Map<String, Channel> channels;
    private final Gson gson;
    private Channel defaultChannel;

    public ChannelManager(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.channels = new HashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void loadChannels() {
        Path dataDir = plugin.getDataDirectory();
        Path channelsFile = dataDir.resolve("channels.json");

        try {
            Files.createDirectories(dataDir);

            if (Files.exists(channelsFile)) {
                String json = Files.readString(channelsFile);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                for (JsonElement el : arr) {
                    Channel ch = deserializeChannel(el.getAsJsonObject());
                    if (ch != null) {
                        registerChannel(ch);
                        if (ch.isDefault()) defaultChannel = ch;
                    }
                }
                plugin.getLogger().at(Level.INFO).log("Loaded %d channels from file", channels.size());
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to load channels: %s", e.getMessage());
        }

        // Create defaults if none loaded
        if (channels.isEmpty()) {
            createDefaultChannels();
            saveChannels();
        }

        // Ensure we have a default
        if (defaultChannel == null && !channels.isEmpty()) {
            defaultChannel = channels.values().iterator().next();
            defaultChannel.setDefault(true);
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
        registerChannel(trade);

        Channel support = new Channel("Support");
        support.setNick("Support");
        support.setColor(Color.GREEN);
        support.setFormat("{nick} {sender}: {msg}");
        support.setAutoJoin(true);
        registerChannel(support);
    }

    public void saveChannels() {
        try {
            Path dataDir = plugin.getDataDirectory();
            Files.createDirectories(dataDir);
            Path channelsFile = dataDir.resolve("channels.json");

            JsonArray arr = new JsonArray();
            for (Channel ch : channels.values()) {
                arr.add(serializeChannel(ch));
            }

            Files.writeString(channelsFile, gson.toJson(arr));
            plugin.getLogger().at(Level.INFO).log("Saved %d channels", channels.size());
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to save channels: %s", e.getMessage());
        }
    }

    private JsonObject serializeChannel(Channel ch) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", ch.getName());
        obj.addProperty("nick", ch.getNick());
        obj.addProperty("color", ch.getColorHex());
        obj.addProperty("format", ch.getFormat());
        obj.addProperty("distance", ch.getDistance());
        obj.addProperty("password", ch.getPassword());
        obj.addProperty("isDefault", ch.isDefault());
        obj.addProperty("autoJoin", ch.isAutoJoin());

        JsonArray mods = new JsonArray();
        for (UUID id : ch.getModerators()) mods.add(id.toString());
        obj.add("moderators", mods);

        JsonArray members = new JsonArray();
        for (UUID id : ch.getMembers()) members.add(id.toString());
        obj.add("members", members);

        JsonArray banned = new JsonArray();
        for (UUID id : ch.getBanned()) banned.add(id.toString());
        obj.add("banned", banned);

        JsonArray muted = new JsonArray();
        for (UUID id : ch.getMuted()) muted.add(id.toString());
        obj.add("muted", muted);

        if (ch.getOwner() != null) {
            obj.addProperty("owner", ch.getOwner().toString());
        }

        if (ch.hasQuickChatSymbol()) {
            obj.addProperty("quickChatSymbol", ch.getQuickChatSymbol());
        }

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

            if (obj.has("owner") && !obj.get("owner").isJsonNull()) {
                ch.setOwner(UUID.fromString(obj.get("owner").getAsString()));
            }

            if (obj.has("quickChatSymbol") && !obj.get("quickChatSymbol").isJsonNull()) {
                ch.setQuickChatSymbol(obj.get("quickChatSymbol").getAsString());
            }

            return ch;
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to load channel: %s", e.getMessage());
            return null;
        }
    }

    public boolean registerChannel(Channel channel) {
        if (channels.containsKey(channel.getName().toLowerCase())) return false;
        channels.put(channel.getName().toLowerCase(), channel);
        return true;
    }

    public boolean unregisterChannel(String name) {
        Channel channel = channels.remove(name.toLowerCase());
        if (channel != null && channel == defaultChannel) {
            defaultChannel = channels.values().stream()
                    .filter(Channel::isDefault).findFirst()
                    .orElse(channels.values().stream().findFirst().orElse(null));
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
        saveChannels();
        return channel;
    }

    public boolean renameChannel(String oldName, String newName) {
        Channel channel = getChannel(oldName);
        if (channel == null) return false;
        if (channelExists(newName)) return false;

        channels.remove(oldName.toLowerCase());
        channel.setName(newName);
        channels.put(newName.toLowerCase(), channel);
        saveChannels();
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
