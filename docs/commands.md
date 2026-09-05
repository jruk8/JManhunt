# Commands

All commands are available under `/manhunt` and its alias `/mh`.

| Command                                           | What it does | Permission |
|---------------------------------------------------| --- | --- |
| `/manhunt`                                        | Shows the current teams and match status. | `jmanhunt.command.status` |
| `/manhunt help`                                   | Shows the in-game command list. | `jmanhunt.command.help` |
| `/manhunt challenges`                             | Shows a chat notice with a clickable link to the optional Challenges addon. | `jmanhunt.command.challenges` |
| `/manhunt setplayer <selector> <role>`            | Assigns `hunter`, `speedrunner`, `afk`, or `none`. | `jmanhunt.command.setplayer` |
| `/manhunt start`                                  | Starts a match. | `jmanhunt.command.start` |
| `/manhunt end`                                    | Ends the active match; hunters win. | `jmanhunt.command.end` |
| `/manhunt quickstart [percentage]`                | Assigns eligible players to teams and starts immediately, bypassing autostart. | `jmanhunt.command.quickstart` |
| `/manhunt qs [percentage]`                        | Alias for `/manhunt quickstart`. | `jmanhunt.command.quickstart` |
| `/manhunt modifiers [setting] [value]`            | Lists, views, or changes built-in actions and settings. | `jmanhunt.command.modifiers` |
| `/manhunt worldengine setlobby [x,y,z,yaw,pitch]` | Sets the world-engine lobby position. | `jmanhunt.command.worldengine` |
| `/manhunt worldengine lobby [selector]`           | Teleports the sender or selected players to the lobby. | `jmanhunt.command.worldengine` |
| `/manhunt reload`                                 | Reloads `config.yml` and `messages.yml`. | `jmanhunt.command.reload` |

## Roles

The `setplayer` command accepts the following roles:

| Role | Description |
| --- | --- |
| `hunter` | Participates as a hunter. Requires `jmanhunt.hunter` permission. |
| `speedrunner` | Participates as a speedrunner. Requires `jmanhunt.speedrunner` permission. |
| `afk` | Excluded from the match entirely. AFK players never become hunters or speedrunners, are excluded from Quick Start, and are ignored by automatic team assignment. They can still be assigned through `/setplayer`. |
| `none` | Not participating. Sent to spectator mode if a match is active. |

## Quick Start

`/manhunt quickstart [percentage]` (alias `/manhunt qs [percentage]`) is a
convenience command for larger servers that want to start a match without
manually assigning roles. It can only be used when no match is active.

- **Without arguments:** assigns every online player with role `none` as a
  Hunter, randomly chooses one Speedrunner (unless a Speedrunner is already
  queued), and immediately starts the game.
- **With a percentage:** interprets the value as the percentage of eligible
  `none` players that should become Speedrunners. For example, `50` with 16
  eligible players results in 8 Speedrunners and 8 Hunters. Fractional results
  are rounded to the nearest whole player, and there is always at least one
  Speedrunner.

Only players with role `none` are assigned. Existing Hunters and Speedrunners
keep their roles, and AFK players are never touched. The match is validated
after assignment: it requires at least one Hunter and one Speedrunner, so a
server with two online players where one is AFK will fail to start.

Quick Start bypasses the autostart system entirely — no countdowns or
autostart messages are displayed.

## Editing Settings In-Game

`/manhunt modifiers` can browse and change scalar settings in-game. Boolean
toggles accept `true` or `false`:

```text
/manhunt modifiers settings.start-delay.enabled true
```

Numerical settings (ints, floats, doubles) accept their numeric value:

```text
/manhunt modifiers settings.compass.refresh-interval 5.0
/manhunt modifiers settings.win-conditions.surviveTime.time 1800.0
/manhunt modifiers settings.world-engine.cell-size 20000
```

Strings and enum-like values are stored verbatim:

```text
/manhunt modifiers settings.start-on-speedrunner-damage.on-expire FORCE_START
/manhunt modifiers settings.win-conditions.acquireItem.item minecraft:diamond
```

