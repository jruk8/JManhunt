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
refreshes show no target and clicks are ignored.

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

## Compass Names

Each role gets its own compass name and lore from `messages.yml`:
`compass.hunter-name` and `compass.hunter-lore` for hunters,
`compass.speedrunner-name` and `compass.speedrunner-lore` for
speedrunners. Picking up a compass restamps it to your role. Players
who are not hunters or speedrunners in a live match cannot pick up a
compass at all: the item is removed instead.

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
hunter's compass after death and track them.

Only one compass may exist in the inventory at a time. Duplicate ones are removed.

```yaml
drop-on-death:
  enabled: true
```

## Refresh Time

Under `settings.compass.refresh-interval`, you can configure how often the
compass should refresh its target. Set to -1 to disable automatic refreshing.

```yaml
refresh-interval: 10.0        # in seconds
```

Under `settings.compass.right-click`, you can configure right-clicking the
compass to refresh it. Left and right clicks share one cooldown under
`settings.compass.click`: an accepted click of either kind blocks the
other until `click-cooldown` time has elapsed. Clicks run apart from
the automatic interval, so a fresh automatic refresh never blocks them;
each click still restarts the automatic interval.

```yaml
right-click:
  refresh-on-right-click: true
click:
  click-cooldown: 3.0      # in seconds
```

Under `settings.compass.left-click`, you can let holders left-click the
compass to cycle a manual target lock through the nearest candidates: live
opponents nearest-first, then last-seen locations nearest-first, up to
`max-targets` total. While locked, the actionbar shows `LOCKED` and
automatic refreshes keep pointing at the locked target, within the same
min/max distance limits. Cycling past
the last candidate returns to automatic tracking, as does clicking again
after the locked target left the candidate set. Only left-clicks on air or
blocks cycle the lock; attacking an entity with the compass does not.

```yaml
left-click:
  enabled: true
  max-targets: 5
  scroll-cooldown: 0.5
```

`scroll-cooldown` is the seconds between accepted scrolls; clicks inside
the window are ignored, so holding the button cannot scroll. Set it to
`0` for no throttling. Left clicks also pass through the shared
`click-cooldown` above. A scroll runs an analysis first when click
analysis is enabled, exactly like a right-click.

With one or fewer candidates there is nothing to lock onto: the click
only consumes the shared cooldown and refreshes nothing. Scrolls are
also refused while the signal is bad or while an analysis is running.

Each successful scroll plays a short click. You can change it under
`sounds.compass.left-click`, or turn it off there. No sound plays when
there is nothing to scroll to.

## Teammate Tracking

Under `settings.compass.teammates`, shift-left-clicking the compass
toggles between tracking enemies and tracking teammates instead of
cycling a lock. Teammate mode tracks same-role players with the same
distance limits and signal rules; toggling back returns to the other
role. The toggle drops any manual lock and refreshes at once, and it
passes through the shared `click-cooldown` like other clicks:

```yaml
teammates:
  enabled: true
```

The mode is per holder and clears when their match ends, like manual
locks. Spectators cannot toggle, and respawning players are never
targets either way. Set `enabled` to false to make shift-left-click
lock exactly like a normal left-click.

## Spinning

When the compass has nothing to point at, its needle spins by aiming at
a dimension you are not in. There is nothing to configure.

## Analysis Delay

Under `settings.compass.analyze`, a refresh can take a purposeful moment to
resolve instead of answering instantly. While analyzing, the actionbar reads
`Analyzing...`, no second refresh can start, and compass clicks are
ignored until it resolves. The automatic clock stamps when the analysis
starts, but the shared click cooldown stamps when it resolves, so the
full cooldown always runs after the refresh:

```yaml
analyze:
  right-click: false
  auto: false
  delay-seconds: 1.0
  delay-deviation-seconds: 0.0
```

`right-click` covers manual refreshes, `auto` covers interval refreshes;
enable either or both. `delay-seconds` is how long each analysis takes,
and `delay-deviation-seconds` adds a random plus-or-minus jitter per
analysis (capped at the delay, `0` for none).
`sound-interval-seconds` ticks the analysis sound while it runs (rounded
to whole ticks, at least one, at most 3 seconds). A click analysis ends
with the refresh click sound; automatic analyses resolve silently.

### Analysis Debuffs

Under `settings.compass.analyze.debuffs`, you can run console commands
every time an analysis starts, in modifier style: `<p>` is the
compass holder, `~` resolves against their location, and `<duration>` is
the analysis delay in whole seconds (floored). The `player` list runs for
every analyzing holder plus their own role list, and only participants
are affected:

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

Each role gets its own tracking limits under `settings.compass.hunter` and
`settings.compass.speedrunner`. The compass uses the block matching the
role of the player holding it.

```yaml
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
`enabled` switch defaults to off, so the signal is always good until
you opt in.

Each sub-option watches one thing:

- `light-level`: fails in the dark, with separate sky and block light
  minimums. Only applies in the overworld.
- `underground`: fails under too many solid blocks overhead. Glass,
  leaves, and other non-whole blocks do not count unless you turn
  `ignore-transparent` off.
- `underwater`: fails under too much water or lava overhead. Works
  like `underground` but counts fluid blocks.
- `altitude`: fails outside a min/max height band.
- `weather`: fails during the listed weather (storm, rain, clear).
- `biome`: fails in the listed biomes, written as full keys like
  `minecraft:desert`.
- `line-of-sight`: fails based on whether the holder can see the
  target. One eye-to-eye ray is checked; glass and leaves never block
  it. `interfere-when` picks the failing side (`VISIBLE` by default,
  `NOT_VISIBLE` for the opposite), and `max-ray-distance` (default
  300) caps the ray: past it, there is no line of sight. Only live
  targets in the same world are checked.

Three extra knobs shape the failure: `required-to-fail` sets how many
options must agree before the compass fails (default 1), `two-way`
checks the target's spot as well as the holder's, and
`chance-to-bypass` gives a bad signal a random chance to track anyway.
Locked targets can fail too. The nearby and out-of-range readouts
consult interference as well; only the no-target readout never does.

With `show-reason-in-actionbar` (default on), a bad signal names its
cause: `:( Bad signal (underground)`. When several options fail at
once, the most recently found one shows. The names come from the
`compass.signal-reason` messages and can be reworded there.

## WorldEdit Navwand

WorldEdit teleports players who click with a compass, which fights the
tracking compass. `settings.server.advanced.disable-worldedit-navwand`
(default on) blocks that teleport for compass clicks without needing
WorldEdit installed. Turn it off if you rely on the navwand.

## QA Checklist

1. As a hunter, shift-left-click and confirm the compass tracks a
   fellow hunter; shift-left-click again and confirm it tracks a
   speedrunner.
2. Lock a target with left-click, shift-left-click, and confirm the
   lock is gone and the mode flipped.
3. Set `teammates.enabled` to false and confirm shift-left-click
   cycles locks exactly like left-click.
4. End the match and confirm a new match starts in enemy mode.
5. In spectator mode, shift-left-click and confirm nothing happens.
