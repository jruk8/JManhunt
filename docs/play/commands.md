# Commands

> For: intermediate admins. Casual players only need `status`, `qs`, and `start`.

All commands are available under `/manhunt` and its alias `/mh`. Tab
completion only suggests what the sender has permission to run.

## Playing

| Command | What it does | Permission |
| --- | --- | --- |
| `/manhunt` | First run: one-click setup. Later: opens the [admin GUI](gui.md) (needs `jmanhunt.gui`) or shows your teams. | `jmanhunt.gui`, `jmanhunt.command.status` |
| `/manhunt status [id\|all]` | Shows one match roster by id, or every running match plus populated lobbies with `all`. | `jmanhunt.command.status`, `jmanhunt.command.status.other` for the argument |
| `/manhunt help` | Shows the in-game command list. | `jmanhunt.command.help` |
| `/manhunt setup` | Step-by-step setup tour in chat (players only). For learning as you go. | `jmanhunt.command.setup` |
| `/manhunt support` | Shows Discord, GitHub, and Ko-fi links. | `jmanhunt.command.support` |
| `/manhunt challenges` | Shows a chat notice with a clickable link to the optional Challenges addon. | `jmanhunt.command.challenges` |

### Match Status

`/manhunt status` without an argument shows your own match, else your
lobby queue. An id shows one match roster grouped by role; `all` lists
every running match first and then every populated lobby. Four extras
can be toggled under `settings.server.status`: win conditions, running
time, enabled modifiers, and the gray `L{lobby}|G{game}` tag (off by
default). Long rosters truncate, and dead players get a skull line
(tune all of it in `messages.yml` under `manhunt.status-*`).

## Teams

| Command | What it does | Permission |
| --- | --- | --- |
| `/manhunt setplayer <selector> <role>` | Assigns `hunter`, `speedrunner`, `spectator`, `afk`, or `none`. | `jmanhunt.command.setplayer` (`jmanhunt.command.setplayer.self` for your own role only) |
| `/manhunt quickstart [percentage]` | Assigns teams and starts immediately, bypassing autostart. | `jmanhunt.command.quickstart` |
| `/manhunt qs [percentage]` | Alias for `/manhunt quickstart`. | `jmanhunt.command.quickstart` |

### Roles

| Role | Description |
| --- | --- |
| `hunter` | Participates as a hunter. Requires `jmanhunt.hunter`. |
| `speedrunner` | Participates as a speedrunner. Requires `jmanhunt.speedrunner`. |
| `spectator` | Watches without playing. Requires `jmanhunt.spectator`. |
| `afk` | Excluded from the match entirely; waits in the lobby. Requires `jmanhunt.afk`. |
| `none` | Not participating: the pool Quick Start draws from. Requires `jmanhunt.none`. |

