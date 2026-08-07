# CLAUDE.md — The Eye and Sickle

Guidance for Claude Code (and humans) working in this repo. Read this first, every session.

---

## What this project is

A **puzzle-centric hacking game** in a surveillance-dystopia setting. Two factions — **The Eye** (the surveillance state) and **The Sickle** (a decentralized resistance). Single-player by default; opt-in, real-loss multiplayer over a **federated, self-hostable** server network. The core loop is a hacking minigame; every surrounding system exists to give that puzzle stakes and consequence.

**Tech stack (decided, end-to-end):** JavaFX desktop client — **one undecorated window containing an in-game window manager the client draws itself** · Spring Boot + **embedded H2** self-hostable home servers (Docker Compose optional) · AT Protocol OAuth for identity (authentication only) · opt-in federation with a reputation-weighted validator quorum and cryptographically signed per-item provenance.

## Where the design lives

All design and architecture documentation is under `docs/`. **This is the source of truth — read it before implementing anything.**

- **`docs/design/`** — game systems, economy, world. Start with `docs/design/README.md`.
  - The spine is `00` (vision + invariants) → `01` (resources) → `02` (gates) → `03` (economy). Read those four before touching any system.
- **`docs/architecture/`** — the technical stack. Start with `docs/architecture/README.md` and `00-overview.md`.
- **`docs/client/`** — what the player actually sees: the two theme families (platform-native and the **uOS** story terminal), the visual-language token contract, the Unix terminology + `man`-page teaching layer, tool windows, resource/inventory UI, and accessibility. Start with `docs/client/README.md`. **`client/01-visual-language.md` is a contract** — it names every colour token, primitive and state class; the other client docs cite it and must not redefine its vocabulary.
- **`docs/education/`** — the **curriculum**: the real computing knowledge the game teaches, which concepts, in what order, against which misconceptions, verified against which sources. Start with `docs/education/README.md`. **`education/00-curriculum-and-method.md` is a contract** — it fixes the entry template, the status vocabulary (`real` / `real, simplified` / `game`) and the sequencing rules that the eight domain documents are written against. Keep the boundary straight: `client/04` owns *how* a definition reaches the player, `docs/education/` owns *what it says and whether it is true*, and `client/src/main/resources/terms/**` is the output. Nothing in `docs/education/` is code or read at run time.
- **`docs/design/ui-design-language.md`** — **a contract, and the newest one.** It fixes the palette, the type roles, the geometry, the component catalog, the motion rules and a build-blocking rejection list (§9). Its §0 **cancels AtlantaFX and the `Stage`-per-tool model** that `architecture/01` had as Established — read §0 and §12 before touching anything visual. §10's acceptance criteria are machine-checked by `UiContractTest`; §11 records what is still open.
- **`docs/design/glossary.md`** — canonical terms **and code-name conventions.** Use these names in code so docs and code stay searchable against each other.
- **`docs/design/15-open-questions.md`** — everything undecided, with a resolution log. Check it before designing; update it when you decide something.

## Established vs. [PROPOSAL] — the most important distinction

Docs are tagged at the top and inline:

- **Established** — decided in the game's design sessions (captured in the project's `ethecoin_design_doc.md`) or in the two technology chats. **Do not change these without explicit direction** — the rest of the system depends on them.
- **[PROPOSAL]** — first-pass design filling a gap the source left open. Chiefly: the **core hacking minigame** (`design/05`), **player-facing multiplayer** (`design/13`), the **world/narrative** (`design/14`), and the **data model** (`architecture/06`). These are safe to change, replace, or reject. When you turn a proposal into a decision, drop the tag and log it in `design/15` §3.

If you're unsure whether something is load-bearing, check whether it's an **invariant** (below).

## The hard invariants — do not violate

From `docs/design/00-vision-and-pillars.md` §4. Each one, if broken, collapses a specific system. If a change would violate one, the change is almost certainly wrong — stop and confirm with the user.

1. **I1** — Compute is never purchasable with ethecoin — ⚠ **AMENDED 2026-08-06: except the compute ladder's FIRST rung** (24→32, `Balance.COMPUTE_32_PRICE`). One rung cannot close the mine→buy→mine-faster→buy-more loop, because 32→48 is not for sale at any price. Held to one rung by `ComputeLadderTest` and `ShortcutsTest`, not by prose.
2. **I2** — Ethecoin never buys a ceiling (only breadth: consumables, replacements, horizontal options).
3. **I3** — Every item sits behind exactly one unlock gate (assignment follows the rule in `design/02`, not taste).
4. **I4** — Self-mining is immune to detection/seizure and generates zero heat (it's the income floor).
5. **I5** — Self-mining and bots stop a bounded time after the client closes (`Balance.OFFLINE_MINING_HOURS`); *all* offline income is capped and never proportional to absence. Amended 2026-07-29 — see `design/15` §3.
6. **I6** — A deployed miner consumes the *host's* compute, not the deployer's.
7. **I7** — Proof-of-skill gates are tier-gated, never count-gated.
8. **I8** — Zero-days are never reliably purchasable/farmable.
9. **I9** — Defending your own rig never generates heat.
10. **I10** — Bots assist, never substitute; a bot never solves the puzzle for the player.
11. **I11** — Bot loss destroys instances + socketed tools, never blueprints.
12. **I12** — Vault capacity scales sub-linearly and is never purchasable.
13. **I13** — Salvage/partial-progress drops are gated on engagement tier.
14. **I14** — Game state never lives in a player's PDS or player-controlled infrastructure — only in the server's **own database**. AT Proto is identity-only. (Wording widened 2026-08-02: the store is embedded H2, not PostgreSQL. The invariant was never about the vendor — it is about *whose* machine holds the state.)
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
├── server/              ← eyeandsickle-server   — Spring Boot + embedded H2 home server
├── engine/              ← eyeandsickle-engine   — THE rules engine, + the shared save system
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

- **`solo`** — **THE rules engine**, despite the module name. One implementation of the mechanics, driven two ways: the client drives it in process for single player, a home server drives it for LAN and federated play. `Balance.java` exists in exactly one place and a re-tune lands in every mode at once. ⚠ **AMENDED TWICE, and both amendments deleted a warning that had been true.** 2026-08-02: it stopped being "a second implementation of a subset of the rules" when the server was pointed at this engine instead of growing its own. 2026-08-03: it stopped being "no framework, no driver, no port" when the **save system was unified** — it carries H2, spring-jdbc and Flyway now.
  ⚠ **NO LEGACY-SAVE MACHINERY AT ALL (2026-08-03).** No build has shipped, so no save, settings file
  or database predates the current one, and everything that existed to read an older format is gone:
  the JSON save importer, the 26 `@JsonAlias` pre-wei keys and the `moneySchema` rescale, the ten
  pre-per-character settings hooks, `retuneNetworkHashrate`, and the V3–V6 patch migrations (folded
  into V2). ⚠ **Money fields keep their zero initialisers** — that is a null-safety rule, not a
  migration one, and `ContributionState.creditedWei` threw an NPE on the login screen for want of it.
  ⚠ **`SaveStore.format` / `CURRENT_FORMAT` also stay**: refusing a save from a *newer* build is
  forward compatibility, which is the opposite of legacy support.
  ⚠ **The moment a build ships, this stops being true**, and the first thing to come back is the
  `theBaselineIsNotRewritten` guard in `SchemaVocabularyTest` — it forbade exactly the V2 edit that
  the squash performs, and it was right to, for a deployed system.
  ⚠ **ONE `SaveStore`, and the pair it replaced was not earning its keep.** `character_game_state.state` is `text` holding the engine's own JSON document — deliberately opaque to SQL (V7's comment: a query reaching inside would be a *second* way to read game state, able to disagree with the first, invisibly) — so `FileSaveStore` and `JdbcSaveStore` were **writing identical bytes**. The split bought two atomicity stories, two failure modes and two places for the save format to drift, and nothing else. `JsonSaveImport` is what is left of the file store: a **reader**, so it cannot become a second store by accident.
  ⚠ **`engine/EngineSessions` is the one engine host** — load → tick → act → persist, one engine per character, one request at a time. It was the server's, taking a `JdbcClient` and a `PlayerRepository`; it takes two *functions* now and needs neither Spring nor JDBC, which is what lets the client use the same one. `ServerEngineSessions` is the server's 40-line wiring.
  ⚠ **What is still banned is what matters**: no Spring Boot, no `spring-web`, no server module. A driver is a place to put bytes; a Boot context with a web layer is a second server, and a second Boot JVM is the wrong price for a mode whose appeal is double-click-and-play.
  ⚠ **I14 is untouched by any of it, and here is the whole argument, because it is easy to get backwards.** I14 governs whose machine holds state that *others* must trust. A local H2 file is **exactly as editable as JSON was** — merely less pleasant to read — so the storage format never protected anything and moving between formats cannot weaken it. Nothing may ever be trusted on the grounds that it came out of a database.
  What actually holds the line is that **a solo character has no route to a server**, and that is mechanical in two different places:
  - **A solo character has no DID and no `players` row.** Going online means a character created *on* a home server (`CharacterService`, which enforces the allowlist and the per-account cap) — it starts empty. There is no exporter, and `Quarantine.refuseIfLan` guards the one export path that exists (`CharacterExportService`) against LAN identities.
  - ⚠ **And a hand-made save cannot be smuggled in, because core's `V8` makes `character_game_state.character_id` a foreign key to `players`.** Engine state on a server is not free-standing: a forged row has nowhere to land until an authorised player exists. `SchemaIT.engineStateRequiresAPlayer` pins it, verified by removing the constraint first.
  ⚠ **The engine tier deliberately does NOT carry that constraint** (V7 has no FK), and the asymmetry *is* the design: the engine's state has the same shape everywhere, and only the authority tier says who may own some.

- **`protocol`** — a record, enum or sealed type that crosses the wire, the provenance verifier, **AT Proto identity resolution**, or the secure transport. Nothing else. No thresholds, no prices, no yields, no gate evaluation. If a constant here changed and a player would gain something, it's a balance value and it belongs to the server. Its packages layer one way: `game → provenance → crypto ← channel`, with `identity` above `crypto` and below `provenance`.
  ⚠ **The charter was two items until 2026-08-02 and is now three.** `identity` was admitted because the verifier already here has always been missing its other half — `SigningKeyDirectory` describes turning a `did:plc:xxx#key1` into a key and resolves nothing (**W-1**) — and because `architecture/04` §6.2 requires that verifier to run **client-side and offline** while `architecture/10` §1 requires the same resolution server-side. Either module owning it means the other copies it, and **two SSRF denylists is one denylist that is wrong**.
  ⚠ **`identity` is the ONLY package here that may open a socket.** Before it, this module did no I/O at all, and the reasons for that austerity (jlink candidate, shared by two very different runtimes) are unchanged. `ArchitectureRulesTest` confines `java.net.http` and `javax.naming` to `identity` and refuses them everywhere else — a wire type that phones home to fill in a field is authoritative-state-by-the-back-door, which is **I14**. It adds no dependency: `HttpClient` and Jackson 3 were already there.
  ⚠ **The "no `*Service`" name check is blunt on purpose and it fires on innocent names.** A DID document's own word for an endpoint is `service`; `DidDocument.ServiceEndpoint` is renamed for the rule rather than the rule being given its first exception.
  ⚠ **`HardenedHttpClient` drives a SOCKET, not `java.net.http`, and that is not a style choice.** Every URL it fetches is attacker-chosen, so the SSRF denylist is load-bearing — and a denylist applied to a *hostname* is defeated by **DNS rebinding**, because `java.net.http` re-resolves the name when it connects. It resolves once, checks every address, connects to **the address**, then layers TLS with the four-argument `SSLSocketFactory.createSocket(Socket, host, port, autoClose)` so SNI and certificate verification use the real name against a pinned connection. ⚠ `setEndpointIdentificationAlgorithm("HTTPS")` must be set — a raw `SSLSocket` validates the chain but **not** the hostname, which makes pinning worse than useless. The cost is a hand-written HTTP/1.1 reader, split out as `HttpResponseReader` so the risky part is testable without a TLS server.
  ⚠ **secp256k1 IS RUNTIME-DEPENDENT AND THREE API LAYERS LIE ABOUT IT.** Measured on two JDK 26 builds on one machine: **Homebrew OpenJDK (SunEC) cannot verify it; IBM Semeru (OpenJ9) can.** On the JVM that cannot, `AlgorithmParameters` resolves the curve, `KeyFactory` builds a key, and **`Signature.initVerify` succeeds** — only `verify()` fails, on the request path. So every cheap availability probe returns true in exactly the case that matters. `MultibaseKey.secp256k1Available()` probes `verify()`, and `decode` refuses an unusable curve up front. ⚠ **Most `did:plc` accounts sign with secp256k1**, so this blocks service-auth JWT verification on stock OpenJDK; provenance (Ed25519) and DPoP (P-256) are unaffected. ⚠ Also: an EC key must be built from the **named-curve** `ECParameterSpec`, never a hand-built one — SunEC matches curves by identity, so a numerically identical spec is a different curve and fails at use.
- **`server`** — anything authoritative: rules, persistence, the ledger, PvP resolution, federation. When in doubt, it goes here.
- **`client`** — rendering and input only. Every view binds to the `GameSession` port and never learns whether it is talking to `solo` in-process or a home server over REST; that is what stops single player drifting into a different game. Inside it, `ui/` is the visual layer and obeys one split: **colours live in `ui/theme.css` and nowhere else; sizes, spacings and durations live in `ui/UiTokens.java` and nowhere else.** JavaFX looked-up values are colour-only (measured — V-1), so there is no third option. `ui/chrome/` is the window manager, `ui/widgets/` the component catalog, `view/` the tools that fill the panels.

`protocol` is named that, and not `common`, on purpose: `common` names no rule, so a game rule can drift in unnoticed. `ArchitectureRulesTest` machine-checks the charter, because prose alone erodes under the constant reasonable-sounding pressure to move "just the gate check" in so the client can predict.

### Build invariants worth not rediscovering

