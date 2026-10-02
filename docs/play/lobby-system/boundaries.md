# Multi-Lobby Boundaries

> For: intermediate admins running more than one lobby.

One lobby world can hold several lobbies. Each lobby gets a teleport
point (where its players land) and an optional boundary box (a region
that auto-joins walkers). Both live in
`settings/world-engine/lobby-config.yml`, one entry per lobby id:

```yaml
lobbies:
  '0':
    lobbytp:
      x: 0.0
      y: 65.0
      z: 0.0
      yaw: 0.0
      pitch: 0.0
    bounds:
      pos1: null
      pos2: null
```

The file generates with defaults on first load and reloads with
`/manhunt reload`. It is not editable through `/manhunt config`: use
the commands below instead.

## Lobby Teleports

Stand where players should land and run:

```text
/manhunt worldengine lobbyconfig setlobbytp <lobby-id>
```

You can also pass coords directly
(`setlobbytp <lobby-id> <x y z yaw pitch>`), which works from the
console too. The world is never stored: teleports always land in the
configured lobby world.

When a lobby has no teleport of its own, players fall back to the
lowest lobby id that has one. When no teleport exists anywhere, or the
lobby world itself is missing, players hear that no lobby exists and
should contact an administrator.

## Boundary Boxes

A boundary box auto-joins walkers: a player who steps into a lobby's
box while in the lobby world joins that lobby with role `none`, no
teleport needed since they are already there. Players already in that
lobby, and players in a running match, are left alone. Where boxes
overlap, the box whose midpoint is nearest wins.

Record two opposite feet-block corners, then store them:

```text
/manhunt worldengine lobbyconfig pos1
/manhunt worldengine lobbyconfig pos2
/manhunt worldengine lobbyconfig setbounds <lobby-id>
```

Tab completion suggests the next id without bounds. Overwriting
existing bounds needs the command run twice within 10 seconds, and
bounds identical to another lobby's box are refused. Remove a whole
entry with `deletelobby <lobby-id>`, also run twice to confirm. That
only deletes the stored entry, never the live lobby or its players.

Walking out of every box is governed by
`advanced.lobbies.bounds.exit-behavior`:

- `KEEP_IN_LOBBY` (default) keeps the membership, so the walk-out is
  ignored.
- `EXIT_LOBBY` leaves the lobby, moving members to
  `advanced.lobbies.bounds.exit-lobby-id` (lobby-less by default),
  unless the destination lands straight inside another lobby's box, in
  which case they join that lobby instead.

Either way this only fires for members whose lobby has complete
bounds, and never for players in a live match.

## More Than One Lobby

Lobby ids run from `0` up. Unknown ids are created on join and empty
lobbies are deleted on leave. Move players between lobbies with:

```text
/manhunt lobby join <selector> <lobby-id> [role]
/manhunt lobby leave [selector]
```

The join role defaults to `none`. Pass `-notp` to skip the lobby
teleport for one join. Players join the default lobby on login (see
`advanced.lobbies.default-lobby-id`); set it negative to leave joining
players lobby-less until they join one manually.

Queue caps limit how many players may queue per role per lobby:

```yaml
advanced:
  lobbies:
    caps:
      speedrunner: -1
      hunter: -1
```

`-1` means no limit. Caps apply to queueing and are bypassed with
`-f` (`-force`); `quickstart` ignores caps. Lowering a cap never
removes already-queued players.

Each lobby runs its own match at the same time: see
[Concurrent Matches](../../multi-instance.md).

## Lobby Upkeep

Arrivals are healed and fed at once, and everyone inside is topped up
on an interval. Both toggles default to on:

```yaml
care:
  heal:
    enabled: true
  saturate:
    enabled: true
  interval: 15
```

Players who fall into the void in the lobby world pop back at their
lobby teleport (or lobby 0 when theirs is unset) instead of dying. This
never applies in the game world, and `lobby-world-void-rescue` in the
same file turns it off.

The same file protects the lobby world with `protected` (on by
default): breaking and placing blocks, interacting, damaging entities,
and losing hunger all need `jmanhunt.editlobby`. Give that node to
builders, or turn `protected` off while setting the lobby up by hand.
