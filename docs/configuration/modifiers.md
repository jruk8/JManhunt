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

Under `modifiers.<name>.options.success-chance`, you can make the modifier
run only sometimes:

```yaml
modifiers:
  gear-dice:
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

## Command Execution

Under `modifiers.<name>.options.execution`, you can run a random line
from a command list instead of every line:

```yaml
modifiers:
  gear-dice:
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

## Interval Settings

Under `modifiers.<name>.options.interval-settings`, you can configure how
often an `INTERVAL` modifier repeats. It only applies when `runs-on`
contains `INTERVAL`:

```yaml
modifiers:
  random-mob-spawner:
    enabled: false
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

## Command Delay

Under `modifiers.<name>.options.delay`, you can delay the modifier's
commands by a number of ticks after they trigger:

```yaml
modifiers:
  everyone-gets-beef:
    options:
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
| `<random-mob>` | A random spawnable living entity type in lowercase (e.g. `zombie`, `creeper`). Under pick-random `PER_INVOKE` one mob is rolled per activation for everyone; under `PER_EXECUTOR` every executor rolls their own. |
| `<random-item>` | A random item material in lowercase (e.g. `diamond_sword`, `bread`). Same scope rule as `<random-mob>`. |
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
selector arguments are dropped. `@p` and `@s` convert to `<p>` the
same way, resolving to the executing player.

## Extended Tags

Besides the placeholders above, commands understand a few computing
tags. They nest inside each other and inside the basic tags, and the
creator editor validates them as you type:

| Tag | Meaning |
| --- | --- |
| `<if:"7 <= 5","yes","no">` | `yes` when the condition holds, else `no` (the else branch may be omitted). |
| `<min:8,3>` | The smaller number: `3`. |
| `<max:8,3>` | The larger number: `8`. |
| `<clamp:8,1,5>` | `8` clamped into `1..5`: `5`. |
| `<id>` | The name of the modifier (or trigger) running the commands. |
| `<gmessage:"hi">` | Sends `hi` to every participant; the tag itself leaves nothing behind. |
| `<pmessage:yo>` | Sends `yo` to the executing player only. |
| `<gsound:block.stone.break>` | Plays the sound for every participant. |
| `<psound:block.stone.break,0.5,2>` | Plays the sound for the executing player, with pitch `0.5` and volume `2` (both default to `1`). |

`<min>`, `<max>`, and `<clamp>` accept math in their arguments
(`<min:8+5,10>` is `10`) and yield `0` with a console warning when an
argument is not a number.

### Conditions

`<if>` compares with `==`, `!=`, `>`, `<`, `>=`, `<=` and joins parts
with `and` / `or` (`and` binds tighter, case does not matter):

```yaml
- 'say <if:"1 == 1 and 2 < 3 or 4 == 5","y","n">'
```

Ordering needs whole numbers with a space on each side of the bracket:
`7 <= 5` works, `7<=5` warns and yields nothing. Each side compares as
a number when it parses as math, otherwise as text.

Quote the condition when it holds `<`, `>`, or commas. An unquoted
`<if:7 <= 5,...>` never resolves: the tag finder cannot tell a bare
`<` from a nested tag. `==` and `!=` need no quotes.

### Bare math

Any no-space token that fully parses as math evaluates: `give <p> egg
1+1` hands out 2 eggs. Parentheses, `**`, `//`, `%`, and `??` (null
coalescing) work; division by zero warns and yields 0. Quote a token
to protect it: `say "2026-09-26"` stays a date, while a bare
`2026-09-26` computes to 1991.

### Stopping a list

A lone `exit` line stops the command list: later lines never run. It is
checked after tags expand, so `<if:"1 == 2","exit","say hi">` skips
the rest only when the branch hits. `exit` with anything else on its
line is skipped with a warning.

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
    options:
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

# Creating Modifiers and Presets

Build entries in the GUI or inline from chat; both write the same
`modifiers.yml` blocks that manual editing produces, and manual
editing keeps working as before. Everything created this way starts
disabled.

In the GUI, a create button sits at the top-right of the modifiers
and presets lists. It prompts for a display name (you become the
author) and opens the new entry in its editor. Right-clicking any
existing entry opens the same editor.

The modifier editor covers every field: name, description, icon,
author, the enabled toggle, trigger toggles, pre-start order, all
timing and chance options, and every command list. The preset editor
covers name, description, icon, and membership toggles. Both editors
also export, rename the id, and delete after a confirm panel.
Buttons that prompt for optional values treat a blank answer as
clearing the field back to its default.

Commands validate when you save them: unbalanced angle brackets,
empty commands, malformed `<random-num:>` or `<random-pick:>`
arguments, unknown root commands, and unknown `give` items are
refused with an error, while unknown tags and skipped pick items
only warn. `<duration>` is compass-only and warns on modifiers.
Set `settings.server.advanced.validate-modifier-editor-commands`
to false to skip the root and item checks; placeholder checks
always run. The command-line creator enforces the same rules; see
[Modifiers](../commands.md#modifiers) for its flags.

Presets keep their display data under `meta:`, exactly like
modifiers, with the member list beside it:

```yaml
presets:
  chaos-mode:
    meta:
      name: "Chaos Mode"
      description: "Random mobs, random items, gear dice"
      item: TNT
    modifiers:
      - random-mob-spawner
      - random-item-giver
```

Presets written in the old flat shape (name and friends next to
`modifiers:`) no longer load: re-indent the four display keys under
`meta:`. Old preset share strings need a fresh export too.

# Sharing Modifiers and Presets

Export any modifier or preset to a share string: a click-to-copy chat
line you can paste to friends or the community. Import takes one back
in; colliding names get numbered automatically. Strings that fail
their checksum or schema check are refused without touching anything.

```text
/manhunt modifiers export modifier gear-dice
/manhunt modifiers export preset chaos-mode
/manhunt modifiers import modifier JMH1D:...
```

The modifiers and presets menus carry an import loom in the
bottom-right corner that prompts for the string, and every editor has
an export button that copies its own share string.

# QA Checklist

1. Create a modifier in the GUI and confirm it starts disabled.
2. Save a line with an unknown root and confirm refusal with an error.
3. Save a line with an unknown tag and confirm it saves with a warning.
4. Export a modifier, import the string back, and confirm the copy works.
5. Hover a preset with 9 members and confirm the `..and 1 more` line.
