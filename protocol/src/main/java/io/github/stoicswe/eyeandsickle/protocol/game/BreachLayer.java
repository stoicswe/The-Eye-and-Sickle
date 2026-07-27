package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * One layer of a breach: which class it is, how much attention it has left, how close it is to lockout,
 * and — when the player has reached it — the board itself.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1: "a given target composes 1–N layers, each an
 * instance of some class (difficulty tier sets N). Breaching means clearing every layer or bypassing
 * one with the Overflow Kit." §1 constraint 6 makes the structure mandatory rather than convenient.
 *
 * <h2>{@code board} is nullable, and that is not laziness</h2>
 *
 * A {@link LayerOutcome#PENDING} layer's board is not information the player has bought. Sending a
 * tier-3 Logic board's alphabet and keyspace while the player is still working layer 0 hands them free
 * planning time nothing in the design charges for, and sending a Traversal lattice early hands them the
 * shape of a graph they have not walked. A server is entitled to withhold it, and this client is
 * required to cope.
 *
 * <p>There is deliberately <strong>no</strong> cross-field check binding {@code board} to {@code state}
 * — {@link ResolutionRecord} states the precedent and the reason generalises. A producer may withhold a
 * cleared layer's board to keep a snapshot small, or disclose a pending one in a tutorial; neither is a
 * bug, and a constructor that rejected either would be legislating a wire policy from inside a value
 * type. Renderers null-check.
 *
 * <h2>{@code probesUsed} is a measurement and must never become a gate</h2>
 *
 * §4 point 2 is why this field exists: under the turn-based model "the bot-versus-human gap is now a
 * <strong>probe count</strong>, not seconds — a number that can be tested deterministically and tuned,
 * instead of one that varies with the player's hardware and reaction time. That is what makes P-3
 * answerable at all." Counting every {@link BreachActionKind#PROBE} and {@link
 * BreachActionKind#LOUD_TOOL} is how the number gets collected.
 *
 * <p>⚠ It is telemetry, not currency. Invariant I7 says proof-of-skill is tier-gated and
 * <strong>never count-gated</strong>, and a per-layer probe count sitting on the wire is exactly the
 * shape of thing someone later gates on — "clear it in under 8 probes for the efficiency unlock" is one
 * sprint away and would re-create the farming incentive §2.4 was written to close. {@link
 * ResolutionRecord}'s javadoc makes the same point about {@code count(*)}: if code reaches for this
 * number to decide something a player gains, that is the exploit arriving.
 *
 * @param index position in the stack, 0-based; layer 0 is the outermost
 * @param puzzleClass which kind of thinking this layer asks for
 * @param title what the panel calls it, e.g. {@code "LAYER 2 · LOGIC"}
 * @param attention the layer's bar — §4's only currency
 * @param state where the layer stands
 * @param strikes alarms tripped on this layer so far
 * @param strikeLimit how many it tolerates before locking out; §3.3's error-tolerance knob
 * @param probesUsed probing actions spent on this layer — P-3's denominator, and nothing else
 * @param board the playable surface, or {@code null} when the producer has not disclosed it
 */
public record BreachLayer(
        int index,
        PuzzleClass puzzleClass,
        String title,
        AttentionBudget attention,
        LayerOutcome state,
        int strikes,
        int strikeLimit,
        int probesUsed,
        BreachBoard board) {

    public BreachLayer {
        Objects.requireNonNull(puzzleClass, "puzzleClass");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(attention, "attention");
        Objects.requireNonNull(state, "state");

        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative, was " + index);
        }
        if (strikes < 0) {
            throw new IllegalArgumentException("strikes must not be negative, was " + strikes);
        }
        // A layer with no error tolerance at all is not a hard layer, it is a layer that ends on the
        // first wrong move — which §3.3 scales towards but never reaches, and which would make the
        // deduction classes unplayable rather than difficult.
        if (strikeLimit < 1) {
            throw new IllegalArgumentException("strikeLimit must be positive, was " + strikeLimit);
        }
        if (probesUsed < 0) {
            throw new IllegalArgumentException("probesUsed must not be negative, was " + probesUsed);
        }
    }
}
