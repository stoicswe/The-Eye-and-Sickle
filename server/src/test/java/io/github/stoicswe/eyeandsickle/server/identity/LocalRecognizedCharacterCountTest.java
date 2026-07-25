package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The single-server default {@link RecognizedCharacterCount} (09 §2): it counts only this server's own
 * <em>active</em> characters for an account. Migrated and retired shells are not counted — a migrated
 * character is recognized at its new home, a retired one nowhere (09 §6.1) — and other accounts are not
 * counted.
 */
class LocalRecognizedCharacterCountTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Did OTHER = Did.of("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb");

    @Test
    @DisplayName("counts an account's active characters only, ignoring shells and other accounts")
    void countsActiveOnly() {
        FakePlayerRepository players = new FakePlayerRepository();
        players.createCharacter(DID, "alice", 1, NOW);
        players.createCharacter(DID, "alice", 2, NOW);
        // A retained shell at slot 3 must not count.
        players.put(new Player(
                java.util.UUID.randomUUID(),
                DID,
                3,
                "alice",
                CharacterStatus.MIGRATED,
                io.github.stoicswe.eyeandsickle.protocol.game.Faction.NONE,
                Heat.ZERO,
                io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.ZERO,
                NOW,
                NOW,
                0));
        // Another account's character must not count.
        players.createCharacter(OTHER, "bob", 1, NOW);

        LocalRecognizedCharacterCount count = new LocalRecognizedCharacterCount(players);

        assertThat(count.countRecognized(DID)).isEqualTo(2);
        assertThat(count.countRecognized(OTHER)).isEqualTo(1);
    }
}
