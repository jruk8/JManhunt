## [Unreleased]

### ✨ Features

- **#39** Add datapack entries for desert pyramids, mineshafts, and mesa
  mineshafts to the overworld-structures datapack, boosting their spawn
  frequency alongside villages, shipwrecks, buried treasure, and ruined
  portals.
- **#36** Add an optional `settings.world-engine.on-fetch-new-cell` command
  array with `<cellX>` and `<cellZ>` placeholders to support chunk-generation
  plugins such as Chunky. A cleanliness flag avoids unnecessary regeneration
  of already-fetched cells.
- **#35** Set the native `spectators_generate_chunks` gamerule to `false`
  during a match to prevent spectators from generating chunks.
- **#33** Add `manhunt qs` (and `mh qs`) alias for `/manhunt quickstart`.
- **#40** Add `settings.compass.tracking-distance` configuration option
  controlling how far away the compass can track a player (default `-1` for
  unlimited distance).

### 🐛 Bug Fixes

- **#37** Lucky blocks no longer drop outcomes when breaking the block would
  not normally produce a drop (e.g. using the wrong tool).
- **#34** No-jump now uses `PlayerJumpEvent` for a smoother experience, and
  one-heart is applied to all participants when the game actually begins
  (rather than only on join/respawn).
- **#32** Spectators are no longer tracked by the compass and their
  last-seen location is no longer updated while in spectator mode.

## [3.1.1] - 2026-08-08

### 🐛 Bug Fixes

- Create CHANGELOG.md

