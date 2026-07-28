package io.github.stoicswe.eyeandsickle.server.chain;

import io.github.stoicswe.eyeandsickle.protocol.game.ChainHead;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Which chain wins — the pure rule, with no database and no network in it.
 *
 * <h2>⚠ Most work, never most blocks</h2>
 *
 * The folk version of "longest chain" counts blocks. That is wrong in the one case the rule exists for:
 * a fork of many easy blocks can be <em>taller</em> than a fork of fewer hard ones, so a height
 * comparison lets an attacker out-vote honest work by lowering difficulty instead of doing any. Bitcoin
 * compares accumulated difficulty and so does this. {@link ChainHead#height()} is for display only, and
 * the rule below never reads it.
 *
 * <h2>⚠ This picks a head to FETCH. It does not decide what is true.</h2>
 *
 * A head is a claim: numbers a peer asserted. Adopting one means going and getting the blocks and
 * checking them. The distinction is the whole safety of the thing — a server that treated the winner of
 * this comparison as authoritative would let any peer rewrite its ledger by sending a large number.
 *
 * <p>{@code docs/architecture/07-transport-security.md} §6 T-1 marks this project's transport layer as
 * <b>reviewed patterns, unreviewed code</b>, and {@code CLAUDE.md} says not to let it guard a live
 * federation until a cryptographer has read it. So nothing calls this with heads from the network yet:
 * it is exercised locally and by tests, and the fetch is the documented seam in
 * {@code ChainBootstrapService}.
 */
public final class ChainSelection {

    private ChainSelection() {}

    /**
     * The best head among the candidates, or empty if none beats {@code local}.
     *
     * <p>Ties go to the incumbent. Two chains of equal work are equally valid and switching between
     * them on a coin flip would make a server's history depend on the order peers happened to answer
     * in — the same non-determinism as a last-writer-wins conflict rule, which
     * {@code docs/architecture/08} §0 already refused once.
     *
     * @param local this server's own tip, or null if it has no chain yet
     * @param candidates heads other servers claim
     */
    public static Optional<ChainHead> better(ChainHead local, Collection<ChainHead> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        ChainHead best = null;
        for (ChainHead candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            // ⚠ Different genesis means a different currency, not a longer chain. Adopting across that
            // line would not be a reorganisation; it would migrate every balance onto another ledger.
            if (local != null && !local.comparableWith(candidate)) {
                continue;
            }
            if (local != null && candidate.totalWork() <= local.totalWork()) {
                continue;
            }
            if (best == null || candidate.totalWork() > best.totalWork()) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Whether a server with this local head should mint a genesis block.
     *
     * <p>Only when it has no chain at all <em>and</em> nobody else has one it could join. A server that
     * minted a genesis while a peer already had a chain would fork the federation on startup, and the
     * two halves would each be certain they were right.
     */
    public static boolean shouldMintGenesis(ChainHead local, Collection<ChainHead> candidates) {
        if (local != null) {
            return false;
        }
        return candidates == null || candidates.stream().allMatch(Objects::isNull);
    }
}
