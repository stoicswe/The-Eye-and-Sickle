package io.github.stoicswe.eyeandsickle.server.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CharacterDirectoryProperties} — the anti-abuse bounds for the directory.
 *
 * <p>Two things matter and are pinned here: null-coalescing (an operator may set one knob and leave the
 * rest at their defaults, and a federating server that sets none still gets a coherent config), and that
 * a non-positive bound is rejected loudly rather than silently disabling a cap.
 */
class CharacterDirectoryPropertiesTest {

    @Test
    @DisplayName("all-null yields the documented defaults")
    void defaults() {
        CharacterDirectoryProperties properties = new CharacterDirectoryProperties(null, null, null);

        assertThat(properties.maxRecordBytes()).isEqualTo(CharacterDirectoryProperties.DEFAULT_MAX_RECORD_BYTES);
        assertThat(properties.maxDirectorySize()).isEqualTo(CharacterDirectoryProperties.DEFAULT_MAX_DIRECTORY_SIZE);
        assertThat(properties.maxHomesPerResolve())
                .isEqualTo(CharacterDirectoryProperties.DEFAULT_MAX_HOMES_PER_RESOLVE);
    }

    @Test
    @DisplayName("a single set knob leaves the rest at their defaults")
    void partialOverride() {
        CharacterDirectoryProperties properties = new CharacterDirectoryProperties(1024, null, null);

        assertThat(properties.maxRecordBytes()).isEqualTo(1024);
        assertThat(properties.maxDirectorySize()).isEqualTo(CharacterDirectoryProperties.DEFAULT_MAX_DIRECTORY_SIZE);
    }

    @Test
    @DisplayName("a non-positive bound is refused, not silently treated as 'no cap'")
    void rejectsNonPositive() {
        assertThatThrownBy(() -> new CharacterDirectoryProperties(0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-record-bytes");
        assertThatThrownBy(() -> new CharacterDirectoryProperties(null, -1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-directory-size");
        assertThatThrownBy(() -> new CharacterDirectoryProperties(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-homes-per-resolve");
    }
}
