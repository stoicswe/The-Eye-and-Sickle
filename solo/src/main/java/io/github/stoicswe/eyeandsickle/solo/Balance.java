package io.github.stoicswe.eyeandsickle.solo;

import java.math.BigInteger;
import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;

/**
 * Every tunable number the solo runtime uses, in one place, each cited to the design document that
 * owns it.
 *
 * <h2>Why this class is a wall of constants instead of scattered literals</h2>
 *
 * This module is a <em>second implementation</em> of a subset of the game's rules — the server is the
 * first, and the authoritative one. Duplication is the price of the small footprint (see the module
 * description in {@code solo/pom.xml}), and the only honest way to pay it is to make the duplicated
 * values <em>findable</em>. A number inlined at its use site drifts silently. A number here, with the
 * document and section it came from on the line above it, drifts visibly: anyone re-tuning {@code
 * docs/design/03-economy.md} can grep this file and see the whole blast radius at once.
 *
 * <p>{@code docs/design/03-economy.md} is explicit that the economy figures are calibrated
 * <em>as a set</em>. Changing one here without re-reading that document is how a solo game ends up
 * subtly easier or harder than the real one, which is worse than either.
 *
 * <h2>What is deliberately NOT here</h2>
 *
 * Nothing in this class may be read as authority for online play. A federated server computes its own
 * numbers from its own configuration; these exist so a player with no network can still play a game
 * whose arithmetic matches the design. Solo characters never federate, so the two can never meet in a
 * transaction where a disagreement would matter.
 *
 * @see io.github.stoicswe.eyeandsickle.solo.rules.MiningRules
 */
public final class Balance {

    /**
     * An amount of ethecoin, as wei — the one way a price is written in this file.
     *
     * <h2>⚠ Written as the DECIMAL a designer reads, never as a wei literal</h2>
     *
     * A price is {@code ec("180")}, not {@code 180000000000000000000L}. Eighteen zeros is not a
     * number anybody can check by eye, and every balance figure in this file has to be checkable
     * against {@code docs/design/03-economy.md} — which quotes ethecoin, not wei. The constants were
     * previously hundredths ({@code 18_000L} meaning 180 EC), which had the same readability problem
     * on a smaller scale and produced exactly one confusion per new reader.
     */
    public static BigInteger ec(String amount) {
        return io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.ofDecimal(amount).wei();
    }


    private Balance() {}

    // ------------------------------------------------------------------ compute

    /**
     * A starting rig has 100 cycles — {@code docs/design/01-core-resources.md} §1.
     *
     * <p>This is the denominator of the entire game. Every cost below is legible only relative to it:
     * a Thorough Scan at 35 is a third of a starting rig, which is why it is a decision rather than a
     * button.
     */
    public static final long STARTING_CYCLES = 100L;

    /**
     * A live deployed miner reserves 3 cycles of the <em>deployer's</em> rig for its control channel,
     * permanently, while it runs — {@code docs/design/01-core-resources.md} §1 and {@code
     * docs/design/04-mining.md} §2.
     *
     * <p>Note which rig pays: the control channel is the deployer's cost, while the miner's actual
     * work is charged to the host (Invariant I6). Getting this backwards would make deployment free,
     * which is the single most load-bearing cost in the mining economy.
     */
    public static final long CONTROL_CHANNEL_CYCLES = 3L;

    // ------------------------------------------------------------------ mining

    /**
     * Self-mining yields 0.4 EC per cycle-hour — derived from {@code docs/design/03-economy.md} §1,
     * which prices a full 100-cycle rig at 40 EC/hr, and stated directly in {@code
     * docs/design/glossary.md}.
     *
     * <p>Expressed in minor units per cycle-hour so the arithmetic stays integral; see {@link
     * io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin} on why money is never a double.
     */
    public static final BigInteger SELF_MINING_WEI_PER_CYCLE_HOUR = ec("0.4");

    // ---------------------------------------------------------------- the chain
    //
    // Self-mining is a real proof-of-work simulation from 2026-07-27 (docs/design/04 §1.3). Every
    // constant below is either a REAL Bitcoin parameter reused verbatim, so the transfer in
    // docs/education/07 §3 is exact, or a game value DERIVED from the anchor above so that the
    // economy table in docs/design/03 §1 keeps its meaning without being re-tuned.
    //
    // ⚠ The anchor is the fixed point and the chain bends to it, never the other way round. If
    // SELF_MINING_WEI_PER_CYCLE_HOUR ever moves, chainNetworkHashrate() follows it
    // automatically and every figure here stays consistent. Hardcoding the network hashrate would
    // silently decouple the two and the first symptom would be an income table that is wrong.

    /**
     * Expected hashes per block at difficulty 1, as {@code 2^32}.
     *
     * <p>⚠ <b>Real, and reused exactly.</b> Bitcoin's difficulty-1 target is
     * {@code 0x00000000FFFF0000...}, which makes the expected number of hashes per block
     * {@code difficulty × 2^48 / 0xffff}, i.e. {@code difficulty × 2^32} to within one part in
     * 65 536. Keeping the real constant is what lets {@code docs/education/07} tell a player to
     * check the arithmetic against a live block explorer and have it come out.
     *
     * <p>Verified against the Bitcoin wiki's Difficulty page, 2026-07-27.
     */
    public static final double HASHES_PER_DIFFICULTY = 4294967296.0d; // 2^32

    /**
     * A cycle is worth 2^20 hashes per second.
     *
     * <p>The one purely conventional number here: it fixes what "hashrate" means in this fiction and
     * nothing downstream depends on its value, because difficulty is derived from it and cancels out
     * of every income figure. Chosen so that network difficulty lands in the hundreds — a number a
     * player can read and compare, rather than the fourteen-digit figure a real chain carries.
     */
    public static final long HASHES_PER_CYCLE_SECOND = 1L << 20;

    /**
     * The chain targets one block every <b>fourteen</b> minutes.
     *
     * <h2>⚠ Deliberately not Bitcoin's ten, and that is the point</h2>
     *
     * The <em>relation</em> {@code difficulty × 2^32} is real and reused exactly; the interval is
     * this chain's own. Ten minutes would have made ethecoin read as a Bitcoin reskin, and a chain
     * that is recognisably one real chain teaches players a specific product rather than the
     * mechanism. Fourteen minutes is unmistakably not Bitcoin's and behaves identically in every way
     * that matters.
     *
     * <p>⚠ <b>The economy does not move when this does.</b> {@link #chainNetworkHashrate()} is solved
     * from this and the {@code docs/design/03-economy.md} §1 anchor, and the product
     * {@code interval × networkHashrate} is fixed by that anchor — so lengthening the interval shrinks
     * the network in exact proportion and every income figure is unchanged. Verified: the 2352-cycle
     * network at ten minutes became a 1680-cycle network at fourteen, and a solo block still takes
     * 4.17 hours at 94 cycles.
     */
    public static final long CHAIN_TARGET_BLOCK_SECONDS = 840L;

    /**
     * Difficulty is recalculated every 1440 blocks.
     *
     * <p>Bitcoin uses 2016 <em>because</em> 2016 × ten minutes is two weeks. This chain keeps the
     * design property and drops the number: 1440 × fourteen minutes is also two weeks, to the hour.
     * A fortnight is long enough for luck to average out and short enough to answer a real change in
     * hashrate, which is the whole reason a retarget window has a length.
     *
     * <p>⚠ A shorter window is a noisier one: the relative spread of {@code n} random block times is
     * {@code 1/√n}, so 1440 jitters about 2.6% per retarget against 2016's 2.2%. That jitter is real
     * and is not a defect — see {@code ChainRules.retarget}.
     */
    public static final long CHAIN_RETARGET_BLOCKS = 1440L;

    /**
     * A retarget may never move difficulty by more than a factor of four in either direction.
     *
     * <p>⚠ <b>Real, and reused exactly.</b> Bitcoin clamps the adjustment to [1/4, 4] so that a
     * sudden hashrate collapse cannot strand the chain and a sudden influx cannot make blocks
     * instantaneous. Verified against the Bitcoin wiki's Difficulty page, 2026-07-27.
     */
    public static final double CHAIN_RETARGET_CLAMP = 4.0d;

    /**
     * A block pays 160 EC.
     *
     * <p><strong>[PROPOSAL]</strong>, and the one knob that sets how much of a lottery solo mining
     * is. It is a <em>lump</em>, not a rate: raising it makes solo blocks rarer and larger without
     * changing anyone's expected income by one minor unit, because
     * {@link #chainNetworkHashrate()} absorbs the change. That is the knob to reach for if solo
     * mining feels too steady or too hopeless — and it is the <b>only</b> one that does not disturb
     * {@code docs/design/03}.
     *
     * <p>At 160 EC a full 100-cycle rig expects a solo block roughly every <b>3 hours 55 minutes</b>
     * — about a 22% chance in any given hour, and about 6% at a quarter rig. That is the reading of
     * "a percent chance of a large payout, and you need a lot of cycles to make it likely".
     */
    public static final BigInteger BLOCK_SUBSIDY_WEI = ec("160");

    /**
     * The pool takes 2%.
     *
     * <p>Real in shape and in size: pay-per-share pools charge roughly 2–4% precisely because the
     * operator is buying the miner's variance and must pay for accepted shares through an unlucky
     * streak in which the pool finds fewer blocks than it owes. Two percent is the bottom of the
     * observed range, which keeps the choice close rather than obvious. Verified against published
     * pool payout-scheme documentation (f2pool, minerstat), 2026-07-27.
     *
     * <p>⚠ <b>This is why solo pays more in expectation</b>, and that is not a bug to balance away.
     * A pool that paid the same as solo would be free insurance and nobody sane would mine solo; the
     * fee is what makes the choice a trade rather than a preference.
     */
    public static final double POOL_FEE = 0.02d;

    /** The pool's cut, as basis points, for the wire and the readout. */
    public static final int POOL_FEE_BASIS_POINTS = (int) Math.round(POOL_FEE * 10_000);

    /**
     * The pool retunes each miner's share difficulty to land a share every 30 seconds.
     *
     * <p>Real, and it is called <b>vardiff</b>. A pool sets each miner's share target from that
     * miner's own hashrate so that shares arrive at a steady rate whatever the rig — a small miner
     * gets an easy target and a large one a hard target, and both submit at about the same pace. It
     * is the reason a pool smooths income for a 10-cycle rig as effectively as for a 100-cycle rig,
     * which a fixed share difficulty would not do.
     *
     * <p>Thirty seconds is a game value. It sets the variance ratio between the two modes directly:
     * pooled income has {@code soloInterval / 30} times less variance, which at a full rig is a
     * factor of about <b>470</b>.
     */
    public static final double POOL_SHARE_SECONDS = 30.0d;

    /**
     * How loud a pooled rig is, in cycle-equivalents, at the reference 30-second share interval.
     *
     * <h2>⚠ Pooled mining is audible and solo mining is not, and that is the right way round</h2>
     *
     * A pooled miner holds an open connection to a pool server and pushes a share up it every thirty
     * seconds, forever. That is outbound traffic to a third party, which is precisely what
     * {@code NoiseRules} counts. A <b>solo</b> miner talks to nobody: the work is local grinding, and
     * the only thing that ever leaves the rig is a block announcement once every few hours. So solo
     * is genuinely silent and pooled genuinely is not.
     *
     * <p><b>Invariant I4 is not violated.</b> I4 makes self-mining immune to detection and seizure and
     * gives it <em>zero heat</em> — and it still has all three. Noise is a rate, heat is what an act
     * leaves behind, and nothing converts this trickle into heat: heat is charged at breach
     * resolution and by counter-hacks, never off the ambient meter. What I4 protects is that going
     * hot cannot take the floor away, and a rig reading 2% on the noise meter has lost nothing.
     *
     * <p>⚠ <b>Deliberately tiny, and deliberately flat.</b> Two cycles on a hundred-cycle rig is 2%
     * of the meter against a sweep's 35 — a sweep is more than seventeen times louder. And it does
     * <em>not</em> scale with allocation, because a share is a small fixed packet however much
     * hashing produced it: doubling your cycles doubles your income and changes your traffic not at
     * all. That is real, and it means the noise-conscious play is to pick a pool, not to mine less.
     *
     * <p>It <em>does</em> scale with how often the pool wants shares, which is the one place a pool's
     * share interval earns its keep as more than flavour — {@code MERIDIAN CLEARING} asks for a share
     * every fifteen seconds and is twice as loud as the reference for it.
     */
    public static final long POOL_SHARE_NOISE_CYCLES = 2L;

    /**
     * The pool settles up every sixty seconds.
     *
     * <h2>⚠ This exists because the ledger is the artefact, not because the maths needed it</h2>
     *
     * Crediting each share the instant it lands is arithmetically identical and puts <b>120 rows an
     * hour</b> into {@code ledger(1)} — a readout whose shipped page calls itself "the only record of
     * where your money went". Buried under a wall of identical 0.31 EC rows it records nothing, which
     * is {@code alert-fatigue(7)} again in the one place a player audits.
     *
     * <p>Real pools do exactly this: shares are credited to an internal balance continuously and paid
     * out on a schedule. Sixty seconds is a game value — short enough that the balance visibly moves,
     * long enough that an hour of mining is sixty readable rows instead of a screenful of noise.
     *
     * <p>⚠ <b>The credit and the ledger row happen together, always.</b> Crediting continuously and
     * ledgering periodically would leave the balance ahead of the last row — and
     * {@code docs/design/04-mining.md} §3.1 makes "two readouts disagree" the way a player detects an
     * intruder. Training them to ignore that would cost more than the tidy ledger bought.
     *
     * <p>A <b>solo</b> block never waits: a block is a real coinbase and earns its own row.
     */
    public static final long POOL_SETTLE_SECONDS = 60L;

