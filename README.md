![JManhunt banner](banner-1280x640.png)

# JManhunt

JManhunt is a deeply configurable Paper plugin for 26.2+ Manhunts. It
comes with a lean world reset engine, placeholders, statistics,
and a variety of built-in actions and modifiers.

Download: https://modrinth.com/plugin/jmanhunt

Contribute: https://github.com/jruk8/JManhunt

## Why JManhunt?

Compared to other plugins, JManhunt excels at configurability.

### Defaults
Almost everything in this plugin is configurable, but you don't have to
touch a single YAML file to get started. The default settings work out of
the box for anyone who just wants to start playing.

### Deep Configurability
Toggle built-in mechanics on or off, or create entirely new gameplay 
through custom modifiers. The plugin is designed so that anyone comfortable 
with vanilla Minecraft commands can figure out how to create custom 
modifiers without much of a learning curve.

If a feature you need doesn't exist yet, contributions are welcome on our
[GitHub page](https://github.com/jruk8/JManhunt). We're intentionally
keeping this plugin lightweight and modular, so please keep that philosophy
in mind when contributing.

## Getting started

1. Assign at least one hunter and one speedrunner:

   ```text
   /manhunt setplayer <selector> hunter
   /manhunt setplayer <selector> speedrunner
   ```

   Selectors such as `@a`, `@p`, and `@a[distance=..10]` are supported.
2. Start the match with `/manhunt start`.
3. Check the teams at any time with `/manhunt status`.
4. The match ends when all speedrunners have died, or manually through
   `/manhunt end`.

Players need `jmanhunt.hunter` or `jmanhunt.speedrunner` to receive the
corresponding role. Both permissions are granted by default. The `/manhunt` 
is also accessible through the `mh` alias.

After playing a few matches, check out the built-in settings and custom
modifiers to enhance your experience.

## Commands

| Command                                           | What it does | Permission |
|---------------------------------------------------| --- | --- |
| `/manhunt`                                        | Shows the current teams and match status. | `jmanhunt.command.status` |
| `/manhunt help`                                   | Shows the in-game command list. | `jmanhunt.command.help` |
| `/manhunt setplayer <selector> <role>`            | Assigns `hunter`, `speedrunner`, or `none`. | `jmanhunt.command.setplayer` |
| `/manhunt start`                                  | Starts a match. | `jmanhunt.command.start` |
| `/manhunt end`                                    | Ends the active match; hunters win. | `jmanhunt.command.end` |
| `/manhunt modifiers [setting] [true\|false]`      | Lists, views, or changes built-in actions and custom modifiers. | `jmanhunt.command.modifiers` |
| `/manhunt worldengine setlobby [x,y,z,yaw,pitch]` | Sets the world-engine lobby position. | `jmanhunt.command.worldengine` |
| `/manhunt worldengine lobby [selector]`           | Teleports the sender or selected players to the lobby. | `jmanhunt.command.worldengine` |
| `/manhunt reload`                                 | Reloads `config.yml` and `messages.yml`. | `jmanhunt.command.reload` |

## Custom modifiers

Custom modifiers are named command bundles in `config.yml` under
`custom-modifiers`. They are disabled by default. A modifier can run commands
when a match starts, on a recurring interval during the match, and when it
ends — either from the console or once for each participating player.

To enable a modifier, use its configuration name:

```text
/manhunt modifiers custom-modifiers.everyone-gets-beef true
```

The example modifier in the default config gives players food and applies
different commands to hunters and speedrunners. `perma-night` is another
example. You can also toggle a modifier by changing its `enabled` value in
`config.yml`, then running `/manhunt reload`.

When creating a modifier, copy the structure of an existing one. Currently
only manual YAML file editing is supported for creation.

### Placeholders

Commands can use these placeholders:

| Placeholder | Replaced with |
| --- | --- |
| `<p>` | The participating player's name. Use this in player and role commands. |
| `<random-mob>` | A random spawnable living entity type (e.g. `zombie`, `creeper`). A new roll is made for each command execution. |
| `<random-item>` | A random item material (e.g. `diamond_sword`, `bread`). A new roll is made for each command execution. |

### Relative coordinates

In player and role commands (`player`, `hunter`, `speedrunner`), tildes (`~`)
are automatically resolved to the participating player's position. For
example, `summon zombie ~ ~ ~` becomes `summon zombie 10.5 64 -20.2` if the
player is at `(10.5, 64.0, -20.2)`. Offsets like `~5` and `~-3` are supported.

All commands are dispatched as the console sender, so there are no permission
issues — the tilde resolution is handled by the plugin before dispatch. Local
coordinates (`^`) are not supported.

### Command lists

The available command lists are:

- `commands.player`: runs at the start for every participating player.
- `commands.hunter`: runs at the start for every hunter.
- `commands.speedrunner`: runs at the start for every speedrunner.
- `commands.console`: runs at the start from the console.
- `commands.console-cleanup`: runs on the console when the match finishes.
- `commands.player-cleanup`: runs on every player when the match finishes.

### Run timing

`runs-on` is a list of events that trigger the modifier's commands. Available
values:

| Value | Trigger |
| --- | --- |
| `ON_START` | Once when the match starts |
| `INTERVAL` | On a fixed interval that starts counting when the game begins |
| `ON_EVERY_KILL` | When a participating player kills any entity (mobs included) |
| `ON_PLAYER_KILL` | When a participating player kills another player |
| `ON_HUNTER_KILL` | When a hunter kills a player |
| `ON_SPEEDRUNNER_KILL` | When a speedrunner kills a player |
| `ON_FIRST_ENTER_NETHER` | When a participating player first enters the Nether |
| `ON_FIRST_ENTER_END` | When a participating player first enters the End |
| `ON_EVERY_ADVANCEMENT` | When a participating player earns any advancement |

Event-based modifiers run their `player`/`hunter`/`speedrunner` commands only
for the specific player involved in the event.

```yaml
custom-modifiers:
  random-mob-spawner:
    enabled: false
    runs-on:
      - INTERVAL
    interval-settings:
      interval: 60          # seconds between runs
    commands:
      speedrunner:
        - "summon <random-mob> ~ ~ ~"
```

Interval modifiers start counting when the game actually begins (i.e., when a
speedrunner hits a hunter, or when the match force-starts), not when `/manhunt
start` is run. They are automatically canceled when the match ends.

### Example

```yaml
custom-modifiers:
  starter-kit:
    enabled: false
    commands:
      player:
        - "give <p> cooked_beef 8"
      hunter: []
      speedrunner: []
      console: []
      console-cleanup: []
      player-cleanup: []
```

## Challenges

Built-in challenges can be toggled in-game with `/manhunt modifiers`:

| Challenge | Effect |
| --- | --- |
| `challenges.no-jump` | Players cannot jump for the duration of the match. |
| `challenges.one-heart` | All participating players have only one heart (2 health points). |
| `challenges.lucky-blocks` | Breaking the configured block drops a random outcome from `settings/lucky-blocks.yml` instead of the block itself. |

The lucky-blocks challenge uses `challenges.lucky-blocks.block-definition`
(default `gold_block`) to select which block is intercepted. The outcome table
in `settings/lucky-blocks.yml` supports `ITEM`, `NONE`, and `COMMAND` outcome
types with weighted random selection. Commands support the same placeholders
(`<p>`, `<random-mob>`, `<random-item>`) and tilde resolution as custom
modifiers, with `relative-to` choosing whether tildes resolve to the broken
block or the player.

## Settings
The plugin provides settings that modify the game flow.
This includes things like:

- starting the game only when speedrunner hits a hunter
- setting participants to adventure mode during the pre-start window
- autostart when enough players join
- custom bartering loot tables for higher ender pearl pulls
- compass tracking settings under `settings.compass`
- optional grid-based world-engine runs with persistent spiral cell assignment,
  stronghold random spread, and automatic End resets between matches
- optional nether-structures datapack that boosts fortress and bastion spawn
  frequency (requires a server restart)

and a lot more!

## World Reset Engine

The plugin provides a world reset engine that can be used to reset the match
area. It works through partitioning the world into configurable cells and
creating fresh matches on unused ones. This allows for practically infinite
matches to run on just one world, which is:

- a clean solution to compared to manually regenerating a world
- more performant than world resets other plugins offer
- far less likely to break on updates.
  
The world reset engine can be configured in the `config.yml` file.

### Setup Guide

1. Enable the `settings.world-engine.enabled` option in `config.yml`.
2. Set the `settings.world-engine.target-world` option to the name of the world
you want to reset. The default should work for most servers.
3. Set the `settings.world-engine.lobby-teleport` option to the location you
want players to be teleported to after the world is reset. The default should
work for most servers.
4. Restart the server to apply the changes.
5. Check `/datapack list` and make sure the `jmanhunt_world_engine` datapack 
is enabled. If not, enable it with `/datapack enable jmanhunt_world_engine`.
If it's still red, restart again.
6. Test the world engine by starting a match and checking if the teleportation
and cell algorithm works correctly.

### Troubleshooting
- Q: The datapack stays red and won't enable no matter what I do.
- A: Regenerate `JManhunt/settings/world-engine` by deleting it and restarting the 
server. Open an issue on GitHub with the relevant exception in server logs.


- Q: Strongholds are generating in non-vanilla places.
- A: This is a deliberate feature, not a bug. The world engine uses a custom
stronghold spread algorithm. You cannot switch to the vanilla stronghold spread
algorithm because this would make certain cells unbeatable after a certain
point.


- Q: I want to disable the world engine.
- A: Set `settings.world-engine.enabled` to `false` in `config.yml` and disable the
`jmanhunt_world_engine` datapack with `/datapack disable jmanhunt_world_engine`.


- Q: Will this work in [specific Minecraft version]?
- A: This feature is tested to work on 26.2. If the plugin is marked to support
a newer version and you encounter issues, please open an issue on GitHub with 
the relevant exception in server logs.

## Configuration

The plugin creates `config.yml` in its data folder. It includes match
behavior, default game actions, command bundles, custom modifiers, compass
tracking settings under `settings.compass`, end-screen statistics, sounds,
text formatting, and optional PlaceholderAPI settings. Use `/manhunt modifiers`
to browse and change the boolean built-in actions and modifier switches
in-game.
The `settings.world-engine` section controls grid cell size, spread radius,
target world, lobby teleport location, and the optional nether-structures
datapack for the grid-based world engine.

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

The hunter compass cannot be dropped, stored in another container, moved by a
hopper, or transferred through inventory interactions. It is restored after a
hunter respawns.

## Statistics and PlaceholderAPI

Career statistics are enabled by default and stored in `jmanhunt.db` using SQLite.
The same database also stores the persistent world-engine spiral cell index.
For statistics shared between servers, set `database.type` to `postgresql` and
configure `database.postgresql` in `config.yml`.

With PlaceholderAPI installed, JManhunt provides placeholders such as
`%jmanhunt_total_kills%` and `%jmanhunt_formatted_time_as_hunter%`. The complete
list and formatting options are documented in `placeholders.yml`.

## Build

Java 25 is required. Gradle can use a locally installed matching toolchain.

```shell
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The plugin jar is written to `build/libs/`. GitHub Actions builds every push
and pull request. Pushing a `v*` tag creates a GitHub release and publishes to
Modrinth using the repository's `MODRINTH_ID`, `MODRINTH_GAME_VERSIONS`,
`MODRINTH_LOADERS`, and `MODRINTH_TOKEN` settings.

See `CONTRIBUTING.md` for contributor setup.

© 2026 jruk8. Licensed under GNU GPLv3.
