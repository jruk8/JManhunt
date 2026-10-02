![JManhunt banner](docs/assets/banner-1280x640.png)

(Try out the latest version of the plugin at server IP: `play.alttari.games`)

# JManhunt

JManhunt is a deeply customizable Paper plugin for 26.2+ Manhunts. It comes with an
automated world reset engine, compass tracking, placeholders, statistics, and a bunch of
config options and modifiers. The plug-and-play defaults work **instantly** for simple
Manhunts with a fully tracking compass and fully automated world resets.

## Key Features

- **One-click setup** » Get World Engine and concurrent matches running in seconds.
- **World Reset Engine** » Grid-based single-world Manhunt engine with persistent spiral 
  cell assignment and automatic world resets.
- **Compass tracking** » Fully tunable tracking compass with jamming, hotspots, 
  teammate tracking, and target cycling.
- **Concurrent matches** » Each lobby runs its own match at the same time, with a native per-match
  spectator system.
- **Mods** » A library of twists made with **JMHScript**, our DSL. Make your own, browse default
  ones in our GUI, or community-made ones in our Discord.
- **Also for serious deployment** » PostgreSQL stats, multiple game queues, per-lobby
  mod/setting overrides, disconnect handling, ready-cell caching, and team chat.

## Why Choose JManhunt?

Manhunts shouldn't need a server restart after each match. We noticed a lot of plugins that 
didn't have any automated world management, nor any real customizability or toggles. We built an
in-house **World Engine** from the ground-up. It segments the world into near-infinite cells without
restarts and thus makes matches feel seamless.

We also built a complete [JMHScript DSL](https://jruk8.github.io/JManhunt/configuration/modifiers/) solely
to make developing **Modifiers** easy and fast. Basic ones like per-role starting kits, random rolls, 
or basic status effects need no scripting knowledge. You can get started with pure Minecraft commands like 
`give @p cooked_beef 8`. You can write mods in-game through the **Modifier/Preset GUI**.

Individual mods can be either downloaded drag-and-drop style from our Discord or imported as strings
through the GUI. The default engine bundles over 15 default modifiers. 

What can you do with custom ones? Well..

- **Lifesteal** » All kills steal a full heart from the victim. Permadeath on 0. (find on Discord)
- **Huddle for Warmth** » Stay close to teammates or wither away. (also Discord)
- **Abilities** » Invisibility on hit for runners, gapple on low health, blind nearby Hunters on
  kill...

The engine is still maturing and the ones mentioned above are just some examples of what
you can do. We can't wait to see what admins build with the powerful scripting engine.

# Getting Started 🗺️

1. **Install the plugin** and restart your server.
2. Run **`/manhunt`** in-game and click the **green button**.
   This sets up the World Engine and creates a default lobby.
3. Walk onto a colored pad to pick a team (red = hunter,
   green = speedrunner), then wait for autostart, or run **`/mh start`**.

For simple Manhunts with fully automated world resets, this is all you need to do with **zero commands**
after. 

Alternatively, you can run `/manhunt setup` for a more intermediate dialogue-based setup.

View our comprehensive [documentation](https://jruk8.github.io/JManhunt/).

# Community Mods

Visit our [Discord](https://discord.gg/hkWmCVmWDC) to browse the modifiers/presets catalogue, chat
with other admins, or get quick support.
