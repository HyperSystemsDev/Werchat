package com.werchat.channels;

import java.awt.Color;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
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
    private String readPermission;

    private UUID owner;
    private String quickChatSymbol; // e.g. "!" to allow "!hello" to route to this channel
    private boolean quickChatEnabled; // whether quick chat symbol is active for this channel
    private final Set<String> worlds; // world name restrictions (empty = all worlds)

    private transient Runnable changeListener;

    public Channel(String name) {
        this.name = name;
        this.nick = name.toLowerCase().substring(0, Math.min(1, name.length()));
        this.color = Color.WHITE;
        this.format = "{nick} {sender}: {msg}";
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

        refreshPermissionNodes();
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    private void notifyChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public boolean addMember(UUID playerId) {
        if (banned.contains(playerId)) {
            return false;
        }

        boolean changed = members.add(playerId);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean removeMember(UUID playerId) {
        boolean changed = members.remove(playerId);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean ban(UUID playerId) {
        boolean changed = members.remove(playerId);
        changed = banned.add(playerId) || changed;
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean unban(UUID playerId) {
        boolean changed = banned.remove(playerId);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean isBanned(UUID playerId) {
        return banned.contains(playerId);
    }

    public boolean mute(UUID playerId) {
        boolean changed = muted.add(playerId);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean unmute(UUID playerId) {
        boolean changed = muted.remove(playerId);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean isMuted(UUID playerId) {
        return muted.contains(playerId);
    }

    public boolean addModerator(UUID playerId) {
        boolean changed = moderators.add(playerId);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean removeModerator(UUID playerId) {
        boolean changed = moderators.remove(playerId);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean isModerator(UUID playerId) {
        return moderators.contains(playerId);
    }

    public boolean checkPassword(String input) {
        if (password == null || password.isEmpty()) {
            return true;
        }
        return password.equals(input);
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    // Getters/Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (Objects.equals(this.name, name)) {
            return;
        }

        this.name = name;
        refreshPermissionNodes();
        notifyChanged();
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        if (Objects.equals(this.nick, nick)) {
            return;
        }

        this.nick = nick;
        notifyChanged();
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        if (Objects.equals(this.color, color)) {
            return;
        }

        this.color = color;
        notifyChanged();
    }

    public String getColorHex() {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public Color getMessageColor() {
        return messageColor;
    }

    public void setMessageColor(Color messageColor) {
        if (Objects.equals(this.messageColor, messageColor)) {
            return;
        }

        this.messageColor = messageColor;
        notifyChanged();
    }

    public boolean hasMessageColor() {
        return messageColor != null;
    }

    public String getMessageColorHex() {
        return messageColor != null
            ? String.format("#%02x%02x%02x", messageColor.getRed(), messageColor.getGreen(), messageColor.getBlue())
            : null;
    }

    public String getEffectiveMessageColorHex() {
        return messageColor != null ? getMessageColorHex() : getColorHex();
    }

    public boolean isAutoJoin() {
        return autoJoin;
    }

    public void setAutoJoin(boolean autoJoin) {
        if (this.autoJoin == autoJoin) {
            return;
        }

        this.autoJoin = autoJoin;
        notifyChanged();
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        if (Objects.equals(this.format, format)) {
            return;
        }

        this.format = format;
        notifyChanged();
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        if (this.distance == distance) {
            return;
        }

        this.distance = distance;
        notifyChanged();
    }

    public boolean isGlobal() {
        return distance <= 0;
    }

    public boolean isLocal() {
        return distance > 0;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (Objects.equals(this.password, password)) {
            return;
        }

        this.password = password;
        notifyChanged();
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        if (this.isDefault == isDefault) {
            return;
        }

        this.isDefault = isDefault;
        notifyChanged();
    }

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusable(boolean focusable) {
        if (this.focusable == focusable) {
            return;
        }

        this.focusable = focusable;
        notifyChanged();
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        if (this.verbose == verbose) {
            return;
        }

        this.verbose = verbose;
        notifyChanged();
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public int getMemberCount() {
        return members.size();
    }

    public Set<UUID> getBanned() {
        return new HashSet<>(banned);
    }

    public Set<UUID> getMuted() {
        return new HashSet<>(muted);
    }

    public Set<UUID> getModerators() {
        return new HashSet<>(moderators);
    }

    public String getJoinPermission() {
        return joinPermission;
    }

    public String getSpeakPermission() {
        return speakPermission;
    }

    public String getReadPermission() {
        return readPermission;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        if (Objects.equals(this.owner, owner)) {
            return;
        }

        this.owner = owner;
        notifyChanged();
    }

    public String getQuickChatSymbol() {
        return quickChatSymbol;
    }

    public void setQuickChatSymbol(String quickChatSymbol) {
        if (Objects.equals(this.quickChatSymbol, quickChatSymbol)) {
            return;
        }

        this.quickChatSymbol = quickChatSymbol;
        notifyChanged();
    }

    public boolean hasQuickChatSymbol() {
        return quickChatSymbol != null && !quickChatSymbol.isEmpty();
    }

    public boolean isQuickChatEnabled() {
        return quickChatEnabled;
    }

    public void setQuickChatEnabled(boolean quickChatEnabled) {
        if (this.quickChatEnabled == quickChatEnabled) {
            return;
        }

        this.quickChatEnabled = quickChatEnabled;
        notifyChanged();
    }

    public Set<String> getWorlds() {
        return new HashSet<>(worlds);
    }

    public void addWorld(String world) {
        if (world == null || world.isEmpty()) {
            return;
        }

        boolean changed = worlds.add(world);
        if (changed) {
            notifyChanged();
        }
    }

    public void removeWorld(String world) {
        boolean changed = worlds.remove(world);
        if (changed) {
            notifyChanged();
        }
    }

    public void clearWorlds() {
        if (worlds.isEmpty()) {
            return;
        }

        worlds.clear();
        notifyChanged();
    }

    public boolean hasWorlds() {
        return !worlds.isEmpty();
    }

    public boolean isWorldRestricted() {
        return hasWorlds();
    }

    public boolean isInAllowedWorld(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }

    // Backward compat helper for single-world migration
    public void setWorld(String world) {
        Set<String> next = new HashSet<>();
        if (world != null && !world.isEmpty()) {
            next.add(world);
        }

        if (worlds.equals(next)) {
            return;
        }

        worlds.clear();
        worlds.addAll(next);
        notifyChanged();
    }

    public String getWorldsDisplay() {
        return worlds.isEmpty() ? "All worlds" : String.join(", ", worlds);
    }

    private void refreshPermissionNodes() {
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        this.joinPermission = "werchat.channel." + lowerName + ".join";
        this.speakPermission = "werchat.channel." + lowerName + ".speak";
        this.readPermission = "werchat.channel." + lowerName + ".read";
    }
}
