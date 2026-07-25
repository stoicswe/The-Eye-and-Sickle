package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shape of an item's unlock requirement.
 *
 * <p>Invariant I3 — every item sits behind exactly one gate — survives only if "which gate?" always
 * has a single answer. {@code docs/design/02-unlock-gates.md} §1.1 nonetheless sanctions split gates,
 * and the tension between those two facts is the entire reason this type is an ordered pair rather
 * than a set. These tests pin the resolution: one primary, at most one secondary, and the ceiling
 * always on the primary.
 */
class GateRequirementTest {

    @Nested
    @DisplayName("a single gate")
    class Single {

        @Test
        @DisplayName("is the common case and needs no secondary")
        void singleGate() {
            GateRequirement fuzzer = GateRequirement.single(UnlockGate.ETHECOIN);

            assertThat(fuzzer.primary()).isEqualTo(UnlockGate.ETHECOIN);
            assertThat(fuzzer.secondary()).isNull();
            assertThat(fuzzer.isSplit()).isFalse();
        }

        @Test
        @DisplayName("works for every gate — none of the five is split-only")
        void everyGateCanStandAlone() {
            for (UnlockGate gate : UnlockGate.values()) {
                assertThat(GateRequirement.single(gate).primary()).isEqualTo(gate);
            }
        }

