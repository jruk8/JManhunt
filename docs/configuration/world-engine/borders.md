# World Border

Under `world-engine.world-border`, you can enable confinement that keeps
players inside their assigned cell. This prevents players from wandering
into unused or already-used cells. No vanilla border is ever set: every
match, lone or concurrent, is confined by a per-instance pseudo-border
that pulls stray participants back into their cell (rubber-band). With
borders off, strays are removed from the match with a notice instead
(auto-leave).

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
```

## Enforcement

Every match with borders on is confined by its own pseudo-border:
players outside their cell are pulled back in and take
`damage.amount` past `damage.buffer`, with the start diameter applying
until their match begins (when the start border is enabled).
Spectators bypass it entirely. With borders off, participants outside
their cell are removed from the match with a notice instead
(auto-leave). See [Concurrent Matches](../../multi-instance.md). The
walls render as particle grids; see
[Pseudoborder Particles](pseudoborder-particles.md).

## Damage

`damage.buffer` is the grace distance in blocks past the cell edge,
after which the player is dealt damage.

`damage.amount` is the damage dealt per second when outside the
buffer.

## Start Border

The `start-border` sub-section provides a smaller initial confinement area
that expands to the full cell size when the game begins. This is only active
when both `world-border.enabled` and `start-border.enabled` are true, and
only until the match begins. The expansion applies to confinement checks
directly.

- `start-border.radius`: Initial confinement radius in blocks. The actual
  diameter used is `max(this, tp-spread-radius + 1) * 2`, ensuring players
  never spawn outside the area. Set to `-1` to use `tp-spread-radius + 1`
  only. Default: `10`
