package io.github.stoicswe.eyeandsickle.server.items;

import java.util.Objects;
import java.util.Set;

/**
 * Decides whether a server DID is a recognized issuer — known to the federation and not flagged.
 *
 * <h2>The seam, and why the default is "only me"</h2>
 *
 * {@code docs/architecture/04-item-provenance.md} §7 says a single-issuer record must be signed by "the
 * authorized issuer DID for that event type", and a chain walk must never accept "a record signed by an
 * unauthorized issuer". <em>Which</em> servers are authorized is federation state — who is in the
 * directory, who has been flagged for equivocation ({@code docs/architecture/03} §4), which server is
 * an item's home — and that state belongs to the federation slice, not this one.
 *
 * <p>So recognition is a seam. Its default, {@link #selfOnly(String)}, recognizes only this server's
 * own DID. That is exactly right for a non-federating server: it trusts the items it minted itself and
 * nothing it cannot yet resolve. Turning federation on replaces this with a directory-backed
 * implementation that additionally recognizes known, non-flagged peers — at which point a cheating
 * server's fabricated items become worthless precisely because it is <em>not</em> recognized here
 * (Invariant I15, {@code 03} §4).
 *
 * <p><strong>[PROPOSAL].</strong> This encodes the enforceable core — the issuer must be a recognized,
 * unflagged server — but not the finer rule that {@code IssuerAuthority} hints at, "only an item's
 * <em>home</em> server may mint it". That needs an item-to-home-server mapping the docs do not define;
 * it is listed as undecided so the integrator can log it against {@code docs/design/15}.
 */
@FunctionalInterface
public interface ServerRecognition {

    /**
     * @param serverDid the DID a record names as its issuer
     * @return whether that server may issue records this server will recognize
     */
    boolean recognizesIssuer(String serverDid);

    /**
     * Recognizes exactly one DID — this server's own.
     *
     * @param ownDid this server's DID, or {@code null} if signing is unconfigured (in which case
     *     nothing is recognized, and only externally supplied items — verified against a real
     *     directory — are ever accepted)
     * @return the recognition policy
     */
    static ServerRecognition selfOnly(String ownDid) {
        return serverDid -> ownDid != null && ownDid.equals(serverDid);
    }

    /**
     * Recognizes a fixed set of DIDs. Useful for a small friends-federation configured by hand, and for
     * tests.
     *
     * @param recognizedDids the DIDs to accept
     * @return the recognition policy
     */
    static ServerRecognition of(Set<String> recognizedDids) {
        Set<String> snapshot = Set.copyOf(Objects.requireNonNull(recognizedDids, "recognizedDids"));
        return snapshot::contains;
    }
}
