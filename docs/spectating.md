# Spectating

JManhunt never puts spectators into vanilla spectator gamemode. Instead,
the `spectator` role (plus headstart holds, death watches, and respawn
waits) activates fake spectator mode: adventure gamemode with flight,
terrain collision, and full invisibility to other players.

## What fake spectator mode does

On enable, the player gets adventure gamemode, flight allowed and active,
entity collision disabled, and `hidePlayer` from every online player
(including late joiners). Hiding also conceals held items and armor
client-side. On disable, flight is grounded, collision is restored, and
the player is shown to everyone again.

While active, the following are cancelled for the spectator:

- Damage and hunger loss.
- Mob targeting.
- Block placing and breaking.
- Block clicks and physical triggers (doors, buttons, pressure plates).
- Dealing damage, by melee or by projectile.

Hotbar items stay usable through right-click air, since the player is in
adventure gamemode rather than true spectator.

## Role versus mode

Being a spectator and being in fake spectator mode are two different
things. The role is an assignment (`spectator` in the role list); the
mode is the transient flight-and-hidden state. Most of the time they
move together: setting the role to `spectator` enables the mode, and
leaving the role disables it. But the mode also covers players who keep
a participant role while held or watching (headstart holds, death cam,
respawn waits), and `NONE` players put into spectator mode by the
`turn-nones-spectator` toggle have the mode without the role.

## Safety guarantees

Fake spectator state lives in memory only and is never persisted. Quit
always disables the mode: flight is grounded and visibility is restored,
so nothing survives a disconnect. Join clears any dangling flight on
the joiner and re-hides the currently active spectators from them, so
a crash can never leave a player stuck flying or invisible.

## Admin notes

- There is no config for fake spectator mode; it is always on.
- Spectators are hidden from each other as well as from players.
- Use `/jmanhunt setplayer <name> spectator` to move someone into the
  spectator role, or the role pads and lobby flows that already do.

## Spectator toolbar

The `spectator` role also deploys a hotbar toolbar. The player's real
inventory (contents, armor, and offhand) is snapshotted in memory and
restored on exit, on quit, and after a crash; toolbar items left behind
by a crash are discarded on the next join. Only the `spectator` role
gets the toolbar: headstart holds, death watches, and `NONE` watchers
keep their own inventories.

### Layout

`settings.players.spectator.toolbar.layout` maps hotbar slots 0 to 8,
one character per slot, default `cp######b`:

- `c`: match browser (compass).
- `p`: player teleporter (blaze rod).
- `b`: back to lobby (paper).
- `#`: empty slot. Any other character is empty too.

The layout must be exactly 9 characters; anything else falls back to
the default.

### Buttons

- `b` returns the spectator to their current lobby's spawn and sets
  their role to `none`, exiting spectator mode.
- `c` (right-click) opens the match browser: every running match.
  Sublobby matches glow and sort to the top. Clicking an entry moves
  the spectator into that match and lands them on the teleport-priority
  target: an alive, non-respawning speedrunner first, then an alive,
  non-respawning hunter, else the match cell's center. Moving within
  the same lobby (including into its sublobbies) is always allowed;
  switching to another lobby's match needs the
  `jmanhunt.spectator.swaplobby` permission.
- `p` opens the player browser: heads of online speedrunners and
  hunters in the spectator's match (or lobby, when outside matches),
  each described by its role in that role's color. Clicking a head
  teleports to that player and locks on. The locked player's head
  glows.

### Lock-on

`settings.players.spectator.toolbar.lock-on` (default `true`) follows
the teleported-to player: every 5 ticks, a locked spectator farther
than `tp-distance` (default 25 blocks, minimum 1) is teleported back
to the target. Any manual movement breaks the lock, as does the target
going offline, dying into a respawn wait, changing role, or leaving
the match. With lock-on disabled, teleports never follow.
