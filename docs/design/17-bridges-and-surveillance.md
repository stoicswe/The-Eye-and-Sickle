# 17 — Bridges, MonJob, and the Tracer

**Status: [PROPOSAL]**, except §1, §2, §3 and the derivation half of §4, which are **implemented** as of 2026-08-07.

The world is already multi-server: `HostKind.BRIDGE` exists, `TopologyGenerator` links servers across
one, `ServerState` records depth from home, and `Sighting.bridgePeerServerName` can name the far side.
What was missing is any reason to *care* about a bridge, and any way to watch one.

This doc adds three things on top of that structure — a reason bridges are worth finding (§1–2), a way
to watch one (§3), and a way to answer being watched (§4) — plus the defence against the answer (§5).

> **The rule every section here is written against.** Invariant **I2**: *ethecoin never buys a
> ceiling, only breadth.* `NetRules.hopCeiling` is 1, or 2 with the Topology Mapper schematic, and
> **takes no sweep tier**. Crossing to another server is repositioning — breach, foothold, `connect`,
> sweep again from there — and is earned, never bought. Nothing below changes that, and every section
> says how it avoids doing so.

---

## 1. Sweep tiers and bridges — **implemented**

| Tier | Gate | Finds | Bridges? |
|---|---|---|---|
| **BASE** | starting kit | the loud furniture of a network, unreliably the quiet machines | **no** |
| **WIDE** | 25 EC | the same distance, listened to harder | **yes** — 0.95 |
| **DEEP** | 55 EC | near-certain on infrastructure | **yes** — 0.99 |

### 1.1 ⚠ What changed, and why it is not an I2 violation

Bridges are `SignalStrength.HIGH`, so `Balance.netSweepBase` gave them **0.85 at tier 1**: the way
*out* of a server was the single most reliable thing the free starting instrument could see. Finding
the exit was easier than finding anything worth taking.

`Balance.NET_SWEEP_BRIDGE_MIN_TIER` (2) now gates them, applied by
`HostArchetypes.detectableBySweep` as a **hard gate on candidacy** — `netSweepBase`'s own note already
described this class of gate ("a hard gate has already decided candidacy").

The gate moves **which kinds a tier can hear**, never **how far it hears**:

- The host is inside the hop ceiling either way. A base sweep is standing in exactly the same place as
  a wide one and simply does not hear this kind of machine.
- `hopCeiling` still takes no tier. A wide sweep buys *knowing the door is there*; it buys nothing
  about reaching the far side.
- So ethecoin bought **sensitivity**, which `02` §1.1 puts on ethecoin. It did not buy a ceiling.

`BridgeVisibilityTest.ReachIsUnchanged` is the half of that file that holds the line, and it is the
assertion to keep above all the others here.

### 1.2 ⚠ Gateways and relays are deliberately not gated with bridges

A gateway is a **signpost** — host index 0, no loot, its whole function is to be the first thing found
on a server. Hiding it would leave a base sweep finding nothing but quiet desktops on a server the
player has already reached. What is withheld is specifically the *way onward*.

### 1.3 ⚠ Monotonicity is a required property

A test asserts `detected(T1) ⊆ detected(T2) ⊆ detected(T3)` from one vantage: a player who buys a
better instrument must never lose a contact they already had. A gate that only ever **adds** kinds as
the tier rises preserves that by construction — which is why this is a **floor**, not a band. Gating a
kind *out* at a higher tier would break it.

---

## 2. Bridges take no deployed work — **implemented (as a predicate)**

`HostArchetypes.acceptsDeployedWork` refuses `BRIDGE`.

A bridge is a **router, not a computer**. Invariant **I6** is what makes that matter mechanically — a
deployed miner consumes the *host's* compute — so a bridge that accepted one would have to be modelled
as having a cycle budget, and every argument for reaching a further server would become an argument
for parking a miner on the way there.

> ⚠ **Nothing calls this yet, and it is stated rather than hidden.** There is no player deploy action
> in the engine: `NodeState.deployedMiners` is read in six places and written by none, because bots
> are `10-bots-and-automation.md` and deliberately unbuilt. A rule with no caller is exactly how
> `reconcileFootholds` stayed broken for weeks — **the deploy action must call this on the day it is
> written**, and `BridgeVisibilityTest.bridgesTakeNoWork` is what gives the rule something to be wrong
> against meanwhile.

---

## 3. What is behind a bridge — **implemented**

A bridge should be able to tell you **how many machines hang off its far side, and what that server is
called** — and *nothing else*. No addresses, no kinds, no tiers, no values.

### 3.1 It is a port-scan finding, not a sweep result

