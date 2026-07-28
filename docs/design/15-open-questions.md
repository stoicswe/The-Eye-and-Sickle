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

**OQ-5 ✅ RESOLVED 2026-07-26 — ~10 destroyed bot instances (~300 EC of value), tier-gated.** Moved to `02` §2.2. Superseded text: **Schematic material conversion rate.** How much generic contribution material a schematic costs (`10` §1a, `02` §2.2) is unset. *Watch for:* too cheap → bot sacrifice becomes a reliable ceiling-grind despite the tier gate; too expensive → salvage is decorative. Status: unset, needs a first number.

**OQ-6 ✅ RESOLVED 2026-07-26 — kept, redefined as signal quality.** It now cuts the false-positive rate rather than raising discovery chance, which makes it non-redundant by construction. Moved to `09`. Superseded text: **Detection-array redundancy.** With discovery resolved via manual investigation + scan tiers (`04` §3), the passive Detection Array (`09`) may be redundant. *Decision needed:* confirm it has a distinct role or fold it into scan efficiency. Status: open, low urgency.

**OQ-7 — Cracking vs. detection incentive.** Profitable hostile-miner cracking (`04` §5) slightly weakens the case for committing compute to security at all. *Watch for:* whether "leave miners to crack them for profit" dominates "defend to prevent them." Status: probably healthy, visible only in playtest numbers.

## 2. Proposal-raised questions (conditional on adopting the [PROPOSAL] docs)

These are **not** established design. They exist because the proposal docs filled gaps, and each gap-fill raised its own questions. If the user rejects a proposal, delete its questions.

### From `05-hacking-minigame.md` (the whole doc is proposed)
- **P-1 ✅ RESOLVED 2026-07-27 — two classes** (Breach Protocol, Offset Cipher). Moved to `05` §3.1; built in `16` §3–§5. Supersedes the 2026-07-26 resolution to three. ⚠ Reopens a small tail: `06` §4's tool→class mapping table and four tools that countered retired mechanics (Fuzzer, Rainbow Table, Credential Harvester, Side-Channel Reader) are now unmapped — logged in `16` §8.
- **P-2 ✅ RESOLVED 2026-07-26 — turn-based attention budget**, no wall clock anywhere in a breach. Moved to `05` §4.
- **P-3 (open, and now answerable):** how much does manual play beat bot play? *The* number behind Invariant I10. It was denominated in **seconds** and therefore hostage to reflexes and hardware; under `05` §4 it is a **difference in probe count on the same layer** — deterministic and testable. Still needs the puzzle to exist.
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
- **MN-2: the chain's peer exchange is not wired up, and must not be until T-1 clears.** `ChainSelection` implements and tests genesis-or-join and most-work-wins, but nothing calls it with heads from the network. `../architecture/07-transport-security.md` §6 T-1 marks the transport as reviewed patterns over unreviewed code and `CLAUDE.md` forbids letting it guard a live federation before a cryptographer reads it. Cross-server transaction sync — real players sending each other funds, shared NPC traffic — is blocked on the same seam. What is missing is not the rule; it is a transport anyone should trust with it.
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
- **AX-5 ✅ RESOLVED 2026-07-25 — the uOS heat ramp now steps luminance, measured with the WCAG
  formula.** The old ramp was 1.02:1 between band 0 and band 4, meaning cold and named-hacker were
  the same lightness in greyscale. The redesigned ramp runs L = 0.612 → 0.639 → 0.480 → 0.252 →
  0.063, giving **5.86:1** end to end, and `uos-classic` carries an inverted ramp for its light field
  (darker as heat rises). ⚠ The band name and pip count remain non-removable: §5.2's rule is never
  colour alone, and a good ramp helps that rule without satisfying it. **The meter-fill figures in
  the original finding (1.64:1 compute, 1.37:1 trace) are NOT fixed** and are still open below.
- **AX-5b (what survives): adjacent meter fills still fail SC 1.4.11.** Computed,
  not assumed: `uos` heat band-0 vs band-4 differ by **1.02:1** (cold and named-hacker are the same
  lightness in greyscale), and adjacent meter fills sit at 1.64:1 (compute) and 1.37:1 (trace) where
  WCAG 1.4.11 wants 3:1. `../client/07-accessibility.md` §5.4 specifies a structural fix (track-coloured
  gaps + mandatory per-role texture, so meaning never rests on hue); whether the **hexes** also move is
  the open decision, and should be taken when the generated per-theme palette lands.
- **CL-7: audio is undesigned.** This doc set is visual and interaction only. Sound is one of the few ways
  to signal urgency without stealing focus mid-keystroke, so it interacts directly with the attention
  ladder (`../client/05` §6) and needs its own doc plus an accessibility pass.
- **CL-4 / T-2 ✅ RESOLVED 2026-07-25 — asked once, on the main menu.** The question worried that a
  first-run familiarity prompt "adds an onboarding step". The main menu removed that cost: the player
  is already stopped there choosing a character, so one more choice is free. Two options — "Explain as
  I go" (`explain`) and "I know Unix" (`terms`) — asked once, persisted as `askedFamiliarity`, and
  changeable at any time with `teach`. The manual works at every level including `off`, so nothing is
  ever lost by answering either way. Superseded text follows.
- **CL-4 / T-2 (superseded, kept for the record): the teaching layer's default.** It defaults to `explain`, which is right for the average
  player the education goal targets and probably wrong for a player who already knows Unix. A first-run
  familiarity question is the obvious answer but adds an onboarding step.
- **V-1: ✅ CONFIRMED by measurement, 2026-07-25 — JavaFX looked-up values really are colour-only.**
  Declaring `-es-mono-font` in the token sheet and referencing it with `-fx-font-family` produced, on
  JavaFX 26.0.2: `WARNING: CSS Error parsing native.css: Expected '<size>' while parsing
  '-es-mono-font'`. A looked-up value resolves to a `Paint`; a font stack is not one, so the reference
  fails and the declaration is dropped **silently at runtime** — the build is green and the style is
  simply absent, which is the worst failure shape available. **Consequence, now implemented:** colour
  tokens live in `client/src/main/resources/.../theme/*.css` and work exactly as the contract intends;
  every non-colour token is inlined per-family or held in Java. There is therefore no single place to
  change the mono stack in CSS, and both family sheets state it. `01-visual-language.md` §2's token
  model is unaffected for colour, which is the great majority of it.
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
side: it inventories **311 concepts** across eight domains and writes **149** of them out in full, each
with a per-claim source and the date it was checked. It raises ~88 numbered questions, owned by the
document that found them (`ED-` 00, `FN-` 01, `CA-` 02, `OS-` 03, `SH-` 04, `NW-` 05, `CT-` 06, `DS-` 07,
`DF-` 08). Those are curriculum detail and live there. These block, or need a product decision:

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
- **DF-9 (blocking, and the sharpest instance of ED-6): `hack-back(7)` states a legal position and
  has had no legal review.** `../client/04` §2.8 makes the statement mandatory, so the page must
  exist and must not hedge — and it is the one place in the entire doc set where confident error
  could *harm* a reader rather than merely misinform them. It cites 18 U.S.C. §1030, the Computer
  Misuse Act 1990 and the never-enacted Active Cyber Defense Certainty Act, and the claim it makes
  is the uncontroversial one. It still needs a reader who knows the law. **If that is not
  obtainable, the page should say on its face that it has not had one** rather than sound more
  certain than its authorship supports.
- **DF-5 (a curriculum finding against the design): the game has no false-positive surface.** Three
  of `08`'s strongest entries — `false-positive(7)`, `base-rate-fallacy(7)`, `alert-fatigue(7)` —
  teach that detectors mostly fire on innocent things, which `../design/04-mining.md` §3.2's scan
  tiers imply but never state. If scans in fact never produce a false hit, the game contradicts its
  own curriculum. **Recommend adding the surface**: it is cheap, it makes the Thorough Scan's price
  legible, and it is the single change that would make this material land.
- **ED-11: the `operating` stage is over budget by about a quarter** — 51 written entries against
  `../education/00` §6.2's ~25–40. It was invisible to every individual document, because each one's
  own graph check passed; only the eight-document total showed it. Likely cause: `operating` is the
  comfortable default when a writer is unsure. Audit `01` and `02` first, and move nothing merely to
  hit a number.
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
- **CT-10 ✅ RESOLVED 2026-07-25 — the architecture transport questions are now `TS-`.**
  `T-` meant three different things: `../client/04` §6's teaching questions, `../architecture/07` §6's
  transport questions, and the question this document labels T-4. Documents cited them by bare number
  and a reader could not tell them apart without the path. The architecture set is renamed `TS-`, so
  `T-` now means "teaching" everywhere and `TS-` means "transport security". The two remaining
  same-numbered pairs (`T-4` here vs `T-4` in `../client/04`) are the ones ED-6 already flags.
  Superseded text follows.
- **CT-10 (superseded, kept for the record): `T-` meant three different things.**
  `../client/04` §6's teaching questions, `../architecture/07` §6's transport questions, and the
  question this document labels T-4. Documents now cite `architecture` T-1/T-3/T-4 and `client`
  T-11/T-12 by name and a reader cannot tell them apart without the path. Renaming the architecture
  set to `TS-` costs one search-and-replace across two files.
- **SH-3 (check before shipping): three command-line terms may be missing a mandatory field.**
  `../education/00` §3.2 makes `notes:` mandatory for every homonym in `../client/04` §2.15.
  `flag`, `history` and `job` are plausible collisions that the curriculum could not check against
  that table's contents.

### From the client implementation (`client/`, `solo/`, 2026-07-25)

Building the client forced the offline question the architecture docs left open, and answered it the
way `../architecture/02` §4 recommended.

- **SOLO-1 (decided, with a standing cost): single player runs in-process, not against a local
  server.** A new `solo` module holds a pure-Java rules engine over a JSON save. The alternative —
  launching the real Spring server locally — was rejected on three measured grounds: `client/pom.xml`
  bans `eyeandsickle-server`, Spring, Postgres and Flyway **transitively** (Invariant I14 made
  mechanical), the schema is deeply PostgreSQL-coupled (37 `jsonb` columns, `uuid`, `now()`) so an H2
  swap is a porting project rather than a config switch, and a second Boot JVM costs roughly 200 MB
  RSS and several seconds of startup for a mode whose appeal is that you double-click and play.
  ⚠ **The cost is real and permanent: `solo` is a second implementation of a subset of the rules.**
  `solo/Balance.java` cites the design document for every number so drift is at least visible, but
  nothing enforces agreement. If the economy is re-tuned in `03`, that file must be re-read.
- **SOLO-2 (decided): a solo character is local-only and can never federate.** The save lives on
  player-controlled infrastructure, which is exactly what I14 forbids for game state — so the
  invariant is preserved by ensuring nothing downstream ever trusts it. `SoloSave.federable` is
  permanently false, there is no export path, and `verify` reports plainly that a local item has no
  provenance chain rather than manufacturing one that would look checkable and prove nothing. Going
  online means a character created on a real home server. This is option 1 in `../architecture/02` §4,
  which recommended it.
- **SOLO-3 ✅ RESOLVED 2026-07-25 for solo — `solo/Catalogue.java` has six offerings.** Priced inside
  `03` §2's published bands and gated by `02` §5's rule rather than by taste: two ethecoin
  consumables, one per-session cost, two schematic-gated and one reputation-gated. **Nothing sells
  compute or vault capacity at any price**, which makes I1 and I12 structural — there is no offering
  to buy, so there is no code path to review. A non-ethecoin gate returns **`77 EX_NOPERM` with the
  requirement in words**, never a refusal with a price, so a gate reads as "not yet, and here is why"
  rather than as an obstruction. ⚠ **The server still has W-3** and one catalogue should eventually
  serve both; this one lives in `solo` because it is a rule rather than a rendering.
- **SOLO-3 (superseded, kept for the record): the market catalogue is empty in solo.** `LocalGameSession.purchase` refuses
  everything, because offerings are content rather than code — the same gap the server has as **W-3**
  (`GatedOfferingCatalog` is empty). One catalogue should serve both, and it does not exist yet.
- **CL-8 (partly closed): `RemoteGameSession` exists; its transport does not.** The class is written
  and tested, and holds the two properties that matter: every read returns a last-known value rather
  than null or a blank (so a network hiccup never empties a HUD mid-decision), and every intent
  returns **`69 EX_UNAVAILABLE`** rather than `1 REFUSED` — because claiming a rule declined the
  request would be a lie about where the decision came from. **What is still missing is the REST
  client, the AT Proto OAuth flow and the reconnect loop.** Adding them changes no view.
- **CL-9 ✅ RESOLVED 2026-07-25 — the five windows are built.** `market` renders the five-gate
  taxonomy and the price bands from `02` and `03` §2; `map` renders the known-node table with the
  discovery rule that keeps recon paid-for; `recon` renders the cost model; `botnet` renders the four
  bot invariants including **I10** and **I11**; `comms` renders the social layer's shape and the one
  thing `00` §3 forbids putting in it (a chat window). Each names the specific undecided question
  where one blocks, rather than showing an empty table that reads as a bug.
- **T-1 ✅ RESOLVED 2026-07-25 — the `man` window is built, as a sixteenth id.** `../client/04` §4.6
  adds it and calls it "a fourteenth id"; `../client/05` §2.1 lists fifteen and never absorbed it,
  because §2.2 added `comms` and `settings` without knowing about it. **The two documents disagree
  about the size of a table both call closed.** It is built, because the teaching layer is pillar C6
  and `man` is how a player reaches it deliberately — and because the honest fix for two documents
  disagreeing is to build the thing and report the disagreement, not to drop it silently.
  ⚠ `../client/05` §2.1's table should gain the row.
- **CL-10 ✅ RESOLVED 2026-07-25 — tier 1 is wired.** `GlossBar` attaches a hover tooltip AND an
  accessible name to any node naming a term, which satisfies §3.6 / SC 1.4.13's requirement that
  hover-triggered content be reachable by keyboard — JavaFX tooltips are hover-only, so the
  accessible name carries the same content down the focus path. It respects the teaching level at
  attach time and at show time, and it never invents: a term with no page gets no gloss, because a
  best-effort guess is exactly the wrong-mapping failure the education doc set exists to prevent.
  Wired to the rig monitor's COMPUTE and BALANCE headings first, those being the two words on screen
  at all times. ⚠ Still to do: the other windows' headings, and the terminal's output — a term
  appearing in `ps` output is not yet glossable because the transcript is one text blob.
- **CL-10 (superseded, kept for the record): the gloss bar and hover tier are not wired.** `../client/04` §4.1 specifies three
  tiers of disclosure — hover for a one-line gloss, a keypress for the full page, citations below.
  Tier 2 (`man`, the window, the index, the status filter) and tier 3 (`reading:`) are built. **Tier 1
  is not:** no surface yet detects a term under the cursor and offers its gloss. `ManView.glossBar`
  renders one and nothing calls it. This is the piece that makes teaching *ambient* rather than
  looked-up, and it is the last substantive gap in pillar C6.

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

### From the UI overhaul (`ui-design-language.md`, 2026-07-26)

`ui-design-language.md` is tagged **decided** and arrived with four open questions of its own (§11).
Two closed on implementation; what survives is below, plus what the implementation itself opened.

- **UI-1 ✅ RESOLVED 2026-07-26 — snapping *and* free-drag, and the rail became the launcher.**
  §11 questions 1 and 3. Question 1 said "prototype both", so both shipped and the choice is a
  setting (`freeDragWindows`, Settings → Desk, or the `desk` command); snap is the default because it
  is what makes edge-tiling reachable, which is how §3's tiling ideal survives having movable windows
  at all. Question 3 asked whether the 34px rail should stop being pure texture and become the
  window switcher — it did, but for a reason §11 did not have: client pillar **C1** requires every
  tool to be reachable without the terminal, and the rail is the only always-visible surface with
  room for seventeen entries. Each entry is the tool's own accelerator key, so it teaches the
  shortcut while removing the need for it.

- **UI-2: is Bandwidth the right cap on open windows, and what is the arithmetic?**
  ⚠ **Reviewed 2026-07-26 and deliberately left open** — asked, and the answer was "keep the
  mechanism, keep it opt-in". So nothing below is decided and the `[PROPOSAL]` tag stands. One thing
  the review did establish and worth not re-deriving: **`11` §2 defines Bandwidth as "how many
  engagements can run at once"** and gives the example "you can have cycles free and still be
  bandwidth-blocked from another engagement" — which reads as an *outward operation*, not as looking
  at a panel. That is the argument against the whole idea, and it is the first thing to weigh when
  this is taken up. It was not adopted, because §8 is a decided document and this is its mechanic.

  §8 wants the desk to be a mechanic rather than a skin, and names Bandwidth (`11` §2) as the cap on
  simultaneous open tool windows. The mechanism is built (`DeskManager.setWindowCap`) and **ships
  off**. The problem is calibration, not plumbing: a starting rig has `bandwidth = 1`, so capping
  windows at Bandwidth directly allows *one* open panel. `GameSession.RigCapacity.proposedWindowCap()`
  adds six always-free windows — the rig monitor, terminal, log, manual, settings and switcher, the
  six that reach nothing — to Bandwidth. That split is invented here, not derived from `11`, which is
  why it is opt-in and marked `[PROPOSAL]`. **Two things to decide:** whether "engagements" in `11`
  §2 really should include *looking at a tool*, and whether Memory Buffer separately caps
  equipped-tool windows as §8 also proposes. A cap that turns out to be wrong must not be discovered
  by a player who cannot open their own map.

