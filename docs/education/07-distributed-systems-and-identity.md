# 06 — Distributed systems — many machines, no boss

**Status:** ⚠️ **[PROPOSAL]** — the *curriculum* is first-pass design, written against the contract in `00-curriculum-and-method.md` §3. What it describes is not: the game's federation, provenance chain and validator quorum are **Established** (`../architecture/03`, `04`, `05` — Tech Chats 1 and 2), implemented, and covered by integration tests. This is the one domain where the teaching material and the shipping code are the same decisions, which is why it can be unusually concrete. Every factual claim carries a source in its `verified:` field; anything not verified is marked ⚠ inline and listed in §2.4.
**Depends on:** `00-curriculum-and-method.md` (the entry template §3, the status vocabulary §4, man-section assignment §5, the four stages §6, the coverage rules §7) — **this document does not re-specify any of it**; `../client/04-terminology-and-education.md` §1 (the principle), §2.13 (the identity/provenance/federation mapping table), §2.15 (homonyms), §3.5 (exit statuses), §4.8 (the file format); `../design/glossary.md` (canonical terms); `../architecture/03-server-and-federation.md`, `../architecture/04-item-provenance.md`, `../architecture/05-validator-quorum.md`, `../architecture/08-discovery-and-sync.md`; `../design/13-multiplayer-and-federation-play.md`
**Depended on by:** `client/src/main/resources/terms/en/7/**` for the 40 concepts inventoried in §2

---

## 1. What this domain is

### 1.1 The domain, in one paragraph

A **distributed system** is two or more machines cooperating on one job with no shared memory, no shared clock, and a network between them that is allowed to delay, reorder, duplicate and drop messages. That definition sounds like a small extension of ordinary programming and is not. On one machine, a function either runs or the whole machine stops; across a network, a request can succeed at the far end and lose the answer on the way back, and the caller has no way to tell that apart from the request never arriving. Everything in this domain — clocks, timeouts, quorums, consensus, the whole apparatus — is machinery built to survive that one gap in knowledge.

The domain divides into three questions, and the inventory in §2 is ordered by them:

1. **Why is this hard at all?** Partial failure, timeouts, the unreliability of clocks, the physical floor on latency.
2. **How do copies of the same data stay usable?** Replication, staleness, consistency models, the CAP theorem, conflict resolution and its failure modes.
3. **What do you do when some of the machines are lying?** Byzantine faults, quorums, the 3f+1 threshold, equivocation, double-spending, Sybil attacks, and the sanctions available to a system with no authority.

### 1.2 Why a player of *this* game benefits

Two reasons, and the second is the stronger.

**The player is inside one.** The client is a machine; the home server is another machine; a federated peer is a third. `../client/00-client-overview.md` §1.1 puts a hard rule on this — the client is authoritative over nothing except window positions and one computation (`verify(1)`), and everything else on screen is a value that arrived over a network and may be stale, pending, or absent. That is not flavour. It produces the exit-status table in `../client/04-terminology-and-education.md` §3.5, where `1` (the server refused) and `69` (the server could not be reached) and `75` (sent, no answer yet, retry is safe) are three different numbers on purpose. A player who understands why those are three numbers and not one has understood partial failure, which is the hardest idea in the domain, from a surface they meet in the first ten minutes.

**The project made these decisions in the open, and wrote down why.** `../architecture/08-discovery-and-sync.md` §0 is the clearest case in the repository. Somebody asked for the obvious feature — servers should sync the newest state — and it was refused, in writing, because "newest wins" is last-writer-wins replication and last-writer-wins hands a cheating server a button that says *I win*: set your clock to the year 3000 and your version of who owns what overwrites everybody's. What replaced it converges on **cryptographic validity rather than recency**, and splits data three ways by how adversarial it is. That is a real distributed-systems design argument, made for real reasons, with the losing option named and buried. A player living inside the result can be shown the argument. Very little teaching material anywhere is this honest, because very little of it is attached to a system whose designers had to actually choose.

### 1.3 Where the player meets it

| Surface | Cited at | Concepts met there |
|---|---|---|
| Exit statuses `1` / `69` / `75` | `../client/04` §3.5 | `partial-failure`, `timeout`, `idempotence`, `distributed-system` |
| The pending and stale authority states on any rendered value | `../client/01-visual-language.md` §2.2.8 | `stale-read`, `replication`, `liveness` |
| `verify(1)` — the client checking a chain itself rather than believing the server | `../client/04` §3.10; `../architecture/04` §6.2 | `distributed-system`, `fork`, `consensus` |
| Your home server, and joining somebody else's | `../architecture/03` §1; `../design/13` §4 | `home-server`, `federation`, `sybil-attack` |
| The federation directory | `../architecture/03` §2 | `federation-directory`, `peer-discovery`, `gossip-protocol` |
| A cross-server duel, adjudicated by 5 of 7 sampled validators | `../architecture/05` §1, §5 | `quorum`, `bft-threshold`, `byzantine-fault`, `validator-quorum`, `consensus` |
| A validator's reputation moving after a round | `../architecture/05` §3 | `validator-reputation`, `aimd`, `cold-start`, `equivocation` |
| An item whose chain does not verify — "not recognised", not "suspicious" | `../architecture/04` §7; `../architecture/03` §4 | `fork`, `non-recognition`, `double-spend` |
| Server descriptors carrying a signed counter instead of a timestamp | `../architecture/08` §2 | `sequence-number`, `wall-clock`, `clock-skew`, `logical-clock` |
| The refusal in `../architecture/08` §0 | `../architecture/08` §0, §1 | `last-writer-wins`, `replication`, `eventual-consistency` |

### 1.4 What this domain does not own

`00-curriculum-and-method.md` §1.4 fixes the ownership ladder: a concept belongs to the lowest-numbered domain that can define it without forward-referencing a higher one, and there is never a second entry for a concept another domain owns. Four boundaries matter here, because every one of them is a place two writers would otherwise produce two pages.

| Concept | Owner | Why not here |
|---|---|---|
| `hash(7)`, `digital-signature(7)`, `public-key-cryptography(7)` | **06** | A hash chain needs hashing; hashing does not need distributed systems. This domain cites all three as prerequisites and never redefines them. |
| `latency(7)` | **01** | The general lesson — operations differ in cost by orders of magnitude — is representation-level and forward-references nothing. §3.1 quotes the transatlantic numbers as a fact inside a description, which `00` §8.1 step 2 explicitly permits, and points at `latency(7)` for the lesson. |
| `packet(7)`, `rtt` | **05** | Properties of one network path, definable without a second machine cooperating. |
| `process(7)`, `daemon(7)` | **03** | Prerequisites, not content. |
| `did(7)`, `pds(7)`, `provenance-record(5)`, `provenance-chain(7)`, `canonicalization(7)`, `append-only-log(7)` | **07 — this document** | ✅ Assigned by the **ED-3** resolution (2026-07-25). `00` §1.4 gives this domain "identity as keys rather than names; DIDs; hash chains and append-only logs; canonicalization", and `06-cryptography-and-trust.md` §1.4 formally cedes all six here, citing them from `public-key-cryptography(7)` and `trust-anchor(7)` in `seeAlso` only. They are inventoried in §2.6 and **not yet written** — see **DS-1**. |

---

## 2. The concept inventory

### 2.1 How to read it

Fifty concepts, every one of which this domain would ship — the last six being the identity cluster assigned here when **ED-3** was resolved (§1.4, and **DS-1**), and now written. A **●** marks the twenty-six written out in full in §3. This table is the **coverage guarantee**: it is what makes it possible to see the whole domain at once and say what is missing, which a tree of forty files cannot. The eighteen marked **●** have a fully-written entry in §3; the rest are specified here and written later to the same template, and none of them may be written without one, because the fields in this table are the fields that decide whether an entry exists at all (`00` §3.1).

`prerequisites` in **bold** belong to another domain. Every stage obeys R1 (a concept is never introduced before its prerequisites) and R8 is satisfied trivially for the domains below: every prerequisite here points downward into 01-06. ⚠ Since `08-detection-and-defence.md` was written this is no longer the highest-numbered domain — but `08` names entries from this one in `prerequisites` and this one names none of `08`'s, so the ladder is intact in the direction that matters.

### 2.2 The inventory

**Why is this hard at all**

| | id | name | gloss | status | stage | prerequisites | game surface |
|---|---|---|---|---|---|---|---|
| ● | `distributed-system` | distributed system | Several machines cooperating, where any of them can fail alone. | real | operating | **process(7)**, **packet(7)** | exit `69`; the pending authority state |
| ● | `partial-failure` | partial failure | When one piece breaks and the rest keeps running, unaware. | real | operating | `distributed-system(7)` | exit `1` vs `69` vs `75` |
| ● | `timeout` | timeout | A deadline on waiting, after which you must guess what happened. | real | operating | `partial-failure(7)`, **latency(7)** | exit `75` — "sent, no answer yet" |
| | `home-server` | Home server | The machine that holds your account, run by a person, not a company. | real, simplified | operating | `distributed-system(7)` | `../architecture/03` §1; the self-host model |
| | `failure-detector` | failure detector | The part that decides a silent peer should be treated as down. | real | investigating | `timeout(7)` | `PeerLiveness` probing, `../architecture/08` §3 |
| | `backoff` | exponential backoff | Waiting longer before each retry, so a struggling peer recovers. | real | investigating | `timeout(7)` | `PeerBackoff`, `../architecture/08` §3 |
| ● | `idempotence` | idempotence | The property that doing it twice is the same as doing it once. | real | investigating | `timeout(7)`, `partial-failure(7)` | exit `75` — "retry is safe" |
| | `network-partition` | network partition | A break that splits the group into halves that cannot reach across. | real | investigating | `distributed-system(7)`, `timeout(7)` | a federated peer going unreachable mid-duel |
| | `liveness` | liveness | The guarantee that the thing you asked for eventually happens. | real | investigating | `distributed-system(7)` | `uptime[i]` tracked apart from reputation, `../architecture/05` §4 |
| | `safety` | safety | The guarantee that a wrong answer never gets out. | real | investigating | `distributed-system(7)` | same — the other half of the split |

**Clocks and ordering**

| | id | name | gloss | status | stage | prerequisites | game surface |
|---|---|---|---|---|---|---|---|
| ● | `wall-clock` | wall clock | The time-of-day reading, which can jump, drift or move backwards. | real | investigating | `distributed-system(7)` | `../architecture/08` §2 — why descriptors carry no timestamp |
| ● | `monotonic-clock` | monotonic clock | A counter that only ever goes up, for measuring elapsed time. | real | investigating | `wall-clock(7)` | the trace meter and every countdown in the client |
| ● | `clock-skew` | clock skew | The gap between what two machines each believe the time is. | real | investigating | `wall-clock(7)`, `distributed-system(7)` | "clocks legitimately disagree across self-hosts", `../architecture/08` §2 |
| | `ntp` | NTP | The protocol that nudges a machine's time toward a better source. | real | investigating | `clock-skew(7)`, **latency(7)** | cited by `clock-skew(7)`; no direct surface ⚠ see DS-5 |
| ● | `sequence-number` | sequence number | A counter the issuer increments, used to order its own updates. | real | adversarial | `clock-skew(7)`, **digital-signature(7)** | signed monotonic sequence on a server descriptor, `../architecture/08` §2 |
| ● | `logical-clock` | logical clock | Ordering by what caused what, with no reference to time of day. | real | adversarial | `clock-skew(7)`, `sequence-number(7)` | `prevRecordHash` — a chain is an ordering with no clock in it |

**Copies of the same data**

| | id | name | gloss | status | stage | prerequisites | game surface |
|---|---|---|---|---|---|---|---|
| | `replication` | replication | Keeping the same data on several machines at once. | real | investigating | `distributed-system(7)` | the same item known to two servers, `../architecture/03` §3 |
| | `stale-read` | stale read | Getting an answer that was true a moment ago and is not now. | real | investigating | `replication(7)`, `partial-failure(7)` | the stale authority state, `../client/01` §2.2.8 |
| | `eventual-consistency` | eventual consistency | A promise that copies agree later, with no promise of when. | real | investigating | `replication(7)`, `stale-read(7)` | peer directory convergence, `../architecture/08` §1 |
| | `linearizability` | linearizability | Behaving as if there were one copy, updated one change at a time. | real | investigating | `replication(7)` | your home server's own Postgres, `../architecture/03` §3 |
| ● | `cap-theorem` | CAP theorem | When the network splits, you must give up answers or accuracy. | real | adversarial | `network-partition(7)`, `linearizability(7)`, `eventual-consistency(7)` | exit `69` — the client refuses rather than guesses |
| ● | `last-writer-wins` | last-writer-wins | Resolving a clash by keeping whichever version claims to be newer. | real | adversarial | `replication(7)`, `wall-clock(7)`, `clock-skew(7)` | the refusal in `../architecture/08` §0 |
| | `fork` | fork | Two histories that both claim to continue the same one. | real | adversarial | `replication(7)`, **hash(7)** | a conflicting provenance chain, `../architecture/08` §1 |

**Agreeing on a history nobody owns**

| | id | name | gloss | status | stage | prerequisites | game surface |
|---|---|---|---|---|---|---|---|
| ● | `proof-of-work` | proof of work | Buying the right to add to a shared history by burning computation. | real | investigating | **hash(7)**, `fork(7)` | `mine(1)`; the chain readout's height and difficulty |
| ● | `mining-pool` | mining pool | Sharing the luck of mining, so income is steady instead of lumpy. | real | investigating | `proof-of-work(7)` | `mine --pool` against `mine --solo`, `../design/04-mining.md` §1.3 |
| | `difficulty-retarget` | difficulty retarget | Periodically re-tuning how hard a block is, to hold a steady pace. | real | adversarial | `proof-of-work(7)` | "retarget in N blocks" in the `mine` readout |
| | `sybil-resistance` | Sybil resistance | Making identities expensive, so one actor cannot pretend to be many. | real | adversarial | `proof-of-work(7)`, `quorum(7)` | validator sampling, `../architecture/05` §4 |

**When some of the machines are lying**

| | id | name | gloss | status | stage | prerequisites | game surface |
|---|---|---|---|---|---|---|---|
| | `crash-fault` | crash fault | A machine that stops. It says nothing false, it just says nothing. | real | investigating | `partial-failure(7)` | a sampled validator that never answers, `../architecture/05` §4 |
| ● | `consensus` | consensus | Getting independent machines to commit to one answer. | real | adversarial | `distributed-system(7)`, `partial-failure(7)` | a duel outcome that both servers must accept, `../design/13` §3 |
| ● | `byzantine-fault` | Byzantine fault | A machine that lies, and lies differently to different peers. | real | adversarial | `crash-fault(7)`, `consensus(7)` | a self-hosted server that mints items, `../architecture/03` §1 |
| ● | `quorum` | quorum | The smallest group whose agreement is enough to decide. | real | adversarial | `consensus(7)`, `replication(7)` | `quorum(7)` is already promised in `../client/04` §3.10 |
| ● | `bft-threshold` | BFT threshold | How many signers must agree before an answer is safe to believe. | real | adversarial | `byzantine-fault(7)`, `quorum(7)` | 5 of 7, `../architecture/05` §1 |
| | `validator-quorum` | Validator quorum | The committee of servers that signs off on a cross-server result. | real, simplified | adversarial | `bft-threshold(7)`, **digital-signature(7)** | `../architecture/05` §5, the complete loop |
| ● | `equivocation` | Equivocation | Signing two answers that contradict each other, and being caught. | real | adversarial | `byzantine-fault(7)`, **digital-signature(7)** | the hard slash, `../architecture/05` §3.3 |
| | `double-spend` | double-spend | Using the same thing twice by telling two parties different stories. | real | adversarial | `equivocation(7)`, `consensus(7)` | an item that two chains both claim to own |
| ● | `sybil-attack` | Sybil attack | One person wearing many faces to outvote everybody else. | real | adversarial | `quorum(7)`, **public-key-cryptography(7)** | allowlists; "a hostile peer can advertise many fake peers", `../architecture/08` §6 G-3 |

**Trust without an authority**

