package io.github.stoicswe.eyeandsickle.protocol.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decodes a DID document's {@code publicKeyMultibase} into a usable {@link PublicKey}.
 *
 * <h2>The encoding, layer by layer</h2>
 *
 * <pre>
 *   "zQ3shXjHei…"   multibase: 'z' means base58btc
 *        ↓ base58 decode
 *   e7 01 02 5a…    multicodec varint prefix, then the key
 *        ↓ read varint
 *   0xe7            secp256k1-pub  ─┐
 *   0x1200          p256-pub        ├─ then a COMPRESSED point (33 bytes)
 *   0xed            ed25519-pub     ─┘  or a raw 32-byte Ed25519 key
 * </pre>
 *
 * <h2>⚠ THE TRAP: secp256k1 support depends on the JVM, and TWO API LAYERS LIE ABOUT IT</h2>
 *
 * Measured 2026-08-02 on two JDK <strong>26</strong> builds on the same machine:
 *
 * <table border="1">
 *   <caption>secp256k1 on two runtimes</caption>
 *   <tr><th>Step</th><th>OpenJDK 26 (SunEC)</th><th>Semeru 26 (OpenJ9)</th></tr>
 *   <tr><td>{@code AlgorithmParameters.init(secp256k1)}</td><td>✅ OK</td><td>✅ OK</td></tr>
 *   <tr><td>{@code KeyFactory.generatePublic}</td><td>✅ OK</td><td>✅ OK</td></tr>
 *   <tr><td>{@code Signature.initVerify}</td><td>✅ <strong>OK — and this is the trap</strong></td><td>✅ OK</td></tr>
 *   <tr><td>{@code Signature.verify}</td><td>❌ <em>Curve not supported</em></td><td>✅ OK</td></tr>
 *   <tr><td>{@code KeyPairGenerator}</td><td>❌ <em>Curve not supported</em></td><td>✅ OK</td></tr>
 * </table>
 *
 * <p>So on stock OpenJDK <strong>three</strong> API layers report success: the curve name resolves, a
 * {@code PublicKey} is constructed, it reports {@code getAlgorithm() == "EC"}, and a {@code Signature}
 * initialises with it. The refusal arrives only at {@code verify()} — the last call, on the request
 * path, when a real federated signature is being checked.
 *
 * <p>⚠ <strong>Every cheap availability check therefore returns true on exactly the JVM where the
 * curve does not work</strong>, {@code initVerify} included — which is the natural thing to probe and
 * was this code's first attempt. {@link #secp256k1Available()} calls {@code verify()} with a
 * well-formed but wrong DER signature: a supported curve answers {@code false}, an unsupported one
 * throws.
 *
 * <p>⚠ This is <em>runtime</em>-dependent, not build-dependent: the same jar works on one player's
 * JVM and not another's. That is why {@link #decode} <strong>refuses an unusable curve up front</strong>
 * with a message naming the real cause, rather than handing back a key that fails three layers away.
 *
 * <h2>⚠ Second trap: build EC keys from the NAMED spec, never a hand-built one</h2>
 *
 * A hand-constructed {@link ECParameterSpec} carrying numerically correct domain parameters is
 * accepted by {@code KeyFactory} and then rejected by {@code Signature.initVerify} — even for
 * <em>P-256</em>, which is otherwise fully supported. SunEC matches curves against its internal table
 * by identity rather than by comparing the numbers, so a numerically-identical spec is a different
 * curve as far as it is concerned. Deriving the spec from {@link ECGenParameterSpec} is what makes
 * P-256 work end to end.
 *
 * <h2>What this means for the design</h2>
 *
 * {@code docs/architecture/10-oauth-and-did-resolution.md} §5.1's original claim — that ES256K needs
 * BouncyCastle — is <strong>correct for stock OpenJDK</strong>. Most {@code did:plc} accounts sign
 * with secp256k1, so a server on stock OpenJDK cannot verify most service-auth JWTs. That is a real
 * blocker for §1's stage 6 and is recorded as such; it does not affect P-256 or Ed25519, so
 * provenance (Ed25519) and DPoP (P-256) are unaffected.
 */
