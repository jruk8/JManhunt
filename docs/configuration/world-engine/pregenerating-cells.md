# Preloading Cells

Under `world-engine.preloading`, you can configure console commands that run
whenever a new cell is fetched, and keep a buffer of ready cells so matches
start without waiting on allocation or chunk generation. This is useful for
pre-generating the cell area with chunk-generation plugins such as Chunky
before a game starts.

> **Why Pre-Generate:**
> Generating terrain dynamically at high coordinates causes severe server lag during active matches.
> Pre-generating during intermissions moves heavy chunk loading out of gameplay.

Category in `config.yml`:
```yaml
world-engine:
  preloading:
    commands: []
    cell-buffer:
      stored-cells-buffer: 1
      increment-when: ALWAYS
```

## Quick Setup (Chunky, 5 minutes)

1. Install Chunky or a similar chunk pre-generation plugin on your server.
2. Copy the example lines below into your `config.yml` under
   `world-engine.preloading.commands`. `<world>` fills in your game world
   name on its own.
3. That is it. Buffered cells are pre-generated automatically, and matches
   start on ready cells.

Try something like:

```yaml
world-engine:
  preloading:
    commands:
      - "chunky world <world>"            # your game world name, filled in automatically
      - "chunky center <cellX> <cellZ>"   # set gen center to the fetched cell's coordinates
      - "chunky radius 150"               # set gen radius to 150 blocks (keep reasonable to avoid lag)
      - "chunky start"                    # start the generation process
      - "chunky confirm"                  # fixes anything that may have prevented the generation
```

## Dynamic Placeholders

The following placeholders are automatically populated at runtime:

- `<world>`: The configured game world name (from `world-engine.world-name`).
- `<cellX>`: The X-block coordinate of the newly fetched cell center.
- `<cellZ>`: The Z-block coordinate of the newly fetched cell center.

## Cell Buffer

`stored-cells-buffer` is how many pregenerated cells to keep ready. Minimum:
`1`. Matches consume one buffered cell at start; if the buffer is empty, the
match fetches a fresh cell on the spot instead. Inspect the buffer with
`/manhunt worldengine cellindex buffer`.

`increment-when` decides when the buffer refills:

- `ALWAYS`: refill whenever a slot is free, even while matches run.
- `NO_MATCH_RUNNING`: only refill while no match is running. Use this when
  pre-generation commands are heavy enough to lag active matches.

## Run Behavior

The buffer tops up on:

- **Match End:** after a match is torn down
- **Autostart:** when a match autostart countdown begins
- **Periodically:** about once a minute, covering matches started while
  others run

These commands run in the background, once per fetched cell. If a fetch
fails, it is retried once after 30 seconds.