        @Test
        @DisplayName("requires a primary")
        void primaryRequired() {
            assertThatThrownBy(() -> GateRequirement.single(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new GateRequirement(null, UnlockGate.ETHECOIN))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("the splits the docs sanction")
    class SanctionedSplits {

        @Test
        @DisplayName("Relay Chain — framework found, hops paid for per session")
        void relayChain() {
            GateRequirement relayChain = GateRequirement.split(UnlockGate.SCHEMATIC, UnlockGate.ETHECOIN);

            assertThat(relayChain.isSplit()).isTrue();
            assertThat(relayChain.primary()).isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(relayChain.secondary()).isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("Rainbow Table — bought with ethecoin, but the capability to use it is found")
        void rainbowTable() {
            // §1.1 writes this one "EC + schematic". The capability is still the ceiling, so it is
            // still the primary: the table's price is what recurs, not what classifies the item.
            GateRequirement rainbowTable = GateRequirement.split(UnlockGate.SCHEMATIC, UnlockGate.ETHECOIN);

            assertThat(rainbowTable.primary()).isEqualTo(UnlockGate.SCHEMATIC);
        }

        @Test
        @DisplayName("Cold Storage Expansion — schematic plus reputation")
        void coldStorageExpansion() {
            GateRequirement coldStorage = GateRequirement.split(UnlockGate.SCHEMATIC, UnlockGate.REPUTATION);

            assertThat(coldStorage.primary()).isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(coldStorage.involves(UnlockGate.REPUTATION)).isTrue();
        }

        @Test
        @DisplayName("Zero-Day — reachable only while hot, then paid for")
        void zeroDay() {
            // §3's Zero-Day row is the reason this type does not also forbid an ETHECOIN primary:
            // heat state gates access, not a ceiling, so which half classifies the item is a design
            // question the docs leave open rather than one this type may answer.
            GateRequirement zeroDay = GateRequirement.split(UnlockGate.HEAT_STATE, UnlockGate.ETHECOIN);

            assertThat(zeroDay.involves(UnlockGate.HEAT_STATE)).isTrue();
            assertThat(zeroDay.involves(UnlockGate.ETHECOIN)).isTrue();
            assertThat(zeroDay.involves(UnlockGate.SCHEMATIC)).isFalse();
        }
    }

    @Nested
    @DisplayName("the structural rules")
    class StructuralRules {

        @Test
        @DisplayName("ethecoin twice is rejected — that is a price, not a split")
        void ethecoinPlusEthecoinRejected() {
            assertThatThrownBy(() -> GateRequirement.split(UnlockGate.ETHECOIN, UnlockGate.ETHECOIN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("two different gates");
        }

        @Test
        @DisplayName("no gate may pair with itself")
        void noGatePairsWithItself() {
            for (UnlockGate gate : UnlockGate.values()) {
                assertThatThrownBy(() -> GateRequirement.split(gate, gate))
                        .as("%s paired with itself", gate)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        @DisplayName("a schematic secondary is rejected — a ceiling always takes primacy")
        void ceilingRaisingSecondaryRejected() {
            // §1.1 asks "does it raise a permanent ceiling?" first, so a real schematic component
            // always wins the classification. A schematic in the secondary slot is either a
            // mis-assignment or a ceiling smuggled in behind a price, which Invariant I2 forbids.
            for (UnlockGate primary : UnlockGate.values()) {
                if (primary == UnlockGate.SCHEMATIC) {
                    continue;
                }
                assertThatThrownBy(() -> GateRequirement.split(primary, UnlockGate.SCHEMATIC))
                        .as("%s + SCHEMATIC", primary)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("ceiling gate");
            }
        }

        @Test
        @DisplayName("split() will not quietly build a single gate from a null secondary")
        void splitRequiresASecondary() {
            assertThatThrownBy(() -> GateRequirement.split(UnlockGate.SCHEMATIC, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("every non-ceiling gate is a legal secondary")
        void legalSecondaries() {
            for (UnlockGate secondary : UnlockGate.values()) {
                if (secondary == UnlockGate.SCHEMATIC || secondary == UnlockGate.REPUTATION) {
                    continue;
                }
                assertThat(GateRequirement.split(UnlockGate.REPUTATION, secondary)
                                .secondary())
                        .isEqualTo(secondary);
            }
        }
    }

    @Nested
    @DisplayName("reading a requirement")
    class Reading {

        @Test
        @DisplayName("involves() looks in both slots and nowhere else")
        void involvesBothSlots() {
            GateRequirement relayChain = GateRequirement.split(UnlockGate.SCHEMATIC, UnlockGate.ETHECOIN);

            assertThat(relayChain.involves(UnlockGate.SCHEMATIC)).isTrue();
            assertThat(relayChain.involves(UnlockGate.ETHECOIN)).isTrue();
            assertThat(relayChain.involves(UnlockGate.PROOF_OF_SKILL)).isFalse();
            assertThat(relayChain.involves(UnlockGate.HEAT_STATE)).isFalse();
            assertThat(relayChain.involves(UnlockGate.REPUTATION)).isFalse();
        }

        @Test
        @DisplayName("a single gate involves only itself")
        void singleInvolvesOnlyItself() {
            GateRequirement overflowKit = GateRequirement.single(UnlockGate.PROOF_OF_SKILL);

            assertThat(overflowKit.involves(UnlockGate.PROOF_OF_SKILL)).isTrue();
            assertThat(overflowKit.involves(UnlockGate.ETHECOIN)).isFalse();
        }

        @Test
        @DisplayName("the primary is a single answer, not a member of a set")
        void primaryIsUnambiguous() {
            // The whole point of the ordered pair: Invariant I3 asks "which one gate?" and there is
            // exactly one field that answers. A Set<UnlockGate> would leave that question to whoever
            // iterated first, and the invariant would stop meaning anything without ever failing.
            GateRequirement coldStorage = GateRequirement.split(UnlockGate.SCHEMATIC, UnlockGate.REPUTATION);

            assertThat(coldStorage.primary()).isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(GateRequirement.class.getRecordComponents()).hasSize(2);
        }

        @Test
        @DisplayName("equal requirements are equal values, and order within the pair matters")
        void valueEquality() {
            assertThat(GateRequirement.split(UnlockGate.HEAT_STATE, UnlockGate.ETHECOIN))
                    .isEqualTo(new GateRequirement(UnlockGate.HEAT_STATE, UnlockGate.ETHECOIN))
                    .isNotEqualTo(GateRequirement.single(UnlockGate.HEAT_STATE));
        }
    }
}
