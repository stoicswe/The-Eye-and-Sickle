# 01 — Core Resources

**Status:** Established (design sessions 1–5); implementation notes marked
**Depends on:** `00-vision-and-pillars.md`
**Depended on by:** effectively everything; `03-economy.md` and `04-mining.md` in particular

The game runs on six resource systems: **compute**, **ethecoin**, **noise**, **heat**, **reputation**, and **storage**. Compute is the master scarcity (Pillar 2); everything else orbits it.

---

## 1. Compute — the master scarcity

The rig's cycle budget. A starting rig has **100 cycles**. Compute is not a currency that gets spent and vanishes — it is *capacity* that gets **allocated**, and most allocations are reservations that persist while the thing they power is running.

### 1.1 What consumes compute

| Consumer | Model | Reference |
|---|---|---|
| Active tools | Per-use, or reserved while equipped | `06-intrusion-tools.md`, `07-recon-tools.md` |
| Botnet frames | Permanent reservation while the bot is running | `10-botnets.md` |
| Self-mining | Whatever the player allocates | `04-mining.md` §1 |
| Deployed-miner control channels | **3 cycles each**, permanent while the miner is live | `04-mining.md` §2 |
| Defensive detection sweeps / arrays | Permanent reservation while armed | `09-defense-and-hardening.md` |
| Relay chain hops | Per hop, per session | `08-stealth-and-noise.md` |

### 1.2 The rule that holds the economy together

> **Compute is never purchasable with ethecoin.** (Invariant I1)

If money could buy cycles, mining income would buy the capacity to mine more, and the master scarcity would collapse into a compounding flywheel. Rig capacity expands **only** through schematics and story milestones (`11-rig-infrastructure.md`). Every system doc in this repo assumes this holds.

### 1.3 Spent cycles recover over time — Thermal Budget

Cycles spent on discrete actions (e.g. scans) do not return to the pool instantly. They recover over a period governed by the rig's **Thermal Budget** stat (`11-rig-infrastructure.md`), and **recovery is slower the closer the rig sits to capacity**. A lean rig gets cycles back quickly; an overextended rig is effectively down those cycles for a long stretch — exactly when it can least afford to be.

This is the single stat that explains why a loaded rig *feels* sluggish, and it converts nominal costs into real opportunity costs. Design intent: overextension should be punished by the physics of the rig, not by a rulebook.

### 1.4 UX requirement

The player must always be able to see, at a glance: total cycles, allocated (by consumer), available, and recovering (with time-to-recover). The compute ledger is the game's most important HUD element. In the multi-window client this justifies a dedicated, always-on-top **rig monitor window** (see `../architecture/01-tech-stack.md`).

> **[PROPOSAL]** Recovery curve, first pass: cycles recover at `base_rate × (1 − load_factor)^k` where `load_factor` = allocated/total, `base_rate` and `k` come from Thermal Budget tier. At 50% load and T1 thermal, a 35-cycle Thorough Scan should take ~2× its run duration to fully recover; at 85% load, ~5×. Numbers exist to be playtested, but the *shape* (superlinear pain as load approaches 100%) is the point.
>
> ⚠ **Recovery starts when the work ends, so those anchors compose rather than overlap** (`04-mining.md` §3.2, decided as **UI-6** on 2026-07-26). Work with a published duration *holds* its cycles while it runs; the curve above begins at the moment they are released. So the ~2× anchor means a Thorough Scan at 50% load is **1× held + 2× recovering ≈ 3× its run duration** before the rig is whole, and ~6× at 85% load. The anchors themselves are unchanged — they were always statements about the recovery, not about the total — but anyone re-tuning `base_rate` or `k` should know which of the two they are moving.
>
> The load factor that sets the curve is read **excluding the releasing allocation** — the load the returning cycles are coming home to, not the one they were part of. Otherwise a scan pays a recovery penalty for its own cycles, which compounds a cost this doc never asks to compound.

---

## 2. Ethecoin (EC)

The in-game cryptocurrency, obtained by mining (`04-mining.md`). Active hacking also yields EC (loot, contracts) at the rates anchored in `03-economy.md`.

### 2.1 What EC buys — and what it never buys

EC buys **consumables, replacements, and horizontal options**. It **never buys a ceiling** (Invariant I2): no compute, no vault capacity, no permanent capability increases. The full gate assignment rule lives in `02-unlock-gates.md`.

### 2.2 The public ledger

Transactions are visible on a **public ledger by default**. This is a gameplay feature, not blockchain flavor: it gives investigators — player and NPC — something to work with. Moving EC or loot untraceably requires a **Dead Drop** (`08-stealth-and-noise.md`), which is reputation-gated.

Implications to preserve in implementation:

- NPC investigators (The Eye) and player investigators can follow EC flows to build evidence (feeds the informant/evidence system, `12-identity-and-social.md`).
- Laundering is a gameplay verb, not an automatic service.
- The federated-architecture consequence: within a home server the ledger is that server's Postgres; **cross-server transfers are provenance events** (`../architecture/04-item-provenance.md`).

---

## 3. Noise

Noise is the short-horizon visibility cost of *doing things*. It is generated by actions and read by defenders and by The Eye's heuristics.

### 3.1 Aggregation rules (established)

