package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The placeholder {@link FactionToolForfeiture}: it records that forfeiture <em>should</em> happen and
 * does nothing else.
 *
 * <h2>[PROPOSAL] — a deliberate stub, not a decision</h2>
 *
 * The identity slice owns the abandonment transition but not the items it would forfeit
 * ({@link FactionToolForfeiture}). Until the item system supplies a real implementation, this bean lets
 * the transition run end to end without silently pretending forfeiture is a no-op forever — it logs at
 * {@code WARN} so the gap is visible in a running server's logs rather than only in the code. Replacing
 * this bean with one that acts on {@code items} is the whole of the remaining work.
 */
@org.springframework.stereotype.Component
public final class NoOpFactionToolForfeiture implements FactionToolForfeiture {

    private static final Logger log = LoggerFactory.getLogger(NoOpFactionToolForfeiture.class);

    @Override
    public void forfeitFactionTools(UUID playerId, Faction abandonedFaction) {
        // Visible on purpose: a real deployment that reaches this line is running with forfeiture
        // unimplemented, which is a design gap the operator and the item-system author should both see.
        log.warn(
                "Faction-tool forfeiture is not implemented: player {} abandoned {} but no tools were forfeited "
                        + "(docs/design/01-core-resources.md §5; awaiting the item system). [PROPOSAL]",
                playerId,
                abandonedFaction);
    }
}
