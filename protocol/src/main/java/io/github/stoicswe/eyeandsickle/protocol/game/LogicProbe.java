package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;

/**
 * One guess at a Logic layer's code and what the lock said back.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1 gives the Logic class as "reconstruct a lock's
 * rule from probe responses", skill tested: deductive reasoning, Mastermind-family. This record is one
 * row of that history, and the history <em>is</em> the puzzle — §3.2 names Logic's human-read step as
 * "probe responses a player interprets holistically", which is only possible if every response the
 * player has ever received stays legible side by side.
 *
 * <h2>The feedback rule, stated once so nobody re-derives it wrong</h2>
 *
 * {@code exact} counts positions where the guessed symbol equals the secret's. {@code partial} counts
 * symbols present in both but out of place, computed as the multiset overlap minus {@code exact}:
 *
 * <pre>{@code
 * exact   = |{ i : guess[i] == secret[i] }|
 * matched = Σ over symbols s of min(count(guess, s), count(secret, s))
 * partial = matched - exact
 * }</pre>
 *
 * The bug this shape exists to prevent is the classic one — counting a repeated symbol once per
 * occurrence in the guess rather than capping at the number in the secret, so that {@code @@@@} against
 * a code containing one {@code @} reports four partials and the player deduces something false. The
 * arithmetic is the engine's; this record only carries the answer. It is written down here because a
 * second implementation on the server will have to agree with the first, and "agree" means agreeing on
 * this exact definition.
 *
 * <h2>Why {@code inconsistent} may be published when the secret may not</h2>
 *
 * A guess is inconsistent when it is provably impossible given the player's own prior responses —
 * nothing satisfying every earlier {@code (guess, exact, partial)} triple could equal it. That is a
 * function of information the player already holds, so saying it aloud leaks nothing; it is a
 * restatement of their own history, and doing the bookkeeping for them is the difference between the
 * class being deduction and the class being guessing. The engine charges it a strike, which is what
 * makes the distinction cost something.
 *
 * @param sequence 1-based within the layer, so the history reads as an ordered argument
 * @param guess one symbol per position, in position order
 * @param exact right symbol in the right place
 * @param partial right symbol in the wrong place, multiset-capped and never double counted;
 *     {@code -1} exactly when this response came from a volley
 * @param volley whether this came from a Fuzzer volley, which buys breadth by returning only
 *     {@code exact} ({@code docs/design/06-intrusion-tools.md} §2: the loud counter to the Logic class)
 * @param inconsistent whether the guess was provably impossible given prior feedback
 */
public record LogicProbe(
        int sequence, List<String> guess, int exact, int partial, boolean volley, boolean inconsistent) {

    public LogicProbe {
        guess = List.copyOf(guess);

        if (sequence < 1) {
            throw new IllegalArgumentException("sequence is 1-based within a layer, was " + sequence);
        }
        if (exact < 0) {
            throw new IllegalArgumentException("exact must not be negative, was " + exact);
        }
        if (partial < -1) {
            throw new IllegalArgumentException("partial is a count, or -1 for a withheld volley response, was "
                    + partial);
        }
        if (exact > guess.size()) {
            throw new IllegalArgumentException(
                    "exact " + exact + " exceeds the " + guess.size() + " positions guessed");
        }
        // Both markers must agree. A volley row rendering a partial count would be inventing precision
        // the Fuzzer explicitly does not buy, and an ordinary probe carrying -1 would be read as "zero
        // out of place" by anything that does arithmetic on the column.
        boolean withheld = partial == -1;
        if (volley != withheld) {
            throw new IllegalArgumentException("A volley response withholds partial (-1) and an ordinary probe "
                    + "reports it; got volley=" + volley + ", partial=" + partial);
        }
        if (!volley && exact + partial > guess.size()) {
            throw new IllegalArgumentException("exact + partial (" + (exact + partial) + ") exceeds the "
                    + guess.size() + " positions guessed — the multiset overlap cannot be larger than the code");
        }
    }
}