| | id | name | gloss | status | stage | prerequisites | game surface |
|---|---|---|---|---|---|---|---|
| | `validator-reputation` | validator reputation | A running score for how well a signing server has behaved. | real, simplified | adversarial | `validator-quorum(7)`, `equivocation(7)` | `../architecture/05` §3; ⚠ never rendered near faction reputation |
| ● | `aimd` | AIMD | Earn trust slowly, lose it fast — the shape borrowed from TCP. | real | adversarial | `validator-reputation(7)` | α = 0.05, β = 0.2–0.3, `../architecture/05` §3.1–3.2 |
| | `cold-start` | cold start | A newcomer with no track record cannot get one without a leg-up. | real | adversarial | `validator-reputation(7)` | the reputation floor, `../architecture/05` §2.5 |
| | `gossip-protocol` | gossip protocol | Each peer tells a few others, until everybody has heard. | real | adversarial | `distributed-system(7)`, `eventual-consistency(7)` | peer exchange, `../architecture/08` §3 |
| | `peer-discovery` | peer discovery | Finding other servers with no central list to look them up in. | real | adversarial | `gossip-protocol(7)`, `federation-directory(7)` | seed peers then peer exchange, `../architecture/08` §3 |
| | `federation` | federation | Independent servers that interoperate without anyone in charge. | real | adversarial | `distributed-system(7)`, `home-server(7)` | `../architecture/03` §5; `../design/14` §2 |
| | `federation-directory` | Federation directory | A published index of who exists, trusted for nothing else. | real, simplified | adversarial | `federation(7)` | "a low-trust index, not an authority", `../architecture/03` §2 |
| | `non-recognition` | non-recognition | The only sanction available when nobody can ban anybody. | real, simplified | adversarial | `federation(7)`, `equivocation(7)` | `../architecture/03` §4 |
| | | | **Identity — the six concepts assigned here by the ED-3 resolution (§1.4). All six are now written in full (§3.19–§3.24).** | | | | |
| ● | `did` | DID | A name you prove you own with a key, rather than one issued to you. | real | investigating | **`public-key-cryptography(7)`** | The `identity` window; `../architecture/02` §1. Listed as a shipping page in `../client/04` §3.10 |
| ● | `pds` | PDS | The server that holds an account's identity records, and nothing else. | real | investigating | `did(7)`, `home-server(7)` | `../architecture/02` §2 — and **Invariant I14**: never game state |
| ● | `canonicalization` | canonicalization | Writing the same data one fixed way, so two copies hash alike. | real | adversarial | **`hash(7)`**, **`character-encoding(7)`** | Why a re-ordered field breaks a signature; `../architecture/04` §3 (JCS, RFC 8785) |
| ● | `append-only-log` | append-only log | A record you may add to and never revise, where edits show. | real | adversarial | **`hash(7)`** | The public ledger; `../architecture/04` §4 |
| ● | `provenance-record` · 5 | provenance record | One signed step in a thing's history, naming what came before. | real, simplified | adversarial | `append-only-log(7)`, **`digital-signature(7)`** | `item-history`; `../architecture/04` §6.1. A **section 5** page — a record format, not a concept |
| ● | `provenance-chain` | provenance chain | Every step in a thing's history, linked so a gap is visible. | real, simplified | adversarial | `provenance-record(5)`, `canonicalization(7)` | `verify(1)`; **already written in full** in `../client/04` §4.9 |

### 2.3 The honesty ledger — status distribution

`00` §7.1 item 5 requires this published, because the `man` window offers a status filter and a player is entitled to ask what the game invented.

| Status | Count | Share |
|---|---|---|
| `real` | 39 | 84.8 % |
| `real, simplified` | 7 | 15.2 % |
| `game` | **0** | 0 % |

**Zero `game` entries, and that is the finding, not an oversight.** `00` §4.4 warns that a domain whose entries are largely `game` is lore rather than curriculum. This domain has the opposite property and it is worth saying why: the federation is not a model of a distributed system, it *is* one. It runs Ed25519 signatures over JCS-canonicalized JSON (`../architecture/04` §4–5), a weighted-reservoir validator sample (`05` §2.3), an AIMD reputation rule borrowed from TCP congestion control (`05` §3), and a peer-exchange gossip layer (`08` §3), and `mvn -Pit verify` runs the lot against a real Postgres. There is nothing here to label fiction. The five `real, simplified` entries are simplified in the direction of *the game being tidier than reality* — a single named directory where real federations have none, a clean collective refusal where reality has a handful of browser vendors — never in the direction of the game being more capable than reality.

### 2.4 Unverified claims

`00` §7.4 requires these marked ⚠ and listed rather than asserted.

- ⚠ **The 5-of-7 threshold is stated twice in incompatible units.** `../architecture/05` §1 says consensus requires "`2f+1` of `3f+1` **weighted** validator power" and then that with N = 7, "**5 of 7** must agree", weighted by reputation. Five-of-seven-by-count and two-thirds-of-weight are different rules that coincide only when weights are equal. `../architecture/04-item-provenance.md` §8's closing note already lists "whether the quorum threshold binds on validator count as well as weight" as open (P-1…P-7). `bft-threshold(7)` in §3.15 therefore teaches the count form, which is the one the arithmetic proof covers, and `validator-quorum(7)` carries the weighting as its simplification. **DS-3.**
- ⚠ **No claim is made about which real BFT system the game's weighting most resembles.** Reputation-weighted committees are real; the specific shape here was not traced to a named published protocol in this pass.

---

## 3. The full entries

### 3.0 Which twenty-six, and why

Chosen on the three grounds `00` §3.1 gives an entry a reason to exist: the game leans on it, it kills a misconception people actually hold, or it unlocks several other concepts.

| Entry | Chosen because |
|---|---|
| `distributed-system(7)` | Root of the domain; a prerequisite of 12 other entries |
| `partial-failure(7)` | The defining difficulty, and the game renders it as three exit statuses |
| `timeout(7)` | "Slow or dead" is the single most useful realisation in the domain |
| `idempotence(7)` | Highest everyday transfer value in the whole document; the hook is one line of the exit-status table |
| `wall-clock(7)` | Kills the most confidently-held false belief here: that time goes forward |
| `monotonic-clock(7)` | The correct answer to the previous entry, and a one-command transfer test |
| `clock-skew(7)` | Carries the NTP numbers; explains why the game's descriptors carry no timestamp |
| `proof-of-work(7)` | `mine(1)` is now a real simulation of it, and it kills the gambler's fallacy in the one place players reliably hold it |
| `mining-pool(7)` | The game makes it a live choice, and almost everyone believes pooling pays *more* |
| `sequence-number(7)` | The game's actual ordering mechanism (`../architecture/08` §2) |
| `logical-clock(7)` | Generalises it, and is the concept the provenance chain is an instance of |
| `last-writer-wins(7)` | **The best teaching opportunity in the game.** A real design decision, refused in writing, for a reason a player can check |
| `cap-theorem(7)` | The most misquoted result in computing, stated correctly |
| `consensus(7)` | Unlocks the entire fourth group |
| `byzantine-fault(7)` | The distinction the whole architecture is built around |
| `bft-threshold(7)` | A glossary term, and the arithmetic can actually be derived on the page |
| `quorum(7)` | Already promised as a page in `../client/04` §3.10 |
| `equivocation(7)` | A glossary term; cryptographically provable; the double-spend problem in one move |
| `sybil-attack(7)` | Explains allowlists, reputation floors and why identity is deliberately expensive |
| `aimd(7)` | A genuine cross-domain link — the reputation rule is TCP congestion control — and the most enjoyable fact in the domain |
| `did(7)` | Every ledger row and every provenance record names its parties this way; and "an identifier must be issued by somebody" is a confident wrong belief |
| `pds(7)` | Carries Invariant **I14** as a lesson rather than a rule — identity is portable, accounting is not, and conflating them is trivially exploitable |
| `canonicalization(7)` | The one entry here whose misconception is a **forgery primitive**, and this project shipped the bug and fixed it |
| `append-only-log(7)` | Tamper-evident versus tamper-proof is the highest-value distinction in the domain, and git makes it demonstrable in two commands |
| `provenance-record(5)` | The domain's only **section 5** page, so it is also where the section system earns its keep |
| `provenance-chain(7)` | Already written as a shipped page (`../client/04` §4.9); this is the curriculum record behind it, and its `CAVEATS` is the domain's thesis in three sentences |

The next four in line, written to the same template but not in this pass: `eventual-consistency(7)`, `validator-quorum(7)`, `non-recognition(7)`, `gossip-protocol(7)`.

Twenty-three of the twenty-four are **section 7**, so none of those has a `SYNOPSIS`, `OPTIONS` or `EXIT STATUS`. The exception is `provenance-record(5)`, which is a record format rather than a concept and therefore does carry one (`00` §5.2). That absence is deliberate and is itself part of what the section system teaches (`00` §5.2).

---

### 3.1 `distributed-system(7)`

```
id:             distributed-system
section:        7
name:           distributed system
canonical:      distributed system
gloss:          Several machines cooperating, where any of them can fail alone.
status:         real
aliases:        distributed computing
seeAlso:        partial-failure(7), timeout(7), home-server(7), federation(7),
                latency(7), packet(7), process(7)
reading:        the fallacies of distributed computing — seven listed by Peter
                Deutsch c. 1994, an eighth added by James Gosling c. 1997;
                Leslie Lamport, "Time, Clocks, and the Ordering of Events in a
                Distributed System", CACM 21(7), 1978
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          operating
prerequisites:  process(7), packet(7)
hook:           The first command that returns exit 69 — "the server could not
                be reached. Nothing was sent" — rather than 0 or 1
                (`../client/04-terminology-and-education.md` §3.5), and the
                pending authority state on any value the client has asked for
                and not yet received (`../client/01-visual-language.md` §2.2.8).
misconception:  commonly believed a networked program is an ordinary program
                with a slower step in the middle; actually the step can fail in
                a way no local step can — it can succeed at the far end and
                lose the answer on the way back — so the caller cannot tell
                "it did not happen" from "it happened and I was not told".
transfer:       On macOS or Linux, run `ping -c 20 1.1.1.1` and read the
                summary: packets transmitted, packets received, percent loss,
                and min/avg/max round-trip time. On Windows, `ping -n 20
                1.1.1.1`. The player can now say what fraction of their own
                messages did not come back and how long the ones that did took,
                and can see that "the network works" is a percentage rather
                than a yes. Platform: any; see ED-8.
verified:       New York–London round-trip floor in vacuum 37.2 ms, and 58.95
                ms on the lowest-latency cable in service (Hibernia Express) —
                Submarine Networks / Equinix published figures; ~2/3 c in
                fibre (about 200,000 km/s, refractive index ~1.5) — standard
                optical-fibre figure, corroborated by O'Reilly *High
                Performance Browser Networking* ch. 1; typical commercial
                transatlantic SLA 90 ms — Verizon Global Latency SLA.
                Checked 2026-07-25.

## DESCRIPTION

Your client is one machine. Your home server is another. Almost every number
on your screen was computed on the server and sent to you, and the client
works out exactly one thing for itself — whether a provenance chain verifies.
See verify(1).

That split is what makes this a distributed system: two or more machines
cooperating on one job, with no shared memory, no shared clock, and a network
between them that may delay, reorder, duplicate or drop what they send each
other.

The consequence shows up as three exit statuses rather than one. A status of 1
means the server refused and nothing changed. 69 means the server could not be
reached and nothing was sent. 75 means the request went out and no answer has
come back. Only the first is a decision. The third is an absence of
information, and no amount of waiting converts it into one. See
partial-failure(7).

Distance is part of it and cannot be engineered away. Light in optical fibre
travels at about two-thirds of its speed in vacuum, roughly 200,000 km/s. New
York to London and back is about 11,000 km, so the floor is 37 milliseconds in
vacuum and about 59 on the fastest cable in service; ordinary paths are 60 to
90. See latency(7).

## REAL-WORLD COUNTERPART

real — every non-trivial system the reader uses is one, including the one they
are reading this on.

A phone syncing photos, a card terminal authorising a payment, a browser
loading a page: each is a machine asking another machine for something over a
link that can fail on its own. The classic summary of what goes wrong is the
"fallacies of distributed computing", a list of assumptions that are each false
and each keep being made: the network is reliable, latency is zero, bandwidth
is infinite, the network is secure, topology does not change, there is one
administrator, transport cost is zero, the network is homogeneous.

The distinguishing property is not that there are many machines. It is that
they fail separately, so a working machine has to make decisions with
incomplete information about the others.
```

---

### 3.2 `partial-failure(7)`

```
id:             partial-failure
section:        7
name:           partial failure
canonical:      partial failure
gloss:          When one piece breaks and the rest keeps running, unaware.
status:         real
seeAlso:        distributed-system(7), timeout(7), idempotence(7),
                crash-fault(7), stale-read(7)
reading:        exit(3); sysexits.h (EX_UNAVAILABLE, EX_TEMPFAIL); Jim Waldo
                et al., "A Note on Distributed Computing" (Sun Microsystems
                Laboratories, 1994)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          operating
prerequisites:  distributed-system(7)
hook:           The exit-status table itself — 1 (refused), 69 (unreachable)
                and 75 (sent, no answer yet) are three different numbers, and
                `../client/04-terminology-and-education.md` §3.5 states that
                keeping 1 and 69 apart is Invariant I14 rendered as a number.
misconception:  commonly believed a failed operation either happened or did
                not; actually there is a third outcome, and it is common — the
                operation happened and you were not told, which looks exactly
                like the operation never happening.
transfer:       Next time a payment page says "do not press back or refresh",
                the player can say what the warning is actually protecting
                against: the shop cannot tell whether the browser never got the
                confirmation or the charge never went through, so it is asking
                a human to break the tie. Also readable in any `curl` failure:
                exit 7 (could not connect) and exit 28 (timed out) are
                different codes for exactly this reason — `man curl`, EXIT
                CODES. Platform: any; `curl` ships with macOS, Linux and
                current Windows.
verified:       curl exit codes 7 and 28 — curl(1) EXIT CODES section;
                EX_UNAVAILABLE = 69 and EX_TEMPFAIL = 75 — sysexits.h, as
                already verified in `../client/04` §3.5; the
                partial-failure argument — Waldo et al. 1994 §3.
                Checked 2026-07-25.

## DESCRIPTION

The exit statuses are where you meet this. A status of 1 says the server
refused: a rule applied, nothing changed, and you know exactly where you
stand. A status of 69 says the request never left. A status of 75 says it left
and nothing has come back.

Only 75 is genuinely uncomfortable, and it is the normal case. Somewhere
between your rig and the server, one of two things is true, and you cannot
find out which: either the request never arrived, or it arrived, the server
did the work, and the answer was lost coming back. Those are opposite facts
and they produce an identical silence.

Partial failure is the name for this: one part of a system stops while the
rest carries on, not knowing. It is the defining difficulty of the whole
subject, and the reason `1` and `69` are never allowed to collapse into one
message anywhere in this client. "Refused" is knowledge. "Unreachable" is the
absence of it, and presenting the second as the first would be a lie the
interface tells.

## REAL-WORLD COUNTERPART

real — and it is the reason for a warning the reader has certainly seen.

"Do not press back or refresh" on a payment page is partial failure, written
for humans. The shop's server cannot tell whether the card was charged and the
confirmation was lost, or the request never arrived. It has no way to
distinguish those, so it asks a person to avoid creating a second one.

Command-line tools encode the same split. `curl` exits 7 when it could not
connect and 28 when it timed out waiting — two codes because they license two
different responses. Retrying after 7 is free. Retrying after 28 may charge
the card twice, unless the operation was built so that a repeat is harmless.
See idempotence(7).

A single machine has almost none of this. When it fails, everything on it
fails together, and there is nobody left to be confused.
```

---

### 3.3 `timeout(7)`

```
id:             timeout
section:        7
name:           timeout
canonical:      timeout
gloss:          A deadline on waiting, after which you must guess what happened.
status:         real
aliases:        deadline
seeAlso:        partial-failure(7), idempotence(7), failure-detector(7),
                backoff(7), latency(7), liveness(7)
reading:        Chandra and Toueg, "Unreliable Failure Detectors for Reliable
                Distributed Systems", JACM 43(2), 1996; Fischer, Lynch and
                Paterson, "Impossibility of Distributed Consensus with One
                Faulty Process", JACM 32(2), 1985
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          operating
prerequisites:  partial-failure(7), latency(7)
hook:           Exit status 75, whose meaning is given as "sent, no answer yet,
                or timed out; retry is safe"
                (`../client/04-terminology-and-education.md` §3.5) — one number
                covering both "still waiting" and "gave up waiting", which is
                the whole lesson.
misconception:  commonly believed a timeout detects that something has failed;
                actually a timeout detects only that an answer has not arrived
                yet, and no timeout anywhere can distinguish a dead machine
                from a slow one — the length of the timeout is a policy choice
                about how long to be wrong for, not a measurement.
transfer:       Run `curl --max-time 2 https://example.com` and then
                `curl --max-time 0.001 https://example.com`. The second fails
                with exit 28 against a server that is working perfectly. The
                player can now read a "connection timed out" message as a
                statement about their own patience rather than about the other
                machine, and can say why raising a timeout sometimes fixes an
                error and sometimes only makes the error slower. Platform: any
                with curl.
