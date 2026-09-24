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

- **Settings** (chest): the four setting categories.
- **History** (book): hover to read lifetime server stats: matches,
  kills, wins, damage, and playtime. It is display-only and opens nothing.
- **Modifiers** (book): the modifier and preset browser, with Back
  returning here.

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

## Sounds

Menu navigation clicks and write feedback come from `sounds.yml`
(see [Sounds](configuration/sounds.md)): dialog success plays
`ui.neutral-sound`, validation failures play `ui.angry-sound` plus a
chat error.
