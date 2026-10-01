# Command Lists and Targeting

## Command Lists

Under `commands`, you configure which commands run
and for whom. The bundled `everyone-gets-beef` example gives every
participating player eight steaks when the match starts:

`mods/modifiers/everyone-gets-beef.yml`:

```yaml
enabled: false
behavior:
  0:
    commands:
      player:
        # Runs for every participating player (only hunters and speedrunners, not including NONE)
        - "give <p> minecraft:cooked_beef 8"
```

The available command lists are:

- `commands.player`: runs for every participating player.
- `commands.hunter`: runs only for hunters.
- `commands.speedrunner`: runs only for speedrunners.
- `commands.console`: runs once from the console on every trigger
  firing, no matter which player or role caused the event.
- `commands.console-cleanup`: runs from the console when the match ends.
- `commands.player-cleanup`: runs for every participating player when the
  match ends.

`<p>` is replaced with the participating player's name. Commands may start
with `/`.

The role-specific lists (`hunter`/`speedrunner`) only run when the executing
player actually has that role. For example, if a hunter enters the Nether and
only a `speedrunner` block is configured, that block does not run. The
`console` list has no such filter: it runs once per trigger firing for
every event the behavior matches (every kill, Nether/End enter,
advancement, and respawn), regardless of the involved player's role.
Only a `chance` below `1.0` can skip it; see Success Chance.

## Blacklisted Commands

Some commands are too dangerous for modifiers to dispatch. The
`advanced.misc.interop.blacklisted-modifier-commands` list in `config.yml`
names command roots that never run, no matter which list holds them
(`console`, `player`, `hunter`, `speedrunner`, or either cleanup list).

Matching uses the first token of the resolved line with slashes and any
`namespace:` prefix stripped, case-insensitively, so `stop`, `/Stop`, and
`minecraft:stop` all match a `stop` entry. When a line hits the blacklist,
the plugin logs an error naming the command and its source line, then skips
the rest of that command list. The match itself keeps running.

Defaults: `op`, `deop`, `stop`, `restart`, `reload`, `luckperms`, `lp`,
`permissions`, `ban`, `kick`, `whitelist`. Empty the list to
disable the blacklist entirely.

## Targeting Sides with Selectors

Manhunt roles mirror to vanilla scoreboard teams (`HUNTER`,
`SPEEDRUNNER`, and `SPECTATOR`), so console commands can aim at a whole
side with the `team` selector argument:

`mods/modifiers/hunter-fear.yml`:

```yaml
enabled: false
behavior:
  0:
    runs-on:
      - INTERVAL
    options:
      interval-settings:
        interval: 30
    commands:
      console:
        - "effect give @a[team=HUNTER] minecraft:darkness 5 0"
```

`@a[team=HUNTER]` only covers hunters in the running match, so it
stays safe when several matches run at once. Membership follows roles
exactly (repaired on every role change and login), carries no colors or
friendly-fire rules, and `none`/`afk` players sit in no team. Pair with
`player`/`hunter`/`speedrunner` lists when you need per-player tags like
`<p>` or `~` coordinates instead. To print the names instead of running
once per player, use the `<all-players:HUNTER>` list tag in a message.

## Match-End Cleanup

The `console-cleanup` and `player-cleanup` lists run when the match ends,
which makes them the right place to undo whatever the modifier changed. The
bundled `perma-night` modifier, for example, re-enables daylight when the
match is over:

`mods/modifiers/perma-night.yml`:

```yaml
enabled: false
behavior:
  0:
    commands:
      console:
        # Ran by the console when the match starts.
        - "gamerule advance_time false"
        - "time set midnight"
      console-cleanup:
        # Ran by the console when the match ends.
        - "gamerule advance_time true"
```

Similarly, `speedrunner-health-advantage` resets every participant's max health
in `player-cleanup`, so temporary attribute changes never leak into the next
match or the lobby.

## Relative Coordinates

In player and role commands (`player`, `hunter`, `speedrunner`), tildes (`~`)
are automatically resolved to the participating player's position. For
example, `summon zombie ~ ~ ~` becomes `summon zombie 10.5 64 -20.2` if the
player is at `(10.5, 64.0, -20.2)`. Offsets like `~5` and `~-3` are supported.

All commands are dispatched as the console sender, so there are no permission
issues. The tilde resolution is handled by the plugin before dispatch.
