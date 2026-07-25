package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import io.github.stoicswe.eyeandsickle.server.items.ServerSigningIdentity;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds real, really-signed provenance chains for the migration integration tests — a lean, self-contained
 * equivalent of the items slice's package-private {@code TestChains}, which is not on this package's test
 * classpath.
 *
 * <p>Nothing is mocked: every signature is a genuine Ed25519 signature over the genuine canonical bytes, so
 * a chain that is asserted to verify is exercising the crypto, and a tampered one is asserted to fail
 * against it. The home key is exposed as a {@link ServerSigningIdentity} so the same key that signed a
 * chain resolves it, exactly as a server verifies items it minted itself.
 */
final class MigrationTestChains {

    static final UUID ITEM_ID = UUID.fromString("2f1c7b64-9a1d-4f0e-8c33-6d5b0a91e777");
    static final String ITEM_TYPE = "hacking_tool_tier2";
    static final String HOME_DID = "did:plc:homeserver0000000000";
    static final String HOLDER_DID = CharacterDid.of("did:plc:holder00000000000000", 1);
    static final String OTHER_HOLDER_DID = CharacterDid.of("did:plc:holder11111111111111", 1);

    /** The instant the verifier judges timestamps against. Fixed, so nothing here depends on today. */
    static final java.time.Instant NOW = java.time.Instant.parse("2026-08-01T12:00:00Z");

    static final java.time.Duration MAX_FUTURE_SKEW = java.time.Duration.ofMinutes(5);

    private final KeyPair homeKeys = Ed25519Signatures.generateKeyPair();

    static String kidOf(String did) {
        return did + "#key1";
    }

    private ProvenancePayload genesis() {
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                ITEM_ID,
                ITEM_TYPE,
                Map.of("power", 42),
                ProvenanceEventType.INITIAL_MINT,
                HOLDER_DID,
                HOME_DID,
                null,
                0,
                "2026-07-01T00:00:00Z",
                "nonce-1");
    }

    private SignatureBlock signHome(ProvenancePayload payload) {
        byte[] signature = Ed25519Signatures.sign(homeKeys.getPrivate(), ProvenanceJson.canonicalBytes(payload));
        return SignatureBlock.eddsa(
                kidOf(HOME_DID), Base64.getUrlEncoder().withoutPadding().encodeToString(signature));
    }

    /** A sound, single-record chain (genesis mint) held by {@link #HOLDER_DID}, as verbatim documents. */
    List<String> validDocuments() {
        ProvenancePayload genesis = genesis();
        return List.of(ProvenanceJson.writeEnvelope(ProvenanceEnvelope.singleIssuer(genesis, signHome(genesis))));
    }

    /** The classic "buff the item, keep the signature" tamper — a well-formed chain that fails verification. */
    List<String> tamperedDocuments() {
        ProvenancePayload genesis = genesis();
        SignatureBlock honest = signHome(genesis); // signed over the ORIGINAL bytes
        ProvenancePayload buffed = new ProvenancePayload(
                genesis.recordVersion(),
                genesis.itemId(),
                genesis.itemType(),
                Map.of("power", 9001), // rewritten stats
                genesis.eventType(),
                genesis.holderDid(),
                genesis.issuerDid(),
                genesis.prevRecordHash(),
                genesis.chainDepth(),
                genesis.timestamp(),
                genesis.nonce());
        return List.of(ProvenanceJson.writeEnvelope(ProvenanceEnvelope.singleIssuer(buffed, honest)));
    }

    /** This server's signing identity, holding the home key so a chain it signed resolves offline. */
    ServerSigningIdentity signingIdentity() {
        return new ServerSigningIdentity() {
            @Override
            public Map<String, PublicKey> localVerificationKeys() {
                return Map.of(kidOf(HOME_DID), homeKeys.getPublic());
            }

            @Override
            public String issuerDidOrNull() {
                return HOME_DID;
            }

            @Override
            public String issuerDid() {
                return HOME_DID;
            }

            @Override
            public String signingKeyId() {
                return kidOf(HOME_DID);
            }

            @Override
            public SignatureBlock sign(byte[] canonicalPayloadBytes) {
                return SignatureBlock.eddsa(
                        kidOf(HOME_DID),
                        Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(Ed25519Signatures.sign(homeKeys.getPrivate(), canonicalPayloadBytes)));
            }

            @Override
            public boolean canSign() {
                return true;
            }
        };
    }
}
