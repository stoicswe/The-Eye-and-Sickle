# 08 — Detection and defence — noticing, proving, and the line you do not cross

**Status:** ⚠️ **[PROPOSAL]** — the *obligation* is Established and unusually specific. Two pages in this domain carry **mandatory content** fixed by `../client/04-terminology-and-education.md`: `log-scrubber(1)` **must** carry the defender's answer (§2.7), and `auto-counter-daemon(8)` **must** state that hacking back is illegal in most jurisdictions (§2.8). `00-curriculum-and-method.md` §7.2 names `cross-view-detection(7)` — cited here, owned by `03` — as the highest-priority audit target in the entire doc set. What is first-pass design here is the inventory, the stage assignments, and the twenty written entries.
**Depends on:** `00-curriculum-and-method.md` — **the contract**: §3 (the entry template), §4 (the status vocabulary), §5 (man-section assignment), §6 (stages and rules R1–R8), §7 (coverage and the dual-use question), §8 (the writer's loop). **None of it is re-specified here.** Also `../client/04-terminology-and-education.md` §2.7 (stealth tools), §2.8 (**defense tools — the mandatory notes**), §2.15 (the homonym table), §3.10 (the command catalogue), §4.4 (the citation ban), §4.8 (the shipped file format); `../design/09-defense-and-hardening.md` §1–§3; `../design/04-mining.md` §3.1–§3.2; `../design/08-stealth-and-noise.md` §2; `../design/12-identity-and-social.md` (the canary handle-tag → evidence path); `../design/glossary.md`; `../../CLAUDE.md`
**Depended on by:** nothing — this is the highest-numbered domain and **no other document may name one of its entries in `prerequisites`** (rule **R8**). That was checked before the document was written, not after: no `prerequisites` field anywhere in `01`–`07` names a detection concept.

---

## 1. What this domain is

### 1.1 The domain in one paragraph

Everything in this domain follows from one uncomfortable fact: **you cannot prevent a sufficiently determined intruder, so the job is to notice.** Detection is the discipline of arranging a machine so that an intrusion leaves a trace somebody will see, and defence is the discipline of making that trace expensive to erase. Both are fundamentally statistical rather than absolute, which is why this domain spends as much time on false positives, base rates and alert fatigue as on the mechanisms — a detector that cries wolf is worse than no detector, and that is a mathematical claim, not a proverb. At the end sits the one question in the whole curriculum with a legal rather than a technical answer: what you may do about it.

### 1.2 Why a player of *this* game benefits

Because this domain is the game's founding story, and the story is real. `../design/04-mining.md` §3.1 requires that a careful player can find a rootkit-wrapped miner by noticing that two views of their own machine disagree — and that is, almost exactly, how the modern discipline began. In 1986 Cliff Stoll, an astronomer running a lab's computers, was asked to resolve a **75-cent accounting discrepancy**. He could have written it off. Instead he pulled the thread and found an intruder selling access to the KGB. The game's central skill — notice that the numbers do not add up, then pull the thread — is not a game mechanic dressed as security. It is the discipline's founding anecdote, and a player who acquires the instinct has acquired the actual thing.

The second benefit is the one this domain owes the player rather than the game: **the boundary.** A game about intrusion that never says "and doing this to a machine you do not own is a crime with a prison sentence attached" has taught something false by omission. `hack-back(7)` is the one page in the game that tells a player not to do something, and `../client/04` §2.8 makes it mandatory rather than optional.

### 1.3 The surfaces this domain answers to

| Surface | Spec | Concepts it raises |
|---|---|---|
| **Canary Token** — a fake file that alerts you and tags the toucher's handle | `../design/09-defense-and-hardening.md` §2; `../client/04` §2.8 | `honeytoken(7)`, `indicator-of-compromise(7)`, `attribution(7)` |
| **Detection Array (T1–T3)** — permanent compute for a standing discovery chance | `../design/09` §1–§2; `../design/04-mining.md` §3 | `intrusion-detection(7)`, `detection-cost(7)`, `false-positive(7)` |
| **Honeypot Stash** — a decoy zone of junk, indistinguishable until extraction | `../design/09` §2 | `honeypot(7)` |
| **Tarpit** — slows every intruder action, buys response time | `../design/09` §2; `../design/10` §1 | `tarpit(7)`, `incident-response(7)` |
| **Auto-Counter Daemon** — automatic weak counter-attack when raided offline | `../design/09` §2; **`../client/04` §2.8 mandatory note** | `hack-back(7)`, `automated-response(7)`, `attribution(7)` |
| **Log Scrubber** — erases your traces after an operation | `../design/08-stealth-and-noise.md` §2; **`../client/04` §2.7 mandatory note** | `anti-forensics(7)`, `log-integrity(7)`, `timestomping(7)` |
| The `audit` window's three tables, and the discrepancy between them | `../design/04-mining.md` §3.1 | `cross-view-detection(7)` (**`03`'s**), `integrity-monitoring(7)` |
| `scan --quick\|--full\|--thorough` at 5 / 15 / 35 compute | `../design/04-mining.md` §3.2 | `detection-cost(7)`, `false-positive(7)` |
| The canary handle-tag feeding the informant evidence path | `../design/12-identity-and-social.md` | `attribution(7)`, `indicator-of-compromise(7)` |
| A paranoid loadout costing more compute than it protects | `../design/09` §3 | `defence-in-depth(7)`, `detection-cost(7)` |

### 1.4 What this document owns, and what it does not

- **Owned here:** detection as a discipline, its statistics, integrity and audit trails, the deception tools (honeytokens, honeypots, tarpits), anti-forensics and its counters, attribution, and the legality of retaliation.
- **`rootkit(7)` and `cross-view-detection(7)` are NOT owned here.** `03-operating-systems.md` §1.4 claims both explicitly and writes both in full (its §3.19 and §3.20), on the correct ground that a rootkit is defined by what it does to *the kernel's answers*. This document cites them constantly and defines neither. **This is the single most likely place for a duplicate entry to appear in the whole doc set** — see **DF-1**.
- **`packet-filter(7)` / firewalling is `05-networking.md`'s.** A firewall is a networking concept that happens to be sold as a defence tool. `firewall(8)` is inventoried here as a *command page* only, and its teaching payload is deferred to `05`.
- **Hashing, signatures and canonicalization are `06`'s; append-only logs and provenance are `07`'s.** `log-integrity(7)` leans on all of them and defines none — it is about the *operational* problem of keeping a log trustworthy, not the cryptography that makes it possible.
- **Not owned here:** `process(7)`, `signal(7)`, `permissions(7)`, `log(7)`, `daemon(7)` — operating systems. `noise(7)`, `heat(7)`, `personal heat` — game resources. `threat-model(7)`, `security-properties(7)` — `06`, and this document's entries sit on them.

---

## 2. The concept inventory

### 2.1 How to read the table

`§` is the man section (`00` §5). `Stage` is when the curriculum first *offers* a concept, not a lock. Prerequisites in **bold** point outside this domain, and every one points *downward* — R8 is satisfied trivially here, because this is the highest-numbered domain and nothing above it exists. **●** marks the twenty written out in full in §3.

### 2.2 The inventory

| | id · § | Name | Gloss | Status | Stage | Prerequisites | Game surface |
|---|---|---|---|---|---|---|---|
| ● | `intrusion-detection` · 7 | intrusion detection | Arranging things so that a break-in leaves a mark somebody sees. | `real` | investigating | **`log(7)`**, **`threat-model(7)`** | Detection Array; the `audit` window |
| ● | `detection-cost` · 7 | detection cost | Watching is never free; the watching itself consumes the machine. | `real` | operating | **`compute(7)`** | Detection Array's permanent 6/14/25; scan at 5/15/35 |
| ● | `false-positive` · 7 | false positive | An alarm about something that turned out to be nothing. | `real` | investigating | `intrusion-detection(7)` | A scan flagging a legitimate process |
| | `false-negative` · 7 | false negative | The break-in your detector looked straight at and did not report. | `real` | investigating | `false-positive(7)` | A Quick Scan missing a rootkit-wrapped miner |
| ● | `base-rate-fallacy` · 7 | base rate fallacy | Why an accurate test still mostly fires on innocent things. | `real` | adversarial | `false-positive(7)` | Why a cheap scan's hits are mostly wrong |
| ● | `alert-fatigue` · 7 | alert fatigue | What happens to a warning nobody believes any more. | `real` | adversarial | `base-rate-fallacy(7)` | Running every defence at once and ignoring all of it |
| ● | `defence-in-depth` · 7 | defence in depth | Layers, on the assumption each one will eventually fail. | `real` | operating | **`threat-model(7)`** | Firewall + Tarpit + Canary + Array together (`../design/09` §3) |
| ● | `audit-trail` · 7 | audit trail | The record of who did what, kept for the day you must reconstruct it. | `real` | investigating | **`log(7)`**, **`privilege(7)`** | The `audit` window's name is not decorative |
| ● | `integrity-monitoring` · 7 | integrity monitoring | Noticing that something changed which had no business changing. | `real` | investigating | **`hash(7)`**, **`filesystem(7)`** | Detection Array; the storage table's delta since last audit |
| ● | `log-integrity` · 7 | log integrity | Keeping a record the intruder cannot quietly rewrite. | `real` | adversarial | `audit-trail(7)`, **`append-only-log(7)`** | Log Scrubber — and what defeats it |
| ● | `indicator-of-compromise` · 7 | indicator of compromise | A specific observable that means somebody has been here. | `real` | investigating | `intrusion-detection(7)` | The canary's handle-tag; a socket with no owning process |
| ● | `anti-forensics` · 7 | anti-forensics | Removing the traces an intrusion left, rather than the intrusion. | `real` | adversarial | `audit-trail(7)`, `indicator-of-compromise(7)` | **Log Scrubber** (`../design/08` §2) |
| ● | `timestomping` · 7 | timestomping | Backdating a file so it stops looking recently touched. | `real` | adversarial | `anti-forensics(7)`, **`inode(7)`** | Log Scrubber's implied capability |
| | `forensic-artefact` · 7 | forensic artefact | Something a system recorded without being asked to. | `real` | adversarial | `anti-forensics(7)` | What survives a Log Scrubber run |
| | `chain-of-custody` · 7 | chain of custody | The unbroken record making evidence worth anything later. | `real` | adversarial | `audit-trail(7)`, **`provenance-chain(7)`** | The informant evidence path (`../design/12`) |
| ● | `honeytoken` · 7 | honeytoken | A thing with no purpose but to tell you somebody touched it. | `real` | investigating | `indicator-of-compromise(7)` | ⚠ **Canary Token** — §2.15 homonym, mandatory `notes:` |
| ● | `honeypot` · 7 | honeypot | A whole machine or store that exists only to be broken into. | `real` | investigating | `honeytoken(7)` | **Honeypot Stash** |
| ● | `tarpit` · 7 | tarpit | Not stopping an intruder, but making everything they do slow. | `real` | investigating | **`tcp(7)`**, `defence-in-depth(7)` | **Tarpit** (`../design/09` §2) |
| | `deception-technology` · 7 | deception technology | Defending by giving an intruder convincing things that are false. | `real` | adversarial | `honeypot(7)` | Honeypot Stash + Canary together |
| ● | `attribution` · 7 | attribution | Working out who actually did it, which is harder than it sounds. | `real` | adversarial | `indicator-of-compromise(7)`, **`ip-address(7)`** | The canary's handle-tag; Identity Spoofer defeating it |
| ● | `hack-back` · 7 | hacking back | Retaliating against an attacker's machine, and why you may not. | `real` | adversarial | `attribution(7)` | ⚠ **Auto-Counter Daemon** — mandatory legal statement |
| ● | `automated-response` · 7 | automated response | Letting software act on an alert without waiting for a person. | `real, simplified` | adversarial | `false-positive(7)`, `intrusion-detection(7)` | Auto-Counter Daemon firing while you are offline |
| | `incident-response` · 7 | incident response | What you do in the hour after you find out, decided beforehand. | `real` | adversarial | `intrusion-detection(7)`, `tarpit(7)` | The bot-backlog triage window (`../design/10` §1) |
| | `containment` · 7 | containment | Stopping the spread before working out what happened. | `real` | adversarial | `incident-response(7)` | Killing a miner before auditing it |
| | `threat-hunting` · 7 | threat hunting | Looking for intruders on the assumption alarms missed them. | `real` | adversarial | `intrusion-detection(7)`, **`cross-view-detection(7)`** | A Thorough Scan run with no alert to prompt it |
| | `baseline` · 7 | baseline | A record of normal, without which "abnormal" means nothing. | `real` | investigating | `integrity-monitoring(7)` | The storage delta *since last audit* |
| | `anomaly-detection` · 7 | anomaly detection | Flagging what differs from normal rather than what matches a rule. | `real` | adversarial | `baseline(7)`, `base-rate-fallacy(7)` | Noise spikes reading as suspicious |
| | `signature-detection` · 7 | signature detection | Matching against a list of known-bad things, and its ceiling. | `real` | investigating | `intrusion-detection(7)`, **`hash(7)`** | ⚠ §2.15 homonym with `digital-signature(7)` — mandatory `notes:` |
| | `network-detection` · 7 | network intrusion detection | Watching the wire rather than the machine. | `real` | adversarial | **`packet-capture(7)`**, `intrusion-detection(7)` | Traffic Analyzer read from the defender's side |
| | `tamper-evidence` · 7 | tamper evidence | Not preventing the change, but guaranteeing it shows. | `real` | adversarial | **`append-only-log(7)`** | The public ledger, from the defender's view |
| | `least-privilege` · 7 | least privilege | Giving a thing exactly the power it needs and no more. | `real` | investigating | **`privilege(7)`**, **`permissions(7)`** | A deployed miner running with the host's rights (**I6**) |
| | `attack-surface` · 7 | attack surface | Every way in, counted — including the ones you forgot. | `real` | operating | **`port(7)`**, **`daemon(7)`** | The connection table; armed defences as exposure |
| | `hardening` · 7 | hardening | Removing capability you are not using, so nobody else uses it. | `real` | operating | `attack-surface(7)` | `../design/09`'s title, and its actual subject |
| | `security-theatre` · 7 | security theatre | Defence that reassures more than it protects. | `real` | adversarial | `detection-cost(7)`, `defence-in-depth(7)` | A loadout costing more compute than it defends |
| | `responsible-disclosure` · 7 | responsible disclosure | Telling the owner first, and what happens when you do not. | `real` | adversarial | `hack-back(7)` | ⚠ No game surface — **DF-6** |
| ● | `log-scrubber` · 1 | log-scrubber | Erases the traces of what you did, imperfectly. | `real` | adversarial | `anti-forensics(7)`, `log-integrity(7)` | **Log Scrubber** — mandatory defender's answer |
| ● | `auto-counter-daemon` · 8 | auto-counter-daemon | Fires back on your behalf while you are logged off. | `real, simplified` | adversarial | `automated-response(7)`, `hack-back(7)` | **Auto-Counter Daemon** — mandatory legal statement |
| | `canary` · 8 | canary | Arms a decoy that reports who touched it. | `real` | investigating | `honeytoken(7)` | **Canary Token** |
| | `detection-array` · 8 | detection-array | Reserves compute permanently to keep watching. | `real, simplified` | operating | `intrusion-detection(7)`, `detection-cost(7)` | **Detection Array T1–T3** |
| | `honeypot-stash` · 8 | honeypot-stash | Puts up a decoy store full of nothing. | `real` | investigating | `honeypot(7)` | **Honeypot Stash** |
| | `tarpit` · 8 | tarpit | Arms the delay that buys you response time. | `real` | investigating | `tarpit(7)` | **Tarpit** |
| | `firewall` · 8 | firewall | Arms the filter that drops what you did not ask for. | `real, simplified` | operating | **`packet-filter(7)`** | **Firewall T1–T3** — teaching payload is `05`'s |

