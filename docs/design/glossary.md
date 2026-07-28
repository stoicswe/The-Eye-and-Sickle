# Glossary

Canonical terms and their code-name conventions. When implementing, use these names so design docs and code stay searchable against each other. Disambiguations are called out where a word means two different things.

---

## Resources

- **Compute / cycles** — the rig's capacity budget; the master scarcity. Code: `compute`, `cycles`, `computeAllocated`, `computeAvailable`. A starting rig = 100.
- **Ethecoin (EC)** — in-game currency. Code: `ethecoin`, `ec`. Never buys ceilings.
- **Noise** — short-horizon, decaying visibility from actions; pools across player + bots. Code: `noise`.
- **Heat** — long-horizon Eye attention. **Personal heat** (`personalHeat`) vs. **server heat** (`serverHeat`). Distinct from noise.
- **Faction reputation** — Eye/Sickle standing (`01` §5). Code: `factionReputation`. ⚠️ Not the same thing as **validator reputation** below; never share a field or column.
- **Validator reputation** — a federation server's trust score (`../architecture/05`). Code: `validatorReputation`. ⚠️ Not the same thing as **faction reputation** above.
  - *Split into two entries 2026-07-25 (**DS-7 / ED-1**).* This was one bullet headed "Reputation" carrying both meanings, which made the distinction vivid to a human and impossible for a machine: `../client/04` §4.10's coverage check joins a term file's `canonical:` against this glossary byte for byte, and no single value could match a two-meaning bullet. Two entries make the distinction the glossary already insisted on machine-checkable.
- **Storage tiers** — Encrypted Vault (`vault`, safe), Standard Storage (`standardStorage`, exposed while online), High-Hackable Zone (`highHackableZone`, always exposed).

## Gates (`02`)

- **Ethecoin gate** — consumables/replaceables/horizontal.
- **Schematic gate** — permanent ceilings; found/earned, never bought.
- **Reputation gate** — economy-distorting-if-free items.
- **Proof-of-skill gate** — automation shortcuts; **tier-gated not count-gated**.
- **Heat-state gate** — access (vendors/contacts), bidirectional (cold-gated and hot-gated).

## Mining (`04`)

- **Self-mining** — on own rig; safe, silent, zero-heat, unseizable, online-only. 0.4 EC/cycle-hr *in expectation* — a real proof-of-work simulation since 2026-07-27 (`04-mining.md` §1.3), not a trickle.
- **Pooled mining** — self-mining against a pool's share target. Pay-per-share: a small payout every ~30s, less a 2% fee. Near-constant income. **The default**, and the floor `03-economy.md` §1 prices.
- **Solo mining** — self-mining against the full network difficulty. One whole block subsidy (160 EC) or nothing, ~every 4 hours on a full rig. No fee, so ~2% more in expectation and roughly twenty times the variance.
- **Share** — a proof of *partial* work, worth something only to the pool that set its target. Never a block; never appears on the chain.
- **PPS** (pay-per-share) — the pool pays a fixed amount per accepted share whether or not it found a block, and charges a higher fee for carrying that risk. Steadiness comes from the share target, so pool size does not affect it.
- **PPLNS** (pay-per-last-N-shares) — the pool pays only out of blocks it actually finds, in proportion to your work. Lower fee; **pool size is the variance knob**.
- **Vardiff** — a pool tuning each miner's share target to that miner's own hashrate, so shares land at a steady pace whatever the rig. Why pooling smooths a small rig as well as a large one.
- **Settlement** — the pool handing over what it owes, every 60s. Shares accrue to an unpaid balance between settlements; a solo block never waits. Paces the *ledger*, never the income.
- **Unpaid balance** — earned, credited by the pool, not yet handed over. Shown on the mining panel with a countdown.
- **Block** — minted every ~14 minutes and **won** by one miner, drawn with probability equal to their share of the chain's hashrate. Not raced.
- **Chain address** — `0x` + 40 hex, derived from the character id. Ethereum's shape; this chain's mechanics.
- **Coinbase** — a block reward. Rendered as a transfer from the zero address, because the coins did not exist before the block, and costing no gas because there was no transaction to execute.
- **Mempool** — transactions waiting for a miner. A block holds 200; the queue is usually deeper, which is what makes a fee a bid.
- **Fee tier** — Economy / Standard / Priority. Buys position in the queue, never a guarantee. Deliberately too small to be an economy sink.
- **Gas price** — a fee expressed as a *rate* (minor units per million gas). The only unit priority may be quoted in; a fee total says nothing about position.
- **Projected block** — what the next block *would* hold if mined now. A queue snapshot, never a schedule.
- **Block subsidy** — what one block pays: 160 EC. A miner also takes every fee in the block.
- **Total work** — accumulated difficulty. What decides between two chains; block height never does. The one knob that changes solo mining's feel without disturbing the economy table.
- **Deployed miner** — parasite on another machine; consumes **host** compute; only offline-earning source; buffer-capped.
- **Control channel** — the 3-cycle/miner reservation the deployer pays per live deployed miner.
- **Yield buffer** — on-host accumulation while deployer is offline; 4-hr cap per miner; the prize in a crack.
- **Sweep** — Eye removal of NPC-hosted miners; probability = deployer heat; correlated (network-wide), not attritional.
- **Crack / Kill / Hijack / Sabotage** — the four responses to a discovered foreign miner (`04` §5).
- **Rootkit-wrapped** — a deployed miner hidden from routine scans but not from manual audit (`09`).

