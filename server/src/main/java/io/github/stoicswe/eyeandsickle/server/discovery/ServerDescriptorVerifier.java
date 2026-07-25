package io.github.stoicswe.eyeandsickle.server.discovery;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.server.discovery.ServerDescriptorCodec.DescriptorCodecException;
import io.github.stoicswe.eyeandsickle.server.discovery.ServerDescriptorCodec.ParsedEnvelope;
import java.security.PublicKey;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns untrusted self-descriptor bytes into a verified {@link ServerDescriptor}, or a typed refusal.
 *
 * <h2>This is the trust boundary of the discovery layer</h2>
 *
 * A descriptor arrives from a server this one does not control ({@code
 * docs/architecture/03-server-and-federation.md} §1). Nothing about it is believed until its signature
 * is checked against the key its DID resolves to — a server may only speak for itself, so the signing
 * {@code kid} must belong to the {@code peerDid} it describes. Only then is the descriptor allowed to
 * become storable directory state. Everything downstream ({@link PeerDirectoryService}, gossip) can
 * therefore assume a {@code ServerDescriptor} was verified, because the only way to make one is through
 * here.
 *
 * <h2>Deterministic, clock-injected</h2>
 *
 * The current instant is a parameter, never read from a wall clock inside verification — the same
 * discipline as the protocol's provenance verifier and {@code TransportKeyAttestation.isValidAt}. It
 * makes verification reproducible and testable, and it keeps the one place a time comparison matters
 * (the transport-key validity window) honest about what "now" it used.
 *
 * <h2>What it checks, cheap and structural first</h2>
 *
 * size cap &rarr; JSON shape &rarr; required fields &rarr; DID and endpoint shape (mirroring the {@code
 * is_did} and endpoint CHECK constraints so a bad value is refused before the database has to) &rarr;
 * transport key decodes as X25519 &rarr; key window sane and not-yet-in-the-far-future &rarr; signer
 * owns the DID &rarr; algorithm is EdDSA &rarr; key resolves &rarr; signature covers the canonical
 * bytes. The expensive elliptic-curve check runs last.
 */
@Component
public class ServerDescriptorVerifier {

    /** Mirrors {@code is_did} in {@code V2__core_schema.sql} so a malformed DID is refused before the INSERT. */
    private static final Pattern DID_SHAPE = Pattern.compile("^did:[a-z0-9]+:[A-Za-z0-9._%:-]+$");

    private static final int DID_MAX_LENGTH = 512;

    /** Mirrors {@code ck_federation_peers_endpoint}. */
    private static final Pattern ENDPOINT_SHAPE = Pattern.compile("^https?://[^\\s]+$");

    private static final int ENDPOINT_MAX_LENGTH = 2048;

    /** Self-asserted capabilities are advisory; cap their count and size so they cannot be an abuse vector. */
    private static final int MAX_CAPABILITIES = 32;

    private static final int MAX_CAPABILITY_LENGTH = 64;

    private final DiscoveryProperties properties;
    private final PeerKeyResolver keyResolver;

