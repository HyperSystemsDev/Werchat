package com.werchat.api;

import java.util.UUID;

/**
 * Mutable context for pre-action hooks.
 */
public final class WerchatApiActionContext {

    private final WerchatApiActionType actionType;
    private final UUID playerId;
    private final String channelInput;
    private final String message;
    private final WerchatOperationOptions options;

    private String resolvedChannelName;
    private boolean cancelled;
    private String cancelReason;

    public WerchatApiActionContext(WerchatApiActionType actionType,
                                   UUID playerId,
                                   String channelInput,
                                   String message,
                                   WerchatOperationOptions options) {
        this.actionType = actionType;
        this.playerId = playerId;
        this.channelInput = channelInput;
        this.message = message;
        this.options = options;
        this.cancelled = false;
        this.cancelReason = null;
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

    public String getMessage() {
        return message;
    }

    public WerchatOperationOptions getOptions() {
        return options;
    }

    public String getResolvedChannelName() {
        return resolvedChannelName;
    }

    void setResolvedChannelName(String resolvedChannelName) {
        this.resolvedChannelName = resolvedChannelName;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void cancel(String reason) {
        this.cancelled = true;
        this.cancelReason = (reason == null || reason.isBlank()) ? "Cancelled by API hook" : reason;
    }
}
