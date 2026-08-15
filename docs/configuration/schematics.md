# Schematic Management

Schematics (WorldEdit `.schem` files) are stored in
`plugins/JManhunt/challenges/structures/`. Use the `/jmanhunt schem` command
to manage them:

- `/jmanhunt schem wand` — gives you the schematic wand.
- `/jmanhunt schem save <name>` — saves the selected region as a schematic.
  Select two corners with the schematic wand (left-click for position 1,
  right-click for position 2), then run the command. If the file already
  exists, run the command again within 5 seconds to confirm overwrite.
- `/jmanhunt schem list` — lists all available schematics.
- `/jmanhunt schem load <name> [player]` — loads a schematic so its saved
  pivot lands on the sender's location (or the selected player's location
  when a selector is provided, so console execution is supported).
- `/jmanhunt schem delete <name>` — deletes a schematic. Run the command
  twice within 5 seconds to confirm deletion.

These commands are reusable by future features, not just Lucky Blocks.

## Requirements

Schematic saving, loading, and the wand require **WorldEdit 7.3.0 or newer**.
WorldEdit is a soft dependency used by the schematic commands and the Lucky
Block `structure` outcomes only — the rest of JManhunt works without it.

When WorldEdit is not installed (or is older than 7.3.0):

- `wand`, `save`, and `load` tell you the required version in chat and log a
  warning to the console.
- `list` and `delete` keep working.
- Lucky Block `structure` outcomes are skipped and rerolled, so the Lucky
  Block still drops its items, runs its commands, and plays feedback. The
  console is warned on reload and at roll time.

## Pivot Point

A schematic is saved **relative to the player saving it**: the block the
saver is standing on becomes the schematic's pivot point. When the schematic
is later loaded, the pivot is placed exactly on the loading player's block
position (or the player selected with `/jmanhunt schem load <name> <player>`).

This makes it easy to save a base and have it appear in the same relative
position to whoever loads it — for example, stand at the entrance of a build
when saving, and the entrance will land on the load position.

## Migration Note

Older JManhunt versions stored structures as Minecraft `.nbt` files. These
are no longer read; structures must be saved as WorldEdit `.schem` files with
`/jmanhunt schem save` (or created with WorldEdit itself) and placed in
`plugins/JManhunt/challenges/structures/`. Any structure referenced by a Lucky
Block outcome whose `.schem` file is missing is skipped and rerolled at
runtime, and the console lists the missing files on reload.
