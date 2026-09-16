![JManhunt banner](assets/banner-1280x640.png)
# Getting Started

## Requirements

- Paper 26.2 or newer
- Java 25 or newer

## Installation

1. Download the latest JManhunt jar from
   [Modrinth](https://modrinth.com/plugin/jmanhunt).
2. Place the jar in your server's `plugins/` folder.
3. Restart the server. JManhunt will generate its default configuration files
   in `plugins/JManhunt/`.
4. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
   to use JManhunt's placeholders.
5. (Optional) Install [JManhunt-Challenges](https://github.com/jruk8/JManhunt-Challenges)
   to play built-in challenges (no-jump, one-heart and lucky-blocks).

## Your First Match

1. Assign at least one hunter and one speedrunner:

   ```text
   /manhunt setplayer <selector> hunter
   /manhunt setplayer <selector> speedrunner
   ```

   Selectors such as `@a`, `@p`, and `@a[distance=..10]` are supported.

   You may also run `/manhunt quickstart` (or `/mh qs`) to quickly start a match
   with one random speedrunner and the rest being hunters.

2. Start the match with `/manhunt start`.
3. Check the teams at any time with `/manhunt status` (or simply `/manhunt`.)
4. The match ends when all speedrunners have died, or manually through
   `/manhunt end`.

Players need permission `jmanhunt.hunter` or `jmanhunt.speedrunner` to receive the
corresponding role, and likewise `jmanhunt.afk` or `jmanhunt.none` for those
roles. All four permissions are granted by default. The `/manhunt`
command is also accessible through the `mh` alias.

## Next Steps

After playing a few matches, check out the built-in settings and custom
modifiers to enhance your experience:

- [Commands](commands.md)
- [Configuration](configuration.md): overview, settings, world engine,
  statistics, and sounds.
- [Placeholders](placeholders.md)