- **The root pom deliberately does not inherit `spring-boot-starter-parent`.** Boot's parent would impose dependency management and plugin config on the JavaFX client too. The server imports the Boot BOM in its own pom instead, confining it to the one module that wants it.
- **`server` must stay a reactor leaf.** `spring-boot:repackage` rewrites its jar into a fat jar that is not resolvable as a normal dependency. If something ever needs server code, split a plain `server-core` jar out below it.
- **`mvn verify` must never require Docker.** Slow schema-backed tests live behind `-Pit`, so a client-only contributor is never blocked. ⚠ **`-Pit` no longer needs Docker either** (2026-08-02) — it was Testcontainers because "a real database" meant a PostgreSQL container, and the database is now embedded H2, so all 203 run wherever the build does. The split survives because those tests migrate a schema and truncate it between every test, which does not belong in the fast loop.
- ⚠ **H2 IS PINNED TO 2.3.232, BELOW WHAT THE BOOT BOM MANAGES, AND THE PIN IS LOAD-BEARING.** Boot 4.1 brings **2.4.240**, which has a regression ([h2database#4291](https://github.com/h2database/h2database/issues/4291), dup #4292/#4320/#4342) that evaluates a CHECK constraint against the session that **parsed** it. Flyway applies the migrations on its own connection and closes it, so from that moment every insert into `players`, `items`, `ledger_transactions`, `breach_resolutions` and `provenance_records` fails.
  ⚠ **The error names the wrong thing.** SQLState **23514** is "check constraint *invalid*", not 23513 "*violated*" — the constraint did not reject the row, it could not run — and H2 **drops the cause**, so the message accuses a constraint the row satisfies. Not confined to `IN` lists: `a = 'x' OR a = 'y'` and `n IN (1,2,3)` fail identically. ⚠ **A file database re-parses on open**, so it looks intermittent — broken from migration until a restart, fine after; with a pool it is worse, working until Hikari evicts Flyway's connection on `idleTimeout`. 2.4.240 is the newest release and the fix is unreleased. **Remove the pin when a later H2 ships it**, and re-run `SchemaIT`.
- ⚠ **JSON parameters take `FORMAT JSON`, never a cast — `Jsonb.CAST`.** `CAST('{"a":1}' AS JSON)` is legal, silent, and **wrong**: it produces the JSON *string* `"{\"a\":1}"`, not the object. Nothing fails at the cast; the column's shape constraint fails several frames later naming `ck_items_attrs_object` against a document the caller can see is an object. Forty rows failed this way during the port.
- ⚠ **`~` runs a JAVA regex, so POSIX bracket expressions are not available.** `[^[:space:]]` is read as "not one of `:` `s` `p` `a` `c` `e`", so `ck_federation_peers_endpoint` refused **every** URL rather than only malformed ones — parsed, applied, looked right. Use `\S`.
- ⚠ **H2 has no partial index, and dropping a `WHERE` from a UNIQUE one changes the RULE.** `uq_flagged_servers_active` was `... WHERE cleared_at IS NULL` — "one *live* flag per server". Without it, a server cleared could never be flagged again. Expressed now as a generated column that is the DID while live and NULL once cleared, since a unique index treats NULLs as distinct. ⚠ Non-unique partial indexes are only a performance change; the unique one was the only semantic loss.
- ⚠ **`ON CONFLICT DO NOTHING` is `MERGE` with NO `WHEN MATCHED` branch. `MERGE ... KEY (...)` IS NOT IT** — that is an upsert, and it silently overwrites the row that was supposed to be left alone (measured). The 0-row return is a contract: `Mutations.requireInserted` and `AllowlistRepository.insertIfAbsent` both read it. `INSERT ... RETURNING` becomes `SELECT ... FROM FINAL TABLE (INSERT ...)`, which keeps the one-statement read-back rather than splitting into an INSERT plus a racy SELECT.
- ⚠ **Two unannotated constructors stop the whole Spring context**, with "No default constructor found" — which names neither the class's problem nor the fix. `EngineSessions` and `GameSessionService` each had a convenience overload; both now take the application's `@Primary Clock` bean and have exactly one.
- ⚠ **A file H2 with NO POOL closes between every statement, and that LOSES WRITES.** `JdbcDataSource` opens a connection per statement and closes it, so with H2's default `DB_CLOSE_DELAY=0` the database is opened and closed between operations — measured on 2.3.232: a row written through one call was visible to the next read and **absent from the one after that**, with nothing in between but a loop. It looks exactly like a phantom deletion and is not one; the database is not the same database twice running. `LocalDatabase` sets **`DB_CLOSE_DELAY=-1`**. The server is unaffected because Hikari holds connections.
- ⚠ **`AUTO_SERVER=TRUE` and `DB_CLOSE_ON_EXIT=FALSE` ARE MUTUALLY EXCLUSIVE** — a hard refusal at connect time, SQLState 50100. `application.yml` shipped **both** from the Postgres migration until 2026-08-03, so the boot jar could not open its own database; no test caught it because every test used an in-memory URL. `DB_CLOSE_ON_EXIT=FALSE` is the one kept.
- ⚠ **`bytea` maps to `binary varying` and must never be cast through a character type.** `CAST(:key AS varchar)` UTF-8-decodes it: every byte ≥ 0x80 becomes U+FFFD and a 32-byte key reads back as 64 bytes of replacement characters, with no error anywhere. The first symptom would be a federation peer whose signatures never verify.
- **Enforcer rules are load-bearing, not decoration.** The client's ban on Spring/server is Invariant I14 made mechanical. Verified to actually fire.
- **Boot 4 split `spring-boot-autoconfigure` into per-technology modules.** Depending on a raw library (e.g. `flyway-core`) instead of its starter gives you the classes without the auto-configuration: green build, dead config, feature silently absent. If you add a Boot integration, use its **starter**.
- **Transport security is `[PROPOSAL]` and needs a cryptographer.** `docs/architecture/07-transport-security.md` §6 T-1. It is a hand-rolled Noise-IK-shaped protocol — reviewed patterns, unreviewed code. Do not let it guard a live federation until someone qualified has read it.
- **Timestamps bind through `persistence/Timestamps.at(Instant)`, never a bare `Instant`.** This began as a Postgres driver rule ("Can't infer the SQL type") and survives as a house rule: `Row.instant()` reads them back as `OffsetDateTime`, and `Timestamps` is the matching write side, so both directions have one spelling. Unit tests with fakes cannot catch a raw bind — only the `-Pit` repository tests do.

### API documentation

`docs/architecture/14-api-documentation.md`. springdoc-openapi generates an **OpenAPI 3.1** spec from
the controllers; `server/web/OpenApiConfiguration` supplies the metadata. ⚠ **Both surfaces ship OFF**
(`EYEANDSICKLE_API_DOCS`, `EYEANDSICKLE_SWAGGER_UI`) — the server is closed by default and an
interactive request builder is not something to enable on somebody else's box. ⚠ **The spec is
checked on every `-Pit` run**, both directions, against `RequestMappingHandlerMapping` rather than a
hand-kept list; negative-tested by hiding a controller.

- ⚠ **springdoc 3.x, NOT 2.x** — 2.x targets Boot 3 and fails at runtime on Boot 4, not at resolution.
- ⚠ **THE REST API CURRENTLY ANSWERS 401 TO EVERYTHING.** `spring-boot-starter-security` was on the
  classpath with **no `SecurityFilterChain` anywhere**, so Boot's `anyRequest().authenticated()`
  applied to all 31 endpoints. **No test in the server module had ever made an HTTP request to its own
  controllers**, so nothing caught it. `ApiDocsSecurityConfiguration` opens the doc paths only and
  deliberately leaves the rest — the auth model is **API-1**/CL-8, not a documentation task.
- ⚠ **`@DynamicPropertySource` CANNOT SET `spring.profiles.active`** — profiles resolve before dynamic
  properties are contributed, so it is accepted, ignored, and reported only as "No active profile set"
  in a passing test's log. `ServerContextLoadsIT` claimed to exercise the federation profile and never
  had. Use `@ActiveProfiles`.

### Server implementation status

The **Established spine is implemented and boots** (`ServerContextLoadsIT` starts the full context against a real database): schema (Flyway core + federation), JdbcClient data layer, AT-Proto-auth + allowlist, compute ledger, ethecoin/public ledger + gates, provenance persistence & ingress verification, validator quorum (A-Res sampling + AIMD reputation), peer discovery, and the `Content-Digest` checksum filter. ~168 main + ~117 test classes; `mvn verify` and `mvn -Pit verify` both green (203 integration tests, no Docker).

What is **stubbed at documented seams** (see `docs/design/15-open-questions.md` W-1…W-6): external DID→key resolution over the network, schematic ownership, gated-offering content, faction-tool forfeiture, and a production AT Proto provider — each a safe `@ConditionalOnMissingBean` default a real implementation supersedes. REST controllers exist only where a slice reached them; most surface is service-level. `[PROPOSAL]` game systems (minigame `05`, bots `10`, narrative `14`) are deliberately not implemented.

### Releasing

`.github/workflows/build.yml` builds and tests every push and PR. **Pushing a `v*` tag additionally publishes a GitHub Release** carrying the five client jars, three native installers, the server's `-boot` jar, and a `SHA256SUMS` covering all of them:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

Files are renamed from the POM version to the tag (`the-eye-and-sickle-0.2.0-mac-aarch64.jar`), so the tag never has to agree with a `pom.xml` nobody remembered to bump. A tag containing a hyphen (`v1.0.0-rc1`) is published as a prerelease.

- **The release is cut with `gh`, not a third-party action.** It is preinstalled on the runner, so a repo that publishes executables adds no supply-chain surface to do it. `permissions:` is `contents: read` for the workflow and widened to `contents: write` on the release job alone.
- ⚠ **CI re-verifies each client jar's architecture** by running `file` on its `glass` native. JavaFX names natives for the OS but not the arch, so a packaging mistake produces five jars that all look right and half of which cannot start — the check exists because that exact bug has already happened here once. It is negative-tested: planting an x86_64 jar as `mac-aarch64` fails the job.
- The `actions/*` steps are pinned to major tags; **pinning them to commit SHAs is the remaining hardening step** and is worth doing before the repo has anything to steal.

**The `native` job is a three-way matrix, because jpackage cannot cross-compile** — `ubuntu-latest` → `.deb`, `windows-latest` → `.msi`, `macos-latest` → **Apple Silicon** `.dmg`. No install steps: the runner images already carry WiX 3.14 (jpackage needs 3.0+) and `fakeroot`/`dpkg-dev`. The same `file`-on-`glass` check runs here too, against the shaded jar in `target/jpackage-input/` — jpackage's own output is necessarily the runner's arch, but a mis-resolved JavaFX classifier would still build cleanly and die on launch.

⚠ **Intel Macs and Linux ARM get no installer, by decision, and the jars are their route.** Adding them is one matrix entry each (`macos-15-intel`, `ubuntu-24.04-arm`) — the arch check is already what would keep the two macOS legs apart, since both runners emit an identically named `libglass.dylib`. Note the asymmetry with `client-dist`, which still builds **all five** jars including `-mac.jar` and `-linux-aarch64.jar`; installers are the narrower set, not the same set.

- ⚠ **It runs on TAGS AND `workflow_dispatch` ONLY, and that is billing, not policy.** While the repo is private GitHub bills 2x on Windows and 10x on macOS, and two legs are macOS — every-push would bill ~90 minutes per push. **When the repo goes public, drop the `if:`** so packaging breaks surface on the commit that caused them.
- ⚠ **The embedded version is normalised and deliberately differs from the tag.** `--app-version` takes one to three integers, so `0.2.0-rc1` is rejected, and on macOS it becomes CFBundleVersion where the first number cannot be zero. CI maps `0.X.Y` → `1.X.Y` for the metadata and keeps the true tag in the **filename**. Known one-time discontinuity: at a real `v1.0.0` the embedded version drops from `1.9.x` to `1.0.0`, so that `.msi` is a fresh install rather than an upgrade.
- ⚠ **The installers are unsigned**, so first launch needs Gatekeeper's right-click → Open and SmartScreen's "Run anyway". Signing needs an Apple Developer ID and a Windows code-signing certificate — both paid, both secrets in CI, and neither wired up.

### Commands

```bash
mvn verify                          # build + unit tests, no Docker needed
mvn install -DskipTests             # publish protocol locally, needed before javafx:run
mvn -pl client javafx:run           # launch the client
mvn -Pit verify                     # + schema-backed integration tests (embedded H2, no Docker)
mvn -Pquality spotless:apply        # format
```

The client **runs offline out of the box**: `mvn install -DskipTests && mvn -pl client javafx:run` opens a
playable solo game with no network, no account and **nothing to install** — the character lives in an
embedded H2 file in the profile directory, created and migrated on first launch (⚠ "no database" was
true until 2026-08-03 and is not any more; what is still true, and is the part that mattered, is that
there is nothing for a player to set up). Fifteen tool windows, eight themes, a shell with real pipelines
and globs, and a 23-page offline manual parsed from `client/src/main/resources/.../terms/`.

⚠ **Window controls sit on the LEFT on macOS, in macOS's order** (close, minimise, zoom) and on the
right everywhere else. The group is **reordered, not mirrored** — mirroring would put close where zoom
lives on a Mac, which is the worst possible place to move a close button. `DeckShell.MAC`.

**The application is named `EAS uOS Client`** — `Launcher.APP_NAME`, set via
`apple.awt.application.name` and `glass.appName` **before `Application.launch`** (both are read once
at toolkit init; setting them later is accepted, does nothing, and reports nothing), plus
`-Xdock:name` in `client/pom.xml` and `.run/`. ⚠ This does **not** rename the *process* — `ps` and
Activity Monitor still say `java`, because that is genuinely the executable. Renaming it needs a
`jpackage` app image, which cannot cross-compile.

⚠ **`view/AvatarChooser` is the ONLY place the client reads a host file it did not write**, and it
holds §7's boundary by three conditions: the player picks it in their own OS dialog, it is read
**once** and only the pixels are kept (never the path — a stored path means reading an arbitrary host
location on every launch), and failure is silent. `ui/Png` is a hand-rolled minimal encoder so no
`javafx-swing`/`java.desktop` dependency is needed and the format work stays headless-testable.

⚠ **`Vgrow`/`Hgrow` without an explicit `setMaxHeight`/`setMaxWidth` can silently do nothing.** A
layout constraint grows a child only up to its maximum, and a **Control**'s computed maximum is not
the unbounded value a Pane reports — so a `ScrollPane` with `Vgrow.ALWAYS` still stops at its
preferred height. Settings had exactly this: the grow call was present and obviously correct, and the
pane sat in the top third of the window. Invisible in review. Also: an unstyled `ScrollPane` paints
Modena's **white** viewport over a dark theme.

**The rig monitor's OVERVIEW is a two-column split (2026-07-30)** — cell grid **and its legend** on
the left, `CoreCage` + `HexStream` on the right, tops aligned. The legend used to span both columns,
putting the key to the left half's colours under the right half's animations.
⚠ **Equal halves need `prefWidth(0)` on BOTH children, not just `Hgrow`** — otherwise the `HBox`
divides the *surplus* evenly and the wider column keeps its head start (measured 64/36).
⚠ **`Greeble.filling()` is opt-in.** A greeble that spans a panel measures the advance and follows
the width; one in a fixed slot (the command strip) keeps its fixed count, so making it the default
would change layouts that are already right. ⚠ Its generator's guard scales with the length now — a
flat 80 iterations silently truncated a filled strip on a wide panel.
⚠ **Matching two bounded panels means matching their INSETS, not their box tops.** Adding the right
half's border pushed the cutaway down 9px; `.es-aside-well` now carries `padding: 10` + 1px like
`.es-grid-well`, and the snapshot probe measures a **cell** rather than the well because comparing
boxes hid the drift. ⚠ **`HexStream` measures the character advance off the applied font** (as
`Substrate` does) and refits its word count to the column — a fixed count half-empties a wide panel
or clips mid-word on a narrow one; every line is rewritten on resize, not just new ones.
⚠ **The cutaway started low even at delta 0.0** — the gap was inside the ART: `CoreCage` projected
its top plate to row 2 of 14. `ROWS` is now 10 with the waist derived from the cage's half-height,
not `ROWS/2`. Safe to trim only because `yaw` enters through x/z, so the drawn extent is constant as
it turns; a varying extent would bob against the grid. **Measure node bounds before hunting a gap in
the layout** — `well top=127.0, cage top=127.0` ended that search in one line.

⚠ **`HBox` FILLS its resizable children to the row height — alignment does not stop it.** The rig
monitor's core cutaway sat with a visible gap above it because `beside` had `TOP_LEFT` but not
`setFillHeight(false)`: the `StackPane` was stretched to the full height of the cell well and centred
its content inside. **Alignment says where a child sits; `fillHeight` says whether it was handed a
height to sit in.** Fixing it also let the cell well shrink-wrap, which is what its own comment always
said it wanted. `ui/widgets/HexStream` now fills the freed space below the cutaway — decoration, on
`Pulse.animate` (so Reduce motion holds one frame), hex digits only for `GlyphCoverageTest`.
⚠ **`CycleGrid.dispose` and `CoreCage.dispose` were written, correct, and called by NOBODY** — every
open of the rig monitor leaked another Pulse subscription. `RigMonitorView` now tears all three down
on the scene listener, guarded by an `attached` flag because that listener fires with null *before*
the panel is ever added as well as after it is removed.

**uOS MODERN LIQUID ABS — two glass themes, dark and light (2026-08-05).** `ThemeId.{LIQUID_DARK,
LIQUID_LIGHT}` + `theme-liquid-{dark,light}.css`. `docs/design/ui-design-language.md` **§9.4** amends
§9's ban on **glassmorphism** into an opt-in, on explicit direction, under §9.1's same four
conditions. ⚠ **Drop shadows and blur are NOT included and stay build-blocking.**

- ⚠ **THE BACKDROP BLUR IS REAL — `ui/chrome/Frost` (2026-08-05), and it is NOT CSS.** There is no
  `backdrop-filter`, and `-fx-effect: gaussianblur` blurs a node's **own** text; that is a fact about
  the *stylesheet*, and it was wrongly recorded here as a fact about the toolkit. `Node.snapshot` is
  the way round: capture what is beneath a window, blur the image, paint it under the panel.
  ⚠ **§9's ban is untouched** — it objects to blur/shadow *on the interface*, which softens the edges
  the geometry budget exists to keep hard. Blurring a **picture of what is behind** a window leaves
  every hairline and glyph exactly as sharp. The CSS scan still runs on all eight stylesheets.
- ⚠ **THREE DECISIONS MAKE IT AFFORDABLE, and each is a trap avoided.** (1) The capture is the
  **whole desk**, not the window's rectangle — so a backdrop changes only when the *content* behind
  changes, and **dragging is free and pixel-accurate** (a translation over a static backdrop is
  exactly a translation of the backdrop). (2) Captured at **0.4 scale**: a blur discards high
  frequencies by definition, so capturing them is work whose only product is thrown away, and the
  smoothed upscale helps. ⚠ The radius is in *downscaled* pixels, ~2.5× larger on screen — and
  JavaFX caps `GaussianBlur` at **63**, a cap on the small number and not the visible one. (3) One
  capture per window, **bottom-up**, hiding everything once and revealing one frame at a time, so
  frame *n* sees exactly frames *0..n-1*.
- ⚠ **DEFERRED AND COALESCED, NEVER SYNCHRONOUS.** `snapshot` forces a CSS and layout pass, so
  calling it from inside one — which is where most of these events originate — re-enters layout.
  Scheduled at the `notifyListeners()` chokepoint, not at the twelve call sites.
- ⚠ **`DeskManager.frostNow()` exists ONLY for the render harness.** No queued runnable executes
  during a synchronous `Scene.snapshot`, so a harness on the normal path photographs every glass
  window with nothing behind it and reports the feature as working.
- ⚠ **IT REFRESHES AT 24 FPS ON ITS OWN CLOCK** (`UiTokens.FROST_MS`), deliberately **not** `Pulse` —
  Pulse ticks at 100ms and **quantises every subscriber to a multiple of it**, so a request for 24fps
  rounds silently to 10, and reaching 24 through Pulse means speeding up every decorative widget in
  the client to fix one. A `Timeline` with an action-only `KeyFrame` interpolates nothing, so §5's
  easing ban is not in play and neither contract test fires: this is a sampling rate, not a tween.
- ⚠ **THE MEASUREMENTS ARE THE WHOLE DESIGN.** Four windows at 1600×1000, per refresh: one capture
  **per window** (a real compositor's semantics) = **~40ms**, a 24fps *ceiling*, i.e. the whole
  thread; each cut to its own window's rectangle = **~37ms**; **one shared capture = ~9ms**. What
  ships is shared + one per **overlapping** window: **8ms tiled, ~34ms fully cascaded**.
  ⚠ The middle number is the counter-intuitive finding: **`snapshot` renders the whole node whatever
  the viewport says — the viewport only crops the result.** Cost is the *number* of snapshots and
  barely at all their size, which is why shrinking bought 7% and taking fewer bought everything, and
  why `SCALE` has diminishing returns (0.22 was still 32ms cascaded).
- ⚠ **SHARED WHERE THAT IS EXACT, PER-WINDOW WHERE IT IS NOT — and the first version got this wrong.**
  A shared capture hides every frame, so it is the desk; handing it to a window sitting **on** another
  shows blurred desk where the window beneath should be, which reads as a hole punched through the
  stack. ⚠ The resolution: a shared capture is not an approximation for most windows, it is **exact** —
  if a window's rectangle overlaps no lower window, the desk genuinely is all that is under it. Only
  real overlaps get their own capture, which in the **tiled layout is none of them**.
  ⚠ **Overlap must be STRICTLY POSITIVE**: tiled windows abut, and a plain `intersects` counts a
  zero-width touch, putting every window on the expensive path to produce an identical picture.
- ⚠ **THE TILED LAYOUT IS THE WRONG ONE TO CHECK STACKING IN**, which is exactly why it shipped wrong:
  every render was tiled. `-Ddeck.cascade` on the harness leaves windows overlapping.
- ⚠ **24 FPS IS A CEILING, NOT A RATE** — paced against `UiTokens.FROST_BUDGET`. `DeskManager`
  measures each refresh and will not start the next until the gap is `cost / budget`. A fixed 24fps
  hands the thread to the blur exactly when the player has the most on screen. The frost stays
  **correct** at any window count and only its *frequency* degrades: full 24fps tiled, ~7fps cascaded.
  ⚠ It reads **`System.nanoTime`**, the one place this client's "always the session clock" rule
  inverts — this is not a game deadline, it is how long *this machine* took, and a wound clock would
  make the pacing believe a 34ms capture was instant. Same reasoning as the event bus's `time`.
- ⚠ **A CAPTURE CLOSES AN OPEN DROPDOWN, and this shipped as a real bug.** A capture hides every
  frame for one snapshot, and a JavaFX `PopupWindow` **dismisses itself when its owner node becomes
  invisible** — so the frost clock closed every dropdown and context menu within 42ms of it opening.
  Reported as "it appears, then disappears". `Frost.popupShowing()` skips the refresh while one is
  up. ⚠ **Invisible to every render harness**, because a popup is a separate window and never appears
  in a `Scene.snapshot` of the deck; and impossible before the frost went on a clock, since opening a
  menu is not a desk event.
- ⚠ **NOTICES are frosted; POPUPS are not, and cannot be by this mechanism.** `Notifications` lives in
  the deck's scene graph, so `Frost.overlayBackdrop` captures what it floats over — the deck INCLUDING
  its windows, which is a different picture from any window's own backdrop — and `-es-notice` (aliasing
  `-es-float`) lets it transmit. ⚠ Context menus, dropdown lists and tooltips are **JavaFX
  PopupWindows**: separate OS windows with their own Scene, so there is nothing beneath them to
  capture and `-es-float` keeps them **opaque**. Making those glass needs a different mechanism.
  ⚠ The toast's frost view must be **unmanaged** — the image is the whole desk, so a managed one
  reports ~1600×1000 as its preferred size and the first notice renders hundreds of pixels tall.
- ⚠ **Reduced motion STOPS THE CLOCK and falls back to the event-driven path** — still correct after
  every interaction, never moving on its own. A frost that merely froze would be *wrong* rather than
  still: it would show a desk that has since changed.
- ⚠ **THREE GROUNDS, THREE RULES, and the middle one was missed on the first frosted build.**
  `-es-panel` is the window body (glass). **`-es-well`** is anything sunk INTO a panel — a terminal's
  scrollback, a table body, a text field, the cycle grid's field, the map canvas, the calculator — and
  it **aliases `-es-void`**, so the six opaque palettes are unchanged. ⚠ It exists because `-es-void`
  does two unrelated jobs, desk AND recess, and glass needs them separated: the desk stays opaque
  (nothing is behind it), while a well painted with it **punches a black box through the glass** —
  exactly what the terminal, file manager, map, manual and calculator looked like. In the glass
  palettes it is a *tint over the frost*: **darker** than the panel (a recess reads as a recess by
  being darker) and **more opaque** than it (a terminal's content sits directly on it, and a blurred
  bright patch behind small monospace is where transmission costs real legibility). `-es-float` is
  the third — floating over content, opaque.
- ⚠ **`ContrastTest.transmissionRequiresFrost` couples the two.** A panel at 80% transmission is safe
  only because a blur sits behind it; lowering a future palette's alpha without declaring
  `frostsBackdrop()` puts a sharp, fully readable second screen under every window.
- ⚠ **THE RIM IS THE MATERIAL, NOT THE TRANSPARENCY.** `-es-rule-hi` already paints `.es-panel-edge`,
  the 1px band the base sheet draws round every window, so brightening that one token lights every
  panel at once and costs **no new component rule**. Transmission alone does not read as glass.
- ⚠ **THE FILM IS LIGHT, NOT DARK, AND THAT IS THE DECISION THE WHOLE PALETTE RESTS ON.** A frosted
  pane scatters *additively* — it lifts what is behind it toward grey rather than tinting it darker,
  which is why macOS glass over a black desktop is a mid grey. Measured at one alpha and desk: a
  mid-toned film leaves the text behind at **4.95:1** (a readable second screen), a light film at
  **2.03:1**. The authentic choice is the only survivable one. ⚠ Cost: the panel composites to a mid
  graphite and the greys above it are near-whites, so the ramp is compressed — that is what heavy
  glass buys and it is what the reference looks like.
- ⚠ **TRANSMISSION IS 80% (dark) / 68% (light) WITH THE BLUR** — about twice what was survivable
  without it. The two failures below are the record of what transparency costs UNFROSTED, and the
  constraint any future unfrosted palette is still held to. Both were found only by looking:
  both found only by looking: (1) at 12% the desk substrate's rows of hex came through as horizontal
  **banding** — it slips under a bound written for *text* because it is *texture*; (2) at 64% the
  notification stack over the LOG window was two columns of text in the same pixels.
- ⚠ **THE NUMERIC BOUND IS NECESSARY AND NOT SUFFICIENT.** That 64% build measured **2.78:1** and
  passed comfortably: each text was individually below the legibility floor and the pair was
  unreadable, because a **per-pair luminance ratio cannot see two texts competing for the same glyph
  cells**. `ContrastTest.whatShowsThroughIsNotReadable` holds the floor; the palettes sit well under
  it. **Passing that test does not mean a palette is legible — render it.**
- ⚠ **`-es-float`: a WINDOW may be glass, a thing that FLOATS over content may not.** Toasts,
  dialogs, context menus, tooltips, the download dock, the sync banner. It **aliases `-es-panel-hi`**
  (verified: a looked-up colour may reference another, and a theme may override it), so the six
  opaque palettes are unchanged and only the glass ones override it with an opaque literal. A window
  body transmits because what is behind it is *usually the desk*; an alert is over content by
  definition, and an alert you cannot read is not an alert.
- ⚠ **The wallpaper is not behind the interface any more, it is INSIDE every panel** — `theme.css`
  drops the substrate and greeble under `.es-theme-liquid-*`. Load-bearing, not decoration.
- ⚠ **A SIX-DIGIT REGEX SILENTLY TRUNCATES `#RRGGBBAA` — WORSE THAN NO CHECK.** `ContrastTest` matched
  `#[0-9A-Fa-f]{6}`; against an eight-digit token it does not fail, it **takes the first six digits and
  drops the alpha**, so every assertion would have measured a panel colour never on screen and passed.
  It composites now: panel over the desk, raised surface over the **panel** (two glass layers stack).
- ⚠ **Eight-digit hex, never `rgba()`.** Both parse (verified on JavaFX 26, alpha survives the
  looked-up-colour indirection); one spelling means one parser for the tests that read these files.
- ⚠ **The accent stays WARM AMBER** though the reference is blue — §2.1's warm-on-cool split is
  load-bearing, and blue beside `gain`/`warn`/`alarm` is the semantic colour system §2.1 bans arriving
  one token at a time. Burnt amber on the light palette, for uOS Classic's reason (bright sodium is
  ~1.7:1 on near-white, which turns the one meaningful colour into decoration).
- ⚠ **They round windows WITHOUT writing the player's §9.3 setting** — a costume must come off
  cleanly. `ThemeId.cornersAreRounded` is the single place that knows the rule, because the two sites
  that shape a window (the Scene-root clip, the desk frames) are otherwise one edit from disagreeing.
  ⚠ It reuses the existing **`.es-rounded`** class rather than a theme-scoped radius, so §9.3's
  machine-checked "never round a measurement" keeps protecting it — and `UiContractTest` needed **no**
  amendment. ⚠ Settings shows the **effective** state, disabled, with a line naming who decided it: a
  control that appears to do nothing reads as broken, and players blame the control. ⚠ The sync must
  guard its own `setSelected`, or displaying the effective state **writes** it and the player's square
  deck is permanently round.
- ⚠ **A theme can change GEOMETRY now, so `EyeAndSickleClient` listens on `themes.currentProperty()`**
  — the chokepoint, not the four pickers. Rounding is a clip plus a style class and a stylesheet swap
  touches neither.
- ⚠ **`DeckSnapshot` had to be taught this or it photographs SQUARE frames** — the harness selects a
  theme and never re-applied the geometry, which is this repo's recurring failure: a render capturing
  the one state indistinguishable from the feature being absent, reported as a pass.
- ⚠ **`UiContractTest`'s sheet lists were hand-kept and HAD ALREADY DRIFTED** — the cursor check named
  five of the six sheets that existed, so `theme-cyberdeck.css` was exempt from a build-blocking rule
  by clerical accident. Derived from `ThemeId` now, and the no-blur/no-shadow scan widened from
  `theme.css` alone to **every** sheet: an overlay is exactly where "just a touch of blur" would land.
- ⚠ **The first palette was tuned against the deck's desk-to-panel step and was wrong.** That step is
  nearly invisible — right for hairlines with no fills, wrong for glass, **where the lift IS the
  material**. A pane level with its ground is not a pane.
- ⚠ **`-es-void` is the desk AND every inset well** (text fields, list grounds, empty rows), so on the
  light palette one token has to be both the surface a pane lifts off and the recess sunk into it.
  uOS Classic lands on a mid grey for exactly this reason.

⚠ **`GameSession.scanReports()` IS NEWEST FIRST, and the SECURITY CENTER read it backwards
(2026-08-05).** `GameEngine` reverses the stored list to deliver it that way and the interface says
so; `SecurityCenterView` called `getLast()`, which is therefore the **oldest** audit on file — so the
verdict was pinned to the player's very first scan and never moved again. Reported from a rig with
eleven audits on file still reading *"the last quick audit was clean, but that was a while ago"*
straight after a full audit.
⚠ **It is silent and it gets MORE wrong with use**: on a fresh rig the first audit is also the last,
so the panel is correct exactly until the second scan — the point at which nobody is watching it any
more. ⚠ `AuditView` consumes the same list correctly, so the contract was right and one caller was
wrong; **an ordering contract stated only in prose is one `getLast()` away from being inverted.**
⚠ Fixed behind `SecurityCenterView.latestOf`, pure and package-private **so it can be tested without
a toolkit** — the same seam `markStateFor` already exists for, and for the same reason: the previous
verdict bug shipped because the rule lived inside a repaint that needed a live scene to reach.
Negative-tested against the unfixed code.

⚠ **Contrast is MEASURED, not assumed — `ui/ContrastTest` (2026-07-30).** It computes real WCAG
ratios for every text token against `-es-panel` and `-es-panel-hi` in all eight palettes and fails the
build below **3:1**. It caught the network map drawing CONTACT/LOCKED in `-es-dim-3` — the *greeble*
token — at **1.77:1** on the deck and **2.06:1** on Classic, where those nodes vanished outright;
and the deck's own `-es-dim-2` at 2.78:1. ⚠ **uOS Classic is the palette that catches this class of
bug** (the only light one), and rendering one theme proves nothing about the others.
⚠ **The exemptions are load-bearing**: `-es-rule`, `-es-rule-hi` and `-es-dim-3` draw hairlines and
texture, and holding a rule to a text threshold would turn every border into a stripe. ⚠ A test also
asserts the floor did **not flatten the hierarchy** — quiet must stay quieter than body text.
⚠ **A RUNTIME auto-contrast layer was rejected**: §10 criterion 2 requires every colour to be a
looked-up token in a stylesheet, and computing one at run time makes the palette unpredictable,
unreviewable, and overrules each theme's deliberate choices. Build-time enforcement puts the fix in
the stylesheet, chosen by a person.

**Every checkbox is a `ui/widgets/Switch` now (2026-07-30)** — a horizontal toggle, because these
settings take effect on change and there is no submit. ⚠ **Square**, pill only under `.es-rounded`
(§9 unamended). ⚠ **The knob SNAPS** — a slide is a tween and a stepped one is a `Timeline` in a
widget; both fail `UiContractTest`, and it makes Reduce motion free. ⚠ **Position is the primary cue,
fill the secondary** (§4.4). ⚠ **It announces the ORIGINAL text** — `Ui.label` uppercases and readers
spell all-caps runs out letter by letter. ⚠ **API-compatible with `CheckBox`** (`selectedProperty`,
`isSelected`, `setSelected`, `setTooltip`) so the 15 call sites changed only a type name.
⚠ **`instanceof CheckBox` in `NodeShellView` silently stopped matching** — a pattern match compiles
fine when the widget type moves, so a switched-on flag never reached the command line and the wrong
command ran. Grep for the old type after a widget swap; the build will not tell you.

**The focused window can carry an outline, in a colour the player picks (2026-07-30).** Settings →
Desk, **off by default**; `ui/chrome/FocusRing`, per character. The deck already marks focus by
lightening the strip and accenting the title — quiet on purpose — and this is for players for whom
that is not enough.

- ⚠ **The hues do NOT join the palette's semantic vocabulary (§2.1).** A ring colour *means nothing*;
  it says "the window you chose the colour for". Confined to `.es-focus-ring-*`, used nowhere else.
  **§4.4 holds** because the strip cue is still there — the ring is never the only marker.
- ⚠ **THEME is first and default**: it resolves `-es-amber`, so it follows every palette.
- ⚠ **It paints the frame's `edge` REGION, not a border on the frame.** Frames are clipped to a
  `Polygon` for the notch, so a border would be cut away and appear to do nothing — silently, CSS
  applying correctly. Same trap as the first rounded-corners attempt; rendered to confirm.
- ⚠ **`VisualSettingsTest`'s hook rule was amended.** It required every `VisualSettings` field to have
  a legacy `@JsonProperty` hook — true when every field was a migrated one, false for a NEW appearance
  field, and a hook for one would read a key no save ever contained. The legacy set is now a literal
  list; a round-trip test covers what the rule was standing in for.

⚠ **Any global appearance flag must reach LIVE objects, not just new ones.** This has now bitten
three times — rounded corners (frames kept their birth clip), and control order (frames kept their
birth layout). `DeskManager.setRoundedCorners` and `setControlOrder` both walk every open window.

⚠ **`subwindowControlOrder` is ORDER only and DESK WINDOWS only.** It never changes which side the
controls sit on, and never touches the outer window — that one sits beside the player's real windows
and follows the host OS unconditionally, because putting close where their OS puts zoom costs
sessions. Reordered, never mirrored: reversing the row puts minimise where the other convention puts
maximise, giving neither.

⚠ **Two chrome opt-ins now amend contracts, and both ship OFF** — `roundedWindows` (§9.3) and
`nativeWindowBorder` (§0.1). §0, §9 and §10 criterion 1 still describe the *default*, and
`WindowChromeSettingsTest` holds that. With a native border the deck must **not** draw its own
`[−] [+] [×]` (two sets of window controls is a question, not a redundancy) and must **not** install
the strip drag handler (it fights the OS title bar). Restart-only: `initStyle` is rejected on a
realised Stage and `DECORATED`/`TRANSPARENT` are mutually exclusive.

⚠ **The Stage is `StageStyle.TRANSPARENT` unless the native border is on.** It used to be conditional on the rounded-corners
setting, which meant the main window could only change on a restart while desk windows changed
instantly — a toggle that half works is worse than one that does not. The scene's ground holder
covers the window edge to edge, so nothing is see-through until a corner is clipped away. The clip
goes on the **Scene root**, not the deck: clipping the deck leaves the scale holder painting the
corners back in, which is indistinguishable from the setting doing nothing.

⚠ **Corner geometry on this deck is a CLIP, not a CSS property.** `WindowFrame` already clips both
painted parts to a `Polygon` for the 18px notch, and **a polygon clip cuts square corners whatever
`-fx-background-radius` says** — the first rounded-corners attempt set the CSS, which applied and was
then clipped off, with nothing anywhere reporting a problem. `WindowFrame.clip` intersects a rounded
rect with the notch. A toggle must `requestLayout()` every live frame, or it appears to affect only
windows opened afterwards. The outer Stage needs `StageStyle.TRANSPARENT` to have a real corner
(UNDECORATED paints its own), which is chosen at startup and cannot change on a realised Stage.

⚠ **§9's rounded-corner ban was amended (§9.3, 2026-07-28) to an opt-in**, off by default, gated on
`.es-rounded`. It rounds the Stage and desk windows and **must never round a measurement** — a meter
cell with a soft corner reads as a smaller cell, and discrete meters exist to be counted.
`UiContractTest.RoundedOptIn` enforces both halves.

⚠ **The rig root is macOS-shaped over a FreeBSD base**: `/Applications`, `/Library`, `/System`,
`/Users`, `/mnt`. Homes are `/Users/<name>`; `/Applications` is system-wide. The Linux FHS did not
vanish — it lives inside **`/System`** (`engine/fs/SystemTree`), laid out as FreeBSD lays one out,
`root:wheel` and `r-xr-xr-x` throughout. **`/System` is read-ONLY, not unlookable** — text
configuration (`rc.conf`, `fstab`, `passwd`, `loader.conf`, …) reads in FreeBSD's real formats;
binaries answer with `file`'s line rather than invented bytes; and `master.passwd` stays closed even
to its owner because it is mode 0600, which is the real reason and the thing worth teaching. On a
machine you breach, the same rule as the rest of it: outline always, contents once you hold it.

⚠ **Ask the rules before trusting `FsEntry.readable`.** It is one bit and there are several reasons a
file will not open (mode, ownership, no foothold). Views that branched on it first told players to
"breach" their own rig. `session.read` first; generic refusal only if it says nothing.

⚠ **Never hard-code a home path.** Use `VirtualFs.home(user)`. The `/home` → `/Users` move broke the
file manager's start path, and a missing directory renders as an *empty folder rather than an error*,
so nothing complains.

⚠ **The three storage tiers live in `~/.VaultStore/`, not `/mnt`, and the window is called
VaultStore** (id still `storage` — ids key saved desk layouts). They were never mounts, and a
`/mnt/vault` in the sidebar of a machine an intruder is standing on is a signpost to the one place
meant to be safe. The dot hides nothing from a determined reader; `design/01` §6's **tier** is the
real protection.

⚠ **The rig root is Ubuntu's (FHS) and the home is macOS's** (`Applications`, `Desktop`, `Documents`,
`Downloads`, `Movies`, `Music`, `Pictures`). Both halves are real somewhere; nothing claims to *be*
Ubuntu. Applications are genuine macOS bundles — `Network.app/Contents/MacOS/network` — and the fact
worth teaching is that an application on a Mac is a folder. `Contents/Upgrades` is **ours** and is
not part of a real bundle.

**The chain runs while the client does not (2026-07-29).** `resume()` fills in every missed block via
`ChainRules.sync`, and the LEDGER window opens on a `SYNCHRONIZING` panel reporting what it did. Height
used to freeze at the last tick, so a character played Monday and again Friday found four days of
wall-clock time and zero blocks — on the one readout whose whole subject is that nobody can stop it.

- ⚠ **Every filled block carries its OWN instant, walked forward on a time cursor.** `retarget()`
  computes `expected / actual` from `Duration.between(retargetStartedAt, now)`, so stamping the whole
  fill at the load instant makes `actual` the entire absence — a window closing two hours into a
  30-day gap is measured as having taken 30 days, the adjustment pins to the ÷4 clamp, and difficulty
  collapses on a chain whose hashrate never moved. The online path never showed this because it ticks
  once a second. `ChainSyncTest.retargetIsNotSkewedByTheAbsence`.
- ⚠ **I5 WAS AMENDED and is no longer "online-only."** The rig keeps hashing for
  `Balance.OFFLINE_MINING_HOURS` (4) after logout and then stops dead; past that its hashrate is zero
  and it is drawn against nothing. The **cap**, not the online-only rule, was always what stopped
  absence out-earning play. Deployed miners kept their identity — they spend the *host's* compute (I6),
  so five buffer five hosts' worth of the same window, and their buffer can be **seized** where
  self-mining cannot. Had they been separated only by "one works offline", this would have deleted the
  distinction. `design/15` §3, `design/04` §1.2.
- ⚠ **TWO levers bound offline mining, and they are not the same lever.** `OFFLINE_MINING_HOURS` caps
  how **long** an absent rig hashes; **`OFFLINE_MINING_WIN_WEIGHT`** (0.5) caps how **well** it does
  while it is, so an hour played beats an hour away *inside* the buffered window too. ⚠ **Fills only**
  — the live tick is untouched, because leaving the client running is playing and this is not an
  idle-time penalty. ⚠ **Deliberately invisible** — no readout names it, by decision.
  ⚠ **IT WAS SOLO-ONLY AND WAS NAMED `OFFLINE_SOLO_WIN_WEIGHT` UNTIL 2026-08-06.** The exemption's
  argument — a pool competes whether or not one member is online — is true of the **pool** and does
  not extend to the **player's** pooled income, which is what it actually exempted: a pooled character
  collected four hours at full rate while a solo one collected four at half, and **the default pool is
  pooled**. One constant now, because "what an absent rig's hashrate is worth" is one question; two
  would be two figures to re-tune and one to forget, which is how they came to differ by 2× at all.
  ⚠ **THREE MODES, THREE PLACES, because the player's hashrate enters three ways.** **Solo** —
  `ChainRules.drawWinner` scales the player's share of the draw; it *must* be the draw, since a solo
  block pays the whole subsidy plus fees and there is no cut to scale. **PPLNS** —
  `MiningRules.runSelfMining` scales the cut of each block carrying `Won.offline()`. **PPS** — the
  same method scales the **share clock's accrual**, because a share pool pays per accepted share out
  of its own balance whether or not anybody found a block, so the draw is not a lever on it at all.
  ⚠ **HALVING THE POOL'S OWN `networkShare()` IN THE DRAW IS THE OBVIOUS IMPLEMENTATION AND IT IS
  WRONG TWICE.** A pool does not lose half its hashrate because one member logged off, so it hands the
  freed probability to the unpooled population for four hours and leaves the block explorer reporting
  that this player's pool underperforms during their absences — and it halves the PPS contributor rows
  while reducing PPS income by **exactly nothing**, since those rows credit zero by construction.
  `ChainSyncTest.OfflineWeight.poolsAreUntouched` holds the chain to the same shape either way.
  ⚠ **The solo branch scales the THRESHOLD, never the number of draws**: one `nextDouble` per block
  whatever the mode, or a stored seed stops being a replay. ⚠ **The PPS lever is the ACCRUAL, never
  the payout or the target** — a share that paid half would make a share mean two things, and a bigger
  target would re-rate the draw.
  ⚠ **Pooled offline mining was ALREADY capped at 4 hours**, so raising it is a *rise* in passive
  income, not a cut: `sync` gates pool-block credit on `competing` and PPS accrues off `minedFor()`.
- ⚠ **Comparing a live run against a fill needs the SAME save loaded twice.** Two saves built
  identically are not identical: a fresh game draws its own initial `networkWorkTarget` from the
  character id, so the walks are a fraction of a block apart at the start and diverge within the hour
  — which reads exactly like a broken RNG contract. `ChainSyncTest.OfflineWeight` persists one and
  loads it twice.
- ⚠ **Two clamps must agree and only one is enforced in `GameEngine`.** `ChainRules.sync` excludes the
  player from the draw past the window, which caps solo and PPLNS. **PPS is not capped by that** — it
  runs its own share clock off `elapsed` — so `resume()` passes `walked.minedFor()`, never the absence.
  Passing the absence breaks I5 silently and *only for pay-per-share*.
- **Confirming pending transactions while away is not income.** The value moved when the ledger row was
  written; confirmation only stamps the height. A transaction unconfirmed across a four-day absence
  would be the lie, and would let a player park money in the mempool to hide it.
- ⚠ `GameEngine.sync` is **session state, never saved.** It describes one transition; persisting it
  replays the sync screen on the next load reporting a catch-up that already happened.
- ⚠ **The panel builds from `takeChainSync()`, NOT `chainSync()` — announced once per session.** A
  closed tool window keeps no state (`DeskManager` calls the factory afresh), so an idempotent read
  replayed the whole fill on *every* open of the ledger. `chainSync()` stays idempotent for tests and
  any second readout; `takeChainSync()` answers once and then reports nothing. Consumed when the panel
  is **built**, not when the replay finishes — otherwise closing and reopening fast replays forever.
  Nothing is lost: `logSync` already wrote the same facts to the rig log, which is where history goes.

**LEDGER has a third tab, CONTRIBUTOR (2026-07-29)** — every block this rig put hashrate into, solo and
pooled, with the rig's share of the chain at the time, the block's transaction count, and the **coinbase
and fee halves of the reward kept separate** (one credit in the ledger, two different things on the
chain; `proof-of-work(7)` teaches the split and a single total hides it).

- ⚠ **Only what was ROLLED is stored** (`ContributionState`): height, mode, scheme, hashrate, network
  hashrate, difficulty, credit. Transaction count, fees and subsidy are **derived from the height** by
  the same calls the block card uses, so a row and its block cannot disagree.
- ⚠ **A PPS row credits ZERO from the block and that is the record working.** A share pool buys accepted
  shares out of its own balance rather than dividing a block, so the column renders **"per share"**, not
  `0.00 EC` — every row of a default character's tab is PPS, and ten zeroes under "your cut" read as a
  broken column. It is the only surface where the two pool schemes differ visibly.
- ⚠ `MiningRules.bank()` banks **per payout**, not per tick. `floor(r+a+b) == floor(r+a) + floor(r+a−floor(r+a)+b)`,
  so the total is identical — what it buys is a per-block figure that *sums to the ledger row*, which a
  separately-rounded display figure would not.

**The mempool projects 3–5 blocks, each with its own queue (2026-07-29).** Depth comes from
`MempoolRules.projectionDepth` — derived from `(blockSeed, height)`, never drawn: the panel repaints once
a second and a drawn count adds and removes a card every repaint. ⚠ **Each projection packs against
`backlogAt(height + 1 + i)`, not against one snapshot drained across the strip.** Draining rendered
`0 txs` from the third card on — one dead card at a fixed three, up to three at 3–5 — which claims the
chain is about to go quiet. It is not: a real mempool has inflow ≈ throughput, which is the entire reason
there is a fee market. Each card also quotes **its own** clearing price, and the outbid check runs at
every index (it was `&& index == 0`, correct only while all cards shared one price).

⚠ **A projection's `transactions` and `feesMinorUnits` are the MINED block's, not the queue's.** Both
come from `MempoolRules.blockTransactionCount`/`blockFeesMinorUnits` at the projected height — the same
calls the block card makes when it lands — so an estimate and the block that replaces it are one number
arrived at once. Two bugs this closes: the count was the *backlog* (a card reading "200 txs" landing as
a 47-transaction block), and `feesMinorUnits` was **this rig's** fees, so every card read `fees 0.00 EC`
on a wallet with nothing queued — a block explorer reporting that mining the next block is worth
nothing. ⚠ The rig's own fee is deliberately **not** added: a player's transaction *displaces* network
traffic rather than adding to it, so the block's total does not move for it (`blockFeesMinorUnits`); the
queue depth still drives `slotsAgainst` for how many slots the player wins, which is a different
question from what the block carries.

⚠ **`Scene.snapshot` does not pick up a plain `setVisible` toggle between two synchronous snapshots of
the same Scene.** It re-applies CSS, so a theme change lands; pushing a visibility change into the render
tree needs a real pulse and nothing fires one headlessly. Three tab PNGs came out byte-identical while
the chip labels proved the state had changed — a verification tool reporting success and showing the
wrong screen. `LedgerSnapshot` builds a fresh Scene per tab. Also: `lookupAll` matches on style class and
finds **nothing** before `applyCss()`.

**Buying a tool settles on-chain (2026-07-29).** Pay → **download** (a real transfer, the file manager's
existing progress bar) → the package lands in `~/Downloads` as a vendor `.pkg` → `install`/`sell` refuse
until the payment is mined → confirmation runs Repac, it becomes a `.upg`, installing it fills the vault.
This **reverses** `GameEngine.debit`'s documented "the goods are immediate" decision, on explicit direction.

- ⚠ **The `.pkg` → `.upg` rename IS the lock — there is no second mechanism.** Repac already means "a
  vendor's package" vs "one this rig can install", and a bought one does not cross that line until the
  chain says the money moved. So the lock shows in `ls`, the file manager and the shell without any of
  them knowing about confirmation, and — being derived from the ledger row's `blockNumber` on every
  read — no flag anywhere can disagree with the chain.
- **First mechanical consequence a `FeeTier` has ever had.** Previously a fee bought only how soon a row
  stopped printing `—`. A higher fee buys a slot in an earlier block, never a faster chain.
- ⚠ **`GameEngine.debit` writes TWO ledger rows** — the spend (broadcast) and a separate `TX_FEE` line
  (not broadcast). Taking "the last row" gets the fee, which never confirms; a package pointed at it is
  held **forever** with the money gone. Use **`spend()`**, which returns the row it broadcast.
- ⚠ **`LedgerEntryState.feeMinorUnits` exists because `confirmInto` DELETES the pending record.** The fee
  lived only on `PendingTxState`, so a confirmed priority transaction reported the *standard* fee — and
  since block rows sort by fee rate, the player's own row sorted into the wrong part of a block they had
  paid to be at the top of. `-1` means "no fee recorded" and is distinct from a fee of zero.
- ⚠ **Open:** the reversed decision's own argument — that withholding goods breaks buying a consumable
  mid-breach — still stands. No such consumable is in the catalogue today; the day one is, it needs an
  answer. `design/15` §3.

**Replace-by-fee (2026-07-29).** `MempoolRules.boost` raises a waiting transaction's tier; the YOUR
PENDING rows carry a `boost +X` chip. ⚠ **Only the DIFFERENCE is charged** — the first fee was debited at
broadcast, so charging the new tier in full takes it twice. ⚠ **Both records move**: the pending record
is what `confirmInto` sorts on, the ledger row is what the explorer reads once the pending record is
deleted — updating one makes a boosted transaction sort at its new fee and render at its old one.
⚠ **A bump only ever goes up**, for real RBF's reason: a replacement paying less would let anyone
rewrite a relayed transaction for free, repeatedly. The hash deliberately does *not* change (see
`submit` — a hash that changed would make the pending row and the mined row two transactions).

⚠ **A panel that pulses cannot host an editable field.** Rebuilding rows on the one-second `Pulse`
tears down an open `TextField` **mid-keystroke**. Split it: rebuild on *data* change only, keep a
`ticking` list for the wall-clock text, and suppress data rebuilds while an editor is open
(`ReconView`). ⚠ This is a workaround — **UI-7** in `design/15` records that the client should be
event-driven here rather than polling, and that the fix wants its own pass.

⚠ **`NodeMenuTest` is the ONLY JUnit test that starts the JavaFX toolkit**, and it broke the CI Linux
job with `UnsupportedOperationException: Unable to open DISPLAY`. Every other FX-touching file here is a
`*Snapshot` **main class** run by hand — that is the convention. It now `Assumptions.abort`s (skips)
when the toolkit cannot start, rather than swallowing and passing: a regression test reporting success
without executing is worse than none. ⚠ **It therefore guards nothing in CI** — fixing that needs
`xvfb-run` on the Linux job or Monocle on the test classpath.

⚠ **RECON is `ReconView` (the collected reports), not `MoreViews.recon`** — that is a one-line pointer
now. The cost model and the teaching moved to `client/terms/en/1/port-scan.md`. ⚠ Shipped as
**`port-scan(1)`, not `port-sweep(1)`** as `education/05` specifies: this game already uses "sweep" for
*finding machines*, so the outward probe of *one* machine is named for what it is. A sweep finds
machines; a port scan interrogates one. ⚠ A term page's `seeAlso` refs must all **resolve** — the
spec's list named three pages that do not exist.

**Port scans file persistent node reports (2026-07-29).** `engine/state/NodeReportState` per machine,
merged by `NodeReports`; Info on the node menu, `[i]` in the network list, and RECON lists every file
with opened/updated dates.

- ⚠ **Each finding carries its OWN `learnedAt`, keyed by `PortScanTarget`.** One timestamp for the file
  would be *worse than storing nothing*: `updatedAt` moves with any scan, so a cheap firewall re-check
  would present a week-old vault estimate as measured this morning. This is what made persisting a
  snapshot acceptable at all — the report was session-only before, precisely because the cycle-load
  line goes stale.
- ⚠ **Findings MERGE — a shallow rescan must not erase what a deep scan paid for.** Write a field only
  when `PortScanReport.knows` says the scan reached it; `-1` means "never looked", never "none".
- ⚠ **`NetText.STATE` is 14, not 12** — `foothold [i]` is exactly 12 characters. `NetHostListTest`
  treats the widths as a contract.
- ⚠ **`Sighting`'s field list is locked by name in `NetTypesTest`** so an addition is a deliberate
  edit. `reported` qualifies on the same grounds as `patched`: it is the player's relationship to a
  machine, not an observation of it.
- ⚠ **A scan requires `host.discovered`, not merely presence in the topology** — every host in the
  world is in the topology, so the old check was a check on nothing while the refusal claimed "no
  machine that a sweep has found".

⚠ **NEVER anchor a `ContextMenu` to the node that fired the event when the handler repaints first.**
The map's node menu selected the machine before showing — correctly, so the entries are about what the
pointer is over — but selecting repaints, repainting rebuilds the graph, and the label the player
right-clicked is **detached from the scene** by the time the popup anchors to it. JavaFX throws
`"The owner node needs to be associated with a window"` on the FX thread, on **every right-click**.
Capture `getScene().getWindow()` *before* the repaint and anchor to the window; screen coordinates make
it identical on screen. `NetMapView.openMenu`, `NodeMenuTest`.

- ⚠ **A Scene with no Stage reproduces it**, which is what makes it testable headlessly.
- ⚠ **The first version of that test passed against the broken code**, because it fired on
  `.es-focusable` — which matches the sweep buttons and legend, none of which carry the node menu. A
  regression test that passes both ways is worse than none: it reports the bug as fixed. Always run a
  new regression test against the *unfixed* code before trusting it.

⚠ **A wall-clock-derived readout needs `Pulse.every`, NOT `session.onChange`.** The file manager's
transfer bar painted once and froze: nothing about the *save* changes while a download runs, so
`onChange` does not fire again until it finishes — a progress bar that does not progress reads as a
stalled download. Only the transfer strip is on the clock; re-running the whole repaint every second
would rebuild the listing under the player's scroll position. Same split `Views.ledger` already makes.

⚠ **A block's transaction list marks the player's rows in a `YOU` COLUMN, not a prefix.** A leading
marker shifts every field after it and breaks the character-cell alignment the table is read down. The
detail panel is a column of Labels rather than one text block, because per-row styling needs per-row
nodes — a two-character `>` gutter in 200 rows of monospace is a needle, not a marker.

⚠ **`ChainState.networkHashrate` is a STORED COPY of a derived balance value, and a stale one is a
silent permanent income cut.** Found on a real save (2026-07-29): a character created 2026-07-26 was
still on the **2352**-cycle network while one created two days later was on **1680** — the re-tune
`Balance.CHAIN_TARGET_BLOCK_SECONDS` records as "the 2352-cycle network at ten minutes became a
1680-cycle network at fourteen". Nothing looked wrong, because that chain's difficulty had correctly
converged to *its own* equilibrium (482 vs 344) — but mining income is `subsidy × rigHashrate × 3600 /
(interval × networkHashrate)`, i.e. **inversely proportional to network size**, so that character had
been earning **71% of what `design/03` §1 prices**, forever, with no readout saying so.
`GameEngine.retuneNetworkHashrate` migrates it on load. ⚠ **It rescales difficulty by the same factor in
the same step** — difficulty is what holds the block interval, and moving the hashrate alone stretches
blocks from 12 to 17 minutes until the next retarget, which is **1440 blocks** away.

⚠ **The block cards' selected state lives OUTSIDE the card, keyed by HEIGHT.** The strip is torn down
and rebuilt on every chain advance, so a selection held on a node dies every ~14 minutes; and before
2026-07-29 there was no selected state at all — clicking rewrote the detail text and marked nothing, so
the header appeared with no indication which of 24 cards it described. `markSelectedBlock` clears the
whole row and re-marks from the one piece of state, and `refreshData` calls it after every rebuild.

⚠ **`.es-rounded` is a real style class again, applied on the deck root by `DeckShell.applyRoundedSetting`.**
It had been removed when window corners moved to clips. It is back because `UiContractTest` permits a
non-zero radius **only** under that selector, so any component wanting a soft corner has to gate on it —
the block cards' miner pill is a flat chip with the setting off and a pill with it on. §9's ban is
**unamended**. ⚠ The radius rule must not name `es-block`/`es-meter`/`es-cell`/… — the test's second
half refuses a radius on anything a measurement is read off, so the shape sits on a generic `.es-pill`
and the colours on `.es-block > .es-miner-pill`.

⚠ **A `.table-cell` text fill needs a THREE-class selector.** `theme.css` sets `-fx-text-fill` under
`.table-view .table-cell` (specificity 0,2,0), so a bare `.es-contrib-paid` loses whatever the order —
the same trap as the late `.label` rule, from the other side. Use `.table-view .table-cell.es-thing`.

⚠ **There are now THREE reputations and none may share a field.** `factionReputation` (Eye/Sickle
standing), `validatorReputation` (federation trust, server-side) and — new — **`traderReputation`**
(whether you deliver what you were paid for, `engine/rules/SecondaryMarket`). A Sickle hero can be a
thief; a scrupulous trader can be a validator nobody trusts. Collapsing any two deletes an axis.

⚠ **Only ETHECOIN-gated upgrades may be resold.** Selling a schematic-gated tool for ethecoin would
let anyone with enough money buy a ceiling — I2, and I8 for zero-days. Anything can still be *stolen
and used*; what is refused is turning a gated item into currency. `engine/rules/Repac.sellable`.

⚠ **A download is bounded by the REMOTE END'S UPLOAD, not your download** — `Balance.LINK_DOWN_BITS`
is 1 Gbit and `LINK_UP_BITS` is 150 Mbit, so every transfer runs at 18.75 MB/s however good the local
link is. The two constants are different numbers *because* that is the teaching. Package sizes are
load-bearing now that transfer time derives from them (an upgrade is 40–320 MB ≈ 2–17 s); re-tuning
one means re-checking the other. A transfer is a **task** in `save.tasks`, so closing the file manager
does not cancel it, and it holds no compute — moving bytes is I/O, not arithmetic. Upgradability is
open as **TR-1**.

⚠ **Ejecting a machine disconnects and stops nothing else.** Miners, bots and the foothold all
survive; what it buys is quiet (a held session is outward and loud) and the cycles back. Said in the
tooltip because the failure is silent: a player who thinks eject kills their miners never ejects.

⚠ **Recents is a real directory** — `~/.local/share/recently-used`, GNOME's own location — and it is
**persisted in the save**, not the profile. It is therefore as exposed as the machine is: an intruder
standing in it reads what the owner has been doing, which is the fiction working rather than leaking.
Recorded via `GameSession.noteAccess` on deliberate opens only; recording from `list` would fill it
with repaint machinery instead of history.

⚠ **App bundles use `Contents/uOS/`, not `Contents/MacOS/`.** A real macOS bundle names that
directory after the operating system — so ours names it after *ours*. Everything else in the bundle
keeps its real name; the OS-named directory is the one part that has to move when the OS does.

⚠ **`engine/rules/AccessLog` is a [PROPOSAL] counter-forensics loop, and it must not become a fourth
exposure surface.** A remote actor who copies something is logged with the address they came from and
may wipe that address before leaving — **blanking it, never deleting the line**, because a deleted
row turns a legible crime into a missing file. `canTake` answers from the item's **tier** (§6), so an
upgrade visible inside an app bundle is a *view* onto an item, not a way around the vault (**I12**).
Nothing writes to it in solo — there are no remote actors — and that is why it is tested rather than
demonstrated.

**The file manager (2026-07-28).** `Shortcut+Shift+H`. GNOME Files' layout over `engine/fs/VirtualFs`:
places sidebar, breadcrumb path bar, detail list, hidden-files toggle. ⚠ **A mount IS a session** —
"Connect to machine" opens a shell session and unmounting closes it, so the file manager's mounted
list and the set of open shells are one fact rather than two that drift. Kind markers are `ls -F`'s
and are shared with the node shell (`NodeCommands.marker`). ⚠ Block-element icons were tried first
and `GlyphCoverageTest` rejected four of them — they are in neither bundled face.

**The local terminal is the same surface as a machine shell (2026-07-30)** — same markup, same
scrollback trimming, same right-click menu with the option builder. `NodeShellView.buildMenu` is
parameterised over the catalogue rather than copied.
- ⚠ **`shell/LocalCatalogue` GENERATES the menu from `Shell.CommandRegistry`.** A hand-kept second
  list would offer a verb the shell no longer has — menu inserts a line, shell answers 127, the game
  looks like it lied. Measured: 40 offered, 40 registered.
- ⚠ **Only the universal flags** (`--explain`, `--dry-run`, `--verbose`) are offered. A `Command`
  does not declare its own options, so anything per-command would be invented and the parser would
  reject it. Per-command flags belong in the `Command` interface as data first.
- ⚠ **Groups derive from `isFilter`/`hasSideEffect`**, which are already load-bearing, so a new
  command lands in the right group by being what it is.
- ⚠ **Tab completion, Ctrl-R, `$?` styling and the security-boundary banner all survived** — the node
  shell lent a look, not a veto.

**Shell sessions (2026-07-28).** Right-click a machine on the map → *Open a shell*, and a terminal
window opens on it: `ls`, `cd`, `cat`, `stat`, `find`, `df`, `get` and the rest, with a right-click
menu that templates any command's options and previews the line before writing it into the input.
Many at once, one window per machine (`shell:<address>` — not a `WindowSpec`; see `docs/client/05`
§2.1 for why that is not the WL-8 duplication).

⚠ **A shell window's `[×]` must release the session's cycles, and for a long time it did not.**
`DeskManager.Spec` accepted an `onClosed` callback and **dropped it** — declared, passed in by
`DeckShell.showShell`, never invoked. Typing `exit` released the 2 cycles (the shell view asks the
rules, then the desk); clicking the close control went straight to the window manager and left the
allocation held forever, with nothing on screen to give it back. Both halves were individually
correct and covered; the defect was in the **join**, same shape as `reconcileFootholds`.
⚠ **The callback fires AFTER the window leaves the map** — it can re-enter `close` for the same id
(ending a session also closes its window), and firing first recurses on a mouse click.
⚠ **Ordinary tool windows pass `null`** and must: a tool is a view onto state that exists whether or
not it is on screen. Only a window whose existence *is* game state releases anything.

**AUDIT has two tabs and a scan history (2026-07-30).** `view/AuditView`. **SCANNER** holds the
tiers plus a live panel printing each file as the walk reaches it, with a bar and a countdown;
**STATUS** keeps `ps`/`ss`/`df` and gains every completed audit. The running caption now says
**"checking for adversarial processes"** — it read "signal strength, not certainty", which answers a
different question and never named the subject.

- ⚠ **The listings stay on ONE tab.** `design/04` §3.1's investigation is that the three should
  agree; splitting them would split the mechanic.
- ⚠ **Running lines are DERIVED from progress** (`auditPaths()` is a stable walk), never appended on
  a tick — otherwise the panel restarts empty on every repaint, reopen, or scan that ran while the
  client was shut. On `Pulse.every` (data), so Reduce motion keeps it.
- ⚠ **The history stores the MEASURED duration**, not the quoted one: an infested rig slows a scan
  (`slowedSeconds`), and a scan taking longer than it should is itself a symptom. Verified — a Quick
  quoted 30s recorded 0:32.
- ⚠ **A clean scan is a row like any other** — it is what dates a later finding. Capped at 100,
  trimmed from the **front**.
- ⚠ **`-es-accent` DOES NOT EXIST.** JavaFX does not fail on an unknown looked-up value — it warns to
  stdout and drops the declaration, so the bar rendered with no fill. Palette names are in
  `theme.css`; `-es-amber` is the accent.
- ⚠ **`ScrollPane.setVvalue(1.0)` needs a deferred pulse** — clamped against a content height the
  pane does not know until it lays out, so the newest line lands off the bottom.

⚠ **`RigMonitorView.ORDER` drives the grid AND the legend — a consumer missing from it is invisible.**
`SHELL_SESSION` was absent, so an open shell's 2 cycles counted toward "84 / 100 CLAIMED" and produced
no slice: the panel claimed 84 and accounted for 80. ⚠ **That reads as a parasite** — `design/04` §3.1
teaches "the numbers do not add up" as how you detect one — so opening a shell faked the game's own
intrusion evidence. `RigLegendCoversEveryConsumerTest` walks the **enum**, not a hand-kept list, so a
new consumer without a legend entry fails the build.

⚠ **The top strip's balance is abbreviated to 4 decimals with the exact figure on HOVER (2026-07-30),
and that is the ONE licensed exception to the rule below.** At 18 places a balance reads
`1234.905777539252303541 EC` and pushes every other cell off the strip. What earns the exception is
not the lack of room — it is that the exact amount stays reachable (tooltip + `accessibleText`, and
the LEDGER is always exact). So the rule is *sharper*: **a held amount may be abbreviated only where
the exact figure is one hover away.** ⚠ The tooltip tracks the **target**, not the counting figure —
mid-count the shown value is not the player's balance.

⚠ **`Ethecoin.formatApprox(wei, n)` ROUNDS and is a separate method for that reason.** Derived,
*labelled* approximations only — `~40 EC/hr`, a projected payout. **Never** a balance, ledger delta,
fee charged or resale price: a rounded amount somebody holds is a lie they cannot detect. The rig
monitor read `~39.99999999999999802 EC/hr` because the rate derives through a double; that residue
always existed and only became visible at 18 places. Four decimals.

⚠ **A session is NOT the vantage, and merging them breaks the reach model.** The vantage is the
single point a sweep measures hop distance from — a hard ceiling no purchase moves (**I2**). A
session is a shell on a machine already held: it costs `Balance.SESSION_CYCLES` while open, buys no
reach, and `SessionRules` never touches `vantageAddress`. Had they been one thing, reach would
multiply by the number of windows a player had open. The map's menu says *Open a shell* and *Move
vantage here* precisely so the two never blur.

⚠ **`engine/fs/VirtualFs` generates every machine's filesystem and stores none of it.** Not for save
size — a stored tree would be a cache of game state that eventually disagrees with it, on the exact
surface a player uses to decide whether a machine has been tampered with. A deployed miner is a unit
file in `/etc/systemd/system` because `deployedMiners` is non-empty. Seeded on the address, so a
listing never reshuffles between visits — which is what makes "was this here before?" answerable.
**Nothing in the package touches a real filesystem**, and `normalise` resolves `..` textually and
cannot climb above `/`.

**The rig monitor has an ABOUT tab (2026-07-29)** — sixth and last, after NETWORK. It carries **Mr. Monitor**, the hand-drawn uOS mascot (`client/.../ui/mascot.png`, from `docs/pngs/`), and a spec sheet: client version, build architecture, runtime, host OS, CPU, GPU, memory. It sits *after* the four table tabs rather than inside them because everything to its left reports the **fictional** rig and this one steps out and reports the player's real hardware. Like `calc` it takes no `GameSession`.

⚠ **`SystemReport` starts no process and opens no host file, and that costs two readouts their specificity.** There is no JVM API for a CPU brand string, and — measured against JavaFX 26 — none for the GPU either: `GraphicsPipeline.getDeviceDetails()` returns context pointers, `GLFactory` is package-private and *prints* its driver info to stdout. So the CPU is cores + architecture and the GPU is the render pipeline plus hardware/software, which is the question a player with a stuttering deck actually has. `Apple M4 Max` needs `sysctl`/`/proc/cpuinfo`/WMI — three platform paths, one a subprocess, in a client that has never spawned one. A footnote on the panel says so, because an unexplained `16 CORES · AARCH64` reads as failed detection.

⚠ **Every lookup in it degrades to `UNAVAILABLE`; none may throw.** `com.sun.management` is a JDK extension, the prism pipeline is reachable only on a classpath launch (`javafx:run` is module-path, so it reports `HARDWARE` alone), and `build.properties` is absent if Maven never filtered it. Two specifics: the reflection catches **`Throwable`**, because a module-path failure is an `IllegalAccessError`; and memory casts to the exported *interface* `com.sun.management.OperatingSystemMXBean`, never the impl class, which `jdk.management` does not export. Also `Platform.isSupported` **initialises** the graphics pipeline rather than querying it — a test asserting "no toolkit, so SOFTWARE" failed with `OPENGL · HARDWARE`.

⚠ **The client's own version comes from a Maven-filtered `build.properties`, not the jar manifest.** The client runs from loose classes in an IDE, a shaded jar, and a jpackage image; a manifest exists for one of those three. ⚠ **Exactly one resource is filtered, by name** — `client/pom.xml` has two `<resource>` entries over the same directory, because filtering the whole of it rewrites the two TTFs and the mascot PNG byte for byte and eats any `${...}` in a term page. `-Dclient.version=` overrides it, since a release is named after its tag while the POM is not.

⚠ **The mascot sits on the bare panel with NO plate behind it, and one was built and rejected.** The reasoning for a plate is sound — the drawing is black ink on white, so on the deck's ground the outlines sink into the dark and the gloves and shoes lose their edges — and it looked wrong anyway, putting the only light surface in the whole client on one tab. Rendered both, kept the transparent one. Notes to that effect sit in `theme.css` and `RigAbout` so the "fix" is not re-attempted.

⚠ **ETHECOIN DIVIDES TO 18 PLACES (2026-07-30), and a `long` cannot carry it.** The unit is `1e-18`
EC — ether's relationship to wei. At that scale a `long` tops out at **9.22 EC**, less than one
firmware image, so `Ethecoin` carries a **`BigInteger`** and the server’s `bigint` column became
`numeric(78,0)` (`V2__core_schema.sql`).

- **Display trims trailing zeros** — `0.05 EC`, `500 EC`, `0.037097927036961408 EC`. A fixed
  `%.18f` would put eighteen characters of noise on every ledger row. ⚠ Trimming is never rounding.
  ⚠ `stripTrailingZeros` leaves a **negative scale** (`500` → `5E+2`); `toPlainString` guards it.
- ⚠ **Ratios stay `double`; amounts do not.** A payout fraction, pool fee, gas price and buffer fill
  have no scale. Past 2^53 (~0.009 EC) a double cannot hold a wei integer exactly, and that residue
  now lands inside digits the formatter prints.
- ⚠ **NPC fees/transfers are drawn in HUNDREDTHS and scaled up.** A uniform draw across the wei range
  gives every transaction an eighteen-digit tail and the mempool reads as machine output.
- ⚠ **`miningResidueWei` is a `BigDecimal`** — as a double it would absorb rounding error rather than
  prevent it, which is the opposite of what the residue is for.
- ⚠ **EVERY renamed money field carries `@JsonAlias` with its OLD key, or the save is lost.** Jackson
  has `FAIL_ON_UNKNOWN_PROPERTIES` **off**, so an unrecognised key is *silently dropped* — a real
  pre-wei save loaded as **0 EC across the board** and the rescale multiplied zero. It surfaced only
  because `ContributionState.creditedWei` lacked an initialiser and threw; every other field failed
  quietly. ⚠ **All money fields must initialise to zero**, never be left null.
- ⚠ **A migration fixture built with the CURRENT code cannot catch a rename.** The first check did
  exactly that and passed against the broken build. `LegacySaveTest` pins **literal old-format JSON**
  and was verified to fail without the aliases.
- ⚠ **Save migration is gated on `GameSave.moneySchema`, never a heuristic** ("is this balance small?"
  is unanswerable — 8 wei is legal). Multiplies by 10^16, once, logged; `newCharacter` stamps the
  current schema; the `MISSING` fee sentinel is skipped.
- ⚠ **Server columns were RENAMED `_ec_minor` → `_wei`, not just retyped.** The suffix is what
  `EconomyColumns` keys on to refuse an I1 conversion, so it has to name the truth. The multiply is in
  the same statement as the widening.
- ⚠ **`send` parsed through `Double.parseDouble`** — the one place a player types an amount, and a
  double holds ~16 digits. Now `Ethecoin.ofDecimal`, which REFUSES finer than 18 places.
- ⚠ **The mempool's fee figures are AMOUNTS, not gas prices.** `lowFeeWei`/`highFeeWei` were
  fee-per-million-gas and printed `5319047619047619000` once amounts were wei. Amounts are also the
  better fix for what the pairing was *for*: mixed units made an under-4× spread read as 180×. The
  block table's gas-price column is gone for the same reason.
- ⚠ **Economy anchors are asserted to DOUBLE precision, not to the wei.** The rate derives through
  `chainNetworkHashrate()`, a double; bit equality asserted a precision the model has never had.
- **Verified unchanged:** subsidy 160 EC, fees 0.02/0.06/0.30, firmware 180 EC, 0.4 EC/cycle-hour,
  loot 3–6…45–65, and the *derived* network hashrate still lands on exactly **1680 cycles**.

⚠ **`Ethecoin.format(long)` is the ONE money formatter — there were thirteen, and twelve were wrong
(2026-07-30).** They read `String.format("%d.%02d EC", m / 100, Math.abs(m % 100))`. Integer division
truncates **toward zero**, so between −1 and −99 the whole part is `0` and `-0` is `0` — **the minus
sign vanished**. Every fee in the game is 2, 6 or 30 minor units, so **every fee row in the LEDGER
rendered as a credit.** (`BalanceReadout`'s `%.2f` copy was accidentally correct, which is why the two
never visibly disagreed.) ⚠ It takes a **`long`, not an `Ethecoin`**: the value type is non-negative by
construction, a ledger *delta* is signed, and that is the seam. ⚠ `Math.abs` goes on the whole part,
never on `minorUnits`, so `Long.MIN_VALUE` cannot overflow to a negative magnitude. ⚠ `EthecoinTest`
used to assert **no** formatter may exist here, on the grounds that one would "invite a second, subtly
different formatter" — backwards: a type with none invites thirteen. The surviving half is still
enforced by reflection: no `Locale`-taking overload, because localization is the client's.

⚠ **`Ethecoin` and `Cycles` render themselves, and the old "no display formatting" note is REVERSED
(2026-07-30).** A record's generated `toString` is *the thing you get by accident*: `"you have " +
balance` compiles, renders without complaint, and printed `Ethecoin[minorUnits=480]` at the player on
**five** surfaces before anyone noticed — a delete confirmation, a storage log line, `inv`'s balance
row, a balance readout, and a refusal about what they could afford. `Views.spec` had already grown a
comment warning the next person. The correct formatter existed as **eleven private copies** of
`money(long)`, which is precisely why nobody reached for it. A footgun that fires five times and
acquires a folk warning is a defect in the **type**. The localization argument survives — a
*localized* amount is a different method and still the client's — what was missing was a safe
canonical default (`Locale.ROOT`). ⚠ Nothing serialises through `toString`; the wire form is
`minorUnits()` and a test pins that. `Cycles` got the same treatment **before** it fired.

**Files can be deleted from your own rig (2026-07-30).** `Repac.delete`, the file manager's Delete
entry, and `rm` in the node shell. Downloads accumulated and nothing removed them.

- ⚠ **Only files the rig actually STORES.** The system tree, app bundles and vault views are
  *generated* by `VirtualFs` and stored nowhere — there is nothing to delete, and the refusal says so
  rather than succeeding and leaving the entry on screen.
- ⚠ **Own rig only.** `AccessLog` already holds that a remote actor **blanks** a log line rather than
  deleting it; a remote delete would grant exactly what that rule refuses.
- ⚠ **An image being flashed cannot be deleted** — `completeFlash` drops a task whose image is gone,
  so without the guard you delete mid-write, wait out the minute and get nothing.
- ⚠ **The GUI confirms; the shell's `rm` does not.** Real `rm` does not ask (that is `rm -i`), and a
  terminal that behaved otherwise teaches something false about a command the manual documents. The
  dialog and the log both name the **resale value** — "delete this file?" and "burn 108 EC?" are
  different questions.
- ⚠ **`rm -rf /` is safe and tested.** `-rf` is not a known flag so it swallows the operand ("missing
  operand"); `rm /` gets "Is a directory". ⚠ The root resolves to **no entry** — `entry()` lists a
  path's *parent* and `/` has none — so that branch is explicit. **No recursive delete, ever:** the
  tree is generated from game state, so there is nothing to walk.

⚠ **`PackageView` caps its width and scrolls; it must not size itself from its content.** It pinned
`setMinWidth(640)`, so the two 71-character SHA digests set the width of the whole window. Now 560 +
`setFitToWidth(true)` — **without `setFitToWidth` a `ScrollPane` hands content its *preferred* width,
so nothing wraps and a horizontal scrollbar appears instead.** Digests are **wrapped, never
shortened** (an elided middle is where a substituted payload hides). ⚠ **Actions are PINNED below the
scroll** — a long refusal would otherwise push Install past the fold, which reads as no Install
button. ⚠ An unstyled `ScrollPane` paints Modena's white viewport; style the `.viewport` too.

**Firmware FLASHES, it does not install (2026-07-30).** `.frm`, a 90-second `save.tasks` task, the
affected tool frozen throughout, behind a full-panel overlay with a drawn warning mark, what is being
written, a bar and a countdown.

- ⚠ **`.pkg → .frm` for firmware, `.pkg → .upg` for software — the `.pkg` stage is UNCHANGED.** That
  rename *is* the confirmation lock; naming firmware `.frm` at both ends leaves it with no rename to
  make and a bought image goes flashable before its payment is mined.
- ⚠ **Raising self-mining is refused for the whole flash; setting it to ZERO never is.** Trapping a
  player's cycles inside a tool they cannot use is a bug wearing a rule's clothes.
- ⚠ **Settled by `tick()` AND `resume()`; the image is consumed on COMPLETION, not at the start** — an
  interrupted flash must cost nothing rather than everything.
- ⚠ **90s is not derived from image size**, unlike a transfer: a flash is bounded by the device
  writing itself, not by anyone's uplink.
- ⚠ **The warning mark is a drawn `Polygon` + two `Region`s, never a glyph** — `U+26A0` is in neither
  bundled face and `GlyphCoverageTest` already rejected it once.
- ⚠ **The bar is `Pulse.every` (data), and its fill is BOUND to `track.widthProperty()`.** Setting
  `prefWidth` from `track.getWidth()` reads 0 before the first layout pass — the bar was empty on the
  opening frame and in every render. Caught by a snapshot, invisible in review.

**Firmware upgrades are a class, and the mining tool's is the first (2026-07-30).** `UpgradeKind
.FIRMWARE`. Needs the **schematic** *and* a **software component** (the image — bought or stolen),
costs more than any ordinary upgrade, and **the affected tool must be stopped to flash it**. All
three are the real behaviour of firmware, which is why they are worth having. `docs/design/11` §3.

- ⚠ **NOT a second gate — I3 is intact.** `design/02` §1.1 already sanctions this split ("Rainbow
  Table is EC + schematic: buy the table, the capability to use it is found") under its condition
  that the **ceiling component sits on the non-EC side**. The schematic is the ceiling and the image
  is inert without it, so `11` §4 rule 1's "no EC path, no exceptions" holds. §4 rule 2 checked too:
  it touches mining income and adds **no cycles** (**I1**).
- ⚠ **Schematic checked BEFORE the running tool.** A player missing both who is told to stop mining
  loses their hashrate and then hits a schematic refusal they were never going to clear.
- ⚠ **Deployed miners count as "running", and that is the half nobody thinks of.** They spend the
  *host's* compute (**I6**) so the player's own rig looks idle — but it is this rig's mining software
  driving them. The refusal names the count; "the tool is running" sends them to the wrong readout.
- ⚠ **A refusal, never an offer to stop mining for them** — that silently costs income they did not
  agree to lose.
- ⚠ **`Offering`'s compact constructor REJECTS firmware with no schematic named.** That one omission
  is what turns firmware into a ceiling reachable with money alone, and it is exactly the edit a
  second firmware item would make by accident.
- ⚠ **The image must stay dear enough that stealing one beats buying it.** `design/01` §6's raiding
  route is what the two-part requirement leans on; a cheap image makes the breach dead content.
- Named `<tool>-firmware.pkg`, not `-upgrade.pkg`, so `ls` shows the class before anything is spent.

**Upgrades carry a version, and Get Info answers before you take one (2026-07-30).** A foreign
`.pkg` used to be opaque — 40–320 MB with no way to learn what it was without paying for the
transfer. `GameEngine.upgradeAt` reads the package's own metadata; the file manager renders a compare
block and `stat` prints the same facts (one source, two surfaces).

- ⚠ **A newer build is worth more and SUPERSEDES an older one. It is NOT a better tool.** The
  better-tool reading was offered and rejected: a capability rising with the hardness of the machine
  you take it off is a ceiling reachable by grinding with no gate on it (**I2**), and the item would
  sit behind two gates (**I3**). The only mechanical effect is resale value.
  `UpgradeVersionTest.Capability` walks the whole catalogue to hold that line — keep it above all the
  others here.
- **The major tracks the HOST's tier**, so hard estates carry newer software; the market ships the
  **middle** of the ladder (`MARKET_UPGRADE_VERSION_MAJOR` = 3) so neither raiding nor buying is
  dominated. Deterministic from item + host, never drawn.
- ⚠ **Two ints, not a string** — lexically `v1.10` sorts before `v1.9`, so the one question the type
  exists for would get a wrong answer that looks right. Parsed tolerantly (it is a save field).
- ⚠ **Recorded at ARRIVAL, never re-derived.** A host's tier can change, and a re-derived version
  would silently change build while the package sat in Downloads.
- ⚠ **An unversioned held item is OLDER, not SAME** — "you already have this build" about a build
  nobody knows the number of is a claim the game cannot support.
- ⚠ **Some bundles advertise upgrades for tools the catalogue does not carry** (`Breach.app`,
  `Mining.app`). Those packages were always duds that `install` refuses *after* the transfer is paid
  for; Get Info now names it beforehand. Filling the gap is content, not code.

⚠ **`NetRules.reconcileFootholds` is what makes a breached machine YOURS, and for a while nothing
called it.** It was written, documented and covered by five tests — every caller was a test — so a
cleared breach left the machine reading `contact` on the map, refusing `connect`, and still holding
its loot. Now `GameEngine.settleBreachOutcomes`, called from **`resume()` and `breachAction`**: the load
path too, or the bug is permanent for any save that already breached something. Safe to call freely —
it is idempotent *by construction* (`foothold` and `looted` are one-way flags, so there is no settled
marker to desync).

- ⚠ **The failure shape, not the fix, is the lesson.** Both pieces were correct and both suites green;
  the defect was in the join, where a unit test cannot look. `NetRulesTest` even carried a comment
  saying the caller existed — true of the design, false of the build. **A comment describing a caller
  is not evidence of one.** `FootholdAfterBreachTest` tests one level up, against `GameEngine`, which is
  the lowest level the bug is visible at; verified by neutering the fix first.
- ⚠ **Map visibility keys on `knownNodes`; port scanning keys on `host.discovered`.** Two notions of
  "found" that agree only because a sweep sets both. A fixture setting just the flag yields a host the
  map has never heard of, failing with `NoSuchElement` rather than anything that names the problem.

**NPC MACHINES AND OPERATORS HAVE NAMES, AND THE NAME IS NOW A PORT-SCAN FINDING (2026-08-07).**
`engine/net/NpcNames` holds three pools; machines are Docker-style `adjective-pioneer` (the pioneer
from computing, mathematics, physics, quantum and astronomy), operators are ordinary given names.
`PortScanTarget.IDENTITY` is a new **eighth rung at the bottom** of the port-scan ladder.

- ⚠ **DERIVED FROM THE ADDRESS, NEVER DRAWN — and the hash is FNV-1a for a measured reason.**
  `TopologyGenerator`'s draw count is a pure function of the world's shape, so one draw per machine
  would re-roll every existing world; `DocumentPool` already solved this by hashing. ⚠ **`String
  .hashCode` is `31·h + c`, so addresses differing by one in the last character land one apart and a
  modulo walks the pool in order.** The eight-name array in `VirtualFs.hostUser` did exactly that:
  measured, the first machines of every server were `wren dana kai morgan riley sasha toma ves` — the
  pool in declaration order, offset by the server index. The "random" name was the host index in
  disguise. Second time this trap has bitten here, so the hash lives in one place now.
- ⚠ **THE LOCKSTEP GUARD TOOK THREE ATTEMPTS AND THE FIRST TWO PASSED AGAINST THE BROKEN HASH.** The
  march happens **only** between addresses differing by one in their *final character*. A run from
  index 0 crosses `10.s.0.9 → 10.s.0.10`, a character longer, and breaks on its own; a run of
  same-length addresses still breaks at `…19 → …20`, where the delta is 31−9. The working test walks
  one **decade**. Each version was caught only by reinstating `hashCode` and watching it pass —
  which is the rule this repo already has, and the third place it has paid for itself.
- ⚠ **NAMES ARE DE-COLLIDED GLOBALLY, and the set is threaded through the whole generator.** 184 × 159
  is 29,256 combinations against a few hundred machines, so by the birthday bound a duplicate is
  *expected*, not unlikely — and two machines called `bold-turing` is worse than a dull name, because
  the map, the list, the shell prompt and the recon file all key a machine by what it is called.
  Resolved the way Docker resolves it (keep looking) but deterministically: walk the adjectives from
  the hashed start, then advance the pioneer. Zero draws, so the RNG contract is untouched.
- ⚠ **NO ADJECTIVE MAY BE DEMEANING.** Real people, much of the pool living. Docker special-cases
  `boring_wozniak` out with the comment that Steve Wozniak is not boring; a rule over the whole list
  is the version that cannot be defeated by adding one more word. ⚠ The rule is **"not demeaning",
  not "complimentary"** — the pool is deliberately moody and a little suggestive, which is atmosphere,
  not a judgement of the person. ⚠ **Surnames only, one word**: the separator is a hyphen, so
  `bold-berners-lee` could not be split back into its halves, and RFC 1123 governs the result because
  these reach `Hostname`'s vocabulary.
- ⚠ **A MACHINE NAME MUST NOT ENCODE WHAT THE MACHINE IS** — naming a node's type is the Passive
  Sniffer's published function (`design/07` §1). The old `<server>-<index>` scheme leaked it quietly:
  host index 0 is the gateway on **every** server, so a trailing `-00` was a free and completely
  reliable tell. An adjective and a surname correlate with nothing.
- ⚠ **A SWEEP NO LONGER NAMES THE MACHINE, and that is the behavioural change.** `NetRules.nodeFor`
  used to copy `host.label` into the player's knowledge, so every machine arrived named and `IDENTITY`
  would have had nothing to sell. `Sighting.label` is read off the **recon file** now, not off ground
  truth — one stored answer, in the one place that applies the write-once rule. `NodeState.label` is
  left empty and nothing reads it.
- ⚠ **ADDING A RUNG AT THE BOTTOM DID NOT RE-PRICE THE SEVEN ABOVE IT, and the naive version would
  have.** Every cost was `f(depth)`, so inserting `IDENTITY` at depth 1 shifted all seven up a number
  and would have raised their cycles, duration, noise **and** detection risk as a side effect —
  silently, since every screen still renders. The formulas key on **`PortScanTarget.steps()`**
  (`depth − 1`); `IdentityFindingTest.theCalibratedRungsAreUnchanged` pins the seven literally,
  because a formula compared against itself proves nothing. ⚠ `IDENTITY_SECONDS`/`IDENTITY_NOISE` are
  floors that bind on the bottom rung **and provably nowhere else** — at `steps ≥ 1` both multipliers
  already exceed them. A rung that took no time and made no sound would be one nobody decides about.
- ⚠ **THE STORED IDENTITY IS WRITE-ONCE; EVERY OTHER FINDING ON THE FILE REFRESHES.** A firewall tier
  is a measurement and a rescan should re-read it. A name is an identity — and because names are
  *derived*, editing a pool shifts every derived name at once, so pinning at first contact is what
  makes "the operator you found when you first broke in" a fact about that break-in rather than about
  the current build. The rule lives in `NodeReports.merge`/`establishIdentity` and is one `if` from
  becoming a refresh.
- ⚠ **A BREACH ESTABLISHES THE IDENTITY AND NOTHING ELSE.** Standing on a machine, the name and the
  account are in the prompt; the vault estimate is not. Hooked into `NetRules.reconcileFootholds`
  **outside** the foothold guard, so a machine breached before this existed gets a name on the next
  load — safe because that method is idempotent by construction and runs on every resume. ⚠ It does
  **not** bump `scans`: a file whose only entry came from a break-in has had no scans, and reporting
  one would make the detection ratio beside it a fraction of a number that never happened.
- ⚠ **`UiTokens.NET_NODE_LINES` WENT 4 → 5, and the slot is reserved whether or not a name is known.**
  A box that grew a line when a scan came back would re-flow the whole map underneath the player.
  ⚠ **The operator rides on the ADDRESS line and the machine name takes its own**, and the widths are
  why: the widest address is `10.6.0.51` (nine), leaving **seven** for an account after the gutter and
  a separator — so **operator names are capped at seven characters**, which is the only reason
  `ragnhild` and `torbjorn` are not in the pool. A machine name does not fit at all (23 at worst), so
  it takes a line and ~2.5% of combinations clip; the full name is on the tooltip, in the host list
  and in the RECON file.
- ⚠ **Existing characters keep their old `<server>-<index>` names.** `TopologyGenerator.generate`
  returns early when a topology exists — that guard is what stops a player rerolling the world — so
  the new scheme applies to **new characters only**. Nothing migrates it, deliberately: a relabelling
  pass is the legacy-save machinery this repo does not have.

**Recon decides which breach puzzle you draw (2026-07-29).** The class was an even coin flip; it is
now weighted by how complete the target's port-scan report is. **Offset Cipher is the DEFAULT** — the
puzzle that needs nothing from the far side — and **Breach Protocol** is the puzzle of someone who
knows the host, so a full report draws it ~95% (`Balance.breachProtocolShare`, linear at one eighth
per finding — one seventh until `PortScanTarget.IDENTITY` became an eighth rung on 2026-08-07; the
step is `1 / values().length` and was never a literal). This is RECON's first mechanical consequence: a report used to be intelligence read by
hand, and now it changes what the breach *is*.

- ⚠ **It buys a DIFFERENT puzzle, never an easier one.** Tier, attention, strikes, layers and cycles
  are identical either way (`BreachPuzzleWeightingTest.Pricing`). **If the two ever stop being
  comparable in difficulty this becomes a discount**, and a proof-of-skill gate that can be bought
  down is not one (**I7**) — re-check it whenever either puzzle is re-tuned.
- ⚠ **0.95, not 1.0.** A guaranteed puzzle means the cipher stops being practised by anyone who
  scans, which is `design/16` §5's original failure returning. The class is announced before anything
  is spent, so the residual is a surprise the player can walk away from.
- ⚠ **The roll is taken unconditionally, even at weight zero.** Skipping it for an unscanned machine
  would make every later draw in the breach depend on whether the player had scanned — same seed,
  different boards. `design/16` §2's replay rule.
- ⚠ **Any breach fixture wanting BREACH_PROTOCOL must scan the target first** — `BreachTestKit
  .fullyScanned`. Without it the class is unreachable and the helper loops every seed and throws.
- ⚠ **Staleness deliberately does not count against a report.** A week-old finding still counts; its
  age is already on screen, and discounting it silently would move the odds with nothing changing.

**Two ring wallpapers (2026-08-02)** — `ring` and `ring-glitch`, the power-on emblem at desk scale.
`ui/widgets/RingField` draws it, `ui/widgets/Wallpaper` is the container `DeskManager.setBackdrop`
now gets (one backdrop node, two layers inside), and `GlowRing` gained a style-base parameter so the
splash and the wallpaper share one tuned recipe.

- ⚠ **NEVER IN AMBER.** §2.1 reserves amber for cycles doing work and income, and the design language
  says the reservation "matters most on the largest surface in the client" — the character wallpaper
  is held to `dim-3` for exactly this. The ring resolves **`-es-text-hi`**, which is also what makes
  **uOS Classic invert for free**: that palette runs the ramp the other way (`-es-void` `#A8A8A8`,
  `-es-text-hi` `#000000`), so the same token is a faint lit ring on the dark decks and a faint drawn
  one on the light. A literal colour is invisible in one or glaring in the other — the `DiskLamp` trap.
