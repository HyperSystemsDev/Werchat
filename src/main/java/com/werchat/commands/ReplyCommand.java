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
 * /r command - reply to last private message
 */
public class ReplyCommand extends CommandBase {

    private final WerchatPlugin plugin;
    private final PlayerDataManager playerDataManager;

    public ReplyCommand(WerchatPlugin plugin) {
        super("r", "Reply to PM", false);
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();

        // Available to all players in Adventure mode
        this.setPermissionGroup(GameMode.Adventure);

        // Allow any arguments - we parse them manually
        this.setAllowsExtraArguments(true);

        this.addAliases("reply");
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

        // Parse raw input: "r hello world" -> get everything after "r "
        String input = ctx.getInputString();
        String[] parts = input.trim().split("\\s+", 2); // Split into max 2 parts

        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /r <message>").color("#FF5555"));
            return;
        }

        String message = parts[1].trim();

        UUID lastFromId = playerDataManager.getLastMessageFrom(senderId);

        if (lastFromId == null) {
            ctx.sendMessage(Message.raw("No one to reply to").color("#FF0000"));
            return;
        }

        PlayerRef target = playerDataManager.getOnlinePlayer(lastFromId);
        if (target == null) {
            ctx.sendMessage(Message.raw("That player is no longer online").color("#FF0000"));
            return;
        }

        // Send the reply
        ChatListener chatListener = plugin.getChatListener();
        chatListener.sendPrivateMessage(sender, target, message);
    }
}
