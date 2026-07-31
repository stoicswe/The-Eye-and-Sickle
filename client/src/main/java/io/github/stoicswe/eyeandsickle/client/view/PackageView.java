package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;

/**
 * The package installer — what is in this thing, who made it, and is it what it says it is.
 *
 * <h2>Why installing gets a panel instead of a menu item that just does it</h2>
 *
 * Installing consumes the package, and a {@code .upg} is an <b>asset</b>: it can be sold instead, and
 * for an upgrade already owned it is worth strictly more sold than installed. That is a decision, and
 * a decision made from a right-click menu is a decision made without the facts. The panel puts the
 * facts where the action is — what it installs, what it will reserve when equipped, what gate it sits
 * behind, and whether anything is currently holding it.
 *
 * <h2>⚠ The two digests are shown, not reduced to a tick</h2>
 *
 * {@link PackageManifest#expectedSha} is what the manifest declares and {@link
 * PackageManifest#actualSha} is what the payload hashes to. <b>Both</b> are printed, in full-width
 * monospace, above a verdict — because the point of a checksum is that a person compares two figures,
 * and a panel that only ever showed "verified ✓" would teach a player to trust a tick mark rather
 * than to read a digest. They always agree today; the mismatch state is built, styled and tested
 * because the player-to-player market in online play is where a payload can stop matching its
 * manifest, and a verification step that arrived at the same moment as the threat would be a new
 * mechanic nobody had a habit for.
 *
 * <h2>Two modes, one panel</h2>
 *
 * {@link Mode#INSTALL} carries the action. {@link Mode#INSPECT} is the same facts with no action at
 * all — the safe way to look at something you have not decided about. Sharing the panel is deliberate:
 * a separate read-only viewer would be a second place for the same six fields to be formatted, and
 * the day they disagreed the one showing the digest would be the one nobody trusted.
 */
public final class PackageView {

    private PackageView() {}

    /** Whether this panel can act, or only report. */
    public enum Mode {
        /** Carries Install and Sell. */
        INSTALL,

        /** Read-only. Nothing here changes anything. */
        INSPECT
    }

    /**
     * Builds the panel.
     *
     * @param onAction run after an install or a sale, so the caller can refresh and dismiss
     * @param report where a refusal is written — the rules' own words, never this panel's guess
     */
    public static Region create(
            GameSession session,
            PackageManifest pkg,
            Mode mode,
            Runnable onAction,
            java.util.function.Consumer<String> report) {
        VBox body = new VBox(UiTokens.SPACE_3);
        body.getStyleClass().add("es-package-body");

        Label title = new Label(mode == Mode.INSPECT ? "INSPECT PACKAGE" : "INSTALL PACKAGE");
        title.getStyleClass().add("es-panel-title");

        Label file = new Label(pkg.name());
        file.getStyleClass().addAll("es-package-name", "es-mono");

        VBox facts = new VBox(UiTokens.SPACE_1);
        facts.getChildren()
                .addAll(
                        field("installs", pkg.displayName()),
                        field("publisher", pkg.publisher()),
                        field("origin", pkg.fromMarket() ? "vendor market" : pkg.origin()),
                        field("gate", pkg.gate().name().toLowerCase(Locale.ROOT).replace('_', '-')),
                        field("size", String.format(Locale.ROOT, "%.1f MB", pkg.sizeBytes() / 1_000_000.0d)),
                        field(
                                "reserves",
                                pkg.equippedCycles() == 0
                                        ? "nothing while equipped"
                                        : pkg.equippedCycles() + (pkg.equippedCycles() == 1 ? " cycle" : " cycles")
                                                + " while equipped"));

        Label contents = new Label(pkg.summary());
        contents.setWrapText(true);
        contents.getStyleClass().add("es-package-summary");

        body.getChildren()
                .addAll(
                        title,
                        file,
                        facts,
                        heading("CONTENTS"),
                        contents,
                        heading("INTEGRITY"),
                        integrity(pkg),
                        heading("STATUS"),
                        status(pkg));

        if (mode == Mode.INSTALL) {
            // ⚠ Firmware takes over the panel while it writes. A StackPane rather than a swap so the
            // panel underneath is never torn down — the flash ends and the facts are simply there
            // again. The overlay is OPAQUE, so it names what it is writing itself.
            return flashable(session, pkg, frame(body, actions(session, pkg, onAction, report)));
        } else {
            // Said out loud, because a panel with facts and no buttons otherwise reads as one whose
            // buttons failed to appear.
            Label note = new Label("Read-only. Nothing on this panel installs, sells or changes "
                    + "anything — close it and choose Install when you have decided.");
            note.setWrapText(true);
            note.getStyleClass().add("es-package-note");
            return frame(body, note);
        }
    }

