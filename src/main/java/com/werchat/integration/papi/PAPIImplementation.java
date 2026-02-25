package com.werchat.integration.papi;

import at.helpch.placeholderapi.PlaceholderAPI;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class PAPIImplementation implements PAPIIntegration {
    private final WerchatPlugin plugin;
    private volatile boolean expansionRegistered = false;
    private volatile boolean loggedRegistrationFailure = false;

    public PAPIImplementation(WerchatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String setPlaceholders(PlayerRef player, String text) {
        try {
            registerExpansionIfReady();
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }

    @Override
    public String setRelationalPlaceholders(PlayerRef one, PlayerRef two, String text) {
        try {
            registerExpansionIfReady();
            return PlaceholderAPI.setRelationalPlaceholders(one, two, text);
        } catch (Throwable ignored) {
            return text;
        }
    }

    @Override
    public void ensureExpansionRegistered(PlayerRef player) {
        registerExpansionIfReady();
    }

    private void registerExpansionIfReady() {
        if (expansionRegistered) {
            return;
        }

        synchronized (this) {
            if (expansionRegistered) {
                return;
            }

            try {
                if (!isPlaceholderApiConfigReady()) {
                    return;
                }

                WerchatExpansion expansion = new WerchatExpansion(plugin);
                if (expansion.isRegistered()) {
                    expansionRegistered = true;
                    loggedRegistrationFailure = false;
                    return;
                }

                if (expansion.register()) {
                    expansionRegistered = true;
                    loggedRegistrationFailure = false;
                    plugin.getLogger().at(Level.INFO).log("PlaceholderAPI integration enabled");
                    return;
                }

                if (!loggedRegistrationFailure) {
                    plugin.getLogger().at(Level.WARNING)
                        .log("PlaceholderAPI detected, but Werchat expansion could not be registered");
                    loggedRegistrationFailure = true;
                }
            } catch (Throwable e) {
                if (!loggedRegistrationFailure) {
                    plugin.getLogger().at(Level.WARNING).log("PlaceholderAPI integration failed: %s", e.getMessage());
                    loggedRegistrationFailure = true;
                }
            }
        }
    }

    private boolean isPlaceholderApiConfigReady() throws Exception {
        Class<?> papiPluginClass = Class.forName("at.helpch.placeholderapi.PlaceholderAPIPlugin");
        Method instanceMethod = papiPluginClass.getMethod("instance");
        Object papiPlugin = instanceMethod.invoke(null);
        if (papiPlugin == null) {
            return false;
        }

        Method configManagerMethod = papiPluginClass.getMethod("configManager");
        Object configManager = configManagerMethod.invoke(papiPlugin);
        if (configManager == null) {
            return false;
        }

        Method configMethod = configManager.getClass().getMethod("config");
        Object config = configMethod.invoke(configManager);
        return config != null;
    }
}
