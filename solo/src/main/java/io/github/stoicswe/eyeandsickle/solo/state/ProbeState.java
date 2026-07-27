package io.github.stoicswe.eyeandsickle.solo.state;

import java.util.ArrayList;
import java.util.List;

/**
 * One Logic-class probe and what came back — a row of the deduction history.
 *
 * <p>Mastermind feedback, computed the way {@code docs/design/05-hacking-minigame.md} §3.1 needs it
 * to be for the class to be deduction rather than guessing: {@link #exact} counts right-symbol
 * right-position, {@link #partial} counts right-symbol wrong-position with each symbol matched at
 * most once. The classic bug is double-counting a repeated symbol, which makes the feedback
 * inconsistent with itself and quietly destroys the player's ability to reason from it.
 *
 * <p>Every field here is public to the player. Nothing in this class is hidden state, which is why
 * it is snapshotted wholesale while {@code LayerState.secret} never is.
 */
public final class ProbeState {

    /** 1-based, within this layer. The player counts probes; so does {@code P-3}. */
    public int sequence = 0;

    public List<String> guess = new ArrayList<>();

    /** Right symbol in the right position. */
    public int exact = 0;

    /**
     * Right symbol in the wrong position.
     *
     * <p>{@code -1} when the response came from a Fuzzer volley. A volley buys breadth and pays for
     * it in quality ({@code docs/design/06-intrusion-tools.md} §2: "the entry-level 'I don't know
     * the rule, so I'll hammer it' tool"), so it returns {@link #exact} only. Encoded as {@code -1}
     * rather than {@code 0} because "no partial information" and "zero partials" are different
     * facts and a player deducing from the second when the first is true will reach a wrong answer.
     */
    public int partial = 0;

    public boolean volley = false;

    /**
     * Whether this guess was provably impossible given every earlier response.
     *
     * <p>Costs a strike. That penalty is what makes the class deduction with a real error cost
     * ({@code docs/design/05-hacking-minigame.md} §3.3, "how many wrong probes before an
     * alarm/lockout") instead of free enumeration of the keyspace.
     */
    public boolean inconsistent = false;

    public ProbeState() {}
}
