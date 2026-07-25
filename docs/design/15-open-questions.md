# 15 — Open Questions & Design Backlog

**Status:** Living. Established open questions (OQ-1…7) are from the source design doc §13; proposal-raised questions (prefixed P/D/S/N) come from the [PROPOSAL] docs and are only "real" if those proposals are adopted.
**Depends on:** all design docs
**Depended on by:** nobody — this is the leaf

This is the single place to look for "what's undecided." When a question is resolved, move the resolution into the relevant system doc and record it in §3 here.

---

## 1. Established open questions (from source design)

These carry over verbatim in intent from the consolidated design doc. Each has a "so what" and a "watch for" so a future session knows why it's open and what data resolves it.

**OQ-1 — Sink calibration.** The `03-economy.md` sinks are theoretically sufficient but unverified against real player income. *Watch for:* aggressive players accumulating large EC balances (sinks too weak) or cautious players unable to afford replacements (faucets too weak). *Resolves with:* playtest income/sink telemetry (`03` §6). Status: adequate for now.

**OQ-2 — Gate consolidation.** Five unlock currencies (`02`) may be too much cognitive load. *Watch for:* players confused about why they can't buy something. *Plan of record:* collapse schematic + proof-of-skill into one "field research" track if bloat appears. Do **not** add a sixth gate meanwhile. Status: fine for now.

**OQ-3 — Correlated-loss variance.** NPC sweeps (`04` §4) make deployed yield far swingier than the `03` averages suggest. *Watch for:* total wipes reading as "unfair" rather than "the crackdown came." *Fallback:* partial-sweep model (lose a fraction, not all). Status: intended as dramatic; verify it lands.

**OQ-4 — Buffer capacity calibration.** 4 hours/miner (`04` §2.3) is a starting figure. *Watch for:* how real session lengths and log-off-with-network-live frequency interact with the cap. *Resolves with:* session-length + offline-network telemetry. Status: starting value.

**OQ-5 — Schematic material conversion rate.** How much generic contribution material a schematic costs (`10` §1a, `02` §2.2) is unset. *Watch for:* too cheap → bot sacrifice becomes a reliable ceiling-grind despite the tier gate; too expensive → salvage is decorative. Status: unset, needs a first number.

**OQ-6 — Detection-array redundancy.** With discovery resolved via manual investigation + scan tiers (`04` §3), the passive Detection Array (`09`) may be redundant. *Decision needed:* confirm it has a distinct role or fold it into scan efficiency. Status: open, low urgency.

**OQ-7 — Cracking vs. detection incentive.** Profitable hostile-miner cracking (`04` §5) slightly weakens the case for committing compute to security at all. *Watch for:* whether "leave miners to crack them for profit" dominates "defend to prevent them." Status: probably healthy, visible only in playtest numbers.

## 2. Proposal-raised questions (conditional on adopting the [PROPOSAL] docs)

These are **not** established design. They exist because the proposal docs filled gaps, and each gap-fill raised its own questions. If the user rejects a proposal, delete its questions.

### From `05-hacking-minigame.md` (the whole doc is proposed)
- **P-1:** Is a 5-class puzzle family right, or does it dilute mastery? (Could ship 2–3.)
- **P-2:** Real-time trace timer vs. turn/probe-budget. Turn-based is more accessible and arguably truer to Pillar 1 — strong candidate to switch.
- **P-3:** How much does manual play actually beat bot play (seconds)? This is *the* number behind Invariant I10; unmeasurable until the real puzzle exists.
- **P-4:** Do the five classes map cleanly to distinct tools (`06`/`07`), or do some tools end up class-less?

### From `13-multiplayer-and-federation-play.md`
- **D-1:** Exact boundary between quorum-requiring "duels" and local PvP. Proposed: any cross-home-server transfer of a provenanced item invokes quorum; else local.
- **D-2:** Is multiplayer opt-in per-session, per-server, or per-account? Proposed: per-server.