    /**
     * Puts the panel's content in a bounded, scrolling frame.
     *
     * <h2>⚠ The width is CAPPED, and that is what makes wrapping happen at all</h2>
     *
     * The panel used to pin {@code setMinWidth(640)} and let its content decide the rest — so the two
     * SHA digests, at 71 unbreakable monospace characters each, set the width of the whole window and
     * every other line stretched to match. A cap plus {@code setFitToWidth} inverts that: the frame
     * decides the width and the content wraps into it.
     *
     * <p>⚠ {@code setFitToWidth(true)} is the load-bearing half. Without it a {@code ScrollPane}
     * hands its content the content's <em>preferred</em> width, which for an unwrapped digest is the
     * full 71 characters — so the wrapping never happens and a horizontal scrollbar appears instead.
     * Same trap as {@code Views.scrollable}'s {@code fillHeight}, from the other side.
     *
     * <p>The height is capped too, so a package with a long summary and a long refusal cannot grow a
     * popup taller than the window it is shown over.
     */
    private static Region frame(Region body, Region pinned) {
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("es-package-scroll");

        // ⚠ The actions are PINNED below the scroll, never inside it. A package with a long summary
        // and a long refusal pushes them past the fold, and a panel whose Install button has to be
        // scrolled to reads as a panel with no Install button. The read-only note is pinned for the
        // same reason: it is the answer to "where are the buttons".
        VBox root = new VBox(UiTokens.SPACE_3, scroll, pinned);
        root.getStyleClass().addAll("es-package", "es-body-pad");
        root.setPrefWidth(PANEL_WIDTH);
        root.setMaxWidth(PANEL_WIDTH);
        root.setMaxHeight(PANEL_HEIGHT);
        // ⚠ Vgrow AND an explicit max. A layout constraint grows a child only up to its maximum, and
        // a Control's computed maximum is not the unbounded value a Pane reports — so a ScrollPane
        // with Vgrow.ALWAYS silently stops at its preferred height. Settings hit exactly this.
        scroll.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return root;
    }

    /** How wide the panel is allowed to be. Everything inside wraps into it. */
    private static final double PANEL_WIDTH = 560;

    /** And how tall, before the content scrolls rather than growing the popup. */
    private static final double PANEL_HEIGHT = 560;

    /**
     * The two digests, then the verdict.
     *
     * <p>⚠ Printed in full and on their own lines. Shortening them to eight characters would make the
     * comparison the panel is asking a player to make impossible to actually make, which is the exact
     * failure of every interface that renders a hash as "9f3c…4a2b ✓".
     */
    private static Region integrity(PackageManifest pkg) {
        VBox box = new VBox(UiTokens.SPACE_1);
        box.getChildren().addAll(digest("declared", pkg.expectedSha()), digest("payload", pkg.actualSha()));

        Label verdict = new Label(
                pkg.shaMatches()
                        ? "MATCH — the payload is what the manifest says it is."
                        : "MISMATCH — the payload is NOT what this manifest declares. Something replaced it "
                                + "after it was signed. Installing it would run whatever it actually is.");
        verdict.setWrapText(true);
        verdict.getStyleClass().addAll("es-mono", pkg.shaMatches() ? "es-package-match" : "es-package-mismatch");
        box.getChildren().add(verdict);

        if (pkg.fromMarket()) {
            Label note = new Label("Vendor packages are signed by the network and always match. A "
                    + "package from another player is not — check this panel before installing one.");
            note.setWrapText(true);
            note.getStyleClass().add("es-package-note");
            box.getChildren().add(note);
        }
        return box;
    }

