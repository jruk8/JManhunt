# Schematic Management

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