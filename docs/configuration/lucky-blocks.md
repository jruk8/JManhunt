# Lucky Blocks

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

## Rarities

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

## Composable Outcomes

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

### Items

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

### Commands

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

### Structure

Structure outcomes load WorldEdit `.schem` files from
`plugins/JManhunt/challenges/structures/<name>.schem`. This requires
**WorldEdit 7.3.0 or newer** to be installed — WorldEdit is a soft dependency
used by lucky blocks (and the schematic commands) only; the rest of the plugin
works without it.

The schematic is anchored by its pivot point: the block position the structure
was saved from with `/jmanhunt schem save`. When a lucky block breaks, the
schematic is pasted so that pivot lands on the broken block.

```yaml
outcomes:
  coin-well:
    weight: 1.0
    structure:
      name: coin-well
```

- `structure.name` (required): the file name without the `.schem` extension.

### Feedback

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

## Reroll Behavior

If a `structure` outcome fails to load or place (e.g. the `.schem` file is
missing or corrupt), the Lucky Block engine automatically rerolls another
outcome. This also applies when WorldEdit is not installed (or is older than
7.3.0): the structure is skipped and another outcome is chosen, so the Lucky
Block still drops its items, runs its commands, and plays feedback. The
console is warned about missing structures and a missing or outdated WorldEdit
install on reload and at roll time. Up to 20 rerolls are attempted. If all
rerolls are exhausted, a warning is logged to the console and the Lucky Block
roll is gracefully aborted — no items or effects are applied.

## Composable Examples

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

## Schematic Management

Schematics (WorldEdit `.schem` files) are stored in
`plugins/JManhunt/challenges/structures/`. They require **WorldEdit 7.3.0 or
newer** (a soft dependency for lucky blocks only). Use the
`/jmanhunt schem` command to manage them:

- `/jmanhunt schem wand` — gives you the schematic wand.
- `/jmanhunt schem save <name>` — saves the selected region as a schematic.
  Select two corners with the schematic wand (left-click for position 1,
  right-click for position 2), then run the command. The schematic is saved
  relative to you: the block you are standing on becomes its pivot point. If
  the file already exists, run the command again within 5 seconds to confirm
  overwrite.
- `/jmanhunt schem list` — lists all available schematics.
- `/jmanhunt schem load <name> [player]` — loads a schematic so its saved
  pivot lands on the sender's location (or the selected player's location when
  a selector is provided, so console execution is supported).
- `/jmanhunt schem delete <name>` — deletes a schematic. Run the command twice
  within 5 seconds to confirm deletion.

These commands are reusable by future features, not just Lucky Blocks. Without
WorldEdit, `wand`, `save`, and `load` tell you the required version in chat
and log a warning to the console; `list` and `delete` keep working.