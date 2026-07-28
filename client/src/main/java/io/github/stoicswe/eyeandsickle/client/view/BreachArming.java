package io.github.stoicswe.eyeandsickle.client.view;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which target the breach window is <b>pointed at</b>, shared across the windows that can point it.
 *
 * <h2>Aiming and firing are two steps, and they are two steps on purpose</h2>
 *
 * A breach reserves compute for the whole attempt and cannot be undone into a refund — aborting is a
 * sanctioned outcome, not a free one ({@code docs/design/05-hacking-minigame.md} §4). So the control
 * that <em>chooses</em> a target and the control that <em>commits</em> to one must not be the same
 * click. Before this existed the breach list began an attempt the instant a row's button was pressed,
 * which put an irreversible spend one mis-click away in a list that reflows whenever a sweep lands.
 *
 * <p>Arming is free, reversible, and says so. The single START BREACH control is the commitment, and
 * it is one control rather than one per row: there is exactly one thing that can be started, so there
 * should be exactly one button that starts it.
 *
 * <h2>Why it is a client object and not a rule</h2>
 *
 * Nothing here is game state. An armed target is an intention the player has not acted on — the
 * engine does not know it exists, it is not saved, and closing the window forgets it. The rules learn
 * about it exactly once, when {@code beginBreach} is called with an id, and they refuse or accept on
 * their own terms as always (Invariant <b>I14</b>). If this object ever grows a method that answers
 * "can I breach this", that answer has escaped the engine.
 *
 * <h2>It also carries the door</h2>
 *
 * {@link #open} is how the network map raises the breach window without knowing that a desk, a window
 * registry or a {@code WindowSpec} exist. The alternative was handing {@code NetMapView} the deck,
 * which would make a panel that renders a graph also a thing that manages windows.
 */
public final class BreachArming {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private String targetId = "";
    private Runnable opener = () -> {};

    /**
     * Points the breach window at a target id, or clears it with {@code ""}.
     *
     * <p>Takes the {@code targetId} the rules publish ({@code "node:10.0.0.4"}, {@code "miner:<uuid>"})
     * rather than an address, because that is what {@code beginBreach} takes and a client-side
     * translation between the two would be a second place that could get the prefix wrong.
     */
    public void arm(String targetId) {
        String wanted = targetId == null ? "" : targetId;
        if (wanted.equals(this.targetId)) {
            return;
        }
        this.targetId = wanted;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /**
     * Points at a target and <b>always notifies</b>, even if it was already the armed one.
     *
     * <h2>⚠ Why {@link #arm} cannot just do this</h2>
     *
     * {@code arm} deliberately no-ops on an unchanged id, because it is called from inside the breach
     * panel's own refresh — {@code arm("")} runs there whenever an armed target stops being
     * available — and a notification on every refresh would re-enter the refresh that sent it.
     *
     * <p>But the network map's BREACH control is a different statement. It means <em>start fresh on
     * this machine</em>, and a player who presses it twice on the same node means it twice. Under
     * the no-op the second press changed nothing and the breach panel never heard about it, so a
     * resolved outcome from a previous attempt stayed on screen with no way past it but Dismiss.
     */
    public void rearm(String targetId) {
        this.targetId = targetId == null ? "" : targetId;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /** The armed id, or {@code ""}. Never null. */
    public String armed() {
        return targetId;
    }

    public boolean isArmed() {
        return !targetId.isEmpty();
    }

    /** Raises the breach window. Does nothing before the deck has wired {@link #setOpener}. */
    public void open() {
        opener.run();
    }

    /** Called once by the client, after the deck exists. */
    public void setOpener(Runnable opener) {
        this.opener = opener == null ? () -> {} : opener;
    }

    /**
     * Registers a listener called whenever the armed target changes.
     *
     * @return a handle that removes it — held and closed by the panel, because a view that outlives
     *     its own subscription is the leak {@code NetMapView} was fixed for
     */
    public AutoCloseable onChange(Runnable listener) {
        if (listener == null) {
            return () -> {};
        }
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
