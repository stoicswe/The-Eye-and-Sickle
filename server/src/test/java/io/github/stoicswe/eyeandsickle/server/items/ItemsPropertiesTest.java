package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ItemsProperties} — the slice's operational knobs, none of which is a balance value (Invariant
 * I14). The tests defend the defaults, the validation that keeps a self-hoster's typo a startup
 * failure rather than a runtime surprise, and the history-page clamp that bounds a request.
 */
class ItemsPropertiesTest {

    private static ItemsProperties defaults() {
        return new ItemsProperties(null, null, null, null);
    }

    // ------------------------------------------------------------------ defaults

    @Test
    @DisplayName("absent values fall back to the documented defaults")
    void defaultsApply() {
        ItemsProperties properties = defaults();

        assertThat(properties.maxFutureSkew())
                .isEqualTo(Duration.ofSeconds(ItemsProperties.DEFAULT_MAX_FUTURE_SKEW_SECONDS));
        assertThat(properties.historyDefaultLimit()).isEqualTo(ItemsProperties.DEFAULT_HISTORY_LIMIT);
        assertThat(properties.historyMaxLimit()).isEqualTo(ItemsProperties.DEFAULT_HISTORY_MAX_LIMIT);
        assertThat(properties.ingressLandingTier()).isEqualTo(StorageTier.STANDARD_STORAGE);
    }

    @Test
    @DisplayName("a blank landing tier falls back to standard storage")
    void blankLandingTierDefaults() {
        assertThat(new ItemsProperties(null, null, null, "  ").ingressLandingTier())
                .isEqualTo(StorageTier.STANDARD_STORAGE);
    }

    @Test
    @DisplayName("a configured landing tier is parsed to its enum")
    void configuredLandingTierParses() {
        assertThat(new ItemsProperties(null, null, null, "VAULT").ingressLandingTier())
                .isEqualTo(StorageTier.VAULT);
    }

    // ------------------------------------------------------------------ validation

    @Nested
    @DisplayName("invalid configuration is refused at construction")
    class Validation {

        @Test
        @DisplayName("a negative skew is refused — it is an anti-replay horizon, not a rewind")
        void negativeSkewRejected() {
            assertThatThrownBy(() -> new ItemsProperties(Duration.ofSeconds(-1), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max-future-skew");
        }

        @Test
        @DisplayName("non-positive history limits are refused")
        void nonPositiveLimitsRejected() {
            assertThatThrownBy(() -> new ItemsProperties(null, 0, 100, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ItemsProperties(null, 20, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a default page larger than the max page is a contradiction")
        void defaultOverMaxRejected() {
            assertThatThrownBy(() -> new ItemsProperties(null, 200, 100, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the max limit");
        }

        @Test
        @DisplayName("an unknown landing tier is a startup failure, not a first-transfer surprise")
        void unknownTierRejected() {
            assertThatThrownBy(() -> new ItemsProperties(null, null, null, "PENTHOUSE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ingress-landing-storage-tier");
        }
    }

    // ------------------------------------------------------------------ history clamp

    @Nested
    @DisplayName("clampHistoryLimit")
    class Clamp {

        private final ItemsProperties properties = defaults();

        @Test
        @DisplayName("an absent limit becomes the default page size")
        void nullBecomesDefault() {
            assertThat(properties.clampHistoryLimit(null)).isEqualTo(ItemsProperties.DEFAULT_HISTORY_LIMIT);
        }

        @Test
        @DisplayName("a request below one is clamped up to one")
        void belowOneClampsUp() {
            assertThat(properties.clampHistoryLimit(0)).isEqualTo(1);
            assertThat(properties.clampHistoryLimit(-25)).isEqualTo(1);
        }

        @Test
        @DisplayName("a request over the ceiling is clamped down to it")
        void overMaxClampsDown() {
            assertThat(properties.clampHistoryLimit(10_000)).isEqualTo(ItemsProperties.DEFAULT_HISTORY_MAX_LIMIT);
        }

        @Test
        @DisplayName("a request within bounds is honoured")
        void withinBoundsKept() {
            assertThatCode(() -> properties.clampHistoryLimit(20)).doesNotThrowAnyException();
            assertThat(properties.clampHistoryLimit(37)).isEqualTo(37);
        }
    }
}