- **UI-3: do bot alerts open as windows or dock into a strip?**
  §11 question 2, unchanged and untouched. Nothing to build against — `10-botnets.md` is
  `[PROPOSAL]`, so there are no alerts yet. Worth deciding *with* §10.1b's split-attention rule
  rather than after it, since §8's whole argument is that crowding the deck **is** the split-attention
  mechanic.

- **UI-4: localisation against an uppercase, fixed-cell type system.**
  §11 question 4, unchanged. One thing is already done in anticipation: every `toUpperCase` and every
  `String.format` in the client passes `Locale.ROOT`. That is not tidiness — a Turkish locale maps
  `i` to `İ`, so `IDENTITY` would render with a dot over the I on one player's machine and nowhere
  else, and a German locale would print `1,25 EC` beside an `EC/HR` projection that used a period.
  The unresolved half is real and structural: uppercase-everything and 11px character cells assume
  Latin script and short strings.

- **UI-5 ✅ RESOLVED 2026-07-26 — confirmed: Classic stays flat, and keeps its name.**
  Asked and answered. Classic keeps its *palette* (light field, black hairlines) on the deck's flat
  geometry and gains no exemption from §9. The reasoning that decided it is the one below: one theme
  exempt from the contract every other theme is held to is worse than a theme that is no longer a
  period costume, and Classic's real value — the most legible non-accessibility skin in the client,
  at 21:1 — is carried entirely by the palette, which survived. The name stays too; renaming was
  offered and declined. Superseded text follows.

- **UI-5 (superseded, kept for the record): uOS Classic lost its chrome, and it is worth confirming that was right.**
  Classic was added on 2026-07-25 as "System 7 meets a Unix workstation" — light grey chassis,
  bevelled controls. §9 makes bevels, drop shadows and rounded corners build-blocking, so what
  survived the overhaul is Classic's *palette* (light field, black hairlines) on the deck's flat
  geometry. That keeps its real value — it is still the most legible non-accessibility skin — but it
  is no longer a period costume. The alternative was one theme exempt from the contract every other
  theme is held to, which seemed worse. **Flagged rather than assumed:** it was a named request, and
  the request is not fully honoured any more.

- **UI-6 ✅ RESOLVED 2026-07-26 — yes. Hold, then recover.** A scan's cycles are held for its
  published Duration and only then enter the Thermal Budget curve. Moved into
  `04-mining.md` §3.2 (the source of truth) with the reasoning, and into `01-core-resources.md`
  §1.3's recovery-curve proposal, whose anchors are stated *relative to the run duration* and
  therefore now **compose rather than overlap** — a Thorough Scan at 50% load is ~1× held + ~2×
  recovering ≈ 3× its run duration before the rig is whole. **This is a price rise, not a clock
  change**, and §3 below records what was re-checked with it. Superseded text follows.

- **UI-6 (superseded, kept for the record): should a scan HOLD its cycles for its duration?**
  Implementing the activity readout forced this into the open. Until 2026-07-26 a scan was
  instantaneous: `SoloGame.scan()` spent the cycles and returned, and the Duration column
  `04-mining.md` §3.2 publishes (~30s / ~2 min / ~6 min) was a number in a log line that nothing ever
  waited for. It now runs for its published duration. **What was deliberately NOT changed is the
  compute**: §3.2 lists Compute and Duration as separate columns and then says "Scan cycles recover
  on the Thermal Budget curve", so the cycles are still spent immediately and recover as before.
  Holding them for the scan's duration *and then* recovering them would roughly double the real cost
  of a Thorough Scan, and `CLAUDE.md` is explicit that the `03`/`04` numbers are calibrated as a set.
  **The open half:** §3.2's flavour line says a Thorough Scan "leaves you effectively down 35 cycles
  for far longer than the scan runs", which is true under the current model only on a heavily loaded
  rig — on a lean one the 35 cycles are back in about four minutes, before the six-minute scan ends.
  Whether that is the intended asymmetry or an argument for hold-then-recover is a balance decision,
  not an implementation one.

- **UI-7: the pointer is drawn by the game, and that has an accessibility cost worth watching.**
  `ui-design-language.md` §0 says the aesthetic "depends on the player never seeing their own
  operating system", and after the window chrome went, the pointer was the last piece of it left on
  screen. Three drawn skins now exist (reticle, chevron, block), built from colours read back out of
  the live stylesheet so they follow every palette including high-visibility. **The system pointer is
  the default and is offered first**, because a pointer is tuned by the player's OS for their display
  and their eyesight and some people run a deliberately enlarged or high-contrast one — overriding
  that by default would be an accessibility regression dressed as art direction. Two things are
  deliberately never re-drawn: the **text I-beam**, whose shape carries real precision information
  about where the caret will land, and **wait/busy**, because the deck has no blocking operations.
  ⚠ **Unresolved:** JavaFX offers no HiDPI mechanism for cursors — `ImageCursor` uses the image at its
  natural size in points, so on a 2× display a 32-point pointer is drawn from 32 pixels and is
  slightly soft. A 64px image would be a physically double-sized pointer, which is worse. If this
  turns out to matter, the fix is a per-skin size setting rather than a bigger bitmap.

- **UI-8: heat is now a banded thermometer, which extends a contract document's rule.**
  `../client/01-visual-language.md` §2.2.4 states flatly: *"Heat renders as a banded chip carrying
  the band name, **never as a continuous meter**."* A thermometer was requested, and the two are
  reconcilable — but only because of *why* §2.2.4 says what it says, so the reasoning is recorded
  rather than assumed. Its two arguments were:
  1. *"A smooth bar invites a precision the model does not have."* The stem is not smooth: eleven
     discrete cells in **five zones, one per band**, sized to each band's real range (0–10, 10–30,
     30–55, 55–80, 80–100), with the gaps between zones as the thresholds and colour **stepping** at
     a boundary rather than interpolating across one. It answers "which band am I in" as before, plus
     "how close to the next" — which serves the threshold decision §2.2.4 protects.
  2. *"Trace is the only continuous red meter, so heat and trace can never be confused."* Trace is a
     continuous horizontal fill of labelled contribution segments (§2.2.5). This is a vertical,
     cell-based, zone-gapped column with a bulb. The collision guarded against was structural, and
     structurally they are nothing alike.

  **✅ RESOLVED 2026-07-26 — the thermometer stays, and the band name stays in the tooltip.** The
  extension above is confirmed rather than reverted. The band-name half was decided the other way
  from what this entry originally recorded, and the original text was **factually wrong about the
  code**, which is worth stating plainly: it claimed the name was "still printed beside the
  thermometer", and it was not — `DeckShell` renders the cell as `KeyValue.keyOnly("Personal heat")`,
  so the strip carries the label and the meter and no band name at all. That was a deliberate
  request from 2026-07-26 that this entry never absorbed.

  ⚠ **What the confirmation required.** §2.2.4 wants the chip to carry the band name and
  `../client/07` §5.2 forbids meaning resting on appearance alone; a hover tooltip answers neither
  for a player who does not hover, and JavaFX tooltips are mouse-only. So the band name and the
  numeric heat are now also `ThermoMeter`'s **accessible text** — the same two-path fix **CL-10**
  used for the gloss bar. Assistive technology gets a sentence; a sighted player who never hovers
  reads the coloured bulb and the lit cell count. That second half is a **knowing stretch of §5.2**,
  recorded here rather than smoothed over. Reverting it is one line: `KeyValue.of("Personal heat",
  band.label())`.

- **UI-9 (new, found by rendering UI-8): the thermometer's five-band fill ramp is non-monotonic in
  luminance in every theme.** Measured with the WCAG relative-luminance formula over the
  `es-thermo-fill-0…4` colours actually in the stylesheets, not assumed:

  | theme | band 0 | band 1 | band 2 | band 3 | band 4 (named hacker) |
  |---|---|---|---|---|---|
  | `deck` | 0.252 | 0.481 | 0.243 | 0.518 | **0.159** |
  | `deck-hc` | 0.832 | 1.000 | 0.518 | 0.594 | **0.326** |
  | `uos-phosphor` | 0.319 | 0.491 | 0.384 | 0.741 | **0.290** |
  | `uos-classic` | 0.023 | 0.005 | 0.228 | 0.103 | 0.075 |

  In all four, **the hottest band is darker than the coldest** — on `deck`, band 4 sits at 0.159
  against band 0's 0.252 — and the ramp zig-zags twice on the way up. In greyscale, or for a player
  who reads brightness as intensity, the signal *inverts* at exactly the band that matters most.

  This is the same failure **AX-5** found and fixed on 2026-07-25, in a new place: AX-5 repaired the
  *uOS heat-chip* ramp to a measured 5.86:1 monotonic run, and the thermometer — added a day later —
  introduced its own ramp by reusing general palette tokens (`-es-dim-1`, `-es-text`,
  `-es-amber-mid`, `-es-amber`, `-es-alarm`) whose luminances were never chosen to sequence.

  ⚠ **Not simply a bug to fix quietly**, which is why it is logged rather than patched: the fix
  means either five new heat-specific tokens per theme (four stylesheets, and §2.1 reserves amber for
  live/earning data, which heat is not) or re-pointing the existing five, which are shared with other
  components. Both are palette decisions against a contract. **Mitigating, and the reason this is not
  urgent:** unlike AX-5's chip, magnitude here is *also* carried by lit-cell count and by zone
  position, so meaning does not rest on the ramp alone — §5.2 is not breached by it. It is a ramp
  that misleads rather than one that hides. Take it with the generated per-theme palette that
  **AX-5b** is already waiting on.

## 3. Resolution log

Record resolutions here when they land (date — question — outcome — where it moved).

