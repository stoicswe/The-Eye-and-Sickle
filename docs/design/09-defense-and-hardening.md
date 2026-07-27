# 09 — Defense & Hardening

**Status:** Established (design sessions 1–5)
**Depends on:** `01-core-resources.md`, `02-unlock-gates.md`, `04-mining.md` (Detection Array, Rootkit Wrapper)
**Depended on by:** `05-hacking-minigame.md` (target defense profiles), `10-botnets.md` (Sentinel), `12-identity-and-social.md` (canary → evidence)

The player is a target as well as an attacker — storage can be raided (`01` §6), miners can be planted (`04` §3), bots can be attacked (`10`). This is the defensive toolbox. Note the compute accounting: most defenses **reserve compute permanently while armed**, so a well-defended player has fewer cycles for offense. Defense competes with everything else for the master scarcity — that's the whole balance.

---

## 1. Tools (established)

| Tool | Function | Gate | Cost | Compute |
|---|---|---|---|---|
| **Firewall (T1–T3)** | Flat difficulty increase on incoming breach attempts | Ethecoin | 40 / 110 / 200 EC | 5 / 9 / 15 (while armed) |
| **Canary Token** | Fake file that alerts the owner the instant it's touched, and tags the toucher's handle | Ethecoin | 8 EC each | 1 |
| **Tarpit** | Slows every intruder action; doesn't stop them, buys response time | Ethecoin | 70 EC | 8 |
| **Honeypot Stash** | Decoy high-hackable zone containing junk; raiders can't tell until extraction | Reputation | — | 12 |
| **Auto-Counter Daemon** | Automatically launches a weak counter-attack when raided while offline | Schematic | — | 18 (permanent while armed) |
| **Detection Array (T1–T3)** | Improves scan **signal quality** — cuts the false-positive rate on your own rig | Schematic | — | 6 / 14 / 25 (permanent) |
| **Rootkit Wrapper** | Hides a *deployed* miner from routine host scans and raises crack difficulty against it; does not survive a deliberate audit | Ethecoin | 50 EC | 2 per miner |
| **Cold Storage Expansion** | Vault capacity, sub-linear scaling | Schematic + reputation | — | — |

## 2. Per-tool notes

**Firewall (T1–T3)** — flat difficulty add to incoming breaches, priced 40/110/200 (T3 is a "top purchasable," `03` §2). EC-gated because it's horizontal protection, not a ceiling. The escalating compute cost (5/9/15 while armed) is the real limiter: a T3 firewall is 15 permanent cycles you're not mining or attacking with.

**Canary Token** — cheap (8 EC) and **disproportionately valuable**: it both alerts you *and tags the toucher's handle*. That handle-tag feeds directly into the evidence and informant systems (`12`) — a canary is how you build a case against a raider. Salt your stash with them; at 8 EC and 1 compute they're nearly free. The design wants these everywhere.

**Tarpit** — doesn't stop intruders, slows them. Pairs specifically with the **bot-backlog timer** (`10` §1): it buys the seconds you need to triage *which bot to save* when multiple engagements fire at once. A defensive force-multiplier for multitaskers, not a wall.

**Honeypot Stash** — a decoy High-Hackable zone full of junk; raiders can't tell real from fake until extraction. Reputation-gated (decoy infrastructure would distort raids if free). The psychological counterpart to Ping Sweep (`07`): it makes casing a target unreliable, so raiding stays risky.

**Auto-Counter Daemon** — offline retaliation: launches a weak counter when you're raided while logged off. Schematic-gated, heavy permanent compute (18). It doesn't win the fight; it makes raiding you *cost* something even in your absence, feeding the attacker some heat/handle exposure.

**Detection Array (T1–T3)** — **redefined 2026-07-26, closing OQ-6.** It was "raises per-tick discovery chance", which did substantially the same job as a paid scan and was the reason OQ-6 asked whether it was redundant at all. It now does something no scan can: **it improves the quality of the signal rather than the chance of a hit.** Standing compute buys a lower false-positive rate on your own rig (`04` §3.2), so a Quick Scan on a well-instrumented rig lies to you less often than a Quick Scan on a bare one.

That makes it non-redundant *by construction* rather than by tuning: scans buy sensitivity, the Array buys precision, and the two are different axes. It also gives the permanent compute reservation a legible payoff — you are paying, continuously, not to be sent chasing ghosts. Folding it into scan efficiency was the alternative and was rejected: it would have removed one of the few reasons to commit standing compute, which is what makes defence a real budget choice (§3).

**Rootkit Wrapper** — the *offensive* defense item: hides your **deployed** miner from routine host scans and raises crack difficulty against it, but **does not survive a deliberate audit** (manual investigation, `04` §3.1, always finds it). This is the counter-play to hosts' detection, and it's what gives crack-difficulty scaling (`04` §5.1) an item to key off. 2 compute per wrapped miner, EC-gated.

**Cold Storage Expansion** — vault capacity, sub-linear (Invariant I12), schematic **+** reputation gated, never purchasable. See `01` §6 for the capacity model and `03` §4 for the [PROPOSAL] on an EC *installation* cost (schematic unlocks, EC installs).

## 3. The defensive compute budget

Sum the "while armed / permanent" costs and the design's core tension appears: a paranoid loadout — T3 Firewall (15) + Tarpit (8) + Honeypot Stash (12) + Auto-Counter Daemon (18) + T2 Detection Array (14) — is **67 permanent cycles** on a 100-cycle rig, leaving 33 for offense, mining, and stealth combined. **You cannot be fully defended and fully offensive at once.** This is intended and load-bearing; do not "fix" it by discounting defensive compute.

## 4. Defensive archetypes (emergent, for balance reference)

- **Turtle:** heavy firewall + honeypot + auto-counter, self-mines the remaining cycles. Low income, very hard to raid. Viable but capped by Invariant I4's 40 EC/hr floor.
- **Trapper:** canaries everywhere + honeypot stash + Traffic Analyzer, builds evidence cases on raiders (`12`). Turns defense into an offensive-intel engine.
- **Ghost miner:** minimal defense, Rootkit-wrapped deployed network, relies on being unfound rather than unbreakable. High variance (`03` §1.2).

If any archetype dominates in playtest, the lever is compute cost, not function.

## 5. Cross-references

- What attackers bring: `06-intrusion-tools.md`
- The breach these defenses modify: `05-hacking-minigame.md` §2 (target defense profile)
- Canary/handle-tag → evidence: `12-identity-and-social.md`
- Detection Array role: **OQ-6 resolved** 2026-07-26 — see above and `15-open-questions.md` §3
