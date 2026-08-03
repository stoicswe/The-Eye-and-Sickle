package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Proves the migrations apply to a real PostgreSQL and that the FIRM constraints in
 * {@code docs/architecture/06-data-model.md} §1 actually hold in the database — not merely in the
 * comments above them.
 *
 * <p>The emphasis is on the failure cases. A schema that stores a valid row is easy; what the other
 * five systems need to be able to rely on is that the database <em>refuses</em> the invalid ones,
 * because on an authoritative server (Invariant I14) the database is the last line of defence and the
 * only one that a bug in the service layer cannot walk past.
 */
class SchemaIT extends DatabaseIntegrationTestBase {

    /**
     * ⚠ Read from the vocabulary, never written out. Fixtures that need <em>some</em> valid puzzle
     * class use this, so a migration that changes the set (V4 did exactly that) breaks the one test
     * that is about the vocabulary rather than half the file.
     */
    private static final String A_PUZZLE_CLASS =
            EnumColumns.PUZZLE_CLASS_VALUES.iterator().next();

    private static final String DID_A = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DID_B = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DID_C = "did:plc:cccccccccccccccccccccccc";

    // ------------------------------------------------------------------ the migrations themselves

    @Test
    @DisplayName("both migration locations apply cleanly and are recorded as successful")
    void migrationsApply() {
        // ⚠ NOT an exact list of version numbers. It used to be, and it was a test that failed on
        // every migration added — which teaches whoever adds one to edit the expectation rather than
        // read it. The properties worth holding are that NOTHING failed, and that the two locations
        // keep disjoint ranges so enabling federation later only ever appends.
        assertThat(jdbcClient()
                        .sql("SELECT count(*) FROM flyway_schema_history WHERE NOT success")
                        .query(Long.class)
                        .single())
                .as("a migration recorded as failed leaves the schema in a state nothing else here describes")
                .isZero();

        List<String> applied = jdbcClient()
                .sql("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL"
                        + " ORDER BY installed_rank")
                .query(String.class)
                .list();

        // Core owns 1..999, federation 1000+. Both ranges must be non-empty: this harness migrates
        // both locations, and a suite that silently ran core alone would leave every federation
        // table untested while reporting success.
        assertThat(applied).contains("1", "2", "1001");
        assertThat(applied.stream().map(Integer::valueOf).filter(v -> v < 1000))
                .as("core migrations")
                .isNotEmpty();
        assertThat(applied.stream().map(Integer::valueOf).filter(v -> v >= 1000))
                .as("federation migrations")
                .isNotEmpty();
        assertThat(applied.stream().map(Integer::valueOf).toList())
                .as("⚠ federation must sort ABOVE core, or enabling it later would insert into the middle"
                        + " of an applied history and Flyway would refuse to start")
                .isSorted();
    }

    @Test
    @DisplayName("every core and federation table exists")
    void tablesExist() {
        assertThat(tableNames())
                .contains(
                        "server_state",
                        "allowlist_entries",
                        "players",
                        "faction_reputations",
                        "rigs",
                        "compute_allocations",
                        "items",
                        "provenance_records",
                        "ledger_transactions",
                        "deployed_miners",
                        "breach_resolutions",
                        "validators",
                        "duels",
                        "flagged_servers",
                        "federation_peers");
    }

    @Test
    @DisplayName("the indexes the design docs actually name are present")
    void indexesExist() {
        // ⚠ Indexes AND constraints, because a UNIQUE declared as a table constraint is backed by an
        // index the engine names for itself — `uq_provenance_records_position` is a constraint here,
        // and H2 files its index under a generated name. Asking only `information_schema.indexes`
        // reported the design's headline access path as missing when it was present.
        List<String> indexes = jdbcClient()
                .sql(
                        """
                        SELECT index_name FROM information_schema.indexes WHERE table_schema = 'public'
                        UNION
                        SELECT constraint_name FROM information_schema.table_constraints
                         WHERE constraint_schema = 'public'
                        """)
                .query(String.class)
                .list();

        assertThat(indexes)
                .as("docs/architecture/06 §1 constraint 3: provenance by item_id with chain_depth range access")
                .contains("uq_provenance_records_position")
                .as("constraint 2: the public ledger is indexed for BOTH counterparties")
                .contains("ix_ledger_from", "ix_ledger_to")
                .as("constraint 4: the compute ledger is attributable from either rig (Invariant I6)")
                .contains("ix_compute_allocations_charged", "ix_compute_allocations_counterparty");
    }

    // ------------------------------------------------------------------ constraint 5: two reputations

    @Test
    @DisplayName("faction reputation and validator reputation share no column and no key")
    void thereIsNoJoinBetweenTheTwoReputations() {
        List<String> factionColumns = columnsOf("faction_reputations");
        List<String> validatorColumns = columnsOf("validators");

        assertThat(factionColumns).contains("standing").doesNotContain("validator_reputation");
        assertThat(validatorColumns).contains("validator_reputation").doesNotContain("standing", "faction");

        // Constraint 5 is stated as "no shared column"; this schema goes further and shares no key
        // either, so no join between a player's Eye/Sickle standing and a server's trust score is
        // expressible at all. `row_version` and `updated_at`-style bookkeeping columns are not a join
        // path, so the assertion is on the identifying columns.
        assertThat(factionColumns).doesNotContain("validator_did");
        assertThat(validatorColumns).doesNotContain("player_id");

        // And no single table holds both notions.
        Long conflated = jdbcClient().sql("""
                        SELECT count(*)
                          FROM information_schema.columns a
                          JOIN information_schema.columns b
                            ON a.table_name = b.table_name
                           AND a.table_schema = b.table_schema
                         WHERE a.table_schema = 'public'
                           AND a.column_name = 'validator_reputation'
                           AND b.column_name IN ('standing', 'faction_reputation')
                        """).query(Long.class).single();
        assertThat(conflated).isZero();
    }

