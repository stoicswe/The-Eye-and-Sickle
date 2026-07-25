package io.github.stoicswe.eyeandsickle.server.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The default operator gate for Option B (§5): closed until a token is configured, then a token match.
 * This is the mechanism that keeps full-state transfer from ever being player-triggered.
 */
class TokenOperatorAuthorizationTest {

    private static OperatorAuthorization withToken(String configured) {
        return new TokenOperatorAuthorization(new MigrationProperties(null, null, null, configured));
    }

    @Test
    @DisplayName("with no token configured, every request is refused — even a null or empty presentation")
    void deniesWhenUnconfigured() {
        OperatorAuthorization auth = withToken(null);

        assertThatThrownBy(() -> auth.requireOperator(null)).isInstanceOf(OperatorAuthorizationException.class);
        assertThatThrownBy(() -> auth.requireOperator("")).isInstanceOf(OperatorAuthorizationException.class);
        assertThatThrownBy(() -> auth.requireOperator("anything")).isInstanceOf(OperatorAuthorizationException.class);
    }

    @Test
    @DisplayName("with a token configured, only the exact token is accepted")
    void requiresExactToken() {
        OperatorAuthorization auth = withToken("correct-horse");

        assertThatThrownBy(() -> auth.requireOperator(null)).isInstanceOf(OperatorAuthorizationException.class);
        assertThatThrownBy(() -> auth.requireOperator("wrong")).isInstanceOf(OperatorAuthorizationException.class);
        assertThatThrownBy(() -> auth.requireOperator("correct-horse "))
                .as("a near-miss is still a miss")
                .isInstanceOf(OperatorAuthorizationException.class);

        assertThatCode(() -> auth.requireOperator("correct-horse")).doesNotThrowAnyException();
    }
}
