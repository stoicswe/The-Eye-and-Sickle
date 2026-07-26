# 00 — Curriculum & Method

**Status:** ⚠️ **[PROPOSAL]** — the *goal* is Established and already load-bearing: client pillar **C6** ("the interface teaches the real thing", `../client/00-client-overview.md` §2), the stated product goal that "an average player should finish a session genuinely understanding a little more about how operating systems, networking and computation work" (`../client/00-client-overview.md` §5), and the falsifiable claim in `../client/04-terminology-and-education.md` §1.1. What this document *adds* — the entry template, the status decision procedure, the four progression stages, the coverage rules and the review requirement — is first-pass design. It is a **contract**: the seven domain curricula (§1.4) are written against it, and changing a field name here invalidates seven documents downstream.
**Depends on:** `../client/04-terminology-and-education.md` §1 (the principle), §2 (the vocabulary map), §4.1–4.5 (the three tiers), §4.8 (the content contract and file format), §4.9 (six worked entries) — **this document does not re-specify any of it**; `../design/glossary.md` (canonical terms and the uOS definition); `../client/00-client-overview.md` §3.3 (uOS is the operating system, not a skin name), §5 (the educational layer), pillar **C6**; `../../CLAUDE.md` (invariants and conventions)
**Depended on by:** `01-foundations.md`, `02-computer-architecture.md`, `03-operating-systems.md`, `04-the-command-line.md`, `05-networking.md`, `06-cryptography-and-trust.md`, `07-distributed-systems-and-identity.md`; and, through them, `client/src/main/resources/terms/**`

---

## 1. What this doc set is, and what it is not

### 1.1 Three layers, three owners

The educational feature has three distinct artefacts, owned by three different documents. Confusing them is the failure this section exists to prevent, because each confusion has a specific, expensive consequence.

| Layer | Artefact | Owner | Question it answers |
|---|---|---|---|
| **Mechanism** | Tiers, triggers, the gloss bar, the `man` window, teaching levels, decay, the file format, accessibility, localisation, the CI checks | `../client/04-terminology-and-education.md` | *How does a definition reach the player?* |
| **Curriculum** | The body of computing knowledge the game teaches: which concepts, in what order, corrected against which false beliefs, verified against which sources | **`docs/education/` — this doc set** | *What, exactly, are we teaching, and is it true?* |
| **Output** | `client/src/main/resources/terms/<locale>/<section>/<id>.md` | The `client/` module | *What ships?* |

The dependency runs one way and only one way:

```
docs/design/glossary.md          canonical game names
        │
docs/education/**                the curriculum — concepts, order, correctness   ← this doc set
        │
docs/client/04                   the delivery mechanism — tiers, format, CI
        │
client/src/main/resources/terms/**   the shipped files
```

### 1.2 The boundary, as rules

**This doc set may not:**

1. Re-specify tiers, triggers, dwell times, popover behaviour, teaching levels, decay, keyboard bindings, accessibility criteria or localisation. All of that is `../client/04`. If a curriculum decision seems to require a mechanism change, that is a finding to file against `04`, not a paragraph to write here.
2. Invent a header key, a body section name, or a status value. The shipped format's key set is closed (`../client/04` §4.8.2) and this document's template is a **superset** of it (§3), never a variant.
3. Contain the shipped prose verbatim as the deliverable. A curriculum entry is the *source* a shipped file is written from; it carries research, justification and ordering that the shipped file does not.

**The shipped term files may not:**

1. Contain a concept that no curriculum entry defines. A string in the resource tree that nobody designed is exactly how the game ends up teaching something false.
2. Silently diverge from the curriculum entry. When play testing forces a change to what a page says, the curriculum entry changes first and the shipped file follows.

**Nothing in `docs/education/` is code.** No file here is read at build time or at run time. It is the design record that makes the shipped content reviewable by a human who knows the subject — which is the only quality gate this feature actually has (§8.4).

### 1.3 Why the curriculum lives here and not in the resource tree

Three reasons, and they are the same three that put the design docs in `docs/` rather than in Javadoc.

- **Ordering is a property of the set, not of a file.** `prerequisites` and `stage` (§3) only mean something when the whole graph is visible in one place. A resource tree of 150 Markdown files cannot be read as a curriculum; seven documents can.
- **The justification has to survive.** The shipped file says a hash is not encryption. The curriculum entry says *why we bother* — that "hashing scrambles data" is a belief a large fraction of competent adults hold, and that dislodging it is worth more than three new facts. Delete the justification and the next writer, under deadline, deletes the sentence.
- **Review needs a target.** `../design/15-open-questions.md` escalates "who writes and reviews the term database" as a blocking product question, and `../client/04` §6 **T-12** budgets a practitioner pass. A reviewer can read a domain document in an afternoon. Nobody reviews a resource tree.

### 1.4 The seven domains

The body of knowledge splits seven ways. The split follows the stack, because the stack is also roughly the order in which a concept can be defined without forward references.

| # | Document | Owns | Anchoring game surface |
|---|---|---|---|
| **01** | `01-foundations.md` | What a computer is and how it represents things: bits, bytes, hexadecimal, base64; text encodings; how a number gets a width, and what happens when it runs out; unit prefixes; the difference between memory and storage; orders of magnitude; abstraction, and data-as-code | The compute readout, any hash on screen, storage tiers, file sizes |
| **02** | `02-computer-architecture.md` | How the machine actually executes: processors, cores and hardware threads; clocks and instruction cycles; caches and the memory hierarchy; interrupts; buses and I/O; heat, power and throttling | `rig-monitor`, Compute Cores, Thermal Budget, Memory Buffer |
| **03** | `03-operating-systems.md` | The kernel and why it exists; processes, PIDs and signals; scheduling and time-sharing; system calls; virtual memory and paging; files, paths, inodes and permissions; mounts and filesystems; daemons and services; logs; isolation — namespaces, containers, VMs | `audit`, `storage`, `botnet`, `defense`, `scan(8)` |
| **04** | `04-the-command-line.md` | The shell as a program; commands, arguments and flags; exit statuses; standard streams and pipeline syntax; globs vs. regular expressions; text as an interface; man pages and how to read one; quoting; job control | `terminal`, the command palette, the `man` window |
| **05** | `05-networking.md` | Addresses and what they identify; ports and sockets; packets, framing and MTU; the layer model, honestly; TCP vs. UDP; routing, hops and round-trip time; DNS; NAT; traffic metadata and why it leaks; TLS at the shape level | `map`, `recon`, Port Sweep, Relay Chain, Traffic Analyzer |
| **06** | `06-cryptography-and-trust.md` | Threat models; authentication vs. authorisation; hashing, salting and key derivation; symmetric and public-key encryption; signatures; randomness; trust anchors; the real attack classes the tools mirror | Intrusion tools, stealth tools, the Vault, `verify(1)`, the breach |
| **07** | `07-distributed-systems-and-identity.md` | Why many machines are harder than one; clocks and ordering; identity as keys rather than names; DIDs; hash chains and append-only logs; canonicalization; Byzantine faults and quorums; federation; ledgers and their publicness | `identity`, `ledger`, provenance, validator quorum, federation |

**The ownership rule, for concepts two domains could claim.** *A concept belongs to the lowest-numbered domain that can fully define it without forward-referencing a higher-numbered one.* A port can be defined without the shell, without crypto and without distributed systems, so it is `05`. A socket needs the operating system's file-descriptor idea, so it is `03`, and it cites `05`. A hash chain needs hashing, so `07` cites `06`.

**The escape hatch, because the rule will be ambiguous sometimes:** when the rule does not decide, the domain whose *game surface* the player meets the concept on owns the entry, and every other domain cites it in `seeAlso`. What is never permitted is two entries for one concept — a player who gets two answers stops trusting both.

