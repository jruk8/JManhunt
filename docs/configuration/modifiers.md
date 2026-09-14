# Custom Modifiers

**Custom modifiers** are named command bundles you define in `config.yml`
under `custom-modifiers`. They are disabled by default. A modifier can run
commands when a match starts, on a recurring interval during the match, when
specific game events happen, and when the match ends, either from the console
or once for each participating player.

All command examples are the default config settings. Some defaults may be outdated
(typically not), in
which case you should refer to the latest version of `config.yml` in the
[GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

Settings for custom modifiers are categorized under `custom-modifiers`:

```yaml
custom-modifiers:
  everyone-gets-beef:
    enabled: false
```

# Enabling a Modifier

Under `custom-modifiers.<name>`, the `enabled` flag decides whether the
bundle runs at all. Toggle a bundle in-game with:

```text
/manhunt modifiers custom-modifiers.everyone-gets-beef true
```

You can also flip `enabled` in `config.yml` directly, then run
`/manhunt reload`. When creating a modifier, copy the structure of an existing
one. Currently only manual YAML file editing is supported for creation.

# Command Lists

Under `custom-modifiers.<name>.commands`, you can configure which commands run
and for whom. The bundled `everyone-gets-beef` example gives every
participating player eight steaks when the match starts:

```yaml
custom-modifiers:
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

Under `custom-modifiers.<name>.runs-on`, you can configure when the commands
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
| `ON_EVERY_ADVANCEMENT` | When a participating player earns any advancement |
| `ON_RESPAWN` | When a player respawns (only the executing player) |
| `ON_SPEEDRUNNER_RESPAWN` | When a speedrunner respawns (only the executing player) |
| `ON_HUNTER_RESPAWN` | When a hunter respawns (only the executing player) |

If `runs-on` is omitted, the modifier defaults to `ON_START`.

> **Note:** `ON_FIRST_ENTER_NETHER` and `ON_FIRST_ENTER_END` are legacy
> aliases of `ON_NETHER_ENTER` and `ON_END_ENTER`. They mean once per player
> per match, not once per match. Use the `ON_FIRST_NETHER_ENTER` /
> `ON_FIRST_END_ENTER` keys when you want the bundle to run a single time for
> the whole match.

Except for `ON_START`, every event trigger runs the `player`, `hunter`, and
`speedrunner` commands only for the specific player involved in the event.
`ON_START` and `INTERVAL` run for all participating players instead.

## Interval Settings

Under `custom-modifiers.<name>.interval-settings`, you can configure how often
an `INTERVAL` modifier repeats. It only applies when `runs-on` contains
`INTERVAL`:

```yaml
custom-modifiers:
  random-mob-spawner:
    enabled: false
    runs-on:
      - INTERVAL
    interval-settings:
      # Interval duration in seconds.
      interval: 60
```

Interval modifiers start counting when the game actually begins (when a
speedrunner hits a hunter, or when the match force-starts), not when
`/manhunt start` is run. They are automatically canceled when the match ends.

The `interval` value supports decimals and is rounded to the nearest tick (1
tick = 0.05 seconds). Values between `0` and `0.05` execute every tick. Set to
`0` or `0.05` for every-tick execution. Negative values disable the modifier.

## Placeholders in Commands

Commands can use these placeholders:

| Placeholder | Replaced with |
| --- | --- |
| `<p>` | The participating player's name. Use this in player and role commands. |
| `<random-mob>` | A random spawnable living entity type in lowercase (e.g. `zombie`, `creeper`). A new roll is made for each command execution. |
| `<random-item>` | A random item material in lowercase (e.g. `diamond_sword`, `bread`). A new roll is made for each command execution. |

## Relative Coordinates

In player and role commands (`player`, `hunter`, `speedrunner`), tildes (`~`)
are automatically resolved to the participating player's position. For
example, `summon zombie ~ ~ ~` becomes `summon zombie 10.5 64 -20.2` if the
player is at `(10.5, 64.0, -20.2)`. Offsets like `~5` and `~-3` are supported.

All commands are dispatched as the console sender, so there are no permission
issues. The tilde resolution is handled by the plugin before dispatch. Local
coordinates (`^`) are not supported.

# Match-End Cleanup

The `console-cleanup` and `player-cleanup` lists run when the match ends,
which makes them the right place to undo whatever the modifier changed. The
bundled `perma-night` modifier, for example, re-enables daylight when the
match is over:

```yaml
custom-modifiers:
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

The default `config.yml` ships more examples to copy from: `full-iron-kit`,
`speedrunner-health-advantage`, `random-mob-spawner`, `random-item-giver`,
`regen-on-kill`, `diamond-on-advancement`, and `fireres-on-nether-enter`.
