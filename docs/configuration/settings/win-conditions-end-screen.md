# Win Conditions

> For: intermediate admins picking how matches are won.

Each side wins as soon as any one of its conditions is satisfied, and
both sides always win by eliminating the other side outright. Keep at
least one enabled.

```yaml
settings:
  match:
    win-conditions:
      speedrunner:
        exit-end:
          enabled: true
        survive-time:
          enabled: false
          time: 3600.0
        acquire-item:
          enabled: false
          item: "minecraft:netherite_ingot"
        reach-advancement:
          enabled: false
          advancement: "minecraft:story/enter_the_nether"
        kill-mob:
          enabled: false
          mob: "minecraft:wither"
      hunter:
        survive-time:
          enabled: false
          time: 3600.0
        acquire-item:
          enabled: false
          item: "minecraft:netherite_ingot"
        reach-advancement:
          enabled: false
          advancement: "minecraft:story/enter_the_nether"
        kill-mob:
          enabled: false
          mob: "minecraft:ender_dragon"
      cancel:
        survived-time:
          enabled: true
          time: 28800.0
```

## Speedrunner Conditions

| Condition | Win when |
| --- | --- |
| `exit-end` (on) | A speedrunner enters the End and returns to the Overworld |
| `survive-time` | The clock in `time` (seconds) runs out |
| `acquire-item` | A speedrunner obtains the namespaced `item` |
| `reach-advancement` | A speedrunner completes the namespaced `advancement` |
| `kill-mob` | A speedrunner kills the namespaced `mob` |

## Hunter Conditions

`survive-time`, `acquire-item`, `reach-advancement`, and `kill-mob`
mirror the speedrunner conditions for the other side. All default to
off.

## Cancel Conditions

`cancel.survived-time` ends the match with no winner once its `time`
expires: a safety valve for games that never end, on by default at 8
hours (28800 seconds).

## Time Announcements

Whenever a time limit runs, the match hears it count down: 8h, 6h, 4h,
2h, 1h, 30m, 15m, 10m, 5m, 2m, 1m, 30s, 15s, 10s, then 5-4-3-2-1. A
mark matching the limit itself stays silent. The lowest enabled time
across the three clocks wins; full ties favor the speedrunners.

Enable `settings.server.status.show-win-conditions` to print each
side's rules in `/manhunt status`. The elimination line hides while
hunters have unlimited lives. The sentence fragments live in
`messages.yml` under `wincon:`.

## Win Announcement

When a match ends with a winner, the announcement reads: a blank line,
the plugin prefix, a separator, the role-colored `Hunters Win!` or
`Speedrunners Win!` title, a reason line (e.g. `Exited the End`), and
a closing separator. The reason format lives in `messages.yml` under
`game.win-reason`. The fullscreen title wears the winner's role color
too.

## End-Screen Statistics

Which match statistics broadcast after a match ends, and in what
order:

```yaml
advanced:
  advanced-match-controls:
    end-statistics: [DAMAGE_DEALT, HUNTER_FINAL_KILLS, SPEEDRUNNER_KILLS, PROGRESSION]
```

Available values are `DAMAGE_DEALT`, `HUNTER_FINAL_KILLS`,
`SPEEDRUNNER_KILLS`, and `PROGRESSION`. For each listed category, the
top three players with a value above zero show. `PROGRESSION` ranks
players by vanilla advancement progress, calculated at match end.
