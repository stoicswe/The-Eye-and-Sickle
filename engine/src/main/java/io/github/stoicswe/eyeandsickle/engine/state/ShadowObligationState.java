package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * Something owed after a Shadow Market trade.
 *
 * <h2>⚠ This record is the ONLY thing standing behind a send-later trade</h2>
 *
 * There is no escrow on this market. The money moved when the buyer committed, so nothing can be
 * unwound — all that exists is this row, a deadline, and the reputation cost of blowing through it.
 * If this is ever made refundable, the delivery modes collapse into one and the whole risk decision
 * disappears with them.
 */
public final class ShadowObligationState {

    public String obligationId = UUID.randomUUID().toString();

    public String itemType = "";

    public int quantity = 1;

    /**
     * What the buyer paid.
     *
     * <p>⚠ Initialised, never null — the money-field rule this codebase learned when
     * {@code ContributionState.creditedWei} threw an NPE on the login screen.
     */
    public BigInteger paidWei = BigInteger.ZERO;

    public String counterpartyHandle = "";

    /** True when the player is the one who has to deliver. */
    public boolean owedByMe = true;

    public Instant incurredAt = Instant.EPOCH;

    public Instant dueAt = Instant.EPOCH;

    /**
     * Whether the reputation consequence has already been applied.
     *
     * <p>⚠ Needed because the tick runs every second and an overdue obligation stays overdue. Without
     * it a seller who missed a deadline would be penalised once per tick until they noticed, which is
     * a slow-motion account deletion rather than a consequence.
     */
    public boolean settled = false;

    public ShadowObligationState() {}
}