    // ---------------------------------------------------------------- the fee market

    /**
     * The block height a new character joins the chain at.
     *
     * <p>124 blocks of history exist before the player does — about a day at fourteen minutes — and
     * every one of them is inspectable in the explorer. A chain that began at the player's first
     * session would say it had been waiting for them, which is the opposite of what a shared ledger
     * is. <strong>[PROPOSAL]</strong>.
     */
    public static final long CHAIN_START_HEIGHT = 124L;

    /**
     * Transactions a block can hold — the gas limit divided by the cost of one transfer.
     *
     * <p>This is the number that makes a fee market exist at all. A block that could hold every
     * pending transaction would make fees pointless, and one that held two would make them
     * everything. Two hundred against a mempool that runs a few hundred deep means an ordinary
     * transaction waits a block or two and a priority one does not.
     */
    public static final int BLOCK_TRANSACTION_LIMIT = 200;

    /**
     * The fee floor, in minor units: what {@link FeeTier#ECONOMY} pays.
     *
     * <h2>⚠ Deliberately small enough not to be an economy change</h2>
     *
     * {@code docs/design/03-economy.md} §4 lists the sinks the economy is balanced against, and this
     * is not one of them. Two minor units — 0.02 EC — against a 40 EC/hr income is a rounding error
     * by design: the fee exists to <b>order a queue</b>, not to drain a balance. If it ever grows
     * enough to matter it becomes a sink and §4 has to know about it.
     */
    public static final BigInteger FEE_ECONOMY_WEI = ec("0.02");

    /** What {@link FeeTier#STANDARD} pays. Still negligible against income; still enough to sort on. */
    public static final BigInteger FEE_STANDARD_WEI = ec("0.06");

    /** What {@link FeeTier#PRIORITY} pays. Fifteen times the floor and still under a cycle-minute. */
    public static final BigInteger FEE_PRIORITY_WEI = ec("0.30");

    /** What a tier costs, in wei. */
    public static BigInteger feeFor(FeeTier tier) {
        return switch (tier == null ? FeeTier.STANDARD : tier) {
            case ECONOMY -> FEE_ECONOMY_WEI;
            case STANDARD -> FEE_STANDARD_WEI;
            case PRIORITY -> FEE_PRIORITY_WEI;
        };
    }

    /**
     * How deep the NPC mempool runs, on average.
     *
     * <p>Enough that a block cannot clear it in one go — otherwise there is no queue to be at the
     * front of, and the fee tiers would all confirm identically. Three hundred against a 200-slot
     * block means a standard transaction waits roughly a block and an economy one several.
     */
    public static final int MEMPOOL_BASELINE_DEPTH = 300;

    /**
     * What a block's fees are worth to whoever mines it, on average — <b>derived, never chosen</b>.
     *
     * <h2>⚠ This is mining income, and since 2026-07-27 it is real income</h2>
     *
     * A block pays its miner {@code subsidy + fees}, as on any real chain. Before that date the fees
     * players paid into the mempool were debited and then ceased to exist, which made the fee market
     * a pure sink and left the explorer's "fees 0.38 EC" naming money nobody ever received.
     *
     * <h2>Derived from the two distributions rather than measured and pasted</h2>
     *
     * A block carries {@code 12 + U[0, LIMIT − 12)} transactions and each pays
     * {@code FEE_ECONOMY + U[0, FEE_PRIORITY − FEE_ECONOMY]}, so the expectation is the product of
     * the two means: {@code 105.5 × 16 = 1688}. Writing the number here instead would be a fourth
     * copy of the fee ladder, silently wrong the first time a tier moved —
     * {@code MiningChainTest} asserts this against 20 000 simulated blocks for exactly that reason.
     *
     * <p>⚠ It is <b>10.55% of the subsidy</b>, so it moves mining income by that much. That was a
     * deliberate call on 2026-07-27: {@code chainNetworkHashrate()} was <em>not</em> re-solved to
     * absorb it, so self-mining now pays about a tenth more than {@code design/03} §1's
     * 0.40 EC/cycle-hour anchor. See `03` §1.1 for what that re-rated and what it did not.
     */
    /**
     * The fees an average block carries, in wei.
     *
     * <h2>⚠ BigDecimal, not double, and the reason is now visible on screen</h2>
     *
     * This used to return a {@code double} and that was harmless while an amount had two decimal
     * places: any float noise sat far below the last digit anybody saw. At 18 decimals the formatter
     * prints every significant digit, so a double result of {@code 3.0000000000000004e19} wei would
     * render as <b>{@code 30.000000000000004 EC}</b> — a plausible-looking figure with four digits of
     * arithmetic residue in it. Doubles hold integers exactly only below 2^53 (~9×10^15), and a wei
     * amount passes that at nine thousandths of an ethecoin.
     *
     * <p>The means themselves are genuinely fractional — both are of a {@code floorMod} over a
     * half-open range, so each sits {@code (span − 1) / 2} above its floor, i.e. 93.5 transactions
     * above 12 — so the calculation is done in {@link BigDecimal} and lands on an exact wei count.
     */
    public static BigInteger expectedBlockFeesWei() {
        java.math.BigDecimal meanTransactions = java.math.BigDecimal.valueOf(
                12 + (BLOCK_TRANSACTION_LIMIT - 12 - 1) / 2.0d);
        java.math.BigDecimal meanFee = new java.math.BigDecimal(
                FEE_ECONOMY_WEI.add(FEE_PRIORITY_WEI.subtract(FEE_ECONOMY_WEI)
                        .divide(BigInteger.TWO)));
        return meanTransactions.multiply(meanFee).toBigInteger();
    }

    /**
     * The rest of the chain's hashrate, in hashes per second — <b>derived, never chosen</b>.
     *
     * <h2>Why this is a derivation</h2>
     *
     * A miner's income is {@code subsidy × ownHashrate / networkHashrate} per block interval. Three
     * of those four are already fixed: the subsidy above, the rig's hashrate, and the 0.4 EC per
     * cycle-hour that {@code docs/design/03-economy.md} §1 prices the whole economy against. So the
     * network's hashrate is not a free parameter — it is whatever value makes the other three agree,
     * and writing it down as a constant would be writing down an answer that can silently stop being
     * the answer.
     *
     * <p>Solving for it at the <em>pooled</em> rate rather than the solo rate is deliberate:
     * {@code 03} §1's 40 EC/hr is described as a <b>floor</b>, and a floor has to be the guaranteed
     * figure. So pooled pays exactly the documented rate and solo pays it back divided by
     * {@code (1 - fee)} — marginally more, in exchange for all of the variance.
     *
     * <p>It works out at about 2352 cycles: the player's full rig is roughly 4% of the chain. That
     * is a small network, which is correct for a resistance's chain and is what keeps a solo block
     * reachable at all.
     */
    public static double chainNetworkHashrate() {
        // ⚠ A RATIO of two wei amounts, so the scale cancels and a double is exact enough — the
        // subsidy and the per-cycle-hour rate are both in wei and divide into a pure number around
        // 2352. Converting either to double on its own would be the lossy step; dividing them is not.
        double subsidyOverRate = new java.math.BigDecimal(BLOCK_SUBSIDY_WEI)
                .divide(new java.math.BigDecimal(SELF_MINING_WEI_PER_CYCLE_HOUR),
                        java.math.MathContext.DECIMAL64)
                .doubleValue();
        double cycles = subsidyOverRate * (1.0d - POOL_FEE) * 3600.0d / CHAIN_TARGET_BLOCK_SECONDS;
        return cycles * HASHES_PER_CYCLE_SECOND;
    }

    /**
     * The difficulty that holds {@link #CHAIN_TARGET_BLOCK_SECONDS} at a given network hashrate.
     *
     * <p>The real relation, rearranged: expected seconds to a block is
     * {@code difficulty × 2^32 / hashrate}, so holding the interval means
     * {@code difficulty = interval × hashrate / 2^32}.
     */
    public static double chainDifficultyFor(double networkHashrate) {
        return CHAIN_TARGET_BLOCK_SECONDS * networkHashrate / HASHES_PER_DIFFICULTY;
    }

    /**
     * A deployed miner's on-host yield buffer caps at 4 hours — {@code docs/design/04-mining.md} §2.3
     * and {@code docs/design/glossary.md}.
     *
     * <p>This cap is what stops offline income scaling with time away, and it is the prize an attacker
     * takes when they crack a miner. {@code docs/design/15-open-questions.md} OQ-4 flags the figure as
     * a starting value pending session-length telemetry.
     */
    public static final long YIELD_BUFFER_HOURS = 4L;

    /**
     * How long the rig keeps hashing after the client closes — {@code docs/design/04-mining.md} §1.2.
     *
     * <h2>⚠ This is Invariant I5, amended on 2026-07-29, and it used to be zero</h2>
     *
     * I5 read "self-mining runs online-only". It now reads "stops a bounded time after the client
     * closes; all offline income is capped, never proportional to absence" — because the cap, not the
     * online-only rule, was always the thing doing the work. The argument against offline accrual was
     * that absence would out-earn play on an income stream that is also zero-heat and unseizable
     * (I4); past this window a longer absence is worth exactly nothing more, so there is no absence to
     * optimise toward, and an hour played always beats an hour away because play is uncapped.
     *
     * <h2>⚠ Deliberately the same figure as {@link #YIELD_BUFFER_HOURS}, and deliberately not the
     * same constant</h2>
     *
     * They agree today and they are not the same quantity. This one bounds how long a rig the player
     * owns goes on working; that one bounds how much a miner sitting on somebody else's disk may hold
     * before an attacker's prize stops growing ({@code 04} §5.1's crack timing bet is priced on it).
     * Aliasing them would mean a re-tune of the crack window silently re-tuning self-mining, which is
     * the class of coupling {@code CLAUDE.md} warns about for the {@code design/03} tables.
     *
     * <p>⚠ What separates self-mining from a deployed miner is now <b>exposure, not duration</b>: a
     * miner spends the <em>host's</em> compute (I6), so five of them buffer five hosts' worth of the
     * same four hours — and their buffer can be seized, where self-mining cannot be touched.
     */
    public static final long OFFLINE_MINING_HOURS = 4L;

    /** The same window in seconds, which is the unit every caller actually wants. */
    public static long offlineMiningSeconds() {
        return OFFLINE_MINING_HOURS * 3600L;
    }

    /**
     * What a solo rig's share of the network counts for while the client was closed.
     *
     * <h2>Why an absence pays at half weight</h2>
     *
     * {@link #OFFLINE_MINING_HOURS} caps <em>how long</em> the rig goes on hashing; this caps
     * <em>how well</em> it does while it is. They are separate levers on purpose. The window is what
     * stops a longer absence being worth more — past four hours nothing accrues, so there is no
     * absence to optimise toward. This is the second half of the same argument: within that window,
     * time away should not be worth as much as time played, or the four hours become a thing to
     * collect rather than a courtesy. Play stays strictly better per hour, and it now stays better
     * per hour <em>inside</em> the buffered window as well as outside it.
     *
     * <h2>⚠ It applies to SELF-MINING only, and only during a fill</h2>
     *
     * The live tick is untouched — a player who leaves the client running is playing, and this is not
     * an idle-time penalty. Pooled mining is untouched as well: a pool's hashrate is the pool's, it
     * competes whether or not one member's client is open, and scaling it here would be this rig
     * reaching into somebody else's rate. What is halved is the <b>player's own</b> share of the draw
     * ({@code ChainRules.drawWinner}); the probability mass that frees up goes to the unpooled
     * population, because somebody still mined the block.
     *
     * <h2>⚠ Invariants this must not disturb</h2>
     *
     * <b>I4</b> — self-mining is still immune to detection and seizure and still generates zero heat;
     * a smaller number is not a risk. <b>I5</b> — offline income remains capped and non-proportional,
     * and this only lowers the cap's value. <b>I2</b> — nothing here is purchasable, so no ceiling
     * moved.
     *
     * <p>⚠ <b>It must never change how much RNG is consumed.</b> The draw is one {@code nextDouble}
     * per block whatever the outcome; this scales the threshold it is compared against, not the
     * number of draws. A generator whose consumption varied with the mode would stop a stored seed
     * being a replay — {@code Rng}'s stated contract, and the reason {@code drawWinner} rolls even
     * for blocks the rig is not contesting.
     *
     * <p>⚠ <b>Deliberately invisible.</b> No readout names it and none should: the SYNCHRONIZING
     * screen reports what the chain did, and a player comparing blocks-won to hashrate share over a
     * few sessions is doing arithmetic on a Poisson process with a sample size of about two. Logged
     * as a decision in {@code docs/design/15-open-questions.md} §3.
     */
    public static final double OFFLINE_SOLO_WIN_WEIGHT = 0.5d;

    /**
     * The most blocks one synchronisation will fill in, block by block.
     *
     * <p>At a 14-minute interval this is a little over five years of absence, so it is a runaway
     * backstop rather than a limit anybody reaches — the loop is a few arithmetic operations per
     * block and 200 000 of them cost single-digit milliseconds. It exists because the alternative to
     * a bound is a save whose {@code lastPlayedAt} was hand-edited to 1970 hanging the client on
     * load, and because {@code advanceNetwork} already takes the same precaution per tick.
     */
    public static final int CHAIN_SYNC_BLOCK_LIMIT = 200_000;

