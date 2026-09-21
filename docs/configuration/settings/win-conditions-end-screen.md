# Win Conditions

Under `settings.win-conditions`, you can configure what constitutes a win in the
game. Each side wins as soon as any one of its conditions is satisfied, and
both sides always win by eliminating the other side outright. Make sure at
least one is enabled.

```yaml
settings:
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
        mob: "minecraft:ender_dragon"
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
```

## Speedrunner Conditions

`exit-end` is the classic manhunt win condition: speedrunners win by
entering the End and then returning to the Overworld. Enabled by default.

`survive-time` lets speedrunners win simply by staying alive for a set
duration, given by `time` in seconds.

`acquire-item` grants the win the moment a speedrunner obtains the item
named in `item`, given as a namespaced key (e.g.
`minecraft:netherite_ingot`).

`reach-advancement` grants the win when a speedrunner completes the
advancement named in `advancement`, given as a namespaced key (e.g.
`minecraft:story/enter_the_nether`).

`kill-mob` grants the win when a speedrunner kills the mob named in
`mob`, given as a namespaced key (e.g. `minecraft:ender_dragon`).

## Hunter Conditions

`survive-time`, `acquire-item`, `reach-advancement`, and `kill-mob`
mirror the speedrunner conditions for the other side: hunters win when
the clock runs out, when a hunter holds the item, when a hunter completes
the advancement, or when a hunter kills the mob. All default to off.

## Time Announcements

Whenever a time limit is running, the match hears it count down: 8h, 6h,
4h, 2h, 1h, 30m, 15m, 10m, 5m, 2m, 1m, 30s, 15s, 10s, then 5-4-3-2-1. A
mark matching the limit itself stays silent (it would fire the instant
the match starts). If both sides' clocks are set, the earlier expiry wins
(ties favor the speedrunners) and the console logs which one was picked.

Enable `settings.status.show-win-conditions` to print each side's rules in
`/manhunt status`, e.g. `Speedrunners win on: eliminate all hunters and
credits screen`. The elimination line hides while hunters have unlimited
lives, since it could never be achieved. The sentence fragments live in
`messages.yml` under `wincon:`, so every word of the rules is editable.

# End-Screen Statistics

Under `match.end-statistics`, you can configure which match statistics are broadcast
after a match ends, and in what order:

```yaml
match:
  end-statistics: [DAMAGE_DEALT, HUNTER_FINAL_KILLS, SPEEDRUNNER_KILLS, PROGRESSION]
```

Available values are `DAMAGE_DEALT`, `HUNTER_FINAL_KILLS`,
`SPEEDRUNNER_KILLS`, and `PROGRESSION`. For each listed category, the top
three players with a value above zero are shown. `PROGRESSION` ranks players by
how far they got through the vanilla advancement track, calculated at match
end.
