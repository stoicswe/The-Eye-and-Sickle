package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

/**
 * The failure cases of {@link Row} and {@link RowMappers}, which are the reason both exist.
 *
 * <p>A mapper that reads the right value from the right column is not what breaks; what breaks is a
 * mapper that keeps working after somebody renames a column or makes one nullable, and hands back a
 * plausible zero. Each test here is one of those.
 */
class RowTest {

    private static final String DESCRIPTION = "TestRecord";

    @Test
    @DisplayName("a renamed or unselected column fails immediately, naming the column and the mapper")
    void aMissingColumnIsLoud() {
        Row row = rowOf(Map.of("total_cycles", 100));

        assertThatThrownBy(() -> row.int64("compute_cores"))
                .isInstanceOf(RowMappingException.class)
                .hasMessageContaining("compute_cores")
                .hasMessageContaining(DESCRIPTION);
    }

    @Test
    @DisplayName("a NULL in a required numeric column is not read as zero")
    void nullIsNotZero() {
        // The JDBC quirk this guards: getLong returns 0 for SQL NULL. On this schema a silent zero is
        // an empty wallet, a cold player, or an unallocated rig — all entirely plausible downstream.
        Row row = rowOf(withNull("ethecoin_balance_ec_minor"));

        assertThatThrownBy(() -> row.int64("ethecoin_balance_ec_minor"))
                .isInstanceOf(RowMappingException.class)
                .hasMessageContaining("was NULL")
                .hasMessageContaining("OrNull");
    }

    @Test
    @DisplayName("a NULL in a required int column is not read as zero either")
    void nullIsNotZeroForInt32() {
        Row row = rowOf(withNull("difficulty_tier"));

        assertThatThrownBy(() -> row.int32("difficulty_tier")).isInstanceOf(RowMappingException.class);
    }

    @Test
    @DisplayName("a NULL in a required boolean column is not read as false")
    void nullIsNotFalse() {
        // False is the safe-looking value for rootkit_wrapped and the dangerous one: it would mean a
        // hidden miner is reported as visible.
        Row row = rowOf(withNull("rootkit_wrapped"));

        assertThatThrownBy(() -> row.bool("rootkit_wrapped")).isInstanceOf(RowMappingException.class);
    }

    @Test
    @DisplayName("the *OrNull accessors permit a NULL and return it")
    void nullableAccessorsReturnNull() {
        Map<String, Object> values = new HashMap<>();
        values.put("from_did", null);
        values.put("counterparty_rig_id", null);
        values.put("recovers_at", null);
        values.put("buffer_ec_minor", null);
        Row row = rowOf(values);

        assertThat(row.textOrNull("from_did")).isNull();
        assertThat(row.uuidOrNull("counterparty_rig_id")).isNull();
        assertThat(row.instantOrNull("recovers_at")).isNull();
        assertThat(row.int64OrNull("buffer_ec_minor")).isNull();
    }

    @Test
    @DisplayName("timestamptz is read through OffsetDateTime, not the JVM's default zone")
    void timestampsAreZoneSafe() {
        OffsetDateTime stored = OffsetDateTime.of(2026, 7, 23, 18, 4, 0, 0, ZoneOffset.UTC);
        Row row = rowOf(Map.of("created_at", stored));

        // A java.sql.Timestamp read would be interpreted against the JVM's zone, so the same column
        // would yield different instants on a laptop and in a container. Self-hosted servers run in
        // every zone there is.
        assertThat(row.instant("created_at")).isEqualTo(Instant.parse("2026-07-23T18:04:00Z"));
    }

    @Test
    @DisplayName("typed values round-trip")
    void typedValuesRoundTrip() {
        UUID id = UUID.randomUUID();
        Row row = rowOf(Map.of(
                "player_id",
                id,
                "handle",
                "operator",
                "personal_heat",
                new BigDecimal("12.5000"),
                "traceable",
                true,
                "transport_public_key",
                new byte[] {1, 2, 3},
                "item_attrs",
                "{\"power\":42}"));

        assertThat(row.uuid("player_id")).isEqualTo(id);
        assertThat(row.text("handle")).isEqualTo("operator");
        assertThat(row.decimal("personal_heat")).isEqualByComparingTo("12.5");
        assertThat(row.bool("traceable")).isTrue();
        assertThat(row.bytes("transport_public_key")).containsExactly(1, 2, 3);
        assertThat(row.json("item_attrs")).isEqualTo("{\"power\":42}");
    }

    @Test
    @DisplayName("numeric is read as BigDecimal, so a threshold comparison is exact")
    void numericIsExact() {
        Row row = rowOf(Map.of("validator_reputation", new BigDecimal("0.10000000")));

        // 0.1 has no exact binary representation, so two servers summing weighted power in different
        // orders would disagree about whether a quorum threshold was cleared — and in a federation, a
        // disagreement about a threshold is indistinguishable from cheating.
        assertThat(row.decimal("validator_reputation")).isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    @DisplayName("RowMappers wires a lambda into a Spring RowMapper")
    void rowMapperDelegates() throws SQLException {
        RowMapper<String> mapper = RowMappers.of("handle", row -> row.text("handle"));
        ResultSet resultSet = FakeResultSet.singleColumn("handle", "operator");

        assertThat(mapper.mapRow(resultSet, 1)).isEqualTo("operator");
    }

    @Test
    @DisplayName("a mapper named after its record says so when it fails")
    void mapperDescriptionUsesTheRecordName() {
        RowMapper<UUID> mapper = RowMappers.of(UUID.class, row -> row.uuid("absent"));
        ResultSet resultSet = FakeResultSet.singleColumn("present", UUID.randomUUID());

        assertThatThrownBy(() -> mapper.mapRow(resultSet, 1))
                .isInstanceOf(RowMappingException.class)
                .hasMessageContaining("UUID")
                .hasMessageContaining("absent");
    }

    private static Row rowOf(Map<String, Object> values) {
        return new Row(FakeResultSet.row(values), DESCRIPTION);
    }

    private static Map<String, Object> withNull(String column) {
        Map<String, Object> values = new HashMap<>();
        values.put(column, null);
        return values;
    }
}
