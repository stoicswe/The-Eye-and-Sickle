# Backup, restore, and cooperative migration

The full-fidelity **Option B** of player-state portability
(`docs/architecture/09-player-state-portability.md` §5) is an *operational* one: your home
server's state lives entirely in its PostgreSQL (Invariant I14), so backing it up and
restoring it is backing up and restoring one database. Nothing here touches an invariant —
the state never leaves trusted hands.

This is the safety net behind every migration. The in-app migration endpoints move a
*character*; this moves (or rescues) the *whole server*.

> The database holds every player's inventory, balance, rig, heat, faction standing, the
> public ledger, and the provenance chains. Treat these dumps as sensitive: they are the
> authoritative game state, and a leaked dump is a leaked server. Keep them somewhere only the
> operator can read.

All commands assume the Compose stack in this directory (`name: eyeandsickle`, DB service
`db`) and that `.env` defines `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`.

## Back up

A logical dump of the whole database, compressed. Run it on a schedule (cron / a timer);
it is safe to run against a live server.

```bash
# from deploy/, with .env loaded
set -a; . ./.env; set +a

docker compose exec -T db \
  pg_dump --format=custom --no-owner --dbname="$POSTGRES_DB" --username="$POSTGRES_USER" \
  > "eyeandsickle-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

- `--format=custom` produces a compressed archive that `pg_restore` can restore selectively
  and in parallel.
- `--no-owner` keeps the dump portable to a destination whose role names differ.
- The Flyway schema-history table is included, so a restored database reports the exact
  migration state it was dumped at — do **not** re-baseline it.

Verify a dump is readable without restoring it:

```bash
pg_restore --list "eyeandsickle-<timestamp>.dump" | head
```

Keep several generations off-box. A backup you have never restored is a hope, not a backup —
rehearse the restore below at least once.

## Restore (same server, disaster recovery)

Restoring **replaces** the current database. Stop the app first so nothing writes mid-restore.

```bash
set -a; . ./.env; set +a

docker compose stop server

# Drop and recreate an empty database, then load the dump into it.
docker compose exec -T db psql --username="$POSTGRES_USER" --dbname=postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE);" \
  -c "CREATE DATABASE \"$POSTGRES_DB\" OWNER \"$POSTGRES_USER\";"

docker compose exec -T db \
  pg_restore --no-owner --clean --if-exists --exit-on-error \
  --dbname="$POSTGRES_DB" --username="$POSTGRES_USER" < "eyeandsickle-<timestamp>.dump"

docker compose start server
```

The app runs Flyway on boot; because the dump already carries the schema **and** its history,
Flyway finds nothing to do and starts clean. A mismatch (a dump older than the app image's
migrations) is resolved by Flyway applying the *newer* migrations on top — which is the normal
upgrade path, not an error.

## Cooperative migration to another operator's server (Option B, §5)

When two operators cooperate and therefore trust each other, a character's **whole** state —
economy included — may move between their servers. Because both sides are trusted end to end,
the honest, lossless mechanism is a database transfer, not a per-field re-import:

1. **Freeze the character on the source.** Retire it there first, so it is never live in two
   places (§6.1, no double-play): call the operator commit endpoint
   `POST /api/operator/migration/commit/{characterId}` with the `X-Operator-Token` header, or,
   for a whole-server move, simply stop the source `server`.
2. **Hand over the state.** For a whole-server move, ship the `pg_dump` above and restore it on
   the destination (the *Restore* steps, pointed at the destination's `db`). For a single
   character, the source operator calls `POST /api/operator/migration/export/{characterId}`
   (operator-authenticated) to produce a signed full-state export and the destination operator
   calls `POST /api/operator/migration/import` with it.
3. **Advance the home binding.** The destination becomes the character's home at a strictly
   higher directory sequence (§4); the monotonic sequence is what stops the source resurrecting
   the character later (no rollback / no fork, §6.1).

Option B is operator-gated on purpose. Set `EYEANDSICKLE_MIGRATION_OPERATOR_TOKEN` (property
`eyeandsickle.migration.operator-token`) to a strong shared secret on **both** operators before
using the per-character endpoints; **until it is set, every Option-B request is refused** — the
safe closed default for a path that moves a character's whole economy.

> **Untrusted destinations use Option C instead**, and it is a different mechanism entirely: the
> player carries only their DID and provenanced items, the destination re-verifies every item
> chain, and the economy **resets** (`09` §6). Never restore a `pg_dump`, or accept a full-state
> export, from a server you do not control — importing freely-assertable economy from an
> untrusted source is exactly the Invariant I14 / §3 violation Option C exists to avoid.

## What the bundle-based path does *not* carry (yet)

The per-character full-state **export/import** endpoints restore the identity-owned standing
(committed faction, personal heat) and re-recognize the provenanced items. Re-applying the
ethecoin **balance** is a ledger transaction the economy slice owns (Invariant I1), and faction
**reputation** is a separate table; those are not written by the migration import today. For a
guaranteed-lossless full-state move, prefer the **database dump/restore** above — it carries
everything by construction.
