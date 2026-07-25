package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The federation-wide non-recognition flag record ({@code docs/architecture/03} §4). */
class FlaggedServerTest {

    private static final UUID FLAG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FLAGGED_AT = Instant.parse("2026-07-24T00:00:00Z");

    private static FlaggedServer flag(Instant clearedAt) {
        return new FlaggedServer(
                FLAG_ID,
                "did:plc:rogue0000000000000000",
                FlaggedServer.REASON_EQUIVOCATION,
                "{}",
                null,
                FLAGGED_AT,
                clearedAt,
                clearedAt == null ? null : "reinstated");
    }

    @Test
    @DisplayName("is active while uncleared and inactive once cleared")
    void isActive() {
        assertThat(flag(null).isActive()).isTrue();
        assertThat(flag(FLAGGED_AT.plusSeconds(3600)).isActive()).isFalse();
    }

    @Test
    @DisplayName("uses the documented automatic-equivocation reason string")
    void equivocationReasonConstant() {
        assertThat(FlaggedServer.REASON_EQUIVOCATION).isEqualTo("validator_equivocation");
    }

    @Test
    @DisplayName("rejects the null fields the schema declares NOT NULL")
    void rejectsNulls() {
        assertThatThrownBy(() -> new FlaggedServer(null, "did:plc:r", "r", "{}", null, FLAGGED_AT, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FlaggedServer(FLAG_ID, null, "r", "{}", null, FLAGGED_AT, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FlaggedServer(FLAG_ID, "did:plc:r", null, "{}", null, FLAGGED_AT, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FlaggedServer(FLAG_ID, "did:plc:r", "r", null, null, FLAGGED_AT, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FlaggedServer(FLAG_ID, "did:plc:r", "r", "{}", null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
