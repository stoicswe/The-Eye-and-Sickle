# 16 — The Breach, As Built

**Status:** Implementation record for `05-hacking-minigame.md` (Decided 2026-07-26). The *rules* here are **[PROPOSAL]** unless marked otherwise; the *structure* implements decisions `05` already made.
**Depends on:** `05-hacking-minigame.md` (§2, §3, §4), `02-unlock-gates.md` §2.2/§2.4, `04-mining.md` §3.2/§3.2a/§5.1, `06-intrusion-tools.md`, `07-recon-tools.md`, `09-defense-and-hardening.md`, `10-botnets.md` §1a
**Implemented in:** `solo/src/main/java/.../solo/breach/`, `solo/.../rules/ScanRules.java`, `solo/.../rules/SalvageRules.java`, `solo/Balance.java`

> **What this document is for.** `05` decided the breach's *shape* — three classes, turn-based attention, a stable resolution record — and deliberately left the content open. This is the content, written down so the next person tuning it can see what every number is anchored to. It also holds the two things that must **never** be published to a player: the Enumeration banner rules (§3) and the Traversal decoy construction (§5).

---

## 1. What was built

An attempt is a persisted document, not a session. It has no clock, no deadline and no settlement path — `05` §4 removed the wall clock outright, so a breach survives a quit for free and reloading puts the player back on the same turn. The only thing it holds over time is **compute**: one `ACTIVE_TOOL` reservation, held for the whole attempt and released onto the Thermal Budget curve at resolution, exactly the hold-then-recover shape UI-6 gave a scan (`04` §3.2).

| Piece | Where |
|---|---|
| Engine — begin, act, abort, resolve, dismiss | `solo/breach/BreachRules.java` |
| Board generation, all three classes | `solo/breach/BoardFactory.java` |
| Per-class move resolution | `solo/breach/{Enumeration,Logic,Traversal}Rules.java` |
| The view the client renders | `solo/breach/BreachSnapshots.java` |
| Target list, loadout, tutorial plant | `solo/breach/Targets.java` |
| Seeded, persisted PRNG | `solo/breach/Rng.java` |
| Logic facts, prose + machine form | `solo/breach/Facts.java` |
| Scan false positives, Detection Array precision | `solo/rules/ScanRules.java` |
| Schematic material | `solo/rules/SalvageRules.java` |

---

## 2. The PRNG, and why it is persisted

`solo` had no randomness before this. It is described in its own module charter as "a pure function of `(save, clock)`", which is what lets `SoloGameTest` assert exact ethecoin figures and what makes a bug reproducible from a save file.

A breach needs generation and a scan needs a roll, so `SoloSave.rngSeed` is a single `long` advanced by **splitmix64** and **written back to the save on every draw**.

> ⚠ **Save scumming is the failure this exists to prevent.** A draw that is not committed is a draw the player can reroll by quitting without saving. A player who did not like their board would reload until they got one they did, and a scan that said the wrong thing would be re-run until it said the right thing — which guts `04` §3.2a's "a scan hit is a lead to corroborate, not an answer". Every rule that draws calls `Rng.commit(save)` before returning. Boards are additionally generated **once, at `begin`, and persisted**, so a mid-breach reload replays nothing at all.

`nextInt` uses Lemire's multiply-shift **without** the rejection loop, deliberately: rejection consumes a variable number of draws, which makes the stream shape depend on the values it produced, which makes a replay from a stored seed depend on the code path as well as the seed. The residual bias is under 2e-17 at any bound this game uses.

---

## 3. ⚠ ENUMERATION — the banner rules (DO NOT PUBLISH)

`05` §3.2 requires every class to have "a verification step that rewards a human read", and spells out only Logic's and Traversal's. **Enumeration's is the service banner.** The target's role constrains which bands can hold open ports, so a player who reads the banner eliminates whole bands without probing them, and a fixed heuristic never can — which is §3.2(d), "cannot use the 'intuition' shortcuts a human gets from reading flavor data", made mechanical rather than asserted.

