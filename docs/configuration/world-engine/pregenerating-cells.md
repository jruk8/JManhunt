# On Fetch New Cell

Under `world-engine.on-fetch-new-cell`, you can configure console commands
that run whenever a new cell is allocated for a match. This
is useful for pre-generating the cell area with chunk-generation plugins
such as Chunky before a game starts.

> **Why Pre-Generate:**
> Generating terrain dynamically at high coordinates causes severe server lag during active matches.
> Pre-generating during intermissions moves heavy chunk loading out of gameplay.

Category in `config.yml`:
```yaml
world-engine:
  on-fetch-new-cell: []
```

## Quick Setup (Chunky, 5 minutes)

1. Install Chunky or a similar chunk pre-generation plugin on your server.
2. Copy the example lines below into your `config.yml` under
   `world-engine.on-fetch-new-cell`. `<world>` fills in your game world
   name on its own.
3. That is it. The next allocated cell is pre-generated automatically between
   matches.

Try something like:

```yaml
world-engine:
  on-fetch-new-cell:
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

## Run Behavior

The commands run on:

- **Match End:** after the previous match becomes inactive
- **Autostart:** when the match autostart countdown begins

These commands are run in the background, and the cell is fetched only once.
If the current cell is **not stale** (i.e., not used), then the pre-generation will
be skipped.