This is the decision that keeps it I2-safe and keeps the map honest:

- `NetRules` holds that *"undiscovered hosts do not exist in `knownNodes`, and the map draws nothing
  where they are. **No placeholder, no count**, no 'three contacts nearby'."* A count published by a
  **sweep** would be a number about machines nobody has found, which is what that rule forbids.
- A count attached to **a bridge you have already discovered and paid to scan** is a different object
  entirely: it is a property of a machine in your files, like its firewall tier. That is what a port
  scan is for, and the report already stores per-finding `learnedAt`.
- It is information you **cannot act on**. You still cannot see, scan, or reach anything behind the
  bridge. Reach is unchanged, so I2 is unchanged.

### 3.2 ⚠ THE INSERTION TRAP — read this before adding the rung

`PortScanTarget` is a strict order, depths 1–8, and **`PortScanRules` prices `depth − 1` steps**. That
indirection exists because `IDENTITY` was added to the bottom after the other seven were calibrated;
keying on `depth` directly would have raised the cycles, duration, noise *and* detection risk of all
seven as a side effect of inserting one below them, invisibly, with every screen still rendering.
`IdentityFindingTest.theCalibratedRungsAreUnchanged` pins the seven literally.

So a `PEERS` rung must satisfy three constraints at once:

1. **It must not re-price the existing eight.** Adding at the *bottom* pushes `IDENTITY` from depth 1
   to depth 2 — one step, and therefore a real cost rise on the cheapest rung in the game. Adding at
   the *top* (depth 9) leaves all eight untouched, and is the safe insertion point.
2. **It must not shrink every other machine's report.** ⚠ **This is the sharp one.**
   `NodeReports.known` returns `found / PortScanTarget.values().length`. A ninth rung that only exists
   on bridges would cap **every ordinary machine in the game** at 8/9 = 0.889 — and `known` feeds
   `Balance.breachProtocolShare`, so the breach-puzzle weighting would silently fall for every target
   a player ever scans. Nothing would render wrong. The fix is for `known` to divide by the rungs
   **applicable to that host**, so a non-bridge stays 8/8 and a bridge is 9/9.
3. ✅ **RESOLVED — bridges get their own applicable set, not a longer universal ladder.**
   `PortScanTarget.appliesTo(HostKind)`. A bridge's findings are `IDENTITY`, `FIREWALL`, `OS_VERSION`,
   `PEERS` and `MONITORED`; vault, downloads and cycle rungs do not apply to it. Everything that is
   not a bridge keeps exactly the calibrated eight and gains nothing.

   ⚠ **This fixed a wart that was already there.** `NodeReports.write` had no kind-gating at all, so a
   port scan of a router dutifully recorded a downloads folder, a high-risk vault count and a
   medium-vault estimate. Nothing failed; the numbers were simply about things a bridge does not have.

   ⚠ **`PEERS` sits at depth 4 and `MONITORED` at 5 — sharing with `CYCLE_CAPABILITY` and
   `CYCLE_LOAD`.** Depth is an *order*, and applicability decides which rung a given machine has at
   that order, so the peer count costs a depth-4 scan rather than being the dearest finding in the
   game. That is only safe because **within any one kind the depths are unique** — a bridge has
   `PEERS` and no `CYCLE_CAPABILITY`, everything else the reverse — which
   `MonJobsTest.depthsAreUniqueWithinAKind` pins.

   ⚠ **`NodeReport.total()` had to stop being static.** It returned `PortScanTarget.values().length`,
   which was only accidentally right while the ladder was universal; a bridge would have displayed
   "3 of 10 findings" while having five. It takes the report's own kind now.

## 4. MonJob — **[PROPOSAL], not implemented**

A monitoring job left on a bridge. It is the first thing in the game that watches a machine you do not
occupy, and it is what makes holding a bridge worth anything.

### 4.1 Tiers

| Tier | On interaction | Cost to the watcher |
|---|---|---|
| **1** | notifies you that *someone interacted with this bridge* | — |
| **2** | notifies you, **and** adds 1–3 nodes of the intruder's approach path to your map | **the intruder is told they were Watched** |

Tier 2's tell is the whole design. It buys real intelligence — a partial route back toward the other
party — and pays for it by giving that party a reason to come and find you. A watcher who wants to
stay invisible stays on tier 1.

⚠ **What a Watched party learns is exactly one fact: which bridge holds the MonJob.** Not who placed
it, not what tier, not what it saw. Turning that one fact into an identity is the Tracer's job (§5),
which is what makes the Tracer worth buying.

### 4.2 ⚠ Not removable by anyone else

