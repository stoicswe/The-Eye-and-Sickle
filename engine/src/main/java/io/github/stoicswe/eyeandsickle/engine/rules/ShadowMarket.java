package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The Shadow Market — where players and NPCs trade upgrades with each other.
 *
 * <h2>⚠ THE CEILING IS THE WHOLE DESIGN, and it is derived rather than chosen</h2>
 *
 * {@code MarketDeals} already documents that a discount past {@code breakEvenDiscountPercent()}
 * turns the economy's sink into a faucet. A <b>player market sitting beside the shop</b> is that
 * same failure one step removed and much easier to miss: if a bid here ever reaches what the
 * storefront charges, then buy-on-GoH → sell-on-ShMark is free money with <em>no compute cost</em>,
 * repeatable, and the ethecoin supply inverts with every screen still rendering correctly.
 *
 * <p>So the highest price this market can ever show is pinned below the storefront's <b>cheapest</b>
 * price — {@code 100 - MarketDeals.maxDiscountPercent()} percent of retail — less a margin. And it
 * is pinned <b>structurally</b>: see the next section.
 *
 * <h2>⚠ BOUNDED NOISE, not a random walk — and that is not a stylistic choice</h2>
 *
 * The obvious way to simulate a price is to accumulate random steps. A random walk is
 * <em>unbounded</em>: given enough ticks it reaches any price at all, so the ceiling above would
 * have to be a clamp — and a clamped walk spends its time pinned to the clamp, which is both
 * visibly wrong and exactly the state where arbitrage is worth checking.
 *
 * <p>This is fractal value noise instead: a few octaves of hash-derived values interpolated over a
 * bucket index. It is <b>bounded by construction</b>, so no length of play can breach the ceiling —
 * the economic guard is a property of the function rather than a check somebody has to remember to
 * run. It is also <b>seekable</b>: the price at any instant costs a handful of hashes, with no need
 * to replay history from genesis, which is what lets a chart draw a week of candles instantly and
 * lets a save store none of it.
 *
 * <h2>⚠ DERIVED, so nothing about the market is stored</h2>
 *
 * Same rule as {@link MarketDeals} and {@code MempoolRules.projectionDepth}: the panel repaints on a
 * clock, and a drawn price would reshuffle the chart every second. Everything here is a pure
 * function of (character seed, item, time). What <em>is</em> stored is only what cannot be
 * recomputed — the player's own orders and fills, in {@code ShadowMarketState}.
 *
 * <h2>Solo, LAN and federated</h2>
 *
 * In solo the counterparties are simulated NPCs. On a server they are real players and the prints
 * are real trades — the client reads the same wire types either way and cannot tell, which is the
 * point of the {@code GameSession} port. ⚠ The federated half is <b>not built</b>: {@code W-9} in
 * {@code docs/design/15-open-questions.md}.
 */
public final class ShadowMarket {

    private ShadowMarket() {}

    /** What the market is called in the fiction. */
    public static final String NAME = "Shadow Market";

    /**
     * A margin under the storefront's cheapest price.
     *
     * <p>⚠ Points of retail, and the same role {@code MarketDeals.RESALE_SAFETY_MARGIN_PERCENT}
     * plays: the guard should bite before the exact break-even, not at it, because every figure
     * feeding it is a balance value somebody will re-tune.
     */
    public static final int ARBITRAGE_MARGIN_PERCENT = 2;

    /**
     * The cheapest this market ever quotes, as a percentage of catalogue retail.
     *
     * <p>Low enough that a shady counterparty undercutting is a real temptation, and that stolen
     * goods — which cost no ethecoin at all — still clear.
     */
    public static final int FLOOR_PERCENT = 45;

    /**
     * The dearest this market can ever quote, as a percentage of catalogue retail.
     *
     * <h2>⚠ DERIVED. Writing a number here is how the sink becomes a faucet.</h2>
     *
     * The storefront's floor is {@code 100 - MarketDeals.maxDiscountPercent()}; anything at or above
     * that makes buy-there-sell-here free money. Restating it as a literal would keep passing its
     * own tests after a discount-band re-tune moved the thing it is supposed to track — which is
     * precisely the failure {@code MarketDeals.breakEvenDiscountPercent} exists to avoid.
     *
     * @return the ceiling in percent of retail
     */
    public static int ceilingPercent() {
        int storefrontFloor = 100 - MarketDeals.maxDiscountPercent();
        return Math.max(FLOOR_PERCENT + 1, storefrontFloor - ARBITRAGE_MARGIN_PERCENT);
    }

    /**
     * How long one price print represents.
     *
     * <p>⚠ The chart moves every 2–8 seconds, which is {@link #tickSecondsAt} — this is the
     * <em>granularity of the underlying series</em>, not the repaint rate. Making them the same
     * number would tie how often the shop looks alive to how much history a candle covers.
     */
    public static final Duration TICK = Duration.ofSeconds(2);