#### Why seven, and not the six originally proposed

This section shipped a six-way split that named computer architecture as `01` and left representation — bits, bytes, encodings, integer width — as a clause inside it. Writing the domains found two faults with that, and both are the kind that only appear once somebody tries to use the split:

1. **Representation is a domain, not a clause.** It has eighteen entries of its own, every other domain depends on it, and it is the one domain that forward-references nothing. It became `01-foundations.md`, and architecture moved to `02`.
2. **The command line cannot sit late.** `03`, `06` and `07` all name `shell(7)` or `exit-status(7)` in `prerequisites`, so any numbering that puts the command line above them breaks the ownership rule outright. It keeps `04`, immediately above the operating system whose programs it runs and below everything that uses a command to reach a concept.

Networking, cryptography and distributed systems each shifted up one. The ordering rule is unchanged and now holds with **zero upward prerequisite edges**, which is check 5 in §6.4.

> **ED-3 is resolved** (2026-07-25), and the resolution is logged in `../design/15-open-questions.md` §3. What was never negotiable, and still is not: one entry per concept, one owner per entry, and the template in §3.
>
> ⚠ Two domains named in the original split are still homeless, and neither is dropped: **detection, logging and anti-forensics** (**CT-1**) and **why "hack back" is illegal** were listed under the old domain 05 and are not in `06-cryptography-and-trust.md`. The proposal on the table is an eighth document, `08-detection-and-defence.md`. Nothing else may claim them in the meantime.

---

## 2. The audience

### 2.1 The reader, written down

> **An intelligent adult, roughly 25 or older, who uses computers every day and possibly professionally, and who never studied computer science.** They have a job, limited time, and no patience for being managed. They have heard "RAM", "IP address" and "encryption", and they hold approximate models of all three that are often *nearly* right — which means our job is usually sharpening, not replacing.

This is not a beginner in the sense that word usually carries. A beginner in a children's book knows nothing. This reader knows a great deal, unevenly, from twenty years of using the things we are about to name. The specific failure to avoid is writing for a novice who does not exist and insulting the reader who does.

### 2.2 Assume / do not assume

| Assume | Do not assume |
|---|---|
| General competence and a working mental model of "files", "programs", "the internet" | Any computer-science education, at any level |
| That they have opened a terminal at least once, possibly under duress, and can be told to do it again | That they can program, in any language |
| Arithmetic, orders of magnitude, and the ability to read a number with a unit | Mathematics beyond arithmetic. No logarithms, no modular arithmetic, no proofs |
| Professional vocabulary from *their* field, and the ability to learn ours if we define it | Hexadecimal, binary notation, pointers, registers, protocol stacks, or big-O |
| That they will notice if we contradict ourselves | That they will look anything up. Everything they need is on the page |
| That they have a strong opinion about at least one thing we are about to correct | That the opinion is wrong. Sometimes they are right and our simplification is the defect |

### 2.3 The register, as rules

**Never:**

- Childish framing. No "imagine your computer is a busy kitchen", no anthropomorphised components, no "the CPU asks politely".
- Exclamation marks. Not one, anywhere in the shipped content or in a curriculum entry's prose.
- "Magic", "under the hood" as a substitute for an explanation, "basically", or "just" used to make a hard thing sound easy.
- Forced whimsy, winks, jokes, or a narrator. `../client/04` §1.5 already forbids a second voice; this is that rule at sentence level.
- Padding. No sentence that restates the previous one at greater length. The 400-word body ceiling (`../client/04` §4.8.3 rule 7) is generous only if nothing is wasted.
- Praise for the reader ("great question", "you've probably already guessed"). They did not ask, and they had not guessed.

**Always:**

- **Define the term precisely on first use**, in the same sentence or the next one. If a definition needs a term we have not defined, define that one first or restructure.
- **Give the real number or the real scale.** 16 bits. 4 KiB. Roughly 100 nanoseconds. 0 to 65535. Scale is where intuition actually forms; "fast" and "large" form none.
- **Prefer the correct word to a softened one, then explain the correct word.** Write "kernel", not "the core part of the system"; then say what a kernel is. A reader who leaves with the right word can look it up for the rest of their life. A reader who leaves with our euphemism cannot.
- **Say what follows from the fact.** A fact with no consequence is trivia. "Ports are 16 bits" earns its place because it explains 65535, and 65535 explains why a full scan is slow.
- **Name the mechanism, not the vibe.** `nmap(1)` is a counterpart; "like a hacker would" is not (`../client/04` §2.1).

### 2.4 Written badly — the three failure modes

Three ways to get it wrong, one concept each, then the same four written correctly in §2.5. **This pair of sections is the register a writer copies.**

| Concept | Mode | The text | The defect |
|---|---|---|---|
| **system call** | Patronising | "A system call is like your program raising its hand and asking the operating system — the grown-up in charge — if it can please have a turn with the disk." | Casts an adult as a child, and the metaphor leaves a false model behind: nothing is taking turns, and the kernel is not an authority granting favours, it is code running in a more privileged processor mode. |
| | Vague | "System calls are how programs interact with the operating system." | True and useless. It names a relationship and no mechanism, no cost, and nothing checkable. The reader finishes knowing exactly what they knew. |
| | Jargon-dump | "A syscall traps into ring 0 via the `SYSCALL` instruction, switching to the kernel stack and dispatching through `sys_call_table` on the number in `RAX`." | Every word correct, none of it lands. It spends four unexplained terms — rings, traps, kernel stack, dispatch table — to explain one. |
| **port** | Patronising | "If your computer is an apartment building, ports are the numbered doors — and a port scanner is just someone walking down the hall jiggling handles!" | The exclamation mark, and an analogy doing all the work while quietly implying the doors are physical and few. It never says the one fact that makes ports usable: the number is 16 bits wide. |
| | Vague | "A port identifies a particular service on a computer." | *Nearly* right, which makes it worse than wrong. The reader still cannot say why there are 65,536 of them, why two programs cannot normally wait on the same one, or why the port belongs to the conversation rather than to the machine. |
| | Jargon-dump | "Ports are 16-bit unsigned integers in the TCP and UDP headers, used for transport-layer demultiplexing to a socket bound to the connection 4-tuple." | Correct, complete, unreadable. "Demultiplexing" and "4-tuple" were the two things that needed explaining. |
| **memory hierarchy** | Patronising | "RAM is your computer's short-term memory and the disk is its long-term memory — just like you!" | The most common analogy in the field, and it leaves a false model: human memory has no hundred-thousand-fold speed cliff at a fixed boundary, and "short-term" explains nothing about why adding RAM sometimes changes nothing at all. |
| | Vague | "RAM is much faster than disk, so the computer keeps what it needs in RAM." | Directionally true, quantitatively empty. Without the ratio the reader can predict nothing, and prediction is the only reason to know this. |
| | Jargon-dump | "The hierarchy runs L1/L2/L3 SRAM, DRAM over DDR5, then NVMe over PCIe, with inclusive and exclusive policies and hardware prefetchers hiding latency under sequential access." | An inventory, not an explanation. It lists every level and omits the only thing that matters: each step down is roughly two orders of magnitude slower. |
| **hash function** | Patronising | "A hash is like a fingerprint for your data — no two are ever the same!" | The analogy is decent and the sentence ruins it. "No two are ever the same" is *false* — collisions must exist by counting alone — and the exclamation mark tells an adult they are being sold something. |
| | Vague | "Hashing scrambles data so it cannot be read." | The single most damaging sentence in this domain, because it merges hashing with encryption. A reader who believes it will later conclude that a leaked password database is safe because it was "scrambled". |
| | Jargon-dump | "SHA-256 is a Merkle–Damgård construction over 512-bit blocks with 64 rounds, producing a 256-bit digest with 128-bit collision resistance under the birthday bound." | Accurate and inert. The reader wanted to know what a hash is *for*. |

