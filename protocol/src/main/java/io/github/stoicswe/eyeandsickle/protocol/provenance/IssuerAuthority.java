package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Decides whether the DID a record names as its issuer was allowed to issue <em>that</em> event.
 *
 * <p>{@code docs/architecture/04-item-provenance.md} §7 requires that a single-issuer record's
 * signature "resolves to the authorized issuer DID for that event type", and that a chain walk never
 * hits "a record signed by an unauthorized issuer". Who is authorized is federation state — which
 * servers are known, which are flagged for equivocation ({@code 05} §3.3), which one is the item's
 * home — and that state is not something this module may hold. So the question is asked of the
 * caller, and the verifier only enforces the answer.
 *
 * <p>Note the verifier separately checks that the signing key's DID equals {@code issuerDid}. This
 * interface is asked only about the issuer named in the payload, so an implementation never has to
 * reason about signatures.
 *
 * <p>Duel outcomes do not come through here: a {@code duel_grant}'s authority is the sampled
 * validator committee, not a single DID, and it is checked against the quorum threshold instead
 * ({@code docs/architecture/05-validator-quorum.md} §1).
 */
@FunctionalInterface
public interface IssuerAuthority {

    /**
     * @param payload the record being verified; both {@code issuerDid} and {@code eventType} matter,
     *     and an implementation may also look at {@code itemId} to enforce that only an item's home
     *     server mints it
     * @return whether that issuer may issue this event
     */
    boolean isAuthorizedIssuer(ProvenancePayload payload);

    /**
     * Authorizes a fixed set of DIDs for every event type.
     *
     * <p>Adequate for a client verifying items from servers it already federates with, and for
     * tests. A home server should implement something sharper: this cannot express "only the minting
     * server may mint", which is the check that actually stops a peer inventing tier-3 tools.
     *
     * @param issuerDids the DIDs to accept
     * @return the authority
     */
    static IssuerAuthority allowing(Collection<String> issuerDids) {
        Set<String> snapshot = Set.copyOf(Objects.requireNonNull(issuerDids, "issuerDids"));
        return payload -> snapshot.contains(payload.issuerDid());
    }
}
