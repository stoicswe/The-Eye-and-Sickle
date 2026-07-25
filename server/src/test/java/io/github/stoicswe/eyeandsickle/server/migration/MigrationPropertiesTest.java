package io.github.stoicswe.eyeandsickle.server.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The migration slice's operational knobs: DoS size bounds and the operator-access token, with a
 * closed-by-default posture on the latter (§5).
 */
class MigrationPropertiesTest {

    @Test
    @DisplayName("nulls fall back to the documented defaults")
    void appliesDefaults() {
        MigrationProperties properties = new MigrationProperties(null, null, null, null);

        assertThat(properties.maxItems()).isEqualTo(MigrationProperties.DEFAULT_MAX_ITEMS);
        assertThat(properties.maxRecordsPerItem()).isEqualTo(MigrationProperties.DEFAULT_MAX_RECORDS_PER_ITEM);
        assertThat(properties.maxBundleBytes()).isEqualTo(MigrationProperties.DEFAULT_MAX_BUNDLE_BYTES);
        assertThat(properties.operatorToken()).isEmpty();
    }

    @Test
    @DisplayName("operator access is disabled until a token is configured — the safe closed default (§5)")
    void operatorAccessClosedByDefault() {
        assertThat(new MigrationProperties(null, null, null, null).operatorAccessEnabled())
                .isFalse();
        assertThat(new MigrationProperties(null, null, null, "  ").operatorAccessEnabled())
                .as("a blank token is not a token")
                .isFalse();
        assertThat(new MigrationProperties(null, null, null, "s3cret").operatorAccessEnabled())
                .isTrue();
    }

    @Test
    @DisplayName("non-positive size bounds are rejected at binding")
    void rejectsNonPositiveBounds() {
        assertThatThrownBy(() -> new MigrationProperties(0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MigrationProperties(null, -1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MigrationProperties(null, null, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
