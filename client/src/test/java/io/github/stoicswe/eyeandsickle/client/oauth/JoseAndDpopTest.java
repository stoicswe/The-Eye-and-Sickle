package io.github.stoicswe.eyeandsickle.client.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The JOSE primitives and the DPoP proof they build. */
class JoseAndDpopTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode part(String jws, int index) {
        String segment = jws.split("\\.")[index];
        return MAPPER.readTree(new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("ES256 signing")
    class Signing {

        @Test
        @DisplayName("produces a RAW 64-byte signature, not DER — the ES256 bug that fails 1 time in 256")
        void rawSignatureFormat() throws Exception {
            // DER is variable-length and self-describing (it starts 0x30). JOSE wants raw R||S at
            // exactly 64 bytes. A DER signature here is accepted by nothing, and a hand-rolled
            // DER→raw conversion that forgets to left-pad a short r works ~255 times out of 256 —
            // which ships, and then rejects one login in every few hundred for no visible reason.
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var pair = generator.generateKeyPair();

            for (int attempt = 0; attempt < 50; attempt++) {
                String jws = Jose.signEs256("{\"alg\":\"ES256\"}", "{\"n\":" + attempt + "}", pair.getPrivate());
                byte[] signature = Base64.getUrlDecoder().decode(jws.split("\\.")[2]);

                // ⚠ The LENGTH is the assertion, and only the length. A first attempt at this test
                // also asserted the first byte is not 0x30 ("DER starts with SEQUENCE") — which is
                // wrong: a raw signature's first byte is random and IS 0x30 about once in 256, so
                // that check failed on its own correct output. A DER signature is 70-72 bytes and
                // variable, so "exactly 64, fifty times running" already excludes it.
                assertThat(signature).as("attempt %d", attempt).hasSize(64);
            }
        }

        @Test
        @DisplayName("the signature actually verifies with the matching public key")
        void verifies() throws Exception {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var pair = generator.generateKeyPair();

            String jws = Jose.signEs256("{\"alg\":\"ES256\"}", "{\"a\":1}", pair.getPrivate());
            String[] parts = jws.split("\\.");

            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(pair.getPublic());
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

            assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        }

        @Test
        @DisplayName("base64url is unpadded — a '=' in a JWS segment is invalid")
        void unpadded() {
            assertThat(Jose.b64("x")).doesNotContain("=");
        }
    }

    @Nested
    @DisplayName("the public JWK and its thumbprint")
    class Jwk {

        private ECPublicKey key() throws Exception {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return (ECPublicKey) generator.generateKeyPair().getPublic();
        }

        @Test
        @DisplayName("members are in RFC 7638's lexicographic order with no whitespace")
        void canonicalOrder() throws Exception {
            // The thumbprint is a hash OF THIS STRING. Any other member order, or a space, gives a
            // different jkt — and jkt is what binds an access token to this key, so a wrong one makes
            // every request fail with an error about the key rather than about the ordering.
            String jwk = Jose.publicJwk(key());

            assertThat(jwk).startsWith("{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"");
            assertThat(jwk).doesNotContain(" ");
            assertThat(jwk.indexOf("\"crv\"")).isLessThan(jwk.indexOf("\"kty\""));
            assertThat(jwk.indexOf("\"kty\"")).isLessThan(jwk.indexOf("\"x\""));
            assertThat(jwk.indexOf("\"x\"")).isLessThan(jwk.indexOf("\"y\""));
        }

        @Test
        @DisplayName("coordinates are ALWAYS 32 bytes — BigInteger.toByteArray is not")
        void coordinatesAreFixedWidth() throws Exception {
            // toByteArray() prepends a sign byte when the top bit is set (33) and drops leading zeros
            // when the value is small (31 or fewer). Both are wrong for a JWK, and both occur often
            // enough to ship and then fail for some users and not others.
            for (int attempt = 0; attempt < 60; attempt++) {
                String jwk = Jose.publicJwk(key());
                JsonNode parsed = MAPPER.readTree(jwk);

                assertThat(Base64.getUrlDecoder().decode(parsed.get("x").stringValue()))
                        .as("x on attempt %d", attempt)
                        .hasSize(32);
                assertThat(Base64.getUrlDecoder().decode(parsed.get("y").stringValue()))
                        .as("y on attempt %d", attempt)
                        .hasSize(32);
            }
        }

        @Test
        @DisplayName("the thumbprint is SHA-256 of the canonical JWK, base64url")
        void thumbprintIsSha256OfTheJwk() throws Exception {
            String jwk = Jose.publicJwk(key());
            byte[] expected = MessageDigest.getInstance("SHA-256").digest(jwk.getBytes(StandardCharsets.UTF_8));

            assertThat(Jose.thumbprint(jwk))
                    .isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(expected));
        }
    }

    @Nested
    @DisplayName("DPoP proofs")
    class Proofs {

        private final DpopKey dpop = DpopKey.generate();
        private final Instant now = Instant.parse("2026-08-02T12:00:00Z");

        @Test
        @DisplayName("carries typ=dpop+jwt, ES256 and the public JWK in the header")
        void header() {
            JsonNode header = part(dpop.proof("POST", URI.create("https://as.example/token"), null, now), 0);

            assertThat(header.get("typ").stringValue()).isEqualTo("dpop+jwt");
            assertThat(header.get("alg").stringValue()).isEqualTo("ES256");
            assertThat(header.get("jwk").get("crv").stringValue()).isEqualTo("P-256");
            assertThat(header.get("jwk").has("d"))
                    .as("the PRIVATE key must never be in the header")
                    .isFalse();
        }

        @Test
        @DisplayName("htu STRIPS the query string")
        void htuHasNoQuery() {
            // The spec defines htu as scheme+host+path. A conforming server rejects a proof whose htu
            // carries parameters and a lenient one accepts it — the worst combination for noticing.
            JsonNode payload =
                    part(dpop.proof("GET", URI.create("https://as.example/token?foo=bar#frag"), null, now), 1);

            assertThat(payload.get("htu").stringValue()).isEqualTo("https://as.example/token");
            assertThat(payload.get("htm").stringValue()).isEqualTo("GET");
        }

        @Test
        @DisplayName("jti is unique on every proof")
        void jtiIsUnique() {
            // Servers track jti to prevent replay, so a repeated one is rejected as a replay attempt.
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                seen.add(part(dpop.proof("POST", URI.create("https://as.example/token"), null, now), 1)
                        .get("jti")
                        .stringValue());
            }
            assertThat(seen).hasSize(200);
        }

        @Test
        @DisplayName("a nonce is included once the server has issued one, and is tracked per ORIGIN")
        void noncePerOrigin() {
            URI authServer = URI.create("https://as.example/token");
            URI pds = URI.create("https://pds.example/xrpc/foo");
            dpop.rememberNonce(authServer, "nonce-from-as");

            assertThat(part(dpop.proof("POST", authServer, null, now), 1)
                            .get("nonce")
                            .stringValue())
                    .isEqualTo("nonce-from-as");
            // The PDS has its own nonce. Sending the auth server's would be rejected in a way that
            // looks like a bad key rather than a bad nonce.
            assertThat(part(dpop.proof("GET", pds, null, now), 1).has("nonce")).isFalse();
        }

        @Test
        @DisplayName("ath binds the proof to a specific access token")
        void athBindsTheToken() {
            JsonNode payload = part(dpop.proof("GET", URI.create("https://pds.example/x"), "the-token", now), 1);

            assertThat(payload.get("ath").stringValue()).isEqualTo(Jose.sha256("the-token"));
        }

        @Test
        @DisplayName("no ath before there is a token")
        void noAthWithoutAToken() {
            assertThat(part(dpop.proof("POST", URI.create("https://as.example/par"), null, now), 1)
                            .has("ath"))
                    .isFalse();
        }

        @Test
        @DisplayName("a restored key produces the same thumbprint — a session survives a restart")
        void restoreRoundTrip() {
            DpopKey restored = DpopKey.restore(dpop.exportPrivate(), dpop.exportPublic());

            // If this drifted, every request after a restart would fail against the jkt the token
            // carries, and the player would be signed out on every launch for no stated reason.
            assertThat(restored.thumbprint()).isEqualTo(dpop.thumbprint());
        }
    }

    @Nested
    @DisplayName("PKCE")
    class PkceTest {

        @Test
        @DisplayName("the challenge is the S256 of the verifier")
        void challengeIsS256() {
            Pkce pkce = Pkce.generate();

            assertThat(pkce.challenge()).isEqualTo(Jose.sha256(pkce.verifier()));
            assertThat(pkce.verifier()).hasSizeGreaterThanOrEqualTo(43).doesNotContain("=");
        }

        @Test
        @DisplayName("every generation is fresh — servers reject a reused challenge for 24 hours")
        void neverReused() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                seen.add(Pkce.generate().verifier());
            }
            assertThat(seen).hasSize(100);
        }
    }
}
