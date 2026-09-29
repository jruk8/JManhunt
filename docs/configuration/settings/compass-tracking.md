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
ones. Players who are still respawning (in spectator mode) are never
targets, neither live nor through their last seen location.

When several options exist, the compass ranks them: the nearest
in-range live player first, then a too-close player, then another
player's last seen location. Anything else (no targets at all, or
only out-of-range ones) makes the needle spin instead of freezing.

The compass does nothing while its holder is in spectator mode:
refreshes show no target and clicks are ignored. Holders in vanilla
spectator mode (for example admins) get nothing at all: no refresh,
no actionbar, and no click behavior.

## Given to Roles

Under `settings.compass.obtaining.given-to`, you can configure which roles
receive a compass when a match starts (and on respawn):

```yaml
given-to:
  hunters: true
  speedrunners: false
```

`hunters` is enabled by default, `speedrunners` is disabled by default.
Hunters always track speedrunners and speedrunners always track hunters.

If you'd like to disable the compass entirely, set both to false.

## Compass Item

Under `settings.compass.item`, you can choose which item is handed out as
the tracking compass, in modern `minecraft:material_name` format (the
`minecraft:` namespace may be omitted):

```yaml
compass:
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

Under `settings.compass.must-be-inventory`, you can configure whether the
compass must stay in the player's inventory. This allows the player to change
its slot but not drop it or put it to a container. Dropping on death is
controlled by the [Drop on Death](#drop-on-death) setting.

```yaml
must-be-inventory:
  enabled: true
```

## Drop on Death

Under `settings.compass.obtaining.drop-on-death`, you can configure whether
the compass is dropped on death. This is useful for allowing speedrunners to
pick up the hunter's compass after death and track them.

Only one compass may exist in the inventory at a time. Duplicate ones are removed.

```yaml
drop-on-death:
  enabled: true
```

## Refresh Time

Under `settings.compass.actions.auto`, you can configure how often the
compass should refresh its target on its own. Set `enabled` to false to
disable automatic refreshing. Each holder runs their own interval: two
players who clicked at different times refresh at different times, and
a holder who just joined refreshes on the next tick. `deviation` adds
a random plus-or-minus jitter to the interval per refresh, capped at
the interval itself.

```yaml
actions:
  auto:
    enabled: true
    interval: 10.0        # in seconds
    deviation: 0.0        # in seconds
```

Under `settings.compass.actions.manual`, you can configure right-clicking
the compass to refresh it. Right-clicks run on their own `cooldown`
and apart from the automatic interval, so a fresh automatic refresh
never blocks them; each click still restarts the automatic interval.
Left-click and shift-left-click never touch this cooldown: they only
browse the snapshot cache (see below). Deaths refresh immediately as
well: when a target dies, every unlocked compass in the match
re-resolves at once instead of waiting for the interval.

```yaml
actions:
  manual:
    enabled: true
    cooldown: 3.0      # in seconds
```

Under `settings.compass.actions.target-cycling`, you can let holders
left-click the compass to cycle a manual target lock through the nearest
candidates:
cached opponents nearest-first, then last-seen locations
nearest-first, then uncached players, up to `max-targets` total
(minimum 1, maximum 20). While locked, the actionbar shows `LOCKED`
and automatic refreshes keep pointing at the locked target, within the
same min/max distance limits. Cycling past
the last candidate returns to automatic tracking, as does clicking again
after the locked target left the candidate set. Only left-clicks on air or
blocks cycle the lock; attacking an entity with the compass does not.

```yaml
actions:
  target-cycling:
    enabled: true
    max-targets: 5
    scroll-cooldown: 0.5