- 2026-07-23 — **Bot destruction vs. degradation** — resolved to **total loss + tier-gated salvage** — moved to `10` §1a. (Source: design session 3.)
- 2026-07-23 — **Counter-attack time budget** — resolved to **parallel, split-attention, non-queuing** — moved to `10` §1b. (Source: design session 3.)
- 2026-07-23 — **Detection signal legibility** — resolved to **signal strength is what you pay for; manual-free vs. scan-costly** — moved to `04` §3. (Source: design sessions.)
- 2026-07-23 — **Provenance chain shape** — resolved to **per-item** — recorded in `../architecture/04`. (Source: Tech Chat 2.)
- 2026-07-23 — **Full technology stack** — resolved end-to-end — recorded in `../architecture/`. (Source: Tech Chat 1.)
- 2026-07-25 — **ED-3: the education domain split** — resolved to **seven domains, not six** — recorded in `../education/00-curriculum-and-method.md` §1.4. The six-way split named computer architecture `01` and left representation as a clause inside it; writing the domains falsified that twice. Representation has eighteen entries of its own and forward-references nothing, so it became `../education/01-foundations.md` and architecture moved to `02`. The command line kept `04` because `03`, `06` and `07` all name `shell(7)` or `exit-status(7)` in `prerequisites`, and any numbering placing it above them breaks rule R8 outright. Networking, cryptography and distributed systems each shifted up one. The ordering rule is unchanged and now holds with **zero upward prerequisite edges**. Closed `FN-1`, `CA-1`, `OS-1`, `NW-1` and the ownership half of `DS-1`; **CT-1** survives it and is listed above.
- 2026-07-25 — **Four concepts had two full entries each** — resolved to **one owner apiece** — recorded in `../education/01-foundations.md` §2 and §3.1, `../education/02-computer-architecture.md` §2.2, `../education/05-networking.md` §3.19. `00` §1.4 forbids two entries for one concept ("a player who gets two answers stops trusting both"), and parallel authorship produced exactly that: `processor` and `memory-hierarchy` in both 01 and 02, `bit-width` in both, and `latency` in both 01 and 05. Architecture took the first two, foundations kept `bit-width`, and the fourth was not a duplicate at all — 05's entry taught round-trip time, so it was **renamed `rtt(7)`**, which is what `07`'s own boundary table had been calling it. Ceded rows are marked ⇧/⇩ in the inventories rather than deleted silently.
- 2026-07-25 — **How single player runs without a server or a database** — resolved to an **in-process `solo` module** — recorded in `solo/pom.xml`, `../architecture/02` §4 and SOLO-1/SOLO-2 above. The client now starts offline by default: no network, no account, no PostgreSQL, no second process, no listening port. Verified end to end — the client launches, ticks, and writes a save of 723 bytes containing `"federable": false` and a 100-cycle rig. The measured footprint of the whole runtime is one JSON file.
- 2026-07-25 — **The rig log** — built as an event stream the engine emits, a `log` command, and a live panel in the docked right column. `../client/04` §3.10 already specified the command (mapped to `journalctl -f`, "severity glyphs follow RFC 5424's eight levels") and it had never been implemented. **The severities are the real ones with the real backwards numbering** — 0 is Emergency and 7 is Debug — and `log -p 4` takes `journalctl -p`'s own semantics of "this level or worse", so the habit transfers verbatim. Every glyph is paired with its keyword, because a glyph alone is a private code that `../client/07` §5.2 forbids. **Its primary job is telling a returning player what happened while they were away:** offline income accrues silently, and silent income is indistinguishable from a bug — so resume now logs the elapsed time, what the deployed miners buffered, and *explicitly* that self-mining earned nothing because it is online-only (I5). ⚠ **Deliberately not logged: per-second accrual.** A line every second would bury the one line that mattered, which is `alert-fatigue(7)` — a page in this game's own manual — and a log that cries wolf teaches its reader to stop looking, disabling the investigation `../design/04-mining.md` §3.1 rests on. The log is capped at 500 entries and persisted, so it survives a restart like a real journal without growing the save without bound. ⚠ **This is a seventeenth window** and the third the catalogue documents did not anticipate; logged against **WL-1**. It is distinct from `../client/05` §6.7's alert tray, which is triage (rung-3/4 items with deadlines, sorted by time remaining) rather than history.
- 2026-07-25 — **GUI/terminal parity — pillar C1 was under-delivered and is now enforced** — recorded in `../client/00-client-overview.md` §6.3's shortcut table (now fully bound) and pinned by `ShortcutsTest`. **The finding:** `scan` and `mv` were reachable only by typing, and the command palette `../client/00` §6.3 specifies on `Shortcut+K` had never been built. That made two core actions invisible to anyone who had not read the manual, which is precisely what C1 forbids — *"a tool's UI is built from the tool's actual output shape, and its cost is shown where the tool is used"*. **Fixed:** the audit window has scan-tier buttons carrying each tier's published cost and duration; the storage window has per-item tier moves whose tooltips state the exposure consequence; the palette exists and searches on the synopsis as well as the name, so a player who does not know a verb can find it by describing what they want. The six missing global shortcuts from §6.3 are bound (`Shortcut+K`, `+Shift+T`, `+Shift+E`, `+Shift+D`, `` +` ``, `+.`), and a test asserts none collides with a window accelerator — both sets install on every Scene, so a collision would silently disable whichever registered second. The palette runs through the same `Shell` the terminal does, so it is a different way in rather than a second engine: same parser, same closed AST, same exit statuses. ⚠ **Not yet parity:** the `map`, `recon`, `botnet` and `comms` windows have no actions to expose because their systems are still `[PROPOSAL]`; `find` (`Shortcut+F`) is per-window and unbuilt.
- 2026-07-25 — **The main menu, the theme redesign, and six open questions** — the client now boots to a main menu rather than straight into a game: three solo save slots (mirroring the online cap in `../architecture/09`), a home-server field, settings and quit. Themes were restructured so each uOS variant owns a stylesheet and can therefore actually differ — the default became the near-black crimson console, the green phosphor survives as `uos-phosphor`, and **uOS Classic** was added (System 7 + Unix; also the most legible skin in the client at 21:1, which makes it a real accessibility option rather than a novelty). Closed in the same pass: **AX-5** (heat ramp now 5.86:1 end to end, up from 1.02:1), **CL-4/T-2** (familiarity asked once on the menu, where it costs nothing), **CL-10** (tier-1 gloss bar), **SOLO-3** (six gated offerings), **DS-7/ED-1** (glossary Reputation bullet split so the coverage check can pass), **CT-10** (architecture transport questions renamed `TS-`). ⚠ **A second JavaFX CSS trap was measured and is worth knowing alongside V-1:** `repeating-linear-gradient` does not exist — it parses as an unknown function and the declaration is dropped silently. JavaFX spells it `linear-gradient(from … to …, repeat, …)`, with `repeat` as a cycle method. Same failure shape as V-1: a web-CSS idiom that is nonsense here and fails at runtime rather than at build time.
- 2026-07-25 — **Which layout a new player opens in** — resolved to **the single-window docked shell**, inverting an Established decision on explicit direction — recorded in `../architecture/01` §1, `../client/05` §5.1, `../client/07` §2.3 and `ClientProfile.Settings.dockedLayout`. `../architecture/01` §1 specified a multi-window `Stage`-per-tool architecture and called it "the default and the fantasy"; that architecture is unchanged and fully built, and multi-window remains one setting away (Settings → Layout, or `dock`). What changed is the default for a fresh profile. **Why it was safe to invert:** `../client/07` §2.3 already required the docked layout to lose no functionality or information, and a test asserts every window in the catalogue is reachable in it — so defaulting to it costs a new player nothing while sparing them fifteen OS windows in the first thirty seconds. ⚠ **One real bug surfaced and was fixed in the same change:** the accelerators were bound to `WindowRegistry::open`, so `Shortcut+4` in docked mode popped a *separate* OS window — silently breaking the single-window model at exactly the moment a player used the keyboard. The handler is now injected, and docked mode binds it to focus the tab.
- 2026-07-25 — **The client's remaining surfaces** — the teaching layer, the docked layout and the five proposal windows all landed. The manual ships **21 pages** parsed by a closed-key parser that refuses unknown keys, unknown body sections, a section-7 page carrying a SYNOPSIS, and a `real, simplified` page with no CAVEATS — every one a silent failure otherwise. The cross-reference check found two dead links on first run (`ledger(1)`, `mine(1)`); both references were correct and the pages were missing, so the pages were written. The docked single-window layout is built as a **mode rather than a fallback** per `../client/07` §2.3, with the rig strip as chrome outside every SplitPane — a stronger guarantee for pillar C2 than always-on-top, because there is no z-order for it to lose. Both layouts verified running with zero exceptions.
- 2026-07-25 — **V-1: are JavaFX looked-up values usable for non-colour tokens** — resolved to **no, they are colour-only** — measured against JavaFX 26.0.2, recorded in `../client/01-visual-language.md`'s question list and in both theme stylesheets. The failure is silent at runtime, which is why it was worth measuring rather than assuming.
- 2026-07-25 — **CT-1: detection, logging, anti-forensics and hack-back had no owner** — resolved by **writing `../education/08-detection-and-defence.md`** (42 concepts, 20 entries). Option (a) was taken as `06` recommended — a document of its own rather than folding into `03` or widening `06`. It carries two obligations `../client/04` makes mandatory: §2.7's defender's answer on `log-scrubber(1)` (forward logs off the host, `chattr +a`, hash-chain them — and a gap in a log is itself evidence), and §2.8's statement that hacking back is illegal, which `hack-back(7)` carries with statutory citations. `08` can sit above everything only because **no `prerequisites` field in `01`–`07` names a detection concept** — checked before it was written, not after. `cross-view-detection(7)` went to `03` as recommended and turned out to be written there already, so CT-1 had over-stated its orphan list by one.
- 2026-07-25 — **DS-1: six identity concepts assigned but unwritten** — resolved by **writing them into `../education/07-distributed-systems-and-identity.md`** (§3.19–§3.24: `did`, `pds`, `canonicalization`, `append-only-log`, `provenance-record(5)`, `provenance-chain`). `06` and `07` had each ceded them to the other; `07` won on two grounds `07` could not see for itself — their game surface is the `identity` window, and under R8 a `06` entry may not depend on a `07` one. `provenance-chain(7)`'s `notes:` names `../client/04` §4.9's already-written page as the ship-side source so the two cannot fork. ⚠ Writing them exposed **sixteen broken edges**: `07` had been citing `public-key(7)` and `signature(7)`, which `06` spells `public-key-cryptography(7)` and `digital-signature(7)`.
- 2026-07-25 — **CT-3 / DS-4: the `adversarial` stage reported as over-subscribed** — **withdrawn; it was a unit error.** Two documents independently counted their **inventory rows** (20 and 25) against `../education/00` §6.2's **written-entry** budget, and the two agreeing made it look confirmed. Counted correctly the stage sits at **36 of 25–40** even after `08` added ten. `06`'s proposal to demote `public-key-cryptography(7)`, `digital-signature(7)` and `trust-anchor(7)` is therefore dropped — that would have been a stage assignment chosen to satisfy a number. §6.2 now states the counting basis and publishes the measured distribution, which is how **ED-11** (the `operating` stage genuinely *is* over) became visible.
- 2026-07-25 — **A whole planned domain was never written** — resolved by **writing `../education/04-the-command-line.md`** (18 entries, 38 concepts). `00` §1.4 had always listed it, and `01`, `02`, `03` and `05` each ceded `shell(7)`, `glob(7)`, `quoting(7)`, `exit-status(7)`, `flag(7)`, `grep(1)` or `man(1)` to it — so six documents cited an owner that did not exist, and `06` carried a prerequisite edge into the gap.
- 2026-07-23 — **JCS canonicalization rejects invalid Unicode** — resolved to **reject unpaired surrogates** — implemented in `protocol` `JsonCanonicalization`. Found while writing RFC 8785 conformance tests: the bundled canonicalizer passed a lone surrogate through and UTF-8 encoding then substituted `?`, so `{"s":"\ud800"}` and `{"s":"\udbff"}` produced **identical signing bytes**. One signature covering two distinct payloads is a forgery primitive, and a verifier is exactly where an untrusted federated payload arrives. RFC 8785 §3.2.2.2 requires the error; the library does not raise it, so the wrapper does.

- 2026-07-26 — **The UI overhaul: `ui-design-language.md` implemented, and two Established decisions reversed with it** — recorded in `ui-design-language.md` §12, `../architecture/01` §1, `../client/05` §5.1 and `../client/07` §2.3. The document is tagged **decided** and its §0 cancels two things `../architecture/01` had specified as Established: **AtlantaFX for native OS theming** and **a separate `Stage` per tool**. The argument is short and hard to argue with — native chrome puts real macOS traffic lights and Windows title bars around the game, and the aesthetic depends on the player never seeing their own operating system. Both are gone. What replaced them is **one `StageStyle.UNDECORATED` Stage containing a window manager the client draws itself** (`ui/chrome/DeskManager`): drag, focus, z-order, minimise, maximise, close, resize, snap-to-grid and edge tiling. That is strictly more capable than *both* layouts it replaces, which is why the setting that chose between them was removed rather than repointed — and it also retires the docked layout inverted on 2026-07-25, one day after that inversion was logged. `../client/07` §2.3's "no functionality or information is lost" requirement carries over and is now **structural rather than maintained by hand**: the compute readout is a cell in the top strip, which is chrome, so there is no z-order for pillar C2 to lose and no tab it can hide behind.

  **Themes collapsed from seven stylesheets to one component sheet plus palette overlays.** §0 says "ship one hand-written stylesheet"; taken literally that deletes the phosphor, amber and Classic skins added the day before on request. Taken as what it argues against — *a second sheet that redefines components and can therefore drift* — it permits what was built: `ui/theme.css` owns every component rule, geometry, hairline and motion, and a variant is ~40 lines of colour. **A test enforces that**, failing the build if an overlay sets a size, a font or a border width. The `native` family is gone with AtlantaFX; a **high-visibility** variant was added (WCAG AAA body text, 3:1 hairlines) and is the one place in the client that spends §2.1's "never `#000`". ⚠ **uOS Classic lost its bevelled chrome** — see **UI-5**.

  **Four JavaFX behaviours were measured rather than assumed, and three of them were traps.** (1) `-fx-shape` scales an SVG path to the region, so a fixed 18px notch becomes a proportional wedge — the notch is a `Polygon` clip recomputed per resize, and `UiContractTest` asserts the 45° legs stay 18px at 320, 1280 and 2560px. (2) **A managed child of a `Pane` is repositioned by the Pane's own `layoutChildren`, silently undoing `resizeRelocate`** — every desk window must be `setManaged(false)`, and without it the window manager appears to work and then snaps every panel to the top-left at zero size on the next layout pass. (3) **In an event filter, `MouseEvent.getX()` is relative to the event's *target*, not to the node the filter is on** — the resize grip therefore worked on a bare panel and stopped wherever a tool had put content, which is everywhere; the fix is `sceneToLocal` at the call site and `DeskGripTest` covers the arithmetic. (4) `Scene.snapshot` takes only a target image; `SnapshotParameters` is `Node`'s overload.

  **§10's acceptance criteria are machine-checked** by `UiContractTest`: no `Interpolator.EASE_*` or `SPLINE` anywhere; `LINEAR` used in exactly one class (`SweepPanel`, which §5 specifies as a linear loop); no hex literal in any `ui` or `view` class; no non-zero border radius, drop shadow or blur in the stylesheet; no proportional typeface named anywhere; both bundled OFL families present in the jar. ⚠ **One factual error in the source document was corrected in the checked-in copy:** §10 criterion 8 claimed "JavaFX cannot read the OS preference" for reduced motion. It can — `Platform.getPreferences().isReducedMotion()` is observable and the client had been reading it since 2026-07-25. The criterion's advice still stands and is implemented.

  **`RigStripView` was deleted** (352 lines, unreferenced after the overhaul): it assigned seven distinct accent hues to compute consumers, which is the semantic colour system §9 lists as build-blocking. Its job is now done by the top strip and by the 100-cell grid, where owners are distinguished by *position in the amber-to-grey ramp* so the palette encodes income-versus-overhead instead of category. Two long-standing view bugs surfaced while checking the new panels against each other and were fixed: the mining window rendered the balance as `Ethecoin[minorUnits=0]` (a record's `toString`) and started its allocation slider at 0 while the rig held 30 cycles — both are the *same* failure, two readouts of one number disagreeing, which is exactly the discrepancy `../design/04-mining.md` §3.1 trains the player to treat as evidence.

- 2026-07-26 — **The rig monitor became an activity monitor, and the game started drawing its own pointer** — recorded in `ui-design-language.md` §12, `solo/state/TaskState.java`, `client/ui/widgets/ActivityList.java` and `client/ui/cursors/`. Two features, and the first one surfaced a gap worth naming: **`docs/design/04-mining.md` §3.2 publishes a Duration for every scan tier and nothing was waiting for it.** `scan()` spent the cycles and returned, so a Thorough Scan was instantaneous and its "~6 min" was decoration. Scans are now real tasks with a start, an end and a persisted lifetime — they survive quitting, complete on the first tick back, and log their finding, for the same reason offline miner income is logged: work the player cannot see is indistinguishable from a bug. The compute side is untouched; see **UI-6** for the balance half that stays open.

  **Progress is counted, not swept.** §4: "3px × 9px cells with 1px gaps. Never a continuous bar or gradient." So the activity readout is a `CellMeter` and a countdown in words, never a `ProgressBar` — same argument as the cycle grid, that a smooth bar implies a precision the model does not have. Unknown progress is rendered as unknown (§5's linear sweep) rather than as an empty meter, because a bar reading 0% on a recovery that is nearly finished is worse than one that admits it does not know.

  **Two bugs were caught by writing the test rather than by looking at the screen.** (1) `RunningTask.progress()` called `Instant.now()` instead of the session's clock — the same "engine reads the wall clock behind its caller's back" failure `ComputeRules.spend`'s own comment warns about. Under any clock but the real one, every task reported 100% complete the instant it started; in production the two clocks agree and nothing would ever have looked wrong. (2) `resume()` sets `lastTick = now`, so `tick()` sees zero elapsed time and returns early — a scan that ended while the game was closed would have sat at 100% forever, never completing. Tasks now settle on the resume path, next to the miner accrual that lives there for exactly the same reason.

  **The pointer.** Three drawn skins plus the system one, selectable in Settings → Pointer. ⚠ **Two more measured JavaFX traps, the fourth and fifth in this project**, both of which would have sent an implementation down a wrong road: **`-fx-cursor: url(...)` does not work at all** — it parses and then fails at apply time with `ClassCastException: java.lang.String incompatible with javafx.scene.Cursor`, so a custom cursor cannot come from a stylesheet; and **a CSS `-fx-cursor` on a node beats an inherited Scene cursor**, measured by reading `getCursor()` back before and after applying a sheet. Together those mean every `-fx-cursor: hand` left in `theme.css` would have punched a system-cursor hole in whichever skin the player chose. All five declarations were removed and replaced by `Cursors#clickable`, and a test now fails the build if one reappears. A third measurement was reassuring rather than alarming: a hotspot outside the image is **clamped, not rejected** (`new ImageCursor(img32, 42, 42)` → `31,31`), which is safe but means a wrong hotspot fails silently.

  **Cursors are drawn in the theme's own colours with no colour constant in Java.** §10 criterion 2 bans hex literals under `client/ui/`, and a cursor is pixels — the two collide directly. `ui/cursors/Palette` resolves a style class against the live stylesheet by applying it to a throwaway `Region` and reading the fill back, so the pointer is drawn in whatever `-es-amber` currently means and follows every palette overlay for free, including high-visibility. That is the rule working rather than a way around it: a cursor with its own hard-coded amber would be the one element of the interface that did not change when the player switched to phosphor.

- 2026-07-26 — **Noise stopped meaning "busy"** — recorded in `client/view/RigStatus#outwardCycles`. The status strip's noise meter was reading total rig load, which is the exact inverse of the risk model the economy is built on. `08-stealth-and-noise.md` §1: "noise is generated by **acting**"; **I4** makes self-mining structurally silent, **I9** makes defending your own rig silent, and `04-mining.md` §3.1 makes scanning your own rig silent. So noise is now the sum of `CONTROL_CHANNEL`, `RELAY_HOP` and `BOT_FRAME` cycles only — work that reaches other machines. A rig at 100% load on self-mining, defences and local scans reads **zero**, which is correct and is precisely the strategy the economy rewards. ⚠ `DEPLOYED_MINER` is excluded deliberately even though it crosses a network: by **I6** it is charged to the *host's* rig, so seeing one in your own budget means somebody else's miner is running on you — counting it would make being a victim look like being an aggressor.

  It is drawn as a **sound meter** rather than a bar for the same reason: a bar reads as a quantity you accumulate, and noise is a rate that decays. Eighteen columns with peak-hold, which keeps a spike visible after it has gone — the spike is what got the player noticed, and without a hold it would be gone before they looked up.

- 2026-07-26 — **The blank space beside the cycle grid became a second instrument** — `client/ui/widgets/CoreCage.java`. Capping the grid's cell size (so 25 cells do not become a chessboard on a wide panel) left usable space to its right. It now holds a slowly turning orthographic cutaway of the rig's compute cage, drawn in box-drawing characters: two hexagonal plates joined by six posts, where **a post is amber for exactly as long as its bank is self-mining** — the only amber in the render, and §2.1's "live/earning data only" applied literally. Depth is carried by glyph weight rather than colour, which the single-accent palette forces and which turns out to read as a service manual, exactly what the fiction wants.

  **The horror is on a timer the player controls.** As personal heat rises the render loses cells — dropouts, not added static, because a rig under attention loses signal rather than gaining noise — and above the Named-hacker band an iris opens inside the cage. A player who manages their heat never sees it. ⚠ The aspect correction in `project()` is the one line that matters: a character cell is about twice as tall as it is wide, and a render that treats the grid as square produces an egg. It is invisible until compared against a real circle.

- 2026-07-26 — **Four interface additions from play testing** — the activity readout was frozen (it held immutable `RunningTask` snapshots stamped with the engine clock and only refreshed on session-change events, which a running scan does not fire — it now polls at 400ms and shows elapsed/total/percent alongside the countdown); ten panels including Settings gained scrolling with a deck-styled 9px gutter; the pointer failed to take over most of the screen because `DeskManager` set `Cursor.DEFAULT` — an explicit request for the platform arrow — on every window frame instead of `null`, which means inherit; and double-clicking a title bar now restores a window that was **dragged** to full screen, not just one maximised with `[□]`, because tiling and maximising were separate code paths and only the latter set the flag the double-click tested.

- 2026-07-26 — **Eleven characters the client drew were in neither bundled font** — fixed across `solo/state/RigEvent`, `client/ui/widgets/{Greeble,CoreCage}`, `client/ui/{DeckShell,Notifications}`, `client/ui/chrome/WindowFrame`, `client/view/Views`, and pinned by `GlyphCoverageTest`. Reported from a cmap parse of the bundled TTFs and confirmed independently.

  **What was broken:** `▮ ▯ ⋮ ▪ ▫ ▲ ● ○ ✖ ‼ ⌘ □` are absent from both IBM Plex Mono and Martian Mono. That included the **maximise control on every window title bar** (`□`), the **Shortcut glyph in every key hint** (`⌘` — which on a Linux box without an Apple font is a tofu box, in the one place the interface explains itself), **four of the six log severity glyphs**, and most of the greeble's alphabet. Every one was being drawn by whatever the host OS substituted, which is exactly what `ui-design-language.md` §2.2 bundles the fonts to prevent — and worse than cosmetic, because a substituted glyph brings its own advance width, so a character-cell texture rendered at a different length per platform.

  **The rule that came out of it:** Martian maps ~638 codepoints against Plex's ~1049 and has **none** of the block-element or box-drawing range (U+2500–U+259F) that every texture here is built from. So **a glyph-based texture is pinned to IBM Plex; Martian is for uppercase Latin labels only.** The greeble strip and the boot logo were both on Martian while drawing block characters. A test now asserts the stylesheet pins them.

  **The check is a cmap walk**, because `Font.loadFont` succeeds whether or not the face contains the glyph you are about to draw and JavaFX exposes no per-codepoint coverage query. `GlyphCoverageTest` parses the format-4 subtable of each bundled TTF, scans every string and char literal in `client` and `solo`, and fails on any uncovered character. It caught two more during the fix that manual review had missed.

  ⚠ **A real rendering bug surfaced while re-checking the ASCII cutaway**: it drew edges in index order with no depth test, so the last edge to touch a cell won. Two posts 180° apart project to the same column, so the far side painted over the near side and the near posts vanished entirely — the cage read as an empty frame. Now z-buffered.

- 2026-07-26 — **The rail launcher got tooltips with real descriptions** — `WindowSpec.description()`. The rail is 34px (§3) so it can only show one accelerator character per tool, which made it seventeen unlabelled keys. `unixAnalogue()` was already there and teaches — a player who learns the audit window *is* `ps`, `netstat` and `df` has learned three commands for free — but "ps / netstat / df" does not tell somebody who has never used a shell what the window is *for*. Each spec now carries both. §3's ban on "tooltips carrying information not shown elsewhere" holds: every line is also on the tool's header strip, in the switcher, or in the manual.

- 2026-07-26 — **The desk now remembers itself, and it never had** — `ClientProfile.DeskWindowState`, `DeckShell#saveLayout` / `#restoreLayout`. The persistence that survived from the `Stage`-per-tool era wrote *nothing the deck could read*: `WindowRegistry.rememberAll()` walks a map of open `Stage`s, and since §0's reversal there are none, so every quit cleared the saved set and saved an empty one. A player rebuilt their arrangement every session. The desk now records position, size, minimised and expanded state, and the restore point an expanded window returns to on double-click — **in open order**, which brings z-order back for free because the desk raises each window as it replays it. Written at the same four moments the session is: autosave, the pause menu's Save, returning to the menu, and quit, through one `saveEverything()` so those four cannot drift into saving different subsets. ⚠ **Capture happens before dispose**, because disposing closes every window and there would be nothing left to record — getting that order backwards saves an empty desk over the player's layout on every quit. Restored geometry is clamped to the current desk: a layout written on a large monitor and reopened on a laptop would otherwise put windows off-screen, where they are invisible AND focusable.

- 2026-07-26 — **A StackPane layout bug that could hide the compute readout** — `DeckShell`. The deck's four regions live in a `BorderPane` inside a `StackPane` that also carries the notice stack and the pause menu. **A StackPane centres a child larger than itself**, clipping it equally top and bottom — so once the deck's minimum height exceeded the window's, the top status strip slid off the top of the screen, taking the always-visible compute readout with it. That is pillar **C2**'s one structural guarantee failing silently at small window sizes. Fixed by letting the deck be squeezed (`setMinSize(0, 0)`) rather than overflow: smaller panels are recoverable, a readout above y=0 is not reachable at all.

- 2026-07-26 — **The top strip became instrumentation rather than a row of words** — `client/ui/DeckShell` and `client/ui/widgets/{ThermoMeter,NoiseMeter,Sparkline}`. Heat is a **banded thermometer** (see **UI-8**); noise is a twelve-row sound meter with peak-hold and an MHz reading derived from the same value the bars are; **load and thermal recovery are time-series sparklines** over the last thirty seconds. The sparklines and the noise meter look alike at strip size and answer opposite questions — a spectrum of simultaneous readings versus a history — so the sparkline's newest column is brighter, which is what tells a player which one they are reading. Everything samples on the shell's single one-second data tick rather than per-widget timers: two histories drifting a few hundred milliseconds apart would put the same spike at two different moments.

  **Balance** gained a projected rate beneath it and **Session** an in-world clock, both following one rule — a strip cell's second line is always a <b>derived reading of the value above it</b>, never an unrelated number sharing a glance. The clock reads `GameSession.now()`, the *engine's* clock, for the same reason `RunningTask#progress` had to be fixed: a readout showing wall time beside figures computed from another clock is a disagreement waiting to happen.

  **The operator name now shows its own bytes.** `HALFLIGHT` prints `48 41 4C 46 4C 49 47 48 54` underneath, elided past ten bytes. It hexes the *displayed* uppercase form so each pair lines up with the glyph above it, and it encodes `getBytes(UTF_8)` rather than per-`char` — encoding per char would print UTF-16 code units and quietly teach something false. It is the cheapest available demonstration of the one idea `../education/01-foundations.md` exists to teach: text is bytes. A player who notices `H` is `48` and that lowercase would be `68` has found the bit that separates ASCII case.

  **Renaming is solo-only, structurally.** `SoloGame.rename` is deliberately NOT on the `GameSession` port: online, a handle comes from an AT Proto DID and the server owns it (**I14**), so putting it on the port would advertise a capability that must never work there. A capability that must be impossible is best made absent rather than guarded. Handles are restricted to printable ASCII and 24 characters — not arbitrary limits, but because the strip prints the name as bytes and a name that is always elided is a name the player never sees.

- 2026-07-26 — **Four UI questions taken to a decision: UI-5 confirmed, UI-6 changed the economy, UI-8 confirmed with a caveat, UI-2 deliberately left open** — recorded in `04-mining.md` §3.2, `01-core-resources.md` §1.3, `../client/06-resource-and-inventory-ui.md` §2.5, and §2 above. These were raised on 2026-07-26 and would otherwise have shipped as decided-by-default, which is the failure the section exists to prevent.

  **UI-6 is the one with teeth: a scan now HOLDS its cycles for its published Duration and only then starts recovering.** It was spend-immediately, on the reading that §3.2 lists Compute and Duration as separate columns. §3.2's *next sentence* is what overturned it — it promises a Thorough Scan leaves the player "effectively down 35 cycles for far longer than the scan runs", and under spend-immediately that was true only on an already-loaded rig. On a lean one the 35 cycles were back in about four minutes, **inside the six-minute scan that paid for them**, so the published Duration cost nothing at all in the case the paragraph was written about. ⚠ **This roughly doubles a Thorough Scan's real cost and is a price change, not a clock change.** `CLAUDE.md` requires re-checking the tables that depend on a moved number, and that was done rather than assumed:
  - **`01` §1.3's recovery anchors survive but now compose.** "~2× its run duration to fully recover" at 50% load was always a statement about the *recovery*, not the total — so the figure is unchanged and the total is now ~3× the run duration (~6× at 85%). §1.3 says so explicitly now, because that is the sentence a future tuner reads before moving `base_rate` or `k`.
  - **The load factor that sets the curve excludes the releasing allocation** (`ComputeRules.loadFactorExcluding`). Measuring load with the scan's own cycles still counted would charge the scan a recovery penalty *for its own cycles* — a second, compounding cost nothing asked for, which would have made the change quietly worse than the doubling it was decided on.
  - **The three published cost/duration figures are untouched** (5/15/35 cycles, ~30s/~2min/~6min), so every curriculum entry that quotes them still holds. Checked all four: `education/02`'s `locality`, `memory-hierarchy` and `thermal-budget` pages and `education/08`'s defence-budget page. Two of them — the ones saying an overextended player is "down 35 cycles for far longer than the scan ran" — become *more* true, since that is now the case on every rig rather than only a loaded one.
  - **Recovery is dated from the task's end, not from the player's next tick.** A scan that finished while the game was closed must not restart its recovery clock on load, or a week away returns a rig still nursing Tuesday's scan. Same argument as `settleRecovered` settling on the resume path; both are covered by tests.
  - ⚠ **`resume()` needs two recovery sweeps and the second is not redundant** — a finished task only becomes `RECOVERING` inside `settleTasks`, dated from when it ended, so without a sweep after it the player watches a week-old scan recover in front of them.
  - **`../client/06` §2.5's compute journal is now two rows for a scan**, held and released, with the load multiplier on the *release* row: under hold-then-recover the load that sets the curve is the load when the cycles let go, which need not be the load when the player pressed the button.

  **UI-8 confirmed, and the entry that recorded it was wrong about the code.** The banded thermometer stays. The band name stays *out* of the strip — `KeyValue.keyOnly("Personal heat")` — which UI-8's own text had claimed was not the case. Since `../client/07` §5.2 forbids meaning resting on appearance and a JavaFX tooltip is mouse-only, the band name and the numeric heat are now `ThermoMeter`'s **accessible text** as well, which is **CL-10**'s two-path fix reused. The remaining stretch of §5.2 is recorded in §2 rather than smoothed over.

  ⚠ **Rendering the confirmation is what found UI-9**, a new question: the thermometer's five-band ramp is **non-monotonic in luminance in all four themes**, with the hottest band *darker* than the coldest in every one (`deck`: 0.159 vs 0.252). That is **AX-5**'s failure in a new place — AX-5 fixed the uOS heat *chip* on 2026-07-25 and the thermometer, added a day later, built its own ramp out of general palette tokens that were never chosen to sequence. Logged rather than patched, because the fix is a palette decision against a contract; see §2. It is also a standing vindication of checking by rendering: `UiContractTest` is green, every colour is a token, no rule is broken, and the meter still tells the player the wrong story as it climbs.

  **UI-2 was reviewed and left open on purpose** — the Bandwidth window cap keeps its mechanism and stays defaulted off. Recorded because "asked and deliberately not decided" is a different state from "never asked", and the next session should not spend the question again. The argument against it that the review surfaced is now written into §2.

  **UI-5 confirmed:** uOS Classic stays flat and keeps its name. No theme gets an exemption from §9.

- 2026-07-26 — **The desk got a wallpaper, and §9's screen-artefact ban was amended around it** — recorded in `ui-design-language.md` §9.1 and §12, `client/ui/widgets/Substrate.java`, `client/ui/CrtOverlay.java`, `client/ui/WallpaperMode.java`. Two changes, one of them to a contract.

  **§9 previously read "CRT scanlines, vignette, bezel, chromatic aberration — explicitly cut, do not reintroduce."** On explicit direction it now permits **scanlines, chromatic aberration and light VHS glitch as optional, player-switchable effects**; **bezel and vignette stay cut**. The line the list draws is now *switchability* rather than the effects themselves, and that is faithful to the original argument rather than a softening of it: the entry was never really about how artefacts look, it was that an interface which permanently degrades its own legibility is lying about what it can show. A toggle answers that completely, and all three ship **off**. `ScreenArtefactTest` asserts the defaults, asserts bezel and vignette are still named as cut, and greps the stylesheet for `radial-gradient` — the only way a vignette could be drawn here — so the amendment cannot quietly widen.

  ⚠ **Chromatic aberration is scoped and the scope is stated in the UI itself.** Full-scene aberration would mean snapshotting and recompositing the whole scene every frame, and there are no shaders available; it is applied to the **wallpaper** (a text node, so three offset labels rather than a raster) and to the **edges of glitch bands**, which is where a tape artefact bleeds colour anyway. The Settings copy says so, because the alternative is a bug report from someone expecting a fringe on the terminal.

  **The wallpaper is greeble at desk scale**, not a new visual idea: §4's exact alphabet, ~7% cell occupancy, `dim-3` at 0.55 opacity — which resolves to about `#1F2727` over the void, i.e. **hairline weight**, deliberately the same order of thing as a panel border. Never amber (§2.1's accent reservation matters most on the largest surface in the client). It has **three states rather than a checkbox** — off, still, drifting — because **WCAG 2.2.2 (Pause, Stop, Hide)** requires automatically-starting motion over five seconds to be pausable, and folding pause into "off" would satisfy the letter while forcing a player who wants texture without movement to give up both. Rows drift at three different rates: a field sliding as one sheet reads as a scrolling raster, which §9.1 still does not want.

  ⚠ **Rendering it found two JavaFX traps, and a green build had reported the feature as done while the desk was empty.** Both are now in `CLAUDE.md`'s list, which went from five to seven. (1) **`theme.css`'s late `.label { -fx-text-fill: -es-text; }` beats any one-class selector** by ordering at equal specificity — so `-fx-opacity` from the wallpaper's own block applied while `-fx-text-fill` was silently discarded, painting it in body-text grey at four times the intended weight. The split between which properties survive is what makes it nearly invisible on inspection. (2) **A width/height listener on the deck fires before the `BorderPane` has laid out its centre**, so `desk.getWidth()` inside `DeskManager.reflow()` is still the previous value; windows survive that because they only clamp against the desk, but the wallpaper was sized from it and stayed **0×0 permanently**. Both were found by measuring — a pixel histogram of the bare desk returned *two* colours — and neither would have failed any test that existed. `DeckSnapshot` now writes a **bare-desk frame**, because every other snapshot tiles four windows edge to edge and therefore covers the one layer being checked.

  **Two revisions the same day, both from looking at the result.** (a) **Glitch was rebuilt to tear at edges.** It shipped first as full-width tracking bands and that was wrong: a real signal breaks up where it changes fastest — window frames, panel borders, table rules, the edges of readouts — and a band floating over empty desk has nothing to be an artefact *of*. `DeckShell` now supplies the overlay with the bounds of every open window frame plus a bounded walk of the elements inside it, and slivers are torn sideways off those. It self-scales in the right direction as a side effect: **a bare desk barely glitches, a crowded one glitches most.** (b) **Scanlines were animated**, because a still line pattern is a Moiré texture rather than a tube. They now drift one pixel every ~500ms and carry a **refresh bar** rolling down the screen, which is the artefact a camera pointed at a CRT actually records. The drift is deliberately slow: fast drift over body text is a shimmer that is tiring to read through, which would undo §9.1's own condition 2.

  ⚠ **The refresh bar is the one soft-edged thing in the client**, and it is permitted by §9's existing wording rather than by a further amendment — gradients are allowed where "hard-edged or **near-transparent**", and every stop in it is under 0.05 alpha, which `ScreenArtefactTest` enforces as a ceiling.

  ⚠ **The scanline period is genuinely duplicated** — the gradient's `to 0px 4px` in `theme.css` and `CrtOverlay.SCAN_PERIOD` in Java, because §7.2 means JavaFX cannot look a *size* up from CSS and the roll cannot read the pattern it is rolling. If they diverge the lines jump once per cycle instead of wrapping, which reads as a rendering stutter rather than as a wrong constant. A test asserts they match; that is the only defence available.

  **A third revision, and a fourth thing added.** (c) **The glitch was rebuilt again, to displace real elements.** Anchoring it to edges was right; *drawing slivers over* those edges was not, because painted marks read as decoration sitting on the screen while a tape fault **moves the image**. It now sets `translateX` on real nodes — a render transform, so no layout pass — and stutters in **bursts**: quiet 3.5–12s, then 3–8 frames at 90ms re-randomising each frame. The original held one pose for 1.4s, which reads as a rendering fault rather than damage; a VHS tear is a snap. ⚠ It mutates nodes it does not own, so each displacement stores the node's **previous** translation and is restored on burst end, switch-off and dispose — restoring a hard zero would silently destroy a translation set elsewhere in the client. (d) **A curvature slider** was added, and §9.1 gained simulated tube curvature as a permitted optional effect. It does **not** warp the interface and the setting says so: barrel distortion needs a shader we do not have, and the render-to-texture alternative *breaks input* — hit-testing would use undistorted geometry, so every click would land away from the control. What it scales is radial rim aberration, measured at full strength as R−B of **−2 at centre, −11 at the edge midpoints, −19 at all four corners**.

  ⚠ **The first aberration model was measurably wrong and the render caught it.** Left/top warm and right/bottom cool looks reasonable; it makes the two bands carry *opposite* channels at the top-right and bottom-left corners, where they cancel — +4 and −8 against +17 and −22 at the other two. Lateral CA magnifies one channel more than the other, so red is outboard on **every** edge and cyan inboard on every edge. Two strong corners and two washed-out ones is that mistake's signature, and it is invisible without sampling the pixels.

  **Reachable from both paths (pillar C1):** Settings → Screen, and the `wallpaper` and `crt` commands (`crt curvature <0-100>`), through the same profile and the same apply call so they cannot disagree.

- 2026-07-26 — **The core loop was specified: `05-hacking-minigame.md` stopped being a proposal** — recorded in `05` (§3.1, §4, §5, §6), `04` §3.2a, `09`, `02` §2.2 and `06`. Eight questions closed in one pass: **P-1, P-2, P-4, OQ-5, OQ-6, DF-5**, plus two doc corrections. `05` was the keystone — its own header noted that "every tool, every gate, every risk system is defined in terms of a puzzle that doesn't have rules yet".

  **P-2 is the load-bearing one: a breach is turn-based, on an attention budget, with no wall clock.** Each layer grants a budget scaled by `difficultyTier`; every action spends from it at a published rate (quiet read 1, ordinary probe 2, loud tool 6, Overflow bypass nearly all of it). Three reasons, in order of weight: Pillar 1 says the puzzle *is* the game and a clock makes it partly a reflex test; **Invariant I10 becomes measurable**, because the bot-versus-human gap is now a probe count rather than seconds — deterministic and tunable instead of varying with hardware and reaction time; and the accessibility risk `05` §5 flagged simply disappears. ⚠ **§2's economy-facing contract did not change** — `traceProgress` is now attention consumed as a fraction of the budget — which is exactly what §2 was written separately to survive.

  **P-1 followed from P-2 rather than from taste.** Five classes became three (Enumeration, Logic, Traversal) by applying §3.1's own merge rule — *"if two classes reduce to the same optimal input pattern, merge them"*. **Timing** had no expression in a probe budget: its skill was "sequencing, rhythm, patience", which is an action skill. **Credential** was already Logic; the proposed table gave one "pattern deduction" and the other "reconstruct a rule from probe responses", which is the same verb. Keeping both would have shipped precisely the reskin §3.1 warns against.

  **P-4: no tool was cut.** The three orphaned by the merge were repointed — Rainbow Table and Credential Harvester to **Logic** (they reveal part of a rule or skip a deduction step, which is what they always did), and Side-Channel Reader to **Enumeration**, where it becomes the only action in the game costing **zero attention**. "Read without entering" is a far stronger identity under a budget than it was under a clock.

  **DF-5 and OQ-6 were solved by the same stroke.** Scans can now be **wrong** — every tier has a false-positive rate, high on Quick and low on Thorough — which closes a real contradiction: `education/08` teaches `false-positive(7)`, `base-rate-fallacy(7)` and `alert-fatigue(7)`, so the game was contradicting its own manual, which `CLAUDE.md` rates worse than teaching nothing. It also makes the Thorough Scan's price legible: you are buying a result you can act on without a second look. That in turn gave the **Detection Array** a distinct job — it cuts the false-positive rate instead of raising discovery chance, so scans buy *sensitivity* and the Array buys *precision*. Two different axes, non-redundant by construction rather than by tuning, and OQ-6 closes without deleting the one item that makes standing compute worth committing.

  ⚠ **`05` §5 was factually wrong and is corrected.** It opened "Because the client is multi-window (`../architecture/01`)" and demanded a single-window fallback. `ui-design-language.md` §0 cancelled that model; there is one undecorated window with a window manager inside it. The accessibility concern is now answered twice — one OS window, and after §4 no time pressure to manage it under.

  **Deliberately not decided:** the multiplayer, social and narrative questions (**D-1/2, S-1/2/3, N-1/2**). Multiplayer is blocked on something no decision fixes — **CL-8** records that `RemoteGameSession` exists but its transport, OAuth flow and reconnect loop do not — so settling the duel-quorum boundary now would fix rules for a system nothing can exercise. **N-2** (critical-path schematic beats) is content authoring rather than mechanics. Also still open: **E-1** (vault expansion reconciliation), and the four calibration questions **OQ-1/3/4/7**, which their own entries say resolve with playtest telemetry and would only gain a false authority from being decided at a desk.

- 2026-07-27 — **Network discovery: topology generation, the `sweep` verb, and a graph view** — recorded in `07-recon-tools.md`'s hop model (unchanged, and that is the point), `solo/net/`, `protocol/game/Net*`, `client/ui/netmap/` and `client/view/NetMapView`. The problem was concrete: discovery was unusable at the start of the game, so the core loop had nothing to point at.

  **The load-bearing decision is that schematics buy reach and ethecoin buys sensitivity.** `07` is Established and makes hop range a *ceiling* on information — which is exactly why the Topology Mapper is schematic-gated, since **Invariant I2** forbids ethecoin buying a ceiling. A noise-probability discovery model threatened that directly: if a better sweep tier saw further, EC would buy reach. The reconciliation keeps both halves: **hop range is raised only by schematic/story gates and never by a tier or by noise**, and within the reach a player already has, sweep tier and target noise together set the *probability* of detection. Sensitivity is breadth, which I2 permits. Verified in play: hop ceiling reads 1 with no Topology Mapper.

  **`sweep` is a new verb, deliberately not `scan`.** `04` §3.2's `scan` audits your own rig for parasites; `sweep` probes a network you do not own. Collapsing them would have made one of the two a lie, and the distinction is worth teaching on its own.

  **Generation**: 5–7 virtual servers, ≤50 machines each, from a depth-biased spanning tree with depth-preserving chords — chosen over a chain (no choice), a ring (ambiguous depth) and a random graph (connectivity becomes a retry loop rather than a construction). Measured over two fresh characters: 153 and 190 hosts across 5 and 6 servers, with average tier climbing **1.21 → 1.82 → 2.89 → 3.79 → 4.57** by depth from home. Home is easiest and depth is the difficulty gradient, exactly as briefed.

  ⚠ **Three real bugs were found by running it, not by reading it.**
  1. **Every character generated the identical world.** `SoloSave.rngSeed` has a constant default and nothing ever derived it, so the topology, detection rolls, loot and documents would have been the same for every player in every install — invisible until two people compared notes. `newCharacter` now derives the seed before anything draws from it.
  2. **A completed sweep discovered nothing.** `settleTasks` logged `"scan ... finished"` for *every* task kind and deleted it, so `NetRules.settleSweep` was never called: the network stayed empty while the log claimed a scan had finished. A task list stopped having one kind of task in it and kept a single code path. It dispatches on kind now.
  3. **`NetGraphTest.crossingsMerge` asserted a layout accident.** It required a rendered fixture to contain `┼`; no fixture produces one, because the router gives each edge its own lane. The test would have failed the day the router got *better*. Retargeted at the merge rule itself, which is total and round-trips — the structural evidence that merging happens in a real render lives in `fanOutMerges`, which passes.

  ⚠ **Still owed: `docs/design/17-network-topology.md`.** The architect's spec — the generation algorithm, the detection formula and the sweep tier table — currently exists only in the workflow output and in code comments. `CLAUDE.md` requires the rule to live in a design doc, so this is a real gap and not a tidy-up.

- 2026-07-27 — **P-1, again: the breach minigame replaced outright** — resolved to **two classes** — recorded in `05` §3.1 and `16` §3–§5. Enumeration, Logic and Traversal are gone; **Breach Protocol** (route a code sequence out of a matrix into a short buffer) and **Offset Cipher** (write the signed offset under each of 6–16 hex bytes) replace them. The three that survived the previous cut still failed §3.1's own merge rule — Enumeration's "read the structure" and Traversal's "route through the structure" were one skill in two costumes, and Logic's Mastermind deduction was in practice a search performed by guessing. What is left is a real axis: **pressure of place** against **pressure of precision**, where being good at one predicts nothing about the other — which is what a proof-of-skill gate (**I7**) has to be able to claim. Three things are worth not re-deriving. (1) **The class is drawn once per attempt and frozen at commission**; a mixed attempt would be two short games and would make a hard target's deeper layers a lottery between the puzzle you are good at and the one you are not. (2) **The cipher has no clock**, because a timer would put arithmetic back under reflex pressure and `05` §4 removed the clock on purpose — so it pays in **noise** instead (×1.8, applied to the attempt's total, *not* per action: per action it would make the cipher quieter, because it has three commits where a grid has eight picks). (3) **Every grid goal is cut out of a walk the generator actually took**, because a randomly-generated sequence is very often unreachable and an open-information puzzle that is unfair gives the player no way to find out — asserted by search on every board at every tier. Schema followed: `V4__breach_puzzle_classes.sql` rewrites the retired spellings onto their nearest survivor rather than deleting rows, because `breach_resolutions` is the proof-of-skill ledger and a deleted row silently revokes an unlock a player has already earned.

- 2026-07-27 — **Self-mining becomes a real proof-of-work simulation, with a pooled/solo choice** — recorded in `04` §1.3, `03` §1.1–1.2, `glossary.md`, and `../education/07` §3.25–3.26. Self-mining was a rate; it is now a Poisson process against a real difficulty, played either **pooled** (a share every ~30s, pay-per-share, 2% fee, near-constant) or **solo** (one whole 160 EC block or nothing, ~every 4 hours at a full rig, no fee). ⚠ **Nothing in `03` was re-tuned, and that was the design constraint rather than a happy accident**: the chain's network hashrate is *derived* (`solo/Balance.chainNetworkHashrate`) from the subsidy and the 0.4 EC/cycle-hour anchor, so pooled income lands on the documented figure to the minor unit and solo lands on it divided by (1 − fee). Hardcoding the hashrate would have silently decoupled the two, and the first symptom would have been an income table that was wrong. Five things worth not re-deriving. (1) **Pooled is the default because I4 says self-mining is the *floor***, and solo pays nothing in ~77% of hours — a hot player silently opted into the lottery would find the safety net had become a second punishment. An unrecognised mode string falls back to pooled for the same reason. (2) **Bitcoin's real constants are reused verbatim** — `difficulty × 2^32`, the ten-minute target, the 2016-block retarget, the ×4 clamp — because `../education/07`'s transfer tests tell the player to check the arithmetic against a live block explorer, and an invented constant would have made every worked example false. (3) **Memorylessness deleted a proposal rather than needing one**: §1.3's old *"pulling cycles mid-block forfeits that block's progress"* described a thing that does not exist, so mode switches and reallocation are free, and the UI publishes an expected time and **never a progress bar** — a bar would teach the gambler's fallacy in the one place players reliably hold it. (4) **The player's own hashrate must be subtracted from the network's** when they mine solo, or the chain counts their blocks twice, the retarget reads that as hashrate arriving, and difficulty spirals: measured at 3.8× income before it was caught. (5) **Deployed miners and bots are deliberately unchanged** — variance on income the player cannot watch is punishment without a decision, and it would break `04` §5.1's crack timing bet, which is priced on the buffer filling smoothly. Open: **MN-1**, whether the server should simulate one chain shared across a federation rather than one per character; solo's chain is local and can never federate, exactly like the rest of a solo save (I14).
- 2026-07-27 — **Five pools, and the MINING panel rebuilt around them** — recorded in `04` §1.3a, `glossary.md`, `../education/07` §3.26. Pooled mining is now a choice of pool: THE COMMONS (PPS 2.00%, the default and the economy anchor), MERIDIAN CLEARING (PPS 3.50%, 38% of the chain, steadiest), PALE LANTERN (PPS 2.50%, one operator), GLASS TEETH (PPLNS 1.00%), SMALL HOURS (PPLNS 0.50%, 5% of the chain, cheapest and lumpiest). ⚠ **The roster is built so nothing dominates**: as the fee falls, income rises *and* the payout interval lengthens, because only the fee moves income while the scheme moves variance — under PPS from the share target (size-independent), under PPLNS from the pool's own block rate (size *is* the knob). Asserted by `MiningChainTest.noPoolDominates` and `onlyTheFeeMovesIncome`, which is what stops a future tuning pass quietly turning the list into a ladder. Three implementation notes. (1) **Solo, PPS and PPLNS are one equation** — `subsidy × fraction × (1 − fee)` every `difficulty × fraction × 2^32 / hashrate` — and the fraction cancels out of the income; writing them as one function is what keeps them from drifting apart. (2) **MERIDIAN's 38% caution has no mechanics behind it and must not grow any**: a consequence would have to touch detection or heat on self-mining, which **I4** forbids. (3) **Pooled mining is a faint noise contributor and solo is silent** — a pooled rig holds a connection to a pool and pushes a share up it on a timer, which is outbound traffic; solo grinds locally and announces nothing until it finds a block. ⚠ **I4 survives**: it grants immunity to detection/seizure and *zero heat*, and a pooled rig keeps all three, because noise is a rate and nothing converts it to heat. The noise is deliberately **flat in allocation** — a share is a fixed packet, and a figure that scaled with hashrate would make the noise-conscious play "mine less", punishing the income floor for being used. It *does* scale with the pool's share interval, which makes picking a quiet pool a real play and gives the roster a third axis for free. (4) **Pool payouts settle on a 60-second window** rather than per share, because 120 ledger rows an hour buries `ledger(1)`; the credit and the row always happen together, since a balance ahead of the last row is the exact discrepancy `04` §3.1 teaches players to read as an intruder. A backwards clock settles immediately — a naive window check would otherwise hold the player's money forever after a host clock correction. (5) The **MINING panel had not been updated** with the proof-of-work work and still printed `cycles × 0.4` — a third copy of a balance rate in a view class, wrong for solo miners and for every pool but the default. It now reads the engine's published expectation, as `RigStatus` and `RigMonitorView` already do; `Views.mining()` carries the note.

- 2026-07-27 — **Fourteen-minute blocks, won rather than raced, and the Ledger tool becomes a block explorer** — recorded in `04` §1.3/§1.3a/§1.3b, `glossary.md`. Three changes. (1) **The interval is 14 minutes and the retarget window 1440 blocks**, neither of which is Bitcoin's; the `difficulty × 2^32` relation and the ×4 clamp still are, so the curriculum's transfer tests still check out. 1440 × 14 min is still a fortnight — the property 2016 exists for, without the number. ⚠ **The economy did not move**: `chainNetworkHashrate()` is solved from the interval and the `03` §1 anchor, and their product is fixed by it, so the network shrank 2352 → 1680 cycles and every income figure is unchanged. (2) **A block is now won by a single draw** weighted by hashrate share, rather than each miner racing their own exponential. Equivalent in expectation, and *legible*: every block has a winner the explorer can name, and "your chance at this block is your share of the chain" is checkable against the readout. Memorylessness survives — the wait is geometric, which is the discrete memoryless distribution. PPS pools are still settled off-chain on a share clock, because a PPS miner is paid whether or not anybody found a block. (3) **The LEDGER window is a block explorer**: block cards, the rig's address, its transactions. Ethereum's *shapes* over Bitcoin's *mechanics*, which is coherent because pre-Merge Ethereum was itself a PoW chain — so `nonce`, `miner`, `difficulty` and `gasUsed` are all honest here. Gas is real arithmetic (21 000 per transfer); the gas limit is deliberately **not** Ethereum's 30M, both because a limit is per-chain and because Ethereum's made every fill bar read 2%. Only a 24-block window is stored and everything else is derived from height, so a save does not grow for a readout. ⚠ Two traps found: the shrunken network made a 100-cycle rig **larger than the 5% SMALL HOURS pool**, silently clamping PPLNS into solo-with-a-fee — the roster's PPLNS floor is now 12% and `pplnsPoolsOutHashAMaxedRig` guards it; and a pool payout must carry **no block number**, or the explorer claims a miner mined a transaction that never touched the chain.
- 2026-07-27 — **Historical graphs above the rig monitor's process table** — `client/view/RigHistory.java`. One chart per tab (CPU, MEMORY) or two where the metric has a direction (DISK read/written, NETWORK rcvd/sent). ⚠ Three things worth not rediscovering. **Sampled on the table's own five-second beat** via `ProcessTableView.setOnSample`, never a clock of its own — two timers started a moment apart put one spike in two places, and a chart that disagrees with the table under it is worse than no chart, because the player is being asked to compare them. **Rates, not totals**: the engine's byte counters are monotonic by design, so charting them draws a line that rises forever; consecutive samples are differenced instead. The **first sample charts zero** rather than the whole accumulated total, which would otherwise pin the ceiling on open and flatten everything after. **Fixed ceilings, not auto-scale** — an auto-scaling chart redraws its own axis as the data moves, so a flat line and a spike look identical, destroying the only thing the chart is for. Answers a question a snapshot cannot: a parasite that has drawn cycles for an hour looks exactly like a tool started a moment ago, until you can see the shape of the last two minutes (`04` §3.1).

- 2026-07-27 — **A mempool, a fee market, 124 pre-existing blocks, and the server's chain-selection rule** — recorded in `04` §1.3b/§1.3c/§1.4, `glossary.md`. Five parts. (1) **The chain opens at height 124 and all 124 blocks are fully inspectable**, because *nothing about a block is stored* — hash, parent, nonce, miner, size and the entire transaction body are derived from the height through one digest, so any height renders identically for no bytes on disk and the save does not grow as the chain does. Only which heights the player *won* is stored, bounded, over the ledger's authoritative record. (2) **A mempool with three fee tiers**: a block holds 200, the derived NPC queue is usually deeper, and a slot is won on fee rate. ⚠ Fees are **deliberately not a sink** — 0.30 EC against 40 EC/hr — because they exist to order a queue, not drain a balance; `feesAreNotASink` fails the build past 1% of an hour's income, at which point `03` §4 has to know. (3) **A spend debits now and confirms later**, which is the one place this declines to be faithful: withholding goods for fourteen minutes would be accurate and would make buying a consumable mid-breach impossible. The fee is charged **on top** and earns its own ledger row. (4) **Blocks carry real bodies** — the player's own transactions plus derived NPC traffic, sorted by fee rate because that is how a miner packed them, with the player's rows marked. (5) **The server's genesis-or-join and longest-chain rules are implemented and tested** (`ChainSelection`): most **work**, never most blocks; a foreign genesis is never adopted however heavy; ties go to the incumbent. ⚠ Three traps found by probing rather than by reading. The mempool shipped `lowFeeRate` in minor units and `highFeeRate` as a gas price, making an under-4× spread read as 180× — everything comparable is a **gas price** now. NPC fees ranged to twice the priority rate, so the top tier a player could pay bought nothing; capped at priority. And the NPC queue drifts with **height, not the wall clock**, or a player could wait out an expensive queue with the game paused. Open: **MN-2**.

- 2026-07-27 — **The Ledger gets a confirmation countdown, and block times stop being a metronome** — recorded in `04` §1.3b (two new subsections). Reverses a standing rule and fixes three bugs a green build had been passing.

  **(1) The "there must never be a countdown" rule is withdrawn, and its reasoning is kept.** `ChainMempool` published a mean interval and no instant, because blocks are memoryless and a countdown claims that waiting brings you closer. Correct — and it produced three cards reading `~14m / ~28m / ~42m` that never moved, which reads as a broken panel, not a principled one. The same complaint had already been filed against the block ages and answered by making them tick. An ETA is published now, with four properties holding the honesty: it is **anchored** (`lastBlockAt + n × 14 min`, no accumulator behind it — anchoring on *now* recomputes the same fourteen minutes every second and freezes the countdown, which is the original bug from the other side); it is **allowed to be overtaken**, saying *"running long — longer than 79% of waits"* from the exact Erlang CDF rather than ever saying *overdue*, because an exponential wait exceeds its mean ~37% of the time; it **never publishes the draw**, though `ChainState.networkWorkTarget` really does know when the next block lands, because publishing it would make being overdue observable and delete the lesson (`doesNotPublishTheDraw` asserts the published figure *disagrees* with the true remaining work); and the **mean is still printed beside it**, since a countdown with no stated average is a deadline.

  **(2) Block times jitter.** The strip back-dated every card at exactly the target interval — `14.0 14.0 14.0 14.0`, twenty-four times. The old defence (a random-looking past would be "inventing a past the chain never had") lost to the fact that a perfectly even past is also invented and more obviously false. Each height is displaced ±3 min, derived, so gaps land in **8–20 minutes**; measured over 815 heights at min 8.25 / max 19.75 / **mean 14.0002**. Jitter is applied to a block's *position*, not to the interval, which keeps it O(1) (`body()` calls it ~4800 times per strip), monotone by construction, and mean-preserving by telescoping. ⚠ The **tip is not displaced** — its timestamp is `lastBlockAt`, a real measurement the mempool panel's "last one 3m ago" also reads. ⚠ The band is **narrower than the live process** on purpose: live intervals are exponential, measured 0 → 95 min with a median of 10.3 and only 34% inside 8–19. The strip is a derivation of a history nobody watched; the ETA and its percentile are the honest readout of the real distribution, and if the two are asked to agree the strip is the one that is wrong.

  ⚠ **Three bugs were found by rendering the panel, not by reading it or by running the suite** — which was green throughout. (1) **The projection and the confirmation rule had drifted into two implementations of "how many slots does the player get"**: the explorer computed `slots − backlog` with no floor and reported zero, while `MempoolRules.slotsFor` gave at least one. On screen that was a 0.30 EC *priority* transaction whose card promised **block +3, ~41:59** and which would confirm in the very next block — the explorer disagreeing with the engine about the player's own money, which is precisely the discrepancy `04` §3.1 teaches players to read as an intruder. One rule now, `slotsAgainst`, called from both. (2) The projection **added** the player's contested slot on top of a full block instead of displacing a network transaction, so a full block rendered 201 transactions against a 200 limit. (3) `humanSeconds` answers `"never"` at or below zero — right for an infinite wait, nonsense for an elapsed one — so a block found this second printed **"last one never ago"**.

- 2026-07-27 — **Window size, UI scale and full screen become settings** — `client/ui/WindowSize.java`, `client/ui/UiScale.java`, `Views.windowSection`. The deck is one undecorated Stage (§0), so there is **no OS resize handle**; the window was created at a hard-coded 1280×800 on every launch and the only other size reachable was maximised. Drawing our own chrome bought the look and quietly took away a thing every other window on the machine can do — this is the half of that trade that had not been paid. Six presets from 1280×800 to 3840×2160, eight scale factors from 80% to 200%, and full screen **off by default**.

  ⚠ **Scaling is a `Scale` transform on the content, not a font size.** The usual JavaFX approach is a root `-fx-font-size` with every dimension in `em`; that is unavailable here because `theme.css` is a contract written in pixels and `UiTokens` owns every size in Java pixels. A transform costs none of that — the character-cell layouts the design language depends on stay exactly proportional, and JavaFX picking accounts for transforms so clicks land where they are drawn. The content is laid out at `scene / factor` and is **unmanaged**, because a managed child of a Pane is repositioned by that pane's `layoutChildren` (the documented trap in `CLAUDE.md`) and would cancel the scale on the next resize.

  ⚠ **Three couplings that are easy to get wrong.** (1) **Scale divides the room, so it multiplies what the window must be**: at 150% a 1280px window gives the deck 853 logical pixels, under the 860 floor. The size list is rebuilt whenever the scale changes and names what it dropped, and the Stage minimum is `floor × factor` — the same rule from the other side, which has to agree or Settings offers a size the Stage refuses. (2) **The scale list is filtered by the display**, not just the preset list: on a 1080p panel 200% needs a 1720×1120 window that does not exist, and every preset then fails too — which is how the size control first ended up offering 1280×800 at a scale where 1280×800 is unusable. (3) **1080p runs out of *height* first**: 1920/2 = 960 clears the 860 width floor while 1080/2 = 540 misses the 560 height floor, so a check that looked only at width would offer it.

  ⚠ **Full screen had to disable JavaFX's own Escape handling.** The built-in full-screen exit key is ESCAPE and it *consumes* the event — Escape is this client's pause menu, so the default meant a player in full screen pressed Escape expecting to pause, dropped out of full screen instead, and the deck's scene filter never saw the key. `setFullScreenExitKeyCombination(NO_MATCH)` plus an empty exit hint, both set before the Stage is ever shown, since neither can be changed later without a frame where the default applies. Off by default because full screen on macOS moves the window to its own Space and hides the menu bar, and a client that did that uninvited on first launch would have taken over the display before the player had seen it once.

- 2026-07-27 — **Blocks pay their transaction fees to whoever mines them** — recorded in `03` §1/§1.1/§1.2, `04` §1.3a/§1.3b. A block now pays `subsidy + fees`: 160 EC plus an average **16.88 EC** (range 1.21–35.08), worth **+10.55%**. Until now the fees players paid into the mempool were debited and then ceased to exist — the fee market was a pure sink, and the explorer's block card had been printing a `fees` total that named money nobody ever received.

  **Two decisions were taken deliberately rather than by default.** (1) **The income anchor was allowed to move.** `Balance.chainNetworkHashrate()` could have been re-solved to absorb the fees, exactly as it was when the block interval changed, leaving `03` §1 untouched — that was the alternative and it was rejected, because the point of paying fees out is that income reflects them. Solo self-mining now runs **+12.80%** over the 0.40 EC/cycle-hour anchor. ⚠ **The re-check `CLAUDE.md` requires found the ordering of income sources unchanged**, which is the structural property `03` §5 depends on: active hacking (70) still leads, deployed networks (55–65) still sit second, self-mining is still last at 39.4–45.1, and faucet rule 1 holds with 25 EC of headroom. What narrowed is the gap — self-mining at its best is 64% of active income where it was 57%. (2) **PPLNS passes fees on and classic PPS does not**, which is what those products actually are: PPLNS divides blocks the pool really won, while pay-per-share sells a fixed price per share payable whether or not a block was found, and a fixed price cannot depend on a block that may not exist (the ones that do are called **PPS+**). That gives the roster a genuine third axis — income, steadiness, **fee exposure** — and splits the EC/hr column into two clusters 11.7% apart.

  ⚠ **The fee total moved out of `ChainExplorer` and into `MempoolRules`.** It decides a payout now, and the explorer's charter is that nothing there decides anything — a presentation class the economy is computed from would be a second, invisible economy. The transaction count and the per-transaction fee moved with it, so the block card and the ledger row come from one function and cannot disagree. ⚠ The expectation is **derived from the two distributions** (`105.5 × 16`) rather than measured and pasted, so a change to the fee ladder or the block limit cannot leave the published income figure quietly describing the old economy; asserted against 20 000 simulated blocks. ⚠ A block's fee total stays the derived one even when the player's own rows are in it — those displace network traffic rather than adding to it, so the figure does not change with who is looking, and the displacement gain is bounded by a fee they had to pay to get in.

  ⚠ **Five tests encoded the old contract and were rewritten, not relaxed**: `onlyTheFeeMovesIncome` became `onlyTheFeeAndSchemeMoveIncome` (the scheme is an income axis now, by decision), with `poolSizeStillDoesNotMoveIncome` split out to pin the half of the identity that had to survive — **pool size is still a variance knob and never an income one**, or the roster becomes a ladder.

- 2026-07-27 — **STORAGE becomes a slot grid** — recorded in `../client/06-resource-and-inventory-ui.md` §5.1a and §5.2. Each mount draws as filled cells for items and dashed cells for the rest of its capacity, with `n / cap` per mount and an `AT RISK NOW` line above. Asked for as "the vault from Marathon"; the visual half of that was declined (see below) and the structural half was already specified.

  ⚠ **This looks like it contradicts `../client/06` §7.2 — "the table is the default; the grid is the option" — and does not.** That rule protects **three-way cost comparison**: the inventory sorts on EC, compute and noise at once, and a card grid puts every value at a different x-coordinate. The storage window has **no cost columns at all** — it is sorted by exposure and compares nothing — so there is nothing there for the alignment rule to protect. Rows stay one chip away.

  What the grid buys is the thing a list structurally cannot show: **capacity**. `01` §6 makes storage a strict capacity/exposure trade and **I12** makes vault capacity the scarce half, but six items in a six-slot vault and six in a sixty-slot zone render identically as a list. ⚠ `STARTING_VAULT_CAPACITY` had been declared since the day storage was written and **read by nothing** — the grid is what finally made it visible. Enforcement is still absent and drawn honestly as over-capacity (`8 / 6`) rather than clamped; it is a *rules* change and belongs with the Cold Storage Expansion schematic, since a hard cap of 6 with no way to raise it is a different game from the one §6 describes. ⚠ The `est. EC to replace` figure §5.2 permits is **omitted entirely** — the client is not told an item's gate or price, and a fabricated total on the one screen whose job is to say what a raid would cost is worse than none.

- 2026-07-27 — **A Cyberdeck palette, and §9's bezel ban amended** — recorded in `ui-design-language.md` §1, §9 and the new §9.2. Two halves of one request, and only one of them needed a contract change.

  **The palette needed none.** Themes are the sanctioned place to vary colour — "every theme is the same deck with a different palette" — so `theme-cyberdeck.css` is rain-lit teal glass under a sodium accent and touches no component rule. ⚠ The accent stays **single and keeps its meaning**: the obvious cyberpunk move is hot magenta for one thing and cyan for another, which is exactly the "second accent hue, or a semantic color system" §9 rejects, so the sodium orange means what amber means on every other skin — money you have, and live work.

  **The casing needed §9 reopened.** Bezel was cut in §9 and *pointedly kept cut* in §9.1 when four other artefacts were permitted. Five styles now ship, off by default, under §9.1's identical four conditions. ⚠ The condition that shaped the implementation is that it may not cost legibility, and it is **structural rather than maintained by hand**: the frame is drawn in a **margin** and the deck is inset by exactly that margin, so no content is ever underneath it — a casing over the top strip would hide the compute readout, which is pillar **C2**. `BezelStyle.margin()` is both the inset and the drawing width, and a test fails the build on a style without one. ⚠ **Vignette stays cut and this does not reopen it**: the objection to a vignette was never about frames but that it "dims real content by position rather than by meaning", and a casing in a margin dims nothing. Corner brackets are left **open in the middle** on purpose — a frame closing on all four sides is what §9 objected to, because it puts the interface inside a picture of a device.

- 2026-07-27 — **A pool payout names its pool, and stops claiming to be a coinbase** — `ChainExplorer`, `ChainTransaction.counterpartyLabel`. The ledger's `From` column showed `0x8f3c…a219` for the row a player most needs to recognise. It now shows `THE COMMONS`, carried **beside** the address rather than replacing it, so `04` §3.1's audit still works.

  ⚠ **Finding the label found a real bug underneath it.** Solo and pooled payouts both credit `SELF_MINING`, so keying "is this minted?" off the entry *type* marked **every pooled payout as a coinbase** — rendered from the zero address, discarding the pool address the engine had carefully stamped on it, and contradicting `LedgerEntryState`'s own rule that a pool payout is not a block reward because the pool paid it out of its own balance. A minted entry has no sender and a pool payout has one; that is the test now. `MempoolTest.coinbaseHasNoSender` had been passing on a **pooled** rig, which is exactly how the bug survived. ⚠ A label is only ever attached to an address the client can **verify** — pool addresses are derived from public ids, so matching one is a fact; everything else gets none, because a name rendered where an address belongs is how a transfer from a stranger gets mistaken for a payout.

- 2026-07-27 — **Items drag between storage mounts, and the top strip wraps** — `Views.storage`, `client/ui/widgets/WrapStrip.java`, `../client/06` §5.1a.

  **Drag.** The whole mount is the drop target rather than each cell, and it highlights only for a mount the item is not already in — accepting a transfer mode gives the drop cursor, so accepting a move that will not happen tells the player it will. The drop reports the **engine's** answer to `setDropCompleted`, not "a drop happened", so a refused move (a full mount, once capacity is enforced) ends the gesture as a failure instead of animating home while the message says otherwise. ⚠ Three JavaFX traps, each of which cost a round: a `DataFormat` is a **process-wide singleton** and constructing one twice throws, so it is looked up first; a drag still delivers `MOUSE_CLICKED` to its source on release, so the click handler needs `isStillSincePress()` or every successful drag also left a stale selection; and the drop **rebuilds the panel the gesture is running on**, so the refresh is deferred with `runLater` or the source is detached before `DRAG_DONE` is delivered.

  **The top strip wraps.** It was an `HBox`, which squeezes and then clips rather than wrapping — at 200% UI scale in a 1280px window the deck is 640 logical pixels wide and most of the strip was simply gone. A `FlowPane` wraps but has no growing child, so the flex spacer that right-aligns the balance and clock (§3) would be lost at the width nearly everyone plays at. `WrapStrip` does the HBox thing while the content fits and the FlowPane thing when it does not; **at any width where the strip fitted before, the layout is what the HBox produced**. Measured: 57px tall at 1280, 113px at 980 and below. ⚠ The window controls are **pinned** out of the flow — they are the only way to minimise, maximise or close an undecorated Stage, and a control that migrates to the second row as the window narrows is the one control that must not. ⚠ Found by rendering: `setSpacer` also added the node to `getChildren()` while the caller added it positionally, which is `IllegalArgumentException: duplicate children added`.

- 2026-07-27 — **The resolution is the viewport's, and the casing became a machine** — recorded in `ui-design-language.md` §9.2. Settings → Window now sets the **screen inside the machine**, not the OS window: choosing 1920 × 1080 gives the deck 1920 × 1080 and the casing is added beyond it, so the window is `(resolution + 2 × casing) × scale`. Measured: a 1280 × 800 viewport with the 26px casing produces a 1332 × 852 window. Before this the casing was subtracted *from* the resolution, so a 20px casing turned a 1920-wide choice into an 1880-wide deck and the number in Settings described something the player never got.

  ⚠ **This decoupled UI scale from the viewport, which is a real simplification rather than a side effect.** The window used to be sized *to* the resolution with the deck laid out at `physical / scale`, so 1280 × 800 at 150% left the deck 853 logical pixels — under §3's 860 floor — and Settings had to hide size/scale combinations that did not hold. Now the deck always gets the full resolution in layout units and the scale only changes how large those pixels are drawn. `WindowSize.usableAt` and `fitsWithin` collapsed into one `fitsOnScreen`, and the only remaining constraint is whether the window fits the display.

  **The casing styles are machinery now**: `Casing` carries vent slots, corner fixings, a port block down one flank and a stamped designator plate; a new `Cable loom` dresses three cable runs at different inset lanes with right-angle bends, junction clamps and terminators. Vents and ports are the **void showing through** the band rather than marks painted on it, which is what makes them read as holes in a panel. ⚠ The trim is **asymmetric on purpose** — real equipment has a front, and identical trim on all four sides reads as a picture frame, which is what §9 objected to about bezels in the first place. ⚠ The junction clamps are the one place the casing takes the accent, and it keeps §2.1's meaning: a live connector is the only part of a shell that is actually powered.

  ⚠ **A flaky test was introduced and caught within the same session.** `MempoolTest.coinbaseHasNoSender` was switched to SOLO (correctly — it had been asserting on a *pooled* payout, which is the bug it now guards). But a solo rig at 90 cycles expects a block every ~4.3 hours against an exponential wait, so the fixed five-hour run it inherited found nothing about 30% of the time. It mines **until** a block is won now, with an early break and a 50-hour bound. It cannot be made exact: the RNG seed is derived per character, which is itself the fix that stopped every save generating an identical world.

- 2026-07-27 — **Four extreme casing styles, and §9.2's motion condition amended** — `ui-design-language.md` §9.2. `Gothic plate` (riveted plate, corner buttresses, hazard chevrons), `Terminal panel` (blinking status lamps, toggle switches, a grille), `Chrome 3.1` (raised bevel, title bar, drawn control boxes) and `Motif` (double bevel, corner grips, square buttons). Ten styles total, still off by default.

  ⚠ **§9.2's third condition was written as "nothing here moves" and that is no longer true.** `Terminal panel`'s lamps blink. The condition has been corrected to §9.1's actual rule — *what moves obeys §5* — and the lamps run on `Pulse.animate`, the decorative channel that `prefers-reduced-motion` freezes. ⚠ Frozen **lit**, not dark: a panel whose indicators all went out reads as powered off, which is a wrong statement about the machine where a still lamp is only a less lively one. The ticker is stopped in `Bezel.dispose()`, wired from `DeckShell.dispose` beside the CRT layer's.

  ⚠ **Two styles imitate window chrome, which §9 bans outright.** That ban protects §0's premise that the player never sees their own operating system — and a thirty-year-old window manager is nobody's operating system, so `Chrome 3.1` and `Motif` read as a retro machine rather than as the host showing through. Their bevels are legal on their own terms: §2.1 says depth comes from **brightness, never shadow**, and a bevel is a light edge against a dark one. No blur, no drop shadow, both still banned and machine-checked.

  ⚠ **`Gothic plate` is genre, not iconography** — rivets, plate and chevrons, deliberately none of the protected emblems the obvious reference is known for. ⚠ Control-box glyphs are **drawn as shapes, never as text**, so no character the bundled fonts might not carry can reach the casing (`GlyphCoverageTest`).

- 2026-07-27 — **There were two network windows; `map` is removed** — recorded in `../client/05-tool-windows-and-layout.md` §2.5. Reported as *"the networking tool was functional a few commits back, it seems to have been reverted"*, and the same for breaching. **Nothing had been reverted and neither tool was broken.**

  The evidence, in order. Neither commit in the window touched a single net or breach file (`git show --stat`). All nineteen tool windows built and laid out. On a fresh character a sweep discovered 5 of 5 machines and a breach opened with a snapshot and two actions. On the reporter's own save — copied, never mutated — the BASE sweep chip's exact call path returned status 0 and took `knownNodes` from **0 → 5** and `breachTargets` from **0 → 5**.

  ⚠ **The actual fault was two windows about the network, and the reachable one was inert.** `map` ("Network map", `Shortcut+2`) held a read-only table with **no sweep control**, so it was permanently empty for anyone who had not swept elsewhere — and it carried a note reading *"Breach targeting is not built"*, stale since the breach window shipped. `netmap` ("Network", `N`) was the real tool. A player pressing the digit landed on an empty table that told them the feature did not exist.

  ⚠ **Breach looked broken for the same reason, one step removed**: breach targets are swept hosts, so an unswept map means an empty target list. Two tools appearing to fail together had one cause.

  `map` is deleted and `netmap` **inherits its `Shortcut+2`**, so the habit that caused the confusion now lands on the tool that works and the digit row stays contiguous. Nothing was lost with it — `netmap` has had a LIST view on a chip beside GRAPH and FOLDERS the whole time, which is the "table you sort" the split was justified by. `WindowCatalogueTest.onlyOneNetworkTool` fails the build if a second network window ever appears.

  ⚠ **Worth keeping as a rule: `scan` is not `sweep`.** `scan` audits your own rig for parasites; `sweep` probes a network you do not own. `SoloGame` §724 says so in a comment and the log lines differ, but a player who runs `scan` and watches the map stay empty has been told nothing useful. An empty-state hint on both windows is the obvious follow-up and is **not** built.

- 2026-07-27 — **Five interface refinements, and a CSS parse error that had been silently killing half the stylesheet** — `ui-design-language.md` §2.1a.

  **(1) The deck fades up instead of flashing.** The flash was the Scene's default fill — **white** — showing for the frame between the Stage taking a new Scene and CSS resolving on it. The scaler's holder now paints `-es-void` (`.es-scene-ground`), so the colour stays in the stylesheet where §10 criterion 2 requires it, and `Motion.fadeIn` brings the deck up over 900ms. ⚠ **`Interpolator.DISCRETE`, nine steps** — a `FadeTransition` is a linear tween and `UiContractTest` fails the build on `LINEAR` outside the sweep bar. Stepping is also the truer effect: a tube coming up to brightness rises through visible levels.

  **(2) The balance counts, and the movement flashes beside it.** Green for a credit, `alarm` for a debit, on the stepped `Pulse` driver. ⚠ The first call **seeds** rather than animating — opening a 4 000 EC save would otherwise count from zero and announce four thousand ethecoin of income that never happened. ⚠ The step is recomputed from the **remaining** distance each frame so a second payout landing mid-count is absorbed; mining pays in bursts, so that is not a rare case. ⚠ Under reduced motion the value snaps and the delta is **held rather than dropped** — which way the money went is information, not decoration.

  **(3) The heat thermometer is horizontal, under its label.** A tall widget in a strip of wide short cells forced the whole strip taller for one readout. It is now label-over-meter, which is the anatomy every other cell already has. ⚠ Cold stays at the origin, so the bulb is on the **left**; a scale filling right-to-left would be read backwards by everyone.

  **(4) Network nodes carry a lock.** `[/]` breached (green), `[!]` patched — locked out (warn), `[#]` never breached. ⚠ ASCII, because §9 bans icon fonts and `GlyphCoverageTest` fails on any literal outside the bundled faces, which have no padlock. ⚠ The lock answers a **different question** from the ink-level marker beside it: the ink says how much is *known*, the lock says whether the way in is *open*. Those came apart the moment a host could be breached and then patched — such a host is richly known and shut, and overloading the ink level would have made "patched" read as "less well known". ⚠ **Nothing sets `patched` true yet** — no rule patches a host. The field, its wire type and its rendering exist so the state has one meaning the day a mechanic lands; **the mechanic itself is unproposed and needs designing.**

  **(5) The clock shows SESSION and LOCAL, with UPTIME and SERVER in a tooltip.** ⚠ UTC is labelled **SERVER** on purpose: a federated home server is authoritative for when things happened (I14) and every timestamp on the wire is UTC, so the clock a player checks a server log against has to be the same one the log is written in. "UTC" alone would be true and would not say why anyone should care.

  ⚠⚠ **The real find: a CSS syntax error silently disables every rule after it, and JavaFX only whispers it to the log.** A comment inserted mid-selector produced `Expected LBRACE at [1216,0]`, and **everything past line 1216 of `theme.css` stopped applying** — the thermometer among it, which is how it was caught (`bg=null` on nodes with correct classes and bounds). The cause was a replacement matching `.es-netmap-foothold` when the real rule was the **descendant** selector `.es-netmap .es-netmap-foothold`. Two lessons worth keeping: a stylesheet edit needs `CssParser` warnings checked, not just a green build; and a bare class selector in that block is also *weaker* than its neighbours and loses to them even when it parses.

- 2026-07-27 — **BREACH on a node clears a finished attempt instead of reopening it** — `BreachView`, `BreachArming.rearm`. Reported as the breach window showing the previous breach after an abandon or a success.

  Two faults, and they compounded. **(1)** A resolved breach deliberately stays on the save until it is dismissed, so an outcome slate survives closing the window — but `open = session.breach().isPresent()` is true for a resolved one, which hides *both* the target list and the launch panel. Arming a node from the map therefore raised the window onto the previous attempt's obituary with no control on screen but Dismiss. **(2)** `BreachArming.arm` no-ops on an unchanged id, so pressing BREACH twice on the same node was inaudible to the panel — which is why the report says "regardless if it is the same node or not".

  The fix is `rearm` for the map's control, which always notifies, plus a clear in the panel's refresh.

  ⚠ **RESOLVED ONLY, and this must stay that way.** A live attempt holds reserved compute that aborting does not refund (`05` §4), so clearing one because the player brushed a node on the map would spend their cycles for them. The check is on `resolved()`, never on `isPresent()`. Verified both ways: after an abort the slate clears on arming, and a live breach survives it.

  ⚠ It cannot loop — `dismissBreach` fires `onChange`, which re-enters the refresh, and by then `session.breach()` is empty so the branch is not taken again.

- 2026-07-27 — **Offset ciphers can arrive part-solved, and abandoning a breach costs noise** — `05` §3.1's cipher is `[PROPOSAL]`, so both are changes to a proposal rather than to settled design.

  **Part-solved boards.** A cipher board has a **55%** chance of arriving with 1–3 columns already correct, and a **12%** chance of a further 1–2 on top. ⚠ Capped at **a third of the board**, which is the part that matters: without it a 6-byte tier-1 board could arrive with 5 of its 6 columns done, which is not a shorter puzzle but an absent one. A third scales the relief with the thing it relieves, so the full give only ever lands on the long boards — 2 columns at tier 1, 5 at tier 5. Measured over 4 000 boards per tier: ~45% get nothing, and the 4–5 cases are 2% and 0.4%.

  ⚠ **Given columns are LOCKED, not merely pre-typed.** A given cell the player could overwrite is a trap dressed as a favour — a stray keystroke on a correct column is only discovered by the commit that costs a strike. Locking is also what makes the give worth more than the keystrokes it saves: a given column does not need *checking*, which is most of the tedium. It renders dimmer than the player's own answers and says "came already solved and is locked" to a screen reader (§4.4's second channel).

  ⚠ **It does not touch I7.** Proof-of-skill gates are tier-gated, never count-gated; a part-solved board is the same tier it always was. What changed is how long a layer takes, not what clearing one proves.

  ⚠ **The generator draws a constant number of values whatever it rolls.** `Rng`'s contract is that consumption must not depend on what was produced — it is why `nextInt` has no rejection loop. The obvious `if (roll < chance) { draw more }` breaks it and would desynchronise every later draw in the breach. All five decisions are drawn every time and only then read; surplus cell picks are drawn and discarded.

  **Abandonment radiates.** Aborting a live breach now leaves **30 cycles of noise for a drawn 5–20 seconds**, making the rig briefly easier to find. Until now abandoning was the *quietest* possible exit — the breach's noise simply stopped — which made "open a breach, read the board, leave if it looks ugly" a free reroll on difficulty. It stays a sanctioned outcome: nothing is taken, and the penalty is a window the player can play around.

  ⚠ **30 keeps the documented ordering intact**: above `BREACH_NOISE_CEILING` (26), so the exit is louder than the attempt was, and below `NET_SWEEP_BASE_NOISE` (35), so "the cheapest sweep is still louder than anything a breach can do" survives. ⚠ **A miner crack never spikes** — Invariant **I9**, the same reason `resolve` zeroes a crack's heat: it is the player's own rig, and a spike there would punish backing out of a fight on their own hardware, which is the tutorial breach (`04` §5.1). ⚠ The window is stored as an **instant, not a countdown**, so it settles correctly across a quit instead of waiting to be served on the next launch.

