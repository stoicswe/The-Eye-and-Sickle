---
id: proof-of-work
section: 7
name: proof of work
canonical: proof of work
gloss: Buying the right to add to a shared history by burning computation.
status: real
aliases: PoW, mining, hashing, block
seeAlso: mining-pool(7), self-mining(7), ethecoin(7), compute(7), mine(1)
reading: Satoshi Nakamoto, "Bitcoin: A Peer-to-Peer Electronic Cash System" (2008), section 4; Adam Back, "Hashcash" (2002)
revision: 1
---

## DESCRIPTION

Ethecoin has no bank deciding whose balance is whose. What it has instead is a
shared history anyone can extend, and a rule that makes extending it
expensive: to add a block you must find a number that, hashed together with
the block, comes out below a target. There is no clever way to find one. You
try, and try, and try.

That is what your cycles do when you `mine`. Each attempt is a hash. The
target is set so the whole network together expects to succeed about once
every ten minutes.

Three numbers on the `mine` readout are the entire system:

       difficulty    how low the target is — expect difficulty x 2^32 hashes
                     per block
       hashrate      how many hashes per second this rig computes
       expected      the first divided by the second

A rig with 4% of the chain's hashrate expects 4% of the blocks.

Here is the part that surprises people. Because every hash is an independent
try against an unchanged target, **nothing accumulates**. A rig four hours
into a dry spell is exactly as close to the next block as one that started a
second ago. Nothing is banked. Nothing is forfeited by stopping. A long gap
does not make you due.

That is why this game shows you an expected time and never a progress bar.
The bar would be a lie, and a specific one: it would tell you to keep mining
to protect progress that does not exist.

The same property means the average badly misdescribes the typical. Waits
like this have a median around 69% of their mean, so more than half come in
early and a long tail runs to several times the average. Solo mining feels
unluckier than it is.

Difficulty is not fixed forever. Every 2016 blocks the network compares how
long that batch actually took against how long it should have, and re-tunes
the target to bring the pace back to ten minutes. One adjustment can never
move it by more than a factor of four in either direction.

## REAL-WORLD COUNTERPART

real, simplified — the equations here are Bitcoin's, unchanged. What is
simplified is the scale, and the stability of the network.

Bitcoin uses exactly the relation above, including the 2^32, the ten minutes,
and the 2016-block retarget window. The idea predates Bitcoin: Adam Back's
Hashcash proposed the same trick in 2002 as anti-spam postage — make the
sender burn a little computation, so sending one message is cheap and sending
a million is not.

Two honest differences. A real solo miner on consumer hardware is a
vanishingly small fraction of the network and would wait a geological length
of time for a block, which is not a game; this chain is small enough that a
full rig is a few percent of it. And this chain's other miners never arrive or
leave, so its difficulty has no long-term trend — a real chain's climbs,
because the hashrate behind it does.

It still wobbles, and that part is real. Two thousand and sixteen random block
times do not add up to exactly two weeks, so every retarget nudges the target
a percent or two in whichever direction the last batch happened to run.

The energy argument is real and is not settled here. Proof of work buys its
security by making history expensive to rewrite, and the expense is
electricity. That is the point and the objection at the same time.

## CAVEATS

The equations are Bitcoin's and are unchanged. Two things about the world
around them are not.

**Scale.** A real solo miner on consumer hardware would wait a geological
length of time for a block. This chain is small enough that a full rig is a
few percent of it.

**Stability.** This chain's other miners never arrive or leave, so its
difficulty has no long-term trend. It still jitters a percent or two at each
retarget, because random block times do not fill a window exactly — that part
is real. What is missing is the climb a growing network produces, which is the
thing the retarget was invented for.
