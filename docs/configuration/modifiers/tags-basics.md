---
icon: material/tag
---

# Tags: Basics

> For: intermediate authors. Straightforward tags up front; the full cheatsheet lives in [Tags: Advanced](tags-advanced.md).

Straight to the most useful ones: `<random-num:4,12>` rolls a whole
number, `<random-pick:a,b>` picks one item, and interval triggers (see
[Behaviors](behaviors.md)) run lines on a timer. Everything below builds
from there.

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

### Escaping Special Characters

A backslash before any char makes it literal text: `\<yellow\>`
survives tag parsing and renders MiniMessage yellow, `say a\,b`
keeps one arg with a comma, and `\\` collapses to `\`. Escaped
text never evaluates as tags, math, conditions, lists, selectors,
or tildes.

```yaml
- '<pmessage:<p>,\<yellow\>This is yellow text!>'
- '<pmessage:<p>,a\,b>'
```

Write escaped lines in single-quoted YAML: double-quoted YAML
eats backslashes before the engine sees them. Escapes hide
chars from JMHScript parsing only: PlaceholderAPI expansion
still sees the restored literal text afterwards.

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
| `<pmessage:Alex,yo>` | Sends `yo` to Alex only. Message tags take literal preset text (MiniMessage), never `messages.yml` keys. |
| `<gsound:block.stone.break>` | Plays the sound for every participant. |
| `<psound:Alex,block.stone.break,0.5,2>` | Plays the sound for Alex, with pitch `0.5` and volume `2` (both default to `1`). |
| `<placeholder:jmanhunt_game_kills_this_session>` | Same placeholder as a tag, so math and conditions can use it. |
| `<loseplayer:Alex,fell>` | Eliminates Alex with the reason `fell`; the tag leaves nothing behind. |
| `<win:HUNTER,trapped>` | Ends the match for the hunters with the reason `trapped`; the tag leaves nothing behind. |
| `<args:0>` | The trigger's first event arg (see below); bare `<args>` reads index `0`. |
| `<len:[a,b]>` | List tools (`list.get`, `list.set`, `list.append`, and more; see Lists). |
| `<range:1,5>` | The list `[1, 2, 3, 4]`, Python style (see Lists). |
| `<active-players:HUNTER>` | Eligible hunters as a list, like `[Alex, Bo]` (`ALL` lists both sides). |
| `<prole:Alex>` | `HUNTER` or `SPEEDRUNNER` for Alex, else `null`. |
| `<phasitem:Alex,golden_apple,2>` | `true` when Alex holds at least 2 golden apples, else `false` (the count is 1 when omitted). |
| `<pheld:Alex>` | Alex's main-hand material, else `null`. |
| `<plocation:Alex>` | Alex's spot as `[x, y, z, world, pitch, yaw]`. |
| `<overlap-players:[0,64,0],HUNTER,10,5>` | Up to 5 hunters within 10 blocks of the origin, nearest first. |
| `<nearby-players:Alex,ALL,10,5>` | Up to 5 players within 10 blocks of Alex, sender excluded. |
| `<pworld:Alex>` | `nether`, `end`, or the raw world name (`<world:Alex>` is the same tag). |
| `<px:Alex>` | Alex's x; `<py>`, `<pz>`, `<pyaw>`, `<ppitch>` read the rest. |
| `<distance:[0,0,0],[3,4,0]>` | Blocks between two spots: `5`. Full location lists in different dimensions yield `null` silently. |
| `<floor:2.7>` | `2`; `<ceil:2.3>` is `3`, `<round:2.5>` is `3`. |
| `<abs:-4>` | `4`; `<sign:-4>` is `-1` (`0` and `1` for the rest). |
| `<sqrt:9>` | `3`; `<cbrt:-8>` is `-2`. |
| `<root:16,4>` | `2`, the nth root of `x`. |
| `<for:[a,b],...>` | Repeats the body per item with the item behind `<i>` (see Loops). |
| `<while:1==1,...>` | Repeats the body while the condition holds (see Loops). |
| `<i>` | The innermost for-loop item, else `null`. |
| `<def:double,x+x,x>` | Defines the run-local function `double` (see Functions). |
| `<run:say hi>` | Runs `say hi` from the console like a command list entry; the tag leaves nothing behind. |
| `<format:"{0} found {1}",[Alex,gold]>` | `Alex found gold`. Plain `{n}` substitution; missing indexes, `{x}`, and stray braces stay verbatim silently. No conversion specifiers. |
| `<format>` mismatch rules | Missing indexes, `{x}`, and stray braces stay verbatim with no warning; `{0:D}`-style specifiers stay literal (none exist); wrong arity or bad quotes warn plus `null`. |
| `<rflag:hunter,boost>` | The flag of the named role, or `ALL` for both (see Flags). |
| `<rmessage:hunter,push!>` | Tells the named role only (`ALL` tells both). |
| `<rsound:hunter,block.note_block.pling>` | Plays for the named role only (`ALL` plays for both). |

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

## Stats

`<pstat:player,key>` reads one player's numbers; `<gstat:key>` reads
match- or world-wide ones. Keys match case-blindly:

| Tag | Meaning |
| --- | --- |
| `<pstat:<p>,health>` | The player's health, normally 0-20. |
| `<pstat:Steve,hunger>` | The player's hunger, 0-20. |
| `<pstat:<p>,max-health>` | The player's effective max health, normally 20. |
| `<pstat:Steve,mobs-killed>` | Mobs the player killed this match. |
| `<pstat:Steve,achievements-gained>` | Non-recipe advancements earned this match. |
| `<pstat:Steve,exp-level>` | Vanilla experience level. |
| `<gstat:duration>` | Whole seconds since the match began. |
| `<gstat:daytime>` | The main world clock in ticks. |

Unknown keys warn and yield nothing, listing the valid keys. Reading a
stat of an offline player warns and yields nothing. `duration` outside
a live match warns and yields 0.

## Roster and locations

`<active-players:HUNTER>` (or `SPEEDRUNNER`, case does not matter)
lists the players currently eligible for interval commands: online,
active, and neither respawning nor held. `ALL` lists both sides as
one list. `<prole:Alex>` reads one player's side, and
`<plocation:Alex>` reads their spot as
`[x, y, z, world, pitch, yaw]`, the same shape as the respawn
event arg. Unknown or offline players yield `null` silently, as do
spectators for `<prole>`.

`<overlap-players:origin,role,radius,max>` and
`<nearby-players:player,role,radius,max>` list names within
`radius` blocks, nearest first with name order breaking ties, capped
at `max` names. The role filters like `<active-players>` and
accepts `ALL`. A bare `[x, y, z]` origin searches every world; a
full `[x, y, z, world, pitch, yaw]` origin searches that world only.
`<nearby-players>` centers on the named player (whose own world
scopes the search) and drops the executing player from the list, so
console lists still see everyone. Bad shapes warn and yield `null`;
an unknown center player yields `null` silently, and an empty search
yields `[]`.

`<pworld:Alex>` reports the world alias: `nether` for the nether,
`end` for the end, else the raw world name. `<world:Alex>` is the
same tag. `<px:Alex>`, `<py:Alex>`, `<pz:Alex>`,
`<pyaw:Alex>`, and `<ppitch:Alex>` read single numbers from the
live spot. Unknown or offline players yield `null` silently.

`<distance>` measures between two spots with only the `x`, `y`, `z`
entries, ignoring pitch and yaw. Two full primitives in different
dimensions yield `null` silently, while short lists carry no
dimension and always compare by coordinates:
`<distance:<plocation:Alex>,[0, 64, 0]>` is the blocks from Alex to
spawn. Null, blank, and unparseable sides (an offline player's spot,
for example) also yield `null` silently. Genuinely malformed non-null
sides warn and stop the line. Nested tags need their own brackets:
`<distance:<plocation:Alex>,[0, 64, 0]>` resolves, while bare
`plocation:Alex` inside the args stays plain text.

## Bare math

Any no-space token that fully parses as math evaluates: `give <p> egg
1+1` hands out 2 eggs. Parentheses, `**`, `//`, and `%` work;
division by zero warns and yields 0. Quote a token to protect it:
`say "2026-09-26"` stays a date, while a bare `2026-09-26` computes
to 1991. For fallback values use `<default:value,fallback>` (see
Flags in the advanced tags).

