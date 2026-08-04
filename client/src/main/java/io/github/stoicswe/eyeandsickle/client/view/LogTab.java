package io.github.stoicswe.eyeandsickle.client.view;

/**
 * The three views the LOG window offers.
 *
 * <h2>Why they are separate rather than one stream</h2>
 *
 * They are written for different readers, and they run at wildly different rates.
 *
 * <ul>
 *   <li>{@link #OVERVIEW} is <b>the rig's journal</b> — what happened to the player's machine, in the
 *       player's vocabulary, at a rate a person can read. It is fiction: the rig is a thing in the
 *       game.
 *   <li>{@link #EVENTS} is every {@code CloudEvent} the client's broker carried — a record of what
 *       the game's own systems told each other.
 *   <li>{@link #CLIENT} is <b>the actual application log</b>, this program's and its libraries'. It
 *       is not fiction at all; it is the same thing that would be on stdout, and it is what a bug
 *       report needs.
 * </ul>
 *
 * <p>Interleaving them would bury the handful of lines that matter to a player under machinery, which
 * is the failure {@code alert-fatigue(7)} — a page in this game's own manual — is about. ⚠ The
 * OVERVIEW/CLIENT split is the sharpest of the three: one is a story about a rig, the other is a
 * Java program reporting on itself, and a reader who cannot tell which they are looking at cannot
 * trust either.
 */
public enum LogTab {

    /**
     * The rig's journal. Everything this window has always been, unchanged.
     *
     * <p>First, and the default, because it is what a player opens LOG to read.
     */
    OVERVIEW("OVERVIEW"),

    /** Every event the broker has carried this session, for debugging. */
    EVENTS("EVENTS"),

    /**
     * The real application log — this client's and its libraries'.
     *
     * <p>Last, because it is the tab a player reaches for only when something has gone wrong, and
     * first-position belongs to what they open LOG to read.
     */
    CLIENT("CLIENT LOGS");

    private final String label;

    LogTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** What this tab shows, for a screen reader. */
    public String description() {
        return switch (this) {
            case OVERVIEW -> "The rig's journal: what has happened, newest last.";
            case EVENTS -> "Every event the client's broker has carried this session, for debugging.";
            case CLIENT -> "The application's own log, all levels, with a filter per level.";
        };
    }

    /** Brackets, not colour — the same selected state every tab strip in this deck draws (§4.4). */
    public String control(LogTab active) {
        return this == active ? "[ " + label + " ]" : "  " + label + "  ";
    }
}
