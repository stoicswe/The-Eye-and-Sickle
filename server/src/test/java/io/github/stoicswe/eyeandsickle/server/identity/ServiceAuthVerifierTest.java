package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.identity.DidResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.HttpFetcher;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Stage 6: verifying a service-auth JWT.
 *
 * <h2>These tests mint REAL tokens</h2>
 *
 * Every case here signs an actual JWT with an actual key and publishes an actual DID document for it,
 * because the failure this class exists to prevent is a verifier that accepts something it should not
 * — and a fixture of pre-baked strings cannot exercise the signature path at all. The
 * {@code secp256k1} cases are the ones that matter most: that curve is what most {@code did:plc}
 * accounts use, and it is the reason BouncyCastle is on the server's classpath.
 */
class ServiceAuthVerifierTest {

    private static final String SERVER_DID = "did:web:home.example";
    private static final String PLAYER_DID = "did:plc:abcdefghijklmnopqrstuvwx";
    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @BeforeAll
    static void registerBouncyCastle() {
        // The server registers this in IdentityConfiguration; a unit test has no context, so it does
        // the same thing. ⚠ Registration must happen BEFORE MultibaseKey probes for a provider — the
        // probe result is cached for the life of the JVM.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ── minting ───────────────────────────────────────────────────────────────────────────────

    private record Account(KeyPair keys, String multibaseKey, String jwsAlgorithm) {}

    private static Account account(String curve) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec(curve));
        KeyPair keys = generator.generateKeyPair();

