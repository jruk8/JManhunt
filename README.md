![JManhunt banner](docs/assets/banner-1280x640.png)

(Try out the latest version of the plugin at server IP: `play.alttari.games`)

# JManhunt

JManhunt is a deeply customizable Paper plugin for 26.2+ Manhunts. It comes with an
automated world reset engine, compass tracking, placeholders, statistics, and a bunch of
config options and modifiers. The plug-and-play defaults work **instantly** for simple
Manhunts with a fully tracking compass and fully automated world resets.

# Key Features

- **One-click setup** » Get World Engine and concurrent matches running in 15 seconds.
- **World Reset Engine** » Grid-based single-world Manhunt engine with persistent spiral 
  cell assignment and automatic world resets.
- **Compass tracking** » Fully tunable tracking compass with jamming, hotspots, 
  teammate tracking, and target cycling.
- **Concurrent matches** » Each lobby runs its own match at the same time, with a native per-match
  spectator system.
- **Mods** » A library of twists made with **JMHScript**, our DSL. Make your own, browse default
  ones in our GUI, or community-made ones in our Discord.
- **Also for serious deployment** » PostgreSQL stats, multiple game queues, per-lobby
  mod/setting overrides, disconnect handling, 280+ config settings, and team chat.

# Why Choose JManhunt?

### Instant World "Resets"

Most Manhunt plugins force full server restarts between matches. JManhunt solves this with a native **World Engine** that segments the world into near-infinite cells without server
restarts. This makes matches feel seamless for not only small friend groups, but also dedicated servers.

### Scriptable Mods

We also built a complete [JMHScript DSL](https://jruk8.github.io/JManhunt/configuration/modifiers/) solely
to make developing **Modifiers** easy and fast. Basic ones like per-role starting kits, random rolls, 
or basic status effects need no scripting knowledge. You can get started with pure Minecraft commands like 
`give @p cooked_beef 8`. Mods can be written in-game through the **Modifier/Preset GUI** or through YML.

Individual mods can be either downloaded drag-and-drop style from our Discord or imported as strings
through the GUI. The default engine bundles over 15 default modifiers. 

What can you do with custom ones? Well..

- **Lifesteal** » All kills steal a full heart from the victim. Permadeath on 0. (find on Discord)
- **Huddle for Warmth** » Stay close to teammates or wither away. (also Discord)
- **Abilities** » Invisibility on hit for runners, gapple on low health, blind nearby Hunters on
  kill...

These examples barely scratch the surface of what you can construct with JMHScript.

# Getting Started (15s) 🗺️

1. **Install the plugin** and restart your server.
2. Run **`/manhunt`** in-game and click the **green button**.
   This sets up the World Engine and creates a default lobby.
3. Walk onto a colored pad to pick a team (red = hunter,
   green = speedrunner), then wait for autostart, or run **`/mh start`**.

For simple Manhunts with fully automated world resets, this is all you need to do with **zero commands** after. 

Alternatively, you can run `/manhunt setup` for a more intermediate dialogue-based setup.

View our comprehensive [documentation](https://jruk8.github.io/JManhunt/).

# Links

Visit our [Discord](https://discord.gg/hkWmCVmWDC) to browse the modifiers/presets catalogue, chat with other admins, or get quick support.

### Consider donating

Developing JManhunt has taken me a lot of time and effort. You can help support the upkeep and future of this plugin by making a [small donation](https://ko-fi.com/jruk). **Thanks!** 👐
