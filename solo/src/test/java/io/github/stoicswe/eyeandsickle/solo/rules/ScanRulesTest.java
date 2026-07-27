package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.breach.Targets;
import io.github.stoicswe.eyeandsickle.solo.state.DefenseState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Scan precision and sensitivity — the two side decisions from {@code docs/design/04-mining.md}
 * §3.2a and {@code docs/design/09-defense-and-hardening.md} §2.
 *
 * <p>The rates are statistical, so the tests are statistical: a fixed seed and enough trials that a
 * rate wrong by a third would fail. Testing a probability with a single draw tests nothing.
 */
class ScanRulesTest {

    private static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");
    private static final int TRIALS = 4_000;

    /**
     * A save with a fixed seed, a genuinely bare rig, and one innocent thing on it.
     *
     * <p>⚠ The scrub matters here more than anywhere. {@code SoloGame.newCharacter} plants the
     * scripted tutorial miner ({@code docs/design/04-mining.md} §5.1), which is a <em>true</em>
     * finding — so a false-positive rate measured without clearing it would be measuring a line that
     * also contains a real hit, and every roll would consume an extra RNG draw for the sensitivity
     * check. Written this way so the measurement is the same whether or not the plant is wired.
     */
    private static SoloSave save(long seed) {
        SoloSave save = SoloGame.newCharacter("operator", T0);
        save.rngSeed = seed;
        save.rig.foreignMiners.clear();
        save.rig.allocations.clear();
        // Something innocent for a false positive to name, and something a player could check it
        // against — design/04 §3.2a wants a lead to corroborate, not an invented ghost.
        save.rig.selfMiningCycles = 40L;
        return save;
    }

    /** The measured false-positive rate for a tier, over many independent saves. */
    private static double measure(String tier, int arrayTier) {
        int hits = 0;
        for (int i = 0; i < TRIALS; i++) {
            SoloSave save = save(i * 31L + 7L);
            if (arrayTier > 0) {
                DefenseState array = new DefenseState();
                array.kind = "detection-array";
                array.tier = arrayTier;
                save.defenses.add(array);
            }
            Rng rng = Rng.of(save);
            String line = ScanRules.roll(save, tier, rng).line();
            rng.commit(save);
            if (line.contains("Also flagged")) {
                hits++;
            }
        }
        return hits / (double) TRIALS;
    }

    @Nested
    @DisplayName("false positives (04 §3.2a)")
    class FalsePositives {

        @Test
        @DisplayName("the cheap tier sends you chasing ghosts; the expensive one earns its price")
        void ratesMatchTheirTiers() {
            double quick = measure("QUICK", 0);
            double full = measure("FULL", 0);
            double thorough = measure("THOROUGH", 0);

            assertThat(quick).isCloseTo(Balance.SCAN_FALSE_POSITIVE_QUICK, org.assertj.core.data.Offset.offset(0.03));
            assertThat(full).isCloseTo(Balance.SCAN_FALSE_POSITIVE_FULL, org.assertj.core.data.Offset.offset(0.03));
            assertThat(thorough)
                    .isCloseTo(Balance.SCAN_FALSE_POSITIVE_THOROUGH, org.assertj.core.data.Offset.offset(0.02));
            // The ordering is the design; the values are for playtest. If a re-tune ever inverts
            // these two, the Thorough Scan's 35 cycles stop being justifiable.
            assertThat(quick).isGreaterThan(full);
            assertThat(full).isGreaterThan(thorough);
        }

        @Test
        @DisplayName("a false positive names a real, innocent thing that the ledger will corroborate")
        void falsePositivesAreCorroborable() {
            int checked = 0;
            for (int i = 0; i < 200; i++) {
                SoloSave save = save(i * 97L + 3L);
                Rng rng = Rng.of(save);
                String line = ScanRules.roll(save, "QUICK", rng).line();
                if (!line.contains("Also flagged")) {
                    continue;
                }
                checked++;
                // design/04 §3.2a: "a scan hit is a lead to corroborate against the compute ledger
                // ... instead of an answer that makes investigation pointless." An invented process
                // name would be uncorroborable, and the lesson would become "scan hits are noise".
                assertThat(line).contains("self-mining");
                assertThat(line).contains("Corroborate");
            }
            assertThat(checked).isPositive();
        }

        @Test
        @DisplayName("a bare rig raises no false positive, because there is nothing honest to name")
        void nothingToNameMeansNoAlarm() {
            SoloSave bare = SoloGame.newCharacter("operator", T0);
            bare.rig.foreignMiners.clear();
            bare.rig.allocations.clear();
            bare.rngSeed = 5L;
            for (int i = 0; i < 100; i++) {
                bare.rngSeed = i * 13L;
                Rng rng = Rng.of(bare);
                assertThat(ScanRules.roll(bare, "QUICK", rng).line()).doesNotContain("Also flagged");
                rng.commit(bare);
            }
        }
    }

    @Nested
    @DisplayName("the Detection Array (09 §2, closing OQ-6)")
    class DetectionArray {

