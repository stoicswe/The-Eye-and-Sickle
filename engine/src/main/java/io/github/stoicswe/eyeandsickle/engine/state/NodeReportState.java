package io.github.stoicswe.eyeandsickle.engine.state;

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

    /**
     * What the player calls this machine. Empty means "no name given".
     *
     * <h2>⚠ The player's name NEVER replaces the address</h2>
     *
     * The address is what the machine is; the alias is what the player decided to call it. A list
     * that showed only the alias would make two rows indistinguishable the moment somebody named two
     * machines "backup", and would hide the one field every other window keys on. Both are shown, and
     * both are searched.
     */
    public String alias = "";

    /**
     * The player's own labels — free text, lowercased on the way in.
     *
     * <p>Deliberately unconstrained: a tag vocabulary the game defined would be the game deciding
     * what is worth noticing about a machine, which is exactly the judgement the player is here to
     * make. What the game supplies is the search.
     */
    public java.util.List<String> tags = new java.util.ArrayList<>();

    /** When the first scan of this machine came back. */
    public Instant createdAt = Instant.EPOCH;

    /** When the most recent one did. */
    public Instant updatedAt = Instant.EPOCH;

    /** How many scans have completed against it, at any depth. */
    public int scans = 0;

    /** How many of those were noticed. A machine that keeps catching you is worth knowing about. */
    public int detections = 0;

    /**
     * What the machine calls itself, and the account that runs it. Empty means "never established".
     *
     * <h2>⚠ These two are WRITE-ONCE, and every other finding on this file is not</h2>
     *
     * A firewall tier, a cycle load or a vault estimate is a <em>measurement</em>: it can change, a
     * rescan should refresh it, and {@link #learnedAt} exists so an old one reads as old. A name is
     * not a measurement, it is an <b>identity</b> — so the first scan or breach that establishes it
     * pins it, and no later scan overwrites it.
     *
     * <p>The reason is not that the world renames machines; it does not. It is that the name is
     * <em>derived</em> from the address by {@code NpcNames}, so editing a name pool shifts every
     * derived name at once. Pinning at first contact means a machine the player has been calling
     * {@code bold-turing} for ten hours is still called that after somebody adds a word to the
     * adjective list — and it is what makes "the operator you found when you first broke in" a fact
     * about that break-in rather than a fact about the current build.
     *
     * <p>⚠ {@code NodeReports.merge} is where the write-once rule lives, and it is one {@code if}
     * away from being a refresh like all the others. {@code NodeReportTest} pins it.
     */
    public String hostName = "";

    public String operatorName = "";

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
