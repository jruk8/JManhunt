# JManhunt

JManhunt is a configurable Paper plugin for running Minecraft Manhunt matches.
Players can be assigned as hunters or speedrunners, while hunters receive a
protected compass that tracks the nearest speedrunner.

Download: https://modrinth.com/plugin/jmanhunt

Contribute: https://github.com/jruk8/JManhunt

## Commands

| Command | Permission | Notes |
| --- | --- | --- |
| `/manhunt` | `jmanhunt.command.status` | Shows match status grouped into speedrunners, hunters, and unassigned players. |
| `/manhunt help` | `jmanhunt.command.help` | Lists available commands. |
| `/manhunt setplayer <selector> <role>` | `jmanhunt.command.setplayer` | Assigns `hunter`, `speedrunner`, or `none`. Supports selectors such as `@a`. |
| `/manhunt start` | `jmanhunt.command.start` | Starts the match. |
| `/manhunt end` | `jmanhunt.command.end` | Ends the match; hunters win. |
| `/manhunt reload` | `jmanhunt.command.reload` | Reloads `config.yml` and `messages.yml`. |

Players need `jmanhunt.hunter` or `jmanhunt.speedrunner` to receive that role.
Both permissions are granted by default. The `mh` alias is also available.

The compass is always protected: it cannot be dropped, moved to a chest or
other container, dragged, swapped, picked up by a hopper, or transferred by
inventory interactions. It is restored to slot 8 after a hunter respawns.

Configuration is generated in the plugin data folder. `config.yml` documents
all options, including command arrays, end-screen statistics, text format,
sounds, compass refresh behavior, and the optional requirement for a speedrunner
to damage a hunter before the match begins. `messages.yml` contains
MiniMessage-first text, with optional legacy formatting.

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