## Network & discovery (`07` §5, [PROPOSAL])

- **Network sweep** — the discovery action: probes machines within the hop ceiling of the current vantage. Code name `net-sweep` / `SweepTier`. ⚠ **Not the Eye's "Sweep" above**, which removes NPC-hosted miners — two unrelated mechanics that English gave the same word. Code never says `sweep` unqualified: the discovery action is `net-sweep*` and `NetRules.beginSweep`; the Eye's is miner seizure.
- **Sweep tier** — `BASE` / `WIDE` / `DEEP`. Buys **sensitivity**, never reach; running one costs cycles and noise and never ethecoin.
- **Vantage** — the machine hops are measured *from*. Moved with `connect`, and only to a host the player holds. Position substitutes for reach, and position is earned rather than bought.
- **Hop ceiling** — how far a sweep sees from the vantage: 1, or 2 with the Topology Mapper schematic. The one number ethecoin may never move (**I2**).
- **Sighting** — one machine *as the player knows it*, never as it is. Distinct from the host record behind it.
- **Contact / identified** — detected-but-untyped versus type established. The gap between them is what the Passive Sniffer sells.
- **Bridge stub** — the far side of a bridge: publishes the peer **server's name** and nothing else.
- **Folder / filing** — the player's own bookmarking of discovered machines (`07` §5.4). Mechanically inert: no gate, no cost, no limit, and nothing in the rules reads it. Code name `FolderState` / `FolderRules`; a machine's membership is `NodeState.folderId`.

## Compute & theft (`01` §1.3, §1.5)

- **Thermal recovery** — how spent cycles come back: slower the busier the rig, **capped at 5 minutes** clean and **10 minutes** on a rig being robbed. Code name `ThermalRules.recoveryTime`.
- **Stolen cycles / theft share** — capacity held by a process the player did not put there, over the rig's ceiling. Slows every task and every recovery, **whether or not it has been found**. Code name `ComputeRules.stolenShare`.
- **Discovered (a parasite)** — whether an audit has *named* the process. ⚠ Until it is true the rig monitor attributes nothing to it and it is not a breach target; the cycles are simply missing. Code name `MinerState.discovered`.
- **Unaccounted-for** — ceiling minus allocated minus recovering minus free. Non-zero exactly when something unaudited is taking cycles. Drawn as dark unlabelled cells, never as an alarm.
- **Armed (a breach target)** — chosen but not committed to. Arming is free and reversible; **START BREACH** is the spend. Code name `BreachArming`.

## The breach (`05`, [PROPOSAL])

