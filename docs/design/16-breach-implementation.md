# 16 — The Breach, As Built

**Status:** Implementation record for `05-hacking-minigame.md` (Decided 2026-07-26). The *rules* here are **[PROPOSAL]** unless marked otherwise; the *structure* implements decisions `05` already made.
**Depends on:** `05-hacking-minigame.md` (§2, §3, §4), `02-unlock-gates.md` §2.2/§2.4, `04-mining.md` §3.2/§3.2a/§5.1, `06-intrusion-tools.md`, `07-recon-tools.md`, `09-defense-and-hardening.md`, `10-botnets.md` §1a
**Implemented in:** `solo/src/main/java/.../solo/breach/`, `solo/.../rules/ScanRules.java`, `solo/.../rules/SalvageRules.java`, `solo/Balance.java`

> **What this document is for.** `05` decided the breach's *shape* — turn-based attention, a stable resolution record — and deliberately left the content open. This is the content, written down so the next person tuning it can see what every number is anchored to.
>
> ⚠ **Rewritten 2026-07-27.** The three classes this document was first written against (Enumeration, Logic, Traversal) were replaced wholesale by two: **Breach Protocol** and **Offset Cipher**. Everything below describes what is actually built. The retired classes are recoverable from git history and are not summarised here — a design document that carries a shadow of what it used to say is a document nobody can trust to be current.

---

## 1. What was built

An attempt is a persisted document, not a session. It has no clock, no deadline and no settlement path — `05` §4 removed the wall clock outright, so a breach survives a quit for free and reloading puts the player back on the same turn. The only thing it holds over time is **compute**: one `ACTIVE_TOOL` reservation, held for the whole attempt and released onto the Thermal Budget curve at resolution, exactly the hold-then-recover shape UI-6 gave a scan (`04` §3.2).

| Piece | Where |
|---|---|
| Engine — begin, act, abort, resolve, dismiss | `solo/breach/BreachRules.java` |
| Board generation, both classes | `solo/breach/BoardFactory.java` |
| Per-class move resolution | `solo/breach/{Matrix,Offset}Rules.java` |
| The view the client renders | `solo/breach/BreachSnapshots.java` |
| Target list, loadout, tutorial plant | `solo/breach/Targets.java` |
| Seeded, persisted PRNG | `solo/breach/Rng.java` |
| Scan false positives, Detection Array precision | `solo/rules/ScanRules.java` |
| Schematic material | `solo/rules/SalvageRules.java` |

---

## 2. The PRNG, and why it is persisted

`solo` had no randomness before this. It is described in its own module charter as "a pure function of `(save, clock)`", which is what lets `SoloGameTest` assert exact ethecoin figures and what makes a bug reproducible from a save file.

A breach needs generation and a scan needs a roll, so `SoloSave.rngSeed` is a single `long` advanced by **splitmix64** and **written back to the save on every draw**.

> ⚠ **Save scumming is the failure this exists to prevent.** A draw that is not committed is a draw the player can reroll by quitting without saving. A player who did not like their board would reload until they got one they did, and a scan that said the wrong thing would be re-run until it said the right thing — which guts `04` §3.2a's "a scan hit is a lead to corroborate, not an answer". Every rule that draws calls `Rng.commit(save)` before returning. Boards are additionally generated **once, at `begin`, and persisted**, so a mid-breach reload replays nothing at all.

`nextInt` uses Lemire's multiply-shift **without** the rejection loop, deliberately: rejection consumes a variable number of draws, which makes the stream shape depend on the values it produced, which makes a replay from a stored seed depend on the code path as well as the seed. The residual bias is under 2e-17 at any bound this game uses.

---

## 3. BREACH PROTOCOL — the grid, and why every goal is reachable

A square grid of two-character codes drawn from six values (`1C 55 7A BD E9 FF`), one to three target sequences, and a buffer of 4–8 slots. Picks alternate: the first is taken from row 0, and each pick then confines the next to the row or column of the cell just taken. A sequence counts when it appears as a **contiguous run anywhere in the buffer**.

