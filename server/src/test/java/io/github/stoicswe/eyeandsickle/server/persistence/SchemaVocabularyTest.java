package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reads the migration SQL off the classpath and checks it against the Java side — no database, so it
 * runs in the default {@code mvn verify}.
 *
 * <h2>What this is for</h2>
 *
 * The database vocabularies in {@link EnumColumns} and the {@code CHECK ... IN (...)} lists in the
 * migrations are two spellings of one decision. Nothing but review keeps them equal, and review is
 * exactly what erodes: someone adds a puzzle class to the enum, the build stays green, and the
 * constraint starts rejecting rows at runtime on somebody else's self-hosted server.
 *
 * <p>Catching that here costs no Docker daemon and no seconds, which is the difference between a
 * check that runs on every build and a check that runs when someone remembers to pass {@code -Pit}.
 */
class SchemaVocabularyTest {

    private static final String CORE = "db/migration/core/V2__core_schema.sql";
    private static final String PUZZLE_CLASSES = "db/migration/core/V4__breach_puzzle_classes.sql";
    private static final String SHELL_SESSIONS = "db/migration/core/V5__shell_sessions.sql";
    private static final String ETHECOIN_WEI = "db/migration/core/V6__ethecoin_wei.sql";
    private static final String FEDERATION = "db/migration/federation/V1001__federation_schema.sql";

    private static final String CORE_SQL = read(CORE);
    private static final String PUZZLE_CLASSES_SQL = read(PUZZLE_CLASSES);
    private static final String SHELL_SESSIONS_SQL = read(SHELL_SESSIONS);
    private static final String FEDERATION_SQL = read(FEDERATION);
    private static final String ETHECOIN_WEI_SQL = read(ETHECOIN_WEI);

    // ------------------------------------------------------------------ vocabularies

    @Test
    @DisplayName("players.faction lists exactly the Faction constants")
    void factionVocabularyMatches() {
        assertThat(inList(CORE_SQL, "players", "faction")).isEqualTo(EnumColumns.FACTION_VALUES);
    }

    @Test
    @DisplayName("faction_reputations.faction lists the named factions only")
    void factionReputationVocabularyMatches() {
        assertThat(inList(CORE_SQL, "faction_reputations", "faction")).isEqualTo(EnumColumns.NAMED_FACTION_VALUES);
    }

    @Test
    @DisplayName("items.storage_tier lists exactly the StorageTier constants")
    void storageTierVocabularyMatches() {
        assertThat(inList(CORE_SQL, "items", "storage_tier")).isEqualTo(EnumColumns.STORAGE_TIER_VALUES);
    }

    @Test
    @DisplayName("compute_allocations.consumer_type lists exactly the ComputeConsumer constants")
    void computeConsumerVocabularyMatches() {
        // Invariant I6 lives in this list: control_channel (charged to the deployer) and
        // deployed_miner (charged to the host) are two consumers, never one.
        //
        // ⚠ Read from V5, not V2. V5 rebuilt this constraint to add shell_session, and V2 must not
        // be edited to match — Flyway checksums a migration that has already run, so a "tidied" V2
        // fails every existing deployment on startup instead of migrating it. The effective
        // vocabulary is whatever the LAST migration to touch the constraint declared.
        assertThat(inRebuiltList(SHELL_SESSIONS_SQL, "consumer_type"))
                .isEqualTo(EnumColumns.COMPUTE_CONSUMER_VALUES);
    }

    @Test
    @DisplayName("⚠ V2's original vocabulary is left alone, because Flyway has checksummed it")
    void theBaselineIsNotRewritten() {
        // The tempting "fix" for the test above is to add shell_session to V2 and delete V5. That
        // turns a green build into a startup failure on every server that has already run V2, and
        // the failure is a checksum mismatch that says nothing about what changed. This assertion
        // exists to make that edit fail here instead of there.
        assertThat(inList(CORE_SQL, "compute_allocations", "consumer_type"))
                .doesNotContain("shell_session");
    }

    @Test
    @DisplayName("compute_allocations.state lists exactly the allocation states")
    void allocationStateVocabularyMatches() {
        assertThat(inList(CORE_SQL, "compute_allocations", "state")).isEqualTo(EnumColumns.ALLOCATION_STATE_VALUES);
    }

    @Test
    @DisplayName("provenance_records.event_type lists exactly the four signed event types")
    void provenanceEventVocabularyMatches() {
        assertThat(inList(CORE_SQL, "provenance_records", "event_type"))
                .isEqualTo(EnumColumns.PROVENANCE_EVENT_TYPE_VALUES);
    }

