# Modifiers

**Modifiers** are named command bundles you define in `config.yml`
under `modifiers`. They are disabled by default. A modifier can run
commands when a match starts, on a recurring interval during the match, when
specific game events happen, and when the match ends, either from the console
or once for each participating player.

All command examples are the default config settings. Refer to the latest
version of `config.yml` in the [GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

Settings for modifiers are categorized under `modifiers`:

```yaml
modifiers:
  everyone-gets-beef:
    enabled: false
```

Writing commands by hand is tedious. Use
[mcstacker.net](https://mcstacker.net/) to generate up-to-date commands,
then paste them into your modifier.

# Enabling a Modifier

Under `modifiers.<name>`, the `enabled` flag decides whether the
bundle runs at all. Toggle a bundle in-game with:

```text
/manhunt config modifiers everyone-gets-beef enabled true
```

You can also flip `enabled` in `config.yml` directly, then run
`/manhunt reload`. When creating a modifier, copy the structure of an existing
one. Currently only manual YAML file editing is supported for creation.

# Command Lists

Under `modifiers.<name>.commands`, you can configure which commands run
and for whom. The bundled `everyone-gets-beef` example gives every
participating player eight steaks when the match starts:

```yaml
modifiers:
  everyone-gets-beef:
    enabled: false
    commands:
      player:
        # Runs for every participating player (only hunters and speedrunners, not including NONE)
        - "give <p> minecraft:cooked_beef 8"
```

The available command lists are:

- `commands.player`: runs for every participating player.
- `commands.hunter`: runs only for hunters.
- `commands.speedrunner`: runs only for speedrunners.
- `commands.console`: runs once from the console.
- `commands.console-cleanup`: runs from the console when the match ends.
- `commands.player-cleanup`: runs for every participating player when the
  match ends.

`<p>` is replaced with the participating player's name. Commands may start
with `/`.

The role-specific lists (`hunter`/`speedrunner`) only run when the executing
player actually has that role. For example, if a hunter enters the Nether and
only a `speedrunner` block is configured, that block does not run. Console
commands run in parallel regardless of any player's role.

# Run Timing

Under `modifiers.<name>.runs-on`, you can configure when the commands
(other than cleanup) run. It is a list of any of:

| Value | Trigger |
| --- | --- |
| `ON_START` | Once when the match starts (runs for all participants) |
| `INTERVAL` | On a fixed interval that starts counting when the game begins |
| `ON_EVERY_KILL` | When a participating player kills any entity (mobs included) |
| `ON_PLAYER_KILL` | When a participating player kills another player |
| `ON_HUNTER_KILL` | When a hunter kills a player |
| `ON_SPEEDRUNNER_KILL` | When a speedrunner kills a player |
| `ON_NETHER_ENTER` | When a participating player enters the Nether (once per player per match) |
| `ON_END_ENTER` | When a participating player enters the End (once per player per match) |
| `ON_FIRST_NETHER_ENTER` | When the first participating player enters the Nether (once per match) |
| `ON_FIRST_END_ENTER` | When the first participating player enters the End (once per match) |
| `ON_EVERY_ADVANCEMENT` | When a participating player earns any advancement (recipe book unlocks excluded) |
| `ON_RESPAWN` | When a player respawns (only the executing player) |
| `ON_SPEEDRUNNER_RESPAWN` | When a speedrunner respawns (only the executing player) |
| `ON_HUNTER_RESPAWN` | When a hunter respawns (only the executing player) |

If `runs-on` is omitted, the modifier defaults to `ON_START`.

Except for `ON_START`, every event trigger runs the `player`, `hunter`, and
`speedrunner` commands only for the specific player involved in the event.
`ON_START` and `INTERVAL` run for all participating players instead.

## Start Timing

Under `modifiers.<name>.on-start`, an `ON_START` modifier can wait
out the pre-start window before running:

```yaml
modifiers:
  hunter-post-start-speed:
    on-start:
      # BEFORE runs at /manhunt start; AFTER waits until the speedrunner
      # first hits a hunter (or the match force-starts). Defaults to BEFORE.
      pre-start-order: AFTER
```

This only applies when `runs-on` contains `ON_START` or is omitted (which
defaults to `ON_START`). When `start-on-speedrunner-damage` is disabled
there is no pre-start window, so both settings run at match start.

## Success Chance

Under `modifiers.<name>.success-chance`, you can make the modifier run
only sometimes:

```yaml
modifiers:
  gear-dice:
    success-chance:
      # Chance to run, from 0.0 (never) to 1.0 (always). This is a fraction,
      # not a percent: use 0.5 for 50%. Defaults to 1.0.
      chance: 0.5
      # PER_INVOKE rolls once for everyone; PER_EXECUTOR rolls the console
      # and each player separately. Defaults to PER_INVOKE.
      behavior: PER_EXECUTOR
```

Without this section the modifier always runs. The roll happens on every
trigger, including each interval firing. Cleanup commands always run and are
never rolled.

## Command Execution

Under `modifiers.<name>.commands.execution`, you can run a random line
from a command list instead of every line:

```yaml
modifiers:
  gear-dice:
    commands:
      execution:
        # IN_ORDER runs every line. PICK_RANDOM runs a random few instead.
        # Defaults to IN_ORDER.
        selection: PICK_RANDOM
        pick-random:
          # How many lines to pick. Minimum 1. Defaults to 1.
          count: 1
          # PER_INVOKE picks once for everyone; PER_EXECUTOR picks separately
          # for the console and each player. Defaults to PER_INVOKE.
          behavior: PER_EXECUTOR
```

This applies to each command list on its own. If you ask for more lines than
the list has, the whole list runs. Cleanup lists always run every line so
changes are reliably undone.

## Interval Settings

Under `modifiers.<name>.interval-settings`, you can configure how often
an `INTERVAL` modifier repeats. It only applies when `runs-on` contains
`INTERVAL`:

```yaml
modifiers:
  random-mob-spawner:
    enabled: false
    runs-on:
      - INTERVAL
    interval-settings:
      # Interval duration in seconds.
      interval: 60
      # Random spread in seconds. 60 and 15 means every 45 to 75 seconds.
      # Cannot go above interval. Defaults to 0.
      deviation: 15
      # PER_INVOKE shares one timer; PER_EXECUTOR gives every player and the
      # console their own timer. Defaults to PER_INVOKE.
      behavior: PER_INVOKE
```

Interval modifiers start counting when the game actually begins (when a
speedrunner hits a hunter, or when the match force-starts), not when
`/manhunt start` is run. 
With `PER_EXECUTOR` deviation, each player has their own timer, so players
who join mid-match get timed in as well.

The `interval` value supports decimals and is rounded to the nearest tick (1
tick = 0.05 seconds). Values between `0` and `0.05` execute every tick. Set to
`0` or `0.05` for every-tick execution. Negative values disable the modifier.

## Command Delay

Under `modifiers.<name>.delay`, you can delay the modifier's commands
by a number of ticks after they trigger:

```yaml
modifiers:
  everyone-gets-beef:
    delay: 5
```

The delay applies to start, interval, and event triggers, but never to
cleanup commands. Player positions and roles resolve when the delayed commands
fire, not when they trigger. If the match ends before the delay elapses, the
commands are dropped.

## Placeholders in Commands

Commands can use these tags:

| Tag | Replaced with |
| --- | --- |
| `<p>` | The participating player's name. Use this in player and role commands. |
| `<random-mob>` | A random spawnable living entity type in lowercase (e.g. `zombie`, `creeper`). A new roll is made for each command execution. |
| `<random-item>` | A random item material in lowercase (e.g. `diamond_sword`, `bread`). A new roll is made for each command execution. |
| `<all-players>` | Every participating player in this match. The command runs once per player with their name. Never touches other matches. |
| `<all-players:HUNTER>` | Same, but only hunters. `SPEEDRUNNER` works too. |
| `<random-player>` | One random participating player in this match. |
| `<random-num:4,12>` | A random whole number between 4 and 12. Order does not matter: `<random-num:12,4>` works the same. |
| `<random-pick:coal, "dirt", 'sand'>` | One random item from the list. Items can be bare, `"double-quoted"`, or `'single-quoted'`, and may hold spaces. |

Tags evaluate from the inside out, so they nest. The bundled
`random-start-resources` modifier uses this to hand out a random ore stash:

```yaml
- "give <p> <random-pick:coal <random-num:4,12>, iron_ingot <random-num:3,9>, gold_ingot <random-num:3,9>, diamond <random-num:1,3>>"
```

Each `<random-num>` rolls first, then `<random-pick>` chooses one
`item amount` pair, giving coal (4-12), iron (3-9), gold (3-9), or
diamonds (1-3).

If a `<random-pick>` item is malformed (mixed quotes, two quoted strings in
one item), it is skipped with a console warning and another item is tried.

Raw `@a` and `@r` selectors are converted to `<all-players>` and
`<random-player>` automatically, so old commands stay match-safe. A
`team=` argument on `@a[...]` survives as a role filter; other vanilla
selector arguments are dropped.

## Relative Coordinates

In player and role commands (`player`, `hunter`, `speedrunner`), tildes (`~`)
are automatically resolved to the participating player's position. For
example, `summon zombie ~ ~ ~` becomes `summon zombie 10.5 64 -20.2` if the
player is at `(10.5, 64.0, -20.2)`. Offsets like `~5` and `~-3` are supported.

All commands are dispatched as the console sender, so there are no permission
issues. The tilde resolution is handled by the plugin before dispatch.

# Targeting Sides with Selectors

Manhunt roles mirror to vanilla scoreboard teams (`HUNTER`,
`SPEEDRUNNER`, and `SPECTATOR`), so console commands can aim at a whole
side with the `team` selector argument:

```yaml
modifiers:
  hunter-fear:
    enabled: false
    runs-on:
      - INTERVAL
    interval-settings:
      interval: 30
    commands:
      console:
        - "effect give <all-players:HUNTER> minecraft:darkness 5 0"
```

`<all-players:HUNTER>` only covers hunters in the running match, so it
stays safe when several matches run at once. Membership follows roles
exactly (repaired on every role change and login), carries no colors or
friendly-fire rules, and `none`/`afk` players sit in no team. Pair with
`player`/`hunter`/`speedrunner` lists when you need per-player tags like
`<p>` or `~` coordinates instead.

# Match-End Cleanup

The `console-cleanup` and `player-cleanup` lists run when the match ends,
which makes them the right place to undo whatever the modifier changed. The
bundled `perma-night` modifier, for example, re-enables daylight when the
match is over:

```yaml
modifiers:
  perma-night:
    enabled: false
    commands:
      console:
        # Ran by the console when the match starts.
        - "gamerule advance_time false"
        - "time set midnight"
      console-cleanup:
        # Ran by the console when the match ends.
        - "gamerule advance_time true"
```

Similarly, `speedrunner-health-advantage` resets every participant's max health
in `player-cleanup`, so temporary attribute changes never leak into the next
match or the lobby.

# Example

A minimal modifier that hands every participant a starter kit looks like this:

```yaml
modifiers:
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

The default `config.yml` ships more examples to copy from: `full-iron-kit`,
`speedrunner-health-advantage`, `random-mob-spawner`, `random-item-giver`,
`random-start-resources`, `gear-dice`, `regen-on-kill`, `diamond-on-advancement`,
`fireres-on-nether-enter`, `hunter-start-debuffs` (slowness II plus
weakness I on every hunter at match start), and `hunter-post-start-speed`
(speed for hunters once the game actually begins).
