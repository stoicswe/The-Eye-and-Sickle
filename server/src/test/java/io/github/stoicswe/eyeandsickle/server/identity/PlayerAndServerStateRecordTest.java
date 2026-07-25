package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Construction invariants for the two authoritative record types the slice reads back: {@link Player}
 * (now a character with a slot and lifecycle status) and {@link ServerState}. Both permit a null DID
 * where the schema does (local-only character; not-yet-provisioned server) and both forbid a negative
 * {@code row_version}. {@link Player} additionally enforces the schema's did/slot pairing and slot range.
 */
class PlayerAndServerStateRecordTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");

    @Nested
    @DisplayName("Player")
    class PlayerRecord {

        @Test
        @DisplayName("a DID-bound character has a slot and a status")
        void didBoundHasSlot() {
            assertThatCode(() -> new Player(
                            UUID.randomUUID(),
                            DID,
                            1,
                            "alice.bsky.social",
                            CharacterStatus.ACTIVE,
                            Faction.NONE,
                            Heat.ZERO,
                            Ethecoin.ZERO,
                            NOW,
                            NOW,
                            0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a local-only character may have a null DID, null slot and null handle")
        void localCharacterAllowed() {
            // players.did is nullable for local-only solo play (docs/architecture/02 §4); a local character
            // is exempt from the slot system, so did and slot are null together (09 §1).
            assertThatCode(() -> new Player(
                            UUID.randomUUID(),
                            null,
                            null,
                            null,
                            CharacterStatus.ACTIVE,
                            Faction.NONE,
                            Heat.ZERO,
                            Ethecoin.ZERO,
                            NOW,
                            null,
                            0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("did and slot must be null together or set together — the schema's pairing")
        void didSlotPairingEnforced() {
            // A DID-bound character with no slot, or a local character that somehow has one, is exactly
            // what ck_players_slot_pairing forbids; the record forbids it too so a bad value never reaches
            // an INSERT.
            assertThatThrownBy(() -> new Player(
                            UUID.randomUUID(),
                            DID,
                            null,
                            "h",
                            CharacterStatus.ACTIVE,
                            Faction.NONE,
                            Heat.ZERO,
                            Ethecoin.ZERO,
                            NOW,
                            NOW,
                            0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Player(
                            UUID.randomUUID(),
                            null,
                            1,
                            "h",
                            CharacterStatus.ACTIVE,
                            Faction.NONE,
                            Heat.ZERO,
                            Ethecoin.ZERO,
                            NOW,
                            NOW,
                            0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a slot outside 1..MAX_SLOT is refused")
        void slotRangeEnforced() {
            assertThatThrownBy(() -> new Player(
                            UUID.randomUUID(),
                            DID,
                            0,
                            "h",
                            CharacterStatus.ACTIVE,
                            Faction.NONE,
                            Heat.ZERO,
                            Ethecoin.ZERO,
                            NOW,
                            NOW,
                            0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Player(
                            UUID.randomUUID(),
                            DID,
                            Player.MAX_SLOT + 1,
                            "h",
                            CharacterStatus.ACTIVE,
                            Faction.NONE,
                            Heat.ZERO,
                            Ethecoin.ZERO,
                            NOW,
                            NOW,
                            0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the non-nullable fields are enforced")
        void requiredFields() {
            UUID id = UUID.randomUUID();
            // status is required
            assertThatThrownBy(() -> new Player(
                            id, null, null, null, null, Faction.NONE, Heat.ZERO, Ethecoin.ZERO, NOW, null, 0))
                    .isInstanceOf(NullPointerException.class);
            // faction is required
            assertThatThrownBy(() -> new Player(
                            id, null, null, null, CharacterStatus.ACTIVE, null, Heat.ZERO, Ethecoin.ZERO, NOW, null, 0))
                    .isInstanceOf(NullPointerException.class);
            // heat is required
            assertThatThrownBy(() -> new Player(
                            id, null, null, null, CharacterStatus.ACTIVE, Faction.NONE, null, Ethecoin.ZERO, NOW, null,
                            0))
                    .isInstanceOf(NullPointerException.class);
            // balance is required
            assertThatThrownBy(() -> new Player(
                            id, null, null, null, CharacterStatus.ACTIVE, Faction.NONE, Heat.ZERO, null, NOW, null, 0))
                    .isInstanceOf(NullPointerException.class);
            // createdAt is required
            assertThatThrownBy(() -> new Player(
                            id, null, null, null, CharacterStatus.ACTIVE, Faction.NONE, Heat.ZERO, Ethecoin.ZERO, null,
                            null, 0))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a negative row_version is refused")
        void negativeRowVersion() {
            assertThatThrownBy(() -> new Player(
                            UUID.randomUUID(),
                            null,
                            null,
                            null,
                            CharacterStatus.ACTIVE,
                            Faction.NONE,
                            Heat.ZERO,
                            Ethecoin.ZERO,
                            NOW,
                            null,
                            -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ServerState")
    class ServerStateRecord {

        @Test
        @DisplayName("the server DID may be null before the server is provisioned")
        void nullServerDidAllowed() {
            // Unknown at migration time; set before the first mint, not at install.
            assertThatCode(() -> new ServerState(null, Heat.ZERO, NOW, 0)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("heat and heat-updated-at are required, and row_version is non-negative")
        void requiredFields() {
            assertThatThrownBy(() -> new ServerState(null, null, NOW, 0)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ServerState(null, Heat.ZERO, null, 0))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ServerState(null, Heat.ZERO, NOW, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
