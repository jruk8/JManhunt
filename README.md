![JManhunt banner](docs/assets/banner-1280x640.png)

(Try out the latest version of the plugin at server IP: `play.alttari.games`)

# JManhunt

JManhunt is a deeply configurable Paper plugin for 26.2+ Manhunts. It comes
with an automated world reset engine, compass tracking, placeholders, statistics,
and a bunch of actions and custom modifiers. The plug-and-play defaults work **instantly** for simple Manhunts with a fully tracking compass and fully automated world resets.

## Key Features

- **Deep configurability** » Toggle actions and settings on or off, or create
  entirely new gameplay through easy-to-use custom modifiers.
- **World reset engine** » Grid-based single-world Manhunt engine with
  persistent spiral cell assignment and per-match End dimensions.
- **Concurrent matches** » Lobby queues with caps and autostart, each
  running its own isolated match at the same time.
- **Compass tracking** » Hunter compass with configurable refresh and
  right-click behavior. Last-seen location locks onto portals.
- **Placeholders & statistics** » Career statistics with PlaceholderAPI
  support.
- **API** » Devs may hook into the Manhunt lifecycle through the API.

## Challenges

The plugin provides dedicated **Custom Modifiers** which allow you to easily create or toggle gamemode presets. These can be as simple as a list of give commands for items, or as configurable as giving a random item on every achievement or mob killed. Best yet, if the modifier engine is not enough (e.g., you're a server network), you can hook custom datapack functions or hook into the API. Modifier options can be accessed in-game via `/mh config modifiers`, or through the config file.

Default custom modifiers include:

- Speedrunners' speed potion
- Full iron kit
- Regen on kill
- One diamond given on any advancement
- Random item or mob every 60 seconds

and so much more (over 15).

JManhunt can even alter Piglin loot tables and structure generation to make Manhunts more balanced for Speedrunners (or, you know, fake a manhunt).

> If the default (or custom) challenges do not provide enough fun, you can install the
> [JManhunt-Challenges](https://builtbybit.com/resources/jmanhunt-challenges.121574)
> addon for special ones like lucky blocks and no jump.

# Getting Started 🗺️

1. **Install the plugin** and restart your server.
2. **Start a quick match** by running `/mh qs`. This picks one random speedrunner automatically.
   - *Or*, to manually assign roles:
     1. Run `/mh setplayer <selector> <role>` for each player, setting them to **HUNTER**, **SPEEDRUNNER**, **AFK**, or **NONE**.
     2. Run `/mh start`.
3. Get **World Resets** working in three minutes by reading [this section](https://jruk8.github.io/JManhunt/configuration/world-engine/).

If you'd like to explore settings, view `config.yml` in your plugins folder. It may look overwhelming at first. That's when the [documentation](https://jruk8.github.io/JManhunt/) comes handy.

## Documentation

Full documentation is available at the
[JManhunt documentation site](https://jruk8.github.io/JManhunt/).

# Update

When installing a MAJOR update (e.g. v3.8.0 => 4.0.0), back-up your current JManhunt resources folder. This is because major updates may modify things with no backward compatibility. Minor and hotfix releases are fully safe to update.

# Contributing

Contributions are welcome! You may contribute by submitting issues or PRs. See our GitHub README for additional details.

© 2026 jruk8. Licensed under GNU AGPLv3.
