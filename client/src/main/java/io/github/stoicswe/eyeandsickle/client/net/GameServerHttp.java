package io.github.stoicswe.eyeandsickle.client.net;

import java.net.URI;
import java.util.Objects;

/**
 * Address handling for a home or LAN server, and the record of how that link must be secured.
 *
 * <h2>⚠ THE LINK IS ALWAYS AUTHENTICATED AND ENCRYPTED — ON A LAN TOO</h2>
 *
 * An earlier draft of this class was a plain HTTP client, justified by "a LAN server will not have a
 * certificate, so use {@code http}". <strong>That was wrong on the architecture's own terms</strong>
 * and is recorded here so it is not re-derived:
 *
 * <ul>
 *   <li>{@code docs/architecture/07-transport-security.md} §3: <em>"TLS 1.3 is mandatory and is not
 *       being replaced. This layer runs inside it."</em> The DID channel is <strong>additional</strong>
 *       to TLS, never a reason to drop it.
 *   <li>The absence of a certificate is not an argument for plaintext. It is an argument for the
 *       channel — which authenticates <em>DIDs, not hostnames</em>, and therefore needs no CA at all
 *       (§3: <em>"no CA fits this trust model"</em>).
 * </ul>
 *
 * <h2>⚠ The precise claim, because "end-to-end" means something different on this hop</h2>
 *
 * {@code 07} §2 is explicit that strict end-to-end encryption between a client and its home server is
 * <strong>a category error</strong>: the server <em>is</em> the other end and must read game state to
 * be authoritative — that is Invariant <b>I14</b>, and it is why the architecture refuses to put game
 * state in a player's PDS. There is no third party to hide the plaintext from.
 *
 * <p>What the link gets instead, and what must hold on a LAN exactly as online:
 *
 * <ul>
 *   <li><strong>Authenticated</strong> — bound to the server's DID, not to whatever hostname or DHCP
 *       lease it has this week.
 *   <li><strong>Encrypted</strong> — TLS against the network, the channel against everything between
 *       the TLS terminator and the application.
 *   <li><strong>Replay-proof</strong> — per-frame, so captured intent cannot be re-sent.
 * </ul>
 *
 * <p>End-to-end in the strict sense is reserved for <strong>server↔server</strong> federation, where
 * the intermediate infrastructure genuinely is a third party (§2).
 *
 * <h2>⚠ Why the hardened HTTP client is not used here either</h2>
 *
 * {@code protocol.identity.HardenedHttpClient} refuses private address ranges and pins against DNS
 * rebinding, because <em>its</em> URLs come from attacker-controlled documents. A server address is
 * typed by the player, who has decided to trust that machine with their characters — a different
 * provenance, and therefore a different threat model. ⚠ The rule is the URL's provenance, not
 * convenience: DID documents, handle resolution and OAuth discovery keep using the hardened client.
 *
 * <h2>Status</h2>
 *
 * ⚠ <strong>The transport is not built.</strong> {@code protocol.channel} has the handshake and frame
 * layer ({@code SecureHandshake}, {@code SecureChannel}), and {@code CLAUDE.md} carries <b>T-1</b>: it
 * is a hand-rolled Noise-IK-shaped protocol, reviewed patterns but unreviewed code, and must not guard
 * anything live until a cryptographer has read it. What remains open for LAN specifically is recorded
 * as <b>LAN-6</b> in {@code docs/architecture/12-lan-mode.md}.
 *
 * <p>Until then this class holds only the address handling, which is needed either way and has no
 * security content of its own.
 */
public final class GameServerHttp {

    private GameServerHttp() {}

    /**
     * Normalises what a player typed into a base URL.
     *
     * <p>⚠ Players type {@code 192.168.1.20:8443}, not a full URL. Rejecting that would be a
     * validation message about a scheme nobody thinks about, on the first screen of the mode.
     *
     * <p>⚠ Defaults to <strong>https</strong>. An earlier version defaulted to {@code http} for LAN —
     * see the class note; that was the wrong call, and defaulting to the plaintext scheme is how a
     * link silently ends up unencrypted on the one mode whose whole trust model is the network.
     *
     * @param typed the address as entered
     * @return a base URL
     */
    public static URI baseUrl(String typed) {
        Objects.requireNonNull(typed, "typed");
        String trimmed = typed.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("enter a server address");
        }
        String withScheme = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        URI uri = URI.create(withScheme);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("that is not a valid address: " + typed);
        }
        return uri;
    }
}
