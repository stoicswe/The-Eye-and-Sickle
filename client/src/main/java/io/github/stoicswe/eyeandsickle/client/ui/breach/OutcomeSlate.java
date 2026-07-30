package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachResolution;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.ResolutionRecord;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * How it ended, itemised.
 *
 * <h2>Nothing on this panel moves, ever</h2>
 *
 * Every other surface in the breach has an argument for why its motion is a readout (D-6). This one
 * has none: the attempt is over, there is nothing left to read out, and a resolution that animates
 * is a resolution the player is waiting on instead of reading. There is no {@code Pulse}
 * subscription in this file and there should never be one.
 *
 * <h2>ABORTED is neutral, and that is a design decision with teeth</h2>
 *
 * {@code docs/client/01-visual-language.md} §2.2.7: aborting is "the sanctioned escape hatch when a
 * read goes wrong; a UI that paints it red teaches players not to use the tool the design gave
 * them." So {@code -es-outcome-aborted} resolves to {@code -es-dim-1} — the same weight as any other
 * settled figure. A player who walks away from a bad board has played correctly and the slate should
 * not tell them otherwise.
 *
 * <h2>The one amber element in the whole feature</h2>
 *
 * Architect's decision D-7: {@code -es-amber} means live/earning, a breach is not earning, and the
 * single exception is the extracted yield on a successful crack — money that has just moved into the
 * player's balance, which is exactly what the accent reservation in §2.1 is for. It appears on no
 * other outcome, on no other line, and not at all when nothing was taken.
 *
 * <h2>⚠ A failure must always print a consequence</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4.1 itemises what a loss costs, and the implementation
 * spec makes an empty consequence list on a {@link BreachOutcome#FAILED} resolution "a bug, not a
 * quiet outcome". The engine is responsible for filling it; this panel is responsible for not
 * hiding the hole if it does not, so an empty list on a failure prints a line saying the record is
 * incomplete rather than rendering a clean-looking slate that lies.
 */
public final class OutcomeSlate extends VBox {

    /** Width of the stamp, in characters. Wide enough for the spaced-out word plus its frame. */
    private static final int STAMP_COLS = 46;

    private static final int STAMP_ROWS = 3;

    private final AsciiCanvas stamp = new AsciiCanvas(STAMP_ROWS, STAMP_COLS);
    private final GridPane facts = new GridPane();
    private final VBox yield = new VBox(UiTokens.SPACE_1);
    private final VBox consequences = new VBox(UiTokens.SPACE_1);

    public OutcomeSlate() {
        super(UiTokens.SPACE_5);
        getStyleClass().add("es-slate");
        facts.setHgap(UiTokens.SPACE_6);
        facts.setVgap(UiTokens.SPACE_1);
        getChildren().addAll(stamp, facts, yield, consequences);
    }

    /**
     * @param snapshot must be resolved; a live or null snapshot leaves the slate blank rather than
     *     rendering half a verdict
     */
    public void show(BreachSnapshot snapshot) {
        facts.getChildren().clear();
        yield.getChildren().clear();
        consequences.getChildren().clear();
        getStyleClass().removeAll("es-slate-breached", "es-slate-failed", "es-slate-aborted");

        if (snapshot == null || !snapshot.resolved()) {
            stamp.clear();
            stamp.paint();
            return;
        }
        BreachResolution resolution = snapshot.resolution();
        ResolutionRecord record = resolution.record();
        BreachOutcome outcome = record.outcome();

        stampFor(outcome);
        getStyleClass().add(slateClass(outcome));

        int probes = 0;
        for (BreachLayer layer : snapshot.layers()) {
            probes += layer.probesUsed();
        }
        var attention = snapshot.totalAttention();

        // Two columns of key:value, §4's readout component. Units always present, and the right-hand
        // column is the cost side — which is the column a player re-reads after a loss.
        row(0, "Class", record.puzzleClass().name(), "Noise", Integer.toString(resolution.noiseGenerated()));
        row(1, "Tier", "T" + record.difficultyTier().tier(),
                "Attention", attention.spent() + " / " + attention.budget()
                        + "  (" + Math.round(resolution.traceProgress() * 100) + "%)");
        row(2, "Target", record.liveOrDormant().name(), "Probes", Integer.toString(probes));
        // Heat is printed even when it is zero, and especially then: Invariant I9 makes a miner
        // crack cost zero heat on EVERY outcome including failure, and a player who never sees the
        // zero has no way to learn that the safest attempt in the game is the one on their own rig.
        row(3, "Heat gained", resolution.heatGained() + (snapshot.minerCrack() ? "  (own rig)" : ""),
                "Cycles released", Long.toString(snapshot.reservedCycles()));

        if (resolution.lootWei().signum() > 0) {
            Label extracted = Ui.value(Ethecoin.format(resolution.lootWei()));
            // D-7. The only amber in the breach. Do not add a second use of this class.
            extracted.getStyleClass().add("es-breach-extract");
            HBox line = Ui.row(UiTokens.SPACE_4, Ui.label("Extracted"), extracted,
                    Ui.small(resolution.lootLabel()));
            line.setAlignment(Pos.BASELINE_LEFT);
            yield.getChildren().add(line);
        } else if (!resolution.lootLabel().isBlank()) {
            yield.getChildren().add(Ui.row(UiTokens.SPACE_4,
                    Ui.label("Salvaged"), Ui.value(resolution.lootLabel())));
        }
        if (resolution.schematicMaterial() > 0) {
            // Tier-gated partial progress (I13). Printed as a plain value, never as a reward flourish
            // — 02 §2.2 makes this a slow accumulation and dressing it up would misprice it.
            yield.getChildren().add(Ui.row(UiTokens.SPACE_4,
                    Ui.label("Material"),
                    Ui.value("+" + resolution.schematicMaterial() + " SCHEMATIC UNIT"
                            + (resolution.schematicMaterial() == 1 ? "" : "S"))));
        }

        consequences.getChildren().add(Ui.label("Consequences"));
        List<String> lines = resolution.consequences();
        if (lines.isEmpty() && outcome == BreachOutcome.FAILED) {
            // See the class comment. Loudly incomplete beats quietly clean.
            consequences.getChildren().add(io.github.stoicswe.eyeandsickle.client.ui.widgets.Note.loss(
                    "Consequence record incomplete.",
                    "The attempt failed but nothing was itemised. Report this."));
        } else if (lines.isEmpty()) {
            consequences.getChildren().add(Ui.small("None."));
        } else {
            for (String line : lines) {
                Label item = Ui.body(AsciiCanvas.BULLET + " " + line);
                item.getStyleClass().add("es-slate-consequence");
                item.setWrapText(true);
                consequences.getChildren().add(item);
            }
        }

        setAccessibleText(describe(snapshot, resolution, outcome, probes));
    }

    /**
     * The stamp.
     *
     * <p>Letter-spaced by hand — {@code B R E A C H E D} — because JavaFX has no
     * {@code letter-spacing} property and this is one of the two places in the client that wants it.
     * Doing it in the string rather than in CSS is also what keeps the word centred inside a
     * character grid whose columns are exact.
     */
    private void stampFor(BreachOutcome outcome) {
        stamp.clear();
        int ink = outcome == BreachOutcome.FAILED ? AsciiCanvas.INK_ALARM
                : outcome == BreachOutcome.BREACHED ? AsciiCanvas.INK_LIVE
                : AsciiCanvas.INK_DIM;
        stamp.box(0, 0, STAMP_ROWS, STAMP_COLS, ink);
        StringBuilder spaced = new StringBuilder();
        for (char c : outcome.name().toCharArray()) {
            if (!spaced.isEmpty()) {
                spaced.append(' ');
            }
            spaced.append(c);
        }
        stamp.centre(1, 1, STAMP_COLS - 2, spaced.toString(), ink);
        stamp.paint();
        stamp.setAccessibleText("Outcome: " + outcome.name().toLowerCase(Locale.ROOT));
    }

    private void row(int index, String leftKey, String leftValue, String rightKey, String rightValue) {
        facts.add(key(leftKey), 0, index);
        facts.add(Ui.value(leftValue), 1, index);
        facts.add(key(rightKey), 2, index);
        facts.add(Ui.value(rightValue), 3, index);
    }

    private static Label key(String text) {
        Label label = Ui.label(text);
        label.getStyleClass().add("es-slate-key");
        return label;
    }

    private static String slateClass(BreachOutcome outcome) {
        return switch (outcome) {
            case BREACHED -> "es-slate-breached";
            case FAILED -> "es-slate-failed";
            case ABORTED -> "es-slate-aborted";
        };
    }


    private static String describe(BreachSnapshot snapshot, BreachResolution resolution,
            BreachOutcome outcome, int probes) {
        StringBuilder out = new StringBuilder("Breach ")
                .append(outcome.name().toLowerCase(Locale.ROOT))
                .append(" on ").append(snapshot.targetLabel())
                .append(". Attention ").append(snapshot.totalAttention().spent())
                .append(" of ").append(snapshot.totalAttention().budget())
                .append(", ").append(probes).append(" probes, noise ")
                .append(resolution.noiseGenerated())
                .append(", heat gained ").append(resolution.heatGained()).append(". ");
        if (resolution.lootWei().signum() > 0) {
            out.append("Extracted ").append(Ethecoin.format(resolution.lootWei())).append(". ");
        }
        for (String line : resolution.consequences()) {
            out.append(line).append(". ");
        }
        return out.toString();
    }

    /** The stamp as text. Test seam. */
    public String frame() {
        return stamp.frame();
    }
}
