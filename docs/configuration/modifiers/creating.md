# Creating and Sharing Modifiers

**Modifiers** are named command bundles you define in `modifiers.yml`
under `modifiers`. They are disabled by default. A modifier can run
commands when a match starts, on a recurring interval during the match, when
specific game events happen, and when the match ends, either from the console
or once for each participating player.

All command examples are the default config settings. Refer to the latest
version of `modifiers.yml` in the [GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/modifiers.yml).

Settings for modifiers are categorized under `modifiers`:

```yaml
modifiers:
  everyone-gets-beef:
    enabled: false
```

Writing commands by hand is tedious. Use
[mcstacker.net](https://mcstacker.net/) to generate up-to-date commands,
then paste them into your modifier.

## Creating Modifiers and Presets

Build entries in the GUI or inline from chat; both write the same
`modifiers.yml` blocks that manual editing produces, and manual
editing keeps working as before. Everything created this way starts
disabled.

In the GUI, a create button sits at the top-right of the modifiers
and presets lists. It prompts for a display name (you become the
author) and opens the new entry in its editor. Right-clicking any
existing entry opens the same editor. The `create` command behaves
the same: without `--author`, the sender becomes the author, and
the console records `CONSOLE`.

The modifier editor covers every field: name, description, icon,
author, the enabled toggle, trigger toggles, pre-start order, all
timing and chance options, and every command list. The preset editor
covers name, description, icon, and membership toggles. Both editors
also export, rename the id, and delete after a confirm panel.
Buttons that prompt for optional values treat a blank answer as
clearing the field back to its default.

Commands validate when you save them: unbalanced angle brackets,
empty commands, malformed `<random-num:>` or `<random-pick:>`
arguments, unknown root commands, and unknown `give` items are
refused with an error, while unknown tags and skipped pick items
only warn. `<duration>` is compass-only and warns on modifiers.
Set `advanced.misc.modifier-editor.validate-commands`
to false to skip the root and item checks; placeholder checks
always run. The command-line creator enforces the same rules; see
[Modifiers](../../commands.md#modifiers) for its flags.

Toggling a modifier back and forth in one match runs its enable
commands at most once and its disable commands at most once, so
effects like granted items never duplicate. Set
`advanced.misc.modifier-editor.prevent-duplicate-toggle` to false
for rapid testing if you are a modifier creator; every toggle
then runs.

Presets keep their display data under `meta:`, exactly like
modifiers, with the member list beside it:

```yaml
presets:
  chaos-mode:
    meta:
      name: "Chaos Mode"
      description: "Random mobs, random items, gear dice"
      item: TNT
    modifiers:
      - random-mob-spawner
      - random-item-giver
```

Presets written in the old flat shape (name and friends next to
`modifiers:`) no longer load: re-indent the four display keys under
`meta:`. Old preset share strings need a fresh export too.

## Sharing Modifiers and Presets

Export any modifier or preset to a share string: a click-to-copy chat
line you can paste to friends or the community. Import takes one back
in; colliding names get numbered automatically. Strings that fail
their checksum or schema check are refused without touching anything.

```text
/manhunt modifiers export modifier gear-dice
/manhunt modifiers export preset chaos-mode
/manhunt modifiers import modifier JMH1D:...
```

The modifiers and presets menus carry an import loom in the
bottom-right corner that prompts for the string, and every editor has
an export button that copies its own share string.

## Example

A minimal modifier that hands every participant a starter kit looks like this:

```yaml
modifiers:
  starter-kit:
    enabled: false
    behavior:
      0:
        commands:
          player:
            - "give <p> cooked_beef 8"
          hunter: []
          speedrunner: []
          console: []
          console-cleanup: []
          player-cleanup: []
```

The default `modifiers.yml` ships more examples to copy from: `full-iron-kit`,
`speedrunner-health-advantage`, `random-mob-spawner`, `random-item-giver`,
`random-start-resources`, `gear-dice`, `regen-on-kill`, `diamond-on-advancement`,
`fireres-on-nether-enter`, `hunter-start-debuffs` (slowness II plus
weakness I on every hunter at match start), `hunter-post-start-speed`
(speed for hunters once the game actually begins), `get-stronger-on-kill`,
and `speedrunner-gapple-on-low-hp`.

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

Runs every 3 seconds for speedrunners. The first line gives a golden
apple when health is at most 7 and more than 300 seconds passed since
the last give; otherwise it exits, which skips the second line while
cooling down. The second line stamps the give time into a per-player
flag. Runners who never got one are treated as due.
