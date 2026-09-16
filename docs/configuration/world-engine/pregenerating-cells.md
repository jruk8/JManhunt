# On Fetch New Cell

Under `world-engine.on-fetch-new-cell`, you can configure console commands
that run whenever a new cell is allocated for a match. This
is useful for pre-generating the cell area with chunk-generation plugins
such as Chunky before a game starts.

**Why?** Obviously, one could generate chunks as they're needed. However, this lags the server
because generating chunks in further out regions is expensive. Thus, it's better for the server to 
load chunks and lag before the game than during it.

The placeholders `<cellX>` and `<cellZ>` are replaced with the cell's block coordinates.
The `<world>` placeholder is replaced with the world-engine world name, so you
never have to hardcode it.

```yaml
world-engine:
  on-fetch-new-cell: []
```

## Quick Setup

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

The commands run:

i. when a match ends (after the match goes inactive) and

ii. when the autostart countdown begins,

giving chunk-generation plugins time to pre-generate the next cell before
a game starts. They only run once per match intermission, so the cell is
fetched exactly once between matches. The commands only run when the cell
is actually new (i.e. the previous cell was not already regenerated),
avoiding unnecessary regeneration.