### From `12-identity-and-social.md`
- **S-1:** Is the informant mass-vote quorum per-home-server or federation-wide? Leans per-home-server (fiction + avoids cross-server vote consensus).
- **S-2:** Concrete Eye-side incentives for opting in as informant, without being strictly better than honest play.
- **S-3:** Can the override be weaponized against productive players by a coordinated group? Verify "real costs regardless of outcome" is a steep enough brake.

### From `14-world-and-narrative.md`
- **N-1:** Concrete server-heat threshold bands and what each unlocks.
- **N-2:** Ordered critical-path schematic beats (starting rig → endgame) as location/story/capability triples. Highest-value narrative task.

### From `03-economy.md`
- **E-1 (proposal):** Confirm the "schematic unlocks, EC installs" reconciliation for vault expansion (`03` §4). Reconciles the source listing "vault expansion costs" as an EC sink with capacity being schematic-gated.

### From architecture docs
- **A-1:** Per-item vs. per-holder provenance chains — **resolved** in Tech Chat 2 to **per-item** (`../architecture/04`). Listed here only to note it's closed.
- **A-2:** Optional stake/bonding layer for validators — explicitly deferred in Tech Chat 2 (`../architecture/05`). Not needed for v1.
- **A-3:** JWS/JSON vs. COSE/CBOR envelope — Tech Chat 2 chose JWS/JSON for debuggability; revisit only if wire size matters (`../architecture/04`).
- **A-4:** **Data-access mechanism — JPA/Hibernate vs. jOOQ vs. plain JDBC.** Explicitly deferred by `../architecture/01` §5 and `06` §3. The project scaffold provisionally wired **JPA/Hibernate** (`server/pom.xml`) so the server module had a working persistence stack; that is a **[PROPOSAL]**, not a decision. The real tension (`06` §4): the economy is intensely cross-referential, which suits relational mapping, but the signed provenance payloads and item attrs are document-shaped `jsonb`, which JPA handles awkwardly and jOOQ handles well. Cheap to change now — no entity exists yet — and expensive after fifty do. **Decide before the first real entity lands.**
- **A-5:** **Provenance envelope signature shape.** `../architecture/04` §3 shows a single `"signature"` object while §3.1 shows a `"signatures"` array whose elements omit `alg`; the doc never reconciles them. The scaffold's `ProvenanceEnvelope` models the general case — always a list, `alg` always present per block, single-issuer records being a list of length one — because one shape is much easier to verify than two. Confirm before anything signs for real.
- **A-6:** **Transport security between clients/servers and between servers.** Not addressed by Tech Chat 1 or 2 at all. See `../architecture/07-transport-security.md` **[PROPOSAL]**.

### From the protocol implementation (2026-07-23)

Building `protocol/` to the checklist in `../architecture/04` §8 forced decisions the source docs never made. Each is implemented as the *minimum* that satisfies the spec, tagged `[PROPOSAL]` in the code, and listed here. **The first three are one-way doors — settle them before anything signs for real.**

**Provenance chain (`../architecture/04` §7):**

- **P-1: `prevRecordHash` format.** §2 says only "sha256-of-previous-record-in-chain". Implemented as SHA-256 over the **canonical payload** bytes (not the envelope), rendered `sha256-` + 64 lowercase hex. Hashing the payload means the link breaks on content change *and only* on content change — re-signing, or adding a validator signature, leaves the chain intact. Hex because the string is itself inside the next record's signed bytes, and base64url has padded/unpadded and multi-alphabet spellings that would let one history hash two ways.
- **P-2: an unverifiable signature inside a duel quorum is a hard fault, not a discard.** §7.2 says "the summed reputation-weight of *valid* signatures", which could be read as silently dropping bad ones and passing if the rest clear the threshold. The implementation rejects instead. Consequence: a rotated or revoked validator key permanently un-recognizes the item. Safer direction, but a real choice.
- **P-3: the quorum threshold is enforced on validator *count* as well as weight.** §7.2 and `../architecture/05` §1 speak only of weighted power — but weight alone lets a single validator holding most of a committee's reputation decide a cross-server outcome by itself, which Invariant I15 forbids. Requiring both never rejects the doc's worked example (5 of 7).
- **P-4: quorum arithmetic for a committee whose size is not exactly `3f+1`.** Undefined by the docs; implemented as the standard BFT floor `⌊2N/3⌋+1`, which equals `2f+1` exactly at N=7.
- **P-5: genesis must be an `initial_mint`, and `initial_mint` may appear only at genesis.** §2/§6.1 fix depth 0 + null `prevRecordHash` but never fix the event type.
- **P-6: verification requires a contiguous chain starting at genesis.** §6.1's "records N through N+20" implies partial windows are a real use case, but a window not rooted at genesis proves nothing. Partial verification against a trusted checkpoint would need designing.
- **P-7: replay policy.** §2 says `nonce` + `timestamp` "prevent replay" without saying what a verifier checks. Implemented as: nonce unique within the chain, timestamps non-decreasing, and not beyond `now + maxFutureSkew` — with both `now` and the skew as caller parameters. No lower bound on age; items are old by design.

