package com.werchat.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.werchat.WerchatPlugin;
import com.werchat.channels.Channel;
import com.werchat.channels.ChannelManager;
import com.werchat.integration.papi.PAPIIntegration;
import com.werchat.storage.PlayerDataManager;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Main Werchat settings UI page shown from /ch and /ch settings.
 * Includes moderator-only per-channel management tabs.
 */
public class ChannelSettingsPage extends InteractiveCustomUIPage<ChannelSettingsPage.PageEventData> {

    private static final String TAB_MAIN = "main";
    private static final String TAB_CHANNELS = "channels";
    private static final String TAB_HELP = "help";
    private static final String TAB_MOD_PREFIX = "mod:";
    private static final String MOD_LIST_BANS = "bans";
    private static final String MOD_LIST_MUTES = "mutes";
    private static final String MOD_LIST_MODERATORS = "moderators";
    private static final Set<Integer> DISTANCE_PRESETS = Set.of(0, 25, 50, 100, 150, 250, 500);
    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int MAX_CHANNEL_DESCRIPTION_LENGTH = 180;
    private static final int MAX_CHANNEL_MOTD_LENGTH = 220;
    private static final long TOGGLE_ACTION_COOLDOWN_MS = 1200L;
    private static final String CHANNEL_OWNER_COLOR = "#FFAA00";
    private static final String CHANNEL_MODERATOR_COLOR = "#55FF55";
    private static final String CHANNEL_MEMBER_COLOR = "#FFFFFF";

    private final WerchatPlugin plugin;
    private final ChannelManager channelManager;
    private final PlayerDataManager playerDataManager;
    private final PAPIIntegration papi;

    private String activeTab = TAB_MAIN;
    private String selectedMainChannel;
    private boolean showJoinPasswordModal;
    private String pendingJoinPasswordChannel;
    private boolean showModerationListModal;
    private String moderationListMode;
    private String statusMessage = "Ready.";
    private String moderatorActionError = "";
    private String moderatorActionSuccess = "";
    private long nextToggleActionAllowedAtMs = 0L;

