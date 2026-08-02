package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * The balance, counting to its new value, with the movement that caused it flashed beside it.
 *
 * <h2>Why the number counts instead of jumping</h2>
 *
 * A balance that snaps from 195.01 to 355.89 tells the player it changed and not by how much — they
 * would have had to be holding the old figure in their head. Counting names the size of the movement
 * with the movement itself, which is the one thing a single number cannot do. It is also how a
 * mechanical register behaves, which is the right register for a machine that draws its own chrome.
 *
 * <h2>⚠ Stepped, not tweened — §5 permits nothing else</h2>
 *
 * {@code UiContractTest} fails the build on any {@code Interpolator.EASE_*} and on {@code LINEAR}
 * outside the sweep bar, so the count runs on {@link Pulse} and advances by whole steps. That suits
 * it: a counter that interpolated would render fractional minor units the ledger never contained.
 *
 * <p>⚠ Under reduced motion the value <b>snaps and the flash is still shown</b>, held rather than
 * faded. §5 wants the static final state, and the delta is <em>information</em> — which way the
 * money went — not decoration. Dropping it would take a fact away from the player who asked for
 * less movement, which §5 explicitly does not license.
 *
 * <h2>⚠ Two colours, and they are a documented exception to §2.1</h2>
 *
 * §2.1 bans a semantic colour system and §9 makes it build-blocking. The delta uses {@code -es-gain}
 * for a credit and {@code -es-alarm} for a debit under the narrow carve-out logged in
 * {@code ui-design-language.md} §2.1a: it is <b>transient</b>, it is confined to the money delta and
 * the network node states, and it never colours a persistent readout. The steady balance keeps the
 * amber it has always had. Note the debit reuses {@code alarm} rather than adding a red — alarm
 * already means loss, so only one new hue enters the palette rather than two.
 */
public final class BalanceReadout extends HBox {

    /** How long the count takes, whatever the distance. A fixed duration reads as one gesture. */
    private static final double COUNT_MS = 520;

    /** How long the delta sits before it starts stepping away. */
    private static final double FLASH_HOLD_MS = 1400;

    private final Label value = Ui.value("—");

    private java.math.BigInteger shownWei = java.math.BigInteger.ZERO;
    private java.math.BigInteger targetWei = java.math.BigInteger.ZERO;
    private boolean seeded;

    private AutoCloseable counter;

    /**
     * Where a movement goes to be drawn. Does nothing until the deck wires it up.
     *
     * <p>A sink rather than a reference to the overlay, so this widget stays buildable on its own —
     * a hard dependency on a node that lives in the deck's overlay layer would make it untestable
     * without a whole deck around it.
     */
    private java.util.function.Consumer<java.math.BigInteger> onDelta = change -> {};

    public BalanceReadout() {
        super(UiTokens.SPACE_3);
        setAlignment(Pos.BASELINE_LEFT);
        Label key = Ui.label("Balance");
        key.getStyleClass().add("es-kv-key");
        value.getStyleClass().add("es-balance-value");
        // ⚠ The exact figure lives in a tooltip on the WHOLE readout, not on the number alone: the
        // strip's cells are small and a player reaching for "Balance" should get the same answer as
        // one reaching for the digits.
        exact.setShowDelay(javafx.util.Duration.millis(200));
        javafx.scene.control.Tooltip.install(this, exact);
        getChildren().addAll(key, value);
    }

    /**
     * How many decimals the strip shows.
     *
     * <h2>⚠ This ROUNDS a held amount, which {@code Ethecoin.formatApprox} otherwise forbids</h2>
     *
     * That rule exists because a rounded balance is a lie the player cannot detect. The exception is
     * earned here by the tooltip: the exact wei figure is one hover away and updates with the
     * balance, so nothing is hidden — it is abbreviated, with the full value on demand.
     *
     * <p>Four places, because at eighteen a real balance renders as
     * {@code 1234.905777539252303541 EC} and pushes every other cell off the top strip. The strip is
     * a glanceable readout; the LEDGER is where an exact amount is read, and it is still exact.
     */
    private static final int STRIP_DECIMALS = 4;

    /** The exact amount, for the hover. Never abbreviated. */
    private final javafx.scene.control.Tooltip exact = new javafx.scene.control.Tooltip();

