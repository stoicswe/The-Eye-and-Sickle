package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * The seam between this authoritative server and AT Protocol OAuth — the one place a network round-trip
 * to a PDS would happen, isolated so nothing else in the slice depends on it.
 *
 * <h2>Authentication only (Invariant I14, {@code docs/architecture/02-identity-and-auth.md} §3)</h2>
 *
 * An implementation authenticates a player and returns their DID. That is the <em>entire</em> contract.
 * It must never request write scope on the player's account, and it must never read or write game state
 * to the player's PDS — the player's vault living on infrastructure they control is the self-hosted
 * cheating problem relocated one layer down, and it is explicitly rejected. The DID that comes back is
 * an identity and nothing more; everything the player owns hangs off that DID here, in this server's
 * Postgres.
 *
 * <h2>Why this is an interface</h2>
 *
 * A production implementation needs a live PDS to talk to. AT Proto mandates pushed authorization
 * requests (PAR), PKCE and DPoP-bound tokens, and it identifies clients by a <em>client-ID metadata
 * document</em> — the {@code client_id} is the HTTPS URL the metadata is fetched from, and the
 * authorization server fetches it. ⚠ There is <strong>no dynamic client registration</strong>; this
 * javadoc claimed there was until 2026-08-02, corrected against
 * {@code docs/architecture/10-oauth-and-did-resolution.md} §3.1.
 *
 * <p>⚠ <strong>Under the decision recorded in {@code 10} §1 (Option C, 2026-08-02) the code exchange
 * does not happen here.</strong> The desktop client is the OAuth client; this server's job is to
 * verify the resulting identity <em>independently</em>, over a service-auth JWT checked against the
 * {@code #atproto} verification method in a DID document this server resolves itself. So an
 * implementation of this interface verifies a signature — it does not complete a handshake.
 *
 * <p>Putting that behind this interface means the authoritative half of sign-in — the
 * allowlist gate, create-on-first-sign-in, sessions — is written, tested and reviewable without a PDS
 * in the loop, and a real provider can be dropped in without touching any of it. It also means the
 * build and the test suite never depend on the network.
 *
 * <p><strong>Status of the shipped implementations:</strong> {@link DevAtProtoIdentityProvider} is the
 * only one in this slice, and it does <em>not</em> perform a real handshake — it trusts a claimed DID
 * and is disabled by default. A production provider that actually verifies control of the DID is
 * required before this server accepts real players; that work is unfinished and is called out as such.
 */
public interface AtProtoIdentityProvider {

    /**
     * Authenticates a sign-in attempt and returns the verified identity.
     *
     * <p>An implementation validates whatever subset of {@code credentials} its flow uses, proves the
     * caller controls the resulting DID, and returns it. It must reject rather than guess: a return
     * value is a claim that authentication <em>succeeded</em>, so a provider that cannot verify must
     * throw.
     *
     * @param credentials the untrusted client inputs
     * @return the authenticated identity — a DID the caller has proven control of
     * @throws SignInException if authentication cannot be completed or the provider is not available
     */
    ResolvedIdentity authenticate(SignInCredentials credentials);
}
