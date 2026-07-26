# 04 — Terminology & the Educational Layer

**Status:** ⚠️ **[PROPOSAL]** — the *goal* is Established as a product goal and is already load-bearing in the doc set: client pillar **C6** ("the interface teaches the real thing", `00-client-overview.md` §2), the three teaching levels `explain` / `terms` / `off` (`00-client-overview.md` §5.2), the three-value honesty marker `real` / `real, simplified` / `game` (§5.3), the four "never" rules (§5.4), and the `es-term` primitive's anatomy (`01-visual-language.md` §8.10). Those are cited here, not re-decided. Everything this document *adds* — the vocabulary map, the Unix command grammar, the `man`-page system, the content schema, the drift tests, the localisation model — is first-pass design. Platform, toolkit and standards claims were verified against live sources in this pass; anything not verified is marked **⚠ unverified** inline.
**Depends on:** `00-client-overview.md` §5 (the educational layer), §6 (information architecture), §7 (non-goals), `01-visual-language.md` §3.5 / §8.10 / §9 (type, `es-term`, microcopy), `../design/glossary.md` (**the canonical term list — this document is bound to it**), `../design/04-mining.md` §3.1 (real, consistent audit data), `../design/00-vision-and-pillars.md` §4 (Invariant I14)
**Depended on by:** `05-tool-windows-and-layout.md` (the command grammar names windows; the gloss bar is a per-window region that the docked layout must also carry), `07-accessibility.md` (§5 here is the teaching layer's slice of that doc's floor); the `client/` module's teaching-content pipeline and its CI checks

---

## 1. The principle

### 1.1 The claim, stated so it can be falsified

> **A player who learns this game learns something that works in a real terminal.** Not "gets a vibe for hacking" — learns that a port is a 16-bit number, that `ps` and `ss` disagree when something is hiding, that a hash chain breaks loudly, that `-v` means verbose almost everywhere, and that a man page's first line tells you whether to keep reading.

That is a testable claim, and the test is uncomfortable on purpose: **take any term the interface shows a player, and ask what they would now be able to do in a real shell.** If the answer is "nothing," the term is decoration and it should either be given a real counterpart or be honestly labelled fiction.

The claim is not "everything in the game is real." Ethecoin is not real. Heat is not real. The five unlock gates are not real. The claim is narrower and much harder to fake: **every term is honestly labelled, and every term that mirrors something real is described in terms of that real thing.**

### 1.1a uOS is why the claim can be true

The claim in §1.1 would be very hard to keep if the game's computing concepts were metaphors that merely *resembled* Unix. They are not. **Every rig in the game runs uOS**, and uOS is **the baseline for every OS-flavoured concept in the game** (`../design/glossary.md`) — processes, the filesystem, permissions, devices, logs, shells, daemons, networking. When this document maps a game term to a real one, it is not drawing an analogy between two different worlds; it is describing **one Unix-like system** in two vocabularies.

This changes the register of the whole teaching layer, and it is worth being precise about how:

- **The player is not "learning a game that is like Unix". They are using a Unix-like OS.** uOS is modelled on real Unix by construction, which is exactly why the knowledge transfers. That is the mechanism behind pillar **C6**, not a happy accident.
- **It sharpens the fiction flag.** §1.1's honesty rule gets a cleaner test: a term is *real* when uOS inherits it from Unix (a process, a file mode, a port), *game* when uOS's world adds it (heat, ethecoin, unlock gates). "Is this uOS-the-Unix-like-OS, or uOS-the-world?" is a much easier question for a writer than "does this feel real?".
- **It does not license inventing Unix.** uOS may add things Unix lacks; it may not *contradict* Unix. If uOS's `ps` behaved differently from real `ps` in a way a player would carry into a real shell and get wrong, that is a defect — it would make the game actively mis-teach, which §1.1 says is worse than teaching nothing.
- **Both themes show the same uOS.** The native family draws uOS's state in the host platform's conventions; the uOS family draws it as uOS's own console. The player's laptop runs macOS, Windows or Linux; their *rig* runs uOS. The client is the window onto it. This is the concrete reason "only the skin changes" (`00-client-overview.md` §3.4) is coherent rather than merely a rule: there is one system underneath, drawn two ways.

> **The naming convention, since this document uses both forms constantly:** **uOS** in prose and in anything a player reads; `uos` in identifiers — theme ids, CSS classes, stylesheet names. The same split the docs already use for macOS/`macos`.

### 1.2 Why this is a constraint, not a garnish

Three reasons, in increasing order of force.

**It is already required.** `../design/04-mining.md` §3.1 makes process, connection and storage views a hard implementation requirement: they "must be real, consistent data — not decorative," because a careful reader has to be able to find a rootkit-wrapped miner from a discrepancy that "is always present in the data" — cycle totals that don't add up, a connection with no owning process, storage deltas. That is not a teaching feature bolted on; that is *cross-view detection*, a real technique (list the same resource by two different paths and compare the answers), and the design already committed to modelling it faithfully. Once the data is honest, labelling it honestly costs almost nothing. Once the data is honest and the labels are *dishonest*, we have gone to the expense of building a real system and then lied about what it is.

**Fake jargon is worse than no jargon.** A player who learns "a rainbow table cracks any password" has been actively miseducated: real rainbow tables are a space–time trade-off against *unsalted* hashes and are largely obsolete against modern password hashing. If the game is going to use the term at all — and it does, `../design/06-intrusion-tools.md` §1 — then the version that costs nothing extra is the true one, and the true one is *more interesting* than the false one, because "useless against salted targets" is already the tool's designed weakness.

**It is the cheapest content in the game.** A `man` page is a few hundred words. A puzzle class is months. The educational layer buys a distinctive product claim, a genuine reason for the Unix register, and a solution to the "what is a schematic gate?" onboarding problem, for the price of prose.

### 1.3 The three-part rule for every term

Every term the interface shows sits in one of three buckets, and each bucket has an obligation:

| Bucket | Obligation | Failure if skipped |
|---|---|---|
| Mirrors a real thing, accurately | Name the real thing, name the real tool or standard, say what a player could go and read | We had the teaching moment and threw it away |
| Mirrors a real thing, simplified | All of the above, **plus** state the simplification explicitly in a `CAVEATS` section | We teach a false model of a real thing — the worst outcome |
| Mirrors nothing real | Say so, plainly, and name the nearest real concept as an adjacency rather than an equivalence | We teach fiction as fact; the whole educational claim becomes indefensible |

`00-client-overview.md` §5.3 fixes the marker vocabulary for these three: `real`, `real, simplified`, `game`. This document supplies the content contract that makes the marker mean something (§4.8) and the mapping table that populates it (§2).

### 1.4 The authority chain

There is exactly one place a term is named, and everything downstream derives from it:

```
../design/glossary.md          canonical name + canonical capitalisation
        │
        ├─► code identifiers            computeAvailable, factionReputation, …   (CLAUDE.md conventions)
        ├─► UI labels                   01-visual-language.md §9.2
        └─► the term database           client/src/main/resources/terms/…        (§4.8)
                    │
                    ├─► the gloss bar   (Tier 1, §4.2)
                    ├─► the man page    (Tier 2, §4.3)
                    └─► the term index  (the `man` window, §4.6)
```

The arrows are enforced, not aspirational: §4.10 specifies three CI checks that fail the build when a term drifts between the glossary, the database and the UI. CLAUDE.md already asks that docs and code stay searchable against each other; this is that rule extended one hop to the strings the player reads.

### 1.5 What the educational layer is *not* allowed to become

- **Not a tutorial gate.** `00-client-overview.md` §5.4 forbids gating progress on reading anything. This is absolute: there is no "read this to continue," no quiz, no acknowledgement checkbox, and no achievement for opening man pages.
- **Not a second voice.** `01-visual-language.md` §9.1 permits teaching-layer prose to be "warmer and more explanatory" than the operator register — that licence covers *explaining clearly*. It does not license jokes, winks, a narrator, or a mascot.
- **Not a substitute for legible UI.** If a control needs a tooltip to be usable, the control is broken. The teaching layer explains *concepts*; it never rescues a bad affordance.
- **Not a credential.** The game does not claim to make anyone a security professional, and no copy anywhere implies it. It teaches vocabulary and a handful of genuine mechanisms. That is a real and honest thing to be.

---

## 2. The vocabulary map

This is the reference every writer, designer and implementer works from. **A wrong row here is worse than a missing row**, because a missing row is silence and a wrong row is instruction.

### 2.1 How to read the tables

The **Status** column is the marker from `00-client-overview.md` §5.3, and it is rendered as an `es-chip` (`01-visual-language.md` §8.3) with a **fill ladder** rather than a hue:

| Status | Chip glyph | Chip text | Meaning |
|---|---|---|---|
| `real` | filled square | `real` | The concept is genuine and the game's model does not misrepresent it |
| `real, simplified` | half-filled square | `real, simplified` | Genuine concept, deliberately abstracted; the abstraction is named in `CAVEATS` |
| `game` | open square | `game` | A construct of this fiction; the nearest real idea is named as an adjacency, never as an equivalence |

> **This spends none of the hue budget.** `01-visual-language.md` §2.5 declares nine hues in service and says a tenth may only arrive by retiring one. A three-step fill ladder reads in greyscale, satisfies §2.4's redundant-encoding rule by construction, and needs no new colour token — the chip uses `-es-status-idle-fg` / `-es-status-idle-subtle`, whose definition ("the absence of a state, not a bad one") is exactly right, since `game` is not a defect and `real` is not a virtue.

The **Real counterpart** column names a *command, standard or mechanism a player could go and look up* — not a genre. "Like a hacker would" is not a counterpart; `nmap(1)` is.

### 2.2 Windows and surfaces

Extends the catalogue in `00-client-overview.md` §6.1, which already fixes the Unix analogue per window. This table adds the *specific* real command and what transfers.

| Window (`00` §6.1) | Status | Real counterpart | What actually transfers |
|---|---|---|---|
| `rig-monitor` | `real, simplified` | `top(1)`, `htop(1)`, `uptime(1)` | Reading a live capacity display: what is running, what it costs, what is left. Real `top` shows *time-shared* CPU percentages and a load average, not reservations — see `compute(7)` in §4.9. |
| `audit` | `real` | `ps(1)`, `ss(8)` (`netstat(8)` on older systems), `df(1)`, `lsof(8)` | The genuine skill: cross-referencing two views of the same machine and noticing they disagree. A socket with no owning process is a real red flag found exactly this way. |
| `map` | `real, simplified` | `traceroute(8)`, `nmap(1)` host discovery, `ip route` | Hop distance and reachability as first-class facts. **`traceroute` measures a path; it does not draw a graph** — the game's persistent map is an accumulation of many such measurements, which is what real network mapping is. |
| `terminal` | `real, simplified` | an interactive shell (`bash(1)`, `zsh(1)`) | Command grammar, flags, exit statuses, history, completion (§3). It is not a shell — §3.1. |
| `recon` | `real` | `less(1)`, `grep(1)` over recovered files | Reading and searching machine-authored text: log lines, headers, records. |
| `mining` | `game` | a mining-pool dashboard | The dashboard *shape* is real; the yield model is not (§2.10). |
| `storage` | `real, simplified` | `ls(1)`, `df(1)`, `mount(8)` | Mount points, capacity, and the idea that where a thing sits determines who can reach it. The exposure column is the game's addition. |
| `ledger` | `real, simplified` | a public blockchain explorer | Public-by-default transaction records are genuinely how most cryptocurrencies work, and chain analysis is a real industry. |
| `market` | `real, simplified` | `apt(8)`, `dnf(8)`, `pacman(8)` — a package manager | `search` / `show` / `install` subcommands, dependency-style requirements stated before you commit. The five gates are not real (§2.14). |
| `botnet` | `real, simplified` | `jobs(1)`, `systemctl(1)`, `cron(8)` | Long-running background work you own, start, inspect and stop. |
| `defense` | `real` | `nftables(8)` / `iptables(8)` / `pf(4)`, `fail2ban(1)` | Rules that permit or deny, and the fact that every one of them costs something to run. |
| `identity` | `real, simplified` | `id(1)`, `whoami(1)` | You are a set of identifiers, not a name. The DID part is fully real (§2.13). |
| `switcher` | `real, simplified` | `jobs(1)`, a window list | — |
| **`man`** *(new, §4.6)* | `real` | `man(1)`, `apropos(1)`, `whatis(1)` | Section numbers, page structure, `SEE ALSO`. |

> **⚠ This adds a fourteenth window id (`man`) to the thirteen fixed in `00-client-overview.md` §6.1.** It is required by §5.2 of that same document ("the term index is a searchable window") but was not given an id there. Flagged as **T-1** for `05-tool-windows-and-layout.md` to absorb or reject.

### 2.3 Resources

