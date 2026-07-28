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

The puzzle is a small family, so that "solve class X to automate class X" is meaningful and different tools counter different classes. **Two classes** (decided 2026-07-27, down from three, which was down from a proposed five):

| Class | Fiction | Skill tested | Pressure |
|---|---|---|---|
| **Breach Protocol** | Route a code sequence out of a memory matrix into a short upload buffer | Spatial planning under a hard budget | The buffer. A handful of picks and it is over either way |
| **Offset Cipher** | Two readings of the same key differ; write the signed offset under every byte | Arithmetic precision, unhurried | None from a clock. It is priced in **noise** instead |

**How they are played.**

- **Breach Protocol** — a square grid of two-character codes, and one to three target sequences. Picks alternate: the first is taken from a row, which confines the next to that pick's *column*, which confines the next to *its* row, and so on. Every pick appends to a buffer that cannot be emptied and cannot be un-taken, and a sequence counts when it appears as a contiguous run anywhere in the buffer. Landing two sequences in a buffer that barely fits one and a half means finding runs that **overlap**. Everything is visible from the first frame: there is nothing to probe for and nothing to buy, and the whole difficulty is in seeing the path.
- **Offset Cipher** — a row of 6–16 observed bytes and, under it, the target row those bytes must become. The player writes a signed offset under each byte such that `observed + offset = target`, then commits the row. A commit says **how many** cells were wrong and **which ones**; it never says what the right value was. Nothing wraps, so every byte has exactly one answer and a player who did the arithmetic correctly can never be told they were wrong. `CARRY` solves one byte for attention, loudly — the one escape hatch, priced so that carrying the whole row costs more than the layer is worth.

**Why three became two.** §3.1 has always carried its own merge rule — *"if two classes reduce to the same optimal input pattern, merge them"* — and the three that survived the last cut still failed it. Enumeration ("read the structure") and Traversal ("route through the structure") were one skill wearing two costumes, and Logic's Mastermind deduction was in practice a search the player performed by guessing rather than by reasoning. What replaced them is a genuine axis: **pressure of place** against **pressure of precision**. One is bounded and spatial, the other unbounded and arithmetical, and being good at one predicts nothing about the other — which is what a proof-of-skill gate (**I7**) has to be able to claim.

**Why the cipher has no clock but is louder.** A timer would put the arithmetic back under reflex pressure, which pillar 1 rules out (see §4). But something has to answer *"why not take all day"*, and the honest answer is that all day is spent **on somebody else's wire**: the cipher's noise is multiplied (`Balance.BREACH_CIPHER_NOISE_FACTOR`, currently ×1.8) against a grid that did the same things. Patience costs exposure rather than time.

⚠ **The class is drawn once per attempt and frozen at commission**, and every layer of that attempt plays it. A target that opened with a grid and followed with a cipher would be two short games rather than one, and would make the deeper layers of a hard target a lottery between the thing the player is good at and the thing they are not. One roll per attempt means a player who draws the puzzle they are worse at knows it before they spend anything, and can walk away.

A given target composes **1–N layers**, each an instance of the drawn class (difficulty tier sets N). Breaching means clearing every layer or bypassing one with the Overflow Kit, which spends nearly the whole attention budget (§4).

> Two is now a floor as well as a target: each class must stay a genuinely different *kind of thinking*. A third needs to earn its place against that test, not fill a table.

### 3.2 Why bots can't just win it (satisfying Invariant I10)

Each class has a step where a fixed heuristic loses ground to a person looking at the whole board. **Breach Protocol** rewards seeing that two sequences share a run and can be landed with one path instead of two — a greedy solver takes the nearest match and burns the buffer. **Offset Cipher** rewards a player who reads a whole row before committing, because a commit that is wrong in one cell costs the same as one wrong in twelve, and the only information it returns is *which*. A bot can *attempt* layers (slower, and it trips more alarms → more noise), but it plays to a fixed heuristic and stalls on that read, which is exactly where manual play pulls ahead. Bots are throughput with a skill ceiling; players are the skill.

Concretely, the bot solver: (a) runs each layer at a time penalty, (b) uses a fixed strategy that a defended/high-tier node can be built to defeat, (c) generates more noise per layer, (d) cannot use the "intuition" shortcuts a human gets from reading flavor data. See `10-botnets.md` §"Bots assist, never substitute."

### 3.3 Difficulty tiers

`difficultyTier` scales: **layer count**, **board size** (a 5×5 grid at tier 1 against a 7×7 with three sequences at tier 5; six cipher bytes against sixteen), and **error tolerance** (how many wrong moves before an alarm/lockout). Tiers are the same knob used by proof-of-skill gates and salvage guards, so they must be a small, legible integer scale (proposed **1–5**, matching the five heat bands loosely for designer intuition).

---

## 4. Attention: pressure without a clock

**Decided 2026-07-26: a breach is turn-based. There is no wall clock anywhere in it.** Each layer grants an **attention budget** set by `difficultyTier`, and every action the player takes spends from it. Breach the layer before the budget empties, or fail.

Per-action cost is the whole mechanic, and it is what makes the loud-vs-patient trade real (constraint 5 requires noise to scale with *how* the player solves it):

| Action | Attention | Why |
|---|---|---|
| Quiet read / passive observation | 1 | The patient baseline |
| Ordinary probe | 2 | The default move |
| Loud tool (`CARRY`, brute attempt) | 6 | Power bought with exposure |
| Overflow Kit bypass | most of the bar | Clears a layer outright (`06`); the cost is the point |
| Composition (typing an offset) | 0 | Arranging your own notes is not a move; it is free and reversible until you commit |

**Why turn-based rather than the real-time trace bar this document originally proposed:**

1. **Pillar 1.** "The puzzle *is* the game." A wall clock makes it partly a reflex game, and reflexes are not what the rest of the design rewards.
2. **Invariant I10 becomes measurable.** The bot-versus-human gap is now a **probe count**, not seconds — a number that can be tested deterministically and tuned, instead of one that varies with the player's hardware and reaction time. That is what makes **P-3** answerable at all.
3. **Accessibility.** Timed pressure across a windowed interface was flagged as a risk in §5 and is now simply absent.

**Attention is visible and itemised at all times**, which is where the "comprehensible failure" constraint lives: the player must always be able to see which action cost what. A loss has to read as *"I was too loud"*, never *"the game decided"*.

### 4.1 Failure

- **Budget exhausted**, **struck out**, or **out of board** (a Breach Protocol buffer that filled with nothing uploaded — there is no legal move left, so the layer locks rather than costing a strike nobody could ever spend) → **failure**, with consequence scaled by target:
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
