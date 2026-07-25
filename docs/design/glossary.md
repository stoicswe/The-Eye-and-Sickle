# Glossary

Canonical terms and their code-name conventions. When implementing, use these names so design docs and code stay searchable against each other. Disambiguations are called out where a word means two different things.

---

## Resources

- **Compute / cycles** — the rig's capacity budget; the master scarcity. Code: `compute`, `cycles`, `computeAllocated`, `computeAvailable`. A starting rig = 100.
- **Ethecoin (EC)** — in-game currency. Code: `ethecoin`, `ec`. Never buys ceilings.
- **Noise** — short-horizon, decaying visibility from actions; pools across player + bots. Code: `noise`.
- **Heat** — long-horizon Eye attention. **Personal heat** (`personalHeat`) vs. **server heat** (`serverHeat`). Distinct from noise.
- **Reputation** — ⚠️ *two unrelated meanings.* **Faction reputation** (`factionReputation`, Eye/Sickle standing, `01` §5) vs. **validator reputation** (`validatorReputation`, federation server trust score, `../architecture/05`). Never conflate in code.
- **Storage tiers** — Encrypted Vault (`vault`, safe), Standard Storage (`standardStorage`, exposed while online), High-Hackable Zone (`highHackableZone`, always exposed).

## Gates (`02`)

- **Ethecoin gate** — consumables/replaceables/horizontal.
- **Schematic gate** — permanent ceilings; found/earned, never bought.
- **Reputation gate** — economy-distorting-if-free items.
- **Proof-of-skill gate** — automation shortcuts; **tier-gated not count-gated**.
- **Heat-state gate** — access (vendors/contacts), bidirectional (cold-gated and hot-gated).

## Mining (`04`)

- **Self-mining** — on own rig; safe, silent, zero-heat, unseizable, online-only. 0.4 EC/cycle-hr.
- **Deployed miner** — parasite on another machine; consumes **host** compute; only offline-earning source; buffer-capped.
- **Control channel** — the 3-cycle/miner reservation the deployer pays per live deployed miner.
- **Yield buffer** — on-host accumulation while deployer is offline; 4-hr cap per miner; the prize in a crack.
- **Sweep** — Eye removal of NPC-hosted miners; probability = deployer heat; correlated (network-wide), not attritional.
- **Crack / Kill / Hijack / Sabotage** — the four responses to a discovered foreign miner (`04` §5).
- **Rootkit-wrapped** — a deployed miner hidden from routine scans but not from manual audit (`09`).

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
