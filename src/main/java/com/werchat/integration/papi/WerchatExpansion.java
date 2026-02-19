package com.werchat.integration.papi;

import at.helpch.placeholderapi.PlaceholderAPI;
import at.helpch.placeholderapi.PlaceholderAPIPlugin;
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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Built-in PlaceholderAPI expansion for Werchat data.
 */
public class WerchatExpansion extends PlaceholderExpansion implements Configurable<WerchatExpansionConfig> {

    private static final String CHANNEL_PREFIX = "channel_";
    private static final String SELECTED_CHANNEL_KEY_PREFIX = "selected_channel_";
    private static final String KEY_SEPARATOR = "__";
    private static final String DEFAULT_ACTIVE_CHANNEL_ALIAS = "active";
    private static final List<String> CHANNEL_PLACEHOLDER_KEYS = List.of(
        "effective_msg_colorhex",
        "has_quickchatsymbol",
        "join_permission",
        "read_permission",
        "speak_permission",
        "quickchatsymbol",
        "moderator_names",
        "moderator_count",
        "member_names",
        "muted_count",
        "muted_names",
        "member_count",
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
                    "Use selected_channel_<key> for focused channel and channel_<name>_<key> for fixed channels.",
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

        if (normalizedParams.startsWith(SELECTED_CHANNEL_KEY_PREFIX)) {
            String key = params.substring(SELECTED_CHANNEL_KEY_PREFIX.length()).toLowerCase(Locale.ROOT);
            if (key.isBlank() || !isKnownChannelPlaceholderKey(key)) {
                return null;
            }
            if (playerId == null) {
                return "";
            }

            Channel selected = channelManager.getChannel(playerDataManager.getFocusedChannel(playerId));
            if (selected == null) {
                return "";
            }

            return resolveChannelPlaceholder(selected, playerDataManager, playerId, key);
        }

        if (normalizedParams.startsWith(CHANNEL_PREFIX)) {
            String remaining = params.substring(CHANNEL_PREFIX.length());
            ChannelPlaceholderRequest request = parseLegacyChannelKeyedPlaceholderRequest(remaining);
            if (request != null) {
                Channel channel = resolveChannel(channelManager, playerDataManager, playerId, request.selector());
                if (channel != null) {
                    return resolveChannelPlaceholder(channel, playerDataManager, playerId, request.key());
                }
            }

            // Backup keyed syntax for edge cases:
            // %werchat_channel_<selector>__<key>%
            request = parseExplicitChannelKeyedPlaceholderRequest(remaining);
            if (request != null) {
                Channel channel = resolveChannel(channelManager, playerDataManager, playerId, request.selector());
                if (channel != null) {
                    return resolveChannelPlaceholder(channel, playerDataManager, playerId, request.key());
                }
            }

            // Alias: %werchat_channel_<selector>% => channel name
            Channel channel = resolveChannel(channelManager, playerDataManager, playerId, remaining);
            if (channel == null) {
                return "";
            }
            return resolveChannelPlaceholder(channel, playerDataManager, playerId, "name");
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
            case "msg_gradient" -> playerId != null
                ? formatGradient(playerDataManager.getMsgColor(playerId), playerDataManager.getMsgGradientEnd(playerId))
                : "";
            case "msg_gradient_end" -> playerId != null ? nullToEmpty(playerDataManager.getMsgGradientEnd(playerId)) : "";
            case "nick_color" -> playerId != null ? nullToEmpty(playerDataManager.getNickColor(playerId)) : "";
            case "nick_gradient_end" -> playerId != null ? nullToEmpty(playerDataManager.getNickGradientEnd(playerId)) : "";
            case "nick" -> playerId != null ? nullToEmpty(playerDataManager.getNickname(playerId)) : "";
            case "display_name" -> playerId != null ? playerDataManager.getDisplayName(playerId) : "";
            default -> null;
        };
    }

    private ChannelPlaceholderRequest parseLegacyChannelKeyedPlaceholderRequest(String remaining) {
        if (remaining == null || remaining.isBlank()) {
            return null;
        }

        // Primary keyed syntax:
        // %werchat_channel_<selector>_<key>%
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

            String selector = normalizeSelector(remaining.substring(0, separatorIndex));
            if (selector == null) {
                return null;
            }

            return new ChannelPlaceholderRequest(selector, key);
        }

