---
id: mining-pool
section: 7
name: mining pool
canonical: mining pool
gloss: Sharing the luck of mining, so income is steady instead of lumpy.
status: real
aliases: pool, pooled mining, PPS, pay-per-share, share
seeAlso: proof-of-work(7), self-mining(7), ethecoin(7), mine(1)
reading: Meni Rosenfeld, "Analysis of Bitcoin Pooled Mining Reward Systems" (2011)
revision: 1
---

## DESCRIPTION

Solo mining pays everything or nothing. On a full rig you expect a block about
every four hours, so most hours pay you nothing at all and occasionally one
pays a great deal. The expected income is fine. The experience is not, and if
you need to eat this week the expectation is cold comfort.

A pool fixes the shape without changing the size. You point your cycles at the
pool instead of the chain, and the pool hands you an easier target than the
real one — easy enough that you hit it every thirty seconds or so. Each hit is
a **share**: proof you really did the work, worth nothing to anyone else, but
enough for the pool to know what you contributed.

The pool pays a fixed amount per share, whether or not it found a block that
day. That is pay-per-share, and it is the pool taking the variance off your
hands and onto its own books. It charges a fee for that.

So the trade is exactly this, and it is usually described backwards:

       same        pooled and solo have the same expected income, less the fee
       less        pooled pays slightly less on average — the fee
       steadier    enormously. A hundred and twenty small payouts an hour
                   instead of a quarter of one large one, and an hour-to-hour
                   swing around twenty times smaller

You are not buying income. You are buying predictability, and the fee is the
price.

The easier target is retuned to your rig rather than fixed — a small rig gets
an easier one, a large rig a harder one, and both submit at about the same
pace. That is why pooling smooths a ten-cycle rig as well as a hundred-cycle
one.

Not every pool works this way. Run `pools` and you will see two kinds:

       PPS     pays per share, so income is smooth however small the pool is.
               The operator fronts your pay through their own unlucky weeks
               and charges more for it.
       PPLNS   pays only out of blocks the pool actually finds. Cheaper,
               because it promises less — and the pool's size becomes your
               variance. Five percent of the chain is a payout every three
               hours.

So the cheapest pool on the chain is the one that behaves most like the solo
mining you were trying to escape. That is not a trick. It is what the fee was
buying.

Only the **fee** changes what you earn. The scheme and the size change only
how lumpily you earn it.

A share is not a block. Your shares never appear on the chain and never move
its height; only the pool's actual blocks do.

The pool settles up every minute rather than per share. Between settlements
what you have earned sits on its books as an unpaid balance — the mining panel
shows it with a countdown. Nothing is at risk and nothing is lost; it is how
real pools account, and it is why your ledger has one readable row a minute
instead of a hundred and twenty an hour.

Being pooled is also the one way self-mining makes any noise at all: you are
holding a connection open and sending something small up it on a timer. It is
a couple of cycles, it generates no heat, and nothing can seize it — but a
pool that wants shares twice as often is twice as loud, which is worth knowing
if you are trying to stay quiet.

## REAL-WORLD COUNTERPART

real — the mechanism, the vocabulary and the trade are all as described.

Real pools use exactly this: a per-miner share target retuned to the miner's
hashrate. The operators call it "vardiff" and configure it with a target time
per share, the same way this game does. Pay-per-share is one of several payout
schemes; the main alternative, PPLNS, pays only out of blocks the pool
actually found, so it passes some of the luck back and charges a lower fee for
doing less. Published PPS fees run around 2-4%, PPLNS around 0-2%. The gap
between them is roughly the price of the variance.

The reason pools dominate real mining is the reason they exist here. As a
network grows, one participant's share of it shrinks and the wait between solo
blocks grows past days, past months, past any horizon a person can plan
against. Pooling makes nobody richer. It makes the income describable.
