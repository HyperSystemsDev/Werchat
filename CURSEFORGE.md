# Werchat - Channel Chat System for Hytale

A fully-featured chat channel system for Hytale servers. Organize player communication with customizable channels, private messaging, moderation tools, and more.

## Features

- **Multiple Chat Channels** - Create and manage unlimited chat channels
- **Channel Customization** - Set colors, nicknames, and passwords for each channel
- **Private Messaging** - Send private messages with `/msg` and quick replies with `/r`
- **Channel Moderation** - Ban, mute, and manage channel moderators
- **Auto-Join Channels** - Configure channels players automatically join on connect
- **Local/Global Chat** - Distance-based local channels or server-wide global channels
- **Persistent Storage** - All channel settings saved to disk

### New in v1.1.8

- **Separate Tag & Text Colors** - `/ch color <channel> <#tag> [#text]` for independent tag/message colors
- **Per-Channel Quick Chat** - Enable/disable quick chat symbols per channel in `channels.json`
- **Split Config Files** - Channel settings and member data stored separately for cleaner configs
- **Player Names in Member Data** - `channel-members.json` shows usernames alongside UUIDs

## Commands

### General Commands (All Players)

| Command | Description |
|---------|-------------|
| `/ch <name>` | Switch to a channel (supports partial matching) |
| `/ch list` | List all available channels |
| `/ch join <channel> [password]` | Join a channel |
| `/ch leave <channel>` | Leave a channel |
| `/ch who <channel>` | See online members in a channel |
| `/ch info <channel>` | View channel details (owner, mods, range) |
| `/ch help` | Show help menu |
| `/msg <player> <message>` | Send a private message |
| `/r <message>` | Reply to last private message |
| `/ignore <player>` | Toggle ignoring a player |
| `/ignorelist` | Show your ignored players |
| `/ch playernick <name> [#color] [#gradient]` | Set your display nickname |
| `/ch playernick reset` | Clear your nickname |
| `/ch msgcolor <#color> [#gradient]` | Set your message color |
| `/ch msgcolor reset` | Clear your message color |

### Channel Management (Moderators/Permission Holders)

| Command | Permission | Description |
|---------|------------|-------------|
| `/ch create <name>` | `werchat.create` | Create a new channel |
| `/ch remove <channel>` | `werchat.remove` | Delete a channel |
| `/ch color <channel> <#tag> [#text]` | `werchat.color` | Set channel tag color and optional text color |
| `/ch nick <channel> <nick>` | `werchat.nick` | Set channel shortcut/nickname |
| `/ch password <channel> [pw]` | `werchat.password` | Set or clear channel password |
| `/ch rename <channel> <newname>` | `werchat.rename` | Rename a channel |
| `/ch mod <channel> <player>` | `werchat.mod` | Add a channel moderator |
| `/ch unmod <channel> <player>` | `werchat.mod` | Remove a channel moderator |
| `/ch distance <channel> <blocks>` | `werchat.distance` | Set chat range (0 = global) |
| `/ch world <channel> add\|remove <world>` | `werchat.world` | Add/remove world restrictions |
| `/ch ban <channel> <player>` | `werchat.ban` | Ban player from channel |
| `/ch unban <channel> <player>` | `werchat.ban` | Unban player from channel |
| `/ch mute <channel> <player>` | `werchat.mute` | Mute player in channel |
| `/ch unmute <channel> <player>` | `werchat.mute` | Unmute player in channel |
| `/ch playernick <player> <name> [#color] [#gradient]` | `werchat.playernick.others` | Set another player's nickname |
| `/ch msgcolor <player> <#color> [#gradient]` | `werchat.msgcolor.others` | Set another player's message color |

## Permissions

All permissions are grant-based. Players need the relevant permission node (or `werchat.*` / `*`) to use each command.

### Wildcards

| Permission | Description |
|------------|-------------|
| `*` | Grants all permissions (includes all werchat permissions) |
| `werchat.*` | Grants all Werchat permissions |

### Player Commands

