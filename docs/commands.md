# Commands

All commands are available under `/manhunt` and its alias `/mh`.

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
| `<random-mob>` | A random spawnable living entity type (e.g. `zombie`, `creeper`). A new roll is made for each command execution. |
| `<random-item>` | A random item material (e.g. `diamond_sword`, `bread`). A new roll is made for each command execution. |

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