---
id: ps
section: 1
name: ps
canonical: ps
gloss: Shows what is currently holding your rig's capacity.
status: real
seeAlso: compute(7), thermal-budget(7), grep(1), pipeline(7), scan(8)
reading: ps(1) | proc(5) — the Linux /proc filesystem | top(1)
revision: 1
---

## SYNOPSIS

       ps [-v] [-h] [--explain] [--]

## DESCRIPTION

Prints everything currently holding compute on your rig, one line each: what
it is, how many cycles it holds, and whether those cycles are active or
recovering.

The totals at the bottom should always add up: total = allocated +
recovering + available. If they ever do not, this says so — and that
discrepancy is worth more attention than anything else on the screen.

## OPTIONS

       -v, --verbose   include which action contributed each allocation
       -h, --help      print this synopsis
       --explain       print the description without running

## EXIT STATUS

       0    the listing was produced

## REAL-WORLD COUNTERPART

real — `ps` is on every Unix machine and does exactly this for processes.

The name is short for "process status". On Linux it reads `/proc`, a
directory that is not really a directory: it is the kernel presenting live
state as files. Try `ps aux` on any Mac or Linux machine, then `ps aux | wc -l`
to count what is running on a machine you thought was idle. The number is
usually a surprise.

`top` is the same information, updating.
