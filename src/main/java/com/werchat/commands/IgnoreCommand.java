package com.werchat.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.werchat.WerchatPlugin;
import com.werchat.storage.PlayerDataManager;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * /ignore command - toggle ignoring a player
 */
public class IgnoreCommand extends CommandBase {

    private final WerchatPlugin plugin;
    private final PlayerDataManager playerDataManager;

    public IgnoreCommand(WerchatPlugin plugin) {
        super("ignore", "Ignore a player's messages", false);
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();

        this.setPermissionGroup(GameMode.Adventure);
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (!ctx.isPlayer()) {
            return;
        }

        UUID playerId = ctx.sender().getUuid();

        // Check permission
        PermissionsModule perms = PermissionsModule.get();
        if (!perms.hasPermission(playerId, "werchat.ignore")
                && !perms.hasPermission(playerId, "werchat.*")
                && !perms.hasPermission(playerId, "*")) {
            ctx.sendMessage(Message.raw("You don't have permission to ignore players").color("#FF5555"));
            return;
        }

        // Parse arguments
        String input = ctx.getInputString();
        String[] parts = input.trim().split("\\s+");
        String targetName = parts.length > 1 ? parts[1] : null;

        if (targetName == null) {
            ctx.sendMessage(Message.raw("Usage: /ignore <player>").color("#FF5555"));
            ctx.sendMessage(Message.raw("Use /ignorelist to see ignored players").color("#AAAAAA"));
            return;
        }

        // Find target player
        PlayerRef target = playerDataManager.findPlayerByName(targetName);
        if (target == null) {
            ctx.sendMessage(Message.raw("Player not found: " + targetName).color("#FF5555"));
            return;
        }

        UUID targetId = target.getUuid();

        // Can't ignore yourself
        if (targetId.equals(playerId)) {
            ctx.sendMessage(Message.raw("You cannot ignore yourself").color("#FF5555"));
            return;
        }

        // Toggle ignore
        boolean wasIgnoring = playerDataManager.isIgnoring(playerId, targetId);
        playerDataManager.toggleIgnore(playerId, targetId);

        if (wasIgnoring) {
            ctx.sendMessage(Message.join(
                Message.raw("You are no longer ignoring ").color("#55FF55"),
                Message.raw(target.getUsername()).color("#FFFFFF")
            ));
        } else {
            ctx.sendMessage(Message.join(
                Message.raw("You are now ignoring ").color("#FFAA00"),
                Message.raw(target.getUsername()).color("#FFFFFF")
            ));
        }
    }
}