Targets always need the permission for their role. `setplayer` assigns
directly while the target's lobby has no running match; with a live
match, the mid-match policy decides instead (see
[Concurrent Matches](concurrent-matches.md#mid-match-setplayer)), and
with the engine off the in-match block stays. Players already in the
match need `-f` (`-force`) for any role change. Moving someone else out
of `afk` needs the command twice within 10 seconds; `-s` (`-silent`)
suppresses the role messages and sounds.

### Quick Start

Runs in your lobby (or the default lobby from the console), only when
that lobby has no running match. Without arguments it keeps existing
teams and converts only what is missing (preferring `none` players); a
percentage converts that share of convertible players to speedrunners
at random, always leaving at least one. AFK players and spectators are
never touched, caps never apply, and autostart is bypassed entirely.

With the world engine off, `/manhunt start` and `/manhunt quickstart`
gather every participant around you on safe ground. From the console,
the group lands at a random wilderness point instead.

## Lobbies

| Command | What it does | Permission |
| --- | --- | --- |
| `/manhunt lobby join <selector> <lobby-id> [role] [-notp]` | Moves players to a lobby queue, teleporting them there unless `-notp` is given. | `jmanhunt.command.lobby` |
| `/manhunt lobby leave [selector]` | Removes players from whatever lobby they are in. | `jmanhunt.command.lobby` |

Each lobby runs its own queue, autostart countdown, and match (see
[Concurrent Matches](concurrent-matches.md)). Joining honors the
per-role queue caps unless `-f` is passed; joining or leaving a
positive lobby prints lines trimmed by
`advanced.lobbies.announce-lobby-changes`, while lobby 0 moves stay
silent. Multiple lobbies need the world engine; with it off, everyone
shares lobby 0. New to lobbies? Start with the
[Lobby System](lobby-system/index.md).

## Matches

| Command | What it does | Permission |
| --- | --- | --- |
| `/manhunt start [lobby-id]` | Starts a match for a lobby queue. | `jmanhunt.command.start` |
| `/manhunt end [id]` | Cancels a match with no winner and no saved stats. | `jmanhunt.command.end` |
| `/manhunt end [id] -i` / `-immediate` | Cancels immediately, skipping the end delay intermission. | `jmanhunt.command.end` |
| `/manhunt end all [-i]` | Cancels every running match at once. Works from console. | `jmanhunt.command.end` |
| `/manhunt game join <id> [role] [selector]` | Adds players to a running match (default `spectator`). | `jmanhunt.command.game` |
| `/manhunt game leave [id] [selector]` | Removes players from a running match; leaving participants need a second run within 10 seconds. | `jmanhunt.command.game` |

### Joining and Leaving a Running Match

`/manhunt game join` is the clean way to let someone watch: joiners
move to the match's lobby, teleport into its cell, and receive lives,
statistics, and a compass. Matches in their end delay cannot be joined.
`/manhunt game leave` removes them; living hunters and speedrunners drop
their gear and become spectators (or return to the lobby, per
`settings.game-leave.destination`), and if a side empties, the other
side wins on the spot. See
[Concurrent Matches](concurrent-matches.md#joining-and-leaving-a-running-match)
for the full behavior.

### Team Chat

Hunters and speedrunners in a match can talk privately by prefixing a
line with `@team` or `@t`. A bare prefix chats a usage hint; anyone
else sends raw with the prefix intact. Under
`settings.server.team-chat`, `enabled` toggles the feature, `prefixes`
lists the aliases, and `spectators-see` lets watchers read every team
line.

## Settings

| Command | What it does | Permission |
| --- | --- | --- |
| `/manhunt config <category> <key...> [value]` | Lists, views, or changes settings by category. | `jmanhunt.command.config` |
| `/manhunt override <lobby> settings <get\|set\|clear> <key...> [value]` | Views, changes, or removes one lobby's setting overrides. | `jmanhunt.command.override` |
| `/manhunt override <lobby> modifiers <get\|set\|clear> [id] [true\|false]` | Views, changes, or removes one lobby's modifier overrides. | `jmanhunt.command.override` |
| `/manhunt override <lobby> clear` | Removes every override of one lobby. | `jmanhunt.command.override` |

### Editing Settings In-Game

`/manhunt config` browses and changes settings in-game, one category at
a time. Each argument drills one level deeper, and tab completion only
suggests the children of the current level. Everything here can also be
changed through the [admin GUI](gui.md).

```text
/manhunt config settings match autostart enabled true
/manhunt config settings compass actions auto interval 5
/manhunt config world-engine cell-size 20000
```

Every value is validated before it is stored; anything invalid is
rejected with an error naming what is allowed. String lists drill by
index (`... 0`, `... 0 <words>`, `... add <words>`,
`... remove 0`, `... reset`). With no category the command lists
categories; setting names match case-insensitively. When
`settings.server.announce-config-changes` is enabled, every change is
announced to all online players except the one who made it.

### Per-Lobby Overrides

`/manhunt override` stores per-lobby setting and modifier overrides.
Matches read the override first and fall back to global config;
anything not overridden behaves exactly as before. The same overrides
can be edited through the [admin GUI](gui.md#lobby-overrides).

```text
/manhunt override 1 settings get settings match autostart enabled
/manhunt override 1 settings set settings match autostart enabled false
/manhunt override 1 modifiers set gapple true
/manhunt override 1 clear
```

`get` shows the effective value plus `(override)` or `(global)`;
`clear` drops one leaf, one list, or a whole subtree. Every clear
variant asks once and deletes on an identical rerun within 10 seconds;
lobbies left with nothing stored are pruned.

## World Engine

| Command | What it does | Permission |
| --- | --- | --- |
| `/manhunt worldengine lobbyconfig pos1\|pos2` | Records a lobby-bounds corner at your feet block. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig setbounds <lobby-id>` | Stores the recorded corners as a lobby's bounds. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig setlobbytp <lobby-id> [coords]` | Sets a lobby's teleport (your position, or `x y z yaw pitch`). | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine lobbyconfig deletelobby <lobby-id>` | Deletes a lobby's stored entry. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.lobbyconfig`) |
| `/manhunt worldengine tpto lobbyworld\|gameworld [selector] [preset]` | Teleports to the lobby world (generating it on a confirmed second run) or the game world spawn. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.tpto`) |
| `/manhunt worldengine cellindex get` | Shows the current world-engine cell index. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex set <value>` | Sets the world-engine cell index, clamped to the addressable grid. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |
| `/manhunt worldengine cellindex buffer` | Lists the buffered ready-cell ids. | `jmanhunt.command.worldengine` (`jmanhunt.command.worldengine.cellindex`) |

### Lobby and Game Worlds

`/manhunt worldengine tpto lobbyworld [selector]` teleports to the
lobby world. If it does not exist yet, the first run asks you to run it
again within 10 seconds; the second run generates a void world, pastes
the `DEFAULT` lobby preset, and points lobby 0 at the spawn. Pass a
preset (`EMPTY`, `DEFAULT`, `ADVANCED`) to generate with that preset
instead; it needs a selector
(`/manhunt worldengine tpto lobbyworld @a ADVANCED`) and is ignored
once the world exists. `gameworld` hops to the game world spawn and
never generates anything. Without a selector, both target you (the
console must pass one).

## Mods and Admin

| Command | What it does | Permission |
| --- | --- | --- |
| `/manhunt modifiers setmod <id> <true\|false>` | Switches one modifier on or off. | `jmanhunt.modifiers` |
| `/manhunt modifiers setpreset <id> <true\|false>` | Switches one preset on or off. | `jmanhunt.modifiers` |
| `/manhunt modifiers export modifier <id>` | Prints a click-to-copy share string. | `jmanhunt.modifiers` |
| `/manhunt modifiers import modifier <string>` | Takes a shared string in. | `jmanhunt.modifiers` |
| `/manhunt modifiers create ...` | Creates entries from chat or console (see below). | `jmanhunt.modifiers` |
| `/manhunt modifiers test <role> <commands>` | Dry-runs commands through the tag pipeline (players only). | `jmanhunt.modifiers` |
| `/manhunt reload` | Reloads the engine and mods, then reports new/removed ids plus file problems. | `jmanhunt.command.reload` |
| `/manhunt debug [INFO\|WARN\|SEVERE]` | Sets lifecycle debug output level; bare command toggles. Bounds draw as particles while on. | `jmanhunt.command.debug` |
| `/manhunt dev schem <pos1\|pos2\|save\|load\|list>` | Saves and loads `.jmhlobby` bundles for authoring presets. Only `list` works from console. | `jmanhunt.command.dev.schem` |

### Modifiers

Browse and toggle mods with `setmod` and `setpreset`, through the
[admin GUI](gui.md), or by editing files under `mods/` and running
`/manhunt reload` (see [Mod Files](../configuration/mods-files.md)).
Share one with `export` and take one in with `import`:

```text
/manhunt modifiers export modifier gear-dice
/manhunt modifiers import modifier JMH1D:...
```

Create entries without touching YAML; values run until the next flag,
so names and commands keep their spaces with no quoting needed:

```text
/manhunt modifiers create modifier Beef Party --trigger ON_START --player give <p> cooked_beef 8
/manhunt modifiers create preset Chaos --member beef-party --member gear-dice
```

Flags: `--desc`, `--item`, `--author`, repeatable `--trigger`,
`--on-start`, `--interval`, `--deviation`, `--interval-scope`,
`--chance`, `--chance-scope`, `--selection`, `--pick-count`,
`--pick-scope`, `--delay`, and one repeatable flag per command list
(`--console`, `--player`, `--hunter`, `--speedrunner`,
`--console-cleanup`, `--player-cleanup`). Presets take `--desc`,
`--item`, `--author`, and repeatable `--member`. Without `--author`,
the creator becomes the author. See
[Creating Modifiers and Presets](../configuration/modifiers/creating.md#creating-modifiers-and-presets).

`/manhunt reload` prints one confirmation line plus up to four report
lines (added/removed ids, unknown files, parse failures, duplicate
ids), each naming the first entry plus how many more follow. Every
string is configurable under `manhunt.reload-*` in `messages.yml`.

The complete modifier reference lives in
[Modifiers](../configuration/modifiers.md).

### Challenges

Play built-in challenges through our
companion plugin [**JManhunt-Challenges**](https://github.com/jruk8/JManhunt-Challenges),
which natively hooks into the [JManhunt API](../api.md). Install
alongside JManhunt and toggle with `/jmhchallenges toggle <challenge>`.

### Developer Tools

`/manhunt dev schem` saves and loads `.jmhlobby` bundles (structure
plus lobby bounds and teleports) for authoring lobby presets. See
[Developer Tools](../dev-tools.md); only `list` works from the console.
