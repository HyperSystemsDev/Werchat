package com.werchat.api;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.listeners.ChatListener;
import com.werchat.storage.PlayerDataManager;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Default Werchat API implementation.
 */
public class WerchatAPIImpl implements WerchatAPI {

    private static final Set<String> CAPABILITIES = Set.of(
        WerchatApiCapabilities.TYPED_RESULTS,
        WerchatApiCapabilities.PERMISSION_AWARE_OPERATIONS,
        WerchatApiCapabilities.HOOKS,
        WerchatApiCapabilities.SUBMIT_PLAYER_CHAT,
        WerchatApiCapabilities.API_VERSIONING
    );

    private final WerchatPlugin plugin;
    private final ChannelManager channelManager;
    private final PlayerDataManager playerDataManager;
    private final ChatListener chatListener;
    private final Map<UUID, WerchatApiHook> hooks;

    public WerchatAPIImpl(WerchatPlugin plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.chatListener = plugin.getChatListener();
        this.hooks = new ConcurrentHashMap<>();
    }

    @Override
    public Collection<WerchatChannelView> getChannels() {
        return channelManager.getAllChannels().stream()
            .map(this::toView)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<WerchatChannelView> getChannel(String channelInput) {
        Channel channel = channelManager.findChannel(channelInput);
        return Optional.ofNullable(channel).map(this::toView);
    }

    @Override
    public String getFocusedChannel(UUID playerId) {
        return playerDataManager.getFocusedChannel(playerId);
    }

    @Override
    public Set<String> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public UUID registerHook(WerchatApiHook hook) {
        if (hook == null) {
            throw new IllegalArgumentException("hook cannot be null");
        }

        UUID hookId = UUID.randomUUID();
        hooks.put(hookId, hook);
        return hookId;
    }

    @Override
    public boolean unregisterHook(UUID hookId) {
        if (hookId == null) {
            return false;
        }

        return hooks.remove(hookId) != null;
    }

    @Override
    public WerchatActionResult setFocusedChannel(UUID playerId, String channelInput) {
        return setFocusedChannel(playerId, channelInput, WerchatOperationOptions.defaults());
    }

    @Override
    public WerchatActionResult setFocusedChannel(UUID playerId, String channelInput, WerchatOperationOptions options) {
        if (playerId == null) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "playerId cannot be null", null);
        }
        if (channelInput == null || channelInput.isBlank()) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "channelInput cannot be blank", null);
        }

        WerchatOperationOptions effectiveOptions = WerchatOperationOptions.orDefault(options);
        WerchatApiActionContext context = new WerchatApiActionContext(
            WerchatApiActionType.SET_FOCUSED_CHANNEL,
            playerId,
            channelInput,
            null,
            effectiveOptions
        );

        Channel channel = channelManager.findChannel(channelInput);
        if (channel != null) {
            context.setResolvedChannelName(channel.getName());
        }

        if (!dispatchBefore(context)) {
            return finishAction(context, cancelledResult(context));
        }

        if (channel == null) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.CHANNEL_NOT_FOUND, "Channel not found", null));
        }
        if (effectiveOptions.isEnforcePermissions() && !hasPermission(playerId, "werchat.switch")) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.PERMISSION_DENIED, "Missing permission: werchat.switch", channel.getName()));
        }
        if (!channel.isFocusable()) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.CHANNEL_NOT_FOCUSABLE, "Channel is not focusable", channel.getName()));
        }
        if (!channel.isMember(playerId)) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.NOT_MEMBER, "Player is not in channel", channel.getName()));
        }

        playerDataManager.setFocusedChannel(playerId, channel.getName());
        return finishAction(context, WerchatActionResult.success("Focused channel updated", channel.getName()));
    }

    @Override
    public WerchatActionResult joinChannel(UUID playerId, String channelInput, String password) {
        return joinChannel(playerId, channelInput, password, WerchatOperationOptions.defaults());
    }

    @Override
    public WerchatActionResult joinChannel(UUID playerId, String channelInput, String password, WerchatOperationOptions options) {
        if (playerId == null) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "playerId cannot be null", null);
        }
        if (channelInput == null || channelInput.isBlank()) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "channelInput cannot be blank", null);
        }

        WerchatOperationOptions effectiveOptions = WerchatOperationOptions.orDefault(options);
        WerchatApiActionContext context = new WerchatApiActionContext(
            WerchatApiActionType.JOIN_CHANNEL,
            playerId,
            channelInput,
            null,
            effectiveOptions
        );

        Channel channel = channelManager.findChannel(channelInput);
        if (channel != null) {
            context.setResolvedChannelName(channel.getName());
        }

        if (!dispatchBefore(context)) {
            return finishAction(context, cancelledResult(context));
        }

        if (channel == null) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.CHANNEL_NOT_FOUND, "Channel not found", null));
        }
        if (effectiveOptions.isEnforcePermissions() && !hasPermission(playerId, "werchat.join")) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.PERMISSION_DENIED, "Missing permission: werchat.join", channel.getName()));
        }
        String joinPermission = channelPermissionNode(channel, "join");
        if (effectiveOptions.isEnforcePermissions() && !hasPermission(playerId, joinPermission)) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.PERMISSION_DENIED, "Missing permission: " + joinPermission, channel.getName()));
        }
        if (channel.isBanned(playerId)) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.BANNED_FROM_CHANNEL, "Player is banned from this channel", channel.getName()));
        }
        if (channel.isMember(playerId)) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.ALREADY_MEMBER, "Player is already in this channel", channel.getName()));
        }
        if (channel.hasPassword() && !channel.checkPassword(password)) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.INVALID_PASSWORD, "Incorrect channel password", channel.getName()));
        }

        channel.addMember(playerId);
        return finishAction(context, WerchatActionResult.success("Joined channel", channel.getName()));
    }

    @Override
    public WerchatActionResult leaveChannel(UUID playerId, String channelInput) {
        return leaveChannel(playerId, channelInput, WerchatOperationOptions.defaults());
    }

    @Override
    public WerchatActionResult leaveChannel(UUID playerId, String channelInput, WerchatOperationOptions options) {
        if (playerId == null) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "playerId cannot be null", null);
        }
        if (channelInput == null || channelInput.isBlank()) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "channelInput cannot be blank", null);
        }

        WerchatOperationOptions effectiveOptions = WerchatOperationOptions.orDefault(options);
        WerchatApiActionContext context = new WerchatApiActionContext(
            WerchatApiActionType.LEAVE_CHANNEL,
            playerId,
            channelInput,
            null,
            effectiveOptions
        );

        Channel channel = channelManager.findChannel(channelInput);
        if (channel != null) {
            context.setResolvedChannelName(channel.getName());
        }

        if (!dispatchBefore(context)) {
            return finishAction(context, cancelledResult(context));
        }

        if (channel == null) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.CHANNEL_NOT_FOUND, "Channel not found", null));
        }
        if (effectiveOptions.isEnforcePermissions() && !hasPermission(playerId, "werchat.leave")) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.PERMISSION_DENIED, "Missing permission: werchat.leave", channel.getName()));
        }
        if (!channel.isMember(playerId)) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.NOT_MEMBER, "Player is not in this channel", channel.getName()));
        }

        channel.removeMember(playerId);

        String focusedChannel = playerDataManager.getFocusedChannel(playerId);
        if (focusedChannel != null && focusedChannel.equalsIgnoreCase(channel.getName())) {
            Channel fallback = channelManager.getDefaultChannel();
            if (fallback != null) {
                playerDataManager.setFocusedChannel(playerId, fallback.getName());
            }
        }

        return finishAction(context, WerchatActionResult.success("Left channel", channel.getName()));
    }

    @Override
    public WerchatMembershipResult getMembership(UUID playerId, String channelInput) {
        if (playerId == null) {
            return WerchatMembershipResult.invalidArgument("playerId cannot be null");
        }
        if (channelInput == null || channelInput.isBlank()) {
            return WerchatMembershipResult.invalidArgument("channelInput cannot be blank");
        }

        Channel channel = channelManager.findChannel(channelInput);
        if (channel == null) {
            return WerchatMembershipResult.channelNotFound(channelInput);
        }

        return channel.isMember(playerId)
            ? WerchatMembershipResult.member(channel.getName())
            : WerchatMembershipResult.notMember(channel.getName());
    }

    @Override
    public WerchatActionResult submitPlayerChat(UUID senderId, String message) {
        return submitPlayerChat(senderId, message, WerchatOperationOptions.defaults());
    }

    @Override
    public WerchatActionResult submitPlayerChat(UUID senderId, String message, WerchatOperationOptions options) {
        if (senderId == null) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "senderId cannot be null", null);
        }
        if (message == null || message.isBlank()) {
            return WerchatActionResult.failure(WerchatActionStatus.INVALID_ARGUMENT, "message cannot be blank", null);
        }

        PlayerRef sender = playerDataManager.getOnlinePlayer(senderId);
        if (sender == null) {
            return WerchatActionResult.failure(WerchatActionStatus.PLAYER_OFFLINE, "Player is not online", null);
        }

        WerchatOperationOptions effectiveOptions = WerchatOperationOptions.orDefault(options);
        WerchatApiActionContext context = new WerchatApiActionContext(
            WerchatApiActionType.SUBMIT_PLAYER_CHAT,
            senderId,
            null,
            message,
            effectiveOptions
        );

        Channel quickChatChannel = channelManager.findChannelByQuickChatSymbol(message);
        boolean quickChatMatch = quickChatChannel != null && quickChatChannel.isQuickChatEnabled();
        Channel selectedChannel = null;

        if (quickChatMatch) {
            selectedChannel = quickChatChannel;
        } else {
            String focusedChannelName = playerDataManager.getFocusedChannel(senderId);
            selectedChannel = channelManager.getChannel(focusedChannelName);
            if (selectedChannel == null) {
                selectedChannel = channelManager.getDefaultChannel();
            }
        }

        if (selectedChannel != null) {
            context.setResolvedChannelName(selectedChannel.getName());
        }

        if (!dispatchBefore(context)) {
            return finishAction(context, cancelledResult(context));
        }

        if (selectedChannel == null) {
            return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.NO_DEFAULT_CHANNEL, "No valid channel available", null));
        }

        if (effectiveOptions.isEnforcePermissions()) {
            if (quickChatMatch && !hasPermission(senderId, "werchat.quickchat")) {
                return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.PERMISSION_DENIED, "Missing permission: werchat.quickchat", selectedChannel.getName()));
            }
            String speakPermission = channelPermissionNode(selectedChannel, "speak");
            if (!hasPermission(senderId, speakPermission)) {
                return finishAction(context, WerchatActionResult.failure(WerchatActionStatus.PERMISSION_DENIED, "Missing permission: " + speakPermission, selectedChannel.getName()));
            }
        }

        chatListener.handleChatInput(sender, message);
        return finishAction(context, WerchatActionResult.success("Message submitted", selectedChannel.getName()));
    }

    private WerchatActionResult cancelledResult(WerchatApiActionContext context) {
        return WerchatActionResult.failure(
            WerchatActionStatus.CANCELLED_BY_HOOK,
            context.getCancelReason(),
            context.getResolvedChannelName()
        );
    }

    private WerchatActionResult finishAction(WerchatApiActionContext context, WerchatActionResult result) {
        dispatchAfter(context, result);
        return result;
    }

    private boolean dispatchBefore(WerchatApiActionContext context) {
        for (WerchatApiHook hook : hooks.values()) {
            try {
                hook.beforeAction(context);
                if (context.isCancelled()) {
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).log("Werchat API hook beforeAction failed: %s", e.getMessage());
            }
        }
        return true;
    }

    private void dispatchAfter(WerchatApiActionContext context, WerchatActionResult result) {
        WerchatApiActionOutcome outcome = new WerchatApiActionOutcome(
            context.getActionType(),
            context.getPlayerId(),
            context.getChannelInput(),
            context.getResolvedChannelName(),
            context.getMessage(),
            context.getOptions(),
            result
        );

        for (WerchatApiHook hook : hooks.values()) {
            try {
                hook.afterAction(outcome);
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).log("Werchat API hook afterAction failed: %s", e.getMessage());
            }
        }
    }

    private boolean hasPermission(UUID playerId, String permission) {
        if (playerId == null || permission == null || permission.isBlank()) {
            return false;
        }

        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, permission)
            || perms.hasPermission(playerId, "werchat.*")
            || perms.hasPermission(playerId, "*");
    }

    private String channelPermissionNode(Channel channel, String action) {
        return "werchat.channel." + channel.getName().toLowerCase(Locale.ROOT) + "." + action;
    }

    private WerchatChannelView toView(Channel channel) {
        Set<String> worlds = Set.copyOf(channel.getWorlds());
        return new WerchatChannelView(
            channel.getName(),
            channel.getNick(),
            channel.getColorHex(),
            channel.getEffectiveMessageColorHex(),
            channel.getDistance(),
            channel.isDefault(),
            channel.isAutoJoin(),
            channel.isQuickChatEnabled(),
            channel.getQuickChatSymbol(),
            worlds
        );
    }
}