## 4. How to use this doc

- Before starting design work on any system, check here for its open questions.
- When you make a decision that closes one, **move the decision into the system doc** (that's the source of truth) and log it in §3 — don't leave the answer only here.
- Proposal questions (§2) are provisional; treat the proposal docs' [PROPOSAL] tags as the signal that nothing there is load-bearing yet.

- 2026-07-27 — **The network sweep was unreachable on an existing character, unreadable as a control, and inaudible on the meter — plus filing, and a visible selection** — recorded in `07-recon-tools.md` §5, `solo/net/FolderRules`, `solo/rules/NoiseRules` and `client/ui/theme.css`.

  **The bug that started it: `sweep` could not be run at all, and said the wrong thing about why.** `SoloSave.topology` is null on a save written before the world generator existed, and the field was documented as deliberately left that way so an old character would "keep working with an empty map rather than being handed a freshly rolled world on load". That reasoning is right about regeneration and wrong about the outcome — a null topology is not a small world, it is *no* world. `NetRules.view` returns an empty map forever, `beginSweep` returns empty at every tier, and the refusal that reached the player read **"not enough available compute"** on a rig with ninety free cycles. Reproduced on a real save. `SoloGame.open` now backfills the world from the save's own persisted seed (the generator's idempotence guard is what makes that a repair and not a reroll), and the compute refusal now quotes both numbers so it cannot be the wrong sentence again. `SaveBackfillTest` covers all three.

  **Running a sweep costs no ethecoin, and the docs now say so where somebody would look.** It never did charge — there is no `LedgerRules` call on the path — but the property was nowhere stated, which is how it gets added by accident. §5.1 gives the argument: discovery is upstream of every ethecoin faucet in the game, so a per-run charge is a spiral with no floor for a player who is broke.

  **Noise was split from compute, because identifying them was measurably wrong.** The sweep's cycle count *was* its noise, via the `CONTROL_CHANNEL` reservation — elegant, one source of truth, and it made a base sweep move a 100-cycle rig's meter by two percent while getting **quieter the bigger the rig grew**. A task now declares its own loudness (`TaskState.noiseCycles`; 35 / 55 / 80 for the ladder), and `NoiseRules` sums declared loudness plus held outward cycles. It is strictly present-tense: a task is counted only while `now` is inside its window, so a finished sweep contributes nothing — noise is a **rate**, and what a loud act leaves behind is heat, which is a different field with different rules. ⚠ **The whole calculation moved out of `client/view/RigStatus`**, which had been enforcing I4, I6 and I9 from inside a view class and gave a home server no way to disagree; `GameSession.noise()` is the seam now.

  **The three sweep controls were reported as "greyed out and cannot be pressed". They were always pressable.** They carried `es-netmap-control` — the *view toggle's* class, which paints its inactive state in `-es-dim-1`. An action has no inactive state, so all three sat permanently in the exact grey this panel uses to mean "not the one in force", in a row beside two toggles that did brighten. New `es-netmap-action` class with the ordinary fill plus hover and pressed states, and each control now names its price (`BASE 2C`) the way `sweep -n` already did. **A control whose only visual state is a colour the panel uses for "unavailable" is unavailable**, whatever the code does.

  **Filing** (§5.4) is new state and a new window mode: a folder tree over discovered machines, mechanically inert, with `folders` / `mkdir` / `rmdir` / `mvdir` / `file` in the terminal for pillar C1. Two decisions worth keeping: filing an undiscovered address is refused **in the same words** as filing a fictional one, because two distinguishable refusals are a free enumeration oracle for the thing the whole sweep ladder is sold on; and `rmdir` is **never recursive** — filing carries no risk lesson, so a mis-click costs a flattened level and nothing else.

  **The graph now marks the machine the player has picked**, in three signals so none of them is load-bearing alone: a double frame (`╔═╗` against the ordinary `┌─┐`), a `▌` gutter bar at its address, and a brighter fill. ⚠ **Not `→`** — that is already this map's arrowhead at the head of every forward edge, so a selection drawn with one is invisible among the nine a two-hop map already has. **The vantage outranks selection for frame weight**: where the player is standing is a fact about the whole map, since every hop count on it is measured from there, so the bar carries selection unconditionally and the double frame only where a weight is going spare. Both marks are one-for-one character swaps, so a selection cannot shear the column it is in — asserted, not assumed.

