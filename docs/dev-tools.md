# Developer Tools

> These commands exist for developers — primarily for authoring the
> default lobby presets. They offer no safety rails and are not intended
> for production servers.

## Schematic Tools

`/manhunt dev schem` saves and loads vanilla `.nbt` structure files in
`JManhunt/settings/world-engine/lobby-schematics/`, the same directory
the [lobby presets](configuration/world-engine.md#lobby-presets) read
from. Requires `jmanhunt.command.dev.schem` (default: op).

```text
/manhunt dev schem pos1
/manhunt dev schem pos2
/manhunt dev schem save <name>
/manhunt dev schem load <name>
/manhunt dev schem list
```

`pos1` and `pos2` record the corners of a region: your feet block at the
moment each runs. `save <name>` captures everything between the corners
(including entities) to `<name>.nbt`, overwriting without asking. `load
<name>` pastes the file into your current world centered on your feet
block. The commands are world agnostic: save and load work in whatever
world you stand in. Only `list` works from the console; the rest need a
player position and refuse console senders.

`dev` is deliberately hidden from `/manhunt <tab>` completion, but once
typed, its subcommands complete normally.
