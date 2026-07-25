package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledCommittee;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds real, really-signed validator votes for the federation tests — the same "nothing is mocked"
 * discipline the protocol {@code ChainFixture} follows.
 *
 * <p>Every signature is a genuine Ed25519 signature over the genuine canonical bytes of a {@code
 * duel_grant} outcome. A suite for the quorum adjudicator and the equivocation detector that stubbed
 * the crypto would only prove the control flow, and the control flow is not the part Invariant I15
 * depends on. Keys are minted lazily per DID, so a test that mentions a rogue validator simply
 * mentions it and gets a working key pair.
 */
final class FederationFixture {

    /** A single item every duel in these tests fights over; its identity is irrelevant to the vote logic. */
    static final UUID ITEM_ID = UUID.fromString("2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777");

    static final String ITEM_TYPE = "hacking_tool_tier2";

    /** Two candidate winners, so two conflicting outcomes have different canonical bytes. */
    static final String HOLDER_A = "did:plc:holderaaaaaaaaaaaaaaa";

    static final String HOLDER_B = "did:plc:holderbbbbbbbbbbbbbbb";

    private final Map<String, KeyPair> keysByDid = new LinkedHashMap<>();

    // ------------------------------------------------------------------ identities

    KeyPair keysOf(String did) {
        return keysByDid.computeIfAbsent(did, ignored -> Ed25519Signatures.generateKeyPair());
    }

    static String kidOf(String did) {
        return did + "#key1";
    }

    /** A live view, so a DID first mentioned after the directory was taken still resolves. */
    SigningKeyDirectory directory() {
        return kid -> {
            int fragment = kid.indexOf('#');
            KeyPair pair = keysByDid.get(fragment < 0 ? kid : kid.substring(0, fragment));
            return pair == null ? null : pair.getPublic();
        };
    }

    static List<String> validatorDids(int count) {
        List<String> dids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            dids.add("did:plc:validator" + i);
        }
        return dids;
    }

    // ------------------------------------------------------------------ outcomes

    /**
     * A {@code duel_grant} outcome awarding the item to {@code winnerDid}. Two different winners
     * produce two different canonical byte-strings, which is what makes one an equivocation of the
     * other.
     */
    ProvenancePayload outcome(String duelId, String winnerDid) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("power", 42);
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                ITEM_ID,
                ITEM_TYPE,
                attrs,
                ProvenanceEventType.DUEL_GRANT,
                winnerDid,
                ValidatorSignature.QUORUM_ISSUER_PREFIX + duelId,
                // A duel_grant is a change of hands, so it is not genesis: depth > 0 with a predecessor.
                "1111111111111111111111111111111111111111111111111111111111111111",
                1,
                "2026-07-24T00:00:00Z",
                "nonce-" + duelId + '-' + winnerDid);
    }

    // ------------------------------------------------------------------ signatures

    SignatureBlock sign(ProvenancePayload payload, String signerDid) {
        return signWith(payload, signerDid, kidOf(signerDid));
    }

    /**
     * Signs with {@code signerDid}'s private key but labels the block with an arbitrary {@code kid} —
     * for the attack where a forged block names a validator whose key will not verify it.
     */
    SignatureBlock signWith(ProvenancePayload payload, String privateKeyDid, String kid) {
        byte[] signature =
                Ed25519Signatures.sign(keysOf(privateKeyDid).getPrivate(), ProvenanceJson.canonicalBytes(payload));
        return SignatureBlock.eddsa(kid, Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    /** A validator's honest vote: it signs the outcome it is voting for with its own key. */
    ValidatorSignature vote(String duelId, String winnerDid, String signerDid) {
        ProvenancePayload payload = outcome(duelId, winnerDid);
        return new ValidatorSignature(payload, sign(payload, signerDid));
    }

    /** A vote whose signature block carries a syntactically fine but cryptographically bogus signature. */
    ValidatorSignature voteWithBadSignature(String duelId, String winnerDid, String signerDid) {
        ProvenancePayload payload = outcome(duelId, winnerDid);
        // 64 zero bytes is a well-formed Ed25519 signature length that will not verify.
        String bogus = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]);
        return new ValidatorSignature(payload, SignatureBlock.eddsa(kidOf(signerDid), bogus));
    }

    // ------------------------------------------------------------------ committees

    /** An equally-weighted committee (weight 1 each), to isolate the count rule from the weight rule. */
    QuorumCommittee committee(String duelId, List<String> validatorDids) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String did : validatorDids) {
            weights.put(did, 1.0);
        }
        return new QuorumCommittee(duelId, weights);
    }

    /** A committee whose members carry the given weights, DID {@code validator1..N} in order. */
    static QuorumCommittee weightedCommittee(String duelId, double... weights) {
        Map<String, Double> sampled = new LinkedHashMap<>();
        for (int i = 0; i < weights.length; i++) {
            sampled.put("did:plc:validator" + (i + 1), weights[i]);
        }
        return new QuorumCommittee(duelId, sampled);
    }

    /** A sampled committee (reputation/uptime carried) for the repositories and the service. */
    static SampledCommittee sampledCommittee(String duelId, List<SampledValidator> members) {
        return new SampledCommittee(duelId, members);
    }
}