    // ------------------------------------------------------------------ constraint 2: the ledger

    @Test
    @DisplayName("the public ledger is queryable from either counterparty")
    void ledgerIsQueryableFromBothSides() {
        insertLedgerRow(null, DID_A, Ethecoin.ofWholeEthecoin(10), "mining_reward", true);
        insertLedgerRow(DID_A, DID_B, Ethecoin.ofWholeEthecoin(4), "trade", true);
        insertLedgerRow(DID_B, DID_C, Ethecoin.ofWholeEthecoin(1), "purchase", false);

        // "Where did this money go" and "who paid for this" are the two halves of an investigation
        // (docs/design/01-core-resources.md §2.2), and both have to be answerable.
        assertThat(ledgerCount("SELECT count(*) FROM ledger_transactions WHERE from_did = :did", DID_A))
                .isEqualTo(1);
        assertThat(ledgerCount("SELECT count(*) FROM ledger_transactions WHERE to_did = :did", DID_A))
                .isEqualTo(1);
        assertThat(ledgerCount(
                        "SELECT count(*) FROM ledger_transactions WHERE from_did = :did OR to_did = :did", DID_B))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a Dead Drop is still a ledger row, just an untraceable one")
    void deadDropsAreRecordedNotOmitted() {
        insertLedgerRow(DID_A, DID_B, Ethecoin.ofWholeEthecoin(50), "trade", false);

        // Laundering is a gameplay verb (docs/design/01 §2.2). If an untraceable transfer left no row
        // at all, there would be nothing for an investigator to ever find, and the Dead Drop would be
        // a perfect crime rather than a hard one.
        assertThat(ledgerCount("SELECT count(*) FROM ledger_transactions WHERE NOT traceable", null))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("only a mining reward may have no payer")
    void onlyTheFaucetHasNoPayer() {
        assertThatThrownBy(() -> insertLedgerRow(null, DID_A, Ethecoin.ofWholeEthecoin(5), "trade", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_faucet");

        // A crack seizure moves ethecoin that already exists on the host's machine — "a transfer, not
        // a faucet" (docs/design/04-mining.md §5.1). A payerless one would mint currency.
        assertThatThrownBy(() -> insertLedgerRow(null, DID_A, Ethecoin.ofWholeEthecoin(5), "crack_seizure", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a zero-value or self-directed ledger row is refused")
    void ledgerRefusesMeaninglessRows() {
        assertThatThrownBy(() -> insertLedgerRow(DID_A, DID_B, Ethecoin.ZERO, "trade", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_amount");

        assertThatThrownBy(() -> insertLedgerRow(DID_A, DID_A, Ethecoin.ofWholeEthecoin(1), "trade", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_not_self");
    }

    @Test
    @DisplayName("the ledger is append-only: UPDATE and DELETE are refused by the database")
    void ledgerCannotBeRewritten() {
        insertLedgerRow(DID_A, DID_B, Ethecoin.ofWholeEthecoin(7), "trade", true);

        // Evidence you can edit is not evidence. A reversal is a new row.
        assertThatThrownBy(() -> jdbcClient()
                        .sql("UPDATE ledger_transactions SET amount_wei = 1")
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() ->
                        jdbcClient().sql("DELETE FROM ledger_transactions").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(ledgerCount("SELECT count(*) FROM ledger_transactions", null))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------ constraint 3: provenance

    @Test
    @DisplayName("a chain is readable by item_id as a chain_depth range")
    void provenanceSupportsRangeAccess() {
        UUID itemId = insertItem(DID_A, "vault");
        insertGenesisRecord(itemId);
        for (int depth = 1; depth <= 30; depth++) {
            insertRecord(itemId, depth, "trade");
        }

        // docs/architecture/04 §6.1: the client asks for "records N through N+20" rather than walking
        // from the tip every time.
        List<Integer> window = jdbcClient()
                .sql("""
                        SELECT chain_depth
                          FROM provenance_records
                         WHERE item_id = :itemId
                           AND chain_depth >= :from
                           AND chain_depth < :from + 20
                         ORDER BY chain_depth
                        """)
                .param("itemId", itemId)
                .param("from", 5)
                .query(Integer.class)
                .list();

        assertThat(window).hasSize(20).first().isEqualTo(5);
        assertThat(window).last().isEqualTo(24);
    }

    @Test
    @DisplayName("UNIQUE (item_id, chain_depth) bites: a forked chain cannot be stored")
    void aForkedChainIsRefused() {
        UUID itemId = insertItem(DID_A, "vault");
        insertGenesisRecord(itemId);
        insertRecord(itemId, 1, "trade");

        // Two records at the same position is exactly what a server fabricating history produces.
        assertThatThrownBy(() -> insertRecord(itemId, 1, "server_grant"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_provenance_records_position");
    }

    @Test
    @DisplayName("genesis must agree with itself: depth, previous hash and event type")
    void genesisMarkersMustAgree() {
        UUID itemId = insertItem(DID_A, "vault");

        // Depth 0 with a predecessor is a broken chain (protocol ProvenancePayload enforces the same).
        assertThatThrownBy(() -> insertRecordRaw(itemId, 0, "sha256-" + "0".repeat(64), "initial_mint"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_provenance_records_genesis");

        // Depth > 0 with no predecessor is the same break from the other side.
        assertThatThrownBy(() -> insertRecordRaw(itemId, 3, null, "trade"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_provenance_records_genesis");

        // [PROPOSAL] P-5: genesis is an initial_mint, and an initial_mint appears only at genesis.
        assertThatThrownBy(() -> insertRecordRaw(itemId, 0, null, "trade"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_provenance_records_mint");
    }

    @Test
    @DisplayName("a provenance record cannot reference an item that does not exist")
    void provenanceForeignKeyBites() {
        // ⚠ The constraint is NAMED in the schema so this assertion means something. It used to look
        // for `provenance_records_item_id_fkey` — a name Postgres generated, which H2 spells
        // `CONSTRAINT_66`. Matching a generated name asserts the vendor, not the rule.
        assertThatThrownBy(() -> insertGenesisRecord(UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_provenance_records_item");
    }

    @Test
    @DisplayName("⚠ engine state cannot exist for a character this server does not have — Invariant I14")
    void engineStateRequiresAPlayer() {
        // ⚠ THIS IS WHAT MAKES "A SOLO CHARACTER CAN NEVER FEDERATE" MECHANICAL RATHER THAN STATED.
        //
        // Single player runs the SAME engine against the SAME table, in a local H2 file, and that file
        // is exactly as editable as the JSON save it replaced. So the protection was never the storage
        // format and cannot be: a determined player can write anything they like into their own
        // database. What stops it mattering is that engine state on a SERVER is not free-standing —
        // `character_game_state` references `players`, so a hand-made row has nowhere to land until a
        // `players` row exists, and that goes through CharacterService: a real DID, the allowlist, and
        // the per-account character cap.
        //
        // ⚠ The engine tier deliberately does NOT carry this constraint (V7 has no foreign key) — it
        // is added by core's V8, which single player never runs. That asymmetry IS the design: the
        // engine's state has the same shape everywhere, and only the authority tier says who may own
        // some.
        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO character_game_state (character_id, state, format, updated_at)
                                VALUES (:id, '{}', 1, CURRENT_TIMESTAMP)
                                """)
                        .param("id", UUID.randomUUID())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_character_game_state_player");
    }

    @Test
    @DisplayName("deleting a character takes its engine state with it, rather than orphaning a whole game")
    void deletingAPlayerCascadesToEngineState() {
        UUID playerId = insertPlayer(DID_A);
        jdbcClient()
                .sql("""
                        INSERT INTO character_game_state (character_id, state, format, updated_at)
                        VALUES (:id, '{}', 1, CURRENT_TIMESTAMP)
                        """)
                .param("id", playerId)
                .update();

        jdbcClient().sql("DELETE FROM players WHERE player_id = :id").param("id", playerId).update();

        // ON DELETE CASCADE, not RESTRICT — unlike provenance, which is signed history other servers
        // may still verify against. A character's engine state is nobody else's evidence, and leaving
        // it behind is an orphan row holding somebody's entire game with no way to reach it.
        assertThat(jdbcClient()
                        .sql("SELECT count(*) FROM character_game_state WHERE character_id = :id")
                        .param("id", playerId)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("deleting an item that has provenance is refused, not cascaded")
    void deletingAnItemDoesNotEraseItsHistory() {
        UUID itemId = insertItem(DID_A, "vault");
        insertGenesisRecord(itemId);

        // A signed history other servers may still verify against must not vanish because a row was
        // deleted locally. Destroying an item (Invariant I11) is a state change, not a DELETE.
        assertThatThrownBy(() -> jdbcClient()
                        .sql("DELETE FROM items WHERE item_id = :id")
                        .param("id", itemId)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a provenance record is append-only")
    void provenanceCannotBeRewritten() {
        UUID itemId = insertItem(DID_A, "vault");
        insertGenesisRecord(itemId);

        assertThatThrownBy(() -> jdbcClient()
                        .sql("UPDATE provenance_records SET holder_did = :did")
                        .param("did", DID_C)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("a record must carry at least one signature block")
    void anUnsignedRecordIsRefused() {
        UUID itemId = insertItem(DID_A, "vault");

        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO provenance_records
                                    (record_id, item_id, chain_depth, record_hash, prev_record_hash, event_type,
                                     holder_did, issuer_did, record_version, payload, envelope, signatures,
                                     payload_timestamp)
                                VALUES (:id, :itemId, 0, :hash, NULL, 'initial_mint', :holder, :issuer, 1,
                                        '{}' FORMAT JSON, '{}' FORMAT JSON, '[]' FORMAT JSON, '2026-07-23T18:04:00Z')
                                """)
                        .param("id", UUID.randomUUID())
                        .param("itemId", itemId)
                        .param("hash", "sha256-" + "1".repeat(64))
                        .param("holder", DID_A)
                        .param("issuer", DID_B)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_provenance_records_sigs");
    }

    // ------------------------------------------------------------------ constraint 4: compute

    @Test
    @DisplayName("an allocation names two rigs, and never the same rig twice (Invariant I6)")
    void anAllocationSpansTwoRigs() {
        UUID hostRig = insertRig(insertPlayer(DID_A), 100);
        UUID deployerRig = insertRig(insertPlayer(DID_B), 100);

        // The host's ledger carries the parasite; the deployer's carries the control channel. Two
        // rows, two rigs, never one charge counted twice.
        insertAllocation(hostRig, deployerRig, "deployed_miner", Cycles.of(20));
        insertAllocation(deployerRig, hostRig, "control_channel", Cycles.of(3));

        assertThat(allocatedOn(hostRig)).isEqualTo(20);
        assertThat(allocatedOn(deployerRig)).isEqualTo(3);

        assertThatThrownBy(() -> insertAllocation(hostRig, hostRig, "self_mining", Cycles.of(5)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_compute_allocations_two_rigs");
    }

    @Test
    @DisplayName("a DISCREPANCY between ceiling and allocations is storable — that is the audit signal")
    void aDiscrepancyIsRepresentable() {
        UUID rigId = insertRig(insertPlayer(DID_A), 100);
        UUID otherRig = insertRig(insertPlayer(DID_B), 100);

        insertAllocation(rigId, null, "self_mining", Cycles.of(60));
        insertAllocation(rigId, null, "defensive_array", Cycles.of(25));
        // The hidden parasite. Nothing about the schema stops it being charged past the ceiling.
        insertAllocation(rigId, otherRig, "deployed_miner", Cycles.of(35));

        Long available = jdbcClient()
                .sql("SELECT available_cycles FROM rig_compute_reconciliation WHERE rig_id = :id")
                .param("id", rigId)
                .query(Long.class)
                .single();

        // docs/design/04-mining.md §3.1: "the discrepancy is always present in the data". A CHECK
        // constraint forcing this to reconcile would delete the manual-audit loop.
        assertThat(available).isEqualTo(-20L);
    }

    @Test
    @DisplayName("a recovering allocation carries a recovery time and an active one does not")
    void recoveryMarkersMustAgree() {
        UUID rigId = insertRig(insertPlayer(DID_A), 100);

        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO compute_allocations
                                    (allocation_id, charged_rig_id, consumer_type, allocated_cycles, state)
                                VALUES (:id, :rig, 'active_tool', 35, 'recovering')
                                """)
                        .param("id", UUID.randomUUID())
                        .param("rig", rigId)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_compute_allocations_recovery");
    }

    @Test
    @DisplayName("an allocation cannot be charged to a rig that does not exist")
    void allocationForeignKeyBites() {
        assertThatThrownBy(() -> insertAllocation(UUID.randomUUID(), null, "self_mining", Cycles.of(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ items, storage, gates

    @Test
    @DisplayName("an item is in a storage tier or in a bot, never both and never neither")
    void anItemHasExactlyOneLocation() {
        assertThatCode(() -> insertItem(DID_A, "high_hackable_zone")).doesNotThrowAnyException();

        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO items (item_id, item_type, holder_did, storage_tier, socketed_in)
                                VALUES (:id, 'hacking_tool_tier2', :holder, 'vault', :bot)
                                """)
                        .param("id", UUID.randomUUID())
                        .param("holder", DID_A)
                        .param("bot", UUID.randomUUID())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_items_one_location");

        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO items (item_id, item_type, holder_did, storage_tier, socketed_in)
                                VALUES (:id, 'hacking_tool_tier2', :holder, NULL, NULL)
                                """)
                        .param("id", UUID.randomUUID())
                        .param("holder", DID_A)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_items_one_location");
    }

    @Test
    @DisplayName("jsonb round-trips through the  FORMAT JSON cast the house style requires")
    void jsonbRoundTrips() {
        UUID itemId = UUID.randomUUID();
        Map<String, Object> attrs = Map.of("power", 42, "durability", "0.87");

        jdbcClient()
                .sql("""
                        INSERT INTO items (item_id, item_type, item_attrs, holder_did, storage_tier)
                        VALUES (:id, 'hacking_tool_tier2', :attrs FORMAT JSON, :holder, 'vault')
                        """)
                .param("id", itemId)
                .param("attrs", Jsonb.writeObject(attrs))
                .param("holder", DID_A)
                .update();

        Map<String, Object> readBack = jdbcClient()
                .sql("SELECT item_attrs FROM items WHERE item_id = :id")
                .param("id", itemId)
                .query(RowMappers.of("itemAttrs", row -> Jsonb.objectColumn(row, "item_attrs")))
                .single();

        assertThat(readBack).containsEntry("power", 42).containsEntry("durability", "0.87");
    }

    @Test
    @DisplayName("a jsonb column constrained to an object refuses an array")
    void jsonbShapeConstraintsBite() {
        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO items (item_id, item_type, item_attrs, holder_did, storage_tier)
                                VALUES (:id, 'x', '[1,2,3]' FORMAT JSON, :holder, 'vault')
                                """)
                        .param("id", UUID.randomUUID())
                        .param("holder", DID_A)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_items_attrs_object");
    }

    @Test
    @DisplayName("breach resolutions accept every proposed vocabulary value and refuse anything else")
    void breachVocabularyIsEnforced() {
        for (String puzzleClass : EnumColumns.PUZZLE_CLASS_VALUES) {
            assertThatCode(() -> insertResolution(puzzleClass, 3, "live", "breached"))
                    .as(puzzleClass)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> insertResolution("social_engineering", 3, "live", "breached"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_breach_resolutions_class");

        // The tier scale is 1..5 ([PROPOSAL] P-10). Off-scale values are rejected at the boundary
        // rather than silently clamped, because a clamped tier hands out an unlock the player did not
        // earn (Invariant I7).
        // ⚠ A VALID class, taken from the vocabulary itself. These lines said "logic" — a class V4
        // removed when the puzzle set became breach_protocol/offset_cipher. The row was then refused
        // by ck_breach_resolutions_CLASS, and the assertion on ck_breach_resolutions_TIER passed only
        // because H2 happened to evaluate the tier check first. A test that can pass for the wrong
        // reason is one that will stop failing when the thing it guards breaks.
        assertThatThrownBy(() -> insertResolution(A_PUZZLE_CLASS, 6, "live", "breached"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_breach_resolutions_tier");
        assertThatThrownBy(() -> insertResolution(A_PUZZLE_CLASS, 0, "live", "breached"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_breach_resolutions_tier");
    }

    @Test
    @DisplayName("proof-of-skill reads the highest tier, never a count (Invariants I7, I13)")
    void proofOfSkillIsTierGated() {
        insertResolution(A_PUZZLE_CLASS, 1, "live", "breached");
        insertResolution(A_PUZZLE_CLASS, 1, "live", "breached");
        insertResolution(A_PUZZLE_CLASS, 1, "live", "breached");
        insertResolution(A_PUZZLE_CLASS, 4, "dormant", "breached");
        insertResolution(A_PUZZLE_CLASS, 5, "live", "failed");
        insertResolution(A_PUZZLE_CLASS, 3, "live", "breached");

        // ⚠ The class is BOUND, not written into the SQL. It was the literal 'logic' while the rows
        // above were inserted under a class name a later migration had replaced — so the query
        // matched nothing and `max()` came back NULL. Reading the fixture's own class is what keeps
        // the query and the rows describing the same thing.
        Integer best = jdbcClient()
                .sql("""
                        SELECT max(difficulty_tier)
                          FROM breach_resolutions
                         WHERE player_did = :did
                           AND puzzle_class = :class
                           AND outcome = 'breached'
                           AND live_or_dormant = 'live'
                        """)
                .param("did", DID_A)
                .param("class", A_PUZZLE_CLASS)
                .query(Integer.class)
                .single();

        // Three tier-1 wins do not add up to a tier-3 unlock; a dormant tier-4 and a failed tier-5 do
        // not count at all. That is the whole point of tier-gating.
        assertThat(best).isEqualTo(3);
    }

    // ------------------------------------------------------------------ identity and access

    @Test
    @DisplayName("a malformed DID is refused everywhere it appears")
    void didShapeIsEnforced() {
        assertThatThrownBy(() -> insertPlayer("not-a-did"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_players_did_shape");

        assertThatThrownBy(() -> jdbcClient()
                        .sql("INSERT INTO allowlist_entries (entry_id, did) VALUES (:id, :did)")
                        .param("id", UUID.randomUUID())
                        .param("did", "https://example.test/user")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_allowlist_entries_did_shape");
    }

    @Test
    @DisplayName("a local-only player may exist without a DID, and two of them do not collide")
    void localOnlyPlayersHaveNoDid() {
        // docs/architecture/02 §4 leaves offline identity open, and a UNIQUE column permits many
        // NULLs — so several local players coexist. Note the live consequence: items.holder_did is
        // NOT NULL, so a DID-less player cannot hold a provenanced item until §4 is resolved.
        assertThatCode(() -> {
                    insertPlayer(null);
                    insertPlayer(null);
                })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a DID+slot is unique among characters, and a DID appears at most once in the allowlist")
    void identitiesAreUnique() {
        // A DID is an ACCOUNT that may hold several characters (docs/architecture/09 §1), so the old
        // one-player-per-DID rule (uq_players_did) is gone. Uniqueness is now per (did, slot): the same
        // account cannot occupy the same slot twice. Both helper inserts use slot 1, so the second
        // collides.
        insertPlayer(DID_A);
        assertThatThrownBy(() -> insertPlayer(DID_A))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_players_did_slot");

        insertAllowlistEntry(DID_A);
        assertThatThrownBy(() -> insertAllowlistEntry(DID_A))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_allowlist_entries_did");
    }

    @Test
    @DisplayName("an allowlist revocation must name who revoked it")
    void revocationIsAttributable() {
        insertAllowlistEntry(DID_A);

        // An unattributable moderation action is the one kind an operator will later need to explain.
        assertThatThrownBy(() -> jdbcClient()
                        .sql("UPDATE allowlist_entries SET revoked_at = now() WHERE did = :did")
                        .param("did", DID_A)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_allowlist_entries_revoked_pair");

        assertThatCode(() -> jdbcClient()
                        .sql("""
                                UPDATE allowlist_entries
                                   SET revoked_at = now(), revoked_by_did = :operator
                                 WHERE did = :did
                                """)
                        .param("operator", DID_B)
                        .param("did", DID_A)
                        .update())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("faction reputation exists per named faction and never for 'none'")
    void factionReputationIsPerNamedFaction() {
        UUID playerId = insertPlayer(DID_A);

        // Standing with both sides before the binary commitment (docs/design/01 §5) is exactly what
        // the one-column sketch in docs/architecture/06 §2 could not represent.
        insertFactionReputation(playerId, "eye", -40);
        insertFactionReputation(playerId, "sickle", 120);

        assertThatThrownBy(() -> insertFactionReputation(playerId, "none", 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_faction_reputations_named_faction");
    }

    @Test
    @DisplayName("the server_state row is a singleton")
    void serverStateIsASingleton() {
        assertThatThrownBy(() -> jdbcClient()
                        .sql("INSERT INTO server_state (only_row) VALUES (true)")
                        .update())
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(jdbcClient()
                        .sql("SELECT count(*) FROM server_state")
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------ deployed miners

    @Test
    @DisplayName("a host reference must match the host type in both directions")
    void deployedMinerHostReferencesAreTyped() {
        UUID hostRig = insertRig(insertPlayer(DID_A), 100);

        assertThatCode(() -> insertDeployedMiner(DID_B, "player", hostRig, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> insertDeployedMiner(DID_B, "npc", null, UUID.randomUUID()))
                .doesNotThrowAnyException();

        // A player-hosted miner with no host rig, or an NPC-hosted one pointing at a rig, is a row
        // whose Invariant I6 accounting cannot be reconstructed.
        assertThatThrownBy(() -> insertDeployedMiner(DID_B, "player", null, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDeployedMiner(DID_B, "npc", hostRig, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a yield buffer cannot go negative and the cap must be positive")
    void bufferBoundsAreEnforced() {
        UUID hostRig = insertRig(insertPlayer(DID_A), 100);
        UUID minerId = insertDeployedMiner(DID_B, "player", hostRig, null);

        assertThatThrownBy(() -> jdbcClient()
                        .sql("UPDATE deployed_miners SET buffer_wei = -1 WHERE miner_id = :id")
                        .param("id", minerId)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_deployed_miners_buffer");

        // A zero-hour cap would silently remove the only offline income source (Invariant I5).
        assertThatThrownBy(() -> jdbcClient()
                        .sql("UPDATE deployed_miners SET buffer_cap_hours = 0 WHERE miner_id = :id")
                        .param("id", minerId)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_deployed_miners_cap");
    }

    // ------------------------------------------------------------------ federation

    @Test
    @DisplayName("a peer's sequence number cannot go backwards")
    void peerSequenceIsMonotonic() {
        UUID peerId = insertPeer(DID_A, 7);

        assertThatCode(() -> updatePeerSequence(peerId, 7)).doesNotThrowAnyException();
        assertThatCode(() -> updatePeerSequence(peerId, 8)).doesNotThrowAnyException();

        // Replaying an older signed self-descriptor would roll the peer back to a retired transport
        // key — a downgrade attack that otherwise looks like a normal directory refresh.
        assertThatThrownBy(() -> updatePeerSequence(peerId, 6))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("must not go backwards");
    }

    @Test
    @DisplayName("a peer endpoint must be an http(s) URL and its transport key a plausible length")
    void peerFieldsAreValidated() {
        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO federation_peers
                                    (peer_id, peer_did, endpoint_url, transport_public_key, self_descriptor,
                                     sequence_number)
                                VALUES (:id, :did, 'ftp://example.test', :key, '{}' FORMAT JSON, 1)
                                """)
                        .param("id", UUID.randomUUID())
                        .param("did", DID_B)
                        .param("key", new byte[44])
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_federation_peers_endpoint");

        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO federation_peers
                                    (peer_id, peer_did, endpoint_url, transport_public_key, self_descriptor,
                                     sequence_number)
                                VALUES (:id, :did, 'https://example.test', :key, '{}' FORMAT JSON, 1)
                                """)
                        .param("id", UUID.randomUUID())
                        .param("did", DID_B)
                        .param("key", new byte[8])
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_federation_peers_key");
    }

    @Test
    @DisplayName("validator reputation and uptime are bounded to [0, 1]")
    void validatorScoresAreBounded() {
        assertThatCode(() -> insertValidator(DID_A, "0.40", "1.0")).doesNotThrowAnyException();

        assertThatThrownBy(() -> insertValidator(DID_B, "1.5", "1.0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_validators_reputation");
        assertThatThrownBy(() -> insertValidator(DID_C, "0.4", "-0.1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_validators_uptime");
    }

    @Test
    @DisplayName("a duel's committee size must match its recorded sample")
    void duelCommitteeIsSelfConsistent() {
        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO duels (duel_id, participants, sampled_validators, committee_size)
                                VALUES (:id, :participants FORMAT JSON, :sample FORMAT JSON, 7)
                                """)
                        .param("id", UUID.randomUUID())
                        .param("participants", Jsonb.writeArray(List.of(DID_A, DID_B)))
                        .param("sample", Jsonb.writeArray(List.of(Map.of("did", DID_C))))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_duels_committee_size");
    }

    @Test
    @DisplayName("a server carries at most one live non-recognition flag")
    void flagsDoNotStack() {
        insertFlag(DID_B, "equivocation");
        assertThatThrownBy(() -> insertFlag(DID_B, "fraudulent mint"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_flagged_servers_active");
    }

    // ------------------------------------------------------------------ optimistic concurrency

    @Test
    @DisplayName("a version-checked update on a stale version matches nothing and is reported as a conflict")
    void optimisticConcurrencyIsDetectable() {
        UUID playerId = insertPlayer(DID_A);

        int first = updateBalance(playerId, 5_000L, 0L);
        assertThat(first).isEqualTo(1);

        // The second writer still believes it holds version 0 — the classic lost update, which on this
        // table would be a player spending the same ethecoin twice.
        int second = updateBalance(playerId, 9_000L, 0L);
        assertThat(second).isZero();

        assertThatThrownBy(() -> Mutations.requireUpdated(second, "players", playerId))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(jdbcClient()
                        .sql("SELECT ethecoin_balance_wei FROM players WHERE player_id = :id")
                        .param("id", playerId)
                        .query(Long.class)
                        .single())
                .isEqualTo(5_000L);
    }

    @Test
    @DisplayName("a balance cannot be driven negative")
    void balancesNeverGoNegative() {
        UUID playerId = insertPlayer(DID_A);

        assertThatThrownBy(() -> updateBalance(playerId, -1L, 0L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_players_balance_non_negative");
    }

    // ------------------------------------------------------------------ helpers

    private List<String> tableNames() {
        return jdbcClient()
                .sql(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")
                .query(String.class)
                .list();
    }

    private List<String> columnsOf(String table) {
        return jdbcClient().sql("""
                        SELECT column_name
                          FROM information_schema.columns
                         WHERE table_schema = 'public' AND table_name = :table
                        """).param("table", table).query(String.class).list();
    }

    private long ledgerCount(String sql, String did) {
        var spec = jdbcClient().sql(sql);
        if (did != null) {
            spec = spec.param("did", did);
        }
        return spec.query(Long.class).single();
    }

    private void insertLedgerRow(String from, String to, Ethecoin amount, String type, boolean traceable) {
        jdbcClient()
                .sql("""
                        INSERT INTO ledger_transactions (tx_id, from_did, to_did, amount_wei, tx_type, traceable)
                        VALUES (:id, :from, :to, :amount, :type, :traceable)
                        """)
                .param("id", UUID.randomUUID())
                .param("from", from)
                .param("to", to)
                .param("amount", EconomyColumns.ethecoinValue("amount_wei", amount))
                .param("type", type)
                .param("traceable", traceable)
                .update();
    }

    private void insertAllowlistEntry(String did) {
        jdbcClient()
                .sql("INSERT INTO allowlist_entries (entry_id, did) VALUES (:id, :did)")
                .param("id", UUID.randomUUID())
                .param("did", did)
                .update();
    }

    private UUID insertPlayer(String did) {
        UUID playerId = UUID.randomUUID();
        jdbcClient()
                // slot pairs with did (the (did IS NULL) = (slot IS NULL) check, V3): a DID-bound
                // character gets slot 1, a local (DID-less) one gets NULL. Reusing slot 1 makes two
                // inserts of the SAME DID collide on uq_players_did_slot (identitiesAreUnique relies on
                // that), while two DID-less inserts both get (NULL, NULL) and coexist.
                .sql("INSERT INTO players (player_id, did, slot, handle) VALUES (:id, :did, :slot, :handle)")
                .param("id", playerId)
                .param("did", did)
                .param("slot", did == null ? null : 1)
                .param("handle", "operator")
                .update();
        return playerId;
    }

    private void insertFactionReputation(UUID playerId, String faction, long standing) {
        jdbcClient()
                .sql("""
                        INSERT INTO faction_reputations (player_id, faction, standing)
                        VALUES (:player, :faction, :standing)
                        """)
                .param("player", playerId)
                .param("faction", faction)
                .param("standing", standing)
                .update();
    }

    private UUID insertRig(UUID playerId, long totalCycles) {
        UUID rigId = UUID.randomUUID();
        jdbcClient()
                .sql("""
                        INSERT INTO rigs (rig_id, player_id, total_cycles, bandwidth, memory_buffer)
                        VALUES (:id, :player, :cycles, 4, 6)
                        """)
                .param("id", rigId)
                .param("player", playerId)
                .param("cycles", EconomyColumns.cyclesValue("total_cycles", Cycles.of(totalCycles)))
                .update();
        return rigId;
    }

    private void insertAllocation(UUID chargedRig, UUID counterpartyRig, String consumerType, Cycles cycles) {
        jdbcClient()
                .sql("""
                        INSERT INTO compute_allocations
                            (allocation_id, charged_rig_id, counterparty_rig_id, consumer_type,
                             allocated_cycles, state)
                        VALUES (:id, :charged, :counterparty, :consumer, :cycles, 'active')
                        """)
                .param("id", UUID.randomUUID())
                .param("charged", chargedRig)
                .param("counterparty", counterpartyRig)
                .param("consumer", consumerType)
                .param("cycles", EconomyColumns.cyclesValue("allocated_cycles", cycles))
                .update();
    }

    private long allocatedOn(UUID rigId) {
        return jdbcClient()
                .sql("SELECT active_cycles FROM rig_compute_reconciliation WHERE rig_id = :id")
                .param("id", rigId)
                .query(Long.class)
                .single();
    }

    private UUID insertItem(String holderDid, String storageTier) {
        UUID itemId = UUID.randomUUID();
        jdbcClient()
                .sql("""
                        INSERT INTO items (item_id, item_type, holder_did, storage_tier)
                        VALUES (:id, 'hacking_tool_tier2', :holder, :tier)
                        """)
                .param("id", itemId)
                .param("holder", holderDid)
                .param("tier", storageTier)
                .update();
        return itemId;
    }

    private void insertGenesisRecord(UUID itemId) {
        insertRecordRaw(itemId, 0, null, "initial_mint");
    }

    private void insertRecord(UUID itemId, int depth, String eventType) {
        insertRecordRaw(itemId, depth, "sha256-" + String.format("%064x", depth), eventType);
    }

    private void insertRecordRaw(UUID itemId, int depth, String prevHash, String eventType) {
        jdbcClient()
                .sql("""
                        INSERT INTO provenance_records
                            (record_id, item_id, chain_depth, record_hash, prev_record_hash, event_type,
                             holder_did, issuer_did, record_version, payload, envelope, signatures,
                             payload_timestamp)
                        VALUES (:id, :itemId, :depth, :hash, :prevHash, :eventType, :holder, :issuer, 1,
                                :payload FORMAT JSON, :envelope FORMAT JSON, :signatures FORMAT JSON, '2026-07-23T18:04:00Z')
                        """)
                .param("id", UUID.randomUUID())
                .param("itemId", itemId)
                .param("depth", depth)
                .param("hash", "sha256-" + UUID.randomUUID().toString().replace("-", "") + "0".repeat(32))
                .param("prevHash", prevHash)
                .param("eventType", eventType)
                .param("holder", DID_A)
                .param("issuer", DID_B)
                .param("payload", Jsonb.writeObject(Map.of("itemId", itemId.toString(), "chainDepth", depth)))
                .param("envelope", Jsonb.writeObject(Map.of("payloadCanonicalization", "JCS-RFC8785")))
                .param("signatures", Jsonb.writeArray(List.of(Map.of("alg", "EdDSA", "kid", DID_B + "#key1"))))
                .update();
    }

    private void insertResolution(String puzzleClass, int tier, String targetState, String outcome) {
        jdbcClient()
                .sql("""
                        INSERT INTO breach_resolutions
                            (resolution_id, player_did, puzzle_class, difficulty_tier, live_or_dormant, outcome)
                        VALUES (:id, :did, :class, :tier, :target, :outcome)
                        """)
                .param("id", UUID.randomUUID())
                .param("did", DID_A)
                .param("class", puzzleClass)
                .param("tier", tier)
                .param("target", targetState)
                .param("outcome", outcome)
                .update();
    }

    private UUID insertDeployedMiner(String deployerDid, String hostType, UUID hostRigId, UUID hostNpcRef) {
        UUID minerId = UUID.randomUUID();
        jdbcClient()
                .sql("""
                        INSERT INTO deployed_miners
                            (miner_id, deployer_did, host_type, host_rig_id, host_npc_ref, tier, buffer_cap_hours)
                        VALUES (:id, :deployer, :hostType, :hostRig, :hostNpc, 't2', :cap)
                        """)
                .param("id", minerId)
                .param("deployer", deployerDid)
                .param("hostType", hostType)
                .param("hostRig", hostRigId)
                .param("hostNpc", hostNpcRef)
                .param("cap", PersistenceProperties.DEFAULT_YIELD_BUFFER_CAP_HOURS)
                .update();
        return minerId;
    }

    private UUID insertPeer(String peerDid, long sequenceNumber) {
        UUID peerId = UUID.randomUUID();
        jdbcClient()
                .sql("""
                        INSERT INTO federation_peers
                            (peer_id, peer_did, endpoint_url, transport_public_key, self_descriptor, sequence_number)
                        VALUES (:id, :did, 'https://peer.example.test', :key, :descriptor FORMAT JSON, :sequence)
                        """)
                .param("id", peerId)
                .param("did", peerDid)
                .param("key", new byte[44])
                .param("descriptor", Jsonb.writeObject(Map.of("endpoint", "https://peer.example.test")))
                .param("sequence", sequenceNumber)
                .update();
        return peerId;
    }

    private void updatePeerSequence(UUID peerId, long sequenceNumber) {
        jdbcClient()
                .sql("UPDATE federation_peers SET sequence_number = :sequence WHERE peer_id = :id")
                .param("sequence", sequenceNumber)
                .param("id", peerId)
                .update();
    }

    private void insertValidator(String did, String reputation, String uptime) {
        jdbcClient()
                .sql("""
                        INSERT INTO validators (validator_did, validator_reputation, uptime)
                        VALUES (:did, CAST(:reputation AS numeric), CAST(:uptime AS numeric))
                        """)
                .param("did", did)
                .param("reputation", reputation)
                .param("uptime", uptime)
                .update();
    }

    private void insertFlag(String serverDid, String reason) {
        jdbcClient()
                .sql("INSERT INTO flagged_servers (flag_id, server_did, reason) VALUES (:id, :did, :reason)")
                .param("id", UUID.randomUUID())
                .param("did", serverDid)
                .param("reason", reason)
                .update();
    }

    private int updateBalance(UUID playerId, long minorUnits, long expectedVersion) {
        return jdbcClient()
                .sql("""
                        UPDATE players
                           SET ethecoin_balance_wei = :balance,
                               row_version = row_version + 1
                         WHERE player_id = :id
                           AND row_version = :expected
                        """)
                .param("balance", minorUnits)
                .param("id", playerId)
                .param("expected", expectedVersion)
                .update();
    }
}
