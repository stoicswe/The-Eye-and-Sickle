package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Note;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.TargetState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * What there is to breach, and what each one would cost.
 *
 * <h2>This list is the decision, so it prints the decision's inputs</h2>
 *
 * Choosing a target is the only part of a breach that happens before any attention is spent, and it
 * is where the two rules a new player most needs are actually actionable. Both are printed on the row
 * they apply to rather than in a help page:
 *
 * <ul>
 *   <li><b>A crack is free to lose.</b> Invariant <b>I9</b> — defending your own rig never generates
 *       heat — makes a foreign miner squatting on your own rig the safest possible first breach, on
 *       every outcome including failure. {@code docs/design/04-mining.md} §5.1 is why the tutorial
 *       plants one.
 *   <li><b>A dormant target cannot unlock anything.</b> {@code docs/design/02-unlock-gates.md} §2.4
 *       and Invariant <b>I7</b>: proof-of-skill counts a class solved at a tier against a
 *       <em>live or defended</em> target. Loot is still loot; it is just never progress.
 * </ul>
 *
 * <h2>Firewall tier 0 means "nothing established", not "nothing there"</h2>
 *
 * {@link BreachTarget#firewallTier()} is what recon has managed to establish, and
 * {@code docs/design/07-recon-tools.md} §2 makes a target dormant-until-analysed rather than
 * safe-until-analysed. Rendering an unestablished tier as {@code T0} would read as a defended machine
 * reporting no defences, which is the one misreading that gets a player killed on their second
 * breach — so it says so in words.
 *
 * <h2>Why the rows are not rebuilt on every change</h2>
 *
 * The session fires {@code onChange} whenever anything moves, which under self-mining is most ticks.
 * Rebuilding a focusable list on each of those would drop keyboard focus out from under anyone
 * tabbing through it. {@link BreachTarget} is a record, so a whole-list equality check is a cheap and
 * exact "did anything a player can see change", and it is the gate on touching the scene graph at
 * all.
 */
public final class BreachTargetList extends VBox {

    private final GameSession session;
    private final VBox rows = new VBox(UiTokens.HAIR);
    private final KeyValue count = KeyValue.of("Targets", "0");

    private List<BreachTarget> current = List.of();
    private Consumer<BreachTarget> onBegin = target -> {};

    public BreachTargetList(GameSession session) {
        super(UiTokens.SPACE_5);
        this.session = session;

        Label heading = Ui.label("Breach targets");
        heading.getStyleClass().add("es-kv-key");

        Label intro = Ui.small(
                "A breach reserves its compute for the whole attempt and releases it into thermal "
                        + "recovery when the attempt ends, however it ends. There is no clock: every "
                        + "layer grants an attention budget, every action spends from it, and running "
                        + "out is the failure. Aborting is a sanctioned outcome, not a loss of nerve.");
        intro.setWrapText(true);

        getChildren().addAll(Ui.row(UiTokens.SPACE_5, heading, count), intro, rows);
        refresh();
    }

    /** Called by the view whenever the port reports a change. */
    public void refresh() {
        List<BreachTarget> targets = session.breachTargets();
        if (targets.equals(current) && !rows.getChildren().isEmpty()) {
            return;
        }
        current = List.copyOf(targets);
        count.set(String.valueOf(targets.size()));

        rows.getChildren().clear();
        if (targets.isEmpty()) {
            // §6: an empty state is an instruction, not a mood piece. So it says what would put
            // something here — and names the cheapest scan, because the tutorial crack is a miner a
            // scan finds rather than a target the map hands over.
            rows.getChildren().add(Note.empty(
                    "Nothing to breach yet. A foreign miner on your own rig is the safest first "
                            + "target, and a scan is how you find one: run `scan --quick`, or open a "
                            + "known node with recon to make it a target."));
            return;
        }
        for (BreachTarget target : targets) {
            rows.getChildren().add(row(target));
        }
    }

    public void setOnBegin(Consumer<BreachTarget> handler) {
        this.onBegin = handler == null ? target -> {} : handler;
    }

    private VBox row(BreachTarget target) {
        VBox box = new VBox(UiTokens.SPACE_2);
        box.getStyleClass().add("es-row");

        Label name = Ui.value(target.label().isBlank() ? target.targetId() : target.label());
        Label address = Ui.small(target.address());
        Label role = Ui.micro(target.role().isBlank() ? "" : target.role());
        role.getStyleClass().add("es-legend-sub");
        HBox title = Ui.row(UiTokens.SPACE_5, name, address, Ui.spacer(), role);

        FlowPane facts = new FlowPane(UiTokens.SPACE_5, UiTokens.SPACE_2);
        facts.setAlignment(Pos.BASELINE_LEFT);
        facts.getChildren().addAll(
                KeyValue.of("Tier", "T" + target.difficultyTier().tier()),
                KeyValue.of("State", target.liveOrDormant().name()),
                KeyValue.of("Compute", target.computeCost() + " CYCLES"),
                KeyValue.of("Defences", defences(target)));
        if (target.minerCrack() && target.estimatedBufferMinorUnits() > 0) {
            // An estimate, and labelled as one: the buffer is a figure recon inferred, and a crack
            // that sweeps it is a transfer of what is actually there (03 §5 rule 3 — nothing here
            // creates currency).
            facts.getChildren().add(
                    KeyValue.of("Buffer", "APPROX " + money(target.estimatedBufferMinorUnits())));
        }

        box.getChildren().addAll(title, facts, teaching(target));

        if (target.available()) {
            BreachView.Chip begin = new BreachView.Chip("Breach", "es-breach-chip-probe");
            begin.setAccessibleText("Begin a breach on " + name.getText()
                    + ", reserving " + target.computeCost() + " cycles.");
            begin.onInvoke(() -> onBegin.accept(target));
            box.getChildren().add(Ui.row(UiTokens.SPACE_3, begin));
        } else {
            // Not a disabled control. C4: the client did not decide this, so it reports the reason
            // the rules gave and offers nothing to press.
            Label refusal = Ui.small(target.refusal().isBlank()
                    ? "Unavailable, and the rules did not say why."
                    : target.refusal());
            refusal.setWrapText(true);
            box.getChildren().add(refusal);
        }
        return box;
    }

    /** The one sentence on this row that is worth reading before choosing. See the class comment. */
    private static Label teaching(BreachTarget target) {
        String text;
        if (target.minerCrack()) {
            text = "Your own rig. No heat, whatever happens — win or lose.";
        } else if (target.liveOrDormant() == TargetState.DORMANT) {
            text = "Dormant. Worth loot, never worth an unlock.";
        } else {
            text = "Live. The only kind of target a proof-of-skill gate counts.";
        }
        Label label = Ui.small(text);
        label.setWrapText(true);
        return label;
    }

    private static String defences(BreachTarget target) {
        List<String> marks = new ArrayList<>();
        marks.add(target.firewallTier() > 0
                ? "FIREWALL T" + target.firewallTier()
                : "FIREWALL NOT ESTABLISHED");
        if (target.tarpit()) {
            marks.add("TARPIT");
        }
        if (target.canaries()) {
            marks.add("CANARIES");
        }
        if (target.honeypotSuspected()) {
            marks.add("HONEYPOT SUSPECTED");
        }
        return String.join(" · ", marks);
    }

    /**
     * Minor units as ethecoin.
     *
     * <p>{@code Ethecoin} deliberately ships no display formatting — separator, symbol placement and
     * abbreviation are localization decisions and localization belongs to the client. {@link
     * Locale#ROOT} for the same reason every other formatted number in this client uses it: a German
     * player must not read {@code 12,40 EC} beside a figure another panel rendered with a period.
     */
    private static String money(long minorUnits) {
        return String.format(Locale.ROOT, "%d.%02d EC", minorUnits / 100, Math.abs(minorUnits % 100));
    }
}
