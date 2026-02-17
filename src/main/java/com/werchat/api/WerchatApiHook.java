package com.werchat.api;

/**
 * Hook interface for observing/cancelling API-driven actions.
 */
public interface WerchatApiHook {

    default void beforeAction(WerchatApiActionContext context) {
    }

    default void afterAction(WerchatApiActionOutcome outcome) {
    }
}