    /** What is holding this package, if anything. */
    private static Region status(PackageManifest pkg) {
        VBox box = new VBox(UiTokens.SPACE_1);
        if (pkg.locked()) {
            Label held = new Label("LOCKED — " + pkg.pendingNote());
            held.setWrapText(true);
            held.getStyleClass().addAll("es-mono", "es-package-locked");
            box.getChildren().add(held);
            // ⚠ The rename is named. It is the lock, and a player who has not been told that will
            // read the `.pkg` as a failed download rather than as an unpaid invoice.
            Label why = new Label("It is still a vendor package — a `.pkg`. Confirmation is what "
                    + "turns it into a `.upg` this rig can install.");
            why.setWrapText(true);
            why.getStyleClass().add("es-package-note");
            box.getChildren().add(why);
        } else if (pkg.owned()) {
            Label owned = new Label(
                    "ALREADY INSTALLED — a second copy adds nothing. This one is " + "worth more sold than installed.");
            owned.setWrapText(true);
            owned.getStyleClass().addAll("es-mono", "es-package-locked");
            box.getChildren().add(owned);
        } else {
            Label ready = new Label("READY — nothing is holding this package.");
            ready.getStyleClass().addAll("es-mono", "es-package-match");
            box.getChildren().add(ready);
        }
        return box;
    }

    /**
     * Install and Sell.
     *
     * <p>⚠ Install is disabled from {@link PackageManifest#installable}, which the rules computed —
     * this panel never decides for itself whether a rule would allow something (client pillar C4).
     * ⚠ And a disabled button still needs to say <em>why</em>: the STATUS block above it always does,
     * so the button never has to carry the explanation and never appears refused for no visible
     * reason.
     */
    /**
     * Wraps the panel so a running firmware flash covers it.
     *
     * <h2>⚠ Driven by {@code Pulse.every}, i.e. DATA — not an animation</h2>
     *
     * The bar's fill is the task's real progress against the session clock, so it is a readout rather
     * than decoration. That matters twice: {@code UiContractTest} rations continuous animation by
     * filename (§5.1), and Reduce motion must not suppress this — a player who cannot see that the
     * flash is progressing has no way to distinguish it from a frozen client.
     *
     * <p>⚠ The subscription is closed when the panel leaves the scene. A tool window is destroyed and
     * rebuilt every time it is opened ({@code DeskManager} calls the factory afresh), so a pulse that
     * outlived the panel would be one leaked repaint per open, forever.
     */
    private static Region flashable(GameSession session, PackageManifest pkg, Region panel) {
        // ⚠ A null session is a supported call — the snapshot mains render this panel with no game
        // behind it, which is what makes the four package states checkable without a save. There is
        // no task list to ask, so there is no flash to draw.
        if (session == null) {
            return panel;
        }
        StackPane stack = new StackPane(panel);
        Region overlay = flashOverlay(pkg);
        overlay.setVisible(false);
        overlay.setManaged(false);
        stack.getChildren().add(overlay);

        ProgressState state = new ProgressState(overlay);
        Runnable refresh = () -> state.apply(session);
        refresh.run();
        AutoCloseable subscription = Pulse.shared().every(250, refresh);
        stack.sceneProperty().addListener((obs, was, now) -> {
            if (now == null) {
                try {
                    subscription.close();
                } catch (Exception ignored) {
                    // Nothing to do and nothing to say: unsubscribing is best-effort teardown.
                }
            }
        });
        return stack;
    }

