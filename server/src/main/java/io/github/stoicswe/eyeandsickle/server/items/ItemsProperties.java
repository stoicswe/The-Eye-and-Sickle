package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operational knobs of the item-provenance slice.
 *
 * <h2>None of this is a balance value</h2>
 *
 * {@code CLAUDE.md}'s working agreement is that calibrated economy figures live in the system that
 * owns them, not scattered as constants. Everything here is the other kind of number — a clock-skew
 * tolerance, a page size, a default storage location — none of which a player can gain from by having
 * it set one way or another (Invariant I14). They are the "caller's operational judgement" the
 * verifier's {@link io.github.stoicswe.eyeandsickle.protocol.provenance.ChainVerificationContext}
 * explicitly delegates, gathered in one bound class so a self-hoster can tune them without a rebuild.
 *
 * @param maxFutureSkew how far ahead of now a received record's timestamp may sit before it is treated
 *     as implausibly future-dated. It cannot be zero — clocks genuinely drift between self-hosted
 *     servers ({@code docs/architecture/04-item-provenance.md} §2) — and it is not a game rule but an
 *     anti-replay horizon. Defaults to {@value #DEFAULT_MAX_FUTURE_SKEW_SECONDS} seconds.
 * @param historyDefaultLimit the window size when a client asks for an item's history without one —
 *     {@code 20}, the "records N through N+20" figure §6.1 names.
 * @param historyMaxLimit the hard ceiling on a single history page, so a request cannot ask this
 *     server to marshal an entire long chain in one response. A denial-of-service bound, not a rule.
 * @param ingressLandingStorageTier where a freshly ingested foreign item is placed. <strong>[PROPOSAL]
 *     </strong> — {@code docs/architecture/04} does not say which storage tier ({@code
 *     docs/design/01} §6) a transferred-in item lands in; {@code standard_storage} is the cautious
 *     default (exposed only while online), pending a decision logged against {@code docs/design/15}.
 */
@ConfigurationProperties(prefix = "eyeandsickle.items")
public record ItemsProperties(
        Duration maxFutureSkew,
        Integer historyDefaultLimit,
        Integer historyMaxLimit,
        String ingressLandingStorageTier) {

    /** Clock-skew tolerance default; an operational anti-replay horizon, not a balance value. */
    public static final long DEFAULT_MAX_FUTURE_SKEW_SECONDS = 300;

    /** The "records N through N+20" window from {@code docs/architecture/04-item-provenance.md} §6.1. */
    public static final int DEFAULT_HISTORY_LIMIT = 20;

    /** A DoS ceiling on one page; deliberately larger than the default so normal paging is unaffected. */
    public static final int DEFAULT_HISTORY_MAX_LIMIT = 100;

    public ItemsProperties {
        maxFutureSkew = maxFutureSkew == null ? Duration.ofSeconds(DEFAULT_MAX_FUTURE_SKEW_SECONDS) : maxFutureSkew;
        historyDefaultLimit = historyDefaultLimit == null ? DEFAULT_HISTORY_LIMIT : historyDefaultLimit;
        historyMaxLimit = historyMaxLimit == null ? DEFAULT_HISTORY_MAX_LIMIT : historyMaxLimit;
        ingressLandingStorageTier = (ingressLandingStorageTier == null || ingressLandingStorageTier.isBlank())
                ? StorageTier.STANDARD_STORAGE.name()
                : ingressLandingStorageTier;

        if (maxFutureSkew.isNegative()) {
            throw new IllegalArgumentException(
                    "eyeandsickle.items.max-future-skew must not be negative, was " + maxFutureSkew);
        }
        if (historyDefaultLimit <= 0 || historyMaxLimit <= 0) {
            throw new IllegalArgumentException(
                    "history limits must be positive; got default=" + historyDefaultLimit + ", max=" + historyMaxLimit);
        }
        if (historyDefaultLimit > historyMaxLimit) {
            throw new IllegalArgumentException("history default limit " + historyDefaultLimit
                    + " exceeds the max limit " + historyMaxLimit + "; the default must fit within one page");
        }
        // Parsed eagerly so a typo in the configured tier is a startup failure, not a surprise on the
        // first cross-server transfer this server accepts.
        ingressLandingTierOf(ingressLandingStorageTier);
    }

    /**
     * The storage tier a freshly ingested item is placed in, as the protocol enum.
     *
     * @return the parsed tier
     * @throws IllegalArgumentException if the configured value is not a {@link StorageTier} constant
     */
    public StorageTier ingressLandingTier() {
        return ingressLandingTierOf(ingressLandingStorageTier);
    }

    private static StorageTier ingressLandingTierOf(String value) {
        try {
            return StorageTier.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "eyeandsickle.items.ingress-landing-storage-tier='" + value
                            + "' is not a StorageTier; use one of VAULT, STANDARD_STORAGE, HIGH_HACKABLE_ZONE",
                    e);
        }
    }

    /**
     * Clamps a caller-supplied page size into {@code [1, historyMaxLimit]}, substituting the default
     * for an absent one.
     *
     * @param requested the client's {@code limit}, or {@code null} if it did not ask
     * @return a safe page size
     */
    public int clampHistoryLimit(Integer requested) {
        if (requested == null) {
            return historyDefaultLimit;
        }
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, historyMaxLimit);
    }
}
