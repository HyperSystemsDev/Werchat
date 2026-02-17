package com.werchat.api;

/**
 * Optional behavior flags for Werchat API operations.
 */
public final class WerchatOperationOptions {

    private static final WerchatOperationOptions DEFAULT = new WerchatOperationOptions(false);
    private static final WerchatOperationOptions ENFORCE_PERMISSIONS = new WerchatOperationOptions(true);

    private final boolean enforcePermissions;

    public WerchatOperationOptions(boolean enforcePermissions) {
        this.enforcePermissions = enforcePermissions;
    }

    public boolean isEnforcePermissions() {
        return enforcePermissions;
    }

    public static WerchatOperationOptions defaults() {
        return DEFAULT;
    }

    public static WerchatOperationOptions enforcePermissions() {
        return ENFORCE_PERMISSIONS;
    }

    static WerchatOperationOptions orDefault(WerchatOperationOptions options) {
        return options == null ? DEFAULT : options;
    }
}
