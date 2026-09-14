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
starts and returns them to the lobby when it ends. Enabling it additionally
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
hard-capped at `cell-size / 2`. The [Start Border](#start-border) uses this value
in its size calculation.

## Spawnpoint Algorithm

`use-spawnpoint-algorithm`, when true, uses the spawnpoint algorithm to find
a valid spawn point for each player. This fixes spawning inside oceans or
lava, but may cause server lag if many checks are required.

## Lobby Location

`lobby-location` is the lobby for all participants after game end. Make sure
it is different from the game world. Use `/manhunt worldengine setlobby` to update
it in-game.

# On Fetch New Cell

Under `world-engine.on-fetch-new-cell`, you can configure console commands
that run whenever a new cell is allocated for a match. This
is useful for pre-generating the cell area with chunk-generation plugins
such as Chunky before a game starts.

**Why?** Obviously, one could generate chunks as they're needed. However, this lags the server
because generating chunks in further out regions is expensive. Thus, it's better for the server to 
load chunks and lag before the game than during it.

The placeholders `<cellX>` and `<cellZ>` are replaced with the cell's block coordinates.

```yaml
world-engine:
  on-fetch-new-cell: []
```

Try something like:

```yaml
world-engine:
  on-fetch-new-cell:
    - "chunky world world"              # replace "world" with your game world name
    - "chunky center <cellX> <cellZ>"   # set gen center to the fetched cell's coordinates
    - "chunky radius 150"               # set gen radius to 150 blocks (keep reasonable to avoid lag)
    - "chunky start"                    # start the generation process
    - "chunky confirm"                  # fixes anything that may have prevented the generation
```

The commands run:

i. when a match ends (after the match goes inactive) and

ii. when the autostart countdown begins,

giving chunk-generation plugins time to pre-generate the next cell before
a game starts. They only run once per match intermission, so the cell is
fetched exactly once between matches. The commands only run when the cell
is actually new (i.e. the previous cell was not already regenerated),
avoiding unnecessary regeneration.

# World Border

Under `world-engine.world-border`, you can enable a world border that
confines players to their assigned cell. This prevents players from
wandering into unused or already-used cells.

```yaml
world-engine:
  world-border:
    enabled: true
    damage:
      buffer: 5.0
      amount: 1.0
    start-border:
      enabled: true
      radius: 10
      fadeout-time: 5
```

> **Note:**
> This setting should only be used when the game world is different from the 
> lobby world. Otherwise, lobby players may suffocate when the border resizes.

## Damage

`damage.buffer` is the size of the world border buffer in blocks, after
which the player is dealt damage (matches Minecraft's world border
damage buffer command.)

`damage.amount` is the damage dealt per block per second when outside the
buffer.

## Start Border

The `start-border` sub-section provides a smaller initial border that
expands to the full cell size when the game begins. This is only active when
both `world-border.enabled` and
`settings.start-on-speedrunner-damage.enabled` are true.

- `start-border.radius`: Initial border radius in blocks. The actual diameter
  used is `max(this, tp-spread-radius + 1) * 2`, ensuring players never spawn
  outside the border. Set to `-1` to use `tp-spread-radius + 1` only.
  Default: `10`
- `start-border.fadeout-time`: Time in seconds for the start border to animate
  expanding to cell size. Set to `0` or `-1` to skip the animation and snap to
  cell size immediately. Default: `5`

# Troubleshooting

**Q: The datapack stays red and won't enable no matter what I do.**

A: Regenerate `JManhunt/settings/world-engine` by deleting it and restarting
the server. Open an issue on GitHub with the relevant exception in server
logs.

**Q: Strongholds are generating in non-vanilla places.**

A: This is a deliberate feature, not a bug. The world engine uses a custom
stronghold spread algorithm. You cannot switch to the vanilla stronghold
spread algorithm because this would make certain cells unbeatable after a
certain point due to the Vanilla 128-per-world limit.

**Q: I want to disable the world engine.**

A: Set `world-engine.enabled` to `false` in `config.yml` and disable
the `jmanhunt_world_engine` datapack with
`/datapack disable jmanhunt_world_engine`.

**Q: Will this work in [specific Minecraft version]?**

A: This feature is tested to work on 26.2. If the plugin is marked to support
a newer version and you encounter issues, please open an issue on GitHub with
the relevant exception in server logs.