public final class MultibaseKey {

    private MultibaseKey() {}

    /** base58btc — Bitcoin's alphabet. Note the absent {@code 0}, {@code O}, {@code I} and {@code l}. */
    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    /** multicodec {@code ed25519-pub}. */
    private static final int ED25519 = 0xED;
    /** multicodec {@code secp256k1-pub}. */
    private static final int SECP256K1 = 0xE7;
    /** multicodec {@code p256-pub}. */
    private static final int P256 = 0x1200;

    /** Named-curve specs are expensive to derive and immutable, so derive each once. */
    private static final Map<String, ECParameterSpec> CURVES = new ConcurrentHashMap<>();

    /**
     * How to verify with each curve on THIS JVM — probed once, not assumed.
     *
     * <p>Absent means "not usable here". See {@link #recipeFor}.
     */
    private static final Map<String, Recipe> RECIPES = new ConcurrentHashMap<>();

    /** Sentinel for "probed, and no provider can do it" — {@link ConcurrentHashMap} forbids nulls. */
    private static final Recipe NONE = new Recipe("", "");

    /**
     * A provider and the algorithm name it calls raw-{@code R||S} ECDSA by.
     *
     * <p>⚠ <strong>Both halves vary, and that is the discovery that shapes this class.</strong> JOSE
     * requires raw {@code R||S}. SunEC spells that {@code SHA256withECDSAinP1363Format} and cannot do
     * secp256k1; BouncyCastle spells it {@code SHA256WITHPLAIN-ECDSA} and can. Neither name works on
     * the other provider — measured 2026-08-02, BC throws {@code NoSuchAlgorithmException} for the
     * P1363 name. So a verifier must carry the pair, never one or the other.
     *
     * @param provider the JCA provider name, or "" for the platform default
     * @param algorithm that provider's name for raw R||S ECDSA
     */
    private record Recipe(String provider, String algorithm) {}

    /**
     * A decoded key, with the JWS algorithm its curve implies.
     *
     * <p>The algorithm rides along because the two must agree and are decided in the same place: a
     * verifier that reads {@code alg} from the <em>token</em> and trusts it lets an attacker nominate
     * the algorithm their own signature will be checked with. The key's curve decides, not the token.
     *
     * @param key the public key
     * @param jwsAlgorithm {@code EdDSA}, {@code ES256} or {@code ES256K}
     */
    public record AtprotoKey(PublicKey key, String jwsAlgorithm) {}

    /**
     * Decodes a {@code publicKeyMultibase} value.
     *
     * @param multibase e.g. {@code zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF}
     * @return the key and its algorithm
     * @throws IdentityResolutionException if the encoding, the codec or the point is invalid
     */
    public static AtprotoKey decode(String multibase) {
        if (multibase == null || multibase.isBlank()) {
            throw new IdentityResolutionException(Kind.INVALID, "no publicKeyMultibase");
        }
        if (multibase.charAt(0) != 'z') {
            // Multibase has many prefixes; atproto uses base58btc and only base58btc. Guessing at
            // another would mean accepting a key encoded in a form the spec does not sanction.
            throw new IdentityResolutionException(
                    Kind.INVALID, "expected base58btc ('z') multibase, got '" + multibase.charAt(0) + "'");
        }
        byte[] bytes = base58Decode(multibase.substring(1));

        int[] cursor = {0};
        int codec = readVarint(bytes, cursor);
        byte[] key = Arrays.copyOfRange(bytes, cursor[0], bytes.length);

        return switch (codec) {
            case ED25519 -> new AtprotoKey(ed25519(key), "EdDSA");
            case P256 -> new AtprotoKey(ec(key, "secp256r1"), "ES256");
            case SECP256K1 -> new AtprotoKey(ec(key, "secp256k1"), "ES256K");
            default ->
                throw new IdentityResolutionException(
                        Kind.INVALID,
                        "unsupported multicodec 0x" + Integer.toHexString(codec) + " in publicKeyMultibase");
        };
    }

