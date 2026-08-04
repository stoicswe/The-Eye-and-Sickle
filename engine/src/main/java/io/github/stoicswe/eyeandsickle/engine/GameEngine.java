package io.github.stoicswe.eyeandsickle.engine;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachAction;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import io.github.stoicswe.eyeandsickle.engine.breach.BreachResult;
import io.github.stoicswe.eyeandsickle.engine.breach.BreachRules;
import io.github.stoicswe.eyeandsickle.engine.breach.BreachSnapshots;
import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.breach.Targets;
import io.github.stoicswe.eyeandsickle.engine.fs.Recents;
import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.net.FolderRules;
import io.github.stoicswe.eyeandsickle.engine.net.NetRules;
import io.github.stoicswe.eyeandsickle.engine.net.SessionRules;
import io.github.stoicswe.eyeandsickle.engine.net.SweepTier;
import io.github.stoicswe.eyeandsickle.engine.net.TopologyGenerator;
import io.github.stoicswe.eyeandsickle.engine.net.TransferRules;
import io.github.stoicswe.eyeandsickle.engine.rules.ChainExplorer;
import io.github.stoicswe.eyeandsickle.engine.rules.ChainRules;
import io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.engine.rules.EventLog;
import io.github.stoicswe.eyeandsickle.engine.rules.LedgerRules;
import io.github.stoicswe.eyeandsickle.engine.rules.MempoolRules;
import io.github.stoicswe.eyeandsickle.engine.rules.MiningRules;
import io.github.stoicswe.eyeandsickle.engine.rules.ScanRules;
import io.github.stoicswe.eyeandsickle.engine.save.SaveStore;
import io.github.stoicswe.eyeandsickle.engine.state.AllocationState;
import io.github.stoicswe.eyeandsickle.engine.state.ChainState;
import io.github.stoicswe.eyeandsickle.engine.state.DefenseState;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.LedgerEntryState;
import io.github.stoicswe.eyeandsickle.engine.state.MinerState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.PendingTxState;
import io.github.stoicswe.eyeandsickle.engine.state.ScanReportState;
import io.github.stoicswe.eyeandsickle.engine.state.SessionState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The single-player game, as one object.
 *
 * <h2>What this is</h2>
 *
 * A rules engine over a {@link GameSave}, with no framework, no database, no thread and no socket.
 * The client owns the clock: it calls {@link #tick(Instant)} on a JavaFX timeline and this class
 * advances mining, recovery and heat. Nothing here starts a thread of its own, which is what keeps
 * "single player" from quietly becoming "a server you did not know you were running".
 *
 * <h2>The catch-up rule</h2>
 *
 * {@link #resume(Instant)} applies elapsed real time on load, which is what makes deployed miners
 * (the only offline income, Invariant I5) work at all. It is also where the buffer cap earns its
 * keep: a player returning after a week gets four hours of yield per miner, not a week's, because the
 * cap bit almost immediately. Without the catch-up the mechanic would be dead; without the cap it
 * would trivialise the economy.
 *
 * <h2>What it deliberately cannot do</h2>
 *
 * There is no method here that federates, exports for trade, or mints a provenance chain. A solo
 * character is local-only ({@code docs/architecture/02} §4), and the way that is enforced is that the
 * capability does not exist rather than that a flag is checked.
 */
public final class GameEngine {

    private final SaveStore store;
    private final Clock clock;
    private GameSave save;
    private Instant lastTick;

    /**
     * What the last {@link #resume()} filled in — the {@code SYNCHRONIZING} screen's whole content.
     *
     * <h2>⚠ Session state, deliberately not saved</h2>
     *
     * It describes one transition rather than the world: "the chain gained 51 blocks just now" is
     * true once, and persisting it would make the sync screen reappear on the next load reporting a
     * catch-up that had already happened. The blocks themselves are in the chain and the money is in
     * the ledger; this is only the explanation, and an explanation has a shelf life.
     */
    private io.github.stoicswe.eyeandsickle.protocol.game.ChainSync sync;

    /**
     * Whether the player has been shown {@link #sync} yet.
     *
     * <p>Session state for the same reason the report is, and reset by {@link #resume()} — a load is
     * what produces a report, so a load is what makes one worth showing again. Not saved: a flag
     * saying "already announced" that survived a restart would suppress the announcement for the one
     * load that actually had something to announce.
     */
    private boolean syncShown;

    private GameEngine(SaveStore store, GameSave save, Clock clock) {
        this.store = store;
        this.save = save;
        this.clock = clock;
        this.lastTick = clock.instant();
        this.sync = io.github.stoicswe.eyeandsickle.protocol.game.ChainSync.none(clock.instant());
    }

    /**
     * Opens the save at {@code store}, creating a new character if there is not one.
     *
     * <p>The clock is injected rather than read from {@code Instant.now()} anywhere inside. A rules
     * engine that reaches for the wall clock behind its caller's back cannot be tested
     * deterministically, and — the sharper problem — it can disagree with itself about what time it
     * is, so an action started "now" outlives a tick that happens "later".
     */
    public static GameEngine open(SaveStore store, String handleIfNew, Clock clock) {
        GameSave loaded = store.load();
        if (loaded == null) {
            loaded = newCharacter(handleIfNew, clock.instant());
        } else {
            backfill(loaded, clock.instant());
        }
        GameEngine game = new GameEngine(store, loaded, clock);
        game.resume();
        return game;
    }

    /**
     * Brings a save written by an older build up to what this one expects.
     *
     * <h2>The world, for a character created before there was one</h2>
     *
     * {@code TopologyGenerator.generate} is idempotent — it returns immediately when {@code topology}
     * is already set — so this rolls a world exactly once, for a save that has never had one, from
     * that save's own persisted seed. It is not a reroll and cannot become one.
     *
     * <p>⚠ <b>The alternative was tried and it is not "harmless".</b> {@code GameSave.topology} used
     * to be documented as deliberately left null on an old save, so that "an old character keeps
     * working with an empty map rather than being handed a freshly rolled world on load". That
     * reasoning is right about regeneration and wrong about the outcome: a null topology is not a
     * small world, it is <em>no</em> world. {@code NetRules.view} returns an empty map, {@code net}
     * lists nothing, and {@code beginSweep} refuses every sweep at every tier — permanently, with the
     * refusal that reaches the player naming compute the rig has plenty of. A character in that state
     * cannot reach the network half of the game at all and has no way to find out why. Backfilling
     * costs nothing and is the only reading under which they can.
     *
     * <h2>Filing</h2>
     *
     * {@code netFolders} is left empty rather than seeded — a folder is the player's own decision and
     * there is no default filing that would not be somebody's clutter. {@code FolderRules.repair}
     * handles the older shape (a node with no {@code folderId} at all) on the first read.
     */
    private static void backfill(GameSave save, Instant now) {
        boolean hadNoWorld = save.topology == null;
        TopologyGenerator.generate(save, now);
        if (hadNoWorld && save.topology != null) {
            EventLog.notice(
                    save, "net", "network interface came up: this character predates the map. `sweep` now works.", now);
        }
        if (save.netFolders == null) {
            save.netFolders = new java.util.ArrayList<>();
        }
        if (save.chain == null) {
            // A character who predates the chain joins it at its current height, exactly as anyone
            // installing a wallet today does. Starting them at block zero would say the chain had
            // been waiting for them, which is the opposite of what a decentralised ledger is.
            Rng rng = Rng.of(save);
            save.chain = ChainRules.genesis(now, rng);
            rng.commit(save);
            EventLog.notice(
                    save,
                    "mining",
                    "chain synced at height " + save.chain.height
                            + "; self-mining is pooled by default. `mine --solo` to go it alone.",
                    now);
        }
        abandonBreachInProgress(save, now);
    }

    /**
     * A breach that was still live when the game closed is <b>abandoned, as an abort</b>.
     *
     * <h2>An attempt does not survive a quit</h2>
     *
     * Everything else with a duration does — a scan finishes while the client is shut, deployed
     * miners accrue, a sweep settles on the first tick back — because all of those are work the rig
     * is doing. A breach is not: it is the player sitting at a console, and there is nobody at the
     * console when the game is closed. Resuming one would also mean the desk restoring an exploit
     * window onto a half-played puzzle the player has no memory of.
     *
     * <h2>⚠ Abandoned as an ABORT, not deleted — and the difference is an exploit</h2>
     *
     * Clearing {@code activeBreach} outright is one line shorter and hands the player a free escape:
     * a losing attempt could be made never to have happened by quitting, which is precisely the
     * reroll-by-reloading this engine refuses everywhere else (a scan's finding, a sweep's result and
     * a breach board are all frozen at commission for the same reason). Routing it through
     * {@code BreachRules.abort} records the {@code aborted} resolution and releases the reserved
     * compute, so quitting mid-attempt costs exactly what walking away costs — which is what
     * {@code docs/design/05-hacking-minigame.md} §4 calls "a sanctioned outcome, not a loss of nerve".
     *
     * <p>A breach that had already <em>resolved</em> is left alone: the outcome slate is where a loss
     * becomes comprehensible ({@code 05} §1 constraint 4), and a player who quit rather than read it
     * should still get to.
     */
    /**
     * Abandons a live breach on demand — what closing the breach window does.
     *
     * <p>Exactly the same act as the one {@link #open} performs for a breach that did not survive a
     * quit, and deliberately the same code: closing the console and closing the client are the same
     * gesture as far as the attempt is concerned, and two implementations of "abandon" would be two
     * chances for one of them to forget to release the cycles.
     *
     * @return true if there was something to abandon
     */
    public boolean abandonBreach() {
        if (save.activeBreach == null || !save.activeBreach.outcome.isEmpty()) {
            return false;
        }
        abandonBreachInProgress(save, clock.instant());
        return true;
    }

    private static void abandonBreachInProgress(GameSave save, Instant now) {
        if (save.activeBreach == null || !save.activeBreach.outcome.isEmpty()) {
            return;
        }
        String label = save.activeBreach.targetLabel;
        BreachRules.abort(save, now);
        // Then cleared. abort() RESOLVES the breach rather than removing it — the outcome slate is
        // where a loss becomes comprehensible — but a slate the player never saw the breach for is
        // not comprehension, it is an unexplained screen where the target list should be. The log
        // line below is the right home for "this happened while you were away", which is what
        // resume()'s whole logging block exists for.
        BreachRules.dismiss(save);
        EventLog.notice(
                save,
                "breach",
                "the attempt on " + label + " was abandoned; it is recorded as aborted and its "
                        + "cycles are recovering.",
                now);
    }

    /** The engine's current time. Every timestamp it writes comes from here. */
    public Instant now() {
        return clock.instant();
    }

    /** A fresh character: base rig, no money, nothing owned, nothing known. */
    public static GameSave newCharacter(String handle, Instant now) {
        GameSave s = new GameSave();
        s.handle = handle == null || handle.isBlank() ? "operator" : handle.trim();
        s.createdAt = now;
        s.lastPlayedAt = now;
        s.ethecoinWei = Balance.STARTING_ETHECOIN_WEI;

        // A parasite on the new rig, from the first second of the game.
        //
        // docs/design/04 §5.1 makes cracking a miner the tutorial case for the whole breach system:
        // it is self-contained, it is on your own rig so it generates no heat (Invariant I9), and
        // the buffer it has been filling is the prize. Without one planted here a fresh character
        // has no reachable target at all and the core loop is unreachable until they discover a
        // node — which is a long way into a game whose central pillar is "the puzzle IS the game".
        //
        // It also makes the audit mechanic true on day one: by Invariant I6 the miner draws the
        // HOST's cycles, so the compute ledger no longer adds up, and docs/design/04 §3.1 calls
        // noticing that discrepancy the game's second-strongest tutorial vector. There is now
        // something to notice.
        // ⚠ DERIVE THE SEED BEFORE ANYTHING DRAWS FROM IT. GameSave.rngSeed has a constant
        // default, so without this line every character in every install generates the identical
        // world — the topology, the detection rolls, the loot and the documents would all be the
        // same for everyone, and the bug is invisible until two players compare notes.
        s.rngSeed = Rng.derive(s.characterId, now);

        // The chain, before anything can mine against it.
        Rng chainRng = Rng.of(s);
        s.chain = ChainRules.genesis(now, chainRng);
        chainRng.commit(s);

        // The world: up to 7 virtual servers and their machines, generated once and persisted.
        // Generated BEFORE the tutorial miner so the miner's own draws cannot shift the topology's
        // position in the RNG stream — see Rng's contract about drawing unconditionally.
        TopologyGenerator.generate(s, now);

        Targets.plantTutorialMiner(s, now);
        return s;
    }

    public GameSave state() {
        return save;
    }

    public SaveStore store() {
        return store;
    }

    // ------------------------------------------------------------------ time

    /**
     * Applies everything that happened while the client was not running.
     *
     * <p>Ordering matters and is not arbitrary. Recovery settles first so that returned cycles are
     * available to the load-factor calculation; mining accrues second so it accrues against the
     * settled rig. Reversing the two would charge a returning player a busy rig's recovery penalty
     * for time they spent with the client closed.
     */
    public void resume() {
        Instant now = clock.instant();
        // ⚠ Sessions are pruned FIRST, before anything reads the compute picture. A session on a
        // machine the player no longer holds is a live reservation against a shell that would refuse
        // every command — two cycles the rig monitor shows as spent with nothing to point at. It has
        // to happen on the offline path for the same reason task settlement does: a foothold can be
        // lost to a patch that landed while the game was closed.
        for (String address : SessionRules.prune(save)) {
            EventLog.notice(save, "net", "shell session on " + address + " ended: the foothold is gone.", now);
        }
        // ⚠ On the LOAD path as well as after a breach, and both are needed. A save written before
        // this was wired carries BREACHED resolutions and no footholds, so without settling here the
        // bug would be permanent for anyone who had already taken a machine — they would have to
        // breach it a second time, on a target the game would still be refusing `connect` to.
        settleBreachOutcomes();
        long recovered = ComputeRules.settleRecovered(save.rig, now);
        // ⚠ Tasks settle HERE, not only in tick(). resume() sets lastTick = now, so the first tick
        // after loading sees zero elapsed time and returns early — a six-minute scan that ended
        // while the game was closed would sit at 100% forever, never completing and never logging
        // its finding. Offline work belongs on the offline path, next to the miner accrual that
        // already lives here for exactly the same reason.
        // ⚠ The absence is the delta here, so a queue paused across four days is still paused when
        // the player returns. Without this every held transfer would find its deadline long past and
        // complete on the first tick back — the pause doing precisely the opposite of what it says,
        // and only ever for a player who closed the client.
        java.time.Duration absence = java.time.Duration.between(save.lastPlayedAt, now);
        io.github.stoicswe.eyeandsickle.engine.rules.DownloadQueue.settle(
                save, absence.isNegative() ? java.time.Duration.ZERO : absence, now);
        settleTasks(now);
        // Second sweep, and it is not redundant. Under UI-6's hold-then-recover a finished task only
        // becomes RECOVERING inside settleTasks above, dated from when it ended — so a scan that
        // finished a week ago is, at this instant, a recovering allocation whose time has long since
        // passed. Without this the player would watch a week-old scan recover in front of them.
        recovered += ComputeRules.settleRecovered(save.rig, now);
        BigInteger accrued = MiningRules.accrueDeployedMiners(save, now);

        // ⚠ The chain ran while the client did not, and until 2026-07-29 it did not — height froze
        // at the last tick, so a character played on Monday and again on Friday found four days of
        // wall-clock time and zero blocks, on the one readout whose whole subject is that nobody can
        // stop it. docs/design/04-mining.md §1.3d. The fill is shown rather than applied silently:
        // a height that jumped 51 blocks with no explanation is indistinguishable from a tampered
        // save, which is exactly the reading §3.1 trains players into.
        ChainRules.Sync walked = catchUpChain(now);

        // The log's primary job: telling a returning player what happened while they were gone.
        // Without this, offline income is invisible and a player has no way to tell it from a bug.
        java.time.Duration away = java.time.Duration.between(save.lastPlayedAt, now);
        if (!away.isNegative() && away.toMinutes() >= 1) {
            EventLog.notice(save, "rig", "Resumed after " + humanAway(away) + " away.", now);
            if (recovered > 0) {
                EventLog.info(save, "compute", recovered + " cycles finished recovering while away.", now);
            }
            if (accrued.signum() > 0) {
                EventLog.info(
                        save,
                        "mining",
                        "Deployed miners buffered " + Ethecoin.format(accrued) + " while away. `collect` sweeps it.",
                        now);
            }
            logSync(walked, now);
        }

        save.lastPlayedAt = now;
        this.lastTick = now;
    }

    /**
     * Runs the chain forward over the absence and settles whatever the rig earned before it stopped.
     *
     * <h2>⚠ Two clamps that must agree, and only one of them is enforced here</h2>
     *
     * {@code ChainRules.sync} has already excluded the player from the winner draw on every block
     * past the spin-down window, so solo and PPLNS income is capped by the chain itself. Pay-per-share
     * is <b>not</b> — it runs on its own share clock off {@code elapsed} — so the elapsed handed to
     * {@code runSelfMining} is the capped window {@code sync} reports rather than the absence.
     * Passing the absence would break Invariant I5 silently, and only for PPS miners.
     */
    private ChainRules.Sync catchUpChain(Instant now) {
        Rng rng = Rng.of(save);
        ChainRules.Sync walked = ChainRules.sync(save, save.lastPlayedAt, now, rng);
        BigInteger credited = MiningRules.runSelfMining(save, walked.minedFor(), now, rng, walked.minted());
        rng.commit(save);

        if (credited.signum() > 0) {
            LedgerEntryState row =
                    LedgerRules.applyEntry(save, credited, "SELF_MINING", offlineMiningLabel(walked), now);
            // ⚠ A SOLO win names the block that carried it; a pool payout does not — the pool paid
            // out of its own balance, and a block number would put a transaction on the chain that
            // no miner ever mined. The LAST block won, not chain.height: the chain has since run on
            // past it, so the tip is somebody else's block.
            if (MiningRules.modeOf(save.rig) == MiningMode.SOLO
                    && !walked.minted().yourBlocks().isEmpty()) {
                row.blockNumber = walked.minted().yourBlocks().getLast().height();
            } else if (MiningRules.modeOf(save.rig) != MiningMode.SOLO) {
                row.counterparty = ChainExplorer.addressOf(MiningRules.poolOf(save.rig));
            }
        }
        this.sync = walked.report().withCredit(credited);
        // A new report is a new thing to announce. Set beside the assignment rather than in
        // resume(), so the flag cannot outlive the report it refers to.
        this.syncShown = false;
        return walked;
    }

    /** What the ledger row for an offline settlement says. */
    private String offlineMiningLabel(ChainRules.Sync walked) {
        int won = walked.minted().yours();
        if (won > 0) {
            return won == 1
                    ? "Solo block " + walked.minted().yourBlocks().getFirst().height() + ", found while away"
                    : won + " solo blocks found while away";
        }
        return "Mining settled for " + humanAway(walked.minedFor()) + " after logout";
    }

    /**
     * Tells a returning player what the chain did, and what their rig did before it stopped.
     *
     * <p>⚠ The spin-down cap is stated whenever it bit. A player who left for a week and was paid
     * for four hours has no way to distinguish that from a bug otherwise — which is the same reason
     * the old log said "self-mining earned nothing while away: it is online-only" in as many words.
     */
    private void logSync(ChainRules.Sync walked, Instant now) {
        if (!sync.any()) {
            return;
        }
        EventLog.info(
                save,
                "chain",
                sync.blocks() + " blocks synchronised — the chain reached height " + sync.toHeight()
                        + " while you were gone.",
                now);
        if (sync.transactionsConfirmed() > 0) {
            EventLog.info(
                    save,
                    "chain",
                    sync.transactionsConfirmed() == 1
                            ? "1 of your transactions confirmed while away."
                            : sync.transactionsConfirmed() + " of your transactions confirmed while away.",
                    now);
        }
        if (save.rig.selfMiningCycles <= 0) {
            return;
        }
        for (ChainRules.Won block : walked.minted().yourBlocks()) {
            EventLog.notice(
                    save,
                    "mining",
                    "block " + block.height() + " is yours — found after logout, "
                            + Ethecoin.format(Balance.BLOCK_SUBSIDY_WEI) + " subsidy plus "
                            + Ethecoin.format(block.feesWei()) + " in fees.",
                    now);
        }
        if (sync.capped()) {
            EventLog.info(
                    save,
                    "mining",
                    "The rig spun down " + humanAway(java.time.Duration.ofSeconds(sync.minedSeconds()))
                            + " after logout; the " + sync.uncontestedBlocks()
                            + " blocks after that were mined without it (I5).",
                    now);
        }
    }

    /**
     * Advances the game to {@code now}. Called on a timeline while the client is open.
     *
     * @return true if anything changed that the UI should re-read
     */
    public boolean tick() {
        Instant now = clock.instant();
        Duration elapsed = Duration.between(lastTick, now);
        if (elapsed.isNegative() || elapsed.isZero()) {
            return false;
        }
        boolean changed = false;

        // ⚠ The queue settles BEFORE tasks, and the order matters both ways. A download promoted to
        // the front needs its transfer commissioned in the same pass it was promoted, or the queue
        // spends a tick with nothing running; and a HELD transfer needs its clock pushed forward
        // before settleTasks looks at deadlines, or a task the player paused finishes anyway.
        changed |= io.github.stoicswe.eyeandsickle.engine.rules.DownloadQueue.settle(save, elapsed, now);
        // ⚠ The Shadow Market settles on the TICK, not when the panel is open. An order that only
        // filled while its window was on screen would make the market a thing that happens to people
        // who are watching, and a player would learn to leave the panel open — which is the opposite
        // of what a resting order is for.
        for (var fill : io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.settle(
                save, now, now.getEpochSecond())) {
            changed = true;
            EventLog.notice(
                    save,
                    "market",
                    fill.bought()
                            ? (fill.delivered()
                                    ? "bought " + fill.itemType() + " on the shadow market from "
                                            + fill.counterparty() + " for "
                                            + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(
                                                    fill.price())
                                    // ⚠ Loud, and the money is NOT returned. That is what a rating is
                                    // for: an undelivered purchase that refunded itself would make
                                    // reputation free to ignore.
                                    : fill.counterparty() + " took your "
                                            + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(
                                                    fill.price())
                                            + " and delivered nothing. That is what an unrated seller is.")
                            : "sold " + fill.itemType() + " on the shadow market to " + fill.counterparty()
                                    + " for "
                                    + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(fill.price()),
                    now);
        }
        // Tasks first: under UI-6 a finished scan releases its held cycles into RECOVERING, and a
        // short scan on a lean rig can finish and fully recover inside one tick. Settling recovery
        // first would leave those cycles a tick behind the readout that just said the scan was done.
        changed |= settleTasks(now);

        long recovered = ComputeRules.settleRecovered(save.rig, now);
        if (recovered > 0) {
            EventLog.info(save, "compute", recovered + " cycles recovered and are available again.", now);
        }
        changed |= recovered > 0;

        // The chain runs whether or not the player is mining — a block explorer that only advanced
        // while you happened to be pointed at it would be a chain with an audience of one.
        Rng miningRng = Rng.of(save);
        long heightBefore = save.chain == null ? 0L : save.chain.height;
        // The chain decides who won each block; MiningRules credits whatever was the player's.
        ChainRules.Minted minted = ChainRules.advanceNetwork(save, elapsed, now, miningRng);
        long payoutsBefore = save.rig.miningPayouts;
        // ⚠ Read the pending-payout count BEFORE running, and add whatever this tick found, because
        // settlement zeroes it. A label built from the field afterwards reads "0 shares" every time.
        int pendingBefore = save.rig.miningPendingPayouts;
        BigInteger selfYield = MiningRules.runSelfMining(save, elapsed, now, miningRng, minted);
        miningRng.commit(save);

        if (selfYield.signum() > 0) {
            int settled =
                    pendingBefore + (int) (save.rig.miningPayouts - payoutsBefore) - save.rig.miningPendingPayouts;
            LedgerEntryState row =
                    LedgerRules.applyEntry(save, selfYield, "SELF_MINING", miningLabel(Math.max(1, settled)), now);
            // ⚠ A SOLO win names the block that carried it; a pool payout does not. The pool paid
            // out of its own balance, and stamping a block number on it would put a transaction on
            // the chain that no miner ever mined.
            if (MiningRules.modeOf(save.rig) == MiningMode.SOLO && minted.yours() > 0) {
                row.blockNumber = save.chain.height;
            } else if (MiningRules.modeOf(save.rig) != MiningMode.SOLO) {
                row.counterparty = ChainExplorer.addressOf(MiningRules.poolOf(save.rig));
            }
            changed = true;
        }
        // ⚠ A solo block is logged; a pool share is not. A line every thirty seconds would bury the
        // one line that mattered, which is `alert-fatigue(7)` — a page in this game's own manual.
        // Finding a block after four hours of nothing is the entire point of the mode and is exactly
        // the event the log exists for.
        if (MiningRules.modeOf(save.rig) == MiningMode.SOLO && save.rig.miningPayouts > payoutsBefore) {
            long won = save.rig.miningPayouts - payoutsBefore;
            // Subsidy and fees named separately. They are one credit in the ledger, but they are two
            // different things — one is minted, the other was paid by the senders in the block —
            // and `proof-of-work(7)` teaches that split. A single total would hide it.
            EventLog.notice(
                    save,
                    "mining",
                    "block " + save.chain.height + " is yours — "
                            + Ethecoin.format(Balance.BLOCK_SUBSIDY_WEI.multiply(BigInteger.valueOf(won)))
                            + " subsidy plus " + Ethecoin.format(minted.yoursFeesWei())
                            + " in fees, whole and unshared.",
                    now);
        }
        changed |= save.chain != null && save.chain.height != heightBefore;

        changed |= MiningRules.accrueDeployedMiners(save, now).signum() > 0;

        save.playedSeconds += elapsed.toSeconds();
        save.lastPlayedAt = now;
        lastTick = now;
        return changed;
    }

    public void persist() {
        store.save(save);
    }

    /**
     * The last port-scan report for each machine, this session.
     *
     * <h2>⚠ Session state, not save state — and the reason is the CYCLE LOAD line</h2>
     *
     * A report is a <b>snapshot</b>: {@code cyclesUsed} was true at the instant it was taken and is
     * a guess five minutes later. Persisting one would hand a returning player a figure about a
     * machine's current load that was measured last Tuesday, presented with exactly the same
     * confidence as one taken thirty seconds ago. Everything durable a scan learned — that the
     * machine exists, its tier, its firewall — already lives on the node.
     */
    private final java.util.Map<String, io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport> lastPortScans =
            new java.util.HashMap<>();

    /** The last report for this machine, if one was taken this session. */
    public java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport> portScan(String address) {
        return java.util.Optional.ofNullable(lastPortScans.get(address));
    }

    /** Commissions a port scan. See {@code PortScanRules} for what depth costs. */
    public io.github.stoicswe.eyeandsickle.engine.net.PortScanRules.Started portScan(
            String address, io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget target) {
        return io.github.stoicswe.eyeandsickle.engine.net.PortScanRules.begin(save, address, target, clock.instant());
    }

    /** The host record behind an address, for the rules that need ground truth. */
    private java.util.Optional<io.github.stoicswe.eyeandsickle.engine.state.HostState> topologyHost(String address) {
        if (save.topology == null) {
            return java.util.Optional.empty();
        }
        return save.topology.hosts.stream()
                .filter(host -> address.equals(host.address))
                .findFirst();
    }

    // ------------------------------------------------------------------ read model

    public ComputeBudget computeBudget() {
        return ComputeRules.snapshot(save);
    }

    public Ethecoin balance() {
        return Ethecoin.ofWei(save.ethecoinWei);
    }

    public List<ItemState> itemsIn(StorageTier tier) {
        return save.items.stream().filter(i -> tier.name().equals(i.tier)).toList();
    }

    public Optional<NodeState> node(String address) {
        return save.knownNodes.stream().filter(n -> n.address.equals(address)).findFirst();
    }

    // ------------------------------------------------------------------ intents

    /**
     * Commits cycles to self-mining, the income floor.
     *
     * <p>Safe, silent, unseizable and zero-heat (Invariant I4), and online-only (I5). Allocating is
     * the one economic action in the game with no downside except the opportunity cost of the cycles,
     * which is exactly why it must never be the most profitable one.
     *
     * @return true if the rig could afford the change
     */
    public boolean allocateSelfMining(long cycles) {
        if (cycles < 0) {
            return false;
        }
        // ⚠ FROZEN while its firmware is being written. This is the other half of "the tool must be
        // stopped to flash it" — stopping it at the door and then letting the player start it again
        // two seconds later would make the requirement a formality rather than a cost. Raising the
        // allocation is refused; setting it to zero is always allowed, because a rule that traps a
        // player's cycles in a tool they cannot use is a bug wearing a rule's clothes.
        if (cycles > save.rig.selfMiningCycles
                && Catalogue.MINING_TOOL.equals(io.github.stoicswe.eyeandsickle.engine.rules.Repac.frozenTool(save))) {
            return false;
        }
        long delta = cycles - save.rig.selfMiningCycles;
        if (delta > 0 && ComputeRules.availableCycles(save.rig) < delta) {
            return false;
        }
        save.rig.selfMiningCycles = cycles;
        EventLog.info(
                save,
                "mining",
                cycles == 0
                        ? "Self-mining stopped; cycles released."
                        : "Self-mining set to " + cycles + " cycles (" + Ethecoin.format(cycles * 40L)
                                + "/hr while open).",
                clock.instant());
        return true;
    }

    /**
     * Points the rig's mining at the pool or at the whole chain.
     *
     * <h2>⚠ Switching costs nothing, forfeits nothing, and that is the mechanic</h2>
     *
     * Mining is memoryless: the work already done buys no claim on the next payout in either mode, so
     * there is nothing to lose by switching and nothing to bank by waiting. The outstanding draw is
     * kept rather than re-rolled — re-rolling would hand the player a free reroll of a wait they
     * cannot see anyway, and keeping it is also simply correct, because the remaining wait on an
     * exponential is distributed exactly like a fresh one.
     *
     * @return true if the mode changed
     */
    public boolean setMiningMode(MiningMode mode) {
        if (mode == null || MiningRules.modeOf(save.rig) == mode) {
            return false;
        }
        save.rig.miningMode = mode.name();
        Instant now = clock.instant();
        MiningSnapshot after = mining();
        EventLog.info(
                save,
                "mining",
                mode == MiningMode.SOLO
                        ? "Mining solo against difficulty "
                                + String.format(java.util.Locale.ROOT, "%.1f", after.difficulty())
                                + ". No fee, no floor: " + Ethecoin.format(after.payoutWei()) + " a block, "
                                + humanAway(java.time.Duration.ofSeconds((long) after.expectedPayoutSeconds()))
                                + " between them on average."
                        : "Mining pooled. " + Ethecoin.format(after.payoutWei()) + " a share, about one every "
                                + Math.round(Balance.POOL_SHARE_SECONDS) + "s, less a "
                                + String.format(java.util.Locale.ROOT, "%.0f%%", Balance.POOL_FEE * 100) + " fee.",
                now);
        return true;
    }

    /**
     * Joins a pool. Pooled mining only; solo has nobody to join.
     *
     * <p>⚠ Switching pools costs nothing and forfeits nothing, for the same reason switching modes
     * does — the outstanding draw survives, because the remaining wait on an exponential is
     * distributed exactly like a fresh one. Real pools have no exit fee either; the thing that makes
     * people hesitate is a minimum payout threshold holding their balance, which this game does not
     * model.
     *
     * @return true if the pool changed
     */
    public boolean setPool(String poolId) {
        if (poolId == null || !Pools.exists(poolId)) {
            return false;
        }
        MiningPool pool = Pools.byId(poolId);
        if (pool.id().equals(MiningRules.poolOf(save.rig).id())) {
            return false;
        }
        save.rig.miningPoolId = pool.id();
        Instant now = clock.instant();
        MiningSnapshot after = mining();
        EventLog.info(
                save,
                "mining",
                "joined " + pool.name() + " (" + pool.scheme() + ", " + pool.feeText() + ") — "
                        + Ethecoin.format(after.payoutWei()) + " every "
                        + humanAway(java.time.Duration.ofSeconds(Math.max(1L, (long) after.expectedPayoutSeconds())))
                        + ", " + Ethecoin.format(after.expectedWeiPerHour()) + "/hr expected.",
                now);
        return true;
    }

    /**
     * What {@code cycles} would earn per hour in the current mode and pool, in minor units.
     *
     * <p>For pricing a slider before it is committed. The rule stays here: a view that scaled the
     * committed figure itself would be the fourth copy of a balance rate, and the third one was
     * already wrong (see {@code RigStatus}).
     */
    public BigInteger miningRateFor(long cycles) {
        return MiningRules.rateFor(save.rig, save.chain, cycles);
    }

    /** This character's chain address. */
    public String chainAddress() {
        return ChainExplorer.addressOf(save);
    }

    /** The explorer's rolling window of blocks, newest first. */
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock> chainBlocks() {
        return ChainExplorer.recentBlocks(save);
    }

    /**
     * What the last load filled in. Reports zero blocks when there was nothing to catch up.
     *
     * <p>Session state rather than save state — see the field. Idempotent: reading it never consumes
     * it, so a test or a second readout can ask without changing what the player sees. The
     * {@code SYNCHRONIZING} screen uses {@link #takeChainSync()} instead.
     */
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainSync chainSync() {
        return sync;
    }

    /**
     * The same report, once — for the surface that <em>shows</em> it.
     *
     * <h2>⚠ Why showing it has to consume it</h2>
     *
     * The LEDGER window is rebuilt from scratch every time it is opened ({@code DeskManager} calls
     * the factory afresh, so a closed window keeps no state). A panel built from {@link #chainSync()}
     * therefore replayed the whole fill on every single open — the third time a player opened the
     * ledger in one sitting they watched a meter fill about a catch-up that had happened an hour ago.
     *
     * <p>A synchronisation is a <b>transition</b>, and a transition is reportable exactly once. The
     * information is not lost: {@code logSync} has already written it to the rig log — how many
     * blocks, what confirmed, where the rig spun down — and the log is where history belongs and
     * where {@code docs/design/15} §3 says a returning player should find what happened while they
     * were gone. The panel is the announcement; the log is the record.
     *
     * <p>⚠ Consumed when the panel is <b>built</b>, not when the replay finishes. A player who closes
     * the window two seconds in does not get it again — which is the deliberate cost of "once per
     * session", and the alternative is that closing and reopening fast replays it forever.
     */
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainSync takeChainSync() {
        if (syncShown) {
            return io.github.stoicswe.eyeandsickle.protocol.game.ChainSync.none(clock.instant());
        }
        syncShown = true;
        return sync;
    }

    /** Every block this character put hashrate into, newest first. */
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.BlockContribution> contributions(int limit) {
        return ChainExplorer.contributions(save, limit);
    }

    /** The player's ledger rendered as chain transactions, newest first. */
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction> chainTransactions(int limit) {
        return ChainExplorer.transactions(save, limit);
    }

    /** Every pool on the chain, for a picker. */
    public java.util.List<MiningPool> pools() {
        return Pools.all();
    }

    /**
     * The mining dashboard, as the client draws it.
     *
     * <p>⚠ Carries no progress figure, deliberately — see {@code MiningSnapshot}. Everything here is
     * either chain state or a published expectation; nothing lets the client work out how close the
     * next payout is, because nothing can.
     */
    public MiningSnapshot mining() {
        ChainState chain = save.chain;
        if (chain == null) {
            chain = ChainRules.genesis(clock.instant(), new Rng(save.rngSeed));
        }
        MiningMode mode = MiningRules.modeOf(save.rig);
        long hashrate = ChainRules.hashrate(save.rig.selfMiningCycles);
        double working = MiningRules.workingDifficulty(save.rig, chain);
        Instant last = save.rig.miningLastPayoutAt;
        return new MiningSnapshot(
                mode,
                save.rig.selfMiningCycles,
                hashrate,
                Math.round(chain.networkHashrate),
                chain.difficulty,
                working,
                chain.height,
                ChainRules.blocksUntilRetarget(chain),
                ChainRules.expectedSeconds(working, hashrate),
                last == null
                        ? -1L
                        : java.time.Duration.between(last, clock.instant()).toSeconds(),
                MiningRules.expectedWeiPerHour(save.rig, chain),
                // ⚠ Rounded in BigDecimal, never through Math.round — that takes a double, and a
                // wei payout passes a double's exact-integer range within an ordinary block.
                MiningRules.payoutWei(save.rig, chain)
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .toBigIntegerExact(),
                save.rig.miningPayouts,
                save.rig.miningWei,
                mode == MiningMode.SOLO ? 0 : MiningRules.poolOf(save.rig).feeBasisPoints(),
                last,
                mode == MiningMode.SOLO ? null : MiningRules.poolOf(save.rig),
                save.rig.miningPendingWei,
                settleIn(),
                MiningRules.poolNoiseCycles(save.rig));
    }

    /** Seconds until the pool settles, or 0 when solo or there is nothing waiting. */
    private long settleIn() {
        if (MiningRules.modeOf(save.rig) == MiningMode.SOLO
                || save.rig.miningPendingWei.signum() <= 0
                || save.rig.miningSettledAt == null) {
            return 0L;
        }
        long elapsed = java.time.Duration.between(save.rig.miningSettledAt, clock.instant())
                .toSeconds();
        return Math.max(0L, Balance.POOL_SETTLE_SECONDS - elapsed);
    }

    /**
     * The ledger line for a settlement — a block names itself, a run of shares is counted.
     *
     * <p>⚠ Read BEFORE the settlement clears the counter. `runSelfMining` zeroes
     * {@code miningPendingPayouts} on the way out, so a label built afterwards would read "0 pool
     * shares" on every row.
     */
    private String miningLabel(int settledPayouts) {
        if (MiningRules.modeOf(save.rig) == MiningMode.SOLO) {
            return settledPayouts == 1 ? "Block " + save.chain.height : settledPayouts + " blocks";
        }
        return settledPayouts == 1 ? "Pool payout, 1 share" : "Pool payout, " + settledPayouts + " shares";
    }

    /**
     * Runs a rig scan at one of the three tiers.
     *
     * <p>What the player buys with a more expensive tier is signal strength, not certainty — see
     * {@code docs/education/08-detection-and-defence.md} §3.5, which uses these exact three numbers
     * to teach the false-positive trade.
     *
     * <h2>Hold, then recover (UI-6, decided 2026-07-26)</h2>
     *
     * <p>A scan's cycles are <b>held for the scan's duration and only then start recovering</b> on
     * the Thermal Budget curve. They used to be spent immediately and recover in parallel with the
     * scan, which made {@code docs/design/04-mining.md} §3.2's published asymmetry false on a lean
     * rig: a Thorough Scan's 35 cycles were back in about four minutes, before the six-minute scan
     * it paid for had even finished. §3.2 promises the player is "effectively down 35 cycles for far
     * longer than the scan runs", and now they are, on every rig rather than only a loaded one.
     *
     * <p>⚠ <b>This is a real price rise</b> — roughly double the wall-clock cost of a Thorough Scan
     * — and {@code CLAUDE.md} is explicit that {@code 03}/{@code 04} are calibrated as a set. It was
     * taken as a decision rather than an implementation detail; see the resolution log in
     * {@code docs/design/15-open-questions.md} §3 for what was re-checked.
     *
     * @return the held allocation, or empty if the rig cannot afford the tier
     */
    public Optional<AllocationState> scan(ScanTier tier) {
        Instant now = clock.instant();
        AllocationState a =
                ComputeRules.reserve(save.rig, ComputeConsumer.ACTIVE_TOOL, "scan --" + tier.flag(), tier.cycles());
        if (a == null) {
            return Optional.empty();
        }
        // Held, not spent: settleTasks hands it to ComputeRules.beginRecovery when the scan ends.
        // Stamped so the rig monitor can draw the hold as progress the same way it draws a recovery.
        a.startedAt = now;

        // ⚠ A scan takes LONGER on an infested rig, and the penalty is baked into the deadline here
        // rather than re-derived at settlement. That is what makes it true offline, and it stops a
        // player dodging it by cracking the parasite while the audit is in flight.
        long seconds = ComputeRules.slowedSeconds(save.rig, tier.seconds());

        // ⚠ The finding is ROLLED NOW and frozen, so an audit that completes while the game is closed
        // reports and reveals exactly what it would have in session. ScanRules.roll was written for
        // this and had never been called by anything but its own tests — until this line, a scan
        // reported a hard-coded stub that did not look at save.rig.foreignMiners at all, so no audit
        // in the game could find the parasite the tutorial plants on every new rig.
        Rng rng = Rng.of(save);
        ScanRules.Finding finding = ScanRules.roll(save, tier.name(), rng);
        rng.commit(save);

        TaskState task = new TaskState(
                "scan", "scan --" + tier.flag(), a.allocationId, tier.cycles(), now, now.plusSeconds(seconds));
        task.outcome = finding.line();
        task.foundMinerIds = new java.util.ArrayList<>(finding.foundMinerIds());
        save.tasks.add(task);

        EventLog.notice(
                save,
                "scan",
                "scan --" + tier.flag() + " started: " + tier.cycles() + " cycles, ~" + seconds + "s.",
                now);
        return Optional.of(a);
    }

    // ── The breach (docs/design/05) ───────────────────────────────────────────────────────────
    //
    // Thin on purpose. The rules live in solo/breach/ and this is the facade the session port binds
    // to, in the same shape as scan() above: take the engine's clock, call the rules, let the rules
    // own every decision. Nothing here interprets the game — if a rule appears in this block, it is
    // in the wrong file.

    /** Nodes the player could attempt right now. */
    public List<BreachTarget> breachTargets() {
        return Targets.available(save);
    }

    /** The breach in progress, as the client is allowed to see it. */
    public Optional<BreachSnapshot> breachSnapshot() {
        BreachSnapshot snapshot = BreachSnapshots.of(save);
        return snapshot == null ? Optional.empty() : Optional.of(snapshot);
    }

    /** Starts an attempt. Reserves compute for its whole duration — see BreachRules. */
    public BreachResult beginBreach(String targetId) {
        Optional<BreachTarget> target = Targets.byId(save, targetId);
        if (target.isEmpty()) {
            return BreachResult.refused("no reachable node called '" + targetId + "'");
        }
        return BreachRules.begin(save, target.get(), clock.instant());
    }

    /** Spends attention on one move. */
    public BreachResult breachAction(String actionId, String argument) {
        BreachResult result = BreachRules.act(save, actionId, argument, clock.instant());
        // ⚠ The move that clears the last layer is the move that takes the machine, and until this
        // line existed nothing joined those two facts up. See settleBreachOutcomes.
        settleBreachOutcomes();
        return result;
    }

    /**
     * Turns cleared attempts into footholds and pays each host's one-time loot.
     *
     * <h2>⚠ This is the wiring that was missing, and its absence was invisible</h2>
     *
     * {@code NetRules.reconcileFootholds} was written, documented and covered by five tests, and
     * <b>every caller was a test</b>. So a player could clear every layer of a breach, be told the
     * attempt succeeded, and find the machine still reading {@code contact} on the map, still
     * refusing {@code connect}, and still holding its loot. Nothing failed, nothing logged, and the
     * unit's own suite stayed green — the defect lived entirely in the join between two correct
     * pieces, which is the one place a unit test cannot look.
     *
     * <p>⚠ <b>Safe to call as often as is convenient.</b> It is idempotent by construction rather
     * than by bookkeeping — {@code foothold} and {@code looted} are both one-way flags on the host,
     * so there is no "settled" marker to get out of step. That is what makes calling it from both
     * the load path and every breach move correct rather than merely tolerable.
     */
    public boolean settleBreachOutcomes() {
        return NetRules.reconcileFootholds(save, clock.instant());
    }

    /** Walks away: no loot, no proof-of-skill credit, attention already spent stays spent. */
    public BreachResult abortBreach() {
        return BreachRules.abort(save, clock.instant());
    }

    /** Clears a finished breach's outcome once the player has read it. */
    public boolean dismissBreach() {
        return BreachRules.dismiss(save);
    }

    /** The moves available right now, each carrying the attention it would cost. */
    public List<BreachAction> breachActions() {
        return BreachRules.actions(save);
    }

    // ── Shell sessions and the filesystem ─────────────────────────────────────────────────────
    //
    // ⚠ A session is NOT the vantage. See SessionRules: the vantage is singular and is what a sweep
    // measures from (I2); a session is a shell on a machine already held, costs compute, and buys no
    // reach. Nothing in this block reads or writes vantageAddress.

    /** Opens a shell session, or reports why not. */
    public SessionRules.Opened openSession(String address) {
        return SessionRules.open(save, address, clock.instant());
    }

    /** Closes one. Returns whether there was one to close. */
    public boolean closeSession(String address) {
        return SessionRules.close(save, address);
    }

    public List<SessionState> sessions() {
        return SessionRules.all(save);
    }

    public Optional<SessionState> session(String address) {
        return SessionRules.find(save, address);
    }

    public boolean changeDirectory(String address, String path) {
        return SessionRules.changeDirectory(save, address, path, clock.instant());
    }

    /**
     * A directory listing on a machine.
     *
     * <p>⚠ The <em>rules</em> decide what is readable, here, once — a session on a host you hold
     * reads it, and everything else sees the shape and nothing inside. A view that worked that out
     * for itself would be answering "may I read this", which is exactly the class of question
     * Invariant <b>I14</b> reserves for this side.
     */
    public List<FsEntry> list(String address, String path) {
        Instant now = clock.instant();
        if (SessionRules.isOwnRig(save, address) || address == null || address.isBlank()) {
            return VirtualFs.listRig(
                    path,
                    save.handle,
                    installed(),
                    io.github.stoicswe.eyeandsickle.engine.rules.AccessLog.size(save),
                    Recents.entries(save),
                    save.files,
                    now);
        }
        HostState host = SessionRules.host(save, address);
        if (host == null) {
            return List.of();
        }
        return VirtualFs.listHost(host, path, minerIdsOn(address), now);
    }

    /** Ids of miners THIS player has deployed on a host — what makes one visible in /etc/systemd. */
    private List<String> minerIdsOn(String address) {
        for (NodeState node : save.knownNodes) {
            if (node.address.equals(address)) {
                return node.deployedMiners.stream().map(m -> m.minerId).toList();
            }
        }
        return List.of();
    }

    /**
     * Every owned item, with the tier it sits in.
     *
     * <p>⚠ The tier travels with the item on purpose. An upgrade shown inside an application bundle
     * is a <b>view</b> onto an item that lives in a storage tier, and the tier is still what decides
     * whether a remote actor can take it ({@code docs/design/01} §6, {@code rules/AccessLog}).
     */
    private List<VirtualFs.Installed> installed() {
        return save.items.stream()
                .map(i -> new VirtualFs.Installed(i.itemType, i.displayName, i.tier, i.equipped))
                .toList();
    }

    /**
     * A file's readable contents, or empty.
     *
     * <p>⚠ Only files the rules actually model return anything. Inventing log lines for
     * {@code /var/log/syslog} would be the engine fabricating content on a surface a player uses to
     * investigate, which is the one place a plausible lie does real damage.
     */
    public List<String> read(String address, String path) {
        String p = VirtualFs.normalise(path);
        boolean own = SessionRules.isOwnRig(save, address) || address == null || address.isBlank();
        if (own && VirtualFs.ACCESS_LOG.equals(p)) {
            return remoteAccessLog();
        }
        if (io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.isSystem(p)) {
            // ⚠ A directory never "reads". It is navigated into, and a caller that asked to read one
            // has asked the wrong question — answering it produced a folder described as an ELF
            // binary in the file manager.
            if (io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.isDirectory(p, clock.instant())) {
                return List.of();
            }
            return systemFile(p, own);
        }
        return List.of();
    }

    /**
     * What this thing IS — the "Get info" answer, as opposed to what it contains.
     *
     * <h2>Why this is separate from {@link #read}</h2>
     *
     * They answer different questions and a player asks them at different moments. {@code read} is
     * "show me what is in it" and is what a double-click means. This is "what am I looking at", which
     * is what a right-click means — and it is the one that works on a <b>directory</b>, where there
     * are no contents to show but there is a great deal to say.
     *
     * <p>It is also where the teaching lives. Somebody who opens {@code /System/bin} gets a listing;
     * somebody who asks about {@code /System/bin} gets told what that directory is for and why it is
     * short. The second is the interesting one.
     */
    public List<String> info(String address, String path) {
        String p = VirtualFs.normalise(path);
        boolean own = SessionRules.isOwnRig(save, address) || address == null || address.isBlank();
        List<String> out = new java.util.ArrayList<>();

        if (io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.isSystem(p)) {
            String note = io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.note(p);
            if (!note.isBlank()) {
                out.add(note);
            }
            if (io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.MODE_RESTRICTED.contains(p)) {
                out.add("");
                out.add("Mode 0600, owner root. On a real FreeBSD machine this file holds the");
                out.add("password hashes, which is why it is the one file in /etc nobody but root");
                out.add("may read -- and why /etc/passwd sits beside it, world-readable, with an");
                out.add("asterisk where each hash would be.");
            }
            out.add("");
            out.add("The base system is read-only: every mode in /System is r-xr-xr-x, owner");
            out.add("root:wheel. You can read all of it and change none of it. Anything you install");
            out.add("goes in /System/usr/local, which is the one directory here that is yours.");
            out.add("");
            out.add("See `man hier`.");
            return List.copyOf(out);
        }

        if (Recents.dirFor(save.handle).equals(p)) {
            out.add("Places you have opened, newest first. A real desktop keeps this too --");
            out.add("~/.local/share/recently-used -- which means it is readable by anything that");
            out.add("gets onto this machine. Worth remembering before you go somewhere private.");
            return List.copyOf(out);
        }
        if (p.startsWith(VirtualFs.home(save.handle) + "/" + VirtualFs.VAULTSTORE)) {
            out.add("Your items. The three tiers are an exposure ladder, not three folders:");
            out.add("vault is never exposed, standard is exposed while you are online, and the hot");
            out.add("zone is raidable even while you are not. Capacity runs the other way.");
            return List.copyOf(out);
        }
        // ⚠ BEFORE the Applications branch below, which matches the whole tree. An upgrade inside a
        // bundle would otherwise get the generic "a bundle is a directory" note — true, and not the
        // answer to the question a player right-clicking a package is asking.
        var upgrade = upgradeAt(address, p);
        if (upgrade.isPresent()) {
            out.addAll(describe(upgrade.get(), own, address));
            return List.copyOf(out);
        }
        // ⚠ An upgrade this rig cannot identify says so, rather than falling through to the generic
        // bundle note. Some app bundles advertise upgrades for tools the solo catalogue does not
        // carry, and those packages are already duds — `install` refuses them as "not an installable
        // upgrade" AFTER the player has paid for the transfer. Saying it here turns a silent dud into
        // a legible one, and costs nothing but the line. The gap itself is content, not code.
        if (p.endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.PAYLOAD_SUFFIX)
                || p.endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.PACKAGE_SUFFIX)
                || p.endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.FIRMWARE_SUFFIX)) {
            out.add("A package for a tool this rig has no catalogue entry for. It can be taken, and");
            out.add("it cannot be installed or sold -- there is nothing here that knows what it is.");
            out.add("Not worth the transfer.");
            return List.copyOf(out);
        }
        if (p.startsWith(VirtualFs.APPLICATIONS)) {
            out.add("An application bundle is a DIRECTORY, not a file -- that is the thing worth");
            out.add("knowing about how a desktop packages a program. Contents/ holds the parts:");
            out.add("the executable, its resources, and Upgrades/, which is ours rather than a");
            out.add("real bundle's.");
            return List.copyOf(out);
        }
        if (!own) {
            out.add("On " + address + ". You are reading somebody else's machine.");
        }
        return List.copyOf(out);
    }

    /**
     * An upgrade, in words — the same text {@code stat} prints and Get Info shows.
     *
     * <p>⚠ One source, two surfaces. A {@code stat} that said less than a right-click would send
     * players to the mouse to learn things, which is the opposite of what this client's terminal is
     * for. The file manager additionally renders the same facts as a structured compare block from
     * {@link #upgradeAt}; this is the text fallback and the terminal's whole answer.
     */
    private List<String> describe(
            io.github.stoicswe.eyeandsickle.protocol.game.UpgradeOffer offer, boolean own, String address) {
        List<String> out = new java.util.ArrayList<>();
        out.add(offer.displayName() + "  " + offer.version());
        out.add("");
        out.addAll(wrap(offer.summary()));
        out.add("");
        out.add(offer.verdict());
        // ⚠ Firmware's conditions are stated on the same surface as everything else, and BEFORE the
        // transfer. The whole reason this panel exists is that a package used to cost a download to
        // identify; firmware that cost a download to discover you cannot flash would be the same
        // defect wearing a new face.
        if (offer.firmware()) {
            out.add("");
            out.add("FIRMWARE -- " + offer.flashRequirement());
        }
        out.add("");
        out.add("Gate      " + offer.gate().name().toLowerCase(java.util.Locale.ROOT));
        out.add("Size      " + offer.sizeBytes() / 1_000_000L + " MB" + "   ("
                + Balance.transferTime(offer.sizeBytes()).toSeconds() + "s to pull)");
        if (offer.equippedCycles() > 0) {
            out.add("Equipped  " + offer.equippedCycles() + " cycles held while armed");
        }
        // ⚠ The resale line is printed even when it is zero, with the reason. A missing line reads as
        // an oversight; "cannot be sold" plus why is the I2 rule teaching itself at the one moment
        // the player has a reason to care about it.
        out.add(
                offer.sellable()
                        ? "Resale    " + Ethecoin.format(offer.resaleWei()) + " for this build"
                        : "Resale    not sellable -- this tool is not gated on money, and selling one "
                                + "would be selling a way past its gate");
        if (!own) {
            out.add("");
            out.add("On " + address + ". Reading the package tells you what it is; taking it still");
            out.add("costs the transfer. A newer build is worth more and replaces an older one --");
            out.add("it is not a better tool.");
        }
        return out;
    }

    /** Breaks a catalogue summary onto terminal-width lines. */
    private static List<String> wrap(String text) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : String.valueOf(text).split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > 72) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    /**
     * Reading something out of the base system.
     *
     * <h2>⚠ Config files read; binaries do not. That is what a real machine does.</h2>
     *
     * An earlier version refused everything on the grounds that a game cannot ship a real kernel.
     * True of {@code /boot/kernel/kernel} and false of {@code /etc/rc.conf} — one is twenty-eight
     * megabytes of machine code, the other is nine lines anybody can read on their own laptop right
     * now. Refusing both taught that an operating system is a closed box, which is the opposite of
     * what this tree exists for.
     *
     * <p>Each answer also carries the manual's note on what that part of the system is <em>for</em>,
     * so opening a file is a chance to learn where you are as well as what it says.
     */
    private List<String> systemFile(String path, boolean own) {
        var note = io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.note(path);
        List<String> contents = io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.contents(path);
        List<String> out = new java.util.ArrayList<>();

        if (!contents.isEmpty()) {
            out.addAll(contents);
        } else if (io.github.stoicswe.eyeandsickle.engine.fs.SystemTree.isBinary(path, false)) {
            // What `file` would tell you, rather than the screenful of noise `cat` would.
            out.add(VirtualFs.nameOf(path) + ": ELF 64-bit LSB executable, x86-64, dynamically linked, stripped");
            out.add("");
            out.add("A binary. There is nothing here a person reads.");
        } else if (!own) {
            return List.of();
        } else {
            out.add("cat: " + VirtualFs.nameOf(path) + ": Permission denied");
            out.add("");
            out.add("Mode 0600, owner root. On a real FreeBSD machine this file holds the password");
            out.add("hashes, which is why it is the one file in /etc nobody but root may read —");
            out.add("and why /etc/passwd sits beside it, world-readable, with an asterisk where");
            out.add("each hash would be.");
        }
        if (!note.isBlank()) {
            out.add("");
            out.add("-- " + note);
            out.add("   See `man hier`.");
        }
        return List.copyOf(out);
    }

    /**
     * Starts a download from a machine this rig is connected to.
     *
     * <p>Refuses rather than throwing, matching every other rule here. The duration comes out of
     * {@code Balance.transferTime}, which is bounded by the <b>remote end's upload</b> — see
     * {@code TransferRules}.
     */
    public TransferRules.Started download(String address, FsEntry entry, String destination) {
        String where = destination == null || destination.isBlank()
                ? io.github.stoicswe.eyeandsickle.engine.rules.Repac.defaultDestination(save.handle)
                : destination;
        return TransferRules.begin(save, address, entry, where, clock.instant());
    }

    /** Installs a downloaded package. The file is consumed; the item lands in the vault. */
    public io.github.stoicswe.eyeandsickle.engine.rules.Repac.Result install(String path) {
        var result = io.github.stoicswe.eyeandsickle.engine.rules.Repac.install(save, path, clock.instant());
        if (result.ok()) {
            EventLog.notice(save, "storage", result.message(), clock.instant());
        }
        return result;
    }

    /**
     * Sells a downloaded package on the secondary market.
     *
     * <p>The credit goes through the same ledger call every other income uses — there is exactly one
     * place in this engine that moves money, and a second one would eventually disagree with the
     * balance.
     */
    public io.github.stoicswe.eyeandsickle.engine.rules.Repac.Result sell(String path) {
        var result = io.github.stoicswe.eyeandsickle.engine.rules.Repac.sell(save, path);
        if (result.ok() && result.wei().signum() > 0) {
            credit(result.wei(), "RESALE", result.message());
        }
        return result;
    }

    /**
     * Which item an upgrade package on a foreign machine installs.
     *
     * <p>Derived from the bundle the file was sitting in, which is the only honest source: the app
     * it upgrades is where it was found. A package whose bundle names no known program installs
     * nothing and is worth nothing, which is the correct outcome rather than an error.
     */
    private String upgradeTypeFor(String path) {
        if (!path.endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.PAYLOAD_SUFFIX)) {
            return "";
        }
        for (String segment : path.split("/")) {
            var app = io.github.stoicswe.eyeandsickle.engine.fs.Apps.byBundle(segment);
            if (app.isEmpty()) {
                continue;
            }
            // ⚠ Resolved to a REAL catalogue id, not to the prefix that matched. A prefix is a
            // matching rule, not an item — installing one would add an item nothing else in the
            // game has ever heard of, and selling one would price a thing with no price.
            return Catalogue.offerings().stream()
                    .filter(offering -> app.get().itemPrefixes().stream()
                            .anyMatch(prefix -> offering.id().startsWith(prefix)))
                    .map(Catalogue.Offering::id)
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    /**
     * Which build of a tool sits on a given machine.
     *
     * <h2>⚠ The vendor is not a machine, and that is why it is a separate branch</h2>
     *
     * {@code TransferRules.VENDOR} is a shopfront with no address on the map, so there is no host to
     * read a tier off. It ships {@code Balance.MARKET_UPGRADE_VERSION_MAJOR} — the middle of the
     * ladder, so a hard estate carries something the shop does not and a cheap desktop carries
     * something worse, which is the whole reason to look at what a machine has before taking it.
     *
     * <p>A host that is not in the topology at all falls back to tier 1. That is the honest reading
     * rather than a guess: nothing is known about it, so it gets the floor.
     */
    /**
     * Everything bought and not yet arrived.
     *
     * <h2>⚠ Progress is computed HERE, not in the client</h2>
     *
     * The view draws a bar; it does not own the transfer model. Sending {@code startedAt} and
     * {@code endsAt} and letting it do the arithmetic would put a second copy of that model in the
     * client, and the two would part company the first time a download was <em>held</em> — which is
     * precisely the case the readout exists for, since holding works by moving both ends of the
     * clock and only the rules know that.
     */
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.DownloadOrder> downloads() {
        var queue = io.github.stoicswe.eyeandsickle.engine.rules.DownloadQueue.orders(save);
        if (queue.isEmpty()) {
            return java.util.List.of();
        }
        Instant now = clock.instant();
        String activeId = io.github.stoicswe.eyeandsickle.engine.rules.DownloadQueue.active(save)
                .map(order -> order.orderId)
                .orElse("");
        java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.DownloadOrder> out =
                new java.util.ArrayList<>();
        for (var order : queue) {
            var task = io.github.stoicswe.eyeandsickle.engine.rules.DownloadQueue.taskFor(save, order);
            boolean active = order.orderId.equals(activeId);
            double progress = task.map(t -> t.progressAt(now)).orElse(0.0d);
            // ⚠ ZERO for anything not moving. A held download's deadline is pushed forward every
            // tick, so the literal time-until-endsAt is a real number that means nothing — it would
            // render as "4s left" on a bar that has been frozen for ten minutes.
            Duration remaining = active
                    ? task.map(t -> {
                                Duration left = Duration.between(now, t.endsAt);
                                return left.isNegative() ? Duration.ZERO : left;
                            })
                            .orElse(Duration.ZERO)
                    : Duration.ZERO;
            out.add(new io.github.stoicswe.eyeandsickle.protocol.game.DownloadOrder(
                    order.orderId,
                    order.label,
                    order.bytes,
                    progress,
                    remaining,
                    order.paused,
                    active,
                    order.isBundle(),
                    order.memberItemTypes.stream()
                            .map(id -> io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(id)
                                    .map(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::name)
                                    .orElse(id))
                            .toList()));
        }
        return out;
    }

    /**
     * The Shadow Market for one listing, at one instant.
     *
     * <p>⚠ ONE clock reading for the chart, the book, the tape and the form. Building them from
     * separate calls means separate instants, and a book quoting one price beside a candle drawing
     * another is the single most damaging thing a trading screen can do.
     */
    public io.github.stoicswe.eyeandsickle.protocol.game.ShadowSnapshot shadowMarket(
            String itemType, io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.Interval interval, int candles) {
        if (itemType == null
                || !io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.listings().contains(itemType)) {
            return io.github.stoicswe.eyeandsickle.protocol.game.ShadowSnapshot.none(String.valueOf(itemType));
        }
        Instant now = clock.instant();
        java.math.BigInteger mid = io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.midAt(save, itemType, now);
        var candleList = io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.candles(
                save, itemType, interval, candles, now);
        var book = io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.bookAt(save, itemType, now);

        double change = 0;
        if (!candleList.isEmpty()) {
            java.math.BigInteger open = candleList.getFirst().open();
            if (open.signum() > 0) {
                change = mid.subtract(open).doubleValue() / open.doubleValue() * 100.0d;
            }
        }
        return new io.github.stoicswe.eyeandsickle.protocol.game.ShadowSnapshot(
                itemType,
                io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(itemType)
                        .map(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::name)
                        .orElse(itemType),
                now,
                mid,
                change,
                candleList.stream()
                        .map(c -> new io.github.stoicswe.eyeandsickle.protocol.game.ShadowCandle(
                                c.openedAt(), c.open(), c.high(), c.low(), c.close(), c.volume()))
                        .toList(),
                book.bids().stream().map(GameEngine::level).toList(),
                book.asks().stream().map(GameEngine::level).toList(),
                io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.tape(save, itemType, 24, now).stream()
                        .map(t -> new io.github.stoicswe.eyeandsickle.protocol.game.ShadowPrint(
                                t.at(), t.price(), t.size(), t.buyerTaker(), t.handle(), false))
                        .toList(),
                shadowOrders(),
                (int) save.items.stream()
                        .filter(item -> itemType.equals(item.itemType))
                        .filter(item -> !item.equipped)
                        .count());
    }

    private static io.github.stoicswe.eyeandsickle.protocol.game.ShadowLevel level(
            io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.Level level) {
        return new io.github.stoicswe.eyeandsickle.protocol.game.ShadowLevel(
                level.price(),
                level.size(),
                level.trader().handle(),
                level.trader().standing(),
                level.trader().fillPercent(),
                false);
    }

    /** The player's resting Shadow Market orders. */
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.ShadowOrder> shadowOrders() {
        return io.github.stoicswe.eyeandsickle.engine.rules.ShadowMarket.orders(save).stream()
                .map(order -> new io.github.stoicswe.eyeandsickle.protocol.game.ShadowOrder(
                        order.orderId,
                        order.itemType,
                        io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(order.itemType)
                                .map(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::name)
                                .orElse(order.itemType),
                        order.buy,
                        order.limitPriceWei,
                        order.quantity,
                        order.placedAt,
                        order.escrowWei))
                .toList();
    }

    public io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion upgradeVersionFor(
            String itemType, String address) {
        if (itemType == null || itemType.isBlank()) {
            return io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion.UNKNOWN;
        }
        if (TransferRules.VENDOR.equals(address)) {
            return io.github.stoicswe.eyeandsickle.engine.rules.Versions.on(
                    itemType, TransferRules.VENDOR, Balance.MARKET_UPGRADE_VERSION_MAJOR);
        }
        int tier = save.topology == null
                ? 1
                : save.topology.hosts.stream()
                        .filter(host -> host.address.equals(address))
                        .mapToInt(host -> host.tier)
                        .findFirst()
                        .orElse(1);
        return io.github.stoicswe.eyeandsickle.engine.rules.Versions.on(itemType, address, tier);
    }

    /**
     * What the upgrade at {@code path} on {@code address} is, and how it compares to what you hold.
     *
     * <h2>⚠ Answers for a package you have NOT taken, which is the whole point</h2>
     *
     * This is a package's own metadata, which a real one carries so that a package manager can tell
     * you what it is about to install before installing it. Nothing secret is disclosed — the payload
     * still costs a download and the exposure that goes with it. What the player gets is the decision
     * they could not previously make: whether this transfer is worth its seconds.
     *
     * <p>⚠ Works on {@code .pkg} and {@code .upg} alike. The first is a vendor's package on somebody
     * else's machine and the second is one this rig has repacked, and a player comparing what they
     * are holding against what is on a machine wants the same answer about both.
     *
     * @return empty when the path is not an upgrade at all, or names a tool with no catalogue entry
     */
    public java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.UpgradeOffer> upgradeAt(
            String address, String path) {
        String p = VirtualFs.normalise(path);
        if (!p.endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.PAYLOAD_SUFFIX)
                && !p.endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.PACKAGE_SUFFIX)
                && !p.endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.FIRMWARE_SUFFIX)) {
            return java.util.Optional.empty();
        }
        // ⚠ A file this rig is holding states its own item type and version; one still sitting on a
        // machine has to be resolved from the bundle it is in. Deriving BOTH from the path would
        // re-derive a held package's build from wherever it currently sits, which is how a package in
        // Downloads would silently change version.
        var stored = save.files.stream().filter(file -> file.path().equals(p)).findFirst();
        String itemType =
                stored.map(file -> file.itemType).filter(id -> !id.isBlank()).orElseGet(() -> upgradeTypeFor(p));
        if (itemType.isBlank()) {
            return java.util.Optional.empty();
        }
        var offering = Catalogue.byId(itemType);
        if (offering.isEmpty()) {
            // Fails closed rather than inventing a name. A tool the catalogue has never heard of has
            // no price, no gate and no summary, and rendering blanks for all three would look like a
            // bug in the panel rather than a gap in the content.
            return java.util.Optional.empty();
        }
        var version = stored.map(
                        file -> io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion.parse(file.version))
                .filter(io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion::known)
                .orElseGet(() -> upgradeVersionFor(itemType, address));
        var standing = io.github.stoicswe.eyeandsickle.engine.rules.Versions.standing(save, itemType, version);
        boolean sellable = io.github.stoicswe.eyeandsickle.engine.rules.Repac.sellable(itemType);
        return java.util.Optional.of(new io.github.stoicswe.eyeandsickle.protocol.game.UpgradeOffer(
                itemType,
                offering.get().name(),
                offering.get().description(),
                version,
                io.github.stoicswe.eyeandsickle.engine.rules.Versions.owned(save, itemType),
                io.github.stoicswe.eyeandsickle.protocol.game.UpgradeOffer.Standing.valueOf(standing.name()),
                offering.get().gate(),
                VirtualFs.upgradeBytes(itemType),
                sellable
                        ? io.github.stoicswe.eyeandsickle.engine.rules.Repac.resaleValue(itemType, version)
                        : BigInteger.ZERO,
                sellable,
                offering.get().equippedCycles(),
                offering.get().kind(),
                offering.get().requiresSchematic(),
                // ⚠ Software reports the schematic as HELD rather than as missing. It needs none, and
                // `false` here would make `readyToFlash` refuse every ordinary upgrade in the game.
                !offering.get().firmware()
                        || (save.schematics != null
                                && save.schematics.contains(offering.get().requiresSchematic())),
                io.github.stoicswe.eyeandsickle.engine.rules.Repac.blockedBy(save, offering.get())));
    }

    /**
     * Deletes a file this rig stores.
     *
     * <p>⚠ <b>Own rig only.</b> Deleting on a machine you have broken into is a different act with
     * different consequences — it is tampering with somebody's evidence, and
     * {@code solo/rules/AccessLog} already holds the line that a remote actor blanks a line rather
     * than removing it, because a deleted row turns a legible crime into a missing file. Offering
     * remote delete here would quietly grant the thing that rule exists to refuse.
     */
    public io.github.stoicswe.eyeandsickle.engine.rules.Repac.Result delete(String address, String path) {
        if (!SessionRules.isOwnRig(save, address) && address != null && !address.isBlank()) {
            return io.github.stoicswe.eyeandsickle.engine.rules.Repac.Result.refusedPublic(
                    io.github.stoicswe.eyeandsickle.engine.rules.Repac.Refusal.NOT_INSTALLABLE,
                    "You can delete files on your own rig. On " + address + " you are a guest who "
                            + "got in through a window -- taking a copy is what you are here for.");
        }
        return io.github.stoicswe.eyeandsickle.engine.rules.Repac.delete(save, path, clock.instant());
    }

    /** Every transfer in flight, for the progress readout. */
    public List<io.github.stoicswe.eyeandsickle.engine.state.TaskState> transfers() {
        return TransferRules.inFlight(save);
    }

    /**
     * Records that the operator looked at something — what fills Recents.
     *
     * <p>⚠ Called <b>explicitly</b> by the surfaces where a player deliberately opened something,
     * never from {@link #list}. {@code list} runs on every repaint and on every parent lookup, so
     * recording there would fill Recents with directories nobody chose to visit — which is the one
     * way a recents list becomes useless, because the signal is drowned by the machinery.
     *
     * <p>Only the player's own rig has a Recents. Browsing somebody else's machine does not put
     * anything in yours, and this method quietly does nothing for a remote address rather than
     * refusing — the caller does not need to know which machines keep one.
     */
    public void noteAccess(String address, String path) {
        if (!(SessionRules.isOwnRig(save, address) || address == null || address.isBlank())) {
            return;
        }
        boolean directory = list(address, path).stream()
                        .anyMatch(e ->
                                !VirtualFs.parentOf(e.path()).equals(VirtualFs.parentOf(VirtualFs.normalise(path))))
                || VirtualFs.normalise(path).equals("/")
                || !list(address, path).isEmpty();
        Recents.record(save, path, directory, clock.instant());
    }

    /** The remote-access log as the player reads it. Empty in solo — nothing remote exists. */
    public List<String> remoteAccessLog() {
        return io.github.stoicswe.eyeandsickle.engine.rules.AccessLog.render(save);
    }

    // ── The network (docs/design/07 + the sweep model) ────────────────────────────────────────
    //
    // Thin, like the breach facade above: the rules live in solo/net/ and this is only what the
    // session port binds to. `sweep` is deliberately NOT `scan` — scan audits your own rig for
    // parasites, sweep probes a network you do not own.

    /** The network as the player knows it: vantage, discovered hosts, links. */
    public NetMap net() {
        return NetRules.view(save);
    }

    /**
     * Runs a sweep from the current vantage.
     *
     * <p>⚠ The tier buys <b>sensitivity</b>, never reach. Hop ceiling comes from
     * {@link NetRules#hopCeiling} and is raised only by the Topology Mapper schematic — Invariant
     * I2 forbids ethecoin buying a ceiling, and {@code docs/design/07} names hop range as exactly
     * that. Schematics buy reach; ethecoin buys sensitivity.
     */
    public Optional<TaskState> sweep(SweepTier tier) {
        return NetRules.beginSweep(save, tier, clock.instant());
    }

    /** Whether the player owns a sweep tier. The refusal wording belongs to the caller. */
    public boolean ownsSweep(SweepTier tier) {
        return NetRules.owns(save, tier);
    }

    /**
     * Whether this character has a generated world at all.
     *
     * <p>Exists so the session layer can tell three refusals apart that {@link #sweep} collapses into
     * one empty {@link Optional}: the tool is not owned, the rig cannot afford the cycles, or there is
     * no network to sweep. That third case used to be reported as "not enough available compute",
     * which is the wrong sentence in the worst way — it names a resource the player has plenty of and
     * sends them to fix something that is not broken. {@link #open} backfills a missing world so the
     * case should now be unreachable, and the distinction stays because "should be unreachable" is
     * not a wording a player ever wants to be on the wrong side of.
     */
    public boolean hasNetwork() {
        return save.topology != null;
    }

    /**
     * How loud the rig is right now, 0–1 — see
     * {@link io.github.stoicswe.eyeandsickle.engine.rules.NoiseRules}.
     *
     * <p>⚠ Read through the session clock. A running sweep's window is measured against it, and
     * {@code Instant.now()} here would report a test clock's sweeps as long finished.
     */
    public double noise() {
        return io.github.stoicswe.eyeandsickle.engine.rules.NoiseRules.level(save, clock.instant());
    }

    // ── The process table (docs/design/04 §3.1, the manual audit) ─────────────────────────────
    //
    // Thin like the rest of this facade. What the table contains, how a parasite hides in it and
    // what killing a row costs all live in solo/proc/; nothing below decides anything.

    /** Everything running on the rig, as rows. ⚠ Nothing in a row says which one is the parasite. */
    public List<io.github.stoicswe.eyeandsickle.protocol.game.RigProcess> processes() {
        return io.github.stoicswe.eyeandsickle.engine.proc.ProcessTable.of(save, clock.instant());
    }

    /** Stops a process the player may stop. Refuses, in words, when they may not. */
    public io.github.stoicswe.eyeandsickle.engine.proc.ProcessRules.Outcome killProcess(String processId) {
        return io.github.stoicswe.eyeandsickle.engine.proc.ProcessRules.kill(save, processId, clock.instant());
    }

    /** Restarts a daemon, taking every tool that depended on it down with it. */
    public io.github.stoicswe.eyeandsickle.engine.proc.ProcessRules.Outcome restartProcess(String processId) {
        return io.github.stoicswe.eyeandsickle.engine.proc.ProcessRules.restart(save, processId, clock.instant());
    }

    // ── Filing what has been found (the folder tree) ──────────────────────────────────────────
    //
    // Thin like the rest of this facade. The rules — and every refusal's wording — live in
    // solo/net/FolderRules; nothing below decides anything.

    /** The player's folders, parents before children, ready to indent by depth. */
    public List<io.github.stoicswe.eyeandsickle.protocol.game.NetFolder> folders() {
        return FolderRules.tree(save);
    }

    /** Discovered machines the player has not filed anywhere. */
    public List<String> unfiledNodes() {
        return FolderRules.unfiled(save);
    }

    /** Creates a folder. The {@code parentId} is {@code ""} for a top-level one. */
    public FolderRules.Result createFolder(String parentId, String name) {
        return FolderRules.create(save, parentId, name, clock.instant());
    }

    public FolderRules.Refusal renameFolder(String folderId, String name) {
        return FolderRules.rename(save, folderId, name);
    }

    public FolderRules.Refusal moveFolder(String folderId, String newParentId) {
        return FolderRules.move(save, folderId, newParentId);
    }

    /** Removes a folder, lifting whatever was inside it up a level. Never recursive. */
    public FolderRules.Refusal removeFolder(String folderId) {
        return FolderRules.remove(save, folderId);
    }

    /** Files a discovered machine under a folder, or unfiles it when {@code folderId} is blank. */
    public FolderRules.Refusal fileNode(String address, String folderId) {
        return FolderRules.file(save, address, folderId);
    }

    /** A folder by the {@code /a/b} path the player typed, or empty. Identity is the id, not this. */
    public Optional<String> folderIdAtPath(String path) {
        var folder = FolderRules.byPath(save, path);
        return folder == null ? Optional.empty() : Optional.of(folder.folderId);
    }

    /** How far the player can see. Raised only by schematic — never bought. */
    public int hopCeiling() {
        return NetRules.hopCeiling(save);
    }

    /** Moves the vantage to a host the player holds; later sweeps measure hops from there. */
    public boolean connectTo(String address) {
        return NetRules.connect(save, address, clock.instant());
    }

    /** Pulls a document off a host that carries one. */
    public Optional<NetDocument> download(String address) {
        return NetRules.download(save, address, clock.instant());
    }

    /** Everything downloaded so far. */
    public List<NetDocument> documents() {
        return NetRules.documents(save);
    }

    /**
     * Renames the operator.
     *
     * <p>⚠ <b>Solo only, structurally.</b> Online, a handle is not the player's to choose — identity
     * comes from an AT Proto DID ({@code docs/architecture/02}) and the server owns it (Invariant
     * I14). This method exists on {@code GameEngine} rather than on the {@code GameSession} port for
     * exactly that reason: putting it on the port would advertise a capability that must never work
     * online, and the honest way to make something impossible is for it to be absent.
     *
     * <p>Logged, because a name change is a real state change and the log is what a player checks
     * when something is not what they remember.
     *
     * @return the name actually taken, after trimming — never blank
     */
    public String rename(String handle) {
        String next = handle == null ? "" : handle.trim();
        if (next.isBlank()) {
            return save.handle;
        }
        String was = save.handle;
        save.handle = next;
        if (!was.equals(next)) {
            EventLog.notice(save, "identity", "Operator renamed: " + was + " -> " + next, clock.instant());
        }
        return save.handle;
    }

    /**
     * Files a completed audit into the rig's scan history.
     *
     * <h2>⚠ The duration is MEASURED, not the quoted one</h2>
     *
     * A scan is slowed by an infested rig ({@code ComputeRules.slowedSeconds}) and may finish while
     * the client is closed, so the figure a player was quoted when they pressed the button is not
     * necessarily the figure it took. Recording the quote would make the one column that could
     * expose a slowed rig agree with the button instead — and a scan taking longer than it should is
     * itself a symptom.
     *
     * <p>⚠ A CLEAN result is recorded like any other. Zero found is a real answer: it is the row
     * that gives a later finding its date, and a history of only the hits would say a rig had always
     * been compromised.
     */
    private void recordScan(TaskState task, int found) {
        ScanReportState row = new ScanReportState();
        row.tier = task.label.replace("scan --", "").trim();
        row.startedAt = task.startedAt;
        row.finishedAt = task.endsAt;
        row.seconds = task.startedAt == null
                ? 0L
                : Math.max(0L, Duration.between(task.startedAt, task.endsAt).toSeconds());
        row.cycles = task.cycles;
        row.summary = ScanRules.finding(task);
        row.found = found;
        save.scanReports.add(row);
        // ⚠ Trimmed from the front: the hundred kept are the hundred most RECENT.
        while (save.scanReports.size() > ScanReportState.LIMIT) {
            save.scanReports.removeFirst();
        }
    }

    /** Every completed audit, newest first — what the AUDIT window's history lists. */
    public List<io.github.stoicswe.eyeandsickle.protocol.game.ScanReport> scanReports() {
        List<io.github.stoicswe.eyeandsickle.protocol.game.ScanReport> out = new java.util.ArrayList<>();
        for (ScanReportState row : save.scanReports) {
            out.add(new io.github.stoicswe.eyeandsickle.protocol.game.ScanReport(
                    row.tier, row.startedAt, row.finishedAt, row.seconds, row.cycles, row.summary, row.found));
        }
        java.util.Collections.reverse(out);
        return List.copyOf(out);
    }

    /**
     * Every file an audit walks on this rig, in the order it walks them.
     *
     * <h2>⚠ Derived here rather than in the view, and STABLE</h2>
     *
     * The SCANNER panel prints the files a running scan has reached, and it repaints once a second.
     * If the order moved between repaints the lines already on screen would rewrite themselves,
     * which reads as the scan going backwards rather than forwards. A depth-first walk of a
     * generated tree is deterministic ({@code VirtualFs} seeds on the address and stores nothing),
     * so the same rig yields the same list every time it is asked.
     *
     * <p>⚠ It is a walk of the rig's <b>own</b> filesystem, which is what an audit inspects — not a
     * remote host's. A scan generates no heat precisely because it never leaves this machine
     * (Invariant <b>I9</b>), and a listing that reached outward would contradict that on screen.
     *
     * <p>Bounded, for the same reason {@code find} is: an unbounded walk of a generated tree is a
     * promise about a shape this class does not own, and the panel only needs enough lines to look
     * like work.
     */
    public List<String> auditPaths() {
        List<String> out = new java.util.ArrayList<>();
        walkOwnRig("/", out, new java.util.HashSet<>(), 600);
        return List.copyOf(out);
    }

    private void walkOwnRig(String path, List<String> out, java.util.Set<String> seen, int budget) {
        if (out.size() >= budget || !seen.add(path)) {
            return;
        }
        for (FsEntry entry : list("", path)) {
            if (out.size() >= budget) {
                return;
            }
            if (entry.directory()) {
                walkOwnRig(entry.path(), out, seen, budget);
            } else {
                out.add(entry.path());
            }
        }
    }

    /** Every task currently running, oldest first. */
    public List<TaskState> tasks() {
        return List.copyOf(save.tasks);
    }

    /**
     * Finishes any task whose end has passed.
     *
     * <p>Reports each completion to the log rather than only to whoever happens to be looking. A
     * six-minute scan that finishes while the player is reading the ledger has to leave a trace, or
     * the answer they paid 35 cycles for is one they can miss entirely — which is the same argument
     * {@code RigLogTest#offlineIncomeIsReported} makes about silent income.
     */
    private boolean settleTasks(Instant now) {
        boolean changed = false;
        for (TaskState task : List.copyOf(save.tasks)) {
            if (!task.isFinishedAt(now)) {
                continue;
            }
            save.tasks.remove(task);
            changed = true;

            // ⚠ DISPATCH ON KIND. This block used to log "scan ... finished" for EVERY task, which
            // meant a completed sweep was quietly deleted without ever running discovery — the
            // network stayed empty, the log claimed a scan had finished, and nothing anywhere said
            // otherwise. A task list with more than one kind of task in it needs a switch, and the
            // moment it grew a second kind it stopped having one.
            if (io.github.stoicswe.eyeandsickle.engine.rules.Repac.FLASH_KIND.equals(task.kind)) {
                var flashed = io.github.stoicswe.eyeandsickle.engine.rules.Repac.completeFlash(save, task, task.endsAt);
                EventLog.notice(
                        save,
                        "storage",
                        flashed.map(item -> "firmware flashed: " + item.displayName
                                        + ". The mining tool is available again.")
                                .orElse("a firmware flash ended with no image to write -- nothing changed."),
                        task.endsAt);
                continue;
            }
            if (io.github.stoicswe.eyeandsickle.engine.rules.Archives.EXTRACT_KIND.equals(task.kind)) {
                var unpacked = io.github.stoicswe.eyeandsickle.engine.rules.Archives.complete(save, task, task.endsAt);
                if (unpacked.isEmpty()) {
                    // ⚠ Says so rather than passing silently. The archive was deleted underneath the
                    // extraction, and a wait that ends with nothing on disk and nothing in the log is
                    // indistinguishable from the feature being broken.
                    EventLog.notice(
                            save,
                            "storage",
                            "an extraction ended with no archive to unpack -- nothing was written.",
                            task.endsAt);
                    continue;
                }
                EventLog.notice(
                        save,
                        "storage",
                        "extracted " + VirtualFs.nameOf(task.outcome) + ": "
                                + unpacked.stream().map(file -> file.name).collect(java.util.stream.Collectors.joining(", "))
                                + ". The archive is gone; the packages install once your payment confirms.",
                        task.endsAt);
                continue;
            }
            if (TransferRules.KIND.equals(task.kind)) {
                // ⚠ An order is forgotten when its TRANSFER lands, not when its contents install. A
                // bundle's members sit in Downloads for as long as the player leaves them there, and
                // a queue that waited for an install would never empty.
                io.github.stoicswe.eyeandsickle.engine.rules.DownloadQueue.completed(save, task);
                if (TransferRules.isArchive(task)) {
                    // ⚠ NOT repacked and NOT locked as a package — an archive is not an upgrade. It
                    // is a file with things inside it, and what those things are is recorded on the
                    // file because it is recorded nowhere else.
                    var archive = io.github.stoicswe.eyeandsickle.engine.rules.Repac.arrive(
                            save,
                            TransferRules.destinationOf(task).isBlank()
                                    ? io.github.stoicswe.eyeandsickle.engine.rules.Repac.defaultDestination(save.handle)
                                    : TransferRules.destinationOf(task),
                            VirtualFs.nameOf(TransferRules.pathOf(task)),
                            TransferRules.addressOf(task),
                            TransferRules.bytesOf(task),
                            "",
                            null,
                            task.endsAt);
                    archive.kind = "archive";
                    archive.archiveItemTypes = new java.util.ArrayList<>(TransferRules.membersOf(task));
                    archive.lockedByEntryId = TransferRules.entryIdOf(task);
                    EventLog.notice(
                            save,
                            "net",
                            archive.name + " arrived from " + TransferRules.addressOf(task) + " in "
                                    + archive.directory + " -- "
                                    + archive.archiveItemTypes.size() + " packages inside. Extract it to unpack them.",
                            task.endsAt);
                    continue;
                }
                // Arriving is the whole of it. What the file BECOMES — an item, a schematic, a
                // recovered fragment — is the receiving rule's business and is deliberately not
                // decided here; TR-2 in docs/design/15 has what is still open about that.
                String destination = TransferRules.destinationOf(task);
                String name = VirtualFs.nameOf(TransferRules.pathOf(task));
                var arrived = io.github.stoicswe.eyeandsickle.engine.rules.Repac.arrive(
                        save,
                        destination.isBlank()
                                ? io.github.stoicswe.eyeandsickle.engine.rules.Repac.defaultDestination(save.handle)
                                : destination,
                        name,
                        TransferRules.addressOf(task),
                        TransferRules.bytesOf(task),
                        // ⚠ A bought package states its own item type; a stolen one is resolved from
                        // the app bundle it was sitting in. Falling back to the path lookup for a
                        // purchase would ask "which app on the vendor's machine was this in", of a
                        // vendor that has no machine.
                        TransferRules.itemTypeOf(task).isBlank()
                                ? upgradeTypeFor(TransferRules.pathOf(task))
                                : TransferRules.itemTypeOf(task),
                        upgradeVersionFor(
                                TransferRules.itemTypeOf(task).isBlank()
                                        ? upgradeTypeFor(TransferRules.pathOf(task))
                                        : TransferRules.itemTypeOf(task),
                                TransferRules.addressOf(task)),
                        task.endsAt);
                // What releases it, carried from the task. Empty for anything not bought.
                arrived.lockedByEntryId = TransferRules.entryIdOf(task);
                EventLog.notice(
                        save,
                        "net",
                        name + " arrived from " + TransferRules.addressOf(task) + " in " + arrived.directory,
                        task.endsAt);
                // ⚠ Repac fires here rather than being a second timed task. The interesting wait —
                // the one bounded by somebody else's uplink — has already happened, and two progress
                // bars for one act is noise. It is LOGGED, though, because a package silently
                // becoming a different file is the step worth having noticed.
                // ⚠ A BOUGHT package is NOT repacked here. Repac is the step that turns somebody
                // else's `.pkg` into a `.upg` this rig can install, and for a purchase that step is
                // the payment being mined — MempoolRules.released runs it. Repacking on arrival
                // would hand over an installable upgrade before the debit had reached a block,
                // which is a purchase with no settlement.
                //
                // Said plainly in the log, because the file is on disk and refuses to install: a
                // player who is not told why concludes the download was corrupt.
                if (io.github.stoicswe.eyeandsickle.engine.rules.Repac.locked(save, arrived)) {
                    EventLog.notice(
                            save,
                            "storage",
                            name + " is a vendor package and stays one until your payment is mined. "
                                    + "`ledger` shows it pending; it becomes installable on "
                                    + "confirmation.",
                            task.endsAt);
                    continue;
                }
                io.github.stoicswe.eyeandsickle.engine.rules.Repac.repack(save, arrived, task.endsAt)
                        .ifPresent(packaged -> EventLog.notice(
                                save,
                                "storage",
                                "repac: " + name + " -> " + packaged.name
                                        + " (installable; double-click it, or sell it)",
                                task.endsAt));
                continue;
            }
            if (io.github.stoicswe.eyeandsickle.engine.net.PortScanRules.KIND.equals(task.kind)) {
                // ⚠ The held cycles are released here, exactly as a finished scan's are. A task kind
                // that forgot this would leak the reservation forever and the rig would shrink by
                // every port scan the player had ever run — with the compute readout still
                // reconciling, because the allocation is real.
                ComputeRules.beginRecovery(save.rig, task.allocationId, task.endsAt);
                Rng scanRng = Rng.of(save);
                var report =
                        io.github.stoicswe.eyeandsickle.engine.net.PortScanRules.settle(save, task, scanRng, task.endsAt);
                lastPortScans.put(report.address(), report);
                // ⚠ Folded into the machine's file BEFORE anything is logged, so the completion
                // notice and the RECON list cannot disagree about what was learned.
                io.github.stoicswe.eyeandsickle.engine.net.NodeReports.merge(save, report, task.endsAt);
                EventLog.notice(
                        save,
                        "net",
                        "port scan of " + report.address() + " finished. " + report.note() + " The report is in RECON.",
                        task.endsAt);
                // ⚠ The reprisal is rolled only when the scan was NOTICED, and it is the target's
                // turn rather than a second failure mode of the scan. See ReprisalRules.
                if (report.detected()) {
                    topologyHost(report.address())
                            .ifPresent(host -> io.github.stoicswe.eyeandsickle.engine.net.ReprisalRules.answer(
                                    save, host, scanRng, task.endsAt));
                }
                scanRng.commit(save);
                // A deep scan that got through narrows the next estimate — the one thing about a
                // port scan that is remembered. See NodeState.deepScans.
                if (!report.blocked()
                        && report.requested()
                                == io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget.deepest()) {
                    save.knownNodes.stream()
                            .filter(node -> report.address().equals(node.address))
                            .findFirst()
                            .ifPresent(node -> node.deepScans++);
                }
                continue;
            }
            if ("sweep".equals(task.kind)) {
                SweepReport report = NetRules.settleSweep(save, task, task.endsAt);
                EventLog.notice(
                        save,
                        "net",
                        task.label + " finished. " + report.found() + " of " + report.inRange()
                                + " machines in range answered."
                                + (report.note().isEmpty() ? "" : " " + report.note()),
                        task.endsAt);
                if (report.counterHacked()) {
                    // Loud, because it is the one outcome the player must not miss: something on the
                    // network noticed the sweep and pushed back.
                    EventLog.warning(save, "net", "Something answered the sweep in the other direction.", task.endsAt);
                }
            } else {
                // The audit names what it found, and naming it is what makes the cycles visible: a
                // discovered parasite's allocation rejoins ComputeRules.snapshot, so the grid stops
                // being short and starts saying "Foreign miner". Until this line runs the theft is
                // real and unattributed, which is the whole shape of docs/design/04 §3.1.
                int named = revealFound(task);
                EventLog.notice(save, "scan", task.label + " finished. " + ScanRules.finding(task), task.endsAt);
                recordScan(task, named);
                if (named > 0) {
                    EventLog.warning(
                            save,
                            "scan",
                            named + (named == 1 ? " process is" : " processes are")
                                    + " now accounted for on the rig monitor. `crack` takes the buffer; "
                                    + "cracking on your own rig costs no heat.",
                            task.endsAt);
                }
            }

            // UI-6: the held cycles only NOW start coming back, and the wait is dated from the
            // task's own end rather than from `now` — a scan that finished while the game was closed
            // must not restart its recovery clock the moment the player opens the client.
            Duration recovery = ComputeRules.beginRecovery(save.rig, task.allocationId, task.endsAt);
            if (recovery != null) {
                EventLog.info(
                        save,
                        "compute",
                        task.cycles + " cycles released; ~" + recovery.toSeconds() + "s to recover.",
                        task.endsAt);
            }
        }
        return changed;
    }

    /**
     * Marks the parasites a finished audit named, and returns how many were newly revealed.
     *
     * <h2>This is the only thing in the engine that sets {@code MinerState.discovered}</h2>
     *
     * Until it runs, an undiscovered parasite's cycles are gone from the rig and absent from the
     * published ledger — the numbers do not reconcile and nothing says why
     * ({@code docs/design/04-mining.md} §3.1). After it runs the allocation rejoins the snapshot and
     * the grid attributes it. The audit ladder in §3.2 is what a player is buying when they run a
     * scan, and this line is what they get for it.
     *
     * <p>⚠ Idempotent, and it has to be: {@code settleTasks} is reached from both {@code resume} and
     * {@code tick}, and a save whose task list was duplicated by a bad merge must still reveal each
     * parasite once. Counting only transitions from false is what makes the log line honest rather
     * than re-announcing a process the player audited last week.
     */
    private int revealFound(TaskState task) {
        if (task.foundMinerIds == null || task.foundMinerIds.isEmpty()) {
            return 0;
        }
        int revealed = 0;
        for (MinerState miner : save.rig.foreignMiners) {
            if (!miner.discovered && task.foundMinerIds.contains(miner.minerId)) {
                miner.discovered = true;
                revealed++;
            }
        }
        return revealed;
    }

    /** Arms a defence, holding its compute until disarmed. Never generates heat (Invariant I9). */
    public Optional<DefenseState> arm(String kind, int tier, long cycles) {
        AllocationState a = ComputeRules.reserve(save.rig, ComputeConsumer.DEFENSIVE_ARRAY, kind, cycles);
        if (a == null) {
            return Optional.empty();
        }
        DefenseState d = new DefenseState();
        d.kind = kind;
        d.tier = tier;
        d.reservedCycles = cycles;
        d.armedAt = clock.instant();
        save.defenses.add(d);
        EventLog.notice(
                save, "defense", kind + " armed; " + cycles + " cycles reserved while it runs.", clock.instant());
        return Optional.of(d);
    }

    /** Sweeps every deployed miner's buffer into the balance. */
    public BigInteger collect() {
        BigInteger collected = MiningRules.collectAll(save, clock.instant());
        if (collected.signum() > 0) {
            EventLog.info(
                    save,
                    "mining",
                    "Collected " + Ethecoin.format(collected) + " from deployed miners.",
                    clock.instant());
        }
        return collected;
    }

    /** Moves an item between storage tiers — the risk change is the point ({@code design/01} §6). */
    public boolean moveItem(String itemId, StorageTier to) {
        for (ItemState item : save.items) {
            if (item.itemId.equals(itemId)) {
                String from = item.tier;
                item.tier = to.name();
                // Moving into an exposed tier is a risk change, and a risk change the player made
                // deliberately is exactly the kind of thing they will want to find again later.
                boolean riskier = to == StorageTier.HIGH_HACKABLE_ZONE
                        || (to == StorageTier.STANDARD_STORAGE && "VAULT".equals(from));
                EventLog.add(
                        save,
                        riskier ? 4 : 6,
                        "storage",
                        item.displayName + " moved to " + to.name().toLowerCase(java.util.Locale.ROOT)
                                + (riskier ? " — now more exposed." : "."),
                        clock.instant());
                return true;
            }
        }
        return false;
    }

    /** Spends ethecoin at the standard fee. Returns false when the player cannot afford it. */
    public boolean debit(BigInteger wei, String type, String description) {
        return debit(wei, type, description, FeeTier.STANDARD, "");
    }

    /**
     * Spends ethecoin and broadcasts the transaction, at the chosen fee.
     *
     * <h2>⚠ The balance moves now; the chain record confirms later</h2>
     *
     * The debit is immediate — the same instant a real wallet shows a send and deducts it from your
     * spendable balance. What waits is the <em>confirmation</em>: a miner has to pack the transaction
     * into a block, and the fee is a bid for one of a block's fixed number of slots.
     *
     * <h2>⚠ AMENDED 2026-07-29 — the goods now wait for the block</h2>
     *
     * This comment used to say the split was "the one place this simulation declines to be faithful",
     * because a purchase handed over the item in the same call that took the money. The argument was
     * that withholding goods for fourteen minutes would make buying a consumable mid-breach
     * impossible.
     *
     * <p>It was reversed on explicit direction, and the reversal is narrower than that argument
     * feared: a bought upgrade <b>downloads immediately</b> and lands in {@code ~/Downloads} as a
     * vendor {@code .pkg}, and what waits is only the step that turns it into something installable.
     * The player has the bytes; the vendor has not released the licence. That is what confirmation
     * means, and it is the first thing in this game that gives a {@link FeeTier} a mechanical
     * consequence rather than a cosmetic one — until now a fee bought nothing but how soon a row
     * stopped printing "—" in the ledger.
     *
     * <p>⚠ The mid-breach objection stands and is unresolved for <b>consumables</b>: the catalogue
     * currently has none whose value depends on being bought during a breach, so nothing is broken
     * today, and the day one is added it needs an answer. Logged in
     * {@code docs/design/15-open-questions.md}. See {@code docs/design/04-mining.md} §1.3e.
     *
     * <p>⚠ <b>The fee is charged on top and is also a debit</b>, so a player who cannot afford
     * {@code amount + fee} cannot send. Charging the fee silently out of the amount would make the
     * recipient short and the arithmetic in the ledger wrong.
     *
     * @param tier how much of a hurry it is in
     * @param counterparty the other end, as an address; empty derives one from the type
     */
    public boolean debit(BigInteger wei, String type, String description, FeeTier tier, String counterparty) {
        return spend(wei, type, description, tier, counterparty).isPresent();
    }

    /**
     * The same spend, handing back the ledger row it broadcast.
     *
     * <h2>⚠ This exists because "the last ledger row" is the WRONG row</h2>
     *
     * {@link #debit} writes <b>two</b> entries: the spend itself, which goes into the mempool, and a
     * separate {@code TX_FEE} row, which does not — a fee folded into the amount would be a charge
     * {@code ledger(1)} could not explain, so it is named on its own line. A caller that needed the
     * transaction and reached for the end of the list therefore got the fee, which is never submitted
     * and so never gets a block number.
     *
     * <p>That is not a cosmetic mix-up. A bought package waits on its entry's confirmation
     * ({@code Repac.locked}), so pointing it at the fee row would hold it <b>forever</b>, with the
     * money gone and no surface anywhere able to say why. Caught by {@code PurchaseFlowTest} rather
     * than by review, which is the argument for the test walking the whole journey instead of
     * stopping at "the purchase succeeded".
     *
     * @return the broadcast entry, or empty when the player cannot afford {@code amount + fee}
     */
    public Optional<LedgerEntryState> spend(
            BigInteger wei, String type, String description, FeeTier tier, String counterparty) {
        BigInteger fee = Balance.feeFor(tier);
        if (!LedgerRules.canDebit(save, wei.add(fee))) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        LedgerEntryState entry = LedgerRules.applyEntry(save, wei.negate(), type, description, now);
        if (save.chain != null) {
            MempoolRules.submit(save, entry, tier, counterparty, true, now);
            if (fee.signum() > 0) {
                // Its own row, named. A fee folded into the amount would be a charge the ledger could
                // not explain, and ledger(1) exists to explain every movement.
                LedgerRules.apply(
                        save,
                        fee.negate(),
                        "TX_FEE",
                        "Transaction fee (" + tier.label().toLowerCase(java.util.Locale.ROOT) + ")",
                        now);
            }
        }
        return Optional.of(entry);
    }

    /** What a spend at this tier would cost in fees, for a dry run. */
    public BigInteger feeFor(FeeTier tier) {
        return Balance.feeFor(tier);
    }

    /** The mempool: what is waiting, and what the next blocks would hold. */
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool mempool() {
        return ChainExplorer.mempool(save, clock.instant());
    }

    /**
     * How many blocks the chain has produced.
     *
     * <p>Exists so a caller can ask the cheap question without building a whole
     * {@code MiningSnapshot}, which computes difficulty, expected yield and a payout to answer it.
     * The client's heartbeat asks once a second.
     */
    public long chainHeight() {
        return save.chain == null ? 0L : save.chain.height;
    }

    /** One block with every transaction in it, for the detail view. Any height renders. */
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock chainBlock(long height) {
        if (save.chain == null || height < 0 || height > save.chain.height) {
            return null;
        }
        return ChainExplorer.blockWithBody(save, height);
    }

    public void credit(BigInteger wei, String type, String description) {
        LedgerRules.apply(save, wei, type, description, clock.instant());
    }

    private static String humanAway(java.time.Duration away) {
        long days = away.toDays();
        if (days >= 1) {
            return days + (days == 1 ? " day" : " days");
        }
        long hours = away.toHours();
        if (hours >= 1) {
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return Math.max(1, away.toMinutes()) + " minutes";
    }

    /** Everything in the rig log, oldest first. */
    public java.util.List<io.github.stoicswe.eyeandsickle.engine.state.RigEvent> log() {
        return java.util.List.copyOf(save.log);
    }

    /** The three scan tiers and their published costs. */
    public enum ScanTier {
        QUICK(Balance.SCAN_QUICK_CYCLES, Balance.SCAN_QUICK_SECONDS, "quick"),
        FULL(Balance.SCAN_FULL_CYCLES, Balance.SCAN_FULL_SECONDS, "full"),
        THOROUGH(Balance.SCAN_THOROUGH_CYCLES, Balance.SCAN_THOROUGH_SECONDS, "thorough");

        private final long cycles;
        private final long seconds;
        private final String flag;

        ScanTier(long cycles, long seconds, String flag) {
            this.cycles = cycles;
            this.seconds = seconds;
            this.flag = flag;
        }

        public long cycles() {
            return cycles;
        }

        public long seconds() {
            return seconds;
        }

        public String flag() {
            return flag;
        }
    }
}
