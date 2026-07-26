---
id: noise
section: 7
name: noise
canonical: Noise
gloss: Short-term visibility from what you are doing right now. It fades.
status: game
aliases: visibility
glossary: ../design/glossary.md
seeAlso: heat(7), self-mining(7)
notes: Distinct from heat(7). Noise decays; heat does not. The pair is the point.
revision: 1
---

## DESCRIPTION

Noise is the trace your current activity leaves. It rises while you work and
falls when you stop, and it pools across everything you have running at once
— you and your bots share one meter.

That pooling is the reason a large operation is harder to run quietly than a
small one, regardless of how careful any single action is.

Noise fades. Heat does not. Getting the two confused is the most expensive
misunderstanding available in this game: waiting out noise works, waiting out
heat does not.

## REAL-WORLD COUNTERPART

game — no system emits a number called noise.

What it stands in for is real and worth knowing: activity generates
observable signal whether or not anyone is looking at it yet. Traffic volume,
connection counts, timing patterns and log entries are all produced as a side
effect of doing things, and they are all still there afterwards. The
game's decay models the fact that most of it is only interesting while it is
fresh — which is true, and is why real detection cares about recency.