    @Test
    @DisplayName("breach_resolutions lists exactly the puzzle classes, target states and outcomes")
    void breachVocabulariesMatch() {
        // ⚠ Read from V4, not V2. V2's list is the five classes the schema shipped with and it may
        // not be rewritten — a migration that has been applied anywhere is frozen by its checksum —
        // so the CURRENT vocabulary is whatever the last migration to touch the constraint says.
        // Asserting against V2 here would pass forever while the running database rejected rows.
        assertThat(inRebuiltList(PUZZLE_CLASSES_SQL, "puzzle_class")).isEqualTo(EnumColumns.PUZZLE_CLASS_VALUES);
        assertThat(inList(CORE_SQL, "breach_resolutions", "live_or_dormant"))
                .isEqualTo(EnumColumns.TARGET_STATE_VALUES);
        assertThat(inList(CORE_SQL, "breach_resolutions", "outcome")).isEqualTo(EnumColumns.BREACH_OUTCOME_VALUES);
    }

    @Test
    @DisplayName("the ledger's transaction types are exactly the six the data-model doc names")
    void ledgerVocabularyIsClosed() {
        // Deliberately NOT extended with a 'dead_drop' type: an untraceable transfer is
        // traceable = false on the type it actually is, which is what makes it hard to spot rather
        // than trivially filterable (docs/design/01-core-resources.md §2.2).
        assertThat(inList(CORE_SQL, "ledger_transactions", "tx_type"))
                .containsExactlyInAnyOrder(
                        "mining_reward", "trade", "crack_seizure", "raid_loot", "payout_splitter", "purchase");
    }

    @Test
    @DisplayName("deployed miner host types and tiers are closed sets")
    void minerVocabulariesAreClosed() {
        assertThat(inList(CORE_SQL, "deployed_miners", "host_type")).containsExactlyInAnyOrder("npc", "player");
        assertThat(inList(CORE_SQL, "deployed_miners", "tier")).containsExactlyInAnyOrder("t1", "t2", "t3");
        assertThat(inList(CORE_SQL, "deployed_miners", "state"))
                .containsExactlyInAnyOrder("live", "hijacked", "sabotaged", "dead");
    }

    // ------------------------------------------------------------------ constraint 5

    @Test
    @DisplayName("the two reputations never appear in the same migration file")
    void theTwoReputationsStayApart() {
        // docs/architecture/06 §1 constraint 5. A pure-text check, which is the point: it fails on the
        // diff, before anyone needs a database to notice the merge. Comments are stripped first —
        // both files talk about the other's concept at length, precisely to warn about it.
        assertThat(stripComments(CORE_SQL)).doesNotContain("validator_reputation");
        assertThat(stripComments(FEDERATION_SQL)).doesNotContain("faction_reputation", "player_id");

        // And each file does carry that warning, in prose, where someone editing it will read it.
        assertThat(FEDERATION_SQL).contains("NOT faction reputation");
        assertThat(CORE_SQL).contains("NOT validator reputation");
    }

    // ------------------------------------------------------------------ schema hygiene

    @Test
    @DisplayName("every table carries a COMMENT explaining what it is")
    void everyTableIsDocumented() {
        for (String table : tableNames(CORE_SQL)) {
            assertThat(CORE_SQL).as("COMMENT ON TABLE " + table).contains("COMMENT ON TABLE " + table + " IS");
        }
        for (String table : tableNames(FEDERATION_SQL)) {
            assertThat(FEDERATION_SQL).as("COMMENT ON TABLE " + table).contains("COMMENT ON TABLE " + table + " IS");
        }
    }

    @Test
    @DisplayName("timestamps are timestamptz everywhere, never a naive timestamp")
    void timeIsAlwaysZoned() {
        // A `timestamp` column is interpreted against whatever zone the reader happens to be in, and
        // self-hosted servers run in every zone there is.
        Pattern naive = Pattern.compile("\\btimestamp\\b(?!tz)");
        assertThat(naive.matcher(stripComments(CORE_SQL)).find()).isFalse();
        assertThat(naive.matcher(stripComments(FEDERATION_SQL)).find()).isFalse();
    }

    @Test
    @DisplayName("no PostgreSQL ENUM types: vocabularies are text plus a CHECK")
    void noDatabaseEnumTypes() {
        // Enum values are cheap to add and effectively impossible to remove or rename, and half these
        // vocabularies are still [PROPOSAL].
        assertThat(stripComments(CORE_SQL)).doesNotContainIgnoringCase("AS ENUM");
        assertThat(stripComments(FEDERATION_SQL)).doesNotContainIgnoringCase("AS ENUM");
    }

