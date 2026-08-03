package io.github.stoicswe.eyeandsickle.client.oauth;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * The small amount of JOSE this client needs: base64url, a compact JWS, and a JWK thumbprint.
 *
 * <h2>Why this is hand-written rather than a library</h2>
 *
 * The client's dependency list is deliberately austere — JavaFX, {@code protocol}, {@code solo} and an
 * allowlisted {@code spring-context} — and every addition is carried into five platform jars and the
 * jpackage image. What is needed here is small and fully specified: a compact JWS is
 * {@code b64url(header) + "." + b64url(payload) + "." + b64url(signature)}.
 *
 * <p>⚠ <strong>This is token formatting over JDK crypto, not a hand-rolled primitive.</strong>
 * {@code docs/architecture/07-transport-security.md} §6 <b>T-1</b> warns that this repo already has
 * one hand-rolled crypto <em>protocol</em> awaiting review; that warning is about designing a
 * handshake, which this is not. The signature comes from the JDK.
 *
 * <h2>⚠ THE trap this class exists to avoid: DER vs raw signatures</h2>
 *
 * {@code Signature.getInstance("SHA256withECDSA")} emits an <strong>ASN.1 DER</strong> {@code SEQUENCE
 * {r, s}} — variable length, with leading zero bytes stripped or added for sign. JOSE requires the
 * opposite: <strong>raw {@code R || S}</strong>, each left-padded to exactly 32 bytes. Converting
 * between them by hand is the single most common ES256 bug, and it fails <em>intermittently</em> —
 * roughly one signature in 256 has a short {@code r} or {@code s}, so a naive implementation works
 * almost every time and then rejects a login for no visible reason.
 *
 * <p>{@code SHA256withECDSAinP1363Format} produces the raw form directly, so the conversion does not
 * exist here at all. That is why the algorithm name matters and must not be "simplified".
 */
final class Jose {

    private Jose() {}

    /** JOSE's base64: URL-safe alphabet, no padding. */
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();

    static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    static String b64(String text) {
        return b64(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Signs a compact JWS with ES256.
     *
     * @param headerJson the protected header, already serialised
     * @param payloadJson the payload, already serialised
     * @param key the P-256 private key
     * @return the compact serialization
     */
    static String signEs256(String headerJson, String payloadJson, PrivateKey key) {
        String signingInput = b64(headerJson) + "." + b64(payloadJson);
        try {
            // ⚠ inP1363Format — see the class comment. Plain "SHA256withECDSA" is DER and produces a
            // token that most servers reject and some accept, depending on the day's random `r`.
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(key);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64(signature.sign());
        } catch (GeneralSecurityException failed) {
            throw new IllegalStateException("could not sign a DPoP proof", failed);
        }
    }

    /**
     * The public JWK for a P-256 key, with members in the exact order RFC 7638 requires.
     *
     * <p>⚠ Member order is not cosmetic here. {@link #thumbprint} hashes this string, and RFC 7638
     * defines the thumbprint over a canonical JSON with <strong>lexicographically ordered</strong>
     * keys and no whitespace — {@code crv}, {@code kty}, {@code x}, {@code y}. Serialising this with
     * a JSON library that does not guarantee order would produce a {@code jkt} that changes between
     * runs, and the {@code jkt} is what binds a token to this key.
     *
     * @param key the public key
     * @return the canonical JWK JSON
     */
    static String publicJwk(ECPublicKey key) {
        String x = b64(coordinate(key.getW().getAffineX()));
        String y = b64(coordinate(key.getW().getAffineY()));
        return "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + x + "\",\"y\":\"" + y + "\"}";
    }

    /**
     * RFC 7638 JWK thumbprint — the {@code jkt} an access token is bound to.
     *
     * @param canonicalJwk the output of {@link #publicJwk}
     * @return the base64url SHA-256 thumbprint
     */
    static String thumbprint(String canonicalJwk) {
        try {
            return b64(MessageDigest.getInstance("SHA-256").digest(canonicalJwk.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 is required on every JVM", impossible);
        }
    }

    /** Base64url SHA-256 of a string — PKCE's {@code S256} and DPoP's {@code ath}. */
    static String sha256(String value) {
        try {
            return b64(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 is required on every JVM", impossible);
        }
    }

    /**
     * A P-256 coordinate as exactly 32 bytes.
     *
     * <p>⚠ {@link BigInteger#toByteArray()} is two's complement: it prepends a zero byte when the top
     * bit is set (33 bytes), and drops leading zeros when the value is small (31 or fewer). Both are
     * wrong for a JWK, where the length <em>is</em> the field size — and both happen often enough to
     * ship and then fail for some users and not others.
     */
    private static byte[] coordinate(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] fixed = new byte[32];
        if (bytes.length > 32) {
            System.arraycopy(bytes, bytes.length - 32, fixed, 0, 32);
        } else {
            System.arraycopy(bytes, 0, fixed, 32 - bytes.length, bytes.length);
        }
        return fixed;
    }
}