Stated in the brief and worth pinning, because it is unusual: an artifact on a machine somebody else
can stand on that they cannot delete. The justification has to be that a MonJob is *not on the
bridge's filesystem* — it is a route the bridge itself keeps, which is why bridges and only bridges can
carry one. If it were a file, `Repac.delete`'s rule ("only files the rig actually stores") and
`AccessLog`'s ("blank the line, never delete it") would both have opinions.

✅ **MJ-1 — RESOLVED 2026-08-07: the owner can remove their own.** Removal is the only operation the
placer gets, and nobody else gets any.

⚠ The asymmetry is the whole rule and it is worth stating as one sentence: **a MonJob answers to the
player who placed it and to nobody else.** Without owner-removal a player would permanently pollute
every bridge they ever touched — including their own route home — and the world would fill with dead
MonJobs nobody could clear, which is a mess with no counterplay rather than a risk with one.

⚠ Removal must be **silent to everyone else**. A watched party learning that a MonJob had been *taken
off* a bridge would hand them the one thing §4.1 withholds — that somebody was watching, and has
stopped — which is a confirmation they could not otherwise buy.

### 4.3 Interactions to settle before building

- ⚠ **Does a MonJob generate heat for the watcher?** **I9** says defending your own rig never
  generates heat — a bridge is not your rig, so I9 does not cover this. Recommend **no heat on
  placement, no heat on firing**: a passive listener that made noise would be self-defeating, and the
  tier-2 tell is already its cost.
- ⚠ **One per bridge, or one per player per bridge?** Per player, or the first player to reach a
  bridge locks everyone else out of the mechanic.
### 4.4 ✅ MJ-2 — RESOLVED 2026-08-07: NPCs place them, and density scales with distance

**Rare on bridges near the player, commoner the further out they reach.** This is what stops the whole
system being multiplayer-only content — in solo, NPC MonJobs are the only ones there are — and it is
the cheapest way to make distance *feel* different rather than merely cost more.

#### ⚠ Zero at home, as its own named constant

`Balance` already does exactly this one field along, and the reasoning transfers verbatim:

> `NET_COUNTER_HACK_HOME = 0.0d` — *"Stated as its own named value so a re-tune of
> `netCounterHackChance` cannot make it non-zero by accident. **A player who has never left home is
> never counter-hacked**: the home server is where the game teaches, and a teaching space that
> occasionally plants a parasite on the student is a teaching space they learn to avoid."*

So `MONJOB_DENSITY_HOME = 0.0`, declared separately for the same reason. The first bridge a player ever
crosses is clean, always. The mechanic is introduced by reaching out, never by being ambushed at home.

#### ⚠ The same curve shape as counter-hack, not a second unrelated one

`netCounterHackChance` is `0.00 / 0.04 / 0.10 / 0.18 / 0.28` by depth. MonJob density should be the
same *shape* — flat at home, accelerating outward, flattening at the top — so the two read as one
escalation rather than two systems that happen to both notice distance. Proposed, anchored to it:

| depth | counter-hack | MonJob density |
|---|---|---|
| 0 (home) | 0.00 | **0.00** |
| 1 | 0.04 | 0.10 |
| 2 | 0.10 | 0.28 |
| 3 | 0.18 | 0.50 |
| 4+ | 0.28 | 0.70 |

Density is much higher than counter-hack chance at every depth on purpose: **being watched is not
being attacked.** A MonJob costs the player nothing directly; it is the thing that makes the *later*
attack legible. Tuning it as though it were a hazard would make deep play unbearable.

#### ⚠ DERIVED BY HASHING, NEVER DRAWN — this one is a trap with a history

`TopologyGenerator`'s draw count is a pure function of the world's shape, so **one draw per bridge
would re-roll every existing world**. `NpcNames` hit this exact wall and records the fix — hash the
address, do not draw — and notes it was *"the second time this trap has bitten here"*. It also has to
consume no RNG, or `SweepDeterminismTest`'s draw-count assertions break.

⚠ And `NetRules`' rule applies with full force: *"Detection is a roll made once, at world generation,
and stored. Nothing here draws for detection, ever."* Whether a bridge carries an NPC MonJob is a
property of the world, fixed before the player arrives — so scouting the same bridge twice can never
produce two different answers.

#### ✅ MJ-3 — RESOLVED: the tier mix ramps too. Density alone would teach nothing.

Density alone does not tell the player anything, because **a tier-1 MonJob is invisible to the
intruder by design** (§4.1). A player who crosses ten distant bridges watched by ten tier-1 jobs
learns nothing at all — and then gets counter-hacked, with no visible cause. That is precisely the
failure `NetRules` names for sweeps: *"a mechanic that punishes without explaining is indistinguishable
from a bug."*

