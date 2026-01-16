package com.werchat.storage;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;

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

    public void saveAll() { plugin.getLogger().at(Level.INFO).log("Saved data for %d players", playerData.size()); }
    public void loadPlayer(UUID playerId) { /* TODO */ }

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

        public PlayerChatData(UUID playerId) {
            this.playerId = playerId;
            this.focusedChannel = "Global";
            this.ignoredPlayers = new HashSet<>();
            this.lastMessageTime = 0;
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
    }
}
