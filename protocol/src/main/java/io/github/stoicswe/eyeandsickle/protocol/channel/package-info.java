/**
 * DID-authenticated encrypted transport between clients and servers, and between servers.
 *
 * <p>Full design and rationale: {@code docs/architecture/07-transport-security.md} (⚠ tagged
 * {@code [PROPOSAL]} — neither technology chat covered transport security, so this fills a gap
 * rather than implementing a decision).
 *
 * <h2>What this is for</h2>
 *
 * Four properties, in the order they matter here:
 *
 * <ol>
 *   <li><strong>Integrity</strong> — nothing in flight can be altered without detection.
 *   <li><strong>Authenticity</strong> — each end knows which <em>DID</em> is at the other end.
 *   <li><strong>Replay resistance</strong> — a captured frame cannot be resent.
 *   <li><strong>Confidentiality</strong> — last, deliberately: an attacker who can alter an item
 *       transfer is a far worse problem than one who can merely watch one.
 * </ol>
 *
 * <h2>Two links, two different meanings of "end to end"</h2>
 *
 * <ul>
 *   <li><strong>Server to server: genuinely end-to-end.</strong> Federation traffic crosses
 *       infrastructure neither peer controls — the directory is explicitly a low-trust index, not an
 *       authority. Sealing to the peer's key means none of it can read or alter the contents.
 *   <li><strong>Client to server: an authenticated, encrypted session, not end-to-end encryption.</strong>
 *       The server <em>is</em> the far end and must read game state to be authoritative (Invariant
 *       I14). There is no third party to hide plaintext from, and hiding it from the server would
 *       break the game rather than secure it. What this link gets is every property above against
 *       everyone <em>except</em> the endpoint that is supposed to read the data.
 * </ul>
 *
 * <h2>Run it inside TLS</h2>
 *
 * TLS 1.3 stays mandatory; this is not a replacement. What it adds is what TLS cannot give here:
 * identity anchored in <strong>DIDs</strong> rather than hostnames and certificate authorities — the
 * same anchor the allowlist, provenance and validator reputation already use — and protection that
 * survives past a self-hoster's reverse proxy, where TLS has already terminated.
 *
 * <h2>The line not to cross</h2>
 *
 * An authenticated channel proves <em>who</em> sent a message and that it arrived unaltered. It
 * proves nothing about whether the contents are <em>true</em>. A cheating client can hold a flawless
 * channel and send flawlessly authenticated lies. Invariant I14 is untouched: the server still
 * validates everything. Any future code that reads "it came over the secure channel" as "therefore
 * it is true" is a bug.
 *
 * @see io.github.stoicswe.eyeandsickle.protocol.channel.SecureHandshake
 * @see io.github.stoicswe.eyeandsickle.protocol.channel.SecureChannel
 */
package io.github.stoicswe.eyeandsickle.protocol.channel;
