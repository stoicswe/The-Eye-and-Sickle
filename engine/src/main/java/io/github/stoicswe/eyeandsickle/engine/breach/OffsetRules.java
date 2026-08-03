package io.github.stoicswe.eyeandsickle.engine.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.OffsetBoard;
import io.github.stoicswe.eyeandsickle.engine.state.LayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The offset cipher: subtract two rows of hex, get every cell right, commit once.
 *
 * <h2>Nothing is hidden, and that is the design</h2>
 *
 * Both rows are on the board and the answer to cell {@code i} is {@code target[i] − observed[i]}.
 * There is no probe here, nothing to deduce and no information to buy — this class exists to ask
 * whether the player can do sixteen hex subtractions <em>correctly</em>, once, when a mistake costs a
 * strike. Hex arithmetic with borrows is a real skill the curriculum already teaches
 * ({@code docs/education/01-foundations.md}), and this is the one place the game asks for it directly.
 *
 * <h2>⚠ There is no clock, and there must never be one</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4 removed the wall clock from the breach, and this is
 * the class most tempting to put it back into — "arithmetic under pressure" sounds like it wants a
 * countdown. It does not. A timer would test how fast a player subtracts rather than whether they
 * can, would be unplayable for anyone who reads slowly, and would re-open a decision §4 already made.
 *
 * <p>What it pays instead is <b>noise</b>. Sitting on somebody else's wire working through sixteen
 * bytes radiates for as long as it takes, so this class is louder on average than Breach Protocol —
 * patience costs exposure rather than time. The weighting is in {@code Balance}, not here.
 *
 * <h2>Three moves and no more</h2>
 *
 * <ul>
 *   <li>{@code type} — bookkeeping. Never charged, never ledgered, and reversible until committed.
 *       Composing an answer is not a move ({@code 05} §3.7).
 *   <li>{@code commit} — the move. Charges attention, checks every cell, and costs a strike if any
 *       cell is wrong. It reports <em>how many</em> and <em>which</em> cells are wrong, and never
 *       what they should have been: naming the right answer would make the second attempt free and
 *       the arithmetic pointless.
 *   <li>{@code carry} — the assist. Fills one unsolved cell correctly, for real attention. The
 *       escape hatch for a player who has lost the thread on one byte, priced so that using it on
 *       every cell costs more than the layer is worth.
 * </ul>
 */
public final class OffsetRules {

    private OffsetRules() {}

    public static final String TYPE = "type";
    public static final String COMMIT = "commit";
    public static final String CARRY = "carry";

    /** Whether an action id is composition rather than a move — never charged, never ledgered. */
    public static boolean isBookkeeping(String actionId) {
        return TYPE.equals(actionId);
    }

    public static Move act(LayerState layer, String actionId, String argument) {
        return switch (actionId == null ? "" : actionId) {
            case TYPE -> type(layer, argument);
            case COMMIT -> commit(layer);
            case CARRY -> carry(layer, argument);
            default -> Move.refunded("that move does nothing on a cipher - type an offset and commit");
        };
    }

    // ================================================================== composition

    /**
     * Writes an offset into a cell, or clears one.
     *
     * @param argument {@code "index:value"}, or {@code "index:"} to clear the cell
     */
    private static Move type(LayerState layer, String argument) {
        String[] parts = (argument == null ? "" : argument).split(":", -1);
        if (parts.length != 2) {
            return Move.refunded("type takes index:value");
        }
        int index;
        try {
            index = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException notANumber) {
            return Move.refunded("type takes index:value");
        }
        if (index < 0 || index >= layer.cipherObserved.size()) {
            return Move.refunded("no such cell");
        }
        // ⚠ A given column is not the player's to change, and refusing costs them nothing —
        // Move.refunded does not spend attention. A board arrives with some columns already solved
        // (Balance.CIPHER_PREFILL_CHANCE); letting a stray keystroke overwrite one would turn the
        // favour into a trap, because the wrong value would not be found until the commit that
        // costs a strike.
        if (isGiven(layer, index)) {
            return Move.refunded("cell " + index + " came already solved - it is not yours to change");
        }

        String raw = parts[1].trim();
        if (raw.isEmpty()) {
            layer.cipherEntered.set(index, null);
            // ⚠ Editing clears the last commit's marks. Leaving them would show the player red cells
            // that no longer describe what is on the board, which is worse than showing nothing: it
            // says "this is still wrong" about an answer they have just changed.
            layer.cipherWrong.clear();
            return Move.bookkeeping("cleared cell " + index);
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            return Move.refunded("an offset is a signed decimal, like -9 or 14");
        }
        if (value < -OffsetBoard.MAX_OFFSET || value > OffsetBoard.MAX_OFFSET) {
            return Move.refunded("an offset is between -" + OffsetBoard.MAX_OFFSET + " and +" + OffsetBoard.MAX_OFFSET);
        }
        layer.cipherEntered.set(index, value);
        layer.cipherWrong.clear();
        return Move.bookkeeping("cell " + index + " = " + (value < 0 ? "" : "+") + value);
    }

