![JManhunt banner](assets/banner-1280x640.png)
# Getting Started

> **Prerequisite:** Make sure you have installed the plugin first. See: [Installation](installation.md)

## Interactive Setup

New here? Run `/mh` once and the setup panel offers two paths. The
green button does the recommended setup in one click: it enables
the world engine, generates the lobby world with lobby 0, and
teleports everyone there. The red button starts the step-by-step
tour in chat instead: it walks you through first-time setup,
modifiers, the compass, and the config command as a short
dialogue. Type the number of your answer, `b` to go back, or `q`
to quit; anything the tour runs (such as enabling the world
engine) uses your own permissions, and it times out after 5 idle
minutes. You can restart the tour any time with `/mh setup`.

Either path ends the same way: walk onto a basket (colored
concrete) to pick Hunter or Speedrunner, then start with
`/mh start` or wait for autostart. Restart the server after setup
so the world engine fully applies.

## Your First Match

1. Assign at least one hunter and one speedrunner:

   ```text
   /manhunt setplayer <selector> hunter
   /manhunt setplayer <selector> speedrunner
   ```

   Selectors such as `PlayerName`, `@a`, `@p`, and `@a[distance=..10]` are supported.

   You may also run `/manhunt quickstart` (or `/mh qs`) to quickly start a match
   with one random speedrunner and the rest being hunters. Unassigned
   (`none`) players are always included, so a fresh lobby starts as-is.

2. Start the match with `/manhunt start`.
3. Check the teams at any time with `/manhunt status` (or simply `/manhunt`.)
4. The match ends when Hunters kill their targets, a Speedrunner escapes The End, 
   or manually through `/manhunt end`.

After dabbling with this, set up [world resets](configuration/world-engine.md).
It only takes 3 minutes (enable the engine, run
`/manhunt worldengine tpto lobbyworld` twice to generate a lobby world, and
restart) and greatly improves the user experience.

## Next Steps

After playing a few matches, check out the built-in settings and custom
modifiers to enhance your experience:

- [Commands](commands.md)
- [Concurrent Matches](multi-instance.md): lobbies and several matches at once.
- [Configuration](configuration.md): overview, settings, world engine,
  statistics, and sounds.
- [Placeholders](placeholders.md)
