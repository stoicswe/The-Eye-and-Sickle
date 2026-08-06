package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The capacity upgrade: an apt-shaped upgrade log, then a reboot.
 *
 * <h2>Why this is its own screen rather than the firmware flash overlay</h2>
 *
 * A firmware flash freezes one tool and shows a progress bar in the panel that owns it, because what
 * changed is that tool. A capacity upgrade changes <b>the rig</b> — every readout in the client is
 * denominated in cycles, and the compute grid is a different shape afterwards. A progress bar in a
 * corner would undersell the one moment in the game where the machine the player has been managing
 * becomes a bigger machine.
 *
 * <p>⚠ It is also the honest fiction: you do not hot-swap a capacity board. The log names the steps
 * a real package upgrade names — unpack, configure, write, verify — and then the machine goes down
 * and comes back. {@code BootSequence} is what comes back.
 *
 * <h2>⚠ REDUCED MOTION SKIPS TO THE END, and the upgrade still happens</h2>
 *
 * The same rule {@code BootSequence} follows: atmosphere is the first thing to go (§5), and the
 * player gets the deck immediately. What must never be conditional is the <em>effect</em> — the
 * capacity is raised by the rules on the tick, not by this animation, so a player who never sees a
 * frame of it is at 32 cycles exactly as fast as one who watches the whole thing. An animation that
 * owned the state change would be an accessibility setting that costs a purchase.
 */
public final class RebootSequence extends StackPane {

    /** How long one line holds. Slower than the boot log — an upgrade is meant to feel deliberate. */
    private static final double LINE_MS = 70;

    /** A pause, in line-ticks, for the moments a real upgrade actually stops at. */
    private static final double BEAT_MS = 320;

    private final List<Line> script = new ArrayList<>();
    private final VBox lines = new VBox(1);
    private final Runnable onFinished;
    private AutoCloseable ticker;
    private boolean finished;
    private int cursor;
    private int beatsRemaining;

    private record Line(String text, String styleClass, boolean beat) {}

    private RebootSequence(Runnable onFinished) {
        this.onFinished = onFinished;
        getStyleClass().add("es-boot");
        lines.getStyleClass().add("es-boot-lines");
        setAlignment(Pos.TOP_LEFT);
        getChildren().add(lines);
    }

    /**
     * Builds and starts the sequence for one rung.
     *
     * @param from the ceiling before
     * @param to the ceiling after
     * @param name the upgrade's display name
     * @param onFinished called on the FX thread exactly once, however it ends — a skip that dropped
     *     the callback would leave the player on a dead screen with a rig they cannot reach
     */
    public static RebootSequence play(long from, long to, String name, Runnable onFinished) {
        RebootSequence reboot = new RebootSequence(onFinished);
        reboot.compose(from, to, name);
        reboot.start();
        return reboot;
    }

    /**
     * The log.
     *
     * <h2>⚠ THE NUMBERS ARE THE REAL ONES, which is the same rule {@code BootSequence} follows</h2>
     *
     * Nothing below is a fixed string with a plausible number in it. The ceilings are what the rules
     * actually moved between, so a player reading the log is reading their own rig — and a log that
     * said 32 while the rig went to 48 would be the client inventing state, which is the one thing
     * {@code docs/client/00} pillar C4 forbids everywhere else.
     */
    private void compose(long from, long to, String name) {
        line("uOS capacity upgrade", "es-boot-logo", false);
        blank();
        line("Reading rig manifest...", "es-boot-dim", false);
        line("Building dependency tree...", "es-boot-dim", true);
        blank();
        line("The following capacity will be installed:", "es-boot-dim", false);
        line("  " + name, "es-boot-ok", false);
        blank();
        line("Compute ceiling " + from + "C -> " + to + "C  (+" + (to - from) + "C)", "es-boot-dim", true);
        blank();
        line("Unpacking " + name + " ...", "es-boot-dim", false);
        line("Setting up capacity board ...", "es-boot-dim", false);
        line("Writing controller firmware ...", "es-boot-dim", true);
        line("[  ok  ] firmware written", "es-boot-ok", false);
        line("[  ok  ] checksum verified", "es-boot-ok", true);
        blank();
        // ⚠ The one warning, and it is true: every allocation is released, so a rig that was mining
        // stops mining. Saying so here is cheaper than a player discovering it from a flat income
        // graph an hour later.
        line("A restart is required to complete the upgrade.", "es-boot-warn", false);
        line("Releasing compute allocations ...", "es-boot-dim", true);
        blank();
        line("Rebooting.", "es-boot-dim", true);
    }

    private void line(String text, String style, boolean beat) {
        script.add(new Line(text, style, beat));
    }

    private void blank() {
        script.add(new Line("", "es-boot-dim", false));
    }

    private void start() {
        if (Pulse.shared().reducedMotion()) {
            // ⚠ Straight to the end. The UPGRADE is not conditional on this — the rules raised the
            // ceiling on the tick — so an accessibility setting costs the player nothing but the
            // theatre. See the class note.
            finish();
            return;
        }
        ticker = Pulse.shared().animate(LINE_MS, this::next);
    }

    private void next() {
        if (finished) {
            return;
        }
        if (beatsRemaining > 0) {
            beatsRemaining--;
            return;
        }
        if (cursor >= script.size()) {
            finish();
            return;
        }
        Line current = script.get(cursor++);
        Label label = new Label(current.text().isEmpty() ? " " : current.text());
        label.getStyleClass().addAll("es-boot-line", current.styleClass());
        lines.getChildren().add(label);
        if (current.beat()) {
            beatsRemaining = (int) Math.round(BEAT_MS / LINE_MS);
        }
    }

    /** Ends it, exactly once, however it ended. */
    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            ticker = null;
        }
        onFinished.run();
    }

    /** The skip hint, shown from the first frame — the same affordance the boot log has. */
    public Region hint() {
        Label label = Ui.label("Press any key to skip");
        label.getStyleClass().add("es-boot-hint");
        HBox box = new HBox(label);
        box.setAlignment(Pos.BOTTOM_RIGHT);
        return box;
    }

    /** Every line composed, for a test that must not start a toolkit. */
    List<String> scriptText() {
        return script.stream().map(Line::text).toList();
    }
}
