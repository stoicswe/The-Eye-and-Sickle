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
    public boolean connected() {
        return connected;
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
}
