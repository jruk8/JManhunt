---
icon: material/grid
---

# Cell & Spawns

> For: intermediate admins tuning maps, plus advanced users sizing cells.

The game world, the size of each spiral cell, where players spawn inside
a cell, and where they return after a match:

```yaml
world-engine:
  enabled: false
  world-name: world
  cell-size: 10000
  tp-spread-radius: 5
  spawnpoint-algorithm:
    enabled: true
    max-retries: 8
    y-tolerance: 7
    spawn-close-to-structure:
      enabled: false
      structures: [VILLAGE, TEMPLE, SHIPWRECK, RUINED_PORTAL]
      attempts: 3
      max-distance: 125
```

When `enabled`, matches teleport participants to a fresh cell on start
and return them to the lobby at the end. Spectators travel along and
land on the shared spectator spawn. Enabling it also spreads
strongholds past the vanilla
128-per-world limit; tweak the rates in
`settings/world-engine/strongholds.json`.

`world-name` is the world partitioned into cells. Leave it on the
default overworld unless you have a reason not to.

## Cell Size

`cell-size` is the size of one spiral cell in blocks, hard-capped at
50,000. Do not go below 5,000, and do not change it after cells have
already been generated: every size lays its own grid, so cells from two
sizes can overlap and clip into each other.

## Cell Index

The engine hands out cells from a persistent counter. Inspect it with
`/manhunt worldengine cellindex get` and overwrite it with
`/manhunt worldengine cellindex set <value>`. Set values clamp to the
addressable grid; if the counter ever grows past it, it restarts at
zero on the next fetch with a console warning telling you to reset the
world manually.

## Spawn Spread

`tp-spread-radius` is the radius around each cell center used when
picking player spawns. Keep it between 5 and 15; it hard-caps at
`cell-size / 2`. The [Start Border](borders.md#start-border) uses this
value in its size calculation.

Mid-match joiners scatter the same way: into the match cell when the
engine runs it, or around the recorded start center when it does not.
Joiners also arrive vulnerable: any leftover game-end invulnerability
is cleared on entry, so released holds play fair.

## Spawnpoint Algorithm

Validates every spawn: below tree leaves, with air at the feet and head
blocks (grass and torches count as air; pressure plates do not). Water,
lava, and powder snow are never picked, and ocean and lava cells are
skipped when fetching.

Spawns are height-leveled around the median roll within `y-tolerance`,
re-rolling up to `max-retries` times. Turn the algorithm off for plain
highest-block spawns.

### Spawn Close to Structure

Keeps a cell only when a listed structure sits within `max-distance`
(50 to 200, default 125) blocks of it. Each fetch runs up to `attempts`
lookups (1 to 5, default 3); on exhaustion the last passing spawn is
kept. Allowed words:

- `VILLAGE`: any village type
- `TEMPLE`: desert pyramid or jungle pyramid
- `SHIPWRECK`: ocean or beached
- `RUINED_PORTAL`: any ruined portal variant
- `BURIED_TREASURE`
- `MINESHAFT`: regular and mesa
- `OCEAN_MONUMENT`
- `MANSION`
- `PILLAGER_OUTPOST`

Unknown words are skipped with a warning. Keep the list focused: each
lookup costs performance on cell fetch. Pair this with the
[overworld structure boosts](../settings/game-boosts.md).

## Lobby Teleports & Bounds

Lobby teleport points, boundary boxes, and upkeep live on their own
page now: see
[Multi-Lobby Boundaries](../../play/lobby-system/boundaries.md).

The fastest way to get a lobby is `/manhunt worldengine tpto lobbyworld`,
run twice: it generates the `jmh_lobby` void world (filled by the
`DEFAULT` preset, or the preset you name) and points lobby 0 at the
spawn automatically.
Set `advanced.lobbies.lobby-world-name` to use your own world instead.
