package io.github.stoicswe.eyeandsickle.protocol.channel;

import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.hpke.HPKE;
import org.bouncycastle.crypto.hpke.HPKEContext;
import org.bouncycastle.crypto.hpke.HPKEContextWithEncapsulation;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;

/**
 * An authenticated, encrypted, replay-proof session — on RFC 9180 HPKE, via BouncyCastle.
 *
 * <h2>Why this exists, and what it supersedes</h2>
 *
 * {@link SecureHandshake} / {@link SecureChannel} are a <strong>hand-rolled Noise-IK-shaped
 * protocol</strong> — reviewed patterns, unreviewed code — and {@code CLAUDE.md} carries <b>T-1</b>:
 * do not let them guard anything live until a cryptographer has read them. This class replaces that
 * bet with a standard: <a href="https://www.rfc-editor.org/rfc/rfc9180.html">RFC 9180</a>, implemented
 * by a library that has been read by many more people than this repository ever will be.
 *
 * <p>It delivers exactly the three properties {@code docs/architecture/07-transport-security.md} §1
 * asks for, and each was verified against real BouncyCastle before this class was written
 * ({@code HpkeChannelTest}):
 *
 * <ul>
 *   <li><strong>Authenticated</strong> — HPKE {@code mode_auth} binds the sender's <em>static</em>
 *       key into the key schedule. ⚠ A wrong sender key does not produce a "wrong identity" flag to
 *       check; it produces a context whose frames <em>do not open at all</em>. Authentication that
 *       cannot be forgotten because there is no branch to forget.
 *   <li><strong>Encrypted</strong> — AES-256-GCM under a key neither side chose alone.
 *   <li><strong>Replay-proof</strong> — the context carries a sequence number that advances on every
 *       {@code seal}/{@code open}. ⚠ Re-presenting a frame fails, and so does presenting them out of
 *       order; this is a property of the construction, not a nonce cache somebody has to maintain.
 * </ul>
 *
 * <h2>⚠ HPKE contexts are ONE-DIRECTIONAL — the reverse channel is derived, not assumed</h2>
 *
 * An HPKE context encrypts from sender to receiver and nothing else. A session needs both ways, and
 * the wrong fix is to reuse one context for both — that would repeat sequence numbers under one key,
 * which is the catastrophic failure mode for AEAD.
 *
 * <p>RFC 9180 §9.8's answer, and the one used here: derive the reverse direction from the context's
 * <strong>exporter secret</strong>, with a different label. Both sides compute the same key from their
 * own context without another round trip — verified: the two exports agree.
 *
 * <h2>What this class deliberately does not do</h2>
 *
 * No transport, no framing on the wire, no key <em>distribution</em>. It turns two static keypairs
 * into a session; where those keys come from — a DID document, a pinned certificate, a LAN join — is
 * the caller's problem and is where the real trust decisions live ({@code 07} §4.1,
 * {@code 12-lan-mode.md} LAN-6).
 *
 * <p>⚠ And it does not replace TLS. {@code 07} §3 makes TLS 1.3 mandatory and puts this layer
 * <em>inside</em> it; this defends the span between a TLS terminator and the application, which is
 * precisely where a self-hoster's reverse proxy leaves traffic plaintext and unattributable.
 */
public final class HpkeChannel {

    /**
     * The suite.
     *
     * <p>X25519 + HKDF-SHA256 + AES-256-GCM: RFC 9180's widely-deployed recommendation and the one
     * Tink defaults to. ⚠ Pinned as constants rather than negotiated — a suite the peer can choose is
     * a suite the peer can choose <em>badly</em>, and there is no legacy here to be compatible with.
     */
    private static final byte MODE = HPKE.mode_auth;

    private static final short KEM = HPKE.kem_X25519_SHA256;
    private static final short KDF = HPKE.kdf_HKDF_SHA256;
    private static final short AEAD = HPKE.aead_AES_GCM256;

    /**
     * Domain separation.
     *
     * <p>⚠ Carries a version. Two peers running different versions must fail to establish a session
     * rather than establish a subtly different one, and folding the version into {@code info} makes
     * that automatic instead of a check somebody writes.
     */
    private static final byte[] INFO = "eyeandsickle/channel/hpke/v1".getBytes(StandardCharsets.UTF_8);

    /** The label the reverse direction is derived under. Must differ from anything else exported. */
    private static final byte[] REVERSE_LABEL = "eyeandsickle/channel/reverse/v1".getBytes(StandardCharsets.UTF_8);

