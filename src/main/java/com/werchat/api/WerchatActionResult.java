package com.werchat.api;

/**
 * Result returned by API actions that mutate state or submit chat.
 */
public final class WerchatActionResult {

    private final WerchatActionStatus status;
    private final String message;
    private final String channelName;

    private WerchatActionResult(WerchatActionStatus status, String message, String channelName) {
        this.status = status;
        this.message = message;
        this.channelName = channelName;
    }

    public static WerchatActionResult success(String message, String channelName) {
        return new WerchatActionResult(WerchatActionStatus.SUCCESS, message, channelName);
    }

    public static WerchatActionResult failure(WerchatActionStatus status, String message, String channelName) {
        if (status == WerchatActionStatus.SUCCESS) {
            throw new IllegalArgumentException("Use success() for successful outcomes");
        }
        return new WerchatActionResult(status, message, channelName);
    }

    public WerchatActionStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getChannelName() {
        return channelName;
    }

    public boolean isSuccess() {
        return status == WerchatActionStatus.SUCCESS;
    }
}
