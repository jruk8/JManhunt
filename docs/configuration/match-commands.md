# Built-in Match Commands

**Built-in match commands** are the plugin's own game-state actions plus two
optional command lists, all configured in `config.yml` under
`gamestate-commands`. Everything here is disabled by default except
`default-commands`. Like custom modifiers, these can be browsed and toggled
in-game with `/manhunt configuration`.

All command examples are the default config settings. Some defaults may be outdated
(typically not), in
which case you should refer to the latest version of `config.yml` in the
[GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/config.yml).

Settings for match commands are categorized under `gamestate-commands`:

```yaml
gamestate-commands:
  default-commands:
    enabled: true
```

# Default Commands

Under `gamestate-commands.default-commands`, you can configure the game-state
actions the plugin applies itself. Unlike everything else on this page, these
run at both match start and match end when enabled:

```yaml
gamestate-commands:
  default-commands:
    enabled: true
    auto-set-gamemode: true
    reset-players-stats: true
    disable-locator-bar: true
    set-respawn-immediate: true
    set-daytime: true
    disable-phantoms: true
```

- `auto-set-gamemode` puts participants in survival mode. Players with role
  `NONE` are put in spectator mode and teleported to the lobby instead, unless
  `settings.roles.none-gamemode-spectator` is disabled, in which case they keep
  their current gamemode.
- `reset-players-stats` clears participants' match statistics.
- `disable-locator-bar` turns off the vanilla locator bar in every world for
  the duration of the match.
- `set-respawn-immediate` enables immediate respawn (no death screen) while a
  match runs, and restores it afterwards.
- `set-daytime` sets every world to daytime.
- `disable-phantoms` stops phantoms from spawning while a match runs, and
  re-enables them afterwards.

# Console Commands

Under `gamestate-commands.console-commands`, you can configure console commands
that run once per configured entry:

```yaml
gamestate-commands:
  console-commands:
    enabled: false
    start:
      - "say The hunt has begun!"
    end:
      - "say The hunt is over!"
```

The `start` list runs when a match starts (after the default start actions),
and the `end` list runs when it ends (before the default end actions).

# Player Commands

Under `gamestate-commands.player-commands`, you can configure commands that run
once for every online hunter and speedrunner. `<p>` is replaced with that
player's name, and commands may start with `/`:

```yaml
gamestate-commands:
  player-commands:
    enabled: false
    start:
      - "give <p> cooked_steak 8"
    end:
      - "clear <p>"
```

For per-role or event-driven commands (hunters only, intervals, Nether entry,
cleanup), use [Custom Modifiers](modifiers.md) instead. These two lists only
cover the whole participant roster at match start and end.
