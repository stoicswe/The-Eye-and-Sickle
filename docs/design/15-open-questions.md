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

### From the client design set (`../client/`, 2026-07-25)

The client doc set (`../client/README.md`) raises ~90 numbered questions, each owned by the document that
found it (`CL-` 00, `V-` 01, `PN-` 02, `SK-` 03, `T-` 04, `WL-` 05, `RI-` 06, `AX-` 07). Those are design
detail and live there. These are the ones that block implementation or need a product decision:

- **AX-1 (platform gap, verify first): JavaFX appears to have no Linux screen-reader bridge.** Its
  accessibility implementation targets Windows UI Automation and macOS VoiceOver; evidence (not proof)
  says Linux/Orca is unsupported. If true this is a platform limitation we cannot fix in our own code, and
  it bounds what we may honestly promise Linux players. Confirm against JavaFX 26 before any accessibility
  claim ships.
- **AX-5 / V-2 / PN-2 (measured defect): the semantic palette fails contrast against *itself*.** Computed,
  not assumed: `uos` heat band-0 vs band-4 differ by **1.02:1** (cold and named-hacker are the same
  lightness in greyscale), and adjacent meter fills sit at 1.64:1 (compute) and 1.37:1 (trace) where
  WCAG 1.4.11 wants 3:1. `../client/07-accessibility.md` §5.4 specifies a structural fix (track-coloured
  gaps + mandatory per-role texture, so meaning never rests on hue); whether the **hexes** also move is
  the open decision, and should be taken when the generated per-theme palette lands.
- **CL-7: audio is undesigned.** This doc set is visual and interaction only. Sound is one of the few ways
  to signal urgency without stealing focus mid-keystroke, so it interacts directly with the attention
  ladder (`../client/05` §6) and needs its own doc plus an accessibility pass.
- **CL-4 / T-2: the teaching layer's default.** It defaults to `explain`, which is right for the average
  player the education goal targets and probably wrong for a player who already knows Unix. A first-run
  familiarity question is the obvious answer but adds an onboarding step.
- **V-1: JavaFX has no custom-property mechanism for non-colour values** (looked-up values are colour-only).
  Every numeric token therefore lives in Java rather than CSS. Verify before the token layer is built — it
  shapes how themes are authored.
- **WL-2 / PN-10: `Stage.alwaysOnTop` is documented as "might be ignored on some platforms".** Client
  pillar C2 (compute never off-screen) rests on it, so the degradation path matters and needs testing on
  all three platforms.
- **T-4: who writes and reviews the term database.** Every entry must state the game meaning, the real
  counterpart, and flag pure fiction. A wrong mapping actively teaches something false, so this needs a
  named technical reviewer, not just a writer.

**Resolved at integration (2026-07-25):** *SK-4* — `../client/01-visual-language.md` §9.2 prescribed letter-spacing
for institutional headers; JavaFX has none (`JDK-8090880`, open). `01` now points at the three
implementable replacements in `../client/03-story-theme.md` §3.4.

### From the education curriculum (`../education/`, 2026-07-25)

The curriculum doc set (`../education/README.md`) is the answer to **T-4** above, seen from the content
side: it inventories **269 concepts** across seven domains and writes **123** of them out in full, each
with a per-claim source and the date it was checked. It raises ~79 numbered questions, owned by the
document that found them (`ED-` 00, `FN-` 01, `CA-` 02, `OS-` 03, `SH-` 04, `NW-` 05, `CT-` 06, `DS-` 07).
Those are curriculum detail and live there. These block, or need a product decision:

- **ED-6 (blocking, and it is T-4): there is still no named technical reviewer.** `../education/00`
  §8.4 makes a practitioner pass a **gate**, not a courtesy, and every domain document's final open
  question is its own instance of it. The doc set improves T-4's position rather than closing it —
  every claim is now sourced and dated, and every runnable transfer test was actually run — but a
  writer verifying their own claims has checked that the claim matches a source, not that the source
  was the right one or that the framing is what a practitioner would recognise. **Without a reviewer,
  this produces confident prose with no verification gate, which is the failure it exists to prevent.**
- **ED-8 (blocking, highest-impact): transfer tests assume a Unix shell, and most players are on
  Windows.** About a third of all entries are worded around it. Options: (a) every test names its
  platform, Windows players use WSL; (b) every test carries a PowerShell equivalent, doubling the
  verification work and teaching a second vocabulary the game does not use; (c) tests target only what
  is universal. The evidence is split by domain and is unusually concrete: `NW-6` shows **11 of 18**
  networking entries already run unmodified on all three platforms and argues for (c); `OS-9` and
  `SH-7` show their domains cannot reach (c) at all, because nothing in Windows shows an inode, a file
  descriptor or a namespace, and a player with no shell has nothing to transfer to. **Interim rule (a)
  is in force and every test names its platform.** Decide before the entries become term files.
- **ED-9 / CT-5 (needs a stated line before more security content is written): the dual-use boundary.**
  `../client/04` §4.4 bans citing offensive-tooling walkthroughs, which governs citations but not our
  own prose. `../education/06` §5 proposes the rule for the whole doc set: *entries explain what an
  attack class is and what defeats it, never how to carry one out; the test is whether a sentence
  helps a defender more than an attacker.* `NW-8` reached the same boundary independently over
  `tcpdump`. Recommend adopting it as written.
