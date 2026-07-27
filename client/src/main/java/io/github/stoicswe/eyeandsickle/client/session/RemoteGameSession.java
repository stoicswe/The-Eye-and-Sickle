package io.github.stoicswe.eyeandsickle.client.session;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A session against a home server.
 *
 * <h2>The last-known-good rule</h2>
 *
 * Every read here returns the last value the server sent, even while disconnected, and reports
 * {@link #connected()} false. It never returns null and never blanks a readout. That is deliberate
 * and it is an accessibility decision as much as a UX one: a HUD that empties when the network hiccups
 * removes information from a player mid-decision, and {@code docs/client/01-visual-language.md} §2.2.8
 * gives stale values their own visual state precisely so they can be shown rather than hidden.
 *
 * <h2>Refused and unreachable are different, all the way down</h2>
 *
 * A refusal comes back as {@code 1} with the server's reason. An unreachable server comes back as
 * {@code 69} — {@code EX_UNAVAILABLE} — and a sent-but-unanswered request as {@code 75}. §9.4 requires
 * that "the server refused this" and "we could not reach the server" never collapse into one message,
 * and giving them different numbers is what makes that structural rather than a matter of copywriting
 * discipline. This class is where that distinction is actually produced.
 *
 * <h2>Status: the transport is not wired</h2>
 *
 * <strong>This implementation holds the shape and refuses every intent with {@code EX_UNAVAILABLE}.</strong>
 * The REST client, the AT Proto OAuth flow and the reconnect loop are <b>CL-8</b> in
 * {@code docs/design/15-open-questions.md} and are not built. Shipping the class in this state is
 * deliberate: it makes the port's two-implementation shape real and checkable, it lets the UI be
 * written against a disconnected session today, and it means the day the transport lands, no view
 * changes. What it must never become is a class that pretends to have data it does not have.
 */
public final class RemoteGameSession implements GameSession {

    private final URI server;
    private final String handle;
    private final List<Consumer<GameSession>> listeners = new CopyOnWriteArrayList<>();

    /** The last thing the server told us. Shown as stale rather than blanked. */
    private ComputeBudget lastBudget;

    private Ethecoin lastBalance = Ethecoin.ofMinorUnits(0);
    private int lastHeat;
    private boolean connected;

    public RemoteGameSession(URI server, String handle) {
        this.server = server;
        this.handle = handle;
        // An empty budget rather than null: see the last-known-good rule above. A rig with zero
        // capacity is obviously wrong on screen, which is better than a crash or a blank.
        this.lastBudget = new ComputeBudget(UUID.randomUUID(), Cycles.of(0), Cycles.of(0), List.of());
    }

    public URI server() {
        return server;
    }

    @Override
    public SessionMode mode() {
        return SessionMode.ONLINE;
    }

    @Override
    public String handle() {
        return handle;
    }

    @Override
    public ComputeBudget computeBudget() {
        return lastBudget;
    }

    @Override
    public Ethecoin balance() {
        return lastBalance;
    }

    @Override
    public int personalHeat() {
        return lastHeat;
    }

    @Override
    public List<InventoryItem> items(StorageTier tier) {
        return List.of();
    }

    @Override
    public List<LedgerRow> ledger(int limit) {
        return List.of();
    }

    @Override
    public List<KnownNode> knownNodes() {
        return List.of();
    }

    @Override
    public List<ArmedDefense> defenses() {
        return List.of();
    }

    @Override
    public List<LogLine> log(int minSeverity, int limit) {
        return List.of();
    }

    @Override
    public MiningSummary mining() {
        return new MiningSummary(0, 0, 0, 0);
    }

    @Override
    public java.util.List<RunningTask> tasks() {
        // The server is authoritative for what a rig is doing (I14). Empty is the honest answer for
        // a transport that does not exist yet, and the readout says "nothing running" rather than
        // inventing activity.
        return java.util.List.of();
    }

    @Override
    public java.time.Instant now() {
        // The server is authoritative for game time too, once there is one. Until then the local
        // clock is the honest answer rather than a fabricated offset.
        return java.time.Instant.now();
    }

    @Override
    public RigCapacity capacity() {
        // A starting rig's caps, so the desk has something coherent to draw before the transport
        // exists. The server is authoritative for these once it does (I14) — the client must never
        // be the thing that decides how much Bandwidth a player has.
        return new RigCapacity(1, 1, 1);
    }

    @Override
    public boolean connected() {
        return connected;
    }

    /**
     * Silence, until a server says otherwise.
     *
     * <p>⚠ Not derived from {@link #lastBudget}. Noise is a rule — which consumers reach other
     * machines, which running work is loud — and re-deriving it here would put a second
     * implementation of that rule in the client, which is the thing moving it into the engine was
     * meant to stop. A disconnected session reporting a quiet rig is also the safer error: a meter
     * that invented loudness would have the player scrubbing logs over nothing.
     */
    @Override
    public double noise() {
        return 0.0d;
    }

    // ------------------------------------------------------------------ intents

    /**
     * Every intent refuses with {@code EX_UNAVAILABLE} until the transport exists.
     *
     * <p>Not {@code REFUSED}: that would claim a rule considered the request and declined it, which
     * would be a lie about where the decision came from. The whole point of the distinction is that a
     * player can tell the difference between "the server said no" and "there was no server".
     */
    private Outcome unavailable() {
        return new Outcome(
                Outcome.UNAVAILABLE,
                "Not connected to " + server + ". Online play is not wired up yet — see CL-8.");
    }

    @Override
    public Outcome allocateSelfMining(long cycles) {
        return unavailable();
    }

    @Override
    public Outcome scan(String tier) {
        return unavailable();
    }

    @Override
    public Outcome collect() {
        return unavailable();
    }

    @Override
    public Outcome moveItem(String itemId, StorageTier to) {
        return unavailable();
    }

    @Override
    public Outcome arm(String kind, int tier) {
        return unavailable();
    }

    @Override
    public Outcome purchase(String offeringId) {
        return unavailable();
    }

    // ------------------------------------------------------------------ plumbing

    @Override
    public AutoCloseable onChange(Consumer<GameSession> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void tick() {
        // A real implementation polls or holds a WebSocket here, off the JavaFX thread, and calls
        // fire() when something arrives. Doing nothing is the honest behaviour for a session with no
        // transport — it must not invent a heartbeat that implies a connection.
    }

    @Override
    public void persist() {
        // Nothing to persist: the server owns the state. That asymmetry with LocalGameSession is
        // Invariant I14 showing through the port, and it is correct rather than an omission.
    }

    @Override
    public void close() {
        listeners.clear();
    }

    private void fire() {
        for (Consumer<GameSession> l : listeners) {
            l.accept(this);
        }
    }

    // ── The breach ────────────────────────────────────────────────────────────────────────────
    //
    // Reads return a last-known value — an empty list, no breach in progress — rather than null or
    // an exception, because a network hiccup must never empty a HUD mid-decision (CL-8). Intents
    // return 69 EX_UNAVAILABLE rather than 1 REFUSED: claiming a *rule* declined the request would
    // be a lie about where the decision came from, and the player would go looking for a rule that
    // does not exist.

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget> breachTargets() {
        return java.util.List.of();
    }

    @Override
    public java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot> breach() {
        return java.util.Optional.empty();
    }

    @Override
    public Outcome beginBreach(String targetId) {
        return unavailable();
    }

    @Override
    public Outcome breachAction(String actionId, String argument) {
        return unavailable();
    }

    @Override
    public Outcome abortBreach() {
        return unavailable();
    }

    @Override
    public Outcome dismissBreach() {
        return unavailable();
    }

    // ── The network ───────────────────────────────────────────────────────────────────────────
    //
    // Reads hand back an empty last-known view rather than null (CL-8: a hiccup must never empty a
    // HUD mid-decision); intents return 69 EX_UNAVAILABLE rather than 1 REFUSED, because saying a
    // rule declined would be a lie about where the decision came from.

    @Override
    public io.github.stoicswe.eyeandsickle.protocol.game.NetMap net() {
        return io.github.stoicswe.eyeandsickle.protocol.game.NetMap.empty();
    }

    @Override
    public Outcome sweep(String flag) {
        return unavailable();
    }

    @Override
    public Outcome connectTo(String address) {
        return unavailable();
    }

    @Override
    public Outcome download(String address) {
        return unavailable();
    }

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.NetDocument> documents() {
        return java.util.List.of();
    }

    // ── The process table ─────────────────────────────────────────────────────────────────────
    //
    // What is running on a rig, and what a parasite is disguised as, are the server's answers (I14).
    // An empty table is the honest one for a transport that does not exist: a fabricated process
    // list would be the client inventing the thing the whole audit mechanic is about.

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.RigProcess> processes() {
        return java.util.List.of();
    }

    @Override
    public Outcome killProcess(String processId) {
        return unavailable();
    }

    @Override
    public Outcome restartProcess(String processId) {
        return unavailable();
    }

    // ── Filing what has been found ────────────────────────────────────────────────────────────
    //
    // The player's filing is state a home server owns like any other (I14) — a folder names a
    // discovered address, and which addresses are discovered is the server's answer. So there is
    // nothing to hand back and nothing to accept until the transport exists.

    @Override
    public java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.NetFolder> folders() {
        return java.util.List.of();
    }

    @Override
    public java.util.List<String> unfiledNodes() {
        return java.util.List.of();
    }

    @Override
    public Outcome createFolder(String parentId, String name) {
        return unavailable();
    }

    @Override
    public Outcome renameFolder(String folderId, String name) {
        return unavailable();
    }

    @Override
    public Outcome moveFolder(String folderId, String newParentId) {
        return unavailable();
    }

    @Override
    public Outcome removeFolder(String folderId) {
        return unavailable();
    }

    @Override
    public Outcome fileNode(String address, String folderId) {
        return unavailable();
    }
}
