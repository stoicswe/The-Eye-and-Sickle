# Education Documentation — The Eye and Sickle

This folder is the **curriculum**: the body of real computing knowledge the game teaches, which
concepts it covers, in what order, against which false beliefs, verified against which sources.

`../design/` covers the game's systems and economy. `../architecture/` covers the stack, servers and
federation. `../client/` covers what the player sees — including
`../client/04-terminology-and-education.md`, which specifies *how* a definition reaches the player.
This folder answers a different question: **what, exactly, are we teaching, and is it true?**

Read `../../CLAUDE.md` first for the invariants and conventions.

## The boundary — three layers, three owners

Confusing these is the failure `00-curriculum-and-method.md` §1.1 exists to prevent, and it is worth
carrying before reading anything else:

| Layer | Artefact | Owner | Question it answers |
|---|---|---|---|
| **Mechanism** | Tiers, triggers, the gloss bar, the `man` window, teaching levels, the file format, CI checks | `../client/04-terminology-and-education.md` | *How does a definition reach the player?* |
| **Curriculum** | Which concepts, in what order, corrected against which misconceptions, verified how | **this folder** | *What are we teaching, and is it true?* |
| **Output** | `client/src/main/resources/terms/<locale>/<section>/<id>.md` | the `client/` module | *What ships?* |

The dependency runs one way: `../design/glossary.md` → `docs/education/**` → `../client/04` →
`client/src/main/resources/terms/**`. **Nothing in this folder is code.** No file here is read at
build time or run time; it is the design record that makes the shipped content reviewable by a human
who knows the subject, which is the only quality gate this feature actually has.

## Reading order

New to the curriculum: read **`00`**, then whichever domain you are working on. `00` is a
**contract** — it fixes the entry template, the status vocabulary, the four progression stages and
the coverage rules, and the eight domain documents are written against it. Changing a field name in
`00` §3 invalidates eight documents downstream.

The domains are ordered so that **no concept forward-references a higher-numbered one**. Read them in
order and nothing is defined in terms of something you have not met.

## Document map

| # | Doc | Status | What it covers |
|---|---|---|---|
| 00 | [`00-curriculum-and-method.md`](00-curriculum-and-method.md) | ⚠️ **[PROPOSAL]** | **The contract.** The entry template and its three veto gates, the status decision procedure, man-section assignment, the four stages and eight sequencing rules, the coverage tests, the writer's loop and the review requirement |
| 01 | [`01-foundations.md`](01-foundations.md) | ⚠️ **[PROPOSAL]** | What a computer is and how it represents things: bits, bytes, hexadecimal, base64, text encodings, integer width and overflow, floating point, unit prefixes, memory vs. storage, orders of magnitude, abstraction, data-as-code |
| 02 | [`02-computer-architecture.md`](02-computer-architecture.md) | ⚠️ **[PROPOSAL]** | How the machine executes: processors, cores and hardware threads, clocks and instruction cycles, caches and the memory hierarchy, interrupts, RAM, throttling and thermal budget |
| 03 | [`03-operating-systems.md`](03-operating-systems.md) | ⚠️ **[PROPOSAL]** | **uOS and what an OS actually does**: kernel, processes, signals, scheduling, system calls, virtual memory, filesystems, inodes, permissions, daemons, namespaces, containers, rootkits and cross-view detection |
| 04 | [`04-the-command-line.md`](04-the-command-line.md) | ⚠️ **[PROPOSAL]** | The shell as a program, arguments and flags, exit statuses, standard streams, pipelines, globs vs. regular expressions, quoting, tab completion, history, and `man` — how to find out |
| 05 | [`05-networking.md`](05-networking.md) | ⚠️ **[PROPOSAL]** | Packets, layering, ports, addresses, subnets, routing, TTL, TCP vs. UDP, DNS, NAT, TLS, traffic metadata, packet capture and onion routing |
| 06 | [`06-cryptography-and-trust.md`](06-cryptography-and-trust.md) | ⚠️ **[PROPOSAL]** | Threat models, hashing, salting, password hashing, symmetric and public-key encryption, signatures, trust anchors, certificate authorities, offline verification, replay — and why you do not roll your own |
| 07 | [`07-distributed-systems-and-identity.md`](07-distributed-systems-and-identity.md) | ⚠️ **[PROPOSAL]** | Partial failure, clocks and ordering, CAP, consensus, Byzantine faults, quorums, equivocation, Sybil attacks, federation — and identity as keys: DIDs, PDSes, canonicalization, append-only logs, provenance records and chains |
| 08 | [`08-detection-and-defence.md`](08-detection-and-defence.md) | ⚠️ **[PROPOSAL]** | Noticing and proving: intrusion detection and the statistics that make it hard — false positives, base rates, alert fatigue; audit trails and log integrity; integrity monitoring; anti-forensics and what survives it; honeytokens, honeypots, tarpits; attribution — **and the legality of hacking back** |

