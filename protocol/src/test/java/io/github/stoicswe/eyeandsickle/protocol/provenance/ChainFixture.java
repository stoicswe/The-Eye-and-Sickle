package io.github.stoicswe.eyeandsickle.protocol.provenance;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds real, really-signed provenance chains for the verifier tests.
 *
 * <p>Nothing here is mocked. Every signature is a genuine Ed25519 signature over the genuine
 * canonical bytes, and every {@code prevRecordHash} is a genuine digest of its predecessor — because
 * a test suite for this verifier that stubbed the crypto would only prove the control flow, and the
 * control flow is not the part anyone is worried about.
 *
 * <p>Keys are minted lazily per DID, so a test that mentions a rogue server simply mentions it and
 * gets a working key pair for it. Timestamps come from a fixture-local counter rather than a clock,
 * so a chain built today and the same chain built in a year are byte-identical.
 */
final class ChainFixture {

    static final UUID ITEM_ID = UUID.fromString("2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777");
    static final String ITEM_TYPE = "hacking_tool_tier2";
    static final String HOME_SERVER = "did:plc:homeserver0000000000";
    static final String ROGUE_SERVER = "did:plc:rogueserver000000000";
    static final String HOLDER = "did:plc:holder00000000000000";
    static final String OTHER_HOLDER = "did:plc:holder11111111111111";
    static final String DUEL_ID = "0a9f-4c2e";

    /** The instant every test judges timestamps against. Fixed, so nothing here depends on today. */
    static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    /** Tolerated clock drift between self-hosted servers. An operational number, not a game value. */
    static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final Map<String, KeyPair> keysByDid = new LinkedHashMap<>();
    private Instant recordClock = Instant.parse("2026-07-01T00:00:00Z");
    private int nonceCounter;

    // ---------------------------------------------------------------- identities

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

