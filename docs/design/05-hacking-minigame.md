# 05 — The Core Hacking Minigame

**Status:** ⚠️ **[PROPOSAL]** — this entire document is a first-pass design for an area the consolidated design doc references constantly but never specifies. Nothing here is established. It is written to be internally consistent with every established rule and to give Claude Code something concrete to build against, but the user should treat it as a starting point to keep, edit, or replace.
**Depends on:** `00-vision-and-pillars.md` (Pillar 1), `01-core-resources.md`, `02-unlock-gates.md`
**Depended on by:** everything — this is the game. `04-mining.md` §5 (cracking), `06-intrusion-tools.md`, `10-botnets.md`

> **Why this doc is speculative but necessary:** Pillar 1 is "the puzzle *is* the game," yet the source design deliberately scoped the puzzle itself out to focus on the economy around it. Every tool, every gate, every risk system is defined in terms of a puzzle that doesn't have rules yet. This proposal fills that hole so the rest of the design is buildable. Design the economy-facing *contract* (§2) first and firmly; the puzzle *content* (§3) can change freely as long as it honors the contract.

---

## 1. Design constraints the minigame must satisfy

These are non-negotiable because established systems depend on them:

1. **It must be the thing bots can't do** (Invariant I10). Bots run it slower and worse; they never auto-win it. So the puzzle must have a skill component a script can't trivially execute.
2. **It must have classes and difficulty tiers** (`02-unlock-gates.md` §2.4, Invariant I7/I13). Proof-of-skill unlocks and bot-salvage guards both key off "solved class X at tier ≥ T against a live/defended target."
3. **It must run against a miner** as a self-contained instance (`04-mining.md` §5.1) — the cracking case proves the puzzle can be instanced against a single target with a yield buffer as the prize.
4. **It must have a comprehensible failure state.** Cracking's tutorial role depends on the player understanding *why* they lost.
5. **It must generate noise as a first-class output**, scaled by how the player solves it (loud tools vs. patient ones).
6. **It must support "layers" that can be bypassed.** The Overflow Kit "bypasses a puzzle layer entirely" (`06`), so the puzzle is explicitly multi-layer.
7. **It must be tunable via difficulty** so a single system scales from tutorial miner-cracks to late-game Eye infrastructure.

---

## 2. The economy-facing contract (design this to last)

Regardless of what the puzzle *is*, it exposes this interface to the rest of the game. Treat this as the stable API; §3 is one implementation of it.

A **breach attempt** is instantiated with:

- `target` — a node with a defense profile (firewall tier, tarpit, honeypot flag, canary tokens, ...) drawn from `09-defense-and-hardening.md`.
- `puzzleClass` ∈ { see §3.1 } — which *kind* of puzzle this node presents.
- `difficultyTier` — integer scaling knob; sets layer count, time pressure, and error tolerance.
- `liveOrDormant` — whether the target is defended/active (required for proof-of-skill credit).
- `equippedTools` — the player's loadout, each of which modifies the attempt (skips a layer, reveals info, reduces noise, ...).

It produces:

- `outcome` ∈ { breached, failed, aborted }.
- `noiseGenerated` — scalar, function of tools used + time spent + alarms tripped.
- `traceProgress` — how close the defender/Eye came to attribution (see §4).
- `loot` on success / `consequence` on failure (tool loss, handle exposure, counter-attack).
- `resolutionRecord` — `{puzzleClass, difficultyTier, liveOrDormant, outcome}`, persisted, feeding proof-of-skill (§`02`) and bot-salvage guards (§`10`).

Everything the economy needs is in that record. Build it early even if the puzzle content is still churning.

---

## 3. Proposed puzzle content

### 3.1 Puzzle classes

The puzzle is not one minigame but a small family, so that "solve class X to automate class X" is meaningful and so different tools counter different classes. First-pass set of **five classes**:

| Class | Fiction | Skill tested | Primary counter-tool |
|---|---|---|---|
| **Enumeration** | Map a node's open ports/services before you can act | Reading structure under time pressure | Port Sweep / Passive Sniffer |
| **Credential** | Defeat an auth layer | Pattern deduction; reuse detection | Rainbow Table (weak creds), Credential Harvester (pivot) |
| **Logic** | Reconstruct a lock's rule from probe responses | Deductive reasoning (Mastermind-family) | Fuzzer (brute malformed input) |
| **Timing** | Exploit a race/timing window | Sequencing, rhythm, patience | Side-Channel Reader (read without entering) |
| **Traversal** | Route through an internal graph to the data node | Pathfinding under a noise budget | Topology Mapper (see 2 hops) |

