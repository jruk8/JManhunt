---
icon: material/view-dashboard
---

# Admin GUI

> For: everyone with access. Casuals browse mods here; intermediates change settings.

`/manhunt` is the gateway to the whole plugin. What it opens depends on
who you are: admins (permission `jmanhunt.gui`, default: op) get the
admin panel, everyone else sees their teams, and the console always gets
status. Everything in the panel can also be done with
[`/manhunt config`](commands.md#editing-settings-in-game), so console
admins lose nothing.

First time opening it, you get a setup panel instead: green sets
everything up in one click, red starts the step-by-step tour in chat.
It only shows once (see [Getting Started](../getting-started.md)).

## What Is Inside

- **Settings**: every tunable, split into General (everyday match,
  compass, player, and server settings) and Advanced (match controls,
  the world engine, lobbies, and power toggles).
- **Modifiers**: the modifier and preset browsers with live enabled
  counts. Browse, switch things on, import a string from Discord, or
  create your own (see [Modifiers](../configuration/modifiers.md)).
- **Lobby Overrides**: point your session at one lobby so every edit
  lands as an override for that lobby only. Opening the GUI always
  starts a fresh global session.
- **History**: lifetime server stats (matches, kills, wins, damage,
  playtime). Display-only.
- **Need Help?**: prints the support links in chat and closes the panel.

## Changing a Setting

Open a category and drill down to the setting you want. Each button
shows its description, current value, and default; buttons glow when
the value differs from the default.

- Toggles flip on click; choices cycle to the next option.
- Numbers and text open a small dialog (sliders for bounded numbers).
  Bad input is rejected with an error naming what is allowed, and the
  old value is kept.
- Right-click any setting to reset it to the default (with a confirm
  step).
- Lists show one entry per row with a way to append; click to edit,
  right-click to delete.

## Lobby Overrides

With an override session running, every menu reads that lobby's
effective values: overrides first, globals otherwise. Overridden rows
glow and carry an `Overrides for Lobby N` line, and every edit writes
an override with the same validation as globals.

- Shift-left-click (or right-click) a setting to remove its override.
- Shift-left-click a category to clear everything below it (with a
  confirm step).
- The same overrides work from chat or console through
  [`/manhunt override`](commands.md#per-lobby-overrides).

## Sounds

Clicks and feedback come from `sounds.yml` (see
[Sounds](../configuration/sounds.md)): a click per action, a neutral
sound for dialogs and saves, and an angry sound plus a chat error when
something is refused.
