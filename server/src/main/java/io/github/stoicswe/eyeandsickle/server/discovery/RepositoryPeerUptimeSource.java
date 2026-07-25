package io.github.stoicswe.eyeandsickle.server.discovery;

import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The {@link PeerUptimeSource} backed by the {@code federation_peers} contact counters.
 *
 * <p>A peer with no contact history yet reports {@link #RATIO_WHEN_NO_DATA}, a neutral 0.5: reporting 0
 * would bury a freshly-announced peer that has simply not been probed yet, and reporting 1 would
 * flatter a peer that has never once answered. Neither is honest about "unknown", so a midpoint stands
 * in until real data replaces it — the consuming quorum policy ({@code
 * docs/architecture/05-validator-quorum.md} §2.5) already has its own cold-start floor for newcomers,
 * so this only has to avoid actively misleading it.
 */
@Component
public class RepositoryPeerUptimeSource implements PeerUptimeSource {

    /** The success ratio reported for a peer with no contact history. Neutral, neither buried nor flattered. */
    public static final double RATIO_WHEN_NO_DATA = 0.5;

    private final FederationPeerRepository repository;

    RepositoryPeerUptimeSource(FederationPeerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<PeerLiveness> livenessOf(String peerDid) {
        return repository.findByDid(peerDid).map(record -> PeerLiveness.of(record, RATIO_WHEN_NO_DATA));
    }
}
