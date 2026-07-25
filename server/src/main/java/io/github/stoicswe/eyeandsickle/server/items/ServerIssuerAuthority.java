package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.IssuerAuthority;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import java.util.Objects;

/**
 * The server-side {@link IssuerAuthority}: decides whether the DID a single-issuer record names as its
 * issuer was allowed to issue that event.
 *
 * <p>{@code docs/architecture/04-item-provenance.md} §7 collapses steps 1–2 for a single-issuer record
 * to "the single signature resolves to the authorized issuer DID for that event type", and step 3
 * forbids a chain walk from ever accepting a record signed by an unauthorized issuer. The verifier
 * separately checks that the signing key's DID equals the payload's {@code issuerDid}; this class is
 * asked only whether that named issuer is authorized.
 *
 * <p>The rule enforced is the enforceable core: <strong>the issuer must be a recognized, unflagged
 * server</strong> ({@link ServerRecognition}). That is what makes a cheating server's fabricated items
 * worthless federation-wide — its DID is not recognized, so honest servers refuse its mints regardless
 * of how well-formed they are ({@code docs/architecture/03} §4, Invariant I15).
 *
 * <p><strong>[PROPOSAL] — what this does not yet enforce.</strong> {@link IssuerAuthority} notes that a
 * sharper authority would express "only an item's home server may mint <em>it</em>", which needs an
 * item-to-home mapping the docs leave undefined. Duel outcomes do not pass through here at all — a
 * {@code duel_grant}'s authority is the sampled validator committee, checked against the quorum
 * threshold instead. Both are recorded in this slice's report as undecided.
 */
public final class ServerIssuerAuthority implements IssuerAuthority {

    private final ServerRecognition recognition;

    /**
     * @param recognition decides which server DIDs are recognized issuers
     */
    public ServerIssuerAuthority(ServerRecognition recognition) {
        this.recognition = Objects.requireNonNull(recognition, "recognition");
    }

    @Override
    public boolean isAuthorizedIssuer(ProvenancePayload payload) {
        Objects.requireNonNull(payload, "payload");
        return recognition.recognizesIssuer(payload.issuerDid());
    }
}