```

Every refresh snapshots the closest hunters plus the closest
speedrunners (capped at `max-targets` each), and cycling reads only
those snapshots: it never fetches a live position and never touches
the refresh cooldown, so browsing targets cannot reveal anyone early.
A target with no snapshot yet shows a Bad Signal without a reason
until the next refresh.

`scroll-cooldown` is the seconds between accepted scrolls; clicks inside
the window are ignored, so holding the button cannot scroll. Set it to
`0` for no throttling. Scrolls never run an analysis and are only
refused while an analysis is running.

With one or fewer candidates there is nothing to lock onto: the click
quits silently without any sound, but still starts the scroll
cooldown so failed clicks cannot be spammed. Each successful scroll
plays a short click. You can change it under
`sounds.compass.left-click`, or turn it off there.

## Teammate Tracking

Under `settings.compass.actions.teammates`, shift-left-clicking the
compass toggles between tracking enemies and tracking teammates instead
of cycling a lock. Teammate mode tracks same-role players with the same
distance limits and signal rules, and the actionbar reads `Tracking
teammate ...`; toggling back returns to the other role. The toggle
drops any manual lock and renders the current snapshot cache at once,
without fetching or touching the refresh cooldown:

```yaml
actions:
  teammates:
    enabled: true
    switch-cooldown: 0.5
```

`switch-cooldown` is the seconds between accepted switches; switches
inside the window are ignored silently. Set it to `0` for no
throttling.

The mode is per holder and clears when their match ends, like manual
locks. Spectators cannot toggle, and respawning players are never
targets either way. Entering teammate mode needs at least one
teammate: with nobody to track, the toggle is refused with a chat
message, but still starts the switch cooldown so failed toggles
cannot be spammed. When the last teammate leaves the game, holders in
teammate mode flip back to opponents automatically. Set `enabled`
to false to make shift-left-click lock exactly like a normal
left-click.

## Actionbar

Under `settings.compass.feedback.actionbar`, `refresh-ticks` sets how
often the tracking actionbar is pushed to holders, in ticks (default 1,
minimum 1). This is frontend only: the tracking refresh interval is
untouched, so lowering it redraws the same snapshot more often.

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
refresh: a green up triangle when the rounded distance grew, a red
down triangle when it shrank. The first sighting of a target, and
any refresh where the rounded distance did not change, renders the
plain white distance. Deltas only show within `max-distance` meters,
and only when the change reaches `min-delta-to-show` meters, so
small wobbles far away do not clutter the screen. Both formats
support `{distance}` and MiniMessage (the bar itself adds the `m`).
With `reverse-on-hunter` (default true), hunters see the two formats
swapped, green on closer and red on further, so approaching the prey
reads as progress; speedrunners always see the unswapped formats.

`mode` picks how long the triangle stays: `HOLD` keeps it until the
next refresh, `BLINK` shows it for `blink-duration-seconds`, then
reverts to the plain white distance. A blink of `0` skips the
triangle entirely.

`show-distance` (default on) toggles the distance readout itself.
With it off, the bar shows direction without any distance, delta
triangle, or distance history.

## Chat Messages

Under `settings.compass.feedback.chat-messages`, compass actions can
chat the holder: locking onto a target, switching teammate mode on and
off, and the death of a locked target:

```yaml
feedback:
  chat-messages:
    enabled: true
```

The lines come from `compass.locked-chat`,
`compass.teammate-on-chat`, `compass.teammate-off-chat`, and
`compass.locked-target-died-chat` in `messages.yml`. Refused or
silent outcomes (no teammates, single-candidate quits) stay silent.

## Spinning

When the compass has nothing to point at, its needle spins by aiming at
a dimension you are not in. There is nothing to configure.

## Analysis Delay

Under `settings.compass.actions.manual.analysis`, a right-click refresh
can take a purposeful moment to resolve instead of answering instantly.
Analysis is strictly right-click only: automatic interval refreshes
always resolve at once. While analyzing, the actionbar reads
`Analyzing...`, no second refresh can start, and compass clicks are
ignored until it resolves. The automatic clock stamps when the analysis
starts, but the shared click cooldown stamps when it resolves, so the
full cooldown always runs after the refresh. Interference checks and the
distance math use your position from when you pressed analyze, not
where you moved to during the delay:

```yaml
actions:
  manual:
    analysis:
      enabled: false
      delay-seconds: 1.0
      delay-deviation-seconds: 0.0