    /**
     * Whether this JVM can actually verify secp256k1 signatures.
     *
     * <p>⚠ Checks {@code initVerify}, not merely that the curve name resolves. The name resolves even
     * where verification does not, so the cheap check reports success in exactly the case that
     * matters.
     *
     * @return true if a secp256k1 key can be built and used
     */
    public static boolean secp256k1Available() {
        return recipeFor("secp256k1") != null;
    }

    /**
     * A {@link Signature} that verifies JOSE-format (raw {@code R||S}) signatures with this key.
     *
     * <p>⚠ The <strong>key's</strong> curve decides the algorithm, never a token's {@code alg} header.
     * A verifier that reads {@code alg} from the thing being verified lets an attacker nominate how
     * their own signature is checked.
     *
     * @param key a decoded key
     * @return an uninitialised {@code Signature} from a provider that can actually use the curve
     */
    public static Signature joseVerifier(AtprotoKey key) {
        try {
            if ("EdDSA".equals(key.jwsAlgorithm())) {
                // Ed25519 signatures are already raw 64 bytes; there is no DER variant to choose.
                return Signature.getInstance("Ed25519");
            }
            String curve = "ES256".equals(key.jwsAlgorithm()) ? "secp256r1" : "secp256k1";
            Recipe recipe = recipeFor(curve);
            if (recipe == null) {
                throw new IdentityResolutionException(
                        Kind.REFUSED_BY_POLICY, "no JCA provider on this JVM can verify " + curve);
            }
            return recipe.provider().isEmpty()
                    ? Signature.getInstance(recipe.algorithm())
                    : Signature.getInstance(recipe.algorithm(), recipe.provider());
        } catch (GeneralSecurityException impossible) {
            // The recipe was probed by USE, so this cannot happen unless a provider was removed
            // after the probe ran.
            throw new IdentityResolutionException(Kind.REFUSED_BY_POLICY, "verifier unavailable", impossible);
        }
    }

    /**
     * Finds a provider that can actually verify with a curve, and what that provider calls the
     * raw-{@code R||S} algorithm. Probed once per curve, by <em>use</em>.
     *
     * <h2>⚠ Registering BouncyCastle is NOT enough on its own</h2>
     *
     * Measured 2026-08-02 with BC registered on OpenJDK 26: the <strong>default</strong> provider
     * path still fails for secp256k1, because {@code Signature.getInstance("SHA256withECDSA")} is
     * answered by SunEC — which accepts the key at {@code initVerify} and only refuses at
     * {@code verify()}. The JCA never falls through to BC, because nothing reported a problem in time
     * for it to. <strong>The provider has to be named explicitly</strong>, which is why this returns a
     * {@link Recipe} rather than a boolean.
     *
     * <p>⚠ And the probe must run {@code verify()}, not {@code initVerify()} — see above; that
     * distinction is the whole reason the earlier boolean version reported the curve as usable on the
     * JVM where it is not.
     *
     * @return a usable recipe, or null if no registered provider can verify with this curve
     */
    private static Recipe recipeFor(String curveName) {
        Recipe cached = RECIPES.computeIfAbsent(curveName, MultibaseKey::probeCurve);
        return cached == NONE ? null : cached;
    }

    private static Recipe probeCurve(String curveName) {
        // Ordered so the platform default is preferred when it works: fewer moving parts, and it is
        // the only one available in a build that has not added a provider.
        for (String algorithm : new String[] {"SHA256withECDSAinP1363Format", "SHA256WITHPLAIN-ECDSA"}) {
            if (canVerify(curveName, "", algorithm)) {
                return new Recipe("", algorithm);
            }
        }
        for (java.security.Provider provider : java.security.Security.getProviders()) {
            for (String algorithm : new String[] {"SHA256WITHPLAIN-ECDSA", "SHA256withECDSAinP1363Format"}) {
                if (canVerify(curveName, provider.getName(), algorithm)) {
                    return new Recipe(provider.getName(), algorithm);
                }
            }
        }
        return NONE;
    }