- 2026-07-27 — **Thermal recovery bounded, theft made felt-before-known, and the breach given an aim/fire split** — recorded in `01-core-resources.md` §1.3 and the new §1.5, `solo/rules/ThermalRules`, `solo/rules/ComputeRules`, and `client/view/BreachArming`.

  **The recovery curve had no ceiling, and the tail was the bug.** `cycles ÷ (0.5 × (1 − load)² × budget)` approaches infinity as the rig fills, and got there fast enough to matter: a 35-cycle Thorough Scan at 90% load took **36 minutes**, two cycles at 82% took a hundred seconds. Over-committing should be felt, not benched. The curve now states its ceiling first and derives the time as a fraction of it — **5 minutes clean, 10 minutes on a rig being robbed** — as an *asymptote rather than a clip*, so load keeps reading all the way up instead of flattening into a plateau where 80% and 95% feel the same. The size term is a square root so a two-cycle sweep still costs something and the ceiling is reachable in play rather than only in theory. The *shape* §1.3 committed to is unchanged.

  **Stolen cycles now have three consequences, all of which land before the player knows.** Less capacity (as before), **every task's duration × `1 + theft_share`** (baked in at commission, so it is true offline), and a **slower recovery with a higher ceiling** — the second one deliberately separate from the load a parasite already causes, so a player who has released every allocation they own and still sees a slow rig has been handed the discrepancy without being told anything.

  ⚠ **What must not happen is the fourth thing, and it was happening.** An unaudited parasite was published in the compute ledger from the moment it was planted — the rig monitor said `Foreign miner 6C`, in blinking alarm red, with a note naming the scan that would find it, to a player who had run no scan. That gives away for free the entire product `04-mining.md` §3.2 sells the audit ladder for, and it made `04` §3.1's "notice the numbers do not add up" unreachable because nothing ever failed to add up. Now: an undiscovered miner is **omitted from the snapshot**, so `claimed + recovering + free` comes up short of the ceiling and the gap is drawn as dark, unlabelled, un-legended cells. The loud note is **deleted**. The one thing the game says is the refusal — *"command could not be executed: not enough cycles to compute — N needed, M free of T"* — which names a shortfall and never a cause.

  **`ScanRules.roll` had never been called by anything but its own tests.** A scan reported a hard-coded stub that did not look at `rig.foreignMiners`, so no audit in the game could find the parasite the tutorial plants on every new rig. It is wired now, frozen at commission like every other roll, and `MinerState.discovered` is set at settlement — the only thing in the engine that sets it, and the moment the theft becomes attributable. ⚠ **An unaudited parasite is also no longer a breach target**, because two windows disagreeing about what the player knows is worse than either answer. The tutorial is now the pipeline `04` actually describes: notice, `scan --full` (not `--quick` — the tutorial miner is T1 and Quick sees T2+), then crack.

  **Aiming and firing are now two steps.** The breach list began an attempt the instant a row's button was pressed, which put an irreversible compute spend one mis-click away in a list that reflows whenever a sweep lands. A row is now *armed*; one **START BREACH** control commits. The network map got a **BREACH** control that arms the breach window at the selected machine and raises it, and the target list got **ordering** (tier / address / threat) plus a READY-ONLY filter — a dozen machines off one sweep is a list nobody reads top to bottom. An already-breached machine stays listed and un-attemptable, carrying the rules' own reason.

  ⚠ **One bug was reproduced doing exactly what the split exists to prevent**: raising the breach window from inside the map's click handler created the launch panel under a still-down pointer, and the release landed on START BREACH — one click on a map cell opened a breach nobody asked for. The control is dead for one JavaFX pulse after it appears: imperceptible to a person, unbridgeable by a single event.

  **Two things reported from play, both fixed.** The Logic board's carets were padded on one side only — three characters over a five-character box, centred by the VBox, so every arrow rendered one cell right of its own tumbler and *"the second arrow changed the left-most column"*. The handlers were correct throughout; the picture was lying about which control was which. And **a breach no longer survives a session**: it is abandoned on load as a recorded `aborted` resolution (deleting it outright would let a player escape a losing attempt by quitting, which is the reroll-by-reloading this engine refuses everywhere else), the slate is cleared, the log says what happened, and the desk no longer restores the breach window.

