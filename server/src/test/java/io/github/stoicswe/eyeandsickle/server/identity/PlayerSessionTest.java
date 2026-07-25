package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayerSession} expiry semantics, tested against injected instants rather than the wall clock.
 * Expiry is inclusive of the boundary — a session is not valid <em>at</em> the instant it expires — and a
 * session whose end precedes its start is a contradiction the type refuses to hold.
 */
class PlayerSessionTest {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Instant ISSUED = Instant.parse("2026-07-24T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-07-24T11:00:00Z");

    private static PlayerSession session() {
        return new PlayerSession("token-abc", UUID.randomUUID(), DID, "alice.bsky.social", ISSUED, EXPIRES);
    }

    @Test
    @DisplayName("a session is valid strictly before its expiry")
    void validBeforeExpiry() {
        assertThat(session().isExpired(EXPIRES.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("a session is expired AT its expiry instant — the boundary is inclusive")
    void expiredAtBoundary() {
        // Defends the "not valid at expiry" rule the store relies on: an off-by-one here would honour a
        // token for one instant past its lifetime.
        assertThat(session().isExpired(EXPIRES)).isTrue();
    }

    @Test
    @DisplayName("a session is expired after its expiry")
    void expiredAfter() {
        assertThat(session().isExpired(EXPIRES.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("expiresAt may not precede issuedAt")
    void expiresBeforeIssuedRejected() {
        // A token that expires before it began is a bug in whoever computed the TTL; surfacing it here
        // keeps it a construction failure rather than a session that is born dead.
        assertThatThrownBy(() -> new PlayerSession("t", UUID.randomUUID(), DID, null, EXPIRES, ISSUED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a zero-length session is permitted and immediately expired")
    void zeroLengthSession() {
        assertThatCode(() -> new PlayerSession("t", UUID.randomUUID(), DID, null, ISSUED, ISSUED))
                .doesNotThrowAnyException();
        assertThat(new PlayerSession("t", UUID.randomUUID(), DID, null, ISSUED, ISSUED).isExpired(ISSUED))
                .isTrue();
    }

    @Test
    @DisplayName("the identifying fields are required")
    void requiredFields() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new PlayerSession(null, id, DID, null, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", null, DID, null, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", id, null, null, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", id, DID, null, null, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", id, DID, null, ISSUED, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("isExpired(null) is a programming error, not 'never expires'")
    void nullNow() {
        assertThatThrownBy(() -> session().isExpired(null)).isInstanceOf(NullPointerException.class);
    }
}
