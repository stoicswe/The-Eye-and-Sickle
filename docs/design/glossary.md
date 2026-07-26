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
