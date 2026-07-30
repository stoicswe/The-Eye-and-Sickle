package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.BreachState;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the boards an attempt will be played on, once, at commission.
 *
 * <h2>⚠ Every draw happens here and is then frozen into the save</h2>
 *
 * {@code docs/design/16-breach-implementation.md} §2: a board decided at commission is a board a
 * reload cannot re-roll. Nothing downstream draws — {@code MatrixRules} and {@code OffsetRules} take
 * no {@code Rng} at all, which is what makes that structural rather than a rule someone remembers.
 *
 * <h2>Which puzzle, and why it is one roll for the whole attempt</h2>
 *
 * The class is drawn once and every layer of the attempt plays it. A target that opened with a
 * protocol grid and followed with a cipher would be two short games rather than one, and would make
 * the deeper layers of a hard target a lottery between "the thing I am good at" and "the thing I am
 * not" — which turns a difficulty tier into a coin flip. One roll per attempt means a player who
 * draws the puzzle they are worse at knows it before they spend anything, and can walk away.
 *
 * <h2>⚠ The roll is weighted by what the player knows about the machine</h2>
 *
 * The <b>offset cipher is the default</b>: it is the puzzle that needs nothing from the far side,
 * because deriving an offset from ciphertext is what you do when you have no other handle. Breach
 * Protocol is the puzzle of someone who already knows the host, so the odds of drawing it rise with
 * how much of the port-scan report is filled in — from {@code BREACH_PROTOCOL_SHARE} against a
 * machine nobody has looked at to {@code BREACH_PROTOCOL_SHARE_INFORMED} against a fully scanned one.
 *
 * <p>That is the whole of RECON's mechanical consequence. Before this, a report was intelligence the
 * player read and acted on by hand; now it changes what the breach <em>is</em>. ⚠ It buys a
 * <b>different</b> puzzle rather than an easier one — nothing about the tier, the attention budget,
 * the strike limit or the layer count moves — so it must not become a discount if either puzzle is
 * ever re-tuned.
 */
public final class BoardFactory {

    private BoardFactory() {}

    /**
     * The code alphabet for a protocol grid.
     *
     * <p>Six codes, deliberately. Fewer makes accidental runs so common that a sequence completes
     * itself; more makes a run vanishingly unlikely to appear along any legal path, and the puzzle
     * stops being solvable by planning. These are the values the genre uses and they are chosen to be
     * visually distinct at a glance — no two share a first character.
     */
    private static final List<String> CODES = List.of("1C", "55", "7A", "BD", "E9", "FF");

    private static final List<String> GOAL_LABELS =
            List.of("BASIC DATAMINE", "ADVANCED DATAMINE", "MASTER DATAMINE");

    /**
     * Builds every layer and its board. Called once, from {@code BreachRules.begin}.
     *
     * @param known how complete the target's port-scan report is, {@code 0} to {@code 1}. Zero for a
     *     machine nothing has been learned about, and for the tutorial miner crack — which has no
     *     address to hold a report and correctly gets the default puzzle
     */
    public static void build(BreachState breach, Rng rng, double known) {
        int tier = breach.difficultyTier;
        // ⚠ One draw, unconditionally, whatever the weight — including at a weight of zero. Rng's
        // contract is that consumption must not depend on what was produced or on the inputs, or a
        // stored seed stops being a replay; skipping the roll for an unscanned machine would make
        // every later draw in the breach depend on whether the player had scanned it.
        PuzzleClass puzzle = rng.nextDouble() < Balance.breachProtocolShare(known)
                ? PuzzleClass.BREACH_PROTOCOL
                : PuzzleClass.OFFSET_CIPHER;
        breach.puzzleClass = puzzle.name();

        for (int i = 0; i < Balance.breachLayers(tier); i++) {
            LayerState layer = new LayerState();
            layer.index = i;
            layer.puzzleClass = puzzle.name();
            layer.budget = budgetFor(tier, breach.targetFirewallTier);
            layer.strikeLimit = Balance.breachStrikeLimit(tier);
            layer.state = i == 0 ? "ACTIVE" : "PENDING";
            if (puzzle == PuzzleClass.BREACH_PROTOCOL) {
                buildMatrix(layer, tier, rng);
            } else {
                buildCipher(layer, tier, rng);
            }
            breach.layers.add(layer);
        }
    }

