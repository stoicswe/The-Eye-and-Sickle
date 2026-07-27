package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Objects;

/**
 * An Enumeration layer: a row of port slots, whatever aggregate readings the player has bought over
 * them, and the set they are currently prepared to declare.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1 — "map a node's open ports/services before you can
 * act", skill tested: reading structure. The punished failure mode is acting before you know the shape.
 *
 * <h2>The banner is the human-read step, and it is why a bot stalls here</h2>
 *
 * §3.2 requires <em>each</em> class to carry "a verification step that rewards a human read", because
 * that step is where Invariant I10 lives: a bot "plays to a fixed heuristic and stalls on the
 * human-read step, which is exactly where manual play pulls ahead". §3.2 spells the step out for Logic
 * and Traversal and leaves Enumeration's implied; the service banner is it.
 *
 * <p>The target announces its role — an edge relay, a storage array, an auth broker, a media cache —
 * and the role constrains where open ports can be. A player who reads the banner and knows the
 * convention eliminates whole bands without spending a probe on them. A fixed heuristic, per §3.2(d),
 * "cannot use the intuition shortcuts a human gets from reading flavor data", and sweeps them anyway.
 *
 * <p><strong>The constraint itself is not on the wire and must never be put there.</strong> It is
 * learned by playing, and it is written down for the designer in {@code
 * docs/design/16-breach-implementation.md}, not for the client. {@code bannerNote} is allowed to
 * gesture at it in flavour; it is not allowed to state it. Publishing the rule would hand the bot the
 * read and delete the class's claim on I10 — which is the whole reason the class survived the cut from
 * five to three.
 *
 * <h2>Why {@code knownOpenTotal} uses a sentinel</h2>
 *
 * Zero is a real and useful answer here — "there are no open ports at all in this range" is exactly the
 * sort of thing a Side-Channel read can legitimately return — so absence cannot be encoded as 0, and
 * {@code -1} is used instead. Keeping the field a primitive also means a renderer refreshing mid-breach
 * cannot NPE on it, which matters for a value that is unset for most of a layer's life.
 *
 * @param banner the target's announced role, e.g. {@code "EDGE RELAY"}. Never blank — a board without
 *     the human-read hook is not an Enumeration layer, it is a lottery
 * @param bannerNote one line of role flavour that may hint at the convention without stating it;
 *     {@code ""} when there is nothing to add
 * @param slots how many ports the board has
 * @param bandSize how many slots one sweep covers
 * @param ports every slot, index-ordered, {@code slots} of them; unprobed ones read
 *     {@link PortState#UNKNOWN}
 * @param readings sweep results so far, oldest first — an append-only account, like the ledger
 * @param knownOpenTotal how many ports are open across the whole board, once the Side-Channel Reader
 *     has said so; {@code -1} while unknown
 * @param declared the set the player is currently composing; pure bookkeeping until they submit it
 */
public record EnumerationBoard(
        String banner,
        String bannerNote,
        int slots,
        int bandSize,
        List<PortSlot> ports,
        List<BandReading> readings,
        int knownOpenTotal,
        List<Integer> declared)
        implements BreachBoard {

    public EnumerationBoard {
        Objects.requireNonNull(banner, "banner");
        Objects.requireNonNull(bannerNote, "bannerNote");

        // List.copyOf both freezes the list against a caller that keeps mutating its own state object
        // and rejects nulls outright — which is why "unknown" is "" everywhere in this vocabulary and
        // never a null element.
        ports = List.copyOf(ports);
        readings = List.copyOf(readings);
        declared = List.copyOf(declared);

        if (banner.isBlank()) {
            throw new IllegalArgumentException("An Enumeration board must announce a role: the banner is the "
                    + "class's human-read step (docs/design/05-hacking-minigame.md §3.2, Invariant I10)");
        }
        if (slots < 0) {
            throw new IllegalArgumentException("slots must not be negative, was " + slots);
        }
        if (bandSize < 1) {
            throw new IllegalArgumentException("bandSize must be positive, was " + bandSize);
        }
        if (knownOpenTotal < -1) {
            throw new IllegalArgumentException("knownOpenTotal is a count or -1 for unknown, was " + knownOpenTotal);
        }
    }

    @Override
    public PuzzleClass puzzleClass() {
        return PuzzleClass.ENUMERATION;
    }
}
