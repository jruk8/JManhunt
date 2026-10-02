# Commands

All commands are available under `/manhunt` and its alias `/mh`.

| Command                                           | What it does | Permission |
|---------------------------------------------------| --- | --- |
| `/manhunt`                                      | First run: one-click setup. Later: opens the [admin GUI](gui.md) (needs `jmanhunt.gui`) or shows your teams. | `jmanhunt.gui`, `jmanhunt.command.status` |
| `/manhunt status [id\|all]`                    | Shows one match roster by id, or every running match plus populated lobbies with `all`. | `jmanhunt.command.status`, `jmanhunt.command.status.other` for the argument |
| `/manhunt help`                                   | Shows the in-game command list. | `jmanhunt.command.help` |
| `/manhunt setup`                                  | Step-by-step setup tour in chat (players only). For learning as you go. | `jmanhunt.command.setup` |
| `/manhunt support`                                | Shows Discord, GitHub, and Ko-fi links. | `jmanhunt.command.support` |
| `/manhunt challenges`                             | Shows a chat notice with a clickable link to the optional Challenges addon. | `jmanhunt.command.challenges` |
| `/manhunt setplayer <selector> <role>`            | Assigns `hunter`, `speedrunner`, `spectator`, `afk`, or `none` in queues without a running match. | `jmanhunt.command.setplayer` (`jmanhunt.command.setplayer.self` for your own role only) |
| `/manhunt lobby join <selector> <lobby-id> [role] [-notp]` | Moves players to a lobby queue, teleporting them there unless `-notp` is given. | `jmanhunt.command.lobby` |
| `/manhunt lobby leave [selector]`                 | Removes players from whatever lobby they are in. | `jmanhunt.command.lobby` |
| `/manhunt start [lobby-id]`                       | Starts a match for a lobby queue. | `jmanhunt.command.start` |
| `/manhunt end [id]`                               | Cancels a match with no winner and no saved stats. | `jmanhunt.command.end` |
| `/manhunt end [id] -i` / `-immediate`           | Cancels the match immediately, skipping the end delay intermission. | `jmanhunt.command.end` |
| `/manhunt end all [-i]`                         | Cancels every running match at once, with one reply. Works from console. | `jmanhunt.command.end` |
| `/manhunt game join <id> [role] [selector]`       | Adds players to a running match (default role `spectator`). | `jmanhunt.command.game` |
| `/manhunt game leave [id] [selector]`             | Removes players from a running match; leaving participants need a second run within 10 seconds. | `jmanhunt.command.game` |
| `/manhunt quickstart [percentage]`                | Assigns eligible players to teams and starts immediately, bypassing autostart. | `jmanhunt.command.quickstart` |
| `/manhunt qs [percentage]`                        | Alias for `/manhunt quickstart`. | `jmanhunt.command.quickstart` |
| `/manhunt config <category> <key...> [value]` | Lists, views, or changes settings by category. | `jmanhunt.command.config` |
| `/manhunt override <lobby> settings <get\|set\|clear> <key...> [value]` | Views, changes, or removes one lobby's setting overrides. | `jmanhunt.command.override` |
| `/manhunt override <lobby> modifiers <get\|set\|clear> [id] [true\|false]` | Views, changes, or removes one lobby's modifier overrides. | `jmanhunt.command.override` |
| `/manhunt override <lobby> clear` | Removes every override of one lobby. | `jmanhunt.command.override` |
| `/manhunt worldengine lobbyconfig pos1\|pos2`                    | Records a lobby-bounds corner at your feet block. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig setbounds <lobby-id>`       | Stores the recorded corners as a lobby's bounds. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig setlobbytp <lobby-id> [coords]` | Sets a lobby's teleport (your position, or `x y z yaw pitch`). | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig deletelobby <lobby-id>`     | Deletes a lobby's stored entry. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine tpto lobbyworld\|gameworld [selector] [preset]` | Teleports to the lobby world (generating it on a confirmed second run) or the game world spawn. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.tpto`) |
| `/manhunt worldengine cellindex get`              | Shows the current world-engine cell index. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex set <value>`      | Sets the world-engine cell index, clamped to the addressable grid. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex buffer`           | Lists the buffered ready-cell ids. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt debug [INFO\|WARN\|SEVERE]`              | Sets your debug level, or the console's when run from it; bare `/manhunt debug` toggles output on at INFO or off. SEVERE shows only severe lines, WARN adds warnings, INFO shows all. Nearby lobby bounds also draw as colored edge particles while debug is on, plus white draft boxes for your own pending lobby and schem corners. | `jmanhunt.command.debug` |
| `/manhunt reload`                                 | Reloads the engine and mods, then reports new/removed ids plus file problems (see below). | `jmanhunt.command.reload` |

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
`advanced.lobbies.mid-match-setplayer` decides what happens instead (see
[Mid-match Setplayer](multi-instance.md#mid-match-setplayer)); with the
engine off the in-match block stays and `/manhunt game join` plus
`/manhunt game leave` are the mid-match tools (`-force` never bypasses
this). Players already in the running match need `-f` (`-force`) for
any role change; without it the command names the player and tells
you to re-run with `-f`. A forced change to `spectator` removes them
from the match exactly like `/manhunt game leave`, while `afk` and
`none` remove them and return them to the lobby, out of the game
entirely. A forced change that leaves the match without hunters or
without speedrunners cancels the match. Assigning someone else away
from `afk` needs the command run twice within 10 seconds; changing
your own role never needs confirmation. `-s` (`-silent`) suppresses
the role messages and sounds the targets would get (including any
role-change announcement); the sender still sees the full summary.

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
`advanced.lobbies.announce-lobby-changes` (`ALL`, `SELF`, `MEMBERS`, `NONE`) trims
who hears those lines. `/manhunt lobby leave [selector]` removes players
from whatever lobby they are in. Re-joining the same lobby with the same
role is refused with a notice. Multiple lobbies need the world engine;
with it off, everyone shares lobby 0. New to lobbies? Start with the
[Lobby Quick Start](lobby-quick-start.md).

## Team Chat

Hunters and speedrunners in a match can talk privately by prefixing a
line with `@team` or `@t`: the line goes to same-team members only
instead of the public broadcast. A bare prefix chats a usage hint.
Anything else (spectators, lobby idlers, players outside a match)
sends raw with the prefix intact. Under
`settings.server.team-chat`, `enabled` toggles the feature,
`prefixes` lists the aliases, and `spectators-see` lets
fake-spectator watchers read every team line. The
`chat.team-chat-format` message supports `{role}`, `{rolecolor}`,
`{player}`, and `{message}`; recipients hear the `chat.team-chat`
sound from `sounds.yml`.

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
match with how many of their role remain, and if the last hunter or
speedrunner leaves, the other side wins on the spot.

## Match Status

`/manhunt status` without an argument shows your own match, else your lobby
queue. `/manhunt status [id|all]` needs `jmanhunt.command.status.other` on
top of the base node: an id shows one match roster grouped by role with a
spectator roll call whenever someone is watching, while `all` lists every
running match first and then every lobby holding queued or match members,
each with a split count (`1 in lobby, 4 in match`, `4 in match`, or
`4 in lobby`). Members who disconnected but have not been kicked yet
still count as in match. Four extras can be toggled under
`settings.server.status`: the per-side win conditions
(`show-win-conditions`), the running time (`show-elapsed-time`), the
enabled modifiers (`show-modifiers`, hidden when none are enabled, listed
by configured display name), and a gray `L{lobby}|G{game}` tag
(`show-ids`, off by default).

Each role line joins names Oxford style (`a, b, and c`; `a and b`) and
truncates long rosters with `and n more`. The per-role caps live in
`messages.yml` (`manhunt.status-limit-*`: 5 speedrunners, 10 hunters,
2 AFK, 20 none, 5 spectators), along with the separators and
templates. Permanently eliminated players move to the spectator role
and are memorialized under their former role block: a dark-gray skull
line shows the 3 most recent deaths (`manhunt.status-dead-line`,
cap at `manhunt.status-limit-dead`), while the end-of-match
leaderboard still lists them without skulls. Players waiting out a
respawn delay carry an inline skull on their alive line instead.

A match roster shows only that match's members (participants plus its
spectators); lobby queues and queued spectators read in lobby status
and join matches through `/manhunt game join`. Match members never
appear in a lobby roster: lobby status lists queue members only, and
match traffic (leave lines, kill lines, friendly fire) reaches only
the match compartment plus the console, never sibling lobbies.
Eliminated spectators keep hearing their match; players who left back
to the lobby hear nothing more.

## Lobby and Game Worlds

`/manhunt worldengine tpto lobbyworld [selector]` teleports to the lobby
world. If it does not exist yet, the first run names the missing world and
asks you to run it again within 10 seconds; the second run generates a void
world, pastes the `DEFAULT` lobby preset, points lobby 0 at the spawn,
and teleports you there. Pass a preset (`EMPTY`, `DEFAULT`, `ADVANCED`) to
generate with that preset instead of `DEFAULT`; the preset is
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
/manhunt config settings compass actions auto interval 5
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
non-boolean settings suggest the **setting's default value**.

When `settings.server.announce-config-changes` is enabled, every
successful change is announced to all online players except the one who
made it (`manhunt.setting-change-announced`). It is disabled by default.

## Per-Lobby Overrides

`/manhunt override` stores per-lobby setting and modifier overrides in
`lobby-config.yml`. Matches running from a lobby read the override
first and fall back to the global config; anything not overridden
behaves exactly as before. Validation matches the global drill, and
writes announce like global changes when announcing is enabled. The
same overrides can be edited through the
[admin GUI](gui.md#lobby-overrides), which also marks each value with
its source.

```text
/manhunt override 1 settings get settings match autostart enabled
/manhunt override 1 settings set settings match autostart enabled false
/manhunt override 1 settings clear settings match autostart
/manhunt override 1 modifiers get
/manhunt override 1 modifiers set gapple true
/manhunt override 1 modifiers clear gapple
/manhunt override 1 clear
```

`settings get` shows the effective value plus `(override)` or
`(global)`; sections list their next level and lists show every entry
by index. `settings set` takes scalars and list indices
(`match end-statistics 0 <words>`), plus `add <words>` and
`remove <index>` on whole lists. `settings clear` drops one leaf,
one list, or a whole subtree. `modifiers get` lists every modifier
and preset with its effective state; `set` accepts a modifier or
preset id (a preset forces every member) and `clear` without an id
drops every modifier override. Bare `clear` drops the whole lobby's
overrides; lobbies left with nothing stored are pruned. Every
clear variant asks once and deletes on an identical rerun within
10 seconds.

## Modifiers

Custom modifiers are named command bundles in `mods/modifiers/`.
They are disabled by default. A modifier can run commands when a
match starts, on a recurring interval during the match, and when it
ends, either from the console or once for each participating player.

Browse and toggle them with `/manhunt modifiers`, through the
[admin GUI](gui.md), or by editing files under `mods/` and running
`/manhunt reload` (see [Mod Files](configuration/mods-files.md)).
Share one with `export`, which prints a
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

Dry-run commands without a match with `test` (players only). Everything
after the role runs through the real tag pipeline with mock stats
(full health and hunger, fresh counters and clocks); gameplay commands
like `give` still execute for real, and tag errors fail the run with
the warning text. Bracketed input runs each element in order:

```text
/manhunt modifiers test hunter give <p> bread
/manhunt modifiers test speedrunner ["<pmessage:<p>,ready>", "effect give <p> speed 5"]
```

In the modifier editor, shift-left-click a command line to test that
line, or shift-left-click a command list to test the whole list.

Modifier flags: `--desc`, `--item`, `--author`, repeatable
`--trigger`, `--on-start`, `--interval`, `--deviation`,
`--interval-scope`, `--chance`, `--chance-scope`, `--selection`,
`--pick-count`, `--pick-scope`, `--delay`, and one repeatable flag per
command list (`--console`, `--player`, `--hunter`, `--speedrunner`,
`--console-cleanup`, `--player-cleanup`). Presets take `--desc`,
`--item`, `--author`, and repeatable `--member`. Without
`--author`, the creator becomes the author (the console records
`CONSOLE`). Scopes accept `PER_INVOKE` or
`PER_EXECUTOR`; orders accept `IN_ORDER` or `PICK_RANDOM`. Invalid
values are refused with an error, while unknown command tags only
warn. The same creator lives in the GUI: a create button sits at the
top-right of each list, and right-clicking any entry edits it (see
[Creating Modifiers and Presets](configuration/modifiers/creating.md#creating-modifiers-and-presets)).

`/manhunt reload` prints one confirmation line, then up to four
report lines for the mods load (lines with nothing to report are
skipped):

```text
Reloaded the Manhunt engine in 40ms.
» 1 new modifier, removed 1 preset
» Unknown files: notes.txt and 1 more
» Failed to parse modifiers/presets: gear-dice.yml and 1 more
» Duplicate id found: vanilla-plus
```

The first report line counts added and removed ids (a rename shows
as one of each); the rest name unknown files, files that failed to
parse, and duplicate ids. Each names the first entry in load order
plus how many more follow. Every string is configurable under
`manhunt.reload-*` in `messages.yml`.

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
`jmanhunt.command.dev.schem`) saves and loads `.jmhlobby` bundles
(structure plus lobby bounds and teleports) for authoring lobby
presets. See [Developer Tools](dev-tools.md); only `list` works from
the console.