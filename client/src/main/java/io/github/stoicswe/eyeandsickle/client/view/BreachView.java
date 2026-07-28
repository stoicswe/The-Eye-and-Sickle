package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.teaching.GlossBar;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.breach.BreachViewport;
import io.github.stoicswe.eyeandsickle.client.ui.breach.CostStrip;
import io.github.stoicswe.eyeandsickle.client.ui.breach.MatrixGrid;
import io.github.stoicswe.eyeandsickle.client.ui.breach.OutcomeSlate;
import io.github.stoicswe.eyeandsickle.client.ui.breach.OffsetRack;
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
 *   <li><b>No scene-graph rebuild per refresh.</b> Both class boards are built once and toggled;
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

    /**
     * How long the abort stays armed between its two presses.
     *
     * <p>Long enough to be a deliberate second press and short enough that a control armed and
     * forgotten cannot fire an abort three moves later. Measured on the session clock, which is the
     * clock every other deadline in this panel is measured against.
     */
    private static final int ABORT_ARM_SECONDS = 4;

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

        // ---------------------------------------------------------------- widgets
        BreachViewport viewport = new BreachViewport();
        AttentionLedger ledger = new AttentionLedger();
        AttentionMeter meter = new AttentionMeter();
        CostStrip strip = new CostStrip();
        HBox.setHgrow(strip, Priority.ALWAYS);
        // The attention column: the blocks the player is spending, and directly under them the
        // record of what each spend bought. One question — "how much have I got and where did it
        // go" — so one column, read top to bottom. The ledger used to sit two panels lower, which
        // meant checking a cost against its outcome was a scroll rather than a glance.
        VBox attentionColumn = new VBox(UiTokens.SPACE_5, meter, ledger);
        attentionColumn.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(ledger, Priority.SOMETIMES);

        HBox gauges = Ui.row(UiTokens.SPACE_6, attentionColumn, strip);
        gauges.setAlignment(Pos.TOP_LEFT);

        // ---------------------------------------------------------------- the console row
        //
        // The viewport and the gauges side by side rather than stacked. The viewport is a
        // fixed-width character texture — it cannot reflow, so given a whole row to itself it left a
        // band of empty panel beside it and pushed everything a player actually reads further down.
        // Putting the instruments in that space costs nothing and makes the top of the window one
        // glance instead of two: what the machine is doing, and what it would cost to act.
        //
        // ⚠ The viewport keeps its natural width and the gauges take the slack. A gauge column that
        // fought the texture for space would either squeeze the strip into one chip per line or
        // shear the viewport, and §7.2's whole point is that a character grid does not negotiate.
        HBox console = Ui.row(UiTokens.SPACE_6, viewport, gauges);
        console.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(gauges, Priority.ALWAYS);
        viewport.setMinWidth(Region.USE_PREF_SIZE);

        KeyValue selection = KeyValue.of("Selected", "NONE");
        Label selectionHint = Ui.micro(
                "Pick a slot or a node first; the action then acts on it. Every action prints its "
                        + "attention cost before you spend it.");
        selectionHint.getStyleClass().add("es-legend-sub");
        HBox selectionRow = Ui.row(UiTokens.SPACE_5, selection, selectionHint);

        MatrixGrid grid = new MatrixGrid();
        OffsetRack cipher = new OffsetRack();
        StackPane boards = new StackPane(grid, cipher);
        StackPane.setAlignment(grid, Pos.TOP_LEFT);
        StackPane.setAlignment(cipher, Pos.TOP_LEFT);

        OutcomeSlate slate = new OutcomeSlate();
        Chip dismiss = new Chip("Dismiss", "es-breach-chip-probe");
        dismiss.setAccessibleText("Clear this outcome and return to the target list.");

        // ⚠ Try again is a SINGLE press, and that is a deliberate exception to the aim/fire split.
        //
        // BreachArming separates choosing a target from committing to one because a mis-click on a
        // reflowing list or a moving graph must not spend compute. This is neither: it is a control
        // on an outcome slate the player is reading, about the attempt they were just in, and it
        // states its price on its face. Making them dismiss, find the row again and press start
        // would be paying for a hazard that is not present here.
        //
        // Shown only when the target is still attemptable — a successful breach leaves a foothold
        // and a successful crack leaves no miner, so in both cases there is nothing left to retry
        // and the rules' own availability answer says so without this view having to know why.
        Chip retry = new Chip("Try again", "es-breach-chip-loud");
        VBox outcome = new VBox(UiTokens.SPACE_5, slate, Ui.row(UiTokens.SPACE_3, retry, dismiss));
        outcome.getStyleClass().add("es-breach-picker");

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
        // Starts hidden. A freshly-constructed VBox is visible by default, and the first refresh
        // used to read that default as "it was already showing" — see the `live` gate below, which
        // that misreading disabled outright.
        visible(launch, false);

        root.getChildren().addAll(
                head, crackNote, console, selectionRow, boards, outcome, launch, targets);

        presenter.bind(viewport, meter, ledger, strip, grid, cipher, slate);
        presenter.setSelectionSink(text -> {
            selection.set(text == null || text.isBlank() ? "NONE" : text);
            selection.valueNode().setAccessibleText("Selected: " + selection.get());
        });

        // Picking a row arms it. Nothing is spent, and picking the armed row again clears it — so
        // there is always a way back to "nothing chosen" without having to choose something else.
        targets.setOnSelect(t ->
                arming.arm(t.targetId().equals(arming.armed()) ? "" : t.targetId()));
        disarm.onInvoke(() -> arming.arm(""));
        // ⚠ The launch control is DEAD FOR ONE PULSE after a target is armed.
        //
        // Pressing BREACH on the network map raises this window from inside the click handler, so
        // the launch panel can be created under a pointer that is still down. A pulse is
        // imperceptible to a person and unbridgeable by a single event, so a human click always
        // works and a same-event one never does.
        //
        // ⚠ IT IS GATED ON BEING ARMED, NOT ON BECOMING VISIBLE, AND THE DIFFERENCE WAS A DEAD
        // BUTTON. The first version flipped `live` on a hidden→visible transition of the panel — but
        // a freshly-constructed VBox is visible by default, so the very first refresh saw
        // "was showing: true", the transition never fired, and START BREACH was permanently inert.
        // A guard that can silently disable the control it protects is worse than the mis-click it
        // was defending against, so it now arms itself from the state it actually cares about and
        // always becomes live one pulse later.
        boolean[] live = {false};
        boolean[] pending = {false};
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
        // worst thing this client can do.
        //
        // ⚠ ARMED FOR A FIXED WINDOW OF TIME, NOT "UNTIL THE NEXT REFRESH", AND THE DIFFERENCE WAS
        // AN ABORT THAT COULD NOT BE PERFORMED AT ALL.
        //
        // The first version disarmed inside `refresh`, which runs on every session change — and the
        // session changes about once a second, because self-mining credits on every tick. So the
        // arming survived for under a second: press once and it arms, a tick clears it, press again
        // and it arms again. A player trying to leave a breach could press Abort all day and never
        // abort, which is exactly what "the breach window gets stuck" looks like from the outside.
        //
        // The window is measured on the session's clock, so it is the same clock everything else in
        // this panel is timed against, and it is long enough to be a deliberate second press and
        // short enough that a forgotten armed control cannot fire three turns later.
        java.time.Instant[] armedUntil = {java.time.Instant.EPOCH};
        abort.onInvoke(() -> {
            java.time.Instant at = session.now();
            if (at.isAfter(armedUntil[0])) {
                armedUntil[0] = at.plusSeconds(ABORT_ARM_SECONDS);
                abort.setText(Ui.upper("Abort · press again"));
                return;
            }
            armedUntil[0] = java.time.Instant.EPOCH;
            abort.setText(Ui.upper("Abort"));
            presenter.abort();
        });
        dismiss.onInvoke(presenter::dismiss);
        retry.onInvoke(() -> {
            String again = session.breach()
                    .map(io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot::targetId)
                    .orElse("");
            if (again.isBlank()) {
                return;
            }
            // Dismiss first, or `begin` refuses: the slate is still on the save and a resolved breach
            // blocks a new one. Both go through the port, so the rules still get the final say on
            // whether the second attempt can be afforded — which after an abort is a real question,
            // because the first attempt's cycles are on the recovery curve and not back yet.
            session.dismissBreach();
            presenter.begin(again);
        });

        Runnable refresh = () -> {
            Optional<BreachSnapshot> found = session.breach();

            // ⚠ A finished attempt yields to a newly armed one, and this is the whole fix for
            // "BREACH shows the previous breach".
            //
            // A resolved breach stays on the save until it is dismissed — that is deliberate, so an
            // outcome slate survives closing the window and can be read later. But `open` is true
            // for it, which hides both the target list and the launch panel: arming a node from the
            // map raised this window onto somebody else's obituary with no control but Dismiss.
            //
            // ⚠ RESOLVED ONLY. A live attempt is never touched. It holds reserved compute that
            // aborting does not refund (docs/design/05 §4), so clearing one because the player
            // brushed a node on the map would spend their cycles for them. The check is on
            // `resolved()`, not on `isPresent()`, and it must stay that way.
            //
            // ⚠ This cannot loop. `dismissBreach` fires onChange, which re-enters here — and by then
            // session.breach() is empty, so the branch is not taken a second time.
            if (arming.isArmed() && found.map(BreachSnapshot::resolved).orElse(false)) {
                session.dismissBreach();
                found = session.breach();
            }

            boolean open = found.isPresent();
            boolean resolved = open && found.get().resolved();

            // Only once the window has actually elapsed — see the arming above. Resetting the label
            // on every refresh is what made the second press unreachable.
            if (session.now().isAfter(armedUntil[0])
                    && !abort.getText().equals(Ui.upper("Abort"))) {
                abort.setText(Ui.upper("Abort"));
            }

            visible(head, open);
            // The row is shown whenever a breach is open. Inside it the meter and the action strip
            // disappear once the attempt has resolved — there is nothing left to spend — but the
            // LEDGER stays: hiding the itemisation on the outcome screen would hide it at the one
            // moment it is being read.
            visible(console, open);
            visible(viewport, open);
            visible(gauges, open);
            visible(attentionColumn, open);
            visible(meter, open && !resolved);
            visible(strip, open && !resolved);
            visible(ledger, open);
            visible(selectionRow, open && !resolved);
            visible(boards, open && !resolved);
            visible(outcome, resolved);
            visible(retry, resolved && found
                    .map(io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot::targetId)
                    .map(id -> session.breachTargets().stream()
                            .anyMatch(t -> t.targetId().equals(id) && t.available()))
                    .orElse(false));
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
            visible(launch, armedTarget.isPresent());
            if (armedTarget.isEmpty()) {
                live[0] = false;
            } else if (!live[0] && !pending[0]) {
                // Scheduled once per arming rather than once per refresh — refresh runs on every
                // session change, and queueing a runLater on each of them would be a slow leak on a
                // panel that is open for a whole session.
                pending[0] = true;
                javafx.application.Platform.runLater(() -> {
                    live[0] = true;
                    pending[0] = false;
                });
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

        // ⚠ TWO subscriptions, and the second one was missing.
        //
        // Arming is not game state, so it does not travel through the session — which meant picking
        // a different row in the list below changed the armed id and repainted nothing: the launch
        // panel kept naming the previous target and the row highlight never moved. The list looked
        // broken because the only thing listening for a change was the thing that does not hear
        // about arming.
        AutoCloseable onSession = session.onChange(s -> refresh.run());
        AutoCloseable onArming = arming.onChange(refresh);

        ScrollPane scroll = new ScrollPane(root);

        // ⚠ KEYS ARE ROUTED FROM THE OUTERMOST NODE, NOT FROM THE BOARD.
        //
        // A filter only fires for events targeted at the node it is on or at one of its descendants.
        // The breach window's focus is almost never on the board — it is on an action chip, or on
        // this ScrollPane, which treats arrows as scroll commands — so a filter on the board sat
        // waiting for events that were being delivered somewhere else. Selecting a cell and pressing
        // an arrow did nothing, and the control looked broken rather than unfocused.
        //
        // Installed here because this is the outermost node of the panel: every key press delivered
        // anywhere inside the breach window passes through it on the way down, including one aimed
        // at the ScrollPane itself.
        //
        // ⚠ Gated on visibility, so exactly one board can be holding the keys and the other keeps
        // the arrows it would need for ordinary focus traversal. Both boards ARE keyboard controls —
        // the grid walks a path with arrows, the cipher types digits into a cell — so this route is
        // the primary one for each rather than a convenience.
        scroll.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (grid.isVisible()) {
                grid.handleKey(event);
            } else if (cipher.isVisible()) {
                cipher.handleKey(event);
            }
        });

        scroll.setFitToWidth(true);
        // Vertical only: this panel reflows to its width, so a horizontal bar would mean it refused
        // to, which is a layout bug rather than something to scroll past.
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        closeOnDetach(root, session, presenter, onSession, onArming);
        return scroll;
    }

    /**
     * Releases both subscriptions when the panel leaves the scene.
     *
     * <p>⚠ The arming one matters more than the session one, and that is not obvious. {@code
     * BreachArming} lives for the whole client rather than for the window, so a listener left on it
     * by a closed panel keeps calling {@code refresh} against a detached scene graph <em>forever</em>
     * — and every re-open adds another. The session's listener leaks the same way and is at least
     * bounded by the session.
     *
     * <p>Only on a transition <b>away</b> from a scene, and only after having been in one: a node's
     * scene is null before it is added as well as after it is removed, so acting on "scene is null"
     * alone would tear the panel down during its own construction. Same shape as {@code NetMapView}.
     */
    private static void closeOnDetach(
            Region root, GameSession session, BreachPresenter presenter, AutoCloseable... handles) {
        boolean[] attached = {false};
        root.sceneProperty().addListener((observable, was, now) -> {
            if (now != null) {
                attached[0] = true;
                return;
            }
            if (!attached[0]) {
                return;
            }
            attached[0] = false;

            // ⚠ Closing the console abandons the attempt, exactly as quitting does.
            //
            // A breach is a player at a terminal, not work the rig is doing — there is nobody at the
            // console once the window is gone. Leaving it open would strand its cycles held
            // indefinitely and leave a half-played puzzle to be resumed with no memory of it, which
            // is the state the load-time abandon already exists to prevent. It is recorded as an
            // ABORTED resolution rather than deleted, so closing a window is not a free escape from a
            // losing attempt.
            //
            // ⚠ Minimising does NOT do this: DeskManager.setMinimized only flips visibility and
            // leaves the frame in the desk, so the scene stays. Only a real close detaches.
            session.abandonBreach();
            presenter.dispose();
            for (AutoCloseable handle : handles) {
                try {
                    handle.close();
                } catch (Exception ignored) {
                    // A registry that refuses to forget a listener is not something this panel can
                    // do anything about, and throwing out of a scene-graph listener would take the
                    // whole close with it.
                }
            }
        });
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
