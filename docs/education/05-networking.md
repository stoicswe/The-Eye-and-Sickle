# 04 — Networking — how machines actually talk

**Status:** ⚠️ **[PROPOSAL]** — the *goal* is Established (client pillar **C6**, `../client/00-client-overview.md` §2 and §5; the falsifiable claim in `../client/04-terminology-and-education.md` §1.1). The vocabulary rows this document is bound to are also Established and were verified in that document's own pass (`../client/04` §2.2, §2.5–§2.8). What *this* document adds — the concept inventory, the stage and prerequisite assignments, the eighteen written entries, and the scope boundary — is first-pass curriculum design against the contract in `00-curriculum-and-method.md`.
**Depends on:** `00-curriculum-and-method.md` — **the contract**: §3 (the entry template), §4 (the status vocabulary), §5 (man-section assignment), §6 (stages and the eight sequencing rules), §7 (coverage), §8 (the writer's loop). **None of it is re-specified here.** Also `../client/04-terminology-and-education.md` §1 (the principle), §2.2 / §2.5 / §2.7 / §2.8 (the mapping rows), §2.15 (the homonym table), §4.8 (the shipped file format), §4.9 (`port-sweep(1)` — an already-written command page this document must not duplicate); `../design/glossary.md`; `../design/07-recon-tools.md`, `../design/08-stealth-and-noise.md` §2, `../design/09-defense-and-hardening.md` §1, `../design/04-mining.md` §3.1; `../client/00-client-overview.md` §3.3 (uOS is the operating system), §6.1 (the window catalogue), pillar **C6**; `../../CLAUDE.md`
**Depended on by:** `client/src/main/resources/terms/<locale>/7/*` and `terms/<locale>/1/traceroute.md`; `06-cryptography-and-trust.md` and `07-distributed-systems-and-identity.md`, which cite this domain's entries in `seeAlso` rather than redefining them (`00-curriculum-and-method.md` §1.4)

---

## 1. What this domain is, and why this game's player needs it

### 1.1 The domain in one paragraph

**How data gets from one machine to another, and what that movement reveals to anyone watching.** Addresses and what they actually identify; packets and why data is cut into them; the layer model, honestly; ports and transport; routing, hops and the physics of delay; names and the machinery that resolves them; address translation at the edge; what sits in the path — filters, proxies, relays; what encryption on the wire does and, more importantly, what it does not do; and what an observer with a capture tool or a flow record can reconstruct without ever reading a byte of content.

### 1.2 Why this domain transfers better than any other

Three reasons, in increasing order of force.

**The tools are the real tools.** Port Sweep *is* `nmap(1)`. Passive Sniffer *is* `tcpdump(8)`. Topology Mapper *is* iterated `traceroute(8)` plus an accumulated view. Traffic Analyzer *is* flow analysis. Relay Chain *is* onion routing, three hops and one encryption layer per relay. These are not resemblances chosen for flavour; `../client/04` §2.5 and §2.7 already record them as verified mappings, and four of the six recon tools are marked `real` rather than `real, simplified`. A player who learns what Port Sweep tells them has learned what a port scan tells them.

**The transfer tests survive Windows.** `00-curriculum-and-method.md` **ED-8** is the doc set's highest-impact open question: most transfer tests assume a Unix shell, and most players are on Windows. Networking is the one domain where ED-8's honest option (c) — *target what is universal* — is not a compromise. `ping`, `tracert`/`traceroute`, `nslookup`, `ipconfig`/`ip addr`, a browser's Network tab and the padlock's certificate view exist on all three platforms and teach the real thing on each. Eleven of this document's eighteen written entries have a transfer test that runs unmodified on Windows.

**The game's central lie is a real truth.** The `noise` scalar is fiction (`../client/04` §2.14). The fact underneath it is not: **scanning is loud, capture is silent, and every probe you send arrives somewhere it can be logged.** `../design/07-recon-tools.md` §2 prices Ping Sweep at high noise with the target notified, and calls that deliberate balance. It is also simply true — an echo request lands in the target's logs. Teaching that correctly costs nothing and is the strongest single claim this domain makes.

### 1.3 The game surfaces this domain lands on

Derived per `00-curriculum-and-method.md` §7.1 item 1 — walked from `../client/04` §2.2's window table, §2.5/§2.7/§2.8's tool tables, and §3.10's command catalogue.

| Surface | Source | Concepts it forces |
|---|---|---|
| `map` window — node graph, hop distance, recon overlays | `../client/00-client-overview.md` §6.1 | `network`, `hop`, `routing`, `traceroute`, `ip-address`, `latency` |
| `/net/<node-address>/` in the virtual namespace | `../client/04` §3.2 | `ip-address`, `subnet`, `loopback` |
| `audit` window — the connection table; a socket with no owning process | `../design/04-mining.md` §3.1 | `port`, `tcp`, `flow-metadata`, and `socket(7)` (owned by **02**) |
| **Port Sweep** | `../design/06-intrusion-tools.md` | `port`, `port-scan`, `tcp`, `three-way-handshake` |
| **Passive Sniffer** — silent, adjacency only | `../design/07-recon-tools.md` §1 | `packet-capture`, `switch`, `mac-address`, `arp`, `layering` |
| **Topology Mapper** — one hop to two hops | `../design/07-recon-tools.md` §2 | `hop`, `routing`, `traceroute`, `ttl` |
| **Traffic Analyzer** — live vs. dormant nodes | `../design/07-recon-tools.md` §1 | `flow-metadata`, `tls`, `packet-capture` |
| **Ping Sweep** — the target is notified | `../design/07-recon-tools.md` §2 | `icmp`, `port-scan`, `packet` |
| **Relay Chain** — framework + per-session hops, latency tax | `../design/08-stealth-and-noise.md` §2 | `onion-routing`, `proxy`, `latency`, `hop` |
| **Traffic Shaper** — never spike above threshold, hard speed ceiling | `../design/08-stealth-and-noise.md` §1 | `traffic-shaping`, `bandwidth`, `jitter` |
| **Firewall T1–T3** | `../design/09-defense-and-hardening.md` §1 | `packet-filter`, `port`, `tcp` |
| **Deployed miner control channel** — must stay up, most detectable part | `../design/04-mining.md`, `../client/04` §2.10 | `flow-metadata` (beaconing), `packet-loss`, `latency` |
| **Dead Drop / the public ledger** | `../design/08-stealth-and-noise.md` §1 | `proxy`, `flow-metadata` (adjacency only; the ledger itself is **06**) |
| `recon` window — reading recovered machine-authored text | `../client/00-client-overview.md` §6.1 | `http`, `dns`, `packet` |

### 1.4 The three things this domain must get right

Everything else is detail. If a player leaves with these three and nothing else, the domain has paid for itself.

1. **An address identifies an interface at a moment, not a machine and not a person.** It is shared by everyone behind a router, rewritten at the edge, reassigned on a lease, and — under carrier-grade NAT — shared by hundreds of unrelated subscribers. This is the correction with the widest reach outside the game, and it is the one an intelligent adult is most likely to have exactly backwards.
2. **Layering is a convention, not a law of nature.** The internet was not built to the OSI seven-layer model, the OSI numbers survive mainly as industry shorthand, and real protocols straddle the boundaries. Teaching OSI as *the* description of reality is the single most common defect in networking material aimed at this audience, and `../client/04` §1.1 rules that mis-teaching is worse than not teaching.
3. **Encryption hides content. It hides nothing else.** Who, to whom, when, how much, how often, for how long — all of it survives TLS, all of it is cheap to store, and all of it is enough to identify applications, detect beaconing and reconstruct behaviour. This is what Traffic Analyzer models, it is what `../client/04` §2.5 calls "genuinely important and genuinely under-taught," and it is the load-bearing fact of the game's entire surveillance premise.

### 1.5 The ownership boundary

Per `00-curriculum-and-method.md` §1.4, the ladder is: *the lowest-numbered domain that can fully define a concept without forward-referencing a higher-numbered one owns it.* This domain therefore owns **ports** (definable without the shell, without cryptography, without distributed systems) but **not sockets** (which need the operating system's file-descriptor idea — §1.4 adjudicates this explicitly), and **not** hashing, signatures or public keys.

One rule this document adopts and states once, because it decides a dozen edge cases and because a reviewer must be able to check it:

> **Naming a later concept and pointing at its page is permitted; depending on it is not.** R4 forbids an entry that *leans* on a concept at a later stage — one the reader must already understand for this page to make sense. It does not forbid writing "see `digital-signature(7)`" or "your machine asks a recursive resolver — see `resolver(7)`". The test is R5's: **remove every cross-reference and read the page cold.** If it still stands alone, the reference was a signpost. If it collapses, it was a prerequisite and belongs in the `prerequisites` field, where R1 will catch it.

This reading is what makes `tls(7)` writable at all inside a domain that is forbidden (R8) from requiring `public-key-cryptography(7)`. It is flagged as **NW-3** in case the contract's authors read R4 more strictly.

---

## 2. The concept inventory

### 2.1 How to read this

Every concept this domain judges worth teaching, whether or not a full entry exists for it yet. **This table is the coverage guarantee** (`00-curriculum-and-method.md` §7.1): a reviewer must be able to see the whole domain at once and say what is missing. Rows marked **▣** have a fully-written entry in §3.

`prereq` uses the same `name(section)` form as the template's `prerequisites` field. Every reference resolves either to a row in this table or to the "owned elsewhere" table in §2.3.

### 2.2 The inventory — 39 concepts

**A. The model**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| | `network(7)` | Machines that have agreed on how to address and reach each other. | real | first-session | none | `map` window, first sight of the node graph |
| | `protocol(7)` | An agreed set of rules two machines follow to exchange data. | real | operating | `network(7)` | Every tool's result format; `recon` window text |
| ▣ | `packet(7)` | One small, separately addressed piece of a larger transmission. | real | operating | `network(7)`, `protocol(7)` | Ping Sweep; Passive Sniffer output |
| ▣ | `layering(7)` | Splitting a stack into parts that each know only their own job. | real, simplified | operating | `packet(7)`, `protocol(7)` | Passive Sniffer output; Overflow Kit's "layer" homonym |
| | `mtu(7)` | The largest single chunk a link will carry without splitting it. | real | investigating | `packet(7)` | Traffic Shaper's speed ceiling; capture output |

**B. The local link**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| | `mac-address(7)` | A 48-bit hardware identifier used to reach a device on one link. | real | operating | `packet(7)` | Identity Spoofer (`../client/04` §2.7 maps it to MAC spoofing) |
| | `switch(7)` | A device that forwards each frame only out the port that needs it. | real | investigating | `mac-address(7)` | Passive Sniffer's adjacency-only limit |
| | `arp(7)` | Finding the hardware identifier that goes with a local IP address. | real | investigating | `mac-address(7)`, `ip-address(7)` | Passive Sniffer revealing adjacent node types without touching them |

**C. Addressing**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| ▣ | `ip-address(7)` | The 32- or 128-bit number that identifies one network interface. | real | operating | `packet(7)` | `/net/<node-address>/`; every target the player names |
| ▣ | `subnet(7)` | A block of addresses sharing a fixed number of leading bits. | real | investigating | `ip-address(7)`, `routing(7)` | The `map` window's grouping of reachable nodes |
| | `loopback(7)` | The address a machine uses to talk to itself, never leaving it. | real | operating | `ip-address(7)` | `/rig/` vs `/net/` — your own rig is not on the network |
| | `private-address(7)` | A range usable inside any network but never routed on the internet. | real | investigating | `subnet(7)` | Nodes reachable only from inside a compromised segment |
| ▣ | `nat(7)` | Rewriting addresses at the edge so many hosts share one of them. | real, simplified | investigating | `private-address(7)`, `port(7)` | Attribution: whose address the defender actually logged |
| | `dhcp(7)` | How a machine is handed an address when it joins a network. | real | investigating | `ip-address(7)`, `subnet(7)` | Node addresses that change between sessions |

**D. Getting there**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| ▣ | `routing(7)` | Each hop deciding, alone, where to send a packet next. | real, simplified | operating | `ip-address(7)`, `packet(7)` | The `map` window's edges; Traversal-class planning |
| | `hop(7)` | One router-to-router step on the way to a destination. | real, simplified | operating | `routing(7)` | Topology Mapper: one hop → two hops; Relay Chain per-hop cost |
| | `icmp(7)` | The protocol networks use to report errors and test reachability. | real | operating | `packet(7)`, `ip-address(7)` | Ping Sweep — and why the target is notified |
| ▣ | `ttl(7)` | A counter decremented at every router, to kill looping traffic. | real | operating | `hop(7)`, `icmp(7)` | Hop distance in the `map` window |
| ▣ | `traceroute(1)` | Reveals each router on the way to a host, one step at a time. | real, simplified | investigating | `ttl(7)`, `icmp(7)`, `hop(7)` | `map`, `traceroute <node>` (`../client/04` §3.10) |

**E. Transport**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| ▣ | `port(7)` | A number from 0 to 65535 saying which program traffic is for. | real | **first-session** | `network(7)`, `process(7)` | Port Sweep result — the first tool in the starting kit |
| ▣ | `tcp(7)` | An ordered, checked, two-way byte stream between two programs. | real | operating | `packet(7)`, `port(7)` | The `audit` window's connection table and its states |
| | `three-way-handshake(7)` | The three messages that open a connection before any data moves. | real | investigating | `tcp(7)` | Why a half-open scan is quieter than a full one |
| ▣ | `udp(7)` | Sending a message with no connection, ordering or delivery check. | real | operating | `packet(7)`, `port(7)` | Services a Port Sweep finds that never "connect" |

**F. Names**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| ▣ | `dns(7)` | The system that turns names people type into numeric addresses. | real, simplified | operating | `ip-address(7)`, `udp(7)` | Recovered text in `recon`; node names vs. node addresses |
| ▣ | `resolver(7)` | The server that does your name lookups for you, and caches them. | real | investigating | `dns(7)` | The Eye's visibility; what a watcher learns without touching you |

**G. What sits in the path**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| | `packet-filter(7)` | A rule set that allows or drops traffic by its header fields. | real, simplified | investigating | `packet(7)`, `port(7)`, `tcp(7)` | Firewall T1–T3 (`../design/09-defense-and-hardening.md` §1) |
| | `proxy(7)` | A machine that makes a connection on your behalf and relays it. | real | investigating | `tcp(7)`, `ip-address(7)` | Relay Chain's first hop; Dead Drop's indirection |
| | `traffic-shaping(7)` | Deliberately delaying packets to hold a flow under a rate limit. | real | investigating | `bandwidth(7)`, `rtt(7)` | **Traffic Shaper** — a §2.15 homonym; see `notes` |
| ▣ | `onion-routing(7)` | Relaying through several hops, each peeling one encryption layer. | real | adversarial | `proxy(7)`, `tls(7)`, `rtt(7)` | **Relay Chain** (`../design/08-stealth-and-noise.md` §2) |

**H. What is protected, and what is not**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| ▣ | `tls(7)` | Encrypts a connection and proves the server holds the name asked for. | real, simplified | investigating | `tcp(7)`, `dns(7)` | Traffic Analyzer working anyway; recovered encrypted sessions |
| | `certificate(7)` | A signed statement binding a name to a public key, plus an expiry. | real, simplified | investigating | `tls(7)` | Honeypot Detector: services that answer but cannot prove who they are |
| | `http(7)` | The request-and-response rules the web is built on. | real, simplified | investigating | `tcp(7)`, `tls(7)` | `recon` window — recovered headers and machine-authored text |

**I. What an observer sees**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| ▣ | `packet-capture(7)` | Recording every frame an interface sees, and sending nothing. | real | investigating | `packet(7)`, `switch(7)`, `layering(7)` | **Passive Sniffer** — zero noise, and that is correct |
| ▣ | `flow-metadata(7)` | Who talked to whom, when and how much, without any content. | real | investigating | `packet(7)`, `port(7)`, `tls(7)` | **Traffic Analyzer**; the control channel's beacon |
| | `port-scan(7)` | Asking a host, one number at a time, what is listening. | real | operating | `port(7)`, `tcp(7)` | **Port Sweep** — the command page is `port-sweep(1)` |

**J. How a network performs**

| ▣ | id | Gloss | Status | Stage | Prereq | Game surface |
|---|---|---|---|---|---|---|
| ▣ | `rtt(7)` | How long a message takes to get there and the answer back. | real | operating | `packet(7)`, `hop(7)` | Relay Chain's per-action latency tax |
| | `bandwidth(7)` | How much data a link can carry per second, not how fast it is. | real | operating | `packet(7)` | **Bandwidth** rig upgrade — a §2.15 homonym; see `notes` |
| | `jitter(7)` | Variation in delay between packets that should arrive evenly. | real | investigating | `rtt(7)` | Traffic Shaper smoothing a curve; unstable hop timings |
| | `packet-loss(7)` | Data that never arrives, and what a protocol does about it. | real | investigating | `packet(7)`, `tcp(7)` | A control channel that drops; a sabotaged miner link |

### 2.3 Concepts met here, owned elsewhere

Listed so nobody writes a second entry for one of them. **One concept, one entry, one owner** (`00-curriculum-and-method.md` §1.4).

| Concept | Owner | Why not here | How this domain uses it |
|---|---|---|---|
| `socket(7)` | **02** operating systems | Needs the file-descriptor idea; §1.4 adjudicates it explicitly | Cited by `port(7)`, `tcp(7)`, `flow-metadata(7)` |
| `process(7)` | **02** | A process is not a networking concept | A **prerequisite** of `port(7)` — a port picks which process receives |
| `hash(7)`, `public-key-cryptography(7)`, `digital-signature(7)` | **06** cryptography & trust | R8 forbids this domain requiring them | `seeAlso` only, from `tls(7)` and `certificate(7)` |
| `noise(7)`, `heat(7)` | **05** | Detection, logging and anti-forensics are 05's | `port-scan(7)` and `packet-capture(7)` cite it; the *real* fact under it is taught here |
| `firewall(8)` | **05** | The section-8 command page for a defense item | The concept page is `packet-filter(7)`, here |
| Recon and stealth **command** pages — `port-sweep(1)`, `passive-sniffer(1)`, `ping-sweep(1)`, `topology-mapper(1)`, `traffic-analyzer(1)`, `relay-chain(1)` | ⚠ undecided — **NW-2** | They are item pages for game tools whose entire `REAL-WORLD COUNTERPART` is networking | This domain supplies the counterpart content; `port-sweep(1)` is already drafted at `../client/04` §4.9 and must not be rewritten |
| **Bandwidth** the rig upgrade | ⚠ see **NW-5** | A rig stat, met on `rig-monitor` | **Recommendation: one page, `bandwidth(7)`, carries both meanings**, exactly as `../client/04` §2.15 prescribes for Canary Token |
| Provenance, quorum, federation, the public ledger | **06** | Distributed systems | Not referenced; `../architecture/07-transport-security.md` is out of scope — see §4 |

### 2.4 The honesty ledger

**Status distribution** (`00-curriculum-and-method.md` §7.1 item 5):

| Status | Count | Share |
|---|---|---|
| `real` | 29 | 74% |
| `real, simplified` | 10 | 26% |
| `game` | **0** | 0% |

**Zero `game` entries, and that is the correct number rather than an omission.** §4.4 warns that a domain document made largely of `game` entries is lore, not a curriculum; the inverse deserves the same scrutiny, so: this domain's fiction is genuinely owned by other domains. `noise` and `heat` are detection constructs (**05**). The Bandwidth rig stat is a rig construct (**01**). What is left in networking is the real internet, because the game's recon tools were built as thin wrappers over real ones and there was nothing to invent. That is a claim about the design, not about this document's honesty, and it is worth stating so a reviewer can falsify it.

**Claims marked ⚠ unverified or approximate**, per §7.4. Each is written into its entry with the hedge intact, or kept out of the shipped page entirely:

| Claim | Status |
|---|---|
| Global BGP table size "on the order of a million IPv4 prefixes" | ⚠ approximate and moving; written as an order of magnitude, never a figure |
| Root-server instance count (~1,950 as of Dec 2025) | ⚠ moves constantly; written as "well over a thousand", sourced to root-servers.org |
| Low-Earth-orbit round-trip figures (~25–60 ms) | ⚠ varies by operator, load and location; given as a range with that caveat |
| `dig` availability on a stock macOS | ⚠ not confirmed in this pass; every transfer test that wants it names a `ping`-based fallback |
| "Diminishing returns past three Tor hops" (asserted at `../client/04` §2.7) | ⚠ restated in `onion-routing(7)` as the *structural* argument — three is the minimum that gives the property — rather than as a quantitative claim |

### 2.5 The graph, checked

The five checks in `00-curriculum-and-method.md` §6.4, run by hand over §2.2:

1. **Every `prerequisites` reference resolves.** All resolve within §2.2 except `process(7)`, which resolves to domain 02 (§2.3). ✓
2. **Acyclic.** The table is ordered so that every prerequisite appears in an earlier group or earlier row within its group. No back-edges exist. ✓
3. **Every stage ≥ the maximum stage of its prerequisites.** Checked row by row. The two places this bit: `dns(7)` at `operating` forced `udp(7)` down from `investigating` to `operating` — correct anyway, since teaching TCP without UDP leaves a false binary; and `onion-routing(7)` sits at `adversarial` because `tls(7)` is `investigating`. ✓
4. **Every entry is reachable from a `first-session` root.** All 39 root through `network(7)`. ✓
5. **No prerequisite edge points to a higher-numbered domain.** The only cross-domain edge is `port(7)` → `process(7)` (04 → 02), which is downward. ✓

**Two first-session slots claimed, out of the ≤12 the whole doc set may spend (R2).** `port(7)` is named for this domain in the method doc's own §6.2 table. `network(7)` is this document's addition and exists to give the graph a root; it is one paragraph long and it is **the entry this domain volunteers first if the budget is contested** — at the cost of check 4 needing a different root, most likely a re-parenting of `packet(7)` onto `process(7)`.

---

## 3. The written entries

### 3.1 Which eighteen, and why

Written fully, under the template in `00-curriculum-and-method.md` §3.3:

`packet(7)` · `layering(7)` · `port(7)` · `ip-address(7)` · `subnet(7)` · `routing(7)` · `ttl(7)` · `traceroute(1)` · `tcp(7)` · `udp(7)` · `dns(7)` · `resolver(7)` · `nat(7)` · `tls(7)` · `flow-metadata(7)` · `packet-capture(7)` · `onion-routing(7)` · `rtt(7)`

Selected against the three criteria the brief sets, and each one qualifies on at least two:

| Reason | Entries |
|---|---|
| **The game leans on it** — a tool or window is unreadable without it | `port`, `packet`, `routing`, `ttl`, `traceroute`, `tcp`, `flow-metadata`, `packet-capture`, `onion-routing` |
| **It kills a misconception worth killing** — an adult reliably arrives with the wrong model | `ip-address` (an address is a machine), `layering` (OSI describes the internet), `ttl` (it is seconds), `tcp` (reliable means guaranteed), `udp` (unreliable means worse), `resolver` (DNS changes propagate), `nat` (your public address is your machine's), `tls` (the padlock means private), `flow-metadata` (encryption hides what you are doing), `latency` (more megabits is faster) |
| **It unlocks several others** — three or more entries name it as a prerequisite | `packet` (7 dependants), `ip-address` (5), `port` (4), `tcp` (5), `routing` (3), `latency` (3) |

Two deliberate omissions from the written set, both of which stay in the inventory:

- **`port-scan(7)`** — because `../client/04` §4.9 already contains a complete `port-sweep(1)` page whose `REAL-WORLD COUNTERPART` covers scanning, open/closed/filtered, and `-sS`/`-sT`/`-sU`/`-sV`. Writing the concept page now would duplicate shipped prose before anyone has read the two side by side. It is drafted last, against that page.
- **`certificate(7)`** — because it is the entry most likely to move to domain **05** when **NW-3** is resolved. Writing it here and moving it later would invalidate its prerequisite edges under R8.

### 3.2 `packet(7)`

```
id:             packet
section:        7
name:           packet
canonical:      packet
gloss:          One small, separately addressed piece of a larger transmission.
status:         real
aliases:        datagram, frame
seeAlso:        network(7), protocol(7), layering(7), ip-address(7), mtu(7),
                tcp(7), udp(7), packet-capture(7), packet-loss(7)
reading:        RFC 791 §3.1 (the IPv4 header); RFC 9293 §3.1 (the TCP
                header); RFC 8200 §3 (the IPv6 header)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  network(7), protocol(7)
hook:           The first Ping Sweep result, where the outcome is reported per
                probe rather than as one answer, and the Passive Sniffer's
                output, which is a list of separate things seen rather than a
                transcript (../design/07-recon-tools.md §1).
misconception:  commonly believed data travels between two machines as a
                continuous stream down a path reserved for it, the way a phone
                call was once switched; actually it is cut into small,
                independently addressed pieces that may take different routes,
                arrive out of order or never arrive at all, and the orderly
                stream the reader sees is reconstructed at the far end by
                software.
transfer:       Run `ping example.com` on macOS, Linux or Windows. Every line
                is one packet, with its own round-trip time and its own
                outcome; the summary line at the end counts how many of them
                came back. The player can now say why one line can be slow or
                missing while the others are fine.
verified:       IPv4 header 20 bytes minimum, IPv6 header 40 bytes fixed —
                RFC 791 §3.1, RFC 8200 §3; TCP header 20 bytes minimum —
                RFC 9293 §3.1; UDP header 8 bytes — RFC 768; Ethernet payload
                1500 octets — IEEE 802.3. Checked 2026-07-25.

## DESCRIPTION

Nothing you send crosses the network in one piece. A Ping Sweep does not ask a
question; it sends a series of small, separate probes and reports what came
back, which is why its result is a list and why parts of it can fail while the
rest succeeds.

A packet is one such piece: a block of data with a header stuck on the front
saying where it is going, where it came from, and what is inside. Anything
larger is cut into several. Each one is handled on its own by every machine
that touches it, and nothing keeps them together on the way.

That has consequences the game leans on. Pieces can arrive out of order and be
reassembled. One can vanish while its neighbours arrive. And each one carries
its own addresses, which is why a listener on the wire learns something from
every single one, whether or not the whole transmission ever completes.

## REAL-WORLD COUNTERPART

real — packets, exactly as every network the reader has ever used moves data.

The sizes are the lesson. An ordinary Ethernet link carries at most 1500 bytes
of payload per frame. An IPv4 header takes 20 of those bytes and a TCP header
another 20, leaving about 1460 bytes of actual content. A 3 MB photograph is
therefore not one transmission; it is roughly two thousand packets, each
addressed separately.

Two reasons it works this way, both still current. **Sharing:** if one machine
sent a whole file as a single unit, it would hold the link for the duration and
everything else would wait. Small pieces interleave, so a hundred conversations
share one wire without any of them being scheduled. **Recovery:** when a piece
is damaged or lost, the cost is that piece. Losing 1500 bytes out of 3 MB and
resending it is cheap; losing the 3 MB is not.

This is the design decision that separates the internet from the telephone
network that preceded it. A phone call reserved a circuit end to end for its
duration. A packet network reserves nothing, promises nothing, and makes that
up in software at the ends.
```

### 3.3 `layering(7)`

```
id:             layering
section:        7
name:           layering
canonical:      layering
gloss:          Splitting a stack into parts that each know only their own job.
status:         real, simplified
aliases:        protocol stack, OSI model, TCP/IP model, layer
glossary:       ../design/glossary.md
seeAlso:        packet(7), protocol(7), ip-address(7), tcp(7), tls(7),
                packet-capture(7), overflow-kit(1)
reading:        RFC 1122 §1.1.3 (the four-layer internet model);
                ISO/IEC 7498-1 (the OSI reference model)
notes:          "Layer" is a homonym in this game. ../design/05-hacking-
                minigame.md uses "layer" for one puzzle class-instance inside a
                multi-layer target, and Overflow Kit "bypasses one layer". That
                is not a protocol layer and has nothing to do with this page.
                Both pages name the other. Translators: the game sense and the
                networking sense may need different words.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  packet(7), protocol(7)
hook:           Passive Sniffer output, where the same captured item shows a
                hardware identifier, an address, a port and a payload all at
                once and the player has to work out which of those is which
                (../design/07-recon-tools.md §1). Also the first time a player
                meets Overflow Kit's "bypasses one layer" and assumes it means
                this.
misconception:  commonly believed the OSI seven-layer model describes how the
                internet is built, because it is what every introductory course
                and certification teaches; actually the internet was specified
                against a different four-layer model (RFC 1122), OSI was a
                rival architecture whose protocols lost, and its numbers
                survive mainly as industry shorthand — "layer 4 load balancer",
                "layer 7 firewall" — rather than as a description of anything.
transfer:       The player can now read a job advertisement or a product page
                that says "layer 7" and say what it means (the application
                content — URLs, headers) as against "layer 4" (addresses and
                ports only), and can say why "layer 8 problem" is a joke about
                the user.
verified:       Four internet layers (application, transport, internet, link) —
                RFC 1122 §1.1.3; seven OSI layers and their names —
                ISO/IEC 7498-1. Checked 2026-07-25.

## DESCRIPTION

One item in a Passive Sniffer's output carries several different kinds of
identifier at once: something naming the device on the local wire, something
naming the machine across the network, something naming the program, and then
the contents. They are not alternatives. They are stacked, because each was put
there by a different piece of software that knew nothing about the others.

That is layering. The part that moves data across one wire does not know what
an address is. The part that routes between networks does not know that a
conversation exists. The part that keeps a conversation in order does not know
which wire anything crossed. Each solves one problem and hands the result down.

The payoff is that any of them can be replaced. Wifi swapped in under Ethernet
without a single application being rewritten, because the application never
knew what was down there.

## REAL-WORLD COUNTERPART

real, simplified — the internet's four-layer model, plus the OSI vocabulary
that surrounds it.

The internet was specified against four layers (RFC 1122): **link** (one wire
or one radio hop), **internet** (addressing and routing between networks),
**transport** (conversations between programs — TCP and UDP), and
**application** (whatever the two programs are actually saying).

The seven-layer OSI model — physical, data link, network, transport, session,
presentation, application — comes from a different architecture, standardised
by ISO, whose protocol suite lost to TCP/IP in the late 1980s. It is a good
teaching vocabulary and a poor description. Real traffic does not respect it:
TLS sits between transport and application and is not usefully either layer 5
or layer 6, and nobody who works on this uses the words "session layer".

The numbers survive anyway, because "layer 4" and "layer 7" are convenient
shorthand for *addresses and ports only* versus *the actual content*.

## CAVEATS

What is simplified: this page teaches the four-layer model as the working
description and names the seven OSI layers only so the reader recognises the
numbers. It does not teach OSI as a model to reason with, because doing so
produces confident wrong answers about where TLS, tunnels and QUIC live.

Layers are also not sealed. A firewall that inspects URLs is reading the
application layer to make a routing-layer decision; NAT rewrites transport
port numbers inside a network-layer device. The model describes an intention,
not a rule anyone is obliged to obey.
```

### 3.4 `port(7)`

```
id:             port
section:        7
name:           port
canonical:      port
gloss:          A number from 0 to 65535 saying which program traffic is for.
status:         real
aliases:        port number, service port
seeAlso:        ip-address(7), tcp(7), udp(7), socket(7), port-scan(7),
                port-sweep(1), packet-filter(7), nat(7)
reading:        RFC 9293 §3.1 (the TCP header); RFC 768 (UDP); RFC 6335
                (the IANA port ranges); the IANA Service Name and Transport
                Protocol Port Number Registry
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          first-session
prerequisites:  network(7), process(7)
hook:           The first Port Sweep result, in the starting kit
                (../design/06-intrusion-tools.md), which is a list of numbers
                and the player has to know what a number in that list is
                before the result means anything.
misconception:  commonly believed a port is a physical or permanent opening on
                a machine, so that machines have ports the way they have USB
                sockets and "closing a port" is like locking a door; actually
                it is a 16-bit number written into a packet's header saying
                which program the data is for — nothing is open unless a
                program is waiting on that number, and closing one means
                stopping the program or refusing its traffic.
transfer:       List what is listening on the reader's own machine.
                Linux: `ss -tlnp`. macOS: `lsof -iTCP -sTCP:LISTEN -P -n`.
                Windows: `netstat -ano`. The player can now name three programs
                on their own computer that are waiting for network traffic, and
                say which numbers they are waiting on. Assumes a terminal or
                Command Prompt; see ED-8.
verified:       16-bit port fields, 0–65535 — RFC 9293 §3.1 and RFC 768;
                ranges 0–1023 System, 1024–49151 User, 49152–65535 Dynamic —
                RFC 6335 §6; nmap scans the 1000 most common ports by default
                and -p- scans 1 through 65535 — nmap(1). Checked 2026-07-25.

## DESCRIPTION

A Port Sweep comes back as a list of numbers. Each number is one place a
program on that node is waiting for traffic.

A node has one address and may be running twenty programs that all want to be
reached. The address gets data to the node; the number decides which program
receives it. Nothing is reachable unless something is waiting on a number, and
a number nothing is waiting on is not a locked door — it is not a door.

The number is 16 bits wide, which is where 0 to 65535 comes from, and which is
why a full sweep is expensive: 65,536 separate questions, every one of which
arrives at the target where it can be logged. That cost is real, not a game
tax. See port-scan(7) and noise(7).

## REAL-WORLD COUNTERPART

real — ports, exactly as every networked computer has them.

Both TCP and UDP put a 16-bit source port and a 16-bit destination port at the
very start of their headers. That is the whole mechanism.

The registry splits the range three ways (RFC 6335). **0–1023** are System or
Well Known ports, assigned by IANA and on Unix reservable only by a privileged
program — 22 for SSH, 53 for DNS, 443 for HTTPS. **1024–49151** are User or
Registered ports, assigned on request. **49152–65535** are Dynamic or
Ephemeral, never assigned, and handed out to the client end of a connection so
that replies can find their way back.

These are conventions, not laws. Moving SSH to port 2222 works, and hides
nothing from anyone who checks. Normally only one program may wait on a given
number at a time, which is why starting a second copy of a server fails with
"address already in use"; modern systems have a deliberate exception so that
several worker processes can share one, but exclusive is the default.

nmap(1) scans the 1000 most common ports by default rather than all of them,
and `-p-` asks for 1 through 65535. The difference in how long those two take
is the 16 bits, made visible.
```

### 3.5 `ip-address(7)`

```
id:             ip-address
section:        7
name:           IP address
canonical:      IP address
gloss:          The 32- or 128-bit number that identifies one network interface.
status:         real
aliases:        IP, address, IPv4, IPv6, node address
seeAlso:        packet(7), subnet(7), routing(7), nat(7), dhcp(7), dns(7),
                loopback(7), private-address(7), identity-spoofer(1)
reading:        RFC 791 §2.3 (IPv4 addressing); RFC 8200 (IPv6);
                RFC 5952 (the canonical text form of an IPv6 address);
                RFC 6890 (special-purpose address registries)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  packet(7)
hook:           `/net/<node-address>/` in the command namespace
                (../client/04 §3.2) and every target the player names on the
                `map` window. Also Identity Spoofer, whose entire premise is
                that an address is attributable to somebody
                (../design/08-stealth-and-noise.md §1).
misconception:  commonly believed an address identifies a computer, and by
                extension a person, so that a log line containing one has
                identified who did something; actually it identifies one
                network interface at one moment — a laptop has several at once,
                everyone behind a router shares the one the world sees, and
                leases are reassigned, so at best it identifies a subscriber
                account and a time window.
transfer:       Linux: `ip addr`. macOS: `ifconfig`. Windows: `ipconfig /all`.
                The player can now count how many addresses their own machine
                holds right now (loopback, at least one wired or wireless, and
                more if a VPN or container runtime is installed) and can say
                that none of them is the address a website sees.
verified:       32-bit IPv4 addresses, 4,294,967,296 total — RFC 791 §2.3;
                128-bit IPv6 addresses — RFC 8200 §2; IPv6 text form and ::
                compression — RFC 5952 §4; IANA's free IPv4 pool was exhausted
                on 3 February 2011 — IANA/NRO announcement. Checked 2026-07-25.

## DESCRIPTION

Every node on the `map` window has one, and `/net/` is indexed by it. It is how
a packet finds a machine at all: the address is written into every packet's
header, and every router on the way reads it to decide where to send that
packet next.

What it identifies is narrower than it looks. It names **one network
interface** — one way into a machine — not the machine and certainly not its
owner. A rig with two connections has two. A rig behind something that
rewrites addresses at its edge has one the world sees and a different one it
actually holds. See nat(7).

That gap is the whole of the game's attribution problem, and Identity Spoofer
exists because of it.

## REAL-WORLD COUNTERPART

real — IP addresses, and there are two kinds in service at once.

An **IPv4** address is 32 bits, written as four numbers 0–255 separated by
dots: `198.51.100.7`. Thirty-two bits gives 4,294,967,296 possible values, and
the shortage everyone has heard about is that arithmetic and nothing else —
IANA handed out the last unallocated blocks on 3 February 2011.

An **IPv6** address is 128 bits, written as up to eight groups of four
hexadecimal digits separated by colons, with one run of zero groups allowed to
collapse to `::` — so `2001:db8::1`. The address space is 2^128, which is a
number with 39 digits and is not going to run out.

The reader's own laptop holds several addresses simultaneously: a loopback
address it uses to talk to itself, one per active network interface, and one
more for every VPN or container runtime installed. Addresses are also usually
**leased** rather than owned — a home machine is given one when it joins the
network and may be given a different one next week. See dhcp(7).

Which is why an address in a log line is evidence of a connection, not of a
person. It is a starting point for an investigation, and an investigation is
what it takes to get from there to anybody.
```

### 3.6 `subnet(7)`

```
id:             subnet
section:        7
name:           subnet
canonical:      subnet
gloss:          A block of addresses sharing a fixed number of leading bits.
status:         real
aliases:        CIDR, prefix, netmask, network prefix
seeAlso:        ip-address(7), routing(7), private-address(7), dhcp(7),
                packet-filter(7), topology-mapper(1)
reading:        RFC 4632 (Classless Inter-Domain Routing);
                RFC 1918 (private address allocation);
                RFC 6890 (special-purpose address registries)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          investigating
prerequisites:  ip-address(7), routing(7)
hook:           The `map` window's grouping of nodes that are reachable from
                one another but not from outside, and Topology Mapper's
                one-hop-to-two-hop extension, which is a claim about which
                nodes share a neighbourhood (../design/07-recon-tools.md §2).
misconception:  commonly believed the /24 in 192.168.1.0/24 is a version, a
                count of machines, or an arbitrary label; actually it is a
                count of leading bits that are fixed — the network part — so
                32 minus that is how many bits are left to number hosts, and
                the block's size follows immediately from the arithmetic.
transfer:       Linux and macOS: `ip route` or `netstat -rn`. Windows:
                `route print`. Every line is a prefix. The player can now read
                their own home network's prefix, say how many addresses it can
                hold, and recognise 0.0.0.0/0 as "everything else".
verified:       CIDR notation and the abolition of address classes — RFC 4632
                §3; private ranges 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 —
                RFC 1918 §3; shared CGN space 100.64.0.0/10 — RFC 6598;
                link-local 169.254.0.0/16 — RFC 3927. Checked 2026-07-25.

## DESCRIPTION

Nodes on the `map` are not scattered evenly. Some sit in groups that can reach
each other cheaply and reach the rest of the graph through one exit, which is
what Topology Mapper's second hop is really telling you: not "two steps away"
but "in the neighbourhood beyond this one".

A subnet is that neighbourhood, defined arithmetically. Write an address and a
number after a slash — `192.168.1.0/24` — and the number says how many of the
address's leading bits are fixed for everything in the block. Twenty-four bits
fixed out of thirty-two leaves eight to number hosts, so the block holds 256
addresses, of which 254 are usable.

Why this matters to a router: it can hold one rule for a whole block instead of
one rule per address. That is not an optimisation, it is the only reason the
system works at all.

## REAL-WORLD COUNTERPART

real — CIDR, from RFC 4632, which replaced the older fixed classes A, B and C
in 1993.

The arithmetic is worth doing once. A `/24` fixes 24 bits and leaves 8, so 2^8
= 256 addresses. A `/25` leaves 7, so 128. A `/16` leaves 16, so 65,536. Each
step of one in the prefix halves the block. In IPv4 two addresses in each block
are reserved — the all-zeros network address and the all-ones broadcast — which
is why a /24 is usually described as holding 254 hosts.

The internet's routing tables are built entirely of prefixes rather than
addresses. There are 4.3 billion IPv4 addresses and, on the order of a million
prefixes in the global routing table — a compression of roughly four thousand
to one, and the reason a router can hold the internet in memory.

Certain prefixes have fixed meanings the reader will recognise on sight:
`10.0.0.0/8`, `172.16.0.0/12` and `192.168.0.0/16` are private (RFC 1918);
`127.0.0.0/8` is loopback; `169.254.0.0/16` is what a machine gives itself when
nothing hands it an address; and `100.64.0.0/10` (RFC 6598) belongs to a
carrier's own translation layer. Seeing that last one on a home router's
external side means the subscriber is behind carrier-grade NAT and cannot
accept inbound connections at all.
```

### 3.7 `routing(7)`

```
id:             routing
section:        7
name:           routing
canonical:      routing
gloss:          Each hop deciding, alone, where to send a packet next.
status:         real, simplified
aliases:        route, routing table, next hop
seeAlso:        ip-address(7), subnet(7), hop(7), ttl(7), traceroute(1),
                packet(7), rtt(7), topology-mapper(1)
reading:        RFC 791 §3.2 (forwarding); RFC 4271 (BGP-4);
                ip-route(8)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  ip-address(7), packet(7)
hook:           The `map` window's edges, and Traversal-class planning
                (../design/05-hacking-minigame.md), where the player is
                choosing a path through a graph and assuming the path is a
                thing that exists.
misconception:  commonly believed a connection follows a path that is chosen
                and held when it opens, so that "the route" is a property of
                the conversation; actually nothing is ever negotiated — each
                router makes an independent decision per packet from its own
                table, two packets of one conversation can take different
                paths, and the path can change mid-conversation without either
                end being told.
transfer:       Linux and macOS: `ip route` or `netstat -rn`. Windows:
                `route print`. The player can now find their machine's default
                route, say what "default" means, and explain why their laptop
                needs no knowledge of the internet's shape to reach any of it.
verified:       Hop-by-hop forwarding and longest-prefix match — RFC 791 §3.2
                and RFC 1812 §5.2.4; BGP as a path-vector protocol between
                autonomous systems — RFC 4271 §3. Checked 2026-07-25.

## DESCRIPTION

The `map` window draws edges, and an edge looks like a decision somebody made.
No such decision exists anywhere in the system.

Each machine that handles a packet — each router — holds a table of address
blocks and, for each block, the single next machine to hand it to. It reads the
destination, finds the most specific block that contains it, forwards it there,
and forgets it. It does not know the rest of the path. It does not know whether
there is a rest of the path.

The consequences are worth carrying into the game. A path is not owned by a
conversation, so two probes to the same node can go different ways and come
back with different timings. A path can change while you are using it. And
nothing along it has agreed to anything: routing is a chain of independent
local guesses that usually adds up.

## REAL-WORLD COUNTERPART

real, simplified — IP forwarding, and the routing protocols that fill the
tables.

A routing table is a list of prefixes and next hops. **Longest-prefix match**
decides: if a packet's destination falls inside both `10.0.0.0/8` and
`10.1.2.0/24`, the /24 wins because it is more specific. Almost every table
ends with `0.0.0.0/0` — the default route, matching everything, which is how a
laptop with four table entries reaches the whole internet.

Between organisations, the tables are filled by BGP (RFC 4271), in which each
network advertises to its neighbours which prefixes it can reach and how far
away they are. It is a system built on assertion: a network that announces a
prefix it does not own can attract that traffic, and route hijacks — sometimes
accidental, sometimes not — are a recurring real event.

## CAVEATS

What is simplified: the game's map presents routes as stable edges between
nodes, and hop distance as a fixed property. Real routing is per-packet and
per-moment. A drawn map is an accumulation of past measurements, and it is out
of date the instant a routing decision changes anywhere along the way.

Also, the return path is not the forward path. Traffic from A to B and traffic
from B to A routinely traverse different routers, in different countries, with
different delays — which is why an apparently slow hop in a measurement is very
often nothing of the kind.
```

### 3.8 `ttl(7)`

```
id:             ttl
section:        7
name:           TTL
canonical:      TTL
gloss:          A counter decremented at every router, to kill looping traffic.
status:         real
aliases:        time to live, hop limit
seeAlso:        hop(7), routing(7), icmp(7), traceroute(1), packet(7),
                resolver(7)
reading:        RFC 791 §3.1 (the IPv4 Time to Live field); RFC 8200 §3
                (the IPv6 Hop Limit field); RFC 792 (ICMP Time Exceeded)
notes:          A real/real homonym, and a nasty one. This page is the IP
                header field, which counts routers. DNS records also carry a
                field called TTL — see resolver(7) — and that one really is
                measured in seconds. Both pages must name the other. Not
                currently a row in ../client/04 §2.15; see NW-4. Translators:
                do not translate "TTL"; it is an identifier.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  hop(7), icmp(7)
hook:           Hop distance on the `map` window, which is a number the game
                presents as a fact about a node. TTL is how that number is
                measured in reality, and Topology Mapper's one-hop-to-two-hop
                upgrade is exactly a TTL of 1 becoming a TTL of 2
                (../design/07-recon-tools.md §2).
misconception:  commonly believed TTL is an amount of time, because it is
                called Time To Live and is a number; actually it counts routers
                and not seconds — IPv6 renamed the identical field Hop Limit
                precisely because the original name misled everyone for
                twenty-five years.
transfer:       Run `ping example.com` on macOS, Linux or Windows and read the
                `ttl=` value in each reply. Compare it with 64, 128 or 255 —
                the usual starting values — and the difference is roughly how
                many routers the reply crossed on its way back. The player can
                now estimate their distance from any host they can ping.
verified:       IPv4 TTL is an 8-bit field, maximum 255, decremented at each
                hop, packet discarded at zero — RFC 791 §3.1; IPv6 calls the
                same field Hop Limit — RFC 8200 §3; a router discarding a
                packet for this reason sends ICMP Time Exceeded, type 11 —
                RFC 792; common initial values 64 (Linux, macOS) and 128
                (Windows) — IANA IP defaults and vendor documentation.
                Checked 2026-07-25.

## DESCRIPTION

The `map` window says a node is two hops away. Something has to have measured
that, because nothing in a network announces its own distance.

Every packet carries a small counter. Each router that forwards it subtracts
one. If the counter reaches zero, that router throws the packet away and sends
a short message back saying so — and that message carries the router's own
address.

Its actual job is garbage collection. Routing tables can, briefly, form a loop;
without the counter a packet caught in one would circle forever, and enough of
them would fill the network with traffic that can never arrive. The counter
guarantees every packet dies.

The side effect is the useful part: send packets with the counter set to 1,
then 2, then 3, and each one dies one step further away and reports back. That
is how distance is measured, and it is all traceroute(1) does.

## REAL-WORLD COUNTERPART

real — the IPv4 Time to Live field, and the IPv6 field of the same shape.

It is 8 bits, so the maximum is 255. RFC 791 originally intended it as seconds
and required routers to decrement it by at least one per second *or* per hop;
in practice every router simply subtracts one, and the seconds reading died. In
IPv6 the field was renamed **Hop Limit** (RFC 8200), which is what it always
was.

Operating systems set different starting values — Linux and macOS start at 64,
Windows at 128, some network equipment at 255 — which is why a reply's
remaining value is both a distance estimate and a weak hint about what kind of
system sent it. That hint is genuine passive fingerprinting: it costs the
observer nothing and the sender cannot tell it happened.

When a router discards a packet this way it sends back ICMP **Time Exceeded**
(RFC 792, type 11). That message is not an error to be fixed. It is the entire
mechanism traceroute depends on, and a network that filters it is a network you
cannot map.
```

### 3.9 `traceroute(1)`

```
id:             traceroute
section:        1
name:           traceroute
canonical:      traceroute
gloss:          Reveals each router on the way to a host, one step at a time.
status:         real, simplified
aliases:        tracert, tracepath, map
seeAlso:        map(1), ttl(7), icmp(7), hop(7), routing(7), rtt(7),
                topology-mapper(1), trace(7)
reading:        traceroute(8); RFC 792 (ICMP Time Exceeded);
                RFC 1393 (the traceroute technique, historical)
notes:          MANDATORY DISAMBIGUATION (../client/04 §2.15). This is not the
                game's `trace` meter. `trace` is the defender-side attribution
                meter that races a breach (../design/05-hacking-minigame.md
                §4); this is a measurement command. They appear in the same
                breach, which is why the collision is the sharpest in the
                client. trace(7) opens its CAVEATS with "this is not
                traceroute(1)"; this page returns the favour.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          investigating
prerequisites:  ttl(7), icmp(7), hop(7)
hook:           `map` and `traceroute <node>` in the command catalogue
                (../client/04 §3.10), and the `map` window itself, whose Unix
                analogue ../client/00-client-overview.md §6.1 gives as
                traceroute.
misconception:  commonly believed traceroute shows the route your traffic
                takes, in both directions, as a fact; actually it shows one
                direction, on one attempt, and every timing it prints includes
                a return path you cannot see — which is why an apparently slow
                middle hop is almost always a router deprioritising its own
                replies rather than congestion on the path.
transfer:       macOS and Linux: `traceroute example.com`. Windows:
                `tracert example.com`. Run it twice and compare. The player can
                now count the routers between themselves and a host, recognise
                a `*` as "this router did not answer" rather than "nothing is
                there", and see for themselves that the path is not stable.
verified:       Incrementing TTL and ICMP Time Exceeded replies — RFC 792,
                traceroute(8); classic Unix traceroute sends UDP to high ports
                from base 33434, `-I` selects ICMP, Windows tracert uses ICMP
                echo by default — traceroute(8) and Microsoft documentation;
                default 30 hops maximum, 3 probes per hop — traceroute(8).
                Checked 2026-07-25.

## SYNOPSIS

       traceroute [-n] [-v] [--] <node>

## DESCRIPTION

Prints every router between your rig and a node, in order, with a round-trip
time for each. This is what fills in the `map` window's hop distances, and it
is the only tool in the game that discovers path structure rather than
endpoints.

It works by abusing the hop counter. Send a probe with the counter set to 1 and
the first router kills it and reports itself. Set it to 2 and the second one
does. Keep going until the destination answers instead. Each reply names the
router that sent it, so the list assembles itself one step at a time.

A `*` means no reply came back for that probe. It does not mean there is no
router there — most often the router is configured not to answer, or is
rate-limiting the replies it sends.

## OPTIONS

       -n, --dry-run   Print the published cycle and noise cost. Send nothing.
       -v, --verbose   Show each probe separately rather than one line per hop.
       --explain       Print this DESCRIPTION and exit.

## EXIT STATUS

       0    The measurement completed.
       1    Refused — a rule applied and nothing changed.
       69   The server could not be reached. Nothing was sent.
       75   Sent, no answer yet. Retry is safe.

## REAL-WORLD COUNTERPART

real, simplified — traceroute(8), on every Unix-like system, and `tracert` on
Windows.

The classic Unix implementation sends UDP packets to unlikely high port numbers
starting at 33434, so the destination answers with "port unreachable" and the
measurement knows it has arrived; `-I` switches it to ICMP echo, which is what
Windows `tracert` uses by default. By default it gives up after 30 hops and
sends three probes per hop, which is why each line usually shows three times.

Read those three times together. If they disagree wildly, the path is unstable
or the router is busy with its own housekeeping. A single high value on one
middle hop with normal values after it means that router deprioritised your
probe, not that the path is congested — traffic passing *through* a router and
traffic *addressed to* a router are handled by different parts of it.

## CAVEATS

What is simplified: **traceroute measures one path at one moment. It does not
draw a graph.** The game's persistent map is an accumulation of many such
measurements, which is exactly what real network mapping is — and it is out of
date the moment routing changes.

It also measures only the forward direction. Every time printed is a round
trip, and the return leg may cross entirely different routers in a different
country. You are looking at a path with one eye.
```

### 3.10 `tcp(7)`

```
id:             tcp
section:        7
name:           TCP
canonical:      TCP
gloss:          An ordered, checked, two-way byte stream between two programs.
status:         real
aliases:        Transmission Control Protocol, connection, stream
seeAlso:        packet(7), port(7), udp(7), three-way-handshake(7),
                packet-loss(7), socket(7), packet-filter(7), rtt(7)
reading:        RFC 9293 (TCP, which obsoletes RFC 793); ss(8); netstat(8)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  packet(7), port(7)
hook:           The `audit` window's connection table, where each row is one
                connection in one state, and the moment a player finds a
                connection with no owning process — the discrepancy
                ../design/04-mining.md §3.1 requires to always be present in
                the data.
misconception:  commonly believed "reliable" means TCP guarantees the data
                arrives, so a TCP connection is a safe channel; actually it
                guarantees only that whatever arrives is complete and in order,
                or that the connection fails and says so — it is an
                error-detection promise, not a delivery promise, and a cable
                pulled halfway through a transfer still loses the transfer.
transfer:       Linux: `ss -tan`. macOS: `netstat -an -p tcp`. Windows:
                `netstat -an`. Read the state column: LISTEN, ESTABLISHED,
                TIME_WAIT. The player can now say what those three mean and why
                a machine that has closed a connection still shows it for a
                couple of minutes.
verified:       Connection-oriented, ordered, checksummed byte stream with
                retransmission — RFC 9293 §3.1 and §3.7; 32-bit sequence
                numbers — RFC 9293 §3.1; loss treated as a congestion signal —
                RFC 9293 §3.8 and RFC 5681; head-of-line blocking as the
                motivation for QUIC — RFC 9000 §1. Checked 2026-07-25.

## DESCRIPTION

Every row in the `audit` window's connection table is one of these, and each
row has a state — a connection is a thing with a lifecycle, not a pipe that
either exists or does not.

TCP takes the packet network underneath, which loses things, duplicates things
and delivers things out of order, and presents both ends with an orderly stream
of bytes instead. It numbers everything it sends, acknowledges what it
receives, resends what was not acknowledged, and puts what arrives back into
order before anyone reads it.

Both ends keep state for the whole conversation, which is why a connection can
be listed, counted, and found to have no owning process. That is what makes
the game's cross-view audit possible at all: a hidden miner still has to hold
an open connection, because it still has to talk.

## REAL-WORLD COUNTERPART

real — TCP, specified in RFC 9293, which replaced the 1981 original in 2022.

Three properties, and the third is the one people get wrong.

**Ordering.** Every byte is numbered with a 32-bit sequence number, so the
receiver can reassemble out-of-order arrivals and detect a gap.

**Retransmission.** Data that goes unacknowledged for long enough is sent
again. The sender keeps a copy until it is acknowledged, which is why an
apparently idle connection still holds memory at both ends.

**"Reliable" is narrower than it sounds.** TCP does not promise delivery. It
promises that what is delivered is intact and in order, and that if it cannot
achieve that, the connection breaks and the program is told. Reliability here
means *no silent corruption*, not *no failure*.

Loss is also a signal, not just a fault: TCP interprets a missing packet as
congestion and slows down. That is why one poor wireless link makes a fast
connection feel slow, and why a single lost packet stalls everything queued
behind it — head-of-line blocking, and the specific problem QUIC and HTTP/3
were built to escape by moving to UDP.
```

### 3.11 `udp(7)`

```
id:             udp
section:        7
name:           UDP
canonical:      UDP
gloss:          Sending a message with no connection, ordering or delivery check.
status:         real
aliases:        User Datagram Protocol, datagram
seeAlso:        tcp(7), port(7), packet(7), dns(7), packet-loss(7),
                port-scan(7)
reading:        RFC 768 (UDP — three pages, including the header diagram);
                RFC 9000 §1 (QUIC's rationale for building on UDP)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  packet(7), port(7)
hook:           Services a Port Sweep finds that never appear as connections in
                the `audit` window, because there is no connection to appear —
                the first time the two views legitimately disagree and it is
                not a rootkit.
misconception:  commonly believed UDP is TCP with the safety features removed,
                so it is a worse protocol used only where raw speed matters
                more than correctness; actually it is deliberately empty — it
                adds port numbers and a checksum to a raw packet and nothing
                else — and it is chosen when the application can do reliability
                better than TCP can, which is why HTTP/3 is built on it.
transfer:       Linux: `ss -uan`. macOS: `netstat -an -p udp`. Windows:
                `netstat -an -p UDP`. Note that no line has a connection state,
                because there is nothing to have a state. The player can now
                say why a UDP port scan is much slower and much less certain
                than a TCP one.
verified:       8-byte header of source port, destination port, length and
                checksum, no connection or ordering — RFC 768; DNS uses UDP
                port 53 by default with fallback to TCP — RFC 1035 §4.2;
                QUIC and HTTP/3 run over UDP — RFC 9000 §1, RFC 9114 §3.
                Checked 2026-07-25.

## DESCRIPTION

A Port Sweep can report a service that never shows up as a connection in the
`audit` window, and nothing is wrong. Some services do not hold connections,
because the protocol they speak does not have any.

UDP sends a message and stops. There is no handshake to open anything, no
acknowledgement that it arrived, no ordering between one message and the next,
and no retransmission when one is lost. If the message matters, the program
sending it has to notice and deal with it.

For the game this has one direct consequence: a service reached this way is
harder to enumerate. A closed TCP port answers "no". A closed UDP port usually
answers nothing at all, which is indistinguishable from a firewall, a busy
host, or a lost probe. Uncertainty is the default here, not a tool limitation.

## REAL-WORLD COUNTERPART

real — the User Datagram Protocol, RFC 768. The specification is three pages
long, which is itself the lesson: the entire header is four fields totalling 8
bytes, against TCP's 20-byte minimum.

It is not the cheap option. It is the *empty* option, chosen when a program
wants to build its own rules on top.

**A DNS lookup** is one question and one answer. Opening a connection first
would triple the exchange to save nothing, so it does not. **Voice and video**
would rather lose a packet than wait for it — a re-sent fragment of speech from
200 ms ago has no use, and delivering it late is worse than dropping it.
**QUIC**, and therefore **HTTP/3**, rebuild ordering and retransmission on top
of UDP deliberately, so that a lost packet stalls only the one stream that
needed it, instead of everything behind it.

The general rule worth carrying: TCP's guarantees are excellent and they are
not free, and a protocol that knows exactly which of them it needs can do
better by asking for none of them and adding back only what it wants.
```

### 3.12 `dns(7)`

```
id:             dns
section:        7
name:           DNS
canonical:      DNS
gloss:          The system that turns names people type into numeric addresses.
status:         real, simplified
aliases:        Domain Name System, name resolution, lookup
seeAlso:        resolver(7), ip-address(7), udp(7), tls(7), flow-metadata(7),
                packet-capture(7)
reading:        RFC 1034 and RFC 1035 (the Domain Name System);
                RFC 6891 (EDNS(0)); root-servers.org
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  ip-address(7), udp(7)
hook:           Recovered machine-authored text in the `recon` window, which
                contains names where the `map` window contains addresses, and
                the player has to work out that these are two ways of saying
                the same thing (../client/00-client-overview.md §6.1).
misconception:  commonly believed a lookup asks one server that knows the
                answer, the way a phone book is consulted; actually the name is
                resolved by walking a hierarchy from the right — the root, then
                the top-level domain, then the domain's own servers — with each
                step handing back only a pointer to the next, and the whole
                walk is skipped whenever a cached answer is still valid.
transfer:       Run `ping example.com` on macOS, Linux or Windows and read the
                address it prints before the first reply. That address came
                from a lookup the player never asked for and never saw. Where
                `dig` is installed, `dig +trace example.com` prints the walk
                down the hierarchy one step at a time.
verified:       Hierarchical delegation and the record types A, AAAA, CNAME,
                MX, NS, TXT — RFC 1034 §3.6 and RFC 1035 §3.2.2; port 53, UDP
                by default, 512-byte answer limit before EDNS(0) — RFC 1035
                §4.2.1 and RFC 6891 §4.3; thirteen root server identities A–M
                served by well over a thousand anycast instances —
                root-servers.org (instance count is ⚠ approximate and moves).
                Checked 2026-07-25.

## DESCRIPTION

Recovered text names machines. The `map` names addresses. Both are real, and
something has to connect them.

A name is read right to left, and each part is a delegation. For
`node.eye.example`, the root of the system knows who runs `example`, that
operator knows who runs `eye.example`, and that operator holds the actual
answer for `node`. Nobody holds the whole tree; everybody holds one branch and
a pointer to the next.

The answer that comes back is a **record**, and there is more than one kind: an
address record, an alias to another name, a mail destination, arbitrary text.
Each carries an expiry, so anything that saw the answer knows how long it may
keep using it.

For an operator this is a target as well as a service. Names are looked up
before anything else happens, in the clear, by default — see resolver(7).

## REAL-WORLD COUNTERPART

real, simplified — the Domain Name System, RFC 1034 and RFC 1035, essentially
unchanged in shape since 1987.

The record types the reader will actually meet: **A** (an IPv4 address),
**AAAA** (an IPv6 address), **CNAME** (this name is an alias for that one),
**MX** (send mail here), **NS** (this domain is served by those name servers),
**TXT** (arbitrary text, which is how domain ownership gets proved to third
parties).

The hierarchy is anchored by thirteen root server identities, lettered A to M.
There are not thirteen machines: each identity is announced from many locations
at once, so the thirteen addresses are served by well over a thousand physical
instances, and a query goes to whichever is nearest.

It runs on port 53, over UDP, with TCP as a fallback. Historically an answer
had to fit in 512 bytes, which is why DNS answers are terse and why the
extension mechanism EDNS(0) exists at all.

## CAVEATS

What is simplified: the game resolves node addresses directly and never shows a
lookup happening, so a player never watches the walk that this page describes.
The map's addresses behave as though they were always known.

This page also omits DNSSEC — the signing scheme that lets a resolver check an
answer was not forged — and everything about how zones are transferred and
kept in step. Both are real, both matter, and neither has a surface here.
```

### 3.13 `resolver(7)`

```
id:             resolver
section:        7
name:           resolver
canonical:      resolver
gloss:          The server that does your name lookups for you, and caches them.
status:         real
aliases:        recursive resolver, DNS server, nameserver
seeAlso:        dns(7), ttl(7), flow-metadata(7), tls(7), proxy(7),
                packet-capture(7), traffic-analyzer(1)
reading:        RFC 1035 §7 (resolver behaviour); RFC 2181 §8 (the TTL field);
                RFC 8484 (DNS over HTTPS); RFC 7858 (DNS over TLS)
notes:          The TTL on a DNS record IS measured in seconds. The TTL in an
                IP header is NOT — it counts routers. Same three letters, two
                unrelated fields. ttl(7) carries the matching note. See NW-4.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          investigating
prerequisites:  dns(7)
hook:           The Eye's visibility over the player without touching them —
                the surveillance premise of ../design/14-world-and-narrative.md
                made concrete — and Traffic Analyzer, which learns who is live
                by watching rather than probing
                (../design/07-recon-tools.md §1).
misconception:  commonly believed DNS changes take hours to "propagate" across
                the internet, as though the new answer were being pushed out;
                actually nothing is pushed anywhere — resolvers keep serving
                the answer they already have until its expiry runs out, so the
                delay experienced is exactly the expiry that was set before the
                change was made.
transfer:       Where `dig` is installed, run `dig example.com` twice a few
                seconds apart and watch the TTL count down — that is one
                resolver's cache emptying in real time. On Windows,
                `ipconfig /displaydns` prints the machine's own cache with time
                remaining per entry. The player can now explain why their own
                DNS change "hasn't taken effect yet".
verified:       Recursive resolution performed on the client's behalf, with
                caching — RFC 1035 §7.4; TTL is an unsigned value in seconds
                carried in the low 31 bits, maximum 2,147,483,647 — RFC 2181
                §8; DoH and DoT encrypt the query to the resolver — RFC 8484
                §1, RFC 7858 §1. Checked 2026-07-25.

## DESCRIPTION

Your rig does not walk the name hierarchy itself. It asks one server to do the
whole job and hand back the answer, and that server keeps a copy for next time.

That arrangement is efficient and it is a chokepoint. Whoever operates it sees
every name you look up, in order, with timestamps — before any connection is
made, and regardless of whether the connection that follows is encrypted. A
watcher who owns your resolver does not need to break anything. They need only
read a log.

This is the honest version of the game's premise. The Eye does not have to
compromise a rig to know what it is interested in; it has to be somewhere in
the path of the questions. That is cheaper, quieter and far more complete than
intrusion, and it is why Traffic Analyzer costs nothing in noise.

## REAL-WORLD COUNTERPART

real — the recursive resolver, and the caching that makes DNS survivable.

The reader is using one right now: their ISP's by default, or a public one such
as `1.1.1.1` or `8.8.8.8` if something changed the setting. It does the walk
described in dns(7) and remembers the result.

Every record carries a **TTL** in seconds — an unsigned value up to
2,147,483,647, or roughly 68 years (RFC 2181). A resolver may keep serving a
cached answer until that runs out. This is the entire truth behind
"propagation": there is no propagation. There are only old answers expiring at
their own pace, and the delay is one you chose in advance when you set the TTL.

Classic DNS is plaintext UDP, which means the resolver is not the only observer
— anyone on the path sees the queries too. **DNS over HTTPS** (RFC 8484) and
**DNS over TLS** (RFC 7858) close that, and it is important to be exact about
what they close: they hide the query *from the path*, and hand it to the
resolver, encrypted, where it is read as normal. Changing to an encrypted
resolver is choosing a different observer, not removing one.
```

### 3.14 `nat(7)`

```
id:             nat
section:        7
name:           NAT
canonical:      NAT
gloss:          Rewriting addresses at the edge so many hosts share one of them.
status:         real, simplified
aliases:        network address translation, NAPT, PAT, CGNAT
seeAlso:        ip-address(7), private-address(7), port(7), subnet(7),
                packet-filter(7), identity-spoofer(1)
reading:        RFC 2663 (NAT terminology); RFC 3022 (traditional NAT and
                NAPT); RFC 6598 (shared address space for carrier-grade NAT)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          investigating
prerequisites:  private-address(7), port(7)
hook:           Attribution: the moment a player realises the address a
                defender logged is not the address of the machine that acted.
                Identity Spoofer trades on exactly this
                (../design/08-stealth-and-noise.md §1), and so does every heat
                and trace mechanic that assigns an action to somebody.
misconception:  commonly believed the public address a website reports is your
                computer's address, so a log line holding it has identified
                your machine; actually your machine holds a private address
                that never leaves the building — the address the world sees
                belongs to the router, is shared by every device behind it, and
                under carrier-grade NAT may be shared by hundreds of unrelated
                subscribers at once.
transfer:       Linux: `ip addr`. macOS: `ifconfig`. Windows: `ipconfig`. Find
                the machine's own address and confirm it starts 10., 172.16–31.
                or 192.168. — then compare with the external address shown on
                the home router's own status page. The two differ, and the
                player can now say which one appears in a website's logs.
verified:       Address and port rewriting with state held at the edge —
                RFC 3022 §2 and §4; terminology NAT/NAPT — RFC 2663 §3 and §4.1;
                100.64.0.0/10 reserved as shared address space for CGN and not
                routed on the public internet — RFC 6598 §1 and §5.
                Checked 2026-07-25.

## DESCRIPTION

A defender logs an address. The game treats that as attribution, and it is
worth knowing how weak the link between an address and a machine actually is.

At the boundary of most networks sits a device that rewrites every outbound
packet's source address to its own, notes which internal machine and port the
packet came from, and reverses the substitution on everything that comes back.
Dozens of machines therefore appear to the outside world as one, distinguished
only by port number.

Two consequences the game depends on. First, an address in a log is a
*household*, not a host — and correlating it to a machine takes information the
observer usually does not have. Second, an unsolicited inbound connection has
no stored substitution to reverse, so it is discarded. That is why a machine
behind one of these is hard to reach without arranging it in advance, and it is
also why that protection is a side effect rather than a security control.

## REAL-WORLD COUNTERPART

real, simplified — network address translation, and specifically NAPT: the
port-rewriting form nearly every home router performs (RFC 2663, RFC 3022).

The reader's own machine holds a private address — `192.168.x.x`,
`10.x.x.x` or `172.16–31.x.x` — that is not routable on the internet and never
leaves the building. The router holds one public address and multiplexes
everybody onto it using the source port as the distinguishing number. Port
forwarding exists because inbound traffic has no state to match, so a mapping
has to be created by hand.

Many ISPs now do it a second time. **Carrier-grade NAT** translates again in
the provider's network, and gives the customer side an address from
`100.64.0.0/10` (RFC 6598). A router whose external address is in that range
cannot accept inbound connections at all, and its subscriber's public address
is shared with hundreds of others — which makes address-based attribution
weaker still.

IPv6 largely removes the reason for any of this: there are enough addresses
that hosts can have real ones, and the filtering is done by a firewall that is
honest about being a firewall.

## CAVEATS

What is simplified: the game's nodes each hold one stable address with no
translation anywhere, so a player never sees the gap between the address a
machine holds and the address it appears to have.

This page also omits the several behaviours real translators exhibit — whether
a mapping is reused across destinations, and whether unsolicited inbound
traffic to an existing mapping is accepted. Those differences decide whether
two machines can connect to each other directly at all, which is why
peer-to-peer software is far more complicated than it looks.

And: **this is not a firewall.** It drops unsolicited inbound traffic as an
accident of having no state to match. It applies no policy, inspects nothing,
and protects nothing that initiates its own connections.
```

### 3.15 `tls(7)`

```
id:             tls
section:        7
name:           TLS
canonical:      TLS
gloss:          Encrypts a connection and proves the server holds the name asked for.
status:         real, simplified
aliases:        SSL, HTTPS, the padlock
seeAlso:        certificate(7), tcp(7), dns(7), flow-metadata(7),
                packet-capture(7), http(7), digital-signature(7), public-key-cryptography(7)
reading:        RFC 8446 (TLS 1.3); RFC 5280 (X.509 certificates);
                RFC 9849 (TLS Encrypted Client Hello)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          investigating
prerequisites:  tcp(7), dns(7)
hook:           Traffic Analyzer, which distinguishes live and defended nodes
                from dormant ones without reading anything
                (../design/07-recon-tools.md §1) — the first time the player
                sees that a tool learns something useful about traffic it
                cannot decrypt.
misconception:  commonly believed the padlock means the site is safe and that
                nobody can see what you are doing; actually it means the
                connection to some server is encrypted and that server proved
                it holds the name that was typed — an observer still sees which
                server, when, how much and how often, and the certificate says
                nothing whatsoever about whether the site is honest.
transfer:       In any browser on any platform, click the padlock and open the
                certificate. Read who issued it, which names it covers, and
                when it expires. The player can now say what the padlock does
                and does not assert, on any site, without a terminal. Works
                identically on macOS, Windows and Linux — see ED-8.
verified:       TLS 1.3 completes its handshake in one round trip, 1.2 needed
                two — RFC 8446 §2 and Appendix A; SNI is sent before encryption
                begins in TLS 1.3 unless ECH is used — RFC 8446 §4.2 and
                RFC 9849 §1; ECH was published as RFC 9849 on 3 March 2026 —
                RFC Editor. Checked 2026-07-25.

## DESCRIPTION

Traffic Analyzer tells you which nodes are live, which are defended and which
are dormant, and it does this without decrypting anything. That is not a
concession the design made — it is what encrypted traffic actually gives up.

TLS does three things to a connection. It makes the contents unreadable to
anyone in the path. It makes the contents un-editable in the path without the
tampering being detected. And it gives the client some assurance that the
server it reached is the one that holds the name it asked for.

It does exactly those three. It does not conceal that the connection exists,
which node it went to, when it started, how long it lasted, how much crossed,
or in what rhythm. Every one of those survives, and together they are enough to
identify what software is running, whether a machine is being used, and whether
something on it is calling home on a schedule. See flow-metadata(7).

## REAL-WORLD COUNTERPART

real, simplified — TLS, most visibly as the S in HTTPS. TLS 1.3 is RFC 8446;
"SSL" is the dead predecessor whose name refuses to die.

The handshake in shape: the client opens with what it supports; the server
replies with a certificate proving its name and with material for agreeing a
shared secret; both sides derive the same secret independently, and everything
after that is encrypted with it. TLS 1.3 completes this in a single round trip,
where 1.2 needed two — which, on a 70 ms path, is a visible saving.

What has historically leaked is the requested name. The client has to say which
site it wants before encryption starts, so that a server hosting many sites
knows which certificate to send, and that field — Server Name Indication — has
been readable by anyone on the path for twenty years. **Encrypted Client Hello**
(RFC 9849, published March 2026) finally encrypts it, and is not yet universally
deployed.

And the assurance is narrower than the padlock implies. A certificate proves
control of a name. A criminal who controls a name gets a valid certificate for
it in minutes, free. The padlock has never meant honest.

## CAVEATS

What is simplified: this page describes what TLS achieves and what it leaks. It
does not describe how the shared secret is agreed, what a cipher suite is, or
how the certificate's signature is checked — see public-key-cryptography(7) and
digital-signature(7), which own the mechanism.

The game has no TLS surface of its own. Nodes are either reachable or not; no
connection here is described as encrypted or not. This page exists because the
player will assume encryption defeats Traffic Analyzer, and it does not.
```

### 3.16 `flow-metadata(7)`

```
id:             flow-metadata
section:        7
name:           flow metadata
canonical:      flow metadata
gloss:          Who talked to whom, when and how much, without any content.
status:         real
aliases:        NetFlow, IPFIX, flow record, traffic analysis
seeAlso:        tls(7), packet-capture(7), port(7), tcp(7), resolver(7),
                traffic-analyzer(1), noise(7), rtt(7)
reading:        RFC 7011 (IPFIX, the IETF flow export standard);
                RFC 3954 (Cisco NetFlow v9, informational)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          investigating
prerequisites:  packet(7), port(7), tls(7)
hook:           Traffic Analyzer distinguishing active and defended nodes from
                dormant ones (../design/07-recon-tools.md §1), and the deployed
                miner's control channel — which must stay up and is "the most
                detectable part of the operation" (../client/04 §2.10).
misconception:  commonly believed that encrypting your traffic hides what you
                are doing, so an observer who cannot decrypt has been defeated;
                actually encryption hides only the content — the endpoints,
                timing, size, direction and rhythm of every connection remain
                fully visible, and those alone are enough to identify which
                applications are running, spot a program calling home on a
                schedule, and reconstruct a great deal of behaviour.
transfer:       Open any browser's developer tools, select the Network tab, and
                load a page. Every row is one request with its destination,
                start time, duration and size, and no contents shown. That is
                flow-shaped metadata about the player's own traffic, and it
                works identically on macOS, Windows and Linux — see ED-8.
verified:       Flow records key on source and destination address, source and
                destination port and protocol, with byte and packet counts and
                timestamps — RFC 7011 §2 and §3.2, RFC 3954 §6.1; flow records
                are orders of magnitude smaller than full packet capture —
                RFC 7011 §1. Checked 2026-07-25.

## DESCRIPTION

Traffic Analyzer never enters a node and never reads anything, and it still
tells you which nodes are alive, which are defended and which are asleep. That
is not a game convenience. It is what watching traffic gives you, and it is
more than most people expect.

A flow record is one summary line for one conversation: which address and port
talked to which address and port, over which protocol, when it started, how
long it lasted, how many packets and how many bytes. No content at all.

For the game the sharpest case is the deployed miner's control channel. It must
stay up, so it produces a regular, small, outbound connection at a steady
interval, and that shape is unmistakable in a flow record even though nothing
in it can be read. A rig that has been quietly hosting someone else's miner is
loud in exactly this one way, which is why the channel is the most detectable
part of the operation.

## REAL-WORLD COUNTERPART

real — flow records. Cisco's NetFlow (RFC 3954) named the idea; IPFIX
(RFC 7011) is the IETF standard, and organisations of any size collect them
continuously.

The reason they are collected is cost. A flow record is a few dozen bytes for a
conversation that carried megabytes, so an organisation that cannot afford to
store packets for a week can afford to store flows for a year. Retrospective
investigation of an incident that happened eight months ago is done with these,
and only these.

What they actually reveal, in practice:

- **Which applications are running**, from destination port and traffic shape,
  without reading a byte.
- **Beaconing.** A small outbound connection at a fixed interval, hour after
  hour, is how command-and-control channels are found — regularity is the
  signature, and encryption does not disturb it.
- **Exfiltration.** A large upload to a destination this machine has never
  contacted before is visible as a size and a direction.
- **Presence.** Whether a machine is in use at all, and when, which is why an
  empty office is obvious from flow data alone.

This is the single most under-taught fact in ordinary computer literacy: the
envelope is not protected, the envelope is cheap to keep, and the envelope is
usually enough.
```

### 3.17 `packet-capture(7)`

```
id:             packet-capture
section:        7
name:           packet capture
canonical:      packet capture
gloss:          Recording every frame an interface sees, and sending nothing.
status:         real
aliases:        sniffing, tcpdump, Wireshark, pcap, promiscuous mode
seeAlso:        packet(7), switch(7), mac-address(7), layering(7), tls(7),
                flow-metadata(7), passive-sniffer(1), noise(7)
reading:        tcpdump(8); pcap(3PCAP); the Wireshark User's Guide
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          investigating
prerequisites:  packet(7), switch(7), layering(7)
hook:           Passive Sniffer, which costs zero noise
                (../design/07-recon-tools.md §1) — the player's first encounter
                with an action that is genuinely undetectable, and the natural
                moment to ask why this one is free when Ping Sweep is not.
misconception:  commonly believed that running a sniffer on a network lets you
                read everyone else's traffic, as it did in films and in the
                1990s; actually a switched network delivers you only your own
                traffic plus broadcast, and almost everything worth reading is
                encrypted — what capture actually yields is metadata, which is
                a great deal, but it is not the contents.
transfer:       On a network the reader is responsible for: macOS or Linux,
                `sudo tcpdump -n -i any -c 20`. Read twenty lines and identify
                the addresses, the ports and the TCP flags. Windows users
                install Wireshark, which shows the same thing with a window
                around it. Requires administrator rights on all three.
verified:       Capture is receive-only and transmits nothing — tcpdump(8),
                pcap(3PCAP); a switch forwards a frame only toward the port
                associated with the destination hardware address, so capture on
                a switched segment sees own traffic plus broadcast and
                multicast — IEEE 802.1D forwarding. Checked 2026-07-25.

## DESCRIPTION

Passive Sniffer costs no noise, and unlike most zero-cost things in this game
that is not a balance decision. Listening emits nothing. There is no probe to
log, no connection to record, no timing to notice. A machine that is only
listening is, to every other machine, indistinguishable from a machine that is
switched off.

That is also why it is limited to what is adjacent. Capture shows you what
reaches your interface, and on any modern network that is your own traffic plus
whatever is broadcast to everyone. The design's "one hop, types only" is not a
concession; it is the real shape of the technique.

What you get from it is still substantial: every address, every port, every
size, every interval, and the structure of every protocol involved — because
the headers are readable even when the contents are not.

## REAL-WORLD COUNTERPART

real — packet capture, with `tcpdump(8)` on the command line and Wireshark for
the same data with a window around it.

The tool asks the operating system for a copy of every frame the interface
sees. That is a privileged operation, which is why it needs administrator
rights, and it is entirely receive-only: the capture itself transmits nothing.

**What limits you is the switch.** A switch learns which hardware address sits
behind which of its ports and then forwards each frame only out the one port
that needs it, so you receive your own traffic and broadcasts, and nothing
else. The hubs that flooded everything to everyone are gone. To see other
people's traffic you need a deliberately configured mirror port, a physical
tap, or a position on the path — and a switch that can be forced to flood is a
known failure mode rather than a design feature, which is why "a switch is a
security boundary" is not a safe assumption.

**What limits you second is encryption.** Headers are always readable —
addresses, ports, sizes, timing, TCP flags. Contents are readable only where
the protocol is plaintext, and most of what matters no longer is. That leaves
metadata, and metadata is the thing worth having anyway (flow-metadata(7)).

The reader should capture only on a network they are responsible for. Reading
other people's traffic without authority is an offence in most jurisdictions,
and the fact that it is silent is not the same as it being permitted.
```

### 3.18 `onion-routing(7)`

```
id:             onion-routing
section:        7
name:           onion routing
canonical:      onion routing
gloss:          Relaying through several hops, each peeling one encryption layer.
status:         real
aliases:        Tor, circuit, relay chain, guard, exit node
seeAlso:        relay-chain(1), proxy(7), tls(7), rtt(7), hop(7),
                flow-metadata(7), identity-spoofer(1)
reading:        The Tor Project's protocol specifications (spec.torproject.org);
                Dingledine, Mathewson & Syverson, "Tor: The Second-Generation
                Onion Router" (2004)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          adversarial
prerequisites:  proxy(7), tls(7), rtt(7)
hook:           Relay Chain — schematic-gated framework plus ethecoin-priced
                hops per session, each hop reducing attribution, slowing every
                action and holding compute
                (../design/08-stealth-and-noise.md §2). The player pays for
                each hop and should know what each one buys.
misconception:  commonly believed Tor encrypts your traffic so nobody can see
                what you are doing; actually it hides who is doing it, by
                ensuring no single relay knows both ends — the exit relay still
                sees whatever leaves the network, so anything not separately
                encrypted end-to-end is readable there, by a stranger.
transfer:       The player can now explain to somebody else why the Tor Browser
                is slow (three extra intermediate hops, each adding real
                distance and a queue), and why "use Tor" and "use HTTPS" are
                answers to two different questions — Tor hides who, TLS hides
                what, and using one without the other leaves the other exposed.
verified:       Three relays by default — guard, middle, exit — with a separate
                symmetric key negotiated per relay and one layer removed per
                relay per cell — Tor protocol specification, spec.torproject.org;
                the exit relay makes the plain connection to the destination and
                can observe unencrypted content — Tor Project relay
                documentation. Checked 2026-07-25.

## DESCRIPTION

Relay Chain charges per hop, per session, and slows every action you take. Both
of those are honest, and it is worth knowing precisely what the money buys,
because it is not secrecy.

Your traffic is passed through a chain of intermediate machines. Before it
leaves, it is wrapped in one layer of encryption for each machine in the chain,
in reverse order. The first relay removes the outermost layer and sees only the
next relay's address. The second removes the next and sees only the third. The
last removes the final layer and sees the destination — and does not see you.

No single relay knows both who you are and what you wanted. That is the entire
property, and it is why the chain must be at least three machines long: with
two, the first and last are neighbours and can be the same operator without
anybody noticing.

The cost is latency, and it is unavoidable. Every hop is real distance and a
real queue.

## REAL-WORLD COUNTERPART

real — onion routing, deployed at scale as Tor since 2004.

A Tor circuit uses three relays by default: a **guard** (the first, deliberately
kept stable over time), a **middle**, and an **exit**. The client negotiates a
separate symmetric key with each and applies one layer of encryption per relay.
Each relay removes exactly one layer and forwards the rest, which is where the
name comes from.

What each one knows is the design: the guard knows your address and the middle's
address, and nothing about the destination. The exit knows the destination and
whatever leaves the network, and nothing about you. The middle knows neither
end. Three is the smallest number that produces this, and adding more costs
latency while buying little against the adversary the system is built for.

Two limits worth carrying. **The exit sees what leaves.** Traffic that is not
independently encrypted end-to-end is readable by a stranger operating that
relay, which is a genuinely worse position than your own ISP. And **an observer
who can see both ends at once** may correlate the timing and volume at each and
link them — traffic confirmation, which onion routing does not claim to solve.

The reader should take one thing away: this hides *who*, not *what*. Use it with
TLS, never instead of it.
```

### 3.19 `rtt(7)`

```
id:             rtt
section:        7
name:           round-trip time
canonical:      round-trip time
gloss:          How long a message takes to get there and the answer back.
status:         real
aliases:        latency, lag, delay, ping time, RTT
seeAlso:        latency(7), bandwidth(7), jitter(7), hop(7), packet(7),
                routing(7), onion-routing(7), traceroute(1),
                packet-loss(7)
reading:        traceroute(8); ping(8); RFC 2681 (a round-trip delay metric)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         05
stage:          operating
prerequisites:  packet(7), hop(7)
hook:           Relay Chain's per-action latency tax
                (../design/08-stealth-and-noise.md §2), which prices the
                trade-off directly: more hops means harder to trace and slower
                to act. The player is being asked to pay in time, and should
                know what they are paying for.
misconception:  commonly believed a faster internet connection means a faster
                experience, so slowness is fixed by buying more megabits;
                actually most of what feels slow is delay, which is set by
                distance and by how many round trips something takes, and
                additional bandwidth reduces neither.
transfer:       Run `ping` against something nearby and something far away on
                macOS, Linux or Windows and compare the times. They differ by
                distance, and they will not change if the connection is
                upgraded. The player can now diagnose "the internet is slow"
                as either a delay problem or a capacity problem, which are
                fixed by completely different things.
verified:       Light in optical fibre travels at roughly 200,000 km/s, about
                two thirds of its vacuum speed; the shortest New York–London
                fibre path is about 5,577 km, giving a round-trip floor near
                56 ms, with commercial low-latency routes measured at just
                under 59 ms and typical routes at 70–90 ms — submarine cable
                operator published figures and standard propagation
                arithmetic; geostationary orbit is 35,786 km, giving a
                round-trip propagation of roughly 480 ms before processing.
                ⚠ Low-Earth-orbit figures (~25–60 ms) vary by operator and
                location. Checked 2026-07-25.

## DESCRIPTION

Each Relay Chain hop adds a latency tax to every action you take. That is the
correct model, and the game is charging you in the currency that actually
matters, because in a network delay is the scarce thing and capacity usually is
not.

Latency is how long one round trip takes: send something, get an answer back.
It is set by how far the signal travels, how many machines handle it on the way,
and how long it waits in a queue at each. Only the last of those can be bought
away.

The number worth carrying is this: an action that requires several exchanges
back and forth pays the round-trip time once per exchange, no matter how much
capacity the link has. That is why a chain of three relays does not slow you
down by a third — it slows every single exchange, all session.

## REAL-WORLD COUNTERPART

real — latency, and its persistent confusion with bandwidth.

**Latency is delay. Bandwidth is rate.** They are independent. A satellite link
can carry an enormous amount of data per second and still take half a second to
answer; a slow rural line can answer in 15 ms and take a minute to move a file.

The scale is the lesson:

| Path | Typical round trip |
|---|---|
| the same building | under 1 ms |
| the same city | 5–15 ms |
| New York to London, over fibre | 70–90 ms, against a physical floor near 56 ms |
| via a geostationary satellite | around 500 ms |
| via low-Earth-orbit satellites | roughly 25–60 ms, varying |

Light in glass travels at about two thirds of its speed in vacuum, roughly
200,000 km/s. The New York–London floor is not an engineering shortcoming; it
is 5,577 km of glass, twice.

The practical consequence, which is where most people's model is wrong:
something that makes fifty requests one after another pays fifty round trips.
On a 70 ms path that is three and a half seconds before a single byte of
content is counted, and upgrading from 100 Mbit to 1 Gbit changes it by
nothing at all. Reducing the number of round trips is the only lever, which is
why protocol design spends so much of its effort on exactly that.
```

---

## 4. What this domain deliberately does not teach

`00-curriculum-and-method.md` §7.3: **an entry with no hook is not written**, however interesting, because the delivery mechanism is contextual and a concept with no surface has no trigger. Everything below is genuinely worth knowing and none of it has a surface in this game. Where the impulse is strong, the honest answer is a `reading:` citation, not a page.

| Not taught | Why | Where an interested player is pointed |
|---|---|---|
| **Inter-domain routing policy** — BGP attributes, AS paths, peering economics, route hijack case studies | `routing(7)` names BGP in two sentences because route hijacks are a real event worth recognising. Beyond that there is no surface: the game's graph has no autonomous systems and no commercial relationships | RFC 4271 |
| **Interior routing protocols** — OSPF, IS-IS, RIP, and how tables get filled | The player never configures a router. Teaching the protocols without a router to point at is pure taxonomy | — |
| **Wireless** — 802.11, channels, WPA2/WPA3, roaming | No wireless surface anywhere in the game. The one wireless fact that matters — that a radio link is a shared medium, so capture behaves differently there — is one clause in `packet-capture(7)` | — |
| **Congestion-control algorithms** — Reno, CUBIC, BBR | `tcp(7)` teaches that loss is treated as a congestion signal, which is the part that explains observable behaviour. Which algorithm does it changes nothing a player can see | RFC 5681 |
| **The OSI model as a working taxonomy** | Deliberate, and the most important omission here. `layering(7)` names the seven layers so the reader recognises "layer 7", and refuses to teach them as a model to reason with, because doing so reliably produces confident wrong answers about TLS, tunnels and QUIC | ISO/IEC 7498-1 |
| **QUIC and HTTP/3 internals** — streams, connection migration, 0-RTT | Named in `udp(7)` and `tcp(7)` as the reason head-of-line blocking matters. The internals have no surface | RFC 9000, RFC 9114 |
| **Multicast, VLANs, MPLS, SDN, overlay networks** | Enterprise and carrier machinery. The game's network is a flat graph of nodes | — |
| **Email transport** — SMTP, IMAP, SPF/DKIM/DMARC | There is no mail surface. This is a sizeable and genuinely useful body of knowledge that this game simply has no hook for | — |
| **Subnetting arithmetic as a drill** | `subnet(7)` teaches what a prefix length *means* and how block size follows from it. Converting masks by hand is a certification exercise, not a concept | — |
| **Socket programming** — `bind`, `listen`, `accept`, `select` | Sections 2 and 3, and the game has no programming surface at all (`00-curriculum-and-method.md` §5.3). `socket(7)` in domain 02 teaches what a socket *is*; nothing teaches how to open one | — |
| **The cryptography inside TLS** — key exchange, cipher suites, signature verification | Domain **06** owns it, and R8 forbids this domain requiring it. `tls(7)` stops at what TLS achieves and what it leaks | `public-key-cryptography(7)`, `digital-signature(7)` |
| **The game's own transport security** — `../architecture/07-transport-security.md` | It is `[PROPOSAL]`, hand-rolled, and explicitly unreviewed ("reviewed patterns, unreviewed code", CLAUDE.md). Writing a teaching page about it would lend it an authority it has not earned. When a cryptographer has read it, revisit | — |
| **How to conduct an attack** — scanning strategy, evasion recipes, exploitation | `00-curriculum-and-method.md` **ED-9**'s line, which this domain hits first. `port-scan(7)` explains what a scan reveals and what it costs the scanner; it names no target and gives no method. `packet-capture(7)` states the legal position rather than implying it | — |

---

## 5. Open questions

Prefix **`NW-`**, unused elsewhere in the doc set (`../design/15-open-questions.md` records `OQ-`/`P-`/`D-`/`S-`/`N-`/`E-`/`A-`/`G-`/`W-`/`Q-`, and the client set records `CL-`, `V-`, `PN-`, `SK-`, `T-`, `WL-`, `RI-`, `AX-`; `00-curriculum-and-method.md` adds `ED-`). Log in `../design/15-open-questions.md` §2 if this doc set is adopted.

- **NW-1: ✅ RESOLVED with ED-3 (2026-07-25) — networking is `05`, and this file was renamed to match.** The file was numbered 04 when written and its entries carried `domain: 04`; **those fields have been re-stamped to `05`.** R8 is unaffected in substance: the only cross-domain prerequisite is `port(7)` → `process(7)`, which points downward into `03` under either scheme. What the resolution *adds* is room below this domain for `04-the-command-line.md`, which several of this document's transfer tests lean on — see **NW-6**.
- **NW-2: who owns the recon and stealth *command* pages?** `port-sweep(1)`, `passive-sniffer(1)`, `ping-sweep(1)`, `topology-mapper(1)`, `traffic-analyzer(1)`, `relay-chain(1)` are item pages for game tools whose entire `REAL-WORLD COUNTERPART` is networking. Under the §1.4 ladder they could sit in this domain; under the escape hatch they sit with whichever domain owns the tool category. This matters because `port-sweep(1)` is **already written** at `../client/04` §4.9 and a second author will otherwise rewrite it. Proposal: the section-1 tool pages belong to the domain that owns the tool table, and this document supplies their counterpart content as a citation. Decide before the second command page is drafted.
- **NW-3: R4 and R8 versus `tls(7)` and `certificate(7)`.** These entries cannot be *fully* explained without `public-key-cryptography(7)` and `digital-signature(7)`, which live in a higher-numbered domain that R8 forbids depending on. §1.5 adopts a reading — *naming a later concept and pointing at its page is permitted; depending on it is not*, tested by reading the page cold per R5 — and both entries are written to survive that test. If the contract's authors read R4 more strictly, the consequence is that `tls(7)` and `certificate(7)` move to domain 05 and this domain cites them, which costs `flow-metadata(7)` a prerequisite. **This is the decision `certificate(7)` was deliberately not written pending.**
- **NW-4: `../client/04` §2.15 needs a TTL row.** A DNS record's TTL is measured in seconds; an IP header's TTL counts routers. Same three letters, two unrelated fields, and the collision is real/real rather than game/real, which is why the existing homonym table does not anticipate it. `ttl(7)` and `resolver(7)` both carry a mandatory `notes:` line here; the table should carry the row so the CI check and the translators see it too.
- **NW-5: two things want the id `bandwidth`.** The real concept is bits per second; the rig upgrade caps simultaneous engagements, which in reality is a connection limit or a worker-pool size (`../client/04` §2.9, §2.15). Both are section 7 and ids are unique per section. **Recommendation: one page, `bandwidth(7)`, teaching the real concept with a `CAVEATS` naming the rig stat** — exactly the pattern §2.15 prescribes for Canary Token, where both meanings are real and both are named on one page. Needs agreement from whichever domain owns rig statistics before either is written.
- **NW-6: this domain is the evidence for ED-8, and it says option (c) works.** Eleven of eighteen written entries have a transfer test that runs unmodified on macOS, Windows and Linux — `ping`, `tracert`/`traceroute`, `ipconfig`/`ip addr`, `route print`/`ip route`, `netstat`, a browser's Network tab, and the padlock's certificate view. The seven that need a Unix shell are `port(7)`, `tcp(7)`, `udp(7)`, `subnet(7)`, `resolver(7)`, `packet-capture(7)` and `nat(7)`, and five of those have a documented Windows equivalent written into the field. **Proposal: adopt ED-8 option (c) as the doc set's rule, with option (a) as the fallback where nothing universal exists.** This domain is the strongest case; `04-the-command-line.md` is the weakest, and it has since been written — its **SH-7** argues that for a domain whose subject *is* the shell, option (c) is meaningless and option (a) is the honest answer. **Decide against both.**
- **NW-7: does the game render an HTTP request or a DNS record on screen anywhere?** `00-curriculum-and-method.md` §5.3 puts formats the game *describes* in section 7 and formats the player actually *reads* in section 5. `http(7)` and the DNS record types inside `dns(7)` are section 7 on the assumption that the `recon` window paraphrases rather than renders. If `../client/05-tool-windows-and-layout.md` decides the recovered-document reader shows real headers, both move to section 5 and gain a `SYNOPSIS`. Cheap now, a rewrite later.
- **NW-8: `packet-capture(7)`'s transfer test asks the reader to run `tcpdump`.** That is squarely inside **ED-9**'s undecided dual-use boundary, and this domain reaches it before `06-cryptography-and-trust.md` does. The position taken here: explaining what a capture or a scan *reveals* is education; naming a target, supplying an evasion recipe, or teaching scanning strategy is not. `packet-capture(7)` states the legal position in its own text rather than leaving it implied, and `port-scan(7)` is written to describe cost and evidence rather than technique. **Confirm this line before `06-cryptography-and-trust.md` is extended**, because it argues the same boundary on its first page — where it is raised as **CT-5**, with a proposed rule for the whole doc set.
- **NW-9: is certificate revocation worth an entry?** CRLs, OCSP, OCSP stapling and the industry's drift toward very short-lived certificates are all real, and the underlying fact — *a certificate can be wrong before it expires, and checking that is unreliable* — is a genuine misconception-killer. It is currently one sentence inside `certificate(7)`. It has no game surface at all, which under §7.3 means it should stay a sentence. Recorded so the decision is deliberate rather than an oversight.
- **NW-10: how much IPv6 does the game's own address rendering force?** `ip-address(7)` teaches both widths and both notations, and `nat(7)` says IPv6 removes the need for translation. But if `/net/<node-address>/` renders addresses in a dotted-quad shape, every player will build a v4-only model regardless of what the page says, because the surface will contradict it thirty times an hour. Ask `../client/05-tool-windows-and-layout.md` what a node address looks like. If it is v4-shaped, either the shape changes or IPv6 needs its own entry with a hook that is honest about being weak.
