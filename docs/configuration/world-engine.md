# World Engine

> For: everyone setting up the plugin.

The **world engine** gives every match its own fresh map and cleans up
afterwards. No manual resets, no leftover damage. Matches run at the same
time, each in its own isolated part of the world.

## Quick Start

Run `/manhunt` and click the green button. That turns the world engine on
and builds your lobby. Matches work right away. Restart once when you can:
the stronghold spread needs it.

Prefer typing? This does the same thing:

```text
/manhunt config world-engine enabled true
```

Or set it in `config.yml`:

```yaml
world-engine:
  enabled: true
```

That is it. Matches now reset themselves. The green button already built
your lobby too, so go run your first match:
see [Lobby System](../../lobby-quick-start.md).

> **Tip:** On smaller servers, install
> [Chunky](https://modrinth.com/plugin/chunky) and
> [hook it up](world-engine/pregenerating-cells.md) so maps load faster.

## Next Steps

- [Cells & Spawns](world-engine/cells-spawns.md): game world, cell size,
  spawn selection
- [Pre-generating Cells](world-engine/pregenerating-cells.md): commands
  that run when a new cell is allocated
- [Borders](world-engine/borders.md): cell border and shrinking start
  border
- [Troubleshooting](world-engine/troubleshooting.md): common questions
  and fixes
- [Lobby System](../../lobby-quick-start.md): lobbies, presets, role pads
- [Concurrent Matches](../multi-instance.md): running several matches at
  once
