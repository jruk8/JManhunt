# Configuration

The plugin creates `config.yml` in its data folder. It includes match
behavior, default game actions, command bundles, custom modifiers, compass
tracking settings under `settings.compass`, end-screen statistics, sounds,
text formatting, and optional PlaceholderAPI settings. Use `/manhunt modifiers`
to browse and change the boolean built-in actions and modifier switches
in-game.

The `settings.world-engine` section controls grid cell size, spread radius,
target world, lobby teleport location, and the optional nether-structures
datapack for the grid-based world engine.

## Settings

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

and a lot more!

## World Reset Engine

The plugin provides a world reset engine that can be used to reset the match
area. It works through partitioning the world into configurable cells and
creating fresh matches on unused ones. This allows for practically infinite
matches to run on just one world, which is:

- a clean solution compared to manually regenerating a world
- more performant than world resets other plugins offer
- far less likely to break on updates

The world reset engine can be configured in the `config.yml` file.

### Setup Guide

1. Enable the `settings.world-engine.enabled` option in `config.yml`.
2. Set the `settings.world-engine.target-world` option to the name of the
   world you want to reset. The default should work for most servers.
3. Set the `settings.world-engine.lobby-teleport` option to the location you
   want players to be teleported to after the world is reset. The default
   should work for most servers.
4. Restart the server to apply the changes.
5. Check `/datapack list` and make sure the `jmanhunt_world_engine` datapack
   is enabled. If not, enable it with `/datapack enable jmanhunt_world_engine`.
   If it's still red, restart again.
6. Test the world engine by starting a match and checking if the teleportation
   and cell algorithm works correctly.

### World Border

Under `settings.world-engine.world-border`, you can enable a world border that
confines players to their assigned cell. This prevents players from wandering
into unused or already-used cells.

The `start-border` sub-section provides a smaller initial border that expands
to the full cell size when the game begins. This is only active when both
`world-border.enabled` and `start-on-speedrunner-damage.enabled` are true.

- `start-border.radius`: Initial border radius in blocks. The actual diameter
  used is `max(this, tp-spread-radius + 1) * 2`, ensuring players never spawn
  outside the border. Set to `-1` to use `tp-spread-radius + 1` only.
  Default: `10`
- `start-border.fadeout-time`: Time in seconds for the start border to animate
  expanding to cell size. Set to `0` or `-1` to skip the animation and snap to
  cell size immediately. Default: `5`

### Troubleshooting

**Q: The datapack stays red and won't enable no matter what I do.**

A: Regenerate `JManhunt/settings/world-engine` by deleting it and restarting
the server. Open an issue on GitHub with the relevant exception in server
logs.

**Q: Strongholds are generating in non-vanilla places.**

A: This is a deliberate feature, not a bug. The world engine uses a custom
stronghold spread algorithm. You cannot switch to the vanilla stronghold
spread algorithm because this would make certain cells unbeatable after a
certain point.

**Q: I want to disable the world engine.**

A: Set `settings.world-engine.enabled` to `false` in `config.yml` and disable
the `jmanhunt_world_engine` datapack with
`/datapack disable jmanhunt_world_engine`.

**Q: Will this work in [specific Minecraft version]?**

A: This feature is tested to work on 26.2. If the plugin is marked to support
a newer version and you encounter issues, please open an issue on GitHub with
the relevant exception in server logs.

## Statistics and PlaceholderAPI

Career statistics are enabled by default and stored in `jmanhunt.db` using
SQLite. The same database also stores the persistent world-engine spiral cell
index. For statistics shared between servers, set `database.type` to
`postgresql` and configure `database.postgresql` in `config.yml`.

With PlaceholderAPI installed, JManhunt provides placeholders such as
`%jmanhunt_total_kills%` and `%jmanhunt_formatted_time_as_hunter%`. The
complete list and formatting options are documented in
[placeholders.md](placeholders.md).

## Sounds

Sound names are namespaced Minecraft keys (e.g. `block.note_block.pling`).
It's recommended to use a sound explorer like
[mudkipdev's Minecraft Sound Explorer](https://mudkipdev.github.io/minecraft-sound-explorer/).

Sounds are configured under the `sounds` section in `config.yml`:

```yaml
sounds:
  neutral-sound:
    enabled: true
    sound: block.note_block.pling
    pitch: 1.0
    volume: 1.0
```

Each sound entry supports `enabled`, `sound`, `pitch`, and `volume`.

## Lucky Blocks

The lucky-blocks challenge uses `challenges.lucky-blocks.block-definition`
(default `gold_block`) to select which block is intercepted. The outcome table
in `settings/lucky-blocks.yml` supports `ITEM`, `NONE`, and `COMMAND` outcome
types with weighted random selection.

Each outcome can optionally define a `feedback` section with a custom sound,
a personal message, and a broadcast:

```yaml
outcomes:
  diamonds:
    weight: 5.0
    type: ITEM
    item-settings:
      name: diamond
      quantity: 1
    feedback:
      sound:
        enabled: true
        sound: block.amethyst_block.step
        pitch: 2.0
        volume: 1.0
      message: "<aqua>You fancied yourself some diamonds!</aqua>"
      broadcast: "<aqua><white>{player}</white> fancied themselves diamonds!</aqua>"
```

- The `sound` section requires both `enabled` and `sound`. `pitch` defaults to
  `1.0` and `volume` defaults to `1.0`.
- `message` is sent only to the player who broke the lucky block.
- `broadcast` is sent to every online player.
- If both `message` and `broadcast` are configured, the triggering player
  receives only `message`, and everyone else receives `broadcast`.
- When no custom sound is configured, the default sound from
  `sounds.challenges.lucky-block-default` in `config.yml` is played.
- `{player}` is replaced with the triggering player's name.

The feedback message format is configured in `messages.yml` under
`lucky-block-feedback-format` and supports both MiniMessage and Legacy
formatting.