---
icon: material/eye
---

# Spectating

> For: everyone watching or managing matches.

Spectators never use the vanilla spectator gamemode. Joining a match as
a `spectator` turns on fake spectator mode instead: adventure gamemode
with flight, an invisibility effect, and hiding from every alive
player. Spectators see each other (marked by their own player heads);
no alive player ever sees them.

This makes spectators ideal tournament observers and casters:
invisible flight cameras the players never notice. The match browser
jumps between concurrent matches, the player browser plus lock-on
follow keeps a shoutcaster glued to the action, and the watched
players see nothing, so point-of-view recordings stay clean.

## Role Versus Mode

The role is an assignment; the mode is the flight-and-hidden state.
Setting the role alone never enters the mode: only actually joining a
match as a spectator (or a held or watching state) does. The mode also
covers participants who are held or watching (headstart holds, death
cam, respawn waits), and `none` players swept in by
`turn-nones-spectator` get the mode without the role.

## What Spectators Cannot Do

While the mode is active, all of this is cancelled:

- Damage and hunger loss.
- Mob targeting.
- Placing and breaking blocks.
- Block clicks and physical triggers (doors, buttons, pressure plates).
- Dealing damage, by melee or by projectile.
- Picking up items.
- Entering nether or end portals.

Hotbar items stay usable through right-click air. Quitting always
cleans the mode up fully, and state is memory-only, so a crash can
never leave anyone stuck flying or invisible.

## Spectator Toolbar

The `spectator` role also deploys a hotbar toolbar. The player's real
inventory is snapshotted and restored on exit; held or watching
participants keep their own inventories. While the toolbar is out, the
hotbar is locked so its buttons cannot move.

`settings.players.spectator.toolbar.layout` maps hotbar slots 0 to 8,
one character per slot, default `cp##s###b`:

- `c`: match browser (compass).
- `p`: player teleporter (blaze rod).
- `s`: snowball toss (middle slot).
- `b`: back to lobby (paper).
- `#`: empty slot. The layout must be exactly 9 characters.

### Snowball

The `s` slot holds a rechargeable snowball: pure fun while watching.
Throwing never consumes it, and it deals no damage or knockback to
anything it hits. Right-click air to throw; aiming at a block throws
nothing. `snowball.enabled` (default `true`) shows or hides it, and
`snowball.cooldown-seconds` (default 8) sets the recharge time.

### Buttons

- `b` returns the spectator to their lobby spawn and sets their role to
  `none`, exiting spectator mode.
- `c` (right-click) opens the match browser: every running match.
  Clicking an entry moves the spectator there. Moving within the same
  lobby is always allowed; switching to another lobby's match needs
  `jmanhunt.spectator.swaplobby`.
- `p` opens the player browser: heads of the match's speedrunners and
  hunters. Clicking a head teleports to that player and locks on.

### Lock-on

`lock-on` (default `true`) follows the teleported-to player: a locked
spectator farther than `tp-distance` (default 25 blocks) is teleported
back, and the actionbar shows who is followed. Double-shifting within
half a second exits the follow, as does the target going offline,
dying, or leaving the match.

## Where Spectators Land

Queued spectators join the match when it starts. Mid-match joins land
on the same shared spawn pick, in this order:

1. The online speedrunner with the highest progression, ties by name.
2. The online hunter with the highest progression, ties by name.
3. The last-seen speedrunner spot.
4. The last-seen hunter spot.
5. The match cell center.

`settings.players.spectator.travel` (default on, 125 blocks) keeps
watchers near the action: anyone farther than `max-distance` from every
participant and last-seen spot is teleported back to the nearest one.
It is silent: no message, no sound.

## When a Match Ends

Spectators of an ending match move to the oldest running match of the
same lobby with everything intact. When no other match of that lobby
runs, they return to the lobby as `none` with their inventory restored.

## Admin Notes

- There is no config for fake spectator mode; it is always on.
- Use `/manhunt setplayer <name> spectator` to move someone into the
  spectator role, or the role pads and lobby flows that already do.
- Casters covering several matches need `jmanhunt.spectator.swaplobby`
  to hop between lobbies from the match browser.
