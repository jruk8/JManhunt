# Autostart

Under `settings.autostart`, you can have JManhunt automatically begin a match
as soon as enough players have queued up, instead of requiring an admin to
run `/manhunt start` manually.

```yaml
settings:
  autostart:
    enabled: false
    countdown-seconds: 60
    minimums:
      hunter: 1
      speedrunner: 1
    needs-broadcast-interval-seconds: 60
```

Autostart triggers once each role reaches its `minimums` queued players
(one hunter and one speedrunner by default; hard minimum 1 per role).
Each lobby runs its own countdown and starts its own match;
see [Concurrent Matches](../../multi-instance.md). Minimums only gate
autostart: manual `/manhunt start` keeps its own
one-hunter-one-speedrunner check.

While a lobby sits below its minimums, its queued hunters and
speedrunners are told what is still missing every
`needs-broadcast-interval-seconds` (`none`, `afk`, and spectator members
are not nagged), for example "The game needs *two* more Hunters and
*one* more Speedrunner to begin."

## Countdown

`countdown-seconds` is how long JManhunt waits, once the queue becomes
eligible, before running `/manhunt start` on its own. Set to `0` to start
immediately with no countdown at all.

# Start on Speedrunner Damage

Under `settings.start-on-speedrunner-damage`, the match will include a **pre-start**
window that lasts until a speedrunner hits a hunter. This gives speedrunners
a moment to get their bearings before hunters are "let loose."

Players in the pre-start window are invulnerable and may not deal any damage. The
speedrunners' first hit on a hunter opens the match: it deals no damage
itself, but its knockback registers and the damage is healed back a tick
later. The pre-start window also blocks `/kill`, unlike everywhere else.
Match clocks — the status elapsed time and any time-limit countdowns —
ignore the pre-start window and start when the game begins.

```yaml
settings:
  start-on-speedrunner-damage:
    enabled: true
    delay-seconds: 30
    on-expire: FORCE_START
    start-in-adventure-mode: true
```

## Pre-Start Timeout

`delay-seconds` is how long JManhunt waits for that first hit to land before
falling back to the behavior configured in `on-expire`. Values below `5` are
clamped up to `5` seconds. Set to `-1` to wait indefinitely for a hit.

## On Expire

`on-expire` decides what happens if the timeout above passes with no hit:

- `CANCEL`: abort the match without recording any stats. Chat sees
  `manhunt.waiting-for-damage-exhausted`, which says the game will not start.
- `FORCE_START`: start the match anyway. Chat sees
  `manhunt.waiting-for-damage-force-started` instead, which says the game
  automatically started, followed by the normal game-start announcement.

## Adventure Mode Lock

`start-in-adventure-mode`, when enabled, puts every participant in
adventure mode during the pre-start window so nobody can break blocks while
waiting for the starting hit. Everyone is restored to survival the instant
the game actually begins.

# Headstarts

Under `settings.headstarts`, either side can be given a head start: a
headstart configured for one side holds the *other* side in spectator
mode while the configured side plays. Each side is configured
independently, so hunters can start ahead, speedrunners can start ahead,
or both (in which case everybody waits).

```yaml
settings:
  headstarts:
    speedrunner:
      enabled: false
      delay-seconds: 30
    hunter:
      enabled: false
      delay-seconds: 30
```

Out of the box, speedrunners start with a 30-second head start while
hunters wait; the hunter headstart is disabled.

If [Start on Speedrunner Damage](#start-on-speedrunner-damage) is also
enabled, the countdowns don't begin until the speedrunner lands that first
hit, so held players stay out through the pre-start window, then continue
waiting out their headstart on top of it.

Each held player's location is recorded when their countdown begins. Held
players can fly around freely during the delay, but once it expires they are
teleported back to their recorded spawnpoint, including its dimension, and
restored to survival mode. The last five seconds announce in chat with the
`autostart-countdown` sound each second, and the spawn moment plays the
neutral sound. Players who join mid-match while their side is held are held
too.

## Delay Length

`delay-seconds` sets the head start length in seconds per side. Values of `0`
or below disable that side's delay entirely.

# Role Announcement

Under `settings.announce-roles`, every participant is told their own role the
moment a match starts, in chat and as a title. Both are toggled independently:

```yaml
settings:
  announce-roles:
    chat:
      enabled: true
    title:
      enabled: true
      fade-in-seconds: 0.5
      stay-seconds: 3.0
      fade-out-seconds: 0.5
```

The announcement runs inside match start after the match status is shown,
but still before the pre-start window opens, so players always learn their
roles before anything can happen, even when
[Start on Speedrunner Damage](#start-on-speedrunner-damage) is enabled.
Spectators are announced too; only `none` and `afk` players are skipped.

The title shows `manhunt.role-announce-title` (`Role: {role}` by default) with a
per-role subtitle from `messages.yml`, and follows the configured fade, stay,
and fade-out times. Each role also hears its own sound
(`sounds.announce.hunter`, `sounds.announce.speedrunner`, and
`sounds.announce.spectator`), toggled per role like any other sound. When
both chat and title are disabled, no announcement plays at all.

# Match End Delay

Under `match.end-delay`, you can configure how long the plugin waits between
the win or cancel announcement and the final cleanup (running end commands,
returning players to the lobby, and deactivating the match):

```yaml
match:
  end-delay: 10.0        # in seconds
```

Match statistics are broadcast halfway through this delay. Interval modifiers
are stopped as soon as the match ends, so they never fire during the delay.
Set to `-1` to skip the delay entirely (cleanup runs immediately); other
negative values are treated as zero.

To cancel a match right away without waiting out the delay, run
`/mh end -i` (alias `-immediate`). It runs the same cancel sequence with no
waiting: stats post instantly, then end commands, cleanup, and deactivation.
It also works mid delay while a match is already ending, finishing it at once.
Stats and end commands still run exactly once. Cancelling never saves career
stats, with or without the flag.

# Start Reminders

Under `match.start-reminder-interval`, you can configure how often players are
reminded while waiting for the first speedrunner hit:

```yaml
match:
  start-reminder-interval: 10.0        # in seconds
```

For finite pre-start timeouts this value is not used directly. Instead,
exactly three reminders are shown, at the full delay and at two equally-sized
slices (for example, a 30-second delay reminds at 30, 20, and 10 seconds). The
value above is only the repeat interval when the pre-start window waits
indefinitely (`delay-seconds: -1`). Set it to `-1` to disable the reminders
entirely while still waiting.

# Disconnect Handling

Under `match.disconnect-handling`, you can configure what happens when a participant
disconnects mid-match, per role:

```yaml
match:
  disconnect-handling:
    speedrunner:
      reconnect-grace-seconds: 60
      max-strikes: 3
    hunter:
      reconnect-grace-seconds: 60
      max-strikes: 3
```

`reconnect-grace-seconds` is how long a disconnected player has to rejoin
before they are removed from the match. A removed speedrunner counts as dead.
If no speedrunners are left, the hunters win. A removed hunter simply leaves
the hunt. If no hunters are left, the speedrunners win.

`max-strikes` is how many disconnects a player can accumulate before the next
one removes them instantly, without a grace period. Strikes carry over across
reconnects within the match. Set to `1` to disable retries entirely.