    private static final int REVERSE_KEY_BYTES = 32;

    private final HPKEContext context;
    private final byte[] encapsulation;
    private final byte[] reverseKey;

    private HpkeChannel(HPKEContext context, byte[] encapsulation, byte[] reverseKey) {
        this.context = context;
        this.encapsulation = encapsulation;
        this.reverseKey = reverseKey;
    }

    private static HPKE hpke() {
        return new HPKE(MODE, KEM, KDF, AEAD);
    }

    /** Generates a static keypair for this suite. */
    public static AsymmetricCipherKeyPair generateStaticKeyPair() {
        return hpke().generatePrivateKey();
    }

    /**
     * Opens a channel as the initiator.
     *
     * @param peerStaticPublicKey the responder's static public key — from its DID document, or a
     *     pinned LAN key. ⚠ Trusting the wrong key here is the one failure this class cannot detect:
     *     the session will work perfectly, with the wrong peer.
     * @param ownStaticKeyPair this side's static keypair, which is what authenticates it
     * @return the channel; its {@link #encapsulation()} must reach the responder
     */
    public static HpkeChannel initiate(
            AsymmetricKeyParameter peerStaticPublicKey, AsymmetricCipherKeyPair ownStaticKeyPair) {
        Objects.requireNonNull(peerStaticPublicKey, "peerStaticPublicKey");
        Objects.requireNonNull(ownStaticKeyPair, "ownStaticKeyPair");
        HPKEContextWithEncapsulation context = hpke().setupAuthS(peerStaticPublicKey, INFO, ownStaticKeyPair);
        return new HpkeChannel(context, context.getEncapsulation(), context.export(REVERSE_LABEL, REVERSE_KEY_BYTES));
    }

    /**
     * Opens a channel as the responder.
     *
     * @param encapsulation what the initiator sent
     * @param ownStaticKeyPair this side's static keypair
     * @param peerStaticPublicKey the initiator's static public key — <strong>this is the
     *     authentication</strong>. ⚠ Pass the key of the identity you believe you are talking to; if
     *     it is not that peer, frames fail to open rather than opening as somebody else.
     * @return the channel
     */
    public static HpkeChannel respond(
            byte[] encapsulation,
            AsymmetricCipherKeyPair ownStaticKeyPair,
            AsymmetricKeyParameter peerStaticPublicKey) {
        Objects.requireNonNull(encapsulation, "encapsulation");
        Objects.requireNonNull(ownStaticKeyPair, "ownStaticKeyPair");
        Objects.requireNonNull(peerStaticPublicKey, "peerStaticPublicKey");
        HPKEContext context = hpke().setupAuthR(encapsulation, ownStaticKeyPair, INFO, peerStaticPublicKey);
        return new HpkeChannel(context, encapsulation, context.export(REVERSE_LABEL, REVERSE_KEY_BYTES));
    }

    /** What the initiator must send to the responder before anything else. */
    public byte[] encapsulation() {
        return encapsulation.clone();
    }

    /**
     * Seals one frame.
     *
     * @param plaintext the frame
     * @param associatedData authenticated but not encrypted; null for none
     * @return the sealed frame
     */
    public byte[] seal(byte[] plaintext, byte[] associatedData) {
        try {
            return context.seal(associatedData == null ? new byte[0] : associatedData, plaintext);
        } catch (InvalidCipherTextException failed) {
            throw new SecureChannelException("could not seal a frame", failed);
        }
    }

    /**
     * Opens one frame.
     *
     * <p>⚠ Failure is <strong>not</strong> distinguishable between "tampered", "replayed", "out of
     * order" and "wrong peer", and must not be made so. Each of those is an attacker learning
     * something from the error, and none of them has a different remedy: the session is finished.
     *
     * @param frame the sealed frame
     * @param associatedData the same value used to seal it
     * @return the plaintext
     * @throws SecureChannelException if the frame does not open, for any reason
     */
    public byte[] open(byte[] frame, byte[] associatedData) {
        try {
            return context.open(associatedData == null ? new byte[0] : associatedData, frame);
        } catch (InvalidCipherTextException failed) {
            throw new SecureChannelException("frame rejected; the session is no longer trustworthy", failed);
        }
    }

    /**
     * The key for the opposite direction, derived per RFC 9180 §9.8.
     *
     * <p>Both peers compute the same value from their own context, with no extra round trip.
     *
     * @return a 32-byte key
     */
    public byte[] reverseDirectionKey() {
        return reverseKey.clone();
    }
}