    /**
     * A layer's attention budget after the Firewall penalty, floored.
     *
     * <p>The floor is not defensive programming; it is a design rule. A budget driven low enough
     * that no sequence of legal moves clears the layer is the game deciding, which is the one
     * reading {@code docs/design/05-hacking-minigame.md} §1 constraint 4 forbids outright.
     */
    static int budgetFor(int tier, int firewallTier) {
        int penalty = Balance.FIREWALL_BUDGET_PENALTY_PER_TIER * Math.max(0, firewallTier);
        return Math.max(Balance.BREACH_ATTENTION_FLOOR, Balance.breachAttention(tier) - penalty);
    }

    // ================================================================== breach protocol

    private static void buildMatrix(LayerState layer, int tier, Rng rng) {
        int size = Balance.breachMatrixSize(tier);
        layer.title = "PROTOCOL " + size + "x" + size;
        layer.matrixSize = size;
        layer.matrixBufferSize = Balance.breachBufferSize(tier);
        layer.matrixRowTurn = true;
        layer.matrixCursorRow = 0;
        layer.matrixCursorColumn = 0;
        layer.matrixBuffer = new ArrayList<>();

        layer.matrixGrid = new ArrayList<>();
        layer.matrixUsed = new ArrayList<>();
        for (int cell = 0; cell < size * size; cell++) {
            layer.matrixGrid.add(CODES.get(rng.nextInt(CODES.size())));
            layer.matrixUsed.add(false);
        }

        layer.matrixGoalLabels = new ArrayList<>();
        layer.matrixGoalCodes = new ArrayList<>();
        layer.matrixGoalLengths = new ArrayList<>();
        layer.matrixGoalSolved = new ArrayList<>();
        layer.matrixGoalMatched = new ArrayList<>();
        layer.matrixGoalRewards = new ArrayList<>();

        int goals = Balance.breachGoalCount(tier);
        for (int goal = 0; goal < goals; goal++) {
            // ⚠ Cut out of a REAL legal path rather than generated at random.
            //
            // A random sequence is very often unreachable: the path alternates row and column, so a
            // run that never appears along any legal walk is a goal the player cannot take however
            // well they play — and they cannot tell which kind they are looking at. Walking the grid
            // first and slicing the goal out of the walk guarantees at least one solution exists,
            // which is the difference between a hard puzzle and an unfair one.
            List<String> walk = legalWalk(layer, rng);
            int length = Math.min(Balance.breachGoalLength(tier, goal), walk.size());
            int from = walk.size() == length ? 0 : rng.nextInt(walk.size() - length + 1);

            layer.matrixGoalLabels.add(GOAL_LABELS.get(Math.min(goal, GOAL_LABELS.size() - 1)));
            layer.matrixGoalCodes.addAll(walk.subList(from, from + length));
            layer.matrixGoalLengths.add(length);
            layer.matrixGoalSolved.add(false);
            layer.matrixGoalMatched.add(0);
            layer.matrixGoalRewards.add(goal + 1);
        }
    }

