---
id: thermal-budget
section: 7
name: Thermal Budget
canonical: Thermal Budget
gloss: How fast spent capacity comes back, and why a busy rig is slower.
status: game
aliases: thermal, recovery
glossary: ../design/glossary.md
seeAlso: compute(7), scan(8), ps(1)
reading: Intel Turbo Boost technology documentation | thermal throttling in laptop CPUs
revision: 1
---

## DESCRIPTION

Cycles you spend on a discrete action do not come straight back. They return
over time, and the rate depends on how loaded your rig already is: the closer
you sit to capacity, the slower recovery gets.

The practical consequence is that over-committing compounds. A rig running
at 90% takes far longer to get a Thorough Scan's 35 cycles back than an idle
one does, so the cost of being busy is not just the cycles — it is the
recovery you have made expensive.

Thermal Budget is the rig stat that governs the rate. Improving it is one of
the quieter upgrades and one of the most useful.

## REAL-WORLD COUNTERPART

game — no machine has a stat called Thermal Budget, and cycles do not
"recover" on a real processor. When you stop using a CPU it is simply
available again.

The idea it borrows from is real, though, and you have felt it: a laptop
under sustained load gets hot, and a hot chip is *throttled* — clocked down
deliberately to shed heat. Intel calls the reverse Turbo Boost, which is a
short burst above the sustainable clock, available precisely because the chip
is cool enough to afford it. So "a machine near its limits performs worse
than the numbers suggest" is true. The specific mechanic here is not.
