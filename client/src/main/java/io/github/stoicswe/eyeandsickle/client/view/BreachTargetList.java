package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
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
 * <h2>Aiming is not firing</h2>
 *
 * A row is <b>selected</b>, and one control elsewhere on the panel starts the attempt. It used to be
 * a button per row, which put an irreversible compute spend one mis-click away in a list that reflows
 * whenever a sweep lands — see {@link BreachArming}. Selecting is free, reversible, and shared with
 * the network map, so a machine picked on the graph arrives here already chosen.
 *
 * <h2>Ordering is the player's, because "which target" has more than one right answer</h2>
 *
 * A dozen machines off one sweep is a list nobody reads top to bottom. The orders offered are the
 * three questions actually being asked — <em>what can I survive</em> (tier), <em>where is it</em>
 * (address), <em>what will fight back</em> (threat) — plus a filter for the only state that makes a
 * row un-actionable. None of them changes what is in the list: ordering a list is presentation, and
 * a filter that hid an unavailable target would hide the refusal that says why it is unavailable.
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
    private Consumer<BreachTarget> onSelect = target -> {};
    private String selected = "";
    private Order order = Order.TIER;
    private boolean readyOnly;
    private String paintedFor = "";

    /**
     * The orders a player can put this list in.
     *
     * <p>An enum and nothing else — no second flag, no order read back out of the scene graph — for
     * the same reason {@code NetMapView.Display} is one: it makes the state machine testable without
     * a toolkit and keeps {@link #ordered} the only place the comparison lives.
     */
    public enum Order {

        /** Easiest first. The order a player picking a survivable fight wants. */
        TIER("TIER"),

        /** Numeric per octet, so 10.0.0.9 sorts before 10.0.0.10 — the order the map reads in. */
        ADDRESS("ADDRESS"),

        /** Loudest defences first: what will fight back, and what a proof-of-skill gate counts. */
        THREAT("THREAT");

        private final String label;

        Order(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Brackets, not colour — §4.4, and it survives a greyscale capture and a screen reader. */
        public String control(Order active) {
            return this == active ? "[ " + label + " ]" : "  " + label + "  ";
        }
    }

    public BreachTargetList(GameSession session) {
        super(UiTokens.SPACE_5);
        this.session = session;

        getStyleClass().add("es-breach-picker");

        Label heading = Ui.label("Breach targets");
        heading.getStyleClass().add("es-kv-key");

        Label intro = Ui.small(
                "A breach reserves its compute for the whole attempt and releases it into thermal "
                        + "recovery when the attempt ends, however it ends. There is no clock: every "
                        + "layer grants an attention budget, every action spends from it, and running "
                        + "out is the failure. Aborting is a sanctioned outcome, not a loss of nerve.");
        intro.setWrapText(true);

        getChildren().addAll(Ui.row(UiTokens.SPACE_5, heading, count), intro, controls(), rows);
        refresh();
    }

    /** The ordering chips and the one filter, as a strip above the rows. */
    private HBox controls() {
        HBox strip = Ui.row(UiTokens.SPACE_3, Ui.label("Order"));
        for (Order value : Order.values()) {
            BreachView.Chip chip = new BreachView.Chip(value.control(order), "es-breach-chip-quiet");
            chip.setAccessibleText("Order the targets by " + value.label().toLowerCase(Locale.ROOT) + ".");
            chip.onInvoke(() -> {
                order = value;
                // The order changed, so the rows must be rebuilt even though the DATA did not —
                // which is exactly what the equality gate in refresh() is there to prevent. Clearing
                // the cache is how a presentation change gets past a data-change guard.
                current = List.of();
                repaintControls(strip);
                refresh();
            });
            strip.getChildren().add(chip);
        }
        BreachView.Chip ready = new BreachView.Chip("READY ONLY", "es-breach-chip-quiet");
        ready.setAccessibleText("Show only targets that can be attempted right now.");
        ready.onInvoke(() -> {
            readyOnly = !readyOnly;
            current = List.of();
            repaintControls(strip);
            refresh();
        });
        strip.getChildren().addAll(Ui.spacer(), ready);
        return strip;
    }

    private void repaintControls(HBox strip) {
        int index = 1;
        for (Order value : Order.values()) {
            if (strip.getChildren().get(index) instanceof Label chip) {
                chip.setText(Ui.upper(value.control(order)));
                chip.getStyleClass().remove("es-breach-chip-loud");
                if (value == order) {
                    chip.getStyleClass().add("es-breach-chip-loud");
                }
            }
            index++;
        }
        var last = strip.getChildren().getLast();
        last.getStyleClass().remove("es-breach-chip-loud");
        if (readyOnly) {
            last.getStyleClass().add("es-breach-chip-loud");
        }
    }

    /** Called by the view whenever the port reports a change. */
    public void refresh() {
        List<BreachTarget> targets = ordered(session.breachTargets());
        // The selection joins the comparison key: it is a visible change the data does not carry, so
        // leaving it out would mean clicking a row repainted nothing until the next tick.
        if (targets.equals(current) && selected.equals(paintedFor) && !rows.getChildren().isEmpty()) {
            return;
        }
        current = List.copyOf(targets);
        paintedFor = selected;
        count.set(String.valueOf(targets.size()));

        rows.getChildren().clear();
        if (targets.isEmpty()) {
            // §6: an empty state is an instruction, not a mood piece. So it says what would put
            // something here — and names the scan that would actually find it.
            //
            // ⚠ It names `scan --full`, not `--quick`. A Quick Scan sees unhidden T2+ miners only
            // (docs/design/04-mining.md §3.2) and the tutorial parasite is T1, so the cheap scan
            // genuinely cannot find the one target a new character has. Telling them otherwise sends
            // them to spend five cycles on a guaranteed miss and conclude the mechanic is broken.
            rows.getChildren().add(Note.empty(readyOnly
                    ? "Nothing you can attempt right now. Clear READY ONLY to see what is out of "
                            + "reach and why."
                    : "Nothing to breach yet. A foreign miner on your own rig is the safest first "
                            + "target — no heat on any outcome — and an audit is how you find one: "
                            + "run `scan --full`. A sweep on the network map finds the rest."));
            return;
        }
        for (BreachTarget target : targets) {
            rows.getChildren().add(row(target));
        }
    }

    /**
     * The list in the player's chosen order, with the one filter applied.
     *
     * <p>Unavailable targets sort last within every order. A row that cannot be attempted is still
     * worth reading — it carries the rules' own reason — but it is never the answer to "what next",
     * so it does not get to sit at the top of a list whose whole job is answering that.
     */
    List<BreachTarget> ordered(List<BreachTarget> targets) {
        List<BreachTarget> out = new ArrayList<>(targets);
        if (readyOnly) {
            out.removeIf(t -> !t.available());
        }
        java.util.Comparator<BreachTarget> comparator = java.util.Comparator
                .comparing((BreachTarget t) -> !t.available())
                .thenComparing(switch (order) {
                    case TIER -> java.util.Comparator
                            .comparingInt((BreachTarget t) -> t.difficultyTier().tier());
                    case ADDRESS -> java.util.Comparator
                            .comparing(BreachTarget::address, NetText::compareAddresses);
                    case THREAT -> java.util.Comparator
                            .comparingInt(BreachTargetList::threat).reversed();
                })
                // A stable tiebreak, so two equal rows never swap places between repaints and the
                // row under the pointer stays the row that gets clicked.
                .thenComparing(BreachTarget::targetId);
        out.sort(comparator);
        return List.copyOf(out);
    }

    /** How much is known to be waiting: defences first, then whether it can fight back at all. */
    static int threat(BreachTarget target) {
        int score = target.firewallTier() * 2;
        score += target.tarpit() ? 2 : 0;
        score += target.canaries() ? 2 : 0;
        score += target.honeypotSuspected() ? 3 : 0;
        score += target.liveOrDormant() == TargetState.LIVE ? 4 : 0;
        return score;
    }

    /** Which row is armed. {@code ""} clears it. */
    public void setSelected(String targetId) {
        this.selected = targetId == null ? "" : targetId;
        refresh();
    }

    /** Called with a row's target when it is picked. Picking arms; it never begins an attempt. */
    public void setOnSelect(Consumer<BreachTarget> handler) {
        this.onSelect = handler == null ? target -> {} : handler;
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
        if (target.minerCrack() && target.estimatedBufferWei().signum() > 0) {
            // An estimate, and labelled as one: the buffer is a figure recon inferred, and a crack
            // that sweeps it is a transfer of what is actually there (03 §5 rule 3 — nothing here
            // creates currency).
            facts.getChildren().add(
                    KeyValue.of("Buffer", "APPROX " + Ethecoin.format(target.estimatedBufferWei())));
        }

        box.getChildren().addAll(title, facts, teaching(target));

        if (!target.available()) {
            // Not a disabled control. C4: the client did not decide this, so it reports the reason
            // the rules gave and offers nothing to press. A target already breached says so here.
            Label refusal = Ui.small(target.refusal().isBlank()
                    ? "Unavailable, and the rules did not say why."
                    : target.refusal());
            refusal.setWrapText(true);
            box.getChildren().add(refusal);
            return box;
        }

        // Available: the whole row is the control, and pressing it ARMS rather than begins. The
        // commitment is one button elsewhere on the panel — see BreachArming for why those are two
        // steps and not one.
        box.getStyleClass().add("es-focusable");
        box.setFocusTraversable(true);
        // ⚠ WITHOUT THIS, MOST OF THE ROW IS NOT CLICKABLE.
        //
        // A JavaFX Region is picked where its background paints, and `.es-row` paints one only on
        // :hover — so at rest the 8px padding and the gaps between the title, the facts and the
        // teaching line are holes. A click that landed on a word bubbled up and selected the row; a
        // click two pixels below it went to the panel behind and did nothing, which reads as a list
        // that responds at random. Picking on bounds makes the whole rectangle the control, which is
        // what a list row is.
        box.setPickOnBounds(true);
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(box);
        if (target.targetId().equals(selected)) {
            box.getStyleClass().add("es-row-armed");
        }
        box.setAccessibleText((target.targetId().equals(selected) ? "Armed. " : "")
                + name.getText() + ", tier " + target.difficultyTier().tier() + ", "
                + target.computeCost() + " cycles to attempt. Select it, then start the breach.");
        box.setOnMouseClicked(event -> {
            event.consume();
            onSelect.accept(target);
        });
        box.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.SPACE
                    || event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                event.consume();
                onSelect.accept(target);
            }
        });
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

}
