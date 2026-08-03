package io.github.stoicswe.eyeandsickle.engine.state;

/**
 * One line of the itemised attention ledger.
 *
 * <h2>This is the "comprehensible failure" constraint, persisted</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §1 constraint 4 requires a loss to read as
 * <em>"I was too loud"</em> and never as <em>"the game decided"</em>, and §4 says where that lives:
 * "the player must always be able to see which action cost what." A ledger with a gap in it is
 * therefore not a cosmetic defect — it is the constraint failing, in the one place a player will
 * look when they lose.
 *
 * <p>So the engine appends a row for <em>every</em> action it accepts, including ones that achieved
 * nothing and ones the fiction refused. A move that cost attention and produced no row is the bug.
 */
public final class AttentionEntryState {

    /** 1-based, per breach rather than per layer, so the whole attempt reads as one narrative. */
    public int sequence = 0;

    public int layerIndex = 0;

    public String actionId = "";

    /** {@code BreachActionKind.name()}. A string for the same reason every other state field is. */
    public String kind = "PROBE";

    /** What the row calls the move, already in the operator's vocabulary: {@code PROBE 07}. */
    public String label = "";

    public int cost = 0;

    /** Running total within the layer, after this row. Printed, so the arithmetic is checkable. */
    public int spentAfter = 0;

    /** What came back, in words. Never a code the player has to look up. */
    public String result = "";

    /**
     * Whether this row is a strike.
     *
     * <p>The only alarm-coloured row kind in the breach interface. {@code docs/client/07} §5.2
     * forbids colour as the sole channel, which is why the row also carries the word.
     */
    public boolean alarm = false;

    public AttentionEntryState() {}
}
