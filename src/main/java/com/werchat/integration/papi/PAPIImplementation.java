package com.werchat.integration.papi;

import at.helpch.placeholderapi.PlaceholderAPI;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class PAPIImplementation extends PAPIIntegration {
    @Override
    public String setPlaceholders(PlayerRef player, String text) {
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }

    @Override
    public String setRelationalPlaceholders(PlayerRef one, PlayerRef two, String text) {
        try {
            return PlaceholderAPI.setRelationalPlaceholders(one, two, text);
        } catch (Throwable ignored) {
            return text;
        }
    }
}
