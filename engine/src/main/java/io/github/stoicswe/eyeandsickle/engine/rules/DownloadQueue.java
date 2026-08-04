package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.net.TransferRules;
import io.github.stoicswe.eyeandsickle.engine.state.DownloadOrderState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One thing downloads at a time, and the player says which.
 *
 * <h2>⚠ THE ACTIVE DOWNLOAD IS DERIVED — the first order that is not paused</h2>
 *
 * Nothing stores which download is running. Ask the list: the head, skipping paused entries. That
 * one decision is why pausing and reordering need no separate machinery — moving an order to the
 * front <em>is</em> starting it, pausing the head <em>is</em> promoting the next one, and there is
 * no combination of the two that can leave a stored flag disagreeing with the list.
 *
 * <h2>⚠ A HELD DOWNLOAD HAS ITS CLOCK PUSHED FORWARD, it is not cancelled</h2>
 *
 * A transfer is a deadline: {@code startedAt}, {@code endsAt}, and wall time doing the rest. There
 * is no way to stop wall time, so holding one means moving <b>both</b> ends forward by however long
 * the hold lasted — which leaves {@code progressAt} reading exactly what it read when the hold
 * began. Shifting only {@code endsAt} would stretch the transfer instead of pausing it, and the bar
 * would visibly crawl backwards.
 *
 * <p>⚠ It follows that the shift must be applied on <b>every</b> tick and in {@code resume()}, with
 * the same delta the rest of the tick uses. A queue paused across a four-day absence whose clocks
 * were not shifted would find every held transfer finished on the first tick back — the pause
 * silently doing the opposite of what the player asked.
 *
 * <h2>⚠ Scope: MARKET downloads only</h2>
 *
 * A file pulled off a machine you are standing on is not queued and never held. That is not an
 * oversight — a purchase is a thing you own that has not arrived yet, and it can wait; a transfer
 * off somebody else's box is running over a session you are holding open and paying for, and
 * putting it behind two bought packages would cost the player a foothold to save them bandwidth
 * they do not have to ration. The two are different acts and the queue governs one of them.
 */
public final class DownloadQueue {

    private DownloadQueue() {}

    /**
     * How many market downloads progress at once.
     *
     * <p>One, and the whole feature depends on it: a queue with a concurrency of two is a list with
     * a decoration. It is a constant rather than a literal so the day it becomes a purchasable
     * upgrade there is one place to look — ⚠ and if that day comes, note that it would be
     * <b>compute buying throughput</b> only if it were priced in cycles, which <b>I1</b> forbids;
     * priced in ethecoin it is breadth, not a ceiling, and I2 is satisfied.
     */
    public static final int CONCURRENCY = 1;

    /**
     * Adds a purchase to the back of the queue, and starts it if the queue was empty.
     *
     * <h2>⚠ It SETTLES immediately, and leaving that to the next tick is a real defect</h2>
     *
     * A player who buys one thing with nothing else owed should see a bar, not a queue entry that
     * sits inert until the clock comes round. The lag is about a second in the running game and
     * indefinite in anything driven by a test clock — so "buy, then look" reports no download at
     * all, which is the shape of every complaint about a purchase that appeared not to work.
     *
     * <p>⚠ {@link Duration#ZERO} is the right delta here and not a shortcut: no wall time has
     * passed inside this call, so nothing held may have its clock pushed. {@link #settle} guards on
     * it, so a held transfer is untouched and only the promotion happens.
     */
    public static DownloadOrderState enqueue(GameSave save, DownloadOrderState order, Instant now) {
        order.orderedAt = now;
        save.downloadQueue.add(order);
        settle(save, Duration.ZERO, now);
        return order;
    }

