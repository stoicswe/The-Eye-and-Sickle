package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.breach.BreachViewport;
import io.github.stoicswe.eyeandsickle.client.ui.breach.CostStrip;
import io.github.stoicswe.eyeandsickle.client.ui.breach.MatrixGrid;
import io.github.stoicswe.eyeandsickle.client.ui.breach.OffsetRack;
import io.github.stoicswe.eyeandsickle.client.ui.breach.OutcomeSlate;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.AttentionLedger;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.AttentionMeter;
import io.github.stoicswe.eyeandsickle.protocol.game.AttentionEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachAction;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.MatrixBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.OffsetBoard;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;

/**
 * The breach window's dispatch: what a click means, and what the port said back.
 *
 * <h2>Why the view does not talk to the port directly</h2>
 *
 * Every intent in this feature is the same three steps — call {@link GameSession}, surface whatever
 * came back, re-read the snapshot — and there are six of them ({@code begin}, an action, a tumbler
 * step, {@code abort}, {@code dismiss}, and the two selections that dispatch nothing). Spread across
 * a view they are six chances to forget the middle step, and the middle step is the one that keeps
 * {@code docs/client/04-terminology-and-education.md} §3.5 honest: a refusal, a gate and an
 * unreachable server are three different answers and must never collapse into one message, or into
 * silence.
 *
 * <p>So this class holds every call to the port and the view holds every {@link Node}. The view can
 * be read to find out what is on screen; this can be read to find out what the game was asked.
 *
 * <h2>Selections are a UI concept and live in the widgets</h2>
 *
 * "The cell my cursor is on" is not game state — the engine has no idea a pointer exists, and the
 * snapshot carries no cursor. Each board widget owns its own, this class reads it when an action asks
 * for an argument, and it is published as a sentence so a player can see what the next action will
 * act on <em>before</em> spending attention on finding out.
 *
 * <h2>⚠ Two boards, two argument shapes, and no third path</h2>
 *
 * A grid pick is {@code row:column} and a cipher action is a byte index. Both come from the widget
 * that drew them rather than from a field here, so the argument a chip sends is the cell the player
 * can see is highlighted — the two cannot drift apart, which is the bug the previous slot-index
 * field kept producing when a layer changed underneath it.
 */
public final class BreachPresenter {

    /**
     * Action ids the widgets dispatch directly, because the board <em>is</em> the control.
     *
     * <p>Taking a cell and typing a digit are the two moves a player makes by touching the board
     * rather than by pressing a chip, so the widget has to name them. Everything else is dispatched
     * generically off {@link BreachAction#argumentHint()} and needs no change here when the engine
     * grows a move — which is the property worth protecting, because the alternative is a client that
     * has to be re-released to learn one.
     */
    private static final String ACTION_PICK = "pick";

    private static final String ACTION_TYPE = "type";

    private final GameSession session;

    private BreachViewport viewport;
    private AttentionMeter meter;
    private AttentionLedger ledger;
    private CostStrip strip;
    private MatrixGrid grid;
    private OffsetRack cipher;
    private OutcomeSlate slate;

    private Consumer<String> noticeSink = text -> {};
    private Consumer<String> selectionSink = text -> {};

    private int boundLayer = -1;
    private String lastMessage = "";

    public BreachPresenter(GameSession session) {
        this.session = session;
    }

    /**
     * Attaches the renderers and wires their callbacks.
     *
     * <p>Called once. The widgets are long-lived and are re-shown rather than rebuilt, so nothing
     * here is safe to call twice — a second call would leave the first set of widgets subscribed to
     * a presenter that no longer draws them.
     */
    public void bind(
            BreachViewport viewport,
            AttentionMeter meter,
            AttentionLedger ledger,
            CostStrip strip,
            MatrixGrid grid,
            OffsetRack cipher,
            OutcomeSlate slate) {
        this.viewport = viewport;
        this.meter = meter;
        this.ledger = ledger;
        this.strip = strip;
        this.grid = grid;
        this.cipher = cipher;
        this.slate = slate;

        strip.setOnInvoke(this::invoke);
        strip.setOnPreview(this::preview);
        grid.setOnPick((row, column) -> invoke(ACTION_PICK, row + ":" + column));
        grid.setOnCursor(this::publishSelection);
        // ⚠ Every keystroke is sent. Composition is free and reversible until COMMIT
        // (docs/design/05 §3.7), and the draft lives in the engine so a reload cannot lose it — a
        // local buffer here would be a second copy of the answer that a reload could disagree with.
        cipher.setOnType((index, value) -> invoke(ACTION_TYPE, index + ":" + value));
        cipher.setOnCursor(this::publishSelection);
    }

    /** Where refusals, gates and argument hints are shown. Called with {@code ""} to clear. */
    public void setNoticeSink(Consumer<String> sink) {
        this.noticeSink = sink == null ? text -> {} : sink;
    }

    /** Where "what the next action will act on" is shown. Called with {@code ""} for nothing. */
    public void setSelectionSink(Consumer<String> sink) {
        this.selectionSink = sink == null ? text -> {} : sink;
    }

