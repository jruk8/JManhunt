# World Reset Engine

The plugin provides a world reset engine that can be used to reset the match
area. It works through partitioning the world into configurable cells and
creating fresh matches on unused ones. This allows for practically infinite
matches to run on just one world, which is:

- a clean solution compared to manually regenerating a world
- more performant than world resets other plugins offer
- far less likely to break on updates

The world reset engine can be configured in the `config.yml` file.

### Setup Guide

1. Enable the `settings.world-engine.enabled` option in `config.yml`.
2. Set the `settings.world-engine.target-world` option to the name of the
   world you want to reset. The default should work for most servers.
3. Set the `settings.world-engine.lobby-teleport` option to the location you
   want players to be teleported to after the world is reset. The default
   should work for most servers.
4. Restart the server to apply the changes.
5. Check `/datapack list` and make sure the `jmanhunt_world_engine` datapack
   is enabled. If not, enable it with `/datapack enable jmanhunt_world_engine`.
   If it's still red, restart again.
6. Test the world engine by starting a match and checking if the teleportation
   and cell algorithm works correctly.

### On-Fetch-New-Cell Commands

Under `settings.world-engine.on-fetch-new-cell`, you can configure console
commands that run whenever a new cell is allocated for a match. The
placeholders `<cellX>` and `<cellZ>` are replaced with the cell's block
coordinates. This is useful for pre-generating the cell area with
chunk-generation plugins such as Chunky before players teleport in.

```yaml
settings:
  world-engine:
    on-fetch-new-cell:
      - "chunky radius 500"
      - "chunky start"
```

The commands only run when a genuinely new cell is allocated, avoiding
unnecessary regeneration of cells that were already fetched.

### World Border

Under `settings.world-engine.world-border`, you can enable a world border that
confines players to their assigned cell. This prevents players from wandering
into unused or already-used cells.

The `start-border` sub-section provides a smaller initial border that expands
to the full cell size when the game begins. This is only active when both
`world-border.enabled` and `start-on-speedrunner-damage.enabled` are true.

- `start-border.radius`: Initial border radius in blocks. The actual diameter
  used is `max(this, tp-spread-radius + 1) * 2`, ensuring players never spawn
  outside the border. Set to `-1` to use `tp-spread-radius + 1` only.
  Default: `10`
- `start-border.fadeout-time`: Time in seconds for the start border to animate
  expanding to cell size. Set to `0` or `-1` to skip the animation and snap to
  cell size immediately. Default: `5`