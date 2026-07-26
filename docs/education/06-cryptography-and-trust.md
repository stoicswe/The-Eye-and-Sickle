# 05 — Cryptography and trust — proving things without a central authority

**Status:** ⚠️ **[PROPOSAL]** — the *goal* is Established and load-bearing (client pillar **C6**, `../client/00-client-overview.md` §2; the honesty rule, §5.3; the falsifiable claim in `../client/04-terminology-and-education.md` §1.1). What this document adds — the concept inventory, the seventeen written entries, the ownership boundary with `07`, and the scope cuts in §5 — is first-pass curriculum written against the contract in `00-curriculum-and-method.md`. Every factual claim was checked against a primary source in this pass and recorded per entry in `verified:`; anything that could not be checked is marked ⚠ inline and listed in §1.6.
**Depends on:** `00-curriculum-and-method.md` (**the contract** — the entry template §3, the status procedure §4, section assignment §5, the stage model and rules R1–R8 §6, the coverage tests §7); `../client/04-terminology-and-education.md` §1 (the principle), §2.13 / §2.15 (the mapping and homonym tables), §4.8 (the shipped file format), §4.9 (`provenance-chain(7)`, `vault(7)`); `../design/glossary.md`; `../architecture/02-identity-and-auth.md`, `../architecture/04-item-provenance.md`, `../architecture/05-validator-quorum.md`, `../architecture/07-transport-security.md`; `protocol/src/main/java/.../crypto/` (the shipped implementation these entries describe)
**Depended on by:** `07-distributed-systems-and-identity.md` (hash chains, canonicalization, DIDs and quorums all sit on this document's `hash(7)`, `digital-signature(7)` and `trust-anchor(7)`); and, through it, `client/src/main/resources/terms/**`

---

## 1. The domain

### 1.1 What it is

This domain is the answer to one question the game asks constantly and almost no other game asks at all: **how do you establish that something is true when there is nobody in charge?**

The Eye is a central authority. The Sickle, structurally, is not — it is a federation of self-hosted servers, each of which is adversarial by design (`../architecture/03-server-and-federation.md` §1) and none of which may be the arbiter of a cross-server outcome (Invariant **I15**). Every mechanism that makes that workable is cryptographic: hashes that make an edit detectable, signatures that bind a statement to a key, chains that make history rewritable only by rewriting all of it, and a quorum that makes one dishonest signer insufficient.

The concepts are not decoration on the fiction. **The game ships the real algorithms**, in `protocol/src/main/java/.../crypto/`: SHA-256 digests formatted as RFC 9530 `Content-Digest` field values, Ed25519 signatures (RFC 8032) over JSON canonicalized with JCS (RFC 8785), packaged as detached JWS (RFC 7515), with X25519 key agreement (RFC 7748), HKDF-SHA256 (RFC 5869) and AES-256-GCM on the transport. A player who understands the `verify` command understands what `gpg --verify` does, because it is the same operation over the same primitives.

### 1.2 Why a player of *this* game benefits

Three reasons, in increasing order of force.

**The game already makes the player do the thing.** `verify(1)` is the single exit status the client computes for itself — every other number on screen is the server's (`../client/00-client-overview.md` §1.1, `../client/04-terminology-and-education.md` §3.5). That is not a UI detail; it is a trust decision the architecture made deliberately (`../architecture/04-item-provenance.md` §6.2, "offline verifiability"), and it is exactly the decision a person makes when they check a download's signature instead of trusting the page it came from. The player performs the discipline before they have a word for it. The curriculum's job is to supply the word.

**The consequences are visible.** "An unverifiable chain is not recognised" (`../architecture/03-server-and-federation.md` §4) is not a warning banner; it is the item becoming worthless. Elsewhere in life, a failed signature check is a red line in a terminal that most people click past. Here it costs something, which is the only reliable way anyone learns what a signature is actually for.

**This is the one domain in the game where nothing was invented.** §1.6's ledger records the result: of 38 concepts, **zero** are `game` status. There is no fictional cryptography anywhere in this game — no invented cipher, no magic key, no "unbreakable encryption". That is unusual enough to state as a finding rather than leave a reader to notice, and it is the strongest single support the game has for the C6 claim.

### 1.3 The surfaces the player meets it on

Each row is a hook that exists in the design or client docs today. §7.1 item 4 of the contract requires this; the entries below cite these rows by anchor.

| Surface | Where it is specified | What the player does | Concepts carried |
|---|---|---|---|
| `verify <item>` | `../client/04-terminology-and-education.md` §3.10; `../client/00-client-overview.md` §1.1 | Asks their own client to re-check an item's signed history | signature, offline verification, trust anchor, hash |
| `item-history <item>` | `../architecture/04-item-provenance.md` §6.1 | Walks `prevRecordHash` back to genesis and reads each event | hash, collision resistance, checksum-vs-hash |
| `provenance-record(5)` | `../architecture/04-item-provenance.md` §2–3 | Reads the record shape: `issuerDid`, `kid`, `nonce`, `timestamp`, `prevRecordHash`, `alg: EdDSA` | nonce, replay, signature, public key |
| `rainbow-table(1)` | `../design/06-intrusion-tools.md` §1, §2 | Buys a tool that is "useless against salted targets" | salt, password hashing, KDF |
| Credential puzzle class | `../design/05-hacking-minigame.md` §3.1 | Defeats an authentication layer | authentication, credential reuse |
| `credential-harvester(1)` | `../design/06-intrusion-tools.md` §1 | Reuses harvested credentials on linked nodes | credential reuse, authorisation |
| Encrypted Vault | `../design/01-core-resources.md` §6; `../client/04-terminology-and-education.md` §4.9 | Chooses where an item lives, trading safety for use | symmetric encryption, key, threat model |
| `relay-chain(1)` | `../design/08-stealth-and-noise.md` | Pays per hop for onion routing | key exchange, forward secrecy, machine-in-the-middle |
| `ghost-protocol(1)` | `../design/08-stealth-and-noise.md`; `../client/04-terminology-and-education.md` §2.7 | Discards an identity and keeps nothing it earned | private key, rotation, revocation, non-repudiation |
| `provenance-tracer(1)` | `../design/07-recon-tools.md` | Audits their own assets to find out whether they are still theirs | checksum, integrity monitoring |
| `market install` | `../client/04-terminology-and-education.md` §2.2 | Installs a tool somebody else made | supply-chain attack, trust anchor |
| `zero-day(1)`, `overflow-kit(1)` | `../design/06-intrusion-tools.md` §1 | Exploits an implementation flaw in something whose design was fine | why you do not roll your own |
| `side-channel-reader(1)` | `../design/06-intrusion-tools.md` §1 | Learns about a system without entering it | constant-time comparison |
| Recovered narrative artefacts | `../client/04-terminology-and-education.md` §3.1 rule 7 | Reads text that *looks* like a command and finds that clicking it does nothing | injection |

### 1.4 The boundary with `07`, stated so nobody writes an entry twice

The contract's ownership ladder (`00-curriculum-and-method.md` §1.4) puts a concept in the lowest-numbered domain that can define it without forward-referencing a higher one, and its own worked example is this boundary: *"a hash chain needs hashing, so `07` cites `05`."*

**This document therefore owns the primitives and cedes the structures built out of them.**

| Ceded to `07` | Why | Cited from here as |
|---|---|---|
| `hash-chain(7)`, `append-only-log(7)`, `provenance-chain(7)` | Need `hash(7)` first | `seeAlso` on `hash(7)`, `checksum(7)`, `collision-resistance(7)` |
| `did(7)` | Needs `public-key-cryptography(7)` first; and its game surface is the `identity` window, which is `07`'s | `seeAlso` on `public-key-cryptography(7)`, `trust-anchor(7)` |
| `canonicalization(7)` | Needs `digital-signature(7)` first — "a signature is over bytes, so you must agree on the bytes" | `seeAlso` on `digital-signature(7)`, `verify(1)` |
| `quorum(7)`, `equivocation(7)`, `validator-reputation(7)` | Byzantine fault tolerance is a distributed-systems concept that happens to use signatures | `seeAlso` on `trust-anchor(7)`, `certificate-authority(7)` |
| `ledger-entry(5)`, ledger publicness | A `07` surface | `seeAlso` on `offline-verification(7)` |

The consequence, and it is a hard rule under **R8**: **no entry in this document may name a `07` entry in `prerequisites`.** Several name them in `seeAlso` and several *describe* them in the body, which R8 permits and R4 permits so long as the stage is not later — every ceded concept above is `adversarial`, and so is every entry here that leans on one.

Also ceded, downward, and assumed to exist: `byte(7)` (`01`), `storage-tiers(7)` and `permissions(7)` (`03`), `shell(7)` and `exit-status(7)` (`04`). §2.4 lists these as a dependency this document does not control.

### 1.5 The detection and legality material, which `08` owns

⚠ **This document is narrower than the domain the contract originally defined, and the difference is not cosmetic.** Before **ED-3** was resolved, `00-curriculum-and-method.md` §1.4 gave this domain *"threat models; authentication vs. authorisation; hashing, salting and key derivation; symmetric and public-key encryption; signatures; randomness; the real attack classes the tools mirror; **detection, logging and anti-forensics; why 'hack back' is illegal**."* This document covers everything in that list except the two items in bold, and the resolved §1.4 now reflects that by dropping them from this domain's row rather than pretending they are here.

Those concepts are now **`08-detection-and-defence.md`'s**, written on 2026-07-25. They are listed here because this document is where the gap was found, and because each one's `seeAlso` reaches back into this domain:

- `log-integrity(7)` / anti-forensics — hook: `log-scrubber(1)`, whose page `../client/04-terminology-and-education.md` §2.7 says **must** carry the defender's answer (ship logs off the box; `chattr +a`). Arguably `03`'s, since it is a filesystem and logging concept.
- `intrusion-detection(7)`, `cross-view-detection(7)`, `integrity-monitoring(7)` — hooks: `scan(8)`, `detection-array(8)`, the `audit` window. `cross-view-detection(7)` is named in the contract's own §7.2 as the highest-priority audit target in the whole doc set, so it must not fall through this crack.
- `hack-back(7)` — hook: `auto-counter-daemon(8)`, whose page **must** state that hacking back is illegal in most jurisdictions (`../client/04-terminology-and-education.md` §2.8). This is the one page in the game that tells a player specifically not to do something, and it has no owner.
- `honeytoken(7)` — hook: Canary Token, a §2.15 homonym with a mandatory `notes:` line.

Raised as **CT-1**, and resolved the way this document recommended: a document of their own rather than a merge. `08-detection-and-defence.md` writes `log-integrity(7)`, `anti-forensics(7)`, `honeytoken(7)` and `hack-back(7)` in full, and inventories the rest. `cross-view-detection(7)` went to `03-operating-systems.md` as recommended — and was in fact already written there.

### 1.6 The honesty ledger

Required by `00-curriculum-and-method.md` §7.1 item 5 and §7.4.

**Status distribution across the 38 inventoried concepts:**

| `status` | Count | Which |
|---|---|---|
| `real` | **36** | Everything except the two below |
| `real, simplified` | **2** | `rainbow-table(1)`, `verify(1)` |
| `game` | **0** | — |

Zero `game` entries is a finding, not an omission. §4.4 of the contract says a `game` page is written when a real-looking word would otherwise be absorbed as a real concept; this domain contains no such word, because the game invented no cryptography. The nearest candidates all belong elsewhere: `ghost-protocol(1)` is `game` (no real system wipes a reputation) and is a stealth tool; `dead-drop(1)` is already written in `../client/04-terminology-and-education.md` §4.9; `self-mining`'s immunity (**I4**) is `game` and belongs to `04`'s mining material. A reviewer should treat "did this document quietly relabel something as `real`?" as their first question, precisely because a clean sheet is suspicious.

**Unverified or approximate claims, marked ⚠ in the entries that carry them:**

| Claim | Where | Status |
|---|---|---|
| Fast-hash guess rates ("billions per second on one consumer graphics card") | `password-hashing(7)` | ⚠ Order of magnitude only. Sourced from published hashcat benchmark tables, which track hardware and date quickly. The entry states the *mechanism* (memory-hardness removes the attacker's parallelism advantage) as the load-bearing fact and treats the rate as illustrative. |
| The number of root certificates in a typical trust store | `trust-anchor(7)`, `certificate-authority(7)` | ⚠ Deliberately not asserted. Could not be pinned to a stable primary source in this pass, so the entries make the reader count their own store instead — which is a better transfer test anyway. |
| Ed25519 verification cost ("well under a millisecond") | `offline-verification(7)` | ⚠ Hardware-dependent. The entry hands the reader `openssl speed ed25519` rather than a number. |
| Whether `../architecture/07-transport-security.md`'s construction is sound | `roll-your-own(7)` | ⚠ Explicitly unreviewed. That is the entry's subject, and the entry says so. |

---

## 2. The concept inventory

### 2.1 How to read it

Thirty-eight concepts. This is the **coverage guarantee**: every concept this domain judges worth teaching appears here, whether or not a full entry has been written for it. §3 writes seventeen of them out in full; the rest are specified enough that a writer can follow §8.1's loop without re-deciding scope.

Columns follow the template in `00-curriculum-and-method.md` §3.2. `prerequisites` uses `name(section)` refs; entries from other domains are marked ⟨d⟩ where d is the owning document. **Full** marks the seventeen written out in §3.

### 2.2 The table

**A — Goals and discipline.** What cryptography is for, and how to think about it at all.

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 1 | `security-properties(7)` | The four different things cryptography can buy you. | real | investigating | `threat-model(7)` | `verify(1)`; the Encrypted Vault | ✔ |
| 2 | `threat-model(7)` | Who you are defending against, and what they can already do. | real | investigating | `storage-tiers(7)`⟨02⟩ | Choosing a storage tier for an item | ✔ |
| 3 | `kerckhoffs-principle(7)` | A system stays safe when the enemy has read its design. | real | investigating | `key(7)` | `market show` prints every tool's rules in full | ✔ |
| 4 | `roll-your-own(7)` | Why a reviewed implementation beats a fresh one, every time. | real | adversarial | `kerckhoffs-principle(7)` | `zero-day(1)`, `overflow-kit(1)` | ✔ |
| 5 | `constant-time-comparison(7)` | Checking two secrets match without leaking how far you got. | real | adversarial | `hash(7)` | `side-channel-reader(1)` |  |

**B — Hashing.** The single most load-bearing primitive in the game, and the one carrying the most damaging misconception in the whole curriculum.

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 6 | `hash(7)` | A fixed-size number computed from any amount of data. | real | investigating | `byte(7)`⟨01⟩ | `item-history(1)` prints `prevRecordHash` | ✔ |
| 7 | `collision-resistance(7)` | Nobody can find two inputs that produce the same output. | real | investigating | `hash(7)` | `verify(1)` failing on a tampered record |  |
| 8 | `checksum(7)` | A short value showing whether data arrived intact. | real | investigating | `hash(7)` | `provenance-tracer(1)`; the transport `Content-Digest` | ✔ |

**C — Passwords and credentials.** The material with the highest real-life value per word in the entire curriculum.

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 9 | `password-hashing(7)` | Storing a login secret so it can be checked, never read back. | real | investigating | `hash(7)`, `salt(7)` | Credential puzzle class | ✔ |
| 10 | `salt(7)` | A unique value mixed into each password before storing it. | real | investigating | `hash(7)` | `rainbow-table(1)`'s stated weakness | ✔ |
| 11 | `kdf(7)` | A function made deliberately slow and memory-hungry on purpose. | real | investigating | `password-hashing(7)` | `rainbow-table(1)` `CAVEATS` |  |
| 12 | `rainbow-table(1)` | Cracks weak or reused credentials; nothing salted. | **real, simplified** | investigating | `salt(7)`, `kdf(7)` | The tool itself. ⚠ §2.15 homonym — mandatory `notes:` |  |
| 13 | `credential-reuse(7)` | One password, many accounts, one breach away from all of them. | real | investigating | `authentication(7)` | `credential-harvester(1)`'s pivot |  |

**D — Randomness.**

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 14 | `randomness(7)` | Values an adversary cannot predict, not merely ones you cannot. | real | investigating | `byte(7)`⟨01⟩ | The `nonce` field on every provenance record |  |

**E — Symmetric cryptography.**

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 15 | `key(7)` | The one secret everything else is allowed to be public around. | real | investigating | `byte(7)`⟨01⟩ | The Encrypted Vault |  |
| 16 | `symmetric-encryption(7)` | Making data unreadable with one key that both sides hold. | real | investigating | `key(7)` | The Encrypted Vault | ✔ |
| 17 | `aead(7)` | Concealing data and detecting alteration in one operation. | real | adversarial | `symmetric-encryption(7)`, `security-properties(7)` | The `vault(7)` caveat; the client↔server channel |  |
| 18 | `nonce(7)` | A value used exactly once, so a message cannot count twice. | real | adversarial | `randomness(7)`, `replay-attack(7)` | The `nonce` field in `provenance-record(5)` | ✔ |

**F — Public-key cryptography.**

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 19 | `public-key-cryptography(7)` | A matched pair, one half published and one half never shared. | real | adversarial | `symmetric-encryption(7)` | `issuerDid` and `kid` on every record | ✔ |
| 20 | `private-key(7)` | The half that never moves, and is the whole of the identity. | real | adversarial | `public-key-cryptography(7)` | `ghost-protocol(1)` |  |
| 21 | `digital-signature(7)` | Proof that one specific key approved these exact bytes. | real | adversarial | `public-key-cryptography(7)`, `hash(7)` | `verify(1)`; the `signature` block | ✔ |
| 22 | `key-exchange(7)` | Agreeing a shared secret over a line everyone can hear. | real | adversarial | `public-key-cryptography(7)` | `relay-chain(1)` — one layer per hop |  |
| 23 | `forward-secrecy(7)` | Today's stolen key does not open last year's recordings. | real | adversarial | `key-exchange(7)` | `relay-chain(1)`; the transport handshake |  |
| 24 | `key-rotation(7)` | Replacing a secret on a schedule, before it is ever lost. | real | adversarial | `private-key(7)` | `ghost-protocol(1)`; transport key attestations |  |

**G — Trust.** The title of this document, and the part with no real-world equivalent that is any easier.

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 25 | `trust-anchor(7)` | The one thing a chain of proof asks you to believe outright. | real | adversarial | `digital-signature(7)` | "An unverifiable chain is not recognised" | ✔ |
| 26 | `certificate(7)` | A signed claim that a particular name owns a particular key. | real | adversarial | `digital-signature(7)` | Its absence — the game binds to DIDs, not hostnames |  |
| 27 | `certificate-authority(7)` | An organisation your machine believes when it vouches for a name. | real | adversarial | `trust-anchor(7)`, `certificate(7)` | Its deliberate absence (`../architecture/07` §3) | ✔ |
| 28 | `web-of-trust(7)` | Deciding whom to believe yourself, and inheriting their opinions. | real | adversarial | `trust-anchor(7)` | Validator reputation weighting, as an adjacency |  |
| 29 | `offline-verification(7)` | Checking a proof yourself rather than trusting the report. | real | adversarial | `digital-signature(7)` | `verify(1)` — the only client-computed status | ✔ |
| 30 | `verify(1)` | Re-checks an item's signed history on your own machine. | **real, simplified** | adversarial | `digital-signature(7)`, `offline-verification(7)`, `exit-status(7)`⟨04⟩ | The command | ✔ |
| 31 | `revocation(7)` | Announcing that a key you already published is no longer good. | real | adversarial | `certificate(7)`, `key-rotation(7)` | `ghost-protocol(1)`; `../architecture/07` T-3 |  |

**H — Attack classes.** Conceptual only; §5 states the line this document will not cross.

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 32 | `replay-attack(7)` | Re-sending a genuine message so it counts a second time. | real | adversarial | `authentication(7)` | `nonce` + `timestamp` on every record | ✔ |
| 33 | `machine-in-the-middle(7)` | Sitting between two parties and answering as each of them. | real | adversarial | `key-exchange(7)` | `relay-chain(1)`; a hostile relay |  |
| 34 | `injection(7)` | Data getting read as instructions by something downstream. | real | investigating | `shell(7)`⟨04⟩ | Recovered artefacts are data and never run |  |
| 35 | `social-engineering(7)` | Attacking the person, because the person is always reachable. | real | investigating | `authentication(7)` | The Informant (`../design/12`) |  |
| 36 | `supply-chain-attack(7)` | Compromising what you install rather than what you run. | real | investigating | `checksum(7)` | `market install` |  |

**I — Authentication and authorisation.**

| # | id | Gloss | Status | Stage | Prerequisites | Game surface | Full |
|---|---|---|---|---|---|---|---|
| 37 | `authentication(7)` | Establishing which account is making a request. | real | operating | `whoami(1)`⟨02/04, ⚠ owner unsettled⟩ | Sign-in; the Credential puzzle class |  |
| 38 | `authorisation(7)` | Deciding whether that account may do this particular thing. | real | operating | `authentication(7)` | Storage-tier exposure; the five unlock gates |  |

### 2.3 The graph, checked

`00-curriculum-and-method.md` §6.4's five checks, run by hand over the table above.

1. **Every `prerequisites` reference resolves** — within this document, yes. Six point outward (§2.4) and are conditional on those documents being written; that is the one real risk and it is **CT-2**.
2. **Acyclic** — yes. The only edge that looks circular is `nonce(7) → replay-attack(7)`, which runs defence-depends-on-attack, not the reverse: a reader must know what a replay is before "used exactly once" means anything, and `replay-attack(7)` depends on `authentication(7)`, never on `nonce(7)`.
3. **Stage ≥ max prerequisite stage** — yes, checked pairwise. The tightest cases are `nonce(7)` (adversarial, from `replay-attack(7)` adversarial) and `credential-reuse(7)` (investigating, from `authentication(7)` operating).
4. **Reachable from a `first-session` root** — yes, through the six outward edges. Every chain in this domain terminates at `byte(7)`, `shell(7)`, `exit-status(7)`, `whoami(1)`, `storage-tiers(7)` or `permissions(7)`. **No entry in this document has `prerequisites: none`**, deliberately: an entry with no prerequisites at a late stage fails check 4, and cryptography genuinely does not start from nothing.
5. **No prerequisite edge points upward** — yes. Zero edges to `07`, despite five `07`-owned concepts being described in bodies (§1.4).

**Stage budget check**, against `00-curriculum-and-method.md` §6.2:

| Stage | This domain | Domain-wide budget | Note |
|---|---|---|---|
| `first-session` | **0** | ≤ 12 across all seven | Correct. Nothing here is needed to act in the first twenty minutes, and **R2** is the rule most easily broken by a domain that finds itself interesting. |
| `operating` | 2 | ~25–40 | Only `authentication(7)` / `authorisation(7)`, which the player meets at sign-in. |
| `investigating` | 17 | ~30–50 | Roughly a third of the stage. Defensible: hashing, salting and threat modelling all arrive when the player first has something worth defending. |
| `adversarial` | 19 | ~25–40 | ⚠ **This domain alone claims about half the adversarial budget, and `07` will claim most of the rest.** That is what §6.2 predicts ("everything that only matters once trust is a problem"), but the two documents must be totalled before either ships. **CT-3.** |

### 2.4 What this domain assumes other documents will define

Six outward prerequisite edges. All point downward, satisfying **R8**; none is optional.

| Ref | Assumed owner | Why this domain needs it |
|---|---|---|
| `byte(7)` | `01` | Nothing here can state a size — 256 bits, 32 bytes, 64 hex characters — without it |
| `storage-tiers(7)` | `03` | `threat-model(7)`'s hook is the tier decision |
| `permissions(7)` | `03` | `authorisation(7)`'s real counterpart |
| `shell(7)` | `04` | `injection(7)` needs the idea that text can be read as a command |
| `exit-status(7)` | `04` | `verify(1)` is a section-1 page with an `EXIT STATUS` block |
| `whoami(1)` | ⚠ `03` or `04` | `authentication(7)`'s only honest prerequisite. Ownership genuinely unclear — see **CT-2** |

---

## 3. The entries

### 3.0 Which seventeen, and why

Seventeen of thirty-eight are written out in full. Each was chosen against at least two of the brief's three criteria — *the game leans on it*, *it carries a misconception worth killing*, *it unlocks several other entries*.

| Entry | Leaned on | Misconception | Unlocks |
|---|---|---|---|
| `security-properties(7)` | — | The domain's root conflation: "encrypted" meaning "safe" | 3 |
| `threat-model(7)` | Storage tiers | "Security is a level" | 1, and the framing every other entry uses |
| `kerckhoffs-principle(7)` | Published tool costs | Security through obscurity | `roll-your-own(7)` |
| `hash(7)` | `item-history(1)` | "Hashing scrambles data" — the single most damaging belief here | 5 |
| `checksum(7)` | `provenance-tracer(1)`, the transport digest | "The checksum matched, so it is genuine" | `supply-chain-attack(7)` |
| `salt(7)` | `rainbow-table(1)` is balanced on it | "The salt is a second secret" | `password-hashing(7)`, `kdf(7)` |
| `password-hashing(7)` | Credential class | "Hashed, therefore harmless" | `kdf(7)`, `credential-reuse(7)` |
| `symmetric-encryption(7)` | The vault | "Encrypted means untamperable" | 3 |
| `nonce(7)` | Visible in `provenance-record(5)` | "A nonce is just a random number" | — |
| `public-key-cryptography(7)` | `issuerDid`, `kid` | "Public encrypts, private decrypts, full stop" | 4 |
| `digital-signature(7)` | The whole provenance layer | "A signature proves a person signed" | 3 |
| `trust-anchor(7)` | Non-recognition of bad chains | "Cryptography means trusting nobody" | 3 |
| `certificate-authority(7)` | Its deliberate absence | "The padlock means the site is safe" | `web-of-trust(7)`, `revocation(7)` |
| `offline-verification(7)` | `verify(1)` is the only client-computed truth | "The server said it is genuine" | `verify(1)` |
| `replay-attack(7)` | `nonce` + `timestamp` exist for it | "Authentic and unaltered is safe to act on" | `nonce(7)` |
| `roll-your-own(7)` | `zero-day(1)`; this project's own transport layer | "Cryptography fails when the maths breaks" | — |
| `verify(1)` | The command | "Verified is a property of a file" | — |

**Deliberately deferred, and why**, so the next writer knows where to start: `key-exchange(7)` and `machine-in-the-middle(7)` (highest value of the remainder; deferred only because their game hook — `relay-chain(1)` — is one tool rather than several surfaces); `randomness(7)` (superb material in the 2008 Debian OpenSSL bug, but it is a prerequisite of one written entry rather than five); `authentication(7)` (blocked on the `whoami(1)` ownership question, **CT-2**); `rainbow-table(1)` (needs the mandatory `notes:` line that `../client/04-terminology-and-education.md` §2.15 requires, and that line should be written alongside the other homonyms, not alone).

---

### 3.1 `threat-model(7)`

```
id:             threat-model
section:        7
name:           threat model
canonical:      threat model
gloss:          Who you are defending against, and what they can already do.
status:         real
aliases:        threat modelling, adversary model
seeAlso:        security-properties(7), storage-tiers(7), vault(7),
                trust-anchor(7), kerckhoffs-principle(7)
reading:        EFF Surveillance Self-Defense, "Your Security Plan";
                STRIDE (Kohnfelder & Garg, Microsoft, 1999);
                RFC 3552 §3 (writing security considerations)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  storage-tiers(7)
hook:           The three-way storage-tier decision the game forces on every
                item (`../design/01-core-resources.md` §6): vault, standard,
                or high-hackable. The player is choosing an adversary to be
                safe from, and paying for it in usability.
misconception:  commonly believed security is a single level you have more or
                less of, so a system can be described as "very secure";
                actually it is always relative to a named adversary — a lock
                that stops a neighbour and not a locksmith is not half secure,
                it is secure against one person and useless against another,
                and the sentence "is this secure?" has no answer until the
                other person is named.
transfer:       Write three lines about your own phone: what you are
                protecting, who you are protecting it from, and what that
                person can already do. Then check whether your screen lock
                addresses the person you named. Most people find it stops a
                pickpocket, does nothing against someone who knows their
                birthday, and was never relevant to a court order at all.
                Platform-independent — no shell required.
verified:       "what are you protecting / from whom / how bad is failure"
                framing — EFF Surveillance Self-Defense "Your Security Plan";
                STRIDE originated in a 1999 Microsoft internal paper by Loren
                Kohnfelder and Praerit Garg; RFC 3552 (BCP 72) requires
                Internet-Draft authors to state the attacker's capabilities
                before the defences. Checked 2026-07-25.

## DESCRIPTION

The game will not let you avoid this. Every item you own sits in one of three
tiers, and the tiers are not better and worse — they are safe from different
people. The vault cannot be raided at all, and cannot be used. Standard storage
is exposed while you are online. The high-hackable zone is exposed always, and
is the only tier with room.

There is no correct answer, because "safe" is not a property of the item. It is
a relationship between the item and somebody specific. A tool in the vault is
safe from every raider in the game and useless to the bot that needed it.

The useful question is therefore never "is this safe" but four questions:
what am I protecting, who wants it, what can they already do, and what happens
if they get it. Answer those and the tier chooses itself. Skip them and you
will make the choice anyway, badly, on instinct.

## REAL-WORLD COUNTERPART

real — threat modelling, which is an ordinary professional discipline with
named methods rather than a mindset.

The best-known framework is STRIDE, from Microsoft in 1999, which enumerates
six things an attacker might do: spoof an identity, tamper with data, repudiate
an action, disclose information, deny service, or elevate privilege. Internet
standards carry the same requirement structurally: RFC 3552 obliges the author
of a specification to describe what the attacker can do before describing the
defences, which is why every RFC has a Security Considerations section.

The reason the discipline exists is that untargeted security spends effort in
the wrong places. Full-disk encryption is excellent against a stolen laptop and
irrelevant against a phishing email, and the two failures are not comparable —
one is recoverable and the other is not.
```

---

### 3.2 `security-properties(7)`

```
id:             security-properties
section:        7
name:           security properties
canonical:      security properties
gloss:          The four different things cryptography can buy you.
status:         real
aliases:        confidentiality, integrity, authenticity, non-repudiation,
                the CIA triad
seeAlso:        threat-model(7), hash(7), symmetric-encryption(7),
                digital-signature(7), aead(7), checksum(7)
reading:        RFC 4949 (Internet Security Glossary, version 2);
                RFC 3552 §2 (BCP 72)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  threat-model(7)
hook:           The gap between the Encrypted Vault and `verify(1)`. The vault
                buys exactly one of the four; `verify` buys two others; and
                nothing in the game buys all four at once, because nothing
                anywhere does.
misconception:  commonly believed encryption makes data "secure", so a system
                that encrypts is a system that is protected; actually
                encryption buys concealment and nothing else — it does not stop
                someone altering what you cannot read, does not prove who sent
                it, and does not stop them denying later that they did. Each of
                those is a separate mechanism with a separate cost, and most
                real failures are a missing one of the other three.
transfer:       Take any product page that says "end-to-end encrypted" and try
                to work out which of the four it is claiming. Almost all claim
                concealment; the good ones also say how the other end is
                authenticated, and that sentence is the one worth reading.
                Platform-independent.
verified:       The four terms and their distinctions — RFC 4949 (Internet
                Security Glossary v2, 2007), definitions of data
                confidentiality, data integrity, authentication and
                non-repudiation; the MAC-versus-signature distinction on
                non-repudiation — RFC 4949, and it follows directly from a MAC
                key being held by both parties. Checked 2026-07-25.

## DESCRIPTION

Four different promises, and the game hands them out separately.

**Confidentiality** — nobody else can read it. This is what the Encrypted Vault
buys, and it is all it buys.

**Integrity** — nobody changed it. This is what `prevRecordHash` buys on a
provenance chain: alter an old record and every identifier after it stops
matching.

**Authenticity** — it came from who it says. This is what the Ed25519 signature
on each record buys, and it is why the issuer's key matters more than the
server relaying it.

**Non-repudiation** — they cannot later deny sending it. The provenance chain
buys this too, and it is the property `ghost-protocol(1)` cannot undo: an
identity can be discarded, but everything that identity ever signed stays
signed, by a key that was demonstrably theirs.

They are independent. You can have any subset. The commonest real mistake is
buying the first and assuming the other three arrived with it.

## REAL-WORLD COUNTERPART

real — these are the standard security properties, defined in RFC 4949, the
IETF's own security glossary.

The sharpest illustration of the independence is the difference between a
message authentication code and a signature. A MAC gives integrity and
authenticity: it proves the message was not altered and came from someone
holding the key. It gives no non-repudiation at all, because *both* parties
hold that key — either of them could have produced the tag, so neither can
prove the other did. A signature gives all three, because only one party holds
the private half. Neither gives confidentiality; both are usually used
alongside encryption, not instead of it.

This is also why the honest reading of "the connection is encrypted" is
"concealed from third parties" and nothing more. It says nothing about who is
at the other end, which is the property a certificate is for.
```

---

### 3.3 `kerckhoffs-principle(7)`

```
id:             kerckhoffs-principle
section:        7
name:           Kerckhoffs's principle
canonical:      Kerckhoffs's principle
gloss:          A system stays safe when the enemy has read its design.
status:         real
aliases:        Shannon's maxim, security through obscurity, open design
seeAlso:        key(7), roll-your-own(7), threat-model(7),
                symmetric-encryption(7), man(1)
reading:        A. Kerckhoffs, "La Cryptographie Militaire", Journal des
                sciences militaires, 1883 — the second of six axioms;
                FIPS 197 (the complete AES specification, published)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  key(7)
hook:           The whole market and `man` surface. `market show` prints a
                tool's requirement in words, `--dry-run` prints its published
                costs, and every defence in the game has a man page describing
                exactly how it works
                (`../client/04-terminology-and-education.md` §3.4, §3.10).
                Knowing how a tarpit works does not help you past one.
misconception:  commonly believed keeping the method secret makes a system
                stronger, so publishing how something works is a risk;
                actually secrecy of the method is a liability rather than an
                asset — when it leaks, and it does, you cannot change it,
                whereas a key can be replaced in an afternoon. Every cipher in
                serious use today is fully published, on purpose.
transfer:       Look up FIPS 197. The complete specification of AES — every
                step, every constant — is a free public document, and AES is
                what protects classified material. Then notice how easy it is
                to change your own Wi-Fi password and how impossible it would
                be to change WPA2. That difference is the entire principle.
                Platform-independent.
verified:       Kerckhoffs's second axiom, "Il faut qu'il n'exige pas le
                secret, et qu'il puisse sans inconvénient tomber entre les
                mains de l'ennemi" — La Cryptographie Militaire, 1883;
                Shannon's restatement, "the enemy knows the system"; AES is
                specified publicly in FIPS 197. Checked 2026-07-25.

## DESCRIPTION

Nothing in this game is hidden from you by design. Every tool prints its costs
before you commit, every gate states its requirement in words, and every
defence has a page explaining how it works. That is not generosity — a defence
whose only strength is that you had not read the manual would be worthless the
first time somebody did.

The principle generalises: **a system should remain secure even if everything
about it except the key is public.** Not "secrecy is worthless" — a defender is
entitled to keep their configuration quiet. The claim is narrower and harder:
security must not *depend* on that secrecy, because secrecy is the one property
you cannot renew.

## REAL-WORLD COUNTERPART

real — stated by Auguste Kerckhoffs in 1883, in an article on military
cryptography, as the second of six axioms. Claude Shannon restated it in one
line: the enemy knows the system.

The economics are the argument. A key is cheap to replace and can be replaced
per person, per session, per message. A design is expensive to replace, is
shared by everyone using it, and leaks through a stolen device, a
reverse-engineered binary, a departing employee, or a patent filing. Building
on the renewable secret and not the fragile one is simply the better trade.

The consequence visible everywhere: AES, SHA-256, Ed25519, TLS and Signal's
protocol are all published in full, and all are stronger for it, because
publication is how a weakness gets found by someone willing to say so. The
inverse pattern — a product describing its cryptography as proprietary — is
treated by practitioners as a warning rather than a feature, and this is the
usual reason why.
```

---

### 3.4 `hash(7)`

```
id:             hash
section:        7
name:           hash function
canonical:      hash function
gloss:          A fixed-size number computed from any amount of data.
status:         real
aliases:        hash, digest, cryptographic hash, SHA-256, message digest
seeAlso:        collision-resistance(7), checksum(7), salt(7),
                password-hashing(7), digital-signature(7),
                provenance-chain(7), item-history(1)
reading:        FIPS 180-4 (the SHA-2 family); shattered.io (the 2017 SHA-1
                collision); NIST policy on hash functions
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  byte(7)
hook:           `item-history <item>` prints one record per event, each naming
                the hash of the record before it
                (`../architecture/04-item-provenance.md` §6.1). Sixty-four hex
                characters per record, and the reason an old record cannot be
                edited quietly.
misconception:  commonly believed hashing scrambles data so it cannot be read,
                which makes it a kind of encryption; actually there is no key
                and nothing to undo — the output is far smaller than the input,
                so the original is not in there to recover. The belief is a
                reasonable extrapolation from a true fact about encryption,
                which is exactly why intelligent people arrive holding it.
transfer:       On macOS run `printf 'a' | shasum -a 256` and then the same
                with 'b'; on Linux use `sha256sum`. Two one-character inputs,
                two 64-character outputs with nothing in common. Then hash a
                large file and note the output is the same length. Assumes a
                Unix shell — see ED-8.
verified:       SHA-256 output length and the SHA-2 family — FIPS 180-4;
                collisions must exist by counting (more possible inputs than
                256-bit outputs); MD5 and SHA-1 collision status —
                shattered.io and the CWI/Google paper "The first collision for
                full SHA-1" (2017); NIST announced on 15 December 2022 that
                SHA-1 is to be phased out by 31 December 2030; git object
                naming — git documentation. Checked 2026-07-25.

## DESCRIPTION

Run `item-history` on anything you own and every record prints a
`prevRecordHash`: 64 hexadecimal characters naming the record before it. That
is a hash — a fixed-size number computed from data of any size.

SHA-256, which is what this game uses, always produces 256 bits — 32 bytes, 64
hex characters — whether it is given one character or an entire chain. The same
input always gives the same output, and changing one character anywhere gives
an output with no visible relationship to the previous one. That second property
is what makes the chain work: edit a record from six months ago and its hash
changes completely, so the record after it no longer points at anything, and so
does every record after that.

It is not encryption. Encryption is built to be undone by whoever holds the key.
A hash has no key, and the output is smaller than the input, so there is nothing
in it to undo.

## REAL-WORLD COUNTERPART

real — cryptographic hash functions, specified in FIPS 180-4 for the SHA-2
family. This is the same SHA-256 the reader's operating system already ships.

Four properties are what make one useful. It is deterministic. It is one-way —
given an output, finding an input that produces it is not feasible. It
avalanches — a one-bit change flips about half the output bits. And it is
collision-resistant: nobody can find two different inputs with the same output.

That last one is a claim about difficulty, not impossibility. There are more
possible inputs than there are 256-bit numbers, so collisions must exist by
counting alone. "MD5 is broken" means people can now construct one deliberately,
not that MD5 stopped producing output.

Hashes are how version control names things — git gives every object an
identifier computed from its own contents, which is why rewriting history
changes every identifier downstream and why a force-push is visible. They are
how a downloaded file is checked, how passwords are stored, and how this game's
chains are chained.
```

---

### 3.5 `checksum(7)`

```
id:             checksum
section:        7
name:           checksum
canonical:      checksum
gloss:          A short value showing whether data arrived intact.
status:         real
aliases:        digest, content digest, CRC, integrity check
seeAlso:        hash(7), digital-signature(7), supply-chain-attack(7),
                provenance-tracer(1), collision-resistance(7)
reading:        RFC 9530 (Digest Fields — the Content-Digest header);
                sha256sum(1); the Debian SHA256SUMS/SHA256SUMS.sign pair
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  hash(7)
hook:           `provenance-tracer(1)` — auditing your own assets to find out
                whether they are still yours
                (`../client/04-terminology-and-education.md` §2.5). It compares
                what an item hashes to now against what it hashed to before.
                That catches drift and corruption; it is not what stops a
                forger, and the difference is the entry.
misconception:  commonly believed that if the checksum on a download page
                matches, the file is genuine; actually anyone who can replace
                the file can replace the number printed beside it, so a bare
                digest proves the bytes did not change by accident and says
                nothing at all about who produced them.
transfer:       Next time you download an installer that publishes a SHA-256,
                check whether the site also publishes a *signature* over the
                checksum file. Debian, Tails and kernel.org do; most vendors do
                not. Where it exists, the two commands are `sha256sum -c
                SHA256SUMS` and then `gpg --verify SHA256SUMS.sign SHA256SUMS`
                — and only the second one is about an adversary. Assumes a
                Unix shell with GnuPG; Windows via Gpg4win.
verified:       Content-Digest field syntax and purpose — RFC 9530 (February
                2024, obsoletes RFC 3230); the corruption-versus-adversary
                distinction is documented in this repository's own
                `protocol/.../crypto/PayloadDigest` javadoc; Debian publishes
                SHA256SUMS alongside a detached OpenPGP signature.
                Checked 2026-07-25.

## DESCRIPTION

Every message this client exchanges with its home server carries a digest of
its own contents — a SHA-256 hash, in the standard HTTP `Content-Digest` form.
If the bytes arrive different from the bytes that were sent, the digest does
not match and the message is refused before anything tries to read it.

That is a **corruption** check, and it earns its place: a truncated read, a
buggy proxy, a flipped bit or a partial write all get caught early and cheaply,
before the confusing failures further downstream. It is also a cheap pre-filter,
because checking a 32-byte hash costs far less than checking a signature.

It is not what stops a cheat. Against somebody who can alter the bytes, a bare
digest proves nothing whatsoever — they alter the bytes, recompute the digest,
and send both. The game's defence against that is elsewhere and stays there:
every provenance record is signed, and a chain that fails is not recognised.

**A digest catches accident. A signature catches intent.** Nothing anywhere may
read "the digest matched" as "the sender is authentic".

## REAL-WORLD COUNTERPART

real — checksums, and specifically the `Content-Digest` field standardised in
RFC 9530.

The distinction has a long history in the tooling. A CRC-32 is 32 bits and is
designed for accidental error: it reliably catches the kinds of corruption a
wire or a disk produces, and because it is a linear function you can work out
exactly what to change to leave it unaltered. It was never meant to resist
anybody, and it does not.

Swapping in a cryptographic hash improves the situation but does not fix it,
because the attacker's move is not to find a collision — it is to replace the
published number. This is why the projects that take it seriously publish a
checksum file *and a detached signature over that file*: the checksum tells you
the download is intact, the signature tells you the checksum came from the
project. Most vendors publish only the first and describe it as verification.
```

---

### 3.6 `salt(7)`

```
id:             salt
section:        7
name:           salt
canonical:      salt
gloss:          A unique value mixed into each password before storing it.
status:         real
aliases:        salting, per-password salt
seeAlso:        password-hashing(7), hash(7), kdf(7), rainbow-table(1),
                nonce(7), credential-reuse(7)
reading:        crypt(5) — the /etc/shadow field format; OWASP Password
                Storage Cheat Sheet; Oechslin (2003) on rainbow tables
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  hash(7)
hook:           `rainbow-table(1)`. The tool's own description says it is
                useless against salted targets
                (`../design/06-intrusion-tools.md` §1), which makes recon worth
                paying for before you buy the attempt. The salt is the reason,
                and it is a one-line reason.
misconception:  commonly believed a salt is a second secret and must therefore
                be hidden; actually it is stored in plain sight next to the
                hash it belongs to, and its job is uniqueness rather than
                secrecy — it makes one precomputed table useless against a
                second account, and makes two people who chose the same
                password store two different values.
transfer:       On Linux, `sudo cat /etc/shadow` and look at one line. The
                field reads `$id$salt$hash` — `$6$` is SHA-512-crypt, `$y$` is
                yescrypt on current Debian and Fedora — and the salt is sitting
                there unhidden, in the same file, readable by anyone who can
                read the hash. Assumes Linux with root; macOS does not use
                /etc/shadow. See ED-8.
verified:       The `$id$salt$hash` field layout and algorithm identifiers —
                crypt(5); rainbow tables are defeated by per-password salts —
                Oechslin (2003) and the OWASP Password Storage Cheat Sheet;
                yescrypt is the default password hashing method on recent
                Debian and Fedora releases. Checked 2026-07-25.

## DESCRIPTION

A Rainbow Table cracks weak or reused credentials instantly and does nothing at
all against a salted target. That is not a balance decision; it is what salting
does, and it is why the tool is worth buying only after recon tells you which
kind of target you are looking at.

A salt is a value — sixteen random bytes is typical — generated fresh for each
account and mixed with the password before it is hashed. It is then stored
alongside the resulting hash, in the open, because it was never a secret.

What it destroys is *precomputation*. An attacker's cheapest attack against a
password store is to hash likely passwords once, in advance, and look up the
results. With a unique salt per account, the attacker's table would have to be
built again for every single account, which turns one cheap job into a hundred
thousand expensive ones. It also means two people who both chose the same
password produce two unrelated stored values, so cracking one tells you nothing
about the other.

## REAL-WORLD COUNTERPART

real — salting, and the reason rainbow tables largely died.

Any Linux machine demonstrates it. Password entries in `/etc/shadow` are
written `$id$salt$hash`: the algorithm identifier, then the salt, then the
result. Nothing is concealed except the password itself, which is not there at
all.

A rainbow table is a genuine and clever construction — a space-and-time
trade-off over precomputed hash chains, published by Oechslin in 2003 and
building on Hellman's 1980 work — and against an unsalted store it is
devastating. Against a per-password salt it does not work at all, which is why
modern password storage uses one universally and why the technique is now of
mostly historical interest.

Salting is not sufficient on its own. It stops precomputation; it does nothing
about an attacker who simply guesses fast against one account. That is what
deliberately slow functions are for.
```

---

### 3.7 `password-hashing(7)`

```
id:             password-hashing
section:        7
name:           password hashing
canonical:      password hashing
gloss:          Storing a login secret so it can be checked, never read back.
status:         real
aliases:        password storage, credential storage
seeAlso:        salt(7), kdf(7), hash(7), credential-reuse(7),
                rainbow-table(1), authentication(7)
reading:        RFC 9106 (Argon2); RFC 7914 (scrypt); OWASP Password Storage
                Cheat Sheet
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  hash(7), salt(7)
hook:           The Credential puzzle class
                (`../design/05-hacking-minigame.md` §3.1) — defeating an
                authentication layer by deduction and by spotting reuse. What
                you are attacking is a stored credential, and how it was stored
                decides whether the attack is minutes or centuries.
misconception:  commonly believed a leaked password database is harmless
                because the passwords were hashed; actually a fast hash over a
                human-chosen password falls in seconds, because the attacker is
                not reversing anything — they are guessing candidates and
                hashing them, at a rate a graphics card measures in billions
                per second. Hashing is necessary and nowhere near sufficient.
transfer:       Read the one-line recommendation in the OWASP Password Storage
                Cheat Sheet — Argon2id, 19 MiB of memory, 2 iterations, 1
                degree of parallelism — then look at what any service you use
                says about its own storage. A service that advertises "we hash
                your passwords with SHA-256" has just told you it is doing this
                wrong, and you can now say precisely why.
                Platform-independent.
verified:       Argon2 and its variants — RFC 9106 (September 2021); scrypt —
                RFC 7914; bcrypt — Provos & Mazières, 1999; the m=19 MiB, t=2,
                p=1 configuration — OWASP Password Storage Cheat Sheet.
                ⚠ Guess-rate figures are order-of-magnitude only, from
                published hashcat benchmark tables, and track hardware
                closely — the memory-hardness argument below is the load-
                bearing claim, not the rate. Checked 2026-07-25.

## DESCRIPTION

A Credential layer is an authentication check, and what sits behind it is a
stored credential rather than a stored password. No competent system keeps the
password itself: it keeps something computed from it, checks a login by
computing the same thing again, and compares.

That much is just hashing. The part that decides whether a stolen store is a
disaster or an inconvenience is *how expensive each guess is*. An attacker
holding a password store does not try to reverse anything. They guess — common
passwords, leaked passwords, dictionary words with a digit on the end — and
hash each guess. If the hash is fast, they get billions of attempts a second
and most human passwords fall the same day.

So the answer is to make it deliberately slow, and specifically to make it
require *memory*. A graphics card has thousands of cores and nothing like
thousands times 19 MiB of fast memory, so a function that insists on real
memory per guess removes the attacker's parallelism advantage rather than
merely taxing it.

## REAL-WORLD COUNTERPART

real — password storage, and the current answer is a small, specific list.

Argon2id (RFC 9106, 2021) is the modern default and won the multi-year Password
Hashing Competition. scrypt (RFC 7914) and bcrypt (Provos and Mazières, 1999)
remain acceptable. OWASP's recommended Argon2id configuration is 19 MiB of
memory, two iterations, and one degree of parallelism, tuned upward until a
single verification takes tens of milliseconds on production hardware — slow
enough to be ruinous a billion times over, fast enough that nobody waiting to
log in notices.

Plain SHA-256 is not on the list, and neither is any other fast hash. It is the
right primitive for a chain and the wrong one for a password, and this is one
of the few places where using a strong tool for the wrong job is itself the
vulnerability.

⚠ Rate figures move with hardware; the durable fact is the shape. Fast hash,
attacker wins on volume. Memory-hard function, volume stops being cheap.
```

---

### 3.8 `symmetric-encryption(7)`

```
id:             symmetric-encryption
section:        7
name:           symmetric encryption
canonical:      symmetric encryption
gloss:          Making data unreadable with one key that both sides hold.
status:         real
aliases:        secret-key encryption, symmetric cipher, AES
seeAlso:        key(7), aead(7), nonce(7), public-key-cryptography(7),
                vault(7), security-properties(7), key-exchange(7)
reading:        FIPS 197 (AES); RFC 5116 (authenticated encryption
                interface); cryptsetup(8)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          investigating
prerequisites:  key(7)
hook:           The Encrypted Vault, and specifically the trade the design
                forces (`../design/01-core-resources.md` §6): anything socketed
                into a bot leaves the vault. Safety and usability are mutually
                exclusive there on purpose, and that is the real property of
                encrypted storage rather than a game rule.
misconception:  commonly believed encrypted data cannot be tampered with,
                because an attacker who cannot read it cannot meaningfully
                change it; actually plain encryption conceals content without
                protecting it from alteration — with common modes an attacker
                who cannot read a message can still flip a chosen bit in what
                the recipient decrypts. That is why serious use is
                *authenticated* encryption rather than encryption.
transfer:       Find the full-disk encryption on the machine you are reading
                this on: FileVault in macOS System Settings, BitLocker in
                Windows, or a `crypt` device in the output of `lsblk` on Linux.
                Then say out loud what it protects (a powered-off machine
                somebody stole) and what it does not (anything at all, while
                you are logged in). Cross-platform.
verified:       AES specification, block size 128 bits, key sizes 128/192/256
                — FIPS 197; ECB's block-level pattern leakage is a documented
                property of the mode; the AEAD interface — RFC 5116; LUKS
                volume encryption — cryptsetup(8); `lsblk` reports TYPE crypt
                for mapped LUKS devices. Checked 2026-07-25.

## DESCRIPTION

The vault protects an item completely and makes it useless. Socket that tool
into a bot and it leaves the vault, and the moment it leaves it is exposed.
The design calls this a deliberate trade; it is also exactly how encrypted
storage behaves in reality.

Symmetric encryption uses one key for both directions: the same secret locks
and unlocks. It is fast — modern processors have instructions for it — and it
is what actually protects bulk data everywhere, including inside systems that
advertise public-key cryptography, which is used to agree the symmetric key and
then gets out of the way.

The key is the whole of the secret. A 256-bit key is a 32-byte number and
nothing more; the algorithm around it is public and the security rests entirely
on nobody else having that number.

## REAL-WORLD COUNTERPART

real — symmetric ciphers, and in practice AES, specified in FIPS 197. It
operates on 128-bit blocks with keys of 128, 192 or 256 bits.

Two things about it are worth knowing and are usually skipped.

First, a cipher alone encrypts one block, and a *mode* decides how a long
message is broken into blocks. The naive mode, ECB, encrypts each block
independently, so identical plaintext blocks produce identical ciphertext
blocks — encrypt a simple image with it and the picture is still visibly
recognisable through the ciphertext. This is the standard demonstration that
"encrypted" is not a single property.

Second, concealment is not protection from alteration. This is what RFC 5116's
authenticated encryption is for, and it is why every current protocol uses a
mode like GCM that produces an authentication tag alongside the ciphertext.

Full-disk encryption is the everyday case, and its limit is the honest lesson:
LUKS, FileVault and BitLocker protect a disk that is powered off. Once the
volume is unlocked so you can use a file, anything running as you can read it —
including anything that should not be running as you.
```

---

### 3.9 `nonce(7)`

```
id:             nonce
section:        7
name:           nonce
canonical:      nonce
gloss:          A value used exactly once, so a message cannot count twice.
status:         real
aliases:        initialization vector, IV, sequence number
seeAlso:        replay-attack(7), randomness(7), aead(7),
                symmetric-encryption(7), provenance-record(5)
reading:        RFC 5116 §3 (nonce requirements); RFC 8446 (TLS 1.3);
                Joux (2006) on GCM nonce reuse
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  randomness(7), replay-attack(7)
hook:           `provenance-record(5)`. Every record in the game carries a
                `nonce` field next to its `timestamp`
                (`../architecture/04-item-provenance.md` §2), and the player
                can read both. They are there for one reason, stated in that
                document: to stop an old valid record being replayed as a new
                event.
misconception:  commonly believed a nonce is simply a random number, so more
                randomness is always better; actually the requirement is
                uniqueness, and a counter satisfies it perfectly while a random
                value only satisfies it probabilistically. In several real
                protocols a counter is the correct choice and a random value is
                the dangerous one.
transfer:       In a browser, open the developer tools' security or connection
                panel and read the negotiated cipher suite — something like
                `TLS_AES_128_GCM_SHA256`. The `GCM` names a mode whose single
                catastrophic failure is using a nonce twice under one key.
                You can now say why every connection derives fresh keys instead
                of reusing them. Cross-platform, no shell required.
verified:       Nonce uniqueness requirement — RFC 5116 §3; GCM nonce reuse
                leaks the authentication subkey and enables forgery — Joux's
                "forbidden attack" (2006), and the same reasoning is recorded
                in this repository at `../architecture/07-transport-security.md`
                §4.3; WEP's 24-bit IV and its repetition are documented in the
                802.11 security literature; TLS 1.3 record nonce construction —
                RFC 8446 §5.3. Checked 2026-07-25.

## DESCRIPTION

Open any provenance record and two fields sit together: a `timestamp` and a
128-bit `nonce`. Neither is decoration. Without them a perfectly valid record
from six months ago could be re-submitted as though it had just happened, and
every signature on it would still check out, because the record genuinely was
signed by the party it names.

A nonce is a **number used once**. Its only requirement is that it never repeat
under the same key or in the same context. That is a weaker requirement than
"random" and a stricter one: randomness is one way to achieve uniqueness, and a
counter is another, usually better one.

## REAL-WORLD COUNTERPART

real — nonces and initialisation vectors, present in essentially every protocol
that encrypts more than one message.

Reuse is not a degradation, it is a cliff. With AES-GCM — the mode a browser
negotiates on most connections — encrypting two different messages under the
same key and the same nonce leaks the authentication subkey, after which an
attacker can forge arbitrary messages that verify correctly. The attack is
known as the forbidden attack and it has been published since 2006. This is the
reason this game's own transport uses a strictly increasing counter per
direction rather than random values: a fresh key per session plus a counter has
no collision risk at all, whereas 96-bit random nonces have a birthday bound
that a long-lived connection can genuinely reach.

The historical case worth knowing is WEP, the original Wi-Fi encryption. Its
initialisation vector was 24 bits, which repeats after a few million frames —
hours on a busy network — and the repeats were enough to recover the key. WEP
used a respected cipher. The nonce was the whole failure.
```

---

### 3.10 `public-key-cryptography(7)`

```
id:             public-key-cryptography
section:        7
name:           public-key cryptography
canonical:      public-key cryptography
gloss:          A matched pair, one half published and one half never shared.
status:         real
aliases:        asymmetric cryptography, public key, keypair, PKI
seeAlso:        private-key(7), digital-signature(7), key-exchange(7),
                symmetric-encryption(7), trust-anchor(7), did(7)
reading:        Diffie & Hellman, "New Directions in Cryptography" (1976);
                RFC 8032 (Ed25519); RFC 7748 (X25519); ssh-keygen(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  symmetric-encryption(7)
hook:           Every provenance record names an `issuerDid` and a `kid`
                (`../architecture/04-item-provenance.md` §3) — a pointer to a
                *public* key that anybody, including you, can fetch and check
                against. The player's own identity resolves to one of these.
misconception:  commonly believed the public key encrypts and the private key
                decrypts, and that is all a keypair does; actually a keypair
                supports two different operations running in opposite
                directions — anyone may encrypt *to* a public key so that only
                the holder can read it, and the holder may sign *with* the
                private key so that anyone can check it. Conflating them is
                why people say "signed with my public key", which is backwards
                and would prove nothing.
transfer:       Run `ssh-keygen -t ed25519` — available on macOS, Linux, and
                Windows 10 and later through the bundled OpenSSH client. It
                writes two files. `id_ed25519` is the private half, mode 600,
                and must never move. `id_ed25519.pub` is one line of about
                eighty characters that is designed to be pasted into other
                people's systems. Open both. The asymmetry is the whole idea.
verified:       The concept's origin — Diffie & Hellman, "New Directions in
                Cryptography", IEEE Transactions on Information Theory, 1976;
                Ed25519 public keys are 32 bytes and signatures 64 bytes —
                RFC 8032; X25519 is a separate key agreement primitive —
                RFC 7748; the two-key-types point is recorded in this
                repository at `../architecture/07-transport-security.md` §4.1.
                Checked 2026-07-25.

## DESCRIPTION

Every record in an item's history names the key that signed it — not a
password, not an account, a key. The `kid` field is a pointer to a *public*
key, and it is public on purpose: you are supposed to fetch it and check the
signature yourself.

A keypair is two mathematically related values. One is published as widely as
possible. The other never leaves the machine that generated it. What makes the
arrangement useful is that the pair supports two separate operations, and they
run in opposite directions:

- **Encrypt to a public key.** Anybody may do it; only the holder of the
  private half can read the result.
- **Sign with a private key.** Only the holder may do it; anybody can check the
  result.

The first solves "I want to send you something secret without meeting you
first". The second solves "I want to prove this came from me". They are
different problems and, in current practice, usually different keys.

## REAL-WORLD COUNTERPART

real — public-key cryptography, published by Diffie and Hellman in 1976 and now
underneath every secure connection in existence.

An Ed25519 public key is 32 bytes and a signature is 64 bytes, regardless of
what was signed. That compactness is one reason this game uses Ed25519, the
other being that it is already the key type AT Protocol identities use, so
there is one key stack rather than two.

Ed25519 keys sign; they do not encrypt. Key agreement uses X25519, a separate
primitive on the same curve family. Converting one to the other is
mathematically possible and is a known footgun, because each algorithm's
security argument assumes the key is used for that algorithm alone — which is
why this game's transport layer attests a *separate* X25519 key rather than
reusing the identity key.

Asymmetric operations are slow relative to symmetric ones, so nothing encrypts
bulk data with them. The universal pattern is to use public-key cryptography to
agree a symmetric key, then encrypt everything with that.
```

---

### 3.11 `digital-signature(7)`

```
id:             digital-signature
section:        7
name:           digital signature
canonical:      digital signature
gloss:          Proof that one specific key approved these exact bytes.
status:         real
aliases:        signature, signing, EdDSA, Ed25519
seeAlso:        public-key-cryptography(7), hash(7), trust-anchor(7),
                offline-verification(7), verify(1), provenance-record(5),
                canonicalization(7), security-properties(7)
reading:        RFC 8032 (Ed25519); RFC 7515 Appendix F (detached JWS);
                git-log(1) --show-signature; gpg(1) --verify
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  public-key-cryptography(7), hash(7)
hook:           `verify <item>`, and the `signature` block visible on every
                provenance record: `alg: EdDSA`, a `kid` naming the key, and
                the signature bytes
                (`../architecture/04-item-provenance.md` §3).
misconception:  commonly believed a signature proves a person signed something;
                actually it proves a *key* was used, which is only as good as
                the binding between that key and a person and as good as the
                key not having been copied. It also proves nothing about
                *when* — a signature has no inherent time, which is why records
                carry timestamps and why timestamping services exist at all.
transfer:       Check a signature on something you already have. On macOS,
                `codesign -dv --verbose=4 /Applications/Safari.app` prints the
                authority chain of a signed application. On Windows, right-
                click any installer, Properties, Digital Signatures. On Linux,
                `gpg --verify` on a signed release, or `git log
                --show-signature` in a repository with signed commits. All
                three are the same operation. Cross-platform.
verified:       Ed25519 signature size (64 bytes) and algorithm — RFC 8032;
                detached signature form — RFC 7515 Appendix F; signing operates
                over a hash of the message, which is why signature size is
                independent of message size; SHA-1's chosen-prefix collision
                and its consequences for signature forgery — "SHA-1 is a
                Shambles" (2020) and the 2017 SHAttered result.
                Checked 2026-07-25.

## DESCRIPTION

Every provenance record ends in a signature block: an algorithm name, a `kid`
identifying which key signed, and the signature bytes themselves. `verify`
checks them. Each one is a claim of the form *the holder of this key approved
exactly these bytes*.

Three things follow, and each one is load-bearing.

**The bytes are exact.** Change one field of a record — a stat, a holder, a
timestamp — and the signature stops matching. This is why the payload is
canonicalized before signing: the same JSON can be written many ways, and a
signature is over bytes, so both sides must agree on the bytes first.

**The key is what is proved, not the person.** A signature says the private half
of this pair was used. Whether that pair belongs to who you think is a
different question with a different answer.

**A signature is not a receipt of truth.** It proves a party attested something.
Whether the thing attested actually happened is a separate matter, and one that
a single signer cannot settle on their own — see `provenance-chain(7)`.

## REAL-WORLD COUNTERPART

real — digital signatures, and specifically Ed25519 as specified in RFC 8032.
This game signs with the same algorithm, in the same detached form (RFC 7515
Appendix F), as a great deal of production infrastructure.

Signing does not operate on the message directly. It operates on a hash of it,
which is why an Ed25519 signature is 64 bytes whether it covers a sentence or a
gigabyte — and why the strength of the hash is part of the strength of the
signature. When SHA-1 fell to chosen-prefix collisions in 2020, the practical
consequence was that a signature over one document could be made to fit a
different one, which is the whole reason for retiring a hash function that
still produces perfectly good-looking output.

The reader already relies on this several times a day. Software updates are
signed. Package repositories are signed. Applications on macOS and Windows are
signed, which is what an operating system checks before it agrees to run
something downloaded from the internet.
```

---

### 3.12 `trust-anchor(7)`

```
id:             trust-anchor
section:        7
name:           trust anchor
canonical:      trust anchor
gloss:          The one thing a chain of proof asks you to believe outright.
status:         real
aliases:        root of trust, trust root, trusted key
seeAlso:        digital-signature(7), certificate-authority(7),
                web-of-trust(7), offline-verification(7), did(7), quorum(7)
reading:        RFC 5280 §6 (path validation begins at a trust anchor);
                RFC 4949 ("trust anchor")
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  digital-signature(7)
hook:           "An unverifiable chain is not recognised"
                (`../architecture/03-server-and-federation.md` §4). The client
                refuses an item whose chain does not check out — but the check
                itself rests on the public keys the client resolved and
                believed. Those keys are the anchor, and nothing beneath them
                is proved.
misconception:  commonly believed cryptography removes the need to trust
                anyone, so a "trustless" system is one where nobody is trusted;
                actually it relocates the trust to a smaller and more specific
                place. Every verification ends at something you decided to
                believe without proof, and the useful question is never "is
                this trustless" but "what exactly am I trusting, how few of
                them are there, and could I check it if I wanted to".
transfer:       Look at your own machine's trust anchors and count them. macOS:
                Keychain Access, System Roots. Windows: run `certmgr.msc`,
                Trusted Root Certification Authorities. Linux: list
                `/etc/ssl/certs`. There will be well over a hundred. You chose
                none of them, any one of them can vouch for any name in the
                world, and your browser will believe it. Cross-platform.
verified:       "Trust anchor" as the defined starting point of certification
                path validation — RFC 5280 §6.1 and RFC 4949; the property that
                any trusted root may issue for any name is inherent to the Web
                PKI's design and is the stated motivation for Certificate
                Transparency (RFC 6962 §1). ⚠ The number of roots in a typical
                store is deliberately not asserted — the transfer test makes
                the reader produce their own. Checked 2026-07-25.

## DESCRIPTION

When `verify` walks an item's chain, each record is checked against the public
key its issuer named. If any step fails, the item is not recognised — not
"suspicious", not recognised.

But notice what the check rests on. Every signature was checked against a key,
and each of those keys was obtained from somewhere and believed. That belief is
not proved by anything in the chain. It is the **trust anchor**: the point at
which the proof stops and a decision starts.

This is not a flaw to be engineered away. Every verification system in
existence has one, because a chain of proof must begin somewhere, and something
at the beginning must be accepted without a prior proof. What a good system does
is make the anchor small, specific, few, and inspectable.

The federation's answer is that the anchor is a DID's key. It is not a company,
not a hostname, and not a server's assurance — and any player who wants to can
resolve the same identifier and check the same signatures independently.

## REAL-WORLD COUNTERPART

real — trust anchors, and the term is used exactly this way in the standards.
RFC 5280, which defines how certificates are validated, begins path validation
at a trust anchor by definition: the algorithm cannot start without one.

The reader's own machine ships with a large set of them. Every root certificate
in the operating system's store is a trust anchor, believed without proof,
chosen by a vendor rather than by the user, and — this is the part worth
sitting with — **any single one of them can vouch for any name on the internet**.
There is no rule in the design confining a Dutch authority to Dutch names or a
government authority to that government's domains.

That is the honest shape of trust on the internet, and it is why the useful
question is never whether a system is trustless. It is: how many things am I
trusting, who chose them, and what happens if one of them is wrong.
```

---

### 3.13 `certificate-authority(7)`

```
id:             certificate-authority
section:        7
name:           certificate authority
canonical:      certificate authority
gloss:          An organisation your machine believes when it vouches for a name.
status:         real
aliases:        CA, root CA, chain of trust, PKI, the padlock
seeAlso:        certificate(7), trust-anchor(7), web-of-trust(7),
                digital-signature(7), revocation(7), did(7)
reading:        RFC 5280 (X.509 and path validation); RFC 6962 and RFC 9162
                (Certificate Transparency); the Black Tulip report on the
                DigiNotar breach
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  trust-anchor(7), certificate(7)
hook:           Its deliberate absence. The federation authenticates DIDs, not
                hostnames, and `../architecture/07-transport-security.md` §3
                gives the reason in one line: no certificate authority fits a
                trust model whose first invariant is that there is no central
                authority (**I15**). Nothing in this game ever asks whether a
                server's certificate is valid; it asks whether a DID's
                signature checks out.
misconception:  commonly believed the padlock means the site is safe or
                legitimate; actually it means one of the hundred-plus
                authorities your machine already trusts has stated that this
                key belongs to this hostname, and nothing else — not that the
                operator is honest, not that the content is true, not that the
                company is the one whose name it resembles.
transfer:       Click the padlock on any site, open the certificate details,
                and read the chain from the bottom up: the site's own
                certificate, one or two intermediates, then a root you have
                never heard of and never chose. Note what the certificate
                actually asserts — a hostname and a key, an issue date and an
                expiry — and note that "this business is legitimate" is not
                among the fields. Cross-platform, browser only.
verified:       Certificate contents and path validation — RFC 5280;
                Certificate Transparency and its motivation — RFC 6962 (2013)
                and RFC 9162 (CT 2.0); DigiNotar was compromised in 2011 and
                fraudulent Google certificates were used against users at
                298,140 unique IP addresses, roughly 95% of them in Iran, per
                the Black Tulip investigation; the Haarlem court declared
                DigiNotar bankrupt on 20 September 2011.
                ⚠ The exact number of roots in a trust store is not asserted.
                Checked 2026-07-25.

## DESCRIPTION

This game has no certificate authorities, and the absence is a design decision
rather than an omission.

The federation has no central authority by construction — that is Invariant
I15 — and self-hosted servers are adversarial by design. Anchoring identity in
the certificate system would reintroduce exactly the single point of trust the
rest of the architecture spent its effort removing. A certificate says "you
reached this hostname". It says nothing about *which operator* is behind it,
and a home server's address is whatever its operator has this month. So the
game keys every trust decision off a DID instead, and asks about signatures
rather than about names.

Which leaves the question the absence raises: what is a certificate authority,
and why does everything outside this game use one?

## REAL-WORLD COUNTERPART

real — the Web PKI, defined by RFC 5280, and the thing behind every padlock.

A certificate is a signed statement binding a name to a public key. An authority
is an organisation whose own key your machine already trusts, so when it signs
such a statement your machine accepts it. Chains exist so the root's key can
stay offline: the root signs an intermediate, the intermediate signs the site.

It solves a real problem — you can talk securely to a site you have never
contacted before — and it has one structural weakness that everybody in the
field knows: any trusted authority can issue a certificate for any name.

In 2011 that stopped being theoretical. Attackers compromised the Dutch
authority DigiNotar and issued working certificates for Google. They were used
to intercept traffic from nearly three hundred thousand distinct addresses,
about 95% of them in Iran. Every browser then removed DigiNotar entirely, and
the company was bankrupt within weeks. The lasting response is Certificate
Transparency: every certificate issued is published to public append-only logs,
so misissuance is detectable afterwards even when it cannot be prevented — the
same argument, and the same shape of solution, as this game's provenance chain.
```

---

### 3.14 `offline-verification(7)`

```
id:             offline-verification
section:        7
name:           offline verification
canonical:      offline verification
gloss:          Checking a proof yourself rather than trusting the report.
status:         real
aliases:        independent verification, client-side verification,
                verifiability
seeAlso:        verify(1), digital-signature(7), trust-anchor(7),
                item-history(1), provenance-chain(7), checksum(7)
reading:        gpg(1) --verify; git-verify-commit(1); RFC 6962 §1
                (why a log must be checkable by anyone)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  digital-signature(7)
hook:           `verify(1)` is the only exit status this client computes for
                itself (`../client/00-client-overview.md` §1.1,
                `../client/04-terminology-and-education.md` §3.5). Every other
                number on screen is the server's, rendered as received. That
                one exception is not a UI quirk; it is the architecture's
                answer to who is allowed to be believed.
misconception:  commonly believed that if the server says an item is genuine
                then it is genuine, because the server is the authority;
                actually the server is a party to the dispute — a self-hosted
                home server is adversarial by design — so "verified" coming
                from it is a claim, whereas a signature the player's own machine
                checked against a public key is a proof. The two words look
                identical on screen and are not the same kind of thing.
transfer:       Verify one signature yourself, on your own machine, instead of
                trusting a tick on a page: `gpg --verify file.sig file`, then
                `echo $?` to see the status. The checking happened on your
                computer, against a key you obtained, and that is the entire
                difference. To see what it costs, run `openssl speed ed25519`
                — signature verification is cheap enough that re-checking a
                whole chain is not a sacrifice. Assumes a Unix shell with
                GnuPG and OpenSSL; Windows via Gpg4win. See ED-8.
verified:       Client-side re-verification against DID public keys without
                trusting the server's UI is a stated requirement —
                `../architecture/04-item-provenance.md` §6.2; the verifier does
                no I/O and reads no clock — `protocol` `ProvenanceChainVerifier`
                and `../architecture/04-item-provenance.md` §8; "anybody can
                check them without trusting the operator" is Certificate
                Transparency's stated security argument — RFC 6962 §1.
                ⚠ Verification cost is hardware-dependent and is left to the
                reader's own benchmark. Checked 2026-07-25.

## DESCRIPTION

Almost everything on your screen is the server's opinion. Your compute, your
balance, whether a gate lets you through — all of it is computed elsewhere and
rendered here as received, because a client that decided any of it would be a
client a cheat could edit.

`verify` is the exception, and it runs the other way. Your client fetches the
records, resolves the issuers' public keys, and checks every signature and every
chain link itself. It does not ask the server whether the chain is good. It
works it out.

That is worth more than an assurance, and the reason is not cryptographic
sophistication — it is that the server is an interested party. In this
federation anybody can run one, and a dishonest operator has every reason to
report that the item they minted is fine. An assurance from them is a claim. A
signature you checked yourself is a proof, and it stays a proof if they lie, go
offline, or are replaced.

## REAL-WORLD COUNTERPART

real — independent verification, and it is the whole design argument behind
several systems the reader already depends on.

It is what `gpg --verify` is for. It is what `git verify-commit` is for. It is
the entire security argument of Certificate Transparency and of Sigstore's Rekor
log: the operator of a public log is not trusted, because anybody can fetch the
log and check it, and a log that lies is a log that can be caught by anyone
paying attention.

The difference in practice is small in effort and total in kind. Downloading a
file and reading a green tick on the download page tells you the page says the
file is fine. Downloading the file, the signature and the signing key and
checking it on your own machine tells you the file is fine — even if the page
is lying, even if the page is not the page you thought it was.

The remaining dependency is honest and unavoidable: you still had to get the
right public key. That is `trust-anchor(7)`, and no amount of local checking
removes it.
```

---

### 3.15 `replay-attack(7)`

```
id:             replay-attack
section:        7
name:           replay attack
canonical:      replay attack
gloss:          Re-sending a genuine message so it counts a second time.
status:         real
aliases:        replay, message replay, freshness
seeAlso:        nonce(7), authentication(7), digital-signature(7),
                security-properties(7), provenance-record(5)
reading:        RFC 6238 (TOTP — time as a freshness mechanism); RFC 8446
                §2 (TLS 1.3 and 0-RTT replay); RFC 4949 ("replay attack")
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  authentication(7)
hook:           The `nonce` and `timestamp` fields on every provenance record
                exist for exactly this and nothing else
                (`../architecture/04-item-provenance.md` §2). The transport
                document puts it more bluntly: a "collect yield" frame replayed
                a thousand times is a thousand collections, every byte of it
                perfectly authentic
                (`../architecture/07-transport-security.md` §1).
misconception:  commonly believed that a message which is authentic and
                unaltered is therefore safe to act on; actually authenticity
                says nothing about freshness. The attacker forges nothing,
                breaks nothing and needs no key — they capture a message that
                was genuine when it was sent and send it again.
transfer:       Watch the two-factor code on your phone change. RFC 6238 sets
                the default step at thirty seconds, and that interval is the
                entire replay defence: a code that never changed would be a
                password with extra steps. Then notice the same idea in a car
                key fob's rolling code and in a card terminal's per-transaction
                counter. Platform-independent.
verified:       Nonce and timestamp are present specifically to prevent replay
                — `../architecture/04-item-provenance.md` §2; TOTP's default
                time step X = 30 seconds — RFC 6238 §4.1; TLS 1.3's 0-RTT data
                is explicitly not replay-protected and the specification says
                so — RFC 8446 §2.3 and Appendix E.5. Checked 2026-07-25.

## DESCRIPTION

The most economical attack in the game requires forging nothing.

Every provenance record is signed, and a signature proves the issuer approved
those exact bytes. It does not prove *when*. Capture a valid record — a grant, a
transfer, a collection — hold it, and submit it again, and every check that
looks only at the signature will pass, because the signature is genuinely valid.

Hence the two fields sitting next to each other on every record: a `timestamp`,
which bounds how old a record may be and still be acted on, and a `nonce`, a
128-bit value that has been used once and will never be accepted again. A
verifier that checks the signature and skips these two has built the expensive
half of the defence and left the cheap half out.

## REAL-WORLD COUNTERPART

real — replay attacks, one of the oldest attack classes there is and one of the
easiest to leave a hole for.

The defences are always the same three, alone or combined: something that
changes and is remembered (a nonce), something that expires (a timestamp or a
window), or something that counts (a sequence number). A two-factor
authenticator code is the everyday example — RFC 6238 divides time into
thirty-second steps and the code is a function of the step, so a captured code
is worthless almost immediately.

The most instructive modern case is TLS 1.3's zero-round-trip mode, which lets a
client send data before the handshake completes and is therefore *not* replay-
protected. The specification says so explicitly and describes what applications
must do about it. That is what maturity looks like in this field: not a claim
that the property was achieved, but a precise statement of where it was traded
away and for what.
```

---

### 3.16 `roll-your-own(7)`

```
id:             roll-your-own
section:        7
name:           roll your own crypto
canonical:      roll your own crypto
gloss:          Why a reviewed implementation beats a fresh one, every time.
status:         real
aliases:        DIY crypto, home-made crypto, implementation risk
seeAlso:        kerckhoffs-principle(7), nonce(7), randomness(7),
                constant-time-comparison(7), supply-chain-attack(7),
                zero-day(1)
reading:        CVE-2008-0166 (the Debian OpenSSL entropy bug); RFC 9106;
                the security considerations section of any current RFC
notes:          The paragraph about this project's own transport layer must be
                kept accurate. It reflects `../architecture/07-transport-
                security.md` §6 T-1 as of revision 1; if that question is
                resolved, this page changes with it. Do not soften it in the
                meantime — the honesty is the lesson.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  kerckhoffs-principle(7)
hook:           `zero-day(1)` and `overflow-kit(1)`. Every exploit in this game
                is an implementation failure in something whose design was
                fine — that is what a zero-day is
                (`../design/06-intrusion-tools.md` §1) — and the same is true
                of nearly every real cryptographic failure.
misconception:  commonly believed cryptography fails when somebody breaks the
                mathematics, so choosing a strong algorithm is the decision
                that matters; actually the algorithms are the strongest part of
                the stack and almost every real failure is an implementation
                one — a reused nonce, a bad random source, a comparison that
                leaks timing, a certificate check that was never wired up.
transfer:       Read the summary of CVE-2008-0166. Nothing about RSA or the SSH
                protocol was broken. A two-line change to a random-number source
                in one distribution's OpenSSL package reduced the entire key
                space to 32,767 possibilities, and stayed that way for about
                twenty months. Then, next time a product says it "uses AES-256",
                ask who wrote the code around it. Platform-independent.
verified:       CVE-2008-0166 removed entropy seeding from Debian's OpenSSL,
                leaving process ID as the only varying input and 32,767
                possible keys per architecture/type/size, affecting keys
                generated from September 2006 to 13 May 2008 — Debian DSA-1571
                and the CVE record; this project's transport layer is a
                hand-rolled Noise-IK-shaped construction awaiting cryptographer
                review — `../architecture/07-transport-security.md` §6 T-1 and
                the repository's own CLAUDE.md; Ed25519 has been in java.base
                since JDK 15 and the protocol module carries no third-party
                crypto dependency — `protocol/.../crypto/Ed25519Signatures`.
                Checked 2026-07-25.

## DESCRIPTION

A zero-day is not a broken algorithm. It is an unfixed mistake in somebody's
code, in a product whose design was perfectly sound. That is what the tool
represents in this game, and it is also, almost without exception, how
cryptography fails in the world.

The rule practitioners state as "do not roll your own crypto" is often heard as
"do not invent an algorithm", which is true and rarely tempting. The sharper
version is: **do not write a new implementation of a correct algorithm either.**
The failures live in the joins — the nonce that repeats, the key derived from a
predictable source, the comparison that returns early and leaks how many bytes
matched, the certificate whose validity nobody checked.

This is also why the failures are so hard to spot. Wrong cryptography produces
output that looks exactly like right cryptography. A test suite passes. Two
endpoints agree. Nothing is slow. There is no symptom until somebody is looking
for one on purpose.

This game's own record is mixed, and it is worth saying so on the page rather
than in a footnote. The provenance layer uses only algorithms the platform
already ships, with no third-party cryptography at all. The transport layer is
a hand-written implementation of a well-understood handshake pattern — safer
than inventing one, and still not the same as reviewed code. Its own
documentation says a cryptographer must read it before it protects anything
real, and until that happens the honest label is unreviewed.

## REAL-WORLD COUNTERPART

real — this is standing professional advice, and it has a canonical example.

In 2006 a change to Debian's OpenSSL package removed the code that seeded the
random number generator, on the reasonable-looking grounds that a static
analysis tool had flagged it. Every key generated on affected systems for the
next twenty months came from a pool of 32,767 possibilities. RSA was not broken.
SSH was not broken. OpenSSL was not broken. Two lines of an integration were,
and every SSH and TLS key produced in that window had to be replaced.

The pattern generalises. Use the platform's implementation. Use the standard
mode. Take the parameters from a published recommendation rather than deriving
them. And when writing anything new in this area, treat "it works" as the
starting point of review rather than the end of it, because in this one field
working correctly and working securely are not the same observation.
```

---

### 3.17 `verify(1)`

```
id:             verify
section:        1
name:           verify
canonical:      verify
gloss:          Re-checks an item's signed history on your own machine.
status:         real, simplified
aliases:        check, validate
seeAlso:        item-history(1), provenance-record(5), provenance-chain(7),
                digital-signature(7), offline-verification(7),
                trust-anchor(7), did(7)
reading:        gpg(1) --verify; git-verify-commit(1); RFC 7515 Appendix F
notes:          This page is the player's first meeting with the idea that one
                number on screen is theirs and every other is the server's.
                Translators: "recognised" here is a status word from
                `../architecture/03-server-and-federation.md` §4, not a
                synonym for "familiar".
revision:       1

--- curriculum only, stripped before shipping ---

domain:         06
stage:          adversarial
prerequisites:  digital-signature(7), offline-verification(7), exit-status(7)
hook:           The command itself, in the `storage` window and the palette
                (`../client/04-terminology-and-education.md` §3.10), and the
                moment an item a stranger traded you comes back exit 1.
misconception:  commonly believed "verified" is a property a file has;
                actually verification is an act somebody performed, at a
                moment, against a key they held. The same item is verified for
                you and unverified for me, and the word without those three
                details is marketing rather than information.
transfer:       Run the real equivalent once: `gpg --verify` on a signed
                release, then `echo $?`. Zero means the signature is good. The
                exit status is the answer — the human-readable line above it is
                a courtesy — which is exactly why scripts check `$?` and not
                the text. Assumes a Unix shell with GnuPG; Windows via
                Gpg4win. See ED-8.
simplified:     Real verification tools return more than pass and fail. `gpg
                --verify` distinguishes a good signature from an untrusted key,
                a good signature from an expired key, a signature it has no
                public key for, and an actually bad signature — four outcomes
                with four different meanings, of which only one is fraud. This
                command collapses all of them into recognised or not
                recognised, because the game can always resolve an issuer's key
                through its DID. Key distribution is the genuinely hard part of
                verification everywhere else, and the game removes it.
verified:       Client-side chain walking against DID keys, offline —
                `../architecture/04-item-provenance.md` §6.2, §7; non-
                recognition of unverifiable chains —
                `../architecture/03-server-and-federation.md` §4; this is the
                only client-computed exit status —
                `../client/00-client-overview.md` §1.1 and
                `../client/04-terminology-and-education.md` §3.5; `gpg
                --verify` returns 0 on a good signature and non-zero otherwise,
                and distinguishes the four cases above — gpg(1).
                Checked 2026-07-25.

## SYNOPSIS

       verify [-n] [-v] [--] <item>

## DESCRIPTION

Walks an item's provenance chain from its current record back to genesis,
checking every signature against the key its issuer named and every link
against the hash of the record before it.

Your client does this itself. It does not ask the server whether the chain is
good — it fetches the records, resolves the keys, and works it out. This is the
only status in the entire client that is not the server's answer rendered back
to you.

A chain that fails any step means the item is **not recognised**. Not
suspicious, not provisional: not recognised, which is how a cheating server's
fabricated items become worthless everywhere else in the federation.

Use `item-history` to read the same chain as events. Use `verify` to find out
whether to believe it.

## OPTIONS

       -h, --help      Print SYNOPSIS and OPTIONS.
       --explain       Print this DESCRIPTION and exit without running.
       -n, --dry-run   Print the checks that would run. Accepted for
                       consistency; this command sends nothing in any case,
                       because verification happens here.
       -v, --verbose   Name each record as it is checked, and on failure name
                       the record and the step that failed.
       --              End of options. Needed when an item id begins with '-'.

## EXIT STATUS

       0    Every signature and every link checked out.
       1    Verification failed. The item is not recognised. -v says where.
       2    Usage: unknown flag or missing argument.
       69   Records for this chain are not held locally and the server could
            not be reached to fetch them. Nothing was verified either way.

## REAL-WORLD COUNTERPART

real, simplified — signature verification. The two commands to know are
`gpg --verify` for signed files and releases, and `git verify-commit` for signed
commits.

Both work the way this one does and report the way this one does: the exit
status is the result, and zero means good. That convention is why automation
checks `$?` rather than reading the message, and it is worth carrying out of
this game, because the most common way real verification fails in practice is a
script that printed the output and ignored the status.

The records here are detached JWS (RFC 7515 Appendix F) over canonicalized
JSON, signed Ed25519. Detached means the signature travels separately from the
payload, so the payload stays readable while remaining tamper-evident — the same
arrangement as a `.sig` file sitting next to a download.

## CAVEATS

Real verification tools return more than pass and fail. `gpg --verify`
distinguishes a good signature from a key you do not trust, a good signature
from an expired key, a signature whose public key you simply do not have, and a
signature that is genuinely bad. Four outcomes, four meanings, one of which is
fraud and three of which are ordinary. This command reports two, because the
game can always resolve an issuer's key through its DID.

That is the shortcut, and it is a real one. **Getting the right public key is
the hard part of verification everywhere outside this game.** A signature check
against the wrong key is not a weaker proof; it is no proof at all. See
`trust-anchor(7)`.

Verification also tells you the chain is consistent, not that the events
happened. What a signed chain proves is what a set of keys attested. See
`provenance-chain(7)`, which carries that distinction and the reason the
federation needs a quorum rather than an authority.
```

---

## 4. Notes for the writer of the remaining twenty-one

Four things this pass learned that are cheaper to state than to rediscover.

**The `misconception` field is easy here and that is a trap.** Cryptography attracts confident approximate beliefs, so a plausible-sounding one can be written for any entry in thirty seconds. §8.2 requires two of three tests. In this domain the productive test is almost always **derivation**: the belief follows from something true, applied one step too far. "Encryption conceals data" is true, so "hashing conceals data" is a reasonable inference; "a signature proves who sent it" is true, so "a signature proves a person" is a reasonable inference. Entries whose misconception was found by derivation are the ones that landed; entries where a misconception had to be invented are the ones that should probably be a paragraph inside another entry.

**Give the reader a command that makes them produce the number.** Three entries here could not honestly assert a figure — how many root certificates a machine trusts, how fast a signature verifies, how many hashes a graphics card manages. In each case handing the reader `certmgr.msc`, `openssl speed ed25519` or a benchmark table beat asserting a number that would rot. This is a better pattern than a hedge and it satisfies §2.7 more honestly than a range would.

**Every entry in this domain trips ED-1.** None of these terms is in `../design/glossary.md` — not "salt", not "nonce", not "trust anchor", not even "Rainbow Table", which exists in `../design/06-intrusion-tools.md` and in `../client/04-terminology-and-education.md` §2.6 but never in the glossary. Under `../client/04-terminology-and-education.md` §4.10's orphan check as currently written, every one of these pages fails or passes trivially by omitting `canonical:`. This domain is the largest single block of affected entries, which makes ED-1 a blocker for this document specifically. **CT-4.**

**Two entries carry mandatory `notes:` lines and both are written above** — `roll-your-own(7)` (because the self-disclosure must track `../architecture/07-transport-security.md` TS-1) and `verify(1)` (because "recognised" is a status word, not an ordinary adjective, and a translator will get it wrong). `rainbow-table(1)`, when written, needs a third, from the §2.15 homonym row.

---

## 5. What this domain deliberately does not teach

`00-curriculum-and-method.md` §7.3: an entry with no hook is not written, however interesting, because an unused entry is an unread entry. This domain attracts more of these than any other, so the cuts are stated rather than left implicit.

**No mathematics.** Not modular arithmetic, not elliptic curves, not the discrete logarithm problem, not why any of it is hard. §2.2 of the contract rules out mathematics beyond arithmetic for this reader, and — more importantly — knowing why factoring is hard changes nothing a player or a person does. Knowing that a signature proves a key rather than a person changes a great deal. The one place a curve is named at all is `public-key-cryptography(7)`, where "Ed25519 signs and X25519 agrees keys, and converting between them is a footgun" is an operational fact rather than a mathematical one.

**No algorithm internals.** Not Merkle–Damgård, not the sponge construction, not AES round functions, not the birthday bound as an equation. §2.4's "jargon-dump" column uses exactly this material as its example of accurate, inert prose. What the player needs from SHA-256 is that it is 256 bits, deterministic, one-way and collision-resistant, and that "broken" means collisions can be constructed rather than that output stopped appearing.

**No exploitation technique.** This is the dual-use line the contract flagged as **ED-9**, and this document proposes the line rather than waiting for it: **entries explain what an attack class is and what defeats it; they never explain how to carry one out.** `injection(7)` says data can be read as instructions and that the defence is never to read it that way; it contains no payload. `machine-in-the-middle(7)` says what the position buys an attacker and why authenticated key exchange denies it; it names no tool. `password-hashing(7)` names Argon2id and OWASP's parameters — defensive information — and no cracking software or wordlist. The test used throughout: **would this sentence help a defender more than an attacker?** If not, it is out. This is consistent with §4.4's ban on citing offensive-tooling walkthroughs, extended from citations to the game's own prose. Proposed as the answer to ED-9 in **CT-5**.

**No cryptocurrency.** Ethecoin has a public ledger and the ledger is real, but consensus mechanisms, proof of work, wallets, smart contracts and tokenomics are `07`'s at most and out of scope at best. The one genuinely transferable idea — a public append-only record that anybody can audit — is `07`'s `append-only-log(7)`, and the game's own version of it is `provenance-chain(7)`.

**No post-quantum cryptography.** It is real, it is coming, and it has no surface in this game. `../architecture/07-transport-security.md` T-4 records the decision to be conscious about it later. Adding an entry now would be writing about a thing the player will never see, at the cost of one more page nobody opens.

**No legal or regulatory material about encryption.** Export controls, lawful access, key escrow debates. Genuinely important, entirely absent from the game's surfaces, and impossible to write in one page without taking a position the game has no business taking. Note that this is *not* the same as the hack-back page, which the game does owe the player because the game models a counter-attack — that page is real, mandatory, and currently unowned (§1.5).

---

## 6. Open questions

Prefix **`CT-`** (cryptography and trust), unused in `../design/15-open-questions.md` and distinct from the existing `T-` (client `04` teaching, and separately architecture `07` transport — an existing collision, noted below). Log in `../design/15-open-questions.md` §2 if this doc set is adopted.

- **CT-1: ✅ RESOLVED 2026-07-25 — the material became `08-detection-and-defence.md`.** Option (a) was taken, as recommended: a document of its own rather than folding the material into `03` or widening this one. It writes 20 entries from a 42-concept inventory, and it carries the two **mandatory** obligations this question flagged — `../client/04` §2.7's defender's answer on `log-scrubber(1)`, and §2.8's statement that hacking back is illegal, which `hack-back(7)` now carries in full with statutory citations. `cross-view-detection(7)` went to `03` exactly as recommended, and turned out to have been written there already, so this question had over-stated its own orphan list by one. The numbering worked because R8 was checked in the direction that mattered: **no `prerequisites` field in `01`–`07` names any detection concept**, so `08` could sit above everything without inverting the ladder. ⚠ One follow-up is `08`'s **DF-9** and it is sharper than anything here: `hack-back(7)` states a **legal** position, which is the one kind of claim in this doc set where confident error could harm a player. It needs a reader with actual legal knowledge, or an explicit note on the page saying it has not had one.
- **CT-2: six prerequisite edges point at entries that do not exist yet, and one of them has no clear owner.** `byte(7)`, `storage-tiers(7)`, `permissions(7)`, `shell(7)` and `exit-status(7)` are safely someone's. `whoami(1)` is not: it is a command (which suggests `04`), it is about users and identity on a machine (which suggests `03`), and it is the `identity` window's Unix analogue (which suggests `07`, whose entries this domain may not depend on under R8). `authentication(7)` currently has no other honest prerequisite, and if `whoami(1)` lands in `07` then `authentication(7)` has none at all and fails graph check 4. **Settle the owner of `whoami(1)` and `id(1)` before `07` is drafted.**
- **CT-3: ✅ WITHDRAWN — the `adversarial` stage is not over-subscribed, and this document's alarm was a unit error.** The original finding counted this document's **inventory** rows (20) against `00` §6.2's budget, which counts **written** entries. `07-distributed-systems-and-identity.md` **DS-4** made the identical mistake independently, and the two agreeing made it look confirmed. Counted correctly: this document writes **9** at `adversarial` and `07` writes 15, the whole curriculum sits at 26 against a 25–40 budget, and the stage is comfortably inside it. The three entries this question proposed demoting — `public-key-cryptography(7)`, `digital-signature(7)` and `trust-anchor(7)` — should therefore **stay where they are**; moving them would have been a stage assignment chosen to satisfy a number. `00` §6.2 now states the counting basis explicitly and publishes the measured distribution.
- **CT-4: ED-1 blocks this document specifically.** Every one of these thirty-eight concepts is a real computing concept the game surfaces with no game-design existence, so none appears in `../design/glossary.md`. Under `../client/04-terminology-and-education.md` §4.10's orphan check they either fail or pass trivially by omitting `canonical:`. The contract's proposed amendment — make `canonical:` unconditionally required and give the check a third accepted source generated from `docs/education/` — resolves it. This domain is the largest single block of affected entries, so **ED-1 should be decided before this document's entries become term files**, not before the first term file generally.
- **CT-5: adopt §5's dual-use rule as the answer to ED-9.** Proposed rule, stated once for the whole doc set: *entries explain what an attack class is and what defeats it; they never explain how to carry one out; the test is whether a sentence helps a defender more than an attacker.* It is checkable at review time, it lets `injection(7)` and `machine-in-the-middle(7)` be written without hedging, and it extends `../client/04-terminology-and-education.md` §4.4's existing ban on offensive-tooling citations from citations to prose. If a different line is wanted, it must be drawn before `machine-in-the-middle(7)`, `injection(7)` and `credential-reuse(7)` are written, because it changes how all three are worded.
- **CT-6: should `verify(1)`'s failure output name the failing step, and does that leak?** `-v` as written names the record and the check that failed, which is exactly what a real tool does and is the more educational behaviour. It is also, arguably, telling a player something about how a forged chain was constructed. The counter-argument is strong — the verification algorithm is public (`../architecture/04-item-provenance.md` §7), so Kerckhoffs applies and hiding the step buys nothing — but this is a game-balance question rather than a content one and it belongs to whoever owns the item-history UI. **Raise against `../architecture/04-item-provenance.md` §8's unstarted client item.**
- **CT-7: the `roll-your-own(7)` self-disclosure needs an owner.** The page currently states that this project's transport layer is unreviewed, because that is true and because the honesty is the lesson. If `../architecture/07-transport-security.md` TS-1 is resolved — a cryptographer reviews it, or a reviewed Noise library replaces it — the page becomes false and nothing in the current process would catch that. This is `../client/04-terminology-and-education.md` §6 **T-11** (semantic drift) with a named instance, and the `hook`/`notes` fields are the hook for it. **Whoever closes architecture TS-1 must bump this entry's `revision`.**
- **CT-8: should `security-properties(7)` be the first entry a player is offered in this domain, ahead of `hash(7)`?** The prerequisite graph says `threat-model(7)` → `security-properties(7)`, and `hash(7)` sits on `byte(7)` in a different branch, so the offering order is genuinely undetermined. There is a case for leading with `security-properties(7)` — it reframes everything after it — and a case against, since the player's first contact is a 64-character hash in `item-history`, and answering the question in front of them is what §4.8.3 rule 2 requires. **Decide with playtest data, not in the abstract**, and note it interacts with **ED-5** (whether `misconception` is ever rendered), because a page that opens by reframing what the reader believes is precisely the page that could read as condescending.
- **CT-9: `aead(7)` has the weakest hook in this domain and may not deserve an entry.** Its game surface is a caveat on `vault(7)` and a transport layer the player never sees. It is genuinely important — it is the answer to the misconception in `symmetric-encryption(7)` — but §7.3 is unambiguous that a concept with no trigger lives only in the index. **Options:** keep it and accept it is index-only; fold its content into `symmetric-encryption(7)`'s body as a paragraph and delete the entry; or find it a real surface. Recommend folding unless a surface appears. Listed here rather than acted on because the same question will arise in every domain and the doc set should answer it once.
- **CT-10: an id collision worth cleaning up before either prefix is logged.** `T-` currently means three different things: `../client/04-terminology-and-education.md` §6's teaching questions, `../architecture/07-transport-security.md` §6's transport questions, and the question `../design/15-open-questions.md` labels T-4. This document cites `architecture` TS-1, TS-3 and TS-4 and `client` T-11 and T-12 by name, and a reader cannot tell them apart without the path. `../education/00-curriculum-and-method.md` ED-6 already flags one instance. **Renaming the architecture set to `TS-` costs one search-and-replace across two files and removes a permanent ambiguity.**
