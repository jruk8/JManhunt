---
icon: material/border-all
---

# World Border

> For: intermediate admins.

Confinement that keeps players inside their assigned cell. No vanilla
border is ever set: every match is confined by its own pseudo-border
that pulls stray participants back in (rubber-band). With borders off,
strays are removed from the match with a notice instead (auto-leave).

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

Players outside their cell are pulled back in and take `damage.amount`
past `damage.buffer` blocks of grace. Only the escaped axis moves,
landing a quarter block inside the edge; the other keeps its exact
spot. Spectators bypass it entirely, and nothing applies in the lobby
world. The walls render as particle grids; see
[Pseudoborder Particles](pseudoborder-particles.md).

## Start Border

A smaller initial confinement area that expands to the full cell when
the game begins. Only active while both `world-border.enabled` and
`start-border.enabled` are true, and only until the match begins.

`start-border.radius` is the initial confinement radius. The diameter
used is `max(this, tp-spread-radius + 1) * 2`, so players never spawn
outside it. Set to `-1` to use `tp-spread-radius + 1` only. Default:
`10`.