    /**
     * How much more a resold upgrade fetches per major version, in percent.
     *
     * <p>⚠ <b>The only mechanical consequence a version has.</b> A newer build is worth more and
     * supersedes an older one; it is not a better tool, because a capability that rises with the
     * hardness of the machine you take it off would be a ceiling reachable by grinding with no gate
     * on it — Invariant <b>I2</b>, and <b>I3</b> as well since the item would then sit behind two
     * gates. See {@code solo/rules/Versions} and {@code protocol/game/UpgradeVersion}.
     *
     * <p>Twelve percent per major, so the spread from a tier-1 desktop to a tier-5 estate is about
     * 1.5× — enough that a player prefers the harder target, not so much that raiding stops being
     * about what the tool is. {@code Versions.resaleWei} clamps the result below retail
     * whatever this is set to, because a resale above retail would make buy-to-resell a money printer
     * and that must not be one re-tune away.
     */
    public static final long UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR = 12L;

    /**
     * The build the vendor ships.
     *
     * <p>⚠ Deliberately in the MIDDLE of the tier ladder, and that placement is the loop. If the
     * market sold the newest build there would be no reason to raid for one; if it sold the oldest,
     * buying would be strictly dominated and the catalogue would be a trap. At three, a tier-4 or
     * tier-5 estate carries something the shop does not, a tier-1 desktop carries something worse
     * than the shop, and both facts are things a player can discover and act on.
     *
     * <p>It buys no capability either way — see {@link #UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR}.
     * What a newer build is worth is resale and supersession, so this decides how good a deal the
     * shop is, not how good the tool is.
     */
    public static final int MARKET_UPGRADE_VERSION_MAJOR = 3;

    /**
     * What the Firmware Implant image costs at the vendor.
     *
     * <h2>⚠ Firmware is priced well above software, and this is the cheapest firmware there is</h2>
     *
     * 180 EC — roughly three times the deepest sweep tier (55 EC) and above every other single
     * purchase in the catalogue. Three reasons, and the third is the one that sets the floor:
     *
     * <ul>
     *   <li>It is the payload of a <b>permanent</b> capability, not a consumable. Everything else the
     *       vendor sells is losable and replaceable ({@code docs/design/02} §2.1); this is not.
     *   <li>It is inert without the schematic, so a player who buys it speculatively has spent real
     *       money on a file — and the price has to make that a decision rather than a shrug.
     *   <li>⚠ It must stay <b>expensive enough that stealing one is worth the breach</b>. A firmware
     *       image is deliberately available both ways ({@code docs/design/01} §6's raiding route), and
     *       if buying were cheap the raid would be pointless — which would quietly delete the reason
     *       the two-part requirement exists at all.
     * </ul>
     *
     * <p>⚠ It buys <b>no ceiling</b>. The schematic is the gate and no amount of ethecoin produces
     * one ({@code 02} §2.2), so this price can move freely without touching <b>I2</b>. What it must
     * never become is cheap enough to make the raid route dead content.
     */
    public static final BigInteger FIRMWARE_IMPLANT_IMAGE_PRICE = ec("180"); // 180 EC

    /**
     * How long flashing firmware takes.
     *
     * <h2>⚠ Long enough to be a commitment, and it is a commitment with the tool DOWN</h2>
     *
     * Ninety seconds. Every other install in this game is instantaneous because the interesting wait
     * — somebody else's uplink — already happened during the download. Firmware is the exception on
     * purpose: the mining tool is frozen for the whole flash, so the cost is real income foregone
     * rather than a progress bar to watch.
     *
     * <p>The figure is bounded at both ends. Much shorter and freezing the tool costs nothing, so the
     * "stop the tool first" rule becomes ceremony. Much longer and a player flashing between sessions
     * is simply denied their rig, which is a punishment rather than a decision. At a minute and a
     * half it is roughly six blocks of self-mining given up — visible on the ledger, and small enough
     * that nobody plans a day around it.
     *
     * <p>⚠ It is <b>not</b> derived from the image's size. A download is bounded by the far end's
     * uplink and its duration should track bytes; a flash is bounded by the device writing its own
     * memory, and a bigger image does not make a slower flash on any hardware a player has met.
     */
    public static final long FIRMWARE_FLASH_SECONDS = 90L;

    // ------------------------------------------------------------------ scanning

    /**
     * Scan tiers cost 5, 15 and 35 cycles — {@code docs/design/04-mining.md} §3.2.
     *
     * <p>What the player buys with the difference is <em>signal strength</em>, not certainty. The
     * curriculum leans on this hard: {@code docs/education/08-detection-and-defence.md} §3.5 uses
     * these three numbers to teach the false-positive trade, so changing them changes a teaching
     * example as well as a cost.
     */
    /**
     * Cycles one open shell session holds, for as long as it is open.
     *
     * <p>Small on purpose — two cycles is a twentieth of a starting rig, so the first few sessions
     * are effectively free and the twentieth is not. That shape is the whole reason the cost exists
     * rather than a cap: {@code docs/design/00} §4's meta-rule is that compute is the master
     * scarcity, and "how many machines can I sit on at once" should be a question the rig answers.
     * A hard cap would answer it with a number nobody could argue with, which is worse.
     *
     * <p>⚠ Held, never spent, and it does <b>not</b> enter thermal recovery on close — see
     * {@code SessionRules.close}. Recovery is the price of having <em>worked</em> the silicon
     * ({@code docs/design/01} §1.3), and an idle shell has not.
     */
    // ── The link (docs/design/15 — TR-1 is open: whether this is ever upgradable) ─────────────
    //
    // ⚠ DECIMAL bits, because that is what a network is measured in. A file is measured in bytes,
    // and the two units meeting is the single most common place people get transfer arithmetic
    // wrong — 150 Mbit/s is 18.75 MB/s, not 150. Keeping the constants in bits and converting once,
    // here, is what stops that error being made twice in different places.

    /** Downstream, bits per second. Gigabit. */
    public static final long LINK_DOWN_BITS = 1_000_000_000L;

    /** Upstream, bits per second. Asymmetric, the way a real consumer line is. */
    public static final long LINK_UP_BITS = 150_000_000L;

    /**
     * Connection setup, in milliseconds, before a byte moves.
     *
     * <p>Real: a handshake, a key exchange and a request happen before any payload does. It exists
     * here for an honest reason rather than a cosmetic one — without it a four-kilobyte document
     * transfers in under a millisecond and the progress readout is a flicker, which would read as
     * the game failing to do anything rather than as the transfer being genuinely instant.
     */
    public static final long TRANSFER_SETUP_MS = 400L;

    /**
     * How fast a download from another machine actually goes, in bytes per second.
     *
     * <p>⚠ <b>The bottleneck is the REMOTE END'S UPLOAD, not your download.</b> Gigabit down is
     * irrelevant when the machine you are pulling from can only push 150 Mbit — so every transfer in
     * this game runs at 18.75 MB/s no matter how good your line is. That is the single most useful
     * true thing about file transfers that most people have experienced and few have had named for
     * them, and it is why the two constants above are different numbers rather than one.
     */
    public static long downloadBytesPerSecond() {
        return Math.min(LINK_DOWN_BITS, LINK_UP_BITS) / 8L;
    }

    /** How long moving {@code bytes} takes, setup included. Never zero — see the setup constant. */
    public static java.time.Duration transferTime(long bytes) {
        long payloadMillis = Math.max(0L, bytes) * 1000L / Math.max(1L, downloadBytesPerSecond());
        return java.time.Duration.ofMillis(TRANSFER_SETUP_MS + payloadMillis);
    }

    public static final long SESSION_CYCLES = 2L;

    public static final long SCAN_QUICK_CYCLES = 5L;

    public static final long SCAN_FULL_CYCLES = 15L;
    public static final long SCAN_THOROUGH_CYCLES = 35L;

    /** Wall-clock duration of each scan tier, in seconds — {@code docs/design/04-mining.md} §3.2. */
    public static final long SCAN_QUICK_SECONDS = 30L;

    public static final long SCAN_FULL_SECONDS = 120L;
    public static final long SCAN_THOROUGH_SECONDS = 360L;

    // ------------------------------------------------------------------ defense

    /**
     * Standing compute reservations for armed defences — {@code docs/design/09-defense-and-hardening.md}
     * §1.
     *
     * <p>These are the numbers behind that document's §3 observation that a fully paranoid loadout
     * costs more than a starting rig has. That tension is the point and must survive re-tuning: if
     * every defence can be armed at once, the defensive-budget decision disappears and so does the
     * lesson in {@code docs/education/08-detection-and-defence.md} §3.8.
     */
    public static final long DEFENSE_FIREWALL_T1_CYCLES = 5L;

    public static final long DEFENSE_FIREWALL_T2_CYCLES = 10L;
    public static final long DEFENSE_FIREWALL_T3_CYCLES = 15L;
    public static final long DEFENSE_CANARY_CYCLES = 1L;
    public static final long DEFENSE_TARPIT_CYCLES = 8L;
    public static final long DEFENSE_HONEYPOT_STASH_CYCLES = 12L;
    public static final long DEFENSE_AUTO_COUNTER_CYCLES = 18L;
    public static final long DEFENSE_DETECTION_ARRAY_T1_CYCLES = 6L;
    public static final long DEFENSE_DETECTION_ARRAY_T2_CYCLES = 14L;
    public static final long DEFENSE_DETECTION_ARRAY_T3_CYCLES = 25L;

    /** Ethecoin prices for the purchasable defences — {@code docs/design/09-defense-and-hardening.md} §1. */
    public static final BigInteger DEFENSE_CANARY_PRICE = ec("8"); // 8 EC

    public static final BigInteger DEFENSE_TARPIT_PRICE = ec("70"); // 70 EC

    // ------------------------------------------------------------------ market price bands

    /**
     * The price bands from {@code docs/design/03-economy.md} §2, in minor units.
     *
     * <p>Bands rather than prices: the document gives ranges because the exact price of any one item
     * is a content decision, and a solo catalogue that invented precise numbers would be asserting
     * authority it does not have. Offerings are priced inside these bands and say so.
     */
    public static final BigInteger PRICE_CONSUMABLE_MIN = ec("5");

    public static final BigInteger PRICE_CONSUMABLE_MAX = ec("15");
    public static final BigInteger PRICE_MID_TIER_MIN = ec("40");
    public static final BigInteger PRICE_MID_TIER_MAX = ec("60");
    public static final BigInteger PRICE_TOP_PURCHASABLE = ec("200");

    /** Relay-chain upkeep, ~8 EC per hop per session — {@code docs/design/03-economy.md} §4. */
    public static final BigInteger RELAY_HOP_UPKEEP = ec("8");

    // ------------------------------------------------------------------ thermal budget

    /**
     * Thermal Budget recovery — {@code docs/design/01-core-resources.md} §1.3, which is explicitly
     * tagged <strong>[PROPOSAL]</strong> with numbers "for playtest".
     *
     * <p>The curve is <b>bounded</b>: recovery is quick in general, slower the closer the rig sits to
     * capacity, and can never take longer than {@link #THERMAL_MAX_CLEAN_SECONDS} on a rig with
     * nothing stealing from it. That <em>shape</em> is the design commitment; the numbers are not.
     *
     * <h2>⚠ The old formulation was unbounded, and that was the bug</h2>
     *
     * It was {@code rate = 0.5 × (1 − load)² × thermalBudget}, with the time as {@code cycles / rate}.
     * The shape was right and the tail was not: as load approaches capacity the rate approaches zero,
     * so the time approaches <em>infinity</em>. Measured on a real save — a Thorough Scan's 35 cycles
     * on a rig at 90% load took <b>36 minutes</b> to come back, and two cycles at 82% load took a
     * hundred seconds. A player who over-commits should be inconvenienced, not benched, and there was
     * no number in the design that said where the ceiling was because the formula had none.
     *
     * <p>The replacement expresses the ceiling directly: the time is a <em>fraction</em> of a
     * published maximum rather than a quotient that can run away. See {@code ThermalRules}.
     *
     * <p>{@code docs/education/02-computer-architecture.md} deliberately states the shape and no
     * number in its {@code thermal-budget(7)} page, precisely so a tuning pass here cannot falsify a
     * teaching page. Keep it that way.
     */
    public static final double THERMAL_LOAD_EXPONENT = 2.0d;

    /**
     * The longest a recovery may take on a rig with nothing stealing from it: <b>five minutes</b>.
     *
     * <p>Reached only in the corner it describes — returning most of the rig's capacity while the
     * rest of it is pinned. It is an asymptote rather than a clip, so load still reads all the way up
     * instead of flattening into a plateau where 80% and 95% feel identical.
     */
    public static final long THERMAL_MAX_CLEAN_SECONDS = 300L;

    /**
     * The longest a recovery may take at all: <b>ten minutes</b>, and only a rig being comprehensively
     * robbed gets near it.
     *
     * <p>⚠ <b>Rogue processes are the only thing that may lift the ceiling above
     * {@link #THERMAL_MAX_CLEAN_SECONDS}, and this is the second of two ways they slow a rig down.</b>
     * The first is ordinary and needs no special case: a parasite holds cycles, so it raises the load
     * factor, so it slows recovery through the curve every other consumer uses. This one is the
     * thermal half — a machine with something else running on it sheds heat worse — and it is
     * separate on purpose, because a player who has cleared their own allocations down to nothing and
     * <em>still</em> sees a slow recovery has been handed the discrepancy
     * {@code docs/design/04-mining.md} §3.1 is built on.
     */
    public static final long THERMAL_MAX_INFESTED_SECONDS = 600L;

