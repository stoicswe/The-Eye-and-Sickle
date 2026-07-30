package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;

/**
 * One completed audit of this rig, kept so a player can compare a scan against the ones before it.
 *
 * <h2>Why a history is worth storing at all</h2>
 *
 * {@code docs/design/04-mining.md} §3.1 makes detection a matter of <b>noticing a change</b> — a
 * process that was not there last week, capacity that stopped adding up. A single scan answers "is
 * something here now"; a list of them answers "since when", which is the question that turns a
 * finding into evidence. It is also the only place a clean result means anything: one clean scan says
 * nothing, and ten clean scans followed by a hit says exactly when the rig was taken.
 *
 * @param tier which scan was run — the tiers differ in sensitivity, so a clean Quick and a clean
 *     Thorough are not the same claim and the list must not let them look alike
 * @param startedAt when it was commissioned
 * @param finishedAt when it completed. ⚠ Real elapsed time, so a scan that ran while the client was
 *     closed reports the duration it actually took rather than the duration it was quoted
 * @param seconds how long it took, in seconds — held separately because the two instants are also
 *     used for ordering and a derived duration would be recomputed at every repaint
 * @param cycles what it cost to run
 * @param summary what it found, in the rules' own words — the same sentence the log carries
 * @param found how many adversarial processes it named. ⚠ Zero is a real result and not an absence:
 *     a clean scan is the row that gives a later finding its date
 */
public record ScanReport(
        String tier,
        Instant startedAt,
        Instant finishedAt,
        long seconds,
        long cycles,
        String summary,
        int found) {

    /** Whether this scan named anything. */
    public boolean clean() {
        return found == 0;
    }

    /** The tier as a player reads it: {@code Quick}, {@code Full}, {@code Thorough}. */
    public String tierLabel() {
        if (tier == null || tier.isBlank()) {
            return "Scan";
        }
        String lower = tier.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** {@code 0:30}, the way every other duration in this client is written. */
    public String duration() {
        long safe = Math.max(0L, seconds);
        return safe / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", safe % 60);
    }
}
