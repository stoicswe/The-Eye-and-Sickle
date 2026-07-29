package io.github.stoicswe.eyeandsickle.client.session;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.RemoteSession;
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
    public String avatar() {
        return game.state().avatarPng;
    }

    @Override
    public Outcome setAvatar(String base64Png) {
        game.state().avatarPng = base64Png == null ? "" : base64Png;
        persist();
        return changed(Outcome.ok(base64Png == null || base64Png.isBlank()
                ? "picture cleared" : "picture set"));
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
    public long uptimeSeconds() {
        return game.state().playedSeconds;
    }

    @Override
    public int storageCapacity(StorageTier tier) {
        return io.github.stoicswe.eyeandsickle.solo.Balance.storageCapacity(tier);
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
    public java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest> packageAt(
            String path) {
        return io.github.stoicswe.eyeandsickle.solo.rules.Repac.manifest(game.state(), path);
    }

    @Override
    public Outcome portScan(
            String address, io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget target) {
        var started = game.portScan(address, target);
        if (started.succeeded()) {
            return changed(Outcome.ok(String.format(Locale.ROOT,
                    "scanning %s for %s — %d cycles, ~%ds, %d%% chance it notices.",
                    address, target.label().toLowerCase(Locale.ROOT),
                    started.cycles(), started.duration().toSeconds(), started.riskPercent())));
        }
        // ⚠ The rules' refusal, named. "Could not scan" would leave a player retrying a thing that
        // will refuse for the same reason every time.
        return switch (started.refusal()) {
            case UNKNOWN_HOST -> Outcome.refused(
                    "no machine at " + address + " that a sweep has found. Sweep for it first.");
            case YOUR_OWN_RIG -> Outcome.refused(
                    "that is your own rig. The audit window reads it directly — free, silent, and "
                            + "it sees everything a scan could not.");
            case NOT_ENOUGH_CYCLES -> notEnoughCycles(started.cycles());
            case ALREADY_RUNNING -> Outcome.refused("a scan of " + address + " is already running.");
        };
    }

    @Override
    public java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport>
            portScanReport(String address) {
        return game.portScan(address);
    }

    @Override
    public java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.NodeReport> nodeReport(
            String address) {
        return io.github.stoicswe.eyeandsickle.solo.net.NodeReports.at(game.state(), address);
    }

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.NodeReport> nodeReports() {
        return io.github.stoicswe.eyeandsickle.solo.net.NodeReports.all(game.state());
    }

    @Override
    public Outcome nameNode(String address, String alias) {
        if (!io.github.stoicswe.eyeandsickle.solo.net.NodeReports.rename(game.state(), address, alias)) {
            return Outcome.refused("no report on " + address + " — scan it first, then name it.");
        }
        persist();
        return changed(Outcome.ok(alias == null || alias.isBlank()
                ? "name cleared on " + address
                : address + " is now \"" + alias.trim() + "\""));
    }

    @Override
    public Outcome tagNode(String address, java.util.List<String> tags) {
        if (!io.github.stoicswe.eyeandsickle.solo.net.NodeReports.retag(game.state(), address, tags)) {
            return Outcome.refused("no report on " + address + " — scan it first, then tag it.");
        }
        persist();
        var now = io.github.stoicswe.eyeandsickle.solo.net.NodeReports.at(game.state(), address);
        return changed(Outcome.ok(now.map(r -> r.tags().isEmpty()
                        ? "tags cleared on " + address
                        : address + " tagged " + String.join(", ", r.tags()))
                .orElse("tags updated")));
    }

    @Override
    public PortScanQuote portScanQuote(
            String address, io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget target) {
        long cycles = io.github.stoicswe.eyeandsickle.solo.net.PortScanRules.cyclesFor(target);
        long seconds = io.github.stoicswe.eyeandsickle.solo.net.PortScanRules
                .durationFor(target).toSeconds();
        var host = game.state().topology == null ? null
                : game.state().topology.hosts.stream()
                        .filter(h -> address.equals(h.address))
                        .findFirst()
                        .orElse(null);
        int risk = io.github.stoicswe.eyeandsickle.solo.net.PortScanRules.riskPercent(host, target);
        boolean affordable = computeBudget().available().cycles() >= cycles;
        return new PortScanQuote(cycles, seconds, risk, affordable);
    }

    @Override
    public Outcome boostFee(String txHash, io.github.stoicswe.eyeandsickle.protocol.game.FeeTier tier) {
        var result = io.github.stoicswe.eyeandsickle.solo.rules.MempoolRules.boost(
                game.state(), txHash, tier, game.now());
        return result.ok() ? changed(Outcome.ok(result.message())) : Outcome.refused(result.message());
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
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainSync chainSync() {
        return game.chainSync();
    }

    @Override
    public io.github.stoicswe.eyeandsickle.protocol.game.ChainSync takeChainSync() {
        return game.takeChainSync();
    }

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.BlockContribution> contributions(
            int limit) {
        return game.contributions(limit);
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

    // ── Shell sessions and the filesystem ─────────────────────────────────────────────────────
    //
    // ⚠ Deliberately NOT routed through connectTo. A session is a shell on a machine already held;
    // the vantage is the single point a sweep measures from (I2). See SessionRules.

    @Override
    public java.util.List<RemoteSession> sessions() {
        java.util.List<RemoteSession> out = new java.util.ArrayList<>();
        for (var state : game.sessions()) {
            var host = game.net().at(state.address);
            out.add(new RemoteSession(
                    state.address,
                    host.map(io.github.stoicswe.eyeandsickle.protocol.game.Sighting::label).orElse(""),
                    state.cwd,
                    state.openedAt,
                    state.cycles,
                    host.map(io.github.stoicswe.eyeandsickle.protocol.game.Sighting::vantage).orElse(false)
                            && isOwnRig(state.address)));
        }
        return java.util.List.copyOf(out);
    }

    private boolean isOwnRig(String address) {
        return game.net().at(address)
                .map(s -> s.kind() == io.github.stoicswe.eyeandsickle.protocol.game.HostKind.SELF)
                .orElse(false);
    }

    @Override
    public Outcome openSession(String address) {
        return announce("net", openSessionIntent(address));
    }

    private Outcome openSessionIntent(String address) {
        var opened = game.openSession(address);
        if (opened.succeeded()) {
            return changed(Outcome.ok("shell opened on " + address
                    + " — " + opened.session().cycles + " cycles held while it stays open"));
        }
        // Each refusal names the thing that would fix it. A shell that just said "no" on a machine
        // the player can SEE is the most frustrating possible refusal, because the obstacle is
        // invisible: docs/client/04 §3.5's rule that 77 means "not yet, and here is why".
        return switch (opened.refusal()) {
            case UNKNOWN_HOST -> Outcome.refused(
                    "no machine at " + address + " — sweep for it first");
            case NO_FOOTHOLD -> Outcome.gated(
                    "no foothold on " + address + " — breach it before you can run anything on it");
            case NOT_ENOUGH_COMPUTE -> notEnoughCycles(
                    io.github.stoicswe.eyeandsickle.solo.Balance.SESSION_CYCLES);
        };
    }

    @Override
    public Outcome closeSession(String address) {
        if (!game.closeSession(address)) {
            return Outcome.refused("no shell open on " + address);
        }
        return announce("net", changed(Outcome.ok("shell on " + address + " closed; cycles returned")));
    }

    @Override
    public Outcome changeDirectory(String address, String path) {
        if (game.changeDirectory(address, path)) {
            return changed(Outcome.ok(game.session(address).map(s -> s.cwd).orElse("/")));
        }
        // The wording is a real `cd`'s, because it is the message a player will meet again.
        return Outcome.refused("cd: " + path + ": No such file or directory");
    }

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.FsEntry> list(
            String address, String path) {
        return game.list(address, path);
    }

    @Override
    public java.util.List<String> read(String address, String path) {
        return game.read(address, path);
    }

    @Override
    public java.util.List<String> info(String address, String path) {
        return game.info(address, path);
    }

    @Override
    public Outcome download(
            String address,
            io.github.stoicswe.eyeandsickle.protocol.game.FsEntry entry,
            String destination) {
        var started = game.download(address, entry, destination);
        if (started.succeeded()) {
            return announce("net", changed(Outcome.ok(
                    "downloading " + entry.name() + " — " + bytes(started.bytes()) + " at "
                            + megabits(io.github.stoicswe.eyeandsickle.solo.Balance.LINK_UP_BITS)
                            + ", about " + Math.max(1, started.duration().toSeconds()) + "s")));
        }
        return switch (started.refusal()) {
            case NOT_CONNECTED -> Outcome.refused(
                    "not connected to " + address + " — mount it before pulling anything off it");
            case NOT_READABLE -> Outcome.gated(
                    entry.name() + ": you do not hold this machine. Breach it first.");
            case ALREADY_RUNNING -> Outcome.refused(entry.name() + " is already on its way");
            // Named rather than generic: a player who tried to copy a log file is entitled to know
            // it is scenery rather than to conclude downloads are broken.
            case NOT_TRANSFERABLE -> Outcome.refused(
                    entry.name() + ": nothing on your rig would know what to do with this. "
                            + "Fragments, wallets, upgrades and schematics are what transfer.");
        };
    }

    @Override
    public java.util.List<String> downloadDestinations() {
        String home = io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.home(handle());
        // The home folders a desktop actually offers in a Save-as sheet. Downloads first, because it
        // is the default and the first entry is the one a hurried player takes.
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(home + "/Downloads");
        for (String folder : io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.homeFolders()) {
            String path = home + "/" + folder;
            if (!out.contains(path)) {
                out.add(path);
            }
        }
        return java.util.List.copyOf(out);
    }

    @Override
    public Outcome install(String path) {
        var result = game.install(path);
        return result.ok()
                ? announce("storage", changed(Outcome.ok(result.message())))
                : Outcome.refused(result.message());
    }

    @Override
    public Outcome sell(String path) {
        var result = game.sell(path);
        return result.ok()
                ? announce("ledger", changed(Outcome.ok(
                        result.message() + " for " + money(result.minorUnits()))))
                // 77 rather than 1 for the gate case: it is "not this way", not "no".
                : new Outcome(
                        result.refusal() == io.github.stoicswe.eyeandsickle.solo.rules.Repac
                                .Refusal.NOT_SELLABLE ? Outcome.NOPERM : Outcome.REFUSED,
                        result.message());
    }

    @Override
    public java.util.List<RunningTask> transfers() {
        java.time.Instant asOf = game.now();
        return game.transfers().stream()
                .map(task -> new RunningTask(
                        task.taskId, task.kind, task.label,
                        "the other end's upload is the ceiling, not your download",
                        task.startedAt, task.endsAt, task.cycles, asOf))
                .toList();
    }

    /** Bytes, in the units a transfer readout should use. Decimal — see Balance's link constants. */
    private static String bytes(long value) {
        if (value < 1_000_000L) {
            return String.format(Locale.ROOT, "%.0f kB", value / 1_000.0d);
        }
        return String.format(Locale.ROOT, "%.1f MB", value / 1_000_000.0d);
    }

    private static String megabits(long bits) {
        return (bits / 1_000_000L) + " Mbit/s";
    }

    @Override
    public void noteAccess(String address, String path) {
        game.noteAccess(address, path);
        // No announce(): looking at a folder is not an event and does not belong in the rig's log.
        // Recents changed, though, and the file manager's sidebar draws it.
        changed(Outcome.ok(""));
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

    /**
     * The sweep ladder with the rules' own ownership verdict on each rung.
     *
     * <p>⚠ The verdict is {@code game.ownsSweep}, which is {@code NetRules.owns} — the <b>same</b>
     * call {@link #sweepIntent} makes before commissioning a sweep. That is the whole point: a
     * control that reads locked and a sweep that then succeeds, or the reverse, is worse than no
     * indication at all, and the only way to guarantee they agree is for there to be one answer.
     * The wording is assembled here rather than in the view because this is the layer that already
     * knows how to translate a rules answer into a sentence — {@code sweepIntent} builds the
     * matching refusal three lines up.
     */
    @Override
    public java.util.List<SweepOption> sweepOptions() {
        java.util.List<SweepOption> options = new java.util.ArrayList<>();
        for (var tier : io.github.stoicswe.eyeandsickle.solo.net.SweepTier.values()) {
            boolean owned = game.ownsSweep(tier);
            var offering = io.github.stoicswe.eyeandsickle.solo.Catalogue.byId(tier.itemId());
            String name = offering.map(o -> o.name()).orElse(tier.label());
            long price = offering.map(o -> o.priceMinorUnits()).orElse(0L);
            // Words, never a bare price: docs/client/05 §5 forbids a generic "locked" and requires
            // the requirement itself be stated. A player who is told "25.00 EC" without being told
            // WHAT costs it has been given a number, not a route.
            String requirement = owned
                    ? ""
                    : price > 0
                            ? "the " + name + " tool, " + money(price) + " in the market"
                            : "the " + name + " tool";
            options.add(new SweepOption(
                    flagFor(tier),
                    name,
                    owned,
                    requirement,
                    price,
                    tier.tier(),
                    tier.cycles(),
                    tier.seconds(),
                    tier.noiseCycles()));
        }
        return java.util.List.copyOf(options);
    }

    /**
     * The flag {@link #sweep} takes for a tier.
     *
     * <p>Derived from the tier's own label rather than switched on, so a fourth tier cannot arrive
     * with a flag this method has never heard of and silently get the base sweep's empty string.
     */
    private static String flagFor(io.github.stoicswe.eyeandsickle.solo.net.SweepTier tier) {
        int space = tier.label().indexOf(' ');
        return space < 0 ? "" : tier.label().substring(space + 1);
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
        // Already on its way, or already installed. Refused rather than charged twice.
        if (game.state().items.stream().anyMatch(i -> o.id().equals(i.itemType))) {
            return Outcome.refused("you already have " + o.name() + ".");
        }
        if (io.github.stoicswe.eyeandsickle.solo.net.TransferRules
                .running(game.state(), "/market/" + o.id() + ".pkg").isPresent()) {
            return Outcome.refused(o.name() + " is already downloading.");
        }
        // ⚠ THE ITEM IS NOT CREATED HERE ANY MORE (changed 2026-07-29).
        //
        // A purchase used to hand over the goods in the same call that took the money, which
        // SoloGame.debit defended as the one place the simulation declined to be faithful. It now
        // downloads a package like any other upgrade — over a real transfer, into Downloads — and
        // that package will not install until the payment is mined. See docs/design/04 §1.3e.
        //
        // ⚠ spend(), not debit(): the package has to wait on the RIGHT ledger row. debit() writes
        // two — the purchase and a separate TX_FEE line — and only the first is broadcast, so
        // reaching for the end of the ledger gets the fee, which never confirms and would hold the
        // package forever with the money gone.
        var paid = game.spend(o.priceMinorUnits(), "MARKET", "Bought " + o.name(),
                io.github.stoicswe.eyeandsickle.protocol.game.FeeTier.STANDARD, "");
        if (paid.isEmpty()) {
            return Outcome.refused("not enough ethecoin — " + o.name() + " costs "
                    + io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.ofMinorUnits(o.priceMinorUnits())
                    + ", you have " + balance());
        }
        var started = io.github.stoicswe.eyeandsickle.solo.net.TransferRules.beginPurchase(
                game.state(), o.id(), o.id() + ".pkg", paid.get().entryId, game.now());
        if (!started.succeeded()) {
            // The money is already gone and the download did not start, which must never happen
            // silently. Nothing here can currently produce it — the two refusals it can return are
            // both checked above — so this is the branch that exists to be loud if that stops being
            // true rather than to be taken.
            return changed(Outcome.refused(
                    "paid for " + o.name() + ", but the download would not start. `ledger` has the "
                            + "transaction; the package is not on its way."));
        }
        return changed(Outcome.ok(String.format(Locale.ROOT,
                "bought %s — downloading %.0f MB to Downloads. It installs once the block carrying "
                        + "your payment confirms.",
                o.name(), started.bytes() / (1024.0d * 1024.0d))));
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
        // ⚠ Here rather than inside SoloGame, and that is a module boundary rather than taste. The
        // lamp is a client concern, and `solo` is a plain rules library the client's own enforcer
        // rules keep free of anything that is not — a UI signal reaching into it would be the first
        // crack in that. `SoloGame.persist` writes unconditionally, so this fires exactly as often
        // as the file is rewritten.
        //
        // Note RemoteGameSession.persist does NOT light it: the server owns that state and nothing
        // touches the player's disk. The lamp reporting an online save would be describing somebody
        // else's hardware.
        io.github.stoicswe.eyeandsickle.client.DiskActivity.wrote();
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