## Coverage

Every domain **inventories** its whole concept set — that is the coverage guarantee, and it is what
makes it possible to say what is *missing*. A subset is then **written out in full** to the §3
template, chosen by `00` §8.1's priorities. The rest carry an id, gloss, status, stage, prerequisites
and hook, which is enough to write them later without re-deciding anything.

| Domain | 01 | 02 | 03 | 04 | 05 | 06 | 07 | 08 | **Total** |
|---|---|---|---|---|---|---|---|---|---|
| Inventoried | 39 | 37 | 35 | 38 | 38 | 36 | 46 | 42 | **311** |
| Written in full | 16 | 18 | 18 | 18 | 18 | 17 | 24 | 20 | **149** |

**One entry per concept, one owner per entry** (`00` §1.4) — a player who gets two answers stops
trusting both. This is machine-checkable and currently holds: no id appears as a full entry in two
documents.

## Status tags — what they mean here

Every document is **[PROPOSAL]**, but the *obligation* is Established and load-bearing, and is cited
inline wherever it constrains a decision:

- Client pillar **C6** — "the interface teaches the real thing" (`../client/00-client-overview.md` §2).
- The product goal that a player should finish a session "genuinely understanding a little more about
  how operating systems, networking and computation work" (`../client/00-client-overview.md` §5).
- The falsifiable claim in `../client/04-terminology-and-education.md` §1.1, and §1.1a's rule that
  **uOS may add what Unix lacks but may never contradict it** — which is what makes the whole
  curriculum's transfer claim keepable rather than aspirational.
- `../design/04-mining.md` §3.1's requirement that process, connection and storage views carry **real,
  consistent data**, so a careful player can find a hidden miner from a discrepancy. That is an
  operating-systems *and* a command-line requirement, and it is why `03` and `04` exist.
- The vocabulary itself comes from `../design/glossary.md` and `../client/04` §2 and must not drift.

What is proposal is the *selection*: which concepts, in what order, at which stage, written against
which misconception.

## The entry template, in one paragraph

Every concept is written in one shape (`00` §3) — a **superset of the shipped file format**, so
producing a term file is a matter of deleting fields rather than re-deciding anything. Twelve fields
ship (`id`, `section`, `name`, `canonical`, `gloss`, `status`, `aliases`, `glossary`, `seeAlso`,
`reading`, `notes`, `revision`); eight are curriculum-only (`domain`, `stage`, `prerequisites`,
`hook`, `misconception`, `transfer`, `simplified`, `verified`). Three are **veto gates**: an entry
with no `hook` is not in the curriculum, an entry with no `transfer` is decoration and gets deleted,
and a `real, simplified` entry with no `simplified` means the writer has not understood their own
simplification. `status` is the honesty field — `real`, `real, simplified`, or `game` — and `00` §4.2's
decision procedure biases toward the *less* flattering label, because a wrong mapping teaches
something false, which is worse than teaching nothing.

## Open questions

Each document ends with its own numbered questions, prefixed by document:

| Prefix | Doc | Prefix | Doc |
|---|---|---|---|
| `ED-` | 00 Curriculum & method | `NW-` | 05 Networking |
| `FN-` | 01 Foundations | `CT-` | 06 Cryptography & trust |
| `CA-` | 02 Computer architecture | `DS-` | 07 Distributed systems & identity |
| `OS-` | 03 Operating systems | `DF-` | 08 Detection & defence |
| `SH-` | 04 The command line | | |

The ones that block rather than merely refine are summarised in `../design/15-open-questions.md`.
Five are worth knowing before reading anything else:

