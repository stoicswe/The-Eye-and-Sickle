package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wire reference to a character within an account (09 §1). Its one structural invariant is that a
 * slot is a positive index; it carries no product rule — the cap and the top slot number are the
 * authoritative server's concern, not a wire fact.
 */
class CharacterRefTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    @DisplayName("holds the character id and slot")
    void holdsIdAndSlot() {
        CharacterRef ref = CharacterRef.of(ID, 2);
        assertThat(ref.characterId()).isEqualTo(ID);
        assertThat(ref.slot()).isEqualTo(2);
    }

    @Test
    @DisplayName("a slot below 1 is not a slot")
    void rejectsNonPositiveSlot() {
        assertThatThrownBy(() -> CharacterRef.of(ID, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CharacterRef.of(ID, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the character id is required")
    void requiresId() {
        assertThatThrownBy(() -> CharacterRef.of(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("it does not bound the slot from above — the cap is a server concern")
    void doesNotEncodeTheCap() {
        // A slot number well above any product cap is still a well-formed reference; how many an account
        // may hold is enforced by the authoritative server (09 §2), not by this wire type.
        assertThat(CharacterRef.of(ID, 99).slot()).isEqualTo(99);
    }
}
