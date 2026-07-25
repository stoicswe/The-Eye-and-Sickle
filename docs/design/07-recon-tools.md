# 07 — Recon & Discovery Tools

**Status:** Established (design sessions 1–5)
**Depends on:** `02-unlock-gates.md`, `01-core-resources.md` §3 (noise)
**Depended on by:** `04-mining.md` (Provenance Tracer), `05-hacking-minigame.md` (information before the breach)

Information tools. Recon is how a player converts session time and compute into *knowing* before *doing* — which is what makes conditional tools like Rainbow Table (`06`) worth owning and what keeps breaches from being blind gambles.

---

## 1. Tool table (established)

| Tool | Function | Gate | Cost | Compute | Noise |
|---|---|---|---|---|---|
| **Passive Sniffer** | Reveals adjacent node types without touching them | Ethecoin | 15 EC | 3 | None |
| **Topology Mapper** | Extends graph visibility from one hop to two | **Schematic** | — | 9 | None |
| **Traffic Analyzer** | Distinguishes active/defended nodes from dormant ones | Reputation | — | 6 | Low |
| **Ping Sweep** | Locates offline players' exposed stashes | Ethecoin | 45 EC | 8 | **High — target is notified something pinged them** |
| **Honeypot Detector** | Flags Eye-planted traps | Schematic | — | 11 | Low |
| **Provenance Tracer** | Audits the player's own deployed miners for hijacking or channel sabotage | Ethecoin | 30 EC | 4 | None |

## 2. Per-tool notes

**Passive Sniffer** — cheap, silent, adjacency-only. The default "look before you leap." Its limits (one hop, types only) are what make Topology Mapper and Traffic Analyzer worth graduating to.

**Topology Mapper** — a **ceiling** on information (1 hop → 2 hops), hence schematic-gated not purchasable (Invariant I2). Two-hop vision fundamentally changes Traversal-class planning (`05`), which is why it's a found capability rather than a shop item.

**Traffic Analyzer** — separates live/defended targets from dormant ones. Reputation-gated because knowing which nodes are *worth* hitting (and which will fight back) is economy-distorting if free. Directly supports proof-of-skill (you must hit *live/defended* targets for credit, `02` §2.4) — this tool tells you which those are.

**Ping Sweep** — the offline-raid enabler: finds other players' exposed High-Hackable stashes (`01` §6). The **high noise + target notification** is the deliberate balance: raiding is not stealthy reconnaissance, the victim knows *someone is casing them*. This is a PvP instigation tool and should feel like one.

**Honeypot Detector** — flags Eye traps, but **must have a false-negative rate** (established): a perfect detector removes the fear the traps exist to create. Target **75–85% detection** — high enough to be worth the schematic and compute, low enough that you can never fully trust a "clear" reading.

> **[PROPOSAL]** Pin the false-negative behavior: on a scanned trap, ~80% chance it's flagged, ~20% it reads clean. A *clean* reading is therefore never a guarantee; a *flagged* reading is always true (no false positives — a false alarm would train players to ignore it). One-directional error preserves tension without breaking trust in positive results.

**Provenance Tracer** — audits the player's *own* deployed miners for hijacking (Cuckoo Patch victimization) or channel sabotage (`04-mining.md` §5). Unglamorous and essential: it is the **only** counter to being hijacked, and running it costs session time that would otherwise earn — the exact tension that stops large deployment networks from being free money (`03-economy.md` §1.1). Zero noise (you're auditing your own assets), low compute, EC-gated as a replaceable operational tool.

## 3. The recon → action pipeline

The intended play pattern the tool set encodes:

1. **Passive Sniffer / Topology Mapper** → learn the graph shape.
2. **Traffic Analyzer** → learn which nodes are live, defended, worth it.
3. **Honeypot Detector** → learn which are traps (with residual doubt).
4. Commit to a breach (`05`) with the right loadout (`06`), or walk away.
5. Post-op / periodically: **Provenance Tracer** to keep the deployed network honest.

Every step is optional and every step costs compute and/or time — the skill is knowing when the information is worth more than the cycles. A reckless player skips recon and eats honeypots and defended nodes; a paranoid player over-scans and out-costs their own income. The sweet spot is the game.

## 4. Balance note

Recon has **no offensive power** — it never breaches, never takes. Its entire value is reducing variance on the *next* action. That's why most recon is silent (None/Low noise): information-gathering shouldn't itself be the risky part, except where it's inherently intrusive (Ping Sweep pings a live target; that one's loud on purpose).
