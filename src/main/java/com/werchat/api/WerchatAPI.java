package com.werchat.api;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public integration surface for Werchat.
 */
public interface WerchatAPI {

    String API_VERSION = "2.1.0";

    Collection<WerchatChannelView> getChannels();

    Optional<WerchatChannelView> getChannel(String channelInput, WerchatChannelLookupMode lookupMode);

    default Optional<WerchatChannelView> getChannel(String channelInput) {
        return getChannel(channelInput, WerchatChannelLookupMode.FUZZY);
    }

    default Optional<WerchatChannelView> getChannelExact(String channelNameOrNick) {
        return getChannel(channelNameOrNick, WerchatChannelLookupMode.EXACT);
    }

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

    WerchatActionResult setFocusedChannel(UUID playerId,
                                          String channelInput,
                                          WerchatOperationOptions options,
                                          WerchatChannelLookupMode lookupMode);

    default WerchatActionResult setFocusedChannel(UUID playerId, String channelInput) {
        return setFocusedChannel(playerId, channelInput, WerchatOperationOptions.defaults(), WerchatChannelLookupMode.FUZZY);
    }

    default WerchatActionResult setFocusedChannel(UUID playerId, String channelInput, WerchatOperationOptions options) {
        return setFocusedChannel(playerId, channelInput, options, WerchatChannelLookupMode.FUZZY);
    }

    default WerchatActionResult setFocusedChannelExact(UUID playerId, String channelNameOrNick) {
        return setFocusedChannel(playerId, channelNameOrNick, WerchatOperationOptions.defaults(), WerchatChannelLookupMode.EXACT);
    }

    default WerchatActionResult setFocusedChannelExact(UUID playerId,
                                                       String channelNameOrNick,
                                                       WerchatOperationOptions options) {
        return setFocusedChannel(playerId, channelNameOrNick, options, WerchatChannelLookupMode.EXACT);
    }

    WerchatActionResult joinChannel(UUID playerId,
                                    String channelInput,
                                    String password,
                                    WerchatOperationOptions options,
                                    WerchatChannelLookupMode lookupMode);

    default WerchatActionResult joinChannel(UUID playerId, String channelInput, String password) {
        return joinChannel(playerId, channelInput, password, WerchatOperationOptions.defaults(), WerchatChannelLookupMode.FUZZY);
    }

    default WerchatActionResult joinChannel(UUID playerId,
                                            String channelInput,
                                            String password,
                                            WerchatOperationOptions options) {
        return joinChannel(playerId, channelInput, password, options, WerchatChannelLookupMode.FUZZY);
    }

    default WerchatActionResult joinChannelExact(UUID playerId, String channelNameOrNick, String password) {
        return joinChannel(playerId, channelNameOrNick, password, WerchatOperationOptions.defaults(), WerchatChannelLookupMode.EXACT);
    }

    default WerchatActionResult joinChannelExact(UUID playerId,
                                                 String channelNameOrNick,
                                                 String password,
                                                 WerchatOperationOptions options) {
        return joinChannel(playerId, channelNameOrNick, password, options, WerchatChannelLookupMode.EXACT);
    }

    WerchatActionResult leaveChannel(UUID playerId,
                                     String channelInput,
                                     WerchatOperationOptions options,
                                     WerchatChannelLookupMode lookupMode);

    default WerchatActionResult leaveChannel(UUID playerId, String channelInput) {
        return leaveChannel(playerId, channelInput, WerchatOperationOptions.defaults(), WerchatChannelLookupMode.FUZZY);
    }

    default WerchatActionResult leaveChannel(UUID playerId, String channelInput, WerchatOperationOptions options) {
        return leaveChannel(playerId, channelInput, options, WerchatChannelLookupMode.FUZZY);
    }

    default WerchatActionResult leaveChannelExact(UUID playerId, String channelNameOrNick) {
        return leaveChannel(playerId, channelNameOrNick, WerchatOperationOptions.defaults(), WerchatChannelLookupMode.EXACT);
    }

    default WerchatActionResult leaveChannelExact(UUID playerId,
                                                  String channelNameOrNick,
                                                  WerchatOperationOptions options) {
        return leaveChannel(playerId, channelNameOrNick, options, WerchatChannelLookupMode.EXACT);
    }

    WerchatMembershipResult getMembership(UUID playerId, String channelInput, WerchatChannelLookupMode lookupMode);

    default WerchatMembershipResult getMembership(UUID playerId, String channelInput) {
        return getMembership(playerId, channelInput, WerchatChannelLookupMode.FUZZY);
    }

    default WerchatMembershipResult getMembershipExact(UUID playerId, String channelNameOrNick) {
        return getMembership(playerId, channelNameOrNick, WerchatChannelLookupMode.EXACT);
    }

    /**
     * Submit chat through Werchat's normal processing path (quick-chat, mute, cooldown, filter, etc).
     */
    WerchatActionResult submitPlayerChat(UUID senderId, String message);

    WerchatActionResult submitPlayerChat(UUID senderId, String message, WerchatOperationOptions options);
}
