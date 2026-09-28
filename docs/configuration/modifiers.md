# Modifiers

**Modifiers** are named command bundles you define in `modifiers.yml`
under `modifiers`. They are disabled by default. A modifier can run
commands when a match starts, on a recurring interval during the match, when
specific game events happen, and when the match ends, either from the console
or once for each participating player.

All command examples are the default config settings. Refer to the latest
version of `modifiers.yml` in the [GitHub repository](https://github.com/jruk8/JManhunt/blob/main/src/main/resources/modifiers.yml).

Settings for modifiers are categorized under `modifiers`:

```yaml
modifiers:
  everyone-gets-beef:
    enabled: false
```

Writing commands by hand is tedious. Use
[mcstacker.net](https://mcstacker.net/) to generate up-to-date commands,
then paste them into your modifier.

# Enabling a Modifier

Under `modifiers.<name>`, the `enabled` flag decides whether the
bundle runs at all. Toggle a bundle in-game with:

```text
/manhunt config modifiers everyone-gets-beef enabled true
```

You can also flip `enabled` in `modifiers.yml` directly, then run
`/manhunt reload`. When creating a modifier, copy the structure of an existing
one. Currently only manual YAML file editing is supported for creation.

Mid-match toggles apply live, but each modifier fires its start
commands at most once and runs its cleanup commands at most once per
match, so repeated toggling never duplicates rewards. Repeat toggles
are skipped with a console warning.

# Behaviors

Under `modifiers.<name>.behavior`, a modifier holds one behavior per
index (`0`, `1`, ...). Each behavior has its own triggers, options,
and command lists, and every behavior of an enabled modifier fires on
its own triggers. Indexes need not be contiguous; a modifier with no
behaviors does nothing:

```yaml
modifiers:
  everyone-gets-beef:
    enabled: false
    behavior:
      0:
        runs-on:
          - ON_START
        commands:
          player:
            - "give <p> minecraft:cooked_beef 8"
      1:
        runs-on:
          - INTERVAL
        options:
          interval-settings:
            interval: 60
        commands:
          player:
            - "give <p> minecraft:cooked_beef 1"
```

All trigger, option, and command paths below live under
`modifiers.<name>.behavior.<index>`.

# Command Lists

Under `commands`, you configure which commands run
and for whom. The bundled `everyone-gets-beef` example gives every
participating player eight steaks when the match starts:

```yaml
modifiers:
  everyone-gets-beef:
    enabled: false
    behavior:
      0:
        commands:
          player:
            # Runs for every participating player (only hunters and speedrunners, not including NONE)
            - "give <p> minecraft:cooked_beef 8"
```

The available command lists are:

- `commands.player`: runs for every participating player.
- `commands.hunter`: runs only for hunters.
- `commands.speedrunner`: runs only for speedrunners.
- `commands.console`: runs once from the console.
- `commands.console-cleanup`: runs from the console when the match ends.
- `commands.player-cleanup`: runs for every participating player when the
  match ends.

`<p>` is replaced with the participating player's name. Commands may start
with `/`.

The role-specific lists (`hunter`/`speedrunner`) only run when the executing
player actually has that role. For example, if a hunter enters the Nether and
only a `speedrunner` block is configured, that block does not run. Console
commands run in parallel regardless of any player's role.

# Blacklisted Commands

Some commands are too dangerous for modifiers to dispatch. The
`advanced.misc.interop.blacklisted-modifier-commands` list in `config.yml`
names command roots that never run, no matter which list holds them
(`console`, `player`, `hunter`, `speedrunner`, or either cleanup list).

Matching uses the first token of the resolved line with slashes and any
`namespace:` prefix stripped, case-insensitively, so `stop`, `/Stop`, and
`minecraft:stop` all match a `stop` entry. When a line hits the blacklist,
the plugin logs an error naming the command and its source line, then skips
the rest of that command list. The match itself keeps running.

Defaults: `op`, `deop`, `stop`, `restart`, `reload`, `luckperms`, `lp`,
`permissions`, `ban`, `kick`, `whitelist`, `execute`. Empty the list to
disable the blacklist entirely.

# Run Timing

Under `runs-on`, you configure when the commands
(other than cleanup) run. It is a list of any of:

| Value | Trigger |
| --- | --- |
| `ON_START` | Once when the match starts (runs for all participants) |
| `INTERVAL` | On a fixed interval that starts counting when the game begins |
| `ON_MOB_KILLED` | When a participating player kills a mob or other non-player entity (never players) |
| `ON_PLAYER_KILLS` | When a participating player kills another player |
| `ON_HUNTER_KILLS` | When a hunter kills a player |
| `ON_SPEEDRUNNER_KILLS` | When a speedrunner kills a player |
| `ON_NETHER_ENTER` | When a participating player enters the Nether (once per player per match) |
| `ON_END_ENTER` | When a participating player enters the End (once per player per match) |
| `ON_FIRST_NETHER_ENTER` | When the first participating player enters the Nether (once per match) |
| `ON_FIRST_END_ENTER` | When the first participating player enters the End (once per match) |
| `ON_EVERY_ADVANCEMENT` | When a participating player earns any advancement (recipe book unlocks excluded) |
| `ON_RESPAWN` | When a player respawns (only the executing player) |
| `ON_SPEEDRUNNER_RESPAWN` | When a speedrunner respawns (only the executing player) |
| `ON_HUNTER_RESPAWN` | When a hunter respawns (only the executing player) |

If `runs-on` is omitted, the modifier defaults to `ON_START`.
To count every kill, enable both `ON_MOB_KILLED` and `ON_PLAYER_KILLS`.

Except for `ON_START`, every event trigger runs the `player`, `hunter`, and
`speedrunner` commands only for the specific player involved in the event.
`ON_START` and `INTERVAL` run for all participating players instead.

## Start Timing

Under `on-start`, an `ON_START` modifier can wait
out the pre-start window before running:

```yaml
modifiers:
  hunter-post-start-speed:
    behavior:
      0:
        on-start:
          # BEFORE runs at /manhunt start; AFTER waits until the speedrunner
          # first hits a hunter (or the match force-starts). Defaults to BEFORE.
          pre-start-order: AFTER
```

This only applies when `runs-on` contains `ON_START` or is omitted (which
defaults to `ON_START`). When `start-on-speedrunner-damage` is disabled
there is no pre-start window, so both settings run at match start.

## Success Chance

Under `options.success-chance`, you can make the modifier
run only sometimes:

```yaml
modifiers:
  gear-dice:
    behavior:
      0:
        options:
          success-chance:
            # Chance to run, from 0.0 (never) to 1.0 (always). This is a fraction,
            # not a percent: use 0.5 for 50%. Defaults to 1.0.
            chance: 0.5
            # PER_INVOKE rolls once for everyone; PER_EXECUTOR rolls the console
            # and each player separately. Defaults to PER_INVOKE.
            behavior: PER_EXECUTOR
```

Without this section the modifier always runs. The roll happens on every
trigger, including each interval firing. Cleanup commands always run and are
never rolled.

## Command Execution

Under `options.execution`, you can run a random line
from a command list instead of every line:

```yaml
modifiers:
  gear-dice:
    behavior:
      0:
        options:
          execution:
            # IN_ORDER runs every line. PICK_RANDOM runs a random few instead.
            # Defaults to IN_ORDER.
            selection: PICK_RANDOM
            pick-random:
              # How many lines to pick. Minimum 1. Defaults to 1.
              count: 1
              # PER_INVOKE picks once for everyone; PER_EXECUTOR picks separately
              # for the console and each player. Defaults to PER_INVOKE.
              behavior: PER_EXECUTOR
```

This applies to each command list on its own. If you ask for more lines than
the list has, the whole list runs. Cleanup lists always run every line so
changes are reliably undone.

## Interval Settings

Under `options.interval-settings`, you configure how
often an `INTERVAL` modifier repeats. It only applies when `runs-on`
contains `INTERVAL`:

```yaml
modifiers:
  random-mob-spawner:
    enabled: false
    behavior:
      0:
        runs-on:
          - INTERVAL
        options:
          interval-settings:
            # Interval duration in seconds.
            interval: 60
            # Random spread in seconds. 60 and 15 means every 45 to 75 seconds.
            # Cannot go above interval. Defaults to 0.
            deviation: 15
            # PER_INVOKE shares one timer; PER_EXECUTOR gives every player and the
            # console their own timer. Defaults to PER_INVOKE.
            behavior: PER_INVOKE
```

Interval modifiers start counting when the game actually begins (when a
speedrunner hits a hunter, or when the match force-starts), not when
`/manhunt start` is run. 
With `PER_EXECUTOR` deviation, each player has their own timer, so players
who join mid-match get timed in as well.

The `interval` value supports decimals and is rounded to the nearest tick (1
tick = 0.05 seconds). Values between `0` and `0.05` execute every tick. Set to
`0` or `0.05` for every-tick execution. Negative values disable the modifier.

Each firing skips players who cannot play right now: match watchers,
dead players waiting on a respawn, and players held in spectator mode
(respawn waits and headstart holds). The console list still fires.

## Command Delay

Under `options.delay`, you can delay the modifier's
commands by a number of ticks after they trigger:

```yaml
modifiers:
  everyone-gets-beef:
    behavior:
      0:
        options:
          delay: 5
```

The delay applies to start, interval, and event triggers, but never to
cleanup commands. Player positions and roles resolve when the delayed commands
fire, not when they trigger. If the match ends before the delay elapses, the
commands are dropped.

## Placeholders in Commands

Modifier commands run through JMHScript (JMHS), the tag engine: tags
evaluate to text, then the full line runs as one command.

Every tag looks like `<name>` or `<name:arg1,arg2>`, with lowercase
names and comma-separated args; quote an arg when it holds commas,
and nest tags freely (they evaluate inside out).

Commands can use these tags:

| Tag | Replaced with |
| --- | --- |
| `<p>` | The participating player's name. Use this in player and role commands. |
| `<random-mob>` | A random spawnable living entity type in lowercase (e.g. `zombie`, `creeper`). Under pick-random `PER_INVOKE` one mob is rolled per activation for everyone; under `PER_EXECUTOR` every executor rolls their own. |
| `<random-item>` | A random item material in lowercase (e.g. `diamond_sword`, `bread`). Same scope rule as `<random-mob>`. |
| `<all-players>` | Every participating player in this match, as a name list like `[Alice, Bob]`. `[]` when nobody matches. |
| `<all-players:HUNTER>` | Same, but only hunters. `SPEEDRUNNER` works too. |
| `<random-player>` | One random participating player in this match. |
| `<random-num:4,12>` | A random whole number between 4 and 12. Order does not matter: `<random-num:12,4>` works the same. |
| `<random-pick:coal, "dirt", 'sand'>` | One random item from the list. Items can be bare, `"double-quoted"`, or `'single-quoted'`, and may hold spaces. |

Tags evaluate from the inside out, so they nest. The bundled
`random-start-resources` modifier uses this to hand out a random ore stash:

```yaml
- "give <p> <random-pick:coal <random-num:4,12>, iron_ingot <random-num:3,9>, gold_ingot <random-num:3,9>, diamond <random-num:1,3>>"
```

Each `<random-num>` rolls first, then `<random-pick>` chooses one
`item amount` pair, giving coal (4-12), iron (3-9), gold (3-9), or
diamonds (1-3).

If a `<random-pick>` item is malformed (mixed quotes, two quoted strings in
one item), it is skipped with a console warning and another item is tried.

Raw `@a`, `@r`, `@p`, and `@s` selectors work like vanilla and are
the recommended default for plain commands: they convert automatically
and stay scoped to the match, so commands never leak into other
matches. `@a` runs the command once per participant; a `team=`
argument on `@a[...]` survives as a role filter, while other vanilla
selector arguments are dropped. `@r` draws one random participant,
and `@p`/`@s` resolve to the executing player. Inside tag arguments
only JMHS tags (`<p>`, `<random-player>`) resolve; bare `@`
selectors there stay verbatim.

## Extended Tags

Besides the placeholders above, JMHS understands a few computing
tags. They nest inside each other and inside the basic tags, and the
creator editor validates them as you type:

| Tag | Meaning |
| --- | --- |
| `<if:"7 <= 5","yes","no">` | `yes` when the condition holds, else `no` (the else branch may be omitted). |
| `<min:8,3>` | The smaller number: `3`. |
| `<max:8,3>` | The larger number: `8`. |
| `<clamp:8,1,5>` | `8` clamped into `1..5`: `5`. |
| `<id>` | The name of the modifier (or trigger) running the commands. |
| `<gmessage:"hi">` | Sends `hi` to every participant; the tag itself leaves nothing behind. |
| `<pmessage:yo>` | Sends `yo` to the executing player only. Message tags take literal preset text (MiniMessage), never `messages.yml` keys. |
| `<gsound:block.stone.break>` | Plays the sound for every participant. |
| `<psound:block.stone.break,0.5,2>` | Plays the sound for the executing player, with pitch `0.5` and volume `2` (both default to `1`). |
| `<placeholder:jmanhunt_game_kills_this_session>` | Same placeholder as a tag, so math and conditions can use it. |
| `<loseplayer:Alex,fell>` | Eliminates Alex with the reason `fell`; the tag leaves nothing behind. |
| `<win:HUNTER,trapped>` | Ends the match for the hunters with the reason `trapped`; the tag leaves nothing behind. |
| `<args:0>` | The trigger's first event arg (see below); bare `<args>` reads index `0`. |
| `<len:[a,b]>` | List tools (`list.get`, `list.set`, `list.append`, and more; see Lists). |
| `<range:1,5>` | The list `[1, 2, 3, 4]`, Python style (see Lists). |
| `<active-players:HUNTER>` | Eligible hunters as a list, like `[Alex, Bo]`. |
| `<prole:Alex>` | `HUNTER` or `SPEEDRUNNER` for Alex, else `null`. |
| `<plocation:Alex>` | Alex's spot as `[x, y, z, pitch, yaw, dimension]`. |
| `<distance:[0,0,0],[3,4,0]>` | Blocks between two spots: `5`. |
| `<floor:2.7>` | `2`; `<ceil:2.3>` is `3`, `<round:2.5>` is `3`. |
| `<abs:-4>` | `4`; `<sign:-4>` is `-1` (`0` and `1` for the rest). |
| `<sqrt:9>` | `3`; `<cbrt:-8>` is `-2`. |
| `<root:16,4>` | `2`, the nth root of `x`. |
| `<for:[a,b],...>` | Repeats the body per item with the item behind `<i>` (see Loops). |
| `<while:1==1,...>` | Repeats the body while the condition holds (see Loops). |
| `<i>` | The innermost for-loop item, else `null`. |
| `<def:double,x+x,x>` | Defines the run-local function `double` (see Functions). |
| `<rflag:hunter,boost>` | The flag of the named role (see Flags). |
| `<rmessage:hunter,push!>` | Tells the named role only. |
| `<rsound:hunter,block.note_block.pling>` | Plays for the named role only. |

`<min>`, `<max>`, and `<clamp>` accept math in their arguments
(`<min:8+5,10>` is `10`) and yield `0` with a console warning when an
argument is not a number. `<floor>`, `<ceil>`, `<round>`, `<abs>`,
and `<sign>` also accept math (`<floor:7/2>` is `3`), round halves
up, and yield `null` with a warning when the argument is not a
number. `<sqrt:x>` and `<cbrt:x>` follow the same one-arg rules
(`<sqrt:-1>` is `null` with a warning; cube roots accept
negatives). `<root:x,n>` follows the two-arg rules: bad shapes, a
zero index, and even roots of negatives yield `0` with a warning,
while odd roots of negatives work (`<root:-8,3>` is `-2`).

### Event args

Each trigger carries its own event args, read with `<args:index>`.
A missing index yields `null`; a non-numeric index warns and yields
`null`:

| Trigger | Args |
| --- | --- |
| `ON_START` | None: every index is `null`. |
| `INTERVAL` | `0`: the seconds actually waited for this firing. |
| `ON_MOB_KILLED` | `0`: the killed mob's entity type name (e.g. `ZOMBIE`). |
| `ON_PLAYER_KILLS`, `ON_HUNTER_KILLS`, `ON_SPEEDRUNNER_KILLS` | `0`: the exact name of the killed player. |
| `ON_NETHER_ENTER`, `ON_END_ENTER`, `ON_FIRST_NETHER_ENTER`, `ON_FIRST_END_ENTER` | `0`: the origin world name, `1`: the destination world name. |
| `ON_EVERY_ADVANCEMENT` | `0`: the advancement's namespaced key (e.g. `minecraft:nether/root`). |
| `ON_RESPAWN`, `ON_SPEEDRUNNER_RESPAWN`, `ON_HUNTER_RESPAWN` | `0`: the death location as one list, `[x, y, z, pitch, yaw, dimension]`. |

### Match control

`<loseplayer:player,reason>` instantly eliminates a hunter or
speedrunner by name (even one already dead) and moves them to
spectator, announcing `lost: <reason>` with that role's death sound.
`<win:ROLE,reason>` ends the match for `HUNTER` or `SPEEDRUNNER`
(case does not matter). Both tags leave nothing behind. The reason
is everything after the first comma, so it may hold commas; an empty
or missing reason becomes `unknown reason`. The win reason is posted
on the win screen.

### Conditions

`<if>` compares with `==`, `!=`, `lt`, `le`, `gt`, `ge` and joins
parts with `and` / `or` (`and` binds tighter, case does not matter).
Leading `not` words flip one comparison and bind tightest of all,
so `not a == b and c == d` reads as `(not (a == b)) and (c == d)`,
and `not not x` cancels out:

```yaml
- 'say <if:"1 == 1 and 2 lt 3 or 4 == 5","y","n">'
- 'say <if:"not <pstat:<p>,health> le 6","healthy","heal!">'
```

Like `and` / `or`, `not` matches case-blindly and needs whitespace
after it, which keeps words like `notable` plain text. A `not` with
no comparison behind it warns and yields nothing.

Only the condition and the chosen branch evaluate: tags in the dead
branch never run. Quote comparisons holding literal `<` or `>`
(`"7 <= 5"`), or use the word operators, so the tag scanner does not
mistake them for tags.

Ordering compares decimals as well as whole numbers (`7.5 gt 7`
holds, `7.5 le 7.5` holds). Each side compares as a number when it
parses as math, otherwise as text. Word operators need a
non-letter-or-digit boundary on each side (spaces work, so does the
edge of the condition), which keeps them distinct from tag brackets:

```yaml
- '<if:"<pstat:<p>,health> le 7 and <gstat:duration>-<pflag:lastuse-<id>> ?? 999999 gt 300","give <p> golden_apple","exit">'
```

Angle brackets are never comparisons: `<`, `>`, `<=`, and `>=` in a
condition warn and yield nothing, naming the word operators to use
instead. Quote the condition when it holds commas or nested tags.

### Bare math

Any no-space token that fully parses as math evaluates: `give <p> egg
1+1` hands out 2 eggs. Parentheses, `**`, `//`, `%`, and `??` (null
coalescing) work; division by zero warns and yields 0. Quote a token
to protect it: `say "2026-09-26"` stays a date, while a bare
`2026-09-26` computes to 1991.

### Stats

`<pstat:player,key>` reads one player's numbers; `<gstat:key>` reads
match- or world-wide ones. Keys match case-blindly:

| Tag | Meaning |
| --- | --- |
| `<pstat:<p>,health>` | The player's health, normally 0-20. |
| `<pstat:Steve,hunger>` | The player's hunger, 0-20. |
| `<pstat:<p>,max-health>` | The player's effective max health, normally 20. |
| `<pstat:Steve,mobs-killed>` | Mobs the player killed this match. |
| `<pstat:Steve,achievements-gained>` | Non-recipe advancements earned this match. |
| `<gstat:duration>` | Whole seconds since the match began. |
| `<gstat:daytime>` | The main world clock in ticks. |

Unknown keys warn and yield nothing, listing the valid keys. Reading a
stat of an offline player warns and yields nothing. `duration` outside
a live match warns and yields 0.

### Flags

Flags are variables commands can share. Omit the value to read, pass
one to write; writes return nothing, and reads of unset flags yield
`null`, which pairs with `??` for defaults. Names match exactly and
may hold spaces inside quotes.

| Tag | Scope | Lifetime |
| --- | --- | --- |
| `<gflag:name,value>` | Whole match | Dies with the match. |
| `<pflag:name,value>` | Executing player (`-CONSOLE` for console lists) | Flushed when the player leaves, is eliminated, or disconnects for good; dies with the match. |
| `<rflag:role,name,value>` | Named role (`hunter` or `speedrunner`) | Dies with the match; player removal leaves role flags alone. |
| `<lflag:name,value>` | This run only | Set in an early line, read in a later line, discarded after. |

`<pflag:"cooldown",<gstat:"duration">>` stamps a cooldown;
`<pflag:"cooldown">` reads it back. `<rflag>` names its role up
front: `<rflag:hunter,boost>` and `<rflag:speedrunner,boost>` keep
independent values under the same name. The role is `hunter` or
`speedrunner` (any case); anything else warns, reads `null`, and
skips writes. Role flags need no executor, so console lists can use
them too.

Flags are modifier-agnostic on purpose: any modifier can read what
another wrote. For a strictly private flag, namespace the name with
`<id>`: `lastuse-<id>` can only collide with itself. Flags live in
memory: a reload or restart wipes them.

### Lists

Lists are bracketed values like `[a, b]` and live happily in flags:
`<gflag:nums,[1, 2, 3]>` stores one, `<gflag:nums>` reads it back.
Items split on top-level commas, so nested lists and quoted commas
survive. Positions start at `0`, and out-of-range reads yield `null`:

| Tag | Meaning |
| --- | --- |
| `<len:[a,b]>` | `2`. |
| `<list.get:[a,b],1>` | `b`. |
| `<list.set:[a,b],0,z>` | `[z, b]` (position `size` appends). |
| `<list.append:[a],b>` | `[a, b]`. |
| `<list.remove:[a,b],a>` | `[b]` (first match only). |
| `<list.contains:[a,b],b>` | `true`. |
| `<list.pop:[a,b]>` | `b`, the last item. |
| `<list.shuffle:[a,b]>` | The items in random order. |
| `<list.clear:[a,b]>` | `[]`. |
| `<range:5>` | `[0, 1, 2, 3, 4]`; `<range:1,5>` starts at `1`, and `<range:5,0,-1>` counts down. |

`<range>` follows Python: start inclusive, stop exclusive, step `1`
unless given. Ranges past 1000 items keep the first thousand with a
warning; a zero step or a non-number warns and yields `null`.

The mutating ops (`append`, `set`, `remove`, `clear`, `pop`,
`shuffle`) write back when their list is a verbatim flag reference:
`<list.append:<gflag:nums>,4>` grows the stored flag and leaves
nothing behind. Anything else (literals, other expressions) applies
purely and returns the new list.

### Roster and locations

`<active-players:HUNTER>` (or `SPEEDRUNNER`, case does not matter)
lists the players currently eligible for interval commands: online,
active, and neither respawning nor held. `<prole:Alex>` reads one
player's side, and `<plocation:Alex>` reads their spot as
`[x, y, z, pitch, yaw, dimension]`, the same shape as the respawn
event arg. Unknown or offline players yield `null` silently, as do
spectators for `<prole>`.

`<distance>` measures between two spots with only the `x`, `y`, `z`
entries, ignoring pitch, yaw, and dimension, so locations from
different worlds still compare by coordinates:
`<distance:<plocation:Alex>,[0, 64, 0]>` is the blocks from Alex to
spawn. Bad shapes warn and stop the line.

### Loops

`<for:list,body>` walks a list with each item behind `<i>`, and
`<while:condition,body>` repeats while its condition holds. Bodies
run for side effects (flag writes, messages, sounds) and any text
they produce is discarded; both loops leave nothing behind on
success:

```yaml
- '<gflag:out,[]>'
- '<for:<range:3>,<list.append:<gflag:out>,item<i>>>'
- 'say <gflag:out>'
```

This says `[item0, item1, item2]`. Nested loops work, with `<i>`
always reading the innermost item; outside any for loop `<i>` is a
silent `null`. The while condition re-resolves every iteration and
accepts a bare `true`/`false` word or a full condition (`<gflag:n>
lt 3`); anything else warns and the whole tag becomes `null`.

Two tripwires keep loops honest. A for loop over a flag reference
snapshots the flag and cancels to `null` with a warning when the
body changes it mid-loop (literals cannot change, so they never
cancel). And every line gets 1000 shared steps, split between loop
iterations and function calls: past that the tag becomes `null`,
the match is cancelled, the console logs the modifier, behavior,
list, and line, and the players are told to contact the
administrator. Quote literal `<` and `>` inside loop bodies so the
tag scanner does not mistake them for tags.

### Functions

`<def:name,body,params...>` defines a run-local function; the body
stores verbatim and never evaluates at definition time. Calling
`<name:args...>` substitutes each param with its arg, then evaluates
the body like any command line, so calls nest and recurse:

```yaml
- '<def:double,x+x,x>'
- 'say <double:21>'
- '<def:countdown,<if:n le 0,done,<countdown:n-1>>,n>'
- 'say <countdown:3>'
```

This says `42`, then `done`. Names are case-insensitive, use
letters, digits, and `_`, and must not collide with builtin tags
(redefining one warns and is ignored). All params are optional:
missing args bind `null` with no warning, while extra args warn and
are ignored. Params substitute as bare case-sensitive tokens
outside quotes, in one pass, so an arg holding a param name is
never re-substituted; quoted text is left alone. Like every tag,
substitution is textual: math bodies should parenthesize params
(`(B)**2` rather than `B**2`) so negative args bind as one value,
and bare math still needs no-space runs. Definitions live and die
with the run like `<lflag>`, and the creator editor warns on calls
it cannot see defined (unknown tags still resolve at runtime).

Recursion terminates through `<if>` base cases, since dead
branches never run. Every call costs one of the line's 1000 shared
steps, and the budget never refunds: leaving a loop cannot launder
steps back, so runaway recursion always ends at the limit above
instead of crashing the server.

### Placeholders

After tags and math resolve, raw `%...%` spans expand through
PlaceholderAPI when it is installed (any expansion works, including
`%jmanhunt_game_kills_this_session%`), or in-house for `%jmanhunt_*%`
spans when it is not. `<placeholder:key>` is the same lookup as a
tag, which matters inside math: `<clamp:<placeholder:x>-1, 0, 9>`
computes on the number, while a raw span would only expand after
math ran.

Placeholders need an executor player, so console lists skip the raw
pass silently; the tag form warns without an executor instead.
Unknown keys stay verbatim.

### Stopping a list

A lone `exit` line stops the command list: later lines never run. It is
checked after tags expand, so `<if:"1 == 2","exit","say hi">` skips
the rest only when the branch hits. `exit` with anything else on its
line is skipped with a warning.

### Null lines

A line that resolves to exactly `null` (lowercase, nothing else on
it) is never dispatched: the console logs a warning naming the
modifier, behavior, list, and line instead. Anything else holding
`null` runs as usual.

## Relative Coordinates

In player and role commands (`player`, `hunter`, `speedrunner`), tildes (`~`)
are automatically resolved to the participating player's position. For
example, `summon zombie ~ ~ ~` becomes `summon zombie 10.5 64 -20.2` if the
player is at `(10.5, 64.0, -20.2)`. Offsets like `~5` and `~-3` are supported.

All commands are dispatched as the console sender, so there are no permission
issues. The tilde resolution is handled by the plugin before dispatch.

# Targeting Sides with Selectors

Manhunt roles mirror to vanilla scoreboard teams (`HUNTER`,
`SPEEDRUNNER`, and `SPECTATOR`), so console commands can aim at a whole
side with the `team` selector argument:

```yaml
modifiers:
  hunter-fear:
    enabled: false
    behavior:
      0:
        runs-on:
          - INTERVAL
        options:
          interval-settings:
            interval: 30
        commands:
          console:
            - "effect give @a[team=HUNTER] minecraft:darkness 5 0"
```

`@a[team=HUNTER]` only covers hunters in the running match, so it
stays safe when several matches run at once. Membership follows roles
exactly (repaired on every role change and login), carries no colors or
friendly-fire rules, and `none`/`afk` players sit in no team. Pair with
`player`/`hunter`/`speedrunner` lists when you need per-player tags like
`<p>` or `~` coordinates instead. To print the names instead of running
once per player, use the `<all-players:HUNTER>` list tag in a message.

# Match-End Cleanup

The `console-cleanup` and `player-cleanup` lists run when the match ends,
which makes them the right place to undo whatever the modifier changed. The
bundled `perma-night` modifier, for example, re-enables daylight when the
match is over:

```yaml
modifiers:
  perma-night:
    enabled: false
    behavior:
      0:
        commands:
          console:
            # Ran by the console when the match starts.
            - "gamerule advance_time false"
            - "time set midnight"
          console-cleanup:
            # Ran by the console when the match ends.
            - "gamerule advance_time true"
```

Similarly, `speedrunner-health-advantage` resets every participant's max health
in `player-cleanup`, so temporary attribute changes never leak into the next
match or the lobby.

# Example

A minimal modifier that hands every participant a starter kit looks like this:

```yaml
modifiers:
  starter-kit:
    enabled: false
    behavior:
      0:
        commands:
          player:
            - "give <p> cooked_beef 8"
          hunter: []
          speedrunner: []
          console: []
          console-cleanup: []
          player-cleanup: []
```

The default `modifiers.yml` ships more examples to copy from: `full-iron-kit`,
`speedrunner-health-advantage`, `random-mob-spawner`, `random-item-giver`,
`random-start-resources`, `gear-dice`, `regen-on-kill`, `diamond-on-advancement`,
`fireres-on-nether-enter`, `hunter-start-debuffs` (slowness II plus
weakness I on every hunter at match start), `hunter-post-start-speed`
(speed for hunters once the game actually begins), `get-stronger-on-kill`,
and `speedrunner-gapple-on-low-hp`.

### Get Stronger On Kill

Runs on every player kill (executor: the killer) and every respawn
(executor: the respawner). The kill is recorded before the commands
run, so the session kill total they read is already fresh.

Each run works in half-hearts, with two tunables at the top of the
player list: `maxhp` (60: total cap, i.e. 30 hearts) and `step`
(4: gain per kill, i.e. 2 hearts):

1. `<pflag:basehp>` caches the player's baseline max health the
   first time the modifier runs for them.
2. `target` is baseline plus step times session kills, capped at
   `maxhp`, and `minecraft:max_health` base is set to it.
3. `instant_health` heals 2 hearts on the spot, even at the cap.
4. `saturation` refills only the hunger points actually missing.

The respawn trigger re-applies the same computed target from the
still-current session counter, so a killer who dies keeps their
earned hearts. Player cleanup restores each cached baseline, and
leaves players the modifier never ran for untouched.

### Gapple On Low HP

Runs every 3 seconds for speedrunners. The first line gives a golden
apple when health is at most 7 and more than 300 seconds passed since
the last give; otherwise it exits, which skips the second line while
cooling down. The second line stamps the give time into a per-player
flag. Runners who never got one are treated as due.

# Creating Modifiers and Presets

Build entries in the GUI or inline from chat; both write the same
`modifiers.yml` blocks that manual editing produces, and manual
editing keeps working as before. Everything created this way starts
disabled.

In the GUI, a create button sits at the top-right of the modifiers
and presets lists. It prompts for a display name (you become the
author) and opens the new entry in its editor. Right-clicking any
existing entry opens the same editor. The `create` command behaves
the same: without `--author`, the sender becomes the author, and
the console records `CONSOLE`.

The modifier editor covers every field: name, description, icon,
author, the enabled toggle, trigger toggles, pre-start order, all
timing and chance options, and every command list. The preset editor
covers name, description, icon, and membership toggles. Both editors
also export, rename the id, and delete after a confirm panel.
Buttons that prompt for optional values treat a blank answer as
clearing the field back to its default.

Commands validate when you save them: unbalanced angle brackets,
empty commands, malformed `<random-num:>` or `<random-pick:>`
arguments, unknown root commands, and unknown `give` items are
refused with an error, while unknown tags and skipped pick items
only warn. `<duration>` is compass-only and warns on modifiers.
Set `advanced.misc.interop.validate-modifier-editor-commands`
to false to skip the root and item checks; placeholder checks
always run. The command-line creator enforces the same rules; see
[Modifiers](../commands.md#modifiers) for its flags.

Presets keep their display data under `meta:`, exactly like
modifiers, with the member list beside it:

```yaml
presets:
  chaos-mode:
    meta:
      name: "Chaos Mode"
      description: "Random mobs, random items, gear dice"
      item: TNT
    modifiers:
      - random-mob-spawner
      - random-item-giver
```

Presets written in the old flat shape (name and friends next to
`modifiers:`) no longer load: re-indent the four display keys under
`meta:`. Old preset share strings need a fresh export too.

# Sharing Modifiers and Presets

Export any modifier or preset to a share string: a click-to-copy chat
line you can paste to friends or the community. Import takes one back
in; colliding names get numbered automatically. Strings that fail
their checksum or schema check are refused without touching anything.

```text
/manhunt modifiers export modifier gear-dice
/manhunt modifiers export preset chaos-mode
/manhunt modifiers import modifier JMH1D:...
```

The modifiers and presets menus carry an import loom in the
bottom-right corner that prompts for the string, and every editor has
an export button that copies its own share string.
