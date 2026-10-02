# Admin GUI

`/manhunt` opens the admin panel (needs `jmanhunt.gui`, default: op).
Everyone else sees their teams, and the console always gets status.
Anything here can also be changed with
[`/manhunt config`](commands.md#editing-settings-in-game).

First time opening it, you get a setup panel instead: green sets
everything up in one click, red starts the step-by-step tour in chat.
It only shows once (see [Getting Started](getting-started.md)).

## Root Menu

- **Need Help?** (glowing recovery compass, first slot): runs
  `/manhunt support` in chat and closes the panel.
- **Settings** (chest): General and Advanced panels. General holds a quad
  over the four everyday setting categories; Advanced holds a quad over
  match controls, the world engine, lobbies, and misc power toggles.
- **History** (book): hover to read lifetime server stats: matches,
  kills, wins, damage, and playtime. It is display-only and opens nothing.
- **Modifiers** (book): a twin panel over the modifier and preset
  browsers with live enabled counts, with Back returning here.
- **Lobby Overrides** (glass): points the session at one lobby so
  every edit lands as an override; yellow while a session runs.
  Left-click to set a lobby id (or `GLOBAL`), right-click to go
  global with a confirmation click. Opening the GUI always starts a
  fresh global session.

## Editing Settings

Open a category, then drill through sections to a setting. Sections
with a single subsection open it directly instead of showing a
one-button menu. Every section button carries its own icon and a
one-line description above the entry count. Each setting button shows
the description, current value, path, type, allowed values, and
default. Buttons glow when the value differs from the default.

- Toggles flip on click; choices cycle to the next option.
- Numbers and text open a dialog: bounded numbers get a slider,
  everything else gets a text field. The dialog shows the
  description first, then the current value and allowed range.
  Invalid input is rejected with an error naming the allowed
  values, and the old value is kept.
- Text settings holding an item name (like a concrete color)
  preview that item's sprite at the top of the dialog.
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

The modifiers and presets lists carry an import loom at the
bottom-right. The presets list keeps its create button at the
top-right; the modifiers list opens a Modifier Editor panel there
instead, pairing Test a Command with the same create flow. Create
prompts for a display name (you become the author) and opens the
new entry in its editor; right-clicking any existing entry opens
the same editor. Preset lore shows the description first, then up
to 8 members, then collapses to `..and N more`. A preset with no
members shows `No modifiers configured!` instead, and clicking it
to enable plays the angry sound with a chat error rather than
flipping state.

Test a Command dry-runs up to five command boxes through the real
tag pipeline with mock stats, like the chat test command: gameplay
lines still execute for real, and tag errors fail the run with
the warning text. Blank boxes drop and the rest collapse in order;
submitting five blanks chats an error and keeps the dialog open.
The remember checkbox keeps the role and boxes per player across
sessions (`advanced.misc.modifier-editor.remember-gui-commands`
preselects it for first-timers).

Each editor is a quad. The modifier root holds Meta, Behavior,
Export, and Delete Modifier; the preset root holds Meta, Modifiers,
Export, and Delete Preset. Meta edits name, description, icon, and
author; right-clicking the name renames the id instead. The name
lore prints the internal id under the Current line, and every edit
refreshes the Current lore at once. Every field prompts or cycles
in place, and blank answers clear optional fields. Setting the
icon chats a confirmation naming the material, renaming the name
chats "Renamed to", and renaming the id chats its own "Id changed
to" line; an id change returns to the same Meta menu. Cancel on a
delete confirm returns to the editor; only confirming the delete
returns to the list.
The Author button wears the author's player head when the author is
a UUID (dashed or trimmed) or a known player name, and the default
head otherwise.
The preset Modifiers screen lists member modifiers first with a glow,
then the rest on a fresh row; the Modifiers button count stays live
after toggling.

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
delete after a confirm. Paper names clamp to 32 characters, and
re-saving a line unchanged chats a notice instead of rewriting
it. Add and edit dialogs open with plain-command guidance (normal
commands like `give @p cooked_beef 8` need no scripting) plus a
curated tag shortlist, then list every available tag with a short
note. Saving validates the line: unbalanced
brackets, empty commands, malformed random args, unknown root
commands, and unknown `give` items are refused with a chat error,
while unknown tags and skipped pick items only warn. Set
`advanced.misc.modifier-editor.validate-commands` to
false to skip the root and item checks. Each save chats which
ordinal line was set. See
[Creating Modifiers and Presets](configuration/modifiers/creating.md#creating-modifiers-and-presets).

## Sounds

Clicks and write feedback come from `sounds.yml` (see
[Sounds](configuration/sounds.md)). A click that runs an action
plays the compass click once; opening a dialog and committing a
value play `ui.neutral-sound`; validation failures and blocked
clicks play `ui.angry-sound` plus a chat error. Fast clicks never
double-fire.