### 2.5 The same four, written correctly

> **system call**
>
> A **system call** is a program asking the operating system to do something the program is not allowed to do itself. Reading a file, sending a packet and asking for more memory are all system calls. Arithmetic is not. The rule is simple: anything that touches hardware, or touches another program, goes through the kernel.
>
> The reason is protection. An ordinary program cannot address the disk directly — if it could, any program could read any file and permissions would be a suggestion. So the program puts a number identifying its request into a **register** (one of a few dozen tiny storage slots inside the processor itself) and runs a single special instruction that hands control to the kernel. The kernel checks the request, does the work, and hands control back.
>
> Linux has a few hundred system calls; a normal program leans on a handful — `read`, `write`, `openat`, `close`. The switch is not free. It costs on the order of a hundred nanoseconds, and several hundred on a machine with the usual processor-vulnerability mitigations enabled, against roughly one nanosecond for an ordinary function call. That is why a program making millions of tiny reads is slow for a reason that is hard to see in a profiler, and why buffering exists at all.

> **port**
>
> A **port** is a number from 0 to 65535 that says which program on a machine a piece of network traffic is meant for. It is 16 bits wide, which is exactly where 65535 comes from.
>
> A machine has one address and may be running twenty programs that all want network traffic. The address gets the data to the machine; the port decides which program receives it. A web server usually waits on port 443 and an SSH server on 22 — conventions, not laws, which is why moving SSH to port 2222 works, and also why it hides nothing from anyone who checks.
>
> Normally only one program can wait on a given port at a time, which is why starting a second copy of a server fails with "address already in use". (Modern systems have a deliberate exception so that several worker processes can share one port, but exclusive is the default.) A **port scan** is asking a machine, one port at a time, whether anything answers — and every one of those questions arrives at the target, where it can be logged.

> **memory hierarchy**
>
> A computer keeps the same data in several places at once, and the places differ enormously in speed. The numbers are the lesson:
>
> | Where | Roughly how long to fetch |
> |---|---|
> | L1 cache, inside the processor core | 1 nanosecond |
> | main memory (RAM) | 60–100 nanoseconds |
> | a small random read from an SSD | 50–100 microseconds |
> | a round trip across the Atlantic | ~100 milliseconds |
>
> Each line is roughly a hundred to a thousand times slower than the one above it. From the processor's point of view, waiting on RAM wastes a hundred opportunities to do work; waiting on the SSD wastes a hundred thousand.
>
> This is why a machine that has run out of memory is not slightly slow but *pathologically* slow: it has started reaching for storage on the ordinary path. It is also why "more RAM" stops helping the moment there is enough. Memory is not a speed dial. It is a cliff you either fall off or you do not.

> **hash function**
>
> A **hash function** turns any amount of data into a fixed-size number. SHA-256 always produces 256 bits, whether it is given one character or a feature film. The same input always produces the same output, and changing one character produces a completely different output.
>
> It is not encryption, and this is the distinction worth keeping. Encryption is built to be undone by whoever holds the key. A hash is built so that nothing undoes it: there is no key, and the output is far smaller than the input, so the original is not in there to recover.
>
> Two different inputs can share a hash — there are more possible files than there are 256-bit numbers, so collisions must exist. A hash function is considered good when nobody can *find* one on purpose. That is what "MD5 is broken" means: people can now construct collisions deliberately, not that MD5 stopped producing output.
>
> Most of what hashes are used for follows from this: checking that a file arrived unaltered, linking records into a chain so that changing an old one changes every identifier after it, and storing passwords in a form that cannot simply be read back — though a bare hash is not enough for that last one, which is what salting and deliberately slow functions are for.

### 2.6 Analogies

Permitted, under three conditions, all three required:

1. **Load-bearing.** It replaces an explanation that would otherwise be much longer or much harder. An analogy that decorates an explanation already given is padding.
2. **Accurate in the mapping it makes.** Every element that maps, maps correctly.
3. **It states where it breaks.** In the same paragraph, not a footnote. "This is a good picture of X and a bad picture of Y" is the required shape.

The reason condition 3 is absolute: `../client/04` §1.1 rules that mis-teaching is worse than not teaching, and an analogy that leaves a false model behind is a wrong mapping with better manners. The apartment-building analogy for ports fails on 3 — nothing in it tells the reader that the doors are numbers rather than places, so a reader will confidently conclude that a port is somewhere on the machine.

A useful test: **write the paragraph without the analogy first.** If it is still clear, delete the analogy. If it is not clear, the analogy is doing real work — keep it and add the breaking point.

### 2.7 Scale, and why every entry owes a number

The single highest-value habit in this doc set. Adults form intuitions from ratios, and almost every important fact in computing is a ratio:

- A port is **16 bits** → 65,536 of them → a full scan is 65,536 questions → scans are slow and loud.
- RAM is **~100×** slower than cache and SSD is **~1000×** slower than RAM → caching is not an optimisation, it is the design.
- A memory page is **4 KiB** on ordinary hardware → reading one byte from a file moves at least 4,096 → "small reads are cheap" is false.
- An IPv4 address is **32 bits** → about 4.3 billion → the shortage is arithmetic, not politics.
- A SHA-256 output is **256 bits** → collisions exist and nobody has found one on purpose.

Where a number exists, give it. Where a number is contested or hardware-dependent, give the range and say what it depends on. Where no honest number exists, say that too — "this varies by orders of magnitude depending on the network" is information; "it depends" is not.

---

## 3. The entry template — **the contract**

### 3.1 What it is for

Every concept in every domain document is written as one entry in this exact shape. The shape does three jobs at once:

- It is a **superset of the shipped file format** (`../client/04` §4.8.2), so producing the shipped file is a matter of deleting fields, never of re-deciding anything.
- It carries the five fields a *curriculum* needs and a shipped file does not — `prerequisites`, `stage`, `hook`, `misconception`, `transfer` — which are what make ordering, coverage and the falsifiable claim checkable rather than aspirational.
- It makes an entry **rejectable**. Three fields are veto gates: an entry with no `hook` is not in the curriculum (§7.3), an entry with no `transfer` is decoration (§3.4), and a `real, simplified` entry with no `simplified` has not understood its own simplification (§4.3).

### 3.2 The field list

| Field | Required | Ships? | Rule |
|---|---|---|---|
| `id` | yes | **ships** | Lowercase, hyphenated, unique within its section. The shipped filename must match (`../client/04` §4.8.2) |
| `section` | yes | **ships** | One of 1, 5, 6, 7, 8. Assignment rule in §5 |
| `name` | yes | **ships** | Display name |
| `canonical` | yes ⚠ | **ships** | Byte-for-byte the term as written in `../design/glossary.md` where it exists there; otherwise the curriculum's own canonical spelling. ⚠ `../client/04` §4.8.2 makes this conditional; this doc set requires it unconditionally — see **ED-1** |
| `gloss` | yes | **ships** | One sentence, ≤ 72 characters, plain language, must not contain the word it defines |
| `status` | yes | **ships** | `real` \| `real, simplified` \| `game`. Decision procedure in §4 |
| `aliases` | no | **ships** | Alternate spellings `apropos` should resolve |
| `glossary` | no | **ships** | Path to the glossary, where the term has an entry there |
| `seeAlso` | yes | **ships** | `name(section)` refs. Every one must resolve |
| `reading` | no | **ships** | Tier-3 citations: RFCs, `man` pages, standards, named incidents (`../client/04` §4.4) |
| `notes` | no | **ships** | Writer and translator guidance. Not rendered. **Mandatory** for every homonym in `../client/04` §2.15 |
| `revision` | yes | **ships** | Integer, incremented on any body change |
| **`domain`** | yes | curriculum only | Which of the seven documents (§1.4) owns this entry |
| **`stage`** | yes | curriculum only | `first-session` \| `operating` \| `investigating` \| `adversarial` (§6) |
| **`prerequisites`** | yes | curriculum only | `name(section)` refs that must be understood first. `none` is a legal and meaningful value |
| **`hook`** | yes | curriculum only | The specific game surface or moment where the player meets this. **Veto gate** |
| **`misconception`** | yes | curriculum only | `commonly believed X; actually Y`. `none known` is legal but must be justified in review |
| **`transfer`** | yes | curriculum only | What the player can now do in a real terminal or real life. **Veto gate** |
| **`simplified`** | conditional | curriculum only | Required iff `status: real, simplified`. Names what was abstracted away; becomes the first sentence of the shipped `## CAVEATS` |
| **`verified`** | yes | curriculum only | Source per factual claim, plus the date checked. **The review record** (§8.4) |

