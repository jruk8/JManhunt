# Mod Files

Modifiers live as one `.yml` file each under `mods/modifiers/`,
presets as one file each under `mods/presets/`, both inside the
plugin data folder. The id is the filename minus `.yml`, so
`mods/modifiers/gear-dice.yml` is the `gear-dice` modifier.
Subdirectories are allowed in both trees and load recursively.

Bundled defaults ship with the plugin and are copied in the first
time each folder is created. Deleting a file removes it for good:
defaults are never restored.

A modifier file holds one entry with no wrapper key:

`mods/modifiers/starter-kit.yml`:

```yaml
enabled: false
meta:
  name: "Starter Kit"
behavior:
  0:
    commands:
      player:
        - "give <p> cooked_beef 8"
```

A preset file holds its display data plus member ids, also with no
wrapper key:

`mods/presets/chaos-mode.yml`:

```yaml
meta:
  name: "Chaos Mode"
modifiers:
  - random-mob-spawner
  - random-item-giver
```

The GUI editor and the `create`/`import` commands always write new
root-level files. Ids use letters, numbers, `-` and `_`, up to 64
chars. Toggling `enabled` rewrites only that one file, atomically.

## Load order

Modifiers load fully before presets. Inside each tree, every
directory loads its subdirectories first (sorted naturally by name,
each processed with this same rule), then its own `.yml` files
sorted naturally by filename. Root-level files therefore load after
everything nested. Sorting is Explorer style: case-insensitive,
with digit runs compared numerically (`mod2` before `mod10`).

## Duplicates, unknown, and failed files

Two files of the same kind with the same id in different folders
are a duplicate: the first in load order wins and the rest are
skipped, with a console warning naming both paths. A modifier and
a preset may share an id; they are separate namespaces.

Non-`.yml` files are reported as unknown files on reload. Temp
files and dotfiles are ignored silently. A `.yml` file that fails
to parse or validate is logged as `<path>: <error>`, skipped, and
reported; fixing it later reloads quietly.

A preset that names an unknown modifier id warns on the console
and skips that member in memory only: the file keeps the id, so a
later reload picks it up once the modifier exists.

`/manhunt reload` prints one confirmation line, then up to four
report lines (empty ones are skipped):

```text
Reloaded the Manhunt engine in 40ms.
» 1 new modifier, removed 1 preset
» Unknown files: notes.txt and 1 more
» Failed to parse modifiers/presets: gear-dice.yml and 1 more
» Duplicate id found: vanilla-plus
```

The first report line counts added and removed ids (a rename shows
as one of each; plain edits do not count). The rest name the first
entry in load order plus how many more follow. Every string is
configurable under `manhunt.reload-*` in `messages.yml`.

Upgrading from the single `modifiers.yml` layout: copy entries into
per-file form by hand. Nothing migrates automatically.
