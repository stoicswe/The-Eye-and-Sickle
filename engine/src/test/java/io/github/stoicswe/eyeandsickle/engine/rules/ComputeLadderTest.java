package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The compute ladder, and the invariant it amends.
 *
 * <h2>⚠ THIS FILE IS THE SAFETY ARGUMENT FOR AMENDING INVARIANT I1</h2>
 *
 * I1 reads "compute is never purchasable with ethecoin", because mining that buys mining capacity is
 * a compounding flywheel and compute is the master scarcity. On explicit direction (2026-08-06,
 * {@code design/15} §3) exactly <b>one</b> rung — 24 → 32 — is purchasable.
 *
 * <p>The amendment is safe only while it stays one rung wide, because a single step cannot close the
 * loop: mine → buy capacity → mine faster → <em>buy more capacity</em> needs a second purchase, and
 * there is not one. {@link Amendment} is that constraint made mechanical. If a later change gives a
 * second rung a price, this file goes red — which is the difference between an invariant that was
 * amended and one that was abandoned by increments.
 */
class ComputeLadderTest {

    private static GameSave rigHolding(String... itemTypes) {
        GameSave save = new GameSave();
        for (String type : itemTypes) {
            ItemState item = new ItemState();
            item.itemType = type;
            save.items.add(item);
        }
        return save;
    }

    @Nested
    @DisplayName("⚠ the I1 amendment stays exactly one rung wide")
    class Amendment {

        /**
         * ⚠ <b>THE TEST THIS WHOLE FILE EXISTS FOR.</b>
         *
         * <p>A second priced rung turns a one-time head start into a flywheel, and it would do so
         * quietly: the shop renders, the purchase works, the rig gets faster, and the reason the
         * game's master scarcity holds is gone.
         */
        @Test
        @DisplayName("only the FIRST rung has a price; everything above it is schematic-gated")
        void onlyTheFirstRungIsForSale() {
            var rungs = ComputeLadder.rungs();
            assertThat(rungs).hasSizeGreaterThan(1);

            var first = Catalogue.byId(rungs.getFirst().itemType()).orElseThrow();
            assertThat(first.gate())
                    .as("the amendment is that the first rung is bought")
                    .isEqualTo(UnlockGate.ETHECOIN);
            assertThat(first.priceWei()).isEqualTo(Balance.COMPUTE_32_PRICE);

            for (var rung : rungs.subList(1, rungs.size())) {
                var offering = Catalogue.byId(rung.itemType()).orElseThrow();
                assertThat(offering.gate())
                        .as("%s is a compute rung above the first — a price on it is I1 abandoned, "
                                + "not amended, and it closes the mine-buys-mining loop", rung.itemType())
                        .isEqualTo(UnlockGate.SCHEMATIC);
                assertThat(offering.priceWei())
                        .as("%s must not be purchasable at ANY price", rung.itemType())
                        .isEqualTo(BigInteger.ZERO);
            }
        }

        /**
         * ⚠ And nothing else in the whole catalogue may sell capacity either.
         *
         * <p>The narrow check above watches the ladder. This one watches for a <em>fourth</em>
         * offering that raises the ceiling by another name — the way the amendment would actually
         * erode, one reasonable-sounding item at a time.
         */
        @Test
        @DisplayName("exactly one ethecoin offering anywhere raises the compute ceiling")
        void nothingElseSellsCapacity() {
            long priced = Catalogue.offerings().stream()
                    .filter(o -> o.gate() == UnlockGate.ETHECOIN)
                    .filter(o -> ComputeLadder.rungFor(o.id()).isPresent())
                    .count();
            assertThat(priced)
                    .as("compute is the master scarcity; one purchasable rung is the whole amendment")
                    .isEqualTo(1);
        }

        /** ⚠ The purchasable rung must be the BOTTOM one, or the ladder can be entered from above. */
        @Test
        @DisplayName("the purchasable rung is the lowest one")
        void theBoughtRungIsTheBottom() {
            var rungs = ComputeLadder.rungs();
            long bought = rungs.getFirst().capacity();
            assertThat(rungs).allSatisfy(rung -> assertThat(rung.capacity()).isGreaterThanOrEqualTo(bought));
        }
    }

    @Nested
    @DisplayName("capacity is derived, never stored")
    class Derivation {

        @Test
        @DisplayName("a fresh rig is at the starting cycles")
        void freshRig() {
            assertThat(ComputeLadder.capacityOf(rigHolding())).isEqualTo(Balance.STARTING_CYCLES);
            assertThat(Balance.STARTING_CYCLES).isEqualTo(24L);
        }

        @Test
        @DisplayName("each upgrade held raises the ceiling to its rung")
        void eachRung() {
            assertThat(ComputeLadder.capacityOf(rigHolding("compute-32"))).isEqualTo(32L);
            assertThat(ComputeLadder.capacityOf(rigHolding("compute-32", "compute-48")))
                    .isEqualTo(48L);
            assertThat(ComputeLadder.capacityOf(rigHolding("compute-32", "compute-48", "compute-64")))
                    .isEqualTo(64L);
        }

