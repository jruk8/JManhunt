# Commands

All commands are available under `/manhunt` and its alias `/mh`.

| Command                                           | What it does | Permission |
|---------------------------------------------------| --- | --- |
| `/manhunt [id\|all]`                             | Shows your match roster, one match roster by id, or every running match with `all`. | `jmanhunt.command.status` |
| `/manhunt help`                                   | Shows the in-game command list. | `jmanhunt.command.help` |
| `/manhunt challenges`                             | Shows a chat notice with a clickable link to the optional Challenges addon. | `jmanhunt.command.challenges` |
| `/manhunt setplayer <selector> <role>`            | Assigns `hunter`, `speedrunner`, `afk`, or `none`. | `jmanhunt.command.setplayer` (`jmanhunt.command.setplayer.self` for your own role only) |
| `/manhunt lobby join <selector> <lobby-id> <role>` | Moves players to a lobby queue with a role. | `jmanhunt.command.lobby` |
| `/manhunt lobby leave <player> <lobby-id>`        | Removes a player from a lobby queue. | `jmanhunt.command.lobby` |
| `/manhunt start [lobby-id]`                       | Starts a match for a lobby queue. | `jmanhunt.command.start` |
| `/manhunt end [id]`                               | Cancels a match with no winner and no saved stats. | `jmanhunt.command.end` |
| `/manhunt end [id] -i` / `-immediate`           | Cancels the match immediately, skipping the end delay intermission. | `jmanhunt.command.end` |
| `/manhunt joingame <selector> <id> <role>`        | Adds players to a running match as `hunter`, `speedrunner`, or `none`. | `jmanhunt.command.joingame` |
| `/manhunt quickstart [percentage]`                | Assigns eligible players to teams and starts immediately, bypassing autostart. | `jmanhunt.command.quickstart` |
| `/manhunt qs [percentage]`                        | Alias for `/manhunt quickstart`. | `jmanhunt.command.quickstart` |
| `/manhunt configuration <category> <key...> [value]` | Lists, views, or changes settings by category. | `jmanhunt.command.configuration` |
| `/manhunt worldengine setlobby [x,y,z,yaw,pitch]` | Sets the lobby 0 world-engine lobby position. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.setlobby`) |
| `/manhunt worldengine setlobbytp <lobby-id>`       | Sets a lobby's location from your current position. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.setlobbytp`) |
| `/manhunt worldengine lobby [selector] [lobby-id]` | Teleports the sender or selected players to a lobby. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobby`) |
| `/manhunt worldengine cellindex get`              | Shows the current world-engine cell index. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex set <value>`      | Sets the world-engine cell index, clamped to the addressable grid. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex buffer`           | Lists the buffered ready cells. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt debug [on\|off]`                         | Toggles debug output for yourself or the console. | `jmanhunt.command.debug` |
| `/manhunt reload`                                 | Reloads `config.yml` and `messages.yml`. | `jmanhunt.command.reload` |

Tab completion only suggests subcommands and worldengine actions the sender
has permission to run.

## Roles

The `setplayer` command accepts the following roles:

| Role | Description |
| --- | --- |
| `hunter` | Participates as a hunter. Requires `jmanhunt.hunter` permission. |
| `speedrunner` | Participates as a speedrunner. Requires `jmanhunt.speedrunner` permission. |
| `afk` | Excluded from the match entirely. AFK players never become hunters or speedrunners, are excluded from Quick Start, and are ignored by automatic team assignment. They can still be assigned through `/setplayer`. Requires `jmanhunt.afk` permission. |
| `none` | Not participating. Sent to spectator mode if a match is active. Requires `jmanhunt.none` permission. |

With `jmanhunt.command.setplayer`, a player can assign anyone to any role
(the target still needs the permission for that role). With only
`jmanhunt.command.setplayer.self`, a player can only target themselves and
only pick roles they have the permission for.

## Quick Start

`/manhunt quickstart [percentage]` (alias `/manhunt qs [percentage]`) is a
convenience command for larger servers that want to start a match without
manually assigning roles. It runs in your lobby (or the default lobby
from the console) and can only be used when that lobby has no running match.

- **Without arguments:** keeps existing teams and converts only what is
  missing to start. If no Speedrunner is queued, a random player becomes one;
  if no Hunter is queued, a random player becomes one. Players with role
  `none` become Hunters. An all-Hunter or all-Speedrunner lobby therefore
  still starts.