- ⚠ **SCALING THE OFFSETS WITHOUT THE STROKE WIDTHS BANDS THE GLOW** — the exact failure `GlowRing`'s
  own comment warns about, hit again at eight times the reference radius. The glow *is* the overlap
  between consecutive strokes, so both scale together. The widths therefore moved to Java for this
  variant (`GLOW_STROKES`) — a stroke width is a **size**, which this repo keeps out of the
  stylesheet, and a property CSS also declares would overwrite it on the next `applyCss`.
- ⚠ **`-fx-opacity` per node, not `rgba()`.** JavaFX CSS cannot apply an alpha to a **looked-up**
  colour — `rgba()` takes literal numbers only — and hard-coding channels is what the token exists to
  avoid. Each halo circle is its own node, so node opacity gives the same accumulating falloff.
- ⚠ **The glitch is SLICED GEOMETRY, not a filter.** §9 makes blur and drop shadows build-blocking, so
  the datamosh look comes from structure: the ring is drawn 26 times, each copy clipped to one
  horizontal band, and bands are displaced sideways. **At intensity zero the copies line up into one
  clean ring** — "no glitch" is this path at rest, not a second path that can drift from it.
- ⚠ **A triangle envelope, never a sine.** §5 permits no easing anywhere and an eased envelope is an
  easing curve however it is spelled. Slips come from a **fixed seed** so a render can be compared
  against the last one.
- **It never fully rests and the axis turns (2026-08-02).** The fault runs continuously between a
  `FLOOR` of 0.10 and a peak, and the slice axis flips **horizontal ↔ vertical** every cycle.
  ⚠ **The flip is a ROTATION of the whole stack**, not a second set of slices — the ring is a circle,
  so slicing it horizontally and turning it a quarter *is* slicing it vertically, and building both
  would double a node count already at 234 circles. ⚠ **Rotated about the DESK's centre**: a `Group`'s
  bounds are whatever its children occupy, so pivoting on those swings the ring across the screen
  instead of turning it in place. ⚠ **The slices therefore cover a SQUARE** of the longer edge — a
  band region shaped like the desk leaves two uncovered wedges the moment it turns. ⚠ **It flips at
  the floor**, because flipping mid-tear snaps every displaced slice across the screen at once.
- ⚠ **Displacement goes as `EXTREMITY` (2.2) power of the envelope, not linearly.** A linear ramp with
  the same peak spends most of its life visibly wobbling behind text, which is a legibility problem
  rather than an effect. At envelope 0.3 a slice moves ~7% of its full distance.
- **72 slices at a 70ms tick (2026-08-02).** ⚠ **Smoothness comes from a FINER LADDER, never from
  interpolation** — §5 permits no easing and §9 makes it build-blocking, so stepped motion is made to
  read as continuous by making the steps small, exactly as `UiTokens.REVEAL_STEPS` does everywhere
  else. ⚠ **`BANDS` is the expensive number**: each slice is its own nine-circle emblem plus two
  fringes, so nodes go as `BANDS × 11` (792). ⚠ The **per-tick** cost is not — a tick sets one
  translate per band, because the copy is a `Group` and the transform is on the group.
⚠ **A SUBSTRATE THAT HAS NEVER BEEN LAID OUT IN A DRAWING MODE STAYS BLACK FOREVER (2026-08-02).**
`Substrate.layoutChildren` early-returns while the mode is `OFF`, and it is the only thing that ever
computes `cols`/`rows` — which `advance()` and `repaint()` both bail on when zero. `setMode` requested
no layout, because the node's **size** had not changed. So starting the client on a ring wallpaper and
switching back to the character texture gave a permanently black desk: the ticker ran, every frame
returned immediately, and nothing anywhere reported a problem. `setMode` now `requestLayout()`s
whenever there is something to draw. ⚠ Reproduced with `DeckSnapshot -Ddeck.wallpaper=ring
-Ddeck.wallpaperSwitch=drift` — a deck built **straight into** drift renders it correctly, so the
switch is the whole bug and a start-up-state test would have passed.

