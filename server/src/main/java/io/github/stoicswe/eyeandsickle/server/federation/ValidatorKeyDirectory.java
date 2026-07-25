package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;

/**
 * Resolves a validator signature's {@code kid} to its public key — the one dependency the quorum has
 * on the identity slice, expressed as a narrow seam this package owns.
 *
 * <p>Adjudication must verify that a vote's signature is really the validator's ({@code
 * docs/architecture/05-validator-quorum.md} §5 step 3, §3.3), and that means resolving a DID key.
 * <em>How</em> a DID resolves to a key is {@code docs/architecture/02-identity-and-auth.md}'s
 * concern, not this package's — validators are AT Protocol DID-identified servers, and key resolution
 * belongs to whoever owns identity. This interface is the boundary: the federation slice depends on
 * the capability, the identity slice supplies it.
 *
 * <p>Extending protocol {@link SigningKeyDirectory} means the same value flows unchanged into the
 * offline chain verifier — the server and a re-verifying client resolve keys through one contract.
 *
 * <h2>Default until identity lands</h2>
 *
 * {@code FederationConfiguration} registers an empty implementation that resolves nothing, so the
 * context wires cleanly before the identity slice exists. With it in place every REST-submitted
 * signature is unverifiable and no duel can resolve over HTTP — deliberately: an unverifiable vote is
 * safer refused than trusted. Tests and the identity slice inject a directory that actually resolves
 * keys. This is a wiring seam, listed in {@code undecidedByDocs}.
 */
public interface ValidatorKeyDirectory extends SigningKeyDirectory {}
