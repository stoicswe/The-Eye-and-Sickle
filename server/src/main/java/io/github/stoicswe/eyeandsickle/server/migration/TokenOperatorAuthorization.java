package io.github.stoicswe.eyeandsickle.server.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * The default {@link OperatorAuthorization}: a configured shared secret, compared in constant time, that
 * <strong>denies when no secret is configured</strong> ({@code
 * docs/architecture/09-player-state-portability.md} §5).
 *
 * <h2>Closed by default</h2>
 *
 * Full-state (Option B) transfer is off until an operator deliberately sets {@code
 * eyeandsickle.migration.operator-token}. Until then every operator request is refused — the safe posture
 * for a capability that moves a character's whole economy between servers. This is a stand-in for a real
 * operator identity system (mutual TLS, an operator OAuth scope, a signed operator assertion); a
 * deployment that has one supersedes this via {@code @ConditionalOnMissingBean}.
 *
 * <p>The comparison is constant-time so the endpoint does not leak, through timing, how much of a guessed
 * token was correct.
 */
class TokenOperatorAuthorization implements OperatorAuthorization {

    private final MigrationProperties properties;

    TokenOperatorAuthorization(MigrationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void requireOperator(String presentedToken) {
        if (!properties.operatorAccessEnabled()) {
            throw new OperatorAuthorizationException("Operator migration is disabled on this server: no "
                    + "eyeandsickle.migration.operator-token is configured, so full-state (Option B) transfer is "
                    + "refused (docs/architecture/09-player-state-portability.md §5).");
        }
        if (presentedToken == null || !constantTimeEquals(presentedToken, properties.operatorToken())) {
            throw new OperatorAuthorizationException(
                    "Not authorized as an operator of this server; a valid operator token is required for "
                            + "full-state (Option B) migration.");
        }
    }

    /**
     * A length-independent constant-time comparison: hashing both sides to a fixed width first means the
     * comparison time reveals neither the configured token's length nor how far a guess matched.
     */
    private static boolean constantTimeEquals(String presented, String configured) {
        byte[] a = sha256(presented);
        byte[] b = sha256(configured);
        return MessageDigest.isEqual(a, b);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to compare operator tokens", e);
        }
    }
}