| Banner | Rule |
|---|---|
| `EDGE RELAY` | at least one open port in the **last** band; never any in band 0 |
| `STORAGE ARRAY` | no open port in band 0; **exactly one** adjacent pair somewhere |
| `AUTH BROKER` | exactly one open port in band 1; no two open ports adjacent anywhere |
| `MEDIA CACHE` | every open port sits on an **even** slot |

> ⚠ **These belong in this document and nowhere else.** Do not put them in a `man` page, a term entry, a tooltip or a tutorial. The moment the rule is published the read becomes a lookup, and the class loses the only thing that distinguishes it from clicking every slot in order. `bannerNote` gives one line of atmosphere that gestures at the rule without stating it; that is the maximum disclosure.

Generation is **constructive**, not rejection-sampled, for the RNG-stream reason in §2. Board size is `12 + 2(tier-1)` slots in bands of 4; open ports `2 + (tier+1)/2`; filtered ports `tier`.

**Slot indices are not port numbers.** The game never claims slot 22 is ssh. Service names are real; the mapping to a slot is not asserted, because a wrong mapping teaches something false, which `CLAUDE.md` treats as worse than teaching nothing. If the curriculum ever wants well-known ports, `../education/05-networking.md` owns that with a source and a date, and this table follows it.

---

## 4. LOGIC — Mastermind, exactly

```
exact   = |{ i : guess[i] == secret[i] }|
matched = sum over symbols s of min( count(guess, s), count(secret, s) )
partial = matched - exact
```

The classic bug is counting a repeated symbol more than once. It looks harmless and is not: the feedback stops being consistent with itself, a player deducing correctly reaches a contradiction, and the class silently converts from reasoning into guessing. Covered by a test with repeats in both operands, and by a 500-trial symmetry check — symmetry is what makes the consistency filter sound.

**Error tolerance is the class.** A guess that is *provably impossible* given every earlier response is marked `inconsistent` and costs a **strike** on top of its attention. Without that single rule, the optimal play at every tier is to fire 2-attention probes until the budget runs out and hope, and the "deductive reasoning" in `05` §3.1 is optional flavour. Note what it does *not* punish: a consistent guess that turns out to be wrong is a legitimate deduction that lost.

**`KEYSPACE 4096 -> 37` is a real number**, recomputed by walking the keyspace (at most 100 000 candidates at tier 5, once per probe, in a turn-based system). It is the class's diegetic centrepiece and the only place a player can *see* that deduction is doing something guessing would not.

**Facts are constraints, not decoration.** A quiet read costs 1 attention and `05` §4 calls it "the patient baseline". If listening did not move the keyspace readout, players would correctly conclude it does nothing and stop listening — killing the patient half of the loud-versus-patient trade. So each card carries a machine form alongside its prose (`solo/breach/Facts.java`) and the candidate filter applies it. Every card is checked true against the secret at generation: the baseline never lies, because a baseline that sometimes lies is a second gamble rather than a baseline.

The alphabet is ten ASCII symbols `@ $ % & * + = ~ ? !`. **`#` is deliberately excluded** — `UiContractTest.noHexInJava` fails the build on `#` followed by 3–8 hex characters anywhere under the client's `ui`/`view` packages, and an alphabet containing `#` is one rendering away from producing exactly that in a source literal.

---

## 5. ⚠ TRAVERSAL — the decoy construction, and the P-3 measurement

`05` §3.2, verbatim: "the true objective node hidden among decoys distinguishable only by cross-referencing recovered logs." Built as:

- the **manifest** names one service and one time, and is public from the start;
- each objective candidate carries a **log fragment** naming a service and a time, recoverable with `listen` at 1 attention;
- **exactly one candidate matches both fields.** The others match one field or neither.

A single-field match is the trap: it is exactly what a reader who skims one column would accept.

> ⚠ **Every node on the penultimate rank exits to *all* objective candidates.** This is load-bearing and was added after a measurement. Without it, a player who navigates correctly can arrive at a junction the true objective is not reachable from — the cross-reference returns "none of these" and the layer becomes a backtracking exercise instead of the read §3.2 specifies. It also keeps P-3 honest: the reader-versus-heuristic comparison only holds if both are choosing from the same K.

### P-3, measured

