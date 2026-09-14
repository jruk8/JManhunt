# Statistics & PlaceholderAPI

Career statistics are enabled by default and stored in `jmanhunt.db` using
SQLite. The same database also stores the persistent world-engine spiral cell
index. For statistics shared between servers, set `database.type` to
`postgresql` and configure `database.postgresql` in `config.yml`.

With PlaceholderAPI installed, JManhunt provides placeholders such as
`%jmanhunt_total_kills%` and `%jmanhunt_formatted_time_as_hunter%`. The
complete list and formatting options are documented in
[placeholders.md](../placeholders.md).

## How Wins Are Counted

A win is only credited to participants on the winning side: when the
speedrunners win, only speedrunners gain `total_wins` and
`total_wins_as_speedrunner`; when the hunters win, only hunters gain
`total_wins` and `total_wins_as_hunter`. `total_wins` is always the sum of the
two role-specific win counts.

## Database

Career statistics are persisted under the `database` section in `config.yml`.
When `enabled` is false, statistics are kept in memory only and are lost on
restart:

```yaml
database:
  enabled: true
  type: sqlite
  sqlite:
    file: jmanhunt.db
  postgresql:
    host: localhost
    port: 5432
    database: jmanhunt
    username: jmanhunt
    password: change-me
    ssl: false
  pool-size: 4
```

SQLite is local and requires no setup. Use `postgresql` when several JManhunt
servers should share the same statistics, and point every server at the same
database. `pool-size` controls how many database connections the pool keeps
open.