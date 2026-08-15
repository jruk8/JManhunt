![JManhunt banner](assets/banner-1280x640.png)
# Getting Started

## Requirements

- Paper 26.2 or newer
- Java 25
- WorldEdit 7.3.0 or newer (optional — required only for the schematic
  commands and Lucky Block `structure` outcomes)

## Installation

1. Download the latest JManhunt jar from
   [Modrinth](https://modrinth.com/plugin/jmanhunt).
2. Place the jar in your server's `plugins/` folder.
3. Restart the server. JManhunt will generate its default configuration files
   in `plugins/JManhunt/`.
4. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
   to use JManhunt's placeholders.
5. (Optional) Install [WorldEdit](https://www.curseforge.com/minecraft/bukkit-plugins/worldedit)
   7.3.0 or newer to use the `/manhunt schem` commands and Lucky Block
   structure outcomes. Without it those features are skipped gracefully and
   the console is told the required version.

## Your First Match

1. Assign at least one hunter and one speedrunner:

   ```text
   /manhunt setplayer <selector> hunter
   /manhunt setplayer <selector> speedrunner
   ```

   Selectors such as `@a`, `@p`, and `@a[distance=..10]` are supported.

2. Start the match with `/manhunt start`.
3. Check the teams at any time with `/manhunt status`.
4. The match ends when all speedrunners have died, or manually through
   `/manhunt end`.

Players need `jmanhunt.hunter` or `jmanhunt.speedrunner` to receive the
corresponding role. Both permissions are granted by default. The `/manhunt`
command is also accessible through the `mh` alias.

### Quick Start (Convenience for Larger Servers)

For larger servers that want to start a match without manually assigning
roles, use Quick Start:

```text
/manhunt quickstart
```

This assigns every eligible online player (excluding AFK) as a Hunter,
randomly chooses one Speedrunner, and immediately starts the game — bypassing
the autostart system entirely.

You can also specify a percentage of eligible players to become Speedrunners:

```text
/manhunt quickstart 50
```

With 16 eligible players and `50`, this results in 8 Speedrunners and 8
Hunters. Fractional results are rounded to the nearest whole player, and there
is always at least one Speedrunner.

Quick Start can only be used when no match is active. See
[Commands](commands.md#quick-start) for more details.

## Next Steps

After playing a few matches, check out the built-in settings and custom
modifiers to enhance your experience:

- [Commands](commands.md)
- [Configuration](configuration.md) — overview, settings, world engine,
  statistics, sounds, and lucky blocks.
- [Placeholders](placeholders.md)
