package com.werchat.api;

/**
 * Result returned by channel membership queries.
 */
public final class WerchatMembershipResult {

    private final WerchatMembershipStatus status;
    private final String channelName;
    private final String message;

    private WerchatMembershipResult(WerchatMembershipStatus status, String channelName, String message) {
        this.status = status;
        this.channelName = channelName;
        this.message = message;
    }

    public static WerchatMembershipResult member(String channelName) {
        return new WerchatMembershipResult(WerchatMembershipStatus.MEMBER, channelName, null);
    }

    public static WerchatMembershipResult notMember(String channelName) {
        return new WerchatMembershipResult(WerchatMembershipStatus.NOT_MEMBER, channelName, null);
    }

    public static WerchatMembershipResult invalidArgument(String message) {
        return new WerchatMembershipResult(WerchatMembershipStatus.INVALID_ARGUMENT, null, message);
    }

    public static WerchatMembershipResult channelNotFound(String channelInput) {
        return new WerchatMembershipResult(WerchatMembershipStatus.CHANNEL_NOT_FOUND, null, "Channel not found: " + channelInput);
    }

    public WerchatMembershipStatus getStatus() {
        return status;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getMessage() {
        return message;
    }

    public boolean isMember() {
        return status == WerchatMembershipStatus.MEMBER;
    }
}
