# Werchat 1.1.9 (Draft)

Status: Unreleased

## Compatibility

- Added explicit Hytale `ServerVersion` targeting in `manifest.json` (`2026.02.17-255364b8e`) to match current loader requirements.
- Added Gradle validation so `ServerVersion` must be an exact build string (no `*`, ranges, or operators).
- Updated build/deploy flow to target `%APPDATA%/Hytale/UserData/Mods` and report deployed jar paths.
- Added `channelPermissions.enforce` config for optional core enforcement of per-channel `join`/`speak`/`read` nodes.

## Added

- PlaceholderAPI integration (soft dependency) for chat format literals.
- Built-in `werchat` PlaceholderAPI expansion with:
  - global server/chat placeholders,
  - focused-channel placeholders,
  - selector-based channel placeholders (`%werchat_channel_<selector>%`, `%werchat_channel_<selector>_<key>%`),
  - player identity/display placeholders,
  - channel roster list placeholders (`member_names`, `moderator_names`, `muted_names`).
- Public Werchat API for external plugin integrations (`WerchatAPI`).
- API capability/version surface:
  - `getApiVersion()`,
  - capability keys,
  - capability checks.
- API hook system for observing/cancelling API-driven actions.
- API lookup mode support (`FUZZY` and `EXACT`) for deterministic integrations.
- Typed API result objects and status enums for action + membership outcomes.

## Changed

- Replaced fixed periodic autosave with dirty + debounced saves (20s) for channels and nicknames.
- Added final persistence flush during plugin shutdown after debounced saver shutdown.
- Channel mutations now auto-mark dirty via change listeners instead of manual save calls across commands.
- `/msg` and `/r` now respect `allowPrivateMessages` config and fail fast when PMs are disabled.
- PAPI channel color placeholders now return hex values for player-friendly configuration output.
- Channel placeholder naming/docs were normalized:
  - `selected_channel` as the recommended focused-channel placeholder,
  - `channel` retained as legacy alias,
  - dynamic channel placeholder patterns documented,
  - `worlds_display` simplified to `worlds`.
- Read/receive terminology now uses `read` as the channel node/placeholder key.

## Fixed

- Hardened PAPI placeholder resolution and naming behavior.
- Improved plugin shutdown lifecycle ordering for reliable final saves.
- Removed dead/unused code paths found during full-project cleanup.
- Implemented optional channel node enforcement in normal command/chat flow:
  - join checks in `/ch join` and `/ch <channel>` auto-join,
  - speak checks before message send,
  - read checks for message recipient delivery and channel visibility commands.
- Reduced join/leave announcement spam by emitting membership notices once through a single announce channel path instead of repeating per joined channel.
- Restored bracketed default channel tags (`[{nick}]`) and migrated legacy built-in channel templates that used unbracketed defaults.

## Docs

- Expanded README placeholder reference to include full Werchat placeholder inventory and dynamic channel key syntax.
- Expanded README API section with typed result + capability + hook guidance.
- Added `CURSEFORGE_DESCRIPTION_UPDATED.md` with refreshed project listing copy for 1.1.9.