- **Colour shift is `wallpaperChromatic`, off by default (§9.1), and applies to BOTH wallpapers.** ⚠ **Literal `rgba` outside the
  palette**, taking the same licence `.es-substrate-warm` already documents: a convergence error is a
  property of the phosphor, not of the design system, so borrowing `-es-alarm` would make the
  wallpaper look like it was reporting a loss. Not the semantic colour system §2.1 bans — an artefact.
  ⚠ **The fringes are the CORE circle only**, never a whole halo: a fringe is an edge artefact, and
  giving each one the nine-circle emblem triples the node count. ⚠ **Stroke, never fill** — a filled
  circle puts a coloured disc behind every window. ⚠ It **scales with the slice's own displacement**,
  so colour appears where the ring has torn and nowhere else — and on top of that it **intensifies
  and falls back on its OWN period** (`CHROMA_CYCLE_STEPS`, co-prime with the tear cycle, so the two
  drift in and out of phase; two effects locked to one clock read as one effect and make the loop
  obvious). ⚠ Its **opacity is driven from Java, not CSS**: it changes every tick, CSS cannot be
  driven on a clock, and a value declared in the stylesheet would overwrite the Java one at the next
  `applyCss`. Same split as the stroke widths.
- ⚠ **On the character texture it drives the aberration layers**, pulling them apart and back on
  their own period — one setting for whichever wallpaper is on, because a per-wallpaper duplicate is
  two controls that look identical and do the same thing. ⚠ **It holds still in a paused mode**:
  `STILL` is WCAG 2.2.2's pause, and colour that kept breathing there would be motion the player had
  explicitly stopped. Only `DRIFT` cycles; `STILL` holds the midpoint.
- ⚠ **Renamed from `ringChromatic` once it stopped being ring-only**, with a setter hook for the old
  key — Jackson has `FAIL_ON_UNKNOWN_PROPERTIES` off, so without it the old key is silently dropped
  and the player's choice quietly reverts.
- ⚠ **`WallpaperMode.moves()` is WCAG 2.2.2 made checkable.** `RING` is to `RING_GLITCH` what `STILL`
  is to `DRIFT` — not a lesser version, the pause. ⚠ `ScreenArtefactTest` asserted `values()).hasSize(3)`
  for this; a **count is not the rule** and fails on any new mode whether or not it obeys 2.2.2. It now
  asserts every moving mode has a still counterpart.
- ⚠ **The ticker follows the SCENE, not the setting.** A `Pulse` subscription on an off-screen layer is
  work with no observer — and because `Pulse` needs a live toolkit, subscribing from a plain setter
  made the widget untestable without starting one, which this repo keeps to a single file.
- ⚠ **Rendering it needs BOTH flags.** `-Ddeck.wallpaper=ring-glitch` alone photographs the clean ring:
  the cycle starts at rest and no `Pulse` tick runs in a synchronous render, so the harness reports the
  effect as working by capturing the one state indistinguishable from it being broken.
  `-Ddeck.glitchPhase=0.775` is the peak. Only the **bare-desk** frame shows any of it — every other
  snapshot tiles windows edge to edge.

**Nothing transient may occupy space in the top strip (2026-08-02).** The balance delta was a third
`Label` inside `BalanceReadout`'s row, so the cell got **wider for as long as it showed** — pushing
the strip past its width budget and wrapping the chrome onto two rows every time the player earned
anything, then springing back 1.4s later. It is now `ui/BalanceDelta`, an overlay under the cell.
Same defect class as the empty refusal cell, same rule.

- ⚠ **The counting animation did NOT move.** `BalanceReadout` still steps the figure to its new value
  on `Pulse`; only where the delta *chip* is drawn changed. It reports movements through a
  `Consumer<BigInteger>` sink so the widget stays buildable without a deck around it.
- ⚠ **`ui/Anchoring` is shared by both overlays**, because getting one on screen cost four debugging
  rounds and none of them produced an error message: `getLayoutBounds()` vs `getBoundsInLocal()`, an
  unmanaged node never being resized, both bounds properties on both anchors, and `applyCss()` before
  measuring. Its class comment is the list.

**The LOAD sparkline has four intensity steps and spikes on a paid block (2026-08-02).**

- ⚠ **The AMBER LADDER, not a traffic light.** §2.1 bans a semantic colour system and §2.1a's
  carve-out is fenced to two named sites (balance delta, network nodes), so green/amber/red was not
  available — and amber is the right answer anyway: §2.1 already spends it on "cycles doing work",
  which is exactly what load is. `dim-2 → amber-low → amber-mid → amber`.
- ⚠ **`alarm` is deliberately NOT the top step.** §2.1 reserves it for loss and hostile state and
  rations it to twice a screen; a busy rig is not a hostile one.
- ⚠ **Intensity is the cell's HEIGHT in the column, not the sample's value** — how hard the rig is
  working is already read off how far up the column goes, so colouring by value would say nothing the
  height did not.
- ⚠ **The spike is added to the DRAWN fraction only.** A block is instantaneous and has no load to
  sample, so nothing would ever appear in a history chart of load without this. The reading beside the
  label stays the real `n/100C`, because that is a measurement.
- ⚠ **Driven by `BlockContribution`, not the ledger.** A ledger credit is any money arriving — a sale,
  a collection — and spiking LOAD for those claims work the rig did not do. ⚠ **Both `won` and a
  positive credit count**: a share pool pays for accepted shares whether or not that block was the
  pool's, so testing `won` alone leaves a pooled player's chart flat while their balance climbs.
- ⚠ **`lastContributionHeight` seeds from the first tick, not from zero.** The chain runs while the
  client does not, so on any load the newest contribution is almost always older than the session — a
  zero seed spikes for a block that landed before the player arrived.

**The chain-sync report drops from the BALANCE cell, not the LEDGER window (2026-08-02).**
`ui/SyncBanner` hangs `view/ChainSyncPanel`'s node under the top strip on load. `ChainSyncPanel` is
unchanged — only the caller moved. `DeckShell.showChainSync()` consumes `takeChainSync()`; the ledger
no longer does, so it cannot repeat it.

- **Why it moved:** the report is about the balance, which is on screen always, and it used to sit on
  a tab of a window nobody was prompted to open. A once-per-session announcement behind two clicks is
  one most players never saw.
- ⚠ **It emerges from BEHIND the strip, and the CLIP is what does that.** This layer paints *above*
  `deckRoot`, so sliding from `translateY = -height` would draw the panel over the readouts on the way
  past. The container sits at its final place and is clipped; the **content** moves inside it.
- ⚠ **`getLayoutBounds()`, NOT `getBoundsInLocal()`.** On a `Parent`, `boundsInLocal` is the union of
  its **children's** bounds — the top strip reported **957px** tall on a 900px window, putting the
  panel off the bottom of the screen while every number in the calculation looked plausible.
- ⚠ **An UNMANAGED node is never resized by its parent.** `setManaged(false)` is what lets the banner
  be placed by translate, and it also means `setPrefSize` is a request to a layout pass that will
  never run on it — `getWidth()` stayed 0, the content laid out into nothing, and the clip cropped the
  remainder. It must `resize()` itself. Same family as `DeskManager`'s managed-child trap, from the
  other side.
- ⚠ **Two anchors, not one.** X follows the balance **cell** (right-aligned; the panel is far wider
  than the cell); Y follows the **strip**. A cell is centred in a taller strip, so anchoring Y to the
  cell puts a few pixels of panel over the readouts — measured at 27 against 31. It looked right, and
  was right by luck.
- ⚠ **Positioning is LAYOUT-driven, never `Platform.runLater`.** A deferred call is a hope that one
  layout pass has happened; it fires too early on a slow first paint and never at all in a synchronous
  render. Listens to `layoutBounds` **and** `boundsInParent` on both anchors and the parent — the two
  report different things (size vs position) and both are needed.
- ⚠ **`applyCss()` the panel before measuring it.** Its padding, font and border are all stylesheet,
  so `prefWidth(-1)` on a node that has never had CSS applied is **zero**.
- ⚠ **The dwell starts when the SUMMARY lands** (`onDone`), not when the panel opens — the 1.8s replay
  is theatre the player cannot read, and starting the clock at the open spends a third of the reading
  time on it. Click dismisses sooner.
- ⚠ **`DeckShell.showChainSync(ChainSync)` is a render seam.** The report only exists after a real
  absence, so a snapshot needs to feed one in rather than doctor a save's timestamps.
  `DeckSnapshot -Ddeck.sync=1`.
- ⚠ **A stand-in strip in a focused harness LIED** — it reported the anchor misaligned when the real
  deck was fine, and would have sent the fix the wrong way. Deleted; the real-deck flag replaced it.
- ⚠ **THE PANEL IS NOT A FIXED SIZE, and the clip has to follow it.** `ChainSyncPanel` adds its
  summary lines when the replay finishes, ~2s after the banner opens. Measuring once at build time
  left the clip at the pre-summary height and **cut the report off mid-sentence** — with the part the
  player actually needs below the cut. Two causes, both needed fixing: the holder was a plain `Pane`,
  which computes its preferred size from where children *are* rather than what they *want*, and
  nothing watched the content for growth. Holder is a `StackPane` now, plus a `layoutBounds` listener
  on the panel.
- ⚠ **A SNAPSHOT CANNOT CATCH THAT.** Render harnesses run under reduced motion, where the panel
  paints its finished state on the first call — the content never grows and every frame looks right.
  `SyncBannerTest` grows the content after placement instead; verified against the unfixed code
  (clip stayed at 90).

**Commands declare their own schema, and there is now ONE way to declare a command (2026-07-31).**
`shell/Commands` is a builder; `shell/CommandSpec` is what a command takes; `shell/CommandCategory`
is which drawer it sits in; `i18n/Messages` supplies the prose. All 51 registrations across the four
registries go through the builder, and the terminal's right-click menu drills down by category and
offers each command's **real** options instead of three universal flags.

- ⚠ **STRUCTURE IS CODE, PROSE IS TEXT — the whole design turns on this line.** Command names, flag
  names and choice values are **never** translated: the parser has no other name for them, real Unix
  does not localise them, and pillar **C6** sells skill that transfers to a real terminal. Localising
  `grep -v` would take that away from exactly the players a translation exists to serve. A spec
  therefore carries a **message key**, never a sentence.
- ⚠ **There were FOUR declaration shapes and that is why the spec had nowhere to live.**
  `BuiltinCommands` had `source`/`filter`/`action` helpers; `NetCommands` and `BreachCommands` each
  had a private `Verb` record with the same six components in the same order; `ClientCommands` had a
  five-component `Simple`. Anything a command needs to carry had to be added in four places, so it
  never was. One `Commands.Definition` is the only `Command` implementation now.
- ⚠ **A declared flag is a CLAIM ABOUT THE BODY, and `CommandSpecTest` holds both directions.**
  Declared-but-unparsed puts a flag in the menu that the parser ignores — worse than a short menu,
  because the game has told the player something false with nothing on screen to contradict it.
  Parsed-but-undeclared means a new flag works from the keyboard and is undiscoverable. Verified by
  breaking all four checks first; each named the exact defect.
- ⚠ **The test reads SOURCE, and not out of laziness.** No runtime call asks a lambda which flags it
  inspects — and **`ClientCommands.register` takes the deck, the themes and the profile**, so a test
  driven off `BuiltinCommands.registry()` silently checks nothing for that whole file while reporting
  success. That is the exact failure the class exists to prevent.
- ⚠ **Flags are attributed PER FILE, never per command.** A flag is read inside a lambda and there is
  no reliable textual way to say which lambda a line sits in — "nearest declaration above"
  mis-attributed `--signed` and `--bits` to `abort` and `verify` when this was written; they are
  `calc`'s.
- ⚠ **`flagText()` derives the dashes from the NAME'S LENGTH**, because that is what the parser does:
  `CommandLine` stores a flag under its dash-stripped token, so `-i` and `--ignore` are different
  keys and `hasFlag("h") || hasFlag("help")` has to ask for both.
- ⚠ **`grep -E` and `wc -l` are advertised in their synopses and NEVER PARSED.** Found by the reverse
  check and left alone — declaring them would be the lie the mechanism exists to stop. Either
  implement them or drop them from the synopsis; the spec deliberately does not paper over it.
- ⚠ **Category is the SUBJECT, not the pipeline behaviour.** Grouping was `isFilter`/`hasSideEffect`
  — true statements, and the wrong question for a menu: it filed `send`, `theme` and `mkdir` together
  under "Act". Those two stay exactly where they were and remain what `Shell` enforces. The enum is
  closed (a free-text group is one typo from a second menu with one command in it) and `values()`
  order **is** the submenu order.
- ⚠ **`Command.category()` defaults to `SHELL` so an undeclared command is still findable — which
  makes a missing declaration INVISIBLE.** `CommandSpecTest.everyCommandIsFiled` checks at the
  declaration site, the only place the omission is legible.
- ⚠ **`LocalCatalogueTest.nothingIsInvented` was WEAKENED deliberately, and only because the other
  half exists.** It used to assert the menu offered *only* the three universal flags — correct while
  a command could not declare anything. It now permits {universal} ∪ {declared}; the "no invented
  flag" property survives solely because `CommandSpecTest` proves declared == parsed. Weakening one
  without the other lets the menu invent flags again.
- ⚠ **The man page INDEX is always English, whatever the locale.** The index is which pages exist — a
  structural fact — so reading a translated one lets a partial translation *silently shrink the
  manual*: twelve of twenty-three rendered means eleven pages cease to exist, and a shorter manual
  looks exactly like a shorter manual. English decides which pages there are; the locale decides how
  each reads.
- ⚠ **Fallback is per KEY and per PAGE, never per file** — a partial translation is the normal state
  of one. A blank value means "not done yet" and does **not** overwrite English; a key nothing defines
  returns **itself** (blank is invisible, null is a crash, the key names what to add). Every page that
  fell back lands in `problems()`, so an unfinished translation is visible to whoever is finishing it.
- ⚠ **A translated page that EXISTS but is malformed reports as malformed** — `exists()` is asked
  before parsing rather than falling back on a parse error, which would hide the one problem a
  translator most needs to see.
- ⚠ **Bundles read as UTF-8 explicitly.** `Properties.load(InputStream)` is ISO-8859-1 *by definition*
  and mangles every accented character in exactly the files a translation puts them in.
- No command uses `section() == 8` today, so `LocalCatalogue`'s old "Rig maintenance" group was always
  empty. Sections are now purely the man page number; the menu drawer is `category()`.

**Settings → Language, and `i18n/Text` (2026-07-31).** `i18n/Language` is the shipped-language registry,
`i18n/Text` is the one place the client asks for a string. README's "Adding a translation" is the
procedure.

- ⚠ **Language is MACHINE-WIDE, not per character** — the same line `uiScalePercent` and
  `reducedMotionOverride` sit on. A palette is a costume; a language is whether the player can read the
  game, so per-character would hand somebody who needs Deutsch an English client on every new character.
- ⚠ **A BLANK setting means "never chosen" and is NOT "chose English".** The first may follow the host's
  language; the second must be obeyed on a German machine. Stored as the **tag**, not the enum, because
  the file outlives the build — an unknown tag falls to English rather than throwing.
- ⚠ **The language is resolved ONCE in `start()`, before `TermDatabase.load()`.** The manual is loaded
  there and never reloaded, so deciding later leaves `man` permanently English however the picker is set.
- ⚠ **`Language` is an explicit enum, never a directory scan.** A scan works from `target/classes` and
  stops working inside a jar (the trap `TermDatabase` already documents), and it would offer a language
  the moment one file existed — a half-empty language in front of every player.
- ⚠ **The picker shows ENDONYMS and is the one control identical in every locale** — `English · Deutsch
  · 日本語`, never `German`. A player who has landed in a language they cannot read must find their own,
  and their own is the only entry they are certain to recognise.
- ⚠ **`Messages.overlay` vs `Messages.load` is about which side owns English.** `commands` uses `load` —
  the bundle is the only place those sentences exist. `windows`/`ui` use `overlay` — `WindowSpec` carries
  its own English and `WindowSpecTest` asserts it against `docs/client/05`, so a `windows_en.properties`
  would be a second English that nothing keeps in step and the copy is the one that would rot.
- ⚠ **`WindowSpec.titleKey()` derives from the id**, which is already the stable identifier (it keys
  saved desk layouts). A translation therefore cannot point at a window that no longer exists.
- ⚠ **`unixAnalogue` is NOT translated** — it is real command names, same rule as flags.
- ⚠ **Never cache `Text.current()`** — same rule as `profile.appearance()`, same reason.
- **Every player-facing caption in `view/` is keyed (2026-07-31).** 170 sites: the 11 Settings
  categories, every switch, section heading and explanatory caption, plus panel headers and empty
  states across the other views. `Views.t(key, english)` is the call; other packages use
  `Text.current().ui(...)`.
- ⚠ **English stays at the CALL SITE and is the fallback — there is no `ui_en.properties`.** Moving
  these into a bundle and leaving a bare key was rejected: half of them explain *why* a setting is off
  by default, and that reasoning belongs where somebody changing the setting will read it. It would
  also make every one a two-file edit, which is how English and the thing it describes drift apart.
- ⚠ **A key per BRANCH of a ternary, never one around it.** The avatar and handle captions each say
  opposite things depending on state ("a picture can be set once a character is loaded" vs how to set
  one); one key around the conditional means a translation of either replaces both.
- ⚠ **An orphaned `ui` key FAILS THE BUILD — `UiKeyTest`.** English and its key sit in two files that
  nothing links, so renaming a caption's key leaves the German line matching nothing: green build,
  passing tests, and that one caption English forever. The test names the orphan. Verified by planting
  one.
- ⚠ **`ui_zz.properties` in `src/test/resources` is a TEST-ONLY pseudo-language**, absent from
  `Language` so no player can select it. Without it every i18n check asserts over an empty set —
  "translations work" would be a claim about machinery nobody had run. It translates exactly two keys
  and one window title, so the fallback path is exercised by the same fixture.
- ⚠ **Keys are derived from the English** (`ui.<view>.<slug>`), so a key names what it says and a
  reviewer can tell which string a translation is for. `UiKeyTest.keysAreUnique` holds that one key
  never carries two different sentences.
- ⚠ **`pages` map keys in Settings are the sidebar LABEL, the search needle and the selection
  identity** — all three follow the translation correctly, and nothing external looks a page up by
  name (checked). Selection is session-local, so nothing persisted breaks.
- **Deliberately left English:** `unixAnalogue` (real command names), `BezelStyle.note()` (enum-owned
  prose — keying it means keying the enum), and shell command output.

⚠ **An EMPTY strip cell is not a narrow cell — it is 29px and a divider (2026-07-31).** A cell is
`-fx-padding: 7 14 7 14` plus a 1px rule, so the top strip's refusal cell — empty almost always — spent
29px of the width budget on every layout pass. Measured on the real deck at 1200px: the strip wanted
**1113** and had **1104**, so it wrapped by **nine pixels**, doubling the height of the chrome and
pushing every window down. The dead cell was three times the overflow.

- ⚠ **`WrapStrip` now skips `!isManaged()` children**, and `DeckShell` binds the refusal cell's
  `managed` to whether the label has text. `setVisible` alone leaves the gap and the divider behind.
- ⚠ **Keyed on `isManaged`, deliberately NOT `isVisible`.** `layoutChildren` sets the spacer invisible
  when it wraps; keying off visibility would change the next pass's measurement, which would change
  whether it wraps, which would flip the visibility back — a strip oscillating between one row and two
  forever.
- ⚠ **Wrapping still happens when it genuinely must** (verified by render at 800px — two full rows).
  That is the 200%-UI-scale case `WrapStrip` exists for; this only stops it firing over a dead cell.
- `WrapStripTest` needs no toolkit — `Region` does its own layout maths, so it exercises the real
  `layoutChildren` rather than a reimplementation that would have agreed with the bug. Verified against
  the unfixed code first: all three checks fired.

**The store has daily stock and is branded GROUP OF HACKS (2026-08-04).** `rules/MarketStock` +
`SaveMarketStock`; `view/MarketView` scrolls, searches and shows stock.

- ⚠ **STOCK IS WORLD STATE, NOT CHARACTER STATE — hence a PORT.** On a server the shelf is shared, so
  a counter in `GameSave` would give every player a private one, which is the opposite of a limited
  item. `MarketStock.Held`: the save in solo (one player, so it matches LAN by construction), the
  server's table online (**W-7**, unbuilt, atomic take required — two buyers racing the last unit must
  resolve to one sale and one refusal).
- ⚠ **The RATION is derived from (item, day); only the COUNT TAKEN is stored.** Consumables 6–14,
  permanents 1–3 — inverted on purpose, since a consumable is rebought and a permanent is a race. An
  item on offer is stocked shorter but **never to zero**. ⚠ A gated item is **not stocked**, which is
  not "0 left": one says come back tomorrow, the other is never coming.