**Body sections**, using the names fixed by `../client/04` §4.3.1 and no others:

| Section | Present on |
|---|---|
| `## SYNOPSIS` | sections 1, 5, 8 only — **never** on a section-7 concept page, and the absence is deliberate (§5.2) |
| `## DESCRIPTION` | every entry. Game-first: answer what the player was looking at, then generalise |
| `## OPTIONS` | sections 1, 8 |
| `## EXIT STATUS` | sections 1, 8. Only statuses this command can actually produce |
| `## REAL-WORLD COUNTERPART` | every entry. Opens with the status word, then names the artefact |
| `## CAVEATS` | mandatory on every `real, simplified` entry and every `../client/04` §2.15 homonym |

`## NAME`, `## SEE ALSO` and `## FURTHER READING` are **not written by hand**: the renderer builds them from `name` + `gloss`, from `seeAlso`, and from `reading` respectively. One source, one surface — this is why the gloss bar and a page's `NAME` line can never disagree.

### 3.3 The template

```
id:             <lowercase-hyphenated>
section:        <1|5|6|7|8>
name:           <display name>
canonical:      <byte-for-byte glossary spelling, or the curriculum's own>
gloss:          <one sentence, <= 72 chars, does not contain the word it defines>
status:         <real | real, simplified | game>
aliases:        <comma-separated, or omit>
glossary:       <../design/glossary.md, or omit when the term is curriculum-owned>
seeAlso:        <name(section), name(section), ...>
reading:        <citation | citation | ...>
notes:          <writer/translator guidance, or omit>
revision:       <integer>

--- curriculum only, stripped before shipping ---

domain:         <01..06>
stage:          <first-session | operating | investigating | adversarial>
prerequisites:  <name(section), ... | none>
hook:           <the exact surface or moment where the player meets this>
misconception:  commonly believed <X>; actually <Y>
transfer:       <the concrete thing the player can now do outside the game>
simplified:     <required iff status is "real, simplified": what was abstracted away>
verified:       <claim — source; claim — source; checked YYYY-MM-DD>

## SYNOPSIS          (sections 1, 5, 8 only)
## DESCRIPTION       (always; game-first)
## OPTIONS           (sections 1, 8)
## EXIT STATUS       (sections 1, 8)
## REAL-WORLD COUNTERPART   (always; opens with the status word)
## CAVEATS           (mandatory when status is "real, simplified", or the term is a §2.15 homonym)
```

### 3.4 The five curriculum-only fields, and why each exists

**`prerequisites`** — *what makes ordering possible.* Without it, "progression" is an assertion. With it, the curriculum is a directed graph that can be checked: acyclic, rooted at `first-session`, every reference resolving. It also enforces something subtler, stated as a rule in §6.3: a gloss or a `DESCRIPTION` may not use a term whose entry sits at a later stage. A one-sentence gloss that depends on a concept the player has not met is not a gloss, it is a second thing to look up.

**`stage`** — *when in the game's progression it is first taught.* A claim about the player's situation, not a lock; every page remains reachable through `man` at any time (`../client/04` §4.6). Its real job is to stop the curriculum from front-loading. See §6.

**`hook`** — *the specific surface or moment where the player meets it.* Not "the terminal" but "the first time `ps` output and the rig monitor's total disagree". The hook is what makes the entry findable at the moment it is wanted, and its absence is the cleanest possible signal that a concept does not belong in this game's curriculum however interesting it is (§7.3).

**`misconception`** — *the wrong belief this entry must dislodge.* **For this audience, the highest-value field in the template.** An adult does not arrive empty. They arrive with confident approximate models built from twenty years of using the thing, and the models have specific, predictable failure points. Correcting one is worth more than adding three facts, because the false belief was actively producing wrong predictions and the three facts were inert. It also, usefully, tells the writer what the entry is *for*: an entry that cannot name a misconception is often an entry with no reason to exist, and §8.1 exploits that by making this the field a writer fills first.

The stated form is `commonly believed X; actually Y`, and both halves are load-bearing. §8.2 requires the "commonly believed" half to be a belief that people actually hold, evidenced, not one invented to give the entry a hook.

**`transfer`** — *the concrete thing the player can now do in a real terminal or in real life.* This is `../client/04` §1.1's falsifiable claim applied per entry:

> *Take any term the interface shows a player, and ask what they would now be able to do in a real shell. If the answer is "nothing," the term is decoration.*

Written as an imperative with a checkable result, so a reviewer can actually run it: `Run 'ps -e | wc -l' on any Mac or Linux machine and say how many processes it is running.` Not `Understands processes better.` **An entry whose `transfer` cannot be filled is deleted, not shipped** — with one narrow and honest exception: a `game`-status entry's transfer is *recognising the fiction as fiction*, phrased as such (`Can say, correctly, that nothing in a real network behaves like this, and name what does`).

### 3.5 A fully worked entry

Chosen deliberately: `process(7)` is a section-7 concept, it is `real`, it sits at `first-session`, and it is the prerequisite of roughly a third of `02`.

```
id:             process
section:        7
name:           process
canonical:      process
gloss:          One running instance of a program, with its own memory and id.
status:         real
aliases:        task, pid
seeAlso:        ps(1), kill(1), compute(7), system-call(7), daemon(7),
                rootkit-wrapper(8)
reading:        ps(1); proc(5) — the /proc filesystem on Linux; signal(7)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         03
stage:          first-session
prerequisites:  compute(7)
hook:           The audit window's process view, and the first time the player
                runs `ps` to find out what is holding their cycles. Also the
                moment a foreign miner appears in that list.
misconception:  commonly believed a program and a process are the same thing,
                so closing an application's window ends everything it was
                doing; actually a program is a file sitting in storage and a
                process is one running instance of it — one program can be many
                processes at once, and a process can have no window at all.
transfer:       Open a terminal on macOS or Linux and run `ps -e | wc -l` (one
                more than the process count — the first line is a header), then
                `ps aux | sort -rnk 3 | head`. The player can now say roughly
                how many processes their own laptop is running and which is
                using the most CPU, and can recognise that Activity Monitor and
                Task Manager are showing the same list with a window around it.
                Assumes a Unix shell — see ED-8.
verified:       program/process distinction and PID allocation — Linux
                man-pages fork(2), credentials(7); SIGKILL cannot be caught,
                blocked or ignored — signal(7); per-site process model —
                Chromium "Site Isolation" documentation. Checked 2026-07-25.

## DESCRIPTION

Everything running on your rig is a process: each bot instance, each armed
defense, the control channel for every deployed miner — and the miner a
stranger left behind. The audit window lists them; `ps` prints the same list as
text.

A process is not a program. The program is the thing sitting in storage, inert.
A process is one running instance of it, with its own slice of memory, its own
state, and its own number — the process id. Start the same program twice and
you have two processes that cannot see into each other's memory.

That distinction is the whole reason auditing works. A foreign miner is a
process. It has to be, because it is running. It can be hidden from a routine
listing — see rootkit-wrapper(8) — but it cannot both run and not exist, which
is why two different views of the same rig eventually disagree, and why the
disagreement is what gives it away.

## REAL-WORLD COUNTERPART

real — processes, exactly as the reader's own computer has them.

An ordinary laptop is running somewhere between two hundred and a thousand
right now. On macOS or Linux, `ps` prints them, `ps -e | wc -l` counts them
(plus one for the header line), and `top` shows which are busy. Activity
Monitor and Task Manager are the same list with a window around it.

Each process has a process id — a PID, a small integer the kernel hands out.
`kill` sends a signal to one by PID, and despite the name most signals are
requests rather than executions: the default asks a process to shut down
cleanly and it may decline, while `kill -9` sends the one signal a process
cannot catch, block or ignore.

A browser is the clearest everyday case. Modern browsers run a separate process
per site rather than one for the whole application, so that a crash — or a
compromise — in one tab cannot reach the others. That is the same isolation
argument this game makes for the Isolated Partition.
```

