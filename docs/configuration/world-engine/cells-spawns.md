# Cell & Spawns

Under `world-engine`, you can configure the game world, the size of each
spiral cell, where players spawn inside a cell, and where they return after
a match.

```yaml
world-engine:
  enabled: false
  world-name: world
  cell-size: 10000
  tp-spread-radius: 5
  use-spawnpoint-algorithm: true
  lobby-locations:
    "0":
      world: world
      x: 0.5
      y: 100.0
      z: 0.5
      yaw: 0.0
      pitch: 0.0
```

When `enabled`, teleports participants to a fresh cell when a match
starts and returns them to the lobby when it ends. Players with role `none`
travel to the cell center as spectators when
`settings.roles.turn-nones-spectator` is enabled, and return to the lobby
with everyone else. Enabling it additionally
modifies stronghold generation to bypass the 128-per-world limit and spread
strongholds around like normal structures. You can tweak the rates in
`settings/world-engine/strongholds.json` (defaults imitate average distance
in a normal world from world origin 0,0).

`world-name` is the name of the world that is partitioned into cells. Don't modify
unless you have a particular reason to not use the default overworld.

## Cell Size

`cell-size` is the size of one spiral cell in blocks. It is hard-capped at
50,000. Too low values may cause issues. In general, don't go below 5,000.

Do not change `cell-size` after cells have already been generated. Every
cell size lays its own grid over the world, so cells generated under one
size can overlap and clip into cells generated under another.

## Cell Index

The engine hands out cells from a persistent counter. You can inspect it
with `/manhunt worldengine cellindex get` and overwrite it with
`/manhunt worldengine cellindex set <value>`. Set values are clamped
between 0 and the addressable grid for the current cell size. If the
counter ever grows past that grid, it restarts at zero on the next fetch
with a console warning telling you to reset the world manually.

## Spawn Spread

`tp-spread-radius` is the radius from each cell center used when selecting
player spawn points. A reasonable value is between 5 and 15. It is
hard-capped at `cell-size / 2`. The [Start Border](borders.md#start-border) uses this value
in its size calculation.

## Spawnpoint Algorithm

`use-spawnpoint-algorithm`, when true, uses the spawnpoint algorithm to find
a valid spawn point for each player. This fixes spawning inside oceans or
lava, but may cause server lag if many checks are required.

## Lobby Locations

`lobby-locations` maps each lobby id to the lobby its players return to after
a match ends. Make sure lobby worlds are different from the game world. Use
`/manhunt worldengine setlobby` for lobby 0, or stand in place and run
`/manhunt worldengine setlobbytp <lobby-id>` for any other lobby, to update
locations in-game.

The fastest way to get a lobby is `/manhunt worldengine tpto lobbyworld`,
run twice: it generates the `jmh-lobby` void world (filled by your lobby
preset) and points lobby 0 at the spawn automatically.
Set `world-engine.lobby-world-name` to use your own world instead.

Players who fall into the void in the lobby world pop back at their lobby
location (or lobby 0 when theirs is unset) instead of dying. This never
applies in the game world, and can be turned off with
`world-engine.lobby-world-void-rescue`.
