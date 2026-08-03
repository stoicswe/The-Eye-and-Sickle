package io.github.stoicswe.eyeandsickle.protocol.identity;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * Decides whether an outbound address is one this process is allowed to fetch from.
 *
 * <h2>Why this exists at all</h2>
 *
 * DID and handle resolution is the <strong>first outbound HTTP either side of this game makes</strong>
 * ({@code docs/architecture/10-oauth-and-did-resolution.md} §4.3), and every URL involved is derived
 * from attacker-supplied input: a handle the player typed, a hostname inside a {@code did:web}, a PDS
 * endpoint inside a DID document somebody else wrote. That is the textbook shape of a server-side
 * request forgery — a hostile DID document naming {@code http://169.254.169.254/} turns this server's
 * resolver into a cloud-metadata reader on the attacker's behalf.
 *
 * <p>The spec calls for "a hardened HTTP client"; this is the half of it that can be tested without a
 * socket, which is why it is a separate type rather than a private method on the client.
 *
 * <h2>⚠ What this does NOT stop: DNS rebinding</h2>
 *
 * {@link #permits(InetAddress)} judges an address that has <em>already been resolved</em>.
 * {@link HardenedHttpClient} resolves a host, checks every address it got, and then makes the request
 * — and the JDK's HTTP client resolves the name <em>again</em> when it connects. A name that answers
 * with a public address on the first lookup and a loopback address on the second walks straight
 * through. Closing that hole properly means connecting to a pinned {@link InetAddress} rather than to
 * a hostname, which the JDK client does not expose.
 *
 * <p>This is a <strong>known, accepted residual risk</strong> and is recorded as such rather than
 * papered over. It is a much smaller hole than the one being closed — it needs an attacker running
 * authoritative DNS with a sub-second TTL, rather than merely a text field — but it is not zero, and
 * anyone hardening this further should start here.
 *
 * <h2>Deny by range, not by name</h2>
 *
 * The list is of <em>address ranges</em>, never of hostnames. A hostname denylist is bypassed by
 * pointing any name you control at the address you wanted; the address is the thing that matters, so
 * the address is the thing that is checked. {@code metadata.google.internal} needs no entry here — it
 * resolves into link-local, which is already refused.
 */
public final class SsrfGuard {

    private SsrfGuard() {}

    /**
     * Checks the shape of a URL before anything is resolved or connected.
     *
     * <p>Cheap, purely syntactic, and deliberately separate from {@link #permits(InetAddress)}: a URL
     * can be refused on its scheme without a DNS lookup, and refusing early means a hostile document
     * full of {@code file://} references costs nothing to reject.
     *
     * @param uri the URL about to be fetched
     * @return null if the URL is acceptable, otherwise the reason it is not
     */
    public static String rejectUrl(URI uri) {
        if (uri == null) {
            return "no URL";
        }
        if (!uri.isAbsolute()) {
            return "not an absolute URL: " + uri;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https")) {
            // HTTPS only, with no development exception. Every URL this client fetches comes from
            // the atproto specs, and all of them are HTTPS: plc.directory, /.well-known/did.json,
            // /.well-known/atproto-did, /.well-known/oauth-*. A plaintext hop would make handle
            // verification (§4.1) defeatable by anyone on the path, which would defeat its purpose.
            return "only https is allowed, got '" + scheme + "': " + uri;
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return "no host: " + uri;
        }
        if (uri.getUserInfo() != null) {
            // Credentials in a URL are how a fetch gets aimed at a host that then treats it as
            // authenticated, and they have no legitimate use in any of these endpoints.
            return "credentials in URL are not allowed: " + uri.getHost();
        }
        return null;
    }

    /**
     * Decides whether a resolved address may be connected to.
     *
     * @param address a resolved address
     * @return true if the address is a normal public one
     */
    public static boolean permits(InetAddress address) {
        return reject(address) == null;
    }

    /**
     * Same decision as {@link #permits}, but says why.
     *
     * <p>Returning the reason rather than a boolean is worth the extra type: an SSRF refusal that
     * says only "refused" is indistinguishable from an outage when a self-hoster is behind NAT and
     * genuinely confused about why their own PDS will not resolve.
     *
     * @param address a resolved address
     * @return null if the address is allowed, otherwise the range that refused it
     */
    public static String reject(InetAddress address) {
        if (address == null) {
            return "no address";
        }
        if (address.isAnyLocalAddress()) {
            return "wildcard address";
        }
        if (address.isLoopbackAddress()) {
            return "loopback";
        }
        if (address.isLinkLocalAddress()) {
            // Covers 169.254.0.0/16 and fe80::/10 — and therefore the cloud metadata endpoints
            // (169.254.169.254 on AWS/Azure/GCP), which is the single highest-value SSRF target.
            return "link-local (includes cloud metadata)";
        }
        if (address.isSiteLocalAddress()) {
            // 10/8, 172.16/12, 192.168/16.
            return "private (RFC 1918)";
        }
        if (address.isMulticastAddress()) {
            return "multicast";
        }

        byte[] a = address.getAddress();
        if (a.length == 4) {
            return rejectIpv4(a);
        }
        if (a.length == 16) {
            return rejectIpv6(a);
        }
        return "unrecognised address length " + a.length;
    }

    private static String rejectIpv4(byte[] a) {
        int b0 = a[0] & 0xFF;
        int b1 = a[1] & 0xFF;

        if (b0 == 0) {
            return "\"this network\" (0.0.0.0/8)";
        }
        if (b0 == 100 && (b1 & 0xC0) == 64) {
            // 100.64.0.0/10, carrier-grade NAT. NOT covered by isSiteLocalAddress, and it is where
            // Alibaba Cloud puts its metadata service (100.100.100.200).
            return "carrier-grade NAT (100.64.0.0/10)";
        }
        if (b0 == 192 && b1 == 0 && (a[2] & 0xFF) == 0) {
            return "IETF protocol assignments (192.0.0.0/24)";
        }
        if (b0 == 198 && (b1 & 0xFE) == 18) {
            return "benchmarking (198.18.0.0/15)";
        }
        if (b0 >= 240) {
            // 240/4 reserved, and 255.255.255.255 broadcast with it.
            return "reserved (240.0.0.0/4)";
        }
        return null;
    }

    private static String rejectIpv6(byte[] a) {
        if ((a[0] & 0xFE) == 0xFC) {
            // fc00::/7 unique-local. isSiteLocalAddress only covers the DEPRECATED fec0::/10, so
            // without this the modern private IPv6 range is allowed straight through.
            return "unique-local IPv6 (fc00::/7)";
        }
        if (isIpv4Mapped(a)) {
            // ::ffff:127.0.0.1 and friends. Java usually hands these back as an Inet4Address, so
            // the earlier checks normally catch them — but "usually" is not a security property,
            // and a literal in a hostile document is exactly where the exception would come from.
            byte[] v4 = {a[12], a[13], a[14], a[15]};
            String mapped = rejectIpv4(v4);
            if (mapped != null) {
                return "IPv4-mapped " + mapped;
            }
            int b0 = v4[0] & 0xFF;
            if (b0 == 127) {
                return "IPv4-mapped loopback";
            }
            if (b0 == 10 || (b0 == 172 && (v4[1] & 0xF0) == 16) || (b0 == 192 && (v4[1] & 0xFF) == 168)) {
                return "IPv4-mapped private (RFC 1918)";
            }
            if (b0 == 169 && (v4[1] & 0xFF) == 254) {
                return "IPv4-mapped link-local";
            }
        }
        return null;
    }

    private static boolean isIpv4Mapped(byte[] a) {
        for (int i = 0; i < 10; i++) {
            if (a[i] != 0) {
                return false;
            }
        }
        return (a[10] & 0xFF) == 0xFF && (a[11] & 0xFF) == 0xFF;
    }
}