**41 concepts, 20 written.** Three carry a **mandatory** obligation from outside this document: `log-scrubber(1)` and `auto-counter-daemon(8)` (`../client/04` §2.7, §2.8), and `honeytoken(7)` as a §2.15 homonym.

### 2.3 Concepts this domain cites but does not own

| Cited | Owner | Used here as |
|---|---|---|
| `rootkit(7)`, `cross-view-detection(7)` | **03** operating systems | Cited in `seeAlso` and described in bodies; **never redefined**. See **DF-1** |
| `log(7)`, `privilege(7)`, `permissions(7)`, `inode(7)`, `filesystem(7)`, `daemon(7)` | **03** operating systems | `prerequisites` |
| `packet-capture(7)`, `tcp(7)`, `ip-address(7)`, `packet-filter(7)`, `port(7)` | **05** networking | `prerequisites` and `seeAlso` |
| `hash(7)`, `threat-model(7)`, `digital-signature(7)` | **06** cryptography & trust | `prerequisites` |
| `append-only-log(7)`, `provenance-chain(7)`, `equivocation(7)` | **07** distributed systems & identity | `prerequisites` on `log-integrity(7)` and `chain-of-custody(7)` |
| `grep(1)`, `shell(7)` | **04** the command line | Transfer tests throughout |

### 2.4 The honesty ledger

**Zero `game` entries out of 41.** Every concept here exists outside the fiction, and the four `real, simplified` entries are simplified because the game's version is *narrower* than the real one, never because it is invented. The reason is structural rather than lucky: this domain's tools were mapped against real counterparts in `../client/04` §2.7–§2.8 before any of this was written, and every one of them landed on something that exists.

Four divergences, each stated on the page that owns it:

| Divergence | Where | How the page handles it |
|---|---|---|
| **Real firewalls filter by rule; the game's adds a difficulty number** | `../client/04` §2.8 | Deferred to `05-networking.md`'s `packet-filter(7)`; `firewall(8)` here is a command page only |
| **Real automated response blocks and isolates; it does not counter-attack** | `../client/04` §2.8 | `auto-counter-daemon(8)` `CAVEATS`, and `hack-back(7)` in full. **The most important caveat in this document** |
| **A Log Scrubber is more effective than real anti-forensics** | `../design/08` §2 | `log-scrubber(1)` `CAVEATS` names what really survives, and carries the defender's answer as mandated |
| **The Detection Array's per-tick chance is a game abstraction of scheduled scanning** | `../client/04` §2.8 | `detection-array(8)`, inventoried; `intrusion-detection(7)` and `detection-cost(7)` carry the real shape |

### 2.5 The graph, checked

`00` §6.4's five checks, run by hand.

1. **Acyclic** — yes. Rooted at `intrusion-detection(7)`, `detection-cost(7)` and `defence-in-depth(7)`.
2. **Every reference resolves** — within this document or to the sixteen external references in §2.3, all of which are written or inventoried in their owning domains.
3. **No gloss uses a term from a later stage** — checked line by line. `base-rate-fallacy(7)`'s gloss avoids "probability", `honeytoken(7)`'s avoids "honeypot".
4. **Reachable from a `first-session` root** — ⚠ **not within this document**, which has no `first-session` entry and should not: nothing here is needed to act in the first twenty minutes. Every chain leaves the domain downward and terminates at a root in `01`–`03`. This is the same limitation `02` raised as **CA-4**, and the same proposed fix applies.
5. **No prerequisite edge points upward** — trivially, being the highest-numbered domain. The check that mattered was the *converse*, and it was run before this document was written: **no `prerequisites` field in `01`–`07` names any concept in this inventory.** Had one done so, this material would have had to sit lower and the whole set would have renumbered again.

**Stage budget**, against `00` §6.2 as corrected (the budget counts *written* entries):

| Stage | Written here | Curriculum total after this document | `00` §6.2 budget | Verdict |
|---|---|---|---|---|
| `first-session` | 0 | **5** | ≤ 12 | Inside |
| `operating` | 2 | **51** | ~25–40 | ⚠ Still over — **ED-11**, and this document deliberately adds only two |
| `investigating` | 8 | **57** | ~40–60 | Inside |
| `adversarial` | 10 | **36** | ~25–40 | Inside. **CT-3** and **DS-4** feared this stage would overflow; with this document written it stands at 36 of 40 — tight, and the reason **DF-6** leaves `responsible-disclosure(7)` in the index |

---

## 3. The written entries

### 3.1 Which twenty, and why

Three obligations decided most of the list before taste got involved: `../client/04` §2.7 and §2.8 make content mandatory on `log-scrubber(1)` and `auto-counter-daemon(8)`, and §2.15 makes `notes:` mandatory on `honeytoken(7)`. Those three were never optional.

The rest follow `00` §8.1's priorities, with one addition specific to this domain: **the statistics were written before the mechanisms.** `base-rate-fallacy(7)` and `alert-fatigue(7)` are not garnish on intrusion detection, they are the reason it is hard, and a curriculum that teaches the tools without them produces someone who buys detectors and ignores their output — which is the actual failure mode in the actual industry.

| Entry | Chosen because |
|---|---|
| `intrusion-detection(7)` | Root of the domain, and the Detection Array is a first-class purchase |
| `detection-cost(7)` | The game charges permanent compute for watching, which is the truest thing it says about security |
| `false-positive(7)` | Prerequisite of the two entries that matter most |
| `base-rate-fallacy(7)` | The highest-value single idea here, and a genuine published result rather than folklore |
| `alert-fatigue(7)` | Where the base rate cashes out as human behaviour; the game reproduces it mechanically |
| `defence-in-depth(7)` | `../design/09` §3's whole tension — a paranoid loadout that costs more than it protects |
| `audit-trail(7)` | The `audit` window's name is not decorative, and the concept underpins four others |
| `integrity-monitoring(7)` | The storage table's *delta since last audit* is exactly this, and it is checkable |
| `log-integrity(7)` | Carries the mandatory defender's answer that `log-scrubber(1)` points at |
| `indicator-of-compromise(7)` | The canary handle-tag and the ownerless socket are both IoCs; unlocks attribution |
| `anti-forensics(7)` | The Log Scrubber's real counterpart, and where the dual-use line gets drawn explicitly |
| `timestomping(7)` | A specific, named, verifiable technique with a specific, named counter |
| `honeytoken(7)` | ⚠ Mandatory `notes:` as a §2.15 homonym, and the Canary Token is the cleverest defence in the game |
| `honeypot(7)` | Honeypot Stash, plus the domain's founding story lives here |
| `tarpit(7)` | An exact real mapping including the philosophy, which is rare enough to be worth teaching |
| `attribution(7)` | Where the canary's promise meets the Identity Spoofer's counter, and the answer is uncomfortable |
| `hack-back(7)` | ⚠ **Mandatory.** The one page that tells a player not to do something |
| `automated-response(7)` | The honest version of what the Auto-Counter Daemon claims to be |
| `log-scrubber(1)` | ⚠ **Mandatory** defender's answer (`../client/04` §2.7) |
| `auto-counter-daemon(8)` | ⚠ **Mandatory** legal statement (`../client/04` §2.8) |

The twenty rows above are the twenty entries written in §3, and they are the twenty marked **●** in §2.2.

### 3.2 A note on register, and on the dual-use line

`00` §2.4's failure modes are patronising, vague and jargon-dump. This domain's specific risk is a fourth one the method document does not name: **thriller register.** Security writing drifts toward menace — "attackers", "adversaries", "kill chains" — and menace is the enemy of accuracy, because it makes an unquantified claim feel established. There is none of it here.

The dual-use rule followed throughout is `06` **CT-5**'s, adopted as written: *entries explain what an attack class is and what defeats it; they never explain how to carry one out; the test is whether a sentence helps a defender more than an attacker.* In this domain that line is sharper than anywhere else, because half the subject matter *is* attacker technique. The concrete application: `anti-forensics(7)` names which artefacts survive erasure and why, because that is the defender's advantage; it does not name a tool or a sequence. `timestomping(7)` explains that a filesystem records more than one timestamp and that the usual technique misses one — again, the defender's advantage. Where a page could not be written that way, it was not written.

### 3.3 `intrusion-detection(7)`

