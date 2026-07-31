package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;
import java.util.Map;

/**
 * The intelligence file on one machine — everything learned about it, and when.
 *
 * <h2>⚠ Every finding carries its OWN age, and one timestamp for the file would not do</h2>
 *
 * {@link #updatedAt} says when the file was last touched; {@link #learnedAt} says when each individual
 * fact was established. Those are different questions, and collapsing them is worse than storing
 * nothing: a cheap firewall check this morning would make a week-old vault estimate look like it was
 * measured this morning too, which is stale intelligence presented with fresh confidence.
 *
 * <p>The cycle load is the field this matters most for. It is a <b>snapshot</b> — true at the instant
 * it was taken, a guess five minutes later — and the panel prints its age beside it for exactly that
 * reason.
 *
 * <p>⚠ Unknown is {@code -1} throughout, never {@code 0}. "Nobody has looked" and "there are none" are
 * different answers about a machine somebody is deciding whether to rob.
 *
 * @param address the machine
 * @param label the name the world gave it, or empty
 * @param alias the name the PLAYER gave it, or empty — never a replacement for the address
 * @param tags the player's own labels, lowercased
 * @param createdAt when the first scan of it came back
 * @param updatedAt when the most recent one did
 * @param scans how many have completed against it
 * @param detections how many of those it noticed
 * @param learnedAt when each finding was established, keyed by {@link PortScanTarget#name()}
 */
public record NodeReport(
        String address,
        String label,
        String alias,
        java.util.List<String> tags,
        Instant createdAt,
        Instant updatedAt,
        int scans,
        int detections,
        int firewallTier,
        String osName,
        long cyclesTotal,
        long cyclesUsed,
        long downloadsBytes,
        int vaultHighCount,
        int vaultMediumEstimate,
        int vaultMediumError,
        Map<String, Instant> learnedAt) {

    /** Whether this finding has ever been established. */
    public boolean knows(PortScanTarget target) {
        return learnedAt.containsKey(target.name());
    }

    /** When it was, or null if never. */
    public Instant when(PortScanTarget target) {
        return learnedAt.get(target.name());
    }

    /** Whether anything at all is on file. */
    public boolean any() {
        return !learnedAt.isEmpty();
    }

    /** What to call this machine in a list: the player's name if they gave one, else the world's. */
    public String displayName() {
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        return label == null || label.isBlank() ? address : label;
    }

    /**
     * Whether this report answers a search.
     *
     * <h2>⚠ Every field a player might remember it by</h2>
     *
     * Address, the name they gave it, the name the world gave it, and their tags. A search that
     * matched only the alias would fail the player who named nothing and tagged everything, and one
     * that matched only tags would fail the reverse — and the whole point of the box is to find a
     * report from whatever the player happens to remember about it.
     *
     * <p>Case-insensitive and substring, because a search that demanded an exact tag would need the
     * player to already know the answer.
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (contains(address, needle) || contains(alias, needle) || contains(label, needle)) {
            return true;
        }
        for (String tag : tags) {
            if (contains(tag, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    /** Cycles free at the moment the load was sampled, or -1 if it never was. */
    public long cyclesFree() {
        return cyclesTotal < 0 || cyclesUsed < 0 ? -1L : Math.max(0L, cyclesTotal - cyclesUsed);
    }

    public int vaultMediumLow() {
        return vaultMediumEstimate < 0 ? -1 : Math.max(0, vaultMediumEstimate - vaultMediumError);
    }

    public int vaultMediumHigh() {
        return vaultMediumEstimate < 0 ? -1 : vaultMediumEstimate + vaultMediumError;
    }

    /**
     * How complete the file is, as a count of the seven rungs.
     *
     * <p>What the RECON list shows so a player can tell at a glance which machines they have actually
     * worked on from the ones they glanced at once.
     */
    public int known() {
        return learnedAt.size();
    }

    /** How many rungs there are to know. */
    public static int total() {
        return PortScanTarget.values().length;
    }
}