| Game term (`../design/glossary.md`) | Status | Real counterpart | The honest note |
|---|---|---|---|
| **compute** | `real, simplified` | CPU scheduling; `nproc(1)`; cgroup v2 `cpu.max` quotas; vCPU allocation; thread pools | Real CPUs *time-share*: two processes each asking for everything both run, slower. The game *reserves*. The nearest real analogue is a cgroup quota or a VM's vCPU allocation, not ordinary scheduling. |
| **cycles** (the unit) | `real, simplified` | **not** clock cycles | ⚠ **The single highest-risk unit in the game.** A real cycle is one tick of a clock — billions per second. A game cycle is a unit of *capacity share*, closer to a vCPU or a cgroup weight. The `compute(7)` page says this in its first `CAVEATS` line, because a player who leaves thinking a CPU has 100 cycles has been taught something false. |
| **Thermal Budget** | `real, simplified` | thermal throttling; TDP; sustained vs. boost clocks | Genuinely real: a CPU that cannot shed heat clocks itself down, so a loaded machine really is slower. What is invented is a *pool of cycles that refills* (`../design/01-core-resources.md` §1.3). |
| **ethecoin (EC)** | `game` | cryptocurrency generally | No referent. The *public ledger* underneath it is real (below). |
| **the public ledger** | `real, simplified` | Bitcoin's ledger; block explorers; chain analysis | Public-by-default transaction history is real, and following value flows to build a case is a real profession. Real ledgers are also append-only and permanent — see `dead-drop(1)` in §4.9. |
| **noise** | `game` | detection footprint; IDS alert volume; `nmap` timing templates `-T0`…`-T5` | Practitioners genuinely call a technique "noisy," and slowing a scan genuinely evades threshold-based alerting. What is invented: one scalar, decaying on a half-life, pooled across your machines. **Real log entries do not decay.** That is why real attackers delete them, and why deleting them is itself detectable. |
| **heat** (`personalHeat`, `serverHeat`) | `game` | — | ⚠ **No clean counterpart, and the page says so in its first line.** The adjacent real ideas — IP-reputation scoring, watchlists, threat-intel attribution confidence — are all *someone else's opinion of you recorded somewhere*, which is thematically close and mechanically nothing like a decaying global scalar with five bands. |
| **trace** | `real, simplified` | incident response; attribution; mean time to detect | Defenders really do correlate evidence toward attribution. They do **not** have a progress bar: real attribution is discontinuous, often never completes, and sometimes completes months later. The meter is a legibility device (`../design/05-hacking-minigame.md` §4). |
| **factionReputation** | `game` | — | Faction standing has no computing referent. |
| **validatorReputation** | `real, simplified` | reputation-weighted BFT consensus; peer scoring in distributed systems | ⚠ **Never rendered near `factionReputation`** (`../design/glossary.md`, `01-visual-language.md` §2.2.10). Two different words in the UI, two different pages in `man`, and each page's `CAVEATS` names the other as the thing it is not. |

### 2.4 Storage tiers

| Game term | Status | Real counterpart | The honest note |
|---|---|---|---|
| **Encrypted Vault** (`vault`) | `real, simplified` | LUKS/`cryptsetup(8)`, FileVault, BitLocker, VeraCrypt, `age`, GPG; "cold storage" in the wallet sense | **Real encrypted storage is only safe while it is locked.** Unlock a volume to use a file and anything running as you can read it. The game's vault is safe *while you play*, which no mounted volume is. The nearest true analogue is genuine cold storage: a device that is powered off and not attached — which is exactly why `../design/01-core-resources.md` §6 says a tool assigned to a bot *leaves* the vault. That rule is the real trade-off, correctly modelled. |
| **Standard Storage** | `real, simplified` | a mounted volume on a running host; file permission bits (`chmod(1)`) | "Exposed while the owner is online" is a fair sketch of a service that is only reachable while running. |
| **High-Hackable Zone** | `real, simplified` | a world-readable directory; an exposed network share; an unauthenticated object store | The single most common real-world data breach is a storage bucket with permissions set to public. The tier is that, with the risk made explicit. |
| **Cold Storage Expansion** | `real` (the term) | cold storage | The term is genuine wallet-security vocabulary. |

### 2.5 Recon tools (`../design/07-recon-tools.md`)

| Tool | Status | Real counterpart | The honest note |
|---|---|---|---|
| **Passive Sniffer** | `real` | `tcpdump(8)`, Wireshark, promiscuous-mode capture; passive reconnaissance | Genuinely silent: listening emits nothing. The game's zero noise cost is *correct*. |
| **Topology Mapper** | `real, simplified` | network mapping; LLDP/CDP neighbour discovery; iterative `traceroute(8)`; BGP looking glasses | "One hop → two hops" is a game abstraction of a real distinction: you know your neighbours cheaply, and everything beyond them expensively. |
| **Traffic Analyzer** | `real` | traffic analysis; NetFlow / IPFIX flow records | **Genuinely important and genuinely under-taught:** flow metadata reveals who talked to whom, when, and how much *even when the content is encrypted*. The tool distinguishing live from dormant nodes is exactly what flow data is for. |
| **Ping Sweep** | `real` | `nmap -sn`, `fping(8)`, ICMP echo sweeps | The game's "target is notified" is **accurate, not a penalty invented for balance**: an echo request arrives at the target and lands in its logs. `../design/07-recon-tools.md` §2 calls this out as deliberate; it happens to also be true. |
| **Honeypot Detector** | `real` | honeypot fingerprinting (detecting emulated services by artifacts, timing, or protocol mistakes) | The ~20 % false-negative rate with **no false positives** (`../design/07-recon-tools.md` §2) is a real property of detectors of this class: evidence of a honeypot is strong, absence of evidence is weak. Good page material. |
| **Provenance Tracer** | `real, simplified` | file integrity monitoring (`aide(1)`, Tripwire); remote attestation; verifying signatures over a fleet | "Audit your own assets to find out whether they are still yours" is a real and unglamorous discipline. |

### 2.6 Intrusion tools (`../design/06-intrusion-tools.md`)

| Tool | Status | Real counterpart | The honest note |
|---|---|---|---|
| **Port Sweep** | `real` | port scanning; `nmap -sS` / `-sT` / `-sU` / `-sV`; ports as 16-bit numbers (RFC 9293 §3.1 — verified) | Fully real. See the worked page in §4.9. |
| **Fuzzer** | `real` | fuzzing; AFL++, libFuzzer, honggfuzz; malformed-input generation and crash triage | Real, and the game's framing is right: fuzzing is what you do when you *don't* know the rule, and it is loud. |
| **Rainbow Table** | `real, simplified` | precomputed hash chains (Hellman 1980; Oechslin 2003); defeated by salting | ⚠ Must state that it is **dated**: modern password storage uses per-password salts and deliberately slow KDFs (bcrypt, scrypt, Argon2), against which rainbow tables do not work at all. The game's "useless against salted targets" is the real reason they died. |
| **Overflow Kit** | `real, simplified` | buffer overflow / stack smashing; mitigations: stack canaries, ASLR, NX/DEP, CFI | A real overflow is a memory-corruption primitive, not a layer skip. Good page: the mitigations list is where the term **stack canary** appears — and §2.15 disambiguates it from the game's Canary Token. |
| **Credential Harvester** | `real` | credential dumping (ATT&CK **T1003** — verified); pass-the-hash; lateral movement | The game's pivot mechanic is precisely why credential reuse matters in reality. |
| **Zero-Day** | `real, simplified` | a vulnerability unknown to the vendor with no patch available | The term is exactly right. The universality is not: real zero-days are specific to a product and version, and the same one does not open every door. |
| **Side-Channel Reader** | `real` | side-channel attacks: timing attacks, differential power analysis, cache timing (Flush+Reload), Spectre/Meltdown, TEMPEST | Fully real, and the fiction — learn about a system without entering it — is genuinely what a side channel is. One of the best pages in the set. |

### 2.7 Stealth (`../design/08-stealth-and-noise.md`)

| Tool | Status | Real counterpart | The honest note |
|---|---|---|---|
| **Log Scrubber** | `real` | anti-forensics; indicator removal (ATT&CK **T1070**); clearing `wtmp`/`utmp`, `journalctl --vacuum` | ⚠ The page **must** carry the defender's answer, because it is the actually-useful lesson: ship logs off the box (remote syslog) and make them append-only (`chattr +a`). You cannot scrub a log you cannot reach. |
| **Identity Spoofer** | `real, simplified` | source-address spoofing; MAC spoofing; forged attribution | Spoofing a source address is real but breaks return traffic, which is why real spoofing is used for reflection and flooding, not for interactive sessions. Stated in `CAVEATS`. |
| **Traffic Shaper** | `real, simplified` | ⚠ **name/function mismatch** — see §2.15 | Real "traffic shaping" is QoS (`tc(8)`, token buckets). The game's function — never spike above a detection threshold, at a hard cost in speed — is **low-and-slow evasion** (`nmap -T0`/`-T1`). The page says both, because a player who leaves thinking `tc` hides you has learned something false. |
| **Dead Drop** | `real` | espionage tradecraft; **dead drop resolver** malware (ATT&CK **T1102.001** — verified); ledger-privacy techniques | See the worked page in §4.9. |
| **Relay Chain** | `real` | onion routing; Tor — three relays by default, one encryption layer per relay (verified against the Tor Project) | Excellent mapping. Latency cost, diminishing returns past three hops, and the guard/middle/exit split are all real. |
| **Ghost Protocol** | `game` | key rotation; abandoning a pseudonym; burner devices | No real system wipes a reputation. The adjacency worth teaching: **an identity is a key, and a key can be discarded — but everything that identity ever signed is still signed.** |
| **Burner Handle** | `real, simplified` | pseudonymity; operational compartmentalisation | Real practice. The real failure mode — one careless cross-post links the two forever — is better teaching than the game's progression tax. |

### 2.8 Defense (`../design/09-defense-and-hardening.md`)

| Tool | Status | Real counterpart | The honest note |
|---|---|---|---|
| **Firewall (T1–T3)** | `real, simplified` | packet filtering: `nftables(8)`, `iptables(8)`, `pf(4)`; stateful inspection | Real firewalls filter by rule, they do not add a difficulty number. Tiers are a game device. |
| **Canary Token** | `real` | honeytokens; Thinkst Canarytokens; honeyfiles | Fully real and genuinely clever: a file that exists only to be touched, whose only purpose is to tell you someone touched it. ⚠ Not a stack canary — §2.15. |
| **Tarpit** | `real` | LaBrea; `endlessh`; SMTP tarpitting; the `TARPIT` target in xtables-addons | Exact match, including the philosophy: you do not stop the intruder, you make them slow, and slowness buys you response time. |
| **Honeypot Stash** | `real` | honeypots; decoy data | Real. The game's "raiders can't tell until extraction" is the real design goal of a honeypot. |
| **Auto-Counter Daemon** | `real, simplified` | automated response (SOAR), `fail2ban(1)` | ⚠ The page must state plainly that **"hacking back" is illegal in most jurisdictions**, and that real automated response blocks, isolates and alerts — it does not counter-attack. This is one of the few places the game models something that a player should specifically not go and do. |
| **Detection Array (T1–T3)** | `real, simplified` | host-based intrusion detection; scheduled integrity scanning | Standing detection that costs resources continuously is exactly right. |
| **Rootkit Wrapper** | `real` | rootkits; LKM rootkits; `LD_PRELOAD` hooking; hiding PIDs from `/proc` readers | ⚠ **The best single mapping in the game.** `../design/09-defense-and-hardening.md` §2 says it hides from routine scans but "does not survive a deliberate audit," and `../design/04-mining.md` §3.1 says the discrepancy is always in the data. That is **cross-view detection**: enumerate the same resource two ways and compare. Real detectors work exactly like this. The game's rule is not a balance concession; it is how rootkit detection actually succeeds. |

### 2.9 Rig infrastructure (`../design/11-rig-infrastructure.md`)

| Upgrade | Status | Real counterpart | The honest note |
|---|---|---|---|
| **Compute Cores** | `real, simplified` | physical cores / hardware threads; `nproc(1)`, `lscpu(1)` | Fine, with the `cycles` caveat from §2.3. |
| **Thermal Budget** | `real, simplified` | thermal throttling / TDP | See §2.3. |
| **Bandwidth** | `game` | ⚠ **name/function mismatch** — see §2.15 | Real bandwidth is bits per second. The game's Bandwidth caps *simultaneous engagements*, which in reality is a connection limit, a worker-pool size or a file-descriptor limit (`ulimit -n`) — not bandwidth. |
| **Memory Buffer** | `real, simplified` | RAM; resident set size; "what fits in memory at once" | Reasonable. ⚠ Not an I/O buffer and nothing to do with buffer overflows — §2.15. |
| **Isolated Partition** | `real, simplified` | namespaces and cgroups; containers; VMs; jails; sandboxing | ⚠ A real "partition" is usually a slice of a disk. The game's meaning — one workload that cannot be correlated with the others — is *isolation*, and the real vocabulary is namespaces and sandboxes. |
| **Firmware Implant** | `real` | UEFI/BIOS implants — LoJax (2018), BlackLotus (2023); ATT&CK Pre-OS Boot | ⚠ Another excellent mapping. "Survives a host wipe" is precisely the property that makes firmware implants frightening: reinstalling the operating system does not remove them. |
| **Worm Module** | `real` | worms — Morris (1988), SQL Slammer (2003), WannaCry (2017) | Real, including the design's insistence that you cannot steer it. The Morris worm's author could not stop it either. |
| **Cuckoo Patch** | `real, simplified` | C2 takeover / hijacking another actor's implant; brood parasitism as the metaphor | ⚠ Not Cuckoo Sandbox (a real, unrelated malware-analysis tool) — §2.15. |
| **Payout Splitter** | `real, simplified` | mining-pool fees; donate-level settings in mining software | Fine. |

