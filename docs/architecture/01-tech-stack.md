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

> ⚠️ **SUPERSEDED 2026-07-26 — AtlantaFX and `Stage`-per-tool are both cancelled.**
> `../design/ui-design-language.md` §0 reverses two decisions this section states as Established, and
> that document is tagged **decided**. Its argument: native theming puts real macOS traffic lights and
> Windows title bars around the game, and *"the entire aesthetic depends on the player never seeing
> their own operating system."*
>
> **What the client actually is now:**
> - **One `Stage`**, `StageStyle.UNDECORATED`, containing an in-game window manager the client draws
>   itself (`client/.../ui/chrome/DeskManager`) — drag, focus, z-order, minimise, maximise, close,
>   resize, snap-to-grid and edge tiling. Multi-window survives in §0 only as an opt-in **multi-monitor**
>   feature, which is not built.
> - **No AtlantaFX.** One hand-written component stylesheet (`ui/theme.css`) plus per-theme palette
>   overlays of ~40 lines. Modena remains the user-agent sheet underneath, overridden per control;
>   replacing the user-agent sheet outright would mean owning every control JavaFX ships, including
>   the dozens this client never instantiates.
>
> This also retires the 2026-07-25 amendment that made the *docked* layout the default — the deck
> replaces both layouts rather than choosing between them, and is strictly more capable than either.
> `../client/07` §2.3's no-loss contract carries over and is now structural rather than maintained by
> hand: the compute readout is a cell in the top status strip, which is chrome, so pillar C2 has no
> z-order to lose and no tab to hide behind. Reasoning logged in `../design/15-open-questions.md` §3;
> `../design/ui-design-language.md` §12 records what following it cost.
>
> **Still true from the paragraph above:** JavaFX, the JVM's three-platform reach, and the rule that
> the client is never authoritative. The window model changed; the layering did not.

> **[PROPOSAL]** Packaging: `jlink`/`jpackage` to ship a self-contained runtime image per platform (no "install a JRE first" friction), keeping the "lightweight" promise at the distribution layer. Confirm at build-tooling time.

## 2. Home server — Spring Boot + embedded H2

**Decision:** a **Spring Boot** service backed by **embedded H2**, self-hostable Minecraft-style with
**allowlists**. Docker Compose is offered but is no longer required.

> ⚠ **AMENDED 2026-08-02 — this said PostgreSQL, and PostgreSQL is gone.** The reasoning below for a
> relational, transactional store is unchanged and is why H2 was chosen over a document or key-value
> store. What changed is that the database is now *inside the server process*: a self-hoster needs a
> JVM and nothing else — no database to install, no container to run, no connection string to get
> wrong. That is a direct win for "anyone can run a server", which `03` §1 calls a core requirement
> rather than a nicety.
>
> ⚠ **The port was not free and the cost is recorded in code, not just here.** PostgreSQL's PL/pgSQL
> enforcement — the append-only ledger and provenance guards, and the anti-rollback guard `08` §2
> relies on — became **Java triggers** (`persistence/AppendOnlyTrigger`, `MonotonicSequenceTrigger`).
> They still fire *inside* the engine on every write, so the guarantee survives; but they are our code
> now and earn the same scrutiny the originals had. The DID shape check moved the other way and came
> out **better**: it is an H2 ALIAS onto `Did.isWellFormed`, so the constraint and the Java validator
> are one implementation where they used to be two copies with a warning to keep them in step.
>
> ⚠ **H2 is a COMPILE dependency, not a driver.** The schema's enforcement is written against its API,
> so the server's code is coupled to H2 rather than merely configured for it.

### Why

- **Spring Boot** — batteries-included JVM server framework; same language ecosystem as the client (Java), so the team maintains one toolchain. Mature OAuth support helps the AT Proto integration (`02`).
- **Embedded H2** — the authoritative store for **all game state** (Invariant I14): player inventories, EC balances, rig configs, the public ledger, deployed-miner records, home-server-local PvP resolution. Relational fits the heavily-cross-referenced item/economy model (`06`), and it keeps one transaction spanning the rules engine's state and the ledger — a split store could produce a balance and a ledger that disagree, which `../design/04-mining.md` §3.1 teaches players to read as evidence of an intruder.
- **Docker Compose** — still offered for one-command self-hosting, but **optional** now: with the database embedded, `java -jar` is a complete server. The compose file no longer has a `db` service, and the game state lives in a single mounted volume that should be backed up like a save file, because that is what it now is.
- **Allowlists** — Minecraft-style access control so a self-hosted server is private-by-default; the operator chooses who joins. Pairs with the opt-in federation model (`03`) — private servers are the single-player/friends experience, federated public servers are the real-loss multiplayer experience (`../design/13` §5).

### Server responsibilities

- Own and validate all game state for its players.
- Resolve **home-server-local** PvP (raids, miner cracks) directly in its own database — no federation needed for these (`../design/13` §3).
- Participate in federation when opted in: serve non-adversarial directory data, act as a validator when sampled (`05`), verify item provenance on cross-server transfers (`04`).
- Enforce the public ledger (`../design/01` §2.2) as queryable, **append-only** relational data — the append-only part is a database trigger, not a convention.

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
