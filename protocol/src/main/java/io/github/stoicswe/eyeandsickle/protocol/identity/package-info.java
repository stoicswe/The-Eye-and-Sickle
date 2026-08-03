/**
 * AT Protocol identity resolution: handles to DIDs, DIDs to documents, documents to keys and
 * endpoints.
 *
 * <h2>⚠ Why this is in {@code protocol}, which had a two-item charter</h2>
 *
 * The module charter said this holds "exactly two things" — wire types, and the provenance verifier —
 * and it was amended on 2026-08-02 to admit a third rather than have this package arrive quietly
 * against it. The argument, recorded so it can be argued with:
 *
 * <ul>
 *   <li><strong>The verifier already here is missing exactly this.</strong>
 *       {@code provenance.SigningKeyDirectory} and {@code server.items.DidPublicKeyResolver} both
 *       describe "turn a {@code did:plc:xxx#key1} into a public key" and both currently resolve
 *       nothing (<b>W-1</b>). This package is the implementation of an interface {@code protocol}
 *       already owns, not a new concern.
 *   <li><strong>Both sides need it, and neither may own it.</strong>
 *       {@code docs/architecture/04-item-provenance.md} §6.2 requires the verifier to run
 *       <em>client-side and offline</em>, so the client needs DID resolution; the server needs the
 *       same thing to verify a sign-in ({@code docs/architecture/10} §1). Putting it in either module
 *       means the other copies it, and two SSRF denylists is one denylist that is wrong.
 *   <li><strong>It is not authoritative over anything.</strong> Invariant I14 is about state a
 *       cheater would forge. Nothing here is state — no threshold, no price, no yield, no gate. A
 *       client that resolves a DID wrongly gets a failed signature check, which is the conservative
 *       outcome and the same one the server gets.
 * </ul>
 *
 * <h2>⚠ This package does network I/O, and it is the only one here that may</h2>
 *
 * That is the genuinely new thing — before this, {@code protocol} opened no sockets. It is confined
 * here and {@code ArchitectureRulesTest} enforces the confinement, because the reason to keep this
 * module austere (it is a jlink candidate, and it is shared by two very different runtimes) has not
 * changed. It adds no dependency: {@link java.net.http.HttpClient} and Jackson 3 are already present,
 * and DNS goes through JNDI in {@code java.naming}.
 *
 * <h2>Layering</h2>
 *
 * <pre>
 *   game  ──►  provenance  ──►  crypto  ◄──  channel
 *                  ▲              ▲
 *                  └── identity ──┘
 * </pre>
 *
 * {@code identity} may use {@code crypto} (key decoding) and be used by {@code provenance} (key
 * resolution). It must never depend on {@code game}: resolving who somebody is cannot be allowed to
 * depend on what they own.
 *
 * <h2>The two rules worth reading before changing anything here</h2>
 *
 * <ol>
 *   <li><strong>No method returns an unverified handle.</strong> {@code alsoKnownAs} is self-asserted
 *       and a display name is evidence in this game ({@code docs/design/12-identity-and-social.md}).
 *       {@link io.github.stoicswe.eyeandsickle.protocol.identity.HandleResolver} keeps its one-way
 *       lookup private for that reason.
 *   <li><strong>Every outbound URL is attacker-chosen.</strong> Handles, {@code did:web} hostnames
 *       and PDS endpoints all come from somebody else, so nothing here fetches anything except
 *       through {@link io.github.stoicswe.eyeandsickle.protocol.identity.HardenedHttpClient}.
 * </ol>
 *
 * @see <a href="../../../../../../../../docs/architecture/10-oauth-and-did-resolution.md">architecture/10
 *     — AT Proto OAuth and DID resolution</a>
 */
package io.github.stoicswe.eyeandsickle.protocol.identity;
