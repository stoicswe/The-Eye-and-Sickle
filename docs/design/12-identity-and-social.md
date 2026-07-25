# 12 — Identity & Social Systems

**Status:** Established (design sessions 1–5; informant system from session 3)
**Depends on:** `01-core-resources.md` (heat, reputation, ledger), `02-unlock-gates.md`
**Depended on by:** `13-multiplayer-and-federation-play.md`, `../architecture/02-identity-and-auth.md`

The social layer is where paranoia lives. Its centerpiece is the **informant system**: a hidden-role mechanic that keeps the Sickle from ever feeling fully trustworthy, resolved through a deliberately fair evidence process with one deliberately unfair escape hatch.

---

## 1. Identity items (established)

| Item | Function | Gate |
|---|---|---|
| **Forged Credentials** | Pose as Eye personnel for a single interaction | Reputation or black market |
| **Burner Handle** | Second identity with separate heat; splitting time slows progression on both | Schematic |
| **Informant Dossier** | Evidence-gathering tooling feeding the informant threshold system | Reputation |
| **Broker Contact** | Unlocks black-market vendors — reachable only above a heat threshold | Heat state |

### Per-item notes

**Forged Credentials** — single-use impersonation of Eye personnel; opens social/access options a Sickle handle can't. Reputation- or black-market-gated (dual-source: earn trust, or buy it while hot). One interaction per use keeps it a *moment*, not a disguise you wear.

**Burner Handle** — a second identity with **separate heat**. The catch: splitting time across two handles **slows progression on both** (you're half as present on each). Schematic-gated because "run two identities" is a capability. The honest-player use is compartmentalization (keep loud ops off your main); it is not a free heat-dodge because the progression tax is real. Interacts with Ghost Protocol (`08` §3) — a burner is the *planned* second identity, Ghost Protocol is the *emergency* rebirth.

**Informant Dossier** — the evidence-gathering tool that feeds §2. Reputation-gated. This is how a player builds the case that clears the evidence threshold; canary-token handle-tags (`09`) and ledger analysis (`01` §2.2) are the raw material it compiles.

**Broker Contact** — unlocks the heat-gated black market (zero-days, forged creds, `06`/`02`). Reachable only **above** a heat threshold — the canonical "being hunted opens doors" mechanic. See `02` §2.5.

## 2. The informant system (session-3 design)

**Informant status, for both NPC commanders and player characters, is randomized and hidden.** Anyone in the Sickle might be feeding The Eye. Key rules:

- Players **may opt in** when recruited by The Eye, with **real incentives** (an actual reward path, not a trap flag).
- An opted-in player **may be doubled** as counter-intelligence — pretend to inform while feeding The Eye false data for the Sickle's benefit. Layered loyalty is the intended texture.
- Because status is hidden and randomized, **suspicion is never certainty** without going through the evidence process.

### 2.1 Two removal paths

An accused informant (NPC or player) can be removed two ways, with deliberately different rigor:

**1. Evidence path — structurally guaranteed correct.**
Requires a **full evidence threshold plus corroboration**. Because it demands real, compiled proof (Informant Dossier output, canary tags, ledger trails), a removal via this path is *always* justified — the system guarantees you can't clear the threshold against an innocent party. This is the fair backbone.

**2. Mass-vote override — deliberately dangerous.**
Requires **partial evidence as an eligibility gate** *plus* a **server-wide Sickle supermajority**. It:
- carries **real costs regardless of outcome** (you spent the political capital and the accused's allies remember),
- has **no reversal**,
- is **deliberately very unlikely** to succeed but **mechanically possible.**

> **The override exists to preserve paranoia inside an otherwise fair system.** Without it, a cleared player is provably safe and the tension evaporates. With it, even an innocent player knows the mob *could* come — very rarely, at great cost, but it could. Do not "balance" the override into reliability; its unreliability is the point.

### 2.2 Why this is fair *and* scary at once

The evidence path means diligent players are never railroaded — do the work, get the correct outcome. The override means no one is ever *guaranteed* safe. The two together produce the target feeling: **justice is real, but so is the mob**, and you never quite know which you're dealing with. This is the social analogue of the correlated-sweep design (`04` §4) — mostly fair, occasionally catastrophic, always looming.

## 3. Evidence economy (how the pieces feed in)

The evidence threshold is built from systems defined elsewhere — the informant system is a *consumer* of the rest of the game's outputs:

| Evidence source | Produced by | Doc |
|---|---|---|
| Handle tags on touched decoys | Canary Token, Honeypot Stash | `09` |
| EC/loot flow analysis | Public ledger (defeated by Dead Drop) | `01` §2.2, `08` |
| Compiled dossiers | Informant Dossier | §1 |
| Behavioral/association data | Traffic Analyzer, recon | `07` |

This is why canary tokens are called "disproportionately valuable" (`09` §2): an 8 EC decoy can be the tag that anchors an evidence case. Cheap defensive items have outsized social consequences.

## 4. [PROPOSAL] Open threads for the social layer

Not in the source design; flagged for a future session (mirror into `15` if adopted):

- **S-1:** How is "server-wide Sickle supermajority" quorum defined on a *federated* server set (`13`)? Is the vote per-home-server or federation-wide? Leans per-home-server for both fiction (your cell) and tech (avoids cross-server vote consensus, which would need the validator quorum, `../architecture/05`).
- **S-2:** What are the concrete Eye-side incentives for opting in as an informant, and do they risk being strictly better than honest play? Needs the same faucet discipline as `03` §5.
- **S-3:** Griefing surface: can the override be weaponized against a productive player by a coordinated group? The "real costs regardless of outcome" rule is the intended brake — verify it's steep enough in playtest.
