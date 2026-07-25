package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.util.Map;
import java.util.Objects;

/**
 * Supplies the sampling record for a duel, so the verifier can check a {@code duel_grant} without
 * doing I/O.
 *
 * <p>A duel outcome's {@code issuerDid} is the synthetic identifier {@code duel:<duelId>} ({@code
 * docs/architecture/04-item-provenance.md} §3.1); the verifier pulls the {@code duelId} out of it and
 * asks here who was sampled. The caller fetches that from its own store, or from the bundle a peer
 * sent along with the item's history.
 *
 * <p>Returning {@code null} means "I have no sampling record for this duel", and that is a rejection,
 * not a pass. Without the committee there is no way to tell a real quorum from five keys an attacker
 * generated, so an unknown duel is exactly as unrecognizable as a forged one.
 */
@FunctionalInterface
public interface DuelCommitteeLookup {

    /**
     * @param duelId the identifier after the {@code duel:} prefix
     * @return the sampled committee, or {@code null} if unknown
     */
    QuorumCommittee committeeFor(String duelId);

    /**
     * A lookup backed by a fixed set of sampling records.
     *
     * @param committees committees indexed by duel id
     * @return the lookup
     */
    static DuelCommitteeLookup ofMap(Map<String, QuorumCommittee> committees) {
        Map<String, QuorumCommittee> snapshot = Map.copyOf(Objects.requireNonNull(committees, "committees"));
        return snapshot::get;
    }

    /**
     * A lookup that knows no duels — correct for verifying an item that has never been fought over,
     * and correctly fatal for one that has.
     *
     * @return the lookup
     */
    static DuelCommitteeLookup none() {
        return duelId -> null;
    }
}
