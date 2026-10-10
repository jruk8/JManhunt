---
icon: material/account-multiple
---

# Roles

> For: intermediate admins tuning rosters, lives, and fair play.

Every player holds one role: `hunter` and `speedrunner` play the
match, `spectator` watches it, `afk` waits it out in the lobby
untouched, and `none` means unassigned (left alone like `afk`, except
Quick Start always includes them). If a leave ever empties a side
mid-match, the other side wins immediately; a forced `setplayer`
change that empties a side cancels the match instead.

Under `settings.server.announce-role-changes` (default off), changes
between a passive role (`none`, `afk`, `spectator`) and an active one
(`hunter`, `speedrunner`) are announced to lobby mates outside live
matches. Only the active side is ever named.

Roles also mirror to vanilla scoreboard teams (`HUNTER`,
`SPEEDRUNNER`, `SPECTATOR`), repaired on every role change and login,
so datapacks and
[custom modifiers](../modifiers/behaviors.md#targeting-sides-with-selectors)
can target sides with selectors.

## Role Resets

How roles reset around the match lifecycle:

```yaml
settings:
  players:
    roles:
      reset-on-game-end:
        enabled: true
```

`reset-on-game-end` returns every queued hunter and speedrunner to
`NONE` when a match finishes, so players re-queue for the next one.
`NONE` players keep their mode and wait in the lobby; use lobby
queues to move them.

## Name Colors

Sides are visible at a glance: hunters red, speedrunners green,
everyone else default, in matches and in the lobby alike. This uses
vanilla team colors with zero dependencies, and LuckPerms prefixes
apply on top untouched (see
[LuckPerms](../../advanced/luckperms.md)).

```yaml
settings:
  players:
    name-colors:
      enabled: true
```

Turn `enabled` off to leave every name default. Team membership is
unaffected either way, so selectors keep working.

## Match Leave Destination

Where players go when they leave a running match, voluntarily or
automatically:

```yaml
settings:
  match:
    game-leave:
      destination: SPECTATOR
```

`SPECTATOR` keeps them at the match as a watcher; `LOBBY` sends them
back to their lobby as `NONE`. See [Joining and
Leaving](../../play/commands.md#joining-and-leaving-a-running-match).

The same destination routes players whose disconnect grace expires:
their held gear (headstart holds, respawn waits) drops where the hold
began, and when they rejoin they arrive with an empty inventory,
either spectating or back in the lobby. Rejoining inside the grace
window keeps everything and resumes the hold. Held players always
watch with a completely empty inventory; their gear returns when
they spawn.

## Anti-Spawn-Camp

A rolling kill limit that punishes campers: by default, three kills by
one attacker on the same victim within 90 seconds triggers it. Every
punishment broadcasts to the whole server.

```yaml
settings:
  server:
    anti-spawn-camp:
      enabled: true
      kills: 3
      window-seconds: 90.0
      first-punishment: GEAR-WIPE
      kill-on-second-time: true
      monitored-roles:
        - SPEEDRUNNER
```

The first offense applies `first-punishment`: `KILL` slays the camper
through a normal death, while `GEAR-WIPE` clears their armor, offhand,
and main hand. With `kill-on-second-time` on, any further offense in
the same match kills the camper. One kill before the limit, the killer
gets a private warning naming their victim.

`monitored-roles` lists which attacker roles the guard punishes. An
empty list switches the guard off entirely.

## Friendly Fire

Whether players of the same role can damage each other once a match is
underway. Always disabled during the pre-start window.

```yaml
settings:
  players:
    friendly-fire:
      speedrunner: true
      hunter: true
      broadcast-kills: true
```

`speedrunner` and `hunter` toggle per-side damage.
`broadcast-kills` broadcasts a mocking line to the origin lobby
whenever a player kills a teammate, picked from
`game.friendly-fire-1/2/3` in `messages.yml`. Cross-team kills and
suicides never trigger it.

## WorldEdit Wand

Hunters and speedrunners cannot select WorldEdit regions mid match:
`advanced.misc.interop.block-worldedit-wand-in-match` (default on)
denies wooden axe clicks for them while their match is active, with
no WorldEdit install needed. Only the selection is blocked; breaking
and stripping blocks still works, and spectators and lobby players
are unaffected. Turn it off if your players need the wand during
matches.

## Respawn & Lives

Each role configures its own respawn delay and lives before
elimination:

```yaml
settings:
  players:
    respawn:
      hunter:
        enabled: true
        delay-seconds: 15
        lives: -1
      speedrunner:
        enabled: false
        delay-seconds: 60
        lives: 1
```

When a role's `enabled` is true, the dead wait out `delay-seconds` in
spectator mode before respawning; `-1` respawns immediately. Delayed
respawns announce to the match when the timer starts and when the
player returns.

`lives` sets lives before elimination (`-1` for unlimited). By
default, speedrunners get a single life while hunters have unlimited
lives. Elimination moves the player to the spectator role with the
toolbar; status shows a skull memorial under their former role.

## Invulnerability

When players are immune to damage outside normal match rules:

```yaml
settings:
  players:
    invulnerability:
      on-game-end:
        enabled: true
      none-players:
        enabled: true
```

`on-game-end` covers the window after a game ends while end feedback
still broadcasts. `none-players` covers everyone outside the match
(`NONE`, `AFK`, spectators). Command kills (`/kill`) still go through;
only the pre-start window blocks those.

## Spectator Toolbar

Spectators get a hotbar toolbar while in fake spectator mode:

```yaml
settings:
  players:
    spectator:
      toolbar:
        layout: "cp##s###b"
        lock-on: true
        tp-distance: 25
        snowball:
          enabled: true
          cooldown-seconds: 8
```

`layout` maps hotbar slots 0 to 8 (`c` match browser, `p` player
teleporter, `s` snowball, `b` back to lobby, `#` empty) and must be
exactly 9 characters. `lock-on` follows teleported-to players within
`tp-distance` blocks. See
[Spectating](../../play/spectating.md) for the full behavior.