    /**
     * What fraction of the ceiling an <em>idle</em> rig still charges, so recovery is never free.
     *
     * <p>Zero here would make an idle rig return cycles instantly, which deletes the resource: the
     * whole point of {@code 01} §1.3 is that spending is a commitment over time rather than a toll.
     */
    public static final double THERMAL_IDLE_FLOOR = 0.12d;

    /** Nothing takes less than this, so a completed recovery is always something the player saw. */
    public static final long THERMAL_MIN_SECONDS = 2L;

    /**
     * How much slower work runs when parasites are eating the rig — {@code 1.0} means a rig with half
     * its capacity stolen runs everything <b>50% slower</b>.
     *
     * <p><strong>[PROPOSAL]</strong>. Proportional and honest: the machine has less of itself to give,
     * so everything it does takes longer. It applies to a task's <em>duration</em> and not to its
     * price, because the cycles a tool needs are a property of the tool and the time it takes is a
     * property of the machine running it.
     *
     * <p>⚠ <b>It applies whether or not the player has found the parasite</b>, which is the point.
     * Alongside the cycles that simply are not there, a rig that has quietly become sluggish is the
     * cheapest possible hint that something is wrong — and unlike a warning, it cannot be dismissed,
     * ignored or read as a false positive.
     */
    public static final double THEFT_SLOWDOWN = 1.0d;

    // ------------------------------------------------------------------ starting position

    /**
     * A new solo character starts with 0 EC and a base rig.
     *
     * <p>{@code docs/design/15-open-questions.md} logs this as <strong>Q-economy-seed</strong> — an
     * undecided question about whether a reset character gets a small onboarding grant. Zero is chosen
     * here because it is the option that cannot be wrong in the direction that matters: a grant can be
     * added later without invalidating anyone's save, whereas taking one away cannot.
     */
    public static final BigInteger STARTING_ETHECOIN_WEI = ec("0");

    /** The Encrypted Vault's starting capacity, in items — {@code docs/design/01-core-resources.md} §6. */
    public static final int STARTING_VAULT_CAPACITY = 6;

    /** Standard Storage — exposed while online. {@code design/01} §6 [PROPOSAL]. */
    public static final int STANDARD_STORAGE_CAPACITY = 20;

    /** The High-Hackable Zone — always exposed, and large because that is the trade. */
    public static final int HIGH_HACKABLE_CAPACITY = 60;

    /**
     * How many slots a tier has, where a slot holds one tool or one stack.
     *
     * <h2>⚠ Published, not yet enforced — and the gap is deliberate rather than forgotten</h2>
     *
     * {@link #STARTING_VAULT_CAPACITY} was declared on the day storage was written and read by
     * nothing for as long as it existed. The STORAGE window now draws a grid against these numbers,
     * so they are finally visible — but {@code moveItem} still does not refuse a move that would
     * overfill a tier, which means a vault can read {@code 8 / 6}. That is rendered honestly as
     * over-capacity rather than hidden, because the alternative is a readout that quietly disagrees
     * with what the player owns.
     *
     * <p>Enforcing it is a <b>rules</b> change — it makes a move fail — and belongs with the
     * Cold Storage Expansion schematic that {@code design/01} §6 pairs it with, since a hard cap of
     * 6 with no way to raise it is a different game from the one that document describes.
     * Invariant I12 constrains how capacity <em>scales</em>, and nothing here sells it.
     */
    public static int storageCapacity(StorageTier tier) {
        return switch (tier) {
            case VAULT -> STARTING_VAULT_CAPACITY;
            case STANDARD_STORAGE -> STANDARD_STORAGE_CAPACITY;
            case HIGH_HACKABLE_ZONE -> HIGH_HACKABLE_CAPACITY;
        };
    }

    // ------------------------------------------------------------------ scan precision

    /**
     * Per-tier false-positive rates — {@code docs/design/04-mining.md} §3.2a, decided 2026-07-26.
     *
     * <p>§3.2a publishes the shape in words and not in numbers: Quick is "cheap, fast, and it will
     * send you chasing ghosts", Full is "the working default", Thorough is "expensive in both
     * compute and attention, and it earns it". These are the first numbers for that shape and are
     * <strong>[PROPOSAL]</strong>.
     *
     * <p>The gap between them is the point rather than the values: a Quick Scan that lies a third of
     * the time and a Thorough Scan that lies one time in twenty-five is what makes the 5-versus-35
     * compute price legible. Compressing the range would make the expensive tier's price
     * unjustifiable; widening it would make the cheap tier useless rather than unreliable, and a
     * tier nobody uses teaches nothing.
     *
     * <p>⚠ These feed {@code docs/education/08-detection-and-defence.md}'s {@code false-positive(7)},
     * {@code base-rate-fallacy(7)} and {@code alert-fatigue(7)} pages, which are three of the
     * curriculum's strongest. Re-tuning here means re-reading those.
     */
    public static final double SCAN_FALSE_POSITIVE_QUICK = 0.35d;

    public static final double SCAN_FALSE_POSITIVE_FULL = 0.15d;
    public static final double SCAN_FALSE_POSITIVE_THOROUGH = 0.04d;

    /**
     * What a standing Detection Array multiplies the rate above by —
     * {@code docs/design/09-defense-and-hardening.md} §2, which closed OQ-6 by redefining the Array
     * as <em>precision</em> rather than sensitivity: "scans buy sensitivity, the Array buys
     * precision, and the two are different axes."
     *
     * <p>Multipliers, not subtractions, and that is the load-bearing choice. A subtraction would let
     * a T3 Array drive the Thorough Scan's 4% to zero and make one tier of one defence into a
     * perfect detector — which removes the doubt the whole detection system exists to create, the
     * same argument {@code docs/design/07-recon-tools.md} §2 makes for the Honeypot Detector's
     * mandatory false-negative rate. Multiplying preserves the ordering of the tiers and can never
     * reach certainty.
     *
     * <p>All three <strong>[PROPOSAL]</strong>. The permanent compute they cost (6 / 14 / 25) is
     * established; what it buys was decided in prose and never in figures.
     */
    public static final double DETECTION_ARRAY_PRECISION_T1 = 0.60d;

    public static final double DETECTION_ARRAY_PRECISION_T2 = 0.35d;
    public static final double DETECTION_ARRAY_PRECISION_T3 = 0.15d;

    /**
     * The chance a Full Scan sees a rootkit-wrapped miner —
     * {@code docs/design/04-mining.md} §3.2, whose Finds column says a Full Scan gets "all unhidden
     * miners; <em>some</em> rootkit-wrapped". This is the number behind "some".
     *
     * <p>Sensitivity, not precision, so the Detection Array does <em>not</em> move it. That
     * separation is what makes the Array non-redundant by construction rather than by tuning
     * ({@code 09} §2) and is the whole content of OQ-6's resolution.
     *
     * <p><strong>[PROPOSAL]</strong>. A coin flip is the honest reading of "some" and keeps
     * {@code docs/design/09}'s Rootkit Wrapper worth its 50 EC without making it a wall — the wall
     * is the deliberate audit ({@code 04} §3.1), which always finds it.
     */
    public static final double SCAN_ROOTKIT_SENSITIVITY_FULL = 0.50d;

    /**
     * The scripted tutorial miner planted on a new character's rig —
     * {@code docs/design/04-mining.md} §5.1, established: "the tutorial flow should <em>plant</em> a
     * weak scripted miner early."
     *
     * <p>Six cycles is deliberately below the 8-cycle default a real deployed miner carries: it is
     * weak, it is meant to be found, and it is the thing the first Quick Scan is wrong <em>about</em>
     * half the time. Tier 1 because the crack it enables is the tutorial for the whole breach system,
     * and because a tier-1 crack can never clear {@link #SCHEMATIC_MATERIAL_MIN_TIER} — the safest
     * introduction in the game must not also be a progression source.
     *
     * <p><strong>[PROPOSAL]</strong> for the figures; the plant itself is established.
     */
    public static final long TUTORIAL_MINER_HOST_CYCLES = 6L;

    public static final int TUTORIAL_MINER_TIER = 1;

    // ------------------------------------------------------------------ schematic material

    /**
     * The engagement tier below which a breach yields no schematic material —
     * {@code docs/design/10-botnets.md} §1a and Invariant I13.
     *
     * <p>§1a's exploit guard, applied to the breach: "the material drop is gated on engagement tier
     * — the bot must have been lost against a defended target above a difficulty threshold. Without
     * this, the optimal play is to build the cheapest junk bot and feed it to a loss." The same
     * failure exists here in a different costume — farm the softest live target for material — and
     * the same guard closes it, reading the same {@code resolutionRecord.difficultyTier}.
     *
     * <p>Tier 3 because that is where {@code docs/design/05-hacking-minigame.md} §3.3's mix first
     * stacks two classes: below it, an attempt tests one kind of thinking, which is not the
     * "engagement" §1a means. <strong>[PROPOSAL]</strong>.
     */
    public static final int SCHEMATIC_MATERIAL_MIN_TIER = 3;

    /** One unit per qualifying breach. <strong>[PROPOSAL]</strong> — see {@link #SCHEMATIC_MATERIAL_PER_UNLOCK}. */
    public static final int SCHEMATIC_MATERIAL_PER_BREACH = 1;

    /**
     * Material for one schematic unlock — {@code docs/design/02-unlock-gates.md} §2.2, decided
     * 2026-07-26 (closing OQ-5).
     *
     * <p>§2.2 fixes the rate against the bot-loss stream: "a schematic costs material equivalent to
     * roughly ten destroyed bot instances", anchored to {@code 10} §2's published 25–35 EC per
     * instance, so about 300 EC of deliberately destroyed value. Twelve qualifying breaches is that
     * figure carried across to the other stream that pays into the same pool.
     *
     * <p>Twelve rather than ten because the two streams cost different things. A bot loss costs
     * ethecoin the player already spent; a tier-3-or-better breach costs an attention budget, a
     * compute reservation, and — on a live target — heat. Pricing them identically would make
     * whichever is cheaper on the day the only one anyone used. ⚠ Both guards from §2.2 stay in
     * force and are what make any rate at this level safe: the tier gate above means material never
     * shortcuts a ceiling the player has not already reached, so this number sets <em>pace</em>,
     * never <em>reach</em>.
     *
     * <p><strong>[PROPOSAL]</strong>, like every other figure in {@code 03}.
     */
    public static final int SCHEMATIC_MATERIAL_PER_UNLOCK = 12;

    // ------------------------------------------------------------------ breach: attention

    /**
     * The published per-action attention costs — {@code docs/design/05-hacking-minigame.md} §4's
     * table, which is <b>decided</b> rather than proposed.
     *
     * <p>These four numbers are the whole loud-versus-patient trade, and §4's own column explains
     * each: a quiet read is "the patient baseline", an ordinary probe is "the default move", a loud
     * tool is "power bought with exposure", and the Side-Channel Reader's zero is "its entire
     * identity" — the only action in the game that costs nothing from the bar.
     *
     * <p>⚠ Do not add a fifth. A new cost tier would need a new row in §4 and a reason a player can
     * feel, and the ratio 1 : 2 : 6 is what makes the choice legible before the click.
     */
    public static final int ATTENTION_QUIET_READ = 1;

    public static final int ATTENTION_PROBE = 2;
    public static final int ATTENTION_LOUD_TOOL = 6;
    public static final int ATTENTION_SIDE_CHANNEL = 0;

    /**
     * The Credential Harvester's in-breach cost — twice an ordinary probe, below a loud tool.
     *
     * <p>The one figure not on {@code docs/design/05-hacking-minigame.md} §4's table, and it needs a
     * reason. {@code docs/design/06-intrusion-tools.md} §2 says harvested credentials "open linked
     * nodes without re-solving the rule", which inside a layer means the tool <em>skips a deduction
     * step</em> — strictly more than a probe buys. But it is a reputation-gated tool, not a loud one,
     * and §1 rates its noise Moderate rather than Very high, so pricing it at the loud tier would
     * make it a worse Fuzzer at the same price.
     *
     * <p>Four is the only value that keeps both relationships true. <strong>[PROPOSAL]</strong>, and
     * flagged in {@code docs/design/16-breach-implementation.md} §7 as the one action cost that is
     * not simply read off §4.
     */
    public static final int ATTENTION_CREDENTIAL_HARVESTER = 4;

    /**
     * What an Overflow Kit bypass costs, as a fraction of the layer's whole budget —
     * {@code docs/design/05-hacking-minigame.md} §4, which prices it as "most of the bar" and adds
     * "the cost is the point".
     *
     * <p>Eighty percent is "most of the bar" made a number, and the residue matters: at 100% the Kit
     * would be a layer-shaped suicide button, and at 50% it would be the default opening on every
     * layer, which is precisely the "panic button with a siren attached, never a default" that
     * {@code docs/design/06-intrusion-tools.md} §2 says it must not become. Twenty percent left is
     * enough to finish a layer you had already half-read and never enough to start one.
     *
     * <p><strong>[PROPOSAL]</strong> for the fraction; the "most of the bar" shape is decided.
     */
    public static final double ATTENTION_BYPASS_FRACTION = 0.80d;