    // ------------------------------------------------------------------ reading

    /**
     * Re-reads the port and pushes it into the widgets.
     *
     * <p>Cheap and idempotent by construction: every widget's {@code show} takes the whole of its
     * state, so calling this twice for one change costs a repaint and nothing else. That is what lets
     * the view subscribe to {@code onChange} and the intents below call it again without either
     * having to know about the other.
     */
    public void refresh() {
        if (viewport == null) {
            return;
        }
        Optional<BreachSnapshot> found = session.breach();
        if (found.isEmpty()) {
            viewport.show(null);
            ledger.clear();
            strip.show(List.of());
            meter.preview(0, "");
            hideBoards();
            boundLayer = -1;
            clearSelection();
            return;
        }

        BreachSnapshot snapshot = found.get();
        viewport.show(snapshot);
        ledger.show(snapshot.ledger());

        if (snapshot.resolved()) {
            slate.show(snapshot);
            strip.show(List.of());
            meter.preview(0, "");
            hideBoards();
            return;
        }

        BreachLayer layer = snapshot.active().orElse(null);
        if (layer == null) {
            strip.show(List.of());
            hideBoards();
            return;
        }
        if (layer.index() != boundLayer) {
            // A new layer is a new board with a new coordinate space. Carrying slot 7 across from a
            // cleared Enumeration layer into a Traversal one would make the next action act on
            // something the player never picked.
            boundLayer = layer.index();
            clearSelection();
        }

        meter.show(layer.attention(), strikeCost(snapshot, layer));
        strip.show(snapshot.actions());
        showBoard(layer.board());
    }

    /** The last thing the port said, or empty when it has said nothing yet or last said nothing. */
    public Optional<String> lastMessage() {
        return lastMessage == null || lastMessage.isBlank() ? Optional.empty() : Optional.of(lastMessage);
    }

    // ------------------------------------------------------------------ intents

    /** Opens a breach on a target from {@link GameSession#breachTargets()}. */
    public void begin(String targetId) {
        surface(session.beginBreach(targetId));
        refresh();
    }

    /**
     * Dispatches an action chip, filling in the argument from the pending selection.
     *
     * <p>When the action needs a selection and there is none, this shows the engine's own
     * {@link BreachAction#argumentHint()} and <b>does not call the port</b>. Sending a call already
     * known to be refused for a reason the interface invented would put a line in the attention
     * ledger that no rule produced — and the ledger is the artefact §4 makes load-bearing.
     */
    public void invoke(BreachAction action) {
        if (action == null) {
            return;
        }
        if (!action.enabled()) {
            // Announced rather than printed inline: a client-side refusal reaches the log and the
            // notification system by the same route a rules refusal does, so the player sees one
            // kind of message in one place. See GameSession.refuse.
            session.refuse(
                    "breach",
                    action.refusal().isBlank() ? "that move is not available on this layer." : action.refusal());
            return;
        }
        BreachLayer layer = session.breach().flatMap(BreachSnapshot::active).orElse(null);
        if (layer == null) {
            session.refuse("breach", "no layer is active.");
            return;
        }
        String argument = argumentFor(action, layer);
        if (argument == null) {
            session.refuse(
                    "breach",
                    "pick a target for this action first — "
                            + (action.argumentHint().isBlank() ? "it needs one." : action.argumentHint()) + ".");
            return;
        }
        invoke(action.actionId(), argument);
    }

    /**
     * The raw form: an action id and its argument, exactly as {@code probe &lt;action&gt; [arg]} sends
     * them. Everything above eventually arrives here, and the shell reaches the same port method by
     * the same two strings — which is what makes the window and the terminal one surface rather than
     * two implementations that agree by convention.
     */
    public void invoke(String actionId, String argument) {
        surface(session.breachAction(actionId, argument == null ? "" : argument));
        refresh();
    }

    /**
     * Withdraws from the attempt.
     *
     * <p>Not a failure and not styled as one: {@code docs/client/01-visual-language.md} §2.2.7 is
     * explicit that painting {@code aborted} red teaches players not to use the escape hatch the
     * design gave them. Attention already spent stays spent and noise already made stays made, which
     * is the cost — the outcome slate says so.
     */
    public void abort() {
        surface(session.abortBreach());
        refresh();
    }

    /** Clears a resolved attempt, returning the window to the target list. */
    public void dismiss() {
        surface(session.dismissBreach());
        refresh();
    }

    /**
     * Previews an action's price on the meter.
     *
     * <p>{@code null} clears it. This is §4's requirement in its stronger form — not merely itemised
     * afterwards but priced beforehand, on hover <em>and</em> on focus, so the keyboard route sees the
     * same number the pointer does.
     */
    public void preview(BreachAction action) {
        if (meter == null) {
            return;
        }
        if (action == null) {
            meter.preview(0, "");
            return;
        }
        meter.preview(action.attentionCost(), action.label());
    }

