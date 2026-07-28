# 03 — Operating systems: uOS and what an OS actually does

**Status:** ⚠️ **[PROPOSAL]** — the *obligation* is Established: client pillar **C6** (`../client/00-client-overview.md` §2), the product goal that a player should finish a session understanding more about "how operating systems, networking and computation work" (`../client/00-client-overview.md` §5), and — hardest of all — `../design/04-mining.md` §3.1's requirement that the process, connection and storage views be **real, consistent data** so that a careful player can find a rootkit-wrapped miner from a discrepancy. That requirement is an operating-systems requirement, and this document is what makes it teachable. The *inventory*, the stage assignments and the 18 written entries are first-pass design and are safe to change.
**Depends on:** `00-curriculum-and-method.md` — **the contract**: the entry template (§3), the status procedure (§4), man-section assignment (§5), the four stages and rules R1–R8 (§6), coverage (§7); `../client/04-terminology-and-education.md` §1 (the principle and §1.1a on uOS), §2 (the vocabulary map — cited, never duplicated), §2.15 (the homonym table), §3 (the command grammar, the namespace, the pipeline), §4.8 (the file format), §4.9 (six worked entries — the register); `../design/glossary.md` (canonical terms and the uOS definition); `../design/04-mining.md` §3.1, §3.2, §5; `../client/00-client-overview.md` §3.3, §5, §6.1, pillar **C6**; `../../CLAUDE.md`
**Depended on by:** `client/src/main/resources/terms/**` for the sections 1, 7 and 8 pages this domain owns; and, through `seeAlso`, every other domain curriculum — `socket`, `file-descriptor`, `process` and `permissions` are cited from at least three of them

---

## 1. What this domain is

### 1.1 The domain in one paragraph

