---
icon: material/gavel
---

# Game Rules

> For: intermediate admins scripting match start and end behavior.

**Game rules** are the plugin's own game-state actions, configured in
`config.yml` under `advanced.advanced-match-controls.game-rules`. They run at both match start and
match end when enabled, and can be browsed and toggled in-game with
`/manhunt config`, which shows one checkbox per rule.

All examples are the default config settings. A fresh `config.yml` with
every default and comment generates in the plugin data folder on first run.

```yaml
advanced:
  advanced-match-controls:
    game-rules:
      enabled: true
      rules:
        - DISABLE_LOCATOR_BAR
        - SET_DAYTIME
        - DISABLE_PHANTOMS
        - DISABLE_PILLAGER_PATROLS
        - DISABLE_WANDERING_TRADER
```

`rules` lists the enabled rules; anything not listed stays off, and
unknown entries are ignored. Matching is case-insensitive.

Setting survival gamemodes at match start and clearing participants'
match statistics are integral to the game and always run; they are no
longer listed here. Match phases never touch `send_command_feedback`.

Immediate respawn is also forced on internally and is not a rule: every
world gets `immediate_respawn=true` on every phase, even with game rules
disabled. A death screen would break state handling, since respawn
triggers, headstart holds, and elimination flows all assume the player
respawns instantly, and a waiting death screen leaves those states
dangling.

`announceAdvancements` is forced off internally the same way: any world
still announcing goes silent the first time anyone earns an advancement
there, and it is never turned back on. Advancement chat is handled
internally instead, so only the earner's own running match sees it
(see [Match Start and End](settings/match-start-end.md#advancement-announcements)).
Lobby players and pre-start matches see no advancement lines at all.

- `DISABLE_LOCATOR_BAR` turns off the vanilla locator bar in every world for
  the duration of the match.
- `SET_DAYTIME` sets every world to daytime.
- `DISABLE_PHANTOMS` stops phantoms from spawning while a match runs, and
  re-enables them afterwards.
- `DISABLE_PILLAGER_PATROLS` stops pillager patrols from spawning while a
  match runs, and restores them afterwards.
- `DISABLE_WANDERING_TRADER` stops wandering traders from spawning while a
  match runs, and re-enables them afterwards.

For your own start/end commands, use [Modifiers](modifiers.md) instead.
