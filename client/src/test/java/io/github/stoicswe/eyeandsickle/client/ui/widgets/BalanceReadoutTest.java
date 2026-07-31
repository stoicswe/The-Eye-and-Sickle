package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The top strip's balance: short on the strip, exact on hover.
 *
 * <h2>⚠ What the abbreviation is allowed to cost, and what it must not</h2>
 *
 * At eighteen decimal places a real balance renders as {@code 1234.905777539252303541 EC}, which
 * pushed every other cell off the top strip. Shortening it is the fix — but a rounded amount somebody
 * <em>holds</em> is normally forbidden outright ({@code Ethecoin.formatApprox}), because a player
 * cannot tell a rounded balance from a wrong one.
 *
 * <p>What earns the exception is the tooltip. The exact figure is one hover away and moves with the
 * balance, so nothing is hidden. These tests hold both halves: the strip is short, and the hover is
 * exact. Losing the second half turns the first into the lie the rule was written about.
 */
class BalanceReadoutTest {

    /** Starts the toolkit, or skips — same convention as {@code NodeMenuTest}; see its note. */
    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch up = new CountDownLatch(1);
        try {
            Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        } catch (UnsupportedOperationException | NoClassDefFoundError | ExceptionInInitializerError headless) {
            Assumptions.abort("no display — the JavaFX toolkit cannot start here: " + headless.getMessage());
        }
        if (!up.await(20, TimeUnit.SECONDS)) {
            Assumptions.abort("the JavaFX toolkit did not start within 20s");
        }
    }

    private static <T> T onFxThread(java.util.function.Supplier<T> body) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(body.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("the FX thread threw", failure.get());
        }
        return result.get();
    }

    /** The awkward amount from the screenshot that started this. */
    private static final String LONG_BALANCE = "1234.905777539252303541";

    private static BalanceReadout seeded(String amount) {
        BalanceReadout readout = new BalanceReadout();
        // ⚠ The FIRST call seeds rather than animating, so the shown figure is the target and no
        // pulse is needed — which is what makes this testable without driving the animation.
        readout.setWei(Ethecoin.ofDecimal(amount).wei());
        return readout;
    }

    @Test
    @DisplayName("the strip shows four decimals, not eighteen")
    void theStripIsShort() throws Exception {
        String shown = onFxThread(() -> seeded(LONG_BALANCE).valueNode().getText());
        assertThat(shown).isEqualTo("1234.9058 EC");
    }

    @Test
    @DisplayName("the exact amount is on the tooltip, to every last wei")
    void theHoverIsExact() throws Exception {
        String tip = onFxThread(() -> {
            BalanceReadout readout = seeded(LONG_BALANCE);
            Tooltip installed = tooltipOf(readout);
            return installed == null ? null : installed.getText();
        });
        assertThat(tip)
                .as("the abbreviation is only legitimate because this is one hover away")
                .isEqualTo(LONG_BALANCE + " EC");
    }

    /**
     * ⚠ A screen reader has no strip and no hover, so it gets the exact figure.
     *
     * <p>The abbreviation is a space constraint on one readout, not a decision about how much a
     * player is allowed to know.
     */
    @Test
    @DisplayName("the accessible text carries the full amount")
    void theAccessibleTextIsExact() throws Exception {
        String spoken = onFxThread(() -> seeded(LONG_BALANCE).valueNode().getAccessibleText());
        assertThat(spoken).isEqualTo("Balance " + LONG_BALANCE + " EC");
    }

    @Test
    @DisplayName("an amount that needs no decimals still reads clean")
    void wholeAmountsStayClean() throws Exception {
        assertThat(onFxThread(() -> seeded("500").valueNode().getText())).isEqualTo("500 EC");
        assertThat(onFxThread(() -> seeded("0.05").valueNode().getText())).isEqualTo("0.05 EC");
    }

    private static Tooltip tooltipOf(BalanceReadout readout) {
        for (var property : readout.getProperties().values()) {
            if (property instanceof Tooltip tooltip) {
                return tooltip;
            }
        }
        // Tooltip.install stores it under a private key; fall back to walking for the label's own.
        Label value = readout.valueNode();
        return value.getTooltip();
    }
}
