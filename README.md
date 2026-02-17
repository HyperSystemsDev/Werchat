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

**Moderation Tools** — Per-channel ban, mute, moderators, word filter (censor/block modes), and chat cooldown.

**Permission Integration** — HyperPerms and LuckPerms prefix/suffix support with hex color codes.

**Message Colors** — Independent message text colors and gradients, separate from nickname colors.

**Persistent Storage** — JSON-based data with auto-save every 5 minutes and immediate save on changes.

## Quick Start

1. Drop `Werchat-1.1.8.jar` in your `mods/` folder
2. Start your server — four default channels are created automatically
3. Use `/ch list` to see channels, `/ch join <channel>` to join one
4. Type `!hello` to quick-chat in Global or `~hello` for Trade

```
/ch list                    # List all channels
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

## Commands

### Player Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/ch list` | `/ch l` | List all channels |
| `/ch join <channel> [password]` | `/ch j` | Join a channel |
| `/ch leave <channel>` | — | Leave a channel |
| `/ch <channel>` | — | Switch active channel |
| `/ch who <channel>` | `/ch w` | View channel members |
| `/ch info <channel>` | — | View channel details |
| `/ch create <name> [nick]` | — | Create a new channel |
| `/ch playernick <name> [#color] [#gradient]` | `/ch pnick`, `/ch nickname` | Set your nickname |
| `/ch msgcolor <#color> [#gradient]` | `/ch chatcolor` | Set your message color |
| `/ch help` | `/ch ?` | Show command help |

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

Channel moderators can use admin commands on their own channels without needing global permission nodes.

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
  "ignoreChatCancellations": false
}
```

| Key | Default | Description |
|-----|---------|-------------|
| `defaultChannelName` | `"Global"` | Default channel players are placed in |
| `autoJoinDefault` | `true` | Auto-join default channel on connect |
| `showJoinLeaveMessages` | `true` | Broadcast join/leave messages to channels |
| `allowPrivateMessages` | `true` | Whether `/msg` is enabled |
| `banMessage` | `"You have been banned from {channel}"` | Message shown to banned players |
| `muteMessage` | `"You have been muted in {channel}"` | Message shown to muted players |
| `wordFilter.enabled` | `false` | Enable the word filter |
| `wordFilter.mode` | `"censor"` | `censor` replaces bad words, `block` rejects the message |
| `wordFilter.replacement` | `"***"` | Replacement string in censor mode |
| `wordFilter.notifyPlayer` | `true` | Warn the player when filtered |
| `wordFilter.warningMessage` | `"Your message contained..."` | Warning message text |
| `wordFilter.words` | (default list) | Words to filter (case-insensitive) |
| `cooldown.enabled` | `false` | Enable chat cooldown |
| `cooldown.seconds` | `3` | Seconds between messages |
| `cooldown.message` | `"Please wait {seconds}s..."` | Cooldown message |
| `cooldown.bypassPermission` | `"werchat.cooldown.bypass"` | Permission to bypass cooldown |
| `mentions.enabled` | `true` | Enable @mention highlighting |
| `mentions.color` | `"#FFFF55"` | Hex color for mention highlights |
| `ignoreChatCancellations` | `false` | Process chat even if cancelled by other plugins |

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

</details>

<details>
<summary><strong>Building from Source</strong></summary>

**Requirements:** Java 21+, Gradle 8.12+ (wrapper included)

```bash
./gradlew jar
# Output: build/libs/Werchat-1.1.8.jar
```

`manifest.json` now needs an explicit `ServerVersion` semver range (not `*`), for example:

```json
"ServerVersion": "=2026.02.17-255364b8e"
```

</details>

## Links

- [Discord](https://discord.gg/SNPjyfkYPc) — Support & community
- [Issues](https://github.com/HyperSystemsDev/Werchat/issues) — Bug reports & features
- [Releases](https://github.com/HyperSystemsDev/Werchat/releases) — Downloads

---

Part of the **HyperSystems** suite: [HyperPerms](https://github.com/HyperSystemsDev/HyperPerms) | [HyperHomes](https://github.com/HyperSystemsDev/HyperHomes) | [HyperFactions](https://github.com/HyperSystemsDev/HyperFactions) | [Werchat](https://github.com/HyperSystemsDev/Werchat)
