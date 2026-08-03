-- ===========================================================================
-- V1001 — federation tables.
--
-- ONLY runs on a server that opted into federation. application.yml adds
-- classpath:db/migration/federation to Flyway's locations under the `federation`
-- profile; a private/friends server never sees these tables at all
-- (docs/architecture/03-server-and-federation.md §2).
--
-- Both locations are scanned by ONE Flyway instance and share one
-- flyway_schema_history, so the version ranges are disjoint by convention:
--     db/migration/core        V1, V2, ...
--     db/migration/federation  V1001, V1002, ...
-- A federating server's history is therefore a superset of a non-federating
-- one's, and turning federation on later only ever appends.
--
-- This file depends on `is_did(text)` from V2. That ordering holds because
-- 2 < 1001 in a single shared history — do not "tidy" the ranges.
--
-- ⚠ REPUTATION DISAMBIGUATION — docs/architecture/06 §1 constraint 5 ⚠
--
-- `validators.validator_reputation` in this file is a FEDERATED SERVER's trust
-- score, used to weight quorum votes on cross-server duel outcomes
-- (docs/architecture/05-validator-quorum.md). It has NOTHING to do with
-- `faction_reputations.standing` in the core schema, which is a PLAYER's
-- Eye/Sickle standing (docs/design/01-core-resources.md §5).
--
-- Different subject, different lifetime, different consequences: slashing a
-- validator is an anti-cheat action, losing Sickle standing is a story beat.
-- They share no column and — because one is keyed by a server DID and the other
-- by a player_id — no key either, so no join between them is expressible. If you
-- find yourself writing one, you are about to merge two things the design keeps
-- apart on purpose. See docs/design/glossary.md.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- validators — opted-in servers eligible to adjudicate cross-server duels.
--
-- docs/architecture/05 §2.1: sampling reads `reputation` and `uptime`, and the
-- sampling weight is their product (§2.2). They are SEPARATE columns because §4
-- is explicit that a validator which was sampled but did not respond must not be
-- penalised like one that actively signed wrong — unavailability and dishonesty
-- are different signals and conflating them into a single score destroys the
-- distinction the whole reputation rule depends on.
--
-- `is_new` carries the cold-start floor (§2.5). Without a floor, a new validator
-- starts at reputation 0, is never sampled, and can never earn reputation —
-- deadlock. The floor VALUE is configuration (application.yml, `newcomer-
-- reputation`), not schema: it is a tuning knob, and knobs do not belong in
-- migrations.
--
-- No equivocation evidence is stored here. A provable equivocation is a FLAG
-- (see flagged_servers) that happens to also drive a hard slash; keeping the
-- proof next to the score would invite reading the score as the evidence.
-- ---------------------------------------------------------------------------
CREATE TABLE validators (
    validator_did        text        PRIMARY KEY,
    -- ⚠ NOT faction reputation. See the header of this file.
    -- Bounded [0, 1] per docs/architecture/05 §2.1. numeric, not double: two
    -- servers summing weighted power in a different order must not disagree
    -- about whether a threshold was cleared, and in a federation a disagreement
    -- about a threshold is indistinguishable from cheating.
    validator_reputation numeric(9, 8) NOT NULL,
    -- Rolling availability, decayed separately on a no-show (§4, gamma).
    uptime               numeric(9, 8) NOT NULL DEFAULT 1,
    is_new               boolean     NOT NULL DEFAULT true,
    enrolled_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_sampled_at      TIMESTAMP WITH TIME ZONE NULL,
    last_vote_at         TIMESTAMP WITH TIME ZONE NULL,
    -- Counters, not a rate: a rate cannot be updated concurrently without losing
    -- one of the two updates, and these are written from the duel-resolution path
    -- where concurrency is the normal case.
    votes_correct        bigint      NOT NULL DEFAULT 0,
    votes_divergent      bigint      NOT NULL DEFAULT 0,
    no_shows             bigint      NOT NULL DEFAULT 0,
    row_version          bigint      NOT NULL DEFAULT 0,

    CONSTRAINT ck_validators_did_shape  CHECK (is_did(validator_did)),
    CONSTRAINT ck_validators_reputation CHECK (validator_reputation >= 0 AND validator_reputation <= 1),
    CONSTRAINT ck_validators_uptime     CHECK (uptime >= 0 AND uptime <= 1),
    CONSTRAINT ck_validators_counters   CHECK (votes_correct >= 0 AND votes_divergent >= 0 AND no_shows >= 0),
    CONSTRAINT ck_validators_row_version CHECK (row_version >= 0)
);