- ⚠ **Purchase noise rides on the download TASK**, so it is present-tense and ends by itself — no new
  mechanism, and it cannot leave a rig permanently loud. **Own home server silent** (I9's reasoning);
  **LAN, foreign federated and solo loud**. The `foreign` flag defaults to **true**, the loud
  direction, because a purchase that should have been observable and was not is invisible.
- ⚠ **The store balance is 2dp under the TOP STRIP'S licensed exception** — exact figure on hover and
  in `accessibleText`, ledger still exact. ⚠ `Ethecoin.formatApprox` appends the unit itself; a second
  `" EC"` rendered **"500 EC EC"**.
- ⚠ **No account-level nav row**, deliberately: the deck already carries identity and balance in the
  top strip and navigation on the rail. ⚠ **The deals strip is not filtered by the search** — the
  strip answers "what is worth buying today", not "what matches". ⚠ An empty section says so rather
  than vanishing.

**Every window shows its size while it is resized (2026-08-04).** `ui/chrome/SizeReadout` —
`840 × 520` bottom-right, held for `DWELL_MS` (900) and then stepped away. On every desk window
(`WindowFrame`) and on the outer window (`DeckShell`).

- **Not only convention:** the deck lays out on real breakpoints — the rail at `NARROW_WIDTH`, the
  cycle grid at 25/20/10 per row, the shelf at three tiles then two — so *which pixel* a layout
  changes at is something a player can watch happen. This turns "it went funny when I made it small"
  into a number.
- ⚠ **It STEPS away and is not a tween.** `REVEAL_STEPS` whole steps on `Pulse`, the same ladder
  `BalanceDelta` and `Motion` use. **`Fade` is deliberately NOT used** — that is the splash's
  continuous ramp, licensed by §5.1 for a title card the player is only *watching*, and this is
  chrome on a window they are working inside.
- ⚠ **`Pulse.every` — DATA, not `animate`.** Under Reduce motion `animate` never fires, so the dwell
  would never expire and every window would carry a permanent number in its corner: the accessibility
  path getting the *worse* behaviour, the exact failure the carousel already recorded. The clock runs
  in both modes and only the ramp is conditional — with motion suppressed it holds, then goes in one
  step. WCAG 2.2.2 satisfied rather than the information withdrawn.
- ⚠ **The FIRST report is swallowed.** A window opening at its saved size is not a resize, and without
  this the deck flashes a number in the corner of all twenty tool windows every time it restores a
  layout. ⚠ It also means a **single-pass render photographs the state indistinguishable from the
  feature being absent** — `DeckSnapshot -Ddeck.resize=<width>` lays out a second time at a different
  size. Nothing fades there either, because no `Pulse` frame fires synchronously, which is what makes
  it photographable.
- ⚠ **Reported from `layoutChildren`, and it must ignore a pass where nothing changed.** That method
  runs whenever any child asks for layout, so an unconditional call lights it up on every repaint of
  whatever the window contains.
- ⚠ **A child of the FRAME, not of `inner`.** `inner` is clipped to the notch polygon, so a readout in
  its bottom-right corner works until somebody turns rounded corners on and the clip eats the corner
  it sits in.
- ⚠ **Unmanaged, so it must `autosize()` itself** — an unmanaged node is never resized by its parent
  and a `Label` that has never been sized is zero wide. Same family as `SyncBanner`'s trap.
- ⚠ **`WindowFrame.dispose()` is called from `DeskManager.close`.** A `Pulse` subscription outlives
  the node that made it; `CycleGrid.dispose`/`CoreCage.dispose` were written, correct and called by
  nobody, and every open of the rig monitor leaked one.
- ⚠ **Driven off `root`, not the `Stage`.** A Stage's width includes OS chrome and is set before the
  scene lays out, so it reports a size the deck never has.
- ⚠ **Not amber.** §2.1 spends amber on cycles doing work and income; a pixel size is neither, and
  colouring it would imply the number meant something about the game. `text-hi` on `panel-hi` — a pair
  `ContrastTest` already measures in all eight palettes, so it inverts correctly on uOS Classic.

**COMS' DIRECT TAB WRAPS THE PLAYER'S REAL BLUESKY DMs (2026-08-06).** `client/bsky/BlueskyChat` +
`view/DirectView` — conversations, **groups**, and history, synced from the account connected in
Settings → Bluesky. The tab is absent unless an account is connected.

- ⚠ **THE THIRD ENTRY IN `docs/client/02` §2.9a's EXHAUSTIVE OUTBOUND LIST**, and the first that is
  somebody's real social account. `00` §7 survives because **the game sends nothing of its own**: no
  handle, DID, avatar, balance, standing, item, machine name or address. Sign-in carries the
  credential the player typed; every later call carries a bearer token and a convo id.
- ⚠ **NOTHING THAT COMES BACK IS EVER WRITTEN TO A SAVE.** `GameSave.messages` is the ENGINE's inbox
  and one of its entries carries `offerItemType` — an entitlement. Merging the two lists puts text a
  stranger typed somewhere trusted enough to grant an item, which is **I14** at its smallest scale.
  The Bluesky cache dies with the window, as a mail client's does. The tab strip is the seam.
- ⚠ **API SHAPES VERIFIED AGAINST THE PUBLISHED LEXICONS, not remembered** — and three of them are
  places a plausible implementation is **silently wrong**:
  ⚠ **`listConvos` takes `status` = `accepted` | `request`, and requests are a SEPARATE BUCKET.**
  Fetching only the accepted ones hides every first approach behind a setting nobody opened. That
  split *is* Bluesky's consent model — wrap it, never build a parallel allow-list, which would be
  this game keeping a social graph.
  ⚠ **A `messageView.sender` is ONLY a DID.** The name lives in the convo's `members`, so it has to
  be resolved by matching — otherwise every line is prefixed with `did:plc:…`.
  ⚠ **A `deletedMessageView` has NO `text` field at all.** Rendering it as an empty line is
  indistinguishable from a failed load; it says "(message deleted)".
  ⚠ `limit` is clamped to the lexicon's **1–100** — asking for more is an *error*, not a bigger page.
  ⚠ The API returns messages **newest first**; the transcript reverses them, because a conversation
  is read downwards.
- ⚠ **`atproto-proxy: did:web:api.bsky.chat#bsky_chat` on every chat call.** Without it the PDS
  answers "unknown method", which reads as the endpoint not existing and sends you hunting for the
  wrong hostname.
- ⚠ **`Bad token scope` is NOT a wrong password** — it is an app password created without the
  direct-messages box ticked. `describeSignInFailure` says so in as many words, because the two are
  indistinguishable to a player and one of them is a two-minute fix.
- ⚠ **Redirects are NEVER followed** — the `Authorization` header would be replayed to whatever host
  the redirect names. Same reasoning `HttpStockFeed` records for its API key.
- ⚠ **Nothing is logged but the endpoint and the status.** The response body *is* the conversation
  and the URL carries a convo id, and this client captures its own log and invites the player to send
  it in. Pinned by a source scan in `BlueskyChatTest`.
- ⚠ **Every call is on a VIRTUAL thread, handed back through `Platform.runLater`.** A round trip to
  somebody else's PDS is not a duration this client gets to bound, and doing it inline freezes the
  deck. Sign-in too — `blueskyPane()` runs on the FX thread while a window is opening.
- ⚠ **`DirectView.state()` MUST NOT ASK `signedIn()`, and this SHIPPED BROKEN.** Sign-in was started
  on a virtual thread and the pane was built in the next statement, where it asked `signedIn()` to
  decide what to render — **false every time**, not intermittently — so the DIRECT tab said *"No
  Bluesky account is connected"* permanently, for a connected handle with a correct DM-scoped app
  password. Reported from a real session. ⚠ **Only a NULL client means "no account"**: `blueskyPane`
  is the one place that can answer it, because it is the one that looked in the settings and the
  credential store, and it says so by returning null. Anything else deciding the same question from a
  different signal is how two answers come apart.
  ⚠ **The second bug was in the same ordering**: `blueskyPane` discarded `signIn`'s returned
  `Optional<String>`, so the *Bad token scope* diagnostic — the one that distinguishes a missing DM
  permission from a wrong password — **could never reach a screen**. `ensureSignedIn` is idempotent,
  runs on the view's own background thread, and returns a **sentence rather than a boolean**
  precisely so a caller cannot throw the reason away again.
  ⚠ **`credentials()` clears any existing session**, or a player who fixes a bad app password keeps a
  token minted from the old one and the fix appears not to work.
  ⚠ **Extracted as a pure `state()` seam** — `SecurityCenterView.latestOf`'s and
  `Anchoring.horizontal`'s reason: the rule lived inside a method that builds nodes, so the only way
  to check it was to run the client and look, which is how it was found *after* it shipped.
  `DirectViewTest` needs no toolkit and was verified against the broken condition first.
- ⚠ **No test signs in.** That would open a connection to a developer's real Bluesky account — the
  side effect `DiscordIpcTest` refuses by never calling `connect`. What is tested is the wire shape
  and the things it forced.
- **"Powered by Bluesky" sits at the top of the tab** — everything below it is somebody else's
  service and somebody else's data, and a tab inside a game window silently showing real
  conversations leaves a reasonable person unsure whose messages these are.
  ⚠ **The mark moved to `ui/widgets/SocialMark` rather than being copied.** It was a private enum in
  `view/Credits`, whose own comment promises that swapping in the official assets is a two-constant
  edit — the moment a second copy existed that promise was false, and **a drifted copy of somebody
  else's mark is a worse failure than a missing one because nobody would notice**. One definition,
  two callers.
  ⚠ **NOT the official logo, and nothing is fetched** — a path authored in this repo, in a client
  that bundles no third-party artwork. §9's icon-set ban is not in play: one quoted mark drawn as a
  path is not an icon vocabulary, and §9's radius rule governs *this* interface's geometry.
  ⚠ **NEUTRAL, never Bluesky's blue.** §2.1 spends amber on cycles doing work and rations alarm to
  loss; an attribution is neither, and a brand blue beside `gain`/`warn`/`alarm` — which all mean
  something — is the semantic colour system §2.1 bans arriving through the back door.
  ⚠ **A shape, so it colours with `-fx-fill`** and sits outside `ContrastTest`, which is correct
  rather than a gap (§4.4: the words carry it, the mark reinforces). ⚠ **One `accessibleText` on the
  ROW**, children cleared — a reader cannot see a butterfly and would otherwise announce an
  unlabelled graphic followed by the text.
⚠ **`Bad token scope` COMES BACK FROM THE CHAT CALL, NOT FROM `createSession` — and it was handled
in the wrong place.** `com.atproto.server.createSession` succeeds with **any** valid app password,
DM access or not; the scope is only checked when a `chat.bsky.*` method runs. So a password without
the box ticked **signs in perfectly** and then fails every conversation fetch. The friendly message
lived on the sign-in path where it could never fire, and the pane fell through to *"No conversations
on this account, or Bluesky could not be reached"* — one sentence covering both "you have no
messages" and "your credential is wrong", which is no help for either. `describeChatFailure` is on
the request path now and `lastError` carries it to the pane; `BlueskyChatTest.Diagnostics` pins that
a scope failure names the fix and that a 429 does **not** blame the credential.

⚠ **THE SUCCESS PATH LOGGED NOTHING, WHICH MADE THE WHOLE FEATURE UNDEBUGGABLE.** "Never log the
body" was applied so hard that a working sign-in followed by a refused chat call produced **zero
lines** — a player's CLIENT LOGS tab showed eleven entries and not one from `bsky`. The rule is
sharper now: **log the SHAPE of the traffic, never its contents.** Sign-in attempt (handle + PDS) and
outcome (DID) at INFO; every non-200 at WARNING with endpoint + status + XRPC **error code**;
conversation and message **counts** at INFO/FINE; `getLog` reports both entries seen and
conversations touched, because "12 entries, 0 changes" is a poll working and "0 entries" is a cursor
that is not advancing. ⚠ The poll itself logs at **FINE** — an INFO line every minute buries the
client log within an hour.

⚠ **`nothingSensitiveIsLogged` fired on the word `accessJwt` in a message that said the token was
ABSENT.** A false positive, and the message was **reworded** rather than the guard given an
exception — the same call this repo made renaming `DidDocument.ServiceEndpoint` for the blunt
"no `*Service`" rule. A guard with one carve-out is a guard somebody adds a second one to.

⚠ **`ensureSignedIn` sets `lastError` only on FAILURE, never clears it on success.** It returns early
once signed in, so clearing there would wipe the scope error — recorded by a later chat call — on
every subsequent poll, and the pane would go back to showing nothing.

**THE DIRECT TAB POLLS, THE MARK SPRINGS, AND MESSAGES CHIME (2026-08-06).**

- ⚠ **POLLING IS `chat.bsky.convo.getLog` — THAT IS WHAT THE ENDPOINT IS FOR.** It returns a cursor
  and only what *changed* since it. Re-running `listConvos` plus a `getMessages` per conversation
  every minute spends a large multiple of the player's own allowance to discover, almost always, that
  nothing happened — Bluesky publishes **5,000 points/hour** and warns that clients polling every few
  seconds consume it. **60 s default, floored at 15 s** (`DirectView.MIN_SYNC_SECONDS`), and the
  floor is not negotiable by the slider: somebody else's service, the player's own budget.
- ⚠ **IT COVERS WHAT THE PLAYER SENT, not just received.** `logCreateMessage` fires for every message
  in a conversation the account is in, whoever wrote it — a reply typed on a phone appears next poll.
- ⚠ **THE FIRST `getLog` IS HISTORY, NOT NEWS** — called once during the initial sync to establish the
  cursor. Without it the first poll reports the player's entire correspondence as new and **chimes
  once per message**. ⚠ **Only `logCreateMessage`/`logDeleteMessage` count**; reads, reactions, mutes
  and the twenty-odd membership events are real entries and none is a new message.
- ⚠ **Polls never overlap** (`syncing[0]`) — two in flight double the cost and can deliver out of
  order. ⚠ **`Pulse.every`, never `animate`**: a decorative subscription never fires under Reduce
  motion, so an `animate` poll means that player **never receives another message**.
- ⚠ **`fullHistory` pages backwards and PREPENDS.** Each page is newest-first and pages walk back in
  time, so appending would interleave the history wrongly. Bounded at `HISTORY_PAGES` (10) — "sync
  all" on a years-old account is otherwise an unbounded loop against somebody else's rate limit.

⚠ **`ui/widgets/SyncSpin` IS A SPRING, WHICH §5 AND §9's REJECTION LIST BOTH NAME AS BUILD-BLOCKING.**
Amended narrowly on explicit direction — `ui-design-language.md` **§5.2** — under four conditions:
**(1) no new animation machinery** (no `Interpolator`/`Timeline`/`KeyValue`/`AnimationTimer`; a
hand-authored **table of angles**, not a function, walked on `Pulse`, so `UiContractTest` is
untouched); **(2) one widget, one mark**, never a shared easing utility; **(3) it turns only while a
real sync is in flight** — a progress indicator, not decoration; **(4) still under Reduce motion**,
where the pane says "Syncing conversations…" in words instead. ⚠ **A TABLE, NOT A FUNCTION** is the
load-bearing half: a formula would be an easing function in the source for the next person to import,
at which point §5 has been *abandoned* rather than amended. ⚠ It **snaps home** when a sync ends
rather than finishing the table — motion after the thing it reports has stopped is the one lie a
progress indicator can tell.

**SOUND: ONE CHIME, `javax.sound.sampled`, NOT `javafx-media` (2026-08-06).** `client/sound/Sfx`,
Settings → Sound (0–100, default **60**, machine-wide).

- ⚠ **Media would have played the supplied MP3 directly and would also have added `libjfxmedia`
  natives to ALL FIVE platform uber jars** for a one-second chime. The file was converted once at
  authoring time — `afconvert -f WAVE -d LEI16@11025 -c 1` — and the **23 KB WAV** ships, a fifth of
  what one platform's media native costs. Same argument `presence/DiscordIpc` records.
- ⚠ **A LINEAR SLIDER IS NOT A LINEAR LOUDNESS.** `MASTER_GAIN` is in **decibels** over roughly
  −80…+6, so feeding it a percentage makes 50 mean "+50 dB" — clamped to the maximum, so every
  setting above a few percent sounds identical and full. It is `20·log10(fraction)`.
- ⚠ **The clip is opened ONCE and rewound before each play.** A `Clip` holds a mixer line and a
  machine has finitely many; one per notification leaks lines until sound silently stops. Not
  rewinding means a second chime within a second plays nothing.
- ⚠ **Zero is silent and returns immediately** — not "play at zero gain", which on some drivers is
  still an audible click.
- ⚠ **Every failure is silent, and catches `Error` as well as `Exception`** — a headless box fails at
  the native layer, and a notification path that threw would take the notification with it.
- ⚠ **The chime rides on the NOTIFICATION the player already asked for**, so a muted facility is
  silent too — one decision, not two that can disagree — and `Notifications.primed` is what stops the
  whole backlog chiming at startup after a few days away.

⚠ **A STATISTICAL BAND HAD TO WIDEN WITH THE CEILING.**
`GameEngineTest.offlineSelfMiningIsCappedNotProportional` compares a 30-day absence against a 5-hour
one. Capped income scales with hashrate, so dropping the test rig from 100 cycles to the ladder's 64
raised the standard error by **√(100/64) = 1.25×** — the old ±20% was ~3σ and became ~2.4σ, failing
on ordinary variance about one run in sixty. Widened **by that arithmetic**, not until it passed.

- ⚠ **`-Ddeck.commsTab=DIRECT`** selects a COMS sub-tab. Same gap `deck.securitySection` closed: the
  pane opens on its first tab, so DIRECT was unrenderable and a render of COMS reported the window as
  covered while only ever photographing INBOX.

**A STARTING RIG IS 24 CYCLES, AND COMPUTE HAS A LADDER: 24 → 32 → 48 → 64 (2026-08-06).**
`Balance.STARTING_CYCLES` fell from **100**; `rules/ComputeLadder` is the ladder;
`ui/RebootSequence` is the apt-shaped upgrade log that plays when a rung lands.

- ⚠ **THIS AMENDS INVARIANT I1**, on explicit direction. I1 read "compute is never purchasable with
  ethecoin"; the ladder's **first rung** (24→32, `Balance.COMPUTE_32_PRICE` = **1200 EC**) now is.
  ⚠ **The flywheel needs a LOOP** — mine → buy capacity → mine faster → buy *more* capacity — and
  closing it takes a second purchase that does not exist. 32→48 is not for sale at any price. Money
  moves a player up **once, ever**: a head start, not a compounding one.
- ⚠ **THE NARROWING IS MECHANICAL, NOT A PROMISE.** `ComputeLadderTest.onlyTheFirstRungIsForSale`
  and the amended `ShortcutsTest.nothingSellsCapacity` both fail the build on a second priced rung.
  The second of those is **the test that WAS protecting I1** — amended in place rather than deleted,
  because the safety argument is precisely that the exception is one item wide. ⚠ **I12 is
  untouched**: vault capacity has no exception and that test still holds all of it.
- ⚠ **THE COSTS WERE DELIBERATELY NOT RESCALED, and that is what makes the ladder worth climbing.**
  A Thorough Scan still costs **35**, which a starting rig cannot run at all; a T3 Firewall still
  holds 15, nearly two thirds of one. What a rung buys is **which operations are possible at all**,
  not bigger numbers — rescaling every cost to fit 24 would leave the player equally capable at every
  rung. `ComputeLadderTest.theTopTierIsBehindTheLadder` pins it, so a future "fix" argues with a test.
- ⚠ **CAPACITY IS DERIVED FROM THE ITEMS HELD; `rig.totalCycles` IS A CACHE** reconciled on the tick.
  This is `ChainState.networkHashrate`'s bug pre-empted — a stored copy of a derived value went stale
  and cost a real character 29% of their income forever, silently. It is also what stops a
  hand-edited `totalCycles` granting the whole ladder. ⚠ **Highest rung held, never a sum.**
- ⚠ **Climbed IN ORDER** — 48 refuses without 32 — or "money moves you up once" is false, since a
  player could leave the purchasable rung unbought and climb entirely on schematics.
- ⚠ **1200 EC is priced against BREACH LOOT, not mining.** ~125 hours at a starting rig's own rate
  (nobody will), or twenty-odd good hauls at `design/03` §3's 45–65 EC — which puts the first rung
  behind **the puzzle** rather than the clock, and is what keeps it from being a mining upgrade even
  though it is bought with mining's currency. ⚠ ~6× `PRICE_TOP_PURCHASABLE`, exempted from §2's
  bands **explicitly** rather than by widening them, which would let every other item creep up.
- ⚠ **The reboot does NOT own the state change.** The rules raise the ceiling on the tick, so the
  animation is safe to skip, safe under Reduce motion, and safe to miss entirely. An animation that
  owned it would be an accessibility setting that costs a purchase.
- ⚠ **`compute-32` is SOFTWARE, not FIRMWARE, and the distinction does real work.** `Offering`'s
  compact constructor **refuses firmware with no schematic named** — that guard is what keeps
  firmware money-unreachable, and marking this one firmware would have forced a schematic onto the
  rung meant to be bought. The shape says what it is: the first rung is a **product**, the two above
  it are things you **compile**. ⚠ Materials are **fill-ins**; Compiler mechanics are still **AS-1**.
- ⚠ **~31 TESTS BROKE AND THE RE-FIXTURE WAS THE BULK OF THE WORK.** Every fixture allocating 40–100
  cycles as a round number was **silently refused** against a 24-cycle rig, and the failures surfaced
  somewhere unrelated: a rate assertion, a task never created, a log line that never appeared, a
  port-scan report that read as a *merging* bug. `TestSaves.bare` (client) and `GameEngineTest.bare`
  (engine) now put a test rig at the **top of the ladder** — the same argument as their already
  removing the tutorial parasite. ⚠ They grant the **items**, never `totalCycles`, or the next
  reconcile stomps it. ⚠ Rate assertions are **derived** from the ceiling now: `design/03` §1
  publishes 0.4 EC per **cycle-hour**, and the old literal `40.0` was that times a 100-cycle rig.

**CREDENTIALS GO IN THE OS STORE, NEVER IN A FILE (2026-08-06).** `client/credentials/` —
`SecretStore` over macOS **Keychain**, Windows **Credential Manager** and freedesktop **Secret
Service**. Settings → Bluesky connects an account; `ClientProfile.Settings.blueskyHandle` holds the
**handle only**.

- ⚠ **THERE IS NO PLAINTEXT FALLBACK AND THERE MUST NEVER BE ONE.** A machine with no agent gets
  `SecretStores.none()` and the feature is **off**. A credential in `settings.json` is a credential
  in every backup, screen share and bug report, and the player has no way to know it happened.
  `NoCredentialsInSettingsTest` fails the build on a `Settings` field named like a secret — including
  `token`, the one most likely to arrive innocently as "infrastructure".
- ⚠ **THE SECRET GOES ON stdin, NEVER IN argv.** Process arguments are **world-readable** — `ps`
  shows them to every user on the machine — and macOS's own tool says so: *"Use of the -p or -w
  options is insecure. Specify -w as the last option to be prompted."* `ToolRunner.run` takes the
  command and the stdin payload as **two parameters with no single-string overload**, so there is
  nowhere to interpolate a password. `SecretStoreTest.NeverInArgv` reads the source and fails on a
  command list mentioning the secret — a run-time check cannot ask a process which argument was a
  password.
- ⚠ **THE ROUND-TRIP TESTS ARE OPT-IN AND SKIP BY DEFAULT (2026-08-06)** — `SecretStoreTest.Roundtrip`,
  `@EnabledIfSystemProperty("eyeandsickle.credentials.roundtrip")`:
  ```bash
  mvn -pl client test -Deyeandsickle.credentials.roundtrip=true
  ```
  Every other test in that file is inert (it reads source, or builds a command list and looks at it);
  these are the only ones with a **side effect on the developer's own machine**. They write a
  throwaway item to a real keychain — deleted in a `finally`, but a failure in between leaves a
  credential-shaped entry in somebody's personal store — and these tools **prompt** when they cannot
  proceed, which in a build reads as a hang (bounded at 10s by `ToolRunner`, so really a slow
  confusing failure). They are also platform-specific by construction, so a green run never meant
  what it looked like. ⚠ **The gate is on the CLASS, not each method**, so a Windows or Secret Service
  round trip is opt-in by being written there rather than by somebody remembering an annotation; the
  per-store `available()` assumptions stay underneath. ⚠ **Kept, never deleted** — running this exact
  code is what found both surprises below, and neither is visible from a command list.
- ⚠ **VERIFIED AGAINST A REAL KEYCHAIN, and it had two surprises.** `security add-generic-password`
  prompts **twice** (password, retype) so the secret is written twice — and sending it once fails the
  comparison while **still exiting zero**, so the status code would never have told us. Exit **44** is
  "no such item", which is what lets a lookup return empty instead of reporting a broken store.
  ⚠ `-U` is required or an existing item is refused and a changed app password silently keeps the old
  one. ⚠ `-A` is deliberately **not** used — the tool's own usage calls it "insecure, not recommended".
- ⚠ **Windows is `powershell.exe`, NOT `pwsh`** — WinRT `PasswordVault` loads in Windows PowerShell
  5.1 (on every Win10/11) and **not** in PowerShell 7 by default, so a machine with both breaks on the
  newer one. ⚠ `cmdkey` was rejected: `/pass:` puts the secret in argv. ⚠ The script arrives on stdin,
  which makes **quoting the injection surface** — single quotes only (a double-quoted PowerShell
  string interpolates, so `$(...)` in a password would execute) and `escape()` doubles them.
  ⚠ `RetrievePassword()` must be called before `.Password` is populated, or a lookup returns empty
  and looks exactly like "nothing stored". ⚠ **Unverified on real Windows/Linux** — both fail closed
  by construction, so the risk is reporting unavailable where it would have worked.
- ⚠ **THIS IS THE FIRST SUBPROCESS THE CLIENT HAS EVER SPAWNED.** `SystemReport` records the previous
  position — "starts no process and opens no host file" — and that austerity cost the ABOUT tab its
  CPU name. Amended narrowly: only this package spawns anything, the executable is a fixed name never
  composed from input, and nothing a player types reaches an argument.
- ⚠ **Nothing here is ever logged.** The client captures its own log at `ALL` and invites the player
  to send it in, and **the output of a lookup IS the secret**. The log line carries the executable and
  the exit code — never the arguments (which hold the account), never stdin, never stdout. Pinned by
  a source scan. ⚠ **stderr is DISCARDED, not merged** — merging puts "item not found" into the value
  a lookup returns, so a missing item comes back as a "secret" that is an error message.
- ⚠ **Bounded at 10s and destroyed on expiry.** These tools *prompt* when they cannot proceed — a
  locked keychain, a missing agent — and a prompt given a pipe may simply wait, which on the FX
  thread is a frozen client with no error.
- ⚠ **Only the trailing newline is stripped, never `strip()`** — an app password may legitimately
  begin or end with a space, and trimming one yields a credential that is wrong invisibly.

⚠ **SETTINGS' DETAIL PANE ELLIPSISES ITS PROSE INSTEAD OF WRAPPING IT — PRE-EXISTING, ALL 11 PAGES.**
`wrapped()` sets `setWrapText(true)` and the labels still render `"...however, this is where the..."`;
confirmed on the Discord page, which predates any of this. The fix is a width constraint in
`Views.settingsPage`/the detail pane rather than at a call site, and it wants its own render pass
across every category — noted here rather than folded into an unrelated change.

**NOTES — A MARKDOWN NOTEBOOK, `Shortcut+T`, ABOVE THE CALCULATOR (2026-08-06).** `view/NotesView`
over `engine/state/NoteState` + `rules/Notes`; explorer tree with nesting folders, a markdown editor
with live syntax highlighting, autosave. Fifteen windows.

- ⚠ **THE HIGHLIGHT IS A `TextFlow` LAID OVER A `TextArea`, and it aligns ONLY because everything is
  monospace.** JavaFX has no rich-text control — `TextArea` is one font, one colour, no third option
  — so the area's own glyphs are drawn **transparent** and coloured runs are painted over them,
  character for character. Same guarantee `CoreCage`/`AsciiCanvas` already lean on.
- ⚠ **A RUN MAY CHANGE COLOUR AND WEIGHT. IT MAY NEVER CHANGE SIZE.** A bigger heading in the editor
  shifts every glyph after it and the caret stops landing under the pointer — silently, and only on
  lines containing markup. Font family, size and padding are declared **once for both layers** in
  `theme.css`; changing one and not its twin breaks alignment for exactly the players who write
  markdown with markers in it.
- ⚠ **THE `TextArea` MUST NOT SCROLL ITSELF.** It owns its viewport and exposes no scroll offset, so
  an overlay inside a scrolling area drifts the moment anybody scrolls. It is grown to its full
  content height (`prefRowCount`, floored at `MIN_ROWS`) and the whole stack goes in **one**
  `ScrollPane` — one viewport, one offset, nothing to keep in step.
- ⚠ **THE CARET IS COLOURED EXPLICITLY.** `-fx-text-fill: transparent` takes Modena's caret with it,
  leaving the player typing into what looks like a dead panel. `-fx-caret-color` and
  `-fx-highlight-text-fill` are set separately for this reason.
- ⚠ **`MarkdownSpans` KEEPS THE MARKERS IN THE RUNS.** `**bold**` is one run of eight characters, not
  four with the asterisks dropped — anything that adds, drops or reorders a character slides the
  highlight off the text. `MarkdownSpansTest` round-trips every construct **and every PREFIX of a
  document**, because a half-typed `**` is the state the editor is in for as long as it takes to
  reach the closing marker. `stripped()` is the reading form and must never be called from the
  overlay.
- ⚠ **NOTHING IN THE NOTEBOOK IS READ BY ANY RULE**, and that is a standing constraint rather than a
  description of today. The moment a gate, price, threshold or outcome depends on note text, every
  note is a save-editable input to the rules. `NotesTest.notesAreInert` pins the field list so a new
  numeric or enum field forces the question.
- ⚠ **Per CHARACTER, in the save** — notes are what *this* character found out, and pooling them
  across characters spoils the thing the window is for. The honest consequence: deleting a character
  deletes their notes.
- ⚠ **DELETE IS RECURSIVE, unlike `Repac.delete`,** and the difference is what is being deleted:
  `Repac` refuses to walk a tree because its filesystem is *generated* from game state, where this
  one is stored. An orphaned note is invisible in the window, still in the save, still counting
  against `LIMIT`. The UI confirms first. ⚠ **`move` refuses a folder into its own descendant** —
  the one operation that detaches a subtree with no error message — and both walks are **bounded**,
  because a hand-edited save can already contain a cycle and an unbounded walk hangs the client on
  load before any screen is drawn.
- ⚠ **`writeNote` is NOT announced and returns OK on an unchanged body.** The editor calls it on a
  timer while somebody is typing; announcing would publish a bus event and light the disk lamp on
  every autosave. ⚠ Autosave is **`Pulse.every`** (data) — under `animate` it would never fire for a
  player with Reduce motion on, who would lose work for having used an accessibility setting — and
  it also writes **on detach**, or up to `AUTOSAVE_MS` of typing dies with the window.
- ⚠ **`Shortcut+T` is NOT a break in the positional scheme.** The rail's keys are a row read top to
  bottom — now `0 1 2 3 4 R F G A S D T X / ,` — not a mnemonic and not an index, so inserting T
  shifts nothing after it. ⚠ **The collision to watch is `Shortcut+Shift+T`**, the global theme
  cycler: different combinations today, one dropped Shift from not being.
- ⚠ **`PresenceState`'s exhaustive switch caught the new window at COMPILE TIME** — which is the
  entire reason it is a switch and not a map. Its line is `"Taking notes"`, never the note's **name**:
  a title is text the player typed, and the enum exists so what can be transmitted is the set of
  constants in it (`PresenceLeakTest`).
- ⚠ **The tree's disclosure arrows are ASCII `>` and `v`**, never an icon set or a block glyph — §9
  bans icon sets and `GlyphCoverageTest` has already rejected four block elements and `U+26A0`.
- ⚠ **The context menu anchors to the WINDOW, never the row** — the handler repaints the tree first,
  which detaches the node the pointer was over, and JavaFX throws on every right-click. `NetMapView`
  records the same failure.
- ⚠ **The side column's width goes on the COLUMN, not the inner tree** — same trap `CommsView`
  records, and the same crushed column it produces.

**COMS IS AN INBOX NOW, AND THE TOR MARKNET UNLOCKS THROUGH IT (2026-08-06).** `view/CommsView`
(list + reading pane) over `engine/state/MessageState` + `rules/Inbox`; `rules/BlackMarket` decides
when the darknet vendor makes contact; the notice carries the **TOR Module**, and holding it adds a
fourth MARKET tab, `view/TorMarknetView`. It replaced a prose stub — `docs/design/12` is still
`[PROPOSAL]`, so what exists is the delivery surface those systems will use plus the one message the
rules currently send.

- ⚠ **TWO SOURCES, ONE WINDOW, AND THEY ARE NOT THE SAME LIST.** **INBOX** is the game talking to the
  player: engine-authored, in the save, trusted. **DIRECT** is player-to-player, which is **Bluesky's**
  DM service, reached through the player's own account, and never touches a save. They share a window
  because that is where a player looks for "who said something to me", and nothing else. ⚠ Merging the
  types is how text somebody else wrote lands in a list whose entries can **grant an item** — **I14**
  at the smallest scale. The tab strip is the seam and is deliberately visible.
- ⚠ **`MessageState.offerItemType` IS A LICENCE TO RECEIVE SOMETHING FOR NOTHING.** Set only by
  `BlackMarket`, cleared on claim, and nothing originating outside the game may ever populate it.
  ⚠ `Inbox.claim` clears it **BEFORE** the download is created: a failure after loses an entitlement,
  a failure before **mints one per retry**.
- ⚠ **"Have I sent this" is answered by looking for the MESSAGE, never a flag.** Standing and heat
  both move and can cross their thresholds several times a session, so the condition cannot answer
  it — and a boolean would be a second place for the fact to live. Delete the message and it sends
  again, which is the honest behaviour.
- ⚠ **Trimming spares an unclaimed offer.** `Inbox.LIMIT` bounds *history*; an entitlement is not
  history, and dropping one to stay under a display cap silently deletes something the player was
  given. It refuses to trim rather than destroy one.
- ⚠ **The Marknet tab is ADDED/REMOVED, never disabled**, and it keys on **owning the module** rather
  than re-checking the thresholds. `design/02` §2.5: "going cold does not confiscate what you bought"
  — the introduction, once made, is made. A visible-but-locked tab would also advertise content to
  somebody with no route to it, which is the opposite of what a heat gate is for.
- ⚠ **The board lists REPUTATION-gated stock derived from the catalogue**, never a hand-kept list —
  otherwise a reputation-gated item added later is unreachable, a gate nobody can pass because
  nothing displays it. **I3 is untouched**: the vendor gates *access*, each item keeps its one gate.
- ⚠ **Not a second storefront with better prices.** That would make finding it an economic reward and
  turn a heat gate into a discount — `MarketDeals`' arbitrage failure in a new hat.
- ⚠ **The notification is free** — the tick writes `EventLog.notice(save, "comms", …)` and
  `Notifications` already drains the rig log. Instrumenting the view instead would mean a message
  that arrived with COMS closed was never announced.
- ⚠ **`markMessageRead` is NOT announced.** Routing it through `announce()` would publish a bus event
  and light the disk lamp every time the player clicked a row in a list.
- ⚠ **THE WIDTH GOES ON THE SCROLLER, NOT THE CONTENT.** min/pref/max on the inner `VBox` looks right
  and does nothing — an HBox distributes to its own children and a `ScrollPane`'s minimum is
  unrelated to what it contains. The list column was crushed to ~45px with the reading pane laid out
  over the top of it. The reader needs `setMinWidth(0)` or the row demands both columns before
  distributing anything. **Found by rendering.**
- ⚠ **`DeckSnapshot`'s factory map is SEPARATE from the client's, and its `default` photographs the
  RECON stub under the right window's title bar.** COMMS fell to it — the frame said COMPORT and the
  content was a different window entirely, which is exactly how it survived. **Add a case before
  rendering a window for the first time; do not trust the title bar.** `-Ddeck.noticed=1` sends the
  contact (a synchronous render never ticks) and `-Ddeck.torInstalled=1` puts the module on the rig.
- ⚠ **Bluesky DM facts, verified 2026-08-06 and not from memory:** `chat.bsky.convo.*` goes to the
  user's **PDS** with header **`atproto-proxy: did:web:api.bsky.chat#bsky_chat`** (service host
  `api.bsky.chat`); an app password must have **DM access explicitly granted** or every call returns
  *Bad token scope*; and **`getConvoAvailability` is Bluesky's own "may I message this person"**, so
  the consent layer exists upstream and must be wrapped rather than reimplemented. ⚠ Wiring it makes
  a **third** entry in `docs/client/00` §2.9a's exhaustive outbound list — CLAUDE.md's own note says
  "a third is a decision" — and it is the first that would carry **player-authored content**.

**DEFENCES MUST BE OWNED TO BE ARMED (2026-08-06), and until this they were not.** `docs/design/09`
§1 has carried a gate and a price for every defence since the design sessions, and
`LocalGameSession.armIntent` checked **compute and nothing else** — so a brand-new character could
arm a T3 firewall, a Detection Array and the Auto-Counter Daemon holding none of them. ⚠ **The
unlock ladder existed in the documents, in the catalogue and in the shop, and did not exist in the
game**, and **I2** and **I3** both rest on it. A published-but-unenforced rule is worse than a
missing one: every surface reads correctly and the defect is invisible to anyone who has not tried
to arm something they never bought.

- **The rule for where a tier sits**, as given: *low-level base tools and low-level upgrades are
  purchasable and cost more than a consumable; high-level and rare items need a schematic.* So:
  Firewall T1/T2/T3 = **EC 40/110/200**; Canary **8**; Tarpit **70**; Detection Array T1/T2 =
  **EC 50/140**; Detection Array **T3 = schematic**; Honeypot Stash = **reputation**; Auto-Counter
  Daemon = **schematic**.
- ⚠ **WHAT KEEPS I2 INTACT IS THAT EACH LADDER'S TOP RUNG IS OUT OF THE MARKET'S REACH.** Money
  reaches the highest rung *below* the ceiling, never the ceiling — the "top purchasable" shape
  `design/03` §2 already gives the firewall, whose whole ladder is EC-gated on `09` §2's argument
  that it is horizontal protection limited by **standing compute** rather than by price. ⚠ Pricing
  Detection Array T3 collapses that **silently**: shop renders, purchase works, ethecoin has bought
  a permanent capability. `CatalogueTest.theTopOfEveryDefenceLadderIsNotForSale`.
- ⚠ **The check is per TIER, never per KIND.** On the kind, buying the cheapest rung would be a key
  to the whole ladder *including its schematic-gated top* — the exact hole the split gate exists to
  avoid. `DefenceGateTest.aRungIsNotTheLadder`.
- ⚠ **I3 is untouched**: three tiers are three *items*, each behind exactly one gate.
- ⚠ **A new character is ISSUED a Firewall T1** (`Catalogue.STARTING_DEFENCE`), into the **VAULT**.
  Granted, **not exempted** — arming requires owning with no special case, because a rule with one
  exception acquires a second. Without it the FIREWALL panel opens as ten refusals and the
  reasonable conclusion is that the tool is broken. It is an ordinary EC item: sellable and
  re-buyable, as `design/02` §2.1 requires of that gate.
- ⚠ **The refusal names the GATE, not the absence** — "sold in the market" / "compiled from a
  schematic and never sold" / "takes standing, not money" are three different sentences, and the
  panel says the same thing in the row's ACTION cell **before** the click.
- ⚠ **`Catalogue.defenceOfferingId(kind, tier)` is the ONE mapping**, with three callers (the arming
  rule, the panel, the market). Written out three times, the day somebody adds a tier is the day the
  panel offers a row nothing sells. It **clamps** an absurd tier rather than rejecting it — a
  hand-edited save must not make an owned defence unrecognisable.
- ⚠ **TWO ESTABLISHED ASSERTIONS BROKE and both were the design colliding with an assumption that
  only held while these items did not exist.** `FirmwareTest.firmwareIsExpensive` asserted firmware
  was the dearest offering — **Firewall T3 is 200 EC against firmware's 180**, and both are pinned
  in the docs; rewritten to what was meant (dearer than every *consumable*, and gated behind a
  schematic no money buys, so its **total** cost is still highest). `UpgradeVersionTest
  .answersBeforeTheTransfer` hard-coded `NEW`, and the starting firewall made a foreign firewall
  package correctly read `UPGRADE`.
- ⚠ **Every fixture that ARMS must now STOCK.** `DeckSnapshot`, `RigStatusTest` and
  `PurchaseFlowTest` all silently became refusals — the render photographed a panel with nothing
  armed, which is indistinguishable from the switches not working. `RigStatusTest.stockAndArm` still
  goes through `arm`, so a refusal for any *other* reason still fails the test it should.
- ⚠ **`Balance.BLACK_MARKET_MIN_REPUTATION` / `BLACK_MARKET_MIN_HEAT` exist and NOTHING CONSUMES THEM
  YET.** They are for `design/09` §2a's heat-gated vendor — the **TOR Marknet** tab, unlocked by a
  module arriving in the COMS inbox. **The inbox does not exist** (`MoreViews.comms` is a prose
  stub), so that half is unbuilt. Reputation reads the **better** of the two faction standings, never
  their sum, or a fence-sitter with middling standing on both qualifies on neither. ⚠ Heat is a
  **FLOOR** — `design/02` §2.5's black-market broker wants you hunted, which inverts every other gate.

**DEFENSE became FIREWALL, and it is a TABLE OF TOGGLES now (2026-08-06).** `Views.firewall` — one
row per protective measure, with a `Switch`, an ACTION column (BLOCK · TAG · DELAY · BAIT · WATCH ·
STRIKE) and a HOLDS column, over a summary line reading `N measures armed · M cycles held`. It was a
column of arm-only buttons and there was **no way to turn anything off at all**: `GameSession.disarm`
/ `GameEngine.disarm` are new.

- ⚠ **The label is deliberately NARROWER than the contents** — it also arms canaries, tarpits,
  honeypots and the counter-daemon. "Firewall" is the word a player already owns for "the thing that
  stops things getting in", and a section nobody can name is a section nobody opens.
- ⚠ **DISARMING RELEASES, IT DOES NOT RECOVER.** `ComputeRules.release`, the call that unequips a
  tool — **not** `beginRecovery`. An armed defence *holds* a reservation rather than doing work, so
  the Thermal Budget curve has nothing to price. A disarm that cost minutes of reduced capacity would
  make never arming anything the correct play, which is the opposite of what **I9** protects.
  Negative-tested (`GameEngineTest.Defences`, verified against a neutered release).
- ⚠ **`DefenseState.allocationId` is stored rather than the allocation being found by LABEL.**
  `ComputeRules.reserve` sets `label = kind`, so a search would *usually* work — and a label has no
  uniqueness rule, so "usually" is one duplicate away from releasing somebody else's cycles with the
  rig simply having compute back that it never gave up.
- ⚠ **A SYNCED SWITCH WRITES WHAT IT DISPLAYS unless guarded.** `setSelected` fires the listener, so
  painting the effective state arms everything already armed. Same trap the rounded-corners setting
  records; `syncing[0]` is the guard. The sync also runs **after** every toggle, so a refusal puts the
  knob back — a row reading armed on an undefended rig is the worst outcome this panel has.
- ⚠ **ALL THREE TIERS ARE LISTED, and the missing middle was a real defect.** It offered T1 and T3
  only. The engine arms `firewall` and `detection-array` at **tier 2** as well (the render harness
  does exactly that), so a rig holding one showed both firewall rows off *and* disabled while the
  summary above them said two measures were armed. Survivable for a list of buttons; not for a table
  whose subject is what is currently armed.
- ⚠ **The HOLDS figures are read from `Balance`, never typed.** They were typed while this was a list
  of buttons and happened to be right; as a column headed HOLDS they are a measurement, and a
  measurement the view keeps its own copy of is one re-tune from quoting a price the rig does not
  charge.
- ⚠ **Only one defence of a KIND may be armed**, so a tiered pair is mutually exclusive. The sibling
  row is **disabled with the reason in its tooltip** rather than refused after the click — a switch
  that springs back with an error is a control the player learns by failing.
- ⚠ **Armed vs off is a BRIGHTNESS STEP, never a colour** (`-es-text-hi` / `-es-dim-1`, both measured
  by `ContrastTest`). §2.1 reserves amber for cycles doing work and rations alarm to loss; "switched
  on" is neither, and green/grey here would be the semantic colour system §2.1 bans arriving one
  table at a time. The knob's **position** stays the primary cue (§4.4). ⚠ `-es-dim-3` is not
  available however faint it looks right — that is the greeble token, exempt from the 3:1 floor.
- ⚠ **A TRIPPED canary shows in the ACTION cell, in `-es-warn` and not `-es-alarm`.** This panel's
  whole alarm ration is already spent on HOME's verdict and trefoil; a defence that fired is
  `design/12`'s evidence path, and evidence is not a loss. Two-class selector, or `.es-fw-on` wins.
- ⚠ **`onChange`, never `Pulse`** — nothing here is derived from wall time, so a one-second repaint
  would be work with no subject and would tear down a `Switch` under the pointer.

**The SECURITY CENTER window opens at 660×550, and `WindowSpec`'s numbers are NOMINAL (2026-08-06).**
Asked for as 655×550. ⚠ **No window opens at the size beside its name**: `DeckShell` scales by
`UiTokens.WINDOW_OPEN_SCALE` (**0.72**, an unnamed literal at three call sites until now) and
`DeskManager` snaps to `UiTokens.SNAP_GRID` (**22**), so the on-screen size is
`round(nominal × 0.72 / 22) × 22`. The row is written **backwards** from the target: `910 × 0.72 =
655.2 → 660`, `764 × 0.72 = 550.08 → 550`. ⚠ **655 is not a multiple of 22 and so is not reachable
at all with snapping on**; with free-drag it opens at 655×550 exactly.
`WindowCatalogueTest.theSecurityCentreOpensAtItsIntendedSize` pins the **effective** figures — an
assertion on `defaultWidth()` would restate the source line and pass just as happily if either
constant moved, which is the change that would silently resize the window.

⚠ **`VBox.setVgrow` ON A CHILD OF AN `HBox` IS IGNORED, SILENTLY.** `SecurityCenterView` had exactly
that — `setVgrow(body, ALWAYS)` where `body` is a child of the `split` HBox — so the constraint read
as obviously correct and did nothing, and the panel stopped short of the window's bottom edge leaving
a band of bare ground that reads as the section having ended early. The constraint belongs on
`split` inside the page's VBox. Invisible until the window shrank to 550 and the band became a third
of it. ⚠ **The fix is NOT `scrollable(content, true)`** — that was tried: `setFitToHeight` forces the
content to the viewport height, a VBox handed less than its children want **squeezes** them, and a
squeezed `wrapText` Label **ellipsises rather than scrolling** (both paragraphs rendered as `...`);
adding a Vgrow spacer to absorb the slack then pushed the legal note past the bottom of a viewport
that, with fitToHeight on, will not scroll to it.

⚠ **`-Ddeck.securitySection=FIREWALL` on `DeckSnapshot`** selects a section by its rail label —
exactly the `-Ddeck.settingsPage=` problem one window along. The panel opens on HOME and there is no
other way in, so AUDIT, FIREWALL and SCHEDULE were **all unrenderable** while the harness reported
the SECURITY window as covered. It also prints every open window's measured size now, because
"measure node bounds before hunting a gap in the layout" has ended more than one search here in one
line.

⚠ **The audit's scan buttons read `Quick (5c)` · `Full (15c)` · `Deep (35c)` (2026-08-06)** — the long
form (`Thorough · 35 cycles · 6m`) wrapped the strip onto two rows at 655px. The duration moved into
the tooltip, which is the only fact the short form drops. ⚠ **"Deep" is a DELIBERATE MISMATCH with the
flag**, which is still `scan --thorough` — and so are `scan(8)`, `commands_en.properties`,
`CommandSpec`, `design/04` §3.2 and `education/02`. Made on explicit direction; it is a small tax on
pillar **C6**, which sells skill that transfers to a real terminal, and it is why the tooltip prints
the real command. Renaming the flag is the other resolution and is much wider.

**The SECURITY CENTER is a consumer security suite's LAYOUT in this deck's language (2026-08-04).**
`view/SecurityCenterView` — a section rail (HOME · AUDIT · DEFENSE · SCHEDULE), a headline verdict,
one primary action and a card per subsystem. `rules/ScanSchedule` + `state/ScanScheduleState` add
audits on a timer.

- ⚠ **NONE of the reference's styling is reproduced, and that is not a shortfall.** §9 makes drop
  shadows, blur and glassmorphism **build-blocking** (`UiContractTest` fails on `dropshadow(`) and
  rounded corners are gated on `.es-rounded`; §2.1 bans a semantic colour system. So the gradients,
  the glowing disc and the blurred colour field are unavailable — the hierarchy is carried by **type
  size and position** instead, which §4.4 wants anyway because it survives greyscale and reaches a
  screen reader. A glow does neither. What was borrowed is the **structure**, which is the good part.
- ⚠ **The verdict is the ONE place `-es-alarm` is spent here.** §2.1 rations it to loss and hostile
  state at twice a screen; a finding is exactly that, and everything else staying neutral is what
  makes the change mean something. ⚠ **"Unaudited" is NOT alarm** — nothing has gone wrong, nobody
  has looked, and colouring it as a threat would cry wolf on every new character.
- ⚠ **The verdict is derived from the LAST SCAN, never from live state.** A security product can only
  report what it found when it last looked, and "nothing found" and "nobody has looked" are different
  sentences. The tier is named with it, because a clean Quick and a clean Thorough are different
  claims about the same rig.
- ⚠ **AT MOST ONE catch-up scan per absence, however long.** Six-hourly across four days is sixteen
  missed scans; running them all spends a day's compute on the first tick back and leaves the rig
  unusable for as long as they take. Same shape as `OFFLINE_MINING_HOURS` — offline yield is capped
  and never proportional to absence — and it means a schedule cannot be farmed by quitting.
- ⚠ **A scheduled scan that cannot be paid for is SKIPPED, not queued.** Queueing lands it at an
  unpredictable later moment, possibly mid-breach, taking cycles the player was counting on.
- ⚠ **`lastRunAt` is stamped to NOW, not advanced by one interval.** Advancing leaves several
  intervals still in the past after a long absence, so the next few ticks each fire another scan —
  the catch-up storm, arriving one tick later than expected.
- ⚠ **Turning the schedule ON starts the clock rather than firing immediately** — a scan the instant
  a switch is flicked reads as the switch having done something violent, and takes cycles the player
  was about to use.
- ⚠ **The interval slider applies on RELEASE.** A slider fires continuously while dragged, and
  writing the schedule per frame would persist the save dozens of times and light the disk lamp like
  a fault.
- ⚠ **The headline is a `FlowPane`, not an HBox with a spacer.** The verdict is 30px type beside a
  168px button, and in a tiled window there is no room for both — an HBox squeezes the headline and
  JavaFX ellipsises it, so "Your rig is Compromised" rendered as **"Your ..."**. `USE_PREF_SIZE` on
  both children, and `setMinWidth(USE_PREF_SIZE)` on the verdict so it can never be truncated: a
  player reads that word by its shape, and "Compromi..." has the wrong one. **Found by rendering the
  DECK, not the panel — the panel alone is never narrow.**