Setting names are matched case-insensitively. When tab-completing a value,
non-boolean settings suggest the **default value from the bundled default
config**.

> **Known limitation:** JManhunt does not use a type-safe configuration
> framework (such as Cloud). Values are parsed against the current type in
> `config.yml` only — booleans and numbers are validated, but strings and
> enums are stored verbatim with **no schema validation**. If you need
> guaranteed-valid enum keys or strict type checking, edit `config.yml`
> directly and run `/manhunt reload`.

## Custom Modifiers

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
| `<random-mob>` | A random spawnable living entity type in lowercase (e.g. `zombie`, `creeper`). A new roll is made for each command execution. |
| `<random-item>` | A random item material in lowercase (e.g. `diamond_sword`, `bread`). A new roll is made for each command execution. |

### Relative Coordinates

In player and role commands (`player`, `hunter`, `speedrunner`), tildes (`~`)
are automatically resolved to the participating player's position. For
example, `summon zombie ~ ~ ~` becomes `summon zombie 10.5 64 -20.2` if the
player is at `(10.5, 64.0, -20.2)`. Offsets like `~5` and `~-3` are supported.

All commands are dispatched as the console sender, so there are no permission
issues — the tilde resolution is handled by the plugin before dispatch. Local
coordinates (`^`) are not supported.

### Command Lists

The available command lists are:

- `commands.player`: runs at the start for every participating player.
- `commands.hunter`: runs at the start for every hunter.
- `commands.speedrunner`: runs at the start for every speedrunner.
- `commands.console`: runs at the start from the console.
- `commands.console-cleanup`: runs on the console when the match finishes.
- `commands.player-cleanup`: runs on every player when the match finishes.

### Run Timing

`runs-on` is a list of events that trigger the modifier's commands. If
`runs-on` is omitted, the modifier defaults to `ON_START`. Available values:

| Value | Trigger |
| --- | --- |
| `ON_START` | Once when the match starts (runs for all participants) |
| `INTERVAL` | On a fixed interval that starts counting when the game begins |
| `ON_EVERY_KILL` | When a participating player kills any entity (mobs included) |
| `ON_PLAYER_KILL` | When a participating player kills another player |
| `ON_HUNTER_KILL` | When a hunter kills a player |
| `ON_SPEEDRUNNER_KILL` | When a speedrunner kills a player |
| `ON_FIRST_ENTER_NETHER` | When a participating player first enters the Nether |
| `ON_FIRST_ENTER_END` | When a participating player first enters the End |
| `ON_EVERY_ADVANCEMENT` | When a participating player earns any advancement |
| `ON_RESPAWN` | When a player respawns (only the executing player) |
| `ON_SPEEDRUNNER_RESPAWN` | When a speedrunner respawns (only the executing player) |
| `ON_HUNTER_RESPAWN` | When a hunter respawns (only the executing player) |

Except for `ON_START`, all event-based modifiers run their `player`,
`hunter`, and `speedrunner` commands only for the specific player involved in
the event. `ON_START` and `INTERVAL` run for all participating players. The
role-specific commands (`hunter`/`speedrunner`) only run when the executing
player has that role — for example, if a hunter enters the Nether and only a
`speedrunner` command block is configured, that block does not run. Console
commands run in parallel regardless of the player's role.

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

The `interval-settings.interval` value supports decimals and is rounded to the
nearest tick (1 tick = 0.05 seconds). Values between `0` and `0.05` execute
every tick. For example, `0.5` runs every 10 ticks (0.5 seconds), and `1.5`
runs every 30 ticks (1.5 seconds). Set to `0` or `0.05` for every-tick
execution.

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

Play built-in challenges through our
companion plugin [**JManhunt-Challenges**](https://github.com/jruk8/JManhunt-Challenges),
which natively hooks into the [JManhunt API](api.md). Install 
alongside JManhunt and toggle with `/jmhchallenges toggle <challenge>`.