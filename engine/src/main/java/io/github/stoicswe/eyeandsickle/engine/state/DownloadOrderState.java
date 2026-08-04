package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Something bought and waiting to come down the wire.
 *
 * <h2>⚠ An ORDER is not a TASK, and the split is the whole design</h2>
 *
 * A {@link TaskState} is work with a deadline: it started, it ends, and the clock between those two
 * is the only thing that moves. That is exactly wrong for a queue, where most entries have not
 * started and one of them is progressing — a task list can express "three transfers running at
 * once" and cannot express "three bought, one downloading".
 *
 * <p>So this is the <b>purchase</b>, which exists from the moment the money moves, and the task is
 * the <b>transfer</b>, which exists only while bytes are in flight. An order with no
 * {@link #taskId} has not started; an order whose task has finished is removed. That is also what
 * makes pausing honest: the money is already spent and the goods are still owed, whatever is or is
 * not moving right now.
 *
 * <h2>⚠ The order survives a restart, because the payment did</h2>
 *
 * Serialised with the save like every other state class. A queue that lived in the client would
 * lose two paid-for downloads when somebody closed the window, and there is no way to tell that
 * apart from theft.
 */
public final class DownloadOrderState {

    public String orderId = UUID.randomUUID().toString();

    /**
     * The catalogue id this installs as, for a single item. Empty for a bundle — a bundle installs
     * as nothing, it <em>contains</em> things, and that is {@link #memberItemTypes}.
     */
    public String itemType = "";

    /** What lands in Downloads: {@code tarpit.pkg}, or {@code bundle-xxxx.tar.xz}. */
    public String fileName = "";

    /**
     * The ledger row that paid for this.
     *
     * <p>⚠ Carried all the way to the file, because it is what releases it — a bought package stays
     * a vendor {@code .pkg} until this transaction is mined. For a bundle it is carried to
     * <b>every</b> member, so the archive's contents are held by the one payment that bought them.
     */
    public String entryId = "";

    /** How big the download is. Fixed at purchase so the queue can total itself without the rules. */
    public long bytes = 0L;

    /**
     * ⚠ Whether the vendor is somebody else's machine, decided at purchase and carried here.
     *
     * <p>The noise belongs to the transfer, and the transfer does not exist yet — a queued purchase
     * makes no racket, because nothing is talking to anybody. Recomputing it when the download
     * finally starts would ask the question of a different game state than the one the player bought
     * in, so it is answered once and kept.
     */
    public boolean foreign = true;

    /**
     * ⚠ Paused by the PLAYER, and this is the only reason a download does not progress.
     *
     * <p>Deliberately not "is it running" — that is derived, and a stored copy of it would be a
     * second answer to a question the queue's own order already settles. The active download is the
     * first order that is not paused; everything else is held. One flag, one truth.
     */
    public boolean paused = false;

    /** The transfer carrying it, once one has started. Empty while it is still waiting its turn. */
    public String taskId = "";

    /** What is inside a bundle. Empty for a single item. */
    public List<String> memberItemTypes = new ArrayList<>();

    /** For the queue readout, so an order can say when it was bought rather than only what it is. */
    public Instant orderedAt = Instant.EPOCH;

    /** A human name for the readout — the item's, or {@code "Bundle (3 items)"}. */
    public String label = "";

    public DownloadOrderState() {}

    /** @return whether this order is a bundle rather than a single item. */
    public boolean isBundle() {
        return !memberItemTypes.isEmpty();
    }

    /** @return whether a transfer has been commissioned for it. */
    public boolean started() {
        return !taskId.isBlank();
    }
}
