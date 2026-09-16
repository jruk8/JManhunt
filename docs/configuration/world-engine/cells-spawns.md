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
  lobby-location:
    world: world
    x: 0.5
    y: 100.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0
```

`enabled`, when true, teleports participants to a fresh cell when a match
starts and returns them to the lobby when it ends. Players with role `none`
travel to the cell center as spectators when
`settings.roles.none-gamemode-spectator` is enabled, and return to the lobby
with everyone else. Enabling it additionally
modifies stronghold generation to bypass the 128-per-world limit and spread
strongholds around like normal structures. You can tweak the rates in
`settings/world-engine/strongholds.json` (defaults imitate average distance
in a normal world from world origin 0,0).

`world-name` is the name of the world that is partitioned into cells. Make
sure it is different from the lobby world. It is recommended to use something
like **Multiverse-Core** to manage both worlds. (test server `play.alttari.games`
uses Multiverse-Core)

## Cell Size

`cell-size` is the size of one spiral cell in blocks. It is hard-capped at
50,000. Too low values may cause issues. In general, don't go below 5,000.

## Spawn Spread

`tp-spread-radius` is the radius from each cell center used when selecting
player spawn points. A reasonable value is between 5 and 15. It is
hard-capped at `cell-size / 2`. The [Start Border](borders.md#start-border) uses this value
in its size calculation.

## Spawnpoint Algorithm

`use-spawnpoint-algorithm`, when true, uses the spawnpoint algorithm to find
a valid spawn point for each player. This fixes spawning inside oceans or
lava, but may cause server lag if many checks are required.

## Lobby Location

`lobby-location` is the lobby for all participants after game end. Make sure
it is different from the game world. Use `/manhunt worldengine setlobby` to update
it in-game.