    static List<String> validators(int count) {
        List<String> dids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            dids.add("did:plc:validator" + i);
        }
        return dids;
    }

    /** A committee whose members all weigh the same, so a test can isolate the count rule. */
    static QuorumCommittee equallyWeighted(String duelId, List<String> validatorDids) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String did : validatorDids) {
            weights.put(did, 1.0);
        }
        return new QuorumCommittee(duelId, weights);
    }

    // ---------------------------------------------------------------- payloads

    String nextNonce() {
        return "nonce-" + (++nonceCounter);
    }

    String nextTimestamp() {
        recordClock = recordClock.plus(Duration.ofHours(1));
        return recordClock.toString();
    }

    ProvenancePayload genesis() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("power", 42);
        attrs.put("durability", 0.87);
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                ITEM_ID,
                ITEM_TYPE,
                attrs,
                ProvenanceEventType.INITIAL_MINT,
                HOLDER,
                HOME_SERVER,
                null,
                0,
                nextTimestamp(),
                nextNonce());
    }

    /** A correctly linked successor: hash of {@code previous}, depth + 1, a later timestamp. */
    ProvenancePayload following(
            ProvenancePayload previous, ProvenanceEventType eventType, String holderDid, String issuerDid) {
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                previous.itemId(),
                previous.itemType(),
                previous.itemAttrs(),
                eventType,
                holderDid,
                issuerDid,
                RecordHash.of(previous),
                previous.chainDepth() + 1,
                nextTimestamp(),
                nextNonce());
    }

    // ---------------------------------------------------------------- envelopes

    SignatureBlock sign(ProvenancePayload payload, String signerDid) {
        byte[] signature =
                Ed25519Signatures.sign(keysOf(signerDid).getPrivate(), ProvenanceJson.canonicalBytes(payload));
        return SignatureBlock.eddsa(
                kidOf(signerDid), Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    /** Signed by whoever the payload names as issuer — the honest case. */
    ProvenanceEnvelope singleIssuer(ProvenancePayload payload) {
        return ProvenanceEnvelope.singleIssuer(payload, sign(payload, payload.issuerDid()));
    }

    ProvenanceEnvelope quorum(ProvenancePayload payload, List<String> signingValidators) {
        List<SignatureBlock> blocks = new ArrayList<>();
        for (String did : signingValidators) {
            blocks.add(sign(payload, did));
        }
        return ProvenanceEnvelope.quorum(payload, blocks);
    }

    /** A mint followed by {@code length - 1} trades, all issued and signed by the home server. */
    List<ProvenanceEnvelope> validChain(int length) {
        List<ProvenanceEnvelope> chain = new ArrayList<>();
        ProvenancePayload payload = genesis();
        chain.add(singleIssuer(payload));
        for (int depth = 1; depth < length; depth++) {
            payload = following(payload, ProvenanceEventType.TRADE, HOLDER, HOME_SERVER);
            chain.add(singleIssuer(payload));
        }
        return chain;
    }

    /** Replaces one record with a re-signed variant, leaving the rest of the chain alone. */
    static List<ProvenanceEnvelope> replacing(
            List<ProvenanceEnvelope> chain, int position, ProvenanceEnvelope replacement) {
        List<ProvenanceEnvelope> copy = new ArrayList<>(chain);
        copy.set(position, replacement);
        return copy;
    }

    // ---------------------------------------------------------------- contexts

    ChainVerificationContext context() {
        return context(DuelCommitteeLookup.none());
    }

    ChainVerificationContext context(DuelCommitteeLookup duelCommittees) {
        return new ChainVerificationContext(
                directory(), IssuerAuthority.allowing(List.of(HOME_SERVER)), duelCommittees, NOW, MAX_FUTURE_SKEW);
    }

    ChainVerificationContext contextWithKeys(SigningKeyDirectory keys) {
        return new ChainVerificationContext(
                keys, IssuerAuthority.allowing(List.of(HOME_SERVER)), DuelCommitteeLookup.none(), NOW, MAX_FUTURE_SKEW);
    }

    // ---------------------------------------------------------------- payload editing

    /**
     * A mutable copy of a payload, so a test can express "this record but with a backwards
     * timestamp" without restating eleven fields and burying the one that matters.
     */
    static final class Edit {

        private int recordVersion;
        private UUID itemId;
        private String itemType;
        private Map<String, Object> itemAttrs;
        private ProvenanceEventType eventType;
        private String holderDid;
        private String issuerDid;
        private String prevRecordHash;
        private int chainDepth;
        private String timestamp;
        private String nonce;

        static Edit of(ProvenancePayload payload) {
            Edit edit = new Edit();
            edit.recordVersion = payload.recordVersion();
            edit.itemId = payload.itemId();
            edit.itemType = payload.itemType();
            edit.itemAttrs = payload.itemAttrs();
            edit.eventType = payload.eventType();
            edit.holderDid = payload.holderDid();
            edit.issuerDid = payload.issuerDid();
            edit.prevRecordHash = payload.prevRecordHash();
            edit.chainDepth = payload.chainDepth();
            edit.timestamp = payload.timestamp();
            edit.nonce = payload.nonce();
            return edit;
        }

        Edit recordVersion(int value) {
            this.recordVersion = value;
            return this;
        }

        Edit itemId(UUID value) {
            this.itemId = value;
            return this;
        }

        Edit itemAttrs(Map<String, Object> value) {
            this.itemAttrs = value;
            return this;
        }

        Edit eventType(ProvenanceEventType value) {
            this.eventType = value;
            return this;
        }

        Edit holderDid(String value) {
            this.holderDid = value;
            return this;
        }

        Edit issuerDid(String value) {
            this.issuerDid = value;
            return this;
        }

        Edit prevRecordHash(String value) {
            this.prevRecordHash = value;
            return this;
        }

        Edit chainDepth(int value) {
            this.chainDepth = value;
            return this;
        }

        Edit timestamp(String value) {
            this.timestamp = value;
            return this;
        }

        Edit nonce(String value) {
            this.nonce = value;
            return this;
        }

        ProvenancePayload build() {
            return new ProvenancePayload(
                    recordVersion,
                    itemId,
                    itemType,
                    itemAttrs,
                    eventType,
                    holderDid,
                    issuerDid,
                    prevRecordHash,
                    chainDepth,
                    timestamp,
                    nonce);
        }
    }
}