- **With a percentage:** interprets the value as the percentage of all
  convertible players that should become Speedrunners, assigned by random
  selection with the rest becoming Hunters. For example, `50` with 16
  players results in 8 Speedrunners and 8 Hunters. Fractional results
  are rounded to the nearest whole player, and there is always at least one
  Speedrunner.

Every online lobby member except AFK is convertible, including existing
Hunters and Speedrunners; default mode preserves queued roles and only
converts the minimum needed, preferring `none` players for conversion. AFK
players are never touched. The match is validated after assignment: it
requires at least one Hunter and one Speedrunner, so a lobby with two online
members where one is AFK will fail to start. Queue caps apply unless `-f`
(`-force`) is passed.

Quick Start bypasses the autostart system entirely: no countdowns or
autostart messages are displayed.

## Lobbies

Each lobby runs its own queue, autostart countdown, and match, so several
matches can run at the same time (see [Concurrent Matches](multi-instance.md)).
Players join the default lobby on login. `/manhunt lobby join` moves players
into a lobby with a role, honoring the per-role queue caps unless `-f`
(`-force`) is passed; `/manhunt lobby leave` removes them again. Multiple
lobbies need the world engine; with it off, everyone shares lobby 0.

## Joining a Running Match

`/manhunt joingame <selector> <id> <role>` adds players to a live match as
`hunter`, `speedrunner`, or `none` (spectator). Joiners move to the match's
lobby, are teleported into its cell, and receive lives, stats, and a compass.
Players already in a live match are skipped. Promoting a queued player with
`setplayer` while their lobby has a running match pulls them in the same way.

## Editing Settings In-Game

`/manhunt configuration` (alias `/mh config`) browses and changes scalar
settings in-game, one category at a time. Each argument drills one level
deeper, and tab completion only suggests the children of the current level.
Boolean toggles accept `true` or `false`:

```text
/manhunt configuration settings headstarts hunter enabled true
```

Numerical settings (ints, floats, doubles) accept their numeric value:

```text
/manhunt configuration settings compass refresh-interval 5.0
/manhunt configuration settings win-conditions surviveTime time 1800.0
/manhunt configuration world-engine cell-size 20000
```

Strings and enum-like values are stored verbatim:

```text
/manhunt configuration settings start-on-speedrunner-damage on-expire FORCE_START
/manhunt configuration settings win-conditions acquireItem item minecraft:diamond
```

With no category, the command lists categories; with a section path, it lists
that section's settings:

```text
/manhunt configuration
/manhunt configuration settings compass
```

Setting names are matched case-insensitively. When tab-completing a value,
non-boolean settings suggest the **default value from the bundled default
config**.

When `settings.announce-config-changes` is enabled, every successful change
is announced to all online players except the one who made it
(`manhunt.setting-change-announced`). It is disabled by default.

> **Known limitation:** JManhunt does not use a type-safe configuration
> framework (such as Cloud). Values are parsed against the current type in
> `config.yml` only: booleans and numbers are validated, but strings and
> enums are stored verbatim with **no schema validation**. If you need
> guaranteed-valid enum keys or strict type checking, edit `config.yml`
> directly and run `/manhunt reload`.

## Custom Modifiers

Custom modifiers are named command bundles in `config.yml` under
`custom-modifiers`. They are disabled by default. A modifier can run commands
when a match starts, on a recurring interval during the match, and when it
ends, either from the console or once for each participating player.

To enable a modifier, use its configuration name:

```text
/manhunt configuration custom-modifiers everyone-gets-beef enabled true
```

The example modifier in the default config gives players food and applies
different commands to hunters and speedrunners. `perma-night` is another
example. You can also toggle a modifier by changing its `enabled` value in
`config.yml`, then running `/manhunt reload`.

When creating a modifier, copy the structure of an existing one. Currently
only manual YAML file editing is supported for creation.

### Full Reference

The complete custom-modifier reference lives in
[Custom Modifiers](configuration/modifiers.md).

## Challenges

Play built-in challenges through our
companion plugin [**JManhunt-Challenges**](https://github.com/jruk8/JManhunt-Challenges),
which natively hooks into the [JManhunt API](api.md). Install 
alongside JManhunt and toggle with `/jmhchallenges toggle <challenge>`.