**Decided: the tier mix shifts toward tier 2 with depth, not just the density.**
`Balance.monJobTierTwoShare` runs `0 / 0.15 / 0.30 / 0.45 / 0.60`, so the chance of being watched
*and told* is about `0 / 1.5% / 8% / 23% / 42%`. Two reasons, and the second is the fiction rather
than the mechanics:

- It is the only thing that makes distance-risk **learnable without a Tracer**. Tier 2 tells the
  intruder *"you were Watched"* and which bridge — so a player who pushes out gets told, in words, that
  the network noticed. That is the signal the escalation needs, and it arrives before the reprisal
  rather than after it.
- Distant NPCs are described as *aggressive* (§6), not cautious. Tier 2's cost is that it reveals the
  watcher — which a cautious operator minds and an aggressive one does not. Deep in the network things
  notice you and do not care that you know.

⚠ **This also gives the Tracer its reason to exist.** Tier 2 hands the player one fact — which bridge —
and §5 is the only way to turn it into an identity. If far bridges were mostly tier 1, the Tracer would
have almost nothing to read.

✅ **MJ-4 — RESOLVED 2026-08-07: yes, and it says "monitored" and nothing else.**
`PortScanTarget.MONITORED`, depth 5, bridges only. Never whose, never what tier.

⚠ **That restraint is what lets the finding exist at all.** A tier-1 MonJob's whole value is that the
intruder does not learn they were seen; reporting the tier would make tier 1 worthless and nobody
would place one. What the player buys is that crossing *would* be seen — which turns distance-risk
into a decision rather than a blind tax, and makes a MonJob deter even when it never fires. Monitoring
that catches the careless and warns off the careful is what monitoring actually does.

⚠ **Tri-state, never a boolean**: `-1` never looked, `0` looked and clear, `1` watched. A boolean
collapses the first two, so an unscanned bridge would report as clean — the one wrong answer that
reads as reassuring.

⚠ **One rule for NPC and player MonJobs.** Only the NPC half is derivable today; when player-placed
jobs land in the save they are OR-ed in at `PortScanRules.monitoringOf` and nowhere else, so the
finding cannot come to mean two different things.

---

## 5. The Tracer — **[PROPOSAL], not implemented**

A permanent tool, bought with ethecoin, semi-expensive. It turns a *notification that something
happened to you* into *a machine on your map*.

### 5.1 What it does

- Reads the MonJobs and pings your machine has received. If you were told you were port-scanned, the
  Tracer is how you ask **who**.
- Running a trace **adds the traced machine to your network map**. That is the whole payload — you can
  then choose to scan or attack it through the normal route.
- A **small chance** of returning one random fact a port scan would have shown. A bonus, never the
  reason to run one.
- **Adds no noise to the tracer.** It is a read of your own logs, not an outward probe.
- **Alerts the target that they are being traced**, which is what makes it a two-sided move.
- Players and NPCs both trace, and both can be traced.

### 5.2 ⚠ Gate classification, per `02` §1.1

Ethecoin, and it needs checking against **I2** rather than assumed. A Tracer does **not** raise the hop
ceiling: the machine it puts on your map is one that *already reached you*, so it is by definition
within your world and not further away than you could go. It buys **breadth** — a new way to learn an
address you would otherwise never get — not a ceiling.

⚠ The check that would fail: if a Tracer could put a machine on the map that is **beyond the hop
ceiling**, ethecoin has bought reach. Recommend the traced machine is added as a **contact you still
have to reach normally** — visible, not adjacent.

### 5.3 ⚠ It must not become the Passive Sniffer

`07` §1 gives the Sniffer the published function of naming a node's *type*. A Tracer that returned
kind, tier and value would delete that tool. The "small chance of one random port-scan fact" is the
fence, and it should stay **one** fact and stay **random**.

---

## 6. Distance, difficulty, and reprisal — **[PROPOSAL]**

`ServerState.depthFromHome` already exists and `Balance.netCounterHackChance(depth)` already scales
with it, so the skeleton is there. What the brief adds is that far machines are not merely *harder* but
*more active* — likelier to come at the player unprompted.

⚠ **This is the part with the most balance risk in the whole doc.** `ReprisalRules` exists and is
tuned; making distance drive aggression means a player who pushes two servers out gets a materially
different game, and `03`'s economy is calibrated as a set. Recommend it lands **after** MonJob and
Tracer, so the systems that let a player *see* an incoming threat exist before the threat is turned up.

---

## 7. The anti-trace minigame — **[PROPOSAL]**

A time-based defence: the target has the trace's duration to complete it, or the trace succeeds.

