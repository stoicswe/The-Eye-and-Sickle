# 02 — The Five Unlock Gates

**Status:** Established (design sessions 1–5)
**Depends on:** `00-vision-and-pillars.md`, `01-core-resources.md`
**Depended on by:** every item/tool doc (`06`–`11`); `15-open-questions.md` OQ-2

Progression control is the difference between this design working and collapsing. Every item in the game sits behind **exactly one** gate (Invariant I3), and gate assignment follows a *rule*, not per-item taste. When adding any new item, classify it with the table below first; if it doesn't classify cleanly, the item is probably badly designed.

---

## 1. The gates

| Gate | What it covers | Rationale |
|---|---|---|
| **Ethecoin** | Consumables, replaceable tools, horizontal options | Money should never raise a ceiling |
| **Schematic** | Permanent capability increases, all rig infrastructure | Progression paced by exploration, not grind |
| **Reputation** | Anything economy-distorting if freely available | Faction investment as a gate on power |
| **Proof-of-Skill** | Automation shortcuts specifically | Demonstrate the manual version before skipping it |
| **Heat State** | Vendor and contact *access* | Determines what's reachable, never what's ownable |

### 1.1 The assignment rule, as a decision procedure

Ask in order; first "yes" wins:

1. Does it raise a permanent ceiling (compute, vault size, new permanent capability)? → **Schematic** (or story milestone; those are the same track).
2. Does it automate or skip a puzzle the player would otherwise solve manually? → **Proof-of-Skill**.
3. Would it distort the economy if anyone could buy it freely (untraceable transfers, decoy stashes, forged identity...)? → **Reputation**.
4. Is it a consumable, a replaceable tool, or a sidegrade? → **Ethecoin**.
5. Is it not an *item* at all but a vendor, contact, or market? → **Heat State** governs reachability.

Split gates are allowed when the capability/recurring-cost split is real: the Relay Chain **framework** is schematic-gated while additional **hops** are an EC expense per session (`08-stealth-and-noise.md`). Rainbow Table is EC + schematic (buy the table, but the capability to use it is found). Cold Storage Expansion is schematic + reputation. In each case the *ceiling* component is always on the non-EC side.

---

## 2. Gate-specific rules

### 2.1 Ethecoin gate

Everything EC-gated must be **losable and replaceable** — this is what makes the loss loops (bot destruction, raid losses, tool loss on failed hacks) economically survivable and what makes EC sinks work (`03-economy.md`). If losing an item permanently would feel like losing progression, it must not be EC-gated — move it up the ladder.

### 2.2 Schematic gate

Schematics are **found or earned at designer-paced milestones** — exploration rewards, story beats, deep-infrastructure objectives (e.g. Firmware Implant is explicitly recovered from deep inside Eye infrastructure, `11-rig-infrastructure.md`). They are never sold for EC, never RNG-farmable from repeatable content.

Related resource: **generic schematic contribution material** — partial-progress salvage from tier-gated bot losses (`10-botnets.md` §1a). Conversion rate is open (OQ-5); the risk to hold in mind is that a cheap rate turns bot sacrifice into a ceiling-progression grind, which is exactly what this gate exists to prevent.

### 2.3 Reputation gate

Reputation-gated items (Dead Drop, Credential Harvester, Honeypot Stash, Sentinel/Mimic frames, Informant Dossier, Forged Credentials...) share a property: they'd be economy- or trust-distorting if freely purchasable. Faction investment is the brake, and faction abandonment (reputation reset, `01-core-resources.md` §5) is what makes the brake real.

### 2.4 Proof-of-skill gate — tier-gated, never count-gated

> **Established rule:** the automation tool for a puzzle class unlocks when the player has solved that class **at or above a set difficulty, against a live or defended target** — not a dormant one. Never "solve it N times."

Count-gating invites farming the weakest available target and rewards patience. Tier-gating rewards competence. This has two implementation requirements:

1. Puzzle instances must carry a **class** and a **difficulty tier**, and the resolution record must note whether the target was live/defended (`05-hacking-minigame.md` §classes; the same tier data feeds the bot-salvage exploit guard, Invariant I13).
2. The unlock check is per-class: Overflow Kit (bypasses a puzzle layer) unlocks off the corresponding class solved at threshold tier. New automation items must name their class + tier at design time.

### 2.5 Heat-state gate — runs both directions

- Some vendors and contacts are only reachable while **cold** (respectable fixers don't meet wanted people).
- **Black-market brokers are only reachable while hot** — being hunted opens doors that being clean does not. This is the only sanctioned route to zero-days (`06-intrusion-tools.md`), which means acquiring one *requires* being hunted: power at the price of exposure.

Heat state never changes what a player *owns* — going cold doesn't confiscate black-market purchases; going hot doesn't lock the vault. It changes what's *reachable*.

---

## 3. Worked examples

| Item | Gate | Why |
|---|---|---|
| Fuzzer (25 EC) | Ethecoin | Replaceable intrusion tool; losing it is an errand, not a setback |
| Topology Mapper | Schematic | Permanent recon capability increase (1 hop → 2 hops is a ceiling) |
| Dead Drop | Reputation | Untraceable transfer distorts the investigator economy if free |
| Overflow Kit | Proof-of-Skill | It *skips a puzzle layer* — the definitional automation shortcut |
| Broker Contact | Heat State | It's access, not ownership; reachable only above a heat threshold |
| Relay Chain | Schematic + EC/hop | Capability earned; operating depth is a recurring expense |
| Zero-Day | Heat state (access) + EC (400+) | Purchasable *only* through the hot-gated black market, consumable, rare-loot alternative source |

---

## 4. Known tension (tracked, not resolved)

**OQ-2 — gate consolidation.** Five currencies of progression may be too much UI and cognitive load. If bloat shows up in playtests, the plan of record is to collapse **schematics + proof-of-skill into a single "field research" track** (both are "the game watched you do something and unlocked the next thing"). Fine as-is for now; do not add a *sixth* gate class under any circumstances without revisiting this.

## 5. Checklist for adding a new item (for future design/Claude Code sessions)

1. Classify with §1.1 — exactly one primary gate.
2. If EC-gated: price it against the `03-economy.md` anchors; confirm it's replaceable; name which sink it feeds.
3. If schematic-gated: name where the schematic is found (region/story beat).
4. If proof-of-skill: name the puzzle class + threshold tier.
5. If reputation: name the faction and the standing tier.
6. If heat-state: name the threshold and direction (hot-gated or cold-gated).
7. Add the row to the relevant tool table (`06`–`11`) with compute + noise stats, and to `glossary.md` if it introduces a term.
