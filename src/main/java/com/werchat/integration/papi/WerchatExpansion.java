package com.werchat.integration.papi;

import at.helpch.placeholderapi.PlaceholderAPI;
import at.helpch.placeholderapi.expansion.Configurable;
import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.storage.PlayerDataManager;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Built-in PlaceholderAPI expansion for Werchat data.
 */
public class WerchatExpansion extends PlaceholderExpansion implements Configurable<WerchatExpansionConfig> {

    private static final String CHANNEL_PREFIX = "channel_";
    private static final List<String> CHANNEL_PLACEHOLDER_KEYS = List.of(
        "effective_msg_colorhex",
        "has_quickchatsymbol",
        "join_permission",
        "see_permission",
        "speak_permission",
        "quickchatsymbol",
        "moderator_count",
        "muted_count",
        "member_count",
        "worlds_display",
        "worlds_count",
        "msg_color_hex",
        "has_password",
        "has_msg_color",
        "has_worlds",
        "is_moderator",
        "is_focusable",
        "is_default",
        "is_autojoin",
        "is_global",
        "is_local",
        "is_verbose",
        "is_member",
        "is_banned",
        "is_muted",
        "owner_name",
        "distance",
        "format",
        "colorhex",
        "worlds",
        "owner",
        "color",
        "nick",
        "name",
        "msg_color"
    ).stream()
        .sorted((a, b) -> Integer.compare(b.length(), a.length()))
        .toList();

    private final WerchatPlugin plugin;

    public WerchatExpansion(WerchatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "werchat";
    }

    @Override
    public String getAuthor() {
        return "Wer";
    }

    @Override
    public String getVersion() {
        return plugin.getManifest().getVersion().toString();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        String activeChannel = getActiveChannelAlias();

        boolean conflict = plugin.getChannelManager().getAllChannels().stream()
            .map(Channel::getName)
            .anyMatch(activeChannel::equalsIgnoreCase);

        if (conflict) {
            plugin.getLogger().at(Level.WARNING).log(
                "[Werchat PAPI] Active channel alias '%s' matches a real channel name. " +
                    "Use channel_active_* placeholders for focused channel and channel_<name>_* for fixed channels.",
                activeChannel
            );
        }

        return super.canRegister();
    }

    @Override
    public String onPlaceholderRequest(PlayerRef playerRef, String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        ChannelManager channelManager = plugin.getChannelManager();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        UUID playerId = playerRef != null ? playerRef.getUuid() : null;

        String normalizedParams = params.toLowerCase(Locale.ROOT);

        if (normalizedParams.startsWith(CHANNEL_PREFIX)) {
            String remaining = params.substring(CHANNEL_PREFIX.length());
            ChannelPlaceholderRequest request = parseChannelPlaceholderRequest(remaining);
            if (request == null) {
                return null;
            }

            Channel channel = resolveChannel(channelManager, playerDataManager, playerId, request.selector());
            if (channel == null) {
                return "";
            }

            return resolveChannelPlaceholder(channel, playerDataManager, playerId, request.key());
        }

        return switch (normalizedParams) {
            case "channels_total" -> String.valueOf(channelManager.getAllChannels().size());
            case "channels" -> channelManager.getAllChannels().stream()
                .map(Channel::getName)
                .collect(Collectors.joining(", "));
            case "default_channel" -> channelManager.getDefaultChannel() != null
                ? channelManager.getDefaultChannel().getName()
                : "";
            case "channel", "selected_channel" -> playerId != null ? playerDataManager.getFocusedChannel(playerId) : "";
            case "ignored_players_total" -> playerId != null
                ? String.valueOf(playerDataManager.getIgnoredPlayers(playerId).size())
                : "0";
            case "ignored_players" -> playerId != null
                ? playerDataManager.getIgnoredPlayers(playerId).stream()
                    .map(playerDataManager::getKnownName)
                    .filter(name -> name != null && !name.isEmpty())
                    .collect(Collectors.joining(", "))
                : "";
            case "known_name" -> playerId != null ? playerDataManager.getKnownName(playerId) : "";
            case "display_colour", "display_color" -> playerId != null ? nullToEmpty(playerDataManager.getDisplayColor(playerId)) : "";
            case "msg_color" -> playerId != null ? nullToEmpty(playerDataManager.getMsgColor(playerId)) : "";
            case "msg_gradient_end" -> playerId != null ? nullToEmpty(playerDataManager.getMsgGradientEnd(playerId)) : "";
            case "nick_color" -> playerId != null ? nullToEmpty(playerDataManager.getNickColor(playerId)) : "";
            case "nick_gradient_end" -> playerId != null ? nullToEmpty(playerDataManager.getNickGradientEnd(playerId)) : "";
            case "nick" -> playerId != null ? nullToEmpty(playerDataManager.getNickname(playerId)) : "";
            case "display_name" -> playerId != null ? playerDataManager.getDisplayName(playerId) : "";
            default -> null;
        };
    }

