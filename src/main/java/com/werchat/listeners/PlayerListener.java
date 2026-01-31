package com.werchat.listeners;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.storage.PlayerDataManager;

import java.util.UUID;

/**
 * Handles player join/leave for channel management
 */
public class PlayerListener {

    private final WerchatPlugin plugin;
    private final ChannelManager channelManager;
    private final PlayerDataManager playerDataManager;

    public PlayerListener(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        UUID playerId = player.getUuid();
        String playerName = player.getUsername();

        // Track online player
        playerDataManager.trackPlayer(playerId, player);

        // Auto-join default channels (silently - no broadcast spam)
        for (Channel channel : channelManager.getAllChannels()) {
            if (channel.isAutoJoin() && !channel.isBanned(playerId)) {
                channel.addMember(playerId);
            }
        }

        // Set default focused channel if none set
        if (playerDataManager.getFocusedChannel(playerId) == null) {
            Channel defaultChannel = channelManager.getDefaultChannel();
            if (defaultChannel != null) {
                playerDataManager.setFocusedChannel(playerId, defaultChannel.getName());
            }
        }

    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        UUID playerId = player.getUuid();
        String playerName = player.getUsername();

        // Don't remove from channels - persist membership across sessions
        // No broadcast spam on disconnect either
        // Only clean up transient data
        playerDataManager.untrackPlayer(playerId);
        playerDataManager.clearTransientData(playerId);
    }

    public boolean joinChannel(PlayerRef player, Channel channel) {
        UUID playerId = player.getUuid();
        if (channel.isBanned(playerId) || channel.isMember(playerId)) {
            return false;
        }
        channel.addMember(playerId);
        if (channel.isVerbose()) {
            broadcastToChannel(channel, player.getUsername() + " joined " + channel.getName());
        }
        return true;
    }

    public boolean leaveChannel(PlayerRef player, Channel channel) {
        UUID playerId = player.getUuid();
        if (!channel.isMember(playerId)) {
            return false;
        }
        channel.removeMember(playerId);
        if (channel.isVerbose()) {
            broadcastToChannel(channel, player.getUsername() + " left " + channel.getName());
        }
        // Reset focus if leaving focused channel
        String focused = playerDataManager.getFocusedChannel(playerId);
        if (focused != null && focused.equalsIgnoreCase(channel.getName())) {
            Channel def = channelManager.getDefaultChannel();
            if (def != null) {
                playerDataManager.setFocusedChannel(playerId, def.getName());
            }
        }
        return true;
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
