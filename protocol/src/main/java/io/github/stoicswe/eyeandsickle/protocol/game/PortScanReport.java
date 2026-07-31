package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;

/**
 * What a port scan came back with.
 *
 * <h2>⚠ Every field below the scan's depth is UNKNOWN, and unknown is not zero</h2>
 *
 * A scan that stopped at {@link PortScanTarget#OS_VERSION} knows nothing about the target's vault,
 * and the readout must say so rather than print a confident {@code 0}. That is why the counts are
 * boxed as {@code -1} sentinels behind {@link #knows} rather than left as bare numbers: a panel that
 * renders "high-risk vault: 0" for a scan that never looked has told the player something false about
 * a machine they are deciding whether to rob.
 *
 * <h2>⚠ The medium-vault figure is an ESTIMATE and carries its own error</h2>
 *
 * {@link #vaultMediumEstimate} is a midpoint and {@link #vaultMediumError} is the half-width around
 * it, so the honest reading is "somewhere between the two". A deeper scan narrows the band; nothing
 * ever closes it, because the middle tier is not readable from outside — that is what
 * {@code docs/design/01-core-resources.md} §6 buys with the tier. Reporting it as a count would make
 * the tier system a formality.
 *
 * @param address the machine scanned
 * @param requested what the player asked to learn — the deepest rung paid for
 * @param at when the snapshot was taken; {@link #cyclesUsed} is only true as of this instant
 * @param detected whether the target noticed
 * @param blocked whether the target refused the scan outright, in which case the findings are partial
 * @param firewallTier 1–3, or -1 when unknown
 * @param osName the OS and version string, or empty when unknown
 * @param cyclesTotal the machine's capability, or -1
 * @param cyclesUsed what was busy at {@link #at}, or -1
 * @param downloadsBytes how much is in the download folder, or -1
 * @param vaultHighCount items in the exposed tier, or -1
 * @param vaultMediumEstimate midpoint of the middle tier's contents, or -1
 * @param vaultMediumError half-width of the band around it; 0 only when nothing was estimated
 * @param note what happened, in words — always present, and the only field on a blocked scan
 */
public record PortScanReport(
        String address,
        PortScanTarget requested,
        Instant at,
        boolean detected,
        boolean blocked,
        int firewallTier,
        String osName,
        long cyclesTotal,
        long cyclesUsed,
        long downloadsBytes,
        int vaultHighCount,
        int vaultMediumEstimate,
        int vaultMediumError,
        String note) {

    /** Whether this scan actually answered {@code target}. */
    public boolean knows(PortScanTarget target) {
        if (requested == null || !target.reachedBy(requested)) {
            return false;
        }
        return switch (target) {
            case FIREWALL -> firewallTier >= 0;
            case OS_VERSION -> osName != null && !osName.isBlank();
            case CYCLE_CAPABILITY -> cyclesTotal >= 0;
            case CYCLE_LOAD -> cyclesUsed >= 0;
            case DOWNLOADS -> downloadsBytes >= 0;
            case VAULT_HIGH -> vaultHighCount >= 0;
            case VAULT_MEDIUM -> vaultMediumEstimate >= 0;
        };
    }

    /** Cycles that were free at {@link #at}, or -1 when the load was never measured. */
    public long cyclesFree() {
        return cyclesTotal < 0 || cyclesUsed < 0 ? -1L : Math.max(0L, cyclesTotal - cyclesUsed);
    }

    /** The low end of the medium-vault band. */
    public int vaultMediumLow() {
        return vaultMediumEstimate < 0 ? -1 : Math.max(0, vaultMediumEstimate - vaultMediumError);
    }

    /** The high end. */
    public int vaultMediumHigh() {
        return vaultMediumEstimate < 0 ? -1 : vaultMediumEstimate + vaultMediumError;
    }

    /** A scan that was refused before it learned anything worth reporting. */
    public static PortScanReport refused(String address, PortScanTarget target, Instant at, String why) {
        return new PortScanReport(address, target, at, true, true, -1, "", -1L, -1L, -1L, -1, -1, 0, why);
    }
}