    private ChannelPlaceholderRequest parseChannelPlaceholderRequest(String remaining) {
        if (remaining == null || remaining.isBlank()) {
            return null;
        }

        for (String key : CHANNEL_PLACEHOLDER_KEYS) {
            int keyLen = key.length();
            int separatorIndex = remaining.length() - keyLen - 1;
            if (separatorIndex <= 0 || separatorIndex >= remaining.length()) {
                continue;
            }
            if (remaining.charAt(separatorIndex) != '_') {
                continue;
            }
            if (!remaining.regionMatches(true, separatorIndex + 1, key, 0, keyLen)) {
                continue;
            }

            String selector = remaining.substring(0, separatorIndex);
            if (selector.isBlank()) {
                return null;
            }

            return new ChannelPlaceholderRequest(selector, key);
        }

        // Alias: %werchat_channel_<selector>% => channel name
        return new ChannelPlaceholderRequest(remaining, "name");
    }

    private Channel resolveChannel(ChannelManager channelManager,
                                   PlayerDataManager playerDataManager,
                                   UUID playerId,
                                   String selector) {
        if (selector == null || selector.isBlank()) {
            return null;
        }

        if (selector.equalsIgnoreCase(getActiveChannelAlias())) {
            if (playerId == null) {
                return null;
            }
            return channelManager.getChannel(playerDataManager.getFocusedChannel(playerId));
        }

        return channelManager.getChannel(selector);
    }

    private String resolveChannelPlaceholder(Channel channel, PlayerDataManager playerDataManager,
                                             UUID playerId, String key) {
        return switch (key) {
            case "name" -> channel.getName();
            case "nick" -> channel.getNick();
            case "colorhex" -> channel.getColorHex();
            case "format" -> channel.getFormat();
            case "color" -> String.valueOf(channel.getColor());
            case "effective_msg_colorhex" -> channel.getEffectiveMessageColorHex();
            case "join_permission" -> channel.getJoinPermission();
            case "msg_color_hex" -> nullToEmpty(channel.getMessageColorHex());
            case "msg_color" -> String.valueOf(channel.getMessageColor());
            case "quickchatsymbol" -> nullToEmpty(channel.getQuickChatSymbol());
            case "see_permission" -> channel.getSeePermission();
            case "speak_permission" -> channel.getSpeakPermission();
            case "worlds_display" -> channel.getWorldsDisplay();
            case "worlds_count" -> String.valueOf(channel.getWorlds().size());
            case "worlds" -> String.join(", ", channel.getWorlds());
            case "distance" -> String.valueOf(channel.getDistance());
            case "member_count" -> String.valueOf(channel.getMemberCount());
            case "moderator_count" -> String.valueOf(channel.getModerators().size());
            case "muted_count" -> String.valueOf(channel.getMuted().size());
            case "owner" -> channel.getOwner() != null ? channel.getOwner().toString() : "";
            case "owner_name" -> {
                UUID owner = channel.getOwner();
                if (owner == null) {
                    yield "";
                }
                PlayerRef player = Universe.get().getPlayer(owner);
                if (player != null) {
                    yield player.getUsername();
                }
                yield playerDataManager.getKnownName(owner);
            }
            case "is_autojoin" -> PlaceholderAPI.booleanValue(channel.isAutoJoin());
            case "is_muted" -> PlaceholderAPI.booleanValue(playerId != null && channel.isMuted(playerId));
            case "is_banned" -> PlaceholderAPI.booleanValue(playerId != null && channel.isBanned(playerId));
            case "is_member" -> PlaceholderAPI.booleanValue(playerId != null && channel.isMember(playerId));
            case "is_moderator" -> PlaceholderAPI.booleanValue(playerId != null && channel.isModerator(playerId));
            case "is_verbose" -> PlaceholderAPI.booleanValue(channel.isVerbose());
            case "is_default" -> PlaceholderAPI.booleanValue(channel.isDefault());
            case "is_focusable" -> PlaceholderAPI.booleanValue(channel.isFocusable());
            case "is_global" -> PlaceholderAPI.booleanValue(channel.isGlobal());
            case "is_local" -> PlaceholderAPI.booleanValue(channel.isLocal());
            case "has_password" -> PlaceholderAPI.booleanValue(channel.hasPassword());
            case "has_msg_color" -> PlaceholderAPI.booleanValue(channel.hasMessageColor());
            case "has_worlds" -> PlaceholderAPI.booleanValue(channel.hasWorlds());
            case "has_quickchatsymbol" -> PlaceholderAPI.booleanValue(channel.hasQuickChatSymbol());
            default -> null;
        };
    }

    private String getActiveChannelAlias() {
        WerchatExpansionConfig config = getConfig();
        if (config == null || config.activeChannel() == null || config.activeChannel().isBlank()) {
            return "active";
        }
        return config.activeChannel();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Class<WerchatExpansionConfig> provideConfigType() {
        return WerchatExpansionConfig.class;
    }

    @Override
    public WerchatExpansionConfig provideDefault() {
        return new WerchatExpansionConfig("active");
    }

    private record ChannelPlaceholderRequest(String selector, String key) {
    }
}
