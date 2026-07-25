package io.github.stoicswe.eyeandsickle.server.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The default character-home directory's one job: monotonicity (§4, §6.1). A binding only advances; a
 * stale or replayed sequence is refused. That single rule is the whole of "no rollback / no fork".
 */
class InMemoryCharacterHomeDirectoryTest {

    private static final Did ACCOUNT = Did.of("did:plc:account00000000000000");
    private static final CharacterRef CHARACTER = CharacterRef.of(UUID.randomUUID(), 1);

    private final InMemoryCharacterHomeDirectory directory = new InMemoryCharacterHomeDirectory();

    @Test
    @DisplayName("an unknown character starts at sequence 0")
    void unknownStartsAtZero() {
        assertThat(directory.currentSequence(ACCOUNT, CHARACTER)).isZero();
    }

    @Test
    @DisplayName("advancing moves the binding strictly past the presented sequence")
    void advancesStrictly() {
        long first = directory.advanceHomeToLocal(ACCOUNT, CHARACTER, UUID.randomUUID(), 0);
        assertThat(first).isEqualTo(1);
        assertThat(directory.currentSequence(ACCOUNT, CHARACTER)).isEqualTo(1);

        long second = directory.advanceHomeToLocal(ACCOUNT, CHARACTER, UUID.randomUUID(), 1);
        assertThat(second).isEqualTo(2);
    }

    @Test
    @DisplayName("re-presenting an already-advanced bundle is refused as stale (replay)")
    void refusesReplay() {
        directory.advanceHomeToLocal(ACCOUNT, CHARACTER, UUID.randomUUID(), 0); // now recognizes 1

        assertThatThrownBy(() -> directory.advanceHomeToLocal(ACCOUNT, CHARACTER, UUID.randomUUID(), 0))
                .isInstanceOf(StaleHomeSequenceException.class);
    }

    @Test
    @DisplayName("a sequence below the recognized one is refused as a rollback")
    void refusesRollback() {
        directory.advanceHomeToLocal(ACCOUNT, CHARACTER, UUID.randomUUID(), 5); // recognizes 6

        assertThatThrownBy(() -> directory.advanceHomeToLocal(ACCOUNT, CHARACTER, UUID.randomUUID(), 3))
                .isInstanceOf(StaleHomeSequenceException.class);
        assertThat(directory.currentSequence(ACCOUNT, CHARACTER))
                .as("a refused advance does not move the binding")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("bindings are per character — advancing one does not touch another")
    void isolatesCharacters() {
        CharacterRef other = CharacterRef.of(UUID.randomUUID(), 2);
        directory.advanceHomeToLocal(ACCOUNT, CHARACTER, UUID.randomUUID(), 4);

        assertThat(directory.currentSequence(ACCOUNT, other)).isZero();
    }
}