Three things this example is demonstrating on purpose:

- **No `## CAVEATS`, because `status` is `real`.** uOS's process model does not misrepresent a real one. What *is* simplified — that uOS reserves capacity where a real scheduler time-shares it — belongs to `compute(7)`, which already carries it (`../client/04` §4.9). **Never repeat another entry's caveat; point at it in `seeAlso`.** Duplicated caveats drift, and a drifted caveat is a page that contradicts another page.
- **No `## SYNOPSIS`.** Section 7 pages do not get one (§5.2), and the absence itself teaches the section system.
- **`canonical: process` with no `glossary:` line.** `process` is a real computing concept the game surfaces, not a game-design noun, so it is not in `../design/glossary.md`. This is the third class of term that `../client/04` §4.10's orphan check does not currently anticipate — **ED-1**.

### 3.6 What happens at ship time

The curriculum entry is the source; the shipped file is generated from it by deleting the eight curriculum-only fields and rendering `## NAME`, `## SEE ALSO` and `## FURTHER READING` from the header. Nothing is re-decided and nothing is re-worded in transit. Whether that deletion is a build step or a manual step is **ED-2**; what is not open is the direction — the curriculum entry is upstream, always.

---

## 4. The status vocabulary

### 4.1 The three markers

Exactly the three fixed by `../client/00-client-overview.md` §5.3 and elaborated in `../client/04` §2.1. No fourth value, no "mostly real", no hedge.

| `status` | Means |
|---|---|
| `real` | The concept is genuine and the game's model does not misrepresent it |
| `real, simplified` | Genuine concept, deliberately abstracted. The abstraction is named, in the entry's own `simplified` field and in its `## CAVEATS` |
| `game` | A construct of this fiction. The nearest real idea is named as an *adjacency*, never as an equivalence |

### 4.2 The decision procedure

Answer in order; the first yes wins. This ordering is deliberate — it biases toward the honest label, because every failure mode of this system is a page claiming to be more real than it is.

1. **Does the game's version contradict the real thing in a way a player would carry into a real system and get wrong?** → `real, simplified` at best, and possibly the mechanic is a defect rather than a simplification. Escalate before writing (`../client/00-client-overview.md` §3.3: uOS may extend Unix, never contradict it).
2. **Is there a specific real artefact — a command with a section number, an RFC, a named standard, a named technique, a named incident — that this *is*?** If no artefact can be named, it is `game`. "Something like this exists in industry" is not an artefact.
3. **Does uOS inherit it from Unix, or does uOS's *world* add it?** Inherited → `real` or `real, simplified`. Added by the world → `game`. This is `../client/04` §1.1a's test and it is much easier to answer than "does this feel real".
4. **Would a practitioner reading our description wince at anything beyond brevity?** Wince → `real, simplified`, and whatever made them wince is the `simplified` field.
5. **Does explaining the mapping honestly require more than two qualifications?** → `real, simplified` at best. Three qualifications means the mapping is doing less work than the qualifications are.
6. Otherwise → `real`.

**The downgrade bias, stated as a rule:** *when two labels are defensible, take the more modest one.* The cost of labelling something `real, simplified` that was actually `real` is a slightly over-cautious caveat. The cost of the reverse is a player who learned something false and has no way to know it. These are not comparable costs, so the tie does not go to the flattering answer.

### 4.3 `real, simplified` must say what was simplified away

Not "this is a simplification". *What* was removed, in the first sentence, concretely enough that a reader could go and find the missing part.

| Not acceptable | Acceptable |
|---|---|
| "The game simplifies how this works." | "Real CPUs time-share: two processes each asking for the whole machine both run, each more slowly. This rig reserves instead — if it is allocated, nobody else gets it." |
| "Real rainbow tables are more complicated." | "A real rainbow table is a space–time trade-off over precomputed hash chains, and it fails completely against per-password salts, which is why modern password storage uses them along with deliberately slow functions." |
| "Real networks are messier." | "`traceroute` measures one path at one moment. It does not draw a graph — a map is an accumulation of many such measurements, and it is out of date the moment routing changes." |

The test is mechanical: **a reader who has only the caveat should be able to state the true version.** A page that cannot articulate its own simplification has not understood it (`../client/04` §4.8.3 rule 4), and there is no way to write a good `## CAVEATS` around a simplification the writer has not identified.

### 4.4 `game` entries in a computing curriculum

A `game` entry is not a coverage entry. It exists for **honesty**, not for completeness: a `game` page is written when a real-looking word would otherwise be mistaken for a real concept. `noise(7)` and `heat(7)` get pages because they sit next to genuine detection vocabulary and would be absorbed as real. `factionReputation` does not need a computing entry at all — nobody will mistake faction standing for a protocol.

Consequence for the domain documents: **most of a domain's entries should be `real` or `real, simplified`.** A domain document whose entries are largely `game` is not a computing curriculum, it is lore, and the concepts it names belong in `../design/` instead. `../client/04` §2.14 already fixes a closed list of the game's own fiction; the curriculum does not extend it.

---

## 5. Man-section assignment

### 5.1 The real sections

The game uses a subset of the real Linux `man` section numbering (`man-pages(7)`), which is why the numbering teaches something on its own:

| § | Real meaning | Used here |
|---|---|---|
| 1 | User commands | yes — everything the player can type |
| 2 | System calls | **no** — see §5.3 |
| 3 | Library functions | **no** |
| 4 | Devices and special files | **not yet** — see §5.3 |
| 5 | File formats and conventions | yes — record shapes the player reads |
| 6 | Games | yes — exactly one page: `eyeandsickle(6)` |
| 7 | Overview, conventions, miscellany | yes — **most of this curriculum** |
| 8 | System administration commands | yes — administering the player's own rig |

### 5.2 The assignment rule

Answer in order; first yes wins.

1. **Is it the shape of a record the player reads?** → **5**. `provenance-record(5)`, `ledger-entry(5)`.
2. **Can the player type it as a command?**
   - **Yes, and it acts on someone else's machine** → **1**.
   - **Yes, and it administers the player's own rig** → **8**.
   The offense/defense split is not decoration; it mirrors exactly how user commands and system-administration commands split in reality, and `../client/04` §3.10 already assigns every command in the catalogue this way.
3. **Is it the root page?** → **6**.
4. **Otherwise it is a concept, a unit, a protocol, a standard, or a piece of vocabulary** → **7**.

**Most curriculum entries are section 7**, and that is correct rather than a fallback. A curriculum teaches concepts; commands are how a few of those concepts are reached.

**Section 7 pages have no `SYNOPSIS`**, exactly as real section-7 pages usually do not. Do not add one to make a page look complete. The absence is one of the things the player learns about the section system, and it is free.

