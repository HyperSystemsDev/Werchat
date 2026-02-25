package com.werchat.integration.papi;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

/**
 * Soft bridge to PlaceholderAPI to avoid hard runtime dependency.
 */
public abstract class PAPIIntegration {
    private static PAPIIntegration impl = null;

    @Nullable
    public static PAPIIntegration get() {
        return impl;
    }

    public static void register(WerchatPlugin plugin) {
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");

            impl = new PAPIImplementation();
            final WerchatExpansion expansion = new WerchatExpansion(plugin);

            plugin.getLogger().at(Level.INFO).log("PlaceholderAPI integration enabled");

            if (expansion.isRegistered()) {
                plugin.getLogger().atWarning().log("Warning! Werchat's PlaceholderAPI identifier %werchat_% is being used by another expansion. You will not be able to use werchat placeholders in other plugins.");
                return;
            }

            if (!expansion.register()) {
                plugin.getLogger().atWarning().log("Failed to register the Werchat expansion with PlaceholderAPI");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().at(Level.WARNING).log("PlaceholderAPI integration failed: %s", e.getMessage());
        }
    }

    public abstract String setPlaceholders(PlayerRef player, String text);

    public abstract String setRelationalPlaceholders(PlayerRef one, PlayerRef two, String text);
}
