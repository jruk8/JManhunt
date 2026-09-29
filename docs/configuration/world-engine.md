# World Engine

**World Engine** partitions a single world into configurable cells and runs
each match on an unused one. This allows for near-infinite matches on
one world file, which is:

- automatic and faster compared to manually deleting a world file
- very performant (new cells preload in the background)
- supports up to half a billion cells (games) on just one world file

All command examples are the default config settings. A fresh `config.yml`
with every default and comment generates in the plugin data folder on first run.

Settings for the world engine are categorized under `world-engine`:

```yaml
world-engine:
  enabled: false
```

## Quick Start (3 minutes)

1. Enable World Engine in-game with `/manhunt config world-engine enabled true`.
2. Run `/manhunt worldengine tpto lobbyworld` twice. The first run tells you
   the lobby world does not exist yet; the second run (within 10 seconds)
   generates a clean void world, pastes your lobby schematic (see Lobby
   Presets below), and teleports you onto it. Lobby 0 is pointed at the
   spawn automatically, the world spawn follows the lowest lobby id with a
   teleport, and `tpto lobbyworld` always lands on that same spot.
3. Walk the pasted lobby: role pads assign roles when stood on. Falling off
   is safe: you pop back at the lobby instead of dying.
4. Restart the server. Everything works without a restart, but the stronghold
   generation changes only apply after one.

Prefer your own world instead? Set `advanced.lobbies.lobby-world-name` to its
name, reload, and `tpto lobbyworld` takes you there without generating
anything. The lobby world is remembered across restarts: while its
folder exists, it loads on boot and `tpto lobbyworld` just takes you
there. Hop back to the game world any time with
`/manhunt worldengine tpto gameworld`. The lobby world name can never
equal the game world name: a clash is refused with a warning until it is
renamed.

Fresh lobby worlds generate with safe defaults: peaceful difficulty,
frozen time and weather, no mob, trader, phantom, or patrol spawns,
and no mob griefing. These apply on generation only; changing them
later is up to you.

As your next step, it's recommended to set up **chunk pre-generation** for cells. This
may sound complex, but it's really as simple as installing [Chunky](https://modrinth.com/plugin/chunky) or a similar plugin
and [hooking it up](world-engine/pregenerating-cells.md). This takes around 5 minutes
and improves performance on low-to-mid-end servers.

New to lobbies? The [Lobby Quick Start](../../lobby-quick-start.md) walks
from zero to a queued lobby in five minutes.

## Lobby Presets

Fresh lobby worlds are filled by the `tpto lobbyworld` preset argument:
`EMPTY`, `DEFAULT`, or `ADVANCED`. Omit it and `DEFAULT` is used.
Nothing is built in code: each preset pastes its schematic from the
bundled `dev/lobby-schematics/` resources with the structure's
midpoint at 0,64,0, then runs its console commands (meant for lobby
teleports; `{world}` and `{preset}` are substituted) before the first
teleport lands. A missing schematic warns in the console and leaves
void, with a bare spawn at y=65 as the fallback. Where a preset name
has a `.jmhlobby` bundle, it wins over the legacy `.nbt`, and its
bundled bounds and teleports build into the lobby config at the paste
corner. Files dropped into the data-folder `lobby-schematics/`
directory are ignored by preset pastes: that folder is the dev
authoring workspace only.

Presets are dev-time data: they live in `Core/dev.yml` inside the jar,
never in the plugin data folder, and cannot be edited at runtime. To
change a preset's schematic or commands, edit that file (and
`dev/lobby-schematics/` for the artwork) and rebuild.

To regenerate with another preset (or an updated schematic): stop the
server, delete the lobby world folder, start up,
and run `tpto lobbyworld <selector> <preset>` twice. Presets only apply on
fresh generation: existing worlds are never touched.

Authors editing the presets themselves can build in-game and capture the
result with the [developer schematic tools](../../dev-tools.md).

## Role Pads

Role pads are lobby-world blocks that assign roles when stood on: step
inside a pad block's XZ space, at most 4 blocks above it, and you take
its role with `setplayer` semantics (queue caps and the mid-match
policy apply; AFK needs no confirmation since standing is consent).
Pads only work in the lobby world, stay silent when caps block them,
and do nothing when your role already matches. Only the spectator
gamemode blocks pads: anyone flying around as a spectator stays
untouched, while your JManhunt role never matters. Defaults under
`advanced.lobbies.role-pads`:

| Block | Role |
| --- | --- |
| `LIME_CONCRETE` | speedrunner |
| `RED_CONCRETE` | hunter |
| `YELLOW_CONCRETE` | afk |
| `LIGHT_GRAY_CONCRETE` | spectator |
| `GRAY_CONCRETE` | none |

Set `silent-role-assignment: true` to make pads assign quietly (no
message, no sound). The block list lives under `blocks:`.

## Next Steps

This section is split into focused pages:

- [Cells & Spawns](world-engine/cells-spawns.md): the game world, cell
  size, spawn selection, and the lobby.
- [Pre-generating Cells](world-engine/pregenerating-cells.md): console
  commands that run when a new cell is allocated.
- [Borders](world-engine/borders.md): the cell world border and the
  shrinking start border.
- [Troubleshooting](world-engine/troubleshooting.md): common questions
  and fixes.

For running several matches at once, see
[Concurrent Matches](../multi-instance.md): lobbies, per-match end
dimensions, portal routing, and the ready-cell buffer.

## QA Checklist

1. Delete the lobby world folder, run `tpto lobbyworld` twice, and
   confirm the world is named `jmh_lobby` with the `DEFAULT` preset.
2. Delete the lobby world folder, run
   `tpto lobbyworld @s EMPTY` twice, and confirm the `EMPTY`
   schematic pasted instead.
3. Run `tpto lobbyworld @s BOGUS` and confirm the invalid-preset
   error names the valid presets.
4. Confirm `world-engine.lobby-presets` exists only in the bundled
   `Core/dev.yml`, not in `/manhunt config` or `config.yml`.
