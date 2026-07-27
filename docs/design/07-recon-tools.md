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

---

## 5. The network sweep ladder — [PROPOSAL]

The sweep is the verb that fills the map. It is not in §1's table because it is not one of the six named tools: it is the *baseline* discovery action every one of those tools is bought to refine, and it has three sensitivities rather than being a single purchase.

| Tier | Item id | Gate | Cost | Compute | Duration | Noise |
|---|---|---|---|---|---|---|
| **Base sweep** | `net-sweep` | **Starting kit** | — | 2 | ~20 s | **High (35)** |
| **Wide sweep** | `net-sweep-wide` | Ethecoin | 25 EC | 5 | ~45 s | **High (55)** |
| **Deep sweep** | `net-sweep-deep` | Ethecoin | 55 EC | 9 | ~90 s | **Very high (80)** |

### 5.1 Running a sweep never costs ethecoin

The **tool** is bought once; **running** it spends cycles and exposure and nothing else. This is not a concession, it is forced by two things at once. Ethecoin never buys a ceiling (**I2**), and discovery is upstream of every ethecoin faucet in the game — a per-run charge would mean a player short of money could not find the machines that are how money is earned, which is a spiral with no floor. The base tier is starting kit for the same reason Port Sweep is (`06` §2): without it a new player cannot find what is next to them, and the whole network half of the game is unreachable.

What the two purchasable tiers buy is **sensitivity within reach the player already has** — how quiet a machine can be and still be heard. Reach itself is the Topology Mapper's, schematic-gated, and no amount of ethecoin moves it (§2).

### 5.2 A sweep is cheap and loud, and those are two different numbers

The compute column and the noise column are **not derived from each other**, and the split is deliberate.

- **Compute** is how much of the player's own rig the job occupies. A sweep occupies almost none of it.
- **Noise** is how much racket reaches machines that are not the player's. A sweep is nothing but that — it puts packets on hosts it has no business touching, which is precisely what `08` §1 means by "noise is generated by **acting**".

Deriving one from the other was the first implementation and it was wrong on screen: noise renders as outward cycles over rig capacity, so a two-cycle sweep moved a 100-cycle rig's meter by two percent — indistinguishable from silence — and got *quieter* as the player's rig grew, which inverts what the instrument is for. Ping Sweep is the precedent the table already had: **High** noise for a tool whose compute cost is 8.

### 5.3 Loud while it runs, silent the moment it ends

A sweep contributes its full noise for its whole duration and **exactly nothing afterwards**. There is no decay curve and no trailing figure: the moment the countdown reaches zero the meter drops back to whatever the rig was already doing.

That is the general rule rather than a property of sweeps. **Noise is a rate, not a debt.** What a loud act leaves behind is **heat** (`01` §4), which is persisted, decays on its own schedule, and is what the Eye actually acts on. Collapsing the two would give the player a number that never came down and no way to read the difference between *"I am being loud"* and *"I have been loud"* — and the stealth kit in `08` answers those two with different tools at different prices.

### 5.4 Filing what has been found — [PROPOSAL]

A discovered machine can be filed into a **folder**, and folders nest (5 levels). This is a bookmark and nothing more:

- It costs no compute, no ethecoin and no time, there is no limit on how many folders exist, and nothing in the game is gated on having them. There is therefore no gate to classify under `02` §1.1 — a quantity a gate could attach to is exactly what this does not have.
- Filing a machine changes **nothing mechanical**: not its tier, not its defences, not what a sweep costs, not what a breach faces.
- ⚠ **Only a discovered address can be filed, and the refusal for an undiscovered address is word-for-word the refusal for one that does not exist.** Two distinguishable refusals would let a player enumerate the world one guess at a time, for free — which is the entire product this ladder is sold on.

The model is a filesystem, not a tag set: one parent per folder, one folder per machine, and the verbs are `mkdir`, `rmdir` and `mvdir`. Moving a *machine* is `file`, deliberately not `mv` — real `mv(1)` moves anything, and a verb here that meant only "reparent a folder" would teach something false about the command it borrowed its name from. Removing a folder is **never recursive**: its contents move up one level. Filing carries no risk lesson, so there is nothing to be gained by making a mis-click expensive.
