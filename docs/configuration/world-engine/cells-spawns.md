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
    max-retries: 8
    y-tolerance: 7
    spawn-close-to-structure:
      enabled: false
      structures: [VILLAGE, TEMPLE, SHIPWRECK, RUINED_PORTAL]
      attempts: 3
      max-distance: 125
```

When `enabled`, teleports participants to a fresh cell when a match
starts and returns them to the lobby when it ends. Queued `spectator`
players always travel to the match with everyone else, and players with
role `none` join them when `settings.roles.turn-nones-spectator` is
enabled; both land on the shared spectator spawn pick (top-progression
runner, then hunter, then last-seen spots, then cell center). They return
to the lobby with everyone else. Enabling it additionally
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
air; pressure plates do not. Water, lava, and powder snow are never
picked. Ocean and lava cells are skipped when fetching.

Spawns are also height-leveled: everyone rolls once, the median height
becomes the target, and anyone outside `y-tolerance` blocks of it
re-rolls up to `max-retries` times (minimum 0). Without a fitting roll
the closest candidate wins; with no valid roll at all, the center is
the fallback. This fixes bad spawns, but may cause server lag if many
checks are required. Turn the algorithm off for plain highest-block
spawns.

### Spawn Close to Structure

`spawn-close-to-structure`, when enabled, keeps a raw-passing cell
only when a listed structure sits within `max-distance` blocks of it.
Each cell fetch after the raw terrain checks runs up to `attempts`
structure lookups (1 to 5, default 3): the first hit wins at once,
and on exhaustion the last raw-passing spawn is kept so later fetches
never revisit the skipped cells. `max-distance` accepts 50 to 200
(default 125). Allowed words:

- `VILLAGE`: any village type
- `TEMPLE`: desert pyramid or jungle pyramid
- `SHIPWRECK`: ocean or beached
- `RUINED_PORTAL`: any ruined portal variant
- `BURIED_TREASURE`
- `MINESHAFT`: regular and mesa
- `OCEAN_MONUMENT`
- `MANSION`
- `PILLAGER_OUTPOST`

Unknown words are skipped with a warning. The more entries, the higher
the chance of a hit. Each lookup carries a medium performance cost on
cell fetch, so keep the list focused. Highly recommended: enable the
[overworld structure boosts](../settings/game-boosts.md) alongside
this setting.

## Lobby Teleports & Bounds

Lobby teleport points, boundary boxes, and upkeep live on their own
page now: see
[Multi-Lobby Boundaries](../../../play/lobby-system/boundaries.md).

The fastest way to get a lobby is `/manhunt worldengine tpto lobbyworld`,
run twice: it generates the `jmh_lobby` void world (filled by the
`DEFAULT` preset, or the preset you name) and points lobby 0 at the
spawn automatically.
Set `advanced.lobbies.lobby-world-name` to use your own world instead.
