# World Engine

**World Engine** partitions a single world into configurable cells and runs
each match on an unused one. This allows for practically infinite matches on
just one world, which is:

- a clean solution compared to manually regenerating a world
- more performant than the world resets other plugins offer
- far less likely to break on updates

All command examples are the default config settings. Some defaults may be outdated
(typically not), in
which case you should refer to the latest version of `config.yml` in the
[GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

Settings for the world engine are categorized under `world-engine`:

```yaml
world-engine:
  enabled: false
```

This section is split into focused pages:

- [Cells & Spawns](world-engine/cells-spawns.md): the game world, cell
  size, spawn selection, and the lobby.
- [Pre-generating Cells](world-engine/pregenerating-cells.md): console
  commands that run when a new cell is allocated.
- [Borders](world-engine/borders.md): the cell world border and the
  shrinking start border.
- [Troubleshooting](world-engine/troubleshooting.md): common questions
  and fixes.
