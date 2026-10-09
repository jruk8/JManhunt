# Dirty Max HP Hack

## What the warning means

Max health in JManhunt is owned by one shared ledger: each modifier
writes its own named entry through the `pmaxhp` tags and the engine
sums every entry onto the vanilla base of 20 (see [Shared max
health](tags-advanced.md#shared-max-health)). The engine remembers the
total it last applied per player.

When another modifier edits the max health attribute directly (an
`attribute` command, another plugin, or a datapack), the engine's next
refresh finds a value it never wrote and logs this once per player per
match:

```text
Dirty Max HP Hack detected for <name>: max health is <current> but the
engine last set <expected>. Another modifier is editing max health
directly; switch it to the pmaxhp tags:
https://jruk8.github.io/JManhunt/configuration/modifiers/dirty-max-hp/
```

The engine then overwrites the foreign value with the ledger total, so
the direct edit is lost.

## Why direct edits break

A modifier that writes the attribute only knows its own bonus. Two
such modifiers overwrite each other, and any `pmaxhp` user recomputes
from the ledger and silently wipes both. Through the ledger every
modifier keeps its own entry and all of them add up.

## How to fix a flagged modifier

Replace the direct edit with ledger tags under one id per modifier
(the modifier's own name is a good id):

```yaml
# Before: clobbers every other max health change.
- <run:attribute <p> minecraft:max_health base set 40>

# After: stacks with every other entry.
- <pmaxhp.set:<p>,my-mod,20>
```

Use `pmaxhp.modify` for bonuses that grow or shrink (lifesteal, kill
rewards), `pmaxhp.get` to read the modifier's own entry (0 when
unset), and `pmaxhp.clear` to drop entries the modifier no longer
needs. Amounts are health points added to the base 20, so a +20
entry means a 40-point total (20 hearts). Increments also heal the
gained amount; decreases never reduce current health.

A total of 0 or less kills permanently regardless of lives or role,
so a debuff modifier should clamp its own entry instead of driving
the total negative. Use `<peliminated:player>` after a write to check
whether the player died from it.