```
id:             intrusion-detection
section:        7
name:           intrusion detection
canonical:      intrusion detection
gloss:          Arranging things so a break-in leaves a mark somebody sees.
status:         real
aliases:        IDS, HIDS, NIDS, host-based intrusion detection
seeAlso:        detection-cost(7), false-positive(7), integrity-monitoring(7),
                indicator-of-compromise(7), cross-view-detection(7),
                audit-trail(7), threat-model(7)
reading:        Stoll, "The Cuckoo's Egg" (1989); Anderson, "Computer
                Security Threat Monitoring and Surveillance" (1980);
                OSSEC/Wazuh, Snort, Suricata and Zeek documentation
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  log(7), threat-model(7)
hook:           Buying the first Detection Array tier and discovering it
                costs compute permanently, not once — the moment
                detection stops being a purchase and starts being a
                budget line.
misconception:  commonly believed detection is a product you install,
                which then tells you when something bad happens;
                actually detection is a practice, and the product is the
                cheap part. Somebody has to decide what "normal" looks
                like, read the output, and act — and a detector whose
                output nobody reads has a true positive rate of zero
                regardless of what it can technically see.
transfer:       On macOS or Linux, run `last | head` to see the record of
                who logged in and from where — a real audit source that
                predates every commercial product. On Linux, `sudo aureport`
                summarises the audit daemon's findings if `auditd` is
                running. The point is that the machine has been recording
                for you all along. Assumes a Unix shell — see ED-8.
verified:       `auditd` present at /usr/sbin/auditd and `last` at
                /usr/bin/last on macOS (Darwin 25.5), confirmed by
                running `command -v`; the discipline's origin in
                Anderson's 1980 report and its first public case in
                Stoll's account of the 1986 LBL intrusion; the game's
                permanent-compute costs (6/14/25 for T1-T3) —
                ../design/09-defense-and-hardening.md §1.
                Checked 2026-07-25.

## DESCRIPTION

The Detection Array does not find intruders. It raises the chance that
an intruder is noticed, permanently, in exchange for compute you never
get back.

That trade is the honest shape of the real thing, and it is why this is
a practice rather than a product. Detection has three parts, and buying
the tool only supplies the first: something that watches, somebody who
knows what normal looks like, and somebody who reads the output and
acts. Miss the third and you have paid for all of it and received none
of it.

The two ways of watching are worth separating. **Host-based** detection
watches one machine from inside it — processes, files, logins — which is
what the Array and the `audit` window do. **Network-based** detection
watches traffic instead, from a position where it can see the wire. They
fail differently: a rootkit can lie to a host-based detector
(`cross-view-detection(7)` is the counter), and a network detector
cannot see inside encrypted traffic but also cannot be lied to by the
compromised machine.

The founding insight is not technical. It is that a small discrepancy is
worth pulling on.

## REAL-WORLD COUNTERPART

real — and both the discipline and the instinct are.

The written origin is James Anderson's 1980 report for the US Air Force,
which proposed watching audit logs for misuse and named most of what the
field still does. The origin most people find memorable is Cliff Stoll's:
in 1986 he was handed a **75-cent accounting discrepancy** on a lab's
computer time, refused to write it off, and followed it to an intruder
selling access to the KGB. He is the reason this domain exists as a
discipline, and the reason this game's `audit` window works the way it
does.

The tools are ordinary and mostly free: OSSEC and Wazuh for hosts; Snort,
Suricata and Zeek for networks; `auditd` on Linux and BSD systems,
including macOS. Your machine is already recording more than you think —
`last` will show you.
```

---

### 3.4 `detection-cost(7)`

```
id:             detection-cost
section:        7
name:           detection cost
canonical:      detection cost
gloss:          Watching is never free; the watching itself uses the machine.
status:         real
aliases:        monitoring overhead, observability cost
seeAlso:        intrusion-detection(7), defence-in-depth(7),
                security-theatre(7), compute(7), alert-fatigue(7)
reading:        auditd(8) and auditctl(8) performance notes; Sysmon
                configuration guidance; any host-IDS deployment guide's
                sizing chapter
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          operating
prerequisites:  compute(7)
hook:           The Detection Array's permanent 6, 14 or 25 compute, and
                the scan tiers at 5, 15 and 35 — the first defences that
                bill continuously rather than once.
misconception:  commonly believed monitoring is essentially free because
                it only observes and does not act; actually observation
                is work — every event examined costs cycles, every event
                stored costs space, and a machine watching itself closely
                enough can spend a serious fraction of itself doing it.
transfer:       On macOS, run `sudo log stats` or open Activity Monitor
                and look at what the logging and indexing daemons are
                consuming while you do nothing. On Linux with auditd
                running, compare `auditctl -s` before and after adding a
                broad watch rule. The overhead is measurable, which is the
                whole point. Assumes a Unix shell — see ED-8.
verified:       Detection Array permanent costs of 6/14/25 and scan costs
                of 5/15/35 — ../design/09-defense-and-hardening.md §1 and
                ../design/04-mining.md §3.2; the compute-as-master-
                scarcity framing and Invariant I1 — ../../CLAUDE.md;
                macOS unified logging present as `log`, confirmed by
                `command -v log` (Darwin 25.5). Checked 2026-07-25.

## DESCRIPTION

Every defence in this game reserves compute for as long as it is armed.
The Detection Array takes 6, 14 or 25 and never gives it back while it
runs. A Thorough Scan takes 35 in one go.

This is the most honest thing the game says about security, and it is
worth resisting the urge to read it as a balance decision. Watching is
work. Something has to examine each event, decide whether it matters,
and write down the ones that do — and on a real machine that shows up as
CPU, as disk, and as the log volume you now have to store and search.

The consequence is a genuine budget rather than a checklist. `../design/09`
§3 makes it explicit: a fully paranoid loadout costs more compute than
most rigs have, so a player must choose which failure they are prepared
to accept. That is not the game being stingy. That is the actual
decision every real operations team makes, with the same structure and
usually less clarity about the numbers.

## REAL-WORLD COUNTERPART

real. Comprehensive auditing is measurably expensive, which is why
production audit rules are written narrowly rather than broadly, and why
"log everything" is a beginner's answer.

The costs stack in three places. **CPU**, in the hook that inspects each
event. **Storage**, which for a busy machine is the binding constraint —
verbose audit logs measured in gigabytes per day per host are unremarkable.
And **search**, because a log you cannot query in reasonable time during
an incident is a log you do not have.

This is also where detection meets economics rather than engineering: the
reason organisations under-monitor is rarely that they do not know how.
It is that somebody costed it.
```

---

### 3.5 `false-positive(7)`

```
id:             false-positive
section:        7
name:           false positive
canonical:      false positive
gloss:          An alarm about something that turned out to be nothing.
status:         real
aliases:        false alarm, type I error
seeAlso:        false-negative(7), base-rate-fallacy(7), alert-fatigue(7),
                intrusion-detection(7), anomaly-detection(7)
reading:        Axelsson, "The Base-Rate Fallacy and the Difficulty of
                Intrusion Detection", ACM TISSEC 3(3), 2000; any
                introductory treatment of sensitivity and specificity
notes:          Keep "false positive" and "false negative" as a pair in
                translation; a locale that renders them with unrelated
                words loses the symmetry the entries depend on.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  intrusion-detection(7)
hook:           A scan flagging something that turns out to be a
                legitimate process, and the decision about whether to
                pay for a more thorough one.
misconception:  commonly believed a detector's quality is one number —
                how accurate it is — so a better detector has fewer
                errors of both kinds; actually there are two independent
                error types and they trade against each other. Tuning
                out false alarms makes misses more likely, and there is
                no setting that eliminates both. Choosing where to sit on
                that trade is the actual engineering decision.
transfer:       Any spam filter is this trade, running on you daily. Look
                at your spam folder for something legitimate (a false
                positive) and your inbox for something obviously spam (a
                false negative), then consider which error you would
                rather the filter made — and notice the answer differs
                for a spam filter and a smoke alarm. Runs anywhere.
verified:       The two error types and their trade-off as standard
                detection theory; the intrusion-detection-specific
                consequence — Axelsson (2000) §4; the game's scan tiers
                buying signal strength rather than certainty —
                ../design/04-mining.md §3.2 and the resolution logged in
                ../design/15-open-questions.md. Checked 2026-07-25.

## DESCRIPTION

A Quick Scan is cheap and misses things. A Thorough Scan is expensive and
flags things that turn out to be nothing. Neither is broken; they sit at
different points on the same trade.

Two errors are possible and they are not the same error. A **false
positive** is an alarm about nothing — wasted attention, and worse, a
small deposit into the pile of alarms you have learned to ignore. A
**false negative** is the intrusion your detector looked straight at and
did not report.

You cannot minimise both. Make a detector more sensitive and it catches
more real intrusions *and* raises more false alarms; make it stricter and
both fall together. Every tuning knob on every detector in the world is
somewhere on that line, and picking the spot is a judgement about which
error hurts you more.

Which is why the game sells *signal strength* rather than certainty. You
are not buying a correct answer. You are buying a better position on the
trade.

## REAL-WORLD COUNTERPART

real, and general well beyond security — it is the same structure as
medical screening, spam filtering and fraud detection, and the vocabulary
is shared. In statistics the two are type I and type II errors; in
detection they are usually discussed as sensitivity against specificity.

The security-specific consequence is severe enough to have its own entry.
Because intrusions are *rare* relative to normal events, even a detector
with an impressively low false-alarm rate will produce alerts that are
mostly wrong — not because it is bad, but because of arithmetic. See
`base-rate-fallacy(7)`, which is the single most useful thing in this
domain.
```

---

### 3.6 `base-rate-fallacy(7)`

```
id:             base-rate-fallacy
section:        7
name:           base rate fallacy
canonical:      base rate fallacy
gloss:          Why an accurate test still mostly fires on innocent things.
status:         real
aliases:        base rate neglect, prosecutor's fallacy
seeAlso:        false-positive(7), alert-fatigue(7), anomaly-detection(7),
                intrusion-detection(7), attribution(7)
reading:        Axelsson, "The Base-Rate Fallacy and the Difficulty of
                Intrusion Detection", ACM TISSEC 3(3), 2000;
                Kahneman & Tversky on base-rate neglect (1973)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  false-positive(7)
hook:           Running a cheap scan repeatedly and noticing that most
                of what it flags is nothing — and the temptation to
                conclude the scan is broken.
misconception:  commonly believed that a test which is 99% accurate means
                a positive result is 99% likely to be real; actually that
                depends entirely on how rare the thing is. If intrusions
                are rare enough, the overwhelming majority of alerts from
                a 99%-accurate detector are false — and no improvement in
                the detector fixes it, because the problem is not the
                detector.
transfer:       Do the arithmetic once and you will never unsee it. Take
                10,000 events, of which 10 are intrusions. A detector
                that catches 99% of intrusions and false-alarms on 1% of
                normal events finds ~10 real ones and ~100 false ones —
                so about 9 alerts in 10 are wrong, from a detector almost
                everyone would call excellent. The same arithmetic is why
                a positive result on a rare-disease screening test is
                usually not the disease. Runs anywhere; needs no shell.
verified:       The arithmetic above recomputed from the stated rates
                (0.99 × 10 ≈ 10 true; 0.01 × 9,990 ≈ 100 false ⇒ ~9%
                precision); the result and its application to intrusion
                detection — Axelsson (2000), which states that the false
                alarm rate is the limiting factor for IDS effectiveness;
                base-rate neglect as a general cognitive result —
                Kahneman & Tversky (1973). Checked 2026-07-25.

## DESCRIPTION

Run a cheap scan often enough and most of what it flags will be nothing.
The natural conclusion is that the scan is bad. The natural conclusion is
wrong, and understanding why is the most useful thing in this section.

Here is the arithmetic, once. Suppose 10,000 things happen on your rig,
and 10 of them are an intrusion. Your detector catches 99% of intrusions
and raises a false alarm on only 1% of normal events — by any ordinary
standard, an excellent detector.

It finds about 10 real intrusions. It also raises about 100 false alarms,
because 1% of 9,990 normal events is 100. So roughly **9 out of every 10
alerts are wrong**, and the detector did nothing incorrectly.

The lever that actually matters, then, is not sensitivity — it is the
false-alarm rate, because it is multiplied by the enormous number of
normal events. Halving your misses barely changes the picture; halving
your false alarms halves the noise.

This is also why "scan more often" is not automatically better, and why
the game charges more for a scan that is *more specific* rather than one
that merely runs more.

## REAL-WORLD COUNTERPART

real, and it is the central quantitative result about intrusion
detection. Stefan Axelsson's 2000 paper argued precisely this: because
intrusions are rare relative to normal activity, the false alarm rate —
not the detection rate — is the limiting factor on whether an IDS is
usable at all.

The same arithmetic runs everywhere rare things are tested for. It is why
a positive screening result for a rare disease is usually not that
disease, and why the "prosecutor's fallacy" — treating the probability of
the evidence given innocence as the probability of innocence given the
evidence — has led to real wrongful convictions.

It is worth carrying out of this game as a habit: whenever someone quotes
an accuracy figure, ask how common the thing being detected actually is.
The answer usually changes the conclusion.
```

---

### 3.7 `alert-fatigue(7)`