    /** The overlay's live parts, so the pulse updates rather than rebuilds. */
    private static final class ProgressState {

        private final Region overlay;
        private final Label caption;
        private final javafx.beans.property.SimpleDoubleProperty progress =
                new javafx.beans.property.SimpleDoubleProperty(0);

        ProgressState(Region overlay) {
            this.overlay = overlay;
            this.caption = (Label) overlay.lookup(".es-flash-caption");
            Region fill = (Region) overlay.lookup(".es-flash-fill");
            Region track = (Region) overlay.lookup(".es-flash-track");
            // ⚠ BOUND, not assigned on each pulse, and this was a real bug caught by a render: the
            // first version set `fill.setPrefWidth(track.getWidth() * progress)`, and `getWidth()` is
            // 0 until the first layout pass — so the bar was empty on the frame the panel opened and
            // stayed empty in any render, which is a progress bar that does not show progress. A
            // binding is also the only version that stays correct when the window is resized.
            fill.prefWidthProperty().bind(track.widthProperty().multiply(progress));
        }

        void apply(GameSession session) {
            var flash = session.tasks().stream()
                    .filter(task -> "flash".equals(task.facility()))
                    .findFirst();
            boolean running = flash.isPresent();
            overlay.setVisible(running);
            overlay.setManaged(running);
            if (!running) {
                return;
            }
            double done = Math.clamp(flash.get().progress(), 0.0d, 1.0d);
            progress.set(done);
            long left = Math.max(
                    0,
                    java.time.Duration.between(flash.get().asOf(), flash.get().endsAt())
                            .toSeconds());
            caption.setText(Math.round(done * 100) + "%  ·  " + left + "s remaining");
        }
    }

    /**
     * The flashing overlay: a drawn warning mark, the words, and a bar.
     *
     * <h2>⚠ The mark is a PATH this repo draws, not a glyph</h2>
     *
     * {@code U+26A0} is in neither bundled face — {@code GlyphCoverageTest} has already rejected it
     * once, in {@code PortScanView} — and a character that falls back to a host font has different
     * shapes and different advance widths per platform. Drawing it is also the only way to get one
     * big enough to read as a warning rather than as punctuation.
     */
    private static Region flashOverlay(PackageManifest pkg) {
        VBox box = new VBox(UiTokens.SPACE_4);
        box.getStyleClass().add("es-flash-overlay");
        box.setAlignment(Pos.CENTER);

        box.getChildren().add(warningMark());

        Label title = new Label("FLASHING FIRMWARE");
        title.getStyleClass().add("es-flash-title");

        // ⚠ WHAT is being flashed, named on the overlay. It covers the panel completely — opaque, so
        // the facts underneath are unreadable — and an overlay that says only "flashing firmware"
        // leaves a player who opened two packages unable to tell which one they committed to.
        Label what = new Label(pkg.displayName());
        what.getStyleClass().add("es-flash-what");

        Label caption = new Label("0%");
        caption.getStyleClass().add("es-flash-caption");

        // ⚠ Square corners. §9's radius ban is unamended for anything a value is read off, and a
        // progress bar is exactly that — a soft-ended fill reads as a shorter fill.
        Region track = new Region();
        track.getStyleClass().add("es-flash-track");
        track.setPrefSize(360, 14);
        track.setMinSize(360, 14);
        Region fill = new Region();
        fill.getStyleClass().add("es-flash-fill");
        fill.setPrefHeight(14);
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setMaxWidth(360);

        Label note = new Label("The mining tool is frozen until this finishes. Closing this window "
                + "does not stop it — firmware is written by the device, not by this panel.");
        note.setWrapText(true);
        note.setMaxWidth(420);
        note.getStyleClass().add("es-flash-note");
        note.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        box.getChildren().addAll(title, what, caption, bar, note);
        return box;
    }

