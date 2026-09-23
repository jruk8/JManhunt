# Placeholders

JManhunt provides built-in PlaceholderAPI identifiers. All placeholders must
be used with the prefix `%jmanhunt_<placeholder>%` (e.g.
`%jmanhunt_total_kills%`, `%jmanhunt_game_phase_0%`).

Each entry is toggled and formatted in `placeholders.yml` (enabled plus
format per identifier). Player values resolve for the player passed by
PlaceholderAPI; lobby values take the lobby id as a suffix
(`%jmanhunt_game_phase_0%`). When a lobby runs sub-lobbies, the first
sub-lobby with a running game answers.

## Persistent Placeholders

Career and lobby totals, kept in the statistics database across restarts.

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
| `%jmanhunt_total_wins%` | INTEGER | Career total wins. |
| `%jmanhunt_total_wins_as_hunter%` | INTEGER | Career wins as a hunter. |
| `%jmanhunt_total_wins_as_speedrunner%` | INTEGER | Career wins as a speedrunner. |
| `%jmanhunt_total_game_sessions%` | INTEGER | Career total matches played as a participant. |
| `%jmanhunt_sessions_as_speedrunner%` | INTEGER | Career matches played as a speedrunner. |
| `%jmanhunt_sessions_as_hunter%` | INTEGER | Career matches played as a hunter. |
| `%jmanhunt_formatted_total_playtime%` | TEXT | Career total playtime (speedrunner + hunter) formatted as days, hours, minutes, seconds. |
| `%jmanhunt_total_kd_as_speedrunner%` | DECIMAL | Career K/D ratio as a speedrunner (kills per session). |
| `%jmanhunt_total_kd_as_hunter%` | DECIMAL | Career K/D ratio as a hunter (kills per session). |
| `%jmanhunt_current_win_streak%` | INTEGER | Career current win streak. |
| `%jmanhunt_best_win_streak%` | INTEGER | Career best win streak. |
| `%jmanhunt_lobby_lifetime_sessions_<lobbyid>%` | INTEGER | Total started sessions for the lobby, all time. |

## Runtime Placeholders

Live match numbers. They fall back when no game is running: counts to
`0`, missing times and session scores to `-1`, phase to `LOBBY`, and
role to `None`.

| Placeholder | Type | Description |
| --- | --- | --- |
| `%jmanhunt_game_duration_<lobbyid>%` | LONG | Running match duration in milliseconds, -1 when idle. |
| `%jmanhunt_game_speedrunners_remaining_<lobbyid>%` | INTEGER | Live speedrunners in the lobby's match. |
| `%jmanhunt_game_hunters_remaining_<lobbyid>%` | INTEGER | Live hunters in the lobby's match. |
| `%jmanhunt_game_players_remaining_<lobbyid>%` | INTEGER | Live participants in the lobby's match. |
| `%jmanhunt_game_spectators_<lobbyid>%` | INTEGER | Online spectators assigned to the lobby's match. |
| `%jmanhunt_lobby_current_sessions_<lobbyid>%` | INTEGER | Running sessions for the lobby right now. |
| `%jmanhunt_game_phase_<lobbyid>%` | TEXT | Lobby match phase: LOBBY, PRESTART, IN_PROGRESS, or ENDED (names configurable in `placeholders.yml`). |
| `%jmanhunt_game_time_remaining_<lobbyid>%` | LONG | Survive clock left in milliseconds, -1 when none runs. |
| `%jmanhunt_game_time_remaining_formatted_<lobbyid>%` | TEXT | Survive clock left, formatted; -1 when none runs. |
| `%jmanhunt_game_role%` | TEXT | Player's current role with its configured color. |
| `%jmanhunt_game_kills_this_session%` | INTEGER | Player's kills this match, -1 outside a match. |
| `%jmanhunt_game_deaths_this_session%` | INTEGER | Player's deaths this match, -1 outside a match. |

## Configuration

Each placeholder can be individually enabled or disabled and formatted in
`placeholders.yml`:

```yaml
placeholders:
  total_kills:
    enabled: true
    format: "{value}"
```

The `{value}` placeholder is replaced with the built-in value. Formatting uses
MiniMessage (classic `&` codes convert automatically).
