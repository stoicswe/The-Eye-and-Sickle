# 04 — The Mining System

**Status:** Established (design sessions 1–5) — the most fully specified system in the game
**Depends on:** `01-core-resources.md`, `03-economy.md`
**Depended on by:** `07-recon-tools.md` (Provenance Tracer), `09-defense-and-hardening.md` (Detection Array, Rootkit Wrapper), `11-rig-infrastructure.md` (Cuckoo Patch, Firmware Implant, Worm Module), `05-hacking-minigame.md` (cracking)

Mining is two very different systems sharing a name: **self-mining** (the safe income floor on your own rig) and **deployed mining** (parasitic miners on other machines — the game's main asymmetric-PvP/PvE surface). Keep them conceptually separate; almost every rule differs.

---

## 1. Self-Mining

Runs on the player's own rig. Consumes allocated compute at **0.4 EC per cycle-hour** (full 100-cycle rig = 40 EC/hr).

### 1.1 The four structural properties (all load-bearing)

1. **Silent** — generates zero noise.
2. **Structurally immune to all detection and seizure** — no NPC or player can reach it, ever (Invariant I4). Not "hard to find"; *unreachable by construction*. Do not implement it as a hidden process that a good-enough scan could theoretically see.
3. **Zero heat.** The one perfectly safe action in the game.
4. Its entire cost is **the compute it occupies** and the slower rate that compute returns (Thermal Budget interaction, `01-core-resources.md` §1.3).

**Why the immunity is load-bearing:** heat can destroy a deployment network but never the floor. A player who goes hot drops from ~60 EC/hr to 40 EC/hr, not to zero. Heat stays a real cost with a real bottom, and mining remains the productive off-ramp from a hot state instead of a second punishment stacked on the first. If self-mining were ever raidable, going hot would mean total income loss and the heat system would become a fun-ejector.

### 1.2 Online-only

Self-mining runs **only while the player is in session** (matching the botnet rule, Invariant I5). This keeps the compute tradeoff real — cycles on mining are cycles not on bots, tools, and defense, and that tension only exists during a session. Offline accrual would make those cycles free and mining would stop being an active bet.

### 1.3 Block-reward model

Yield arrives in **lumps at intervals**, not a continuous trickle. Interval shortens with allocation:

| Allocation | Block interval | Effective rate |
|---|---|---|
| Full rig (100 cycles) | 4 min | 40 EC/hr |
| Half rig (50 cycles) | 8 min | 20 EC/hr |
| Quarter rig (25 cycles) | 16 min | 10 EC/hr |

Same EC/hr as a linear model — the block structure is pure *feel*: partial allocation reads as sluggish and unreliable in a way a smooth trickle would not, and every mid-block reallocation is a decision point (pull cycles now and forfeit block progress, or ride it out?).

> **[PROPOSAL]** Mid-block reallocation rule, first pass: pulling cycles off mining mid-block forfeits that block's progress entirely (no partial credit). Restoring allocation starts a fresh block. Partial credit would dissolve the decision point the block model exists to create. UI must show block progress so the forfeit is an informed choice.

> **[PROPOSAL]** Implied generalization: interval = `4 min × (100 / allocated_cycles)`, yield per block = interval × rate, i.e. every block pays ~2.67 EC at any allocation on a starting rig; bigger rigs shorten intervals rather than fattening blocks. Keeps blocks frequent enough to feel alive at all rig sizes. Needs a designer pass once rig growth curves exist.

---

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
