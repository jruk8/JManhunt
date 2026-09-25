# Commands

All commands are available under `/manhunt` and its alias `/mh`.

| Command                                           | What it does | Permission |
|---------------------------------------------------| --- | --- |
| `/manhunt`                                      | Opens the [admin GUI](gui.md) (needs `jmanhunt.gui`) or shows your match roster. | `jmanhunt.gui`, `jmanhunt.command.status` |
| `/manhunt status [id\|all]`                    | Shows one match roster by id, or every running match plus populated lobbies with `all`. | `jmanhunt.command.status`, `jmanhunt.command.status.other` for the argument |
| `/manhunt help`                                   | Shows the in-game command list. | `jmanhunt.command.help` |
| `/manhunt setup`                                  | Starts the interactive setup tutorial (players only). | `jmanhunt.command.setup` |
| `/manhunt challenges`                             | Shows a chat notice with a clickable link to the optional Challenges addon. | `jmanhunt.command.challenges` |
| `/manhunt setplayer <selector> <role>`            | Assigns `hunter`, `speedrunner`, `spectator`, `afk`, or `none` in queues without a running match. | `jmanhunt.command.setplayer` (`jmanhunt.command.setplayer.self` for your own role only) |
| `/manhunt lobby join <selector> <lobby-id> [role] [-notp]` | Moves players to a lobby queue, teleporting them there unless `-notp` is given. | `jmanhunt.command.lobby` |
| `/manhunt lobby leave [selector]`                 | Removes players from whatever lobby they are in. | `jmanhunt.command.lobby` |
| `/manhunt start [lobby-id]`                       | Starts a match for a lobby queue. | `jmanhunt.command.start` |
| `/manhunt end [id]`                               | Cancels a match with no winner and no saved stats. | `jmanhunt.command.end` |
| `/manhunt end [id] -i` / `-immediate`           | Cancels the match immediately, skipping the end delay intermission. | `jmanhunt.command.end` |
| `/manhunt swaproles [id]`                       | Swaps hunters and speedrunners in your match, or the single live match. | `jmanhunt.command.swaproles` |
| `/manhunt game join <id> [role] [selector]`       | Adds players to a running match (default role `spectator`). | `jmanhunt.command.game` |
| `/manhunt game leave [id] [selector]`             | Removes players from a running match; leaving participants need a second run within 10 seconds. | `jmanhunt.command.game` |
| `/manhunt quickstart [percentage]`                | Assigns eligible players to teams and starts immediately, bypassing autostart. | `jmanhunt.command.quickstart` |
| `/manhunt qs [percentage]`                        | Alias for `/manhunt quickstart`. | `jmanhunt.command.quickstart` |
| `/manhunt config <category> <key...> [value]` | Lists, views, or changes settings by category. | `jmanhunt.command.config` |
| `/manhunt worldengine lobbyconfig pos1\|pos2`                    | Records a lobby-bounds corner at your feet block. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig setbounds <lobby-id>`       | Stores the recorded corners as a lobby's bounds. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig setlobbytp <lobby-id> [coords]` | Sets a lobby's teleport (your position, or `x y z yaw pitch`). | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig deletelobby <lobby-id>`     | Deletes a lobby's stored entry. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine tpto lobbyworld\|gameworld [selector] [preset]` | Teleports to the lobby world (generating it on a confirmed second run) or the game world spawn. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.tpto`) |
| `/manhunt worldengine cellindex get`              | Shows the current world-engine cell index. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex set <value>`      | Sets the world-engine cell index, clamped to the addressable grid. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex buffer`           | Lists the buffered ready-cell ids. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt debug [on\|off]`                         | Toggles debug output for yourself or the console. Nearby lobby bounds also draw as colored edge particles while debug is on, plus white draft boxes for your own pending lobby and schem corners. | `jmanhunt.command.debug` |
| `/manhunt reload`                                 | Reloads `config.yml`, `messages.yml`, and `lobby-config.yml`. | `jmanhunt.command.reload` |

Tab completion only suggests subcommands and worldengine actions the sender
has permission to run.

## Roles

The `setplayer` command accepts the following roles:

| Role | Description |
| --- | --- |
| `hunter` | Participates as a hunter. Requires `jmanhunt.hunter` permission. |
| `speedrunner` | Participates as a speedrunner. Requires `jmanhunt.speedrunner` permission. |
| `spectator` | Watches a match without playing. Always put in spectator mode. Requires `jmanhunt.spectator` permission. |
| `afk` | Excluded from the match entirely. AFK players wait out the game in the lobby, are excluded from Quick Start, and are ignored by automatic team assignment. They can still be assigned through `setplayer`. Requires `jmanhunt.afk` permission. |
| `none` | Not participating: the recruit pool Quick Start and friends draw from. Requires `jmanhunt.none` permission. |

With `jmanhunt.command.setplayer`, a player can assign anyone to any role
(the target still needs the permission for that role). With only
`jmanhunt.command.setplayer.self`, a player can only target themselves and
only pick roles they have the permission for.

`setplayer` assigns directly while the target's lobby has no running
match. With a live match and the world engine on,
`lobbies.mid-match-setplayer` decides what happens instead (see
[Mid-match Setplayer](multi-instance.md#mid-match-setplayer)); with the
engine off the in-match block stays and `/manhunt game join` plus
`/manhunt game leave` are the mid-match tools (`-force` never bypasses
this). Assigning someone else away from `afk` needs the command run
twice within 10 seconds; changing your own role never needs
confirmation. `-s` (`-silent`) suppresses the role messages and sounds
the targets would get (including any role-change announcement); the
sender still sees the full summary.

## Quick Start

`/manhunt quickstart [percentage]` (alias `/manhunt qs [percentage]`) is a
convenience command for larger servers that want to start a match without
manually assigning roles. It runs in your lobby (or the default lobby
from the console) and can only be used when that lobby has no running match.

- **Without arguments:** keeps existing teams and converts only what is
  missing to start. If no Speedrunner is queued, a random player becomes one;
  if no Hunter is queued, a random player becomes one. An all-Hunter or
  all-Speedrunner lobby therefore still starts.
- **With a percentage:** interprets the value as the percentage of all
  convertible players that should become Speedrunners, assigned by random
  selection with the rest becoming Hunters. For example, `50` with 16
  players results in 8 Speedrunners and 8 Hunters. Fractional results
  are rounded to the nearest whole player, and there is always at least one
  Speedrunner.

Every online lobby member except AFK players and spectators is convertible,
including existing Hunters and Speedrunners as well as `none` players;
default mode preserves queued roles and only converts the minimum needed,
preferring `none` players for conversion. AFK players and spectators are
never touched. The match is validated after assignment: it requires at
least one Hunter and one Speedrunner, so a lobby with two online members
where one is AFK will fail to start. Queue caps never apply to Quick Start.

Quick Start bypasses the autostart system entirely: no countdowns or
autostart messages are displayed.

With the world engine off, `/manhunt start` and `/manhunt quickstart`
gather every participant around you on safe ground. From the console,
the group lands at a random wilderness point instead.

## Lobbies

Each lobby runs its own queue, autostart countdown, and match, so several
matches can run at the same time (see [Concurrent Matches](multi-instance.md)).
Players join the default lobby on login. `/manhunt lobby join` moves players
into a lobby (default role `none`) and teleports them to it unless `-notp`
is passed, honoring the per-role queue caps unless `-f` (`-force`) is
passed. Joining or leaving a positive lobby prints join and leave lines
to the moved player and that lobby's members; lobby 0 moves stay silent.
`lobbies.announce-lobby-changes` (`ALL`, `SELF`, `MEMBERS`, `NONE`) trims
who hears those lines. `/manhunt lobby leave [selector]` removes players
from whatever lobby they are in. Re-joining the same lobby with the same
role is refused with a notice. Multiple lobbies need the world engine;
with it off, everyone shares lobby 0. New to lobbies? Start with the
[Lobby Quick Start](lobby-quick-start.md).

## Joining and Leaving a Running Match

`/manhunt game join <id> [role] [selector]` adds players to a live match,
defaulting to `spectator`: the clean way to let someone watch. Joiners
move to the match's lobby, are teleported into its cell, and receive lives,
statistics, and a compass. Players already in a live match are skipped, and
matches in their end delay cannot be joined.

`/manhunt game leave [id] [selector]` removes them again. Living hunters
and speedrunners must run it twice within 10 seconds; they drop their gear
and become spectators, or return to the lobby, depending on
`settings.game-leave.destination`. Their departure is announced to the
lobby with how many of their role remain, and if the last hunter or
speedrunner leaves, the other side wins on the spot.

## Match Status

`/manhunt status` without an argument shows your own match, else your lobby
queue. `/manhunt status [id|all]` needs `jmanhunt.command.status.other` on
top of the base node: an id shows one match roster grouped by role with a
spectator roll call whenever someone is watching, while `all` lists every
running match first and then every lobby holding at least one player with
its player count. Four extras can be toggled under `settings.status`: the
per-side win conditions (`show-win-conditions`), the running time
(`show-elapsed-time`), the enabled modifiers (`show-modifiers`, hidden when
none are enabled), and a gray `L{lobby}|G{game}` tag (`show-ids`, on by
default).

## Lobby and Game Worlds

`/manhunt worldengine tpto lobbyworld [selector]` teleports to the lobby
world. If it does not exist yet, the first run names the missing world and
asks you to run it again within 10 seconds; the second run generates a void
world, pastes the configured lobby preset, points lobby 0 at the spawn,
and teleports you there. Pass a preset (`EMPTY`, `DEFAULT`, `ADVANCED`) to
generate with that preset instead of the configured one; the preset is
ignored once the world exists, and it cannot be combined with `gameworld`.
`/manhunt worldengine tpto gameworld [selector]` hops to the game world
spawn and never generates anything. Without a selector, both target you
(consoles must pass one).

## Editing Settings In-Game

`/manhunt config` (alias `/mh config`) browses and changes settings
in-game, one category at a time. Each argument drills one level deeper,
and tab completion only suggests the children of the current level.
Settings live under four categories: `settings.match`,
`settings.compass`, `settings.players`, and `settings.server`.
Everything here can also be changed through the [admin GUI](gui.md).

Every value is validated before it is stored. Booleans accept only
`true` or `false`; numbers must be numeric and inside their documented
bounds; choices accept only their listed options; anything else is
rejected with an error naming what is allowed:

```text
/manhunt config settings match autostart enabled true
```

Numerical settings accept their numeric value:

```text
/manhunt config settings compass refresh-interval 5
/manhunt config settings match win-conditions speedrunner survive-time time 1800
/manhunt config world-engine cell-size 20000
```

Choices accept one of their listed options, and text joins multiple
words into one value:

```text
/manhunt config settings match start-on-speedrunner-damage on-expire FORCE_START
```

String lists drill by index: view one entry, set one entry, append,
remove, or reset to defaults (only lists under `settings.*` also
appear in the GUI):

```text
/manhunt config match end-statistics 0
/manhunt config match end-statistics 0 <words>
/manhunt config match end-statistics add <words>
/manhunt config match end-statistics remove 0
/manhunt config match end-statistics reset
```

With no category, the command lists categories; with a section path, it lists
that section's next level: sub-sections as bare names, editable settings
as `child: value`. Non-editable branches never surface:

```text
/manhunt config
/manhunt config settings compass
```

Setting names are matched case-insensitively. When tab-completing a value,
non-boolean settings suggest the **default value from the bundled default
config**.

When `settings.server.announce-config-changes` is enabled, every
successful change is announced to all online players except the one who
made it (`manhunt.setting-change-announced`). It is disabled by default.

## Modifiers

Custom modifiers are named command bundles in `modifiers.yml`. They
are disabled by default. A modifier can run commands when a match
starts, on a recurring interval during the match, and when it ends,
either from the console or once for each participating player.

Browse and toggle them with `/manhunt modifiers`, through the
[admin GUI](gui.md), or by editing `modifiers.yml` and running
`/manhunt reload`. Share one with `export`, which prints a
click-to-copy string, and take one in with `import`:

```text
/manhunt modifiers export modifier gear-dice
/manhunt modifiers import modifier JMH1D:...
```

The example modifier in the default config gives players food and applies
different commands to hunters and speedrunners. `perma-night` is another
example.

Create new entries without touching YAML, from chat or the console.
Values run until the next flag, so names and commands keep their
spaces with no quoting needed:

```text
/manhunt modifiers create modifier Beef Party --trigger ON_START --player give <p> cooked_beef 8
/manhunt modifiers create preset Chaos --member beef-party --member gear-dice
```

Modifier flags: `--desc`, `--item`, `--author`, repeatable
`--trigger`, `--on-start`, `--interval`, `--deviation`,
`--interval-scope`, `--chance`, `--chance-scope`, `--selection`,
`--pick-count`, `--pick-scope`, `--delay`, and one repeatable flag per
command list (`--console`, `--player`, `--hunter`, `--speedrunner`,
`--console-cleanup`, `--player-cleanup`). Presets take `--desc`,
`--item`, and repeatable `--member`. Scopes accept `PER_INVOKE` or
`PER_EXECUTOR`; orders accept `IN_ORDER` or `PICK_RANDOM`. Invalid
values are refused with an error, while unknown command tags only
warn. The same creator lives in the GUI: a create button sits at the
top-right of each list, and right-clicking any entry edits it (see
[Creating Modifiers and Presets](configuration/modifiers.md#creating-modifiers-and-presets)).

### Full Reference

The complete modifier reference lives in
[Modifiers](configuration/modifiers.md).

## Challenges

Play built-in challenges through our
companion plugin [**JManhunt-Challenges**](https://github.com/jruk8/JManhunt-Challenges),
which natively hooks into the [JManhunt API](api.md). Install 
alongside JManhunt and toggle with `/jmhchallenges toggle <challenge>`.

## Developer Tools

`/manhunt dev schem <pos1|pos2|save|load|list>` (permission
`jmanhunt.command.dev.schem`) saves and loads vanilla `.nbt` structure
files for authoring lobby presets. See [Developer Tools](dev-tools.md);
only `list` works from the console.