**Wire vocabulary:**

- **P-8: ethecoin precision.** No doc states EC's granularity; implemented as 100 minor units because two decimals is the finest any published figure uses. **Must be settled before any ledger row exists.**
- **P-9: `ComputeConsumer.DEPLOYED_MINER`.** `01` §1.1's table lists only the consumers on *your own* rig, so the host-side draw of a foreign miner has no name — yet Invariant I6 and `../architecture/06` §1 constraint 4 require one, or a discovered parasite is unrepresentable.
- **P-10: `DifficultyTier` bounded 1–5** per `05` §3.3, enforced at the wire boundary. Widening it later is a wire-compatibility change.
- **P-11: faction reputation scale and sign.** `../architecture/06` types it `numeric`; nothing says whether standing may go negative. Modelled as an integral score permitting negatives.
- **P-12: may an ethecoin-primary gate carry a reputation or proof-of-skill secondary?** `02` §1.1's first-yes-wins order implies those would take primacy, but §3's Zero-Day row lists a split without saying which half classifies the item. Not enforced.

**Transport (`../architecture/07`):**

- **P-13: attestation validity window is `[notBefore, notAfter)`** — half-open. §4.1 says "valid from T1 to T2" and never states inclusivity. Back-to-back key rotation needs a decided answer or it gets a one-instant gap.
- **P-14: an absent prologue means an empty prologue**, not a wildcard.
- **P-15: the wire-format field cap is 1 MiB.** §4.1 says lengths are "bounded" and names no number. A denial-of-service bound rather than a balance value, so it stays in `protocol`.

### From peer discovery (`../architecture/08`, 2026-07-24)

- **G-1: flag propagation** across the directory is unspecified (`../architecture/03` §4 leaves the flagging mechanism `[PROPOSAL]`). Equivocation is automatic; softer fraud and gossip spread need the federation designer.
- **G-2: descriptor freshness vs. churn** — re-probe/re-exchange cadence and how long a silent peer stays listed. Tuning, not invariant.
- **G-3: Sybil resistance in peer exchange** — a hostile peer advertises many fakes; bounds cap the blast radius, but whether reputation should gate belief in peer exchange is open.
- **G-4: bootstrapping the local descriptor** — `LocalDescriptorSource` is wired empty until the descriptor builder is connected to the server's signing identity (a wiring seam, below).

### Player state portability (`../architecture/09`, 2026-07-24)

The 3-slot character model (online-only) plus DID→home resolution (E), home-server backup/restore (B), and verifiable migration of the provenanced subset with economy reset (C). Design is decided (`../architecture/09`); these are the sub-decisions it leaves open:

