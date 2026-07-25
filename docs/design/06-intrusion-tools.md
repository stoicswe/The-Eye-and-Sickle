# 06 — Intrusion Tools

**Status:** Established (tool set + stats from design sessions 1–5); class mappings marked [PROPOSAL]
**Depends on:** `02-unlock-gates.md`, `05-hacking-minigame.md`
**Depended on by:** `10-botnets.md` (socketed into frames), `03-economy.md` (pricing)

Offensive tools used during a breach (`05-hacking-minigame.md`). Every tool carries four economy-facing stats: **gate**, **EC cost** (if EC-gated), **compute**, and **noise**. These are first-class balance levers — a tool's power is always paid for in at least one of compute or noise.

---

## 1. Tool table (established)

| Tool | Function | Gate | Cost | Compute | Noise |
|---|---|---|---|---|---|
| **Port Sweep** | Basic node enumeration | Starting kit | — | 2 | Low |
| **Fuzzer** | Brute-forces malformed input into a node until something breaks | Ethecoin | 25 EC | 6 | Moderate |
| **Rainbow Table** | Instant crack against weak or reused credentials; useless against salted targets | Ethecoin + schematic | 60 EC | 8 | Low |
| **Overflow Kit** | Bypasses a puzzle layer entirely | Proof-of-skill | — | 10 | **Very high** |
| **Credential Harvester** | Steals credentials from a breached node, usable on linked nodes — enables pivoting | Sickle reputation | — | 7 | Moderate |
| **Zero-Day** | One-shot instant breach of any single node, no noise | Rare loot / black market (heat-gated) | 400+ EC | 0 | None |
| **Side-Channel Reader** | Infers node contents via timing and power analysis without entering | Schematic (late) | — | 14 | None |

## 2. Per-tool design notes

**Port Sweep** — the free starting enumerator. Everyone has it; it's the baseline the Enumeration class is tuned against. Deliberately weak so that better recon (`07`) stays worth buying.

**Fuzzer** — the entry-level "I don't know the rule, so I'll hammer it" tool. Moderate noise is the cost of impatience; it's the loud counter to the Logic class. First real EC purchase for most players → priced at the low end of mid-tier (25 EC).

**Rainbow Table** — hard-countered by salting, by design. This is a *conditional* power spike: devastating against lazy targets, useless against prepared ones, so it rewards recon (know before you buy the attempt). EC + schematic split: buy the table, but the capability to wield it is found.

**Overflow Kit** — the definitional proof-of-skill item: it *skips a puzzle layer*, so you must prove you can clear that layer class manually first (`02` §2.4). **Very high noise** is the balancing cost — bypassing the puzzle screams. It's a panic button with a siren attached, never a default.

**Credential Harvester** — the pivoting enabler and the reason breaching one node can cascade into a network. Sickle-reputation-gated because free pivoting would distort the raid economy. Interacts with the Traversal/Credential classes: harvested creds open linked nodes without re-solving auth.

**Zero-Day** — see §3; the most tightly controlled item in the game.

**Side-Channel Reader** — the patient operator's tool: learn a node's contents *without entering*, zero noise, at a steep compute cost (14) and a late schematic gate. Counters the Timing class and enables "case the target, then decide" play. The high compute cost is what stops it from being a free universal scanner.

## 3. Zero-Days — the hard rule

> **Zero-days must never become farmable** (Invariant I8). The moment they're reliably purchasable they become the answer to every problem and the puzzle layer stops mattering.

Enforcement, all three required together:

1. **Consumable** — one-shot, gone on use.
2. **Rare** — only as uncommon loot, or via the **heat-gated black market** (`02` §2.5), which means *being hunted* is the price of access.
3. **Expensive** — 400+ EC when bought, on top of the heat cost of reaching a broker.

Design test for any future "instant win" item: if a player can acquire it on a schedule, it's mis-gated. Zero-days are the ceiling example of "power at the price of exposure," and nothing may undercut them by offering the same effect more cheaply or reliably.

## 4. [PROPOSAL] Puzzle-class mapping

Tying tools to the proposed puzzle classes (`05` §3.1). Speculative — reconcile if/when classes settle (open question P-4):

| Tool | Primarily counters | How |
|---|---|---|
| Port Sweep | Enumeration | Reveals node structure |
| Fuzzer | Logic | Brute-forces the rule instead of deducing it (loudly) |
| Rainbow Table | Credential | Instant vs. weak/reused creds |
| Credential Harvester | Credential / Traversal | Reuses creds to skip auth on linked nodes |
| Side-Channel Reader | Timing | Reads contents without triggering the window |
| Overflow Kit | *any* layer | Universal bypass; the proof-of-skill escape hatch |
| Zero-Day | *entire node* | Skips the whole puzzle; the rarity is the balance |

## 5. Balance levers (for tuning sessions)

- **Compute** gates *simultaneity* — how many tools you can field at once against your rig ceiling.
- **Noise** gates *stealth* — loud tools accelerate the trace (`05` §4) and feed heat.
- **Gate** controls *when in progression* a tool appears.
- **EC cost** controls *how replaceable* it is (and which sink it feeds, `03`).

When a tool feels overpowered, raise the stat that matches its abuse: too-spammable → compute; too-safe → noise; too-early → re-gate. Avoid nerfing raw function; the function is the fantasy.
