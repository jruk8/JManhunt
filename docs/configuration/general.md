# Configuration

The plugin creates `config.yml` in its data folder. It includes match
behavior, default game actions, command bundles, custom modifiers, compass
tracking settings under `settings.compass`, end-screen statistics, sounds,
text formatting, and optional PlaceholderAPI settings. Use `/manhunt modifiers`
to browse and change the boolean built-in actions and modifier switches
in-game.

## Settings

The plugin provides settings that modify the game flow. This includes things
like:

- starting the game only when a speedrunner hits a hunter
- setting participants to adventure mode during the pre-start window
- autostart when enough players join
- custom bartering loot tables for higher ender pearl pulls
- compass tracking settings under `settings.compass`
- friendly fire rules for hunters and speedrunners
- delayed hunter respawn and per-role lives
- start delay for speedrunners (hunters in spectator for a configurable head start)
- alternate win conditions (exit End, survive time, acquire item, reach advancement)

The world reset engine, compass tracking distance, statistics, lucky blocks, and schematic management are documented in their respective configuration pages.