![banner](banner-1280x640.png)
# JManhunt

JManhunt is a configurable Paper plugin for running Minecraft Manhunt matches.
Players can be assigned as hunters or speedrunners, while hunters receive a
protected compass that tracks the nearest speedrunner.

Download: https://modrinth.com/plugin/jmanhunt

Contribute: https://github.com/jruk8/JManhunt

## Commands

| Command | Permission | Notes                                                                                             |
| --- | --- |---------------------------------------------------------------------------------------------------|
| `/manhunt` | `jmanhunt.command.status` | Shows match status grouped into speedrunners, hunters, and unassigned players.                    |
| `/manhunt help` | `jmanhunt.command.help` | Lists available commands.                                                                         |
| `/manhunt setplayer <selector> <role>` | `jmanhunt.command.setplayer` | Assigns `hunter`, `speedrunner`, or `none`. Supports selectors such as `@a` and `[distance=..x]`. |
| `/manhunt start` | `jmanhunt.command.start` | Starts the match.                                                                                 |
| `/manhunt end` | `jmanhunt.command.end` | Ends the match; hunters win.                                                                      |
| `/manhunt settings <setting> <true\|false>` | `jmanhunt.command.settings` | Views or changes boolean default-command and custom-modifier settings. `/manhunt modifiers` is an alias. |
| `/manhunt reload` | `jmanhunt.command.reload` | Reloads `config.yml` and `messages.yml`.                                                          |

Players need `jmanhunt.hunter` or `jmanhunt.speedrunner` to receive that role.
Both permissions are granted by default. The `mh` alias is also available.

The compass is always protected: it cannot be dropped, moved to a chest or
other container, dragged, swapped, picked up by a hopper, or transferred by
inventory interactions. It is restored to the last slot of the hotbar after
a hunter respawns.

Configuration is generated in the plugin data folder. `config.yml` documents
all options, including default actions, command arrays, custom modifiers,
end-screen statistics, text format, sounds, compass refresh behavior, and the
optional requirement for a speedrunner to damage a hunter before the match
begins. `messages.yml` contains
MiniMessage-first text, with optional legacy formatting.

Game-state console commands use `<winner>` in end commands. Player commands use
`<p>` and run once per participating online player, for example
`/give <p> cooked_steak 8`. Custom modifiers can additionally define
`hunter-commands` and `speedrunner-commands` to target only that role. Command
sections are disabled by default; built-in default actions and named
`custom-modifiers` can be enabled or disabled in-game with `/manhunt settings`
or its `/manhunt modifiers` alias. Each configured sound has a `sound` and
`pitch` value under its sound name in `config.yml`.

## Optional PlaceholderAPI Dependency

Career statistics are enabled by default and stored in `stats.db` using SQLite,
so they survive server restarts without additional setup. For proxy-wide configs, 
set `database.type` to `postgresql` and configure `database.postgresql` in
`config.yml`.

The SQLite driver, PostgreSQL driver, and HikariCP are declared through
Paper's `libraries` loader. Paper downloads them at runtime, so they are not
bundled into the JManhunt jar.

When PlaceholderAPI is installed, JManhunt registers the internal `jmanhunt`
expansion. Examples include `%jmanhunt_total_kills%` and
`%jmanhunt_formatted_time_as_hunter%`. Available identifiers are documented in
`placeholders.yml`; formatting and per-placeholder enable/disable settings are
configured in `config.yml`. Uses the formatting configured in `config.yml`.

## Build

Java 25 is required. Gradle can use a locally installed matching toolchain.

```shell
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The plugin jar is written to `build/libs/`. GitHub Actions builds every push and
pull request. Pushing a `v*` tag creates a GitHub release and publishes to
Modrinth using the repository’s `MODRINTH_ID`, `MODRINTH_GAME_VERSIONS`, and
`MODRINTH_TOKEN` settings.

See `CONTRIBUTING.md` for contributor setup.

© 2026 jruk8. Licensed under GNU GPLv3.
