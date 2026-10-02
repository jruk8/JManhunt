# Statistics

> For: intermediate admins keeping career stats, plus advanced multi-server setups.

Career statistics are on by default, stored in `statistics.db` via
SQLite. For statistics shared between servers, set `statistics.type`
to `postgresql` and point every server at the same database.

With PlaceholderAPI installed, JManhunt provides placeholders such as
`%jmanhunt_total_kills%`. The complete list lives in
[Placeholders](../advanced/placeholders.md).

## How Wins Are Counted

A win credits only participants on the winning side, and `total_wins`
is always the sum of the two role-specific counts. Match statistics
cover in-match hunters and speedrunners only: spectators never accrue,
and the match clock freezes when the match ends.

## Database

```yaml
statistics:
  enabled: true
  type: sqlite
  sqlite:
    file: statistics.db
  postgresql:
    host: localhost
    port: 5432
    database: jmanhunt
    username: jmanhunt
    password: change-me
    ssl: false
  pool-size: 4
```

When `enabled` is false, statistics stay in memory only and die on
restart. `pool-size` controls how many database connections stay open.

## Engine State

The world-engine cell index plus the crash cleanup roster live in
`engine.db`, always SQLite, needing no configuration. After a crash,
each listed player is wiped back to normal on their next join, then
removed from the roster. Clean exits delete the row immediately, so a
clean restart wipes nobody.

## Upgrading

Upgrading from `jmanhunt.db`: rename that file to `statistics.db` to
keep your stored statistics. Your existing `database` settings move to
`statistics` automatically on reload. The cell index starts fresh in a
new `engine.db` file.