    /** Candle intervals a player can choose, coarsest last. */
    public enum Interval {
        M1(Duration.ofMinutes(1), "1m"),
        M5(Duration.ofMinutes(5), "5m"),
        M15(Duration.ofMinutes(15), "15m"),
        H1(Duration.ofHours(1), "1h");

        private final Duration span;
        private final String label;

        Interval(Duration span, String label) {
            this.span = span;
            this.label = label;
        }

        public Duration span() {
            return span;
        }

        public String label() {
            return label;
        }
    }

    /**
     * How long until the next print, in seconds — between 2 and 8.
     *
     * <p>⚠ DERIVED from the tick index, never drawn. The panel repaints on a clock, and a drawn
     * interval would change every repaint, so the countdown to the next print would jitter instead
     * of counting down. Same rule as {@code MempoolRules.projectionDepth}.
     */
    public static long tickSecondsAt(String itemType, long tickIndex) {
        return 2 + Math.floorMod(hash(itemType, tickIndex, 0x51CE), 7);
    }

    // ── the price series ──────────────────────────────────────────────────────────────────────

    /** How many octaves of noise. Three gives a curve with both trend and texture; more is mush. */
    private static final int OCTAVES = 3;

    /** Buckets per octave at the finest scale. */
    private static final long BASE_BUCKET_SECONDS = 90;

    /**
     * The mid price of an item at an instant, in wei.
     *
     * <p>⚠ Every caller gets the same answer for the same instant, which is what lets the chart, the
     * order book and a fill all agree. A drawn price would have the book quoting one number and the
     * candle drawing another, on the same screen, at the same time.
     *
     * @param save the character — its id seeds the whole market, so two characters see different
     *     markets and one character's market is stable across sessions
     * @param itemType the catalogue id
     * @param at the instant
     * @return the mid, in wei, always inside {@link #FLOOR_PERCENT}..{@link #ceilingPercent}
     */
    public static BigInteger midAt(GameSave save, String itemType, Instant at) {
        BigInteger retail = Catalogue.byId(itemType)
                .map(Catalogue.Offering::priceWei)
                .orElse(BigInteger.ZERO);
        if (retail.signum() <= 0) {
            return BigInteger.ZERO;
        }
        double unit = noiseAt(seedOf(save, itemType), at.getEpochSecond());
        // Retail is the band's unit; `retail` above is the same figure.
        int span = ceilingPercent() - FLOOR_PERCENT;
        // ⚠ The BOUND is applied by the mapping, not by a clamp afterwards. `unit` is in [0,1] by
        // construction, so the result cannot leave the band however long the market runs — which is
        // what makes the arbitrage ceiling a property of the function rather than a check.
        double percent = FLOOR_PERCENT + unit * span;
        return retail.multiply(BigInteger.valueOf(Math.round(percent * 1000L)))
                .divide(BigInteger.valueOf(100_000L));
    }

    /**
     * Fractal value noise in [0,1], seekable at any second.
     *
     * <p>Octaves at 1×, 4× and 16× the base bucket, halving in amplitude. Interpolated linearly
     * between buckets — ⚠ linear rather than smoothstep on purpose: a smooth curve reads as a
     * <em>drawn illustration</em> of a price, and real prints are piecewise. It also keeps this
     * function free of any easing curve, which §5 would have opinions about if it ever drove a
     * pixel directly.
     */
    private static double noiseAt(long seed, long epochSecond) {
        double total = 0;
        double amplitude = 1;
        double normal = 0;
        long bucketSeconds = BASE_BUCKET_SECONDS * 16;
        for (int octave = 0; octave < OCTAVES; octave++) {
            long bucket = Math.floorDiv(epochSecond, bucketSeconds);
            double t = (epochSecond - bucket * bucketSeconds) / (double) bucketSeconds;
            double a = unit(hash(seed, bucket, octave));
            double b = unit(hash(seed, bucket + 1, octave));
            total += (a + (b - a) * t) * amplitude;
            normal += amplitude;
            amplitude /= 2;
            bucketSeconds /= 4;
        }
        return total / normal;
    }

    /** One candle. */
    public record Candle(
            Instant openedAt, BigInteger open, BigInteger high, BigInteger low, BigInteger close, long volume) {

        public boolean up() {
            return close.compareTo(open) >= 0;
        }
    }

    /**
     * The last {@code count} candles ending at {@code now}.
     *
     * <p>⚠ The newest candle is the one still forming, so its close is the current mid and it grows
     * as time passes — which is what the player watches. Building it as a finished candle would make
     * the chart lag one whole interval behind the price the order form is quoting.
     */
    public static List<Candle> candles(GameSave save, String itemType, Interval interval, int count, Instant now) {
        List<Candle> out = new ArrayList<>();
        long span = interval.span().toSeconds();
        long newest = Math.floorDiv(now.getEpochSecond(), span);
        for (long i = newest - count + 1; i <= newest; i++) {
            Instant opened = Instant.ofEpochSecond(i * span);
            Instant closes = Instant.ofEpochSecond((i + 1) * span);
            // ⚠ The forming candle stops at NOW, not at its own close — otherwise the chart shows
            // the future, and a player could read the next few seconds of price off it.
            Instant end = closes.isAfter(now) ? now : closes;
            out.add(candle(save, itemType, opened, end, i));
        }
        return out;
    }

