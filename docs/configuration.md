# Configuration

The plugin creates `config.yml` in its data folder. It includes match
behavior, default game actions, command bundles, custom modifiers, compass
tracking settings under `settings.compass`, end-screen statistics, sounds,
text formatting, and optional PlaceholderAPI settings. Use `/manhunt modifiers`
to browse and change the boolean built-in actions and modifier switches
in-game.

The `settings.world-engine` section controls grid cell size, spread radius,
target world, lobby teleport location, and the optional nether-structures and
overworld-structures datapacks for the grid-based world engine.

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

### On-Fetch-New-Cell Commands

Under `settings.world-engine.on-fetch-new-cell`, you can configure console
commands that run whenever a new cell is allocated for a match. The
placeholders `<cellX>` and `<cellZ>` are replaced with the cell's block
coordinates. This is useful for pre-generating the cell area with
chunk-generation plugins such as Chunky before players teleport in.

```yaml
settings:
  world-engine:
    on-fetch-new-cell:
      - "chunky radius 500"
      - "chunky start"
```

The commands only run when a genuinely new cell is allocated, avoiding
unnecessary regeneration of cells that were already fetched.

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

### Compass Tracking Distance

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

The lucky-blocks challenge uses `challenges.lucky-blocks.enabled`
(default `false`) to toggle the challenge and
`challenges.lucky-blocks.block-definition` (default `gold_block`) to select
which block is intercepted. The definition accepts `minecraft:<name>` (or a
bare `<name>`) and the special value `random`. When set to `random`, a random
registered block is chosen each time a match starts, excluding unobtainable
blocks (air, barriers, command blocks, structure voids, etc.). The chosen
block is announced to all players when the match starts using its display
name (e.g. "Gold Block"). The outcome table is in
`challenges/lucky-block/lucky-blocks.yml` and uses two-stage weighted random
selection.

### Rarities

The top-level `rarities` section defines rarity tiers and their weights.
Selection is two-stage: first a rarity is rolled using these weights, then an
outcome within that rarity is rolled using the outcome's own `weight`
(default `1.0`). Outcomes without an explicit `rarity` default to `common`.

```yaml
rarities:
  common: 60
  rare: 22
  epic: 12
  legendary: 6
```

Each outcome may set a `rarity` field:

```yaml
outcomes:
  elytra:
    rarity: legendary
    weight: 1.0
    items:
      - elytra 1
```

Unknown rarity names fall back to `common` and log a warning on reload.
The default rarities (common 60, rare 22, epic 12, legendary 6) are used when
the `rarities` section is omitted.

### Composable Outcomes

Outcomes are composable: each outcome may contain any combination of the
following optional action sections. Any combination is valid, and an outcome
with none of these sections is a valid empty outcome that does nothing
(unless feedback is configured).

| Section | Effect |
| --- | --- |
| `items` | Drops the configured items at the broken block's location, replacing the Lucky Block's normal drops. |
| `commands` | Runs console commands. |
| `structure` | Places a structure from the structures directory. |
| `feedback` | Plays a custom sound and/or sends messages. |

#### Items

The `items` section is a list of strings in the format `<item> <quantity>`
(quantity defaults to 1). Item names default to `minecraft:<name>` unless a
namespace is specified.

```yaml
outcomes:
  diamonds:
    weight: 5.0
    items:
      - diamond 1
```

Items also support the modern data-component syntax used by the `/give`
command. Components can be written inline in brackets after the item name, or
as trailing tokens after the quantity:

```yaml
outcomes:
  sharp-sword:
    items:
      - "minecraft:golden_sword[enchantments={sharpness:10},damage=29] 1"
      - "golden_sword 1 enchantments={sharpness:10},damage=29"
```

Both forms produce the same item. The component string is parsed by the
server's item parser, so anything accepted by `/give` works here.

#### Commands

The `commands` section is a map with an optional `relative-to` key and a
`commands` list. `relative-to` controls how tildes (`~`) resolve: `BLOCK`
(default) resolves to the broken block, `PLAYER` resolves to the triggering
player.