A given target composes **1–N layers**, each an instance of some class (difficulty tier sets N). Breaching means clearing every layer or bypassing it (Overflow Kit) — a bypass clears one layer at the cost of *very high* noise.

> Design intent: classes should feel like *different kinds of thinking*, not reskins. If two classes reduce to the same optimal input pattern, merge them — five is a target, not a mandate.

### 3.2 Why bots can't just win it (satisfying Invariant I10)

Each class has a **verification step that rewards a human read**: the Logic class exposes probe responses a player interprets holistically; the Traversal class hides the true objective node among decoys distinguishable only by cross-referencing recovered logs. A bot can *attempt* layers (slower, and it trips more alarms → more noise), but it plays to a fixed heuristic and stalls on the human-read step, which is exactly where manual play pulls ahead. Bots are throughput with a skill ceiling; players are the skill.

Concretely, the bot solver: (a) runs each layer at a time penalty, (b) uses a fixed strategy that a defended/high-tier node can be built to defeat, (c) generates more noise per layer, (d) cannot use the "intuition" shortcuts a human gets from reading flavor data. See `10-botnets.md` §"Bots assist, never substitute."

### 3.3 Difficulty tiers

`difficultyTier` scales: **layer count**, **class mix** (higher tiers stack harder classes), **time pressure** (trace timer speed, §4), and **error tolerance** (how many wrong probes before an alarm/lockout). Tiers are the same knob used by proof-of-skill gates and salvage guards, so they must be a small, legible integer scale (proposed **1–5**, matching the five heat bands loosely for designer intuition).

---

## 4. Trace, noise, and the failure state

While a breach is in progress, a **trace** accrues on the defender side — the in-fiction "someone is noticing." Trace speed rises with the target's tier and the noise the player is generating, and falls with stealth tooling (Traffic Shaper caps the spike; Relay Chain slows attribution; Log Scrubber can retroactively cut noise mid-op).

- **Breach before trace completes** → success: loot + `resolutionRecord(outcome=breached)`.
- **Trace completes first** → **failure**, with consequence scaled by target:
  - On a *miner crack* (`04-mining.md`): dead-man switch — buffer flushed to deployer, miner self-destructs, your handle exposed to them. (No heat, per I9 — it's your own rig.)
  - On an *offensive breach* of an NPC/player node: possible tool loss (a consumable or the tool that failed), heat gain, canary/counter-attack triggers (`09`), and Eye trace progress toward named-hacker attention.
- **Abort** → no loot, partial noise already spent, no proof-of-skill credit. The escape hatch when a read goes bad.

This is where the "comprehensible failure" constraint lives: the trace bar must be visible and its inputs legible (which action added how much), so a loss reads as "I was too loud / too slow," never "the game decided."

---

## 5. Multi-window presentation (client-specific)

Because the client is multi-window (`../architecture/01-tech-stack.md`), a breach can span windows the way a real operator's desk does: the **map window** shows the target graph (Traversal), a **terminal window** hosts the active layer, the **rig monitor** shows compute/trace in real time, and a **recon window** holds the flavor logs the human-read steps depend on. This is a genuine design opportunity — the "spread across windows" fantasy is strongest during a live breach — but it is also an accessibility risk (window management under time pressure). Flag for `design:accessibility-review` before committing: single-window fallback layout must exist.

---

## 6. Open questions this proposal raises (add to `15-open-questions.md` if adopted)

- **P-1:** Is a 5-class family the right breadth, or does it dilute mastery? Could ship with 2–3 and expand.
- **P-2:** Real-time trace timer vs. turn/probe-budget (no wall-clock). Turn-based is more accessible and more "puzzle," less "action" — arguably truer to Pillar 1. Strong candidate to switch.
- **P-3:** How much does manual play actually beat bot play, in seconds? Needs the real puzzle to measure; it's the number that makes or breaks Invariant I10.
- **P-4:** Do the five classes map cleanly onto distinct tools, or do some tools end up class-less? Reconcile with `06`/`07` once classes settle.
