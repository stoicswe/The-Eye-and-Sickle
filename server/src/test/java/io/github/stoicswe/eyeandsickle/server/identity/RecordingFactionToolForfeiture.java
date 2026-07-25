package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A {@link FactionToolForfeiture} that records the forfeitures it was asked to perform, so a test can
 * assert the abandonment transition fires the seam with the side actually being left.
 */
final class RecordingFactionToolForfeiture implements FactionToolForfeiture {

    /** One recorded call to {@link #forfeitFactionTools}. */
    record Call(UUID playerId, Faction abandonedFaction) {}

    private final List<Call> calls = new ArrayList<>();

    @Override
    public void forfeitFactionTools(UUID playerId, Faction abandonedFaction) {
        calls.add(new Call(playerId, abandonedFaction));
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }
}
