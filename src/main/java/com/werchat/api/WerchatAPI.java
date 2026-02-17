package com.werchat.api;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public integration surface for Werchat.
 */
public interface WerchatAPI {

    String API_VERSION = "2.0.0";

    Collection<WerchatChannelView> getChannels();

    Optional<WerchatChannelView> getChannel(String channelInput);

    String getFocusedChannel(UUID playerId);

    Set<String> getCapabilities();

    default String getApiVersion() {
        return API_VERSION;
    }

    default boolean hasCapability(String capability) {
        return capability != null && getCapabilities().contains(capability);
    }

    /**
     * Register an API hook to observe/cancel API-driven actions.
     */
    UUID registerHook(WerchatApiHook hook);

    boolean unregisterHook(UUID hookId);

    WerchatActionResult setFocusedChannel(UUID playerId, String channelInput);

    WerchatActionResult setFocusedChannel(UUID playerId, String channelInput, WerchatOperationOptions options);

    WerchatActionResult joinChannel(UUID playerId, String channelInput, String password);

    WerchatActionResult joinChannel(UUID playerId, String channelInput, String password, WerchatOperationOptions options);

    WerchatActionResult leaveChannel(UUID playerId, String channelInput);

    WerchatActionResult leaveChannel(UUID playerId, String channelInput, WerchatOperationOptions options);

    WerchatMembershipResult getMembership(UUID playerId, String channelInput);

    /**
     * Submit chat through Werchat's normal processing path (quick-chat, mute, cooldown, filter, etc).
     */
    WerchatActionResult submitPlayerChat(UUID senderId, String message);

    WerchatActionResult submitPlayerChat(UUID senderId, String message, WerchatOperationOptions options);

    /**
     * @deprecated Use {@link #getMembership(UUID, String)}.
     */
    @Deprecated(forRemoval = false)
    default boolean isMember(UUID playerId, String channelInput) {
        return getMembership(playerId, channelInput).isMember();
    }

    /**
     * @deprecated Use {@link #submitPlayerChat(UUID, String)}.
     */
    @Deprecated(forRemoval = false)
    default boolean relayChat(UUID senderId, String message) {
        return submitPlayerChat(senderId, message).isSuccess();
    }
}
