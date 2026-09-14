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

- `CANCEL`: abort the match without recording any stats.
- `FORCE_START`: start the match anyway.

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
that first hit, so hunters stay in spectator through the pre-start window,
then continue waiting out this delay on top of it.

## Delay Length

`delay-seconds` sets the head start length in seconds. Values of `0` or below
disable the delay entirely.

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
Players without a participating role are skipped.

The title shows `manhunt.role-announce-title` (`Role: {role}` by default) with a
per-role subtitle from `messages.yml`, and follows the configured fade, stay,
and fade-out times. Each role also hears its own sound
(`sounds.announce.hunter` and `sounds.announce.speedrunner`), toggled per role
like any other sound. When both chat and title are disabled, no announcement
plays at all.

# Match End Delay

Under `match.end-delay`, you can configure how long the plugin waits between
the win announcement and the final cleanup (running end commands, returning
players to the lobby, and deactivating the match):

```yaml
match:
  end-delay: 10.0        # in seconds
```

Match statistics are broadcast halfway through this delay. Interval modifiers
are stopped as soon as the match ends, so they never fire during the delay.
Set to `-1` to skip the delay entirely (cleanup runs immediately); other
negative values are treated as zero.

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
