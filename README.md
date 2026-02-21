# Werchat

[![Latest Release](https://img.shields.io/github/v/release/HyperSystemsDev/Werchat?label=version)](https://github.com/HyperSystemsDev/Werchat/releases)
[![License](https://img.shields.io/badge/license-GPLv3-green)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA?logo=discord&logoColor=white)](https://discord.gg/SNPjyfkYPc)
[![GitHub Stars](https://img.shields.io/github/stars/HyperSystemsDev/Werchat?style=social)](https://github.com/HyperSystemsDev/Werchat)

**Channel-based chat system for Hytale servers.** Multiple channels, quick-chat routing, private messaging, nicknames, and moderation — all in one plugin.

**[Discord](https://discord.gg/SNPjyfkYPc)**

![Werchat](WerChat.png)

## Features

**Channel System** — Multiple channels with custom colors, nicknames, distance-based and world-restricted delivery.

**Quick Chat** — Prefix symbols to route messages to any channel without switching. `!` for Global, `~` for Trade.

**Private Messaging** — `/msg` and `/r` with reply tracking, ignore lists, and offline detection.

**Player Nicknames** — Custom display names up to 20 characters with hex colors and two-color gradients.

**Mention System** — `@player` highlighting with configurable color and bold formatting.

**Clickable URLs** — Chat and private messages automatically turn URLs into clickable links (configurable).

**Moderation Tools** — Per-channel ban, mute, moderators, word filter (censor/block modes), and chat cooldown.

**In-Game Settings UI** — `/ch`, `/ch settings`, `/ch list`, and `/ch help` open a unified UI for profile, channels, and moderator tools.

**Permission Integration** — HyperPerms and LuckPerms prefix/suffix support with hex color codes.

**PlaceholderAPI Integration** — Supports external `%...%` placeholders in channel format text and includes a built-in `%werchat_*%` expansion.

**Plugin API** — Exposes a lightweight `WerchatAPI` for other plugins to query channels, manage membership/focus, and submit chat through Werchat's pipeline.

**Message Colors** — Independent message text colors and gradients, separate from nickname colors.

**Persistent Storage** — JSON-based data with dirty + debounced saves (20s) and a final flush on shutdown.

## Quick Start

1. Drop `Werchat-1.11.1.jar` in your `mods/` folder
2. Start your server — four default channels are created automatically
3. Use `/ch` to open the settings UI (`/ch list` opens directly to Channels)
4. Type `!hello` to quick-chat in Global or `~hello` for Trade

```
/ch                         # Open Werchat settings UI
/ch list                    # Open Channels tab
/ch global                  # Switch to Global channel
/ch join trade              # Join the Trade channel
/msg Steve Hey!             # Send a private message
/ch playernick CoolName     # Set your nickname
```

## Default Channels

| Channel | Nick | Color | Range | Auto-Join | Quick-Chat Symbol |
|---------|------|-------|-------|-----------|-------------------|
| Global | Global | White (`#ffffff`) | Unlimited | Yes | `!` |
| Local | Local | Gray (`#808080`) | 100 blocks | Yes | — |
| Trade | Trade | Gold (`#ffd700`) | Unlimited | Yes | `~` |
| Support | Support | Green (`#00ff00`) | Unlimited | Yes | — |

## Settings UI

- `/ch` or `/ch settings` opens the Werchat settings UI.
- Main tab includes focused-channel controls, nickname/color tools, and permission-filtered command help.
- Channels tab includes join/leave/focus actions plus owner, description, and MOTD visibility.
- Moderator channel management uses a dropdown selector so large channel counts stay usable.

![Werchat Settings - Channels Tab](docs/images/settings-ui-channels.png)

## Commands

### Player Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/ch` | — | Open Werchat settings UI |
| `/ch settings` | — | Open Werchat settings UI |
| `/ch list` | `/ch l` | Open Channels tab in the settings UI |
| `/ch join <channel> [password]` | `/ch j` | Join a channel |
| `/ch leave <channel>` | — | Leave a channel |
| `/ch <channel>` | — | Switch active channel |
| `/ch who <channel>` | `/ch w` | View channel members |
| `/ch info <channel>` | — | View channel details |
| `/ch create <name> [nick]` | — | Create a new channel |
| `/ch playernick <name> [#color] [#gradient]` | `/ch pnick`, `/ch nickname` | Set your nickname |
| `/ch msgcolor <#color> [#gradient]` | `/ch chatcolor` | Set your message color |
| `/ch help` | `/ch ?` | Open Main tab help (permission-filtered) |

### Messaging Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/msg <player> <message>` | `/whisper`, `/w`, `/tell`, `/pm` | Send a private message |
| `/r <message>` | `/reply` | Reply to last PM |
| `/ignore <player>` | — | Toggle ignore on a player |
| `/ignorelist` | — | List ignored players |

### Admin / Moderator Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/ch color <channel> <#hex> [#hex]` | — | Set channel tag color (and optional message color) |
| `/ch nick <channel> <nick>` | — | Set channel shortcut name |
| `/ch rename <channel> <newname>` | — | Rename a channel |
| `/ch password <channel> [password]` | `/ch pass` | Set or clear channel password |
| `/ch distance <channel> <blocks>` | `/ch range` | Set range (0 = unlimited) |
| `/ch world <channel> add\|remove <world>` | — | Restrict channel to worlds |
| `/ch mod <channel> <player>` | `/ch moderator` | Add a channel moderator |
| `/ch unmod <channel> <player>` | — | Remove a channel moderator |
| `/ch ban <channel> <player>` | — | Ban a player from a channel |
| `/ch unban <channel> <player>` | — | Unban a player |
| `/ch mute <channel> <player>` | — | Mute a player in a channel |
| `/ch unmute <channel> <player>` | — | Unmute a player |
| `/ch remove <channel>` | `/ch delete`, `/ch del` | Delete a channel |
| `/ch reload` | — | Reload Werchat config and channel data from disk |

Channel moderators can use admin commands on their own channels without needing global permission nodes.

If `/ch create <name>` is used without a nick, Werchat auto-generates a short nick from the first character and disambiguates collisions:
- Example: `12312345 -> 1`, `1234903425 -> 1-2`, `1 -> 1-3`.

<details>
<summary><strong>All Permissions</strong></summary>

| Permission | Description |
|------------|-------------|
| `werchat.*` | All Werchat permissions (wildcard) |
| `werchat.list` | List channels |
| `werchat.join` | Join channels |
| `werchat.leave` | Leave channels |
| `werchat.create` | Create channels |
| `werchat.who` | View channel members |
| `werchat.info` | View channel info |
| `werchat.switch` | Switch active channel |
| `werchat.color` | Set channel colors |
| `werchat.nick` | Set channel nick |
| `werchat.password` | Set channel password |
| `werchat.rename` | Rename channels |
| `werchat.remove` | Delete channels |
| `werchat.mod` | Manage channel moderators |
| `werchat.distance` | Set channel range |
| `werchat.ban` | Ban/unban from channels |
| `werchat.mute` | Mute/unmute in channels |
| `werchat.world` | Set world restrictions |
| `werchat.reload` | Reload Werchat config/channel data |
| `werchat.msg` | Send private messages |
| `werchat.ignore` | Ignore players |
| `werchat.quickchat` | Use quick-chat symbols |
| `werchat.playernick` | Set own nickname |
| `werchat.playernick.others` | Set other players' nicknames |
| `werchat.nickcolor` | Set nickname color/gradient |
| `werchat.msgcolor` | Set own message color |
| `werchat.msgcolor.others` | Set other players' message color |
| `werchat.cooldown.bypass` | Bypass chat cooldown |

Players with `werchat.*` or `*` also bypass cooldowns and the word filter.

Per-channel permission nodes:
- `werchat.channel.<channel>.join`
- `werchat.channel.<channel>.speak`
- `werchat.channel.<channel>.read` (primary receive/read node)

These nodes are enforced in normal `/ch` + chat flow only when `channelPermissions.enforce` is enabled.

</details>

## Configuration

Config file: `mods/com.werchat_Werchat/config.json`

<details>
<summary><strong>View full config</strong></summary>

```json
{
  "defaultChannelName": "Global",
  "autoJoinDefault": true,
  "showJoinLeaveMessages": true,
  "allowPrivateMessages": true,
  "channelPermissions": {
    "enforce": false
  },
  "banMessage": "You have been banned from {channel}",
  "muteMessage": "You have been muted in {channel}",
  "wordFilter": {
    "enabled": false,
    "mode": "censor",
    "replacement": "***",
    "notifyPlayer": true,
    "warningMessage": "Your message contained inappropriate language.",
    "words": ["..."]
  },
  "cooldown": {
    "enabled": false,
    "seconds": 3,
    "message": "Please wait {seconds}s before sending another message.",
    "bypassPermission": "werchat.cooldown.bypass"
  },
  "mentions": {
    "enabled": true,
    "color": "#FFFF55"
  },
  "clickableUrls": {
    "enabled": true
  },
  "ignoreChatCancellations": false
}
```

| Key | Default | Description |
|-----|---------|-------------|
| `defaultChannelName` | `"Global"` | Default channel players are placed in |
| `autoJoinDefault` | `true` | Auto-join default channel on connect |
| `showJoinLeaveMessages` | `true` | Broadcast join/leave messages to channels |
| `allowPrivateMessages` | `true` | Whether `/msg` is enabled |
| `channelPermissions.enforce` | `false` | Enforce per-channel `join`/`speak`/`read` nodes in normal `/ch` + chat flow |
| `banMessage` | `"You have been banned from {channel}"` | Message shown to banned players |
| `muteMessage` | `"You have been muted in {channel}"` | Message shown to muted players |
| `wordFilter.enabled` | `false` | Enable the word filter |
| `wordFilter.mode` | `"censor"` | `censor` replaces bad words, `block` rejects the message |
| `wordFilter.replacement` | `"***"` | Replacement string in censor mode |
| `wordFilter.notifyPlayer` | `true` | Warn the player when filtered |
| `wordFilter.warningMessage` | `"Your message contained..."` | Warning message text |
| `wordFilter.words` | `[]` | Words to filter (case-insensitive); empty by default |
| `cooldown.enabled` | `false` | Enable chat cooldown |
| `cooldown.seconds` | `3` | Seconds between messages |
| `cooldown.message` | `"Please wait {seconds}s..."` | Cooldown message |
| `cooldown.bypassPermission` | `"werchat.cooldown.bypass"` | Permission to bypass cooldown |
| `mentions.enabled` | `true` | Enable @mention highlighting |
| `mentions.color` | `"#FFFF55"` | Hex color for mention highlights |
| `clickableUrls.enabled` | `true` | Convert detected URLs in chat/PM text into clickable links |
| `ignoreChatCancellations` | `false` | Process chat even if cancelled by other plugins |

When `channelPermissions.enforce` is enabled:
- `join` checks run in `/ch join` and `/ch <channel>` auto-join.
- `speak` checks run before sending messages.
- `read` checks run for sender selection and per-recipient delivery.
- Password checks still apply; permission nodes never bypass channel passwords.

</details>

<details>
<summary><strong>Channel Format Placeholders</strong></summary>

Custom channel message formats support these placeholders:

| Placeholder | Description |
|-------------|-------------|
| `{name}` | Full channel name |
| `{nick}` | Channel nick/abbreviation |
| `{color}` | Channel color code |
| `{sender}` | Player display name |
| `{msg}` | Message content |
| `{prefix}` | Player's permission prefix (HyperPerms/LuckPerms) |
| `{suffix}` | Player's permission suffix (HyperPerms/LuckPerms) |

Default format: `{nick} {sender}: {msg}`

Format literals also support PlaceholderAPI placeholders when PlaceholderAPI is installed.

Werchat also registers a built-in PlaceholderAPI expansion with identifier `werchat`.

Top-level Werchat placeholders:

| Placeholder | Description |
|-------------|-------------|
| `%werchat_channels_total%` | Total number of channels |
| `%werchat_channels%` | Comma-separated channel names |
| `%werchat_default_channel%` | Default channel name |
| `%werchat_selected_channel%` | Focused channel name for the player (recommended) |
| `%werchat_selected_channel_<key>%` | Focused channel field/value by key (preferred keyed form) |
| `%werchat_channel%` | Focused channel name for the player (legacy alias) |
| `%werchat_channel_<selector>%` | Specific channel by selector (returns channel name) |
| `%werchat_channel_<selector>_<key>%` | Specific channel field/value by selector + key (primary keyed form) |
| `%werchat_channel_<selector>__<key>%` | Edge-case keyed syntax for selectors with underscore ambiguity |
| `%werchat_ignored_players_total%` | Number of ignored players |
| `%werchat_ignored_players%` | Comma-separated ignored player names |
| `%werchat_known_name%` | Real account username |
| `%werchat_display_colour%` | Player display color |
| `%werchat_display_color%` | Player display color (US spelling alias) |
| `%werchat_msg_color%` | Player message color |
| `%werchat_msg_gradient%` | Player message gradient as `#START,#END` (blank when no gradient) |
| `%werchat_msg_gradient_end%` | Player message gradient end color |
| `%werchat_nick_color%` | Player nickname color |
| `%werchat_nick_gradient_end%` | Player nickname gradient end color |
| `%werchat_nick%` | Custom nickname only (blank if none set) |
| `%werchat_display_name%` | Final chat name (nickname if set, else username) |

Name placeholder semantics:
- `known_name`: always the real account name.
- `nick`: only the custom nickname value, can be blank.
- `display_name`: what Werchat renders in chat.

Channel-scoped Werchat placeholder syntax:

Preferred keyed forms:

`%werchat_selected_channel_<key>%`

`%werchat_channel_<selector>_<key>%`

Braced selector forms (recommended for underscore channel names):

`%werchat_channel_{<selector>}%`

`%werchat_channel_{<selector>}_<key>%`

Direct channel selector alias:

`%werchat_channel_<selector>%` (returns that channel's name)

Edge-case keyed form (use only when selector/key underscore ambiguity appears):

`%werchat_channel_<selector>__<key>%`

`<selector>` values:
- Exact channel name or nick.
- Optional `{}` wrapper is supported for explicit selectors (example: `{trade_german}`).
- Channel names with underscores are supported (example: `trade_name`).
- Legacy active alias (`active` by default) is still supported for compatibility.

Key separator note:
- `_` (single underscore) is the primary keyed separator.
- `__` (double underscore) is the backup separator for ambiguous channel names (example: `trade_name`).
- Example explicit form: `%werchat_channel_{trade_german}_member_count%`.

`<key>` values:

| Key | Description |
|-----|-------------|
| `name` | Channel's canonical name (what admins create/rename) |
| `nick` | Channel's short label/alias |
| `colorhex` | Channel tag color as hex (`#RRGGBB`) |
| `format` | Raw channel format template string |
| `color` | Channel tag color as hex (`#RRGGBB`) |
| `effective_msg_colorhex` | Final message text color hex (message color override or tag color fallback) |
| `join_permission` | Permission node string associated with joining this channel (`werchat.channel.<name>.join`) |
| `read_permission` | Permission node string associated with reading/receiving this channel (`werchat.channel.<name>.read`) |
| `msg_color_hex` | Explicit message text color hex override (`#RRGGBB`), blank when unset |
| `msg_color` | Explicit message text color hex (`#RRGGBB`), blank when unset |
| `quickchatsymbol` | Quick-chat trigger symbol (for example `!`), blank when unset |
| `speak_permission` | Permission node string associated with speaking in this channel (`werchat.channel.<name>.speak`) |
| `worlds_count` | Number of configured world restrictions |
| `worlds` | Worlds shown as text (`All worlds` or a comma-separated list) |
| `distance` | Channel chat range in blocks (`0` means global/unlimited) |
| `member_count` | Current number of channel members |
| `member_names` | Comma-separated member names (online username, fallback known name, then short UUID) |
| `moderator_count` | Current number of channel moderators |
| `moderator_names` | Comma-separated moderator names (online username, fallback known name, then short UUID) |
| `muted_count` | Current number of muted members |
| `muted_names` | Comma-separated muted member names (online username, fallback known name, then short UUID) |
| `owner` | Channel owner UUID |
| `owner_name` | Best-known owner name (online username fallback to stored known name) |
| `is_autojoin` | `true` if this channel auto-joins players on connect |
| `is_muted` | `true` if the requesting player context is muted in this channel |
| `is_banned` | `true` if the requesting player context is banned from this channel |
| `is_member` | `true` if the requesting player context is a member of this channel |
| `is_moderator` | `true` if the requesting player context is a moderator of this channel |
| `is_verbose` | `true` if channel verbose mode is enabled |
| `is_default` | `true` if this is the server default channel |
| `is_focusable` | `true` if players are allowed to focus/select this channel |
| `is_global` | `true` if channel range is global (`distance <= 0`) |
| `is_local` | `true` if channel range is local (`distance > 0`) |
| `has_password` | `true` if a password is set on this channel |
| `has_msg_color` | `true` if explicit message color override is set |
| `has_worlds` | `true` if world restrictions are configured |
| `has_quickchatsymbol` | `true` if a quick-chat symbol is configured |

</details>

<details>
<summary><strong>Plugin API</strong></summary>

Access Werchat's integration API:

```java
WerchatAPI api = WerchatPlugin.api();
if (api != null) {
    WerchatOperationOptions opts = WerchatOperationOptions.enforcePermissions();

    WerchatActionResult join = api.joinChannel(playerId, "trade", null, opts);
    if (join.isSuccess()) {
        api.setFocusedChannel(playerId, "trade", opts);
        api.submitPlayerChat(playerId, "Selling iron!", opts);
    }
}
```

API notes:
- `submitPlayerChat(...)` is the primary API method for submitting player chat.
- `WerchatActionResult` and `WerchatMembershipResult` expose explicit status enums instead of booleans.
- Channel lookups can now be explicit: use `getChannelExact(...)`, `joinChannelExact(...)`, `setFocusedChannelExact(...)`, etc. for deterministic integrations, or default/fuzzy methods for command-like behavior.
- `api.getApiVersion()`, `api.getCapabilities()`, and `api.hasCapability(...)` let integrations gate behavior safely.
- Hooks are available through `registerHook(...)` / `unregisterHook(...)` for pre/post API action handling.

</details>

<details>
<summary><strong>Building from Source</strong></summary>

**Requirements:** Java 21+, Gradle 8.12+ (wrapper included)

```bash
./gradlew buildRelease
# Output: build/libs/Werchat-1.11.1.jar (built for latest release channel)
```

Build variants:

```bash
./gradlew buildRelease      # latest release channel
./gradlew buildPreRelease   # latest pre-release channel
./gradlew build             # defaults to release channel
```

The plugin `manifest.json` `ServerVersion` is injected automatically at build time from Hytale Maven metadata.  
If you need a manual override for testing, set an explicit version:

```bash
./gradlew build -Phytale_server_version=2026.02.18-f3b8fff95
```

</details>

## Links

- [Discord](https://discord.gg/SNPjyfkYPc) — Support & community
- [Issues](https://github.com/HyperSystemsDev/Werchat/issues) — Bug reports & features
- [Releases](https://github.com/HyperSystemsDev/Werchat/releases) — Downloads

---

Part of the **HyperSystems** suite: [HyperPerms](https://github.com/HyperSystemsDev/HyperPerms) | [HyperHomes](https://github.com/HyperSystemsDev/HyperHomes) | [HyperFactions](https://github.com/HyperSystemsDev/HyperFactions) | [Werchat](https://github.com/HyperSystemsDev/Werchat)
