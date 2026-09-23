# Permissions

## Command Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `jmanhunt.command.help` | Use `/manhunt help`. | Everyone |
| `jmanhunt.command.status` | View the current Manhunt status. | Everyone |
| `jmanhunt.command.challenges` | Show the Challenges addon notice with `/manhunt challenges`. | Everyone |
| `jmanhunt.command.setplayer` | Assign players to Manhunt roles. | OP |
| `jmanhunt.command.setplayer.self` | Assign your own Manhunt role. Requires the permission for the target role as well. | OP |
| `jmanhunt.command.start` | Start a Manhunt match. | OP |
| `jmanhunt.command.end` | End an active Manhunt match. | OP |
| `jmanhunt.command.quickstart` | Quick-start a match with auto team assignment. | OP |
| `jmanhunt.command.lobby` | Manage lobby queues. | OP |
| `jmanhunt.command.game` | Join players to or remove them from a running match. | OP |
| `jmanhunt.command.setup` | Use the interactive setup tutorial. | OP |
| `jmanhunt.command.debug` | Toggle JManhunt debug output. | OP |
| `jmanhunt.command.dev.schem` | Use the developer schematic tools. | OP |
| `jmanhunt.command.reload` | Reload JManhunt configuration. | OP |
| `jmanhunt.command.config` | View or change configuration by category. | OP |
| `jmanhunt.admin` | Receive plugin update notices and other admin messages. | OP |
| `jmanhunt.command.worldengine` | View world-engine settings. Implies every worldengine action below. | OP |
| `jmanhunt.command.worldengine.lobbyconfig` | Manage lobby teleports, bounds, and entries. | OP |
| `jmanhunt.command.worldengine.tpto` | Teleport to the lobby world or game world. | OP |
| `jmanhunt.command.worldengine.cellindex` | View or change the world-engine cell index. | OP |

## Role Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `jmanhunt.hunter` | Allows a player to become a hunter. | Everyone |
| `jmanhunt.speedrunner` | Allows a player to become a speedrunner. | Everyone |
| `jmanhunt.afk` | Allows a player to become afk. | Everyone |
| `jmanhunt.none` | Allows a player to become unassigned (none). | Everyone |
| `jmanhunt.spectator` | Allows a player to become a spectator. | Everyone |