    /**
     * Extra attention a strike burns, on top of whatever the failing action already cost —
     * {@code docs/design/05-hacking-minigame.md} §3.3's "error tolerance (how many wrong probes
     * before an alarm/lockout)", expressed in the only currency §4 leaves.
     *
     * <p>Three is a probe and a half: enough that a guess costs meaningfully more than a deduction,
     * which is the mechanical difference between the Logic class being reasoning and being
     * enumeration, and small enough that a player who miscounts once is not finished.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int ATTENTION_ALARM_PENALTY = 3;

    /**
     * Attention a Tarpit adds to <em>every</em> action's cost —
     * {@code docs/design/09-defense-and-hardening.md} §1: "Slows every intruder action; doesn't stop
     * them, buys response time."
     *
     * <p>A surcharge on each action rather than a cut to the budget, and the distinction is the
     * whole translation: cutting the budget would make a Tarpit a flat difficulty add, which is the
     * <em>Firewall's</em> published function. A per-action surcharge punishes exactly the play the
     * Tarpit is written to punish — many small moves — and leaves a patient reader who takes few
     * moves nearly untouched.
     *
     * <p>⚠ {@code 09} §2 also ties the Tarpit to the bot-backlog timer in {@code 10} §1, which is
     * real-time and completely unaffected by this. The two effects coexist; neither replaces the
     * other. <strong>[PROPOSAL]</strong>.
     */
    public static final int TARPIT_ATTENTION_SURCHARGE = 1;

    /**
     * Attention a Firewall removes from each layer's budget, per tier —
     * {@code docs/design/09-defense-and-hardening.md} §1, whose Function column for the Firewall is
     * exactly "Flat difficulty increase on incoming breach attempts" and nothing more specific.
     *
     * <p>This is {@code docs/design/05-hacking-minigame.md} §8's unpublished item 7 given a number.
     * Two per tier means a T3 Firewall takes six attention off every layer — roughly three ordinary
     * probes, against a budget of twenty-odd. That is felt without being decisive, which is what
     * "flat difficulty increase" asks for and what keeps the Firewall's real cost its 15 permanent
     * cycles ({@code 09} §2) rather than its effect. <strong>[PROPOSAL]</strong>.
     */
    public static final int FIREWALL_BUDGET_PENALTY_PER_TIER = 2;

    /**
     * The floor a layer's budget can never be pushed below by defences.
     *
     * <p>Without it, a T3 Firewall on a tier-5 target would leave 14, and stacking any future
     * defence on the same axis would eventually produce a layer that cannot be cleared by any
     * sequence of legal moves. An unwinnable board is not difficulty; it is the game deciding, which
     * is the one reading {@code docs/design/05-hacking-minigame.md} §1 constraint 4 forbids
     * outright. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_ATTENTION_FLOOR = 8;

    // ------------------------------------------------------------------ breach: noise

    /**
     * Noise a breach generates just by happening, before any action —
     * {@code docs/design/01-core-resources.md} §3 ("noise is generated by acting") and
     * {@code docs/design/05-hacking-minigame.md} §2, which makes {@code noiseGenerated} a
     * first-class output of every attempt.
     *
     * <p>Non-zero on purpose: an attempt that generated nothing would make an aborted breach free,
     * and {@code 05} §4.1 is explicit that on an abort "the noise already generated stays
     * generated". <strong>[PROPOSAL]</strong> — {@code 01} §3.2's own concrete model is tagged the
     * same way.
     */
    public static final int NOISE_BASE = 2;

    /**
     * Per-action noise, mapping {@code docs/design/06-intrusion-tools.md} §1's qualitative Noise
     * column onto {@code 01} §3.2's "noise is a scalar per player-pool".
     *
     * <p>The column gives None / Low / Moderate / Very high; this is that ladder as 0 / 1 / 5 / 12.
     * The Side-Channel Reader's zero is stated twice in the docs ({@code 06} §1's table and §2's
     * "zero noise"), so it is the one value here that is not an interpretation. The Overflow Kit's
     * "**Very high**" is bolded in the source table and is the loudest thing in the tool set, which
     * is why the gap between it and a loud tool is wider than the gap between loud and quiet.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int NOISE_QUIET_READ = 0;

    public static final int NOISE_PROBE = 1;
    public static final int NOISE_LOUD_TOOL = 5;
    public static final int NOISE_BYPASS = 12;
    public static final int NOISE_SIDE_CHANNEL = 0;

    /**
     * Noise added per alarm tripped.
     *
     * <p>An alarm is the defender noticing, which is the definition of being loud. Pricing it at
     * four puts a single strike between an ordinary probe and a loud tool, so a clumsy quiet run can
     * end up noisier than a clean loud one — which is the correct lesson and the one
     * {@code docs/design/08-stealth-and-noise.md} §4 assumes when it pairs each offensive escalation
     * with a stealth counter. <strong>[PROPOSAL]</strong>.
     */
    public static final int NOISE_PER_ALARM = 4;

    /**
     * How much noise converts into one point of personal heat.
     *
     * <p>{@code docs/design/01-core-resources.md} §3.2: "noise is per-action and decaying; heat is
     * accumulated standing. Noise is tactical, heat is strategic." A divisor is that sentence made
     * arithmetic — most of a breach's noise never becomes heat at all, and only a genuinely loud
     * attempt leaves a mark that outlives the session.
     *
     * <p>At eight, a clean quiet breach of a small target leaves nothing, and a bypass-and-alarms
     * attempt leaves two or three points on a 0–100 scale. Reaching the named-hacker band ({@code
     * 01} §4.1) therefore takes a campaign rather than an evening, which is what that band is for.
     * <strong>[PROPOSAL]</strong>.
     *
     * <p>⚠ Invariant I9 short-circuits all of this on a miner crack: zero heat, on every outcome,
     * including failure. Defending your own rig never contributes to being wanted.
     */
    public static final int NOISE_PER_HEAT_POINT = 8;

    /** Personal heat is a 0–100 scale — {@code docs/design/01-core-resources.md} §4.1's bands. */
    public static final int PERSONAL_HEAT_MAX = 100;

    // ------------------------------------------------------------------ breach: session cost

    /**
     * Cycles a breach attempt reserves for itself, before the loadout —
     * {@code docs/design/05-hacking-minigame.md} §2, which instantiates an attempt with
     * {@code equippedTools} "each of which modifies the attempt", implying a cost for the attempt
     * itself that the tool list does not carry.
     *
     * <p>Bracketed by numbers that already exist: a Quick Scan is 5 and a Full Scan is 15 ({@code
     * 04} §3.2), and the Overflow Kit — the one tool defined entirely in terms of being inside a
     * breach — is 10 ({@code 06} §1). Ten puts an attempt above a cheap look and below a serious
     * sweep, which is the right relative price for something that is a commitment rather than a
     * glance.
     *
     * <p>Held for the whole attempt and released into recovery at resolution, exactly like a scan
     * (UI-6). <strong>[PROPOSAL]</strong>.
     */
    public static final long BREACH_SESSION_CYCLES = 10L;

    // ------------------------------------------------------------------ breach: tier tables

    /**
     * Layers per attempt, by difficulty tier — {@code docs/design/05-hacking-minigame.md} §3.3,
     * which makes {@code difficultyTier} scale "layer count, class mix, time pressure and error
     * tolerance", and §3.1, where "a given target composes 1-N layers".
     *
     * <p>1, 1, 2, 3, 3. Growth stops at three because attention per layer is already falling across
     * the tiers — a fourth layer would be attrition rather than difficulty. Every layer of an attempt
     * plays the same class ({@code BoardFactory}), so stacking more of them is stacking more of one
     * puzzle, which is a length knob and not a skill one.
     *
     * <p>A method rather than an array constant: {@code public static final int[]} is writable by
     * anyone who holds it, and a balance table that any caller can silently edit is worse than no
     * table. <strong>[PROPOSAL]</strong>.
     */
    public static int breachLayers(int tier) {
        return switch (clampTier(tier)) {
            case 1, 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    /**
     * Attention granted per layer, by tier, before defensive modifiers — §3.3's "time pressure" knob
     * translated into the only currency §4 leaves.
     *
     * <p>26, 24, 22, 22, 20. It <em>falls</em> as boards grow, which is the whole difficulty curve:
     * a tier-5 Logic board has a keyspace of 100 000 against a tier-2 board's 1296, and it gets four
     * fewer attention to crack it. Reading the two tables together is the only way either makes
     * sense, which is why they sit next to each other.
     *
     * <p>⚠ {@code 05} §3.3 still says "time pressure (trace timer speed)". That is residual pre-§4
     * wording — §4 removed the wall clock outright and there is no timer. Flagged for the
     * integrator. <strong>[PROPOSAL]</strong>.
     */
    public static int breachAttention(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 26;
            case 2 -> 24;
            case 3, 4 -> 22;
            default -> 20;
        };
    }

    /**
     * Strikes a layer tolerates before it locks out — §3.3's "error tolerance (how many wrong probes
     * before an alarm/lockout)".
     *
     * <p>4, 3, 3, 2, 2. Four at tier 1 is the tutorial being forgiving on the player's own rig,
     * where Invariant I9 already guarantees a loss costs no heat; two at the top is where a wrong
     * read is genuinely expensive. <strong>[PROPOSAL]</strong>.
     */
    public static int breachStrikeLimit(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 4;
            case 2, 3 -> 3;
            default -> 2;
        };
    }

    /** Clamps to the 1–5 scale {@code DifficultyTier} publishes, so a hand-edited save cannot crash generation. */
    private static int clampTier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    // ------------------------------------------------------------------ breach: board generation

    /**
     * Enumeration board size — {@code docs/design/05-hacking-minigame.md} §3.1 ("map a node's open
     * ports/services before you can act") and §3.3's layer scaling.
     *
     * <p>12 slots at tier 1 growing by 2 a tier, in bands of 4. Twelve is three full bands, which is
     * the smallest board on which a sweep is worth its 1 attention against a probe's 2 — on a
     * smaller board, probing everything is simply cheaper, and the class's central trade would not
     * exist at tier 1 where it is being taught. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_ENUM_SLOTS_BASE = 12;

    public static final int BREACH_ENUM_SLOTS_PER_TIER = 2;
    public static final int BREACH_ENUM_BAND_SIZE = 4;

    /**
     * Logic code length and alphabet size — {@code docs/design/05-hacking-minigame.md} §3.1's
     * "Mastermind-family reasoning".
     *
     * <p>Length {@code 3 + tier/2} against an alphabet of {@code 5 + tier}: keyspaces of 216, 1296,
     * 4096, 6561 and 100 000. The jump at tier 5 is deliberate — it is where the class stops being
     * solvable by holding candidates in your head and starts requiring the readout the board
     * publishes.
     *
     * <p>Ten symbols is the ceiling because the alphabet is a fixed ASCII set (see
     * {@code LogicRules}); adding an eleventh means adding a character, and every character the
     * client draws has to exist in the bundled font. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_LENGTH_BASE = 3;

    public static final int BREACH_LOGIC_ALPHABET_BASE = 5;

    /**
     * The tier at and above which a Logic code is always salted, and the chance it is salted below
     * that — {@code docs/design/06-intrusion-tools.md} §2, which makes the Rainbow Table
     * "hard-countered by salting, by design" and "a conditional power spike: devastating against
     * lazy targets, useless against prepared ones, so it rewards recon".
     *
     * <p>The 30% below tier 3 is what makes it conditional rather than binary. If low tiers were
     * never salted the Table would be an unconditional win on exactly the targets a new owner can
     * reach, and §2's "know before you buy the attempt" would have nothing to know.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_ALWAYS_SALTED_TIER = 3;

    public static final double BREACH_LOGIC_SALT_CHANCE = 0.30d;

    /**
     * Probes in one Fuzzer volley, and the tier at which a volley starts also costing a strike.
     *
     * <p>{@code docs/design/06-intrusion-tools.md} §2 calls the Fuzzer "the entry-level 'I don't know
     * the rule, so I'll hammer it' tool" with "moderate noise as the cost of impatience". Four
     * guesses for 6 attention is breadth at three times a probe's price, paid in quality: a volley
     * returns exact counts only, never partials.
     *
     * <p>From tier 4 the hammer starts setting off alarms, which is where the tool stops scaling and
     * the class stops being brute-forceable — the mechanical form of §3.2's "a defended/high-tier
     * node can be built to defeat a fixed strategy". <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_VOLLEY_SIZE = 4;

    public static final int BREACH_LOGIC_VOLLEY_ALARM_TIER = 4;

    /**
     * Positions a Rainbow Table reveals against an unsalted code —
     * {@code docs/design/06-intrusion-tools.md} §1, whose Function column is "instant crack against
     * weak or reused credentials".
     *
     * <p>Two rather than all of them. A full reveal would make the Table skip the layer, which is the
     * Overflow Kit's job and is proof-of-skill-gated for exactly that reason ({@code 02} §2.4) — an
     * EC-plus-schematic item must not do a proof-of-skill item's work. Two positions collapse the
     * keyspace by a factor of the alphabet squared and still leave a deduction to finish.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_RAINBOW_REVEALS = 2;

    /**
     * Traversal lattice shape — {@code docs/design/05-hacking-minigame.md} §3.1 ("route through an
     * internal graph to the data node") and §3.2's decoy requirement.
     *
     * <p>{@code 3 + tier/2} ranks, 3–4 nodes wide, with {@code 2 + tier/2} objective candidates on
     * the final rank. The candidate count is what {@code P-3} is measured against: a fixed heuristic
     * that cannot read the logs must extract at random and averages {@code (K+1)/2} attempts, while
     * a reader gets it in one. That gap is the number, and it must not be tuned away — see
     * {@code docs/design/16-breach-implementation.md} §5. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_TRAVERSAL_RANKS_BASE = 3;

    public static final int BREACH_TRAVERSAL_WIDTH_MIN = 3;
    public static final int BREACH_TRAVERSAL_WIDTH_MAX = 4;
    public static final int BREACH_TRAVERSAL_OBJECTIVES_BASE = 2;

    /**
     * Extra attention a tarpit node charges on entry, and how many ranks a {@code traceroute}
     * reveals ahead of the current node.
     *
     * <p>Two ranks is the Topology Mapper's published reach ({@code docs/design/07-recon-tools.md}
     * §1: "extends graph visibility from one hop to two"), reused here because a loud in-breach tool
     * should buy what the quiet pre-breach tool buys and pay for it in noise instead of a schematic.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_TRAVERSAL_TARPIT_STEP_COST = 2;

    public static final int BREACH_TRACEROUTE_RANKS = 2;

    // ================================================================== the network (design/17)
    //
    // Two sentences hold this whole block together, and every number below is one of them made
    // arithmetic:
    //
    //   1. SCHEMATICS BUY REACH, ETHECOIN BUYS SENSITIVITY. The hop ceiling is 1, or 2 with the
    //      Topology Mapper schematic — docs/design/07-recon-tools.md §2 calls that tool "a CEILING on
    //      information ... hence schematic-gated not purchasable (Invariant I2)". No sweep tier at
    //      any price appears in NetRules.hopCeiling, and there is no constant here it could read.
    //      What the tiers move is the PROBABILITY of detecting what is already in reach, which is
    //      breadth, which docs/design/02-unlock-gates.md §1.1 step 4 puts on ethecoin.
    //
    //   2. DETECTION IS ROLLED ONCE, AT GENERATION, AND STORED. Every table below that produces a
    //      threshold is compared against HostState.detectRoll, which the world was built with. A
    //      sweep makes no detection draw, so re-sweeping is never a reroll and save-scumming is
    //      defeated by construction rather than by a cooldown.

    // ------------------------------------------------------------------ network: world size

    /**
     * Five to seven virtual servers — the brief's ceiling, and a floor that keeps the depth gradient
     * usable.
     *
     * <p>Seven is a hard cap on how much world one save may carry: at the machine cap below it is
     * 350 hosts in a file rewritten every thirty seconds. Five is the floor because a depth-biased
     * spanning tree over four servers frequently produces a maximum depth of two, and the difficulty
     * tables run to four — a world whose deep rows never appear is a world where half the tuning is
     * decoration. <strong>[PROPOSAL]</strong>.
     */
    public static final int NET_SERVERS_MIN = 5;

