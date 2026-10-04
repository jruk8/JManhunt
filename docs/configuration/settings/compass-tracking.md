---
icon: material/compass
---

# Compass

> For: intermediate admins tuning tracking. The deep end lives in [Compass: Advanced](compass-advanced.md).

The tracking compass points hunters at speedrunners (and back again),
with dimensional tracking, min/max distance, and per-role rules. Every
setting below lives under `settings.compass`.

## Target Selection

The **target** is a live player of the opposite role. **Dimensional
tracking** means only same-dimension targets count; the compass falls
back to the target's last seen location in your dimension, which is
handy for finding the portal they left through. **Online tracking**
means disconnects record a last seen location but are never live
targets. Respawning players are never targets at all.

Ranking: nearest in-range live player first, then a too-close player,
then another player's last seen location. With nothing trackable, the
needle spins. The compass does nothing while its holder is in
spectator mode, and vanilla spectators (admins) get nothing at all.

## Given to Roles

Which roles receive a compass on match start (and on respawn):

```yaml
given-to:
  hunters: true
  speedrunners: false
```

`hunters` is enabled by default, `speedrunners` is disabled by default.
Hunters always track speedrunners and speedrunners always track hunters.

If you'd like to disable the compass entirely, set both to false.

## Compass Item

Which item is handed out as the tracking compass, in
`minecraft:material_name` format (the `minecraft:` namespace may be
omitted):

```yaml
compass:
  obtaining:
    item: compass
```

The default is `compass`. Try `clock` or `recovery_compass` for a different
look; anything else works too. Unknown names, and anything with placement
functionality such as dirt, signs, or redstone, fall back to `compass` with
a console warning. Already handed out compasses keep working after a
change; new ones use the configured item.

Note that only `compass` points its needle at the tracked player. A
`recovery_compass` always points at its holder's last death location, so it
works as a match token while direction readout stays on the actionbar.

Handed-out compasses land in the last hotbar slot (slot 9); when it is
taken, the first free inventory slot is used instead, or the compass
drops at the holder's feet when the inventory is full. Picked-up
compasses never stack: every compass stays at amount 1.

## Compass Names

Each role gets its own compass name and lore from `messages.yml`:
`compass.hunter-name` and `compass.hunter-lore` for hunters,
`compass.speedrunner-name` and `compass.speedrunner-lore` for
speedrunners. Picking up a compass restamps it to your role. Players
who are not hunters or speedrunners in a live match cannot pick up a
compass at all: the item is removed instead.

## Inventory Lock

