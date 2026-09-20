# Concurrent Matches

With the world engine enabled, JManhunt runs several matches at the same
time, one per lobby. Each match gets its own world cell, compass tracking,
statistics, border confinement, and end dimension, so matches never see or
interfere with each other. With the world engine off, everyone shares lobby
0 and only one match runs at a time.

## Lobbies

A lobby is a waiting queue with its own autostart countdown and match.
Players join the default lobby when they log in and keep it until they join
another lobby or leave the server. Lobby membership is not saved across
restarts.

```yaml
lobbies:
  default-lobby-id: 0
  join-teleports-to-lobby: false
  caps:
    speedrunner: -1
    hunter: -1
```

`default-lobby-id` is the lobby players join on login. Set it to a negative
value to leave joining players lobby-less until they join one manually.
`join-teleports-to-lobby`, when true, teleports players to their lobby
location when they join it (see
[Cells & Spawns](configuration/world-engine/cells-spawns.md#lobby-locations)).

Move players between lobbies with:

```text
/manhunt lobby join <selector> <lobby-id> <role>
/manhunt lobby leave <player> <lobby-id>
```

Lobby ids run from `0` to `2147483647`. Unknown ids are created on join and
empty lobbies are deleted on leave.

## Queue Caps

`caps` limits how many players may queue for each role per lobby. `-1`
means no limit. Caps apply to queueing (`setplayer`, `lobby join`, and
`quickstart`) and are bypassed with `-f` (`-force`). Lowering a cap never
removes already-queued players, and joining a running match ignores caps.

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

## Joining a Running Match

`/manhunt joingame <selector> <id> <role>` adds players to a live match as
`hunter`, `speedrunner`, or `none`. Joiners move to the match's lobby, are
teleported into its cell, and receive lives, statistics, and a compass.
Players already in a live match are skipped, and matches in their end delay
cannot be joined.

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

Extra end dimensions accumulate over time. `end-cell-prune-when` controls
cleanup: `ALWAYS` deletes one extra dimension per match start until only the
cell buffer remains, while `NEVER` (the default) keeps them. Busy servers
should use `ALWAYS`.

```yaml
world-engine:
  end-cell-prune-when: NEVER
```

## Preloading

The engine keeps ready cells buffered so matches start without waiting on
allocation or chunk generation. See
[Preloading Cells](configuration/world-engine/pregenerating-cells.md).

## Debug Output

`/manhunt debug [on|off]` toggles terse lifecycle lines (cell fetches,
buffer fills, match starts and ends, end-cell operations, border mode
changes, and portal reroutes) for yourself, or for the console when run
from it. `debug.enabled` in `config.yml` sets the console default.
