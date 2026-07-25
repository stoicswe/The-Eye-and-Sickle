package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The affected-row-count checks, which exist because ignoring that count is how a race becomes an
 * exploit on this schema.
 */
class MutationsTest {

    @Test
    @DisplayName("exactly one row updated is the only success")
    void oneRowIsSuccess() {
        assertThatCode(() -> Mutations.requireUpdated(1, "players", UUID.randomUUID()))
                .doesNotThrowAnyException();
        assertThatCode(() -> Mutations.requireInserted(1, "ledger_transactions"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("zero rows updated is a concurrency conflict, not a no-op")
    void zeroRowsIsAConflict() {
        UUID playerId = UUID.randomUUID();

        // Two requests that both read a balance of 100, both decide they can afford 60, and both
        // write 40 have produced one item for free. The version check turns the second write into
        // this failure instead of a lost update.
        assertThatThrownBy(() -> Mutations.requireUpdated(0, "players", playerId))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("players")
                .hasMessageContaining(playerId.toString())
                .hasMessageContaining(Mutations.ROW_VERSION);
    }

    @Test
    @DisplayName("more than one row updated means the WHERE clause is wrong")
    void manyRowsIsAProgrammingError() {
        // A version-checked update identifies one row by primary key. Hitting several means it hit
        // rows the caller never reasoned about — a different and worse bug than a lost update.
        assertThatThrownBy(() -> Mutations.requireUpdated(4, "deployed_miners", "network"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("4");
    }

    @Test
    @DisplayName("an insert that wrote nothing is reported, not treated as success")
    void silentInsertsAreReported() {
        // The usual culprit is ON CONFLICT DO NOTHING, whose zero-row result reads as success to
        // every caller that does not look.
        assertThatThrownBy(() -> Mutations.requireInserted(0, "items"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("ON CONFLICT");
    }

    @Test
    @DisplayName("the next row version is the stored version plus one")
    void versionsAdvanceByOne() {
        assertThat(Mutations.nextRowVersion(0L)).isEqualTo(1L);
        assertThat(Mutations.nextRowVersion(41L)).isEqualTo(42L);
    }

    @Test
    @DisplayName("a negative or overflowing row version is refused")
    void versionBoundsAreEnforced() {
        assertThatThrownBy(() -> Mutations.nextRowVersion(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Mutations.ROW_VERSION);

        // A silently wrapped version is worse than a failed request: it looks like a legitimate
        // number to every layer above, and the next conditional update would match the wrong row
        // state. Same reasoning as protocol Ethecoin's overflow behaviour.
        assertThatThrownBy(() -> Mutations.nextRowVersion(Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("the version column name is the one the migrations use")
    void theVersionColumnNameMatchesTheSchema() {
        assertThat(Mutations.ROW_VERSION).isEqualTo("row_version");
    }
}
