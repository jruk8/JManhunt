# Settings

**Settings** are a core part in configuring JManhunt. Many default settings are toggled
off to keep the game simple for new users. However, everything included is fully functional
and designed to enhance the experience.

All command examples are the default config settings. Some defaults may be outdated, in
which case you should refer to the latest version of `config.yml` in the 
[GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

# Compass

The compass is an integral component for manhunts. JManhunt's compass supports dimensional 
tracking, min/max distance, and support for both hunters and speedrunners.

Settings for the compass are categorized under `settings.compass`:
```yaml
settings:
  compass:
```

## Target Selection

**Target Selection** determines the **target** of the compass. The target refers to a live
player of the opposite role.

**Dimensional tracking** means tracking is enabled only when the target is in the same dimension.
While this means players may not track targets that are away, it also means that the compass
tracks the player's last seen location in the current dimension. This is useful for locating
portals where the target left the current dimension.

**Online tracking** means only online players are actively tracked. However, a target's last
seen location is still recorded when they disconnect.

In any case, targets who are **active in the same dimension** are prioritized over inactive
ones.

## Given to Roles

Under `settings.compass.given-to`, you can configure which roles receive a
compass when a match starts (and on respawn):

```yaml
given-to:
  hunters: true
  speedrunners: false
```

`hunters` is enabled by default, `speedrunners` is disabled by default.
Hunters always track speedrunners and speedrunners always track hunters.

If you'd like to disable the compass entirely, set both to false.

## Inventory Lock

Under `settings.compass.inventory-lock`, you can configure whether the compass
must stay in the player's inventory. This allows the player to change its slot
but not drop it or put it to a container. Dropping on death is controlled by the
[Drop on Death](#drop-on-death) setting.

```yaml
must-be-inventory:
  enabled: true
```

## Drop on Death

Under `settings.compass.drop-on-death`, you can configure whether the compass
is dropped on death. This is useful for allowing speedrunners to pick up the
hunter's compass after death, or for allowing hunters to pick up the speedrunner's
compass after death.

Only one compass may exist in the inventory at a time. Duplicate ones are removed.

```yaml
drop-on-death:
  enabled: false
```

## Refresh Time

Under `settings.compass.refresh-interval`, you can configure how often the
compass should refresh its target. Set to -1 to disable automatic refreshing.

```yaml
refresh-interval: 10.0        # in seconds
```

Under `settings.compass.right-click`, you can configure right-clicking the
compass to refresh it. You may enable it, in which case right-clicking will
refresh the compass if `right-click-cooldown` time has elapsed since last
click.

```yaml
right-click:
  refresh-on-right-click: false
  right-click-cooldown: 3.0      # in seconds
```

## Tracking Distance

### Minimum Distance

Under `settings.compass.disable-when-nearby`, you can configure the minimum
distance at which the compass stops tracking. This is useful for making _camping_
a viable strategy for speedrunners.

```yaml
disable-when-nearby:
  enabled: true
  # The flat distance in blocks at which the compass stops tracking.
  # Default: 25
  distance: 25.0
```

### Maximum Distance

Under `settings.compass.tracking-distance`, you can limit how far the
compass can track a player. When the target is beyond this distance, the
compass points at a random distance and shows an out-of-range actionbar.

```yaml
settings:
  compass:
    tracking-distance: -1.0    # radius in blocks
```

Set to a negative value like `-1.0` for unlimited distance. Any positive value
over zero enables this feature.

# Autostart

Under `settings.autostart`, you can have JManhunt automatically begin a match
as soon as enough players have queued up, instead of requiring an admin to
run `/manhunt start` manually.

```yaml
settings:
  autostart:
    enabled: false
    countdown-seconds: 60
```

Autostart triggers once at least one hunter and one speedrunner are queued.

## Countdown

`countdown-seconds` is how long JManhunt waits, once the queue becomes
eligible, before running `/manhunt start` on its own. Set to `0` to start
immediately with no countdown at all.

# Start on Speedrunner Damage

Under `settings.start-on-speedrunner-damage`, the match will include a **pre-start**
window that lasts until a speedrunner hits a hunter. This gives speedrunners
a moment to get their bearings before hunters are "let loose."

Players in the pre-start window are invulnerable and may not deal any damage, except for 
the speedrunners' first hit.

```yaml
settings:
  start-on-speedrunner-damage:
    enabled: true
    delay-seconds: 30
    on-expire: FORCE_START
    start-with-adventure-mode: true
```

## Pre-Start Timeout

`delay-seconds` is how long JManhunt waits for that first hit to land before
falling back to the behavior configured in `on-expire`. Values below `5` are
clamped up to `5` seconds. Set to `-1` to wait indefinitely for a hit.

## On Expire

`on-expire` decides what happens if the timeout above passes with no hit:

- `CANCEL` — abort the match without recording any stats.
- `FORCE_START` — start the match anyway.

## Adventure Mode Lock

`start-with-adventure-mode`, when enabled, puts every participant in
adventure mode during the pre-start window so nobody can break blocks while
waiting for the starting hit. Everyone is restored to survival the instant
the game actually begins.

# Start Delay

Under `settings.start-delay`, hunters can be given a head start disadvantage:
they sit in spectator mode for a set delay while speedrunners get to move and
gear up unimpeded.

```yaml
settings:
  start-delay:
    enabled: false
    delay-seconds: 30
```

If [Start on Speedrunner Damage](#start-on-speedrunner-damage) is also
enabled, this delay's countdown doesn't begin until the speedrunner lands
that first hit — so hunters stay in spectator through the pre-start window,
then continue waiting out this delay on top of it.

## Delay Length

`delay-seconds` sets the head start length in seconds. Values of `0` or below
disable the delay entirely.

# Win Conditions

Under `settings.win-conditions`, you can configure what constitutes a win in the
game. Multiple conditions can be enabled at once. Make sure at least one is
enabled.

```yaml
settings:
  win-conditions:
    exitEnd:
      enabled: true
    surviveTime:
      enabled: false
      time: 3600.0
    acquireItem:
      enabled: false
      item: "minecraft:netherite_ingot"
    reachAdvancement:
      enabled: false
      advancement: "minecraft:story/enter_the_nether"
```

## Exit the End

`exitEnd` is the classic manhunt win condition: speedrunners win by entering
the End and then returning to the Overworld. Enabled by default.

## Survive Time

`surviveTime` lets speedrunners win simply by staying alive for a set
duration, given by `time` in seconds.

## Acquire Item

`acquireItem` grants the win the moment a speedrunner obtains the item named
in `item`, given as a namespaced key (e.g. `minecraft:netherite_ingot`).

## Reach Advancement

`reachAdvancement` grants the win when a speedrunner completes the
advancement named in `advancement`, given as a namespaced key (e.g.
`minecraft:story/enter_the_nether`).

# Roles

*Proposed grouping — see note below.*

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

> **Note:** in the current `config.yml` these three settings live as separate
> top-level keys — `reset-roles-on-game-end`, `reset-role-on-leave`, and
> `set-none-gamemode-spectator`. They're documented together here under a
> proposed `settings.roles` namespace since they all govern the same thing
> (role assignment/reset), which makes them easier to find as a set. Adjust
> the paths below to match whichever layout you end up shipping.

## Reset on Game End

`reset-on-game-end`, when enabled, returns every queued hunter and
speedrunner to `NONE` as soon as a match finishes, so players must re-queue
before the next one.

## Reset on Leave

`reset-on-leave`, when enabled, resets a player back to `NONE` if they leave
the server while no game is running. If a game is currently in progress, the
reset is deferred until that game ends instead.

## None Gamemode

`none-gamemode-spectator` controls what happens to `NONE`-role players
whenever the plugin's automatic gamemode assignment is active:

- `true` — `NONE` players are put into spectator mode.
- `false` — `NONE` players keep whatever gamemode they already had.

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
> just hunters — worth keeping in mind if this section ever gets split up.

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
effects the moment the game begins — useful for softening a hunter's
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

- `seconds` — how long the effect lasts.
- `amplifier` — the effect's level, zero-indexed (`0` = level I, `1` = level
  II, and so on).

Add or remove entries freely — only the effects you list are applied.

# Game Boosts

*Refactored from `world-engine` and `loot-tables` — see note below.*

Under `settings.game-boosts`, a set of settings that skew world generation
and loot odds in the speedrunners' favor, without touching any actual
gameplay rules.

```yaml
settings:
  game-boosts:
    nether-structures:
      enabled: false
    overworld-structures:
      enabled: false
    custom-piglin-barter: false
```

> **Note:** `nether-structures` and `overworld-structures` currently live
> under `settings.world-engine` in `config.yml`, and `custom-piglin-barter`
> lives under `settings.loot-tables`. They're grouped here because all three
> boost generation or loot rates rather than change game rules, which makes
> `settings.game-boosts` a more discoverable home for them regardless of
> whether world-engine is even enabled.

## Nether Structures

`nether-structures` applies a datapack that boosts fortress and bastion
remnant spawn frequency in the Nether. Requires a server restart to take
effect.

## Overworld Structures

`overworld-structures` applies a datapack that boosts village, shipwreck,
buried treasure, dungeon, ruined portal, desert pyramid, mineshaft, and mesa
mineshaft spawn frequency in the Overworld. Requires a server restart to take
effect.

## Custom Piglin Barter

`custom-piglin-barter` swaps in a custom piglin bartering loot table with
boosted ender pearl and obsidian odds, useful for balancing longer matches. This
resembles the odds of the popular speedrunning version 1.16.1.

Loot tables are JSON files stored under `settings/loot-tables`, using the
same format as vanilla Minecraft loot tables (with some parsing
limitations).

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

`none-players` refers to players with the `NONE` role. Generally, they are
set to spectators, but this setting guarantees they may never take damage.
