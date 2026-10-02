---
icon: material/weather-night
---

# End Dimensions

> For: advanced users.

A shared pool of reusable end dimensions instead of one per match. Pool
members live as `<base-name>_<n>` worlds, starting at `jmh_end_1`, and
are handed out on demand.

```yaml
world-engine:
  end:
    base-name: "jmh_end"
    buffer: 3
```

`base-name` is the prefix every pool member shares; the `_n` suffix is
fixed. Renaming it strands existing pool folders, so delete them by
hand or keep the old name. `buffer` is how many free dimensions stay
ready. Minimum 1.

## How It Works

No end dimension is reserved at game start. The first time a match
player enters an end portal, the match takes the lowest free pool
number, and leaving the end returns to the match cell. If taking a
member drops the free count below `buffer`, a replacement generates
during the same portal jump; if the pool is ever empty, one generates
on demand instead of failing the jump.

Refills also run on the cell buffer event: one member generates while
fewer than `buffer` are free, and one is deleted (highest free number)
past `buffer` times 5. Assigned members are never deleted, and matches
that never touch a portal leave the pool alone.

Fresh members generate with a seed hashed from the overworld seed and
the pool number. When a match ends, its dimension resets with a new
seed hashed from its previous one, then rejoins the free buffer.
Assignments left behind by a crash are deleted on the next startup.
