# Pseudoborder Particles

Every match with borders on is confined by a per-instance
pseudoborder. These walls render as particle grids so players can see
their cell edges. Only the four walls render; cells are unbounded
vertically, so there is no floor or ceiling.

```yaml
world-engine:
  world-border:
    particles:
      type: DUST
      particle-spacing: 1
      color: "#de7766"
      render-radius: 10.0
      pulse-mode: SINE_WAVE
      interval: 0.5
      wave-direction-angle: 0.0
      wave-length: 8.0
      wave-speed: 0.5
      max-particles-per-player: 1000
```

`type` is the wall particle shape: `DUST`, `HEART`, `HAPPY_VILLAGER`,
`ANGRY_VILLAGER`, `WITCH`, `FLAME`, `SMALL_FLAME`, `EFFECT`,
`INSTANT_EFFECT`, `PORTAL`, `END_ROD`, `NOTE`, or `SOUL`. Default:
`DUST`.

`particle-spacing` is the blocks between adjacent grid vertices on each
wall, 1 to 8. 1 is the tightest grid (most detail, most expensive) and 8
is the loosest.

`color` is the wall color as a hex value, with or without a leading `#`,
and applies to the `DUST`, `EFFECT`, and `INSTANT_EFFECT` types only.
Invalid values fall back to the default brand color.

`render-radius` is how close, in perpendicular blocks from a wall, a
player must be before that wall starts rendering. 0 disables the walls.

## Pulse Modes

Exactly one pulse mode is active. `SINE_WAVE` (the default) sweeps a
traveling band across each wall. `wave-direction-angle` is the travel
direction in degrees, counter-clockwise from the wall horizontal when
facing the wall: 0 travels along the wall, 90 travels up.
`wave-length` is the spatial period in blocks, 2 to 64. `wave-speed`
is the temporal frequency in Hz, 0.05 to 3.0.

`INTERVAL` blinks every wall in sync instead: `interval` is the seconds
between blinks, 0 to 3, where 0 shows every tick (not recommended,
lag). The cadence is identical whatever direction the player looks.

## Corners And Budget

Each wall is evaluated on its own, so near a corner both adjacent walls
render at once. Shared corner edges belong to exactly one wall, so
corners never render double-bright.

`max-particles-per-player` caps the total wall particles per player per
tick; the budget spreads evenly across every visible wall, so even a
small budget shows all four sides instead of one corner. Cost scales
with player count, never with cell size: each player only ever
renders the wall patches around them, and distant or coarse-spacing
walls stay cheap.
