package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.teaching.GlossBar;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.breach.BreachViewport;
import io.github.stoicswe.eyeandsickle.client.ui.breach.CostStrip;
import io.github.stoicswe.eyeandsickle.client.ui.breach.LatticeMap;
import io.github.stoicswe.eyeandsickle.client.ui.breach.OutcomeSlate;
import io.github.stoicswe.eyeandsickle.client.ui.breach.PortComb;
import io.github.stoicswe.eyeandsickle.client.ui.breach.TumblerRack;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.AttentionLedger;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.AttentionMeter;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Note;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The breach window — the core hacking minigame, played.
 *
 * <h2>Everything here is a readout of the port, and nothing here is a rule</h2>
 *
 * This view composes {@code ui/breach}'s renderers against {@link GameSession} and does not own a
 * single number the player could gain something by changing. It never asks {@code SoloGame} anything;
 * it asks {@code session.breach()} and draws what comes back. That is Invariant <b>I14</b> at the one
 * place it is easiest to break — a client that predicted an attention cost, or decided that a probe
 * was going to fail, would be authoritative over exactly the thing a cheater forges.
 *
 * <p>The consequence worth stating: the same window plays a solo breach in-process and a home
 * server's breach over REST, because it cannot tell the difference. If a method call here ever needs
 * to know which, the seam has already leaked.
 *
 * <h2>Cost before the click, itemisation after it — {@code docs/design/05} §4</h2>
 *
 * §4 makes the legibility of a loss a design constraint rather than a nicety: <em>"the player must
 * always be able to see which action cost what. A loss has to read as 'I was too loud', never 'the
 * game decided'."</em> Three surfaces carry that here and all three are always on screen while a
 * breach is live:
 *
 * <ul>
 *   <li>{@link CostStrip} prints every legal move's price <em>before</em> it is taken, affordable or
 *       not, and hover or focus previews that price on the meter.
 *   <li>{@link AttentionMeter} shows the budget as countable cells, with the points lost to strikes
 *       marked apart from the points spent on moves — the "I was too loud" mark.
 *   <li>{@link AttentionLedger} itemises every action after the fact, oldest first, never re-sorted.
 * </ul>
 *
 * <p>The ledger deliberately stays on screen <b>after</b> the attempt resolves, underneath the
 * outcome slate. A resolution screen that showed the verdict and hid the arithmetic would be the
 * precise failure §4 forbids, and it would hide it at the only moment the player is actually asking
 * the question.
 *
 * <h2>What must not happen in a breach window</h2>
 *
 * <ul>
 *   <li><b>No focus theft.</b> {@code docs/client/00-client-overview.md} §2 (C5) calls it "the single
 *       most damaging thing this client could do", and it names the breach specifically. Nothing in
 *       {@code refresh} calls {@code requestFocus()}, and the abort control confirms in place rather
 *       than through a modal dialog — a dialog would take focus and the keyboard mid-puzzle.
 *   <li><b>No scene-graph rebuild per refresh.</b> The three class boards are built once and toggled;
 *       rebuilding them would drop the player's focus and their pointer target between two frames of
 *       a turn they are halfway through taking.
 *   <li><b>No decorative motion.</b> No {@code Motion.reveal}, no {@code Greeble}, no
 *       {@code Substrate}, no {@code SweepPanel} — per the amendment to {@code docs/client/01} §7.3
 *       (D-6): inside a live breach a surface may animate only when the motion <em>is</em> the
 *       readout, which is true of the widgets and of nothing this class adds.
 *   <li><b>No poll.</b> A breach is turn-based and changes only when an intent is dispatched, and
 *       every applied intent fires {@code onChange}. A {@code Pulse.every} here would repaint a
 *       static panel forever.
 * </ul>
 */
public final class BreachView {

    private BreachView() {}

    public static Region create(GameSession session) {
        return create(session, null, null, new BreachArming());
    }

