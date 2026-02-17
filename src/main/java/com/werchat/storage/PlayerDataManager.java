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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PlayerDataManager {

    private static final long NICKNAME_SAVE_DEBOUNCE_SECONDS = 20;

    private final WerchatPlugin plugin;
    private final Map<UUID, PlayerChatData> playerData;
    private final Map<UUID, PlayerRef> onlinePlayers;
    private final Map<UUID, String> knownNames; // persists after disconnect
    private final ScheduledExecutorService nicknameSaveExecutor;
    private final Object nicknameSaveLock = new Object();
    private ScheduledFuture<?> pendingNicknameSave;

    public PlayerDataManager(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.playerData = new HashMap<>();
        this.onlinePlayers = new HashMap<>();
        this.knownNames = new HashMap<>();
        this.nicknameSaveExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public void trackPlayer(UUID playerId, PlayerRef player) {
        onlinePlayers.put(playerId, player);
        knownNames.put(playerId, player.getUsername());
    }

    public void untrackPlayer(UUID playerId) {
        onlinePlayers.remove(playerId);
    }

    public PlayerRef getOnlinePlayer(UUID playerId) {
        return onlinePlayers.get(playerId);
    }

    public String getKnownName(UUID playerId) {
        PlayerRef online = onlinePlayers.get(playerId);
        if (online != null) return online.getUsername();
        return knownNames.getOrDefault(playerId, "");
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
            PlayerChatData data = new PlayerChatData();
            Channel defaultChannel = plugin.getChannelManager().getDefaultChannel();
            if (defaultChannel != null) data.setFocusedChannel(defaultChannel.getName());
            return data;
        });
    }

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

    // Nickname methods
    public String getNickname(UUID playerId) { return getPlayerData(playerId).getNickname(); }
    public void setNickname(UUID playerId, String nickname) {
        getPlayerData(playerId).setNickname(nickname);
        markNicknamesDirty();
    }
    public String getNickColor(UUID playerId) { return getPlayerData(playerId).getNickColor(); }
    public void setNickColor(UUID playerId, String color) {
        getPlayerData(playerId).setNickColor(color);
        markNicknamesDirty();
    }
    public String getNickGradientEnd(UUID playerId) { return getPlayerData(playerId).getNickGradientEnd(); }
    public void setNickGradientEnd(UUID playerId, String color) {
        getPlayerData(playerId).setNickGradientEnd(color);
        markNicknamesDirty();
    }

    // Message color methods
    public String getMsgColor(UUID playerId) { return getPlayerData(playerId).getMsgColor(); }
    public void setMsgColor(UUID playerId, String color) {
        getPlayerData(playerId).setMsgColor(color);
        markNicknamesDirty();
    }
    public String getMsgGradientEnd(UUID playerId) { return getPlayerData(playerId).getMsgGradientEnd(); }
    public void setMsgGradientEnd(UUID playerId, String color) {
        getPlayerData(playerId).setMsgGradientEnd(color);
        markNicknamesDirty();
    }
    public void clearMsgColor(UUID playerId) {
        PlayerChatData data = getPlayerData(playerId);
        data.setMsgColor(null);
        data.setMsgGradientEnd(null);
        markNicknamesDirty();
    }

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
        data.setNickGradientEnd(null);
        markNicknamesDirty();
    }

    // Persistence for nicknames
    private Path getNicknamesFile() {
        return plugin.getDataDirectory().resolve("nicknames.json");
    }

    private void markNicknamesDirty() {
        synchronized (nicknameSaveLock) {
            if (pendingNicknameSave != null) {
                pendingNicknameSave.cancel(false);
            }
            pendingNicknameSave = nicknameSaveExecutor.schedule(
                this::flushDebouncedNicknameSave,
                NICKNAME_SAVE_DEBOUNCE_SECONDS,
                TimeUnit.SECONDS
            );
        }
    }

    private void flushDebouncedNicknameSave() {
        synchronized (nicknameSaveLock) {
            pendingNicknameSave = null;
        }

        saveNicknames();
    }

    public void shutdownDebouncedSaver() {
        synchronized (nicknameSaveLock) {
            if (pendingNicknameSave != null) {
                pendingNicknameSave.cancel(false);
                pendingNicknameSave = null;
            }
        }

        nicknameSaveExecutor.shutdown();
        try {
            if (!nicknameSaveExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                nicknameSaveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            nicknameSaveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
                    data.setNickGradientEnd(nickData.gradientEnd);
                    data.setMsgColor(nickData.msgColor);
                    data.setMsgGradientEnd(nickData.msgGradientEnd);
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
                if (data.hasNickname() || data.hasMsgColor()) {
                    toSave.put(entry.getKey().toString(), new NicknameData(
                        data.getNickname(), data.getNickColor(), data.getNickGradientEnd(),
                        data.getMsgColor(), data.getMsgGradientEnd()));
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
        String gradientEnd;
        String msgColor;
        String msgGradientEnd;
        NicknameData(String nickname, String color, String gradientEnd, String msgColor, String msgGradientEnd) {
            this.nickname = nickname;
            this.color = color;
            this.gradientEnd = gradientEnd;
            this.msgColor = msgColor;
            this.msgGradientEnd = msgGradientEnd;
        }
    }

    public void clearTransientData(UUID playerId) {
        PlayerChatData data = playerData.get(playerId);
        if (data != null) {
            data.setLastMessageFrom(null);
        }
    }

    public static class PlayerChatData {
        private String focusedChannel;
        private final Set<UUID> ignoredPlayers;
        private UUID lastMessageFrom;
        private long lastMessageTime; // For cooldown
        private String nickname; // Custom display name
        private String nickColor; // Hex color for nickname (e.g., "#FF5555")
        private String nickGradientEnd; // End color for gradient (e.g., "#5555FF")
        private String msgColor; // Custom message color
        private String msgGradientEnd; // End color for message gradient

        public PlayerChatData() {
            this.focusedChannel = "Global";
            this.ignoredPlayers = new HashSet<>();
            this.lastMessageTime = 0;
            this.nickname = null;
            this.nickColor = null;
            this.nickGradientEnd = null;
            this.msgColor = null;
            this.msgGradientEnd = null;
        }

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
        public String getNickGradientEnd() { return nickGradientEnd; }
        public void setNickGradientEnd(String nickGradientEnd) { this.nickGradientEnd = nickGradientEnd; }
        public boolean hasNickname() { return nickname != null && !nickname.isEmpty(); }
        public String getMsgColor() { return msgColor; }
        public void setMsgColor(String msgColor) { this.msgColor = msgColor; }
        public String getMsgGradientEnd() { return msgGradientEnd; }
        public void setMsgGradientEnd(String msgGradientEnd) { this.msgGradientEnd = msgGradientEnd; }
        public boolean hasMsgColor() { return msgColor != null && !msgColor.isEmpty(); }
    }
}
