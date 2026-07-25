package io.github.stoicswe.eyeandsickle.server.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operational knobs of the character-migration slice
 * ({@code docs/architecture/09-player-state-portability.md} §6).
 *
 * <h2>None of this is a balance value</h2>
 *
 * Following {@code CLAUDE.md}'s working agreement, everything here is operational rather than a
 * calibrated economy figure. The size bounds are denial-of-service ceilings on an untrusted courier's
 * bundle (a destination re-verifies every chain, which is real work); the operator token gates Option B,
 * which moves full state between cooperating operators (§5). A player gains nothing from how any of these
 * is set (Invariant I14).
 *
 * @param maxItems the most items one migration bundle may carry before it is refused unverified;
 *     defaults to {@value #DEFAULT_MAX_ITEMS}
 * @param maxRecordsPerItem the longest provenance chain a single item may present before the bundle is
 *     refused; defaults to {@value #DEFAULT_MAX_RECORDS_PER_ITEM}
 * @param maxBundleBytes the total size, in bytes, of all envelope documents a bundle may carry before it
 *     is refused; defaults to {@value #DEFAULT_MAX_BUNDLE_BYTES}
 * @param operatorToken the shared secret an Option-B (full-state) request must present to prove it is an
 *     operator of this server (§5). <strong>Unset by default, which denies every operator request</strong>
 *     — the safe closed default: full-state transfer stays off until an operator deliberately configures a
 *     credential. Never {@code null} once bound (defaults to the empty string, which the default
 *     authorization treats as "no operator access").
 */
@ConfigurationProperties(prefix = "eyeandsickle.migration")
public record MigrationProperties(
        Integer maxItems, Integer maxRecordsPerItem, Long maxBundleBytes, String operatorToken) {

    /** Default cap on items per bundle; a DoS ceiling, not a rule. */
    public static final int DEFAULT_MAX_ITEMS = 256;

    /** Default cap on a single item's chain length; a DoS ceiling. */
    public static final int DEFAULT_MAX_RECORDS_PER_ITEM = 512;

    /** Default cap on total envelope bytes in a bundle (~8 MiB); a DoS ceiling. */
    public static final long DEFAULT_MAX_BUNDLE_BYTES = 8L * 1024 * 1024;

    public MigrationProperties {
        maxItems = maxItems == null ? DEFAULT_MAX_ITEMS : maxItems;
        maxRecordsPerItem = maxRecordsPerItem == null ? DEFAULT_MAX_RECORDS_PER_ITEM : maxRecordsPerItem;
        maxBundleBytes = maxBundleBytes == null ? DEFAULT_MAX_BUNDLE_BYTES : maxBundleBytes;
        // Empty rather than null so the default authorization can compare without a null check, and so an
        // operator who leaves this unset gets "deny all" rather than a surprise NPE.
        operatorToken = operatorToken == null ? "" : operatorToken;

        if (maxItems <= 0) {
            throw new IllegalArgumentException("eyeandsickle.migration.max-items must be positive, was " + maxItems);
        }
        if (maxRecordsPerItem <= 0) {
            throw new IllegalArgumentException(
                    "eyeandsickle.migration.max-records-per-item must be positive, was " + maxRecordsPerItem);
        }
        if (maxBundleBytes <= 0) {
            throw new IllegalArgumentException(
                    "eyeandsickle.migration.max-bundle-bytes must be positive, was " + maxBundleBytes);
        }
    }

    /**
     * @return whether an operator credential is configured at all; when false, every Option-B request is
     *     denied (the safe default)
     */
    public boolean operatorAccessEnabled() {
        return !operatorToken.isBlank();
    }
}
