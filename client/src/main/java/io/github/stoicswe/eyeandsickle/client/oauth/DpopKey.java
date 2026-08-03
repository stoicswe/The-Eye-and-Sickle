package io.github.stoicswe.eyeandsickle.client.oauth;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The DPoP keypair for one auth session, and the proofs it signs.
 *
 * <h2>One keypair per SESSION — not per client, not per install</h2>
 *
 * The atproto OAuth spec is explicit: a DPoP key is unique to a user/device/session, and keys "should
 * never be exported or moved between devices". That is what makes a stolen access token useless on
 * its own — the token carries the key's thumbprint ({@code jkt}) and every request must be signed by
 * the matching private key.
 *
 * <p>⚠ Which is also why the private key is a <strong>credential</strong> and gets the same storage
 * treatment as the refresh token ({@link TokenStore}). A refresh token stored securely beside a DPoP
 * key stored carelessly is a refresh token stored carelessly.
 *
 * <h2>⚠ Nonces are stateful, mandatory, and per-server</h2>
 *
 * The server issues a {@code DPoP-Nonce} header and rotates it on a ≤5-minute lifetime. A client must
 * track nonces <em>per account-session and per server</em>, and <strong>must reject a response that
 * omits the header when DPoP was used</strong>. The normal first request to a server therefore fails
 * with {@code use_dpop_nonce} and is retried once with the nonce it just handed back — that is the
 * designed flow, not an error path, and a client that treats the first 401 as a failure never
 * authenticates at all.
 *
 * <p>Nonces are held per <em>origin</em> because the authorization server and the PDS are different
 * hosts with different nonces, and mixing them produces a rejection that looks like a bad key.
 */
final class DpopKey {

    private final KeyPair keyPair;
    private final String canonicalJwk;
    private final String thumbprint;
    private final SecureRandom random = new SecureRandom();

    /** Per-origin, because the auth server and the PDS issue different nonces. */
    private final Map<String, String> nonces = new ConcurrentHashMap<>();

    private DpopKey(KeyPair keyPair) {
        this.keyPair = keyPair;
        this.canonicalJwk = Jose.publicJwk((ECPublicKey) keyPair.getPublic());
        this.thumbprint = Jose.thumbprint(canonicalJwk);
    }

    /** Generates a fresh session key. ES256 is the only algorithm atproto mandates for DPoP. */
    static DpopKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return new DpopKey(generator.generateKeyPair());
        } catch (GeneralSecurityException impossible) {
            // P-256 is available on every JVM this ships to — unlike secp256k1, which is not.
            throw new IllegalStateException("P-256 must be available", impossible);
        }
    }

    /** @return the thumbprint an access token is bound to */
    String thumbprint() {
        return thumbprint;
    }

    PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    /** Records a nonce the server handed back. */
    void rememberNonce(URI endpoint, String nonce) {
        if (nonce != null && !nonce.isBlank()) {
            nonces.put(origin(endpoint), nonce);
        }
    }

    String nonceFor(URI endpoint) {
        return nonces.get(origin(endpoint));
    }

    private static String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    /**
     * Builds a DPoP proof for one request.
     *
     * @param method the HTTP method
     * @param endpoint the target — its {@code htu} form, see below
     * @param accessToken the token being presented, or null before there is one
     * @param now the clock reading
     * @return the compact JWS
     */
    String proof(String method, URI endpoint, String accessToken, Instant now) {
        String header = "{\"typ\":\"dpop+jwt\",\"alg\":\"ES256\",\"jwk\":" + canonicalJwk + "}";

        StringBuilder payload = new StringBuilder("{");
        payload.append("\"jti\":\"").append(jti()).append("\",");
        payload.append("\"htm\":\"").append(method).append("\",");
        payload.append("\"htu\":\"").append(htu(endpoint)).append("\",");
        payload.append("\"iat\":").append(now.getEpochSecond());
        String nonce = nonceFor(endpoint);
        if (nonce != null) {
            payload.append(",\"nonce\":\"").append(nonce).append('"');
        }
        if (accessToken != null) {
            // `ath` binds the proof to the specific access token, so a proof captured from one
            // request cannot be replayed with a different token.
            payload.append(",\"ath\":\"").append(Jose.sha256(accessToken)).append('"');
        }
        payload.append('}');

        return Jose.signEs256(header, payload.toString(), keyPair.getPrivate());
    }

    /**
     * The {@code htu} claim: the request URI <strong>without</strong> query or fragment.
     *
     * <p>⚠ Including the query is a real and easy mistake — the spec defines {@code htu} as the
     * scheme, host and path only, so a proof for a URL with parameters is rejected by a conforming
     * server and accepted by a lenient one, which is the worst combination for finding it.
     */
    private static String htu(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return origin(uri) + path;
    }

    /** A fresh random nonce per proof — the spec requires uniqueness, and servers track it. */
    private String jti() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ── Persistence ───────────────────────────────────────────────────────────────────────────
    //
    // ⚠ These exist so the key can be put in the TokenStore, and NOWHERE else. The spec says a DPoP
    // key must never be exported or moved between devices; keeping the session across a restart on
    // the same machine is not that, but the encoding must never reach a network, a log or a profile.

    String exportPrivate() {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    String exportPublic() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    static DpopKey restore(String privateBase64, String publicBase64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            PrivateKey privateKey = factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateBase64)));
            var publicKey = factory.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicBase64)));
            return new DpopKey(new KeyPair(publicKey, privateKey));
        } catch (GeneralSecurityException | IllegalArgumentException unusable) {
            throw new OauthException(
                    OauthException.Kind.STORAGE, "the stored DPoP key could not be read; sign in again", unusable);
        }
    }
}