COMMENT ON TABLE validators IS
    'Opted-in federated servers eligible for quorum sampling (docs/architecture/05-validator-quorum.md). '
    'validator_reputation is NOT faction reputation and has no join path to players.';
COMMENT ON COLUMN validators.uptime IS
    'Liveness, tracked separately from correctness on purpose (docs/architecture/05 §4).';

-- Sampling is weighted-random over reputation * uptime (§2.2, A-Res), which
-- reads every eligible candidate. This index keeps that scan off the rows that
-- are ineligible anyway.
CREATE INDEX ix_validators_eligible ON validators (validator_reputation DESC, uptime DESC);


-- ---------------------------------------------------------------------------
-- duels — cross-server adjudications and their sampling record.
--
-- `sampled_validators` is not decoration: docs/architecture/04 §7 step 1 requires
-- a verifier to confirm that each signature "resolves to a validator that was
-- actually sampled for that duel". Without the persisted sampling record, a
-- server could accept signatures from validators it invented afterwards, and
-- Invariant I15 would hold only on paper.
--
-- The committee is stored as JSON rather than a join table because it is a
-- snapshot of weights AT SAMPLING TIME. Reputation moves after every duel, so a
-- foreign key into `validators` would resolve to today's weight and silently
-- re-adjudicate old duels with new numbers.
--
-- Nothing here decides consensus. The 2f+1-of-3f+1 weighted threshold
-- (docs/architecture/05 §1) is evaluated by protocol `QuorumCommittee`; this
-- table holds the evidence it judges.
-- ---------------------------------------------------------------------------
CREATE TABLE duels (
    duel_id            uuid        PRIMARY KEY,
    -- Array of participant DIDs.
    participants       JSON       NOT NULL,
    -- Array of {did, reputation, uptime, weight} as sampled. A snapshot, frozen.
    sampled_validators JSON       NOT NULL,
    committee_size     integer     NOT NULL,
    -- The agreed outcome document, NULL until the threshold is reached.
    outcome            JSON       NULL,
    -- Validator signature blocks over the outcome (docs/architecture/04 §3.1).
    signatures         JSON       NOT NULL DEFAULT JSON '[]',
    opened_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    resolved_at        TIMESTAMP WITH TIME ZONE NULL,
    row_version        bigint      NOT NULL DEFAULT 0,

    CONSTRAINT ck_duels_participants  CHECK (is_json_array(participants)
                                             AND json_array_length(participants) >= 2),
    CONSTRAINT ck_duels_committee     CHECK (is_json_array(sampled_validators)),
    CONSTRAINT ck_duels_committee_size CHECK (committee_size > 0
                                              AND committee_size = json_array_length(sampled_validators)),
    CONSTRAINT ck_duels_signatures    CHECK (is_json_array(signatures)),
    CONSTRAINT ck_duels_outcome_object CHECK (outcome IS NULL OR is_json_object(outcome)),
    -- An outcome without a resolution time, or a resolution time without an
    -- outcome, is a half-written duel. Both markers agree or the row is refused.
    CONSTRAINT ck_duels_resolved_pair CHECK ((outcome IS NULL) = (resolved_at IS NULL)),
    CONSTRAINT ck_duels_resolved_time CHECK (resolved_at IS NULL OR resolved_at >= opened_at),
    CONSTRAINT ck_duels_row_version   CHECK (row_version >= 0)
);

COMMENT ON TABLE duels IS
    'Cross-server adjudications with their frozen sampling record (docs/architecture/05 §2, §5). '
    'The sampling record is what makes docs/architecture/04 §7 step 1 checkable.';

CREATE INDEX ix_duels_unresolved ON duels (opened_at);


