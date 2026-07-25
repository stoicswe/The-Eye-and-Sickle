package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The factions, and standing with one of them.
 *
 * <p>The glossary marks "reputation" as a word with two unrelated meanings, and the failure this
 * suite guards against is not a wrong number — it is a merge. {@code factionReputation} (a player's
 * Eye/Sickle standing) and {@code validatorReputation} (a federated server's trust score) would look
 * interchangeable to anyone reading either one in isolation, and the day they share a type is the day
 * a story beat can move a consensus weight.
 */
class FactionReputationTest {

    @Nested
    @DisplayName("the factions")
    class Factions {

        @Test
        @DisplayName("are the two sides plus uncommitted, and nothing else")
        void closedSet() {
            assertThat(Arrays.stream(Faction.values()).map(Enum::name).toList())
                    .containsExactly("EYE", "SICKLE", "NONE");
        }
    }

    @Nested
    @DisplayName("standing")
    class Standing {

        @Test
        @DisplayName("is held against a named faction")
        void heldAgainstANamedFaction() {
            FactionReputation sickle = new FactionReputation(Faction.SICKLE, 1_200);

            assertThat(sickle.faction()).isEqualTo(Faction.SICKLE);
            assertThat(sickle.standing()).isEqualTo(1_200L);
        }

        @Test
        @DisplayName("cannot be held against NONE — uncommitted is the absence of a standing")
        void noneRejected() {
            assertThatThrownBy(() -> new FactionReputation(Faction.NONE, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("uncommitted");
        }

        @Test
        @DisplayName("requires a faction")
        void nullFactionRejected() {
            assertThatThrownBy(() -> new FactionReputation(null, 10)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of zero is legal — that is what abandoning a side leaves behind")
        void zeroIsLegal() {
            // docs/design/01 §5: abandoning a side resets that reputation. The reset value is a
            // standing of zero with a named faction, not a standing with nobody.
            assertThat(new FactionReputation(Faction.EYE, 0).standing()).isZero();
        }

        @Test
        @DisplayName("may be negative — the docs do not settle whether hostility exists, so the type does not either")
        void negativeIsPermitted() {
            // [PROPOSAL] tracked in the type's javadoc. A wire type that rejects a value the design
            // later wants fails worse than one that carries a value the server never sends.
            assertThat(new FactionReputation(Faction.EYE, -500).standing()).isEqualTo(-500L);
        }

        @Test
        @DisplayName("is per faction: the same score against each side is two different values")
        void perFactionIdentity() {
            assertThat(new FactionReputation(Faction.EYE, 1_200))
                    .isNotEqualTo(new FactionReputation(Faction.SICKLE, 1_200));
            assertThat(new FactionReputation(Faction.EYE, 1_200))
                    .isEqualTo(new FactionReputation(Faction.EYE, 1_200))
                    .hasSameHashCodeAs(new FactionReputation(Faction.EYE, 1_200));
        }
    }

    @Test
    @DisplayName("no type in this package is called plain Reputation")
    void noGenericReputationType() {
        // Belt and braces with ArchitectureRulesTest, which enforces this module-wide. Repeated here
        // because this is the package where the temptation actually arises: someone modelling "a
        // score attached to an actor" will reach for the generic name, and the two scores it would
        // unify are the two the glossary spends a ⚠ on keeping apart.
        assertThatThrownBy(() -> Class.forName(FactionReputation.class.getPackageName() + ".Reputation"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
