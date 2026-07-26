---
id: ledger
section: 1
name: ledger
canonical: ledger
gloss: Every movement of ethecoin, newest first.
status: real, simplified
seeAlso: ethecoin(7), mine(1), self-mining(7)
reading: git-log(1) | RFC 6962 (Certificate Transparency) | double-entry bookkeeping, c. 1494
revision: 1
---

## SYNOPSIS

       ledger [--since <duration>] [-v] [--]

## DESCRIPTION

Prints the ethecoin ledger: when, how much, the balance afterwards, and what
caused it.

Entries are added and never edited. Each row carries the balance *after* it,
so the log reconciles without replaying it from the beginning — if the last
row's balance is not your balance, something is wrong, and you can find where
by reading upward.

This is the only record of where your money went. There is no separate
"balance" stored somewhere else that could disagree with it, because the
balance is written by the same code that writes these rows.

## OPTIONS

       --since <duration>   only entries newer than this
       -v, --verbose        include which action produced each entry

## EXIT STATUS

       0    the listing was produced

## REAL-WORLD COUNTERPART

real, simplified — an append-only transaction log, which is how every real
accounting system works and has for about five hundred years. Double-entry
bookkeeping is the same idea: you do not edit history, you post a correcting
entry, and the trail of what happened survives.

The modern computing version is a hash-linked log — git does it for commits,
Certificate Transparency does it for TLS certificates. There the point is
that tampering becomes visible rather than impossible.

## CAVEATS

This ledger is not hash-linked and not signed. In a solo game it is a plain
list in a file you can edit, and nothing here would detect that you had.

That is the honest position rather than a gap: a chain signed by a key on the
same disk would prove only that the disk agreed with itself. The federated
version of this game does sign transfers, because there the record has to
convince somebody else.