    public static Region create(GameSession session, TermDatabase terms, ClientProfile profile) {
        return create(session, terms, profile, new BreachArming());
    }

    /**
     * With a term database attached, the head glosses itself on hover and on focus.
     *
     * <p>Four terms are worth the tier-1 gloss here — {@code attention}, {@code trace},
     * {@code noise} and {@code puzzle-class} — because all four are words the game invented for
     * things a player has to reason about within seconds of opening this window.
     * {@link GlossBar#attach} is silent when a term has no page, which is the correct behaviour and
     * not a fallback: a definition the curriculum has not written yet must not be improvised here
     * ({@code docs/education/00-curriculum-and-method.md} §1.2).
     */
    public static Region create(
            GameSession session, TermDatabase terms, ClientProfile profile, BreachArming arming) {
        VBox root = new VBox(UiTokens.SPACE_6);
        root.getStyleClass().add("es-body-pad");

        BreachPresenter presenter = new BreachPresenter(session);

        // ---------------------------------------------------------------- head
        KeyValue target = KeyValue.of("Target", "");
        KeyValue tier = KeyValue.of("Tier", "");
        KeyValue state = KeyValue.of("State", "");
        KeyValue layer = KeyValue.of("Layer", "");
        KeyValue attention = KeyValue.of("Attention", "");
        KeyValue trace = KeyValue.of("Trace", "");
        KeyValue strikes = KeyValue.of("Strikes", "");
        KeyValue noise = KeyValue.of("Noise", "");
        KeyValue held = KeyValue.of("Held", "");

        if (terms != null && profile != null) {
            GlossBar.attach(attention, "attention", terms, profile);
            GlossBar.attach(trace, "trace", terms, profile);
            GlossBar.attach(noise, "noise", terms, profile);
            GlossBar.attach(layer, "puzzle-class", terms, profile);
        }

        FlowPane readouts = new FlowPane(UiTokens.SPACE_6, UiTokens.SPACE_2);
        readouts.setAlignment(Pos.BASELINE_LEFT);
        readouts.getChildren().addAll(target, tier, state, layer, attention, trace, strikes, noise, held);
        HBox.setHgrow(readouts, Priority.ALWAYS);

        Chip abort = new Chip("Abort", "es-breach-chip-quiet");
        abort.setAccessibleText("Abort the breach. Press twice: the first press asks, the second commits.");
        // The FlowPane takes the slack rather than a spacer doing it, so the readouts still reflow
        // onto a second line in a narrow window instead of holding one long unwrappable row.
        HBox head = Ui.row(UiTokens.SPACE_5, readouts, abort);

        // I9, and the reason the crack is the tutorial: a breach against a miner squatting on your
        // own rig cannot raise heat, on any outcome, including a failure. Stated in the live window
        // rather than only on the target row, because it is what makes losing safe to do repeatedly.
        Label crackNote = Ui.small("Your own rig. No heat, whatever happens — win or lose.");

        // ---------------------------------------------------------------- notices
        //
        // Placed above everything and driven by its own content rather than by breach state: a
        // refusal from `beginBreach` arrives when there is no breach open at all, and hiding the one
        // surface that says why would leave the player pressing a control that silently does nothing.
        VBox notices = new VBox(UiTokens.SPACE_2);

        // ---------------------------------------------------------------- widgets
        BreachViewport viewport = new BreachViewport();
        AttentionMeter meter = new AttentionMeter();
        CostStrip strip = new CostStrip();
        HBox.setHgrow(strip, Priority.ALWAYS);
        HBox gauges = Ui.row(UiTokens.SPACE_6, meter, strip);
        gauges.setAlignment(Pos.TOP_LEFT);

        KeyValue selection = KeyValue.of("Selected", "NONE");
        Label selectionHint = Ui.micro(
                "Pick a slot or a node first; the action then acts on it. Every action prints its "
                        + "attention cost before you spend it.");
        selectionHint.getStyleClass().add("es-legend-sub");
        HBox selectionRow = Ui.row(UiTokens.SPACE_5, selection, selectionHint);

        PortComb comb = new PortComb();
        TumblerRack rack = new TumblerRack();
        LatticeMap map = new LatticeMap();
        StackPane boards = new StackPane(comb, rack, map);
        StackPane.setAlignment(comb, Pos.TOP_LEFT);
        StackPane.setAlignment(rack, Pos.TOP_LEFT);
        StackPane.setAlignment(map, Pos.TOP_LEFT);

        AttentionLedger ledger = new AttentionLedger();
        VBox.setVgrow(ledger, Priority.SOMETIMES);

        OutcomeSlate slate = new OutcomeSlate();
        Chip dismiss = new Chip("Dismiss", "es-breach-chip-probe");
        dismiss.setAccessibleText("Clear this outcome and return to the target list.");
        VBox outcome = new VBox(UiTokens.SPACE_5, slate, Ui.row(UiTokens.SPACE_3, dismiss));

        BreachTargetList targets = new BreachTargetList(session);

        // ---------------------------------------------------------------- the launch panel
        //
        // The one control that spends. Above the list rather than inside it, because there is
        // exactly one thing that can be started and there should be exactly one button that starts
        // it — see BreachArming for why arming and firing are two steps. It is also the door the
        // network map opens onto: a machine picked on the graph arrives here already armed, and the
        // player's next act is a single deliberate press.
        Label armedLabel = Ui.value("");
        Label armedFacts = Ui.small("");
        armedFacts.setWrapText(true);
        Chip start = new Chip("Start breach", "es-breach-chip-loud");
        start.setAccessibleText("Begin the breach on the armed target. This reserves its compute for "
                + "the whole attempt and cannot be undone into a refund.");
        Chip disarm = new Chip("Clear", "es-breach-chip-quiet");
        disarm.setAccessibleText("Un-arm the target without starting anything.");
        VBox launch = new VBox(UiTokens.SPACE_3,
                armedLabel, armedFacts, Ui.row(UiTokens.SPACE_3, start, disarm));
        // Both classes: `-launch` is the panel's own frame, `-picker` is what scopes the chip rules
        // (they are declared under it, because the cost-strip block only styles chips inside a
        // breach and these two live outside one).
        launch.getStyleClass().addAll("es-breach-launch", "es-breach-picker");

        root.getChildren().addAll(
                head, crackNote, notices, viewport, gauges, selectionRow, boards, outcome, ledger,
                launch, targets);

        presenter.bind(viewport, meter, ledger, strip, comb, rack, map, slate);
        presenter.setNoticeSink(text -> {
            if (text == null || text.isBlank()) {
                notices.getChildren().clear();
            } else {
                // A blank lead on purpose. Note's lead clause renders in amber, and D-7 rations the
                // whole breach feature to exactly one amber element — the extracted-yield line on a
                // successful crack. A refusal is not live and not earning, so it does not get the
                // accent; the distinction it has to carry (refused / gated / unreachable) is carried
                // by the first word of the sentence instead, which docs/client/07 §5.2 wants anyway.
                notices.getChildren().setAll(Note.consequence("", text));
            }
            visible(notices, !notices.getChildren().isEmpty());
        });
        presenter.setSelectionSink(text -> {
            selection.set(text == null || text.isBlank() ? "NONE" : text);
            selection.valueNode().setAccessibleText("Selected: " + selection.get());
        });

        // Picking a row arms it. Nothing is spent, and picking the armed row again clears it — so
        // there is always a way back to "nothing chosen" without having to choose something else.
        targets.setOnSelect(t ->
                arming.arm(t.targetId().equals(arming.armed()) ? "" : t.targetId()));
        disarm.onInvoke(() -> arming.arm(""));
        // ⚠ The launch control is DEAD FOR ONE PULSE after it appears, and this is not defensive
        // programming — it is a bug that was reproduced.
        //
        // Pressing BREACH on the network map raises this window from inside the click handler. The
        // launch panel is then created under a pointer that is still down, and the release lands on
        // START BREACH: one click on a map cell reserved twelve cycles and opened a breach the
        // player never asked for. That is precisely the mis-click BreachArming splits aiming from
        // firing to prevent, arriving through the window manager instead of through the list.
        //
        // A pulse is imperceptible to a person and unbridgeable by a single event, so a human click
        // always works and a same-event one never does. It is not a two-press confirm: the user
        // asked for one button and one click, and making the primary action a double-press to fix a
        // race would be paying for the fix in the wrong currency.
        boolean[] live = {false};
        start.onInvoke(() -> {
            if (!live[0] || !arming.isArmed()) {
                return;
            }
            String armed = arming.armed();
            // Disarmed BEFORE the attempt, not after. beginBreach either opens a breach — in which
            // case the launch panel is hidden anyway — or refuses, and a refusal that left the
            // target armed would leave a START BREACH button sitting under the sentence explaining
            // why it will not work.
            arming.arm("");
            presenter.begin(armed);
        });

        // Two presses, in place, rather than a confirmation dialog. `aborted` is a persisted outcome
        // with real consequences (docs/design/05 §4), so a mis-key must not be able to spend one —
        // but a modal Alert would take the keyboard away mid-breach, which pillar C5 names as the
        // worst thing this client can do. Arming disarms itself on the next refresh, so a forgotten
        // armed control cannot fire an abort three turns later.
        boolean[] armed = {false};
        abort.onInvoke(() -> {
            if (!armed[0]) {
                armed[0] = true;
                abort.setText(Ui.upper("Abort · press again"));
                return;
            }
            armed[0] = false;
            abort.setText(Ui.upper("Abort"));
            presenter.abort();
        });
        dismiss.onInvoke(presenter::dismiss);

        Runnable refresh = () -> {
            Optional<BreachSnapshot> found = session.breach();
            boolean open = found.isPresent();
            boolean resolved = open && found.get().resolved();

            if (armed[0]) {
                armed[0] = false;
                abort.setText(Ui.upper("Abort"));
            }

            visible(head, open);
            visible(viewport, open);
            visible(gauges, open && !resolved);
            visible(selectionRow, open && !resolved);
            visible(boards, open && !resolved);
            visible(outcome, resolved);
            // The ledger outlives the attempt. See the class comment: hiding the itemisation on the
            // outcome screen would hide it at the one moment it is being read.
            visible(ledger, open);
            visible(targets, !open);
            visible(crackNote, open && found.get().minerCrack());

            // ---- the launch panel
            //
            // Shown only when there is no breach running: while one is open the whole panel below is
            // the breach, and a second "start" control would be offering to begin an attempt on top
            // of the one in progress.
            Optional<io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget> armedTarget =
                    open ? Optional.empty() : session.breachTargets().stream()
                            .filter(t -> t.targetId().equals(arming.armed()))
                            .findFirst();
            // An armed id that is no longer in the list is dropped rather than kept: a machine can
            // stop being a target between arming and pressing — somebody breached it, the compute
            // went — and a button pointing at a target the rules would now refuse reads as broken.
            if (!open && arming.isArmed() && armedTarget.isEmpty()) {
                arming.arm("");
            }
            boolean wasShowing = launch.isVisible();
            visible(launch, armedTarget.isPresent());
            if (armedTarget.isPresent() && !wasShowing) {
                live[0] = false;
                javafx.application.Platform.runLater(() -> live[0] = true);
            }
            targets.setSelected(open ? "" : arming.armed());
            armedTarget.ifPresent(t -> {
                armedLabel.setText(Ui.upper(
                        (t.label().isBlank() ? t.targetId() : t.label()) + " · " + t.address()));
                armedFacts.setText("Tier " + t.difficultyTier().tier() + " · "
                        + t.computeCost() + " cycles, reserved for the whole attempt and released "
                        + "into thermal recovery however it ends"
                        + (t.minerCrack() ? " · your own rig, so no heat on any outcome." : "."));
            });

            if (open) {
                BreachSnapshot snapshot = found.get();
                target.set(snapshot.targetLabel() + (snapshot.minerCrack() ? " · CRACK" : ""));
                tier.set("T" + snapshot.difficultyTier().tier());
                state.set(snapshot.liveOrDormant().name());
                noise.set(String.valueOf(snapshot.noiseSoFar()));
                held.set(snapshot.reservedCycles() + " CYCLES");

                var total = snapshot.totalAttention();
                attention.set(total.spent() + " / " + total.budget() + " SPENT");
                trace.set(Math.round(total.traceProgress() * 100) + "%");

                Optional<BreachLayer> active = snapshot.active();
                layer.set(active.map(BreachLayer::title)
                        .orElse(resolved ? "RESOLVED" : Ui.upper("no active layer")));
                strikes.set(strikeText(snapshot, active.orElse(null)));
            } else {
                targets.refresh();
            }

            presenter.refresh();
        };

        refresh.run();
        session.onChange(s -> refresh.run());

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        // Vertical only: this panel reflows to its width, so a horizontal bar would mean it refused
        // to, which is a layout bug rather than something to scroll past.
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /**
     * Strikes, in words, beside the viewport's character-drawn gauge.
     *
     * <p>Duplicated deliberately. The gauge is a texture, and {@code docs/client/07-accessibility.md}
     * §5.2 forbids meaning that rests on appearance alone — a screen-reader user, or anyone reading a
     * greyscale capture, needs the same number as a sentence. While a layer is live this is that
     * layer's count, because that is the one a decision turns on; once the attempt has resolved it is
     * the whole attempt's, because there is no longer a layer to be at.
     */
    private static String strikeText(BreachSnapshot snapshot, BreachLayer active) {
        if (active != null) {
            return active.strikes() + " OF " + active.strikeLimit() + " SPENT";
        }
        int spent = 0;
        int limit = 0;
        for (BreachLayer layer : snapshot.layers()) {
            spent += layer.strikes();
            limit += layer.strikeLimit();
        }
        return spent + " OF " + limit + " SPENT";
    }

    /**
     * Shows or hides a node and takes it out of the layout with it.
     *
     * <p>{@code setManaged} matters as much as {@code setVisible}: a merely invisible child still
     * claims its height, so a hidden outcome slate would leave a rectangle of empty panel above the
     * ledger for the whole attempt.
     */
    private static void visible(Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }

    /**
     * A control that is a {@link Label}, not a {@link javafx.scene.control.Button}.
     *
     * <p>Same reason {@code WindowFrame} draws its strip controls this way: Modena's Button brings a
     * focus ring, a background and a padding scale that {@code docs/design/ui-design-language.md} §9
     * rejects, and overriding all three costs more than drawing the control. The keyboard route is
     * therefore built by hand — focus traversal, the shared focus ring, and Space/Enter — because
     * {@code docs/client/07} §3 requires every action to have one, and a Label has none by default.
     *
     * <p>Package-private so {@link BreachTargetList} uses the same control rather than a second one
     * that drifts from it.
     */
    static final class Chip extends Label {

        private Runnable action = () -> {};

        Chip(String text, String kindClass) {
            super(Ui.upper(text));
            getStyleClass().addAll("es-breach-chip", kindClass, "es-focusable");
            Cursors.shared().clickable(this);
            setFocusTraversable(true);
            setOnMouseClicked(e -> {
                e.consume();
                action.run();
            });
            setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                    e.consume();
                    action.run();
                }
            });
        }

        void onInvoke(Runnable handler) {
            this.action = handler == null ? () -> {} : handler;
        }
    }
}
