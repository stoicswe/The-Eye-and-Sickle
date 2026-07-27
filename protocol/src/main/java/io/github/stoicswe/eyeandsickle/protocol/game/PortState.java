package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * A port slot's state <em>as far as the player has established it</em> — the readout of an Enumeration
 * layer ({@code docs/design/05-hacking-minigame.md} §3.1: "map a node's open ports/services before you
 * can act").
 *
 * <h2>{@link #UNKNOWN} is the load-bearing constant</h2>
 *
 * The other three are answers; this one is the absence of an answer, and it is how a breach snapshot
 * carries an unmapped port without carrying the truth about it. A wire record that could only say
 * {@code CLOSED}, {@code OPEN} or {@code FILTERED} would have to be populated with the real state of
 * every slot the moment the board was generated, and the puzzle would be one deserialization away from
 * being solved for free.
 *
 * <p>That matters even in single player, where the save file is a JSON document the player may edit at
 * will. The point is not to defeat a determined cheat; it is that the honest path physically cannot
 * leak, so the same records can go over a wire to a home server without anyone re-auditing them. The
 * engine reveals a slot when the player pays for it, and only then does the state stop being
 * {@code UNKNOWN}.
 *
 * <h2>Why three answers and not a boolean</h2>
 *
 * {@link #CLOSED} and {@link #FILTERED} are different facts about the world and the player is meant to
 * act on the difference. The real-world mapping — and its sources — already live in the shipped
 * {@code port-sweep(1)} page ({@code docs/client/04-terminology-and-education.md} §4.9); this type
 * deliberately restates none of it. Anything the game <em>teaches</em> about port states changes in
 * {@code docs/education/05-networking.md} first and reaches the player through
 * {@code client/src/main/resources/.../terms/**}, never through a javadoc comment on a wire enum.
 */
public enum PortState {

    /**
     * Not established. The player has not probed this slot, or has only bought an aggregate reading
     * over the band containing it (see {@link BandReading}).
     */
    UNKNOWN,

    /** Probed, and nothing is listening. */
    CLOSED,

    /** Probed, and something is listening — the thing an Enumeration layer is asking the player to find. */
    OPEN,

    /**
     * Probed, and something between the player and the port swallowed the question. Distinct from
     * {@link #CLOSED} because it is a defence rather than an absence, and in this game it is where the
     * Enumeration class hides its canaries.
     */
    FILTERED
}
