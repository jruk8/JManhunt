# Settings

The plugin provides settings that modify the game flow. This includes things
like:

- starting the game only when a speedrunner hits a hunter
- setting participants to adventure mode during the pre-start window
- autostart when enough players join
- custom bartering loot tables for higher ender pearl pulls
- compass tracking settings under `settings.compass`
- optional grid-based world-engine runs with persistent spiral cell assignment,
  stronghold random spread, and automatic End resets between matches
- optional nether-structures datapack that boosts fortress and bastion spawn
  frequency (requires a server restart)
- optional overworld-structures datapack that boosts village, shipwreck,
  buried treasure, dungeon, ruined portal, desert pyramid, mineshaft, and
  mesa mineshaft spawn frequency (requires a server restart)
- compass disable-when-nearby that stops tracking when the target is within a
  configurable flat distance
- compass tracking-distance that limits how far away the compass can track a
  player (default `-1` for unlimited)
- on-fetch-new-cell console commands that run when a new world-engine cell is
  allocated, useful for pre-generating chunks with plugins such as Chunky
- native `spectators_generate_chunks` gamerule is set to `false` during a
  match to prevent spectators from generating chunks
- `gamestate-commands.default-commands.disable-phantoms` disables the
  `doInsomnia` gamerule during a match so phantoms cannot spawn while players
  are away from a bed; it is restored when the match ends
- friendly fire rules for hunters and speedrunners
- delayed hunter respawn and per-role lives
- start delay for speedrunners (hunters in spectator for a configurable head start)
- alternate win conditions (exit End, survive time, acquire item, reach advancement)

and a lot more!

## Compass Tracking Distance

Under `settings.compass.tracking-distance`, you can limit how far away the
compass can track a player. When the target is beyond this distance, the
compass shows an out-of-range actionbar instead of pointing at the target.

```yaml
settings:
  compass:
    tracking-distance: -1.0
```

Set to `-1` (the default) for unlimited distance. Any non-negative value
limits tracking to that many blocks.