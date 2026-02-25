package com.werchat.listeners;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.config.WerchatConfig;
import com.werchat.storage.PlayerDataManager;

import java.util.UUID;

/**
 * Handles player join/leave for channel management
 */
public class PlayerListener {

    private final ChatListener chatListener;
    private final ChannelManager channelManager;
    private final PlayerDataManager playerDataManager;
    private final WerchatConfig config;

    public PlayerListener(WerchatPlugin plugin) {
        this.chatListener = plugin.getChatListener();
        this.channelManager = plugin.getChannelManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.config = plugin.getConfig();
    }

    private boolean hasChannelJoinPermission(UUID playerId, Channel channel) {
        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, channel.getJoinPermission())
            || perms.hasPermission(playerId, "werchat.*")
            || perms.hasPermission(playerId, "*");
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        UUID playerId = player.getUuid();
        String playerName = player.getUsername();

        // Track online player
        playerDataManager.trackPlayer(playerId, player);
        channelManager.resetMotdSession(playerId);

        Channel firstJoinedChannel = null;

        // Auto-join channels flagged with autoJoin, optionally skipping default when disabled in config
        for (Channel channel : channelManager.getAllChannels()) {
            boolean shouldAutoJoin = channel.isAutoJoin();
            if (channel.isDefault() && !config.isAutoJoinDefault()) {
                shouldAutoJoin = false;
            }
            if (config.isEnforceChannelPermissions() && !hasChannelJoinPermission(playerId, channel)) {
                shouldAutoJoin = false;
            }
            if (shouldAutoJoin && !channel.isBanned(playerId) && channel.addMember(playerId)) {
                if (firstJoinedChannel == null) {
                    firstJoinedChannel = channel;
                }
            }
        }

        Channel defaultChannel = channelManager.getDefaultChannel();
        if (defaultChannel != null && config.isAutoJoinDefault() && !defaultChannel.isBanned(playerId)) {
            boolean canJoinDefault = !config.isEnforceChannelPermissions() || hasChannelJoinPermission(playerId, defaultChannel);
            if (canJoinDefault && defaultChannel.addMember(playerId) && firstJoinedChannel == null) {
                firstJoinedChannel = defaultChannel;
            }
        }

        // Ensure focused channel points to a channel the player is currently in.
        String focusedName = playerDataManager.getFocusedChannel(playerId);
        Channel focusedChannel = focusedName != null ? channelManager.getChannel(focusedName) : null;
        if (focusedChannel == null || !focusedChannel.isMember(playerId)) {
            if (firstJoinedChannel != null) {
                playerDataManager.setFocusedChannel(playerId, firstJoinedChannel.getName());
            } else if (defaultChannel != null) {
                playerDataManager.setFocusedChannel(playerId, defaultChannel.getName());
            }
        }

        if (config.isShowJoinLeaveMessages() && firstJoinedChannel != null) {
            broadcastMembershipEvent(playerName + " joined the server");
        }
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        UUID playerId = player.getUuid();
        String playerName = player.getUsername();

        if (config.isShowJoinLeaveMessages()) {
            broadcastMembershipEvent(playerName + " left the server");
        }

        // Don't remove from channels - persist membership across sessions
        channelManager.resetMotdSession(playerId);
        playerDataManager.untrackPlayer(playerId);
        playerDataManager.clearTransientData(playerId);
    }

    private void broadcastMembershipEvent(String text) {
        Channel defaultChannel = channelManager.getDefaultChannel();
        if (defaultChannel != null) {
            broadcastToChannel(defaultChannel, text);
        }
    }

    private void broadcastToChannel(Channel channel, String message) {
        Message formatted = Message.join(
            Message.raw("[" + channel.getNick() + "] ").color(channel.getColorHex()),
            Message.raw(message).color("#FFFF55")
        );
        for (UUID memberId : channel.getMembers()) {
            PlayerRef member = playerDataManager.getOnlinePlayer(memberId);
            if (member != null) {
                member.sendMessage(formatted);
            }
        }
    }
}
