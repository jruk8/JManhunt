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
