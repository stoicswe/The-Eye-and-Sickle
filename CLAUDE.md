# CLAUDE.md — The Eye and Sickle

Guidance for Claude Code (and humans) working in this repo. Read this first, every session.

---

## What this project is

A **puzzle-centric hacking game** in a surveillance-dystopia setting. Two factions — **The Eye** (the surveillance state) and **The Sickle** (a decentralized resistance). Single-player by default; opt-in, real-loss multiplayer over a **federated, self-hostable** server network. The core loop is a hacking minigame; every surrounding system exists to give that puzzle stakes and consequence.

**Tech stack (decided, end-to-end):** JavaFX multi-window desktop client (one OS window per tool) · Spring Boot + PostgreSQL self-hostable home servers (Docker Compose) · AT Protocol OAuth for identity (authentication only) · opt-in federation with a reputation-weighted validator quorum and cryptographically signed per-item provenance.

## Where the design lives

All design and architecture documentation is under `docs/`. **This is the source of truth — read it before implementing anything.**

- **`docs/design/`** — game systems, economy, world. Start with `docs/design/README.md`.
  - The spine is `00` (vision + invariants) → `01` (resources) → `02` (gates) → `03` (economy). Read those four before touching any system.
- **`docs/architecture/`** — the technical stack. Start with `docs/architecture/README.md` and `00-overview.md`.
- **`docs/client/`** — what the player actually sees: the two theme families (platform-native and the **uOS** story terminal), the visual-language token contract, the Unix terminology + `man`-page teaching layer, tool windows, resource/inventory UI, and accessibility. Start with `docs/client/README.md`. **`client/01-visual-language.md` is a contract** — it names every colour token, primitive and state class; the other client docs cite it and must not redefine its vocabulary.
- **`docs/education/`** — the **curriculum**: the real computing knowledge the game teaches, which concepts, in what order, against which misconceptions, verified against which sources. Start with `docs/education/README.md`. **`education/00-curriculum-and-method.md` is a contract** — it fixes the entry template, the status vocabulary (`real` / `real, simplified` / `game`) and the sequencing rules that the eight domain documents are written against. Keep the boundary straight: `client/04` owns *how* a definition reaches the player, `docs/education/` owns *what it says and whether it is true*, and `client/src/main/resources/terms/**` is the output. Nothing in `docs/education/` is code or read at run time.
- **`docs/design/glossary.md`** — canonical terms **and code-name conventions.** Use these names in code so docs and code stay searchable against each other.
- **`docs/design/15-open-questions.md`** — everything undecided, with a resolution log. Check it before designing; update it when you decide something.

## Established vs. [PROPOSAL] — the most important distinction

Docs are tagged at the top and inline:

- **Established** — decided in the game's design sessions (captured in the project's `ethecoin_design_doc.md`) or in the two technology chats. **Do not change these without explicit direction** — the rest of the system depends on them.
- **[PROPOSAL]** — first-pass design filling a gap the source left open. Chiefly: the **core hacking minigame** (`design/05`), **player-facing multiplayer** (`design/13`), the **world/narrative** (`design/14`), and the **data model** (`architecture/06`). These are safe to change, replace, or reject. When you turn a proposal into a decision, drop the tag and log it in `design/15` §3.

If you're unsure whether something is load-bearing, check whether it's an **invariant** (below).

## The hard invariants — do not violate

From `docs/design/00-vision-and-pillars.md` §4. Each one, if broken, collapses a specific system. If a change would violate one, the change is almost certainly wrong — stop and confirm with the user.