- **A big state mark fills the space beside the verdict** — `ui/widgets/SecurityMark`: an animated
  shield when clear, a warning triangle when there is something to attend to, a quarantine trefoil
  when an audit named something.
  - ⚠ **ALL THREE ARE DRAWN.** `GlyphCoverageTest` has already rejected `U+26A0` in this panel and
    shield/biohazard are certainly absent from both bundled faces, so these are `Polygon`s, `Arc`s
    and `Circle`s — the same decision the flash overlay's warning mark and the carousel's dots record.
  - ⚠ **THE VERDICT IS ABOUT THE AUDIT, and folding anything else in broke the panel.** "Nothing
    armed" briefly also forced CHECK — reasonable-sounding, and wrong in a way that made the whole
    tool look broken: on a rig with no defences, running a clean audit left the mark on the same
    warning triangle it already had, so **the primary action appeared to do nothing**. A player
    cannot tell "your audit changed nothing" from "the button is broken", and they assume the
    button. The verdict now answers exactly what an audit answers — *is something on this rig now* —
    and the defence gap is a statement about the **future**, said in the reason line and on the
    DEFENSE card. ⚠ `markStateFor` is pure and package-private **so it can be tested without a
    toolkit**; the bug shipped because the rule lived inside a repaint that needed a live scene to
    reach. `SecurityVerdictTest`, verified against the broken rule first.
  - ⚠ **THREE states, and the middle one is the point.** A clean audit is a statement about a
    *moment*, so a week-old "clear" is **unknown**, not clear (`ScanSchedule.STALE_AFTER`, 24h); and
    a rig with nothing armed is undefended rather than compromised. Neither gets the alarm a real
    finding gets — collapsing them into it would cry wolf until the player stopped reading. The
    caption names *which* condition is true, because the two have different fixes.
  - ⚠ **Alarm is spent exactly twice** (§2.1's ration): the verdict and the trefoil. CHECK is
    `-es-warn`, and spending alarm there would leave nothing louder for an actual finding.
  - ⚠ **Motion is stepped and decorative**, so Reduce motion holds one frame. The test for whether
    that is safe: **if it stopped forever, would the player still know what it says?** The shield
    carries a tick and the trefoil is a trefoil at rest — the sweep and the turn add nothing to the
    reading, which is what makes them suppressible.
  - ⚠ **The mark is built ONCE and replaced only when the STATE changes.** `buildHome` runs on the
    one-second Pulse, so constructing a fresh one per repaint reset its step counter every second —
    the sweep travelled a quarter of the way down and jumped back, forever — and leaked a Pulse
    subscription each time.
  - ⚠ **THE TREFOIL'S ARCS FACE OUTWARD, and backwards renders a PROPELLER.** A biohazard's arms are
    the parts of three overlapping rings pointing *away* from the centre; arcs centred on the inward
    direction draw three blades around a hub. Found by rendering — the first version used `+ 180`.
  - ⚠ **ONLY THE VERDICT PAIRS WITH THE MARK.** The action and its note sit in their own row below,
    which is what lets the mark come up and left instead of stranding itself under the button beside
    a column of empty space.
  - ⚠ **`setMaxWidth` DOES NOT CONSTRAIN A WRAPPED LABEL'S PREFERRED WIDTH**, and that is why the
    mark wrapped in a window with ample room. A `FlowPane` places children at their **preferred**
    size, and a `wrapText` Label prefers its whole string on one line however low its maximum is set
    — so the column reported ~900px, the pair did not fit, and the mark dropped to the next row while
    the panel was plainly wide enough. **`setPrefWidth` is the fix**; `setMaxWidth` alone looks like
    it should work and silently does not. Same family as the `Vgrow`-without-`setMaxHeight` trap.
- ⚠ **No `⚠` glyph in UI strings, and `GlyphCoverageTest` scans SOURCE** — a placeholder literal that
  gets overwritten at runtime still fails the build.

**The Security Center's section marks: detective · castle · alarm clock (2026-08-05).**
`ui/widgets/SectionMark`, top-right of AUDIT, DEFENSE and SCHEDULE.

- ⚠ **DRAWN, never a glyph.** `GlyphCoverageTest` has already rejected `U+26A0` in this very panel and
  §9 bans icon sets, so these are `Polygon`s, `Circle`s, `Ellipse`s and `Line`s — the decision
  `SecurityMark`, the flash overlay's warning mark and the carousel's dots all record.
- ⚠ **THE CONTENT IS INSET BY THE MARK'S COLUMN.** A `StackPane` layers its children and reserves
  nothing for the one on top, so the first version put the castle across the DEFENSE paragraph and
  the detective across the AUDIT tab strip. Insetting the content is what turns an overlay into a
  column. ⚠ It costs that width down the **whole** panel — the honest price of not editing
  `AuditView` and `Views.defense`, which are complete panels used elsewhere.
- ⚠ **THE GLARE MOVES, THE GLASS DOES NOT.** A bar of light clipped to the lens, sweeping one way and
  starting again — a reflection that retraces its path reads as the lens rocking rather than as a
  light going past, and a glare present on every frame is a highlight painted on. The first version
  moved the whole magnifier, which read as waving the prop about.
- ⚠ **The fedora's PINCH and the coat's LAPEL are what name the figure.** A head-and-shoulders
  outline beside a circle is a person holding a lens; three shapes make it a detective, with no face
  — the reference is faceless too, and a silhouette that grows eyes at this size becomes a cartoon.
  ⚠ The brim is a flattened **ellipse** and the crown a tapered polygon: two rectangles read as a top
  hat.
- ⚠ **Placement uses `layoutX/Y`, animation owns `translate`.** `Pulse.animate` **invokes once
  immediately** — a trap for an action that moves something rather than paints it — so the first tick
  overwrote the offset that put the glass in the hand and it rendered outside the widget entirely.
  **Found by rendering.**
- ⚠ Neutral ramp only: `SecurityMark` already spends this panel's whole `alarm` budget on the verdict,
  and the glare is the one part allowed near-white, because that is what a specular highlight is.

**THE RAIL WAS REORDERED AND IDENTITY REMOVED (2026-08-05).** `WindowSpec`'s declaration order IS
the rail order (`DeckShell` walks `values()`), so the catalogue is now: rig monitor · security ·
terminal · files · vaultstore · ledger · network · market · assembl · **COMPort** · log · calc ·
manual · settings. Fourteen windows.

- ⚠ **`COMMS` KEPT ITS ID and changed only its label** to COMPort. An id keys saved desk layouts and
  accelerator bindings; renaming it to follow a display name moves three things to change what one
  screen says. Same rule the liquid themes' ids record.
- ⚠ **IDENTITY DID NOT SIMPLY GO AWAY — it became the OPERATOR panel.** `Views.operatorProfile`
  slides out of the top strip's operator cell (a second `SyncBanner`; the chain-sync one is not
  shared, or the two would evict each other). The operator's name and face were already sitting on
  the strip doing nothing when clicked, and "who am I" in the rail put identity on the same footing
  as a tool. ⚠ Everything the window carried — handle, mode, heat, balance — is on the panel, **plus**
  the identifier and the three standings it never showed. Verified before deleting.
- ⚠ **The identifier line follows the MODE**: the local UUID in solo, the DID once federated, and the
  label says which. A solo character has **no DID** structurally — it has no route to a server, which
  is half of what keeps **I14** true — so a panel that always said "did" would claim an identity that
  does not exist. `GameSession.identityCard()`; `RemoteGameSession` returns **zero** standings rather
  than invented ones, because those are the server's to report and the snapshot does not carry them.
- ⚠ **Three reputations, never merged** — trader, faction (eye/sickle separately), and validator,
  which is the server's and is deliberately absent. A Sickle hero can be a thief.
- ⚠ **The panel is REBUILT on every open**, not kept: it reports heat, balance and standing, all of
  which move while the deck runs.
- ⚠ **`toggleOperatorPanel` is a TOGGLE.** The cell stays put while the panel is open, so a second
  click has to close it — re-showing would make the obvious way to dismiss it the one thing that does
  not. ⚠ The avatar is **unmanaged when absent**, or a character with no picture opens the panel on a
  96px square of nothing that reads as a failed load.
- ⚠ **`-Ddeck.operator=1`** on the render harness slides it out; it opens on a click and a synchronous
  render never delivers one.
- ⚠ **`Anchoring` RIGHT-ALIGNED EVERY OVERLAY, and the operator cell is the FIRST cell (2026-08-05).**
  "Right-align to the cell, clamp at zero" is correct for a readout near the right-hand *end* of the
  strip, which every drop-down was until this one. Right-aligning a 420px panel to a cell whose right
  edge is at 290 asks for **−130**; the clamp made it **0**, so the panel landed flush with the window
  edge, **on top of the rail**, lined up with nothing — reported as "looks like it's off screen a bit".
  ⚠ **The alignment is chosen now, not assumed**: right-aligned when there is room to the left of the
  cell, **left-aligned to the cell** when there is not. ⚠ And `place`/`watch` take a **`within`** node —
  **the desk, never the deck root**, because the rail is part of the root and is exactly what an
  overlay must not be clamped on top of. Both drawers pass `desk.root()`; the sync banner's failure was
  **latent rather than absent** (a narrow deck puts its left edge over the rail too).
  ⚠ **`min` BEFORE `max`**: an overlay wider than the desk cannot satisfy both bounds, and this order
  spills it **right** (readable from its first character) rather than left under the rail.
  ⚠ **The rule is extracted as pure package-private `Anchoring.horizontal`** — `SecurityCenterView
  .latestOf`'s seam, for its reason: it shipped wrong *because* it lived inside a method needing live
  scene bounds, so the only check was to render and look. `AnchoringTest` needs no toolkit and was
  verified against the clamp-only version.
  ⚠ **The field must be watched in its own right** — the rail collapses below `NARROW_WIDTH`, so the
  desk's left edge moves while the root's bounds do not change at all.

**THE WINDOW CATALOGUE WAS RESHAPED (2026-08-04).** `recon`, `breach` and `botnet` → **NETWORK**
(`view/NetworkView`, in that operational order: find, study, get in, what you left running);
`mining` → **LEDGER**; `audit` + `defense` → the new **SECURITY CENTER**. New: **ASSEMBL COMPILER**
and **SECURITY CENTER**. Twenty windows became fifteen.

- ⚠ **AUDIT and DEFENSE went into the rig monitor and came straight back out**, into their own tool.
  The monitor *asks* whether something is wrong; those two are what you *do* about it, and burying
  the answer four tabs into a window titled something else made it harder to reach than the question.
  ⚠ Both views moved twice in one afternoon and lost nothing, which is only true because neither
  ever held its own state — a view that remembered anything would have lost it on the first move.
- ⚠ **`ShortcutsTest` asserted `values().length >= 16`** — an arbitrary FLOOR on the window count,
  which fails on every consolidation and passes for every reason except the one it meant. It also
  duplicated `WindowCatalogueTest`, which pins the exact set by id. It now asserts what §6.3 actually
  requires: every per-window accelerator is distinct and none collides with a global.
- ⚠ **The SWITCHER window is gone (2026-08-04), and the check before deleting it was the point.** It
  existed as "the way back to a window you lost", so the question was whether it was the *only* way
  back to a **minimised** one — which would have stranded them. It is not: the rail carries a chip
  per catalogue window, and clicking one calls `DeskManager.show`, which un-minimises (its own
  comment records that being a flag rather than an early return, for exactly this reason). Verified,
  then removed. ⚠ First run now opens the **rig monitor alone**, which is the better answer anyway:
  a first run should show the machine rather than a list of things to open.
- **LEDGER's tabs are LEDGER then MINING.** It was the other way round, on cause-before-effect
  reasoning — but the window is named for the ledger, and a tool whose first tab is not the thing on
  its title bar makes a player wonder whether they opened the right one.

- ⚠ **FOLDING THE BREACH IN CONTRADICTS `docs/client/05` §44, knowingly.** That section argues a
  breach must span windows the way an operator's desk does, because the puzzle's anti-bot property
  (**I10**) is that a human cross-references material a fixed heuristic cannot — *"cross-referencing
  two documents is a simultaneity problem; a tabbed shell makes it a memory problem instead."*
  Nothing breaks today because the minigame is a `[PROPOSAL]` and unbuilt, so the cost is real and
  **currently unpaid**. ⚠ **UI-8**: when the puzzle is built, the breach probably comes back out.
- ⚠ **`RigTab.isTable()` WAS AN EXCEPTION LIST AND IT BROKE.** It read `!= OVERVIEW && != ABOUT` —
  so briefly adding AUDIT and DEFENSE silently made both "table tabs" and they would have rendered
  the process listing under their own panels. Its own comment already recorded
  this failure once, from ABOUT. It asks **`isPanel()`** now, the positive question, so a new panel
  tab is correct by declaring what it is. ⚠ The `columns()` switch caught the same addition **at
  compile time** — an exhaustive switch over an enum is the one place a new constant cannot be
  forgotten, which is why it is worth keeping one.
- ⚠ **Accelerators are POSITIONAL now (reassigned 2026-08-05)** — the rail reads
  `0 1 2 3 4 R F G A S D X / ,` top to bottom, and `WindowSpec`'s declaration order IS the rail
  order. The binding a player learns is **where a tool sits**, not what it is called.
  ⚠ **That traded away every mnemonic**: market was **B** ("the one accelerator a player will reach
  for without being told"), files was **H** for Home, calc was **C**. ⚠ **`Shortcut+F` now opens the
  network map and §6.3 reserves it for per-window find** — nothing collides today because no window
  binds find, so no test fails; the day a find bar is added, one of the two has to move.
  ⚠ Every binding is a plain `Shortcut+key`; five needed Shift before and now do not.
  `WindowCatalogueTest` fails the build on a duplicate and `ShortcutsTest` on a clash with a global.
- ⚠ **The views were REPARENTED, not rewritten.** `AuditView`, `Views.defense`, `Views.mining`,
  `ReconView` and `BreachView` are unchanged — rebuilding any of them would have been another place
  for the rig's own diagnosis to drift from itself.
- ⚠ **The `.app` bundles in `/Applications` stay**, by decision: they are where an eventual upgrade
  path hangs off, and a tool being a tab in the deck is a fact about the deck rather than about the
  filesystem.
- **ASSEMBL COMPILER — a schematic is a BLUEPRINT, not a purchase gate.** ⚠ **I3 survives**: the item
  still sits behind exactly one gate, the schematic; what changed is what holding one *lets you do* —
  it was permission to buy and it is now the ability to build. ⚠ **I2 survives more comfortably than
  before**: there is now **no path at all** from ethecoin to a schematic-gated item, where previously
  there was a priced one behind a check. ⚠ **The mechanics are deliberately NOT designed** (AS-1) and
  the window says so — a tool that invented a cost would put a rule in the code the design has not
  made. ⚠ The storefront drops schematic-gated items entirely but **keeps the other gates listed**:
  `design/02`'s taxonomy exists so a refusal is legible, and deleting those turns a legible gate into
  a missing item.

**The right-hand column is one TRADE CARD PER HOLDING, and it no longer follows the selection
(2026-08-05).** `AnonShareView.paintQuote` builds a card per `positions()` entry — name, symbol,
shares held, price, and its own stepper + Buy + Sell. The POSITIONS table went back to being a table.

- ⚠ **It was a single quote card aimed at WHATEVER WAS LAST CLICKED**, anywhere, including a LISTINGS
  row glanced at several actions ago. So the one Buy button on the panel pointed at something the
  player may not have meant, and topping up a holding meant finding it again first. It is derived
  from `positions()` and nothing else now.
- ⚠ **Opening a position in something NOT held still goes through the detail overlay** and the
  right-click `Buy 1`. **Checked before removing the shared ticket** — without it the panel would
  have had no route to buy anything at all.
- ⚠ **NO CONTROLS IN THE POSITIONS TABLE.** Buy/Sell in both places puts the same two actions twice
  on one screen, which is worse than either placement: the player has to work out whether they are
  the same thing.
- ⚠ **A STEPPER, NEVER A TEXT FIELD** — the cards are rebuilt by `repaint[0]`, which runs on every
  `session.onChange` as well as the price cadence, so a `TextField` is torn down mid-keystroke
  (**UI-7**, `ReconView`). The quantity lives in a map **outside** the rebuild, keyed by symbol, or
  it resets to one on every price refresh.
- ⚠ **Sell sells the SAME quantity the stepper shows**, capped at what is held, and says so on the
  button (`Sell 3`). One number driving both sides. A Sell that quietly disposed of the whole
  position while the stepper read 3 is the worst surprise this panel could spring.
- ⚠ **Reset on SUCCESS only** — a count left under the card after it went through is a loaded gun the
  next click fires; left after a **refusal** the player still wants that many and would have to dial
  it back up to learn why they cannot have them.
- ⚠ **The actions are TWO ROWS.** One row of stepper + Buy + Sell overran the narrow side column and
  JavaFX clipped Sell to `Sel` — a control whose whole meaning is its word. **Found by rendering**,
  and it fits at a wide window, so a single-width check would have missed it.

**Selecting a share opens a DETAIL OVERLAY; the chart stays the account's (2026-08-05).** Clicking a
row in LISTINGS or in the search results opens a modal card over the whole panel — name, symbol,
sector, price, yield, what you hold, and a Buy ticket.

- ⚠ **It does NOT repoint the chart.** The chart is the account's value over time; aiming it at
  whatever the player last clicked answers a question nobody asked and loses the one thing the panel
  is for. A per-share chart exists — it is the watchlist's — and it is reached deliberately.
- ⚠ **Clicking the SCRIM closes; clicking the card does not.** Without the target check the overlay
  dismisses itself the moment anybody reaches for the Buy button inside it.
- ⚠ **`.es-shmark-listing` is on THREE different row kinds** — positions, search hits and listings —
  so a `lookup` for one finds whichever the scene graph reaches first. The render harness clicked a
  POSITIONS row believing it was a listing and reported the overlay as broken. Listing rows carry
  `.es-anon-listing` as well.
- **Right-click a share** → Details · Buy 1 · Add to watchlist · New watchlist… ⚠ Anchored to the
  **window**, never the row: this panel repaints on a clock and a menu shown against a node a repaint
  has detached throws on the FX thread — `NetMapView` records the same failure. ⚠ **New watchlist…**
  is offered even when none exist, or a player with none sees a dead submenu and no way to fix it.
  ⚠ After creating one it **re-reads the snapshot** for the new id — the one the menu was built from
  predates the list, so reaching for it files the symbol nowhere, silently.

**WATCHING drills in: index → one list, with a chart and a symbol column (2026-08-05).**

- ⚠ **A WATCHED symbol is now sampled exactly like a held one** (`Brokerage.sample` walks
  `tracked()`), because a watchlist with no chart behind it is a list of names. It is deliberately
  the **same set** that gets the fast refresh cadence — wiring the two to different sets would give a
  watched symbol a chart made of one point a day. ⚠ The **portfolio total stays over holdings only**:
  folding a watched symbol in would show a player money they do not have.
- ⚠ **The chart is built ONCE and re-parented**, never rebuilt per repaint — a Canvas rebuilt on the
  clock loses its width, its hover state and its layout listener every second. Same defect the
  security mark's step counter had.
- ⚠ **Two charts, two hover indices.** Sharing one moves the marker on the panel nobody is looking at.

**The pointer readout FOLLOWS THE CURSOR and is not a cell in a row (2026-08-05).** It sat beside the
portfolio total, where it cost the header a variable amount of width for as long as it showed — so
the total, the change and the countdown all ellipsised together (`1705....`, `-455.39 EC on ...`,
`refreshin...`). Same rule as the balance delta in the top strip: **nothing transient may occupy
space in a row of readouts.** The countdown moved down beside the range buttons.

- ⚠ **A plain `Pane`, not a `StackPane`.** A Pane does not resize an unmanaged child and a `Canvas`
  is not resizable at all, so both keep the sizes they were given; a StackPane stretches both and the
  readout becomes a full-width band. ⚠ The unmanaged Label must `applyCss()` then `autosize()` — one
  that has never been sized is zero wide and paints nothing. ⚠ Clamped on both axes.
- ⚠ **A SYNTHETIC `MouseEvent`'S x/y ARE SCENE COORDINATES when the source is null**, and
  `Event.fireEvent` leaves it null — so the constructor's "x with respect to the source" is the
  scene's, and delivery recomputes the node-local one from it. Passing node-local values put `getX()`
  ~300px left of the pointer, which clamped to the first sample: the render showed the readout pinned
  to the left edge, **indistinguishable from a broken clamp in the view itself.**

**Axis labels are legible and their precision follows the span (2026-08-05).**

- ⚠ **The axis TEXT is not the gridline colour.** It was drawn in `-es-rule`, which `ContrastTest`
  exempts from the 3:1 floor *precisely because* a border held to a text threshold becomes a stripe —
  so the labels were legal and barely readable. Lines stay `-es-rule`; numbers take `-es-shmark-axis`
  (`-es-text`), which the contrast floor does measure.
- ⚠ **A FIXED precision is wrong in both directions.** At zero decimals a watchlist chart of a share
  moving 140.3 → 141.1 labelled every gridline `141 EC` — five identical labels, the same symptom as
  the wei-to-`long` overflow and a different cause. At two, a four-digit portfolio runs off the
  gutter. Derived from the span **and** the whole part, against a six-character budget.
- ⚠ **The unit is on the top label only** — it annotates the axis rather than naming an amount held,
  and repeating it costs three characters a row in the gutter that decides the precision.
- ⚠ **A share on the wire carries its ALIAS, and it did not.** `shares()` used `displayNameOf`, the
  **item catalogue's** lookup, which a ticker is never in — so every share fell through to its
  `orElse` and arrived with its symbol where its name belonged. Invisible because the tables show the
  symbol in its own column; it surfaced on the watchlist title and in screen-reader text.
  `tickerNameOf` now. Pinned by a test, verified against the unfixed code.

**AnonShare is four sub-tabs — Overview · Listings · Watching · History (2026-08-05).** They are the
questions a holder asks, in order: what am I worth, what else is there, what am I following, what
have I done.

- ⚠ **ONLY OVERVIEW CARRIES LIVE PRICES**, which is what makes the other three cheap enough to keep
  painted while they are off screen. **LISTINGS deliberately draws NO price column** — held and
  watched symbols refresh at the player's rate and everything else once a day, so most rows have
  never been fetched and a price column would print a zero or yesterday's number for the majority of
  the list, on the one panel whose whole subject is what a price is. Selecting a row quotes it on
  Overview, which fetches.
- ⚠ **Two search boxes, two queries, and they must NOT share one.** Overview's writes
  `setShareQuery` and spends a provider lookup; Listings' filters what is already known locally.
  Sharing the session's query means whichever tab repainted last decides what the *other* one is
  showing — on a tab the player is not looking at. Both fire `discoverSymbol` when nothing matches,
  so the universe grows from either.
- ⚠ **Selecting anything anywhere jumps back to OVERVIEW.** The quote ticket lives there, so a click
  on another tab that silently changed a panel the player cannot see reads as the click doing
  nothing.
- ⚠ **HISTORY is RECORDED, never recomputed.** `BrokerageState.Trade`, written at the trade, newest
  first, bounded at `TRADE_LIMIT` (300) and trimmed from the front. A price is a fact about an
  instant; a history rebuilt from today's quotes would rewrite what somebody actually paid.
  ⚠ **The commission has its own column** rather than being folded into the price — they are two
  different charges and only one is the market's, and a merged figure cannot answer why a round trip
  at an unchanged price lost money, which is the single thing this tab exists to explain.
  ⚠ **Realised gain is a SELL's figure**: a buy renders a dash, not a zero, because zero means broke
  even. ⚠ **The SIDE is not coloured** — up/down mean gain and loss everywhere else in this client,
  and a red BUY beside a green SELL says buying was the mistake, which is not a claim a transaction
  log gets to make.
- ⚠ **`main.setMinWidth(0)` is load-bearing: without it the quote card is CLIPPED.** A `Canvas` is
  not resizable, so it contributes its whole current width to the column's computed minimum — and an
  HBox satisfies minimums *before* it distributes anything, so the row demanded more than the content
  column has and the last child ran off the edge. Same family from the other side: a grown
  `TextField` that cannot shrink pushed the session readout off the nav row and JavaFX ellipsised it
  ("market open · closes 16:00" → "market open ·"), fixed with `search.setMinWidth(120)`.
- ⚠ **`column()`, never `setMinWidth`, for a table cell.** A minimum alone leaves the label's
  *computed* preferred width in charge, so one long invented name widens its own cell and pushes the
  sector out of line **for that row only** — the table looks like it has lost a column.
- ⚠ **The harness needs an OPEN session and real state.** `-Dmarket.now=2026-08-04T15:00:00Z` is
  11:00 in New York (the default 12:00Z is 08:00, i.e. shut, where every trading control is disabled
  and no history can be built); `-Dmarket.trades=N` buys N and sells one back — the only way to get a
  realised figure into the table — and `-Dmarket.watch=true` builds a watchlist.
  ⚠ `-Dmarket.subtab=N` excludes the outer TabPane **by identity**, because AnonShare's own pane
  carries the same style class and `lookup` returns the ancestor first.
- **The bundled universe is ~190 symbols, capped at `LISTINGS_LIMIT` (500) on screen**, and the cap
  says so when it cuts. ⚠ **The real name is never displayed** — `Aliaser` renames every one — so a
  name that is slightly off yields a different *invented* name rather than a false claim about a
  company. That is what makes a bundled list this size defensible: the **symbol** is the part that
  has to be right, because it is what a quote is fetched against. `referencePrice`/`annualYieldBp`
  are anchors for the offline feed, never claims.

**MARKET has a THIRD tab, AnonShare — Anonymous Shares Inc. (2026-08-04).** Real US tickers, aliased
company names, real market hours, portfolios and dividends. `engine/stocks/*`, `rules/Brokerage`,
`client/stocks/HttpStockFeed`, `view/AnonShareView`.

- ⚠ **THE COMMISSION IS THE ONLY THING BOUNDING THIS MARKET.** Every other market here has a ceiling
  derived from a number the game controls; this one tracks prices the game cannot predict, so there
  is nothing to derive. `Balance.BROKERAGE_COMMISSION_BP` (65) charged **both ways** makes a round
  trip negative-expectation — a gamble, not a printer. **Lowering it towards zero re-opens the
  faucet and every screen still renders correctly while it does.** Pinned by a test asserting a flat
  round trip loses money.
- ⚠ **THE PLAYER'S OWN API KEY, and that is also the licensing answer.** Rate limits are public;
  none of the pages stated whether a *distributed desktop app* may use a free key. The question does
  not arise when each player signs up themselves — nothing is redistributed and no key ships here.
  ⚠ The picker **links** each provider's terms rather than summarising them; a summary would be a
  claim this project makes on the player's behalf. Three providers, limits recorded **with the date
  they were checked** (`StockProvider.LIMITS_CHECKED`) so nobody reads them as current fact later:
  Finnhub 60/min (default), Twelve Data 800/day + 8/min, **Alpha Vantage 25 per DAY** — offered but
  flagged as too small for a live panel.
- ⚠ **The HTTP feed lives in the CLIENT, not the engine.** An engine that could fetch would also
  fetch on a home server — different limits, different party's terms, nobody asked. ⚠ **It never
  blocks the FX thread**: answers from cache, refreshes on a virtual thread, falls back to the
  offline feed so the panel is never blank and never waits. ⚠ **Redirects refused** (the URL carries
  the key) and **the URL is never logged**. ⚠ Rate limits are obeyed by **backing off on a 429**,
  never by counting — a hard-coded budget that drifted would throttle a player with headroom or
  hammer a service that had already cut them off.
- ⚠ **`feedIsLive` must reach the screen.** A simulated price shown as real is the only harm this tab
  could cause *outside* the game. The offline feed names itself and the panel renders it at the top,
  always.
- ⚠ **Symbols are real, names are not**, and **search matches the ALIAS** — a player who found a
  company by typing its real name would have been told the real name, which is what the layer exists
  to prevent. `Aliaser` is deterministic: a drifting alias makes a player's own portfolio
  unrecognisable. ⚠ It tokenizes on **dots and hyphens** as well as spaces, or `Amazon.com` and
  `Coca-Cola` match nothing and fall through to the bland fallback.
- ⚠ **Session hours are NEW YORK's; the clock on screen is the PLAYER's.** Every figure is an
  `Instant`, so Berlin sees 15:30 and Tokyo 23:30 and both are right. Storing "09:30" and comparing
  it to a local clock opens the market at four different instants. Weekend-observed holidays and
  half-days included; ⚠ the Good Friday list **runs out**, and past its horizon the market reads as
  open on a day it was shut — the harmless direction.
- ⚠ **Dividends are paid ONCE per quarter** (`lastPaidQuarter`). The tick runs every second and a
  quarter stays current for three months — without the marker a holder is paid once per *second* for
  a quarter of a year. ⚠ **Buying does not immediately collect** — you are paid for quarters you held
  *through*, the simplification standing in for a record date. ⚠ **Rounds DOWN**, the opposite of a
  fee: rounding a payment up creates wei from nothing on every dividend in the game. ⚠ **No
  commission on a dividend**, and **paid whether or not the market is open** — a weekend-only player
  must still collect.
- **The panel is a BROKER's layout (2026-08-05)** — account column, portfolio total, value chart with
  range buttons, positions table, watchlists. ⚠ None of the reference's styling is reproduced (§9
  makes shadows, blur and gradients build-blocking); the hierarchy is type size and position, and the
  chart is a stroked line rather than a gradient fill.
- ⚠ **PRICE HISTORY IS RECORDED, and it is the only stored series in the game.** Every other line —
  the Shadow Market's candles, the mempool, the chain — is seekable noise, so history costs nothing.
  A **real** quote cannot be recomputed: nobody can ask what AAPL cost an hour ago without having
  written it down an hour ago. ⚠ Sampled every 5 minutes, **only while the market is open** (prices
  freeze out of hours, so overnight samples are identical rows that push the interesting ones off a
  bounded buffer), **only for symbols held**, capped at 240 and trimmed from the front. ⚠ A symbol
  sold **drops its series** — otherwise the save grows forever with the history of things nobody owns.
  ⚠ The **portfolio value** is its own series, not reconstructed from the per-symbol ones: rebuilding
  it would need the share count *at each past instant*, so the line would rewrite its own past every
  time somebody bought or sold.
- ⚠ **TWO REFRESH CADENCES, and the split is what makes a free tier last the day.** Held and watched
  symbols refresh at the player's chosen interval; everything else once every 24 hours. Fifty symbols
  at the fast rate would spend a few-hundred-call allowance in minutes on prices nobody is watching.
  ⚠ `HttpStockFeed` takes a **supplier**, read at refresh time — a set captured at construction would
  leave anything bought this session stuck on the daily cadence until a restart.
- **The universe GROWS by search (2026-08-05).** The bundled fifty are a browsable starting point;
  typing an unknown ticker asks the provider's symbol-lookup endpoint and **keeps** what comes back.
  `Tickers.register` / `discovered`, persisted machine-wide in settings (bounded 500) and replayed
  into the registry at startup, so a character opened offline knows every ticker a previous session
  found. ⚠ Implemented for **all three** providers — each has its own search URL and response keys
  (`description` / `instrument_name` / `2. name`).
  - ⚠ **A lookup is a CALL against the same allowance as a quote.** It fires only when the query
    matches nothing known **and** looks like a ticker, and `SymbolLookup` remembers what it has asked
    **including the misses** — a symbol that does not exist costs the same to ask about twice.
  - ⚠ **The parser fails CLOSED.** A shape it does not recognise registers **nothing**: a symbol filed
    under the wrong company would rename it for the life of the character, and the alias is derived
    from that name.
  - ⚠ **`Tickers` holds a static registry**, which this codebase otherwise avoids. The argument is
    that it is *reference data, never game state* — the same for every character and save,
    append-only, and it decides only what a symbol is **called**. If it ever influences a rule it has
    to become a port. `forget()` exists so it cannot leak between tests.
- **The search results are a sliding OVERLAY, not a column.** Results in the flow would push the
  portfolio total and its chart down on every keystroke — the two things a holder came to look at,
  moving as they type. ⚠ Stepped on `Pulse.every` (never `animate`, which would leave it unable to
  open under Reduce motion), clipped, and **managed**: it lives in a `StackPane` where children are
  layered, so a managed child costs its siblings no space. ⚠ **Unmanaged was a real bug** — the parent
  never resized it, so `prefHeight` went to a layout pass that never ran, the node had no size, the
  background painted nothing, and the results rendered as bare text over the account column. Same
  family as `SyncBanner`'s trap, from the other side.
- **The chart has axes and a hover readout.** ⚠ The plot is **inset** and the labels live in the
  gutters — text over the plot sits on the line at exactly the values a reader is comparing. Hover
  snaps to a **recorded point**, never an interpolation: drawing a number for an instant nothing was
  sampled at would be inventing a price on the one panel whose subject is what a price was.
  ⚠ **The hover index is measured from the PLOT's left edge**, or the marker sits a fixed distance
  right of the pointer.
- ⚠ **AN AXIS LABEL CAST WEI TO `long` AND EVERY ONE READ "9 EC".** A portfolio of 2742 EC is 2.7e21
  wei and a `long` tops out at **9.22 EC**, so the cast saturated at `Long.MAX_VALUE` and all five
  gridlines rendered identically. Exactly the overflow the currency's own notes warn about; five
  identical labels is what it looks like from outside. Through `BigDecimal` now. **Found by
  rendering.**
- **A refresh countdown sits beside the chart**, on its **own one-second Pulse**. The price repaint
  runs at the player's interval — which can be ten minutes — so a timer driven by it would hold one
  number and then jump. ⚠ `nextRefreshAt` is the **feed's** answer, because only the feed knows which
  cadence a symbol is on; `EPOCH` means *not applicable* and the panel renders no timer rather than
  an invented one, which is the honest answer for a derived feed that never refreshes.
- ⚠ **ONE ROW PER SYMBOL, not one per purchase.** Two buys of one company rendered as two rows, which
  made the panel read as a ledger of transactions rather than a portfolio. `Brokerage.positions`
  collapses them; **the lots survive underneath** and the cost basis is still per-lot.
- ⚠ **Selling from a position is FIFO.** `sellPosition` takes the oldest lot first — what a broker
  does when you do not name one, and the panel no longer shows a lot to name. `sellShares(holdingId)`
  survives for anyone who does want to pick.
- ⚠ **The per-lot cost basis is why `sellShares(holdingId)` still exists.** Two buys at different
  prices have two different answers to "am I up on this", and an averaged book can only show one.
- ⚠ **Deleting a portfolio UNFILES holdings, never sells them.** A portfolio is a label.
- ⚠ **The refresh cadence is the PLAYER's** (Settings → AnonShare, 15s–10m, default 60s) and the
  slider says what it **costs** in calls-per-day, because that is the number they are really
  choosing. Read when the window opens.
- ⚠ **No `⚠` glyph in UI strings** — U+26A0 is in neither bundled face and `GlyphCoverageTest` caught
  exactly this line. Emphasis comes from the word and the colour, which §4.4 wants anyway.
- ⚠ **The wordmark is `-es-text-hi`** — not amber (GoH, income), not alarm (ShMark, hostile). A
  brokerage tracking an outside market is neither, and colouring it would claim the game had an
  opinion about what these prices mean.

**MARKET is two tabs — GoH and ShMark (2026-08-04).** The storefront moved into **GoH**; **ShMark**
is the **Shadow Market**, the darknet secondary market, as a trading desk. `rules/ShadowMarket`
simulates it, `protocol/game/Shadow*` carries it, `view/ShadowMarketView` draws it. Always viewable,
solo included — the listings are readable whether or not anybody real is on the other side.

- ⚠ **THE ARBITRAGE CEILING IS THE WHOLE DESIGN.** A player market beside the shop is
  `MarketDeals`' faucet failure one step removed and much easier to miss: if a bid here ever reaches
  what the storefront charges, buy-on-GoH → sell-on-ShMark is free money with **no compute cost**,
  repeatable, and the ethecoin supply inverts with every screen still rendering correctly.
  `ceilingPercent()` is **derived** from `100 - MarketDeals.maxDiscountPercent()` less a margin —
  never written down, for the reason `breakEvenDiscountPercent` exists. `ShadowMarketTest` sweeps a
  **year × 5 characters × every listing** and fails the build on any bid reaching the storefront floor.
- ⚠ **BOUNDED NOISE, NOT A RANDOM WALK, and that is not a style choice.** A walk is unbounded, so the
  ceiling would have to be a clamp — and a clamped walk sits pinned to the clamp, which is both
  visibly wrong and exactly the state where arbitrage is worth checking. Fractal value noise is
  bounded **by construction**, so no length of play can breach the ceiling: the economic guard is a
  property of the function rather than a check somebody must remember to run. It is also **seekable**,
  which is what lets a chart draw a week of candles instantly and the save store none of it.
- ⚠ **DERIVED, so nothing about the market is stored.** Price, book, tape and candles are pure
  functions of (character, item, clock) — the panel repaints on a clock and a drawn price would
  reshuffle the chart every second. **Only the player's own orders persist**: a buy escrows real
  ethecoin, a sell reserves one **specific copy by id** (items stopped stacking, so a type-named
  order would sell the wrong build).
- ⚠ **THE CHEAPEST ASK IS SYSTEMATICALLY THE RISKIEST, and that IS the decision.** A trusted seller
  asks a premium for the certainty; a shady one undercuts. The book sorts by price, so the top of the
  asks is the worst-rated counterparty — which falls out of sorting rather than being staged. Every
  row therefore carries its standing beside its price; a book showing prices alone renders the one
  reading of it that is wrong. ⚠ **A defection does NOT refund** — an undelivered purchase that
  refunded itself would make reputation free to ignore.
- ⚠ **`BASE_SPREAD_BP` MUST EXCEED `REPUTATION_SWING_BP` or the book CROSSES.** At depth 0 the offset
  is reputation alone: a shady buyer bid above a shady seller's ask, and a crossed book is a standing
  offer to buy and sell simultaneously for a profit with no counterparty risk. **Measured — it
  crossed within 44 minutes of the epoch.** Pinned by a test.
- ⚠ **Only ethecoin-gated items are listed** — `Repac.sellable`, i.e. **I2** and **I8**. Listing a
  schematic- or zero-day-gated tool would put a price on the one thing whose point is that it has none.
- ⚠ **The market settles on the TICK, not when the panel is open.** An order that only filled while
  its window was on screen would teach players to leave the panel open.
- ⚠ **A `Canvas` cannot resolve a looked-up colour**, and §10 criterion 2 forbids the hex literals
  that would replace it — so the chart reads its two colours off **invisible probe labels in the live
  scene**. ⚠ The first version wrapped a probe in a **throwaway `Scene`, which carries no stylesheet**:
  nothing resolved, every probe returned Modena's default, and every candle rendered the same colour
  with up and down indistinguishable. No error anywhere. **Found by rendering.**
- ⚠ **THE CANVAS MUST BE REDRAWN ON LAYOUT.** The panel paints once during construction, before
  anything is in a Scene and before CSS — so even with the probes fixed the first frame was
  monochrome. The running game hides it a tick later; a synchronous render makes it permanent, which
  is how it was found. ⚠ **Only the chart** is redrawn there: re-running the full repaint rebuilds the
  book, which dirties layout, which fires the listener again. Drawing on a Canvas dirties nothing.
