package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The {@code flagged_servers} registry against a real PostgreSQL — the partial unique index that
 * allows at most one active flag per server, the idempotent flag it enables, and the auditable clear
 * that never deletes a flag ({@code docs/architecture/03} §4).
 */
class FlaggedServerRegistryIT extends PostgresIntegrationTestBase {

    private static final String SERVER = "did:plc:rogueserver00000000a";
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-24T13:00:00Z");

    private FlaggedServerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new FlaggedServerRegistry(jdbcClient());
    }

    @Nested
    @DisplayName("raising a flag")
    class Raise {

        @Test
        @DisplayName("makes the server non-recognised and self-consistent across the query surface")
        void flagsServer() {
            FlaggedServer flag = registry.flag(SERVER, FlaggedServer.REASON_EQUIVOCATION, Map.of(), null, NOW);

            assertThat(flag.isActive()).isTrue();
            assertThat(registry.isFlagged(SERVER)).isTrue();
            assertThat(registry.findActive(SERVER)).map(FlaggedServer::flagId).contains(flag.flagId());
            assertThat(registry.find(flag.flagId())).isPresent();
            assertThat(registry.listActive()).extracting(FlaggedServer::flagId).containsExactly(flag.flagId());
        }

        @Test
        @DisplayName("is idempotent — raising the same active flag twice settles to one flag")
        void idempotent() {
            FlaggedServer first = registry.flag(SERVER, FlaggedServer.REASON_EQUIVOCATION, Map.of(), null, NOW);
            // Two peers detecting the same equivocation must not produce two active flags: one could be
            // cleared while the other silently kept the server non-recognised.
            FlaggedServer second = registry.flag(SERVER, FlaggedServer.REASON_EQUIVOCATION, Map.of(), null, LATER);

            assertThat(second.flagId()).isEqualTo(first.flagId());
            assertThat(registry.listActive()).hasSize(1);
        }

        @Test
        @DisplayName("refuses a malformed server DID")
        void refusesMalformedDid() {
            assertThatThrownBy(() -> registry.flag("not-a-did", "spam", Map.of(), null, NOW))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("refuses a blank reason")
        void refusesBlankReason() {
            assertThatThrownBy(() -> registry.flag(SERVER, "   ", Map.of(), null, NOW))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("clearing a flag")
    class Clear {

        @Test
        @DisplayName("restores recognition but keeps the flag row for audit")
        void clearsButRetains() {
            FlaggedServer flag = registry.flag(SERVER, FlaggedServer.REASON_EQUIVOCATION, Map.of(), null, NOW);

            registry.clear(SERVER, "manually reviewed and reinstated", LATER);

            assertThat(registry.isFlagged(SERVER)).isFalse();
            assertThat(registry.findActive(SERVER)).isEmpty();
            // The row is not deleted — "why did we un-ignore that server" must stay answerable.
            assertThat(registry.find(flag.flagId())).isPresent();
            assertThat(registry.find(flag.flagId()).orElseThrow().isActive()).isFalse();
        }

        @Test
        @DisplayName("fails loudly when there is no active flag to clear")
        void failsWhenNothingActive() {
            // requireUpdated turns "no active flag" into a failure rather than a silent no-op the caller
            // would read as success.
            assertThatThrownBy(() -> registry.clear(SERVER, "note", NOW))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("lets a server be flagged again after a clear")
        void reflaggableAfterClear() {
            FlaggedServer first = registry.flag(SERVER, FlaggedServer.REASON_EQUIVOCATION, Map.of(), null, NOW);
            registry.clear(SERVER, "reinstated", LATER);

            FlaggedServer second = registry.flag(SERVER, FlaggedServer.REASON_EQUIVOCATION, Map.of(), null, LATER);

            // The partial unique index only forbids two ACTIVE flags; a fresh flag after a clear is a
            // new, distinct row.
            assertThat(second.flagId()).isNotEqualTo(first.flagId());
            assertThat(registry.isFlagged(SERVER)).isTrue();
        }
    }
}
