package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

/**
 * Something owed after a trade, and when it stops being forgivable.
 *
 * <h2>⚠ It exists because there is no escrow</h2>
 *
 * With escrow, an unfulfilled trade unwinds itself. Without it, the money has already moved and the
 * only thing left is an obligation and a deadline — so this record <em>is</em> the market's
 * enforcement mechanism, and the reputation hit at {@link #dueAt} is the only consequence there is.
 *
 * @param obligationId what fulfil names
 * @param itemType what is owed
 * @param displayName what to call it
 * @param quantity how many
 * @param paidWei what the buyer already handed over — ⚠ shown because it is gone either way
 * @param counterpartyHandle the other side
 * @param owedByMe whether the player is the one who has to act
 * @param incurredAt when the trade happened
 * @param dueAt when defaulting starts costing reputation
 * @param asOf the session's clock, so a countdown is measured against the game's time and not the
 *     wall clock — the standing rule for anything with a deadline
 */
public record ShadowObligation(
        String obligationId,
        String itemType,
        String displayName,
        int quantity,
        BigInteger paidWei,
        String counterpartyHandle,
        boolean owedByMe,
        Instant incurredAt,
        Instant dueAt,
        Instant asOf) {

    /** @return how long is left, never negative. */
    public Duration remaining() {
        Duration left = Duration.between(asOf, dueAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** @return whether the window has closed. */
    public boolean overdue() {
        return !asOf.isBefore(dueAt);
    }
}
