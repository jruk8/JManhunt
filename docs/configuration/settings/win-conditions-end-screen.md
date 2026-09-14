# Win Conditions

Under `settings.win-conditions`, you can configure what constitutes a win in the
game. Multiple conditions can be enabled at once. Make sure at least one is
enabled.

```yaml
settings:
  win-conditions:
    exitEnd:
      enabled: true
    surviveTime:
      enabled: false
      time: 3600.0
    acquireItem:
      enabled: false
      item: "minecraft:netherite_ingot"
    reachAdvancement:
      enabled: false
      advancement: "minecraft:story/enter_the_nether"
```

## Exit the End

`exitEnd` is the classic manhunt win condition: speedrunners win by entering
the End and then returning to the Overworld. Enabled by default.

## Survive Time

`surviveTime` lets speedrunners win simply by staying alive for a set
duration, given by `time` in seconds.

## Acquire Item

`acquireItem` grants the win the moment a speedrunner obtains the item named
in `item`, given as a namespaced key (e.g. `minecraft:netherite_ingot`).

## Reach Advancement

`reachAdvancement` grants the win when a speedrunner completes the
advancement named in `advancement`, given as a namespaced key (e.g.
`minecraft:story/enter_the_nether`).

# End-Screen Statistics

Under `end-statistics`, you can configure which match statistics are broadcast
after a match ends, and in what order:

```yaml
end-statistics: [DAMAGE_DEALT, HUNTER_FINAL_KILLS, SPEEDRUNNER_KILLS, PROGRESSION]
```

Available values are `DAMAGE_DEALT`, `HUNTER_FINAL_KILLS`,
`SPEEDRUNNER_KILLS`, and `PROGRESSION`. For each listed category, the top
three players with a value above zero are shown. `PROGRESSION` ranks players by
how far they got through the vanilla advancement track, calculated at match
end.
