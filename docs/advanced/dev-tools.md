# Developer Tools

> For: advanced users and developers only.

> These commands exist for developers: primarily for authoring
> default lobby presets. They offer no safety rails and are not intended
> for production servers.

## Schematic Tools

`/manhunt dev schem` saves and loads lobby schematics in
`JManhunt/settings/world-engine/lobby-schematics/`. The
[lobby presets](../play/lobby-system/index.md#lobby-presets) do not
read that directory: they paste only the bundled
`dev/lobby-schematics/` resources, so ship a finished preset by
copying its file there and rebuilding. Requires
`jmanhunt.command.dev.schem` (default: op).

```text
/manhunt dev schem pos1
/manhunt dev schem pos2
/manhunt dev schem save <name>
/manhunt dev schem load <name>
/manhunt dev schem list
```

`pos1` and `pos2` record the corners of a region: your feet block at the
moment each runs. `save <name>` captures everything between the corners
(including entities) to `<name>.jmhlobby`, overwriting without asking.
`load <name>` pastes the file into your current world centered on your
feet block. Only `list` works from the console; the rest need a
player position and refuse console senders.

## The .jmhlobby Format

A `.jmhlobby` file is a zip containing exactly two entries: `schem.nbt`
(the vanilla structure bytes) and `lobby.json` (the lobby boxes and
teleports collected with them). Saving collects every lobby bounds box
at least partly inside the region, plus every lobby teleport whose
block sits inside it. Coordinates in the manifest are relative to the
region's minimum corner, so pasting anywhere rebuilds the entries at
the right offsets.

A teleport whose lobby collected no boundary is an orphan: it is
dropped from the bundle with a console warning naming the lobby and
the teleport. A region with no bounds or teleports inside still saves
a valid bundle with empty lists.

On load, the structure pastes first, then the bundled bounds and
teleports are written into the lobby config at the paste corner,
creating missing lobby entries and overwriting the bounds and
teleports of existing ones (overrides and other fields are preserved).
Overwritten lobby ids always warn in the console. Command loads that
would overwrite ask first: rerun the same load within 10 seconds to
confirm. Automated preset pastes during lobby generation never ask.
Entries only build when the paste lands in the lobby world: loading
anywhere else places blocks only, since lobby entries are
lobby-world coordinates. With the world engine off, nonzero lobbies
are skipped. Hand-written bundles listing one lobby twice apply the
last entry.

`lobby.json` looks like this:

```json
{
  "format": 1,
  "origin": {
    "x": 100,
    "y": 64,
    "z": 100
  },
  "bounds": [
    {
      "lobby": 0,
      "min": {
        "x": 0,
        "y": 0,
        "z": 0
      },
      "max": {
        "x": 9,
        "y": 9,
        "z": 9
      }
    }
  ],
  "teleports": [
    {
      "lobby": 0,
      "x": 5.5,
      "y": 1.0,
      "z": 5.5,
      "yaw": 90.0,
      "pitch": 0.0
    }
  ]
}
```

`origin` is the save-time minimum corner. Bounds are relative min/max
block corners per lobby id; teleports are relative coordinates rounded
to one decimal place, plus look direction.

Legacy `.nbt` files still load through the old blocks-only path. Where
both exist for one name, the bundle wins; `list` shows both kinds.

`dev` is deliberately hidden from `/manhunt <tab>` completion.

## Debug Output

`/manhunt debug [INFO|WARN|SEVERE]` sets the level of the terse
lifecycle lines (cell fetches, buffer fills, match starts and ends,
end-cell operations, border mode changes, and portal reroutes) for
yourself, or for the console when run from it. Bare `/manhunt debug`
toggles output on at INFO or off. Each line carries its level tag:
`[INFO]` for routine flow, `[WARN]` for degraded paths such as lobby
fallbacks, `[SEVERE]` for failures needing admin attention. SEVERE
shows only severe lines, WARN adds warnings, INFO shows all. Nothing
persists across restarts; there is no config default.

## Lobby Preset Internals

Presets are dev-time data: they live in `Core/dev.yml` inside the jar,
never in the plugin data folder, and cannot be edited at runtime. To
change a preset's schematic or commands, edit that file (plus
`dev/lobby-schematics/` for the artwork) and rebuild.

Each preset pastes its schematic from the bundled
`dev/lobby-schematics/` resources with the structure midpoint at
0,64,0, then runs its console commands (meant for lobby teleports;
`{world}` and `{preset}` are substituted) before the first teleport
lands. A missing schematic warns in the console and leaves void, with a
bare spawn at y=65 as the fallback. Where a preset name has a
`.jmhlobby` bundle, it wins over the legacy `.nbt`, and its bundled
bounds and teleports build into the lobby config at the paste corner.
Files dropped into the data-folder `lobby-schematics/` directory are
ignored by preset pastes: that folder is the dev authoring workspace
only.
