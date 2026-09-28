# Spectating

JManhunt never puts spectators into vanilla spectator gamemode. Instead,
joining a match as a `spectator` (plus headstart holds, death watches,
and respawn waits) activates fake spectator mode: adventure gamemode
with flight, terrain collision, and full invisibility to other players.

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
- Picking up items from the ground.
- Entering nether or end portals.

Hotbar items stay usable through right-click air, since the player is in
adventure gamemode rather than true spectator.

## Role versus mode

Being a spectator and being in fake spectator mode are two different
things. The role is an assignment (`spectator` in the role list); the
mode is the transient flight-and-hidden state. Setting the role alone
never enters the mode: only actually joining a match as a spectator
(or a held or watching state) does. Leaving the `spectator` role
exits the mode, but only for players who have it. The mode also
covers players who keep a participant role while held or watching
(headstart holds, death cam, respawn waits), and `NONE` players put
into spectator mode by the `turn-nones-spectator` toggle have the
mode without the role.

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

While the toolbar is out, the hotbar is locked: offhand swaps, number
keys, drags, drops, and clicks into the player's own inventory are all
cancelled, so toolbar buttons cannot move. The browser menus still
work, since they only use their own top half.

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
  the spectator into that match and lands them on the shared spectator
  spawn pick (see below). Moving within
  the same lobby (including into its sublobbies) is always allowed;
  switching to another lobby's match needs the
  `jmanhunt.spectator.swaplobby` permission.
- `p` opens the player browser: heads of online speedrunners and
  hunters in the spectator's match (or lobby, when outside matches),
  each described by its role in that role's color. Clicking a head
  plays a click, chats a spectate confirmation, teleports to that
  player, and locks on. The locked player's head glows.

### Lock-on

`settings.players.spectator.toolbar.lock-on` (default `true`) follows
the teleported-to player: every 5 ticks, a locked spectator farther
than `tp-distance` (default 25 blocks, minimum 1) is teleported back
to the target, and the actionbar shows who is followed. Moving around
never breaks the lock; shifting exits the follow with a chat message
and a neutral sound. The target going offline, dying, changing role,
or leaving the match also exits the follow with feedback. With
lock-on disabled, teleports still confirm in chat but never follow,
so the actionbar stays empty.

## Where spectators land

Match starts, mid-match joins, and the match browser all land
spectators on the same shared spawn pick, in this order:

1. The online speedrunner with the highest progression (Got Iron
   outranks Got Wood), ties broken by name.
2. The online hunter with the highest progression, ties by name.
3. The last-seen speedrunner spot, when no online runner qualifies.
4. The last-seen hunter spot, when no online hunter qualifies.
5. The match cell center.

## Travel limit

`settings.players.spectator.travel` (default on, 125 blocks) keeps
spectators near the action: a watcher farther than `max-distance` from
every online participant and last-seen spot is teleported back to the
nearest one, or to the cell center when no anchor exists. This stops
roaming spectators from generating chunks far from the match. It is
silent: no message, no sound.
