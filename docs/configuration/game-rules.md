# Game Rules

**Game rules** are the plugin's own game-state actions, configured in
`config.yml` under `advanced.advanced-match-controls.game-rules`. They run at both match start and
match end when enabled, and can be browsed and toggled in-game with
`/manhunt config`, which shows one checkbox per rule.

All examples are the default config settings. Refer to the latest
version of `config.yml` in the [GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

```yaml
advanced:
  advanced-match-controls:
    game-rules:
      enabled: true
      rules:
        - AUTO_SET_GAMEMODE
        - RESET_PLAYERS_STATS
        - DISABLE_LOCATOR_BAR
        - SET_RESPAWN_IMMEDIATE
        - SET_DAYTIME
        - DISABLE_PHANTOMS
        - DISABLE_PILLAGER_PATROLS
        - DISABLE_WANDERING_TRADER
```

`rules` lists the enabled rules; anything not listed stays off, and
unknown entries are ignored. Matching is case-insensitive.

- `AUTO_SET_GAMEMODE` puts participants in survival mode. Players with role
  `NONE` are put in spectator mode and teleported to the lobby instead, unless
  `settings.roles.turn-nones-spectator` is disabled, in which case they keep
  their current gamemode.
- `RESET_PLAYERS_STATS` clears participants' match statistics.
- `DISABLE_LOCATOR_BAR` turns off the vanilla locator bar in every world for
  the duration of the match.
- `SET_RESPAWN_IMMEDIATE` enables immediate respawn (no death screen) while a
  match runs, and restores it afterwards.
- `SET_DAYTIME` sets every world to daytime.
- `DISABLE_PHANTOMS` stops phantoms from spawning while a match runs, and
  re-enables them afterwards.
- `DISABLE_COMMAND_FEEDBACK` sets `send_command_feedback` to false while a
  match runs, and restores it afterwards. Unlike the rest, it stays off
  unless you list it.
- `DISABLE_PILLAGER_PATROLS` stops pillager patrols from spawning while a
  match runs, and restores them afterwards.
- `DISABLE_WANDERING_TRADER` stops wandering traders from spawning while a
  match runs, and re-enables them afterwards.

For your own start/end commands, use [Modifiers](modifiers.md) instead.
