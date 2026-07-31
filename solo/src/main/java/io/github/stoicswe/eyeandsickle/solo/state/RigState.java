package io.github.stoicswe.eyeandsickle.solo.state;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.Pools;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The player's machine: its capacity, and every claim currently made against it. */
public final class RigState {

    public String rigId = UUID.randomUUID().toString();

    /**
     * Total capacity in cycles. Expands only through schematics and story milestones — never by
     * purchase (Invariant I1, I12), which is why nothing in the market catalogue writes to it.
     */
    public long totalCycles = Balance.STARTING_CYCLES;

    /** Rig stats from {@code docs/design/11-rig-infrastructure.md} §2. */
    public int thermalBudget = 1;

    public int memoryBuffer = 1;
    public int bandwidth = 1;

    /** Cycles the player has voluntarily committed to self-mining. Safe, silent, zero-heat (I4). */
    public long selfMiningCycles = 0L;

    /**
     * Which way those cycles are pointed: {@code "POOLED"} or {@code "SOLO"}.
     *
     * <p>⚠ <b>Pooled is the default, and that is Invariant I4 talking.</b> {@code
     * docs/design/03-economy.md} §1 prices self-mining as the income <em>floor</em> — the thing a
     * player falls back on when heat has closed everything else off — and a floor has to be the
     * guaranteed figure. Solo mining has the same expectation and none of the guarantee: a hot
     * player who was silently opted into it could mine a whole session for nothing, which turns the
     * safety net into a second punishment and is precisely the "fun-ejector" {@code
     * docs/design/04-mining.md} §1.1 warns against. Solo is a thing you choose.
     *
     * <p>A string rather than the enum for the same reason every other persisted vocabulary here is:
     * a save that predates a constant must load rather than throw.
     */
    public String miningMode = "POOLED";

    /**
     * Which pool, when pooled. Ignored entirely when solo.
     *
     * <p>An id rather than the record, and tolerant of an unknown one — a save naming a pool that no
     * longer exists falls back to the default rather than throwing, the same way an unknown mode
     * does. A player should never be locked out of their own save by a content change.
     */
    public String miningPoolId = Pools.DEFAULT_ID;

    /**
     * Normalised progress toward the next payout, and the {@code Exp(1)} variate it is racing.
     *
     * <p>See {@code ChainState} for why this pair is normalised rather than counted in hashes. ⚠ It
     * is persisted but <b>never published</b>: mining is memoryless, so a progress readout would be
     * a lie, and a player who believed it would hold cycles on mining to protect progress that does
     * not exist ({@code MiningSnapshot}).
     */
    public double miningWorkDone = 0.0d;

    public double miningWorkTarget = 1.0d;

    /**
     * Sub-minor-unit change owed to the player, carried between payouts.
     *
     * <p>⚠ Not fussiness. A pool share is worth about 33.3 minor units, and truncating each one
     * would quietly skim a third of a minor unit per share — about 40 EC over a hundred hours, and
     * it would make a session played in one sitting pay differently from the same session played in
     * ten. That is the exact class of bug {@code MiningRules} was already written to avoid, so the
     * remainder is carried instead of dropped.
     */
    /**
     * ⚠ A {@link java.math.BigDecimal}, not a double, since the move to 18 decimals.
     *
     * <p>A residue is the fraction of a unit a payout did not fill, and it is carried so that a
     * hundred payouts of a third of a unit bank thirty-three units rather than nothing. A double held
     * that fine while a unit was a hundredth of an ethecoin; at 1e-18 EC the accumulated value passes
     * a double's exact-integer range (2^53) within a single ordinary payout, and the residue would
     * start absorbing rounding error instead of preventing it — which is the exact opposite of what
     * it is for.
     */
    /**
     * ⚠ {@code @JsonAlias} carries the PRE-WEI key, and a save is lost without it.
     *
     * <p>The field was {@code miningResidueMinorUnits} when an ethecoin was 100 minor units. Jackson has
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} off — deliberately, so a save from a newer build still opens
     * — which means a key it does not recognise is <b>silently dropped</b>. Renaming the field without
     * this alias therefore does not fail: it loads the save, leaves every amount at its initialiser,
     * and hands the player a balance of zero with nothing anywhere saying why.
     *
     * <p>Measured, not theorised. A real pre-migration save loaded as {@code 0 EC} across the board,
     * and the only reason it was noticed at all is that one field had no initialiser and threw.
     */
    @com.fasterxml.jackson.annotation.JsonAlias("miningResidueMinorUnits")
    public java.math.BigDecimal miningResidueWei = java.math.BigDecimal.ZERO;

