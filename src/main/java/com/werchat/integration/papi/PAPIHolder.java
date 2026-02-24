package com.werchat.integration.papi;

import com.werchat.WerchatPlugin;

/**
 * Caches a single PAPIIntegration instance so the expansion is only
 * registered (and logged) once, regardless of how many callers invoke
 * {@link PAPIIntegration#register}.
 */
final class PAPIHolder {
    private static volatile PAPIIntegration instance;

    private PAPIHolder() {}

    static PAPIIntegration getOrCreate(WerchatPlugin plugin) {
        if (instance != null) {
            return instance;
        }
        synchronized (PAPIHolder.class) {
            if (instance != null) {
                return instance;
            }
            try {
                Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
                instance = new PAPIImplementation(plugin);
            } catch (ClassNotFoundException ignored) {
                // PlaceholderAPI not present — leave null
            }
            return instance;
        }
    }
}
