---
icon: material/school
---

# Your First Modifier

> For: intermediate admins making their first tweak. Zero scripting.

A runner starter kit: every speedrunner gets food, a sword, and a
shield when the match starts. Plain `give` commands, no tags needed.

## In the GUI

1. Open `/manhunt` and pick **Modifiers**.
2. Hit the create button (top-right) and name it `Runner Starter Kit`.
   You become the author, and the new entry opens in its editor.
3. Open **Behavior** and make sure `ON_START` is ticked under Runs On:
   the kit hands out once when the match starts.
4. Open **Command Lists**, then the `speedrunner` list, and append
   these three lines:
   - `give <p> cooked_beef 8`
   - `give <p> stone_sword 1`
   - `give <p> shield 1`
5. Back out to the modifiers list and flip the entry's toggle on.
6. Start a match: every speedrunner spawns with the kit.

`<p>` becomes each runner's name when the lines run. Lines in the
`speedrunner` list only run for speedrunners; hunters get nothing.

## As a File

The GUI wrote `mods/modifiers/runner-starter-kit.yml`. The same file
by hand looks like this:

```yaml
enabled: true
meta:
  name: "Runner Starter Kit"
  author: You
behavior:
  0:
    runs-on:
      - ON_START
    commands:
      speedrunner:
        - "give <p> cooked_beef 8"
        - "give <p> stone_sword 1"
        - "give <p> shield 1"
```

After editing files by hand, run `/manhunt reload` (or switch the
modifier with `/manhunt modifiers setmod runner-starter-kit true`).

## If It Does Not Work

- Is the toggle on? New entries start disabled.
- Are you testing as a speedrunner? The `hunter` list would skip you.
- Did the save complain? The editor refuses unknown items and broken
  lines with a chat error naming the problem.

## Next Steps

- [Creating and Sharing](creating.md): validation rules, presets, and
  share strings.
- [Behaviors](behaviors.md): intervals, kill triggers, timing, chance.
- [Tags: Basics](tags-basics.md): straightforward tags like
  `<random-num:4,12>` when plain commands run out.
