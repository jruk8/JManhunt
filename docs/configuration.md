# Configuration

The plugin creates `config.yml` in its data folder. You can find it in
`plugins/JManhunt/config.yml`.

See the individual configuration pages for detailed documentation:

## Sections

- [Settings](configuration/settings.md): match start & end, compass
  tracking, hunters & speedrunners, win conditions, and game boosts.
- [World Engine](configuration/world-engine.md): grid-based world
  engine setup, cell pre-generation commands, world border, and troubleshooting.
- [Custom Modifiers](configuration/modifiers.md): named command bundles,
  run timing, and match-end cleanup.
- [Built-in Match Commands](configuration/match-commands.md): default
  game-state actions and match start/end command lists.
- [Statistics & PlaceholderAPI](configuration/statistics.md): career
  statistics database and placeholder configuration.
- [Sounds](configuration/sounds.md): sound configuration for game events.

## Core Settings

Under `text-format`, you can choose the message parser used for
`messages.yml`:

```yaml
# Message parser used for messages.yml: minimessage or legacy.
text-format: minimessage
```

`minimessage` enables MiniMessage formatting; `legacy` keeps classic `&`
color codes. Placeholder formatting in `config.yml` follows the same setting.
Changes apply after `/manhunt reload`.
