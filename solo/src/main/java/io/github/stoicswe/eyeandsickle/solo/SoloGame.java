package io.github.stoicswe.eyeandsickle.solo;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.rules.LedgerRules;
import io.github.stoicswe.eyeandsickle.solo.rules.MiningRules;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.DefenseState;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
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
 * A rules engine over a {@link SoloSave}, with no framework, no database, no thread and no socket.
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
public final class SoloGame {

    private final SaveStore store;
    private final Clock clock;
    private SoloSave save;
    private Instant lastTick;

    private SoloGame(SaveStore store, SoloSave save, Clock clock) {
        this.store = store;
        this.save = save;
        this.clock = clock;
        this.lastTick = clock.instant();
    }

    /**
     * Opens the save at {@code store}, creating a new character if there is not one.
     *
     * <p>The clock is injected rather than read from {@code Instant.now()} anywhere inside. A rules
     * engine that reaches for the wall clock behind its caller's back cannot be tested
     * deterministically, and — the sharper problem — it can disagree with itself about what time it
     * is, so an action started "now" outlives a tick that happens "later".
     */
    public static SoloGame open(SaveStore store, String handleIfNew, Clock clock) {
        SoloSave loaded = store.load();
        if (loaded == null) {
            loaded = newCharacter(handleIfNew, clock.instant());
        }
        SoloGame game = new SoloGame(store, loaded, clock);
        game.resume();
        return game;
    }

    /** The engine's current time. Every timestamp it writes comes from here. */
    public Instant now() {
        return clock.instant();
    }

    /** A fresh character: base rig, no money, nothing owned, nothing known. */
    public static SoloSave newCharacter(String handle, Instant now) {
        SoloSave s = new SoloSave();
        s.handle = handle == null || handle.isBlank() ? "operator" : handle.trim();
        s.createdAt = now;
        s.lastPlayedAt = now;
        s.ethecoinMinorUnits = Balance.STARTING_ETHECOIN_MINOR_UNITS;
        return s;
    }

    public SoloSave state() {
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
        ComputeRules.settleRecovered(save.rig, now);
        MiningRules.accrueDeployedMiners(save, now);

        // Self-mining is deliberately NOT credited for time away. It is online-only (Invariant I5),
        // and crediting it here would make the safe, zero-heat, unseizable income source also the
        // best one — which collapses the entire risk economy into "leave the game closed". Deployed
        // miners, accrued above, are the only offline income, and their buffer cap is what bounds it.
        save.lastPlayedAt = now;
        this.lastTick = now;
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

        long recovered = ComputeRules.settleRecovered(save.rig, now);
        changed |= recovered > 0;

        long selfYield = MiningRules.selfMiningYield(save.rig.selfMiningCycles, elapsed);
        if (selfYield > 0) {
            LedgerRules.apply(save, selfYield, "SELF_MINING", "Self-mining", now);
            changed = true;
        }

        changed |= MiningRules.accrueDeployedMiners(save, now) > 0;

        save.playedSeconds += elapsed.toSeconds();
        save.lastPlayedAt = now;
        lastTick = now;
        return changed;
    }

    public void persist() {
        store.save(save);
    }

    // ------------------------------------------------------------------ read model

    public ComputeBudget computeBudget() {
        return ComputeRules.snapshot(save);
    }

    public Ethecoin balance() {
        return Ethecoin.ofMinorUnits(save.ethecoinMinorUnits);
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
        long delta = cycles - save.rig.selfMiningCycles;
        if (delta > 0 && ComputeRules.availableCycles(save.rig) < delta) {
            return false;
        }
        save.rig.selfMiningCycles = cycles;
        return true;
    }

    /**
     * Runs a rig scan at one of the three tiers.
     *
     * <p>Costs are spent rather than reserved, so they come back on the Thermal Budget curve. What the
     * player buys with a more expensive tier is signal strength, not certainty — see {@code
     * docs/education/08-detection-and-defence.md} §3.5, which uses these exact three numbers to teach
     * the false-positive trade.
     *
     * @return the recovering allocation, or empty if the rig cannot afford the tier
     */
    public Optional<AllocationState> scan(ScanTier tier) {
        AllocationState a = ComputeRules.spend(
                save.rig, ComputeConsumer.ACTIVE_TOOL, "scan --" + tier.flag(), tier.cycles(), clock.instant());
        return Optional.ofNullable(a);
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
        return Optional.of(d);
    }

    /** Sweeps every deployed miner's buffer into the balance. */
    public long collect() {
        return MiningRules.collectAll(save, clock.instant());
    }

    /** Moves an item between storage tiers — the risk change is the point ({@code design/01} §6). */
    public boolean moveItem(String itemId, StorageTier to) {
        for (ItemState item : save.items) {
            if (item.itemId.equals(itemId)) {
                item.tier = to.name();
                return true;
            }
        }
        return false;
    }

    /** Spends ethecoin. Returns false rather than throwing when the player cannot afford it. */
    public boolean debit(long minorUnits, String type, String description) {
        if (!LedgerRules.canDebit(save, minorUnits)) {
            return false;
        }
        LedgerRules.apply(save, -minorUnits, type, description, clock.instant());
        return true;
    }

    public void credit(long minorUnits, String type, String description) {
        LedgerRules.apply(save, minorUnits, type, description, clock.instant());
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
