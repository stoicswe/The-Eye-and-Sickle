package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Which build of a tool a package carries.
 *
 * <h2>⚠ A version is NOT a capability tier, and that is the load-bearing decision</h2>
 *
 * A newer build is <b>worth more and supersedes an older one; it is not a better tool</b>. A
 * {@code v2.4} Net Sweep (Wide) does exactly what a {@code v1.8} does — one hop, the same detection
 * — and the difference is what it fetches on the secondary market and the fact that installing it
 * replaces the older one.
 *
 * <p>That restraint is what keeps the whole idea legal. If a newer version were a <em>better</em>
 * tool, then raiding harder machines would be a capability ladder with no gate on it: a player could
 * climb to a ceiling nobody sold them, which is Invariant <b>I2</b> from an unexpected direction, and
 * the item would sit behind two gates at once, which is <b>I3</b>. What a version buys is value and a
 * reason to prefer one target over another — breadth, not ceiling.
 *
 * <h2>Why a record rather than a string</h2>
 *
 * The whole feature is <em>comparison</em>: "is the one on their machine newer than mine". A string
 * compares lexically, so {@code "v1.10"} sorts before {@code "v1.9"} and the one question this type
 * exists to answer gets a wrong answer that looks right. Two integers cannot do that.
 *
 * @param major bumped when the tool's line changes; the number a player actually reads
 * @param minor the build within that line
 */
public record UpgradeVersion(int major, int minor) implements Comparable<UpgradeVersion> {

    /** What an item carries when nothing has recorded a version for it. */
    public static final UpgradeVersion UNKNOWN = new UpgradeVersion(0, 0);

    public UpgradeVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("a version is not negative: " + major + "." + minor);
        }
    }

    /** Whether this is a real version rather than the absence of one. */
    public boolean known() {
        return major > 0 || minor > 0;
    }

    /** Whether this build replaces {@code other} — strictly newer, never equal. */
    public boolean supersedes(UpgradeVersion other) {
        return other != null && compareTo(other) > 0;
    }

    @Override
    public int compareTo(UpgradeVersion other) {
        int byMajor = Integer.compare(major, other.major);
        return byMajor != 0 ? byMajor : Integer.compare(minor, other.minor);
    }

    /** {@code v2.4} — how every surface in the game prints one. */
    @Override
    public String toString() {
        return "v" + major + "." + minor;
    }

    /**
     * Reads one back, tolerantly.
     *
     * <p>⚠ Returns {@link #UNKNOWN} rather than throwing on anything it cannot read. This parses a
     * field out of a save file, and a save carrying a version this build does not understand — an
     * older one that had no versions, a hand-edited one — must open rather than fail. An unreadable
     * version is the absence of one, which every surface already renders.
     */
    public static UpgradeVersion parse(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }
        String body = text.startsWith("v") || text.startsWith("V") ? text.substring(1) : text;
        int dot = body.indexOf('.');
        if (dot < 0) {
            return UNKNOWN;
        }
        try {
            int major = Integer.parseInt(body.substring(0, dot).trim());
            int minor = Integer.parseInt(body.substring(dot + 1).trim());
            return major < 0 || minor < 0 ? UNKNOWN : new UpgradeVersion(major, minor);
        } catch (NumberFormatException notAVersion) {
            return UNKNOWN;
        }
    }
}