        return null;
    }

    private ChannelPlaceholderRequest parseExplicitChannelKeyedPlaceholderRequest(String remaining) {
        if (remaining == null || remaining.isBlank()) {
            return null;
        }

        int explicitSeparator = remaining.lastIndexOf(KEY_SEPARATOR);
        if (explicitSeparator <= 0 || explicitSeparator + KEY_SEPARATOR.length() >= remaining.length()) {
            return null;
        }

        String selector = normalizeSelector(remaining.substring(0, explicitSeparator));
        String key = remaining.substring(explicitSeparator + KEY_SEPARATOR.length()).toLowerCase(Locale.ROOT);
        if (selector == null || !isKnownChannelPlaceholderKey(key)) {
            return null;
        }

        return new ChannelPlaceholderRequest(selector, key);
    }

    private boolean isKnownChannelPlaceholderKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return CHANNEL_PLACEHOLDER_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    private Channel resolveChannel(ChannelManager channelManager,
                                   PlayerDataManager playerDataManager,
                                   UUID playerId,
                                   String selector) {
        String normalizedSelector = normalizeSelector(selector);
        if (normalizedSelector == null) {
            return null;
        }

        if (normalizedSelector.equalsIgnoreCase(getActiveChannelAlias())) {
            if (playerId == null) {
                return null;
            }
            return channelManager.getChannel(playerDataManager.getFocusedChannel(playerId));
        }

        return channelManager.getChannel(normalizedSelector);
    }

    private String normalizeSelector(String selector) {
        if (selector == null) {
            return null;
        }

        String normalized = selector.trim();
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() >= 2 && normalized.startsWith("{") && normalized.endsWith("}")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
            if (normalized.isBlank()) {
                return null;
            }
        }

        return normalized;
    }

    private String resolveChannelPlaceholder(Channel channel, PlayerDataManager playerDataManager,
                                             UUID playerId, String key) {
        return switch (key) {
            case "name" -> channel.getName();
            case "nick" -> channel.getNick();
            case "colorhex" -> channel.getColorHex();
            case "format" -> channel.getFormat();
            case "color" -> channel.getColorHex();
            case "effective_msg_colorhex" -> channel.getEffectiveMessageColorHex();
            case "join_permission" -> channel.getJoinPermission();
            case "read_permission" -> channel.getReadPermission();
            case "msg_color_hex" -> nullToEmpty(channel.getMessageColorHex());
            case "msg_color" -> nullToEmpty(channel.getMessageColorHex());
            case "quickchatsymbol" -> nullToEmpty(channel.getQuickChatSymbol());
            case "speak_permission" -> channel.getSpeakPermission();
            case "worlds_count" -> String.valueOf(channel.getWorlds().size());
            case "worlds" -> channel.getWorldsDisplay();
            case "distance" -> String.valueOf(channel.getDistance());
            case "member_count" -> String.valueOf(channel.getMemberCount());
            case "moderator_count" -> String.valueOf(channel.getModerators().size());
            case "muted_count" -> String.valueOf(channel.getMuted().size());
            case "member_names" -> joinPlayerNames(channel.getMembers(), playerDataManager);
            case "moderator_names" -> joinPlayerNames(channel.getModerators(), playerDataManager);
            case "muted_names" -> joinPlayerNames(channel.getMuted(), playerDataManager);
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
        String alias = readActiveChannelAliasFromPapiConfig();
        if (alias == null || alias.isBlank()) {
            return DEFAULT_ACTIVE_CHANNEL_ALIAS;
        }
        return alias;
    }

    private String readActiveChannelAliasFromPapiConfig() {
        // Primary path: read raw expansion config directly from PlaceholderAPI.
        // This stays safe when /papi reload leaves persistent expansions registered but
        // rehydrates config entries as generic maps.
        try {
            Object rawConfig = PlaceholderAPIPlugin.instance()
                .configManager()
                .config()
                .expansions()
                .get(getIdentifier());

            String alias = extractActiveChannelAlias(rawConfig);
            if (alias != null && !alias.isBlank()) {
                return alias;
            }
        } catch (Throwable ignored) {
            // Fall through to typed fallback.
        }

        // Fallback path: typed Configurable config when available.
        try {
            WerchatExpansionConfig typedConfig = getConfig();
            String alias = typedConfig != null ? typedConfig.activeChannel() : null;
            if (alias != null && !alias.isBlank()) {
                return alias;
            }
        } catch (Throwable ignored) {
            // Use default alias when config cannot be read.
        }

        return null;
    }

    private String extractActiveChannelAlias(Object configObject) {
        if (configObject == null) {
            return null;
        }

        if (configObject instanceof WerchatExpansionConfig typedConfig) {
            return typedConfig.activeChannel();
        }

        if (configObject instanceof java.util.Map<?, ?> configMap) {
            Object alias = configMap.get("active_channel");
            if (alias == null) {
                alias = configMap.get("activeChannel");
            }
            if (alias != null) {
                String text = String.valueOf(alias).trim();
                return text.isBlank() ? null : text;
            }
        }

        return null;
    }

    private String joinPlayerNames(Set<UUID> playerIds, PlayerDataManager playerDataManager) {
        return playerIds.stream()
            .map(playerId -> {
                PlayerRef online = Universe.get().getPlayer(playerId);
                if (online != null) {
                    return online.getUsername();
                }

                String knownName = playerDataManager.getKnownName(playerId);
                if (knownName != null && !knownName.isBlank()) {
                    return knownName;
                }

                return playerId.toString().substring(0, 8);
            })
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining(", "));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatGradient(String start, String end) {
        if (start == null || start.isBlank() || end == null || end.isBlank()) {
            return "";
        }
        return start + "," + end;
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
