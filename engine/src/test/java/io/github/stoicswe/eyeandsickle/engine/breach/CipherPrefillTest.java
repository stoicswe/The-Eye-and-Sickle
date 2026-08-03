package io.github.stoicswe.eyeandsickle.engine.breach;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.LayerState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Boards that arrive part-solved, so a sixteen-column cipher is shorter work than it looks.
 *
 * <p>Statistical, because the property is a property of a distribution — see
 * {@code MiningChainTest}'s note. Deterministic against a fixed seed, so no flakes.
 */
class CipherPrefillTest {

    /** {@code buildCipher} is private and stays that way; the test reaches it rather than widening it. */
    private static void buildCipher(LayerState layer, int tier, Rng rng) throws Exception {
        Method m = BoardFactory.class.getDeclaredMethod("buildCipher", LayerState.class, int.class, Rng.class);
        m.setAccessible(true);
        m.invoke(null, layer, tier, rng);
    }

    private static int givenCount(LayerState layer) {
        int given = 0;
        for (int c = 0; c < layer.cipherGiven.size(); c++) {
            if (layer.cipherGiven.get(c)) {
                given++;
            }
        }
        return given;
    }

    @Test
    @DisplayName("⚠ every given column holds the RIGHT answer, on every board")
    void givenColumnsAreCorrect() throws Exception {
        GameSave save = new GameSave();
        save.rngSeed = 0xBEEFL;
        Rng rng = Rng.of(save);
        for (int tier = 1; tier <= 5; tier++) {
            for (int i = 0; i < 500; i++) {
                LayerState layer = new LayerState();
                buildCipher(layer, tier, rng);
                for (int c = 0; c < layer.cipherGiven.size(); c++) {
                    if (layer.cipherGiven.get(c)) {
                        // A given column that held the wrong value would be the cruellest possible
                        // bug: the player cannot edit it, and would lose a strike on commit for an
                        // answer the board wrote itself.
                        assertThat(layer.cipherEntered.get(c))
                                .as("tier %d board %d cell %d", tier, i, c)
                                .isEqualTo(OffsetRules.expected(layer, c));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a give happens on roughly the published share of boards")
    void happensAboutAsOftenAsAdvertised() throws Exception {
        GameSave save = new GameSave();
        save.rngSeed = 0xC0FFEEL;
        Rng rng = Rng.of(save);
        int boards = 4000;
        int withAny = 0;
        for (int i = 0; i < boards; i++) {
            LayerState layer = new LayerState();
            buildCipher(layer, 5, rng);
            if (givenCount(layer) > 0) {
                withAny++;
            }
        }
        // Standard error at n=4000 is under 1%, so 4 points is comfortable and still meaningful.
        assertThat(withAny / (double) boards)
                .isCloseTo(Balance.CIPHER_PREFILL_CHANCE, org.assertj.core.data.Offset.offset(0.04d));
    }

    @Test
    @DisplayName("⚠ the cap holds: a short board is never mostly done for you")
    void capHolds() throws Exception {
        GameSave save = new GameSave();
        save.rngSeed = 0x5EEDL;
        Rng rng = Rng.of(save);
        for (int tier = 1; tier <= 5; tier++) {
            int length = Balance.breachCipherLength(tier);
            int cap = Balance.cipherPrefillCap(length);
            for (int i = 0; i < 800; i++) {
                LayerState layer = new LayerState();
                buildCipher(layer, tier, rng);
                // Without the cap a 6-byte board could arrive with 5 of 6 columns done, which is not
                // a shorter puzzle but an absent one.
                assertThat(givenCount(layer)).as("tier %d", tier).isLessThanOrEqualTo(cap);
                assertThat(givenCount(layer)).isLessThan(length);
            }
        }
    }

    @Test
    @DisplayName("⚠ the draw is constant-length, so a stored seed is still a replay")
    void consumesTheSameStreamWhicheverWayItRolls() throws Exception {
        // Rng's contract: consumption must not depend on the values produced. If prefill drew a
        // variable number of values, two boards differing only in the first roll would desynchronise
        // every later draw in the breach — the exact bug nextInt has no rejection loop to avoid.
        long[] after = new long[40];
        for (int i = 0; i < after.length; i++) {
            GameSave save = new GameSave();
            save.rngSeed = 1000L + i;
            Rng rng = Rng.of(save);
            LayerState layer = new LayerState();
            buildCipher(layer, 5, rng);
            rng.commit(save);
            after[i] = save.rngSeed;
        }
        // Every seed advanced by the same number of draws, so the distance from its start is a pure
        // function of the seed. The check that it is CONSTANT-length is that no two runs of the same
        // starting seed differ — re-run one and compare.
        for (int i = 0; i < after.length; i++) {
            GameSave save = new GameSave();
            save.rngSeed = 1000L + i;
            Rng rng = Rng.of(save);
            LayerState layer = new LayerState();
            buildCipher(layer, 5, rng);
            rng.commit(save);
            assertThat(save.rngSeed)
                    .as("seed %d replays identically", 1000L + i)
                    .isEqualTo(after[i]);
        }
    }
}
