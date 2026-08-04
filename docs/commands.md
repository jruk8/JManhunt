# Commands

All commands are available under `/manhunt` and its alias `/mh`.

| Command                                           | What it does | Permission |
|---------------------------------------------------| --- | --- |
| `/manhunt`                                        | Shows the current teams and match status. | `jmanhunt.command.status` |
| `/manhunt help`                                   | Shows the in-game command list. | `jmanhunt.command.help` |
| `/manhunt setplayer <selector> <role>`            | Assigns `hunter`, `speedrunner`, `afk`, or `none`. | `jmanhunt.command.setplayer` |
| `/manhunt start`                                  | Starts a match. | `jmanhunt.command.start` |
| `/manhunt end`                                    | Ends the active match; hunters win. | `jmanhunt.command.end` |
| `/manhunt quickstart [percentage]`                | Assigns eligible players to teams and starts immediately, bypassing autostart. | `jmanhunt.command.quickstart` |
| `/manhunt modifiers [setting] [true\|false]`      | Lists, views, or changes built-in actions and custom modifiers. | `jmanhunt.command.modifiers` |
| `/manhunt worldengine setlobby [x,y,z,yaw,pitch]` | Sets the world-engine lobby position. | `jmanhunt.command.worldengine` |
| `/manhunt worldengine lobby [selector]`           | Teleports the sender or selected players to the lobby. | `jmanhunt.command.worldengine` |
| `/manhunt schem wand`                             | Gives you the schematic wand. | `jmanhunt.command.schem` |
| `/manhunt schem save <name>`                      | Saves the selected region as a schematic. | `jmanhunt.command.schem` |
| `/manhunt schem list`                             | Lists all available schematics. | `jmanhunt.command.schem` |
| `/manhunt schem delete <name>`                    | Deletes a schematic. | `jmanhunt.command.schem` |
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

`/manhunt quickstart [percentage]` is a convenience command for larger servers
that want to start a match without manually assigning roles. It can only be
used when no match is active.

- **Without arguments:** assigns every eligible online player (excluding AFK,
  including NONE) as a Hunter, randomly chooses one Speedrunner, and
  immediately starts the game.
- **With a percentage:** interprets the value as the percentage of eligible
  players that should become Speedrunners. For example, `50` with 16 eligible
  players results in 8 Speedrunners and 8 Hunters. Fractional results are
  rounded to the nearest whole player, and there is always at least one
  Speedrunner.

Quick Start bypasses the autostart system entirely — no countdowns or
autostart messages are displayed.

## Schematic Management

The `/jmanhunt schem` command manages structure `.nbt` files stored in
`plugins/JManhunt/challenges/structures/`. These are used by
Lucky Block `STRUCTURE` outcomes and can be reused by future features.

- **Save:** Obtain the schematic wand with `/jmanhunt schem wand`, then select
  two corners (left-click for position 1, right-click for position 2), and run
  `/jmanhunt schem save <name>`. If the file already exists, run the command
  again within 5 seconds to confirm overwrite.
- **List:** `/jmanhunt schem list` displays all available schematics.
- **Delete:** `/jmanhunt schem delete <name>` deletes a schematic. Run the
  command twice within 5 seconds to confirm deletion.

Confirmation is tracked per executor and expires after 5 seconds. Feedback
messages work correctly for both players and the console.

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
| `challenges.lucky-blocks` | Breaking the configured block drops a random outcome from `challenges/lucky-block/lucky-blocks.yml` instead of the block itself. |

The lucky-blocks challenge uses `challenges.lucky-blocks.block-definition`
(default `gold_block`) to select which block is intercepted. The outcome table
in `challenges/lucky-block/lucky-blocks.yml` uses composable outcomes with
weighted random selection. Each outcome may contain any combination of
`items`, `commands`, `structure`, and `feedback` sections. Commands support
the same placeholders (`<p>`, `<random-mob>`, `<random-item>`) and tilde
resolution as custom modifiers, with `relative-to` choosing whether tildes
resolve to the broken block or the player.

See [configuration.md](configuration.md#lucky-blocks) for full details on
composable outcomes, structure placement, reroll behavior, and feedback.
