# End Dimensions

The world engine keeps a shared pool of reusable end dimensions instead of
generating one per match. Pool members live as `<base-name>_<n>` worlds,
starting at `jmh_end_1`, and are handed out to matches on demand. Depending
on the server version their folders sit in the world container root or under
the main level's `dimensions/minecraft` dir; the pool scan counts loaded
worlds either way, so both layouts behave the same.

```yaml
world-engine:
  end:
    base-name: "jmh_end"
    buffer: 3
```

`base-name` is the prefix every pool member shares; the `_n` suffix is fixed.
Renaming it strands existing pool folders, so delete them by hand or keep the
old name. `buffer` is how many free dimensions are kept ready. Minimum 1.

## Assignment

Game start no longer reserves an end dimension. The first time a match player
enters an end portal, the match is assigned the lowest free pool number, and
that dimension serves the whole match from that point on. Leaving the end
still returns to the match cell.

If taking the member drops the free count below `buffer`, one replacement is
generated during the same portal jump. If the pool is ever empty, a member is
generated on demand rather than failing the jump.

## Buffer And Overflow

The cell buffer refill event also maintains the end pool. When fewer than
`buffer` members are free, exactly one member is generated with the next
number (highest existing plus 1). When the free count exceeds `buffer` times
5 (15 by default), exactly one member is deleted: the highest free number.
Assigned members are never deleted.

The times-5 ceiling avoids disk churn. Trimming at exactly the buffer size
would delete and regenerate dimensions constantly; the headroom means the
pool almost never runs dry while usage spikes still get reclaimed.

## Seeds

Fresh members generate with a deterministic seed hashed from the overworld
seed and the pool number, so the same number on the same overworld always
produces the same terrain. When a match ends, its dimension resets through
the normal reset path with a new seed hashed from its previous seed, then
rejoins the free buffer. Matches that never enter a portal leave the pool
untouched, and the shared end resets for them as before.

## Startup Sweep

No match survives a restart, so assignments left behind by a crash are
deleted on the next startup, along with leftover `<world>_the_end_*` folders
from older versions. Old folders are removed, never converted. Numeric pool
members are kept: they are the free buffer, not orphans.
