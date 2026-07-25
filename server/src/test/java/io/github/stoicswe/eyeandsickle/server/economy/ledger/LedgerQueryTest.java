package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.server.economy.ledger.LedgerQuery.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The investigator's filters. The viewer is deliberately NOT one of them — who is asking is bound from
 * the authenticated principal and passed to the repository separately, so that a client-chosen viewer
 * can never claim to be a counterparty and read another player's Dead Drops (Invariant I14). These
 * tests pin the factory shapes and the limit guard.
 */
class LedgerQueryTest {

    private static final String A = "did:plc:aaaa000000000000000000";
    private static final String B = "did:plc:bbbb000000000000000000";

    @Nested
    @DisplayName("factories")
    class Factories {

        @Test
        @DisplayName("recent() is the global feed: no participant, no counterparty, either direction, any type")
        void recent() {
            LedgerQuery query = LedgerQuery.recent(25);
            assertThat(query.participantDid()).isEmpty();
            assertThat(query.counterpartyDid()).isEmpty();
            assertThat(query.direction()).isEqualTo(Direction.EITHER);
            assertThat(query.typeFilter()).isEmpty();
            assertThat(query.limit()).isEqualTo(25);
        }

        @Test
        @DisplayName("forParticipant() narrows to one subject on a chosen side")
        void forParticipant() {
            LedgerQuery query = LedgerQuery.forParticipant(A, Direction.SENT, 10);
            assertThat(query.participantDid()).contains(A);
            assertThat(query.counterpartyDid()).isEmpty();
            assertThat(query.direction()).isEqualTo(Direction.SENT);
        }

        @Test
        @DisplayName("between() pins both ends of a flow, matched in either direction between them")
        void between() {
            LedgerQuery query = LedgerQuery.between(A, B, 10);
            assertThat(query.participantDid()).contains(A);
            assertThat(query.counterpartyDid()).contains(B);
            assertThat(query.direction()).isEqualTo(Direction.EITHER);
        }

        @Test
        @DisplayName("ofType() returns a copy restricted to one type, leaving the rest intact")
        void ofType() {
            LedgerQuery base = LedgerQuery.forParticipant(A, Direction.RECEIVED, 10);
            LedgerQuery restricted = base.ofType(LedgerEntryType.CRACK_SEIZURE);

            assertThat(restricted.typeFilter()).contains(LedgerEntryType.CRACK_SEIZURE);
            assertThat(restricted.participantDid()).contains(A);
            assertThat(restricted.direction()).isEqualTo(Direction.RECEIVED);
            assertThat(base.typeFilter()).as("the original is untouched").isEmpty();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("a non-positive limit is rejected")
        void nonPositiveLimitRejected() {
            assertThatThrownBy(() -> LedgerQuery.recent(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit must be positive");
            assertThatThrownBy(() -> LedgerQuery.recent(-1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a null direction is rejected")
        void nullDirectionRejected() {
            assertThatThrownBy(() -> new LedgerQuery(A, B, null, null, 10)).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("the page-size constants are the documented defaults")
    void constants() {
        assertThat(LedgerQuery.DEFAULT_LIMIT).isEqualTo(50);
        assertThat(LedgerQuery.MAX_LIMIT).isEqualTo(200);
    }
}
