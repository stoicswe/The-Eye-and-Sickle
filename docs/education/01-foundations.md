# 01 — Foundations: what a computer is and how it represents things

**Status:** ⚠️ **[PROPOSAL]** — the *obligation* is Established (client pillar **C6**, `../client/00-client-overview.md` §2 and §5; the falsifiable claim in `../client/04-terminology-and-education.md` §1.1). The *contract* this document is written against is `00-curriculum-and-method.md`, itself a proposal. What is first-pass design here is the concept set, the ordering, the status calls and the sixteen written entries. Every factual claim was verified against a live or primary source in this pass and recorded in each entry's `verified:` field; anything not verified is marked ⚠ inline and listed in §1.5.
**Depends on:** `00-curriculum-and-method.md` (**the contract** — the entry template §3, the status procedure §4, man-section assignment §5, stages and sequencing rules §6, coverage and honesty §7, the writer's loop §8; this document does not restate any of it); `../client/04-terminology-and-education.md` §1 (the principle), §2.3 / §2.9 / §2.15 (the mappings and the homonym table), §4.8 (the shipped file format), §4.9 (`compute(7)`, already drafted); `../design/glossary.md` (canonical terms and the uOS definition); `../client/00-client-overview.md` §3.3, pillar **C6**; `../../CLAUDE.md` (invariants and conventions)
**Depended on by:** `03-operating-systems.md`, `05-networking.md`, `04-the-command-line.md`, `06-cryptography-and-trust.md`, `07-distributed-systems-and-identity.md` — **all five**, because this is the domain they are permitted to assume (`00-curriculum-and-method.md` §6.3 **R8**); and, through them, `client/src/main/resources/terms/**`

---

## 1. What this domain is

### 1.1 The thesis, in one sentence

> **A computer is a machine that holds state and changes it by following instructions — and everything it holds is a number, so the only question that ever matters about a piece of data is what rule is being used to read it.**

Two halves, and every concept in this document hangs off one of them. The first half is mechanism: state, instructions, the processor that steps, the memory it steps through. The second half is representation: bits, bytes, widths, encodings — the fact that a run of bytes is a hash, a price, a word, a picture or an instruction only because something decided to read it that way.

The second half is the one this domain leads with, and that is deliberate. An adult who uses computers professionally already has a working model of the first half; almost nobody has a working model of the second. It is the half that produces every "why did it do *that*" moment in a career — the drive that is smaller than the box said, the accented character that came out as `Ã©`, the invoice total that ends in `.30000000000000004`, the counter that went negative. None of those are mysteries. They are all one fact, applied four times.

### 1.2 Why a player of this game benefits

The game already committed to being honest about its data. `../design/04-mining.md` §3.1 makes it a hard implementation requirement that the process, connection and storage views be *real, consistent data*, because a careful player has to be able to find a rootkit-wrapped miner from a discrepancy that is always present. `../client/00-client-overview.md` pillar **C6** takes the next step: since the data is honest, label it honestly and explain it.

This domain is the part of that promise that the other five cannot do without.

- **02 (operating systems)** cannot explain virtual memory without `memory(7)` and `storage(7)`, and cannot explain why a small read is not cheap without `memory-hierarchy(7)`.
- **03 (networking)** cannot explain why a port is 0–65535, why IPv4 ran out, or why a round trip to another continent has a floor, without `bit-width(7)` and `latency(7)`.
- **04 (the command line)** cannot explain what a pipe carries without `character-encoding(7)`, because what a pipe carries is bytes and a hope about how to read them.
- **05 (security)** cannot explain injection at all without `data-and-code(7)`, and cannot explain "the hash is not encryption" without `bit-width(7)` and `hexadecimal(7)` first.
- **06 (distributed systems)** cannot explain canonicalization without the fact that a signature is over *bytes*, which is `byte(7)` and `utf-8(7)`.

That is the coverage argument. The player-facing argument is narrower and better: this is the domain where the game's own screen furniture stops being decoration. `sha256-9c2f…41ab` in the item panel becomes 64 hexadecimal characters standing for 256 bits. `1.8 Ti` in the storage window becomes a unit choice the player can now argue about. `72 / 100 cycles` becomes a word the game borrowed and bent, with the real one named next to it.

### 1.3 The surfaces this domain anchors to

Every entry's `hook` cites one of these, with a section anchor. A hook that does not appear here is not a hook (`00-curriculum-and-method.md` §7.1 item 4).

| Surface | Where it is specified | What this domain owes it |
|---|---|---|
| `rig-monitor` headline `72 / 100 cycles`, the segmented gauge, the Thermal row | `../client/05-tool-windows-and-layout.md` §2.3; `../design/01-core-resources.md` §1 | `compute(7)`, `cycle(7)`, `clock(7)`, `processor(7)`, `core(7)`, `thermal-budget(7)` |
| The compute gauge's **over-range segment** — an allocation past 100% drawn rather than clamped | `../client/06-resource-and-inventory-ui.md` §2.1 | `integer-overflow(7)` |
| Compute Cores, Thermal Budget, Bandwidth, Memory Buffer — the four rig stats | `../design/11-rig-infrastructure.md` §2 | `core(7)`, `thermal-budget(7)`, `bandwidth(7)`, `memory-buffer(7)`, `throughput(7)` |
| `storage` — three tiers as mount points, `used / capacity`, `df -h` | `../client/06-resource-and-inventory-ui.md` §5; `../client/04` §3.10 | `byte(7)`, `unit-prefixes(7)`, `storage(7)` |
| `audit` — the storage table's *delta since last audit*, and scan durations of 30 s / 2 m / 6 m | `../client/05-tool-windows-and-layout.md` §2.7; `../design/04-mining.md` §3.2 | `storage(7)`, `memory-hierarchy(7)`, `latency(7)` |
| Item provenance timeline — `prev sha256-9c2f…41ab`, middle-truncated, copyable | `../client/06-resource-and-inventory-ui.md` §8.2 | `hexadecimal(7)`, `bit(7)`, `bit-width(7)` |
| The `[ raw ]` provenance view — canonicalized JSON and the detached-JWS envelope, `"sig"` in base64url | `../client/06-resource-and-inventory-ui.md` §8.5; `../architecture/04-item-provenance.md` §2 | `base64(7)`, `utf-8(7)`, `byte(7)` |
| `recon` — recovered logs, emails and database records; the whole narrative channel | `../client/05-tool-windows-and-layout.md` §2.6; `../design/14-world-and-narrative.md` §3 | `character-encoding(7)`, `utf-8(7)`, `ascii(7)`, `unicode(7)` |
| The rule that a recovered log line reading `rm -rf /rig` is a prop and clicking it does nothing | `../client/04-terminology-and-education.md` §3.1 rule 7 | `data-and-code(7)` |
| Pipelines filtering *rendered text* — `ps \| grep miner` can match the wrong column, on purpose | `../client/04-terminology-and-education.md` §3.7 | `abstraction(7)`, `interface(7)`, `layer(7)` |
| The ledger's `1,240.00 EC`, fixed two decimals over 100 minor units | `../client/06-resource-and-inventory-ui.md` §4; `../design/15-open-questions.md` **P-8** | `floating-point(7)` |
| Relay Chain hop latency; the `map`'s hop distance | `../design/08-stealth-and-noise.md`; `../client/05-tool-windows-and-layout.md` §2.5 | `latency(7)`, `throughput(7)` |

### 1.4 What this domain owns, and where the boundary runs

The ownership rule is `00-curriculum-and-method.md` §1.4: *the lowest-numbered domain that can fully define a concept without forward-referencing a higher-numbered one owns it, and every other domain cites it in `seeAlso`.* Domain 01 is the lowest, so this document has no upward escape and every one of its `prerequisites` is either a domain-01 entry or `none` (**R8**). Three boundary calls are worth stating, because a later writer will otherwise re-litigate them:

- **`compute(7)` is domain 01, not 02.** Its real-world counterpart is CPU scheduling and cgroup quotas, which is operating-systems material — but its *game surface* is the `rig-monitor`, which `00-curriculum-and-method.md` §1.4 assigns to 01, and it can be defined against cores and cycles without the kernel. The §1.4 escape hatch ("the domain whose game surface the player meets it on") decides it. This also makes the method doc's own worked entry consistent: `process(7)` is domain 02 with prerequisite `compute(7)`, which is a downward edge and legal under R8. **Its page is already drafted** in `../client/04-terminology-and-education.md` §4.9 and is not rewritten here (§3.1).
- **`program(7)` is 01; `process(7)` is 02.** The inert file is representation and storage; the running instance needs the kernel. They are one lesson written from two ends, which is why `program(7)` is inventoried here but deliberately not written out yet (§3.1).
- **`throughput(7)` is 01; `bandwidth(7)` is 01 too, and `packet(7)` is 03.** Throughput is a general idea about rate. The game's **Bandwidth** rig stat is a concurrency cap wearing a networking word, and `../client/04` §2.15 requires the collision be stated rather than implied — it can be stated using only `throughput(7)`, so it stays here and 03 cites it.

### 1.5 The honesty ledger

Published per `00-curriculum-and-method.md` §7.1 item 5 and §7.4, and kept current.

| `status` | Count | Share |
|---|---|---|
| `real` | 35 | 90 % |
| `real, simplified` | 3 | 8 % |
| `game` | 1 | 2 % |
| **Total** | **39** | |

The three `real, simplified` entries are `compute(7)`, `thermal-budget(7)` and `memory-buffer(7)` — all three are rig stats, all three are game abstractions over real hardware behaviour, and all three carry a `CAVEATS` naming the abstraction. The single `game` entry is `bandwidth(7)`.

**This is the least-invented domain in the game, and the distribution should stay that way.** Nothing about bits, bytes, encodings, floating point or the memory hierarchy is fiction; the fiction starts at the rig stats and it is confined to four of them. A future revision that pushes the `game` count up in domain 01 is a signal that lore has leaked into a computing curriculum, which `00-curriculum-and-method.md` §4.4 already warns against.

**Unverified claims: two.** Both are marked ⚠ at the point of use and repeated here so they are actionable rather than admired.

- ⚠ **FN-3** — `endianness(7)` is inventoried with a *contingent* hook. No documented client surface renders raw bytes in a form where byte order is visible. If none is added, the entry is deleted under §7.3, not shipped with a weak hook.
- ⚠ **FN-4** — `../client/06-resource-and-inventory-ui.md` §2.1's over-range compute segment is the best available hook for `integer-overflow(7)`, but the game does not model wrapping and the entry says so. If the hook is judged too indirect, the entry drops to `reading:` on `bit-width(7)`.

---

## 2. The concept inventory

### 2.1 How to read it

Thirty-nine concepts, grouped by what they are about rather than by stage, so that the whole domain is visible at once — which is the point of the inventory (`00-curriculum-and-method.md` §1.3). Columns are the entry template's fields, compressed:

- **id** — the shipped filename stem. Every entry is section **7** (a concept, not a command and not a record shape — `00-curriculum-and-method.md` §5.2 step 4), so the section column is omitted rather than repeated thirty-nine times.
- **gloss** — the shipped one-liner, ≤ 72 characters, never containing the word it defines.
- **status / stage / prerequisites** — as defined by the contract. `—` in prerequisites means `none`.
- **Met at** — the game surface, abbreviated from §1.3. This column is the `hook` field's short form; the full hook with its section anchor lives in the entry.

**Bold ids are the sixteen written out in full in §3.** A **⇧** marks a concept that is inventoried here for context but **owned by a higher-numbered domain** and written there — this document cites it and must never define it. Both current cases went to `02-computer-architecture.md` when **ED-3** was resolved: `processor` and `memory-hierarchy` are architecture's subject matter, and each had briefly been written twice.

### 2.2 Group A — models of the machine

What a computer is, and the ideas used to talk about one. These are the entries that make the other five domains readable.

| id | name | gloss | status | stage | prerequisites | Met at |
|---|---|---|---|---|---|---|
| computer | computer | A machine that holds values and changes them by rule. | real | operating | — | The rig, as a whole |
| instruction | instruction | One step small enough that a chip can perform it directly. | real | operating | — | `terminal` command → server intent |
| program | program | A stored list of steps, doing nothing until something runs it. | real | operating | instruction(7), storage(7) | Tools in `storage`; bot frames |
| state | state | Every value a machine is holding at one instant. | real | investigating | — | `rig-monitor`; a saved session |
| determinism | determinism | Same starting values and same steps give the same result. | real | investigating | state(7) | Puzzle re-attempts; replay in `../architecture/07` §1 |
| **abstraction** | abstraction | A smaller promise standing in front of a larger mechanism. | real | operating | — | `ps \| grep miner` matching the wrong column |
| layer | layering | Parts stacked so each uses only the one directly below. | real | operating | abstraction(7) | Window ↔ command ↔ intent ↔ server |
| interface | interface | The agreed edge where two parts meet and stop asking questions. | real | investigating | abstraction(7) | The five universal flags (`../client/04` §3.4) |
| **data-and-code** | data and code | The same stored numbers can be read as content or as steps. | real | adversarial | program(7), byte(7) | Recovered log line reading `rm -rf /rig` |
| compute | compute, cycles | The rig's capacity budget; the master scarcity. | real, simplified | first-session | — | `rig-monitor` headline |

> `compute(7)`'s gloss and page are fixed by `../client/04-terminology-and-education.md` §4.9 and reproduced here only as an inventory row. **Do not re-word it in this document** — one source, one surface.

### 2.3 Group B — representation

How anything at all is written down. The spine of the domain.

| id | name | gloss | status | stage | prerequisites | Met at |
|---|---|---|---|---|---|---|
| **bit** | bit | The smallest unit of information: one of exactly two states. | real | operating | — | The `256` in SHA-256 |
| binary | binary | Counting with two symbols instead of ten, by place value. | real | operating | bit(7) | `bit(7)`; `hexadecimal(7)` |
| **byte** | byte | Eight bits, and the unit almost every size is counted in. | real | operating | bit(7) | `storage` capacity column |
| **bit-width** | bit width | How many binary digits a number gets, and so how large it may be. | real | investigating | bit(7), binary(7) | "a port is 16 bits"; `sha256-` |
| **hexadecimal** | hexadecimal | Base 16, written 0-9 then a-f, so one digit stands for four bits. | real | operating | byte(7), binary(7) | `prev sha256-9c2f…41ab` |
| **base64** | base64 | A way of re-spelling raw bytes using 64 printable characters. | real | investigating | byte(7) | `[ raw ]` view — the `sig` field |
| endianness ⚠ | endianness | Which end of a multi-byte number gets written down first. | real | investigating | byte(7), hexadecimal(7) | ⚠ contingent — see FN-3 |
| **unit-prefixes** | unit prefixes | Why a 2 TB drive shows as 1.8 TB: two ladders, 1000 and 1024. | real | operating | byte(7) | `df -h` in `storage` |

### 2.4 Group C — numbers

| id | name | gloss | status | stage | prerequisites | Met at |
|---|---|---|---|---|---|---|
| **integer-overflow** | integer overflow | What happens when a count outgrows the space reserved for it. | real | investigating | bit-width(7) | The gauge's over-range segment |
| **floating-point** | floating point | How a machine holds fractions, approximately, in a fixed space. | real | investigating | bit-width(7), binary(7) | `1,240.00 EC` in `ledger` |

> Signed versus unsigned is **inside** `bit-width(7)`, not a separate entry. Its gloss would need an "and", which `00-curriculum-and-method.md` §6.3 **R6** rules is the symptom of two entries — but the two halves cannot be taught apart, because the whole content of "signed" is that one of the bits is spent on the minus sign and the range halves. It is one fact about width.

### 2.5 Group D — text

| id | name | gloss | status | stage | prerequisites | Met at |
|---|---|---|---|---|---|---|
| **character-encoding** | character encoding | The rule that decides which letter a stored number stands for. | real | investigating | byte(7) | Recovered documents in `recon` |
| ascii | ASCII | The 1960s table of 128 characters everything else grew out of. | real | investigating | character-encoding(7) | Index sort order (`../client/04` §4.11) |
| unicode | Unicode | One catalogue giving every writing system's characters a number. | real | investigating | character-encoding(7) | Non-English recovered material |
| **utf-8** | UTF-8 | The rule almost all text uses to turn letters into bytes today. | real | investigating | character-encoding(7), byte(7) | `recon`; the shipped term files |

### 2.6 Group E — memory and storage

| id | name | gloss | status | stage | prerequisites | Met at |
|---|---|---|---|---|---|---|
| **memory** | memory | Fast working space, holding what runs now, emptied when power stops. | real | operating | byte(7) | Memory Buffer; `rig-monitor` |
| **storage** | storage | The slow, permanent place data sits when nothing is using it. | real | operating | byte(7) | `storage` window; `audit`'s deltas |
| memory-hierarchy ⇧ | memory hierarchy | Several stores of very different speeds, holding the same data. | real | investigating | memory(7), storage(7), latency(7), orders-of-magnitude(7) | Why a Thorough Scan takes six minutes |
| cache | cache | A small fast copy of something slow, kept in case it is wanted again. | real | investigating | memory-hierarchy(7) | Recon knowledge persisting on the `map` |
| memory-buffer | Memory Buffer | The rig stat capping how many tools you can have readied at once. | real, simplified | investigating | memory(7) | Loadout slots (`../client/06` §7.6) |

### 2.7 Group F — time, scale and cost

| id | name | gloss | status | stage | prerequisites | Met at |
|---|---|---|---|---|---|---|
| **latency** | latency | How long one operation takes from request to answer. | real | operating | — | Relay Chain hop cost; `map` hop distance |
| throughput | throughput | How much gets finished per unit of time, whatever each one costs. | real | operating | latency(7) | Mining yield per cycle-hour |
| bandwidth | Bandwidth | The rig stat capping how many engagements can run at once. | game | operating | throughput(7) | The four rig stats |
| orders-of-magnitude | orders of magnitude | Steps of a thousand, and why each one changes what is possible. | real | operating | — | Scan durations; recovery clocks |

### 2.8 Group G — the processor

| id | name | gloss | status | stage | prerequisites | Met at |
|---|---|---|---|---|---|---|
| processor ⇧ | processor | The part of a machine that reads instructions and carries them out. | real | operating | instruction(7) | Compute Cores |
| core | core | One instruction-follower; a chip may hold many, working at once. | real | operating | processor(7) | Compute Cores |
| hardware-thread | hardware thread | Two instruction streams sharing one core's idle moments. | real | investigating | core(7) | Why `nproc` reports 16 on an 8-core chip |
| clock | clock | The steady pulse that decides when a chip may take its next step. | real | operating | processor(7) | Thermal Budget's recovery rate |
| **cycle** | cycle | One tick of the clock that paces a chip; billions happen a second. | real | operating | clock(7) | `72 / 100 cycles` — the homonym |
| thermal-budget | Thermal Budget | The rig stat governing how fast spent capacity comes back. | real, simplified | operating | processor(7), compute(7) | `rig-monitor`'s Thermal row |

### 2.9 The prerequisite graph, and the five checks

`00-curriculum-and-method.md` §6.4 fixes five checks. Four pass; one does not, and the failure is in the check rather than in the entries.

| # | Check | Result |
|---|---|---|
| 1 | Every `prerequisites` reference resolves | **Pass.** Every one names a domain-01 entry in §2.2–§2.8 |
| 2 | The graph is acyclic | **Pass.** Verified by hand; the deepest chain is five — bit → binary → bit-width → floating-point, and bit → byte → memory → memory-hierarchy |
| 3 | Every entry's `stage` ≥ the maximum `stage` of its prerequisites | **Pass** |
| 4 | Every entry is reachable from a `first-session` root by following prerequisites backwards | **Fail — see FN-2.** Domain 01 has exactly one `first-session` entry (`compute(7)`) and it is not a prerequisite of the representation cluster, because understanding a bit does not require understanding the rig's capacity budget. Seven entries here are legitimate roots with `prerequisites: none` |
| 5 | No `prerequisites` edge points from a lower-numbered domain to a higher one (**R8**) | **Pass**, trivially — 01 is the lowest and has no outward edges |

The seven roots are `computer`, `instruction`, `state`, `abstraction`, `bit`, `latency` and `orders-of-magnitude`. Each is a genuine foundation with nothing legitimately beneath it, and forcing an artificial edge to `compute(7)` to satisfy check 4 would teach a dependency that does not exist. **FN-2** proposes the amendment.

**Stage distribution:** `first-session` 1 · `operating` 21 · `investigating` 16 · `adversarial` 1.

One first-session entry, against a **twelve-entry ceiling shared by all seven domains** (**R2**). This is deliberate restraint and worth defending: the foundational domain is the one most tempted to front-load, because everything in it feels prerequisite. It is not. A player in their first twenty minutes needs to know that actions cost cycles and that the cost is visible. They do not need to know what a bit is, and telling them is the fastest way to make them stop reading (`00-curriculum-and-method.md` §6.3 R2).

### 2.10 References this document makes outside domain 01

`seeAlso` refs may point at entries in higher-numbered domains; `prerequisites` may not. The refs below are made by entries in §3 and **do not yet resolve**, because the owning documents are not written. They are listed so the debt is visible rather than discovered at CI time (`../client/04` §4.10, integrity check).

| Ref | Owner | Made by |
|---|---|---|
| `process(7)` | 02 | `program(7)`, `abstraction(7)` |
| `system-call(7)` | 02 | `abstraction(7)`, `layer(7)` |
| `virtual-memory(7)` | 02 | `memory(7)`, `memory-hierarchy(7)` |
| `filesystem(7)`, `permissions(7)` | 02 | `storage(7)` |
| `port(7)` | 03 | `bit-width(7)` |
| `packet(7)` | 03 | `latency(7)`, `throughput(7)` |
| `hash(7)` | 05 | `hexadecimal(7)`, `bit-width(7)` |
| `injection(7)` | 05 | `data-and-code(7)` |
| `canonicalization(7)`, `provenance-chain(7)` | 06 | `utf-8(7)`, `base64(7)`, `hexadecimal(7)` |

In-game command and concept refs that already exist in `../client/04` §3.10's catalogue — `df(1)`, `ls(1)`, `ps(1)`, `top(1)`, `grep(1)`, `verify(1)`, `item-history(1)`, `storage-tiers(7)`, `noise(7)`, `trace(7)` — are treated as resolving.

---

## 3. The sixteen full entries

### 3.1 Which sixteen, and why

Written out in full because a writer copies a full entry and only counts an inventory row. The selection rule is `00-curriculum-and-method.md` §8.1's own priorities: the game leans on it, it carries a misconception worth killing, or it unlocks several other concepts.

| Entry | Chosen because |
|---|---|
| `bit(7)` | Root of the whole domain. Also the one place the "why two states" question gets a real engineering answer instead of a shrug |
| `byte(7)` | Every size, every hash, every file. Prerequisite of eight other entries |
| `hexadecimal(7)` | Direct game surface (`sha256-9c2f…41ab`), and the single most common barrier between a competent adult and a hash |
| `base64(7)` | Direct game surface (the `[ raw ]` view), and "base64 is encryption" is a top-tier professional misconception |
| `unit-prefixes(7)` | The classic falsehood. `df -h` is on screen, and the answer is a standard, not a conspiracy |
| `bit-width(7)` | Unlocks `port(7)`, IPv4 exhaustion, overflow, Y2038 and every "why that number" question in domains 03 and 05 |
| `integer-overflow(7)` | Three verifiable named incidents, one of them still ahead of us |
| `floating-point(7)` | The game's ledger already made the correct engineering choice; the entry explains why it had to |
| `character-encoding(7)` | "It's just text" is never just text, and `recon` is the entire story channel |
| `utf-8(7)` | What actually ships. Also where a signature-over-bytes argument begins |
| `cycle(7)` | `../client/04` §2.15 calls this collision the most consequential in the game. Mandatory `notes:` and `CAVEATS` |
| `memory(7)` | Memory Buffer is a §2.15 homonym, and memory/storage confusion is near-universal |
| `storage(7)` | The other half of that split, plus the deleted-file misconception, which 05 will lean on |
| `latency(7)` | Latency-is-not-bandwidth is the most useful thing an adult can learn from this document about their own home |
| `abstraction(7)` | The organising idea of computing. Every other domain assumes it |
| `data-and-code(7)` | Where an entire attack class lives, and the reason a client rule in `../client/04` §3.1 exists |

**Two entries were written here and then ceded**, which is why the count is sixteen rather than eighteen. `processor(7)` and `memory-hierarchy(7)` were drafted in this document and independently drafted in `02-computer-architecture.md`, and `00-curriculum-and-method.md` §1.4 permits exactly one entry per concept. Both went to `02` on the ownership rule — a processor and a cache hierarchy are how the machine executes, not how it represents things — and this document now cites them (marked **⇧** in §2). The loss is smaller than it looks: the *latency ladder* those entries carried is still taught here, by `latency(7)`, which is this domain's and is what `02`'s pages cite back to.

**Four deliberate omissions from the sixteen, each for a stated reason:**

- **`compute(7)`** — its page is already drafted in `../client/04-terminology-and-education.md` §4.9. Rewriting it here would create two sources for one page, which is the exact drift §4.10's CI checks exist to catch. Inventoried, not rewritten.
- **`program(7)`** — one half of a lesson whose other half (`process(7)`, domain 03) is already written in `00-curriculum-and-method.md` §3.5. Writing the 01 half in isolation risks repeating the 03 half's content — which §3.5 explicitly forbids. It should be drafted as a pair with `03-operating-systems.md`'s owner, and **OS-11** already proposes moving `process(7)`'s body out of the method document and into `03`.
- **`orders-of-magnitude(7)`** — its payload is delivered in practice by `memory-hierarchy(7)`'s ladder and `latency(7)`'s physics. The standalone page is the unit-vocabulary page (nano, micro, milli) and has the weakest hook of the three.
- **`computer(7)`** — the domain's thesis, stated in §1.1, and every specific claim it would make lives in `bit(7)`, `program(7)`, `processor(7)` and `memory(7)`. Under §8.1 step 3 it struggles to fill a concrete `transfer`, and an entry that struggles at step 3 is a warning, not a formatting problem. Kept in the inventory; written last, if at all.

**A note on transfer tests.** Every one below states the platform it assumes, per `00-curriculum-and-method.md` §8.3 and pending **ED-8**. Each was **actually run** on macOS 15 (Darwin 25.5.0, Apple M3) before being written down, and the output quoted is the output observed. Where a Linux form differs it is given; where a test works unchanged on Windows it says so, because those are the ones worth having.

---

### 3.2 `bit(7)`

```
id:             bit
section:        7
name:           bit
canonical:      bit
gloss:          The smallest unit of information: one of exactly two states.
status:         real
aliases:        binary digit, bits
seeAlso:        byte(7), binary(7), bit-width(7), hexadecimal(7), memory(7),
                hash(7)
reading:        C. E. Shannon, "A Mathematical Theory of Communication" (1948),
                §1 — where the word first appears in print
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  none
hook:           The 256 in SHA-256, which the player meets as the prefix on
                every provenance record in an item's timeline
                (../client/06-resource-and-inventory-ui.md §8.2). Also the "16
                bits" on port-sweep(1)'s page (../client/04 §4.9).
misconception:  commonly believed computers use two states because binary is
                mathematically elegant or efficient; actually it is an
                engineering compromise about noise — two voltage levels can be
                told apart reliably on a warm, ageing, interference-filled
                circuit, and ten cannot.
transfer:       In a macOS or Linux terminal, run `printf 'A' | xxd -b`. The
                output is `01000001` — the eight bits that spell the letter A.
                Then `printf 'A' | xxd` prints `41`, the same eight bits in
                hexadecimal. The player can now say what "256-bit" counts, and
                why 8 bits gives 256 possibilities rather than 8.
verified:       Shannon 1948 §1 credits the contraction "bit" to J. W. Tukey —
                Bell System Technical Journal 27(3); multi-level cells are real
                (SLC/MLC/TLC/QLC flash store 1-4 bits per cell at a documented
                cost in endurance and error correction) — JEDEC/manufacturer
                documentation; `xxd -b` output confirmed on macOS 15.
                Checked 2026-07-25.

## DESCRIPTION

The 256 in SHA-256 is a count of bits, and so is the 16 in "a port is a
16-bit number". A bit is one piece of information that can be in exactly two
states: 0 or 1, off or on, no or yes. Everything on your rig is built out of
them and nothing else — an item id, a ledger row, a recovered memo, the
foreign miner in your process list.

A single bit says almost nothing. Bits are useful because they combine: n of
them have 2ⁿ possible arrangements. Eight bits give 256 arrangements. Sixteen
give 65,536. Two hundred and fifty-six give a number with seventy-eight
digits in front of it.

That doubling is where nearly every number in this game's vocabulary comes
from, which is why it is worth knowing before any of them.

## REAL-WORLD COUNTERPART

real — the bit, exactly as the reader's own machine has them.

Why two states rather than ten? Not elegance. A circuit is warm, ageing and
full of interference, and the voltage on a wire drifts. Asking "is this above
or below the threshold?" survives that drift. Asking "which of ten levels is
this?" does not, because the gaps between levels shrink by a factor of ten
while the noise does not. Two states buys the widest margin available, and a
wide margin is what keeps a machine working when its hardware is imperfect,
which hardware always is.

The compromise is visible where somebody took the other side of it. Flash
memory does store several levels per cell — four, eight, sixteen — to fit
more data in the same silicon, and it pays for that in wear and in the amount
of error correction the drive has to run underneath. The cheapest drives are
the ones that pushed hardest on this, and it is why they wear out first.

The word is a contraction of "binary digit", suggested by John Tukey and put
into print by Claude Shannon in 1948.
```

---

### 3.3 `byte(7)`

```
id:             byte
section:        7
name:           byte
canonical:      byte
gloss:          Eight bits, and the unit almost every size is counted in.
status:         real
aliases:        octet, bytes
seeAlso:        bit(7), hexadecimal(7), unit-prefixes(7), character-encoding(7),
                memory(7), storage(7)
reading:        RFC 791 §1.3 (why the specifications say "octet" and not
                "byte"); utf-8(7) on any Linux system
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  bit(7)
hook:           The storage window's used / capacity column and the `df -h`
                output behind it (../client/06-resource-and-inventory-ui.md §5;
                ../client/04 §3.10).
misconception:  commonly believed a byte is one character of text, so a
                thousand-character document is a thousand bytes; actually a
                byte is eight bits holding one of 256 values, and a single
                character may occupy one, two, three or four of them depending
                on which encoding is in use.
transfer:       In a macOS or Linux terminal:
                `printf 'hello' > /tmp/t && ls -l /tmp/t` reports 5.
                `printf 'héllo' > /tmp/t && ls -l /tmp/t` reports 6 — same five
                characters, one more byte. The player can now explain why a
                file size and a character count are different questions.
verified:       eight-bit byte and the octet convention — RFC 791 and the IETF
                practice of writing "octet" precisely because byte size varied
                on early hardware; historical non-8-bit words (PDP-10, 36-bit)
                — DEC documentation; both `ls -l` outputs confirmed on macOS 15.
                Checked 2026-07-25.

## DESCRIPTION

Every size in the storage window is counted in bytes, and so is every file
you recover, every item record you verify and every log line you read.

A byte is eight bits taken together. Eight bits have 256 arrangements, so one
byte holds one of 256 values — 0 to 255 if you are counting upwards, or −128
to 127 if one of the bits is spent carrying a minus sign. It is the smallest
piece of a file that anything will hand you individually: storage is
addressed a byte at a time, sizes are quoted a byte at a time, and a hash is
described as 32 of them.

The important consequence is that a byte is a *quantity*, not a *meaning*.
The byte 0x41 is the number 65, and it is also the letter A, and it is also
one eighth of a colour value, depending entirely on what is reading it.

## REAL-WORLD COUNTERPART

real — the byte, on every machine the reader has ever used.

Eight has not always been the answer. Early machines used 6-bit, 7-bit,
9-bit and 36-bit units, and that is exactly why internet standards say
**octet** where an ordinary person says byte: RFC 791 and its successors
needed a word that could only mean eight bits, on hardware where "byte"
could not be trusted to.

The byte is where the ladder of everyday units starts. A thousand-odd of
them is a kilobyte, and from there the counting gets contentious enough to
need its own page — see unit-prefixes(7). It is also the unit in which the
distinction between a *character* and a *number* stops being academic: a
plain English letter is one byte in the encoding almost everything uses, an
accented letter is two, and an emoji is four. See character-encoding(7).
```

---

### 3.4 `hexadecimal(7)`

```
id:             hexadecimal
section:        7
name:           hexadecimal
canonical:      hexadecimal
gloss:          Base 16, written 0-9 then a-f, so one digit stands for four bits.
status:         real
aliases:        hex, base 16
seeAlso:        bit(7), byte(7), binary(7), bit-width(7), base64(7),
                provenance-chain(7), verify(1)
reading:        `man ascii` on any Linux or macOS system — the table is printed
                in hexadecimal, octal and decimal side by side
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  byte(7), binary(7)
hook:           The item history timeline's `prev  sha256-9c2f…41ab` row, mono,
                middle-truncated and copyable
                (../client/06-resource-and-inventory-ui.md §8.2), and the full
                value the player sees when they copy it out.
misconception:  commonly believed hexadecimal is a different kind of data, or a
                form of scrambling; actually it is only a way of writing a
                number down — the bytes are byte-for-byte identical whether
                they are printed as hex, as decimal, or not printed at all.
transfer:       In a macOS or Linux terminal, `printf 'Hi!' | xxd` prints
                `4869 21` — three bytes, six hex digits, two digits each. Then
                `printf 'hello' | shasum -a 256` prints 64 hex characters:
                64 x 4 bits = 256, which is the 256 in SHA-256. The player can
                now read any hash, colour code or MAC address as what it is.
verified:       one hex digit is exactly four bits and a byte is exactly two,
                by construction (16 = 2^4); SHA-256 output is 256 bits = 32
                bytes = 64 hex characters — FIPS 180-4; `xxd` and `shasum -a
                256` outputs confirmed on macOS 15 (`4869 21`, and a 64-character
                digest). Checked 2026-07-25.

## DESCRIPTION

Open any item's history and the chain rows carry a value like
`sha256-9c2f…41ab`. Copy one out and you get sixty-four characters drawn from
`0123456789abcdef` and nothing else. That is hexadecimal, and the sixty-four
is not arbitrary.

Hexadecimal counts in sixteens instead of tens, using the ten ordinary digits
and then a, b, c, d, e, f for the values ten to fifteen. Because sixteen is
two multiplied by itself four times, **one hex digit is exactly four bits**
and one byte is exactly two hex digits, always, with no arithmetic and no
carrying between them.

That is the entire reason it exists. A SHA-256 value is 256 bits, which is 32
bytes, which is 64 hex characters — and you can point at any pair of
characters in that string and say precisely which byte it is.

Nothing is hidden by writing a value this way. `verify(1)` compares the same
bytes whether or not anybody prints them.

## REAL-WORLD COUNTERPART

real — hexadecimal, and the reader has already been reading it for years.

`#1e1e1e` in a colour picker is three bytes: red 30, green 30, blue 30. A MAC
address is six bytes. A memory address in a crash report is hexadecimal
because the alternative is a twenty-digit decimal number with no visible
structure. `U+00E9` is the Unicode number for é, written the same way.

Decimal cannot do this job. Ten is not a power of two, so a decimal digit
does not correspond to any whole number of bits, and the boundary between one
byte and the next falls in the middle of a digit. Writing the same value in
binary works but takes eight characters per byte, and a 256-bit hash becomes
an unreadable wall.

Hexadecimal is the compromise: compact enough to read, and aligned to the
bits underneath.
```

---

### 3.5 `base64(7)`

```
id:             base64
section:        7
name:           base64
canonical:      base64
gloss:          A way of re-spelling raw bytes using 64 printable characters.
status:         real
aliases:        base64url, b64
seeAlso:        byte(7), hexadecimal(7), character-encoding(7),
                provenance-chain(7), provenance-record(5)
reading:        RFC 4648 §4 (base64) and §5 (the URL-safe alphabet)
notes:          The game's provenance envelope uses the URL-safe alphabet of
                RFC 4648 §5, not the standard one. Do not "correct" a `-` or
                `_` in an example to `+` or `/`.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          investigating
prerequisites:  byte(7)
hook:           The [ raw ] provenance view, which shows the detached-JWS
                envelope with its `sig` field as a long run of letters, digits,
                `-` and `_` (../client/06-resource-and-inventory-ui.md §8.5;
                ../architecture/04-item-provenance.md §2).
misconception:  commonly believed base64 is a form of encryption, or at least
                obscures what it wraps; actually it is a reversible re-spelling
                with no key at all — one command undoes it, and it makes the
                data about a third larger rather than smaller.
transfer:       In a macOS or Linux terminal, `printf 'hello' | base64` prints
                `aGVsbG8=`, and `printf 'aGVsbG8=' | base64 --decode` prints
                `hello` straight back. The player can now recognise base64 on
                sight and, crucially, stop treating it as protection.
verified:       alphabets and padding — RFC 4648 §4 and §5; 3 bytes -> 4
                characters, so a 33% expansion, by construction; round trip
                confirmed on macOS 15 (`aGVsbG8=`). GNU coreutils uses
                `base64 -d` where BSD/macOS accepts `--decode`.
                Checked 2026-07-25.

## DESCRIPTION

Open an item's raw provenance view and the signature is a long unbroken run
of letters, digits, hyphens and underscores. That is base64, and it is not
protecting anything.

A signature is raw bytes, and raw bytes include values that no text format
can carry safely — line breaks, null bytes, control codes that would end a
JSON string early. Base64 solves that by re-spelling every three bytes as
four characters drawn from a fixed alphabet of sixty-four safe ones. Nothing
is added and nothing is concealed; the same bytes come back out.

The cost is size. Four characters for every three bytes means the text is
about a third larger than what it wraps. The `=` characters sometimes on the
end are padding, present because the input length was not a multiple of
three.

If a value in this game looks like unreadable noise, base64 is one of the two
most likely reasons. The other is hexadecimal(7).

## REAL-WORLD COUNTERPART

real — base64, specified in RFC 4648.

It is everywhere a binary value has to travel through something that only
handles text: email attachments, images embedded in a web page, API tokens,
certificates, and the signature fields of every JWS-based format including
this game's own item records.

Two alphabets exist. The standard one (§4) ends in `+` and `/`. The URL-safe
one (§5) replaces those with `-` and `_`, because `+` and `/` have their own
meanings inside a URL. This game's records use the URL-safe form, which is
why the signatures are full of hyphens.

The habit worth taking away is the one this page opened with. Base64 is a
transport convenience. Treating it as a secret — pasting a base64 token into
a public issue tracker, storing base64 "encrypted" credentials — is a
recurring, entirely real, entirely preventable class of breach.
```

---

### 3.6 `unit-prefixes(7)`

```
id:             unit-prefixes
section:        7
name:           unit prefixes
canonical:      unit prefixes
gloss:          Why a 2 TB drive shows as 1.8 TB: two ladders, 1000 and 1024.
status:         real
aliases:        kilobyte, kibibyte, KB, KiB, GB, GiB
seeAlso:        byte(7), storage(7), storage-tiers(7), df(1), throughput(7)
reading:        IEC 80000-13 (binary prefixes: kibi-, mebi-, gibi-); df(1);
                the "About bits and bytes" note at iec.ch
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  byte(7)
hook:           The storage window's capacity figures and the `df -h` output
                behind them (../client/06-resource-and-inventory-ui.md §5;
                ../client/04 §3.10, which gives `df` its `-h` flag).
misconception:  commonly believed drive manufacturers inflate capacity, which
                is why a 2 TB drive shows as 1.8 TB; actually the manufacturer
                is using the standard meaning of "tera" — a million million —
                while the operating system divides by 1024 three times and
                still writes "TB". The drive holds exactly what the box said.
transfer:       On macOS, run `df -h /` and then `df -H /` on the same disk.
                This machine reported `1.8Ti` and then `1995G` — a ten percent
                gap, same hardware, both correct. On Linux the pair is `df -h`
                and `df -h --si`. The player can now say which convention a
                number is using and stop suspecting fraud.
verified:       kibibyte = 1024 bytes, IEC 80000-13 (current edition 2025;
                prefixes introduced by IEC 60027-2 Amendment 2, 1999); Windows
                divides by 1024 and labels "GB" while macOS has used decimal
                since 10.6 (2009) — vendor documentation and reporting;
                divergence 2.4% at kilo, 7.4% at giga, 10.0% at tera, computed
                from 1024^n / 1000^n; `df -h` / `df -H` outputs observed on
                macOS 15. Checked 2026-07-25.

## DESCRIPTION

Ask the storage window for sizes and you get figures like `1.8 Ti`. Ask
elsewhere and the same disk is `1995 G`. Neither is wrong, and the gap is not
rounding.

There are two ladders in use and they disagree by a widening margin:

| Prefix | Decimal (SI) | Binary (IEC) | They differ by |
|---|---|---|---|
| kilo / kibi | 1,000 | 1,024 | 2.4 % |
| mega / mebi | 1,000,000 | 1,048,576 | 4.9 % |
| giga / gibi | 1,000,000,000 | 1,073,741,824 | 7.4 % |
| tera / tebi | 1,000,000,000,000 | 1,099,511,627,776 | 10.0 % |

The binary ladder exists because memory is addressed in powers of two, so
1024 is a natural quantity and 1000 is not. The decimal ladder exists because
"kilo" has meant a thousand since 1795 and is not the computing industry's to
redefine.

The trouble is not that both exist. It is that for thirty years the binary
one was written with the decimal one's abbreviations.

## REAL-WORLD COUNTERPART

real — and the standard settling it is IEC 80000-13.

It gives the binary ladder its own names and symbols: **kibibyte (KiB)**,
mebibyte (MiB), gibibyte (GiB), tebibyte (TiB). A kilobyte is 1,000 bytes. A
kibibyte is 1,024. There is no ambiguity left in the standard; there is
plenty left in the software.

Drive manufacturers quote decimal, correctly. Windows divides by 1024 and
prints "GB", which is where the missing tenth of a 2 TB drive goes. macOS has
used decimal since 2009, so the same drive reports two different capacities
depending on which machine it is plugged into. Linux's `df -h` and `ls -h`
use 1024 and label it `G`; `--si` switches them to 1000.

One trap left, because it catches everyone: **network speeds are decimal and
they are in bits, not bytes.** A 100 Mb/s connection carries at most 12.5
megabytes per second — a factor of eight, on top of everything above. See
throughput(7).
```

---

### 3.7 `bit-width(7)`

```
id:             bit-width
section:        7
name:           bit width
canonical:      bit width
gloss:          How many binary digits a number gets, and so how large it may be.
status:         real
aliases:        word size, width, 32-bit, 64-bit
seeAlso:        bit(7), binary(7), integer-overflow(7), floating-point(7),
                hexadecimal(7), port(7), hash(7)
reading:        RFC 9293 §3.1 (the TCP header, where the 16-bit port field is
                drawn); FIPS 180-4 (SHA-2 output sizes)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          investigating
prerequisites:  bit(7), binary(7)
hook:           port-sweep(1)'s page, which states that a port is a 16-bit
                number from 0 to 65535 (../client/04 §4.9), and the `sha256-`
                prefix in every provenance row
                (../client/06-resource-and-inventory-ui.md §8.2).
misconception:  commonly believed a number in a computer is simply a number, so
                a program that adds two of them gets the right answer; actually
                every number lives in a fixed number of bits chosen by somebody
                years earlier, and that choice sets a hard ceiling the program
                cannot exceed and usually does not check.
transfer:       In a terminal with Python 3 (macOS, Linux, or Windows via the
                Store build), run:
                `python3 -c "print(2**16-1, 2**31-1, 2**32-1, 2**63-1)"`
                It prints 65535, 2147483647, 4294967295 and
                9223372036854775807. The player can now explain, without
                looking anything up, why there are 65,536 ports, why IPv4 ran
                out, and where the "2.1 billion" ceiling in the news came from.
verified:       ports are a 16-bit field — RFC 9293 §3.1; IPv4 addresses are
                32 bits, giving 4,294,967,296 — RFC 791; SHA-256 produces 256
                bits — FIPS 180-4; x86-64 implementations use 48-bit virtual
                addresses (256 TiB), or 57-bit with five-level paging — Intel
                and AMD architecture manuals; Python output confirmed on
                macOS 15. Checked 2026-07-25.

## DESCRIPTION

Two of this game's numbers are explained entirely by their width. A port runs
0 to 65535 because the field holding it is sixteen bits wide. An item's
provenance hash is written `sha256-` because the value is two hundred and
fifty-six bits wide, every time, whatever it was computed over.

Width is decided in advance and it is not negotiable afterwards. n bits hold
2ⁿ different values, so:

- 8 bits — 256 values
- 16 bits — 65,536
- 32 bits — about 4.3 billion
- 64 bits — about 18 quintillion

If the number might be negative, one bit goes on the sign and the positive
range halves: eight bits is either 0 to 255 or −128 to 127, never both.

"64-bit machine" is the same idea one level up. It means the processor's own
working registers and its addresses are sixty-four bits wide — which is why a
32-bit machine could not use more than four gigabytes of memory, and why the
successor did not need another upgrade.

## REAL-WORLD COUNTERPART

real — field width and word size, in every system the reader will meet.

The numbers that shape the internet are all widths. An IPv4 address is 32
bits, so there are about 4.3 billion of them; the shortage is arithmetic, not
politics. A port is 16 bits (RFC 9293 §3.1), so a full scan is 65,536
questions, which is why scanning is slow and noisy. A SHA-256 digest is 256
bits, so it is 32 bytes, so it is 64 hexadecimal characters.

Real hardware is more conservative than its name suggests. A 64-bit x86
processor does not actually use 64-bit addresses: implementations use 48 bits
— 256 TiB of address space — or 57 with an extra level of page tables. The
remaining bits are reserved rather than wasted, so the width can grow later
without changing the instruction set.

The habit worth taking is to ask, of any suspicious limit, *how wide is it?*
Most of the arbitrary-looking ceilings in computing are a power of two with
the exponent filed off.
```

---

### 3.8 `integer-overflow(7)`

```
id:             integer-overflow
section:        7
name:           integer overflow
canonical:      integer overflow
gloss:          What happens when a count outgrows the space reserved for it.
status:         real
aliases:        overflow, wraparound, wrapping
seeAlso:        bit-width(7), floating-point(7), determinism(7), compute(7)
reading:        CWE-190 (Integer Overflow or Wraparound); FAA Airworthiness
                Directive 2015-09-07; the Ariane 5 Flight 501 Inquiry Board
                report (Lions, 1996)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          investigating
prerequisites:  bit-width(7)
hook:           The compute gauge's over-range segment. A server-side rig
                change can transiently produce an allocation beyond capacity,
                and the client is required to draw it cross-hatched past the
                100% mark rather than clamp the bar, because "clamping would
                make an impossible state look like a full rig"
                (../client/06-resource-and-inventory-ui.md §2.1). ⚠ FN-4.
misconception:  commonly believed a number that grows too large produces an
                error, so the program will tell you; actually in most compiled
                languages it silently wraps to a large negative value or to
                zero, and the program carries on confidently with a wrong
                answer that nothing flagged.
transfer:       In a terminal with Python 3, run:
                `python3 -c "import ctypes; x=ctypes.c_int32(2147483647);
                print(x.value); x.value+=1; print(x.value)"`
                It prints 2147483647 and then -2147483648. Adding one made the
                largest number the smallest, with no error. On macOS, `date -u
                -r 2147483647` prints Tue Jan 19 03:14:07 UTC 2038 — the exact
                second a great deal of software has left. On Linux the form is
                `date -u -d @2147483647`.
verified:       wrap from 2147483647 to -2147483648 observed on macOS 15 via
                ctypes; the 2038 instant confirmed by `date -u -r 2147483647`;
                YouTube's 32-bit view counter and the 64-bit upgrade, 2014 —
                Google statement and Guinness World Records; Ariane 5 Flight
                501, 4 June 1996, a 64-bit float converted to a 16-bit signed
                integer, failure 37 seconds after launch — Inquiry Board
                report; Boeing 787 GCU counter overflowing after 248 days of
                continuous power — FAA AD 2015-09-07. The inference that 248
                days is 2^31 hundredths of a second (248.55 days) is widely
                drawn and consistent, but is not stated by the FAA.
                Checked 2026-07-25.

## DESCRIPTION

The compute gauge draws an allocation past 100% as a cross-hatched segment
sticking out beyond the track, rather than filling the bar and stopping. That
is a deliberate choice, and it is this page's whole subject: a number that has
exceeded the space made for it is dangerous mainly when something hides it. A
clamped bar looks like a full rig, and a full rig is a state you think you
understand.

Every number has a fixed width — see bit-width(7). Counting past the top of
that width does not raise an alarm in most languages. It wraps. The
odometer rolls over, and the largest possible value becomes the smallest
possible value in one step.

This rig does not simulate wrapping; nothing in the game will hand you a
negative cycle count. The lesson is the reverse of a mechanic: it is why the
one place the client *could* have quietly clamped an impossible number, it
was told not to.

## REAL-WORLD COUNTERPART

real — integer overflow, catalogued as CWE-190, and responsible for a
remarkable amount of history.

In 2014 the play counter on a single YouTube video reached 2,147,483,647 —
the largest value a signed 32-bit number holds. Google's engineers had
already seen it coming and moved the counter to 64 bits, which buys room for
about 9.2 quintillion.

Ariane 5 Flight 501 is the version with a body count of zero and a bill of
several hundred million. On 4 June 1996 a 64-bit floating-point value for
horizontal velocity was converted into a 16-bit signed integer that could not
hold it. The conversion failed, the guidance computer shut down, the backup
running identical code failed identically, and the rocket destroyed itself
thirty-seven seconds after launch.

The Boeing 787 case is the quietest and the most alarming. A counter inside
the generator control units overflows after 248 days of continuous power, at
which point all four units enter failsafe simultaneously and the aircraft
loses all AC electrical power. The FAA's answer, in Airworthiness Directive
2015-09-07, was to require that the aircraft be switched off periodically.

And one is still ahead of us. Time on Unix-derived systems has been counted
in seconds since 1970 in a signed 32-bit number, which runs out at 03:14:07
UTC on 19 January 2038.
```

---

### 3.9 `floating-point(7)`

```
id:             floating-point
section:        7
name:           floating point
canonical:      floating point
gloss:          How a machine holds fractions, approximately, in a fixed space.
status:         real
aliases:        float, double, IEEE 754
seeAlso:        bit-width(7), binary(7), integer-overflow(7),
                determinism(7), ledger-entry(5)
reading:        IEEE 754-2019; David Goldberg, "What Every Computer Scientist
                Should Know About Floating-Point Arithmetic" (1991)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          investigating
prerequisites:  bit-width(7), binary(7)
hook:           Every EC amount in the ledger, rendered as exactly two decimals
                over a value held as 100 minor units
                (../client/06-resource-and-inventory-ui.md §4;
                ../design/15-open-questions.md P-8). The player never sees a
                rounding error in this game, and this page explains what that
                cost.
misconception:  commonly believed a computer does exact decimal arithmetic, so
                0.1 + 0.2 is 0.3; actually the standard format stores fractions
                in binary, 0.1 has no exact binary form any more than a third
                has an exact decimal one, and the sum comes out as
                0.30000000000000004.
transfer:       In a terminal with Python 3 (macOS, Linux, or Windows via the
                Store build), run:
                `python3 -c "print(0.1+0.2); print(0.1+0.2==0.3)"`
                It prints 0.30000000000000004 and then False. Every mainstream
                language does the same thing, because they all use the same
                hardware format. The player can now recognise the defect class
                in any spreadsheet or invoice that ends in a trail of zeroes
                and a four.
verified:       0.1+0.2 == 0.30000000000000004 and the comparison to 0.3 is
                False — observed on macOS 15, CPython 3; the stored value
                nearest 0.1 is 0.1000000000000000055511151231257827 — observed
                via `f"{0.1:.20f}"`; binary64 has 53 bits of significand
                precision and about 15-17 significant decimal digits —
                IEEE 754-2019 and `sys.float_info`. Checked 2026-07-25.

## DESCRIPTION

Every amount in the ledger is shown to exactly two decimal places, and the
value underneath is held as a whole number of minor units — 1,240.00 EC is
124,000 of them. That is not a display convention. It is the standard defence
against the subject of this page.

A computer stores fractions the way it stores everything else: as a fixed
number of bits, in binary. In binary, some perfectly ordinary decimal
fractions have no exact form at all. One tenth is one of them. Written in
binary, 0.1 repeats forever, exactly as a third repeats forever in decimal,
so the machine keeps the nearest value it can fit and that value is very
slightly too large.

Do arithmetic on two of those approximations and the error comes with you.
0.1 + 0.2 does not produce 0.3. It produces 0.30000000000000004, and a test
for equality with 0.3 returns false.

Nothing is broken. The format is doing exactly what it promised — it just
promised something narrower than most people assume.

## REAL-WORLD COUNTERPART

real — IEEE 754, the format essentially every processor implements in
hardware.

The common size, called `double` in most languages, is 64 bits: one for the
sign, eleven for the exponent, and fifty-two stored for the digits, giving
about fifteen to seventeen significant decimal digits. That is enormous
precision and it is still not exactness, and the difference between those two
words is where the money goes.

Which is why money is never stored in it. Ledgers, banks, payment processors
and this game all store currency as an integer count of the smallest unit —
cents, pence, satoshis, EC minor units — and put the decimal point in only
when printing. A currency amount is a count of indivisible things, and
integers count things exactly.

Two habits follow, and both are useful outside a terminal. Never compare two
floating-point values for equality; ask whether the difference is smaller
than some tolerance you chose deliberately. And when a total is off by a
hundredth, suspect the format before suspecting the arithmetic.
```

---

### 3.10 `character-encoding(7)`

```
id:             character-encoding
section:        7
name:           character encoding
canonical:      character encoding
gloss:          The rule that decides which letter a stored number stands for.
status:         real
aliases:        encoding, charset, text encoding
seeAlso:        byte(7), ascii(7), unicode(7), utf-8(7), data-and-code(7),
                grep(1), recon(1)
reading:        RFC 3629 (UTF-8); the Unicode Standard, Ch. 2 "General
                Structure"; charsets(7) on any Linux system
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          investigating
prerequisites:  byte(7)
hook:           The recon window, which is the entire delivery channel for the
                story: recovered logs, emails and database records, searchable
                and cross-referenceable (../client/05-tool-windows-and-layout.md
                §2.6; ../design/14-world-and-narrative.md §3). Also the term
                index's use of a locale collator rather than byte order
                (../client/04 §4.11).
misconception:  commonly believed a text file contains letters and "plain text"
                has no format; actually a file contains numbers, and a separate
                rule — usually not stored in the file at all — decides which
                letters those numbers mean, which is why a document sometimes
                opens as `Ã©tÃ©` instead of `été`.
transfer:       In a macOS or Linux terminal, run the same string through two
                different questions:
                `printf 'héllo' | LC_ALL=C wc -c` prints 6.
                `printf 'héllo' | LC_ALL=en_US.UTF-8 wc -m` prints 5.
                Six bytes, five characters, one file. The player can now
                explain why a "1000-character limit" and a "1000-byte limit"
                are different limits, and why one of them cuts a name in half.
verified:       both `wc` outputs observed on macOS 15 (6 and 5); the é in
                UTF-8 is the two bytes c3 a9, observed via `printf 'é' | xxd`;
                byte-order sorting versus locale collation — the difference
                observed by running `sort` under LC_ALL=C and under
                LC_ALL=en_US.UTF-8 on the same four lines, which produced
                `A B a b` and `a A b B` respectively. Checked 2026-07-25.

## DESCRIPTION

Everything the story is told through arrives in the recon window as text:
memos, log lines, database rows, the routine bureaucratic paperwork that is
the Eye's most frightening output. None of it is stored as letters.

What is stored is numbers. A character encoding is the rule that says which
character each number stands for — 65 is A, 233 might be é or might be
something else entirely, depending on which rule is in force. The
uncomfortable part is that **the rule is usually not in the file.** It
travels separately, in a header, a declaration, a locale setting, or an
assumption. When the assumption is wrong, the text does not fail; it comes
out as somebody else's alphabet.

This is why "it's just a text file" is never quite true, and why a search
that works in one window can miss the same word in another.

## REAL-WORLD COUNTERPART

real — character encoding, and the reader has already been bitten by it.

`Ã©` where `é` should be is the signature: text written as UTF-8 and read as
a single-byte Western European encoding, so one character's two bytes get
displayed as two characters. The Japanese have a word for the general
phenomenon — *mojibake* — because they got it worse and earlier.

The historical shape is three layers. ASCII fixed 128 characters in seven
bits and covered English. A scatter of incompatible eight-bit extensions
covered everyone else, badly, and disagreed with each other above 127.
Unicode gave every character in every script one number, and UTF-8 gave those
numbers a byte form that is backwards-compatible with ASCII. See ascii(7),
unicode(7), utf-8(7).

Two practical consequences survive the history. Length is ambiguous —
"characters" and "bytes" are different counts, and a limit expressed in one
will surprise somebody working in the other. And sorting is not byte order:
sorting text by the numbers underneath puts every capital letter before every
lower-case one and files `ä` after `z`, which is why software that means it
sorts with a locale-aware collator instead.
```

---

### 3.11 `utf-8(7)`

```
id:             utf-8
section:        7
name:           UTF-8
canonical:      UTF-8
gloss:          The rule almost all text uses to turn letters into bytes today.
status:         real
aliases:        utf8, UTF8
seeAlso:        character-encoding(7), unicode(7), ascii(7), byte(7),
                canonicalization(7), provenance-record(5)
reading:        RFC 3629; RFC 8785 §3.2.2.2 (why a canonicalizer must reject
                text it cannot encode); utf-8(7) on any Linux system
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          investigating
prerequisites:  character-encoding(7), byte(7)
hook:           Recovered material in the recon window, and the fact that every
                page in this man system is itself stored and read as UTF-8
                (../client/04 §4.11).
misconception:  commonly believed Unicode and UTF-8 are two names for the same
                thing; actually Unicode is the catalogue that gives each
                character a number, and UTF-8 is one of several rules for
                writing those numbers as bytes — the same text in the same
                catalogue can sit on disk as two completely different byte
                sequences.
transfer:       In a macOS or Linux terminal:
                `printf 'A' | xxd` prints `41` — one byte.
                `printf 'é' | xxd` prints `c3a9` — two bytes, one character.
                `printf '€' | wc -c` prints 3 and a musical-clef character
                prints 4. The player can now predict how much space a name in
                any language will take, and why an English-only test suite
                never catches this.
verified:       byte counts of 1, 2, 3 and 4 for A, é, € and U+1D11E observed
                on macOS 15; é encodes as c3 a9, observed via xxd; UTF-8 covers
                U+0000..U+10FFFF in one to four octets and ASCII is a byte-for-
                byte subset — RFC 3629; 1,112,064 encodable code points =
                1,114,112 minus the 2,048 surrogates; Unicode 17.0 (2025)
                assigns 159,801 graphic and format characters — unicode.org
                character-count table. Checked 2026-07-25.

## DESCRIPTION

Every recovered document you read, and every page in this manual, is stored
as UTF-8.

Unicode gives each character in every writing system a number — é is 233,
written `U+00E9`. UTF-8 is the rule for turning those numbers into bytes, and
its design has one property that made it win: **the first 128 numbers encode
as a single byte, identical to ASCII.** An English-only file written forty
years ago is already valid UTF-8 and needs no conversion.

Everything above 127 takes two, three or four bytes. é takes two. The euro
sign takes three. Most emoji take four. Which means a character count and a
byte count are different numbers for the same text — an ordinary fact with
sharp edges, because software that assumes one byte per character works
perfectly right up until somebody's name is in it.

## REAL-WORLD COUNTERPART

real — UTF-8, specified in RFC 3629, and the encoding of the overwhelming
majority of text on the internet.

The numbering space runs from U+0000 to U+10FFFF, which is 1,114,112
positions; 2,048 of them are reserved as surrogates and cannot be encoded, so
1,112,064 are usable. Unicode 17.0, released in 2025, assigns 159,801 of
them, so there is a great deal of room left.

The reserved 2,048 are worth one sentence, because they are where signature
systems go wrong. They exist only as a pairing trick in a different encoding,
UTF-16, and they have no meaning on their own. A sloppy encoder handed one on
its own may silently substitute a replacement character — and if two different
inputs both come out as the same bytes, and the bytes are what got signed,
then one signature covers two different documents. That is why RFC 8785
requires a canonicalizer to raise an error rather than substitute. See
canonicalization(7).

The rule underneath is the one to keep: when a signature is over bytes,
everyone must agree on the bytes first, and an encoding is where that
agreement lives.
```

---

---

### 3.12 `cycle(7)`

```
id:             cycle
section:        7
name:           cycle
canonical:      cycle
gloss:          One tick of the clock that paces a chip; billions happen a second.
status:         real
aliases:        clock cycle, tick
seeAlso:        clock(7), processor(7), compute(7), memory-hierarchy(7),
                latency(7), thermal-budget(7)
reading:        lscpu(1); any processor vendor's specification sheet, which
                quotes base and boost clocks in GHz
notes:          MANDATORY DISAMBIGUATION. This page is the real clock cycle.
                The game's *cycles* — the rig's 100-cycle budget — are units of
                capacity share and are compute(7)'s subject, not this page's.
                ../client/04 §2.15 names this the most consequential homonym in
                the game. Translators: if the target language has one word for
                both, coin or borrow a second rather than merging them, and say
                so in the translator note.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  clock(7)
hook:           The rig monitor's headline, `72 / 100 cycles`, which is the
                first number in the game and uses this word for something else
                (../client/05-tool-windows-and-layout.md §2.3;
                ../design/01-core-resources.md §1).
misconception:  commonly believed a 3 GHz processor performs three billion
                operations a second, one per tick; actually a tick is only the
                pace at which work may be started — a core may retire several
                instructions in one tick and may also stall for two or three
                hundred of them waiting on main memory.
transfer:       On Linux, `lscpu | grep -i mhz` prints the clock; on macOS,
                `sysctl -n machdep.cpu.brand_string` names the chip (this
                machine: `Apple M3`) and the vendor page gives its clock.
                Divide one second by that figure: a 3 GHz chip has a tick of
                about a third of a nanosecond. The player can then read
                memory-hierarchy(7)'s table and say, correctly, that a fetch
                from main memory costs their processor roughly 250 wasted
                ticks.
verified:       `sysctl -n machdep.cpu.brand_string` returned `Apple M3` on
                macOS 15; 1 / 3e9 s = 0.333 ns, by arithmetic; main memory at
                60-100 ns against a 0.33 ns tick is 180-300 ticks, by
                arithmetic over the figures in memory-hierarchy(7); light
                travels about 30 cm in a nanosecond in vacuum (299,792,458
                m/s), about 20 cm in fibre. Checked 2026-07-25.

## DESCRIPTION

The first number this game shows you is `72 / 100 cycles`, and the word is
borrowed. **A game cycle is a unit of the rig's capacity, and a real cycle is
a unit of time.** They are not the same thing and not the same size. See
compute(7) for the game's meaning; this page is the real one.

A processor is paced by a clock: a signal that ticks at a fixed rate, and on
each tick the chip may advance its work by one step. One tick is one cycle. A
3 GHz processor ticks three billion times a second, so one cycle lasts about
a third of a nanosecond — in that time light travels roughly ten centimetres
down a wire.

That is the whole of it. Everything else people say about cycles is about how
much work fits into one, and the answer is: it varies enormously.

## REAL-WORLD COUNTERPART

real — the clock cycle, as quoted on every processor specification ever
printed.

A cycle is not an operation. Modern cores are *superscalar*: they have
several execution units and can finish several instructions in the same tick
when the instructions do not depend on each other. The same core will also
sit for two or three hundred consecutive ticks doing nothing at all, because
a value it needs is in main memory and main memory is slow — see
memory-hierarchy(7).

So the honest reading of a clock figure is: how often the chip *may* take a
step, not how much it gets done. Two chips at the same gigahertz can differ
by a factor of two in real work, and the gigahertz figure will not tell you
which. This is why processor marketing moved on from clock speed, and it is
also why a machine can feel slow while its clock is at maximum.

## CAVEATS

The word in this game's interface is not this word. Everywhere the rig
monitor, the market, a tool's cost or a bot's reservation says "cycles", it
means a share of your rig's capacity that is held while something runs — an
amount, not a duration. A starting rig has 100 of them. A real processor does
not have 100 cycles; it has billions per second, and it does not run out of
them, it runs out of time. compute(7) is the page for the game's meaning.
```

---

### 3.13 `memory(7)`

```
id:             memory
section:        7
name:           memory
canonical:      memory
gloss:          Fast working space, holding what runs now, emptied when power stops.
status:         real
aliases:        RAM, main memory, DRAM
seeAlso:        storage(7), memory-hierarchy(7), memory-buffer(7), byte(7),
                virtual-memory(7), process(7), top(1)
reading:        free(1) on Linux; vm_stat(1) on macOS; the JEDEC DDR
                specifications for the refresh requirement
notes:          The game's **Memory Buffer** rig stat is a §2.15 homonym: it
                caps how many tools may be readied at once, and has nothing to
                do with an I/O buffer or with buffer overflows. See
                memory-buffer(7); do not merge the two pages.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  byte(7)
hook:           The Memory Buffer rig stat — "storage is how much you own,
                memory buffer is how much you can have readied at once"
                (../design/11-rig-infrastructure.md §2), rendered as loadout
                slots in ../client/06-resource-and-inventory-ui.md §7.6.
misconception:  commonly believed memory and storage are the same kind of space
                in different amounts, so "16 GB of memory" and "512 GB of
                storage" describe one thing twice; actually they are different
                hardware doing different jobs — memory loses everything the
                instant power stops, and it is roughly a thousand times faster.
transfer:       On macOS, `sysctl -n hw.memsize` prints installed memory in
                bytes (this machine: 25769803776, which is 24 GiB); on Linux,
                `free -h`. Then run `df -h` and compare. The player can now say
                which of the two numbers on a laptop's spec sheet is which, and
                why only one of them survives a power cut.
verified:       `sysctl -n hw.memsize` returned 25769803776 on macOS 15, which
                is exactly 24 x 2^30; DRAM stores each bit as charge on a
                capacitor that leaks, and the JEDEC DDR3/DDR4 specifications
                require every cell to be refreshed within a 64 ms retention
                window, one refresh command every 7.8 microseconds; RAM latency
                of 60-100 ns against storage in the tens of microseconds — see
                memory-hierarchy(7). Checked 2026-07-25.

## DESCRIPTION

Your rig has two different kinds of space and the game names them separately
on purpose. Storage is how much you own. **Memory Buffer** is how much you can
have readied at once — a tool sitting in the vault costs you nothing, a tool
socketed into your loadout occupies a slot.

That is a fair sketch of the real distinction. Memory is the fast working
space a machine uses for whatever is running right now. Anything a program is
actually touching has to be there. It is expensive, there is comparatively
little of it, and it empties completely the moment power stops.

The consequence that matters is that memory is a *capacity*, not a *speed
dial*. Adding more helps enormously while there is not enough and does
essentially nothing once there is. That is the shape the Memory Buffer stat
is modelling: another slot only helps if you had a tool you wanted to field.

## REAL-WORLD COUNTERPART

real — RAM, and the reason a machine forgets.

The usual kind is DRAM, and it is worth knowing how it works because it
explains the forgetting. Each bit is a tiny capacitor holding a charge, and
the charge leaks. The standard requires every cell to be read and rewritten
within 64 milliseconds, forever, just to keep what is already there — a
refresh command goes out roughly every 7.8 microseconds. Remove power and the
charge is gone in a fraction of a second. Nothing is "erased"; it simply
stops being held up.

That refresh cost is also why memory is not free to have. It draws power
continuously whether or not anything is using it.

The number on a spec sheet is not the whole story either. An operating system
will happily use every spare byte as a cache for things it read from storage,
and then hand it back the moment a program wants it — which is why "memory is
95% used" on a healthy machine is normal and not a problem. What actually
hurts is running out, at which point the system starts using storage as
overflow memory and everything slows by a factor nobody expects. See
memory-hierarchy(7).
```

---

### 3.14 `storage(7)`

```
id:             storage
section:        7
name:           storage
canonical:      storage
gloss:          The slow, permanent place data sits when nothing is using it.
status:         real
aliases:        disk, drive, persistent storage, SSD
seeAlso:        memory(7), memory-hierarchy(7), unit-prefixes(7),
                storage-tiers(7), filesystem(7), df(1), ls(1)
reading:        df(1), du(1); the ATA and NVMe specifications' TRIM/deallocate
                commands
notes:          Not to be confused with storage-tiers(7), which is this game's
                three-tier exposure model. This page is the hardware idea; that
                page is the game rule built on top of it.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  byte(7)
hook:           The audit window's storage table, whose columns are mount, used
                / capacity, and **delta since last audit** — the delta being
                one of the three discrepancies a careful player uses to find a
                hidden miner (../client/05-tool-windows-and-layout.md §2.7;
                ../design/04-mining.md §3.1).
misconception:  commonly believed a deleted file is gone; actually deleting
                usually removes the entry that points at the data and marks the
                space reusable, leaving the bytes in place until something is
                written over them — which is why recovery tools work at all and
                why securely destroying data is a separate, slower operation.
transfer:       In a macOS or Linux terminal:
                `printf 'hello' > /tmp/t && ls -l /tmp/t` reports 5 bytes, but
                `du -h /tmp/t` reports 4.0K. Storage is handed out in blocks,
                so a five-byte file still occupies a whole one. The player can
                now explain why a folder of tiny files takes far more space
                than the sum of its file sizes.
verified:       `ls -l` reported 5 and `du -h` reported 4.0K for the same
                five-byte file on macOS 15; deletion removing the directory
                entry rather than the data is the basis of file-recovery tools
                and of the wipe/erase distinction — filesystem documentation;
                ⚠ on SSDs the TRIM/deallocate command lets the controller erase
                blocks in the background, so recovery after deletion is far
                less reliable than on a spinning disk, and where full-disk
                encryption is in use discarding the key is the effective wipe.
                Checked 2026-07-25.

## DESCRIPTION

The audit window's storage table has three columns, and the third one is the
interesting one: **delta since last audit**. Storage is where things stay when
nothing is looking at them, which is exactly why a change in it is evidence.

Storage is the slow, permanent half of the pair. Its contents survive power
loss, it holds hundreds of times more than memory does, and reaching anything
in it takes on the order of a thousand times longer. Everything you own lives
here; only what you are actively running is copied into memory.

The slowness is not a defect to be engineered away — it is the price of
persistence — and it is the reason a Thorough Scan takes six minutes rather
than six seconds. A scan is not thinking hard. It is waiting for storage,
several million times.

## REAL-WORLD COUNTERPART

real — persistent storage: solid-state drives, spinning disks, and everything
built on them.

Two facts that repay knowing.

**Space is handed out in blocks, not bytes.** A filesystem allocates in fixed
units — commonly 4 KiB — so a five-byte file still occupies a whole block.
This is why the size of a folder and the sum of the sizes of its files are
different numbers, and why a million tiny files are expensive in a way their
contents do not explain.

**Deleting is not erasing.** In the ordinary case, deleting a file removes the
record pointing at the data and marks the space as available. The bytes stay
until something writes over them, which is the entire basis of the
file-recovery industry, and it is why a "secure erase" is a separate and
slower operation than a delete.

⚠ That second fact is weaker than it used to be, and honesty demands the
qualification. On an SSD the operating system usually tells the drive the
blocks are free, and the drive's controller may erase them in the background
within seconds. And on a machine with full-disk encryption, the real wipe is
discarding the key — after which the remaining bytes are unreadable whether
or not they are still physically present.
```

---

---

### 3.15 `latency(7)`

```
id:             latency
section:        7
name:           latency
canonical:      latency
gloss:          How long one operation takes from request to answer.
status:         real
aliases:        delay, round-trip time, RTT, ping
seeAlso:        throughput(7), bandwidth(7), memory-hierarchy(7),
                orders-of-magnitude(7), relay-chain(1), packet(7)
reading:        ping(8), traceroute(8); Stuart Cheshire, "It's the Latency,
                Stupid" (1996)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  none
hook:           The Relay Chain, where each additional hop buys anonymity and
                costs delay, with diminishing returns past three
                (../design/08-stealth-and-noise.md; ../client/04 §2.7), and the
                map window's hop distance
                (../client/05-tool-windows-and-layout.md §2.5).
misconception:  commonly believed a faster connection means faster responses,
                so upgrading the line fixes a sluggish application; actually
                delay and capacity are independent — a gigabit line does not
                shorten a round trip to another continent by one millisecond,
                because that number is set by distance and the speed of light.
transfer:       Works on macOS, Linux and Windows. Run `ping` against a host
                nearby and one far away and compare the `time=` figures; then
                `traceroute` (macOS, Linux) or `tracert` (Windows) and watch
                the numbers climb as the hops get further away. The player can
                now tell a slow connection from a distant one, which are
                different problems with different fixes.
verified:       speed of light in optical fibre is about two-thirds of the
                vacuum figure of 299,792,458 m/s, so roughly 200,000 km/s —
                refractive index of silica fibre near 1.47; New York-London
                great-circle distance about 5,585 km, giving a round-trip floor
                of about 56 ms by arithmetic; the lowest-latency dedicated
                cable in service is measured at 58.95 ms round trip — operator
                published figures; Tor uses three relays by default —
                Tor Project documentation. Checked 2026-07-25.

## DESCRIPTION

Every hop you add to a Relay Chain buys you distance between your traffic and
your name, and charges you delay for it. Past about three hops the anonymity
gain flattens and the delay does not, which is why three is the number.

Latency is how long one thing takes, measured from asking to answering. It is
not a measure of how much a connection can carry, and confusing the two is
the single most expensive misunderstanding a person can hold about a network.

The distinction, in one line: **latency is how long the first byte takes;
throughput is how many bytes arrive per second afterwards.** A relay chain
adds latency and barely touches throughput. A busy connection loses
throughput while its latency stays flat. They move independently, and almost
every complaint about a "slow" system is precisely one of them.

## REAL-WORLD COUNTERPART

real — latency, and it has a floor that no amount of money moves.

Signals in optical fibre travel at about two-thirds of the speed of light in
vacuum: roughly 200,000 kilometres per second. New York to London is about
5,585 kilometres, so a there-and-back trip cannot take less than about 56
milliseconds. The fastest dedicated transatlantic cable in commercial service
measures 58.95 — five percent above the physical limit, which is close enough
to the limit that the remaining engineering is about route length, not
technology.

That is why a video call to another continent has a perceptible beat in it
however much bandwidth both ends have bought, and why moving a service closer
to its users is a real engineering strategy while upgrading its connection
often is not.

The ladder in memory-hierarchy(7) is the same idea at smaller scales: one
nanosecond inside a core, a hundred to main memory, tens of thousands to
storage, tens of millions across an ocean. Every one of those numbers is a
latency, and every one of them is a floor rather than a target.
```

---

### 3.16 `abstraction(7)`

```
id:             abstraction
section:        7
name:           abstraction
canonical:      abstraction
gloss:          A smaller promise standing in front of a larger mechanism.
status:         real
aliases:        abstraction layer
seeAlso:        layer(7), interface(7), program(7), shell(7), grep(1),
                system-call(7), process(7)
reading:        Joel Spolsky, "The Law of Leaky Abstractions" (2002);
                awk(1) and jq(1) as the usual answers to the leak below
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          operating
prerequisites:  none
hook:           Pipelines. `ps | grep miner` filters the *rendered text* of the
                process list, exactly as real Unix does, which means it can
                match the wrong column — and ../client/04 §3.7 keeps that
                fidelity deliberately, calling the cost "itself the lesson".
misconception:  commonly believed an abstraction means the thing underneath no
                longer matters, so you never need to know what it hides;
                actually it means you usually do not need to — and every
                abstraction leaks at exactly the moment its cost matters, which
                is the moment you are trying to fix something.
transfer:       In a macOS or Linux terminal, run `ps aux | grep bash`. The
                output includes the `grep` process itself, because `grep` is
                filtering lines of text and has no idea that one of them
                describes the command doing the filtering. The player has now
                seen an abstraction leak, in one line, and can predict where
                the next one will.
verified:       `ps aux | grep bash` returned the matching grep process in its
                own output on macOS 15; text-stream filtering as the mechanism
                behind that behaviour — grep(1), ps(1); the "leaky abstraction"
                framing is Spolsky (2002) and is widely attested in the field.
                Checked 2026-07-25.

## DESCRIPTION

Type `ps | grep miner` and something instructive happens: the filter works on
the *text* of the process list, not on the processes. It can match a number in
the wrong column. That behaviour is not a shortcut in this game; real Unix
pipelines work exactly the same way, and this client keeps the behaviour on
purpose.

An abstraction is a smaller promise put in front of a larger mechanism.
"A list of lines" is a promise. Underneath it is a process table with typed
fields, and the promise deliberately forgets that. In exchange you get to
attach any filter in existence to any command in existence, which is the
entire reason the pipeline is worth having.

Every window in this game is an abstraction over the same server-owned state,
and every command is another. `top` and the rig monitor are two promises over
one truth.

## REAL-WORLD COUNTERPART

real — abstraction, the organising idea of the whole field.

Computing is built as a stack of these. Transistors promise logic gates.
Gates promise instructions. Instructions promise a program. The operating
system promises files rather than blocks on a disk, and processes rather than
a scheduler's bookkeeping. A network promises a connection rather than a
succession of packets that may arrive out of order or not at all. Nobody
holds all of it in their head, and the layering is what makes that acceptable.

The part worth learning is the failure mode. **Abstractions leak.** They hold
right up until the mechanism underneath starts to matter, which is almost
always when something is slow, broken or under attack — the exact moment you
were relying on not needing to know. Reading a file is a simple promise until
the file is on a network drive and the promise takes four seconds. A
programming language promises numbers until floating-point(7) reminds you what
is underneath.

So the useful habit is not to distrust abstractions. It is to know what each
one is standing in front of, so that when it leaks you know which floor to go
down to.
```

---

### 3.17 `data-and-code(7)`

```
id:             data-and-code
section:        7
name:           data and code
canonical:      data and code
gloss:          The same stored numbers can be read as content or as steps.
status:         real
aliases:        code as data, stored program
seeAlso:        program(7), byte(7), instruction(7), memory(7),
                character-encoding(7), injection(7), overflow-kit(1)
reading:        CWE-89 (SQL injection), CWE-79 (cross-site scripting),
                CWE-77 (command injection); von Neumann, "First Draft of a
                Report on the EDVAC" (1945)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         01
stage:          adversarial
prerequisites:  program(7), byte(7)
hook:           The client rule that recovered material is inert: "a recovered
                log line that reads `rm -rf /rig` is a story prop, and clicking
                it does nothing" (../client/04 §3.1 rule 7). That rule exists
                because of this concept, and a player who reads such a line in
                the recon window has just met it.
misconception:  commonly believed a document cannot do anything because it is
                only data; actually whether a run of bytes is content or
                instruction is decided entirely by whatever reads it, and most
                serious attack classes consist of persuading something to read
                attacker-supplied data as instructions.
transfer:       In a macOS or Linux shell, run `echo '$(whoami)'` and then
                `echo "$(whoami)"`. The first prints the characters
                `$(whoami)`; the second prints your username, because the same
                characters were treated as something to run. Nothing changed
                but the quoting. The player has now seen the boundary move, and
                can recognise why "just put quotes round it" is both a real fix
                and an unreliable one.
verified:       both `echo` forms observed on macOS 15 — the single-quoted form
                printed `$(whoami)` literally and the double-quoted form
                printed the username; the stored-program principle is von
                Neumann's 1945 EDVAC draft; injection classes and their
                identifiers — MITRE CWE-89, CWE-79, CWE-77; NX/DEP marks pages
                writable or executable but not both, and parameterised queries
                separate a query's structure from its values — vendor and
                CWE mitigation documentation. Checked 2026-07-25.

## DESCRIPTION

Recovered documents in this game are inert by rule. A memo you pull out of an
Eye node might contain a line reading `rm -rf /rig`; it is a story prop, and
clicking it does nothing, because the client never interprets recovered
content as a command.

That rule is not caution for its own sake. It is the single most important
structural fact about computers, stated as a policy.

Instructions are stored the same way everything else is: as numbers, in the
same memory, indistinguishable by inspection. There is no flag on a byte
marking it "this one is a step". Whether a run of bytes is a picture, a price,
a sentence or a program is decided entirely by what reads it and what that
reader decides to do.

That is what makes a computer general-purpose — a machine that can load a new
program is a machine that can become a different machine. It is also, and for
exactly the same reason, where a whole family of attacks lives.

## REAL-WORLD COUNTERPART

real — the stored-program principle, and the injection class of vulnerability
that falls straight out of it.

The pattern is always the same shape. A system builds an instruction by
gluing together something it wrote and something a stranger supplied, and the
stranger's part contains characters the reader treats as structure rather than
content. A search box whose text ends up inside a database query is SQL
injection (CWE-89). A comment whose text ends up inside a web page is
cross-site scripting (CWE-79). A filename that ends up inside a shell command
is command injection (CWE-77). Three names, one mistake.

The defences are all the same idea too: keep the structure and the values on
separate channels so that no amount of cleverness in the value can change the
structure. Parameterised queries send the query and the data separately.
Templating systems escape by default. Processors mark memory as writable or
executable but not both, so bytes that arrived as data cannot later be run as
instructions.

Filtering the dangerous characters out of the input is the approach that
looks obvious and fails, repeatedly and famously, because it requires
predicting every spelling of "dangerous". Separation does not.
```

---

## 4. What this domain deliberately does not teach

`00-curriculum-and-method.md` §7.3 makes the rule blunt: an entry with no hook is not written, however interesting, because the delivery mechanism is contextual and a concept with no surface has no trigger. It exists only in the index, where it is found only by someone who already knows to look for it. Scope discipline here is not economy — it is the difference between an entry that gets read and an entry that costs writing, review, translation and index noise and teaches nobody.

Five cuts, each with the reason:

**Buses, interrupts, DMA and I/O generally.** `00-curriculum-and-method.md` §1.4 lists "buses and I/O" in this domain's remit and this document does not cover them. No game surface addresses a device: the namespace in `../client/04` §3.2 has `/rig/`, `/net/`, `/ledger/` and `/man/`, and nothing that names a device. `00-curriculum-and-method.md` §5.3 already anticipates this — man section 4 is *reserved, not banned*, precisely so that device concepts can arrive with a real teaching payload if uOS ever renders a device tree. Until then, an interrupt is a fact with no consequence for the player, and a fact with no consequence is trivia.

**How a chip is built.** Transistors, logic gates, adders, pipelines, branch prediction, out-of-order execution. This is genuinely the most interesting material in the domain and it earns exactly two sentences, in `processor(7)` and `cycle(7)`, where they explain something the player can act on. Branch prediction explains nothing the player can act on. `reading:` is the honest way to serve the impulse.

**Assembly language, registers as a programming surface, and how compilation works.** The game has no programming surface (`../client/04` §3.1), so there is nothing to hook to, and `00-curriculum-and-method.md` §5.3 already refuses man sections 2 and 3 for the same reason. A *register* is named once, in `00-curriculum-and-method.md` §2.5's `system-call(7)` prose, as a one-clause definition inside another entry — which is the correct treatment for a term that is needed but not taught.

**Number bases beyond 2, 10 and 16.** Octal exists, appears in file permissions, and belongs to `permissions(7)` in domain 02 where the player actually meets `chmod 640`. Teaching it here would be teaching it twice.

**Character encodings other than ASCII, Unicode and UTF-8.** UTF-16, Latin-1, Shift-JIS, code pages. `character-encoding(7)` needs *the existence of incompatible encodings* to explain mojibake, and it says so in one clause; it does not need their names. The one exception is UTF-16's surrogates, which get a mention in `utf-8(7)` because a signature-over-bytes argument in domain 06 depends on them.

**And one thing that looks like a cut and is not.** Endianness is inventoried, not deleted, with a ⚠ on its hook (FN-3). It is the one concept in this document whose fate depends on a client decision that has not been made. Recording it as contingent is more useful than either writing it speculatively or dropping it silently.

---

## 5. Open questions

Prefix **`FN-`**, unused elsewhere in the doc set. Log in `../design/15-open-questions.md` §2 if this document is adopted.

- **FN-1: ✅ RESOLVED with ED-3 (2026-07-25) — foundations is its own domain at `01`, and architecture moved to `02`.** The recommendation made here was taken: `00-curriculum-and-method.md` §1.4 now lists `01-foundations.md` as a domain in its own right and gives its reasoning in the sub-section beneath the table. The argument that carried it is the one made here — "computer architecture" implies the hardware cluster, roughly half of this document is representation, and a document named for the smaller half attracts the wrong entries — plus one fact only visible once the inventory existed: this is the only domain that forward-references nothing, which is exactly what a `01` should be.

- **FN-2: `00-curriculum-and-method.md` §6.4 check 4 rejects legitimate roots.** The check requires every entry to be reachable from a `first-session` root by following prerequisites. Domain 01 has one `first-session` entry (`compute(7)`) and seven entries with `prerequisites: none` — `computer`, `instruction`, `state`, `abstraction`, `bit`, `latency`, `orders-of-magnitude`. Each is a genuine foundation, and giving any of them an artificial edge to `compute(7)` would assert a dependency that does not exist and would violate **R1**'s spirit while satisfying its letter. **Proposed amendment:** restate check 4 as *"every entry is reachable from some root, and every root at a stage later than `first-session` names in one clause why it is not first-session."* That keeps what the check was for — catching a concept that arrives from nowhere — without forcing false prerequisites. This is a defect in the contract, not a preference, and it will hit all six domains.

- **FN-3: `endianness(7)` has no confirmed hook.** No client surface documented in `../client/05`–`06` renders bytes in a form where byte order is visible: the `[ raw ]` provenance view shows JSON and base64url text, and the item timeline shows hex hashes, none of which expose ordering. If a byte-level view is ever added — a hex pane in `recon` for a captured artefact would be the natural place — the entry is written. If not, it is deleted under §7.3. **Owner: whoever specifies `recon`'s reader.** Until then it stays inventoried with a ⚠ and is not written.

- **FN-4: is the compute gauge's over-range segment a strong enough hook for `integer-overflow(7)`?** `../client/06-resource-and-inventory-ui.md` §2.1 requires an allocation past capacity to be drawn beyond the track rather than clamped, and the reason it gives — that clamping "would make an impossible state look like a full rig" — is the same argument this entry makes about silent wrapping. It is a real, cited, specific surface. It is also indirect: the game does not model wrapping, and the entry says so in its own DESCRIPTION. If a reviewer judges it too indirect, the fallback is to fold the three incidents into `bit-width(7)`'s `reading:` and delete the entry. **Resolve in technical review, not by argument here.**

- **FN-5: `throughput(7)` and `bandwidth(7)` are inventoried but not written, and the homonym is load-bearing.** `../client/04` §2.15 requires the Bandwidth collision to be stated on the page, not implied — the rig stat caps simultaneous engagements, which in reality is a connection limit or a worker-pool size, and has nothing to do with bits per second. Both entries are straightforward; neither is in the eighteen because `latency(7)` carries the conceptual distinction. **They should be in the first batch written after this document is reviewed**, and `bandwidth(7)` needs the mandatory `notes:` line that every §2.15 homonym requires.

- **FN-6: `../client/04` §4.9's `compute(7)` page predates this curriculum and has no header block.** It is a drafted page in the mechanism document, with prose but no `hook`, `misconception`, `transfer`, `stage`, `prerequisites` or `verified`. Under `00-curriculum-and-method.md` §1.2 the curriculum entry is upstream of the shipped page, so `compute(7)` currently has its artefacts in the wrong order. Recommend back-filling a curriculum header for it in this document at the next revision, leaving the body text in `../client/04` §4.9 as the single source for the prose. Related to **ED-2** — if pages are generated from entries, this one has nothing to generate from.

- **FN-7: `00-curriculum-and-method.md` §2.5's memory-hierarchy exemplar quotes 50-100 microseconds for a small SSD read; measured figures for current NVMe drives are lower.** A 2025 PCIe 5.0 drive measures about 30 microseconds for a 4 KiB random read at queue depth 1, with 20-70 the typical modern range; 50-100 is right for a SATA SSD or a drive under load. `memory-hierarchy(7)` above uses **20-100 microseconds** and names the dependency. The two documents should agree. Recommend widening §2.5's row rather than narrowing this one, since the register exemplar is quoted as the model for six domains. Minor, but it is exactly the drift **ED-10** exists to catch, discovered on the first domain.

- **FN-8: three transfer tests in §3 have no Windows form, and two of them are among the best in the document.** `xxd`, `shasum`, `base64`, `df -H` and `du` are Unix. `ping`, `tracert` and Python 3 work everywhere, and `latency(7)`, `bit-width(7)`, `floating-point(7)` and `integer-overflow(7)` were deliberately written around that. The remainder assume a Unix shell, per **ED-8**'s interim rule (a). Two observations for whoever resolves ED-8: option (c) — targeting what is universal — is achievable for about half of this domain at no loss, because Python 3 and a browser's developer tools cover representation and numbers completely. It is *not* achievable for `abstraction(7)`, whose entire transfer is a Unix pipeline leaking, and forcing it would destroy the best one-line demonstration in the document.

- **FN-9: `misconception` is the strongest field in every entry above and the player never sees it (ED-5).** Writing sixteen of these is evidence rather than opinion, so it is worth recording: in this domain the misconception is frequently *the entry's reason to exist* — `unit-prefixes(7)`, `base64(7)`, `floating-point(7)` and `latency(7)` are each almost entirely a correction. Their DESCRIPTION sections therefore had to smuggle the correction in as a positive statement, which works but loses the direct address. This is a genuine argument for a `## COMMON MISTAKE` section, and a genuine argument against it is that four pages opening with "you probably believe X" would read as a lecture. **Suggested resolution: try it on exactly one page** — `unit-prefixes(7)`, where the false belief is an accusation of fraud against a manufacturer and correcting it directly is a service — and decide from the play test rather than in the abstract.

- **FN-10: nothing in this domain has had a technical review, and `00-curriculum-and-method.md` §8.4 makes that a gate rather than a courtesy.** Every factual claim above is sourced and dated in a `verified:` field, and every transfer test was actually run — but §8.4 rule 1 is explicit that a writer who verified their own claims has checked that the claim matches a source, not that the source was the right one or that the framing is what a practitioner would recognise. The specific places a practitioner is most likely to wince are the cache latency figures in `memory-hierarchy(7)` (which vary by microarchitecture more than a single table can honestly express), the SSD deletion caveat in `storage(7)` (where TRIM behaviour is drive- and filesystem-dependent), and `data-and-code(7)`'s mitigation list (which is domain 05's subject matter appearing early). **ED-6 is the blocking question; this is its first concrete instance.**