    private static Candle candle(GameSave save, String itemType, Instant from, Instant to, long index) {
        // ⚠ SAMPLED, not accumulated. A candle is the extremes of a continuous function over a
        // window, and sampling it a fixed number of times is both O(1) per candle and stable — an
        // accumulation would have to replay every print in the window and would give a different
        // answer at a different zoom.
        int samples = 16;
        long fromS = from.getEpochSecond();
        long toS = Math.max(fromS, to.getEpochSecond());
        BigInteger open = midAt(save, itemType, from);
        BigInteger close = midAt(save, itemType, Instant.ofEpochSecond(toS));
        BigInteger high = open.max(close);
        BigInteger low = open.min(close);
        for (int i = 1; i < samples; i++) {
            BigInteger at = midAt(save, itemType, Instant.ofEpochSecond(fromS + (toS - fromS) * i / samples));
            high = high.max(at);
            low = low.min(at);
        }
        long volume = 4 + Math.floorMod(hash(seedOf(save, itemType), index, 0x0101), 40);
        return new Candle(from, open, high, low, close, volume);
    }

    // ── the counterparties ────────────────────────────────────────────────────────────────────

    /**
     * Somebody on the other side of a trade.
     *
     * <h2>⚠ Rating is a PRICE, and that is the mechanic</h2>
     *
     * A well-rated seller asks more and delivers; a shady one undercuts and might not. So the cheap
     * price on screen is not the good price — it is a bet, and the spread between the two <em>is</em>
     * the value of reputation, expressed in ethecoin rather than in a badge. That is the same trade
     * {@code SecondaryMarket} already models from the player's own side, seen from the other.
     *
     * @param handle what they call themselves
     * @param rating −100…100, the same scale {@link SecondaryMarket} uses for the player
     * @param fillPercent the chance they actually deliver
     */
    public record Trader(String handle, int rating, int fillPercent) {

        /** ⚠ Named the same way the standings are, so one vocabulary covers both sides of a trade. */
        public String standing() {
            if (rating >= 60) {
                return "trusted";
            }
            if (rating >= 20) {
                return "known";
            }
            if (rating >= -20) {
                return "unrated";
            }
            return "shady";
        }
    }

    /** Handles are assembled rather than drawn, so the same seed always names the same trader. */
    private static final String[] HANDLE_HEAD = {
        "null", "grey", "half", "cold", "dead", "bent", "salt", "iron", "dim", "quiet", "raw", "tin"
    };

    private static final String[] HANDLE_TAIL = {
        "wire", "relay", "fence", "drop", "broker", "hand", "cache", "vault", "runner", "market"
    };

    /**
     * The trader behind a quote.
     *
     * <p>Derived from (character, item, side, depth), so the third-best ask is always the same
     * person until the book moves — a book whose names reshuffled every repaint would make
     * reputation unreadable, which is the one thing it exists to be.
     */
    public static Trader traderAt(GameSave save, String itemType, boolean buy, int depth, long bookIndex) {
        long seed = seedOf(save, itemType) ^ (buy ? 0x0B1DL : 0x0A5EL);
        long h = hash(seed, bookIndex * 31 + depth, 0x7A5E);
        String handle = HANDLE_HEAD[(int) Math.floorMod(h, HANDLE_HEAD.length)]
                + HANDLE_TAIL[(int) Math.floorMod(h >> 8, HANDLE_TAIL.length)];
        int rating = (int) (Math.floorMod(h >> 16, 201L) - 100L);
        // ⚠ Never zero and never certain. A 100% counterparty makes reputation free to ignore once
        // found; a 0% one is a trap rather than a risk, and the player has no way to learn the
        // difference from a single failure.
        int fill = 55 + (rating + 100) * 44 / 200;
        return new Trader(handle, rating, fill);
    }

    /** One resting order in the book. */
    public record Level(BigInteger price, long size, Trader trader) {}

    /** Both sides of the book, best first. */
    public record Book(List<Level> bids, List<Level> asks) {

        public Book {
            bids = List.copyOf(bids);
            asks = List.copyOf(asks);
        }

        public BigInteger bestBid() {
            return bids.isEmpty() ? BigInteger.ZERO : bids.getFirst().price();
        }

        public BigInteger bestAsk() {
            return asks.isEmpty() ? BigInteger.ZERO : asks.getFirst().price();
        }

        public BigInteger spread() {
            return asks.isEmpty() || bids.isEmpty() ? BigInteger.ZERO : bestAsk().subtract(bestBid());
        }
    }

    /** How deep the book goes on each side. */
    public static final int DEPTH = 8;

