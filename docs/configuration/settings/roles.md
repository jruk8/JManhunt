# Roles

Every player holds one role: `hunter` and `speedrunner` play the match,
`spectator` watches it (always in spectator mode, announced and titled
like everyone else), `afk` waits it out in the lobby untouched, and `none`
means unassigned — left alone like `afk` unless Quick Start is forced.
If a role change ever empties the hunters or the speedrunners mid-match,
the other side wins immediately.

Under `settings.announce-role-changes` (default `false`), changes between
a passive role (`none`, `afk`, `spectator`) and an active one (`hunter`,
`speedrunner`) are announced to the player's lobby mates who are not in
a live match — e.g. `Aria is now a Hunter.` Only the active side is ever
named; changes within the same class stay silent.

Roles also mirror to vanilla scoreboard teams (`HUNTER`, `SPEEDRUNNER`,
`SPECTATOR`; `none` and `afk` sit in no team), repaired on every role
change and login, so datapacks and
[custom modifiers](../modifiers.md#targeting-sides-with-selectors) can
target sides with selectors like `@a[distance=..15,team=HUNTER]`.

Under `settings.roles`, you can control how player roles are assigned and
reset around the lifecycle of a match.

```yaml
settings:
  roles:
    reset-on-game-end:
      enabled: false
    reset-on-leave:
      enabled: true
    turn-nones-spectator:
      enabled: false
```

## Reset on Game End

`reset-on-game-end`, when enabled, returns every queued hunter and
speedrunner to `NONE` as soon as a match finishes, so players must re-queue
before the next one.

## Reset on Leave

`reset-on-leave`, when enabled, resets a player back to `NONE` if they leave
the server while their lobby has no running match. If their lobby has a game
in progress, the reset is deferred until that game ends instead.

## Turn Nones Spectator

`turn-nones-spectator` controls what happens to `NONE`-role players
whenever the plugin's automatic gamemode assignment is active:

- `true`: `NONE` players are put into spectator mode.
- `false`: `NONE` players keep their gamemode and stay put, like `afk`.

When the world engine is enabled and this setting is `true`, `NONE`
players are teleported to the match cell center together with the
participants when a match starts, so they can spectate the match instead
of waiting in the lobby. They return to the lobby when the match ends.
When this setting is `false`, they are not teleported to the cell. `AFK`
players are always left alone either way.

# Match Leave Destination

Under `settings.game-leave`, you decide where players go when they leave a
running match, voluntarily or automatically:

```yaml
settings:
  game-leave:
    destination: SPECTATOR
```

`SPECTATOR` keeps them at the match as a watcher; `LOBBY` sends them back
to their lobby as `NONE`. See [Joining and Leaving](../../commands.md#joining-and-leaving-a-running-match).

# Anti-Spawn-Camp

Under `settings.anti-spawn-camp`, a rolling kill limit punishes campers:
by default, three kills by one attacker on the same victim within 120
seconds triggers it. Every punishment is broadcast to the whole server.

```yaml
settings:
  anti-spawn-camp:
    enabled: true
    kills: 3
    window-seconds: 120.0
    punishment: KILL
```

`KILL` slays the camper through a normal death; `GEAR-WIPE` instead clears
their armor, offhand, and main hand. One kill before the limit, the
killer gets a private warning naming their victim.

# Friendly Fire

Under `settings.friendly-fire`, you can control whether players of the same
role can damage each other once a match is underway.

```yaml
settings:
  friendly-fire:
    speedrunner: true
    hunter: true
```

Friendly fire is always disabled during the pre-start window, regardless of
these settings.

## Speedrunner Friendly Fire

`speedrunner` toggles whether speedrunners can damage other speedrunners in
matches with more than one.

## Hunter Friendly Fire

`hunter` toggles whether hunters can damage other hunters.

# Respawn & Lives

Under `settings.respawn`, each role configures its own respawn delay and how
many lives it has before elimination.

```yaml
settings:
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

## Respawn Delay

When a role's `enabled` is true, a dead player of that role must wait out
`delay-seconds` in spectator mode before respawning, rather than respawning
immediately. Set `delay-seconds` to `-1` for an immediate respawn. Delayed
respawns are announced to the match when the timer starts and when the
player returns.

## Lives

`lives` sets how many lives each role gets before that player is eliminated
for the rest of the match. Set either value to `-1` for unlimited lives. By
default, speedrunners get a single life while hunters have unlimited lives.

# Invulnerability

Under `settings.invulnerability`, you can control when players are made
immune to damage outside of normal match rules.

```yaml
settings:
  invulnerability:
    on-game-end:
      enabled: true
    none-players:
      enabled: true
```

`on-game-end` refers to the period after a game ends, but where feedback 
(like chat statistics) has not yet finished broadcasting. 

`none-players` refers to players outside the match (`NONE`, `AFK`, and
spectators). This makes players who are not in a game immune to damage.
Command kills (`/kill`) still go through; only the pre-start window blocks
those, to avoid glitches.
