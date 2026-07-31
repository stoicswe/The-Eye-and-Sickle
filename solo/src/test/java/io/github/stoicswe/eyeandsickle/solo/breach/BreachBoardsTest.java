package io.github.stoicswe.eyeandsickle.solo.breach;

import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.T0;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.focus;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.fullyScanned;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.nodeTarget;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.withNode;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.MatrixBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.OffsetBoard;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Board generation, and the one property that matters more than any of it: a board is solvable and a
 * snapshot never carries the answer.
 *
 * <p>The generation tests are property tests over many seeds rather than golden-value tests over one.
 * A board is a random object with rules, and the rules are what a player learns — a test pinned to a
 * single seed would pass while every other board in the game was wrong.
 *
 * <h2>⚠ The two classes are tested for opposite things, on purpose</h2>
 *
 * {@code BREACH_PROTOCOL} publishes everything and hides nothing, so what has to be proved about it
 * is that a path <em>exists</em> — an open-information puzzle whose goal is unreachable is not hard,
 * it is broken, and nothing on screen would tell the player which. {@code OFFSET_CIPHER} publishes
 * two rows and hides the arithmetic between them, so what has to be proved about it is that the
 * arithmetic has exactly one answer and that no surface leaks it.
 */
class BreachBoardsTest {

    /** Enough seeds that a rule violated one time in fifty would fail the build. */
    private static final int SEEDS = 120;

    private static LayerState generate(long seed, int tier, String puzzleClass) {
        SoloSave save = withNode(seed, tier, 0, false, false);
        // ⚠ Fully scanned, so BOTH classes are reachable. The offset cipher is the default against a
        // machine nothing is known about, and a fixture that skipped this would generate ciphers
        // forever and report "no BREACH_PROTOCOL layer was generated at all" — which is the weighting
        // working, not a broken board. These tests are about what a board IS; which one you draw is
        // BreachPuzzleWeightingTest's subject.
        fullyScanned(save, "10.0.0.5");
        BreachRules.begin(save, nodeTarget(save), T0);
        for (LayerState layer : save.activeBreach.layers) {
            if (puzzleClass.equals(layer.puzzleClass)) {
                return layer;
            }
        }
        return null;
    }