**Six codes, deliberately.** Fewer makes accidental runs so common that a sequence completes itself; more makes a run vanishingly unlikely along any legal path, and the puzzle stops being solvable by planning. No two share a first character, so they are distinguishable at a glance.

> ⚠ **Goals are cut out of a walk the generator actually took, never generated at random.** A random sequence is very often unreachable — the path alternates row and column, so a run that appears along no legal walk is a goal the player can see, read, and never land, *with nothing on screen to distinguish that from being bad at the game*. `BoardFactory.legalWalk` walks the grid first and slices the goal out of the walk, which guarantees at least one solution exists. `BreachBoardsTest.everyBoardIsSolvable` searches for that path on every board the generator will make, at every tier, across 120 seeds. **This is the property an open-information puzzle lives on** — it has nothing hidden, so if it is unfair there is no way for the player to find out.

**Scoring rescans the whole buffer; it never advances a pointer.** A run can restart mid-buffer: with a goal of `1C 1C 55`, a buffer of `1C 1C 1C 55` contains it, and a pointer that advanced on the first two and reset on the third would miss it. Rescanning is a handful of comparisons on a buffer of at most eight and cannot get that wrong.

**Published progress is about the buffer's *tail*, not its best match anywhere.** What the player needs to know is whether the next pick continues a run; a figure counting a partial match they have already walked away from would point them at a sequence that can no longer be completed from here.

**An illegal pick is refunded, not a strike.** The client cannot produce one — every cell it offers is selectable — so an illegal pick is a mis-typed terminal command, and charging attention for a typo would make the terminal worse to play than the window. A *wasted* pick is not a strike either: it already costs a buffer slot and moves the cursor somewhere the player did not choose, and charging on top would punish one mistake twice and make an exploratory pick (sometimes the only legal move) read as an error.

> ⚠ **A full buffer with nothing uploaded LOCKS the layer, it does not strike.** Every pick from there is refused for want of a slot, so a strike would leave the player in front of a board they cannot touch with a counter that will never reach its limit. `Move.locked` exists for exactly this and for nothing else.

**What this class must never publish:** a reachable-goal count, a "best next cell", or a marker on the cell a solver would take. Working out where the path goes is the entire game. Note the contrast with the retired classes — this one has no secret to keep at all, so the discipline is about what the *interface* adds rather than about what the snapshot withholds.

---

## 4. OFFSET CIPHER — arithmetic, and the one answer rule

A row of 6–16 observed bytes, a target row under it, and a signed offset to write under each: `observed + offset = target`. Both rows are public from the first frame. `COMMIT` reports how many cells were wrong and **which ones**, never what they should have been.

> ⚠ **Nothing wraps, and that is a rule rather than an implementation detail.** With wrapping there would be two answers per byte — the short way and the long way round — and a player who did the arithmetic correctly could still be told they were wrong, which is the one thing an arithmetic puzzle may never do. `expected(i) = target[i] - observed[i]`, in `-255..+255`, and `OffsetRulesTest`/`BreachBoardsTest.answerIsUnique` assert it on every generated board.

**The target is the observed byte plus a non-zero step, not a second free draw.** Two independent draws collide about once every 256 cells, and a column whose answer is zero is a column the player skips on sight — a board of them looks sixteen bytes long and is four. The step is `1..255` and wraps into the byte, so every column is real work.

**Typing is composition: free, reversible, unledgered, and it lives in the engine.** `05` §3.7 makes arranging your own notes not-a-move. The draft is held in the save rather than in the client so a reload cannot lose a half-written row — a local buffer in the widget would be a second copy of the answer that a reload could disagree with. An all-blank commit is refunded rather than striked: it is a mis-click, not a wrong answer.

**There is no probe, and the reason is the opposite of the grid's.** The grid has nothing to ask because everything is visible; the cipher has nothing to ask because the answer is arithmetic. A "check one cell" action would be a probe in all but name and would turn a test of care into a test of budget — the player would simply buy the answer one cell at a time. `CARRY` is the single escape hatch and is priced so that carrying the whole row costs more than the layer is worth.

