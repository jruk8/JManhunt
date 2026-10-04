---
icon: material/lightning-bolt
---

# Modifier Behaviors

> For: intermediate authors wiring triggers, plus advanced authors tuning timing.

## Enabling a Modifier

In its file, the `enabled` flag decides whether the
bundle runs at all. Toggle a bundle in-game with:

```text
/manhunt modifiers setmod everyone-gets-beef true
```

You can also flip `enabled` in the file directly, then run
`/manhunt reload`. Create new ones with `/manhunt modifiers create`,
through the admin GUI editor, or by copying an existing entry.

Mid-match toggles apply live, but each modifier fires its start
commands at most once and runs its cleanup commands at most once per
match, so repeated toggling never duplicates rewards. Repeat toggles
are skipped with a console warning.

## Behaviors

Under the file's `behavior:` map, a modifier holds one behavior per
index (`0`, `1`, ...). Each behavior has its own triggers, options,
and command lists, and every behavior of an enabled modifier fires on
its own triggers. Indexes need not be contiguous; a modifier with no
behaviors does nothing:

`mods/modifiers/everyone-gets-beef.yml`:

```yaml
enabled: false
behavior:
  0:
    runs-on:
      - ON_START
    commands:
      player:
        - "give <p> minecraft:cooked_beef 8"
  1:
    runs-on:
      - INTERVAL
    options:
      interval-settings:
        interval: 60
    commands:
      player:
        - "give <p> minecraft:cooked_beef 1"
```

All trigger, option, and command paths below live under
`behavior.<index>`.

## Run Timing

The commands (other than cleanup) run on these triggers:

| Value | Trigger |
| --- | --- |
| `ON_START` | Once when the match starts (runs for all participants) |
| `INTERVAL` | On a fixed interval that starts counting when the game begins |
| `ON_MOB_KILLED` | When a participating player kills a mob or other non-player entity (never players) |
| `ON_PLAYER_KILLS` | When a participating player kills another player |
| `ON_HUNTER_KILLS` | When a hunter kills a player |
| `ON_SPEEDRUNNER_KILLS` | When a speedrunner kills a player |
| `ON_NETHER_ENTER` | When a participating player enters the Nether (once per player per match) |
| `ON_END_ENTER` | When a participating player enters the End (once per player per match) |
| `ON_FIRST_NETHER_ENTER` | When the first participating player enters the Nether (once per match) |
| `ON_FIRST_END_ENTER` | When the first participating player enters the End (once per match) |
| `ON_EVERY_ADVANCEMENT` | When a participating player earns any advancement (recipe book unlocks excluded) |
| `ON_RESPAWN` | When a player respawns (only the executing player) |
| `ON_SPEEDRUNNER_RESPAWN` | When a speedrunner respawns (only the executing player) |
| `ON_HUNTER_RESPAWN` | When a hunter respawns (only the executing player) |
| `ON_DAMAGE_TAKEN` | When a player takes damage from any source (runs for the damaged player only) |
| `ON_DEATH` | When a participant dies (runs for the dead player only; `<args:0>` is the exact dead-player name, `<args:1>` is the exact killer name or null) |

If `runs-on` is omitted, the modifier defaults to `ON_START`.
To count every kill, enable both `ON_MOB_KILLED` and `ON_PLAYER_KILLS`.

Except for `ON_START`, every event trigger runs the `player`, `hunter`, and
`speedrunner` commands only for the specific player involved in the event.
`ON_START` and `INTERVAL` run for all participating players instead.

### Start Timing

An `ON_START` modifier can wait out the pre-start window before
running:

`mods/modifiers/hunter-post-start-speed.yml`:

```yaml
behavior:
  0:
    on-start:
      # BEFORE runs at /manhunt start; AFTER waits until the speedrunner
      # first hits a hunter (or the match force-starts). Defaults to BEFORE.
      pre-start-order: AFTER
```

This only applies when `runs-on` contains `ON_START` or is omitted (which
defaults to `ON_START`). When `start-on-speedrunner-damage` is disabled
there is no pre-start window, so both settings run at match start.

### Success Chance

Make the modifier run only sometimes:

`mods/modifiers/gear-dice.yml`:

```yaml
behavior:
  0:
    options:
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

### Command Execution

Run a random line from a command list instead of every line:

`mods/modifiers/gear-dice.yml`:

```yaml
behavior:
  0:
    options:
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