- ⚠ **A doji's body is floored at one pixel.** Open equal to close is a real and common candle, and a
  zero-height body draws nothing — the chart develops gaps that read as missing data.
- ⚠ **Asks are drawn HIGHEST first** so the two sides meet at the spread, as every book a player has
  seen is laid out. Best-first puts the touch at the top and the spread at the outer edges.
- ⚠ **The wordmark is `-es-alarm`** — the one place §2.1's reservation for *hostile state* is the
  right reading rather than a borrowed one. Nobody here is bonded and the money can simply not come
  back. GROUP OF HACKS is amber because a shop is income; this is not a shop.
- ⚠ **Prices are typed through `Ethecoin.ofDecimal`, never `Double.parseDouble`** — the second place
  in the game a player types an amount, and a double holds ~16 digits against ethecoin's 18.
- ⚠ **`RemoteGameSession` answers an EMPTY market, never a local simulation.** On a server the prints
  are real trades across the federation; answering with a simulation would put invented prices on a
  screen whose entire subject is what a price is. **W-9**, unbuilt.

**ShMark trades between real people: listings, no escrow, and a 6-hour clock (2026-08-04).**
`rules/ShadowTrading`; `state/ShadowListingState` + `ShadowObligationState`; `protocol/game/{DeliveryMode,
ShadowListing,ShadowObligation}`.

- ⚠ **THERE IS NO ESCROW, AND THAT IS THE FEATURE.** Money moves the instant a buyer confirms and
  nothing holds it — if the seller never ships, the buyer has simply lost it. A market between people
  who can defect is a different game from one that cannot go wrong. ⚠ **Reintroducing escrow anywhere
  collapses `DeliveryMode` into one option**, because the whole difference between the two is whether
  the buyer is carrying risk. `ShadowOrderState.escrowWei` survives as a permanently-zero marker
  saying so.
- ⚠ **`ATTACHED` REMOVES the items from storage; it is not a reservation.** A reservation looks
  equivalent and is a lie — the seller could equip, sell elsewhere or delete the reserved copy and the
  "safe" purchase would fail at delivery with nothing able to say why. Cancelling returns them to
  **arrivals**: the listing did not remember where they came from, and inventing a destination files
  goods somewhere the player did not choose.
- ⚠ **Possession is required for BOTH modes.** A send-later listing for something never owned is a
  confidence trick with no cost of entry, and a market where those are free is one where every
  listing is presumed fake.
- ⚠ **Delivery mode is DERIVED from the seller's rating, not drawn.** A shady seller usually wants
  paying up front; a trusted one usually attaches. Rolling them apart would give trustworthy sellers
  who demand trust anyway and shady ones who hand the goods over — price and risk carrying no
  information about each other, so there is nothing to read. **"Usually", not "always"**, or the
  standing would be redundant with the mode.
- ⚠ **The mode is the FIRST thing on a listing row and the header of the confirmation**, ahead of the
  price. It is the whole decision; a screen leading with the number sells risk without naming it.
  ⚠ **Only the risky one is coloured** (`-es-warn`) — colouring both makes them read as two categories
  of equal weight. **Not `-es-alarm`**: §2.1 rations that to loss, and a promise is not a loss until
  it is broken (an *overdue* obligation is, and that is where alarm is spent).
- ⚠ **The confirmation's button NAMES THE ACT and Cancel is the default** — `MainMenuView`'s two
  rules, and they apply harder here: there is no escrow behind that button, so a mis-press cannot be
  undone by anybody.
- ⚠ **`Balance.SHADOW_FULFILMENT_HOURS` (6) runs on the TICK**, so the deadline survives a logout —
  otherwise closing the client would be the way to escape one. ⚠ **`settled` makes the penalty land
  once**: the tick runs every second and an overdue obligation stays overdue, so without it a seller
  who missed a deadline is penalised once per second until they notice.
- ⚠ **No refund on default, in either direction**, and the **buyer's** standing is untouched — the
  reputation cost belongs to whoever failed to act. `SecondaryMarket.defect` is reused rather than
  duplicated, so there is one defection mechanic reached from two places. ⚠ Its roll is seeded from
  the save's `Rng` and committed back: `new Random()` would make being caught depend on when the
  client happened to be running.
- ⚠ **A bid that outlived its funding is CANCELLED, not defaulted.** The fill is the agreement, and it
  did not happen — treating it as a defection would punish somebody for a market moving while they
  spent their own money.
- ⚠ **A dialog builds its own Scene and inherits NO stylesheet** — it paints Modena white unless the
  owner scene's sheets are copied onto it. Same family as the unstyled `ScrollPane` viewport.
- **Your listings sell to NPCs on the tick, at a RATE PER HOUR (2026-08-04).** ⚠ **Never a chance per
  tick** — that makes a faster-ticking client sell faster and gives a three-day absence exactly one
  roll, both invisible in play and both making the tuned number meaningless. `1 - e^(-rate × hours)`
  off the tick's own `elapsed`, so frequency drops out and a listing left up over an absence settles
  as though it had been standing the whole time, which it was.
  ⚠ **Priced against the mid**: measured at 0.9h median at market, 0.2h at 30% under, 4.9h at 10%
  over, 27.7h at 20% over — halving every 4% above, because "significantly less" means a few percent
  over should visibly *stall*.
  ⚠ **NOTHING SELLS ABOVE THE ARBITRAGE CEILING, at any probability** — a hard zero, not a small
  number. An NPC's ethecoin is invented, so paying above the storefront's floor is *issuance* on a
  repeatable action; "unlikely but possible" is still a faucet. Selling to a real player is a transfer
  and must not inherit this.
  ⚠ **One unit per sale**, or quantity becomes a multiplier on luck rather than on time.
  ⚠ A send-later sale leaves the player **owing**, on the same 6-hour clock — the mode reaching them
  from the other side.
- **A listing fee, by standing (2026-08-04).** `ShadowTrading.feeBasisPoints`: **1.5% trusted / 3%
  standard / 12% shady**. ⚠ **Basis points, not percent** — the trusted rate is not an integer number
  of percent, and whole percent would either lose it or invite a `double` into wei arithmetic.
  ⚠ **The bands ARE the standing bands**, not new thresholds: a second set would let a seller read
  "trusted" on one screen and be charged the shady rate on another, both screens correct.
  ⚠ **The untrusted pay TWICE** — once to list and again on sale — and the up-front charge is
  **refused if unaffordable** (taking a partial fee and listing anyway is the worst of both) and
  **not refunded on withdrawal**. A refundable one is no deterrent: a shady seller could paper the
  board and withdraw for free, which is the exact behaviour it exists to stop.
  ⚠ **Taken from the PROCEEDS at payment**, so it is already gone when a deadline lapses — a fee
  charged at delivery is one a defaulting seller never pays.
  ⚠ **Rounds UP**, or a small enough listing arranges a fee of zero.
  ⚠ **Burned, not paid to anybody** — this is a real sink; crediting a house account would make it a
  delay instead.
  ⚠ **A resting SELL ORDER pays it too.** Exempting the order form would be a fee-free back door
  every seller would learn to use — the same feature with the sink switched off.
- ⚠ **A LISTING IS NOT A PRICE TICK, and keying it to one made buying IMPOSSIBLE.** Listings were
  derived from the 2-second `TICK`, so the board turned over while the confirmation dialog was open
  and `buyNow` answered "that listing is gone" every time — a board that looked alive and could not
  be traded with. `LISTING_DWELL` is **2 minutes**, staggered per slot so roughly one turns over
  every 20s while each individual one stands for the full dwell. ⚠ Solo only: a federated listing is
  a real posting and does not rotate at all.
- ⚠ **A listing's PRICE is frozen at the instant it opened**, read from `bookAt(opened)` and never
  from `now`. Reading live leaves the id stable and the price drifting under it, so the confirmation
  quotes one number and the debit takes another — invisible until somebody checks their ledger.
  ⚠ The board is therefore **sorted after the fact**: each slot froze at a different instant, so slot
  order would put a stale dear listing above a fresh cheap one.
- ⚠ **`offer()` RECONSTRUCTS from the id rather than searching the live board**, with a bounded
  `LISTING_GRACE` (45s). With staggered slots one listing is always near its boundary, so searching
  live made purchases fail through nobody's fault. The grace is a quote-expiry window; it is
  **bounded** because an unbounded one is a free option on a moving market that a player could farm.
  ⚠ A listing from the **future** is refused too — an id is a string and a save is a file the player
  can edit.
- ⚠ **A listing's id is derived from (item, window, depth), never random.** A random id would be a
  different listing on every repaint, so the confirmation would name one thing and buy another.
- ⚠ **Room is checked before the money moves**, and for send-later too — refusing now is far kinder
  than refusing in six hours.

**The ShMark order form is a HOVER DRAWER on the right edge (2026-08-04).** `ShadowMarketView.drawer`
— a vertical `BUY / SELL` handle; hovering it slides the form in from the right. It was a third
column in the row, where it took width from the chart at every window size and sat on screen whether
or not anybody was trading.

- ⚠ **It STEPS, on `Pulse.every`.** §5 permits no easing and `UiContractTest` rations `AnimationTimer`
  to two files by name, so the slide is `REVEAL_STEPS` whole jumps — the ladder `SizeReadout`,
  `BalanceDelta` and `Motion` already use. ⚠ **`every`, not `animate`**: a decorative subscription
  never fires under Reduce motion, so an `animate` drawer is one that **cannot open** — the control
  broken on exactly the accessibility path. The clock runs in both modes and only the ramp is
  conditional; with motion suppressed it snaps open in one step.
- ⚠ **Hover is on the DRAWER, not the handle.** Opening it puts the pointer on a journey across the
  form, which is part of the drawer — keying on the handle alone closes it the instant the player
  moves toward the thing they opened it for. Focus-within holds it open for keyboard users.
- ⚠ **A rotated caption inside a `StackPane` renders as an ELLIPSIS.** A StackPane resizes a
  resizable child to fit itself, so the label was squeezed to the handle's 22px width and truncated
  to `...` — and only *then* rotated, so what reached the screen was a vertical row of three dots
  that read as texture. `Group` does not resize its children; wrapping the label in one keeps its
  natural width. ⚠ Same root cause as the `SHMARK_TAB_WIDTH` note: **a rotation is a transform and
  does not change layout bounds**, so the holder must be a fixed box.
- ⚠ **Overlay, clipped to the panel.** It rests translated a full form-width right, which is off the
  panel's edge — without the clip a closed drawer paints outside its own window. `USE_PREF_SIZE` both
  ways, or the StackPane grows it into a transparent full-panel pane swallowing every click.
- **The instrument picker is a `MenuButton` grouped by category** (`Offering.category()` = the first
  tag). ⚠ **Tag zero, not a new field** — a parallel `category` would be a second answer to a question
  the tags already settle, and the day somebody edited one and not the other the picker and the
  storefront's search would file the same item in two places. `ShadowMarketTest` holds that every
  listing is filed and that the categories actually group.
- ⚠ **The menu is built ONCE, not per repaint.** The panel repaints every second, and a menu rebuilt
  under an open popup closes it mid-click.
- **YOUR ORDERS sits UNDER THE CHART**, in the same column, not at the foot of the page. Below
  everything it was past the fold on any normal window — the one part of the screen about the
  player's own money was the part they could not see, while the chart column carried a block of empty
  space the same size. Each row carries a **Cancel** (not "Withdraw": the correct term is the one
  nobody reads on a trading screen) and the **escrow it is holding**, because that is money the
  balance no longer counts.

**Items do not stack, and a purchase needs somewhere to land (2026-08-04).** `rules/StorageRules`.

- ⚠ **OWNING ONE IS NO LONGER A REASON TO REFUSE A SALE, and `Repac.install` allows a duplicate.**
  Each copy has its own `itemId`, tier and build, so a second Tarpit is a second *thing* — a shop
  that refused was answering a question about inventory rather than about money. The old refusal's
  reasoning ("this copy is worth more sold than installed") survives where it belongs: `manifest()`
  still reports `owned`, so the package panel says so **before** anything is consumed.
- ⚠ **Two copies must be two FILES — `Repac.boughtPackageName` puts a 6-char order tag in the name.**
  `Repac.find` resolves a path to *the first* match, so two `tarpit.pkg` files would make Get Info
  describe one, `install` consume one and `rm` delete one, with nothing on screen saying which — a
  filesystem where the path is not an identifier.
- ⚠ **A BOUGHT item lands in `StorageRules.ARRIVALS` — the HIGH-RISK zone, not the vault.** The vault
  is meant to be a decision: goods you have not put away are goods anybody can take, and a purchase
  that filed itself safely would make `design/01` §6's tiers a setting nobody touches. ⚠ **A STOLEN
  item keeps the vault** — you already carried the risk of taking it, and charging it again on
  arrival is the same tax twice. That asymmetry is deliberate; if it ever reads wrong, change it in
  one place (`Repac.install`).
- ⚠ **Capacity is enforced ONLY at purchase.** `Balance.storageCapacity`'s own note warns that a hard
  cap of six on the vault with no way to raise it "is a different game from the one that document
  describes" — so nothing caps the **vault** and nothing refuses a **move**. What is enforced is the
  narrow thing asked for, and it binds against the arrivals tier's 60 rather than the vault's 6.
- ⚠ **COMMITTED, not occupied — three things claim a slot.** Items in the tier, orders in the queue
  (a bundle once per member), and bought packages still sitting in Downloads (an unextracted archive
  once per package inside). Counting only installed items lets a player queue a hundred against sixty
  and find out forty installs later with the money gone. ⚠ A **stolen** package claims nothing — it
  lands in the vault, and counting it would make the shop refuse a sale over a file taken for free.
- ⚠ **A bundle checks room for every member ONCE.** Asking per item passes for the first two and
  fails on the third with the money already gone.
- **The item id is surfaced now**, because it never was: `verify <item>` has always *taken* an
  `itemId` and nothing anywhere *showed* one. Six characters on the storage tile and in the ROWS
  listing (matching the package tag, so it is one habit not two), the full id on the tooltip, in the
  selection panel and in `verify`'s output — full wherever it is meant to be copied, since a
  truncated identifier that looks copyable and is not is worse than none.

⚠ **`-fx-strikethrough` DOES NOTHING ON A LABEL, and it failed silently for a week (2026-08-04).**
It is a property of `Text`; **`Labeled` has `-fx-underline` and no strikethrough**, and JavaFX drops a
property it does not recognise without warning — the same silence that hides an unknown looked-up
colour (`-es-accent`). `.es-market-was` declared it, the stylesheet read exactly right, and the market
showed a sale price beside the old one with **nothing saying which was cancelled**. Invisible in
review; found by magnifying a render.

- **The line is DRAWN** — `MarketView.struck` puts a 1px `Region` over the `Label`, the same reasoning
  behind the carousel's drawn dots and the flash overlay's drawn warning mark: a thing that must be
  certain is not left to a property that can be dropped.
- ⚠ **A `Text` node was the other option and is worse here.** It supports the property and colours
  with `-fx-fill` rather than `-fx-text-fill`, which would take the price out of `ContrastTest`'s
  reach — and that test measures every text token against both panel grounds in eight palettes.
- ⚠ **The baseline must be delegated to the label.** A `Region`'s `getBaselineOffset()` is
  `BASELINE_OFFSET_SAME_AS_HEIGHT`, so in the `BASELINE_LEFT` rows these sit in, a bare `StackPane`
  aligns its *bottom edge* to the row's baseline and the struck price rides up above its neighbour.
- ⚠ **The rule is raised off the box centre by `STRIKE_RISE`** (0.125 em, derived from the applied
  font). A label's box carries the descender space, so its centre sits below the middle of a row of
  figures and a centred line reads as one that has slipped.
- ⚠ **The rule takes the TEXT's colour, never an accent.** A strikethrough is part of the word it
  crosses; §2.1 rations `-es-alarm` to loss and hostile state, and a price no longer being charged is
  neither.
- ⚠ **`UiContractTest.strikethroughIsNotAvailableHere` bans it from all six stylesheets**, negative-
  tested by planting one. Blanket rather than scoped because every text node in this client is a
  `Labeled` — the day a `Text` is styled by class, that test is where the exception gets carved, with
  the class named.

**The storefront is a FIXED CONTENT COLUMN of floating cards (2026-08-04).**
`UiTokens.MARKET_CONTENT_WIDTH` (960), centred; the shelf is a tile grid.

- **One measure for the whole page** — masthead, search, carousel, bundle and shelf all take it. The
  reason is legibility, not fashion: text reflowing to `MAX_SUPPORTED_WIDTH` is a line nobody's eye
  tracks back from, and a shelf that silently goes from three tiles to eight is a different shop at
  every window size. A **maximum**, so a narrow market window still works — the tiles wrap to two,
  then one.
- ⚠ **The cap goes on the PAGE and the centring on a holder around it.** A `ScrollPane` with
  `setFitToWidth` resizes content to the viewport and has **no alignment** for content narrower than
  that, so capping the page alone pins the whole shop to the left edge of a wide window.
