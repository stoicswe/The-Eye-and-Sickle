package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;

/**
 * What one sweep produced.
 *
 * <p>A sweep probes the network around the player's current position and reports which machines it
 * detected. It is a value rather than something a view recomputes because the whole result is decided
 * when the sweep <em>begins</em> and applied when it settles — the same generate-once-and-persist rule
 * {@code docs/design/16-breach-implementation.md} §2 gives puzzle boards, so a reload mid-sweep replays
 * nothing and finishes the sweep the player actually started.
 *
 * <h2>{@code inRange} is the only aggregate anything may publish about undetected machines</h2>
 *
 * Everywhere else in this vocabulary, a machine the player has not found leaves no trace at all: no
 * {@link Sighting}, no cell on the graph, no row in the list, no count. This field is the one exception
 * and it is a narrow one — it says how many machines were <em>inside the player's reach</em>, i.e. how
 * many the instrument considered, not how many exist and not anything about the ones it missed.
 *
 * <p>It is safe because it describes the instrument rather than the network. {@code
 * docs/design/04-mining.md} §3.3 states the rule it teaches — "Nothing announces itself. <strong>Signal
 * strength is what the player pays for</strong>" — and §3.2a makes the same point about scan tiers:
 * "Signal quality, not just sensitivity, is what a more expensive tier buys." "Nine were in range and I
 * found four" is a player learning what their own sweep is worth, which is exactly the reading that makes
 * the next purchase a decision instead of a guess. It carries no address, no type, no tier and no value,
 * so there is nothing in it to act on except buying a better instrument or standing somewhere else.
 *
 * <h2>Why the same sweep, run twice, is not a re-roll</h2>
 *
 * Detection is never drawn at sweep time: it compares the instrument's sensitivity against a value fixed
 * before the player asked — the machine's own roll, settled when the world was generated, scaled by a
 * hash of the machine and the position it is being heard from. So the same tier from the same position
 * returns the same machines forever, and quitting without saving changes nothing. Only two things move
 * it, and both cost: a better sweep (ethecoin, plus its compute and its noise) or a <b>different</b>
 * position (a breach, a foothold, and moving the vantage). ⚠ "Different", not merely "closer", since
 * 2026-08-08 — a position the same distance away hears a different subset, which is what makes working
 * outward build a graph rather than merely widen a circle. {@code note} is where a producer says that in the player's language
 * when a sweep finds nothing new, so the mechanic teaches rather than merely disappointing — the prose
 * belongs to the side that knows why the sweep came back empty, never to the renderer.
 *
 * <h2>What is not here: a heat figure</h2>
 *
 * {@code counterHacked} says a machine noticed and hit back; it does not say what that cost. A sweep
 * reaches other machines, which makes it the intrusive outbound kind of action {@code
 * docs/design/01-core-resources.md} §3 prices in noise and §4.1 charges the player heat for, but how much
 * heat is a balance value and arrives through the same readouts as every other heat change. The
 * consolation is structural rather than in this record:
 * a counter-hack leaves a foreign miner on the player's own rig, and cracking one of those generates no
 * heat on any outcome (Invariant I9, {@code docs/design/04-mining.md} §5.1), so being hit back hands the
 * player the safest teaching target in the game.
 *
 * @param sweepToolId which instrument was run
 * @param vantageAddress where it was run from — the same sweep from two positions is two different sweeps
 * @param inRange how many machines were inside the hop ceiling; see above for why this one aggregate is
 *     permitted and what it must never grow into
 * @param found how many were newly detected; never more than {@code inRange}
 * @param foundAddresses the addresses newly detected
 * @param counterHacked whether something hit back
 * @param note the player-facing line, in the producer's words; {@code ""} when there is nothing to add
 */
public record SweepReport(
        String sweepToolId,
        String vantageAddress,
        int inRange,
        int found,
        List<String> foundAddresses,
        boolean counterHacked,
        String note) {

    public SweepReport {
        foundAddresses = List.copyOf(foundAddresses == null ? List.of() : foundAddresses);
        sweepToolId = sweepToolId == null ? "" : sweepToolId;
        vantageAddress = vantageAddress == null ? "" : vantageAddress;
        note = note == null ? "" : note;

        if (inRange < 0) {
            throw new IllegalArgumentException("inRange is a count of machines considered, was " + inRange);
        }
        if (found < 0) {
            throw new IllegalArgumentException("found is a count of machines detected, was " + found);
        }
        // The detected set is a subset of the considered set, by definition of both. A report claiming
        // otherwise is the one arithmetic error that would actively mislead: the player reads these two
        // numbers as a fraction and decides whether to buy a better sweep from it, so found > inRange
        // tells them their instrument is better than perfect.
        if (found > inRange) {
            throw new IllegalArgumentException(
                    "a sweep cannot detect more machines than it considered; found " + found + " of " + inRange);
        }

        // foundAddresses.size() is deliberately NOT required to equal `found`. A producer that counts
        // more than it names is disclosing less than it knows, which is always a legal move here — the
        // same reasoning BreachSnapshot uses for an activeLayer past the end of its layer list.
    }
}
