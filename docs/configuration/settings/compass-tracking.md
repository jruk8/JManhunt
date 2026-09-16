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
