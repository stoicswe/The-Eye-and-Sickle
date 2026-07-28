package io.github.stoicswe.eyeandsickle.solo.state;

import java.util.ArrayList;
import java.util.List;

/**
 * One layer of a breach: its budget, its damage, and its board.
 *
 * <h2>Three classes, one class per instance, all three shapes in one file</h2>
 *
 * A layer is an instance of exactly one puzzle class ({@code docs/design/05-hacking-minigame.md}
 * §3.1), so at most one of the three field groups below is populated. That is a union hand-rolled
 * as a flat record rather than a sealed hierarchy, and the reason is the save format: this is a JSON
 * document that outlives the code which wrote it, and polymorphic deserialisation needs a type
 * discriminator that a hand-edited save can get wrong in a way an unused-fields layout cannot. The
 * unused groups serialise as empty lists and zeroes and cost nothing.
 *
 * <h2>⚠ Three fields are the answer and must never leave this object</h2>
 *
 * {@link #secret}, {@link #factDeck} and {@link #objectiveNodeId} are the puzzle. {@code
 * BreachSnapshots} does not read them at all — not "filters them", <em>does not read them</em> — so
 * that a hidden field cannot be leaked by a snapshot builder that forgot a branch. A unit test
 * asserts the secret appears nowhere in any snapshot; see {@code BreachSnapshotsTest}.
 *
 * <p>It would be easy to argue this does not matter in a save file the player can edit. It matters
 * because the same records go over a wire to a real home server, where the client is never
 * authoritative (Invariant I14), and because a puzzle whose answer is one careless field copy away
 * from the renderer is not a puzzle anyone can trust.
 */
public final class LayerState {

    public int index = 0;

    /** Which minigame this layer is running: {@code BREACH_PROTOCOL} or {@code OFFSET_CIPHER}. */
    public String puzzleClass = "BREACH_PROTOCOL";

    /** Attention this layer grants. Spending it is the whole clock this puzzle has. */
    public int budget = 20;

    public int spent = 0;

    public int strikes = 0;

    public int strikeLimit = 3;

    /** Moves made that asked the board a question. Bookkeeping never counts. */
    public int probesUsed = 0;

    /** {@code PENDING}, {@code ACTIVE}, {@code CLEARED} or {@code FAILED}. */
    public String state = "PENDING";

    /** What the layer is called on screen. */
    public String title = "";

    // ── BREACH_PROTOCOL ───────────────────────────────────────────────────────────────────────
    //
    // ⚠ The grid and the used mask are FLAT lists indexed `row * matrixSize + column`, not lists of
    // lists. Jackson round-trips a List<String> without help and a List<List<String>> only with a
    // type token it has no reason to have here; a save that deserialised as a list of LinkedHashMaps
    // would fail at the first cast, at load, on somebody's real character. Flat costs one
    // multiplication at every access and cannot do that.

    /** Two-character hex codes, row-major. */
    public List<String> matrixGrid = new ArrayList<>();

    /** Which cells have been taken. Same length and same indexing as {@link #matrixGrid}. */
    public List<Boolean> matrixUsed = new ArrayList<>();

    /** The side of the square. */
    public int matrixSize = 5;

    /** Codes taken so far, in order. Cannot be emptied — that is the puzzle. */
    public List<String> matrixBuffer = new ArrayList<>();

    /** How many picks the attempt gets in total. */
    public int matrixBufferSize = 6;

    /** True when the next pick must be in {@link #matrixCursorRow}, false for the column. */
    public boolean matrixRowTurn = true;

    public int matrixCursorRow = 0;

    public int matrixCursorColumn = 0;

    /** One label per goal. The three goal lists are parallel and must stay the same length. */
    public List<String> matrixGoalLabels = new ArrayList<>();

    /**
     * Every goal's codes, concatenated.
     *
     * <p>⚠ Flat, with {@link #matrixGoalLengths} carrying the split, for the same reason the grid is:
     * a nested list is a deserialisation hazard in a file that outlives the code that wrote it.
     * {@code MatrixRules.goalCodes} is the only thing that should ever slice it.
     */
    public List<String> matrixGoalCodes = new ArrayList<>();

    public List<Integer> matrixGoalLengths = new ArrayList<>();

    public List<Boolean> matrixGoalSolved = new ArrayList<>();

    public List<Integer> matrixGoalMatched = new ArrayList<>();

    public List<Integer> matrixGoalRewards = new ArrayList<>();

    // ── OFFSET_CIPHER ─────────────────────────────────────────────────────────────────────────

    /** The bytes read off the wire, {@code 0x00}–{@code 0xFF}. Shown to the player. */
    public List<Integer> cipherObserved = new ArrayList<>();

    /** The bytes the far end expects. ⚠ Also shown — the arithmetic is the game, not the secret. */
    public List<Integer> cipherTarget = new ArrayList<>();

    /**
     * What the player has typed under each cell.
     *
     * <p>⚠ Holds nulls, and must. {@code 0} is a legitimate offset — a cell where observed and target
     * already agree — so a sentinel value cannot mean "untouched" without also meaning a real answer.
     */
    public List<Integer> cipherEntered = new ArrayList<>();

    /**
     * Which cells arrived already solved, and are therefore not the player's to edit.
     *
     * <p>⚠ Locked rather than merely pre-typed. A given cell the player could overwrite is a trap
     * dressed as a favour: they would have no way to tell their own answer from the board's, and a
     * stray keystroke on a correct column would cost a strike on commit. Locking also makes the give
     * worth more than the keystrokes it saves — it is a column that does not need CHECKING.
     */
    public List<Boolean> cipherGiven = new ArrayList<>();

    /** Cells the last commit rejected. Cleared whenever the player edits. */
    public List<Integer> cipherWrong = new ArrayList<>();

    /** Which cell typing goes into. */
    public int cipherCursor = 0;

    /** How many times the row has been submitted. Each failure cost a strike. */
    public int cipherCommits = 0;
}