- **ED-6 — there is no technical reviewer.** `00` §8.4 makes a practitioner pass a *gate*, not a
  courtesy, and every domain's last open question is its own instance of this. Every factual claim in
  these documents is sourced and dated, and a writer verifying their own claims has checked that the
  claim matches a source — not that the source was the right one. Without a named reviewer this doc
  set produces confident prose with no verification gate, which is the exact failure it exists to
  prevent.
- **ED-8 — transfer tests assume a Unix shell, and most players are on Windows.** The highest-impact
  question in the set: a third of all entries are worded around it. `05` §NW-6 argues option (c)
  (target what is universal) works for its domain; `03` §OS-9 and `04` §SH-7 argue it cannot for
  theirs. The interim rule is that every transfer test names its platform.
- **ED-9 / CT-5 — where is the dual-use line?** `../client/04` §4.4 bans citing offensive-tooling
  walkthroughs, which handles citations but not our own prose. `06` §5 proposes the rule for the whole
  doc set: *entries explain what an attack class is and what defeats it, never how to carry one out;
  the test is whether a sentence helps a defender more than an attacker.*
- **DF-9 — `hack-back(7)` states a legal position and has had no legal review.** It is the one page
  in the game that tells a player not to do something (`../client/04` §2.8 makes it mandatory), and it
  is the one place in the whole doc set where being confidently wrong could harm a reader rather than
  merely misinform them. It cites the statutes it relies on. It still needs a reader who knows the
  law, or an explicit note on the page saying it has not had one.
- **ED-11 — the `operating` stage is over budget by about a quarter** (51 written against ~25–40).
  Invisible to every individual document — each one's own §2.5 check passed — and visible only once
  all eight were totalled. The likely cause is that `operating` is the comfortable default when a
  writer is unsure of a stage.

**Resolved on 2026-07-25**, and recorded in `00` §1.4 and `../design/15-open-questions.md` §3:

- **ED-3 (the domain split)** — six domains became eight. Representation became `01-foundations.md` in
  its own right; the command line kept `04`, because `03`, `06` and `07` all name `shell(7)` or
  `exit-status(7)` in `prerequisites` and no later numbering satisfies rule **R8**.
- **CT-1** — detection, logging, anti-forensics and the legality material became
  `08-detection-and-defence.md`. It sits above everything because nothing below it names a detection
  concept as a prerequisite, which was checked before it was written.
- **DS-1** — the six identity concepts (`did`, `pds`, `canonicalization`, `append-only-log`,
  `provenance-record(5)`, `provenance-chain`) were assigned to `07` and are now written. Writing them
  exposed sixteen `seeAlso` and `prerequisites` edges pointing at entries under names `06` does not
  use.
- **CT-3 / DS-4** — two documents independently reported the `adversarial` stage as over-subscribed.
  Both were counting **inventory rows** against a **written-entry** budget. Counted correctly the
  stage sits at 36 of 25–40. `00` §6.2 now states the counting basis and publishes the measured
  distribution.

## A note on verification

These documents make several hundred factual claims about hardware, operating systems, networking,
cryptography and distributed systems. Each entry's `verified:` field names a source *per claim* and
the date it was checked, and the standard applied was primary sources — POSIX.1-2024, the relevant
RFCs, `man` pages, IEEE 754, published incident reports — rather than secondary summaries.

Where a claim could be checked by running it, **it was run**, and the command is quoted in `verified:`
so a reviewer can re-run it rather than take it on trust. Where something could not be verified it is
marked ⚠ inline and listed in that document's unverified-claims section rather than stated flatly.

Two habits are worth knowing because they are defences against a specific failure:

- **No entry quotes a tunable balance value in its body.** `../client/04` §6 **T-11** is semantic
  drift — a page that silently becomes false when a number is retuned. `02`'s `thermal-budget(7)`
  states the *shape* of the recovery curve and no number, deliberately, because
  `../design/01-core-resources.md` §1.3 is explicitly marked for playtest tuning.
- **Self-disclosure is treated as content, not embarrassment.** `06`'s `roll-your-own(7)` states that
  this project's own transport layer is unreviewed, because it is true
  (`../architecture/07-transport-security.md` §6 T-1) and because the honesty *is* the lesson. If that
  question is ever resolved, the page becomes false — **CT-7** names the owner of that bump.