- 2026-07-27 — **The rig monitor became a process table, and the manual audit finally exists** — recorded in `04-mining.md` §6, `solo/proc/`, `protocol/game/RigProcess`.

  §3.1 has claimed since it was written that a hidden miner is findable by hand — "the discrepancy is always present in the data". Nothing implemented it. The rig monitor now has **five tabs** (Overview · CPU · MEMORY · DISK · NETWORK) listing the player's tools, their standing reservations, thirteen system daemons, and whatever else is running, with columns and sorting modelled on a real process monitor.

  **Five disguises**, chosen once when a parasite is planted and never re-rolled: a **tool twin** (copies a running tool's name), a **system mimic** (plausible daemon name under an account nothing else uses), a **typosquat** (`syspolicvd` beside the real `syspolicyd`), a **resource hog** (top of the column, nothing accounts for it) and a **stopped clock** (heavy CPU, no accumulated CPU time). Two more tells come free — a five-figure pid on something claiming to have booted, and traffic on something that should be local.

  ⚠ **Every tell is a relationship, never a marker.** There is no `rogue` field on `RigProcess`, no "suspicious" style class, and no column that scores anything — a renderer that painted the answer would turn the investigation into spot-the-red-row. `ProcessTableTest` asserts that a parasite's capability fields read identically to an honest user process's.

  ⚠ **The figures had to be made stable to make two of the disguises possible at all.** They are pure functions of `(identity, a four-second bucket, real rig state)` and never touch `Rng` — that generator is persisted, so decorating a readout from it would let opening a window change a breach board. `CPU TIME` is the deliberate exception: it accumulates in real time, which is the entire basis of the stopped-clock tell.

  **Right-click kills.** A tool ends where it stands and keeps what it managed — a truncation of the frozen answer, never a re-roll — while **its cycles still take the full thermal recovery**: stopping early buys back time, never capacity. A parasite dies and its buffer is forfeit (a crack takes a buffer; §5 prices four responses against each other and a paying kill would collapse three of them), and **it can be killed without an audit**, which is the payoff for reading the table. A system process is restart-only, and restarting takes down every tool that depended on it at the same price — `netd` carries sweeps, `auditd` carries scans.

- 2026-07-27 — **The process table given real dynamics, and re-based on FreeBSD** — recorded in `04-mining.md` §6.1/§6.1a, `solo/proc/Vitals`, `solo/proc/SystemProcesses`.

  **The figures move now.** Everything advances on a five-second tick, split into two kinds that were previously conflated: **gauges** (`%CPU`, threads, memory, idle wakeups) wander around a resting level on a smoothed walk — white noise reads as a slot machine rather than a computer — and **counters** (CPU time, bytes, packets) are **monotonic by construction**, `intervals × mean + partial(interval)` with `partial` bounded below `mean`. ⚠ A test caught the obvious version being wrong: CPU time derived from the *wandering* gauge falls the moment the gauge dips, so it accumulates at the process's **resting** share instead. Sorting by a moving column now re-orders rows the way a real monitor does, which is what makes a row pinned at the top of `%CPU` worth a second look.

  ⚠ **The table repaints on its own clock**, `Pulse.every(5s)`, not on the session's change signal — an idle rig fires no change at all, and a table that froze when the player stopped doing things would be stale exactly when they were reading it. It is instrumentation rather than animation, so it is a non-decorative subscription and still ticks under reduced motion. ⚠ A repaint declines to happen underneath an **open context menu**: a rebuilt row takes its menu with it, and without the guard a slow hand would land the kill on whatever row had moved into that position.

  **The daemon set is now FreeBSD's**, reversing the earlier call in that file. The premise was what was wrong: uOS is FreeBSD-flavoured, so inventing names was teaching nothing where it could have taught something true. Kernel threads are bracketed, pid 0 is the kernel and pid 1 is `init`, and the service accounts are real (`daemon`, `operator`, `nobody`, `unbound`, `_dhcp`, `ntpd`). ⚠ The fiction's own daemons are flagged `real = false` in the record rather than left to be assumed — nothing may quietly assert FreeBSD ships a `cyclesd`, and only the real rows may ever be cited by a curriculum entry. Kernel threads are modelled as kernel threads (no disk, no network, almost no resident memory), which is both true and load-bearing: a parasite claiming to be one would have to show zero traffic to fit in, and it cannot.

- 2026-07-27 — **START BREACH was inert and the target list would not switch targets — three faults, all mine, all from the same change** — fixed in `client/view/BreachView`, `client/view/BreachTargetList`, `client/ui/widgets/ProcessTableView`.

  **1. A guard disabled the control it was protecting.** The launch button is dead for one JavaFX pulse after a target is armed, so a click that raised the window from the network map cannot also land on it. The first version flipped that flag on a *hidden→visible transition of the panel* — and a freshly-constructed `VBox` is visible by default, so the very first refresh read "was showing: true", the transition never fired, and START BREACH was **permanently inert**. It now gates on being armed, which is the state it actually cares about, and always becomes live one pulse later. ⚠ The general lesson is worth more than the fix: **a guard that can silently disable its own control is worse than the mis-click it prevents**, so it must not depend on a transition it might miss.

  **2. Nothing was listening for an arming change.** The breach window subscribed to the session and to nothing else — but arming is *not* game state (it is an intention the player has not acted on), so it does not travel through the session. Picking a different row changed the armed id and repainted nothing: the launch panel kept naming the previous target and the highlight never moved. `BreachView` now holds `arming.onChange` as well, and **both** handles are released on detach — `BreachArming` lives for the whole client, so a listener left by a closed panel would call `refresh` against a detached scene graph forever, and every re-open would add another.

  **3. Most of a row was not clickable.** ⚠ A JavaFX `Region` is picked where its background *paints*, and `.es-row` paints one only on `:hover`. At rest the 8px padding and the gaps between a row's three lines were holes: a click on a word bubbled up and selected the row, a click two pixels below it went to the panel behind. That reads as a list that responds at random. `setPickOnBounds(true)` makes the whole rectangle the control, which is what a list row is — applied to the breach target rows and, pre-emptively, to the process table's, where the same click is a kill.

- 2026-07-27 — **A breach is heard now, and it can be answered** — recorded in `05-hacking-minigame.md`'s noise handling, `solo/rules/NoiseRules`, `solo/rules/IntrusionRules`, `Balance.BREACH_NOISE_*`.

  A breach accumulated in-puzzle noise from the day the engine shipped and converted it to heat at resolution, but **the meter read zero for the whole attempt** — being inside somebody else's machine registered as nothing at all, so sitting in a breach indefinitely was the quietest thing a player could do.

  **Live noise, quieter than a sweep and never silent.** A floor from the moment it opens, climbing with what the player does in there — a bypass adds twelve at a stroke, a tripped canary four. ⚠ The ceiling sits **below the cheapest sweep's figure**, and that ordering is a balance statement rather than a tuning accident: a sweep touches every machine within reach and announces itself to all of them, and a breach that could out-shout one would make the sweep ladder's price (2 cycles for the loudest act in the game) read as a mistake. A test asserts it.

  ⚠ **A crack is silent on every outcome** (Invariant I9) and so is a resolved breach — noise is a rate; what a loud attempt leaves behind is heat.

  **And it can bite back.** A resolved offensive breach rolls for a counter-hack with the chance scaled by the noise it made, anchored so that a breach at the reference figure carries exactly the risk a sweep of the same depth does — both read the same depth table, so re-tuning one cannot silently move the other. Depth zero never bites back, the same rule sweeps have. ⚠ Rolled at **resolution**, which is the opposite of a sweep's commission-time roll, and deliberately: a breach's noise does not exist yet when it opens — it is the sum of choices the player has not made — so rolling early would either ignore them or predict them. It fires on failure and abort too, because `05` §4.1's "the noise you made stays made" now has something behind it.

  The planting moved to `solo/rules/IntrusionRules` so the sweep and the breach share one implementation. Duplicating it would have been worse than the coupling: a parasite planted by one path and not dressed, or not charged heat, or not given an allocation, would be a second class of parasite nobody would notice until a player found one that could not be audited.

- 2026-07-27 — **Three breach-window UI faults reported from play** — fixed in `client/ui/widgets/AttentionMeter`, `client/ui/breach/TumblerRack`, `client/view/BreachView`.

  **1. The action chips oscillated under the pointer.** ⚠ A genuine feedback loop, not a rendering artefact: the attention meter's preview caption is empty at rest and reads `NEXT: FUZZER VOLLEY -6` on hover, which **widened the meter**, which narrowed the cost strip beside it, which reflowed the `FlowPane`, which moved the chip out from under the pointer — firing MOUSE_EXITED, clearing the caption, shrinking the meter and moving the chip back. The caption now reserves its width unconditionally, so the meter's size cannot depend on what the pointer is doing and the loop cannot start.

  **2. The tumbler took the keyboard.** Left and right move a cursor between positions, up and down cycle the symbol under it — the way a combination lock works. ⚠ Bound on the **rack**, not per cell: a per-cell handler only fires while that cell holds focus, so the player had to Tab into the right one first, and Tab skips every locked position — which breaks "left to right" the first time the Rainbow Table establishes a character in the middle. The cursor skips locked positions and re-homes when one is established under it.

  **3. The animation shares a row now.** The viewport is a fixed-width character texture, so a whole row to itself left a band of empty panel beside it and pushed everything a player reads further down. The gauges sit in that space; the viewport keeps its natural width and the gauges take the slack, because a character grid does not negotiate for space.

  ⚠ A fourth thing came out of it and is worth stating on its own: **`setPickOnBounds(true)` was missing on every clickable control in the breach window** — chips, tumblers, carets. A JavaFX `Region` is picked where it *paints*, and none of these paint a background at rest, so the hit area was the glyphs alone. On a five-cell-wide caret that is a target of one character. This is now the fourth surface in this client to have had the same bug; it is worth treating as the default for any Label or Region used as a control.

- 2026-07-27 — **Refusals became notifications, and stopped being the one message class that never reached the journal** — recorded in `EventLog.error`, `GameSession.refuse`, and the three panels that used to print them inline.

  Every tool window kept a strip at the top for its last refusal. Three problems, and the third is the one that mattered: it duplicated a surface the client already has; it put the message at the top of a panel whose controls may be at the bottom; and **a refusal was the only kind of message that never reached the log**. A player could be told "not enough cycles", look away, and have no way to find out what they had been told.

  ⚠ **The fix is to log them, not to add a push API.** `Notifications` is emphatic that it is "the log, filtered — not a second source of truth", because a toast with its own copy of an event can disagree with the journal and `04-mining.md` §3.1 makes noticing that two readouts disagree the way a player catches a hidden miner. So refusals are written with `EventLog.error` and the existing pipeline carries them: they toast as errors, and they stay permanently readable in the log window, which is exactly the condition `../client/01` §3 sets for a transient surface. **ERROR (3)** rather than WARNING so it passes any threshold a player is likely to set — a filter that swallowed a direct answer to a click would leave a button that silently does nothing. A repeat of the last line is dropped, the way syslog does it, so mashing an unaffordable control cannot flood a 500-line journal.

  ⚠ **`EX_USAGE` is deliberately not announced** — a malformed command only arrives from the terminal, which already answers on the line below, and toasting it would be telling the player twice that they typed something wrong.

  ⚠ **Client-side refusals needed their own route.** Three of the breach presenter's refusals ("pick a target first", "no layer is active") never reach the rules, so they produce no `Outcome` and would have gone silent. `GameSession.refuse(facility, why)` writes the line and nothing else — not a back door for the client to author game state, since no argument there can move a balance, a gate or an outcome.

- 2026-07-27 — **Breach console: arrow-keys-only tumbler, the ledger under the attention blocks, and closing the window ends the attempt.**

  **The tumbler is a keyboard control now, and only a keyboard control.** Clicking used to both choose a position *and* cycle it, which is why "selecting a column" appeared not to work — the click that was meant to aim also fired, and the arrow keys then went nowhere because focus had never reached the rack. Now: click aims, arrows act. Left and right move the cursor, up and down change the symbol under it.
  - ⚠ Bound as an **event filter on the rack**, not a handler and not per cell. A handler fires during bubbling, so anything between the focus owner and the rack can swallow the key first — and this panel lives inside a `ScrollPane`, which treats arrows as scroll commands.
  - ⚠ **The carets appear over the cursor column only.** Four pairs said "each of these is a control" and none of them usefully was. One pair says the true thing: this is where the keyboard is pointed, and left/right moves it.
  - The rack claims focus **once**, when a Logic layer first opens. A keyboard-only control the player has to find and click before it responds has no route at all until they guess; re-focusing on every refresh would yank the keyboard back from the action chips on every tick.

  **The attention ledger moved under the attention blocks**, same column. One question — how much have I got, and where did it go — so one column read top to bottom. It used to sit two panels lower, which made checking a cost against its outcome a scroll rather than a glance. The ledger stays visible after the attempt resolves; the meter and the action strip do not.

  **Closing the breach window abandons the attempt**, by the same method a quit does — a breach is a player at a terminal, not work the rig is doing, and there is nobody at the console once the window is gone. Recorded as an `aborted` resolution rather than deleted, so closing a window is not a free escape from a losing attempt, and the cycles go onto the thermal curve instead of being held by a console nobody is sitting at. ⚠ **Minimising does not do this** — `setMinimized` only flips visibility and leaves the frame in the desk, so only a real close detaches.

- 2026-07-27 — **The Logic board split into two halves.** The lock on the left — the tumbler rack, `SALTED`, the collapsing keyspace and the keyboard route — and what has been deduced on the right: the facts and the guess history. Two questions, *what am I turning* and *what do I know*, and stacking them put the second below the fold for the whole breach on anything but a tall window.

  ⚠ Fifty-fifty by `prefWidth(0)` plus `Hgrow.ALWAYS` on both halves, which is JavaFX's idiom for an even split — an `HBox` has no percentages, and setting a pref width of zero is what makes two children ask for nothing and therefore share the slack equally. ⚠ The left half will not shrink past the rack, because `minWidth` stays computed and an `HBox` respects it: a narrow window must clip the deductions rather than shear the lock, which is a character-cell texture and cannot reflow at all.

- 2026-07-27 — **"The breach window gets stuck after an abort" — two faults, and the first one meant the abort could not be performed at all.**

  **1. ⚠ The two-press abort was disarmed by every session tick.** Arming set a flag and `refresh` cleared it — and `refresh` runs on every session change, which is about once a second because self-mining credits on every tick. So the arming survived for under a second: press once and it arms, a tick clears it, press again and it arms again. A player trying to leave a breach could press Abort indefinitely and never abort. It is now armed for a **fixed window on the session clock** (4s), long enough to be a deliberate second press and short enough that a forgotten armed control cannot fire three moves later.

  ⚠ The general shape is worth naming, because this is the second time it has bitten in two days: **a transient UI state must not be cleared by a periodic repaint.** The START BREACH gate failed the same way from the other direction — it depended on a transition a repaint had already consumed.

  **2. `begin` gave the wrong refusal, which read as "you may not retry this target".** A resolved-but-undismissed breach is not open — the player already aborted — so being told to "abort it first" is an instruction they have carried out and cannot carry out again. The two states now have two sentences, and the second names the control that clears it. Aborting costs the attempt and never the target: only a *successful* breach leaves the foothold that blocks one.

  **3. The outcome slate offers "Try again"** on an aborted or failed attempt whose target is still attemptable — one press, dismissing and re-opening. ⚠ A deliberate exception to `BreachArming`'s aim/fire split: that split exists so a mis-click on a reflowing list or a moving graph cannot spend compute, and a control on an outcome slate the player is reading, about the attempt they were just in, is neither. It is hidden when there is nothing to retry, which the rules' own availability answer decides — a successful breach leaves a foothold and a successful crack leaves no miner.

  **4. ⚠ One throwing change-listener took every other panel with it.** `fire()` was a bare loop, so a listener that threw aborted the iteration and every listener after it in the list stopped being notified for that change — with which panels those were depending on subscription order. The symptom is a window that silently stops updating and looks frozen, with nothing in the log, and the panel that actually had the bug is not the one the player notices. Failures are now isolated and printed rather than swallowed.

- 2026-07-27 — **The tumbler's arrow keys were being delivered somewhere else entirely.**

  Reported as "selecting the first slot does nothing when I click the arrow keys", with a screenshot showing the cursor correctly parked over position 1. The cursor was right; the keys never arrived.

  ⚠ **A JavaFX event filter only fires for events targeted at the node it is on, or at one of its descendants.** The filter was on the rack — but the breach window's focus is almost never there. It is on an action chip, or on the `ScrollPane` that wraps the whole panel, and a `ScrollPane` treats arrow keys as scroll commands. So the rack sat waiting for events that were being delivered to a sibling or to an ancestor, and the control looked inert no matter which column was selected.

  The panel now installs the filter on its **outermost node** — every key press delivered anywhere inside the breach window passes through it on the way down, including one aimed at the `ScrollPane` itself — and hands arrows to the rack while the Logic board is showing. Gated on visibility, so the Enumeration and Traversal boards keep the arrows they need for ordinary focus traversal.

  ⚠ Worth stating as a rule, because "focus the node and add a filter" was tried twice and failed twice: **for a keyboard control inside a scrollable panel, route from the panel and gate on visibility.** Requesting focus is a nicety that helps when it works and cannot be relied on — a `requestFocus` on a node that is not yet visible, not yet in a scene, or in an unfocused window is a silent no-op, and there is no signal that it failed.
