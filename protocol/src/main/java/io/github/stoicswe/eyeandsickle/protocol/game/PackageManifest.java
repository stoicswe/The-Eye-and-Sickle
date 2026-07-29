package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Everything a package declares about itself, and what it actually turned out to be.
 *
 * <h2>Why the two SHAs are separate fields</h2>
 *
 * {@link #expectedSha} is what the package's own manifest says its payload hashes to.
 * {@link #actualSha} is what the bytes on this disk hash to. Carrying one field and a boolean would
 * be strictly less useful and strictly less honest: the entire point of a checksum is that you are
 * shown both figures and can see for yourself that they are the same, which is what makes the day
 * they differ mean anything. A panel that only ever printed "verified ✓" would teach a player to
 * trust a tick mark rather than to compare a digest.
 *
 * <p>⚠ <b>They always match today, and the seam is deliberate.</b> Every package in single player
 * comes from the vendor catalogue or off a machine the player broke into, and neither can tamper with
 * anything — there is exactly one party. The mismatch path exists, is rendered, and is tested,
 * because the player-to-player market in online play is precisely where a package's payload can stop
 * agreeing with its manifest, and a verification step introduced at that point would be a new
 * mechanic arriving at the same moment as the threat it defends against. Introduced here, it is a
 * habit by the time it matters.
 *
 * @param path where the file is
 * @param name the file's own name — {@code .pkg} while it is a vendor's, {@code .upg} once it is
 *     this rig's. That rename is the confirmation lock; see {@code docs/design/04-mining.md} §1.3e
 * @param itemType the catalogue id this installs as
 * @param displayName what the installed tool is called
 * @param summary what the tool does, in the catalogue's own words
 * @param publisher who signed it — a vendor for a bought package, the machine it came off for a
 *     stolen one
 * @param origin how it got here, in words
 * @param gate the unlock gate the installed item sits behind
 * @param sizeBytes the payload's size
 * @param equippedCycles what it will reserve when equipped, or 0
 * @param expectedSha the digest the manifest declares
 * @param actualSha the digest of the payload actually on disk
 * @param locked waiting on a payment to be mined — see {@link #pendingNote}
 * @param pendingNote why it is locked, in words; empty when it is not
 * @param owned whether this tool is already installed, in which case installing again does nothing
 * @param installable whether {@code install} would currently succeed
 */
public record PackageManifest(
        String path,
        String name,
        String itemType,
        String displayName,
        String summary,
        String publisher,
        String origin,
        UnlockGate gate,
        long sizeBytes,
        long equippedCycles,
        String expectedSha,
        String actualSha,
        boolean locked,
        String pendingNote,
        boolean owned,
        boolean installable) {

    /**
     * Whether the payload is what the manifest says it is.
     *
     * <p>⚠ The one question this panel exists to answer. It is compared rather than trusted: a
     * package that says it is a Noise Damper and hashes to something else is not a Noise Damper, and
     * no amount of the manifest being well-formed changes that.
     */
    public boolean shaMatches() {
        return expectedSha != null && expectedSha.equals(actualSha);
    }

    /** A digest, shortened the way every tool that prints one shortens it. */
    public static String shorten(String sha) {
        if (sha == null || sha.length() < 20) {
            return String.valueOf(sha);
        }
        return sha.substring(0, 10) + "…" + sha.substring(sha.length() - 6);
    }

    /** Whether this package came from the vendor catalogue rather than off somebody's machine. */
    public boolean fromMarket() {
        return "market".equals(origin);
    }
}
