# Concurrent Matches

With the world engine enabled, JManhunt runs several matches at the same
time, one per lobby. Each match gets its own world cell, compass tracking,
statistics, border confinement, and end dimension, so matches never see or
interfere with each other. With the world engine **off**, everyone shares lobby
0 and only one match runs at a time.

## Lobbies

A lobby is a waiting queue with its own autostart countdown and match.
Players join the default lobby when they log in and keep it until they join
another lobby or leave the server. Lobby membership is not saved across
restarts.

```yaml
lobbies:
  default-lobby-id: 0
  join-teleports-to-lobby: true
  announce-lobby-changes: ALL
  caps:
    speedrunner: -1
    hunter: -1
```

`default-lobby-id` is the lobby players join on login. Set it to a negative
value to leave joining players lobby-less until they join one manually.
`join-teleports-to-lobby`, when true, teleports players to their lobby
location when they join it (see
[Cells & Spawns](configuration/world-engine/cells-spawns.md#lobby-teleports-bounds)).
Pass `-notp` to skip that teleport for one join.
`announce-lobby-changes` picks who hears the join and leave lines for
positive lobbies: `ALL` (the moved player plus lobby members), `SELF`,
`MEMBERS`, or `NONE`. Lobby 0 moves never announce.

Move players between lobbies with:

```text
/manhunt lobby join <selector> <lobby-id> [role]
/manhunt lobby leave [selector]
```

The join role defaults to `none`. Players logging in while a match runs
but with nowhere to wait (world engine off, or no lobby set) skip the
lobby and join the newest running match as spectators instead.

Lobby ids run from `0` to `2147483647`. Unknown ids are created on join and
empty lobbies are deleted on leave. New to this? `/manhunt worldengine tpto
lobbyworld`, run twice, generates a lobby world and points lobby 0 at it;
see [World Engine](configuration/world-engine.md).

## Queue Caps

`caps` limits how many players may queue for each role per lobby. `-1`
means no limit. Caps apply to queueing (`setplayer` and `lobby join`)
and are bypassed with `-f` (`-force`); `quickstart` ignores caps.
Lowering a cap never removes already-queued players, and joining a
running match ignores caps.

## Starting and Stopping

- `/manhunt start [lobby-id]` starts a match for one lobby queue. Without an
  id it uses your lobby, or the default lobby from the console.
- `/manhunt quickstart` works the same way, in the sender's lobby.
- Autostart runs one countdown per eligible lobby instead of one global
  countdown.
- `/manhunt end [id]` cancels one match. Without an id it cancels your own
  match; the console must pass an id. The `-i` (`-immediate`) flag skips the
  end delay intermission.
- `/manhunt [id|all]` shows one match roster, or every running match with
  `all`. Without an argument it shows your own match, else your lobby queue.

Match ids are the incrementing numbers shown by `status all`. A match's
world-engine cell index works as an alias wherever an id is accepted.

## Mid-match Setplayer

`lobbies.mid-match-setplayer` decides what `setplayer` does when the
target's lobby has a live match. It only applies with the world engine
on; otherwise the in-match block stays.

- `HOLD` assigns the role for the next game, tells the player a match is
  in progress, and does nothing further.
- `JOIN_ANY` joins any role straight into the running match.
- `JOIN_SPECTATORS` joins spectators mid-match and holds every other
  role.
- `SUBLOBBY` (default) queues the role for the next sublobby; see
  [Sublobbies](#sublobbies).

Held players count against queue caps; joined players ignore them, like
`game join`.

## Sublobbies

With the default `SUBLOBBY` policy, a lobby never hosts a match
directly: it only orchestrates sublobbies, and every match (the first
included) runs as one. Sublobbies are numbered per lobby from zero and
shown as `L{lobby-id}-{sublobby-id}` (so the first match in lobby 2 is
`L2-0`); ids are never reused within a run.

Sublobbies run one live match per parent lobby, exactly like direct
hosting: the queue keeps gathering in the parent while its sublobby
plays. `setplayer` into a live sublobby queues the role for the next
one, and status output shows the tag (`L2-0|G5` in `status`,
`L2-0|G5`-style entries in `status all`). Switch the policy to `HOLD`
for plain direct hosting.

## Joining and Leaving a Running Match

`/manhunt game join <id> [role] [selector]` adds players to a live match,
defaulting to `spectator`. Joiners move to the match's lobby, are
teleported into its cell, and receive lives, statistics, and a compass.
Players already in a live match are skipped, and matches in their end
delay cannot be joined.

`/manhunt game leave [id] [selector]` removes them again. Living hunters
and speedrunners confirm with a second run within 10 seconds, drop their
gear, and land wherever `settings.game-leave.destination` points
(`SPECTATOR` by default, `LOBBY` to return to the queue as `none`). If a
role change ever leaves a side empty (a last leaver included), the other
side wins immediately.

## Match Area Enforcement

A participant who leaves their cell, or who wanders into the lobby world
mid-match, is removed from the match automatically and reset
match-end-style, for themselves only. This is the backstop for servers
running without borders; where borders are on, the rubber-band still
brings stray players back instead.

## Borders

A lone match uses the real world border from
[Cells & Spawns](configuration/world-engine/cells-spawns.md). Once a second
match starts, the real border drops and every match is confined by a
per-instance pseudo-border instead: players outside their cell are pulled
back in and take the configured border damage past the damage buffer.
Spectators bypass it like the vanilla border. When concurrency drops back to
one match, the survivor gets the real border again.

## Portals and End Dimensions

Nether portals stay inside the match: destinations are clamped into the
match's slice of the shared nether, so concurrent matches never meet there.
End portals lead to the match's own end dimension, and leaving the end
returns to the match cell.

Each match's end dimension is deleted when the match ends. Anything
left behind by a crash is cleaned up on the next startup.

## Preloading

The engine keeps ready cells buffered so matches start without waiting on
allocation or chunk generation. See
[Preloading Cells](configuration/world-engine/pregenerating-cells.md).

## Debug Output

`/manhunt debug [on|off]` toggles terse lifecycle lines (cell fetches,
buffer fills, match starts and ends, end-cell operations, border mode
changes, and portal reroutes) for yourself, or for the console when run
from it. `debug.enabled` in `config.yml` sets the console default.
