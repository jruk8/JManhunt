---
icon: material/plus-circle
---

# Creating and Sharing Modifiers

> For: intermediate admins making mods, plus advanced authors going deep.

Modifiers are named command bundles: one `.yml` file each under
`mods/modifiers/`, presets beside them under `mods/presets/`. The id
is the filename minus `.yml`, so `gear-dice.yml` is the `gear-dice`
modifier. Subdirectories are allowed and load recursively. A modifier
can run commands when a match starts, on an interval, on game events,
and when the match ends, from the console or per player. New entries
start disabled.

Bundled defaults ship with the plugin and copy in the first time each
folder is created. Deleting a file removes it for good: defaults are
never restored. All examples below match the bundled files; the latest
live in the [GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/mods/).

Writing commands by hand is tedious. Use
[mcstacker.net](https://mcstacker.net/) to generate up-to-date commands,
then paste them into your modifier.

## Creating Modifiers and Presets

Build entries in the GUI or inline from chat; both write the same
per-file entries manual editing produces. Everything lands as a
root-level file: `mods/modifiers/<id>.yml` or `mods/presets/<id>.yml`.
Ids use letters, numbers, `-` and `_`, up to 64 chars; a taken name
gets ` {n}` numbering.

In the GUI, the create button sits top-right of the lists. It prompts
for a display name (you become the author) and opens the new entry in
its editor; right-clicking any existing entry opens the same editor.
The `create` command behaves the same (the console records `CONSOLE`
as author). The modifier editor covers every field: meta, triggers,
timing and chance options, and every command list. Blank answers clear
optional fields back to defaults. See
[Your First Modifier](first-modifier.md) for a worked kit example.

Commands validate on save: unbalanced brackets, empty commands,
malformed random args, unknown root commands, and unknown `give` items
are refused with an error, while unknown tags only warn. Set
`advanced.misc.modifier-editor.validate-commands` to false to skip the
root and item checks. See
[Modifiers](../../play/commands.md#modifiers) for the create flags.
Lines edited outside the GUI skip that check, so at runtime an
unterminated `<run>` warns in the console naming the line instead of
running silently.

Toggling a modifier mid-match runs its start commands at most once and
its cleanup at most once, so rewards never duplicate. Set
`advanced.misc.modifier-editor.prevent-duplicate-toggle` to false for
rapid testing; every toggle then runs.

Presets keep display data under `meta:` with the member list beside it:

`mods/presets/chaos-mode.yml`:

```yaml
meta:
  name: "Chaos Mode"
  description: "Random mobs, random items, gear dice"
  item: TNT
modifiers:
  - random-mob-spawner
  - random-item-giver
```

Member ids with no loaded modifier show as `'id' missing` in red and
are skipped by toggles; a preset with nothing but missing members
refuses to toggle.

Presets written in the old flat shape (display keys next to
`modifiers:`) no longer load: re-indent the four display keys under
`meta:`, and re-export old share strings. Upgrading from the single
`modifiers.yml` layout: copy entries into per-file form by hand,
nothing migrates automatically.

## Sharing Modifiers and Presets

Export any entry to a share string: a click-to-copy chat line for
friends or the community. Import takes one back in, always disabled
no matter the export state; colliding names get numbered
automatically. Strings that fail their checksum or schema check are
refused without touching anything.

```text
/manhunt modifiers export modifier gear-dice
/manhunt modifiers export preset chaos-mode
/manhunt modifiers import modifier JMH1D:...
```

The lists carry an import button that prompts for the string, and every
editor has an export button for its own share string.

## Example

A minimal starter kit for every participant:

`mods/modifiers/starter-kit.yml`:

```yaml
enabled: false
behavior:
  0:
    commands:
      player:
        - "give <p> cooked_beef 8"
```

The bundled files ship more examples to copy from: `full-leather-kit`,
`speedrunner-health-advantage`, `random-mob-spawner`,
`random-item-giver`, `random-start-resources`, `gear-dice`,
`regen-on-kill`, `diamond-on-advancement`, `fireres-on-nether-enter`,
`hunter-start-debuffs`, `get-stronger-on-kill`,
`speedrunner-gapple-on-low-hp`, `vip-escort`, and `infection`.

### Get Stronger On Kill

Runs on every player kill (executor: the killer) and every respawn
(executor: the respawner). The kill is recorded before the commands
run, so the session kill total they read is already fresh.

Each run works in half-hearts, with two tunables at the top of the
player list: `maxhp` (60: total cap, i.e. 30 hearts) and `step`
(4: gain per kill, i.e. 2 hearts):

1. `<pflag:basehp>` caches the player's baseline max health the
   first time the modifier runs for them.
2. `target` is baseline plus step times session kills, capped at
   `maxhp`, and `minecraft:max_health` base is set to it.
3. `instant_health` heals 2 hearts on the spot, even at the cap.
4. `saturation` refills only the hunger points actually missing.

The respawn trigger re-applies the same computed target from the
still-current session counter, so a killer who dies keeps their
earned hearts. Player cleanup restores each cached baseline, and
leaves players the modifier never ran for untouched.

### Gapple On Low HP

Runs every 2 seconds for speedrunners. Lines exit cheapest-first:
the cooldown read (no stamp), then the health check, then the
inventory scan. The last line is the gate itself: `<pcooldown>` yields
`true` and stamps at most every 300 seconds, so the apple lands only
when all four pass. Runners who never got one are treated as due.

### VIP Escort

Flags one random speedrunner as the VIP after the pre-start window
and announces them; the runners lose outright when the VIP dies. Run
it with speedrunner lives at `-1` so everyone else respawns forever
while the VIP stays mortal. Three blocks: an `ON_START` pick into a
`gflag`, an `ON_DEATH` guard that eliminates the VIP and ends the
match for the hunters, and a 1-second `INTERVAL` block with two
toggles at the top of the console list: `effects` (the nested
`effect_list` of `[id, amplifier]` pairs, reapplied with the
interval seconds rounded up plus one) and `no_armor` (clears all
four armor slots every second, warning the VIP with a message and
sound when something was actually worn).

### Infection

Speedrunners who die their final death come back as hunters via
`<pswitch>`. The `ON_DEATH` guards read the victim former role from
`<args:2>` plus their live role from `<prole>`, so hunter deaths and
non-final runner deaths exit quietly, and disconnect removals (which
never fire `ON_DEATH`) are never converted. When no runners remain,
the engine ends the match with a hunter win on its own.
Engine elimination and win announcements still run, so Infection
lines may duplicate engine chat and sounds. A second block runs every
12 seconds per hunter: converted hunters get sporadic 5-second
blindness plus occasional rotten flesh and bones (each item
candidate independently fails `give_fail_chance` percent of the
time, 80 by default), while root hunters exit untouched. Effect
durations and item counts live in the block's `effect_list`
(`[id, amplifier, seconds]`) and `item_list` (`[item, min, max]`)
settings.