    /**
     * @param properties the size cap and clock-skew tolerance
     * @param keyResolver resolves a signing {@code kid} to its Ed25519 key; the identity slice provides
     *     the real implementation, and until it does an empty resolver simply refuses every descriptor
     */
    public ServerDescriptorVerifier(DiscoveryProperties properties, PeerKeyResolver keyResolver) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
    }

    /**
     * Verifies a raw self-descriptor envelope.
     *
     * @param rawEnvelope the received JSON, verbatim; stored unchanged if accepted
     * @param now the instant to evaluate the transport-key window against
     * @return an accepted {@link ServerDescriptor} or a typed refusal
     */
    public DescriptorVerification verify(String rawEnvelope, Instant now) {
        Objects.requireNonNull(rawEnvelope, "rawEnvelope");
        Objects.requireNonNull(now, "now");

        ParsedEnvelope parsed;
        try {
            parsed = ServerDescriptorCodec.parse(rawEnvelope, properties.maxDescriptorBytes());
        } catch (DescriptorCodecException e) {
            return DescriptorVerification.rejected(e.fault(), e.getMessage());
        }

        String peerDid = string(parsed, ServerDescriptorCodec.PEER_DID);
        if (peerDid == null) {
            return reject(DescriptorFault.MISSING_FIELD, "descriptor has no '" + ServerDescriptorCodec.PEER_DID + "'");
        }
        if (peerDid.length() > DID_MAX_LENGTH || !DID_SHAPE.matcher(peerDid).matches()) {
            return reject(DescriptorFault.MALFORMED_DID, "'" + peerDid + "' is not DID-shaped");
        }

        String endpoint = string(parsed, ServerDescriptorCodec.ENDPOINT);
        if (endpoint == null) {
            return reject(DescriptorFault.MISSING_FIELD, "descriptor has no '" + ServerDescriptorCodec.ENDPOINT + "'");
        }
        if (endpoint.length() > ENDPOINT_MAX_LENGTH
                || !ENDPOINT_SHAPE.matcher(endpoint).matches()) {
            return reject(DescriptorFault.MALFORMED_ENDPOINT, "endpoint '" + endpoint + "' is not an http(s) URL");
        }

        Long sequence = integer(parsed, ServerDescriptorCodec.SEQUENCE);
        if (sequence == null) {
            return reject(DescriptorFault.MALFORMED_SEQUENCE, "sequence is missing or not an integer");
        }
        if (sequence < 0) {
            return reject(DescriptorFault.MALFORMED_SEQUENCE, "sequence is negative: " + sequence);
        }

        String transportKeyB64 = string(parsed, ServerDescriptorCodec.TRANSPORT_PUBLIC_KEY);
        if (transportKeyB64 == null) {
            return reject(
                    DescriptorFault.MISSING_FIELD,
                    "descriptor has no '" + ServerDescriptorCodec.TRANSPORT_PUBLIC_KEY + "'");
        }
        byte[] transportKey;
        try {
            transportKey = ServerDescriptorCodec.decodeBase64Url(transportKeyB64);
            // Decodes and validates as an X25519 key; the return value is discarded, we only needed the
            // check. Storing the X.509 bytes, the same form the column and the channel expect.
            io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange.decodePublicKey(transportKey);
        } catch (RuntimeException e) {
            return reject(DescriptorFault.MALFORMED_TRANSPORT_KEY, "transport key is not a valid X25519 key");
        }

        Instant notBefore;
        Instant notAfter;
        try {
            notBefore = optionalInstant(parsed, ServerDescriptorCodec.TRANSPORT_KEY_NOT_BEFORE);
            notAfter = optionalInstant(parsed, ServerDescriptorCodec.TRANSPORT_KEY_NOT_AFTER);
        } catch (DateTimeParseException e) {
            return reject(DescriptorFault.MALFORMED_KEY_WINDOW, "transport-key window is not an ISO-8601 instant");
        }
        if (notBefore != null && notAfter != null && !notAfter.isAfter(notBefore)) {
            return reject(DescriptorFault.MALFORMED_KEY_WINDOW, "transport-key notAfter is not after notBefore");
        }
        if (notBefore != null && notBefore.isAfter(now.plus(properties.clockSkewTolerance()))) {
            return reject(
                    DescriptorFault.NOT_YET_VALID,
                    "transport key does not become valid until " + notBefore + ", beyond the tolerated skew");
        }

        // A server may only sign for itself: the signing key must belong to the DID being described.
        String signerDid = didOf(parsed.kid());
        if (!peerDid.equals(signerDid)) {
            return reject(
                    DescriptorFault.SIGNER_NOT_OWNER,
                    "signed by " + signerDid + " but the descriptor is for " + peerDid);
        }
        if (!Ed25519Signatures.JOSE_ALG.equals(parsed.alg())) {
            return reject(
                    DescriptorFault.WRONG_SIGNATURE_ALGORITHM,
                    "signature alg is '" + parsed.alg() + "', not " + Ed25519Signatures.JOSE_ALG);
        }
        PublicKey key = keyResolver.resolve(parsed.kid());
        if (key == null) {
            return reject(DescriptorFault.UNKNOWN_SIGNING_KEY, "no key resolves for kid '" + parsed.kid() + "'");
        }
        byte[] signature;
        try {
            signature = ServerDescriptorCodec.decodeBase64Url(parsed.sig());
        } catch (RuntimeException e) {
            return reject(DescriptorFault.INVALID_SIGNATURE, "signature is not decodable base64url");
        }
        if (!Ed25519Signatures.verify(key, parsed.canonicalBytes(), signature)) {
            return reject(DescriptorFault.INVALID_SIGNATURE, "signature does not cover the canonical descriptor bytes");
        }

        ServerDescriptor descriptor = new ServerDescriptor(
                peerDid,
                endpoint,
                transportKey,
                string(parsed, ServerDescriptorCodec.TRANSPORT_KEY_ID),
                notBefore,
                notAfter,
                sequence,
                capabilities(parsed),
                optionalInstantLenient(parsed, ServerDescriptorCodec.ISSUED_AT),
                rawEnvelope);
        return DescriptorVerification.accepted(descriptor);
    }

    private static DescriptorVerification reject(DescriptorFault fault, String detail) {
        return DescriptorVerification.rejected(fault, detail);
    }

    private static String didOf(String kid) {
        int fragment = kid.indexOf('#');
        return fragment < 0 ? kid : kid.substring(0, fragment);
    }

    private static String string(ParsedEnvelope parsed, String key) {
        Object value = parsed.descriptor().get(key);
        return value instanceof String s ? s : null;
    }

    private static Long integer(ParsedEnvelope parsed, String key) {
        Object value = parsed.descriptor().get(key);
        // Accept any integral JSON number; reject a fractional one, which is never a version counter.
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof java.math.BigInteger b) {
            try {
                return b.longValueExact();
            } catch (ArithmeticException overflow) {
                // A sequence larger than Long can hold is not a usable version counter — and it
                // arrives from an untrusted peer. Return null so the caller refuses it as a typed
                // MALFORMED_SEQUENCE, rather than letting the exception escape verify() and blow up
                // the whole gossip round. Every other malformation here is a typed refusal; so is this.
                return null;
            }
        }
        return null;
    }

    private static Instant optionalInstant(ParsedEnvelope parsed, String key) {
        String value = string(parsed, key);
        return value == null ? null : Instant.parse(value);
    }

    private static Instant optionalInstantLenient(ParsedEnvelope parsed, String key) {
        String value = string(parsed, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            // issuedAt is advisory only and never orders anything; an unparseable one is dropped rather
            // than failing an otherwise-valid descriptor.
            return null;
        }
    }

    private static List<String> capabilities(ParsedEnvelope parsed) {
        Object value = parsed.descriptor().get(ServerDescriptorCodec.CAPABILITIES);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> capabilities = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof String s && s.length() <= MAX_CAPABILITY_LENGTH) {
                capabilities.add(s);
            }
            if (capabilities.size() >= MAX_CAPABILITIES) {
                break;
            }
        }
        return capabilities;
    }
}