1. **I1** — Compute is never purchasable with ethecoin.
2. **I2** — Ethecoin never buys a ceiling (only breadth: consumables, replacements, horizontal options).
3. **I3** — Every item sits behind exactly one unlock gate (assignment follows the rule in `design/02`, not taste).
4. **I4** — Self-mining is immune to detection/seizure and generates zero heat (it's the income floor).
5. **I5** — Self-mining and bots are online-only; deployed miners are the only offline income (buffer-capped).
6. **I6** — A deployed miner consumes the *host's* compute, not the deployer's.
7. **I7** — Proof-of-skill gates are tier-gated, never count-gated.
8. **I8** — Zero-days are never reliably purchasable/farmable.
9. **I9** — Defending your own rig never generates heat.
10. **I10** — Bots assist, never substitute; a bot never solves the puzzle for the player.
11. **I11** — Bot loss destroys instances + socketed tools, never blueprints.
12. **I12** — Vault capacity scales sub-linearly and is never purchasable.
13. **I13** — Salvage/partial-progress drops are gated on engagement tier.
14. **I14** — Game state never lives in a player's PDS or player-controlled infrastructure — only in the server's Postgres. AT Proto is identity-only.
15. **I15** — No single arbiter decides cross-server adversarial outcomes; trust comes from quorum + provenance.

The two meta-rules behind most of these: **compute is the master scarcity** (never let anything create a compute-buys-compute loop) and **the puzzle is the game** (never let anything skip it wholesale).

## Conventions

- **Terminology:** follow `docs/design/glossary.md`. In particular, **`factionReputation` and `validatorReputation` are different things** — never share a field/column.
- **Doc cross-refs** use relative paths and section anchors (e.g. `docs/design/04-mining.md` §5). Keep them working when you move things.
- **When you make a design decision**, put it in the relevant system doc (the source of truth) and log it in `docs/design/15-open-questions.md` §3 — don't leave the answer only in a chat or a commit message.
- **When you add an item/tool**, follow the checklist in `docs/design/02-unlock-gates.md` §5 (classify the gate, price against `03`, add to the right table + glossary).
- **When you add or change something the game *teaches***, the curriculum entry changes first and the shipped term file follows — never the reverse (`docs/education/00-curriculum-and-method.md` §1.2). One concept gets exactly one entry in exactly one domain; a player who gets two answers stops trusting both. An entry with no `hook` does not belong in the curriculum and an entry with no `transfer` is decoration — both are veto gates, not guidelines. **Never state a real-world fact you have not checked**: a wrong mapping teaches something false, which is worse than teaching nothing, so every claim carries its source and the date in `verified:`.
- **The client is never authoritative** over anything a cheater would forge — server validates (I14/I15).

## Working agreements for Claude Code

- Prefer editing the design docs over inventing undocumented mechanics. If a needed rule doesn't exist, add it as a clearly-marked **[PROPOSAL]** in the right doc and note it in `design/15`, rather than silently deciding it in code.
- The economy numbers (`design/03`, `04`) are calibrated as a set. Changing one means re-checking the tables that depend on it — don't spot-edit a single value.
- Big open design areas (minigame, multiplayer, narrative) are proposals for a reason — surface options to the user rather than hard-committing them in code.

## Repo layout (current)

```
.
├── CLAUDE.md            ← you are here
├── README.md
├── LICENSE
├── pom.xml              ← reactor root; inherits from NOTHING (see below)
├── protocol/            ← eyeandsickle-protocol — wire types + provenance verifier
├── server/              ← eyeandsickle-server   — Spring Boot + Postgres home server
├── solo/                ← eyeandsickle-solo     — offline single-player rules engine + JSON save
├── client/              ← eyeandsickle-client   — JavaFX multi-window desktop client
├── deploy/              ← Dockerfile, docker-compose.yml, .env.example
└── docs/
    ├── design/          ← game systems, economy, world (16 docs + glossary + README)
    ├── architecture/    ← tech stack, identity, federation, crypto (10 docs + README)
    ├── client/          ← what the player sees: themes, UI, terminology, accessibility (8 docs + README)
    └── education/       ← the curriculum: what the game teaches and whether it's true (9 docs + README)
```

**Toolchain:** Java 25 (LTS) target, built with Maven. Spring Boot 4.1 · JavaFX 26 · AtlantaFX 2.1 · Flyway · JUnit 6 · ArchUnit.

### Where does this class go?

- **`solo`** — the **offline single-player runtime**: rules over a JSON save, with no Spring, no driver, no HTTP stack and no thread of its own (its own enforcer rule holds that line). It exists because the client bans the server transitively and because a second Boot JVM is the wrong price for a mode whose appeal is double-click-and-play. ⚠ It is a **second implementation of a subset of the rules** — `solo/Balance.java` cites the design doc for every number, and re-tuning `design/03` means re-reading it. A solo character is **local-only and can never federate**, which is how I14 survives a save file the player can edit.
- **`protocol`** — a record, enum or sealed type that crosses the wire, the provenance verifier, or the secure transport. Nothing else. No thresholds, no prices, no yields, no gate evaluation. If a constant here changed and a player would gain something, it's a balance value and it belongs to the server. Its packages layer one way: `game → provenance → crypto ← channel`.
- **`server`** — anything authoritative: rules, persistence, the ledger, PvP resolution, federation. When in doubt, it goes here.
- **`client`** — rendering and input only. Every view binds to the `GameSession` port and never learns whether it is talking to `solo` in-process or a home server over REST; that is what stops single player drifting into a different game.

`protocol` is named that, and not `common`, on purpose: `common` names no rule, so a game rule can drift in unnoticed. `ArchitectureRulesTest` machine-checks the charter, because prose alone erodes under the constant reasonable-sounding pressure to move "just the gate check" in so the client can predict.

### Build invariants worth not rediscovering

- **The root pom deliberately does not inherit `spring-boot-starter-parent`.** Boot's parent would impose dependency management and plugin config on the JavaFX client too. The server imports the Boot BOM in its own pom instead, confining it to the one module that wants it.
- **`server` must stay a reactor leaf.** `spring-boot:repackage` rewrites its jar into a fat jar that is not resolvable as a normal dependency. If something ever needs server code, split a plain `server-core` jar out below it.
- **`mvn verify` must never require Docker.** Container-backed tests live behind `-Pit`, so a client-only contributor is never blocked.
- **Enforcer rules are load-bearing, not decoration.** The client's ban on Spring/server is Invariant I14 made mechanical. Verified to actually fire.
- **Boot 4 split `spring-boot-autoconfigure` into per-technology modules.** Depending on a raw library (e.g. `flyway-core`) instead of its starter gives you the classes without the auto-configuration: green build, dead config, feature silently absent. If you add a Boot integration, use its **starter**.
- **Transport security is `[PROPOSAL]` and needs a cryptographer.** `docs/architecture/07-transport-security.md` §6 T-1. It is a hand-rolled Noise-IK-shaped protocol — reviewed patterns, unreviewed code. Do not let it guard a live federation until someone qualified has read it.
- **Timestamps bind through `persistence/Timestamps.at(Instant)`, never a bare `Instant`.** The Postgres driver refuses a raw `java.time.Instant` ("Can't infer the SQL type"); `Row.instant()` reads them back as `OffsetDateTime`, and `Timestamps` is the matching write side. Unit tests with fakes cannot catch a raw bind — only the `-Pit` repository tests do.

### Server implementation status

The **Established spine is implemented and boots** (`ServerContextLoadsIT` starts the full context against a real Postgres): schema (Flyway core + federation), JdbcClient data layer, AT-Proto-auth + allowlist, compute ledger, ethecoin/public ledger + gates, provenance persistence & ingress verification, validator quorum (A-Res sampling + AIMD reputation), peer discovery, and the `Content-Digest` checksum filter. ~168 main + ~117 test classes; `mvn verify` and `mvn -Pit verify` both green.

What is **stubbed at documented seams** (see `docs/design/15-open-questions.md` W-1…W-6): external DID→key resolution over the network, schematic ownership, gated-offering content, faction-tool forfeiture, and a production AT Proto provider — each a safe `@ConditionalOnMissingBean` default a real implementation supersedes. REST controllers exist only where a slice reached them; most surface is service-level. `[PROPOSAL]` game systems (minigame `05`, bots `10`, narrative `14`) are deliberately not implemented.

### Commands

```bash
mvn verify                          # build + unit tests, no Docker needed
mvn install -DskipTests             # publish protocol locally, needed before javafx:run
mvn -pl client javafx:run           # launch the client
mvn -Pit verify                     # + Testcontainers integration tests (needs Docker)
mvn -Pquality spotless:apply        # format
```

The client **runs offline out of the box**: `mvn install -DskipTests && mvn -pl client javafx:run` opens a
playable solo game with no network, account or database. Sixteen tool windows, two theme families, a
shell with real pipelines and globs, and a 21-page offline manual parsed from `client/src/main/resources/
.../terms/`. **The single-window layout is the default** as of 2026-07-25: one window, tools as tabs, the compute
strip as chrome. This inverts `docs/architecture/01` §1's Established "multi-window is the default"
— changed on explicit direction, logged in `docs/design/15-open-questions.md` §3, and noted in
`docs/client/05` §5.1 and `07` §2.3. The multi-window desk is unchanged and one setting away
(`dockedLayout: false`, Settings → Layout, or `dock` in the terminal). `docs/client/07` §2.3 requires
the docked layout to lose no functionality, and a test asserts every window is reachable in it. Its profile (settings, window geometry, save)
lives in the platform's conventional directory — `~/Library/Application Support/The Eye and Sickle` on
macOS, `%APPDATA%` on Windows, `$XDG_DATA_HOME` on Linux — and `-Deyeandsickle.profile=<dir>` relocates it.

**Running from an IDE — read this before debugging a missing-JavaFX error.** Start the client through
`io.github.stoicswe.eyeandsickle.client.Launcher`, never through `EyeAndSickleClient`. A main class that
extends `javafx.application.Application` makes the JVM look for JavaFX on the **module path** before
`main` runs, and a classpath launch then dies with:

```
Error: JavaFX runtime components are missing, and are required to run this application
```

That message names the wrong problem — the runtime is present, the launcher just refused to look for it
on the classpath. `Launcher` does not extend `Application`, so the toolkit starts from inside `main` with
the classpath already established. `EyeAndSickleClient` has **no `main` of its own** precisely so an IDE
cannot offer the launch that cannot work, and `.run/` ships IntelliJ configurations pointing at the right
one.

⚠ One VM flag differs by launch mode and is easy to get backwards. JavaFX's `System::load` needs a
native-access grant on JDK 24+, and **which** module you grant depends on how it started: a module-path
launch (`mvn javafx:run`) wants `--enable-native-access=javafx.graphics`, a classpath launch (any IDE)
wants `--enable-native-access=ALL-UNNAMED`. The module form from the classpath prints
`WARNING: Unknown module: javafx.graphics` and grants nothing. Verified on JDK 25 / JavaFX 26.0.2 —
`client/pom.xml` and `.run/` deliberately differ for this reason.

Client packaging (jlink/jpackage) is **not wired up** — `jlink` cannot link the current graph (an automatic module in the dependency tree). See the closing comment in `client/pom.xml` before attempting it.

Keep this file's stack summary, invariant list, and layout in sync with reality as the code grows.
