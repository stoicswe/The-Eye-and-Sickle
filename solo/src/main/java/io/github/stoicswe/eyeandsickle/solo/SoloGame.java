package io.github.stoicswe.eyeandsickle.solo;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.rules.EventLog;
import io.github.stoicswe.eyeandsickle.solo.rules.LedgerRules;
import io.github.stoicswe.eyeandsickle.solo.rules.MiningRules;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.DefenseState;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
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
        long recovered = ComputeRules.settleRecovered(save.rig, now);
        long accrued = MiningRules.accrueDeployedMiners(save, now);
        // ⚠ Tasks settle HERE, not only in tick(). resume() sets lastTick = now, so the first tick
        // after loading sees zero elapsed time and returns early — a six-minute scan that ended
        // while the game was closed would sit at 100% forever, never completing and never logging
        // its finding. Offline work belongs on the offline path, next to the miner accrual that
        // already lives here for exactly the same reason.
        settleTasks(now);

        // The log's primary job: telling a returning player what happened while they were gone.
        // Without this, offline income is invisible and a player has no way to tell it from a bug.
        java.time.Duration away = java.time.Duration.between(save.lastPlayedAt, now);
        if (!away.isNegative() && away.toMinutes() >= 1) {
            EventLog.notice(save, "rig", "Resumed after " + humanAway(away) + " away.", now);
            if (recovered > 0) {
                EventLog.info(save, "compute", recovered + " cycles finished recovering while away.", now);
            }
            if (accrued > 0) {
                EventLog.info(save, "mining",
                        "Deployed miners buffered " + money(accrued) + " while away. `collect` sweeps it.", now);
            }
            if (save.rig.selfMiningCycles > 0) {
                // Said explicitly because its absence is otherwise indistinguishable from a bug —
                // and Invariant I5 is the reason, not an oversight.
                EventLog.info(save, "mining",
                        "Self-mining earned nothing while away: it is online-only.", now);
            }
        }

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
        if (recovered > 0) {
            EventLog.info(save, "compute", recovered + " cycles recovered and are available again.", now);
        }
        changed |= recovered > 0;

        changed |= settleTasks(now);

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
        EventLog.info(save, "mining",
                cycles == 0
                        ? "Self-mining stopped; cycles released."
                        : "Self-mining set to " + cycles + " cycles (" + money(cycles * 40L) + "/hr while open).",
                clock.instant());
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
        Instant now = clock.instant();
        AllocationState a = ComputeRules.spend(
                save.rig, ComputeConsumer.ACTIVE_TOOL, "scan --" + tier.flag(), tier.cycles(), now);
        if (a == null) {
            return Optional.empty();
        }
        // The scan now actually runs for the duration docs/design/04 §3.2 publishes, instead of the
        // duration being a number in a log line that nothing ever waited for. The cycles are
        // unchanged — spent here, recovering on the thermal curve — because §3.2 lists Compute and
        // Duration as separate columns. See TaskState's class comment.
        save.tasks.add(new TaskState(
                "scan",
                "scan --" + tier.flag(),
                a.allocationId,
                tier.cycles(),
                now,
                now.plusSeconds(tier.seconds())));
        EventLog.notice(save, "scan",
                "scan --" + tier.flag() + " started: " + tier.cycles() + " cycles, ~" + tier.seconds() + "s.",
                now);
        return Optional.of(a);
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
            EventLog.notice(save, "scan", task.label + " finished. " + scanFinding(), now);
        }
        return changed;
    }

    /**
     * What a completed scan reports.
     *
     * <p>⚠ Deliberately honest about being a stub. {@code docs/design/04-mining.md} §3.2 makes the
     * tiers differ in what they <em>find</em> — Quick sees unhidden T2–T3 miners, Thorough sees
     * rootkit-wrapped ones — and solo has no foreign miners to find yet, because deployment onto
     * NPC nodes is not built. Returning a confident "nothing found" for a Thorough Scan would be a
     * lie the player would reasonably act on, so it says what it actually checked.
     */
    private String scanFinding() {
        long foreign = save.knownNodes.stream()
                .filter(n -> n.hostsForeignMiner)
                .count();
        return foreign == 0
                ? "No foreign miner on this rig. Manual audit still sees things a scan does not."
                : foreign + " node(s) host something that is not yours.";
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
        EventLog.notice(save, "defense",
                kind + " armed; " + cycles + " cycles reserved while it runs.", clock.instant());
        return Optional.of(d);
    }

    /** Sweeps every deployed miner's buffer into the balance. */
    public long collect() {
        long collected = MiningRules.collectAll(save, clock.instant());
        if (collected > 0) {
            EventLog.info(save, "mining", "Collected " + money(collected) + " from deployed miners.",
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
                EventLog.add(save, riskier ? 4 : 6, "storage",
                        item.displayName + " moved to " + to.name().toLowerCase(java.util.Locale.ROOT)
                                + (riskier ? " — now more exposed." : "."),
                        clock.instant());
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

    private static String money(long minorUnits) {
        return String.format(java.util.Locale.ROOT, "%d.%02d EC", minorUnits / 100, Math.abs(minorUnits % 100));
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
    public java.util.List<io.github.stoicswe.eyeandsickle.solo.state.RigEvent> log() {
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