    public static final int NET_SERVERS_MAX = 7;

    /**
     * The chance a newly placed server attaches to one of the currently deepest servers rather than
     * to any already-placed one.
     *
     * <p>This single number is the shape of the world. At 0.0 the tree is a bush — every server one
     * hop from home, no gradient at all. At 1.0 it is a chain — one path, no choice about which way
     * to push, and a depth that is forced rather than chosen. Sixty percent leans deep while still
     * branching often enough that the player is regularly choosing between two directions, which is
     * the decision the map exists to present. <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SERVER_DEEPEN_BIAS = 0.60d;

    /**
     * Chance and cap for the extra server-graph edges added on top of the spanning tree.
     *
     * <p>Chords are what make the picture read as a network rather than a taxonomy: without them
     * every route is the unique tree path and the graph view is a family tree. Two is enough to be
     * felt on a five-to-seven node graph and few enough that the tree is still obviously the
     * skeleton.
     *
     * <p>⚠ A chord may only join servers whose depths differ by at most one, and that constraint is
     * load-bearing rather than cosmetic — see {@link io.github.stoicswe.eyeandsickle.solo.state.ServerState}.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SERVER_CHORD_CHANCE = 0.35d;

    public static final int NET_SERVER_CHORD_MAX = 2;

    /**
     * Chance of an extra link between two machines on the same server, per host.
     *
     * <p>The intra-server graph is a spanning tree first (so every machine is reachable by
     * construction, never by a retry loop) and then this. Without it, every host has exactly one
     * route and taking a foothold never opens more than one new direction, which would make the
     * vantage mechanic — reposition to see further — a straight line instead of a choice.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_INTRA_CHORD_CHANCE = 0.22d;

    /** The brief's hard ceiling: never more than fifty machines on one server. */
    public static final int NET_MACHINES_HARD_CAP = 50;

    // ------------------------------------------------------------------ network: the home floor

    /**
     * How many machines the player's rig is guaranteed to sit one link from, and how many of those
     * are guaranteed to be workable first contacts.
     *
     * <p>⚠ These two are not tuning. They are the fix for "discovery is unusable at the start", and
     * they are applied deterministically after every roll: the first three non-gateway hosts at one
     * link from the rig are forced to {@code detectRoll = 0.0}, tier 1, firewall 0, undefended, with
     * a payout floor. Zero is below the base T1 sweep's <em>worst</em> threshold (0.35, a quiet
     * machine at one hop), so <b>the first sweep a new player runs always returns at least three
     * breachable, un-firewalled targets worth at least 3 EC each</b>, on every seed, forever.
     *
     * <p>Five neighbours rather than three so the guarantee is not also the whole board — a new
     * player should have something to miss and then find with a better instrument, which is what
     * teaches that sensitivity is a purchase. <strong>[PROPOSAL]</strong> in the figures; the
     * guarantee itself is not.
     */
    public static final int NET_HOME_SEED_NEIGHBOURS = 5;

    public static final int NET_HOME_GUARANTEED_CONTACTS = 3;

    /**
     * The payout floor on a guaranteed first contact, in minor units — 3 EC.
     *
     * <p>The bottom of the T1 loot band, restated as a floor so that the three machines a new player
     * is guaranteed to find are also guaranteed to be worth breaching. Three of them is 9 EC against
     * the 15 EC Passive Sniffer, which is the intended first purchase and the intended second or
     * third session's worth of work. <strong>[PROPOSAL]</strong>.
     */
    public static final BigInteger NET_LOOT_FLOOR_WEI = ec("3");

    // ------------------------------------------------------------------ network: the sweep line

    /**
     * How much a hop of distance costs a sweep's detection chance.
     *
     * <p>{@code docs/design/07-recon-tools.md} §2 makes the Topology Mapper a <em>reach</em>
     * purchase — "extends graph visibility from one hop to two" — and pointedly not a clarity one.
     * So the second hop is real and coarse: everything visible at one hop is more likely to be seen
     * than the same machine at two, and the schematic buys the ability to look at all rather than a
     * better look. <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_HOP_FACTOR_1 = 1.00d;

    public static final double NET_HOP_FACTOR_2 = 0.60d;

    /**
     * Compute each sweep tier holds while it runs — inside {@code docs/design/07-recon-tools.md}
     * §1's established 2–14 recon range.
     *
     * <p>⚠ <b>These are the sweep's price in capacity, and are no longer also its noise.</b> They
     * used to be both, via the {@code CONTROL_CHANNEL} reservation, and the identity was elegant and
     * measurably wrong on screen: noise is drawn as outward cycles over <em>rig capacity</em>, so a
     * base sweep on a 100-cycle rig moved the meter by two percent — indistinguishable from silence,
     * and getting quieter the bigger the player's rig grew, which inverts the reading. Loudness is
     * now stated separately in {@link #NET_SWEEP_BASE_NOISE} and the two are free to differ, because
     * how much of your machine a job occupies and how much racket it makes outside it are genuinely
     * different quantities. See {@code docs/design/08-stealth-and-noise.md} §1: noise is generated by
     * <em>acting</em>, and the act here is touching machines that are not yours.
     */
    public static final long NET_SWEEP_BASE_CYCLES = 2L;

    public static final long NET_SWEEP_WIDE_CYCLES = 5L;
    public static final long NET_SWEEP_DEEP_CYCLES = 9L;

    /**
     * How loud each sweep tier is while it runs, on the same 0–{@code totalCycles} scale the noise
     * meter reads — so 35 on a 100-cycle rig is a bit over a third of the meter.
     *
     * <p><strong>[PROPOSAL]</strong>. A sweep is intrusive by construction: it puts packets on
     * machines the player does not own and has no business touching, and
     * {@code docs/design/08-stealth-and-noise.md} §1 makes that exactly what noise measures. Every
     * tier is therefore loud, and the ladder is loudness, not just sensitivity — a deep sweep listens
     * harder <em>by shouting louder</em>, which is what makes buying up the ladder a decision rather
     * than a strict upgrade. The precedent is {@code docs/design/07-recon-tools.md} §1's Ping Sweep,
     * the one recon tool the table already marks <b>High</b> for exactly this reason.
     *
     * <p>⚠ <b>It is loud only while it runs.</b> The allocation goes into thermal recovery the moment
     * the sweep settles, and recovering cycles are excluded from the noise sum — so the meter drops
     * back to whatever the rig was doing before. Noise is a rate, not a debt; what persists after a
     * loud act is <em>heat</em>, which is a different field and is charged by different rules.
     *
     * <p>⚠ Deliberately below {@code totalCycles} even at the top of the ladder. A tier that pinned
     * the meter would erase the distinction between "loud" and "as loud as this rig gets", and the
     * player needs the headroom to read a sweep running <em>on top of</em> something else.
     */
    public static final long NET_SWEEP_BASE_NOISE = 35L;

    public static final long NET_SWEEP_WIDE_NOISE = 55L;
    public static final long NET_SWEEP_DEEP_NOISE = 80L;

    /**
     * Wall-clock duration of each sweep tier, in seconds.
     *
     * <p>Bracketed by the scan ladder ({@code docs/design/04-mining.md} §3.2: 30 s / 120 s / 360 s).
     * A base sweep is shorter than a Quick Scan because it is the verb a new player runs before they
     * own anything, and a tool whose floor is a thirty-second wait is a tool they run once.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final long NET_SWEEP_BASE_SECONDS = 20L;

    public static final long NET_SWEEP_WIDE_SECONDS = 45L;
    public static final long NET_SWEEP_DEEP_SECONDS = 90L;

    /**
     * Ethecoin prices for the two purchasable sweep tiers.
     *
     * <p>Priced against {@code docs/design/03-economy.md} §2's bands, and 25 EC sits deliberately
     * <em>between</em> the consumable band (5–15) and the mid-tier band (40–60). Both edges are
     * forced: it is a permanent tool, so it must cost more than the 15 EC Passive Sniffer, and it is
     * the first upgrade a new player buys, so pricing it at 40 would put it roughly two hours out of
     * reach on §3's 20–30 EC/hr cautious net. The Provenance Tracer's established 30 EC is the
     * precedent for a permanent tool priced off-band.
     *
     * <p>55 EC is squarely inside the mid-tier band and is about one cautious session — {@code 03}
     * §2's own rule for that band is that losing one costs an evening. <strong>[PROPOSAL]</strong>.
     */
    public static final BigInteger NET_SWEEP_WIDE_PRICE = ec("25"); // 25 EC

    public static final BigInteger NET_SWEEP_DEEP_PRICE = ec("55"); // 55 EC

    /**
     * Depth 0 is never counter-hacked, and this is a constant rather than a table row that happens to
     * be zero.
     *
     * <p>⚠ Stated as its own named value so a re-tune of {@link #netCounterHackChance} cannot make it
     * non-zero by accident. <b>A player who has never left home is never counter-hacked</b>: the home
     * server is where the game teaches, and a teaching space that occasionally plants a parasite on
     * the student is a teaching space they learn to avoid.
     */
    public static final double NET_COUNTER_HACK_HOME = 0.0d;

    /**
     * What a live breach adds to the noise meter before the player has done anything loud in it.
     *
     * <p>⚠ <b>Deliberately well below a sweep.</b> The base sweep sits at
     * {@link #NET_SWEEP_BASE_NOISE} and it is the loudest thing in the game for its cost, because a
     * sweep touches every machine within reach and announces itself to all of them. A breach is one
     * connection to one machine — quieter by nature, and quiet is a strategy inside it: a player who
     * only reads is barely making a sound.
     *
     * <p>But it is not zero, and that is the point of the floor. Being <em>inside</em> somebody
     * else's machine is an act, and {@code docs/design/08-stealth-and-noise.md} §1 charges acts. A
     * breach that registered nothing on the meter would make the safest possible play "stay in a
     * breach forever", which is the opposite of the tension {@code docs/design/05} §4 is built on.
     */
    public static final long BREACH_NOISE_FLOOR = 6L;

    /**
     * The loudest a breach can read on the meter, however badly it goes.
     *
     * <p>Below {@link #NET_SWEEP_BASE_NOISE}, so <b>the cheapest sweep is still louder than the
     * worst breach</b>. That ordering is the balance statement and it must survive re-tuning: a
     * breach that could out-shout a sweep would make the sweep ladder's whole price — 2 cycles for
     * the loudest act available — read as a mistake.
     */
    public static final long BREACH_NOISE_CEILING = 26L;

