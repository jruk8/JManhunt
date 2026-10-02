---
icon: material/cog
---

# Configuration

> For: intermediate admins. Start at [Settings](configuration/settings.md).

The plugin creates `config.yml` in its data folder. You can find it in
`plugins/JManhunt/config.yml`.

See the individual configuration pages for detailed documentation:

## Sections

- [Settings](configuration/settings.md): match start & end, compass
  tracking, hunters & speedrunners, win conditions, and game boosts.
- [World Engine](configuration/world-engine.md): grid-based world
  engine setup, cell pre-generation commands, world border, and troubleshooting.
- [Modifiers](configuration/modifiers.md): named command bundles,
  run timing, and match-end cleanup.
- [Game Rules](configuration/game-rules.md): built-in
  game-state actions applied at match start and end.
- [Statistics](configuration/statistics.md): career
  statistics database and placeholder configuration.
- [Sounds](configuration/sounds.md): sound configuration for game events.

## What Settings Cover

Settings modify the game flow. This includes things like:

- autostart when enough players join
- starting the game only when a speedrunner hits a hunter
- adventure mode during the pre-start window
- compass tracking rules and distances
- friendly fire rules for hunters and speedrunners
- delayed respawns and per-role lives
- head starts for either side
- alternate win conditions (exit End, survive time, acquire item, reach advancement)
- custom bartering loot tables for higher ender pearl pulls

The built-in challenges (no-jump, one-heart and lucky-blocks) come
from the companion plugin
[JManhunt-Challenges](https://github.com/jruk8/JManhunt-Challenges).

## Core Settings

Messages in `messages.yml` use MiniMessage formatting. Classic `&` color
codes (like `&7` or `&6`) still work anywhere: they convert
automatically. Placeholder formatting in `config.yml` follows the same
rules. Changes apply after `/manhunt reload`.

Set any message in `messages.yml` to an empty string (`""`) to disable it:
it will never be sent. (A single space still counts as a message.) Each
role's color lives under `role-colors:` as one key per role, used by role
headers and every message that names a role; `&` codes work there too.

## Update Checker

JManhunt compares the running version against the latest GitHub release
(never a pre-release) and tells admins when an update is out. The check
runs once, shortly after the plugin enables; admins who join later hear
about a pending update when they log in. Only players holding
`jmanhunt.admin` (default: op) see the notice.

```yaml
update-checker:
  enabled: true
  releases:
    major: true
    minor: true
    hotfix: false
```

Each severity toggles separately: with the defaults, new major and minor
releases notify while hotfixes stay silent. Failed checks (no network,
GitHub down) log one warning and never affect startup.

## Upgrading

This release regroups every setting under four categories
(`settings.match`, `settings.compass`, `settings.players`,
`settings.server`) and moves sounds to `sounds.yml`. Old flat
`settings.*` keys and the `sounds:` block are dropped: affected values
reset to fresh defaults, and startup logs a warning naming the stale
blocks. Re-apply your tweaks under the new paths after upgrading; every
other top-level key keeps its path.

## Anonymous Statistics

JManhunt collects anonymous usage statistics through bStats, which helps guide
development. This is enabled by default and can only be changed directly in
`config.yml`.

```yaml
send-anonymous-statistics: true
```

Set it to `false` and restart the server to opt out.