### 5.3 The cases that will come up

- **A concept and a command share a name.** Write both. `ps(1)` says how to run it here and what its output means; `process(7)` says what a process *is*. Cross-reference each way. Never fold a concept into a command page — the concept outlives the command, and a player who leaves the game keeps the concept.
- **Section 2 is tempting and wrong.** A curriculum entry about system calls is `system-call(7)`, not `read(2)`. Sections 2 and 3 document *programming interfaces* for people writing code against them, and this game has no programming surface. The player is learning that system calls exist and what they cost, not how to invoke one. Using section 2 would teach the section system incorrectly, which is a small lie in service of looking authentic — the exact trade this doc set refuses.
- **Section 4 is reserved, not banned.** If uOS ever renders a device tree the player addresses by path, device concepts move to 4 and gain a real teaching payload. Until then they are section 7. Stated so that nobody either invents section 4 early or forgets it is available.
- **Real formats the game describes but does not render** — a syslog line, an HTTP request — are **7**, not 5. Section 5 is for formats the player actually reads on screen. If the game later renders one, it moves, and that is a `revision` bump plus a `seeAlso` fix, not a rewrite.

---

## 6. Progression

### 6.1 Why stages and not levels

A number ("level 3 content") would be arbitrary and would immediately be gamed into a difficulty curve. A stage is defined by **what the player is doing**, which is checkable against the design docs and stays true even when pacing changes. It also produces the right instinct in a writer: the question is never "is this advanced?" but "does the player have a reason to want this yet?"

The teaching layer already decays on its own — a term settles after three sightings or one page open (`../client/04` §4.5). Stages govern something different and complementary: not how loudly a concept is offered, but **whether it exists to be offered at all**.

### 6.2 The four stages

| `stage` | The player is… | What arrives | Typical entries | Budget |
|---|---|---|---|---|
| **`first-session`** | Sitting in front of a rig monitor and a terminal with one target and no idea what anything costs. Learning that actions have prices and that the prices are visible | **Only what is needed to act and to understand the result.** Compute as a budget; a process as a thing that holds some of it; a command as a verb with flags; a port as a number; that a status of 0 means it worked | `compute(7)`, `process(7)`, `port(7)`, `shell(7)`, `exit-status(7)`, `flag(7)`, `man(1)` | **≤ 12 entries across all six domains.** A hard ceiling, not a target |
| **`operating`** | Running tools, allocating compute, reading tool output, self-mining, deploying, taking the first real losses | The mechanisms behind the prices. Why a scan is slow; what a service listening means; what the filesystem is; what "the process is still running" means; what a log line is | `system-call(7)`, `filesystem(7)`, `packet(7)`, `daemon(7)`, `log(7)`, `permissions(7)`, `latency(7)` | ~25–40 |
| **`investigating`** | Owning things worth defending. Auditing their own rig, cross-checking views, finding a discrepancy, hardening, detecting | **Depth, on demand, because the player now has a question.** Why two views of the same machine disagree; what hiding actually costs; virtual memory; isolation; how detection really works; traffic metadata | `virtual-memory(7)`, `namespace(7)`, `cross-view-detection(7)`, `flow-metadata(7)`, `integrity-monitoring(7)`, `hash(7)` | ~30–50 |
| **`adversarial`** | Dealing with other people and other servers, where nobody is trusted and outcomes must be *proved* | Everything that only matters once trust is a problem: keys as identity, signatures, hash chains, canonicalization, clocks and ordering, Byzantine faults, quorums, why a public ledger is public | `public-key(7)`, `signature(7)`, `provenance-chain(7)`, `canonicalization(7)`, `did(7)`, `quorum(7)`, `equivocation(7)` | ~25–40 |

Rough total: **100–140 full entries**, 20–35 per domain document. That is a planning figure, not a quota — a domain that honestly needs 18 should ship 18 (§7.1).

### 6.3 The sequencing rules

**R1 — A concept is never introduced before its prerequisites.** The graph formed by `prerequisites` is acyclic, and every entry's `stage` is at or after the latest stage among its prerequisites. A cycle is a design error, not a formatting error: it means two entries are each assuming the other.

**R2 — The first session teaches the minimum needed to act.** Twelve entries, maximum, across all seven domains combined. This is the rule most likely to be argued with, so: the failure mode of a generous first session is not "too much information", it is a player who stops reading everything, permanently, in the first twenty minutes — after which the other 120 entries are unreachable no matter how good they are.

**R3 — Depth arrives when the player has a reason to want it.** A concept is placed at the stage where the player first *needs* it, not where it is first mentioned. Compute is mentioned in the first minute and thermal throttling is mentioned alongside it; throttling is `operating`, because until the player has overextended once, the explanation answers a question nobody asked.

**R4 — No forward references in a gloss or a `DESCRIPTION`.** An entry may not lean on a concept at a later stage. This is mechanically checkable against `prerequisites` and it is the rule that makes the 72-character gloss possible at all: a gloss that needs a second lookup has failed.

**R5 — The game never gates progress on reading.** Absolute, and already binding: `../client/00-client-overview.md` §5.4 and `../client/04` §4.7 forbid it in the mechanism; §4.7 makes it structural by ensuring no teaching-layer state is ever an input to any intent the client sends. The curriculum inherits the same ban in a form of its own: **no entry may be written as though the player has read a previous entry.** `prerequisites` orders the *offering*, never the access. Every page must stand alone for a player who arrived at it from search at three in the morning, which is exactly how real man pages are read.

**R6 — One concept per entry.** If the `gloss` needs an "and", it is two entries. `../client/04` §4.8.3 rule 7 caps a body at 400 words; hitting the cap is the symptom, not the disease.

**R7 — Stage is a claim about the player, not a lock.** Every page is reachable through `man`, search and the index at every teaching level including `off` (`../client/04` §4.6). Nothing is hidden, ever. A player who wants `quorum(7)` in their first ten minutes gets `quorum(7)`.

**R8 — Prerequisites may not cross domains upward.** An entry in `01` may not require an entry in `05`. The domain ordering in §1.4 is chosen so this is achievable; if an entry cannot satisfy it, the entry is probably in the wrong document.

### 6.4 What the graph must satisfy

A domain document's entry set is checkable, and these are the checks:

1. Every `prerequisites` reference resolves to an entry that exists somewhere in the doc set.
2. The graph is acyclic.
3. Every entry's `stage` is ≥ the maximum `stage` of its prerequisites.
4. Every entry is reachable from at least one `first-session` root by following prerequisites backwards. An unreachable entry means the curriculum expects a concept to arrive from nowhere.
5. No `prerequisites` edge points from a lower-numbered domain to a higher-numbered one (R8).

These are the curriculum's equivalent of `../client/04` §4.10's three CI checks. They are not automated today, and whether they should be is **ED-4**.

---

## 7. Coverage and honesty

### 7.1 When a domain is adequately covered

Coverage is not a word count and not a percentage of a syllabus we did not write. A domain document is done when all five hold:

1. **Every game surface the domain touches has a teachable entry.** Derived mechanically: walk `../client/04` §2's tables for rows in this domain, walk §3.10's command catalogue, walk `../client/00-client-overview.md` §6.1's window list. Every one either has an entry or has a written reason why not.
2. **Every entry's `prerequisites` resolve, and §6.4's five graph checks pass.**
3. **Every entry has a filled `transfer`.** No exceptions; §3.4's `game`-status form counts.
4. **Every entry's `hook` names a surface that actually exists** in the design or client docs, cited with a section anchor. A hook pointing at a feature nobody has designed is a promise, not a hook.
5. **The status distribution is published** at the head of the domain document — how many `real`, how many `real, simplified`, how many `game`. Because the `man` window offers a status filter (`../client/04` §4.6), a player can already ask "what did this game make up?" and get an answer; the domain doc should be able to answer the same question before the content ships.