verified:       curl --max-time and exit 28 — curl(1); the impossibility of
                distinguishing slow from crashed in an asynchronous system —
                Fischer, Lynch and Paterson 1985 (consensus is impossible with
                one crash fault), and the failure-detector framing that
                followed it, Chandra and Toueg 1996. Checked 2026-07-25.

## DESCRIPTION

Exit 75 covers two situations that feel different and are not: the answer has
not come back yet, and the client stopped waiting. The second is a timeout,
and a timeout is a decision your side makes, alone, about how long to keep
waiting before acting as though the answer is not coming.

It is not a measurement of the other machine. The server may be down. It may
also be fine and 40 milliseconds further away than usual. Nothing observable
from your side separates those, ever — not with a longer timeout, not with a
better network. A slow machine and a dead machine emit exactly the same thing,
which is nothing.

So the timeout length is a policy, and both directions cost something. Set it
short and you will declare healthy servers dead, retry work that already
succeeded, and add load to a system that was merely busy. Set it long and a
genuinely dead server holds your interface hostage for that many seconds.
Every timeout value in every system is a bet about which mistake is cheaper.

## REAL-WORLD COUNTERPART

real — and the impossibility is a proved result, not a limitation of current
engineering.

In 1985 Fischer, Lynch and Paterson proved that in a network with no bound on
message delay, no deterministic protocol can guarantee that a group of
machines will agree on anything, if even one of them may crash. The reason is
precisely this gap: nobody can tell a crashed participant from a slow one, so
a protocol either waits forever or eventually acts on a guess.

Real systems live with it by naming the guess. Chandra and Toueg's 1996 paper
formalised the guessing part as a "failure detector" — a component that is
allowed to be wrong, with stated bounds on how wrong. Every modern system has
one, usually spelled as a heartbeat interval and a miss count.

Practically: a timeout is how long you are willing to be wrong for. Choosing
one is choosing which way.
```

---

### 3.4 `idempotence(7)`

```
id:             idempotence
section:        7
name:           idempotence
canonical:      idempotence
gloss:          The property that doing it twice is the same as doing it once.
status:         real
aliases:        idempotent, retry-safe
seeAlso:        timeout(7), partial-failure(7), backoff(7), sequence-number(7)
reading:        RFC 9110 §9.2.2 (Idempotent Methods); Stripe API reference,
                "Idempotent requests"
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  timeout(7), partial-failure(7)
hook:           The exit-status table's gloss on 75: "Sent, no answer yet, or
                timed out; **retry is safe**"
                (`../client/04-terminology-and-education.md` §3.5). That
                promise is a claim about the server, and this page is what the
                claim means.
misconception:  commonly believed that whether it is safe to retry depends on
                the network — if the request probably failed, retrying is
                probably fine; actually it never depends on the network,
                because you can never establish that it failed. It depends
                entirely on whether the operation was built so that a repeat
                changes nothing.
transfer:       The player can now read the "Idempotency-Key" header in any
                payment API's documentation and say what it is for: the client
                invents a unique value, sends it with the request, and the
                server remembers it, so a retry after a lost answer returns the
                original result instead of charging again. They can also
                predict which HTTP methods are safe to retry from RFC 9110 §9.2
                — GET, PUT and DELETE are defined as idempotent, POST is not —
                and why a browser warns before re-submitting a form.
                Platform: none needed; reading, not running.
verified:       GET, HEAD, PUT, DELETE, OPTIONS and TRACE are defined
                idempotent and POST is not — RFC 9110 §9.2.2; idempotency keys
                as the standard payment-API mechanism — Stripe API reference,
                "Idempotent requests". Checked 2026-07-25.

## DESCRIPTION

Exit status 75 promises that a retry is safe. That promise is not about the
network — nothing about the network could support it — it is about how the
server was built.

An operation is idempotent when performing it twice leaves the system in the
same state as performing it once. "Set this tool's storage tier to vault" is
idempotent: run it twice and the tool is in the vault, the same as if you had
run it once. "Move 40 EC to this handle" is not: run it twice and 80 EC is
gone.

This is the only real answer to the third outcome in partial-failure(7). You
cannot find out whether the lost request took effect, so the useful question
is not "did it happen" but "does it matter if it happens again". Where the
answer is no, a retry needs no thought. Where the answer is yes, the operation
has to carry something that lets the server recognise the repeat — an
identifier the client generated once and reuses on every attempt.

## REAL-WORLD COUNTERPART

real — one of the most widely applied ideas in the subject, and one of the
least discussed outside it.

HTTP defines it directly. RFC 9110 §9.2.2 marks GET, HEAD, PUT, DELETE,
OPTIONS and TRACE as idempotent and deliberately leaves POST out, which is
exactly why a browser will re-issue a page load without asking and will warn
before re-submitting a form. The warning is not politeness; the specification
says the server made no promise.

Payment systems solve it with an idempotency key. The client generates a
unique value, attaches it to the charge request, and retries with the same
value. The server stores the key with the result, so the second arrival
returns the first answer rather than taking the money again. Stripe's API has
worked this way for years, and every payment API since has copied it, for the
reason on this page: the client cannot tell a lost answer from a lost request,
so the server has to make the difference stop mattering.
```

---

### 3.5 `wall-clock(7)`

```
id:             wall-clock
section:        7
name:           wall clock
canonical:      wall clock
gloss:          The time-of-day reading, which can jump, drift or move backwards.
status:         real
aliases:        real-time clock, wall time, system time
seeAlso:        monotonic-clock(7), clock-skew(7), ntp(7), sequence-number(7),
                logical-clock(7), last-writer-wins(7)
reading:        clock_gettime(3) — CLOCK_REALTIME; RFC 5905 (NTPv4);
                date(1); CGPM 2022 Resolution 4 (the end of the leap second)
notes:          Do not translate "wall clock" idiomatically into a phrase
                meaning "accurate clock". The point of the term is that it is
                the clock a person reads, not the one a program should trust.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  distributed-system(7)
hook:           `../architecture/08-discovery-and-sync.md` §2, which explains
                in two bullets why a server descriptor is ordered by a signed
                counter and never by a timestamp: "clocks are
                attacker-controlled" and "clocks legitimately disagree across
                self-hosts, producing flapping".
misconception:  commonly believed the clock on a computer moves forward
                steadily, so two readings taken in order will be in order;
                actually the time-of-day clock is corrected against outside
                sources while the machine runs, so it can jump forward, be
                slowed down, or step backwards — and a program that subtracts
                two of its readings can get a negative duration.
transfer:       On macOS or Linux, run `date` and then `uptime`. The first
                prints a value that is corrected from the network and can move
                in either direction; the second prints one derived from a
                counter that cannot. The player can now say why a stopwatch
                should never be built by subtracting two `date` readings, and
                will recognise the class of bug behind "this operation took
                minus three seconds" in a log. Platform: Unix shell; see ED-8.
verified:       CLOCK_REALTIME is settable and subject to discontinuous jumps,
                CLOCK_MONOTONIC is not — clock_gettime(3), Linux man-pages;
                27 leap seconds inserted since 1972, the last on 2016-12-31 —
                IERS/BIPM record; CGPM Resolution 4 of 18 November 2022 decided
                to stop inserting leap seconds by 2035 — BIPM/CGPM.
                Checked 2026-07-25.

## DESCRIPTION

The federation deliberately does not use one. A server's directory record —
its address, its transport key, what it can do — legitimately changes over
time, so peers need to know which version is newer. The obvious field for that
is a timestamp, and `../architecture/08-discovery-and-sync.md` §2 refuses it
for two separate reasons.

The first is that a self-hosted server sets its own clock. A hostile operator
puts theirs in the year 3000 and their record is permanently "newest",
overwriting everyone else's view of them for as long as the federation lasts.
The second reason applies even with no attacker: honest home servers disagree
about the time by ordinary amounts, so records taken minutes apart can arrive
in the wrong order and flap back and forth.

A wall clock is the time-of-day reading — what a person means by "the time".
It is a fine thing to display to a human and a poor thing to sort by, because
it is not a counter. It is an estimate that the machine keeps correcting.

## REAL-WORLD COUNTERPART

real — this is the reader's own computer clock, and the surprise is genuine.

Time-of-day is maintained by nudging: the machine compares itself to time
servers and adjusts, either by slowing or speeding its rate or, when the gap
is large, by stepping the value directly. Either way it is not monotonic. A
program that reads the clock, does work, reads it again and subtracts can get
zero or a negative number, and code that assumes otherwise has produced
outages at large companies.

Even the calendar underneath is not steady. Between 1972 and 2016, 27 leap
seconds were inserted into UTC to keep it in step with the Earth's rotation,
which means a minute containing 61 seconds — a value some software has never
handled. In November 2022 the General Conference on Weights and Measures voted
to stop adding them by 2035.

