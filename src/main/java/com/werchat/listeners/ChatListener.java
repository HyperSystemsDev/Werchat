package com.werchat.listeners;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.config.WerchatConfig;
import com.werchat.integration.papi.PAPIIntegration;
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
    private final PAPIIntegration papi;

    // Pattern for @mentions
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");
    private static final Pattern FORMAT_TOKEN_PATTERN = Pattern.compile("(\\{name\\}|\\{nick\\}|\\{color\\}|\\{sender\\}|\\{msg\\}|\\{prefix\\}|\\{suffix\\})");
    private static final String LEGACY_DEFAULT_FORMAT = "{nick} {sender}: {msg}";
    private static final String CURRENT_DEFAULT_FORMAT = "[{nick}] {sender}: {msg}";

    // HyperPerms soft dependency - uses reflection to avoid hard dependency
    private static boolean hyperPermsChecked = false;
    private static boolean hyperPermsAvailable = false;
    private static Method hyperPermsPrefixMethod = null;
    private static Method hyperPermsSuffixMethod = null;

    // LuckPerms soft dependency
    private static boolean luckPermsChecked = false;
    private static boolean luckPermsAvailable = false;
    private static Method luckPermsProviderGet = null;
    private static Method luckPermsGetUserManager = null;
    private static Method userManagerGetUser = null;
    private static Method userGetCachedData = null;
    private static Method cachedDataGetMetaData = null;
    private static Method metaDataGetPrefix = null;
    private static Method metaDataGetSuffix = null;

    public ChatListener(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.config = plugin.getConfig();
        this.papi = PAPIIntegration.register(plugin);
    }

    /**
     * Check if player is an admin/op (has * or werchat.* permission)
     */
    private boolean isAdmin(UUID playerId) {
        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, "*") || perms.hasPermission(playerId, "werchat.*");
    }

    private boolean hasPermission(UUID playerId, String permission) {
        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, permission)
            || perms.hasPermission(playerId, "werchat.*")
            || perms.hasPermission(playerId, "*");
    }

    private boolean hasChannelSpeakPermission(UUID playerId, Channel channel) {
        return hasPermission(playerId, channel.getSpeakPermission());
    }

    private boolean hasChannelReadPermission(UUID playerId, Channel channel) {
        return hasPermission(playerId, channel.getReadPermission());
    }

    private boolean bypassesCooldown(UUID playerId) {
        if (isAdmin(playerId)) {
            return true;
        }

        String bypassPermission = config.getCooldownBypassPermission();
        if (bypassPermission == null || bypassPermission.isBlank()) {
            return false;
        }

        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, bypassPermission);
    }

    /**
     * Resolve a world name to its UUID via Universe lookup.
     * Returns null if the world is not found.
     */
    private UUID resolveWorldUuid(String worldName) {
        if (worldName == null || worldName.isEmpty()) return null;
        try {
            World world = Universe.get().getWorld(worldName);
            if (world != null) {
                return world.getWorldConfig().getUuid();
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Failed to resolve world '%s': %s", worldName, e.getMessage());
        }
        return null;
    }

    /**
     * Check if a player is in one of the worlds a channel is restricted to.
     */
    private boolean isPlayerInChannelWorld(PlayerRef player, Channel channel) {
        if (!channel.isWorldRestricted()) return true;
        try {
            UUID playerWorldId = player.getWorldUuid();
            for (String worldName : channel.getWorlds()) {
                UUID channelWorldId = resolveWorldUuid(worldName);
                if (channelWorldId != null && channelWorldId.equals(playerWorldId)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Initialize HyperPerms integration via reflection (soft dependency)
     */
    private void initHyperPerms() {
        if (hyperPermsChecked) return;
        hyperPermsChecked = true;

        try {
            Class<?> chatApiClass = Class.forName("com.hyperperms.api.ChatAPI");
            hyperPermsPrefixMethod = chatApiClass.getMethod("getPrefix", UUID.class);
            hyperPermsSuffixMethod = chatApiClass.getMethod("getSuffix", UUID.class);
            hyperPermsAvailable = true;
            plugin.getLogger().at(Level.INFO).log("HyperPerms integration enabled for prefix/suffix display");
        } catch (Exception e) {
            hyperPermsAvailable = false;
            plugin.getLogger().at(Level.WARNING).log("HyperPerms integration failed: %s", e.getMessage());
        }
    }

    /**
     * Initialize LuckPerms integration via reflection (soft dependency)
     */
    private void initLuckPerms() {
        if (luckPermsChecked) return;
        luckPermsChecked = true;

        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPermsProviderGet = providerClass.getMethod("get");

            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            luckPermsGetUserManager = luckPermsClass.getMethod("getUserManager");

            Class<?> userManagerClass = Class.forName("net.luckperms.api.model.user.UserManager");
            userManagerGetUser = userManagerClass.getMethod("getUser", UUID.class);

            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            userGetCachedData = userClass.getMethod("getCachedData");

            Class<?> cachedDataClass = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            cachedDataGetMetaData = cachedDataClass.getMethod("getMetaData");

            Class<?> metaDataClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            metaDataGetPrefix = metaDataClass.getMethod("getPrefix");
            metaDataGetSuffix = metaDataClass.getMethod("getSuffix");

            luckPermsAvailable = true;
            plugin.getLogger().at(Level.INFO).log("LuckPerms integration enabled for prefix/suffix display");
        } catch (Exception e) {
            luckPermsAvailable = false;
        }
    }

    /**
     * Get player prefix from permission plugins (HyperPerms or LuckPerms)
     */
    private String getPrefix(UUID playerId) {
        // Try HyperPerms first
        initHyperPerms();
        if (hyperPermsAvailable && hyperPermsPrefixMethod != null) {
            try {
                String prefix = (String) hyperPermsPrefixMethod.invoke(null, playerId);
                if (prefix != null && !prefix.isEmpty()) {
                    return prefix;
                }
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).log("HyperPerms prefix error: %s", e.getMessage());
            }
        }

        // Try LuckPerms
        initLuckPerms();
        if (luckPermsAvailable) {
            try {
                Object luckPerms = luckPermsProviderGet.invoke(null);
                Object userManager = luckPermsGetUserManager.invoke(luckPerms);
                Object user = userManagerGetUser.invoke(userManager, playerId);
                if (user != null) {
                    Object cachedData = userGetCachedData.invoke(user);
                    Object metaData = cachedDataGetMetaData.invoke(cachedData);
                    String prefix = (String) metaDataGetPrefix.invoke(metaData);
                    if (prefix != null && !prefix.isEmpty()) {
                        return prefix;
                    }
                }
            } catch (Exception ignored) {}
        }

        return "";
    }

    /**
     * Get player suffix from permission plugins (HyperPerms or LuckPerms)
     */
    private String getSuffix(UUID playerId) {
        // Try HyperPerms first
        initHyperPerms();
        if (hyperPermsAvailable && hyperPermsSuffixMethod != null) {
            try {
                String suffix = (String) hyperPermsSuffixMethod.invoke(null, playerId);
                if (suffix != null && !suffix.isEmpty()) {
                    return suffix;
                }
            } catch (Exception ignored) {}
        }

        // Try LuckPerms
        initLuckPerms();
        if (luckPermsAvailable) {
            try {
                Object luckPerms = luckPermsProviderGet.invoke(null);
                Object userManager = luckPermsGetUserManager.invoke(luckPerms);
                Object user = userManagerGetUser.invoke(userManager, playerId);
                if (user != null) {
                    Object cachedData = userGetCachedData.invoke(user);
                    Object metaData = cachedDataGetMetaData.invoke(cachedData);
                    String suffix = (String) metaDataGetSuffix.invoke(metaData);
                    if (suffix != null && !suffix.isEmpty()) {
                        return suffix;
                    }
                }
            } catch (Exception ignored) {}
        }

        return "";
    }

    public void onPlayerChat(PlayerChatEvent event) {
        // Respect other plugins (e.g. EssentialsPlus mute) that cancelled the event
        if (event.isCancelled() && !config.isIgnoreChatCancellations()) {
            return;
        }

        // Cancel default chat - Werchat handles channel routing
        event.setCancelled(true);

        handleChatInput(event.getSender(), event.getContent());
    }

    /**
     * Entry point for both native chat events and external plugin API integrations.
     */
    public void handleChatInput(PlayerRef sender, String rawMessage) {
        if (sender == null || rawMessage == null) {
            return;
        }

        UUID senderId = sender.getUuid();
        String message = rawMessage;
        if (message.isBlank()) {
            return;
        }

        // Check for quick chat symbol triggers (e.g. "!hello" routes to Global)
        Channel channel = null;
        {
            Channel quickChatChannel = channelManager.findChannelByQuickChatSymbol(message);
            if (quickChatChannel != null && quickChatChannel.isQuickChatEnabled()) {
                // Check permission for quick chat
                PermissionsModule qcPerms = PermissionsModule.get();
                boolean hasQuickChat = qcPerms.hasPermission(senderId, "werchat.quickchat")
                    || qcPerms.hasPermission(senderId, "werchat.*")
                    || qcPerms.hasPermission(senderId, "*");

                if (hasQuickChat) {
                    channel = quickChatChannel;
                    // Remove the symbol prefix from the message
                    message = message.substring(quickChatChannel.getQuickChatSymbol().length()).trim();
                    if (message.isEmpty()) {
                        return; // Just the symbol with no message, ignore silently
                    }
                }
            }
        }

        // Fall back to player's focused channel
        if (channel == null) {
            String channelName = playerDataManager.getFocusedChannel(senderId);
            channel = channelManager.getChannel(channelName);
            if (channel == null) {
                channel = channelManager.getDefaultChannel();
            }
        }
        if (channel == null) {
            sender.sendMessage(Message.raw("No chat channel is available").color("#FF0000"));
            return;
        }

        if (config.isEnforceChannelPermissions() && !hasChannelSpeakPermission(senderId, channel)) {
            sender.sendMessage(Message.raw("You don't have permission to speak in " + channel.getName()).color("#FF0000"));
            return;
        }
        if (config.isEnforceChannelPermissions() && !hasChannelReadPermission(senderId, channel)) {
            sender.sendMessage(Message.raw("You don't have permission to read " + channel.getName()).color("#FF0000"));
            return;
        }

        // Check world restriction - fall back to default if player isn't in the channel's world
        if (channel.isWorldRestricted() && !isPlayerInChannelWorld(sender, channel)) {
            Channel fallback = channelManager.getDefaultChannel();
            if (fallback != null && fallback != channel) {
                channel = fallback;
            } else {
                sender.sendMessage(Message.raw("You are not in the correct world for " + channel.getName()).color("#FF0000"));
                return;
            }
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

        // Check cooldown
        if (config.isCooldownEnabled() && !bypassesCooldown(senderId)) {
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

        // Get sender position and world for distance check
        double senderX = 0, senderY = 0, senderZ = 0;
        UUID senderWorldId = null;
        boolean isLocal = channel.isLocal();
        int maxDistance = channel.getDistance();

        if (isLocal) {
            try {
                var senderPos = sender.getTransform().getPosition();
                senderX = senderPos.x;
                senderY = senderPos.y;
                senderZ = senderPos.z;
                senderWorldId = sender.getWorldUuid();
            } catch (Exception e) {
                // If we can't get position, treat as global
                isLocal = false;
            }
        }

        // Resolve world restriction UUIDs for filtering
        Set<UUID> allowedWorldIds = new HashSet<>();
        if (channel.isWorldRestricted()) {
            for (String worldName : channel.getWorlds()) {
                UUID wid = resolveWorldUuid(worldName);
                if (wid != null) allowedWorldIds.add(wid);
            }
        }

        // Send to all channel members who aren't ignoring the sender
        for (UUID memberId : channel.getMembers()) {
            if (config.isEnforceChannelPermissions() && !hasChannelReadPermission(memberId, channel)) {
                continue;
            }
            if (playerDataManager.isIgnoring(memberId, senderId)) {
                continue;
            }
            PlayerRef member = playerDataManager.getOnlinePlayer(memberId);
            if (member != null) {
                // Check world restriction
                if (!allowedWorldIds.isEmpty()) {
                    try {
                        UUID memberWorldId = member.getWorldUuid();
                        if (!allowedWorldIds.contains(memberWorldId)) {
                            continue; // Not in any of the channel's worlds
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }

                // Check distance and world for local channels
                if (isLocal && !memberId.equals(senderId)) {
                    try {
                        // Must be in the same world
                        UUID memberWorldId = member.getWorldUuid();
                        if (senderWorldId != null && !senderWorldId.equals(memberWorldId)) {
                            continue; // Different world
                        }
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

                Message formatted = formatMessageForRecipient(channel, sender, message, memberId, mentionedPlayers);
                member.sendMessage(formatted);
            }
        }

        // Log the message
        plugin.getLogger().at(Level.INFO).log("[%s] %s: %s", channel.getName(), senderName, message);
    }

    /**
     * Format message with mention highlighting for a specific recipient.
     * Integrates with permission plugins for prefix/suffix display.
     */
    private Message formatMessageForRecipient(Channel channel, PlayerRef sender, String message,
                                              UUID recipientId, Set<UUID> mentionedPlayers) {
        UUID senderId = sender.getUuid();
        boolean isMentioned = mentionedPlayers.contains(recipientId);
        PlayerRef recipient = playerDataManager.getOnlinePlayer(recipientId);

        String displayName = playerDataManager.getDisplayName(senderId);
        String nickColor = playerDataManager.getDisplayColor(senderId);
        if (nickColor == null) nickColor = "#FFFFFF";

        String prefix = applyPapi(sender, recipient, getPrefix(senderId));
        String suffix = applyPapi(sender, recipient, getSuffix(senderId));

        Message senderPart = buildSenderPart(senderId, displayName, nickColor);
        Message messagePart = buildMessagePart(channel, senderId, message, isMentioned);

        Map<String, Message> tokenParts = new HashMap<>();
        tokenParts.put("{name}", Message.raw(applyPapi(sender, recipient, channel.getName())).color(channel.getColorHex()));
        tokenParts.put("{nick}", Message.raw(applyPapi(sender, recipient, channel.getNick())).color(channel.getColorHex()));
        tokenParts.put("{color}", Message.raw(channel.getColorHex()).color(channel.getColorHex()));
        tokenParts.put("{sender}", senderPart);
        tokenParts.put("{msg}", messagePart);
        tokenParts.put("{prefix}", parseColoredString(prefix));
        tokenParts.put("{suffix}", parseColoredString(suffix));

        String format = channel.getFormat();
        if (format == null || format.isBlank()) {
            format = CURRENT_DEFAULT_FORMAT;
        } else if (LEGACY_DEFAULT_FORMAT.equals(format)) {
            // Render old default templates with the current bracketed tag style.
            format = CURRENT_DEFAULT_FORMAT;
        }

        return renderFormat(format, tokenParts, sender, recipient);
    }

    private Message buildSenderPart(UUID senderId, String displayName, String nickColor) {
        String gradientEnd = playerDataManager.getNickGradientEnd(senderId);
        if (gradientEnd != null && nickColor != null) {
            return createGradientMessage(displayName, nickColor, gradientEnd);
        }
        return Message.raw(displayName).color(nickColor);
    }

    private Message buildMessagePart(Channel channel, UUID senderId, String message, boolean isMentioned) {
        if (isMentioned && config.isMentionsEnabled()) {
            return Message.raw(message).color(config.getMentionColor()).bold(true);
        }

        String msgColor = playerDataManager.getMsgColor(senderId);
        String msgGradientEnd = playerDataManager.getMsgGradientEnd(senderId);
        if (msgColor != null && msgGradientEnd != null) {
            return createGradientMessage(message, msgColor, msgGradientEnd);
        }
        if (msgColor != null) {
            return Message.raw(message).color(msgColor);
        }
        return Message.raw(message).color(channel.getEffectiveMessageColorHex());
    }

    private Message renderFormat(String format, Map<String, Message> tokenParts, PlayerRef sender, PlayerRef recipient) {
        List<Message> parts = new ArrayList<>();
        Matcher matcher = FORMAT_TOKEN_PATTERN.matcher(format);
        int last = 0;

        while (matcher.find()) {
            appendLiteralPart(parts, format.substring(last, matcher.start()), sender, recipient);

            Message tokenPart = tokenParts.get(matcher.group(1));
            if (tokenPart != null) {
                parts.add(tokenPart);
            }

            last = matcher.end();
        }

        appendLiteralPart(parts, format.substring(last), sender, recipient);

        if (parts.isEmpty()) {
            return Message.raw("");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return Message.join(parts.toArray(new Message[0]));
    }

    private void appendLiteralPart(List<Message> parts, String literal, PlayerRef sender, PlayerRef recipient) {
        if (literal == null || literal.isEmpty()) {
            return;
        }

        String resolved = applyPapi(sender, recipient, literal);
        if (!resolved.isEmpty()) {
            parts.add(parseColoredString(resolved));
        }
    }

    private String applyPapi(PlayerRef sender, PlayerRef recipient, String text) {
        if (text == null || text.isEmpty() || papi == null) {
            return text == null ? "" : text;
        }

        try {
            String resolved = papi.setPlaceholders(sender, text);
            if (recipient != null) {
                resolved = papi.setRelationalPlaceholders(sender, recipient, resolved);
            }
            return resolved == null ? text : resolved;
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Parse a string containing HyperPerms color codes (&c, &6, &#RRGGBB, etc.)
     * and convert to Hytale Message with proper coloring.
     */
    private Message parseColoredString(String text) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }

        List<Message> parts = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        String currentColor = "#FFFFFF";
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            // Handle both & and § (section sign) color code prefixes
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length()) {
                // Flush current text with current formatting
                if (currentText.length() > 0) {
                    Message part = Message.raw(currentText.toString()).color(currentColor);
                    if (bold) part = part.bold(true);
                    if (italic) part = part.italic(true);
                    parts.add(part);
                    currentText = new StringBuilder();
                }

                char next = text.charAt(i + 1);

                // Check for hex color: &#RRGGBB or §#RRGGBB
                if (next == '#' && i + 8 <= text.length()) {
                    String hex = text.substring(i + 1, i + 8);
                    if (hex.matches("#[0-9A-Fa-f]{6}")) {
                        currentColor = hex.toUpperCase();
                        i += 8;
                        continue;
                    }
                }

                // Check for Minecraft extended hex: §x§R§R§G§G§B§B (14 chars total)
                if ((next == 'x' || next == 'X') && i + 14 <= text.length()) {
                    String extended = text.substring(i, i + 14);
                    if (extended.matches("[\u00A7&][xX]([\u00A7&][0-9A-Fa-f]){6}")) {
                        // Extract hex digits
                        StringBuilder hexBuilder = new StringBuilder("#");
                        for (int j = 2; j < 14; j += 2) {
                            hexBuilder.append(extended.charAt(j + 1));
                        }
                        currentColor = hexBuilder.toString().toUpperCase();
                        i += 14;
                        continue;
                    }
                }

                // Check for legacy color/format codes
                String newColor = legacyColorToHex(next);
                if (newColor != null) {
                    currentColor = newColor;
                    i += 2;
                    continue;
                }

                // Check for formatting codes
                switch (Character.toLowerCase(next)) {
                    case 'l': bold = true; i += 2; continue;
                    case 'o': italic = true; i += 2; continue;
                    case 'n': underline = true; i += 2; continue;
                    case 'm': strikethrough = true; i += 2; continue;
                    case 'r': // Reset
                        currentColor = "#FFFFFF";
                        bold = false;
                        italic = false;
                        underline = false;
                        strikethrough = false;
                        i += 2;
                        continue;
                }
            }

            currentText.append(c);
            i++;
        }

        // Flush remaining text
        if (currentText.length() > 0) {
            Message part = Message.raw(currentText.toString()).color(currentColor);
            if (bold) part = part.bold(true);
            if (italic) part = part.italic(true);
            parts.add(part);
        }

        if (parts.isEmpty()) {
            return Message.raw("");
        } else if (parts.size() == 1) {
            return parts.get(0);
        } else {
            return Message.join(parts.toArray(new Message[0]));
        }
    }

    /**
     * Convert Minecraft legacy color code to hex color.
     */
    private String legacyColorToHex(char code) {
        return switch (Character.toLowerCase(code)) {
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
            default -> null;
        };
    }

    /**
     * Create a gradient-colored message where each character transitions from startColor to endColor.
     */
    private Message createGradientMessage(String text, String startColor, String endColor) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }
        if (text.length() == 1) {
            return Message.raw(text).color(startColor);
        }

        // Parse hex colors
        int startR = Integer.parseInt(startColor.substring(1, 3), 16);
        int startG = Integer.parseInt(startColor.substring(3, 5), 16);
        int startB = Integer.parseInt(startColor.substring(5, 7), 16);
        int endR = Integer.parseInt(endColor.substring(1, 3), 16);
        int endG = Integer.parseInt(endColor.substring(3, 5), 16);
        int endB = Integer.parseInt(endColor.substring(5, 7), 16);

        List<Message> parts = new ArrayList<>();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            float ratio = (float) i / (len - 1);
            int r = Math.round(startR + (endR - startR) * ratio);
            int g = Math.round(startG + (endG - startG) * ratio);
            int b = Math.round(startB + (endB - startB) * ratio);
            String color = String.format("#%02X%02X%02X", r, g, b);
            parts.add(Message.raw(String.valueOf(text.charAt(i))).color(color));
        }

        return Message.join(parts.toArray(new Message[0]));
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

        // Use display names (nicknames if set)
        String senderDisplayName = playerDataManager.getDisplayName(senderId);
        String recipientDisplayName = playerDataManager.getDisplayName(recipientId);

        // Get nick colors if set
        String senderColor = playerDataManager.getDisplayColor(senderId);
        String recipientColor = playerDataManager.getDisplayColor(recipientId);
        if (senderColor == null) senderColor = "#55FF55";
        if (recipientColor == null) recipientColor = "#55FF55";

        // Message to recipient: [From SenderName] message
        Message toRecipient = Message.join(
            Message.raw("[From ").color("#AAAAAA"),
            Message.raw(senderDisplayName).color(senderColor),
            Message.raw("] ").color("#AAAAAA"),
            Message.raw(message).color("#FFFFFF")
        );

        // Message to sender: [To RecipientName] message
        Message toSender = Message.join(
            Message.raw("[To ").color("#AAAAAA"),
            Message.raw(recipientDisplayName).color(recipientColor),
            Message.raw("] ").color("#AAAAAA"),
            Message.raw(message).color("#FFFFFF")
        );

        recipient.sendMessage(toRecipient);
        sender.sendMessage(toSender);

        // Update last message from for reply functionality
        playerDataManager.setLastMessageFrom(recipientId, senderId);

        // Log PM (use real usernames for logs)
        plugin.getLogger().at(Level.INFO).log("[PM] %s -> %s: %s", sender.getUsername(), recipient.getUsername(), message);
    }
}
