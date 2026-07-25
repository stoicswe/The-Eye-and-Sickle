package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.FactionReputation;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * factionReputation is NEVER validatorReputation. The glossary flags "reputation" as a word with two
 * unrelated meanings, and this test defends the trap at the slice boundary: the player-standing type and
 * the repository that writes it carry no field, component, or method that would let a player's Eye/Sickle
 * standing be conflated with a federated server's trust score. (The database-level guarantee — no shared
 * column, no shared key — is covered by {@code SchemaIT}.)
 */
class FactionReputationSeparationTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

    @Nested
    @DisplayName("the two reputations are structurally separate")
    class Separation {

        @Test
        @DisplayName("FactionReputation is exactly (faction, standing) — nothing validator-shaped rides along")
        void componentsAreOnlyFactionAndStanding() {
            String[] components = Arrays.stream(FactionReputation.class.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toArray(String[]::new);
            assertThat(components).containsExactly("faction", "standing");
        }

        @Test
        @DisplayName("neither the standing type nor the standings repository mentions 'validator' anywhere")
        void noValidatorSurfaceInTheSlice() {
            // A generic or shared surface is the shape the forbidden merge would arrive in; asserting its
            // absence keeps the two namespaces from drifting together (docs/design/glossary.md).
            Stream.concat(
                            Arrays.stream(FactionReputation.class.getMethods()),
                            Arrays.stream(FactionReputationRepository.class.getMethods()))
                    .map(Method::getName)
                    .forEach(name -> assertThat(name.toLowerCase())
                            .as("method '%s' must not reference validator reputation", name)
                            .doesNotContain("validator"));
        }
    }

    @Nested
    @DisplayName("standing is always with a NAMED faction")
    class NamedFactionOnly {

        @Test
        @DisplayName("the protocol record rejects Faction.NONE")
        void recordRejectsNone() {
            assertThatThrownBy(() -> new FactionReputation(Faction.NONE, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a standing may be negative (actively hostile) and is preserved verbatim")
        void negativeStandingPreserved() {
            FactionReputation hostile = new FactionReputation(Faction.EYE, -40);
            assertThat(hostile.faction()).isEqualTo(Faction.EYE);
            assertThat(hostile.standing()).isEqualTo(-40);
        }

        @Test
        @DisplayName("the repository refuses NONE for both adjust and set, without issuing any SQL")
        void repositoryRejectsNoneBeforeTouchingTheDatabase() {
            // requireNamed fires before jdbcClient is used, so a NONE target is caught as a named rule
            // rather than surfacing as a raw ck_faction_reputations_named_faction violation.
            JdbcClient jdbcClient = mock(JdbcClient.class);
            FactionReputationRepository repository = new FactionReputationRepository(jdbcClient);
            UUID playerId = UUID.randomUUID();

            assertThatThrownBy(() -> repository.adjustStanding(playerId, Faction.NONE, 1, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repository.setStanding(playerId, Faction.NONE, 0, NOW))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(jdbcClient);
        }
    }

    @Test
    @DisplayName("Faction is a two-way choice with a waiting room — EYE, SICKLE, NONE and nothing else")
    void factionShape() {
        assertThat(Faction.values()).containsExactly(Faction.EYE, Faction.SICKLE, Faction.NONE);
    }
}
