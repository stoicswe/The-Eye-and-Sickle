# CLAUDE.md — The Eye and Sickle

Guidance for Claude Code (and humans) working in this repo. Read this first, every session.

---

## What this project is

A **puzzle-centric hacking game** in a surveillance-dystopia setting. Two factions — **The Eye** (the surveillance state) and **The Sickle** (a decentralized resistance). Single-player by default; opt-in, real-loss multiplayer over a **federated, self-hostable** server network. The core loop is a hacking minigame; every surrounding system exists to give that puzzle stakes and consequence.

**Tech stack (decided, end-to-end):** JavaFX desktop client — **one undecorated window containing an in-game window manager the client draws itself** · Spring Boot + PostgreSQL self-hostable home servers (Docker Compose) · AT Protocol OAuth for identity (authentication only) · opt-in federation with a reputation-weighted validator quorum and cryptographically signed per-item provenance.

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

1. **I1** — Compute is never purchasable with ethecoin.
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
- **`mvn verify` must never require Docker.** Container-backed tests live behind `-Pit`, so a client-only contributor is never blocked.
- **Enforcer rules are load-bearing, not decoration.** The client's ban on Spring/server is Invariant I14 made mechanical. Verified to actually fire.
- **Boot 4 split `spring-boot-autoconfigure` into per-technology modules.** Depending on a raw library (e.g. `flyway-core`) instead of its starter gives you the classes without the auto-configuration: green build, dead config, feature silently absent. If you add a Boot integration, use its **starter**.
- **Transport security is `[PROPOSAL]` and needs a cryptographer.** `docs/architecture/07-transport-security.md` §6 T-1. It is a hand-rolled Noise-IK-shaped protocol — reviewed patterns, unreviewed code. Do not let it guard a live federation until someone qualified has read it.
- **Timestamps bind through `persistence/Timestamps.at(Instant)`, never a bare `Instant`.** The Postgres driver refuses a raw `java.time.Instant` ("Can't infer the SQL type"); `Row.instant()` reads them back as `OffsetDateTime`, and `Timestamps` is the matching write side. Unit tests with fakes cannot catch a raw bind — only the `-Pit` repository tests do.

### Server implementation status