| Permission | Description |
|------------|-------------|
| `werchat.list` | List available channels (`/ch list`) |
| `werchat.join` | Join channels (`/ch join`) |
| `werchat.leave` | Leave channels (`/ch leave`) |
| `werchat.switch` | Switch focused channel (`/ch <name>`) |
| `werchat.who` | View online members (`/ch who`) |
| `werchat.info` | View channel details (`/ch info`) |
| `werchat.msg` | Send private messages (`/msg`, `/r`) |
| `werchat.ignore` | Ignore/unignore players (`/ignore`) |
| `werchat.playernick` | Set a player nickname |
| `werchat.nickcolor` | Use colors in player nicknames |
| `werchat.msgcolor` | Set custom message colors |
| `werchat.quickchat` | Use quick chat symbol triggers |

### Channel Management

| Permission | Description |
|------------|-------------|
| `werchat.create` | Create new channels |
| `werchat.remove` | Delete any channel |
| `werchat.color` | Change any channel's color |
| `werchat.nick` | Change any channel's nickname |
| `werchat.password` | Set/clear any channel's password |
| `werchat.rename` | Rename any channel |
| `werchat.mod` | Add/remove moderators on any channel |
| `werchat.distance` | Set chat range on any channel |
| `werchat.world` | Set world restriction on any channel |
| `werchat.ban` | Ban/unban players from any channel |
| `werchat.mute` | Mute/unmute players in any channel |
| `werchat.playernick.others` | Set/clear another player's nickname |
| `werchat.msgcolor.others` | Set/clear another player's message color |

**Note:** Management commands can also be used by **channel moderators** without needing explicit permissions. The creator of a channel automatically becomes its owner and moderator.

## Configuration (config.json)

Werchat creates a `config.json` file in the plugin data directory with these options:

```json
{
  "wordFilter": {
    "enabled": false,
    "mode": "censor",
    "replacement": "***",
    "notifyPlayer": true,
    "warningMessage": "Your message contained inappropriate language.",
    "words": ["badword1", "badword2"]
  },
  "cooldown": {
    "enabled": false,
    "seconds": 3,
    "message": "Please wait {seconds}s before sending another message."
  },
  "mentions": {
    "enabled": true,
    "color": "#FFFF55"
  }
}
```

### Word Filter Modes

- **censor**: Replaces bad words with `***` but sends the message
- **block**: Blocks the entire message from being sent

**Note:** Admins (players with `*` or `werchat.*` permission) bypass the word filter. A default list of common profanity is included - customize via `config.json`.

## Data Files

Werchat stores its data in the plugin directory (`mods/com.werchat_Werchat/`):

| File | Purpose |
|------|---------|
| `config.json` | Global settings (word filter, cooldown, mentions) |
| `channels.json` | Channel settings (colors, format, distance, quick chat, etc.) |
| `channel-members.json` | Channel membership, moderators, bans, mutes (with player names) |
| `nicknames.json` | Player nicknames and custom colors |

`channels.json` is safe to hand-edit. `channel-members.json` shows player usernames next to UUIDs for readability — only the UUIDs are used when loading.

## @Mentions

Type `@username` to mention someone. The mentioned player sees the message highlighted in yellow.

Example: `Hey @Wer check this out!`

## Ignore System

