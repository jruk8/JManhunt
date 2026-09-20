# World Border

Under `world-engine.world-border`, you can enable a world border that
confines players to their assigned cell. This prevents players from
wandering into unused or already-used cells. When the border is disabled, or
when the world engine is disabled while the border is on, every touched world
is restored to the vanilla defaults (center 0, 0 and size 59999968) at match
start and end, so no shrunken border survives into the next match.

```yaml
world-engine:
  world-border:
    enabled: true
    damage:
      buffer: 5.0
      amount: 1.0
    start-border:
      enabled: true
      radius: 10
      fadeout-time: 5
```

> **Note:**
> This setting should only be used when the game world is different from the 
> lobby world. Otherwise, lobby players may suffocate when the border resizes.

## Concurrent Matches

A lone match uses the real world border described above. Once a second match
starts, the real border drops (one world can only hold one) and every match
is confined by a per-instance pseudo-border instead: players outside their
cell are pulled back in and take `damage.amount` past `damage.buffer`, with
the start diameter applying until their match begins. Spectators bypass it
like the vanilla border. When concurrency drops back to one match, the
survivor gets the real border again. See
[Concurrent Matches](../../multi-instance.md).

## Damage

`damage.buffer` is the size of the world border buffer in blocks, after
which the player is dealt damage (matches Minecraft's world border
damage buffer command.)

`damage.amount` is the damage dealt per block per second when outside the
buffer.

## Start Border

The `start-border` sub-section provides a smaller initial border that
expands to the full cell size when the game begins. This is only active when
both `world-border.enabled` and
`settings.start-on-speedrunner-damage.enabled` are true.

- `start-border.radius`: Initial border radius in blocks. The actual diameter
  used is `max(this, tp-spread-radius + 1) * 2`, ensuring players never spawn
  outside the border. Set to `-1` to use `tp-spread-radius + 1` only.
  Default: `10`
- `start-border.fadeout-time`: Time in seconds for the start border to animate
  expanding to cell size. Set to `0` or `-1` to skip the animation and snap to
  cell size immediately. Default: `5`