- **Breach attempt** — one instance of the core hacking minigame against a target node.
- **Puzzle class** — the *kind* of puzzle (Enumeration/Credential/Logic/Timing/Traversal — proposed).
- **Difficulty tier** — integer scaling knob; also the proof-of-skill and salvage-guard threshold.
- **Trace** — defender-side attribution meter that races the player's breach; completes → failure.
- **Layer** — one class-instance within a multi-layer target; Overflow Kit bypasses one.
- **resolutionRecord** — persisted `{class, tier, liveOrDormant, outcome}`; feeds proof-of-skill and salvage guards.

## Bots (`10`)

- **Frame** — a bot *blueprint* (the gated capability). Types: Recon, Miner, Sentinel, Breacher, Mimic, Scavenger.
- **Instance** — a built, running bot (EC cost). Loss destroys instance + socketed tools, never the frame.
- **Backlog timer** — shrinking per-item response window that scales with active bot count.
- **Split attention** — parallel, non-queuing penalty applied to all simultaneous engagements.
- **Schematic contribution material** — tier-gated partial-progress salvage from a lost bot.

## Stealth & identity (`08`, `12`)

- **Relay chain** — onion routing; framework (schematic) + hops (EC/session).
- **Ghost Protocol** — installable identity reset; wipes personal heat, forfeits handle/leaderboard/reputation.
- **Dead Drop** — untraceable transfer, defeats the public ledger.
- **Burner handle** — second identity, separate heat, halves progression presence.
- **Informant** — hidden randomized role (NPC or player); removed via evidence path or mass-vote override.
- **Named-hacker** — top personal-heat + reputation state; triggers targeted Eye pursuit.

## The operating system (`../client/`)

- **uOS** — **the operating system every rig in the game runs, and the baseline for every OS-flavoured concept in the game.** When a design doc, a tool, a window or a term refers to processes, the filesystem, permissions, devices, logs, shells, daemons or networking, it means *uOS's* version of that thing. uOS is deliberately **Unix-like**, which is what makes the educational goal work: a player learning uOS is learning transferable Unix, not a bespoke fiction. Code: `uos`.
  - **Casing is a convention, not a preference.** Write **uOS** in prose, in UI copy and in anything a player reads; write `uos` in identifiers — theme ids, CSS classes, stylesheet filenames, config keys. Exactly the macOS/`macos` split, and the docs already follow it for the host platforms.
  - **Variants** (`../client/03-story-theme.md` §2.2) are suffixes on the identifier: `uos` (default), `uos-amber`, `uos-phosphor`, with `-hc` as a high-contrast modifier (`uos-hc`, `uos-amber-hc`, …).
  - **uOS is the OS; a theme is how it is drawn.** Both client theme families render the *same* uOS state: the **native** family draws it using the host platform's conventions (`../client/02`), the **uOS** family draws it as uOS's own operator console would (`../client/03`). Neither is "the real one" — the player's laptop runs macOS/Windows/Linux, their *rig* runs uOS, and the client is the window onto it. This is why "only the skin changes" holds: there is one system underneath, drawn two ways.
  - ⚠ **[PROPOSAL]** — the name and the baseline role are decided; uOS has no system doc of its own yet. If one is written, it belongs beside the world/narrative material and should not restate `../client/04-terminology-and-education.md`'s mapping tables.

## Architecture (`../architecture/`)

- **DID** — decentralized identifier (AT Proto); the portable player ID. Code: `did`.
- **PDS** — Personal Data Server (AT Proto). Used for *identity only*; never game state (Invariant I14).
- **Home server** — a self-hosted Spring Boot + Postgres game server.
- **Federation directory** — opt-in list of public servers sharing non-adversarial data.
- **Validator quorum** — sampled committee of opted-in servers that signs cross-server duel outcomes.
- **Provenance record** — detached-JWS-signed, per-item event chain proving legitimate item history.
- **BFT threshold** — 2f+1 of 3f+1 weighted validator power required for consensus.
- **Equivocation** — a validator signing two conflicting outcomes; cryptographically provable, hard-slashed.

## Factions & world (`00`, `14`)

- **The Eye** — the surveillance state; systemic automatic pursuer.
- **The Sickle** — the decentralized resistance coalition; maps onto the federation of home servers.
- **Named-hacker** — see above (identity section).
