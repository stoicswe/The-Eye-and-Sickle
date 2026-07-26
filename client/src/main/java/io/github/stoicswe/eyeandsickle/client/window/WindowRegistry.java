package io.github.stoicswe.eyeandsickle.client.window;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Which tool windows exist, which are open, and where they are.
 *
 * <p>{@code docs/client/05-tool-windows-and-layout.md} §1.5 calls this "the implementation shape
 * everything else assumes", and three of its sections are really specifications for this class.
 *
 * <h2>The accelerator-installation trap (§3.6)</h2>
 *
 * A JavaFX accelerator registered on one {@code Scene} fires only while that Scene has focus. For a
 * shortcut whose entire job is to raise a window you <em>cannot see</em>, that is exactly backwards —
 * {@code Shortcut+4} would work only when the audit window was already in front of you. So every
 * accelerator is installed on <em>every</em> Stage as it is created, and on every Stage created later.
 * This is the single most likely thing to be broken by a well-meaning refactor, which is why it is one
 * method with a loud name.
 *
 * <h2>A saved position may no longer be a place (§3.8)</h2>
 *
 * Monitors get unplugged. A window restored to a coordinate on a screen that no longer exists is
 * invisible and, worse, focusable — so the player can hear it respond and never find it. Every
 * restore is checked against the current screen layout and falls back to the primary screen's centre
 * if the remembered position is not on any of them.
 */
public final class WindowRegistry {

    private final ClientProfile profile;
    private final Map<WindowSpec, Stage> open = new EnumMap<>(WindowSpec.class);
    private final Map<WindowSpec, Function<WindowSpec, Parent>> factories = new EnumMap<>(WindowSpec.class);
    private final ObservableList<WindowSpec> openIds = FXCollections.observableArrayList();
    private final SimpleBooleanProperty docked = new SimpleBooleanProperty(false);

    /** Applied to every Scene this registry creates, so a theme change reaches every window at once. */
    private Runnable themeApplier = () -> {};

    public WindowRegistry(ClientProfile profile) {
        this.profile = profile;
        this.docked.set(profile.settings().dockedLayout);
    }

    /** Registers how to build a window's content. Called once per window at startup. */
    public void register(WindowSpec spec, Function<WindowSpec, Parent> factory) {
        factories.put(spec, factory);
    }

    public void onThemeChange(Runnable applier) {
        this.themeApplier = applier == null ? () -> {} : applier;
    }

    /** The windows currently open, in the order they were opened. Drives the switcher. */
    public ObservableList<WindowSpec> openWindows() {
        return FXCollections.unmodifiableObservableList(openIds);
    }

    public ReadOnlyBooleanProperty dockedProperty() {
        return docked;
    }

    public boolean isDocked() {
        return docked.get();
    }

    public Optional<Stage> stageFor(WindowSpec spec) {
        return Optional.ofNullable(open.get(spec));
    }

    public boolean isOpen(WindowSpec spec) {
        return open.containsKey(spec);
    }

    /**
     * Opens the window if it is closed, then raises and focuses it.
     *
     * <p>Idempotent on purpose: {@code docs/client/05} §3.3 wants a cross-window link ("show me this
     * item in storage") and an accelerator to behave identically whether the target is closed, open
     * behind something, or already in front.
     */
    public Stage open(WindowSpec spec) {
        Stage existing = open.get(spec);
        if (existing != null) {
            raise(existing);
            return existing;
        }

        Function<WindowSpec, Parent> factory = factories.get(spec);
        if (factory == null) {
            throw new IllegalStateException("No content registered for window " + spec.id());
        }

        Stage stage = new Stage();
        stage.setTitle(spec.windowTitle());
        stage.setMinWidth(spec.minWidth());
        stage.setMinHeight(spec.minHeight());

        Scene scene = new Scene(factory.apply(spec), spec.defaultWidth(), spec.defaultHeight());
        stage.setScene(scene);

        restoreGeometry(spec, stage);
        installAllAccelerators(scene);

        stage.setOnHidden(e -> {
            rememberGeometry(spec, stage);
            open.remove(spec);
            openIds.remove(spec);
        });

        // A non-closable window must not be closable by the OS chrome either. The rig monitor
        // collapses to a strip instead; letting the title-bar X hide the mandatory compute readout
        // would break client pillar C2 through a route the UI never offers.
        if (!spec.closable()) {
            stage.setOnCloseRequest(javafx.event.Event::consume);
        }

        open.put(spec, stage);
        openIds.add(spec);
        stage.show();
        themeApplier.run();
        return stage;
    }

