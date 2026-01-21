package com.werchat.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

public class PlayerDataManager {

    private final WerchatPlugin plugin;
    private final Map<UUID, PlayerChatData> playerData;
    private final Map<UUID, PlayerRef> onlinePlayers;

    public PlayerDataManager(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.playerData = new HashMap<>();
        this.onlinePlayers = new HashMap<>();
    }

    public void trackPlayer(UUID playerId, PlayerRef player) {
        onlinePlayers.put(playerId, player);
    }

    public void untrackPlayer(UUID playerId) {
        onlinePlayers.remove(playerId);
    }

    public PlayerRef getOnlinePlayer(UUID playerId) {
        return onlinePlayers.get(playerId);
    }

    public Collection<PlayerRef> getOnlinePlayers() {
        return Collections.unmodifiableCollection(onlinePlayers.values());
    }

    public PlayerRef findPlayerByName(String name) {
        for (PlayerRef player : onlinePlayers.values()) {
            if (player.getUsername().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    public PlayerChatData getPlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, id -> {
            PlayerChatData data = new PlayerChatData(id);
            Channel defaultChannel = plugin.getChannelManager().getDefaultChannel();
            if (defaultChannel != null) data.setFocusedChannel(defaultChannel.getName());
            return data;
        });
    }

    public void removePlayerData(UUID playerId) { playerData.remove(playerId); }

    public String getFocusedChannel(UUID playerId) { return getPlayerData(playerId).getFocusedChannel(); }
    public void setFocusedChannel(UUID playerId, String channelName) { getPlayerData(playerId).setFocusedChannel(channelName); }

    public boolean isIgnoring(UUID playerId, UUID targetId) { return getPlayerData(playerId).isIgnoring(targetId); }
    public void toggleIgnore(UUID playerId, UUID targetId) {
        PlayerChatData data = getPlayerData(playerId);
        if (data.isIgnoring(targetId)) data.removeIgnore(targetId);
        else data.addIgnore(targetId);
    }
    public Set<UUID> getIgnoredPlayers(UUID playerId) { return getPlayerData(playerId).getIgnoredPlayers(); }

    public UUID getLastMessageFrom(UUID playerId) { return getPlayerData(playerId).getLastMessageFrom(); }
    public void setLastMessageFrom(UUID playerId, UUID fromId) { getPlayerData(playerId).setLastMessageFrom(fromId); }

    // Cooldown tracking
    public long getLastMessageTime(UUID playerId) { return getPlayerData(playerId).getLastMessageTime(); }
    public void setLastMessageTime(UUID playerId, long time) { getPlayerData(playerId).setLastMessageTime(time); }

    // Cooldown bypass (always false for now - can be extended later via config)
    public boolean hasCooldownBypass(UUID playerId) { return false; }

    // Nickname methods
    public String getNickname(UUID playerId) { return getPlayerData(playerId).getNickname(); }
    public void setNickname(UUID playerId, String nickname) {
        getPlayerData(playerId).setNickname(nickname);
        saveNicknames();
    }
    public String getNickColor(UUID playerId) { return getPlayerData(playerId).getNickColor(); }
    public void setNickColor(UUID playerId, String color) {
        getPlayerData(playerId).setNickColor(color);
        saveNicknames();
    }
    public boolean hasNickname(UUID playerId) { return getPlayerData(playerId).hasNickname(); }

    public String getDisplayName(UUID playerId) {
        PlayerChatData data = getPlayerData(playerId);
        if (data.hasNickname()) {
            return data.getNickname();
        }
        PlayerRef player = getOnlinePlayer(playerId);
        return player != null ? player.getUsername() : "Unknown";
    }

    public String getDisplayColor(UUID playerId) {
        return getPlayerData(playerId).getNickColor();
    }

    public void clearNickname(UUID playerId) {
        PlayerChatData data = getPlayerData(playerId);
        data.setNickname(null);
        data.setNickColor(null);
        saveNicknames();
    }

    // Persistence for nicknames
    private Path getNicknamesFile() {
        return plugin.getDataDirectory().resolve("nicknames.json");
    }

    public void loadNicknames() {
        Path file = getNicknamesFile();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, NicknameData>>(){}.getType();
            Map<String, NicknameData> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                for (Map.Entry<String, NicknameData> entry : loaded.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    NicknameData nickData = entry.getValue();
                    PlayerChatData data = getPlayerData(playerId);
                    data.setNickname(nickData.nickname);
                    data.setNickColor(nickData.color);
                }
                plugin.getLogger().at(Level.INFO).log("Loaded %d nicknames", loaded.size());
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to load nicknames: %s", e.getMessage());
        }
    }

    public void saveNicknames() {
        Path file = getNicknamesFile();
        try {
            Files.createDirectories(file.getParent());
            Map<String, NicknameData> toSave = new HashMap<>();
            for (Map.Entry<UUID, PlayerChatData> entry : playerData.entrySet()) {
                PlayerChatData data = entry.getValue();
                if (data.hasNickname()) {
                    toSave.put(entry.getKey().toString(), new NicknameData(data.getNickname(), data.getNickColor()));
                }
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(toSave, writer);
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to save nicknames: %s", e.getMessage());
        }
    }

    private static class NicknameData {
        String nickname;
        String color;
        NicknameData(String nickname, String color) {
            this.nickname = nickname;
            this.color = color;
        }
    }

    public void saveAll() {
        saveNicknames();
        plugin.getLogger().at(Level.INFO).log("Saved data for %d players", playerData.size());
    }
    public void loadPlayer(UUID playerId) { /* Nicknames loaded on startup */ }

    public void clearTransientData(UUID playerId) {
        PlayerChatData data = playerData.get(playerId);
        if (data != null) {
            data.setLastMessageFrom(null);
        }
    }

    public static class PlayerChatData {
        private final UUID playerId;
        private String focusedChannel;
        private final Set<UUID> ignoredPlayers;
        private UUID lastMessageFrom;
        private long lastMessageTime; // For cooldown
        private String nickname; // Custom display name
        private String nickColor; // Hex color for nickname (e.g., "#FF5555")

        public PlayerChatData(UUID playerId) {
            this.playerId = playerId;
            this.focusedChannel = "Global";
            this.ignoredPlayers = new HashSet<>();
            this.lastMessageTime = 0;
            this.nickname = null;
            this.nickColor = null;
        }

        public UUID getPlayerId() { return playerId; }
        public String getFocusedChannel() { return focusedChannel; }
        public void setFocusedChannel(String channel) { this.focusedChannel = channel; }
        public boolean isIgnoring(UUID targetId) { return ignoredPlayers.contains(targetId); }
        public void addIgnore(UUID targetId) { ignoredPlayers.add(targetId); }
        public void removeIgnore(UUID targetId) { ignoredPlayers.remove(targetId); }
        public Set<UUID> getIgnoredPlayers() { return new HashSet<>(ignoredPlayers); }
        public UUID getLastMessageFrom() { return lastMessageFrom; }
        public void setLastMessageFrom(UUID from) { this.lastMessageFrom = from; }
        public long getLastMessageTime() { return lastMessageTime; }
        public void setLastMessageTime(long time) { this.lastMessageTime = time; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getNickColor() { return nickColor; }
        public void setNickColor(String nickColor) { this.nickColor = nickColor; }
        public boolean hasNickname() { return nickname != null && !nickname.isEmpty(); }
    }
}