- ⚠ **The bundle sits BELOW the carousel with a band of clear space either side** (`MARKET_BAND_GAP`,
  inside §2.3's closed scale). It is a different *kind* of offer — one price for several things — and
  stacked flush against the carousel above and the shelf below, all three read as one undifferentiated
  column of cards. The band is the only thing saying "this is a different question". An explicit
  spacer node, not more container spacing: widening the VBox would push the carousel off the masthead
  too, which is a different relationship and already right.


- ⚠ **A market card had NO BORDER and was filled `-es-panel` — the same colour as the window body**,
  so nothing could say where one ended and they read as full-bleed slabs. `.es-market-card` now
  carries the deck's one card recipe, the two properties `.es-block` and `.es-package` already use:
  **`-es-panel-hi` ground + 1px `-es-rule`**. §2.1's "depth from brightness, never shadow" is the
  only lever available (§9 makes drop shadows build-blocking), so **the lift IS the float**.
  ⚠ Correct on **uOS Classic** for free — `panel-hi` is lighter there too, so a card reads as paper
  raised off the desk rather than a hole in it. A literal colour would have inverted.
- ⚠ **The accent-edge rules are TWO-CLASS and declared AFTER the hover rule.** `.es-market-card:hover`
  is class + pseudo-class, the same 0,2,0 as `.es-market-card.es-market-deal`, so at equal weight the
  later rule wins and a one-class `.es-market-deal` would lose its amber edge under the pointer —
  `.es-block-yours` recorded this exact trap one screen up.
- ⚠ **The page needs a GUTTER or a bounded card is not a card.** With four borders, a page flush to
  the window body puts every left edge on x=0 where the window clips it — full-bleed bands again,
  which is what the border was added to stop.
- ⚠ **`TilePane`, not `FlowPane`, for the shelf.** A FlowPane gives each card its own height and
  centres it in the row, so a long description leaves its neighbours' Buy buttons at three different
  heights — a styled list. TilePane sizes **every** tile to the largest, and a `VBox`'s maximum is
  unbounded (a **Control's would not be** — the `Vgrow` trap), so each card fills its tile and a
  `Vgrow` spacer puts every price and Buy on one line.
- ⚠ **A `FlowPane` FILLS its children to the row height** — `rowValignment` does not stop it,
  `layoutInArea` grows a child to its maximum whatever the alignment says. Found when the bundle sat
  *beside* the hero (superseded, see the content column above): the shorter card was stretched to the
  hero's height with a third of itself empty. `setMaxHeight(USE_PREF_SIZE)`, which the bundle keeps.
  Same family as `HBox.setFillHeight`, from the same side.

**Bundles buy in one action, ship as a `.tar.xz`, and downloads QUEUE (2026-08-04).**
`engine/rules/DownloadQueue` + `Archives`; `state/DownloadOrderState`; `protocol/game/DownloadOrder`;
`view/DownloadDock`. `unxz(1)` in the shell, Extract in the file manager.

- ⚠ **THE ACTIVE DOWNLOAD IS DERIVED — the first order that is not paused.** Nothing stores which one
  is running. That single decision is why pause and reorder need no separate machinery: moving an
  order to the front *is* starting it, pausing the head *is* promoting the next, and no combination
  can leave a stored flag disagreeing with the list.
- ⚠ **An ORDER is not a TASK.** A task is work with a deadline and cannot express "three bought, one
  downloading". The order exists from the moment the money moves; the transfer exists only while
  bytes are in flight. **Persisted**, because a queue that lived in the client would lose paid-for
  downloads on close, which is indistinguishable from being robbed.
- ⚠ **A HELD DOWNLOAD HAS BOTH ENDS OF ITS CLOCK PUSHED FORWARD.** Wall time cannot be stopped, so
  holding means shifting `startedAt` **and** `endsAt` — shifting only `endsAt` stretches the transfer
  instead of pausing it and the bar crawls backwards. ⚠ It must run in the tick **and in `resume()`
  with the absence as the delta**, or a queue paused across four days finds every held transfer
  finished on the first tick back — the pause doing the opposite of what it says, and only for a
  player who closed the client. Both halves are negative-tested.
- ⚠ **`enqueue` settles immediately.** Leaving promotion to the next tick means a lone purchase shows
  a queue entry and no bar — about a second in the running game and **indefinite under a test clock**,
  which is what six existing purchase tests caught. `Duration.ZERO` is the right delta: no wall time
  passed inside the call, so nothing held may shift.
- ⚠ **Extraction takes REAL TIME, unlike Repac.** `Repac.repack` is instant because renaming a payload
  is bookkeeping; `xz` is genuinely slow to decompress, and `EXTRACT_BYTES_PER_SECOND` is deliberately
  **below the link speed** so unpacking outlasts the download. That relationship is the teaching — a
  rate above the link would teach the opposite with nothing reporting a problem.
- ⚠ **The archive is consumed at COMPLETION and its contents are built FIRST.** Same rule as the
  firmware flash: an interrupted extraction must cost nothing rather than everything, and the member
  list lives **only** on the archive (`archiveItemTypes`), so removing it before building the members
  destroys a bundle the player paid for.
- ⚠ **Members carry the BUNDLE's `lockedByEntryId`**, so one payment releases the whole thing.
  Unpacking is local work on bytes already held and settles nothing — releasing on extraction would
  make a bundle the one purchase that skips the on-chain settlement every other purchase waits for.
- ⚠ **All or nothing, checked BEFORE the debit.** A bundle price is quoted for a specific set; selling
  three-quarters of it at that price is worse than refusing, and there is no refund mechanism.
- ⚠ **Field 4 of the archive task's `outcome` is EMPTY and the members are field 6.** Assembled with
  `String.join` by index rather than a `+` chain — spelled the other way it is a double space inside a
  literal, invisible in review, and deleting it shifts the entry id into the item-type slot and loses
  the lock.
- ⚠ **The dock is LAID OVER the page and takes no layout space.** A panel in the flow would appear
  above or below the fold depending on scroll, so the one confirmation a purchase worked would be
  invisible about half the time — and it would move the shelf on every purchase. Same rule the balance
  delta had to learn. ⚠ **A `StackPane` RESIZES a resizable child to fill it**, so the maximums are
  what stop it being a transparent full-window pane eating every click.
- ⚠ **`Pulse.every` does NOT invoke immediately — the exact inverse of `Pulse.animate`.** The dock is
  data, so it is on `every`, so it must paint once at build or it opens as an empty box for half a
  second and **forever in a synchronous render**. Found by rendering.
- ⚠ **`Boolean.getBoolean` needs the literal `"true"`** — `-Dmarket.bundle=1` is silently false, and
  the render reported two features missing that were present.
- ⚠ **A render harness must not wrap `MarketView.create` in a second `ScrollPane`** — that hands the
  dock's StackPane its *preferred* height, so bottom-centre lands a page below the viewport and the
  dock photographs as absent.
- ⚠ **Scope: MARKET downloads only.** A pull off a machine you are standing on runs over a session you
  are holding open and paying for; queueing it behind two bought packages would cost a foothold to
  ration bandwidth nobody has to ration.
- ⚠ **`MarketDealsTest`'s ceiling is unchanged and still binds** — a bundle is still capped by
  `maxDiscountPercent()`, so buying one to resell is still not free money.

**TODAY'S OFFERS is a carousel — one hero card, arrows, dots (2026-08-04).** `MarketView.carousel`.
It was three cards across, which showed every offer at once and left arrows nothing to do; one at a
time buys the room for the full description, the struck-through price and the saving.

- ⚠ **`Pulse.animate` INVOKES ITS ACTION ONCE IMMEDIATELY — documented, and a trap for an action that
  ADVANCES rather than paints.** The immediate call is right for its usual caller, a widget that would
  otherwise be blank until its first tick. Here it stepped the carousel before anyone had seen it, so
  the shelf opened on **offer 2 of 3**. ⚠ **Worse under Reduce motion**, where the immediate call still
  happens and the periodic one never does: the second offer becomes the permanent one and the first is
  reachable only by pressing an arrow — a defect that lands on the accessibility path only. A
  `settled` flag skips the first invocation. **Found by rendering; it compiles and reads correctly.**
- ⚠ **There are TWO settling invocations, and the second is easy to miss.** `Pulse.setReducedMotion(
  true)` fires every decorative subscription **once** so a widget suppressed mid-animation paints its
  final state — for an advance that is one more step, i.e. turning Reduce motion on skips the player
  forward a card. The action asks `Pulse.shared().reducedMotion()` and bails; the flag is assigned
  before the loop fires, so the answer is already true by then.
- ⚠ **Auto-advance is `Pulse.animate`, i.e. DECORATION**, so Reduce motion holds one card still and
  the arrows still work — that is WCAG 2.2.2's pause, and the same relationship `WallpaperMode.moves()`
  encodes. Paused on hover and on focus, because a card that moves while it is being read is worse
  than one that does not move at all.
- ⚠ **The dots are drawn `Region`s, never glyphs.** `GlyphCoverageTest` has already rejected four
  block elements and the warning sign; `←`/`→` are safe only because `NetCanvas` already renders them.
- ⚠ **`Math.floorMod`, not `%`** — `-1 % 3` is `-1` in Java, so the left arrow on the first card
  throws rather than wrapping to the last.

**The market is a storefront, with deals that rotate every three days (2026-08-04).**
`engine/rules/MarketDeals` decides; `protocol/game/MarketWindow` carries; `view/MarketView` renders.

- ⚠ **A DISCOUNT CAN TURN THE ECONOMY'S SINK INTO A FAUCET, AND THE CEILING IS ARITHMETIC.** Anything
  ethecoin-gated is resellable, and `Repac.resaleValue` is a fraction of the **catalogue** price, not
  of what was paid. A market package ships at `MARKET_UPGRADE_VERSION_MAJOR` (3), so it fetches
  `RESALE_PERCENT` scaled by `UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR` — **74.4% of retail**. Past a
  **26%** discount, buy-then-resell is free money with no compute cost. `breakEvenDiscountPercent()`
  **derives** that from the constants rather than restating it; `maxDiscountPercent()` holds 5 points
  below; `MarketDealsTest` walks a year of rotations across five characters and fails the build if any
  deal or bundle reaches it. ⚠ **Widening a band without reading this inverts the ethecoin supply,
  silently** — the shop still works and the price still renders.
- **Bands:** consumables 10–20%, permanents 5–10%, bundles 12–18%. ⚠ Permanents are shallower not
  because they are stronger (I2 already forbids a ceiling) but because they are bought **once** — a
  discount there leaves the sink permanently smaller for a decision the player was making anyway.
- ⚠ **`Durability` is a new classification and is NOT a gate.** `equippedCycles == 0` is the tempting
  proxy and is wrong both ways: a Net Sweep holds nothing and is kept forever; a Relay hop holds
  nothing and is per-session. The convenience constructor defaults to **PERMANENT**, the cautious
  direction — a new entry gets the smaller sale by omission, never the larger.
- ⚠ **Deals are DERIVED from (character, 3-day epoch), never drawn and never stored.** The storefront
  repaints on a clock; a drawn deal would reshuffle the shelves every second. Same rule as
  `MempoolRules.projectionDepth`. `floorDiv`, so an instant before 1970 does not share a window with
  one after it.
- ⚠ **Only ethecoin-gated items go on sale.** A "sale" on a schematic-gated item would put a price on
  the one thing whose whole point is that it has none.
- ⚠ **`MarketWindow` carries `asOf` — the SESSION's clock.** The countdown built on `Instant.now()`
  read "8h 5m" against a window the game clock had opened minutes earlier. Caught by rendering it.
- ⚠ **The discounted price rounds UP.** Integer division truncates, which rounds the price down and
  the discount up — a wei, in the one direction the resale ceiling guards.
- ⚠ **The bundle is priced but not purchasable as one action**, and the card says so. Wiring it as a
  loop over `purchase()` would charge retail per item and silently ignore the bundle discount.

**LOG has a third tab, CLIENT LOGS (2026-08-04)** — the application's own log, all five levels, one
toggle each. `client/log/{ClientLog,LogEntry,LogLevel}` capture; `view/ClientLogView` renders.

- ⚠ **`java.util.logging`, not SLF4J.** No new dependency, no enforcer amendment — and JUL is what
  the libraries here already use (Flyway logs to it; commons-logging falls back to it), so **one
  handler captures the client and its libraries in one ordered stream**. "The migration ran, then the
  save loaded, then the deck failed" is a sequence no per-subsystem log would show.
- ⚠ **THE ROOT LOGGER IS NOT OPENED TO `ALL`, AND THAT IS MEASURED.** The obvious way to "capture
  everything" makes the panel useless inside one frame: **JavaFX logs its own layout at `FINEST`**,
  a record per node resized and per node moved, per pass. First render with the root open dropped
  **11,905 records** and evicted every line the client itself had logged. It compiled, the capture
  tests passed, and only a render showed it. So `io.github.stoicswe.eyeandsickle` goes to `ALL` and
  the root keeps its default — libraries still contribute INFO and above.
  `-Deyeandsickle.log.verbose=true` opens the root for anyone chasing a library.
  `ClientLogTest.theRootIsLeftAlone` guards it, because the wrong version still passes every other test.
- ⚠ **TRACE is CAPTURED but not SHOWN.** The panel starts with it filtered out. Capturing it anyway is
  the whole point: a player asked to turn trace on sees what led *up* to the problem rather than only
  what happens next, so an intermittent fault does not need reproducing first.
- ⚠ **Installed in `Launcher.main`, before the toolkit.** There is no backfill, and start-up is when
  the failures worth sending in happen.
- ⚠ **A `LogRecord` is flattened at capture.** It is mutable and reusable, its message is a format
  string resolved later, and its parameters may be live game objects — so holding one renders somebody
  else's message later and pins an object graph meanwhile.
- ⚠ **Level colours need TWO-CLASS selectors** (`.list-cell.es-log-error`): `.list-cell` already sets
  `-fx-text-fill` at one-class specificity. All five are existing `ContrastTest` tokens, so they are
  measured in eight palettes; **`-es-dim-3` is not available** — it is the greeble token, exempt from the
  floor, and the network map is where that already went wrong.
- ⚠ **The ListCell clears every level class before adding one.** Cells are recycled, and a class left
  behind paints an INFO line in the error colour.
- **Logging is at CHOKEPOINTS**, the same rule the event bus follows: `changed()`/`announce()` in
  `LocalGameSession` (FINE for a success, INFO for a refusal), `Shell.finish()`, the two disk writes,
  `DeskManager.open/close`, `LocalDatabase` migration, and every previously-silent catch —
  `AvatarChooser`, `CharacterSlots`, `EventBus`'s subscriber-failed handler. Instrumenting call sites
  instead means the next one added goes unrecorded.

**THE MESSENGER SENDS NOW, AND MESSAGES ARE BUBBLES (2026-08-06).** `BlueskyChat.send` over
`chat.bsky.convo.sendMessage`; `DirectView.Composer` + `DirectView.bubble`. Own messages are
`-es-amber` and right-aligned, everyone else's a neutral ground and left-aligned.

- ⚠ **THE TAB IS CALLED "ALO MESSENGER"; THE KEY AND THE CLASS ARE STILL `direct`.** `ui.comms.direct`
  is what a translation is filed under and the class is still `DirectView` — renaming either to
  follow a display name moves three things to change what one strip says. Same rule COMMS' own id
  records from being relabelled COMPort.
  ⚠ **`-Ddeck.commsTab=` matches a PREFIX now, not for equality**, which is what survived the rename:
  an exact match against a label breaks silently the day the word is edited, and it breaks by
  printing NOT FOUND and then photographing the wrong tab — the failure the flag exists to stop.
- ⚠ **A `TextArea`, not a `TextField`, and Enter had to be taken back from it.** Wrapping, newlines
  and growth are all impossible on a field. **Enter sends; Shift+Enter inserts a newline** — via an
  event **FILTER**, because a text area's own skin handles Enter first: a normal handler runs *after*
  the newline is inserted, so the message would send AND leave a blank line in a box just cleared.
  Shift+Enter is deliberately **not** consumed and falls through to the default rather than
  re-implementing insertion.
- ⚠ **It grows to six rows and then scrolls, and the row count is MEASURED FROM A STANDALONE `Text`
  NODE.** The obvious version reads the control's own laid-out `.text` node and **never grows**:
  running from a text listener, the skin has not re-laid-out the new string, so the node still
  carries the *previous* height. Measured — one row for a 351-character message, always exactly one
  layout pass stale, so the box grows one line late forever and looks simply broken.
  ⚠ **`Platform.runLater` is the other tempting fix and is worse**: no queued runnable executes
  during a synchronous `Scene.snapshot`, so every render harness would photograph a one-line box and
  report the feature absent. `rowsFor(text, width, font)` is pure, needs no Scene, and is tested.
  ⚠ It also refits on **width**, since a wrapped message's line count is a function of its column.
  ⚠ Verified by render: **2 rows → 53px, 6 rows → 121px (capped)**.
- ⚠ **`field.setMinHeight/​setMaxHeight(USE_PREF_SIZE)`** or the HBox fills it to the row height and
  the growth does nothing visible; and the row is **`BOTTOM_LEFT`**, because a centred Send button
  drifts upward as the box grows — the one control on the panel that must not move.
- ⚠ **THE SEND BUTTON IS A DRAWN ENVELOPE — `ui/widgets/MailMark` — AND NOT A GLYPH.** `U+2709` is in
  neither bundled face and `GlyphCoverageTest` scans **source** for literals; it already rejected
  `U+26A0` in this same window. ⚠ **§9's icon-set ban is intact**: what that forbids is a *vocabulary*
  of symbols standing in for words across the interface, and this is one shape for one control, on
  the same footing as `SecurityMark` and `SectionMark`. A second one is the moment to ask whether a
  set is being assembled.
  ⚠ **Stroked, and drawn AT SIZE rather than authored large and scaled** — a scale transform scales
  the stroke with it, so a 1px hairline from a 24px box arrives at ~0.5px and greys into a smudge at
  the 14px this renders at. ⚠ **`-fx-fill: transparent` is load-bearing**: an `SVGPath` defaults to a
  **black** fill, so both subpaths would paint solid — a black blob on a dark button, i.e. invisible
  rather than obviously wrong. ⚠ The stroke is **`-es-text`, never a literal and never amber**: it is
  the token `ContrastTest` already measures against `-es-panel-hi`, which *is* this button's ground,
  so it inverts on uOS Classic for free (measured: `#A9BCBD` on the deck, `#101010` on Classic).
  ⚠ **`accessibleText` AND a tooltip are both required, not decoration.** `SocialMark`'s "the words
  carry it, the mark reinforces" argument does **not** apply — there are no words, the mark is the
  whole control, so that is the only place "Send" exists. The tooltip names **Enter** as well, since
  an icon cannot say which key sends. ⚠ The disabled state colours the *mark*, because an icon button
  has no greyed-out word to carry it.
- ⚠ **A CONVERSATION OPENS ON ITS NEWEST MESSAGE — `DirectView.scrollToEnd`, and it must lay out the
  SCROLL PANE, not just the transcript.** `vvalue` is clamped against the pane's own idea of its
  content height, which it only learns during **its** layout pass — laying out the content alone
  leaves the pane still measuring the *previous* conversation, so opening a long history after a
  short one lands part-way down and reads as scrolling to a random place. ⚠ Synchronous, never
  `Platform.runLater`: that never runs inside a synchronous `Scene.snapshot`, so a harness would
  photograph a transcript at the top and report the behaviour absent. ⚠ Package-private so
  `DirectSnapshot` drives **the real thing** — a harness reimplementing the two layout calls would
  agree with itself and prove nothing. Verified: `vvalue=1.00` on a 40-message history.

**AN ARRIVING MESSAGE CHIMES AND PREVIEWS, UNLESS IT IS ALREADY ON SCREEN (2026-08-06).**
`DirectView.Alerts`, supplied by `EyeAndSickleClient.deckAlerts()`.

- ⚠ **THE SUPPRESSION NEEDS BOTH HALVES: COMS FOCUSED *AND* THAT CONVERSATION OPEN.** Either alone
  gets a case backwards — COMS focused on a *different* conversation is exactly when a preview is
  most useful, and the right conversation open *behind another window* is a message the player cannot
  see.
- ⚠ **ONE CHIME PER POLL, NOT PER CONVERSATION.** A poll can report several at once (the first after
  an absence usually does) and a chime each is a burst of identical sounds. Previews still stack.
- ⚠ **NOTHING IS ANNOUNCED FOR THE PLAYER'S OWN MESSAGES.** `logCreateMessage` fires for every message
  in a conversation the account is in — that is what keeps this client in step with their phone — so
  a reply typed elsewhere arrives here as a change. `Convo.lastSenderDid` exists solely for this;
  chiming for it would be the app notifying somebody about themselves.
- ⚠ **`Alerts` is an interface, not a `DeckShell` handle.** This view has never known what a deck is,
  and the window manager has never known what a conversation is — the rule needs both, so it lives at
  the one place that has them. `Alerts.NONE` is what a pane built without a deck gets.
- ⚠ **The notice is NOT severe.** §2.1 rations alarm to loss and hostile state; a message from a
  friend is neither, and shouting would spend the stack's whole alarm budget on somebody saying hello.
- ⚠ **`snippet` flattens whitespace and cuts on a WORD boundary** — a notice is one line, and a
  message pasted with breaks would make the stack jump in height. ⚠ Its test asserts the *original*
  has a space at the cut point; asserting the preview "does not end mid-word" is trivially true of
  every cut and would have passed against a blind substring.

- ⚠ **THIS IS THE ONLY THING THE CLIENT WRITES TO SOMEBODY ELSE'S SERVICE.** Everything else in
  `docs/client/02` §2.9a's exhaustive outbound list reads. **Nothing of the game's goes with it** —
  no handle, DID, balance, standing, item, machine name or address; the request is a convo id and the
  text the player typed, which is what keeps `00` §7 true rather than merely narrow.
- ⚠ **`maxLength` IS UTF-8 BYTES AND `maxGraphemes` IS GRAPHEME CLUSTERS — neither is
  `String.length()`.** The lexicon caps text at 10000/1000. Checking `length()` passes a message the
  server then rejects: family emoji are a handful of graphemes and a great many chars, accented Latin
  is fewer bytes than it looks. `withinLimits` uses `BreakIterator.getCharacterInstance`, refusing
  before the round trip rather than after an unexplained 400.
- ⚠ **THE COMPOSER LIVES OUTSIDE THE SCROLLING TRANSCRIPT, and that is structural.** The poll rebuilds
  the transcript whenever the open conversation changes, and a `TextField` in a container repainted on
  a clock is destroyed **mid-keystroke** — **UI-7**, which `ReconView` records from having shipped it.
  A sibling node cannot be taken away by the refresh path by accident.
- ⚠ **The field is cleared on SUCCESS and only on success.** A failed send must leave what the player
  wrote where it is; clearing on the attempt loses somebody's words to a network error they did not
  cause. The **server's** returned `messageView` is what gets rendered, never the typed string — the
  id and timestamp are the real ones and it costs no second round trip.
- ⚠ **A BUBBLE MUST BE WRAPPED IN AN `HBox` OR IT IS A FULL-WIDTH BAND.** A `VBox` stretches children
  to its width, so a bubble added straight to the transcript reads as a section background rather
  than a message however its alignment is set. `setFillHeight(false)` on the row too — the rig
  monitor's core cutaway records the same trap.
- ⚠ **`-es-amber` HERE AMENDS §2.1, and it is fenced as `ui-design-language.md` §2.1b.** The accent is
  reserved for cycles doing work and income; a message is neither. What makes it defensible is that
  it is **deixis, not a category** — it means "this one is yours", says nothing about value or state,
  and is meaningless outside a conversation. **Only one side is marked**; two coloured bubbles would
  be the semantic colour system §2.1 bans. Alignment is the primary cue and the sender's name is on
  every bubble, so §4.4 holds under greyscale and a screen reader.
- ⚠ **A BUBBLE IS A NEW GROUND, AND EVERY OTHER CONTRAST CHECK MEASURES AGAINST THE PANEL.**
  `-es-amber` is a bright sodium on six palettes and a burnt brown on two, so one hard-coded text
  colour would be illegible on half of them whichever it was — the `DiskLamp` trap.
  `-es-bubble-mine-text` and `-es-bubble-them` are per palette and
  `ContrastTest.chatBubblesAreLegible` measures both, compositing the neutral bubble over the panel
  (it is deliberately **translucent** on the two glass themes so it tints the frost instead of
  punching an opaque box through the window). ⚠ It also asserts the neutral bubble is **distinguishable
  from the panel** — one that matched the window body would leave the fill doing nothing while the
  text stayed perfectly legible, which no text-contrast check can see.
- ⚠ **Two-class selectors, or the fill silently loses.** `theme.css` sets `.label { -fx-text-fill:
  -es-text; }`, so `.es-dm-mine .label` (0,2,0) is what actually paints. Sizes live in rules that set
  no fill, so the two concerns cannot fight on ordering.
- ⚠ **§9 unamended: square by default, rounded only under `.es-rounded`.** A chat bubble is the one
  shape here a reader expects to be round, which is exactly why it obeys the player's setting rather
  than taking an exemption. Verified by **sampling the corner pixel** of a rendered bubble: bubble
  colour when square, panel colour when rounded.
- ⚠ **`scrollToEnd` must `applyCss()` + `layout()` BEFORE `setVvalue(1.0)`.** A `ScrollPane` clamps
  against a content height it does not know until the new bubbles are measured, so setting it in the
  same frame scrolls to the end of the *old* content. `AttentionLedger` records the same fix.
- ⚠ **`DirectSnapshot` renders the transcript on a PANEL, not the desk, and the first version got that
  wrong.** `es-scene-ground` paints `-es-void`; the real transcript sits in a window body. On the dark
  palettes the two are a shade apart and the mistake is invisible — on uOS Classic they are `#A8A8A8`
  and `#E4E4E4`, so the neutral bubble was being judged against a ground it never sits on.
  ⚠ **Verify renders by SAMPLING PIXELS, not by looking at the preview** — the preview misrepresented
  a `#E4E4E4` light-theme ground as dark, twice, and the pixel data settled it in one command.

**THE SYNC MARK MOVED OFF `Pulse` ONTO ITS OWN 30 FPS CLOCK (2026-08-06).** `UiTokens.SPIN_MS`.

- ⚠ **`Pulse` QUANTISES EVERY SUBSCRIPTION TO A MULTIPLE OF ITS 100 ms DRIVER**, silently:
  `Math.max(TICK_MS, round(periodMs / TICK_MS) * TICK_MS)`. So `SyncSpin`'s `animate(60, …)` was
  **100 ms — 10 fps**, and a hand-tuned table stepped a third as often as the number beside it said.
  Nothing reports it. Same trap `Frost` records for asking Pulse for 24 fps.
- ⚠ **The fix is NOT to lower Pulse's driver** — that speeds up every decorative widget in the client
  to smooth one mark. A `Timeline` with an **action-only `KeyFrame`** interpolates nothing, so §5's
  easing ban is not in play and neither contract test fires (they scan for `Interpolator.EASE/SPLINE`,
  `Interpolator.LINEAR` and `AnimationTimer`): this is a sampling rate, not a tween. `Frost`'s
  precedent exactly.
- ⚠ **30 rather than the frost's 24, because this one is ROTATION** — a turning shape at 24 fps beats
  against the eye's motion tracking, and the cost is one `setRotate` per tick.
- ⚠ **Smoothness comes from a FINER LADDER, never interpolation** — 20 entries became 51, authored
  once and pasted as **data**. The table's size fence rose 40 → 80, which is what keeps "finer" from
  becoming "generated".
- ⚠ **A TURN THAT HAS BEGUN ALWAYS RUNS TO THE END OF THE TABLE**, on explicit direction. A `getLog`
  poll can return in a couple of hundred milliseconds, and snapping home on completion rendered a
  **twitch** whose length was a function of somebody else's latency — the same event looking different
  every time. ⚠ **This reverses a rule this file used to hold** ("motion after the thing it reports has
  stopped is the one lie a progress indicator can tell"): the mark can now turn for up to ~1.7 s after
  the sync ends. An indicator that is legible slightly too long beats one that is illegible every time.
- ⚠ **Reduce motion is the ONE case that still stops mid-turn**, and it had to become explicit — on
  `Pulse.animate` a decorative subscription simply never fires there, so the mark held still without
  this widget knowing why. Asked **every tick**, because the setting can be turned on mid-sync.
- ⚠ **`advance(step, syncing)` is pure and package-private** so the always-completes rule is testable
  without a toolkit — `DirectView.state`'s seam, for its reason.

⚠ **`bsky.social` IS NOT A PDS, AND ASSUMING IT WAS BROKE EVERY DIRECT MESSAGE (2026-08-06).**
`BlueskyChat` hard-coded `https://bsky.social` and sent the sign-in *and every later call* there.
Sign-in **succeeded** — which is what made this unreadable — and every `chat.bsky.*` call came back
**501 MethodNotImplemented**. `bsky.social` is the **entryway**: it fronts account and session
methods for every Bluesky-hosted account, holds none of them, and does not pipethrough chat. The
real host is in the account's DID document — measured: `stoicswe.com` → `did:plc:zczf6tbnu4prqmdtj2hemgqu`
→ **`https://leccinum.us-west.host.bsky.network`**. `client/bsky/PdsDirectory` resolves it.

- ⚠ **501 IS THE SIGNATURE OF AN UNROUTED METHOD, and knowing that ends the search in one probe.**
  Measured against the live services: `api.bsky.chat` answers **401** for `getLog` (method exists,
  auth missing) and **501** for a method that does not exist at all. So a 501 was never a wrong
  header, a wrong scope or a missing parameter — it meant *the server that answered had never heard
  of the method and was not forwarding it*. Only the host was wrong. ⚠ The `atproto-proxy` header
  was present and correct the whole time; **the header is only half of the routing.**
- ⚠ **BOTH SERVERS ANSWER 401 UNAUTHENTICATED, so an anonymous probe cannot tell them apart.**
  `bsky.social/xrpc/<anything>` is 401 — including a nonsense method — because auth middleware runs
  first. The discriminating probe is against **`api.bsky.chat`**, which answers before auth.
- ⚠ **RESOLUTION HAPPENS BEFORE THE PASSWORD IS SENT, and that order is the entire privacy argument.**
  The free fix is to sign in at the entryway and adopt the `didDoc` that `createSession` returns
  (verified optional in the lexicon; it is what `@atproto/api` does, and it is kept here as a second
  correction). On its own it means **a self-hosting player's app password reaches Bluesky before
  anyone discovers their account is not there.** So the host is settled first from public data —
  `com.atproto.identity.resolveHandle`, then `plc.directory` — and the credential is posted only to
  the machine meant to hold it. Two requests, **once per sign-in**, never per poll.
- ⚠ **`resolveHandle` on the entryway answers for SELF-HOSTED handles too** — it runs the full
  network resolution. Verified against `pfrazee.com`, which is not a Bluesky-hosted account. That is
  what makes one lookup enough instead of DNS plus a well-known fetch against a domain the player
  typed.
- ⚠ **The `service` array is a list of DIFFERENT services — never take `service[0]`.** A labeler and
  a feed generator sit in the same array, so the first entry works for a plain account and silently
  points the client at a labeler for anybody running one — failing identically, with the same
  unreadable 501. Matched on `#atproto_pds` / `AtprotoPersonalDataServer`.
- ⚠ **The endpoint is a CREDENTIAL DESTINATION, so it is validated rather than trusted**: HTTPS only
  (an `http://` endpoint puts the app password in clear), no userinfo (`https://real@evil/` reads as
  one host and resolves to another), trailing slash stripped (every caller appends `/xrpc/…`).
  Anything unusable **falls back to the entryway** rather than failing the sign-in.
- ⚠ **An absent `didDoc` must leave the host ALONE, not clear it.** It is optional in the lexicon, so
  blanking on absence turns a good session into a stream of malformed URLs — a worse failure than the
  one being fixed, and only against certain servers.
- ⚠ **The host is now VOLATILE and changes.** The field it replaced was `final`, which is precisely
  what made this unfixable in place: there was one host and it was decided before anyone knew whose
  account it was. `chatHost()` and `adoptDidDocument` are package-private **so the routing is
  checkable without a network** — `SecurityCenterView.latestOf`'s seam, for its reason. Negative-
  tested: with the adoption neutered, `theHostMovesToTheAccountsOwnPds` fails with
  `but was: "https://bsky.social"`.
- ⚠ **A 501 now has its own sentence** in `describeChatFailure`. The raw status points a reader at
  the wrong thing entirely, and this failure is silent in the worst way: sign-in works, the tab says
  connected, and only the messages never arrive.

**DISCORD RICH PRESENCE — the ONE thing this client tells anyone outside the machine (2026-08-05).**
`client/presence/{PresenceState,DiscordIpc,RichPresence,Transport}`, Settings → Discord,
`ClientProfile.Settings.discordPresenceEnabled`. **Off by default.**

- ⚠ **`docs/client/00` §7's "not a telemetry client" was AMENDED, not stretched**, the way §9.4 amends
  the glassmorphism ban — narrowed to what it protects (*collection*: the game gathering facts and
  sending them where the player did not choose) under four conditions that must all stay true. And
  §2.9 gained **§2.9a**, an exhaustive list of everything outbound that is not a home server: **two
  entries**, this and AnonShare's quote feed, which was the precedent. A third is a decision.
- ⚠ **THE GUARANTEE IS STRUCTURAL, NOT CARE AT THE CALL SITES.** `RichPresence.activity` takes a
  `PresenceState` and an `Instant` and is handed no session, so what it *can* transmit is the set of
  constants in `PresenceState`. A format string is one interpolation from a handle or a target
  address, with nothing on screen reporting it — and the leak goes to a friends list, not to a log.
- ⚠ **The window id is the one subject that could smuggle something**: the desk publishes
  `shell:<address>`. `forWindowId` resolves it to `TERMINAL` and **drops the address**; that exact
  case has its own test. `PresenceLeakTest` was verified against a deliberately-leaking build —
  three planted leaks, three intended assertions, then reverted.
- ⚠ **NO NEW DEPENDENCY, and that is the point.** Local IPC: a named pipe on Windows, a Unix domain
  socket elsewhere, 8-byte **little-endian** header + UTF-8 JSON — all JDK (`SocketChannel` +
  `StandardProtocolFamily.UNIX`, `RandomAccessFile`), Jackson 3 already present. A library would mean
  widening the enforcer, a jar in all five uber jars, and supply-chain surface on a repo publishing
  **unsigned** executables, for 200 bytes down a pipe.
- ⚠ **Little-endian fails SILENTLY when wrong** — the connection opens, the handshake is accepted, and
  Discord waits for a 16-million-byte payload. Asserted byte by byte, never through the reader (which
  would agree with its own mistake). ⚠ **Every reply is drained** or the pipe buffer fills and the
  next write blocks forever. ⚠ The frame length is **bounded**: another process chooses it and it is
  fed to `new byte[n]`. ⚠ Candidate paths are **composed from env vars, never a directory listing** —
  enumerating `$TMPDIR` reads every other program's IPC endpoint names, which §2.9 forbids.
- ⚠ **ONE update per 15s, COALESCED — Discord's limit.** Latest-wanted, never a queue (a queue replays
  a stale sequence minutes behind the player). That is what makes reporting a window *closing* as
  `DECK` safe: the focus event that follows overwrites it before anything is sent.
- ⚠ **Two clocks, the documented inversion of the session-clock rule.** Pacing is `System.nanoTime`
  (how long *this machine* took — `Frost`'s reasoning); the elapsed stamp is `Instant.now()`, because
  Discord renders it as real time to another person. Neither is a game deadline.
- ⚠ **Off, and shutdown, CLEAR the activity** rather than stopping updates — a presence frozen on "At
  a terminal" after quitting is the feature still talking about them.
- ⚠ **A BLANK APPLICATION ID IS A SUPPORTED STATE.** The id is public, not a credential, but it
  belongs to whoever registered the app — a fork has none. Blank disables with one `FINE` line and the
  Settings switch is **disabled and says which of three conditions applies** (no id / off / on but
  Discord absent). Arrives via `build.properties` (`<discord.app.id>` in `client/pom.xml`);
  `-Deyeandsickle.discord.appId=` overrides at run time.
- ⚠ **`applicationId()` IS AN INSTANCE METHOD WITH A TEST SEAM, AND IT WAS STATIC UNTIL A REAL ID WAS
  SET.** As a static reading only the property and the resource, a test could say "no id" only by
  **clearing the system property** — which works exactly while `<discord.app.id>` is empty and breaks
  the moment somebody fills it in. `noApplicationIdMeansOff` duly failed on the first build that
  configured one. ⚠ **Worse than the red build**: with the built-in id satisfying the check, that test
  started the worker holding the **real** connector and would have opened a pipe to the developer's
  own Discord — the side effect `DiscordIpcTest` refuses by never calling `connect` at all. Now
  `useApplicationId("")` says it explicitly, `@BeforeEach` installs a recording transport for **every**
  test in the file, and a partner test asserts a configured id *does* start (a refusal test with no
  partner passes just as well when the thing is unreachable for an unrelated reason).
  ⚠ **A test that is green because a build property happens to be unconfigured is testing the build.**
- ⚠ **The vocabulary caption is GENERATED from the enum** — a typed copy becomes a false statement
  about what is transmitted, on the caption a player reads to decide whether to consent. ⚠ Joining
  sixteen states with separators gave four lines of run-on prose: correct, wrapped, and the wrong
  shape for something whose job is to be *audited*. One per line. **Found by rendering** —
  `DeckSnapshot` gained **`-Ddeck.settingsPage=`**, since the panel opens on its first category and
  every other page was unrenderable.
- ⚠ **The presence strings are NOT translated**: the reader is the friends list, not the player, and
  the player's own locale says nothing about what those people read.

**The client has an event bus, and it is CloudEvents v1.0.2 (2026-07-29)** — `client/.../events/`,
over Spring's `SimpleApplicationEventMulticaster`. The LOG window gained an **EVENTS** tab beside
**OVERVIEW**, which is its previous content unchanged.

- ⚠ **`client/pom.xml`'s Spring ban is now an ALLOWLIST of six artifacts**, not a blanket refusal:
  `spring-context` plus the five jars it cannot resolve without. **I14 is untouched** — an in-process
  multicaster makes nothing authoritative and reaches no network, while `spring-web`, the jdbc layers
  and the server module are still refused. Negative-tested: adding `spring-web` fails the build.
  **No `ApplicationContext`** — a context can add a listener after refresh and offers no public way to
  **remove** one, and every panel here is created and destroyed as windows open and close.
- ⚠ **The spec is enforced in the compact constructor, with the section cited on every rule.** `id` is
  a generated **UUID** and `time` is filled by the builder: §3.1.1 requires `source` + `id` to be
  unique per distinct event, and a hand-written id breaks that first. An extension name that breaks
  §4.1 is **rejected, not lowercased** — coercing `retryCount` means the key read back is not the key
  written.
- ⚠ **`time` is `Instant.now()`, NOT the session clock** — the one place in this codebase that inverts
  that rule. An event log records when the *process* observed something; a test clock would file a
  developer's afternoon under the wrong year. Nothing here is a deadline.
- ⚠ **Publication is at chokepoints so coverage cannot drift** — `changed()` for successes,
  `announce()` for **refusals** (a success-only stream describes a game where nothing was refused),
  `DeskManager` for windows. The subject is the **calling method read off the stack**, which is what
  makes it cost nothing at forty call sites.
- ⚠ **Background events are DIFFED across the tick, not emitted by the rules.** `solo` has no broker
  and must not gain one, so `LocalGameSession.tick()` compares running-task ids and chain height
  before/after and publishes the difference. A multi-block settle is **one** event carrying the count;
  a quiet tick publishes **nothing**.
- ⚠ **The recorder subscribes in the `EventBus` CONSTRUCTOR**, so "all events are logged" is
  structural — an event published before the LOG window ever opened is still there. Bounded ring
  (2000), reports its drops, never persisted.
- ⚠ **Spring propagates a listener's exception to the publisher unless an error handler is set.** That
  default would let a panel's failed repaint unwind a **purchase** with the coin already spent. The
  handler catches it and publishes the failure as an event — recorded, not printed, because a packaged
  client has no console behind it.
- ⚠ **Snapshotting two tabs needs a fresh Scene per tab.** `Scene.snapshot` renders what the scene last
  laid out, so toggling `setVisible` between synchronous snapshots yields two identical images.
- **UI-7 is not closed by this.** Nothing was migrated off a `Pulse`; the requirement was that
  behaviour not change. `docs/design/15-open-questions.md` UI-7 records what is left.

**The command strip has a drive activity lamp (2026-07-29)** — left of the prompt, where a machine's LED sits. ⚠ **Every flash is a file actually written.** `DiskActivity.wrote()` is called at the two chokepoints that do the writing — `ClientProfile.save` and `LocalGameSession.persist` — **after** the bytes land, not at the call sites: a settings change, an avatar, a window move and the 30s autosave all reach those two by different routes, and instrumenting callers means a new route silently stops lighting it. `RemoteGameSession.persist` deliberately does **not** light it — the server owns that state and nothing touches the player's disk.

- ⚠ **A counter, not a timestamp.** The lamp asks "anything since I last looked", which needs no clock — so the `Instant.now()`-versus-session-clock trap cannot apply. Counts are compared, never consumed, so a second reader can't eat the signal.
- ⚠ **A 2-second stutter from a FIXED pattern, not `Math.random()`** — testable, reproducible across players, and *shapeable*: `FLICKER` is dense at the head and sparse at the tail so a write reads as a burst that settles. A write mid-burst resets `phase` as well as the countdown, which is what makes the pattern's leading `1` a real guarantee. The state machine is a `record` outside the widget because a 2-second flicker is invisible to a screenshot — `DiskLampTest` asserts it tick by tick.
- ⚠ **A `Circle` and the shared `Pulse`** — not `-fx-background-radius` (§9, build-blocking) and not a `Timeline` of its own (§7.3, and `UiContractTest` rations `AnimationTimer`/`LINEAR` by filename). Subscribed via `Pulse.every`, i.e. **data**, so Reduce motion keeps it: suppressing it would remove the only evidence the game touched the disk.
- ⚠ **Colours are `-es-dim-3` → `-es-text-hi`, never a literal white.** uOS Classic runs the ramp the other way, so a white lamp would be invisible lit and a dark one permanently on. The token pair inverts correctly: faint-grey → near-white on the dark palettes, faint-grey → black on Classic.

**Settings → Credits (2026-07-29)** — `view/Credits`, beneath About and out of the fiction entirely. Folding real names into a spec sheet that also lists an invented kernel version is the one context where a person's name reads as set dressing.

⚠ **Portraits are looked up, never required.** Each entry loads `ui/credits/<slug>.png` and falls back to initials in a dashed ring, so a photo is added by **dropping a file in** — no code change. The dash is deliberate, same as `MainMenuView`'s empty slot: a placeholder that looks finished never gets replaced.

⚠ **The Bluesky and YouTube marks are paths THIS repo authored, not the official logos.** The client bundles no third-party artwork and downloads nothing at run time; they exist so a reader knows which network a handle is on. Swapping in official assets replaces two constants. The YouTube plate needs `FillRule.EVEN_ODD` — under the default the triangle fills and it becomes a lozenge. §9's radius ban is not in play: that governs the interface's own geometry, and this is a quoted mark drawn as a path. ⚠ Handles are **printed, not clickable** — opening a browser would throw the player out of a full-screen game, and this client has never opened one. The network name is spoken only in `accessibleText`, since a screen reader cannot see a butterfly.

⚠ **`Views.scrollable` now has a `fillHeight` overload, and Settings needs it.** A `ScrollPane` hands its content the content's **preferred** height, so every `Vgrow` inside was measured against a box that had already stopped — the visible symptom was not the pages but the sidebar's divider ending halfway down the window, which reads as the panel having ended. `setFitToHeight` on both the outer wrapper and the detail pane fixes it, and never shrinks anything: a category taller than the window still scrolls. It is **off by default** because stretching is only right when something inside wants the room.

⚠ **`RigTab.isTable()` exists because `!isOverview()` used to mean "draws the table".** A third kind of tab made that silently false — ABOUT would have rendered the process table under the mascot. Ask what a tab *is*, not what it is not.

⚠ **`calc` is the one tool window that takes no `GameSession`,** and keeping it that way is the point.
It spends nothing, is gated by nothing and cannot be lost, so adding it required checking no invariant —
I14 is about state a cheater would forge, and the answer to `0xFF + 1` is not the server's opinion. It
earns its place on pillar C6: `docs/education/01-foundations.md` is a whole domain about bases, bit
width, two's complement, byte order and overflow, and every *other* window hands the player numbers in
the machine's notation without any surface that makes them legible. `client/ui/calc/` is the engine and
is pure — the shell's `calc(1)` drives the same one, so the two cannot come to different answers.

**Three screens come before the deck, and they are three different fictions (2026-07-28).**
`ui/PowerOn` is the rig's **firmware** — a glowing ring with `u` and `S` fading in beside it as a bar
fills, white on black, once per *process*. `view/MainMenuView` is the **login screen** — macOS's user
picker (round faces, name under each) over GDM's furniture (`docs/design/ui-design-language.md` §3.1).
`view/SetupWizardView` is the **setup assistant**, five questions on the way to a new character (§3.2).
Then `ui/BootSequence` — **uOS** booting, printing that save's real state — and then the deck.

⚠ **The splash ignores the palette on purpose.** `.es-poweron` declares its own white and black
rather than resolving `-es-` tokens, so the five overlays have nothing to override: firmware runs
before anything knows who the player is. ⚠ Its glow is **eight overlapping concentric strokes**
(`ui/GlowRing`), not an effect — §9's ban on drop shadows and blur still stands, and evenly-spaced
strokes render as concentric circles rather than as a falloff. ⚠ `GlowRing`'s colours are declared
under `.es-poweron` and **resolve nowhere else**; the assistant restates them in the palette's terms.

⚠ **APPEARANCE IS PER CHARACTER (2026-07-28).** `profile/VisualSettings` holds palette, pointer skin,
wallpaper, casing, the three screen artefacts, curvature, rounded corners and subwindow control
order — one per solo slot, in `Settings.characterAppearance` keyed by slot number. `Settings.appearance`
is the machine's own look: the splash, the login screen, and the seed for a new character.
`profile.appearance()` returns whichever is in force; **never cache it.**

⚠ **Three things stay machine-wide, each for its own reason.** `uiScalePercent` and
`reducedMotionOverride` are accessibility **floors** (`docs/client/07`) — per-character would give a
player who needs 150% text 100% on every new character. `nativeWindowBorder` cannot be per-character
at all: `Stage.initStyle` is rejected on a realised Stage. `windowSize`/`fullScreen` are geometry, and
per-character would resize the player's window on every save switch.

⚠ **`ThemeManager` caches the current `ThemeId` and paints from the cache**, so pointing the profile at
a different look changes nothing by itself and `applyAll()` re-applies the PREVIOUS character's
palette. Call **`themes.reloadAppearance()`** after every swap; `VisualSettingsTest` asserts that.

⚠ **Migration is setter-only `@JsonProperty` hooks.** The mapper has `FAIL_ON_UNKNOWN_PROPERTIES` off,
so without them every pre-split `settings.json` would have loaded with its ten appearance keys
silently dropped — a player launching into a theme they never chose, with no error anywhere.

⚠ **Only the handle and the picture belong to the SAVE.** The assistant previews appearance on a
**detached** `VisualSettings` that becomes the character's only when it is created — which is what
makes Cancel free rather than something to unwind. It still writes four machine-wide settings
(hostname, teaching, text size, motion), and `SettingsSnapshot` restores those on Cancel. The picture
cannot be applied where it is chosen (no save exists yet), so it rides out and lands via
`session.setAvatar` immediately after `startSolo`. **CL-4 / T-2 is answered here now**, not in an
`Alert` on the login screen.

⚠ **Continuous motion is rationed by FILENAME.** A `Timeline` + `KeyValue` interpolates with
`Interpolator.LINEAR` **by default**, so a fade could be added anywhere without the word appearing in
the source — passing §10 criterion 7 by never tripping it. `UiContractTest` therefore asserts
`AnimationTimer` appears in exactly `Fade.java` and `PowerOn.java` (§5.1). ⚠ And both fade their
**content**, never the scene root: the root paints the ground and the Stage is `TRANSPARENT`, so
ramping it shows the window through itself.

**The deck is the client, as of 2026-07-26.** One `StageStyle.UNDECORATED` Stage — no OS chrome on any
platform — laid out as `docs/design/ui-design-language.md` §3 specifies: top status strip, 34px rail,
desk, command strip. Inside it, `ui/chrome/DeskManager` is a window manager the client draws itself:
drag, focus, z-order, minimise, maximise, close, resize, **snap-to-grid and edge tiling** (drag a panel
against a side of the desk to fill that half, into a corner for that quarter), with free-drag as a
setting. This replaces *both* previous layouts — the `Stage`-per-tool desk and the docked tabbed shell —
and cancels **AtlantaFX** with them; see `docs/architecture/01` §1 and `docs/design/15` §3. Pillar C2 is
now structural rather than maintained by hand: the compute readout is a cell in the top strip, which is
chrome, so it has no z-order to lose and no tab to hide behind.

The rig monitor doubles as an **activity monitor**: running work with a discrete cell meter and a
countdown. Scans are real tasks now — `docs/design/04-mining.md` §3.2 has always published a duration
per tier and nothing waited for it until 2026-07-26. They persist, survive a quit, and complete on the
first tick back. The **pointer** is drawn by the game too (Settings → Pointer; system is the default and
that is an accessibility floor, not a placeholder).

⚠ **Seven JavaFX behaviours here cost a debugging round each and are easy to hit again.** (1) A
**managed** child of a `Pane` is repositioned by the Pane's `layoutChildren`, silently undoing
`resizeRelocate` — every desk window is `setManaged(false)`, and without it the window manager works
until the next layout pass and then stacks every panel at the origin. (2) In an **event filter**,
`MouseEvent.getX()` is relative to the event's *target*, not to the node the filter is on — resize grips
must convert with `sceneToLocal`, or they work on a bare panel and stop wherever a tool put content.
(3) `-fx-shape` scales an SVG path to the region, so the 18px notch has to be a `Polygon` clip
recomputed per resize (§7.2). (4) **`-fx-cursor: url(...)` does not work** — it fails at apply time with
`ClassCastException: String incompatible with Cursor`, so custom cursors must be set from Java.
(5) **A CSS `-fx-cursor` on a node beats an inherited Scene cursor**, so a single `-fx-cursor: hand`
in the stylesheet punches a system-cursor hole in every custom skin. (6) **`theme.css` has a
late `.label { -fx-text-fill: -es-text; }`, and a one-class selector cannot beat it** — equal
specificity means the later rule wins, so a new `.es-thing` that sets a text fill silently paints in
body-text grey while every *other* property in the same block applies normally. That split is what
makes it hard to see; use a two-class selector (`.es-parent > .es-thing`). Measured on the wallpaper,
which came out four times too bright. (7) **A width/height listener on the deck fires before the
`BorderPane` has laid out its centre**, so `desk.getWidth()` is still the previous value inside
`DeskManager.reflow()`. Windows survive it because they only clamp against the desk; anything sized
*from* it does not, and the desk wallpaper stayed 0×0 forever while every widget-level check passed.
Size such a node from `desk.widthProperty()` directly. All seven are covered by tests or by a probe.

⚠ **Every character the client draws must be in a bundled font, and textures go on IBM Plex.**
Martian Mono maps ~638 codepoints against Plex's ~1049 and has **none** of the block-element or
box-drawing range (U+2500–U+259F). A texture styled with Martian silently falls back to a host font —
different shapes *and* different advance widths per platform, which breaks character-cell layout.
Eleven codepoints were wrong this way once, including the window maximise control and the Shortcut
key hint. `GlyphCoverageTest` parses the TTF cmaps and fails the build on any uncovered literal;
`Font.loadFont` cannot tell you this, and JavaFX has no per-codepoint coverage query.

⚠ **Anything with a deadline must take the session's clock, never `Instant.now()`.** `RunningTask`
got this wrong once and every task reported 100% complete the moment it started under a test clock —
invisible in production, where the two clocks agree. `ComputeRules.spend` has the same warning one
module down. Related: **work that can finish while the game is closed settles in `GameEngine.resume()`,
not in `tick()`** — `resume()` sets `lastTick = now`, so the first tick sees zero elapsed time and
returns early.

Escape opens an in-deck **pause menu** (save, settings, quit to menu, quit game with a confirm) rather
than dropping straight back to the main menu. The profile (settings, window geometry, save) lives in the
platform's conventional directory — `~/Library/Application Support/The Eye and Sickle` on macOS,
`%APPDATA%` on Windows, `$XDG_DATA_HOME` on Linux — and `-Deyeandsickle.profile=<dir>` relocates it.

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

⚠ **Any `javafx-maven-plugin` `<option>` whose value contains a SPACE must be quoted inside the element.** The plugin hands options to commons-exec, which tokenises on whitespace, so `-Xdock:name=EAS uOS Client` arrived as three arguments and `uOS` was taken as the main class:

```
Error: Could not find or load main class uOS
```

`mvn -pl client javafx:run` — the launch this file documents — failed on **every** run from the moment the app was renamed until 2026-07-29, and the message names neither the option nor the plugin. `LauncherTest.dockFlagIsWiredUp` passed the whole time because it asserted the substring rather than the working form; it now pins the quotes.

**`mvn clean install` builds a runnable jar for every platform**, in the `client-dist` module: `client-dist/target/eyeandsickle-client-dist-<version>-{win,mac,mac-aarch64,linux,linux-aarch64}.jar`, each run with `java -jar`. All five come off one machine because nothing is compiled per platform — only a different set of prebuilt JavaFX natives is packaged. Needs a JDK/JRE 25+ on the target; it does **not** bundle a runtime. `-Ddist.skip=true` skips the five uber jars when you want a fast build.

⚠ **`client-dist` is a separate module on purpose — do not fold it into `client`.** Shade can only filter the dependencies of the project it runs in, so five jars means declaring all five JavaFX platform sets as dependencies. In `client` that would put every platform's natives on the **test and `javafx:run` classpath**, and JavaFX resolves a native by OS-specific *file name* — so on an Apple Silicon Mac the x86_64 `mac` jar's `libglass.dylib` can shadow the arm64 one purely by classpath order, silently, until something starts the toolkit. `client-dist` has no tests and nothing to run, so the same dependencies are harmless there.

⚠ **There is deliberately no single all-platform jar, and it was built and measured before being rejected.** JavaFX puts natives at the jar root under names carrying the OS but *not* the architecture — `libglass.dylib` is the name on both Intel and Apple Silicon, `libglass.so` on both x64 and ARM Linux — so merging all five classifier jars has the two Mac builds silently overwrite each other (shade sees identical paths, keeps the last). The merged jar really did contain one x86_64 `libglass.dylib` and died on the arm64 machine that built it: `UnsatisfiedLinkError ... (have 'x86_64', need 'arm64')`. A true single jar needs the launcher to extract arch-scoped natives and point JavaFX at them via `javafx.cachedir` (layout `<dir>/<runtime version>/<os.arch>/`) — undocumented internals, silently wrong again if they move.

**`mvn -Pnative` bundles a Java runtime — for the HOST PLATFORM ONLY** (`client` module, `client/target/jpackage/`):

```bash
mvn -Pnative -pl client -am package -DskipTests
```

gives `EAS uOS Client.app` on macOS, `EAS uOS Client\` on Windows/Linux; adding `-Djpackage.installer=true` gives a `.dmg` / `.msi` / `.deb` instead. Nothing has to be told which platform it is on: JavaFX natives come from the classifier-less dependencies (whose own poms carry os profiles), the runtime is jlinked from the JDK running Maven, and the installer type + OS-specific options come from three `<os>`-activated profiles. This complements `-Pdist`/`client-dist` rather than replacing it — jpackage **cannot cross-compile** (a Windows launcher must be built on Windows), so all-five-platforms still means the runtime-less uber jars.

⚠ **jpackage runs in NON-MODULAR mode (`app-image` + `input` + `main-jar`), and it must stay that way.** `jlink` alone cannot link this graph — `io.github.erdtman:java-json-canonicalization` is a plain Java 8 jar that can only be an automatic module, which jlink refuses. Non-modular jpackage sidesteps it by jlinking a runtime from the JDK's own modules and putting our classpath jar beside it. Cost: image size (~135 MB on macOS). Trimming it needs full JPMS, which is still open.

⚠ **Four jpackage traps, each of which cost a build here.** (1) **OS-specific options are rejected, not ignored** — `--win-menu` on a Mac is `Option [--win-menu] is not valid on this platform`, a hard failure, so one shared config listing `winMenu` + `linuxShortcut` + `macPackageIdentifier` builds *nowhere*; they live in `<os>`-activated profiles. (2) The Linux profile activates on **`os.name=Linux`, not family `unix`** — macOS matches `unix` too, and Maven's family vocabulary has no `linux`. (3) **`--app-version` cannot be `${revision}`**: `0.1.0-SNAPSHOT` fails validation outright and even a clean `0.1.0` fails on macOS ("the first number cannot be zero", it becomes CFBundleVersion) — hence the separate `jpackage.appVersion`. (4) `--input` gets a **dedicated directory holding exactly one uber jar**; jpackage copies *everything* under it into the app and classpaths every jar it finds, so pointing it at `target/` ships `classes/` and `maven-status/` inside the application.

Keep this file's stack summary, invariant list, and layout in sync with reality as the code grows.