- **Q-item-keying (LOAD-BEARING — decide before the feature is truly "separate characters"):** `items.holder_did`, `ledger_transactions.from/to_did`, and `deployed_miners.deployer_did` key on the DID, which is now an *account* of up to 3 characters — so those systems currently **share** state across an account's characters, contradicting the design. Options in `../architecture/09` §9 (recommend option 3: a derived per-character identity). Touches the Established provenance holder model, so it needs a conscious decision. Also: `economy AccountRepository.findByDid` returns the wrong result / throws once an account has >1 character — the concrete face of this.
- **Q-cap-race:** converging a simultaneous over-creation of characters to ≤3 *recognized*, and telling the loser (soft, eventually-consistent enforcement given I15).
- **Q-home-auth:** whether the account's DID key co-signs its character-home bindings, or the home-server signature alone suffices.
- **Q-slot-scope:** cap is network-wide vs. per-federation-directory — two disjoint federations cannot see each other's counts.
- **Q-economy-seed:** what a reset (migrated-in) character starts with — 0 EC + base rig, or a small onboarding grant. A balance value for `03`.
- **Q-retire-window:** how long a migrated character's retired shell is retained at its old home for dispute/audit before reaping.

### Server wiring seams (integration, 2026-07-24)

The six server slices were built in isolation and could not implement each other's ports; the integrator wired safe `@ConditionalOnMissingBean` defaults so the context starts. Each is a place a real implementation should later supersede the default:

- **W-1: external DID→public-key resolution** is stubbed to resolve nothing (`DidPublicKeyResolver`, `PeerKeyResolver`, `ValidatorKeyDirectory`). A signature from another server is therefore currently unverifiable → the item is not recognized (safe). Real resolution needs a network client for `did:plc` (directory) and `did:web` (HTTPS) — `../architecture/02` §5.
- **W-2: schematic ownership** (`SchematicHoldings`) denies everything by default, so the schematic gate fails closed. The progression slice owns the real state.
- **W-3: gated-offering content** (`GatedOfferingCatalog`) is empty — offerings are game content, not code.
- **W-4: player sessions** are in-memory (`InMemoryPlayerSessionStore`), fine for one allowlist-bounded process; a multi-instance deployment would need a shared store.
- **W-5: faction-tool forfeiture** (`NoOpFactionToolForfeiture`) records intent and does nothing — the real forfeiture on faction abandonment (`01` §5) is unbuilt.
- **W-6: AT Proto authentication** falls back to the self-guarding dev provider, which refuses every sign-in unless `eyeandsickle.identity.dev-signin.enabled` is set. A production node has no working sign-in until W-1's network resolver lands.

## 3. Resolution log

Record resolutions here when they land (date — question — outcome — where it moved).

- 2026-07-23 — **Bot destruction vs. degradation** — resolved to **total loss + tier-gated salvage** — moved to `10` §1a. (Source: design session 3.)
- 2026-07-23 — **Counter-attack time budget** — resolved to **parallel, split-attention, non-queuing** — moved to `10` §1b. (Source: design session 3.)
- 2026-07-23 — **Detection signal legibility** — resolved to **signal strength is what you pay for; manual-free vs. scan-costly** — moved to `04` §3. (Source: design sessions.)
- 2026-07-23 — **Provenance chain shape** — resolved to **per-item** — recorded in `../architecture/04`. (Source: Tech Chat 2.)
- 2026-07-23 — **Full technology stack** — resolved end-to-end — recorded in `../architecture/`. (Source: Tech Chat 1.)
- 2026-07-23 — **JCS canonicalization rejects invalid Unicode** — resolved to **reject unpaired surrogates** — implemented in `protocol` `JsonCanonicalization`. Found while writing RFC 8785 conformance tests: the bundled canonicalizer passed a lone surrogate through and UTF-8 encoding then substituted `?`, so `{"s":"\ud800"}` and `{"s":"\udbff"}` produced **identical signing bytes**. One signature covering two distinct payloads is a forgery primitive, and a verifier is exactly where an untrusted federated payload arrives. RFC 8785 §3.2.2.2 requires the error; the library does not raise it, so the wrapper does.

## 4. How to use this doc

- Before starting design work on any system, check here for its open questions.
- When you make a decision that closes one, **move the decision into the system doc** (that's the source of truth) and log it in §3 — don't leave the answer only here.
- Proposal questions (§2) are provisional; treat the proposal docs' [PROPOSAL] tags as the signal that nothing there is load-bearing yet.
