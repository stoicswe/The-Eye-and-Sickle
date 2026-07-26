# Architecture Documentation — The Eye and Sickle

The **technical** source of truth: client, servers, identity, federation, and the cryptographic anti-cheat model. The companion folder `../design/` covers game systems and economy. Read `../../CLAUDE.md` first.

## Status of this folder

Almost everything here is **Established** — the full stack was decided end-to-end in **Tech Chat 1**, and the federation cryptography was specified in detail in **Tech Chat 2**. The substantially proposed docs are `06-data-model.md` (a first-pass schema) and `07-transport-security.md` (a gap neither tech chat addressed). Individual [PROPOSAL] notes are tagged inline elsewhere.

## Reading order

`00-overview.md` → then whichever layer you're working in.

## Document map

| # | Doc | Status | Covers |
|---|---|---|---|
| 00 | [`00-overview.md`](00-overview.md) | Established | The whole stack on one page + the constraints it was chosen against |
| 01 | [`01-tech-stack.md`](01-tech-stack.md) | Established | Client (JavaFX multi-window), server (Spring Boot + Postgres), deployment (Docker Compose) |
| 02 | [`02-identity-and-auth.md`](02-identity-and-auth.md) | Established | AT Protocol OAuth (authn-only), DID as portable player ID |
| 03 | [`03-server-and-federation.md`](03-server-and-federation.md) | Established | Self-hosting, allowlists, opt-in federation directory, non-recognition |
| 04 | [`04-item-provenance.md`](04-item-provenance.md) | Established | Detached-JWS per-item provenance chains; the full record/envelope schema |
| 05 | [`05-validator-quorum.md`](05-validator-quorum.md) | Established | Weighted-random sampling, BFT threshold, reputation update math |
| 06 | [`06-data-model.md`](06-data-model.md) | ⚠️ **[PROPOSAL]** | First-pass Postgres schema tying game state to the above |
| 07 | [`07-transport-security.md`](07-transport-security.md) | ⚠️ **[PROPOSAL]** | DID-authenticated encrypted channels between servers and between client and server |
| 08 | [`08-discovery-and-sync.md`](08-discovery-and-sync.md) | ⚠️ **[PROPOSAL]** | Automatic peer discovery; why shared state converges on validity, never recency |
| 09 | [`09-player-state-portability.md`](09-player-state-portability.md) | ⚠️ **[PROPOSAL]** | 3-slot character model (online-only); finding, backing up, and migrating a character across machines and servers |

The **client's** visual design — the two theme families, the UI token contract, terminology and accessibility — lives in [`../client/`](../client/README.md), not here. This folder covers the stack; that one covers the surface.

## The one-sentence summary

A lightweight cross-platform **JavaFX** client (one OS window per tool) talks to a self-hostable **Spring Boot + PostgreSQL** home server; players authenticate with their **AT Protocol DID** (identity only — no game data in their PDS); servers **opt into a federation** that adjudicates cross-server adversarial outcomes through a **reputation-weighted validator quorum** and **cryptographically signed per-item provenance**, with **federation-wide non-recognition** as the penalty for cheating servers.

## Why these choices (the constraints)

Every decision traces to a constraint the user actually gave in Tech Chat 1:

- *"very lightweight"* → JavaFX over a game engine; Postgres over anything heavier.
- *"multi-window (a window per tool)"* → JavaFX `Stage`-per-tool.
- *"cross-platform macOS/Linux/Windows"* → JVM; AtlantaFX for native theming.
- *self-hostable, no single point of trust for adversarial state* → Minecraft-style servers + validator quorum + provenance signatures instead of a central authority.
- *portable identity across independent servers* → AT Proto DIDs.

## Provenance

- **Tech Chat 1** — stack, client, server, deployment, identity scope, federation shape.
- **Tech Chat 2** — provenance JWS schema, per-item chain decision, validator sampling (A-Res, N=7), reputation update formulas (AIMD-style), equivocation slashing.

Both are also captured in the Claude project as a compact tech-decisions doc so they're never lost to chat history again.