    /**
     * Earned but not yet paid out, in minor units — the pool's internal balance for this rig.
     *
     * <p>Settled every {@link Balance#POOL_SETTLE_SECONDS}, which is what keeps {@code ledger(1)}
     * readable at 120 shares an hour. ⚠ Persisted, so a quit never loses it; the first tick back
     * settles it because the check is against the clock rather than a counter.
     */
    /**
     * ⚠ {@code @JsonAlias} carries the PRE-WEI key, and a save is lost without it.
     *
     * <p>The field was {@code miningPendingMinorUnits} when an ethecoin was 100 minor units. Jackson has
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} off — deliberately, so a save from a newer build still opens
     * — which means a key it does not recognise is <b>silently dropped</b>. Renaming the field without
     * this alias therefore does not fail: it loads the save, leaves every amount at its initialiser,
     * and hands the player a balance of zero with nothing anywhere saying why.
     *
     * <p>Measured, not theorised. A real pre-migration save loaded as {@code 0 EC} across the board,
     * and the only reason it was noticed at all is that one field had no initialiser and threw.
     */
    @com.fasterxml.jackson.annotation.JsonAlias("miningPendingMinorUnits")
    public BigInteger miningPendingWei = BigInteger.ZERO;

    /** How many payouts are waiting in {@link #miningPendingWei}, for the ledger's wording. */
    public int miningPendingPayouts = 0;

    /** When the pool last settled up. */
    public Instant miningSettledAt;

    /** Blocks found, or shares accepted, over this character's life. */
    public long miningPayouts = 0L;

    /** Everything mining has ever paid this character, in minor units. */
    /**
     * ⚠ {@code @JsonAlias} carries the PRE-WEI key, and a save is lost without it.
     *
     * <p>The field was {@code miningMinorUnits} when an ethecoin was 100 minor units. Jackson has
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} off — deliberately, so a save from a newer build still opens
     * — which means a key it does not recognise is <b>silently dropped</b>. Renaming the field without
     * this alias therefore does not fail: it loads the save, leaves every amount at its initialiser,
     * and hands the player a balance of zero with nothing anywhere saying why.
     *
     * <p>Measured, not theorised. A real pre-migration save loaded as {@code 0 EC} across the board,
     * and the only reason it was noticed at all is that one field had no initialiser and threw.
     */
    @com.fasterxml.jackson.annotation.JsonAlias("miningMinorUnits")
    public BigInteger miningWei = BigInteger.ZERO;

    /** When the last payout landed, or null if none ever has. */
    public Instant miningLastPayoutAt;

    public List<AllocationState> allocations = new ArrayList<>();

    /**
     * Miners somebody else planted on <em>this</em> rig, stealing its cycles.
     *
     * <p>The other side of Invariant I6. {@link #allocations} already holds the deployer's half —
     * a {@code CONTROL_CHANNEL} reservation on the deployer's rig — and this list is the host's
     * half: each miner here also holds a {@code DEPLOYED_MINER} allocation against
     * {@link #totalCycles}, which is why a foreign miner shows up in the compute readout as cycles
     * the player is not getting back.
     *
     * <p>That visibility is the point, not an oversight. {@code docs/design/04-mining.md} §3.1 makes
     * manual investigation work by requiring that "the discrepancy is always present in the data —
     * cycle totals that don't add up", and a parasite charged to nobody would leave no discrepancy
     * to find. The client already renders {@code DEPLOYED_MINER} as "Foreign miner / on your rig";
     * it had simply never had one to render.
     *
     * <p>A rootkit-wrapped miner ({@code docs/design/09-defense-and-hardening.md}) is hidden by
     * being absent from the disclosed <em>allocation list</em> while still consuming the cycles —
     * which is what {@code ComputeBudget.unaccountedFor()} exists to expose. This module does not
     * hide them yet; when it does, that is the mechanism.
     *
     * <p>These are what {@code breachTargets()} offers as crack targets ({@code 04} §5.1).
     */
    public List<MinerState> foreignMiners = new ArrayList<>();
}