    /**
     * The burst of noise left behind by <b>abandoning</b> a breach, in cycles.
     *
     * <h2>Why walking away is not free, and why the cost is noise rather than anything else</h2>
     *
     * Aborting is a sanctioned outcome ({@code docs/design/05} §4) and it stays one — the attention
     * already spent stays spent and nothing else is taken. But dropping a live connection mid-session
     * is a conspicuous thing to do on somebody else's machine, and until 2026-07-27 it was the
     * quietest possible exit: the breach's noise contribution simply stopped. That made "open a
     * breach, look at the board, leave if it is ugly" a free reroll on difficulty.
     *
     * <p>So an abandonment radiates for a few seconds afterwards, and being loud is exactly what
     * makes a rig worth a sweep. The penalty is a window in which the player is easier to find,
     * which is a consequence they can play around rather than a number taken off them.
     *
     * <p>⚠ <b>30, which keeps the documented ordering intact.</b> Above
     * {@link #BREACH_NOISE_CEILING} — the exit is louder than the attempt was, which is the point —
     * and still below {@link #NET_SWEEP_BASE_NOISE}, so "the cheapest sweep is still louder than
     * anything a breach can do" survives.
     */
    public static final long BREACH_ABANDON_SPIKE_CYCLES = 30L;

    /** The shortest an abandonment keeps radiating. */
    public static final long BREACH_ABANDON_SPIKE_MIN_SECONDS = 5L;

    /**
     * The longest it does.
     *
     * <p>Drawn per abandonment rather than fixed, so a player cannot learn one number and wait it
     * out precisely. The band is narrow enough to stay a nuisance rather than a punishment.
     */
    public static final long BREACH_ABANDON_SPIKE_MAX_SECONDS = 20L;

    /**
     * How much of a breach's accumulated in-puzzle noise reaches the meter.
     *
     * <p>{@code BreachState.noise} is a puzzle-scale figure — a bypass is 12, an alarm is 4 — and the
     * meter is on the rig's 0-to-capacity scale. One-for-one is the honest mapping: a bypass on a
     * hundred-cycle rig moves the needle twelve percent, which is roughly what "you just kicked the
     * door" should look like from outside.
     */
    public static final double BREACH_NOISE_PER_POINT = 1.0d;

    // ------------------------------------------------------------------ the two minigames

    /**
     * How often an attempt draws Breach Protocol rather than the offset cipher, against a machine
     * <b>nothing is known about</b>.
     *
     * <h2>The offset cipher is the default, and recon is what buys the other one</h2>
     *
     * This used to be an even coin flip and the split is now earned. Walking blind into a machine
     * gets the cipher: it is the puzzle that needs no knowledge of the far side, because working an
     * offset out from ciphertext is exactly what you do when you have nothing else. Breach Protocol
     * is the puzzle of someone who knows the machine — its grid is that host's own protocol surface —
     * so it is what a filled-in port-scan report unlocks.
     *
     * <p>That gives RECON a mechanical consequence it did not have. A report was intelligence a
     * player read and acted on by hand; it now changes what the breach <em>is</em>.
     *
     * <p>⚠ <b>It buys a different puzzle, not an easier one.</b> The two are priced the same — same
     * tier, same attention budget, same strike limit, same layer count — and the intended reading is
     * that a player picks up recon to reach the puzzle they are better at, not to lower the bar. If
     * the two ever stop being comparable in difficulty, this stops being a choice and becomes a
     * discount, which is the thing to watch when either is re-tuned. <strong>[PROPOSAL]</strong>.
     *
     * @see #BREACH_PROTOCOL_SHARE_INFORMED
     */
    public static final double BREACH_PROTOCOL_SHARE = 0.0d;

    /**
     * How often a <b>fully scanned</b> machine draws Breach Protocol.
     *
     * <p>⚠ Not 1.0, deliberately. A complete report should make the protocol grid the overwhelming
     * expectation without making it a certainty — a machine that can still surprise a well-prepared
     * operator once in twenty is the fiction working, and a guaranteed puzzle means the cipher stops
     * being practised by anyone who scans. The player is told which one they drew before they spend
     * anything ({@code BoardFactory}), so the residual is a surprise they can walk away from.
     */
    public static final double BREACH_PROTOCOL_SHARE_INFORMED = 0.95d;

    /**
     * The chance of drawing Breach Protocol against a machine whose report is {@code known} complete.
     *
     * <p>Linear between {@link #BREACH_PROTOCOL_SHARE} and {@link #BREACH_PROTOCOL_SHARE_INFORMED},
     * so each of the seven findings is worth the same increment and there is no threshold to
     * discover — a player who scans one more thing sees the odds move, which is what makes the
     * relationship learnable at all.
     *
     * @param known how much of the report is filled in, 0…1
     */
    public static double breachProtocolShare(double known) {
        double fraction = Math.clamp(known, 0.0d, 1.0d);
        return BREACH_PROTOCOL_SHARE
                + (BREACH_PROTOCOL_SHARE_INFORMED - BREACH_PROTOCOL_SHARE) * fraction;
    }

    /**
     * How much louder the offset cipher is than Breach Protocol, as a multiplier on the layer's noise.
     *
     * <p>⚠ <b>This is the cipher's price for having no clock.</b> Breach Protocol is bounded by its
     * buffer — a handful of picks and it is over either way — while the cipher lets the player sit
     * there working through sixteen bytes for as long as they like. Something has to answer "why not
     * take all day", and the honest answer is that all day is spent <em>on somebody else's wire</em>.
     * Patience costs exposure rather than time, which keeps {@code docs/design/05} §4's decision to
     * remove the wall clock intact while still charging for the thing the clock used to charge for.
     *
     * <p><strong>[PROPOSAL]</strong>. Kept as a multiplier rather than a flat addition so the
     * relationship survives a re-tune of the underlying noise numbers: the cipher is <em>louder than
     * the grid</em>, whatever the grid turns out to cost.
     */
    public static final double BREACH_CIPHER_NOISE_FACTOR = 1.8d;

    /**
     * A breach's noise points after its puzzle class has had its say — the one place the factor lands.
     *
     * <p>⚠ Applied to the <b>total</b> rather than per action, and that is the difference between
     * "the cipher is louder" and "the cipher punishes you for pressing things". A per-action
     * multiplier would make the cipher quieter overall, because it has far fewer paid moves than a
     * grid does: three commits against eight picks. Scaling the total is what actually delivers the
     * rule, and it flows through {@code NoiseRules} to the meter and through {@code BreachRules} to
     * heat and the counter-hack roll — one number, three consequences, no chance of them disagreeing.
     */
    public static int breachNoisePoints(String puzzleClass, int rawNoise) {
        return "OFFSET_CIPHER".equals(puzzleClass)
                ? (int) Math.round(rawNoise * BREACH_CIPHER_NOISE_FACTOR)
                : rawNoise;
    }