    /**
     * The order book right now.
     *
     * <h2>⚠ A SHADY seller undercuts and a TRUSTED one asks more — the book is sorted by price, so
     * the top of the asks is systematically the riskiest counterparty</h2>
     *
     * That is the whole shape of the decision and it falls out of sorting rather than being staged:
     * the cheapest offer on screen is cheap <em>because</em> nobody vouches for it. A book that
     * priced reputation the other way round would make the best price also the safest, and there
     * would be nothing to decide.
     */
    public static Book bookAt(GameSave save, String itemType, Instant now) {
        BigInteger mid = midAt(save, itemType, now);
        BigInteger retail = retailOf(itemType);
        if (mid.signum() <= 0) {
            return new Book(List.of(), List.of());
        }
        long bookIndex = Math.floorDiv(now.getEpochSecond(), TICK.toSeconds());
        List<Level> bids = new ArrayList<>();
        List<Level> asks = new ArrayList<>();
        for (int depth = 0; depth < DEPTH; depth++) {
            Trader buyer = traderAt(save, itemType, true, depth, bookIndex);
            Trader seller = traderAt(save, itemType, false, depth, bookIndex);
            bids.add(new Level(
                    quote(retail, mid, buyer, depth, true), size(save, itemType, bookIndex, depth, true), buyer));
            asks.add(new Level(
                    quote(retail, mid, seller, depth, false), size(save, itemType, bookIndex, depth, false), seller));
        }
        bids.sort((a, b) -> b.price().compareTo(a.price()));
        asks.sort((a, b) -> a.price().compareTo(b.price()));
        return new Book(bids, asks);
    }

    /**
     * What a trader quotes.
     *
     * <p>⚠ Reputation moves the price and depth moves it further. A trusted seller wants a premium
     * for the certainty; a shady one buys attention with a discount. The result is clamped into the
     * band by {@link #bandClamp}, which is the one place a clamp is correct — a <em>quote</em> is an
     * offset off the mid and could otherwise poke through the ceiling the mid respects.
     */
    private static BigInteger quote(BigInteger retail, BigInteger mid, Trader trader, int depth, boolean buy) {
        // A trusted seller asks a premium for the certainty; a shady one buys attention with a
        // discount. Buyers mirror it — a shady buyer bids up, which is what makes a good price from
        // a bad counterparty a decision on both sides of the book.
        long reputationBp = trader.rating() * REPUTATION_SWING_BP / 100L;
        // ⚠ EVERY quote starts BASE_SPREAD_BP away from the mid, and the base has to exceed the
        // reputation swing or the book CROSSES. Without it, at depth 0 the offset is reputation
        // alone: a shady buyer bids mid + 0.4% while a shady seller asks mid − 0.4%, and a crossed
        // book is a standing offer to buy and sell into simultaneously for a profit with no
        // counterparty risk at all. Measured — it crossed within 44 minutes of the epoch.
        long offsetBp = BASE_SPREAD_BP + depth * DEPTH_STEP_BP + (buy ? -reputationBp : reputationBp);
        BigInteger moved = buy
                ? mid.subtract(mid.multiply(BigInteger.valueOf(offsetBp)).divide(BigInteger.valueOf(10_000L)))
                : mid.add(mid.multiply(BigInteger.valueOf(offsetBp)).divide(BigInteger.valueOf(10_000L)));
        return bandClamp(retail, moved);
    }

    /**
     * How far the touch sits from the mid, in basis points.
     *
     * <p>⚠ MUST exceed {@link #REPUTATION_SWING_BP}. The two together are what make a crossed book
     * impossible by construction rather than by luck — {@code ShadowMarketTest.theBookIsSorted}
     * pins the relationship, and it is the one arithmetic fact in this file a re-tune can break
     * silently.
     */
    public static final long BASE_SPREAD_BP = 60;

    /** How much a level away from the touch adds. */
    public static final long DEPTH_STEP_BP = 60;

    /** The most reputation can move a quote, in basis points. @see #BASE_SPREAD_BP */
    public static final long REPUTATION_SWING_BP = 40;

