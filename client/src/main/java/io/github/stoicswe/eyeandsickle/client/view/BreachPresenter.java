package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.breach.BreachViewport;
import io.github.stoicswe.eyeandsickle.client.ui.breach.CostStrip;
import io.github.stoicswe.eyeandsickle.client.ui.breach.LatticeMap;
import io.github.stoicswe.eyeandsickle.client.ui.breach.OutcomeSlate;
import io.github.stoicswe.eyeandsickle.client.ui.breach.PortComb;
import io.github.stoicswe.eyeandsickle.client.ui.breach.TumblerRack;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.AttentionLedger;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.AttentionMeter;
import io.github.stoicswe.eyeandsickle.protocol.game.AttentionEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachAction;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.EnumerationBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.LogicBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.TraversalBoard;
import java.util.List;
import java.util.Locale;
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
 * <h2>Selections are a UI concept and stop at this class</h2>
 *
 * "The slot I last clicked" is not game state — the engine has no idea a pointer exists, and the
 * snapshot carries no cursor. It lives here, it is cleared whenever the active layer changes (a slot
 * index from a cleared Enumeration layer must not silently become an argument on the next one), and
 * it is published as a sentence so a player can see what the next action will act on <em>before</em>
 * spending attention on finding out.
 *
 * <h2>The one derivation this class performs, and why it is not a rule</h2>
 *
 * {@code sweep} takes a <em>band</em> index while the comb emits <em>slot</em> indices, so clicking
 * slot 7 on a board with 4-slot bands sweeps band 1. That arithmetic is over
 * {@link EnumerationBoard#bandSize()}, which the engine published in the snapshot, and it changes no
 * outcome — the engine still decides what a sweep of band 1 reveals. Without it a player could not
 * reach {@code sweep} from the board at all, which pillar C1 does not allow.
 */
public final class BreachPresenter {

    /**
     * Action ids the client has to recognise by name.
     *
     * <p>Only these two, and both because the <em>shape of the argument</em> differs from every other
     * action in their class ({@code docs/design/05-hacking-minigame.md} §3.6 and §3.7 fix the
     * tables). Every other action is dispatched generically off {@link BreachAction#argumentHint()},
     * so adding one to the engine needs no change here — which is the property worth protecting,
     * because the alternative is a client that has to be re-released to learn a new move.
     */
    private static final String ACTION_SWEEP = "sweep";

    private static final String ACTION_SET = "set";

    private final GameSession session;

    private BreachViewport viewport;
    private AttentionMeter meter;
    private AttentionLedger ledger;
    private CostStrip strip;
    private PortComb comb;
    private TumblerRack rack;
    private LatticeMap map;
    private OutcomeSlate slate;

    private Consumer<String> noticeSink = text -> {};
    private Consumer<String> selectionSink = text -> {};

    private int selectedSlot = -1;
    private String selectedNode = "";
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
            PortComb comb,
            TumblerRack rack,
            LatticeMap map,
            OutcomeSlate slate) {
        this.viewport = viewport;
        this.meter = meter;
        this.ledger = ledger;
        this.strip = strip;
        this.comb = comb;
        this.rack = rack;
        this.map = map;
        this.slate = slate;

        strip.setOnInvoke(this::invoke);
        strip.setOnPreview(this::preview);
        comb.setOnSlot(this::selectSlot);
        map.setOnNode(this::selectNode);
        rack.setOnCycle(this::cycleTumbler);
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
            noticeSink.accept("Refused — "
                    + (action.refusal().isBlank() ? "that move is not available on this layer."
                            : action.refusal()));
            return;
        }
        BreachLayer layer = session.breach().flatMap(BreachSnapshot::active).orElse(null);
        if (layer == null) {
            noticeSink.accept("Refused — no layer is active.");
            return;
        }
        String argument = argumentFor(action, layer);
        if (argument == null) {
            noticeSink.accept("Pick a target for this action first — "
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

    /** Records the slot an Enumeration action will act on. Costs nothing; spends nothing. */
    public void selectSlot(int index) {
        selectedSlot = index;
        selectedNode = "";
        selectionSink.accept(slotLabel(index));
    }

    /** Records the node a Traversal action will act on. */
    public void selectNode(String nodeId) {
        selectedNode = nodeId == null ? "" : nodeId;
        selectedSlot = -1;
        selectionSink.accept(selectedNode.isBlank() ? "" : "NODE " + selectedNode);
    }

    /**
     * Steps one tumbler through the alphabet and submits the change as a {@code set}.
     *
     * <p>The draft lives in the engine, not here — §3.7 makes {@code set} a real (free) action so
     * that a reload cannot lose a half-composed guess. The client only computes <em>which</em> symbol
     * comes next, from the alphabet and the draft the snapshot already published.
     */
    public void cycleTumbler(int position, int delta) {
        LogicBoard board = session.breach()
                .flatMap(BreachSnapshot::active)
                .map(BreachLayer::board)
                .filter(LogicBoard.class::isInstance)
                .map(LogicBoard.class::cast)
                .orElse(null);
        if (board == null || board.alphabet().isEmpty() || position < 0 || position >= board.length()) {
            return;
        }
        List<String> alphabet = board.alphabet();
        String current = position < board.draft().size() ? board.draft().get(position) : "";
        int at = alphabet.indexOf(current);
        int next = at < 0
                // An unset position starts at whichever end the player stepped from, so one press
                // always lands on a symbol rather than on the same blank it started at.
                ? (delta >= 0 ? 0 : alphabet.size() - 1)
                : Math.floorMod(at + delta, alphabet.size());
        invoke(ACTION_SET, position + ":" + alphabet.get(next));
    }

    /** Releases the widgets' animation subscriptions. */
    public void dispose() {
        if (viewport != null) {
            viewport.dispose();
        }
        if (meter != null) {
            meter.dispose();
        }
        if (comb != null) {
            comb.dispose();
        }
        if (rack != null) {
            rack.dispose();
        }
        if (map != null) {
            map.dispose();
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
        if (board instanceof EnumerationBoard enumeration) {
            if (selectedSlot < 0) {
                return null;
            }
            if (ACTION_SWEEP.equals(action.actionId())) {
                int bandSize = Math.max(1, enumeration.bandSize());
                return String.valueOf(selectedSlot / bandSize);
            }
            return String.valueOf(selectedSlot);
        }
        if (board instanceof TraversalBoard) {
            return selectedNode.isBlank() ? null : selectedNode;
        }
        // Logic composes its guess on the rack, so a Logic action that asks for an argument has no
        // selection to draw on. Saying so beats sending an empty one and having the engine refuse it.
        return null;
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
            case EnumerationBoard enumeration -> {
                comb.show(enumeration);
                only(comb);
            }
            case LogicBoard logic -> {
                rack.show(logic);
                only(rack);
            }
            case TraversalBoard traversal -> {
                map.show(traversal);
                only(map);
            }
        }
    }

    /**
     * Shows one board and hides the other two.
     *
     * <p>All three are built once and toggled rather than swapped in and out of the scene graph.
     * Rebuilding would reset scroll position and drop keyboard focus every time the port fires a
     * change, which during a breach means every turn.
     */
    private void only(Node shown) {
        for (Node node : new Node[] {comb, rack, map}) {
            boolean on = node == shown;
            node.setVisible(on);
            node.setManaged(on);
        }
    }

    private void hideBoards() {
        only(null);
    }

    private void clearSelection() {
        selectedSlot = -1;
        selectedNode = "";
        selectionSink.accept("");
    }

    private String slotLabel(int index) {
        String slot = String.format(Locale.ROOT, "SLOT %02d", index);
        int bandSize = session.breach()
                .flatMap(BreachSnapshot::active)
                .map(BreachLayer::board)
                .filter(EnumerationBoard.class::isInstance)
                .map(board -> ((EnumerationBoard) board).bandSize())
                .orElse(0);
        return bandSize > 0 ? slot + " · BAND " + (index / bandSize) : slot;
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
    private void surface(GameSession.Outcome outcome) {
        lastMessage = outcome.message();
        if (outcome.succeeded()) {
            noticeSink.accept("");
            return;
        }
        String body = outcome.message().isBlank()
                ? "the rules declined it and did not say why."
                : outcome.message();
        noticeSink.accept(lead(outcome.status()) + body);
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
