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

`.github/workflows/build.yml` builds and tests every push and PR. **Pushing a `v*` tag additionally publishes a GitHub Release** carrying the five client jars, the server's `-boot` jar, and a `SHA256SUMS` covering all of them:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

Files are renamed from the POM version to the tag (`the-eye-and-sickle-0.2.0-mac-aarch64.jar`), so the tag never has to agree with a `pom.xml` nobody remembered to bump. A tag containing a hyphen (`v1.0.0-rc1`) is published as a prerelease.

- **The release is cut with `gh`, not a third-party action.** It is preinstalled on the runner, so a repo that publishes executables adds no supply-chain surface to do it. `permissions:` is `contents: read` for the workflow and widened to `contents: write` on the release job alone.
- ⚠ **CI re-verifies each client jar's architecture** by running `file` on its `glass` native. JavaFX names natives for the OS but not the arch, so a packaging mistake produces five jars that all look right and half of which cannot start — the check exists because that exact bug has already happened here once. It is negative-tested: planting an x86_64 jar as `mac-aarch64` fails the job.
- The `actions/*` steps are pinned to major tags; **pinning them to commit SHAs is the remaining hardening step** and is worth doing before the repo has anything to steal.

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

**Shell sessions (2026-07-28).** Right-click a machine on the map → *Open a shell*, and a terminal
window opens on it: `ls`, `cd`, `cat`, `stat`, `find`, `df`, `get` and the rest, with a right-click
menu that templates any command's options and previews the line before writing it into the input.
Many at once, one window per machine (`shell:<address>` — not a `WindowSpec`; see `docs/client/05`
§2.1 for why that is not the WL-8 duplication).

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

**`mvn clean install` builds a runnable jar for every platform**, in the `client-dist` module: `client-dist/target/eyeandsickle-client-dist-<version>-{win,mac,mac-aarch64,linux,linux-aarch64}.jar`, each run with `java -jar`. All five come off one machine because nothing is compiled per platform — only a different set of prebuilt JavaFX natives is packaged. Needs a JDK/JRE 25+ on the target; it does **not** bundle a runtime. `-Ddist.skip=true` skips the five uber jars when you want a fast build.

⚠ **`client-dist` is a separate module on purpose — do not fold it into `client`.** Shade can only filter the dependencies of the project it runs in, so five jars means declaring all five JavaFX platform sets as dependencies. In `client` that would put every platform's natives on the **test and `javafx:run` classpath**, and JavaFX resolves a native by OS-specific *file name* — so on an Apple Silicon Mac the x86_64 `mac` jar's `libglass.dylib` can shadow the arm64 one purely by classpath order, silently, until something starts the toolkit. `client-dist` has no tests and nothing to run, so the same dependencies are harmless there.

⚠ **There is deliberately no single all-platform jar, and it was built and measured before being rejected.** JavaFX puts natives at the jar root under names carrying the OS but *not* the architecture — `libglass.dylib` is the name on both Intel and Apple Silicon, `libglass.so` on both x64 and ARM Linux — so merging all five classifier jars has the two Mac builds silently overwrite each other (shade sees identical paths, keeps the last). The merged jar really did contain one x86_64 `libglass.dylib` and died on the arm64 machine that built it: `UnsatisfiedLinkError ... (have 'x86_64', need 'arm64')`. A true single jar needs the launcher to extract arch-scoped natives and point JavaFX at them via `javafx.cachedir` (layout `<dir>/<runtime version>/<os.arch>/`) — undocumented internals, silently wrong again if they move.

**Self-contained installers (jlink/jpackage) are still not wired up** — `jlink` cannot link the current graph (an automatic module in the dependency tree), and `jpackage` **cannot cross-compile**: a Windows image must be built on Windows, so three platforms would need three machines. See the closing comment in `client/pom.xml` before attempting it.

Keep this file's stack summary, invariant list, and layout in sync with reality as the code grows.
