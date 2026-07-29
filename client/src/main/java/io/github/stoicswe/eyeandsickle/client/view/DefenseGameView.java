package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * <strong>[PROTOTYPE]</strong> — the defence minigame's window, with the game itself not yet chosen.
 *
 * <h2>⚠ This is NOT the breach board, and the distinction is the reason it exists</h2>
 *
 * {@code BreachView} is the <em>offensive</em> puzzle: you are on somebody else's machine, spending a
 * budget to get through their layers, and every move is loud. {@code solo/breach/Targets} already
 * lists foreign miners among its targets, so <b>cracking a parasite off your rig already works</b> and
 * runs that board — {@code docs/design/04-mining.md} §5.1 specifies exactly that.
 *
 * <p>What this is for is the other axis. <b>Defence</b> is its own kind of play under Invariant
 * <b>I9</b> — defending your own rig never generates heat — and a puzzle about holding something is
 * not the same puzzle as one about getting into it. Reusing the breach board for both would say they
 * were, and the two would then have to be balanced against each other forever.
 *
 * <h2>What is deliberately missing</h2>
 *
 * The game. This window has a WIN button and a FAIL button and nothing between them, because the
 * mechanic has not been chosen yet and inventing one in code is exactly what {@code CLAUDE.md} asks
 * not to happen — a system this size is a design decision that belongs in {@code docs/design/} before
 * it belongs in a view.
 *
 * <p>What the prototype <em>does</em> pin down is the <b>contract</b>: a defence attempt is a thing
 * that opens, resolves to exactly one of two outcomes, and hands that outcome back to the caller. Any
 * minigame that fits that shape can be dropped in behind these two buttons without touching whatever
 * called it, which is the part worth having settled early.
 *
 * <p>⚠ Like the port scanner, it is <b>not in the rail</b>: a defence is always <em>of something</em>,
 * so a window that opened with no subject would have nothing to be about.
 */
public final class DefenseGameView {

    private DefenseGameView() {}

    /** How a defence attempt ended. */
    public enum Outcome {
        /** The player held. */
        HELD,

        /** The player did not. */
        BREACHED
    }

    /**
     * Builds the prototype.
     *
     * @param subject what is being defended, in words — a parasite's process name, an armed defence
     * @param onResolved handed exactly one outcome, exactly once
     */
    public static Region create(GameSession session, String subject, java.util.function.Consumer<Outcome> onResolved) {
        VBox root = new VBox(UiTokens.SPACE_3);
        root.getStyleClass().addAll("es-defensegame", "es-body-pad");
        root.setMinWidth(560);

        Label title = new Label("DEFENCE");
        title.getStyleClass().add("es-panel-title");

        Label what = new Label(subject);
        what.getStyleClass().addAll("es-defensegame-subject", "es-mono");

        Label stub = new Label("""
                This minigame is not built yet.

                Defence is its own axis — it never generates heat (I9) — and a puzzle about \
                holding something is not the puzzle about getting into it. The breach board \
                already covers the offensive side, including cracking a parasite off your own \
                rig; this is the other one, and what it actually is has not been decided.

                The two buttons below stand in for it, so everything around the minigame can be \
                built and tested against a real outcome.""");
        stub.setWrapText(true);
        stub.getStyleClass().add("es-defensegame-note");

        // ⚠ Resolved AT MOST ONCE. A prototype whose buttons stayed live would let a player who
        // failed simply press win — and, worse, would let the caller be handed two outcomes for one
        // attempt, which is the one thing the contract this window exists to pin down forbids.
        boolean[] resolved = {false};
        BreachView.Chip win = new BreachView.Chip("Win", "es-breach-chip-loud");
        BreachView.Chip fail = new BreachView.Chip("Fail", "es-breach-chip-quiet");
        Label verdict = new Label("");
        verdict.getStyleClass().addAll("es-mono", "es-defensegame-note");

        Runnable[] settle = new Runnable[1];
        settle[0] = () -> {
            win.setDisable(true);
            fail.setDisable(true);
        };
        win.onInvoke(() -> {
            if (resolved[0]) {
                return;
            }
            resolved[0] = true;
            settle[0].run();
            verdict.setText("HELD — the attempt was turned back.");
            onResolved.accept(Outcome.HELD);
        });
        fail.onInvoke(() -> {
            if (resolved[0]) {
                return;
            }
            resolved[0] = true;
            settle[0].run();
            verdict.setText("BREACHED — it got through.");
            onResolved.accept(Outcome.BREACHED);
        });

        win.setAccessibleText("Resolve this defence as a success. A placeholder for the minigame.");
        fail.setAccessibleText("Resolve this defence as a failure. A placeholder for the minigame.");

        HBox actions = Ui.row(UiTokens.SPACE_3, win, fail);
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(title, what, stub, actions, verdict);
        return root;
    }
}