    /**
     * ⚠ The EFFECTIVE schema, not the CREATE TABLE text.
     *
     * <h2>Why this had to learn about ALTER</h2>
     *
     * Ethecoin moved from hundredths to wei in {@code V6}, which both widens the columns to
     * {@code numeric(78,0)} and renames them off the now-false {@code _ec_minor} suffix. A test that
     * only read {@code CREATE TABLE} would still be describing the schema as it was three migrations
     * ago — and it would have passed, silently, while asserting nothing about the columns that
     * actually exist.
     *
     * <p>So later migrations are replayed over the definitions: {@code ALTER COLUMN … TYPE} updates a
     * type and {@code RENAME COLUMN … TO} updates a name. Every future migration is covered by the
     * same mechanism rather than by remembering to edit this test.
     */
    private static List<String[]> effectiveColumns() {
        List<String[]> columns = new ArrayList<>();
        columns.addAll(columnDefinitions(CORE_SQL));
        columns.addAll(columnDefinitions(FEDERATION_SQL));

        for (String sql : List.of(PUZZLE_CLASSES_SQL, SHELL_SESSIONS_SQL, ETHECOIN_WEI_SQL)) {
            String body = stripComments(sql);
            Matcher retyped = Pattern
                    .compile("ALTER\\s+COLUMN\\s+(\\w+)\\s+TYPE\\s+([a-z]+(?:\\s*\\([^)]*\\))?)",
                            Pattern.CASE_INSENSITIVE)
                    .matcher(body);
            while (retyped.find()) {
                String name = retyped.group(1);
                String type = retyped.group(2).replaceAll("\\s+", "");
                columns.replaceAll(c -> c[0].equals(name) ? new String[] {c[0], type} : c);
            }
            Matcher renamed = Pattern
                    .compile("RENAME\\s+COLUMN\\s+(\\w+)\\s+TO\\s+(\\w+)", Pattern.CASE_INSENSITIVE)
                    .matcher(body);
            while (renamed.find()) {
                String from = renamed.group(1);
                String to = renamed.group(2);
                columns.replaceAll(c -> c[0].equals(from) ? new String[] {to, c[1]} : c);
            }
        }
        return columns;
    }

    @Test
    @DisplayName("ethecoin columns are numeric(78,0) and cycles columns are integral")
    void economyColumnTypesFollowTheConvention() {
        List<String[]> columns = effectiveColumns();

        List<String> ethecoin = new ArrayList<>();
        List<String> cycles = new ArrayList<>();
        for (String[] column : columns) {
            if (EconomyColumns.isEthecoinColumn(column[0])) {
                ethecoin.add(column[0]);
                // ⚠ numeric, not bigint. At 18 decimal places a bigint tops out at 9.22 EC — less
                // than one firmware image — so the width is not a preference. Scale 0 keeps it an
                // integral COUNT of the smallest unit, which is what stops the rounding the whole
                // integral model exists to prevent.
                assertThat(column[1])
                        .as("%s must be numeric(78,0): ethecoin is an integral count of wei", column[0])
                        .isEqualTo("numeric(78,0)");
            }
            if (EconomyColumns.isCyclesColumn(column[0])) {
                cycles.add(column[0]);
                assertThat(column[1])
                        .as("%s must be integral: nothing in the design divides a cycle", column[0])
                        .isIn("integer", "bigint");
            }
        }

        // The columns other systems will build against. If one disappears, that is a contract change.
        assertThat(ethecoin)
                .containsExactlyInAnyOrder("ethecoin_balance_wei", "amount_wei", "buffer_wei");
        assertThat(cycles).containsExactlyInAnyOrder("total_cycles", "allocated_cycles");
    }

    @Test
    @DisplayName("every DID column is shape-checked through the one shared function")
    void didShapeHasOneAuthority() {
        assertThat(CORE_SQL).contains("CREATE FUNCTION is_did(value text)");
        // The federation file depends on the core file's function, which only holds because the two
        // migration directories share one history and 2 < 1001. Do not renumber them.
        assertThat(FEDERATION_SQL).contains("is_did(");
    }

    @Test
    @DisplayName("the evidence tables are append-only at the database level")
    void evidenceTablesAreGuarded() {
        assertThat(CORE_SQL)
                .contains("CREATE TRIGGER ledger_transactions_append_only")
                .contains("CREATE TRIGGER provenance_records_append_only")
                // A ROW trigger on UPDATE/DELETE only, so TRUNCATE still works and the test harness
                // needs no privileged escape hatch that would then exist in production too.
                .contains("BEFORE UPDATE OR DELETE ON ledger_transactions");
    }