    /** The side of a protocol grid: 5 at tier 1, 7 at the top. */
    public static int breachMatrixSize(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1, 2 -> 5;
            case 3 -> 6;
            default -> 7;
        };
    }

    /**
     * How many picks a protocol attempt gets.
     *
     * <p>⚠ Grows with tier even though the puzzle gets harder, and that is not a mistake: a bigger
     * grid with more goals needs a longer buffer to be solvable at all. The difficulty comes from
     * needing to land <em>more sequences</em> inside it, not from having fewer slots.
     */
    public static int breachBufferSize(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> 4;
            case 2 -> 5;
            case 3 -> 6;
            case 4 -> 7;
            default -> 8;
        };
    }

    /** How many sequences a protocol attempt offers. Clearing any one of them clears the layer. */
    public static int breachGoalCount(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }

    /** How long the {@code goal}-th sequence is. Later goals are longer and worth more. */
    public static int breachGoalLength(int tier, int goal) {
        return Math.min(breachBufferSize(tier), 2 + Math.max(1, Math.min(3, tier)) / 2 + goal);
    }

    /**
     * How many bytes a cipher asks the player to subtract: 6 at tier 1, 16 at the top.
     *
     * <p>The full published range. Sixteen bytes of hex subtraction with borrows is a real piece of
     * work and is meant to be — it is the top of a five-tier scale, not the ordinary case.
     */
    /**
     * The chance an offset board arrives with some columns already solved.
     *
     * <h2>Why a board would come part-done at all</h2>
     *
     * The cipher's difficulty is arithmetic care, and its <em>cost</em> is time — sixteen columns of
     * subtraction is a lot of keystrokes for a layer a player may be doing for the fourth time
     * tonight. Giving a few columns away removes tedium without removing the test: the ones left
     * are the same arithmetic, and a wrong commit still costs a strike.
     *
     * <p>⚠ It does not touch {@code I7}. Proof-of-skill gates are <b>tier-gated, never
     * count-gated</b>, and a partly-filled board is the same tier it always was. What changes is how
     * long a layer takes, not what clearing one proves.
     */
    public static final double CIPHER_PREFILL_CHANCE = 0.55d;

    /** On top of the base give, a rare second helping. See {@link #CIPHER_PREFILL_CHANCE}. */
    public static final double CIPHER_PREFILL_BONUS_CHANCE = 0.12d;

    /** The base give: 1–3 columns when it happens at all. */
    public static final int CIPHER_PREFILL_BASE_MAX = 3;

    /** The bonus give: a further 1–2. */
    public static final int CIPHER_PREFILL_BONUS_MAX = 2;

    /** The most cells the generator will ever hand over, before the per-board cap. */
    public static final int CIPHER_PREFILL_CEILING =
            CIPHER_PREFILL_BASE_MAX + CIPHER_PREFILL_BONUS_MAX;

    /**
     * How many columns a board of this length may have given away.
     *
     * <h2>⚠ A third, and the cap is the part that matters</h2>
     *
     * Without it a 6-byte tier-1 board could arrive with 5 of its 6 columns done, which is not a
     * shorter puzzle but an absent one. A third scales the relief with the thing it is relieving:
     * the tedium is proportional to length, so the give should be too. In practice that is 2 columns
     * at tier 1 and 5 at tier 5 — so the full 1–3 plus 1–2 only ever lands on the long boards, which
     * are the only ones anybody complained about.
     */
    public static int cipherPrefillCap(int length) {
        return Math.min(CIPHER_PREFILL_CEILING, length / 3);
    }

    public static int breachCipherLength(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> 6;
            case 2 -> 8;
            case 3 -> 10;
            case 4 -> 13;
            default -> 16;
        };
    }

    /**
     * The chance a resolved breach gets you hacked back, given how loud it was and how deep.
     *
     * <h2>Noise is the whole variable, which is what makes quiet play a real strategy</h2>
     *
     * A breach that never went past {@code QUIET_READ} resolves at {@link #NOISE_BASE} and is very
     * nearly safe. One that leant on the Overflow Kit and tripped two canaries is several times that
     * and is genuinely dangerous. {@code docs/design/05} §4 makes trace the in-puzzle cost of being
     * loud; this is the out-of-puzzle one, and having both is what stops "just bypass everything"
     * being free once the trace bar is survivable.
     *
     * <p>⚠ <b>Depth zero is always zero</b>, the same rule {@link #NET_COUNTER_HACK_HOME} fixes for
     * sweeps and for the same reason: the home server is where the game teaches, and a teaching space
     * that occasionally plants a parasite on the student is one they learn to avoid.
     *
     * <p>⚠ <b>A crack is never rolled for at all</b> and the caller must not call this for one. It is
     * a breach against a process on the player's own rig — nothing leaves the machine, so there is
     * nobody to answer. That is Invariant <b>I9</b> and it is what makes the crack safe to lose
     * repeatedly and therefore usable as the tutorial ({@code docs/design/04-mining.md} §5.1).
     *
     * @param noise the breach's resolved noise — {@code BreachState.resolvedNoise}
     * @param depth the target server's depth from home
     */
    public static double breachCounterHackChance(int noise, int depth) {
        if (netDepth(depth) <= 0) {
            return NET_COUNTER_HACK_HOME;
        }
        // Scaled off the same depth table a sweep uses, so the two paths cannot drift apart, and
        // multiplied by how loud this attempt was against a quiet one. A silent breach reads about
        // a third of a sweep's chance; a very loud one reads about double it.
        double loudness = Math.max(0.35d, Math.min(2.0d, noise / (double) BREACH_NOISE_REFERENCE));
        return netCounterHackChance(depth) * loudness;
    }

    /**
     * The noise a merely-competent breach makes — the divisor {@link #breachCounterHackChance} is
     * measured against.
     *
     * <p>Roughly {@link #NOISE_BASE} plus a couple of probes and one loud tool. A breach at exactly
     * this figure carries the same risk a sweep of the same depth does; quieter is safer, louder is
     * not.
     */
    public static final int BREACH_NOISE_REFERENCE = 10;

    // ------------------------------------------------------------------ network: depth tables
    //
    // Methods, never int[]. See breachLayers above: "a balance table that any caller can silently
    // edit is worse than no table." Every one of these clamps its depth through netDepth first, so a
    // hand-edited save carrying a depth of 40 reads the deepest published row rather than crashing.

    /**
     * Clamps a server's BFS depth to the {@code 0..4} range every table below publishes.
     *
     * <p>Deeper servers read the row for 4. A depth-biased tree over seven servers can reach six, and
     * inventing rows for depths the tables were never tuned against would be tuning by extrapolation.
     */
    public static int netDepth(int depth) {
        return Math.max(0, Math.min(4, depth));
    }

    /**
     * Machines per server, by depth — the brief's "max 50 machines per server", with home smallest.
     *
     * <p>Home is 12–20 because it is the tutorial floor and has to be legible: the list view is
     * exhaustive and the graph view clamps at ten rows a layer, so a home server of fifty machines
     * would introduce the overflow marker on the first screen a player ever sees. Deeper servers grow
     * to the cap, which is where the split between the legible surface and the exhaustive one starts
     * doing real work. <strong>[PROPOSAL]</strong>.
     */
    public static int netMachinesMin(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 12;
            case 1 -> 18;
            case 2 -> 24;
            case 3 -> 30;
            default -> 34;
        };
    }

    /** @see #netMachinesMin */
    public static int netMachinesMax(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 20;
            case 1 -> 30;
            case 2 -> 38;
            case 3 -> 46;
            default -> NET_MACHINES_HARD_CAP;
        };
    }

    /**
     * Host kind for a rolled {@code u}, by depth — cumulative left to right over TERMINAL, RELAY,
     * STORE, SENTRY.
     *
     * <p>The gradient is the world getting less civilian and more instrumented as you leave home:
     * TERMINAL falls from 0.60 to 0.20 while SENTRY rises from 0.03 to 0.40. A {@code TERMINAL} is a
     * citizen's or clerk's desktop — the bread-and-butter low-level NPC the brief asks for — and a
     * {@code SENTRY} is {@code docs/design/14}'s "new defended infrastructure appearing on the graph".
     *
     * <p>⚠ {@code GATEWAY} and {@code BRIDGE} are assigned structurally and override this. The roll is
     * still made, because the RNG contract is draw-unconditionally-discard-conditionally: a draw
     * count that depended on whether a host happened to be a bridge would make the stream shape
     * depend on earlier draws, and a replay from a stored seed would stop being a replay.
     *
     * @return a {@code HostKind.name()}
     */
    public static String netHostKind(int depth, double u) {
        double terminal;
        double relay;
        double store;
        switch (netDepth(depth)) {
            case 0 -> {
                terminal = 0.60d;
                relay = 0.22d;
                store = 0.15d;
            }
            case 1 -> {
                terminal = 0.50d;
                relay = 0.22d;
                store = 0.20d;
            }
            case 2 -> {
                terminal = 0.38d;
                relay = 0.20d;
                store = 0.25d;
            }
            case 3 -> {
                terminal = 0.28d;
                relay = 0.18d;
                store = 0.26d;
            }
            default -> {
                terminal = 0.20d;
                relay = 0.15d;
                store = 0.25d;
            }
        }
        if (u < terminal) {
            return "TERMINAL";
        }
        if (u < terminal + relay) {
            return "RELAY";
        }
        if (u < terminal + relay + store) {
            return "STORE";
        }
        return "SENTRY";
    }

    /**
     * Difficulty tier for a rolled {@code u}, by depth. Means 1.30 / 1.85 / 2.80 / 3.80 / 4.55.
     *
     * <p>⚠ The bands do not merely shift, they <em>slide</em>: tier 1 is unreachable from depth 2
     * and tier 5 unreachable below depth 3. That is the brief's "the more bridge hops from home, the
     * harder on average" made a floor as well as an average — a player two bridges out cannot stumble
     * onto a tutorial-grade machine, and a player at home cannot stumble onto a wall.
     *
     * <p>Home tops out at tier 2 by the table and is clamped to 2 again by the home floor pass, so
     * the clamp is belt and braces on the one server where a bad roll is unrecoverable.
     * <strong>[PROPOSAL]</strong>.
     *
     * @return a tier on the shared 1–5 scale
     */
    public static int netTier(int depth, double u) {
        return switch (netDepth(depth)) {
            case 0 -> u < 0.70d ? 1 : 2;
            case 1 -> u < 0.35d ? 1 : (u < 0.80d ? 2 : 3);
            case 2 -> u < 0.40d ? 2 : (u < 0.80d ? 3 : 4);
            case 3 -> u < 0.40d ? 3 : (u < 0.80d ? 4 : 5);
            default -> u < 0.45d ? 4 : 5;
        };
    }

    /**
     * Firewall tier for a rolled {@code u}, by depth — the flat difficulty add from
     * {@code docs/design/09-defense-and-hardening.md} §1.
     *
     * <p>⚠ <b>Never returns 4.</b> {@code BreachTarget}'s compact constructor throws outside 0..3,
     * so a fourth band here would not be a balance mistake, it would be an exception thrown while
     * building the target list — which is to say, a save that cannot render its own network.
     * <strong>[PROPOSAL]</strong>.
     */
    public static int netFirewallTier(int depth, double u) {
        return switch (netDepth(depth)) {
            case 0 -> u < 0.85d ? 0 : 1;
            case 1 -> u < 0.45d ? 0 : (u < 0.90d ? 1 : 2);
            case 2 -> u < 0.15d ? 0 : (u < 0.55d ? 1 : (u < 0.90d ? 2 : 3));
            case 3 -> u < 0.05d ? 0 : (u < 0.25d ? 1 : (u < 0.70d ? 2 : 3));
            default -> u < 0.10d ? 1 : (u < 0.45d ? 2 : 3);
        };
    }

    /**
     * Chance a host runs a Tarpit, by depth.
     *
     * <p>Zero at home, and that is the home floor rather than the table: a Tarpit surcharges every
     * action, which is exactly the defence that punishes a player still learning to read a board.
     * <strong>[PROPOSAL]</strong>.
     */
    public static double netTarpitChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.05d;
            case 2 -> 0.15d;
            case 3 -> 0.25d;
            default -> 0.35d;
        };
    }

    /**
     * Chance a host carries canary tokens, by depth.
     *
     * <p>A canary tags the toucher's handle rather than stopping them ({@code docs/design/09} §1), so
     * the gradient is really a gradient in how much of the deep network knows who you are.
     * <strong>[PROPOSAL]</strong>.
     */
    public static double netCanaryChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.08d;
            case 2 -> 0.20d;
            case 3 -> 0.32d;
            default -> 0.45d;
        };
    }

    /**
     * Chance a host is genuinely live and defended, by depth.
     *
     * <p>⚠ This is <b>ground truth</b> and it is not what the player is told. A target reports
     * {@code LIVE} only once the Traffic Analyzer has established it ({@code docs/design/07} §1,
     * §2 — "directly supports proof-of-skill"), so this number sets how much of the deep network is
     * <em>worth</em> proof-of-skill credit, not how much of it hands credit out. A generator that
     * also set {@code trafficAnalyzed} would give away a reputation-gated tool's entire product and
     * with it Invariant I7. <strong>[PROPOSAL]</strong>.
     */
    public static double netDefendedChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.05d;
            case 1 -> 0.25d;
            case 2 -> 0.50d;
            case 3 -> 0.70d;
            default -> 0.85d;
        };
    }

    /**
     * Chance a host is actually an Eye trap, by depth.
     *
     * <p>Ground truth again, and the Honeypot Detector's mandatory false-negative rate
     * ({@code docs/design/07} §2) sits on top of it — so the player's worst case is not fourteen
     * percent of deep machines being traps, it is fourteen percent being traps and a clean reading
     * never being a guarantee. That residual doubt is the product. <strong>[PROPOSAL]</strong>.
     */
    public static double netHoneypotChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.02d;
            case 2 -> 0.06d;
            case 3 -> 0.10d;
            default -> 0.14d;
        };
    }

    /**
     * Chance a document-eligible host carries a story fragment, by depth.
     *
     * <p>⚠ Home is zero, and that is decision <b>N-4</b> made structural rather than promised: story
     * documents are flavour plus schematic material and must never be a critical path, so the
     * flavour layer starts one bridge out and nothing on the early path can depend on it. Only
     * {@code STORE} and {@code SENTRY} hosts are eligible at all. <strong>[PROPOSAL]</strong>.
     */
    public static double netDocumentChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.05d;
            case 2 -> 0.15d;
            case 3 -> 0.28d;
            default -> 0.40d;
        };
    }

    /**
     * Chance one sweep provokes a counter-hack, measured against the deepest server the sweep
     * reached.
     *
     * <p>The brief's "the more bridge hops from home, ... the more likely the machine hacks the
     * player back". Rolled against the <em>candidate</em> set rather than the detected set: the
     * machines notice you probing them whether or not you learn anything, which is also the honest
     * reading of a sweep as an intrusive outbound action.
     *
     * <p>⚠ Depth 0 returns {@link #NET_COUNTER_HACK_HOME}, a named constant, and a test asserts it.
     * <strong>[PROPOSAL]</strong> above home.
     */
    public static double netCounterHackChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> NET_COUNTER_HACK_HOME;
            case 1 -> 0.04d;
            case 2 -> 0.10d;
            case 3 -> 0.18d;
            default -> 0.28d;
        };
    }

    /**
     * Personal heat a counter-hack leaves, by the depth that provoked it.
     *
     * <p>⚠ The heat lands on the <b>player</b>, and that is correct rather than harsh: the player's
     * own sweep reached another machine, which is an intrusive outbound action and heat-bearing under
     * {@code docs/design/01-core-resources.md} §3. Invariant I9 then applies to what happens next —
     * cracking the planted miner on your own rig generates <b>no heat on any outcome</b>, so getting
     * counter-hacked hands the player the safest teaching target in the game. On a 0–100 scale, one
     * to three points means the named-hacker band still takes a campaign. <strong>[PROPOSAL]</strong>.
     */
    public static int netCounterHackHeat(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0;
            case 1, 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    /**
     * A host's one-time payout, by breach tier, in minor units — 3–6 / 7–12 / 14–22 / 26–38 / 45–65 EC.
     *
     * <p>⚠ <b>A stock, not a flow.</b> {@code docs/design/03-economy.md} §5 rule 1 caps new faucets at
     * 70 EC/hr effective; nothing here produces a rate, because each host pays exactly once and the
     * world contains a fixed number of hosts. Home's whole pool is roughly fifteen hosts at a mean of
     * about 4.5 EC — call it 68 EC, which buys the 15 EC Passive Sniffer and the 25 EC wide sweep with
     * change, and is then gone. The deep bands are steep because the risk is: a tier-5 SENTRY at depth
     * 4 is 45–65 EC behind a firewall 2–3, likely tarpitted and canaried, with a 14% chance of being a
     * trap and a 28% chance of hacking you back for merely sweeping near it.
     *
     * <p>Interpolated rather than banded so two hosts of the same tier are rarely worth exactly the
     * same, which is what makes a payout read as a thing found rather than a number awarded.
     * <strong>[PROPOSAL]</strong>.
     *
     * @param u a roll in {@code [0, 1)}
     */
    public static BigInteger netLootWei(int tier, double u) {
        // ⚠ Written as the EC bands docs/design quotes, not as wei. These were hundredths (300L for
        // 3 EC), which was already a number nobody could check against the doc without dividing.
        String lo;
        String hi;
        switch (clampTier(tier)) {
            case 1 -> {
                lo = "3";
                hi = "6";
            }
            case 2 -> {
                lo = "7";
                hi = "12";
            }
            case 3 -> {
                lo = "14";
                hi = "22";
            }
            case 4 -> {
                lo = "26";
                hi = "38";
            }
            default -> {
                lo = "45";
                hi = "65";
            }
        }
        BigInteger floor = ec(lo);
        BigInteger span = ec(hi).subtract(floor);
        // ⚠ The roll is a fraction, so it scales the SPAN in BigDecimal rather than being applied to
        // a double amount. Rounded to whole hundredths afterwards: a loot figure with eighteen
        // digits of interpolation residue reads as machine output, and these are meant to read as
        // amounts somebody left lying about.
        BigInteger step = ec("0.01");
        BigInteger scaled = new java.math.BigDecimal(span)
                .multiply(java.math.BigDecimal.valueOf(Math.clamp(u, 0.0d, 1.0d)))
                .toBigInteger()
                .divide(step)
                .multiply(step);
        return floor.add(scaled);
    }

    /**
     * The chance one sweep tier detects one signal strength at one hop, before the hop factor.
     *
     * <p>⚠ <b>Strictly increasing in tier for every signal, and that is a required property rather
     * than a happy accident.</b> A player who buys a better instrument must never lose a contact they
     * already had — the monotonicity is what makes an upgrade legible, and a test asserts
     * {@code detected(T1) ⊆ detected(T2) ⊆ detected(T3)} from the same vantage.
     *
     * <p>The signal axis is {@code docs/design/04-mining.md} §2.1's Low / Moderate / High, generalised
     * from miners to hosts: infrastructure is chatty, stores and sentries middling, a citizen's
     * desktop quiet. So the base sweep reliably finds the loud furniture of a network — gateways,
     * relays, bridges — and unreliably finds the machines actually worth breaching, which is what the
     * upgrade is for.
     *
     * <p>⚠ <b>No entry here, at any tier, changes the hop ceiling.</b> That is Invariant I2 made
     * structural: there is no code path from ethecoin to reach, and this method returns a probability
     * that is multiplied by a hop factor after a hard gate has already decided candidacy.
     * <strong>[PROPOSAL]</strong>.
     *
     * @param sweepTier 1, 2 or 3 — see {@code SweepTier}
     * @param signal a {@code SignalStrength.name()}
     */
    public static double netSweepBase(int sweepTier, String signal) {
        String s = signal == null ? "LOW" : signal.trim().toUpperCase(java.util.Locale.ROOT);
        int t = Math.max(1, Math.min(3, sweepTier));
        return switch (s) {
            case "HIGH" -> switch (t) {
                case 1 -> 0.85d;
                case 2 -> 0.95d;
                default -> 0.99d;
            };
            case "MODERATE" -> switch (t) {
                case 1 -> 0.60d;
                case 2 -> 0.78d;
                default -> 0.90d;
            };
            default -> switch (t) {
                case 1 -> 0.35d;
                case 2 -> 0.55d;
                default -> 0.72d;
            };
        };
    }
}