```
id:             alert-fatigue
section:        7
name:           alert fatigue
canonical:      alert fatigue
gloss:          What happens to a warning nobody believes any more.
status:         real
aliases:        alarm fatigue, alert overload
seeAlso:        base-rate-fallacy(7), false-positive(7), detection-cost(7),
                security-theatre(7), automated-response(7)
reading:        Axelsson (2000); US FDA and Joint Commission literature
                on clinical alarm fatigue; post-incident reporting on
                the 2013 Target breach
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  base-rate-fallacy(7)
hook:           Arming every defence at once, then finding the alerts
                arriving faster than they can be triaged — the game's
                bot-backlog timer applied to your own warnings.
misconception:  commonly believed that more alerting is safer, because a
                missed warning is worse than an extra one; actually the
                extra ones destroy the useful ones. A warning is only
                worth anything if somebody acts on it, and attention is a
                fixed budget — so past a threshold, adding alerts reduces
                the number of real problems handled.
transfer:       Look at any notification setting on your own devices that
                you have muted, and ask what you would miss if it
                mattered. That is alert fatigue, self-inflicted and
                already happening. The professional version differs only
                in stakes. Runs anywhere; needs no shell.
verified:       Alarm fatigue as a documented and studied clinical
                patient-safety hazard — US FDA and Joint Commission
                literature; the false-alarm rate as the binding
                constraint on IDS usability — Axelsson (2000); alerts
                that fired and were not acted upon in the 2013 Target
                breach — contemporaneous post-incident reporting.
                ⚠ The Target detail is from secondary reporting rather
                than a primary incident report — see §5 DF-8.
                Checked 2026-07-25.

## DESCRIPTION

Arm everything at once and the warnings arrive faster than you can
triage them. Within an hour you are dismissing them without reading,
which means you now have no detection at all — while still paying the
compute for it.

This is the human half of the base-rate problem, and it is the reason
that entry is worth the arithmetic. A detector producing nine false
alarms for every real one does not merely waste time; it teaches its
operator that alerts are noise. That lesson is learned quickly and
unlearned slowly, and it is learned by a person who is entirely rational
to learn it.

The consequence for how you defend a rig: **a defence you will not
respond to is worse than no defence**, because it costs compute and
produces false confidence. Choosing fewer, better-targeted defences is
not being cheap. It is the correct engineering decision, and `../design/09`
§3's compute budget is what forces you to make it.

## REAL-WORLD COUNTERPART

real, well-documented, and not confined to security. Clinical alarm
fatigue — hospital staff desensitised by constant device alarms — is a
recognised patient-safety hazard with regulatory attention behind it,
and it is the same phenomenon with the same cause.

In security the canonical illustration is a large 2013 retail breach in
which the intrusion *was* detected and alerts *were* generated, and the
alerts were not acted upon among the volume of others. The technology
worked. The system around it did not, and the distinction is the whole
lesson.

The practical countermeasure is unglamorous: fewer alerts, tuned harder,
each with a defined action attached. An alert with no decided response is
not a warning, it is a note.
```

---

### 3.8 `defence-in-depth(7)`

```
id:             defence-in-depth
section:        7
name:           defence in depth
canonical:      defence in depth
gloss:          Layers, on the assumption each one will eventually fail.
status:         real
aliases:        defense in depth, layered defence, layered security
seeAlso:        detection-cost(7), security-theatre(7), tarpit(7),
                least-privilege(7), threat-model(7), attack-surface(7)
reading:        NSA "Defense in Depth" guidance; NIST SP 800-53
                (control families as layers); Reason's "Swiss cheese"
                model of accident causation (1990)
notes:          British and American spellings both occur in the
                literature; the id uses the document set's spelling and
                `aliases` carries the other so apropos resolves both.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          operating
prerequisites:  threat-model(7)
hook:           `../design/09-defense-and-hardening.md` §3's arithmetic —
                a paranoid loadout of Firewall T3, Tarpit, Honeypot
                Stash, Auto-Counter Daemon and Detection Array costs more
                compute than most rigs have.
misconception:  commonly believed layering means stacking similar
                protections so that a stronger total is achieved;
                actually the point is that the layers must fail
                *differently*. Three defences that all fail to the same
                technique are one defence billed three times, which is
                exactly the shape of most expensive security postures.
transfer:       Look at how your own accounts are actually protected: a
                password, a second factor, and a recovery path. Then ask
                which single event defeats more than one of them — a
                compromised phone often defeats two — and you have found
                the layer that was not independent. Runs anywhere.
verified:       Independence of layers as the operative property, and
                the accident-model framing — Reason (1990) and NSA
                defense-in-depth guidance; the game's permanent-cost
                arithmetic and its stated tension —
                ../design/09-defense-and-hardening.md §3.
                Checked 2026-07-25.

## DESCRIPTION

You cannot afford every defence. `../design/09` §3 makes that arithmetic
explicit: Firewall at tier 3, a Tarpit, a Honeypot Stash, an Auto-Counter
Daemon and a Detection Array together reserve more compute than a rig
has, permanently. Something has to be left off.

The useful question is therefore not "which defences are good" but
"which failures am I covering, and do these layers fail *independently*?"
Layering only buys anything when the thing that defeats one layer does
not also defeat the next. A firewall and a tarpit are independent: one
refuses connections, the other slows whatever gets through. A firewall
and a second firewall are not.

The assumption underneath is the mature one, and it is worth adopting
early: **each layer will eventually fail.** Not might — will. Defences
are not designed to be impassable, they are designed so that passing one
costs time and leaves evidence, which is what turns a breach into an
incident somebody catches.

## REAL-WORLD COUNTERPART

real, and one of the oldest ideas in the field — the term is borrowed
from military doctrine and formalised in NSA and NIST guidance, where
control families are explicitly meant to overlap.

The clearest way to think about it comes from outside security entirely:
James Reason's "Swiss cheese" model of accident causation, in which every
safeguard has holes and disaster requires the holes to line up. That
model makes the independence requirement obvious in a way security
diagrams usually do not — layers cut from the same cheese have holes in
the same places.

The common real-world failure is buying layers from one vendor
implementing one philosophy, then discovering during an incident that a
single technique walked through all of them.
```

---

### 3.9 `audit-trail(7)`

```
id:             audit-trail
section:        7
name:           audit trail
canonical:      audit trail
gloss:          The record of who did what, kept for when you must reconstruct it.
status:         real
aliases:        audit log, accounting record, auditing
seeAlso:        log-integrity(7), anti-forensics(7), chain-of-custody(7),
                intrusion-detection(7), log(7), privilege(7)
reading:        auditd(8), auditctl(8), aureport(8); the BSM audit
                framework on macOS and BSD; wtmp(5), utmp(5),
                journalctl(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  log(7), privilege(7)
hook:           The `audit` window's name, which is not decorative — it
                is three views of the machine kept precisely so they can
                be compared afterwards.
misconception:  commonly believed a log and an audit trail are the same
                thing; actually they answer different questions and are
                built differently. A log is for the operator — what
                happened, so you can debug it. An audit trail is for the
                *investigator* — who did what, when, and under whose
                authority, recorded on the assumption that somebody will
                later dispute it.
transfer:       On macOS or Linux, `last` reads the login record and `who`
                reads the current one — both from files the system
                maintains for exactly this purpose. On Linux with auditd,
                `sudo aureport --summary` reports what it has been
                keeping. You did not turn any of this on. Assumes a Unix
                shell — see ED-8.
verified:       `last` and `who` present at /usr/bin on macOS
                (Darwin 25.5), confirmed by `command -v`; `auditd`
                present at /usr/sbin/auditd; the operator-log versus
                investigator-trail distinction — auditd(8) and BSM
                documentation; the game's audit surfaces —
                ../design/04-mining.md §3.1. Checked 2026-07-25.

## DESCRIPTION

The `audit` window is not called that by accident. It keeps three views
of your rig — processes, connections, storage — for the specific purpose
of being compared later, by you, when something does not look right.

An audit trail differs from an ordinary log in what it is *for*, and that
changes how it is built. A log serves the operator: it records what the
system did so you can work out why something broke, and it is fine for it
to be chatty, incomplete and rotated away after a week. An audit trail
serves an investigator: it records **who** did **what**, **when**, and
**under whose authority**, on the assumption that somebody will later
dispute the answer.

That assumption drives three requirements a normal log does not have. It
must record the actor and not just the action. It must be complete over
the period it covers, because a gap is itself a finding. And it must be
hard for the actor to edit — which is `log-integrity(7)`, and is the
hardest of the three.

## REAL-WORLD COUNTERPART

real, and already running on your machine. Unix systems have kept login
accounting in `wtmp` and `utmp` for decades — that is what `last` and
`who` read. Linux systems commonly run `auditd`, configured with
`auditctl` and reported by `aureport`; macOS and the BSDs ship the BSM
audit framework, which is why `auditd` exists there too.

Auditing is also where security meets law and regulation rather than
engineering: financial, medical and government systems are *required* to
keep trails of a specified completeness, and the requirement usually
predates any specific technology. That is worth knowing because it
explains why audit subsystems are often older, stranger and more rigid
than the rest of a system — they were built to satisfy a rule, not a
sysadmin.
```

---

### 3.10 `integrity-monitoring(7)`

```
id:             integrity-monitoring
section:        7
name:           integrity monitoring
canonical:      integrity monitoring
gloss:          Noticing that something changed which had no business changing.
status:         real
aliases:        FIM, file integrity monitoring, tripwire
seeAlso:        baseline(7), hash(7), intrusion-detection(7),
                cross-view-detection(7), timestomping(7), rootkit(7)
reading:        AIDE, Tripwire and Samhain documentation; Kim & Spafford,
                "The Design and Implementation of Tripwire", 1994;
                dpkg --verify, rpm -Va
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  hash(7), filesystem(7)
hook:           The storage table's **delta since last audit** — the
                game's built-in "what changed while I was not looking",
                and the first place a hidden miner's footprint shows up.
misconception:  commonly believed you detect tampering by looking at
                whether a file seems modified — its timestamp, its size;
                actually those are attacker-controlled and cheap to
                forge. Integrity monitoring works by hashing content and
                comparing against a record taken earlier, which is only
                as trustworthy as that earlier record.
transfer:       On a Debian or Ubuntu machine, `sudo dpkg --verify`
                compares every installed file against the package's
                recorded checksums and prints only what differs; on
                Red Hat family systems it is `rpm -Va`. Both are file
                integrity monitoring that was already installed. Assumes
                a Unix shell — see ED-8.
verified:       Hash-comparison-against-baseline as the mechanism, and
                the requirement that the baseline be stored out of the
                monitored system's reach — Kim & Spafford (1994) and
                AIDE/Samhain documentation; `dpkg --verify` and `rpm -Va`
                as shipped integrity checks — dpkg(1), rpm(8); the
                delta-since-last-audit surface —
                ../design/04-mining.md §3.1. Checked 2026-07-25.

## DESCRIPTION

The storage table shows what changed since your last audit. That single
column is the game's integrity monitoring, and it is the thing a hidden
miner is worst at hiding from.

The mechanism is simple and the difficulty is entirely in one place.
Hash everything you care about, store the hashes, and later re-hash and
compare. Anything whose hash changed, changed — regardless of what its
timestamp claims, because a hash covers content and content is what you
actually care about.

The difficulty is the stored hashes. If they live on the machine being
monitored, an intruder with enough access simply updates them after
making their change, and your integrity check now cheerfully confirms
that everything is as it should be. The record has to live somewhere the
attacker cannot reach — a different machine, read-only media, or signed
by a key kept elsewhere — and that requirement, not the hashing, is what
makes real deployments hard.

The same reasoning is why `cross-view-detection(7)` exists: when you
cannot trust the machine's answers, ask two different parts of it and
compare.

## REAL-WORLD COUNTERPART

real, and mature. Tripwire (1994) established the pattern and AIDE and
Samhain continue it; the design paper is still worth reading precisely
because it is honest about the baseline-storage problem rather than
waving at it.

You almost certainly have a form of it already. Debian's
`dpkg --verify` and Red Hat's `rpm -Va` check installed files against
checksums the package manager recorded at install time — an integrity
baseline that arrived for free with the operating system, and one very
few people ever run.

The limitation to keep: integrity monitoring tells you something changed.
It does not tell you whether the change was legitimate, and on a busy
machine most changes are. Without a good baseline it produces exactly
the flood `alert-fatigue(7)` describes.
```

---

### 3.11 `log-integrity(7)`

