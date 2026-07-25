package io.github.stoicswe.eyeandsickle.protocol.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.erdtman.jcs.JsonCanonicalizer;

/**
 * JSON Canonicalization Scheme (RFC 8785), the deterministic byte form that provenance payloads are
 * signed over.
 *
 * <p>Canonicalization is what makes a detached signature reliable: the same logical payload must
 * always produce the same signature input, no matter which language, library or key ordering
 * produced the JSON. {@code docs/architecture/04-item-provenance.md} §4 fixes JCS as the scheme, and
 * every envelope records that choice in its {@code payloadCanonicalization} field so a future
 * migration stays verifiable.
 *
 * <p>The alternative considered and deferred there was COSE/CBOR (RFC 9052), which sidesteps
 * canonicalization entirely because CBOR has a defined deterministic encoding. It is worth revisiting
 * only if wire size actually becomes a problem for the client; until then, JSON stays debuggable and
 * loggable.
 */
public final class JsonCanonicalization {

    private JsonCanonicalization() {}

    /**
     * Canonicalizes a JSON document to its RFC 8785 UTF-8 byte form — the exact bytes to sign or
     * verify.
     *
     * @param json a serialized JSON document
     * @return the canonical UTF-8 encoding
     * @throws IllegalArgumentException if {@code json} is not well-formed JSON
     */
    public static byte[] canonicalize(String json) {
        try {
            return encode(new JsonCanonicalizer(json).getEncodedString());
        } catch (IOException e) {
            // The underlying canonicalizer reports malformed input as IOException. That is a
            // caller error, not an I/O failure, so surface it as one.
            throw new IllegalArgumentException("Not well-formed JSON; cannot canonicalize", e);
        }
    }

    /**
     * Canonicalizes a JSON document and returns it as a string, for logging and for the
     * player-facing item-history view.
     *
     * @param json a serialized JSON document
     * @return the canonical form
     * @throws IllegalArgumentException if {@code json} is not well-formed JSON
     */
    public static String canonicalizeToString(String json) {
        return new String(canonicalize(json), StandardCharsets.UTF_8);
    }

    /**
     * Convenience overload for callers that already hold UTF-8 bytes.
     *
     * @param json a serialized JSON document, UTF-8 encoded
     * @return the canonical UTF-8 encoding
     * @throws IllegalArgumentException if {@code json} is not well-formed JSON, or contains invalid
     *     Unicode — the same contract as the {@link #canonicalize(String)} overload, deliberately
     */
    public static byte[] canonicalize(byte[] json) {
        try {
            return encode(new JsonCanonicalizer(json).getEncodedString());
        } catch (IOException e) {
            throw new IllegalArgumentException("Not well-formed JSON; cannot canonicalize", e);
        }
    }

    /**
     * Encodes the canonical form to UTF-8, refusing invalid Unicode first.
     *
     * <p>RFC 8785 §3.2.2.2 requires a conformant implementation to stop with an error on invalid
     * Unicode. The bundled canonicalizer does not — it passes a lone surrogate through, and Java's
     * UTF-8 encoder then silently substitutes {@code '?'} for it.
     *
     * <p>That substitution is a <strong>signature-collision primitive</strong>, which is why this is
     * enforced here rather than left as a conformance footnote. Two payloads differing only in
     * <em>which</em> invalid surrogate they carry encode to identical bytes, so a single signature
     * covers both — and a verifier that accepted one has, without knowing it, accepted the other. Our
     * own producers never emit invalid Unicode, but a federated peer's payload is untrusted input and
     * a verifier is precisely where untrusted input arrives.
     */
    private static byte[] encode(String canonical) {
        for (int i = 0; i < canonical.length(); i++) {
            char c = canonical.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= canonical.length() || !Character.isLowSurrogate(canonical.charAt(i + 1))) {
                    throw new IllegalArgumentException(
                            "Invalid Unicode: unpaired high surrogate at index " + i + " (RFC 8785 §3.2.2.2)");
                }
                i++; // consume the low surrogate of a valid pair
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException(
                        "Invalid Unicode: unpaired low surrogate at index " + i + " (RFC 8785 §3.2.2.2)");
            }
        }
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
}