The **Established spine is implemented and boots** (`ServerContextLoadsIT` starts the full context against a real Postgres): schema (Flyway core + federation), JdbcClient data layer, AT-Proto-auth + allowlist, compute ledger, ethecoin/public ledger + gates, provenance persistence & ingress verification, validator quorum (A-Res sampling + AIMD reputation), peer discovery, and the `Content-Digest` checksum filter. ~168 main + ~117 test classes; `mvn verify` and `mvn -Pit verify` both green.

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
mvn -Pit verify                     # + Testcontainers integration tests (needs Docker)
mvn -Pquality spotless:apply        # format
```

The client **runs offline out of the box**: `mvn install -DskipTests && mvn -pl client javafx:run` opens a
playable solo game with no network, account or database. Twenty tool windows, five themes, a shell
with real pipelines and globs, and a 23-page offline manual parsed from `client/src/main/resources/
.../terms/`.

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

⚠ **Contrast is MEASURED, not assumed — `ui/ContrastTest` (2026-07-30).** It computes real WCAG
ratios for every text token against `-es-panel` and `-es-panel-hi` in all six palettes and fails the
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
- ⚠ **THEME is first and default**: it resolves `-es-amber`, so it follows all five palettes.
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
vanish — it lives inside **`/System`** (`solo/fs/SystemTree`), laid out as FreeBSD lays one out,
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
  how **long** an absent rig hashes; **`OFFLINE_SOLO_WIN_WEIGHT`** (0.5, 2026-07-29) caps how **well**
  it does while it is, so an hour played beats an hour away *inside* the buffered window too. ⚠ **Self-
  mining and fills only** — the live tick is untouched (leaving the client running is playing), and a
  pool competes whether or not one member is online, so weighting a pool would be this rig reaching
  into somebody else's rate. The freed probability goes to the **unpooled** remainder. ⚠ It scales the
  **threshold**, never the number of draws: one `nextDouble` per block whatever the mode, or a stored
  seed stops being a replay. ⚠ **Deliberately invisible** — no readout names it, by decision.
- ⚠ **Comparing a live run against a fill needs the SAME save loaded twice.** Two saves built
  identically are not identical: a fresh game draws its own initial `networkWorkTarget` from the
  character id, so the walks are a fraction of a block apart at the start and diverge within the hour
  — which reads exactly like a broken RNG contract. `ChainSyncTest.OfflineWeight` persists one and
  loads it twice.
- ⚠ **Two clamps must agree and only one is enforced in `SoloGame`.** `ChainRules.sync` excludes the
  player from the draw past the window, which caps solo and PPLNS. **PPS is not capped by that** — it
  runs its own share clock off `elapsed` — so `resume()` passes `walked.minedFor()`, never the absence.
  Passing the absence breaks I5 silently and *only for pay-per-share*.
- **Confirming pending transactions while away is not income.** The value moved when the ledger row was
  written; confirmation only stamps the height. A transaction unconfirmed across a four-day absence
  would be the lie, and would let a player park money in the mempool to hide it.
- ⚠ `SoloGame.sync` is **session state, never saved.** It describes one transition; persisting it
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
This **reverses** `SoloGame.debit`'s documented "the goods are immediate" decision, on explicit direction.

- ⚠ **The `.pkg` → `.upg` rename IS the lock — there is no second mechanism.** Repac already means "a
  vendor's package" vs "one this rig can install", and a bought one does not cross that line until the
  chain says the money moved. So the lock shows in `ls`, the file manager and the shell without any of
  them knowing about confirmation, and — being derived from the ledger row's `blockNumber` on every
  read — no flag anywhere can disagree with the chain.
- **First mechanical consequence a `FeeTier` has ever had.** Previously a fee bought only how soon a row
  stopped printing `—`. A higher fee buys a slot in an earlier block, never a faster chain.
- ⚠ **`SoloGame.debit` writes TWO ledger rows** — the spend (broadcast) and a separate `TX_FEE` line
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

**Port scans file persistent node reports (2026-07-29).** `solo/state/NodeReportState` per machine,
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
`SoloGame.retuneNetworkHashrate` migrates it on load. ⚠ **It rescales difficulty by the same factor in
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
(whether you deliver what you were paid for, `solo/rules/SecondaryMarket`). A Sickle hero can be a
thief; a scrupulous trader can be a validator nobody trusts. Collapsing any two deletes an axis.

⚠ **Only ETHECOIN-gated upgrades may be resold.** Selling a schematic-gated tool for ethecoin would
let anyone with enough money buy a ceiling — I2, and I8 for zero-days. Anything can still be *stolen
and used*; what is refused is turning a gated item into currency. `solo/rules/Repac.sellable`.

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

⚠ **`solo/rules/AccessLog` is a [PROPOSAL] counter-forensics loop, and it must not become a fourth
exposure surface.** A remote actor who copies something is logged with the address they came from and
may wipe that address before leaving — **blanking it, never deleting the line**, because a deleted
row turns a legible crime into a missing file. `canTake` answers from the item's **tier** (§6), so an
upgrade visible inside an app bundle is a *view* onto an item, not a way around the vault (**I12**).
Nothing writes to it in solo — there are no remote actors — and that is why it is tested rather than
demonstrated.

**The file manager (2026-07-28).** `Shortcut+Shift+H`. GNOME Files' layout over `solo/fs/VirtualFs`:
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

⚠ **`solo/fs/VirtualFs` generates every machine's filesystem and stores none of it.** Not for save
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
firmware image, so `Ethecoin` carries a **`BigInteger`** and Postgres `bigint` became
`numeric(78,0)` (`V6__ethecoin_wei.sql`).

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
- ⚠ **Save migration is gated on `SoloSave.moneySchema`, never a heuristic** ("is this balance small?"
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
transfer. `SoloGame.upgradeAt` reads the package's own metadata; the file manager renders a compare
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
its loot. Now `SoloGame.settleBreachOutcomes`, called from **`resume()` and `breachAction`**: the load
path too, or the bug is permanent for any save that already breached something. Safe to call freely —
it is idempotent *by construction* (`foothold` and `looted` are one-way flags, so there is no settled
marker to desync).

- ⚠ **The failure shape, not the fix, is the lesson.** Both pieces were correct and both suites green;
  the defect was in the join, where a unit test cannot look. `NetRulesTest` even carried a comment
  saying the caller existed — true of the design, false of the build. **A comment describing a caller
  is not evidence of one.** `FootholdAfterBreachTest` tests one level up, against `SoloGame`, which is
  the lowest level the bug is visible at; verified by neutering the fix first.
- ⚠ **Map visibility keys on `knownNodes`; port scanning keys on `host.discovered`.** Two notions of
  "found" that agree only because a sweep sets both. A fixture setting just the flag yields a host the
  map has never heard of, failing with `NoSuchElement` rather than anything that names the problem.

**Recon decides which breach puzzle you draw (2026-07-29).** The class was an even coin flip; it is
now weighted by how complete the target's port-scan report is. **Offset Cipher is the DEFAULT** — the
puzzle that needs nothing from the far side — and **Breach Protocol** is the puzzle of someone who
knows the host, so a full report draws it ~95% (`Balance.breachProtocolShare`, linear at one seventh
per finding). This is RECON's first mechanical consequence: a report used to be intelligence read by
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
module down. Related: **work that can finish while the game is closed settles in `SoloGame.resume()`,
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