```
id:             log-integrity
section:        7
name:           log integrity
canonical:      log integrity
gloss:          Keeping a record the intruder cannot quietly rewrite.
status:         real
aliases:        tamper-evident logging, append-only logging, remote logging
seeAlso:        audit-trail(7), anti-forensics(7), append-only-log(7),
                log-scrubber(1), tamper-evidence(7), hash(7)
reading:        chattr(1) and the ext4 append-only attribute;
                rsyslog and syslog-ng remote forwarding documentation;
                RFC 5424 (syslog); RFC 6962 (Certificate Transparency)
notes:          ⚠ This page carries the defender's answer that
                ../client/04 §2.7 makes **mandatory** on log-scrubber(1).
                It may be shortened but that content may not be removed.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  audit-trail(7), append-only-log(7)
hook:           Owning a Log Scrubber and realising the same tool works
                against you — the moment the question flips from "how do
                I erase this" to "how would I have stopped someone".
misconception:  commonly believed that if logs are on your machine and
                your machine is secure, the logs are safe; actually an
                intruder who reached your machine has reached your logs,
                and the first thing capable intruders do is edit the
                record of their arrival. A log is only trustworthy if it
                is somewhere the intruder did not get to.
transfer:       On a Linux machine, `sudo chattr +a somefile` makes a
                file append-only — you can add to it, and even root
                cannot truncate or rewrite it without first removing the
                attribute, which is itself a logged action. Verify with
                `lsattr`. ⚠ Linux-only: `chattr` does not exist on macOS,
                whose equivalent is the `uappnd`/`sappnd` flags via
                `chflags`. Assumes a Unix shell — see ED-8.
verified:       `chattr` and `lsattr` confirmed **absent** on macOS
                (Darwin 25.5) via `command -v`, so the Linux-only caveat
                is tested rather than assumed; append-only attribute
                semantics — chattr(1); remote forwarding as the primary
                real defence — rsyslog and syslog-ng documentation;
                the game's Log Scrubber and the mandatory defender's
                answer — ../design/08-stealth-and-noise.md §2 and
                ../client/04-terminology-and-education.md §2.7.
                Checked 2026-07-25.

## DESCRIPTION

A Log Scrubber erases traces of what you did on a machine you broke
into. Which means somebody with a Log Scrubber can erase traces of what
they did on *yours*.

That symmetry is the point of this page, and the answer is not a better
lock on the log file. Once an intruder is on the machine with enough
privilege, every file on it is theirs to edit, including the one
recording their arrival. Defending the log by defending the machine is
circular.

There are exactly three things that actually work, and all of them move
the record out of the attacker's reach:

- **Send it somewhere else, immediately.** A log forwarded to another
  machine as it is written is a log the intruder must compromise a second
  machine to alter. This is the single most effective measure and it is
  ordinary infrastructure, not a product.
- **Make it append-only.** A file that can be added to but not rewritten
  means erasure requires first removing that restriction — which is
  itself an event worth alarming on.
- **Chain it.** If each entry commits to the hash of the one before,
  deletion stops being silent and becomes a visible gap. See
  `append-only-log(7)`; this is the same machinery the ledger uses.

None of the three prevents tampering. All three convert it from something
invisible into something noisy, which is the whole bargain of this domain.

## REAL-WORLD COUNTERPART

real, and this is standard practice rather than an exotic control.
Forwarding logs off the host with `rsyslog` or `syslog-ng` is what
centralised logging *is*, and its security value — not merely its
convenience — is why it is a compliance requirement in most regulated
environments.

Append-only files are a real filesystem capability: on Linux,
`chattr +a` sets an attribute that even root must explicitly clear before
a file can be rewritten. macOS and the BSDs have the equivalent through
`chflags` with the append-only flags.

Hash-chained logging is the same idea as Certificate Transparency
(RFC 6962), applied locally: you cannot stop the operator editing the
log, so you make the edit detectable by anyone holding an earlier copy.
```

---

### 3.12 `indicator-of-compromise(7)`

```
id:             indicator-of-compromise
section:        7
name:           indicator of compromise
canonical:      indicator of compromise
gloss:          A specific observable that means somebody has been here.
status:         real
aliases:        IoC, indicator, artefact
seeAlso:        intrusion-detection(7), attribution(7), honeytoken(7),
                anti-forensics(7), cross-view-detection(7),
                signature-detection(7)
reading:        MITRE ATT&CK; the STIX indicator specification;
                Bianco's "Pyramid of Pain" (2013)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  intrusion-detection(7)
hook:           A connection in the `audit` window with no owning
                process — the game's cleanest single indicator, and the
                one `../design/04-mining.md` §3.1 is built around.
misconception:  commonly believed indicators are conclusive, so finding
                one means you have found an intrusion; actually an
                indicator is evidence with a false-positive rate, and
                different indicators are worth wildly different amounts.
                A file hash is trivially changed by the attacker; a
                behaviour is not.
transfer:       On your own machine, run `ss -tunap` on Linux or
                `lsof -i` on macOS and look for a connection you cannot
                account for. Most of what you find will be legitimate
                software you forgot about — which is the lesson: knowing
                your own baseline is what makes an indicator mean
                anything. Assumes a Unix shell — see ED-8.
verified:       Indicator types and their varying durability — Bianco's
                "Pyramid of Pain" (2013), which ranks hashes as trivially
                changed and TTPs as costly; ATT&CK as the standard
                catalogue of behaviours; the ownerless-connection
                indicator — ../design/04-mining.md §3.1 and
                ../education/03-operating-systems.md §3.20.
                Checked 2026-07-25.

## DESCRIPTION

A connection with no owning process. A file that changed when nothing
should have changed it. A canary that was touched. Each is an
**indicator** — a specific, observable thing whose presence suggests
somebody has been here.

Indicators are not equal, and the difference is how expensive they are
for the intruder to avoid. At the cheap end sit exact values: a specific
file hash, a specific address. An attacker changes those by recompiling
or moving, so a defence built on them expires almost immediately. At the
expensive end sit **behaviours** — a miner has to consume compute and has
to talk to its deployer, and no amount of recompiling removes either.
Detect the behaviour and the attacker has to change how they operate,
which is genuinely costly.

That ranking explains why this game's best detection is behavioural. The
ownerless connection is not a signature of any particular tool; it is a
consequence of what hiding a process *requires*, and that is why it works
against tools nobody has seen before.

An indicator is evidence, not proof — see `base-rate-fallacy(7)` before
acting on a single one.

## REAL-WORLD COUNTERPART

real, and central to how threat intelligence is shared: STIX exists to
exchange indicators in a machine-readable form, and MITRE ATT&CK
catalogues the behavioural end as techniques rather than values.

The framing worth carrying is David Bianco's "Pyramid of Pain" (2013),
which ranks indicator types by how much it hurts the attacker when you
detect them. Hashes: trivial to change. Addresses: easy. Tools: annoying.
Tactics and procedures: genuinely expensive, because changing them means
changing how the operation works.

Most commercial detection sells the bottom of that pyramid, because it is
easy to package. The top is where the value is, and it requires knowing
what normal looks like on your own systems — which nobody can sell you.
```

---

### 3.13 `anti-forensics(7)`

```
id:             anti-forensics
section:        7
name:           anti-forensics
canonical:      anti-forensics
gloss:          Removing the traces an intrusion left, rather than the intrusion.
status:         real
aliases:        indicator removal, log cleaning, trace removal
seeAlso:        log-integrity(7), timestomping(7), audit-trail(7),
                forensic-artefact(7), log-scrubber(1), noise(7)
reading:        MITRE ATT&CK **T1070** "Indicator Removal";
                journalctl(1) --vacuum-time; wtmp(5), utmp(5)
notes:          ⚠ Dual-use boundary (00 §7, CT-5). This page states what
                survives erasure and why — the defender's advantage — and
                names no tool, sequence or technique for performing it.
                A revision that adds one has crossed the line.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  audit-trail(7), indicator-of-compromise(7)
hook:           Running a Log Scrubber after an operation, and the
                question of whether it actually worked.
misconception:  commonly believed that erasing logs erases the evidence,
                so a clean log means a clean escape; actually a system
                records the same event in many places, most of them not
                obvious, and erasure is itself an event. Investigators
                routinely treat a *gap* as a stronger signal than
                anything the missing entries would have contained.
transfer:       On any machine, compare `last` (login history) with what
                your shell's own history file contains, then with what
                your browser recorded, then with file modification times
                in your home directory. Four independent records of the
                same afternoon, none of which knows about the others.
                That redundancy is the defender's actual advantage.
                Assumes a Unix shell — see ED-8.
verified:       Indicator removal as a catalogued adversary technique —
                MITRE ATT&CK T1070; multiple independent record locations
                for one event — wtmp(5), utmp(5), shell history and
                filesystem timestamps, all confirmed present on macOS
                (Darwin 25.5); log erasure leaving a detectable gap —
                journalctl(1) and standard forensic practice; the game's
                Log Scrubber — ../design/08-stealth-and-noise.md §2.
                Checked 2026-07-25.

## DESCRIPTION

Anti-forensics is not about hiding during an operation — that is noise
and stealth. It is about what you leave behind afterwards, and whether
somebody reconstructing the afternoon can tell you were there.

The Log Scrubber cleans your traces. The honest question this page exists
to answer is: **how well does that actually work?** Less well than it
feels, for a structural reason worth understanding.

A system does not record an event once. It records fragments of it in
many places that were never designed to corroborate each other — login
accounting, the shell's own history, filesystem timestamps, the
scheduler's records, whatever the application wrote, and whatever was
forwarded off the machine before anybody touched anything. Erasing one of
those is easy. Erasing all of them requires knowing all of them, on a
system you did not build.

And erasure is itself an event. A log that runs continuously and then has
a gap has told the investigator two things: that something happened, and
roughly when. That is frequently more useful than the deleted entries
would have been, because it converts an unbounded search into a bounded
one.

This is why `log-integrity(7)`'s defences are worth the trouble. None of
them prevents erasure. All of them make the gap louder.

## REAL-WORLD COUNTERPART

real, and catalogued: MITRE ATT&CK tracks it as **T1070, Indicator
Removal**, with sub-techniques for clearing specific record types —
which tells you both that it is a routine part of real intrusions and
that defenders have enumerated it thoroughly enough to detect it.

The defender's advantage is redundancy, and it is larger than most
attackers assume. Logs forwarded off the host before anyone arrived are
outside the attacker's reach entirely. Backups predate them. And modern
systems record far more, in more places, than the obvious log directory.

⚠ This page describes what survives and why. It deliberately does not
describe how removal is performed — that is the boundary in `00` §7 and
`06` **CT-5**, and it is drawn here rather than left implied.
```

---

### 3.14 `timestomping(7)`

```
id:             timestomping
section:        7
name:           timestomping
canonical:      timestomping
gloss:          Backdating a file so it stops looking recently touched.
status:         real
aliases:        timestomp, timestamp manipulation, MAC time alteration
seeAlso:        anti-forensics(7), forensic-artefact(7), inode(7),
                integrity-monitoring(7), audit-trail(7)
reading:        MITRE ATT&CK **T1070.006** "Timestomp"; stat(1);
                inode(7); ext4 and APFS timestamp documentation
notes:          ⚠ Dual-use boundary. This page exists because the
                *counter* is simple and worth knowing; it names no tool.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  anti-forensics(7), inode(7)
hook:           Sorting the storage table by modification time to find
                what changed recently — and the question of whether that
                column can be trusted.
misconception:  commonly believed a file has one timestamp, so setting it
                back makes the file look old; actually a Unix file has
                several — when its contents changed, when it was last
                read, and when its *inode* last changed — and the last of
                those cannot be set directly by the ordinary interface.
                A file whose content time is older than its inode time
                is announcing that somebody adjusted it.
transfer:       Run `stat` on any file — `stat -x` on macOS, `stat` on
                Linux — and read the three or four separate times it
                reports. Then `touch -t 202001010000 somefile` and run
                `stat` again: the modification time obeys you and the
                inode change time updates to *now*, because you just
                changed the inode. That discrepancy is the whole
                detection. Assumes a Unix shell — see ED-8.
verified:       Timestomping as a catalogued sub-technique — MITRE
                ATT&CK T1070.006; the ctime-cannot-be-set-directly
                property and the resulting mtime/ctime inconsistency —
                stat(1), inode(7), and standard filesystem forensic
                practice; `stat -x` as the macOS spelling — stat(1) on
                Darwin 25.5. Checked 2026-07-25.

## DESCRIPTION

Sort the storage table by "last modified" and you find what changed
recently. Which raises the obvious question: can that column be trusted?

Not entirely, and the way it fails is instructive. A Unix file does not
have *a* timestamp. It has several: when its contents were last modified
(**mtime**), when it was last read (**atime**), and when the file's
record itself last changed (**ctime**). Ordinary tools show you the
first.

The first two can be set to anything by whoever owns the file — that is
what `touch` is for, and it is a legitimate feature. The third cannot be
set directly through the normal interface, because it updates *as a
consequence* of the record changing. So the act of backdating a file
updates the timestamp you cannot backdate.

The result is a file claiming it was last modified in 2020 while its
record says it changed ten minutes ago — an inconsistency that means
something specific rather than something suspicious. That is a much
stronger position than "this file looks odd".

## REAL-WORLD COUNTERPART

real, catalogued as MITRE ATT&CK **T1070.006**, and old enough to be a
routine part of forensic training rather than an exotic finding.

The counter generalises past this specific case, and is the reason the
page is worth writing: **look for internal inconsistency rather than for
suspicious values.** An attacker can control what a system reports about
one thing. Controlling what several independent parts of a system report,
consistently, is much harder — which is the same argument
`cross-view-detection(7)` makes about processes and this page makes about
timestamps.

Filesystems differ in what they record and at what resolution, and modern
ones often keep a creation time as well. More independent records is more
opportunity for one of them to disagree.
```

