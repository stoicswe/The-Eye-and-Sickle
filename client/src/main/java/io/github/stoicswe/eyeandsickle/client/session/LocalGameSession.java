package io.github.stoicswe.eyeandsickle.client.session;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.state.DefenseState;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.LedgerEntryState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The offline session: a {@link SoloGame} behind the {@link GameSession} port.
 *
 * <h2>What this class is and is not</h2>
 *
 * It is an adapter. Every rule lives in the {@code solo} module; this translates that module's
 * mutable save objects into the immutable view types the interface renders, and translates refusals
 * into exit statuses. When a method here contains a decision rather than a translation, that decision
 * has escaped its module and should be moved.
 *
 * <p>The one thing it adds is the {@link #onChange} fan-out, because notification is a client concern:
 * the rules engine has no opinion about when a window should redraw.
 */
public final class LocalGameSession implements GameSession {

    private final SoloGame game;
    private final List<Consumer<GameSession>> listeners = new CopyOnWriteArrayList<>();

    public LocalGameSession(SoloGame game) {
        this.game = game;
    }

    public SoloGame game() {
        return game;
    }

    @Override
    public SessionMode mode() {
        return SessionMode.SOLO;
    }

    @Override
    public String handle() {
        return game.state().handle;
    }

    @Override
    public ComputeBudget computeBudget() {
        return game.computeBudget();
    }

    @Override
    public Ethecoin balance() {
        return game.balance();
    }

    @Override
    public int personalHeat() {
        return game.state().personalHeat;
    }

    @Override
    public List<InventoryItem> items(StorageTier tier) {
        List<InventoryItem> out = new ArrayList<>();
        for (ItemState i : game.state().items) {
            if (tier == null || tier.name().equals(i.tier)) {
                out.add(new InventoryItem(
                        i.itemId,
                        i.displayName,
                        i.itemType,
                        StorageTier.valueOf(i.tier),
                        i.origin,
                        i.equipped,
                        i.equippedCycles,
                        // Always false in solo. An item minted on the player's own disk has nobody to
                        // prove anything to, and a chain signed by a key on the same disk would prove
                        // only that the disk agreed with itself. `verify` says so rather than
                        // manufacturing an artefact that looks checkable.
                        false));
            }
        }
        return out;
    }

    @Override
    public List<LedgerRow> ledger(int limit) {
        List<LedgerEntryState> rows = game.state().ledger;
        int from = Math.max(0, rows.size() - Math.max(0, limit));
        List<LedgerRow> out = new ArrayList<>();
        for (LedgerEntryState e : rows.subList(from, rows.size())) {
            out.add(new LedgerRow(e.entryId, e.at, e.deltaMinorUnits, e.balanceAfterMinorUnits, e.type, e.description));
        }
        out.sort((a, b) -> b.at().compareTo(a.at()));
        return out;
    }

    @Override
    public List<KnownNode> knownNodes() {
        List<KnownNode> out = new ArrayList<>();
        for (NodeState n : game.state().knownNodes) {
            out.add(new KnownNode(n.address, n.label, n.reconLevel, n.tier, n.deployedMiners.size(), n.hostsForeignMiner));
        }
        return out;
    }

    @Override
    public List<ArmedDefense> defenses() {
        List<ArmedDefense> out = new ArrayList<>();
        for (DefenseState d : game.state().defenses) {
            out.add(new ArmedDefense(d.kind, d.tier, d.reservedCycles, d.triggered));
        }
        return out;
    }

    @Override
    public List<LogLine> log(int minSeverity, int limit) {
        List<LogLine> out = new ArrayList<>();
        for (var e : game.state().log) {
            if (e.severity <= minSeverity) {
                out.add(new LogLine(e.at, e.severity, e.facility, e.message, e.keyword(), e.glyph()));
            }
        }
        int from = Math.max(0, out.size() - Math.max(1, limit));
        return List.copyOf(out.subList(from, out.size()));
    }

    @Override
    public MiningSummary mining() {
        long buffered = 0;
        long cap = 0;
        int miners = 0;
        for (NodeState node : game.state().knownNodes) {
            for (var miner : node.deployedMiners) {
                buffered += miner.bufferedMinorUnits;
                cap += io.github.stoicswe.eyeandsickle.solo.rules.MiningRules.bufferCap(miner);
                miners++;
            }
        }
        return new MiningSummary(game.state().rig.selfMiningCycles, buffered, cap, miners);
    }

    @Override
    public io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot miningChain() {
        return game.mining();
    }

    /**
     * The rig's current work, newest last.
     *
     * <p>Three sources, one shape. Ordered so the thing with a real deadline the player is waiting
     * on — a scan — sits above the background heat the rig is shedding, because that is the order
     * the questions get asked in.
     */
    @Override
    public java.util.List<RunningTask> tasks() {
        java.util.List<RunningTask> out = new java.util.ArrayList<>();
        // The engine's clock, so progress and countdowns agree with the rules that will complete
        // the task. See RunningTask#progress.
        java.time.Instant asOf = game.now();

        for (var task : game.tasks()) {
            out.add(new RunningTask(
                    task.taskId,
                    task.kind,
                    task.label,
                    "signal strength, not certainty",
                    task.startedAt,
                    task.endsAt,
                    task.cycles,
                    asOf));
        }

        for (var allocation : game.state().rig.allocations) {
            if (!"RECOVERING".equals(allocation.state) || allocation.recoversAt == null) {
                continue;
            }
            // Skip the allocation a running scan is already represented by — otherwise a Thorough
            // Scan shows up twice, once as itself and once as the cycles paying for it, and the
            // player reasonably concludes the rig is doing two things.
            if (game.tasks().stream().anyMatch(t -> t.allocationId.equals(allocation.allocationId))) {
                continue;
            }
            out.add(new RunningTask(
                    allocation.allocationId,
                    "compute",
                    "thermal recovery",
                    allocation.label.isBlank() ? "cycles returning" : "from " + allocation.label,
                    allocation.startedAt,
                    allocation.recoversAt,
                    allocation.cycles,
                    asOf));
        }

        return java.util.List.copyOf(out);
    }

    @Override
    public java.time.Instant now() {
        return game.now();
    }

    @Override
    public RigCapacity capacity() {
        var rig = game.state().rig;
        return new RigCapacity(rig.bandwidth, rig.memoryBuffer, rig.thermalBudget);
    }

    @Override
    public boolean connected() {
        // There is nothing to disconnect from. Reporting true is not optimism, it is accurate: a
        // solo session's authority is in this process.
        return true;
    }

    // ------------------------------------------------------------------ intents

    /**
     * Abandons a live breach. Silent when there was nothing to abandon.
     *
     * <p>⚠ Deliberately NOT routed through {@code announce}. Closing a window is not a request that
     * was refused — it is the player doing something perfectly ordinary — so toasting "nothing to
     * abandon" every time they close an idle breach window would be the client complaining about its
     * own bookkeeping. The rules log the abandonment itself when there is one.
     */
    @Override
    public Outcome abandonBreach() {
        return game.abandonBreach() ? changed(Outcome.ok()) : Outcome.ok();
    }

    @Override
    public Outcome refuse(String facility, String why) {
        return announce(facility, Outcome.refused(why));
    }

    /**
     * Writes a refusal to the rig's log, so the notification system carries it.
     *
     * <h2>Why the panels stopped printing these inline</h2>
     *
     * Every tool window used to keep a strip at the top for the last refusal. Three problems with
     * that, and the third is the one that matters. It duplicated a surface the client already has;
     * it put the message somewhere the player might not be looking, because the strip is at the top
     * of a panel and the control they pressed may be at the bottom; and <b>a refusal was the one
     * class of message that never reached the journal</b>. A player could be told "not enough
     * cycles", look away, and have no way to find out what they had been told.
     *
     * <p>Logging it fixes all three. {@code Notifications} is "the log, filtered" by design — it
     * refuses to carry anything the rig did not emit, precisely so the toast and the journal cannot
     * disagree — so a refusal that is in the log is a refusal that toasts, and one that toasts is one
     * the player can go back and read. See {@code EventLog.error} for the severity choice and the
     * repeat suppression.
     *
     * <h2>⚠ A usage error is deliberately NOT announced</h2>
     *
     * {@code EX_USAGE} means the command was malformed — a mistyped flag. That only reaches this
     * class from the terminal, which already prints the answer on the line below the mistake, and
     * toasting it as well would be the client telling a player twice that they typed something
     * wrong. Every other non-zero status is a decision the rules made about a request that was
     * well-formed, which is exactly what a player needs surfaced.
     */
    private Outcome announce(String facility, Outcome outcome) {
        if (outcome.succeeded() || outcome.status() == Outcome.USAGE || outcome.message().isBlank()) {
            return outcome;
        }
        io.github.stoicswe.eyeandsickle.solo.rules.EventLog.error(
                game.state(), facility, outcome.message(), game.now());
        // The log changed, so the toast poller and every log window have something to pick up. Not
        // routed through `changed()` above it, because that one is about GAME state changing and a
        // refusal is by definition the game not changing.
        fire();
        return outcome;
    }

    /**
     * The one refusal a rig gives when it has not got the capacity — and the one hint a player gets
     * that something is eating it.
     *
     * <h2>Why every caller says this in the same words</h2>
     *
     * A parasite the player has not audited is invisible on the readout by design
     * ({@code ComputeRules.snapshot}): the cycles are gone and nothing attributes them. That is the
     * right amount of silence right up until the moment it stops a command, and then silence would be
     * indistinguishable from a bug — the player asks for a nine-cycle sweep, the rig says no, and
     * every number they can see says they could afford it. So this message exists, it fires on every
     * capacity refusal in the port, and it carries the three figures that make the discrepancy
     * derivable: what was needed, what is free, and <b>what the rig's ceiling is</b>.
     *
     * <p>⚠ It must never mention a parasite, a rogue process, or an audit. It reports a shortfall,
     * which is the only thing the rig honestly knows. The player who compares "12 free of 100"
     * against a grid showing 75 committed has found the gap themselves, which is
     * {@code docs/design/04-mining.md} §3.1 working exactly as written; a message that named the cause
     * would be the refusal doing the audit's job for free.
     */
    private Outcome notEnoughCycles(long needed) {
        var budget = computeBudget();
        return Outcome.refused("command could not be executed: not enough cycles to compute — "
                + needed + " needed, " + budget.available().cycles() + " free of "
                + budget.total().cycles());
    }

    private Outcome allocateSelfMiningIntent(long cycles) {
        if (cycles < 0) {
            return Outcome.usage("cycles must not be negative");
        }
        if (!game.allocateSelfMining(cycles)) {
            return notEnoughCycles(cycles);
        }
        return changed(Outcome.ok("self-mining set to " + cycles + " cycles"));
    }

    private Outcome scanIntent(String tier) {
        SoloGame.ScanTier t;
        try {
            t = SoloGame.ScanTier.valueOf(tier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Outcome.usage("unknown scan tier '" + tier + "' — expected quick, full or thorough");
        }
        return game.scan(t)
                .map(a -> changed(Outcome.ok("scan --" + t.flag() + " started; " + t.cycles() + " cycles committed")))
                .orElseGet(() -> notEnoughCycles(t.cycles()));
    }


    // ── every refusal is announced ────────────────────────────────────────────────────────────
    //
    // Each intent below is a wrapper around the private one that does the work, and the wrapper's
    // only job is to hand a failure to `announce`. Twenty two-line wrappers is more code than a
    // single interceptor would be and Java has no interceptor — but the alternative was logging at
    // each of thirty-five return statements, which is thirty-five chances to forget one.

    @Override
    public Outcome allocateSelfMining(long cycles) {
        return announce("mining", allocateSelfMiningIntent(cycles));
    }

    @Override
    public Outcome setMiningMode(io.github.stoicswe.eyeandsickle.protocol.game.MiningMode mode) {
        return announce("mining", setMiningModeIntent(mode));
    }

    @Override
    public long miningRateFor(long cycles) {
        return game.miningRateFor(cycles);
    }

    @Override
    public String chainAddress() {
        return game.chainAddress();
    }

    @Override
    public Outcome send(String toAddress, long minorUnits,
            io.github.stoicswe.eyeandsickle.protocol.game.FeeTier tier) {
        return announce("chain", sendIntent(toAddress, minorUnits, tier));
    }

    private Outcome sendIntent(String toAddress, long minorUnits,
            io.github.stoicswe.eyeandsickle.protocol.game.FeeTier tier) {
        if (toAddress == null || !toAddress.matches("0x[0-9a-fA-F]{40}")) {
            return Outcome.usage("send <0x…40 hex> <amount> — an address is 20 bytes of hex");
        }
        if (minorUnits <= 0) {
            return Outcome.usage("send: the amount must be positive");
        }
        long fee = game.feeFor(tier);
        if (!game.debit(minorUnits, "TRANSFER", "Sent to " + toAddress, tier, toAddress)) {
            return Outcome.refused("not enough ethecoin — " + money(minorUnits + fee)
                    + " needed including the " + money(fee) + " fee, "
                    + money(game.balance().minorUnits()) + " held");
        }
        return Outcome.ok("broadcast " + money(minorUnits) + " to " + toAddress
                + " with a " + money(fee) + " fee — waiting for a miner");
    }

    @Override
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool mempool() {
        return game.mempool();
    }

    @Override
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock chainBlock(long height) {
        return game.chainBlock(height);
    }

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock> chainBlocks() {
        return game.chainBlocks();
    }

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction> chainTransactions(
            int limit) {
        return game.chainTransactions(limit);
    }

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.MiningPool> pools() {
        return game.pools();
    }

    @Override
    public Outcome setMiningPool(String poolId) {
        return announce("mining", setMiningPoolIntent(poolId));
    }

    private Outcome setMiningPoolIntent(String poolId) {
        if (poolId == null || poolId.isBlank()) {
            return Outcome.usage("mine --pool=<id>; `pools` lists them");
        }
        if (!io.github.stoicswe.eyeandsickle.solo.Pools.exists(poolId)) {
            return Outcome.refused("no pool called '" + poolId + "'. `pools` lists them.");
        }
        if (!game.setPool(poolId)) {
            return Outcome.ok("already mining with " + game.mining().pool().name());
        }
        var after = game.mining();
        // Both numbers, always. The fee is what changed the income and the interval is what changed
        // the feel, and a player told only one of them will conclude the other did not move.
        return Outcome.ok("joined " + after.pool().name() + " — "
                + String.format(java.util.Locale.ROOT, "%.2f EC", after.expectedMinorUnitsPerHour() / 100.0d)
                + "/hr expected, paid about every "
                + Math.round(after.expectedPayoutSeconds()) + "s");
    }

    private Outcome setMiningModeIntent(io.github.stoicswe.eyeandsickle.protocol.game.MiningMode mode) {
        if (mode == null) {
            return Outcome.usage("mine: --pool or --solo");
        }
        if (!game.setMiningMode(mode)) {
            return Outcome.ok("already mining " + mode.name().toLowerCase(java.util.Locale.ROOT));
        }
        var after = game.mining();
        return Outcome.ok(mode == io.github.stoicswe.eyeandsickle.protocol.game.MiningMode.SOLO
                ? "mining solo: the whole block subsidy or nothing, about one block every "
                        + Math.round(after.expectedPayoutSeconds() / 60) + " minutes on average"
                : "mining pooled: a steady share every "
                        + Math.round(io.github.stoicswe.eyeandsickle.solo.Balance.POOL_SHARE_SECONDS)
                        + "s, less the pool's fee");
    }

    @Override
    public Outcome scan(String tier) {
        return announce("scan", scanIntent(tier));
    }

    @Override
    public Outcome beginBreach(String targetId) {
        return announce("breach", beginBreachIntent(targetId));
    }

    @Override
    public Outcome breachAction(String actionId, String argument) {
        return announce("breach", breachActionIntent(actionId, argument));
    }

    @Override
    public Outcome abortBreach() {
        return announce("breach", abortBreachIntent());
    }

    @Override
    public Outcome dismissBreach() {
        return announce("breach", dismissBreachIntent());
    }

    @Override
    public Outcome sweep(String flag) {
        return announce("net", sweepIntent(flag));
    }

    @Override
    public Outcome killProcess(String processId) {
        return announce("rig", killProcessIntent(processId));
    }

    @Override
    public Outcome restartProcess(String processId) {
        return announce("rig", restartProcessIntent(processId));
    }

    @Override
    public Outcome createFolder(String parentId, String name) {
        return announce("net", createFolderIntent(parentId, name));
    }

    @Override
    public Outcome renameFolder(String folderId, String name) {
        return announce("net", renameFolderIntent(folderId, name));
    }

    @Override
    public Outcome moveFolder(String folderId, String newParentId) {
        return announce("net", moveFolderIntent(folderId, newParentId));
    }

    @Override
    public Outcome removeFolder(String folderId) {
        return announce("net", removeFolderIntent(folderId));
    }

    @Override
    public Outcome fileNode(String address, String folderId) {
        return announce("net", fileNodeIntent(address, folderId));
    }

    @Override
    public Outcome connectTo(String address) {
        return announce("net", connectToIntent(address));
    }

    @Override
    public Outcome download(String address) {
        return announce("net", downloadIntent(address));
    }

    @Override
    public Outcome collect() {
        return announce("mining", collectIntent());
    }

    @Override
    public Outcome moveItem(String itemId, StorageTier to) {
        return announce("rig", moveItemIntent(itemId, to));
    }

    @Override
    public Outcome arm(String kind, int tier) {
        return announce("defense", armIntent(kind, tier));
    }

    @Override
    public Outcome purchase(String offeringId) {
        return announce("rig", purchaseIntent(offeringId));
    }

    // ── The breach ────────────────────────────────────────────────────────────────────────────
    //
    // Every method here is a translation and nothing more: the engine returns a BreachResult, and
    // this converts it into the port's Outcome vocabulary. The engine's own types stop at this
    // class — the view never sees a BreachResult, only a protocol snapshot and an Outcome, which is
    // what lets the identical view work against a home server.

    @Override
    public List<io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget> breachTargets() {
        return game.breachTargets();
    }

    @Override
    public java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot> breach() {
        return game.breachSnapshot();
    }

    private Outcome beginBreachIntent(String targetId) {
        return translate(game.beginBreach(targetId));
    }

    private Outcome breachActionIntent(String actionId, String argument) {
        return translate(game.breachAction(actionId, argument));
    }

    private Outcome abortBreachIntent() {
        return translate(game.abortBreach());
    }

    private Outcome dismissBreachIntent() {
        return game.dismissBreach()
                ? changed(Outcome.ok("outcome cleared"))
                : Outcome.ok("nothing to clear");
    }

    /**
     * BreachResult → Outcome.
     *
     * <p>The three-way split is deliberate and matches the rest of the client's vocabulary: a
     * <em>gated</em> result is {@code 77 EX_NOPERM} with the requirement in words, never a refusal
     * with a price, so a gate reads as "not yet, and here is why" rather than as an obstruction.
     * A refusal is a rule declining; only an applied move counts as a state change worth telling
     * the views about.
     */
    private Outcome translate(io.github.stoicswe.eyeandsickle.solo.breach.BreachResult result) {
        if (result.gated()) {
            return Outcome.gated(result.message());
        }
        if (!result.applied()) {
            return Outcome.refused(result.message());
        }
        return changed(Outcome.ok(result.message()));
    }

    // ── The network ───────────────────────────────────────────────────────────────────────────

    @Override
    public io.github.stoicswe.eyeandsickle.protocol.game.NetMap net() {
        return game.net();
    }

    private Outcome sweepIntent(String flag) {
        var tier = io.github.stoicswe.eyeandsickle.solo.net.SweepTier.byFlag(flag == null ? "" : flag);
        if (tier.isEmpty()) {
            return Outcome.usage("unknown sweep tier '" + flag + "' — expected --wide or --deep, or no flag");
        }
        if (!game.ownsSweep(tier.get())) {
            // 77 EX_NOPERM with the requirement in words, never a refusal with a price: a gate
            // must read as "not yet, and here is why" rather than as an obstruction.
            return Outcome.gated("requires " + tier.get().itemId());
        }
        // ⚠ Checked before the compute refusal, and the order is the fix rather than a tidy-up. A
        // character created before the world generator existed has a null topology, so beginSweep
        // returns empty for a reason that has nothing to do with cycles — and this method used to
        // report every one of those as "not enough available compute", sending the player to free up
        // capacity they already had while the real answer was that they had no network at all.
        // SoloGame.open backfills the world now, so this should be unreachable; it stays because a
        // refusal that names the wrong resource is the most expensive kind of wrong.
        if (!game.hasNetwork()) {
            return Outcome.refused(
                    "this character has no network yet — reopen the save to bring the interface up");
        }
        return game.sweep(tier.get())
                .map(t -> changed(Outcome.ok("sweep started from " + game.net().vantageAddress()
                        + " — " + tier.get().cycles() + " cycles held, and loud until it ends")))
                .orElseGet(() -> notEnoughCycles(tier.get().cycles()));
    }

    @Override
    public double noise() {
        return game.noise();
    }

    @Override
    public List<io.github.stoicswe.eyeandsickle.protocol.game.RigProcess> processes() {
        return game.processes();
    }

    private Outcome killProcessIntent(String processId) {
        return apply(game.killProcess(processId));
    }

    private Outcome restartProcessIntent(String processId) {
        return apply(game.restartProcess(processId));
    }

    private Outcome apply(io.github.stoicswe.eyeandsickle.solo.proc.ProcessRules.Outcome outcome) {
        return outcome.refused() ? Outcome.refused(outcome.why()) : changed(Outcome.ok());
    }

    // ── Filing what has been found ────────────────────────────────────────────────────────────
    //
    // Every refusal below is the rules' own sentence, passed through unedited. The view and the
    // shell both print it, so there is exactly one wording per failure and neither surface can
    // invent a friendlier one that says something slightly different.

    @Override
    public List<io.github.stoicswe.eyeandsickle.protocol.game.NetFolder> folders() {
        return game.folders();
    }

    @Override
    public List<String> unfiledNodes() {
        return game.unfiledNodes();
    }

    private Outcome createFolderIntent(String parentId, String name) {
        var result = game.createFolder(parentId, name);
        return result.refused() ? Outcome.refused(result.why()) : changed(Outcome.ok());
    }

    private Outcome renameFolderIntent(String folderId, String name) {
        return apply(game.renameFolder(folderId, name));
    }

    private Outcome moveFolderIntent(String folderId, String newParentId) {
        return apply(game.moveFolder(folderId, newParentId));
    }

    private Outcome removeFolderIntent(String folderId) {
        return apply(game.removeFolder(folderId));
    }

    private Outcome fileNodeIntent(String address, String folderId) {
        return apply(game.fileNode(address, folderId));
    }

    private Outcome apply(io.github.stoicswe.eyeandsickle.solo.net.FolderRules.Refusal refusal) {
        return refusal.refused() ? Outcome.refused(refusal.why()) : changed(Outcome.ok());
    }

    private Outcome connectToIntent(String address) {
        return game.connectTo(address)
                ? changed(Outcome.ok("vantage moved to " + address + "; sweeps now measure hops from there"))
                : Outcome.refused("cannot connect to '" + address + "' — you must hold a host to use it as a vantage");
    }

    private Outcome downloadIntent(String address) {
        return game.download(address)
                .map(d -> changed(Outcome.ok("downloaded: " + d.title())))
                .orElseGet(() -> Outcome.refused("nothing to download from '" + address + "'"));
    }

    @Override
    public List<io.github.stoicswe.eyeandsickle.protocol.game.NetDocument> documents() {
        return game.documents();
    }

    private Outcome collectIntent() {
        long collected = game.collect();
        if (collected == 0) {
            return Outcome.ok("nothing to collect");
        }
        return changed(Outcome.ok("collected " + Ethecoin.ofMinorUnits(collected)));
    }

    private Outcome moveItemIntent(String itemId, StorageTier to) {
        if (!game.moveItem(itemId, to)) {
            return Outcome.refused("no such item: " + itemId);
        }
        return changed(Outcome.ok("moved to " + to));
    }

    private Outcome armIntent(String kind, int tier) {
        long cycles = defenseCycles(kind, tier);
        if (cycles <= 0) {
            return Outcome.usage("unknown defence '" + kind + "'");
        }
        for (DefenseState d : game.state().defenses) {
            if (d.kind.equals(kind)) {
                return Outcome.refused(kind + " is already armed");
            }
        }
        return game.arm(kind, tier, cycles)
                .map(d -> changed(Outcome.ok(kind + " armed; " + cycles + " cycles reserved while it runs")))
                .orElseGet(() -> notEnoughCycles(cycles));
    }

    private Outcome purchaseIntent(String offeringId) {
        var offering = io.github.stoicswe.eyeandsickle.solo.Catalogue.byId(offeringId);
        if (offering.isEmpty()) {
            return Outcome.refused("nothing is offered under that name");
        }
        var o = offering.get();

        // A gate that is not ethecoin is reported as EX_NOPERM with the requirement in words, never
        // as a refusal with a price. docs/client/04 §3.5: 77 means "a gate blocks this, and the
        // requirement is printed" — and printing the requirement is what makes the gate legible
        // rather than merely obstructive.
        if (!o.purchasable()) {
            return Outcome.gated(o.name() + " is behind the "
                    + o.gate().name().toLowerCase(Locale.ROOT).replace('_', '-')
                    + " gate. " + o.gateRequirement());
        }
        if (!game.debit(o.priceMinorUnits(), "MARKET", "Bought " + o.name())) {
            return Outcome.refused("not enough ethecoin — " + o.name() + " costs "
                    + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.ofMinorUnits(o.priceMinorUnits())
                    + ", you have " + balance());
        }
        ItemState item = new ItemState();
        item.displayName = o.name();
        item.itemType = o.id();
        item.tier = StorageTier.VAULT.name();
        item.origin = "bought";
        item.equippedCycles = o.equippedCycles();
        item.acquiredAt = game.now();
        game.state().items.add(item);

        return changed(Outcome.ok("bought " + o.name() + "; it is in the vault"));
    }

    /** Standing reservations from {@code docs/design/09-defense-and-hardening.md} §1. */
    private static long defenseCycles(String kind, int tier) {
        return switch (kind) {
            case "firewall" -> switch (tier) {
                case 1 -> Balance.DEFENSE_FIREWALL_T1_CYCLES;
                case 2 -> Balance.DEFENSE_FIREWALL_T2_CYCLES;
                default -> Balance.DEFENSE_FIREWALL_T3_CYCLES;
            };
            case "canary" -> Balance.DEFENSE_CANARY_CYCLES;
            case "tarpit" -> Balance.DEFENSE_TARPIT_CYCLES;
            case "honeypot-stash" -> Balance.DEFENSE_HONEYPOT_STASH_CYCLES;
            case "auto-counter-daemon" -> Balance.DEFENSE_AUTO_COUNTER_CYCLES;
            case "detection-array" -> switch (tier) {
                case 1 -> Balance.DEFENSE_DETECTION_ARRAY_T1_CYCLES;
                case 2 -> Balance.DEFENSE_DETECTION_ARRAY_T2_CYCLES;
                default -> Balance.DEFENSE_DETECTION_ARRAY_T3_CYCLES;
            };
            default -> 0L;
        };
    }

    // ------------------------------------------------------------------ plumbing

    @Override
    public AutoCloseable onChange(Consumer<GameSession> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void tick() {
        if (game.tick()) {
            fire();
        }
    }

    @Override
    public void persist() {
        game.persist();
    }

    @Override
    public void close() {
        persist();
        listeners.clear();
    }

    private Outcome changed(Outcome outcome) {
        fire();
        return outcome;
    }

    /**
     * Tells every listener something moved.
     *
     * <h2>⚠ One listener that throws must not take the others with it</h2>
     *
     * This used to be a bare loop. A panel that threw — an unexpected null in a readout, a widget
     * mid-rebuild — aborted the iteration, so <b>every listener after it in the list stopped being
     * notified for that change</b>, and which panels those were depended on the order they happened
     * to have subscribed in. The visible symptom is a window that silently stops updating and looks
     * frozen, with nothing in the log to say why, and the panel that actually had the bug is not the
     * one the player notices.
     *
     * <p>The throw is printed rather than swallowed. A listener that fails is still a bug and hiding
     * it entirely would trade a loud wrong behaviour for a quiet one; what changes is that it is now
     * that listener's problem alone.
     */
    private void fire() {
        for (Consumer<GameSession> l : List.copyOf(listeners)) {
            try {
                l.accept(this);
            } catch (RuntimeException failed) {
                System.err.println("[session] a change listener threw; the rest still ran: " + failed);
                failed.printStackTrace();
            }
        }
    }
    private static String money(long minorUnits) {
        return String.format(java.util.Locale.ROOT, "%d.%02d EC", minorUnits / 100, Math.abs(minorUnits % 100));
    }

}
