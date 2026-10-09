---
icon: material/layers
---

# Concurrent Matches

> For: intermediate admins running more than one match.

With the world engine on, each lobby can run its own match at the same
time: every match gets its own map and compass tracking, so they never
interfere. With the world engine **off**, everyone shares lobby 0 and
only one match runs at a time.

## Lobbies

A lobby is a waiting queue with its own autostart countdown and match.
Players join the default lobby on login and keep it until they join
another lobby or leave the server. Lobby membership is not saved across
restarts.

```yaml
advanced:
  lobbies:
    default-lobby-id: 0
    join-teleports-to-lobby: true
    announce-lobby-changes: ALL
    caps:
      speedrunner: -1
      hunter: -1
```

`default-lobby-id` is the lobby players join on login; set it negative
to leave joining players lobby-less until they join one manually.
`join-teleports-to-lobby` teleports players to their lobby on join
(see [Lobby Teleports](lobby-system/boundaries.md#lobby-teleports));
pass `-notp` to skip that teleport once.
`announce-lobby-changes` (`ALL`, `SELF`, `MEMBERS`, `NONE`) picks who
hears join and leave lines; lobby 0 moves never announce.
`disable-player-collisions` lets everyone in the lobby world pass
through each other, members and visitors alike.

Move players between lobbies with:

```text
/manhunt lobby join <selector> <lobby-id> [role]
/manhunt lobby leave [selector]
```

The join role defaults to `none`. Players logging in while a match runs
but with nowhere to wait skip the lobby and join the newest running
match as spectators instead. New to lobbies? Start with the
[Lobby System](lobby-system/index.md).

## Queue Caps

`caps` limits how many may queue per role per lobby (`-1` means no
limit). Caps apply to queueing, bypass with `-f` (`-force`), and
`quickstart` ignores them. Lowering a cap never removes already-queued
players. See [Multi-Lobby Boundaries](lobby-system/boundaries.md) for
the full queue picture.

## Starting and Stopping

- `/manhunt start [lobby-id]` starts one lobby queue. Without an id it
  uses your lobby, or the default lobby from the console.
- `/manhunt quickstart` works the same way, in the sender's lobby.
- Autostart runs one countdown per eligible lobby instead of one global
  countdown.
- `/manhunt end [id]` cancels one match (yours, or pass an id; the
  console must pass one). `-i` (`-immediate`) skips the end delay.
- `/manhunt status [id|all]` shows one roster, or every running match
  plus every populated lobby with `all`.

Match ids are the incrementing numbers shown by `status all`. A match's
cell index works as an alias wherever an id is accepted.

## Mid-match Setplayer

`advanced.lobbies.mid-match-setplayer` decides what `setplayer` does
when the target's lobby has a live match (engine on only):

- `HOLD` assigns the role for the next game and does nothing further.
- `JOIN_ANY` joins any role straight into the running match.
- `JOIN_SPECTATORS` joins spectators mid-match and holds every other
  role.
- `SUBLOBBY` queues the role for the next sublobby (see below).
- `SUBLOBBY_WITH_SPECTATORS` (default) queues like `SUBLOBBY`, except
  spectators join the oldest running sublobby.

Held players count against queue caps; joined players ignore them.
Queue counts only cover queued players, never anyone already in a
live match.

## Mid-match Join Timing

`advanced.lobbies.join-timing` decides what `NONE`, `SPECTATOR`, and
`AFK` players wait out when they enter a live match through
`/game join`, `pswitch`, or a joining `setplayer`. Players already in
the game are never affected, and roles are lobby specific (a runner
from another lobby counts as `NONE` in the target lobby):

- `WAIT` (default) holds the joiner for the larger of the joined
  role's headstart and respawn durations, or joins instantly when
  both are disabled.
- `INSTANT` always joins immediately.

The hold releases into play without a respawn (no teleport, no heal,
no triggers) and cancels cleanly on leave or match end.

## Sublobbies

Under the sublobby policies (default), a lobby never hosts a match
directly: every match, the first included, runs as a numbered child
(`L2-0` is the first match in lobby 2). The queue keeps gathering in
the parent while children play, and each `start` launches the next
child from queued players only. When a child ends, its spectators move
to the oldest running sibling; the last match's spectators return to
the lobby as `none`. Switch the policy to `HOLD` for one match per
lobby with no children.

## Joining and Leaving a Running Match

`/manhunt game join <id> [role] [selector]` adds players to a live
match (default `spectator`); `/manhunt game leave [id] [selector]`
removes them. Living participants confirm leaving with a second run
within 10 seconds, drop their gear, and land wherever
`settings.game-leave.destination` points. If a leave ever empties a
side, the other side wins immediately. See
[Commands](commands.md#joining-and-leaving-a-running-match) for the
full syntax.

## Borders and Enforcement

No vanilla border is ever set. Every match with borders on is confined
by its own pseudo-border (see
[World Border](../configuration/world-engine/borders.md)); with
borders off, strays are removed from the match instead. Wandering into
the lobby world mid-match also removes the player from the match.

## Portals and End Dimensions

Nether portals stay inside the match: destinations are clamped into the
match's slice of the shared nether. End portals lead to the match's own
end dimension from the shared pool, and leaving the end returns to the
match cell. See
[End Dimensions](../configuration/world-engine/end-dimensions.md).

## Preloading

The engine keeps ready cells buffered so matches start without waiting.
See
[Preloading Cells](../configuration/world-engine/preloading-cells.md).
