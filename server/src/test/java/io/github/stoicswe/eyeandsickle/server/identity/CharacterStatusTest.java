package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The character lifecycle vocabulary and its database spellings (09 §6.1). {@code active} is the only
 * playable, non-terminal state; {@code migrated} and {@code retired} are terminal. The db mapping round
 * trips, and an unknown stored value is rejected rather than mapped to a fallback.
 */
class CharacterStatusTest {

    @Test
    @DisplayName("db spellings round-trip through fromDb")
    void roundTrips() {
        for (CharacterStatus status : CharacterStatus.values()) {
            assertThat(CharacterStatus.fromDb(status.dbValue())).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("only active is playable; migrated and retired are terminal")
    void playableAndTerminal() {
        assertThat(CharacterStatus.ACTIVE.isPlayable()).isTrue();
        assertThat(CharacterStatus.ACTIVE.isTerminal()).isFalse();

        assertThat(CharacterStatus.MIGRATED.isPlayable()).isFalse();
        assertThat(CharacterStatus.MIGRATED.isTerminal()).isTrue();
        assertThat(CharacterStatus.RETIRED.isPlayable()).isFalse();
        assertThat(CharacterStatus.RETIRED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("an unknown stored value is rejected, never mapped to a fallback")
    void unknownRejected() {
        assertThatThrownBy(() -> CharacterStatus.fromDb("bogus")).isInstanceOf(IllegalArgumentException.class);
    }
}
