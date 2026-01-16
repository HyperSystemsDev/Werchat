# Werchat

A channel-based chat system for Hytale servers, inspired by HeroChat for Minecraft.

## Features

- **Multiple chat channels** - Global, Local, Trade, Help, and custom channels
- **Channel management** - Create, join, leave, and focus channels
- **Private messaging** - /msg and /r commands for direct player communication
- **Channel moderation** - Ban, mute, and moderator permissions per channel
- **Ignore system** - Block messages from specific players
- **Auto-join** - Automatically join default channels on login
- **Distance-based local chat** - Local channel only reaches nearby players

## Requirements

- Java 25
- Gradle 9.2+
- Hytale Server JAR (for compilation)

## Building

1. **Get the Hytale Server JAR**

   Copy `HytaleServer.jar` from your Hytale server installation to the `libs/` directory:

   ```
   Werchat/
   ├── libs/
   │   └── HytaleServer.jar   <-- Place server JAR here
   ├── src/
   └── build.gradle.kts
   ```

2. **Build the plugin**

   ```bash
   ./gradlew shadowJar
   ```

   The compiled JAR will be in `build/libs/Werchat-1.0.0.jar`

## Installation

1. Copy `Werchat-1.0.0.jar` to your Hytale server's mods folder:
   - Windows: `%appdata%/Hytale/UserData/Mods/`
   - Linux: `~/.config/Hytale/UserData/Mods/`

2. Restart your server

## Commands

### Channel Commands (`/ch` or `/channel`)

| Command | Description |
|---------|-------------|
| `/ch list` | List all channels |
| `/ch join <channel> [password]` | Join a channel |
| `/ch leave <channel>` | Leave a channel |
| `/ch focus <channel>` | Set your speaking channel |
| `/ch create <name> [nick]` | Create a new channel |
| `/ch who <channel>` | List channel members |
| `/ch help` | Show help |

### Private Messaging

| Command | Description |
|---------|-------------|
| `/msg <player> <message>` | Send a private message |
| `/r <message>` | Reply to last private message |

Aliases: `/whisper`, `/w`, `/tell`, `/pm`, `/reply`

## Default Channels

| Channel | Nick | Description |
|---------|------|-------------|
| Global | g | Server-wide chat (default, auto-join) |
| Local | l | Distance-based chat (100 blocks, auto-join) |
| Trade | t | Trading channel |
| Help | h | Help channel (auto-join) |

## Permissions

Each channel has three permission nodes:
- `werchat.channel.<name>.join` - Join the channel
- `werchat.channel.<name>.speak` - Send messages
- `werchat.channel.<name>.see` - See messages

## Development

### Project Structure

```
src/main/java/com/mansfielddev/werchat/
├── WerchatPlugin.java          # Main plugin class
├── channels/
│   ├── Channel.java            # Channel data model
│   └── ChannelManager.java     # Channel registry
├── commands/
│   ├── ChannelCommand.java     # /ch command
│   ├── MessageCommand.java     # /msg command
│   └── ReplyCommand.java       # /r command
├── listeners/
│   ├── ChatListener.java       # Chat event handler
│   └── PlayerListener.java     # Join/leave handler
└── storage/
    └── PlayerDataManager.java  # Player preferences
```

### API Patterns Used

- **Plugin lifecycle**: `setup()` / `shutdown()` methods
- **Event registration**: `getEventRegistry().registerGlobalListener()`
- **Command registration**: `getCommandRegistry().registerCommand()`
- **Messaging**: `Message.raw().color()` and `Message.join()`

## License

MIT License - See LICENSE file

## Credits

- Mansfield Direct
- Inspired by HeroChat for Bukkit/Spigot