    /** Says what the next chip would act on, in the board's own coordinates. */
    private void publishSelection() {
        BreachBoard board = session.breach()
                .flatMap(BreachSnapshot::active)
                .map(BreachLayer::board)
                .orElse(null);
        if (board instanceof MatrixBoard && !grid.selection().isBlank()) {
            String[] cell = grid.selection().split(":");
            selectionSink.accept("ROW " + cell[0] + " · COLUMN " + cell[1]);
        } else if (board instanceof OffsetBoard offsets && !cipher.selection().isBlank()) {
            int index = Integer.parseInt(cipher.selection());
            selectionSink.accept("BYTE " + (index + 1) + " OF " + offsets.length());
        } else {
            selectionSink.accept("");
        }
    }

    /** Releases the widgets' animation subscriptions. */
    public void dispose() {
        if (viewport != null) {
            viewport.dispose();
        }
        if (meter != null) {
            meter.dispose();
        }
    }

    // ------------------------------------------------------------------ internals

    /**
     * The argument an action wants, or {@code null} when it wants one and nothing is selected.
     *
     * <p>Driven off {@link BreachAction#argumentHint()} rather than off a list of action ids, so an
     * action the engine adds later works here without a client change. The hint's <em>text</em> is
     * never parsed — only whether it is blank — because the moment the client starts reading the
     * engine's prose for meaning, a wording change becomes a behaviour change.
     */
    private String argumentFor(BreachAction action, BreachLayer layer) {
        if (action.argumentHint().isBlank()) {
            return "";
        }
        BreachBoard board = layer.board();
        String selection =
                switch (board) {
                    case MatrixBoard ignored -> grid.selection();
                    case OffsetBoard ignored -> cipher.selection();
                    case null -> "";
                };
        return selection.isBlank() ? null : selection;
    }

    /**
     * Attention lost to strikes, as the ledger accounts for it.
     *
     * <p>Summed from the ledger rather than derived independently, and that is the point: the meter's
     * alarm cells and the ledger's alarm rows are two renderings of one set of entries, so they
     * cannot disagree about how much being loud cost. A player who counts the red cells and then
     * reads the rows must get the same number, or neither surface is evidence of anything.
     *
     * <p>Clamped to what the layer has actually spent, because a meter cannot lose more points than
     * were ever taken.
     */
    private static int strikeCost(BreachSnapshot snapshot, BreachLayer layer) {
        int lost = 0;
        for (AttentionEntry entry : snapshot.ledger()) {
            if (entry.alarm() && entry.layerIndex() == layer.index()) {
                lost += entry.cost();
            }
        }
        return Math.min(lost, layer.attention().spent());
    }

    private void showBoard(BreachBoard board) {
        if (board == null) {
            hideBoards();
            return;
        }
        switch (board) {
            case MatrixBoard matrix -> {
                grid.show(matrix);
                only(grid);
            }
            case OffsetBoard offsets -> {
                cipher.show(offsets);
                only(cipher);
            }
        }
        publishSelection();
    }

    /**
     * Shows one board and hides the other.
     *
     * <p>Both are built once and toggled rather than swapped in and out of the scene graph.
     * Rebuilding would reset scroll position and drop keyboard focus every time the port fires a
     * change, which during a breach means every turn. ⚠ It is also what the panel's key routing is
     * gated on — {@code BreachView} asks which board is visible before handing it an arrow.
     */
    private void only(Node shown) {
        for (Node node : new Node[] {grid, cipher}) {
            boolean on = node == shown;
            node.setVisible(on);
            node.setManaged(on);
        }
    }

    private void hideBoards() {
        only(null);
    }

    private void clearSelection() {
        selectionSink.accept("");
    }

    /**
     * Publishes whatever the port answered.
     *
     * <p>A success says nothing: the ledger already carries the line, and a toast repeating it would
     * be the second place a player has to look for the same fact. Everything else is shown, with the
     * status in the first word — {@code docs/client/01-visual-language.md} §9.4 forbids "the rules
     * refused this" and "we could not reach the server" from collapsing into one message, and
     * {@code docs/client/07} §5.2 forbids the distinction from resting on colour, so it rests on a
     * word.
     */
    /**
     * Remembers what the rules said.
     *
     * <p>⚠ It no longer pushes anywhere. Every intent this class calls goes through the session,
     * which writes a failed one to the rig's log on the way back — so pushing it to a panel strip as
     * well would show the same sentence twice, once in a place the player might not be looking and
     * once in a place they will. {@code lastMessage} stays because the outcome slate quotes it.
     */
    private void surface(GameSession.Outcome outcome) {
        lastMessage = outcome.message();
    }

    private static String lead(int status) {
        return switch (status) {
            case GameSession.Outcome.NOPERM -> "Gate — ";
            case GameSession.Outcome.UNAVAILABLE, GameSession.Outcome.TEMPFAIL -> "Unreachable — ";
            case GameSession.Outcome.USAGE -> "Usage — ";
            default -> "Refused — ";
        };
    }
}