    /** Signs and verifies a throwaway message. Nothing short of that tells the truth here. */
    private static boolean canVerify(String curveName, String providerName, String algorithm) {
        try {
            java.security.KeyPairGenerator generator = providerName.isEmpty()
                    ? java.security.KeyPairGenerator.getInstance("EC")
                    : java.security.KeyPairGenerator.getInstance("EC", providerName);
            generator.initialize(new ECGenParameterSpec(curveName));
            java.security.KeyPair pair = generator.generateKeyPair();

            Signature signer = providerName.isEmpty()
                    ? Signature.getInstance(algorithm)
                    : Signature.getInstance(algorithm, providerName);
            signer.initSign(pair.getPrivate());
            signer.update(new byte[] {1, 2, 3});
            byte[] signature = signer.sign();

            Signature verifier = providerName.isEmpty()
                    ? Signature.getInstance(algorithm)
                    : Signature.getInstance(algorithm, providerName);
            verifier.initVerify(pair.getPublic());
            verifier.update(new byte[] {1, 2, 3});
            // A real round trip. `false` here would mean the provider is broken rather than absent,
            // and either way it is not one to verify players' tokens with.
            return verifier.verify(signature);
        } catch (GeneralSecurityException | RuntimeException unusable) {
            return false;
        }
    }

