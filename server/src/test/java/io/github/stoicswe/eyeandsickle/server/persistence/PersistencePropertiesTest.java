package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Binding and validation for the one calibrated number the schema forces someone to supply. */
class PersistencePropertiesTest {

    @Test
    @DisplayName("an unconfigured cap falls back to the documented starting figure")
    void defaultsToTheDocumentedFigure() {
        // docs/design/04-mining.md §2.3. This is open question OQ-4, not a decision — 4 hours is a
        // starting figure to be resolved against session-length telemetry.
        assertThat(new PersistenceProperties(null).yieldBufferCapHours()).isEqualTo(4);
        assertThat(PersistenceProperties.DEFAULT_YIELD_BUFFER_CAP_HOURS).isEqualTo(4);
    }

    @Test
    @DisplayName("an explicit value wins over the default")
    void explicitValuesAreHonoured() {
        assertThat(new PersistenceProperties(6).yieldBufferCapHours()).isEqualTo(6);
    }

    @Test
    @DisplayName("a zero or negative cap is refused at startup, not discovered in play")
    void nonPositiveCapsAreRefused() {
        // Deployed miners are the ONLY offline income source (Invariant I5). A zero cap silently
        // removes it, and the symptom — "my miners earn nothing overnight" — reads as a game bug
        // rather than as a configuration error.
        assertThatThrownBy(() -> new PersistenceProperties(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invariant I5");
        assertThatThrownBy(() -> new PersistenceProperties(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
