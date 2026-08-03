package io.github.stoicswe.eyeandsickle.server.lan;

/**
 * Which of the three ways to play this server offers.
 *
 * <p>Solo is not here: it has no server ({@code solo} runs in the client's process). What a server can
 * be is federated or LAN, and {@code docs/architecture/12-lan-mode.md} is the whole comparison.
 *
 * <h2>⚠ This is a property of the DATABASE, not a switch</h2>
 *
 * A server may not be flipped from {@link #LAN} to {@link #FEDERATED} with its characters intact —
 * that is exactly "import every quarantined character at once", which is the thing
 * {@code 12} §1 exists to refuse. An operator who wants both runs two servers.
 */
public enum ServerMode {

    /**
     * The full model: AT Protocol identity, peer discovery, provenance, validator quorum.
     *
     * <p>Adversarial servers exist by design here ({@code docs/architecture/03} §1), which is why all
     * of that machinery is present.
     */
    FEDERATED,

    /**
     * A friends game on a private network — {@code docs/architecture/03} §2's "a private/allowlisted
     * server can ignore federation entirely".
     *
     * <p>Identity is a server-assigned UUID, federation is off in every direction, and nothing that
     * happens here can leave. ⚠ The trust boundary is the <em>network</em>, which is only true while
     * the server is actually on a private one — see {@link LanAddressInterlock}.
     */
    LAN;

    public boolean isLan() {
        return this == LAN;
    }
}
