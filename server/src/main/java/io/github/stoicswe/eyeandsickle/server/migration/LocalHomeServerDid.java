package io.github.stoicswe.eyeandsickle.server.migration;

/**
 * This server's own DID, as it names itself when it originates a migration
 * ({@code docs/architecture/09-player-state-portability.md} §4).
 *
 * <h2>Why a seam and not a direct read of the signing identity</h2>
 *
 * A migration bundle records the DID of the home server releasing the character (§4), so the destination
 * can tie the home binding it advances to a signer. That DID is this server's provenance-issuer DID. Rather
 * than couple the migration services to the whole signing identity, this one-method seam exposes just the
 * DID, and the default {@code MigrationConfiguration} bean reads it from the server's signing identity —
 * which a fake trivially replaces in a unit test.
 */
@FunctionalInterface
public interface LocalHomeServerDid {

    /**
     * @return this server's DID
     * @throws IllegalStateException if this server has no configured DID and therefore cannot originate a
     *     migration — a server that hosts online characters must have one
     */
    String value();
}
