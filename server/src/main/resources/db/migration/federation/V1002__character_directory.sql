-- ===========================================================================
-- V1002 — the character directory.
--
-- ONLY runs on a server that opted into federation, exactly like V1001: it lives
-- under db/migration/federation, which the `federation` profile adds to Flyway's
-- locations. A private/friends server never sees this table
-- (docs/architecture/03-server-and-federation.md §2, 09 §1). Directory data is a
-- federation concern; a non-federating home has no one to publish a home binding
-- to and no one to resolve one for.
--
-- WHAT THIS TABLE IS — docs/architecture/09-player-state-portability.md §4, §8:
-- the player-character analogue of federation_peers. It maps an account DID to
-- the current home of each of its characters, so a client on a new machine can
-- resolve "where are my characters?" from its DID alone ("DNS for your
-- character"). Each row is the currently-recognised home binding for one
-- (account, slot); the signed CharacterHomeRecord that produced it (protocol
-- channel) is verified before a byte becomes a row.
--
-- WHAT THIS TABLE IS NOT (Invariant I14). It is a NON-ADVERSARIAL LOCATION
-- INDEX, never authority over game state. It says "this character's home is that
-- server", nothing about the character's items, balance, heat or standing — that
-- state lives only in the home server's Postgres and never travels as
-- self-asserted data. Nothing here adjudicates anything.
--
-- WHY (account_did, slot) IS THE KEY, NOT character_id. A save slot is the
-- account-relative identity that survives a migration; migrating to a new home
-- mints a FRESH character_id (09 §6), but the slot is stable (09 §8, uniqueness
-- moved to (did, slot)). Keying the binding on (account_did, slot) is therefore
-- what lets the monotonic sequence stop a stale record from resurrecting a
-- character that has already moved home, and what makes the recognised-character
-- count (the soft slot cap, 09 §2) a plain COUNT over an account's rows.
--
-- ANTI-ROLLBACK. sequence_number is monotonic per (account_did, slot), enforced
-- by the trigger below, exactly as federation_peers.sequence_number is. Without
-- it a captured OLD home binding could be replayed to point a moved character
-- back at a home it has left — a rollback that looks like a normal directory
-- refresh. A CHECK cannot express "greater than the value already stored", so the
-- rule needs a trigger; it does not need a service-layer promise.
--
-- This file depends on is_did(text) from V2 (2 < 1002 in the one shared history).
-- ===========================================================================


CREATE TABLE character_directory (
    entry_id                   uuid        PRIMARY KEY,
    -- The account (a DID) whose character this binding is for. Text with an
    -- is_did shape check, NOT a foreign key: the account may have no local
    -- players row at all — the whole point is to locate a character that lives on
    -- another home server (09 §4). A gossip-safe public identifier (09 §7).
    account_did                text        NOT NULL,
    -- The character's id at its home server. Home-relative and not a foreign key,
    -- for the same reason: the character lives elsewhere. Descriptive data on the
    -- (account_did, slot) binding; a migration to a new home replaces it.
    character_id               uuid        NOT NULL,
    -- The save slot within the account. The stable, account-relative identity the
    -- binding is keyed on. Bounded 1..16 mirroring ck_players_slot_bound (the
    -- generous structural range a slot may take); the real, soft cap on how many
    -- an account may hold is service-enforced (CharacterProperties.maxCharacters).
    slot                       smallint    NOT NULL,
    -- The home server that hosts the character and SIGNED this binding. The only
    -- stable way to reach the character; never key anything off the endpoint.
    home_server_did            text        NOT NULL,
    -- Where to reach the home server. Moves when a self-hoster changes address.
    home_endpoint              text        NOT NULL,
    -- X.509-encoded X25519 transport key of the HOME server, carried so a resolver
    -- can seal traffic to it without a second lookup
    -- (docs/architecture/07-transport-security.md). bytea, not text: re-encoding
    -- key material through base64 in and out of storage is a needless place for a
    -- byte to change. Same 32..256 bound as federation_peers.transport_public_key.
    home_transport_public_key  bytea       NOT NULL,
    -- The DID fragment naming the home server's signing key, e.g.
    -- 'did:plc:home#key1'. Kept so a rotation is attributable, and so a verifier
    -- knows which key to resolve. Its DID part must equal home_server_did — a
    -- server may only sign for itself — which the verifier checks before storage.
    signing_key_id             text        NOT NULL,
    -- Monotonic per (account_did, slot). See the anti-rollback note above.
    sequence_number            bigint      NOT NULL,
    -- The home server's Ed25519 signature over the record's canonical signing
    -- bytes (protocol CharacterHomeRecord.signingBytes). Stored so any peer can
    -- re-verify the binding it was served, and so an equal-sequence re-announcement
    -- can be told apart from an equal-sequence CONFLICT by comparing signatures.
    signature                  bytea       NOT NULL,
    first_seen_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_seen_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    row_version                bigint      NOT NULL DEFAULT 0,

    -- One home binding per (account, slot). Two rows for the same slot would let a
    -- character be resolved to two homes at once — the double-home this table
    -- exists to prevent.
    CONSTRAINT uq_character_directory_account_slot UNIQUE (account_did, slot),
    CONSTRAINT ck_character_directory_account_did  CHECK (is_did(account_did)),
    CONSTRAINT ck_character_directory_home_did     CHECK (is_did(home_server_did)),
    -- \S, NOT [^[:space:]] — see the same constraint on federation_peers in V1001. `~` runs a
    -- Java regex, which has no POSIX bracket expressions, and the POSIX spelling refused every
    -- endpoint rather than only the malformed ones.
    CONSTRAINT ck_character_directory_endpoint     CHECK (home_endpoint ~ '^https?://\S+$'
                                                          AND length(home_endpoint) <= 2048),
    CONSTRAINT ck_character_directory_slot         CHECK (slot BETWEEN 1 AND 16),
    CONSTRAINT ck_character_directory_key          CHECK (octet_length(home_transport_public_key) BETWEEN 32 AND 256),
    CONSTRAINT ck_character_directory_signature    CHECK (octet_length(signature) BETWEEN 1 AND 128),
    CONSTRAINT ck_character_directory_sequence     CHECK (sequence_number >= 0),
    CONSTRAINT ck_character_directory_seen         CHECK (last_seen_at >= first_seen_at),
    CONSTRAINT ck_character_directory_row_version  CHECK (row_version >= 0)
);

COMMENT ON TABLE character_directory IS
    'The character directory: DID -> current home per (account, slot) (docs/architecture/09 §4). '
    'A non-adversarial location index, never authority over game state (Invariant I14). '
    'sequence_number is monotonic per (account_did, slot) (anti-rollback, trigger-enforced).';
COMMENT ON COLUMN character_directory.home_transport_public_key IS
    'X.509-encoded X25519 transport key of the home server (docs/architecture/07-transport-security.md).';

-- Resolution ("where are DID D's characters?") and the recognised-character count
-- both scan by account_did; this keeps that off a full-table scan.
CREATE INDEX ix_character_directory_account ON character_directory (account_did, slot);


-- ---------------------------------------------------------------------------
-- Monotonic sequence enforcement for character_directory.
--
-- A CHECK cannot see the previous value, so the rule needs a trigger — the same
-- shape as federation_peers_no_sequence_rollback. It refuses a rollback outright
-- rather than ignoring it, because a silently-ignored rollback leaves no trace of
-- the attack: the row still looks current and nobody learns a stale home binding
-- was just replayed.
--
-- Equality is allowed: a re-announcement of the SAME binding is a normal refresh
-- and must not fail, so last_seen_at can still be bumped. The service layer is
-- what tells an equal-sequence refresh (same signature) apart from an
-- equal-sequence CONFLICT (a different signature at the same sequence), and it
-- never issues an UPDATE for the latter.
-- ---------------------------------------------------------------------------




CREATE TRIGGER character_directory_no_sequence_rollback BEFORE UPDATE ON character_directory FOR EACH ROW CALL "io.github.stoicswe.eyeandsickle.server.persistence.MonotonicSequenceTrigger";
