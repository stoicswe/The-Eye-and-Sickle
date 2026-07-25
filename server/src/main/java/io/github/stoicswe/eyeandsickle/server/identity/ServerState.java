package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Instant;
import java.util.Objects;

/**
 * The single row that describes this home server itself ({@code server_state}).
 *
 * <p>It holds <strong>server heat</strong> ({@code docs/design/01-core-resources.md} §4.2) — the
 * population-wide Eye attention that drives institutional escalation affecting everyone — and this
 * server's own DID, which is the issuer on every provenance record it signs
 * ({@code docs/architecture/04-item-provenance.md} §2).
 *
 * <p>The identity slice <em>reads</em> this to report server heat alongside a player's profile and to
 * attribute operator actions to the server's DID. It does not drive server heat: that reading is
 * maintained by the mining/heat systems as Sickle activity accrues across the population, and mutating
 * it is deliberately outside this slice. The DID is nullable because it is unknown at migration time
 * and is set before the first mint, not at install.
 *
 * @param serverDid this server's own DID, or {@code null} before it has been provisioned
 * @param serverHeat population-wide Eye attention (§4.2); distinct from any player's personal heat
 * @param heatUpdatedAt when server heat was last recalculated
 * @param rowVersion optimistic-concurrency guard
 */
public record ServerState(Did serverDid, Heat serverHeat, Instant heatUpdatedAt, long rowVersion) {

    public ServerState {
        Objects.requireNonNull(serverHeat, "serverHeat");
        Objects.requireNonNull(heatUpdatedAt, "heatUpdatedAt");
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion is never negative, was " + rowVersion);
        }
    }
}
