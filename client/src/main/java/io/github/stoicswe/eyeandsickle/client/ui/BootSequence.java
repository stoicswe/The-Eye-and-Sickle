package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The uOS boot log, played once when a character is opened.
 *
 * <h2>Why a loading screen is worth building at all</h2>
 *
 * Solo loads in milliseconds — there is nothing to wait for. So this is not a progress indicator
 * pretending to be one; it is the moment the game establishes that <em>you are sitting down at a
 * machine</em>. Every line it prints is real: the rig's actual capacity, its actual Thermal Budget,
 * Memory Buffer and Bandwidth, its actual balance and armed defences, read from the save that was
 * just opened. A boot log with invented specs would be the first thing the game told the player, and
 * it would be a lie.
 *
 * <p>That is also what makes it a teaching surface. {@code docs/client/04-terminology-and-education.md}
 * builds the client's vocabulary out of real Unix; a boot sequence is where a real machine tells you
 * what it found, and this one uses the same facility names the log uses and the same figures the rig
 * monitor will show thirty seconds later.
 *
 * <h2>Motion: typing, not fading</h2>
 *
 * §5: "Text arrival — types in character by character; never fades." Lines arrive one at a time on
 * the shared {@link Pulse} driver. There is no opacity tween anywhere in here, and the "monitor
 * warm-up" is not a fade either — it is a small number of discrete brightness steps applied as style
 * classes, which is the same trick {@link Motion#reveal} uses and the only kind of ramp §5 permits.
 *
 * <p>Under reduced motion the whole thing is skipped: the player gets the deck immediately. A boot
 * sequence is atmosphere, and §5 makes atmosphere the first thing to go.
 *
 * <h2>Skippable, always</h2>
 *
 * Any key or click ends it. A cutscene a player cannot skip is one they resent by the third time,
 * and this one plays on <em>every</em> load.
 */
public final class BootSequence extends StackPane {

    /** Between lines. Fast enough not to be a wait, slow enough to read as a machine working. */
    private static final double LINE_MS = 55;

    /** A few lines pause slightly longer, where a real boot would actually be doing something. */
    private static final double BEAT_MS = 260;

    /**
     * The uOS mark.
     *
     * <p>Box-drawing characters only — §9 bans icon fonts and Material sets outright, and the
     * greeble already establishes this vocabulary. Rendered in the display face so it lines up:
     * every glyph here is full-width in a monospace cell.
     */
    private static final String[] LOGO = {
        "  ██  ██   ██████   ██████  ",
        "  ██  ██  ██    ██  ██      ",
        "  ██  ██  ██    ██  ██████  ",
        "  ██  ██  ██    ██      ██  ",
        "   ████    ██████   ██████  ",
    };

    private final VBox lines = new VBox(1);
    private final List<Line> script = new ArrayList<>();
    private final Runnable onFinished;
    private AutoCloseable ticker;
    private int cursor;
    private boolean finished;

    private record Line(String text, String styleClass, boolean beat) {}

    private BootSequence(Runnable onFinished) {
        this.onFinished = onFinished;
        getStyleClass().add("es-boot");
        setAlignment(Pos.CENTER);

        lines.getStyleClass().add("es-boot-body");
        lines.setAlignment(Pos.TOP_LEFT);
        lines.setMaxWidth(Region.USE_PREF_SIZE);
        lines.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(lines);

        setOnMouseClicked(e -> finish());
        setFocusTraversable(true);
        setOnKeyPressed(e -> finish());
    }

    /**
     * Builds the sequence for a session and starts it.
     *
     * @param onFinished called on the JavaFX thread when the log completes or is skipped. Called
     *     exactly once, whichever way it ends — a skip that silently dropped the callback would
     *     leave the player on a dead screen.
     */
    public static BootSequence play(GameSession session, Runnable onFinished) {
        BootSequence boot = new BootSequence(onFinished);
        boot.compose(session);
        boot.start();
        return boot;
    }

    /**
     * Writes the log from the session's real state.
     *
     * <p>Nothing below is a fixed string with a number in it for flavour. Every figure is read from
     * the rig that was just loaded, so a player who has upgraded their Thermal Budget sees it here
     * before they see it anywhere else.
     */
    private void compose(GameSession session) {
        var budget = session.computeBudget();
        var capacity = session.capacity();
        var mining = session.mining();
        long total = budget.total().cycles();
        long available = budget.available().cycles();
        int defenses = session.defenses().size();

        for (String row : LOGO) {
            script.add(new Line(row, "es-boot-logo", false));
        }
        script.add(new Line("", "es-boot-dim", false));
        script.add(new Line("uOS  ·  operator terminal  ·  local instance", "es-boot-dim", true));
        script.add(new Line("", "es-boot-dim", false));

        script.add(new Line("[  ok  ] mounting /rig", "es-boot-ok", false));
        script.add(new Line("         rig " + shortId(session) + " · capacity " + total + " cycles",
                "es-boot-dim", false));
        script.add(new Line("[  ok  ] compute ledger reconciled", "es-boot-ok", false));
        script.add(new Line("         " + available + " of " + total + " cycles available · "
                + budget.recovering().cycles() + " recovering", "es-boot-dim", true));

        script.add(new Line("[  ok  ] thermal budget T" + capacity.thermalBudget(), "es-boot-ok", false));
        script.add(new Line("[  ok  ] memory buffer  " + capacity.memoryBuffer() + " slot"
                + (capacity.memoryBuffer() == 1 ? "" : "s"), "es-boot-ok", false));
        script.add(new Line("[  ok  ] bandwidth      " + capacity.bandwidth() + " engagement"
                + (capacity.bandwidth() == 1 ? "" : "s"), "es-boot-ok", true));

        script.add(new Line(defenses == 0
                ? "[ warn ] defensive array: nothing armed"
                : "[  ok  ] defensive array: " + defenses + " armed",
                defenses == 0 ? "es-boot-warn" : "es-boot-ok", false));
        script.add(new Line(mining.selfMiningCycles() == 0
                ? "[ warn ] self-mining idle — the income floor is not running"
                : "[  ok  ] self-mining " + mining.selfMiningCycles() + " cycles",
                mining.selfMiningCycles() == 0 ? "es-boot-warn" : "es-boot-ok", false));

        // ⚠ Ethecoin.format, not a local "%.2f" — that was one of the thirteen private formatters, and
        // at 18 decimals a fixed two-place format would round a real amount away on the boot screen.
        script.add(new Line("[  ok  ] ledger " + session.balance(), "es-boot-ok", true));

        // The one line that is not reassuring. It is also true: this is a game about being watched,
        // and the client genuinely does not know whether anything is looking.
        script.add(new Line("[ ---- ] external interfaces: none configured", "es-boot-dim", false));
        script.add(new Line("[ ---- ] surveillance posture: unknown", "es-boot-dim", true));
        script.add(new Line("", "es-boot-dim", false));
        script.add(new Line("operator " + session.handle().toUpperCase(Locale.ROOT)
                + " authenticated against local keyring", "es-boot-text", false));
        script.add(new Line("uOS ready.", "es-boot-text", true));
    }

    private static String shortId(GameSession session) {
        String handle = session.handle();
        int hash = Math.abs(handle.hashCode() % 0xFFFF);
        return String.format(Locale.ROOT, "%04X", hash);
    }

    private void start() {
        if (Pulse.shared().reducedMotion()) {
            // Atmosphere is the first thing to go (§5). The player gets the deck immediately.
            finish();
            return;
        }
        ticker = Pulse.shared().animate(LINE_MS, this::next);
    }

    private int beatsRemaining;

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
        Line line = script.get(cursor++);
        Label label = new Label(line.text().isEmpty() ? " " : line.text());
        label.getStyleClass().addAll("es-boot-line", line.styleClass());
        lines.getChildren().add(label);
        if (line.beat()) {
            beatsRemaining = (int) Math.round(BEAT_MS / LINE_MS);
        }
    }

    /** Ends the sequence, exactly once. */
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

    /** The keybind hint that tells the player it is skippable. Shown from the first frame. */
    public Region hint() {
        Label label = Ui.label("Press any key to skip");
        label.getStyleClass().add("es-boot-hint");
        HBox box = new HBox(label);
        box.setAlignment(Pos.BOTTOM_CENTER);
        return box;
    }
}
