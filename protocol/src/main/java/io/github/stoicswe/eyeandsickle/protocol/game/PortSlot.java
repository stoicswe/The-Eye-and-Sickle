package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * One slot of an Enumeration board, as far as the player has established it.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1 gives the Enumeration class as "map a node's open
 * ports/services before you can act", tested skill: reading structure. A board is a row of these, and
 * a probe turns exactly one of them from {@link PortState#UNKNOWN} into an answer.
 *
 * <h2>The service name is a second purchase, and the constructor enforces it</h2>
 *
 * A slot that is not {@link PortState#OPEN} carries no service, ever. That is the D-2 boundary drawn
 * where it is cheapest to hold: a closed or filtered slot naming what <em>would</em> have been there
 * hands the player the shape of the board for free, and a slot still reading {@code UNKNOWN} while
 * naming a service is a straight leak of the answer.
 *
 * <p>The check is here, in the wire type, rather than in the engine that populates it, because this is
 * the one place every producer must pass through — a solo rules engine today, a home server later. A
 * leak guard that lives beside the leak is a leak guard that survives the second implementation.
 *
 * @param index position in the board, 0-based, matching the numbers the player reads on screen
 * @param state what the player has established about this slot
 * @param service what is listening, once the player has revealed it; {@code ""} in every other case
 */
public record PortSlot(int index, PortState state, String service) {

    public PortSlot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(service, "service");

        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative, was " + index);
        }
        if (state != PortState.OPEN && !service.isEmpty()) {
            throw new IllegalArgumentException("Only an OPEN slot names a service; slot " + index + " was " + state
                    + " and named \"" + service + "\"");
        }
    }
}
