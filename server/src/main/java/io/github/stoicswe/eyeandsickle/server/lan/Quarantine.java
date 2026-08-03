package io.github.stoicswe.eyeandsickle.server.lan;

import io.github.stoicswe.eyeandsickle.server.identity.Did;

/**
 * The one-way valve between LAN state and federated state.
 *
 * <h2>⚠ Both directions, and the inbound one is the one that gets forgotten</h2>
 *
 * {@code docs/architecture/12-lan-mode.md} §1. Refusing to <em>export</em> LAN state is the obvious
 * half — a UUID nobody can verify must not be able to mint items the federation accepts.
 *
 * <p>Refusing to <em>import</em> matters just as much and is less obvious: a player who could carry a
 * federated item onto a LAN server would suffer real losses adjudicated by a machine with no
 * accountability, and if they could carry it back, the LAN server is an item duplicator.
 *
 * <h2>Why a prefix check and not a column</h2>
 *
 * A LAN identity is {@code did:easlan:<uuid>} ({@link LanIdentity}), so the quarantine travels with the
 * identity itself. A {@code federable} column would be a second source of truth that can be wrong,
 * stale, or forgotten on an INSERT; a prefix cannot.
 */
public final class Quarantine {

    private Quarantine() {}

    /**
     * Refuses if a DID may not cross into federated state.
     *
     * @param did the identity being moved, exported, or adjudicated across servers
     * @throws QuarantinedException if it is a LAN identity
     */
    public static void refuseIfLan(Did did) {
        if (LanIdentity.isLanIdentity(did)) {
            throw new QuarantinedException(
                    "This is a LAN character. Nothing created on a LAN server can move to a federated one: its "
                            + "identity has no proof behind it, its items have no verifiable provenance, and its "
                            + "outcomes were decided by one machine with no quorum. See "
                            + "docs/architecture/12-lan-mode.md section 1.");
        }
    }

    /** Raised when LAN state was asked to cross a boundary it may not. */
    public static class QuarantinedException extends RuntimeException {
        public QuarantinedException(String message) {
            super(message);
        }
    }
}