```

`delay-seconds` is how long each analysis takes, and
`delay-deviation-seconds` adds a random plus-or-minus jitter per
analysis (capped at the delay, `0` for none).
`sound-interval-seconds` ticks the analysis sound while it runs (rounded
to whole ticks, at least one, at most 3 seconds). An analysis ends
with the refresh click sound on success, or the failure sound when
the needle lands on nothing trackable.

### Analysis Debuffs

Under `settings.compass.actions.manual.analysis.debuffs`, you can run
console commands every time an analysis starts, in modifier style: `<p>`
is the compass holder, `~` resolves against their location, and
`<duration>` is the analysis delay in whole seconds (floored). The
`player` list runs for every analyzing holder plus their own role list,
and only participants are affected:

```yaml
debuffs:
  enabled: false
  commands:
    player:
      - "effect give <p> minecraft:slowness <duration> 1 true"
    speedrunner: []
    hunter:
      - "summon lightning_bolt ~ ~ ~"
```

## Tracking Distance

Each role gets its own tracking limits under
`settings.compass.distance-limits.hunter` and
`settings.compass.distance-limits.speedrunner`. The compass uses the block
matching the role of the player holding it.

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

## Signal Interference

Under `settings.compass.signal-interference`, you can make tracking fail
with a gray Bad signal readout when conditions are bad. The master
`enabled` switch defaults to on, with only the `invisible` option
on, so invisibility interferes out of the box while everything else
stays opt-in.

Each sub-option watches one thing (all default to off except
`invisible`):

- `light-level`: fails in the dark, with separate sky and block light
  minimums. Only applies in the overworld. `interfere-when` picks
  whether one unmet minimum is enough (`ONE_UNMET`) or both must be
  unmet (`BOTH_UNMET`, the default).
- `underground`: fails under too many solid blocks overhead. Glass,
  leaves, and other non-whole blocks do not count unless you turn
  `ignore-transparent` off.
- `underwater`: fails under too much water overhead, but only while
  the feet block itself is water. Works like `underground` but
  counts fluid blocks above the feet.
- `altitude`: fails outside a min/max height band.
- `weather`: fails during the listed weather (`STORM`, `RAIN`,
  `CLEAR`). Pick the values from the in-game checklist.
- `biome`: fails in the listed biomes, written as full keys like
  `minecraft:desert`.
- `movement`: fails when the refresher moved past `threshold-blocks`
  (default 0.2) from their press spot. Only fires on the analysis
  path, since instant refreshes have no gap to move in.
- `line-of-sight`: fails based on whether the holder can see the
  target. One eye-to-eye ray is checked; glass and leaves never block
  it. `interfere-when` picks the failing side (`NOT_VISIBLE` by
  default, `VISIBLE` for the opposite), and `max-ray-distance`
  (default 300) caps the ray: past it, there is no line of sight.
  Only live targets in the same world are checked.
- `invisible`: fails when the watched side is under the invisibility
  effect (on by default). `mode` picks the side: `TARGET` watches
  the tracked target, `SELF` watches the holder.

Every option except `line-of-sight` has its own `two-way` flag
(default off). With two-way on, the target's press-time spot must
pass the check too; with it off, only the holder's spot is checked.
Each side counts its own failures against `required-to-fail`
(default 1), and `chance-to-bypass` gives a bad signal a random
chance to track anyway. Locked targets can fail too. The nearby and
out-of-range readouts consult interference as well; only the no-target
readout never does.

With `show-reason-in-actionbar` (default on), a bad signal names its
cause: `:( Bad signal (underground)`. When several options fail at
once, the most recently found one shows; the holder side wins ties.
Target-side failures are prefixed: `:( Bad signal (target weather)`.
The names come from the `compass.signal-reason` messages and can be
reworded there.

## WorldEdit Navwand

WorldEdit teleports players who click with a compass, which fights the
tracking compass. `advanced.misc.interop.disable-worldedit-navwand`
(default on) blocks that teleport for compass clicks without needing
WorldEdit installed. Turn it off if you rely on the navwand.

## QA Checklist

1. As a hunter, shift-left-click and confirm the compass tracks a
   fellow hunter; shift-left-click again and confirm it tracks a
   speedrunner.
2. Lock a target with left-click, shift-left-click, and confirm the
   lock is gone and the mode flipped.
3. Set `actions.teammates.enabled` to false and confirm shift-left-click
   cycles locks exactly like left-click.
4. End the match and confirm a new match starts in enemy mode.
5. In spectator mode, shift-left-click and confirm nothing happens.
