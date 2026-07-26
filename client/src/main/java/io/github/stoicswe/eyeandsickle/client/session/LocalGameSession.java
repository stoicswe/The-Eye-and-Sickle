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
        // Content, not code: the solo catalogue is deliberately small and lives in the market view.
        // W-3 in docs/design/15-open-questions.md tracks the same gap on the server side.
        return Outcome.refused("nothing is offered under that name");
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
