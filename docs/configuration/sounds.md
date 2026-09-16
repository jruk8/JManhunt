# Sounds

Sound names are namespaced Minecraft keys (e.g. `block.note_block.pling`).
It's recommended to use a sound explorer like
[mudkipdev's Minecraft Sound Explorer](https://mudkipdev.github.io/minecraft-sound-explorer/).

Sounds are configured under the `sounds` section in `config.yml`:

```yaml
sounds:
  neutral-sound:
    enabled: true
    sound: block.note_block.pling
    pitch: 1.0
    volume: 1.0
```

Each sound entry supports `enabled`, `sound`, `pitch`, and `volume`.

## Game Sounds

Under `sounds.game`, each entry plays at a fixed moment in the match:

- `autostart-countdown`: during the autostart countdown and the hunter spawn countdown.
- `speedrunner-death`: when a speedrunner dies, by any means.
- `hunter-death`: when a hunter dies, by any means.
- `win-sound`: when the speedrunners win.
- `fail-sound`: when the hunters win.
- `cancelled-sound`: when a match is cancelled. Defaults to the hunters win sound.
- `announce.hunter`: heard by each hunter when roles are announced.
- `announce.speedrunner`: heard by each speedrunner when roles are announced.

Under `sounds.neutral-sound`, the default sound plays for general feedback
such as match start, hunter spawn after the start delay, and command
confirmations.