    @Test
    @DisplayName("the compute ledger has no constraint forcing it to reconcile")
    void theAuditSignalIsNotConstrainedAway() {
        String body = tableBody(CORE_SQL, "compute_allocations");

        // docs/architecture/06 §1 constraint 4 and docs/design/04-mining.md §3.1: a discrepancy
        // between a rig's ceiling and the sum of its allocations IS the manual-audit signal. A CHECK
        // that made the numbers always add up would delete the gameplay by way of a tidy refactor.
        assertThat(body).doesNotContain("total_cycles");
        assertThat(body).doesNotContainIgnoringCase("sum(");
    }

    @Test
    @DisplayName("the provenance position constraint exists and is unique")
    void provenanceRangeAccessIsIndexed() {
        // docs/architecture/04 §6.1: index by item_id, with chain_depth for "records N through N+20".
        // One index does both jobs and additionally forbids a forked chain.
        assertThat(tableBody(CORE_SQL, "provenance_records"))
                .contains("CONSTRAINT uq_provenance_records_position UNIQUE (item_id, chain_depth)");
    }

    // ------------------------------------------------------------------ parsing helpers

    /** The values of a {@code column IN (...)} CHECK, scoped to one table's definition. */
    private static Set<String> inList(String sql, String table, String column) {
        String body = tableBody(sql, table);
        Matcher matcher = Pattern.compile(Pattern.quote(column) + "\\s+IN\\s*\\(([^)]*)\\)")
                .matcher(body);
        assertThat(matcher.find())
                .as("%s.%s should be constrained by a CHECK ... IN (...)", table, column)
                .isTrue();

        Set<String> values = new LinkedHashSet<>();
        for (String literal : matcher.group(1).split(",")) {
            String trimmed = literal.strip();
            if (!trimmed.isEmpty()) {
                assertThat(trimmed).startsWith("'").endsWith("'");
                values.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return values;
    }

    /**
     * The same, for a constraint an {@code ALTER TABLE} migration rebuilt rather than one a
     * {@code CREATE TABLE} declared.
     *
     * <p>Scans the whole file: there is no table body to scope to, and a migration that rebuilds one
     * constraint is short enough that the column name is unambiguous within it. Comments are stripped
     * for the same reason they are stripped from a table body — this schema explains itself at length
     * and a prose mention of the old spellings would otherwise satisfy the assertion by accident.
     */
    private static Set<String> inRebuiltList(String sql, String column) {
        String body = stripComments(sql);
        // ⚠ Anchored on CHECK. A migration that rewrites rows before rebuilding the constraint has a
        // second `column IN (...)` in its UPDATE — the OLD spellings, which is exactly the list this
        // must not read. Without the anchor the assertion happily checks the vocabulary being retired.
        Matcher matcher = Pattern.compile("CHECK\\s*\\(\\s*" + Pattern.quote(column) + "\\s+IN\\s*\\(([^)]*)\\)")
                .matcher(body);
        assertThat(matcher.find())
                .as("%s should be constrained by a CHECK ... IN (...)", column)
                .isTrue();
        Set<String> values = new LinkedHashSet<>();
        for (String literal : matcher.group(1).split(",")) {
            String trimmed = literal.strip();
            if (!trimmed.isEmpty()) {
                assertThat(trimmed).startsWith("'").endsWith("'");
                values.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return values;
    }

    /**
     * The text between {@code CREATE TABLE name (} and the closing {@code );}, with {@code --}
     * comments removed — the comments in this schema explain a lot of design and would otherwise
     * satisfy or defeat these assertions by accident.
     */
    private static String tableBody(String sql, String table) {
        String header = "CREATE TABLE " + table + " (";
        int start = sql.indexOf(header);
        assertThat(start).as("CREATE TABLE %s", table).isNotNegative();
        int end = sql.indexOf("\n);", start);
        assertThat(end).as("closing paren for %s", table).isGreaterThan(start);
        return stripComments(sql.substring(start + header.length(), end));
    }

    private static List<String> tableNames(String sql) {
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("CREATE TABLE (\\w+) \\(").matcher(sql);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        assertThat(names).isNotEmpty();
        return names;
    }

    /** {@code {name, type}} for every four-space-indented column definition. */
    private static List<String[]> columnDefinitions(String sql) {
        List<String[]> columns = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?m)^ {4}([a-z_]+) +([a-z]+)").matcher(stripComments(sql));
        while (matcher.find()) {
            columns.add(new String[] {matcher.group(1), matcher.group(2)});
        }
        return columns;
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("--[^\\n]*", "");
    }

    private static String read(String resource) {
        try (InputStream in = SchemaVocabularyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
