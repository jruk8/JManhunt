# Modifiers

**Modifiers** are named command bundles in `modifiers.yml` that run
console or per-player commands on match triggers. They are disabled
by default; details live in [Behaviors](modifiers/behaviors.md),
[Command Lists and Targeting](modifiers/command-lists.md),
[Tags: Basics](modifiers/tags-basics.md),
[Tags: Advanced](modifiers/tags-advanced.md), and
[Creating and Sharing](modifiers/creating.md).

## Tag cheat sheet

Every engine tag, one line each. Signatures show the common shape;
see the Tags pages for full rules.

### Basic

| Tag | Meaning |
| --- | --- |
| `<p>` | The participating player's name. |
| `<id>` | Name of the running modifier (or trigger). |
| `<args:0>` | Trigger's event arg by index; bare `<args>` reads `0`. |
| `<placeholder:key>` | PlaceholderAPI (or in-house `jmanhunt_*`) value as a tag. |
| `<random-mob>` | Random spawnable entity type, lowercase. |
| `<random-item>` | Random item material, lowercase. |
| `<random-num:4,12>` | Random whole number in range, order free. |
| `<random-pick:a,b>` | One random item from the list. |
| `<random-player>` | One random participant. |
| `<all-players>` | Every participant as a name list (`:ROLE` filters). |

### Messages and Sounds

| Tag | Meaning |
| --- | --- |
| `<gmessage:text>` | Send text to every participant; leaves nothing behind (alias `<gmsg>`). |
| `<pmessage:player,text>` | Send text to one player (alias `<pmsg>`). |
| `<rmessage:role,text>` | Send text to one role (`ALL` sends to both; alias `<rmsg>`). |
| `<gsound:id,pitch,volume>` | Play a sound for every participant. |
| `<psound:player,id,pitch,volume>` | Play a sound for one player. |
| `<rsound:role,id,pitch,volume>` | Play a sound for one role (`ALL` plays for both). |

### Stats and Roster

| Tag | Meaning |
| --- | --- |
| `<pstat:player,key>` | One player's stat (`health`, `hunger`, ...). |
| `<gstat:key>` | Match-wide stat (`duration`, ...). |
| `<phasitem:player,item,count>` | `true` when the player holds count of item. |
| `<pheld:player>` | Main-hand material, else `null`. |
| `<active-players:ROLE>` | Eligible names as a list (`ALL` lists both sides). |
| `<plocation:player>` | Player spot as `[x, y, z, world, pitch, yaw]`. |
| `<overlap-players:origin,role,radius,max>` | Names near an origin, nearest first. |
| `<nearby-players:player,role,radius,max>` | Names near a player, sender excluded. |
| `<pworld:player>` | `nether`, `end`, or the raw world name (`<world:player>` alias). |
| `<px:player>` | Single coords (`py`, `pz`, `pyaw`, `ppitch`). |
| `<prole:player>` | `HUNTER` or `SPEEDRUNNER`, else `null`. |
| `<distance:loc1,loc2>` | 3D distance on xyz; cross-dimension full lists yield silent `null`. |

### Math

| Tag | Meaning |
| --- | --- |
| `<min:a,b>` | The smaller number. |
| `<max:a,b>` | The larger number. |
| `<clamp:x,low,high>` | `x` clamped into range. |
| `<floor:2.7>` | `2`, rounds down. |
| `<ceil:2.3>` | `3`, rounds up. |
| `<round:2.5>` | `3`, rounds half up. |
| `<abs:-4>` | `4`. |
| `<sign:-4>` | `-1`, else `0` or `1`. |
| `<sqrt:9>` | `3`, square root (`null` for negatives). |
| `<cbrt:-8>` | `-2`, cube root. |
| `<root:16,4>` | `2`, the nth root of `x`. |
| `<range:1,5>` | `[1, 2, 3, 4]`, Python style. |
| `<len:list>` | Item count, `0` when no list. |
| `<format:"{0} found {1}",[Alex,gold]>` | Plain `{n}` substitution, silent on mismatch. |
| `<format>` mismatch rules | Missing indexes, `{x}`, and stray braces stay verbatim silently; `{0:D}`-style specifiers stay literal; malformed shape warns plus `null`. |

### Conditions

| Tag | Meaning |
| --- | --- |
| `<if:"a == b","y","n">` | `y` when the condition holds, else `n` (else omittable). |

### Lists

| Tag | Meaning |
| --- | --- |
| `<list.append:list,x>` | Append `x`, store back, empty. |
| `<list.get:list,index>` | Item at index, `null` when missing. |
| `<list.set:list,index,x>` | Set index, store back, empty. |
| `<list.remove:list,x>` | Remove first `x`, `true`/`false`. |
| `<list.contains:list,x>` | `true` when `x` is an item. |
| `<list.clear:list>` | Empty the list, store back. |
| `<list.pop:list>` | Remove and return the first item. |
| `<list.shuffle:list>` | Shuffle, store back, empty. |
| `<list.filter:list,cond>` | Items whose condition is `true` (`<i>` bound). |
| `<list.reverse:list>` | Reversed copy. |
| `<list.join:list,sep>` | Items joined with `sep`. |
| `<list.slice:list,start,end>` | Python-style slice. |
| `<list.first:list>` | First item, `null` when empty. |
| `<list.last:list>` | Last item, `null` when empty. |

