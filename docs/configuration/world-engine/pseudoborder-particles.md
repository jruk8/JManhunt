---
icon: material/shimmer
---

# Pseudoborder Particles

> For: intermediate admins styling borders.

The border walls render as particle grids so players can see their cell
edges. Only the four walls render; cells are unbounded vertically, so
there is no floor or ceiling.

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

## Look

`color` is the wall color as hex, with or without a leading `#`. It
applies to `DUST`, `EFFECT`, and `INSTANT_EFFECT` only; invalid values
fall back to the default brand color.

`type` is the wall shape: `DUST`, `HEART`, `HAPPY_VILLAGER`,
`ANGRY_VILLAGER`, `WITCH`, `FLAME`, `SMALL_FLAME`, `EFFECT`,
`INSTANT_EFFECT`, `PORTAL`, `END_ROD`, `NOTE`, or `SOUL`. Default:
`DUST`.

`particle-spacing` is the blocks between grid points on each wall, 1
(tightest, most expensive) to 8 (loosest).

`render-radius` is how close to a wall a player must be before it
renders. 0 disables the walls.

## Motion

One pulse mode is active. `SINE_WAVE` (default) sweeps a traveling band
across each wall: `wave-direction-angle` is the travel direction in
degrees (0 along the wall, 90 up), `wave-length` the period in blocks
(2 to 64), `wave-speed` the frequency in Hz (0.05 to 3.0).

`INTERVAL` blinks every wall in sync instead: `interval` is the seconds
between blinks, 0 to 3, where 0 shows every tick (not recommended,
lag).

## Cost

`max-particles-per-player` caps wall particles per player per tick,
spread evenly across visible walls. Cost scales with player count,
never with cell size: each player only renders the wall patches around
them.
