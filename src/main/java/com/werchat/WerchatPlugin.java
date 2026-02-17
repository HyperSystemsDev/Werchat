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
import com.werchat.listeners.ChatListener;
import com.werchat.listeners.PlayerListener;
import com.werchat.storage.PlayerDataManager;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Werchat - Channel-based chat system for Hytale
 * Version 1.1.8
 */
public class WerchatPlugin extends JavaPlugin {

    private static WerchatPlugin instance;
    private WerchatConfig config;
    private ChannelManager channelManager;
    private PlayerDataManager playerDataManager;
    private ChatListener chatListener;
    private PlayerListener playerListener;
    private ScheduledExecutorService autoSaveScheduler;

    public WerchatPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> preLoad() {
        getLogger().at(Level.INFO).log("Werchat 1.1.8 is loading...");

        // Initialize config first
        this.config = new WerchatConfig(this);
        config.load();

        // Initialize managers
        this.channelManager = new ChannelManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.chatListener = new ChatListener(this);
        this.playerListener = new PlayerListener(this);

        // Load data
        channelManager.loadChannels();
        playerDataManager.loadNicknames();

        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("Werchat is registering events and commands...");
        registerListeners();
        registerCommands();

        // Auto-save every 5 minutes to keep player names updated in channel-members.json
        autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
        autoSaveScheduler.scheduleAtFixedRate(() -> {
            try {
                channelManager.saveChannels();
                playerDataManager.saveNicknames();
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("Auto-save failed: %s", e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);

        getLogger().at(Level.INFO).log("Werchat enabled! %d channels loaded.", channelManager.getChannelCount());

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
        // Stop auto-save
        if (autoSaveScheduler != null) {
            autoSaveScheduler.shutdown();
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
    public WerchatConfig getConfig() { return config; }
    public ChannelManager getChannelManager() { return channelManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public ChatListener getChatListener() { return chatListener; }
}
