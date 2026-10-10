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

## Quick Start: Role Suffixes (5 Minutes)

Show a colored `[RUNNER]` or `[HUNTER]` tag after names in chat:

1. Install a LuckPerms-compatible chat plugin so suffixes actually
   render. [LPC Chat](https://modrinth.com/plugin/lpc-chat) works and
   supports MiniMessage. Without one, the suffixes below exist in
   LuckPerms data but never display.
2. Add the speedrunner suffix (note the leading space: suffixes
   always look like `" [suffix]"` so they never butt against the name):

   ```
   /lp group default meta setsuffix 100 " <gray>[<#74de66>RUNNER</#74de66>]" jmh-role=speedrunner
   ```

3. Add the hunter suffix:

   ```
   /lp group default meta setsuffix 100 " <gray>[<#de666e>HUNTER</#de666e>]" jmh-role=hunter
   ```

That is it: the `jmh-role` context swaps the suffix automatically as
roles change. To avoid doubling up with the built-in team colors,
turn them off:

```yaml
settings:
  players:
    name-colors:
      enabled: false
```

The built-in colors (red hunters, green speedrunners, yellow AFK,
gray spectators) only recolor
the name itself; LuckPerms prefixes and suffixes apply on top
untouched. They stay on by default; see
[Roles](../configuration/settings/roles.md#name-colors).

Without LuckPerms installed the context is unavailable and the
plugin logs a single line at startup. Nothing else changes.