    /**
     * Walks the grid the way a player would, and returns the codes it passed through.
     *
     * <p>⚠ Draws a fixed number of times whatever it finds, because {@code Rng}'s contract is that a
     * generator whose consumption depends on what it produced makes a replay from a stored seed stop
     * being a replay. Every step draws once; a step with nowhere legal to go takes the draw and
     * stops.
     */
    private static List<String> legalWalk(LayerState layer, Rng rng) {
        int size = layer.matrixSize;
        boolean[] taken = new boolean[size * size];
        List<String> codes = new ArrayList<>();
        boolean rowTurn = true;
        int row = 0;
        int column = 0;

        for (int step = 0; step < layer.matrixBufferSize; step++) {
            List<Integer> options = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                int candidate = rowTurn ? row * size + i : i * size + column;
                if (!taken[candidate]) {
                    options.add(candidate);
                }
            }
            int pick = rng.nextInt(Math.max(1, options.size()));
            if (options.isEmpty()) {
                break;
            }
            int cell = options.get(pick);
            taken[cell] = true;
            codes.add(layer.matrixGrid.get(cell));
            row = cell / size;
            column = cell % size;
            rowTurn = !rowTurn;
        }
        return codes;
    }

    // ================================================================== offset cipher

    private static void buildCipher(LayerState layer, int tier, Rng rng) {
        int length = Balance.breachCipherLength(tier);
        layer.title = "CIPHER " + length + " BYTES";
        layer.cipherObserved = new ArrayList<>();
        layer.cipherTarget = new ArrayList<>();
        layer.cipherEntered = new ArrayList<>();
        layer.cipherWrong = new ArrayList<>();
        layer.cipherCursor = 0;
        layer.cipherCommits = 0;

        for (int cell = 0; cell < length; cell++) {
            int observed = rng.nextInt(256);
            // ⚠ The target is the observed byte plus a NON-ZERO step, not a second free draw.
            //
            // Two independent draws collide about once every 256 cells, and a column whose answer is
            // zero is a column the player skips on sight — a board of them looks sixteen bytes long
            // and is four. The step is 1..255 and wraps, so every column is real work and the offset
            // still lands inside the +/-255 the board publishes.
            int step = 1 + rng.nextInt(255);
            layer.cipherObserved.add(observed);
            layer.cipherTarget.add((observed + step) % 256);
            layer.cipherEntered.add(null);
            layer.cipherGiven.add(false);
        }

        prefill(layer, length, rng);
    }

    /**
     * Hands the player a few solved columns, so a long board is shorter work than it looks.
     *
     * <h2>⚠ EVERY draw here is unconditional, and that is the whole shape of this method</h2>
     *
     * {@code Rng}'s contract is that the number of values consumed must not depend on the values
     * produced — it is why {@code nextInt} has no rejection loop, and it is what makes a stored seed
     * a faithful replay rather than a replay of one code path. The obvious spelling of this method,
     * {@code if (roll < chance) { draw more }}, breaks it: two boards that differed only in the
     * first roll would consume different amounts of stream and every later draw in the breach would
     * diverge.
     *
     * <p>So all five decisions are drawn every time and only then read. The cost is four wasted
     * longs per board; the alternative is a generator whose stream shape depends on its own output.
     *
     * <p>See {@code Balance.CIPHER_PREFILL_CHANCE} for the odds and
     * {@code Balance.cipherPrefillCap} for why a sixth of a short board is not the same gift as a
     * third of a long one.
     */
    private static void prefill(LayerState layer, int length, Rng rng) {
        double baseRoll = rng.nextDouble();
        int baseCount = 1 + rng.nextInt(Balance.CIPHER_PREFILL_BASE_MAX);
        double bonusRoll = rng.nextDouble();
        int bonusCount = 1 + rng.nextInt(Balance.CIPHER_PREFILL_BONUS_MAX);

        // ⚠ Drawn to the CEILING, not to `given`. Picking only as many cells as were wanted would
        // make the consumption depend on the rolls above, which is the thing this method exists to
        // avoid. Surplus picks are drawn and discarded.
        int[] picks = new int[Balance.CIPHER_PREFILL_CEILING];
        for (int i = 0; i < picks.length; i++) {
            picks[i] = rng.nextInt(Math.max(1, length));
        }

        int given = 0;
        if (baseRoll < Balance.CIPHER_PREFILL_CHANCE) {
            given = baseCount;
            if (bonusRoll < Balance.CIPHER_PREFILL_BONUS_CHANCE) {
                given += bonusCount;
            }
        }
        given = Math.min(given, Balance.cipherPrefillCap(length));

        // ⚠ Distinct cells, and the loop counts what it FILLED rather than what it tried. Picks
        // collide — five draws over a six-cell board very often name the same column twice — and
        // counting attempts would quietly hand out fewer columns than the odds above promise.
        int filled = 0;
        for (int pick : picks) {
            if (filled >= given) {
                break;
            }
            if (!layer.cipherGiven.get(pick)) {
                layer.cipherGiven.set(pick, true);
                layer.cipherEntered.set(pick, layer.cipherTarget.get(pick) - layer.cipherObserved.get(pick));
                filled++;
            }
        }

        // The cursor starts on something the player can actually type into. Landing it on a locked
        // column would make the first keystroke do nothing, which reads as a broken board.
        for (int cell = 0; cell < length; cell++) {
            if (!layer.cipherGiven.get(cell)) {
                layer.cipherCursor = cell;
                break;
            }
        }
    }
}
