---
id: scan
section: 8
name: scan
canonical: scan
gloss: Searches your own rig for things hiding from routine listings.
status: real, simplified
seeAlso: ps(1), compute(7), thermal-budget(7), heat(7)
reading: rkhunter(8) | chkrootkit(8) | AIDE and Tripwire — file integrity monitoring
revision: 1
---

## SYNOPSIS

       scan [--quick | --full | --thorough] [-n] [--]

## DESCRIPTION

Looks for what `ps` will not show you. Three tiers, and what you buy with the
more expensive ones is *signal strength*, not certainty:

       --quick       5 cycles, 30 seconds
       --full       15 cycles, 2 minutes
       --thorough   35 cycles, 6 minutes

A Thorough Scan is a third of a starting rig. That is a decision, not a
button — and the cycles come back slowly afterwards, more slowly the busier
your rig already was.

Scanning your own machine never generates heat.

## OPTIONS

       -n, --dry-run   print the published cost and requirements; send nothing
       --              end of options

## EXIT STATUS

       0    the scan was started
       1    refused — not enough available compute

## REAL-WORLD COUNTERPART

real, simplified — this is host-based rootkit and integrity scanning.
`rkhunter` and `chkrootkit` look for known signs of compromise; AIDE and
Tripwire hash everything and tell you what changed.

Two real things this gets right. Scanning genuinely costs resources — a
thorough integrity scan on a large filesystem is expensive enough that people
schedule it for the small hours. And a more expensive scan really does buy a
better chance rather than a guarantee.

## CAVEATS

A real scanner does not have three tiers with fixed prices. It has a
configuration, and the trade you are making is between how much you examine
and how long you can afford to spend.

The deeper simplification: no scanner returns a clean yes or no. It returns
findings, most of which are false alarms — and on a machine where intrusions
are rare, *most alerts from an accurate scanner are wrong*. That is
arithmetic rather than a flaw in the tool.
