# Game Rules

**Game rules** are the plugin's own game-state actions, configured in
`config.yml` under `match.game-rules`. They run at both match start and
match end when enabled, and can be browsed and toggled in-game with
`/manhunt configuration`.

All examples are the default config settings. Refer to the latest
version of `config.yml` in the [GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

```yaml
match:
  game-rules:
    enabled: true
    rules:
      auto-set-gamemode: true
      reset-players-stats: true
      disable-locator-bar: true
      set-respawn-immediate: true
      set-daytime: true
      disable-phantoms: true
      disable-command-feedback: false
```

- `auto-set-gamemode` puts participants in survival mode. Players with role
  `NONE` are put in spectator mode and teleported to the lobby instead, unless
  `settings.roles.turn-nones-spectator` is disabled, in which case they keep
  their current gamemode.
- `reset-players-stats` clears participants' match statistics.
- `disable-locator-bar` turns off the vanilla locator bar in every world for
  the duration of the match.
- `set-respawn-immediate` enables immediate respawn (no death screen) while a
  match runs, and restores it afterwards.
- `set-daytime` sets every world to daytime.
- `disable-phantoms` stops phantoms from spawning while a match runs, and
  re-enables them afterwards.
- `disable-command-feedback` sets `send_command_feedback` to false while a
  match runs, and restores it afterwards. Unlike the rest, it stays off
  unless you enable it.

Pillager patrols (`spawnPatrols`) are always paused while a match runs and
restored when the last match ends. This one has no toggle.

For your own start/end commands, use [Modifiers](modifiers.md) instead.