    /** @return every order, in the order they will arrive. */
    public static List<DownloadOrderState> orders(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.downloadQueue);
    }

    /** @return the order that is progressing right now, if any. */
    public static Optional<DownloadOrderState> active(GameSave save) {
        if (save == null) {
            return Optional.empty();
        }
        return save.downloadQueue.stream().filter(order -> !order.paused).findFirst();
    }

    public static Optional<DownloadOrderState> byId(GameSave save, String orderId) {
        if (save == null || orderId == null) {
            return Optional.empty();
        }
        return save.downloadQueue.stream().filter(order -> orderId.equals(order.orderId)).findFirst();
    }

    /**
     * Holds an order.
     *
     * <p>⚠ Pausing the active one promotes the next, which is the behaviour of every download
     * manager a player has used and the reason pause is worth having at all. It does not cancel:
     * the bytes already transferred stay transferred, because the task's clock is shifted rather
     * than reset.
     *
     * @return whether anything changed
     */
    public static boolean pause(GameSave save, String orderId) {
        return byId(save, orderId)
                .filter(order -> !order.paused)
                .map(order -> {
                    order.paused = true;
                    return true;
                })
                .orElse(false);
    }

    /** @return whether anything changed */
    public static boolean resume(GameSave save, String orderId) {
        return byId(save, orderId)
                .filter(order -> order.paused)
                .map(order -> {
                    order.paused = false;
                    return true;
                })
                .orElse(false);
    }

    /**
     * Moves an order through the queue.
     *
     * <p>⚠ A move can displace whatever is downloading, and that is deliberate — "put this one
     * first" is the single most useful thing a queue offers, and a queue that refused to reorder
     * its head would be a list of things you cannot influence. Nothing is lost by it: the displaced
     * transfer is <em>held</em>, keeping its progress, and resumes from where it stopped when it
     * reaches the front again.
     *
     * @param delta how far, negative towards the front
     * @return whether anything changed
     */
    public static boolean move(GameSave save, String orderId, int delta) {
        if (save == null) {
            return false;
        }
        int from = indexOf(save, orderId);
        if (from < 0 || delta == 0) {
            return false;
        }
        int to = Math.max(0, Math.min(save.downloadQueue.size() - 1, from + delta));
        if (to == from) {
            return false;
        }
        DownloadOrderState order = save.downloadQueue.remove(from);
        save.downloadQueue.add(to, order);
        return true;
    }

    private static int indexOf(GameSave save, String orderId) {
        for (int i = 0; i < save.downloadQueue.size(); i++) {
            if (save.downloadQueue.get(i).orderId.equals(orderId)) {
                return i;
            }
        }
        return -1;
    }

    /** The transfer carrying an order, if it has started. */
    public static Optional<TaskState> taskFor(GameSave save, DownloadOrderState order) {
        if (save == null || order == null || !order.started()) {
            return Optional.empty();
        }
        return save.tasks.stream().filter(task -> task.taskId.equals(order.taskId)).findFirst();
    }

    /**
     * Starts what should be running and holds what should not.
     *
     * <h2>⚠ Called from the TICK and from {@code resume()}, with the tick's own delta</h2>
     *
     * {@code elapsed} is how much wall time has passed since the last pass — the same figure the
     * rest of the tick works from. Held transfers have both ends of their clock pushed forward by
     * it, which is what freezes their progress. Passing {@code Instant.now()} minus something else,
     * or passing a whole absence to the tick, are the two ways to get this wrong, and both look like
     * the pause working right up until the download finishes early.
     *
     * @param save the character
     * @param elapsed wall time since the last pass
     * @param now the session clock
     * @return whether anything changed
     */
    public static boolean settle(GameSave save, Duration elapsed, Instant now) {
        if (save == null || save.downloadQueue.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Optional<DownloadOrderState> running = active(save);

        for (DownloadOrderState order : save.downloadQueue) {
            boolean isActive = running.isPresent() && running.get().orderId.equals(order.orderId);
            if (isActive) {
                if (!order.started()) {
                    changed |= begin(save, order, now);
                }
                continue;
            }
            // ⚠ Held: push BOTH ends forward so the fraction complete does not move. Only a task
            // that has actually started can be held — an order still waiting its turn has no clock
            // to freeze, which is the cheap case and by far the common one.
            if (order.started() && !elapsed.isZero() && !elapsed.isNegative()) {
                Optional<TaskState> task = taskFor(save, order);
                if (task.isPresent()) {
                    task.get().startedAt = task.get().startedAt.plus(elapsed);
                    task.get().endsAt = task.get().endsAt.plus(elapsed);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Commissions the transfer for an order whose turn has come.
     *
     * <p>⚠ The noise is decided here from the order's stored {@code foreign}, not recomputed. A
     * queued purchase makes no racket because nothing is talking to anybody yet — the loudness
     * belongs to the transfer, and the transfer is only now starting.
     */
    private static boolean begin(GameSave save, DownloadOrderState order, Instant now) {
        TransferRules.Started started = order.isBundle()
                ? TransferRules.beginArchive(
                        save, order.fileName, order.entryId, order.bytes, order.memberItemTypes, now, order.foreign)
                : TransferRules.beginPurchase(
                        save, order.itemType, order.fileName, order.entryId, now, order.foreign);
        if (!started.succeeded()) {
            return false;
        }
        order.taskId = started.task().taskId;
        return true;
    }

    /**
     * Forgets an order whose transfer has landed.
     *
     * <p>⚠ Keyed on the TASK, not on the item, because a queue may hold two orders for things that
     * install as the same type — a bundle member and a separately bought copy — and removing "the
     * order for this item" would drop the wrong one.
     */
    public static boolean completed(GameSave save, TaskState task) {
        if (save == null || task == null) {
            return false;
        }
        return save.downloadQueue.removeIf(order -> task.taskId.equals(order.taskId));
    }

    /** @return how many are still owed, including whichever is downloading. */
    public static int outstanding(GameSave save) {
        return save == null ? 0 : save.downloadQueue.size();
    }
}
