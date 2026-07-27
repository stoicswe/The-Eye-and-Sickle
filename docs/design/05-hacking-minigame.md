# 05 — The Core Hacking Minigame

**Status:** **Decided 2026-07-26** for its structure — timing model, class set, trace mechanism and tool mapping. Puzzle *content* within those three classes is still first-pass and free to change. Resolved P-1, P-2 and P-4; **P-3 remains open and is measurable only once the puzzle is playable.**
**Depends on:** `00-vision-and-pillars.md` (Pillar 1), `01-core-resources.md`, `02-unlock-gates.md`
**Depended on by:** everything — this is the game. `04-mining.md` §5 (cracking), `06-intrusion-tools.md`, `10-botnets.md`

> **Why this doc mattered most:** Pillar 1 is "the puzzle *is* the game," yet the source design deliberately scoped the puzzle itself out to focus on the economy around it. Every tool, every gate, every risk system was defined in terms of a puzzle that had no rules. The **economy-facing contract (§2) is unchanged** by the 2026-07-26 decisions — that was the point of writing it separately — and the puzzle content (§3) may still change freely as long as it honours that contract.

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

The puzzle is a small family, so that "solve class X to automate class X" is meaningful and different tools counter different classes. **Three classes** (decided 2026-07-26, down from a proposed five):

| Class | Fiction | Skill tested | Primary counter-tools |
|---|---|---|---|
| **Enumeration** | Map a node's open ports/services before you can act | Reading structure | Port Sweep, Passive Sniffer, **Side-Channel Reader** |
| **Logic** | Reconstruct a lock's rule from probe responses | Deductive reasoning (Mastermind-family) | Fuzzer, **Rainbow Table**, **Credential Harvester** |
| **Traversal** | Route through an internal graph to the data node | Pathfinding under an attention budget | Topology Mapper |

**Why five became three.** §3.1 always carried its own merge rule — *"if two classes reduce to the same optimal input pattern, merge them"* — and applying it honestly closed two:

- **Timing is gone.** Its skill was "sequencing, rhythm, patience," which is an *action* skill with nothing to express in a probe budget (§4). It did not survive the timing decision; it was not cut on taste.
- **Credential folded into Logic.** The proposed table gave Credential's skill as "pattern deduction" and Logic's as "reconstruct a rule from probe responses." Those are the same verb. Keeping both would have shipped exactly the reskin §3.1 warns against.

A given target composes **1–N layers**, each an instance of some class (difficulty tier sets N). Breaching means clearing every layer or bypassing one with the Overflow Kit, which spends nearly the whole attention budget (§4).

> Three is now a floor as well as a target: each class must stay a genuinely different *kind of thinking*. A fourth needs to earn its place against that test, not fill a table.

### 3.2 Why bots can't just win it (satisfying Invariant I10)

Each class has a **verification step that rewards a human read**: the Logic class exposes probe responses a player interprets holistically; the Traversal class hides the true objective node among decoys distinguishable only by cross-referencing recovered logs. A bot can *attempt* layers (slower, and it trips more alarms → more noise), but it plays to a fixed heuristic and stalls on the human-read step, which is exactly where manual play pulls ahead. Bots are throughput with a skill ceiling; players are the skill.

Concretely, the bot solver: (a) runs each layer at a time penalty, (b) uses a fixed strategy that a defended/high-tier node can be built to defeat, (c) generates more noise per layer, (d) cannot use the "intuition" shortcuts a human gets from reading flavor data. See `10-botnets.md` §"Bots assist, never substitute."

### 3.3 Difficulty tiers

`difficultyTier` scales: **layer count**, **class mix** (higher tiers stack harder classes), **time pressure** (trace timer speed, §4), and **error tolerance** (how many wrong probes before an alarm/lockout). Tiers are the same knob used by proof-of-skill gates and salvage guards, so they must be a small, legible integer scale (proposed **1–5**, matching the five heat bands loosely for designer intuition).

---

## 4. Attention: pressure without a clock

