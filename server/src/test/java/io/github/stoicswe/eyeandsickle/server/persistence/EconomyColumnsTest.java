package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariant I1 at the persistence boundary: ethecoin and cycles never swap columns.
 *
 * <p>The refusal cases carry the weight here. Reading an ethecoin amount out of an ethecoin column is
 * uninteresting; reading one out of {@code total_cycles} is the mistake that turns compute into
 * something ethecoin can buy, and it is a mistake that compiles.
 */
class EconomyColumnsTest {

    @Test
    @DisplayName("reading ethecoin out of a cycles column is refused, and says why")
    void ethecoinCannotBeReadFromACyclesColumn() {
        Row row = rowOf("total_cycles", 100L);

        assertThatThrownBy(() -> EconomyColumns.ethecoin(row, "total_cycles"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invariant I1")
                .hasMessageContaining("_wei");
    }

    @Test
    @DisplayName("reading cycles out of an ethecoin column is refused, and says why")
    void cyclesCannotBeReadFromAnEthecoinColumn() {
        Row row = rowOf("amount_wei", 4_000L);

        assertThatThrownBy(() -> EconomyColumns.cycles(row, "amount_wei"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invariant I1")
                .hasMessageContaining("_cycles");
    }

    @Test
    @DisplayName("the write side is checked too, not just the read side")
    void bindingIsCheckedAsWell() {
        // Binding amount.wei() straight into a cycles column would compile and would be the
        // exact conversion the invariant forbids, so the check has to exist on both sides.
        assertThatThrownBy(() -> EconomyColumns.ethecoinValue("allocated_cycles", Ethecoin.ofWholeEthecoin(40)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EconomyColumns.cyclesValue("buffer_wei", Cycles.of(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a column that follows neither convention is refused with guidance")
    void unconventionalColumnsAreRefused() {
        Row row = rowOf("compute_cores", 100L);

        assertThatThrownBy(() -> EconomyColumns.cycles(row, "compute_cores"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name new columns with the suffix");
    }

    @Test
    @DisplayName("every ethecoin and cycles column in the schema is named to match")
    void theSchemasColumnsFollowTheConvention() {
        // If a migration ever adds a money or compute column without the suffix, this is where it gets
        // caught — before the helper starts refusing legitimate reads at runtime.
        assertThat(EconomyColumns.isEthecoinColumn("ethecoin_balance_wei")).isTrue();
        assertThat(EconomyColumns.isEthecoinColumn("amount_wei")).isTrue();
        assertThat(EconomyColumns.isEthecoinColumn("buffer_wei")).isTrue();

        assertThat(EconomyColumns.isCyclesColumn("total_cycles")).isTrue();
        assertThat(EconomyColumns.isCyclesColumn("allocated_cycles")).isTrue();
        assertThat(EconomyColumns.isCyclesColumn("active_cycles")).isTrue();
        assertThat(EconomyColumns.isCyclesColumn("recovering_cycles")).isTrue();
        assertThat(EconomyColumns.isCyclesColumn("available_cycles")).isTrue();

        // No column may satisfy both, or the check would pass for the wrong unit.
        assertThat(EconomyColumns.isCyclesColumn("amount_wei")).isFalse();
        assertThat(EconomyColumns.isEthecoinColumn("total_cycles")).isFalse();
    }

    @Test
    @DisplayName("values round-trip through the typed helpers")
    void valuesRoundTrip() {
        // ⚠ A BigDecimal, because the column is now numeric(78,0) and that is what the driver
        // hands back. Feeding a Long here would test a shape the database never produces.
        Row row = rowOf("amount_wei", new java.math.BigDecimal("267000000000000000000"));
        assertThat(EconomyColumns.ethecoin(row, "amount_wei")).isEqualTo(Ethecoin.ofDecimal("267"));

        Row cyclesRow = rowOf("allocated_cycles", 35L);
        assertThat(EconomyColumns.cycles(cyclesRow, "allocated_cycles")).isEqualTo(Cycles.of(35));

        assertThat(EconomyColumns.ethecoinValue("amount_wei", Ethecoin.ofWholeEthecoin(4)))
                .isEqualTo(Ethecoin.WEI_PER_ETHECOIN.multiply(java.math.BigInteger.valueOf(4)));
        assertThat(EconomyColumns.cyclesValue("allocated_cycles", Cycles.of(3))).isEqualTo(3L);
    }

    @Test
    @DisplayName("a NULL ethecoin column is readable only through the nullable accessor")
    void nullableEthecoin() {
        Map<String, Object> values = new HashMap<>();
        values.put("buffer_wei", null);
        Row row = new Row(FakeResultSet.row(values), "DeployedMiner");

        assertThat(EconomyColumns.ethecoinOrNull(row, "buffer_wei")).isNull();
        assertThatThrownBy(() -> EconomyColumns.ethecoin(row, "buffer_wei"))
                .isInstanceOf(RowMappingException.class);
    }

    @Test
    @DisplayName("an over-subscribed rig reads back as a negative figure rather than throwing")
    void overSubscriptionIsObservable() {
        Row row = rowOf("available_cycles", -20L);

        // Cycles refuses negatives on purpose, so an over-subscribed rig cannot be expressed as one.
        // The manual-audit loop (docs/design/04-mining.md §3.1) depends on the discrepancy being
        // readable, not on the read failing.
        assertThat(EconomyColumns.signedCycles(row, "available_cycles")).isEqualTo(-20L);
        assertThatThrownBy(() -> EconomyColumns.cycles(row, "available_cycles"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never negative");
    }

    @Test
    @DisplayName("a null column name is rejected rather than silently passing the suffix check")
    void nullColumnNames() {
        Row row = rowOf("amount_wei", 1L);

        assertThatThrownBy(() -> EconomyColumns.ethecoin(row, null)).isInstanceOf(NullPointerException.class);
        assertThat(EconomyColumns.isEthecoinColumn(null)).isFalse();
        assertThat(EconomyColumns.isCyclesColumn(null)).isFalse();
    }

    private static Row rowOf(String column, Object value) {
        return new Row(FakeResultSet.singleColumn(column, value), "TestRecord");
    }
}