### Strings

| Tag | Meaning |
| --- | --- |
| `<str.join:list,sep>` | Same join as `<list.join>`. |
| `<str.split:text,delim>` | Literal split to a list. |
| `<str.lower:text>` | Lowercase. |
| `<str.upper:text>` | Uppercase. |
| `<str.contains:text,needle>` | `true` when held (case-sensitive). |

### Cooldowns

| Tag | Meaning |
| --- | --- |
| `<pcooldown:player,key,seconds>` | `true` when ready, stamps. |
| `<pcooldown.get:player,key,seconds>` | Seconds left, else `0`. |
| `<pcooldown.reset:player,key>` | Clears, `true`. |
| `<gcooldown:key,seconds>` | Match-wide gate, `true` when ready. |
| `<gcooldown.get:key,seconds>` | Seconds left, else `0`. |
| `<gcooldown.reset:key>` | Clears, `true`. |

### Flags

| Tag | Meaning |
| --- | --- |
| `<gflag:name,value>` | Match-wide flag (alias `<gf>`). |
| `<pflag:name,value>` | Per-player flag (alias `<pf>`). |
| `<lflag:name,value>` | Run-only flag (alias `<lf>`). |
| `<rflag:role,name,value>` | Flag of the named role (`ALL` fans out sets, reads consensus). |
| `<default:value,fallback>` | Fallback when blank or `null`. |

### Loops and Functions

| Tag | Meaning |
| --- | --- |
| `<while:cond,body>` | Repeat the body while the condition holds. |
| `<for:[a,b],body>` | Run the body per item with the item behind `<i>`. |
| `<i>` | Innermost for-loop item, else `null`. |
| `<def:double,x+x,x>` | Run-local function. |
| `<run:say hi>` | Run a console command, empty. |

### Match Control

| Tag | Meaning |
| --- | --- |
| `<loseplayer:player,reason>` | Eliminate a player, empty. |
| `<win:ROLE,reason>` | End the match for a role, empty. |

## 3-minute quickstart

1. Enable the bundled beef modifier:
   `/manhunt config modifiers everyone-gets-beef enabled true`.
2. In `plugins/JManhunt/modifiers.yml`, change
   `give <p> minecraft:cooked_beef 8` to `... cooked_beef 16`.
3. Run `/manhunt reload`.
4. Start a match: every participant gets 16 steak.

## 20-minute JMHScript quickstart

File layout: `plugins/JManhunt/modifiers.yml` holds a `modifiers:`
map. Each entry has `enabled`, `meta:` (name, description, icon,
author), and `behavior:` with numbered blocks.

Behavior anatomy: each block picks triggers under `runs-on:`
(`ON_START`, `INTERVAL`, kill and portal events, ...), optional
timing options, and `commands:` lists. List flavors are `player`
(everyone), `hunter`, `speedrunner`, and `console`. See
[Behaviors](modifiers/behaviors.md) and
[Command Lists](modifiers/command-lists.md).

Evaluate-then-dispatch: tags resolve to text first, then the whole
line runs as one console command. A line resolving to exactly
`null` never dispatches; a lone `exit` stops the list.

Tags nest inside out: `<random-pick:coal <random-num:4,12>>`
rolls the number, then picks. A backslash escapes any char:
`\<yellow\>` stays literal for MiniMessage, `\,` keeps one arg
with a comma. Write escaped lines single-quoted. See
[Tags: Basics](modifiers/tags-basics.md).

Flags hold state: `<gflag>` match-wide, `<pflag>` per player,
`<lflag>` for the run only. `<if>` branches with `==`, word
operators (`lt`, `le`, `gt`, `ge`), `and`/`or`/`not`; only the
chosen branch runs. See [Tags: Advanced](modifiers/tags-advanced.md).

Test from chat: `/manhunt modifiers test hunter give <p> bread`
runs lines as a hunter with mock vitals; the GUI editor's test
button does the same per list.

One complete modifier, annotated:

```yaml
modifiers:
  gapple-comeback:
    enabled: false
    meta:
      name: "Gapple Comeback"
      description: "Low runners get a gapple every 10 seconds"
      item: GOLDEN_APPLE
      author: You
    behavior:
      0:
        runs-on:
          - INTERVAL
        options:
          interval-settings:
            interval: 10
        commands:
          speedrunner:
            # 6 health or less: hand a gapple, else stop the list
            - '<if:"<pstat:<p>,health> le 6","give <p> golden_apple","exit">'
            # only reached on a hit: confirm in yellow
            - '<pmessage:<p>,\<yellow\>Second wind!'
```