    private static PublicKey ed25519(byte[] raw) {
        if (raw.length != 32) {
            throw new IdentityResolutionException(Kind.INVALID, "an Ed25519 key is 32 bytes, got " + raw.length);
        }
        // RFC 8032 §5.1.2: little-endian y, with the top bit of the last byte carrying x's parity.
        byte[] le = raw.clone();
        boolean xOdd = (le[31] & 0x80) != 0;
        le[31] &= 0x7F;
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) {
            be[i] = le[31 - i];
        }
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new EdECPublicKeySpec(
                            NamedParameterSpec.ED25519, new EdECPoint(xOdd, new BigInteger(1, be))));
        } catch (GeneralSecurityException refused) {
            throw new IdentityResolutionException(Kind.INVALID, "not a valid Ed25519 key", refused);
        }
    }

    private static PublicKey ec(byte[] compressed, String curveName) {
        if (compressed.length != 33) {
            throw new IdentityResolutionException(
                    Kind.INVALID,
                    "expected a 33-byte compressed point on " + curveName + ", got " + compressed.length + " bytes");
        }
        int prefix = compressed[0] & 0xFF;
        if (prefix != 0x02 && prefix != 0x03) {
            throw new IdentityResolutionException(
                    Kind.INVALID, "compressed point prefix must be 0x02 or 0x03, got 0x" + Integer.toHexString(prefix));
        }
        if (recipeFor(curveName) == null) {
            // Refused HERE rather than three layers away at the point of verification. On stock
            // OpenJDK a secp256k1 key constructs perfectly and then cannot verify anything; a caller
            // holding that key has no way to tell it apart from a working one, and the eventual
            // SignatureException names neither the curve nor the cause.
            throw new IdentityResolutionException(
                    Kind.REFUSED_BY_POLICY,
                    "this JVM's crypto provider cannot verify " + curveName + " signatures, so a "
                            + curveName + " key would be unusable. Note that the curve name resolves, a key can be"
                            + " constructed, and Signature.initVerify SUCCEEDS on such a JVM — only verify() fails,"
                            + " which is why this is checked here and not left to the point of use. Run on a JVM"
                            + " whose provider supports the curve, or add one that does.");
        }
        ECParameterSpec spec = namedCurve(curveName);
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(compressed, 1, 33));
        ECPoint point = new ECPoint(x, decompressY(x, prefix == 0x03, spec.getCurve(), curveName));
        try {
            // ⚠ `spec` here is the NAMED spec, never a hand-built one. See the class comment: a
            // hand-built spec builds a key that fails at initVerify, three layers away from here.
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
        } catch (GeneralSecurityException refused) {
            throw new IdentityResolutionException(Kind.INVALID, "not a valid " + curveName + " key", refused);
        }
    }

    /**
     * Recovers {@code y} from {@code x} and the sign bit.
     *
     * <p>Both curves atproto uses have {@code p ≡ 3 (mod 4)}, so the modular square root is the
     * closed form {@code v^((p+1)/4)} and needs no Tonelli–Shanks.
     */
    private static BigInteger decompressY(BigInteger x, boolean odd, EllipticCurve curve, String curveName) {
        ECField field = curve.getField();
        if (!(field instanceof ECFieldFp fp)) {
            throw new IdentityResolutionException(Kind.INVALID, curveName + " is not over a prime field");
        }
        BigInteger p = fp.getP();
        BigInteger rhs = x.modPow(BigInteger.TWO, p)
                .add(curve.getA())
                .multiply(x)
                .add(curve.getB())
                .mod(p);
        BigInteger y = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p);
        // ⚠ modPow always returns SOMETHING. If rhs is not a quadratic residue the result is simply
        // not a square root, and without this check an arbitrary 32-byte string becomes a "key" that
        // is not on the curve — which is an invalid-curve attack handed over for free.
        if (!y.modPow(BigInteger.TWO, p).equals(rhs)) {
            throw new IdentityResolutionException(Kind.INVALID, "point is not on curve " + curveName);
        }
        if (y.testBit(0) != odd) {
            y = p.subtract(y);
        }
        return y;
    }

    private static ECParameterSpec namedCurve(String name) {
        return CURVES.computeIfAbsent(name, curveName -> {
            try {
                AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
                parameters.init(new ECGenParameterSpec(curveName));
                return parameters.getParameterSpec(ECParameterSpec.class);
            } catch (GeneralSecurityException absent) {
                throw new IdentityResolutionException(
                        Kind.INVALID, "this JVM does not provide the curve " + curveName, absent);
            }
        });
    }

    /** Reads an unsigned LEB128 varint — how multicodec encodes its table index. */
    private static int readVarint(byte[] bytes, int[] cursor) {
        int value = 0;
        int shift = 0;
        while (cursor[0] < bytes.length) {
            int b = bytes[cursor[0]++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IdentityResolutionException(Kind.INVALID, "multicodec varint is too long");
            }
        }
        throw new IdentityResolutionException(Kind.INVALID, "truncated multicodec varint");
    }

    private static byte[] base58Decode(String input) {
        if (input.isEmpty()) {
            throw new IdentityResolutionException(Kind.INVALID, "empty base58 payload");
        }
        BigInteger value = BigInteger.ZERO;
        BigInteger radix = BigInteger.valueOf(58);
        for (int i = 0; i < input.length(); i++) {
            int digit = BASE58.indexOf(input.charAt(i));
            if (digit < 0) {
                throw new IdentityResolutionException(
                        Kind.INVALID, "'" + input.charAt(i) + "' is not a base58btc character");
            }
            value = value.multiply(radix).add(BigInteger.valueOf(digit));
        }
        byte[] magnitude = value.toByteArray();
        // BigInteger prepends a zero byte when the top bit is set; that byte is sign, not data.
        int from = (magnitude.length > 1 && magnitude[0] == 0) ? 1 : 0;

        // Leading '1's are base58's encoding of leading ZERO BYTES, which the integer conversion
        // above cannot represent. Dropping them silently shortens the payload and shifts the
        // multicodec prefix.
        int leadingZeros = 0;
        while (leadingZeros < input.length() && input.charAt(leadingZeros) == '1') {
            leadingZeros++;
        }
        byte[] out = new byte[leadingZeros + magnitude.length - from];
        System.arraycopy(magnitude, from, out, leadingZeros, magnitude.length - from);
        return out;
    }
}
