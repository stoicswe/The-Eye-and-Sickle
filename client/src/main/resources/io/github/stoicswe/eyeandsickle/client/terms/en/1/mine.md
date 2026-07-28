---
id: mine
section: 1
name: mine
canonical: mine
gloss: Commit cycles to mining, switch payout mode, or report the chain.
status: game
seeAlso: self-mining(7), proof-of-work(7), mining-pool(7), compute(7), ethecoin(7), ledger(1), pools(1)
revision: 2
---

## SYNOPSIS

       mine [--allocate=<cycles>] [--pool[=<id>] | --solo] [-n] [--]

## DESCRIPTION

With no argument, reports: the chain's height and difficulty, this rig's
hashrate and its share of the network, what a payout is worth, how often to
expect one, and what mining has paid you so far.

`mine --allocate=<cycles>` commits cycles. `mine --allocate=0` stops.

Committed cycles average 0.4 EC per cycle-hour while the client is open, and
are unavailable for anything else in the meantime. That is the entire trade:
this is the safest income in the game and the cycles are the price.

It earns nothing while you are logged off. Close the client with 100 cycles
committed, come back a week later, and you will find exactly what you left.

`--pool` (the default) mines with a pool; `--pool=<id>` joins a particular
one, and `pools` lists them with their fees and payout intervals. A pool mines
against a share target: a small payout
every thirty seconds or so, less the pool's fee. `--solo` mines against the
whole chain: the entire block subsidy, or nothing, with a full rig expecting
one block about every four hours.

Expected income is the same either way — solo is slightly higher, because
there is no fee. The variance is not the same, and that is the whole choice.
The same holds between pools: only a pool's fee changes what you earn, and its
scheme and size change only how lumpily. See mining-pool(7).

Switching costs nothing and forfeits nothing. Mining is memoryless: there is
no such thing as being partway through a block, so there is nothing to lose.
See proof-of-work(7).

## OPTIONS

       --allocate=<cycles>   how many cycles to commit
       --pool[=<id>]         mine with a pool; names one to join it (default)
       --solo                whole blocks at long random intervals, no fee
       -n, --dry-run         print the published figures; change nothing
       -v, --verbose         report the allocation that was created

## EXIT STATUS

       0    the allocation was changed
       1    refused — not enough available compute
       2    bad invocation

## REAL-WORLD COUNTERPART

game — the mechanic is invented, and it has nothing to do with cryptocurrency
mining, which is a competitive race rather than a rate.

The `--dry-run` flag is real convention, though, and worth carrying: `rsync -n`
lists the files it would copy, `make -n` prints the commands it would run,
`apt-get -s` simulates an install. Any operation that is hard to reverse is
worth asking about first, and a surprising number of real tools will tell you.

Note what a dry run here does not do: it prints the published numbers and no
verdict. It will not tell you whether you can afford something. That is
deliberate — the numbers are shown so you can do the arithmetic, because the
answer is not the client's to give.