```yaml
outcomes:
  pigmen:
    weight: 1.2
    commands:
      relative-to: BLOCK
      commands:
        - "summon pig ~ ~ ~"
        - "summon lightning_bolt ~ ~ ~"
```

A command entry of `delay: <ticks>` pauses the sequence for that many ticks
before the next command runs. This enables choreographed sequences like
rainbow towers:

```yaml
outcomes:
  rainbow-tower:
    rarity: legendary
    commands:
      relative-to: BLOCK
      commands:
        - "summon falling_block ~ ~10 ~ {block_state:{Name:\"minecraft:red_concrete_powder\"},Time:1}"
        - "delay: 2"
        - "summon falling_block ~ ~10 ~ {block_state:{Name:\"minecraft:orange_concrete_powder\"},Time:1}"
        - "delay: 2"
        - "summon falling_block ~ ~10 ~ {block_state:{Name:\"minecraft:yellow_concrete_powder\"},Time:1}"
        - "delay: 10"
        - "summon lightning_bolt ~ ~15 ~"
```

Delays must be positive integers; invalid or non-positive delays are rejected
at load time.

#### Structure

Structure outcomes load `.nbt` files from
`plugins/JManhunt/challenges/structures/<name>.nbt`. The structure
is placed relative to the broken lucky block using Bukkit's intended pivot
behavior.

```yaml
outcomes:
  coin-well:
    weight: 1.0
    structure:
      name: coin-well
      random-rotation: false
```

- `structure.name` (required): the file name without the `.nbt` extension.
- `structure.random-rotation` (optional, default `false`): when `true`, the
  structure is placed with a random rotation.

#### Feedback

Each outcome can optionally define a `feedback` section with a custom sound,
a personal message, and a broadcast:

```yaml
outcomes:
  diamonds:
    weight: 5.0
    items:
      - diamond 1
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

### Reroll Behavior

If a `structure` outcome fails to load or place (e.g. the `.nbt` file is
missing or corrupt), the Lucky Block engine automatically rerolls another
outcome. Up to 20 rerolls are attempted. If all rerolls are exhausted, a
warning is logged to the console and the Lucky Block roll is gracefully
aborted — no items or effects are applied.

### Composable Examples

All sections are optional and can be combined freely:

```yaml
# items only
outcomes:
  loot:
    items:
      - diamond 3

# structure only
outcomes:
  castle:
    structure:
      name: castle

# commands only
outcomes:
  say-hi:
    commands:
      commands:
        - "say hello"

# structure + commands
outcomes:
  combo:
    structure:
      name: castle
    commands:
      commands:
        - "say A castle appeared!"

# items + commands
outcomes:
  combo:
    items:
      - diamond 3
    commands:
      commands:
        - "give <p> golden_sword 1"

# items + structure + feedback
outcomes:
  combo:
    items:
      - diamond 1
    structure:
      name: castle
    feedback:
      message: "<gold>Shiny!</gold>"

# all sections together
outcomes:
  pirate-ship:
    weight: 2
    structure:
      name: pirate-ship
    commands:
      relative-to: BLOCK
      commands:
        - "summon pillager ~ ~ ~"
    items:
      - spyglass 1
      - cooked_cod 16
    feedback:
      message: "<gold>Land ho!</gold>"

# no action sections (empty outcome)
outcomes:
  nothing: {}
```

### Schematic Management

Schematics (structure `.nbt` files) are stored in
`plugins/JManhunt/challenges/structures/`. Use the
`/jmanhunt schem` command to manage them:

- `/jmanhunt schem wand` — gives you the schematic wand.
- `/jmanhunt schem save <name>` — saves the selected region as a schematic.
  Select two corners with the schematic wand (left-click for position 1,
  right-click for position 2), then run the command. If the file already
  exists, run the command again within 5 seconds to confirm overwrite.
- `/jmanhunt schem list` — lists all available schematics.
- `/jmanhunt schem load <name> [player]` — loads a schematic centered on the
  sender's location (or the selected player's location when a selector is
  provided, so console execution is supported).
- `/jmanhunt schem delete <name>` — deletes a schematic. Run the command twice
  within 5 seconds to confirm deletion.

These commands are reusable by future features, not just Lucky Blocks.
