package com.werchat.api;

import java.util.UUID;

/**
 * Immutable post-action payload for hooks.
 */
public final class WerchatApiActionOutcome {

    private final WerchatApiActionType actionType;
    private final UUID playerId;
    private final String channelInput;
    private final String resolvedChannelName;
    private final String message;
    private final WerchatOperationOptions options;
    private final WerchatActionResult result;

    public WerchatApiActionOutcome(WerchatApiActionType actionType,
                                   UUID playerId,
                                   String channelInput,
                                   String resolvedChannelName,
                                   String message,
                                   WerchatOperationOptions options,
                                   WerchatActionResult result) {
        this.actionType = actionType;
        this.playerId = playerId;
        this.channelInput = channelInput;
        this.resolvedChannelName = resolvedChannelName;
        this.message = message;
        this.options = options;
        this.result = result;
    }

    public WerchatApiActionType getActionType() {
        return actionType;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getChannelInput() {
        return channelInput;
    }

    public String getResolvedChannelName() {
        return resolvedChannelName;
    }

    public String getMessage() {
        return message;
    }

    public WerchatOperationOptions getOptions() {
        return options;
    }

    public WerchatActionResult getResult() {
        return result;
    }
}