    /**
     * Renders the counting figure short, and points the tooltip at the true one.
     *
     * <p>⚠ The tooltip tracks {@link #targetWei}, not {@link #shownWei}. Mid-count the shown figure
     * is a step on the way to the balance rather than the balance, and a hover that reported it
     * would be exact about a number that is not the player's.
     */
    private void render() {
        value.setText(Ethecoin.formatApprox(shownWei, STRIP_DECIMALS));
        String full = Ethecoin.format(targetWei);
        exact.setText(full);
        // Screen readers get the exact figure too: the abbreviation is a space constraint on the
        // strip, and a reader has no strip.
        value.setAccessibleText("Balance " + full);
    }

    /** The label the strip marks live when income is flowing. */
    public Label valueNode() {
        return value;
    }

    /**
     * Points the readout at a new balance.
     *
     * <p>⚠ The first call <b>seeds</b> rather than animating. Opening the deck on a 4 000 EC save
     * would otherwise count up from zero and announce four thousand ethecoin of income that did not
     * happen — the flash is for movement, and arriving is not movement.
     */
    public void setWei(java.math.BigInteger wei) {
        if (!seeded) {
            seeded = true;
            shownWei = wei;
            targetWei = wei;
            render();
            return;
        }
        if (wei.equals(targetWei)) {
            return;
        }
        java.math.BigInteger change = wei.subtract(targetWei);
        targetWei = wei;
        showDelta(change);

        if (Pulse.shared().reducedMotion()) {
            shownWei = targetWei;
            render();
            return;
        }
        startCount();
    }

    /**
     * Steps the shown figure toward the target on the shared driver.
     *
     * <p>⚠ The step is recomputed from the <b>remaining</b> distance every frame rather than fixed
     * at the start, so a second movement landing mid-count is absorbed instead of fighting it. A
     * fixed step would overshoot the moment two payouts arrived a frame apart, and mining pays in
     * bursts — this is not a rare case.
     */
    private void startCount() {
        stop(counter);
        java.math.BigInteger startedFrom = shownWei;
        int[] frame = {0};
        int frames = Math.max(1, (int) Math.round(COUNT_MS / UiTokens.FRAME_MS));
        counter = Pulse.shared().animate(UiTokens.FRAME_MS, () -> {
            frame[0]++;
            double progress = Math.min(1.0d, frame[0] / (double) frames);
            // ⚠ The interpolation runs in BigDecimal. `span * progress` in double would round the
            // amount to ~16 significant figures every frame, and at 18 decimals that is visible in
            // the digits the readout now prints — the counter would tick through nonsense.
            java.math.BigDecimal span = new java.math.BigDecimal(targetWei.subtract(startedFrom));
            shownWei = startedFrom.add(span.multiply(java.math.BigDecimal.valueOf(progress))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .toBigIntegerExact());
            render();
            if (progress >= 1.0d) {
                // Pinned to the target rather than left on the rounded step. A readout a minor unit
                // off the ledger is the exact disagreement docs/design/04 §3.1 teaches players to
                // read as evidence of an intruder.
                shownWei = targetWei;
                render();
                stop(counter);
                counter = null;
            }
        });
    }

    /**
     * Hands the movement to whoever is drawing it.
     *
     * <h2>⚠ The delta is NOT a child of this row any more (2026-08-02)</h2>
     *
     * It was, and that made the balance cell wider for as long as it showed — which pushed the top
     * strip past its width budget and wrapped it onto a second row every time the player earned
     * anything, then sprang back a second and a half later. Nothing transient may occupy space in the
     * strip. {@code ui/BalanceDelta} draws it as an overlay hanging under the cell instead, so the
     * strip's width no longer depends on whether money has moved recently.
     *
     * <p>The counting animation stayed here, because that is this readout doing its job: the figure
     * still steps to its new value on {@link Pulse}, and where the delta chip is drawn has nothing to
     * do with it.
     */
    private void showDelta(java.math.BigInteger change) {
        onDelta.accept(change);
    }

    private static void stop(AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception ignored) {
            // Nothing to recover: the subscription is going away and a failed unsubscribe is not
            // something a player can act on.
        }
    }

    /** Points movements at the overlay that draws them. */
    public void setOnDelta(java.util.function.Consumer<java.math.BigInteger> sink) {
        this.onDelta = sink == null ? change -> {} : sink;
    }

    /** Stops the counter. Called by {@code DeckShell.dispose}. */
    public void dispose() {
        stop(counter);
        counter = null;
    }
}
