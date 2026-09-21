# World Engine

**World Engine** partitions a single world into configurable cells and runs
each match on an unused one. This allows for near-infinite matches on
one world file, which is:

- automatic and faster compared to manually deleting a world file
- very performant (new cells preload in the background)
- supports up to half a billion cells (games) on just one world file

All command examples are the default config settings. Refer to the latest 
version of `config.yml` in the [GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

Settings for the world engine are categorized under `world-engine`:

```yaml
world-engine:
  enabled: false
```

## Quick Start (3 minutes)

1. Enable World Engine in-game with `/manhunt configuration world-engine enabled true`.
2. Run `/manhunt worldengine tpto lobbyworld` twice. The first run tells you
   the lobby world does not exist yet; the second run (within 10 seconds)
   generates a clean void world, pastes your lobby schematic (see Lobby
   Presets below), and teleports you onto it. Lobby 0 is pointed at the
   spawn automatically.
3. Walk the pasted lobby: role pads assign roles when stood on. Falling off
   is safe: you pop back at the lobby instead of dying.
4. Restart the server. Everything works without a restart, but the stronghold
   generation changes only apply after one.

Prefer your own world instead? Set `world-engine.lobby-world-name` to its
name, reload, and `tpto lobbyworld` takes you there without generating
anything. Hop back to the game world any time with
`/manhunt worldengine tpto gameworld`. The lobby world name can never
equal the game world name: a clash is refused with a warning until it is
renamed.

As your next step, it's recommended to set up **chunk pre-generation** for cells. This
may sound complex, but it's really as simple as installing [Chunky](https://modrinth.com/plugin/chunky) or a similar plugin
and [hooking it up](world-engine/pregenerating-cells.md). This takes around 5 minutes
and improves performance on low-to-mid-end servers.

New to lobbies? The [Lobby Quick Start](../../lobby-quick-start.md) walks
from zero to a queued lobby in five minutes.

## Lobby Presets

Fresh lobby worlds are filled by `world-engine.lobby-preset`: `EMPTY`,
`DEFAULT`, or `ADVANCED` (default). Nothing is built in code — each
preset pastes its vanilla structure-block `.nbt` from
`JManhunt/settings/world-engine/lobby-schematics/` with the structure's
midpoint at 0,64,0, then runs its console commands (meant for lobby
teleports; `{world}` and `{preset}` are substituted) before the first
teleport lands. A missing schematic warns in the console and leaves
void, with a bare spawn at y=65 as the fallback.

To regenerate with another preset (or an updated `.nbt`): stop the
server, delete the lobby world folder, set `lobby-preset`, start up,
and run `tpto lobbyworld` twice. Presets only apply on fresh
generation — existing worlds are never touched.

Authors editing the presets themselves can build in-game and capture the
result with the [developer schematic tools](../../dev-tools.md).

## Role Pads

Role pads are lobby-world blocks that assign roles when stood on: step
inside a pad block's XZ space, at most 4 blocks above it, and you take
its role with `setplayer` semantics (queue caps and the mid-match
policy apply; AFK needs no confirmation since standing is consent).
Pads only work in the lobby world, stay silent when caps block them,
and do nothing when your role already matches. Defaults under
`world-engine.role-pads`:

| Block | Role |
| --- | --- |
| `LIME_CONCRETE` | speedrunner |
| `RED_CONCRETE` | hunter |
| `YELLOW_CONCRETE` | afk |
| `LIGHT_GRAY_CONCRETE` | spectator |
| `GRAY_CONCRETE` | none |

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
