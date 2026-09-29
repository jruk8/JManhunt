# Game Rules

**Game rules** are the plugin's own game-state actions, configured in
`config.yml` under `advanced.advanced-match-controls.game-rules`. They run at both match start and
match end when enabled, and can be browsed and toggled in-game with
`/manhunt config`, which shows one checkbox per rule.

All examples are the default config settings. A fresh `config.yml` with
every default and comment generates in the plugin data folder on first run.

```yaml
advanced:
  advanced-match-controls:
    game-rules:
      enabled: true
      rules:
        - DISABLE_LOCATOR_BAR
        - SET_RESPAWN_IMMEDIATE
        - SET_DAYTIME
        - DISABLE_PHANTOMS
        - DISABLE_PILLAGER_PATROLS
        - DISABLE_WANDERING_TRADER
```

`rules` lists the enabled rules; anything not listed stays off, and
unknown entries are ignored. Matching is case-insensitive.

Setting survival gamemodes at match start and clearing participants'
match statistics are integral to the game and always run; they are no
longer listed here. Match phases never touch `send_command_feedback`.

- `DISABLE_LOCATOR_BAR` turns off the vanilla locator bar in every world for
  the duration of the match.
- `SET_RESPAWN_IMMEDIATE` enables immediate respawn (no death screen) while a
  match runs, and restores it afterwards.
- `SET_DAYTIME` sets every world to daytime.
- `DISABLE_PHANTOMS` stops phantoms from spawning while a match runs, and
  re-enables them afterwards.
- `DISABLE_PILLAGER_PATROLS` stops pillager patrols from spawning while a
  match runs, and restores them afterwards.
- `DISABLE_WANDERING_TRADER` stops wandering traders from spawning while a
  match runs, and re-enables them afterwards.

For your own start/end commands, use [Modifiers](modifiers.md) instead.
