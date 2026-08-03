package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.DidDocument;
import io.github.stoicswe.eyeandsickle.protocol.identity.DidResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException;
import io.github.stoicswe.eyeandsickle.protocol.identity.MultibaseKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies an AT Protocol <strong>inter-service auth</strong> JWT — stage 6 of
 * {@code docs/architecture/10-oauth-and-did-resolution.md} §7, and the thing that makes Option C's
 * trust model real.
 *
 * <h2>What this is for</h2>
 *
 * Under §1 Option C the desktop client runs the OAuth flow and this server does <strong>not</strong>
 * take its word for who it is. Instead the client asks its own PDS for a short-lived JWT
 * ({@code com.atproto.server.getServiceAuth}) whose {@code aud} is this server's DID, signed by the
 * account's own atproto signing key. This class checks that signature against a DID document it
 * resolves itself.
 *
 * <p>⚠ <strong>That is the whole point: trust comes from a signature checked against a document this
 * server fetched, never from a field in a request body.</strong> No access token, refresh token or
 * DPoP key ever reaches the server.
 *
 * <h2>The checks, and why each one is not optional</h2>
 *
 * <ol>
 *   <li>⚠ <strong>The algorithm comes from the KEY, never from the token's {@code alg} header.</strong>
 *       A verifier that reads {@code alg} from the thing it is verifying lets the attacker choose how
 *       their own signature is checked — the classic JWT algorithm-confusion flaw, whose worst form
 *       ({@code alg: none}) this cannot even express because the algorithm is never taken from there.
 *   <li>⚠ <strong>{@code kid} must be {@code #atproto}.</strong> A DID document may carry several
 *       verification methods; accepting whichever one the token names lets its holder pick a key they
 *       have a signature for. The atproto spec says to accept only {@code #atproto} unless
 *       application-specific keys are expected, and none are here.
 *   <li>⚠ <strong>{@code aud} must be THIS server's DID.</strong> Without it, a token minted for one
 *       home server is replayable at every other server in the federation — the player signs in
 *       somewhere honest and the operator relays their token onward. This is the single most
 *       important claim check in the class.
 *   <li><strong>{@code exp} must be in the future, and the lifetime bounded.</strong> The spec
 *       recommends 60 seconds; a token good for a year is a bearer credential, not a proof.
 *   <li>⚠ <strong>{@code jti} must be unused.</strong> Everything above still permits replay within
 *       the validity window — see {@link ServiceAuthReplayGuard}.
 * </ol>
 *
 * <h2>⚠ secp256k1 needs BouncyCastle, and registering it is not enough</h2>
 *
 * Most {@code did:plc} accounts sign with secp256k1, which SunEC cannot verify. The server declares
 * BouncyCastle for exactly this, and {@code MultibaseKey} names the provider explicitly — because with
 * BC merely registered, {@code Signature.getInstance} is still answered by SunEC, which accepts the
 * key and fails at {@code verify()}. {@link IdentityConfiguration} registers the provider; this class
 * asserts it took effect rather than discovering otherwise on a player's first sign-in.
 */
public class ServiceAuthVerifier {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** The fragment an atproto signing key lives under. Nothing else is accepted. */
    private static final String ATPROTO_KID = "#atproto";

    /**
     * The longest lifetime a token may claim.
     *
     * <p>The spec recommends 60 seconds. This is deliberately looser — clocks on self-hosted machines
     * genuinely disagree — but bounded, because the point of a short-lived proof is that it stops
     * being one.
     */
    static final Duration MAX_LIFETIME = Duration.ofMinutes(5);

    /** How far ahead of us a signer's clock may be before {@code iat} is refused. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(2);

    private final DidResolver dids;
    private final String serverDid;
    private final ServiceAuthReplayGuard replays;
    private final Supplier<Instant> clock;

    public ServiceAuthVerifier(
            DidResolver dids, String serverDid, ServiceAuthReplayGuard replays, Supplier<Instant> clock) {
        this.dids = Objects.requireNonNull(dids, "dids");
        this.serverDid = Objects.requireNonNull(serverDid, "serverDid");
        this.replays = Objects.requireNonNull(replays, "replays");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Verifies a service-auth JWT and returns the DID it proves.
     *
     * @param jwt the compact JWS
     * @return the issuer's DID — proven, not claimed
     * @throws SignInException if anything about the token does not check out
     */
    public Did verify(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            throw new SignInException("no service-auth token presented");
        }
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new SignInException("the service-auth token is not a compact JWS");
        }

        JsonNode header = decodeJson(parts[0], "header");
        JsonNode payload = decodeJson(parts[1], "payload");

        String issuer = text(payload, "iss");
        if (issuer == null) {
            throw new SignInException("the service-auth token names no issuer");
        }
        Did did = parseDid(issuer);

        // ── kid ──────────────────────────────────────────────────────────────────────────────
        String kid = text(header, "kid");
        if (kid != null && !kid.isBlank() && !kid.equals(ATPROTO_KID) && !kid.endsWith(ATPROTO_KID)) {
            // Absent defaults to #atproto per spec; present-and-different is a request to be verified
            // with a key of the holder's choosing.
            throw new SignInException(
                    "the service-auth token names key '" + kid + "'; only " + ATPROTO_KID + " is accepted");
        }

        // ── aud ──────────────────────────────────────────────────────────────────────────────
        String audience = text(payload, "aud");
        if (audience == null) {
            throw new SignInException("the service-auth token names no audience");
        }
        // The audience may carry a service fragment (did:web:host#atproto_labeler); this server is
        // the whole DID, so compare the part before any fragment.
        String audienceDid = audience.contains("#") ? audience.substring(0, audience.indexOf('#')) : audience;
        if (!serverDid.equals(audienceDid)) {
            throw new SignInException(
                    "the service-auth token was minted for '" + audienceDid + "', not for this server");
        }

        // ── timing ───────────────────────────────────────────────────────────────────────────
        Instant now = clock.get();
        long expSeconds = payload.path("exp").asLong(0);
        if (expSeconds <= 0) {
            throw new SignInException("the service-auth token has no expiry");
        }
        Instant expiry = Instant.ofEpochSecond(expSeconds);
        if (!expiry.isAfter(now)) {
            throw new SignInException("the service-auth token expired at " + expiry);
        }
        long iatSeconds = payload.path("iat").asLong(0);
        Instant issuedAt = iatSeconds > 0 ? Instant.ofEpochSecond(iatSeconds) : now;
        if (issuedAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw new SignInException("the service-auth token is issued in the future");
        }
        if (Duration.between(issuedAt, expiry).compareTo(MAX_LIFETIME) > 0) {
            throw new SignInException("the service-auth token claims a lifetime over " + MAX_LIFETIME);
        }

        // ── signature ────────────────────────────────────────────────────────────────────────
        verifySignature(did, parts, jwt);

        // ── replay ───────────────────────────────────────────────────────────────────────────
        // ⚠ LAST, deliberately. Consuming a jti before the signature is checked lets an attacker
        // burn a legitimate token's id with a forged copy, denying the real player their sign-in.
        String jti = text(payload, "jti");
        if (jti == null || jti.isBlank()) {
            throw new SignInException("the service-auth token has no jti and cannot be replay-checked");
        }
        if (!replays.claim(did.value() + ":" + jti, expiry)) {
            throw new SignInException("this service-auth token has already been used");
        }
        return did;
    }

    private void verifySignature(Did did, String[] parts, String jwt) {
        DidDocument document;
        try {
            document = dids.resolve(did.value());
        } catch (IdentityResolutionException unresolvable) {
            throw new SignInUnavailableException(
                    "could not resolve " + did + " to verify its token: " + unresolvable.getMessage());
        }
        DidDocument.VerificationMethod method = document.atprotoSigningKey();
        if (method == null || method.publicKeyMultibase() == null) {
            throw new SignInException("the DID document for " + did + " declares no " + ATPROTO_KID + " key");
        }

        MultibaseKey.AtprotoKey key;
        try {
            key = MultibaseKey.decode(method.publicKeyMultibase());
        } catch (IdentityResolutionException unusable) {
            // Includes "this JVM cannot verify secp256k1", which is an operator problem, not the
            // player's — so it must not be reported as a rejected sign-in.
            throw new SignInUnavailableException("cannot use the signing key of " + did + ": " + unusable.getMessage());
        }

        try {
            // ⚠ The algorithm comes from the KEY. The token's `alg` header is never consulted, which
            // is what makes algorithm confusion inexpressible here rather than merely guarded against.
            Signature signature = MultibaseKey.joseVerifier(key);
            signature.initVerify(key.key());
            signature.update(jwt.substring(0, jwt.lastIndexOf('.')).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new SignInException("the service-auth token's signature does not verify");
            }
        } catch (GeneralSecurityException | IllegalArgumentException broken) {
            throw new SignInException("the service-auth token's signature could not be checked");
        } catch (IdentityResolutionException unavailable) {
            throw new SignInUnavailableException(unavailable.getMessage());
        }
    }

    private static Did parseDid(String value) {
        try {
            return Did.of(value);
        } catch (IllegalArgumentException malformed) {
            throw new SignInException("the service-auth token's issuer is not a well-formed DID");
        }
    }

    private static JsonNode decodeJson(String segment, String what) {
        try {
            return MAPPER.readTree(new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8));
        } catch (JacksonException | IllegalArgumentException malformed) {
            throw new SignInException("the service-auth token's " + what + " is not valid JSON");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }
}