---

### 3.15 `honeytoken(7)`

```
id:             honeytoken
section:        7
name:           honeytoken
canonical:      honeytoken
gloss:          A thing with no purpose but to tell you somebody touched it.
status:         real
aliases:        canary token, honeyfile, canarytoken, decoy credential
seeAlso:        honeypot(7), indicator-of-compromise(7), attribution(7),
                deception-technology(7), false-positive(7), canary(8)
reading:        Thinkst Canarytokens (canarytokens.org); Spitzner,
                "Honeytokens: The Other Honeypot" (2003); Stoll,
                "The Cuckoo's Egg" (1989)
notes:          ⚠ **§2.15 homonym — mandatory.** "Canary" means two
                unrelated things a player will meet: this decoy, and the
                unrelated release-engineering sense (a canary build or
                canary deployment — a small early rollout that detects
                problems before a full one). Neither is a metaphor for
                the other and the page must not blur them. Both descend
                from the mining canary, which is the only thing they
                share.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  indicator-of-compromise(7)
hook:           The Canary Token — 8 EC, one compute, and it both alerts
                you and tags the toucher's handle
                (`../design/09-defense-and-hardening.md` §2).
misconception:  commonly believed a decoy has to be convincing to be
                worth anything, so building one is expensive; actually it
                only has to be *touchable*, and its value comes from a
                different property entirely — it has **no legitimate
                use**, so any interaction with it is a real alert. It is
                the rare detector with a false positive rate of
                approximately zero.
transfer:       Create a free token at canarytokens.org — a document, a
                DNS name, an AWS key — put it somewhere private, and
                forget it. If it ever fires, something is wrong, and you
                will have found out for nothing. This is genuinely one of
                the highest value-per-effort things in personal security.
                Runs anywhere; needs no shell.
verified:       Honeytokens as a named technique — Spitzner (2003);
                Canarytokens as a free, current, hosted implementation
                offering document, DNS and cloud-credential tokens —
                Thinkst Canarytokens documentation; the near-zero false
                positive property following from having no legitimate
                use; the game's cost and handle-tagging —
                ../design/09-defense-and-hardening.md §1-§2 and the
                evidence path in ../design/12-identity-and-social.md.
                Checked 2026-07-25.

## DESCRIPTION

A Canary Token is a file that does nothing. It holds no data you need, it
serves no purpose, and nothing legitimate ever opens it. That is the
entire design.

Because it has no legitimate use, **any** interaction with it is a real
signal. Compare that with every other detector in this domain, which must
somehow distinguish suspicious activity from the enormous volume of
normal activity and mostly fails at it (`base-rate-fallacy(7)`). A
honeytoken sidesteps the problem rather than solving it: there is no
normal activity to be confused by.

This makes it, per unit of effort, the best detection in the game and
arguably the best in real life. It costs 8 EC and one compute. It
requires no tuning, produces no routine output, and cannot suffer alert
fatigue because it is silent until it matters.

The game's version also tags the toucher's handle, which feeds the
evidence path in `../design/12-identity-and-social.md`. Be careful with
what that tag means — see `attribution(7)`, because an identity in a log
is a claim about who was there, not a proof.

## REAL-WORLD COUNTERPART

real, and you can deploy one this afternoon for free. Thinkst's
Canarytokens will generate a document, a DNS name, a URL, or a cloud
credential that emails you if anything ever touches it. Organisations
scatter them through file shares and codebases on the theory that an
intruder browsing for valuables will eventually open one.

The idea is older than the tooling and older than the term. Cliff Stoll's
1986 investigation ended with fake documents planted specifically to
occupy an intruder long enough to trace the connection — a honeytoken,
before anybody called it that.

## CAVEATS

⚠ **"Canary" means two unrelated things in computing** and a player will
meet both. This page is the decoy sense. The other is release
engineering: a *canary build* or *canary deployment* is a small early
rollout to a fraction of users, so that problems surface before everyone
gets them. There is no relationship between the two beyond a shared
borrowing from the mining canary, and treating one as a metaphor for the
other will mislead you in both directions.
```

---

### 3.16 `honeypot(7)`

```
id:             honeypot
section:        7
name:           honeypot
canonical:      honeypot
gloss:          A whole machine or store existing only to be broken into.
status:         real
aliases:        decoy system, deception host
seeAlso:        honeytoken(7), deception-technology(7), tarpit(7),
                intrusion-detection(7), attribution(7)
reading:        Spitzner, "Honeypots: Tracking Hackers" (2002); the
                Honeynet Project; Stoll, "The Cuckoo's Egg" (1989);
                Cowrie and Dionaea documentation
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  honeytoken(7)
hook:           The Honeypot Stash — a decoy high-hackable zone full of
                junk, which a raider cannot distinguish from the real
                thing until extraction.
misconception:  commonly believed a honeypot is a trap that catches or
                punishes an intruder; actually it catches nothing. It is
                an *observation* instrument: its value is that everything
                happening on it is unambiguously hostile, so you can
                watch an intruder work without the noise of legitimate
                activity — and, in a game, waste their time.
transfer:       Search for a public honeypot report or run Cowrie (an SSH
                honeypot) on a spare machine and read its logs after a
                day. Anything exposed to the internet is probed
                constantly by automation, and seeing the volume for
                yourself recalibrates what "the internet" is in a way no
                statistic does. ⚠ Only on infrastructure you own —
                see CAVEATS.
verified:       Honeypots as observation instruments rather than traps,
                and the high-versus-low-interaction distinction —
                Spitzner (2002) and Honeynet Project material; Cowrie and
                Dionaea as current low-interaction implementations; the
                game's decoy-until-extraction design —
                ../design/09-defense-and-hardening.md §2.
                Checked 2026-07-25.

## DESCRIPTION

The Honeypot Stash is a whole storage zone containing nothing worth
having, which a raider cannot tell from a real one until they have paid
the cost of extracting from it.

That is the real design goal, stated exactly. A honeypot is not a trap in
the sense of catching anybody — it has no teeth. Its value comes from the
same property as a honeytoken, scaled up: nothing legitimate happens
there, so **everything** that happens there is worth looking at. You get
to watch an intruder operate, at length, without having to separate their
activity from anyone else's.

The secondary value, which is the one the game leans on, is cost
imposition. Time spent on your decoy is time not spent on your actual
storage, and in a game with a bot-backlog timer that is a defence in
itself.

Real deployments split by how convincing the decoy is. A **low-interaction**
honeypot simulates just enough of a service to record what is attempted —
cheap, safe, and it fools automation but not a person. A
**high-interaction** one is a real system, which produces far better
observation and creates a genuine problem, because you have deliberately
put an exploitable machine on your network.

## REAL-WORLD COUNTERPART

real, with a long literature. Lance Spitzner's *Honeypots: Tracking
Hackers* (2002) and the Honeynet Project's work established the practice;
Cowrie (SSH) and Dionaea (malware collection) are current, free,
low-interaction implementations.

The founding story is the same one this domain opened with. Cliff Stoll's
1986 investigation eventually turned on fabricated documents planted to
hold an intruder's attention long enough to complete a trace — a
high-interaction deception, run by an astronomer, before any of this had
a name.

## CAVEATS

- **A high-interaction honeypot is a real vulnerable machine that you
  put there on purpose.** If it is compromised and used to attack a third
  party, that is your machine attacking them. Isolate it properly or do
  not run one.
- **Only on infrastructure you own.** The transfer test above means a
  machine you control; running a honeypot on someone else's
  infrastructure is not a research exercise, it is unauthorised access —
  see `hack-back(7)`.
- **Automation finds everything.** Anything exposed will be probed within
  hours, which makes honeypots productive and also means "nobody knows it
  is there" is never a defence.
```

---

### 3.17 `tarpit(7)`

```
id:             tarpit
section:        7
name:           tarpit
canonical:      tarpit
gloss:          Not stopping an intruder, but making everything they do slow.
status:         real
aliases:        tarpitting, sticky honeypot, LaBrea
seeAlso:        honeypot(7), defence-in-depth(7), incident-response(7),
                tcp(7), rtt(7), detection-cost(7)
reading:        LaBrea tarpit documentation; endlessh; the TARPIT target
                in xtables-addons; SMTP tarpitting practice
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          investigating
prerequisites:  tcp(7), defence-in-depth(7)
hook:           The Tarpit — 70 EC, 8 permanent compute, and it stops
                nobody. It buys seconds, and the bot-backlog timer is
                what makes seconds worth 70 EC.
misconception:  commonly believed a defence that does not stop the attack
                has failed; actually delay is a defence in its own right,
                because responses have deadlines. Everything an intruder
                does has a time budget, and so does everyone defending —
                so seconds bought are not a consolation prize, they are
                the resource the whole response is made of.
transfer:       Look at how login forms handle repeated failures: almost
                all of them now add a delay that grows with each attempt.
                That is a tarpit, deployed against you at some point,
                and it works — not by refusing the guess, but by making
                the millionth guess take a week. Runs anywhere.
verified:       LaBrea as the original TCP tarpit, endlessh as an SSH
                tarpit that drips a banner indefinitely, and the TARPIT
                target in xtables-addons — their respective
                documentation; SMTP tarpitting as long-standing
                anti-spam practice; the game's cost and its pairing with
                the backlog timer — ../design/09-defense-and-hardening.md
                §2 and ../design/10 §1. Checked 2026-07-25.

## DESCRIPTION

The Tarpit does not stop intruders. It slows every action they take,
which sounds like a consolation prize and is not.

The reason is that defending has a clock. `../design/10` §1's bot-backlog
timer gives you a shrinking window to respond to each event, and that
window shrinks further with every bot you are running. A Tarpit does not
change what an intruder can do; it changes how much of your window is
left when you notice — which is the difference between triaging the right
thing and triaging whatever you happened to see first.

The general principle is worth extracting: **an attack that must complete
within a time budget can be defeated by spending the budget.** Nothing
was blocked. The operation simply ran out of time.

This is also the honest reading of a great deal of real security. Rate
limits, exponential backoff on failed logins, and deliberately slow
password hashing all work this way — none prevents the attempt, all make
the *volume* of attempts uneconomic.

## REAL-WORLD COUNTERPART

real, and an exact match including the philosophy — which is unusual
enough to be worth saying, because most game defences are simplified and
this one is not.

LaBrea, written in 2001, answered connection attempts to unused addresses
and then held them open indefinitely, so that scanning tools trying to
map a network got stuck on machines that did not exist. `endlessh` does
the same to SSH scanners by dripping out an endless banner, exploiting
the fact that the protocol says a client must wait for it. The TARPIT
target in `xtables-addons` provides the mechanism at the firewall layer,
and mail servers have tarpitted suspected spam senders for decades.

You have almost certainly been tarpitted yourself, by a login form that
made you wait a little longer after each wrong password.
```

---

### 3.18 `attribution(7)`

