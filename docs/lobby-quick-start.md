# Lobby Quick Start

Get from zero to a queued lobby in five minutes. This guide assumes the
world engine is on; with it off, everyone simply shares lobby 0.

## 1. Generate the lobby world

Run `/manhunt worldengine tpto lobbyworld` twice. The first run warns
the world does not exist yet; the second (within 10 seconds) generates
it, pastes your lobby schematic, and teleports you in. Lobby 0 is
pointed at the spawn automatically. See [World
Engine](configuration/world-engine.md) for presets and regeneration.

## 2. Bring players in

Players join the default lobby on login. To move people yourself:

- `/manhunt lobby join <selector> <lobby-id> [role] [-notp] [-s]`: moves
  players into a lobby queue and teleports them there (add `-notp` to skip
  the teleport, `-s` to skip the role message). Unknown lobby ids are
  created on join.
- `/manhunt lobby leave [selector]`: removes players from a lobby.

## 3. Assign roles

Pick whichever fits your server:

- `/manhunt setplayer <selector> <role>`: direct assignment with
  queue-cap and permission checks.
- **Role pads**: stand on a colored concrete pad in the lobby world to
  take its role (lime: speedrunner, red: hunter, yellow: afk, light
  gray: spectator, gray: none). Runs **setplayer** commands under the hood.

## 4. Start the match

- `/manhunt start [lobby-id]`: starts a match in the given lobby.
- `/manhunt quickstart [percentage]`: assigns teams and starts in one
  go, great for larger servers.
- **Autostart**: eligible lobbies count down and start on their own.

While a match runs, the lobby keeps queueing: newcomers wait (or join
as spectators) under `lobbies.mid-match-setplayer`.

## Next steps

- [Concurrent Matches](multi-instance.md): sublobbies, caps, joining
  live matches, and status output.
- [Commands](commands.md): the full command reference.
- [World Engine](configuration/world-engine.md): presets, role pads,
  cells, and borders.