### 7.1 ⚠ The ordering objection is withdrawn — the breach puzzle is built

This section first argued the anti-trace puzzle should wait, on the grounds that it *"would be the
game's second minigame while the first is still unbuilt"*. **That was wrong on its facts.** The breach
puzzle is complete: `05` reads *Decided 2026-07-26*, `16-breach-implementation.md` is titled *"The
Breach, As Built"*, and there are two puzzle classes, nine classes in `engine/breach/`, seven test
classes and a `BreachView` playing it. The claim came from a stale line in `CLAUDE.md`, now corrected.

So there is no scheduling reason to defer this, and it can be built when the Tracer is.

### 7.2 ⚠ What does NOT go away: I10

**I10** — *bots assist, never substitute; a bot never solves the puzzle for the player* — still has to
be answered for this puzzle independently, and it is harder here than it was for the breach.

A **timed** defence is the shape automation is best at: a fixed reaction under a deadline is precisely
what a script beats a human at, and the breach's own anti-bot property does not transfer. `client/05`
§44 states what that property actually is — *"a human cross-references material a fixed heuristic
cannot; cross-referencing two documents is a simultaneity problem"* — and a countdown is not one.

Two ways to satisfy I10 here, and the choice should be made before any of it is drawn:

- **(a) Make it a reading problem under time**, not a reaction problem: what the defender must do is
  determined by something they have to *look up* — which bridge, which MonJob tier, what the trace
  claims about them — so the clock bounds a decision rather than a reflex.
- **(b) Make failure cheap enough that automating it is not worth it.** A trace that succeeds costs
  the defender *being on somebody's map*, which §5.1 is explicit is not by itself an attack. If the
  stake stays that low, a bot that always won this puzzle would have won very little.

**Recommendation: (a), with (b) as the backstop.** (a) is the one that keeps the puzzle honest;
(b) means that if (a) turns out to be automatable anyway, the game has not lost much.

### 7.3 Reuse, do not rebuild

When it is built it should use `ui/breach/AsciiCanvas` and `BreachViewport` rather than introducing a
second rendering path. `AsciiCanvas`'s own class comment records that it was extracted from
`CoreCage` precisely so a second consumer would not re-derive the same character-cell machinery.

---

## 8. Open questions

Logged in `15-open-questions.md`.

- ~~**MJ-1** — can a MonJob's owner remove their own?~~ ✅ **Resolved 2026-08-07: yes, and only they
  can.** Removal is silent to everyone else. (§4.2)
- ~~**MJ-2** — do NPCs place MonJobs, and on which bridges?~~ ✅ **Resolved 2026-08-07: yes, density
  scaling with depth from home, zero at home.** Derived by hashing, never drawn. (§4.4)
- ~~**MJ-3** — does the **tier** mix shift with depth as well as the density?~~ ✅ **Resolved
  2026-08-07: yes.** `Balance.monJobTierTwoShare` = `0 / 0.15 / 0.30 / 0.45 / 0.60`. (§4.4)
- ~~**MJ-4** — can a scan detect a MonJob **before** you cross?~~ ✅ **Resolved 2026-08-07: yes, and
  it says "monitored" and nothing else** — never whose, never what tier. (§4.4)
- **MJ-5** *(new)* — player-placed MonJobs are not built. Only the NPC derivation exists; placement,
  removal, the owner's notification and the tier-2 "Watched" alert to the intruder are all still
  design. They OR into `PortScanRules.monitoringOf` and nowhere else when they land. (§4)
- ~~**PS-1** — `PEERS` rung depth?~~ ✅ **Resolved 2026-08-07: bridges get their own applicable set.**
  `PEERS` at depth 4, `MONITORED` at 5, sharing depths with rungs bridges do not have. (§3.2)
- ~~**PS-2** — `NodeReports.known` must divide by *applicable* rungs.~~ ✅ **Done 2026-08-07**, and it
  was a live latent bug rather than a precaution: three existing tests fired on the change, including
  `BreachPuzzleWeightingTest`, which is exactly the silent game-wide re-weighting it was there to
  prevent. (§3.2)
- **TR-2** — does a traced machine arrive inside or outside the hop ceiling? I2 turns on the answer.
  (§5.2)
- **AT-1** — the anti-trace minigame and **I10**. A timed defence is the shape automation is best at,
  and the breach's anti-bot property (cross-referencing as a simultaneity problem) does not transfer
  to a countdown. Make it a reading problem under time, or keep the stake low enough that winning it
  automatically wins little. ⚠ **The scheduling half of this question is closed** — the breach puzzle
  is built, so there is no "second minigame first" objection. (§7)
