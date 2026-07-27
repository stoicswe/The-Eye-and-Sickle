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

    /** Moves the vantage to a host the player holds. Sweeping again measures hops from there. */
    Outcome connectTo(String address);

    /** Pulls a document off a host that carries one. */
    Outcome download(String address);

    /** Everything downloaded so far. */
    List<io.github.stoicswe.eyeandsickle.protocol.game.NetDocument> documents();

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
