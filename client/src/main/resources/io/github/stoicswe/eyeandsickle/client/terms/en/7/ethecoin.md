---
id: ethecoin
section: 7
name: ethecoin
canonical: Ethecoin (EC)
gloss: The game's currency. It buys breadth, never a higher ceiling.
status: game
aliases: ec, coin, money
glossary: ../design/glossary.md
seeAlso: compute(7), ledger(1), self-mining(7), storage-tiers(7)
revision: 1
---

## DESCRIPTION

Ethecoin buys consumables, replacements, and horizontal options — a second
tool, a spare, a different approach. It never buys a ceiling.

That distinction is the economy's spine. You cannot buy compute, you cannot
buy vault capacity, and you cannot buy your way past a proof-of-skill gate.
What money does is let you keep operating: replace what you lost, carry a
spare, take a path you could not otherwise afford.

Balances are integral. There is no fraction of a minor unit anywhere in the
game, deliberately — see the counterpart below.

## REAL-WORLD COUNTERPART

game — ethecoin is invented, and it is not a cryptocurrency. There is no
chain, no wallet, no mining in the proof-of-work sense, and "mining" here
means something entirely different from what it means outside.

One real thing it does carry: money is stored as whole minor units, never as
a decimal fraction. Binary floating point cannot represent 0.1 exactly, so
two systems summing the same transactions in a different order can disagree
about a balance by a fraction of a penny. Every real financial system stores
integers for this reason. It is one of the most consequential small facts in
software, and this game is quietly built on it.
