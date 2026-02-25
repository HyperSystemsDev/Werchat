package com.werchat;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.event.EventPriority;
import com.werchat.channels.ChannelManager;
import com.werchat.commands.ChannelCommand;
import com.werchat.commands.IgnoreCommand;
import com.werchat.commands.IgnoreListCommand;
import com.werchat.commands.MessageCommand;
import com.werchat.commands.ReplyCommand;
import com.werchat.config.WerchatConfig;
import com.werchat.api.WerchatAPI;
import com.werchat.api.WerchatAPIImpl;
import com.werchat.integration.papi.PAPIIntegration;
import com.werchat.listeners.ChatListener;
import com.werchat.listeners.PlayerListener;
import com.werchat.storage.PlayerDataManager;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Werchat - Channel-based chat system for Hytale
 * Version 1.11.0
 */
public class WerchatPlugin extends JavaPlugin {

    private static WerchatPlugin instance;
    private WerchatConfig config;
    private ChannelManager channelManager;
    private PlayerDataManager playerDataManager;
    private ChatListener chatListener;
    private PlayerListener playerListener;
    private WerchatAPI api;

    public WerchatPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> preLoad() {
        getLogger().at(Level.INFO).log("Werchat 1.11.0 is loading...");

        // Initialize config first
        this.config = new WerchatConfig(this);
        config.load();

        // Initialize managers
        this.channelManager = new ChannelManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.chatListener = new ChatListener(this);
        this.playerListener = new PlayerListener(this);
        this.api = new WerchatAPIImpl(this);

        // Load data
        channelManager.loadChannels();
        playerDataManager.loadNicknames();

        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("Werchat is registering events and commands...");
        PAPIIntegration.register(this);
        registerListeners();
        registerCommands();

        getLogger().at(Level.INFO).log("Werchat enabled! %d channels loaded.", channelManager.getChannelCount());
        getLogger().at(Level.INFO).log("Persistence mode: dirty + debounced saves (20s)");
        getLogger().at(Level.INFO).log(
            "Channel permission enforcement (core command/chat flow): %s",
            config.isEnforceChannelPermissions() ? "ENABLED" : "DISABLED"
        );

        // Log enabled features
        if (config.isWordFilterEnabled()) {
            getLogger().at(Level.INFO).log("Word filter: ENABLED (%s mode)", config.getFilterMode());
        }
        if (config.isCooldownEnabled()) {
            getLogger().at(Level.INFO).log("Chat cooldown: ENABLED (%ds)", config.getCooldownSeconds());
        }
        if (config.isMentionsEnabled()) {
            getLogger().at(Level.INFO).log("Mentions: ENABLED");
        }
    }

    private void registerListeners() {
        // Register chat at LATE priority so permission plugins (HyperPerms, LuckPerms) can format first
        getEventRegistry().registerGlobal(EventPriority.LATE, PlayerChatEvent.class, chatListener::onPlayerChat);
        getEventRegistry().registerGlobal(PlayerConnectEvent.class, playerListener::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, playerListener::onPlayerDisconnect);
    }

    private void registerCommands() {
        getCommandRegistry().registerCommand(new ChannelCommand(this));
        getCommandRegistry().registerCommand(new MessageCommand(this));
        getCommandRegistry().registerCommand(new ReplyCommand(this));
        getCommandRegistry().registerCommand(new IgnoreCommand(this));
        getCommandRegistry().registerCommand(new IgnoreListCommand(this));
    }

    @Override
    protected void shutdown() {
        // Stop debounced savers before final flush
        if (channelManager != null) {
            channelManager.shutdownDebouncedSaver();
        }
        if (playerDataManager != null) {
            playerDataManager.shutdownDebouncedSaver();
        }

        // Save data
        if (channelManager != null) {
            channelManager.saveChannels();
        }
        if (playerDataManager != null) {
            playerDataManager.saveNicknames();
        }
        if (config != null) {
            config.save();
        }

        getLogger().at(Level.INFO).log("Werchat disabled.");
    }

    public static WerchatPlugin getInstance() { return instance; }
    public static WerchatAPI api() { return instance != null ? instance.getAPI() : null; }
    public WerchatConfig getConfig() { return config; }
    public ChannelManager getChannelManager() { return channelManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public ChatListener getChatListener() { return chatListener; }
    public WerchatAPI getAPI() { return api; }
}
