package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The select-view projection. It exposes the character's wire reference, status, handle and side, and
 * deliberately nothing of the authoritative internals a roster has no business showing — balance, heat,
 * or the concurrency token. A local, slot-less character is not an account character and cannot be
 * summarized this way.
 */
class CharacterSummaryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");

    @Test
    @DisplayName("projects a DID-bound character to its reference, status, handle and side")
    void projectsDidBound() {
        UUID id = UUID.randomUUID();
        Player character = new Player(
                id,
                DID,
                2,
                "alice.bsky.social",
                CharacterStatus.ACTIVE,
                Faction.SICKLE,
                Heat.ZERO,
                Ethecoin.ZERO,
                NOW,
                NOW,
                0);

        CharacterSummary summary = CharacterSummary.from(character);

        assertThat(summary.ref().characterId()).isEqualTo(id);
        assertThat(summary.ref().slot()).isEqualTo(2);
        assertThat(summary.status()).isEqualTo("active");
        assertThat(summary.handle()).isEqualTo("alice.bsky.social");
        assertThat(summary.faction()).isEqualTo(Faction.SICKLE);
    }

    @Test
    @DisplayName("a local, slot-less character cannot be summarized as an account character")
    void refusesLocal() {
        Player local = new Player(
                UUID.randomUUID(),
                null,
                null,
                "solo",
                CharacterStatus.ACTIVE,
                Faction.NONE,
                Heat.ZERO,
                Ethecoin.ZERO,
                NOW,
                NOW,
                0);

        assertThatThrownBy(() -> CharacterSummary.from(local)).isInstanceOf(IllegalArgumentException.class);
    }
}
