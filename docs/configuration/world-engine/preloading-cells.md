---
icon: material/cached
---

# Preloading Cells

> For: intermediate admins fighting lag.

Console commands that run whenever a new cell is fetched, plus a buffer
of ready cells so matches start without waiting on allocation or chunk
generation. The main use is pre-generating the cell area with a plugin
like Chunky before a game starts.

> **Why pre-generate:** building terrain at high coordinates lags active
> matches. Doing it during intermissions moves that work out of
> gameplay.

```yaml
world-engine:
  preloading:
    commands: []
    cell-buffer:
      stored-cells-buffer: 3
      increment-when: ALWAYS
```

## Quick Setup (Chunky, 5 minutes)

1. Install Chunky or a similar chunk pre-generation plugin on your server.
2. Copy the example lines below into your `config.yml` under
   `world-engine.preloading.commands`. `<world>` fills in your game world
   name on its own.
3. That is it. Buffered cells are pre-generated automatically, and matches
   start on ready cells.

```yaml
world-engine:
  preloading:
    commands:
      - "chunky world <world>"            # your game world name, filled in automatically
      - "chunky center <cellX> <cellZ>"   # set gen center to the fetched cell's coordinates
      - "chunky radius 120"               # set gen radius to 120 blocks (keep reasonable to avoid lag)
      - "chunky start"                    # start the generation process
      - "chunky confirm"                  # fixes anything that may have prevented the generation
```

Placeholders filled in at runtime: `<world>` (your game world name),
`<cellX>` and `<cellZ>` (the new cell center).

## Cell Buffer

`stored-cells-buffer` is how many ready cells to keep. Default `3`,
minimum `1`. Matches consume one buffered cell at start; if the buffer
is empty, the match fetches a fresh cell on the spot instead. Inspect
it with `/manhunt worldengine cellindex buffer`.

`increment-when` decides when the buffer refills:

- `ALWAYS`: refill whenever a slot is free, even while matches run.
- `NO_MATCH_RUNNING`: only refill while no match is running. Use this
  when pre-generation is heavy enough to lag active matches.

The buffer tops up after match end, when an autostart countdown begins,
and about once a minute. Commands run in the background, once per
fetched cell; a failed fetch retries once after 30 seconds.
