<img src="./docs/pngs/Mr._Monitor_PNG.png" width="450" height="300" />

*Mr. Monitor is © Sham Tomaselli — [shamcube](https://www.youtube.com/@ShamCube).*

# The-Eye-and-Sickle

A distributed, federated online hacking game involving the "Eye" and the "Sickle".

A puzzle-centric hacking game set in a surveillance dystopia. Play as an operator in **The Sickle**, a decentralized resistance, against **The Eye**, the surveillance state. Compute — not money — is the master scarcity, and the core hacking minigame is the game; every other system exists to give it stakes. Single-player by default, with opt-in, real-loss multiplayer over a self-hostable, federated server network.

**Stack:** JavaFX multi-window desktop client · Spring Boot + PostgreSQL self-hostable servers (Docker Compose) · AT Protocol identity (auth-only) · federated anti-cheat via validator quorum + signed item provenance.

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
| [`server/`](server) | The self-hostable home server. Spring Boot + PostgreSQL. Authoritative for all game state. |
| [`client/`](client) | The JavaFX desktop client. One OS window per tool. Renders; never decides. |

### Running it

```bash
mvn install -DskipTests && mvn -pl client javafx:run
```

The `install` step is needed once so the client can resolve `protocol` from your local repository.

For the server, [`deploy/`](deploy) has a Docker Compose stack — copy `deploy/.env.example` to `deploy/.env`, fill it in, then `docker compose -f deploy/docker-compose.yml up`.

### Other build targets

```bash
mvn -Pit verify                     # + Testcontainers integration tests (needs Docker)
mvn -Pquality spotless:apply        # format with palantir-java-format
```

Container-backed tests are deliberately kept out of the default build so a plain `mvn verify` works on a fresh clone with no toolchain beyond a JDK.

Self-contained client packaging (jlink/jpackage) is **not wired up yet** — see the comment at the bottom of [`client/pom.xml`](client/pom.xml) for why `jlink` cannot work with the current dependency graph and what the two real options are.