- Noise aggregates across **the player and all their active bots into a single pool**. Fewer bots = less noise, with no special-case rule needed.
- Within an engagement, noise **averages across participants** as more join — crowds are individually quieter but collectively present.
- **Proximity = network-graph hop distance.** When someone joins an engagement, every existing participant is notified that *someone* joined; the **detail** of what each participant sees about the joiner (and vice versa) scales with hop distance between the joiner's entry node and that participant's position, and with how much noise each is contributing.

### 3.2 Design consequences

- Noise is *per-action and decaying*; heat is *accumulated standing* (§4). Noise is tactical, heat is strategic.
- Tools are priced in noise as a first-class stat (see tool tables in `06`/`07`) — from `None` (Passive Sniffer, Zero-Day) to `Very high` (Overflow Kit).
- Noise management is its own toolbox (`08-stealth-and-noise.md`): scrubbing it after the fact, spoofing its attribution, shaping its curve, or decoying it elsewhere (Mimic frame).

> **[PROPOSAL]** Concrete model, first pass: noise is a scalar per player-pool, decaying exponentially (half-life ~90s in-engagement). Each action adds its noise cost; crossing defender-side thresholds triggers escalating responses (see `05-hacking-minigame.md` §trace). Threshold values per target tier are a tuning table, not per-target hand-tuning.

---

## 4. Heat

Heat is long-horizon attention from The Eye. Two tiers:

### 4.1 Personal heat

Accrues from the player's own actions. High personal heat raises sweep probability against their deployed network (`04-mining.md` §4), changes vendor availability (`02-unlock-gates.md` §heat-state), and at the top end — combined with substantial reputation — tips the player into **named-hacker** status: targeted, personal pursuit by The Eye rather than dragnet attention.

### 4.2 Server heat

The average Sickle activity across the population. Drives institutional Eye escalation affecting **everyone** (smarter heuristics, harsher sweeps) and gates world-state narrative thresholds (`14-world-and-narrative.md`). Server heat is why other players' recklessness is *your* problem — deliberate social pressure inside the fiction.

### 4.3 Reducing heat

- **Laying low:** passive decay, deliberately slow. The intended off-ramp is self-mining (safe, zero-heat, income floor — Invariant I4).
- **Ghost Protocol** (`08-stealth-and-noise.md` §3): total identity reset. Wipes personal heat entirely; costs the handle, leaderboard position, and all reputation tied to that name. Deliberately painful enough that laying low is usually the better choice.

### 4.4 What heat never does

Heat gates *access* (which vendors/contacts are reachable — in both directions; being hot opens the black market). It never gates *ownership*, and defending your own rig never generates it (Invariant I9).

---

## 5. Reputation

Per-faction standing (Eye / Sickle), moved by player choices. Key established rules:

- Reputation eventually forces a **binary commitment** to one faction.
- Abandoning a side **resets that reputation, spikes heat temporarily, and forfeits faction-specific tools**.
- Reputation is an unlock gate class of its own (`02-unlock-gates.md`) for anything that would distort the economy if freely available.
- Named-hacker status requires substantial reputation, not just heat — you have to *matter* to be hunted by name.

Note the distinct namespace: **validator reputation** in the federation layer (`../architecture/05-validator-quorum.md`) is an unrelated server-level trust score. The glossary disambiguates; code should too (`factionReputation` vs `validatorReputation`).

---

## 6. Storage tiers

Where a player's items and EC-adjacent loot live. Three tiers with a strict capacity/exposure trade:

| Tier | Capacity | Exposure |
|---|---|---|
| **Encrypted Vault** | Small | Never exposed |
| **Standard Storage** | Limited | Exposed while owner is online |
| **High-Hackable Zone** | Large | Always exposed (raidable even while owner is offline) |

Established rules:

- **Vault capacity scales sub-linearly** with progression and is schematic-gated (+ reputation), never purchasable (Invariant I12). Rationale: linear-or-better scaling produces late-game veterans who are functionally unraidable, which kills the risk economy for exactly the players holding the most valuable gear.
- **Anything assigned to a bot leaves the vault and becomes mid-risk** (`10-botnets.md`). Vault items are safe until you decide to put them to work — safety and productivity are mutually exclusive by design.
- The High-Hackable Zone is the target of offline raids located via Ping Sweep (`07-recon-tools.md`) and defended by Honeypot Stash decoys, Auto-Counter Daemon, etc. (`09-defense-and-hardening.md`).

> **[PROPOSAL]** Starting capacities, first pass: Vault 6 slots / Standard 20 slots / High-Hackable 60 slots, where a "slot" holds one tool or one stack of consumables. Vault expansion via Cold Storage Expansion schematic: +4, then +3, then +2, then +1 (sub-linear by construction, hard cap 16). Numbers for playtest; the sub-linear *shape* is established design.

---

## 7. Resource interaction summary (the loops)

- Compute → allocated to mining → EC → spent on tools/consumables → enables hacking → yields EC + **noise** → noise accumulates toward **heat** → heat raises sweep risk against deployed miners and shifts vendor access → pushing the player back toward safe self-mining (compute) until heat decays.
- Storage risk loop: earn gear → vault fills (small) → overflow sits in exposed tiers or gets socketed into bots → exposure creates PvP/NPC raid content → losses are replaceable because they're EC-class items (Invariant I2 keeps losses non-permanent).
- Faction loop: EC → Payout Splitter → Sickle reputation (poor conversion rate; the faction-side EC sink) → reputation-gated tools → deeper operations → story access.
