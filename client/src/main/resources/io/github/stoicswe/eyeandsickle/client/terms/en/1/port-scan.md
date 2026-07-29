---
id: port-scan
section: 1
name: port-scan
gloss: Asks a machine, from outside, what it is willing to tell you.
status: real, simplified
aliases: portscan, scan-node, probe
seeAlso: scan(8), noise(7), heat(7), compute(7)
reading: nmap(1) | RFC 9293 §3.1 (TCP header, ports are 16 bits) | RFC 6335 §6 (the port registry)
notes: NOT scan(8), which audits your own rig — see the CAVEATS. The design docs call the recon tool "Port Sweep"; this game already uses "sweep" for finding machines, so the outward probe of one machine is named for what it is. See design/15.
revision: 1
---

## SYNOPSIS

       port-scan ADDRESS --for TARGET

## DESCRIPTION

Looks at a machine you do not hold, from outside it. You name the deepest
thing you want to learn, and that sets what it costs — in cycles, in time,
and in the chance the machine notices you doing it.

       --for firewall      5 cycles   15s    what is filtered
       --for os            7 cycles   30s    banner and stack fingerprint
       --for capability    9 cycles   45s    how big the machine is
       --for load         11 cycles   60s    cycles free and used, right now
       --for downloads    13 cycles   75s    how much is in its download folder
       --for hot-vault    15 cycles   90s    how many items are exposed
       --for mid-vault    17 cycles  105s    an estimate, never a count

Everything above your chosen rung comes back with it: a scan that reached
that far already passed through the rest.

The findings collect into a report on that machine, which is kept. Each one
records when it was learned, because they age at different rates — the load
figure is a snapshot and is a guess an hour later, while a firewall reading
holds until somebody changes it.

Being noticed is not merely a wasted scan. A machine that catches you may
refuse the scan, and a defended one may come back at you.

## OPTIONS

       --for TARGET    the deepest thing to learn; sets cost, time and risk
       --              end of options

## EXIT STATUS

       0    the scan was started
       1    refused — not enough available compute
       69   no such machine, or no sweep has found it yet

## REAL-WORLD COUNTERPART

real, simplified — this is port scanning, and the standard tool is `nmap(1)`.

Three real things this gets right. A port is a 16-bit number, so a machine has
65 535 of them per protocol, and asking about all of them takes noticeably
longer than asking about a few. A closed port and a filtered port answer
differently, which is why a firewall's posture is the cheapest thing to learn
— refusing you *is* an answer. And scanning is loud: it is ordinary traffic in
unusual shapes, and anything watching can see it.

Version and OS detection are real too. `nmap -sV` reads service banners and
`nmap -O` fingerprints the TCP/IP stack, because implementations differ in
ways nobody standardised — window sizes, option ordering, how they answer
malformed packets.

## CAVEATS

The simplification is the ladder. A real scan does not have seven priced
rungs; it has flags, and the trade is between how much you probe and how long
you can afford to take. What is faithful is the shape of that trade.

Two things here are not real at all. No scan tells you what is in somebody's
storage — that is the game standing in for a much longer chain of intrusion,
and the estimate with an error band is the game admitting it. And a real
machine does not hack you back for scanning it; it logs you, and a person
decides what to do about it later.

⚠ This is not `scan(8)`. That one audits your own rig, costs no heat and
tells nobody. This one touches a machine that is not yours.

The real caution has nothing to do with the game: scanning machines you do not
own or have permission to test is, in many places, a criminal offence.
