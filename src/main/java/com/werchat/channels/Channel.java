package com.werchat.channels;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a chat channel
 */
public class Channel {

    private String name;
    private String nick;
    private Color color;
    private Color messageColor; // null = use tag color for message text
    private String format;
    private int distance;
    private String password;
    private boolean isDefault;
    private boolean focusable;
    private boolean verbose;
    private boolean autoJoin;

    private final Set<UUID> members;
    private final Set<UUID> banned;
    private final Set<UUID> muted;
    private final Set<UUID> moderators;

    private String joinPermission;
    private String speakPermission;
    private String seePermission;

    private UUID owner;
    private String quickChatSymbol; // e.g. "!" to allow "!hello" to route to this channel
    private boolean quickChatEnabled; // whether quick chat symbol is active for this channel
    private final Set<String> worlds; // world name restrictions (empty = all worlds)

    public Channel(String name) {
        this.name = name;
        this.nick = name.toLowerCase().substring(0, Math.min(1, name.length()));
        this.color = Color.WHITE;
        this.format = "[{nick}] {sender}: {msg}";
        this.distance = 0;
        this.isDefault = false;
        this.focusable = true;
        this.verbose = true;
        this.autoJoin = false;

        this.members = new HashSet<>();
        this.banned = new HashSet<>();
        this.muted = new HashSet<>();
        this.moderators = new HashSet<>();

        this.worlds = new HashSet<>();

        this.joinPermission = "werchat.channel." + name.toLowerCase() + ".join";
        this.speakPermission = "werchat.channel." + name.toLowerCase() + ".speak";
        this.seePermission = "werchat.channel." + name.toLowerCase() + ".see";
    }

    public boolean addMember(UUID playerId) {
        if (banned.contains(playerId)) return false;
        return members.add(playerId);
    }

    public boolean removeMember(UUID playerId) { return members.remove(playerId); }
    public boolean isMember(UUID playerId) { return members.contains(playerId); }

    public boolean ban(UUID playerId) { members.remove(playerId); return banned.add(playerId); }
    public boolean unban(UUID playerId) { return banned.remove(playerId); }
    public boolean isBanned(UUID playerId) { return banned.contains(playerId); }

    public boolean mute(UUID playerId) { return muted.add(playerId); }
    public boolean unmute(UUID playerId) { return muted.remove(playerId); }
    public boolean isMuted(UUID playerId) { return muted.contains(playerId); }

    public boolean addModerator(UUID playerId) { return moderators.add(playerId); }
    public boolean removeModerator(UUID playerId) { return moderators.remove(playerId); }
    public boolean isModerator(UUID playerId) { return moderators.contains(playerId); }

    public boolean checkPassword(String input) {
        if (password == null || password.isEmpty()) return true;
        return password.equals(input);
    }
    public boolean hasPassword() { return password != null && !password.isEmpty(); }

    public String formatMessage(String senderName, String message, String prefix, String suffix) {
        String colorHex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return format
                .replace("{name}", name)
                .replace("{nick}", nick)
                .replace("{color}", colorHex)
                .replace("{sender}", senderName)
                .replace("{msg}", message)
                .replace("{prefix}", prefix != null ? prefix : "")
                .replace("{suffix}", suffix != null ? suffix : "");
    }

    // Getters/Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNick() { return nick; }
    public void setNick(String nick) { this.nick = nick; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public String getColorHex() { return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public Color getMessageColor() { return messageColor; }
    public void setMessageColor(Color messageColor) { this.messageColor = messageColor; }
    public boolean hasMessageColor() { return messageColor != null; }
    public String getMessageColorHex() { return messageColor != null ? String.format("#%02x%02x%02x", messageColor.getRed(), messageColor.getGreen(), messageColor.getBlue()) : null; }
    public String getEffectiveMessageColorHex() { return messageColor != null ? getMessageColorHex() : getColorHex(); }
    public boolean isAutoJoin() { return autoJoin; }
    public void setAutoJoin(boolean autoJoin) { this.autoJoin = autoJoin; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public int getDistance() { return distance; }
    public void setDistance(int distance) { this.distance = distance; }
    public boolean isGlobal() { return distance <= 0; }
    public boolean isLocal() { return distance > 0; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public boolean isFocusable() { return focusable; }
    public void setFocusable(boolean focusable) { this.focusable = focusable; }
    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public int getMemberCount() { return members.size(); }
    public Set<UUID> getBanned() { return new HashSet<>(banned); }
    public Set<UUID> getMuted() { return new HashSet<>(muted); }
    public Set<UUID> getModerators() { return new HashSet<>(moderators); }
    public String getJoinPermission() { return joinPermission; }
    public String getSpeakPermission() { return speakPermission; }
    public String getSeePermission() { return seePermission; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public String getQuickChatSymbol() { return quickChatSymbol; }
    public void setQuickChatSymbol(String quickChatSymbol) { this.quickChatSymbol = quickChatSymbol; }
    public boolean hasQuickChatSymbol() { return quickChatSymbol != null && !quickChatSymbol.isEmpty(); }
    public boolean isQuickChatEnabled() { return quickChatEnabled; }
    public void setQuickChatEnabled(boolean quickChatEnabled) { this.quickChatEnabled = quickChatEnabled; }
    public Set<String> getWorlds() { return new HashSet<>(worlds); }
    public void addWorld(String world) { if (world != null && !world.isEmpty()) worlds.add(world); }
    public void removeWorld(String world) { worlds.remove(world); }
    public void clearWorlds() { worlds.clear(); }
    public boolean hasWorlds() { return !worlds.isEmpty(); }
    public boolean isWorldRestricted() { return hasWorlds(); }
    public boolean isInAllowedWorld(String worldName) { return worlds.isEmpty() || worlds.contains(worldName); }
    // Backward compat helper for single-world migration
    public void setWorld(String world) { worlds.clear(); if (world != null && !world.isEmpty()) worlds.add(world); }
    public String getWorldsDisplay() { return worlds.isEmpty() ? "All worlds" : String.join(", ", worlds); }
}