        /**
         * ⚠ The HIGHEST rung, never a sum.
         *
         * <p>Summing would put a player who acquired them out of order at a capacity no rung
         * exists at — and would make the ceiling depend on acquisition order, which nothing else in
         * the game does.
         */
        @Test
        @DisplayName("holding several is the highest of them, not the total")
        void highestNotSum() {
            assertThat(ComputeLadder.capacityOf(rigHolding("compute-48", "compute-64")))
                    .as("48 + 64 is not 112")
                    .isEqualTo(64L);
        }

        /**
         * ⚠ THE BUG THIS DERIVATION EXISTS TO PREVENT, in miniature.
         *
         * <p>{@code ChainState.networkHashrate} was a stored copy of a derived value; it went stale
         * against a re-tune and cost a real character 29% of their income forever, silently. A
         * stored capacity fails identically — and is also what a hand-edited save would raise to
         * grant itself the whole ladder.
         */
        @Test
        @DisplayName("a save whose stored ceiling disagrees is corrected, in both directions")
        void theCacheAlwaysAgreesWithTheLadder() {
            GameSave inflated = rigHolding();
            inflated.rig.totalCycles = 999L;
            assertThat(ComputeLadder.reconcile(inflated)).isTrue();
            assertThat(inflated.rig.totalCycles)
                    .as("a hand-edited ceiling does not grant capacity")
                    .isEqualTo(Balance.STARTING_CYCLES);

            GameSave stale = rigHolding("compute-32");
            stale.rig.totalCycles = Balance.STARTING_CYCLES;
            assertThat(ComputeLadder.reconcile(stale)).isTrue();
            assertThat(stale.rig.totalCycles)
                    .as("and an upgrade that landed by any route takes effect")
                    .isEqualTo(32L);
        }

        @Test
        @DisplayName("reconciling twice is reconciling once")
        void idempotent() {
            GameSave save = rigHolding("compute-32");
            assertThat(ComputeLadder.reconcile(save)).isTrue();
            assertThat(ComputeLadder.reconcile(save))
                    .as("nothing changed, so nothing is logged or persisted for it")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("climbing")
    class Climbing {

        @Test
        @DisplayName("the next rung is the one above where the rig is")
        void next() {
            assertThat(ComputeLadder.next(rigHolding()).orElseThrow().capacity()).isEqualTo(32L);
            assertThat(ComputeLadder.next(rigHolding("compute-32")).orElseThrow().capacity())
                    .isEqualTo(48L);
            assertThat(ComputeLadder.next(rigHolding("compute-32", "compute-48", "compute-64")))
                    .as("the top of the ladder has nothing above it")
                    .isEmpty();
        }

        /**
         * ⚠ In order, and this is what keeps the amendment's reasoning true.
         *
         * <p>If 48 could be reached without 32, a player could leave the one purchasable rung
         * unbought forever and climb entirely on schematics — which is a different game from the one
         * "money moves you up once, ever" was reasoned about.
         */
        @Test
        @DisplayName("a rung cannot be taken before the ones below it")
        void inOrder() {
            var rungs = ComputeLadder.rungs();
            var lattice48 = rungs.get(1);
            var lattice64 = rungs.get(2);

            assertThat(ComputeLadder.rungsBelowAreHeld(rigHolding(), lattice48)).isFalse();
            assertThat(ComputeLadder.rungsBelowAreHeld(rigHolding("compute-32"), lattice48))
                    .isTrue();
            assertThat(ComputeLadder.rungsBelowAreHeld(rigHolding("compute-32"), lattice64))
                    .as("48 is still missing")
                    .isFalse();
            assertThat(ComputeLadder.rungsBelowAreHeld(rigHolding("compute-32", "compute-48"), lattice64))
                    .isTrue();
        }

        /** ⚠ The materials are fill-ins, but the SHAPE has to be real or the Compiler has nothing. */
        @Test
        @DisplayName("every compiled rung names materials; the bought one names none")
        void materials() {
            var rungs = ComputeLadder.rungs();
            assertThat(rungs.getFirst().materials())
                    .as("the bought rung is a product, not a build")
                    .isEmpty();
            for (var rung : rungs.subList(1, rungs.size())) {
                assertThat(rung.materials())
                        .as("%s is compiled and must require something", rung.itemType())
                        .isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("what the ladder is FOR")
    class WhyItMatters {

        /**
         * ⚠ The costs were deliberately NOT rescaled, and this is the assertion that says so.
         *
         * <p>A Thorough Scan costs 35 cycles and a starting rig has 24, so the top scan tier is
         * content behind the ladder rather than a button a new character can press. That is the
         * reason capacity is worth buying: what a rung unlocks is <em>which operations are possible
         * at all</em>, not bigger numbers. If somebody ever "fixes" this by rescaling the costs, the
         * upgrades stop buying anything and this test is where that conversation happens.
         */
        @Test
        @DisplayName("a starting rig genuinely cannot run the top scan tier")
        void theTopTierIsBehindTheLadder() {
            assertThat(Balance.SCAN_THOROUGH_CYCLES)
                    .as("if this ever fits inside a starting rig, the ladder buys nothing")
                    .isGreaterThan(Balance.STARTING_CYCLES);
            assertThat(Balance.SCAN_THOROUGH_CYCLES)
                    .as("and the first rung is not enough for it either")
                    .isGreaterThan(Balance.COMPUTE_RUNGS[1]);
            assertThat(Balance.SCAN_THOROUGH_CYCLES)
                    .as("but the second rung is")
                    .isLessThan(Balance.COMPUTE_RUNGS[2]);
        }
    }
}
