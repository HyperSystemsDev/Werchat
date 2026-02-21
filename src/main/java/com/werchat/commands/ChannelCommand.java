package com.werchat.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.integration.papi.PAPIIntegration;
import com.werchat.storage.PlayerDataManager;
import com.werchat.ui.ChannelSettingsPage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /ch command handler
 */
public class ChannelCommand extends CommandBase {

    private final WerchatPlugin plugin;
    private final ChannelManager channelManager;
    private final PlayerDataManager playerDataManager;
    private final PAPIIntegration papi;
    private static final String CHANNEL_OWNER_COLOR = "#FFAA00";
    private static final String CHANNEL_MODERATOR_COLOR = "#55FF55";
    private static final String CHANNEL_MEMBER_COLOR = "#FFFFFF";
    private static final String SETTINGS_TAB_MAIN = "main";
    private static final String SETTINGS_TAB_CHANNELS = "channels";
    private static final String SETTINGS_TAB_HELP = "help";

    /**
     * Check if sender has a werchat permission, including wildcard support.
     */
    private boolean hasWerchatPermission(CommandContext ctx, String permission) {
        UUID playerId = ctx.sender().getUuid();
        return hasPermission(playerId, permission);
    }

    private boolean hasPermission(UUID playerId, String permission) {
        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, permission)
            || perms.hasPermission(playerId, "werchat.*")
            || perms.hasPermission(playerId, "*");
    }

    private boolean enforceChannelPermissions() {
        return plugin.getConfig().isEnforceChannelPermissions();
    }

    private boolean hasChannelJoinPermission(UUID playerId, Channel channel) {
        return hasPermission(playerId, channel.getJoinPermission());
    }

    private boolean hasChannelReadPermission(UUID playerId, Channel channel) {
        return hasPermission(playerId, channel.getReadPermission());
    }

    /**
     * Check if sender has ANY admin/management permission.
     * Used to decide whether to show admin commands in help.
     */
    private boolean hasAnyAdminPermission(CommandContext ctx) {
        UUID playerId = ctx.sender().getUuid();
        PermissionsModule perms = PermissionsModule.get();
        if (perms.hasPermission(playerId, "*") || perms.hasPermission(playerId, "werchat.*")) return true;
        String[] adminPerms = {"werchat.create", "werchat.remove", "werchat.color", "werchat.nick",
            "werchat.password", "werchat.rename", "werchat.mod", "werchat.distance",
            "werchat.ban", "werchat.mute", "werchat.world", "werchat.description",
            "werchat.motd", "werchat.reload"};
        for (String perm : adminPerms) {
            if (perms.hasPermission(playerId, perm)) return true;
        }
        return false;
    }

    public ChannelCommand(WerchatPlugin plugin) {
        super("ch", "Channel chat system", false);
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.papi = PAPIIntegration.register(plugin);

        // Available to all players in Adventure mode
        this.setPermissionGroup(GameMode.Adventure);

        // Allow any arguments - we parse them manually
        this.setAllowsExtraArguments(true);

        this.addAliases("channel", "chat", "werchat");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (!ctx.isPlayer()) {
            return;
        }

        UUID playerId = ctx.sender().getUuid();
        PlayerRef player = playerDataManager.getOnlinePlayer(playerId);
        if (player == null) {
            ctx.sendMessage(Message.raw("Player not found").color("#FF5555"));
            return;
        }

        // Parse raw input: "ch help" -> ["help"], "ch join global" -> ["join", "global"]
        String input = ctx.getInputString();
        String[] parts = input.trim().split("\\s+");

        // parts[0] is "ch", rest are arguments
        String cmd = parts.length > 1 ? parts[1].toLowerCase() : "";
        String arg1 = parts.length > 2 ? parts[2] : null;
        String arg2 = parts.length > 3 ? parts[3] : null;
        String arg3 = parts.length > 4 ? parts[4] : null;
        String arg4 = parts.length > 5 ? parts[5] : null;
        String argRest = joinArgs(parts, 3);

        // No args = open settings entry point
        if (cmd.isEmpty()) {
            openSettings(ctx);
            return;
        }

        // Check for known subcommands first
        switch (cmd) {
            case "help", "?" -> { showHelp(ctx); return; }
            case "settings" -> { openSettings(ctx); return; }
            case "reload" -> {
                if (!hasWerchatPermission(ctx, "werchat.reload")) {
                    ctx.sendMessage(Message.raw("You don't have permission to reload Werchat").color("#FF5555"));
                    return;
                }
                reloadData(ctx);
                return;
            }
            case "list", "l" -> {
                if (!hasWerchatPermission(ctx, "werchat.list")) {
                    ctx.sendMessage(Message.raw("You don't have permission to list channels").color("#FF5555"));
                    return;
                }
                openSettings(ctx, SETTINGS_TAB_CHANNELS);
                return;
            }
            case "join", "j" -> {
                if (!hasWerchatPermission(ctx, "werchat.join")) {
                    ctx.sendMessage(Message.raw("You don't have permission to join channels").color("#FF5555"));
                    return;
                }
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch join <channel>").color("#FF5555"));
                    return;
                }
                joinChannel(ctx, playerId, arg1, arg2);
                return;
            }
            case "leave" -> {
                if (!hasWerchatPermission(ctx, "werchat.leave")) {
                    ctx.sendMessage(Message.raw("You don't have permission to leave channels").color("#FF5555"));
                    return;
                }
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch leave <channel>").color("#FF5555"));
                    return;
                }
                leaveChannel(ctx, playerId, arg1);
                return;
            }
            case "create" -> {
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch create <name> [nick]").color("#FF5555"));
                    return;
                }
                createChannel(ctx, playerId, arg1, arg2);
                return;
            }
            case "who", "w" -> {
                if (!hasWerchatPermission(ctx, "werchat.who")) {
                    ctx.sendMessage(Message.raw("You don't have permission to view channel members").color("#FF5555"));
                    return;
                }
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch who <channel>").color("#FF5555"));
                    return;
                }
                showMembers(ctx, playerId, arg1);
                return;
            }
            case "color" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch color <channel> <#tag> [#text]").color("#FF5555"));
                    return;
                }
                setChannelColor(ctx, playerId, arg1, arg2, arg3);
                return;
            }
            case "password", "pass" -> {
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch password <channel> [password]").color("#FF5555"));
                    return;
                }
                setChannelPassword(ctx, playerId, arg1, arg2);
                return;
            }
            case "description", "desc" -> {
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch description <channel> <text|on|off|clear>").color("#FF5555"));
                    return;
                }
                setChannelDescription(ctx, playerId, arg1, argRest);
                return;
            }
            case "motd" -> {
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch motd <channel> <text|on|off|clear>").color("#FF5555"));
                    return;
                }
                setChannelMotd(ctx, playerId, arg1, argRest);
                return;
            }
            case "nick" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch nick <channel> <nick>").color("#FF5555"));
                    return;
                }
                setChannelNick(ctx, playerId, arg1, arg2);
                return;
            }
            case "info" -> {
                if (!hasWerchatPermission(ctx, "werchat.info")) {
                    ctx.sendMessage(Message.raw("You don't have permission to view channel info").color("#FF5555"));
                    return;
                }
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch info <channel>").color("#FF5555"));
                    return;
                }
                showChannelInfo(ctx, playerId, arg1);
                return;
            }
            case "rename" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch rename <channel> <newname>").color("#FF5555"));
                    return;
                }
                renameChannel(ctx, playerId, arg1, arg2);
                return;
            }
            case "remove", "delete", "del" -> {
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch remove <channel>").color("#FF5555"));
                    return;
                }
                removeChannel(ctx, playerId, arg1);
                return;
            }
            case "mod", "moderator" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch mod <channel> <player>").color("#FF5555"));
                    return;
                }
                addModerator(ctx, playerId, arg1, arg2);
                return;
            }
            case "unmod" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch unmod <channel> <player>").color("#FF5555"));
                    return;
                }
                removeModerator(ctx, playerId, arg1, arg2);
                return;
            }
            case "distance", "range" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch distance <channel> <blocks>").color("#FF5555"));
                    ctx.sendMessage(Message.raw("Use 0 for global (unlimited range)").color("#AAAAAA"));
                    return;
                }
                setChannelDistance(ctx, playerId, arg1, arg2);
                return;
            }
            case "ban" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch ban <channel> <player>").color("#FF5555"));
                    return;
                }
                banPlayer(ctx, playerId, arg1, arg2);
                return;
            }
            case "unban" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch unban <channel> <player>").color("#FF5555"));
                    return;
                }
                unbanPlayer(ctx, playerId, arg1, arg2);
                return;
            }
            case "mute" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch mute <channel> <player>").color("#FF5555"));
                    return;
                }
                mutePlayer(ctx, playerId, arg1, arg2);
                return;
            }
            case "unmute" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch unmute <channel> <player>").color("#FF5555"));
                    return;
                }
                unmutePlayer(ctx, playerId, arg1, arg2);
                return;
            }
            case "world" -> {
                if (arg1 == null || arg2 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch world <channel> add|remove <world>").color("#FF5555"));
                    ctx.sendMessage(Message.raw("       /ch world <channel> none").color("#FF5555"));
                    return;
                }
                setChannelWorld(ctx, playerId, arg1, arg2, arg3);
                return;
            }
            case "playernick", "pnick", "nickname" -> {
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch playernick <name> [#color] [#gradientEnd]").color("#FF5555"));
                    ctx.sendMessage(Message.raw("Admin: /ch playernick <player> <name> [#color] [#gradient]").color("#FF5555"));
                    ctx.sendMessage(Message.raw("Use /ch playernick reset to clear").color("#AAAAAA"));
                    return;
                }
                setPlayerNickname(ctx, playerId, arg1, arg2, arg3, arg4);
                return;
            }
            case "msgcolor", "messagecolor", "chatcolor" -> {
                if (arg1 == null) {
                    ctx.sendMessage(Message.raw("Usage: /ch msgcolor <#color> [#gradientEnd]").color("#FF5555"));
                    ctx.sendMessage(Message.raw("Admin: /ch msgcolor <player> <#color> [#gradient]").color("#FF5555"));
                    ctx.sendMessage(Message.raw("Use /ch msgcolor reset to clear").color("#AAAAAA"));
                    return;
                }
                setMessageColor(ctx, playerId, arg1, arg2, arg3);
                return;
            }
        }

        // Not a known command - try to switch to a channel by name/nick
        if (!hasWerchatPermission(ctx, "werchat.switch")) {
            ctx.sendMessage(Message.raw("You don't have permission to switch channels").color("#FF5555"));
            return;
        }
        switchToChannel(ctx, playerId, cmd);
    }

    private void switchToChannel(CommandContext ctx, UUID playerId, String input) {
        Channel channel = channelManager.findChannel(input);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Unknown channel: " + input).color("#FF5555"));
            ctx.sendMessage(Message.raw("Use /ch list to see available channels").color("#AAAAAA"));
            return;
        }

        if (enforceChannelPermissions() && !hasChannelReadPermission(playerId, channel)) {
            ctx.sendMessage(Message.raw("You don't have permission to read " + channel.getName()).color("#FF5555"));
            return;
        }

        // Auto-join if not a member
        if (!channel.isMember(playerId)) {
            if (channel.isBanned(playerId)) {
                ctx.sendMessage(Message.raw("You are banned from " + channel.getName()).color("#FF5555"));
                return;
            }
            if (enforceChannelPermissions() && !hasChannelJoinPermission(playerId, channel)) {
                ctx.sendMessage(Message.raw("You don't have permission to join " + channel.getName()).color("#FF5555"));
                return;
            }
            if (channel.hasPassword()) {
                ctx.sendMessage(Message.raw("Channel requires password: /ch join " + channel.getName() + " <password>").color("#FF5555"));
                return;
            }
            channel.addMember(playerId);
        }

        // Focus on this channel
        playerDataManager.setFocusedChannel(playerId, channel.getName());
        channelManager.sendChannelMotd(playerId, channel);
        ctx.sendMessage(Message.join(
            Message.raw("Now chatting in ").color("#AAAAAA"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void showHelp(CommandContext ctx) {
        openSettings(ctx, SETTINGS_TAB_HELP);
    }

    private void openSettings(CommandContext ctx) {
        openSettings(ctx, null);
    }

    private void openSettings(CommandContext ctx, String initialTab) {
        Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
        if (playerRef == null) {
            ctx.sendMessage(Message.raw("Player not found").color("#FF5555"));
            return;
        }

        Store<EntityStore> store = playerRef.getStore();
        if (store == null) {
            ctx.sendMessage(Message.raw("Player state unavailable").color("#FF5555"));
            return;
        }

        EntityStore entityStore = store.getExternalData();
        World world = entityStore != null ? entityStore.getWorld() : null;
        if (world == null) {
            ctx.sendMessage(Message.raw("Player state unavailable").color("#FF5555"));
            return;
        }

        world.execute(() -> {
            try {
                Player player = store.getComponent(playerRef, Player.getComponentType());
                if (player == null) {
                    ctx.sendMessage(Message.raw("Player state unavailable").color("#FF5555"));
                    return;
                }

                PlayerRef pagePlayerRef = store.getComponent(playerRef, PlayerRef.getComponentType());
                if (pagePlayerRef == null) {
                    ctx.sendMessage(Message.raw("Player state unavailable").color("#FF5555"));
                    return;
                }

                player.getPageManager().openCustomPage(
                    playerRef,
                    store,
                    new ChannelSettingsPage(plugin, pagePlayerRef, initialTab)
                );
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).log("Failed to open Werchat settings UI: %s", e.getMessage());
                ctx.sendMessage(Message.raw("Failed to open settings UI. Check server logs.").color("#FF5555"));
            }
        });
    }

    private void reloadData(CommandContext ctx) {
        try {
            boolean externalChannelEdits = channelManager.hasExternalChannelDataEdits();
            boolean externalNicknameEdits = playerDataManager.hasExternalNicknameDataEdits();

            if (externalChannelEdits) {
                channelManager.discardPendingSave();
                plugin.getLogger().at(Level.INFO).log("Detected external channel file edits; reloading from disk without pre-save");
            } else {
                channelManager.flushPendingSaveNow();
            }

            if (externalNicknameEdits) {
                playerDataManager.discardPendingNicknameSave();
                plugin.getLogger().at(Level.INFO).log("Detected external nicknames file edits; reloading from disk without pre-save");
            } else {
                playerDataManager.flushPendingNicknameSaveNow();
            }

            plugin.getConfig().load();
            boolean channelsLoaded = channelManager.loadChannels();
            playerDataManager.loadNicknames();
            reconcileFocusedChannelsAfterReload();
            if (channelsLoaded) {
                ctx.sendMessage(Message.raw("Werchat config and channels reloaded.").color("#55FF55"));
            } else {
                ctx.sendMessage(Message.raw("Reload completed with warnings. Keeping last known channel state.").color("#FFAA00"));
            }
        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).log("Werchat reload failed: %s", e.getMessage());
            ctx.sendMessage(Message.raw("Reload failed. Check server logs.").color("#FF5555"));
        }
    }

    private void reconcileFocusedChannelsAfterReload() {
        Channel defaultChannel = channelManager.getDefaultChannel();
        if (defaultChannel == null) {
            return;
        }

        for (PlayerRef online : playerDataManager.getOnlinePlayers()) {
            UUID playerId = online.getUuid();
            Channel focused = channelManager.getChannel(playerDataManager.getFocusedChannel(playerId));
            if (focused != null && focused.isMember(playerId)) {
                continue;
            }

            if (!defaultChannel.isBanned(playerId)) {
                defaultChannel.addMember(playerId);
                playerDataManager.setFocusedChannel(playerId, defaultChannel.getName());
            }
        }
    }

    private void listChannels(CommandContext ctx, UUID playerId) {
        ctx.sendMessage(Message.raw("=== Channels ===").color("#55FF55"));
        String focused = playerDataManager.getFocusedChannel(playerId);
        for (Channel ch : channelManager.getAllChannels()) {
            if (enforceChannelPermissions() && !hasChannelReadPermission(playerId, ch) && !ch.isMember(playerId)) {
                continue;
            }
            String status = ch.isMember(playerId) ? " [Joined]" : "";
            if (ch.getName().equalsIgnoreCase(focused)) status += " [*]";
            if (ch.isWorldRestricted()) status += " [W:" + ch.getWorldsDisplay() + "]";
            String displayNick = applyPapi(playerId, ch.getNick());
            ctx.sendMessage(Message.raw("[" + displayNick + "] " + ch.getName() + " (" + ch.getMemberCount() + ")" + status).color(ch.getColorHex()));
            if (plugin.getConfig().isChannelDescriptionsEnabled() && ch.isDescriptionEnabled() && ch.hasDescription()) {
                ctx.sendMessage(Message.raw("   " + applyPapi(playerId, ch.getDescription())).color("#9EB3CC"));
            }
        }
    }

    private void joinChannel(CommandContext ctx, UUID playerId, String channelName, String password) {
        Channel channel = channelManager.getChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF0000"));
            return;
        }
        if (enforceChannelPermissions()) {
            if (!hasChannelJoinPermission(playerId, channel)) {
                ctx.sendMessage(Message.raw("You don't have permission to join this channel").color("#FF5555"));
                return;
            }
            if (!hasChannelReadPermission(playerId, channel)) {
                ctx.sendMessage(Message.raw("You don't have permission to read this channel").color("#FF5555"));
                return;
            }
        }
        if (channel.isBanned(playerId)) {
            ctx.sendMessage(Message.raw("You are banned from this channel").color("#FF0000"));
            return;
        }
        if (channel.isMember(playerId)) {
            ctx.sendMessage(Message.raw("Already in channel: " + channel.getName()).color("#FFFF55"));
            return;
        }
        if (channel.hasPassword() && !channel.checkPassword(password)) {
            ctx.sendMessage(Message.raw("Wrong password").color("#FF0000"));
            return;
        }
        channel.addMember(playerId);
        playerDataManager.setFocusedChannel(playerId, channel.getName());
        channelManager.sendChannelMotd(playerId, channel);
        ctx.sendMessage(Message.raw("Joined and focused: " + channel.getName()).color("#55FF55"));
    }

    private void leaveChannel(CommandContext ctx, UUID playerId, String channelName) {
        Channel channel = channelManager.getChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF0000"));
            return;
        }
        if (!channel.isMember(playerId)) {
            ctx.sendMessage(Message.raw("Not in channel: " + channel.getName()).color("#FFFF55"));
            return;
        }
        channel.removeMember(playerId);
        ctx.sendMessage(Message.raw("Left: " + channel.getName()).color("#55FF55"));
    }

    private void createChannel(CommandContext ctx, UUID playerId, String name, String nick) {
        // Requires werchat.create permission
        if (!hasWerchatPermission(ctx, "werchat.create")) {
            ctx.sendMessage(Message.raw("You don't have permission to create channels").color("#FF5555"));
            return;
        }
        if (channelManager.channelExists(name)) {
            ctx.sendMessage(Message.raw("Channel already exists: " + name).color("#FF0000"));
            return;
        }
        Channel channel = channelManager.createChannel(name, playerId);
        if (nick != null && !nick.isEmpty()) {
            channel.setNick(nick);
        }
        channel.addMember(playerId);
        playerDataManager.setFocusedChannel(playerId, channel.getName());
        ctx.sendMessage(Message.raw("Created channel: " + channel.getName()).color("#55FF55"));
    }

    private void showMembers(CommandContext ctx, UUID playerId, String channelName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (enforceChannelPermissions() && !hasChannelReadPermission(playerId, channel)) {
            ctx.sendMessage(Message.raw("You don't have permission to read this channel").color("#FF5555"));
            return;
        }

        ctx.sendMessage(Message.raw(channel.getName() + " - " + channel.getMemberCount() + " members").color(channel.getColorHex()));

        List<UUID> onlineMembers = new ArrayList<>();
        for (UUID memberId : channel.getMembers()) {
            PlayerRef member = playerDataManager.getOnlinePlayer(memberId);
            if (member != null) {
                onlineMembers.add(memberId);
            }
        }

        if (onlineMembers.isEmpty()) {
            ctx.sendMessage(Message.raw("  No members online").color("#AAAAAA"));
            return;
        }

        onlineMembers.sort((left, right) -> {
            int leftPriority = getChannelRankPriority(channel, left);
            int rightPriority = getChannelRankPriority(channel, right);
            if (leftPriority != rightPriority) {
                return Integer.compare(leftPriority, rightPriority);
            }
            return getOnlineName(left).compareToIgnoreCase(getOnlineName(right));
        });

        List<Message> parts = new ArrayList<>();
        parts.add(Message.raw("  Online: ").color("#55FF55"));
        for (int i = 0; i < onlineMembers.size(); i++) {
            UUID memberId = onlineMembers.get(i);
            if (i > 0) {
                parts.add(Message.raw(", ").color("#AAAAAA"));
            }
            parts.add(Message.raw(getOnlineName(memberId)).color(getChannelRankColor(channel, memberId)));
        }
        ctx.sendMessage(Message.join(parts.toArray(new Message[0])));
    }

    private int getChannelRankPriority(Channel channel, UUID memberId) {
        if (channel.getOwner() != null && channel.getOwner().equals(memberId)) {
            return 0;
        }
        if (channel.isModerator(memberId)) {
            return 1;
        }
        return 2;
    }

    private String getChannelRankColor(Channel channel, UUID memberId) {
        int priority = getChannelRankPriority(channel, memberId);
        if (priority == 0) {
            return CHANNEL_OWNER_COLOR;
        }
        if (priority == 1) {
            return CHANNEL_MODERATOR_COLOR;
        }
        return CHANNEL_MEMBER_COLOR;
    }

    private String getOnlineName(UUID memberId) {
        PlayerRef member = playerDataManager.getOnlinePlayer(memberId);
        if (member != null) {
            return member.getUsername();
        }
        return playerDataManager.getKnownName(memberId);
    }

    private String applyPapi(UUID playerId, String text) {
        if (text == null || text.isEmpty() || papi == null) {
            return text == null ? "" : text;
        }
        PlayerRef player = playerDataManager.getOnlinePlayer(playerId);
        if (player == null) {
            return text;
        }
        return applyPapi(player, text);
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

    private void setChannelColor(CommandContext ctx, UUID playerId, String channelName, String hexColor, String textHexColor) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.color") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        try {
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            channel.setColor(new java.awt.Color(r, g, b));

            if (textHexColor != null && !textHexColor.isEmpty()) {
                // Two colors: first is tag, second is message text
                String textHex = textHexColor.startsWith("#") ? textHexColor.substring(1) : textHexColor;
                int tr = Integer.parseInt(textHex.substring(0, 2), 16);
                int tg = Integer.parseInt(textHex.substring(2, 4), 16);
                int tb = Integer.parseInt(textHex.substring(4, 6), 16);
                channel.setMessageColor(new java.awt.Color(tr, tg, tb));
            } else {
                // One color: clear separate message color (tag color used for both)
                channel.setMessageColor(null);
            }

            if (channel.hasMessageColor()) {
                ctx.sendMessage(Message.join(
                    Message.raw("Tag color: ").color("#AAAAAA"),
                    Message.raw(channel.getColorHex()).color(channel.getColorHex()),
                    Message.raw("  Text color: ").color("#AAAAAA"),
                    Message.raw(channel.getMessageColorHex()).color(channel.getMessageColorHex())
                ));
            } else {
                ctx.sendMessage(Message.join(
                    Message.raw("Color set to ").color("#AAAAAA"),
                    Message.raw(channel.getColorHex()).color(channel.getColorHex())
                ));
            }
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Invalid color. Use hex format: #FF5555").color("#FF5555"));
        }
    }

    private void setChannelPassword(CommandContext ctx, UUID playerId, String channelName, String password) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.password") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        if (password == null || password.isEmpty()) {
            channel.setPassword(null);
            ctx.sendMessage(Message.raw("Password removed from " + channel.getName()).color("#55FF55"));
        } else {
            channel.setPassword(password);
            ctx.sendMessage(Message.raw("Password set for " + channel.getName()).color("#55FF55"));
        }
    }

    private void setChannelDescription(CommandContext ctx, UUID playerId, String channelName, String value) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.description") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }

        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /ch description <channel> <text|on|off|clear>").color("#FF5555"));
            return;
        }

        if (input.equalsIgnoreCase("on")) {
            channel.setDescriptionEnabled(true);
            ctx.sendMessage(Message.raw("Description enabled for " + channel.getName()).color("#55FF55"));
            return;
        }
        if (input.equalsIgnoreCase("off")) {
            channel.setDescriptionEnabled(false);
            ctx.sendMessage(Message.raw("Description disabled for " + channel.getName()).color("#55FF55"));
            return;
        }
        if (input.equalsIgnoreCase("clear") || input.equalsIgnoreCase("reset")) {
            channel.setDescription("");
            channel.setDescriptionEnabled(false);
            ctx.sendMessage(Message.raw("Description cleared for " + channel.getName()).color("#55FF55"));
            return;
        }

        channel.setDescription(input);
        channel.setDescriptionEnabled(true);
        ctx.sendMessage(Message.join(
            Message.raw("Description set for ").color("#55FF55"),
            Message.raw(channel.getName()).color(channel.getColorHex()),
            Message.raw(": ").color("#55FF55"),
            Message.raw(applyPapi(playerId, channel.getDescription())).color("#FFFFFF")
        ));
    }

    private void setChannelMotd(CommandContext ctx, UUID playerId, String channelName, String value) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.motd") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }

        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /ch motd <channel> <text|on|off|clear>").color("#FF5555"));
            return;
        }

        if (input.equalsIgnoreCase("on")) {
            channel.setMotdEnabled(true);
            ctx.sendMessage(Message.raw("MOTD enabled for " + channel.getName()).color("#55FF55"));
            return;
        }
        if (input.equalsIgnoreCase("off")) {
            channel.setMotdEnabled(false);
            ctx.sendMessage(Message.raw("MOTD disabled for " + channel.getName()).color("#55FF55"));
            return;
        }
        if (input.equalsIgnoreCase("clear") || input.equalsIgnoreCase("reset")) {
            channel.setMotd("");
            channel.setMotdEnabled(false);
            ctx.sendMessage(Message.raw("MOTD cleared for " + channel.getName()).color("#55FF55"));
            return;
        }

        channel.setMotd(input);
        channel.setMotdEnabled(true);
        ctx.sendMessage(Message.join(
            Message.raw("MOTD set for ").color("#55FF55"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void setChannelNick(CommandContext ctx, UUID playerId, String channelName, String nick) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.nick") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        channel.setNick(nick);
        ctx.sendMessage(Message.raw("Nick set to: " + applyPapi(playerId, nick)).color("#55FF55"));
    }

    private void showChannelInfo(CommandContext ctx, UUID playerId, String channelName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (enforceChannelPermissions() && !hasChannelReadPermission(playerId, channel)) {
            ctx.sendMessage(Message.raw("You don't have permission to read this channel").color("#FF5555"));
            return;
        }
        ctx.sendMessage(Message.raw("").color("#000000"));
        ctx.sendMessage(Message.raw("  " + channel.getName()).color(channel.getColorHex()).bold(true));

        // Show owner
        String ownerName = "None";
        if (channel.getOwner() != null) {
            PlayerRef owner = playerDataManager.getOnlinePlayer(channel.getOwner());
            ownerName = owner != null ? owner.getUsername() : channel.getOwner().toString().substring(0, 8) + "...";
        }
        ctx.sendMessage(Message.join(
            Message.raw("  Owner: ").color("#AAAAAA"),
            Message.raw(ownerName).color("#FFAA00")
        ));

        ctx.sendMessage(Message.join(
            Message.raw("  Nick: ").color("#AAAAAA"),
            Message.raw(applyPapi(playerId, channel.getNick())).color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Tag Color: ").color("#AAAAAA"),
            Message.raw(channel.getColorHex()).color(channel.getColorHex())
        ));
        if (channel.hasMessageColor()) {
            ctx.sendMessage(Message.join(
                Message.raw("  Text Color: ").color("#AAAAAA"),
                Message.raw(channel.getMessageColorHex()).color(channel.getMessageColorHex())
            ));
        }
        ctx.sendMessage(Message.join(
            Message.raw("  Members: ").color("#AAAAAA"),
            Message.raw(String.valueOf(channel.getMemberCount())).color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Password: ").color("#AAAAAA"),
            Message.raw(channel.hasPassword() ? "Yes" : "No").color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Range: ").color("#AAAAAA"),
            Message.raw(channel.isGlobal() ? "Global" : channel.getDistance() + " blocks").color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Worlds: ").color("#AAAAAA"),
            Message.raw(channel.getWorldsDisplay()).color("#FFFFFF")
        ));
        if (plugin.getConfig().isChannelDescriptionsEnabled()) {
            ctx.sendMessage(Message.join(
                Message.raw("  Description: ").color("#AAAAAA"),
                Message.raw(channel.isDescriptionEnabled() && channel.hasDescription()
                    ? applyPapi(playerId, channel.getDescription())
                    : "Disabled").color("#FFFFFF")
            ));
        }
        if (plugin.getConfig().isChannelMotdSystemEnabled()) {
            ctx.sendMessage(Message.join(
                Message.raw("  MOTD: ").color("#AAAAAA"),
                Message.raw(channel.isMotdEnabled() && channel.hasMotd()
                    ? applyPapi(playerId, channel.getMotd())
                    : "Disabled").color("#FFFFFF")
            ));
        }

        // Show moderators
        StringBuilder modNames = new StringBuilder();
        for (UUID modId : channel.getModerators()) {
            PlayerRef mod = playerDataManager.getOnlinePlayer(modId);
            if (modNames.length() > 0) modNames.append(", ");
            modNames.append(mod != null ? mod.getUsername() : modId.toString().substring(0, 8));
        }
        ctx.sendMessage(Message.join(
            Message.raw("  Moderators: ").color("#AAAAAA"),
            Message.raw(modNames.length() > 0 ? modNames.toString() : "None").color("#55FF55")
        ));
        ctx.sendMessage(Message.raw("").color("#000000"));
    }

    private void removeChannel(CommandContext ctx, UUID playerId, String channelName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.remove") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        if (channel.isDefault()) {
            ctx.sendMessage(Message.raw("Cannot delete the default channel").color("#FF5555"));
            return;
        }
        String name = channel.getName();
        if (channelManager.deleteChannel(name)) {
            ctx.sendMessage(Message.raw("Deleted channel: " + name).color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw("Cannot delete channel (must have at least one channel)").color("#FF5555"));
        }
    }

    private void renameChannel(CommandContext ctx, UUID playerId, String channelName, String newName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.rename") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        if (channelManager.channelExists(newName)) {
            ctx.sendMessage(Message.raw("A channel with that name already exists").color("#FF5555"));
            return;
        }
        String oldName = channel.getName();
        if (channelManager.renameChannel(oldName, newName)) {
            ctx.sendMessage(Message.join(
                Message.raw("Channel renamed: ").color("#55FF55"),
                Message.raw(oldName).color("#AAAAAA"),
                Message.raw(" -> ").color("#55FF55"),
                Message.raw(newName).color(channel.getColorHex())
            ));
        } else {
            ctx.sendMessage(Message.raw("Failed to rename channel").color("#FF5555"));
        }
    }

    private void addModerator(CommandContext ctx, UUID playerId, String channelName, String playerName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.mod") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        // Find player by name
        PlayerRef target = playerDataManager.findPlayerByName(playerName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color("#FF5555"));
            return;
        }
        UUID targetId = target.getUuid();
        if (channel.isModerator(targetId)) {
            ctx.sendMessage(Message.raw(playerName + " is already a moderator").color("#FFFF55"));
            return;
        }
        channel.addModerator(targetId);
        ctx.sendMessage(Message.join(
            Message.raw(playerName).color("#FFFFFF"),
            Message.raw(" is now a moderator of ").color("#55FF55"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void removeModerator(CommandContext ctx, UUID playerId, String channelName, String playerName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.mod") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        // Find player by name
        PlayerRef target = playerDataManager.findPlayerByName(playerName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color("#FF5555"));
            return;
        }
        UUID targetId = target.getUuid();
        if (channel.getOwner() != null && channel.getOwner().equals(targetId)) {
            ctx.sendMessage(Message.raw("Cannot remove the channel owner as moderator").color("#FF5555"));
            return;
        }
        if (!channel.isModerator(targetId)) {
            ctx.sendMessage(Message.raw(playerName + " is not a moderator").color("#FFFF55"));
            return;
        }
        channel.removeModerator(targetId);
        ctx.sendMessage(Message.join(
            Message.raw(playerName).color("#FFFFFF"),
            Message.raw(" is no longer a moderator of ").color("#55FF55"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void setChannelDistance(CommandContext ctx, UUID playerId, String channelName, String distanceStr) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.distance") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        try {
            int distance = Integer.parseInt(distanceStr);
            if (distance < 0) {
                ctx.sendMessage(Message.raw("Distance must be 0 or greater").color("#FF5555"));
                return;
            }
            channel.setDistance(distance);
            if (distance == 0) {
                ctx.sendMessage(Message.join(
                    Message.raw(channel.getName()).color(channel.getColorHex()),
                    Message.raw(" is now a global channel").color("#55FF55")
                ));
            } else {
                ctx.sendMessage(Message.join(
                    Message.raw(channel.getName()).color(channel.getColorHex()),
                    Message.raw(" range set to ").color("#55FF55"),
                    Message.raw(distance + " blocks").color("#FFFFFF")
                ));
            }
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("Invalid number: " + distanceStr).color("#FF5555"));
        }
    }

    private void banPlayer(CommandContext ctx, UUID playerId, String channelName, String playerName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.ban") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        PlayerRef target = playerDataManager.findPlayerByName(playerName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color("#FF5555"));
            return;
        }
        UUID targetId = target.getUuid();
        if (channel.isBanned(targetId)) {
            ctx.sendMessage(Message.raw(playerName + " is already banned").color("#FFFF55"));
            return;
        }
        channel.ban(targetId);

        // Notify the banned player
        String banMsg = plugin.getConfig().getBanMessage().replace("{channel}", channel.getName());
        banMsg = applyPapi(target, banMsg);
        target.sendMessage(Message.raw(banMsg).color("#FF5555"));

        ctx.sendMessage(Message.join(
            Message.raw(playerName).color("#FFFFFF"),
            Message.raw(" has been banned from ").color("#FF5555"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void unbanPlayer(CommandContext ctx, UUID playerId, String channelName, String playerName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.ban") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        PlayerRef target = playerDataManager.findPlayerByName(playerName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color("#FF5555"));
            return;
        }
        UUID targetId = target.getUuid();
        if (!channel.isBanned(targetId)) {
            ctx.sendMessage(Message.raw(playerName + " is not banned").color("#FFFF55"));
            return;
        }
        channel.unban(targetId);
        ctx.sendMessage(Message.join(
            Message.raw(playerName).color("#FFFFFF"),
            Message.raw(" has been unbanned from ").color("#55FF55"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void mutePlayer(CommandContext ctx, UUID playerId, String channelName, String playerName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.mute") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        PlayerRef target = playerDataManager.findPlayerByName(playerName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color("#FF5555"));
            return;
        }
        UUID targetId = target.getUuid();
        if (channel.isMuted(targetId)) {
            ctx.sendMessage(Message.raw(playerName + " is already muted").color("#FFFF55"));
            return;
        }
        channel.mute(targetId);

        // Notify the muted player
        String muteMsg = plugin.getConfig().getMuteMessage().replace("{channel}", channel.getName());
        muteMsg = applyPapi(target, muteMsg);
        target.sendMessage(Message.raw(muteMsg).color("#FF5555"));

        ctx.sendMessage(Message.join(
            Message.raw(playerName).color("#FFFFFF"),
            Message.raw(" has been muted in ").color("#FF5555"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void unmutePlayer(CommandContext ctx, UUID playerId, String channelName, String playerName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.mute") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }
        PlayerRef target = playerDataManager.findPlayerByName(playerName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color("#FF5555"));
            return;
        }
        UUID targetId = target.getUuid();
        if (!channel.isMuted(targetId)) {
            ctx.sendMessage(Message.raw(playerName + " is not muted").color("#FFFF55"));
            return;
        }
        channel.unmute(targetId);
        ctx.sendMessage(Message.join(
            Message.raw(playerName).color("#FFFFFF"),
            Message.raw(" has been unmuted in ").color("#55FF55"),
            Message.raw(channel.getName()).color(channel.getColorHex())
        ));
    }

    private void setChannelWorld(CommandContext ctx, UUID playerId, String channelName, String action, String worldName) {
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null) {
            ctx.sendMessage(Message.raw("Channel not found: " + channelName).color("#FF5555"));
            return;
        }
        if (!hasWerchatPermission(ctx, "werchat.world") && !channel.isModerator(playerId)) {
            ctx.sendMessage(Message.raw("You must be a channel moderator to do that").color("#FF5555"));
            return;
        }

        if (action.equalsIgnoreCase("none") || action.equalsIgnoreCase("clear") || action.equalsIgnoreCase("off")) {
            channel.clearWorlds();
            ctx.sendMessage(Message.join(
                Message.raw(channel.getName()).color(channel.getColorHex()),
                Message.raw(" is no longer world-restricted").color("#55FF55")
            ));
        } else if (action.equalsIgnoreCase("add")) {
            if (worldName == null || worldName.isEmpty()) {
                ctx.sendMessage(Message.raw("Usage: /ch world <channel> add <world>").color("#FF5555"));
                return;
            }
            // Validate that the world exists
            try {
                World world = Universe.get().getWorld(worldName);
                if (world == null) {
                    ctx.sendMessage(Message.raw("World not found: " + worldName).color("#FF5555"));
                    ctx.sendMessage(Message.raw("Make sure the world name matches exactly").color("#AAAAAA"));
                    return;
                }
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Could not verify world: " + worldName).color("#FFAA00"));
                ctx.sendMessage(Message.raw("Adding anyway - will take effect when world is loaded").color("#AAAAAA"));
            }

            channel.addWorld(worldName);
            ctx.sendMessage(Message.join(
                Message.raw("Added world ").color("#55FF55"),
                Message.raw(worldName).color("#FFFFFF"),
                Message.raw(" to ").color("#55FF55"),
                Message.raw(channel.getName()).color(channel.getColorHex()),
                Message.raw(" (" + channel.getWorldsDisplay() + ")").color("#AAAAAA")
            ));
        } else if (action.equalsIgnoreCase("remove") || action.equalsIgnoreCase("rem")) {
            if (worldName == null || worldName.isEmpty()) {
                ctx.sendMessage(Message.raw("Usage: /ch world <channel> remove <world>").color("#FF5555"));
                return;
            }
            if (!channel.getWorlds().contains(worldName)) {
                ctx.sendMessage(Message.join(
                    Message.raw(worldName).color("#FFFFFF"),
                    Message.raw(" is not in ").color("#FF5555"),
                    Message.raw(channel.getName()).color(channel.getColorHex()),
                    Message.raw("'s world list").color("#FF5555")
                ));
                return;
            }
            channel.removeWorld(worldName);
            ctx.sendMessage(Message.join(
                Message.raw("Removed world ").color("#55FF55"),
                Message.raw(worldName).color("#FFFFFF"),
                Message.raw(" from ").color("#55FF55"),
                Message.raw(channel.getName()).color(channel.getColorHex()),
                Message.raw(" (" + channel.getWorldsDisplay() + ")").color("#AAAAAA")
            ));
        } else {
            // Backward compat: treat as single world set (e.g., /ch world Global myworld)
            try {
                World world = Universe.get().getWorld(action);
                if (world == null) {
                    ctx.sendMessage(Message.raw("Unknown action: " + action).color("#FF5555"));
                    ctx.sendMessage(Message.raw("Usage: /ch world <channel> add|remove <world>").color("#AAAAAA"));
                    ctx.sendMessage(Message.raw("       /ch world <channel> none").color("#AAAAAA"));
                    return;
                }
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Unknown action: " + action).color("#FF5555"));
                ctx.sendMessage(Message.raw("Usage: /ch world <channel> add|remove <world>").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("       /ch world <channel> none").color("#AAAAAA"));
                return;
            }

            // World name provided directly - set as the only world
            channel.setWorld(action);
            ctx.sendMessage(Message.join(
                Message.raw(channel.getName()).color(channel.getColorHex()),
                Message.raw(" is now restricted to world: ").color("#55FF55"),
                Message.raw(action).color("#FFFFFF")
            ));
        }
    }

    private static final int MAX_NICKNAME_LENGTH = 20;

    private void setPlayerNickname(CommandContext ctx, UUID playerId, String arg1, String arg2, String arg3, String arg4) {
        // Detect admin mode: if arg1 matches an online player (not self) and sender has permission
        UUID targetId = playerId;
        String targetName = null;
        String nickname;
        String color;
        String gradientEnd;

        PlayerRef targetPlayer = playerDataManager.findPlayerByName(arg1);
        boolean isAdminMode = targetPlayer != null
                && !targetPlayer.getUuid().equals(playerId)
                && hasWerchatPermission(ctx, "werchat.playernick.others");

        if (isAdminMode) {
            // Admin mode: /ch playernick <player> <name> [#color] [#gradient]
            targetId = targetPlayer.getUuid();
            targetName = targetPlayer.getUsername();
            nickname = arg2;
            color = arg3;
            gradientEnd = arg4;

            if (nickname == null) {
                ctx.sendMessage(Message.raw("Usage: /ch playernick <player> <name> [#color] [#gradient]").color("#FF5555"));
                return;
            }
        } else {
            // Self mode: /ch playernick <name> [#color] [#gradient]
            nickname = arg1;
            color = arg2;
            gradientEnd = arg3;
        }

        // Handle reset
        if (nickname.equalsIgnoreCase("reset") || nickname.equalsIgnoreCase("clear") || nickname.equalsIgnoreCase("off")) {
            playerDataManager.clearNickname(targetId);
            if (targetName != null) {
                ctx.sendMessage(Message.raw("Nickname cleared for " + targetName).color("#55FF55"));
            } else {
                ctx.sendMessage(Message.raw("Nickname cleared").color("#55FF55"));
            }
            return;
        }

        // Check permission (self mode only - admin already checked above)
        if (targetName == null && !hasWerchatPermission(ctx, "werchat.playernick")) {
            ctx.sendMessage(Message.raw("You don't have permission to set nicknames").color("#FF5555"));
            return;
        }

        // Validate length
        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            ctx.sendMessage(Message.raw("Nickname too long (max " + MAX_NICKNAME_LENGTH + " characters)").color("#FF5555"));
            return;
        }

        // Check for impersonation - can't use another player's username (admins bypass this)
        if (targetName == null) {
            for (PlayerRef online : playerDataManager.getOnlinePlayers()) {
                if (online.getUuid().equals(playerId)) continue; // Skip self
                if (online.getUsername().equalsIgnoreCase(nickname)) {
                    ctx.sendMessage(Message.raw("You cannot use another player's username as your nickname").color("#FF5555"));
                    return;
                }
            }
        }

        String prefix = targetName != null ? targetName + "'s nickname" : "Nickname";

        // Set nickname
        playerDataManager.setNickname(targetId, nickname);

        // Handle color if provided
        if (color != null && !color.isEmpty()) {
            // Check permission for colors (self mode only)
            if (targetName == null && !hasWerchatPermission(ctx, "werchat.nickcolor")) {
                ctx.sendMessage(Message.join(
                    Message.raw(prefix + " set to: ").color("#AAAAAA"),
                    Message.raw(nickname).color("#FFFFFF")
                ));
                ctx.sendMessage(Message.raw("You need werchat.nickcolor permission for colors").color("#FFAA00"));
                return;
            }

            // Validate start color
            String startHex = color.startsWith("#") ? color : "#" + color;
            if (!startHex.matches("#[0-9A-Fa-f]{6}")) {
                ctx.sendMessage(Message.raw("Invalid color format. Use #RRGGBB (e.g., #FF5555)").color("#FF5555"));
                return;
            }
            playerDataManager.setNickColor(targetId, startHex);

            // Handle gradient if second color provided
            if (gradientEnd != null && !gradientEnd.isEmpty()) {
                String endHex = gradientEnd.startsWith("#") ? gradientEnd : "#" + gradientEnd;
                if (!endHex.matches("#[0-9A-Fa-f]{6}")) {
                    ctx.sendMessage(Message.raw("Invalid gradient end color. Use #RRGGBB (e.g., #5555FF)").color("#FF5555"));
                    return;
                }
                playerDataManager.setNickGradientEnd(targetId, endHex);
                ctx.sendMessage(Message.join(
                    Message.raw(prefix + " set to: ").color("#AAAAAA"),
                    createGradientPreview(nickname, startHex, endHex)
                ));
            } else {
                // Clear any existing gradient
                playerDataManager.setNickGradientEnd(targetId, null);
                ctx.sendMessage(Message.join(
                    Message.raw(prefix + " set to: ").color("#AAAAAA"),
                    Message.raw(nickname).color(startHex)
                ));
            }
        } else {
            // Clear colors
            playerDataManager.setNickColor(targetId, null);
            playerDataManager.setNickGradientEnd(targetId, null);
            ctx.sendMessage(Message.join(
                Message.raw(prefix + " set to: ").color("#AAAAAA"),
                Message.raw(nickname).color("#FFFFFF")
            ));
        }
    }

    private Message createGradientPreview(String text, String startColor, String endColor) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }
        if (text.length() == 1) {
            return Message.raw(text).color(startColor);
        }

        int startR = Integer.parseInt(startColor.substring(1, 3), 16);
        int startG = Integer.parseInt(startColor.substring(3, 5), 16);
        int startB = Integer.parseInt(startColor.substring(5, 7), 16);
        int endR = Integer.parseInt(endColor.substring(1, 3), 16);
        int endG = Integer.parseInt(endColor.substring(3, 5), 16);
        int endB = Integer.parseInt(endColor.substring(5, 7), 16);

        java.util.List<Message> parts = new java.util.ArrayList<>();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            float ratio = (float) i / (len - 1);
            int r = Math.round(startR + (endR - startR) * ratio);
            int g = Math.round(startG + (endG - startG) * ratio);
            int b = Math.round(startB + (endB - startB) * ratio);
            String hexColor = String.format("#%02X%02X%02X", r, g, b);
            parts.add(Message.raw(String.valueOf(text.charAt(i))).color(hexColor));
        }

        return Message.join(parts.toArray(new Message[0]));
    }

    private void setMessageColor(CommandContext ctx, UUID playerId, String arg1, String arg2, String arg3) {
        // Detect if arg1 is a player name (admin mode) or a color (self mode)
        // Colors start with # or are reset keywords
        boolean isResetKeyword = arg1.equalsIgnoreCase("reset") || arg1.equalsIgnoreCase("clear") || arg1.equalsIgnoreCase("off");
        boolean isColor = arg1.startsWith("#") || isResetKeyword;

        UUID targetId;
        String targetName;
        String color;
        String gradientEnd;

        if (isColor) {
            // Self mode: /ch msgcolor <#color> [#gradient]
            targetId = playerId;
            targetName = null;
            color = arg1;
            gradientEnd = arg2;
        } else {
            // Admin mode: /ch msgcolor <player> <#color> [#gradient]
            if (!hasWerchatPermission(ctx, "werchat.msgcolor.others")) {
                ctx.sendMessage(Message.raw("You don't have permission to set other players' message colors").color("#FF5555"));
                return;
            }
            PlayerRef target = playerDataManager.findPlayerByName(arg1);
            if (target == null) {
                ctx.sendMessage(Message.raw("Player not found: " + arg1).color("#FF5555"));
                return;
            }
            targetId = target.getUuid();
            targetName = target.getUsername();
            color = arg2;
            gradientEnd = arg3;

            if (color == null) {
                ctx.sendMessage(Message.raw("Usage: /ch msgcolor <player> <#color> [#gradient]").color("#FF5555"));
                return;
            }
        }

        // Handle reset
        if (color.equalsIgnoreCase("reset") || color.equalsIgnoreCase("clear") || color.equalsIgnoreCase("off")) {
            playerDataManager.clearMsgColor(targetId);
            if (targetName != null) {
                ctx.sendMessage(Message.raw("Message color cleared for " + targetName).color("#55FF55"));
            } else {
                ctx.sendMessage(Message.raw("Message color cleared (using channel color)").color("#55FF55"));
            }
            return;
        }

        // Check permission (self mode only - admin already checked above)
        if (targetName == null && !hasWerchatPermission(ctx, "werchat.msgcolor")) {
            ctx.sendMessage(Message.raw("You don't have permission to set message colors").color("#FF5555"));
            return;
        }

        // Validate start color
        String startHex = color.startsWith("#") ? color : "#" + color;
        if (!startHex.matches("#[0-9A-Fa-f]{6}")) {
            ctx.sendMessage(Message.raw("Invalid color format. Use #RRGGBB (e.g., #FF5555)").color("#FF5555"));
            return;
        }
        playerDataManager.setMsgColor(targetId, startHex);

        String prefix = targetName != null ? targetName + "'s message color" : "Message color";

        // Handle gradient if second color provided
        if (gradientEnd != null && !gradientEnd.isEmpty()) {
            String endHex = gradientEnd.startsWith("#") ? gradientEnd : "#" + gradientEnd;
            if (!endHex.matches("#[0-9A-Fa-f]{6}")) {
                ctx.sendMessage(Message.raw("Invalid gradient end color. Use #RRGGBB (e.g., #5555FF)").color("#FF5555"));
                return;
            }
            playerDataManager.setMsgGradientEnd(targetId, endHex);
            ctx.sendMessage(Message.join(
                Message.raw(prefix + " set to: ").color("#AAAAAA"),
                createGradientPreview("Example message", startHex, endHex)
            ));
        } else {
            // Clear any existing gradient
            playerDataManager.setMsgGradientEnd(targetId, null);
            ctx.sendMessage(Message.join(
                Message.raw(prefix + " set to: ").color("#AAAAAA"),
                Message.raw("Example message").color(startHex)
            ));
        }
    }

    private String joinArgs(String[] parts, int startIndexInclusive) {
        if (parts == null || startIndexInclusive < 0 || startIndexInclusive >= parts.length) {
            return null;
        }
        return String.join(" ", Arrays.copyOfRange(parts, startIndexInclusive, parts.length));
    }

}
