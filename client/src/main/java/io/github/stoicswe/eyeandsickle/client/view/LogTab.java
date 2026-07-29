package io.github.stoicswe.eyeandsickle.client.view;

/**
 * The two views the LOG window offers.
 *
 * <h2>Why they are separate rather than one stream</h2>
 *
 * They are written for different readers. {@link #OVERVIEW} is the rig's journal — what happened to
 * the player's machine, in the player's vocabulary, at a rate a person can read. {@link #EVENTS} is
 * every {@code CloudEvent} the client's broker carried, which is a developer's record and runs orders
 * of magnitude faster. Interleaving them would bury the handful of lines that matter to a player
 * under machinery, which is the failure {@code alert-fatigue(7)} — a page in this game's own manual —
 * is about.
 */
public enum LogTab {

    /**
     * The rig's journal. Everything this window has always been, unchanged.
     *
     * <p>First, and the default, because it is what a player opens LOG to read.
     */
    OVERVIEW("OVERVIEW"),

    /** Every event the broker has carried this session, for debugging. */
    EVENTS("EVENTS");

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
        };
    }

    /** Brackets, not colour — the same selected state every tab strip in this deck draws (§4.4). */
    public String control(LogTab active) {
        return this == active ? "[ " + label + " ]" : "  " + label + "  ";
    }
}
