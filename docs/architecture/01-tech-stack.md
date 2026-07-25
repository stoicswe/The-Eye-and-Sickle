# 01 — Tech Stack: Client, Server, Deployment

**Status:** Established (Tech Chat 1)
**Depends on:** `00-overview.md`
**Depended on by:** `06-data-model.md`, `../design/05-hacking-minigame.md` §5 (multi-window presentation)

---

## 1. Client — Java + JavaFX

**Decision:** Java + **JavaFX**, with **AtlantaFX** for native-OS theming, and a **multi-window** architecture using a separate `Stage` instance per tool.

### Why JavaFX (against the constraints given)

- *"very lightweight"* — a windowing toolkit, not a game engine. The game's surface is terminals, network maps, and dashboards — UI, not a rendered 3D/2D scene. JavaFX models that directly with far less weight than Unity/Godot/Unreal.
- *"multi-window (a window per tool)"* — JavaFX's `Stage` is a top-level OS window; one `Stage` per tool gives the literal "operator's desk" layout the design wants (`../design/05` §5). This is a *native* capability, not a fight against the framework.
- *"cross-platform macOS / Linux / Windows so all players can join"* — the JVM runs everywhere; one codebase, three platforms. **AtlantaFX** supplies modern native-feeling themes so it doesn't look like a 2005 Swing app on any of them.

### Client responsibilities

- Render the tool windows (map, terminal, rig monitor, recon, etc.).
- Run the client side of the core hacking minigame (`../design/05`).
- Hold the AT Proto OAuth session and present the DID to the server (`02`).
- **Never** be authoritative over adversarial state — the server owns item ownership, EC balances, duel outcomes. The client is a view + input layer; anything a cheating client could lie about is server-validated. (This is the client-side face of Invariant I14/I15.)

> **[PROPOSAL]** Window management needs an accessibility fallback: a single-window / docked layout for players who can't manage many OS windows under time pressure (`../design/05` §5, flagged for `design:accessibility-review`). Multi-window is the default and the fantasy; it must not be the *only* option.

> **[PROPOSAL]** Packaging: `jlink`/`jpackage` to ship a self-contained runtime image per platform (no "install a JRE first" friction), keeping the "lightweight" promise at the distribution layer. Confirm at build-tooling time.

## 2. Home server — Spring Boot + PostgreSQL

**Decision:** a **Spring Boot** service backed by **PostgreSQL**, deployed via **Docker Compose**, self-hostable Minecraft-style with **allowlists**.

### Why

- **Spring Boot** — batteries-included JVM server framework; same language ecosystem as the client (Java), so the team maintains one toolchain. Mature OAuth support helps the AT Proto integration (`02`).
- **PostgreSQL** — the authoritative store for **all game state** (Invariant I14): player inventories, EC balances, rig configs, the public ledger, deployed-miner records, home-server-local PvP resolution. Relational fits the heavily-cross-referenced item/economy model (`06`).
- **Docker Compose** — one-command self-hosting (`docker compose up`), the "anyone can run a server" requirement. Bundles the Spring Boot app + Postgres + any support services.
- **Allowlists** — Minecraft-style access control so a self-hosted server is private-by-default; the operator chooses who joins. Pairs with the opt-in federation model (`03`) — private servers are the single-player/friends experience, federated public servers are the real-loss multiplayer experience (`../design/13` §5).

### Server responsibilities

- Own and validate all game state for its players.
- Resolve **home-server-local** PvP (raids, miner cracks) directly in Postgres — no federation needed for these (`../design/13` §3).
- Participate in federation when opted in: serve non-adversarial directory data, act as a validator when sampled (`05`), verify item provenance on cross-server transfers (`04`).
- Enforce the public ledger (`../design/01` §2.2) as queryable Postgres data.

## 3. Deployment & topology

- **Self-hosted home servers** are the unit of deployment. Each is a Docker Compose stack.
- **No central game server exists** — by design (Invariant I15). There is at most a federation *directory* (opt-in, `03`), which is a low-trust index, not an authority.
- **Cross-platform clients** connect to whichever home server(s) they play on; identity (`02`) makes the same player recognizable across them.

## 4. Language/ecosystem summary

Everything is **JVM/Java**: client (JavaFX), server (Spring Boot). One language, one build ecosystem, three target OSes via the JVM. This is a deliberate small-team choice — no context-switching between a client language and a server language, and the item/provenance types can potentially be shared as a common Java module between client and server.

> **[PROPOSAL]** Consider a shared `common` Java module for the wire types (provenance records `04`, item DTOs, duel messages) imported by both client and server, so the schema can't drift between them. Standard practice; confirm at project-scaffold time.

## 5. What this doc deliberately does not decide

- Build tooling (Gradle vs. Maven), test framework, CI — not specified in the source; pick at scaffold time.
- Networking protocol between client and server (REST/WebSocket/custom) — not in the source. WebSocket is the likely fit for the live, event-driven engagements (breaches, raid alerts, join notifications), with REST for CRUD; **[PROPOSAL]**, confirm at design time.
- ORM/data-access (JPA/Hibernate vs. jOOQ vs. plain JDBC) — deferred to `06`.