**Decided 2026-07-26: a breach is turn-based. There is no wall clock anywhere in it.** Each layer grants an **attention budget** set by `difficultyTier`, and every action the player takes spends from it. Breach the layer before the budget empties, or fail.

Per-action cost is the whole mechanic, and it is what makes the loud-vs-patient trade real (constraint 5 requires noise to scale with *how* the player solves it):

| Action | Attention | Why |
|---|---|---|
| Quiet read / passive observation | 1 | The patient baseline |
| Ordinary probe | 2 | The default move |
| Loud tool (Fuzzer volley, brute attempt) | 6 | Power bought with exposure |
| Overflow Kit bypass | most of the bar | Clears a layer outright (`06`); the cost is the point |
| Side-Channel Reader | 0 | Reads without entering — its entire identity |

**Why turn-based rather than the real-time trace bar this document originally proposed:**

1. **Pillar 1.** "The puzzle *is* the game." A wall clock makes it partly a reflex game, and reflexes are not what the rest of the design rewards.
2. **Invariant I10 becomes measurable.** The bot-versus-human gap is now a **probe count**, not seconds — a number that can be tested deterministically and tuned, instead of one that varies with the player's hardware and reaction time. That is what makes **P-3** answerable at all.
3. **Accessibility.** Timed pressure across a windowed interface was flagged as a risk in §5 and is now simply absent.

**Attention is visible and itemised at all times**, which is where the "comprehensible failure" constraint lives: the player must always be able to see which action cost what. A loss has to read as *"I was too loud"*, never *"the game decided"*.

### 4.1 Failure

- **Budget exhausted** → **failure**, with consequence scaled by target:
  - On a *miner crack* (`04-mining.md`): dead-man switch — buffer flushed to deployer, miner self-destructs, your handle exposed to them. (No heat, per **I9** — it is your own rig.)
  - On an *offensive breach* of an NPC/player node: possible tool loss, heat gain, canary/counter-attack triggers (`09`), and Eye progress toward named-hacker attention.
- **Abort** → no loot, attention already spent is gone, no proof-of-skill credit. The escape hatch when a read goes bad.

⚠ `traceProgress` in §2's contract is unchanged and still meaningful — it is now **attention consumed as a fraction of the budget**, so the persisted record and everything downstream of it keep working. The contract survived the mechanism changing underneath it, which is what §2 was written for.

## 5. Presentation across the deck

A breach can span panels the way a real operator's desk does: the **map window** shows the target graph (Traversal), a **terminal window** hosts the active layer, the **rig monitor** shows compute and attention, and a **recon window** holds the flavour logs the human-read steps depend on.

⚠ **This section used to open "Because the client is multi-window" and cite `../architecture/01`. That is no longer true and the correction matters.** `ui-design-language.md` §0 cancelled the `Stage`-per-tool model; the client is now **one undecorated window containing a window manager it draws itself**. The accessibility risk this section raised — window management under time pressure, and the demand for a single-window fallback — is now answered twice over: there is only ever one OS window, and after §4 there is no time pressure to manage it under.

## 6. Open questions

- **P-1 ✅ RESOLVED 2026-07-26 — three classes**, not five. See §3.1 for why Timing and Credential closed rather than being cut.
- **P-2 ✅ RESOLVED 2026-07-26 — turn-based attention budget.** No wall clock. See §4.
- **P-4 ✅ RESOLVED 2026-07-26 — every tool has a class.** The three orphaned by the merge were repointed rather than dropped: Rainbow Table and Credential Harvester to **Logic** (they reveal part of a rule or skip a deduction step, which is what they always did), and Side-Channel Reader to **Enumeration**, where "read without entering" becomes a zero-attention structure read and is the strongest thing in the class.
- **P-3 (still open, and now answerable):** how much does manual play beat bot play? It is **the** number behind Invariant I10. Previously unmeasurable because it was denominated in seconds; under §4 it is a **difference in probe count on the same layer**, which is deterministic and testable. It still needs the real puzzle to exist. Nothing else in this document is blocked on it.
