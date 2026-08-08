package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import io.github.stoicswe.eyeandsickle.client.events.EventBus;
import io.github.stoicswe.eyeandsickle.client.events.EventTypes;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * The in-game window manager.
 *
 * <h2>Why the client draws its own windows</h2>
 *
 * {@code docs/design/ui-design-language.md} §0 cancels the separate-{@code Stage}-per-tool model:
 * native chrome puts real macOS traffic lights and Windows title bars around the game, and "the
 * entire aesthetic depends on the player never seeing their own operating system." So there is one
 * undecorated {@code Stage}, and everything inside it — dragging, focus, z-order, minimise,
 * maximise, close — happens here.
 *
 * <h2>Both placement modes, because the question was never settled</h2>
 *
 * §11 question 1 asks whether windows should snap to a coarse grid or drag freely and answers
 * "prototype both": snapping reinforces the character-cell language and makes the Bandwidth cap
 * legible; free-drag feels more like an operator's desk. Both are built, and
 * {@link Placement} switches between them at runtime from Settings — the honest resolution of an
 * open question is to ship the choice, not to pick one and delete the evidence.
 *
 * <p>{@link Placement#SNAP} also does edge tiling: dragging a window against an edge of the desk
 * tiles it to that half, and into a corner to that quarter. That is what makes §3's tiling ideal
 * reachable by hand — panels that abut and share edges — without giving up windows that can be
 * moved at all.
 *
 * <h2>The desk is a mechanic, not a skin</h2>
 *
 * §8: Bandwidth caps how many tool windows can be open at once. {@link #setWindowCap(int)} is that
 * cap, and hitting it produces a refusal naming the constraint rather than a window that silently
 * fails to appear. When screen real estate is attention, the window manager is a game system.
 */
public final class DeskManager {

    /** ⚠ JUL — captured by {@code log/ClientLog} for the CLIENT LOGS tab. */
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(DeskManager.class.getName());

    /** How close to an edge counts as a resize grip. Wide enough to hit, narrow enough not to. */
    static final double RESIZE_MARGIN = 6;

    /** Corners get a larger grip — see {@link #edgeAt}. */
    static final double CORNER_GRIP = 14;

    /** How close to the desk edge a drag must get to tile. */
    private static final double TILE_ZONE = 24;

    private static final double MIN_WIDTH = 240;
    private static final double MIN_HEIGHT = 120;

    /** Offset between cascaded windows, on the snap lattice so cascading never breaks alignment. */
    private static final double CASCADE = UiTokens.SNAP_GRID * 2;

    private final Pane desk = new Pane();
    private final Region snapPreview = new Region();
    private Region backdrop;
    private final Map<String, DeskWindow> windows = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private Placement placement = Placement.SNAP;
    private DeskWindow focused;
    private int windowCap = Integer.MAX_VALUE;
    private String capName = "Bandwidth";
    private Consumer<String> onRefusal = message -> {};

    /** Where this desk narrates itself. Null until {@link #setEventBus} is called. */
    private EventBus bus;

    private int cascadeIndex;

    public DeskManager() {
        desk.getStyleClass().add("es-desk");
        snapPreview.getStyleClass().add("es-snap-preview");
        snapPreview.setVisible(false);
        snapPreview.setMouseTransparent(true);
        // ⚠ Unmanaged, like every window. A managed child of a Pane is positioned by the Pane's own
        // layoutChildren on the next pass, which silently undoes resizeRelocate — the window manager
        // appears to work, and then every panel snaps back to the top-left at zero size the first
        // time anything triggers a layout. This one line is what makes the desk a desk.
        snapPreview.setManaged(false);
        desk.getChildren().add(snapPreview);
        // Clicking bare desk drops focus, so the focused-window highlight always names something
        // the player can see rather than a window that was closed three actions ago.
        desk.setOnMousePressed(e -> {
            if (e.getTarget() == desk) {
                focus(null);
            }
        });
        // ⚠ The backdrop follows the DESK's own size, not a reflow() call from the shell. The shell
        // reflows on the deck's width/height listeners, which fire when the BorderPane's own size
        // changes — that is BEFORE the BorderPane lays out its centre, so `desk.getWidth()` is still
        // the previous value at that moment. Windows survive it because they only clamp against the
        // desk; the backdrop does not, and the symptom is a wallpaper permanently sized 0×0 while
        // every widget test and every panel around it renders perfectly. Measured, not theorised.
        desk.widthProperty().addListener((obs, was, now) -> {
            layoutBackdrop();
            scheduleFrost();
        });
        desk.heightProperty().addListener((obs, was, now) -> {
            layoutBackdrop();
            scheduleFrost();
        });
    }

    public Region root() {
        return desk;
    }

    /**
     * Puts a node behind every window on the desk — the wallpaper.
     *
     * <p>Kept generic on purpose: the window manager's job is z-order and geometry, and what the
     * backdrop actually draws belongs to {@code ui/widgets}. This is the seam between them.
     *
     * <p>⚠ <b>Unmanaged and mouse-transparent, for two different reasons.</b> Unmanaged because a
     * managed child of a {@code Pane} is repositioned by the Pane's own {@code layoutChildren},
     * which silently undoes {@code resizeRelocate} — the same trap every desk window is
     * {@code setManaged(false)} for. Mouse-transparent because {@link #focus} is dropped on a press
     * whose target <em>is</em> the desk; a backdrop that accepted events would become the target and
     * clicking bare desk would stop clearing focus, which no geometry test would catch.
     *
     * <p>Inserted at index 0 so it sits under the snap preview as well as under the windows.
     */
    public void setBackdrop(Region backdrop) {
        if (this.backdrop != null) {
            desk.getChildren().remove(this.backdrop);
        }
        this.backdrop = backdrop;
        if (backdrop == null) {
            return;
        }
        backdrop.setManaged(false);
        backdrop.setMouseTransparent(true);
        desk.getChildren().add(0, backdrop);
        layoutBackdrop();
    }

    private void layoutBackdrop() {
        if (backdrop != null) {
            backdrop.resizeRelocate(0, 0, desk.getWidth(), desk.getHeight());
        }
    }

    /** Free-drag or snap-to-grid. §11 question 1, shipped as a setting rather than a decision. */
    public enum Placement {
        SNAP,
        FREE
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
        if (placement == Placement.SNAP) {
            for (DeskWindow window : windows.values()) {
                window.setGeometry(snap(window.x, window.y, window.width, window.height));
            }
        }
        notifyListeners();
    }

    public Placement placement() {
        return placement;
    }

    /**
     * The Bandwidth cap from §8.
     *
     * @param cap how many windows may be open at once
     * @param resourceName what to blame when the cap is hit — the player should learn which stat to
     *     raise, and "too many windows" teaches nothing
     */
    public void setWindowCap(int cap, String resourceName) {
        this.windowCap = cap;
        this.capName = resourceName;
        notifyListeners();
    }

    public int windowCap() {
        return windowCap;
    }

    public void setOnRefusal(Consumer<String> onRefusal) {
        this.onRefusal = onRefusal == null ? message -> {} : onRefusal;
    }

    /**
     * The bus this desk narrates itself onto. Optional — a desk with none still works.
     *
     * <p>⚠ Optional on purpose, and it is what keeps the window manager testable. {@code DeskManager}
     * is exercised by tests and by three snapshot mains that have no session and therefore no bus, and
     * a required dependency here would mean either wiring an unused broker into all of them or the
     * window manager knowing what a {@code GameSession} is. Null-checked at three call sites is the
     * cheaper of those.
     */
    public void setEventBus(EventBus bus) {
        this.bus = bus;
    }

    /** Publishes one window event, if there is a bus to publish it to. */
    private void publish(String what, String id) {
        if (bus != null && id != null) {
            bus.publish(EventTypes.of(EventTypes.WINDOW + "." + what), "/client/desk", id);
        }
    }

    /** Called whenever the window set, focus or placement changes — for the switcher and the rail. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
        // ⚠ THE CHOKEPOINT for re-frosting, and it is here rather than at the twelve call sites for
        // the reason this codebase records repeatedly: a per-caller hook is one new caller away from
        // a window whose blur shows a stack that no longer exists. Opening, closing, raising,
        // minimising, restoring and focusing all pass through here.
        scheduleFrost();
    }

    // ── The frosted backdrop (§9.4) ──────────────────────────────────────────────────────────

    /** Whether the palette in force asks for a blurred backdrop. */
    private boolean frosted;

    /** Set while a re-frost is already queued, so a burst of changes costs one capture. */
    private boolean frostPending;

    /**
     * Turns the blurred backdrop on or off for the whole desk.
     *
     * @param enabled true under a glass palette; false clears every frame's image
     */
    public void setFrosted(boolean enabled) {
        this.frosted = enabled;
        scheduleFrost();
        retimeFrost();
    }

    /**
     * The frost's own clock — {@link UiTokens#FROST_MS}, i.e. 24fps.
     *
     * <p>⚠ <b>Not a {@code Pulse} subscription.</b> Pulse drives at 100ms and quantises every
     * subscriber to a multiple of it, so a request for 24fps rounds silently to 10. Reaching 24
     * through Pulse means lowering the shared driver, which speeds up every decorative widget in the
     * client to fix one of them.
     *
     * <p>⚠ A {@code Timeline} with an action-only {@code KeyFrame} interpolates nothing, so §5's ban
     * on easing is not in play and neither contract test fires: this is a sampling rate, not a tween.
     */
    private javafx.animation.Timeline frostClock;

    /**
     * Starts or stops the 24fps re-capture.
     *
     * <h2>⚠ Reduced motion falls back to event-driven, and does not simply stop</h2>
     *
     * A frost that froze entirely would be <em>wrong</em> rather than merely still — it would show a
     * picture of a desk that has since changed, which is worse than a stale-but-plausible blur. So
     * under reduced motion the clock stops and {@link #scheduleFrost} still refreshes on every
     * structural change: correct after every interaction, and never moving on its own. That is what
     * WCAG 2.2.2 asks for, and it is the same relationship {@code WallpaperMode.moves()} encodes.
     */
    private void retimeFrost() {
        boolean shouldTick = frosted
                && !io.github.stoicswe.eyeandsickle.client.ui.Pulse.shared().reducedMotion();
        if (!shouldTick) {
            if (frostClock != null) {
                frostClock.stop();
            }
            return;
        }
        if (frostClock == null) {
            frostClock = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(io.github.stoicswe.eyeandsickle.client.ui.UiTokens.FROST_MS),
                    event -> pacedFrost()));
            frostClock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        }
        if (frostClock.getStatus() != javafx.animation.Animation.Status.RUNNING) {
            frostClock.play();
        }
    }

    /** When the next capture is allowed to start, from {@link System#nanoTime}. */
    private long frostReadyAt;

    /**
     * Run after each window re-frost, for overlays the desk does not own.
     *
     * <p>The notification stack floats above the whole deck rather than sitting on it, so its
     * backdrop is a different picture from any window's — see {@code Frost.overlayBackdrop}. The
     * shell owns that layer, so it supplies the callback rather than this class reaching upward.
     * ⚠ It is inside the PACED section deliberately: an overlay backdrop is a second full capture,
     * and running it on the raw clock would put the expensive path back.
     */
    private Runnable onFrosted;

    public void setOnFrosted(Runnable onFrosted) {
        this.onFrosted = onFrosted;
    }

    /**
     * One tick of the frost clock, refusing to run more often than {@link UiTokens#FROST_BUDGET}.
     *
     * <h2>⚠ 24fps is a ceiling, not a rate, and this is where that is enforced</h2>
     *
     * A refresh costs one snapshot per overlapping window plus one shared, so it is ~9ms tiled and
     * ~34ms with four windows fully cascaded. Running a 34ms job 24 times a second hands the thread
     * to the blur precisely when the player has the most on screen. Measuring the last one and
     * spacing the next by {@code cost / budget} keeps the frost <b>correct</b> at any window count
     * and lets only its frequency degrade — 24fps when it is cheap, about 7fps when it is not.
     *
     * <p>⚠ <b>{@code System.nanoTime}, deliberately, and this is the one place that rule inverts.</b>
     * Everything with a deadline in this client takes the session clock so a test can wind it. This
     * is not a deadline in the game; it is a measurement of how long this machine took to do a piece
     * of work, and a wound clock would make the pacing believe a 34ms capture was instant. Same
     * reasoning as the event bus's {@code time} field.
     */
    private void pacedFrost() {
        long now = System.nanoTime();
        if (now < frostReadyAt) {
            return;
        }
        Frost.refresh(desk, frosted);
        if (onFrosted != null) {
            onFrosted.run();
        }
        long spent = System.nanoTime() - now;
        double gapMs = Math.max(UiTokens.FROST_MS, spent / 1_000_000.0d / UiTokens.FROST_BUDGET);
        frostReadyAt = now + (long) (gapMs * 1_000_000.0d);
    }

    /** Stops the frost clock. Called when the deck goes away, so it does not tick over a dead desk. */
    public void stopFrost() {
        if (frostClock != null) {
            frostClock.stop();
        }
    }

    /**
     * Queues a re-capture of every window's backdrop.
     *
     * <p>⚠ <b>Deferred, and never synchronous.</b> {@code snapshot} forces a CSS and layout pass, so
     * calling it from inside one — which is where most of these events originate — re-enters layout
     * and can loop. ⚠ <b>Coalesced</b>, because opening a window fires several notifications and
     * each capture re-renders the desk once per window; without the flag, restoring a saved layout
     * of six windows would capture thirty-odd times before the first frame was drawn.
     */
    /**
     * Captures every window's backdrop <b>now</b>, rather than on the next pulse of the FX queue.
     *
     * <p>⚠ Exists for the render harness and for nothing else. {@link #scheduleFrost} defers through
     * {@code Platform.runLater}, and <b>no queued runnable ever executes during a synchronous
     * {@code Scene.snapshot}</b> — so a harness that only called the normal path would photograph
     * every glass window with no blur behind it and report the feature as working. That is this
     * repo's recurring failure mode: a render capturing the one state indistinguishable from the
     * feature being absent.
     */
    public void frostNow() {
        Frost.refresh(desk, frosted);
        // ⚠ The overlay layer too, or a harness photographs frosted WINDOWS and unfrosted NOTICES —
        // which is the same trap this method exists for, one layer up.
        if (onFrosted != null) {
            onFrosted.run();
        }
    }

    /** How many windows are open. Used by the render harness's frost benchmark. */
    public int windowCount() {
        return windows.size();
    }

    private void scheduleFrost() {
        // ⚠ Redundant while the clock is running — it will re-capture within 42ms anyway — and
        // skipping it is what stops a burst of window events costing a second full cycle each.
        if (frostPending
                || (frostClock != null && frostClock.getStatus() == javafx.animation.Animation.Status.RUNNING)) {
            return;
        }
        frostPending = true;
        javafx.application.Platform.runLater(() -> {
            frostPending = false;
            Frost.refresh(desk, frosted);
        });
    }

    // ── Opening and closing ──────────────────────────────────────────────────────────────────

    /**
     * Opens a window, or raises it if it is already open.
     *
     * <p>Raising rather than duplicating is not a convenience: two windows showing the same tool
     * would each be a live view of the same session state, and the player would have no way to tell
     * which one they were reading.
     *
     * @return the window, or empty if the Bandwidth cap refused it
     */
    public Optional<DeskWindow> open(Spec spec) {
        // ⚠ FINE. A player opens and closes windows constantly, so this belongs below the lifecycle
        // lines rather than beside them — but it is the single most useful thing to have when a
        // panel misbehaves, because it says what was on screen and in what order.
        LOG.log(java.util.logging.Level.FINE, "opening window {0}", spec.id());
        DeskWindow existing = windows.get(spec.id());
        if (existing != null) {
            existing.setMinimized(false);
            focus(existing);
            // ⚠ `raised`, not `opened`. They are different things and a debugging record that called
            // them both "opened" would show a tool being opened five times when it was opened once
            // and brought forward four times — which is exactly the distinction someone chasing a
            // duplicate-window bug is looking for.
            publish("raised", spec.id());
            return Optional.of(existing);
        }
        if (openCount() >= windowCap) {
            onRefusal.accept(capName.toUpperCase(java.util.Locale.ROOT) + " EXHAUSTED — " + windowCap
                    + " WINDOWS IS THE LIMIT. CLOSE ONE FIRST.");
            publish("refused", spec.id());
            return Optional.empty();
        }

        WindowFrame frame = new WindowFrame(spec.title(), spec.identifier());
        frame.setContent(spec.content());
        DeskWindow window = new DeskWindow(spec.id(), frame, spec.closable(), spec.onClosed());
        frame.setControls(
                () -> window.setMinimized(true),
                () -> window.toggleMaximized(),
                spec.closable() ? () -> close(spec.id()) : null);

        windows.put(spec.id(), window);
        // See the note in the constructor: the desk positions its own children, so they must not be
        // managed by the Pane.
        frame.setManaged(false);
        desk.getChildren().add(frame);
        snapPreview.toFront();

        window.setGeometry(placeNew(spec));
        install(window);
        focus(window);
        notifyListeners();
        publish("opened", spec.id());
        return Optional.of(window);
    }

    /**
     * Turns rounded corners on or off for every window on the desk (§9.3).
     *
     * <p>⚠ Every live frame is re-laid, not just new ones. The clip is computed in
     * {@code layoutChildren}, so a frame that is not asked to lay out again keeps the shape it was
     * born with — and the player would see the setting take effect only on windows they opened
     * afterwards, which reads as the toggle being broken rather than lazy.
     */
    /**
     * Sets the control order for every window on the desk, open ones included.
     *
     * <p>⚠ Desk windows only. The outer game window keeps following the host OS — it sits beside
     * the player's real windows and is judged against them, and letting a player put close where
     * their OS puts zoom is the one arrangement guaranteed to cost somebody a session.
     */
    public void setControlOrder(ControlOrder order, boolean mac) {
        WindowFrame.setControlOrder(order, mac);
        for (DeskWindow window : windows.values()) {
            window.frame().rebuildControls();
        }
    }

    /**
     * Turns the focused-window outline on or off for every window on the desk, open ones included.
     *
     * <p>⚠ Walks the live frames. The class is applied in {@code WindowFrame}'s constructor too, so
     * without this the setting would take effect only on windows opened afterwards — which reads as
     * a broken toggle rather than a lazy one, and is the same trap rounded corners and control order
     * both fell into.
     */
    public void setFocusRing(boolean enabled, String color) {
        WindowFrame.setFocusRing(enabled, color);
        for (DeskWindow window : windows.values()) {
            window.frame().applyFocusRing();
        }
    }

    public void setRoundedCorners(boolean rounded) {
        WindowFrame.setRounded(rounded);
        for (DeskWindow window : windows.values()) {
            window.frame().requestLayout();
        }
    }

    public void close(String id) {
        LOG.log(java.util.logging.Level.FINE, "closing window {0}", id);
        DeskWindow window = windows.remove(id);
        if (window == null) {
            return;
        }
        // ⚠ Read BEFORE the frame leaves the desk. A WindowFrame off the scene graph still reports
        // its last laid-out bounds today, which is precisely the kind of accident that works until it
        // does not; capturing here makes the ordering explicit rather than lucky.
        //
        // ⚠ THE RESTORE POINT WHEN THE WINDOW IS EXPANDED, NOT THE GEOMETRY — and this is the whole
        // difference between "the size the player chose" and "the size the desk computed". A
        // maximised or edge-tiled window's geometry belongs to the DESK: closing one while it fills
        // the screen would hand a handler the desk's own dimensions, and a handler that remembers
        // sizes would then open that window full-desk forever after, on every later session, with no
        // way for the player to work out what they had done. `saveLayout` already keeps both figures
        // apart for exactly this reason; this is the same distinction at the moment of closing.
        Geometry closing = window.isExpanded() && window.restorePoint() != null
                ? window.restorePoint()
                : window.geometry();
        desk.getChildren().remove(window.frame);
        // ⚠ The frame's own Pulse subscription, released here because nothing else will. Same defect
        // class as CycleGrid.dispose and CoreCage.dispose, which were written, correct, and called
        // by nobody — every open of the rig monitor leaked one.
        window.frame.dispose();
        publish("closed", id);
        // ⚠ AFTER the window is off the desk, and it may re-enter close() for the same id — the
        // shell's callback ends the session, which is also what an `exit` typed inside the shell
        // does, and that path closes the window itself. The window is already out of the map by
        // now, so the second call finds nothing and returns; firing before the removal would
        // recurse. The rules' own close is idempotent for the same reason.
        if (window.onClosed != null) {
            // ⚠ HANDED THE GEOMETRY, because by now the handler cannot go and look. The removal above
            // is deliberate and must stay first (see the note on it), so a handler that tried to find
            // its own window in `windows()` would find nothing — which is exactly what
            // `DeckShell.rememberSize` did, silently, for every window a player closed by hand.
            //
            // ⚠ AND IT IS GUARDED, because everything after it is the desk's own bookkeeping. An
            // unguarded throw here leaves `focused` pointing at a window already off the desk, skips
            // `notifyListeners` so the rail keeps advertising a window that is gone, and — worst —
            // aborts `closeAll`'s loop partway through a quit, so every window after the failing one
            // is never closed and whatever it was holding is never released. A handler is somebody
            // else's code; the window manager finishes closing the window either way.
            try {
                window.onClosed.accept(closing);
            } catch (RuntimeException handlerFailed) {
                LOG.log(java.util.logging.Level.WARNING, "close handler for " + id + " threw", handlerFailed);
            }
        }
        if (focused == window) {
            focused = null;
            windows.values().stream()
                    .filter(w -> !w.minimized)
                    .reduce((first, second) -> second)
                    .ifPresent(this::focus);
        }
        notifyListeners();
    }

    public void closeAll() {
        for (String id : List.copyOf(windows.keySet())) {
            close(id);
        }
        cascadeIndex = 0;
    }

    public Optional<DeskWindow> find(String id) {
        return Optional.ofNullable(windows.get(id));
    }

    public List<DeskWindow> windows() {
        return List.copyOf(windows.values());
    }

    public long openCount() {
        return windows.size();
    }

    public List<DeskWindow> minimized() {
        return windows.values().stream().filter(w -> w.minimized).toList();
    }

    public Optional<DeskWindow> focusedWindow() {
        return Optional.ofNullable(focused);
    }

    public void focus(DeskWindow window) {
        // ⚠ Published only on a CHANGE of focus, and the test is taken BEFORE anything moves. `focus`
        // is called on every open, every raise, and every click on the already-focused window, so
        // publishing unconditionally would bury the stream under the one event carrying the least
        // information. ⚠ It is a flag and NOT an early return: this method also un-minimises, brings
        // the frame to the front and notifies the rail, and skipping those for a window that happened
        // to already hold focus would make un-minimising it silently do nothing.
        boolean moved = focused != window;
        if (focused != null) {
            focused.frame.focusedFlag().set(false);
        }
        focused = window;
        if (moved && window != null) {
            publish("focused", window.id());
        }
        if (window != null) {
            window.frame.focusedFlag().set(true);
            window.frame.toFront();
            snapPreview.toFront();
        }
        notifyListeners();
    }

    /** Focus the next non-minimised window — the desk's answer to alt-tab. */
    public void focusNext() {
        List<DeskWindow> open =
                windows.values().stream().filter(w -> !w.minimized).toList();
        if (open.isEmpty()) {
            return;
        }
        int index = focused == null ? -1 : open.indexOf(focused);
        focus(open.get((index + 1) % open.size()));
    }

    // ── Placement ────────────────────────────────────────────────────────────────────────────

    private Geometry placeNew(Spec spec) {
        double deskWidth = desk.getWidth() > 0 ? desk.getWidth() : UiTokens.MIN_SUPPORTED_WIDTH;
        double deskHeight = desk.getHeight() > 0 ? desk.getHeight() : 720;
        double width = Math.min(spec.width(), deskWidth - CASCADE);
        double height = Math.min(spec.height(), deskHeight - CASCADE);

        // Cascade, then wrap before running off the bottom. Without the wrap the fifth window opens
        // mostly off-screen, which reads as a bug rather than as a full desk.
        double x = CASCADE * 0.5 + (cascadeIndex % 6) * CASCADE;
        double y = CASCADE * 0.5 + (cascadeIndex % 6) * (CASCADE * 0.6);
        cascadeIndex++;
        x = Math.min(x, Math.max(0, deskWidth - width));
        y = Math.min(y, Math.max(0, deskHeight - height));
        return snapIfNeeded(new Geometry(x, y, width, height));
    }

    private Geometry snapIfNeeded(Geometry geometry) {
        return placement == Placement.SNAP
                ? snap(geometry.x(), geometry.y(), geometry.width(), geometry.height())
                : geometry;
    }

    /** Rounds to the {@link UiTokens#SNAP_GRID} lattice — the character-cell language of §11. */
    private static Geometry snap(double x, double y, double width, double height) {
        double g = UiTokens.SNAP_GRID;
        return new Geometry(
                Math.round(x / g) * g,
                Math.round(y / g) * g,
                Math.max(MIN_WIDTH, Math.round(width / g) * g),
                Math.max(MIN_HEIGHT, Math.round(height / g) * g));
    }

    /**
     * The tiling zone a pointer is in, if any.
     *
     * <p>Corners are tested before edges, because a pointer in the top-left corner is inside both
     * the top zone and the left zone and the player who dragged there meant the quarter.
     */
    private Optional<Geometry> tileZone(double pointerX, double pointerY) {
        if (placement != Placement.SNAP) {
            return Optional.empty();
        }
        double w = desk.getWidth();
        double h = desk.getHeight();
        if (w <= 0 || h <= 0) {
            return Optional.empty();
        }
        boolean left = pointerX <= TILE_ZONE;
        boolean right = pointerX >= w - TILE_ZONE;
        boolean top = pointerY <= TILE_ZONE;
        boolean bottom = pointerY >= h - TILE_ZONE;

        if (top && left) {
            return Optional.of(new Geometry(0, 0, w / 2, h / 2));
        }
        if (top && right) {
            return Optional.of(new Geometry(w / 2, 0, w / 2, h / 2));
        }
        if (bottom && left) {
            return Optional.of(new Geometry(0, h / 2, w / 2, h / 2));
        }
        if (bottom && right) {
            return Optional.of(new Geometry(w / 2, h / 2, w / 2, h / 2));
        }
        if (top) {
            return Optional.of(new Geometry(0, 0, w, h));
        }
        if (left) {
            return Optional.of(new Geometry(0, 0, w / 2, h));
        }
        if (right) {
            return Optional.of(new Geometry(w / 2, 0, w / 2, h));
        }
        if (bottom) {
            return Optional.of(new Geometry(0, h / 2, w, h / 2));
        }
        return Optional.empty();
    }

    // ── Interaction ──────────────────────────────────────────────────────────────────────────

    private void install(DeskWindow window) {
        WindowFrame frame = window.frame;
        Drag drag = new Drag();

        frame.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> focus(window));

        // Move: the header strip is the handle (§3). Dragging by the body would make every click
        // inside a tool a potential accidental move.
        frame.headerStrip().setOnMousePressed(e -> {
            drag.startX = e.getSceneX();
            drag.startY = e.getSceneY();

            // Dragging a tiled or maximised window shrinks it back to its restored SIZE, keeping the
            // grab point under the cursor — the behaviour every desktop has, and the reason it is
            // worth the arithmetic: pulling a full-width panel off the top edge should hand you the
            // panel you had, not a full-width one you now have to resize by hand.
            //
            // The cursor is kept at the same FRACTION along the title bar rather than the same pixel
            // offset. Grab a maximised window near its right edge and a pixel offset would place the
            // restored window far to the left of the pointer, which reads as the window escaping.
            if (window.expanded && window.restoreGeometry != null) {
                javafx.geometry.Point2D local = desk.sceneToLocal(e.getSceneX(), e.getSceneY());
                double fraction = window.width <= 0 ? 0 : (local.getX() - window.x) / window.width;
                double restoredWidth = window.restoreGeometry.width();
                double restoredHeight = window.restoreGeometry.height();
                double newX = local.getX() - restoredWidth * fraction;
                double newY = window.y;
                window.placedByHand();
                window.setGeometry(new Geometry(newX, newY, restoredWidth, restoredHeight));
            }

            drag.originX = window.x;
            drag.originY = window.y;
            drag.moving = true;
            e.consume();
        });
        frame.headerStrip().setOnMouseDragged(e -> {
            if (!drag.moving || window.maximized) {
                return;
            }
            double x = drag.originX + (e.getSceneX() - drag.startX);
            double y = drag.originY + (e.getSceneY() - drag.startY);
            // Never let the strip leave the desk: a window dragged fully past the top edge has no
            // handle left to drag it back by, which is the classic unrecoverable WM state.
            y = Math.max(0, Math.min(y, desk.getHeight() - UiTokens.STRIP_HEIGHT));
            x = Math.max(-window.width + CASCADE, Math.min(x, desk.getWidth() - CASCADE));
            window.setGeometry(new Geometry(x, y, window.width, window.height));

            javafx.geometry.Point2D local = desk.sceneToLocal(e.getSceneX(), e.getSceneY());
            Optional<Geometry> zone = tileZone(local.getX(), local.getY());
            zone.ifPresentOrElse(this::showPreview, this::hidePreview);
            e.consume();
        });
        frame.headerStrip().setOnMouseReleased(e -> {
            if (!drag.moving) {
                return;
            }
            drag.moving = false;
            hidePreview();
            javafx.geometry.Point2D local = desk.sceneToLocal(e.getSceneX(), e.getSceneY());
            Optional<Geometry> zone = tileZone(local.getX(), local.getY());
            if (zone.isPresent()) {
                Geometry target = zone.get();
                // Where the drag STARTED, not where it ended: the window is currently under the
                // pointer at the edge, and restoring it there would drop it back on the edge it was
                // just tiled from.
                window.restoreFrom(new Geometry(drag.originX, drag.originY, window.width, window.height));
                boolean fillsDesk = target.width() >= desk.getWidth() && target.height() >= desk.getHeight();
                window.expandTo(target, fillsDesk);
            } else {
                window.placedByHand();
                window.setGeometry(snapIfNeeded(new Geometry(window.x, window.y, window.width, window.height)));
            }
            // ⚠ On RELEASE, not on drag. The dragged window's own frost needs no re-capture — its
            // image is the whole desk and `layoutChildren` re-anchors it, so it stays pixel-correct
            // for free. What does change is what the windows it moved off now have beneath them.
            scheduleFrost();
            e.consume();
        });
        frame.headerStrip().setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                // Restores a window that was dragged to an edge just as readily as one that was
                // maximised with the [□] control — see toggleExpanded.
                window.toggleExpanded();
                e.consume();
            }
        });

        // Resize: an edge grip, detected by proximity. Filters rather than handlers so the grip
        // works over whatever the tool put in its corner.
        //
        // ⚠ The coordinates MUST be converted from scene space. In an event filter, `e.getX()` and
        // `e.getY()` are relative to the event's TARGET node — not to the node the filter is
        // installed on. The target here is almost always some Label or ScrollPane deep inside the
        // tool, so the raw values are that child's local coordinates and the edge test reads them as
        // if they were the frame's. The visible symptom is a resize grip that works on a bare panel
        // and silently stops working wherever the tool put content, which is everywhere.
        frame.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (window.maximized) {
                frame.setCursor(null);
                return;
            }
            javafx.geometry.Point2D at = frame.sceneToLocal(e.getSceneX(), e.getSceneY());
            frame.setCursor(cursorFor(edgeAt(at.getX(), at.getY(), frame.getWidth(), frame.getHeight())));
        });
        // Without this the cursor keeps whatever arrow it had when the pointer left a panel edge.
        frame.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
            if (drag.edge == 0) {
                frame.setCursor(null);
            }
        });
        frame.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (window.maximized) {
                return;
            }
            javafx.geometry.Point2D at = frame.sceneToLocal(e.getSceneX(), e.getSceneY());
            int edge = edgeAt(at.getX(), at.getY(), frame.getWidth(), frame.getHeight());
            if (edge == 0) {
                return;
            }
            drag.edge = edge;
            drag.startX = e.getSceneX();
            drag.startY = e.getSceneY();
            drag.originX = window.x;
            drag.originY = window.y;
            drag.originW = window.width;
            drag.originH = window.height;
            e.consume();
        });
        frame.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (drag.edge == 0) {
                return;
            }
            double dx = e.getSceneX() - drag.startX;
            double dy = e.getSceneY() - drag.startY;
            double x = drag.originX;
            double y = drag.originY;
            double w = drag.originW;
            double h = drag.originH;

            if ((drag.edge & EAST) != 0) {
                w = Math.max(MIN_WIDTH, drag.originW + dx);
            }
            if ((drag.edge & SOUTH) != 0) {
                h = Math.max(MIN_HEIGHT, drag.originH + dy);
            }
            if ((drag.edge & WEST) != 0) {
                // Clamped BEFORE the origin is derived from it. Deriving x from an unclamped width
                // lets the left edge keep travelling right after the window has stopped shrinking,
                // which drags the whole panel across the desk.
                w = Math.max(MIN_WIDTH, drag.originW - dx);
                x = drag.originX + (drag.originW - w);
            }
            if ((drag.edge & NORTH) != 0) {
                h = Math.max(MIN_HEIGHT, drag.originH - dy);
                y = drag.originY + (drag.originH - h);
            }
            window.setGeometry(new Geometry(x, y, w, h));
            e.consume();
        });
        frame.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (drag.edge == 0) {
                return;
            }
            drag.edge = 0;
            window.placedByHand();
            window.setGeometry(snapIfNeeded(new Geometry(window.x, window.y, window.width, window.height)));
            frame.setCursor(null);
        });
    }

    private static final int NORTH = 1;
    private static final int SOUTH = 2;
    private static final int WEST = 4;
    private static final int EAST = 8;

    /**
     * Which edges a point is gripping, in frame-local coordinates.
     *
     * <p>Pure and package-visible so {@code DeskManagerTest} can check it without a toolkit — the
     * grip is the one piece of the window manager a player notices instantly when it is wrong, and
     * it was wrong once already (see the coordinate note at the call site).
     *
     * <p>A point outside the frame grips nothing. Without that guard a negative local coordinate —
     * which is what {@code sceneToLocal} returns for a pointer above or left of the panel — reads as
     * {@code <= RESIZE_MARGIN} and arms a resize from outside the window.
     */
    static int edgeAt(double x, double y, double width, double height) {
        if (x < 0 || y < 0 || x > width || y > height) {
            return 0;
        }

        // Corners get a bigger grip than edges. A 5px corner is the standard frustration of every
        // hand-rolled window manager: the diagonal resize is the one people reach for most and the
        // hardest to hit, because it is the only one that requires being close to two edges at once.
        boolean roomToDistinguish = width > 3 * CORNER_GRIP && height > 3 * CORNER_GRIP;
        if (roomToDistinguish) {
            int corner = 0;
            if (y <= CORNER_GRIP) {
                corner |= NORTH;
            } else if (y >= height - CORNER_GRIP) {
                corner |= SOUTH;
            }
            if (x <= CORNER_GRIP) {
                corner |= WEST;
            } else if (x >= width - CORNER_GRIP) {
                corner |= EAST;
            }
            // Both axes, i.e. genuinely a corner rather than a point that happens to be near one.
            if ((corner & (NORTH | SOUTH)) != 0 && (corner & (WEST | EAST)) != 0) {
                return corner;
            }
        }

        int edge = 0;
        if (y <= RESIZE_MARGIN) {
            edge |= NORTH;
        }
        if (y >= height - RESIZE_MARGIN) {
            edge |= SOUTH;
        }
        if (x <= RESIZE_MARGIN) {
            edge |= WEST;
        }
        if (x >= width - RESIZE_MARGIN) {
            edge |= EAST;
        }
        return edge;
    }

    /**
     * The grip cursor, from the player's chosen pointer skin.
     *
     * <p>Routed through {@code ui/cursors/Cursors} rather than naming {@link Cursor} constants
     * directly, because this is the one place the player touches an edge <em>this application
     * drew</em> — a system resize arrow on a window the client rendered itself is the most
     * conspicuous seam in the whole deck. Under the system skin it resolves back to exactly these
     * constants.
     */
    private static Cursor cursorFor(int edge) {
        if (edge == 0) {
            // ⚠ null, NOT Cursor.DEFAULT. A node cursor of null means "inherit from my parent, and
            // ultimately from the Scene"; Cursor.DEFAULT is an explicit request for the platform
            // arrow, which beats the Scene cursor. Setting DEFAULT here pinned the system pointer
            // over the entire surface of every window — so a custom skin only showed up on the
            // resize grips and on the handful of nodes that set their own cursor, which is exactly
            // the "it doesn't take over on general hover" symptom.
            return null;
        }
        return io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared()
                .resize((edge & NORTH) != 0, (edge & SOUTH) != 0, (edge & WEST) != 0, (edge & EAST) != 0);
    }

    private void showPreview(Geometry geometry) {
        snapPreview.setVisible(true);
        snapPreview.resizeRelocate(geometry.x(), geometry.y(), geometry.width(), geometry.height());
        snapPreview.toFront();
    }

    private void hidePreview() {
        snapPreview.setVisible(false);
    }

    private static final class Drag {
        private double startX;
        private double startY;
        private double originX;
        private double originY;
        private double originW;
        private double originH;
        private boolean moving;
        private int edge;
    }

    /** Window geometry on the desk, in desk coordinates. */
    public record Geometry(double x, double y, double width, double height) {}

    /** What to open. Mirrors the fields {@code WindowSpec} already carries. */
    /**
     * What to open.
     *
     * @param onClosed called when the window is closed, with <b>the size the player chose</b> — the
     *     window's geometry, or its restore point when it was closed maximised or edge-tiled, because
     *     an expanded window's geometry is the desk's rather than the player's. ⚠ It is
     *     handed that rather than left to look it up: {@link DeskManager#close} removes the window
     *     from the desk before firing this, deliberately, so a handler asking {@code windows()} for
     *     its own window finds nothing. That is not hypothetical — {@code DeckShell.rememberSize} did
     *     exactly that and silently recorded nothing for every window closed by hand. Also for a
     *     window whose existence <em>is</em> game
     *     state. ⚠ Most windows have none and pass {@code null}: a tool window is a view onto state
     *     that exists whether or not it is on screen, so closing one must change nothing. A shell is
     *     the exception — it is an instance created by an act in the game, holding
     *     {@code Balance.SESSION_CYCLES} for as long as it lives, and a window manager that closed it
     *     silently would leak those cycles with nothing left on screen to release them.
     */
    public record Spec(
            String id,
            String title,
            String identifier,
            Node content,
            double width,
            double height,
            boolean closable,
            java.util.function.Consumer<Geometry> onClosed) {

        /** The ordinary window: a view onto state, with nothing to release when it goes. */
        public Spec(
                String id,
                String title,
                String identifier,
                Node content,
                double width,
                double height,
                boolean closable) {
            this(id, title, identifier, content, width, height, closable, null);
        }
    }

    /** One window on the desk. */
    public final class DeskWindow {

        private final String id;
        private final WindowFrame frame;
        private final boolean closable;
        private final java.util.function.Consumer<Geometry> onClosed;
        private double x;
        private double y;
        private double width;
        private double height;
        private boolean minimized;
        private boolean maximized;
        private boolean expanded;
        private Geometry restoreGeometry;

        private DeskWindow(String id, WindowFrame frame, boolean closable,
                java.util.function.Consumer<Geometry> onClosed) {
            this.id = id;
            this.frame = frame;
            this.closable = closable;
            this.onClosed = onClosed;
        }

        public String id() {
            return id;
        }

        public WindowFrame frame() {
            return frame;
        }

        public boolean closable() {
            return closable;
        }

        public Geometry geometry() {
            return new Geometry(x, y, width, height);
        }

        public void setGeometry(Geometry geometry) {
            this.x = geometry.x();
            this.y = geometry.y();
            this.width = Math.max(MIN_WIDTH, geometry.width());
            this.height = Math.max(MIN_HEIGHT, geometry.height());
            frame.resizeRelocate(x, y, width, height);
        }

        public boolean isMinimized() {
            return minimized;
        }

        public void setMinimized(boolean minimized) {
            this.minimized = minimized;
            // Visibility only. Managed stays false forever — see the constructor's note.
            frame.setVisible(!minimized);
            if (minimized && focused == this) {
                focused = null;
                frame.focusedFlag().set(false);
            }
            notifyListeners();
        }

        public boolean isMaximized() {
            return maximized;
        }

        /** True while the window sits in a computed position rather than one the player chose. */
        public boolean isExpanded() {
            return expanded;
        }

        /**
         * Puts the window back where the player last had it, or fills the desk if it is already
         * where the player put it.
         *
         * <p>⚠ The important half is the FIRST branch, and it is what was missing. A window dragged
         * against the top edge is <em>tiled</em> to the full desk — it looks maximised, but
         * {@code maximized} stayed false because tiling and maximising were different code paths.
         * Double-clicking the strip then "maximised" an already-full window, which did nothing
         * visible, and only a second double-click restored it. One flag, {@link #expanded}, now
         * covers both: if the window is anywhere it did not choose to be, double-click sends it back.
         *
         * <p>The distinction {@code maximized} still carries is narrower and only matters on resize:
         * a window the player maximised should keep filling the desk when the desk changes size,
         * whereas one tiled to a half should not silently grow into the space.
         */
        public void toggleExpanded() {
            if (expanded) {
                expanded = false;
                maximized = false;
                if (restoreGeometry != null) {
                    setGeometry(restoreGeometry);
                }
            } else {
                restoreGeometry = geometry();
                expanded = true;
                maximized = true;
                setGeometry(new Geometry(0, 0, desk.getWidth(), desk.getHeight()));
            }
            notifyListeners();
        }

        /** Kept for callers that mean "the [□] control". Same behaviour. */
        public void toggleMaximized() {
            toggleExpanded();
        }

        /**
         * Records where to come back to before the desk moves this window somewhere computed.
         *
         * <p>Only remembers the FIRST such move: tiling left, then right, then to a corner should
         * still restore to where the player had it, not to the previous tile.
         */
        void expandTo(Geometry target, boolean fillsDesk) {
            if (!expanded) {
                restoreGeometry = geometry();
            }
            expanded = true;
            maximized = fillsDesk;
            setGeometry(target);
            notifyListeners();
        }

        /** The player has placed this window by hand, so there is nothing to restore it to. */
        void placedByHand() {
            expanded = false;
            maximized = false;
        }

        /** Where this window would go on a double-click, or null if it is where the player put it. */
        public Geometry restorePoint() {
            return restoreGeometry;
        }

        /**
         * Puts the window back into a saved state wholesale.
         *
         * <p>Used only when replaying a persisted layout. It sets the flags directly rather than
         * going through {@link #expandTo}, because that would overwrite the restore point with the
         * expanded bounds — the saved one is the answer the player actually wants back.
         */
        public void restoreState(Geometry geometry, boolean minimized, boolean expanded, Geometry restorePoint) {
            setGeometry(geometry);
            this.expanded = expanded;
            this.maximized =
                    expanded && geometry.width() >= desk.getWidth() - 1 && geometry.height() >= desk.getHeight() - 1;
            this.restoreGeometry = restorePoint;
            setMinimized(minimized);
        }

        /** Overrides the remembered geometry, for a drag that knows better than the current bounds. */
        void restoreFrom(Geometry geometry) {
            if (!expanded) {
                restoreGeometry = geometry;
            }
        }

        /** Re-applies the maximised or tiled size after the desk itself changes size. */
        void reflow(double deskWidth, double deskHeight) {
            if (maximized) {
                setGeometry(new Geometry(0, 0, deskWidth, deskHeight));
            } else {
                // Keep the window reachable when the desk shrinks — otherwise a player who resizes
                // the game window down loses every panel that was to the right of the new edge.
                double clampedX = Math.min(x, Math.max(0, deskWidth - CASCADE));
                double clampedY = Math.min(y, Math.max(0, deskHeight - UiTokens.STRIP_HEIGHT));
                if (clampedX != x || clampedY != y) {
                    setGeometry(new Geometry(clampedX, clampedY, width, height));
                }
            }
        }
    }

    /**
     * Arranges every open window into the two-column split §3 specifies.
     *
     * <p>This is what the desk looks like before the player touches it. {@code
     * docs/design/ui-design-language.md} §3 is unambiguous that the layout is <b>"tiling, not
     * floating — panels abut and share edges, filling the screen. Nothing sits on neutral background
     * with margin around it."</b> A cascade of overlapping windows is the opposite of that, and it
     * also starves the panel that needs width most: the cycle grid drops from 25 cells a row to 10
     * in a cascaded window, which is the signature component degrading before the player has done
     * anything.
     *
     * <p>Free movement survives — this is the starting arrangement, not a constraint. Windows opened
     * afterwards cascade, because re-tiling the whole desk every time a tool opens would move panels
     * the player had deliberately placed.
     *
     * <p>The 1.32:1 ratio is §3's. Below {@link UiTokens#NARROW_WIDTH} it collapses to one column,
     * same as the section's responsive rule.
     */
    public void tileAll() {
        List<DeskWindow> open =
                windows.values().stream().filter(w -> !w.minimized).toList();
        if (open.isEmpty()) {
            return;
        }
        double w = desk.getWidth();
        double h = desk.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        if (open.size() == 1) {
            open.getFirst().setGeometry(new Geometry(0, 0, w, h));
            return;
        }
        if (w < UiTokens.NARROW_WIDTH) {
            double each = h / open.size();
            for (int i = 0; i < open.size(); i++) {
                open.get(i).setGeometry(new Geometry(0, i * each, w, each));
            }
            return;
        }

        double leftWidth = Math.floor(w * 1.32 / 2.32);
        // The first window gets the wide column to itself when there are three or fewer; with four
        // or more the columns split evenly by count. The rig monitor is always first, and it is the
        // one panel that genuinely needs the width — 25 cells a row is the whole point of it.
        int leftCount = open.size() <= 3 ? 1 : open.size() / 2;
        int rightCount = open.size() - leftCount;

        double leftEach = h / leftCount;
        for (int i = 0; i < leftCount; i++) {
            open.get(i).setGeometry(new Geometry(0, i * leftEach, leftWidth, leftEach));
        }
        double rightEach = h / rightCount;
        for (int i = 0; i < rightCount; i++) {
            open.get(leftCount + i).setGeometry(new Geometry(leftWidth, i * rightEach, w - leftWidth, rightEach));
        }
    }

    /** Called by the shell when the desk resizes. */
    public void reflow() {
        // The backdrop first: it is unmanaged, so nothing else will ever resize it, and a wallpaper
        // still at its old size is visible as a hard edge across the desk.
        layoutBackdrop();
        for (DeskWindow window : windows.values()) {
            window.reflow(desk.getWidth(), desk.getHeight());
        }
    }
}
