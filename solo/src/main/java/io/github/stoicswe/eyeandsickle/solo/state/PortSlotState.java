package io.github.stoicswe.eyeandsickle.solo.state;

/**
 * One port slot on an Enumeration board: what it really is, and how much of that the player has
 * established.
 *
 * <h2>{@link #truth} is the hidden half, and it must stay hidden</h2>
 *
 * The whole Enumeration class is "find the shape before you declare it" ({@code
 * docs/design/05-hacking-minigame.md} §3.1), so a snapshot that leaked {@link #truth} for an
 * unrevealed slot would hand the player the answer. {@code BreachSnapshots} reads {@link #revealed}
 * <em>before</em> it reads {@link #truth} for exactly that reason, and never the other way round.
 *
 * <p>Yes, this is a save file the player can open in a text editor. The discipline is not about them
 * — it is about the same records going over a wire to a real home server, where the client is never
 * authoritative (Invariant I14). A snapshot builder that only hides secrets when it remembers to is
 * one refactor from not hiding them at all.
 *
 * <p>⚠ {@link #index} is a slot on this board, not a TCP port number. The game never claims that
 * slot 22 is ssh; {@link #service} is a name attached to a slot by generation, and nothing in the
 * curriculum is allowed to rest on a mapping this class invented.
 */
public final class PortSlotState {

    public int index = 0;

    /** {@code CLOSED}, {@code OPEN} or {@code FILTERED}. ⚠ Never snapshotted unless {@link #revealed}. */
    public String truth = "CLOSED";

    /** Whether the player has established this slot's state — by probing, bannering or side-channel. */
    public boolean revealed = false;

    /** The service name on an open slot. Empty until revealed, and empty on a slot that is not open. */
    public String service = "";

    public PortSlotState() {}
}
