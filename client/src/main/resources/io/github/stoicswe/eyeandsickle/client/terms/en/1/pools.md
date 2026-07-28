---
id: pools
section: 1
name: pools
canonical: pools
gloss: List the mining pools on the chain and what each one costs.
status: game
seeAlso: mining-pool(7), proof-of-work(7), self-mining(7), mine(1)
revision: 1
---

## SYNOPSIS

       pools [--]

## DESCRIPTION

Prints every pool on the chain: its payout scheme, its fee, its share of the
network's hashrate, and how often it pays. A `*` marks the one you are mining
with.

Read the fee column and the pays column **together**. They pull against each
other, and a table sorted by fee alone would look like a ladder with an
obvious top:

       MERIDIAN CLEARING   PPS     3.50%   38%   every 15s
       THE COMMONS         PPS     2.00%   24%   every 30s
       PALE LANTERN        PPS     2.50%    9%   every 45s
       GLASS TEETH         PPLNS   1.00%   17%   every 59m
       SMALL HOURS         PPLNS   0.50%    5%   every 3.3h

The cheapest pool on the chain pays least often. SMALL HOURS is nearly as
lumpy as mining alone, which is what you were paying the others to avoid.

Only the **fee** changes what you earn. A pool's scheme and its size change
only how lumpily you earn it. See mining-pool(7).

The pays column has a third use: it is also how loud the pool makes you. Every
share is a packet to a third party, so a pool asking for one every fifteen
seconds is four times as audible as one asking every sixty. It is a very small
amount of noise either way — a couple of cycles against a sweep's thirty-five
— and it generates no heat. But if you are running quiet, the bottom of this
table is the quiet end.

`mine --pool=<id>` joins one. Switching costs nothing and forfeits nothing.

## EXIT STATUS

       0    the list was printed
       69   not connected to a chain

## REAL-WORLD COUNTERPART

game — no real mining client has a `pools` command, because pools are not a
property of the network. You find one by looking it up, make an account, and
point your miner at its address.

Everything the table *reports* is real, though: pools genuinely publish their
scheme, their fee and their share of network hashrate, and comparison sites
exist that tabulate exactly these columns. The one thing this list flatters is
that it is complete — a real chain has hundreds of pools and no authority that
knows about all of them.
