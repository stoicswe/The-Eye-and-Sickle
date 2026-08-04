package io.github.stoicswe.eyeandsickle.client.session;

import io.github.stoicswe.eyeandsickle.protocol.game.BlockContribution;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainSync;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything the interface can ask the game, and everything it can ask the game to do.
 *
 * <h2>One port, two worlds</h2>
 *
 * A view binds to this and never learns whether it is talking to a rules engine three method calls
 * away ({@link LocalGameSession}) or a home server across a network. That is not architectural
 * tidiness for its own sake — it is what makes the offline mode honest. If the single-player build
 * had its own screens, single player would drift into a different game; because it does not, the
 * rig monitor a solo player reads is the rig monitor an online player reads.
 *
 * <h2>Why every mutation returns an {@link Outcome} instead of throwing</h2>
 *
 * Client pillar **C4** ({@code docs/client/00-client-overview.md} §2) says the client never claims
 * authority it does not have, and {@code docs/client/04-terminology-and-education.md} §3.5 makes that
 * structural by giving refusal and unreachability different exit statuses. A refusal is a normal,
 * expected answer — the server (or the rules) considered the request and declined — so it is a return
 * value. Exceptions are for the cases where the question could not be asked at all.
 *
 * <p>The distinction shows up on screen: "the server refused this" and "we could not reach the
 * server" must never collapse into one message ({@code docs/client/01-visual-language.md} §9.4).
 *
 * <h2>Threading</h2>
 *
 * Implementations are called from the JavaFX application thread and must not block it. The local
 * implementation is synchronous because it is arithmetic; a remote one must do its I/O elsewhere and
 * deliver results back through {@link #onChange}.
 */
public interface GameSession extends AutoCloseable {

    /**
     * The client's event broker.
     *
     * <h2>⚠ Reached through the session on purpose, not through a static</h2>
     *
     * Every view already holds a {@code GameSession} and nothing else, which is what stops a panel
     * from acquiring a second route to the game's state. A global bus would be exactly that second
     * route — and a static one would be shared across the two sessions a test opens side by side,
     * which is how one test's events end up asserted by another.
     *
     * <p>Events published here are the client's own: what the player did and what the world did back.
     * They are <b>not</b> game state, are never persisted, and nothing reads them to decide anything.
     * A subscriber that started deciding an outcome from an event would be the client claiming
     * authority (I14) by a new route.
     */
    io.github.stoicswe.eyeandsickle.client.events.EventBus events();

    /** Whether this session is a local solo game or a connection to a home server. */
    SessionMode mode();

    /** The player's handle. In solo this is a local name, not a DID — there is no identity here. */
    String handle();

    /**
     * The operator's picture as a base64 PNG, or empty when none is set.
     *
     * <p>⚠ Pixels, never a path — see {@code GameSave.avatarPng}. A stored path would mean reading
     * an arbitrary host location on every launch, which is the boundary {@code docs/client/00} §7
     * exists to hold.
     */
    String avatar();

    /** Sets it. Empty clears it back to the generated silhouette. */
    Outcome setAvatar(String base64Png);

    /**
     * The rig's capacity ledger — mandatory and always visible ({@code docs/design/01} §1.4).
     *
     * <p>Never null, even while a remote session is reconnecting: a session that cannot answer
     * returns its last known budget and reports {@link #connected()} false, so the UI can mark the
     * numbers stale rather than blanking a HUD the player is mid-decision on.
     */
    ComputeBudget computeBudget();

    Ethecoin balance();

    /** Long-horizon Eye attention. Distinct from noise, which is short-horizon and decays. */
    int personalHeat();

    List<InventoryItem> items(StorageTier tier);

    /**
     * How many slots this tier has — what the STORAGE grid draws its empty cells against.
     *
     * <p>⚠ A capacity, not a limit: nothing currently refuses a move that would overfill a tier, so
     * this can be smaller than {@code items(tier).size()}. The window renders that as over-capacity
     * rather than clamping it, because a grid that hid items to make its own arithmetic work would
     * be lying about what the player owns. See {@code solo/Balance.storageCapacity}.
     */
    int storageCapacity(StorageTier tier);

    /**
     * Total time this character has been played, across every session, in seconds.
     *
     * <p>⚠ Distinct from the session clock the strip shows, and that is the point of having both.
     * The session clock answers "how long have I been at this sitting"; this answers "how much of
     * my life is in this character". They are different questions and a player asks the second one
     * far less often, which is why it lives in a tooltip.
     */
    long uptimeSeconds();

    List<LedgerRow> ledger(int limit);

    List<KnownNode> knownNodes();

    /** Every armed defence, so the rig readout can show a posture rather than a number. */
    List<ArmedDefense> defenses();

    /**
     * The rig's log, oldest first.
     *
     * @param minSeverity RFC 5424 level; entries less severe than this are excluded. Remember the
     *     numbering runs backwards — {@code 4} (warning) excludes {@code 6} (info).
     */
    List<LogLine> log(int minSeverity, int limit);

    /**
     * What mining is currently doing.
     *
     * <p>Exposed as a summary rather than as raw nodes because the readout wants rates and caps, and
     * making every view derive those from the node list would be three chances to derive them
     * differently.
     */
    MiningSummary mining();

    /**
     * The chain, the rig's part in it, and what mining has actually paid.
     *
     * <p>⚠ Carries no progress figure and must never grow one — mining is memoryless, so there is
     * nothing to be partway through. See {@code MiningSnapshot}.
     */
    MiningSnapshot miningChain();

    /**
     * The rig's structural caps — the axes {@code docs/design/11-rig-infrastructure.md} §2 defines
     * that are not compute.
     *
     * <p>Read by the desk, which uses Bandwidth to cap how many tool windows may be open at once
     * ({@code docs/design/ui-design-language.md} §8). That mapping is a <b>[PROPOSAL]</b> and is
     * defaulted off — see {@code docs/design/15-open-questions.md} <b>UI-2</b>.
     */
    RigCapacity capacity();

    /**
     * The engine's own clock.
     *
     * <p>Not {@code Instant.now()}. Everything with a deadline in this client is measured against
     * the session's clock, and a readout that showed the wall clock beside figures computed from a
     * different one would be the same class of disagreement {@code RunningTask#progress} was fixed
     * for. In production the two are the same clock; under a test clock only this one is right.
     */
    java.time.Instant now();

    /**
     * Everything the rig is currently working on, for the activity readout.
     *
     * <p>Deliberately a flat list of one shape rather than "scans, plus recoveries, plus buffers".
     * A player asking "what is this machine doing right now" is asking one question, and three
     * differently-shaped answers stitched together in the view is three chances for one of them to
     * stop being rendered without anyone noticing.
     */
    List<RunningTask> tasks();

    /**
     * False when a remote session has lost its server. Always true for a local session — there is
     * nothing to lose.
     */
    boolean connected();

    /**
     * How loud the rig is right now, 0–1 — <b>not</b> how busy it is.
     *
     * <p>⚠ A rig at full load on self-mining, defences and local scans reads <b>zero</b>, and that is
     * the whole point rather than an edge case: Invariants <b>I4</b> and <b>I9</b> and
     * {@code docs/design/04-mining.md} §3.1 each make one of those silent, and together they are the
     * quiet-play strategy the economy is built to reward. What is loud is work that reaches machines
     * the player does not own.
     *
     * <p>The rules answer this, not the view. It was computed in {@code RigStatus} until 2026-07-27,
     * which put three invariants inside a view class and gave a home server no way to disagree.
     */
    double noise();

    // ------------------------------------------------------------------ intents

    /** Commits cycles to self-mining. Safe, silent, zero-heat (I4), online-only (I5). */
    Outcome allocateSelfMining(long cycles);

    /**
     * Points self-mining at the pool or at the whole chain.
     *
     * <p>Both are self-mining and both keep Invariant I4 — silent, unseizable, zero heat. The only
     * difference is the shape of the income: a steady drip against a pool's share target, or the
     * whole block subsidy at long and random intervals. Switching costs nothing and forfeits
     * nothing, because there is no progress to lose.
     */
    Outcome setMiningMode(MiningMode mode);

    /**
     * What {@code cycles} would earn per hour, in minor units, in the current mode and pool.
     *
     * <p>Asked of the engine rather than scaled locally. The rate depends on the mode and the pool's
     * fee, and a view doing its own arithmetic has already been wrong about it once.
     */
    java.math.BigInteger miningRateFor(long cycles);

    /** This character's chain address, or {@code ""} when not connected. */
    String chainAddress();

    /** The last two dozen blocks, newest first. Empty when not connected. */
    List<ChainBlock> chainBlocks();

    /**
     * The player's own movements, rendered as chain transactions, newest first.
     *
     * <p>⚠ The same list {@link #ledger(int)} returns, in chain clothes — not a second source. A
     * player who adds these up and compares against the balance must get the same answer, because
     * {@code docs/design/04-mining.md} §3.1 makes exactly that comparison the way an intruder is
     * caught.
     */
    List<ChainTransaction> chainTransactions(int limit);

    /**
     * What the chain did while this client was closed — the {@code SYNCHRONIZING} screen's content.
     *
     * <p>Reports zero blocks when there was nothing to catch up, which is the common case: a session
     * that has been running, or a character loaded seconds after it was saved. The LEDGER window asks
     * once when it opens and shows nothing when the answer is nothing.
     *
     * <p>⚠ This describes one transition and not the world, so it is <b>session state</b> — never
     * persisted. Persisting it would replay the sync screen on the next load, reporting a catch-up
     * that had already happened. The blocks are in the chain and the money is in the ledger; this is
     * only the explanation, and an explanation has a shelf life.
     */
    ChainSync chainSync();

    /**
     * The same report, once — for the surface that <em>shows</em> it.
     *
     * <h2>⚠ The window is rebuilt on every open, so reading it cannot be what shows it</h2>
     *
     * A closed tool window keeps no state: {@code DeskManager} calls the factory afresh each time,
     * so a {@code SYNCHRONIZING} panel built from {@link #chainSync()} replayed the entire fill every
     * time the player opened the ledger. The third open in one sitting meant watching a meter fill
     * about a catch-up that had happened an hour earlier.
     *
     * <p>A synchronisation is a <b>transition</b>, and a transition is announceable exactly once.
     * Nothing is lost by consuming it — the rig log already carries the same facts, and a log is
     * where history belongs.
     *
     * <p>Returns a report with no blocks once it has been taken, and on every call for a session that
     * had nothing to catch up. Use {@link #chainSync()} for an idempotent read.
     */
    ChainSync takeChainSync();

    /**
     * Every block this character put hashrate into, newest first.
     *
     * <p>Wider than "blocks won": under a pool it includes every block the <em>pool</em> found while
     * this rig was contributing, and under pay-per-share those pay nothing at all — the pool buys the
     * shares instead. See {@link BlockContribution}, which is where that distinction is explained.
     */
    List<BlockContribution> contributions(int limit);

    /**
     * Sends ethecoin to an address, at the chosen fee.
     *
     * <p>The balance moves now and the transaction enters the mempool; the fee buys how soon a miner
     * packs it into a block. The fee is charged on top, so a sender who cannot afford
     * {@code amount + fee} is refused rather than shorting the recipient.
     */
    Outcome send(String toAddress, java.math.BigInteger wei, FeeTier tier);

    /** The mempool: what is waiting, and what the next blocks would hold. */
    ChainMempool mempool();

    /**
     * Raises a waiting transaction's fee — <b>replace-by-fee</b>.
     *
     * <p>A transaction in a mempool is not committed to anything: its sender can offer more, and
     * miners, who sort by fee rate, will prefer the better offer. It is the mechanism behind every
     * "stuck transaction, bump the fee" thread on the internet, and it is what makes a fee feel like
     * a bid rather than a price.
     *
     * <p>Only the <b>difference</b> is charged — the original fee was debited when the transaction
     * was broadcast. Refused when the transaction has already been mined, when the new tier is not
     * higher (a replacement that paid less would let anyone rewrite a relayed transaction for free),
     * or when the difference cannot be afforded.
     */
    Outcome boostFee(String txHash, FeeTier tier);

    /**
     * What a package on this rig declares about itself, and what it actually is.
     *
     * <p>Backs the installer panel: publisher, contents, size, both digests, and whether a payment is
     * still holding it. Empty for a path that is not a package this rig holds.
     */
    java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest> packageAt(String path);

    /**
     * Commissions a port scan against a machine a sweep has found.
     *
     * <p>The player names the deepest thing they want to know, which sets the cycle cost, the
     * duration and the chance the target notices all at once — see {@code PortScanTarget}. Being
     * noticed is not merely a wasted scan: the target gets a turn.
     */
    Outcome portScan(String address, io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget target);

    /** The last report for this machine, if one was taken this session. */
    java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport> portScanReport(String address);

    /**
     * The intelligence file on one machine — everything ever learned about it, and when.
     *
     * <p>Distinct from {@link #portScanReport}, which is the <em>last scan</em> and is session state.
     * This is the accumulated file: it survives a restart, merges findings across scans of different
     * depths, and dates each one individually so a week-old vault estimate does not read as fresh.
     */
    java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.NodeReport> nodeReport(String address);

    /** Every file on record, most recently updated first. What RECON lists. */
    List<io.github.stoicswe.eyeandsickle.protocol.game.NodeReport> nodeReports();

    /**
     * Names a machine you hold a report on, or clears the name.
     *
     * <p>⚠ Only a machine with a file can be named. A name is a note about intelligence you already
     * hold; letting one attach to a machine nobody has looked at would turn RECON into a bookmark
     * folder with the reports buried in it.
     */
    Outcome nameNode(String address, String alias);

    /** Replaces a machine's tags. Lowercased and de-duplicated; blanks are dropped. */
    Outcome tagNode(String address, List<String> tags);

    /** What a scan of this depth would cost against this machine, before committing to it. */
    PortScanQuote portScanQuote(String address, io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget target);

    /**
     * The price of a scan, in all three currencies it is paid in.
     *
     * <p>⚠ Shown <b>before</b> the player commits. Cycles and seconds are ordinary costs; the risk is
     * the one that makes the choice a choice, and a panel that revealed it afterwards would be
     * offering a gamble without saying it was one.
     *
     * @param riskPercent the chance the target notices, 0–100
     */
    record PortScanQuote(long cycles, long seconds, int riskPercent, boolean affordable) {}

    /** One block with every transaction in it. Null for a height the chain has not reached. */
    ChainBlock chainBlock(long height);

    /** Every pool on the chain, for a picker. Empty when not connected. */
    List<MiningPool> pools();

    /**
     * Joins a pool. Pooled mining only.
     *
     * <p>Costs nothing and forfeits nothing — see {@link #setMiningMode}. Only the pool's <b>fee</b>
     * changes what a rig earns; its scheme and its size change only how lumpily.
     */
    Outcome setMiningPool(String poolId);

    /** Runs a rig scan. The tiers cost 5 / 15 / 35 cycles and buy signal strength, not certainty. */
    Outcome scan(String tier);

    /** Sweeps deployed-miner buffers into the balance. */
    Outcome collect();

    /** Moves an item between storage tiers. The risk change is the point ({@code design/01} §6). */
    Outcome moveItem(String itemId, StorageTier to);

    /** Arms a defence. Defending your own rig never generates heat (Invariant I9). */
    Outcome arm(String kind, int tier);

    // ── The breach (docs/design/05) ───────────────────────────────────────────────────────────
    //
    // Six methods, and every one of them takes or returns a PROTOCOL type rather than anything
    // solo-shaped. That is the same seam ComputeBudget uses: the view binds to a BreachSnapshot and
    // never learns whether a rules engine in this process produced it or a home server sent it.
    // The engine's own types (BreachRules, BreachResult) stop at LocalGameSession.

    /** Nodes the player could attempt right now. Empty until something is discovered. */
    List<io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget> breachTargets();

    /**
     * The breach in progress, if there is one.
     *
     * <p>⚠ A snapshot carries <b>only revealed information</b> — never the Logic code, the true port
     * states or the true objective node. That is not paranoia about a save file the player can edit
     * anyway; it is what keeps the puzzle honest when the same record travels over a wire, and it
     * means a view physically cannot render a cheat even by accident.
     */
    java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot> breach();

    /** Starts an attempt against a target. Reserves compute for the whole attempt. */
    Outcome beginBreach(String targetId);

    /**
     * Spends attention on one move.
     *
     * @param actionId which move — see {@code BreachActionKind}
     * @param argument the move's operand (a band, a code guess, a node id); {@code ""} when it has
     *     none. A string rather than a typed union because the same call has to survive a REST hop.
     */
    Outcome breachAction(String actionId, String argument);

    /** Walks away. No loot, attention already spent stays spent, no proof-of-skill credit. */
    Outcome abortBreach();

    /** Clears a finished breach's outcome slate once the player has read it. */
    Outcome dismissBreach();

    /**
     * Abandons a live breach — what closing the breach window does.
     *
     * <p>⚠ Not the same as {@link #abortBreach}, though it costs the same. Abort is a <em>move</em>:
     * the player looked at the board and walked away, and the window stays open on the outcome slate
     * so they can read why. This is the console being shut on an attempt that is still running, so
     * the slate is cleared too — a slate for an attempt the player never saw end is not
     * comprehension, it is an unexplained screen the next time they open the window.
     *
     * <p>⚠ Recorded as an {@code aborted} resolution rather than deleted, which is the same reasoning
     * that governs a breach that did not survive a quit: silently dropping it would let a player
     * escape a losing attempt by closing a window, and every roll in this engine is frozen precisely
     * so that reloading cannot undo it. The compute is released onto the thermal curve either way.
     */
    Outcome abandonBreach();

    // ── The network (docs/design/07, and the sweep model) ─────────────────────────────────────
    //
    // `sweep` is NOT `scan`. `scan` (above) audits the player's OWN rig for foreign miners;
    // `sweep` probes a network they do not own. Two activities, two verbs — the distinction is
    // itself worth teaching, and collapsing them would make one of the two a lie.

    /**
     * The network as the player currently knows it: their vantage, the visible hosts and links.
     *
     * <p>⚠ Carries <b>only discovered hosts</b>. An undetected node is absent entirely — no
     * placeholder, no "3 more nearby". A count would leak the thing the sweep is supposed to be
     * for, and would make a better sweep tier pointless.
     */
    io.github.stoicswe.eyeandsickle.protocol.game.NetMap net();

    /**
     * Runs a sweep from the current vantage.
     *
     * <p>⚠ Hop range is a <b>hard ceiling</b> and no tier changes it (Invariant I2 — ethecoin never
     * buys a ceiling; {@code docs/design/07} makes hop range exactly that, which is why the
     * Topology Mapper is schematic-gated). A tier buys <em>sensitivity</em> within the reach the
     * player already has. Schematics buy reach; ethecoin buys sensitivity.
     *
     * @param flag {@code ""}, {@code "--wide"} or {@code "--deep"}
     */
    Outcome sweep(String flag);

    /**
     * The sweep ladder, as the rules describe it — <b>including whether each rung may be run.</b>
     *
     * <h2>⚠ Why this is on the port and not worked out in the view</h2>
     *
     * {@code docs/client/05} §5 states the rule this exists to obey: <em>reachability is a server
     * verdict rendered as received (C4); the client never evaluates a gate.</em> A map window that
     * decided a sweep was locked by looking for an item id in the player's inventory would be a
     * second implementation of {@link io.github.stoicswe.eyeandsickle.engine.net.NetRules#owns} living
     * in a view — and the day the rule grows a second condition, the two disagree and the window is
     * the one that lies. So the rules answer, and the panel paints the answer.
     *
     * <h2>An absent rung is NOT a locked rung</h2>
     *
     * A session that cannot reach the rules returns an <b>empty list</b>, and a caller must render
     * that as "no verdict" rather than as "locked". The two are different claims and collapsing them
     * would have the client inventing a gate the moment the network hiccups — which is the same
     * last-known-good rule every other read here follows, applied to a permission instead of a
     * number.
     */
    List<SweepOption> sweepOptions();

    /**
     * One rung of the sweep ladder.
     *
     * @param flag what {@link #sweep} takes: {@code ""}, {@code "--wide"} or {@code "--deep"}
     * @param name the tool's own name, as the market lists it
     * @param available the rules' verdict, rendered as received. Never computed by a view
     * @param requirement what it would take, in words, when it is not available. Empty when it is.
     *     Words rather than a price, because {@code docs/client/05} §5 forbids a generic "locked"
     * @param priceWei what the market charges, or 0 when it is not something you buy
     * @param sensitivity 1, 2 or 3 — and ⚠ <b>never a reach value.</b> Invariant <b>I2</b>: no tier
     *     changes the hop ceiling at any price, and this record carries nothing that could
     * @param cycles compute held for the sweep's whole duration
     * @param seconds how long it runs
     * @param noiseCycles how loud it is while it runs, on the noise meter's scale
     */
    record SweepOption(
            String flag,
            String name,
            boolean available,
            String requirement,
            java.math.BigInteger priceWei,
            int sensitivity,
            long cycles,
            long seconds,
            long noiseCycles) {}

    // ── Shell sessions and the filesystem ─────────────────────────────────────────────────────
    //
    // ⚠ A SESSION IS NOT THE VANTAGE, and the two verbs below are deliberately not the same verb as
    // `connectTo`. The vantage is singular and is what a sweep measures hop distance from — a hard
    // ceiling no purchase moves (Invariant I2). A session is a shell on a machine already held: you
    // may have many, each costs compute for as long as it is open, and none of them buys reach. If
    // one ever became a vantage, reach would multiply by the number of windows a player had open,
    // which is the ceiling sold for the price of a click.

    /** Every shell session currently open, in the order they were opened. */
    List<io.github.stoicswe.eyeandsickle.protocol.game.RemoteSession> sessions();

    /**
     * Opens a shell on a machine the player holds a foothold on. Their own rig is always available.
     *
     * <p>Idempotent — asking for one that is already open raises nothing and costs nothing, which is
     * what makes it safe to wire straight to a control a player may double-click.
     */
    Outcome openSession(String address);

    /** Closes one, handing its held cycles straight back. */
    Outcome closeSession(String address);

    /** Moves a session's working directory. Refused, in words, for anything that is not one. */
    Outcome changeDirectory(String address, String path);

    /**
     * A directory listing on a machine.
     *
     * <p>⚠ {@code FsEntry.readable} is the <b>rules'</b> verdict and a view renders it as received
     * (C4). A file manager that decided readability itself would be answering "may I read this",
     * which is the class of question Invariant <b>I14</b> reserves for the authoritative side — and a
     * file manager is the surface where that mistake is easiest to make, because a path in a tree
     * looks like something you could simply open.
     *
     * @param address the machine, or blank for the player's own rig
     * @return the entries directly under {@code path}, directories first. Never null
     */
    List<io.github.stoicswe.eyeandsickle.protocol.game.FsEntry> list(String address, String path);

    /**
     * The readable contents of a file, or empty when there are none.
     *
     * <p>⚠ Empty is the normal answer. Only a handful of files in this game have text behind them —
     * the remote-access log, and the two game kinds a host can carry. Everything else is a plausible
     * artefact whose job is to make a directory look like a directory, and returning invented log
     * lines for one would be the client fabricating game content on the surface a player is using to
     * investigate.
     */
    List<String> read(String address, String path);

    /**
     * What a thing IS, rather than what it contains — the "Get info" answer.
     *
     * <p>⚠ Separate from {@link #read} because they answer different questions and a player asks
     * them at different moments: read is "show me what is in it" and is what a double-click means;
     * this is "what am I looking at" and is what a right-click means. It is also the one that works
     * on a <b>directory</b>, where there are no contents to show but there is plenty to say.
     */
    /**
     * Completed audits, newest first — the AUDIT window's history.
     *
     * <p>Capped by the rules at the most recent hundred. A clean scan is a row like any other: it is
     * what gives a later finding its date.
     */
    java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.ScanReport> scanReports();

    /**
     * Every file an audit walks on this rig, in the order it walks them.
     *
     * <p>⚠ Stable across calls, because the SCANNER panel repaints once a second and prints the
     * files a scan has reached so far. A list that re-ordered itself would rewrite lines already on
     * screen — which reads as the scan going backwards.
     */
    java.util.List<String> auditPaths();

    List<String> info(String address, String path);

    /**
     * What the upgrade at {@code path} on {@code address} is, and how it compares to what you hold.
     *
     * <p>Answers for a package the player has <b>not</b> taken — a real package carries this
     * metadata so a manager can say what it is about to install. The payload still costs a transfer.
     *
     * @return empty when the path is not an upgrade, or names a tool with no catalogue entry
     */
    java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.UpgradeOffer> upgradeAt(
            String address, String path);

    /**
     * Records that the player deliberately opened something — what fills Recents.
     *
     * <p>⚠ Called by the surfaces where a player <b>chose</b> to go somewhere, never from
     * {@link #list}. Listing runs on every repaint and on every parent lookup, so recording there
     * would fill Recents with directories nobody visited — which is exactly how a recents list stops
     * being worth opening.
     *
     * <p>Only a machine's own operator has a Recents; noting an access on somebody else's machine
     * does nothing. Not an intent and returns no {@code Outcome}, because there is nothing here a
     * rule could refuse.
     */
    void noteAccess(String address, String path);

    /**
     * Starts a download from a machine this rig is connected to.
     *
     * <p>⚠ The duration is the <b>remote end's upload</b>, not this rig's download — a Gigabit line
     * against a 150 Mbit uplink transfers at 18.75 MB/s however good the local link is. The transfer
     * runs as a {@link RunningTask}, so it appears in the rig monitor's activity list and survives
     * the file manager being closed.
     */
    Outcome download(String address, io.github.stoicswe.eyeandsickle.protocol.game.FsEntry entry, String destination);

    /** Where a download may be put — the folders a "Save as" menu should offer. */
    List<String> downloadDestinations();

    /**
     * Installs a downloaded {@code .upg}. The file is consumed and the item becomes owned.
     *
     * <p>⚠ Installing is <b>optional</b>: a package is an asset, and selling it is a real
     * alternative. That is the whole point of the secondary market.
     */
    Outcome install(String path);

    /**
     * Sells a downloaded {@code .upg} on the secondary market.
     *
     * <p>⚠ Refused for anything not already gated on ethecoin. Selling a schematic-gated tool would
     * let anybody with enough money buy a ceiling, which is Invariant <b>I2</b>. The refusal says so.
     */
    Outcome sell(String path);

    /**
     * Deletes a file this rig stores.
     *
     * <p>⚠ Destructive and not undoable. The rules do not ask — asking is this layer's job, and the
     * shell's {@code rm} would be wrong to — so any GUI surface must confirm before calling it.
     *
     * @param address the machine, for the own-rig check. Blank or the rig's own address
     */
    Outcome delete(String address, String path);

    /** Transfers currently in flight, for a progress readout. A subset of {@link #tasks()}. */
    List<RunningTask> transfers();

    /** Moves the vantage to a host the player holds. Sweeping again measures hops from there. */
    Outcome connectTo(String address);

    /** Pulls a document off a host that carries one. */
    Outcome download(String address);

    /** Everything downloaded so far. */
    List<io.github.stoicswe.eyeandsickle.protocol.game.NetDocument> documents();

    // ── The process table ─────────────────────────────────────────────────────────────────────
    //
    // docs/design/04-mining.md §3.1 has always described a manual audit and nothing ever implemented
    // one. This is it: everything running on the rig, as rows, with a parasite hiding among them in
    // whatever costume the rules gave it.

    /**
     * Everything running on the rig.
     *
     * <p>⚠ <b>No row says which one is hostile.</b> A parasite hides by looking like the others, and
     * the only thing that gives it away is the data — a name one character off a real daemon, a user
     * nothing else runs as, a CPU figure that does not match its own accumulated CPU time. A flag the
     * client could paint red would turn an investigation into a highlight. See {@code RigProcess}.
     */
    List<io.github.stoicswe.eyeandsickle.protocol.game.RigProcess> processes();

    /**
     * Stops a process.
     *
     * <p>A tool of the player's own ends where it stands and <b>keeps what it had</b> — its cycles
     * still take the full thermal recovery, because stopping early buys back time and never capacity.
     * A parasite goes, and its buffer is forfeit; a crack is what takes a buffer. A system process is
     * refused, in words, and offered {@link #restartProcess} instead.
     */
    Outcome killProcess(String processId);

    /**
     * Restarts a system process, taking down every running tool that depended on it.
     *
     * <p>Each of those is ended exactly as {@link #killProcess} would end it. That cascade is the
     * price, and it is what makes suspecting a system row a decision rather than a free click.
     */
    Outcome restartProcess(String processId);

    // ── Filing what has been found ────────────────────────────────────────────────────────────
    //
    // Folders are the player's own annotation over what they have discovered, and nothing in the
    // rules reads one back — filing a machine changes no cost, no gate and no chance. They are on
    // the port rather than in client-side settings for one reason: a folder may only hold an address
    // the player has actually discovered, and "have I discovered this" is a rules question the client
    // is specifically not allowed to answer (Invariant I14). A client-side store would either
    // duplicate knownNodes or accept any address it was handed, and the second is a free oracle for
    // the one thing every sweep tier is sold on.

    /**
     * The folder tree, <b>parents before children, siblings by name</b>.
     *
     * <p>The order is the contract, not an incidental. Both surfaces that draw this — the map window
     * and the terminal — indent by {@code depth} and walk the list once; if either did its own
     * traversal the two would eventually sort siblings differently, which is the C1 parity failure
     * that is hardest to notice.
     */
    List<io.github.stoicswe.eyeandsickle.protocol.game.NetFolder> folders();

    /** Discovered machines not filed anywhere, ascending by address. */
    List<String> unfiledNodes();

    /** Creates a folder under {@code parentId}, or under nothing when it is blank. */
    Outcome createFolder(String parentId, String name);

    Outcome renameFolder(String folderId, String name);

    /** Moves a folder under a new parent. Refused when that would put it inside itself. */
    Outcome moveFolder(String folderId, String newParentId);

    /**
     * Removes a folder, lifting what was inside it up a level.
     *
     * <p>Never recursive. Filing carries no risk lesson, so there is nothing to be gained by making a
     * mis-click expensive — the worst outcome of a wrong removal is a flattened level.
     */
    Outcome removeFolder(String folderId);

    /** Files a discovered machine under a folder, or unfiles it when {@code folderId} is blank. */
    Outcome fileNode(String address, String folderId);

    /** Buys from the market. Refused — not thrown — when the player cannot afford it or a gate blocks. */
    Outcome purchase(String offeringId);

    /**
     * Buys today's bundle as one act, at the bundle price.
     *
     * <h2>⚠ NOT a loop over {@link #purchase}, and that is the whole reason it exists</h2>
     *
     * Buying the members one at a time charges the retail price for each and silently discards the
     * bundle discount — the shop advertising one number and the ledger recording another, which is
     * the single most damaging thing a sale can get wrong. One call, one debit, one ledger row, one
     * archive.
     *
     * <p>Refused when any member is sold out, already owned or already on its way: a bundle is
     * all-or-nothing, because a partial one charged the bundle price for fewer things than the
     * bundle price was quoted for.
     */
    Outcome purchaseBundle();

    /**
     * Everything bought and not yet arrived, in the order it will arrive.
     *
     * <p>Empty when nothing is owed. The first entry that is not paused is the one downloading —
     * flagged on the record rather than left for the client to work out, because deriving it needs
     * the queue policy and a client holding the policy can predict it wrongly.
     */
    List<io.github.stoicswe.eyeandsickle.protocol.game.DownloadOrder> downloads();

    /** Holds a queued download. Pausing the active one promotes the next. */
    Outcome pauseDownload(String orderId);

    /** Releases a held download. */
    Outcome resumeDownload(String orderId);

    /**
     * Moves a download through the queue.
     *
     * @param delta how far, negative towards the front
     */
    Outcome moveDownload(String orderId, int delta);

    /**
     * Unpacks a {@code .tar.xz}, which takes real time.
     *
     * <p>⚠ Slower than fetching it was — {@code xz} trades expensive decompression for small files,
     * and on a fast line the squeeze is what you wait for. That is the fact the wait exists to
     * teach, so it is a task with a countdown rather than an instant rename.
     */
    Outcome extract(String path);

    /**
     * The Shadow Market for one listing.
     *
     * <p>⚠ Always answerable, even in solo — the darknet market's listings are readable whether or
     * not there is anybody real on the other side. In solo the counterparties are simulated; on a
     * server they are players, and the client cannot tell, which is the point of this port.
     *
     * @param itemType which listing
     * @param interval the candle width, as a {@code ShadowMarket.Interval} name
     * @param candles how many candles to draw
     */
    io.github.stoicswe.eyeandsickle.protocol.game.ShadowSnapshot shadowMarket(
            String itemType, String interval, int candles);

    /** Everything this market lists. ⚠ Ethecoin-gated items only — I2 and I8. */
    List<String> shadowListings();

    /**
     * Rests a limit order.
     *
     * <p>⚠ A buy escrows the ethecoin immediately; a sell reserves one specific copy by id. Both are
     * returned by {@link #cancelShadowOrder}.
     *
     * @param heldItemId for a sell, which copy — blank picks any unequipped one
     */
    Outcome placeShadowOrder(
            String itemType, boolean buy, java.math.BigInteger limitPriceWei, int quantity, String heldItemId);

    /**
     * Withdraws a resting order.
     *
     * <p>⚠ Nothing comes back, because nothing was held — this market has no escrow.
     */
    Outcome cancelShadowOrder(String orderId);

    /**
     * Takes a listing outright at the seller's price.
     *
     * <h2>⚠ THE MONEY GOES IMMEDIATELY AND CANNOT COME BACK</h2>
     *
     * There is no escrow. On an {@code ATTACHED} listing the goods arrive in the same call; on a
     * {@code SEND_LATER} one the buyer holds nothing but an obligation and a deadline, and if the
     * seller never ships, the money is simply gone. <b>Callers must confirm with the player first</b>,
     * and the confirmation must name the delivery mode — it is the whole decision.
     *
     * @param itemType which listing's instrument
     * @param listingId the offer, as the snapshot gave it
     */
    Outcome buyShadowListing(String itemType, String listingId);

    /**
     * Puts something up for sale.
     *
     * <p>⚠ Refused unless the player holds every named copy, unequipped — including for
     * {@code SEND_LATER}. A promise-only listing for something never owned is a confidence trick
     * with no cost of entry.
     *
     * @param itemIds which copies, by id
     * @param sendLater true to keep the goods and owe delivery, false to attach them now
     */
    Outcome createShadowListing(
            String itemType, java.math.BigInteger priceWei, List<String> itemIds, boolean sendLater);

    /** Takes a listing down; anything attached comes back to storage. */
    Outcome cancelShadowListing(String listingId);

    /** Hands over what was promised, closing an obligation before its deadline. */
    Outcome fulfilShadowObligation(String obligationId);

    // ── AnonShare ─────────────────────────────────────────────────────────────────────────────

    /**
     * A quote and the session state around it.
     *
     * <p>⚠ Answerable at any time, including when the market is shut and when the player is offline —
     * a brokerage screen that went blank out of hours would be a screen nobody could learn to read.
     */
    io.github.stoicswe.eyeandsickle.protocol.game.SharesSnapshot shares(String symbol);

    /** Buys at the feed's price, plus commission. ⚠ Refused when the market is closed. */
    Outcome buyShares(String symbol, int shares);

    /** Sells a specific parcel. ⚠ By holding id, so the player picks their own cost basis. */
    Outcome sellShares(String holdingId, int shares);

    /** Creates a named collection. */
    Outcome createPortfolio(String name);

    /** Removes one. ⚠ Holdings filed under it are unfiled, never sold. */
    Outcome deletePortfolio(String portfolioId);

    /** Adds or removes a symbol from a portfolio's watchlist. */
    Outcome watchSymbol(String portfolioId, String symbol, boolean watch);

    /** Files a holding under a portfolio, or unfiles it with a blank id. */
    Outcome fileHolding(String holdingId, String portfolioId);

    /**
     * Records a refusal the <em>client</em> made before it asked the rules anything.
     *
     * <h2>Why this exists rather than the view printing it somewhere</h2>
     *
     * A few refusals are genuinely the interface's: "pick a target for this action first", "no layer
     * is active". The rules never see those requests, so they never produce an {@link Outcome}, so
     * they never reach the log — and once the panels stopped printing refusals inline they would have
     * become <b>silent</b>, which is the one outcome a refusal must never be.
     *
     * <p>This gives them the same route every other refusal takes: into the rig's journal, and from
     * there into the notification system, which is "the log, filtered" by design. The player sees the
     * same kind of message in the same place whether the rules declined or the interface did, and can
     * go back and read it either way.
     *
     * <p>⚠ It writes a log line and <b>nothing else</b>. It is not a back door for the client to
     * author game state (Invariant <b>I14</b>) — there is no argument here that could change a
     * balance, a gate or an outcome, and the returned status is always {@code REFUSED}.
     *
     * @param facility which part of the rig this concerns, in the log's own vocabulary
     * @param why the sentence the player reads
     */
    Outcome refuse(String facility, String why);

    // ------------------------------------------------------------------ change notification

    /**
     * Registers a listener called whenever anything above may have changed.
     *
     * <p>Deliberately coarse. A fine-grained event model would be more efficient and would also be a
     * second source of truth about what changed; with one signal, every view re-reads the port and
     * cannot drift from it. The data is small enough that this is free.
     *
     * @return a handle that removes the listener
     */
    AutoCloseable onChange(Consumer<GameSession> listener);

    /** Advances the game. The client drives this from a timeline; a remote session may ignore it. */
    void tick();

    /** Flushes any unsaved state. Called on autosave and on exit. */
    void persist();

    @Override
    void close();

    // ------------------------------------------------------------------ value types

    /**
     * The result of asking the game to do something.
     *
     * <p>{@code status} follows {@code docs/client/04-terminology-and-education.md} §3.5 exactly,
     * including the {@code sysexits.h} borrowings, so the terminal's {@code $?} and a button's error
     * toast are the same value rendered two ways.
     */
    record Outcome(int status, String message) {

        public static final int OK = 0;
        public static final int REFUSED = 1;
        public static final int USAGE = 2;
        public static final int UNAVAILABLE = 69; // EX_UNAVAILABLE — could not reach the server
        public static final int TEMPFAIL = 75; // EX_TEMPFAIL — sent, no answer yet
        public static final int NOPERM = 77; // EX_NOPERM — a gate blocks this
        public static final int CANNOT_FIELD = 126;
        public static final int NO_SUCH_COMMAND = 127;
        public static final int ABORTED = 130; // 128 + SIGINT

        public static Outcome ok() {
            return new Outcome(OK, "");
        }

        public static Outcome ok(String message) {
            return new Outcome(OK, message);
        }

        /** A rule applied and nothing changed. This is not an error condition; it is an answer. */
        public static Outcome refused(String why) {
            return new Outcome(REFUSED, why);
        }

        public static Outcome usage(String why) {
            return new Outcome(USAGE, why);
        }

        public static Outcome gated(String requirement) {
            return new Outcome(NOPERM, requirement);
        }

        public boolean succeeded() {
            return status == OK;
        }
    }

    /** One owned thing, flattened for display. */
    record InventoryItem(
            String itemId,
            String displayName,
            String itemType,
            StorageTier tier,
            String origin,
            boolean equipped,
            long equippedCycles,
            /**
             * Whether this item carries a verifiable provenance chain. False for everything in a solo
             * game, and {@code verify} says so plainly rather than inventing a chain that would look
             * checkable and prove nothing.
             */
            boolean hasProvenance) {}

    /** One ledger row. */
    record LedgerRow(
            String entryId,
            java.time.Instant at,
            java.math.BigInteger deltaWei,
            java.math.BigInteger balanceAfterWei,
            String type,
            String description) {}

    /** One discovered machine. Undiscovered nodes are never in this list — recon is a paid service. */
    record KnownNode(
            String address, String label, int reconLevel, int tier, int deployedMiners, boolean hostsForeignMiner) {}

    /** One armed defence and what it is holding. */
    record ArmedDefense(String kind, int tier, long reservedCycles, boolean triggered) {}

    /**
     * One line of the rig log.
     *
     * <p>{@code severity} is RFC 5424's real numbering, {@code facility} is which subsystem spoke.
     * Both are carried through rather than flattened into a string, so the panel can filter and
     * {@code log | grep} can still work on the rendered form.
     */
    record LogLine(java.time.Instant at, int severity, String facility, String message, String keyword, String glyph) {}

    /**
     * Mining, summarised.
     *
     * @param selfMiningCycles cycles committed to self-mining; earns only while the client is open
     * @param bufferedWei yield sitting on hosts, waiting to be collected
     * @param bufferCapWei the ceiling those buffers stop at — the reason time away is worth
     *     something but not proportionally
     * @param deployedMiners how many are live
     */
    record MiningSummary(
            long selfMiningCycles,
            java.math.BigInteger bufferedWei,
            java.math.BigInteger bufferCapWei,
            int deployedMiners) {

        /** True once every buffer is full, which is when being away stops paying at all. */
        public boolean buffersFull() {
            return deployedMiners > 0 && bufferedWei.compareTo(bufferCapWei) >= 0;
        }
    }

    /**
     * The rig's non-compute caps ({@code docs/design/11-rig-infrastructure.md} §2).
     *
     * @param bandwidth simultaneous engagements
     * @param memoryBuffer equipped-tool slots — how much can be readied at once, as distinct from
     *     how much is owned
     * @param thermalBudget how fast spent cycles return
     */
    /**
     * One piece of work the rig is doing, with enough to draw a progress meter and an ETA.
     *
     * <p>{@code startedAt} may be null on state written before it was tracked. That is not the same
     * as zero progress and must not be rendered as such — {@link #progress()} returns a negative
     * value to mean <em>unknown</em>, which the readout shows as an indeterminate sweep rather than
     * an empty bar. A bar reading 0% on a recovery that is nearly finished is worse than one that
     * admits it does not know.
     *
     * @param facility which subsystem owns it, matching the log's facility names so the two
     *     surfaces name the same thing
     * @param cycles compute this work is holding, or 0 if it holds none
     */
    record RunningTask(
            String id,
            String facility,
            String label,
            String detail,
            java.time.Instant startedAt,
            java.time.Instant endsAt,
            long cycles,
            java.time.Instant asOf) {

        /**
         * ⚠ {@code asOf} is the session's own clock, stamped when this record was built — never
         * {@link java.time.Instant#now()}.
         *
         * <p>Reading the wall clock here would be the same mistake {@code ComputeRules.spend}'s
         * comment warns about one module down: a view that reads the real time behind the engine's
         * back disagrees with the engine about what time it is. In production the two are the same
         * clock and nothing would ever look wrong; under a fixed test clock every task reported 100%
         * complete the instant it started, which is how this was caught.
         */
        public double progress() {
            if (startedAt == null || endsAt == null) {
                return -1;
            }
            long total = java.time.Duration.between(startedAt, endsAt).toMillis();
            if (total <= 0) {
                return 1;
            }
            long done = java.time.Duration.between(startedAt, asOf).toMillis();
            return Math.max(0, Math.min(1, done / (double) total));
        }

        /** Time left, never negative. Zero means it should complete on the next tick. */
        public java.time.Duration remaining() {
            if (endsAt == null) {
                return java.time.Duration.ZERO;
            }
            java.time.Duration left = java.time.Duration.between(asOf, endsAt);
            return left.isNegative() ? java.time.Duration.ZERO : left;
        }

        public boolean indeterminate() {
            return progress() < 0;
        }
    }

    record RigCapacity(int bandwidth, int memoryBuffer, int thermalBudget) {

        /** Windows that never count against Bandwidth: the six that reach nothing. */
        public static final int FREE_WINDOWS = 6;

        /**
         * The window cap {@code ui-design-language.md} §8 proposes, if it is switched on.
         *
         * <p><b>[PROPOSAL]</b>, and the arithmetic is the whole proposal. A starting rig has
         * {@code bandwidth = 1}, so capping windows at Bandwidth directly would allow <em>one</em>
         * open panel and make the game unusable. The split below is the smallest thing that makes
         * §8's idea coherent: the tools that are not engagements — the rig monitor, the terminal,
         * the log, the manual, settings, the switcher — are always available, and Bandwidth caps
         * the ones that actually reach out to something.
         *
         * <p>Logged as <b>UI-2</b> in {@code docs/design/15-open-questions.md} and defaulted off,
         * because a cap that turns out to be wrong should not be discovered by a player who cannot
         * open their own map.
         */
        public int proposedWindowCap() {
            return FREE_WINDOWS + Math.max(1, bandwidth);
        }
    }
    /**
     * What the market is charging right now, deals included.
     *
     * <p>⚠ Read from here rather than from the rules directly. The engine runs on a server for LAN
     * and federated play, so a view that called {@code MarketDeals} would render a shop in single
     * player and an empty shelf online.
     *
     * @return the current window; {@link io.github.stoicswe.eyeandsickle.protocol.game.MarketWindow#none()}
     *     when this session cannot price anything
     */
    io.github.stoicswe.eyeandsickle.protocol.game.MarketWindow market();

}
