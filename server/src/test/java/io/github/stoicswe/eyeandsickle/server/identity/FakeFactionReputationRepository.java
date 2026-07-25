package io.github.stoicswe.eyeandsickle.server.identity;

import static org.mockito.Mockito.mock;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Records the standing writes {@link FactionService} makes, so a test can assert that abandonment resets
 * exactly the side being left and no other.
 *
 * <p>{@code Faction.NONE} is rejected here as the real repository rejects it, so a test cannot pass an
 * invalid target through the fake and get a false green.
 */
final class FakeFactionReputationRepository extends FactionReputationRepository {

    /** One recorded absolute-set of a standing. */
    record SetStanding(UUID playerId, Faction faction, long standing, Instant now) {}

    /** One recorded relative adjustment of a standing. */
    record AdjustStanding(UUID playerId, Faction faction, long delta, Instant now) {}

    private final List<SetStanding> setCalls = new ArrayList<>();
    private final List<AdjustStanding> adjustCalls = new ArrayList<>();

    FakeFactionReputationRepository() {
        super(mock(JdbcClient.class));
    }

    @Override
    public void setStanding(UUID playerId, Faction faction, long standing, Instant now) {
        requireNamed(faction);
        setCalls.add(new SetStanding(playerId, faction, standing, now));
    }

    @Override
    public void adjustStanding(UUID playerId, Faction faction, long delta, Instant now) {
        requireNamed(faction);
        adjustCalls.add(new AdjustStanding(playerId, faction, delta, now));
    }

    private static void requireNamed(Faction faction) {
        if (faction == Faction.NONE) {
            throw new IllegalArgumentException("Faction.NONE is not a target for standing");
        }
    }

    List<SetStanding> setCalls() {
        return List.copyOf(setCalls);
    }

    List<AdjustStanding> adjustCalls() {
        return List.copyOf(adjustCalls);
    }
}
