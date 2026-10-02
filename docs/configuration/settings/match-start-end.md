---
icon: material/flag-checkered
---

# Match Start & End

> For: intermediate admins tuning the match flow.

## Autostart

JManhunt begins a match on its own once enough players queue up, instead
of waiting for `/manhunt start`:

```yaml
settings:
  match:
    autostart:
      enabled: true
      countdown-seconds: 30
      countdown-style: VERSUS
      minimums:
        hunter: 1
        speedrunner: 1
      maximums:
        hunter: -1
        speedrunner: -1
      broadcast-requirements:
        enabled: false
        interval-seconds: 60
```

Autostart triggers once each role reaches its `minimums` (hard minimum
1 per role). Each lobby runs its own countdown and starts its own
match; see [Concurrent Matches](../../play/concurrent-matches.md).
Minimums and maximums (`-1` disables a role) only gate autostart:
manual `/manhunt start` keeps its own one-hunter-one-speedrunner check.

While a lobby sits below its minimums, `broadcast-requirements` tells
queued hunters and speedrunners what is still missing every
`interval-seconds`. When a role sits above its maximum, the nag names
the excess instead. The timer restarts whenever someone new joins the
teams.

### Countdown

`countdown-seconds` (default 30) is the wait before JManhunt runs
`/manhunt start` on its own. Set to `0` to start immediately with no
countdown.

`countdown-style` (default `VERSUS`) picks the announcement style.
`SIMPLE` uses the plain autostart messages, while `VERSUS` shows the
runner-vs-hunter lineup instead (`3v5 starts in 30s.`). Both are
customizable in `messages.yml`.

## Start on Speedrunner Damage

The match opens with a **pre-start** window that lasts until a
speedrunner hits a hunter, giving speedrunners a moment to get their
bearings before hunters are let loose.

```yaml
settings:
  match:
    start-on-speedrunner-damage:
      enabled: true
      delay-seconds: 45
      on-expire: FORCE_START
      start-in-adventure-mode: true
```

Players in the pre-start window are invulnerable and deal no damage.
The speedrunners' first hit on a hunter opens the match: it deals no
damage itself, but its knockback registers. Hunter hits are cancelled
outright. Match clocks ignore the pre-start window and start when the
game begins.

If a leave empties a side before the game begins, the match cancels.
Disconnecting during pre-start removes you at once, with no grace
period.

### Pre-Start Timeout

`delay-seconds` is how long JManhunt waits for that first hit before
falling back to `on-expire`. Values below `5` clamp up to `5`; set to
`-1` to wait indefinitely.

### On Expire

- `CANCEL`: abort the match without recording stats.
- `FORCE_START`: start the match anyway, with the normal game-start
  announcement after the force-start line.

### Adventure Mode Lock

`start-in-adventure-mode` puts every participant in adventure mode
during the pre-start window so nobody breaks blocks while waiting.
Everyone returns to survival the instant the game begins.

## Headstarts

Either side can start ahead: a headstart configured for one side holds
the *other* side in spectator mode while the configured side plays.
Each side is configured independently.

```yaml
settings:
  match:
    headstarts:
      speedrunner:
        enabled: false
        delay-seconds: 30
      hunter:
        enabled: false
        delay-seconds: 30
```

Both sides are disabled out of the box. `delay-seconds` accepts 0 and
up; 0 releases the held side immediately.

If [Start on Speedrunner Damage](#start-on-speedrunner-damage) is also
enabled, countdowns do not begin until that first hit lands. Each held
player's location is recorded when their countdown begins: held
players may fly around freely, then teleport back to their recorded
spawnpoint when the delay expires. The last five seconds announce in
chat each second. Players who join mid-match while their side is held
are held too.

## Role Announcement

Every participant learns their own role the moment a match starts, in
chat and as a title, with a sound. All three toggle independently:

```yaml
settings:
  players:
    announce-roles:
      chat:
        enabled: true
      title:
        enabled: true
        fade-in-seconds: 0.5
        stay-seconds: 3.0
        fade-out-seconds: 0.5
      sounds:
        enabled: true
```

The announcement runs before the pre-start window opens, so players
always learn their roles before anything can happen. Spectators are
announced too; only `none` and `afk` players are skipped. Each role
hears its own sound (`sounds.announce.*`). When both chat and title
are disabled, no announcement plays at all.

## Match End Delay

How long the plugin waits between the win or cancel announcement and
final cleanup (end commands, lobby return, deactivation):

```yaml
advanced:
  advanced-match-controls:
    end-delay: 10.0        # in seconds
```

Match statistics broadcast halfway through the delay. Interval
modifiers stop at match end, so they never fire during it. Set to `-1`
to skip the delay entirely; other negatives count as zero.

To cancel right away without waiting, run `/mh end -i`
(`-immediate`). Stats post instantly, then end commands and cleanup.
It also works mid delay. Cancelling never saves career stats, with or
without the flag.

## Start Reminders

How often players are reminded while waiting for the first
speedrunner hit:

```yaml
advanced:
  advanced-match-controls:
    start-reminder-interval: 30.0        # in seconds
```

For finite pre-start timeouts this value is not used directly:
exactly three reminders show, at the full delay and two equal slices
(a 30-second delay reminds at 30, 20, and 10 seconds). The value above
is only the repeat interval when the pre-start window waits
indefinitely (`delay-seconds: -1`). Set it to `-1` to disable reminders
while still waiting.

## Disconnect Handling

What happens when a participant disconnects mid-match, per role:

```yaml
advanced:
  advanced-match-controls:
    disconnect-handling:
      speedrunner:
        reconnect-grace-seconds: 60
        max-strikes: 3
      hunter:
        reconnect-grace-seconds: 60
        max-strikes: 3
```

`reconnect-grace-seconds` is how long a disconnected player has to
rejoin before removal from the match. A removed speedrunner counts as
dead (no speedrunners left means the hunters win); a removed hunter
simply leaves (no hunters left means the speedrunners win).

`max-strikes` is how many disconnects a player can accumulate before
the next one removes them instantly. Strikes carry across reconnects
within the match. Set to `1` to disable retries entirely.
