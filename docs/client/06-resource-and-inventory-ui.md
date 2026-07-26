# 06 — Resources, Valuables and Inventory

**Status:** ⚠️ **[PROPOSAL]** — every layout, anatomy and interaction rule below is new ground. What it renders is not: the four-number compute requirement (`../design/01-core-resources.md` §1.4), the resource model itself (`../design/01-core-resources.md`), the three storage tiers and their exposure semantics (§6 of the same doc), the tool stat tables (`../design/06-intrusion-tools.md` §1, `../design/07-recon-tools.md` §1, `../design/08-stealth-and-noise.md` §1, `../design/09-defense-and-hardening.md` §1, `../design/10-botnets.md` §2), and the per-item signed provenance chain with **client-side offline re-verification** (`../architecture/04-item-provenance.md` §6.1–6.2) are all **Established** and are cited, never re-decided. Invariants I1, I2, I3, I6, I9, I11, I12, I13 and I14 appear here as concrete UI rules; where one does, it is named. Toolkit and standards claims were checked against live sources in this pass and are marked **(verified)**; anything not checked is marked **⚠ unverified**.
**Depends on:** `00-client-overview.md` (pillars C1–C6, §1.1 the owns/renders split, §6.1 the window catalogue), `01-visual-language.md` (**the token contract — token, primitive and state-class names here are cited verbatim, never redefined**), `../design/01-core-resources.md`, `../design/02-unlock-gates.md`, `../design/03-economy.md`, `../design/04-mining.md`, `../design/06`–`../design/11`, `../architecture/04-item-provenance.md`, `../architecture/06-data-model.md`
**Depended on by:** `03` (window catalogue detail — this doc fixes the *content* of `rig-monitor`, `ledger` and `storage`; `03` fixes their chrome and their neighbours), `05` (docked fallback — every surface here must survive being a panel), the `client/` module implementation

---

## 1. Scope, and the surfaces this document governs

### 1.1 What this doc is for

The project owner's fourth requirement is that the interface be **easy to use while clearly representing the player's currencies, valuables, inventory and tools**. This document is that requirement's home. It specifies, to build depth, the readouts for compute, ethecoin, noise, heat and storage; the reusable item card; the inventory at full size; the item-history view; and the general cost-before-commit rule that governs every spend.

