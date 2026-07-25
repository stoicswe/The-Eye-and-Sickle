package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The value that goes in the next record's {@code prevRecordHash}, and the definition of what
 * "chains to" means.
 *
 * <p>{@code docs/architecture/04-item-provenance.md} §2 specifies the field only as
 * "sha256-of-previous-record-in-chain-or-null-if-genesis", and §6 fixes that chains are per-item.
 * Everything else below is a <strong>[PROPOSAL]</strong> filling that gap, and needs confirming
 * before anything signs for real — once a chain exists in the wild, this format cannot change
 * without invalidating it.
 *
 * <h2>[PROPOSAL] What is hashed: the canonical payload bytes</h2>
 *
 * The digest covers {@link ProvenanceJson#canonicalBytes(ProvenancePayload)} — the same bytes the
 * predecessor's signature covers — and <em>not</em> the envelope.
 *
 * <p>The consequence is worth stating plainly, because it is the whole point. Since the hash is over
 * exactly the signed bytes, a record's link to its predecessor is broken by any change to the
 * predecessor's content and by nothing else. Re-signing a record, adding a validator's signature to
 * a duel outcome, or re-serializing an envelope with different spacing all leave the chain intact,
 * while altering one item attribute three records back breaks every link after it. Hashing the
 * envelope instead would make the chain fragile to signature-set changes that carry no meaning, and
 * would let the same payload have two different hashes.
 *
 * <h2>[PROPOSAL] The string form: {@code sha256-} + 64 lowercase hex characters</h2>
 *
 * <ul>
 *   <li><strong>Algorithm-prefixed</strong> so the value is self-describing. A future migration to a
 *       different digest then produces strings an old verifier <em>rejects</em> rather than
 *       mis-compares, and the prefix matches the doc's own placeholder text.
 *   <li><strong>Lowercase hex, not base64url</strong>, because this string is itself part of the
 *       next record's signed bytes. Base64url has padded and unpadded spellings and more than one
 *       alphabet in the wild; two implementations disagreeing on which to emit would produce two
 *       different chains for the same history, and the disagreement would look like cheating. Hex
 *       has exactly one rendering once case is fixed. The 21 extra bytes per record buy that.
 *   <li><strong>Not multibase/multihash</strong>, which solves the same self-description problem with
 *       a dependency and a varint the rest of this codebase has no use for.
 * </ul>
 */
public final class RecordHash {

    /** Marks which digest produced the value. Part of the string, and part of what gets signed. */
    public static final String ALGORITHM_PREFIX = "sha256-";

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final int HEX_LENGTH = 64;
    private static final HexFormat HEX = HexFormat.of();

    private RecordHash() {}

    /**
     * The hash a successor record must carry in its {@code prevRecordHash}.
     *
     * @param payload the predecessor's payload
     * @return {@code sha256-<hex>}
     */
    public static String of(ProvenancePayload payload) {
        Objects.requireNonNull(payload, "payload");
        return ofCanonicalBytes(ProvenanceJson.canonicalBytes(payload));
    }

    /**
     * The same hash, for a caller that already canonicalized — the verifier, which needs those bytes
     * for signature checking anyway and should not canonicalize the same payload twice.
     *
     * @param canonicalBytes output of {@link ProvenanceJson#canonicalBytes(ProvenancePayload)}
     * @return {@code sha256-<hex>}
     */
    public static String ofCanonicalBytes(byte[] canonicalBytes) {
        Objects.requireNonNull(canonicalBytes, "canonicalBytes");
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return ALGORITHM_PREFIX + HEX.formatHex(digest.digest(canonicalBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable; provenance chains cannot be linked", e);
        }
    }

    /**
     * Whether a string is shaped like a record hash this build understands.
     *
     * <p>Used to tell "this record names a digest we cannot interpret" apart from "this record names
     * the wrong predecessor" — a distinction that matters when someone is debugging a federation
     * dispute at two in the morning.
     *
     * @param recordHash the candidate, may be {@code null}
     * @return whether it is {@code sha256-} followed by 64 lowercase hex characters
     */
    public static boolean isWellFormed(String recordHash) {
        if (recordHash == null || recordHash.length() != ALGORITHM_PREFIX.length() + HEX_LENGTH) {
            return false;
        }
        if (!recordHash.startsWith(ALGORITHM_PREFIX)) {
            return false;
        }
        for (int i = ALGORITHM_PREFIX.length(); i < recordHash.length(); i++) {
            char c = recordHash.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hexDigit) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a record's {@code prevRecordHash} really names the given predecessor.
     *
     * <p>Plain equality is right here: both sides are public values, so there is no secret for a
     * timing side-channel to leak.
     *
     * @param prevRecordHash the successor's claim, may be {@code null} for a genesis record
     * @param predecessor the record it should be chaining to
     * @return whether the link holds
     */
    public static boolean links(String prevRecordHash, ProvenancePayload predecessor) {
        Objects.requireNonNull(predecessor, "predecessor");
        return prevRecordHash != null && prevRecordHash.equals(of(predecessor));
    }
}