- **CT-1: detection, logging, anti-forensics and "why hacking back is illegal" have no owner.** They
  were in the original domain-05 description and are not in `../education/06-cryptography-and-trust.md`. Two are
  not optional: `cross-view-detection(7)` is named in `../education/00` §7.2 as the **highest-priority
  audit target in the whole doc set**, and `hack-back(7)` is the one page in the game that tells a
  player not to do something, which `../client/04` §2.8 makes mandatory. Proposal: an eighth document,
  `../education/08-detection-and-defence.md` (not yet written).
- **DS-1: the six identity concepts are assigned but unwritten.** `did`, `pds`, `canonicalization`,
  `append-only-log`, `provenance-record(5)` and `provenance-chain` now sit in `../education/07` §2.2.
  Three are already promised as shipping pages (`../client/04` §3.10, §4.9), so they have prose to
  adapt rather than invent.
- **ED-5: should `misconception` ever be shown to the player?** Currently curriculum-only, and it is
  the highest-value field in the template for this audience. `OS-10` reports that **15 of 18**
  operating-systems entries exist primarily to dislodge a specific false belief, several of them held
  confidently by working professionals. Against: a page that opens by telling an adult what they
  believe wrongly can read as condescending. `FN-9` proposes settling it by play test on exactly one
  page rather than in the abstract.
- **DS-7 / ED-1 (cheap, and it makes an existing rule machine-checkable): split the glossary's
  `Reputation` bullet in two.** `../client/04` §4.10's coverage check joins a term file's `canonical:`
  against `glossary.md` byte for byte. The glossary keeps **faction reputation** and **validator
  reputation** in one bullet — deliberately, to insist they are different — and no `canonical:` value
  can match it, so the check cannot pass for either. Splitting it into two entries, each retaining the
  ⚠ cross-reference, makes the distinction the glossary already insists on checkable.
- **CT-10 / ED-6 (housekeeping, but it is a live ambiguity): `T-` means three different things.**
  `../client/04` §6's teaching questions, `../architecture/07` §6's transport questions, and the
  question this document labels T-4. Documents now cite `architecture` T-1/T-3/T-4 and `client`
  T-11/T-12 by name and a reader cannot tell them apart without the path. Renaming the architecture
  set to `TS-` costs one search-and-replace across two files.
- **SH-3 (check before shipping): three command-line terms may be missing a mandatory field.**
  `../education/00` §3.2 makes `notes:` mandatory for every homonym in `../client/04` §2.15.
  `flag`, `history` and `job` are plausible collisions that the curriculum could not check against
  that table's contents.

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
- 2026-07-25 — **ED-3: the education domain split** — resolved to **seven domains, not six** — recorded in `../education/00-curriculum-and-method.md` §1.4. The six-way split named computer architecture `01` and left representation as a clause inside it; writing the domains falsified that twice. Representation has eighteen entries of its own and forward-references nothing, so it became `../education/01-foundations.md` and architecture moved to `02`. The command line kept `04` because `03`, `06` and `07` all name `shell(7)` or `exit-status(7)` in `prerequisites`, and any numbering placing it above them breaks rule R8 outright. Networking, cryptography and distributed systems each shifted up one. The ordering rule is unchanged and now holds with **zero upward prerequisite edges**. Closed `FN-1`, `CA-1`, `OS-1`, `NW-1` and the ownership half of `DS-1`; **CT-1** survives it and is listed above.
- 2026-07-25 — **Four concepts had two full entries each** — resolved to **one owner apiece** — recorded in `../education/01-foundations.md` §2 and §3.1, `../education/02-computer-architecture.md` §2.2, `../education/05-networking.md` §3.19. `00` §1.4 forbids two entries for one concept ("a player who gets two answers stops trusting both"), and parallel authorship produced exactly that: `processor` and `memory-hierarchy` in both 01 and 02, `bit-width` in both, and `latency` in both 01 and 05. Architecture took the first two, foundations kept `bit-width`, and the fourth was not a duplicate at all — 05's entry taught round-trip time, so it was **renamed `rtt(7)`**, which is what `07`'s own boundary table had been calling it. Ceded rows are marked ⇧/⇩ in the inventories rather than deleted silently.
- 2026-07-25 — **A whole planned domain was never written** — resolved by **writing `../education/04-the-command-line.md`** (18 entries, 38 concepts). `00` §1.4 had always listed it, and `01`, `02`, `03` and `05` each ceded `shell(7)`, `glob(7)`, `quoting(7)`, `exit-status(7)`, `flag(7)`, `grep(1)` or `man(1)` to it — so six documents cited an owner that did not exist, and `06` carried a prerequisite edge into the gap.
- 2026-07-23 — **JCS canonicalization rejects invalid Unicode** — resolved to **reject unpaired surrogates** — implemented in `protocol` `JsonCanonicalization`. Found while writing RFC 8785 conformance tests: the bundled canonicalizer passed a lone surrogate through and UTF-8 encoding then substituted `?`, so `{"s":"\ud800"}` and `{"s":"\udbff"}` produced **identical signing bytes**. One signature covering two distinct payloads is a forgery primitive, and a verifier is exactly where an untrusted federated payload arrives. RFC 8785 §3.2.2.2 requires the error; the library does not raise it, so the wrapper does.

## 4. How to use this doc

- Before starting design work on any system, check here for its open questions.
- When you make a decision that closes one, **move the decision into the system doc** (that's the source of truth) and log it in §3 — don't leave the answer only here.
- Proposal questions (§2) are provisional; treat the proposal docs' [PROPOSAL] tags as the signal that nothing there is load-bearing yet.
