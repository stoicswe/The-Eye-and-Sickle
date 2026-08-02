package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * A panel that drops out from under a cell in the top strip.
 *
 * <h2>What it is for</h2>
 *
 * The chain runs while the client does not, so a load has a report to make — and that report used to
 * live at the top of the LEDGER window's CHAIN tab, where a player had to go and look for it. The
 * thing it is about is the <b>balance</b>, which is on screen at all times, so this hangs the report
 * off the balance cell instead: the number that changed, and immediately under it the account of why.
 *
 * <p>It is deliberately not a {@code Notifications} toast. A toast is a line of text that expires;
 * this is a panel with a meter and a multi-line summary, it is the only one of its kind in a session,
 * and it is <em>about</em> a specific readout rather than about the rig in general. Reusing the toast
 * stack would have put it in a queue behind whatever else the load logged, which is exactly where it
 * would be missed.
 *
 * <h2>⚠ It emerges from BEHIND the strip, which is what the clip is for</h2>
 *
 * This layer sits above {@code deckRoot} in the deck's {@link StackPane}, so it paints over the top
 * strip rather than under it. Sliding a panel down from {@code translateY = -height} would therefore
 * draw it <em>on top of</em> the strip on the way past — a panel crossing the readouts it is about,
 * which reads as a rendering fault rather than as a drawer opening.
 *
 * <p>So the container sits at its final place, is clipped to its own bounds, and the <b>content</b>
 * is what moves inside it. The container's top edge is the strip's bottom edge, so the first frame
 * shows nothing and the panel appears to come out from under the strip. The clip is resized with the
 * container, because a clip is a fixed shape and a stale one crops the panel at its old height.
 *
 * <h2>⚠ Motion is DISCRETE, and reduced motion skips it entirely</h2>
 *
 * {@code ui-design-language.md} §5 permits no easing and §9 lists easing curves as build-blocking, so
 * the slide is {@link Interpolator#DISCRETE} over {@link UiTokens#REVEAL_STEPS} steps — the same
 * ladder {@code Notifications} and the panel reveal use. Under reduced motion the panel is simply
 * there, which is also the better behaviour: the animation was never carrying any of the information.
 */
public final class SyncBanner extends StackPane {

    /**
     * What the content sits in, and what the clip crops.
     *
     * <p>⚠ A {@link StackPane}, NOT a plain {@code Pane}. A {@code Pane} computes its preferred size
     * from where its children currently <em>are</em> rather than from what they <em>want</em>, so the
     * panel's size never grew when the replay finished and added its summary lines — the clip stayed
     * at the height measured before the summary existed and cropped it off. A {@code StackPane}
     * propagates a child's preferred size, which is what makes the panel grow into its own report.
     */
    private final StackPane holder = new StackPane();

    /** The clip. A rectangle rather than a shape, because the container is always a rectangle. */
    private final Rectangle clip = new Rectangle();

    /** The cell whose right edge the panel hangs from. */
    private Node xAnchor;

    /**
     * The band whose bottom edge is the panel's top edge.
     *
     * <h2>⚠ Separate from {@link #xAnchor}, and not a tidiness split</h2>
     *
     * A strip cell is centred in a strip that is taller than it, so the cell's bottom edge sits a few
     * pixels ABOVE the strip's. Anchoring the panel's top to the cell would put those few pixels of
     * panel over the strip — and this layer paints above {@code deckRoot}, so they would cover the
     * readouts rather than slide under them. Measured at 27 against 31 when this was written: it
     * looked right, and was right by luck.
     */
    private Node yAnchor;

    private Region content;
    private Runnable onDismiss = () -> {};

    public SyncBanner() {
        getStyleClass().add("es-sync-banner");
        setAlignment(Pos.TOP_LEFT);
        setPickOnBounds(false);
        setVisible(false);
        setManaged(false);
        // ⚠ USE_PREF_SIZE both ways. In a StackPane a child fills the whole cell by default, so
        // without this the banner would be the size of the deck and its clip would reveal nothing.
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        holder.setPickOnBounds(false);
        holder.setAlignment(Pos.TOP_LEFT);
        setClip(clip);
        getChildren().add(holder);
    }

    /**
     * Shows {@code panel} hanging under {@code anchor}.
     *
     * @param xAnchor the cell it belongs to — the panel's right edge lines up with this one's
     * @param yAnchor the band it drops from — this one's bottom edge is the panel's top edge
     * @param panel the content; sized to its own preferred size
     * @param onDismiss run once when the panel goes away, however it goes away
     */
    public void show(Node xAnchor, Node yAnchor, Region panel, Runnable onDismiss) {
        this.xAnchor = xAnchor;
        this.yAnchor = yAnchor;
        this.content = panel;
        this.onDismiss = onDismiss == null ? () -> {} : onDismiss;

        holder.getChildren().setAll(panel);
        panel.setManaged(true);
        setVisible(true);
        setManaged(false);
        // ⚠ CSS FIRST, or the panel has no preferred size to measure. Its padding, font and border
        // all come from the stylesheet, so prefWidth(-1) on a node that has never had CSS applied is
        // zero — reposition would then size the clip to nothing and the panel would be invisible with
        // every other part of the mechanism working correctly.
        panel.applyCss();

        // A click anywhere on it puts it away. There is nothing else to do with it, and a report the
        // player has already read should not sit there until a timer says otherwise.
        setOnMouseClicked(event -> {
            event.consume();
            dismiss();
        });

        // ⚠ Driven by LAYOUT, not by a deferred call. The anchors' bounds are all zero until the
        // strip has laid out, so the obvious `Platform.runLater(this::reposition)` is really a hope
        // that one layout pass has happened by then — and it is wrong twice over: it fires too early
        // on a slow first paint, and it never fires at all in a synchronous render, which is what a
        // snapshot harness does. The listeners below fire on every bounds change including the first,
        // so the panel lands as soon as there is somewhere to land.
        reposition();

        Anchoring.watch(this, xAnchor, yAnchor, this::reposition);
        // ⚠ And the CONTENT itself. The panel is not a fixed size: ChainSyncPanel adds its summary
        // lines when the replay finishes, roughly two seconds after this runs, and without this the
        // clip keeps the height it was given before those lines existed — the report visibly cut off
        // mid-sentence, with the part the player actually needs below the cut.
        panel.layoutBoundsProperty().addListener((o, was, now) -> reposition());
    }

    /**
     * Whether the panel has been placed and started its slide.
     *
     * <p>The slide runs on the first reposition that produces a real size — before that there is
     * nothing to travel and a Timeline over a zero height would finish instantly, leaving the panel
     * simply present. Once is enough: a later resize moves it, it does not re-open it.
     */
    private boolean opened;

    /** Puts the panel where the anchors say, and opens it the first time that yields a real size. */
    private void reposition() {
        Anchoring.Size size = Anchoring.place(this, xAnchor, yAnchor);
        clip.setWidth(size.width());
        clip.setHeight(size.height());
        if (!opened && size.real()) {
            opened = true;
            slideDown(size.height());
        }
    }

    /** The content comes down out of the container's clipped top edge. */
    private void slideDown(double height) {
        if (content == null) {
            return;
        }
        if (Pulse.shared().reducedMotion()) {
            content.setTranslateY(0);
            return;
        }
        // ⚠ The height comes from the measurement that just ran, not from getHeight() or
        // getPrefHeight(). Anchoring.place resizes the node directly rather than going through a
        // layout pass, so neither property is a reliable source here — and a travel of one pixel is a
        // slide that reads as the panel simply appearing.
        double travel = Math.max(1, height);
        content.setTranslateY(-travel);
        Timeline slide = new Timeline();
        double step = UiTokens.REVEAL_MS / UiTokens.REVEAL_STEPS;
        for (int i = 1; i <= UiTokens.REVEAL_STEPS; i++) {
            double remaining = -travel * (1 - i / (double) UiTokens.REVEAL_STEPS);
            slide.getKeyFrames()
                    .add(new KeyFrame(
                            Duration.millis(step * i),
                            new KeyValue(content.translateYProperty(), remaining, Interpolator.DISCRETE)));
        }
        slide.play();
    }

    /**
     * Takes the panel away after {@code millis}.
     *
     * <p>⚠ Called when the <b>summary appears</b>, not when the panel opens, so the reading time is
     * reading time. Starting the clock at the open would spend a third of it on the replay the player
     * cannot read anyway, and the summary is the part with the information in it.
     */
    public void dismissAfter(double millis) {
        if (!isVisible()) {
            return;
        }
        Timeline expiry = new Timeline(new KeyFrame(Duration.millis(millis), e -> dismiss()));
        expiry.play();
    }

    /**
     * Takes the panel away.
     *
     * <p>⚠ Idempotent, and it must be: the caller dismisses on a timer, the player dismisses by
     * clicking, and a character swap dismisses by tearing the deck down. All three can race, and the
     * release this runs closes a {@link Pulse} subscription that must not be closed twice.
     */
    public void dismiss() {
        if (!isVisible()) {
            return;
        }
        setVisible(false);
        setManaged(false);
        holder.getChildren().clear();
        setOnMouseClicked(null);
        opened = false;
        xAnchor = null;
        yAnchor = null;
        content = null;
        Runnable done = onDismiss;
        onDismiss = () -> {};
        done.run();
    }
}
