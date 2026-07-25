package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.DevSignin;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.Operator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The faction commitment state machine ({@code docs/design/01-core-resources.md} §5). Commit is to one
 * named side and refuses a silent switch; abandon resets the abandoned side's standing, spikes heat, and
 * forfeits that side's tools — all three together. Two things it must NOT do: invent the heat-spike
 * magnitude (a balance value), or fold a side-switch into one step that skips the cost of leaving.
 */
class FactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");

    private static IdentityProperties props(BigDecimal spike) {
        return new IdentityProperties(
                new Operator(null, null, null), new DevSignin(false), java.time.Duration.ofHours(24), spike);
    }

    private static Player player(UUID id, Faction faction, Heat heat, long rowVersion) {
        return new Player(
                id, DID, 1, "alice.bsky.social", CharacterStatus.ACTIVE, faction, heat, Ethecoin.ZERO, NOW, NOW,
                rowVersion);
    }

    private record Harness(
            FactionService service,
            FakePlayerRepository players,
            FakeFactionReputationRepository reputations,
            RecordingFactionToolForfeiture forfeiture) {}

    private static Harness harness(BigDecimal spike) {
        FakePlayerRepository players = new FakePlayerRepository();
        FakeFactionReputationRepository reputations = new FakeFactionReputationRepository();
        RecordingFactionToolForfeiture forfeiture = new RecordingFactionToolForfeiture();
        FactionService service = new FactionService(players, reputations, forfeiture, props(spike), CLOCK);
        return new Harness(service, players, reputations, forfeiture);
    }

    // ------------------------------------------------------------------ commit

    @Nested
    @DisplayName("commit")
    class Commit {

        @Test
        @DisplayName("an uncommitted player commits to a side")
        void commitsUncommitted() {
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.NONE, Heat.ZERO, 0));

            Player after = h.service().commit(id, Faction.SICKLE);

            assertThat(after.faction()).isEqualTo(Faction.SICKLE);
            assertThat(after.rowVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("committing to the side already held is an idempotent no-op")
        void idempotentForSameSide() {
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.EYE, Heat.ZERO, 5));

            Player after = h.service().commit(id, Faction.EYE);

            assertThat(after.faction()).isEqualTo(Faction.EYE);
            assertThat(after.rowVersion()).as("no write, so no version bump").isEqualTo(5);
            assertThat(h.players().updateFactionCalls()).isEmpty();
        }

        @Test
        @DisplayName("committing to the OPPOSITE side is refused — a switch must go via abandonment")
        void refusesSilentSwitch() {
            // Folding a switch into one step would skip the reset, heat spike and forfeiture that leaving a
            // side is supposed to cost.
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.EYE, Heat.ZERO, 0));

            assertThatThrownBy(() -> h.service().commit(id, Faction.SICKLE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("abandon");
            assertThat(h.players().updateFactionCalls()).isEmpty();
        }

        @Test
        @DisplayName("committing to Faction.NONE is a category error")
        void refusesNone() {
            // NONE is where a player starts, not somewhere they commit to; abandon returns them there.
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.NONE, Heat.ZERO, 0));

            assertThatThrownBy(() -> h.service().commit(id, Faction.NONE)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("committing a missing player is a 404, not an NPE")
        void missingPlayer() {
            Harness h = harness(null);
            assertThatThrownBy(() -> h.service().commit(UUID.randomUUID(), Faction.EYE))
                    .isInstanceOf(PlayerNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------ abandon

    @Nested
    @DisplayName("abandon")
    class Abandon {

        @Test
        @DisplayName("with no configured spike, abandon refuses rather than inventing a magnitude")
        void refusesToFabricateSpike() {
            // The heat-spike magnitude is an undecided balance value; the service must not make one up. It
            // fails, and the transition does not partially apply.
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.EYE, Heat.ZERO, 0));

            assertThatThrownBy(() -> h.service().abandon(id))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("balance value");

            assertThat(h.players().findCharacter(id).orElseThrow().faction())
                    .as("nothing changed")
                    .isEqualTo(Faction.EYE);
            assertThat(h.reputations().setCalls()).isEmpty();
            assertThat(h.forfeiture().calls()).isEmpty();
        }

        @Test
        @DisplayName("with a configured spike, abandon applies exactly that magnitude")
        void appliesConfiguredSpike() {
            // The service uses the configured value verbatim — it neither scales nor invents it.
            Harness h = harness(new BigDecimal("12.5"));
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.EYE, new Heat(new BigDecimal("3.0000")), 2));

            Player after = h.service().abandon(id);

            assertThat(after.faction()).isEqualTo(Faction.NONE);
            assertThat(after.personalHeat().value()).isEqualByComparingTo("15.5");
        }

        @Test
        @DisplayName("an explicit spike resets the abandoned side, spikes heat, and forfeits its tools")
        void fullTransition() {
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.SICKLE, new Heat(new BigDecimal("4")), 1));

            Player after = h.service().abandon(id, new BigDecimal("6"));

            // Faction returns to NONE and heat rises — one version-checked update, so indivisible.
            assertThat(after.faction()).isEqualTo(Faction.NONE);
            assertThat(after.personalHeat().value()).isEqualByComparingTo("10");
            assertThat(after.rowVersion()).isEqualTo(2);

            // The abandoned side's standing is reset to zero...
            assertThat(h.reputations().setCalls()).singleElement().satisfies(call -> {
                assertThat(call.faction()).isEqualTo(Faction.SICKLE);
                assertThat(call.standing()).isZero();
                assertThat(call.now()).isEqualTo(NOW);
            });
            // ...and only the abandoned side — the player left one side, not both.
            assertThat(h.reputations().setCalls()).noneMatch(call -> call.faction() == Faction.EYE);

            // ...and that side's tools are forfeited.
            assertThat(h.forfeiture().calls()).singleElement().satisfies(call -> {
                assertThat(call.playerId()).isEqualTo(id);
                assertThat(call.abandonedFaction()).isEqualTo(Faction.SICKLE);
            });
        }

        @Test
        @DisplayName("a negative spike is refused — a spike is a magnitude")
        void negativeSpikeRejected() {
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.EYE, Heat.ZERO, 0));

            assertThatThrownBy(() -> h.service().abandon(id, new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(h.forfeiture().calls()).isEmpty();
        }

        @Test
        @DisplayName("an uncommitted player has no side to abandon")
        void nothingToAbandon() {
            Harness h = harness(null);
            UUID id = UUID.randomUUID();
            h.players().put(player(id, Faction.NONE, Heat.ZERO, 0));

            assertThatThrownBy(() -> h.service().abandon(id, new BigDecimal("5")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no committed faction");
            assertThat(h.reputations().setCalls()).isEmpty();
            assertThat(h.forfeiture().calls()).isEmpty();
        }

        @Test
        @DisplayName("abandoning a missing player is a 404")
        void missingPlayer() {
            Harness h = harness(null);
            assertThatThrownBy(() -> h.service().abandon(UUID.randomUUID(), new BigDecimal("5")))
                    .isInstanceOf(PlayerNotFoundException.class);
        }
    }
}