        int codec = curve.equals("secp256r1") ? 0x1200 : 0xE7;
        return new Account(
                keys,
                multibase(codec, compress((ECPublicKey) keys.getPublic())),
                curve.equals("secp256r1") ? "ES256" : "ES256K");
    }

    private static String didDocument(String multibaseKey) {
        return """
        {"id":"%s",
         "alsoKnownAs":["at://alice.example"],
         "verificationMethod":[{"id":"%s#atproto","type":"Multikey","controller":"%s",
           "publicKeyMultibase":"%s"}],
         "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer",
           "serviceEndpoint":"https://pds.example"}]}
        """.formatted(PLAYER_DID, PLAYER_DID, PLAYER_DID, multibaseKey);
    }

    /** Signs a real compact JWS in JOSE's raw R||S form. */
    private static String mint(Account account, String headerJson, String payloadJson) throws Exception {
        String input = b64url(headerJson) + "." + b64url(payloadJson);
        Signature signer = Signature.getInstance("SHA256WITHPLAIN-ECDSA", "BC");
        signer.initSign(account.keys().getPrivate());
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private static String payload(String iss, String aud, String jti, Instant iat, Duration lifetime) {
        return "{\"iss\":\"" + iss + "\",\"aud\":\"" + aud + "\",\"jti\":\"" + jti + "\",\"iat\":"
                + iat.getEpochSecond() + ",\"exp\":" + iat.plus(lifetime).getEpochSecond()
                + ",\"lxm\":\"com.atproto.server.getServiceAuth\"}";
    }

    private static String header(String alg) {
        return "{\"typ\":\"JWT\",\"alg\":\"" + alg + "\",\"kid\":\"#atproto\"}";
    }

    private static String b64url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ServiceAuthVerifier verifier(String didDocumentJson) {
        HttpFetcher http = request -> request.uri().toString().endsWith(PLAYER_DID)
                ? new HttpFetcher.Response(200, didDocumentJson, Map.of())
                : new HttpFetcher.Response(404, "", Map.of());
        DidResolver dids =
                new DidResolver(http, URI.create("https://plc.example"), Duration.ofMinutes(15), 4096, () -> NOW);
        return new ServiceAuthVerifier(dids, SERVER_DID, new ServiceAuthReplayGuard(() -> NOW), () -> NOW);
    }

    // ── the happy paths ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("a well-formed token")
    class Accepted {

        @Test
        @DisplayName("secp256k1 — the curve most did:plc accounts actually use")
        void secp256k1() throws Exception {
            // The whole reason BouncyCastle is a server dependency. If this fails, the server cannot
            // authenticate the majority of real AT Protocol accounts.
            Account account = account("secp256k1");
            String token = mint(
                    account, header("ES256K"), payload(PLAYER_DID, SERVER_DID, "jti-1", NOW, Duration.ofSeconds(60)));

            assertThat(verifier(didDocument(account.multibaseKey()))
                            .verify(token)
                            .value())
                    .isEqualTo(PLAYER_DID);
        }

        @Test
        @DisplayName("P-256")
        void p256() throws Exception {
            Account account = account("secp256r1");
            String token = mint(
                    account, header("ES256"), payload(PLAYER_DID, SERVER_DID, "jti-2", NOW, Duration.ofSeconds(60)));

            assertThat(verifier(didDocument(account.multibaseKey()))
                            .verify(token)
                            .value())
                    .isEqualTo(PLAYER_DID);
        }

        @Test
        @DisplayName("an aud carrying a service fragment still matches this server")
        void audienceFragment() throws Exception {
            Account account = account("secp256k1");
            String token = mint(
                    account,
                    header("ES256K"),
                    payload(PLAYER_DID, SERVER_DID + "#atproto_labeler", "jti-3", NOW, Duration.ofSeconds(60)));

            assertThat(verifier(didDocument(account.multibaseKey())).verify(token))
                    .isNotNull();
        }
    }

    // ── the refusals that matter ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refusals")
    class Refused {

        @Test
        @DisplayName("⚠ a token minted for ANOTHER server is refused — the replay-across-federation hole")
        void wrongAudience() throws Exception {
            // Without this, a player signs in to one honest home server and its operator relays their
            // token to every other server in the federation as them. The single most important claim
            // check in the verifier.
            Account account = account("secp256k1");
            String token = mint(
                    account,
                    header("ES256K"),
                    payload(PLAYER_DID, "did:web:someone-else.example", "jti-4", NOW, Duration.ofSeconds(60)));

            assertThatThrownBy(
                            () -> verifier(didDocument(account.multibaseKey())).verify(token))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("not for this server");
        }

        @Test
        @DisplayName("⚠ a token signed by the WRONG KEY is refused")
        void wrongSigner() throws Exception {
            // The DID document publishes one key; the token is signed with another. This is the
            // check the whole class exists for.
            Account real = account("secp256k1");
            Account impostor = account("secp256k1");
            String token = mint(
                    impostor, header("ES256K"), payload(PLAYER_DID, SERVER_DID, "jti-5", NOW, Duration.ofSeconds(60)));

            assertThatThrownBy(() -> verifier(didDocument(real.multibaseKey())).verify(token))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("⚠ a TAMPERED payload is refused even though the signature is otherwise real")
        void tamperedPayload() throws Exception {
            // ⚠ Only the jti is altered. A first version of this test swapped the ISSUER, which
            // made the fake directory answer 404 and the test passed with the signature check
            // disabled — proving nothing. Everything else here is identical, so the signature is the
            // only thing that can reject it.
            Account account = account("secp256k1");
            String token = mint(
                    account, header("ES256K"), payload(PLAYER_DID, SERVER_DID, "jti-6", NOW, Duration.ofSeconds(60)));
            String[] parts = token.split("\\.");
            String swapped = parts[0] + "."
                    + b64url(payload(PLAYER_DID, SERVER_DID, "jti-6-tampered", NOW, Duration.ofSeconds(60)))
                    + "." + parts[2];

            assertThatThrownBy(
                            () -> verifier(didDocument(account.multibaseKey())).verify(swapped))
                    .isInstanceOf(SignInException.class);
        }

        @Test
        @DisplayName("⚠ alg:none is inexpressible — the algorithm comes from the KEY, not the header")
        void algorithmConfusion() throws Exception {
            // The classic JWT flaw. This verifier never reads `alg`, so a token claiming `none` (or
            // HS256, or anything else) is simply checked with the key's own algorithm and fails.
            // ⚠ The signature segment is PRESENT but junk. An earlier version ended the token with a
            // bare "." — which split() collapses to two segments, so it was rejected as malformed
            // before the signature was ever considered, and passed with the signature check disabled.
            Account account = account("secp256k1");
            String unsigned = b64url("{\"typ\":\"JWT\",\"alg\":\"none\",\"kid\":\"#atproto\"}") + "."
                    + b64url(payload(PLAYER_DID, SERVER_DID, "jti-7", NOW, Duration.ofSeconds(60))) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]);

            assertThatThrownBy(
                            () -> verifier(didDocument(account.multibaseKey())).verify(unsigned))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("⚠ a REPLAYED token is refused the second time")
        void replay() throws Exception {
            Account account = account("secp256k1");
            String token = mint(
                    account, header("ES256K"), payload(PLAYER_DID, SERVER_DID, "jti-8", NOW, Duration.ofSeconds(60)));
            ServiceAuthVerifier verifier = verifier(didDocument(account.multibaseKey()));

            assertThat(verifier.verify(token)).isNotNull();
            assertThatThrownBy(() -> verifier.verify(token))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("an EXPIRED token is refused")
        void expired() throws Exception {
            Account account = account("secp256k1");
            String token = mint(
                    account,
                    header("ES256K"),
                    payload(PLAYER_DID, SERVER_DID, "jti-9", NOW.minus(Duration.ofHours(1)), Duration.ofSeconds(60)));

            assertThatThrownBy(
                            () -> verifier(didDocument(account.multibaseKey())).verify(token))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("a token claiming a YEAR-long lifetime is refused — a proof, not a bearer credential")
        void absurdLifetime() throws Exception {
            Account account = account("secp256k1");
            String token = mint(
                    account, header("ES256K"), payload(PLAYER_DID, SERVER_DID, "jti-10", NOW, Duration.ofDays(365)));

            assertThatThrownBy(
                            () -> verifier(didDocument(account.multibaseKey())).verify(token))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("lifetime");
        }

        @Test
        @DisplayName("⚠ a token naming a key OTHER than #atproto is refused")
        void wrongKid() throws Exception {
            // A document may publish several methods; letting the token choose one lets its holder
            // pick whichever they have a signature for.
            Account account = account("secp256k1");
            String token = mint(
                    account,
                    "{\"typ\":\"JWT\",\"alg\":\"ES256K\",\"kid\":\"#some-other-key\"}",
                    payload(PLAYER_DID, SERVER_DID, "jti-11", NOW, Duration.ofSeconds(60)));

            assertThatThrownBy(
                            () -> verifier(didDocument(account.multibaseKey())).verify(token))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("#atproto");
        }

        @Test
        @DisplayName("a token with no jti cannot be replay-checked, so it is refused")
        void noJti() throws Exception {
            Account account = account("secp256k1");
            String token = mint(
                    account,
                    header("ES256K"),
                    "{\"iss\":\"" + PLAYER_DID + "\",\"aud\":\"" + SERVER_DID + "\",\"iat\":" + NOW.getEpochSecond()
                            + ",\"exp\":" + NOW.plusSeconds(60).getEpochSecond() + "}");

            assertThatThrownBy(
                            () -> verifier(didDocument(account.multibaseKey())).verify(token))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("jti");
        }

        @Test
        @DisplayName("garbage is refused without an unhandled exception")
        void garbage() {
            String document = didDocument("zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF");
            for (String bad : new String[] {"", "not.a.jwt", "a.b", "....", "x"}) {
                assertThatThrownBy(() -> verifier(document).verify(bad))
                        .as("input '%s'", bad)
                        .isInstanceOf(SignInException.class);
            }
        }
    }

    // ── encoding helpers, mirroring MultibaseKey's decoder ────────────────────────────────────

    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private static byte[] compress(ECPublicKey key) {
        byte[] x = key.getW().getAffineX().toByteArray();
        byte[] fixed = new byte[32];
        int from = Math.max(0, x.length - 32);
        System.arraycopy(x, from, fixed, 32 - (x.length - from), x.length - from);
        byte[] out = new byte[33];
        out[0] = (byte) (key.getW().getAffineY().testBit(0) ? 0x03 : 0x02);
        System.arraycopy(fixed, 0, out, 1, 32);
        return out;
    }

    private static String multibase(int codec, byte[] key) {
        byte[] varint = codec < 0x80
                ? new byte[] {(byte) codec}
                : new byte[] {(byte) ((codec & 0x7F) | 0x80), (byte) (codec >> 7)};
        byte[] all = new byte[varint.length + key.length];
        System.arraycopy(varint, 0, all, 0, varint.length);
        System.arraycopy(key, 0, all, varint.length, key.length);

        StringBuilder out = new StringBuilder();
        BigInteger value = new BigInteger(1, all);
        while (value.signum() > 0) {
            BigInteger[] divmod = value.divideAndRemainder(BigInteger.valueOf(58));
            out.append(BASE58.charAt(divmod[1].intValue()));
            value = divmod[0];
        }
        for (byte b : all) {
            if (b != 0) {
                break;
            }
            out.append('1');
        }
        return "z" + out.reverse();
    }
}
