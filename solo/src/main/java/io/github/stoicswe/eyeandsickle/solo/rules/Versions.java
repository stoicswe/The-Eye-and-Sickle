package io.github.stoicswe.eyeandsickle.solo.rules;

import java.math.BigInteger;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;

/**
 * Which build of a tool a given machine is carrying, and what that is worth.
 *
 * <h2>What a version is for</h2>
 *
 * Before this existed, every upgrade sitting in somebody's {@code Contents/Upgrades} was
 * interchangeable with every other copy of the same tool: a player who already owned the Wide Sweep
 * had no reason to look at another one, and no reason to prefer a hard target over an easy one when
 * both carried the same item. A version makes the same tool on two different machines two different
 * propositions.
 *
 * <h2>⚠ A newer build is worth more and supersedes an older one. It is NOT a better tool.</h2>
 *
 * See {@link UpgradeVersion} for the full argument. In short: a capability that rises with the
 * hardness of the machine you take it off is a ceiling with no gate on it, reachable by grinding —
 * Invariant <b>I2</b> arriving from an unexpected direction, and <b>I3</b> broken as well because the
 * item would then sit behind its catalogue gate <em>and</em> a raiding ladder. Nothing here touches
 * {@code equippedCycles}, detection probability, hop reach or any other capability figure, and
 * {@code UpgradeVersionTest} asserts that a version never reaches one.
 *
 * <h2>Where the number comes from</h2>
 *
 * Deterministic from the item and the host, never drawn. The same machine carries the same build
 * every time you look at it, which is what makes "was this here before?" and "is theirs newer than
 * mine?" answerable at all — and it is the same rule {@code VirtualFs.upgradeBytes} already follows
 * for the same reason.
 *
 * <p>⚠ <b>The major number tracks the HOST's tier, and that is the whole reward loop.</b> A tier-5
 * machine runs newer software than a tier-1 desktop, which is both true of real estates and exactly
 * the incentive this feature exists to create: raid harder targets to find newer builds. The minor
 * number is scattered off the item and address so two tier-3 machines are not carrying identical
 * copies.
 */
public final class Versions {

    private Versions() {}

    /**
     * The build a host of the given tier carries.
     *
     * <p>⚠ Tier drives the major, so the ladder is legible: a player who notices that the good stuff
     * sits on hard machines has learned the rule without being told it. The offset means a tier-1
     * machine is on {@code v1.x} rather than {@code v0.x} — a zero major reads as a prerelease, and
     * nothing in this world ships one.
     */
    public static UpgradeVersion on(String itemType, String address, int hostTier) {
        int tier = Math.max(1, Math.min(5, hostTier));
        long scatter = Math.abs((long) (String.valueOf(itemType) + "@" + address).hashCode());
        // ⚠ Minor is bounded well below 10 on purpose. Two-digit minors would invite exactly the
        // lexical comparison UpgradeVersion exists to prevent anybody attempting.
        return new UpgradeVersion(tier, (int) (scatter % 9L));
    }

    /** What the player currently holds of this tool, or {@link UpgradeVersion#UNKNOWN}. */
    public static UpgradeVersion owned(SoloSave save, String itemType) {
        if (save == null || itemType == null || itemType.isBlank()) {
            return UpgradeVersion.UNKNOWN;
        }
        UpgradeVersion best = UpgradeVersion.UNKNOWN;
        for (ItemState item : save.items) {
            if (itemType.equals(item.itemType)) {
                UpgradeVersion version = UpgradeVersion.parse(item.version);
                if (version.compareTo(best) > 0) {
                    best = version;
                }
            }
        }
        return best;
    }

    /**
     * What a package fetches on resale, given its build.
     *
     * <h2>⚠ This is the ONLY mechanical consequence a version has</h2>
     *
     * Scaled off the base resale figure rather than off the catalogue price, so
     * {@code Repac.RESALE_PERCENT}'s standing argument still holds: even the newest build of the most
     * expensive tool stays well under retail, because theft has no compute cost, no thermal recovery
     * and no cap, and {@code docs/design/00} §4 says compute is the master scarcity.
     *
     * <p>⚠ It must never exceed retail. A resale worth more than the catalogue price would make
     * buying-to-resell a money printer, and the ceiling below is what stops a future tier bump from
     * quietly creating one.
     */
    public static BigInteger resaleWei(BigInteger baseResaleWei, BigInteger retailWei, UpgradeVersion version) {
        if (baseResaleWei.signum() <= 0) {
            return BigInteger.ZERO;
        }
        if (!version.known()) {
            return baseResaleWei;
        }
        BigInteger scaled = baseResaleWei
                .multiply(BigInteger.valueOf(
                        100L + Balance.UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR * (version.major() - 1L)))
                .divide(BigInteger.valueOf(100L));
        // Never above retail — see above. Guarded here rather than trusted to the constants, because
        // the constants are exactly what a re-tune moves.
        return retailWei.signum() > 0 ? scaled.min(retailWei.subtract(BigInteger.ONE)) : scaled;
    }

    /**
     * How installing {@code candidate} would relate to what the player already holds.
     *
     * <p>Four answers rather than a boolean, because "you cannot install this" and "this would
     * replace what you have" and "you already have this exact build" are three different messages and
     * a player told the wrong one will act on it.
     */
    public enum Standing {
        /** Nothing of this tool is owned — a plain acquisition. */
        NEW,
        /** Strictly newer than what is held; installing replaces it. */
        UPGRADE,
        /** The same build already sits in the vault. */
        SAME,
        /** Older than what is held. Still worth stealing to SELL; installing is refused. */
        OLDER
    }

    /** Where {@code candidate} stands against what the player holds of the same tool. */
    public static Standing standing(SoloSave save, String itemType, UpgradeVersion candidate) {
        boolean has = save != null && save.items.stream()
                .anyMatch(item -> itemType != null && itemType.equals(item.itemType));
        if (!has) {
            return Standing.NEW;
        }
        UpgradeVersion mine = owned(save, itemType);
        // ⚠ An owned item with no recorded version — anything installed before versions existed —
        // is treated as OLDER rather than SAME. Saying "you already have this" about a build nobody
        // knows the number of would be a claim the game cannot support, and the harmless reading is
        // the one that lets the player replace it.
        if (!mine.known()) {
            return candidate.known() ? Standing.UPGRADE : Standing.SAME;
        }
        if (candidate.supersedes(mine)) {
            return Standing.UPGRADE;
        }
        return candidate.equals(mine) ? Standing.SAME : Standing.OLDER;
    }
}
