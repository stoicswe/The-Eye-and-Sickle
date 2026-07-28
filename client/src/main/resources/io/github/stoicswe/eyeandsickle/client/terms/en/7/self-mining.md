---
id: self-mining
section: 7
name: self-mining
canonical: Self-mining
gloss: Turning your own capacity into income. Safe, quiet, and online only.
status: real, simplified
aliases: mining, mine
glossary: ../design/glossary.md
seeAlso: compute(7), ethecoin(7), heat(7), proof-of-work(7), mining-pool(7), mine(1)
revision: 2
---

## DESCRIPTION

Commit cycles to self-mining and they work on the ethecoin chain for as long
as the client is open, earning 0.4 EC per cycle-hour.

It is the income floor, and it is deliberately boring. It generates no heat,
it cannot be detected, it cannot be seized, and nothing you do to your own
rig will ever attract attention for it. The only cost is the opportunity
cost: cycles spent mining are cycles not spent on anything else.

It earns nothing while you are logged off. That is the trade — the safest
income in the game is also the one that requires you to be present. Deployed
miners are the only offline income, and they carry every risk this does not.

The 0.4 EC per cycle-hour is an *average*, and you choose the shape it
arrives in.

       mine --pool   the default. Shares land every thirty seconds or so and
                     the pool settles up every minute, less its fee. `pools`
                     lists five, from 0.50% to 3.50% — and the cheapest pays
                     least often.
       mine --solo   no fee, and no floor. You are racing the whole chain for
                     a whole block. On a full rig that is about one payout
                     every four hours, and most hours pay nothing.

Expected income is the same either way — solo is very slightly higher,
because there is no fee to hand over. What differs is the variance, and it
differs by a factor of about twenty.

Pooled is the default on purpose: this is the income you fall back on when
heat has closed everything else off, and a floor that sometimes pays nothing
is not a floor. Solo is a thing you choose.

One more difference. A pooled rig holds a connection to its pool and pushes a
share up it on a timer, so it is **faintly audible** — a couple of cycles on
the noise meter, against a sweep's thirty-five. Solo mining is **silent**: the
work is local and nothing leaves your rig until you find a block.

Neither generates heat, and neither can be detected or seized. The noise does
not scale with how many cycles you commit, because a share is a small fixed
packet however much hashing produced it — so mining less makes you no quieter.
Picking a pool that asks for shares less often does.

Switching costs nothing and forfeits nothing. See proof-of-work(7) for why
there is no such thing as being partway through a block.

## REAL-WORLD COUNTERPART

real, simplified — this is a genuine proof-of-work simulation, using
Bitcoin's own equations. `mine` reports a real difficulty, a real hashrate
and a real expected time between blocks, and they are related exactly as they
are on a live chain.

What is simplified is scale. Real solo mining on consumer hardware is
hopeless — one participant is far too small a fraction of the network — so
this chain is small enough that your rig is a few percent of it. And the rest
of this chain's miners never arrive or leave, so its difficulty settles and
stays where a real one would keep moving.

What is game is the safety. Real mining is neither silent nor unseizable, and
nothing about a real chain protects you from anyone. The immunity here exists
because the design needs an income floor that heat cannot take away, not
because it is how anything works.

See proof-of-work(7) and mining-pool(7).

## CAVEATS

Three things here are not how real mining works, and the difference matters if
you carry any of it outward.

**The scale is wrong on purpose.** A real solo miner on consumer hardware is a
vanishingly small fraction of a real network and would wait years for a block.
This chain is small enough that a full rig is a few percent of it, which is
what makes solo mining a choice rather than a joke.

**The network never changes.** Real difficulty moves constantly, because the
hashrate behind it does. Here the other miners never arrive or leave, so
difficulty settles and stays. The retarget is real and runs; there is simply
nothing to move it.

**The safety is invented.** Real mining is neither silent nor unseizable, and
nothing about a real chain protects you from anyone. The immunity here exists
because this game needs an income floor that heat cannot take away. The one
honest part is the noise: a pooled miner really is holding a connection open
and really is sending something small, on a timer, to a third party.

See proof-of-work(7) and mining-pool(7).
