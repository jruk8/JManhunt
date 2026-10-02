![JManhunt banner](assets/banner-1280x640.png)
# Getting Started

> For: everyone setting up the plugin. Just installed? See [Installation](installation.md) first (two steps).

> **Note:** The Lobby System, multi-instancing, and World Engine are
> only for **dedicated servers**. Enabling these features will partition
> the Overworld into cells. It can always be disabled, but some gamerules
> may be modified after.

## Set up in one click

1. Run `/manhunt` in-game.
2. Click the green button.
3. Done. The Lobby System and the World Engine are both on, and your
   lobby is built.

Matches work right away. Restart once when you can: until you do,
strongholds stay in their vanilla ring near the world origin, so new
maps can end up with no reachable End portal.

Rather be walked through it? Run `/manhunt setup` instead. It switches
on the same two systems step by step, with preset and compass choices
along the way. Answer with a number, `b` to go back, `q` to quit.

## Play your first match

1. Pick teams: walk onto a colored pad in the lobby
   (lime = speedrunner, red = hunter), or run `/mh qs` to assign
   everyone at random.
2. Start with `/mh start` (or just wait, it starts on its own).
3. Check the match anytime with `/mh status`. End early with `/mh end`.

That's the whole loop.

## Once it gets stale

Played a few games and want a twist? Open `/manhunt` and try the
modifiers panel: kits, boosts, and game twists, no config needed.

## What's next?

- [Lobby System](play/lobby-system/index.md): bring players in, run matches.
- [Modifiers](configuration/modifiers.md): spice up the game.
- [Commands](commands.md): look up any command.
- [Configuration](configuration.md): change a setting.
- [Concurrent Matches](multi-instance.md): run several matches at once.
