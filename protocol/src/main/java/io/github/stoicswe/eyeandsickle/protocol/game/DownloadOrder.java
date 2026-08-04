package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Duration;
import java.util.List;

/**
 * One paid-for download, as the storefront sees it.
 *
 * <h2>⚠ RESULTS, never the rules that produced them</h2>
 *
 * A fraction and a remaining duration, not a byte rate, a start instant or a queue policy. The
 * client draws a bar; it does not compute one. Sending the endpoints and letting the view do the
 * arithmetic would put a second copy of the transfer model in the client, and the two would
 * disagree the moment a download was held — which is exactly the case the readout exists for.
 *
 * <h2>⚠ {@code active} is DERIVED server-side and is not a stored flag</h2>
 *
 * The active download is the first order that is not paused. That is decided by the rules, once,
 * and carried here — a client that worked it out itself would need the queue policy, and a client
 * holding the policy is one refactor from predicting it wrongly.
 *
 * @param orderId what pause, resume and reorder name
 * @param label what to call it on screen
 * @param bytes the size of the download
 * @param progress 0–1, frozen while held
 * @param remaining how much longer at the current rate; zero when nothing is moving
 * @param paused whether the player stopped this one
 * @param active whether this is the one actually progressing
 * @param bundle whether it is an archive of several packages
 * @param memberNames what is inside a bundle, for the readout. Empty for a single item.
 */
public record DownloadOrder(
        String orderId,
        String label,
        long bytes,
        double progress,
        Duration remaining,
        boolean paused,
        boolean active,
        boolean bundle,
        List<String> memberNames) {

    public DownloadOrder {
        memberNames = List.copyOf(memberNames);
    }

    /**
     * @return whether this one has not started yet — neither moving nor deliberately held, just
     *     waiting its turn. The three states are distinct on screen and a queue that showed only
     *     two would render "waiting" and "paused" identically, which is the one distinction the
     *     player's own action created.
     */
    public boolean waiting() {
        return !active && !paused;
    }
}
