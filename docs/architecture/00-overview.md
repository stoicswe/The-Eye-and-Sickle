# 00 — Architecture Overview

**Status:** Established (Tech Chat 1 & 2)
**Depends on:** the design invariants in `../design/00-vision-and-pillars.md` §4 (esp. I14, I15)

The complete technology stack, decided end-to-end. Each layer has its own doc; this page is the map and the rationale.

---

## 1. The stack at a glance

| Layer | Choice | Doc |
|---|---|---|
| **Client** | Java + JavaFX (AtlantaFX theming), multi-window via one `Stage` per tool | `01` |
| **Home server** | Spring Boot service + embedded H2 | `01` |
| **Deployment** | Docker Compose, self-hostable, Minecraft-style with allowlists | `01`, `03` |
| **Identity / auth** | AT Protocol OAuth, authentication-only; player DID = portable ID | `02` |
| **Federation directory** | Opt-in list of public servers; non-adversarial data only | `03` |
| **Cross-server duels** | Validator quorum drawn from opted-in servers; majority-signed; no single arbiter | `05` |
| **Anti-cheat** | Reputation registry + item provenance signatures; federation-wide non-recognition for flagged servers | `04`, `05` |

## 2. The shape of the system

```
   ┌─────────────────────────────────────────────┐
   │  CLIENT  (JavaFX, cross-platform desktop)    │
   │  one OS window per tool  ── map | term | rig │
   └───────────────┬─────────────────────────────┘
                   │  authenticates with AT Proto DID (authn-only)
                   │  game traffic ↓
   ┌───────────────▼─────────────────────────────┐
   │  HOME SERVER  (Spring Boot + embedded H2)    │
   │  self-hosted · Docker Compose · allowlist    │
   │  owns all game state for its players         │
   └───────────────┬─────────────────────────────┘
                   │  opt-in federation (non-adversarial data)
   ┌───────────────▼─────────────────────────────┐
   │  FEDERATION                                  │
   │  · directory of public servers               │
   │  · validator quorum  → signs duel outcomes   │
   │  · item provenance   → per-item JWS chains   │
   │  · non-recognition   → flagged servers' items│
   │                        refused federation-wide│
   └──────────────────────────────────────────────┘
```

External dependency, identity only:
```
   CLIENT ──sign-in──► AT Protocol (Bluesky/PDS)  ──resolve──► DID
   (never stores game state in the player's PDS — Invariant I14)
```

## 3. The three problems this architecture solves

1. **"Lightweight, cross-platform, multi-window desktop game."** → JVM + JavaFX. No game engine; the UI *is* the game (terminals, maps, dashboards), which a windowing toolkit models better than a scene-graph engine. (`01`)

2. **"Portable identity across independently-run servers, without building an identity service."** → AT Protocol DIDs. The player signs in with an existing AT Proto/Bluesky handle; their DID becomes the cross-server player ID the reputation/provenance systems key off. Scoped strictly to authentication. (`02`)

3. **"Self-hostable servers with no single point of trust for adversarial state (item ownership, duel outcomes)."** → the federation model: quorum consensus for cross-server outcomes and signed provenance for items, so no one server (including a cheating self-host) can unilaterally mint items or declare duel results. (`03`, `04`, `05`)

## 4. The load-bearing scoping decision

The single most important architectural boundary (Invariant I14):

> **Game state lives in the server's own database (embedded H2). AT Protocol is used *only* to prove "you are player X." No game data is ever written to a player's PDS.**

Rationale (from Tech Chat 1): AT Proto's repository model would *technically* allow writing custom item records into a player's own PDS via Lexicons — but that puts a player's vault contents in infrastructure *they control*, which is the exact self-hosted-cheating problem the quorum/provenance system exists to solve, just moved down a layer. Identity is safe to decentralize to the player; adversarial state is not.

## 5. Accepted trade-offs (named, not hidden)

- **AT Proto is a soft requirement to play online.** Every player needs an AT Proto identity (Bluesky account or other PDS). For a cyberpunk PvP audience that may not already be on Bluesky, that's real onboarding friction — accepted deliberately in Tech Chat 1 in exchange for not building/operating an identity service. Single-player/local play does not need it (see `02` §4 for the offline-identity question).
- **Federation adds latency and complexity** to cross-server duels (quorum rounds). Mitigated by keeping quorum on the *rare* high-value path only; most PvP is home-server-local (`../design/13` §3).
- **Self-hosting means adversarial servers exist.** The entire `04`/`05` cryptographic apparatus is the cost of allowing untrusted self-hosts. Accepted because self-hostability was a core requirement.

## 6. What's decided vs. still open

**Decided (Tech Chats 1 & 2):** client, server, deployment, identity scope, federation shape, provenance schema (per-item, detached JWS), validator sampling (weighted-random, N=7), reputation update math, equivocation slashing.

**Deferred / open:** stake-bonding layer for validators (explicitly optional, not v1 — `05`); COSE/CBOR envelope alternative (only if wire size matters — `04`); concrete schema (`06` is a proposal); offline/local-play identity handling (`02` §4).