-- ---------------------------------------------------------------------------
-- flagged_servers — federation-wide non-recognition.
--
-- docs/architecture/03 §4: the negative half of the anti-cheat model. A server
-- caught minting fraudulent items or running dishonest validators has its items
-- refused by honest servers. There is no authority to ban it; it simply gets
-- ignored, which makes its fraudulent items worthless outside its own walls.
--
-- `reason` is free text and `evidence` is JSON because the flagging MECHANISM
-- is explicitly [PROPOSAL] (§4): equivocation is cryptographic and automatic —
-- both contradicting signatures exist, so `evidence` carries them — but the
-- softer fraud cases are unspecified, and so is who propagates a flag. Encoding
-- a vocabulary here would promote that proposal to a decision.
--
-- Reversibility is likewise open, which is why `cleared_at` exists and is
-- nullable rather than a flag being deleted. A deleted flag cannot be audited,
-- and "why did we un-ignore that server" is a question a federation will ask.
-- ---------------------------------------------------------------------------
CREATE TABLE flagged_servers (
    flag_id       uuid        PRIMARY KEY,
    server_did    text        NOT NULL,
    reason        text        NOT NULL,
    -- The proof, where a proof exists. For equivocation this is the two
    -- conflicting signed outcomes (docs/architecture/05 §3.3) — self-contained,
    -- so any peer can re-verify the flag instead of trusting whoever raised it.
    evidence      JSON       NOT NULL DEFAULT JSON '{}',
    raised_by_did text        NULL,
    flagged_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    cleared_at    TIMESTAMP WITH TIME ZONE NULL,
    cleared_note  text        NULL,

    -- The UNIQUENESS KEY FOR LIVE FLAGS ONLY, and it exists because H2 has no partial index.
    -- In Postgres this rule was `CREATE UNIQUE INDEX ... WHERE cleared_at IS NULL`. Dropping the
    -- WHERE during the port turned "one live flag per server" into "one flag per server, EVER" —
    -- so a server that was flagged and then cleared could never be flagged again. Nothing failed
    -- at migration time; `FlaggedServerRegistryIT.reflaggableAfterClear` is what caught it.
    --
    -- Generated, never written: it is `server_did` while the flag is live and NULL once cleared,
    -- and a unique index treats NULLs as distinct, so any number of cleared rows coexist. Derived
    -- from the two columns it depends on, so it cannot disagree with them.
    active_server_did text GENERATED ALWAYS AS (CASE WHEN cleared_at IS NULL THEN server_did END),

    CONSTRAINT ck_flagged_servers_did      CHECK (is_did(server_did)),
    CONSTRAINT ck_flagged_servers_raiser   CHECK (raised_by_did IS NULL OR is_did(raised_by_did)),
    CONSTRAINT ck_flagged_servers_reason   CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_flagged_servers_evidence CHECK (is_json_object(evidence)),
    CONSTRAINT ck_flagged_servers_cleared  CHECK (cleared_at IS NULL OR cleared_at >= flagged_at)
);

COMMENT ON TABLE flagged_servers IS
    'Non-recognition flags (docs/architecture/03-server-and-federation.md §4). Reason vocabulary is '
    'deliberately open — the flagging mechanism is [PROPOSAL].';

-- A server is flagged or it is not; two live flags for the same server would let
-- one be cleared while the other silently keeps it non-recognised. Over the generated
-- column, so it constrains LIVE flags only — see the column's own note.
CREATE UNIQUE INDEX uq_flagged_servers_active ON flagged_servers (active_server_did);


