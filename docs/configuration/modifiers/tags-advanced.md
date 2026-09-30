# Tags: Advanced

## Conditions

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
branch never run. Use the word operators for ordering comparisons,
and backslash escapes for literal brackets (see Escaping Special
Characters): quotes group condition text but never hid tags from
the scanner. Empty branch results, including side-effect branches
(which yield empty), are fine and warn-free on both paths.

!!! warning "Dead branches never run"
    Side effects in the unchosen branch (messages, sounds, flag
    writes, `<run>`) do NOT fire. Flatten nested `<if>`s into one
    condition with `and` / `or` whenever both elses agree:

    ```yaml
    # nested: same result, harder to read
    - '<if:"<pstat:<p>,health> le 6",<if:"<phasitem:<p>,golden_apple> == true","eat gap","wait">,"wait">'
    # flat: one condition
    - '<if:"<pstat:<p>,health> le 6 and <phasitem:<p>,golden_apple> == true","eat gap","wait">'
    ```

Tags on a command line resolve left-to-right: flag writes earlier
on the line are visible to `<if>`s and loops later on it, so setup
can share the line with the logic that reads it.

Ordering compares decimals as well as whole numbers (`7.5 gt 7`
holds, `7.5 le 7.5` holds). Each side compares as a number when it
parses as math, otherwise as text. Word operators need a
non-letter-or-digit boundary on each side (spaces work, so does the
edge of the condition), which keeps them distinct from tag brackets:

```yaml
- '<if:"<pstat:<p>,health> le 7 and <gstat:duration>-<pflag:lastuse-<id>> ?? 999999 gt 300","give <p> golden_apple","exit">'
```

An `<if>` without an else branch yields nothing when the condition
fails, so a bare guard line aborts the list only on a hit. This
first line skips the rest when the runner already holds a gapple:

```yaml
- '<if:"<phasitem:<p>,golden_apple> == true","exit">'
```

Angle brackets are never comparisons: `<`, `>`, `<=`, and `>=` in a
condition warn and yield nothing, naming the word operators to use
instead. Quote the condition when it holds commas or nested tags.

## Flags

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

`<lf>`, `<pf>`, and `<gf>` are short aliases for `<lflag>`,
`<pflag>`, and `<gflag>`; they behave identically everywhere,
including flag references and the tag cheatsheet.

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

## Lists

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

## Loops

`<for:list,body>` walks a list with each item behind `<i>`, and
`<while:condition,body>` repeats while its condition holds. Bodies
run for side effects (flag writes, messages, sounds, `<run>`
commands) and any text they produce is discarded; both loops leave
nothing behind on success. Like `<if>`, loops see flag writes
earlier on the line (see Conditions):

```yaml
- '<gflag:out,[]>'
- '<for:<range:3>,<list.append:<gflag:out>,item<i>>>'
- 'say <gflag:out>'
- '<for:<active-players:HUNTER>,<run:effect give <i> minecraft:glowing 10>>'
```

This says `[item0, item1, item2]`, then gives every hunter ten
seconds of glowing: `<run:>` dispatches its evaluated body from
the console per iteration, with the blacklist enforced and `exit`
holding no special meaning inside it.

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
administrator. Escape literal `<` and `>` inside loop bodies
(see Escaping Special Characters): quotes never hid tags from
the scanner.

## Functions

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
with the run like `<lflag>`. The creator editor collects every
`<def:name>` in the edited line's function scope, so calls to
defined functions (including recursive self-calls and calls from
other lines in the same scope) never warn; genuinely unknown tags
still warn, and still resolve at runtime.

Recursion terminates through `<if>` base cases, since dead
branches never run. Every call costs one of the line's 1000 shared
steps, and the budget never refunds: leaving a loop cannot launder
steps back, so runaway recursion always ends at the limit above
instead of crashing the server.

## Event args

Each trigger carries its own event args, read with `<args:index>`.
A missing index yields `null`; a non-numeric index warns and yields
`null`:

| Trigger | Args |
| --- | --- |
| `ON_START` | None: every index is `null`. |
| `INTERVAL` | `0`: the seconds actually waited for this firing. |
| `ON_MOB_KILLED` | `0`: the killed mob's entity type name (e.g. `ZOMBIE`). |
| `ON_PLAYER_KILLS`, `ON_HUNTER_KILLS`, `ON_SPEEDRUNNER_KILLS` | `0`: the exact name of the killer. `1`: the exact name of the killed player. |
| `ON_NETHER_ENTER`, `ON_END_ENTER`, `ON_FIRST_NETHER_ENTER`, `ON_FIRST_END_ENTER` | `0`: the origin world name, `1`: the destination world name. |
| `ON_EVERY_ADVANCEMENT` | `0`: the advancement's namespaced key (e.g. `minecraft:nether/root`). |
| `ON_RESPAWN`, `ON_SPEEDRUNNER_RESPAWN`, `ON_HUNTER_RESPAWN` | `0`: the death location as one list, `[x, y, z, pitch, yaw, dimension]`. |
| `ON_DAMAGE_TAKEN` | `0`: the damaged player's name. `1`: damage taken in half hearts. `2`: the damage dealer's name, or `null` for mobs and the environment. |

## Match control

`<loseplayer:player,reason>` instantly eliminates a hunter or
speedrunner by name (even one already dead) and moves them to
spectator, announcing `lost: <reason>` with that role's death sound.
`<win:ROLE,reason>` ends the match for `HUNTER` or `SPEEDRUNNER`
(case does not matter). Both tags leave nothing behind. The reason
is everything after the first comma, so it may hold commas; an empty
or missing reason becomes `unknown reason`. The win reason is posted
on the win screen.
