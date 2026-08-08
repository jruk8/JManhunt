# Placeholders

JManhunt provides built-in PlaceholderAPI identifiers. All placeholders must
be used with the prefix `%jmanhunt_<placeholder>%` (e.g.
`%jmanhunt_total_kills%`, `%jmanhunt_time_as_hunter%`).

Formatting, aliases, and enabled/disabled state are configured in `config.yml`
under the `placeholders` section. Values are resolved for the player passed by
PlaceholderAPI.

## Placeholder List

| Placeholder | Type | Description |
| --- | --- | --- |
| `%jmanhunt_time_as_speedrunner%` | LONG | Career time played as a speedrunner, in milliseconds. |
| `%jmanhunt_time_as_hunter%` | LONG | Career time played as a hunter, in milliseconds. |
| `%jmanhunt_formatted_time_as_speedrunner%` | TEXT | Career speedrunner time formatted as days, hours, minutes, seconds. |
| `%jmanhunt_formatted_time_as_hunter%` | TEXT | Career hunter time formatted as days, hours, minutes, seconds. |
| `%jmanhunt_total_kills%` | INTEGER | Career total kills. |
| `%jmanhunt_total_kills_as_hunter%` | INTEGER | Career kills while playing as a hunter. |
| `%jmanhunt_total_kills_as_speedrunner%` | INTEGER | Career kills while playing as a speedrunner. |
| `%jmanhunt_total_final_kills%` | INTEGER | Career final kills. |
| `%jmanhunt_total_damage_dealt%` | DECIMAL | Career damage dealt in hearts. |
| `%jmanhunt_total_wins_as_hunter%` | INTEGER | Career wins as a hunter. |
| `%jmanhunt_total_wins_as_speedrunner%` | INTEGER | Career wins as a speedrunner. |
| `%jmanhunt_total_game_sessions%` | INTEGER | Career total matches played as a participant. |
| `%jmanhunt_sessions_as_speedrunner%` | INTEGER | Career matches played as a speedrunner. |
| `%jmanhunt_sessions_as_hunter%` | INTEGER | Career matches played as a hunter. |
| `%jmanhunt_formatted_total_playtime%` | TEXT | Career total playtime (speedrunner + hunter) formatted as days, hours, minutes, seconds. |
| `%jmanhunt_total_kd_as_speedrunner%` | DECIMAL | Career K/D ratio as a speedrunner (kills per session). |
| `%jmanhunt_total_kd_as_hunter%` | DECIMAL | Career K/D ratio as a hunter (kills per session). |

## Configuration

Each placeholder can be individually enabled or disabled and formatted in
`config.yml`:

```yaml
placeholders:
  total_kills:
    enabled: true
    format: "{value}"
```

The `{value}` placeholder is replaced with the built-in value. Formatting uses
the selected `text-format` from `config.yml` (either `minimessage` or
`legacy`).