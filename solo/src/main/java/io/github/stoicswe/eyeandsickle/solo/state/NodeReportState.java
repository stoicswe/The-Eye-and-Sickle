package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What this character has learned about one machine, accumulated across every scan of it.
 *
 * <h2>⚠ This is PERSISTED, reversing an earlier decision — and the timestamps are why</h2>
 *
 * A port-scan report was session state at first, on the reasoning that the cycle-load line is a
 * <b>snapshot</b>: it was true at the instant it was taken and is a guess five minutes later, so
 * persisting one would hand a returning player a figure measured last Tuesday with the same
 * confidence as one measured thirty seconds ago.
 *
 * <p>That objection is answered by {@link #learnedAt} rather than by throwing the report away.
 * Every finding records <em>when</em> it was learned, and the panel prints the age beside the value —
 * so a stale load reads as stale instead of reading as current. Discarding the intelligence was
 * always the worse half of the trade; what was actually needed was for the readout to say how old it
 * is.
 *
 * <h2>Findings ACCUMULATE, and a rescan refreshes rather than replaces</h2>
 *
 * A shallow scan learns the firewall; a deep one later adds the vault estimate and re-reads the
 * firewall. The report keeps the best-known value for every field with its own timestamp, so a player
 * who paid for a deep scan last week and a cheap one this morning has an accurate firewall reading
 * and a week-old vault estimate — which is exactly what they have, and exactly what the panel should
 * say.
 *
 * <p>⚠ Unknown is {@code -1} throughout, never {@code 0}. "The scan never looked" and "there are none"
 * are different answers, and a report that printed a confident zero for the first would be lying
 * about a machine the player is deciding whether to rob.
 */
public final class NodeReportState {

    public String address = "";

    /** When the first scan of this machine came back. */
    public Instant createdAt = Instant.EPOCH;

    /** When the most recent one did. */
    public Instant updatedAt = Instant.EPOCH;

    /** How many scans have completed against it, at any depth. */
    public int scans = 0;

    /** How many of those were noticed. A machine that keeps catching you is worth knowing about. */
    public int detections = 0;

    public int firewallTier = -1;
    public String osName = "";
    public long cyclesTotal = -1L;
    public long cyclesUsed = -1L;
    public long downloadsBytes = -1L;
    public int vaultHighCount = -1;
    public int vaultMediumEstimate = -1;

    /** Half-width of the band around the estimate. Narrows with repeat deep scans; never reaches 0. */
    public int vaultMediumError = 0;

    /**
     * When each finding was learned, keyed by {@code PortScanTarget.name()}.
     *
     * <h2>⚠ Per FIELD, not per report, and that is the whole point of persisting any of this</h2>
     *
     * One timestamp for the report would date every field to the most recent scan — so a cheap
     * firewall check this morning would make a week-old vault estimate look like it was taken this
     * morning too. That is worse than not storing the estimate at all, because it presents stale
     * intelligence with fresh confidence.
     *
     * <p>A {@code Map} rather than paired fields so the save is self-describing and adding an eighth
     * rung to {@code PortScanTarget} needs no migration.
     */
    public Map<String, Instant> learnedAt = new LinkedHashMap<>();

    /** Whether anything at all has been learned about this machine. */
    public boolean any() {
        return scans > 0 && !learnedAt.isEmpty();
    }
}
