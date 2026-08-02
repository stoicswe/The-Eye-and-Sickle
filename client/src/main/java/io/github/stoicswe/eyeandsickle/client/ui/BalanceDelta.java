package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.math.BigInteger;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * The money that just moved, shown under the balance rather than beside it.
 *
 * <h2>⚠ Why it left the strip cell</h2>
 *
 * It used to be a third {@code Label} inside {@code BalanceReadout}'s row, and that made the balance
 * cell <b>wider for as long as it was showing</b>. The top strip is a single row of cells with a
 * fixed width budget, so a movement of a few hundred EC pushed the strip past its budget and wrapped
 * it onto two rows — the chrome doubling in height every time the player earned anything, then
 * springing back a second and a half later. The same class of defect as the empty refusal cell, and
 * the same fix: <b>nothing transient may occupy space in the strip.</b>
 *
 * <p>So this is an overlay hanging off the cell instead. The strip's width no longer depends on
 * whether money has moved recently, which is the property that has to hold.
 *
 * <h2>What did NOT change</h2>
 *
 * The counting animation. {@code BalanceReadout} still steps the figure to its new value on
 * {@link Pulse} — that is the readout doing its job and is unrelated to where the delta is drawn.
 * This shows only the movement, and it is still the one licensed use of the gain/loss colours
 * ({@code ui-design-language.md} §2.1a): transient, confined to money, and never the only cue —
 * the sign is written out.
 *
 * <h2>⚠ Held under reduced motion, not faded</h2>
 *
 * Which way the money went is <b>information</b>. §5 asks for the static final state, so under
 * reduced motion the chip stays put until the next movement replaces it rather than stepping away.
 */
public final class BalanceDelta extends StackPane {

    /** How long it sits before it starts stepping away. */
    private static final double HOLD_MS = 1400;

    private final Label chip = Ui.micro("");

    private Node xAnchor;
    private Node yAnchor;
    private AutoCloseable flash;

    public BalanceDelta() {
        getStyleClass().add("es-balance-pop");
        setAlignment(Pos.CENTER_RIGHT);
        setVisible(false);
        // ⚠ Unmanaged, so Anchoring can place it by translate — and therefore it must resize itself.
        // See Anchoring's class comment for why that is not optional.
        setManaged(false);
        setMouseTransparent(true);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        chip.getStyleClass().add("es-balance-delta");
        getChildren().add(chip);
    }

    /** Points it at the cell it belongs under. Called once, when the strip is built. */
    public void anchorTo(Node cell, Node strip) {
        this.xAnchor = cell;
        this.yAnchor = strip;
        Anchoring.watch(this, cell, strip, this::reposition);
    }

    /** Shows a movement, then steps it away. */
    public void show(BigInteger change) {
        if (change == null || change.signum() == 0 || xAnchor == null) {
            return;
        }
        stop();
        chip.setText((change.signum() >= 0 ? "+" : "−") + Ethecoin.format(change.abs()));
        chip.getStyleClass().removeAll("es-balance-gain", "es-balance-loss");
        chip.getStyleClass().add(change.signum() >= 0 ? "es-balance-gain" : "es-balance-loss");
        setVisible(true);
        setOpacity(1);
        // CSS first, or there is no preferred size to measure — see Anchoring.
        applyCss();
        reposition();

        if (Pulse.shared().reducedMotion()) {
            return;
        }
        int[] frame = {0};
        int hold = (int) Math.round(HOLD_MS / UiTokens.FRAME_MS);
        flash = Pulse.shared().animate(UiTokens.FRAME_MS, () -> {
            frame[0]++;
            if (frame[0] <= hold) {
                return;
            }
            // Nine whole steps down, the same ladder Motion uses. Never a tween (§5).
            double opacity = 1 - (frame[0] - hold) / (double) UiTokens.REVEAL_STEPS;
            if (opacity <= 0) {
                setVisible(false);
                stop();
                return;
            }
            setOpacity(opacity);
        });
    }

    private void reposition() {
        if (isVisible()) {
            Anchoring.place(this, xAnchor, yAnchor);
        }
    }

    private void stop() {
        if (flash == null) {
            return;
        }
        try {
            flash.close();
        } catch (Exception ignored) {
            // Unsubscribing cannot fail, and a failed one is not something a player can act on.
        }
        flash = null;
    }

    /** Stops the driver. Called by {@code DeckShell.dispose}. */
    public void dispose() {
        stop();
    }
}