### 2.10 Mining (`../design/04-mining.md`)

| Game term | Status | Real counterpart | The honest note |
|---|---|---|---|
| **self-mining** | `game` | — | Structural immunity to detection and seizure (Invariant I4) has no real analogue whatsoever; nothing on a networked machine is unreachable by construction. This is a deliberate game rule protecting an income floor, and the page says so in one sentence without apologising. |
| **deployed miner** | `real` | **cryptojacking** — Coinhive, XMRig implants; ATT&CK **T1496 Resource Hijacking**, sub-technique **T1496.001 Compute Hijacking** (verified) | ⚠ Invariant I6 — "a deployed miner consumes the *host's* compute" — is not a game contrivance. It is the definition of cryptojacking, and the reason it is worth detecting: the victim pays the electricity and the latency. One of the strongest pages available. |
| **control channel** | `real` | command and control (C2); beaconing | Real, including the fact that it must stay up and that it is the most detectable part of the operation. |
| **yield buffer** | `real, simplified` | a miner's unpaid local balance; a pool's payout threshold | Reasonable. |
| **block-reward model** | `real, simplified` | proof-of-work block rewards; mining pools | ⚠ Real PoW mining is a **lottery**: you win whole blocks at random, which is why pools exist — to convert variance into a steady trickle. The game's fixed-interval blocks resemble a pool payout, not solo mining. `CAVEATS` says exactly that. |
| **sweep** | `game` | takedowns, sinkholing, coordinated enforcement actions | The correlated, all-at-once shape is real; the probability-per-hour driven by a personal heat scalar is not. ⚠ Homonym with Ping Sweep — §2.15. |
| **crack / kill / hijack / sabotage** | mixed | `kill(1)` is exactly real | The four-way response menu is a game construct; `kill` doing what `kill` does is a free teaching moment (§3.10). |

### 2.11 Bot frames (`../design/10-botnets.md`)

The frame/instance distinction is genuinely instructive and maps cleanly.

