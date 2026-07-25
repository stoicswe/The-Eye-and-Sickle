# Design Documentation — The Eye and Sickle

This folder is the **design source of truth** for the game's systems, economy, and world. The companion folder `../architecture/` covers the *technical* stack (client, servers, identity, federation). Read `../../CLAUDE.md` at the repo root first for the invariants and conventions.

## Reading order

New to the project: read **`00` → `01` → `02` → `03`** in order. That's the spine — vision, resources, gates, economy. Everything else hangs off those four.

## Document map

| # | Doc | Status | What it covers |
|---|---|---|---|
| 00 | [`00-vision-and-pillars.md`](00-vision-and-pillars.md) | Established | Premise, factions, 5 pillars, **the 15 hard invariants**, product shape |
| 01 | [`01-core-resources.md`](01-core-resources.md) | Established | Compute, ethecoin, noise, heat, reputation, storage tiers |
| 02 | [`02-unlock-gates.md`](02-unlock-gates.md) | Established | The five gates + the assignment decision procedure |
| 03 | [`03-economy.md`](03-economy.md) | Established (calibration pending) | Income/cost anchors, sinks, faucet discipline |
| 04 | [`04-mining.md`](04-mining.md) | Established | Self-mining + deployed miners; the most complete system |
| 05 | [`05-hacking-minigame.md`](05-hacking-minigame.md) | ⚠️ **[PROPOSAL]** | The core puzzle — proposed, with a stable economy-facing contract |
| 06 | [`06-intrusion-tools.md`](06-intrusion-tools.md) | Established | Offensive tools + the zero-day rule |
| 07 | [`07-recon-tools.md`](07-recon-tools.md) | Established | Information tools + Provenance Tracer |
| 08 | [`08-stealth-and-noise.md`](08-stealth-and-noise.md) | Established | Noise management, relay chain, Ghost Protocol |
| 09 | [`09-defense-and-hardening.md`](09-defense-and-hardening.md) | Established | Defensive tools + the defensive compute budget |
| 10 | [`10-botnets.md`](10-botnets.md) | Established | Frames-as-blueprints, loss resolution, split attention |
| 11 | [`11-rig-infrastructure.md`](11-rig-infrastructure.md) | Established | The upgrade tree; the anti-flywheel gate discipline |
| 12 | [`12-identity-and-social.md`](12-identity-and-social.md) | Established | Identity items + the informant system |
| 13 | [`13-multiplayer-and-federation-play.md`](13-multiplayer-and-federation-play.md) | ⚠️ Mixed | Player-facing multiplayer (proposed) over an established tech base |
| 14 | [`14-world-and-narrative.md`](14-world-and-narrative.md) | ⚠️ **[PROPOSAL]** | The world, environmental story, escalation |
| 15 | [`15-open-questions.md`](15-open-questions.md) | Living | Every open question + resolution log |
| — | [`glossary.md`](glossary.md) | Living | Canonical terms + code-name conventions |

## Status tags — what they mean

- **Established** — decided in design sessions 1–5 (captured in the project's `ethecoin_design_doc.md`) or in the two technology chats. Change these only deliberately; they're what the rest depends on.
- **[PROPOSAL]** — first-pass design written to fill a gap the source material left open (chiefly the core minigame, player-facing multiplayer, and the world/narrative). Clearly marked at the top of each doc and inline. Keep, edit, or replace freely — nothing else is built to depend on the *specifics* of a proposal, only on established rules.

The split matters: a proposal can be wrong without breaking anything; an established invariant can't be touched without a redesign. When you resolve a proposal into a decision, update the doc, drop the [PROPOSAL] tag, and log it in `15` §3.

## The invariants

`00` §4 lists 15 hard invariants (I1–I15) — the load-bearing rules. `../../CLAUDE.md` mirrors them. If a change would violate one, the change is almost certainly wrong. They exist because each one, if broken, collapses a specific system (usually the compute economy or the risk economy).

## Provenance of this documentation

Extracted and expanded from:
- `ethecoin_design_doc.md` (the project's consolidated design doc, sessions 1–5) — all economy/systems content.
- **Tech Chat 1** — the full technology stack decision (→ `../architecture/`).
- **Tech Chat 2** — provenance schema, validator sampling, reputation math (→ `../architecture/04`, `05`).

Gaps the sources left open were filled with clearly-tagged proposals rather than left blank, so Claude Code sessions have something concrete to build against.
