package io.github.stoicswe.eyeandsickle.client.oauth;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE, {@code S256} only.
 *
 * <p>The atproto spec makes PKCE mandatory on every authorization request, requires a new random
 * challenge each time, and forbids the {@code plain} method outright — so this class offers no way to
 * produce one. Authorization servers must reject a reused {@code code_challenge} for at least 24
 * hours, which means a client that cached one would work once and then stop.
 *
 * @param verifier the secret, held until the token exchange
 * @param challenge its SHA-256, sent on the authorization request
 */
record Pkce(String verifier, String challenge) {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @return a fresh verifier and its challenge
     */
    static Pkce generate() {
        // RFC 7636 allows 43–128 characters; 32 random bytes base64url-encodes to 43, the minimum
        // that is also the full output of the hash the challenge uses. More would not add entropy.
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Pkce(verifier, Jose.sha256(verifier));
    }
}
