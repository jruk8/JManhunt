# Admin GUI

Bare `/manhunt` opens a chest-menu admin panel for players holding
`jmanhunt.gui` (default: op). Everyone else sees their match roster,
exactly as before, and the console always gets status. Every GUI edit
can also be made from chat or the console through
[`/manhunt config`](commands.md#editing-settings-in-game).

The first ever open shows a one-time setup nudge: confirm to start the
interactive setup guide, or skip forever to dismiss it and open the
panel. Starting setup or enabling the world engine dismisses it too.

## Root Menu

- **Settings** (chest): a quad over the four setting categories.
- **History** (book): hover to read lifetime server stats: matches,
  kills, wins, damage, and playtime. It is display-only and opens nothing.
- **Modifiers** (book): a twin panel over the modifier and preset
  browsers with live enabled counts, with Back returning here.
- **Lobby Overrides** (glass): points the session at one lobby so
  every edit lands as an override; yellow while a session runs.
  Left-click to set a lobby id (or `GLOBAL`), right-click to go
  global. Opening the GUI always starts a fresh global session.

## Editing Settings

Open a category, then drill through sections to a setting. Sections
with a single subsection open it directly instead of showing a
one-button menu. Every section button carries its own icon and a
one-line description above the entry count. Each setting button shows
the description, current value, path, type, allowed values, and
default. Buttons glow when the value differs from the default.

- Toggles flip on click; choices cycle to the next option.
- Numbers and text open a dialog: bounded numbers get a slider,
  everything else gets a text field. Invalid input is rejected with an
  error naming the allowed values, and the old value is kept.
- Right-click any setting to reset it to the default after a confirm
  panel showing old versus default.
- String lists show one paper per entry plus a stick to append; click
  to edit, right-click to delete after a confirm panel. Right-click
  the list entry itself to reset the whole list to defaults.

## Lobby Overrides

With an override session running, every settings and modifiers menu
reads effective values for that lobby: overrides first, globals
otherwise. Glow means overridden instead of modified, and overridden
rows carry a red `Overrides for Lobby N` line. Clicks, toggles,
cycles, dialogs, and list edits all write overrides with the same
validation as globals.

- Shift-left-click any setting, list, or category to remove the
  override (categories confirm first, then clear everything below).
- Right-click a setting or list to remove its override.
- Shift-left-click a modifier to remove its override; presets drop
  every member override at once.
- The same overrides can be edited from chat or the console through
  [`/manhunt override`](commands.md#per-lobby-overrides).

## Modifier Creator

The modifiers and presets lists carry a create button at the
top-right and an import loom at the bottom-right. Create prompts
for a display name (you become the author) and opens the new entry
in its editor; right-clicking any existing entry opens the same
editor. Preset lore lists up to 8 members, then collapses to
`..and N more`.

Each editor is a quad. The modifier root holds Meta, Behavior,
Export, and Delete Modifier; the preset root holds Meta, Modifiers,
Export, and Delete Preset. Meta edits name, description, icon, and
author; right-clicking the name renames the id instead. Every field
prompts or cycles in place, and blank answers clear optional
fields. Setting the icon chats a confirmation naming the material.

Behavior is a twin panel over Behavior Options and Command Lists.
Behavior Options holds Runs On trigger checkboxes, Interval
Settings (blocked until INTERVAL is selected), Execution, Delay,
and Success Chance; the enabled toggle lives on the modifier list
row instead. Every option row follows the settings schema:
description, value, path, type, allowed values or choice bullets,
and default. Rows glow when they differ from the engine default.
Right-click any row to reset it after a confirm panel.

Command Lists shows the six lists (player, speedrunner, hunter,
console, and the two cleanups) with live line counts; non-empty
lists glow. Each list menu shows one paper per line with a
trailing stick to append: click a line to edit, right-click to
delete after a confirm. Add and edit dialogs list every available
tag with a short note. Saving validates the line: unbalanced
brackets, empty commands, malformed random args, unknown root
commands, and unknown `give` items are refused with a chat error,
while unknown tags and skipped pick items only warn. Set
`settings.server.advanced.validate-modifier-editor-commands` to
false to skip the root and item checks. Each save chats which
ordinal line was set. See
[Creating Modifiers and Presets](configuration/modifiers.md#creating-modifiers-and-presets).

## Sounds

Clicks and write feedback come from `sounds.yml` (see
[Sounds](configuration/sounds.md)). A click that runs an action
plays the compass click once; opening a dialog and committing a
value play `ui.neutral-sound`; validation failures and blocked
clicks play `ui.angry-sound` plus a chat error. Fast clicks never
double-fire.

## QA Checklist

1. Open both editor roots and confirm the four quad buttons plus Back.
2. In Behavior Options, deselect INTERVAL and confirm Interval Settings
   blocks with an error; reselect it, change the interval, then
   right-click the row and confirm the reset.
3. In Command Lists, save `asd asd` and confirm refusal, then save
   `give <p> cooked_beef 8` and confirm the ordinal chat line.
4. Toggle `validate-modifier-editor-commands` off, save `asd asd`,
   then toggle it back on.
5. Import a share string through the bottom-right loom in both lists.
