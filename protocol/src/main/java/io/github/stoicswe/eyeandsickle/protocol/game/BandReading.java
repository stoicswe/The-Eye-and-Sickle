package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * The result of one sweep over a band of an Enumeration board: <em>how many</em> ports in the range are
 * open, never <em>which</em>.
 *
 * <p>This is the Enumeration class's entire information economy in one record. {@code
 * docs/design/05-hacking-minigame.md} §4 prices a quiet read at 1 attention and an ordinary probe at 2,
 * and the difference has to buy something real or the cheap action is a trap. So the cheap action buys
 * an <em>aggregate</em> and the expensive one buys a <em>location</em>: a sweep narrows the search, a
 * probe ends it. Reading structure — §3.1's stated skill for the class — is what happens in the gap
 * between those two.
 *
 * <p>The counterpart in the fiction is deliberate too: a sweep that returned positions would be a probe
 * at half price, and the class would collapse into "sweep everything, then act", which is the failure
 * mode §3.1's merge rule exists to catch.
 *
 * @param fromSlot first slot covered, inclusive
 * @param toSlot last slot covered, inclusive; never before {@code fromSlot}
 * @param openCount how many slots in the range are open — the whole payload, and never more than the
 *     range can hold
 */
public record BandReading(int fromSlot, int toSlot, int openCount) {

    public BandReading {
        if (fromSlot < 0) {
            throw new IllegalArgumentException("fromSlot must not be negative, was " + fromSlot);
        }
        if (toSlot < fromSlot) {
            throw new IllegalArgumentException(
                    "A band runs forwards; got fromSlot " + fromSlot + " and toSlot " + toSlot);
        }
        if (openCount < 0) {
            throw new IllegalArgumentException("openCount must not be negative, was " + openCount);
        }
        // A count that exceeds the band is not a rounding artefact; it is the shape a mis-indexed sweep
        // takes, and on screen it reads as a bracket promising more open ports than it spans — which
        // sends the player probing a range that cannot contain them.
        int span = toSlot - fromSlot + 1;
        if (openCount > span) {
            throw new IllegalArgumentException("openCount " + openCount + " exceeds the " + span
                    + " slots in band " + fromSlot + ".." + toSlot);
        }
    }
}
