package io.github.stoicswe.eyeandsickle.server.federation.sampling;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The frozen sampling record for one duel: which validators were drawn and what each one's vote
 * weighs — the evidence {@code docs/architecture/04-item-provenance.md} §7 step 1 is checked against.
 *
 * <p>This is the server-side, storable form of a draw. It carries the full {@link SampledValidator}
 * for each member (DID plus the reputation and uptime the weight was derived from), which is what
 * persists to {@code duels.sampled_validators} and what a peer re-verifies the committee from. The
 * protocol {@link QuorumCommittee} is the leaner projection the threshold arithmetic needs — DID to
 * weight — and {@link #toQuorumCommittee()} produces it, so the persisted evidence and the value the
 * verifier judges are derived from one source rather than assembled twice.
 *
 * @param duelId the duel this committee was sampled for; the provenance {@code issuerDid} is {@code
 *     duel:<duelId>}
 * @param members the drawn validators, in the order the sampler returned them
 */
public record SampledCommittee(String duelId, List<SampledValidator> members) {

    public SampledCommittee {
        Objects.requireNonNull(duelId, "duelId");
        Objects.requireNonNull(members, "members");
        if (members.isEmpty()) {
            throw new IllegalArgumentException("A sampled committee has at least one validator");
        }
        members = List.copyOf(members);
    }

    /** How many validators were drawn — {@code N}, or fewer if the eligible pool was smaller. */
    public int size() {
        return members.size();
    }

    /**
     * Projects to the protocol committee the BFT threshold is computed over.
     *
     * @return a {@link QuorumCommittee} mapping each member DID to its frozen {@code reputation ×
     *     uptime} weight
     */
    public QuorumCommittee toQuorumCommittee() {
        // LinkedHashMap to keep the sampler's order, which makes a logged committee read the same as
        // the stored one. QuorumCommittee copies it defensively, so this map is not shared.
        Map<String, Double> weights = new LinkedHashMap<>();
        for (SampledValidator member : members) {
            weights.put(member.validatorDid(), member.weight());
        }
        return new QuorumCommittee(duelId, weights);
    }

    /**
     * The committee as a list of plain maps, ready to serialize into the {@code duels
     * .sampled_validators} jsonb array.
     *
     * <p>Each entry records {@code did}, {@code reputation}, {@code uptime} and the derived {@code
     * weight}. The weight is stored even though it is recomputable, so a reader — including a
     * federated peer auditing the draw — sees the number the committee was actually judged by without
     * having to trust that it re-multiplies the factors the same way.
     *
     * @return the array form, one map per member
     */
    public List<Map<String, Object>> toJsonArray() {
        List<Map<String, Object>> array = new ArrayList<>(members.size());
        for (SampledValidator member : members) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("did", member.validatorDid());
            entry.put("reputation", member.reputation());
            entry.put("uptime", member.uptime());
            entry.put("weight", member.weight());
            array.add(entry);
        }
        return array;
    }
}
