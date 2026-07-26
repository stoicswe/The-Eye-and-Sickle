package io.github.stoicswe.eyeandsickle.client.session;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
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

    /** Whether this session is a local solo game or a connection to a home server. */
    SessionMode mode();

    /** The player's handle. In solo this is a local name, not a DID — there is no identity here. */
    String handle();

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
     * The rig's structural caps — the axes {@code docs/design/11-rig-infrastructure.md} §2 defines
     * that are not compute.
     *
     * <p>Read by the desk, which uses Bandwidth to cap how many tool windows may be open at once
     * ({@code docs/design/ui-design-language.md} §8). That mapping is a <b>[PROPOSAL]</b> and is
     * defaulted off — see {@code docs/design/15-open-questions.md} <b>UI-2</b>.
     */
    RigCapacity capacity();

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

    // ------------------------------------------------------------------ intents

    /** Commits cycles to self-mining. Safe, silent, zero-heat (I4), online-only (I5). */
    Outcome allocateSelfMining(long cycles);

    /** Runs a rig scan. The tiers cost 5 / 15 / 35 cycles and buy signal strength, not certainty. */
    Outcome scan(String tier);

    /** Sweeps deployed-miner buffers into the balance. */
    Outcome collect();

    /** Moves an item between storage tiers. The risk change is the point ({@code design/01} §6). */
    Outcome moveItem(String itemId, StorageTier to);

    /** Arms a defence. Defending your own rig never generates heat (Invariant I9). */
    Outcome arm(String kind, int tier);

    /** Buys from the market. Refused — not thrown — when the player cannot afford it or a gate blocks. */
    Outcome purchase(String offeringId);

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
    record LedgerRow(String entryId, java.time.Instant at, long deltaMinorUnits, long balanceAfterMinorUnits, String type, String description) {}

    /** One discovered machine. Undiscovered nodes are never in this list — recon is a paid service. */
    record KnownNode(String address, String label, int reconLevel, int tier, int deployedMiners, boolean hostsForeignMiner) {}

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
     * @param bufferedMinorUnits yield sitting on hosts, waiting to be collected
     * @param bufferCapMinorUnits the ceiling those buffers stop at — the reason time away is worth
     *     something but not proportionally
     * @param deployedMiners how many are live
     */
    record MiningSummary(
            long selfMiningCycles, long bufferedMinorUnits, long bufferCapMinorUnits, int deployedMiners) {

        /** True once every buffer is full, which is when being away stops paying at all. */
        public boolean buffersFull() {
            return deployedMiners > 0 && bufferedMinorUnits >= bufferCapMinorUnits;
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
}
