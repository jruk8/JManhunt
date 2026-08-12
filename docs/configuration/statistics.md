# Statistics and PlaceholderAPI

Career statistics are enabled by default and stored in `jmanhunt.db` using
SQLite. The same database also stores the persistent world-engine spiral cell
index. For statistics shared between servers, set `database.type` to
`postgresql` and configure `database.postgresql` in `config.yml`.

With PlaceholderAPI installed, JManhunt provides placeholders such as
`%jmanhunt_total_kills%` and `%jmanhunt_formatted_time_as_hunter%`. The
complete list and formatting options are documented in
[placeholders.md](placeholders.md).