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
- '<if:"<pstat:<p>,health> le 7 and <gstat:duration>-<default:<pflag:lastuse-<id>>,-999999> gt 300","give <p> golden_apple","exit">'
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
`null`, which pairs with `<default>` for fallbacks.
`<default:<pflag:cooldown>,0>` reads `0` until the flag is set; any
blank or case-blind `null` value falls back, anything else passes
through. Names match exactly and may hold spaces inside quotes.

| Tag | Scope | Lifetime |
| --- | --- | --- |
| `<gflag:name,value>` | Whole match | Dies with the match. |
| `<pflag:name,value>` | Executing player (`-CONSOLE` for console lists) | Flushed when the player leaves, is eliminated, or disconnects for good; dies with the match. |
| `<rflag:role,name,value>` | Named role (`hunter`, `speedrunner`, or `ALL`) | Dies with the match; player removal leaves role flags alone. |
| `<lflag:name,value>` | This run only | Set in an early line, read in a later line, discarded after. |

`<lf>`, `<pf>`, and `<gf>` are short aliases for `<lflag>`,
`<pflag>`, and `<gflag>`; they behave identically everywhere,
including flag references and the tag cheatsheet.

`<pflag:"cooldown",<gstat:"duration">>` stamps a cooldown;
`<pflag:"cooldown">` reads it back. `<rflag>` names its role up
front: `<rflag:hunter,boost>` and `<rflag:speedrunner,boost>` keep
independent values under the same name. The role is `hunter`,
`speedrunner`, or `ALL` (any case). `ALL` fans a set out to both
roles and reads back the shared value, or `null` when the two sides
disagree. Anything else warns, reads `null`, and skips writes. Role
flags need no executor, so console lists can use them too.

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
| `<list.filter:[a,b],cond>` | Items whose condition resolves to `true`. |
| `<list.reverse:[a,b]>` | `[b, a]`. |
| `<list.join:[a,b],->` | `a-b`. |
| `<list.slice:[a,b,c],1,3>` | `[b, c]`. |
| `<list.first:[a,b]>` | `a` (`null` when empty). |
| `<list.last:[a,b]>` | `b` (`null` when empty). |
| `<range:5>` | `[0, 1, 2, 3, 4]`; `<range:1,5>` starts at `1`, and `<range:5,0,-1>` counts down. |

`<range>` follows Python: start inclusive, stop exclusive, step `1`
unless given. Ranges past 1000 items keep the first thousand with a
warning; a zero step or a non-number warns and yields `null`.

The mutating ops (`append`, `set`, `remove`, `clear`, `pop`,
`shuffle`) write back when their list is a verbatim flag reference:
`<list.append:<gflag:nums>,4>` grows the stored flag and leaves
nothing behind. Anything else (literals, other expressions) applies
purely and returns the new list.

`<list.filter:list,condition>` keeps the items whose condition
resolves to `true` (case-blind, nothing else counts), with each
item behind `<i>` in turn:
`<list.filter:[a,bb,abc],<str.contains:<i>,b>>` is `[bb, abc]`.
The condition re-resolves per item like a loop body and must hold
no top-level commas, so quote-free tag expressions work best; each
item spends one shared line step like a loop iteration. A non-list
warns and yields `null`.

`<list.slice:list,start,end>` follows Python: start inclusive, end
exclusive, negatives count from the end, out-of-range bounds clamp.
Non-integer bounds warn and yield `null`. `<list.reverse>` returns
a reversed copy, `<list.first>` and `<list.last>` read the ends
(`null` when empty), and `<list.join:list,separator>` joins with
the separator verbatim (it may be empty). Joining a non-list warns
and yields `null`.

## Strings

`<str.join:list,separator>` is the same join as `<list.join>`,
and `<str.split:text,delimiter>` inverts it: the split is literal
(never a pattern) and keeps every part, so splitting a join
restores the list exactly. The delimiter must be non-empty, and
text holding commas needs quotes (`<str.split:"a,b",",">` is
`[a, b]`); dynamic comma text nests behind quotes too
(`<str.split:"<gflag:csv>",",">`). `<str.lower>` and `<str.upper>`
fold case, and `<str.contains:text,needle>` is a case-sensitive
`true`/`false` test.

## Cooldowns

