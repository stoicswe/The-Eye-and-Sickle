# 10 — Botnets

**Status:** Established (design sessions 1–5, including session-3 resolutions on loss/counter-attack)
**Depends on:** `01-core-resources.md`, `02-unlock-gates.md`, `05-hacking-minigame.md` (bots run the puzzle, worse)
**Depended on by:** `03-economy.md` (bot income variance), `11-rig-infrastructure.md` (Isolated Partition)

Bots are the player's force-multiplier and the game's biggest self-inflicted-risk surface. The governing principle overrides every convenience argument:

> **Bots assist, they never substitute** (Invariant I10). They speed things up, reduce risk, and open options. They do **not** solve the puzzle for the player — the puzzle is the game (Pillar 1).

---

## 1. Rules (established)

- Bots are equipped with tools **from the player's own stash**. Assigning a tool pulls it out of the vault and makes it **mid-risk** (`01` §6) — safety and productivity are mutually exclusive.
- Bots are **slower** than manual play.
- Bots are live **only while the player is online** (Invariant I5) — a botnet is not an idle-game overnight farm.
- All bot noise **pools back into the player's aggregate noise** (`01` §3.1). More bots, louder you.
- If a bot is targeted, the player is **notified and may defend or counter-attack** on the bot's behalf.
- **More active bots shortens the timer for responding to each item in the defense backlog.** The penalty scales with bot count directly, so skilled multitaskers can't fully escape it by getting faster.

### 1a. Failed defense — resolution (session-3 decision)

- **Failure means total loss.** An undefended bot is destroyed outright along with **every tool assigned to it**. No degraded-but-surviving state. Overextending on bot count is a hard risk — networks can be wiped in a bad session.
- **Low chance of salvage:** a destroyed bot has a small probability of yielding **generic schematic contribution material** (partial-progress toward schematic unlocks, `02` §2.2).
- **Exploit guard (Invariant I13):** the material drop is gated on **engagement tier** — the bot must have been lost against a defended target above a difficulty threshold. Without this, the optimal play is to build the cheapest junk bot and feed it to a loss, turning bot sacrifice into a grind path toward ceiling raises — the exact failure the gate rule (`02`) exists to prevent. Tier-gating the drop is consistent with proof-of-skill handling; it reuses the same `resolutionRecord.difficultyTier` from `05` §2.

### 1b. Split attention (session-3 decision)

- A bot alert **does not interrupt or queue.** It runs **in parallel** with whatever the player is currently doing, under a **split-attention penalty applied to both engagements**.
- This **stacks intentionally** with the shrinking backlog timer: more bots → less time per item **and** degraded performance on every simultaneous action. Skilled players are still rewarded for multitasking, but the system pushes back as bot count grows rather than capping out at player skill.
- **Playtest watch:** two penalties on the same axis (bot count) could tip bots from "tense" into "not worth running." If that happens, **soften split attention first** — the timer is doing the load-bearing work. (Tracked as a tuning note, not an open question, because the resolution order is already decided.)

## 2. Frames — blueprints, not objects (the critical distinction)

> **A frame is a blueprint.** The schematic/reputation gate unlocks the *ability to build* that frame type permanently. Each running instance is assembled at an EC cost.

This matters because of §1a: total loss destroys the **instance and its socketed tools**, never the blueprint (Invariant I11). Bot loss is therefore an **EC-and-gear** cost — correctly EC-gated since EC covers replaceables — rather than permanent forfeiture of a ceiling-tier asset. Without this, losing a Breacher would mean losing a late-game *schematic*, and running one would be irrational. Socketed tools stay EC-purchasable, preserving the loss-and-replace loop (`03` §4).

| Frame | Function | Blueprint gate | Instance cost | Compute |
|---|---|---|---|---|
| **Recon** | Runs ping sweeps and mapping | Schematic | 25 EC | 8 |
| **Miner** | Deploys and maintains miners | Schematic | 35 EC | 10 |
| **Sentinel** | Guards stash tiers, responds to raids before the player does | Reputation | 50 EC | 14 |
| **Breacher** | Runs actual intrusions — slowest, loudest, most valuable | Schematic (late) | 90 EC | 22 |
| **Mimic** | Generates decoy noise attributed to the player elsewhere on the graph | Reputation | 45 EC | 12 |
| **Scavenger** | Recovers partial tool fragments from failed hacks | Schematic | 30 EC | 9 |

### Per-frame notes

**Recon** — automates the `07` pipeline. Cheapest, lightest; the safe first bot. Still pools noise, still mid-risks its socketed recon tools.

**Miner** — deploys and maintains deployed miners (`04` §2). The engine of a large network — and thus the thing that makes Provenance Tracer maintenance (`07`) heavier, since more miners = more to audit.

**Sentinel** — reputation-gated defensive bot that responds to raids *before the player does*, partially offsetting the backlog timer (`09` Tarpit synergy). The counter to the split-attention spiral: a Sentinel is another responder, though it too is slower than you.

**Breacher** — the big one: runs actual intrusions. **Slowest, loudest, most valuable, most compute (22).** Explicitly does not auto-win the puzzle (Invariant I10) — it plays the `05` minigame on a fixed heuristic with a time penalty and can be defeated by defended/high-tier nodes. Late schematic gate. A running Breacher is a major compute commitment and a major noise source.

**Mimic** — reputation-gated stealth bot: generates decoy noise attributed to you *elsewhere* on the graph, muddying attribution (pairs with Identity Spoofer, `08`). A defensive-deception asset that spends compute to buy misdirection.

**Scavenger** — recovers partial tool fragments from failed hacks, softening the loss loop. Schematic-gated. Note the interaction with §1a salvage: Scavenger recovers *tool* fragments; the tier-gated *schematic* material is separate — keep the two salvage streams distinct in implementation.

## 3. The compute reality of running a botnet

Frames reserve compute permanently while running (like defenses, `09` §3). A three-bot loadout — Recon (8) + Miner (10) + Breacher (22) = **40 cycles** — plus the Breacher's socketed tools and the Miner's control-channel reservations (`04` §2, 3/miner) can consume most of a starting rig before a single defensive tool is armed. This is the self-correcting cap on botnet size, mirroring the deployed-miner cap (`04` §2.2): **no hard bot limit is needed, and none should be added.** The rig ceiling is the limit.

The **Isolated Partition** rig upgrade (`11`) lets *one* bot run without contributing to the noise pool — extremely expensive, hard cap of one or two — the single exception that proves the pooling rule.

## 4. Design summary: why botnets are risk, not just power

Every bot simultaneously: (a) reserves scarce compute, (b) pools its noise into your heat exposure, (c) mid-risks the tools socketed into it, (d) shortens your defense-response timer, (e) applies a split-attention penalty to everything you do. Five distinct costs against one benefit (throughput). That five-to-one pressure is why a botnet reads as *overextension you chose*, and why the design never needs a hard cap to stop players from running fifty of them.