### 7.2 A concept the game *uses* must be teachable

**If a mechanic depends on a concept, that concept has an entry.** This follows directly from pillar C6 and from `../client/04` §1.1: a game that models cross-view detection faithfully and then never explains what a process is has built the expensive part and skipped the cheap part.

The rule bites hardest in three places, which is where to look first when auditing a domain:

- **Where the game is most faithful.** Cross-view rootkit detection (`../design/04-mining.md` §3.1) is the strongest real technique in the game and needs `process(7)`, `socket(7)` and `cross-view-detection(7)` to be legible at all.
- **Where the game is least real.** Invariant I4's self-mining immunity has no real analogue whatsoever. It still gets an entry, which says so in one sentence and does not apologise (`../client/04` §2.10). An unexplained fiction is indistinguishable from an unexplained fact.
- **Where the word is borrowed.** Every row of `../client/04` §2.15's homonym table is a concept the game uses under a name that already means something else. Each needs both meanings written down, and a mandatory `notes:` line on both entries.

### 7.3 The converse: we do not teach what the game does not touch

**An entry with no `hook` is not written.** However interesting the concept, however much it would improve the curriculum as a curriculum.

This is the rule that keeps the doc set finishable, and the argument for it is not economy but effectiveness: **an unused entry is an unread entry.** The teaching layer's entire delivery mechanism is contextual — the gloss bar fills when the player's attention is already on the term, the popover opens because the player asked about the thing in front of them. A concept with no surface has no trigger, so it exists only in the index, where it is found only by someone who already knows to look for it. It costs writing time, review time, translation cost, and index noise, and it teaches nobody.

Concretely, this means the curriculum does **not** cover: how a compiler works, data structures and algorithms, how to program, database internals, machine learning, or the history of computing. All genuinely worth knowing. None of them have a surface in this game.

The honest way to serve that impulse is `reading:` — Tier 3 (`../client/04` §4.4) exists exactly so an interested player can leave, and pointing at a real specification is a better gift than a page we wrote to feel complete.

### 7.4 The honesty ledger

Two counts a domain document publishes and keeps current:

- **Status distribution** (§7.1 item 5), so the honesty claim is auditable rather than asserted.
- **Unverified claims**, marked ⚠ inline and listed. `../client/04` §2.16 rule 2 requires every claim to be verified before it is written, and §4.8.3 rule 8 requires it before merge. Where a writer could not verify something, the correct action is to **mark it ⚠ and leave it out of the shipped page** — not to assert it confidently and hope. A curriculum that teaches something false is worse than no curriculum, and a ⚠ is how that belief gets acted on rather than admired.

---

## 8. How a writer works

### 8.1 The loop

Eight steps, in this order. The order is the method, not a formality — steps 2 and 3 are cheap and reject entries that steps 4–6 would have made expensive.

1. **Pick a concept from the domain's inventory**, and confirm it has a hook. No hook, no entry (§7.3).
2. **Write the `misconception` first.** Before the gloss, before anything. If no false belief can be named, ask hard whether the entry is needed at all — sometimes the honest answer is that it is a fact, not a lesson, and belongs inside another entry's `DESCRIPTION`. This step also, reliably, tells you what the entry is actually about.
3. **Write the `transfer` second.** If it cannot be filled with a concrete, checkable action, stop. The entry is decoration and deleting it now costs nothing (§3.4).
4. **Set `status` using §4.2's ordered procedure**, and if the answer is `real, simplified`, write the `simplified` field *before* the body. It will change how the body is written.
5. **Set `section` (§5), `stage` and `prerequisites` (§6).** Check R4 immediately: if the gloss you are about to write needs a later-stage concept, the stage is wrong or the entry is two entries.
6. **Write the body**: `DESCRIPTION` game-first, then `REAL-WORLD COUNTERPART` opening with the status word, then `CAVEATS` if required. 400 words maximum. Then the 72-character `gloss` — **last**, because a gloss is a compression of something that already exists, and writing it first produces a body that justifies the gloss instead of a gloss that summarises the body.
7. **Verify every factual claim against a primary source and record it in `verified`** with the date. A remembered fact is a defect waiting for a player who knows better (`../client/04` §2.16 rule 2). Prefer RFCs, `man7.org`, W3C recommendations, MITRE ATT&CK, and project documentation; avoid anything paywalled, anything that rots, and anything whose framing belongs to a vendor.
8. **Send it for the two reviews in §8.4.**

### 8.2 Checking that the misconception is real

The `misconception` field is worthless if it is invented, and inventing one is easy and tempting — it makes any entry look purposeful. Three tests, at least two of which must pass:

- **Attestation.** The belief appears as a corrected misconception in a reputable source: a `man` page's `CAVEATS` or `BUGS`, a standard's rationale section, a well-known FAQ, vendor documentation that explicitly warns about it, or a specification's security-considerations section.
- **Observation.** Somebody the writer has actually spoken to holds it — a colleague, a play tester, a support thread, a widely-upvoted question. Recorded in `verified` as such, honestly, including that it is anecdotal.
- **Derivation.** The belief follows naturally from a *true* thing the reader already knows, applied one step too far. These are the highest-value misconceptions because they are the ones intelligent people reliably construct for themselves: "encryption scrambles data so it cannot be read" is true, so "hashing scrambles data so it cannot be read" is an entirely reasonable extrapolation from the fact that both are described as scrambling.

Failing all three, the honest entry is `misconception: none known` — which is legal, must be justified in review, and is far better than a fabricated one. A fabricated misconception is a writer telling the reader what they think, incorrectly, and an adult notices.

### 8.3 Running the transfer test

Literally run it. Open a terminal, type the command, read the output, and confirm that a person who has read only this entry could do the same and understand what came back.

Three things this catches that nothing else does:

- **Commands that do not exist or do not behave as claimed** on the platform the transfer names. `ps aux` is BSD-style and works on macOS and Linux; `ps -ef` is the System V form; the two print different columns.
- **Transfers that require setup we did not mention** — a package to install, a permission the player does not have, a file that only exists on servers.
- **Windows.** Most players are on Windows and most transfer tests as written assume a Unix shell. This is a real, unsolved problem for the whole doc set, and it is **ED-8**. Until it is decided, every transfer test states the platform it assumes rather than pretending to be universal.

### 8.4 Review — the requirement, not the suggestion

`../client/04` §1.1's entire claim collapses if we teach something false, so review is a gate, not a courtesy. `../design/15-open-questions.md` escalates "who writes and reviews the term database" as a blocking product question, and `../client/04` §6 **T-12** asks for a practitioner pass over the mapping tables. This section is this doc set's answer.

**Two reviews, two different people, neither of them the writer.**

| Review | Reviewer | The three questions |
|---|---|---|
| **Technical** | Someone who knows the domain professionally — not "someone technical", someone who works in *this* domain | (1) Is every factual claim true? (2) Is the misconception the one people actually hold? (3) Would a practitioner wince at anything here? |
| **Editorial** | Someone who did not write it, reading as the §2.1 audience | (1) Is every term defined before it is used? (2) Is there a number where a number exists? (3) Is there a sentence that patronises, pads, or softens a correct word into a vague one? |

Four rules that make this real rather than ceremonial:

