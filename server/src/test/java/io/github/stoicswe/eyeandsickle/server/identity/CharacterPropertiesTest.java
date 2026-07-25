package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The character-cap configuration binding. The default is 3 (09 §1); the value is a product knob a
 * self-hoster may tune, bounded only so it stays sane — at least 1, and no more than the structural slot
 * range so every character can be assigned a slot.
 */
class CharacterPropertiesTest {

    @Test
    @DisplayName("the documented default is 3")
    void defaultIsThree() {
        assertThat(new CharacterProperties(3).maxCharacters()).isEqualTo(3);
    }

    @Test
    @DisplayName("a cap below 1 is refused — an account must be able to hold at least one character")
    void refusesBelowOne() {
        assertThatThrownBy(() -> new CharacterProperties(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharacterProperties(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a cap above the structural slot bound is refused — there would be no slot to assign")
    void refusesAboveSlotBound() {
        assertThat(new CharacterProperties(Player.MAX_SLOT).maxCharacters()).isEqualTo(Player.MAX_SLOT);
        assertThatThrownBy(() -> new CharacterProperties(Player.MAX_SLOT + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
