# World Border

Under `world-engine.world-border`, you can enable confinement that keeps
players inside their assigned cell. This prevents players from wandering
into unused or already-used cells. No vanilla border is ever set: a lone
match removes stray participants with a notice (auto-leave), while
concurrent matches pull them back in (pseudo-border rubber-band). When a
second match starts, any vanilla border is cleared, so no shrunken border
survives into the next match.

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

A lone match sets no vanilla border: participants outside their cell are
removed from the match with a notice instead. Once a second match starts,
every match is confined by a per-instance pseudo-border instead: players
outside their cell are pulled back in and take `damage.amount` past
`damage.buffer`, with the start diameter applying until their match
begins. Spectators bypass it entirely. When concurrency drops back to one
match, the survivor falls back to auto-leave. See
[Concurrent Matches](../../multi-instance.md). The walls render as
particle grids; see
[Pseudoborder Particles](pseudoborder-particles.md).

## Damage

`damage.buffer` is the size of the world border buffer in blocks, after
which the player is dealt damage (matches Minecraft's world border
damage buffer command.)

`damage.amount` is the damage dealt per block per second when outside the
buffer.

## Start Border

The `start-border` sub-section provides a smaller initial confinement area
that expands to the full cell size when the game begins. This is only active
when both `world-border.enabled` and
`settings.start-on-speedrunner-damage.enabled` are true. Since no vanilla
border is set, the expansion applies to confinement checks directly rather
than animating a border.

- `start-border.radius`: Initial confinement radius in blocks. The actual
  diameter used is `max(this, tp-spread-radius + 1) * 2`, ensuring players
  never spawn outside the area. Set to `-1` to use `tp-spread-radius + 1`
  only. Default: `10`
- `start-border.fadeout-time`: Reserved for the expansion animation. With no
  vanilla border set, expansion applies immediately; this value is kept for
  a future animated border. Default: `5`
