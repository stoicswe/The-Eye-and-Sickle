---
id: compute
section: 7
name: compute
canonical: Compute / cycles
gloss: Your rig's capacity to do work, measured in cycles.
status: real, simplified
aliases: cycles, capacity
glossary: ../design/glossary.md
seeAlso: thermal-budget(7), self-mining(7), ethecoin(7), ps(1), scan(8)
reading: top(1) | nice(1) | cgroups(7) — Linux control groups
revision: 1
---

## DESCRIPTION

Compute is what your rig can do at once. A starting rig has 100 cycles, and
every meaningful decision in this game is a question of where they go.

It is not money. Cycles are not spent and gone — they are *allocated*, and
most allocations are reservations that last as long as the thing they power
runs. A deployed miner's control channel holds 3 cycles for as long as the
miner lives. An armed defence holds its cost until you disarm it. What comes
back is what you stop doing.

Discrete actions are different: a scan spends its cycles and they return
slowly, on a curve. See thermal-budget(7).

You cannot buy compute. Not with ethecoin, not ever — the rig expands only
through schematics and story milestones. That is a deliberate wall: an
economy where money buys capacity and capacity earns money is an economy
that only rewards whoever started first.

## REAL-WORLD COUNTERPART

real, simplified — this is CPU capacity and the scheduling of it.

A real machine has cores and a scheduler that shares them between processes,
and a real operator makes the same decision you make here: what runs, what
waits, and what is not worth the cycles. `top` shows you the same picture
your rig monitor does. `nice` changes who wins when two things want the
processor at once. Control groups on Linux do exactly what this game's
allocation model does — reserve a share of a machine for a named consumer.

## CAVEATS

A real processor does not have a single number called "capacity". It has
cores, threads, clock speeds, cache levels and memory bandwidth, and which
of them limits you depends entirely on what you are running.

One cycle here is a unit of allocation, not a real clock cycle. A real CPU
cycle is about a third of a nanosecond; you get billions per second. The
word is borrowed for the idea — a fixed budget of work per unit time — and
not for the magnitude.
