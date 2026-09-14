# Game Boosts

Under `settings.game-boosts`, a set of settings that skew world generation
and loot odds in the speedrunners' favor, without touching any actual
gameplay rules.

```yaml
settings:
  game-boosts:
    nether-structures:
      enabled: false
    overworld-structures:
      enabled: false
    custom-piglin-barter: false
```

## Nether Structures

`nether-structures` applies a datapack that boosts fortress and bastion
remnant spawn frequency in the Nether. Toggling it in-game or with
`/manhunt reload` applies the files immediately. Requires a server restart to take
effect.

## Overworld Structures

`overworld-structures` applies a datapack that boosts village, shipwreck,
buried treasure, dungeon, ruined portal, desert pyramid, mineshaft, and mesa
mineshaft spawn frequency in the Overworld. Toggling it in-game or with
`/manhunt reload` applies the files immediately. Requires a server restart to take
effect.

If you hand-edit the structure files in the plugin folder, run
`/manhunt reload` when done so the plugin picks up your changes, then restart
the server so Minecraft loads them. Edits made directly to the world
`datapacks` copies are overwritten on reload: the plugin folder is the source
of truth.

## Custom Piglin Barter

`custom-piglin-barter` swaps in a custom piglin bartering loot table with
boosted ender pearl and obsidian odds, useful for balancing longer matches. This
resembles the odds of the popular speedrunning version 1.16.1.

Loot tables are JSON files stored under `settings/loot-tables`, using the
same format as vanilla Minecraft loot tables (with some parsing
limitations).
