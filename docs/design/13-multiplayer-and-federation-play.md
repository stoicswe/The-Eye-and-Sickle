# 13 — Multiplayer & Federation Play

**Status:** ⚠️ Mixed. The *technical* federation model is **established** (Tech Chat 1 & 2 — see `../architecture/`). The *player-facing multiplayer design* below is largely **[PROPOSAL]**, extrapolated from established rules. Sections are tagged individually.
**Depends on:** `00-vision-and-pillars.md`, `04-mining.md`, `12-identity-and-social.md`, and the whole `../architecture/` tree
**Depended on by:** `14-world-and-narrative.md` (server heat), `15-open-questions.md`

This doc is the *gameplay* view of multiplayer — what it feels like to play with and against other people. The *plumbing* (servers, identity, provenance, quorum) is in `../architecture/`. Read this for design intent; read those for how it's built.

---

## 1. Established frame

From `00` and the tech decisions:

- **Single-player by default; multiplayer is opt-in and offers better rewards at the cost of real loss risk.** Multiplayer is not a separate mode — it's the same game with other real operators sharing the graph.
- **Servers are self-hostable (Minecraft-style) with allowlists**, and an **opt-in federation directory** links public servers (`../architecture/03-server-and-federation.md`).
- **Cross-server adversarial outcomes (duels, item transfers) are secured by a validator quorum + signed item provenance, with no single arbiter** (Invariant I15, `../architecture/04`/`05`).
- **Identity is a portable DID** (AT Proto), so "the same operator" is recognizable across servers (`../architecture/02`).

## 2. [PROPOSAL] What multiplayer *is*, at the play level

The established systems already imply the multiplayer surface — it's mostly the existing systems pointed at other players instead of NPCs:

| System | Single-player form | Multiplayer form |
|---|---|---|
| Deployed miners (`04`) | Plant on NPC machines | Plant on **other players'** rigs; they can crack/hijack/sabotage back |
| Raids (`01` §6, `07` Ping Sweep) | Hit NPC stashes | Hit **other players'** exposed High-Hackable zones |
| The breach (`05`) | vs. NPC defense profiles | vs. **player-built** defenses (`09`) |
| Informant system (`12`) | NPC commanders | **Player** informants, doubled agents, mob votes |
| Reputation/heat | Systemic Eye pursuit | **Server heat** from the whole population (`01` §4.2) |

**Design intent:** multiplayer doesn't add mechanics, it adds *intent behind the opposition*. An NPC honeypot is a puzzle; a player's honeypot is a mind game. The "better rewards, real loss" trade (`00`) is what makes the same actions feel higher-stakes against humans.

## 3. [PROPOSAL] Cross-server duels — the player experience

The architecture specifies *how* a duel is adjudicated (validator quorum, majority-signed consensus). What a duel *is*, at the design level, is under-specified in the source. First pass:

- A **duel** is a consequential 1v1 adversarial engagement whose outcome must be trusted across servers — e.g. a breach-vs-defense contest where a valuable, provenanced item changes hands.
- Because the item's ownership transfer is federation-visible, the outcome can't be self-reported (that's the cheating problem the quorum solves). So duels are the *specific subset* of PvP that produces a **signed provenance event** (`../architecture/04`).
- Most PvP (raiding an exposed stash, cracking a planted miner) is **home-server-local** and resolved by that server's Postgres — no quorum needed. Duels are the escalated, cross-server, high-value case.

> **[PROPOSAL] D-1:** Define the exact trigger boundary — *which* engagements are "duels" requiring quorum vs. local PvP. Proposed rule: **any transfer of a provenanced item between players on different home servers** invokes the quorum; everything else is local. This keeps the expensive consensus path rare (it should be — quorum rounds cost latency and validator work) while still covering the case that actually needs trustless adjudication. Needs the federation designer's sign-off.

## 4. [PROPOSAL] Server culture and the self-host model

The Minecraft-style self-host model has design consequences worth stating:

- **Home servers are communities**, not shards. A server's allowlist, its house rules, and its population's collective server-heat (`01` §4.2) give each server a character. The Sickle-cell fiction (`12`) maps naturally onto "your home server = your cell."
- **Federation is opt-in and non-adversarial-data-only** (`../architecture/03`): servers share the minimum needed to recognize identities and validate provenance, never enough for one server to grief another's internal state.
- **Flagged servers face federation-wide non-recognition** (`../architecture/03`/`05`): a server caught minting fraudulent items or running dishonest validators has its items refused by honest servers. This is the anti-cheat endgame — you can run any server you like, but the federation decides whose items are *real*.

## 5. [PROPOSAL] Loss, stakes, and consent

The "real loss risk" of multiplayer needs guardrails so it's *exciting* rather than *rage-quitting*:

- All multiplayer losses are still **EC-class** (Invariant I2/I11) — you lose tools, instances, buffers, loot, never blueprints or ceilings. The floor (self-mining, vault) survives any PvP disaster. This is what makes opting into real-loss multiplayer sane.
- Opting into multiplayer should be a **legible choice with legible exposure** — the player should understand what's now at risk (exposed storage tiers, deployed miners on live rigs) before they opt in.

> **[PROPOSAL] D-2:** Is multiplayer opt-in **per-session, per-server, or per-account**? Recommend **per-server**: joining a federated public server is the opt-in; a solo/allowlisted private server is the single-player experience. Clean mental model, maps to the self-host architecture, and makes "real loss" a property of *where you chose to play*.

## 6. Cross-references

- The entire technical basis: `../architecture/03-server-and-federation.md`, `04-item-provenance.md`, `05-validator-quorum.md`
- Social/informant multiplayer: `12-identity-and-social.md`
- Why server heat makes other players your problem: `01-core-resources.md` §4.2
- Open questions: `15-open-questions.md` (D-1, D-2, S-1 all live here)