1. **The technical reviewer is never the writer.** A writer who verified their own claim in step 7 has checked that the claim matches a source; they cannot check that the source was the right one to consult or that the framing is what a practitioner would recognise.
2. **Cryptography and security entries get a specialist.** CLAUDE.md already carries this instinct for `../architecture/07-transport-security.md` — "reviewed patterns, unreviewed code. Do not let it guard a live federation until someone qualified has read it." The same applies to prose: an entry about signatures, key handling or an attack class needs someone who does this for a living, and until then it stays ⚠.
3. **Review is recorded in the change, not in a field.** `verified` records *sources*; sign-off is the commit or PR. Adding an `approved-by` field would produce a value that rots the first time an entry is edited without re-review, which is worse than no field.
4. **Re-review is triggered by change, not by calendar.** A `revision` bump on the body, or a change to the underlying mechanic, sends the entry back through both reviews. Semantic drift — a tuning pass that silently falsifies a `CAVEATS` — is `../client/04` §6 **T-11**, and this doc set's `hook` field is the hook for it: a change to a tool's row in `../design/06`–`11` flags every entry whose hook names it.

### 8.5 Definition of done

An entry is done when every box is true. A domain document ships when every entry in it is done.

- [ ] `hook` names a real surface, cited with a document and section anchor
- [ ] `misconception` passes at least two of §8.2's three tests, or is honestly `none known`
- [ ] `transfer` is a concrete action, was actually run, and states its platform
- [ ] `status` was set by §4.2's ordered procedure, and the downgrade bias was applied at any tie
- [ ] `simplified` is present and specific if `status` is `real, simplified`
- [ ] `section` follows §5.2; no `SYNOPSIS` on a section-7 page
- [ ] `stage` and `prerequisites` satisfy R1, R3, R4 and R8
- [ ] `gloss` is one sentence, ≤ 72 characters, and does not contain the word it defines
- [ ] Body is ≤ 400 words, `DESCRIPTION` is game-first, `REAL-WORLD COUNTERPART` opens with the status word
- [ ] `CAVEATS` present where §4.3 or `../client/04` §2.15 requires it, and it does not repeat another entry's caveat
- [ ] Every factual claim has a source in `verified`, with a date
- [ ] Every `seeAlso` reference resolves to an entry that exists
- [ ] `notes` present for every `../client/04` §2.15 homonym
- [ ] Technical review passed, by someone who knows the domain and did not write it
- [ ] Editorial review passed against §2

---

## 9. Open questions

Prefix **`ED-`**, which is unused: `../design/15-open-questions.md` records `CL-` (client 00), `V-` (01), `PN-` (02), `SK-` (03), `T-` (04), `WL-` (05), `RI-` (06), `AX-` (07), plus the design set's own `OQ-`/`P-`/`D-`/`S-`/`N-`/`E-`/`A-`/`G-`/`W-`/`Q-`. Log these in `../design/15-open-questions.md` §2 if this doc set is adopted.

- **ED-1: the curriculum introduces a third class of term, and `../client/04` §4.10's orphan check does not anticipate it.** The check asserts that every `canonical:` appears in `../design/glossary.md` or in a `ui-only.txt` allowlist. A curriculum concept such as `process(7)`, `system-call(7)` or `packet(7)` is neither — it is a real computing concept the game surfaces, with no game-design existence. Worse, `canonical:` is currently *conditional* ("when the term exists in the glossary"), so an entry that omits it passes the orphan check trivially, which is a hole big enough to drive an invented term through. **Proposed amendment to `../client/04` §4.8.2 and §4.10:** make `canonical:` unconditionally required, and give the orphan check three accepted sources — the glossary, `ui-only.txt`, and a curriculum index generated from `docs/education/`. That makes this doc set a build input rather than a document nobody reads, which is the only reliable way it stays current. **Decide before the first term file is written.**
- **ED-2: does the shipped file get generated from the curriculum entry, or hand-copied?** Generation guarantees they never diverge and costs a small build step; hand-copying is free today and drifts by the third edit. Related to ED-1 — if the curriculum index is generated anyway, generation of the pages is nearly free.
- **ED-3: ✅ RESOLVED 2026-07-25 — seven domains, not six** (§1.4, and the sub-section beneath the table). The tie-break rule survived contact with a real inventory of 126 entries; the *split* did not. Representation became its own domain (`01-foundations.md`) because it has eighteen entries and forward-references nothing, and the command line stayed at `04` because `03`, `06` and `07` name `shell(7)` or `exit-status(7)` in `prerequisites` and no later numbering can satisfy R8. Architecture, networking, cryptography and distributed systems each shifted up one. **Two follow-ups survive the resolution and are tracked separately:** detection/logging/anti-forensics and the legality material are still unowned (**CT-1**, proposal: an eighth document), and the ownership ladder itself now needs the §6.4 check-5 audit run across all seven documents rather than within each.
- **ED-4: are §6.4's five graph checks automated, and where?** They are the curriculum's equivalent of `../client/04` §4.10's CI checks, but they operate on Markdown in `docs/`, not on resources in `client/`. Options: a small test in the client module that parses the curriculum, a standalone script, or manual audit at review time. Manual audit is honest for 20 entries and fictional for 140.
- **ED-5: should `misconception` ever be rendered to the player?** It is currently curriculum-only. There is a real argument for a `## COMMON MISTAKE` body section — it is the highest-value content in the template for this audience, and hiding it from the player is odd. Against: `../client/04` §4.3.1 already carries two game-added sections and marks them visually so a player is not surprised by a real man page later; a third needs a deliberate decision, and a page that opens by telling the reader what they believe wrongly can read as condescending — the exact failure §2.3 bans. Decide with the first domain document, not in the abstract.
- **ED-6: who is the technical reviewer, and is there budget?** §8.4 specifies the requirement; it does not conjure a person. This is `../design/15-open-questions.md`'s escalated "who writes and reviews the term database" and `../client/04` §6 T-12 seen from the content side. Without a named reviewer this doc set produces confident prose with no verification gate, which is precisely the failure it exists to prevent. ⚠ Note also an id collision worth cleaning up: `../design/15` escalates a question it labels **T-4** ("who writes and reviews the term database") while `../client/04` §6's own **T-4** is readline key bindings. Two different questions, one id.
- **ED-7: is reading level measured or asserted?** `../client/04` §4.8.3 rule 5 invokes WCAG SC 3.1.5 and satisfies it structurally (the Tier-1 gloss is the plain-language supplement). Whether we also *measure* the body prose — and against which metric, none of which handle technical vocabulary well — is open. A bad metric would push writers toward the "vague" column of §2.4, which would be a net loss.
- **ED-8: transfer tests assume a Unix shell, and most players are on Windows.** The highest-impact open question in this document. The client ships on macOS, Windows and Linux (`../client/02-platform-native-themes.md`), the game's whole vocabulary is Unix, and `ps aux` is not a thing a Windows player can type. Options: (a) every transfer test names its platform and Windows players are told to use WSL; (b) every transfer test carries a PowerShell equivalent, roughly doubling the verification work and introducing a second command vocabulary the game does not teach; (c) transfer tests target what is universal — Task Manager, a browser's developer tools, `ping`, `tracert`. Option (c) is weaker but honest, and (a) is currently the interim rule (§8.3). **Decide before the domain documents are written**, because it changes how a third of the entries are worded.
- **ED-9: where is the dual-use line?** `../client/04` §4.4 forbids citing offensive-tooling walkthroughs, which handles *citations*. It does not say how far our own prose may go. Explaining what a buffer overflow is, is education; explaining how to build one is not this game's business, and the boundary between them is a matter of degree that `06-cryptography-and-trust.md` will hit on its first page. Needs a stated line before that document is drafted, not after.
- **ED-10: what triggers a re-check when the world changes?** Real-world facts move: standards are superseded, techniques die, "current" becomes "obsolete". Rainbow tables are already the game's worked example of a technique that died. Proposal: `verified` carries a date, and any entry whose date is older than a release cycle is re-checked before that release ships. Cheap, but somebody has to own it or it will not happen — the same failure mode as `../client/04` §6 T-11.