The correct use of a wall clock is showing a date to a person, and stamping a
record with roughly when it was made. Ordering events by it — especially
events from different machines — is a bug that behaves for years and then does
not. See monotonic-clock(7).
```

---

### 3.6 `monotonic-clock(7)`

```
id:             monotonic-clock
section:        7
name:           monotonic clock
canonical:      monotonic clock
gloss:          A counter that only ever goes up, for measuring elapsed time.
status:         real
aliases:        monotonic time, steady clock
seeAlso:        wall-clock(7), clock-skew(7), sequence-number(7), timeout(7)
reading:        clock_gettime(3) — CLOCK_MONOTONIC and CLOCK_MONOTONIC_RAW;
                uptime(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  wall-clock(7)
hook:           Every countdown the client draws — the trace meter during a
                breach (`../design/05-hacking-minigame.md` §4), a compute
                recovery clock (`../client/04-terminology-and-education.md`
                §4.9, compute(7)), a backlog timer. All of them are elapsed
                time, none of them is time of day.
misconception:  commonly believed a clock is a clock, so the same one serves
                for "what time is it" and "how long did that take"; actually
                these are two different measurements needing two different
                sources, and every operating system provides both separately
                because using the wrong one is a known and repeated defect.
transfer:       Run `uptime` on macOS or Linux. The value it prints comes from
                a counter that started when the machine booted and cannot go
                backwards, which is why it is trustworthy even on a machine
                whose time-of-day is wrong. The player can now name the right
                tool for a duration in any language they encounter —
                `System.nanoTime()` rather than `System.currentTimeMillis()` in
                Java, `time.monotonic()` rather than `time.time()` in Python —
                and say why the distinction exists. Platform: Unix shell for
                `uptime`; see ED-8.
verified:       CLOCK_MONOTONIC is unaffected by discontinuous jumps in system
                time but is affected by incremental adjustments from adjtime(3)
                and NTP; CLOCK_MONOTONIC_RAW is affected by neither and was
                added in Linux 2.6.28 — clock_gettime(3), Linux man-pages.
                Python's time.monotonic() and Java's System.nanoTime() are the
                documented monotonic sources in each language — CPython and
                JDK API documentation. Checked 2026-07-25.

## DESCRIPTION

Everything in this game that counts down is measuring elapsed time, not time
of day: the trace meter racing your breach, a compute allocation recovering, a
backlog timer shrinking. None of them cares what date it is, and none of them
should break if the machine's clock is corrected mid-breach.

A monotonic clock is a counter that only increases. It has no relationship to
any calendar — its zero is arbitrary, usually the moment the machine started —
and its only useful operation is subtracting one reading from another to get a
duration. That duration is trustworthy in a way a wall-clock difference is
not, because nothing outside the machine is allowed to move the counter.

The rule is worth memorising in this form: **time of day for humans, monotonic
for durations.** Two clocks, two jobs, and no overlap.

## REAL-WORLD COUNTERPART

real — every operating system ships both, and every reasonable language
exposes both.

On Linux and macOS the choice is an argument: `clock_gettime(CLOCK_REALTIME)`
gives time of day and can jump; `clock_gettime(CLOCK_MONOTONIC)` gives the
counter and cannot. Java splits them as `System.currentTimeMillis()` and
`System.nanoTime()`; Python as `time.time()` and `time.monotonic()`. The
shell's `uptime` reads the monotonic side, which is why it stays honest on a
machine whose date is wrong.

One nuance, because it is the kind of detail that matters later: on Linux the
ordinary monotonic clock still has its *rate* adjusted by the time-synchronising
daemon, so it is guaranteed not to jump but not guaranteed to tick at exactly
one second per second. `CLOCK_MONOTONIC_RAW`, added in 2008, is the untouched
hardware counter for the rare cases that need it.

The practical payoff is a bug class the reader can now avoid: any stopwatch
built from two time-of-day readings is wrong, occasionally, at exactly the
moment a clock correction lands.
```

---

### 3.7 `clock-skew(7)`

```
id:             clock-skew
section:        7
name:           clock skew
canonical:      clock skew
gloss:          The gap between what two machines each believe the time is.
status:         real
aliases:        clock drift, clock offset
seeAlso:        wall-clock(7), monotonic-clock(7), ntp(7),
                last-writer-wins(7), sequence-number(7), logical-clock(7)
reading:        RFC 5905 (NTPv4); IEEE 1588 (PTP); Google's "leap smear"
                engineering notes
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  wall-clock(7), distributed-system(7)
hook:           `../architecture/08-discovery-and-sync.md` §2's second bullet:
                "clocks legitimately disagree across self-hosts, producing
                flapping". Every home server in the federation is somebody's
                machine in somebody's house, with whatever clock discipline
                that implies.
misconception:  commonly believed that machines synchronised over the internet
                agree on the time, so timestamps from two of them are
                comparable; actually synchronisation leaves a residual
                disagreement of tens of milliseconds over the public internet
                and much more on a badly configured machine, which is longer
                than most of the events being ordered.
transfer:       On macOS or Linux, run `sntp -d time.apple.com` (macOS) or
                `chronyc tracking` / `timedatectl show-timesync` (Linux, where
                configured) and read the offset. The player can now name their
                own machine's disagreement with a reference clock in
                milliseconds, and can say why "the log says 12:00:00.150 on
                server A and 12:00:00.100 on server B" does not establish which
                event happened first. Platform: Unix shell, and the exact
                command depends on the time daemon installed; see ED-8.
verified:       NTP typically holds time to within tens of milliseconds over
                the public internet and better than one millisecond on a LAN
                under good conditions, with asymmetric routing and congestion
                causing errors of 100 ms or more — RFC 5905 and the NTP
                project's own summary documentation; PTP (IEEE 1588) reaches
                sub-microsecond on dedicated hardware — IEEE 1588.
                Checked 2026-07-25.

## DESCRIPTION

Every home server in the federation is a machine somebody runs, and no two of
them agree on the time. That is not a defect anybody can fix; it is the normal
condition, and it is one of the two reasons a server's directory record is
ordered by a signed counter rather than a date
(`../architecture/08-discovery-and-sync.md` §2).

Clock skew is the difference between two machines' idea of the current time.
Left alone, a machine's clock drifts because its oscillator is not perfect —
tens of parts per million, which is seconds per day. Time synchronisation
pulls it back, but never to zero, and the residue is large compared with the
events being ordered.

The consequence is the important part. Two records timestamped from two
machines cannot be put in order by comparing their timestamps, because the
difference between them may be smaller than the disagreement between the
clocks that produced them. This is not a rare edge case; for events seconds
apart on machines synchronised over the internet, it is routine.

## REAL-WORLD COUNTERPART

real — with published numbers.

NTP, defined in RFC 5905, is the protocol nearly every machine uses. Over the
public internet it usually holds a machine to within **tens of milliseconds**
of its reference. On a local network under good conditions it can do better
than a millisecond. Asymmetric routing — packets taking a different path back
than out — breaks its central assumption and can produce errors of 100
milliseconds or more, and NTP cannot detect that this has happened.

Where milliseconds are not good enough, the answer is hardware. PTP (IEEE
1588) uses network equipment that timestamps packets as they pass and reaches
sub-microsecond agreement, which is why exchanges and telecoms networks use it
and ordinary datacentres mostly do not.

So the honest summary is a range, not a number: two synchronised machines on
the internet agree to within tens of milliseconds, two machines on a LAN to
about a millisecond, and two machines where nobody checked to within anything
at all. Ordering events across them needs something that is not a clock. See
sequence-number(7) and logical-clock(7).
```

---

### 3.8 `sequence-number(7)`

```
id:             sequence-number
section:        7
name:           sequence number
canonical:      sequence number
gloss:          A counter the issuer increments, used to order its own updates.
status:         real
aliases:        monotonic sequence, version number
seeAlso:        logical-clock(7), clock-skew(7), wall-clock(7),
                last-writer-wins(7), digital-signature(7), fork(7)
reading:        RFC 9293 §3.4 (TCP sequence numbers — a different use of the
                same phrase); DNS SOA serial numbers, RFC 1035 §3.3.13
notes:          **Homonym.** "Sequence number" is also the 32-bit field in
                every TCP segment that orders bytes within one connection
                (RFC 9293). Both are real, both order things, and they are not
                the same mechanism: TCP's orders bytes inside one conversation,
                this orders successive versions of one record across machines.
                Name the collision; do not leave it to inference. See DS-2.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  clock-skew(7), digital-signature(7)
hook:           `../architecture/08-discovery-and-sync.md` §2 — "each descriptor
                carries a sequence number the issuing server increments and
                signs", with monotonicity enforced by a database trigger on
                `federation_peers` so that even a bug cannot roll a peer's
                record backwards.
misconception:  commonly believed a version counter is just a tidier timestamp
                and could be replaced by one; actually the counter has a
                property no timestamp has — only its issuer can produce the
                next value, and a signature proves the issuer produced it — so
                it cannot be forged forward the way a clock reading can.
transfer:       The player can now read a DNS zone's SOA serial number and say
                what it is for: secondary name servers compare serials to
                decide whether to pull a fresh copy, and the operator must
                increment it or the change does not propagate — the classic
                "I edited the zone and nothing happened" failure. Visible with
                `dig SOA example.com +short` on macOS or Linux. Platform: Unix
                shell with dig; see ED-8.
verified:       The signed monotonic sequence and the federation_peers rollback
                trigger — `../architecture/08-discovery-and-sync.md` §2; SOA
                SERIAL is compared by secondaries to decide whether to
                re-transfer — RFC 1035 §3.3.13 and RFC 1912 §2.2; TCP's
                sequence number field is 32 bits — RFC 9293 §3.1.
                Checked 2026-07-25.

## DESCRIPTION

This is what the federation uses instead of a timestamp. A server's directory
record carries a counter that only that server increments, and the whole
record — counter included — is signed with that server's key. A peer accepts a
new record only if its counter is higher than the one already held **and** the
signature checks out. The database refuses to go backwards even if the code
asks it to.

That combination does something a date cannot. A hostile server can write any
timestamp it likes, including one in the year 3000, and the timestamp is just
a number in a field. It cannot write a higher counter *and* produce a valid
signature over it unless it holds the key — and if it does hold the key, it is
that server, updating its own record, which is exactly what the mechanism is
for.

The scope is the limit worth noticing. A sequence number orders one issuer's
updates against each other. It says nothing about how my version 7 relates to
your version 7, because they were counted by different people. Ordering across
issuers is a harder problem. See logical-clock(7).

## REAL-WORLD COUNTERPART

real — the standard answer whenever ordering matters and clocks cannot be
trusted.

DNS has used it since 1987. Every zone carries an SOA record with a serial
number, and a secondary name server decides whether to fetch a fresh copy by
comparing the serial it holds with the one the primary advertises. Nothing in
that decision consults a clock, which is why it works between machines run by
different organisations. It also produces the field's most common operational
mistake: edit the zone, forget the serial, and the change never propagates,
because as far as every secondary is concerned nothing happened.

Databases use the same idea as an optimistic-concurrency version column;
document stores expose it as an ETag; replication protocols call it an epoch
or a term.

The trade is always the same. A counter gives you a reliable order and no
sense of time — you can say which came later, never how much later, and never
what a human clock read.
```

---

### 3.9 `logical-clock(7)`

```
id:             logical-clock
section:        7
name:           logical clock
canonical:      logical clock
gloss:          Ordering by what caused what, with no reference to time of day.
status:         real
aliases:        Lamport clock, happens-before, causal ordering, vector clock
seeAlso:        sequence-number(7), clock-skew(7), fork(7), quorum(7),
                hash(7), last-writer-wins(7)
reading:        Leslie Lamport, "Time, Clocks, and the Ordering of Events in a
                Distributed System", CACM 21(7), 1978; git-log(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  clock-skew(7), sequence-number(7)
hook:           `item-history(1)`, which walks an item's chain to genesis by
                following `prevRecordHash` (`../client/04` §3.10;
                `../architecture/04-item-provenance.md` §6.1). The chain puts
                an item's whole life in order and contains no clock that
                anything is ordered by.
misconception:  commonly believed that if two events have timestamps, one of
                them happened first; actually events on different machines can
                be genuinely *concurrent* — neither could have influenced the
                other — and forcing them into a single order invents a fact
                that no observer possessed.
transfer:       Run `git log --graph --oneline` in any repository with a merge
                in it. The player can now read the branching picture as what it
                is: a partial order, where two commits on separate branches are
                not "one before the other" but genuinely unordered until a
                merge relates them. That is why merge conflicts exist, and why
                a commit's author date can be older than its parent's without
                anything being wrong. Platform: any with git.
verified:       The happens-before relation is a partial order and Lamport
                timestamps are consistent with it but do not characterise it
                (C(a) < C(b) does not imply a → b) — Lamport 1978 §2; a git
                commit names its parents and the commit id is a hash over
                content including them — git documentation, and already stated
                in `../client/04` §4.9's provenance-chain(7) entry.
                Checked 2026-07-25.

## DESCRIPTION

An item's provenance chain is ordered without a clock. Each record names the
hash of the record before it, so "minted, then granted, then traded" is fixed
by the structure of the chain rather than by any timestamp inside it. The
timestamps are there for replay protection, not for ordering
(`../architecture/08-discovery-and-sync.md` §4).

A logical clock is that idea in general: order events by *what could have
influenced what*, not by when a machine says they happened. If a record names
another record, the named one came first — necessarily, because you cannot
name the hash of something that does not exist yet. Nothing else is needed and
no clock is consulted.

The pay-off, and the part that takes a moment: this produces a **partial**
order. Some pairs of events genuinely have no order. If two servers each issue
a record and neither knew of the other, neither happened first in any
meaningful sense, and a system that forces them into a line is inventing a
fact. Recognising that pair as concurrent, rather than picking a winner, is
what separates a system that detects a conflict from one that silently loses
data. See last-writer-wins(7).

## REAL-WORLD COUNTERPART

real — Lamport's 1978 paper is one of the most cited in computing, and its
argument is short.

It defines "happens-before": event a happens before event b if they are on the
same machine in that order, or if a is the sending of a message and b is its
receipt, or by chaining those two rules. Anything not related that way is
concurrent. The paper then attaches a counter to each machine, bumped on every
event and carried on every message, so that if a happens before b, a's counter
is lower. The converse does not hold — a lower counter does not prove
causality — and a later refinement, the vector clock, is what tells the two
cases apart.

The reader has used one. A git history is exactly this: a commit names its
parents, the id is a hash over content that includes them, and two commits on
separate branches are unordered until a merge relates them. The dates in a git
log are decorations that can be edited or simply wrong; the parent links are
the order, and they are the reason rewriting an old commit changes every id
after it.
```

---

### 3.10 `last-writer-wins(7)`

```
id:             last-writer-wins
section:        7
name:           last-writer-wins
canonical:      last-writer-wins
gloss:          Resolving a clash by keeping whichever version claims to be newer.
status:         real
aliases:        LWW, newest wins
seeAlso:        wall-clock(7), clock-skew(7), logical-clock(7), fork(7),
                sequence-number(7), replication(7), non-recognition(7)
reading:        DeCandia et al., "Dynamo: Amazon's Highly Available Key-value
                Store", SOSP 2007 §4.4; Marc Shapiro et al. on
                conflict-free replicated data types (CRDTs), 2011
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  replication(7), wall-clock(7), clock-skew(7)
hook:           `../architecture/08-discovery-and-sync.md` §0 — the section
                that exists specifically to record a refusal, so that nobody
                proposes the feature again. A player who opens this page is
                reading the reason their game does not have a feature that
                sounds obviously good.
misconception:  commonly believed that when two copies of something disagree,
                keeping the newer one is the safe default; actually "newer" is
                a claim made by whoever wrote the record, on a clock they
                control, so in any system with an adversary in it, last-writer-
                wins means "whoever lies hardest about the time wins".
transfer:       The player can now ask one question of any sync feature they
                use — a notes app, a shared folder, a password manager — namely
                what happens when the same item is edited on two devices that
                were both offline. If the answer is "the newest wins", they can
                predict the failure: the device with the faster clock erases
                the other's work, silently, with no error anywhere.
                Platform: none needed.
verified:       "Newest wins" was refused for game state and the reasoning is
                recorded — `../architecture/08-discovery-and-sync.md` §0, §1;
                Dynamo's reconciliation makes "add to cart" never lost but
                allows a deleted item to resurface — DeCandia et al. 2007 §4.4
                and Vogels's own summary of the paper. Checked 2026-07-25.

## DESCRIPTION

Somebody asked for an obvious feature: servers should find each other and sync
the newest state. Half of that was built. The other half was refused in
writing, and the refusal is the clearest distributed-systems argument in this
project.

"Sync the newest state" is last-writer-wins: when two servers disagree about
who owns an item, keep whichever record says it is more recent. In a system
where anybody can run a server — which is the whole point of this one — that
is a win button. A hostile self-host stamps its version of events with a
newer time and overwrites honest servers' records of item ownership, balances
and duel results. Every piece of machinery in `../architecture/04` and `05`
exists to defeat that attacker, and one convenience feature would hand them the
outcome.

What replaced it splits data by how adversarial it is. A server's own
directory record is self-asserted and nobody else may change it, so newer
genuinely should win there — ordered by a signed counter, never a clock. Item
ownership and duel outcomes converge on **cryptographic validity instead of
recency**: a chain that verifies and properly extends a known chain is
accepted, and a conflicting chain is not merged but treated as evidence of
misbehaviour. Recency is never authority.

## REAL-WORLD COUNTERPART

real — a genuine, widely used strategy with a genuine, widely known failure
mode.

Last-writer-wins is the default in several production databases, for good
reasons: it always terminates, needs no application logic, and is one
comparison. Its cost is that the losing write is destroyed with no record that
it existed.

Amazon's 2007 Dynamo paper is the canonical illustration of choosing not to do
it. Dynamo kept conflicting versions and handed them back to the application,
and its shopping cart merged them by union — so "add to cart" was never lost,
at the documented price that a deleted item could reappear. The trade is
honest: pick a resolution rule and you pick which surprise your users get.
There is no rule that surprises nobody.

The modern alternative is to design data so that conflicts cannot occur —
conflict-free replicated data types, whose merge gives the same answer whatever
order updates arrive in. That works for counters, sets and text, and not at all
for "who owns this unique item".
```

---

### 3.11 `cap-theorem(7)`

```
id:             cap-theorem
section:        7
name:           CAP theorem
canonical:      CAP theorem
gloss:          When the network splits, you must give up answers or accuracy.
status:         real
aliases:        Brewer's theorem, CAP
seeAlso:        network-partition(7), linearizability(7),
                eventual-consistency(7), partial-failure(7), quorum(7)
reading:        Gilbert and Lynch, "Brewer's Conjecture and the Feasibility of
                Consistent, Available, Partition-Tolerant Web Services", ACM
                SIGACT News 33(2), 2002; Eric Brewer, "CAP Twelve Years Later:
                How the 'Rules' Have Changed", IEEE Computer, 2012; Martin
                Kleppmann, "A Critique of the CAP Theorem", 2015
notes:          The two-of-three framing is the single most common error about
                this result and it will appear in almost any source a
                translator consults. Do not soften the correction: the page
                exists because the popular version is wrong.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  network-partition(7), linearizability(7),
                eventual-consistency(7)
hook:           Exit status 69 — "the server could not be reached. Nothing was
                sent" (`../client/04-terminology-and-education.md` §3.5). The
                client refuses rather than showing a guess, and every rendered
                value carries an authority state rather than a plausible number
                (`../client/01-visual-language.md` §2.2.8). That is a CAP
                choice, made deliberately, visible on screen.
misconception:  commonly believed the theorem says "pick two of consistency,
                availability and partition tolerance"; actually partitions are
                not something a designer picks — they are a thing the network
                does — so the real statement is much narrower: *during a
                partition*, a system must choose between answering requests and
                answering them correctly, and at all other times it need give
                up nothing.
transfer:       The player can now read any database's marketing as either "CP"
                or "AP" and translate it into a behaviour they can test: when
                the network splits, does this system return an error, or does
                it return an answer that might be out of date? They can also
                identify the claim "we are CA" as meaningless for anything that
                runs on a network. Platform: none needed.
verified:       Gilbert and Lynch's Theorem 1 — it is impossible in the
                asynchronous network model to implement a read/write data
                object that guarantees both availability and atomic consistency
                in all fair executions including those where messages are lost;
                their "consistency" is atomic consistency, i.e.
                linearizability as defined by Herlihy and Wing 1990; their
                "availability" requires every request to a non-failing node to
                receive a non-error response — Gilbert and Lynch 2002 §2–3.
                Brewer's own 2012 retrospective states that the two-of-three
                formulation is misleading. Checked 2026-07-25.

## DESCRIPTION

When your client cannot reach your home server, it reports exit 69 and shows
nothing rather than showing a stale number that looks current. That is a
choice, and it is the choice this theorem is about.

Stated carefully: **if the network between machines can break, a system cannot
be both always-answering and always-correct.** During a break — a partition —
a machine that is cut off has two options and only two. It can answer with
what it has, which may be out of date. Or it can refuse to answer until it
can be sure. There is no third behaviour, because the isolated machine has no
way to learn what happened on the other side.

The client takes the second option, everywhere. `../client/00-client-
overview.md` §2 makes the server authoritative and the client a renderer, and
`../client/01-visual-language.md` §2.2.8 gives every value an authority state
so that "we do not currently know this" is drawable. A game that guessed your
balance during a fault and reconciled later would take the first, and would
produce a player who spent ethecoin they did not have.

## REAL-WORLD COUNTERPART

real — a proved theorem, and one of the most misquoted results in the field.

The popular version says "pick two of consistency, availability and partition
tolerance". That is wrong, and it is wrong in a way that matters: partition
tolerance is not a property you choose. Networks partition. Choosing not to
tolerate partitions means choosing to break when one happens, which is not a
design, and "CA" is not an available option for anything that spans machines.

The actual result, proved by Gilbert and Lynch in 2002 from a conjecture of
Eric Brewer's, is narrower and stronger. In a network that may lose arbitrary
messages, no read/write store can guarantee both *availability* — every
request to a working node gets a non-error response — and *atomic consistency*
— the system behaves as though there were a single copy updated one operation
at a time. Both definitions are demanding, and the theorem is about those two
specifically, not about looser things called by the same words.

The choice applies only during a partition. Brewer's own 2012 retrospective
makes that the headline: the design work is deciding what to do while a
partition lasts and how to recover afterwards. The rest of the time a system
gives up nothing.
```

---

### 3.12 `consensus(7)`

```
id:             consensus
section:        7
name:           consensus
canonical:      consensus
gloss:          Getting independent machines to commit to one answer.
status:         real
aliases:        agreement
seeAlso:        quorum(7), bft-threshold(7), byzantine-fault(7),
                crash-fault(7), timeout(7), liveness(7), safety(7)
reading:        Fischer, Lynch and Paterson, JACM 32(2), 1985; Diego Ongaro
                and John Ousterhout, "In Search of an Understandable Consensus
                Algorithm" (Raft), USENIX ATC 2014
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  distributed-system(7), partial-failure(7)
hook:           A cross-server duel. `../design/13-multiplayer-and-federation-
                play.md` §3 defines it as the engagement whose outcome must be
                trusted across servers, and `../architecture/05-validator-
                quorum.md` §5 is the six-step loop that produces one answer
                both servers will accept.
misconception:  commonly believed that agreement is easy once everyone can talk
                — just take a vote; actually voting is the easy part, and the
                hard part is that a machine which has not answered may be dead,
                may be slow, or may answer in a moment with something
                different, so "the votes are in" is a judgement rather than an
                observation.
transfer:       The player can now say what a database or cluster manager means
                when it reports "no leader elected" or "lost quorum", and why
                such a system typically stops serving writes rather than
                guessing. They will recognise the same shape in Kubernetes,
                etcd, ZooKeeper and Postgres failover documentation, all of
                which describe leader election in these terms.
                Platform: none needed.
verified:       Consensus is impossible in an asynchronous system with even one
                crash fault, for deterministic protocols — Fischer, Lynch and
                Paterson 1985; practical protocols achieve safety always and
                liveness under partial synchrony — Raft (Ongaro and Ousterhout
                2014) §2, §5, and the same property stated for PBFT in Castro
                and Liskov 1999. Checked 2026-07-25.

## DESCRIPTION

Two players on two different home servers fight over an item. Both servers
must end up believing the same thing about who owns it afterwards, and neither
gets to simply declare it — that is the cheating problem the whole federation
layer exists to solve.

Consensus is that requirement, stated generally: a set of machines, each with
its own opinion, must all commit to a single value, and must not be able to
change their minds afterwards. Three things are being asked for at once.
Everyone who decides decides the same thing (agreement). The thing decided was
proposed by somebody rather than invented (validity). And everyone eventually
decides at all (termination).

The reason this is hard is timeout(7)'s reason. A machine that has not replied
might be dead, might be slow, or might be about to reply with something
inconvenient, and no observer can tell those apart. So a protocol that waits
for everyone can wait forever, and a protocol that stops waiting has decided
without hearing from someone who may still be there.

## REAL-WORLD COUNTERPART

real — and the difficulty is provable rather than a matter of engineering
skill.

The 1985 impossibility result is the boundary: in an asynchronous network with
no bound on message delay, no deterministic protocol can guarantee that
machines will agree if even one may crash. Not "is hard to build" —
impossible.

Every real protocol therefore relaxes one of the assumptions, and they nearly
all relax the same one in the same way. Raft, Paxos, ZooKeeper's Zab and
PBFT are all **always safe and eventually live**: they will never produce two
conflicting decisions, and they will produce a decision once the network
behaves for long enough. When it does not behave, they stop rather than guess.
That is why a cluster with too few reachable members refuses writes and reports
"lost quorum" instead of carrying on.

This game's federation makes the same trade. A cross-server outcome that
cannot gather enough signatures does not happen, and the player sees a refusal
rather than a result that might later be reversed.
```

---

### 3.13 `byzantine-fault(7)`

```
id:             byzantine-fault
section:        7
name:           Byzantine fault
canonical:      Byzantine fault
gloss:          A machine that lies, and lies differently to different peers.
status:         real
aliases:        Byzantine failure, arbitrary fault
seeAlso:        crash-fault(7), bft-threshold(7), quorum(7), equivocation(7),
                consensus(7), non-recognition(7), digital-signature(7)
reading:        Lamport, Shostak and Pease, "The Byzantine Generals Problem",
                ACM TOPLAS 4(3), 1982; Pease, Shostak and Lamport, "Reaching
                Agreement in the Presence of Faults", JACM 27(2), 1980; Castro
                and Liskov, "Practical Byzantine Fault Tolerance", OSDI 1999
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  crash-fault(7), consensus(7)
hook:           `../architecture/03-server-and-federation.md` §1, which states
                as a design consequence that "adversarial, untrusted servers
                exist by design" — a self-hosted server could mint items from
                nothing, declare its player won every duel, or run dishonest
                validators. The player joining a federated server is joining a
                system whose threat model is this page.
misconception:  commonly believed that fault tolerance means surviving crashes
                and hardware failure, so a system that handles those is
                robust; actually a crash is the *easy* failure — it is honest
                — and the expensive case is a participant that keeps running
                and sends well-formed, plausible, contradictory messages,
                which no amount of redundancy alone survives.
transfer:       The player can now explain why every self-hostable federated
                network — Matrix, Mastodon, email — has an abuse problem that
                a single-operator service does not, and can state the reason in
                one sentence: anybody can run a participant, so the protocol
                has to assume some participants are hostile rather than merely
                broken. Platform: none needed.
verified:       Byzantine agreement with unauthenticated ("oral") messages
                requires n ≥ 3f+1 — Pease, Shostak and Lamport 1980; with
                digital signatures the synchronous problem is solvable for any
                number of faulty parties, yet practical asynchronous protocols
                still need 3f+1 — Lamport, Shostak and Pease 1982 (algorithm
                SM(m)) and Castro and Liskov 1999, whose 3f+1 is described as
                optimal resilience. The name comes from the 1982 paper's
                framing device. Checked 2026-07-25.

## DESCRIPTION

Anyone can run a home server here. That is a stated requirement, and
`../architecture/03-server-and-federation.md` §1 spells out what follows from
it: a self-hosted server can try to create items out of nothing, declare its
own player the winner of every duel, or run validators that sign whatever
suits it. Those servers are not a bug in the design; they are assumed to
exist.

A **crash fault** is a machine that stops. It is honest: everything it ever
said was true, and now it says nothing. A **Byzantine fault** is a machine
that keeps running and behaves arbitrarily — including sending correct-looking
messages that are false, and including telling one peer one thing and another
peer the opposite.

That last part is the expensive bit. Against a crashed machine, more copies
help: ask three, one is down, two answer, done. Against a lying machine, more
copies do not help by themselves, because the extra copies may be the liar's
and every answer looks equally well-formed. You need a rule about *how many
agreeing answers is enough*, and the rule has to assume some of the agreement
is manufactured. See bft-threshold(7).

## REAL-WORLD COUNTERPART

real — the standard term, from a 1982 paper that named it with a story about
generals besieging a city who must agree to attack or retreat while some of
them are traitors sending different orders to different colleagues.

The threat model is used wherever participants cannot be vetted: public
blockchains, aircraft and spacecraft flight-control computers voting on sensor
readings, and federated networks where anybody can stand up a server. It is
also, quietly, the model for hardware that fails strangely rather than
cleanly — a cosmic-ray bit flip and a malicious message are the same thing to
a protocol.

Two results from the original papers are worth carrying away. With plain
unauthenticated messages, agreement is possible only when the number of
participants exceeds three times the number of faulty ones. With digital
signatures, a message cannot be altered in transit or falsely attributed, and
the synchronous version of the problem becomes solvable for any number of
liars — which is why this game signs everything. Signatures do not, however,
make the practical threshold go away, and the reason is on the next page.
```

---

### 3.14 `quorum(7)`

```
id:             quorum
section:        7
name:           quorum
canonical:      quorum
gloss:          The smallest group whose agreement is enough to decide.
status:         real
seeAlso:        bft-threshold(7), consensus(7), byzantine-fault(7),
                validator-quorum(7), replication(7), liveness(7)
reading:        Castro and Liskov, "Practical Byzantine Fault Tolerance", OSDI
                1999 §4; DeCandia et al., "Dynamo", SOSP 2007 §4.5 (R + W > N)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  consensus(7), replication(7)
hook:           `quorum(7)` is already listed as a shipping concept page in
                `../client/04-terminology-and-education.md` §3.10. The player
                meets the thing itself at a cross-server duel, where a
                committee of 7 servers is sampled and 5 must sign
                (`../architecture/05-validator-quorum.md` §1).
misconception:  commonly believed a quorum is a majority, so more than half is
                the general rule; actually a majority is only the right size
                when participants can fail by stopping. The size is derived
                from a requirement — that any two decision-making groups must
                share at least one honest member — and different failure
                assumptions give different sizes, of which "more than half" is
                the friendliest case.
transfer:       The player can now read the replication settings of any
                quorum-based store — Cassandra, Riak, MongoDB, etcd — and say
                what R and W mean and why the documentation insists that
                R + W > N. They can also predict the behaviour of a three-node
                cluster that loses two nodes: it stops accepting writes,
                deliberately, because the survivor cannot form a quorum.
                Platform: none needed.
verified:       PBFT forms quorums of size 2f+1 from 3f+1 replicas, so any two
                quorums intersect in at least f+1 nodes and therefore in at
                least one correct node — Castro and Liskov 1999 §4; Dynamo's
                R + W > N condition for read/write quorum overlap — DeCandia
                et al. 2007 §4.5. Checked 2026-07-25.

## DESCRIPTION

When a duel is adjudicated here, seven servers are sampled and five of them
must sign the outcome. Five is the quorum: the smallest group whose agreement
the federation will act on.

The number is not a vote threshold picked for feel. It comes from a property
the design needs, which is worth stating plainly because everything else
follows from it: **any two groups that could each decide something must share
at least one honest member.** If two disjoint groups could each reach a
decision, they could reach opposite decisions, and both would be valid. The
system would then hold two contradictory truths with no way to prefer either.

So a quorum is sized by overlap, not by fairness. Ask how many participants
exist, how many might be untrustworthy, and how many you are willing to wait
for — and the smallest size that guarantees the overlap is the answer. The
arithmetic for the lying case is on bft-threshold(7).

The cost is stated as plainly. A quorum you cannot assemble is a decision you
cannot make. A system that requires five of seven and can only reach four
stops, and stopping is the correct behaviour, because the alternative is
deciding with an overlap you cannot guarantee.

## REAL-WORLD COUNTERPART

real — and the reader has probably configured one without being told this is
what it was.

Distributed databases expose quorums as two numbers over a replication factor:
write to W copies, read from R copies, out of N. The documentation always
insists on R + W > N, and this page is why — that inequality is exactly the
statement that a read group and a write group must overlap in at least one
copy, so a read cannot entirely miss a completed write. Set R = W = 1 on three
copies and you have opted out of the guarantee, which is sometimes the right
call and never an accident worth having.

Cluster managers expose it as a member count. A three-node etcd or ZooKeeper
cluster tolerates one failure and refuses writes at two, which is also why
production advice is always an odd number of members: four nodes tolerate one
failure, exactly like three, and cost more.

Where participants may lie rather than merely stop, the sizes change. See
bft-threshold(7).
```

---

### 3.15 `bft-threshold(7)`

```
id:             bft-threshold
section:        7
name:           BFT threshold
canonical:      BFT threshold
gloss:          How many signers must agree before an answer is safe to believe.
status:         real
aliases:        Byzantine quorum, 2f+1 of 3f+1, supermajority
glossary:       ../design/glossary.md
seeAlso:        quorum(7), byzantine-fault(7), validator-quorum(7),
                equivocation(7), consensus(7), digital-signature(7)
reading:        Castro and Liskov, "Practical Byzantine Fault Tolerance", OSDI
                1999; Pease, Shostak and Lamport, JACM 27(2), 1980
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  byzantine-fault(7), quorum(7)
hook:           `../architecture/05-validator-quorum.md` §1, and the number the
                player actually sees: a committee of 7, of which 5 must agree.
misconception:  commonly believed the two-thirds threshold exists because you
                need a convincing majority, or because you cannot tell who
                lied; actually it comes from arithmetic that can be done on
                one line — you must be able to decide after hearing from
                everyone except the f you may never hear from, and two such
                groups must still overlap in someone honest.
transfer:       The player can now check the fault tolerance claim of any
                Byzantine-fault-tolerant system they read about — a blockchain
                validator set, an aviation voting computer — by dividing:
                a network of 100 validators tolerates 33, and one that claims
                to tolerate 40 of 100 is claiming something the arithmetic does
                not permit. Platform: none needed.
verified:       PBFT uses 3f+1 replicas with quorums of 2f+1; two such quorums
                intersect in at least f+1 replicas, hence in at least one
                correct replica; 3f+1 is described as optimal resilience for
                asynchronous Byzantine agreement — Castro and Liskov 1999;
                the n ≥ 3f+1 lower bound for oral messages — Pease, Shostak and
                Lamport 1980. ⚠ The game states the threshold in two units;
                see §2.4 and DS-3. Checked 2026-07-25.

## DESCRIPTION

Seven validators are sampled for a duel and five must sign. Both numbers come
from one line of arithmetic, worth doing rather than asserting.

Write the number of participants as N and the number that may be lying as f.
You cannot wait for everyone, because the f dishonest ones may simply never
answer, and a system that waits for a machine that will not answer never
finishes. So you must be able to decide after hearing from **N − f**.

Now consider two decisions made at different moments, each by some group of
N − f. They may not be the same group. Two groups of size N − f drawn from N
must share at least N − 2f members. For the decisions to be compatible, that
shared part must contain at least one honest participant, so it must be bigger
than the f liars: **N − 2f > f**, which is **N > 3f**.

The smallest N for a given f is therefore 3f + 1, and the group you wait for is
N − f = 2f + 1. Put f = 2 and you get 7 and 5. That is where the committee size
in this game comes from, and it is why the committee is 7 rather than 6 or 8.

## REAL-WORLD COUNTERPART

real — the standard threshold, and this game uses it unchanged.

Practical Byzantine Fault Tolerance, published by Castro and Liskov in 1999,
was the first protocol to do this efficiently enough to matter, and 3f+1 is
described there as optimal: no protocol does better in an asynchronous network.
Proof-of-stake systems restate the bound as "more than two-thirds of voting
power must be honest" — the same inequality with weights instead of a
headcount.

One correction, because the wrong inference is natural: signatures do not
remove the bound. Signing means a message cannot be altered or falsely
attributed, and in a *synchronous* network — where silence proves the sender is
faulty — that is enough to tolerate any number of liars. Real networks are not
synchronous. Silence proves nothing (see timeout(7)), so you must still proceed
after N − f replies, and the overlap argument still applies. Every signature
here is Ed25519 and the threshold is still 5 of 7.

As a ratio: a Byzantine system survives fewer than a third of its participants
being dishonest, and no more.
```

---

### 3.16 `equivocation(7)`

```
id:             equivocation
section:        7
name:           Equivocation
canonical:      Equivocation
gloss:          Signing two answers that contradict each other, and being caught.
status:         real
aliases:        double-signing, double signing
glossary:       ../design/glossary.md
seeAlso:        byzantine-fault(7), double-spend(7), bft-threshold(7),
                validator-reputation(7), non-recognition(7), digital-signature(7),
                fork(7)
reading:        Ethereum consensus specification, "slashing" conditions;
                Cosmos SDK evidence module documentation
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  byzantine-fault(7), digital-signature(7)
hook:           `../architecture/05-validator-quorum.md` §3.3 — the "hard
                slash". A validator that signs two conflicting outcomes for one
                duel is dropped to a reputation floor or ejected outright, with
                no benefit of the doubt, unlike a merely divergent vote.
misconception:  commonly believed that catching a dishonest participant
                requires trusting somebody's account of what they did;
                actually this particular offence needs no trust at all — the
                two contradictory signatures are the evidence, anyone holding
                both can check them without asking anyone, and the accused
                cannot deny producing them without conceding that their key was
                stolen.
transfer:       The player can now say what "slashing" means in any
                proof-of-stake system they read about, and why double-signing
                is punished far more harshly than being offline: one is proof,
                the other is an absence. They can also state the general form —
                any two signed statements that cannot both be true, from one
                key, are self-contained evidence — which is the pattern behind
                certificate-transparency misbehaviour proofs.
                Platform: none needed.
verified:       Equivocation is treated as immediate and severe with no
                averaging, because both contradicting signatures exist and the
                proof is cryptographic — `../architecture/05-validator-
                quorum.md` §3.3; divergence is treated separately and more
                mildly because it can be caused by lag or a race — §3.2;
                the same asymmetry (harsh for double-signing, mild for
                downtime) is standard in deployed proof-of-stake systems —
                Ethereum consensus specification slashing conditions and Cosmos
                SDK evidence handling. Checked 2026-07-25.

## DESCRIPTION

A sampled validator has three ways to be wrong, and the federation treats them
as three different things.

It can fail to answer. That costs it uptime, lightly, because being offline is
unavailability rather than dishonesty. It can sign an outcome that disagrees
with the one the committee reached, which costs it a fifth to a third of its
reputation — genuine disagreement is possible from lag or a race, so this is
not treated as proof of anything. Or it can sign **two conflicting outcomes for
the same duel**, and that is equivocation, and the penalty is immediate: a
reputation floor or ejection from the validator pool, with no averaging and no
benefit of the doubt.

The asymmetry is not severity for its own sake; it is a statement about
evidence. The first two cases are inferences — someone did not reply, someone
disagreed — and inferences can be wrong. Equivocation is not an inference. Both
signatures exist, anyone holding both can verify them against the same public
key, and the validator cannot argue the point without arguing that its key was
stolen, which is a concession rather than a defence.

It is also why this offence triggers federation-wide non-recognition
automatically, while softer fraud needs somebody to detect and propagate a
judgement.

## REAL-WORLD COUNTERPART

real — the standard term, and the standard treatment.

Proof-of-stake blockchains call the penalty *slashing* and apply it in exactly
this shape. Signing two conflicting blocks for the same slot destroys a large
part of a validator's stake and ejects it; being offline costs a small,
gradual amount. Both rules are in the protocol rather than in anyone's
judgement, because both are decidable from data any participant can hold.

The general pattern is worth taking away, because it recurs everywhere: **two
signed statements from one key that cannot both be true form a proof of
misbehaviour that needs no authority to evaluate.** Certificate Transparency
uses the same idea to catch a log server that presents different histories to
different clients. A system that can be *shown* to have lied is in a stronger
position than one that must be *believed* to have lied, and designing for
self-contained evidence is the way you get there.

Note what it does not do: it catches contradiction, not falsehood. A validator
that lies once, consistently, to everybody, has produced one signature and no
proof.
```

---

### 3.17 `sybil-attack(7)`

```
id:             sybil-attack
section:        7
name:           Sybil attack
canonical:      Sybil attack
gloss:          One person wearing many faces to outvote everybody else.
status:         real
aliases:        sockpuppet attack
seeAlso:        quorum(7), bft-threshold(7), validator-reputation(7),
                federation(7), peer-discovery(7), public-key-cryptography(7)
reading:        John R. Douceur, "The Sybil Attack", IPTPS 2002; Levine,
                Shields and Margolin, "A Survey of Solutions to the Sybil
                Attack" (2006)
notes:          The name is from a 1973 book about a patient with many
                identities. Keep the name untranslated — it is the term of art
                in every language's literature — and translate the gloss.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  quorum(7), public-key-cryptography(7)
hook:           Two surfaces. Home servers ship with allowlists and the
                operator controls who joins (`../architecture/03-server-and-
                federation.md` §1), and peer exchange has an open question
                against exactly this: "a hostile peer can advertise many fake
                peers" (`../architecture/08-discovery-and-sync.md` §6, G-3).
misconception:  commonly believed that a system with enough participants is
                safe because an attacker cannot control most of them; actually
                the count of *participants* is not the count of *people* — one
                person can create identities as fast as they can generate
                keys — so any threshold expressed in identities is meaningless
                unless identities are expensive to obtain.
transfer:       The player can now explain why a new account on almost any
                platform is limited — rate limits, phone verification, waiting
                periods, invitation codes — and identify each as a way of
                making identities cost something. They can also say why online
                polls without such friction carry no information whatsoever.
                Platform: none needed.
verified:       Douceur's result — without a logically central authority, Sybil
                attacks are always possible except under extreme and
                unrealistic assumptions of resource parity and coordination —
                Douceur 2002, conclusion; the game's allowlist model and the
                open Sybil question in peer exchange — `../architecture/03` §1
                and `../architecture/08` §6 G-3; the reputation floor for new
                validators — `../architecture/05` §2.5. Checked 2026-07-25.

## DESCRIPTION

Every threshold in this federation is counted in servers. Five of seven must
sign a duel outcome; a Byzantine system survives fewer than a third of its
participants being dishonest. Both of those sentences quietly assume that
seven servers means seven independent operators.

A Sybil attack is one operator running many. Generating a key pair costs
nothing and takes microseconds, so if a validator's identity is a key, one
person with a laptop can be fifty validators. Every threshold expressed as a
fraction of identities then measures nothing, because the attacker supplies
the denominator as well as the numerator.

The game's answers to this are visible and worth noticing as answers. A home
server has an allowlist, so its operator decides who plays there. Federation
is opt-in, so a server chooses whose existence it cares about. New validators
start at a reputation floor rather than at parity, so a freshly created
identity is sampled occasionally and outweighed by proven ones until it has a
record. And `../architecture/08-discovery-and-sync.md` §6 keeps the remaining
hole open honestly: a hostile peer can advertise many fake peers, bounds limit
the damage, and whether reputation should gate who is believed in peer exchange
is unresolved.

## REAL-WORLD COUNTERPART

real — named in a 2002 paper by John Douceur, whose conclusion is the reason
this is a permanent design constraint rather than a bug.

Douceur showed that without a logically centralised authority issuing
identities, Sybil attacks are always possible, except under assumptions about
equal resources and coordinated verification that no real system meets. That
is an uncomfortable result, because the whole appeal of a decentralised system
is not having such an authority.

Every deployed defence therefore attacks the *cost* of an identity rather than
the possibility of one. Proof-of-work makes each vote cost electricity.
Proof-of-stake makes it cost capital that can be destroyed. Social systems make
it cost an invitation, a phone number, or a waiting period. Reputation systems
make it cost time, which is the approach this game takes.

None of them prevents an identity from being created. They make creating
thousands expensive enough that the attack stops paying, and the design
question is only ever how expensive is enough.
```

---

### 3.18 `aimd(7)`

```
id:             aimd
section:        7
name:           AIMD
canonical:      AIMD
gloss:          Earn trust slowly, lose it fast — the shape borrowed from TCP.
status:         real
aliases:        additive increase multiplicative decrease
seeAlso:        validator-reputation(7), equivocation(7), cold-start(7),
                bft-threshold(7), backoff(7)
reading:        RFC 5681 (TCP Congestion Control); RFC 9438 (CUBIC); Dah-Ming
                Chiu and Raj Jain, "Analysis of the Increase and Decrease
                Algorithms for Congestion Avoidance in Computer Networks",
                Computer Networks and ISDN Systems 17(1), 1989
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  validator-reputation(7)
hook:           `../architecture/05-validator-quorum.md` §3, which names the
                borrowing outright: the reputation update rule "mirrors AIMD
                (additive-increase / multiplicative-decrease) from TCP
                congestion control — the proven 'reward slowly, punish fast'
                shape for adversarial systems".
misconception:  commonly believed a score should move by the same amount in
                both directions, because anything else is unfair; actually
                symmetric adjustment is the wrong shape whenever the two errors
                cost different amounts, and in both congestion control and
                trust the cost of being too generous is far higher than the
                cost of being too cautious — so the correct rule is
                deliberately asymmetric.
transfer:       The player can now read what their own network connection does
                under load. Run a large download and watch the throughput: the
                slow climb and the sudden drop is this algorithm, once per
                round trip. They can also name their machine's congestion
                control — `sysctl net.ipv4.tcp_congestion_control` on Linux —
                and say what the answer means. Platform: Linux for the sysctl;
                the observation works anywhere. See ED-8.
verified:       The reputation rule is explicitly AIMD-shaped with α = 0.05 for
                a correct vote and β = 0.2–0.3 for divergence —
                `../architecture/05-validator-quorum.md` §3.1–3.2; TCP's
                additive-increase/multiplicative-decrease congestion avoidance
                — RFC 5681 §3.1; CUBIC, the default on Linux since kernel
                2.6.19 (2006) and on Windows and Apple stacks, keeps a
                multiplicative decrease of 0.7 and replaces the additive
                increase with a cubic function — RFC 9438; AIMD converges to
                fairness and efficiency where other increase/decrease
                combinations do not — Chiu and Jain 1989. Checked 2026-07-25.

## DESCRIPTION

A validator's reputation moves after every round it is sampled for, and it
moves by very different amounts in the two directions. A correct vote adds
about 5 % of the remaining distance to 1.0, so trust takes dozens of rounds to
build. A vote that diverged from the committee's outcome multiplies the score
by roughly 0.7 to 0.8, so it falls in one step. Provable equivocation skips the
formula entirely and drops the validator to the floor.

That shape has a name and a source: additive increase, multiplicative
decrease, taken from TCP congestion control. It is worth knowing that the
borrowing is deliberate rather than a coincidence of tuning, because it is
the same problem twice.

The reason the shape works is that the two mistakes are not equally
expensive. Being slightly too cautious with a good validator costs a few extra
rounds before it is trusted. Being slightly too generous with a bad one costs
a signed outcome that should not exist. When the costs are that asymmetric,
the correct response is asymmetric too, and "reward slowly, punish fast" is
that asymmetry written as two lines of arithmetic.

## REAL-WORLD COUNTERPART

real — the same algorithm that decides how fast the reader's downloads go.

A TCP connection has no way to ask the network how much capacity is available,
so it probes: it sends slightly more each round trip — additive increase — and
when a packet is lost, it cuts its sending rate by a large fraction all at
once — multiplicative decrease. That is the sawtooth pattern in any throughput
graph. Chiu and Jain proved in 1989 that among the simple increase/decrease
combinations, this one is the only one that converges to both efficiency and
fairness between competing connections.

The modern default is a refinement rather than a replacement. CUBIC, the
default on Linux since 2006 and on Windows and Apple systems, keeps the
multiplicative decrease — it cuts to 0.7 of the window — and replaces the
straight-line increase with a curve that returns quickly to the previous rate
and then probes cautiously above it.

The transferable idea is the general one: when you must estimate something you
cannot observe, and guessing high is much more expensive than guessing low,
approach the answer slowly and retreat from it quickly.
```

---

### 3.19 `did(7)`

```
id:             did
section:        7
name:           DID
canonical:      DID
gloss:          A name you prove you own with a key, not one issued to you.
status:         real
aliases:        decentralized identifier, did:plc, did:web
glossary:       ../design/glossary.md
seeAlso:        pds(7), public-key-cryptography(7), digital-signature(7),
                trust-anchor(7), provenance-record(5), home-server(7),
                sybil-attack(7)
reading:        W3C "Decentralized Identifiers (DIDs) v1.0",
                Recommendation 19 July 2022; AT Protocol specification,
                "Identity"; did:web method specification
notes:          Write DID in prose and `did` in identifiers
                (../design/glossary.md). Do not translate the string
                literals `did:plc:` or `did:web:`.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  public-key-cryptography(7)
hook:           The `identity` window, and the first provenance record
                the player reads — where the thing that owned an item
                is a `did:plc:` string rather than a username.
misconception:  commonly believed an identifier has to be issued by
                somebody — a company, a registrar, a government — so a
                decentralized one must be a gimmick or a blockchain;
                actually the identifier is derived from a key you hold,
                and "owning" it means being able to sign as it. Nobody
                issues it, and nobody can revoke it, because there is
                no registry to strike you from.
transfer:       Open any Bluesky profile in a browser and view its
                account's DID — it is a `did:plc:` string, and it stays
                the same when the handle changes. Then read the
                did:web method specification, which resolves a DID by
                fetching `/.well-known/did.json` over HTTPS: a DID you
                could publish yourself this afternoon with a web server.
                Runs on any platform.
verified:       DIDs as key-derived, self-certifying identifiers, and
                the resolution model — W3C DID Core v1.0 Recommendation
                (19 July 2022) §3, §7; did:plc and did:web as the
                methods AT Proto uses, and DID stability across handle
                changes — AT Protocol specification "Identity";
                identity-only use and Invariant I14 —
                ../architecture/02-identity-and-auth.md §1, §3.
                Checked 2026-07-25.

## DESCRIPTION

Everything in the ledger and every provenance record names its parties
by DID, not by handle. A handle is a label you chose and can change; a
DID is what you actually are to the system.

The difference is where the identifier comes from. A username is issued
— some service decided you may have it, and that service can take it
back. A DID is derived from a key pair you hold. You "own" it in the
only sense that matters to a verifier: you can produce signatures that
check against it and nobody else can.

That is why your history survives a handle change, and why an item
granted to you two months ago still verifies as yours. It is also why
losing the key is serious in a way that losing a password is not: there
is no registry holding a copy, and therefore nobody to appeal to.

This game uses DIDs for **identity only** — never for game state. Your
items, balances and rig live in your home server's database, and the
reasons are in `pds(7)`.

## REAL-WORLD COUNTERPART

real — DIDs are a W3C Recommendation (v1.0, July 2022) and are what
AT Protocol, and therefore Bluesky, uses for every account. The DID on
a Bluesky account today is exactly the kind of string this game means.

Two methods matter here. `did:plc` is AT Proto's own, resolved through a
directory service — a pragmatic compromise, not a fully decentralized
one, and worth knowing as such. `did:web` resolves by fetching a JSON
document over HTTPS from a domain you control, which means anybody with
a web server can mint one. Neither is a blockchain, and neither costs
anything.

The property that makes them worth the trouble is *stability across
infrastructure*: the identifier keeps working when the handle changes,
when the hosting provider changes, and when the company that operated
your first server no longer exists.
```

---

### 3.20 `pds(7)`

```
id:             pds
section:        7
name:           PDS
canonical:      PDS
gloss:          The server holding an account's identity records, and nothing else.
status:         real, simplified
aliases:        Personal Data Server, personal data server
glossary:       ../design/glossary.md
seeAlso:        did(7), home-server(7), federation(7),
                distributed-system(7), trust-anchor(7)
reading:        AT Protocol specification, "Personal Data Server" and
                "Repository"; ../architecture/02-identity-and-auth.md §3
notes:          ⚠ The CAVEATS section states Invariant I14 and is not
                optional. A shortened version of this page that drops
                it teaches the opposite of the architecture.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  did(7), home-server(7)
hook:           Signing in with an existing Bluesky account and
                discovering the game did not ask you to create one —
                and that your items are nonetheless not stored there.
misconception:  commonly believed that if your identity is portable then
                your data is too, so an account's "home" holds
                everything about you; actually the two are separable and
                this game separates them deliberately — identity is
                portable and player-controlled, game state is not and
                must not be.
transfer:       AT Proto lets you run your own PDS and point your DID at
                it. Read the AT Protocol specification's Repository
                section to see what an account's repository actually
                holds — signed records, in a Merkle structure — and
                notice that it is a *personal* store, which is exactly
                why an adversarial game may not use it. Runs on any
                platform.
simplified:     A real PDS holds an account's whole public repository —
                posts, follows, likes — as signed records. This game
                touches none of that. It uses AT Proto to answer one
                question ("is this the holder of this DID?") and reads
                nothing else, so the page describes the part of a PDS
                the game actually meets.
verified:       PDS as the host of an account's signed repository —
                AT Protocol specification, "Personal Data Server" and
                "Repository"; authentication-only use, and the explicit
                rejection of writing game records into a player's PDS
                via Lexicons — ../architecture/02-identity-and-auth.md
                §2, §3 (Invariant I14). Checked 2026-07-25.

## DESCRIPTION

Your DID resolves to a Personal Data Server — the machine that holds
your identity records and answers the question of whether a given key
really is yours. It may be run by a company, or by you.

The game talks to it once, to sign you in, and then stops. Your items,
your ethecoin, your rig, your heat and your provenance chains live in
your **home server's** database, which is a different machine with a
different job (`home-server(7)`).

That separation is deliberate and it is worth understanding rather than
accepting, because the obvious design is the wrong one. AT Proto would
technically let this game write item records into your own PDS. It does
not, and never will.

## REAL-WORLD COUNTERPART

real, simplified — a PDS is a real component of AT Protocol, and
self-hosting one is a supported thing people actually do. An account's
repository there is a signed, Merkle-structured collection of records,
which is genuinely elegant: your posts are verifiable as yours
independently of who is serving them.

The simplification is scope. This game meets a vanishingly small part of
a PDS — the identity part — and this page describes that part. The rest
of the repository model is real and interesting and irrelevant here.

## CAVEATS

**Game state never lives in a player's PDS.** This is Invariant I14, and
the reasoning is the single clearest lesson in this domain: a PDS is
*player-controlled infrastructure*, and this is an adversarial game. Any
value a cheater would want to forge — an item, a balance, a rig stat —
must live somewhere the cheater does not control, or it is not a fact,
it is a claim.

The general principle transfers well beyond games, and is worth carrying:
**self-sovereign identity is not self-sovereign accounting.** Letting
someone prove who they are is a completely different problem from letting
them tell you what they own, and systems that conflate the two are
trivially exploitable.
```

---

### 3.21 `canonicalization(7)`

```
id:             canonicalization
section:        7
name:           canonicalization
canonical:      canonicalization
gloss:          Writing the same data one fixed way, so two copies hash alike.
status:         real
aliases:        canonicalisation, canonical form, JCS, normalization
seeAlso:        hash(7), digital-signature(7), provenance-record(5),
                character-encoding(7), utf-8(7), byte(7)
reading:        RFC 8785 "JSON Canonicalization Scheme (JCS)";
                RFC 8259 (JSON); ECMAScript number-to-string
                (ECMA-262 §6.1.6.1.20); ../architecture/04-item-provenance.md §4
notes:          ⚠ Both spellings are in use; `canonicalization` is the
                RFC's and is the canonical id. Translators should keep
                the RFC's term rather than a local word for "normalise".
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  hash(7), character-encoding(7)
hook:           The first time `verify` refuses a record that looks
                perfectly fine on screen, because the bytes it was
                signed over are not the bytes that arrived.
misconception:  commonly believed a signature covers "the data", so two
                copies of the same object with the same fields must
                verify the same way; actually a signature covers
                *bytes*, and one object can be written as many different
                byte strings — reordered keys, different spacing,
                `1.0` versus `1` — every one of which hashes to
                something different. Somebody has to decide which
                spelling is the real one before anybody signs anything.
transfer:       In any terminal, run
                `printf '{"a":1,"b":2}' | shasum -a 256` and then
                `printf '{"b":2,"a":1}' | shasum -a 256`. Same object,
                two completely different hashes, and therefore two
                different signatures. Now read RFC 8785 §3, which is
                the list of decisions somebody had to make to stop that
                happening. Assumes a Unix shell — see ED-8.
verified:       JCS as the canonicalization scheme, its key-ordering by
                UTF-16 code unit and its number formatting rule —
                RFC 8785 §3.2.3, §3.2.2.3, referencing ECMA-262
                §6.1.6.1.20; the game's use of JCS —
                ../architecture/04-item-provenance.md §4; the
                unpaired-surrogate rejection requirement — RFC 8785
                §3.2.2.2, and the project's own resolution log entry of
                2026-07-23 in ../design/15-open-questions.md §3.
                Checked 2026-07-25.

## DESCRIPTION

A signature is arithmetic over bytes. It does not know what a field is.

That is the whole problem. `{"a":1,"b":2}` and `{"b":2,"a":1}` are the
same object to every program that reads JSON, and they are two different
byte strings, so they hash differently and a signature over one will not
check against the other. Add whitespace, or write `1.0` instead of `1`,
or escape a character differently, and you have another one.

So before anything can be signed, both sides must agree on exactly one
way to write it down. That agreement is canonicalization: sort the keys
this way, format numbers that way, escape strings this way, use no
insignificant whitespace. This game uses JCS, which is RFC 8785.

The reason it belongs in the adversarial stage rather than filed under
tidiness: a canonicalizer that lets two different inputs produce the
same bytes is not a formatting bug, it is a forgery primitive. One
signature that covers two distinct payloads means an attacker can get
you to sign one thing and present it as another.

## REAL-WORLD COUNTERPART

real, and this game uses the actual standard. JCS (RFC 8785) fixes
property order by UTF-16 code unit, and borrows JavaScript's
number-to-string algorithm — an odd-looking choice that is deliberate,
because it was already implemented identically everywhere.

The idea is much older than JSON. XML had Canonical XML for the same
reason and it was notoriously difficult; the XML signature-wrapping
attacks of the late 2000s are what happens when the canonical form and
the parsed form disagree about what a document says.

This project met the failure mode directly. Its bundled canonicalizer
passed a lone unpaired surrogate straight through, and the UTF-8 encoder
then substituted a replacement character — so two genuinely different
payloads produced **identical signing bytes**. RFC 8785 §3.2.2.2
requires that this be an error; the library did not raise it, so the
wrapper does. That is a real forgery primitive found in real code, and
the fix is one check.
```

---

### 3.22 `append-only-log(7)`

```
id:             append-only-log
section:        7
name:           append-only log
canonical:      append-only log
gloss:          A record you may add to and never revise, where edits show.
status:         real
aliases:        transparency log, hash chain, tamper-evident log,
                write-once log
seeAlso:        hash(7), provenance-chain(7), provenance-record(5),
                digital-signature(7), equivocation(7), last-writer-wins(7)
reading:        RFC 6962 "Certificate Transparency"; RFC 9162 (CT v2.0);
                Haber & Stornetta, "How to Time-Stamp a Digital
                Document", Journal of Cryptology 3(2), 1991;
                git-log(1), git-cat-file(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  hash(7)
hook:           The public ledger — a record of transfers that anybody
                can read and nobody, including the server that wrote it,
                can quietly revise.
misconception:  commonly believed a tamper-*evident* record is the same
                as a tamper-*proof* one, so an append-only log stops
                somebody changing history; actually nothing stops them
                editing the file. What the structure guarantees is that
                the edit becomes *visible* — every hash after the change
                is now wrong. It converts a silent problem into a loud
                one, which is a smaller claim and a much more achievable
                one.
transfer:       In any git repository, run `git log --format='%h %p'` to
                see each commit naming its parent, then
                `git cat-file -p HEAD` to see that the commit object
                literally contains a `parent <hash>` line — which is why
                the commit id, a hash over that content, changes if any
                ancestor changes. That is a hash chain you already use
                every day. Runs anywhere git is installed.
verified:       Commit objects containing an explicit `parent <hash>`
                line, confirmed by running `git cat-file -p HEAD` in
                this repository (2026-07-25); tamper-evidence through
                hash linking — Haber & Stornetta (1991); public
                append-only logs with independent auditing as the
                security argument — RFC 6962 §1-2 and RFC 9162.
                Checked 2026-07-25.

## DESCRIPTION

The ledger only grows. Entries are added and never edited, and each one
is bound to the one before it by a hash.

That binding is what does the work. Because every entry names the hash
of its predecessor, changing an old entry changes its hash, which
falsifies the next entry's reference, which changes *that* entry's hash,
and so on to the end. There is no way to alter one thing quietly; you
would have to rewrite everything after it, in front of everybody holding
a copy.

Notice what is *not* being claimed. Nobody is prevented from editing the
file — it is a file. The guarantee is that the edit is detectable by
anybody who kept an older copy or who checks the chain. Tamper-evident,
not tamper-proof, and the difference is the entire design.

This is also why the ledger being *public* is a feature rather than a
privacy failure. A log nobody can read is a log nobody can audit, and an
unauditable append-only log is just a database with extra steps.

## REAL-WORLD COUNTERPART

real, and older than most people assume: hash-linked timestamping was
published by Haber and Stornetta in 1991, nearly two decades before it
was used for cryptocurrency.

The example you already use is git. A commit names its parent, and the
commit id is a hash over content that includes that parent — which is
exactly why rewriting history changes every id downstream, and why a
force-push is visible rather than silent.

The example that matters most in production is Certificate Transparency
(RFC 6962, now RFC 9162): every publicly-trusted TLS certificate is
logged to public append-only logs, so a certificate authority that
issues a certificate for a domain it should not can be *caught* — it
cannot be *stopped*, which is precisely the tamper-evident bargain.
Sigstore's Rekor does the same for software signatures.

None of these needs a blockchain. What they need is a hash chain and
somebody willing to look.
```

---

### 3.23 `provenance-record(5)`

```
id:             provenance-record
section:        5
name:           provenance record
canonical:      Provenance record
gloss:          One signed step in a thing's history, naming what came before.
status:         real, simplified
aliases:        provenance entry, signed record
glossary:       ../design/glossary.md
seeAlso:        provenance-chain(7), append-only-log(7), did(7),
                canonicalization(7), digital-signature(7), verify(1),
                quorum(7), equivocation(7)
reading:        RFC 7515 "JSON Web Signature", Appendix F (detached
                content); RFC 8785; RFC 8032 (EdDSA);
                ../architecture/04-item-provenance.md §2-3
notes:          A **section 5** page — a record format, like crontab(5),
                not a concept page. It therefore has a SYNOPSIS, which
                section 7 pages must never have (00 §5.2).
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  append-only-log(7), digital-signature(7)
hook:           `item-history <item>` — the first time the player reads
                the actual steps an item took to reach them, rather than
                a summary of them.
misconception:  commonly believed a record like this is a receipt — a
                note *about* the item kept somewhere alongside it;
                actually the record *is* the item's definition. The
                stats live in `itemAttrs` inside the signed payload, so
                there is no separate authoritative copy to disagree with
                it, and changing a stat means forging a signature rather
                than editing a row.
transfer:       Run `git log --show-signature` on any repository with
                signed commits, or `gpg --verify` on a detached
                signature file. A detached signature is the same shape
                as this record's: the signature travels separately from
                the bytes it covers, and verifying means re-deriving
                those bytes and checking. Assumes a Unix shell —
                see ED-8.
simplified:     The shipped page shows the payload fields but not the
                envelope's full multi-signature variant, which carries
                one signature block per validator for duel outcomes
                (../architecture/04 §3.1). A player reading one record
                does not need the aggregation rule; a player reading a
                *contested* record does, and `quorum(7)` has it.
verified:       Payload field set, `prevRecordHash` linkage, and the
                nonce/timestamp replay defence —
                ../architecture/04-item-provenance.md §2; detached-JWS
                envelope and the multi-signature duel variant — §3,
                §3.1; JCS canonicalization — §4; Ed25519 signatures —
                §5, RFC 8032; detached content as a real JWS mode —
                RFC 7515 Appendix F. Checked 2026-07-25.

## SYNOPSIS

       item-history <item>          # read the records
       verify <item>                # check them

## DESCRIPTION

One event in an item's life, written down and signed. Minted, granted,
traded, won in a duel — each is one record.

Every record carries the same fields: which item, what kind of event,
who holds it afterwards, who issued the record, the hash of the previous
record, a timestamp and a random nonce. The item's actual stats travel
inside it too, which is the part worth pausing on — **the record is not
a note about the item, it is the item's definition.** There is no other
authoritative copy of an item's power rating to disagree with this one.

The timestamp and nonce are there for one specific attack: without them,
an old genuine record could be replayed as though it were a new event.

The signature is *detached*, meaning it travels beside the bytes rather
than wrapped around them. A verifier therefore has to rebuild the exact
signed bytes before it can check anything, which is why
`canonicalization(7)` is a prerequisite for this page and not a footnote
to it.

## REAL-WORLD COUNTERPART

real, simplified — every mechanism here is a real standard used the way
it is normally used. The envelope is a detached JSON Web Signature
(RFC 7515, Appendix F). The bytes are canonicalized with JCS (RFC 8785).
The signature is Ed25519 (RFC 8032). The parties are named by DID.

The everyday counterpart is a signed git commit or a `gpg --verify`
detached signature: the same three-part shape of *content*, *a
canonical form of that content*, and *a signature over it by a named
key*.

The simplification is the envelope's multi-signature variant, used when
a duel outcome needs a quorum rather than a single issuer. See
`quorum(7)` and `bft-threshold(7)`.
```

---

### 3.24 `provenance-chain(7)`

```
id:             provenance-chain
section:        7
name:           provenance chain
canonical:      Provenance chain
gloss:          Every step in a thing's history, linked so a gap shows.
status:         real, simplified
aliases:        item history, chain of custody, signed history
glossary:       ../design/glossary.md
seeAlso:        provenance-record(5), append-only-log(7),
                canonicalization(7), did(7), quorum(7), verify(1),
                item-history(1), provenance-tracer(1), trust-anchor(7)
reading:        RFC 7515 Appendix F; RFC 8785; RFC 8032;
                git-log(1) --show-signature; RFC 6962 (Certificate
                Transparency); ../architecture/04-item-provenance.md §6-7
notes:          ⚠ **The shipped page for this term is already written in
                full** at ../client/04-terminology-and-education.md §4.9.
                That page is the ship-side source of truth for the prose;
                this entry is the curriculum record behind it and adds
                the eight curriculum-only fields. If the two ever
                disagree, the curriculum changes first and the shipped
                page follows (00 §1.2). Do not fork the wording.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          adversarial
prerequisites:  provenance-record(5), canonicalization(7)
hook:           `verify <item>` returning a status the client worked out
                for itself — the one number on screen that is not the
                server's word (../client/00-client-overview.md §1.1).
misconception:  commonly believed that a chain which verifies proves the
                events actually happened; actually it proves only that a
                set of keys attested to them. If every key in a chain
                belongs to one dishonest server, the chain verifies
                perfectly and the item is still fabricated — which is
                exactly why cross-server outcomes need a quorum of
                independent signers rather than one authority.
transfer:       Run `git log --show-signature` on a repository with
                signed commits: you are walking a chain, checking each
                link's signature against a named key, and the tool will
                tell you about a broken link rather than about a wrong
                one. Then consider what it does *not* tell you — whether
                the person holding that key should have been trusted.
                Assumes a Unix shell — see ED-8.
simplified:     The shipped page describes walking the chain and the
                not-recognised outcome, but not the full verification
                algorithm for a multi-signature duel record — which also
                checks that each signer was actually sampled for that
                duel and that the summed reputation-weight clears the
                BFT threshold (../architecture/04 §7, ../architecture/05).
                Those are `quorum(7)`'s and `bft-threshold(7)`'s.
verified:       Per-item chain model and player-facing history as a
                requirement — ../architecture/04-item-provenance.md §6,
                §6.1; the three-step verification algorithm and the
                not-recognised outcome — §7; client-side verification as
                the single client-computed truth —
                ../client/00-client-overview.md §1.1 and
                ../client/04-terminology-and-education.md §3.5;
                git commit chaining confirmed by `git cat-file -p HEAD`
                in this repository. Checked 2026-07-25.

## DESCRIPTION

Every item carries its whole life as a chain of records: minted here,
granted there, traded, won. Each record names the hash of the record
before it, and each is signed by whoever issued it.

Your client walks that chain itself, against the issuers' public keys,
rather than taking the server's word that it checked. That is the one
thing this client works out for itself — every other number on your
screen is the server's.

A chain that fails any step means the item is **not recognised**. Not
"suspicious", not "flagged for review": not recognised. Across the
federation, that is how a cheating server's fabricated items become
worthless — not by anyone banning it, but by everyone else declining to
recognise what it produced.

Verification also works offline, because everything needed is in the
chain and the keys.

## REAL-WORLD COUNTERPART

real, simplified — hash chains and signed logs, using the same standards
in the same way. Each record names the hash of the one before it, so
altering an old record changes every hash after it. Git does exactly
this, which is why rewriting history changes every id downstream.

The mechanisms are ordinary: detached JWS over JCS-canonicalized JSON,
signed Ed25519, keys named by DID. Certificate Transparency and
Sigstore's Rekor are public logs built on the same argument — that
anybody can check them without trusting the operator.

## CAVEATS

**A chain proves what a set of keys attested. It does not prove the
events happened.** If every key in a chain belongs to one dishonest
server, the chain verifies perfectly and the item is still fabricated.

That is why cross-server outcomes here need a quorum of independent
signers rather than one authority — and it is the same reason real
transparency logs need independent witnesses. Verification tells you a
story is consistent, not that it is true.

The simplification: this page describes the single-issuer walk. A
contested duel record is checked differently — see `quorum(7)`.
```

---


### 3.25 `proof-of-work(7)`

```
id:             proof-of-work
section:        7
name:           proof of work
canonical:      proof of work
gloss:          Buying the right to add to a shared history by burning computation.
status:         real
aliases:        PoW, mining, hashing, block
seeAlso:        mining-pool(7), difficulty-retarget(7), fork(7), hash(7),
                hash-chain(7), quorum(7), self-mining(7), mine(1)
reading:        Satoshi Nakamoto, "Bitcoin: A Peer-to-Peer Electronic Cash
                System" (2008), §4 "Proof-of-Work"; Adam Back, "Hashcash — A
                Denial of Service Counter-Measure" (2002)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  hash(7), fork(7)
hook:           `mine` with no argument prints the chain's height, its
                difficulty, this rig's hashrate as a percentage of the whole
                chain, and the expected time to a block. Those four numbers are
                related by one equation, and the readout is the equation
                (`../design/04-mining.md` §1.3).
misconception:  commonly believed that a miner who has gone a long time without
                a block is "due" one, and that mining accumulates progress
                toward the next block the way filling a bucket does; actually
                every hash is an independent trial against the same target, so
                the process is memoryless — the expected wait from right now is
                the same whether you started a second ago or have been going
                for a day, and stopping loses nothing because there was nothing
                banked. The belief is the gambler's fallacy meeting an
                interface that usually does show progress bars, which is why
                the game deliberately refuses to draw one.
transfer:       Open any Bitcoin block explorer and read off the current
                difficulty. Multiply it by 2^32 — that is roughly how many
                hashes the whole world expects to compute per block. Divide by
                the network hashrate the same page reports, and the answer is
                about 600 seconds. Then look at the timestamps of the last
                twenty blocks: they average ten minutes and individually range
                from under one to over forty, which is what "memoryless" looks
                like from outside. Platform: any browser; see ED-8.
verified:       Difficulty-1 target 0x00000000FFFF0000...0000 and the
                resulting expected-hashes relation difficulty x 2^48 / 0xffff,
                i.e. difficulty x 2^32; expected time to a block =
                difficulty x 2^32 / hashrate; the 10-minute target interval;
                the 2016-block retarget window; the factor-of-4 clamp on one
                adjustment — Bitcoin wiki, "Difficulty". Block arrivals as a
                Poisson process with exponentially distributed, memoryless
                inter-block times — corroborated across the mining-statistics
                literature (e.g. Bowden et al., "Block arrivals in the Bitcoin
                blockchain", arXiv:1801.07447). Median of an exponential is
                ln 2 (about 69%) of its mean, and the relative standard
                deviation of a Poisson count is 1/sqrt(n) — standard results,
                derivable rather than sourced. Checked 2026-07-27.

## DESCRIPTION

Ethecoin has no bank deciding whose balance is whose. What it has instead is a
shared history that anyone can extend, and a rule that makes extending it
expensive: to add a block you must find a number that, hashed together with the
block, comes out below a target. There is no clever way to find one. You try,
and try, and try.

That is what your cycles are doing when you `mine`. Each attempt is a hash. The
target is set so that the whole network, all of it together, expects to succeed
about once every ten minutes.

Three numbers on the `mine` readout are the whole system. **Difficulty** says
how low the target is: expect to compute difficulty x 2^32 hashes per block.
**Hashrate** says how many you compute per second. Divide the first by the
second and you get the third — the expected time between your blocks. A rig
with 4% of the chain's hashrate expects 4% of the blocks.

Here is the part that surprises people. Because every hash is an independent
try against an unchanged target, **nothing accumulates**. A rig four hours into
a dry spell is exactly as close to the next block as one that started a second
ago. Nothing is banked, nothing is forfeited by stopping, and a long gap does
not make you due. This is why the game shows you an expected time and never a
progress bar: the bar would be a lie, and a specific one — it would tell you to
keep mining to protect progress that does not exist.

The same property means the average badly misdescribes the typical. Waits like
this have a median around 69% of their mean, so more than half come in early
and a long tail runs to several times the average. Solo mining feels unluckier
than it is. See mining-pool(7).

Difficulty is not fixed forever. Every 2016 blocks the network compares how
long that batch actually took against how long it should have taken and
re-tunes the target to bring the pace back to ten minutes. See
difficulty-retarget(7).

## REAL-WORLD COUNTERPART

real, simplified — the equations here are Bitcoin's, unchanged. What is
simplified is the size of everything and the stability of the network.

Bitcoin uses exactly the relation above, including the 2^32 and the ten
minutes and the 2016-block retarget window. The idea predates Bitcoin: Adam
Back's Hashcash proposed the same trick in 2002 as anti-spam postage — make
the sender burn a little computation so that sending one message is cheap and
sending a million is not.

Two honest differences. First, scale: a real solo miner with consumer hardware
is a vanishingly small fraction of the network and would wait a geological
length of time for a block, which is not a game. This chain is small enough
that a full rig is a few percent of it. Second, this chain's other miners never
arrive or leave, so its difficulty has no long-term trend; a real chain's
climbs, because the hashrate behind it does. It still jitters a percent or two
per retarget, because 2016 random block times do not fill a window exactly —
and that part is real.

The energy argument is real and is not settled here. Proof of work buys its
security by making history expensive to rewrite, and the expense is electricity
— which is the point and the objection at the same time.
```

---

### 3.26 `mining-pool(7)`

```
id:             mining-pool
section:        7
name:           mining pool
canonical:      mining pool
gloss:          Sharing the luck of mining, so income is steady instead of lumpy.
status:         real
aliases:        pool, pooled mining, PPS, pay-per-share, share
seeAlso:        proof-of-work(7), difficulty-retarget(7), self-mining(7),
                mine(1), ethecoin(7)
reading:        Meni Rosenfeld, "Analysis of Bitcoin Pooled Mining Reward
                Systems" (arXiv:1112.4980); published payout-scheme
                documentation from operating pools (f2pool, Braiins)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         07
stage:          investigating
prerequisites:  proof-of-work(7)
hook:           `pools` lists five operations with fees from 0.50% to 3.50%
                and payout intervals from fifteen seconds to three hours, and
                the cheapest one on the list is the one that pays least often
                (`../design/04-mining.md` §1.3a).
misconception:  commonly believed that joining a pool earns you more, because
                the pool "finds more blocks", and that a bigger pool is
                therefore always steadier; actually a pool earns you very
                slightly LESS — it charges a fee — and what you buy is not
                income but predictability. Expected earnings are the same
                either way, because your share of the pool's blocks is your
                share of its hashrate, which is what you would have got alone.
                And size only smooths under PPLNS, where you are paid out of
                blocks the pool finds; under PPS the smoothing comes from the
                share target, so a one-rack PPS pool smooths exactly as well as
                the largest on the network. People arrive holding both beliefs
                because a pool genuinely does find far more blocks than they
                would, and it is easy to miss that the blocks are divided in
                the same proportion.
transfer:       Find any large pool's public statistics page and read two
                figures: its share of total network hashrate, and its fee.
                Multiply the first by 144 — the number of blocks a day — and
                you have how many blocks a day it should find; compare against
                what it reports actually finding that week. Then notice the fee
                is a percentage of your reward and not a percentage of the
                pool's luck. Platform: any browser; see ED-8.
verified:       Pay-per-share pays a fixed amount per accepted share regardless
                of whether the pool found a block, placing the variance risk on
                the operator, and PPS fees run roughly 2-4% against roughly
                0-2% for PPLNS, which pays only out of blocks actually found;
                variable difficulty ("vardiff") assigns each miner a share
                target scaled to that miner's hashrate to hold a roughly
                constant share rate, configured with a target time per share —
                published pool payout-scheme documentation (f2pool, minerstat)
                and Stratum pool implementations (miningcore). Relative
                standard deviation of a Poisson count is 1/sqrt(n) — standard
                result. Checked 2026-07-27.

## DESCRIPTION

Solo mining pays everything or nothing. On a full rig you expect a block about
every four hours, which means most hours pay you nothing at all and occasionally
one pays a great deal. The expected income is fine. The experience is not, and
if you need to eat this week the expectation is cold comfort.

A pool fixes the shape without changing the size. You point your cycles at the
pool instead of the chain, and the pool hands you an easier target than the real
one — easy enough that you hit it every thirty seconds or so. Each hit is a
**share**: a proof that you really did the work, worth nothing to anyone else,
but enough for the pool to know what you contributed.

The pool pays you a fixed amount per share, whether or not the pool found a
block that day. That is called pay-per-share, and it is the pool taking the
variance off your hands and onto its own books. It charges a fee for that.

Not every pool does this the same way, and the difference is the whole choice:

  - **PPS** pays per share, so your income is smooth however small the pool
    is. The operator is fronting your pay through their own unlucky weeks, and
    charges more for it — around 2-4%.
  - **PPLNS** pays only out of blocks the pool actually finds, in proportion
    to your shares. It charges less, around 0-2%, because it promises less.
    Here **the pool's size becomes your variance**: a pool with 5% of the
    network finds a block roughly every three hours, so that is how often you
    are paid.

So the cheapest pool available is very often the one that behaves most like
the solo mining you were trying to escape. That is not a trick; it is what the
fee was buying.

So the trade is exactly this, and it is worth being precise because it is
usually described backwards:

  - Pooled and solo have the **same** expected income, less the fee.
  - Pooled pays **slightly less** on average, because of the fee.
  - Pooled is **enormously** steadier. A hundred and twenty small payouts an
    hour instead of a quarter of a large one. The hour-to-hour swing is around
    twenty times smaller.

You are not buying income. You are buying predictability, and the fee is the
price.

The easier target is retuned to your rig, not fixed — a small rig gets an easier
one, a large rig a harder one, so both submit shares at about the same pace.
That is why pooling smooths a ten-cycle rig as well as a hundred-cycle one.

A share is not a block. Your shares never appear on the chain and never move its
height; only the pool's actual blocks do. See proof-of-work(7).

## REAL-WORLD COUNTERPART

real — the mechanism, the vocabulary and the trade are all as described.

Real pools use exactly this: a per-miner share target, retuned to the miner's
hashrate (the operators call it "vardiff", and configure it with a target time
per share, the same way this game does). Pay-per-share is one of several payout
schemes; the main alternative, PPLNS, pays only out of blocks the pool actually
found, so it passes some of the luck back to the miners and charges a lower fee
for doing less. Published PPS fees run around 2-4%; PPLNS around 0-2%. The gap
between them is roughly the price of the variance.

Real pools also settle on a schedule rather than per share: shares are credited
to an internal balance continuously and paid out periodically, often with a
minimum threshold as well. The game does the same on a one-minute window, for
the same reason and one of its own — a ledger with one row per share is a
ledger nobody can read.

One further real detail the game keeps: being pooled means holding a connection
open to somebody else's server and sending it something on a timer. That is
observable traffic. Solo mining is not — it is local computation, and nothing
leaves the machine until a block is found. It is the one respect in which
pooling is less private than mining alone.

The reason pools dominate real mining is the reason they exist here. As a
network grows, one participant's share of it shrinks, and the wait between solo
blocks grows with it — past days, past months, past any horizon a person can
plan against. Pooling does not make anyone richer. It makes the income
describable.
```

---

## 4. What this domain deliberately does not teach

`00` §7.3 is the governing rule: an entry with no hook is not written, however interesting, because the delivery mechanism is contextual and a concept with no surface has no trigger. What follows is not a list of things judged unimportant. Every item is genuinely worth knowing and none of it has a place in this game to be met.

| Not taught | Why not |
|---|---|
| **Named consensus protocols in detail** — Paxos, Raft, PBFT's three-phase message flow, view changes | The player never runs one and never sees one fail. `consensus(7)` and `bft-threshold(7)` teach the *requirement* and the *threshold*, which are what the game's surfaces show. The protocols are `reading:` citations. |
| **Vector clocks, version vectors, CRDTs, operational transformation** | `logical-clock(7)` names concurrency and stops. The game has no merge: a conflicting chain is a fork and is refused, never reconciled (`../architecture/08` §1), so there is no surface where merge strategy is visible. CRDTs get one sentence in `last-writer-wins(7)` because they are the honest alternative to the thing that was refused. |
| **Sharding, partitioning strategies, consistent hashing** | A home server holds its own players' state in one Postgres. Nothing in the game distributes one dataset across machines, so consistent hashing would be a page about a problem this game does not have. Note the vocabulary trap: "partition" in the game's sense is a *network* partition, not a data partition. |
| **Distributed transactions, two-phase commit, sagas** | No surface. Cross-server item transfer is a signed chain extension, not a transaction across two databases. |
| **Message queues, brokers, exactly-once delivery, back-pressure** | The client speaks HTTP to one server. `idempotence(7)` already carries the only part with everyday transfer value, which is that "exactly once" is achieved by making repeats harmless rather than by preventing them. |
| **Consensus economics** — proof-of-work difficulty, staking yields, MEV, tokenomics | The game has a currency and a public ledger, and both are `../client/04` §2.14 fiction with a `game` label. Teaching mining economics from a game whose mining is explicitly not proof-of-work (`../client/04` §2.10) would be teaching from a false model. |
| **Observability of distributed systems** — tracing, spans, correlation ids | Real and useful, and there is no surface. The `log` command reads one machine's log. |
| **Blockchain internals** — Merkle trees, block structure, forks-as-in-chain-splits, finality | `fork(7)` teaches the general shape at the level the game uses. Beyond that the game's ledger is a fiction and the honest move is a citation, not a page. |
| **Clock synchronisation algorithms** — Berkeley, Cristian's, PTP's mechanism | `clock-skew(7)` gives the numbers a player can act on. How NTP computes an offset from four timestamps is a page nobody will open. |

One deliberate near-miss, recorded so it is not mistaken for an oversight: **the physical latency floor** — the speed of light in fibre, the transatlantic numbers — is used as a fact in `distributed-system(7)`'s description but is *owned* by `latency(7)` in domain 03. If domain 03 ships `latency(7)` without those figures, this document's §3.1 is the only place the game states them, and that should be corrected in 03 rather than duplicated here.

---

## 5. Open questions

Prefix **`DS-`**, which is unused across `../design/15-open-questions.md`'s existing id space. Log these in `../design/15-open-questions.md` §2 if this document is adopted.

- **DS-1: ✅ FULLY RESOLVED (2026-07-25) — the six identity concepts are this document's, and are now written.** Ownership was settled with **ED-3**; the entries followed in the same pass and are §3.19–§3.24. The option this document originally recommended — that domain 06 absorb them — was **not** taken, and the reason is worth keeping, because the argument for it was good. `06-cryptography-and-trust.md` §1.4 had independently ceded all six *here*, on two grounds this document could not see: their game surface is the `identity` window and the item-history view, which are this domain's, and under **R8** a `06` entry may not name a `07` entry in `prerequisites` — so putting them in `06` while `07`'s entries depend on them would have inverted the ladder. Two documents reaching for the same six concepts from opposite directions is what made the gap visible at all. Three had prose to adapt rather than invent: `../client/04` §3.10 lists `did` and `provenance-record`, and §4.9 contains a fully worked `provenance-chain(7)` — which `provenance-chain(7)`'s `notes:` now names as the ship-side source of truth, so the two cannot fork. ⚠ One real defect surfaced while writing them and is fixed: this document had been citing `public-key(7)` and `signature(7)`, which `06` spells `public-key-cryptography(7)` and `digital-signature(7)`, so **sixteen prerequisite and seeAlso edges pointed at entries that did not exist.**
- **DS-2: three homonyms this domain introduces are not in `../client/04` §2.15's table, and each is a collision with a term another domain will certainly teach.** (1) **fork** — a chain fork here, `fork(2)` the system call that creates a process in domain 02, and a project fork in ordinary use. (2) **partition** — a *network* partition here, the game's **Isolated Partition** rig upgrade, and a disk partition; §2.15 currently records only the second and third, making this a three-way collision. (3) **sequence number** — an issuer's version counter here, and the 32-bit field in every TCP segment in domain 03. Proposed: three rows added to `../client/04` §2.15, and a mandatory `notes:` line on all six entries. §3.8's entry already carries its half.
- **DS-3: the BFT threshold is stated in two incompatible units and the curriculum has to teach one of them.** `../architecture/05` §1 gives both "`2f+1` of `3f+1` **weighted** validator power" and "**5 of 7** must agree", and these coincide only when reputation weights are equal, which that document's §2.2 (`weight = reputation × uptime`) guarantees they are not. `../architecture/04` already lists the same ambiguity as open (P-1…P-7: "whether the quorum threshold binds on validator count as well as weight"). §3.15 teaches the count form because that is the form the intersection proof covers; if the implementation binds on weight, the entry's arithmetic paragraph must be rewritten and `bft-threshold(7)` may have to become `real, simplified`. **Blocks the technical review of §3.15.**
- **DS-4: ✅ WITHDRAWN — the `adversarial` stage is not over-subscribed, and this document's alarm was a unit error.** The original finding counted this document's **inventory** rows (25) against `00` §6.2's budget, which counts **written** entries. `06-cryptography-and-trust.md` **CT-3** made the identical mistake independently, and two documents agreeing made it look confirmed. Counted correctly: this document writes 15 at `adversarial` and `06` writes 9, the whole curriculum sits at **26** against a 25–40 budget, and the stage is comfortably inside it. `00` §6.2 now states the counting basis explicitly and publishes the measured distribution so this cannot recur. ⚠ **The real overage is elsewhere and nobody flagged it:** `operating` runs 49 written against ~25–40 — logged as **ED-11**. It was invisible to every individual document, because each one's own §2.5 check passed; only the total showed it.
- **DS-5: `ntp(7)` has a weak hook and may fail `00` §7.3.** No surface in the game shows NTP. It is inventoried because `clock-skew(7)` cannot state its numbers without naming the protocol that produces them, and because "my clock is synchronised, therefore my timestamps are comparable" is a live misconception worth killing. But by the letter of the rule, a concept whose only hook is another entry's citation should be a paragraph inside that entry rather than a page of its own. Recommend folding it into `clock-skew(7)` unless a surface appears.
- **DS-6: `federation-directory(7)` is `real` in `../client/04` §2.13 and `real, simplified` here.** Applying `00` §4.2's ordered procedure, step 4 fires: a practitioner would wince at "the federation directory" as a definite article, because most real federations — email, Matrix, XMPP — have no directory at all and you find a server by being told about it. The simplification is nameable ("real federations mostly have no index; the game has one, opt-in, trusted for nothing"), and `00` §4.2's downgrade bias says the tie does not go to the flattering label. This is a **finding filed against `../client/04` §2.13**, not a unilateral change: the vocabulary map and the curriculum must not ship different status values for one term.
- **DS-7: `validator reputation` cannot satisfy the byte-for-byte coverage check.** `../client/04` §4.10's coverage check joins a term file's `canonical:` against `../design/glossary.md` byte for byte. The glossary's entry is a single bullet headed **Reputation** carrying two unrelated meanings, `factionReputation` and `validatorReputation`, deliberately kept apart. No `canonical:` value can match that bullet, and inventing one would defeat the check's purpose. Proposed: split the glossary bullet into two entries, **faction reputation** and **validator reputation**, each retaining the ⚠ cross-reference to the other. Cheap, and it makes the distinction the glossary already insists on machine-checkable. Related to **ED-1**.
- **DS-8: does the `last-writer-wins(7)` page name the refusal as a refusal?** §3.10's description says somebody asked for a feature and it was declined, which is unusually candid for player-facing content and is also, on this document's judgement, the single most instructive thing in the domain: a player can see a real design argument with the losing option named. The alternative register states the rule without the history ("ownership converges on validity, never recency"), which is shorter and loses the lesson. This is a tone decision for whoever owns the shipped voice, not a technical one, and it interacts with **ED-5** — a page that opens by describing an argument is close to a page that opens by describing a mistake.
- **DS-9: several transfer tests name a command whose availability depends on the time daemon installed.** §3.7's transfer offers `sntp`, `chronyc` or `timedatectl show-timesync` depending on the platform and configuration, which is three commands where the template wants one checkable action. This is **ED-8** in its most awkward form — not merely Windows versus Unix, but variation within Linux. If ED-8 resolves to option (c) — transfer tests target what is universal — this entry needs a different test, and the honest candidate is reading the clock offset out of the machine's own settings interface rather than a shell.
