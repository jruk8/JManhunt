---
icon: material/puzzle
---

# Modifiers

> For: everyone spicing up the game. No scripting needed.

Modifiers are ready-made game twists: starter kits, random chaos,
buffs, and rule changes. 19 modifiers and 3 presets ship with the
plugin, all switched off until you switch them on.

## Switch Some On

Open `/manhunt` and pick **Modifiers**. Click entries to switch them
on and off; the counts stay live. Try `gear-dice` (random gear),
`random-mob-spawner`, `full-iron-kit`, or the `chaos-mode` preset.

Prefer typing? This does the same thing:

```text
/manhunt modifiers setmod gear-dice true
```

## Taste One in 3 Minutes

1. Enable the beef modifier:
   `/manhunt modifiers setmod everyone-gets-beef true`.
2. Start a match: every participant gets 8 steak.
3. Want 16 instead, or your own kit? See
   [Your First Modifier](modifiers/first-modifier.md).

## Import from Discord

The community shares modifiers and presets as click-to-copy strings in
our [Discord](https://discord.gg/hkWmCVmWDC) modifier forum. Taking one
in is one step: open the Modifiers panel and use the import button, or
run:

```text
/manhunt modifiers import modifier JMH1D:...
```

Sharing yours works the other way with `export`, which prints your own
string. See [Creating and Sharing](modifiers/creating.md).

## Make Your Own

Plain Minecraft commands need no scripting: `give @p cooked_beef 8`
works as-is inside a modifier. Build entries in the GUI editor (create
button, top-right of the lists) or straight from chat. Start here:

- [Your First Modifier](modifiers/first-modifier.md): a runner kit,
  zero scripting.
- [Creating and Sharing](modifiers/creating.md): the full creation
  flow, validation rules, and share strings.
- [Behaviors](modifiers/behaviors.md): triggers, timing, and command
  lists.
- [Tags: Basics](modifiers/tags-basics.md): straightforward tags like
  `<random-num:4,12>`.
- [Tags: Advanced](modifiers/tags-advanced.md): the full JMHScript
  cheatsheet and reference.

## How Modifiers Think

One `.yml` file per modifier under `mods/modifiers/`, named after its
id. Each file has `enabled`, `meta:` (name, description, icon,
author), and `behavior:` with numbered blocks. Each block picks
triggers under `runs-on:` (`ON_START`, `INTERVAL`, kill and portal
events), optional timing, and `commands:` lists (`player`, `hunter`,
`speedrunner`, `console`, plus cleanups).

Tags resolve to text first, then the whole line runs as one console
command. A line resolving to exactly `null` never runs; a lone `exit`
stops the list. Tags nest inside out, flags hold state (`<gflag>`
match-wide, `<pflag>` per player), and `<if>` branches. Dry-run any
line from chat:

```text
/manhunt modifiers test hunter give <p> bread
```

One complete modifier, annotated (`mods/modifiers/gapple-comeback.yml`):

```yaml
enabled: false
meta:
  name: "Gapple Comeback"
  description: "Low runners get a gapple every 10 seconds"
  item: GOLDEN_APPLE
  author: You
behavior:
  0:
    runs-on:
      - INTERVAL
    options:
      interval-settings:
        interval: 10
    commands:
      speedrunner:
        # 6 health or less: hand a gapple, else stop the list
        - '<if:"<pstat:<p>,health> le 6","give <p> golden_apple","exit">'
        # only reached on a hit: confirm in yellow
        - '<pmessage:<p>,\<yellow\>Second wind!'
```
