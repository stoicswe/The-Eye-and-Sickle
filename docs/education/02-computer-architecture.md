# 02 — Computer architecture: how the machine actually executes

**Status:** ⚠️ **[PROPOSAL]** — every entry in this document is first-pass curriculum written against the contract in `00-curriculum-and-method.md`. What is *not* proposal here is the obligation: client pillar **C6** (`../client/00-client-overview.md` §2), the stated product goal in `../client/00-client-overview.md` §5, and the falsifiable claim in `../client/04-terminology-and-education.md` §1.1 already commit the game to teaching this material honestly. What this document adds is *which* concepts, in what order, against which false beliefs, verified against which sources. Statuses and mappings are inherited from `../client/04` §2.3, §2.9 and §2.15 and are **not** re-decided here; where this document disagrees with an existing shipped page, §2.6 says which one wins and why.
**Depends on:** `00-curriculum-and-method.md` — §2 (the register), §3 (**the entry template**), §4 (the status vocabulary), §5 (man-section assignment), §6 (stages and sequencing rules R1–R8), §7 (coverage), §8 (the writer's loop); `../client/04-terminology-and-education.md` §1.1 / §1.1a (the claim and why uOS makes it keepable), §2.3 (resources), §2.9 (rig infrastructure), §2.10 (mining), §2.15 (**the homonym table — three rows in this domain**), §4.8 (the content contract), §4.9 (`compute(7)`, already written); `../design/glossary.md` (canonical names, uOS); `../design/01-core-resources.md` §1 (compute, Thermal Budget); `../design/11-rig-infrastructure.md` §2 (the four core rig stats); `../design/04-mining.md` §3.1–3.2 (manual investigation, scan costs); `../client/00-client-overview.md` §3.3 (uOS), §6.1 (the window catalogue), pillar **C6**; `../../CLAUDE.md` (Invariants I1, I2, I6)
**Depended on by:** the operating-systems, networking, security-and-cryptography and distributed-systems domain curricula — every one of them cites entries defined here and none of them may redefine one (`00-curriculum-and-method.md` §1.4); and, through them, `client/src/main/resources/terms/**`

---

## 1. What this domain is, and why this game's player needs it

### 1.1 The domain in one sentence

**How a physical computer gets work done, and what each part of that costs in time, heat and energy.** Not how to program one, not how an operating system arranges the work — the machine underneath both.

### 1.2 Why this domain exists at all: compute is the master scarcity

Every other domain in this curriculum teaches something the player *does*. This one teaches something the player *spends*, in every single minute of play, from the first.

`../design/00-vision-and-pillars.md` makes compute the master scarcity and Invariant **I1** forbids buying it. `../design/01-core-resources.md` §1 gives a starting rig 100 cycles and prices everything against them. `../design/11-rig-infrastructure.md` §2 makes the four rig stats — Compute Cores, Thermal Budget, Bandwidth, Memory Buffer — the shape of the player's whole capability. The rig monitor is on screen from the first minute and is described in the design docs as "the game's most important HUD element" (`../design/01-core-resources.md` §1.4).

A player therefore spends the entire game reading a resource meter. The question this domain answers is whether that meter is **arbitrary or explicable**. If cycles are just a number that goes down, the game has a currency with an odd name. If cycles are capacity on a physical machine that has cores, a clock, a heat limit and a memory hierarchy, then every price in the game is a consequence of something true, and the player who understands the machine can predict the game.

That is the whole argument for putting this domain first, and it has a second half: **it is also the domain where the game is closest to real without anybody having to try.** Three examples, all Established design that happens to be accurate:

| The mechanic | The real thing it already is | Where |
|---|---|---|
| **Thermal Budget** — spent cycles return more slowly the closer the rig sits to capacity | Thermal throttling. A chip that cannot shed heat lowers its own clock, so a machine under sustained load genuinely is slower than the same machine idle | `../design/01-core-resources.md` §1.3, `../design/11-rig-infrastructure.md` §2 |
| **Invariant I6** — a deployed miner consumes the **host's** compute, not the deployer's | The definition of cryptojacking. ATT&CK **T1496 Resource Hijacking**, sub-technique **T1496.001 Compute Hijacking**. The victim pays the electricity and the latency; that is *why* it is worth detecting | `../../CLAUDE.md` I6, `../client/04-terminology-and-education.md` §2.10 |
| **"Cycle totals that don't add up"** — the always-present discrepancy that gives a rootkit-wrapped miner away | Cross-view detection. Enumerate a resource two ways, compare the answers, and the hidden thing is in the gap. The arithmetic only means anything to a player who knows that cycles are a fixed budget that must balance | `../design/04-mining.md` §3.1 |

None of those three were designed as teaching. All three are teachable for the price of prose, which is `../client/04` §1.2's argument in its cheapest form.

### 1.3 The surfaces this domain is anchored to

Per `00-curriculum-and-method.md` §7.1 item 4, every `hook` in this document names a surface that already exists in the design or client docs. They are, in the order a player meets them:

| Surface | Document | What it teaches here |
|---|---|---|
| `rig-monitor` window (Unix analogue `top`) — total, allocated by consumer, available, recovering with time-to-recover | `../client/00-client-overview.md` §6.1, `../design/01-core-resources.md` §1.4 | compute, cores, throttling, thermal budget |
| Published tool costs, and the `-n` / `--dry-run` flag that prints them | `../client/04-terminology-and-education.md` §3.4 | compute as a price; latency vs. throughput |
| The four rig stats in `market` and on the rig | `../design/11-rig-infrastructure.md` §2 | Compute Cores, Thermal Budget, Bandwidth, Memory Buffer — and the three homonyms among them |
| `audit` window (`ps` / `ss` / `df`) — where cycle totals fail to add up | `../design/04-mining.md` §3.1 | why a budget must balance; where a hidden consumer shows |
| `scan --quick\|--full\|--thorough`, at 5 / 15 / 35 compute | `../design/04-mining.md` §3.2 | the Thermal Budget curve; why scanning while overextended hurts |
| `storage` window — three tiers as mount points | `../design/01-core-resources.md` §6 | persistent storage, and how it differs from memory |
| Deployed miners on someone else's rig | `../design/04-mining.md` §2, Invariant I6 | whose compute is being spent, and why that is theft |
| `side-channel-reader(1)` | `../design/06-intrusion-tools.md` | caches, timing, speculative execution |
| Firmware Implant — deployed miners survive a host wipe | `../design/11-rig-infrastructure.md` §3 | firmware, and what "below the operating system" means |

### 1.4 What this domain owes the other five

It is the floor. Under sequencing rule **R8** (`00-curriculum-and-method.md` §6.3) no entry may take a prerequisite from a higher-numbered domain, and this domain is the one that makes R8 satisfiable at all: it can define everything it defines without a single forward reference to an operating system, a network, a cipher or a peer.

Three specific debts it discharges:

- **`compute(7)` is the prerequisite of `process(7)`** in the operating-systems domain (`00-curriculum-and-method.md` §3.5). A process is a thing that holds some of a budget; the budget has to exist first.
- **`bit-width(7)` underwrites every "N bits" claim in the doc set** — a port is 16 bits, an IPv4 address is 32, a SHA-256 output is 256. Those numbers are the curriculum's main instrument for building intuition (`00-curriculum-and-method.md` §2.7) and exactly one entry should have to explain what they mean.
- **`memory-hierarchy(7)` and `cache(7)` underwrite every latency argument anywhere** — why a system call costs a hundred nanoseconds and a network round trip a hundred milliseconds only lands against a scale the reader already has.

> **A caution the other five domains should read.** `bit-width(7)` sits at `operating`, and `port(7)` sits at `first-session`. **`bit-width(7)` must therefore not be listed as a prerequisite of `port(7)`**, or rule R1 breaks and one of the two entries has to move. It does not need to be: a port is a number from 0 to 65535 and can be used without knowing why. `port(7)` states the 16-bit fact inline and cites `bit-width(7)` in `seeAlso`, which is the correct relationship — *explains further*, not *required first*.

---

## 2. The concept inventory

### 2.1 How to read it

Every concept this domain judges worth teaching, whether or not §3 writes it out in full. This table is the **coverage guarantee** (`00-curriculum-and-method.md` §7.1): a reviewer can see the whole domain at once and check that nothing the game leans on is missing.

- **Gloss** is the shipped Tier-1 one-liner: ≤ 72 characters, and it never contains the word it defines.
- **Status** follows `00-curriculum-and-method.md` §4.2's ordered procedure with its downgrade bias. Where `../client/04` §2 has already fixed a status, that value is inherited, not re-argued.
- **Stage** follows §6.2. **Exactly one entry in this domain is `first-session`**, which is deliberate — see §2.4.
- **Prerequisites** are `name(section)` refs. `none` is a real value and eleven entries have it, because this is the bottom of the stack.
- **Full entry** marks the eighteen written out in §3.
- **⇩** marks a concept inventoried here for context but **owned by a lower-numbered domain** and written there. The one case is `bit-width`, which is representation rather than architecture and belongs to `01-foundations.md` §3.7; it had briefly been written in both. This document cites it and must not define it.

All thirty-seven entries are **section 7**. That is not a fallback; see §4.2.

### 2.2 The inventory

**A. The unit, and the machine that has it**

| id | name | gloss (≤ 72 ch) | status | stage | prerequisites | game surface | full? |
|---|---|---|---|---|---|---|---|
| `compute` | compute | The rig's capacity budget, and the scarcity everything else orbits. | real, simplified | first-session | none | `rig-monitor`; every published tool cost; `-n` | **§3.1** |
| `processor` | processor (CPU) | The part that carries out orders; everything else feeds or waits on it. | real | operating | compute(7) | `rig-monitor`; Compute Cores | **§3.2** |
| `bit-width` ⇩ | bit width | How many binary digits a number is given, which fixes its range. | real | operating | none | Every "N bits" figure in the game; `port(7)`'s 65535 | **`01` §3.7** |
| `isa` | instruction set | The vocabulary of orders one family of chips agrees to understand. | real | investigating | instruction(7) | ⚠ weak — `zero-day(1)`'s product-and-version caveat; Firmware Implant | no |

**B. Instructions and time**

| id | name | gloss (≤ 72 ch) | status | stage | prerequisites | game surface | full? |
|---|---|---|---|---|---|---|---|
| `instruction` | machine instruction | One indivisible order a chip can carry out: add, compare, load, store. | real | operating | processor(7) | Tool costs published as fixed numbers | no |
| `clock-speed` | clock speed | How many times a second the chip advances, counted in GHz. | real | operating | processor(7) | `rig-monitor`; the `cycles` homonym | **§3.3** |
| `instruction-cycle` | fetch–decode–execute | The repeating steps that turn a stored order into an effect. | real, simplified | operating | instruction(7), clock-speed(7) | `rig-monitor`; `compute(7)`'s CAVEATS | **§3.4** |
| `pipelining` | pipelining | Starting the next order before the last one has finished. | real, simplified | investigating | instruction-cycle(7) | Why a "cycle" buys more than one order | no |
| `speculative-execution` | speculative execution | Guessing which way a branch goes and doing the work early. | real, simplified | investigating | pipelining(7) | `side-channel-reader(1)` | no |
| `register` | register | The handful of storage slots inside the chip, faster than anything. | real | investigating | processor(7) | `system-call(7)`'s description of a request being handed over | no |
| `latency` | latency | How long one thing takes; not the same as how much fits through. | real | operating | none | Scan durations vs. compute cost (`../design/04-mining.md` §3.2) | no ⚠ contested — §2.6 |

**C. More than one engine**

| id | name | gloss (≤ 72 ch) | status | stage | prerequisites | game surface | full? |
|---|---|---|---|---|---|---|---|
| `core` | core | One complete engine on the chip, able to run work on its own. | real | operating | processor(7) | Compute Cores upgrade | **§3.5** |
| `hardware-thread` | hardware thread | A second queue of work sharing one engine's idle moments. | real, simplified | investigating | core(7) | Compute Cores; why the count is not the capability | **§3.6** |
| `parallelism` | parallelism | Doing several things in the same instant, not taking turns fast. | real | investigating | core(7) | Bandwidth cap; split attention; running several bots | **§3.7** |
| `gpu` | GPU | Thousands of narrow engines doing one operation on masses of data. | real, simplified | investigating | core(7), parallelism(7) | Mining, and why a real miner wants one | no |

**D. Memory**

| id | name | gloss (≤ 72 ch) | status | stage | prerequisites | game surface | full? |
|---|---|---|---|---|---|---|---|
| `memory-hierarchy` | memory hierarchy | Several places to keep the same data, each far slower than the last. | real | operating | processor(7) | `rig-monitor`; `storage`; every latency claim in the game | **§3.8** |
| `cache` | cache | A small fast copy of what was just used, kept beside the engine. | real | operating | memory-hierarchy(7) | Why more cycles does not always mean more done | **§3.9** |
| `cache-line` | cache line | Memory moves in fixed blocks of 64 bytes, never one byte at a time. | real | investigating | cache(7) | `side-channel-reader(1)`; storage read sizes | **§3.10** |
| `locality` | locality | Recently used, and next door to it, are the two cheap cases. | real | investigating | cache-line(7) | Why a Thorough Scan is slow out of proportion to its size | no |
| `cache-miss` | cache miss | The wait when what you asked for was not being held nearby. | real | investigating | cache(7) | `side-channel-reader(1)`; unexplained slowness | no |
| `ram` | RAM | Fast working memory that holds nothing once the power stops. | real | operating | memory-hierarchy(7) | Memory Buffer; `storage` (as the thing it is *not*) | **§3.11** |
| `volatility` | volatile memory | Whether stored data survives losing power; usually it does not. | real, simplified | investigating | ram(7) | Ghost Protocol; what a host wipe does and does not remove | no |
| `von-neumann` | the stored-program model | Orders and data live in the same memory, reached down the same path. | real, simplified | investigating | instruction(7), ram(7) | `overflow-kit(1)`; why the machine can be told to do the wrong thing | **§3.19** |

**E. Moving data, and noticing things**

| id | name | gloss (≤ 72 ch) | status | stage | prerequisites | game surface | full? |
|---|---|---|---|---|---|---|---|
| `bus` | bus | The shared wiring parts use to move data between each other. | real, simplified | investigating | processor(7), ram(7) | Bandwidth (the homonym); `df` throughput | no |
| `io` | I/O | Work done outside the chip, which the chip mostly waits for. | real | operating | processor(7) | Scan durations; `storage`; every network tool | no |
| `interrupt` | interrupt | A signal that stops the chip mid-work to say something happened. | real | investigating | io(7) | Canary Token trips; alert accretion in `defense` | **§3.15** |
| `polling` | polling | Asking repeatedly whether anything happened, and paying each time. | real | investigating | interrupt(7) | Detection Array's permanent compute reservation | no |
| `dma` | direct memory access | A device writing straight into memory without asking the chip. | real | investigating | bus(7), ram(7) | Firmware Implant; why "below the OS" is a real place | no |
| `storage-device` | persistent storage | Where data waits when nothing is powered: slow, large, permanent. | real | operating | memory-hierarchy(7) | `storage` window; `df(1)`; the three tiers | **§3.12** |
| `flash-wear` | flash wear and TRIM | Solid-state cells die after a set number of erasures, so writes cost. | real, simplified | investigating | storage-device(7) | `log-scrubber(1)` — why deleting is not erasing | no |

**F. Heat and power — the real ceilings**

| id | name | gloss (≤ 72 ch) | status | stage | prerequisites | game surface | full? |
|---|---|---|---|---|---|---|---|
| `throttling` | thermal throttling | A chip too hot to run fast slows itself down rather than fail. | real | operating | clock-speed(7) | Thermal Budget; a loaded rig feeling sluggish | **§3.13** |
| `power` | power | Every calculation costs energy, and energy sets the real ceiling. | real | investigating | clock-speed(7) | Deployed mining: the host pays the electricity bill | no |
| `firmware` | firmware | Code that runs before the operating system and outlives reinstalls. | real | adversarial | processor(7) | Firmware Implant (`../design/11-rig-infrastructure.md` §3) | no |

**G. The game's own rig stats — where the fiction and the machine meet**

| id | name | gloss (≤ 72 ch) | status | stage | prerequisites | game surface | full? |
|---|---|---|---|---|---|---|---|
| `thermal-budget` | Thermal Budget | The rig stat governing how fast spent cycles come back. | real, simplified | operating | compute(7), throttling(7) | The stat; scan recovery; `../design/01-core-resources.md` §1.3 | **§3.14** |
| `compute-cores` | Compute Cores | The rig upgrade that raises the cycle ceiling itself. | real, simplified | operating | compute(7), core(7) | The upgrade; Invariant I1's "never purchasable" | no |
| `memory-buffer` | Memory Buffer | The rig stat for how many tools you can have readied at once. | real, simplified | operating | ram(7) | The upgrade; loadout size vs. storage size | **§3.16** ⚠ homonym |
| `bandwidth` | Bandwidth | The rig stat capping how many engagements can run at once. | game | operating | compute(7) | The upgrade; being blocked with cycles still free | **§3.17** ⚠ homonym |

### 2.3 The honesty ledger — status distribution

Required by `00-curriculum-and-method.md` §7.1 item 5, so that the `man` window's status filter has an answer the domain document already agrees with.

| Status | Count | Share |
|---|---|---|
| `real` | 23 | 62 % |
| `real, simplified` | 13 | 35 % |
| `game` | 1 | 3 % |
| **Total** | **37** | |

One `game` entry, and it is `bandwidth(7)` — which exists purely for honesty (`00-curriculum-and-method.md` §4.4), because "Bandwidth" is a real word with a real meaning that this game has attached to a different function. This is the expected shape for this domain: physical machines are the least fictionalised thing in the game, and a domain document here that were half `game` would mean the design had invented physics, which it has not.

Three of the thirty-seven are rows in `../client/04` §2.15's homonym table and therefore carry a **mandatory `notes:` field and a mandatory `## CAVEATS`**: `compute(7)` (cycles ≠ clock cycles — §2.15 calls this "the most consequential one"), `memory-buffer(7)` (not an I/O buffer, nothing to do with buffer overflows), and `bandwidth(7)` (not bits per second).

### 2.4 The dependency spine

Eleven entries have `prerequisites: none` and could each be read cold. The spine that actually carries the domain is short, and every full entry in §3 sits on it:

```
compute(7)                                    ← first-session; the only one
   └── processor(7)
         ├── clock-speed(7) ── throttling(7) ── thermal-budget(7)
         │        └── power(7)
         ├── instruction(7) ── instruction-cycle(7) ── pipelining(7) ── speculative-execution(7)
         │        └── isa(7)
         ├── core(7) ── hardware-thread(7)
         │        ├── parallelism(7) ── gpu(7)
         │        └── compute-cores(7)
         ├── register(7)
         ├── io(7) ── interrupt(7) ── polling(7)
         └── memory-hierarchy(7)
                  ├── cache(7) ── cache-line(7) ── locality(7)
                  │        └── cache-miss(7)
                  ├── ram(7) ── volatility(7)
                  │        ├── memory-buffer(7)
                  │        ├── bus(7) ── dma(7)
                  │        └── von-neumann(7)
                  └── storage-device(7) ── flash-wear(7)

bit-width(7)     latency(7)     firmware(7)     bandwidth(7)     (roots of their own)
```

**§6.4's five graph checks, run by hand.** (1) Every `prerequisites` reference resolves — 34 point inside this document, `compute(7)` has none, and no entry here takes a prerequisite from another domain. (2) Acyclic — the spine above is a tree. (3) Every stage is at or after the latest stage among its prerequisites — checked row by row; the two that came closest to failing were `thermal-budget(7)` (prerequisite `throttling(7)`, both `operating`) and `memory-buffer(7)` (prerequisite `ram(7)`, both `operating`). (4) Every entry is reachable backwards from a `first-session` root: 33 reach `compute(7)`; the four roots in the last line do not, and that is a real finding rather than a formatting one — see **CA-4**. (5) No prerequisite edge points upward across domains: none points out of this document at all.

**Only one `first-session` entry.** Rule **R2** caps the whole game's first session at twelve entries across seven domains, and `00-curriculum-and-method.md` §6.2 already names `compute(7)` in its example set. This domain takes that one slot and nothing else, on purpose: in the first twenty minutes a player needs to know that cycles are a budget and that the budget is visible. They do not need to know what a cache line is, and rule **R3** says an explanation that answers a question nobody asked is worse than no explanation, because it is what teaches a player to stop reading.

### 2.5 Which eighteen were written in full, and why

`00-curriculum-and-method.md` §8 makes a full entry expensive — a verified `misconception`, a `transfer` that was actually run, a status set by ordered procedure, two reviews. Nineteen were written because they meet at least two of three tests:

| Test | Entries that meet it |
|---|---|
| **The game leans on it directly** — a mechanic breaks or becomes arbitrary without it | `compute`, `core`, `throttling`, `thermal-budget`, `bandwidth`, `memory-buffer`, `storage-device`, `interrupt` |
| **It carries a misconception worth killing** — a confident wrong belief that is actively producing bad predictions | `clock-speed` (GHz is speed), `hardware-thread` (16 threads is 16 cores), `parallelism` (concurrent means parallel), `ram` (RAM is memory, disk is storage), `cache` (caching is an optimisation), `memory-hierarchy` (RAM is fast), `instruction-cycle` (one instruction per cycle), `von-neumann` (data and code are different things to the machine) |
| **It unlocks several others** — three or more entries depend on it | `processor`, `memory-hierarchy`, `cache`, `cache-line`, `core`, `clock-speed`, `compute` (`bit-width` also qualifies, but is `01`'s — see §2.2 ⇩) |

The nineteen not written in full are inventoried, not abandoned: each has an id, a gloss, a status, a stage, prerequisites and a hook, which is everything a writer needs to run `00-curriculum-and-method.md` §8.1's loop without re-deciding anything. The three most likely to be promoted next are `polling(7)` (it completes `interrupt(7)`, which is written), `cache-miss(7)` (it is where `cache(7)`'s payload actually lands), and `power(7)` (Invariant I6 is an electricity-theft story and nothing says so yet).

### 2.6 Ownership — the concepts two domains could claim

Under `00-curriculum-and-method.md` §1.4 there is **one entry per concept and one owner per entry**; a player who gets two answers stops trusting both. Four cases had to be decided.

| Concept | Contested with | Resolved | Why |
|---|---|---|---|
| `compute(7)` | operating systems | **here** | Its real counterpart is CPU scheduling, which is an OS idea — but the primary rule is "the lowest-numbered domain that can fully define it without forward-referencing a higher one", and capacity share can be defined from a processor alone. It is also settled by force: `process(7)` (operating systems) lists `compute(7)` as a prerequisite, and rule **R8** forbids a prerequisite pointing upward, so `compute(7)` cannot live above the OS domain. Its shipped body already exists at `../client/04` §4.9; §3.1 is that page's curriculum source, and where the two differ, `00-curriculum-and-method.md` §1.2 makes the curriculum entry upstream. |
| `latency(7)` | networking | **here**, ⚠ contested | Architecture can define it from memory-access times with no network in the room; networking cannot define it without borrowing a time scale. The primary rule therefore gives it here. The escape hatch pulls the other way — the player meets latency most visibly as hop distance on the `map` window — so this is genuinely arguable, and it is **CA-2**. It is deliberately *not* written in full here so that a decision costs one paragraph rather than one page. |
| `top(1)` | the command line | **not here** | The rig monitor is this domain's anchoring surface, but `top(1)` is a command page: how to invoke it, what its columns mean, how to read its output. Its most-asked-about column — the load average — is a scheduler concept, not an architecture one. Left to the command-line domain, cited in `seeAlso`. |
| `isolated-partition(7)` | operating systems | **not here** | A `../client/04` §2.15 homonym ("partition" suggests a slice of a disk) whose real meaning is namespaces, cgroups and sandboxes. That is squarely the OS domain. Cited, never defined here. |

---

## 3. The entries

Written to `00-curriculum-and-method.md` §3.3's template, verbatim. Every entry is section 7, so **none has a `SYNOPSIS`, `OPTIONS` or `EXIT STATUS`** — §5.2 of the method document, and the absence itself teaches the section system.

Transfer tests state the platform they assume, per §8.3 and **ED-8**. Where a test is genuinely universal it says so.

---

### 3.1 `compute(7)`

```
id:             compute
section:        7
name:           compute
canonical:      Compute
gloss:          The rig's capacity budget, and the scarcity everything else orbits.
status:         real, simplified
aliases:        cycles, compute budget, capacity
glossary:       ../design/glossary.md
seeAlso:        thermal-budget(7), clock-speed(7), core(7), process(7),
                top(1), ps(1), unlock-gates(7), bandwidth(7)
reading:        nproc(1); lscpu(1); top(1); Linux cgroup v2 documentation,
                "cpu.max"
notes:          MANDATORY HOMONYM (../client/04 §2.15). A game cycle is not a
                CPU clock cycle, and this is the single most consequential
                collision in the game's vocabulary. Translators: "cycle" here
                means an allocation unit, not a clock tick — do not translate
                it with the word your language uses for a processor's clock.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          first-session
prerequisites:  none
hook:           The rig monitor, which is on screen in the first minute
                (`../client/00-client-overview.md` §6.1) and shows four
                numbers: total, allocated by consumer, available, recovering.
                Also every tool's published cycle cost, and the first time the
                player runs a command with `-n` and reads a price before
                committing (`../client/04` §3.4).
misconception:  commonly believed that a computer's speed is one number and
                that running more things divides it evenly; actually capacity
                is held rather than consumed, most of what a machine is doing
                is waiting rather than calculating, and the limit a player hits
                first is usually not raw speed but something reserved — a
                connection slot, a memory ceiling, a thermal limit.
transfer:       On macOS or Linux, run `nproc` (Linux) or `sysctl -n hw.ncpu`
                (macOS) to see how many hardware threads the machine offers,
                then `top` and read the load and per-process CPU columns. The
                player can now say what their own machine's capacity is and
                what is holding it — the same four questions the rig monitor
                answers. Assumes a Unix shell; on Windows, Task Manager's
                Performance tab shows the same two facts (ED-8).
simplified:     Real processors time-share: two programs each asking for the
                whole machine both run, each more slowly. This rig reserves —
                if a cycle is allocated, nobody else gets it. Reservation is
                the exception in real systems, not the rule; the nearest real
                analogue is a cgroup quota (`cpu.max`) or a cloud vCPU
                allocation, both of which are deliberate configurations rather
                than how a machine behaves by default. Cycles that "recover"
                on a timer are a game rule with no direct counterpart.
verified:       cgroup v2 cpu.max semantics — Linux kernel admin-guide
                cgroup-v2 documentation; vCPU as a sold allocation unit —
                major cloud provider instance documentation; time-sharing as
                the default scheduling model — any operating-systems text;
                clock cycle magnitudes (billions per second) — see
                clock-speed(7). Checked 2026-07-25.

## DESCRIPTION

Your rig has a fixed budget of cycles — 100 on a starting rig. Everything you
run holds some of it for as long as it runs: each bot, each armed defense,
mining, a control channel for every deployed miner, each relay hop. Compute is
capacity, not currency. It is not spent and gone; it is allocated and returned.

Four numbers matter and the rig monitor shows all four. **Allocated** is held
right now, broken down by what is holding it. **Available** is free to commit.
**Recovering** is on its way back, with a clock. **Total** is your ceiling.

The breakdown is not decoration. Because the four must add up, a consumer that
runs without being listed shows as arithmetic that fails — which is how a
hidden miner is found by hand, at no compute cost, when a scan would miss it.
See ps(1) and scan(8).

Cycles are never purchasable. Capacity comes from schematics and story
milestones only. See unlock-gates(7).

## REAL-WORLD COUNTERPART

real, simplified — CPU capacity, and the quotas used to divide it.

A real machine has a fixed number of hardware threads and an operating system
that divides them among everything that wants to run. `nproc` prints how many
you have; `top` shows what is using them. On Linux, cgroups can give a workload
a hard share of the processor — `cpu.max` — and cloud machines are sold in
vCPUs, which is that idea with a price on it.

Capacity is finite and contended, and the contention is visible if you look.
Every serious performance problem in real computing is a version of that:
something is holding a resource, and the job is finding out what.

## CAVEATS

A game cycle is not a CPU clock cycle. A real clock cycle is one tick of the
processor's clock and there are billions of them every second; a game cycle is
a unit of capacity share, much closer to a vCPU or a cgroup quota. See
clock-speed(7).

Real processors time-share. Two programs each asking for the whole machine both
run, each more slowly. This rig reserves instead: if it is allocated, nobody
else gets it. Reservation is the exception in real systems, not the rule.

Cycles that recover on a clock are a game rule. What is real underneath: a chip
that cannot shed heat slows itself down, so a machine under sustained load
genuinely is slower than the same machine idle. See thermal-budget(7).
```

> **Writer's note on §3.1.** A page for this term already ships in draft at `../client/04` §4.9. This entry is its curriculum source and the two are deliberately close. Two differences are intentional and both are improvements the shipped page should take: the `DESCRIPTION` now says *why* the four numbers matter (the arithmetic is the audit), which connects the domain's root entry to `../design/04-mining.md` §3.1's hard requirement; and the `misconception` field, which the shipped page never had, is what selected that sentence. `00-curriculum-and-method.md` §1.2 makes the curriculum upstream, so this is a `revision` bump on the shipped file, not a fork.

---

### 3.2 `processor(7)`

```
id:             processor
section:        7
name:           processor
canonical:      processor
gloss:          The part that carries out orders; everything else feeds or waits on it.
status:         real
aliases:        CPU, chip, central processing unit
seeAlso:        compute(7), core(7), clock-speed(7), instruction-cycle(7),
                memory-hierarchy(7), register(7), gpu(7)
reading:        lscpu(1); /proc/cpuinfo — proc(5); Patterson & Hennessy,
                "Computer Organization and Design", ch. 1
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  compute(7)
hook:           The Compute Cores upgrade (`../design/11-rig-infrastructure.md`
                §2) — the first time the player is told that the *ceiling*
                itself can grow, and wants to know what a "core" is a core of.
misconception:  commonly believed the processor is where a computer's data and
                programs live, so a faster one makes everything faster;
                actually the processor holds almost nothing — it works on a few
                hundred bytes at a time, in registers — and it spends most of
                its life waiting for data to arrive from somewhere slower.
                Making it faster speeds up only the part that was not waiting.
transfer:       On Linux, run `lscpu`; on macOS, `sysctl -a | grep machdep.cpu`
                or About This Mac. The player can now name their own machine's
                model, its core count, its thread count and its cache sizes,
                and can tell which of those numbers a shop's spec sheet was
                leaning on. Assumes a Unix shell; Windows Task Manager's
                Performance → CPU tab shows the same four figures (ED-8).
verified:       register file size and general-purpose register counts —
                Intel 64 and IA-32 Architectures Software Developer's Manual,
                Vol. 1 (16 GPRs); Arm Architecture Reference Manual for
                A-profile (31 GPRs); memory-wait dominance — see cache-miss(7)
                and the latency table in memory-hierarchy(7). Checked
                2026-07-25.

## DESCRIPTION

Every cycle on your rig monitor is a share of one physical thing: the
processor. It is the only part of the machine that actually carries out orders.
Storage keeps data, memory holds what is in use, the network moves it around —
but nothing happens until the processor does it.

It is much smaller than most people expect. A processor works on a few hundred
bytes at a time, held in registers: a few dozen named slots inside the chip
itself. Everything else it needs has to be fetched from somewhere further away,
and the fetching is slow enough that a modern processor spends a large fraction
of its life doing nothing at all while it waits. See memory-hierarchy(7).

This is why compute in this game is *allocated* rather than *spent*. A tool
that holds cycles is holding a share of a physical engine, and the engine
cannot be in two places at once.

## REAL-WORLD COUNTERPART

real — the CPU in the reader's own machine.

A processor carries out machine instructions: tiny, fixed orders such as "add
these two numbers", "compare these", "load this from memory", "jump there if
the last comparison was equal". Everything a computer does — a video call, a
spreadsheet, this game — is those orders, in enormous quantity.

x86-64 processors, the kind in most desktops and laptops, give a program 16
general-purpose registers. ARM64 chips, the kind in phones and in Apple's Macs,
give 31. That is the entire working surface: 128 or 248 bytes of storage that
the processor can reach at full speed. Everything else is a trip.

The consequence is the one worth keeping. A processor is not a container. It is
a very fast, very small workshop with a long supply line, and most attempts to
make a real program faster turn out to be about the supply line rather than the
workshop.
```

---

### 3.3 `clock-speed(7)`

```
id:             clock-speed
section:        7
name:           clock speed
canonical:      clock speed
gloss:          How many times a second the chip advances, counted in GHz.
status:         real
aliases:        clock, frequency, GHz, clock rate
seeAlso:        compute(7), processor(7), instruction-cycle(7), core(7),
                throttling(7), power(7)
reading:        lscpu(1); `cat /proc/cpuinfo`; Intel and AMD processor
                datasheets, "base frequency" and "maximum turbo frequency"
notes:          This page is half of the ../client/04 §2.15 "cycles" homonym.
                compute(7) carries the other half; the two must not drift.
                Both must remain in seeAlso of the other.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  processor(7)
hook:           The word "cycles" on the rig monitor, which a player who knows
                anything about computers will read as clock cycles — the
                collision `../client/04` §2.15 calls "the most consequential
                one" in the game.
misconception:  commonly believed that gigahertz is the measure of how fast a
                computer is, so a 3.5 GHz chip is faster than a 3.0 GHz one;
                actually clock speed only says how often the chip advances, not
                how much it gets through per advance, and the two chips can
                differ by a factor of two in real work at identical frequency.
                Clock speed also stopped being the growth story around 2004,
                which is why processors got wider instead of faster.
transfer:       On Linux, `lscpu | grep -i mhz` and `cat /proc/cpuinfo | grep
                -i mhz` show the current frequency, which will visibly move
                while the machine works. On macOS, `sysctl -n hw.cpufrequency`
                (Intel Macs only; Apple Silicon does not publish it). The
                player can now look at any spec sheet, find the base and boost
                frequencies, and know that the second number is a ceiling under
                ideal cooling rather than a promise. Assumes a Unix shell
                (ED-8).
verified:       Intel cancelled its planned 4 GHz Pentium 4 in October 2004,
                shipping 3.8 GHz as the top of the line and shifting to cache
                and cores instead — contemporaneous trade press
                (Computerworld, Electronics Weekly, PC Perspective, Oct 2004);
                current top boost clocks are around 6 GHz (Intel Core
                i9-14900KS, 6.2 GHz) — vendor product specifications; modern
                cores complete several instructions per cycle — see
                instruction-cycle(7). Checked 2026-07-25.

## DESCRIPTION

A processor has a clock: an oscillator that ticks, and on each tick the chip
advances by one step. Clock speed is how many ticks there are in a second.
Three gigahertz is three billion.

This is where the game's word "cycles" comes from, and it is where the game's
word stops matching. A rig with 100 cycles does not have a 100 Hz processor.
The rig monitor's cycles are units of capacity share; a clock cycle is a unit
of time roughly a third of a nanosecond long. See compute(7).

The number is worth knowing anyway, because it is the honest floor under every
other timing claim in this game and in real computing. If a chip ticks three
billion times a second, one tick is about 0.33 nanoseconds, and everything else
— a memory fetch, a system call, a network round trip — can be measured in how
many ticks are wasted waiting for it.

## REAL-WORLD COUNTERPART

real — processor clock frequency, the number on every spec sheet.

Clock speed was the headline number for thirty years and then stopped. In
October 2004, Intel cancelled its planned 4 GHz Pentium 4 and shipped 3.8 GHz
as the top of that line, because the power and heat required to go further had
become unmanageable. Twenty years later, top desktop chips boost to a little
over 6 GHz — under two times faster — while core counts went from one to
dozens. That is the whole reason this domain has entries for cores, threads and
parallelism at all.

Two frequencies are quoted for a modern chip: a base frequency it can hold
indefinitely, and a boost or turbo frequency it can reach briefly, on a few
cores, when it is cool enough. The second number is a ceiling under favourable
conditions, not a rating. See throttling(7).

Frequency also does not say how much work happens per tick. A modern core can
complete four to eight instructions in a single cycle when the work allows it,
and one every few hundred cycles when it is waiting on memory. Two chips at the
same frequency routinely differ by half in real work.
```

---

### 3.4 `instruction-cycle(7)`

```
id:             instruction-cycle
section:        7
name:           fetch–decode–execute
canonical:      fetch–decode–execute cycle
gloss:          The repeating steps that turn a stored order into an effect.
status:         real, simplified
aliases:        instruction cycle, machine cycle, fetch-execute cycle
seeAlso:        instruction(7), processor(7), clock-speed(7), register(7),
                pipelining(7), von-neumann(7), memory-hierarchy(7)
reading:        Patterson & Hennessy, "Computer Organization and Design",
                ch. 4; Agner Fog's microarchitecture manuals
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  instruction(7), clock-speed(7)
hook:           `compute(7)`'s CAVEATS, which tells the player that a clock
                cycle is one tick and there are billions per second, and leaves
                the obvious question — what happens in a tick — unanswered.
misconception:  commonly believed a processor does one instruction per clock
                tick, in order, finishing each before starting the next;
                actually instructions are broken into stages and overlapped,
                so a modern core has well over a hundred in flight at once,
                completes several per tick when the work allows it, and
                routinely runs them out of order and speculatively.
transfer:       Nothing to type — this is the entry whose payload is a corrected
                model rather than a command. The checkable result: the player
                can now explain why "3 GHz" and "3 billion operations per
                second" are different claims, and why a benchmark result is not
                predictable from a frequency. (Universal; requires no shell.)
simplified:     The four-step loop is how a textbook first processor works and
                how the idea is best introduced, but no processor built in the
                last thirty years executes this way. Real cores pipeline the
                stages, issue several instructions per cycle, execute them out
                of program order, and speculate past branches — then present
                results as though everything had happened in order. What is
                left out here is precisely that machinery, which
                pipelining(7) and speculative-execution(7) pick up.
verified:       pipelining, superscalar issue and out-of-order execution as
                standard in mainstream cores — Patterson & Hennessy ch. 4,
                and vendor optimisation manuals (Intel Optimization Reference
                Manual; Arm Cortex software optimisation guides); the
                stored-program model this loop implements — von Neumann,
                "First Draft of a Report on the EDVAC" (1945). Checked
                2026-07-25.

## DESCRIPTION

A cycle on your rig monitor is capacity. A cycle inside a processor is time:
one tick, about a third of a nanosecond. What happens in that time is worth
knowing, because it is the smallest unit of anything actually getting done.

The loop, in its simplest form, has three steps and never stops. **Fetch** the
next instruction from memory. **Decode** it — work out what it is asking for.
**Execute** it, then store any result. Then advance to the next instruction and
begin again. That loop is running on every processor in the world right now,
including the one drawing this window.

Two things follow that matter here. First, the processor has no idea what it is
doing: there is no plan, only the next order. Second, "fetch from memory" is by
far the most expensive step, and it is the reason a machine can be fully
occupied and still doing nothing. See memory-hierarchy(7).

## REAL-WORLD COUNTERPART

real, simplified — the instruction cycle, as every processor implements it.

The loop is genuine and the vocabulary is standard. What is simplified is the
scale of the overlap. A real core does not finish one instruction before
starting the next; it splits each into stages and keeps a stage of one
instruction busy while another instruction is in a different stage — the same
principle as an assembly line. See pipelining(7).

Modern cores go much further. They decode several instructions at once, run
them out of program order as their inputs become available, guess which way a
branch will go and start work past it before the answer is known, and then
present the results as though everything had happened neatly in sequence. A
mainstream core can have well over a hundred instructions in flight.

## CAVEATS

The four-step loop is a teaching model, not a description of hardware built
this century. No processor you can buy executes one instruction at a time in
order.

The gap matters in two places a player will meet. It is why frequency does not
predict performance — the same tick can retire six instructions or none. And it
is the origin of a whole family of real attacks: work done speculatively is
undone when the guess was wrong, but its effect on the caches is not, and that
residue can be measured. That is what Spectre and Meltdown were, and it is what
side-channel-reader(1) is named after. See speculative-execution(7).
```

---

### 3.5 `core(7)`

```
id:             core
section:        7
name:           core
canonical:      core
gloss:          One complete engine on the chip, able to run work on its own.
status:         real
aliases:        CPU core, physical core
seeAlso:        processor(7), compute(7), compute-cores(7), hardware-thread(7),
                parallelism(7), cache(7), throttling(7)
reading:        nproc(1); lscpu(1); Linux `/proc/cpuinfo` — proc(5)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  processor(7)
hook:           The **Compute Cores** rig upgrade
                (`../design/11-rig-infrastructure.md` §2), the headline
                progression stat and the thing every other number in the game
                is measured against.
misconception:  commonly believed that doubling the cores halves the time, so a
                16-core machine finishes a job sixteen times sooner; actually
                only the part of a job that can be split gains anything, the
                parts that must happen in order gain nothing, and the cores
                contend for one shared memory system and one shared power and
                heat budget — so eight cores at full load often run at a lower
                clock than one core at full load.
transfer:       On Linux, `lscpu` prints "Core(s) per socket" and "Thread(s)
                per core" as separate lines, and `nproc` prints their product.
                On macOS, `sysctl -n hw.physicalcpu` and `sysctl -n
                hw.logicalcpu` print the two figures. The player can now say
                how many real engines their machine has, as distinct from how
                many the operating system advertises. Assumes a Unix shell;
                Windows Task Manager → Performance → CPU shows "Cores" and
                "Logical processors" (ED-8).
verified:       physical vs. logical core reporting — lscpu(1) and
                util-linux documentation, macOS sysctl(8) hw namespace;
                shared last-level cache and shared power budget across cores —
                Intel and AMD processor datasheets; all-core turbo below
                single-core turbo — vendor frequency specifications. Checked
                2026-07-25.

## DESCRIPTION

Compute Cores is the rig stat that raises your cycle ceiling, and it is the
only stat that does. Under Invariant I1 it cannot be bought — capacity comes
from schematics and story milestones, never from ethecoin — which is what stops
mining income from buying the capacity to mine more.

A core is one complete engine: it can fetch, decode and execute on its own,
independently of the others, at the same instant. Two cores are genuinely two
things happening at once, which is different from one core switching between
two jobs quickly. See parallelism(7).

What cores do not give you is a proportional increase in everything. They share
one memory system, one last-level cache and one power and heat budget. A rig
with more cores that is thermally constrained gets less out of each of them —
which is exactly the interaction the Thermal Budget stat models. See
thermal-budget(7).

## REAL-WORLD COUNTERPART

real — cores, exactly as the reader's own machine has them.

An ordinary laptop has between four and sixteen. `lscpu` on Linux prints them,
along with a separate line for threads per core, and `nproc` prints the two
multiplied together — which is why the advertised number is often double the
real one. See hardware-thread(7).

Cores became the growth story because clock speed stopped being one around
2004. Since then, more work per second has come almost entirely from more
engines rather than faster ones, and that shifted the burden onto software: a
job that cannot be split across cores gets no faster on a machine with more of
them, no matter how many.

Cores also share what surrounds them. They contend for the last-level cache and
for memory bandwidth, and they draw from one power budget, which is why a chip
that boosts one core to 5.5 GHz will hold all sixteen at something closer to
4.5 GHz. The advertised peak frequency and the advertised core count are
achievable individually and not together.
```

---

### 3.6 `hardware-thread(7)`

```
id:             hardware-thread
section:        7
name:           hardware thread
canonical:      hardware thread
gloss:          A second queue of work sharing one engine's idle moments.
status:         real, simplified
aliases:        logical processor, logical core, SMT, hyper-threading,
                simultaneous multithreading
seeAlso:        core(7), processor(7), compute(7), cache-miss(7),
                parallelism(7), compute-cores(7)
reading:        lscpu(1); Intel 64 and IA-32 Architectures Optimization
                Reference Manual (Hyper-Threading guidance)
notes:          "Thread" also means a software thread — a strand of execution
                inside a process — which is an operating-system concept and a
                different thing. Always write "hardware thread" in full on
                first use. Translators: this is the hardware sense only.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          investigating
prerequisites:  core(7)
hook:           The gap between the Compute Cores stat and the cycle total it
                produces — the first time a player asks why the ceiling is not
                simply the core count multiplied by something.
misconception:  commonly believed a "16-thread" processor has sixteen engines,
                so it does twice the work of an 8-core one; actually eight of
                those sixteen are a second queue on an existing engine, sharing
                its execution units, and the realistic gain from the second
                queue is roughly 15 to 30 per cent of throughput — not 100.
transfer:       On Linux, `lscpu` prints "Thread(s) per core"; a 2 there means
                half the advertised processors are the second queue. On macOS,
                compare `sysctl -n hw.physicalcpu` with `sysctl -n
                hw.logicalcpu`. The player can now read a laptop's "8 cores, 16
                threads" specification correctly, and can say why a
                sixteen-thread machine is not twice an eight-core one. Assumes
                a Unix shell (ED-8).
simplified:     Left out: that the second queue duplicates only the
                architectural state — the registers and the program counter —
                while the expensive parts (execution units, caches, branch
                predictors) stay shared, which is why the gain is a fraction
                rather than a doubling. Also left out: that sharing a core is
                a genuine security boundary problem, because two workloads on
                one core share caches and can therefore measure each other.
                Cloud providers and some browsers disable it for this reason.
verified:       real-world SMT throughput gain of roughly 15–30 % in
                throughput-oriented workloads — Intel Hyper-Threading
                documentation and independent measurement (NASA Advanced
                Supercomputing, "The Impact of Hyper-Threading on Processor
                Resource Utilization", 2011); Apple Silicon does not implement
                SMT — Apple platform documentation; IBM POWER supports up to
                8 threads per core — IBM POWER architecture documentation;
                SMT as a side-channel surface — the L1TF and MDS advisories.
                Checked 2026-07-25.

## DESCRIPTION

A core spends much of its time waiting: for memory, for a result, for a branch
to resolve. While it waits, its execution units sit idle. A hardware thread is
a second queue of work fed into the same core so that something can be run
during those gaps.

This is why the number of things a machine can run at once is often twice its
core count, and why that second number is worth less than the first. The engine
is not duplicated. Only enough state to keep a second job's place is
duplicated: its registers and its position in the program.

For this game the consequence is a modelling one. Compute Cores raises the
cycle ceiling as a single figure precisely because real capacity is not the
tidy product of engines and speed — it depends on how much of the engine is
idle, which depends on what is running.

## REAL-WORLD COUNTERPART

real, simplified — simultaneous multithreading; Intel calls its version
Hyper-Threading.

A machine advertised as "8 cores, 16 threads" has eight engines and sixteen
queues. The operating system sees sixteen processors and schedules onto all of
them. Measured gain from the second queue is typically 15 to 30 per cent extra
throughput when both queues have real work — worth having, nothing like a
doubling, and occasionally negative when the two jobs fight over the same
cache.

Not every design uses it. Apple's M-series chips have none: every logical
processor is a physical core. IBM's POWER processors go the other way and
support up to eight threads per core.

There is a security dimension worth knowing, because it is the same idea as
this game's isolation rules. Two workloads sharing a core share its caches and
its branch predictors, so each can measure the other's behaviour. That is a
real, exploited class of attack, and it is why some cloud providers will not
put two customers on one core and some browsers disable the feature outright.
See side-channel-reader(1).

## CAVEATS

"Thread" means two different things and both are common. A **hardware** thread
is this: a queue on a physical core. A **software** thread is a strand of
execution inside a running program, which the operating system schedules — a
machine can run thousands of those on eight hardware threads. Neither usage is
wrong; they are simply different layers. See process(7).
```

---

### 3.7 `parallelism(7)`

```
id:             parallelism
section:        7
name:           parallelism
canonical:      parallelism
gloss:          Doing several things in the same instant, not taking turns fast.
status:         real
aliases:        concurrency, parallel execution
seeAlso:        core(7), hardware-thread(7), bandwidth(7), gpu(7), process(7),
                compute(7)
reading:        Rob Pike, "Concurrency Is Not Parallelism" (2012 talk);
                Amdahl, "Validity of the single processor approach to
                achieving large scale computing capabilities" (1967)
notes:          Do not use "concurrent" and "parallel" as synonyms anywhere in
                the shipped content. This page is the reason. Translators:
                many languages have one word for both — where that is true,
                keep the English terms in parentheses on first use.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          investigating
prerequisites:  core(7)
hook:           The **Bandwidth** rig stat, which caps how many engagements can
                run at once (`../design/11-rig-infrastructure.md` §2) — and
                the split-attention penalty in `../design/10-botnets.md`, which
                is the game saying that running several things at once is not
                free.
misconception:  commonly believed that concurrent and parallel mean the same
                thing, so a machine that can run twenty things at once must
                have twenty engines; actually concurrency is a way of
                structuring work so that several jobs are in progress, which a
                single engine can do by switching between them, and parallelism
                is several engines running at the same instant. Almost
                everything a laptop appears to do simultaneously is the first.
transfer:       Open Activity Monitor (macOS), Task Manager (Windows) or `top`
                (Linux) and count the running processes — several hundred on an
                ordinary machine — then count the machine's cores. The player
                can now say why several hundred things are in progress on eight
                engines, and what the operating system is actually doing to
                make that true. (Universal: all three platforms have a
                built-in tool.)
verified:       the concurrency/parallelism distinction as stated — Rob Pike,
                "Concurrency Is Not Parallelism" (2012); the limit on speedup
                from the non-parallelisable fraction of a job — Amdahl (1967);
                process counts on ordinary machines — see process(7). Checked
                2026-07-25.

## DESCRIPTION

Bandwidth caps how many engagements you can run at once, and it is a separate
stat from compute on purpose: you can have cycles free and still be blocked
from starting another one. That separation is a real distinction wearing a
confusing name, and the distinction is worth more than the name.

**Concurrency** is having several jobs in progress. **Parallelism** is having
several jobs executing in the same instant. One engine can be concurrent by
switching between jobs quickly; only several engines can be parallel.

Almost everything that looks simultaneous is the first kind. Your rig runs
bots, defenses and a control channel per deployed miner, and they are all in
progress at once — but they are in progress the way a single person handles
several conversations, not the way several people do. That is why split
attention costs you something (`../design/10-botnets.md`): the switching is
real work, and the attention was never actually divided into equal halves.

## REAL-WORLD COUNTERPART

real — the concurrency/parallelism distinction, which is standard vocabulary
and constantly conflated even by practitioners.

Rob Pike's formulation is the one worth remembering: concurrency is about
*dealing with* many things at once; parallelism is about *doing* many things at
once. Concurrency is a way of structuring a program. Parallelism is a property
of the hardware it lands on. The same concurrent program is parallel on eight
cores and merely interleaved on one.

An ordinary laptop is running several hundred processes on eight or sixteen
hardware threads, so at any given instant almost all of them are stopped. The
operating system switches between them fast enough that a human cannot see the
gaps. See process(7).

The limit on parallelism is the part of the job that cannot be split.
If a tenth of a job must happen in order, then even with infinite engines the
job cannot go more than ten times faster, because that tenth still has to
happen. This is Amdahl's argument from 1967 and it has never stopped being
true; it is why more cores stop helping, and it is the reason "just add
machines" is not an answer to most performance problems.
```

---

### 3.8 `memory-hierarchy(7)`

```
id:             memory-hierarchy
section:        7
name:           memory hierarchy
canonical:      memory hierarchy
gloss:          Several places to keep the same data, each far slower than the last.
status:         real
aliases:        storage hierarchy, memory pyramid
seeAlso:        cache(7), ram(7), storage-device(7), cache-line(7),
                cache-miss(7), latency(7), processor(7), system-call(7)
reading:        "Latency Numbers Every Programmer Should Know" (Jeff Dean /
                Peter Norvig); 7-cpu.com measured cache latencies; Patterson &
                Hennessy, "Computer Organization and Design", ch. 5
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  processor(7)
hook:           The scan cost table in `../design/04-mining.md` §3.2 — 5, 15
                and 35 compute for scans lasting 30 seconds, 2 minutes and 6
                minutes. The compute cost and the duration are different
                currencies, and the reason they are different is this entry.
misconception:  commonly believed RAM is fast and disks are slow, and that
                these are the two speeds a computer has; actually there are
                five or six speeds spanning seven orders of magnitude, each
                step down is roughly a hundred to a thousand times slower than
                the one above it, and RAM — the thing usually called fast — is
                about a hundred times slower than the cache the processor
                actually works from.
transfer:       On Linux, `lscpu` prints the L1, L2 and L3 cache sizes for the
                machine; on macOS, `sysctl -a | grep cachesize`. Compare those
                figures (kilobytes and megabytes) to the machine's RAM
                (gigabytes) and its disk (terabytes). The player can now say
                why a program's performance can change by a hundred times
                without a single line of it changing — because the data crossed
                a level. Assumes a Unix shell (ED-8).
verified:       L1 latency 3–5 cycles, L2 12–20 cycles, RAM ~70–100 ns —
                7-cpu.com measured figures (Apple M1 Firestorm: L1 3–4 cycles,
                L2 18 cycles, RAM 18 cycles + 91 ns at 3.2 GHz, read
                2026-07-25) and Chips and Cheese measurements of AMD Zen 4
                (L1D 4 cycles); NVMe random-read latency in the 50–100 µs
                range — vendor drive specifications at queue depth 1;
                transatlantic round trip ~70–100 ms — routine `ping`
                measurement; overall shape — Dean/Norvig "Latency Numbers".
                Consistent with the table in `00-curriculum-and-method.md`
                §2.5. Checked 2026-07-25.

## DESCRIPTION

Your rig's scans cost compute *and* take time, and the two are priced
separately: a Thorough Scan costs 35 cycles and runs for six minutes. That is
not an inconsistency. Holding capacity and waiting are different costs, and on
a real machine almost all the waiting is for data to arrive from somewhere.

A computer keeps the same data in several places at once and the places differ
enormously in speed. The numbers are the lesson:

| Where | Roughly how long to fetch | Relative to the line above |
|---|---|---|
| a register, inside the core | under 1 nanosecond | — |
| L1 cache | about 1 ns (3–5 clock ticks) | a few times |
| L2 cache | 3–6 ns | ~4× |
| L3 cache | 10–20 ns | ~4× |
| main memory (RAM) | 70–100 ns | ~5–10× |
| a small random read from an SSD | 50–100 microseconds | ~1,000× |
| a small random read from a spinning disk | 5–10 milliseconds | ~100× |
| a round trip across the Atlantic | 70–100 milliseconds | ~10× |

From the processor's point of view, waiting on RAM wastes roughly a hundred
opportunities to do work. Waiting on an SSD wastes a hundred thousand. Waiting
on a network wastes a hundred million.

## REAL-WORLD COUNTERPART

real — the memory hierarchy, in every computer ever built with more than one
kind of storage.

The hierarchy exists because fast memory is expensive and small and slow memory
is cheap and large, and no single technology is both. So machines are built
with all of them at once and the hardware moves data upward as it is used. See
cache(7).

Two consequences are worth carrying away. First, this is why a machine that has
run out of memory is not slightly slow but pathologically slow: it has started
reaching for storage on the ordinary path, and it crossed a thousand-fold
boundary to do it. Second, it is why "more RAM" stops helping the instant there
is enough. Memory is not a speed dial. It is a cliff you either fall off or you
do not.

The same shape explains costs elsewhere in this curriculum. A system call costs
a few hundred nanoseconds against about one for an ordinary function call, so
buffering exists. A network round trip costs a hundred milliseconds, so
protocols are designed to avoid needing several. Every one of those arguments
is this table.
```

---

### 3.9 `cache(7)`

```
id:             cache
section:        7
name:           cache
canonical:      cache
gloss:          A small fast copy of what was just used, kept beside the engine.
status:         real
aliases:        CPU cache, L1, L2, L3, last-level cache
seeAlso:        memory-hierarchy(7), cache-line(7), cache-miss(7), locality(7),
                ram(7), core(7), hardware-thread(7)
reading:        lscpu(1); 7-cpu.com; Patterson & Hennessy, "Computer
                Organization and Design", ch. 5
notes:          "Cache" is also used for software caches — a browser cache, a
                DNS cache. Same principle, different layer; do not let the page
                imply the word only means the hardware sense.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  memory-hierarchy(7)
hook:           The first time a player notices that two rigs with the same
                cycle total do not feel the same to run — and, on the real
                side, `side-channel-reader(1)`, whose entire premise is
                learning about a system without entering it.
misconception:  commonly believed caching is an optimisation that clever
                software adds on top; actually the hardware does it
                unconditionally, on every memory access, with no way to switch
                it off, and modern performance is mostly a question of whether
                the cache is being used well rather than how fast the
                processor is.
transfer:       On Linux, `lscpu | grep -i cache` prints L1, L2 and L3 sizes;
                on macOS, `sysctl -a | grep cachesize`. Note that L1 is
                measured in tens of kilobytes while the machine's RAM is
                measured in gigabytes — a ratio of about a hundred thousand to
                one. The player can now explain why a program that fits in
                cache can be a hundred times faster than the same program on
                slightly more data. Assumes a Unix shell (ED-8).
verified:       typical L1 data cache 32–64 KiB per core, L2 0.5–2 MiB per
                core, L3 8–128 MiB shared — vendor processor specifications
                (Intel Raptor Lake, AMD Zen 4) and lscpu(1) output; L3 shared
                across cores — same; hardware caching is not optional on
                mainstream CPUs — architecture manuals. Checked 2026-07-25.

## DESCRIPTION

Cycles are not the only reason two rigs perform differently, and on real
hardware they are usually not the main one. What matters most is whether the
data the processor needs is already close to it.

A cache is a small, very fast copy of recently used data kept next to the core.
Processors have several, in layers. **L1** is tiny — tens of kilobytes per core
— and about as fast as the core itself. **L2** is larger and a few times
slower. **L3** is shared between all the cores on the chip, measured in tens of
megabytes, and slower again. Below that is RAM, roughly a hundred times slower
than L1.

The processor does this on its own. There is no instruction to enable it and no
setting to turn it off. Every read either finds what it wants nearby or does
not, and the difference between those two outcomes is roughly a factor of a
hundred. See cache-miss(7).

## REAL-WORLD COUNTERPART

real — CPU caches, present in every processor made in the last thirty-five
years.

The sizes are worth holding. An L1 data cache is typically 32 to 64 kilobytes
per core. L2 is half a megabyte to two megabytes per core. L3 runs from eight
megabytes to well over a hundred on server parts, shared by every core on the
chip. Against a machine's sixteen or thirty-two gigabytes of RAM, the fastest
level holds about one part in a million of it.

This is why performance advice that sounds like folklore is usually about
caches. "Iterate in the right order", "keep your data together", "avoid
pointer-chasing" — all of them mean the same thing: stay in the levels that are
fast. A program that fits in cache and one that does not can differ by a
hundred times with identical instructions.

Caching is also a general principle rather than a hardware trick. A browser
cache, a DNS cache and a database's buffer pool are all the same bet: keep what
was recently used, because it is likely to be wanted again. The bet is right
often enough that computing at every scale is built on it.
```

---

### 3.10 `cache-line(7)`

```
id:             cache-line
section:        7
name:           cache line
canonical:      cache line
gloss:          Memory moves in fixed blocks of 64 bytes, never one byte at a time.
status:         real
aliases:        cache block, line size
seeAlso:        cache(7), locality(7), cache-miss(7), memory-hierarchy(7),
                ram(7), side-channel-reader(1)
reading:        getconf(1) — LEVEL1_DCACHE_LINESIZE; sysctl(8) —
                hw.cachelinesize; Ulrich Drepper, "What Every Programmer
                Should Know About Memory" (2007)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          investigating
prerequisites:  cache(7)
hook:           `side-channel-reader(1)` — "learn about a system without
                entering it" (`../client/04` §2.6). The mechanism that makes
                cache side channels work is the fixed block size, because a
                fixed block is a measurable unit.
misconception:  commonly believed a computer reads exactly the byte it was
                asked for; actually the smallest thing that ever moves between
                memory and cache is a fixed block — 64 bytes on almost
                everything — so asking for one byte moves 64, and two variables
                that happen to sit in the same block are, as far as the
                hardware is concerned, one thing.
transfer:       On Linux, run `getconf LEVEL1_DCACHE_LINESIZE`. On macOS, run
                `sysctl hw.cachelinesize`. Both print the block size in bytes:
                64 on x86-64 machines, 128 on Apple Silicon. The player has now
                measured a hardware constant on their own machine that explains
                a whole class of otherwise mysterious performance behaviour.
                Assumes a Unix shell (ED-8).
verified:       64-byte lines on x86-64 — Intel and AMD architecture manuals;
                Apple Silicon reports 128 via `sysctl hw.cachelinesize`, though
                7-cpu.com measures the M1 Firestorm L1 at 64 bytes with 128 at
                L2/L3 (read 2026-07-25) — see §5.2, marked ⚠; false sharing as
                a named, measured effect — Drepper (2007) and standard
                concurrency literature; cache-line granularity as the basis of
                Flush+Reload — Yarom & Falkner, "FLUSH+RELOAD" (USENIX
                Security 2014). Checked 2026-07-25.

## DESCRIPTION

The Side-Channel Reader learns about a system without entering it. That is a
real class of attack and this is the fact that makes most of it possible: the
machine moves memory in fixed-size blocks, and a fixed size is something you
can count.

When a processor needs a byte that is not in cache, it does not fetch that
byte. It fetches the whole block containing it — 64 bytes on nearly every
machine a player is likely to own. There is no mechanism to fetch less. The
block is the unit the hardware deals in, all the way down.

Two consequences follow directly. Reading one byte you needed also brings 63
you did not, which is free and often useful. And two pieces of data that happen
to land in the same block are, to the hardware, a single object — so two cores
updating them independently will fight over the block even though they never
touch the same byte.

## REAL-WORLD COUNTERPART

real — the cache line, an architectural constant of the machine.

It is 64 bytes on x86-64 and on most ARM designs; Apple Silicon reports 128.
The reader can print it: `getconf LEVEL1_DCACHE_LINESIZE` on Linux, `sysctl
hw.cachelinesize` on macOS.

Two named real effects come out of it. **False sharing** is the fight described
above: two threads updating adjacent variables force the block back and forth
between cores, and a program can lose most of its parallel speedup to it
without any obvious cause. The standard fix is to pad the variables apart —
which is why performance-critical code sometimes has apparently pointless
64-byte gaps in it.

The second is measurement. Because a line is either present or not, and because
present and absent differ by roughly a hundred nanoseconds, a program can time
its own accesses and learn which lines *another* program touched. That is the
Flush+Reload technique, published in 2014, and it is the mechanism behind a
large fraction of the cache attacks that followed — including the ones that
made Spectre and Meltdown exploitable rather than theoretical. See
side-channel-reader(1) and speculative-execution(7).
```

---

### 3.11 `ram(7)`

```
id:             ram
section:        7
name:           RAM
canonical:      RAM
gloss:          Fast working memory that holds nothing once the power stops.
status:         real
aliases:        main memory, DRAM, system memory, memory
seeAlso:        memory-hierarchy(7), cache(7), storage-device(7),
                memory-buffer(7), volatility(7), von-neumann(7),
                virtual-memory(7)
reading:        free(1); vmstat(8); JEDEC DDR4/DDR5 standards (refresh
                timing); Halderman et al., "Lest We Remember: Cold Boot
                Attacks on Encryption Keys" (USENIX Security 2008)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  memory-hierarchy(7)
hook:           The **Memory Buffer** rig stat — how many tools can be readied
                at once, as distinct from how many are owned
                (`../design/11-rig-infrastructure.md` §2) — and the `storage`
                window next to it, which is the thing Memory Buffer is not.
misconception:  commonly believed RAM and storage are two words for the same
                idea with different speeds; actually they differ in kind
                rather than degree — RAM forgets everything the moment power
                stops, storage does not, and that single difference is why a
                computer has both and why "save your work" exists at all.
                (The corollary that is also wrong: that RAM forgets
                *instantly*.)
transfer:       On Linux, `free -h` prints total, used, free and available
                memory; note that "available" is the number that matters and
                that a healthy machine shows very little "free", because unused
                memory is wasted memory and the kernel fills it with cache. On
                macOS, Activity Monitor's Memory tab shows the same with
                "Memory Pressure" as the equivalent signal. The player can now
                read their own machine's memory correctly and stop worrying
                about a low "free" figure. Assumes a Unix shell or macOS
                (ED-8).
verified:       DRAM stores a bit as charge in a capacitor and requires refresh
                — JEDEC DDR standards; 64 ms retention window at ≤ 85 °C,
                32 ms above it — JEDEC DDR3/DDR4 specifications; data persists
                for seconds to minutes after power loss, longer when cooled —
                Halderman et al. (2008); Linux "available" vs "free"
                accounting — free(1) and Documentation/filesystems/proc.rst.
                Checked 2026-07-25.

## DESCRIPTION

Memory Buffer is how many tools you can have readied at once. Storage is how
many you own. Those are different stats because they are different things on a
real machine too, and the difference is not a matter of degree.

RAM is where a machine keeps what it is currently working on. It is roughly a
thousand times faster than an SSD and roughly a hundred times slower than the
processor's cache, and it holds a few gigabytes to a few dozen.

It is also **volatile**: it holds its contents only while it is powered. Cut
the power and it is gone. That is the entire reason a computer has storage as
well, and the entire reason "save your work" is a thing anyone has ever had to
say.

## REAL-WORLD COUNTERPART

real — main memory, the "16 GB" on a laptop's specification.

Ordinary RAM is DRAM, and the D is dynamic: each bit is a tiny charge on a
capacitor that leaks away. The memory controller therefore has to read and
rewrite every row on a schedule to keep it — the JEDEC standards require a full
refresh every 64 milliseconds at normal temperature, and every 32 above 85 °C,
because the leak is faster when it is hot. A meaningful fraction of a memory
chip's time is spent maintaining data that nobody asked for.

One thing worth correcting about your own machine: on Linux, a healthy system
shows almost no "free" memory, and that is correct. The kernel fills unused
memory with cached file data because unused memory is wasted memory, and gives
it back the instant a program needs it. The figure to read is `available`, not
`free`. `free -h` prints both.

## CAVEATS

"Volatile" does not mean instant. When power is cut, DRAM contents decay over
seconds — longer if the chips are cold. Researchers demonstrated in 2008 that
encryption keys could be recovered from a machine's memory after a reboot by
chilling the modules first, which is the origin of the **cold boot attack** and
the reason full-disk encryption schemes are careful about where keys live.

The lesson generalises past the trick: "the data is gone because the power went
off" is an assumption about physics, and physics is negotiable if someone
brings equipment. See volatility(7).
```

---

### 3.12 `storage-device(7)`

```
id:             storage-device
section:        7
name:           persistent storage
canonical:      persistent storage
gloss:          Where data waits when nothing is powered: slow, large, permanent.
status:         real
aliases:        disk, drive, SSD, HDD, hard disk, non-volatile storage
seeAlso:        memory-hierarchy(7), ram(7), flash-wear(7), storage-tiers(7),
                df(1), ls(1), filesystem(7), log-scrubber(1)
reading:        df(1); lsblk(8); smartctl(8); ATA Command Set (ACS) —
                DATA SET MANAGEMENT; NVM Express Base Specification — Dataset
                Management
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  memory-hierarchy(7)
hook:           The `storage` window, which shows the three tiers as mount
                points with capacities (`../client/00-client-overview.md`
                §6.1), and the `df` command that prints the same thing as
                text (`../client/04` §3.10).
misconception:  commonly believed deleting a file removes its contents from
                the drive; actually deleting normally removes only the
                directory entry that points at the data, leaving the data in
                place until something else happens to overwrite it — which is
                why file recovery works, why secure erasure is a separate
                operation, and why "I deleted the logs" is a weaker claim than
                it sounds.
transfer:       On macOS or Linux, run `df -h` and read the mount points,
                sizes and use percentages — the same four columns the storage
                window shows. On Linux, `lsblk` additionally shows which
                physical device each mount sits on. The player can now say how
                their own machine's storage is divided and which filesystem a
                given directory actually lives on. Assumes a Unix shell;
                Windows `wmic logicaldisk get size,freespace,caption` is the
                nearest equivalent (ED-8).
verified:       7200 rpm gives 8.33 ms per revolution and therefore ~4.17 ms
                average rotational latency — arithmetic from the rotation
                rate, standard in drive specifications; NVMe random-read
                latency 50–100 µs at queue depth 1 — vendor drive
                specifications; deletion removes the directory entry rather
                than the data — filesystem documentation (ext4, APFS, NTFS) and
                the existence of `shred(1)` and secure-erase commands as
                separate operations. Checked 2026-07-25.

## DESCRIPTION

Your storage window shows three mount points — vault, standard, high-hackable —
with capacities and an exposure column. `df` prints the same four facts as
text. Storage is where things sit when nothing is running, which is exactly
what makes it worth raiding: it is the tier that survives you logging off.

The distinction from memory is one of kind. Memory holds what is being worked
on and forgets it when the power stops. Storage holds what is being kept and
does not. Everything else — that storage is larger, slower and cheaper per byte
— follows from the technologies that can make that promise.

Storage is also the slowest thing in the machine that is not a network. A small
read from a solid-state drive takes fifty to a hundred microseconds: about a
thousand times slower than memory, and roughly a hundred thousand wasted
opportunities from the processor's point of view.

## REAL-WORLD COUNTERPART

real — drives, in the two kinds a reader is likely to own.

A **hard disk** stores bits magnetically on spinning platters and reads them
with a head on an arm. The arm must move to the right track, and then the
platter must rotate the data under it. At 7200 revolutions per minute a
rotation takes 8.3 milliseconds, so the average wait for the right sector alone
is about 4.2 — before any arm movement. That is why random access on a spinning
disk is roughly a thousand times worse than sequential, and why decades of
software were designed around not doing it.

A **solid-state drive** stores bits as trapped charge in flash cells with no
moving parts, so there is no seek and no rotation. Random reads run in tens of
microseconds instead of milliseconds — the largest single performance change in
ordinary computing in twenty years.

The property that matters most for this game is neither speed nor size. It is
that deleting a file usually does not erase it. A filesystem removes the entry
that points at the data and marks the space reusable; the bytes stay until
something writes over them. That is why deleted files are recoverable, why
secure erasure is a separate and slower operation, and why log-scrubber(1)'s
real-world counterpart — anti-forensics — is a much harder problem than it
appears. See flash-wear(7), where solid-state drives make it harder still.
```

---

### 3.13 `throttling(7)`

```
id:             throttling
section:        7
name:           thermal throttling
canonical:      thermal throttling
gloss:          A chip too hot to run fast slows itself down rather than fail.
status:         real
aliases:        throttling, thermal limit, TDP, power limit
seeAlso:        thermal-budget(7), clock-speed(7), power(7), core(7),
                compute(7)
reading:        Intel processor datasheets, "Package Power Control" (PL1, PL2,
                Tau); `sensors` from lm-sensors; `powermetrics(8)` on macOS
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  clock-speed(7)
hook:           The **Thermal Budget** rig stat and the recovery curve it
                governs (`../design/01-core-resources.md` §1.3) — specifically
                the first time a player runs a Thorough Scan while heavily
                loaded and finds themselves down 35 cycles for far longer than
                the scan ran.
misconception:  commonly believed a computer runs at its advertised speed and
                only slows down when it is broken or old; actually every modern
                machine continuously adjusts its own clock against temperature
                and power limits, the advertised boost figure is a short-term
                ceiling under good cooling rather than a rating, and the same
                laptop can be twice as fast on a hard desk as on a duvet.
transfer:       On a laptop, start something demanding — a video export, a
                large build, a game — and watch the frequency over several
                minutes: `watch -n1 "grep MHz /proc/cpuinfo"` on Linux, or
                `sudo powermetrics --samplers smc,cpu_power -i 1000` on macOS.
                The clock will start high and settle lower once the machine
                heats up. The player has now observed thermal throttling
                happening on their own hardware, which is the single most
                convincing way to learn it. Assumes a Unix shell; macOS
                requires sudo (ED-8).
verified:       Intel's PL1/PL2 power-limit model, with PL1 corresponding to
                Processor Base Power (formerly TDP) and PL2 to Maximum Turbo
                Power sustained for a window Tau — Intel processor datasheets,
                Package Power Control; boost frequencies are specified as
                achievable under favourable thermal conditions, not guaranteed
                — vendor frequency specifications; TDP is a cooling design
                target rather than a maximum power draw — same. Checked
                2026-07-25.

## DESCRIPTION

The Thermal Budget stat decides how fast spent cycles come back, and it decides
it worse the closer your rig sits to capacity. `../design/01-core-resources.md`
§1.3 is explicit about the intent: overextension should be punished by the
physics of the rig rather than by a rulebook.

The physics it is imitating is real and is happening in the machine the player
is reading this on. A processor turns essentially all the energy it draws into
heat. If the cooling cannot remove that heat as fast as it arrives, the chip
gets hotter, and past a limit it reduces its own clock — because running slower
is the alternative to being damaged.

So a loaded machine really is slower than an idle one, and not only because it
is busy. It is slower per unit of work. That is the honest thing the Thermal
Budget stat is pointing at, and it is one of the places this game's mechanics
happen to be describing something true.

## REAL-WORLD COUNTERPART

real — thermal and power throttling, on every processor sold in the last two
decades.

Modern chips are specified with two power limits. **PL1** is the power the chip
can sustain indefinitely — Intel now calls it Processor Base Power, and it is
what "TDP" used to mean. **PL2** is a higher figure the chip may draw for a
limited window, called Maximum Turbo Power. When the window expires or the
temperature limit is reached, the clock comes down.

This is why a thin laptop and a desktop with the same processor model are not
the same machine. Both reach the advertised boost frequency; only one holds it.
Reviewers measure this as "sustained versus burst" performance, and the gap is
routinely 30 to 50 per cent.

It is also why TDP is the most misread number on a spec sheet. It was never
maximum power draw — it is a design target for the cooling solution. A chip
with a 65 W figure can and does draw well over 200 W in a boost window.

The reader can watch it happen. Start something demanding on a laptop and watch
the reported clock frequency over five minutes: it starts high, and settles.
```

---

### 3.14 `thermal-budget(7)`

```
id:             thermal-budget
section:        7
name:           Thermal Budget
canonical:      Thermal Budget
gloss:          The rig stat governing how fast spent cycles come back.
status:         real, simplified
aliases:        thermal, recovery rate
glossary:       ../design/glossary.md
seeAlso:        throttling(7), compute(7), power(7), scan(8), core(7),
                unlock-gates(7)
reading:        Intel processor datasheets, "Package Power Control";
                `sensors` from lm-sensors; `powermetrics(8)` on macOS
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  compute(7), throttling(7)
hook:           The rig monitor's **recovering** figure, with its
                time-to-recover clock (`../design/01-core-resources.md` §1.4),
                and the scan cost table in `../design/04-mining.md` §3.2 —
                where a 35-cycle Thorough Scan on a loaded rig leaves the
                player down those cycles for far longer than the scan itself
                runs.
misconception:  commonly believed that a resource which "recovers over time" is
                a game convention with no counterpart, like a stamina bar;
                actually the thing it is standing in for is real and
                measurable — sustained performance below peak performance, with
                the gap widening the harder and longer a machine is pushed —
                and it is the reason a phone that is fast for thirty seconds is
                slow for thirty minutes.
transfer:       Run a sustained load on a laptop for five minutes and watch the
                clock frequency fall (see throttling(7) for the exact
                commands). Then re-run a short benchmark cold and hot and
                compare. The player can now predict, correctly, that any
                reviewer's single-run benchmark of a thin laptop overstates
                what that machine does for an hour. Assumes a Unix shell
                (ED-8).
simplified:     Two things are invented. **A refilling pool**: real hardware has
                no reservoir of cycles that drains and refills; it has a clock
                that goes down under heat and back up when cool, which is a
                continuous rate change rather than a stock. And **a single
                governing stat**: real sustained performance depends on the
                cooler, the case, the ambient temperature, the workload's mix
                and the vendor's power policy, not on one number. What is not
                invented is the shape — superlinear pain as load approaches
                capacity — which is exactly how real thermal behaviour feels.
verified:       sustained clock below boost clock under continuous load, with
                the gap depending on cooling — Intel PL1/PL2/Tau model in
                processor datasheets, and routine independent laptop testing;
                mobile devices throttle hard on sustained load — vendor
                documentation and standard sustained-performance benchmarking.
                Checked 2026-07-25.

## DESCRIPTION

Cycles spent on a discrete action do not come straight back. They return over
time, at a rate this stat governs, and **the closer your rig sits to capacity,
the slower they return**. A lean rig recovers quickly. An overextended one is
effectively down those cycles for a long stretch — which arrives precisely when
it can least afford it.

This is the stat that makes scanning cost something real. A Thorough Scan is 35
cycles and six minutes. On a lean rig that is an expense. On a rig already at
85 per cent allocation it is a hole you sit in, unable to respond to anything
else, for far longer than the scan ran. See scan(8).

It is also why a loaded rig *feels* sluggish rather than merely smaller.
Nominal and real costs diverge as you fill up, and the divergence is the
design.

## REAL-WORLD COUNTERPART

real, simplified — thermal and power throttling.

A processor converts almost all the energy it draws into heat. When cooling
cannot keep up, the chip lowers its own clock rather than damage itself, so a
machine under sustained load genuinely is slower per unit of work than the same
machine idle. Vendors specify this explicitly: a power limit the chip can hold
indefinitely, and a higher one it may use for a limited window. See
throttling(7).

The pattern every reader has met is a phone or a thin laptop that is fast for
half a minute and slower after ten. That is not degradation and not a fault; it
is the machine choosing a clock it can sustain.

## CAVEATS

Real hardware has no pool of cycles that drains and refills. It has a clock
that falls under heat and rises when cool — a continuously varying rate, not a
stock with a level. The refilling pool is a game device, chosen because a
visible reservoir with a clock on it is legible and a fluctuating frequency
curve is not.

One stat also stands in for many. Real sustained performance depends on the
cooler, the chassis, the room, the workload and the vendor's power policy. What
survives the simplification is the shape — the pain grows faster than the load,
and it grows fastest exactly when you are most committed.

This stat is not the game's "heat". Personal and server heat are The Eye's
attention and have nothing to do with temperature. See heat(7).
```

---

### 3.15 `interrupt(7)`

```
id:             interrupt
section:        7
name:           interrupt
canonical:      interrupt
gloss:          A signal that stops the chip mid-work to say something happened.
status:         real
aliases:        IRQ, hardware interrupt
seeAlso:        polling(7), io(7), processor(7), dma(7), canary(8),
                detection-array(8), system-call(7)
reading:        `cat /proc/interrupts` — proc(5); Linux NAPI documentation
                (networking driver interface); signal(7) for the software
                analogue
notes:          A hardware interrupt and a Unix signal are different things at
                different layers, though signals are often introduced with the
                same word. Keep them apart: this page is hardware.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          investigating
prerequisites:  io(7)
hook:           The contrast between two defenses the player owns. A **Canary
                Token** costs nothing while nothing happens and tells you the
                moment it is touched. A **Detection Array** holds a permanent
                compute reservation and scans on a schedule
                (`../design/09-defense-and-hardening.md`). Those are exactly
                interrupts and polling, priced exactly as the real ones are.
misconception:  commonly believed a computer checks its devices regularly to
                see whether anything has happened; actually the devices
                interrupt the processor, which is why an idle machine can use
                almost no power — a polling machine can never sleep, because it
                has to keep waking up to ask.
transfer:       On Linux, run `cat /proc/interrupts` — one row per interrupt
                source, one column per CPU, counts rising as the machine works.
                Run it twice a second apart and watch which rows move. The
                player can now see, concretely, which hardware is talking to
                their processor and how often. Assumes Linux; macOS and Windows
                do not expose an equivalent in a comparable form (ED-8).
verified:       interrupt-driven I/O and the interrupt controller model —
                Intel 64 and IA-32 SDM Vol. 3 (interrupt and exception
                handling); `/proc/interrupts` format — Linux proc(5); Linux
                NAPI switching from interrupt-per-packet to polling under high
                load — Linux kernel networking documentation. Checked
                2026-07-25.

## DESCRIPTION

You own two kinds of detection and they cost completely differently. A Canary
Token sits there costing nothing and tells you the instant somebody touches it.
A Detection Array holds a permanent compute reservation and looks, on a
schedule, whether anything is wrong.

That is not a game balance decision. It is the oldest trade in systems design,
and both halves have names.

An **interrupt** is the canary. A device that needs attention raises a signal;
the processor stops what it is doing, saves its place, runs a short handler,
and resumes. Nothing is spent while nothing happens, and the response is
immediate.

**Polling** is the array. The processor asks repeatedly whether anything has
happened. Every ask costs something whether or not the answer is yes, and the
answer is late by up to one interval. See polling(7).

## REAL-WORLD COUNTERPART

real — hardware interrupts, the mechanism by which a computer notices anything
at all.

Every keypress, every packet arriving, every completed disk read, every tick of
the system timer is an interrupt. On Linux the reader can watch them: `cat
/proc/interrupts` prints one row per source with a count per processor, and the
counts visibly climb as the machine is used.

This is why an idle machine can draw almost no power. It is genuinely asleep,
and hardware wakes it. A machine that polled instead could never sleep, because
it would have to keep waking up to ask.

The trade reverses under load, which is the part worth keeping. Handling one
interrupt is cheap; handling ten million a second is not, because each one
costs a stop, a save and a resume. So a network card receiving at full rate
would spend the machine entirely on being told about packets. Linux solves this
with NAPI: the driver takes the first interrupt, then switches the card to
silence and polls until the burst is over.

The rule generalises well beyond hardware. Interrupt when events are rare and
matter individually. Poll when they are constant and can be batched. A canary
and a scheduled scan are the same choice, at a different layer.
```

---

### 3.16 `memory-buffer(7)`

```
id:             memory-buffer
section:        7
name:           Memory Buffer
canonical:      Memory Buffer
gloss:          The rig stat for how many tools you can have readied at once.
status:         real, simplified
aliases:        buffer, tool slots, loadout
glossary:       ../design/glossary.md
seeAlso:        ram(7), storage-tiers(7), compute(7), vault(7),
                memory-hierarchy(7), overflow-kit(1)
reading:        free(1); "resident set size" in ps(1) and top(1)
notes:          MANDATORY HOMONYM (../client/04 §2.15). A reader who knows the
                field will hear "buffer" and think of an I/O buffer, or of
                buffer overflows. This stat is neither. The CAVEATS section
                must name both wrong readings explicitly; do not leave it to
                inference. Translators: "buffer" here means working-set
                capacity, not a data staging area.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  ram(7)
hook:           The Memory Buffer upgrade
                (`../design/11-rig-infrastructure.md` §2) sitting next to the
                storage tiers in the market, and the first time a player owns
                more tools than they can field.
misconception:  commonly believed that owning software and being able to use it
                are the same thing, because on a modern machine they usually
                are; actually there is always a working set — the part actually
                held in fast memory — and it is much smaller than what is
                installed, which is why a machine with a large disk and small
                memory can be unusable while a machine with the reverse is
                fine.
transfer:       On macOS or Linux, run `ps aux | sort -rnk 4 | head` to list
                processes by memory share, or `top` and sort by the RES column.
                Compare the total resident memory of everything running to the
                size of the applications folder. The player can now distinguish
                what their machine has *installed* from what it is *holding*,
                which is the exact distinction this stat makes. Assumes a Unix
                shell (ED-8).
simplified:     Left out: that real memory is not a slot count. A program's
                resident set is measured in bytes and varies continuously as it
                runs; the operating system pages parts of it in and out, shares
                pages between processes, and can lie convincingly about how
                much is in use. "Number of things readied" is a legible stand-in
                for "bytes currently resident", which is the real quantity.
                Also left out: virtual memory entirely, which is what makes a
                program able to believe it has more memory than exists.
verified:       resident set size as the standard measure of a process's real
                memory use — ps(1), top(1); shared pages between processes
                causing RSS totals to exceed physical memory — proc(5) and
                standard memory-accounting documentation; installed size versus
                resident size as different quantities — same. Checked
                2026-07-25.

## DESCRIPTION

Storage is how much you own. Memory Buffer is how much you can have readied at
once. A player can own a deep toolkit and still field only a loadout-sized
slice of it, and expanding the two is a separate decision with a separate cost.

That separation exists on real machines and it is the more important of the two
numbers. A machine's disk holds everything installed; its memory holds what is
actually in use. The first can be enormous and the second is always modest, and
performance follows the second.

The reason is the memory hierarchy. What is in memory can be reached in about a
hundred nanoseconds. What is only on disk costs a thousand times that. A
machine that has readied too much for its memory does not slow down gently — it
starts fetching from storage on the ordinary path and becomes pathologically
slow. See memory-hierarchy(7).

## REAL-WORLD COUNTERPART

real, simplified — a program's working set, usually measured as resident set
size.

`ps` and `top` both report it: the RES or RSS column is how much physical
memory a process is actually holding right now, as distinct from how large the
program is on disk and from how much memory it has asked the system for.

An ordinary laptop shows the ratio: a few hundred gigabytes installed, sixteen
of memory, a browser holding perhaps two across all its processes. The
installed figure has almost no bearing on how the machine feels. The resident
figure has almost all of it.

## CAVEATS

**This is not an I/O buffer.** In ordinary computing, a buffer is a staging
area — a chunk of memory where data waits between being produced and being
consumed, which is what makes reading a file in one large chunk cheaper than a
million small ones. That is a real and useful concept and it is not this stat.

**It has nothing to do with buffer overflows.** A buffer overflow is a
memory-corruption bug: writing past the end of an allocated region into
whatever sits after it. The game's Overflow Kit is named after that, not after
this stat, and the two share only a word. See overflow-kit(1).

What this stat simplifies: memory is not slots. A working set is measured in
bytes, changes continuously, is shared between processes, and is managed by the
operating system rather than chosen by the user.
```

---

### 3.17 `bandwidth(7)`

```
id:             bandwidth
section:        7
name:           Bandwidth
canonical:      Bandwidth
gloss:          The rig stat capping how many engagements can run at once.
status:         game
aliases:        simultaneity cap, engagement cap
glossary:       ../design/glossary.md
seeAlso:        compute(7), parallelism(7), bus(7), memory-hierarchy(7),
                socket(7), packet(7)
reading:        `ulimit -n`; getrlimit(2) — RLIMIT_NOFILE; iperf3 for
                measuring real network bandwidth
notes:          MANDATORY HOMONYM (../client/04 §2.15, §2.9). "Bandwidth" is a
                real term meaning data rate; this stat caps concurrency
                instead. The mismatch must be stated in CAVEATS, never implied.
                Translators: do not translate this with your language's word
                for network throughput — it will be actively misleading. Keep
                the English term and gloss it.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          operating
prerequisites:  compute(7)
hook:           The moment a player has cycles free and is still refused
                another engagement (`../design/11-rig-infrastructure.md` §2:
                "you can have cycles free and still be bandwidth-blocked").
                That refusal is confusing precisely because the stat is
                misnamed, which is what makes it a teaching opportunity rather
                than only a defect.
misconception:  commonly believed bandwidth means "speed" and that a slow
                connection is always a bandwidth problem; actually bandwidth is
                how much data fits through per second and latency is how long
                one round trip takes, they are independent, and most of what
                feels slow on a fast connection is latency or a concurrency
                limit rather than a shortage of bandwidth.
transfer:       Run `ulimit -n` on macOS or Linux: the number of files and
                network connections one process may have open at once,
                typically 256 on macOS and 1024 or 1048576 on Linux. That is a
                real concurrency cap of exactly the kind this stat models, and
                exceeding it is the actual cause of the "too many open files"
                error that a player will eventually meet in real life. Assumes
                a Unix shell (ED-8).
verified:       RLIMIT_NOFILE as a per-process open-descriptor cap —
                getrlimit(2), ulimit built-in documentation; default soft
                limits vary by platform — macOS and common Linux distribution
                defaults; bandwidth and latency as independent quantities —
                any networking text, and directly observable via `ping`
                alongside a throughput test. Checked 2026-07-25.

## DESCRIPTION

Bandwidth caps how many engagements you can run at once. It is deliberately
separate from compute: you can have cycles free and still be blocked from
starting another one, which bounds the botnet and multitasking ceiling
independently of raw capacity.

That is a useful mechanic with a borrowed name, and the borrowing is worth
knowing about, because the word means something else everywhere outside this
game.

## REAL-WORLD COUNTERPART

game — the stat as defined here has no counterpart. The word does, and it means
something different.

Real bandwidth is a **rate**: how much data fits through a link per second. A
gigabit connection is a bandwidth figure. It says nothing about how long any
individual thing takes, which is latency, and the two are independent — a
satellite link can have enormous bandwidth and terrible latency, and a
short-haul fibre the reverse. See latency(7).

What this stat actually models is a **concurrency limit**, and real systems have
several of those under other names: the number of worker processes in a pool,
the number of simultaneous connections a server accepts, and — most directly —
the number of open files and sockets one process is allowed, which on Unix is
`RLIMIT_NOFILE` and is printed by `ulimit -n`. Exceeding it produces "too many
open files", which is one of the more common and more confusing errors in real
operations, precisely because the machine has plenty of memory and plenty of
CPU and is refusing anyway.

That is exactly this stat's behaviour: cycles free, request refused.

## CAVEATS

**This stat is not bits per second.** Nothing about it is a data rate. A player
who leaves this game believing that "more bandwidth" means "more simultaneous
things" will apply that to a home internet connection and be wrong: buying a
faster line does not raise how many connections a machine will accept, and does
not fix a latency problem at all.

The honest name for the real thing would be a connection limit or a worker-pool
size. The game's name is kept for readability, and this page exists so the
keeping does not cost the player a true belief.
```

---

---

### 3.18 `von-neumann(7)`

```
id:             von-neumann
section:        7
name:           the stored-program model
canonical:      stored-program model
gloss:          Orders and data live in the same memory, reached down the same path.
status:         real, simplified
aliases:        von Neumann architecture, von Neumann bottleneck,
                stored-program computer
seeAlso:        instruction(7), ram(7), memory-hierarchy(7), cache(7),
                overflow-kit(1), processor(7), firmware(7)
reading:        von Neumann, "First Draft of a Report on the EDVAC" (1945);
                Backus, "Can Programming Be Liberated from the von Neumann
                Style?", 1977 ACM Turing Award lecture, CACM 21(8), 1978
revision:       1

--- curriculum only, stripped before shipping ---

domain:         02
stage:          investigating
prerequisites:  instruction(7), ram(7)
hook:           `overflow-kit(1)` — the game's memory-corruption tool
                (`../client/04` §2.6). A player who has used it and read that
                a real overflow is "a memory-corruption primitive" is owed the
                one structural fact that makes such a thing possible at all.
misconception:  commonly believed a computer keeps its programs and its data
                separately, the way a person keeps tools and materials apart;
                actually both are bytes in the same memory reached down the
                same wires, and nothing in the hardware distinguishes them —
                which is simultaneously why a computer can run any program you
                give it and why writing past the end of a data structure can
                change what the machine does next.
transfer:       On Linux, run `cat /proc/self/maps` — the memory map of the
                command you just ran, with permission flags on each region:
                `r-x` for executable code, `rw-` for writable data. The player
                can now see that both are regions of one address space, and
                that the only thing keeping them apart is a permission bit the
                operating system set. Assumes Linux; macOS has `vmmap` (ED-8).
simplified:     Left out: that real machines are no longer purely von Neumann.
                Processors split the first level of cache into separate
                instruction and data caches, which is a Harvard arrangement at
                the top of the hierarchy over a shared memory underneath. Also
                left out: that the "nothing distinguishes them" claim is true
                of the hardware's data path but not of modern protections —
                every current operating system marks writable pages
                non-executable, which is what turned the simplest overflow
                attacks into a much harder problem.
verified:       the stored-program concept as described — von Neumann, "First
                Draft of a Report on the EDVAC" (1945); "von Neumann
                bottleneck" coined in Backus's 1977 Turing Award lecture,
                published CACM 21(8), 1978; split L1 instruction and data
                caches as standard practice — vendor architecture manuals;
                W^X / NX page protection as standard — Intel SDM (execute
                disable bit), Arm ARM (XN), and OS documentation. Checked
                2026-07-25.

## DESCRIPTION

The Overflow Kit corrupts memory rather than skipping a step, and that is only
possible because of one structural decision made in 1945 and never reversed.

A computer keeps its instructions and its data in the same memory. There is no
separate place for programs. When a processor fetches its next instruction it
reads from memory exactly as it would read a number, down the same wires, with
no marker distinguishing the two. Whether a given byte is an instruction or a
value depends entirely on how it is reached.

This is the source of almost everything a computer can do. A machine that
stores its own instructions can be given new ones, which is why one machine
runs every program rather than being built per task. It is also the source of a
whole family of attacks: if data and instructions share a space, writing too
much data into the wrong place can change what runs next.

## REAL-WORLD COUNTERPART

real, simplified — the stored-program computer, described in von Neumann's 1945
draft report on the EDVAC and the basis of essentially every machine since.

The design has one famous cost, named by John Backus in his 1977 Turing Award
lecture: the **von Neumann bottleneck**. Instructions and data travel between
the processor and memory over the same path, so the machine's rate is capped by
that path — and processor speed has risen far faster than memory latency has
fallen, for decades, so the gap has widened rather than closed. Caches exist
because of it. See memory-hierarchy(7).

The reader can see it directly. On Linux, `cat /proc/self/maps` prints a
running command's memory regions with permission flags: `r-x` where code lives,
`rw-` where data does. One address space, two kinds of contents, separated by a
permission bit rather than anything physical.

## CAVEATS

Machines are no longer purely von Neumann at the top. Processors split the
first-level cache into separate instruction and data caches — a Harvard
arrangement layered over shared memory below. The single path is real; it is
not the only path.

And "nothing distinguishes code from data" describes the hardware's data path,
not the protections built on it. Every current operating system marks writable
memory non-executable, so the simplest form of the attack — write instructions
into a data buffer and jump to them — has not worked by default for around
twenty years. What replaced it reuses instructions that were already there,
which is harder and still routine. See overflow-kit(1).
```

---

## 4. What this domain deliberately does not teach

`00-curriculum-and-method.md` §7.3 is blunt about the reason: **an entry with no hook is an unread entry.** The delivery mechanism is contextual — the gloss bar fills because the player's attention is already on the term — so a concept with no surface has no trigger and lives only in the index, where it is found by people who already knew to look. It costs writing, review, translation and index noise, and it teaches nobody.

### 4.1 Out of scope, with reasons

| Not taught | Why not |
|---|---|
| **Digital logic** — gates, flip-flops, adders, how a transistor works | Genuinely the layer below this one, and genuinely interesting. No game surface reaches it: nothing the player touches becomes more predictable for knowing what a NAND gate is. `reading:` on `processor(7)` points at Patterson & Hennessy for anyone who wants to go down. |
| **Assembly language and how to write it** | `../client/04` §5.3's ruling that section 2 is "tempting and wrong" applies with equal force here. The game has no programming surface. The player is learning that instructions exist and what they cost, not how to emit one. |
| **Hexadecimal and binary notation as a skill** | `00-curriculum-and-method.md` §2.2 explicitly does not assume it, and nothing in the game requires reading a hex value. `bit-width(7)` teaches what "16 bits" *means* without ever asking the reader to convert anything. |
| **Endianness, alignment, floating point, two's complement** | Real, important, and invisible from every surface in this game. A player never sees a byte order or a rounding error. |
| **Specific microarchitectures**, benchmark comparisons, product recommendations | Rots within a year, is a vendor's framing, and teaches nothing durable. Where a number is needed, this document gives a range and says what it depends on (§2.7 of the method doc). |
| **NUMA, cache coherence protocols, memory consistency models** | The genuinely hard part of multiprocessor architecture, and correspondingly the part with no hook whatsoever. `parallelism(7)` stops at the boundary where the player's questions stop. |
| **Virtual memory, paging, page tables, the MMU** | Owned by the operating-systems domain, which is correct: paging is something an OS does, not something a machine is. `ram(7)` deliberately stops at "physical memory holds what is in use" and cites `virtual-memory(7)`. |
| **Scheduling, context switches, load average** | Same reason. `compute(7)` names time-sharing to make its own simplification legible and then hands off. |
| **Isolation: containers, VMs, namespaces** | A `../client/04` §2.15 homonym (Isolated Partition) and squarely the OS domain's. Cited from `hardware-thread(7)`'s security note, never defined here. |
| **Cryptographic hardware** — AES-NI, TPMs, secure enclaves | Belongs with the security domain, whose threat-model framing is what makes them make sense. `firmware(7)` touches the boundary and stops. |
| **The history of computing** | `00-curriculum-and-method.md` §7.3 names it directly. Where a date carries a live consequence it appears inside an entry — 1945 for the stored-program model, 2004 for the clock plateau — and never as its own page. |

### 4.2 Two structural non-choices worth stating

**Everything here is section 7, and there is no command page.** Under `00-curriculum-and-method.md` §5.2 that is the correct outcome, not a gap: none of these concepts is something the player types. The commands that *would* belong to this domain — `nproc(1)`, `lscpu(1)`, `sensors`, `powermetrics(8)` — are not in the game's command catalogue (`../client/04` §3.10), so under §7.3 they get no entry. They appear in `reading:` instead, which is exactly what Tier 3 is for: a real tool, named precisely, that a curious player can go and run tonight.

**No entry here renders a game value.** `../client/04` §4.7's addition — the teaching layer never renders a server-owned value — bites hardest in this domain, because every one of these pages sits next to a live number. `compute(7)` says what a cycle is; it never says how many you have. That keeps the content static, cacheable and translatable, and it means a page here can never be wrong about the game state, because it never claims to know it.

---

## 5. The honesty ledger

`00-curriculum-and-method.md` §7.4 requires two published counts. The status distribution is §2.3. This is the second.

### 5.1 What was verified, and against what

Every factual claim in §3 carries a source in its `verified:` field. The classes of source used, in order of preference and per `../client/04` §4.4 (stable, primary, free, not a vendor's framing):

- **Vendor architecture manuals** — Intel 64 and IA-32 SDM, Arm Architecture Reference Manual, processor datasheets. Used for register counts, cache-line size, power-limit models, interrupt handling.
- **Measured public data** — 7-cpu.com (Apple M1 page read 2026-07-25), Chips and Cheese microbenchmarks (Zen 4). Used for cache and memory latencies, in cycles and nanoseconds.
- **Standards** — JEDEC DDR specifications (refresh timing), IEC 80000-13 (binary prefixes), ATA Command Set and NVM Express (TRIM / Deallocate), RFC 9293 §3.1 (16-bit ports, inherited from `../client/04` §2.6).
- **Primary literature** — von Neumann (1945); Backus (1978, the 1977 Turing lecture); Amdahl (1967); Halderman et al. (2008, cold boot); Yarom & Falkner (2014, Flush+Reload); Pike (2012, concurrency vs parallelism).
- **Contemporaneous record** — trade press from October 2004 on the cancellation of the 4 GHz Pentium 4.
- **Manual pages and kernel documentation** — `lscpu(1)`, `free(1)`, `df(1)`, `getconf(1)`, `proc(5)`, `getrlimit(2)`, Linux NAPI documentation.

The latency figures in `memory-hierarchy(7)` were checked for consistency against `00-curriculum-and-method.md` §2.5's table, which is the register exemplar the whole doc set copies. **They agree.** Where this document adds levels §2.5 did not name — L2 and L3 — the figures are measured ones, cited.

### 5.2 Marked ⚠ — claims not fully verified

Per §7.4, these are marked here rather than asserted confidently in a shipped page. The correct action for each is to resolve it before the corresponding term file is written.

- ⚠ **Apple Silicon's cache line size.** `sysctl hw.cachelinesize` reports 128 on M-series machines, and that is what `cache-line(7)`'s transfer test will print. But 7-cpu.com's M1 page (read 2026-07-25) records 64 bytes for the Firestorm core's L1 and 128 for L2 and L3. Both may be true — the OS reports the largest line in the hierarchy — but this document has not confirmed that reading. `cache-line(7)` is worded to survive either ("Apple Silicon reports 128"), and the underlying question is **CA-6**.
- ⚠ **QLC flash endurance.** `flash-wear(7)`'s inventory row does not carry a number for QLC program/erase cycles, because the figure could only be confirmed as "materially lower than TLC" (whose 1,000–3,000 range *is* confirmed). When that entry is written in full, either a cited figure is found or the entry says "lower, and vendors do not publish comparable figures" — which is itself information.
- ⚠ **The cost of a single hardware interrupt.** `interrupt(7)` deliberately gives no per-interrupt nanosecond figure, because the honest answer spans an order of magnitude across platforms and interrupt types. The argument it makes instead — that ten million per second is unaffordable, hence NAPI — is true without needing the constant. If a defensible figure is found, it is an improvement; inventing one would not be.
- ⚠ **`isa(7)`'s hook.** Marked weak in §2.2. Its game surface is `zero-day(1)`'s caveat that real vulnerabilities are specific to a product and version, plus Firmware Implant. That may not be enough to clear §7.3, and if it does not, the entry should be cut rather than written. **CA-5.**

### 5.3 One inherited claim this document did not re-verify

`../client/04` §2 states that its mappings were "checked against a live source in this pass". This document **inherits** the statuses in §2.3, §2.9, §2.10 and §2.15 rather than re-deriving them, which is deliberate — re-deriving a status is how two documents come to disagree. The consequence is that if a `../client/04` §2 row is wrong, the corresponding entry here is wrong the same way. The three rows this domain depends on most are `compute` / `cycles` (§2.3), `Thermal Budget` (§2.3, §2.9) and `Bandwidth` (§2.9, §2.15), and all three were independently confirmed while writing §3.1, §3.14 and §3.17.

---

## 6. Open questions

Prefix **`CA-`**, which is unused across the doc set: `../design/15-open-questions.md` records `CL-`, `V-`, `PN-`, `SK-`, `T-`, `WL-`, `RI-`, `AX-`, the design set's `OQ-`/`P-`/`D-`/`S-`/`N-`/`E-`/`A-`/`G-`/`W-`/`Q-`, and `00-curriculum-and-method.md` adds `ED-`. Log these in `../design/15-open-questions.md` §2 if this document is adopted.

- **CA-1: ✅ RESOLVED with ED-3 (2026-07-25) — architecture is `02`, and this file was already right.** The contract's §1.4 now numbers this domain `02`, below operating systems and above foundations; representation moved out into `01-foundations.md`, which is what freed the number. No `domain:` field in this document needed re-stamping. ⚠ **One artefact of the old numbering survives and must be fixed before any term file is written:** the method document's own worked entry `process(7)` (`00-curriculum-and-method.md` §3.5) carries `domain: 02`, which meant *operating systems* under the superseded scheme and means *this document* under the resolved one. `process(7)` belongs to `03-operating-systems.md`, so that field now reads `03`. Tracked as **OS-11**, which already proposes moving the entry's body into `03` outright.
- **CA-2: `latency(7)` — this domain or networking?** §2.6 resolves it here under §1.4's primary rule (architecture can define it with no forward reference; networking cannot define it without borrowing a time scale). The escape hatch points the other way, because the player meets latency most visibly as hop distance on the `map` window. It is inventoried here and deliberately **not** written in full, so reassignment costs a table row rather than a page. Decide before either domain drafts it, since it is a prerequisite candidate in both.
- **CA-3: `compute(7)`'s shipped page already exists and its `gloss` is a fragment.** `../client/04` §4.9's `NAME` line reads "the rig's capacity budget; the master scarcity" — not a sentence. `00-curriculum-and-method.md` §3.2 requires the gloss to be "one sentence". §3.1 above writes it as one, which creates a diff against a page that is already drafted. Either the rule admits noun-phrase glosses (which is what real `whatis(1)` output looks like, and is arguably the better answer) or §4.9's page takes a `revision` bump. This will recur for every already-drafted page, so decide the rule rather than the instance.
- **CA-4: four entries here are unreachable from a `first-session` root**, failing §6.4 check 4: `bit-width(7)`, `latency(7)`, `firmware(7)` and `bandwidth(7)` have `prerequisites: none` and nothing depends on them within this domain. For `bit-width(7)` and `latency(7)` this is *correct in substance* — they are cited by entries in other domains, and check 4 as written only follows prerequisite edges, not `seeAlso`. **The check may be under-specified rather than the entries wrong.** Proposal: check 4 should read "reachable from a `first-session` root **or** cited in `seeAlso` by at least two entries in other domains". Needs deciding alongside **ED-4** (whether these checks are automated at all), because a hand-run check can absorb a judgement call and an automated one cannot.
- **CA-5: does `isa(7)` clear the hook test?** Its surface is `zero-day(1)`'s "specific to a product and version" caveat and the Firmware Implant, neither of which is squarely about instruction sets. §7.3 says an entry with no hook is not written, and the honest reading is that this one is borderline. Recommend: cut unless the security domain's `zero-day(1)` entry turns out to need it as a prerequisite, in which case it is theirs to request.
- **CA-6: Apple Silicon's cache line size — 64 or 128?** See §5.2. `cache-line(7)`'s transfer test tells the reader to run `sysctl hw.cachelinesize` and will print 128 on those machines; if the L1 line is in fact 64, the page should say why the two figures differ rather than leave a reader who checks with a contradiction. A reviewer with an M-series Mac and ten minutes closes this.
- **CA-7: `thermal-budget(7)` is the entry most exposed to balance drift.** `../design/01-core-resources.md` §1.3's recovery curve is explicitly `[PROPOSAL]` with numbers "for playtest", and this entry's `DESCRIPTION` states the shape ("slower the closer the rig sits to capacity") without stating a number, precisely so a tuning pass cannot falsify it. That is a deliberate defence against `../client/04` §6 **T-11** (semantic drift), and it is worth checking whether other domains have made the same choice. If any entry anywhere quotes a tunable value in its body, it will eventually be wrong.
- **CA-8: should `interrupt(7)`'s transfer test ship at all, given it is Linux-only?** `/proc/interrupts` has no macOS or Windows equivalent in comparable form, so under **ED-8**'s interim rule the test names its platform and leaves most players with nothing to run. The alternatives are weak: option (c) — target something universal — has no universal surface for interrupts. This is the clearest single case in this domain where ED-8's decision changes what gets written, and it is offered as evidence for whichever way ED-8 goes.
- **CA-9: `bandwidth(7)` is a `game` entry that exists because a stat is misnamed.** The page does honest work, but the cheaper fix is upstream: rename the rig stat. `../design/11-rig-infrastructure.md` §2 defines it as "the simultaneity cap", which is already a better name than the one it ships under. If the stat were renamed, this entry becomes `real, simplified` and gains a genuine counterpart (`RLIMIT_NOFILE`, worker pools) instead of a disclaimer. **A curriculum finding against the design, filed rather than acted on**, per `00-curriculum-and-method.md` §1.2 rule 1.