    /** Every layer of every tier, so a property is checked against boards nobody chose. */
    private static List<LayerState> allLayers(String puzzleClass) {
        List<LayerState> found = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            for (int tier = 1; tier <= 5; tier++) {
                LayerState layer = generate(seed, tier, puzzleClass);
                if (layer != null) {
                    found.add(layer);
                }
            }
        }
        assertThat(found).as("no %s layer was generated at all", puzzleClass).isNotEmpty();
        return found;
    }

    @Nested
    @DisplayName("both classes appear")
    class Mix {

        @Test
        @DisplayName("a run of attempts draws both puzzle classes, and only those two")
        void bothClassesAppear() {
            Set<String> seen = new HashSet<>();
            for (long seed = 1; seed <= SEEDS; seed++) {
                SoloSave save = withNode(seed, 3, 0, false, false);
                // Against a fully scanned machine both are on the table — the informed share is 0.95
                // rather than 1.0 precisely so the cipher never stops appearing.
                fullyScanned(save, "10.0.0.5");
                BreachRules.begin(save, nodeTarget(save), T0);
                for (LayerState layer : save.activeBreach.layers) {
                    seen.add(layer.puzzleClass);
                }
            }
            // The whole point of having two: a player who only ever met one would be practising half
            // the skill the proof-of-skill gate (Invariant I7) claims to certify.
            assertThat(seen).containsExactlyInAnyOrder("BREACH_PROTOCOL", "OFFSET_CIPHER");
        }

        @Test
        @DisplayName("the class is frozen at commission, so a reload cannot reroll into the easier one")
        void classIsFrozen() {
            SoloSave save = withNode(4242L, 4, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            List<String> classes =
                    save.activeBreach.layers.stream().map(l -> l.puzzleClass).toList();

            // Re-reading is not re-rolling. Without this, a player who disliked the cipher could quit
            // to menu and come back until the grid turned up — which is choosing your own difficulty.
            BreachSnapshots.of(save);
            BreachSnapshots.of(save);
            assertThat(save.activeBreach.layers.stream().map(l -> l.puzzleClass).toList())
                    .isEqualTo(classes);
        }
    }

    @Nested
    @DisplayName("Breach Protocol grids")
    class Matrix {

        @Test
        @DisplayName("⚠ every goal is reachable — the property an open-information puzzle lives on")
        void everyBoardIsSolvable() {
            for (LayerState layer : allLayers("BREACH_PROTOCOL")) {
                // BoardFactory cuts each sequence out of a walk it actually took, so this must hold
                // for every board it will ever make. If it stops holding, a player is being shown a
                // sequence they can see, can read, and cannot land — with nothing on screen to
                // distinguish that from being bad at the game.
                assertThat(BreachTestKit.matrixPath(layer))
                        .as("a legal walk exists on a %dx%d grid", layer.matrixSize, layer.matrixSize)
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("the buffer can hold the goals but is never generous about it")
        void bufferIsTight() {
            for (LayerState layer : allLayers("BREACH_PROTOCOL")) {
                int longest = layer.matrixGoalLengths.stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0);
                int total = layer.matrixGoalLengths.stream()
                        .mapToInt(Integer::intValue)
                        .sum();
                // The longest sequence must fit, or it is decoration.
                assertThat(layer.matrixBufferSize).isGreaterThanOrEqualTo(longest);
                if (layer.matrixGoalLengths.size() > 1) {
                    // And there must be no room to spare on a multi-goal board: the buffer IS the
                    // difficulty, so taking everything has to mean finding runs that OVERLAP rather
                    // than queueing them one after another.
                    assertThat(layer.matrixBufferSize).isLessThanOrEqualTo(total);
                }
            }
        }

        @Test
        @DisplayName("size, buffer and goal count scale with tier")
        void scalesWithTier() {
            LayerState low = firstOf(1, "BREACH_PROTOCOL");
            LayerState high = firstOf(5, "BREACH_PROTOCOL");
            assertThat(high.matrixSize).isGreaterThan(low.matrixSize);
            assertThat(high.matrixGoalLabels.size()).isGreaterThanOrEqualTo(low.matrixGoalLabels.size());
        }

        @Test
        @DisplayName("a grid is drawn from the published alphabet and starts wholly untaken")
        void gridIsWellFormed() {
            for (LayerState layer : allLayers("BREACH_PROTOCOL")) {
                assertThat(layer.matrixGrid).hasSize(layer.matrixSize * layer.matrixSize);
                assertThat(layer.matrixUsed).hasSize(layer.matrixGrid.size()).allMatch(used -> !used);
                assertThat(layer.matrixBuffer).isEmpty();
                assertThat(layer.matrixGoalSolved).allMatch(solved -> !solved);
            }
        }

        @Test
        @DisplayName("a sequence that restarts mid-buffer still counts — the pointer bug")
        void restartedRunsAreFound() {
            // 1C 1C 55 IS in 1C 1C 1C 55, and a scorer that advanced a pointer on the first two and
            // reset on the third would say it is not. Asserted directly because generating a board
            // that exhibits it is luck, and this is the failure that would look like the player
            // mis-remembering what they picked.
            assertThat(MatrixRules.contains(List.of("1C", "1C", "1C", "55"), List.of("1C", "1C", "55")))
                    .isTrue();
        }

        @Test
        @DisplayName("progress is about the buffer's tail, not its best match anywhere")
        void progressIsTrailing() {
            // A run the player has already walked away from must not read as progress: acting on it
            // means picking towards a sequence that can no longer be completed from here.
            assertThat(MatrixRules.trailingMatch(List.of("1C", "55", "BD"), List.of("1C", "55")))
                    .isZero();
            assertThat(MatrixRules.trailingMatch(List.of("BD", "1C", "55"), List.of("1C", "55")))
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Offset ciphers")
    class Cipher {

        @Test
        @DisplayName("every byte has exactly one offset, and it is the subtraction the player can see")
        void answerIsUnique() {
            for (LayerState layer : allLayers("OFFSET_CIPHER")) {
                for (int i = 0; i < layer.cipherObserved.size(); i++) {
                    int expected = OffsetRules.expected(layer, i);
                    // No wrapping. With wrapping there would be two answers per byte — the short way
                    // and the long way round — and a player who did the arithmetic correctly could
                    // still be told they were wrong, which is the one thing an arithmetic puzzle may
                    // never do.
                    assertThat(layer.cipherObserved.get(i) + expected).isEqualTo(layer.cipherTarget.get(i));
                    assertThat(Math.abs(expected)).isLessThanOrEqualTo(OffsetBoard.MAX_OFFSET);
                }
            }
        }

        @Test
        @DisplayName("both rows are full bytes and the same length")
        void rowsAreWellFormed() {
            for (LayerState layer : allLayers("OFFSET_CIPHER")) {
                assertThat(layer.cipherTarget).hasSameSizeAs(layer.cipherObserved);
                assertThat(layer.cipherEntered).hasSameSizeAs(layer.cipherObserved);
                assertThat(layer.cipherGiven).hasSameSizeAs(layer.cipherObserved);
                // ⚠ Since 2026-07-27 a board can arrive part-solved, so "every cell is null" is no
                // longer the contract. The one that replaced it: a cell holds a value if and only if
                // it was GIVEN, and a given cell holds the right answer. See CipherPrefillTest.
                for (int c = 0; c < layer.cipherObserved.size(); c++) {
                    if (layer.cipherGiven.get(c)) {
                        assertThat(layer.cipherEntered.get(c)).isEqualTo(OffsetRules.expected(layer, c));
                    } else {
                        assertThat(layer.cipherEntered.get(c)).isNull();
                    }
                }
                assertThat(layer.cipherObserved).allMatch(value -> value >= 0 && value <= 255);
                assertThat(layer.cipherTarget).allMatch(value -> value >= 0 && value <= 255);
            }
        }

        @Test
        @DisplayName("length is 6 to 16 bytes and rises with tier")
        void lengthScalesWithTier() {
            for (int tier = 1; tier <= 5; tier++) {
                assertThat(Balance.breachCipherLength(tier)).isBetween(6, 16);
            }
            assertThat(Balance.breachCipherLength(5)).isGreaterThan(Balance.breachCipherLength(1));
        }

        @Test
        @DisplayName("a byte is never already correct — every column is real work")
        void noFreeColumns() {
            int free = 0;
            for (LayerState layer : allLayers("OFFSET_CIPHER")) {
                for (int i = 0; i < layer.cipherObserved.size(); i++) {
                    if (OffsetRules.expected(layer, i) == 0) {
                        free++;
                    }
                }
            }
            // A column whose answer is zero is a column the player can skip on sight, and a board of
            // them would be a puzzle that looks 16 bytes long and is four.
            assertThat(free).isZero();
        }
    }

    @Nested
    @DisplayName("snapshots (D-2)")
    class Snapshots {

        @Test
        @DisplayName("a snapshot never carries the cipher's answer, on any layer, at any tier")
        void snapshotsCarryOnlyRevealedInformation() {
            for (long seed = 1; seed <= 40; seed++) {
                SoloSave save = withNode(seed, 5, 0, false, false);
                BreachRules.begin(save, nodeTarget(save), T0);
                // Force every layer active so both boards are published at once — the worst case for
                // a leak, and the one a normal play-through would never reach.
                for (LayerState layer : save.activeBreach.layers) {
                    layer.state = "ACTIVE";
                }
                String rendered = BreachSnapshots.of(save).toString();

                for (LayerState layer : save.activeBreach.layers) {
                    if (!"OFFSET_CIPHER".equals(layer.puzzleClass)) {
                        continue;
                    }
                    List<String> answer = new ArrayList<>();
                    for (int i = 0; i < layer.cipherObserved.size(); i++) {
                        answer.add(String.valueOf(OffsetRules.expected(layer, i)));
                    }
                    // The whole solution as one run. A single offset can coincide with a byte value
                    // and asserting on one would be flaky; the sequence cannot appear by accident.
                    assertThat(rendered).doesNotContain(String.join(", ", answer));
                }
            }
        }

        @Test
        @DisplayName("an untouched cipher publishes both rows and no offsets")
        void cipherStartsBlank() {
            SoloSave save = withNode(31337L, 3, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "OFFSET_CIPHER");
            if (layer == null) {
                return;
            }

            OffsetBoard board = (OffsetBoard)
                    BreachSnapshots.of(save).active().orElseThrow().board();
            // Both rows are public from the first frame — the puzzle is the arithmetic, not finding
            // out what to subtract. The answer row is empty, and stays the player's to fill.
            assertThat(board.observed()).isNotEmpty();
            assertThat(board.target()).hasSameSizeAs(board.observed());
            // ⚠ "Blank" became "blank except what the board gave you" on 2026-07-27. A cipher can
            // arrive with a few columns solved so a sixteen-byte layer is shorter work than it
            // looks; what has NOT changed is that the rest is the player's, and that no board ever
            // arrives finished.
            int given = 0;
            for (int c = 0; c < board.length(); c++) {
                if (board.isGiven(c)) {
                    given++;
                    assertThat(board.entered().get(c)).isNotNull();
                } else {
                    assertThat(board.entered().get(c)).isNull();
                }
            }
            assertThat(board.filled()).isEqualTo(given);
            assertThat(given).isLessThan(board.length());
            assertThat(board.complete()).isFalse();
            assertThat(board.wrong()).isEmpty();
        }

        @Test
        @DisplayName("a grid publishes everything — there is nothing to withhold")
        void matrixIsFullyPublished() {
            SoloSave save = withNode(777L, 4, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "BREACH_PROTOCOL");
            if (layer == null) {
                return;
            }

            MatrixBoard board = (MatrixBoard)
                    BreachSnapshots.of(save).active().orElseThrow().board();
            assertThat(board.size()).isEqualTo(layer.matrixSize);
            assertThat(board.grid()).hasSize(layer.matrixSize);
            assertThat(board.goals()).isNotEmpty();
            assertThat(board.goals()).allMatch(goal -> !goal.codes().isEmpty());
            // ⚠ And it must publish no MORE than that: no reachable-goal count, no suggested cell.
            // Working out where the path goes is the entire game.
            assertThat(board.bufferRemaining()).isEqualTo(board.bufferSize());
        }
    }

    @Nested
    @DisplayName("the persisted RNG (D-4)")
    class Randomness {

        @Test
        @DisplayName("the same seed produces the same boards")
        void generationIsDeterministic() {
            SoloSave a = withNode(99L, 5, 0, false, false);
            SoloSave b = withNode(99L, 5, 0, false, false);
            BreachRules.begin(a, nodeTarget(a), T0);
            BreachRules.begin(b, nodeTarget(b), T0);

            for (int i = 0; i < a.activeBreach.layers.size(); i++) {
                LayerState la = a.activeBreach.layers.get(i);
                LayerState lb = b.activeBreach.layers.get(i);
                assertThat(la.puzzleClass).isEqualTo(lb.puzzleClass);
                assertThat(la.matrixGrid).isEqualTo(lb.matrixGrid);
                assertThat(la.matrixGoalCodes).isEqualTo(lb.matrixGoalCodes);
                assertThat(la.cipherObserved).isEqualTo(lb.cipherObserved);
                assertThat(la.cipherTarget).isEqualTo(lb.cipherTarget);
            }
        }

        @Test
        @DisplayName("⚠ opening a breach commits the advanced seed, so a reload cannot reroll it")
        void beginCommitsTheSeed() {
            SoloSave save = withNode(99L, 5, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            // The single most important correctness property in this lane. Without the commit the
            // save still holds the seed the draws started from, and reloading rerolls the board a
            // player did not like — which makes generation advisory rather than committed.
            assertThat(save.rngSeed).isNotEqualTo(99L);
        }

        @Test
        @DisplayName("reading a snapshot draws nothing and regenerates nothing")
        void snapshotsAreSideEffectFree() {
            SoloSave save = withNode(808L, 5, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            long seed = save.rngSeed;
            List<String> grid = List.copyOf(save.activeBreach.layers.getFirst().matrixGrid);
            List<Integer> observed = List.copyOf(save.activeBreach.layers.getFirst().cipherObserved);

            BreachSnapshots.of(save);
            BreachSnapshots.of(save);

            assertThat(save.rngSeed).isEqualTo(seed);
            assertThat(save.activeBreach.layers.getFirst().matrixGrid).isEqualTo(grid);
            assertThat(save.activeBreach.layers.getFirst().cipherObserved).isEqualTo(observed);
        }

        @Test
        @DisplayName("a derived seed depends on the character, not on an ambient clock read")
        void seedsAreDerivedFromInputs() {
            assertThat(Rng.derive("abc", T0)).isEqualTo(Rng.derive("abc", T0));
            assertThat(Rng.derive("abc", T0)).isNotEqualTo(Rng.derive("abd", T0));
            assertThat(Rng.derive("abc", T0)).isNotEqualTo(Rng.derive("abc", T0.plusSeconds(1)));
        }

        @Test
        @DisplayName("nextInt stays in range and is roughly uniform")
        void nextIntIsSane() {
            Rng rng = new Rng(1234L);
            int[] buckets = new int[7];
            for (int i = 0; i < 70_000; i++) {
                int value = rng.nextInt(buckets.length);
                assertThat(value).isBetween(0, buckets.length - 1);
                buckets[value]++;
            }
            for (int count : buckets) {
                assertThat(count).isBetween(9_000, 11_000);
            }
        }
    }

    /** The first layer of the given class at the given tier, across seeds. */
    private static LayerState firstOf(int tier, String puzzleClass) {
        for (long seed = 1; seed <= SEEDS; seed++) {
            LayerState layer = generate(seed, tier, puzzleClass);
            if (layer != null) {
                return layer;
            }
        }
        throw new AssertionError("no " + puzzleClass + " layer at tier " + tier);
    }
}
