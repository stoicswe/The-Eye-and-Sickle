package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * A pool a rig can point its cycles at.
 *
 * <h2>Two numbers, and they pull against each other</h2>
 *
 * A pool is a trade between <b>fee</b> (which is expected income, directly) and <b>steadiness</b>
 * (which is variance). Nothing here is strictly better than everything else: the cheapest pool on
 * the list is a small {@link PoolScheme#PPLNS} operation that pays you roughly as erratically as
 * mining alone, and the steadiest is the one that charges most for it.
 *
 * @param id the stable identifier a command takes
 * @param name the display name
 * @param scheme how it decides what it owes
 * @param feeBasisPoints its cut, in hundredths of a percent
 * @param networkShare its share of the whole chain's hashrate, 0–1
 * @param shareSeconds how often its share target is tuned to pay, under {@link PoolScheme#PPS}
 * @param blurb one line of what kind of operation it is
 * @param caution a warning worth reading before joining, or {@code ""}
 */
public record MiningPool(
        String id,
        String name,
        PoolScheme scheme,
        int feeBasisPoints,
        double networkShare,
        double shareSeconds,
        String blurb,
        String caution) {

    /** The fee as a fraction, for arithmetic. */
    public double fee() {
        return feeBasisPoints / 10_000.0d;
    }

    /** {@code "2.00%"}. */
    public String feeText() {
        return String.format(java.util.Locale.ROOT, "%.2f%%", feeBasisPoints / 100.0d);
    }

    /** {@code "24%"} — its share of the chain. */
    public String shareText() {
        return String.format(java.util.Locale.ROOT, "%.0f%%", networkShare * 100);
    }
}
