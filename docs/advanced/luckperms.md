---
icon: material/shield
---

# LuckPerms

> For: advanced users gating perks by manhunt role.

Install LuckPerms and JManhunt exposes a `jmh-role` context carrying
the player's live role: `none`, `afk`, `spectator`, `speedrunner`, or
`hunter`. Unassigned and offline players read as `none`; there is no
null value. The context refreshes on every role change without relog.

Use it in any context-aware command, for example (schematic):

```
/lp user <name> permission set some.perk true jmh-role=hunter
```

Name colors (red hunters, green speedrunners) only recolor the name
itself; LuckPerms prefixes and suffixes apply on top untouched.
Toggle them under `settings.players.name-colors` (default on). See
[Roles](../configuration/settings/roles.md#name-colors).

Without LuckPerms installed the context is unavailable and the
plugin logs a single line at startup. Nothing else changes.