**No clock, higher noise.** `05` §4 removed the wall clock and this class is where that would otherwise be free: the player may sit and subtract for as long as they like. `Balance.breachNoisePoints` multiplies the attempt's noise by `BREACH_CIPHER_NOISE_FACTOR` (×1.8) so that patience costs exposure instead of time.

> ⚠ **The multiplier is applied to the attempt's TOTAL, not per action.** Per action it would make the cipher *quieter* overall, because it has far fewer paid moves than a grid does — three commits against eight picks. One number, applied once, feeding the meter, the heat gain and the counter-hack roll, so none of the three can disagree.

---

## 5. Why five became three became two

`05` §3.1 has always carried its own merge rule: *if two classes reduce to the same optimal input pattern, merge them.* Applying it honestly twice got here.

| Retired | Closed because |
|---|---|
| Timing | Its skill was sequencing and rhythm — an *action* skill with nothing to express in a probe budget once `05` §4 removed the clock |
| Credential | Its skill was "pattern deduction"; Logic's was "reconstruct a rule from probe responses". The same verb, and exactly the reskin §3.1 warns against |
| Enumeration | "Read the structure" and Traversal's "route through the structure" were one skill in two costumes |
| Traversal | As above. Its decoy read survives in spirit as the grid's overlapping-sequence read |
| Logic | Mastermind deduction was, in practice, a search the player performed by guessing. What replaced it asks for the same care with a closed-form answer and no guessing branch |

What is left is a genuine axis — **pressure of place** against **pressure of precision** — and being good at one predicts nothing about the other, which is what a proof-of-skill gate (**I7**) has to be able to claim.

⚠ **The class is drawn once per attempt and every layer plays it.** Frozen at commission, so a reload cannot reroll into the easier one. A mixed attempt would be two short games and would make a hard target's deeper layers a lottery between the puzzle the player is good at and the one they are not.

### 5.1 Which one you draw is earned — recon weights the roll (2026-07-29)

The draw was an even coin flip. It is now weighted by **how much of the target's port-scan report is filled in**, and the split reads out of the fiction rather than out of a constant:

- **Offset Cipher is the default.** It is the puzzle that needs nothing from the far side — deriving an offset from ciphertext is exactly what you do when you have no other handle on a machine. Against a target nobody has scanned it is what you get, every time (`Balance.BREACH_PROTOCOL_SHARE` = 0).
- **Breach Protocol is the puzzle of someone who knows the host.** Its grid *is* that machine's protocol surface, so it takes knowing the machine to be looking at one. A complete report draws it about **95%** of the time (`BREACH_PROTOCOL_SHARE_INFORMED`).
- **Linear in between** (`Balance.breachProtocolShare`), one seventh per finding, so there is no threshold to discover — a player who scans one more thing sees the odds move, which is what makes the relationship learnable.

**This is RECON's first mechanical consequence.** A report used to be intelligence the player read and acted on by hand; it now changes what the breach *is*. `07`'s tools finally feed `05`'s puzzle rather than sitting beside it.

> ⚠ **It buys a DIFFERENT puzzle, never an easier one.** Tier, attention budget, strike limit, layer count and cycle cost are all identical either way, and `BreachPuzzleWeightingTest.Pricing` asserts it directly. The intended reading is that recon lets a player steer toward the puzzle they are better at — not lower the bar. **If the two ever stop being comparable in difficulty this silently becomes a discount**, and a proof-of-skill gate that can be bought down is not proof of skill (**I7**). That is the thing to re-check whenever either puzzle is re-tuned.

> ⚠ **Not 1.0 at full knowledge, deliberately.** A machine that can still surprise a well-prepared operator once in twenty is the fiction working, and a guaranteed puzzle means the cipher stops being practised by anyone who scans — which would put us back where §5 started, with a player drilling half the skill the gate claims to certify. The class is announced before anything is spent, so the residual is a surprise the player can walk away from.

> ⚠ **Staleness deliberately does not count against the report.** A finding learned a week ago still counts as known. The report already shows each finding's age, so a player can see what has gone cold; making an old finding silently stop counting would move the odds under them with nothing on screen changing.

