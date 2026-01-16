package com.werchat.listeners;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.config.WerchatConfig;
import com.werchat.storage.PlayerDataManager;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes chat messages to appropriate channels
 */
public class ChatListener {

    private final WerchatPlugin plugin;
    private final ChannelManager channelManager;
    private final PlayerDataManager playerDataManager;
    private final WerchatConfig config;

    // Pattern for @mentions
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    // HyperPerms integration - cached reflection lookups
    private static boolean hyperPermsChecked = false;
    private static boolean hyperPermsAvailable = false;
    private static Method getPrefixMethod = null;
    private static Method getSuffixMethod = null;
    private static Method preloadMethod = null;

    public ChatListener(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.config = plugin.getConfig();
    }

    /**
     * Check if player is an admin/op (has * or werchat.* permission)
     */
    private boolean isAdmin(UUID playerId) {
        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, "*") || perms.hasPermission(playerId, "werchat.*");
    }

    /**
     * Initialize HyperPerms reflection (called once, cached)
     */
    private void initHyperPerms() {
        if (hyperPermsChecked) return;
        hyperPermsChecked = true;

        try {
            Class<?> chatApi = Class.forName("com.hyperperms.api.ChatAPI");
            getPrefixMethod = chatApi.getMethod("getPrefix", UUID.class);
            getSuffixMethod = chatApi.getMethod("getSuffix", UUID.class);
            preloadMethod = chatApi.getMethod("preload", UUID.class);
            hyperPermsAvailable = true;
            plugin.getLogger().at(Level.INFO).log("[Werchat] HyperPerms ChatAPI found - prefix/suffix integration enabled");
        } catch (Exception e) {
            // HyperPerms not installed
            hyperPermsAvailable = false;
            plugin.getLogger().at(Level.INFO).log("[Werchat] HyperPerms not found - running standalone");
        }
    }

    /**
     * Preload HyperPerms data for a player (call on player join to warm cache)
     */
    public void preloadHyperPerms(UUID playerId) {
        initHyperPerms();
        if (!hyperPermsAvailable || preloadMethod == null) return;

        try {
            preloadMethod.invoke(null, playerId);
        } catch (Exception ignored) {
            // Silently fail - not critical
        }
    }

    /**
     * Get HyperPerms prefix for a player (returns empty string if not available)
     * Strips legacy color codes (§x) since Hytale uses hex colors
     */
    private String getHyperPermsPrefix(UUID playerId) {
        initHyperPerms();
        if (!hyperPermsAvailable || getPrefixMethod == null) {
            return "";
        }

        try {
            String prefix = (String) getPrefixMethod.invoke(null, playerId);
            if (prefix == null || prefix.isEmpty()) {
                plugin.getLogger().at(Level.INFO).log("[Werchat] HyperPerms returned empty prefix for %s", playerId);
                return "";
            }
            // Strip legacy Minecraft color codes (§x format)
            String stripped = prefix.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
            plugin.getLogger().at(Level.INFO).log("[Werchat] HyperPerms prefix: '%s' -> '%s'", prefix, stripped);
            return stripped;
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("[Werchat] HyperPerms getPrefix failed: %s", e.getMessage());
            return "";
        }
    }

    /**
     * Get HyperPerms suffix for a player (returns empty string if not available)
     * Strips legacy color codes (§x) since Hytale uses hex colors
     */
    private String getHyperPermsSuffix(UUID playerId) {
        initHyperPerms();
        if (!hyperPermsAvailable || getSuffixMethod == null) return "";

        try {
            String suffix = (String) getSuffixMethod.invoke(null, playerId);
            if (suffix == null || suffix.isEmpty()) return "";
            // Strip legacy Minecraft color codes (§x format)
            String stripped = suffix.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
            return stripped;
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Extract hex color from HyperPerms prefix (converts §x codes to hex)
     * Returns null if no color code found
     */
    private String extractPrefixColor(UUID playerId) {
        initHyperPerms();
        if (!hyperPermsAvailable || getPrefixMethod == null) return null;

        try {
            String prefix = (String) getPrefixMethod.invoke(null, playerId);
            if (prefix == null || prefix.isEmpty()) return null;

            // Find first color code and convert to hex
            if (prefix.length() >= 2 && prefix.charAt(0) == '§') {
                char code = Character.toLowerCase(prefix.charAt(1));
                return mcColorToHex(code);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Convert Minecraft color code to hex
     */
    private String mcColorToHex(char code) {
        return switch (code) {
            case '0' -> "#000000"; // Black
            case '1' -> "#0000AA"; // Dark Blue
            case '2' -> "#00AA00"; // Dark Green
            case '3' -> "#00AAAA"; // Dark Aqua
            case '4' -> "#AA0000"; // Dark Red
            case '5' -> "#AA00AA"; // Dark Purple
            case '6' -> "#FFAA00"; // Gold
            case '7' -> "#AAAAAA"; // Gray
            case '8' -> "#555555"; // Dark Gray
            case '9' -> "#5555FF"; // Blue
            case 'a' -> "#55FF55"; // Green
            case 'b' -> "#55FFFF"; // Aqua
            case 'c' -> "#FF5555"; // Red
            case 'd' -> "#FF55FF"; // Light Purple
            case 'e' -> "#FFFF55"; // Yellow
            case 'f' -> "#FFFFFF"; // White
            default -> "#AAAAAA";  // Default gray
        };
    }

    public void onPlayerChat(PlayerChatEvent event) {
        // Cancel default chat immediately - Werchat handles all chat routing
        event.setCancelled(true);

        PlayerRef sender = event.getSender();
        UUID senderId = sender.getUuid();
        String message = event.getContent();

        // Get player's focused channel
        String channelName = playerDataManager.getFocusedChannel(senderId);
        Channel channel = channelManager.getChannel(channelName);
        if (channel == null) {
            channel = channelManager.getDefaultChannel();
        }

        // Check membership
        if (!channel.isMember(senderId)) {
            sender.sendMessage(Message.raw("You are not in channel: " + channel.getName()).color("#FF0000"));
            return;
        }

        // Check muted
        if (channel.isMuted(senderId)) {
            sender.sendMessage(Message.raw("You are muted in " + channel.getName()).color("#FF0000"));
            return;
        }

        // Check cooldown (admins bypass)
        if (config.isCooldownEnabled() && !isAdmin(senderId)) {
            long now = System.currentTimeMillis();
            long lastTime = playerDataManager.getLastMessageTime(senderId);
            long cooldownMs = config.getCooldownSeconds() * 1000L;

            if (now - lastTime < cooldownMs) {
                int remaining = (int) Math.ceil((cooldownMs - (now - lastTime)) / 1000.0);
                String msg = config.getCooldownMessage().replace("{seconds}", String.valueOf(remaining));
                sender.sendMessage(Message.raw(msg).color("#FF5555"));
                return;
            }
        }

        // Word filter (admins bypass)
        if (config.isWordFilterEnabled() && !isAdmin(senderId)) {
            FilterResult filterResult = filterMessage(message);
            if (filterResult.containsBadWords) {
                if (config.getFilterMode().equals("block")) {
                    // Block entire message
                    if (config.isFilterNotifyPlayer()) {
                        sender.sendMessage(Message.raw(config.getFilterWarningMessage()).color("#FF5555"));
                    }
                    return;
                } else {
                    // Censor mode - replace bad words
                    message = filterResult.filteredMessage;
                    if (config.isFilterNotifyPlayer()) {
                        sender.sendMessage(Message.raw(config.getFilterWarningMessage()).color("#FFAA00"));
                    }
                }
            }
        }

        // Update cooldown time
        playerDataManager.setLastMessageTime(senderId, System.currentTimeMillis());

        // Broadcast to channel
        broadcastToChannel(channel, sender, message);
    }

    /**
     * Filter message for bad words
     */
    private FilterResult filterMessage(String message) {
        Set<String> badWords = config.getFilteredWords();
        String replacement = config.getFilterReplacement();
        String lowerMessage = message.toLowerCase();
        boolean found = false;
        String filtered = message;

        for (String word : badWords) {
            if (lowerMessage.contains(word.toLowerCase())) {
                found = true;
                // Case-insensitive replace
                filtered = filtered.replaceAll("(?i)" + Pattern.quote(word), replacement);
            }
        }

        return new FilterResult(found, filtered);
    }

    private static class FilterResult {
        final boolean containsBadWords;
        final String filteredMessage;

        FilterResult(boolean containsBadWords, String filteredMessage) {
            this.containsBadWords = containsBadWords;
            this.filteredMessage = filteredMessage;
        }
    }

    /**
     * Find mentioned players in a message
     */
    private Set<UUID> findMentionedPlayers(String message) {
        Set<UUID> mentioned = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(message);

        while (matcher.find()) {
            String username = matcher.group(1);
            PlayerRef player = playerDataManager.findPlayerByName(username);
            if (player != null) {
                mentioned.add(player.getUuid());
            }
        }

        return mentioned;
    }

    public void broadcastToChannel(Channel channel, PlayerRef sender, String message) {
        UUID senderId = sender.getUuid();
        String senderName = sender.getUsername();

        // Find mentioned players
        Set<UUID> mentionedPlayers = config.isMentionsEnabled() ? findMentionedPlayers(message) : Collections.emptySet();

        // Get sender position for distance check
        double senderX = 0, senderY = 0, senderZ = 0;
        boolean isLocal = channel.isLocal();
        int maxDistance = channel.getDistance();

        if (isLocal) {
            try {
                var senderPos = sender.getTransform().getPosition();
                senderX = senderPos.x;
                senderY = senderPos.y;
                senderZ = senderPos.z;
            } catch (Exception e) {
                // If we can't get position, treat as global
                isLocal = false;
            }
        }

        // Send to all channel members who aren't ignoring the sender
        for (UUID memberId : channel.getMembers()) {
            if (playerDataManager.isIgnoring(memberId, senderId)) {
                continue;
            }
            PlayerRef member = playerDataManager.getOnlinePlayer(memberId);
            if (member != null) {
                // Check distance for local channels
                if (isLocal && !memberId.equals(senderId)) {
                    try {
                        var memberPos = member.getTransform().getPosition();
                        double dx = memberPos.x - senderX;
                        double dy = memberPos.y - senderY;
                        double dz = memberPos.z - senderZ;
                        double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (distance > maxDistance) {
                            continue; // Too far away
                        }
                    } catch (Exception e) {
                        // If we can't check distance, skip this member
                        continue;
                    }
                }

                // Format message with mention highlighting for this recipient
                Message formatted = formatMessageForRecipient(channel, senderId, senderName, message, memberId, mentionedPlayers);
                member.sendMessage(formatted);
            }
        }

        // Log the message
        plugin.getLogger().at(Level.INFO).log("[%s] %s: %s", channel.getName(), senderName, message);
    }

    /**
     * Format message with mention highlighting for a specific recipient
     */
    private Message formatMessageForRecipient(Channel channel, UUID senderId, String senderName, String message,
                                               UUID recipientId, Set<UUID> mentionedPlayers) {
        // Check if this recipient is mentioned
        boolean isMentioned = mentionedPlayers.contains(recipientId);

        // Get HyperPerms prefix/suffix (empty string if not installed)
        String prefix = getHyperPermsPrefix(senderId);
        String suffix = getHyperPermsSuffix(senderId);
        String prefixColor = extractPrefixColor(senderId);
        if (prefixColor == null) prefixColor = "#AAAAAA"; // Default gray if no color

        if (isMentioned && config.isMentionsEnabled()) {
            // Highlight the entire message for mentioned players
            return Message.join(
                Message.raw("[" + channel.getNick() + "] ").color(channel.getColorHex()),
                Message.raw(prefix).color(prefixColor),
                Message.raw(senderName).color("#FFFFFF"),
                Message.raw(suffix).color("#AAAAAA"),
                Message.raw(": ").color("#AAAAAA"),
                Message.raw(message).color(config.getMentionColor()).bold(true)
            );
        } else {
            // Normal formatting
            return Message.join(
                Message.raw("[" + channel.getNick() + "] ").color(channel.getColorHex()),
                Message.raw(prefix).color(prefixColor),
                Message.raw(senderName).color("#FFFFFF"),
                Message.raw(suffix).color("#AAAAAA"),
                Message.raw(": ").color("#AAAAAA"),
                Message.raw(message).color(channel.getColorHex())
            );
        }
    }

    public void sendPrivateMessage(PlayerRef sender, PlayerRef recipient, String message) {
        UUID senderId = sender.getUuid();
        UUID recipientId = recipient.getUuid();

        // Check if recipient is ignoring sender
        if (playerDataManager.isIgnoring(recipientId, senderId)) {
            sender.sendMessage(Message.raw("That player is not receiving messages from you.").color("#FF0000"));
            return;
        }

        // Word filter for PMs
        if (config.isWordFilterEnabled()) {
            FilterResult filterResult = filterMessage(message);
            if (filterResult.containsBadWords) {
                if (config.getFilterMode().equals("block")) {
                    if (config.isFilterNotifyPlayer()) {
                        sender.sendMessage(Message.raw(config.getFilterWarningMessage()).color("#FF5555"));
                    }
                    return;
                } else {
                    message = filterResult.filteredMessage;
                    if (config.isFilterNotifyPlayer()) {
                        sender.sendMessage(Message.raw(config.getFilterWarningMessage()).color("#FFAA00"));
                    }
                }
            }
        }

        String senderName = sender.getUsername();
        String recipientName = recipient.getUsername();

        // Message to recipient: [From SenderName] message
        Message toRecipient = Message.join(
            Message.raw("[From ").color("#AAAAAA"),
            Message.raw(senderName).color("#55FF55"),
            Message.raw("] ").color("#AAAAAA"),
            Message.raw(message).color("#FFFFFF")
        );

        // Message to sender: [To RecipientName] message
        Message toSender = Message.join(
            Message.raw("[To ").color("#AAAAAA"),
            Message.raw(recipientName).color("#55FF55"),
            Message.raw("] ").color("#AAAAAA"),
            Message.raw(message).color("#FFFFFF")
        );

        recipient.sendMessage(toRecipient);
        sender.sendMessage(toSender);

        // Update last message from for reply functionality
        playerDataManager.setLastMessageFrom(recipientId, senderId);

        // Log PM
        plugin.getLogger().at(Level.INFO).log("[PM] %s -> %s: %s", senderName, recipientName, message);
    }
}
