# 04 — The Mining System

**Status:** Established (design sessions 1–5) — the most fully specified system in the game
**Depends on:** `01-core-resources.md`, `03-economy.md`
**Depended on by:** `07-recon-tools.md` (Provenance Tracer), `09-defense-and-hardening.md` (Detection Array, Rootkit Wrapper), `11-rig-infrastructure.md` (Cuckoo Patch, Firmware Implant, Worm Module), `05-hacking-minigame.md` (cracking)

Mining is two very different systems sharing a name: **self-mining** (the safe income floor on your own rig) and **deployed mining** (parasitic miners on other machines — the game's main asymmetric-PvP/PvE surface). Keep them conceptually separate; almost every rule differs.

---

## 1. Self-Mining

Runs on the player's own rig. Consumes allocated compute at **0.4 EC per cycle-hour** (full 100-cycle rig = 40 EC/hr) — an *expectation* since 2026-07-27, not a trickle; see §1.3.

### 1.1 The four structural properties (all load-bearing)

1. **Silent** — generates zero noise.
2. **Structurally immune to all detection and seizure** — no NPC or player can reach it, ever (Invariant I4). Not "hard to find"; *unreachable by construction*. Do not implement it as a hidden process that a good-enough scan could theoretically see.
3. **Zero heat.** The one perfectly safe action in the game.
4. Its entire cost is **the compute it occupies** and the slower rate that compute returns (Thermal Budget interaction, `01-core-resources.md` §1.3).

**Why the immunity is load-bearing:** heat can destroy a deployment network but never the floor. A player who goes hot drops from ~60 EC/hr to 40 EC/hr, not to zero. Heat stays a real cost with a real bottom, and mining remains the productive off-ramp from a hot state instead of a second punishment stacked on the first. If self-mining were ever raidable, going hot would mean total income loss and the heat system would become a fun-ejector.

### 1.2 Online-only

Self-mining runs **only while the player is in session** (matching the botnet rule, Invariant I5). This keeps the compute tradeoff real — cycles on mining are cycles not on bots, tools, and defense, and that tension only exists during a session. Offline accrual would make those cycles free and mining would stop being an active bet.

### 1.3 The chain — pooled and solo (rebuilt 2026-07-27)

Self-mining is a **real proof-of-work simulation**, not a rate. It uses the real proof-of-work equations:

```
expected hashes per block   = difficulty × 2³²
expected seconds to a block = difficulty × 2³² / hashrate
difficulty holding interval = interval × networkHashrate / 2³²
```

The chain targets **one block every fourteen minutes** and retargets every **1440 blocks**, clamped to a factor of four per adjustment.

> **⚠ The *relation* is real; the *numbers* are this chain's own.** `difficulty × 2³²` and the factor-of-four clamp are reused exactly, so the arithmetic in `../education/07-distributed-systems-and-identity.md` §3.25 still checks out against a live explorer. But ten minutes and 2016 blocks would have made ethecoin read as a Bitcoin reskin, and a chain that is recognisably one real product teaches that product rather than the mechanism. Fourteen minutes is unmistakably not Bitcoin's, and 1440 × 14 min is still a fortnight — the design property 2016 exists for, without the number.

**A block is *won*, not raced.** Every ~14 minutes the chain mints one block, and a single draw picks the winner with probability equal to their share of the hashrate. A rig with 5.6% of the chain wins 5.6% of the blocks. That is the standard formulation and it is exactly equivalent in expectation to every miner racing their own exponential — but it is **legible**: every block now has a winner, which is a field the explorer shows, and *"your chance at this block is your share of the chain"* is a sentence a player can check against the readout.

> **⚠ Memorylessness survives intact.** The player's wait is now a *geometric* number of blocks rather than an exponential time, and the geometric is the discrete memoryless distribution — losing forty blocks running tells you nothing about the forty-first. Nothing accumulates, nothing is owed, and there is still no progress to draw.

**Pay-per-share pools are settled off-chain**, on their own share clock, because a PPS miner is paid per accepted share whether or not anybody found a block — that is the entire product they are buying. Solo and PPLNS are paid out of blocks.

**The player chooses the shape of their income, not its size.**

| | Pays | Interval (full rig) | Fee | Hour-to-hour swing |
|---|---|---|---|---|
| **Pooled** (default) | one share | ~30 s | 2% | ~9% |
| **Solo** | one whole block, 160 EC | ~4 h 10 m | none | ~196% |

Both are self-mining and both keep **I4** — silent, unseizable, zero heat. Expected income is identical but for the fee, which solo keeps: **pooled is exactly the 0.4 EC per cycle-hour `03-economy.md` §1 prices the economy against, and solo is that divided by (1 − fee)**, about 2% more.

**Why pooled is the default.** §1.1 calls this the floor and explains that a hot player must drop from ~60 EC/hr to 40, *not to zero*. Solo mining pays nothing in about 77% of hours. A player silently opted into it would find the safety net had become a second punishment, which is the fun-ejector §1.1 warns against. Solo is a thing you choose.

**The pool is modelled as a real pool.** It hands each rig an easier target retuned to that rig's hashrate — real pools call this *vardiff* — so a share lands every ~30 s whatever the allocation, which is why pooling smooths a 10-cycle rig as well as a 100-cycle one. Payment is **pay-per-share**: a fixed amount per accepted share whether or not the pool found a block, with the fee as the price of the operator carrying that variance. A share is never a block and never touches the chain's height.

> **⚠ The block subsidy is the only knob that changes the lottery's feel without disturbing `03`.** Raising it makes solo blocks rarer and larger and changes nobody's expected income by one minor unit, because the network hashrate is *derived* from the subsidy and the economy anchor (`solo/Balance.chainNetworkHashrate`). Reach for that one; do not reach for the rate.

### 1.3a The pools — [PROPOSAL]

Pooled mining is a choice of **pool**, not just of mode. Five operate on this chain (`solo/Pools.java`):

| Pool | Scheme | Fee | Chain | Pays | EC/hr @ 94 cy |
|---|---|---|---|---|---|
| **THE COMMONS** *(default)* | PPS | 2.00% | 22% | every 30s | 37.60 |
| **MERIDIAN CLEARING** | PPS | 3.50% | 32% | every 15s | 37.02 |
| **PALE LANTERN** | PPS | 2.50% | 7% | every 45s | 37.41 |
| **GLASS TEETH** | PPLNS | 1.00% | 18% | every 1.3h | 37.98 |
| **SMALL HOURS** | PPLNS | 0.50% | 12% | every 1.9h | 38.18 |

> **⚠ Nothing on this list dominates anything else, and the ordering above is the proof.** As the fee falls, income rises *and the interval lengthens*. The cheapest pool on the chain pays least often — SMALL HOURS is nearly as lumpy as mining alone. A player who reads only the fee column picks the pool that behaves most like the thing they were trying to avoid, which is a true lesson about real pools.

**The two axes are fee and steadiness, and they come from different places.**

- **Fee is the only thing that changes expected income.** Payout × rate cancels the payout fraction out entirely (`MiningRules.payoutFraction`), so scheme and pool size change *nothing* about what you earn.
- **Steadiness comes from the scheme.** Under **PPS** it comes from the pool's share target (vardiff) and is therefore *independent of pool size* — a one-rack PPS pool smooths as well as the biggest on the chain. Under **PPLNS** you are paid only when the **pool** finds a block, so **pool size is the variance knob**: 5% of the chain is one payout every three hours.

**The shares total 91%.** The rest is solo miners and operations too small to list — roughly what a real chain looks like, and what keeps unpooled mining a real place to be rather than an empty one.

> **⚠ Every PPLNS pool must out-hash a maxed player rig.** A PPLNS payout is `playerHashrate / poolHashrate` of a block, clamped at 1 — so a rig larger than its own pool clamps, and the pool then quietly behaves like solo mining with a fee attached. The chain is 1680 cycle-equivalents and a 100-cycle rig is 6% of it; the smallest PPLNS pool is 12%. This has already bitten once: moving to 14-minute blocks shrank the network from 2352 to 1680 cycles and a full rig became *larger* than the 5% pool it was mining with. `MiningChainTest.pplnsPoolsOutHashAMaxedRig` fails the build on it.

> **⚠ THE COMMONS's fee must stay equal to `Balance.POOL_FEE`.** The network hashrate is derived from that constant and the `03-economy.md` §1 anchor, and a new character mines here. If the default's fee drifts, the documented 0.4 EC/cycle-hour stops being the rate anyone actually gets. `MiningChainTest.defaultPoolIsTheAnchor` fails on it.

**MERIDIAN at 32% carries a caution in the fiction**, and it is real: a pool past half the chain can rewrite history alone, and nobody is obliged to announce when it gets close. There are deliberately **no mechanics behind it** — a consequence would have to touch detection or heat on self-mining, which **I4** forbids outright. It is flavour that happens to be true, and the shipped `mining-pool(7)` page carries the same point.

---

### 1.3b The explorer — the Ledger tool as a block explorer

The LEDGER window is a block explorer: the chain's last two dozen blocks as cards, this rig's address and balance, and its transactions.

**Ethereum's shapes, this chain's mechanics.** Addresses are `0x` + 40 hex, hashes `0x` + 64, and the block header carries **pre-Merge Ethereum's** fields — `number`, `hash`, `parentHash`, `nonce`, `miner`, `difficulty`, `gasUsed`/`gasLimit`, `size`, `extraData`. Pre-Merge Ethereum *was* a proof-of-work chain with a miner taking a block reward, so none of those fields is borrowed dishonestly. Deliberately absent: contract addresses, logs, uncles, a fee market. Gas is real arithmetic rather than decoration — every transaction here is a plain value transfer at the 21 000 gas Ethereum charges for one, so a block's `gasUsed` is its transaction count times 21 000 and nothing else. The **gas limit is 200 transfers' worth, not Ethereum's 30 000 000**: a limit is a per-chain figure miners vote on, and borrowing Ethereum's would both claim to be Ethereum and make every fill bar read 2%.

**A block reward comes from the zero address**, which is what explorers really show — the coins did not exist before the block — and a coinbase costs no gas because there was no transaction to execute. A **pool payout gets no block number at all**: the pool paid it out of its own balance, and stamping a height on it would put a transaction on the chain that no miner mined.

> **⚠ The explorer and `ledger(1)` are two renderings of one list.** Same amounts, same moments, same running balance. §3.1 makes *"add these up and compare against the balance"* the way a player catches a hidden miner, so two surfaces that could disagree would turn the game's central investigation into a false-positive generator.

**Nothing about a block is stored, including who mined it.** Every field — hash, parent hash, nonce, miner, size, transaction count, and the whole body of transactions — is *derived from the height* through one digest over `(blockSeed, height)`. A real block's hash is the thing the miner searched for and has to be recorded; here the winner is *drawn*, so a hash carries no information and only has to be **stable**. That is what lets a new character open at **height 124 with all 124 blocks fully inspectable**, and lets the chain keep growing while the save does not.

The one thing that cannot be derived is what was *rolled*: whether the player won a given block. That is a bounded index (`ChainState.blocksWon`) over the authoritative record, which is the ledger — a won block writes a row naming its height. Even the historical miner is derived, from the same weighted pool table the live draw uses, so the past's distribution matches the present's; a pool "losing" a block the player won is correct, because somebody had to.

The seed is per-character. Without it every save would render identical hashes at identical heights and the chain would read as a shared fixture rather than each character's own world.

**Block timestamps before the player joined are back-dated at an even cadence.** Real block times jitter and these do not, which is the one place the derivation is visibly a derivation — but a history with plausible-looking random gaps would be inventing a past the chain never had, and an even cadence at least does not claim to be a measurement.

---

### 1.3c The mempool and the fee market — [PROPOSAL]

A block holds **200 transactions** and the queue is usually deeper, so a slot has to be won on fee rate. That is the whole mechanic, and it needs three things at once — a queue longer than a block, a higher fee that genuinely gets in sooner, and a low fee that is never stranded forever. Break any one and the tiers become cosmetic.

| Tier | Fee | Promise |
|---|---|---|
| **Economy** | 0.02 EC | in a few blocks, when the queue thins |
| **Standard** *(default)* | 0.06 EC | usually the next block or the one after |
| **Priority** | 0.30 EC | the next block, unless everyone else is paying this too |

> **⚠ Fees are deliberately not a sink.** `03-economy.md` §4 lists the sinks the economy is balanced against and this is not one: 0.30 EC against 40 EC/hr is a rounding error **by design**, because the fee exists to *order a queue* rather than to drain a balance. If it ever grows enough to matter it has become a sink and §4 has to know. `MempoolTest.feesAreNotASink` fails the build past 1% of an hour's income.

**A spend debits immediately and confirms later.** The balance moves now and whatever was bought is the player's now — the same instant a real wallet shows a send and deducts it from spendable balance. What waits is the *chain record*. That is the one place this simulation declines to be faithful, deliberately: a purchase that withheld the goods for fourteen minutes would be accurate and would also make buying a consumable mid-breach impossible, for a lesson the visible mempool already teaches. **The fee is charged on top**, so a sender who cannot afford `amount + fee` is refused rather than shorting the recipient — and it earns its own ledger row, because a fee folded into the amount is a charge `ledger(1)` cannot explain.

**Everything is quoted as a gas price** — minor units per million gas. A fee total and a fee *rate* differ by a factor of 21 here, and shipping the mempool's cheapest slot in one unit and the top of its queue in the other made an under-4× spread read as 180×. Real explorers quote sat/vB or gwei for exactly this reason: a total says nothing about priority.

**The NPC queue is derived, not stored** — a function of `(blockSeed, height)`, so the same chain state always shows the same queue and a save does not grow for a histogram. It drifts with *height* rather than the wall clock, deliberately: a mempool that moved while the game was paused would let a player wait for a cheap moment without the chain advancing, which is gaming the fee market by doing nothing. NPC fees are capped at the priority rate, or the top tier a player can pay would buy nothing.

#### Projections are not a schedule

The panel shows the next three blocks as they *would* be if mined right now from the current queue. `ChainMempool` carries that warning at the type level, and the strip draws projected blocks dashed and prefixed `~`, because it is the one thing a player could reasonably misread as a promise.

> **⚠ There is no countdown to the next block, and there must never be one.** Blocks arrive on a Poisson schedule, so there is no moment to count down *to*. The panel prints the mean interval and the elapsed time side by side — one an average, one a fact — and a player comparing them is reading the distribution. A ticking countdown would be the same lie a mining progress bar would be, one step removed.

---

#### Noise — pooled is faintly audible, solo is silent (2026-07-27)

| | Noise | Why |
|---|---|---|
| **Pooled** | 1–4 cycle-equivalents | Holds a connection to a pool server and pushes a share up it on a timer. Outbound traffic to a third party. |
| **Solo** | **zero** | Local grinding. Nothing leaves the rig until a block is found. |

**Invariant I4 is intact.** I4 grants immunity to detection and seizure and **zero heat**, and a pooled rig still has all three — noise is a *rate*, heat is what an act leaves behind, and nothing converts this trickle into heat (heat is charged at breach resolution and by counter-hacks, never off the ambient meter). What I4 protects is that going hot cannot take the floor away, and a rig reading 2% on the noise meter has lost nothing.

Two properties are load-bearing:

- **It does not scale with allocation.** A share is a small fixed packet however much hashing produced it, so doubling your cycles doubles your income and changes your traffic not at all. If it scaled, the noise-conscious play would be *to mine less* — punishing the income floor for being used, which is precisely what I4 exists to prevent.
- **It scales with the pool's share interval**, which is the one place that number is more than flavour: MERIDIAN asks for a share every 15s and is twice as loud as the 30s reference; SMALL HOURS asks every 60s and is half. **Picking a quieter pool is a real play**, and it is a third axis on the roster that costs nothing to balance.

For scale: a sweep is `NET_SWEEP_BASE_NOISE = 35`. Pooling is 1–4. A sweep is more than seventeen times louder than the loudest pool.

#### Settlement — why the ledger is not 120 rows an hour

A pool credits shares to an internal balance continuously and **settles every 60 seconds**; a solo block is a coinbase and is paid at once. Real pools do exactly this, and the game needs it for a different reason: at a share every 30s, crediting each one puts **120 rows an hour** into `ledger(1)`, a readout whose shipped page calls itself *"the only record of where your money went"*. Buried under a wall of identical 0.31 EC rows it records nothing — `alert-fatigue(7)` again, in the one place a player audits.

> **⚠ The credit and the ledger row happen together, always.** Crediting continuously and ledgering periodically would leave the balance ahead of the last row, and §3.1 makes *"two readouts disagree"* the way a player detects an intruder. Training them to ignore that would cost far more than the tidy ledger bought.

Two behaviours worth not rediscovering: the **first payout of a character's life never waits** (holding it back makes mining look broken for a minute), and a **null or backwards clock settles immediately** — a naive `elapsed >= window` check goes permanently false against a `settledAt` in the future, so a host clock correction would make the pool hold the player's money forever.

#### What memorylessness buys, and what it deleted

Every hash is an independent trial against an unchanged target, so the wait is exponentially distributed and therefore **memoryless**. Three consequences the design now relies on:

- **Nothing accumulates**, so there is no partial progress. This is why the old *"pulling cycles off mining mid-block forfeits that block's progress"* proposal was **deleted rather than implemented** — it described a thing that does not exist. Switching mode or reallocating costs nothing and forfeits nothing.
- **Nothing is "due".** A long dry spell does not raise the next block's chance. The UI therefore shows an expected time and **never a progress bar**: a bar would be a lie, and would teach players to hold cycles on mining to protect progress that isn't there. `mine --solo` prints the point in words.
- **The mean misdescribes the typical.** The median of an exponential is ~69% of its mean, so solo mining *feels* unluckier than it is. That is a true fact about the distribution, not a tuning failure.

#### What is simplified, stated plainly

The rest of the chain's hashrate never changes, so difficulty has **no trend** — it sits at the equilibrium `Balance.chainDifficultyFor` sets. What it does *not* do is sit still: 2016 random block times have a spread of about `1/√2016 ≈ 2.2%`, so every retarget nudges difficulty a percent or two either way. Measured over 2000 simulated hours, difficulty wandered 344.5 → 351.1 across five retargets while income held within 0.3% of expectation. **That jitter is real Bitcoin behaviour** — a difficulty holding a constant value to the decimal would be the bug. The missing thing is the *trend* a growing network supplies.

The chain is also small: a full rig is ~4% of it, where a real solo miner is a rounding error and would wait years. Both simplifications are stated in the shipped `proof-of-work(7)` page rather than hidden.

#### Deployed miners and bots are unchanged

Deliberately. They are pooled by construction — a buffer that fills at a rate, capped, collected on a visit — and variance on the one income stream the player cannot watch or react to would be punishment without a decision. It would also break §5.1's crack timing bet, which is priced on *"payout scales with buffer fullness"*: a buffer that filled in jumps would make *"found at minute five, it holds almost nothing"* false about half the time.

---

---

## 1.4 Multiplayer — the chain a server runs — [PROPOSAL]

Single player runs its chain in `solo`, entirely locally, and a solo character can never federate (`solo/pom.xml`, Invariant **I14**). A home server runs the same simulation for the characters it hosts, plus two things single player has no need of.

### 1.4a Genesis, or joining

On startup a server asks: do I have a chain? Do any of my peers?

| Local | Peers | What happens |
|---|---|---|
| none | none | **mint genesis** |
| none | some | **join theirs** — never mint |
| some | heavier | **adopt and reorganise** |
| some | equal or lighter | keep ours |

> **⚠ Genesis is minted only when nobody has a chain at all.** A server that minted one while a peer already had a chain would fork the federation on startup, and both halves would be certain they were right. `ChainSelection.shouldMintGenesis`.

### 1.4b "Longest" means most work, never most blocks

`ChainSelection.better` compares **accumulated difficulty**. Height is the intuitive reading and it is wrong in the one case the rule exists for: a fork of many easy blocks can be *taller* than a fork of fewer hard ones, so a height comparison lets an attacker out-vote honest work by lowering difficulty instead of doing any. Bitcoin compares cumulative difficulty and so does this; `ChainHead.height` is display-only and the rule never reads it.

Two further rules, each a security property rather than a nicety:

- **A different genesis is never adopted, however heavy.** That is not a longer chain, it is a different currency, and adopting across the line would migrate every balance this server holds onto somebody else's ledger.
- **Ties go to the incumbent.** Two chains of equal work are equally valid, and switching on a coin flip would make a server's history depend on the order peers happened to answer in — the same non-determinism as last-writer-wins, which `../architecture/08-peer-discovery.md` §0 already refused once.

> **⚠ `ChainSelection` picks a head to *fetch*. It does not decide what is true.** A head is a claim: numbers a peer asserted. Adopting one means fetching the blocks and verifying them. A server that treated the winner of the comparison as authoritative would let any peer rewrite its ledger by sending a large number.

### 1.4c What is deliberately not wired up

**The network fetch.** `../architecture/07-transport-security.md` §6 T-1 marks this project's transport as *reviewed patterns, unreviewed code*, and `CLAUDE.md` is explicit: do not let it guard a live federation until a cryptographer has read it. So the selection rule and the genesis rule are implemented and tested against local and synthetic heads, and the peer exchange stays behind the same documented seam every other federation feature sits behind. Logged as **MN-2**.

Cross-server transaction sync (real players sending each other funds, and NPC traffic shared between servers) rides that same seam and is blocked on it for the same reason. What exists today: the rule that decides which chain wins, tested against the attacks it is there to stop.


## 2. Deployed Miners

Placed on another machine — NPC or player. The system's foundation, stated plainly:

> **A deployed miner consumes the host's compute, not the deployer's.** (Invariant I6)

If a hostile miner cost the host nothing, no rational host would ever spend compute on detection and the entire discovery mechanic would go unused. Because it steals cycles, ignoring it has a price, and finding one *returns capacity* rather than just firing a notification.

The deployer's own cost: a **3-cycle control channel reservation per live miner**, permanent while the miner runs.

### 2.1 Tiers

| Tier | Yield | Host compute stolen | Signal strength |
|---|---|---|---|
| T1 | 12 EC/hr | 10 cycles | Low |
| T2 | 19 EC/hr | 20 cycles | Moderate |
| T3 | 30 EC/hr | 35 cycles | High |

### 2.2 The self-correcting network cap

Five T2 miners cost the deployer 15 cycles of a 100-cycle rig — felt but survivable. Twenty miners cost 60 cycles and leave the deployer nearly defenseless. **That is the cap on network size; no hard limit is needed.** Do not add one — the compute economy already does this job, and a hard cap would remove the overextension mistake the design wants players to be able to make.

### 2.3 Offline accrual and yield buffers

Deployed miners are **the only income source that operates while the player is logged off** — that is their primary identity, not just the higher yield. But payout requires an active control channel, so with the deployer offline, output accumulates in a **local buffer on the host machine** that must be collected on return.

- Each buffer holds **4 hours of that miner's yield**; once full, the miner runs and produces nothing.
- Reference math: a five-miner T2 network caps at ~380 EC per offline stretch ≈ 5.5 hours of active-play income.
- Rationale against uncapped accrual: an overnight absence at low heat would out-earn playing, and it would reward the *cautious* player most (a cold network survives ~50 hours at the 2%/hr sweep floor). The buffer keeps offline income meaningful without making absence optimal, and stops rewarding longer absences specifically.
- Buffer calibration is OQ-4 (4 hours is a starting figure).
- The buffer is also the second reason Provenance Tracer exists: a **hijacked** miner has been filling *someone else's* buffer the whole time you were away (§5).

---

## 3. Discovery — player-hosted miners

Detection on a player's own rig is **entirely a function of compute the host commits to security** — defense costs the same resource as earning, which keeps it inside the master scarcity. Two paths:

### 3.1 Manual investigation

The player inspects their own running processes, storage, and connection table directly. Costs **zero compute**; costs attention and session time. A careful reader can find *anything*, including rootkit-wrapped miners, because **the discrepancy is always present in the data** even when scans would miss it (cycle totals that don't add up, a connection with no owning process, storage deltas).

This is a hard implementation requirement: the process/connection/storage views must be real, consistent data — not decorative — so that manual auditing genuinely works. It rewards attentive players without taxing the scarce resource, and it is the game's second-strongest tutorial vector.

### 3.2 Scans

Automated, compute-expensive, faster:

| Scan | Compute | Duration | Finds |
|---|---|---|---|
| **Quick** | 5 | ~30s | Unhidden T2–T3 miners only |
| **Full** | 15 | ~2 min | All unhidden miners; some rootkit-wrapped |
| **Thorough** | 35 | ~6 min | All miners, including rootkit-wrapped |

**A scan holds its cycles for its Duration, and only then do they start recovering** on the Thermal Budget curve (`01-core-resources.md` §1.3). The two columns compose rather than run in parallel: a Thorough Scan takes 35 cycles out of the rig for ~6 minutes and *then* the recovery curve begins, so the player is down 35 cycles for the scan plus the recovery — on a heavily loaded rig, far longer again, precisely when they're least able to respond to anything else. **Scanning aggressively while overextended is punishing; scanning while lean is merely expensive.** That asymmetry is the design.

> **Decided 2026-07-26 (UI-6).** This was previously spend-immediately: the cycles went onto the recovery curve the moment the scan started, so on a lean rig a Thorough Scan's 35 cycles were back in about four minutes — *inside* the six-minute scan that paid for them, which made the paragraph above false for exactly the players it was written about. Hold-then-recover is the reading that makes the published Duration cost something. It roughly doubles a Thorough Scan's real cost; see `15-open-questions.md` §3 for what was re-checked alongside it.

### 3.2a Scans can be wrong (decided 2026-07-26, closing DF-5)

**A scan result is evidence, not a verdict.** Every tier can produce a **false positive** — a hit on something innocent — and the cheaper tiers do it more often. Signal quality, not just sensitivity, is what a more expensive tier buys.

| Scan | False-positive rate | Reading |
|---|---|---|
| **Quick** | High | Cheap, fast, and it will send you chasing ghosts |
| **Full** | Moderate | The working default |
| **Thorough** | Low | Expensive in both compute and attention, and it earns it |

A standing **Detection Array** (`09`) cuts these rates further — that is now its entire distinct role (OQ-6).

**Why this was added rather than left out.** `../education/08-detection-and-defence.md` teaches `false-positive(7)`, `base-rate-fallacy(7)` and `alert-fatigue(7)` — three of the curriculum's strongest pages, all resting on the fact that real detectors mostly fire on innocent things. §3.2's scan tiers implied it and never delivered it, so **the game contradicted its own manual**, which `CLAUDE.md` treats as worse than teaching nothing at all. It also makes the Thorough Scan's price legible: you are buying a result you can *act on* without a second look.

⚠ It strengthens §3.1's manual investigation rather than competing with it. A scan hit is now a lead to corroborate against the compute ledger — exactly the cross-referencing §3.1 calls the game's second-strongest tutorial vector — instead of an answer that makes investigation pointless.

### 3.3 Detection legibility (the established answer)

Nothing announces itself. **Signal strength is what the player pays for**, and the choice between free-but-slow manual work and fast-but-costly automation belongs to the player. The passive alternative (Detection Array, `09-defense-and-hardening.md`) reserves compute permanently to raise per-tick discovery chance — whether it stays distinct from scans is OQ-6.

---

## 4. Discovery — NPC-hosted miners

Miners persist notably longer on NPC machines. Sweep probability is a function of the **deploying player's overall heat**, with a floor:

| Player heat | Sweep chance/hr (network-wide) |
|---|---|
| Zero | 2% |
| Low | ~8% |
| Moderate | ~25% |
| High | ~45% |
| Named-hacker | ~60% |

- The 2% floor exists so a permanently cold player's network still erodes; without it, cautious play produces permanent free income.
- **Losses are correlated, not attritional.** Heat is a single global value, so every NPC-hosted miner rolls against the same number — networks disappear in sweeps, not smooth decay. This makes the §`03-economy.md` effective-yield figures higher-variance than they look. If playtests show wipes feel unfair rather than dramatic, the fallback is a partial-sweep model (OQ-3).

> **[PROPOSAL]** Interpretation to confirm: "network-wide" means one roll per hour against the whole NPC-hosted network; on a triggered sweep, all NPC-hosted miners of that player are found and destroyed (subject to Firmware Implant survival, `11-rig-infrastructure.md`). Player-hosted miners are never swept by The Eye — they're found by the host or not at all.

---

## 5. Responding to a discovered miner

A host who discovers a foreign miner has **four options** — this menu is core game content, not an edge case:

| Response | Gets | Costs | Deployer learns |
|---|---|---|---|
| **Kill** | Compute reclaimed | Forfeits the buffer | Channel drops — knows immediately |
| **Crack** (minigame) | Buffer seized **and** compute reclaimed | Risk of total loss on failure | Nothing on success; handle exposed on failure |
| **Hijack** (Cuckoo Patch) | All *future* yield | Compute stays stolen | Nothing until they audit |
| **Sabotage** | Nothing | Compute stays stolen | Nothing — keeps paying 3 cycles for a dead miner |

### 5.1 Cracking

A **full instance of the core hacking minigame** run against the miner itself (`05-hacking-minigame.md`). On success the host seizes the miner's accumulated yield buffer and removes it. The buffer physically resides on the host's machine (the control channel can't route payment while the deployer is offline), so the EC is already there to take — a **transfer, not a faucet**; no new currency enters the economy.

- **The timing bet:** payout scales with buffer fullness. Found at minute five, it holds almost nothing; found at hour four, the full cap. Killing immediately is safe and worth little; leaving it to fatten means bleeding compute meanwhile and risking the deployer returning to collect first. Both are defensible — that's what makes discovery a decision rather than a reflex.
- **Failure — dead-man switch:** a botched crack flushes the buffer to the deployer immediately and the miner self-destructs. Host reclaims compute but gains nothing, and **the deployer is alerted with the host's handle attached** — feeding bounty/retaliation options. Without this, cracking would strictly dominate killing.
- **Difficulty** scales with miner tier, raised further by Rootkit Wrapper (which gives that item a defensive-denial role).
- **Noise/heat:** low noise, **no heat** (Invariant I9). Defending your own rig never contributes to being wanted.
- **Tutorial use (established):** cracking is the strongest early-game teaching vector for the core minigame — self-contained, on the player's own machine, visible reward, comprehensible failure, no heat cost for losing. The tutorial flow should *plant* a weak scripted miner early.

### 5.2 Crack vs. hijack — time horizons, not tiers

Cracking takes the accumulated **past** and ends the intrusion. Hijacking (requires the Cuckoo Patch rig module) takes the **future** income stream but leaves the host's cycles stolen. A host short on compute should crack; a host with headroom and patience should hijack. Sabotage is the spite/counterintel play — the deployer keeps paying 3 control cycles for a dead asset until they audit.

### 5.3 The maintenance consequence

Hijack, sabotage, and crack are collectively why **Provenance Tracer** (`07-recon-tools.md`) exists and why a large deployment network demands ongoing *maintenance* (auditing time) rather than just capital. This is the self-limiting loop that keeps deployed mining below active income in practice (`03-economy.md` §1.1).

---

## 6. Cross-references

- The **puzzle** used for cracking: `05-hacking-minigame.md`
- Hiding deployed miners: Rootkit Wrapper, `09-defense-and-hardening.md`
- Auditing your own network: Provenance Tracer, `07-recon-tools.md`
- Rig modules that extend mining: Cuckoo Patch, Firmware Implant, Worm Module — `11-rig-infrastructure.md`
- Miner-focused bot frame: `10-botnets.md` §2
- Economy context and variance warnings: `03-economy.md`
- Open questions touching mining: OQ-3 (partial sweeps), OQ-4 (buffer size), OQ-6 (Detection Array role), OQ-7 (crack profitability vs. security incentive)

---

## 6. The process table — the manual audit, implemented — [PROPOSAL]

§3.1 has always said that a hidden miner is findable *by hand*: "the discrepancy is always present in the data — cycle totals that don't add up." Until now that was a sentence with no mechanic behind it. The rig monitor's five tabs are the mechanic.

### 6.1 Five tabs, because each one is a question

**Overview · CPU · MEMORY · DISK · NETWORK**, in that order — cheapest signal to most specific. A player who suspects something walks rightwards. Every tab lists the same processes with different columns: the player's own tools and reservations, the system's own processes, and anything else that happens to be running.

**The rig runs a FreeBSD-shaped system, and the table is FreeBSD's.** Kernel threads are bracketed (`[pagedaemon]`, `[g_up]`, `[bufdaemon]`), pid 0 is the kernel and pid 1 is `init`, and the service accounts are real ones — `root`, `daemon`, `operator`, `nobody`, `unbound`, `_dhcp`, `ntpd`. All three conventions transfer to a real machine, which is the point.

> ⚠ A handful of rows are the fiction's own — `cyclesd`, `netd`, `ledgerd`, `vaultd`, `provenanced`, `attestd`, `syspolicyd`, `pulsed` — and they are flagged as such in the source rather than left to be assumed. Nothing here may quietly assert that FreeBSD ships a `cyclesd`. `netd` in particular is invented because real FreeBSD has no single networking daemon (the stack is in the kernel), and inventing a plausible-sounding *real* name would have been exactly the wrong mapping.

### 6.1a The figures move, on a five-second tick

Two kinds of number, and conflating them is what looks fake:

- **Gauges** — `%CPU`, threads, memory, idle wakeups — **wander** around a resting level the process keeps. A smoothed walk, not a fresh random draw each interval: white noise reads as a slot machine, not a computer, and a player watching one row learns nothing from it. Threads hold for about a minute at a time, because threads do not fidget every five seconds on a real machine.
- **Counters** — CPU time, bytes read and written, packets in and out — **only ever increase**, monotonic by construction rather than by tuning. A byte total that ticked backwards is the single most obviously-fabricated thing a process table can do.

⚠ **CPU time accumulates at the process's *resting* share, never its instantaneous one.** Deriving the rate from the wandering gauge makes `intervals × rate` fall the moment the gauge dips. A test caught this.

Sorting a column therefore does what it does on a real monitor: **rows jump and re-order** as processes get busier and quieter. Which makes a row that stays pinned at the top of `%CPU` worth a second look — and is why the table's own repaint runs on its own five-second clock rather than on the game's change signal, which on an idle rig may never fire.

### 6.2 How a parasite hides

A parasite wears a costume chosen **once, when it is planted**, and never re-rolled — a disguise that changed between readings would be unfindable by construction. There are five:

| Disguise | What it does | The tell |
|---|---|---|
| **Tool twin** | Takes the exact name of a tool the player runs | Two rows called `scan --full` |
| **System mimic** | A plausible daemon name, under an odd account | Its user appears **exactly once** in the table; real service accounts appear on several rows |
| **Typosquat** | A real daemon's name, one character off (`syspolicvd`) | The real one is in the same table — sort by name and they land together |
| **Resource hog** | No name games; just sits at the top of a column | Nothing the player started accounts for it |
| **Stopped clock** | Claims heavy CPU with almost no accumulated CPU time | `% CPU` against `CPU TIME`, two columns apart |

Two more tells come free and apply to every disguise: **a five-figure pid** on something claiming to have started at boot, and **network traffic** on something that should be local (only work reaching other machines has traffic — the same rule the noise meter uses).

⚠ **Every tell is a *relationship*, never a marker.** A row against another row, or a row against itself. That is why there is no `rogue` field on the wire type, no "suspicious" style class, and no column that scores anything: a renderer that painted the answer would turn an investigation into spot-the-red-row.

⚠ **None of them is hard.** Two seconds once you know where to look, and invisible to a glance. Making the audit a ten-minute puzzle would push players back onto buying scans, which is the opposite of what §3.1 wants.

### 6.3 Killing a row, and what it costs

Right-click any row.

- **A tool of your own** stops where it is and **keeps what it managed**: a half-finished audit names half the parasites it was going to, a killed sweep reports the machines it had already reached. The result is a **truncation of the frozen answer**, never a fresh smaller roll — otherwise a kill would be a re-roll a player could force at will.
  > ⚠ **The cycles are not refunded and the recovery is the full one.** Stopping early buys back your *time*, never your *capacity*. Without that, "start everything and kill the losers" is free.
- **A parasite** dies and its cycles come back. Its **buffer is forfeit** — a crack is what takes a buffer (§5), and a kill that also paid would collapse three of §5's four responses into one. What a kill buys is *immediacy*: no breach, no attention, no puzzle. **It works without an audit**, which is the payoff for reading the table.
- **A system process cannot be killed, only restarted.** The rig needs it. Restarting takes down every running tool that depended on it, and each of those pays exactly the price above. That cascade is what makes suspecting a system row a decision rather than a free click — today `netd` carries sweeps and `auditd` carries scans.
