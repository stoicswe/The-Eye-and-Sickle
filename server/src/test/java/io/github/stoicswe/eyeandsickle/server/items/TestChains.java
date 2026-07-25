package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.provenance.DuelCommitteeLookup;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.RecordHash;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds real, really-signed provenance chains for the server-slice tests.
 *
 * <p>The protocol module has its own {@code ChainFixture}, but it is not on this module's test
 * classpath, so the server slice keeps a lean equivalent here. Nothing is mocked: every signature is a
 * genuine Ed25519 signature over the genuine canonical bytes, so a test that says "this record
 * verifies" is exercising the crypto rather than a stub of it. Keys are minted lazily per DID, and
 * timestamps come from a fixture-local counter rather than a clock, so a chain built today and the
 * same chain built next year are byte-identical.
 */
final class TestChains {

    static final UUID ITEM_ID = UUID.fromString("2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777");
    static final String ITEM_TYPE = "hacking_tool_tier2";
    static final String HOME_DID = "did:plc:homeserver0000000000";
    static final String ROGUE_DID = "did:plc:rogueserver000000000";
    static final String HOLDER = "did:plc:holder00000000000000";
    static final String OTHER_HOLDER = "did:plc:holder11111111111111";

    /**
     * One account DID with two characters in different slots. Their derived character DIDs differ, which is
     * what lets a test show two characters of a single account holding <em>separate</em> items (09 §9). Item
     * ownership keys on the character DID, so these — not {@link #HOLDER} — are what mint/grant/trade stamp.
     */
    static final String ACCOUNT_DID = "did:plc:account00000000000";

    static final CharacterDid CHARACTER_SLOT_1 = new CharacterDid(ACCOUNT_DID, 1);
    static final CharacterDid CHARACTER_SLOT_2 = new CharacterDid(ACCOUNT_DID, 2);

    /** The instant the verifier judges timestamps against. Fixed, so nothing here depends on today. */
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

    PublicKey publicKeyOf(String did) {
        return keysOf(did).getPublic();
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
        return genesis(HOME_DID);
    }

    ProvenancePayload genesis(String issuerDid) {
        return genesisForHolder(issuerDid, HOLDER);
    }

    /**
     * A genesis payload minted to an arbitrary holder string — used to build a chain whose holder is a
     * character DID ({@link #CHARACTER_SLOT_1}), showing ingress records a character holder verbatim (09 §9).
     */
    ProvenancePayload genesisForHolder(String issuerDid, String holderDid) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("power", 42);
        attrs.put("durability", 0.87);
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                ITEM_ID,
                ITEM_TYPE,
                attrs,
                ProvenanceEventType.INITIAL_MINT,
                holderDid,
                issuerDid,
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

    /** A genesis-position payload (depth 0, no predecessor) carrying a non-mint event type. */
    ProvenancePayload genesisWithEvent(ProvenanceEventType eventType) {
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                ITEM_ID,
                ITEM_TYPE,
                Map.of("power", 42),
                eventType,
                HOLDER,
                HOME_DID,
                null,
                0,
                nextTimestamp(),
                nextNonce());
    }

    /** The same payload but chained to a different (here: wrong) predecessor hash. */
    static ProvenancePayload withPrevRecordHash(ProvenancePayload payload, String prevRecordHash) {
        return new ProvenancePayload(
                payload.recordVersion(),
                payload.itemId(),
                payload.itemType(),
                payload.itemAttrs(),
                payload.eventType(),
                payload.holderDid(),
                payload.issuerDid(),
                prevRecordHash,
                payload.chainDepth(),
                payload.timestamp(),
                payload.nonce());
    }

    /** The same payload with rewritten stats — the classic "buff my item, keep the signature" tamper. */
    static ProvenancePayload withAttrs(ProvenancePayload payload, Map<String, Object> attrs) {
        return new ProvenancePayload(
                payload.recordVersion(),
                payload.itemId(),
                payload.itemType(),
                attrs,
                payload.eventType(),
                payload.holderDid(),
                payload.issuerDid(),
                payload.prevRecordHash(),
                payload.chainDepth(),
                payload.timestamp(),
                payload.nonce());
    }

    // ---------------------------------------------------------------- envelopes

    SignatureBlock sign(ProvenancePayload payload, String signerDid) {
        return signWithKid(payload, signerDid, kidOf(signerDid));
    }

    /** Signs with {@code signerDid}'s private key but labels the block with an arbitrary {@code kid}. */
    SignatureBlock signWithKid(ProvenancePayload payload, String signerDid, String kid) {
        byte[] signature =
                Ed25519Signatures.sign(keysOf(signerDid).getPrivate(), ProvenanceJson.canonicalBytes(payload));
        return SignatureBlock.eddsa(kid, Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    /** Signed by whoever the payload names as issuer — the honest case. */
    ProvenanceEnvelope singleIssuer(ProvenancePayload payload) {
        return ProvenanceEnvelope.singleIssuer(payload, sign(payload, payload.issuerDid()));
    }

    /** A mint followed by {@code length - 1} trades, all issued and signed by the home server. */
    List<ProvenanceEnvelope> validChain(int length) {
        List<ProvenanceEnvelope> chain = new ArrayList<>();
        ProvenancePayload payload = genesis();
        chain.add(singleIssuer(payload));
        for (int depth = 1; depth < length; depth++) {
            payload = following(payload, ProvenanceEventType.TRADE, HOLDER, HOME_DID);
            chain.add(singleIssuer(payload));
        }
        return chain;
    }

    /** The verbatim envelope documents the federation layer would hand the ingress service. */
    static List<String> documentsOf(List<ProvenanceEnvelope> chain) {
        return chain.stream().map(ProvenanceJson::writeEnvelope).toList();
    }

    // ---------------------------------------------------------------- verification wiring

    /**
     * A verification service that resolves every minted key and recognizes the given issuers, judging
     * timestamps against {@link #NOW}.
     */
    ProvenanceVerificationService verification(String... recognizedDids) {
        ServerRecognition recognition = ServerRecognition.of(java.util.Set.of(recognizedDids));
        return new ProvenanceVerificationService(
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC),
                new ItemsProperties(MAX_FUTURE_SKEW, null, null, null),
                directory(),
                new ServerIssuerAuthority(recognition),
                DuelCommitteeLookup.none());
    }

    // ---------------------------------------------------------------- determinism helpers

    /**
     * A deterministic {@link SecureRandom}. SHA1PRNG ships with the SUN provider on every standard JVM
     * and produces identical output for identical seeds, which is what lets a nonce-determinism test be
     * meaningful.
     */
    static SecureRandom deterministicRandom(long seed) {
        try {
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(seed);
            return random;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA1PRNG is required for deterministic provenance tests", e);
        }
    }
}
