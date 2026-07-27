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

## 4. How to use this doc

- Before starting design work on any system, check here for its open questions.
- When you make a decision that closes one, **move the decision into the system doc** (that's the source of truth) and log it in §3 — don't leave the answer only here.
- Proposal questions (§2) are provisional; treat the proposal docs' [PROPOSAL] tags as the signal that nothing there is load-bearing yet.