`<pcooldown:player,key,seconds>` gates one player and
`<gcooldown:key,seconds>` gates match-wide, each with `.get`
(remaining window, `0` when ready or unknown) and `.reset`
(clears, yields `true`) siblings. The set tags stamp only when the
gate opens (no stamp or the window elapsed) and yield `true` then,
else `false` without touching the stamp, so polling never extends
a cooldown:

```yaml
- '<if:"<pcooldown:<p>,dash,5> == false","exit">'
- 'effect give <p> minecraft:speed 5 1 true'
```

Player keys namespace below the lower-cased name, so `Steve` and
`steve` share one cooldown; offline or unknown players resolve
`null` silently. Non-numeric or negative seconds warn and yield
`null`, as do blank players and keys. Stamps clear with the match
on teardown.

## Vectors

Every `vec.` op reads its vectors from 3-element lists or
6-element location primitives (first three indices); anything else
warns and yields `null`. `<pdir:player>` is the player's unit look
direction, y-up (straight up is `[0, 1, 0]`); offline or unknown
players resolve `null` silently. Normalizing the zero vector
yields `[0, 0, 0]` silently, never a warning.

| Tag | Meaning |
| --- | --- |
| `<vec.add:a,b>` | `[a0+b0, a1+b1, a2+b2]`. |
| `<vec.sub:a,b>` | A minus B: the direction from B to A. |
| `<vec.mult:vec,scalar>` | `[v0*s, v1*s, v2*s]`; a non-numeric scalar warns plus `null`. |
| `<vec.normalize:vec>` | The unit vector (`[0, 0, 0]` for zero). |
| `<vec.sqrdist:a,b>` | Squared distance. |
| `<vec.dist:a,b>` | Euclidean distance. |
| `<vec.dot:a,b>` | Dot product scalar. |
| `<vec.cross:a,b>` | Cross product vector. |
| `<loc.shift:loc,dir,dist>` | Base plus direction times distance, as `[x, y, z, world, pitch, yaw]`. |
| `<ploc:player>` | Alias of `<plocation:player>`. |

`<loc.shift:location,direction,distance>` needs a full 6-element
base and uses the direction as given, so callers normalize first
when they need unit steps; world, pitch, and yaw carry over
verbatim from the base. Aim from yourself at Alex, then spawn five
blocks ahead of yourself:

```yaml
- '<gflag:aim,<vec.normalize:<vec.sub:<ploc:Alex>,<ploc:<p>>>>>'
- '<gflag:ahead,<loc.shift:<plocation:<p>>,<pdir:<p>>,5>>'
- 'summon minecraft:armor_stand <list.get:<gflag:ahead>,0> <list.get:<gflag:ahead>,1> <list.get:<gflag:ahead>,2>'
```

## Players

`<pstate:player,state>` reads the live body: `SNEAK`, `SPRINT`,
`GLIDE`, `SWIM`, or `GROUND` (any case), `true` or `false`.
Unknown states warn, list the valid names, and yield `null`;
offline or unknown players resolve `null` silently.
`<pstandingon:player>` reports the upper-case material below the
feet (`AIR` over the void), silent `null` when offline.
`<pstat:player,exp-level>` reads the vanilla experience level.

`<ptitle:player,title,subtitle,stay,in,out>` sends a center-screen
title; the timings are seconds in written order (stay, fade in,
fade out: never Paper order), trailing args may be omitted, and
defaults are stay `2.0`, in `0.4`, out `0.4`. Seconds convert to
ticks through the engine converter (0.05s per tick, minimum one
tick). Title and subtitle parse like message text (MiniMessage
plus legacy codes); blank parts are allowed. Bad timing warns
plus `null` with nothing sent, as does an offline player; success
returns empty.

`<pslot:player,slot>` gets `[MATERIAL, qty]`, with `null` for an
empty slot or an offline player. `<pslot:player,slot,newitem>`
sets unconditionally and returns empty. Slots name equipment
(`mainhand`, `offhand`, `helmet`, `chestplate`, `leggings`,
`boots`, any case) or address the raw player inventory by index:
0-8 hotbar, 9-35 main storage, 36-39 armor, 40 offhand. A
`[material, qty]` list sets both, a bare material sets qty 1;
materials match case-blind, qty clamps silently to 1 through the
max stack, and anything invalid (unknown slot or material,
non-integer qty, bad index) warns plus `null` with no change.

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
| `ON_RESPAWN`, `ON_SPEEDRUNNER_RESPAWN`, `ON_HUNTER_RESPAWN` | `0`: the death location as one list, `[x, y, z, world, pitch, yaw]`. |
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
