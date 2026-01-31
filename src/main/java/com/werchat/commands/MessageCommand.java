package com.werchat.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.listeners.ChatListener;
import com.werchat.storage.PlayerDataManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * /msg command for private messages
 */
public class MessageCommand extends CommandBase {

    private final WerchatPlugin plugin;
    private final PlayerDataManager playerDataManager;

    public MessageCommand(WerchatPlugin plugin) {
        super("msg", "Private message", false);
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();

        // Available to all players in Adventure mode
        this.setPermissionGroup(GameMode.Adventure);

        // Allow any arguments - we parse them manually
        this.setAllowsExtraArguments(true);

        this.addAliases("whisper", "w", "tell", "pm");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (!ctx.isPlayer()) {
            return;
        }

        UUID senderId = ctx.sender().getUuid();

        // Check permission
        PermissionsModule perms = PermissionsModule.get();
        if (!perms.hasPermission(senderId, "werchat.msg")
                && !perms.hasPermission(senderId, "werchat.*")
                && !perms.hasPermission(senderId, "*")) {
            ctx.sendMessage(Message.raw("You don't have permission to send private messages").color("#FF5555"));
            return;
        }

        PlayerRef sender = playerDataManager.getOnlinePlayer(senderId);
        if (sender == null) {
            ctx.sendMessage(Message.raw("Error: Sender not found").color("#FF0000"));
            return;
        }

        // Parse raw input: "msg player hello world" -> ["msg", "player", "hello", "world"]
        String input = ctx.getInputString();
        String[] parts = input.trim().split("\\s+");

        // parts[0] is "msg", parts[1] is player name, rest is the message
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /msg <player> <message>").color("#FF5555"));
            return;
        }

        String playerName = parts[1];

        // Get everything after the player name as the message
        int messageStart = input.indexOf(playerName) + playerName.length();
        String message = input.substring(messageStart).trim();

        if (message.isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /msg <player> <message>").color("#FF5555"));
            return;
        }

        // Find target player
        PlayerRef target = playerDataManager.findPlayerByName(playerName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + playerName).color("#FF0000"));
            return;
        }

        // Can't message yourself
        if (senderId.equals(target.getUuid())) {
            ctx.sendMessage(Message.raw("You cannot message yourself").color("#FF0000"));
            return;
        }

        // Send the private message
        ChatListener chatListener = plugin.getChatListener();
        chatListener.sendPrivateMessage(sender, target, message);
    }
}