## Placeholders

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

## Stopping a list

A lone `exit` line stops the command list: later lines never run. It is
checked after tags expand, so `<if:"1 == 2","exit","say hi">` skips
the rest only when the branch hits. `exit` with anything else on its
line is skipped with a warning.

## Null lines

A line that resolves to exactly `null` (lowercase, nothing else on
it) is never dispatched: the console logs a warning naming the
modifier, behavior, list, and line instead. Anything else holding
`null` runs as usual.

## Mod Files and Load Order

Modifiers load fully before presets. Inside each tree, every
directory loads its subdirectories first (sorted naturally by name,
each processed with this same rule), then its own `.yml` files
sorted naturally by filename. Root-level files therefore load after
everything nested. Sorting is Explorer style: case-insensitive,
with digit runs compared numerically (`mod2` before `mod10`).

Two files of the same kind with the same id in different folders
are a duplicate: the first in load order wins and the rest are
skipped, with a console warning naming both paths. A modifier and
a preset may share an id; they are separate namespaces.

Non-`.yml` files are reported as unknown files on reload (temp
files and dotfiles are ignored silently). A `.yml` file that fails
to parse or validate is logged as `<path>: <error>`, skipped, and
reported; fixing it later reloads quietly. A preset that names an
unknown modifier id warns and skips that member in memory only: the
file keeps the id, so a later reload picks it up once the modifier
exists.
