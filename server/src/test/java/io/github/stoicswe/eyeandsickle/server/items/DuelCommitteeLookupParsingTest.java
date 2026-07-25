package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link DuelCommitteeLookupJdbc#parseCommittee(String, String)} — extracting a frozen sampling record
 * from the {@code duels.sampled_validators} jsonb ({@code docs/architecture/05} §2). Pure logic over a
 * parsed array, so it is unit-tested with no database; the SQL read is exercised separately in {@code
 * DuelCommitteeLookupJdbcIT}.
 *
 * <p>The weight extraction is the part that matters: a snapshot weight is authoritative when present,
 * and reconstructed as {@code reputation × uptime} only for records written before the weight was
 * materialised. Getting it wrong silently re-weights an old duel.
 */
class DuelCommitteeLookupParsingTest {

    private static QuorumCommittee parse(String sampledValidatorsJson) {
        return DuelCommitteeLookupJdbc.parseCommittee("duel-1", sampledValidatorsJson);
    }

    // ------------------------------------------------------------------ weights

    @Nested
    @DisplayName("weight extraction")
    class Weights {

        @Test
        @DisplayName("uses the snapshot weight when it is present")
        void snapshotWeightUsedDirectly() {
            QuorumCommittee committee = parse("[{\"did\":\"did:plc:v1\",\"weight\":2.5}]");

            assertThat(committee.weightOf("did:plc:v1")).isEqualTo(2.5);
        }

        @Test
        @DisplayName("falls back to reputation x uptime when no weight was materialised")
        void fallsBackToReputationTimesUptime() {
            QuorumCommittee committee = parse("[{\"did\":\"did:plc:v1\",\"reputation\":0.5,\"uptime\":0.8}]");

            // The sampling weight's own definition (05 §2.2), reconstructed for an older snapshot.
            assertThat(committee.weightOf("did:plc:v1")).isCloseTo(0.4, within(1e-9));
        }

        @Test
        @DisplayName("prefers an explicit weight over reputation x uptime")
        void explicitWeightWins() {
            QuorumCommittee committee =
                    parse("[{\"did\":\"did:plc:v1\",\"reputation\":0.5,\"uptime\":0.8,\"weight\":3.0}]");

            assertThat(committee.weightOf("did:plc:v1")).isEqualTo(3.0);
        }

        @Test
        @DisplayName("parses a multi-validator committee, preserving each weight")
        void multipleValidators() {
            QuorumCommittee committee =
                    parse("[{\"did\":\"did:plc:v1\",\"weight\":1.0},{\"did\":\"did:plc:v2\",\"weight\":2.0}]");

            assertThat(committee.size()).isEqualTo(2);
            assertThat(committee.wasSampled("did:plc:v1")).isTrue();
            assertThat(committee.wasSampled("did:plc:v2")).isTrue();
            assertThat(committee.totalWeight()).isEqualTo(3.0);
        }
    }

    // ------------------------------------------------------------------ malformed snapshots

    @Nested
    @DisplayName("a malformed snapshot is refused")
    class Malformed {

        @Test
        @DisplayName("an empty committee is refused — a sampled committee has at least one validator")
        void emptyArrayRejected() {
            assertThatThrownBy(() -> parse("[]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("a non-object entry is refused")
        void nonObjectEntryRejected() {
            assertThatThrownBy(() -> parse("[\"did:plc:v1\"]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not an object");
        }

        @Test
        @DisplayName("an entry with no did is refused")
        void missingDidRejected() {
            assertThatThrownBy(() -> parse("[{\"weight\":1}]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("did");
        }

        @Test
        @DisplayName("a blank did is refused")
        void blankDidRejected() {
            assertThatThrownBy(() -> parse("[{\"did\":\"\",\"weight\":1}]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("did");
        }

        @Test
        @DisplayName("the same validator sampled twice is refused — it would double-count weight")
        void duplicateValidatorRejected() {
            assertThatThrownBy(() ->
                            parse("[{\"did\":\"did:plc:v1\",\"weight\":1},{\"did\":\"did:plc:v1\",\"weight\":2}]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("twice");
        }

        @Test
        @DisplayName("an entry with neither weight nor reputation/uptime is refused")
        void noWeightAndNoDerivationRejected() {
            assertThatThrownBy(() -> parse("[{\"did\":\"did:plc:v1\"}]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("weight");
        }

        @Test
        @DisplayName("a negative weight is refused by the committee itself")
        void negativeWeightRejected() {
            // QuorumCommittee requires every weight finite and non-negative.
            assertThatThrownBy(() -> parse("[{\"did\":\"did:plc:v1\",\"weight\":-1.0}]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }
    }
}
