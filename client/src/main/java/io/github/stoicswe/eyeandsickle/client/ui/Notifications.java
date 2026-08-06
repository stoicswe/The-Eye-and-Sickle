package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Slide-in notices for things that finished, and things that went wrong.
 *
 * <h2>It is the log, filtered — not a second source of truth</h2>
 *
 * Every notice here is a {@link GameSession.LogLine} the rig already emitted. Nothing has its own
 * message, its own severity, or its own idea of what happened. That matters more than it sounds:
 * a notification system with its own copy of the events is one that can disagree with the log, and
 * {@code docs/design/04-mining.md} §3.1 makes noticing that two readouts disagree the way a player
 * catches a hidden miner. If the toast and the log can differ, that skill stops working.
 *
 * <p>It also means the filter is already meaningful. The log uses real RFC 5424 severities, so
 * "which notifications do I want" is a severity threshold — exactly {@code journalctl -p}'s
 * semantics, and the same habit the manual teaches. A player who sets it to <b>4</b> gets warnings
 * and worse, which is a sentence that is true of this game and of every Linux box they will ever
 * touch.
 *
 * <h2>Not a modal, and allowed to be missed</h2>
 *
 * §3 bans hidden UI and modals; a toast is neither. It never blocks, it never demands a click, and
 * <b>everything it says is permanently available in the log window</b> — which is precisely the
 * condition §3 sets for a transient surface. A player who ignores every notice loses nothing.
 *
 * <h2>Motion</h2>
 *
 * The slide is {@link Interpolator#DISCRETE} in {@link UiTokens#REVEAL_STEPS} steps, like every
 * other movement in this client. §9 lists easing curves as build-blocking, and a toast easing in
 * from the right is the single most recognisable piece of web-app furniture there is — it would
 * undo the aesthetic on its own. Under reduced motion notices appear in place and hold.
 */
public final class Notifications extends VBox {

    /** How long an ordinary notice stays. Long enough to read twice at reading speed. */
    private static final double DWELL_MS = 6000;

    /** Warnings and worse stay longer, because they are the ones with a decision attached. */
    private static final double DWELL_SEVERE_MS = 11000;

    /** Beyond this, older notices are dropped rather than stacked — see {@link #push}. */
    private static final int MAX_VISIBLE = 4;

    private final ClientProfile profile;
    private final Set<String> seen = new LinkedHashSet<>();
    private GameSession session;
    private AutoCloseable subscription;
    private boolean primed;

    public Notifications(ClientProfile profile) {
        super(UiTokens.SPACE_2);
        this.profile = profile;
        getStyleClass().add("es-toasts");
        setAlignment(Pos.TOP_RIGHT);
        setPickOnBounds(false);
        // Mouse-transparent as a whole: a notice must never intercept a click meant for the panel
        // underneath it. It has no actions of its own, so there is nothing to click.
        setMouseTransparent(true);
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);
    }

    /**
     * Starts watching a session.
     *
     * <p>The first pass only records what is already in the log without showing anything. Otherwise
     * loading a save would fire a notice for every event in its history — including the resume
     * summary, which is genuinely useful and would arrive buried under thirty others.
     */
    public void watch(GameSession session) {
        detach();
        this.session = session;
        this.primed = false;
        seen.clear();
        drain();
        this.primed = true;
        subscription = session.onChange(s -> drain());
        // The session does not fire a change event for everything that logs — a task completing on
        // a tick is the obvious case — so the stream is also polled. Cheap: it is a list scan.
        Pulse.shared().every(700, this::drain);
    }

    public void detach() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            subscription = null;
        }
        session = null;
        getChildren().clear();
    }

    /** Pulls anything new off the log and shows what passes the player's filter. */
    private void drain() {
        if (session == null) {
            return;
        }
        List<GameSession.LogLine> lines = session.log(RigEventSeverity.DEBUG, 60);
        List<GameSession.LogLine> fresh = new ArrayList<>();
        for (GameSession.LogLine line : lines) {
            if (seen.add(key(line))) {
                fresh.add(line);
            }
        }
        // Bounded, or a long session's seen-set grows for the lifetime of the process.
        while (seen.size() > 400) {
            var it = seen.iterator();
            it.next();
            it.remove();
        }
        if (!primed) {
            return;
        }
        for (GameSession.LogLine line : fresh) {
            if (wants(line)) {
                push(line);
                // ⚠ The chime rides on the notification the player already asked for, so a MUTED
                // facility is silent too — one decision, not two that can disagree. And it is here
                // rather than at the rules that WRITE the message: a message that arrived while the
                // client was closed is drained on the next load and would otherwise be announced
                // silently, or announced on a tick nobody was watching.
                //
                // ⚠ `primed` above is what stops the whole backlog chiming at startup. Without it,
                // opening the game after a few days away plays the sound once per stored message.
                if ("comms".equals(line.facility())) {
                    io.github.stoicswe.eyeandsickle.client.sound.Sfx.message();
                }
            }
        }
    }

    private static String key(GameSession.LogLine line) {
        return line.at().toEpochMilli() + "|" + line.facility() + "|" + line.message();
    }

    /**
     * Whether the player asked to see this.
     *
     * <p>Two filters, both theirs: a severity floor with {@code journalctl -p} semantics, and a set
     * of muted facilities. Facility rather than "category" because that is what the log calls it and
     * what {@code log} prints — one vocabulary, so a player who mutes {@code mining} here can also
     * type {@code log} and see exactly what they muted.
     */
    private boolean wants(GameSession.LogLine line) {
        var settings = profile.settings();
        if (!settings.notificationsEnabled) {
            return false;
        }
        // Remember the numbering runs backwards: 4 (warning) is MORE severe than 6 (info).
        if (line.severity() > settings.notifyMinSeverity) {
            return false;
        }
        return !settings.mutedFacilities.contains(line.facility());
    }

    private void push(GameSession.LogLine line) {
        Region toast = build(line);
        getChildren().add(toast);

        // Oldest first, because the newest notice is the one being read. Dropping the newest to
        // protect the stack would hide exactly the thing that just happened.
        while (getChildren().size() > MAX_VISIBLE) {
            getChildren().removeFirst();
        }

        boolean severe = line.severity() <= RigEventSeverity.WARNING;
        slideIn(toast);
        double dwell = severe ? DWELL_SEVERE_MS : DWELL_MS;
        Timeline expiry = new Timeline(
                new KeyFrame(Duration.millis(dwell), e -> getChildren().remove(toast)));
        expiry.play();
    }

    private Region build(GameSession.LogLine line) {
        boolean severe = line.severity() <= RigEventSeverity.WARNING;

        Label glyph = new Label(line.glyph());
        glyph.getStyleClass().add(severe ? "es-toast-glyph-severe" : "es-toast-glyph");
        // The keyword travels with the glyph, always. docs/client/07 §5.2: meaning may not rest on
        // appearance, and a symbol with no word beside it is a private code.
        Label keyword = Ui.label(line.keyword());
        Label facility = Ui.label(line.facility());
        facility.getStyleClass().add("es-legend-sub");

        HBox head = Ui.row(UiTokens.SPACE_3, glyph, keyword, facility);

        Label message = Ui.body(line.message());
        message.setWrapText(true);
        message.getStyleClass().add("es-toast-message");

        VBox body = new VBox(3, head, message);
        body.getStyleClass().addAll("es-toast", severe ? "es-toast-severe" : "es-toast-normal");
        body.setMaxWidth(340);
        body.setPrefWidth(340);

        // ⚠ The frost goes UNDER the toast's own translucent fill, in a holder, rather than into its
        // Background. A JavaFX Background paints its fills first and its images ON TOP of them, so a
        // BackgroundImage would cover the tint instead of sitting beneath it — the layering would be
        // exactly inverted, and only visibly so once the tint stopped being opaque.
        javafx.scene.image.ImageView frost = new javafx.scene.image.ImageView();
        frost.setMouseTransparent(true);
        // ⚠ UNMANAGED, or the toast grows to the size of the desk. The image is a picture of the
        // WHOLE deck, so a managed child reports ~1600×1000 as its preferred size and the Pane
        // holding it asks for exactly that — the first notice rendered several hundred pixels tall
        // with its text in the top corner. Unmanaged means it is placed by `relocate` and measured
        // by nobody, which is what this needs.
        frost.setManaged(false);
        javafx.scene.layout.Pane frostHolder = new javafx.scene.layout.Pane(frost);
        frostHolder.setMouseTransparent(true);
        StackPane framed = new StackPane(frostHolder, body);
        framed.getStyleClass().add("es-toast-framed");
        framed.setMaxWidth(340);
        framed.setPrefWidth(340);
        framed.setMaxHeight(Region.USE_PREF_SIZE);
        // Clipped to the toast's own box, or the whole-deck image paints across the screen.
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(framed.widthProperty());
        clip.heightProperty().bind(framed.heightProperty());
        frostHolder.setClip(clip);
        frosted.put(framed, frost);
        return framed;
    }

    /**
     * Each live toast and the view carrying its blurred backdrop.
     *
     * <p>⚠ An {@link java.util.WeakHashMap} so a dismissed notice is not held here after the scene
     * has dropped it — toasts are created and discarded continuously, and this map is the only thing
     * that would otherwise keep every one of them alive for the life of the deck.
     */
    private final java.util.Map<Region, javafx.scene.image.ImageView> frosted = new java.util.WeakHashMap<>();

    /**
     * Re-aims every live notice at a fresh capture of what it is floating over.
     *
     * <p>⚠ Called by {@code DeckShell} from the frost clock, and <b>only when a notice is on
     * screen</b>: an overlay backdrop is a second full capture of the deck, so paying for it while
     * the notification stack is empty would roughly double the cost of the effect for nothing.
     *
     * @param deck the node the notices float over
     */
    public void refreshFrost(javafx.scene.Node deck) {
        if (getChildren().isEmpty() || !isVisible()) {
            return;
        }
        javafx.scene.image.Image backdrop =
                io.github.stoicswe.eyeandsickle.client.ui.chrome.Frost.overlayBackdrop(deck, java.util.List.of(this));
        for (javafx.scene.Node child : getChildren()) {
            javafx.scene.image.ImageView view = frosted.get(child);
            if (view != null) {
                io.github.stoicswe.eyeandsickle.client.ui.chrome.Frost.placeOverlay(view, child, deck, backdrop);
            }
        }
    }

    /**
     * Slides a notice in from the right in discrete steps.
     *
     * <p>{@link Interpolator#DISCRETE}, not a tween — see the class comment. Under reduced motion it
     * simply appears, which §5 requires and which is also the better behaviour: a notice is
     * information, and the animation was never carrying any of it.
     */
    private void slideIn(Region toast) {
        if (Pulse.shared().reducedMotion()) {
            return;
        }
        double travel = 360;
        toast.setTranslateX(travel);
        Timeline slide = new Timeline();
        double step = UiTokens.REVEAL_MS / UiTokens.REVEAL_STEPS;
        for (int i = 1; i <= UiTokens.REVEAL_STEPS; i++) {
            double remaining = travel * (1 - i / (double) UiTokens.REVEAL_STEPS);
            slide.getKeyFrames()
                    .add(new KeyFrame(
                            Duration.millis(step * i),
                            new KeyValue(toast.translateXProperty(), remaining, Interpolator.DISCRETE)));
        }
        slide.play();
    }

    /** Shows a notice the client itself produced, with no log line behind it. */
    public void say(String facility, String message, boolean severe) {
        push(new GameSession.LogLine(
                Instant.now(),
                severe ? RigEventSeverity.WARNING : RigEventSeverity.NOTICE,
                facility,
                message,
                severe ? "warning" : "notice",
                // The same glyphs RigEvent uses for these levels, so a client-originated notice is
                // indistinguishable from a rig-originated one — which is the point, since the
                // notice stack is supposed to be one surface. Both are verified font-covered.
                RigEventSeverity.glyphFor(severe)));
    }

    /** RFC 5424 levels, mirrored so this class does not reach into the solo module. */
    public static final class RigEventSeverity {
        public static final int WARNING = 4;
        public static final int NOTICE = 5;
        public static final int INFORMATIONAL = 6;
        public static final int DEBUG = 7;

        /**
         * The glyph for a client-originated notice.
         *
         * <p>Delegates to the engine's own table rather than repeating two characters here. That is
         * not tidiness: {@code GlyphCoverageTest} checks every severity glyph against the bundled
         * font cmaps, and a second copy in this file is a copy the check would pass while the
         * shipped client rendered a host-OS fallback.
         */
        static String glyphFor(boolean severe) {
            return io.github.stoicswe.eyeandsickle.engine.state.RigEvent.glyph(severe ? WARNING : NOTICE);
        }

        private RigEventSeverity() {}
    }
}
