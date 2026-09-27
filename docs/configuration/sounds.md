# Sounds

Sound names are namespaced Minecraft keys (e.g. `block.note_block.pling`).
It's recommended to use a sound explorer like
[mudkipdev's Minecraft Sound Explorer](https://mudkipdev.github.io/minecraft-sound-explorer/).

Sounds are configured in `sounds.yml`:

```yaml
ui:
  neutral-sound:
    enabled: true
    sound: block.note_block.pling
    pitch: 1.0
    volume: 1.0
  angry-sound:
    enabled: true
    sound: block.bamboo_wood.place
    pitch: 1.0
    volume: 1.0
  destructive-sound:
    enabled: true
    sound: block.cherry_wood_hanging_sign.step
    pitch: 0.8
    volume: 1.0
```

Each sound entry supports `enabled`, `sound`, `pitch`, and `volume`.

## UI Sounds

Under `ui`, the shared interface sounds play across commands, GUIs, and
the setup tutorial:

- `neutral-sound`: confirmations such as match start, hunter spawn after
  the start delay, and setting updates.
- `angry-sound`: validation errors and invalid input.
- `destructive-sound`: confirmed deletes, like removing a modifier or
  preset.

## Game Sounds

Under `game`, each entry plays at a fixed moment in the match:

- `autostart-countdown`: during the autostart countdown and the hunter spawn countdown.
- `autostart-cancelled`: when an autostart countdown is cancelled.
- `speedrunner-death`: when a speedrunner dies, by any means.
- `hunter-death`: when a hunter dies, by any means.
- `win-sound`: when the speedrunners win.
- `fail-sound`: when the hunters win.
- `cancelled-sound`: when a match is cancelled. Defaults to the hunters win sound.
- `announce.hunter`: heard by each hunter when roles are announced.
- `announce.speedrunner`: heard by each speedrunner when roles are announced.

## Compass Sounds

Under `compass`, heard by the compass holder:

- `left-click`: when left-click cycling changes the target lock.
- `right-click`: on a compass click refresh that tracks a target, and
  again when a click-initiated analysis resolves onto a target.
- `analysis`: ticked while an analysis runs, every
  `advanced.misc.analyze.sound-interval-seconds`.
- `failure`: when a compass click fails to track: target too near or
  too far, signal interference, no target, or a scroll with nothing
  to cycle to.

## Chat Sounds

Under `chat`:

- `team-chat`: heard by team chat recipients on each message.
