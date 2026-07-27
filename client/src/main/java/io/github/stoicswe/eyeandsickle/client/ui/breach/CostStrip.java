package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachAction;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachActionKind;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.util.Duration;

/**
 * The legal moves, each with its price on its face.
 *
 * <h2>§4's requirement, in its stronger form</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4 asks that attention be "visible and itemised at all
 * times". The ledger is the itemised half. This is the visible half, and it goes one step further
 * than the doc asks: <b>every chip prints its cost whether or not the player can afford it</b>. A
 * cost that only appears when it is payable teaches the player nothing about the shape of the
 * choice, and the whole loud-versus-patient trade the class is built on is a comparison between two
 * numbers the player has to be able to see side by side.
 *
 * <p>The cost is on the {@link BreachAction} rather than computed here, which is Invariant I14 doing
 * its job: a tarpit surcharge, a firewall penalty and a loadout discount all move the price, and a
 * client that recomputed any of that would be a second implementation of a balance rule sitting
 * where a cheater can reach it. The engine sends the number; this draws it.
 *
 * <h2>Kind before cost</h2>
 *
 * The chip's class is keyed to {@link BreachActionKind}, so <em>how loud this is</em> is legible
 * before the digits are read — a quiet read is dim, a probe is body weight, a loud tool is bright,
 * a bypass carries an alarm-coloured border. That ordering matches how a player actually decides:
 * they choose a register first and a specific move second.
 *
 * <h2>⚠ Labels, not Buttons</h2>
 *
 * The same call {@code WindowFrame} makes for its window controls, for the same measured reason:
 * Modena's {@code .button} brings its own padding, its own focus ring and its own background insets,
 * none of which survive contact with a design language whose corner radius is zero everywhere. A
 * {@link Label} plus {@code es-focusable} plus explicit Space/Enter handling is the whole of what a
 * button is here, and it is fully keyboard-reachable — which a {@code setOnMouseClicked}-only chip
 * would not be, and which {@code docs/client/07-accessibility.md} requires.
 *
 * <h2>Preview fires on focus as well as hover</h2>
 *
 * Hover-only cost preview is a mouse feature. Focus fires it too, so a player driving the breach
 * from the keyboard gets the same forewarning — and the refusal string is set as accessible text as
 * well as tooltip text, because JavaFX tooltips are mouse-only and a refusal nobody can read is a
 * dead control.
 */
public final class CostStrip extends FlowPane {

    private Consumer<BreachAction> onInvoke = action -> {};
    private Consumer<BreachAction> onPreview = action -> {};

    public CostStrip() {
        super(UiTokens.SPACE_2, UiTokens.SPACE_2);
        getStyleClass().add("es-cost-strip");
    }

    public void setOnInvoke(Consumer<BreachAction> handler) {
        this.onInvoke = handler == null ? action -> {} : handler;
    }

    /** Called with the hovered or focused action, and with {@code null} when nothing is targeted. */
    public void setOnPreview(Consumer<BreachAction> handler) {
        this.onPreview = handler == null ? action -> {} : handler;
    }

    /** Replaces every chip. Actions arrive in the engine's display order and are not re-sorted. */
    public void show(List<BreachAction> actions) {
        getChildren().clear();
        if (actions == null) {
            return;
        }
        for (BreachAction action : actions) {
            getChildren().add(chip(action));
        }
    }

    private Label chip(BreachAction action) {
        // The cost is always printed — see the class comment. Zero is printed too: the Side-Channel
        // Reader's entire identity is that it reads without entering (05 §4), and a blank where the
        // number goes would hide the one action in the game that costs nothing.
        Label chip = Ui.label(action.label() + " " + AsciiCanvas.BULLET + " " + action.attentionCost());
        chip.getStyleClass().addAll("es-breach-chip", kindClass(action), "es-focusable");

        boolean enabled = action.enabled();
        if (!enabled) {
            chip.getStyleClass().add("es-breach-chip-off");
        }

        StringBuilder tip = new StringBuilder(Ui.upper(action.label()))
                .append(" — ")
                .append(action.attentionCost())
                .append(" attention");
        if (!action.detail().isBlank()) {
            tip.append('\n').append(action.detail());
        }
        if (!action.argumentHint().isBlank()) {
            tip.append("\nTakes: ").append(action.argumentHint());
        }
        if (!enabled && !action.refusal().isBlank()) {
            tip.append('\n').append(action.refusal());
        }
        Tooltip tooltip = new Tooltip(tip.toString());
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        tooltip.setShowDelay(Duration.millis(220));
        Tooltip.install(chip, tooltip);
        // The same content down the second path. A tooltip is mouse-only, and the refusal is the
        // one string that tells a blocked player what to do instead.
        chip.setAccessibleText(tip.toString().replace('\n', ' '));

        chip.setFocusTraversable(true);
        Cursors.shared().clickable(chip);

        chip.setOnMouseEntered(e -> onPreview.accept(action));
        chip.setOnMouseExited(e -> onPreview.accept(null));
        chip.focusedProperty().addListener((obs, was, now) -> onPreview.accept(now ? action : null));

        if (enabled) {
            chip.setOnMouseClicked(e -> onInvoke.accept(action));
            chip.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                    onInvoke.accept(action);
                    e.consume();
                }
            });
        }
        return chip;
    }

    /**
     * ⚠ A free action is styled by its cost, not by its kind.
     *
     * <p>{@link BreachActionKind#PROBE} covers both the ordinary two-attention move and the
     * zero-cost bookkeeping ones — {@code mark} on Enumeration and {@code set} on Logic, which the
     * implementation spec is explicit are "bookkeeping, not a move" and never touch the ledger.
     * Drawing them as probes would price them as probes in the player's head, which is exactly the
     * misread that makes someone hesitate before composing a guess.
     */
    private static String kindClass(BreachAction action) {
        if (action.attentionCost() == 0) {
            return "es-breach-chip-free";
        }
        return switch (action.kind()) {
            case QUIET_READ -> "es-breach-chip-quiet";
            case LOUD_TOOL -> "es-breach-chip-loud";
            case BYPASS -> "es-breach-chip-bypass";
            case SIDE_CHANNEL -> "es-breach-chip-free";
            case PROBE -> "es-breach-chip-probe";
        };
    }

    /** The chip captions in order. Test seam: proves every action prints a cost. */
    public List<String> captions() {
        return getChildren().stream()
                .filter(Label.class::isInstance)
                .map(node -> ((Label) node).getText())
                .toList();
    }
}