An operating system does three things and nothing else: it **arbitrates** (one machine, many programs, finite hardware — somebody must decide who gets the processor and the memory), it **abstracts** (a program says "write this to a file" rather than addressing a specific block on a specific device), and it **protects** (a program may not read another's memory, may not touch the disk directly, and may not do either of those by accident). Every concept in this document is one of those three jobs seen close up. Processes and the scheduler are arbitration. Files, paths and descriptors are abstraction. Permissions, privilege levels, virtual memory and namespaces are protection. The kernel is where all three are implemented, and the system call is the only door into it.

### 1.2 Why a player of *this* game benefits

Three reasons, in increasing order of force.

**Because the player is already using one.** Every rig in the game runs **uOS**, which is Unix-like *by construction* rather than by resemblance (`../design/glossary.md`; `../client/00-client-overview.md` §3.3). The player is not learning a game that is like an operating system. They are operating one. `../client/04` §1.1a states the consequence precisely: uOS may *extend* Unix and may never *contradict* it, so a page here that describes uOS accurately also describes a real machine accurately. That is why this domain has the highest `real` proportion in the whole curriculum (§2.4) and the lowest licence to invent.

**Because the game's best mechanic is an operating-systems skill.** `../design/04-mining.md` §3.1 makes it a hard implementation requirement that a careful player can find a rootkit-wrapped miner without spending a single compute, "because the discrepancy is always present in the data" — cycle totals that do not add up, a connection with no owning process, storage deltas. That is **cross-view detection**, and it is how real rootkits are really caught. A player who cannot read a process table against a connection table cannot perform it, and a player who has not been told that a rootkit intercepts *answers* rather than *facts* has no reason to believe the discrepancy exists. The game has already paid for the expensive half. This document is the cheap half.

**Because it is the vocabulary the interface is made of.** The rig monitor is `top`. The audit window is `ps`, `ss` and `df` cross-referenced. The storage window is three mount points. The botnet window is `systemctl`. The market is a package manager. The player will read these words for hours whether or not anybody explains them; the only decision available is whether they leave knowing what they mean.

### 1.3 The surfaces this domain answers to

`00-curriculum-and-method.md` §7.1 item 1 requires a mechanical walk of every game surface the domain touches. This is that walk. Every row cites the document that specifies the surface, so a hook in the entries below is checkable rather than asserted (§7.1 item 4).

| Game surface | Specified in | Entries that answer it |
|---|---|---|
| `audit` window — process, connection and storage tables | `../client/00-client-overview.md` §6.1; `../design/04-mining.md` §3.1 | `ps(1)`, `process(7)`, `socket(7)`, `file-descriptor(7)`, `cross-view-detection(7)`, `df(1)` |
| `rig-monitor` — allocation by consumer | `../client/00-client-overview.md` §6.1 | `scheduler(7)`, `process(7)`, `daemon(7)` |
| `storage` window; `df`, `ls`, `stat`, `mv <item> <tier>` | `../client/04` §3.10; `../design/01-core-resources.md` §6 | `filesystem(7)`, `mount-point(7)`, `path(7)`, `inode(7)`, `permissions(7)`, `df(1)` |
| The virtual namespace `/rig/`, `/net/`, `/ledger/`, `/man/` | `../client/04` §3.2 | `path(7)`, `filesystem(7)`, `mount-point(7)` |
| `botnet` window; `jobs`, `bot build\|stop` | `../client/00-client-overview.md` §6.1; `../design/10-botnets.md` | `daemon(7)`, `init(7)`, `cron(7)`, `container(7)` |
| Bot **frame** vs **instance**; Invariant I11 | `../design/10-botnets.md`; `../client/04` §2.11 | `container(7)`, `process(7)` |
| `defense` window — armed defenses holding compute permanently | `../design/09-defense-and-hardening.md` | `daemon(7)`, `privilege(7)` |
| `terminal` pipelines — `ps \| grep miner` | `../client/04` §3.7 | `pipe(7)`, `file-descriptor(7)` |
| `scan --quick\|--full\|--thorough` | `../design/04-mining.md` §3.2 | `scan(8)`, `rootkit(7)`, `cross-view-detection(7)` |
| **Rootkit Wrapper** | `../design/09-defense-and-hardening.md`; `../client/04` §2.8, §2.15 | `rootkit(7)`, `kernel-module(7)`, `system-call(7)` |
| **Firmware Implant** — "survives a host wipe" | `../client/04` §2.9 | `boot(7)`, `kernel(7)` |
| **Isolated Partition** | `../client/04` §2.9, §2.15 | `namespace(7)`, `container(7)`, `virtual-memory(7)` |
| **Memory Buffer** | `../client/04` §2.9, §2.15 | `virtual-memory(7)`, `memory-pressure(7)` |
| **Bandwidth** (rig stat, capped concurrency) | `../client/04` §2.15 | `file-descriptor(7)` |
| `log [-f] [--since]`, RFC 5424 severity glyphs | `../client/04` §3.10 | `log(7)` |
| **Encrypted Vault** — "anything running as you can read it" | `../client/04` §4.9 `vault(7)` CAVEATS | `privilege(7)`, `permissions(7)` |
| `kill` / `crack` / `hijack` / `sabotage`; exit status `130` | `../design/04-mining.md` §5; `../client/04` §3.5 | `signal(7)`, `kill(1)` |
| `market search\|show\|install` | `../client/00-client-overview.md` §6.1 | `package-manager(7)` |
| The theme picker — both families draw the same uOS | `../client/00-client-overview.md` §3.3 | `uos(7)`, `operating-system(7)` |

### 1.4 What this document owns, and what it does not

The tie-break ladder is `00-curriculum-and-method.md` §1.4: *a concept belongs to the lowest-numbered domain that can fully define it without forward-referencing a higher-numbered one*, and where that does not decide, the domain whose game surface the player meets it on owns it. Applied here:

- **`socket(7)` is owned here, not by networking.** §1.4 uses this as its worked example: a socket needs the file-descriptor idea, so it belongs to the operating-systems domain and cites networking for `port(7)`.
- **`pipe(7)` is owned here; the pipeline *syntax* is not.** A pipe is a kernel object — a buffer with a descriptor at each end. The `|` character, its precedence and its exit-status rule are shell grammar and belong to the command-line domain. Both cite each other.
- **`rootkit(7)` and `cross-view-detection(7)` are owned here, not by security.** A rootkit is defined by what it does to the kernel's answers; it can be fully explained with `system-call(7)`, `process(7)` and `kernel(7)` and no security vocabulary at all. The security domain cites both.
- **The OS-state command pages are owned here** — `ps(1)`, `kill(1)`, `df(1)`, `scan(8)`. The command-line domain owns the *grammar* (flags, exit statuses, quoting, globs, pipelines, `man(1)`); this document owns the pages whose teaching payload is an operating-system concept. See **OS-3**: this is a boundary two agents can plausibly both claim, and it needs confirming rather than assuming.
- **Not owned here:** `compute(7)`, `thermal-budget(7)`, `memory-buffer(7)`, `top(1)` and anything about cores, clocks or latency numbers — computer architecture. `port(7)`, `packet(7)`, `ss(1)`/`netstat(1)` — networking. `shell(7)`, `glob(7)`, `quoting(7)`, `exit-status(7)`, `grep(1)` — the command line. `hash(7)`, encryption, anti-forensics, `log-scrubber(1)` — security. `storage-tiers(7)`, `unlock-gates(7)` — game-construct pages belonging with the resource docs.

> ✅ **This file is numbered `03` and that is now the contract's number too** (`00-curriculum-and-method.md` §1.4, resolved 2026-07-25). Entries below carry `domain: 03`. The only rule that depends on the number is **R8** — no `prerequisites` edge from a lower-numbered domain to a higher-numbered one — and every cross-domain prerequisite in this document points *downward*, into foundations and computer architecture. The command line, networking, cryptography and distributed systems appear in `seeAlso` only, never in `prerequisites`. Logged as **OS-1**, now closed.

---

## 2. The concept inventory

This is the coverage guarantee: the whole domain, visible at once. Thirty-five entries. Eighteen of them are written in full in §3; the other seventeen are specified here to the point where a writer can execute them without re-deciding anything.

### 2.1 How to read the table

`status` and `stage` are the template's values (`00-curriculum-and-method.md` §3.2, §4, §6.2). `prerequisites` are `name(section)` references; entries in **bold** are owned by another domain and are cited downward only. "Game surface" is the `hook` in compressed form — the full hook, with its document anchor, is in the entry.

### 2.2 The inventory

| # | id · § | Name | Gloss | Status | Stage | Prerequisites | Game surface |
|---|---|---|---|---|---|---|---|
| 1 | `uos` · 7 | uOS | The Unix-like system every rig in this game runs. | `game` | operating | `operating-system(7)` | The theme picker; every window |
| 2 | `operating-system` · 7 | operating system | The program that shares one machine's hardware between others. | `real` | operating | `process(7)` | Rig monitor, audit and storage as three views of one machine |
| 3 | `kernel` · 7 | kernel | The part of a system that has direct access to the hardware. | `real` | operating | `operating-system(7)`, `process(7)` | `scan(8)`; Rootkit Wrapper |
| 4 | `system-call` · 7 | system call | A program's request for the kernel to act on its behalf. | `real` | operating | `kernel(7)` | Every terminal command; Rootkit Wrapper's interception |
| 5 | `process` · 7 | process | One running instance of a program, with its own memory and id. | `real` | first-session | **`compute(7)`** | Audit process view; the foreign miner |
| 6 | `process-tree` · 7 | process tree | Each running task records which one started it. | `real` | investigating | `process(7)`, `signal(7)` | ⚠ contingent — audit process table parentage (**OS-5**) |
| 7 | `thread` · 7 | thread | One line of execution inside a program that shares its memory. | `real` | investigating | `process(7)`, `scheduler(7)` | Compute Cores' "hardware threads" — a homonym worth killing |
| 8 | `scheduler` · 7 | scheduler | What decides which task runs next, and for how long. | `real, simplified` | operating | `process(7)`, **`compute(7)`** | Rig monitor's allocation-by-consumer view |
| 9 | `virtual-memory` · 7 | virtual memory | Each program sees a private address range that is not the real one. | `real` | investigating | `process(7)`, `kernel(7)`, **`memory-hierarchy(7)`** | Isolated Partition; why a foreign miner cannot read your memory |
| 10 | `memory-pressure` · 7 | memory pressure | What a machine does when there is no room left to hold work. | `real` | investigating | `virtual-memory(7)` | Memory Buffer; running more bots than the rig holds |
| 11 | `filesystem` · 7 | filesystem | The organising scheme that turns a raw device into named data. | `real` | operating | `operating-system(7)` | Storage window; `df` |
| 12 | `path` · 7 | path | The text address of one place in a system's single naming tree. | `real, simplified` | operating | `filesystem(7)` | The `/rig/`, `/net/`, `/ledger/` namespace (`../client/04` §3.2) |
| 13 | `inode` · 7 | inode | The record that is the actual file; its name is only a label. | `real` | investigating | `filesystem(7)`, `path(7)` | `mv <item> <tier>`; `stat` |
| 14 | `mount-point` · 7 | mount point | The place in the tree where another device's contents appear. | `real` | operating | `filesystem(7)`, `path(7)` | Three storage tiers rendered as mounts; `df` |
| 15 | `file-descriptor` · 7 | file descriptor | A small number a program uses to refer to something it opened. | `real` | investigating | `process(7)`, `filesystem(7)`, `system-call(7)` | The Bandwidth rig stat (`ulimit -n`); the connection table |
| 16 | `socket` · 7 | socket | One endpoint of a network conversation, held open by a program. | `real` | investigating | `file-descriptor(7)`, `process(7)` | Audit connection table; "a connection with no owning process" |
| 17 | `permissions` · 7 | permissions | The recorded rules for who may read, change or run a thing. | `real` | operating | `filesystem(7)`, `process(7)` | Storage exposure; the Vault caveat |
| 18 | `privilege` · 7 | privilege | How much a running task is allowed to do, and on whose behalf. | `real` | investigating | `process(7)`, `permissions(7)` | A deployed miner running with the host's own rights (I6) |
| 19 | `signal` · 7 | signal | A short, numbered message the system delivers to a running task. | `real` | operating | `process(7)`, `kernel(7)` | `kill`; exit status `130`; `abort` |
| 20 | `pipe` · 7 | pipe | A one-way channel making one program's output another's input. | `real` | investigating | `file-descriptor(7)`, `process(7)` | `ps \| grep miner` (`../client/04` §3.7) |
| 21 | `daemon` · 7 | daemon | A program that runs continuously in the background, unattended. | `real` | operating | `process(7)` | Bot instances; armed defenses; the control channel |
| 22 | `init` · 7 | init | The first task a machine starts, and the parent of all others. | `real` | operating | `daemon(7)`, `process(7)` | `bot build\|stop`; the botnet window as `systemctl` |
| 23 | `cron` · 7 | cron | The service that runs chosen work at chosen times, unattended. | `real` | operating | `daemon(7)` | Bot frames that act on an interval; the backlog timer |
| 24 | `namespace` · 7 | namespace | A private view of system resources, given to some tasks only. | `real` | investigating | `process(7)`, `filesystem(7)`, `kernel(7)` | **Isolated Partition** (§2.15 homonym) |
| 25 | `container` · 7 | container | One running copy of a packaged program, isolated from the rest. | `real` | investigating | `process(7)`, `namespace(7)`, `filesystem(7)` | Frame vs instance; Invariant I11 |
| 26 | `boot` · 7 | boot | The handover chain that ends with a machine able to run work. | `real` | investigating | `kernel(7)`, `init(7)` | **Firmware Implant** — "survives a host wipe" |
| 27 | `kernel-module` · 7 | kernel module | Code loaded into the most privileged part of a running system. | `real` | investigating | `kernel(7)`, `system-call(7)` | Rootkit Wrapper's mechanism |
| 28 | `rootkit` · 7 | rootkit | Software that changes what a machine reports about itself. | `real` | investigating | `system-call(7)`, `process(7)`, `kernel(7)` | **Rootkit Wrapper** |
| 29 | `cross-view-detection` · 7 | cross-view detection | Asking two tools the same question and comparing the answers. | `real` | investigating | `rootkit(7)`, `ps(1)`, `socket(7)` | `../design/04-mining.md` §3.1 — the discrepancy in the data |
| 30 | `log` · 7 | log | An append-only record of what a system did, and when. | `real` | operating | `filesystem(7)`, `daemon(7)` | `log [-f] [--since]`; the eight severity glyphs |
| 31 | `package-manager` · 7 | package manager | The service that installs, updates and removes software for you. | `real, simplified` | operating | `filesystem(7)`, `permissions(7)` | The `market` window |
| 32 | `ps` · 1 | ps | Prints the table of everything currently running. | `real, simplified` | first-session | `process(7)` | The audit window's process view |
| 33 | `kill` · 1 | kill | Sends a numbered request to a running task, usually to stop it. | `real` | operating | `signal(7)`, `process(7)` | The four responses to a discovered miner (`04` §5) |
| 34 | `df` · 1 | df | Reports how much room each mounted device has left. | `real, simplified` | operating | `filesystem(7)`, `mount-point(7)` | The storage window's three tiers |
| 35 | `scan` · 8 | scan | Searches your own rig for things hiding from routine listings. | `real, simplified` | investigating | `rootkit(7)`, `cross-view-detection(7)` | `scan --quick\|--full\|--thorough` (`04` §3.2) |

### 2.3 Concepts this domain cites but does not own

Named here so that a `seeAlso` reference is a promise somebody else has made, not a gap. If any of these does not exist when the term files are written, the citing entry loses the reference — it does not grow a second definition (`00-curriculum-and-method.md` §1.4: two entries for one concept is never permitted).

| Reference | Owner | Cited by |
|---|---|---|
| `compute(7)`, `thermal-budget(7)`, `memory-hierarchy(7)`, `memory-buffer(7)`, `top(1)` | computer architecture | `process(7)`, `scheduler(7)`, `virtual-memory(7)`, `memory-pressure(7)`, `ps(1)` |
| `port(7)`, `packet(7)`, `ss(1)` / `netstat(1)`, `latency(7)` | networking | `socket(7)`, `cross-view-detection(7)` |
| `shell(7)`, `exit-status(7)`, `flag(7)`, `glob(7)`, `grep(1)`, `man(1)` | the command line | `ps(1)`, `pipe(7)`, `kill(1)`, `df(1)` |
| `hash(7)`, `signature(7)`, `log-scrubber(1)`, `integrity-monitoring(7)` | security and cryptography | `log(7)`, `package-manager(7)`, `rootkit(7)` |
| `provenance-chain(7)`, `did(7)` | distributed systems and identity | `package-manager(7)` |
| `storage-tiers(7)`, `unlock-gates(7)`, `noise(7)`, `heat(7)` | game-construct pages (`../client/04` §2.14) | `permissions(7)`, `scan(8)`, `log(7)` |

### 2.4 The honesty ledger

`00-curriculum-and-method.md` §7.1 item 5 and §7.4 require this published at the head of the domain.

| Status | Count | Share |
|---|---|---|
| `real` | 28 | 80 % |
| `real, simplified` | 6 | 17 % |
| `game` | 1 | 3 % |

**Why the `real` share is this high, and why that is a finding rather than a boast.** uOS is Unix-like by construction (`../client/04` §1.1a), so most of this domain is not a mapping between two worlds — it is one Unix-like system described in two vocabularies. The six `real, simplified` entries are exactly the places where the *game's* model diverges: `scheduler(7)` and `ps(1)` because the rig reserves capacity where a real machine time-shares it; `path(7)` because uOS's tree is not the Filesystem Hierarchy Standard and `/net/` shows only what the player has paid to discover; `package-manager(7)` because the market's five gates are not dependencies; `df(1)` because of the Exposure column; `scan(8)` because real scanning has no guaranteed find-rate at a stated price.

The single `game` entry is `uos(7)` itself, and it is written for exactly the reason §4.4 gives: a real-looking word that would otherwise be absorbed as real. There is no uOS to install.

**Unverified claims (⚠), listed per §7.4.** Two, both confined to entries below and both marked inline:

- The exact per-architecture stability of signal *numbers* (`signal(7)` states that 1, 2, 9 and 15 are constant across Linux architectures while some higher numbers are not — verified against `signal(7)`, but the game's own numbering is not yet designed; see **OS-8** on section numbers generally).
- Whether uOS has more than one user account at all (`permissions(7)`, `privilege(7)`). The design has never said. Both entries are written so they remain true either way, and the question is **OS-7**.

### 2.5 The graph, checked

`00-curriculum-and-method.md` §6.4's five checks, run by hand against §2.2.

1. **Every `prerequisites` reference resolves** — within this document, or to one of the six external references in §2.3. ⚠ The external ids are this document's *proposal* for what the neighbouring domains will call them; **OS-2** tracks the reconciliation.
2. **Acyclic** — yes. Every edge points to an entry at an earlier or equal stage, and within the `operating` band the order is `operating-system → kernel → system-call`, `operating-system → filesystem → {path, permissions} → mount-point`, `process → {daemon → {init, cron}, signal → kill}`, with no back edges.
3. **Stage ≥ max prerequisite stage** — yes, checked row by row.
4. **Every entry reachable from a `first-session` root** — yes. Both roots are `process(7)` and `compute(7)`; every other entry reaches one of them by following prerequisites backwards.
5. **No upward cross-domain edge** — yes. Every external prerequisite points into computer architecture; networking, the command line, security and distributed systems appear only in `seeAlso`.

**Stage distribution:** `first-session` 2 · `operating` 17 · `investigating` 16 · `adversarial` 0.

Two observations worth carrying forward. This domain claims **two of R2's twelve** `first-session` entries across the whole curriculum — `process(7)` and `ps(1)` — and the second one is claimed deliberately, because `../design/04-mining.md` §5.1 says the tutorial should plant a weak scripted miner early and `../design/04-mining.md` §3.1 calls manual investigation the game's second-strongest tutorial vector. A player who is shown a process table in the first twenty minutes and not told how to read it has been shown nothing. And **`adversarial` is empty on purpose**: nothing in operating systems is about untrusted peers. That stage belongs to distributed systems and identity, and a domain that padded into it would be inventing.

⚠ Seventeen `operating` entries against `00-curriculum-and-method.md` §6.2's ~25–40 for that stage *across all seven domains* is more than half the global budget for one document. Flagged as **OS-4** with a recommendation.

---

## 3. The written entries

### 3.1 Which, and why

Eighteen entries are written in full. They were chosen against `00-curriculum-and-method.md` §3.1's three tests — the game leans on it, it carries a misconception worth killing, or it unlocks several other entries — and between them they cover every stage, both man sections this domain uses in volume, and all three status values.

| Entry | Chosen because |
|---|---|
| `uos(7)` | The one `game` entry, and the frame for the whole domain. Also discharges `../client/04` §4.10's coverage check for the glossary's **uOS** row, which currently has no page (**OS-6**) |
| `operating-system(7)` | Unlocks the domain. Kills the most widespread false model in it — that the OS is the desktop |
| `kernel(7)` | Prerequisite of eight other entries. Without the privileged/unprivileged boundary, nothing about rootkits, permissions or isolation is explicable |
| `system-call(7)` | The actual interface. `../client/04` §2.8 calls the Rootkit Wrapper the game's best mapping, and this is the boundary a rootkit intercepts |
| `ps(1)` | The audit window's anchor, a `first-session` entry, and the only section-1 page written here — so it demonstrates the `SYNOPSIS` / `OPTIONS` / `EXIT STATUS` shape the other seventeen deliberately lack |
| `scheduler(7)` | Where "eight cores means eight programs" dies. Carries the game's most consequential divergence by pointing at it |
| `virtual-memory(7)` | Answers the question `process(7)` raises and does not settle: *why* one program cannot read another's memory |
| `filesystem(7)` | Prerequisite of six entries; the storage window's whole vocabulary |
| `inode(7)` | The best single teaching moment in the domain: `mv <item> <tier>` looks like a rename and is not |
| `permissions(7)` | The exposure model the storage tiers stand on, and a genuinely useful thing for an adult to know about their own machine |
| `file-descriptor(7)` | Unlocks `socket(7)` and `pipe(7)`; carries the mandatory **Bandwidth** homonym disambiguation (`../client/04` §2.15) |
| `socket(7)` | Half of cross-view detection. "A connection with no owning process" is meaningless without it |
| `signal(7)` | `kill -9` is the most confidently misunderstood command in computing, and the game's `kill` is exactly real |
| `daemon(7)` | Every bot, every armed defense and every control channel is one |
| `namespace(7)` | Carries the mandatory **Isolated Partition** homonym disambiguation, and is `container(7)`'s prerequisite |
| `container(7)` | Frame vs instance. `../client/04` §2.11 calls it "an unusually clean mapping"; it is also *why* Invariant I11 is shaped as it is |
| `rootkit(7)` | The game's strongest real mechanic, and a §2.15 row that exists only to stop anybody softening it |
| `cross-view-detection(7)` | The skill `../design/04-mining.md` §3.1 requires the player to be able to perform |

**`process(7)` is this domain's and is already written**, in full, in `00-curriculum-and-method.md` §3.5, where it serves as the template's worked example. It is not restated here: one entry, one home. **OS-2** proposes that at ship time the body moves into this document and §3.5 cites it, so that a future edit has exactly one place to land.

The remaining sixteen inventory rows are specified in §2.2 to the point where a writer can execute them under `00-curriculum-and-method.md` §8.1 without re-deciding scope, status or stage.

### 3.2 A note on register

Every entry below follows `00-curriculum-and-method.md` §2 and the worked pages in `../client/04` §4.9. `DESCRIPTION` is game-first. `REAL-WORLD COUNTERPART` opens with the status word. Numbers are given wherever a number exists, because that is where an adult's intuition actually forms. There are no analogies in this document at all: this domain describes machinery the reader already owns, so the honest move is to describe it, and §2.6's three conditions were never met by any candidate analogy considered.

---

### 3.3 `uos(7)`

```
id:             uos
section:        7
name:           uOS
canonical:      uOS
gloss:          The Unix-like system every rig in this game runs.
status:         game
aliases:        uos
glossary:       ../design/glossary.md
seeAlso:        operating-system(7), kernel(7), process(7), shell(7),
                permissions(7), signal(7), eyeandsickle(6)
reading:        The Open Group, "The Register of UNIX Certified Products";
                POSIX.1-2024 (IEEE Std 1003.1-2024); man-pages(7)
notes:          Casing is fixed: uOS in prose and anything a player reads,
                uos in identifiers (../design/glossary.md). Do not translate
                either form.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  operating-system(7)
hook:           The theme picker (../client/00-client-overview.md §3.3:
                both theme families draw the same uOS), and the first time
                the player types `ps` and a process table comes back.
misconception:  commonly believed a game's operating system is scenery, so
                nothing learned inside it applies outside; actually this one
                is Unix-like by construction rather than by resemblance —
                it may add what Unix lacks and is not permitted to
                contradict Unix (../client/04 §1.1a), so its processes,
                permission bits, descriptors and signals are the ones a
                real machine has.
transfer:       A recognition test rather than a command. The player can
                sort what they have learned into two lists — uOS's own
                (heat, noise, ethecoin, the five gates) and Unix's
                (processes, permissions, sockets, signals) — and check
                every item on the second list with `man 7 signal`,
                `man 1 ps` or `man 7 inode` on any Mac or Linux machine.
                Assumes a Unix shell — see ED-8.
verified:       macOS is UNIX 03-certified and Linux is generally not
                submitted for certification — The Open Group Register of
                UNIX Certified Products; uOS's Unix-like-by-construction
                rule — ../client/04 §1.1a, ../design/glossary.md.
                Checked 2026-07-25.

## DESCRIPTION

Your rig runs uOS. So does every rig in the game, including the ones you
break into. It is why `ps` lists processes rather than "programs", why
storage has mount points rather than drives, and why a permission is
three letters rather than a checkbox.

uOS is Unix-like on purpose. It may add things Unix does not have — this
world has heat, noise and ethecoin, and no real operating system has any
of them. What it may never do is contradict Unix. If something here
behaved differently from the real thing in a way you would carry to a
real machine and get wrong, that is a defect in this game, not flavour.

Both ways of drawing the client show the same uOS. Your laptop runs
macOS, Windows or Linux; your rig runs uOS; the client is the window.

## REAL-WORLD COUNTERPART

game — there is no uOS. You cannot install it, and nobody outside this
fiction has heard of it.

Everything it is modelled on is real and installable. "Unix-like" is a
family: Linux, the BSDs, and the Darwin layer underneath macOS. The
family resemblance is not vague — it is written down as POSIX, a
standard describing what a system of this kind must provide.

"UNIX" itself is a certification rather than a style. The Open Group
maintains a register of certified products; macOS is on it, and Linux
generally is not, because Linux distributors do not submit for
certification. Linux is Unix-like and not UNIX, and that distinction is
a trademark fact rather than a technical one.

## CAVEATS

uOS is a name in a game. Where this curriculum marks a page `real`, the
concept is genuine and you can go and check it. Where it marks a page
`game`, the concept exists only here. This page is the second kind, and
it is the reason the others can be the first kind: because uOS is
Unix-like by construction, describing uOS accurately and describing a
real machine accurately are the same act.
```

---

### 3.4 `operating-system(7)`

```
id:             operating-system
section:        7
name:           operating system
canonical:      operating system
gloss:          The program that shares one machine's hardware between others.
status:         real
aliases:        OS
seeAlso:        uos(7), kernel(7), process(7), system-call(7),
                filesystem(7), scheduler(7), permissions(7)
reading:        man-pages(7); Linux `uname(1)`; POSIX.1-2024 rationale
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  process(7)
hook:           The moment the player notices that the rig monitor, the
                audit window and the storage window are three views of one
                machine (../client/00-client-overview.md §6.1), and that
                the machine has a name.
misconception:  commonly believed the operating system is the desktop —
                the windows, the menus, the file manager, the thing that
                has a version number on the box; actually those are
                ordinary programs, and the operating system is the code
                underneath them that decides which program gets the
                processor, what memory each may touch, and whether a
                request to open a file is permitted. Most machines in the
                world run one with no desktop at all.
transfer:       On macOS or Linux, run `uname -sr` — it prints the
                operating system proper and its version, which on a Mac is
                "Darwin", not "macOS". Then `ps -e | wc -l` and count how
                many of those have a window. Assumes a Unix shell —
                see ED-8.
verified:       The three-job framing (arbitration, abstraction,
                protection) — standard operating-systems treatment, e.g.
                POSIX.1-2024 rationale and the Linux man-pages overview
                man-pages(7); `uname -sr` output on macOS reports Darwin —
                uname(1). Checked 2026-07-25.

## DESCRIPTION

Your rig has one set of hardware and a great many things asking for it:
bots, armed defenses, self-mining, a control channel for every deployed
miner, and whatever a stranger has left running. Something has to decide
who gets what. That something is the operating system, and on your rig it
is uOS.

It does three jobs, and every window in this client is one of them seen
close up.

It **arbitrates**. Finite capacity, many claims on it. The rig monitor is
this job made visible.

It **abstracts**. Nothing you run addresses hardware. A tool says "read
this item" and the operating system works out which device, which
offset, which permissions. That is why the storage window can show three
tiers as three mount points instead of three sets of physical addresses.

It **protects**. One process cannot read another's memory, cannot touch
storage directly, and cannot do either by accident. This is why a foreign
miner can steal your cycles and still not read your vault.

## REAL-WORLD COUNTERPART

real — operating systems, exactly as the reader's own machine has one.

The same three jobs, in the same order of importance. Linux, the BSDs,
Darwin under macOS and the Windows NT kernel all do them; they differ in
how, not in what.

The scale is where the intuition sits. An ordinary laptop has eight or
sixteen hardware threads and is running somewhere between two hundred and
a thousand processes. Fewer than ten of them have a window. The
arbitration is not occasional — the machine passes through the operating
system thousands of times a second, on every file read, every packet,
every timer, and every time a program asks for more memory.

On macOS or Linux, `uname -sr` prints the operating system's own name and
version. On a Mac it says Darwin, because macOS is the product and Darwin
is the operating system.
```

---

### 3.5 `kernel(7)`

```
id:             kernel
section:        7
name:           kernel
canonical:      kernel
gloss:          The part of a system that has direct access to the hardware.
status:         real
seeAlso:        operating-system(7), system-call(7), process(7),
                kernel-module(7), rootkit(7), virtual-memory(7), boot(7)
reading:        syscalls(2) — the Linux system-call list; kernel.org
                documentation; capabilities(7)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  operating-system(7), process(7)
hook:           `scan --quick|--full|--thorough` (../design/04-mining.md
                §3.2) and the Rootkit Wrapper (../design/09-defense-and-
                hardening.md), both of which turn on the question "who is
                answering when a tool asks what is running?"
misconception:  commonly believed the kernel is one more program among the
                others, just an important one; actually it is not a
                program in the ordinary sense at all — it has no process
                of its own for the scheduler to pick, and it runs in a
                more privileged processor mode that ordinary code cannot
                enter except through three doors: a system call, a
                hardware interrupt, or a fault.
transfer:       On Linux, `uname -r` prints the running kernel version and
                `lsmod | head` lists code currently loaded into it; on
                macOS, `uname -v`. Then `cat /proc/version` on Linux. The
                player can now say which part of their machine survives
                logging out and which does not. Assumes a Unix shell —
                see ED-8.
verified:       Privileged/unprivileged execution modes and the three
                entry paths — Linux kernel documentation and syscalls(2);
                Linux exposes well over 300 system calls on x86-64 —
                syscalls(2); loadable modules — lsmod(8), modprobe(8).
                Checked 2026-07-25.

## DESCRIPTION

When you run a scan, something has to look. When a tool lists your
processes, something has to know what they are. That something is the
kernel: the part of uOS that talks to the hardware and answers questions
about it. Everything else on the rig — your tools, your bots, the miner
somebody left behind — is on the outside asking.

The division has a name on each side. Code with direct hardware access
runs in the **kernel**; everything else runs in **user space**, which is
not a place but a restriction: a processor mode in which the instructions
that touch hardware are simply illegal.

This is the whole reason auditing can work, and also the reason it can be
defeated. Every question you ask about your rig is answered by the
kernel. Anything that gets *into* the kernel can change the answers. That
is what a rootkit is, and why the game's Rootkit Wrapper hides from a
routine listing but not from a deliberate audit — see rootkit(7).

## REAL-WORLD COUNTERPART

real — the kernel, the same thing on the reader's machine.

Linux, Darwin's XNU and the Windows NT kernel are all kernels; the
operating system as sold is the kernel plus a large collection of
ordinary programs around it.

The privilege split is enforced by the processor itself, not by
convention. The hardware carries a mode flag — Intel and AMD call the
levels rings, Arm calls them exception levels — and an instruction that
touches a device raises a fault if the flag is wrong. There is no way for
a normal program to talk itself past this; it can only ask.

There are exactly three ways into the kernel. A program asks
deliberately, which is a system call — Linux exposes well over three
hundred of them. A device interrupts, because a packet arrived or a timer
expired. Or the program makes a mistake — divides by zero, touches memory
it does not have — and the hardware hands control over whether the
program likes it or not.

On Linux, `uname -r` prints the running kernel's version and `lsmod`
lists code that has been loaded into it since boot.
```

---

### 3.6 `system-call(7)`

```
id:             system-call
section:        7
name:           system call
canonical:      system call
gloss:          A program's request for the kernel to act on its behalf.
status:         real
aliases:        syscall
seeAlso:        kernel(7), process(7), file-descriptor(7), rootkit(7),
                kernel-module(7), scheduler(7)
reading:        syscalls(2); ld.so(8) — the dynamic loader and LD_PRELOAD;
                strace(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  kernel(7)
hook:           Every command in the terminal resolves to one eventually,
                and specifically the Rootkit Wrapper (../client/04 §2.8),
                which works by intercepting requests at exactly this
                boundary.
misconception:  commonly believed that when a program writes a file, the
                program writes the file; actually the program cannot reach
                the disk at all — it hands a request across the boundary
                into the kernel, which does the work and hands control
                back, and that handover is both the security model and the
                dominant cost of anything that does a lot of small reads.
transfer:       On Linux, `strace -c ls` runs `ls` and prints a count of
                every system call it made — usually a few hundred for a
                one-line directory listing. On macOS the equivalent is
                `sudo dtruss ls`, and it needs privileges. The player can
                now see the boundary being crossed, by name and by count.
                Assumes a Unix shell — see ED-8.
verified:       System-call cost of order 100 ns, several hundred with
                processor-vulnerability mitigations enabled, against
                roughly 1 ns for an ordinary function call —
                ../education/00-curriculum-and-method.md §2.5, itself
                sourced from published KPTI microbenchmarks; Linux exposes
                well over 300 system calls on x86-64 — syscalls(2);
                LD_PRELOAD interposition — ld.so(8). Checked 2026-07-25.

## DESCRIPTION

Nothing your rig runs touches hardware. A tool that reads an item, a bot
that opens a connection, a miner that asks for more memory: each one
stops at the boundary and asks uOS to do it. That request is a system
call, and it is the only way across.

The rule is simple. Anything that touches hardware, or touches another
process, is a system call. Arithmetic is not. A tool computing a hash
never leaves user space; the same tool writing the result to storage
crosses the boundary once per write.

This matters here for one specific reason. If every question about your
rig is a request to uOS, then anything that can intercept requests can
lie in the answer. That is precisely how the Rootkit Wrapper hides a
miner — not by making it stop running, but by editing the reply. See
rootkit(7) and cross-view-detection(7).

## REAL-WORLD COUNTERPART

real — system calls, on every operating system the reader has used.

Reading a file, sending a packet and asking for more memory are all
system calls; `read`, `write`, `openat` and `close` are four a normal
program leans on constantly. Linux has well over three hundred, and most
programs use a few dozen.

The mechanism is deliberately narrow. The program puts a number
identifying its request into a register — one of a few dozen small
storage slots inside the processor — and runs a single special
instruction that switches the processor into kernel mode at a fixed entry
point. The kernel checks the request, does the work, and switches back.
The program never chooses where in the kernel it lands.

The switch is not free. It costs on the order of a hundred nanoseconds,
and several hundred on a machine with the usual processor-vulnerability
mitigations enabled, against roughly one nanosecond for an ordinary
function call. That is why a program doing millions of one-byte reads is
slow in a way that is hard to see, and it is the entire reason buffering
exists.

On Linux, `strace -c` counts them for any command you run.
```

---

### 3.7 `ps(1)`

```
id:             ps
section:        1
name:           ps
canonical:      ps
gloss:          Prints the table of everything currently running.
status:         real, simplified
aliases:        processes
seeAlso:        process(7), compute(7), top(1), kill(1), socket(7),
                cross-view-detection(7), scan(8), grep(1)
reading:        ps(1); proc(5) — the /proc filesystem on Linux; top(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          first-session
prerequisites:  process(7)
hook:           The audit window's process view
                (../client/00-client-overview.md §6.1), and the first
                time the player runs `ps` to find out what is holding
                their cycles — including the scripted miner the tutorial
                plants (../design/04-mining.md §5.1).
misconception:  commonly believed that a list of running programs is a
                short list, roughly the things with windows open;
                actually it is hundreds of entries on any machine, almost
                none of which have a window, and learning to read past
                that is the whole skill.
transfer:       On macOS or Linux: `ps -e | wc -l` counts the processes
                (one more than the true count — the first line is a
                header), and `ps aux | sort -rnk 3 | head` shows the ten
                using the most processor. The player can now say how many
                processes their laptop is running and which is the
                heaviest, and can recognise that Activity Monitor and
                Task Manager show the same list with a window round it.
                Assumes a Unix shell — see ED-8.
simplified:     uOS's `ps` prints compute allocation per consumer — a
                reservation held for as long as the consumer runs. A real
                `ps` prints a percentage of processor time recently used,
                because a real machine time-shares rather than reserves.
                The divergence itself is stated once, on compute(7); this
                page names it and points there.
verified:       `ps aux` is the BSD form and `ps -ef` the System V form,
                printing different columns — ps(1); Linux `ps` reads
                /proc — proc(5); macOS has no /proc and `ps` uses sysctl
                — ps(1) on Darwin. Checked 2026-07-25.

## SYNOPSIS

       ps [--by=consumer] [-v] [-h] [--explain] [--]

## DESCRIPTION

Prints everything currently running on your rig, one line each: what it
is, what started it, and how much compute it is holding. This is the
audit window as text, and it is the cheapest investigation in the game —
it costs no compute at all (../design/04-mining.md §3.1).

Read it against the rig monitor. The rig monitor gives you one total;
`ps` gives you the parts. When the parts do not add up to the total,
something is running that this list is not showing you, and that gap is
the single most useful thing on your screen. See
cross-view-detection(7).

`ps` is a read-only source, so it can start a pipeline:
`ps | grep miner`.

## OPTIONS

       --by=consumer   Group by what is holding the compute rather than
                       by start order.
       -v, --verbose   Include per-consumer attribution detail.
       -h, --help      Print SYNOPSIS and OPTIONS in place.
       --explain       Print this DESCRIPTION and exit without running.
       -n, --dry-run   Print published costs. `ps` sends no intent and
                       changes nothing in any case.
       --              End of options.

## EXIT STATUS

       0    The listing was produced.
       2    Bad invocation: unknown flag or bad argument.
       69   The server could not be reached; no listing was produced.

## REAL-WORLD COUNTERPART

real, simplified — `ps(1)`, on every Unix-like machine.

There are two syntaxes and they are not interchangeable. `ps aux` is the
BSD form and works on macOS and Linux; `ps -ef` is the System V form.
They print different columns, which is why two people can both be right
about what `ps` shows.

On Linux the command is not consulting anything privileged — it reads
`/proc`, a directory of files that the kernel invents on demand, one
numbered subdirectory per process. macOS has no `/proc` and its `ps`
asks the kernel directly instead. This difference matters later: two
tools that read the same source are not two views (see
cross-view-detection(7)).

`ps -e | wc -l` counts, one over, because of the header. `ps aux | sort
-rnk 3 | head` sorts by processor use. Activity Monitor and Task Manager
are this list with a window round it.

## CAVEATS

This `ps` reports compute allocation — capacity reserved for as long as
the consumer runs. A real `ps` reports a percentage of processor time
recently used, which is a different quantity: it moves second to second,
it can exceed 100 for a program using several cores, and it says nothing
about what has been promised. The rig reserves where a real machine
time-shares; that difference is stated in full on compute(7).

A real `ps` is also a single snapshot taken the moment you ran it, not a
live view. `top(1)` refreshes; `ps` does not.
```

---

### 3.8 `scheduler(7)`

```
id:             scheduler
section:        7
name:           scheduler
canonical:      scheduler
gloss:          What decides which task runs next, and for how long.
status:         real, simplified
aliases:        scheduling, time slicing, preemption
seeAlso:        process(7), thread(7), compute(7), kernel(7), ps(1),
                top(1), daemon(7)
reading:        sched(7); nice(1); the Linux kernel's EEVDF scheduler
                documentation (docs.kernel.org/scheduler)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  process(7), compute(7)
hook:           The rig monitor's allocation-by-consumer view
                (../client/00-client-overview.md §6.1), and the moment a
                player wonders why their rig cannot simply run one more
                thing.
misconception:  commonly believed a machine with eight cores can run
                eight programs at once and no more, so a laptop showing
                four hundred processes must be badly wrong; actually it
                runs eight at any *instant* and hundreds over any second,
                because the scheduler stops one and starts another
                thousands of times a second — which is why four hundred
                processes on eight cores is not overload, it is an idle
                machine.
transfer:       On macOS or Linux, run `uptime` and read the three load
                averages — they are counts of runnable tasks, not
                percentages, so on an 8-core machine a load of 4 means
                half busy and a load of 16 means twice oversubscribed.
                Then `ps -eo pid,ni,comm | head` to see the nice value of
                each process. Assumes a Unix shell — see ED-8.
simplified:     This page describes a scheduler as "pick the next task",
                which is one processor's worth of the truth. Real
                schedulers keep a separate run queue per processor,
                migrate work between them, treat processor-bound and
                input-bound work differently, and hold entirely separate
                classes for real-time work that must not be made to wait.
                Separately, uOS's rig does not model preemption at all —
                compute is reserved, never time-sliced — and that
                divergence is stated in full on compute(7) rather than
                repeated here.
verified:       Linux replaced the Completely Fair Scheduler with EEVDF
                in kernel 6.6 (2023) — docs.kernel.org/scheduler/sched-
                eevdf.html and the 6.6 merge announcement; nice range
                -20 to 19, lower is more favourable — nice(1), sched(7);
                measured direct context-switch cost of roughly 1.2–2.2 µs
                on Linux threads, with indirect cache-refill cost
                typically larger — E. Bendersky, "Measuring context
                switching and memory overheads for Linux threads" (2018).
                Checked 2026-07-25.

## DESCRIPTION

Your rig runs many things at once — bots, defenses, mining, a control
channel per deployed miner — and the rig monitor shows you what each one
holds. Something has to keep that arrangement true from moment to
moment. On a real machine that something is the scheduler, and it is the
part of the operating system that decides, continuously, which of the
waiting tasks gets the processor next.

The rig's version of this is unusually simple, and knowing the real
version is what makes the rig's simplification legible rather than
mysterious. See compute(7).

## REAL-WORLD COUNTERPART

real, simplified — process scheduling.

A processor core runs exactly one thing at a time. The impression that a
machine runs hundreds of programs simultaneously is produced by stopping
one and starting another thousands of times a second, fast enough that
nothing appears to pause. Each turn is a time slice, and taking the
processor away from a task that has not finished is called preemption —
the property that separates a modern system from one where a
badly-written program could freeze the machine.

Priority is adjustable. On Unix the knob is nice, and it runs from -20
(most favourable) to 19 (least); the name comes from being nice to other
users by lowering your own priority. `nice -n 10 <command>` runs
something at low priority, which is genuinely useful on a laptop.

The switch itself costs something. Measurements on Linux put the direct
cost at roughly one to two microseconds, and the indirect cost —
refilling processor caches that the previous task had filled with its own
data — is often larger than that. A machine that spends its time
switching is doing arithmetic for nobody.

The algorithms move. Linux ran the Completely Fair Scheduler from 2007
until kernel 6.6 in 2023, when it was replaced by EEVDF, which does the
same job with more predictable latency.

## CAVEATS

This page describes one processor's worth of scheduling. A real scheduler
keeps a run queue per core, moves work between cores, distinguishes
processor-bound from input-bound tasks, and reserves separate handling
for real-time work. None of that changes the picture above; all of it
would be needed to tune a real system.

The rig does not preempt anything. Compute here is reserved, not shared:
if it is allocated, nobody else gets it, and nothing on your rig is ever
paused to make room. Real machines are the other way round, and
compute(7) says so in full.
```

---

### 3.9 `virtual-memory(7)`

```
id:             virtual-memory
section:        7
name:           virtual memory
canonical:      virtual memory
gloss:          Each program sees a private address range that is not the real one.
status:         real
aliases:        paging, address space, MMU
seeAlso:        process(7), kernel(7), memory-pressure(7), namespace(7),
                memory-hierarchy(7), memory-buffer(7), rootkit(7)
reading:        mmap(2); proc(5) — /proc/<pid>/maps; ptrace(2)
notes:          Not the game's Memory Buffer, which is a rig upgrade
                measuring working-set capacity (../client/04 §2.15).
                Keep the two words apart in translation.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  process(7), kernel(7), memory-hierarchy(7)
hook:           The Isolated Partition (../client/04 §2.9), and the
                question a deployed miner on your own rig raises: it is
                running on your machine and stealing your cycles, so why
                can it not simply read everything you have?
misconception:  commonly believed "virtual memory" means the swap file —
                the disk space a machine borrows when it runs out of RAM;
                actually it is the address translation every modern
                machine performs on every single memory access, present
                and working on a machine with no swap at all. Swap is one
                consequence of the mechanism, not its definition.
transfer:       On macOS or Linux, run `ps -eo pid,rss,vsz,comm | head`.
                VSZ (virtual size) is usually many times larger than RSS
                (resident set size, the part actually in RAM), and the
                gap is this page: addresses a program holds are not
                memory a program is using. On Linux, `cat /proc/self/maps`
                prints one process's entire address layout. Assumes a
                Unix shell — see ED-8.
verified:       4 KiB page size on x86-64 and 16 KiB on Apple silicon —
                Arm64 page-size documentation (Ampere tuning guide; Asahi
                Linux) and Apple platform behaviour; x86-64 implements 48
                bits of virtual address by default, extended to 57 with
                5-level paging — Intel/AMD architecture documentation and
                the Linux x86 memory-map documentation; a debugger reads
                another process's memory through ptrace(2), not through
                its own address space — ptrace(2). Checked 2026-07-25.

## DESCRIPTION

A miner somebody deployed on your rig is a process on your machine. It
consumes your compute — that is the point of it (Invariant I6). What it
cannot do is read what your other processes are holding, and the reason
is not a rule the game invented. It is the way memory works on every
machine built in the last forty years.

Each process gets its own address space: a private range of addresses
that mean nothing outside it. Address 5,000 in one process and address
5,000 in another refer to different physical memory, or to no memory at
all. There is no instruction a program can run to reach outside it,
because the translation happens in hardware and the program never sees
the real address.

This is the argument behind the Isolated Partition, and it is the same
argument that makes cracking a miner a contained operation rather than a
catastrophe.

## REAL-WORLD COUNTERPART

real — virtual memory, on every desktop, laptop, phone and server.

Memory is handed out in fixed-size chunks called pages: 4 KiB on
ordinary x86-64 hardware, 16 KiB on Apple silicon. The processor
contains a unit — the memory management unit — that translates every
address a program uses into a physical one, using tables the kernel
maintains per process. Every access. Billions per second. A small cache
of recent translations, the TLB, is what makes it affordable.

The address space is enormous and mostly empty. x86-64 processors
implement 48 of the 64 address bits by default, which is 256 TiB, split
between the kernel's half and the program's; a newer mode extends it to
57 bits. A program holding a 40 GiB range of addresses may be using 200
MiB of actual memory, which is exactly what the gap between the VSZ and
RSS columns of `ps` is showing you.

Two useful consequences. A page can be shared: one copy of a library in
physical memory, mapped into forty processes at once. And a page can be
absent: the kernel can leave it on storage and fetch it when touched,
which is where swap comes from — see memory-pressure(7).

The isolation is not absolute, and the exceptions are worth knowing. A
debugger reads another process's memory, using a system call written for
the purpose and permission checks to match. So does anything running as
the same user with the right privileges. Isolation between processes is
strong; isolation from the machine's owner is not the same thing.
```

---

### 3.10 `filesystem(7)`

```
id:             filesystem
section:        7
name:           filesystem
canonical:      filesystem
gloss:          The organising scheme that turns a raw device into named data.
status:         real
aliases:        file system, fs
seeAlso:        path(7), inode(7), mount-point(7), permissions(7),
                df(1), file-descriptor(7), storage-tiers(7)
reading:        hier(7) — the Linux filesystem hierarchy; Filesystem
                Hierarchy Standard 3.0; statfs(2)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  operating-system(7)
hook:           The storage window (../client/00-client-overview.md §6.1)
                and `df`, which shows the three tiers as three separate
                things with separate capacities.
misconception:  commonly believed a folder contains its files the way a
                box contains objects, so moving a large file into a
                folder moves the data into it; actually a directory is
                itself a file whose contents are a list of names paired
                with numbers, and the data is elsewhere — which is why
                renaming a 40 GB file is instantaneous and why one file
                can have two names.
transfer:       On macOS or Linux: `df -h` lists the filesystems mounted
                right now and how full each is; `stat .` prints the
                current directory's own record, including that it is a
                directory and how large that list is. Assumes a Unix
                shell — see ED-8.
verified:       Directories are files containing name-to-inode mappings —
                inode(7), path_resolution(7); the Filesystem Hierarchy
                Standard is at version 3.0 (2015) and Linux documents the
                layout in hier(7); typical filesystem block size 4 KiB —
                statfs(2), mkfs defaults. Checked 2026-07-25.

## DESCRIPTION

A storage device is a very long row of numbered blocks and nothing else.
It has no notion of a name, a folder, or an item. A filesystem is the
scheme written on top of that row which turns it into names you can
address — and your rig has three of them, one per storage tier, which is
why the storage window can show three separate capacities.

The key structural fact is that a directory is not a container. It is a
file whose contents happen to be a list: name, and a number identifying
the record that holds the data. Everything follows from that. Renaming a
huge item is instant, because only the list entry changes. An item can
appear under two names at once. And moving an item from one tier to
another is not a rename at all, because the two tiers are two different
filesystems — see inode(7), which is where that has consequences.

## REAL-WORLD COUNTERPART

real — filesystems, on every machine the reader owns.

ext4 and XFS on Linux, APFS on macOS, NTFS on Windows, ZFS and Btrfs
where reliability is bought deliberately. They differ in how they record
things and in what they promise after a power cut; they all present the
same shape — a tree of directories, each a list of names paired with
record numbers.

Space is not handed out by the byte. A filesystem allocates in blocks,
typically 4 KiB, so a one-byte file occupies 4,096 bytes of the device.
Ten thousand tiny files cost about 40 MB regardless of their contents.

Unix has exactly one tree. There are no drive letters: a second device is
attached at some directory inside the existing tree, and from then on it
is simply part of it — see mount-point(7). Where things go in that tree
is conventional rather than arbitrary, and the convention is written
down: the Filesystem Hierarchy Standard, currently version 3.0, is why
`/etc` holds configuration, `/var/log` holds logs and `/home` holds
users on essentially every Linux machine. Linux documents its own layout
in `hier(7)`.

`df -h` shows the filesystems currently mounted and what is left on each.
```

---

### 3.10a `hier(7)`

```
id:             hier
section:        7
name:           hier
canonical:      hier
gloss:          Where things live on a Unix machine, and why it is not arbitrary.
status:         real, simplified
aliases:        filesystem hierarchy, layout
seeAlso:        filesystem(7), permissions(7), path(7), ls(1), df(1)
reading:        hier(7) on any FreeBSD or macOS machine; Filesystem
                Hierarchy Standard 3.0 (Linux); `man 7 hier`
revision:       1
verified:       2026-07-28 — against FreeBSD's hier(7), which is the
                canonical description of the layout this entry teaches.
                ⚠ Deliberately asserts nothing version-specific to
                FreeBSD 15: the LAYOUT is the durable, checkable part,
                and a claim about one release is the kind that goes
                stale silently.

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  filesystem(7)
hook:           The `files` window and `/System`, which a player can see
                the whole shape of and open none of. Being unable to
                read it is what sends them to this page.
misconception:  commonly believed that a Unix root directory is a
                historical accident — twenty cryptic two- and three-letter
                names with no logic to them; actually the split is by
                ANSWER TO ONE QUESTION: what must work before the rest of
                the disk is available. /bin and /sbin hold what boots the
                machine, /usr holds everything else, and /usr/local holds
                what nobody shipped with it. Once that question is
                visible the layout stops needing to be memorised.
transfer:       On any Mac or Linux machine, run `ls /usr/local/bin`.
                Everything in it was installed after the operating
                system. Then run `ls /bin` — that is what the machine
                needs to start. The two lists being different lengths is
                the whole idea.
```

## DESCRIPTION

A Unix filesystem is not one pile of files. It is split by a question:
**what has to work before the rest of the disk is available?**

    /bin  /sbin  /lib      what the machine needs to start at all
    /usr                   everything else the system ships
    /usr/local             everything nobody shipped with it
    /etc                   configuration, so it survives a reinstall
    /var                   files that change while it runs

That is the entire logic. `/bin` is short because almost nothing has to be
there. `/usr` is enormous because almost everything else does. `/usr/local`
exists so that upgrading the operating system cannot delete the software you
installed.

⚠ **FreeBSD draws the /usr/local line harder than Linux does.** On FreeBSD
the base system — kernel, libraries, and the tools in `/bin`, `/sbin`,
`/usr/bin` and `/usr/sbin` — is developed, versioned and released as one
coherent whole. Anything from a port or a package installs under
`/usr/local` and nowhere else. Linux distributions have no such boundary:
the same `/usr/bin` holds the C library's tools and yesterday's package
manager install, and telling them apart means asking the package manager.

That difference is the single most useful thing to carry away from this page,
because it explains a category of problem — "the upgrade removed my software" —
that one design has and the other does not.

## REAL-WORLD COUNTERPART

real, simplified — the layout is exact and is what `hier(7)` documents on any
FreeBSD or macOS machine. What is simplified is depth: uOS shows a
representative subset of each directory rather than the tens of thousands of
files a real base system contains.

## CAVEATS

**uOS's root is not FreeBSD's root.** uOS puts `/Applications`, `/Library`,
`/System` and `/Users` at the top, which is macOS's arrangement, and keeps the
FreeBSD hierarchy inside `/System`. On a real FreeBSD machine those
directories are the root. On a real Mac, `/System` exists and is very nearly
this — macOS is the system where both halves of this description are true at
once, which is why uOS is shaped like it.

**Nothing in `/System` opens, and that is a game limitation stated as one.**
A real `/System/boot/kernel/kernel` is a real kernel. uOS cannot ship one, and
a file that printed invented bytes would be teaching something false about the
one subject this page exists to teach. So the tree is complete and closed, and
this page is what you get instead.

---

### 3.11 `inode(7)`

```
id:             inode
section:        7
name:           inode
canonical:      inode
gloss:          The record that is the actual file; its name is only a label.
status:         real
aliases:        index node, file record
seeAlso:        filesystem(7), path(7), mount-point(7), file-descriptor(7),
                permissions(7), df(1), cross-view-detection(7)
reading:        inode(7); stat(1); ln(1); lsof(8)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  filesystem(7), path(7)
hook:           `mv <item> <tier>` in the storage window (../client/04
                §3.10) — an operation that looks like a rename and is
                not, because the tiers are separate filesystems; and
                `stat`, which prints the record itself.
misconception:  commonly believed a file's name is the file, so deleting
                the name destroys the data; actually the name is a link
                to a numbered record, deleting removes the link, and the
                data survives for as long as any other link or any
                program's open handle still refers to it — which is why a
                deleted log file can keep a disk full and why `df` and
                `du` disagree.
transfer:       On macOS or Linux: `ls -li` shows each file's inode
                number in the first column. Then `echo hi > a; ln a b;
                ls -li a b` — two names, one number, one file. Delete `a`
                and `b` still holds the data. On Linux, `lsof +L1` lists
                files that have been deleted and are still open, which is
                the classic answer to "the disk is full and I cannot find
                what is using it". Assumes a Unix shell — see ED-8.
verified:       An inode holds the metadata and block pointers, and the
                name lives in the directory — inode(7); link count and
                deletion semantics, data freed only when link count is
                zero *and* no descriptor is open — unlink(2), inode(7);
                inode numbers are unique per filesystem, which is why
                hard links cannot cross one — ln(1), inode(7); `mv`
                across filesystems is copy-then-unlink — mv(1) (GNU
                coreutils). Checked 2026-07-25.

## DESCRIPTION

Moving an item between storage tiers changes its exposure, and that is
not a game rule bolted onto a rename. The three tiers are three separate
filesystems, and moving a file between two filesystems is not a rename in
any system — it is a copy followed by a delete. The item genuinely leaves
one place and arrives in another, which is exactly why the risk changes.

Underneath every name is a record — an inode — holding everything about
the file except its name: its size, its owner, its permission bits, its
timestamps, and where its data actually sits on the device. The name
lives in a directory and points at that record's number. The record does
not know its own name, and does not need to.

## REAL-WORLD COUNTERPART

real — inodes, on every Unix-like filesystem.

`ls -li` prints the number. Two names can share one: `ln a b` makes a
hard link, and afterwards `a` and `b` are equally the file. There is no
original — the record simply counts how many names refer to it.

Deleting is therefore not deleting. `rm` removes a name and decrements
the count; the data is released only when the count reaches zero *and*
no running program still has the file open. This is the reason for one
of the most common and most confusing situations in system
administration: a log file is deleted, the disk stays full, and nothing
in the directory tree accounts for the space. The process that was
writing it still holds it open, so the blocks are still allocated and no
name refers to them. `df` reports the space as used; `du`, which walks
names, cannot see it. On Linux, `lsof +L1` finds them.

Inode numbers are unique within a filesystem and meaningless outside it,
which is why a hard link cannot cross one — and why `mv` between two
mount points falls back to copying the data and unlinking the original,
where `mv` inside one is instantaneous whatever the file's size.
```

---

### 3.12 `permissions(7)`

```
id:             permissions
section:        7
name:           permissions
canonical:      permissions
gloss:          The recorded rules for who may read, change or run a thing.
status:         real
aliases:        file mode, mode bits, chmod, rwx
seeAlso:        privilege(7), filesystem(7), inode(7), process(7),
                storage-tiers(7), vault(7), package-manager(7)
reading:        chmod(1); inode(7) — the file mode bits; umask(2);
                credentials(7)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  filesystem(7), process(7)
hook:           The storage window's exposure column
                (../client/00-client-overview.md §6.1), and the Encrypted
                Vault's caveat that "anything running as you can read it"
                (../client/04 §4.9).
misconception:  commonly believed permissions are about people — that
                "only I can open my files" means a person is being
                checked; actually the check is against the account a
                *program* is running as, so every tool you start inherits
                everything you are allowed to do, and a hostile one you
                run has precisely your access. Nothing asks you per file.
transfer:       On macOS or Linux, run `ls -l` and read the first ten
                characters of a line: a type character, then three groups
                of rwx for owner, group and everyone else. Then
                `stat -c '%a %U %G %n' <file>` on Linux (`stat -f '%Lp
                %Su %Sg %N' <file>` on macOS) prints the same thing in
                octal. `chmod 600 <file>` makes a file readable only by
                its owner. Assumes a Unix shell — see ED-8.
verified:       Twelve mode bits — nine permission bits plus setuid,
                setgid and sticky — inode(7); octal notation, 0644, 0755,
                4755 — chmod(1); the check is against the process's
                effective user and group ids, not against a human —
                credentials(7), path_resolution(7); default umask 022 on
                most systems — umask(2). Checked 2026-07-25.

## DESCRIPTION

Your storage tiers differ in who can reach what is in them, and the
storage window shows that as an exposure column. Underneath, on any
Unix-like system including uOS, reachability is recorded per item, in a
handful of bits attached to the item's own record.

The model is small enough to hold in your head. Three permissions —
read, write, execute — times three audiences — the owner, a named group,
and everybody else. Nine bits. The check happens when something is
opened, against the account the *opening program* is running as, and
never against a person.

That is the sentence that matters for this game. A tool you field runs as
you. A bot you build runs as you. If something hostile is running as you,
permissions do not help, because permissions were never designed to
distinguish between two programs with the same owner. This is exactly why
the Encrypted Vault's protection is described the way it is: encryption
protects an item nobody has unlocked, and permissions protect it from
other accounts — neither protects it from a program running as you.

## REAL-WORLD COUNTERPART

real — the Unix file mode, unchanged in shape since the 1970s.

`ls -l` shows it as ten characters: a type, then rwx three times.
`-rw-r--r--` is a regular file the owner can read and write and everyone
else can only read. The same thing in octal is 644, because each group of
three bits is one octal digit: read is 4, write is 2, execute is 1.
Directories reuse the letters differently — execute on a directory means
"may enter", not "may run".

There are twelve bits, not nine. The extra three are setuid, setgid and
the sticky bit. Setuid is the interesting one: a program with it set runs
as its *owner* rather than as whoever started it, which is how an
ordinary user can change their own password in a file only the
administrator may write. It is also, for the same reason, one of the
oldest and most productive sources of privilege-escalation bugs in the
history of the field. See privilege(7).

New files do not get their permissions from nowhere. A per-session mask —
the umask, usually 022 — clears bits from whatever a program requests,
which is why files you create are typically 644 and not 666.
```

---

### 3.13 `file-descriptor(7)`

```
id:             file-descriptor
section:        7
name:           file descriptor
canonical:      file descriptor
gloss:          A small number a program uses to refer to something it opened.
status:         real
aliases:        fd, handle
seeAlso:        socket(7), pipe(7), process(7), system-call(7),
                inode(7), filesystem(7), bandwidth(7)
reading:        open(2); proc(5) — /proc/<pid>/fd; getrlimit(2);
                lsof(8); ioctl(2)
notes:          The game's Bandwidth rig stat is a homonym: it sounds
                like bits per second and is actually a concurrency cap,
                whose honest counterpart is a descriptor limit
                (../client/04 §2.15). The disambiguation is mandatory on
                both pages.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  process(7), filesystem(7), system-call(7)
hook:           The Bandwidth rig stat, which caps how many engagements
                you can hold open at once (../client/04 §2.9, §2.15), and
                the audit window's connection table, where every entry
                has an owning process because every connection is one of
                these.
misconception:  commonly believed a program refers to an open file by its
                name, so renaming or deleting a file while a program is
                using it will confuse or break it; actually the name is
                consulted exactly once, at open, and everything after
                that goes through a small integer — which is why you can
                delete a file that is being written to and the writing
                carries on perfectly happily.
transfer:       On Linux, `ls -l /proc/self/fd` lists the descriptors of
                the command you just ran: 0, 1 and 2 are standard input,
                output and errors, pointing at your terminal. `ulimit -n`
                prints how many you are allowed at once — commonly 1024.
                On macOS, `lsof -p $$` shows the same for your shell.
                Assumes a Unix shell — see ED-8.
verified:       Descriptors 0, 1, 2 are standard input, output and error
                — stdin(3), open(2); the descriptor is an index into a
                per-process table and the path is resolved once at open
                — open(2), path_resolution(7); systemd sets a default
                soft limit of 1024 open files with a hard limit of
                524288 (systemd 240+) — systemd system.conf
                documentation; ioctl(2) exists precisely because the file
                interface is insufficient for many devices — ioctl(2).
                Checked 2026-07-25.

## DESCRIPTION

Every connection in your audit window has an owning process, and that is
not a display convenience. It is structural: a connection *is* something
a process is holding open, and the thing it holds is a descriptor.

When a process opens anything — an item in storage, a connection to
another node, a channel to another process — uOS hands back a small
integer. From that moment the name is irrelevant; the process reads and
writes by number. The number is meaningful only inside that one process,
which is why two processes can both hold descriptor 4 and mean entirely
different things.

Three of them are always there, handed down from whatever started the
process: 0 for input, 1 for output, 2 for errors. That is the whole
reason `ps | grep miner` works — see pipe(7).

## REAL-WORLD COUNTERPART

real — file descriptors, on every Unix-like system.

The phrase "everything is a file" is the shorthand for this: files,
directories, terminals, network sockets, pipes and many devices are all
reached through the same three system calls — read, write, close — no
matter what is on the other end. It is genuinely one of the best design
decisions in computing, because it means a program that copies bytes does
not need to know whether it is copying from a disk, a keyboard or a
network.

There is a limit, and it is low enough to hit. A process may hold only so
many at once; the common default under systemd is 1,024, with a hard
ceiling of 524,288 available if a program asks. "Too many open files" is
one of the most frequent failure modes in server software, and it is
almost always a program that opened without closing.

On Linux `ls -l /proc/self/fd` shows them, and `lsof` shows them for any
process on the machine.

## CAVEATS

"Everything is a file" is a slogan with real exceptions. Network
interfaces on Linux are not files. Processes are not files, although
`/proc` presents information about them as if they were. And `ioctl` —
a system call whose entire purpose is "do the thing this specific device
needs that read and write cannot express" — exists precisely because the
file interface was not enough. Plan 9 pushed the idea considerably
further than Unix did, which is the clearest evidence that Unix stopped
short.

The game's **Bandwidth** rig stat is not this, and is not bits per
second either. It caps how many engagements you can hold at once, which
in a real system is a descriptor limit, a worker-pool size or a
connection cap — `ulimit -n`, not a data rate.
```

---

### 3.14 `socket(7)`

```
id:             socket
section:        7
name:           socket
canonical:      socket
gloss:          One endpoint of a network conversation, held open by a program.
status:         real
seeAlso:        file-descriptor(7), process(7), port(7), packet(7),
                cross-view-detection(7), rootkit(7), ss(1), daemon(7)
reading:        socket(2); unix(7); ss(8); lsof(8); RFC 9293 §3.1
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  file-descriptor(7), process(7)
hook:           The audit window's connection table
                (../client/00-client-overview.md §6.1), and specifically
                ../design/04-mining.md §3.1's "a connection with no
                owning process" — the discovery the whole manual-audit
                mechanic is built around.
misconception:  commonly believed a connection belongs to the machine, or
                to a port, so two programs cannot both be talking on port
                443; actually a connection belongs to a process, is held
                as a descriptor, and is identified by four values — both
                addresses and both ports — which is how one server holds
                fifty thousand simultaneous connections on a single port.
transfer:       On Linux, `ss -tanp` lists TCP sockets with the process
                holding each (attribution for other users' processes
                needs privilege). On macOS, `lsof -i -P -n`. On any of
                them, `netstat -an` still works and prints less. The
                player can now look at their own machine and say which
                program each connection belongs to. Assumes a Unix shell
                — see ED-8.
verified:       A connection is identified by the 4-tuple of source
                address, source port, destination address, destination
                port — RFC 9293 §3.1 and socket(2); a socket is a file
                descriptor and is read and written like one — socket(2),
                unix(7); `ss` and `lsof` require privilege to attribute
                sockets owned by other users — ss(8), lsof(8).
                Checked 2026-07-25.

## DESCRIPTION

Your audit window lists connections, and every one of them should have a
process next to it. When one does not, you have found something.

A socket is one end of a conversation, held open by a process exactly the
way an item in storage is held open — as a descriptor. That is the whole
point of it: a bot reading from a connection uses the same operations it
would use to read from storage. The connection is not floating in the
machine somewhere. Something owns it, and if you cannot see what, either
you are not allowed to know or something is hiding.

Two views of this are what makes manual auditing work. The process table
says what is running. The connection table says what is talking. A
deployed miner has to do both — it cannot mine without running and cannot
report without talking — so it must appear in both lists, unless
something is editing one of them. See cross-view-detection(7).

## REAL-WORLD COUNTERPART

real — sockets, exactly as any networked machine has them.

A TCP connection is identified by four values: your address, your port,
their address, their port. All four. This is why a web server can hold
tens of thousands of simultaneous connections while listening on one port
— the port is shared, the four-value combination is unique per
conversation. It is also why "the port is in use" and "the connection is
in use" are entirely different statements.

Sockets come in more than one family. Internet sockets talk over a
network; Unix domain sockets talk between processes on one machine
through a path in the filesystem, and are how a great deal of local
software actually communicates.

On Linux `ss -tanp` lists them with owning processes; `lsof -i` does the
same on macOS. `netstat` still works everywhere and prints an older, less
informative view — the game's `netstat` prints a deprecation note for the
same reason the real one increasingly does.

## CAVEATS

An empty process column is not by itself evidence of anything. On a real
machine, `ss` and `lsof` can only attribute sockets you are entitled to
see: run them without privilege and every socket belonging to another
user appears unowned. That is the single most common false alarm in this
technique, and a rule that ignores it will produce a hundred wrong
answers for every right one.

On your own rig, where everything is yours, an unattributed connection
means what it appears to mean. On somebody else's machine, check what you
were allowed to see before concluding anything.
```

---

### 3.15 `signal(7)`

```
id:             signal
section:        7
name:           signal
canonical:      signal
gloss:          A short, numbered message the system delivers to a running task.
status:         real
aliases:        SIGTERM, SIGKILL, SIGINT
seeAlso:        kill(1), process(7), process-tree(7), kernel(7),
                exit-status(7), daemon(7)
reading:        signal(7); kill(1); kill(2); sysexits.h
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  process(7), kernel(7)
hook:           `kill` — one of the four responses to a discovered
                foreign miner (../design/04-mining.md §5) — and exit
                status 130, which the client reports when a breach is
                aborted (../client/04 §3.5).
misconception:  commonly believed that `kill` destroys a program and that
                `kill -9` is the same thing done harder; actually most
                signals are requests the program is free to catch and
                handle — the default politely asks it to shut down and it
                may decline — while 9 is one of exactly two that cannot
                be caught, blocked or ignored, which is also why it gives
                the program no chance to finish writing anything, close
                anything, or clean up after itself.
transfer:       On macOS or Linux, `kill -l` lists every signal by name
                and number. Then `sleep 100 &` followed by `kill %1`
                sends the default, and the shell reports "Terminated".
                Pressing Ctrl-C in a terminal sends SIGINT, signal 2,
                which is why an aborted command exits with status 130 —
                128 plus 2. Assumes a Unix shell — see ED-8.
verified:       SIGKILL (9) and SIGSTOP (19 on x86 Linux) cannot be
                caught, blocked or ignored — signal(7); SIGINT is 2,
                SIGTERM is 15, SIGHUP is 1, and these low numbers are
                constant across Linux architectures while several higher
                ones are not — signal(7); shells report a signal-
                terminated command as 128 + signal number — bash(1),
                and ../client/04 §3.5. Checked 2026-07-25.

## DESCRIPTION

`kill` is one of the four things you can do with a foreign miner you have
found, and it is the only one of the four whose name is exactly real.
What it does is send a signal.

A signal is a very small message — a number, and nothing else — delivered
to a running process by the kernel. It is not a request over a
connection and it carries no data. It interrupts whatever the process was
doing, and the process either has arranged in advance what to do about
that number or it has not, in which case the system applies a default.

Most defaults are "stop". That is why the command is called kill despite
most signals not being fatal, and it is a name people have regretted for
fifty years.

## REAL-WORLD COUNTERPART

real — signals, and `kill(1)` doing exactly what `kill(1)` does.

The default that `kill` sends is SIGTERM, number 15, and it means "please
shut down". A well-written program catches it, finishes the write it was
in the middle of, closes what it has open, and exits. A program is
entitled to catch it and do nothing at all.

`kill -9` sends SIGKILL, and SIGKILL is one of exactly two signals a
process cannot catch, block or ignore — the other is SIGSTOP, which
suspends. The kernel removes the process without consulting it. This is
why `kill -9` is not the polite version done firmly: the program gets no
opportunity to save state, flush a buffer, or release anything it was
holding, and using it first rather than last is how half-written files
happen.

A few numbers are worth carrying. 1 is SIGHUP, historically "the terminal
went away", now widely repurposed to mean "reload your configuration". 2
is SIGINT, which is what Ctrl-C sends. 15 is SIGTERM. 9 is SIGKILL. Those
four are the same everywhere on Linux; several of the higher numbers
differ between processor architectures, so `kill -l` is the honest way to
look one up.

The exit status convention follows from this. A command killed by signal
N reports 128 + N, which is why an aborted operation reports 130: 128
plus SIGINT's 2.
```

---

### 3.16 `daemon(7)`

```
id:             daemon
section:        7
name:           daemon
canonical:      daemon
gloss:          A program that runs continuously in the background, unattended.
status:         real
aliases:        service, background process
seeAlso:        process(7), init(7), cron(7), container(7), log(7),
                signal(7), ps(1), scheduler(7)
reading:        daemon(7); systemd(1); systemd.service(5); launchd(8);
                MITRE ATT&CK T1543.002 (Systemd Service)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          operating
prerequisites:  process(7)
hook:           The botnet window (../client/00-client-overview.md §6.1)
                — every bot instance is one — plus armed defenses holding
                compute permanently (../design/09-defense-and-hardening.md)
                and the 3-cycle control channel each deployed miner costs
                (../design/04-mining.md §2).
misconception:  commonly believed a program only runs while its window is
                open, so closing the window stops the work; actually most
                of what any computer does has no window at all — the
                clock sync, the print service, the update checker, the
                indexer, the SSH server — and the way to stop one is to
                ask the service manager, not to close something.
transfer:       On Linux, `systemctl list-units --type=service --state=
                running` lists every service currently running, and it
                will be dozens. On macOS, `launchctl list`. Compare
                either count against how many windows are open. Assumes a
                Unix shell — see ED-8.
verified:       Daemons are ordinary processes distinguished by having no
                controlling terminal, conventionally started and
                supervised by the service manager — daemon(7),
                systemd(1); the trailing "d" convention (sshd, crond,
                systemd) — daemon(7); adversaries persist by creating or
                modifying services — MITRE ATT&CK T1543.002.
                Checked 2026-07-25.

## DESCRIPTION

Everything you own that keeps working while you look at something else is
a daemon. Every bot instance. Every armed defense, which is why they hold
compute permanently rather than only when they act. The control channel
for each deployed miner, which is why each one costs you three cycles for
as long as it lives.

A daemon is not a special kind of program. It is an ordinary process with
two properties: nothing is attached to it that a person is typing into,
and something else is responsible for starting it, restarting it if it
dies, and stopping it on request. That something is the service manager —
see init(7). This is why the botnet window's verbs are build and stop
rather than open and close.

The backlog timer is the honest consequence: things that run unattended
still need attending to, and the more of them you run the less attention
each one gets.

## REAL-WORLD COUNTERPART

real — daemons, called services on Windows and increasingly on Linux too.

An ordinary laptop is running dozens. Time synchronisation, printing,
Bluetooth, the software updater, the search indexer, the SSH server on
anything you administer. The naming convention is visible in `ps` output:
a trailing "d" — `sshd`, `crond`, `systemd` — and the word itself comes
from Maxwell's demon, not from anything infernal.

They are supervised rather than launched. On Linux, systemd starts them
from unit files that declare what to run, what must be running first, and
what to do if it exits; `systemctl status <name>` reports on one and
`systemctl list-units --type=service` lists them all. macOS uses launchd
with property lists; Windows has the Service Control Manager.

This is also, predictably, where persistence lives. An attacker who wants
to survive a reboot installs a service, because a service is the
mechanism the system provides for surviving a reboot. MITRE catalogues it
as T1543.002. Which is why an unfamiliar service on a machine you own is
worth a minute of attention, and why listing them is one of the first
things anyone does on a host they have inherited.
```

---

### 3.17 `namespace(7)`

```
id:             namespace
section:        7
name:           namespace
canonical:      namespace
gloss:          A private view of system resources, given to some tasks only.
status:         real
aliases:        isolation, sandbox
seeAlso:        container(7), process(7), kernel(7), virtual-memory(7),
                filesystem(7), mount-point(7), isolated-partition(7)
reading:        namespaces(7); cgroups(7); unshare(1); lsns(8)
notes:          The game's Isolated Partition is a homonym: a real
                "partition" is a slice of a disk, and the game's meaning
                — one workload that cannot be correlated with the others
                — is isolation, whose real vocabulary is namespaces and
                sandboxes (../client/04 §2.9, §2.15). Mandatory
                disambiguation on both pages. Do not translate
                "partition" using the disk word.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  process(7), filesystem(7), kernel(7)
hook:           The Isolated Partition rig upgrade (../client/04 §2.9),
                whose promise is that one workload cannot be correlated
                with the others.
misconception:  commonly believed that isolating a workload means giving
                it a separate machine, or at least a separate disk;
                actually the usual mechanism gives it a separate *view* —
                the same kernel, the same hardware, but its own list of
                processes, its own filesystem tree, its own network stack
                — so two isolated workloads on one machine cannot see
                each other while sharing everything underneath.
transfer:       On Linux, `lsns` lists the namespaces in use on the
                machine and which processes are in each. Where
                unprivileged user namespaces are enabled, `unshare
                --user --pid --fork --mount-proc ps -e` runs `ps` inside
                a fresh process namespace and prints a list two entries
                long — the same machine, a different view. Assumes Linux;
                macOS has no equivalent command. See ED-8.
verified:       Linux provides eight namespace types — cgroup, IPC,
                network, mount, PID, time, user, UTS — namespaces(7); the
                time namespace was added in Linux 5.6 — namespaces(7);
                namespaces isolate the view while cgroups limit the
                resources, and the two are separate mechanisms —
                namespaces(7), cgroups(7). Checked 2026-07-25.

## DESCRIPTION

The Isolated Partition promises that one thing you are running cannot be
tied to the others. On a real system that is not achieved by giving the
work its own machine or its own disk. It is achieved by giving it its own
*view*.

A namespace is a private version of some system-wide resource, handed to
a set of processes. Put a process in its own process namespace and it
sees a machine with two processes on it — itself and whatever it started.
Its own mount namespace and it sees a different filesystem tree. Its own
network namespace and it has its own interfaces, its own routing, its own
sockets. The rest of the machine is still there, still running, still
sharing the same processor and the same kernel. The isolated process
simply cannot see it, and cannot name it.

That is a real boundary and a cheaper one than a separate machine. It is
also a weaker one, and knowing which is which is the useful part.

## REAL-WORLD COUNTERPART

real — namespaces on Linux, and the equivalent sandboxing mechanisms
elsewhere.

Linux has eight kinds: processes, mounts, network, hostname, inter-process
communication, users, control groups and time. They are independent — a
process can have its own network view while sharing everything else — and
they are what containers are built from, along with cgroups, which are a
separate mechanism that limits how much a group of processes may consume
rather than what it may see.

`lsns` lists them. `unshare` creates them from the command line, which
means the whole mechanism can be demonstrated in one command without
installing anything.

## CAVEATS

The game's **Isolated Partition** is not a partition. A partition, in
ordinary usage, is a slice of a storage device — a real thing, a
different thing, and one this upgrade has nothing to do with. What the
upgrade describes is isolation, and the real words for it are namespaces,
sandboxes and containers.

The boundary is not as strong as a separate machine. Everything inside a
namespace runs on the host's kernel, so a flaw in the kernel is a flaw
that crosses the boundary, and container escapes are a real and
well-populated category of vulnerability. A virtual machine, which brings
its own kernel and is separated by the processor's own virtualisation
support, is the stronger boundary — and costs correspondingly more.
```

---

### 3.18 `container(7)`

```
id:             container
section:        7
name:           container
canonical:      container
gloss:          One running copy of a packaged program, isolated from the rest.
status:         real
aliases:        image, instance
seeAlso:        namespace(7), process(7), filesystem(7), daemon(7),
                init(7), virtual-memory(7), package-manager(7)
reading:        namespaces(7); cgroups(7); the Open Container Initiative
                image specification; systemd-detect-virt(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  process(7), namespace(7), filesystem(7)
hook:           Bot **frames** versus **instances**
                (../design/10-botnets.md; ../client/04 §2.11), and
                Invariant I11 — loss destroys instances and socketed
                tools, never blueprints.
misconception:  commonly believed a container is a small virtual machine
                with a stripped-down operating system inside it; actually
                it runs directly on the host's own kernel and has no
                operating system of its own at all — what it has is a
                private view and a capped share — which is why containers
                start in milliseconds where a virtual machine takes
                seconds, and why a kernel flaw is a container's weakest
                point and a virtual machine's least likely one.
transfer:       Where Docker or Podman is installed, `docker images` and
                `docker ps` are literally this distinction: the first
                lists blueprints, the second lists running copies, and
                one blueprint can appear in the second list many times.
                Where neither is installed, the same distinction is
                visible without any of it: `/usr/bin/bash` is one file
                and `ps -e | grep -c bash` counts how many copies of it
                are running. Assumes a Unix shell — see ED-8.
verified:       Containers share the host kernel and are built from
                namespaces plus cgroups — namespaces(7), cgroups(7); an
                image is a read-only layered filesystem and a container
                is a running instance of one — Open Container Initiative
                image specification; virtual machines run their own
                kernel under hardware virtualisation, which is the source
                of the startup-time and isolation-strength difference —
                standard virtualisation documentation.
                Checked 2026-07-25.

## DESCRIPTION

A bot frame is a blueprint. A bot instance is a running copy of it. Lose
the instance and you lose the instance and whatever was socketed into it;
the frame is untouched, and you build another (Invariant I11).

That is not a game convention. It is the single most useful structural
idea in modern software operations, and it is the same distinction as
program and process one level up: the frame is a file that describes what
to run, the instance is the thing running. One frame, many instances,
each with its own state, each destroyable without touching the
description it came from.

The reason the design can afford to destroy instances freely is the
reason real operations can: nothing irreplaceable lives in a running
copy. Everything irreplaceable lives in the blueprint.

## REAL-WORLD COUNTERPART

real — container images and containers, and the same shape in several
other places.

An image is a packaged, read-only filesystem plus a note about what to
run. A container is one running instance of an image. `docker images`
lists the first, `docker ps` lists the second, and running the same image
five times gives five containers that cannot see each other. Deleting a
container does not touch the image. This is exactly frame and instance.

A container is not a small virtual machine. It shares the host's kernel
and is built entirely from namespaces (a private view — see namespace(7))
and cgroups (a capped share of processor, memory and I/O). There is no
second operating system inside it. That is why it starts in milliseconds,
why a hundred of them fit on one host, and why the kernel is the
boundary's weak point. A virtual machine, by contrast, runs its own
kernel on hardware virtualisation support, starts in seconds, and is the
stronger boundary for that reason.

The same blueprint-and-instance shape appears as a systemd unit file
versus a running service, and as a class versus an object. Recognising it
once means recognising it everywhere.

## CAVEATS

The game's rule that a lost instance also destroys the tools socketed
into it has no real counterpart. Destroying a container destroys its
temporary state; anything that mattered was on a mounted volume, in an
image, or in a database, precisely so that killing the container is
routine rather than costly. The game's version makes loss hurt on
purpose. Real operations spend considerable effort making sure it does
not.
```

---

### 3.19 `rootkit(7)`

```
id:             rootkit
section:        7
name:           rootkit
canonical:      rootkit
gloss:          Software that changes what a machine reports about itself.
status:         real
seeAlso:        cross-view-detection(7), scan(8), system-call(7),
                kernel(7), kernel-module(7), process(7), boot(7),
                rootkit-wrapper(8)
reading:        MITRE ATT&CK T1014 (Rootkit); ld.so(8) — LD_PRELOAD;
                Microsoft Sysinternals RootkitRevealer documentation;
                chkrootkit(8), rkhunter(8)
notes:          ../client/04 §2.15 lists rootkit with "no disambiguation
                needed", flagged only so that nobody softens it. The
                word means what it means; do not translate it into a
                generic term for malware.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  system-call(7), process(7), kernel(7)
hook:           The Rootkit Wrapper (../design/09-defense-and-hardening.md;
                ../client/04 §2.8), which hides a deployed miner from
                routine scans but not from a deliberate audit.
misconception:  commonly believed a rootkit is a kind of virus — a
                particular malicious program that does damage; actually
                it is a capability rather than a payload: code whose only
                function is to change what the machine reports about
                itself, so that something else stays invisible. The
                hiding is the entire product, and the thing hidden is
                usually mundane — a miner, most often of all.
transfer:       Do not build one; this is an entry about detection. On
                Linux, `man ld.so` and read the LD_PRELOAD section, then
                run `LD_PRELOAD=/nonexistent ls`. The loader complains
                that it could not preload the library and runs `ls`
                anyway — which demonstrates, safely, that library calls
                can be intercepted before they reach the real
                implementation. That interposition is the whole mechanism
                of a userland rootkit. Assumes a Unix shell — see ED-8.
verified:       Rootkits may reside at user level, kernel level, in a
                hypervisor, in the boot record or in firmware, and work
                by hooking and modifying the API calls that supply system
                information — MITRE ATT&CK T1014; LD_PRELOAD interposes a
                library ahead of the ordinary ones — ld.so(8); RootkitRevealer
                detected rootkits by comparing a high-level API view with
                a raw scan of the underlying storage — Microsoft
                Sysinternals documentation (tool is discontinued and
                Windows XP-era). Checked 2026-07-25.

## DESCRIPTION

The Rootkit Wrapper does not make a deployed miner stop consuming your
compute. It cannot: a miner that stops mining is not a miner. What it
does is make the miner absent from routine listings — the machine is
asked what is running and does not mention it.

That is what a rootkit is, precisely and generally: something that
changes the answers a machine gives about itself. Not a payload. A
capability, applied to whatever needs hiding.

And it is exactly why the design says the Wrapper survives a scan and
does not survive a deliberate audit, and why `../design/04-mining.md`
§3.1 says the discrepancy is *always* present in the data. Hiding edits
answers; it does not edit facts. The cycles are still gone. The
connection still exists. Every one of those is a separate route to the
same truth, and hiding from all of them at once is much harder than
hiding from the one a scan happens to use. See
cross-view-detection(7).

## REAL-WORLD COUNTERPART

real — rootkits, catalogued by MITRE as technique T1014.

They exist at every level, and the level determines what can still catch
them. A userland rootkit replaces library functions — on Linux, the
LD_PRELOAD mechanism loads a library ahead of the standard ones, so a
program that asks for a directory listing gets an edited answer without
knowing it. A kernel rootkit loads code into the kernel itself, at which
point every question routed through that kernel is answerable however the
rootkit prefers. Below that are boot-record and firmware implants, which
survive reinstalling the operating system entirely — LoJax in 2018 and
BlackLotus in 2023 are the named ones.

The defender's answer has been the same since the early 2000s and it is
the reason this game's mechanic is honest: enumerate the same thing by
two routes that the rootkit would have to corrupt separately, and compare
the answers. Sysinternals' RootkitRevealer did it on Windows by comparing
what the Windows API reported against a raw read of the filesystem and
registry; anything visible in the raw scan and absent from the API view
was, by construction, being hidden. The tool is long discontinued and the
technique is not.
```

---

### 3.20 `cross-view-detection(7)`

```
id:             cross-view-detection
section:        7
name:           cross-view detection
canonical:      cross-view detection
gloss:          Asking two tools the same question and comparing the answers.
status:         real
aliases:        cross-view diff, differential enumeration
seeAlso:        rootkit(7), ps(1), socket(7), df(1), scan(8),
                process(7), inode(7), file-descriptor(7)
reading:        Microsoft Sysinternals RootkitRevealer documentation;
                chkrootkit(8); rkhunter(8); lsof(8); MITRE ATT&CK T1014
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          investigating
prerequisites:  rootkit(7), ps(1), socket(7)
hook:           ../design/04-mining.md §3.1 — manual investigation costs
                zero compute and finds anything, "because the discrepancy
                is always present in the data": cycle totals that do not
                add up, a connection with no owning process, storage
                deltas.
misconception:  commonly believed that if a good scanner reports nothing,
                nothing is there; actually a scanner asks the machine a
                question and a compromised machine answers it, so a clean
                result from a single tool is evidence about the tool as
                much as about the machine — which is why the reliable
                move is to ask the same question by two routes a liar
                would have to corrupt separately.
transfer:       On macOS or Linux, run `df -h /` and then
                `du -sh / 2>/dev/null`. They will disagree. Both are
                reporting how much of one filesystem is used: `df` asks
                the filesystem, `du` walks the files and adds them up.
                The gap is real and its usual causes — reserved blocks,
                files deleted while still open, subtrees you were not
                permitted to read — are exactly the reasons two views of
                one machine differ in general. Assumes a Unix shell —
                see ED-8.
verified:       Cross-view detection by comparing a high-level API view
                against a raw scan — Microsoft Sysinternals
                RootkitRevealer documentation; `df` reports filesystem
                accounting while `du` sums files it can traverse, and the
                two differ for deleted-but-open files and unreadable
                subtrees — df(1), du(1), lsof(8); hooking API calls to
                remove an adversary's presence from listings — MITRE
                ATT&CK T1014. Checked 2026-07-25.

## DESCRIPTION

This is the skill the game is built around, and it costs no compute
(../design/04-mining.md §3.1).

Your rig monitor reports one compute total. Your process list reports
what each consumer holds. Add up the second and compare it with the
first. Your connection table lists conversations; your process list lists
what could be having them. Match one against the other. Your storage
capacity reports one figure; the items you can see account for some
amount. Compare.

When two views of one machine disagree, the disagreement is the finding.
Not the miner — you may never see the miner. The gap.

This works against hiding for a structural reason. Something concealing
itself has to edit an answer, and every answer it fails to edit is a
route to the truth. It is easy to be missing from one list. It is very
hard to be missing from a list, absent from a total, and consistent with
a capacity figure all at once, because those three numbers are produced
by three different paths through the system.

## REAL-WORLD COUNTERPART

real — cross-view detection, the standard technique against rootkits and
the reason they are catchable at all.

Sysinternals' RootkitRevealer, on Windows in the mid-2000s, compared what
the Windows API reported about the filesystem and registry against a raw
read of the same storage. Anything present in the raw scan and missing
from the API view was hidden by definition. `chkrootkit` and `rkhunter`
do a version of this on Unix, and the `unhide` tool goes further,
comparing the process listing against a brute-force probe of every
possible process id.

The discipline that makes it usable is knowing that two views must be
genuinely independent. Two tools that both read `/proc` are not two
views; a rootkit that has edited `/proc` has edited both. Choosing the
pair is the skill.

The second discipline is accepting that most discrepancies are innocent.
`df` and `du` disagree on a healthy machine, every time, for perfectly
ordinary reasons. A discrepancy is a question, not a verdict, and an
investigator who treats every gap as an intrusion will stop
investigating within a week.
```

---

## 4. What this domain deliberately does not teach

`00-curriculum-and-method.md` §7.3 is the rule: **an entry with no hook is not written**, however interesting, because the delivery mechanism is contextual and a concept with no surface has no trigger. An unused entry is an unread entry, and it costs writing, review, translation and index noise to teach nobody.

### 4.1 Folded into another entry rather than given one of their own

Each of these is taught — it just does not get a page, because on its own it would fail `00-curriculum-and-method.md` §6.4's reachability test or R6's one-concept rule.

| Concept | Folded into | Why |
|---|---|---|
| Context switching and its cost | `scheduler(7)` | It is the mechanism of time slicing, not a separate idea, and its number (~1–2 µs direct) lands better inside the explanation of why switching cannot be free |
| Paging, the MMU, address spaces | `virtual-memory(7)` | One mechanism described three ways. Splitting it would produce three pages that each need the other two |
| Swap, thrashing, the OOM killer | `memory-pressure(7)` | All three are "what happens when there is no room", in escalating order |
| Syslog severities, journald, `dmesg` | `log(7)` | RFC 5424's eight levels are a number inside the explanation of what a log line is, not a topic |
| The Filesystem Hierarchy Standard, `/etc`, `/var`, `/home` | `path(7)` | Best taught by contrast — uOS's tree is deliberately not the FHS, which is what makes the FHS visible |
| Zombies and orphans | `process-tree(7)` | They are what parentage is *for*: an orphan is reparented, a zombie is a record kept until its parent reads it |
| Device drivers | `kernel-module(7)` | A driver is the commonest thing a module is |
| `setuid` | `permissions(7)` and `privilege(7)` | The bit belongs with the mode bits; the consequence belongs with privilege escalation |
| "Everything is a file" | `file-descriptor(7)` | The slogan is the descriptor idea stated as a slogan, and it needs its exceptions in the same breath |
| Hardware threads vs. software threads | `thread(7)` | The disambiguation *is* the entry's misconception |

### 4.2 Not taught at all, and why

- **Environment variables.** `../client/04` §3.1 rule 4 bans expansion in the client, deliberately, so that a player's OS username can never leak into a screenshotted game surface. There is therefore no surface. If **T-9** ever adds a game namespace of variables, this becomes a first-rate entry — variables and expansion are core shell literacy — and until then it has no hook.
- **Redirection (`>`, `>>`, `<`).** A parse error by design (`../client/04` §3.1 rule 5), with a specific message that names why. Teaching a feature the game refuses to have would be teaching the player to type something that fails.
- **Job control (`fg`, `bg`, `Ctrl-Z`).** The game's `jobs` is the botnet window, not a shell job table; `daemon(7)` covers what the player actually has. Shell job control belongs to the command-line domain if it belongs anywhere.
- **How to write a program against any of this.** `00-curriculum-and-method.md` §5.3 is explicit: section 2 is tempting and wrong. The player learns that system calls exist and what they cost, not how to invoke one. There is no programming surface in this game, and pretending otherwise would teach the man-page section system incorrectly.
- **Filesystem internals** — journalling, copy-on-write, extents, B-trees, RAID. No surface, and the parts that matter to a player (a directory is a list, a name is not the file) are in `filesystem(7)` and `inode(7)` already.
- **Kernel internals** — locking, RCU, memory reclaim, scheduler tuning. Genuinely fascinating and entirely invisible from every window in this client.
- **How to build, install or deploy a rootkit.** `rootkit(7)` is written from the defender's side, and its transfer test is deliberately a demonstration of interposition rather than an implementation of it. **ED-9** owes this doc set a stated dual-use line; until it exists, this domain's line is: *explain what a technique is and how it is caught; never how it is built.*
- **Windows-specific architecture** — the registry, services.msc, the NT object manager. Named where it clarifies (`daemon(7)` says services, `cross-view-detection(7)` cites RootkitRevealer) and never taught as a system, because uOS is Unix-like and a second architecture would double the vocabulary for no gain. This collides with **ED-8**, since most players are on Windows; see **OS-9**.
- **Unix history and the POSIX standards process.** One sentence in `uos(7)` establishes that "UNIX" is a certification rather than a style. Beyond that it is trivia with no consequence, and `00-curriculum-and-method.md` §2.3 rules that a fact with no consequence is trivia.

---

## 5. Open questions

Prefix **`OS-`**, which is unused across the doc set: `../design/15-open-questions.md` records `OQ-`/`P-`/`D-`/`S-`/`N-`/`E-`/`A-`/`G-`/`W-`/`Q-`, the client set records `CL-`, `V-`, `PN-`, `SK-`, `T-`, `WL-`, `RI-`, `AX-`, and `00-curriculum-and-method.md` records `ED-`. Log these in `../design/15-open-questions.md` §2 if this doc set is adopted.

- **OS-1: ✅ RESOLVED with ED-3 (2026-07-25) — this file's number and the contract's now agree at `03`.** Entries carry `domain: 03`, no `domain:` value needed re-stamping, and **R8** holds: every cross-domain prerequisite here points downward into foundations and computer architecture, and the command line, networking, cryptography and distributed systems appear only in `seeAlso`. One consequence worth carrying forward: the command line is now `04`, immediately above this document, which is the numbering §2.3's cession of `shell(7)`, `glob(7)`, `quoting(7)`, `exit-status(7)` and `man(1)` always assumed.
- **OS-2: the external prerequisite ids in §2.3 are this document's guess at what the neighbouring domains will call them.** `compute(7)` is safe — it is used by the method document's own worked entry and by `../client/04` §4.9. `memory-hierarchy(7)` and `memory-buffer(7)` are inferred from `00-curriculum-and-method.md` §1.4's description of domain 01, and `ss(1)`/`port(7)` from its description of networking. `00-curriculum-and-method.md` §6.4 check 1 requires every prerequisite reference to resolve; this needs one reconciliation pass once all six domains exist, before any term file is written.
- **OS-3: who owns the OS-state command pages?** This document claims `ps(1)`, `kill(1)`, `df(1)` and `scan(8)`, on the ground that a command page's teaching payload is the concept it exposes and the command-line domain owns the grammar rather than the catalogue. That is defensible and it is not obvious, and the command-line domain may reasonably have claimed the same four. Two entries for one concept is the one thing `00-curriculum-and-method.md` §1.4 forbids outright. **Reconcile before writing.** ⚠ Related: `ss` and `netstat` are claimed by neither this document nor, presumably, cleanly by networking — `socket(7)` is here, the tool is theirs, and the tool is half of the game's flagship mechanic.
- **OS-4: `00-curriculum-and-method.md` §6.2's stage budgets cannot hold this domain.** Seventeen `operating` entries here against a global figure of ~25–40 for that stage leaves eight to twenty-three for the other five domains combined. Either the budgets are per-stage planning figures that need raising (§6.2 already calls the totals "a planning figure, not a quota"), or several entries here move to `investigating`. Recommendation: raise the budget rather than restage, because the entries most likely to be pushed later — `filesystem(7)`, `permissions(7)`, `signal(7)` — are needed the first time a player opens the storage window, and R3 places a concept where the player first needs it.
- **OS-5: does the audit window's process table show parentage?** `process-tree(7)` is in the inventory with a hook marked ⚠ contingent, because `../design/04-mining.md` §3.1 requires the process view to be real, consistent data but does not enumerate its columns. Parentage is one of the genuinely useful cross-checks against a hidden process, and it is free if the data model already has it. **Request to `../client/05-tool-windows-and-layout.md`:** if the table shows a parent column, this entry is written; if not, it is not, and zombies and orphans go untaught.
- **OS-6: `uOS` has no term page today, and `../client/04` §4.10's coverage check requires one.** The check asserts that every canonical term in `../design/glossary.md` has a term file whose `canonical:` matches byte for byte; **uOS** is in the glossary and would fail. §3.3 above claims it as `uos(7)` with `status: game`. Confirm the status choice in particular — labelling the operating system the entire game runs on as fiction reads oddly at first glance and is, on the §4.2 procedure, plainly correct: the name is added by the world, and everything Unix-like about it is inherited and is `real` on its own pages.
- **OS-7: does uOS have more than one user account?** `permissions(7)` and `privilege(7)` teach the owner/group/others model and the meaning of root, and the game surfaces exactly one identity. Both entries are written to stay true either way, but a player who reads about groups and never sees one has learned something inert. Either uOS is single-user by design — in which case the pages should say so in a line, and the exposure model is about *processes* rather than *people*, which is the more interesting lesson anyway — or the design should say who else is on the rig.
- **OS-8: the game's command sections diverge from the real ones for `ss` and `netstat`.** `../client/04` §3.10 places both in section 1; the real ones are `ss(8)` and `netstat(8)`. `00-curriculum-and-method.md` §5.1 says the numbering teaches something on its own, and a player who types `man 8 ss` on a real Linux box after learning `ss(1)` here meets a small, avoidable contradiction. Small, but this doc set's whole claim is that the small things are right.
- **OS-9: this domain is the worst affected by ED-8, and it is not close.** Nearly every transfer test here is a Unix command, because nearly every concept here is surfaced by a Unix command. Option (c) in ED-8 — target what is universal — barely helps: Task Manager shows processes, and nothing in Windows shows an inode, a file descriptor or a namespace. The interim rule is followed throughout (every transfer names its platform), and one entry, `namespace(7)`, is Linux-only with no macOS equivalent at all and says so. **Decide ED-8 before these eighteen entries become term files**, because a third of them are worded around the assumption.
- **OS-10: ED-5 — should `misconception` be rendered to the player? — has its strongest evidence here.** Fifteen of this document's eighteen entries exist primarily to dislodge a specific false belief, and several of those beliefs (`kill -9` is the strong kill; virtual memory is the swap file; a container is a small virtual machine; permissions check people) are held confidently by working professionals. Hiding the best content in the template from the reader is a real cost. The counter-argument in ED-5 stands — a page that opens by telling an adult what they believe wrongly can read as condescending — and the shape that resolves it may be that the correction belongs *inside* `DESCRIPTION` as a plain statement of the true thing, which is how the entries above are already written. Offered as evidence toward ED-5 rather than as a separate question.
- **OS-11: `process(7)`'s body lives in `00-curriculum-and-method.md` §3.5 and is owned by this document.** It is not restated here, because one entry with two homes drifts by the third edit. Proposal: at ship time the body moves into §3 of this document and §3.5 cites it as "the worked example, written in full in `03-operating-systems.md` §3.x". That keeps the method document's teaching purpose intact and gives the entry one place to be edited. Trivial to do now, annoying to do after both documents have been translated.
