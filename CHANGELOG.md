## [3.7.0] - 2026-08-15

### 🚀 Features

- Convert schems to use worldedit as soft dep

### ⚙️ Miscellaneous Tasks

- *(changelog)* Update for main [skip ci]
## [3.6.2] - 2026-08-15

### 🐛 Bug Fixes

- *(deps)* Bump gradle-wrapper in the gradle-dependencies group (#51)
- Publish
## [3.6.0] - 2026-08-13

### 🚀 Features

- Compass start configs, float/string/enum modifiers modification in-game
## [3.5.3] - 2026-08-12

### 🐛 Bug Fixes

- Respawn scheduler not reset, on-fetch-new-cell timing

### ⚙️ Miscellaneous Tasks

- *(changelog)* Update for main [skip ci]
## [3.5.2] - 2026-08-12

### 🐛 Bug Fixes

- Gamerule bug and reload warn messages

### ⚙️ Miscellaneous Tasks

- *(changelog)* Update for v3.5.1 [skip ci]
## [3.5.1] - 2026-08-12

### 📚 Documentation

- Update README.md
- Split configuration.md into separate files for better organization
- Clean up general.md and remove duplicate content
- Update mkdocs files

### ⚙️ Miscellaneous Tasks

- *(bump)* Plugin conventions 1.3.4
## [3.5.0] - 2026-08-11

### 🚀 Features

- Add full-iron-kit modifier, add 3 new runs-on sequences, fix bug with cleanup on autostart running out on cancel option

### 📚 Documentation

- Add gallery images and update banner
## [3.4.0] - 2026-08-11

### 🚀 Features

- Tons of new lucky block outcomes

### 🐛 Bug Fixes

- Added random item giver to custom modifiers, fixed existing syntax
- General gamecycle fixes
## [3.3.0] - 2026-08-10

### 🐛 Bug Fixes

- Fix start delay setting hunters to spectator too early when `start-on-speedrunner-damage` is also enabled. Hunters now only enter spectator when the delay countdown actually begins, after the speedrunner deals first damage.
- Fix lucky block definition being broadcast even when the challenge is disabled. `currentLuckyBlock` is now reset at the start of each game.
- Fix custom modifier command arrays not resetting properly on reload. `YamlFileUpdater` no longer resurrects removed default command role keys under `custom-modifiers.*.commands`, and interval modifier tasks are now cancelled before settings reload.

### 🚀 Features

- Additions and bug fixes

### ⚙️ Miscellaneous Tasks

- *(changelog)* Update for v3.2.1 [skip ci]
## [3.2.0] - 2026-08-09

### 🚀 Features

- Numerous fixes, qs subcommand alias
- Add datapack entries for desert pyramids, mineshafts, and mesa mineshafts (#39)

### ⚙️ Miscellaneous Tasks

- *(changelog)* Update for v3.1.2 [skip ci]
## [3.1.1] - 2026-08-08

### 🐛 Bug Fixes

- Create CHANGELOG.md

