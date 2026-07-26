---
id: mine
section: 1
name: mine
canonical: mine
gloss: Commit cycles to self-mining, or take them back.
status: game
seeAlso: self-mining(7), compute(7), ethecoin(7), ledger(1)
revision: 1
---

## SYNOPSIS

       mine --allocate=<cycles> [-n] [--]

## DESCRIPTION

Commits cycles to self-mining. `mine --allocate=0` stops.

Committed cycles earn 0.4 EC per cycle-hour while the client is open, and are
unavailable for anything else in the meantime. That is the entire trade: this
is the safest income in the game and the cycles are the price.

It earns nothing while you are logged off. Close the client with 100 cycles
committed, come back a week later, and you will find exactly what you left.

## OPTIONS

       --allocate=<cycles>   how many cycles to commit
       -n, --dry-run         print the published rate and your capacity; change
                             nothing
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
