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
   generates a clean void world with a small stone platform and teleports
   you onto it. Lobby 0 is pointed at that platform automatically.
3. Build your lobby on and around the platform. Falling off is safe: you pop
   back at the lobby instead of dying.
4. Restart the server. Everything works without a restart, but the stronghold
   generation changes only apply after one.

Prefer your own world instead? Set `world-engine.lobby-world-name` to its
name, reload, and `tpto lobbyworld` takes you there without generating
anything. Hop back to the game world any time with
`/manhunt worldengine tpto gameworld`.

As your next step, it's recommended to set up **chunk pre-generation** for cells. This
may sound complex, but it's really as simple as installing [Chunky](https://modrinth.com/plugin/chunky) or a similar plugin
and [hooking it up](world-engine/pregenerating-cells.md). This takes around 5 minutes
and improves performance on low-to-mid-end servers.

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