Whether the compass must stay in the player's inventory: the slot
may change, but dropping and containers are blocked. Dropping on death
is controlled by [Drop on Death](#drop-on-death).

```yaml
compass:
  lock-to-inventory: true
```

## Drop on Death

Whether the compass drops on death: handy for letting speedrunners
pick up a dead hunter's compass and track back.

Only one compass may exist in the inventory at a time. Duplicate ones are removed.

```yaml
drop-on-death:
  enabled: true
```

## Refresh Time

How often the compass refreshes its target on its own. Set `enabled`
to false to disable automatic refreshing. Each holder runs their own
interval, and `deviation` adds a random plus-or-minus jitter per
refresh, capped at the interval itself.

```yaml
actions:
  auto:
    enabled: true
    interval: 35.0        # in seconds
    deviation: 0.0        # in seconds
```

Right-clicking the compass to refresh it. Right-clicks run on their
own `cooldown`, apart from the automatic interval, and each click
restarts the automatic interval. Left-clicks never touch this
cooldown: they only browse the snapshot cache. Deaths refresh
immediately too: when a target dies, every unlocked compass in the
match re-resolves at once.

```yaml
actions:
  manual:
    enabled: true
    cooldown: 3.0      # in seconds
```

Left-clicking the compass to cycle a manual target lock through the
nearest candidates: cached opponents nearest-first, then last-seen
locations, then uncached players, up to `max-targets` total (1 to 20).
While locked, the actionbar shows `LOCKED` and refreshes keep pointing
at the locked target within the min/max limits. Cycling past the last
candidate returns to automatic tracking. Only left-clicks on air or
blocks cycle the lock; attacking an entity does not.

```yaml
actions:
  target-cycling:
    enabled: true
    max-targets: 5
    scroll-cooldown: 0.5
```

Every refresh snapshots the closest hunters plus the closest
speedrunners (capped at `max-targets` each), and cycling reads only
those snapshots, so browsing cannot reveal anyone early. When the
last refresh hit a bad signal, cached browsing keeps showing Bad
Signal instead of the snapshots until the next refresh.

`scroll-cooldown` is the seconds between accepted scrolls; clicks inside
the window are ignored. Set it to `0` for no throttling. With one or
fewer candidates the click quits silently but still starts the
cooldown. Each successful scroll plays a short click, changeable under
`sounds.compass.left-click`.

## Teammate Tracking

Shift-left-clicking the compass toggles between tracking enemies and
tracking teammates instead of cycling a lock. Teammate mode tracks
same-role players with the same distance limits, and the actionbar
reads `Tracking teammate ...`. The toggle drops any manual lock:

```yaml
actions:
  teammates:
    enabled: true
    switch-cooldown: 0.5
```

`switch-cooldown` is the seconds between accepted switches; switches
inside the window are ignored. Set it to `0` for no throttling.

The mode is per holder and clears when their match ends. Entering
teammate mode needs at least one teammate; when the last teammate
leaves, holders flip back to opponents automatically. Set `enabled`
to false to make shift-left-click lock exactly like a normal
left-click.

## Actionbar

`refresh-ticks` sets how often the tracking actionbar is pushed to
holders, in ticks (default 1, minimum 1). Frontend only: the tracking
interval is untouched, so lowering it redraws the same snapshot more
often.

```yaml
feedback:
  actionbar:
    refresh-ticks: 1
    show-distance: true
    show-distance-delta:
      enabled: true
      further-format: "<green>▲{distance}"
      closer-format: "<red>▼{distance}"
      max-distance: 500.0
      min-delta-to-show: 5.0
      mode: BLINK
      blink-duration-seconds: 0.6
      reverse-on-hunter: true
```

`show-distance-delta` colors the distance by movement since the last
refresh: a green up triangle when it grew, a red down triangle when it
shrank. Deltas only show within `max-distance` and past
`min-delta-to-show`, so small wobbles far away do not clutter the
screen. Both formats support `{distance}` and MiniMessage. With
`reverse-on-hunter` (default true), hunters see the formats swapped,
so approaching the prey reads as progress.

`mode` picks how long the triangle stays: `HOLD` keeps it until the
next refresh, `BLINK` shows it for `blink-duration-seconds`, then
reverts to plain white. A blink of `0` skips the triangle entirely.

`show-distance` (default on) toggles the distance readout itself.
With it off, the bar shows direction without any distance.

### Show Accuracy

`enabled` (default off) appends a stepped accuracy percent after the
tracked name: `Tracking Alex (80%)`. The percent snaps to tens: 0% is
the worst possible error, 100% is the true spot. With inaccuracy off,
every bar reads 100%. `accurate-color` (default `#63d42a`) and
`inaccurate-color` (default `#cc472d`) set the ends as `#rrggbb`; junk
values fall back to the defaults.

## Chat Messages

Compass actions can chat the holder: locking onto a target, switching
teammate mode on and off, and the death of a locked target:

```yaml
feedback:
  chat-messages:
    enabled: true
```

The lines come from `compass.locked-chat`, `compass.teammate-on-chat`,
`compass.teammate-off-chat`, and `compass.locked-target-died-chat` in
`messages.yml`.

## Spinning

When the compass has nothing to point at, its needle spins by aiming at
a dimension you are not in. There is nothing to configure.

## Tracking Distance

Each role gets its own tracking limits. The compass uses the block
matching the role of the player holding it:

```yaml
distance-limits:
  hunter:
    min-distance:
      enabled: true
      distance: 25.0
    max-distance:
      enabled: true
      distance: -1.0
```

### Minimum Distance

When the target is at or inside the min distance, the compass stops
tracking and shows the nearby message instead. Only flat distance counts:
a target directly above or below you still reads as nearby. This makes
camping a viable strategy for speedrunners.

### Maximum Distance

When the target is beyond the max distance, the compass shows an
out-of-range message and either points at a closer last seen location of
another player or spins its needle. Set the distance to `-1` for
unlimited range.

A manually locked target obeys both limits: a locked target that is
too close shows the nearby message, and one that is too far shows the
out-of-range message.