```
id:             attribution
section:        7
name:           attribution
canonical:      attribution
gloss:          Working out who actually did it, which is harder than it sounds.
status:         real
aliases:        identification, ascription
seeAlso:        hack-back(7), indicator-of-compromise(7), honeytoken(7),
                ip-address(7), onion-routing(7), base-rate-fallacy(7),
                identity-spoofer(1)
reading:        Rid & Buchanan, "Attributing Cyber Attacks", Journal of
                Strategic Studies 38(1-2), 2015; ATT&CK on
                infrastructure reuse; false-flag literature
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  indicator-of-compromise(7), ip-address(7)
hook:           The Canary Token tags the toucher's handle — and the
                Identity Spoofer exists specifically to make that tag
                wrong. Two tools the game sells, pointed at each other.
misconception:  commonly believed an address or an identity in a log
                identifies who was responsible; actually it identifies
                the last hop that touched you, which may be a relay, a
                compromised third party, or a deliberate impersonation.
                Attribution is an argument built from many weak pieces of
                evidence, not a lookup — and the confident version you
                see reported is usually the end of a long process, not
                the start of one.
transfer:       Look at any failed-login record on a machine you own —
                `last -f /var/log/btmp` on Linux, or Console on macOS.
                The addresses are real and almost none of them identify
                anyone: they are compromised machines, hosting providers
                and relays. That gap between "an address" and "a person"
                is the entire subject. Assumes a Unix shell — see ED-8.
verified:       Attribution as an evidentiary process combining technical,
                operational and contextual indicators rather than a
                technical lookup — Rid & Buchanan (2015); relay chains
                breaking address-based attribution, and the game's
                Identity Spoofer being explicitly this —
                ../client/04-terminology-and-education.md §2.7 and
                ../design/08-stealth-and-noise.md §2; the canary
                handle-tag feeding the evidence path —
                ../design/12-identity-and-social.md. Checked 2026-07-25.

## DESCRIPTION

Your Canary Token fires and hands you a handle. You now know who raided
you — except that the Identity Spoofer exists, and the Relay Chain
exists, and the game sells both to the same people it sells canaries to.

That tension is not a design flaw, it is the subject. An identifier in a
log tells you what the last hop presented, which is a different question
from who was responsible. A relay chain means the address you see belongs
to a relay. A spoofed identity means the handle you see was chosen. A
compromised machine means the party you are looking at is another victim.

Real attribution is therefore an **argument**, assembled from many pieces
that are individually weak: technical indicators, operational patterns,
timing, mistakes, and context that has nothing to do with computers. It
is closer to how a case is built than to how a lookup is performed, and
it takes time that a rig under attack does not have.

Which leads directly to the reason this entry is `hack-back(7)`'s
prerequisite. If you cannot be confident who did it, then retaliating
means acting against a party you have not identified — and the most
likely party at the other end of a relay is somebody else who was
compromised first.

## REAL-WORLD COUNTERPART

real, and genuinely hard — hard enough that it is studied as a problem of
evidence rather than a problem of engineering. Rid and Buchanan's 2015
treatment is the standard reference and argues attribution is a process
combining technical, operational and strategic reasoning, with confidence
levels rather than answers.

The recurring practical traps are worth naming: infrastructure is
frequently reused between unrelated actors, tools leak and get copied,
and false flags — deliberately planting another party's indicators — are
a documented technique rather than a thriller device.

Public attributions that sound certain are usually the published end of a
long, mostly non-technical process, often including sources that have
nothing to do with the network. The confident version is the summary, not
the method.
```

---

### 3.19 `hack-back(7)`

```
id:             hack-back
section:        7
name:           hacking back
canonical:      hacking back
gloss:          Retaliating against an attacker's machine, and why you may not.
status:         real
aliases:        hack back, active defence, offensive countermeasures,
                retaliation
seeAlso:        attribution(7), automated-response(7),
                auto-counter-daemon(8), incident-response(7),
                responsible-disclosure(7), honeypot(7)
reading:        18 U.S.C. §1030 (Computer Fraud and Abuse Act);
                Computer Misuse Act 1990 (UK); Budapest Convention on
                Cybercrime (2001); the Active Cyber Defense Certainty
                Act (US, introduced 2017 and 2019 — never enacted)
notes:          ⚠ **MANDATORY CONTENT.** ../client/04 §2.8 requires that
                the auto-counter-daemon page state plainly that hacking
                back is illegal in most jurisdictions; that statement
                lives here and is cited from there. It may be reworded.
                It may not be softened, hedged into "may be illegal in
                some places", or removed. This is the one page in the
                game that tells a player not to do something.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  attribution(7)
hook:           The Auto-Counter Daemon, which fires back on your behalf
                while you are logged off — a schematic-gated tool that
                does, in the fiction, the exact thing this page says you
                may not do outside it.
misconception:  commonly believed that retaliating against a machine that
                attacked you is self-defence and therefore lawful;
                actually unauthorised access is defined by the *lack of
                authorisation*, not by motive. Being attacked first does
                not authorise you. There is no computer-crime equivalent
                of self-defence in most jurisdictions, and the attempts to
                create one have not passed.
transfer:       Read the first paragraph of the Computer Fraud and Abuse
                Act (18 U.S.C. §1030) or the UK's Computer Misuse Act
                1990 §1. Both criminalise unauthorised access as such,
                with no exception for provocation. This is a five-minute
                read and it is the most consequential thing in this
                document. Runs anywhere; needs no shell.
verified:       CFAA and CMA 1990 both defining the offence by absence of
                authorisation, with no retaliation exemption — the
                statutes' own text; the Active Cyber Defense Certainty
                Act introduced in the US Congress in 2017 and again in
                2019 and never enacted; the Budapest Convention as the
                multilateral instrument aligning many national laws;
                the mandatory-statement requirement —
                ../client/04-terminology-and-education.md §2.8.
                Checked 2026-07-25.

## DESCRIPTION

**Hacking back is illegal in most jurisdictions, including the United
States and the United Kingdom, and being attacked first does not change
that.**

That is the whole point of this page, stated first because it is the part
that matters. The Auto-Counter Daemon is a game mechanic. The thing it
does is a crime nearly everywhere it could be done.

The reason is structural rather than an oversight in the law. Computer
crime statutes define the offence as access *without authorisation*.
Authorisation is granted by the system's owner and by nobody else — so it
is not something an attacker forfeits by attacking you, because it was
never theirs to forfeit. There is no provocation defence, because the
element the law cares about is not your motive.

Two practical problems compound the legal one, and they are the reasons
practitioners oppose hacking back even where they wish it were lawful:

- **You probably have the wrong target.** See `attribution(7)`. The
  machine attacking you is very often a compromised third party — a
  small business, a home router, a hospital. Retaliating means attacking
  a victim.
- **It escalates against an opponent who has less to lose.** You have
  infrastructure, customers and a legal identity. They frequently do not.

What you may do instead is substantial and none of it requires touching
their machine: block, isolate, collect evidence, preserve logs, and
report. Deception — honeypots and honeytokens on **your own**
infrastructure — is lawful and effective, which is why this domain spends
three entries on it.

## REAL-WORLD COUNTERPART

real, and the legal position is settled rather than contested. In the
United States the Computer Fraud and Abuse Act (18 U.S.C. §1030)
criminalises unauthorised access with no retaliation exemption. In the
United Kingdom the Computer Misuse Act 1990 does the same. The Budapest
Convention aligns much of the rest.

The attempt to change this is instructive. The **Active Cyber Defense
Certainty Act** was introduced in the US Congress in 2017 and again in
2019, proposing a limited exemption for certain defensive measures
outside one's own network. It never passed, and the objections were
predominantly from security practitioners rather than from civil
libertarians — largely on the attribution and escalation grounds above.

The phrase "active defence" is worth watching, because it is used for
two quite different things: measures on your own systems, which are
ordinary and lawful, and measures on someone else's, which are not.

## CAVEATS

⚠ **The Auto-Counter Daemon does not model a real capability that is
merely restricted. It models something you must not do.** The game does
not draw a line in the wrong place; it draws no line at all, because in
the fiction the Eye is not going to prosecute you. The real world is
different in exactly this respect, and this page exists so nobody
confuses the two.

Nothing here is legal advice, and jurisdictions differ in detail. What
does not differ, anywhere the authors could find, is that being attacked
does not authorise you to access someone else's machine.
```

---

### 3.20 `automated-response(7)`

```
id:             automated-response
section:        7
name:           automated response
canonical:      automated response
gloss:          Letting software act on an alert without waiting for a person.
status:         real, simplified
aliases:        SOAR, active response, automated remediation
seeAlso:        auto-counter-daemon(8), false-positive(7), hack-back(7),
                alert-fatigue(7), intrusion-detection(7), containment(7)
reading:        fail2ban(1); OSSEC/Wazuh active-response documentation;
                vendor SOAR literature, read sceptically
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  false-positive(7), intrusion-detection(7)
hook:           The Auto-Counter Daemon acting while you are logged off —
                the first defence that makes decisions without you, and
                the first one that can be wrong without you.
misconception:  commonly believed automation removes the false-positive
                problem by handling alerts faster than a person could;
                actually it multiplies it. A false positive a human would
                have dismissed in two seconds becomes an *action* — a
                blocked customer, a killed process, a quarantined
                machine — and automation applies it at machine speed and
                machine scale.
transfer:       `fail2ban` on any internet-facing Linux machine is this:
                it watches auth logs and blocks addresses after repeated
                failures. Its documentation's warnings are the
                interesting part — it can and does lock out legitimate
                users, and the standard mitigation is an allow-list for
                your own address. That warning is the whole lesson.
                Assumes a Unix shell — see ED-8.
simplified:     Real automated response **blocks, isolates and
                quarantines** — always acting on systems the defender
                owns. The game's Auto-Counter Daemon attacks the
                attacker's machine, which real automated response does
                not do and, per hack-back(7), may not do. The mechanism
                is real; the direction it points is not.
verified:       fail2ban as log-driven automated blocking with documented
                lockout risk — fail2ban(1) and its own documentation;
                active response as block/isolate/quarantine rather than
                counter-attack — OSSEC/Wazuh active-response
                documentation; the game's offline-counter behaviour and
                its permanent 18 compute —
                ../design/09-defense-and-hardening.md §2.
                Checked 2026-07-25.

## DESCRIPTION

The Auto-Counter Daemon acts while you are logged off. That is its whole
value and its whole risk, and the two are the same property.

Automation is genuinely necessary — attacks proceed faster than people
respond, and nobody is awake at four in the morning. But every automated
response inherits its detector's false-positive rate and converts it into
consequences. An alert a person would have glanced at and dismissed
becomes, unattended, a blocked address or a killed process. At machine
speed, and repeatedly.

The way this is managed in practice is not clever, and is worth copying:
**automate the reversible, escalate the rest.** Blocking an address for
fifteen minutes is cheap to be wrong about. Wiping a machine is not.
Match the automated action's blast radius to your confidence in the
detection, and keep a way to undo it that does not require the thing that
just broke.

The game's version diverges from real practice in one specific and
important way, covered below and in `hack-back(7)`.

## REAL-WORLD COUNTERPART

real, simplified. Automated response is standard and mundane: `fail2ban`
watches authentication logs and blocks addresses that fail repeatedly;
OSSEC and Wazuh ship active-response scripts; the enterprise version is
sold as SOAR.

The universal caveat in all of their documentation is the same one this
page makes. `fail2ban` will eventually lock out a legitimate user — often
the administrator, from their own server, at an inconvenient moment —
which is why every guide tells you to allow-list your own address before
enabling it.

## CAVEATS

**Real automated response acts on systems you own.** It blocks, isolates
and quarantines. It does not counter-attack, and the reason is
`hack-back(7)`: doing so would be unauthorised access regardless of what
provoked it.

The Auto-Counter Daemon therefore models the *mechanism* accurately and
points it somewhere real automation does not go. Take from it the idea
that software can respond without you, and the discipline of matching
automated actions to confidence. Do not take from it the idea that
automatic retaliation is a product category.
```

---

### 3.21 `log-scrubber(1)`

