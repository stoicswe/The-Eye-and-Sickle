package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The allowlist row value object. It mirrors {@code ck_allowlist_entries_revoked_pair} in memory: a
 * revocation names an actor, or there is no revocation — never one without the other. {@link
 * AllowlistEntry#isActive()} is the whole point of the row from the sign-in path's view.
 */
class AllowlistEntryTest {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Did OPERATOR = Did.of("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb");
    private static final Instant ADDED = Instant.parse("2026-07-24T10:00:00Z");
    private static final Instant REVOKED = Instant.parse("2026-07-24T12:00:00Z");

    @Test
    @DisplayName("an unrevoked entry is active — this DID may join right now")
    void unrevokedIsActive() {
        AllowlistEntry entry = new AllowlistEntry(UUID.randomUUID(), DID, ADDED, null, "seed", null, null);
        assertThat(entry.isActive()).isTrue();
    }

    @Test
    @DisplayName("a fully revoked entry is inactive but retains its history")
    void revokedIsInactive() {
        AllowlistEntry entry =
                new AllowlistEntry(UUID.randomUUID(), DID, ADDED, OPERATOR, "removed", REVOKED, OPERATOR);
        assertThat(entry.isActive()).isFalse();
        // Soft revocation: the moderation record survives on the row itself.
        assertThat(entry.revokedAt()).isEqualTo(REVOKED);
        assertThat(entry.revokedBy()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("a revocation timestamp without an actor is refused")
    void revokedAtWithoutActor() {
        // Defends the in-memory type against the exact shape the DB CHECK forbids: an unattributable
        // moderation action, which is the one an operator will later need to explain.
        assertThatThrownBy(() -> new AllowlistEntry(UUID.randomUUID(), DID, ADDED, null, null, REVOKED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name an actor");
    }

    @Test
    @DisplayName("an actor without a revocation timestamp is equally refused")
    void actorWithoutRevokedAt() {
        assertThatThrownBy(() -> new AllowlistEntry(UUID.randomUUID(), DID, ADDED, null, null, null, OPERATOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a configuration seed legitimately has no adder")
    void nullAdderAllowed() {
        // added_by_did is nullable: the initial seed has no in-game actor to attribute it to.
        assertThatCode(() -> new AllowlistEntry(UUID.randomUUID(), DID, ADDED, null, "seed", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the required fields are required")
    void requiredFields() {
        assertThatThrownBy(() -> new AllowlistEntry(null, DID, ADDED, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AllowlistEntry(UUID.randomUUID(), null, ADDED, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AllowlistEntry(UUID.randomUUID(), DID, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
