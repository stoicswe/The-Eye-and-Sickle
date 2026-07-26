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

    @Override
    public Outcome allocateSelfMining(long cycles) {
        if (cycles < 0) {
            return Outcome.usage("cycles must not be negative");
        }
        if (!game.allocateSelfMining(cycles)) {
            return Outcome.refused("not enough available compute — the rig has "
                    + computeBudget().available().cycles() + " free");
        }
        return changed(Outcome.ok("self-mining set to " + cycles + " cycles"));
    }

    @Override
    public Outcome scan(String tier) {
        SoloGame.ScanTier t;
        try {
            t = SoloGame.ScanTier.valueOf(tier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Outcome.usage("unknown scan tier '" + tier + "' — expected quick, full or thorough");
        }
        return game.scan(t)
                .map(a -> changed(Outcome.ok("scan --" + t.flag() + " started; " + t.cycles() + " cycles committed")))
                .orElseGet(() -> Outcome.refused("not enough available compute — " + t.cycles() + " needed, "
                        + computeBudget().available().cycles() + " free"));
    }

    @Override
    public Outcome collect() {
        long collected = game.collect();
        if (collected == 0) {
            return Outcome.ok("nothing to collect");
        }
        return changed(Outcome.ok("collected " + Ethecoin.ofMinorUnits(collected)));
    }

    @Override
    public Outcome moveItem(String itemId, StorageTier to) {
        if (!game.moveItem(itemId, to)) {
            return Outcome.refused("no such item: " + itemId);
        }
        return changed(Outcome.ok("moved to " + to));
    }

    @Override
    public Outcome arm(String kind, int tier) {
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
                .orElseGet(() -> Outcome.refused("not enough available compute — " + cycles + " needed, "
                        + computeBudget().available().cycles() + " free"));
    }

    @Override
    public Outcome purchase(String offeringId) {
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

    private void fire() {
        for (Consumer<GameSession> l : listeners) {
            l.accept(this);
        }
    }
}