```
id:             log-scrubber
section:        1
name:           log-scrubber
canonical:      Log Scrubber
gloss:          Erases the traces of what you did, imperfectly.
status:         real
aliases:        log cleaner, trace remover
glossary:       ../design/glossary.md
seeAlso:        anti-forensics(7), log-integrity(7), timestomping(7),
                audit-trail(7), noise(7), forensic-artefact(7)
reading:        MITRE ATT&CK T1070; journalctl(1) --vacuum-time;
                chattr(1); rsyslog remote forwarding documentation
notes:          ⚠ **MANDATORY CONTENT.** ../client/04 §2.7 requires this
                page to carry the defender's answer, because it is the
                page most at risk of teaching only the offensive half.
                CAVEATS is that content. It may be reworded; it may not
                be dropped.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  anti-forensics(7), log-integrity(7)
hook:           Finishing an operation with heat on the clock and
                reaching for the tool that makes it go away.
misconception:  commonly believed that running a scrubber returns the
                target to its prior state, so a successful run means no
                evidence remains; actually it removes the records it
                knows about on the machine it ran on. Anything already
                forwarded elsewhere is untouched, anything append-only
                resists it, and the resulting gap is itself evidence.
transfer:       Ask the defender's question on your own machine: if
                somebody erased your logs right now, what would still
                betray them? Shell history, file modification times,
                anything already shipped to another host, your router's
                records. Listing them takes five minutes and is the most
                useful defensive exercise in this document. Assumes a
                Unix shell — see ED-8.
verified:       Indicator removal as ATT&CK T1070; forwarding, append-only
                attributes and hash-chaining as the three effective
                counters — rsyslog/syslog-ng documentation, chattr(1),
                RFC 6962; the game's tool and its heat interaction —
                ../design/08-stealth-and-noise.md §2; the mandatory
                defender's-answer requirement —
                ../client/04-terminology-and-education.md §2.7.
                Checked 2026-07-25.

## SYNOPSIS

       log-scrubber [-n] [-v] [--] <target>

## DESCRIPTION

Removes the records your operation left on a target, reducing the trace
it would otherwise leave behind.

It works on the records it can reach, on the machine it ran on. That
qualifier is the entire content of this page, because it is where the
difference between the game's version and reality lives — and because
the same tool, in someone else's hands, is what you are defending
against.

## OPTIONS

- `-n`, `--dry-run` — print what would be removed and its published cost,
  without acting. See `dry-run(7)`.
- `-v`, `--verbose` — report which record types were addressed.

## EXIT STATUS

- `0` — the server accepted the operation.
- `1` — refused; nothing changed.
- `77` — a gate blocks this; the requirement is printed.

## REAL-WORLD COUNTERPART

real — log manipulation is a routine stage of real intrusions,
catalogued as MITRE ATT&CK **T1070, Indicator Removal**. It is
sufficiently standard that defenders enumerate and detect it rather than
being surprised by it.

Real erasure is markedly less effective than this tool, for the reasons
in `anti-forensics(7)`: a system records fragments of the same event in
several unrelated places, and erasure leaves a gap that is itself a
finding.

## CAVEATS

**The defender's answer — how you stop this being done to you.** Three
measures, in order of effectiveness, and none of them is a product you
have to buy:

- **Forward your logs off the machine as they are written.** `rsyslog`
  and `syslog-ng` do this and it is the single most effective control
  here: a record that left the machine before the intruder arrived cannot
  be scrubbed by anyone who only has that machine.
- **Make the local record append-only.** On Linux, `chattr +a` means even
  root must explicitly clear the attribute before rewriting — and that
  clearing is an event you can alarm on.
- **Chain the entries.** If each entry commits to the hash of the last, a
  deletion becomes a visible gap rather than a silent absence. See
  `append-only-log(7)`.

And the finding that survives all scrubbing: **a gap is evidence.** A log
that runs continuously and then stops for eleven minutes has told an
investigator that something happened, and when. That is often more useful
than the deleted lines would have been.
```

---

### 3.22 `auto-counter-daemon(8)`

```
id:             auto-counter-daemon
section:        8
name:           auto-counter-daemon
canonical:      Auto-Counter Daemon
gloss:          Fires back on your behalf while you are logged off.
status:         real, simplified
aliases:        counter-daemon, automatic retaliation
glossary:       ../design/glossary.md
seeAlso:        hack-back(7), automated-response(7), attribution(7),
                daemon(7), incident-response(7), detection-cost(7)
reading:        18 U.S.C. §1030; Computer Misuse Act 1990;
                fail2ban(1); OSSEC/Wazuh active response
notes:          ⚠ **MANDATORY CONTENT** (../client/04 §2.8): this page
                must state plainly that hacking back is illegal in most
                jurisdictions, and that real automated response does not
                counter-attack. Both statements are in CAVEATS and are
                not removable. hack-back(7) carries the full treatment.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         08
stage:          adversarial
prerequisites:  automated-response(7), hack-back(7)
hook:           Arming it before logging off, and finding on return that
                it fired at something while you were asleep.
misconception:  commonly believed that automatic retaliation is a real
                security product one could buy for a real network;
                actually no such category exists, for two reasons that
                are both fatal: it would be unauthorised access, and the
                machine it would retaliate against is usually another
                victim.
transfer:       Set up `fail2ban` on a machine you own and read what it
                does when triggered — it blocks, and that is the entire
                repertoire of real automated response. Then notice that
                its documentation's main warning is about locking out
                *yourself*. Assumes a Unix shell — see ED-8.
simplified:     The mechanism — a daemon that watches, decides and acts
                unattended — is exactly real. Its direction is not: real
                active response acts on systems the defender owns.
verified:       The permanent 18-compute cost and schematic gate —
                ../design/09-defense-and-hardening.md §1-§2; real active
                response limited to blocking, isolation and quarantine —
                OSSEC/Wazuh documentation, fail2ban(1); the legal
                position — 18 U.S.C. §1030 and Computer Misuse Act 1990;
                the mandatory statement requirement —
                ../client/04-terminology-and-education.md §2.8.
                Checked 2026-07-25.

## SYNOPSIS

       auto-counter-daemon [--arm|--disarm] [-v] [--]

## DESCRIPTION

Armed, it launches a weak counter-attack when your rig is raided while
you are logged off. It is schematic-gated and reserves 18 compute
permanently for as long as it stays armed — the heaviest standing cost of
any defence in the game, for the least reliable effect.

That trade is deliberate. It is a daemon in the ordinary sense
(`daemon(7)`): a background process that watches for a condition and acts
without you. What it does when it acts is where the fiction and the world
part company.

## OPTIONS

- `--arm`, `--disarm` — begin or end the standing reservation.
- `-v`, `--verbose` — report which event triggered a firing and against
  what.

## EXIT STATUS

- `0` — the server accepted the arm or disarm.
- `1` — refused; nothing changed.
- `77` — the schematic gate blocks this; the requirement is printed.

## REAL-WORLD COUNTERPART

real, simplified — as a *mechanism*. Software that watches for a
condition and responds without a human is completely ordinary:
`fail2ban`, OSSEC and Wazuh active response, and every SOAR product.

What none of them does is attack back. Their entire repertoire is
defensive and confined to systems the defender owns: block an address,
isolate a host, kill a process, quarantine a file, page a human.

## CAVEATS

⚠ **Hacking back is illegal in most jurisdictions, including the United
States and the United Kingdom, and being attacked first does not change
that.** Computer-crime statutes define the offence by the absence of the
owner's authorisation, and an attacker cannot forfeit authorisation they
never had the power to grant. There is no provocation defence. See
`hack-back(7)`, which carries the full treatment and the citations.

⚠ **Real automated response does not counter-attack**, and the reason is
not only legal. The machine attacking you is very often a compromised
third party, so retaliation lands on another victim — see
`attribution(7)`.

This tool is a game mechanic in a fiction where the Eye is not going to
prosecute you. Take from it the idea of a daemon that acts unattended,
and the cost of keeping one armed. Do not take from it the idea that
automatic retaliation is something you may build.
```

---

## 4. What this domain deliberately does not teach

Four exclusions, each a decision rather than an omission.

**No offensive technique.** The dual-use rule from `06` **CT-5** is applied strictly here because this domain sits closest to the line: pages explain what an attack class is and what defeats it, never how to perform one. `anti-forensics(7)` names what survives erasure; it names no tool. `timestomping(7)` explains the inconsistency that betrays it; it does not explain how to produce one. `../client/04` §4.4's ban on citing offensive-tooling walkthroughs is honoured in every `reading:` field.

**No malware analysis, reverse engineering or exploit development.** Real, adjacent, and with no surface in this game — `00` §7.3 says a concept with no hook is not written, and inventing a hook to justify interesting material is exactly the failure that rule exists to stop.

**No vendor taxonomy.** EDR, XDR, SIEM, SOAR, MDR and the rest are marketing categories that change faster than this document could track, and teaching them would date the curriculum badly while conveying almost nothing durable. The underlying practices — detection, integrity monitoring, automated response, audit — are here under their own names.

**No compliance frameworks.** SOC 2, ISO 27001, PCI DSS and their relatives are real and consequential, and they are about organisations rather than machines. A player learns nothing transferable about how computers work from a control catalogue.

---

## 5. Open questions

Prefix **`DF-`** (defence). Distinct from `CT-` (`06`), `DS-` (`07`) and the three unrelated meanings of `T-` noted in **CT-10**.

- **DF-1: `rootkit(7)` and `cross-view-detection(7)` are `03`'s, and this is the doc set's most likely place for a duplicate entry.** `03-operating-systems.md` §1.4 claims both and writes both in full (its §3.19, §3.20), correctly — a rootkit is defined by what it does to the kernel's answers. But this domain describes both constantly, and the natural instinct for whoever translates these into term files is to explain "briefly, for context", at which point there are two entries answering one question and `00` §1.4 is violated. **Proposal: when this domain's term files are written, each page mentioning either concept is diffed against `03`'s, and the reviewer's explicit question is "does this page teach `03`'s payload?"** Same mechanism as **SH-4**, same reason.

- **DF-2: this document has no `first-session` entry and its graph check 4 therefore fails as written.** Every chain here leaves the domain downward and roots in `01`–`03`, which is correct — nothing in detection is needed to act in the first twenty minutes. `02` raised the identical problem as **CA-4** and proposed that check 4 read "reachable from a `first-session` root **or** cited in `seeAlso` by entries in other domains". **Two documents now hit it, which is enough evidence to amend the check.** Needs deciding with **ED-4** (whether the checks are automated at all).

- **DF-3: `honeytoken(7)`'s homonym is not in `../client/04` §2.15's table.** `00` §3.2 makes `notes:` mandatory for every §2.15 homonym, and this entry carries one — but the collision it documents (canary-as-decoy versus canary-as-early-rollout) was identified here rather than found in that table. Either §2.15 should gain the row, or this document has invented an obligation for itself. The collision is real either way; a player who works in software will meet both senses. **Same shape as SH-3**, and the two should be resolved together by someone who can read §2.15's actual contents.

- **DF-4: `signature-detection(7)` collides with `digital-signature(7)` and neither is flagged.** Inventoried here, unwritten. "Signature" means a known-bad pattern in detection and a cryptographic proof in `06`, and the two appear within three documents of each other. When the entry is written it needs mandatory `notes:` — and this is a genuine addition to `../client/04` §2.15's table rather than a candidate. **Raise against §2.15 before the entry is written**, not after.

- **DF-5: the game has no false-positive surface, which weakens three good entries.** `false-positive(7)`, `base-rate-fallacy(7)` and `alert-fatigue(7)` are the strongest teaching in this domain, and their hooks are the weakest — each is written against what a player *would* experience if scans sometimes flagged legitimate processes and armed defences sometimes fired wrongly. `../design/04-mining.md` §3.2's scan tiers imply this but do not state it. **This is a curriculum finding against the design** (`00` §1.2 rule 1): if scans never produce a false hit, then `base-rate-fallacy(7)` is teaching a real and important thing that the game itself contradicts, and the honest options are to add the surface or to demote the three entries to index-only. **Recommend adding the surface** — it is cheap, it makes the Thorough Scan's price legible, and it is the single change that would make this domain's best material land.

- **DF-6: `responsible-disclosure(7)` has no game surface at all.** It is inventoried because it is the constructive counterpart to `hack-back(7)` — what you *do* when you find someone else's vulnerability — and because a game teaching intrusion arguably owes the player that page. But `00` §7.3 is unambiguous that a concept with no hook stays in the index, and inventing a hook to justify it would be the failure §7.3 exists to prevent. **Recommend: leave inventoried, and revisit if `../design/14-world-and-narrative.md` ever gives a Sickle contact a reason to ask.**

- **DF-7: does this domain's material change what `../design/09-defense-and-hardening.md` OQ-6 concludes?** That open question asks whether the Detection Array is redundant now that discovery is resolved via manual investigation and scan tiers. This document's answer, offered as input rather than as a decision: **the Array is the only surface in the game for standing, unattended detection**, and `detection-cost(7)`, `alert-fatigue(7)` and `automated-response(7)` all hang off it. Folding it into scan efficiency would remove the one place the game teaches that watching has a permanent price. Not this document's call, but the curriculum has a stake in it.

- **DF-8: one citation in `alert-fatigue(7)` is secondary rather than primary.** The 2013 retail-breach example — alerts generated, not acted upon — is drawn from contemporaneous reporting rather than from a primary incident report, and `00` §8.2 asks for primary sources. The *phenomenon* is well-established independently and the entry does not depend on the example. **Either source it properly or cut it to a general statement**; do not leave a dated, named, secondhand claim in shipped content.

- **DF-9: nothing in this domain has had a technical review, and here it matters more than usual.** `00` §8.4 makes a practitioner pass a gate. Two specific places a practitioner is most likely to object: `base-rate-fallacy(7)`'s worked arithmetic is deliberately simplified — it assumes independence between events and a single detector, which is not how layered detection actually composes — and `hack-back(7)` states a legal position, which is the one kind of claim in this entire doc set where being confidently wrong could genuinely harm a player. **`hack-back(7)` should be read by someone with actual legal knowledge before it ships**, and if that is not possible, it should say so on the page rather than sound more certain than its authorship supports. **ED-6 is the blocking question; this is its sharpest instance anywhere in the curriculum.**