- `/ignore <player>` - Toggle ignoring a player (you won't see their messages)
- `/ignorelist` - See all players you're ignoring
- Use `/ignore <player>` again to unignore

## Channel Ownership & Moderation

- **Owner**: The player who created the channel. Shown in `/ch info`.
- **Moderators**: Players who can manage channel settings. The owner is automatically a moderator.
- **Adding Moderators**: Use `/ch mod <channel> <player>` to add a moderator.
- **Removing Moderators**: Use `/ch unmod <channel> <player>` to remove a moderator.

## Banning & Muting

- **Ban**: Removes player from channel and prevents rejoining. Use `/ch ban <channel> <player>`.
- **Mute**: Player can read messages but cannot send. Use `/ch mute <channel> <player>`.
- **Unban/Unmute**: Use `/ch unban` or `/ch unmute` to reverse.

Players are notified when banned or muted with a configurable message.

## Per-World Channels

Channels can be restricted to one or more worlds. When a channel has worlds set, only players in those worlds can send and receive messages in the channel.

### Configuration

Set the `"worlds"` field in `channels.json`:

```json
{
  "name": "Mining",
  "nick": "Mining",
  "color": "#FFD700",
  "messageColor": "",
  "format": "{nick} {sender}: {msg}",
  "distance": 0,
  "worlds": ["mining-world", "cave-world"],
  "password": null,
  "quickChatSymbol": "",
  "quickChatEnabled": false,
  "isDefault": false,
  "autoJoin": true
}
```

### Commands

| Command | Description |
|---------|-------------|
| `/ch world <channel> add <world>` | Add a world to the restriction list |
| `/ch world <channel> remove <world>` | Remove a world from the list |
| `/ch world <channel> none` | Clear all world restrictions |

An empty `"worlds": []` means the channel is available in all worlds.

## Local vs Global Channels

Channels can be **global** (server-wide) or **local** (distance-based):

- **Global**: Range set to 0. All members see messages regardless of location.
- **Local**: Range set to a number of blocks. Only members within range see messages.

Use `/ch distance <channel> <blocks>` to configure. Example: `/ch distance local 50` limits chat to 50 blocks.

## Default Channels

Werchat creates these channels on first run:

| Channel | Display | Color | Description |
|---------|---------|-------|-------------|
| Global | [Global] | White | Server-wide chat (default) |
| Local | [Local] | Gray | Distance-based local chat (100 blocks) |
| Trade | [Trade] | Gold | Trading channel |
| Support | [Support] | Green | Support/help channel |

## Installation

1. Download `Werchat-1.1.8.jar`
2. Place in your Hytale server's `Mods` folder
3. Restart the server
4. (Optional) Edit `config.json` to enable word filter, cooldown, or announcements
5. (Optional) Grant `werchat.create` permission to staff who should create channels

## Changelog

### v1.1.8
**New Features:**
- **Separate Tag & Text Colors** - Set independent colors for channel tag and message text
  - `/ch color <channel> <#tag> [#text]` — one color sets both, two colors set them independently
  - Example: `/ch color Global #55FF55 #FFFFFF` gives a green tag but white message text
  - Shown in `/ch info` when a separate text color is set
  - Backward compatible: existing channels continue using tag color for both
- **Per-Channel Quick Chat** - Quick chat symbol triggers are now controlled per channel
  - Each channel has `quickChatEnabled` (true/false) in channels.json
  - Moved from global config.json toggle to per-channel control
  - Existing channels with symbols auto-enable on first load
- **Multi-World Channels** - Restrict channels to one or more worlds
  - Set `"worlds": ["world1", "world2"]` in channels.json
  - Players can only send/receive messages when in an allowed world
  - `/ch world <channel> add <world>` — add a world to the restriction list
  - `/ch world <channel> remove <world>` — remove a world from the list
  - `/ch world <channel> none` — clear all world restrictions
  - Requires `werchat.world` permission
  - World restriction shown in `/ch info` and `/ch list`
  - Backward compatible: old `"world": "name"` format auto-migrates to array
- **Admin Targeting for Nicknames & Message Colors** - Admins can set other players' nicknames and message colors
  - `/ch playernick <player> <name> [#color] [#gradient]` — requires `werchat.playernick.others`
  - `/ch msgcolor <player> <#color> [#gradient]` — requires `werchat.msgcolor.others`
  - Use `/ch playernick <player> reset` or `/ch msgcolor <player> reset` to clear
- **Ignore Chat Cancellations** - Optional `ignoreChatCancellations` setting in config.json
  - When enabled, Werchat processes chat even if another plugin cancelled the event
  - Useful when running alongside plugins that have their own chat formatters

**Improvements:**
- **Split Config Files** - Channel settings and member data are now stored separately
  - `channels.json` — clean, editable channel settings only
  - `channel-members.json` — member lists, moderators, bans, and mutes
  - Player usernames shown alongside UUIDs in member data for easy identification
  - Automatic migration from old format on first load
- **Organized Channel Settings** - Related fields grouped together in channels.json
  - All fields always present (no hidden optional fields)
  - `messageColor` next to `color`, `world` next to `distance`, etc.
- Removed `quickChat` section from config.json (now per-channel)

### v1.1.6
**New Features:**
- **Gradient Nicknames** - Set gradient colors for your display name
  - Usage: `/ch playernick <name> #startColor #endColor`
  - Each character smoothly transitions between colors
- **Custom Message Colors** - Override channel colors with personal colors
  - Usage: `/ch msgcolor #color [#gradientEnd]`
  - Requires `werchat.msgcolor` permission
  - Supports solid colors and gradients
- **Optional Channel Tags** - Hide `[Channel]` prefix by setting nick to empty in channels.json
- **Quick Chat Symbol Triggers** - Send messages to specific channels without switching focus
  - Prefix a message with `!` to send to Global, `~` for Trade, etc.
  - Symbols configured per-channel via `quickChatSymbol` and `quickChatEnabled` in channels.json
  - Requires `werchat.quickchat` permission
- **Granular Permissions** - Separate permission nodes for all player commands
  - `werchat.list`, `werchat.join`, `werchat.leave`, `werchat.switch`, `werchat.who`, `werchat.info`
  - `werchat.msg` (private messages), `werchat.ignore` (ignore system)
  - Help menu now hides admin commands from non-admin players
- **EssentialsPlus Compatibility** - Respects mutes from EssentialsPlus and other plugins
  - If another plugin cancels the chat event (e.g. muted player), Werchat will not process the message

**Improvements:**
- Config auto-migration - new config fields are automatically added when updating Werchat
- Default channels now include quick chat symbols (`!` for Global, `~` for Trade)

**Bug Fixes:**
- Colon separator now white instead of gray
- Removed welcome message spam on player connect
- Removed broken HyFactions integration

### v1.1.4
**New Features:**
- **LuckPerms Support** - Prefixes and suffixes now work with LuckPerms in addition to HyperPerms
- **Improved Color Code Parsing** - Properly renders all color formats:
  - Legacy codes: `§c`, `&c`
  - Hex colors: `&#RRGGBB`
  - Extended hex: `§x§R§R§G§G§B§B`

**Technical Changes:**
- Both permission plugins are soft dependencies (reflection-based, no compile-time dependency)
- Tries HyperPerms first, falls back to LuckPerms if unavailable
- Works standalone if neither plugin is installed

### v1.1.3
**New Features:**
- **Player Nicknames** - Set custom display names with `/ch playernick <name> [#color]`
  - Requires `werchat.playernick` permission to set nicknames
  - Optional custom colors (requires `werchat.nickcolor` permission)
  - Nicknames display in channel chat and private messages
  - Anti-impersonation: Cannot use another player's username
  - Max 20 characters
  - Persistent across sessions (saved to `nicknames.json`)
  - Aliases: `/ch pnick`, `/ch nickname`

**Bug Fixes:**
- Fixed permission checks not working correctly (commands were accessible to all players)
- Removed prefix/suffix integration that was interfering with other chat plugins

**Compatibility:**
- Verified compatible with party plugins (sn0wkzy:party, PartyPlugin, SimpleParty)

### v1.1.1
**Bug Fixes:**
- Fixed private messages (`/msg`, `/r`) being cut off after first word
- Fixed channel membership not persisting across server restarts
- Fixed bans and mutes not persisting across server restarts
- Removed join/leave message spam when players connect/disconnect

**Changes:**
- Trade channel now auto-joins by default

### v1.1.0
**New Features:**
- Word filter with censor or block mode (disabled by default)
- @mention highlighting (yellow for mentioned players)
- `/ignore <player>` and `/ignorelist` commands
- Chat cooldown anti-spam (disabled by default, 3 seconds)
- `config.json` for all settings
- Admin bypass - ops skip word filter and cooldown

**Improvements:**
- Channel prefixes now show full names: `[Global]` instead of `[g]`
- Joining a channel auto-focuses to it
- `/werchat` now works as alias for `/ch`
- `/ch help` shows all commands including ignore
- Fixed chat not cancelling default message properly
- Improved message formatting

### v1.0.0
- Initial release
- Channel system with create/join/leave
- Private messaging
- Channel moderation (ban/mute)
- Local/global channels
- Permission system

## Support

For issues or feature requests, leave a comment on CurseForge.

---

**Version:** 1.1.8
**Game Version:** Hytale Early Access
**Author:** Werw
**License:** MIT