    public void close(WindowSpec spec) {
        Stage stage = open.get(spec);
        if (stage != null && spec.closable()) {
            stage.close();
        }
    }

    public void toggle(WindowSpec spec) {
        if (isOpen(spec)) {
            close(spec);
        } else {
            open(spec);
        }
    }

    private static void raise(Stage stage) {
        if (stage.isIconified()) {
            stage.setIconified(false);
        }
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * Installs every window accelerator on the given scene, opening a separate Stage per window.
     *
     * <p>Read the class comment before changing this. Accelerators are per-Scene in JavaFX, and a
     * per-window registration produces shortcuts that only work when they are least needed.
     */
    public void installAllAccelerators(Scene scene) {
        installAllAccelerators(scene, this::open);
    }

    /**
     * Installs every window accelerator, letting the caller decide what "activate this tool" means.
     *
     * <p><b>The docked layout needs this and it is not a nicety.</b> {@code Shortcut+4} must focus the
     * audit <em>tab</em> in single-window mode, not open a second OS window — a shortcut that silently
     * breaks the single-window model is worse than no shortcut, because the player chose that mode
     * specifically to avoid managing windows. Binding the handler here rather than hard-coding
     * {@link #open} is what keeps one accelerator table serving both layouts.
     */
    public void installAllAccelerators(Scene scene, java.util.function.Consumer<WindowSpec> activate) {
        for (WindowSpec spec : WindowSpec.values()) {
            scene.getAccelerators().put(spec.combination(), () -> activate.accept(spec));
        }
    }

    // ------------------------------------------------------------------ geometry

    private void rememberGeometry(WindowSpec spec, Stage stage) {
        profile.settings()
                .windows
                .put(
                        spec.id(),
                        new ClientProfile.WindowGeometry(
                                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), stage.isMaximized()));
    }

    private void restoreGeometry(WindowSpec spec, Stage stage) {
        ClientProfile.WindowGeometry geometry = profile.settings().windows.get(spec.id());
        if (geometry == null || geometry.width <= 0 || geometry.height <= 0) {
            stage.centerOnScreen();
            return;
        }
        if (!isOnAScreen(geometry)) {
            // The monitor it was on is gone. Restoring the size is still useful and still correct;
            // restoring the position would put the window somewhere the player cannot reach.
            stage.setWidth(Math.max(spec.minWidth(), geometry.width));
            stage.setHeight(Math.max(spec.minHeight(), geometry.height));
            stage.centerOnScreen();
            return;
        }
        stage.setX(geometry.x);
        stage.setY(geometry.y);
        stage.setWidth(Math.max(spec.minWidth(), geometry.width));
        stage.setHeight(Math.max(spec.minHeight(), geometry.height));
        stage.setMaximized(geometry.maximized);
    }

    /**
     * Whether enough of the window would land on a real screen to be grabbable.
     *
     * <p>"Enough" is a visible strip of title bar, not full containment: a window deliberately hung
     * half off the edge of a monitor is a normal thing a player does, and snapping it back would be
     * the annoying kind of helpful.
     */
    static boolean isOnAScreen(ClientProfile.WindowGeometry geometry, List<Rectangle2D> screens) {
        double grabbableWidth = Math.min(120, geometry.width);
        double grabbableHeight = 32;
        for (Rectangle2D screen : screens) {
            boolean overlapsX = geometry.x + grabbableWidth > screen.getMinX() && geometry.x < screen.getMaxX();
            boolean overlapsY = geometry.y + grabbableHeight > screen.getMinY() && geometry.y < screen.getMaxY();
            if (overlapsX && overlapsY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOnAScreen(ClientProfile.WindowGeometry geometry) {
        return isOnAScreen(geometry, Screen.getScreens().stream().map(Screen::getVisualBounds).toList());
    }

    /** Saves the position of everything currently open. Called on autosave and on exit. */
    public void rememberAll() {
        open.forEach(this::rememberGeometry);
        profile.settings().openWindows.clear();
        for (WindowSpec spec : openIds) {
            profile.settings().openWindows.put(spec.id(), true);
        }
        profile.settings().dockedLayout = docked.get();
    }

    /**
     * Closes every tool window.
     *
     * <p>Needed by "back to menu": leaving a dozen Stages open behind a menu the player thinks is
     * the whole application is the sort of thing that reads as the game having crashed.
     */
    public void closeAll() {
        for (Stage stage : List.copyOf(open.values())) {
            stage.setOnCloseRequest(null);
            stage.close();
        }
        open.clear();
        openIds.clear();
    }

    public void setDocked(boolean value) {
        docked.set(value);
        profile.settings().dockedLayout = value;
    }
}