    // ================================================================== the move

    /**
     * Submits the whole row.
     *
     * <p>⚠ Reports the count and the positions, never the values. A commit that named the correct
     * offsets would hand the player the answer for the price of one attention and turn a puzzle about
     * arithmetic into a puzzle about pressing commit twice.
     */
    private static Move commit(LayerState layer) {
        layer.cipherCommits++;
        List<Integer> wrong = new ArrayList<>();
        int blank = 0;
        for (int i = 0; i < layer.cipherObserved.size(); i++) {
            Integer entered = layer.cipherEntered.get(i);
            if (entered == null) {
                blank++;
                wrong.add(i);
            } else if (entered != expected(layer, i)) {
                wrong.add(i);
            }
        }
        layer.cipherWrong.clear();
        layer.cipherWrong.addAll(wrong);

        if (wrong.isEmpty()) {
            return Move.cleared("the sequence resolves - every offset lands");
        }
        if (blank == wrong.size() && blank > 0) {
            // Nothing was actually wrong, the row was merely unfinished. Refunded, because a commit
            // the player plainly did not mean to make is a slip rather than a decision, and charging
            // a strike for it would punish the keystroke rather than the arithmetic.
            return Move.refunded(blank + (blank == 1 ? " cell is" : " cells are") + " still empty");
        }
        return Move.strike(
                wrong.size() + (wrong.size() == 1 ? " offset is wrong: " : " offsets are wrong: ") + positions(wrong));
    }

    /**
     * Fills one cell correctly, for attention.
     *
     * @param argument the cell index, or empty for the first unsolved one
     */
    private static Move carry(LayerState layer, String argument) {
        int index = -1;
        String raw = argument == null ? "" : argument.trim();
        if (!raw.isEmpty()) {
            try {
                index = Integer.parseInt(raw);
            } catch (NumberFormatException notANumber) {
                return Move.refunded("carry takes a cell index, or nothing for the first unsolved one");
            }
            if (index < 0 || index >= layer.cipherObserved.size()) {
                return Move.refunded("no such cell");
            }
        } else {
            for (int i = 0; i < layer.cipherObserved.size() && index < 0; i++) {
                Integer entered = layer.cipherEntered.get(i);
                if (entered == null || entered != expected(layer, i)) {
                    index = i;
                }
            }
        }
        if (index < 0) {
            return Move.refunded("every cell already holds the right offset - commit it");
        }
        layer.cipherEntered.set(index, expected(layer, index));
        layer.cipherWrong.remove(Integer.valueOf(index));
        return Move.of("carried cell " + index);
    }

    // ================================================================== arithmetic

    /**
     * The one right answer for a cell.
     *
     * <p>Plain signed subtraction with <b>no wrapping</b>, which is what makes the answer unique. A
     * modular offset would make {@code -9} and {@code +247} both correct for the same cell, and a
     * puzzle with two right answers cannot tell a player they got it wrong.
     */
    public static int expected(LayerState layer, int index) {
        return layer.cipherTarget.get(index) - layer.cipherObserved.get(index);
    }

    /**
     * Whether this column arrived already solved.
     *
     * <p>⚠ Tolerates a short or absent list rather than indexing blind. {@code cipherGiven} was
     * added on 2026-07-27 and a breach saved before that has none — an in-flight board on an older
     * save must keep working, and "nothing was given" is the true answer for one.
     */
    public static boolean isGiven(LayerState layer, int index) {
        return layer.cipherGiven != null
                && index >= 0
                && index < layer.cipherGiven.size()
                && Boolean.TRUE.equals(layer.cipherGiven.get(index));
    }

    /** {@code 03, 07 and 11} — positions, in the player's own one-based counting. */
    private static String positions(List<Integer> wrong) {
        List<String> text = new ArrayList<>();
        for (Integer index : wrong) {
            text.add(String.format(Locale.ROOT, "%02d", index + 1));
        }
        if (text.size() == 1) {
            return text.getFirst();
        }
        return String.join(", ", text.subList(0, text.size() - 1)) + " and " + text.getLast();
    }
}
