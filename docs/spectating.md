# Spectating

JManhunt never puts spectators into vanilla spectator gamemode. Instead,
joining a match as a `spectator` (plus headstart holds, death watches,
and respawn waits) activates fake spectator mode: adventure gamemode
with flight, terrain collision, an infinite invisibility effect, and
full invisibility to alive players.

## What fake spectator mode does

On enable, the player gets adventure gamemode, flight allowed and active,
entity collision disabled, an infinite invisibility effect (no
particles, no icon), and `hidePlayer` from every alive online player
(including late joiners). Fake spectators see each other: enabling
shows every active fake spectator to the newcomer and the newcomer to
them. Hiding also conceals held items and armor client-side. On
disable, flight is grounded, collision is restored, the invisibility
effect is removed, and the player is shown to everyone again, while
remaining fake spectators are hidden from them once more.

## Spectator marker heads

Every player in fake spectator mode wears their own player head in the
helmet slot, so mutually visible spectators can tell each other apart
(the invisibility effect hides bodies; worn armor, including the head,
stays visible). The head is fetched from the player's UUID and cannot
be moved to another slot by anyone. Any previously worn helmet is
preserved and restored exactly on exit; toolbar spectators additionally
get the standard inventory snapshot and restore.

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
always disables the mode: flight is grounded, the invisibility effect
is removed, the marker head is taken off with any real helmet restored,
and visibility is restored, so nothing survives a disconnect. Join
clears any dangling flight on the joiner and re-hides the currently
active spectators from them, so a crash can never leave a player stuck
flying or invisible. A crashed spectator's pre-match inventory snapshot
is memory-only and gone with the crash, so rejoiners are wiped to normal
instead of restored. Alive players never see fake spectators: the
hide holds on every enable path (join, respawn, headstart, death
watch), on every join of an alive player, and on every disable, with
the invisibility effect as backup.

## Admin notes

- There is no config for fake spectator mode; it is always on.
- Fake spectators see each other (marked by their player heads) but no
  alive player ever sees them.
- Use `/jmanhunt setplayer <name> spectator` to move someone into the
  spectator role, or the role pads and lobby flows that already do.

## Spectator toolbar

The `spectator` role also deploys a hotbar toolbar. The player's real
inventory (contents, armor, and offhand) is snapshotted in memory and
restored on exit, on quit, and after a crash; toolbar items left behind
by a crash are discarded on the next join. Only the `spectator` role
gets the toolbar: headstart holds, death watches, respawn waits, and
`NONE` watchers keep their own inventories.

While the toolbar is out, the hotbar is locked: offhand swaps, number
keys, drags, drops, and clicks into the player's own inventory are all
cancelled, so toolbar buttons cannot move. The browser menus still
work, since they only use their own top half.

### Layout

`settings.players.spectator.toolbar.layout` maps hotbar slots 0 to 8,
one character per slot, default `cp##s###b`:

- `c`: match browser (compass).
- `p`: player teleporter (blaze rod).
- `s`: snowball toss (snowball, middle slot).
- `b`: back to lobby (paper).
- `#`: empty slot. Any other character is empty too.

The layout must be exactly 9 characters; anything else falls back to
the default.

### Snowball

The `s` slot holds a rechargeable snowball: pure fun while watching.
Throwing it never consumes it, it deals no damage and applies no
knockback to anything it hits (players, mobs, end crystals, armor
stands, item frames), and every participant and spectator sees it fly.
Right-click air to throw; aiming at a block throws nothing, since
spectators cannot interact with blocks.

`settings.players.spectator.toolbar.snowball.enabled` (default `true`)
shows or hides the item; disabled layouts render `s` as empty.
`settings.players.spectator.toolbar.snowball.cooldown-seconds`
(default 8, minimum 0) sets the recharge time. The thrown snowball is
a plain visible projectile, so it is never hidden from anyone.

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
never breaks the lock; double-shifting within half a second exits the
follow with a chat message and a neutral sound (the first shift is
silent). The target going offline, dying, changing role,
or leaving the match also exits the follow with feedback. With
lock-on disabled, teleports still confirm in chat but never follow,
so the actionbar stays empty.

## Where spectators land

Queued spectators join the match when it starts, under every lobby
mode: they attach to the starting match with fake spectator mode, the
toolbar, and the shared spawn pick below. Mid-match joins and the
match browser land spectators on that same shared spawn pick, in this
order:

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

## When a match ends

Spectators of an ending match move to the oldest running match of the
same lobby (lowest sublobby number) with their role, fake spectator
mode, and toolbar intact. When no other match of that lobby runs, they
return to the lobby as `none` with their inventory restored.
