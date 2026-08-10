## [Unreleased]

### 🚀 Features

- **Schematic pivot point system**: Schematics now save a pivot offset from the
  origin corner to a door/gate block. When loading, the structure is rotated to
  match the player's facing and pasted so the door lands on the player. The
  door block is auto-detected by scanning the region, or can be targeted
  explicitly with the player's crosshair when saving.
- **Alternate win conditions** (#41): Multiple win conditions can be enabled
  simultaneously. Speedrunners win as soon as any one is satisfied:
  - `exitEnd` (enabled by default) — exit the End and return to the Overworld
  - `surviveTime` — survive for a configured number of seconds
  - `acquireItem` — acquire a configured item
  - `reachAdvancement` — complete a configured advancement
- **Start delay for speedrunners** (#40): A configurable start delay (default
  30s, disabled by default) puts all hunters in spectator mode for the initial
  delay of the match, giving speedrunners a head start. Compatible with
  start-on-speedrunner-damage: the delay countdown only starts when the
  speedrunner first damages a hunter.

### 🐛 Bug Fixes

- AFK players are no longer set to spectator mode when a match starts.
- NONE players are now teleported to the lobby (in addition to being set to
  spectator) when a match starts.
- Lucky block announcement is now shown after the match status instead of
  before, so players no longer need to scroll to see it.

## [3.2.0] - 2026-08-09

### 🚀 Features

- Numerous fixes, qs subcommand alias
- Add datapack entries for desert pyramids, mineshafts, and mesa mineshafts (#39)

### ⚙️ Miscellaneous Tasks

- *(changelog)* Update for v3.1.2 [skip ci]
## [3.1.1] - 2026-08-08

### 🐛 Bug Fixes

- Create CHANGELOG.md