It does **not** specify the breach surfaces (trace meter, live engagement, the terminal's probe loop), the command grammar and teaching layer, or the docked fallback. Those are `03`, `04` and `05`.

| Window id (`00-client-overview.md` §6.1) | What this document fixes | What it defers |
|---|---|---|
| `rig-monitor` | The whole compute readout (§2), the compact strip (§2.5), personal heat and noise placement (§4) | Window chrome, geometry persistence → `03` |
| `ledger` | Balance presentation, pending vs settled, the public-ledger investigator view (§3) | Ledger command grammar → `04` |
| `storage` | Tier layout, the at-risk answer, tier moves and their confirmations (§5) | — |
| `botnet` | The socket action and its confirmation (§5.4), the loss record (§6.4) | Bot control surfaces → `03` |
| `market` | The I1/I2 vocabulary rules (§3.4), the blocked-item card state (§6.3) | Offering catalogue → `03`, and it is stubbed server-side (W-3) |
| *any window* | The `CommitSheet` (§9) and the item-history view (§8), both of which open wherever their subject lives | — |

### 1.2 Four framing rules that apply to everything below

**R1 — One number, one home.** Every resource has exactly one canonical readout surface. Compute's is `rig-monitor`; ethecoin's is `ledger`; personal heat's is `identity`. Every other appearance of that number is a **reference**: same format, same precision, same authority state, rendered by the same component. The failure this avoids is specific and nasty — two surfaces disagreeing during a latency window, which under C4 is the client contradicting itself at the exact moment the player is deciding whether to trust it.

**R2 — Server value, client value, and draft value are three different things and never look alike.**

| Kind | Example | Rendering |
|---|---|---|
| Server value | balance, cycles allocated, gate verdict | `-es-fg-primary`, mono, `es-authority` badge per `01-visual-language.md` §8.4 |
| Client-computed convenience figure | `est. 1,830 EC to replace` (§5.2) | `-es-fg-secondary`, prefixed `est.`, **never** used in a `CommitSheet`, never styled like a server value |
| Draft (client-owned, unsubmitted) | an unapplied loadout (§7.6) | panel-level label + explicit `[ apply ]` / `[ revert ]`; **not** a state class (see **RI-2**) |

`00-client-overview.md` §1.1 gives the client exactly one category of genuine ownership beyond presentation — draft state, and offline provenance verification. Anything the client derives beyond that is a convenience, and convenience figures that dress as server facts are how C4 erodes one helpful number at a time.

**R3 — A zero is never an omission.** A cost of zero renders as `none` **with its reason** (`none · defending your own rig`), not as a blank line and not as an absent row. A blank teaches nothing; an explicit zero with a reason teaches Invariant I9. This is the single cheapest C6 mechanism in the client, and it costs one line of copy per surface.

**R4 — Vocabulary enforces invariants more reliably than logic does.** §3.4 reserves `buy` / `price` / `purchase` for ethecoin-gated items and `unlock` / `install` for ceilings; §9 reserves the arrow form `a → b` for projections. A word that cannot be written is an invariant that cannot be violated by accident, and unlike a runtime check it survives being copied into a new screen by a developer who never read this file.

---

## 2. The compute readout

> **Established requirement**, quoted from `../design/01-core-resources.md` §1.4: *"The player must always be able to see, at a glance: total cycles, allocated (by consumer), available, and recovering (with time-to-recover). The compute ledger is the game's most important HUD element."* That sentence justifies the always-on-top window (the scaffold's `ToolWindow.rigMonitor()` already encodes it) and it is the specification this section implements.

Compute is the master scarcity (Pillar 2). A starting rig is **100 cycles**. It is not spent-and-gone; it is **allocated**, and most allocations are reservations that persist while the thing they power runs (`../design/01-core-resources.md` §1).

### 2.1 The anatomy, top to bottom

Default geometry 420×560 (the scaffold's figure), comfortable density.

```
┌ RIG MONITOR ───────────────────────────────────── ⌘0 ┐
│                                                       │
│  available                                            │
│  15 / 100 cycles                        loaded  ×4.6  │   ← 1 headline + 2 thermal
│                                                       │
│  ▐███████│████│██│███│▤▤▤▤│░░░░▌                      │   ← 3 gauge
│   mining  bots ch df  ??  recov  avail                │
│                                                       │
│  ▮ self-mining                       30 c  [realloc]  │   ← 4 legend
│  ▮ Breacher #3 · bot frame           22 c  [stand dn] │
│  ▮ Recon #1 · bot frame               8 c  [stand dn] │
│  ▮ control channels ×5               15 c  [audit]    │
│  ▮ Tarpit · armed                     8 c  [disarm]   │
│  ▮ unaccounted                       20 c  ⚠ no owner │
│  ▤ recovering                        18 c  4m 20s     │
│  ▯ available                         15 c             │
│                                     ─────             │
│  total                              100 c             │
│                                                       │
│  ▸ compute journal            (last 20 events)        │   ← 5 attribution
│                                                       │
│ ─────────────────────────────────────────────────     │
│  noise  43 · moderate  ▐███▏░░│░░░░░░▌ 60  90         │   ← 6 noise (§4.2)
│  heat   PERSONAL · moderate  ●●●○○                    │   ← 7 heat (§4.3)
└───────────────────────────────────────────────────────┘
```

**1 — Headline.** `es-stat`, mono `TYPE_MONO_READOUT_LG` (28/34). The numerator is **available**, not allocated, because `01-visual-language.md` §2.2.1 names available as *"the number the player is looking for"*, and because the decision compute governs is always "can I afford this now". The word `available` sits directly above it as the label, and it is never dropped: `15 / 100` on its own is ambiguous in exactly the way that gets a player killed.

Per `01-visual-language.md` §8.1 the readout is `-es-fg-primary`, **not** `-es-compute-available`. The tie between the headline number and its bar segment is carried by the legend swatch, not by tinting the number — tinting every readout in its domain colour would spend the palette on decoration and would put a 28px coloured number next to a coloured meter, which reads as two signals where there is one.

**2 — Thermal state.** A band chip plus a recovery multiplier. §2.3.

**3 — The gauge.** `es-gauge es-gauge-compute`, track `-es-compute-track`, height 8 (comfortable) / 6 (compact), radius `RADIUS_SM`. Segment order is fixed and specified in §2.2.

**4 — The legend.** Mandatory: `01-visual-language.md` §8.1 states outright that for the compute variant the legend *"is not optional — it is the decision surface."* One row per segment, always including `recovering` and `available`, closing with a `total` row that reconciles. The reconciliation row exists because §2.4's `unaccounted` segment only means anything if the player can see the arithmetic.

**5 — Compute journal.** §2.4.

**6, 7 — Noise and heat.** §4. They live at the bottom of the rig monitor under a divider because they are the two other things the player checks in the same glance, but they are separated by a rule and by scope labels so they can never be read as part of the compute ledger.

### 2.2 Segmentation by consumer — the part that is not optional

A single grey `allocated` bar is a defect (`01-visual-language.md` §2.2.1, `00-client-overview.md` §C2). The player's decision is never "am I loaded" — it is **which one do I stop**, and that question is unanswerable from an aggregate.

The six consumer classes are Established (`../design/01-core-resources.md` §1.1) and are named on the wire by `../architecture/06-data-model.md`'s `compute_allocations.consumer_type` enum:

| Class | Wire name | Model | Legend label form | Reclaim action offered |
|---|---|---|---|---|
| Self-mining | `self_mining` | player-set allocation | `self-mining` | `[ reallocate ]` → `mining` |
| Bot frames | `bot_frame` | permanent while the instance runs | `<frame> #<n> · bot frame` | `[ stand down ]` — see **RI-7** |
| Control channels | `control_channel` | **3 cycles each**, permanent while the miner is live | `control channels ×N` | `[ audit ]` → `mining` |
| Defenses | `defense` | permanent while armed | `<tool> · armed` | `[ disarm ]` |
| Relay hops | `relay_hop` | per hop, per session | `relay chain · N hops` | `[ drop hop ]` |
| Tools | `tool` | per-use, **or** reserved while equipped | `<tool> · reserved` / `<tool> · running` | `[ unequip ]` |

**Segment order is fixed by consumer class, never by magnitude:**

```
self_mining · bot_frame · control_channel · defense · relay_hop · tool
  · unaccounted · recovering · available
```

Two reasons, and the second is the one that matters. First, a bar whose segments reorder when a value changes cannot be pointed at, which is the same rule `01-visual-language.md` §7.3 applies to lists ("anything that reorders a list the player may be pointing at" may never animate). Second, the order runs from **standing commitments the player set deliberately** to **transient draws that end on their own**, so the churn is clustered at the right-hand end next to `recovering` and `available`. The left half of the bar is stable for an entire session; the right half is where the movement is. A player who learns "my mining and my bots are the first two blocks" keeps that mental image all evening.

Within a class, instances render in creation order and never re-sort. Control channels **aggregate** into one segment with a count (`control channels ×5 · 15 c`) because they are identical 3-cycle reservations and five identical slivers is noise; every other class renders one segment per instance. The aggregate expands in the legend on click.

**Legend actions are affordances, not authority.** Clicking `[ disarm ]` opens the `CommitSheet` for that action (§9); it does not change the bar. The bar changes when the server says so.

**When the rig has too many consumers**, `01-visual-language.md` **V-7** already flags that per-consumer segmentation stays legible to roughly eight segments and that a late-game loadout exceeds it. This document's answer, offered as the candidate V-7 asked for: **collapse by class, never by magnitude.** Once segment count exceeds eight, classes with more than one instance collapse to a class segment with a count (`bot frames ×6 · 71 c`), expandable in the legend and on `Alt`. Collapsing by class preserves the fixed order and the mental image; collapsing "the six smallest" into an "other" bucket would destroy both, and would hide exactly the cheap reservations a player is looking to reclaim.

### 2.3 Making the Thermal Budget felt

`../design/01-core-resources.md` §1.3 is the design's own statement of intent: *"overextension should be punished by the physics of the rig, not by a rulebook."* The catch is that the punishment is deferred and conditional — the player pays it later, in recovery time they did not experience at the moment of the decision. A rig that is overextended must therefore **read** as overextended continuously, and the cost must be visible **before** the commit, or the punishment arrives as a mystery and the game has produced exactly the "the game decided" feeling `../design/00-vision-and-pillars.md` §5 forbids.

Three mechanisms, in increasing order of how much they change behaviour.

**(a) The load and multiplier readout — always on.** Next to the headline: `loaded · 84% · ×4.6`. Three tokens of information:

- **the band name**, which is what the player remembers;
- **the load factor** `allocated / total`, which is what they can move;
- **the recovery multiplier**, the factor by which time-to-recover is stretched relative to an unloaded rig — the honest translation of a curve into a number a player can act on.

`../design/01-core-resources.md` §1.3 gives the curve as `base_rate × (1 − load_factor)^k` and two anchor points: at 50% load and T1 thermal a 35-cycle Thorough Scan takes ~2× its run duration to recover; at 85% load, ~5×. **The client does not evaluate that curve.** The multiplier, the band name and each recovering allocation's completion time arrive from the server, exactly as heat bands do (`01-visual-language.md` §2.2.4). This is I14 in its most tempting-to-violate form: the curve is simple enough that a client could compute it, and the moment it does, a balance value lives in the view layer and `k` can drift between client and server without anything failing loudly.

**(b) Thermal bands.** Four bands, rendered as an `es-chip` with the band name — not a meter, for the same reason heat is not a meter (`01-visual-language.md` §2.2.4): the player's decision is a threshold decision and a smooth bar invites a precision the model does not have.

| Band | Load factor | Approx. multiplier (T1) | Chip token |
|---|---|---|---|
| `lean` | < 40% | ×1.0 – ×1.3 | `-es-status-idle-fg` / `-subtle` |
| `working` | 40 – 69% | ×1.3 – ×2.2 | `-es-compute-allocated` |
| `loaded` | 70 – 89% | ×2.2 – ×5 | `-es-status-warn-fg` / `-subtle` |
| `saturated` | ≥ 90% | ×5 + | `-es-status-bad-fg` / `-subtle` |

⚠️ **[PROPOSAL], and the numbers are not this document's to fix.** The band count and boundaries are balance values (`CLAUDE.md`: *"if a constant here changed and a player would gain something, it's a balance value and it belongs to the server"*). They are anchored to the two points the design gives and they exist so an implementer has something to render. The **client contract** is only: a band name from a fixed enum, a load percentage, and a multiplier. Tracked as **RI-8**.

Note the deliberate reuse of the status roles rather than four new tokens. `01-visual-language.md` §2.5 caps the hue budget at nine and thermal load is not a new *meaning* — it is caution and then danger about a quantity that already has a colour. A `thermal` hue would be a tenth hue bought for a band name.

**(c) The projection — the mechanism that actually teaches the curve.** Hovering or focusing a legend row's reclaim action draws a **ghost projection** on the gauge: the segment that would be freed is redrawn as a 1px outline in its own token with no fill, `available` extends by the same outline, and the load readout switches to the projection form:

```
loaded 84% ×4.6   →   working 62% ×1.9
```

Three rules make this safe:

1. **A projection is outline-only and never filled.** A filled projection is the client rendering a state the server has not agreed to, which is C4's exact failure.
2. **The arrow form `a → b` is reserved for projections** across the entire client (R4). Nothing else uses it — not deltas, which use signed values with U+2212 (`01-visual-language.md` §9.3), and not transitions in a log line.
3. **The projection is dismissed on blur and can never persist.** A stale projection is a lie with a long half-life.

The reason this is the mechanism that teaches: the player can stand in front of the sheet, hover three different reclaim actions, and watch `×4.6` fall to `×1.9` or barely move. That is the Thermal Budget curve made manipulable, and it converts a stat nobody reads on an upgrade screen into the thing they are thinking about when they decide how many bots to run.

**Rendering the hatch.** The `recovering` segment is hatched (`01-visual-language.md` §2.4 requires it, so the distinction survives greyscale) and `overcommit` is cross-hatched. Mechanism: JavaFX CSS `linear-gradient` accepts a cycle method — the documented grammar is `linear-gradient( [ [from <point> to <point>] | [ to <side-or-corner>], ]? [ [ repeat | reflect ], ]? <color-stop>[, <color-stop>]+)` **(verified against the JavaFX 26 CSS Reference)**. A 45° two-stop repeating gradient over a 6px period gives the hatch with no image asset and no per-theme raster:

```css
.es-gauge-compute .segment-recovering {
    -fx-background-color: linear-gradient(from 0px 0px to 4px 4px, repeat,
        -es-compute-recovering 0%, -es-compute-recovering 49%,
        -es-compute-track 50%, -es-compute-track 100%);
}
```

`-fx-background-repeat` with `repeat-x`/`repeat-y` is also documented **(verified)** and remains the fallback if the gradient period misbehaves at fractional DPI scales.

**Overcommit.** `../design/01-core-resources.md` does not describe a mechanism that produces allocation beyond capacity, and none should be added — but a server-side rig change can produce one transiently. The rule from `01-visual-language.md` §2.2.1 stands: render it as a cross-hatched over-range segment, **never by silently clamping the bar**. Concretely: the track reserves a 12px over-gutter to the right of the 100% mark, separated by a 1px `-es-border-control` rule, and the over-range draws into it with `es-state-overcommit`. Clamping would make an impossible state look like a full rig, and a full rig is a state the player thinks they understand.

### 2.4 `unaccounted` — where the audit gameplay lives

`../architecture/06-data-model.md` §2 states the constraint plainly: *"sum of active allocations must reconcile against `rigs.compute_cores`, and a discrepancy is exactly what a manual auditor (or a hidden hostile miner) creates."* `../design/04-mining.md` §3.1 makes it a hard implementation requirement that the process, connection and storage views be real and consistent, because *"the discrepancy is always present in the data"* even when scans would miss it. And `../design/15-open-questions.md` **P-9** notes that the consumer enum has no name for the host-side draw of a foreign miner (Invariant I6), so a rootkit-wrapped parasite has no legend row to appear in.

The UI consequence is a rule, and it is the single most important rule in this section:

> **The rig monitor renders the sum of what it was told, and never reconciles a mismatch on the client.** If `Σ allocations + recovering + available ≠ total`, the difference renders as an **`unaccounted`** segment with a legend row reading `unaccounted · 20 c · no owning consumer`. It is content, not a bug.

Rendering: `-es-compute-allocated` fill with `es-state-unknown` and a `?` glyph on the legend row — the client genuinely does not know what owns those cycles, and `es-state-unknown` is exactly the class for that. It does not get its own colour, both because the hue budget is spent and because a distinctive colour would make the tell easier to spot than `../design/04-mining.md` §3.3's *"nothing announces itself"* intends. Tracked as **RI-1**.

Two things follow that an implementer must not get wrong:

- **The `total` reconciliation row is mandatory**, and it shows the arithmetic. An `unaccounted` segment nobody can check is a coincidence; one that sits above a visible sum is evidence.
- **The rig monitor's numbers and the `audit` window's process/connection/storage tables are the same data.** They must reconcile with each other exactly, because cross-referencing them *is* the manual-investigation mechanic, and it is described by `../design/04-mining.md` §3.1 as the game's second-strongest tutorial vector. If the two windows round differently, or one filters a row the other shows, the mechanic silently breaks and no test will notice.

### 2.5 Attribution: the compute journal

C3 requires every number that moved to explain itself, and `00-client-overview.md` §6.3 binds `Alt`/`Option` to reveal attribution overlays on every visible meter.

**Held `Alt`** labels each segment in place: consumer name, cycles, and the timestamp it was allocated (`Breacher #3 · 22 c · since 14:02:11`). Recovering segments name the action that spent them and when they return (`Thorough Scan · 35 c · spent 21:04:33 · returns 21:31:12`).

**The compute journal** is a collapsible panel in `rig-monitor` listing the last 20 allocation events, newest first, built from `es-log-line`:

```
21:31:12  ●  recovery      +35 c returned            Thorough Scan
21:14:07  ●  bot_frame     −22 c destroyed           Breacher #3
21:04:33  ●  scan          −35 c → recovering        Thorough Scan · load 84% ×4.6
20:58:02  ●  control_ch    −3 c reserved             miner @ node 4c-11
```

The `load 84% ×4.6` suffix on the spend row is the whole point: three hours later, when a player asks why a scan took twenty-seven minutes to come back, the answer is on the line that spent it. That is C3 as a data-retention requirement rather than a hover state.

New lines append; the view never re-sorts under the cursor (`01-visual-language.md` §7.3).

### 2.6 The compact rig strip

`00-client-overview.md` §C2 establishes that `rig-monitor` cannot be permanently closed — closing it **minimises it to a compact strip**. Specification:

- An always-on-top `Stage`, 360×44 comfortable / 320×36 compact.
- Contents, left to right: available/total (`TYPE_MONO_READOUT`, 20/26) · the compute gauge at 6px with its segments but **no legend** · the thermal band chip · the noise value and band · the personal heat chip.
- `Shortcut+0` restores the full monitor (unremappable — `00-client-overview.md` §6.3).

**Be honest about what the strip is.** It removes information, so it is **not** a compact density and `01-visual-language.md` §4.4's rule that "compact removes space, never information" is not violated — the strip is a distinct *window state*, and it is required to (a) carry an explicit affordance back to the full window, and (b) never be the only compute surface the client offers. C2 is satisfied because available-of-total is on screen; the legend is one keystroke away, not gone.

The strip is also the reason `01-visual-language.md` **V-7**'s collapse-by-class rule (§2.2) matters: at 360px with six bot frames the strip is *always* in the collapsed regime, so the collapse must be the same one the full window uses, or the two surfaces show differently-shaped bars for the same rig.

### 2.7 What the compute readout may never do

1. Never show one aggregate `allocated` figure (C2, `../design/01-core-resources.md` §1.4).
2. Never merge `recovering` into `available` (`01-visual-language.md` §2.2.1). They are different facts with different time horizons and merging them is how a player commits cycles that are not there.
3. Never animate a numeric readout — no count-up, no odometer (`01-visual-language.md` §7.3). Values snap.
4. Never ease a meter fill. The Thermal Budget curve is already non-linear in the data; easing on top double-counts and misinforms (`01-visual-language.md` §7.2).
5. Never clamp overcommit (§2.3).
6. Never reconcile a discrepancy (§2.4).
7. **Never present a compute purchase, not even disabled.** See §3.4 — this one is Invariant I1 and it lives in the ethecoin section because that is where the temptation is.

---

## 3. Ethecoin

### 3.1 The balance, and why it is not one number

Format is fixed by `01-visual-language.md` §9.3 and by **P-8** in `../design/15-open-questions.md`, which settled EC at 100 minor units: **always two decimals, suffix `EC`, never a glyph, mono, right-aligned via `es-numeric`, thousands separator from the OS locale**. `1,240.00 EC`.

The `ledger` window's headline is a stack of two facts, never a single computed figure:

```
BALANCE
  1,240.00 EC                    ← settled, -es-ec-fg, TYPE_MONO_READOUT_LG
  ─────────
  ⟳ 2 pending    −85.00 EC       ← es-state-pending, -es-ec-debit, expandable
```

**There is deliberately no third "available to spend" line.** It would be the client subtracting its own outbox from a server fact and publishing the result as a balance — a number the server never sent and can contradict two ways (a refused spend, a concurrent credit). Once it exists the player treats it as *the* balance, and the first time it disagrees with a refusal the client has taught them it lies.

What replaces it costs the player nothing: the two operands are right-aligned mono on consecutive lines, so `1,240.00` over `−85.00` **reads as a subtraction without one being performed**. `01-visual-language.md` §4.6's shared-right-edge rule was written for exactly this. Expanding the pending strip itemises each in-flight intent — what, how much, how long in flight — so the player can see which one to worry about.

The affordability *verdict* is never the client's either (`00-client-overview.md` §C4: *"The client never pre-computes a gate"*). The `CommitSheet` shows the cost; the server returns `confirmed` or `refused` with the rule it applied (§9).

### 3.2 Pending, settled, refused — the honest lifecycle

| Stage | What exists | Rendering | Balance moves? |
|---|---|---|---|
| drafted | client-owned draft (`00-client-overview.md` §1.1) | inside the `CommitSheet`; no ledger row | no |
| submitted | intent sent | a row appears **at the top** of `ledger` with `es-ledger-row es-state-pending`, hollow-ring `es-authority` badge naming the action | **no** |
| confirmed | server record | badge disappears; the row settles into timestamp order | yes |
| refused | server applied a rule | the pending row is **replaced in place** by a refusal row stating the rule | no |
| connection lost | last known values | every server-owned value in the window takes `es-state-stale` with its age; a window-level connection bar appears (never a modal — `01-visual-language.md` §5.3) | no |

Timing, concrete:

- **0–120 ms (`DUR_FAST`): render nothing.** A pending badge that flickers on every fast round-trip trains the player to ignore pending badges, which destroys the one signal C4 depends on.
- **120 ms – 5 s:** `es-state-pending`, in-flight action named.
- **5 s +:** pending plus an age (`in flight 7s`).
- **Transport loss:** `es-state-stale` with the value's age, **not** `es-state-unknown`. The last confirmed balance is still a true fact about the past, and `01-visual-language.md` §2.2.8 reserves `unknown` for "never received, or invalidated". On reconnect the client **re-reads** the balance from the server; it never reconciles locally.

Three rules with teeth:

- **A pending row never moves the balance.** Optimistic UI is standard practice and it is wrong here for the reason `00-client-overview.md` §C4 gives: in a game whose anti-cheat model is "the server decides and the client cannot lie", showing an outcome before confirmation teaches that the client is the source of truth, then contradicts itself at the highest-stakes moment.
- **A refused spend never silently vanishes.** It converts to a refusal row that stays until dismissed. Copy follows `01-visual-language.md` §9.4's three-part form and distinguishes *refused* (a rule applied, nothing changed) from *failed* (something went wrong mid-flight, state may need reconciling) — different facts, different next actions, different words.
- **A refusal names its author.** "The server refused this transfer" and "the client could not reach the server" never collapse into one message.

### 3.3 The public ledger as an investigator tool

`../design/01-core-resources.md` §2.2 is Established and unusually explicit about intent: the public ledger is *"a gameplay feature, not blockchain flavor: it gives investigators — player and NPC — something to work with."* `01-visual-language.md` §8.5 draws the conclusion — *"it is not a pretty transaction list; it is evidence, and it must be usable as evidence."* This section is what "usable as evidence" means concretely.

**Two views, one table.** Tab 1: your own flows. Tab 2: the public ledger — everyone's, which is the whole point. Same columns, same row primitive, same filter grammar.

**Columns**, all sortable: `time · direction · amount · counterparty · type · traceability`. Transaction types come from `../architecture/06-data-model.md`'s `ledger_transactions.tx_type`: `mining_reward` · `trade` · `crack_seizure` · `raid_loot` · `payout_splitter` · `purchase`.

**Implementation.** `TableView` over a `SortedList` over a `FilteredList` over the observable transaction list. The documented binding is `sortedList.comparatorProperty().bind(tableView.comparatorProperty())` **(verified — TableView's own javadoc states this pattern)**. `TableView` is virtualized and cells are recycled **(verified — the `Cell` javadoc: cells are "recycled, or reused... This is what we mean when we say that these controls are virtualized")**, which means `updateItem` must fully reset every cell's state including its authority and traceability chips. A recycled cell showing the previous row's `no ledger entry` chip is not a cosmetic bug in an evidence surface.

**The filter bar is the investigator's tool** and its grammar is Unix (C6). It must be the same grammar `04` specifies for the command palette — one grammar, two surfaces (**RI-11**).

| Field | Values | Example |
|---|---|---|
| `from:` / `to:` | handle or DID | `to:did:plc:4kz…` |
| `amount:` | `>N` `<N` `=N` | `amount:>100` |
| `type:` | the six `tx_type` values | `type:crack_seizure` |
| `after:` / `before:` | time or date | `after:21:00` |
| `traceable:` | `yes` `no` | `traceable:no` |
| bare term | substring over counterparty and reason | `4c-11` |

Bare terms AND together; a leading `-` negates. Each parsed term becomes a removable chip. **An unparseable term is never silently dropped** — it renders as a free-text chip with a note, because silently reinterpreting a query is how a player concludes a counterparty has no transactions when in fact the filter did something else.

**Pivoting is the verb.** Click a counterparty → `identity` focused on that handle (`00-client-overview.md` §6.2's cross-window links). Right-click → *trace flows from this handle*, which rewrites the filter to that DID and clears the rest. Two pivots is what building a case looks like, and a filter that must be retyped between pivots is a filter nobody uses twice.

**Copy out.** Selected rows copy as TSV via `Clipboard.getSystemClipboard()` and `ClipboardContent.putString()` **(verified)**. Evidence gets shared out of band between players — that is the informant/evidence system working as designed (`../design/12-identity-and-social.md`) — and a ledger that can only be screenshotted is a ledger that cannot be cross-referenced. Never render a ledger row as an image; every field is selectable text (`01-visual-language.md` §8.5).

**Dead Drop and the gap in the record.** `../design/08-stealth-and-noise.md` §1 gives Dead Drop as the reputation-gated untraceable transfer; `01-visual-language.md` §8.5 gives it the `es-state-untraceable` row state and the `no ledger entry` chip. But `../architecture/06-data-model.md` records it as a row with `traceable = false`, *"still recorded, but obscured to investigators"* — which is a materially different UI. A **missing row** and a **row with an obscured counterparty** support different inferences: a gap in a sequence is itself evidence, and an obscured row announces that a laundering tool was used. This document assumes the latter (row present, counterparty `—` in the public view, full counterparty in the sender's own view, `no ledger trail` chip on both) and flags the reconciliation as **RI-4**. It must be settled before the ledger view is built.

**Never editorialise.** No green for credits — `01-visual-language.md` §2.2.2 gives the reason and it is a design one: `../design/03-economy.md` §3 states that an aggressive player runs near zero *by design*, so painting a positive balance as success argues against the intended shape of play. No net-worth headline. No EC leaderboard. `../design/08-stealth-and-noise.md` §3 confirms leaderboards exist (Ghost Protocol costs a leaderboard position), but whatever they rank, this client never presents an EC balance as a score.

### 3.4 I1 and I2 as interface rules

The two invariants most likely to be violated by a well-meaning screen. Both are stated in `../design/00-vision-and-pillars.md` §4 and both have a specific UI failure mode.

**I1 — compute is never purchasable with ethecoin.**

> **The compute domain and the ethecoin domain never share a commit surface.** A `CommitSheet` may charge EC *and* reserve cycles — every tool does both — but no sheet ever converts one into the other, and no outcome line ever takes the form `−X EC → +Y cycles`.

> **No surface may offer, advertise, preview, or disable-with-a-tooltip a compute purchase.** A greyed-out "buy cycles" control is *worse than absent*: it teaches the player that the concept exists and is merely locked, which is precisely the mental model I1 exists to prevent. The concept must not appear.

Concretely, `rig-monitor` contains **no market affordance at all** — no upgrade button, no store link, no "expand your rig" call to action. Its only reference to a capability increase is a read-only line naming the schematic that would raise the ceiling, rendered as an `es-gate-badge` with the Schematic glyph and its requirement in words (`Compute Cores · schematic · found: deep Eye infrastructure`), and **it does not link through to `market`**, because `market` sells nothing that raises it (`../design/11-rig-infrastructure.md`, opening rule: *"All rig infrastructure is schematic or story-milestone gated. None is purchasable."*). Navigation is the enforcement.

**I2 — ethecoin never buys a ceiling.**

> **No EC figure ever appears beside a ceiling stat.** Compute Cores, Thermal Budget, Bandwidth, Memory Buffer (`../design/11-rig-infrastructure.md` §2) and vault capacity (`../design/01-core-resources.md` §6, Invariant I12) render with a Schematic or Schematic+Reputation gate badge and **no price**.

One sanctioned exception, and it must be rendered as two lines, never one: `../design/03-economy.md` §4's `[PROPOSAL]` reconciliation, *"schematic unlocks, EC installs"* (tracked as **E-1**). Where it lands:

```
Cold Storage Expansion  (+4 vault slots, hard cap 16)
  unlock    Cold Storage Expansion schematic + Sickle standing: trusted   [ held ]
  install   150.00 EC materials
```

Never `Cold Storage Expansion — 150.00 EC`. The two-line form is the difference between "money installs a thing you earned" and "money buys capacity", and those are opposite sides of I2.

**The vocabulary rule that carries both** (R4): **`buy` / `price` / `purchase` are reserved for ethecoin-gated items. Ceilings use `unlock` and `install`.** No screen in this client may write "buy" next to a schematic. A word that cannot be written is an invariant that survives a screen being copied by someone who has not read this file.

**The consequence for EC-gated items** runs the other way and is worth stating because it is load-bearing for the loss economy: `../design/02-unlock-gates.md` §2.1 requires everything EC-gated to be **losable and replaceable**. So the item card's loss state (§6.4) shows the replacement path (`replaceable · 25.00 EC`) — which reads as consolation, and is in fact I2 doing its job. If losing an item felt like losing progression, it would be mis-gated.

---

## 4. Noise and heat

### 4.1 Two systems, and the interface may never blur them

| | Noise | Heat |
|---|---|---|
| Horizon | short; decays (`../design/01-core-resources.md` §3) | long; accumulated standing (§4) |
| Scope | pooled across the player **and all active bots** (§3.1) | `personalHeat` vs `serverHeat` (§4.1, §4.2) |
| Reads as | tactical — what am I doing right now | strategic — who am I to The Eye |
| Primitive | `es-gauge es-gauge-noise` — continuous, with a decay tail and threshold ticks | `es-chip` — banded, discrete, 5-pip indicator |
| Hue | violet | the band ramp: grey → yellow → orange → red-orange → red |
| Never appears without | a decay horizon | a scope label |

The separation is enforced four ways — **different primitive, different hue, different mandatory suffix, never in the same row**. Any one of those alone would be defeated by a hurried layout; together they make the confusion structurally hard. In the rig monitor (§2.1) they sit on consecutive lines below a divider, each with its own label, and there is no combined "exposure" readout anywhere in the client.

### 4.2 The noise readout

`es-gauge es-gauge-noise`. Format `43 · moderate` (`01-visual-language.md` §9.3), where the band words are exactly the five the tool tables use: `none` · `low` · `moderate` · `high` · `very high`.

```
noise · pooled                              43 · moderate
▐███████████▏░░░░░│░░░░░░░░░░░│░░░░░░░░░▌
              ↑decay tail      ↑60         ↑90
half-life ~90s
```

**Anatomy beyond the base gauge:**

- **The decay tail** (`-es-noise-decay`) is drawn behind the current fill as the **peak-hold envelope over the last 30 seconds**. The gap between the fill's right edge and the tail's right edge *is* the decay the player has earned by waiting. `01-visual-language.md` §2.2.3 says the tail is "what makes waiting a legible tactic"; peak-hold is the specific mechanism that makes it so, because it renders the thing the player did (nothing) as a visible quantity.
- **Threshold ticks** (`-es-noise-threshold`), 2px, each labelled with its numeric value. Crossing one is a **discrete event**: a log line appends to the window's stream (`threshold 60 crossed · Overflow Kit +18`) and the gauge takes `es-state-imminent` on the last threshold before a defender response. Never a gradient — `../design/01-core-resources.md` §3.2 makes thresholds a per-target-tier tuning table, and a smooth ramp would imply a continuous response function the model does not have.
- **The decay horizon is always present** in the label (`half-life ~90s`, the `[PROPOSAL]` figure from `../design/01-core-resources.md` §3.2). Without it the player cannot tell whether waiting is a plan or a hope.

**Pool attribution is mandatory, not an affordance.** Noise pools across the player and every active bot (`../design/01-core-resources.md` §3.1, `../design/10-botnets.md` §1: *"More bots, louder you"*). A pooled number with no decomposition makes the five-costs-to-one-benefit pressure `../design/10-botnets.md` §4 describes completely invisible. `Alt` or hover decomposes:

```
you                     26
Breacher #3             11
Recon #1                 6
Mimic #2                 —      decoy noise, attributed elsewhere
Miner #2                  0     [ isolated partition ]
                       ────
pool                    43
```

Two details that are easy to omit and expensive to omit:

- **Mimic frames** generate decoy noise *attributed to you elsewhere on the graph* (`../design/10-botnets.md` §2). That is a real contribution the player is paying 12 cycles for, and it must appear in the list with what it is doing, or the frame reads as inert.
- **An Isolated Partition bot appears in the list at zero, with a chip.** `../design/11-rig-infrastructure.md` §3 makes it *"extremely expensive, hard cap of one or two"* and the single exception to noise pooling. An expensive upgrade whose effect is the absence of a row is an upgrade the player cannot see working.

**Engagement noise is a second number and never replaces the pool.** `../design/01-core-resources.md` §3.1: *"Within an engagement, noise averages across participants as more join."* So during multi-party play there are two true quantities:

```
your pool     43 · moderate
engagement    27 · low        4 participants
```

Showing one is a bug that will read as a stealth tool malfunctioning. And per the same section, what each participant can see about the others scales with **network-graph hop distance** — so the participant list renders what the player actually knows and renders absence as `—`, never as a placeholder name or a blurred graphic. `es-state-unknown` and the `—` rule (`01-visual-language.md` §2.2.8) apply to other people exactly as they apply to balances.

### 4.3 The heat readouts

Two chips, never merged, scope label first and mandatory (`01-visual-language.md` §2.2.4):

```
PERSONAL · moderate   ●●●○○
SERVER   · high       ●●●●○
```

Five bands from `../design/04-mining.md` §4 — Zero, Low, Moderate, High, Named-hacker — mapped to `-es-heat-band-0` … `-es-heat-band-4`, with the 5-pip indicator as the mandatory redundant encoding.

**Where each one lives, and why they differ.**

| | `rig-monitor` + strip | commit sheets | `identity` | switcher badge |
|---|---|---|---|---|
| `personalHeat` | yes | yes, on any action that generates it | yes, with full consequence detail | on band change |
| `serverHeat` | **no** | **no** | yes, with world-state context | on band change |

Server heat is deliberately absent from the decision surfaces. `../design/01-core-resources.md` §4.2 makes it *the average Sickle activity across the population* — a number the player cannot move at the moment they are deciding anything. Putting an unmovable number next to a decision trains the player to filter out that region of the screen, and the filter does not distinguish between the two heats. So server heat lives where the player goes to *understand* rather than to *act*, and it announces itself on band changes, which are the only moments it is news. If playtesters never notice it at all, the fix is a louder band-change event, not a permanent readout (**RI-9**).

**The consequence is stated, never implied.** A band name a player cannot price is decoration. The personal-heat chip's popover states the two things heat actually does (`../design/01-core-resources.md` §4.4 — it gates *access*, never *ownership*):

```
PERSONAL · moderate

sweep chance    ~25 %/hr against your NPC-hosted network
                network-wide — losses are correlated, not attritional
reachable       black-market brokers: open
                respectable fixers:   closed
reduce it       lay low (passive decay) · self-mining is the zero-heat floor
```

The sweep percentage comes from `../design/04-mining.md` §4's table (2 / ~8 / ~25 / ~45 / ~60 %) and is server-supplied. The *"correlated, not attritional"* line is there because `../design/03-economy.md` §1.2 warns the variance is hidden and **OQ-3** tracks whether wipes read as unfair; a player who was told the loss model in advance has a different experience of the wipe than one who infers it afterwards.

**Heat never renders as a meter and never shows progress-to-next-band as a bar** (`01-visual-language.md` §2.2.4). But if the underlying model is a scalar, the player will want it — so the popover may show it **as text**: `personal heat 41 · next band at 60`. Text carries the precision without implying the band is a ramp, and it keeps trace as the only continuous red meter in the client.

**Named-hacker is a state, not a number.** `../design/01-core-resources.md` §4.1: it requires *"substantial reputation, not just heat — you have to matter to be hunted by name."* So the band-4 presentation names **both** conditions and which one is currently binding:

```
NAMED-HACKER
  personal heat        94   ✓ above threshold
  Sickle reputation   612   ✓ above threshold
  targeted pursuit active — Ghost Protocol is the only fast exit
```

A player at maximum heat with low reputation is **not** named, and a UI that implies heat alone does it will get them killed by a decision made on a wrong model.

**Invariant I9 gets an explicit zero.** Defending your own rig never generates heat. So every defensive `CommitSheet` — crack a discovered miner, arm a defense, run a scan on your own rig — carries the line `heat: none · defending your own rig`, per R3. An absent line reads as "not shown"; an explicit zero with a reason teaches the rule the design considers load-bearing.

### 4.4 Where the two legitimately meet

`../design/01-core-resources.md` §7 describes the loop: noise accumulates toward heat. That relationship appears in exactly one place — a sentence in `identity` next to the last band-change event — and never as a combined meter, a "heat forecast", or a projected band. A forecast would be the client modelling a server-owned accumulation function, and the player would plan against it.

---

## 5. Storage tiers and exposure

### 5.1 The question the window exists to answer

`../design/01-core-resources.md` §6 fixes three tiers on a strict capacity/exposure trade, and `00-client-overview.md` §6.1 draws the layout conclusion: **`storage` is organised by exposure, not by category**, because the three tiers *are* a risk gradient and sorting by item type would bury the only property that matters.

So the window answers one question at the top, in one glance: **what of mine is at risk right now.**

```
┌ STORAGE ────────────────────────────── ls across three mounts ┐
│                                                               │
│  AT RISK NOW      61 items · 12 tools · est. 1,830 EC to replace
│                   exposed while online 13 · always exposed 41 · socketed 7
│                   + 3 items with no EC replacement            │
│ ───────────────────────────────────────────────────────────── │
│  🔒 Encrypted Vault        4 / 6     never exposed            │
│     …4 item rows…                                             │
│ ───────────────────────────────────────────────────────────── │
│  🔓 Standard Storage      13 / 20    exposed while you are online
│     when you log off: safe                                    │
│     …13 item rows…                                            │
│ ───────────────────────────────────────────────────────────── │
│  ⛓ High-Hackable Zone     41 / 60    always exposed · raidable offline
│     when you log off: raidable                                │
│     …41 item rows…                                            │
│ ───────────────────────────────────────────────────────────── │
│  ⚙ Socketed into bots      7         mid-risk · out of the vault
│     when you log off: bots stop (I5) — see RI-5               │
│     …7 item rows, grouped by bot instance…                    │
│ ───────────────────────────────────────────────────────────── │
│  ◇ Honeypot Stash          9         decoy · junk · your own  │
└───────────────────────────────────────────────────────────────┘
```

**Five sections, in fixed order, always all present even when empty.** Three tiers plus two the design requires but the tier enum does not name:

- **Socketed.** `../architecture/06-data-model.md`'s `items.storage_tier` is *"null if socketed into a bot"*, and `../design/01-core-resources.md` §6 states that anything assigned to a bot **leaves the vault and becomes mid-risk**. Without a fourth section those items are invisible in the one window whose job is to say what is at risk, which would be the worst possible omission given `../design/10-botnets.md` §1a: bot loss destroys every socketed tool outright, *"no degraded-but-surviving state."*
- **Honeypot Stash.** `../design/09-defense-and-hardening.md` §1: a decoy High-Hackable zone containing junk, which *"raiders can't tell until extraction."* Your own decoy is labelled as a decoy **to you** and given its own section, because a decoy that looks real in your own inventory will eventually be treated as real by you. And the corollary, which is I14: **the client never renders a decoy marker on someone else's stash.** It has no such fact, and if it did, the mechanic would not exist.

Capacities are the `[PROPOSAL]` first-pass from `../design/01-core-resources.md` §6 — Vault 6 / Standard 20 / High-Hackable 60, Cold Storage Expansion adding +4, +3, +2, +1 to a hard cap of 16. The sub-linear *shape* is Established (Invariant I12); the numbers are for playtest.

### 5.2 The at-risk summary, and the rule for client-computed figures

`est. 1,830 EC to replace` is a **client-computed convenience figure** (R2) and it is allowed under three conditions, all mandatory:

1. It is prefixed `est.` and rendered `-es-fg-secondary`, never `-es-ec-fg`.
2. It counts **only EC-gated items**, because only EC-gated items have a replacement price at all (`../design/02-unlock-gates.md` §2.1).
3. **It names its own omission**: `+ 3 items with no EC replacement`.

Condition 3 is the interesting one, because it turns a limitation into the most useful line on the screen. The items with no EC replacement are the schematic-, reputation- and proof-of-skill-gated ones — the ceilings and the earned things — and "these you cannot buy back" is exactly the warning a player about to socket a tool into a bot needs. Invariant I2 makes the number *structurally* incapable of including a ceiling, so the honest disclosure and the invariant are the same sentence.

It never appears in a `CommitSheet` (§9), because a sheet is where the player commits against real prices and a `market` price is server-owned and gated. Tracked as **RI-10**.

### 5.3 Exposure is dynamic, and the risky state is the one you cannot see

The tier the player most needs to understand is `standardStorage`: *exposed while the owner is online*. But the player is, definitionally, online whenever they are looking at this window. The state that matters — what happens after they close the client — is the one state the UI can never be showing.

**Two mechanisms.**

**(a) The `when you log off:` line.** Every section carries it, permanently, in `-es-fg-secondary` at `TYPE_CAPTION`. Vault: `safe`. Standard: `safe`. High-Hackable: `raidable` — `../design/07-recon-tools.md` §1 makes Ping Sweep the tool that locates offline players' exposed stashes, and *the target is notified something pinged them*, so this is not hypothetical. Socketed: blocked on **RI-5**.

**(b) The sign-off summary.** When the player quits or signs out, an inline summary (not an application-modal dialog — `01-visual-language.md` §5.3 bans `Modality.APPLICATION_MODAL` outright) states exactly what remains exposed while they are away:

```
Signing off

  41 items remain raidable            High-Hackable Zone
   9 decoy items                      Honeypot Stash — raiders cannot tell
   7 socketed tools                   see RI-5
  ✓ Auto-Counter Daemon armed         18 c reserved
  ✓ 3 Canary Tokens placed
  5 deployed miners buffer for 4h     ~380 EC cap, then they produce nothing
  personal heat MODERATE              ~25 %/hr network-wide sweep chance

  [ Stay ]   [ Sign off ]
```

This is the highest-value screen in the game for making losses attributable. `../design/00-vision-and-pillars.md` §5 wants losing to feel like *"I got greedy"*; the mechanism is that the player was told, in specific numbers, immediately before the risk was taken. The buffer figures come from `../design/04-mining.md` §2.3 (4-hour cap per miner, ~380 EC for a five-miner T2 network) and the sweep chance from §4's table. Every line is a fact the server already owns.

### 5.4 Moving an item

**Every move is a server intent.** The item does **not** visually move until the server confirms. It stays where it is, takes `es-state-pending`, and names its destination (`→ Encrypted Vault`). The alternative — showing it in the destination greyed — was considered and rejected: an item rendered in two places at once is a worse lie than an item rendered in one place with a pending marker, and a move that snaps back on refusal is the client having claimed authority (C4).

**The confirmation rule, and it is a rule rather than a list:**

> **A move confirms if and only if it increases exposure.**

| Move | Confirms |
|---|---|
| vault → standard | yes |
| vault → high-hackable | yes |
| vault → socketed | **yes, and this one is the strongest** |
| standard → high-hackable | yes |
| standard → socketed | yes |
| anything → vault | no |
| unsocket → any tier | no |

The reason it must be a rule: confirmations that fire on safe actions get click-through-trained within an evening, and once trained the player clicks through the one that matters too. Confirming exactly on risk increase is what keeps the dialog meaningful. This is the same reasoning §9.3 applies to `CommitSheet` tiers, and it is the whole of this client's confirmation policy.

**Vault → socketed, in full.** `../design/01-core-resources.md` §6 (*"safety and productivity are mutually exclusive by design"*) and `../design/10-botnets.md` §1a (total loss, instance **and** every tool assigned to it) make this the most consequential inventory action in the game.

```
Socket Rainbow Table into Breacher #3

  leaves          Encrypted Vault  🔒 never exposed
  becomes         socketed · mid-risk  ⚙
  on bot loss     destroyed with the instance — total loss, no degraded state
  replace         60.00 EC   (Rainbow Table schematic: held, not lost)
  vault after     3 / 6
  noise           Breacher #3 pools into your noise — currently 43 · moderate

  [ Cancel ]   [ Socket ]
```

Note the fourth line does two jobs. It states the replacement price (I2: EC-gated things are replaceable, which is what makes the risk survivable) **and** it states that the schematic half of the split gate is not at risk (Invariant I11: bot loss destroys instances and socketed tools, never blueprints). A player's first fear on losing a Rainbow Table is that they lost the found capability; saying so *before* the risk is taken is worth more than saying it after.

**Bulk moves confirm once**, listing every item in a scrollable list with the destination stated once. Never N dialogs — the same principle as `00-client-overview.md` §C3's rule that a network-wide wipe presents as one event with one cause rather than eleven notifications.

**Drag-and-drop is supported and is never the only route.** JavaFX `startDragAndDrop` with `TransferMode.MOVE` handles the pointer case. WCAG 2.2 **SC 2.5.7 Dragging Movements (Level AA)** requires that *"all functionality that uses a dragging movement for operation can be achieved by a single pointer without dragging"* **(verified)**, and C5 independently forbids a time-critical control that requires a drag. So every move is also reachable by:

- selection + `Shortcut+Shift+V` (to vault) / `Shortcut+Shift+S` (to standard) / `Shortcut+Shift+H` (to high-hackable);
- a `move` verb in the row's context menu;
- the command palette (`04` owns the grammar).

Hit targets for move controls follow `01-visual-language.md` §4.3 and never fall below WCAG 2.2 **SC 2.5.8 Target Size (Minimum)**'s 24×24 CSS px at Level AA **(verified)**.

---

## 6. The item card

`01-visual-language.md` §8.6 fixes the anatomy and the state set for `es-item-card`. This section fills in sizes, forms, exact copy, and the states' rendering.

### 6.1 Three forms, one model

The same item appears in a vault list, a market listing, a loadout slot and a bot socket. Three renderings, derived from one model with one accessible name:

| Form | Size | Shows | Used in |
|---|---|---|---|
| **card** | 320×176 comfortable / 320×148 compact | full anatomy | detail panes, market, the compare view |
| **row** | 32 / 26 row height (`01-visual-language.md` §4.3) | name · class · gate chip · EC · compute · noise · tier chip · provenance chip · state | `storage`, `market` list, inventory table |
| **token** | 28px | glyph · name · compute | loadout slots, bot sockets, the `CommitSheet`'s at-risk line |

> **The row form omits the function sentence and the actions. It never omits a stat.** This is `01-visual-language.md` §4.4's density discipline generalised to a form change: compact removes space, never information, and the three economy-facing stats are information.

### 6.2 Anatomy, filled in

```
┌───────────────────────────────────────────────┐
│ Overflow Kit                     ⛊ unverified │  name · provenance chip
│ intrusion tool · bypass                       │  class
│ ┌─────────────────────────────────────────┐   │
│ │ ⌁ Proof-of-Skill                        │   │  gate badge — exactly one (I3)
│ │   Logic class, tier ≥ 3, live target    │   │  requirement in words
│ │                                [ met ]  │   │  verdict — server-rendered (C4)
│ └─────────────────────────────────────────┘   │
│   price      —            not EC-gated        │  ┐
│   compute    10 c         per use             │  ├ the three economy stats,
│   noise      very high                        │  ┘ equal weight
│   🔒 Encrypted Vault              1 use       │  tier chip · consumable/durability
│ Bypasses a puzzle layer entirely.             │  function, one sentence
│ [ equip ]   [ history ]   [ move… ]           │  actions
└───────────────────────────────────────────────┘
```

**Compute and noise carry equal visual weight to price.** `01-visual-language.md` §8.6 states the rule and `../design/06-intrusion-tools.md` §5 states the reason: compute gates *simultaneity*, noise gates *stealth*, EC gates *replaceability*, and those are the three real balance levers. A card that renders the price at `TYPE_TITLE_3` and the compute cost in a caption teaches the wrong economy — and this game's economy is the one where a 10-cycle tool on an 84%-loaded rig is unaffordable at any price.

**Noise is the band word from the tool table, never a number.** `../design/06-intrusion-tools.md` §1 and `../design/07-recon-tools.md` §1 price tools in bands — `None`, `Low`, `Moderate`, `High`, `Very high` — and `01-visual-language.md` §9.3 fixes exactly those five words. The card renders the band. The **scalar** appears only in the `CommitSheet` at the moment of use, where the server quotes the resolved value against the current pool (§9.2). Two different quantities; the card must not invent the second one.

**Exactly one gate badge, always** (Invariant I3). Split gates — Relay Chain, Rainbow Table, Cold Storage Expansion (`../design/02-unlock-gates.md` §1.1) — render **both components inside one badge**, ceiling component first, per `01-visual-language.md` §8.9. Two badges would read as two gates and would contradict I3 on the surface where players actually learn the gate system. `../design/15-open-questions.md` **P-12** notes the source docs never say which half classifies a split-gate item; the UI sidesteps it by never claiming a primary — it shows both requirements and the single verdict.

**Durability and consumables.** `../architecture/04-item-provenance.md` §2's `itemAttrs` example carries `"durability": 0.87`, but no design doc specifies a durability mechanic, thresholds, or a repair path. This document therefore renders durability **as text only** (`durability 87%`), never as a coloured meter, because a three-band meter would invent thresholds the design has not set and the player would plan against them. Consumables render `1 use` / `N uses`. Tracked as **RI-6** — either specify durability or drop it from the example payload.

**Provenance chip.** `verified` / `unverified` / `broken` per `01-visual-language.md` §2.2.9, opening §8's history view on click. Note that `../design/15-open-questions.md` **W-1** stubs external DID→key resolution, so **`unverified` is the common case today** and the card must render it quietly (§8.4).

### 6.3 The states

| State | Class (`01-visual-language.md` §8.11) | Rendering |
|---|---|---|
| owned, idle | — | full card on `-es-surface-raised`, level 1 elevation |
| equipped | `es-state-equipped` | 2px left rule in `-es-accent-emphasis`; `equipped` chip; the Memory Buffer slot number |
| socketed | `es-state-socketed` | 2px left rule; `socketed · Breacher #3` chip; **the tier chip is replaced by the mid-risk glyph** |
| at risk | *not a state class* | carried entirely by the **tier chip** — see below |
| gate unmet | `es-state-blocked` | card is **never hidden**; requirement in words; actions take `es-state-disabled` |
| pending | `es-state-pending` | hollow-ring authority badge naming the in-flight action |
| unknown | `es-state-unknown` | stats render `—`, never `0` |
| lost | *not a card state* | see §6.4 |

**"At risk" is not a state class, and that is deliberate.** Risk is a property of *where the item is*, and the tier chip already carries it with a mandatory glyph that survives greyscale (`01-visual-language.md` §2.2.6). Adding a parallel `at-risk` decoration would produce two encodings of one fact that can disagree — for instance on a socketed item, whose tier is null. One carrier, one truth.

**`es-state-socketed` is visually loud on purpose** (`01-visual-language.md` §8.6): "this item left the vault" is the risk decision the whole storage system is built around.

**A blocked item is never hidden.** `../design/15-open-questions.md` **OQ-2** flags five gates as possible cognitive overload, and the mitigation is that the answer to "why can't I have this" is always on screen. Hiding blocked items removes the answer and the question with it, which looks like simplification and is actually the removal of the progression system's only teaching surface.

### 6.4 "Lost" is a record, not a card

A destroyed item does not become a greyed card in the vault. It leaves the inventory and appears in a **loss record**. `../design/10-botnets.md` §1a is explicit that failure means total loss with *"no degraded-but-surviving state"*, and a ghost card in the vault is the client keeping a fiction alive in the one window whose entire value is being true.

What the player needs afterwards is not a tombstone but an account. One event, one surface (C3):

```
Breacher #3 destroyed                                   21:14:07

  engagement            defended target · tier 4 · undefended for 38s
  destroyed with it     Rainbow Table · Fuzzer · Port Sweep
  replacement           85.00 EC   (2 of 3 are EC-gated)
  RETAINED              Breacher blueprint — frames are never lost (I11)
  RETAINED              Rainbow Table schematic — the capability, not the item
  salvage               schematic contribution material ×1
                        tier 4 cleared the salvage threshold (I13)
  compute returned      22 c → recovering · 3m 10s at load 71% ×2.4

  [ rebuild Breacher · 90.00 EC ]   [ what happened ]
```

**Naming what survived is as important as naming what died.** The player's first fear on losing a Breacher is that a late-game schematic went with it, and `../design/10-botnets.md` §2 is explicit that if that were true, running one would be irrational. The loss record says so in two lines, and those two lines are the difference between a design that reads as harsh and one that reads as arbitrary.

The `compute returned` line matters too: 22 cycles come back on the Thermal Budget curve, at the current load, which is often the practical reason a bad session compounds. Attribution again (C3).

### 6.5 What a card may never do

- **Never a rarity colour or rarity tier.** No rarity system exists in this design. Inventing one would create a sixth progression currency next to the five gates, and `../design/02-unlock-gates.md` §4 says under **OQ-2** not to add a sixth gate class *"under any circumstances without revisiting this"*. A rarity ramp is a sixth gate wearing a colour.
- **Never a power score, DPS number, or aggregate rating.** `../design/06-intrusion-tools.md` §2 makes tools conditional by design — Rainbow Table is devastating against lazy targets and useless against salted ones — so any scalar rating is wrong on the merits before it is presumptuous.
- **Never a "recommended" or "best in slot" badge.** That is the client giving advice about a server-owned economy it does not model.
- **Never hide a blocked item** (§6.3).
- **Never render a price next to a ceiling stat** (§3.4, I2).

---

## 7. Inventory at scale

### 7.1 The actual scale, and the actual problem

Worst case from `../design/01-core-resources.md` §6's `[PROPOSAL]` capacities: 16 vault (hard cap) + 20 standard + 60 high-hackable + socketed tools across up to six bot frames ≈ **110 items**, plus consumable stacks. That is not a large collection by inventory-game standards — which means the problem here is **not** volume. It is that every item is priced in **three currencies at once** (EC, compute, noise) plus a gate, and the player's real question is almost never "where is my Fuzzer" but "what can I field on 15 available cycles without going above `low` noise".

That reframing drives the next decision.

### 7.2 The table is the default; the grid is the option

Most inventories default to a card grid. This one does not, and the reason is mechanical rather than aesthetic: `01-visual-language.md` §4.6 establishes that right-aligned values on a shared right edge are *"the only arrangement in which a column of numbers can be compared at a glance"*, and §3.5 establishes that JavaFX cannot enable tabular figures, so a monospaced face in a column is the only mechanism for aligned digits at all. A card grid puts every compute cost at a different x-coordinate. For an inventory whose whole difficulty is three-way cost comparison, that is the wrong default.

The grid remains available (`Shortcut+Shift+G`) because it is better for recognising an unfamiliar item and for the market's browse case.

**Default columns**, all sortable:

```
name · class · gate · EC · compute · noise · tier · provenance · state
```

**Sorting details that are real bug sources:**

- **Band words sort by ordinal, never lexicographically.** `none < low < moderate < high < very high`. A default string comparator produces `high < low < moderate < none < very high`, which is not obviously wrong on screen and is completely wrong in use.
- **`—` sorts last in every direction.** An unknown value is not a small value (`01-visual-language.md` §2.2.8).
- **Gate sorts by the five-gate order in `../design/02-unlock-gates.md` §1**, not alphabetically, because that order is the progression order players learn.

**Implementation.** `TableView` + `SortedList` + `FilteredList`, with `sortedList.comparatorProperty().bind(tableView.comparatorProperty())` **(verified)**. Cells are recycled **(verified)**, so `updateItem` must reset every piece of cell state — most importantly the **provenance chip**, since a recycled cell showing the previous item's `verified` badge is a false claim about a cryptographic check on the one surface where that claim carries weight.

### 7.3 Filtering — the Unix grammar

One filter field, `Shortcut+F` (`00-client-overview.md` §6.3). Grammar is `field:value`, bare terms AND together, a leading `-` negates. This is Unix (C6) and it must be the **same grammar** `04` specifies for the command palette's `ls` / `find` verbs — one grammar, two surfaces. If they diverge, C6's claim that the interface teaches real conventions is false (**RI-11**).

| Field | Values | Example |
|---|---|---|
| `tier:` | `vault` `standard` `high-hackable` `socketed` `decoy` | `tier:vault` |
| `gate:` | `ec` `schematic` `reputation` `skill` `heat` | `gate:schematic` |
| `class:` | `intrusion` `recon` `defense` `stealth` `rig` `frame` `consumable` | `class:recon` |
| `compute:` | `<N` `>N` `=N` | `compute:<8` |
| `noise:` | band word, or `<=` / `>=` a band | `noise:<=low` |
| `state:` | `equipped` `socketed` `blocked` `pending` | `state:blocked` |
| `prov:` | `verified` `unverified` `broken` | `prov:broken` |
| `risk:` | `exposed` `safe` | `risk:exposed` |
| bare term | substring over name and function sentence | `rainbow` |

Every parsed term becomes a removable chip. **An unparseable term is never silently dropped** — it renders as a free-text chip with a note. Silently reinterpreting a query is how a player concludes they no longer own something.

**Two presets earn dedicated buttons**, because they answer questions the design asks constantly:

- `risk:exposed` — §5's question, answered from the inventory side.
- `compute:<=N` where N is *current available* — "what can I field right now". This one reads live compute state, so it is written as a preset that expands to a literal value at the moment it is clicked (`compute:<=15`) and the chip shows the literal. A preset that silently re-evaluates would change what the player is looking at while they look at it.

### 7.4 Grouping

Group by: **tier** (default), class, gate, or provenance state. Group headers show count and a compute **range**, never a sum:

```
▾ intrusion tools   5 items    compute 2–14    noise low–very high
```

The range rather than a sum is a correctness rule, not a space-saving one. `../design/01-core-resources.md` §1.1 models tool compute as *"per-use, or reserved while equipped"* — two different models in one column. Summing across them produces a number that is neither the reservation nor the draw, and the player will treat it as the reservation. The real summation happens in the loadout view (§7.6), where the reservation model is known because the tools are actually equipped.

### 7.5 Comparing two tools

Select two rows, `Shortcut+D` (diff — Unix register, C6). A compare panel with two value columns, shared row labels, and a delta column. **Only differing rows are emphasised**; identical rows drop to `-es-fg-secondary`, so the eye lands on the differences without reading the table.

```
                  Fuzzer          Rainbow Table      Δ
gate              Ethecoin        EC + schematic     —
price             25.00 EC        60.00 EC          +35.00 EC
compute           6 c             8 c               +2 c
noise             moderate        low               −1 band
counters          Logic           Credential         —
storage           🔒 vault        🔓 standard        —
provenance        ⛉ verified      ⛊ unverified       —
```

Rules:

- **Deltas use U+2212 MINUS SIGN, never hyphen-minus** (`01-visual-language.md` §9.3).
- **Band deltas are ordinal** (`−1 band`), never numeric, because bands are ordinal.
- **The compare panel never declares a winner.** No "better" arrow, no highlight on the cheaper column, no score. `../design/06-intrusion-tools.md` §2 makes Rainbow Table *"a conditional power spike: devastating against lazy targets, useless against prepared ones"* — a client-side verdict would be wrong on the merits roughly half the time, and would substitute for the recon step (`../design/07-recon-tools.md` §3) that the tool exists to reward.

### 7.6 The loadout view

`../design/11-rig-infrastructure.md` §2 fixes the bound: **Memory Buffer** is equipped-tool slots, *"separate axis from storage: storage is how much you own, memory buffer is how much you can have readied at once."* So the loadout is N slots where N is Memory Buffer, and it is the surface where cost-before-commit (§9) applies to a whole configuration rather than one action.

```
LOADOUT                                    4 / 6 slots · Memory Buffer
  [ Port Sweep 2c ] [ Fuzzer 6c ] [ Overflow Kit 10c ] [ Passive Sniffer 3c ] [ + ] [ + ]

  reserved while equipped          0 c
  per-use if every tool fires     21 c
  ─────────────────────────────────────
  worst-case draw                 21 c    available 15 → −6      ⚠ over
  noise if every tool fires       very high    Overflow Kit dominates
  EC at risk if lost              85.00 EC    + 1 item with no EC replacement

  unsubmitted loadout · 2 changes          [ revert ]  [ apply ]
```

**Rules:**

- **Reserved and per-use are never added into one number without both being labelled.** Same reason as §7.4, and here it is load-bearing: an equipped Side-Channel Reader reserves nothing until it fires, while an armed Tarpit reserves 8 cycles continuously. One number would be wrong in both directions.
- **`worst-case draw` is the honest headline.** It is what the rig monitor would show if every equipped tool fired at once, and it uses the projection arrow form (`available 15 → −6`, R4/§2.3) with the over-capacity case shown rather than clamped.
- **The noise line names its dominating contributor.** `very high` on its own fails C3; `very high — Overflow Kit dominates` tells the player which slot to change. `../design/06-intrusion-tools.md` §2 calls the Overflow Kit *"a panic button with a siren attached"*, and the loadout view is where the siren should be audible before the panic.
- **`EC at risk if lost`** follows §5.2's rule exactly — EC-gated items only, omission named.
- **The loadout is draft state** (`00-client-overview.md` §1.1) until `[ apply ]`. It is therefore neither `confirmed` nor `pending`, and `01-visual-language.md` §8.11's state matrix — which is exhaustive by construction — has no class for it. Rather than invent a competing name, this document distinguishes drafts by a **panel-level label plus an explicit apply/revert pair**. If drafts turn up in three or more surfaces, add the class once instead of three workarounds (**RI-2**).

---

## 8. Item history and provenance

**Established, and one of the genuinely differentiating features in the product.** `../architecture/04-item-provenance.md` §6.1 records it as an explicit user requirement — *"the user explicitly wanted players to view an item's chain of events"* — and notes that because the chain is per-item, walking it is just following `prevRecordHash` back to genesis with no extra schema. §6.2 is the part that matters most: signatures stay **verifiable offline**, so *"a player's client can re-verify the whole displayed chain against the DID public keys without trusting the server's UI to have checked it."*

§8 of that document lists the client half as the one unbuilt item: `[ ] Item-history UI walking the chain (§6.1) — client, not started`. This section is that item's design. The verifier itself already exists — `protocol` `provenance/ProvenanceChainVerifier`, which *"does no I/O, reads no clock, and takes key resolution / issuer authority / duel committees as caller-supplied interfaces."*

### 8.1 A naming rule first

`../design/07-recon-tools.md` §1 gives the player a recon tool called **Provenance Tracer**, which audits *deployed miners* for hijacking and channel sabotage. It has nothing whatsoever to do with item provenance chains. Two unrelated mechanics share a word, and if the UI lets them look like one feature, the educational layer will be working around the confusion forever.

> **Rule:** the surface specified here is called **item history**. It is never labelled "provenance tracer", never uses the Tracer's iconography, and never opens from the `mining` window. The recon tool keeps its canonical glossary name unchanged. Tracked as **RI-12**.

### 8.2 The timeline

Opens from any item card's `[ history ]` action, and from the raid/loss/trade surfaces where an item changed hands. Newest at the top, walking back to genesis (depth 0).

```
┌ ITEM HISTORY — Rainbow Table ────────────────────────────────┐
│ ⛉ verified locally · 13 records to genesis · 41 ms · 21:04:33 │
│                                        [ re-check ]  [ raw ] │
│ ─────────────────────────────────────────────────────────────│
│ #12  ⚔ duel_grant            2026-07-24 18:04:00        ⛉    │
│      holder    did:plc:4kz…9qw   ← you                       │
│      issuer    duel:8f2c-…-11ab   3 of 5 validators, weight 4.2│
│      attrs     durability 0.87 (was 0.91)                    │
│      prev      sha256-9c2f…41ab                              │
│ ─────────────────────────────────────────────────────────────│
│ #11  ⇄ trade                 2026-07-22 09:17:41        ⛉    │
│      holder    did:plc:7mm…2rt                               │
│      issuer    did:plc:home-alpha#key1                       │
│      attrs     —  unchanged                                  │
│ ─────────────────────────────────────────────────────────────│
│ …                                                            │
│ #0   ✦ initial_mint          2026-05-02 11:00:12        ⛉    │
└──────────────────────────────────────────────────────────────┘
```

**Per-record fields**, drawn from the signed payload in `../architecture/04-item-provenance.md` §2:

| Field | Rendering |
|---|---|
| `chainDepth` | left gutter, mono `TYPE_MONO_CAPTION`, `#12`. Note this field is itself a `[PROPOSAL]` addition to the payload schema (§6.1) |
| `eventType` | `es-chip`: `initial_mint` · `server_grant` · `trade` · `duel_grant`, each with a glyph |
| `timestamp` | `HH:mm:ss` local; absolute date on hover (`01-visual-language.md` §9.3) |
| `holderDid` | mono, middle-truncated, full value copyable, click → `identity` |
| `issuerDid` | mono, middle-truncated. For duel outcomes it is the synthetic `duel:<duelId>` and the row expands to the signature list |
| `itemAttrs` | **the attributes as of this event, with changes from the previous record marked** |
| `prevRecordHash` | mono `TYPE_MONO_CAPTION`, middle-truncated, copyable |
| `nonce` | expanded raw view only |
| verification | per-record badge (§8.3) |

The `itemAttrs` diff is worth the implementation cost: `../architecture/04-item-provenance.md` §2 notes that `itemAttrs` *"carries the game-relevant stats, so the provenance record is the authoritative item definition, not just a receipt."* Which means the chain is simultaneously the item's ownership history **and** its stat history, and showing what changed at each hand-over turns a cryptographic artefact into something a player reads for gameplay reasons. A feature players use is a feature they will notice is honest.

**Primitive note.** The chain-event row is a **composition** of `es-log-line` (timestamp, glyph, source, message), `es-chip` (event type, verification) and `es-authority` — not a new primitive. `01-visual-language.md` §8 fixes ten primitives and downstream docs cite rather than extend. If `03` or `05` need the same row, it should be named there once rather than composed twice (**RI-3**).

### 8.3 The verification badge — the anti-cheat surface players actually touch

Two levels: a **chain-level** badge at the top, and a **per-record** badge on every row.

**The wording rule, and it is the most important sentence in this section:**

> The chain-level badge says **"verified locally"**, never "verified" alone.

`../architecture/04-item-provenance.md` §6.2's entire point is that the client checks *so it need not trust the server's UI*. If the badge does not say **who checked**, that differentiator is invisible and the player learns nothing from the one place where this game is genuinely doing something unusual. `01-visual-language.md` §2.2.9 already establishes the provenance tokens as the sole exception to C4 — *"client-computed and therefore trustworthy to display"* — and the copy is what makes the exception legible.

| State | Token | Chain-level copy | Detail |
|---|---|---|---|
| verified | `-es-provenance-verified` | `verified locally · 13 records to genesis · 41 ms` | the algorithm summary; when it ran |
| unverified | `-es-provenance-unverified` | `not verified · 3 keys unresolved` | **which** DIDs could not be resolved, and why |
| broken | `-es-provenance-broken` | `chain broken at depth 4` | **which** check failed |

**`broken` must always name the failing check**, and the set of failures is finite and enumerable, because `../architecture/04-item-provenance.md` §7 specifies exactly three checks (with a documented collapse for single-issuer records) and the implemented verifier resolved the ambiguities as **P-1 … P-7** in `../design/15-open-questions.md`. The complete copy table:

| Failure | Copy | Source |
|---|---|---|
| signature invalid | `depth 4 · signature does not verify against did:plc:…#key1` | §7 step 1 / single-issuer collapse |
| issuer not authorized | `depth 4 · issuer is not authorized to issue server_grant` | §7 single-issuer collapse |
| signer not in committee | `depth 7 · signer was not sampled for duel:8f2c-…` | §7 step 1 |
| unverifiable signature in a quorum | `depth 7 · one signature could not be verified — quorum rejected` | **P-2** (hard fault, not a discard) |
| quorum weight below threshold | `depth 7 · signature weight 4.2, threshold 5.1` | §7 step 2 |
| quorum count below threshold | `depth 7 · 3 signers, threshold 5` | **P-3** (count as well as weight, per I15) |
| chain link broken | `depth 4 · prevRecordHash does not match depth 3` | §7 step 3, **P-1** |
| bad genesis | `depth 0 · genesis is not an initial_mint` | **P-5** |
| non-contiguous | `chain does not reach genesis · missing depth 2–3` | **P-6** |
| replay | `depth 5 · timestamp precedes depth 4` / `nonce repeats depth 2` | **P-7** |

An implementer building this table is implementing the verifier's actual return values, which is the point: the UI's failure vocabulary and the verifier's failure vocabulary are the same list, so a new check cannot ship with no copy.

### 8.4 `unverified` and `broken` never share anything

`01-visual-language.md` §2.2.9 rules they may never share a colour, because *"'we haven't checked' and 'we checked and it's forged' are opposite facts."* This document adds a second rule: **they never share a verb, either.**

- `unverified` copy is in the first person, about **us**: `we could not resolve 3 signing keys`. Rendering: hollow shield, no fill, `-es-fg-secondary` body copy, no alarm.
- `broken` copy is about **the chain**: `the chain fails at depth 4`. Rendering: broken shield, `-es-provenance-broken`, `-es-status-bad-subtle` fill.

The reason this matters right now rather than later: `../design/15-open-questions.md` **W-1** stubs external DID→key resolution to resolve nothing, so *every* item that has crossed a server boundary is currently `unverified`. If `unverified` renders as alarming, players learn within an hour to ignore the badge — and then the first real forgery lands silently, on a surface that had been shouting wolf all week.

**And `broken` blocks nothing.** The client reports; it does not confiscate. `../architecture/04-item-provenance.md` §7 makes non-recognition a *federation* consequence — *"a chain that fails any check is not recognized"* — decided by servers, not by a view layer. A client that greyed out a broken-chain item would be enforcing a rule it does not own, which is C4's failure mode wearing a security costume.

### 8.5 Verification is an explicit, timestamped, offline-capable act

- **Runs on open**, and reports **when it ran and how long it took**: `verified locally · 13 records · 41 ms · 21:04:33`. Showing the cost is honest, and it is the teaching moment (C6): the player learns that verification is real work over real Ed25519 signatures, not a green tick someone typed.
- **`[ re-check ]` re-runs it.** Never on a timer — an automatic re-check would make the timestamp meaningless and would turn the badge into ambient decoration.
- **Offline is first-class, and it produces the sharpest distinction in this document.** With no server connection, item history still opens from the local cache and still verifies, because that is precisely what `../architecture/04-item-provenance.md` §6.2 buys. But two different facts are then in play:

  | Fact | Owner | Marker |
  |---|---|---|
  | *this chain is cryptographically valid* | the **client** — it checked | `-es-provenance-verified` |
  | *this is the current chain* | the **server** — and we have not heard from it | `es-state-stale` with an age |

  Rendered together: `⛉ verified locally · offline · records cached 14m ago`. The client can be **certain** the chain it holds is valid while being **unsure** it is the latest, and collapsing those two into one badge in either direction is a lie. This is C4 and its one exception coexisting on the same line, and it is the clearest available demonstration that the authority model is real rather than rhetorical.

- **The raw view.** `[ raw ]` shows the canonicalized payload JSON and the detached-JWS envelope: mono, selectable, copyable via `Clipboard` **(verified)**. Shipping it costs almost nothing and is the strongest possible expression of C4 and C6 together — `../architecture/04-item-provenance.md` §1 chose detached JWS over canonicalized JSON *specifically* to keep the payload *"human-readable/debuggable while remaining tamper-evident"*, and §4 chose JCS (RFC 8785) for the same reason. Exposing it means a suspicious player can verify a chain with an external tool and this client's claims can be independently checked. A client that says "trust me, I checked" and a client that hands you the bytes are different products.

### 8.6 Paging, and the mid-state that is easy to get wrong

`../architecture/04-item-provenance.md` §6.1 stores `chainDepth` so a client can request *"records N through N+20"* instead of walking from the tip every time. So the timeline loads the tip window first and pages back on scroll.

But **P-6** in `../design/15-open-questions.md` records that the implemented verifier *"requires a contiguous chain starting at genesis"* — a window not rooted at genesis proves nothing. Which produces a genuine mid-state the UI must render honestly:

```
⛊ not yet verified · 13 of 41 records · verifying to genesis…   ▓▓▓▓░░░░
```

- Records display as they load; the **badge stays `unverified`** until depth 0 is reached and checked.
- The progress readout is determinate (`13 of 41`), because an indeterminate spinner on a security check invites the player to assume it will finish clean.
- If the walk cannot complete — a missing range, an unreachable server while offline — the badge stays `unverified` with the reason, and **never** upgrades on partial success.

The failure to avoid is the obvious one: showing `verified` for the twenty records that happened to load. That is the client asserting a check it did not perform, on the surface whose entire value is that it does not do that.

---

## 9. Cost before commit

### 9.1 The rule, and its second half

> **Any action that spends cycles, generates noise, moves ethecoin, or risks an item shows its full, itemised cost on one surface before the player commits.**

That is the obvious half. The half that does the work:

> **The commit surface and the outcome surface are the same template.** Before: predicted. After: actual, in the same rows, in the same order, with deltas.

`../design/00-vision-and-pillars.md` §5 wants losing to feel attributable — *"I got greedy," not "the dice hated me"* — and `../design/05-hacking-minigame.md` §4 makes it a hard requirement for the trace specifically: the inputs must be legible so a loss reads as *"I was too loud"*. A shared template is how that becomes mechanical rather than aspirational: the player has a receipt, the comparison is pre-composed, and the client did not have to decide afterwards which facts were relevant.

### 9.2 `CommitSheet` — anatomy

Where it lives: **inline in the window that owns the action**, or a `ModalPane` scoped to that window (`01-visual-language.md` §5.2 rule 4). `Modality.APPLICATION_MODAL` is banned outright (§5.3) — a dialog that blocks the rig monitor while the player decides how many cycles to spend is a design failure, and this sheet is *about* cycles.

Fixed order. Every line always present; a zero renders as `none` with its reason (R3).

```
Run Thorough Scan                                       audit · your own rig

  ethecoin        none
  compute         35 c · spent, recovers
  recovery        returns in 27m       load 84% ×4.6      loaded
  noise           none · scanning your own rig
  heat            none · defending your own rig (I9)
  at risk         nothing
  duration        ~6 min
  finds           all miners, including rootkit-wrapped
  irreversible    nothing

                                       [ Cancel ]   [ Run scan ]
```

| Line | Format | Notes |
|---|---|---|
| ethecoin | `−45.00 EC` or `none` | two decimals always (P-8) |
| compute | `N c` **plus the reservation model** | `35 c · spent, recovers` vs `8 c · reserved while armed` vs `3 c · permanent while the miner runs` — three different futures, and the words are the only thing distinguishing them |
| recovery | time at current load + the multiplier | §2.3. Live: it updates if the player frees cycles in another window before confirming |
| noise | band + the **scalar the server quotes** + the resulting pool | `high · +18 to pool (43 → 61)` |
| heat | band delta or `none` + reason | I9's explicit zero (§4.3) |
| at risk | the items that could be lost, as tokens (§6.1) | `Rainbow Table · Fuzzer` |
| irreversible | the named forfeiture, or `nothing` | `forfeits block progress · 1.4 EC` (`../design/04-mining.md` §1.3) |
| gate | `es-gate-badge`, verdict server-rendered | only when a gate applies |

**The recovery line being live is not a detail.** It is the mechanism by which §2.3's projection reaches the moment of decision: a player looking at `returns in 27m · load 84% ×4.6` can disarm a Tarpit in the defense window and watch the sheet read `returns in 11m · load 76% ×2.9` without losing their place. That is the Thermal Budget being *felt* rather than described.

**The confirming button carries the verb**, never "OK". Nothing destructive is default-focused. After commit, an `awaiting server confirmation` line appears **in place** — the sheet does not close on click, because closing implies the outcome (C4).

### 9.3 Which actions get a sheet — the tier rule

Confirmation fatigue is the failure mode that destroys confirmation, and it is caused by confirming safe things. The same reasoning as §5.4's exposure rule:

| Tier | Actions | Surface |
|---|---|---|
| Free and reversible | equip from vault, filter, sort, open a window, move **toward** safety | none |
| Costs cycles or noise, reversible | arm a defense, allocate to self-mining, run a scan, add a relay hop | **inline cost strip** on the control itself, always visible; one click commits |
| Costs EC, increases exposure, or risks an item | buy, socket into a bot, move up a tier, deploy a miner, start a breach | **`CommitSheet`** |
| Irreversible with a named forfeiture | abort a breach, kill a deployed miner, crack a foreign miner, install Ghost Protocol, abandon a faction | **`CommitSheet` + a second, specific acknowledgement** |

**The second acknowledgement is not a generic "are you sure".** It is a click on **the specific forfeiture line**, which acts as a checkbox:

```
  ☐  I understand: this forfeits 87.50 EC of buffered yield
```

One extra click, landing on the exact fact that matters, rather than a modal the player learns to dismiss. `../design/04-mining.md` §5 makes the four responses to a discovered miner — Kill, Crack, Hijack, Sabotage — *"core game content, not an edge case"*, and each forfeits something different; a generic confirm would flatten a four-way decision into a yes/no.

**Time-critical actions confirm by key repeat, never by mouse.** `00-client-overview.md` §6.3 binds `Shortcut+.` to abort and states that it always confirms, because `aborted` is a persisted outcome with real consequences (`../design/05-hacking-minigame.md` §4). Under a running trace timer, a mouse-targeted dialog is exactly the reflex test C5 forbids. So:

> **`Shortcut+.` pressed once shows `press again to abort · 2s` in the breach window's status line. A second press within 2 s aborts. No pointer, no focus change, no modal.**

Ghost Protocol and faction abandonment are not time-critical and keep the acknowledge-the-forfeiture click. `01-visual-language.md` §5.3 already names both as actions that confirm inside their own window.

### 9.4 The outcome surface mirrors the sheet

```
Thorough Scan complete                                  21:10:41

                    predicted        actual        Δ
  compute           35 c             35 c          —
  recovery          27m              31m 12s       +4m 12s   load rose to 87%
  noise             none             none          —
  heat              none             none          —
  found             —                1 miner       rootkit-wrapped · T2 · node 4c-11
```

Rules:

- **Predicted values are never rewritten to match actuals.** Keeping a wrong prediction on screen is what makes the game's model learnable — and `+4m 12s · load rose to 87%` teaches more about Thermal Budget than any tooltip.
- **Every non-zero Δ states its cause** (C3). A delta with no explanation is the "the game decided" experience with extra decimal places.
- **In-game consequences are not errors** (`01-visual-language.md` §9.4). A failed breach, a swept network, a destroyed bot get outcome surfaces, never error dialogs.

### 9.5 What cost-before-commit must never become

- **Never a wall of numbers on a free action.** §9.3's tiers exist so the sheet keeps its meaning; a sheet on every click is a sheet on nothing.
- **Never a client-computed affordability verdict** (C4). The sheet shows the cost; the server returns `refused` with the rule it applied.
- **Never a recommendation, an efficiency score, or an "optimal loadout".**
- **Never omit a zero** (R3).
- **Never present an EC cost and a compute gain on the same sheet** (§3.4, I1).

---

## 10. Open questions

Deliberately undecided here. Prefix `RI-`, chosen to avoid collision with the existing `OQ`/`P`/`D`/`S`/`N`/`E`/`A`/`G`/`W`/`Q` prefixes in `../design/15-open-questions.md` and with `CL-` (`00`), `V-` (`01`) and `PN-` (`02`). Log in `../design/15-open-questions.md` §2 if this doc set is adopted.

- **RI-1: does `unaccounted` compute deserve its own token?** §2.4 renders cycles with no owning consumer as `-es-compute-allocated` + `es-state-unknown`, because the hue budget is spent (`01-visual-language.md` §2.5) and because a distinctive colour would make the rootkit tell easier to spot than `../design/04-mining.md` §3.3's *"nothing announces itself"* intends. But this is the single most gameplay-significant thing the compute gauge can show. Decide together with the `audit` window in `03`, and note it is entangled with **P-9** (`ComputeConsumer` has no name for a foreign miner's host-side draw).

- **RI-2: does the state matrix need `es-state-draft`?** §7.6's unsubmitted loadout is client-owned draft state (`00-client-overview.md` §1.1) and is neither `confirmed` nor `pending`. `01-visual-language.md` §8.11 is exhaustive by construction, so this doc used a panel-level label instead of inventing a class. If drafts appear in three or more surfaces — loadout, allocation, an unsent transfer — add the class once rather than accumulating three workarounds.

- **RI-3: does the chain-event row earn an eleventh primitive?** §8.2 composes `es-log-line` + `es-chip` + `es-authority`. If `03` (federation surfaces) or `05` need the same structured signed-record row, name it in `01-visual-language.md` rather than composing it twice.

- **RI-4: Dead Drop's ledger visibility.** `../design/01-core-resources.md` §2.2 says a Dead Drop transfer moves value *"without a traceable ledger entry"*; `../architecture/06-data-model.md` models it as a row with `traceable = false`, *"still recorded, but obscured to investigators"*. Those are different UIs supporting different inferences — a **gap in a sequence** is itself evidence, while an **obscured row** announces that a laundering tool was used. §3.3 assumes the obscured-row reading. **Settle before the ledger view is built**; it changes what the investigator gameplay in `../design/12-identity-and-social.md` can actually do.

- **RI-5: on log-off, what happens to tools socketed into bots?** Bots run online-only (Invariant I5) but `../design/10-botnets.md` does not say whether socketed tools return to storage between sessions or stay socketed and mid-risk. §5.3's sign-off summary cannot be written until this is answered, and the answer materially changes the offline at-risk total — which is the number that determines whether a player logs off comfortably or not at all.

- **RI-6: does durability exist as a mechanic?** `../architecture/04-item-provenance.md` §2's `itemAttrs` example contains `"durability": 0.87`, but no design doc gives a durability model, thresholds, or a repair path, and no tool table has a durability column. §6.2 renders it as text only, deliberately refusing to invent bands. Either specify it (and it becomes a new EC sink worth pricing against `../design/03-economy.md` §4) or drop it from the example payload before it is implemented as real.

- **RI-7: what does standing a bot down cost?** §2.2's legend offers `[ stand down ]` on a bot-frame segment — the most useful reclaim affordance on the compute gauge, since a Breacher is 22 cycles. `../design/10-botnets.md` §2 prices *building* an instance (90 EC for a Breacher) but never says whether standing one down refunds it, preserves it, or destroys it. The affordance cannot ship until this is known, and the answer changes how overextension is escaped.

- **RI-8: thermal band names and boundaries.** §2.3 proposes four bands (`lean` / `working` / `loaded` / `saturated`) anchored to the two data points in `../design/01-core-resources.md` §1.3. Both the count and the boundaries are balance values and belong to the server and to a tuning pass. The client contract is only: a band name from a fixed enum, a load percentage, and a recovery multiplier — confirm that is what the wire will carry.

- **RI-9: where does server heat actually belong?** §4.3 confines it to `identity` plus a band-change event, on the grounds that a number the player cannot move does not belong next to a decision they are making. `../design/01-core-resources.md` §4.2 wants it to create real social pressure — *"other players' recklessness is your problem"*. If playtesters never notice it, the fix is a louder band-change event and a world-state surface, not a permanent readout on the rig strip.

- **RI-10: the estimated-replacement figure.** §5.2 and §7.6 show a client-computed `est. EC to replace`. It is genuinely useful, it obeys R2's labelling rules, and it is still a number the server never sent. If it drifts from real `market` prices — which are server-owned, gated, and stubbed today (**W-3**) — delete it rather than caching prices client-side.

- **RI-11: one filter grammar, two surfaces.** §3.3 and §7.3 specify field names (`tier:`, `gate:`, `noise:`, `prov:`, `risk:`, `amount:`, `traceable:`) that `04` will also need for the command palette's `ls` / `find` verbs. `04` owns the grammar; these tables must be reconciled into it rather than forked. If they diverge, C6's claim that the interface teaches real conventions is false in the most visible possible way.

- **RI-12: the "provenance" naming collision.** §8.1 rules that the item-history surface is never called a provenance tracer, because `../design/07-recon-tools.md`'s **Provenance Tracer** audits deployed miners and has nothing to do with signed item chains. Confirm the recon tool keeps its glossary name, or rename one of the two. Two unrelated mechanics sharing a word is a teaching failure the educational layer will be working around for the life of the product.
