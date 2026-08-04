package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.net.TransferRules;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;

/**
 * Whether there is anywhere to put a thing.
 *
 * <h2>⚠ Where a BOUGHT item lands, and why it is the exposed tier</h2>
 *
 * {@link #ARRIVALS} is {@link StorageTier#HIGH_HACKABLE_ZONE}. Everything bought arrives there and
 * the player moves it somewhere safer themselves — the vault is a decision, not a default. That is
 * the one thing that makes {@code docs/design/01-core-resources.md} §6's tiers a live trade rather
 * than a setting nobody touches: goods you have not filed are goods anybody can take, so filing them
 * is an act with a cost (a move is loud) and skipping it is a risk you chose.
 *
 * <p>⚠ It is also why the capacity check below binds against a tier of
 * {@link Balance#HIGH_HACKABLE_CAPACITY} (60) rather than the vault's six.
 * {@code Balance.storageCapacity}'s own note warns that enforcing a hard cap of six with no way to
 * raise it "is a different game from the one that document describes" — and it is right, which is
 * why nothing here enforces the <b>vault's</b> cap or refuses a <b>move</b>. What is enforced is the
 * narrow thing: the shop will not sell you something you have nowhere to put.
 *
 * <h2>⚠ COMMITTED, not merely occupied — three things claim a slot</h2>
 *
 * Counting only installed items would let a player queue a hundred downloads against sixty slots and
 * discover the problem forty installs later, with the money gone. So a slot is claimed by anything
 * already paid for that is going to want one:
 *
 * <ul>
 *   <li>items already sitting in the tier;
 *   <li>orders in the download queue, a bundle counting once per member;
 *   <li>bought packages already on disk and not yet installed — including an unextracted archive,
 *       which counts once per package inside it.
 * </ul>
 *
 * ⚠ A <b>stolen</b> package is deliberately not counted. It lands in the vault, not here, and
 * counting it would make somebody else's shelf refuse a sale over a file they took for free.
 */
public final class StorageRules {

    private StorageRules() {}

    /**
     * Where anything bought is filed when it is installed.
     *
     * <p>⚠ Not the vault. See the class note — the exposure is the point, and a purchase that filed
     * itself safely would make the tier system a thing the player never has to think about.
     */
    public static final StorageTier ARRIVALS = StorageTier.HIGH_HACKABLE_ZONE;

    /** How many items are actually sitting in a tier right now. */
    public static int occupied(GameSave save, StorageTier tier) {
        if (save == null) {
            return 0;
        }
        return (int) save.items.stream().filter(item -> tier.name().equals(item.tier)).count();
    }

    /**
     * How many slots in a tier are spoken for — occupied, plus everything paid for that is on its
     * way there.
     *
     * @param save the character
     * @param tier which tier
     * @return the count, which may exceed {@link Balance#storageCapacity} on a save that predates
     *     this check or on one edited by hand
     */
    public static int committed(GameSave save, StorageTier tier) {
        if (save == null) {
            return 0;
        }
        int count = occupied(save, tier);
        if (tier != ARRIVALS) {
            // Only the arrivals tier has anything in flight towards it: a purchase is the one route
            // that reserves a slot before the item exists.
            return count;
        }
        for (var order : DownloadQueue.orders(save)) {
            count += order.isBundle() ? order.memberItemTypes.size() : 1;
        }
        for (var file : save.files) {
            if (!TransferRules.VENDOR.equals(file.sourceAddress)) {
                continue;
            }
            if (Archives.isArchive(file)) {
                count += file.archiveItemTypes.size();
            } else if (!file.itemType.isBlank()) {
                count++;
            }
        }
        return count;
    }

    /** How many more will fit, never negative. */
    public static int free(GameSave save, StorageTier tier) {
        return Math.max(0, Balance.storageCapacity(tier) - committed(save, tier));
    }

    /**
     * Whether a purchase of {@code wanted} items can be housed.
     *
     * @param save the character
     * @param wanted how many slots the purchase needs — one per item, and a bundle needs one per
     *     member
     * @return true if they all fit
     */
    public static boolean roomFor(GameSave save, int wanted) {
        return free(save, ARRIVALS) >= wanted;
    }

    /**
     * The refusal, in words a player can act on.
     *
     * <p>⚠ Names the tier, the numbers <b>and the way out</b>. "Not enough room" is a dead end; the
     * way out is that the arrivals tier is the exposed one and moving things off it is exactly what
     * the player was supposed to be doing anyway, so the sentence teaches the mechanic at the moment
     * it starts to matter.
     */
    public static String noRoomMessage(GameSave save, int wanted) {
        int free = free(save, ARRIVALS);
        return "no room for " + (wanted == 1 ? "that" : "all " + wanted + " of those")
                + " — bought goods land in the high-risk zone and it has "
                + free + " of " + Balance.storageCapacity(ARRIVALS)
                + " slots free (downloads on their way count). Move what is there into the vault or "
                + "standard storage, or sell it.";
    }
}