-- ---------------------------------------------------------------------------
-- federation_peers — the directory / discovery table.
--
-- docs/architecture/03 §2: the directory is a LOW-TRUST INDEX, NOT AN AUTHORITY.
-- It says "these servers exist and claim to federate", nothing more. Trust in
-- any specific outcome comes from the quorum (05) and provenance (04). Nothing
-- in this table may be treated as adjudicating anything.
--
-- Peer discovery is being implemented separately; this is the storage contract
-- it writes against.
--
-- ANTI-ROLLBACK. `sequence_number` is monotonic per peer and enforced by trigger
-- below. Without it, an attacker who captured an OLD self-descriptor could
-- replay it to roll a peer back to a retired transport key — a downgrade attack
-- that looks exactly like a normal directory refresh. The CHECK constraints
-- cannot express "greater than the value already stored", so the rule needs a
-- trigger; it does not need a service-layer promise.
-- ---------------------------------------------------------------------------
CREATE TABLE federation_peers (
    peer_id                    uuid        PRIMARY KEY,
    peer_did                   text        NOT NULL,
    -- Where to reach it. An endpoint moves (self-hosters change addresses); the
    -- DID does not. Never key anything off this column.
    endpoint_url               text        NOT NULL,
    -- X.509-encoded X25519 public key, the form protocol
    -- `X25519KeyExchange.encodePublicKey` produces and
    -- `TransportKeyAttestation.transportPublicKey` carries
    -- (docs/architecture/07-transport-security.md). bytea, not text: re-encoding
    -- key material through base64 in and out of storage is a needless place for a
    -- byte to change.
    transport_public_key       bytea       NOT NULL,
    -- The DID fragment naming that key, e.g. 'did:plc:xxx#transport-1'. Kept so a
    -- rotation is attributable rather than just observed.
    transport_key_id           text        NULL,
    -- Not before / not after from the attestation, so an expired transport key is
    -- detectable without re-parsing the descriptor on every dial.
    transport_key_not_before   TIMESTAMP WITH TIME ZONE NULL,
    transport_key_not_after    TIMESTAMP WITH TIME ZONE NULL,
    -- The peer's signed self-descriptor, exactly as received: the descriptor
    -- document AND its signature, one self-contained blob, the same pattern as a
    -- provenance envelope. Stored verbatim because the signature covers specific
    -- bytes; splitting it into columns and reassembling it later is how a
    -- signature stops verifying for reasons nobody can reproduce.
    self_descriptor            JSON       NOT NULL,
    -- Monotonic per peer. See the anti-rollback note above.
    sequence_number            bigint      NOT NULL,
    first_seen_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_seen_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    -- Distinct from last_seen_at: a peer can be ANNOUNCED by a third party in the
    -- directory (seen) without this server ever having completed a handshake with
    -- it (contacted). Collapsing the two would let a peer that has never once
    -- answered look healthy.
    last_successful_contact_at TIMESTAMP WITH TIME ZONE NULL,
    -- Liveness as two counters rather than one ratio, for the same reason
    -- validators.uptime is separate from validators.validator_reputation: a rate
    -- cannot be updated concurrently without losing an update, and the derived
    -- ratio is one division away whenever it is actually wanted.
    contact_successes          bigint      NOT NULL DEFAULT 0,
    contact_failures           bigint      NOT NULL DEFAULT 0,
    consecutive_failures       integer     NOT NULL DEFAULT 0,
    row_version                bigint      NOT NULL DEFAULT 0,

    CONSTRAINT uq_federation_peers_did       UNIQUE (peer_did),
    CONSTRAINT ck_federation_peers_did_shape CHECK (is_did(peer_did)),
    -- \S, NOT [^[:space:]]. `~` runs a JAVA regex here, and Java has no POSIX bracket
    -- expressions: it reads [^[:space:]] as "not one of : s p a c e", so the constraint
    -- refused every URL containing an `s`, an `e` or a `.` — i.e. all of them. It parsed,
    -- applied, and looked right. Verified both ways: this accepts https://peer.example.test
    -- and http://localhost:8080, and still refuses ftp://, an embedded space, and `notaurl`.
    CONSTRAINT ck_federation_peers_endpoint  CHECK (endpoint_url ~ '^https?://\S+$'
                                                    AND length(endpoint_url) <= 2048),
    CONSTRAINT ck_federation_peers_key       CHECK (octet_length(transport_public_key) BETWEEN 32 AND 256),
    CONSTRAINT ck_federation_peers_descriptor CHECK (is_json_object(self_descriptor)),
    CONSTRAINT ck_federation_peers_sequence  CHECK (sequence_number >= 0),
    CONSTRAINT ck_federation_peers_key_window CHECK (transport_key_not_before IS NULL
                                                     OR transport_key_not_after IS NULL
                                                     OR transport_key_not_after > transport_key_not_before),
    CONSTRAINT ck_federation_peers_seen      CHECK (last_seen_at >= first_seen_at),
    CONSTRAINT ck_federation_peers_contact   CHECK (last_successful_contact_at IS NULL
                                                    OR last_successful_contact_at >= first_seen_at),
    CONSTRAINT ck_federation_peers_counters  CHECK (contact_successes >= 0
                                                    AND contact_failures >= 0
                                                    AND consecutive_failures >= 0),
    CONSTRAINT ck_federation_peers_row_version CHECK (row_version >= 0)
);

COMMENT ON TABLE federation_peers IS
    'The federation directory as this server knows it (docs/architecture/03-server-and-federation.md §2). '
    'A low-trust index, never an authority. sequence_number is monotonic (anti-rollback, trigger-enforced).';
COMMENT ON COLUMN federation_peers.transport_public_key IS
    'X.509-encoded X25519 public key (docs/architecture/07-transport-security.md).';

-- The dial list: peers worth trying, freshest contact first.
CREATE INDEX ix_federation_peers_liveness ON federation_peers (last_successful_contact_at DESC NULLS LAST);


-- ---------------------------------------------------------------------------
-- Monotonic sequence enforcement for federation_peers.
--
-- A CHECK constraint cannot see the previous value, so this rule needs a
-- trigger. It refuses a rollback outright rather than ignoring it, because a
-- silently-ignored rollback attempt leaves no trace of the attack: the row still
-- looks current, and nobody learns that a peer just tried to hand back an old
-- descriptor.
--
-- Equality is allowed: a re-announcement of the SAME descriptor is a normal
-- directory refresh and must not fail, so liveness counters can still be bumped.
-- ---------------------------------------------------------------------------




CREATE TRIGGER federation_peers_no_sequence_rollback BEFORE UPDATE ON federation_peers FOR EACH ROW CALL "io.github.stoicswe.eyeandsickle.server.persistence.MonotonicSequenceTrigger";