    public ChannelSettingsPage(WerchatPlugin plugin, PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageEventData.CODEC);
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.papi = PAPIIntegration.register(plugin);
    }

    public ChannelSettingsPage(WerchatPlugin plugin, PlayerRef playerRef, String initialTab) {
        this(plugin, playerRef);
        this.activeTab = normalizeInitialTab(initialTab);
    }

    private String normalizeInitialTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return TAB_MAIN;
        }
        String normalized = tab.trim().toLowerCase(Locale.ROOT);
        if (TAB_HELP.equals(normalized)) {
            return TAB_HELP;
        }
        if (TAB_CHANNELS.equals(normalized)) {
            return TAB_CHANNELS;
        }
        return TAB_MAIN;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder cmd,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        cmd.append("Werchat/ChannelSettings.ui");
        bindStaticEvents(events);

        UUID viewerId = playerRef.getUuid();
        List<Channel> moderatorChannels = getModeratorChannels(viewerId);
        validateActiveTab(moderatorChannels);

        applyTabState(cmd);
        renderModeratorTabs(cmd, moderatorChannels);
        renderMainPanel(cmd, viewerId);
        renderHelpPanel(cmd, viewerId);
        renderChannels(cmd, events, viewerId);
        renderJoinPasswordModal(cmd);
        renderModeratorPanel(cmd, viewerId);
        renderModerationListModal(cmd, events, viewerId);
        renderStatusBanner(cmd);
        cmd.set("#FooterMessage.Text", statusMessage);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageEventData data
    ) {
        super.handleDataEvent(ref, store, data);
        if (data.button == null || data.button.isBlank()) {
            return;
        }

        switch (data.button) {
            case "Refresh" -> {
                statusMessage = "Refreshed.";
                rebuild();
            }
            case "Tab" -> {
                if (data.tab != null && !data.tab.isBlank()) {
                    activeTab = data.tab.toLowerCase(Locale.ROOT);
                    closeJoinPasswordModal();
                    closeModerationListModal();
                    moderatorActionError = "";
                    moderatorActionSuccess = "";
                    statusMessage = "Viewing " + activeTab + ".";
                }
                rebuild();
            }
            case "ModTab" -> {
                if (data.channel == null || data.channel.isBlank()) {
                    statusMessage = "Unknown moderator tab.";
                    rebuild();
                    return;
                }
                activeTab = TAB_MOD_PREFIX + data.channel.toLowerCase(Locale.ROOT);
                closeJoinPasswordModal();
                closeModerationListModal();
                moderatorActionError = "";
                moderatorActionSuccess = "";
                statusMessage = "Managing " + data.channel + ".";
                rebuild();
            }
            case "Focus" -> {
                handleFocusAction(data.channel);
                rebuild();
            }
            case "Join" -> {
                handleJoinAction(data.channel, null);
                rebuild();
            }
            case "JoinPasswordConfirm" -> {
                handleJoinPasswordConfirm(data.password);
                rebuild();
            }
            case "JoinPasswordCancel" -> {
                closeJoinPasswordModal();
                statusMessage = "Join cancelled.";
                rebuild();
            }
            case "OpenModerationList" -> {
                openModerationListModal(data.action);
                rebuild();
            }
            case "CloseModerationList" -> {
                closeModerationListModal();
                moderatorActionError = "";
                moderatorActionSuccess = "";
                statusMessage = "Closed moderation list.";
                rebuild();
            }
            case "Leave" -> {
                handleLeaveAction(data.channel);
                rebuild();
            }
            case "ModAction" -> {
                handleModeratorAction(data.action, data.target, data.value, data.password);
                syncModeratorActionFeedbackFromStatus();
                sendInPlaceUpdatePreserveScroll();
            }
            case "ProfileAction" -> {
                handleProfileAction(data.action, data.value, data.extra);
                sendInPlaceUpdatePreserveScroll();
            }
            default -> {
                statusMessage = "Unknown UI action.";
                rebuild();
            }
        }
    }

    private void sendInPlaceUpdatePreserveScroll() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        UUID viewerId = playerRef.getUuid();
        List<Channel> moderatorChannels = getModeratorChannels(viewerId);
        validateActiveTab(moderatorChannels);

        applyTabState(cmd);
        renderModeratorTabs(cmd, moderatorChannels);
        renderMainPanel(cmd, viewerId);
        renderHelpPanel(cmd, viewerId);
        renderChannels(cmd, events, viewerId);
        renderJoinPasswordModal(cmd);
        renderModeratorPanel(cmd, viewerId);
        renderModerationListModal(cmd, events, viewerId);
        renderStatusBanner(cmd);
        cmd.set("#FooterMessage.Text", statusMessage);
        sendUpdate(cmd, events, false);
    }

    private void bindStaticEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
            EventData.of("Button", "Refresh"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabMainButton",
            EventData.of("Button", "Tab").append("Tab", TAB_MAIN), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabChannelsButton",
            EventData.of("Button", "Tab").append("Tab", TAB_CHANNELS), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModeratorTabsOpenButton",
            EventData.of("Button", "ModTab").append("@Channel", "#ModeratorTabsDropdown.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#JoinPasswordModalConfirmButton",
            EventData.of("Button", "JoinPasswordConfirm")
                .append("@Password", "#JoinPasswordModalInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#JoinPasswordModalCancelButton",
            EventData.of("Button", "JoinPasswordCancel"), false);

        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MainFocusDropdown",
            EventData.of("Button", "ProfileAction").append("Action", "preview_selected")
                .append("@Value", "#MainFocusDropdown.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainSetFocusButton",
            EventData.of("Button", "ProfileAction").append("Action", "set_focus")
                .append("@Value", "#MainFocusDropdown.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainQuickChannelsButton",
            EventData.of("Button", "Tab").append("Tab", TAB_CHANNELS), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainSetNickButton",
            EventData.of("Button", "ProfileAction").append("Action", "set_nickname")
                .append("@Value", "#MainNickInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainClearNickButton",
            EventData.of("Button", "ProfileAction").append("Action", "clear_nickname"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainApplyNickColorButton",
            EventData.of("Button", "ProfileAction").append("Action", "set_nick_solid")
                .append("@Value", "#MainNickStartColorPicker.Color"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainApplyNickGradientButton",
            EventData.of("Button", "ProfileAction").append("Action", "set_nick_gradient")
                .append("@Value", "#MainNickStartColorPicker.Color")
                .append("@Extra", "#MainNickEndColorPicker.Color"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainClearNickColorButton",
            EventData.of("Button", "ProfileAction").append("Action", "clear_nick_color"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainApplyMsgColorButton",
            EventData.of("Button", "ProfileAction").append("Action", "set_msg_solid")
                .append("@Value", "#MainMsgStartColorPicker.Color"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainApplyMsgGradientButton",
            EventData.of("Button", "ProfileAction").append("Action", "set_msg_gradient")
                .append("@Value", "#MainMsgStartColorPicker.Color")
                .append("@Extra", "#MainMsgEndColorPicker.Color"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MainClearMsgColorButton",
            EventData.of("Button", "ProfileAction").append("Action", "clear_msg_color"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModBanButton",
            EventData.of("Button", "ModAction").append("Action", "ban")
                .append("@Target", "#ModTargetInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModMuteButton",
            EventData.of("Button", "ModAction").append("Action", "mute")
                .append("@Target", "#ModTargetInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModAddModeratorButton",
            EventData.of("Button", "ModAction").append("Action", "addmod")
                .append("@Target", "#ModTargetInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModOpenBansButton",
            EventData.of("Button", "OpenModerationList").append("Action", MOD_LIST_BANS), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModOpenMutesButton",
            EventData.of("Button", "OpenModerationList").append("Action", MOD_LIST_MUTES), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModOpenModeratorsButton",
            EventData.of("Button", "OpenModerationList").append("Action", MOD_LIST_MODERATORS), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModerationListModalCloseButton",
            EventData.of("Button", "CloseModerationList"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetNickButton",
            EventData.of("Button", "ModAction").append("Action", "set_nick")
                .append("@Value", "#ModNickInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetDescriptionButton",
            EventData.of("Button", "ModAction").append("Action", "set_description")
                .append("@Value", "#ModDescriptionInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModClearDescriptionButton",
            EventData.of("Button", "ModAction").append("Action", "clear_description"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ModDescriptionEnabledCheck",
            EventData.of("Button", "ModAction").append("Action", "toggle_description"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetMotdButton",
            EventData.of("Button", "ModAction").append("Action", "set_motd")
                .append("@Value", "#ModMotdInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModClearMotdButton",
            EventData.of("Button", "ModAction").append("Action", "clear_motd"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ModMotdEnabledCheck",
            EventData.of("Button", "ModAction").append("Action", "toggle_motd"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetDistanceButton",
            EventData.of("Button", "ModAction").append("Action", "set_distance")
                .append("@Value", "#ModDistanceDropdown.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetCustomDistanceButton",
            EventData.of("Button", "ModAction").append("Action", "set_distance")
                .append("@Value", "#ModDistanceInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetPasswordButton",
            EventData.of("Button", "ModAction").append("Action", "set_password")
                .append("@Password", "#ModPasswordInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModClearPasswordButton",
            EventData.of("Button", "ModAction").append("Action", "clear_password"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetTagColorButton",
            EventData.of("Button", "ModAction").append("Action", "set_tag_color")
                .append("@Value", "#ModTagColorPicker.Color"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModSetTextColorButton",
            EventData.of("Button", "ModAction").append("Action", "set_text_color")
                .append("@Value", "#ModTextColorPicker.Color"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModClearTextColorButton",
            EventData.of("Button", "ModAction").append("Action", "clear_text_color"), false);
    }

    private void applyTabState(UICommandBuilder cmd) {
        boolean main = TAB_MAIN.equals(activeTab);
        boolean channels = TAB_CHANNELS.equals(activeTab);
        boolean help = TAB_HELP.equals(activeTab);
        boolean moderator = activeTab.startsWith(TAB_MOD_PREFIX);

        cmd.set("#MainPanel.Visible", main);
        cmd.set("#HelpPanel.Visible", help);
        cmd.set("#ChannelsPanel.Visible", channels);
        cmd.set("#ModeratorPanel.Visible", moderator);
        cmd.set("#ModeratorTabsSection.Visible", channels || moderator);

        cmd.set("#TabMainButton.Text", main ? "[Main]" : "Main");
        cmd.set("#TabChannelsButton.Text", channels ? "[Channels]" : "Channels");
    }

    private void renderModeratorTabs(UICommandBuilder cmd, List<Channel> moderatorChannels) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        String activeModeratorName = activeTab.startsWith(TAB_MOD_PREFIX)
            ? activeTab.substring(TAB_MOD_PREFIX.length())
            : null;
        String selectedValue = null;

        for (Channel channel : moderatorChannels) {
            entries.add(new DropdownEntryInfo(
                LocalizableString.fromString(buildModeratorDropdownLabel(channel)),
                channel.getName()
            ));
            if (activeModeratorName != null && channel.getName().equalsIgnoreCase(activeModeratorName)) {
                selectedValue = channel.getName();
            }
        }

        boolean hasModeratorChannels = !moderatorChannels.isEmpty();
        cmd.set("#ModeratorTabsLabel.Text", "Moderator Channels (" + moderatorChannels.size() + ")");
        cmd.set("#ModeratorTabsDropdown.Visible", hasModeratorChannels);
        cmd.set("#ModeratorTabsOpenButton.Visible", hasModeratorChannels);
        cmd.set("#ModeratorTabsEmptyLabel.Visible", !hasModeratorChannels);

        if (!hasModeratorChannels) {
            cmd.set("#ModeratorTabsDropdown.Disabled", true);
            cmd.set("#ModeratorTabsOpenButton.Disabled", true);
            cmd.set("#ModeratorTabsDropdown.Entries", List.of(
                new DropdownEntryInfo(LocalizableString.fromString("No moderated channels"), "")
            ));
            cmd.set("#ModeratorTabsDropdown.Value", "");
            return;
        }

        if (selectedValue == null) {
            selectedValue = moderatorChannels.get(0).getName();
        }

        cmd.set("#ModeratorTabsDropdown.Entries", entries);
        cmd.set("#ModeratorTabsDropdown.Value", selectedValue);
        cmd.set("#ModeratorTabsDropdown.Disabled", false);
        cmd.set("#ModeratorTabsOpenButton.Disabled", false);
    }

    private String buildModeratorDropdownLabel(Channel channel) {
        String name = channel.getName();
        String nick = channel.getNick();
        if (nick == null || nick.isBlank() || nick.equalsIgnoreCase(name)) {
            return name;
        }
        return name + " [" + nick + "]";
    }

    private void renderMainPanel(UICommandBuilder cmd, UUID viewerId) {
        String focused = playerDataManager.getFocusedChannel(viewerId);
        String displayName = playerDataManager.getDisplayName(viewerId);
        String nickname = playerDataManager.getNickname(viewerId);
        String nickColor = playerDataManager.getNickColor(viewerId);
        String nickGradient = playerDataManager.getNickGradientEnd(viewerId);
        String msgColor = playerDataManager.getMsgColor(viewerId);
        String msgGradient = playerDataManager.getMsgGradientEnd(viewerId);

        List<Channel> focusableChannels = getFocusableChannels(viewerId);
        List<DropdownEntryInfo> focusEntries = new ArrayList<>();
        List<String> focusValues = new ArrayList<>();
        for (Channel channel : focusableChannels) {
            String label = channel.getName();
            if (channel.getNick() != null && !channel.getNick().isBlank()
                && !channel.getNick().equalsIgnoreCase(channel.getName())) {
                label = channel.getNick() + " (" + channel.getName() + ")";
            }
            focusEntries.add(new DropdownEntryInfo(LocalizableString.fromString(label), channel.getName()));
            focusValues.add(channel.getName());
        }
        if (focusEntries.isEmpty()) {
            focusEntries.add(new DropdownEntryInfo(LocalizableString.fromString("No joined channels"), ""));
            focusValues.add("");
        }

        cmd.set("#MainFocusDropdown.Entries", focusEntries);

        String selectedFocus = selectedMainChannel;
        if (selectedFocus == null || selectedFocus.isBlank()) {
            selectedFocus = focused;
        }
        if (selectedFocus == null || selectedFocus.isBlank() || !containsDropdownValue(focusValues, selectedFocus)) {
            selectedFocus = focusValues.get(0);
        }
        selectedMainChannel = selectedFocus;
        cmd.set("#MainFocusDropdown.Value", selectedFocus == null ? "" : selectedFocus);

        int joinedCount = 0;
        List<String> joinedChannelNames = new ArrayList<>();
        for (Channel channel : channelManager.getAllChannels()) {
            if (channel.isMember(viewerId)) {
                joinedCount++;
                joinedChannelNames.add(channel.getName());
            }
        }
        joinedChannelNames.sort(String.CASE_INSENSITIVE_ORDER);

        renderDisplayNamePreview(cmd, displayName, nickColor, nickGradient);
        cmd.set("#MainJoinedCountValue.Text", String.valueOf(joinedCount));
        cmd.set("#MainRecentChannelsValue.Text", buildChannelStrip(joinedChannelNames, focused));
        cmd.set("#MainFocusedChannelValue.Text", focused == null || focused.isBlank() ? "None" : focused);

        cmd.set("#MainNicknameValue.Text", (nickname == null || nickname.isBlank()) ? "Not set" : nickname);
        cmd.set("#MainNickColorValue.Text", formatColorValue(nickColor, nickGradient));
        cmd.set("#MainMsgColorValue.Text", formatColorValue(msgColor, msgGradient));
        cmd.set("#MainMsgPreviewValue.Text", buildMessagePreviewText(msgColor, msgGradient));
        cmd.set("#MainMsgPreviewValue.Style.TextColor", resolveMessagePreviewColor(msgColor, focused));

        cmd.set("#MainNickInput.Value", nickname == null ? "" : nickname);
        cmd.set("#MainNickStartColorPicker.Color", defaultColor(nickColor));
        cmd.set("#MainNickEndColorPicker.Color", defaultColor(nickGradient != null ? nickGradient : nickColor));
        cmd.set("#MainMsgStartColorPicker.Color", defaultColor(msgColor));
        cmd.set("#MainMsgEndColorPicker.Color", defaultColor(msgGradient != null ? msgGradient : msgColor));

        boolean canSwitch = hasPermission(viewerId, "werchat.switch");
        boolean canNick = hasPermission(viewerId, "werchat.playernick");
        boolean canNickColor = hasPermission(viewerId, "werchat.nickcolor");
        boolean canMsgColor = hasPermission(viewerId, "werchat.msgcolor");
        Channel selectedChannel = (selectedFocus == null || selectedFocus.isBlank())
            ? null
            : channelManager.findChannel(selectedFocus);
        renderSelectedOnlineMembers(cmd, selectedChannel);

        cmd.set("#MainSetFocusButton.Disabled", !canSwitch || selectedFocus == null || selectedFocus.isBlank());
        cmd.set("#MainQuickChannelsButton.Disabled", false);
        cmd.set("#MainSetNickButton.Disabled", !canNick);
        cmd.set("#MainClearNickButton.Disabled", !canNick);
        cmd.set("#MainApplyNickColorButton.Disabled", !canNickColor);
        cmd.set("#MainApplyNickGradientButton.Disabled", !canNickColor);
        cmd.set("#MainClearNickColorButton.Disabled", !canNickColor);
        cmd.set("#MainApplyMsgColorButton.Disabled", !canMsgColor);
        cmd.set("#MainApplyMsgGradientButton.Disabled", !canMsgColor);
        cmd.set("#MainClearMsgColorButton.Disabled", !canMsgColor);
        renderHelpCommands(cmd, viewerId, "#MainHelpCommands");
    }

    private void renderHelpPanel(UICommandBuilder cmd, UUID viewerId) {
        renderHelpCommands(cmd, viewerId, "#HelpCommands");
    }

    private void renderHelpCommands(UICommandBuilder cmd, UUID viewerId, String containerSelector) {
        List<HelpCommandLine> lines = buildHelpCommandLines(viewerId);
        cmd.clear(containerSelector);

        for (int i = 0; i < lines.size(); i++) {
            HelpCommandLine line = lines.get(i);
            String base = containerSelector + "[" + i + "]";
            cmd.append(containerSelector, "Werchat/HelpCommandRow.ui");
            cmd.set(base + " #HelpCommandText.Text", line.text());
            cmd.set(base + " #HelpCommandText.Style.TextColor", line.colorHex());
        }
    }

    private List<HelpCommandLine> buildHelpCommandLines(UUID viewerId) {
        List<String> playerCommands = new ArrayList<>();
        playerCommands.add("/ch - Open settings");
        playerCommands.add("/ch settings - Open settings");
        playerCommands.add("/ch help - Open this help view");

        if (hasPermission(viewerId, "werchat.list")) {
            playerCommands.add("/ch list - Open channels tab");
        }
        if (hasPermission(viewerId, "werchat.switch")) {
            playerCommands.add("/ch <name> - Switch focused channel");
        }
        if (hasPermission(viewerId, "werchat.join")) {
            playerCommands.add("/ch join <channel> [password] - Join channel");
        }
        if (hasPermission(viewerId, "werchat.leave")) {
            playerCommands.add("/ch leave <channel> - Leave channel");
        }
        if (hasPermission(viewerId, "werchat.who")) {
            playerCommands.add("/ch who <channel> - Show online members");
        }
        if (hasPermission(viewerId, "werchat.info")) {
            playerCommands.add("/ch info <channel> - Show channel details");
        }
        if (hasPermission(viewerId, "werchat.playernick")) {
            playerCommands.add("/ch playernick <name> [#color] [#gradient] - Set nickname");
            playerCommands.add("/ch playernick reset - Clear nickname");
        }
        if (hasPermission(viewerId, "werchat.msgcolor")) {
            playerCommands.add("/ch msgcolor <#color> [#gradient] - Set message color");
            playerCommands.add("/ch msgcolor reset - Clear message color");
        }
        if (plugin.getConfig().isAllowPrivateMessages() && hasPermission(viewerId, "werchat.msg")) {
            playerCommands.add("/msg <player> <message> - Private message");
            playerCommands.add("/r <message> - Reply to last PM");
        }
        if (hasPermission(viewerId, "werchat.ignore")) {
            playerCommands.add("/ignore <player> - Ignore or unignore player");
            playerCommands.add("/ignorelist - Show ignored players");
        }

        boolean canModerateAnyChannel = !getModeratorChannels(viewerId).isEmpty();
        List<String> managementCommands = new ArrayList<>();
        if (hasPermission(viewerId, "werchat.create")) {
            managementCommands.add("/ch create <name> - Create channel");
        }
        if (hasPermission(viewerId, "werchat.remove")) {
            managementCommands.add("/ch remove <channel> - Delete channel");
        }
        if (hasPermission(viewerId, "werchat.rename") || canModerateAnyChannel) {
            managementCommands.add("/ch rename <channel> <newname> - Rename channel");
        }
        if (hasPermission(viewerId, "werchat.color") || canModerateAnyChannel) {
            managementCommands.add("/ch color <channel> <#tag> [#text] - Set channel colors");
        }
        if (hasPermission(viewerId, "werchat.nick") || canModerateAnyChannel) {
            managementCommands.add("/ch nick <channel> <nick> - Set channel nick");
        }
        if (hasPermission(viewerId, "werchat.password") || canModerateAnyChannel) {
            managementCommands.add("/ch password <channel> [password] - Set or clear password");
        }
        if (hasPermission(viewerId, "werchat.description") || canModerateAnyChannel) {
            managementCommands.add("/ch description <channel> <text|on|off|clear> - Description");
        }
        if (hasPermission(viewerId, "werchat.motd") || canModerateAnyChannel) {
            managementCommands.add("/ch motd <channel> <text|on|off|clear> - MOTD");
        }
        if (hasPermission(viewerId, "werchat.distance") || canModerateAnyChannel) {
            managementCommands.add("/ch distance <channel> <blocks> - Set range (0 global)");
        }
        if (hasPermission(viewerId, "werchat.world") || canModerateAnyChannel) {
            managementCommands.add("/ch world <channel> add|remove <world> - World restriction");
            managementCommands.add("/ch world <channel> none - Clear world restrictions");
        }
        if (hasPermission(viewerId, "werchat.mod") || canModerateAnyChannel) {
            managementCommands.add("/ch mod <channel> <player> - Add moderator");
            managementCommands.add("/ch unmod <channel> <player> - Remove moderator");
        }
        if (hasPermission(viewerId, "werchat.ban") || canModerateAnyChannel) {
            managementCommands.add("/ch ban <channel> <player> - Ban player");
            managementCommands.add("/ch unban <channel> <player> - Unban player");
        }
        if (hasPermission(viewerId, "werchat.mute") || canModerateAnyChannel) {
            managementCommands.add("/ch mute <channel> <player> - Mute player");
            managementCommands.add("/ch unmute <channel> <player> - Unmute player");
        }
        if (hasPermission(viewerId, "werchat.playernick.others")) {
            managementCommands.add("/ch playernick <player> <name> [#color] [#gradient] - Set player nickname");
        }
        if (hasPermission(viewerId, "werchat.msgcolor.others")) {
            managementCommands.add("/ch msgcolor <player> <#color> [#gradient] - Set player message color");
        }
        if (hasPermission(viewerId, "werchat.reload")) {
            managementCommands.add("/ch reload - Reload config and channels");
        }

        List<HelpCommandLine> lines = new ArrayList<>();
        lines.add(new HelpCommandLine("Player Commands", "#8ea5c0"));
        for (String command : playerCommands) {
            lines.add(new HelpCommandLine(command, "#d8e3f0"));
        }
        if (!managementCommands.isEmpty()) {
            lines.add(new HelpCommandLine("", "#d8e3f0"));
            lines.add(new HelpCommandLine("Management Commands", "#8ea5c0"));
            for (String command : managementCommands) {
                lines.add(new HelpCommandLine(command, "#d8e3f0"));
            }
        }
        return lines;
    }

    private List<Channel> getFocusableChannels(UUID viewerId) {
        List<Channel> channels = new ArrayList<>();
        for (Channel channel : channelManager.getAllChannels()) {
            if (!channel.isMember(viewerId)) {
                continue;
            }
            if (channel.isBanned(viewerId)) {
                continue;
            }

            if (plugin.getConfig().isEnforceChannelPermissions()) {
                if (!hasPermission(viewerId, channel.getReadPermission())) {
                    continue;
                }
                channels.add(channel);
            } else {
                channels.add(channel);
            }
        }
        channels.sort(Comparator.comparing(channel -> channel.getName().toLowerCase(Locale.ROOT)));
        return channels;
    }

    private boolean containsDropdownValue(List<String> entries, String value) {
        if (value == null) {
            return false;
        }
        for (String entry : entries) {
            if (entry != null && value.equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }

    private String defaultColor(String value) {
        String normalized = normalizeHexColor(value);
        return normalized == null ? "#FFFFFF" : normalized;
    }

    private String formatColorValue(String start, String end) {
        String startHex = normalizeHexColor(start);
        String endHex = normalizeHexColor(end);
        if (startHex == null) {
            return "Default";
        }
        if (endHex != null) {
            return startHex + " -> " + endHex;
        }
        return startHex;
    }

    private String resolveMessagePreviewColor(String msgColor, String focusedChannelName) {
        String explicit = normalizeHexColor(msgColor);
        if (explicit != null) {
            return explicit;
        }

        if (focusedChannelName != null && !focusedChannelName.isBlank()) {
            Channel focusedChannel = channelManager.getChannel(focusedChannelName);
            if (focusedChannel != null) {
                String channelColor = normalizeHexColor(focusedChannel.getEffectiveMessageColorHex());
                if (channelColor != null) {
                    return channelColor;
                }
            }
        }
        return "#FFFFFF";
    }

    private String buildMessagePreviewText(String msgColor, String msgGradient) {
        String startHex = normalizeHexColor(msgColor);
        String endHex = normalizeHexColor(msgGradient);
        if (startHex != null && endHex != null) {
            return "Sample message (" + startHex + " -> " + endHex + ")";
        }
        if (startHex != null) {
            return "Sample message";
        }
        return "Sample message (channel default)";
    }

    private String shortenText(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        if (maxLen <= 3) {
            return trimmed.substring(0, maxLen);
        }
        return trimmed.substring(0, maxLen - 3) + "...";
    }

    private String buildChannelDescriptionLine(UUID viewerId, Channel channel) {
        if (!plugin.getConfig().isChannelDescriptionsEnabled()) {
            return "Description: Disabled in config";
        }
        if (!channel.isDescriptionEnabled()) {
            return "Description: Off";
        }
        if (!channel.hasDescription()) {
            return "Description: Not set";
        }
        return "Description: " + shortenText(applyPapi(viewerId, channel.getDescription()), 80);
    }

    private String buildChannelMotdLine(UUID viewerId, Channel channel, boolean member) {
        if (!plugin.getConfig().isChannelMotdSystemEnabled()) {
            return "MOTD: Disabled in config";
        }
        if (!channel.isMotdEnabled()) {
            return "MOTD: Off";
        }
        if (!channel.hasMotd()) {
            return "MOTD: Not set";
        }
        if (!member) {
            return "MOTD: Set (join to view)";
        }
        return "MOTD: " + shortenText(applyPapi(viewerId, channel.getMotd()), 80);
    }

    private String buildModeratorDescriptionValue(UUID viewerId, Channel channel) {
        String prefix = plugin.getConfig().isChannelDescriptionsEnabled()
            ? "Description"
            : "Description (global off)";
        if (!channel.isDescriptionEnabled()) {
            return prefix + ": Off";
        }
        if (!channel.hasDescription()) {
            return prefix + ": Not set";
        }
        return prefix + ": " + shortenText(applyPapi(viewerId, channel.getDescription()), 100);
    }

    private String buildModeratorMotdValue(UUID viewerId, Channel channel) {
        String prefix = plugin.getConfig().isChannelMotdSystemEnabled()
            ? "MOTD"
            : "MOTD (global off)";
        if (!channel.isMotdEnabled()) {
            return prefix + ": Off";
        }
        if (!channel.hasMotd()) {
            return prefix + ": Not set";
        }
        return prefix + ": " + shortenText(applyPapi(viewerId, channel.getMotd()), 100);
    }

    private String buildChannelStrip(List<String> channels, String focused) {
        if (channels == null || channels.isEmpty()) {
            return "No joined channels yet.";
        }

        List<String> ordered = new ArrayList<>(channels);
        ordered.sort((left, right) -> {
            boolean leftFocused = focused != null && left.equalsIgnoreCase(focused);
            boolean rightFocused = focused != null && right.equalsIgnoreCase(focused);
            if (leftFocused && !rightFocused) {
                return -1;
            }
            if (!leftFocused && rightFocused) {
                return 1;
            }
            return left.compareToIgnoreCase(right);
        });

        int limit = Math.min(4, ordered.size());
        List<String> preview = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            String name = ordered.get(i);
            if (focused != null && name.equalsIgnoreCase(focused)) {
                preview.add("[" + name + "]");
            } else {
                preview.add(name);
            }
        }

        String strip = String.join(" | ", preview);
        if (ordered.size() > limit) {
            strip = strip + " | +" + (ordered.size() - limit);
        }
        return strip;
    }

    private void renderSelectedOnlineMembers(UICommandBuilder cmd, Channel selectedChannel) {
        if (selectedChannel == null) {
            cmd.set("#MainSelectedMembersSummary.Text", "No channel selected.");
            hideSelectedMemberRows(cmd);
            return;
        }

        List<OnlineMemberEntry> rankedOnlineMembers = getRankedOnlineMembers(selectedChannel);
        cmd.set("#MainSelectedMembersSummary.Text", rankedOnlineMembers.size() + " online");

        int previewLimit = Math.min(5, rankedOnlineMembers.size());
        for (int i = 0; i < 5; i++) {
            String selector = "#MainSelectedMember" + (i + 1);
            if (i < previewLimit) {
                OnlineMemberEntry entry = rankedOnlineMembers.get(i);
                cmd.set(selector + ".Text", entry.name());
                cmd.set(selector + ".Style.TextColor", entry.color());
                cmd.set(selector + ".Visible", true);
            } else {
                cmd.set(selector + ".Visible", false);
            }
        }

        if (rankedOnlineMembers.size() > previewLimit) {
            cmd.set("#MainSelectedMembersMore.Text", "+" + (rankedOnlineMembers.size() - previewLimit) + " more online");
            cmd.set("#MainSelectedMembersMore.Visible", true);
        } else {
            cmd.set("#MainSelectedMembersMore.Visible", false);
        }
    }

    private void hideSelectedMemberRows(UICommandBuilder cmd) {
        for (int i = 1; i <= 5; i++) {
            cmd.set("#MainSelectedMember" + i + ".Visible", false);
        }
        cmd.set("#MainSelectedMembersMore.Visible", false);
    }

    private List<OnlineMemberEntry> getRankedOnlineMembers(Channel channel) {
        List<OnlineMemberEntry> members = new ArrayList<>();
        for (UUID memberId : channel.getMembers()) {
            PlayerRef online = playerDataManager.getOnlinePlayer(memberId);
            if (online == null) {
                continue;
            }
            members.add(new OnlineMemberEntry(
                online.getUsername(),
                getChannelRankColor(channel, memberId),
                getChannelRankPriority(channel, memberId)
            ));
        }

        members.sort((left, right) -> {
            if (left.priority() != right.priority()) {
                return Integer.compare(left.priority(), right.priority());
            }
            return left.name().compareToIgnoreCase(right.name());
        });
        return members;
    }

    private int getChannelRankPriority(Channel channel, UUID playerId) {
        if (channel.getOwner() != null && channel.getOwner().equals(playerId)) {
            return 0;
        }
        if (channel.isModerator(playerId)) {
            return 1;
        }
        return 2;
    }

    private String getChannelRankColor(Channel channel, UUID playerId) {
        int priority = getChannelRankPriority(channel, playerId);
        if (priority == 0) {
            return CHANNEL_OWNER_COLOR;
        }
        if (priority == 1) {
            return CHANNEL_MODERATOR_COLOR;
        }
        return CHANNEL_MEMBER_COLOR;
    }

    private record OnlineMemberEntry(String name, String color, int priority) { }
    private record HelpCommandLine(String text, String colorHex) { }

    private void renderDisplayNamePreview(UICommandBuilder cmd, String displayName, String nickColor, String nickGradient) {
        String name = (displayName == null || displayName.isBlank()) ? "-" : displayName;
        String startColor = defaultColor(nickColor);
        String endColor = normalizeHexColor(nickGradient);

        if (endColor == null || endColor.equalsIgnoreCase(startColor) || name.length() < 2) {
            cmd.set("#MainDisplayNameStartValue.Text", name);
            cmd.set("#MainDisplayNameStartValue.Style.TextColor", startColor);
            cmd.set("#MainDisplayNameEndValue.Text", "");
            return;
        }

        int split = Math.max(1, name.length() / 2);
        cmd.set("#MainDisplayNameStartValue.Text", name.substring(0, split));
        cmd.set("#MainDisplayNameStartValue.Style.TextColor", startColor);
        cmd.set("#MainDisplayNameEndValue.Text", name.substring(split));
        cmd.set("#MainDisplayNameEndValue.Style.TextColor", endColor);
    }

    private void renderChannels(UICommandBuilder cmd, UIEventBuilder events, UUID viewerId) {
        cmd.clear("#ChannelRows");
        String focused = playerDataManager.getFocusedChannel(viewerId);

        int index = 0;
        for (Channel channel : channelManager.getAllChannels()) {
            if (plugin.getConfig().isEnforceChannelPermissions()
                && !hasPermission(viewerId, channel.getReadPermission())
                && !channel.isMember(viewerId)) {
                continue;
            }

            cmd.append("#ChannelRows", "Werchat/ChannelRow.ui");
            String base = "#ChannelRows[" + index + "]";

            cmd.set(base + " #ChannelTag.Text", "[" + applyPapi(viewerId, channel.getNick()) + "]");
            cmd.set(base + " #ChannelTag.Style.TextColor", channel.getColorHex());
            cmd.set(base + " #ChannelName.Text", channel.getName());
            cmd.set(base + " #ChannelName.Style.TextColor", channel.getColorHex());

            String meta = buildChannelMeta(channel, viewerId, focused);
            cmd.set(base + " #ChannelMeta.Text", meta);
            cmd.set(base + " #ChannelOwner.Text", buildChannelOwnerLine(channel));

            boolean member = channel.isMember(viewerId);
            boolean focusedChannel = focused != null && focused.equalsIgnoreCase(channel.getName());
            cmd.set(base + " #ChannelDescription.Visible", true);
            cmd.set(base + " #ChannelDescription.Text", buildChannelDescriptionLine(viewerId, channel));
            cmd.set(base + " #ChannelMotd.Visible", true);
            cmd.set(base + " #ChannelMotd.Text", buildChannelMotdLine(viewerId, channel, member));

            boolean canJoin = !member
                && !channel.isBanned(viewerId)
                && (!plugin.getConfig().isEnforceChannelPermissions() || hasPermission(viewerId, channel.getJoinPermission()));
            boolean canLeave = member && !channel.isDefault();
            boolean canFocus = member && !focusedChannel;

            cmd.set(base + " #JoinButton.Visible", canJoin);
            cmd.set(base + " #LeaveButton.Visible", canLeave);
            cmd.set(base + " #FocusButton.Visible", canFocus);
            cmd.set(base + " #FocusedChip.Visible", focusedChannel);

            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                base + " #JoinButton",
                EventData.of("Button", "Join").append("Channel", channel.getName()),
                false
            );
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                base + " #LeaveButton",
                EventData.of("Button", "Leave").append("Channel", channel.getName()),
                false
            );
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                base + " #FocusButton",
                EventData.of("Button", "Focus").append("Channel", channel.getName()),
                false
            );
            index++;
        }

        cmd.set("#ChannelsEmptyLabel.Visible", index == 0);
    }

    private void renderJoinPasswordModal(UICommandBuilder cmd) {
        boolean visible = TAB_CHANNELS.equals(activeTab)
            && showJoinPasswordModal
            && pendingJoinPasswordChannel != null
            && !pendingJoinPasswordChannel.isBlank();

        if (!visible) {
            cmd.set("#JoinPasswordModalBackdrop.Visible", false);
            return;
        }

        Channel pending = channelManager.findChannel(pendingJoinPasswordChannel);
        if (pending == null || !pending.hasPassword()) {
            closeJoinPasswordModal();
            cmd.set("#JoinPasswordModalBackdrop.Visible", false);
            return;
        }

        cmd.set("#JoinPasswordModalBackdrop.Visible", true);
        cmd.set("#JoinPasswordModalTitle.Text", "Locked: " + pending.getName());
        cmd.set("#JoinPasswordModalSubtitle.Text", "Enter the password to join " + pending.getName() + ".");
        cmd.set("#JoinPasswordModalInput.Value", "");
    }

    private void renderModerationListModal(UICommandBuilder cmd, UIEventBuilder events, UUID viewerId) {
        boolean visible = activeTab.startsWith(TAB_MOD_PREFIX)
            && showModerationListModal
            && moderationListMode != null
            && !moderationListMode.isBlank();

        if (!visible) {
            cmd.set("#ModerationListModalBackdrop.Visible", false);
            return;
        }

        Channel channel = getActiveModeratorChannel(viewerId);
        if (channel == null) {
            closeModerationListModal();
            cmd.set("#ModerationListModalBackdrop.Visible", false);
            return;
        }

        String title;
        String subtitle;
        String actionLabel;
        String actionKey;
        List<UUID> entries = new ArrayList<>();

        switch (moderationListMode) {
            case MOD_LIST_BANS -> {
                title = "Banned Players";
                subtitle = "Unban players from " + channel.getName() + ".";
                actionLabel = "Unban";
                actionKey = "unban_uuid";
                entries.addAll(channel.getBanned());
            }
            case MOD_LIST_MUTES -> {
                title = "Muted Players";
                subtitle = "Unmute players in " + channel.getName() + ".";
                actionLabel = "Unmute";
                actionKey = "unmute_uuid";
                entries.addAll(channel.getMuted());
            }
            case MOD_LIST_MODERATORS -> {
                title = "Channel Moderators";
                subtitle = "Remove moderators in " + channel.getName() + ".";
                actionLabel = "Remove";
                actionKey = "removemod_uuid";
                entries.addAll(channel.getModerators());
            }
            default -> {
                closeModerationListModal();
                cmd.set("#ModerationListModalBackdrop.Visible", false);
                return;
            }
        }

        entries.sort((left, right) -> {
            int leftPriority = getChannelRankPriority(channel, left);
            int rightPriority = getChannelRankPriority(channel, right);
            if (leftPriority != rightPriority) {
                return Integer.compare(leftPriority, rightPriority);
            }
            return resolvePlayerName(left).compareToIgnoreCase(resolvePlayerName(right));
        });

        cmd.set("#ModerationListModalBackdrop.Visible", true);
        cmd.set("#ModerationListModalTitle.Text", title);
        cmd.set("#ModerationListModalSubtitle.Text", subtitle);

        cmd.clear("#ModerationListModalRows");
        cmd.set("#ModerationListModalEmptyLabel.Visible", entries.isEmpty());
        if (entries.isEmpty()) {
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            UUID targetId = entries.get(i);
            String base = "#ModerationListModalRows[" + i + "]";

            cmd.append("#ModerationListModalRows", "Werchat/ModerationListRow.ui");
            cmd.set(base + " #ModerationListEntryName.Text", resolvePlayerName(targetId));
            cmd.set(base + " #ModerationListEntryName.Style.TextColor", getChannelRankColor(channel, targetId));

            boolean ownerLocked = MOD_LIST_MODERATORS.equals(moderationListMode)
                && channel.getOwner() != null
                && channel.getOwner().equals(targetId);
            if (ownerLocked) {
                cmd.set(base + " #ModerationListEntryActionButton.Text", "Owner");
                cmd.set(base + " #ModerationListEntryActionButton.Disabled", true);
                continue;
            }

            cmd.set(base + " #ModerationListEntryActionButton.Text", actionLabel);
            cmd.set(base + " #ModerationListEntryActionButton.Disabled", false);
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                base + " #ModerationListEntryActionButton",
                EventData.of("Button", "ModAction")
                    .append("Action", actionKey)
                    .append("Value", targetId.toString()),
                false
            );
        }
    }

    private void renderStatusBanner(UICommandBuilder cmd) {
        String message = statusMessage == null ? "" : statusMessage.trim();
        boolean visible = !message.isEmpty() && isErrorStatusMessage(message);
        cmd.set("#StatusBanner.Visible", visible);
        if (!visible) {
            return;
        }

        cmd.set("#StatusBannerMessage.Text", message);
        cmd.set("#StatusBannerMessage.Style.TextColor", "#FF5555");
    }

    private void renderModeratorActionFeedback(UICommandBuilder cmd) {
        String errorMessage = moderatorActionError == null ? "" : moderatorActionError.trim();
        boolean errorVisible = !errorMessage.isEmpty();
        cmd.set("#ModActionErrorMessage.Visible", errorVisible);
        cmd.set("#ModActionErrorMessage.Text", errorVisible ? errorMessage : "");

        String successMessage = moderatorActionSuccess == null ? "" : moderatorActionSuccess.trim();
        boolean successVisible = !successMessage.isEmpty();
        cmd.set("#ModActionSuccessMessage.Visible", successVisible);
        cmd.set("#ModActionSuccessMessage.Text", successVisible ? successMessage : "");
    }

    private void syncModeratorActionFeedbackFromStatus() {
        String message = statusMessage == null ? "" : statusMessage.trim();
        if (message.isEmpty()) {
            moderatorActionError = "";
            moderatorActionSuccess = "";
            return;
        }

        if (isErrorStatusMessage(message)) {
            moderatorActionError = message;
            moderatorActionSuccess = "";
            return;
        }

        moderatorActionError = "";
        moderatorActionSuccess = message;
    }

    private boolean isErrorStatusMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String lower = message.toLowerCase(Locale.ROOT);
        return containsAny(lower,
            "error", "failed", "unknown", "invalid", "cannot", "can't", "wrong", "not found",
            "required", "unavailable", "don't have permission", "no join permission", "no read permission",
            "cannot be", "must be", "banned from", "not in", "not a moderator", "empty", "please wait");
    }

    private boolean enforceToggleCooldown() {
        long now = System.currentTimeMillis();
        if (now < nextToggleActionAllowedAtMs) {
            long remainingMs = nextToggleActionAllowedAtMs - now;
            double remaining = Math.max(0.1D, remainingMs / 1000.0D);
            statusMessage = String.format(Locale.ROOT, "Please wait %.1fs before toggling again.", remaining);
            return false;
        }

        nextToggleActionAllowedAtMs = now + TOGGLE_ACTION_COOLDOWN_MS;
        return true;
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void renderModeratorPanel(UICommandBuilder cmd, UUID viewerId) {
        if (!activeTab.startsWith(TAB_MOD_PREFIX)) {
            return;
        }

        String channelName = activeTab.substring(TAB_MOD_PREFIX.length());
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null || !isChannelModerator(viewerId, channel)) {
            statusMessage = "Moderator tab unavailable.";
            activeTab = TAB_CHANNELS;
            return;
        }

        cmd.set("#ModeratorChannelNameValue.Text", channel.getName());
        cmd.set("#ModeratorMemberCountValue.Text", "Members: " + channel.getMemberCount());
        cmd.set("#ModeratorMutedCountValue.Text", "Muted: " + channel.getMuted().size());
        cmd.set("#ModeratorBannedCountValue.Text", "Banned: " + channel.getBanned().size());
        cmd.set("#ModeratorModeratorsCountValue.Text", "Moderators: " + channel.getModerators().size());
        cmd.set("#ModeratorDistanceValue.Text", channel.isGlobal() ? "Distance: Global" : "Distance: " + channel.getDistance() + " blocks");
        cmd.set("#ModeratorPasswordValue.Text", "Password: " + (channel.hasPassword() ? "Enabled" : "None"));
        cmd.set("#ModeratorTagColorValue.Text", "Tag Color: " + channel.getColorHex());
        cmd.set("#ModeratorTextColorValue.Text", "Text Color: " + (channel.hasMessageColor() ? channel.getMessageColorHex() : "Default"));
        cmd.set("#ModeratorDescriptionValue.Text", buildModeratorDescriptionValue(viewerId, channel));
        cmd.set("#ModeratorMotdValue.Text", buildModeratorMotdValue(viewerId, channel));

        cmd.set("#ModNickInput.Value", channel.getNick());
        cmd.set("#ModDescriptionInput.Value", channel.hasDescription() ? channel.getDescription() : "");
        cmd.set("#ModDescriptionEnabledCheck.Value", channel.isDescriptionEnabled());
        cmd.set("#ModMotdInput.Value", channel.hasMotd() ? channel.getMotd() : "");
        cmd.set("#ModMotdEnabledCheck.Value", channel.isMotdEnabled());
        cmd.set("#ModDistanceDropdown.Value", toDistancePresetValue(channel.getDistance()));
        cmd.set("#ModDistanceInput.Value", String.valueOf(Math.max(0, channel.getDistance())));
        cmd.set("#ModTagColorPicker.Color", channel.getColorHex());
        cmd.set("#ModTextColorPicker.Color", defaultColor(channel.getMessageColorHex()));
        renderModeratorActionFeedback(cmd);

    }

    private String buildChannelMeta(Channel channel, UUID viewerId, String focused) {
        StringBuilder meta = new StringBuilder();
        meta.append("Members: ").append(channel.getMemberCount());
        if (channel.hasPassword()) {
            meta.append(" | Password");
        }
        if (channel.isWorldRestricted()) {
            meta.append(" | Worlds: ").append(channel.getWorldsDisplay());
        }
        if (channel.isGlobal()) {
            meta.append(" | Global");
        } else {
            meta.append(" | ").append(channel.getDistance()).append(" blocks");
        }
        if (focused != null && focused.equalsIgnoreCase(channel.getName())) {
            meta.append(" | Selected");
        }
        if (channel.isModerator(viewerId)) {
            meta.append(" | Mod");
        }
        return meta.toString();
    }

    private String buildChannelOwnerLine(Channel channel) {
        UUID ownerId = channel.getOwner();
        if (ownerId == null) {
            return "Owner: None";
        }
        String ownerName = playerDataManager.getKnownName(ownerId);
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = ownerId.toString().substring(0, 8);
        }
        return "Owner: " + ownerName;
    }

    private void handleFocusAction(String channelName) {
        Channel channel = channelManager.findChannel(channelName);
        UUID viewerId = playerRef.getUuid();
        if (channel == null) {
            statusMessage = "Channel not found.";
            return;
        }
        if (plugin.getConfig().isEnforceChannelPermissions() && !hasPermission(viewerId, channel.getReadPermission())) {
            statusMessage = "No read permission for " + channel.getName() + ".";
            return;
        }
        if (!channel.isMember(viewerId)) {
            if (!attemptJoin(viewerId, channel, null)) {
                return;
            }
        }

        playerDataManager.setFocusedChannel(viewerId, channel.getName());
        channelManager.sendChannelMotd(viewerId, channel);
        statusMessage = "Now chatting in " + channel.getName() + ".";
    }

    private boolean handleJoinAction(String channelName, String password) {
        Channel channel = channelManager.findChannel(channelName);
        UUID viewerId = playerRef.getUuid();
        if (channel == null) {
            statusMessage = "Channel not found.";
            return false;
        }
        if (channel.isMember(viewerId)) {
            statusMessage = "Already in " + channel.getName() + ".";
            closeJoinPasswordModal();
            return false;
        }
        if (channel.hasPassword() && (password == null || password.isBlank())) {
            pendingJoinPasswordChannel = channel.getName();
            showJoinPasswordModal = true;
            statusMessage = "Password required for " + channel.getName() + ".";
            return false;
        }

        if (attemptJoin(viewerId, channel, password)) {
            playerDataManager.setFocusedChannel(viewerId, channel.getName());
            channelManager.sendChannelMotd(viewerId, channel);
            closeJoinPasswordModal();
            statusMessage = "Joined and focused " + channel.getName() + ".";
            return true;
        }

        if (channel.hasPassword()) {
            pendingJoinPasswordChannel = channel.getName();
            showJoinPasswordModal = true;
        }
        return false;
    }

    private void handleJoinPasswordConfirm(String password) {
        if (pendingJoinPasswordChannel == null || pendingJoinPasswordChannel.isBlank()) {
            closeJoinPasswordModal();
            statusMessage = "No locked channel selected.";
            return;
        }
        if (password == null || password.isBlank()) {
            statusMessage = "Enter a password first.";
            return;
        }
        handleJoinAction(pendingJoinPasswordChannel, password);
    }

    private void closeJoinPasswordModal() {
        showJoinPasswordModal = false;
        pendingJoinPasswordChannel = null;
    }

    private void openModerationListModal(String mode) {
        if (mode == null || mode.isBlank()) {
            statusMessage = "Unknown moderation list.";
            return;
        }

        if (!mode.equals(MOD_LIST_BANS) && !mode.equals(MOD_LIST_MUTES) && !mode.equals(MOD_LIST_MODERATORS)) {
            statusMessage = "Unknown moderation list.";
            return;
        }

        UUID viewerId = playerRef.getUuid();
        Channel channel = getActiveModeratorChannel(viewerId);
        if (channel == null) {
            statusMessage = "No moderator channel selected.";
            return;
        }
        if (!isChannelModerator(viewerId, channel)) {
            statusMessage = "You are not a moderator of " + channel.getName() + ".";
            return;
        }

        moderationListMode = mode;
        showModerationListModal = true;
        if (mode.equals(MOD_LIST_BANS)) {
            statusMessage = "Viewing banned players.";
        } else if (mode.equals(MOD_LIST_MUTES)) {
            statusMessage = "Viewing muted players.";
        } else {
            statusMessage = "Viewing channel moderators.";
        }
    }

    private void closeModerationListModal() {
        showModerationListModal = false;
        moderationListMode = null;
    }

    private String resolvePlayerName(UUID playerId) {
        PlayerRef online = playerDataManager.getOnlinePlayer(playerId);
        if (online != null) {
            return online.getUsername();
        }

        String known = playerDataManager.getKnownName(playerId);
        if (known != null && !known.isBlank()) {
            return known;
        }
        return playerId.toString().substring(0, 8);
    }

    private boolean attemptJoin(UUID viewerId, Channel channel, String password) {
        if (channel.isBanned(viewerId)) {
            statusMessage = "You are banned from " + channel.getName() + ".";
            return false;
        }
        if (plugin.getConfig().isEnforceChannelPermissions()) {
            if (!hasPermission(viewerId, channel.getJoinPermission())) {
                statusMessage = "No join permission for " + channel.getName() + ".";
                return false;
            }
            if (!hasPermission(viewerId, channel.getReadPermission())) {
                statusMessage = "No read permission for " + channel.getName() + ".";
                return false;
            }
        }
        if (channel.hasPassword() && !channel.checkPassword(password)) {
            statusMessage = "Wrong password for " + channel.getName() + ".";
            return false;
        }

        channel.addMember(viewerId);
        return true;
    }

    private void handleLeaveAction(String channelName) {
        Channel channel = channelManager.findChannel(channelName);
        UUID viewerId = playerRef.getUuid();
        if (channel == null) {
            statusMessage = "Channel not found.";
            return;
        }
        if (channel.isDefault()) {
            statusMessage = "Default channel cannot be left.";
            return;
        }
        if (!channel.isMember(viewerId)) {
            statusMessage = "You are not in " + channel.getName() + ".";
            return;
        }

        channel.removeMember(viewerId);

        String focused = playerDataManager.getFocusedChannel(viewerId);
        if (focused != null && focused.equalsIgnoreCase(channel.getName())) {
            Channel fallback = channelManager.getDefaultChannel();
            if (fallback != null && !fallback.isBanned(viewerId)) {
                fallback.addMember(viewerId);
                playerDataManager.setFocusedChannel(viewerId, fallback.getName());
                statusMessage = "Left " + channel.getName() + ". Switched to " + fallback.getName() + ".";
            } else {
                statusMessage = "Left " + channel.getName() + ".";
            }
        } else {
            statusMessage = "Left " + channel.getName() + ".";
        }
    }

    private void handleProfileAction(String action, String value, String extra) {
        UUID viewerId = playerRef.getUuid();
        if (action == null || action.isBlank()) {
            statusMessage = "Unknown profile action.";
            return;
        }

        switch (action) {
            case "preview_selected" -> {
                selectedMainChannel = (value == null || value.isBlank()) ? null : value;
                statusMessage = selectedMainChannel == null ? "Select a channel." : "Selected " + selectedMainChannel + ".";
            }
            case "set_focus" -> {
                if (!hasPermission(viewerId, "werchat.switch")) {
                    statusMessage = "You don't have permission to switch channels.";
                    return;
                }
                if (value == null || value.isBlank()) {
                    statusMessage = "Select a channel first.";
                    return;
                }
                Channel channel = channelManager.findChannel(value);
                if (channel == null || !channel.isMember(viewerId)) {
                    statusMessage = "You are not in that channel.";
                    return;
                }
                selectedMainChannel = channel.getName();
                handleFocusAction(channel.getName());
            }
            case "set_nickname" -> {
                if (!hasPermission(viewerId, "werchat.playernick")) {
                    statusMessage = "You don't have permission to set nicknames.";
                    return;
                }
                String nickname = value == null ? "" : value.trim();
                if (nickname.isEmpty()) {
                    statusMessage = "Nickname cannot be empty.";
                    return;
                }
                if (nickname.length() > MAX_NICKNAME_LENGTH) {
                    statusMessage = "Nickname too long (max " + MAX_NICKNAME_LENGTH + ").";
                    return;
                }
                for (PlayerRef online : playerDataManager.getOnlinePlayers()) {
                    if (online.getUuid().equals(viewerId)) {
                        continue;
                    }
                    if (online.getUsername().equalsIgnoreCase(nickname)) {
                        statusMessage = "You cannot use another player's username.";
                        return;
                    }
                }
                playerDataManager.setNickname(viewerId, nickname);
                statusMessage = "Nickname updated.";
            }
            case "clear_nickname" -> {
                if (!hasPermission(viewerId, "werchat.playernick")) {
                    statusMessage = "You don't have permission to clear nicknames.";
                    return;
                }
                playerDataManager.clearNickname(viewerId);
                statusMessage = "Nickname and nickname colors cleared.";
            }
            case "set_nick_solid" -> {
                if (!hasPermission(viewerId, "werchat.nickcolor")) {
                    statusMessage = "You don't have permission to set nickname colors.";
                    return;
                }
                String start = normalizeHexColor(value);
                if (start == null) {
                    statusMessage = "Invalid nickname color.";
                    return;
                }
                playerDataManager.setNickColor(viewerId, start);
                playerDataManager.setNickGradientEnd(viewerId, null);
                statusMessage = "Nickname color updated.";
            }
            case "set_nick_gradient" -> {
                if (!hasPermission(viewerId, "werchat.nickcolor")) {
                    statusMessage = "You don't have permission to set nickname colors.";
                    return;
                }
                String start = normalizeHexColor(value);
                String end = normalizeHexColor(extra);
                if (start == null || end == null) {
                    statusMessage = "Invalid nickname gradient colors.";
                    return;
                }
                playerDataManager.setNickColor(viewerId, start);
                playerDataManager.setNickGradientEnd(viewerId, end);
                statusMessage = "Nickname gradient updated.";
            }
            case "clear_nick_color" -> {
                if (!hasPermission(viewerId, "werchat.nickcolor")) {
                    statusMessage = "You don't have permission to clear nickname colors.";
                    return;
                }
                playerDataManager.setNickColor(viewerId, null);
                playerDataManager.setNickGradientEnd(viewerId, null);
                statusMessage = "Nickname colors cleared.";
            }
            case "set_msg_solid" -> {
                if (!hasPermission(viewerId, "werchat.msgcolor")) {
                    statusMessage = "You don't have permission to set message colors.";
                    return;
                }
                String start = normalizeHexColor(value);
                if (start == null) {
                    statusMessage = "Invalid message color.";
                    return;
                }
                playerDataManager.setMsgColor(viewerId, start);
                playerDataManager.setMsgGradientEnd(viewerId, null);
                statusMessage = "Message color updated.";
            }
            case "set_msg_gradient" -> {
                if (!hasPermission(viewerId, "werchat.msgcolor")) {
                    statusMessage = "You don't have permission to set message colors.";
                    return;
                }
                String start = normalizeHexColor(value);
                String end = normalizeHexColor(extra);
                if (start == null || end == null) {
                    statusMessage = "Invalid message gradient colors.";
                    return;
                }
                playerDataManager.setMsgColor(viewerId, start);
                playerDataManager.setMsgGradientEnd(viewerId, end);
                statusMessage = "Message gradient updated.";
            }
            case "clear_msg_color" -> {
                if (!hasPermission(viewerId, "werchat.msgcolor")) {
                    statusMessage = "You don't have permission to clear message colors.";
                    return;
                }
                playerDataManager.clearMsgColor(viewerId);
                statusMessage = "Message colors cleared.";
            }
            default -> statusMessage = "Unsupported profile action.";
        }
    }

    private void handleModeratorAction(String action, String targetName, String value, String password) {
        UUID viewerId = playerRef.getUuid();
        Channel channel = getActiveModeratorChannel(viewerId);
        if (channel == null) {
            statusMessage = "No moderator channel selected.";
            return;
        }
        if (!isChannelModerator(viewerId, channel)) {
            statusMessage = "You are not a moderator of " + channel.getName() + ".";
            return;
        }

        if (action == null || action.isBlank()) {
            statusMessage = "Unknown moderator action.";
            return;
        }

        switch (action) {
            case "ban" -> {
                UUID target = resolveTargetUuid(targetName, channel);
                if (target == null) {
                    statusMessage = "Target not found.";
                    return;
                }
                channel.ban(target);
                statusMessage = "Banned " + playerDataManager.getKnownName(target) + " from " + channel.getName() + ".";
            }
            case "unban" -> {
                UUID target = resolveTargetUuid(targetName, channel);
                if (target == null) {
                    statusMessage = "Target not found.";
                    return;
                }
                channel.unban(target);
                statusMessage = "Unbanned " + playerDataManager.getKnownName(target) + " in " + channel.getName() + ".";
            }
            case "unban_uuid" -> {
                UUID target = parseUuid(value);
                if (target == null || !channel.getBanned().contains(target)) {
                    statusMessage = "Target not found in banned list.";
                    return;
                }
                channel.unban(target);
                statusMessage = "Unbanned " + resolvePlayerName(target) + " in " + channel.getName() + ".";
            }
            case "mute" -> {
                UUID target = resolveTargetUuid(targetName, channel);
                if (target == null) {
                    statusMessage = "Target not found.";
                    return;
                }
                channel.mute(target);
                statusMessage = "Muted " + playerDataManager.getKnownName(target) + " in " + channel.getName() + ".";
            }
            case "unmute" -> {
                UUID target = resolveTargetUuid(targetName, channel);
                if (target == null) {
                    statusMessage = "Target not found.";
                    return;
                }
                channel.unmute(target);
                statusMessage = "Unmuted " + playerDataManager.getKnownName(target) + " in " + channel.getName() + ".";
            }
            case "unmute_uuid" -> {
                UUID target = parseUuid(value);
                if (target == null || !channel.getMuted().contains(target)) {
                    statusMessage = "Target not found in muted list.";
                    return;
                }
                channel.unmute(target);
                statusMessage = "Unmuted " + resolvePlayerName(target) + " in " + channel.getName() + ".";
            }
            case "addmod" -> {
                UUID target = resolveTargetUuid(targetName, channel);
                if (target == null) {
                    statusMessage = "Target not found.";
                    return;
                }
                channel.addModerator(target);
                statusMessage = "Added moderator " + playerDataManager.getKnownName(target) + " in " + channel.getName() + ".";
            }
            case "removemod" -> {
                UUID target = resolveTargetUuid(targetName, channel);
                if (target == null) {
                    statusMessage = "Target not found.";
                    return;
                }
                if (channel.getOwner() != null && channel.getOwner().equals(target)) {
                    statusMessage = "Channel owner cannot be removed as moderator.";
                    return;
                }
                channel.removeModerator(target);
                statusMessage = "Removed moderator " + playerDataManager.getKnownName(target) + " in " + channel.getName() + ".";
            }
            case "removemod_uuid" -> {
                UUID target = parseUuid(value);
                if (target == null || !channel.getModerators().contains(target)) {
                    statusMessage = "Target not found in moderator list.";
                    return;
                }
                if (channel.getOwner() != null && channel.getOwner().equals(target)) {
                    statusMessage = "Channel owner cannot be removed as moderator.";
                    return;
                }
                channel.removeModerator(target);
                statusMessage = "Removed moderator " + resolvePlayerName(target) + " in " + channel.getName() + ".";
            }
            case "set_nick" -> {
                if (value == null || value.isBlank()) {
                    statusMessage = "Channel nick cannot be empty.";
                    return;
                }
                channel.setNick(value.trim());
                statusMessage = "Channel nick updated.";
            }
            case "set_description" -> {
                String input = value == null ? "" : value.trim();
                if (input.isEmpty()) {
                    statusMessage = "Description cannot be empty.";
                    return;
                }
                if (input.length() > MAX_CHANNEL_DESCRIPTION_LENGTH) {
                    statusMessage = "Description too long (max " + MAX_CHANNEL_DESCRIPTION_LENGTH + ").";
                    return;
                }
                channel.setDescription(input);
                channel.setDescriptionEnabled(true);
                statusMessage = "Description updated.";
            }
            case "clear_description" -> {
                channel.setDescription("");
                channel.setDescriptionEnabled(false);
                statusMessage = "Description cleared.";
            }
            case "toggle_description" -> {
                if (!enforceToggleCooldown()) {
                    return;
                }
                boolean enabled = !channel.isDescriptionEnabled();
                channel.setDescriptionEnabled(enabled);
                statusMessage = enabled ? "Description enabled." : "Description disabled.";
            }
            case "set_motd" -> {
                String input = value == null ? "" : value.trim();
                if (input.isEmpty()) {
                    statusMessage = "MOTD cannot be empty.";
                    return;
                }
                if (input.length() > MAX_CHANNEL_MOTD_LENGTH) {
                    statusMessage = "MOTD too long (max " + MAX_CHANNEL_MOTD_LENGTH + ").";
                    return;
                }
                channel.setMotd(input);
                channel.setMotdEnabled(true);
                statusMessage = "MOTD updated.";
            }
            case "clear_motd" -> {
                channel.setMotd("");
                channel.setMotdEnabled(false);
                statusMessage = "MOTD cleared.";
            }
            case "toggle_motd" -> {
                if (!enforceToggleCooldown()) {
                    return;
                }
                boolean enabled = !channel.isMotdEnabled();
                channel.setMotdEnabled(enabled);
                statusMessage = enabled ? "MOTD enabled." : "MOTD disabled.";
            }
            case "set_distance" -> {
                if (value == null || value.isBlank()) {
                    statusMessage = "Distance is required.";
                    return;
                }
                if ("custom".equalsIgnoreCase(value.trim())) {
                    statusMessage = "Select a preset distance or use custom distance input.";
                    return;
                }
                try {
                    int parsed = Integer.parseInt(value.trim());
                    if (parsed < 0) {
                        statusMessage = "Distance must be 0 or greater.";
                        return;
                    }
                    channel.setDistance(parsed);
                    statusMessage = parsed == 0 ? "Distance set to global." : "Distance set to " + parsed + " blocks.";
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid distance.";
                }
            }
            case "set_password" -> {
                if (password == null || password.isBlank()) {
                    statusMessage = "Password is empty.";
                    return;
                }
                channel.setPassword(password);
                statusMessage = "Password updated.";
            }
            case "clear_password" -> {
                channel.setPassword(null);
                statusMessage = "Password cleared.";
            }
            case "set_tag_color" -> {
                Color color = parseHexColor(value);
                if (color == null) {
                    statusMessage = "Invalid tag color. Use #RRGGBB.";
                    return;
                }
                channel.setColor(color);
                statusMessage = "Tag color updated.";
            }
            case "set_text_color" -> {
                if (value == null || value.isBlank()) {
                    channel.setMessageColor(null);
                    statusMessage = "Text color reset to default.";
                    return;
                }
                Color color = parseHexColor(value);
                if (color == null) {
                    statusMessage = "Invalid text color. Use #RRGGBB.";
                    return;
                }
                channel.setMessageColor(color);
                statusMessage = "Text color updated.";
            }
            case "clear_text_color" -> {
                channel.setMessageColor(null);
                statusMessage = "Text color reset to default.";
            }
            default -> statusMessage = "Unsupported moderator action.";
        }
    }

    private Channel getActiveModeratorChannel(UUID viewerId) {
        if (!activeTab.startsWith(TAB_MOD_PREFIX)) {
            return null;
        }
        String channelName = activeTab.substring(TAB_MOD_PREFIX.length());
        Channel channel = channelManager.findChannel(channelName);
        if (channel == null || !isChannelModerator(viewerId, channel)) {
            return null;
        }
        return channel;
    }

    private void validateActiveTab(List<Channel> moderatorChannels) {
        if (!activeTab.startsWith(TAB_MOD_PREFIX)) {
            return;
        }
        String channelName = activeTab.substring(TAB_MOD_PREFIX.length());
        for (Channel channel : moderatorChannels) {
            if (channel.getName().equalsIgnoreCase(channelName)) {
                return;
            }
        }
        activeTab = TAB_CHANNELS;
    }

    private List<Channel> getModeratorChannels(UUID viewerId) {
        List<Channel> channels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Channel channel : channelManager.getAllChannels()) {
            String key = channel.getName().toLowerCase(Locale.ROOT);
            if (isChannelModerator(viewerId, channel) && seen.add(key)) {
                channels.add(channel);
            }
        }
        channels.sort(Comparator.comparing(channel -> channel.getName().toLowerCase(Locale.ROOT)));
        return channels;
    }

    private boolean isChannelModerator(UUID viewerId, Channel channel) {
        return channel.isModerator(viewerId)
            || (channel.getOwner() != null && channel.getOwner().equals(viewerId))
            || hasPermission(viewerId, "werchat.mod")
            || hasPermission(viewerId, "werchat.*")
            || hasPermission(viewerId, "*");
    }

    private boolean hasPermission(UUID playerId, String permission) {
        PermissionsModule perms = PermissionsModule.get();
        return perms.hasPermission(playerId, permission)
            || perms.hasPermission(playerId, "werchat.*")
            || perms.hasPermission(playerId, "*");
    }

    private UUID resolveTargetUuid(String name, Channel channel) {
        if (name == null || name.isBlank()) {
            return null;
        }

        PlayerRef online = playerDataManager.findPlayerByName(name.trim());
        if (online != null) {
            return online.getUuid();
        }

        String normalized = name.trim().toLowerCase(Locale.ROOT);
        Set<UUID> candidates = new HashSet<>();
        candidates.addAll(channel.getMembers());
        candidates.addAll(channel.getModerators());
        candidates.addAll(channel.getBanned());
        candidates.addAll(channel.getMuted());
        if (channel.getOwner() != null) {
            candidates.add(channel.getOwner());
        }

        for (UUID candidate : candidates) {
            String known = playerDataManager.getKnownName(candidate);
            if (known != null && known.toLowerCase(Locale.ROOT).equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Color parseHexColor(String value) {
        if (value == null) {
            return null;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.matches("(?i)^[0-9a-f]{8}$")) {
            // ColorPicker returns 8-digit RGBA (RRGGBBAA). Ignore trailing alpha.
            hex = hex.substring(0, 6);
        }
        if (!hex.matches("(?i)^[0-9a-f]{6}$")) {
            return null;
        }
        try {
            return new Color(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeHexColor(String value) {
        Color parsed = parseHexColor(value);
        if (parsed == null) {
            return null;
        }
        return String.format("#%02X%02X%02X", parsed.getRed(), parsed.getGreen(), parsed.getBlue());
    }

    private String applyPapi(UUID viewerId, String text) {
        if (text == null || text.isEmpty() || papi == null) {
            return text == null ? "" : text;
        }
        PlayerRef viewer = playerDataManager.getOnlinePlayer(viewerId);
        if (viewer == null) {
            return text;
        }
        try {
            String resolved = papi.setPlaceholders(viewer, text);
            return resolved == null ? text : resolved;
        } catch (Throwable ignored) {
            return text;
        }
    }

    private String toDistancePresetValue(int distance) {
        if (DISTANCE_PRESETS.contains(distance)) {
            return String.valueOf(distance);
        }
        return "custom";
    }

    public static class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC = BuilderCodec.builder(PageEventData.class, PageEventData::new)
            .addField(new KeyedCodec<>("Button", Codec.STRING), (data, value) -> data.button = value, data -> data.button)
            .addField(new KeyedCodec<>("Tab", Codec.STRING), (data, value) -> data.tab = value, data -> data.tab)
            .addField(new KeyedCodec<>("Channel", Codec.STRING), (data, value) -> data.channel = value, data -> data.channel)
            .addField(new KeyedCodec<>("@Channel", Codec.STRING), (data, value) -> data.channel = value, data -> data.channel)
            .addField(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action)
            .addField(new KeyedCodec<>("@Target", Codec.STRING), (data, value) -> data.target = value, data -> data.target)
            .addField(new KeyedCodec<>("@Value", Codec.STRING), (data, value) -> data.value = value, data -> data.value)
            .addField(new KeyedCodec<>("@Extra", Codec.STRING), (data, value) -> data.extra = value, data -> data.extra)
            .addField(new KeyedCodec<>("@Password", Codec.STRING), (data, value) -> data.password = value, data -> data.password)
            .addField(new KeyedCodec<>("Target", Codec.STRING), (data, value) -> data.target = value, data -> data.target)
            .addField(new KeyedCodec<>("Value", Codec.STRING), (data, value) -> data.value = value, data -> data.value)
            .addField(new KeyedCodec<>("Extra", Codec.STRING), (data, value) -> data.extra = value, data -> data.extra)
            .addField(new KeyedCodec<>("Password", Codec.STRING), (data, value) -> data.password = value, data -> data.password)
            .build();

        public String button;
        public String tab;
        public String channel;
        public String action;
        public String target;
        public String value;
        public String extra;
        public String password;
    }
}
