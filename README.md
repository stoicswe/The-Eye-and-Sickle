<img src="./docs/pngs/Mr._Monitor_PNG.png" width="450" height="300" />

*Mr. Monitor is © Sham Tomaselli — [shamcube](https://www.youtube.com/@ShamCube).*

# The-Eye-and-Sickle

A distributed, federated online hacking game involving the "Eye" and the "Sickle".

A puzzle-centric hacking game set in a surveillance dystopia. Play as an operator in **The Sickle**, a decentralized resistance, against **The Eye**, the surveillance state. Compute — not money — is the master scarcity, and the core hacking minigame is the game; every other system exists to give it stakes. Single-player by default, with opt-in, real-loss multiplayer over a self-hostable, federated server network.

**Stack:** JavaFX multi-window desktop client · Spring Boot + embedded H2 self-hostable servers (Docker Compose optional) · AT Protocol identity (auth-only) · federated anti-cheat via validator quorum + signed item provenance.

## Documentation

Design and architecture live in [`docs/`](docs/). Start here:

- **[`CLAUDE.md`](CLAUDE.md)** — orientation, the 15 hard design invariants, and conventions. Read first.
- **[`docs/design/README.md`](docs/design/README.md)** — game systems, economy, and world. The spine is `00` → `01` → `02` → `03`.
- **[`docs/architecture/README.md`](docs/architecture/README.md)** — the technical stack, identity, federation, and cryptographic anti-cheat model.

Docs are tagged **Established** (decided; don't change without direction) or **[PROPOSAL]** (first-pass, safe to revise — chiefly the core minigame, multiplayer, world/narrative, and data model). Open questions and their resolution log live in [`docs/design/15-open-questions.md`](docs/design/15-open-questions.md).

## Building

Requires **JDK 25 or newer** and **Maven 3.9+**. Nothing else — the default build does not need Docker.

```bash
mvn verify
```

| Module | What it is |
|---|---|
| [`protocol/`](protocol) | Wire types shared by both sides, the item-provenance verifier (detached JWS over JCS/RFC 8785, Ed25519), and the DID-authenticated encrypted transport (X25519 + HKDF-SHA256 + AES-256-GCM). No Spring, no JavaFX, no game rules, and no third-party crypto. |
| [`server/`](server) | The self-hostable home server. Spring Boot + **embedded H2** — no database to install. Authoritative for all game state. |
| [`client/`](client) | The JavaFX desktop client. One OS window per tool. Renders; never decides. |

### Running it

```bash
mvn install -DskipTests && mvn -pl client javafx:run
```

The `install` step is needed once so the client can resolve `protocol` from your local repository.

For the server, [`deploy/`](deploy) has a Docker Compose stack — copy `deploy/.env.example` to `deploy/.env`, fill it in, then `docker compose -f deploy/docker-compose.yml up`.

### Other build targets

```bash
mvn -Pit verify                     # + schema-backed integration tests (embedded H2, no Docker)
mvn -Pquality spotless:apply        # format with palantir-java-format
```

Container-backed tests are deliberately kept out of the default build so a plain `mvn verify` works on a fresh clone with no toolchain beyond a JDK.

Self-contained client packaging (jlink/jpackage) is **not wired up yet** — see the comment at the bottom of [`client/pom.xml`](client/pom.xml) for why `jlink` cannot work with the current dependency graph and what the two real options are.

## Adding a translation

The client ships English and is built to take more. A translation is **data, not code** — new files
in `client/src/main/resources/`, plus one line in an enum so the language appears in the picker.

Players choose their language in **Settings → Language**. It is machine-wide rather than per
character, for the same reason text size and Reduce motion are: a palette is a costume, but a
language is whether you can read the game.

### What is translated, and what is never translated

This is the one rule that matters, and getting it backwards would damage the thing the game exists
for.

| Translated | Never translated |
|---|---|
| What a command's option **means** | The command's name — `grep`, `sweep`, `mine` |
| What an argument **is for** | Flag names — `--thorough`, `-i`, `--fee` |
| Manual page prose | Choice values — `--fee=priority` |
| Window titles and descriptions | Window ids (they key saved desk layouts) |
| Settings labels and captions | The name of a language, in the picker |

`grep -v` is `grep -v` in every locale. The parser has no other name for it, real Unix does not
localise flags either, and pillar **C6** sells skill that transfers to a real terminal — a player who
learns `grep -v` here can use it tonight on any machine they touch. Localising a flag would take that
away from precisely the players a translation exists to serve.

Language names in the picker are the one string deliberately **identical in every locale**: it reads
`English · Deutsch · 日本語`, never `English · German · Japanese`. Someone who has landed in a
language they cannot read has to find their own on that list, and their own is the only entry they
are certain to recognise.

### The three places text lives

Say you are adding German (`de`). Everything is keyed off the IETF tag.

**1. Message bundles** — `client/src/main/resources/io/github/stoicswe/eyeandsickle/client/i18n/`

```bash
cp commands_en.properties commands_de.properties
```

Translate the values; leave every key alone. There are three bundles:

| Bundle | Holds | English lives in |
|---|---|---|
| `commands_*` | Option and argument descriptions, command-menu headings | the bundle — `commands_en.properties` |
| `windows_*` | Tool window titles and descriptions | `WindowSpec`, in code |
| `ui_*` | Every other caption: settings categories, panel headers, buttons, empty states | the call site, in code |

The last two have **no `_en` file at all**, on purpose. `WindowSpec` carries its own title and
description and a test asserts that table against `docs/client/05`; the `ui` strings sit beside the
control they describe, and half of them explain *why* a setting is off by default — reasoning that
belongs where somebody changing the setting will read it. Copying either into a properties file would
create a second English that nothing keeps in step, and the copy is the one that would rot.

So for those two, create `windows_de.properties` and `ui_de.properties` from scratch. Find the keys
by grepping the source:

```bash
grep -rho 't("[a-z][^"]*"' client/src/main/java | sort -u
```

Any key you do not write keeps the English from the code.

**Files are read as UTF-8**, explicitly. Write `Größe`, not `Größe` — Java's own
`Properties.load(InputStream)` is ISO-8859-1 by definition, which is why the loader does not use it.

⚠ **A `ui` key that no call site asks for fails the build.** English and its key live in two files
that nothing links, so renaming a caption's key would leave your translated line matching nothing —
silently, forever, with the player just seeing English. `UiKeyTest` turns that into a build failure
that names the orphaned key.

**2. Manual pages** — `client/src/main/resources/io/github/stoicswe/eyeandsickle/client/terms/`

```bash
mkdir -p terms/de
```

Copy pages across from `terms/en/` as you translate them. **Do not copy `index.txt`** — the index is
always read from `en`, because it is the list of pages the manual *has*, which is structure rather
than text. A translated index would let a partial translation silently shrink the manual: render
twelve of twenty-three pages and the other eleven would not go missing in one language, they would
cease to exist, and a shorter manual looks exactly like a shorter manual.

**3. The registry** — `client/src/main/java/.../i18n/Language.java`

```java
ENGLISH("en", "English"),
GERMAN("de", "Deutsch");
```

This is an explicit list rather than a directory scan on purpose. Scanning works from
`target/classes` and quietly stops working from inside a jar — and the client ships as a jar three
different ways. It would also offer a language the moment one file existed for it, putting a
half-empty language in front of every player.

### Partial translations are the normal case

You do not have to finish before you ship. Fallback is **per key** and **per manual page**, never per
file:

- A key your bundle does not define keeps its English.
- A key left **blank** is treated as "not done yet" and also keeps its English — so delete a line
  rather than emptying it if you mean to fall back deliberately.
- A manual page you have not written yet is shown in English rather than left out.
- Every page that fell back is listed in `TermDatabase.problems()`, so an unfinished translation is
  visible to whoever is finishing it and not only to the player.

A page that *exists* but is malformed reports as malformed rather than silently falling back — that
is the one problem a translator most needs to see.

### Checking your work

```bash
mvn -pl client test -Dtest='LanguageTest,LanguageFallbackTest,CommandSpecTest,UiKeyTest'
```

`CommandSpecTest` is the one that will catch a structural mistake: it holds that every flag a command
declares is one its parser really reads, that every message key resolves, and that the bundle carries
no key nothing asks for — a dead key being a translator's wasted afternoon on an option that no
longer exists.