| Game term | Status | Real counterpart | The honest note |
|---|---|---|---|
| **bot** (the player's) | `real, simplified` | daemons; `systemd` units; `cron(8)` jobs; agents | Long-running work you own and must supervise. |
| **frame** (blueprint) vs **instance** | `real` | an image vs. a container; a unit file vs. a running service; a class vs. an object | ⚠ An unusually clean mapping, and a genuinely valuable concept for a beginner. Invariant I11 (loss destroys instances, never blueprints) is *why* real systems separate the two. |
| **backlog timer** | `real, simplified` | alert queue depth; on-call triage; alert fatigue | Real and under-appreciated: more monitoring means more alerts means less time per alert. |
| **split attention** | `game` | — | A player-facing penalty with no system referent. |
| Frames: Recon / Miner / Sentinel / Breacher / Mimic / Scavenger | `game` (as a set) | scanners, miners, watchdogs, exploit runners, decoys, recovery jobs | Each maps loosely to a real category of automation; the six-frame taxonomy is the game's. |

### 2.12 Puzzle classes (`../design/05-hacking-minigame.md` §3.1 — itself `[PROPOSAL]`)

| Class | Status | Real counterpart | The honest note |
|---|---|---|---|
| **Enumeration** | `real` | service enumeration; ATT&CK Discovery tactic; `nmap -sV` | Real phase of a real methodology. |
| **Credential** | `real` | authentication; password hashing, salting, KDFs; credential stuffing | Real, and the most immediately useful thing in the game for a player's own life. |
| **Logic** | `real, simplified` | **oracle attacks** — padding oracles (Vaudenay 2002), blind SQL injection; the game's presentation is Mastermind-family | Deducing a secret from the shape of the responses is a real and important attack class. |
| **Timing** | `real` | race conditions; TOCTOU (time-of-check to time-of-use); timing attacks | Fully real. |
| **Traversal** | `real, simplified` | lateral movement; pivoting; graph traversal | ⚠ **Not path traversal** (`../../etc/passwd`), which is a different, very common, very real bug class — §2.15. The page disambiguates in its first `CAVEATS` line. |

### 2.13 Identity, provenance, federation (`../architecture/`)

The densest region of genuinely real material in the whole game — every row here is a real standard the player could go and read.

| Game term | Status | Real counterpart | The honest note |
|---|---|---|---|
| **DID** | `real` | W3C Decentralized Identifiers (DID Core became a W3C Recommendation in 2022); `did:plc`, `did:web`; AT Protocol | Real and current. |
| **PDS** | `real` | AT Protocol Personal Data Server | Real. Invariant I14 (game state never lives there) is the game's rule, not the protocol's. |
| **provenance record** | `real` | detached JWS (RFC 7515, Appendix F "Detached Content"), JSON Canonicalization Scheme (RFC 8785), Ed25519 (RFC 8032; RFC 8037 for JOSE) | The game ships real cryptography, not a metaphor. |
| **prevRecordHash / the chain** | `real` | hash chains; Merkle trees; `git log`; Certificate Transparency (RFC 6962); Sigstore/Rekor | See the worked page in §4.9. |
| **canonicalization** | `real` | JCS (RFC 8785) | An unusually good teaching moment: the same JSON object can be *written* many ways, and a signature is over bytes, so you must agree on the bytes first. |
| **validator quorum / BFT threshold** | `real` | Byzantine fault tolerance; PBFT; the `2f+1` of `3f+1` threshold | Real distributed-systems material, correctly named in `../architecture/05-validator-quorum.md`. |
| **equivocation** | `real` | equivocation in consensus protocols; slashing in proof-of-stake systems | Real term, real meaning: signing two conflicting statements is provable by anyone holding both. |
| **federation directory** | `real` | federated systems — email, ActivityPub, Matrix; server discovery | Real, and the fiction and the architecture are the same fact (`../design/14-world-and-narrative.md` §2). |
| **allowlist** | `real` | access control lists; Minecraft-style server allowlists | Real, including the modern preference for "allowlist" over the older term. |
| **home server** | `real, simplified` | self-hosting | Real practice. |

### 2.14 Deliberately unmapped: the game's own fiction

Stated here as a closed list so nobody later "finds" a counterpart for one of them and quietly writes a false page.

| Term | Why there is no counterpart |
|---|---|
| **ethecoin** | An in-fiction currency. The ledger under it is real; the coin is not. |
| **noise**, **heat** | Both are single scalars standing in for something real that is not a scalar (detection footprint; institutional attention). Fixed as `game` by `00-client-overview.md` §5.3. |
| **the five unlock gates** — Ethecoin / Schematic / Reputation / Proof-of-Skill / Heat State | Progression control. Software has licences, entitlements and feature flags; none of them are these. `unlock-gates(7)` says so, and then explains each gate purely in game terms — which is the honest thing to do, and also the fastest fix for the cognitive-load risk flagged as OQ-2 in `../design/02-unlock-gates.md` §4. |
| **schematic**, **schematic contribution material** | Progression items. |
| **The Eye**, **The Sickle**, **faction reputation**, **named-hacker** | Fiction. |
| **self-mining's immunity** (I4) | Explicitly impossible in reality; see §2.10. |
| **split attention**, **difficulty tier**, **puzzle layer** | Game mechanics. |

### 2.15 The homonym table — the highest-risk content in this document

Every row is a case where the game's word already means something else to someone who knows the field, or where the game borrows a real name for a different function. **Each of these gets an explicit `CAVEATS` line; none may be left to inference.**

| Game term | What a reader may assume | What it actually is here | Required disambiguation |
|---|---|---|---|
| **Canary Token** | a *stack canary* (an overflow mitigation) | a *honeytoken* — a decoy that reports being touched | Both are real, both are named for the mine canary, and they are unrelated. Both named in the page. |
| **Traversal** (puzzle class) | *path traversal* (`../../etc/passwd`) | graph traversal / lateral movement | Path traversal is real, common, and a different thing. Named and dismissed in one line. |
| **trace** (the meter) | `traceroute(8)` | defender-side attribution | ⚠ Sharpest collision in the client: the `map` window's analogue *is* `traceroute`, and `trace` is a meter in the same breach. The `trace(7)` page's first `CAVEATS` line is "this is not `traceroute(8)`." |
| **Bandwidth** (rig stat) | bits per second | a concurrency cap | Real name, different function. Say `ulimit -n` / worker pool. |
| **Traffic Shaper** | `tc(8)` QoS shaping | detection-threshold evasion | Real name, different function. Say `nmap -T0`. |
| **Isolated Partition** | a disk partition | a namespace / sandbox | Real word, wrong domain. |
| **Memory Buffer** | an I/O buffer; buffer overflows | working-set capacity | Real word, wrong domain. |
| **Cuckoo Patch** | Cuckoo Sandbox | hijacking a foreign implant | Different tool with the same bird. |
| **sweep** (Eye action) | **Ping Sweep** (the recon tool) | an Eye takedown of your deployed network | In-game homonym. Both pages cross-reference the other in `SEE ALSO`. |
| **cycles** | CPU clock cycles | capacity share | See §2.3 — the most consequential one. |
| **mining** | proof-of-work | linear yield per allocated capacity | See §2.10. |
| **Rainbow Table** | a current technique | a largely obsolete one | Say obsolete, say why (salts + slow KDFs). |
| **rootkit** | (accurate) | (accurate) | No disambiguation needed — flagged only so nobody "simplifies" it. |

### 2.16 Rules for adding a mapping

Mirrors the item checklist in `../design/02-unlock-gates.md` §5, for terms.

1. **Name a specific artefact.** A command with a section number, an RFC number, a standard, a named technique, or a named historical incident. "Like hacking in movies" is not a counterpart.
2. **Verify it before you write it.** Every claim in §2 was checked against a live source in this pass; a new row gets the same treatment. A remembered fact is a defect waiting for a player who knows better.
3. **If the mapping needs three qualifications to be true, it is `real, simplified` at best, and possibly `game`.** Downgrade rather than qualify.
4. **If the name collides with a real term of different meaning, it goes in §2.15**, and the collision is stated in the page — not implied.
5. **Prefer the boring true thing to the exciting false thing.** The true version is almost always more specific, and specificity is what makes a page worth reading.
6. **A `game` entry still gets a page.** Fiction that admits it is fiction is more trustworthy than fiction that stays silent, and the "nearest real idea" line is often the most interesting sentence on the page.

---

## 3. Interaction idioms

### 3.1 The safety boundary — read this before implementing anything in §3

`00-client-overview.md` §7 already states it as a non-goal, and calls it "a security boundary, not a scope decision." Restated here as mechanical rules, because §3 describes something that *looks exactly like a shell* and the pressure to make it one will be constant.

> **The client never executes a host command, reads a host path, or touches a host process. The "shell" is a game surface over a virtual namespace held in memory. There is no escape hatch, and none is added later "just for debugging."**

Seven rules, all checkable:

1. **No process execution anywhere in the `client` module.** `java.lang.ProcessBuilder`, `Runtime#exec`, `System#loadLibrary`, and `java.awt.Desktop` are forbidden imports. The repo already machine-checks its module charter with ArchUnit (`ArchitectureRulesTest`); this is one more rule of the same kind, and it should fire in CI, not in review.
2. **The parser produces a closed AST over an enumerated registry.** An unrecognised verb is `exit 127` (§3.5), never a fallthrough to anything.
3. **The namespace is virtual (§3.2).** No path in it ever resolves to a host path, and no path component is ever concatenated into a host filesystem call. The only host filesystem the client touches at all is its own profile directory, for settings and window geometry (`00-client-overview.md` §4.5).
4. **No environment expansion.** `$HOME`, `%USERPROFILE%` and friends are not expanded — not because it is hard, but because it would leak the player's OS username into a game surface that gets screenshotted and shared.
5. **No redirection, no command substitution, no chaining.** `>`, `>>`, `<`, `` ` ` ``, `$( )`, `;`, `&&`, `||`, `&` are all parse errors with a specific message (§3.11). The grammar's entire composition surface is the pipeline in §3.7, over read-only sources.
6. **The one outbound action is `HostServices#showDocument(String)`** — verified on `javafx.application.HostServices` — used only for a "further reading" link, only on an explicit per-link action, only after a confirmation naming the URL, and only with a URL that carries no query parameters (§4.4). The client is not a telemetry client (`00-client-overview.md` §7) and a reading link must not become one.
7. **No user-supplied content is ever interpreted as a command.** Recovered narrative artefacts (`../design/14-world-and-narrative.md` §3), node names, handles and item names are *data*. A recovered log line that reads `rm -rf /rig` is a story prop, and clicking it does nothing.

### 3.2 The namespace

A single virtual tree, rooted at `/`, addressed with real path syntax. It exists so that `ls`, `df`, globs and tab completion have something true to operate on, and so that a player learns what a path *is*.

```
/rig/                       this rig
/rig/compute/               one entry per compute consumer  → ps(1)
/rig/storage/vault/         the three tiers as mount points → df(1), mount(8)
/rig/storage/standard/
/rig/storage/high/
/rig/tools/                 owned tools and consumables
/rig/bots/                  running bot instances           → jobs(1)
/rig/defense/               armed defenses
/net/                       the network graph — KNOWN NODES ONLY
/net/<node-address>/
/net/<node-address>/miners/
/ledger/                    ledger entries by period
/man/<section>/<term>       the term database                → man(1)
```

Three rules:

- **`/net/` contains only what the player has discovered.** This is not a nicety: recon is a paid service (`../design/07-recon-tools.md` §3), and a namespace that lists unscanned nodes is a free Passive Sniffer. The tree is built from server-sent *known* state only, and the same rule governs tab completion (§3.6).
- **Paths are display-and-input syntax, never an authority.** Everything under `/rig/` and `/net/` is server-owned state rendered per `01-visual-language.md` §2.2.8; an entry whose value has not arrived shows `—`, not an empty directory.
- **Relative paths, `.` and `..` work.** They are free, they are real, and they are one of the things a player most usefully transfers.

### 3.3 Command grammar — a real capability

| Element | Form | Real? | Notes |
|---|---|---|---|
| Command name | canonical glossary name, lowercased, spaces → hyphens: `port-sweep`, `rainbow-table`, `side-channel-reader`, `dead-drop` | idiom | Matches `01-visual-language.md` §9.2, which already writes `man rainbow-table` and `theme uos`. Keeps the palette greppable against `../design/glossary.md`. |
| Aliases | real command names where the mapping is genuine: `top`, `ps`, `ss`, `netstat`, `df`, `ls`, `kill`, `jobs`, `id`, `whoami`, `man`, `less` | **real** | The teaching hook: a player types `top` and discovers their rig monitor *is* a `top`. |
| Subcommands | `market search …`, `market install …`, `bot build …` | **real** | The `git` / `apt` / `systemctl` pattern, which is worth learning on its own. |
| Short options | `-n`, `-v`, `-h`; clustering `-vn` | **real** | POSIX utility syntax. |
| Long options | `--dry-run`, `--tier=T2`, `--since 1h` | **real** | GNU style, both `--opt value` and `--opt=value`. |
| End of options | `--` | **real** | Needed for real reasons here: a node address or handle may begin with `-`. |
| Quoting | `'single'` and `"double"`, both literal; no interpolation inside either | idiom | Real shells interpolate inside double quotes; ours does not, because §3.1 rule 4 forbids expansion. `CAVEATS` on `quoting(7)` says so. |
| Case | commands and flags lowercase; matching is case-insensitive, insertion preserves case | idiom | Real shells are case-sensitive. We are forgiving because typing under a trace timer is not the skill being tested (pillar C5). Stated on `shell(7)`. |

### 3.4 The five universal flags

Every command accepts all five. A small universal vocabulary is what makes flags *learnable* rather than memorised, and it is genuinely what good Unix tools do.

| Flag | Effect | Exit |
|---|---|---|
| `-h`, `--help` | Prints `SYNOPSIS` + `OPTIONS` from this command's man page, in place | 0 |
| `--explain` | Prints the man page's `DESCRIPTION` in place, and does **not** run the command | 0 |
| `-n`, `--dry-run` | Prints the published costs and stated requirements. Sends no intent. | 0 |
| `-v`, `--verbose` | Includes attribution detail in the output — which action contributed what (pillar C3) | as normal |
| `--` | End of options | — |

> **`--dry-run` and Invariant I14.** A dry run prints *published, static* figures: the tool's own compute and noise from its item definition, and the gate's requirement in words (`01-visual-language.md` §8.9). It **never** prints a verdict — no "affordable", no "you meet this gate", no computed remainder. Gate evaluation is server-side and rendered as received (`00-client-overview.md` §2, C4). Printing the numbers and letting the player do the arithmetic is both the correct architecture and the better teaching.

### 3.5 Exit statuses — a real capability, with a borrowed numbering

Every command reports a status. The `terminal` window shows the last one in its status line as `$?`, and the command palette shows it as an `es-chip`.

| Status | Name | Meaning here | Convention borrowed from |
|---|---|---|---|
| `0` | success | The server accepted the intent | universal |
| `1` | refused | A game rule applied and **nothing changed** | universal "general error" |
| `2` | usage | Bad invocation: unknown flag, missing argument | GNU convention |
| `69` | `EX_UNAVAILABLE` | **Could not reach the server** — distinct from refused | `sysexits.h` (verified) |
| `75` | `EX_TEMPFAIL` | Sent, no answer yet, or timed out; retry is safe | `sysexits.h` (verified) |
| `77` | `EX_NOPERM` | A gate blocks this — the requirement is printed | `sysexits.h` (verified) |
| `126` | — | Command known, but you do not own or cannot field that tool | shell convention |
| `127` | — | No such command | shell convention |
| `130` | `128 + 2` | **Aborted** — the breach was withdrawn | shell convention: `128 + signal`, and signal 2 is `SIGINT`, what `Ctrl-C` sends |

Three consequences worth stating:

- **`1` versus `69` is Invariant I14 rendered as a number.** `01-visual-language.md` §9.4 requires that "the server refused this" and "we could not reach the server" never collapse into one message. Giving them different exit statuses makes the distinction structural rather than a matter of copywriting discipline.
- **An exit status is a server-owned value and carries an authority state.** Between sending and confirmation the status is `—` with `es-state-pending` (`01-visual-language.md` §2.2.8), never a provisional `0`. The one exception is `verify(1)` (§3.10), whose status the client computes itself by walking the provenance chain — the single client-computed truth in the whole application (`00-client-overview.md` §1.1).
- **`130` is a teaching moment worth the whole scheme.** The abort shortcut is `Shortcut+.` (`00-client-overview.md` §6.3) and it reports `130`; the page for it explains `128 + N`, explains that N=2 is `SIGINT`, and explains that `SIGINT` is what `Ctrl-C` sends in a real terminal — a fact the player can go and use tonight.

### 3.6 Tab completion — a real capability

| Behaviour | Real? |
|---|---|
| `Tab` completes the longest unambiguous prefix | **real** |
| `Tab` again lists candidates (max 12 visible, scrollable, keyboard-navigable) | **real** — the double-Tab convention |
| `Shift+Tab` cycles backwards through candidates | idiom |
| Completion is position-sensitive: verb → command; `--` → that command's flags; path → the namespace; item → owned items; node → **known** nodes | **real** — programmable completion (`bash-completion`, zsh `compsys`) |
| Completion never executes | **real** |
| Matching is case-insensitive, insertion is case-preserving | idiom (see §3.3) |

> **The completion index is built only from state the server has sent as *known*.** Completing an unscanned node address would hand the player, for free, the information `../design/07-recon-tools.md` sells for 15–45 EC and 3–8 compute. This is the least obvious way the client could accidentally become authoritative, and it is a one-line mistake to make.

### 3.7 Pipelines — a real capability, deliberately restricted

**Pipes compose queries. They never compose actions.**

```
ps | grep miner
ss | grep -v ESTAB | wc -l
ls /rig/storage/high | sort -k size -r | head -n 5
log --since 10m | grep canary
```

| Role | Members |
|---|---|
| **Sources** (read-only, always first) | `ps`, `ss` / `netstat`, `df`, `ls`, `log`, `jobs`, `ledger`, `items` |
| **Filters** (any number, in the middle) | `grep [-i] [-v] [-E]`, `sort [-k] [-r] [-n]`, `uniq [-c]`, `head [-n]`, `tail [-n]`, `wc [-l]`, `cut -f` |
| **Sinks** | the `terminal` transcript, as selectable mono text (`01-visual-language.md` §8.7) |

Rules:

- **A pipeline may not contain a command with a side effect.** Not "should not" — the parser rejects it, with a message naming the offending command. This keeps §3.1's boundary simple and keeps a partially-applied pipeline from ever existing under C4.
- **Filters operate on the rendered text, exactly like real Unix.** This is a deliberate fidelity choice with a cost, and the cost is itself the lesson: `ps | grep miner` can match the wrong column, which is precisely why `awk`, `jq` and structured output exist. `grep(1)`'s `CAVEATS` says so.
- **Pipeline exit status is the last command's**, as in a real shell without `pipefail`. `shell(7)` explains this and mentions `set -o pipefail`, because the surprise is real and worth inoculating against.

> **Why this is the strongest educational feature in the client.** `../design/04-mining.md` §3.1 requires that a careful player can find a hidden miner by noticing that two views of the machine disagree. With `ps`, `ss` and a pipeline, that investigation is performed *the way it is really performed*: list processes, list sockets, compare, notice the connection with no owning process. The game does not simulate the skill. It is the skill, on a smaller board.

### 3.8 Globbing — real syntax, and the distinction from regex

Real glob semantics over the §3.2 namespace: `*` (any run of characters, including empty), `?` (exactly one), `[abc]`, `[a-z]`, `[!abc]` negation. No `**`.

```
ls /rig/tools/*-sweep
kill /net/*/miners/*
```

> **Glob is not regex, and the client teaches the difference by containing both.** In a glob, `*` means "any characters." In `grep`'s pattern, `*` means "zero or more of the thing before it," and "any characters" is `.*`. Beginners conflate these constantly. Because the palette has globs in path position and regex in `grep` position, the confusion *will* arise in play — and `glob(7)` and `grep(1)` each answer it, with the other in `SEE ALSO`. A confusion the player hits themselves is worth ten paragraphs of prevention.

### 3.9 Keys — what is borrowed, and where the desktop wins

| Key | Where | Behaviour | Note |
|---|---|---|---|
| `Tab` / `Shift+Tab` | palette, terminal | completion (§3.6) | **real** |
| `Up` / `Down` | palette, terminal | history | **real** (readline) |
| `Ctrl+R` | palette, terminal | reverse search through history | **real** (readline) — ⚠ see collision note |
| `Ctrl+A` / `Ctrl+E` | palette, terminal | start / end of line | **real** (readline) — ⚠ collides with Select All on Windows/Linux |
| `/`, `n`, `N` | `recon`, `man` | search, next, previous | **real** (`less(1)`) |
| `g` / `G` | `recon`, `man` | top / bottom | **real** (`less(1)`) |
| `q` | `man` popover, `recon` reader | close | **real** (`less(1)`) — in addition to `Escape`, never instead of it |
| `Shortcut+/` | anywhere | gloss for the focused term (`01-visual-language.md` §8.10) | idiom |
| `Shortcut+Shift+/`, `F1` | anywhere | full man page for the focused term | idiom — `Shortcut+Shift+/` is `⌘?`, the macOS Help convention; `F1` is the Windows/Linux one |
| `Shortcut+Shift+E` | anywhere | cycle teaching level (`00-client-overview.md` §6.3) | idiom |

> **Where the desktop wins, and it always does.** `Ctrl-C` is **copy**, everywhere, permanently. It is not `SIGINT`, it does not abort a breach, and it is not remappable. `01-visual-language.md` §8.7 requires log lines to be copyable because manual investigation depends on it, and breaking the universal copy key to score a diegetic point would be exactly the "immersion as a defect" failure `00-client-overview.md` §2 warns about under pillar C1. The abort is `Shortcut+.` (a genuine macOS cancel convention), it reports exit `130`, and its man page tells the player what `Ctrl-C` does in a real terminal. **Teaching a key is not the same as stealing it.**
>
> ⚠ The same reasoning applies to `Ctrl+A` and `Ctrl+R`: they are readline conventions *and* Select All / Replace on two platforms. Proposal: readline bindings are active **only when a single-line command input holds focus**, where Select All is near-worthless, and never in a text pane. Flagged as **T-4** — this needs a real decision, per platform, before the palette is built.

### 3.10 The command catalogue

Sections follow real `man` numbering (§4.3): **1** = commands, **5** = record formats, **7** = concepts, **8** = maintaining your own rig. The split is meaningful, not decorative: offense is section 1, defending your own machine is section 8, exactly as user commands and system-administration commands split in reality.

| Command | § | Acts on | Real counterpart | Note |
|---|---|---|---|---|
| `man`, `whatis`, `apropos` | 1 | `man` | `man(1)`, `whatis(1)`, `apropos(1)` | §4 |
| `help` | 1 | palette | — | Lists commands you can currently run |
| `history` | 1 | `terminal` | `history(1)` | The transcript |
| `teach --level=explain\|terms\|off`, `teach --reset` | 1 | settings | — | The teaching level; equivalent to `Shortcut+Shift+E` |
| `theme [--list] [<id>]` | 1 | settings | — | Already specified in `00-client-overview.md` §4.2 |
| `top` | 1 | `rig-monitor` | `top(1)` | Raises the rig monitor |
| `ps [--by=consumer]` | 1 | `audit` | `ps(1)` | Compute allocation by consumer |
| `ss`, `netstat` | 1 | `audit` | `ss(8)`, `netstat(8)` | Connection table. `netstat` prints a deprecation note — as the real one increasingly does |
| `df [-h]` | 1 | `storage` | `df(1)` | Three tiers as mount points, plus an **Exposure** column the real one has no equivalent for |
| `ls`, `stat` | 1 | `storage` | `ls(1)`, `stat(1)` | |
| `mv <item> <tier>` | 1 | `storage` | `mv(1)` | Sends intent; the risk change is the point (`../design/01-core-resources.md` §6) |
| `log [-f] [--since]` | 1 | any | `journalctl -f`, `tail -f` | Severity glyphs follow RFC 5424's eight levels (verified) |
| `map`, `traceroute <node>` | 1 | `map` | `traceroute(8)` | ⚠ Not `trace` — §2.15 |
| `port-sweep`, `passive-sniffer`, `topology-mapper`, `traffic-analyzer`, `ping-sweep`, `honeypot-detector`, `provenance-tracer` | 1 | `map`, `recon` | §2.5 | One per recon tool |
| `fuzzer`, `rainbow-table`, `overflow-kit`, `credential-harvester`, `zero-day`, `side-channel-reader` | 1 | `terminal` | §2.6 | One per intrusion tool |
| `breach <node>` | 1 | `terminal` | — | Starts the minigame |
| `abort` | 1 | `terminal` | — | Exit `130`; always confirms (`00-client-overview.md` §6.3) |
| `log-scrubber`, `identity-spoofer`, `traffic-shaper`, `dead-drop`, `relay-chain`, `ghost-protocol` | 1 | various | §2.7 | |
| `mine [--allocate=N]`, `deploy`, `collect` | 1 | `mining` | — | |
| `crack`, `kill`, `hijack`, `sabotage` | 1 | `audit`, `mining` | `kill(1)` is exact | The four responses (`../design/04-mining.md` §5) |
| `jobs`, `bot build\|stop` | 1 | `botnet` | `jobs(1)`, `systemctl(1)` | |
| `id`, `whoami` | 1 | `identity` | `id(1)`, `whoami(1)` | |
| `market search\|show\|install` | 1 | `market` | `apt(8)` | `show` prints the blocking gate in words |
| `ledger [--since] [--counterparty]` | 1 | `ledger` | — | |
| `verify <item>` | 1 | `storage` | `git verify-commit`, `gpg --verify` | **The only client-computed exit status** (§3.5) |
| `item-history <item>` | 1 | `storage` | `git log` | Walks `prevRecordHash` to genesis (`../architecture/04-item-provenance.md` §6.1) |
| `scan --quick\|--full\|--thorough` | 8 | `audit` | `rkhunter(8)`, `chkrootkit(8)` | Costs 5 / 15 / 35 compute (`../design/04-mining.md` §3.2) |
| `firewall`, `canary`, `tarpit`, `honeypot-stash`, `auto-counter-daemon`, `detection-array` | 8 | `defense` | §2.8 | |
| `grep`, `sort`, `uniq`, `head`, `tail`, `wc`, `cut` | 1 | pipelines | the real ones | §3.7 |
| `provenance-record`, `resolution-record`, `ledger-entry` | 5 | — | file-format pages | Record shapes, documented like `crontab(5)` |
| `compute`, `noise`, `heat`, `trace`, `storage-tiers`, `unlock-gates`, `shell`, `glob`, `quoting`, `did`, `quorum` | 7 | — | concept pages | No `SYNOPSIS` — see §4.3 |
| `eyeandsickle` | 6 | — | — | The root page. Section 6 is **games** in real man numbering, so this is correct, not a joke |

### 3.11 What is stylistic borrowing only

Stated plainly so nobody implements a shell by accident, one plausible feature at a time.

| Looks like | Actually is | Why not the real thing |
|---|---|---|
| A shell prompt | A game command input | §3.1 |
| `$?` in the status line | A rendered server-owned value with an authority state | §3.5 |
| Man pages | Authored game content in man-page *form* | Not `groff`, not `troff`, no `man` binary |
| Paths | Keys into an in-memory tree | §3.1 rule 3 |
| `kill`, `jobs`, `top` | Game actions with borrowed names | Nothing OS-level is touched |
| Case-insensitive commands, no interpolation | Deliberate divergences | §3.3 — each stated on `shell(7)` |
| Any of `>` `\|\|` `&&` `;` `` ` `` `$( )` `&` | Parse errors | §3.1 rule 5. The error message is specific and teaches: *"Redirection is not available — this is a game surface, not a shell. See shell(7)."* Not a generic syntax error, because a generic error invites a player to keep guessing. |

---

## 4. The teaching system

### 4.1 Three tiers, and exactly what triggers each

| Tier | Surface | Trigger | Content | Interrupts? |
|---|---|---|---|---|
| **1 — gloss** | The **gloss bar**: a persistent one-line region at the foot of every tool window | Hover, keyboard focus, or caret entering a marked term. Also filled once on a first encounter at level `explain` | One sentence, ≤ 72 characters, plain language | Never — the region always exists and never overlays content |
| **2 — page** | A `man` page, shown in an `atlantafx.base.controls.Popover` (verified present in AtlantaFX 2.1.0) anchored to the term, or in the `man` window | `Shortcut+Shift+/` or `F1` on the focused term; hover dwell ≥ 600 ms; `man <term>`; clicking the term's marker | The full page (§4.3) | Never steals focus; suppressed on hover during a live engagement |
| **3 — further reading** | The page's `FURTHER READING` section | Reading the page | Real citations: `nmap(1)`, RFC 9293 §3.1, ATT&CK T1496 | Never auto-opens anything (§4.4) |

**Tier 1 is a persistent region, not a popup, and that is the load-bearing decision in this section.** It resolves an apparent contradiction in the source: `00-client-overview.md` §5.2 requires that at level `explain` "the first appearance of a term in a session surfaces the definition inline once", while §5.4 forbids definitions that interrupt, open on their own, or move layout. A popup cannot satisfy both. A fixed-height region that is always present can: it never reflows (it is always there, empty or not), it never occludes (it is outside the content area), and filling it is not an interruption because nothing moves and nothing takes focus.

It is also the most Unix answer available. `less(1)` has a status line. `vi` has a command line. A one-line strip at the bottom of a tool that tells you about the thing under the cursor is native to the register, not imported into it.

### 4.2 Tier 1 — the gloss bar

**Class:** `es-term es-term-bar`. No new primitive is introduced: `01-visual-language.md` §1.2's grammar already permits `es-<primitive>-<variant>` (as in `es-gauge-compute`), and the bar renders exactly the Tier-1 content of the `es-term` anatomy in §8.10 — term, status chip, definition — laid out on one line.

| Property | Value |
|---|---|
| Height | 24 px comfortable, 20 px compact — **fixed**, present whether or not it has content |
| Position | Bottom edge of the window's content area, below all panels, above the window frame |
| Type | Term: mono `TYPE_MONO_CAPTION` (12/18) if it is a literal identifier, UI `TYPE_CAPTION` otherwise. Definition: UI face, `TYPE_CAPTION` |
| Colour | `-es-surface-sunken` fill, `-es-fg-secondary` text, `-es-border-divider` top edge |
| Format | `term — one-sentence gloss` followed by the status chip. **This is `whatis(1)` output format**, and saying so on `whatis(1)`'s own page is free teaching |
| Empty state | Blank. Never a hint, never "hover a term", never a rotating tip |
| Latency | Fills on hover/focus with no delay. It cannot be mistimed because it cannot occlude anything |
| Gloss budget | **72 characters.** At `TYPE_MONO_CAPTION` (12 px) with a 0.6 em advance, a 560 px content width minus `SPACE_3` padding on both sides leaves ~74 characters. 72 also happens to be the classic terminal measure. ⚠ Assumes a minimum tool-window content width of **560 px**; `05-tool-windows-and-layout.md` must not go below that without renegotiating this number |
| During a live breach | **Still active.** It is passive and occludes nothing, so `00-client-overview.md` §5.4's "never during a live breach" restriction applies to the *popover*, not to this |

The gloss bar is also where a **first encounter** is surfaced at level `explain` (§4.5), which is what discharges §5.2's "inline once" requirement without violating §5.4.

### 4.3 Tier 2 — the `man` page

#### 4.3.1 Structure

Real man-page section order, verified against `man-pages(7)`, which names **NAME, SYNOPSIS, DESCRIPTION** and **SEE ALSO** as the essential sections and defines the canonical order. We use a subset of the real sections plus two clearly-marked additions:

| Section | Real? | Present on | Content |
|---|---|---|---|
| `NAME` | real | every page | `term — one-line gloss`. **The same string as the gloss bar** — one source, two surfaces |
| `SYNOPSIS` | real | **section 1, 5, 8 only** | Invocation, with real notation: `[ ]` optional, `...` repeatable, `\|` alternatives (verified). Concept pages (section 7) have **no** `SYNOPSIS`, exactly as real section-7 pages usually do not — the absence teaches the section system |
| `DESCRIPTION` | real | every page | What it is *in this game*, in the player's terms |
| `OPTIONS` | real | section 1, 8 | Every flag, including the five universals (§3.4) |
| `EXIT STATUS` | real | section 1, 8 | Only the statuses this command can actually produce |
| `REAL-WORLD COUNTERPART` | **game-added** | every page | Opens with the status marker. Names the real thing and the real tool or standard |
| `CAVEATS` | real | wherever the model diverges | **Mandatory** on every `real, simplified` page and every §2.15 homonym |
| `SEE ALSO` | real | every page | In-game refs in real `name(section)` form: `noise(7), port-sweep(1), traffic-shaper(1)` |
| `FURTHER READING` | **game-added** | where it earns its place | Real citations (§4.4) |

Two sections are game additions and are visually marked as such — a small `game-added section` label in `-es-fg-tertiary` beside the heading. A player who later opens a real man page and finds no `REAL-WORLD COUNTERPART` should not think their memory is faulty.

#### 4.3.2 Rendering

**Class:** `es-term es-term-page`.

```
PORT-SWEEP(1)                Operator Commands                PORT-SWEEP(1)
```

| Property | Value |
|---|---|
| Header / footer | Real man style: `NAME(SECTION)   section title   NAME(SECTION)`, mono, `TYPE_MONO_CAPTION`, `-es-fg-secondary`. Footer carries the content revision |
| Section headings | Mono, `TYPE_MONO_BODY` weight 600, flush left, `-es-fg-primary` |
| Body prose | **UI face**, `TYPE_BODY` (14/20), indented 7 characters from the left margin like a real man page |
| Body identifiers | Mono inline: command names, flags, paths, hashes, RFC numbers, `name(section)` refs — mandatory per `01-visual-language.md` §3.5 rules 2, 5 and 6 |
| Measure | 72 characters maximum |
| Panel width | 560 px default, 420 px minimum, resizable |
| Status chip | In the header row, and again as the first token of `REAL-WORLD COUNTERPART` |

> **Why body prose is UI face and not mono, in a document about being faithful to Unix.** `01-visual-language.md` §3.5 is explicit: teaching-layer definitions are UI face, and "mono for running prose is a legibility cost with no compensating benefit." The token contract outranks the aesthetic. What we keep is everything that *carries meaning*: the structure, the section names, the `name(section)` notation, the `SYNOPSIS` grammar, and mono for every identifier. What we give up is a monospaced paragraph, which was never the part that taught anything. Stated here rather than buried, because it is the most likely thing for an implementer to "fix" back the wrong way.

#### 4.3.3 The popover, and its four traps

The Tier-2 popover uses `atlantafx.base.controls.Popover` — verified present in `atlantafx-base-2.1.0`, with `show(Node owner)`, `detach()`, `detachable`, `closeButtonEnabled`, `headerAlwaysVisible`, and a twelve-value `ArrowLocation` enum, all read from the shipped source.

1. **It must not steal focus, but Escape must still close it.** `javafx.stage.PopupWindow.hideOnEscape` defaults to `true` — but its javadoc scopes that to "while the popup **has focus**" (verified). A popover that deliberately never takes focus therefore *never sees the key*. **The owning window installs the `ESCAPE` key filter and hides the popover itself.** This is the single most likely accessibility regression in the feature, and it is invisible in manual testing by anyone who clicks the popover first.
2. **Tear-off is free and worth having.** `Popover` sets `autoHide` to `!detached` on the `detached` property (read from the source), so dragging a page away from its term gives a persistent panel that survives clicking elsewhere. That is a genuine "keep this open while I work" affordance at zero cost, and it maps to having a man page open in another terminal.
3. **`headerAlwaysVisible(true)` and `closeButtonEnabled(true)`.** SC 1.4.13 *Dismissible* needs a mechanism; a visible close control plus Escape is two.
4. **Never during a live engagement, on hover.** `00-client-overview.md` §5.4 forbids interrupting during a running timer. Hover dwell is disabled while an engagement is live; `Shortcut+Shift/`, `F1` and `man` still work, because those are the player asking.

### 4.4 Tier 3 — further reading

Content: real, checkable citations. `nmap(1)`. RFC 9293 §3.1. ATT&CK T1496.001. `cryptsetup(8)`. A named paper. A named incident.

Rules:

- **Citations render as plain, selectable, copyable text by default.** No auto-linking, no link colour, no underline.
- **Each entry carries one explicit action**, which confirms with the full URL shown before calling `HostServices#showDocument` (verified API). No URL carries a query string, ever — a reading link that identifies the player would make the client a telemetry client by the back door (`00-client-overview.md` §7).
- **No citation is required reading**, and nothing in the game references having followed one.
- **Prefer stable, primary, free sources**: RFCs, `man7.org`, W3C recommendations, MITRE ATT&CK, project documentation. Avoid anything paywalled, anything that rots, and anything whose framing is a vendor's.
- ⚠ **Do not cite offensive-tooling walkthroughs.** Naming `nmap(1)` and explaining what a port scan is, is education. Linking a guide to scanning networks you do not own is not the game's business. Citations name *concepts and specifications*; the player can find tutorials without our help.

### 4.5 Levels, decay, and first encounter

The three levels are fixed by `00-client-overview.md` §5.2 (`explain` default on a fresh profile, `terms`, `off`). This section supplies the mechanics they left open.

| Level | Marker shown when | First encounter | Gloss bar | Tier 2 |
|---|---|---|---|---|
| `explain` | Always, on every marked term | Chip + auto-filled gloss bar, once per term per profile | Fills on hover/focus | On request or hover dwell |
| `terms` | Only on terms **not yet settled** (below) | Chip only | Fills on hover/focus | On request or hover dwell |
| `off` | Never | Never | Fills on **focus only**, not hover | On request only |

**Settling — the decay rule.** A term is *settled* for a profile when either:

- it has accumulated **3 sightings**, or
- its `man` page has been opened **once**.

Opening the page settles it immediately, because a player who read the page does not need the reminder, and that is a far better signal than a count.

**What counts as a sighting.** At most **one per term, per window, per session**, and only when the term was inside the viewport for **≥ 1 second**. Without both clauses, scrolling a 200-line log burns every counter in the game without the player reading a word — the most obvious way this feature quietly stops working.

**Reset.** The `man` window offers "mark all terms unseen" and `teach --reset` does the same. Counters are client-owned per-profile state (`00-client-overview.md` §4.5) and never round-trip to the server.

**First-encounter highlighting — exactly once, and here is what "once" means.** The first time a term is *rendered in this profile*, it carries an `es-chip` reading `new term` (glyph + text, per `01-visual-language.md` §2.4 — never colour alone) beside the standard `es-term` marker. At level `explain`, the gloss bar is additionally filled with that term's gloss at the same moment. The chip is removed the next time that term renders, and never returns. Not once per session — once, ever, per profile, per term.

The marker never changes the term's metrics (`01-visual-language.md` §8.10). Changing teaching level, or a term settling, must not reflow a single line of text — the marker occupies its space whether drawn or not.

### 4.6 The `man` window and the term index

Window id **`man`** (⚠ a fourteenth id — §2.2, **T-1**). Unix analogue: `man(1)` / `apropos(1)`.

Contents:

- **Index**, browsable by section (1, 5, 6, 7, 8) and by domain (resources, recon, intrusion, stealth, defense, rig, mining, bots, identity, federation). Sorted with `java.text.Collator` for the active locale, not by ASCII.
- **Search** — `apropos` semantics: matches the `NAME` line of every page, which is what real `apropos` does. `--all` extends to full text.
- **Status filter** — show only `game` entries, or only `real, simplified`. A player who wants to know exactly what this game made up is entitled to a one-click answer, and being able to give one is the strongest possible statement that the labelling is honest.
- **Seen/unseen filter**, and the "mark all unseen" reset.
- **Reading history**, so a player can get back to a page they half-read during a breach.

Per `00-client-overview.md` §5.2, **definitions are never destroyed, only quieted**: at every level, including `off`, `man <term>` resolves and this window works.

### 4.7 The four "never" rules, made mechanical

`00-client-overview.md` §5.4 states them; here is what each forbids in code.

| Rule | Mechanical form |
|---|---|
| **Never gate progress** | No teaching-layer state is ever an input to any intent the client sends. No server call carries a "has read" flag. There is nothing to gate on |
| **Never interrupt** | Nothing in the teaching layer calls `requestFocus()`, `toFront()`, or `setAlwaysOnTop()`. Tier 2 opens on explicit request or hover dwell only, and hover dwell is disabled while an engagement is live |
| **Never move layout** | The gloss bar has a fixed height and always exists. The `es-term` marker occupies its space whether drawn or not. Tier 2 is a popover, never an inline expansion |
| **Never replace the label** | The marker decorates a term that is already fully readable. A term is never abbreviated, truncated, or replaced by an icon because a definition exists |

One addition of this document's own: **the teaching layer never renders a server-owned value.** A page may say what compute *is*; it never says how much you have. That keeps the entire content set static, cacheable, translatable and free of authority states — and it means a `man` page can never be wrong about the game state, because it never claims to know it.

### 4.8 The content contract

#### 4.8.1 Storage

```
client/src/main/resources/terms/
    en/
        1/port-sweep.md
        1/dead-drop.md
        5/provenance-record.md
        7/compute.md
        7/noise.md
        7/storage-tiers.md
        8/scan.md
    de/
        7/compute.md
    index.json                (generated at build time, never hand-edited)
```

**One file per term, per locale.** Not one big file: a writer edits one page, a reviewer diffs one page, a translator is handed one page, and a merge conflict touches one page. **Not hardcoded strings:** a string in Java cannot be reviewed by a writer, cannot be handed to a translator, and cannot be checked against the glossary by CI.

#### 4.8.2 File format

A fixed-key header block, then man-style sections. No YAML library, no Markdown library, no new dependency — the parser is a hundred lines and the format is stable because the key set is closed.

```
---
id: port-sweep
section: 1
name: Port Sweep
canonical: Port Sweep
gloss: Lists which services a node is listening for connections on.
status: real
aliases: portsweep, ports
glossary: ../design/glossary.md
seeAlso: enumeration(7), passive-sniffer(1), noise(7), ping-sweep(1)
reading: nmap(1) | RFC 9293 §3.1 (TCP header format, ports are 16-bit)
notes: "sweep" here is the recon tool, not the Eye action — see sweep(7).
revision: 3
---

## DESCRIPTION
...

## REAL-WORLD COUNTERPART
...
```

| Key | Required | Rule |
|---|---|---|
| `id` | yes | Lowercase, hyphenated, unique within a section. The filename must match |
| `section` | yes | One of 1, 5, 6, 7, 8 |
| `name` | yes | Display name |
| `canonical` | when the term exists in the glossary | **Byte-for-byte** the glossary's spelling, including capitalisation (`01-visual-language.md` §9.2). This is the join key for the drift test |
| `gloss` | yes | One sentence, ≤ 72 characters, plain language, no jargon that is not itself a term |
| `status` | yes | `real` \| `real, simplified` \| `game` |
| `aliases` | no | Alternate spellings the index and `apropos` resolve |
| `seeAlso` | yes | `name(section)` refs. Every one must resolve (§4.10) |
| `reading` | no | Tier 3 citations |
| `notes` | no | **Translator and writer guidance.** Not rendered. Necessary given §2.15's homonyms |
| `revision` | yes | Increment on any body change; drives the translation-staleness check |

Body sections use `## SECTIONNAME` with the names from §4.3.1 and nothing else. An unrecognised section name fails the build rather than rendering — silent-drop is how content quietly goes missing.

#### 4.8.3 Writing rules

1. **`gloss` is one sentence and avoids the word it defines.** "Compute is the compute you have" fails review.
2. **`DESCRIPTION` is game-first.** The player opened this because something in front of them was unclear. Answer that first, then generalise.
3. **`REAL-WORLD COUNTERPART` opens with the status word**, then names the artefact. `real — port scanning; the standard tool is nmap(1).`
4. **`CAVEATS` is mandatory on every `real, simplified` page**, and names the simplification in the first sentence. A page that cannot articulate its own simplification has not understood it.
5. **Reading level.** Plain language, short sentences, no unglossed jargon. WCAG SC 3.1.5 *Reading Level* (Level AAA, verified) asks for supplemental content where text exceeds a lower-secondary reading level; the Tier-1 gloss **is** that supplement for every page, which is a rare case of an accessibility requirement and a product goal being the same feature.
6. **No second person imperative about the real world.** "Real defenders ship logs off the box" — not "you should ship your logs off the box."
7. **Length ceiling: 400 words of body prose.** Past that, split the concept or accept that the `FURTHER READING` line is doing its job.
8. **Every factual claim is verified before merge, not remembered.** §2.16 rule 2, applied to prose.

### 4.9 Six worked entries

Templates to copy. Shown as page content; §4.3.2 governs how each renders.

---

#### `compute(7)`

```
COMPUTE(7)                      Concepts                       COMPUTE(7)

NAME
       compute, cycles — the rig's capacity budget; the master scarcity

DESCRIPTION
       Your rig has a fixed budget of cycles — 100 on a starting rig.
       Everything you run holds some of it for as long as it runs: bots,
       armed defenses, mining, a control channel for each deployed
       miner, each relay hop. Compute is capacity, not currency: it is
       not spent and gone, it is allocated and returned.

       Four numbers matter, and the rig monitor always shows all four.
       Allocated is held right now, always broken down by what is
       holding it. Available is free to commit. Recovering is on its way
       back, with a clock. Total is your ceiling.

       Cycles are never purchasable. Capacity comes from schematics and
       story milestones only. See unlock-gates(7).

REAL-WORLD COUNTERPART
       real, simplified — CPU scheduling and resource quotas.

       A real machine has a fixed number of hardware threads, and the
       operating system's scheduler divides them among everything that
       wants to run. nproc(1) prints how many you have; top(1) shows
       what is using them. On Linux, cgroups let you give a workload a
       hard share of the CPU — cpu.max — and cloud machines are sold in
       vCPUs, which is the same idea with a price attached.

CAVEATS
       A game cycle is not a CPU clock cycle. A real clock cycle is one
       tick of the processor's clock; there are billions per second.
       A game cycle is a unit of capacity share, much closer to a vCPU
       or a cgroup quota.

       Real CPUs time-share. Two processes each asking for the whole
       machine both run, each slower. This rig reserves instead: if it
       is allocated, nobody else gets it. Reservation is the exception
       in real systems, not the rule.

       Cycles that "recover" are a game rule. What is real is the idea
       underneath it: a chip that cannot shed heat slows itself down, so
       a machine under sustained load genuinely is slower than the same
       machine idle. See thermal-budget(7).

SEE ALSO
       thermal-budget(7), top(1), ps(1), noise(7), unlock-gates(7)

FURTHER READING
       nproc(1), top(1); cgroups v2 "cpu.max"; CPU scheduling in any
       operating-systems textbook.
```

---

#### `noise(7)`

```
NOISE(7)                        Concepts                         NOISE(7)

NAME
       noise — the short-term visibility cost of doing things

DESCRIPTION
       Every action you take adds noise. It decays on its own, so
       waiting is a real tactic, and it pools across you and every bot
       you are running, so more bots means a louder you even when you
       personally do nothing.

       Defenders watch for thresholds. Crossing one is an event, not a
       gradient — the response either fires or it does not. Noise also
       feeds the trace during a breach. See trace(7).

       Tools are priced in noise as a first-class stat, from none
       (passive-sniffer(1), zero-day(1)) to very high (overflow-kit(1)).

REAL-WORLD COUNTERPART
       game — but the idea it stands for is entirely real.

       Practitioners genuinely call a technique "noisy". A fast port
       scan writes thousands of log lines and trips intrusion-detection
       signatures immediately; the same scan run slowly may pass under
       the alerting threshold, which is why nmap(1) ships timing
       templates from -T0 (paranoid) to -T5 (insane). Defenders really
       do set volume thresholds, and attackers really do stay under
       them.

CAVEATS
       Detection is not a number. There is no scalar anywhere in a real
       network that goes up when you act and down when you wait.

       And real logs do not decay. A line written yesterday is still
       there tomorrow unless somebody deletes it — which is exactly why
       log-scrubber(1) exists in this game, and exactly why real
       defenders ship their logs to a machine the attacker does not
       control.

SEE ALSO
       heat(7), trace(7), traffic-shaper(1), log-scrubber(1),
       identity-spoofer(1)

FURTHER READING
       nmap(1) timing templates -T0..-T5; RFC 5424 (syslog severities).
```

---

#### `vault(7)`

```
VAULT(7)                        Concepts                         VAULT(7)

NAME
       vault, Encrypted Vault — storage nobody can reach

DESCRIPTION
       The safest of the three storage tiers. Items in the vault cannot
       be raided, online or offline. It is also the smallest, it grows
       sub-linearly, and it is never purchasable — capacity comes from
       the Cold Storage Expansion schematic. See storage-tiers(7).

       The trade is deliberate: anything you socket into a bot leaves
       the vault and becomes exposed. Safety and productivity are
       mutually exclusive here, on purpose.

REAL-WORLD COUNTERPART
       real, simplified — encryption at rest, and cold storage.

       Full-disk and volume encryption are ordinary: LUKS on Linux
       (cryptsetup(8)), FileVault on macOS, BitLocker on Windows, and
       age or GPG for single files. "Cold storage" is the wallet term
       for keys kept on a device that is not connected to anything.

CAVEATS
       Real encrypted storage is only safe while it is locked. The
       moment you unlock a volume to use a file, anything running as you
       can read it — including anything that should not be running as
       you. Encryption at rest protects a stolen disk, not a live
       machine.

       This vault is safe even while you play, which no mounted volume
       is. The honest analogue is true cold storage: a device that is
       powered off and unplugged. Which is why taking something out to
       use it costs you the protection — that part is not a game rule,
       that is the real trade.

SEE ALSO
       storage-tiers(7), cold-storage-expansion(8), df(1), ls(1)

FURTHER READING
       cryptsetup(8); the LUKS2 on-disk format specification.
```

---

#### `port-sweep(1)`

```
PORT-SWEEP(1)               Operator Commands              PORT-SWEEP(1)

NAME
       port-sweep — list the services a node is listening with

SYNOPSIS
       port-sweep [-n] [-v] [--] <node>

DESCRIPTION
       Basic enumeration, and part of the starting kit. Costs 2 cycles
       and generates low noise. Returns the services a node has open,
       which is what an Enumeration layer asks you to work out.

       Deliberately weak. Better recon — passive-sniffer(1),
       topology-mapper(1), traffic-analyzer(1) — exists because this
       tool is the floor, not the ceiling.

OPTIONS
       -n, --dry-run   Print the published cycle and noise cost. Send
                       nothing.
       -v, --verbose   Include per-service detail in the result.
       --explain       Print this DESCRIPTION and exit.

EXIT STATUS
       0    The sweep was accepted.
       1    Refused — a rule applied and nothing changed.
       69   The server could not be reached. Nothing was sent.
       126  You do not currently have this tool fielded.

REAL-WORLD COUNTERPART
       real — port scanning.

       A port is a 16-bit number, 0 to 65535, that identifies one end of
       a TCP or UDP conversation on a host (RFC 9293 §3.1). A service
       "listens" on one: a web server on 443, SSH on 22. Scanning is
       asking, one port at a time, whether anything answers. The
       standard tool is nmap(1): -sS sends a half-open SYN, -sT opens a
       full connection, -sU tries UDP, and -sV goes further and guesses
       which software is answering.

       Closed TCP ports usually answer with a reset. Filtered ones
       answer nothing at all, which is why scans are slow — you are
       waiting out a timeout for every silent port.

CAVEATS
       Scanning is loud in reality too. A full scan writes thousands of
       lines into the target's logs and matches intrusion-detection
       signatures designed for exactly this. The noise cost here is not
       a balance tax; it is the truth.

       A real scan returns open, closed or filtered for each port and
       leaves the interpreting to you. This one hands you a tidy list.

SEE ALSO
       enumeration(7), passive-sniffer(1), ping-sweep(1), noise(7)

FURTHER READING
       nmap(1); RFC 9293 §3.1 (TCP header format); the IANA service
       name and port number registry.
```

---

#### `provenance-chain(7)`

```
PROVENANCE-CHAIN(7)             Concepts             PROVENANCE-CHAIN(7)

NAME
       provenance chain — an item's signed history, checkable by you

DESCRIPTION
       Every item carries a chain of records covering its whole life:
       minted here, granted there, traded, won in a duel. Each record
       names the hash of the record before it, and each is signed by
       whoever issued it.

       Your client walks that chain itself, against the issuers' public
       keys, rather than taking the server's word that it checked.
       That is the one thing this client works out for itself — every
       other number on your screen is the server's. See verify(1) and
       item-history(1).

       A chain that fails any step means the item is not recognised.
       Not "suspicious": not recognised.

REAL-WORLD COUNTERPART
       real — hash chains and signed logs. Not a metaphor: the same
       standards, used the same way.

       Each record names the hash of the one before it, so altering an
       old record changes every hash after it. Git does exactly this: a
       commit names its parent, and the commit id is a hash over content
       that includes that parent, which is why rewriting history changes
       every id downstream and why a force-push is visible.

       Signing is ordinary too — git commit -S, git verify-commit,
       gpg --verify. Certificate Transparency (RFC 6962) and Sigstore's
       Rekor are public append-only logs whose entire security argument
       is that anybody can check them without trusting the operator.

       This game's records are detached JWS (RFC 7515) over JSON put in
       a canonical byte form with JCS (RFC 8785), signed Ed25519
       (RFC 8032), with keys named by DID. Canonicalisation matters more
       than it sounds: the same JSON object can be written many ways,
       and a signature is over bytes, so both sides must agree on the
       bytes first.

CAVEATS
       A chain proves what a set of keys attested. It does not prove the
       events happened. If every key in a chain belongs to one dishonest
       server, the chain verifies perfectly and the item is still
       fabricated.

       That is why cross-server outcomes here need a quorum of
       independent signers rather than one authority — and it is the
       same reason real transparency logs need independent witnesses.
       Verification tells you a story is consistent, not that it is
       true.

SEE ALSO
       verify(1), item-history(1), provenance-record(5), did(7),
       quorum(7), provenance-tracer(1)

FURTHER READING
       RFC 7515 Appendix F (detached content); RFC 8785; RFC 8032;
       git-log(1) --show-signature; RFC 6962 (Certificate Transparency).
```

---

#### `dead-drop(1)`

```
DEAD-DROP(1)                Operator Commands               DEAD-DROP(1)

NAME
       dead-drop — move value with no public ledger entry

SYNOPSIS
       dead-drop [-n] [-v] [--] <recipient> <amount|item>

DESCRIPTION
       Transfers ethecoin or an item without writing a row to the public
       ledger. Costs 4 cycles. Reputation-gated, because untraceable
       movement would wreck the investigator economy if anyone could do
       it — ledger analysis is how players and NPCs build cases.

       Everything else you move is followable. See ledger-entry(5).

OPTIONS
       -n, --dry-run   Print the published cost and the gate's stated
                       requirement. Send nothing.
       -v, --verbose   Show what the transfer would and would not leave
                       behind.

EXIT STATUS
       0    Accepted.
       1    Refused — a rule applied and nothing changed.
       69   The server could not be reached. Nothing was sent.
       77   A gate blocks this; the requirement is printed above.

REAL-WORLD COUNTERPART
       real — the term is genuine tradecraft, and it has a genuine
       computing sense.

       A dead drop is a place where two people exchange something
       without meeting, so neither is ever seen with the other. In
       computing the phrase survives as the "dead drop resolver":
       malware that reads its next instruction from an ordinary public
       service — a gist, a paste site, a social profile — so that its
       traffic looks like everyone else's. MITRE ATT&CK catalogues it as
       T1102.001.

       The ledger half is real as well. Public blockchains are public;
       following the money across them is an industry; and mixing
       services, CoinJoin and privacy-focused coins exist precisely
       because "the ledger is public by default" is a real property with
       real consequences.

CAVEATS
       Here, a dead drop leaves no entry at all. In reality nothing
       removes a transaction from a public ledger. The real techniques
       break the link between sender and receiver — they never delete
       the record, and they are probabilistic. This is the strong form
       of something that in life is only ever a matter of degree.

SEE ALSO
       ledger-entry(5), relay-chain(1), heat(7), identity-spoofer(1)

FURTHER READING
       MITRE ATT&CK T1102.001 (Dead Drop Resolver); introductory
       material on blockchain analysis and CoinJoin.
```

---

### 4.10 Binding to the glossary — three CI checks

Terminology drifts silently. These three tests make it fail loudly. All are cheap, all run in `mvn verify`, none need Docker.

| Check | Asserts | Fails when |
|---|---|---|
| **Coverage** | Every canonical term in `../design/glossary.md` has a term file whose `canonical:` matches it **byte for byte** | A term is added to the glossary and never explained; or capitalisation drifts (`Rainbow table` vs `Rainbow Table`) |
| **Orphans** | Every `canonical:` in the term tree appears in the glossary, or in an explicit `ui-only.txt` allowlist for interface terms with no game-design existence (`gloss bar`, `command palette`) | A term is invented in the teaching layer that the design does not have |
| **Integrity** | Every `seeAlso` ref resolves; every `id` is unique per section; every `status` is one of the three; every `gloss` is ≤ 72 characters; every body section name is recognised; every `real, simplified` page has a `CAVEATS` section | A page grows a dangling `SEE ALSO`, a gloss that overflows the bar, or a simplification with no caveat |

A fourth check belongs to the UI rather than the content: **every string rendered with the `es-term` marker resolves to a term id.** An orphan marker — a dotted underline that opens nothing — is worse than no marker, because it teaches the player that the feature is unreliable.

CLAUDE.md already asks that docs and code stay searchable against each other, and notes that "prose alone erodes under constant reasonable-sounding pressure." This is the same argument one hop further out: the strings the player reads erode fastest of all, because they are edited by whoever is closest to the deadline.

### 4.11 Localisation

Teaching content is long-form prose, which is a different translation problem from button labels, and it is treated as one.

**Two string classes, two mechanisms:**

| Class | Where | Mechanism |
|---|---|---|
| Chrome strings — buttons, labels, errors, units | `client/src/main/resources/i18n/messages_<locale>.properties` | `java.util.ResourceBundle`. ⚠ **unverified in this pass**: the JDK release from which `.properties` bundles default to UTF-8 — confirm before authoring non-ASCII, or read the files explicitly as UTF-8 and remove the question |
| Teaching content | `client/src/main/resources/terms/<locale>/<section>/<id>.md` | The §4.8.2 format, read as UTF-8 by our own parser, so the encoding question does not arise |

**English is canonical.** Every translated file carries `translationOf: <revision>` naming the English `revision` it was made from. When the English revision moves ahead, the translation is **stale**, and the client renders the English page **with a visible one-line notice** — never a silent fallback, and never a mixed page. This is the same honesty principle as `-es-authority-stale` in `01-visual-language.md` §2.2.8: a value whose freshness is unknown must say so rather than pass itself off as current.

**What is never translated**, and this is the important rule:

| Never translated | Why |
|---|---|
| Command names and aliases (`ps`, `top`, `port-sweep`) | ⚠ **Real Unix commands are not localised.** A French player who learns `ps` here must be able to type `ps` on a real machine. Translating the command name would destroy the one thing this whole document exists to deliver. Their *glosses* are translated |
| Flag names (`--dry-run`, `-v`) | Same reason |
| Exit status names (`EX_TEMPFAIL`) | They are identifiers from a real header file |
| Section numbers and `name(section)` refs | Real notation |
| Canonical item names (Port Sweep, Rainbow Table, Cold Storage Expansion) | Fixed by `01-visual-language.md` §9.2 and joined to the glossary by the §4.10 checks. A localised *display* name may accompany the canonical one; it never replaces it |
| Real-world citations (RFC numbers, ATT&CK ids, `nmap(1)`) | They address a specific document |

**Everything else is translated:** glosses, `DESCRIPTION`, `REAL-WORLD COUNTERPART`, `CAVEATS`, and the `FURTHER READING` annotations (though not the identifiers inside them).

**Translator support.** The `notes:` field (§4.8.2) exists for exactly this game's problem: it is full of homonyms (§2.15), and a translator with no context will render "sweep" the same way in `ping-sweep(1)` and `sweep(7)` and destroy a distinction the design depends on. Every §2.15 row generates a mandatory `notes:` line on both pages.

**Interpolation and plurals.** `java.text.MessageFormat` with `ChoiceFormat` covers English and most two-form languages. Languages with more plural categories need CLDR plural rules, which in practice means ICU4J — a real dependency for a client whose non-goals include weight. ⚠ Deferred as **T-8**.

**Sorting and search** use `java.text.Collator` for the active locale. ASCII sort order in a term index is a defect in most of Europe.

**Right-to-left** is owned by `01-visual-language.md` **V-10** and not duplicated here — except to note that this document adds a specific hazard to that list: man-page layout is column-based, and mono identifiers embedded in RTL prose are a bidirectional-text problem, not a mirroring problem.

---

## 5. Accessibility of the teaching layer

The floor in `00-client-overview.md` §3.5 applies here in full, and `07-accessibility.md` owns it globally. This section covers only what is specific to content that appears on hover or focus; where the two disagree, `07` wins and this section is the defect.

### 5.1 SC 1.4.13 Content on Hover or Focus — the criterion this feature lives or dies by

WCAG 2.1 / 2.2 Success Criterion 1.4.13, **Level AA** (verified against the W3C's Understanding document):

> *"Where receiving and then removing pointer hover or keyboard focus triggers additional content to become visible and then hidden, the following are true:"*
> **Dismissible:** *"A mechanism is available to dismiss the additional content without moving pointer hover or keyboard focus, unless the additional content communicates an input error or does not obscure or replace other content"*
> **Hoverable:** *"If pointer hover can trigger the additional content, then the pointer can be moved over the additional content without the additional content disappearing"*
> **Persistent:** *"The additional content remains visible until the hover or focus trigger is removed, the user dismisses it, or its information is no longer valid"*

| Requirement | How the Tier-2 popover meets it |
|---|---|
| **Dismissible** | `Escape` — installed as a key filter on the **owning window**, because `PopupWindow.hideOnEscape` only applies "while the popup has focus" (verified) and this popover deliberately never takes focus. Plus a visible close button (`closeButtonEnabled(true)`, `headerAlwaysVisible(true)`) |
| **Hoverable** | The popover stays while the pointer is over it, and there is a forgiving path between term and popover. `Popover` sets `autoHide` from `!detached` (read from the shipped source), so a detached page persists unconditionally |
| **Persistent** | It closes on trigger removal, on dismissal, or never. **There is no timeout of any kind** |

> ⚠ **The trap, stated as loudly as it deserves.** `javafx.scene.control.Tooltip` has a `showDuration` that **defaults to 5000 ms** (verified against the JavaFX 26 javadoc). A stock JavaFX tooltip therefore fails *Persistent* out of the box, and it fails it invisibly — nobody notices in testing, because nobody in testing is still reading at second six. Its javadoc also describes activation as *"when the mouse moves over a Control"*, with no keyboard trigger documented, so it fails SC 2.1.1 as a definition mechanism too.
>
> **Therefore: `Tooltip` is not used for the teaching layer.** Tier 1 is the gloss bar and Tier 2 is a `Popover`. Where `Tooltip` is used elsewhere — icon-only controls, per `01-visual-language.md` §6.4 — every instance sets `showDuration` to `Duration.INDEFINITE`. That should be a single factory method, and constructing a bare `Tooltip` anywhere in the client should be an ArchUnit failure, because this is precisely the kind of default that gets reintroduced by a well-meaning patch six months later.

**Tier 1 sits outside the criterion by construction.** The gloss bar is a permanent region: it does not become visible and then hidden, it does not obscure or replace other content, and it does not overlay anything. That is not a loophole, it is the reason the design puts Tier 1 there.

### 5.2 Keyboard — nothing is hover-only

| Path | Keys |
|---|---|
| Reach a term | Standard focus traversal. **Marked terms inside otherwise non-interactive text are focusable** — a term in a log line is a focus stop |
| Tier 1 | Focus alone fills the gloss bar. No extra keystroke, at every teaching level |
| Tier 2 | `Shortcut+Shift+/` or `F1` on the focused term; `man <term>` in the palette |
| Read Tier 2 | Focus moves into the popover on open **only when opened by keyboard** — a keyboard user asked for it and must be able to read it. A pointer-opened popover never takes focus |
| Leave Tier 2 | `Escape` or `q` returns focus to the term that opened it. Never to the top of the window |
| Browse | The `man` window is fully keyboard-operable, including search and filters |

Focus indication follows `01-visual-language.md` (`-es-focus-ring`, 2 px, outside the control's bounds). A term marker's focus ring must not change the term's metrics — same rule as the marker itself.

⚠ **Focus stops in long text are a real cost.** A log pane with forty marked terms is forty extra `Tab` stops, which is hostile to a keyboard user trying to get past it. Mitigation: within a scrolling text pane, terms form a **nested focus group** — `Tab` moves past the pane, and `F6` or arrow keys move between terms inside it. This is the standard composite-widget pattern and it needs testing with a real screen-reader user before it is trusted. Flagged as **T-6**.

### 5.3 Screen readers

- Term markers set `accessibleText` to `"<term>, term, <status>"` and `accessibleHelp` to the gloss, both verified as properties of `javafx.scene.Node` in `01-visual-language.md` §6.4.
- The Tier-2 popover's root sets `accessibleRole` to `AccessibleRole.TOOLTIP` — verified to exist in JavaFX 26's `AccessibleRole` enum (single word, no underscore).
- The status is **spoken as a word**, never conveyed by the chip's fill alone. The fill ladder in §2.1 serves sighted users; `accessibleText` serves everyone else; neither is the sole carrier (`01-visual-language.md` §2.4).
- The gloss bar is an ARIA-live-region-equivalent problem with no clean JavaFX answer. Proposal: it is **not** announced automatically (an auto-announcing bar would talk over everything during a busy window), and it is reachable as an ordinary focusable region with a stable accessible name. ⚠ Needs testing with a real screen reader; flagged as **T-7**.

> ⚠ **The Linux problem, stated rather than hidden.** JavaFX's accessibility bridges target Windows (UI Automation) and macOS (NSAccessibility). A Linux AT-SPI bridge does not appear in the toolkit, and Orca's own list of supported toolkits names Java **Swing**, not JavaFX. **⚠ unverified** — this could not be confirmed against a primary OpenJFX source in this pass, and it must be, because if it is true then on Linux the entire accessible-name layer is inert and the teaching content is reachable only as ordinary on-screen text.
>
> The design consequence, which is worth adopting either way: **the gloss bar and the `man` window render real, selectable, copyable text in the normal reading order.** A player using screen magnification, OS-level text selection, or a clipboard-reading tool gets the content even where no accessibility API is listening. `01-visual-language.md` §8.7 already requires selectable log lines for gameplay reasons; the same property is the Linux accessibility fallback. Flagged as **T-5**.

### 5.4 Not time-limited, not motion-dependent

- **No timeouts anywhere in the teaching layer.** Nothing auto-dismisses, nothing counts down, no page expires. SC 2.2.1 *Timing Adjustable* is satisfied by having no timing at all, which is the cheapest way to satisfy it.
- **No motion is required to read anything.** Under reduced motion (`01-visual-language.md` §7.4) the popover appears at its end state with no fade. Nothing about the teaching layer is conveyed by movement.
- **The `new term` chip is a static mark**, never a pulse. `01-visual-language.md` §7.3's ban on animation during a live breach makes an animated chip unimplementable there anyway, and a mark that behaves differently in different conditions is worse than one that never moves.
- **Text scales.** Body prose reflows within the popover's measure; the popover grows to the scene's bounds and then scrolls. Nothing is clipped and nothing requires horizontal scrolling (SC 1.4.10 *Reflow*, SC 1.4.4 *Resize Text*).

### 5.5 Placement — SC 2.4.11 Focus Not Obscured

WCAG 2.2 added SC 2.4.11 *Focus Not Obscured (Minimum)*, Level AA: the focused element must not be entirely hidden by author-created content. A popover anchored *over* the term that opened it would do exactly that.

Rule: the popover is placed so it never covers its own anchor. `Popover.ArrowLocation` has twelve values (read from the shipped source); placement picks the first that keeps both the anchor and the popover fully inside the scene, preferring below-then-above, then right-then-left. If no placement fits, the page opens in the `man` window instead of being crammed in.

### 5.6 Reading level and comprehension

- SC 3.1.5 *Reading Level* (Level AAA, verified) is addressed structurally: the Tier-1 gloss is a plain-language supplement for every Tier-2 page (§4.8.3 rule 5).
- SC 3.1.3 *Unusual Words* (Level AAA, verified) — *"A mechanism is available for identifying specific definitions of words or phrases used in an unusual or restricted way, including idioms and jargon"* — is what this entire document is. Its suggested techniques are a glossary, inline definitions, and linked definitions; we have all three (the `man` window, the gloss bar, the `SEE ALSO` graph). **This is unusual: a game meeting a Level AAA criterion as a side effect of its product goal.** Worth stating, because it is the argument for why the teaching layer should never be treated as a cuttable feature.
- Level `off` never removes the mechanism, only the markers (`00-client-overview.md` §5.2). A player at `off` still has `man`, the index and search. Conformance does not depend on a setting.

---

## 6. Open questions

Deliberately undecided here. Prefix **`T-`** (terminology/teaching), chosen to avoid the existing `OQ`/`P`/`D`/`S`/`N`/`E`/`A`/`G`/`W`/`Q` prefixes in `../design/15-open-questions.md`, and `CL-` (`00`), `V-` (`01`), `PN-` (`02`). Log in `../design/15-open-questions.md` §2 if this doc set is adopted.

**T-5, T-6 and T-7 are accessibility questions raised by this feature specifically.** They are stated here because this document introduces the surfaces that raise them; `07-accessibility.md` owns the general answers, and the integrator should fold these three into that document's list rather than tracking them twice.

- **T-1: The `man` window is a fourteenth window id.** `00-client-overview.md` §6.1 fixes thirteen; §5.2 of the same document requires "the term index is a searchable window" without giving it one. Either `05-tool-windows-and-layout.md` absorbs `man` into the catalogue, or the index folds into an existing window (`recon` is the least-bad host, since it is already the reading surface). Decide there, before window persistence keys are written — a window id is a persisted key and changing it later orphans player layouts.
- **T-2: Two `es-term` variants are introduced** — `es-term-bar` (§4.2) and `es-term-page` (§4.3.2). Both are legal under `01-visual-language.md` §1.2's `es-<primitive>-<variant>` grammar and neither is a new primitive or a new state class, so the §8.11 matrix is untouched. Confirm the integrator agrees, or have `01` §8.10 enumerate them explicitly.
- **T-3: Does `explain` survive contact with players who already know Unix?** This is **CL-4** in `00-client-overview.md` seen from the content side, and this document adds a specific lever it did not have: the first-run question could set the level *per domain* — quiet on `ps` and `grep`, loud on `unlock-gates(7)` and `provenance-chain(7)` — since knowing Unix says nothing about knowing DIDs or BFT quorums. More machinery; possibly much better fit. Decide with **CL-4**, not separately.
- **T-4: readline bindings versus platform edit keys.** §3.9 proposes `Ctrl+A`/`Ctrl+E`/`Ctrl+R` only while a single-line command input holds focus. That is a per-platform call — on macOS these are *system-wide* emacs bindings in every text field and taking them is uncontroversial; on Windows and Linux `Ctrl+A` is Select All and taking it is not. Needs a decision per platform before the palette is built, and it belongs with `02`'s per-platform work.
- **T-5: JavaFX accessibility on Linux.** §5.3 could not confirm whether an AT-SPI bridge exists. If it does not, the Linux accessibility story rests entirely on selectable text and OS magnification, and the client should say so honestly in its accessibility notes rather than implying screen-reader support it does not have. **Verify against a primary OpenJFX source before any accessibility claim is published.**
- **T-6: focus stops for terms inside long text panes.** §5.2 proposes a nested focus group with `F6`/arrow entry. Untested. The failure mode — forty extra `Tab` stops in a log pane — is bad enough that the fallback (terms in scrolling panes are *not* focus stops, and are reachable only through the `man` window's search) needs costing too.
- **T-7: how should the gloss bar behave for a screen reader?** §5.3 proposes not announcing automatically. The alternative — announce on focus change only, never on hover — may be better, and JavaFX has no live-region concept to lean on. Needs a real screen-reader user, not a simulation.
- **T-8: pluralisation beyond two forms.** §4.11 leaves CLDR plural rules unsolved. ICU4J is the correct answer and a real dependency in a client whose non-goals include weight. Decide before any locale with more than two plural categories is committed to, not after.
- **T-9: does the command surface need variables at all?** §3.1 rule 4 bans host environment expansion. A *game* namespace of variables (`$rig`, `$handle`, `$target`) would be a genuinely useful teaching device — variables and expansion are core shell literacy — and it is a new parser surface with its own injection questions. Currently out; revisit once the pipeline in §3.7 has been played with.
- **T-10: should `man` pages be readable outside the game?** The content is plain text in a documented format, so a static site or a plain `man` tree is nearly free, and "the game's glossary is a genuinely good introduction to these terms" is the kind of thing that travels. It is also a public claim of accuracy that would need maintaining. Not v1; worth an explicit decision rather than drifting into it.
- **T-11: how are `real, simplified` pages kept honest as the game is balanced?** A tuning pass that changes what a tool does can silently falsify its `CAVEATS`. The §4.10 checks catch *naming* drift, not *semantic* drift. Candidate: the item table docs (`../design/06`–`11`) and the term files cross-reference by id, and a change to a tool's row flags its page for review. Needs someone to own it, or it will not happen.
- **T-12: a verification pass over §2 by someone who does this for a living.** Every claim here was checked against a live source, and that is not the same as being reviewed by a practitioner. The rows most likely to be subtly wrong are the ones this document is proudest of — cross-view rootkit detection, the flow-analysis mapping for Traffic Analyzer, and the honeypot-detector asymmetry. **A wrong row teaches something false, which is the one failure this whole document exists to prevent.** Budget the review.
