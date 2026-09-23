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
  spawnpoint-algorithm:
    enabled: true
    max-retries: 5
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

`spawnpoint-algorithm`, when enabled, validates every player spawn: it
lands below tree leaves and requires an air gap at the feet and head
blocks. Transparent, non-solid blocks like grass and torches count as
air; pressure plates do not. Ocean and lava cells are skipped when
fetching. Invalid spawns retry with fresh random offsets up to
`max-retries` times (minimum 0), then fall back to the plain spread.
This fixes bad spawns, but may cause server lag if many checks are
required. Turn the algorithm off for plain highest-block spawns.

## Lobby Teleports & Bounds

Lobby teleport points and boundary boxes live in
`settings/world-engine/lobby-config.yml`, generated with defaults on first
load and reloaded with `/manhunt reload`. They are keyed by lobby id, one
entry per lobby:

```yaml
lobbies:
  '0':
    lobbytp:
      x: 0.0
      y: 65.0
      z: 0.0
      yaw: 0.0
      pitch: 0.0
    bounds:
      pos1: null
      pos2: null
```

Set a teleport by standing in the lobby world and running
`/manhunt worldengine lobbyconfig setlobbytp <lobby-id>`, or pass coords
directly (`setlobbytp <lobby-id> <x y z yaw pitch>`, which also works
from the console). The world is never stored: teleports always land in
the configured lobby world. When a lobby has no teleport of its own,
players fall back to the lowest lobby id that has one (noted in debug
output). When no teleport exists anywhere, or the lobby world itself is
missing, players are told no lobby exists and to contact an
administrator. This file is not editable through
`/manhunt configuration`.

The same file holds lobby upkeep under `care`: arrivals are healed
and fed at once, and everyone inside is topped up every `interval`
seconds. Both toggles default to on:

```yaml
care:
  heal:
    enabled: true
  saturate:
    enabled: true
  interval: 15
```

Boundary boxes auto-join walkers: a player who steps into a lobby's box
while in the lobby world joins that lobby with role `none`, the same as
`/manhunt lobby join` with role `none` (no teleport, since they are
already there). Players already in that lobby, and players in a running
match, are left alone. Where boxes overlap, the box whose midpoint is
nearest wins. Record two opposite feet-block corners with
`/manhunt worldengine lobbyconfig pos1|pos2`, then store them with
`/manhunt worldengine lobbyconfig setbounds <lobby-id>` (tab completion
suggests the next id without bounds; overwriting existing bounds needs
the command run twice within 10 seconds). Remove a whole entry with
`/manhunt worldengine lobbyconfig deletelobby <lobby-id>`, also run
twice to confirm. This only deletes the stored entry, never the live
lobby or its players.

The fastest way to get a lobby is `/manhunt worldengine tpto lobbyworld`,
run twice: it generates the `jmh-lobby` void world (filled by your lobby
preset) and points lobby 0 at the spawn automatically.
Set `world-engine.lobby-world-name` to use your own world instead.

Players who fall into the void in the lobby world pop back at their lobby
teleport (or lobby 0 when theirs is unset) instead of dying. This never
applies in the game world, and can be turned off with
`world-engine.lobby-world-void-rescue`.