### Interval Settings

How often an `INTERVAL` modifier repeats (only applies with
`INTERVAL` in `runs-on`):

`mods/modifiers/random-mob-spawner.yml`:

```yaml
enabled: false
behavior:
  0:
    runs-on:
      - INTERVAL
    options:
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

Each firing skips players who cannot play right now: match watchers,
dead players waiting on a respawn, and players held in spectator mode
(respawn waits and headstart holds). The console list still fires.

### Command Delay

Delay the modifier's commands by a number of ticks after they
trigger:

`mods/modifiers/everyone-gets-beef.yml`:

```yaml
behavior:
  0:
    options:
      delay: 5
```

The delay applies to start, interval, and event triggers, but never to
cleanup commands. Player positions and roles resolve when the delayed commands
fire, not when they trigger. If the match ends before the delay elapses, the
commands are dropped.

## Command Lists

`commands` picks which commands run and for whom. The bundled
`everyone-gets-beef` gives every participant eight steaks on match
start:

`mods/modifiers/everyone-gets-beef.yml`:

```yaml
enabled: false
behavior:
  0:
    commands:
      player:
        # Runs for every participating player (only hunters and speedrunners, not including NONE)
        - "give <p> minecraft:cooked_beef 8"
```

The available lists are:

- `commands.player`: runs for every participating player.
- `commands.hunter`: runs only for hunters.
- `commands.speedrunner`: runs only for speedrunners.
- `commands.console`: runs once from the console on every trigger
  firing, no matter which player or role caused the event.
- `commands.console-cleanup`: runs from the console when the match ends.
- `commands.player-cleanup`: runs for every participating player when the
  match ends.

`<p>` is replaced with the participating player's name. Commands may start
with `/`.

The role lists only run when the executing player actually has that
role: if a hunter enters the Nether and only a `speedrunner` block is
configured, that block does not run. The `console` list has no such
filter: it runs once per trigger firing for every matched event,
regardless of role. Only a `chance` below `1.0` can skip it; see
Success Chance.

## Blacklisted Commands

Some commands are too dangerous for modifiers to dispatch. The
`advanced.misc.interop.blacklisted-modifier-commands` list in `config.yml`
names command roots that never run, no matter which list holds them.

Matching uses the first token of the resolved line with slashes and any
`namespace:` prefix stripped, case-insensitively, so `stop`, `/Stop`, and
`minecraft:stop` all match a `stop` entry. A hit logs an error naming
the command and its source line, then skips the rest of that list. The
match itself keeps running.

Defaults: `op`, `deop`, `stop`, `restart`, `reload`, `luckperms`, `lp`,
`permissions`, `ban`, `kick`, `whitelist`. Empty the list to
disable the blacklist entirely.

## Targeting Sides with Selectors

Manhunt roles mirror to vanilla scoreboard teams (`HUNTER`,
`SPEEDRUNNER`, and `SPECTATOR`), so console commands can aim at a whole
side with the `team` selector argument:

`mods/modifiers/hunter-fear.yml`:

```yaml
enabled: false
behavior:
  0:
    runs-on:
      - INTERVAL
    options:
      interval-settings:
        interval: 30
    commands:
      console:
        - "effect give @a[team=HUNTER] minecraft:darkness 5 0"
```

`@a[team=HUNTER]` only covers hunters in the running match, so it
stays safe when several matches run at once. Membership follows roles
exactly, carries no colors or friendly-fire rules, and `none`/`afk`
players sit in no team. Pair with `player`/`hunter`/`speedrunner` lists
when you need per-player tags like `<p>` or `~` coordinates instead.

## Match-End Cleanup

The `console-cleanup` and `player-cleanup` lists run when the match ends,
which makes them the right place to undo whatever the modifier changed.
The bundled `perma-night`, for example, re-enables daylight when the
match is over:

`mods/modifiers/perma-night.yml`:

```yaml
enabled: false
behavior:
  0:
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

## Relative Coordinates

In player and role commands (`player`, `hunter`, `speedrunner`), tildes (`~`)
are automatically resolved to the participating player's position. For
example, `summon zombie ~ ~ ~` becomes `summon zombie 10.5 64 -20.2` if the
player is at `(10.5, 64.0, -20.2)`. Offsets like `~5` and `~-3` are supported.

All commands are dispatched as the console sender, so there are no permission
issues. The tilde resolution is handled by the plugin before dispatch.
