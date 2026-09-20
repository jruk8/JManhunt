# Roles

Under `settings.roles`, you can control how player roles are assigned and
reset around the lifecycle of a match.

```yaml
settings:
  roles:
    reset-on-game-end:
      enabled: false
    reset-on-leave:
      enabled: true
    none-gamemode-spectator:
      enabled: true
```

## Reset on Game End

`reset-on-game-end`, when enabled, returns every queued hunter and
speedrunner to `NONE` as soon as a match finishes, so players must re-queue
before the next one.

## Reset on Leave

`reset-on-leave`, when enabled, resets a player back to `NONE` if they leave
the server while their lobby has no running match. If their lobby has a game
in progress, the reset is deferred until that game ends instead.

## None Gamemode

`none-gamemode-spectator` controls what happens to `NONE`-role players
whenever the plugin's automatic gamemode assignment is active:

- `true`: `NONE` players are put into spectator mode.
- `false`: `NONE` players keep whatever gamemode they already had.

When the world engine is enabled and this setting is `true`, `NONE`
players are teleported to the match cell center together with the
participants when a match starts, so they can spectate the match instead
of waiting in the lobby. They return to the lobby when the match ends.
When this setting is `false`, they are not teleported to the cell.

# Friendly Fire

Under `settings.friendly-fire`, you can control whether players of the same
role can damage each other once a match is underway.

```yaml
settings:
  friendly-fire:
    speedrunner: false
    hunter: false
```

Friendly fire is always disabled during the pre-start window, regardless of
these settings.

## Speedrunner Friendly Fire

`speedrunner` toggles whether speedrunners can damage other speedrunners in
matches with more than one.

## Hunter Friendly Fire

`hunter` toggles whether hunters can damage other hunters.

# Hunter Respawn & Lives

Under `settings.hunter-respawn`, you can delay hunter respawns and set how
many lives each role has before elimination.

```yaml
settings:
  hunter-respawn:
    enabled: false
    delay-seconds: 60
    lives:
      speedrunner: 1
      hunter: -1
```

> **Note:** despite the section's name, `lives` configures both roles, not
> just hunters.

## Respawn Delay

When `enabled`, a hunter who dies must wait out `delay-seconds` before
respawning, rather than respawning immediately. Set `delay-seconds` to `-1`
for an immediate respawn.

## Lives

`lives` sets how many lives each role gets before that player is eliminated
for the rest of the match. Set either value to `-1` for unlimited lives. By
default, speedrunners get a single life while hunters have unlimited lives.

# Start Debuffs

Under `settings.start-debuffs`, every hunter can be given a set of potion
effects the moment the game begins, useful for softening a hunter's
advantage right out of the gate.

```yaml
settings:
  start-debuffs:
    enabled: false
    effects:
      SLOWNESS:
        seconds: 10.0
        amplifier: 1
      WEAKNESS:
        seconds: 10.0
        amplifier: 0
```

## Effects

Each entry under `effects` is a [Bukkit `PotionEffectType`](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/potion/PotionEffectType.html)
name, with:

- `seconds`: how long the effect lasts.
- `amplifier`: the effect's level, zero-indexed (`0` = level I, `1` = level
  II, and so on).

Add or remove entries freely. Only the effects listed are applied.

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

`none-players` refers to players with the `NONE` role. This makes players
who are not in a game immune to damage.
