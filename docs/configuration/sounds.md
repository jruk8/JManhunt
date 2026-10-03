---
icon: material/volume-high
---

# Sounds

> For: intermediate admins styling feedback.

Sound names are namespaced Minecraft keys (e.g.
`block.note_block.pling`). A sound explorer like
[JSoundExplorer](https://jruk8.github.io/JSoundExplorer/)
helps pick them.

Sounds live in `sounds.yml`. Each entry supports `enabled`, `sound`,
`pitch`, and `volume`:

```yaml
ui:
  neutral-sound:
    enabled: true
    sound: block.spawner.fall
    pitch: 1.0
    volume: 1.0
```

## UI Sounds

Shared interface sounds across commands, GUIs, and the setup tutorial:

| Entry | Plays on |
| --- | --- |
| `neutral-sound` | Confirmations: match start, hunter spawn, setting updates |
| `angry-sound` | Validation errors and invalid input |
| `destructive-sound` | Confirmed deletes, like removing a modifier |

## Game Sounds

Each entry plays at a fixed match moment:

| Entry | Plays on |
| --- | --- |
| `autostart-countdown` | Autostart and hunter spawn countdowns |
| `autostart-cancelled` | A cancelled autostart countdown |
| `speedrunner-death` | A speedrunner death (`block.dried_ghast.ambient`) |
| `hunter-death` | A hunter death (`item.spyglass.use`) |
| `win-sound` | A speedrunner win (`block.beacon.activate`, pitch 1.1) |
| `fail-sound` | A hunter win (`block.sculk_shrieker.break`, pitch 0.7) |
| `cancelled-sound` | A cancelled match (defaults to the hunter win sound) |
| `match-started` | Match start, after any prestart (`entity.illusioner.ambient`, pitch 0.9) |
| `announce.hunter` | Each hunter at role announcement |
| `announce.speedrunner` | Each speedrunner at role announcement |

## Compass Sounds

Heard by the compass holder:

| Entry | Plays on |
| --- | --- |
| `left-click` | Target lock changes |
| `right-click` | A click refresh (and analysis resolution) onto a target |
| `analysis` | Ticked while an analysis runs |
| `failure` | A failed click: too near or far, interference, no target |
| `cost-too-high` | An unaffordable analysis cost (defaults to `failure`) |
| `cost-used-exp` | An experience charge |
| `cost-used-health` | A health charge |
| `cost-used-saturation` | A hunger charge (one picked at random when several apply) |

## Chat Sounds

| Entry | Plays on |
| --- | --- |
| `team-chat` | Each team chat message, for recipients |
