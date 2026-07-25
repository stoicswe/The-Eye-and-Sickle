package io.github.stoicswe.eyeandsickle.protocol.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * A SHA-256 content digest over a payload, formatted as an RFC 9530 {@code Content-Digest} field
 * value — the checksum carried alongside messages sent between servers and between server and client.
 *
 * <h2>What this is for, and what it is NOT</h2>
 *
 * This is a <strong>corruption</strong> check, not the game's tamper defence. Be precise about the
 * difference, because conflating them is how a system ends up trusting a checksum it should not:
 *
 * <ul>
 *   <li>Against an <em>adversary</em>, a bare digest proves nothing — anyone who can alter the bytes
 *       can recompute the digest to match. The real adversarial guarantees are elsewhere and stay
 *       there: {@link io.github.stoicswe.eyeandsickle.protocol.channel.SecureChannel} authenticates
 *       every byte on the wire with AES-GCM, and item provenance carries Ed25519 signatures
 *       ({@code docs/architecture/04-item-provenance.md}). A digest is not a substitute for either,
 *       and nothing may treat "the digest matched" as "the sender is authentic".
 *   <li>Against <em>accident</em> — a truncated read, a buggy proxy, a flipped bit, a partial write,
 *       a mismatched {@code Content-Length} — a digest is exactly right, and it catches the failure
 *       early and cheaply, before the bytes reach signature verification or JSON parsing where the
 *       error would be far more confusing. It also survives store-and-forward hops (the federation
 *       directory relays messages) independently of whatever transport each hop used.
 * </ul>
 *
 * <p>It doubles as a cheap pre-filter: verifying a 32-byte hash is far cheaper than an Ed25519
 * signature check, so a corrupted federation payload can be rejected before the expensive work.
 *
 * <h2>Format — RFC 9530, not a bespoke header</h2>
 *
 * The wire representation is a standard HTTP {@code Content-Digest} field value:
 * {@code sha-256=:<base64 of the raw 32 digest bytes>:}. Using the published standard rather than an
 * invented {@code X-Checksum} header means off-the-shelf proxies, gateways and HTTP clients already
 * understand it, and there is one unambiguous spelling instead of a house dialect that drifts.
 *
 * <p>This class is stateless and its methods are pure — no clock, no randomness — so it is safe to
 * share across threads and trivial to test against fixed vectors.
 */
public final class PayloadDigest {

    /** The algorithm token as it appears in an RFC 9530 field value. */
    public static final String ALGORITHM_TOKEN = "sha-256";

    private static final String JCA_ALGORITHM = "SHA-256";
    private static final int SHA256_LENGTH = 32;

    private PayloadDigest() {}

    /**
     * Computes the raw SHA-256 digest of a payload.
     *
     * @param payload the exact bytes that were, or will be, transmitted
     * @return the 32 raw digest bytes
     */
    public static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance(JCA_ALGORITHM).digest(payload);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated on every conformant JVM; its absence is not a recoverable state.
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    /**
     * Computes the RFC 9530 {@code Content-Digest} field value for a payload.
     *
     * @param payload the exact bytes that were, or will be, transmitted
     * @return a field value of the form {@code sha-256=:<base64>:}
     */
    public static String contentDigest(byte[] payload) {
        return ALGORITHM_TOKEN + "=:" + Base64.getEncoder().encodeToString(sha256(payload)) + ":";
    }

    /**
     * Verifies a payload against an expected RFC 9530 {@code Content-Digest} field value.
     *
     * <p>The comparison is constant-time. It need not be — an accidental-corruption check has no
     * secret to leak — but doing it anyway costs nothing, keeps the one digest-comparison idiom in
     * the codebase timing-safe, and removes any doubt for a reader about whether this value was ever
     * security-relevant.
     *
     * @param payload the received bytes
     * @param expectedFieldValue the {@code Content-Digest} value the sender supplied
     * @return whether the payload matches, i.e. arrived intact; {@code false} for any mismatch,
     *     including a malformed, empty, or unsupported-algorithm field value
     */
    public static boolean matches(byte[] payload, String expectedFieldValue) {
        byte[] expected = decode(expectedFieldValue);
        if (expected == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(payload), expected);
    }

    /**
     * Parses the raw digest bytes out of an RFC 9530 field value, tolerating only the one algorithm
     * this game speaks.
     *
     * <p>An RFC 9530 field may in general list several algorithms; a peer that offers only, say,
     * {@code sha-512} is not interoperable with us, and that is reported as a parse failure ({@code
     * null}) rather than papered over — silently accepting an algorithm we do not check would defeat
     * the point of the field.
     *
     * @param fieldValue an RFC 9530 {@code Content-Digest} field value
     * @return the raw digest bytes, or {@code null} if the value is absent, malformed, not
     *     {@code sha-256}, or not the right length
     */
    public static byte[] decode(String fieldValue) {
        if (fieldValue == null) {
            return null;
        }
        String trimmed = fieldValue.strip();
        // Only the single-member sha-256 form is accepted. A comma would mean a list this code does
        // not parse; refuse it rather than guess which member to trust.
        String prefix = ALGORITHM_TOKEN + "=:";
        if (!trimmed.startsWith(prefix) || !trimmed.endsWith(":") || trimmed.indexOf(',') >= 0) {
            return null;
        }
        String base64 = trimmed.substring(prefix.length(), trimmed.length() - 1);
        try {
            byte[] raw = Base64.getDecoder().decode(base64);
            return raw.length == SHA256_LENGTH ? raw : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