        @Test
        @DisplayName("standing compute buys precision — a lower false-positive rate, tier by tier")
        void arrayCutsTheRate() {
            double bare = measure("QUICK", 0);
            double t1 = measure("QUICK", 1);
            double t3 = measure("QUICK", 3);

            assertThat(t1).isLessThan(bare);
            assertThat(t3).isLessThan(t1);
            // ⚠ Never zero. A multiplier cannot reach certainty, which is the same reason design/07
            // §2 requires the Honeypot Detector to have a false-negative rate: a perfect detector
            // removes the doubt the whole detection system exists to create.
            assertThat(t3).isPositive();
        }

        @Test
        @DisplayName("it buys precision and NOT sensitivity — that is what closed OQ-6")
        void arrayDoesNotChangeWhatIsSeen() {
            MinerState hidden = new MinerState();
            hidden.tier = 3;
            hidden.rootkitWrapped = true;

            // The Array is not a parameter of sensitiveTo at all. Scans buy sensitivity, the Array
            // buys precision, and design/09 §2's resolution is exactly that they are different axes
            // — a version of this that took the Array would re-merge them and re-open the question.
            assertThat(ScanRules.sensitiveTo("QUICK", hidden, new Rng(1L))).isFalse();
            assertThat(ScanRules.sensitiveTo("THOROUGH", hidden, new Rng(1L))).isTrue();
        }

        @Test
        @DisplayName("the armed tier is read off the defence list")
        void tierIsReadFromDefences() {
            SoloSave save = save(1L);
            assertThat(ScanRules.detectionArrayTier(save)).isZero();

            DefenseState array = new DefenseState();
            array.kind = "detection-array";
            array.tier = 2;
            save.defenses.add(array);
            assertThat(ScanRules.detectionArrayTier(save)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("sensitivity (04 §3.2's Finds column)")
    class Sensitivity {

        @Test
        @DisplayName("Quick sees unhidden T2-T3; Full sees all unhidden plus some rootkits; Thorough sees all")
        void tiersDifferInWhatTheyFind() {
            MinerState weak = new MinerState();
            weak.tier = 1;
            MinerState ordinary = new MinerState();
            ordinary.tier = 3;
            MinerState hidden = new MinerState();
            hidden.tier = 3;
            hidden.rootkitWrapped = true;

            assertThat(ScanRules.sensitiveTo("QUICK", weak, new Rng(1L))).isFalse();
            assertThat(ScanRules.sensitiveTo("QUICK", ordinary, new Rng(1L))).isTrue();
            assertThat(ScanRules.sensitiveTo("QUICK", hidden, new Rng(1L))).isFalse();
            assertThat(ScanRules.sensitiveTo("FULL", ordinary, new Rng(1L))).isTrue();
            assertThat(ScanRules.sensitiveTo("THOROUGH", hidden, new Rng(1L))).isTrue();
        }

        @Test
        @DisplayName("a Full Scan finds SOME rootkit-wrapped miners — the number behind 'some'")
        void fullScanIsACoinFlipOnRootkits() {
            MinerState hidden = new MinerState();
            hidden.tier = 3;
            hidden.rootkitWrapped = true;

            Rng rng = new Rng(2024L);
            int found = 0;
            for (int i = 0; i < TRIALS; i++) {
                if (ScanRules.sensitiveTo("FULL", hidden, rng)) {
                    found++;
                }
            }
            assertThat(found / (double) TRIALS)
                    .isCloseTo(Balance.SCAN_ROOTKIT_SENSITIVITY_FULL, org.assertj.core.data.Offset.offset(0.03));
        }

        @Test
        @DisplayName("a real miner on the rig is reported by a tier that can see it")
        void trueHitsAreReported() {
            SoloSave save = save(11L);
            MinerState miner = Targets.plantTutorialMiner(save, T0);
            miner.tier = 3;
            miner.label = "unregistered process";

            Rng rng = Rng.of(save);
            assertThat(ScanRules.roll(save, "THOROUGH", rng).line()).contains("unregistered process");
        }
    }

    @Nested
    @DisplayName("freezing the finding")
    class Freezing {

        @Test
        @DisplayName("a completed scan reports what it captured at the start")
        void findingComesOffTheTask() {
            TaskState task = new TaskState("scan", "scan --full", "alloc", 15L, T0, T0.plusSeconds(120));
            task.outcome = "2 foreign miners found: a, b.";

            // Rolling at completion instead would mean a six-minute scan quietly re-rolled its
            // answer depending on whether the player watched — and under the persisted RNG that is
            // also a reroll a player could force by quitting.
            assertThat(ScanRules.finding(task)).isEqualTo("2 foreign miners found: a, b.");
        }

        @Test
        @DisplayName("a save written before findings were captured says so rather than inventing one")
        void oldSavesAreHonest() {
            TaskState legacy = new TaskState("scan", "scan --quick", "alloc", 5L, T0, T0.plusSeconds(30));

            String finding = ScanRules.finding(legacy);
            // A confident "nothing found" the engine did not establish is a lie the player would
            // reasonably act on — worse than admitting the gap.
            assertThat(finding).contains("predates");
            assertThat(finding).doesNotContain("No foreign miner");
        }
    }
}