    /**
     * ⚠ Holds a quote inside the band, and it is the ONLY clamp in this file.
     *
     * <p>The <em>mid</em> cannot breach the ceiling — that is structural, and the whole reason the
     * series is bounded noise rather than a random walk. A <em>quote</em> is the mid plus a
     * reputation-and-depth offset, and an offset can push past it. So the clamp exists for exactly
     * that gap and nothing else: it is a property of the offset, not a safety net under the model.
     *
     * <p>⚠ Against RETAIL, not against the mid. The band is defined in percent of the catalogue
     * price; deriving it from the mid would move the ceiling every time the price did, which is a
     * ceiling that is not one.
     */
    private static BigInteger bandClamp(BigInteger retail, BigInteger price) {
        if (retail.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger ceiling = retail.multiply(BigInteger.valueOf(ceilingPercent())).divide(BigInteger.valueOf(100));
        BigInteger floor = retail.multiply(BigInteger.valueOf(FLOOR_PERCENT)).divide(BigInteger.valueOf(100));
        return price.max(floor).min(ceiling);
    }

    /** What the catalogue charges, which is what the band is a percentage of. */
    private static BigInteger retailOf(String itemType) {
        return Catalogue.byId(itemType).map(Catalogue.Offering::priceWei).orElse(BigInteger.ZERO);
    }

    private static long size(GameSave save, String itemType, long bookIndex, int depth, boolean buy) {
        return 1 + Math.floorMod(hash(seedOf(save, itemType) + (buy ? 1 : 2), bookIndex * 17 + depth, 0x5122), 6);
    }

    // ── the tape ──────────────────────────────────────────────────────────────────────────────

    /** A print — somebody traded. */
    public record Print(Instant at, BigInteger price, long size, boolean buyerTaker, String handle) {}

    /**
     * The most recent prints, newest first.
     *
     * <p>⚠ Derived from the tick index, so the tape does not rewrite itself on repaint. A drawn tape
     * would show a different history every second, which is worse than no tape: it teaches the
     * player that nothing on this screen is a record.
     */
    public static List<Print> tape(GameSave save, String itemType, int count, Instant now) {
        List<Print> out = new ArrayList<>();
        long tick = Math.floorDiv(now.getEpochSecond(), TICK.toSeconds());
        for (long i = tick; i > tick - count && i >= 0; i--) {
            Instant at = Instant.ofEpochSecond(i * TICK.toSeconds());
            long h = hash(seedOf(save, itemType), i, 0x7A9E);
            out.add(new Print(
                    at,
                    midAt(save, itemType, at),
                    1 + Math.floorMod(h, 5),
                    Math.floorMod(h >> 4, 2) == 0,
                    traderAt(save, itemType, Math.floorMod(h >> 4, 2) == 0, 0, i).handle()));
        }
        return out;
    }

    // ── listings ──────────────────────────────────────────────────────────────────────────────

    /** One offer from a counterparty, with its delivery mode. */
    public record Offer(
            String listingId,
            String itemType,
            BigInteger price,
            int quantity,
            io.github.stoicswe.eyeandsickle.protocol.game.DeliveryMode delivery,
            Trader trader) {}

    /** How many counterparty listings stand at once. */
    public static final int OFFERS = 6;

    /**
     * How long one simulated listing stands before it is replaced.
     *
     * <h2>⚠ A LISTING IS NOT A PRICE TICK, and keying it to one made buying impossible</h2>
     *
     * Listings were derived from {@link #TICK}, so the whole board turned over every two seconds. A
     * player would right-click a row, read the confirmation, press Pay — and by then the listing it
     * named no longer existed, so {@code buyNow} answered "that listing is gone" every time. The
     * board looked alive and could not be traded with.
     *
     * <p>The two are genuinely different things: the <b>price</b> moves continuously because that is
     * what a market does, and a <b>listing</b> is somebody's standing offer that sits there until it
     * sells or is pulled. Two minutes is long enough to read a row, open a dialog and decide.
     *
     * <p>⚠ This governs the <b>simulation only</b>. A federated listing does not rotate at all — it
     * is a real posting and it stands until its seller takes it down or somebody buys it, so there is
     * no dwell to tune on that path.
     */
    public static final Duration LISTING_DWELL = Duration.ofMinutes(2);

    /**
     * The listings a buyer can take outright.
     *
     * <h2>⚠ A SHADY seller is far likelier to want paying up front</h2>
     *
     * Delivery mode is derived from the trader's rating, not drawn independently, and that is the
     * whole shape of the decision: the cheap listings are cheap <em>and</em> promised, the safe ones
     * are attached <em>and</em> dearer. Rolling the two apart would produce trustworthy sellers who
     * demand trust anyway and shady ones who hand the goods over — a market where the price and the
     * risk carry no information about each other, so there is nothing to read.
     *
     * <p>⚠ Derived from the book index, so listings do not reshuffle on repaint. A player who
     * right-clicks a listing must get the listing they aimed at.
     */
    public static List<Offer> offersAt(GameSave save, String itemType, Instant now) {
        BigInteger retail = retailOf(itemType);
        if (retail.signum() <= 0) {
            return List.of();
        }
        List<Offer> offers = new ArrayList<>();
        for (int depth = 0; depth < OFFERS; depth++) {
            // ⚠ STAGGERED, so the board is not replaced wholesale every two minutes. Each slot keeps
            // its own phase, so roughly one listing turns over every twenty seconds while any
            // individual one still stands for the full dwell — the board reads as alive and every
            // row on it is long-lived enough to buy.
            Instant opened = windowStart(now, depth);
            // ⚠ The book is read AT THE INSTANT THE LISTING OPENED, never at `now`. Reading it live
            // would leave the id stable and the price drifting underneath it, so the confirmation
            // would quote one number and the debit would take another — the single most damaging
            // thing a shop can get wrong, and invisible until somebody checked their ledger.
            Book book = bookAt(save, itemType, opened);
            if (depth >= book.asks().size()) {
                continue;
            }
            Level level = book.asks().get(depth);
            Trader trader = level.trader();
            long window = Math.floorDiv(opened.getEpochSecond(), LISTING_DWELL.toSeconds());
            // ⚠ Derived from the rating with a deterministic wobble, so a trusted seller USUALLY
            // attaches and a shady one usually does not — "usually" rather than "always", or the
            // standing would be redundant with the mode and the player would only ever read one.
            long roll = Math.floorMod(hash(seedOf(save, itemType), window * 97 + depth, 0xDE11), 100L);
            var mode = roll < trader.rating() + 100L
                    ? io.github.stoicswe.eyeandsickle.protocol.game.DeliveryMode.ATTACHED
                    : io.github.stoicswe.eyeandsickle.protocol.game.DeliveryMode.SEND_LATER;
            // ⚠ A STABLE id derived from (item, window, depth). A random id would be a different
            // listing on every repaint, so the confirmation dialog would name one thing and buy
            // another.
            String id = itemType + ":" + window + ":" + depth;
            offers.add(new Offer(id, itemType, level.price(), (int) level.size(), mode, trader));
        }
        // ⚠ Sorted after the fact, because each slot froze its price at a different instant — leaving
        // them in slot order would put a stale dear listing above a fresh cheap one and the board
        // would stop reading as a book.
        offers.sort((a, b) -> a.price().compareTo(b.price()));
        return offers;
    }

    /**
     * When the listing in a given slot went up.
     *
     * <p>Each slot is offset by a fraction of the dwell so they do not all turn over together.
     */
    private static Instant windowStart(Instant now, int depth) {
        long dwell = LISTING_DWELL.toSeconds();
        long offset = dwell * depth / Math.max(1, OFFERS);
        long window = Math.floorDiv(now.getEpochSecond() - offset, dwell);
        return Instant.ofEpochSecond(window * dwell + offset);
    }


    /**
     * How long a listing is still honoured after it leaves the board.
     *
     * <h2>⚠ Without it, a purchase can fail through nobody's fault</h2>
     *
     * The slots are staggered, so at any moment one of them is close to its boundary — a player who
     * right-clicks that row and takes four seconds over the confirmation would be told the listing is
     * gone, having done nothing wrong. The grace is what a real quote-expiry flow gives you: the
     * price you were shown is the price you can take, for a little longer than it takes to decide.
     *
     * <p>⚠ Bounded, and deliberately short. Reconstructing the offer from its id means an
     * arbitrarily old listing could otherwise be bought at its original price by leaving the dialog
     * open — which is a free option on a moving market, and a player who found it could farm it.
     */
    public static final Duration LISTING_GRACE = Duration.ofSeconds(45);

    /**
     * Finds a counterparty listing by the id the panel showed.
     *
     * <h2>⚠ RECONSTRUCTED from the id, not searched for on the current board</h2>
     *
     * The id carries the window and the slot, and both the price and the counterparty are functions
     * of those — so the exact listing the player clicked can be rebuilt whether or not it is still on
     * screen. Searching the live board instead made a purchase fail whenever the board had turned
     * over between the right-click and the confirmation, which with staggered slots is a real and
     * regular occurrence rather than an edge case.
     */
    public static java.util.Optional<Offer> offer(GameSave save, String itemType, String listingId, Instant now) {
        String[] parts = String.valueOf(listingId).split(":");
        if (parts.length < 3) {
            return java.util.Optional.empty();
        }
        long window;
        int depth;
        try {
            window = Long.parseLong(parts[parts.length - 2]);
            depth = Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException malformed) {
            return java.util.Optional.empty();
        }
        if (depth < 0 || depth >= OFFERS) {
            return java.util.Optional.empty();
        }
        long dwell = LISTING_DWELL.toSeconds();
        long offset = dwell * depth / Math.max(1, OFFERS);
        Instant opened = Instant.ofEpochSecond(window * dwell + offset);
        Instant expires = opened.plus(LISTING_DWELL).plus(LISTING_GRACE);
        // ⚠ Both ends checked. A listing from the FUTURE is as wrong as a stale one, and an id is a
        // string the player's own save could carry — refusing to price something that has not been
        // posted yet costs nothing and closes the obvious edit.
        if (now.isBefore(opened) || !now.isBefore(expires)) {
            return java.util.Optional.empty();
        }
        return offersAt(save, itemType, opened).stream()
                .filter(offer -> offer.listingId().equals(listingId))
                .findFirst();
    }

    // ── what is traded here ───────────────────────────────────────────────────────────────────

    /**
     * Everything this market lists.
     *
     * <p>⚠ {@code Repac.sellable} decides, which is <b>Invariant I2</b>: only an ethecoin-gated item
     * may become money. Listing a schematic- or zero-day-gated tool here would put a price on the one
     * thing whose whole point is that it has none, and would let anybody with enough ethecoin buy a
     * ceiling (I2) or farm a zero-day (I8).
     */
    public static List<String> listings() {
        return Catalogue.offerings().stream()
                .map(Catalogue.Offering::id)
                .filter(Repac::sellable)
                .toList();
    }

    // ── seeds ─────────────────────────────────────────────────────────────────────────────────

    /**
     * ⚠ Seeded on the CHARACTER, so two characters see two different markets and one character's
     * market is the same market every time they open it. A seed from the wall clock would make the
     * chart a new chart on every launch, and a global constant would give every player on earth an
     * identical price history.
     */
    private static long seedOf(GameSave save, String itemType) {
        String id = save == null || save.characterId == null ? "" : save.characterId;
        return (long) id.hashCode() * 31L + (itemType == null ? 0 : itemType.hashCode());
    }

    private static long hash(String text, long a, long b) {
        return hash(text == null ? 0 : text.hashCode(), a, b);
    }

    /** SplitMix64's finaliser — cheap, well-distributed and deterministic across JVMs. */
    private static long hash(long seed, long a, long b) {
        long x = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL;
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }

    private static double unit(long hash) {
        return (Math.floorMod(hash, 1_000_003L)) / 1_000_003.0d;
    }

    // ── the player's own orders ───────────────────────────────────────────────────────────────

    /** Why an order could not be placed. */
    public enum Refusal {
        /** Not something this market lists — I2 keeps non-ethecoin gates off it. */
        NOT_LISTED,

        /** A price of zero, or a quantity of none. */
        MALFORMED,

        /** Not enough ethecoin to escrow the buy. */
        CANNOT_AFFORD,

        /** Nothing to sell — no copy of that item outside the vault's locked state. */
        NOTHING_TO_SELL,

        /** No such order. */
        NO_SUCH_ORDER
    }

    /** What happened. */
    public record Placed(io.github.stoicswe.eyeandsickle.engine.state.ShadowOrderState order, Refusal refusal) {

        public boolean succeeded() {
            return order != null;
        }

        static Placed refused(Refusal refusal) {
            return new Placed(null, refusal);
        }
    }

    /**
     * Rests a limit order.
     *
     * <h2>⚠ A BUY ESCROWS THE MONEY NOW</h2>
     *
     * The alternative is checking the balance when it fills, and a player who spent it in between
     * gets an order that silently did not execute — the worst of the three possible behaviours,
     * because nothing anywhere reports it. Escrow is also honest about what the player can spend:
     * the money is committed, so it is gone from the balance and shown against the order.
     *
     * <h2>⚠ A SELL RESERVES A SPECIFIC COPY, by id</h2>
     *
     * Items stopped stacking on 2026-08-04. An order naming only the type would sell whichever copy
     * the code found first, and the player would watch the wrong build — possibly the equipped one —
     * leave the vault.
     */
    public static Placed place(
            GameSave save,
            String itemType,
            boolean buy,
            BigInteger limitPriceWei,
            int quantity,
            String heldItemId,
            Instant now) {
        if (save == null || !listings().contains(itemType)) {
            return Placed.refused(Refusal.NOT_LISTED);
        }
        if (limitPriceWei == null || limitPriceWei.signum() <= 0 || quantity <= 0) {
            return Placed.refused(Refusal.MALFORMED);
        }
        var order = new io.github.stoicswe.eyeandsickle.engine.state.ShadowOrderState();
        order.itemType = itemType;
        order.buy = buy;
        order.limitPriceWei = limitPriceWei;
        order.quantity = quantity;
        order.placedAt = now;

        if (buy) {
            // ⚠ NO ESCROW (2026-08-04). This used to move the money out of the balance at
            // placement, which made a resting bid risk-free — and this market is between people who
            // can defect, so risk-free is the one thing it must not be. The consequence is real and
            // deliberate: a bid can fill against a balance that has since been spent, and `settle`
            // simply cancels it. See the class note on ShadowTrading.
            if (save.ethecoinWei.compareTo(limitPriceWei.multiply(BigInteger.valueOf(quantity))) < 0) {
                // Refused at placement as a courtesy, NOT as a guarantee — nothing stops the player
                // spending it before the fill.
                return Placed.refused(Refusal.CANNOT_AFFORD);
            }
        } else {
            // ⚠ The untrusted pay to advertise here too — a resting sell order is a listing, and
            // charging it only on the listings board would leave the deterrent with an obvious hole.
            if (ShadowTrading.chargedUpFront(save)) {
                BigInteger upFront = ShadowTrading.feeOn(limitPriceWei, save);
                if (save.ethecoinWei.compareTo(upFront) < 0) {
                    return Placed.refused(Refusal.CANNOT_AFFORD);
                }
                save.ethecoinWei = save.ethecoinWei.subtract(upFront);
            }
            var held = save.items.stream()
                    .filter(item -> itemType.equals(item.itemType))
                    .filter(item -> !item.equipped)
                    .filter(item -> heldItemId == null || heldItemId.isBlank() || heldItemId.equals(item.itemId))
                    .findFirst();
            if (held.isEmpty()) {
                return Placed.refused(Refusal.NOTHING_TO_SELL);
            }
            // ⚠ The item is NOT removed here. It is reserved by id and leaves on the fill — an item
            // deleted at placement is one a cancel has to conjure back, and a conjured item is a
            // different item with a different id.
            order.heldItemId = held.get().itemId;
            order.quantity = 1;
        }
        save.shadowOrders.add(order);
        return new Placed(order, null);
    }

    /**
     * Withdraws an order.
     *
     * <p>⚠ Nothing is returned, because nothing was held — there is no escrow on this market. A
     * cancelled bid simply stops being an offer. ⚠ Cancelling is free, and a cancellation fee would
     * be a sink nobody asked for that punished the one action which corrects a mistake.
     */
    public static boolean cancel(GameSave save, String orderId) {
        if (save == null || orderId == null) {
            return false;
        }
        var found = save.shadowOrders.stream()
                .filter(order -> orderId.equals(order.orderId))
                .findFirst();
        if (found.isEmpty()) {
            return false;
        }
        save.shadowOrders.remove(found.get());
        return true;
    }

    /** What a fill did, for the log. */
    public record Fill(String itemType, boolean bought, BigInteger price, boolean delivered, String counterparty) {}

    /**
     * Matches resting orders against the book.
     *
     * <h2>⚠ A buy fills when the best ASK falls to the limit, and vice versa</h2>
     *
     * The same rule a real book runs: you are filled by somebody crossing to you, or by the market
     * coming to your price. Filling at the mid would make a limit order a market order with extra
     * steps.
     *
     * <h2>⚠ THE COUNTERPARTY MAY NOT DELIVER, and the money is still gone</h2>
     *
     * That is what a rating is for, and it is the whole reason the cheapest ask is the worst-rated
     * seller. A defection is logged loudly and the escrow is <b>not</b> returned — an undelivered
     * purchase that refunded itself would make reputation free to ignore.
     *
     * @param elapsedSeed varies the delivery roll per tick without storing anything
     * @return what filled
     */
    public static List<Fill> settle(GameSave save, Instant now, long elapsedSeed) {
        if (save == null || save.shadowOrders.isEmpty()) {
            return List.of();
        }
        List<Fill> fills = new ArrayList<>();
        for (var order : List.copyOf(save.shadowOrders)) {
            Book book = bookAt(save, order.itemType, now);
            if (book.asks().isEmpty() || book.bids().isEmpty()) {
                continue;
            }
            Level touch = order.buy ? book.asks().getFirst() : book.bids().getFirst();
            boolean crosses = order.buy
                    ? touch.price().compareTo(order.limitPriceWei) <= 0
                    : touch.price().compareTo(order.limitPriceWei) >= 0;
            if (!crosses) {
                continue;
            }
            long roll = Math.floorMod(hash(order.orderId.hashCode(), elapsedSeed, 0xF11L), 100L);
            boolean delivered = roll < touch.trader().fillPercent();
            fills.add(apply(save, order, touch, delivered, now));
        }
        return fills;
    }

    private static Fill apply(
            GameSave save,
            io.github.stoicswe.eyeandsickle.engine.state.ShadowOrderState order,
            Level touch,
            boolean delivered,
            Instant now) {
        save.shadowOrders.remove(order);
        if (order.buy) {
            // ⚠ The fill is at the TOUCH, not at the limit — a limit is the worst price you accept,
            // and charging it when the market is better would quietly take the difference.
            BigInteger cost = touch.price();
            // ⚠ CHECKED AT FILL TIME, because nothing was escrowed. A bid that outlived its funding
            // is cancelled rather than defaulted: the player never entered an agreement, because the
            // fill is the agreement and it did not happen. Treating this as a defection would punish
            // somebody for a market moving while they spent their own money.
            if (save.ethecoinWei.compareTo(cost) < 0) {
                return new Fill(order.itemType, true, cost, false, "");
            }
            save.ethecoinWei = save.ethecoinWei.subtract(cost);
            if (delivered) {
                var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
                item.itemType = order.itemType;
                item.displayName = Repac.displayName(order.itemType);
                // ⚠ The high-risk zone, same as anything else bought. Where a thing came from does
                // not change that goods you have not filed are goods anybody can take.
                item.tier = StorageRules.ARRIVALS.name();
                item.acquiredAt = now;
                item.origin = "bought on the " + NAME + " from " + touch.trader().handle();
                save.items.add(item);
            }
            return new Fill(order.itemType, true, cost, delivered, touch.trader().handle());
        }
        save.items.removeIf(item -> item.itemId.equals(order.heldItemId));
        // ⚠ A resting sell order IS a listing, so it pays the same fee. Exempting it would make the
        // order form a fee-free back door around the listing board, and every seller would learn to
        // use it — which is the same feature with the sink switched off.
        save.ethecoinWei = save.ethecoinWei.add(ShadowTrading.takeFee(save, touch.price()));
        return new Fill(order.itemType, false, touch.price(), true, touch.trader().handle());
    }

    /** The player's resting orders. */
    public static List<io.github.stoicswe.eyeandsickle.engine.state.ShadowOrderState> orders(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.shadowOrders);
    }

    /** How much noise a listed trade makes. Selling on a darknet market is not a quiet act. */
    public static long tradeNoiseCycles() {
        return Balance.MARKET_FOREIGN_PURCHASE_NOISE_CYCLES;
    }
}