    /**
     * A warning triangle, drawn.
     *
     * <p>⚠ {@code FillRule} is irrelevant here but the composition is not: the bar and the dot are
     * separate nodes over the triangle rather than a hole punched in it, because a hole would show
     * the panel's own content through the mark and make it unreadable against the facts underneath.
     */
    private static Region warningMark() {
        Polygon triangle = new Polygon(48.0, 4.0, 94.0, 84.0, 2.0, 84.0);
        triangle.getStyleClass().add("es-flash-mark");

        Region bar = new Region();
        bar.getStyleClass().add("es-flash-mark-bar");
        bar.setPrefSize(8, 30);
        bar.setMaxSize(8, 30);
        Region dot = new Region();
        dot.getStyleClass().add("es-flash-mark-bar");
        dot.setPrefSize(8, 8);
        dot.setMaxSize(8, 8);

        VBox stroke = new VBox(6, bar, dot);
        stroke.setAlignment(Pos.CENTER);
        stroke.setMouseTransparent(true);

        StackPane mark = new StackPane(triangle, stroke);
        mark.setPrefSize(96, 88);
        mark.setMaxSize(96, 88);
        StackPane.setAlignment(stroke, Pos.BOTTOM_CENTER);
        StackPane.setMargin(stroke, new javafx.geometry.Insets(0, 0, 12, 0));
        return mark;
    }

    private static Region actions(
            GameSession session, PackageManifest pkg, Runnable onAction, java.util.function.Consumer<String> report) {
        BreachView.Chip install = new BreachView.Chip("Install", "es-breach-chip-loud");
        install.setDisable(!pkg.installable());
        install.setAccessibleText(
                pkg.installable()
                        ? "Install " + pkg.displayName() + ". The package is consumed."
                        : "Install is unavailable: "
                                + (pkg.locked()
                                        ? "the payment has not been mined yet."
                                        : "this tool is already installed."));
        install.onInvoke(() -> {
            report.accept(session.install(pkg.path()).message());
            onAction.run();
        });

        BreachView.Chip sell = new BreachView.Chip("Sell", "es-breach-chip-quiet");
        // ⚠ Not gated on installable(): an ALREADY OWNED package cannot be installed and is precisely
        // the one most worth selling. Only the confirmation hold stops a sale, because selling a
        // package whose payment has not been mined turns goods that are not paid for into spendable
        // ethecoin.
        sell.setDisable(pkg.locked());
        sell.setAccessibleText(
                "Sell " + pkg.displayName() + " on the secondary market. Only " + "ethecoin-gated tools can be sold.");
        sell.onInvoke(() -> {
            report.accept(session.sell(pkg.path()).message());
            onAction.run();
        });

        HBox row = Ui.row(UiTokens.SPACE_3, install, sell);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * One labelled digest.
     *
     * <h2>⚠ Wrapped, and shown in FULL rather than shortened</h2>
     *
     * A digest is 71 monospace characters with no break opportunities, and it was what set the whole
     * panel's width. {@code PackageManifest.shorten} exists and is deliberately not used here: this
     * class's whole argument is that <em>"you are shown both figures and can see for yourself that
     * they are the same, which is what makes the day they differ mean anything"</em>, and an elided
     * middle is exactly where a substituted payload would hide.
     *
     * <p>Both wrap at the same width, so they stay line-for-line comparable — which is how anybody
     * actually checks two hashes by eye.
     */
    private static Region digest(String name, String sha) {
        Label label = new Label(pad(name) + sha);
        label.getStyleClass().addAll("es-mono", "es-package-sha");
        label.setWrapText(true);
        return label;
    }

    private static Region field(String name, String value) {
        Label label = new Label(pad(name) + value);
        label.getStyleClass().addAll("es-mono", "es-package-field");
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-package-heading");
        return label;
    }

    /** A fixed-width label column, so the values line up down the panel. */
    private static String pad(String name) {
        return (name + "            ").substring(0, 12);
    }
}