`05` §6 leaves **P-3** open — "how much does manual play beat bot play? It is *the* number behind Invariant I10" — and notes that §4 made it answerable by denominating the gap in probe count rather than seconds. Measured over 600 generated tier-4 and tier-5 boards, playing each one twice: once cross-referencing the logs, once extracting blind in a rotated order (a fixed heuristic that cannot read).

| | attention spent | layer lost outright |
|---|---|---|
| **Reader** | 6.00 | **0.0%** |
| **Fixed heuristic** | 7.33 | **51.7%** |

> **The finding is the loss rate, not the probe count.** The attention gap alone is ~1.2x, which would have looked negligible and might have been "fixed" by making the miss more expensive. The real answer is that at K=4 candidates and a 2-strike limit, a blind extractor strikes out about half the time — `P(first two both wrong) = 3/4 x 2/3 = 1/2`, and the measurement agrees. That is Invariant I10 with a number on it.

⚠ **Do not tune this away.** If the gap ever needs widening the lever is a larger K or a subtler decoy — never a cheaper `extract`, which is what would make the heuristic competitive and the reading pointless. Asserted by `BreachBoardsTest.theHumanReadIsWorthSomething`.

---

## 6. The numbers, and what each is anchored to

Every figure lives in `solo/Balance.java` with its citation. Summary of what is **decided** versus what this document invented:

**Decided elsewhere, used as-is:** the per-action attention costs 1 / 2 / 6 / 0 (`05` §4's table); the tool compute figures (`06` §1, `07` §1); the 1–5 tier scale (`DifficultyTier`); scan compute and durations (`04` §3.2).

**[PROPOSAL], invented here:**

| Value | Anchor |
|---|---|
| Layers per tier — 1, 1, 2, 3, 3 | `05` §3.3's "layer count"; stops at 3 because §3.1 fixed the class set at 3 |
| Attention per layer — 26, 24, 22, 22, 20 | `05` §3.3's "time pressure", in the only currency §4 leaves. It **falls** as boards grow; read with the size tables or neither makes sense |
| Strikes per layer — 4, 3, 3, 2, 2 | `05` §3.3's "error tolerance" |
| Class mix per tier | Enumeration → Logic → Traversal, the order they teach in. Tier 5 repeats Traversal deliberately |
| Bypass = 80% of the bar | `05` §4's "most of the bar". At 100% it is a suicide button; at 50% it is the default opening |
| Alarm penalty = 3 attention | A probe and a half: a guess costs meaningfully more than a deduction |
| Firewall = −2 attention per tier, floor 8 | `09` §1's "flat difficulty increase". The floor is a design rule, not defensive coding: an unwinnable board is the game deciding |
| Tarpit = +1 attention per action | `09` §1's "slows every intruder action". A *surcharge*, not a budget cut — cutting the budget would make it a second Firewall |
| Noise 0 / 1 / 5 / 12, base 2, +4 per alarm | `06` §1's None/Low/Moderate/Very high ladder as a scalar (`01` §3.2) |
| Noise ÷ 8 = 1 heat | `01` §3.2's "noise is tactical, heat is strategic". Most breach noise never becomes heat |
| Breach session = 10 cycles | Bracketed by Quick Scan 5 and Full Scan 15 (`04` §3.2), and the Overflow Kit's own 10 (`06` §1) |
| Credential Harvester = 4 attention | The one cost not on `05` §4's table — see §7 |
| Enumeration 12 + 2/tier slots, bands of 4 | 12 is three full bands, the smallest board on which a 1-attention sweep beats probing everything |
| Logic length 3 + tier/2, alphabet 5 + tier | Keyspaces 216 → 100 000. The tier-5 jump is where the readout stops being optional |
| Salted at tier ≥ 3, else 30% | `06` §2's "conditional power spike ... it rewards recon" |
| Rainbow reveals 2 positions | A full reveal is the Overflow Kit's job, and the Kit is proof-of-skill-gated for that reason |
| Traversal 3 + tier/2 ranks, K = 2 + tier/2 | K is P-3's denominator — see §5 |
| Scan false positives 0.35 / 0.15 / 0.04 | `04` §3.2a's "chasing ghosts / working default / it earns it" |
| Detection Array ×0.60 / ×0.35 / ×0.15 | `09` §2. **Multipliers**, so precision can never reach certainty |
| Full Scan sees 50% of rootkits | `04` §3.2's "some rootkit-wrapped" |
| Schematic material: tier ≥ 3, 1 per breach, 12 per unlock | `02` §2.2's "roughly ten destroyed bot instances" (~300 EC), carried across to the other stream |
| Tutorial miner: 6 cycles, tier 1 | `04` §5.1's "weak scripted miner". Below the default 8; tier 1 can never clear the material gate |

---

## 7. Open questions raised by building it

- **BR-1 — a multi-class attempt earns credit for one class.** `ResolutionRecord` carries one `puzzleClass` (`05` §2's fixed shape), so a tier-4 attempt that solved Enumeration, Logic *and* Traversal records only the deepest. Every class actually cleared is listed in `ResolutionState.classesCleared` (local telemetry, alongside `probesUsed`) rather than in extra rows — extra rows would be a **countable** artefact, which is the thing I7 forbids. A proof-of-skill implementation should read that field. Needs a ruling on whether the wire record should grow.
- **BR-2 — a crack is reported `DORMANT`, so it never earns proof-of-skill.** A miner on your own rig is neither live nor defended: it does not fight back, and it is available on demand as soon as one is planted. Reporting it `LIVE` would make the safest action in the game (I9: zero heat on every outcome) also the proof-of-skill source, which is precisely the farming failure `02` §2.4 was written to prevent. Decided that way; flagged because it is a real design call and not an implementation detail.
- **BR-3 — the Credential Harvester's 4 attention is the only cost not on `05` §4's table.** It skips a deduction step, which is more than a probe buys, but it is reputation-gated and only Moderate noise, so pricing it loud would make it a worse Fuzzer at the same price. Four is the only value keeping both relationships true. If §4's table is meant to be closed, this needs a row or a re-price.
- **BR-4 — one bypass per attempt, not one per layer.** `05` §3.1 says "clearing every layer **or bypassing one**". Read as once-per-layer, a tier-4 attempt is three presses from `BREACHED` with nothing solved, which is `CLAUDE.md`'s "never let anything skip the puzzle wholesale" and would make the Kit a default rather than `06` §2's "panic button with a siren attached". Implemented as once per attempt. Caught by running a tier-3 attempt to a win without solving a layer.
- **BR-5 — offensive-breach tool loss is not implemented.** `05` §4.1 lists "possible tool loss" among failure consequences; only heat and canary handle-tagging are built. Needs a rule for *which* tool, and it touches the same seam as W-4 (faction-tool forfeiture).
- **BR-6 — a foreign miner on the player's own rig is a new state shape.** `RigState.foreignMiners`, each holding a real `DEPLOYED_MINER` allocation (Invariant I6, and `04` §3.1's "the discrepancy is always present in the data"). Rootkit-wrapped miners should eventually be charged but **not disclosed**, which is what `ComputeBudget.unaccountedFor()` exists to expose; today they are disclosed like any other.

## 8. Stale wording found while building

- **`05` §3.3** still says time pressure is "(trace timer speed, §4)". §4 removed the wall clock; there is no timer. It is the per-layer attention budget.
- **`05` §2** says `noiseGenerated` is a function of "time spent". It is attention spent.
- **`04` §3.3** still says the Detection Array "reserves compute permanently to raise per-tick discovery chance". `04` §3.2a and `09` §2 replaced that with precision on 2026-07-26; §3.3 is the last copy of the old reading.
- **`06` §4**'s puzzle-class mapping table still lists `Credential` and `Timing`, both closed by `05` §3.1 on 2026-07-26.

## 9. Cross-references

- The decisions this implements: `05-hacking-minigame.md` §2, §3, §4
- Target defence profiles: `09-defense-and-hardening.md` §1
- The tools that modify an attempt: `06-intrusion-tools.md`, `07-recon-tools.md`
- Cracking, the tutorial vector: `04-mining.md` §5.1
- What material is for: `02-unlock-gates.md` §2.2, `10-botnets.md` §1a
- Open questions log: `15-open-questions.md`
