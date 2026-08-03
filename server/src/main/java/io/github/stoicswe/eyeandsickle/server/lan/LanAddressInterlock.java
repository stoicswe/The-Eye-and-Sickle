package io.github.stoicswe.eyeandsickle.server.lan;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Refuses to run a LAN-mode server on a public address.
 *
 * <h2>⚠ This is the most important check in LAN mode</h2>
 *
 * LAN mode has <strong>no authentication</strong>. Identity is a UUID the server hands out to anyone
 * who asks for one, and that is acceptable for exactly one reason: the network is the trust boundary,
 * so the people who can reach the server are people who were invited onto the LAN.
 *
 * <p>A LAN-mode server bound to a public interface is therefore <strong>an open server that will mint
 * an identity for any stranger on the internet</strong>. ⚠ And the failure is completely silent — it
 * works perfectly, for everyone, including everyone who was never meant to reach it. Nothing else in
 * the system would notice.
 *
 * <p>So this fails the server at startup rather than warning. Every other decision in
 * {@code docs/architecture/12-lan-mode.md} rests on this one being true.
 *
 * <h2>What counts as private</h2>
 *
 * Loopback, link-local, RFC 1918 site-local, and IPv6 unique-local ({@code fc00::/7}). ⚠ The last is
 * checked by hand because {@code isSiteLocalAddress()} covers only the <em>deprecated</em>
 * {@code fec0::/10} for IPv6 — the same trap {@code protocol identity SsrfGuard} documents, and it
 * would let a modern IPv6 network through as "public" or a public one through as private depending on
 * which way the check was written.
 */
public class LanAddressInterlock implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(LanAddressInterlock.class);

    private final LanProperties properties;

    public LanAddressInterlock(LanProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.mode().isLan()) {
            return;
        }
        List<InetAddress> publicAddresses = publicAddresses();
        if (publicAddresses.isEmpty()) {
            log.info("LAN mode: every local address is private. The network is the trust boundary.");
            return;
        }
        if (properties.allowPublicAddress()) {
            // ⚠ Logged at ERROR even though the operator asked for it. This is not a configuration
            // preference; it is a server with no authentication reachable from a public address, and
            // the log is the only record that somebody chose it.
            log.error(
                    "LAN mode is running with PUBLIC addresses {} because eyeandsickle.allow-public-address is set. "
                            + "This server hands an identity to anyone who asks. Do not leave this on.",
                    publicAddresses);
            return;
        }
        throw new IllegalStateException(
                "LAN mode refuses to start: this machine has public addresses " + publicAddresses
                        + ". LAN mode has NO authentication — identity is a UUID given to whoever asks — and it is "
                        + "only safe while the network is the trust boundary. Bind to a private interface, run "
                        + "federated mode instead, or set eyeandsickle.allow-public-address=true if you are certain "
                        + "the container network is private. See docs/architecture/12-lan-mode.md section 2.");
    }

    /** Every non-private address this machine holds. */
    static List<InetAddress> publicAddresses() {
        List<InetAddress> found = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!isPrivate(address)) {
                        found.add(address);
                    }
                }
            }
        } catch (Exception unreadable) {
            // ⚠ Fail CLOSED. If the interfaces cannot be enumerated, "no public addresses found" is
            // not a safe conclusion — it is an unanswered question, and the safe answer to an
            // unanswered question here is to refuse.
            throw new IllegalStateException(
                    "LAN mode could not enumerate this machine's network interfaces, so it cannot confirm the "
                            + "server is on a private network. Refusing to start.",
                    unreadable);
        }
        return found;
    }

    static boolean isPrivate(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // fc00::/7 unique-local. isSiteLocalAddress covers only the DEPRECATED fec0::/10 for IPv6, so
        // without this a modern private IPv6 network reads as public and the server refuses to start
        // on exactly the setup LAN mode is for.
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
