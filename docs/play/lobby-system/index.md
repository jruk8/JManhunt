# Lobby System

> For: everyone running matches. Presets and pads cover intermediate tweaks too.

A lobby is where players wait and pick teams before a match. The Lobby
System switches on by itself once the world engine is on and the lobby
world exists, which the green button in `/manhunt` handles for you.

> Already clicked the green button in `/manhunt`? Your lobby is ready:
> skip to step 2.

## 1. Build the lobby (only if you skipped the green button)

Run this:

```text
/manhunt worldengine tpto lobbyworld
```

This generates the **lobbyworld**. After this, all players automatically
spawn here on join.

## 2. Bring players in

Tell players to reconnect, or run the previous command again with a selector
like `@a` at the end.

## 3. Pick teams

- Walk onto a colored pad (defaults): red = HUNTER, green = SPEEDRUNNER,
  yellow = AFK, and gray = NONE.
- Or from chat: `/manhunt setplayer <player> <ROLE>`.
- Or start a match instantly: `/mh qs [percentage-of-runers]`
  - Optional arg specifies percentage of players to become runners. Leave out
    for strictly one runner.

## 4. Start

Wait for autostart, or run:

```text
/manhunt start
```

New players who join mid-match wait in the lobby for the next game.

## Lobby Presets

New lobby worlds are built from a preset: `EMPTY`, `DEFAULT`, or
`ADVANCED`. Skip the choice and you get `DEFAULT`.

To pick one up front, name it when you generate the lobby:

```text
/manhunt worldengine tpto lobbyworld @a ADVANCED
```

To switch presets later, stop the server, delete the `jmh_lobby` world
folder, start up again, and follow the quick start above. Presets only
apply to freshly generated lobbies. Existing worlds are never touched.

If a preset's schematic is missing, the console warns you and the lobby
stays a void world with a bare spawn platform.

Want to build your own preset? That is developer territory: see the
[schematic tools](../../dev-tools.md#schematic-tools).

## Role Pads

Role pads are blocks in the lobby that assign a role when a player
stands on them. They only work in the lobby world.

| Block | Role |
| --- | --- |
| `LIME_CONCRETE` | speedrunner |
| `RED_CONCRETE` | hunter |
| `YELLOW_CONCRETE` | afk |
| `LIGHT_GRAY_CONCRETE` | spectator |
| `GRAY_CONCRETE` | none |

Players take the role the moment they step on a pad. Queue caps and
mid-match rules still apply, and nothing happens if the player already
has that role. Spectators are never affected by pads.

To change the blocks, edit `advanced.lobbies.role-pads.blocks`. Set
`silent-role-assignment: true` to assign roles without a message or
sound.

## Next steps

- [Multi-Lobby Boundaries](boundaries.md): teleports, boxes, upkeep.
- [Concurrent Matches](../../multi-instance.md): run several matches at once.
- [Commands](../../commands.md): the full list.
- [World Engine](../../configuration/world-engine.md): maps, cells, borders.
