package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import java.util.Objects;
import java.util.Optional;

/**
 * The filters an investigator applies to the public ledger — the query behind the gameplay surface of
 * {@code docs/design/01-core-resources.md} §2.2.
 *
 * <p>An investigation runs in both directions — "where did this money go" and "who paid for this" — so
 * the ledger is queryable from either counterparty, and this record captures the ways to narrow it: a
 * subject ({@link #participant()}), optionally the other end of a specific flow ({@link
 * #counterparty()}), a direction, and a transaction type. With none of them set it degrades to the
 * recent-activity feed the ledger view opens on.
 *
 * <h2>What is deliberately not a field here: the viewer</h2>
 *
 * These are the filters a <em>client chooses</em>. Who is <em>asking</em> — the viewer whose own Dead
 * Drops are visible to them and to nobody else — is not one of them. That is bound from the
 * authenticated principal at the edge and passed to {@link LedgerRepository} separately, because a
 * client-chosen viewer would let anyone claim to be a counterparty and read another player's
 * untraceable transfers (Invariant I14). Keeping it out of this record makes that mistake unspellable.
 *
 * @param participant the DID under investigation, matched per {@link #direction()}, or {@code null}
 *     for the global recent feed
 * @param counterparty the other end of a specific flow, or {@code null} to match any counterparty;
 *     matched on either side
 * @param direction which side {@link #participant()} must be on
 * @param type restrict to one transaction type, or {@code null} for all types
 * @param limit the most rows to return; clamped by {@link LedgerRepository} to a sane maximum
 */
public record LedgerQuery(
        String participant, String counterparty, Direction direction, LedgerEntryType type, int limit) {

    /** The default page size when a caller does not specify one. */
    public static final int DEFAULT_LIMIT = 50;

    /** The largest page the repository will return, whatever a caller asks for. A scan bound, not a rule. */
    public static final int MAX_LIMIT = 200;

    public LedgerQuery {
        Objects.requireNonNull(direction, "direction");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
    }

    /** Which side of a transaction the participant must appear on. */
    public enum Direction {

        /** Rows the participant sent (participant = payer). */
        SENT,

        /** Rows the participant received (participant = payee). */
        RECEIVED,

        /** Rows the participant is on either side of. */
        EITHER
    }

    /**
     * The recent-activity feed: latest rows, any party.
     *
     * @param limit the page size
     * @return the query
     */
    public static LedgerQuery recent(int limit) {
        return new LedgerQuery(null, null, Direction.EITHER, null, limit);
    }

    /**
     * Everything involving one DID, in a given direction.
     *
     * @param participant the subject DID
     * @param direction which side it must be on
     * @param limit the page size
     * @return the query
     */
    public static LedgerQuery forParticipant(String participant, Direction direction, int limit) {
        return new LedgerQuery(Objects.requireNonNull(participant, "participant"), null, direction, null, limit);
    }

    /**
     * The flow between two DIDs, in either direction between them.
     *
     * @param participant one party
     * @param counterparty the other party
     * @param limit the page size
     * @return the query
     */
    public static LedgerQuery between(String participant, String counterparty, int limit) {
        return new LedgerQuery(
                Objects.requireNonNull(participant, "participant"),
                Objects.requireNonNull(counterparty, "counterparty"),
                Direction.EITHER,
                null,
                limit);
    }

    /**
     * A copy restricted to one transaction type.
     *
     * @param restrictedType the type to keep
     * @return a new query with the type filter applied
     */
    public LedgerQuery ofType(LedgerEntryType restrictedType) {
        return new LedgerQuery(participant, counterparty, direction, restrictedType, limit);
    }

    /** @return the participant, if any */
    public Optional<String> participantDid() {
        return Optional.ofNullable(participant);
    }

    /** @return the counterparty, if any */
    public Optional<String> counterpartyDid() {
        return Optional.ofNullable(counterparty);
    }

    /** @return the type filter, if any */
    public Optional<LedgerEntryType> typeFilter() {
        return Optional.ofNullable(type);
    }
}
