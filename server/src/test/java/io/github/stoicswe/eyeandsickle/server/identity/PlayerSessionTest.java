package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayerSession} expiry semantics, tested against injected instants rather than the wall clock.
 * Expiry is inclusive of the boundary — a session is not valid <em>at</em> the instant it expires — and a
 * session whose end precedes its start is a contradiction the type refuses to hold. It also carries the
 * selected character's DID as the actor, and refuses one that belongs to a different account (09 §9).
 */
class PlayerSessionTest {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final CharacterDid CHARACTER = new CharacterDid(DID.value(), 1);
    private static final Instant ISSUED = Instant.parse("2026-07-24T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-07-24T11:00:00Z");

    private static PlayerSession session() {
        return new PlayerSession("token-abc", UUID.randomUUID(), DID, CHARACTER, "alice.bsky.social", ISSUED, EXPIRES);
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
    @DisplayName("it carries the selected character's DID as the actor")
    void carriesCharacterDid() {
        PlayerSession session = session();
        assertThat(session.characterDid()).isEqualTo(CHARACTER);
        assertThat(session.characterDid().accountDid()).isEqualTo(DID.value());
        assertThat(session.characterDid().slot()).isEqualTo(1);
        // Both identities are present: the account for auth, the character for game state.
        assertThat(session.did()).isEqualTo(DID);
    }

    @Test
    @DisplayName("a character DID from a different account is refused — a session cannot stamp another account")
    void characterMustMatchAccount() {
        CharacterDid otherAccount = new CharacterDid("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb", 1);
        assertThatThrownBy(() -> new PlayerSession("t", UUID.randomUUID(), DID, otherAccount, null, ISSUED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("expiresAt may not precede issuedAt")
    void expiresBeforeIssuedRejected() {
        // A token that expires before it began is a bug in whoever computed the TTL; surfacing it here
        // keeps it a construction failure rather than a session that is born dead.
        assertThatThrownBy(() -> new PlayerSession("t", UUID.randomUUID(), DID, CHARACTER, null, EXPIRES, ISSUED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a zero-length session is permitted and immediately expired")
    void zeroLengthSession() {
        assertThatCode(() -> new PlayerSession("t", UUID.randomUUID(), DID, CHARACTER, null, ISSUED, ISSUED))
                .doesNotThrowAnyException();
        assertThat(new PlayerSession("t", UUID.randomUUID(), DID, CHARACTER, null, ISSUED, ISSUED).isExpired(ISSUED))
                .isTrue();
    }

    @Test
    @DisplayName("the identifying fields are required")
    void requiredFields() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new PlayerSession(null, id, DID, CHARACTER, null, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", null, DID, CHARACTER, null, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", id, null, CHARACTER, null, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", id, DID, null, null, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", id, DID, CHARACTER, null, null, EXPIRES))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PlayerSession("t", id, DID, CHARACTER, null, ISSUED, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("isExpired(null) is a programming error, not 'never expires'")
    void nullNow() {
        assertThatThrownBy(() -> session().isExpired(null)).isInstanceOf(NullPointerException.class);
    }
}