> ⚠ **The roll is taken unconditionally, even at a weight of zero.** Skipping it for an unscanned machine is the obvious optimisation and would break replay — every later draw in the breach would then depend on whether the player had scanned the target, so the same seed would generate different boards. §2's rule, applied to a new input.

---

## 6. The numbers, and what each is anchored to

Every figure lives in `solo/Balance.java` with its citation. Summary of what is **decided** versus what this document invented:

**Decided elsewhere, used as-is:** the per-action attention costs 1 / 2 / 6 / 0 (`05` §4's table); the tool compute figures (`06` §1, `07` §1); the 1–5 tier scale (`DifficultyTier`); scan compute and durations (`04` §3.2).

**[PROPOSAL], invented here:**

| Value | Anchor |
|---|---|
| Layers per tier — 1, 1, 2, 3, 3 | `05` §3.3's "layer count". Stops at 3 because attention per layer is already falling; a fourth would be attrition rather than difficulty |
| Attention per layer — 26, 24, 22, 22, 20 | `05` §3.3's "time pressure", in the only currency §4 leaves. It **falls** as boards grow; read with the size tables or neither makes sense |
| Strikes per layer — 4, 3, 3, 2, 2 | `05` §3.3's "error tolerance" |
| Class share — 50/50 | `05` §3.1: the two test different things and a player who is worse at one should meet it as often as the other, or the weaker skill never improves |
| Cipher noise ×1.8 | The price of having no clock (§4). A multiplier rather than a flat addition, so "the cipher is louder than the grid" survives a re-tune of the underlying noise numbers |
| Bypass = 80% of the bar | `05` §4's "most of the bar". At 100% it is a suicide button; at 50% it is the default opening |
| Alarm penalty = 3 attention | A probe and a half: a guess costs meaningfully more than a deduction |
| Firewall = −2 attention per tier, floor 8 | `09` §1's "flat difficulty increase". The floor is a design rule, not defensive coding: an unwinnable board is the game deciding |
| Tarpit = +1 attention per action | `09` §1's "slows every intruder action". A *surcharge*, not a budget cut — cutting the budget would make it a second Firewall |
| Noise 0 / 1 / 5 / 12, base 2, +4 per alarm | `06` §1's None/Low/Moderate/Very high ladder as a scalar (`01` §3.2) |
| Noise ÷ 8 = 1 heat | `01` §3.2's "noise is tactical, heat is strategic". Most breach noise never becomes heat |
| Breach session = 10 cycles | Bracketed by Quick Scan 5 and Full Scan 15 (`04` §3.2), and the Overflow Kit's own 10 (`06` §1) |
| Grid 5, 5, 6, 7, 7 per side | Big enough that a path is not obvious at tier 1; 7×7 is the largest that stays readable as one character-cell block |
| Buffer 4, 5, 6, 7, 8 | The buffer **is** the difficulty. On a multi-goal board it never exceeds the goals' total length, so taking everything means finding runs that overlap rather than queueing them |
| Goals 1, 1, 2, 2, 3; length 2 + tier/2 + index | Later goals are longer and worth more. The longest always fits the buffer, or it would be decoration |
| Cipher 6, 8, 10, 13, 16 bytes | `05` §3.1's published range. Sixteen bytes of hex subtraction with borrows is a real piece of work and is meant to be |
| Scan false positives 0.35 / 0.15 / 0.04 | `04` §3.2a's "chasing ghosts / working default / it earns it" |
| Detection Array ×0.60 / ×0.35 / ×0.15 | `09` §2. **Multipliers**, so precision can never reach certainty |
| Full Scan sees 50% of rootkits | `04` §3.2's "some rootkit-wrapped" |
| Schematic material: tier ≥ 3, 1 per breach, 12 per unlock | `02` §2.2's "roughly ten destroyed bot instances" (~300 EC), carried across to the other stream |
| Tutorial miner: 6 cycles, tier 1 | `04` §5.1's "weak scripted miner". Below the default 8; tier 1 can never clear the material gate |

---

## 7. Open questions raised by building it

- **BR-1 — a multi-layer attempt earns credit once.** `ResolutionRecord` carries one `puzzleClass` (`05` §2's fixed shape). Since the class is now drawn once per attempt this is no longer a conflict in practice — every layer is the same class — but the field and the local `classesCleared` list both remain, because a future mixed-class attempt would reopen it exactly as it was. Every class actually cleared is listed in `ResolutionState.classesCleared` (local telemetry, alongside `probesUsed`) rather than in extra rows — extra rows would be a **countable** artefact, which is the thing I7 forbids. A proof-of-skill implementation should read that field. Needs a ruling on whether the wire record should grow.
- **BR-2 — a crack is reported `DORMANT`, so it never earns proof-of-skill.** A miner on your own rig is neither live nor defended: it does not fight back, and it is available on demand as soon as one is planted. Reporting it `LIVE` would make the safest action in the game (I9: zero heat on every outcome) also the proof-of-skill source, which is precisely the farming failure `02` §2.4 was written to prevent. Decided that way; flagged because it is a real design call and not an implementation detail.
- **BR-3 — `CARRY`'s price is a relationship, not a number.** It must cost more than carrying is worth on a short cipher and less than losing the layer on a long one, and it is currently the loud-tool rate (6). That holds at 6 and 16 bytes; it has not been checked against a re-tune of the attention budget, and it is the first thing to re-derive if that table moves.
- **BR-4 — one bypass per attempt, not one per layer.** `05` §3.1 says "clearing every layer **or bypassing one**". Read as once-per-layer, a tier-4 attempt is three presses from `BREACHED` with nothing solved, which is `CLAUDE.md`'s "never let anything skip the puzzle wholesale" and would make the Kit a default rather than `06` §2's "panic button with a siren attached". Implemented as once per attempt. Caught by running a tier-3 attempt to a win without solving a layer.
- **BR-5 — offensive-breach tool loss is not implemented.** `05` §4.1 lists "possible tool loss" among failure consequences; only heat and canary handle-tagging are built. Needs a rule for *which* tool, and it touches the same seam as W-4 (faction-tool forfeiture).
- **BR-6 — a foreign miner on the player's own rig is a new state shape.** `RigState.foreignMiners`, each holding a real `DEPLOYED_MINER` allocation (Invariant I6, and `04` §3.1's "the discrepancy is always present in the data"). Rootkit-wrapped miners should eventually be charged but **not disclosed**, which is what `ComputeBudget.unaccountedFor()` exists to expose; today they are disclosed like any other.

## 8. Stale wording found while building

- **`05` §3.3** still says time pressure is "(trace timer speed, §4)". §4 removed the wall clock; there is no timer. It is the per-layer attention budget.
- **`05` §2** says `noiseGenerated` is a function of "time spent". It is attention spent.
- **`04` §3.3** still says the Detection Array "reserves compute permanently to raise per-tick discovery chance". `04` §3.2a and `09` §2 replaced that with precision on 2026-07-26; §3.3 is the last copy of the old reading.
- **`06` §4**'s puzzle-class mapping table lists the retired five. Every entry needs remapping onto Breach Protocol / Offset Cipher, and four of the tools it names (Fuzzer, Rainbow Table, Credential Harvester, Side-Channel Reader) countered mechanics that no longer exist — they are currently unimplemented rather than removed. Logged as **P-1** in `15` §3.
- **`07`'s Topology Mapper** is described as the Traversal counter. The class is gone; the tool's network-map role is unaffected, but its breach role needs restating or dropping.

## 9. Cross-references

- The decisions this implements: `05-hacking-minigame.md` §2, §3, §4
- Target defence profiles: `09-defense-and-hardening.md` §1
- The tools that modify an attempt: `06-intrusion-tools.md`, `07-recon-tools.md`
- Cracking, the tutorial vector: `04-mining.md` §5.1
- What material is for: `02-unlock-gates.md` §2.2, `10-botnets.md` §1a
- Open questions log: `15-open-questions.md`
