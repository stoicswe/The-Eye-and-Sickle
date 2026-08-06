package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.bsky.BlueskyChat;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Separator;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javax.imageio.ImageIO;

/**
 * Renders the DIRECT transcript — chat bubbles and the composer — in every palette.
 *
 * <h2>Why this one needs a picture</h2>
 *
 * The bubbles are the first thing in this client to put text on a ground that is <b>not</b> a panel
 * token, and the accent is a bright sodium on six palettes and a burnt brown on two. {@code
 * ContrastTest} proves the numbers, and numbers are exactly what this deck has learned not to trust
 * on their own: it measured a 64% glass build at a comfortable 2.78:1 while two columns of text sat
 * in the same pixels. What a ratio cannot see is whether a bubble reads as a message or as a
 * full-width band, whether the two sides are actually distinguishable, and whether the square corners
 * look deliberate.
 *
 * <p>It also renders both corner states, because {@code .es-rounded} is an opt-in and a bubble is the
 * one shape in this client a reader expects to be round — so the square default is the one worth
 * looking at.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.DirectSnapshot \
 *     -Dexec.args="/tmp/direct"
 * }</pre>
 */
public final class DirectSnapshot {

    private DirectSnapshot() {}

    private static final String ME = "did:plc:me";
    private static final String THEM = "did:plc:them";
    private static final String THIRD = "did:plc:third";

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        out.toFile().mkdirs();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                render(out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void render(Path out) throws Exception {
        Path profileDir = out.resolve("profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        ThemeManager themes = new ThemeManager(profile);

        for (ThemeId theme : ThemeId.selectable()) {
            themes.select(theme);
            shoot(
                    themes,
                    panel(false),
                    out.resolve("dm-" + theme.name().toLowerCase(java.util.Locale.ROOT) + ".png"),
                    null);
        }
        // ⚠ The opt-in, on the base palette only — the question it answers is whether the radius is
        // gated at all, and that is one stylesheet's worth of question.
        themes.select(ThemeId.values()[0]);
        shoot(themes, panel(true), out.resolve("dm-rounded.png"), null);

        // ⚠ THE COMPOSER'S GROWTH IS THE PART A SINGLE FRAME CANNOT SHOW. Typed text is set AFTER the
        // first layout pass, because the row count is measured off the laid-out `.text` node — before
        // one has happened the lookup returns null and the fallback counts newlines, which is exactly
        // the undercount the measurement exists to avoid. So these two frames are also the check that
        // the measured path, not the fallback, is what runs.
        shoot(themes, panel(false), out.resolve("dm-composer-two-lines.png"), typed(TWO_LINES));
        shoot(themes, panel(false), out.resolve("dm-composer-overflow.png"), typed(MANY_LINES));

        // ⚠ OPENING A CONVERSATION MUST LAND ON THE NEWEST MESSAGE. A history shorter than the
        // viewport cannot show this — there is nothing to scroll — so this one is deliberately long
        // enough to overflow, and the check is the pane's own vvalue rather than a look at the
        // picture. Driving `DirectView.scrollToEnd` rather than a copy of it is the point: a harness
        // that reimplemented the two layout calls would agree with itself and prove nothing.
        shoot(themes, panel(false, LONG_HISTORY), out.resolve("dm-scrolled-to-latest.png"), root -> {
            javafx.scene.control.ScrollPane pane =
                    (javafx.scene.control.ScrollPane) root.lookup(".es-dm-transcript");
            DirectView.scrollToEnd(pane);
            System.out.printf(
                    "    transcript: vvalue=%.2f (1.00 = newest message in view)%n", pane.getVvalue());
        });
    }

    /** Long enough to overflow the viewport, so "scrolled to the end" is a question with an answer. */
    private static final int LONG_HISTORY = 40;

    private static final String TWO_LINES = "that estate box is still leaking — I want another look before we move on, "
            + "and I would rather do it while their detection array is still down";

    /** ⚠ Comfortably past six lines in this column, so the cap and the scrollbar both have to show. */
    private static final String MANY_LINES = "so the plan is: sweep the block first, then port scan "
            + "the two that answer, then decide which one is worth the heat. if the firewall reads T3 "
            + "we leave it — that is nearly two thirds of a starting rig just to hold the door open, "
            + "and the loot on those boxes has never been worth it. bring the tarpit anyway.\n"
            + "second thing: do not buy the compute rung yet.";

    private static java.util.function.Consumer<Region> typed(String text) {
        return panel -> {
            javafx.scene.control.TextArea input = (javafx.scene.control.TextArea) panel.lookup(".es-dm-input");
            input.setText(text);
        };
    }

    /** @param rounded whether to apply the player's §9.3 opt-in */
    private static Region panel(boolean rounded) {
        return panel(rounded, 0);
    }

    /** @param extra how many filler messages to prepend, so the transcript overflows its viewport */
    private static Region panel(boolean rounded, int extra) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(THEM, "Ada Lovelace");
        names.put(THIRD, "grace.bsky.social");

        List<BlueskyChat.Message> history = List.of(
                new BlueskyChat.Message("1", THEM, "did you get the dump off the estate box?", at(0), false),
                new BlueskyChat.Message("2", ME, "yeah — took three tries, their tarpit is nasty", at(2), false),
                new BlueskyChat.Message(
                        "3",
                        ME,
                        "the vault estimate was way off too. it read 40 EC on the scan and there was "
                                + "nearly triple that sitting in the arrivals tier",
                        at(3),
                        false),
                new BlueskyChat.Message("4", THIRD, "post the report?", at(5), false),
                new BlueskyChat.Message("5", THEM, "", at(6), true),
                new BlueskyChat.Message("6", ME, "ok", at(7), false));

        VBox transcript = new VBox(UiTokens.SPACE_2);
        transcript.getStyleClass().add("es-body-pad");
        for (int i = 0; i < extra; i++) {
            String who = i % 2 == 0 ? THEM : ME;
            transcript
                    .getChildren()
                    .add(DirectView.bubble(
                            ME, names, new BlueskyChat.Message("f" + i, who, "earlier message " + i, at(-100 + i), false)));
        }
        for (BlueskyChat.Message message : history) {
            transcript.getChildren().add(DirectView.bubble(ME, names, message));
        }

        Region scroller = Views.scrollable(transcript);
        // ⚠ A class the harness can find it by. `lookup(".scroll-pane")` would match the composer's
        // own internal one first on some layouts, which is the `.es-shmark-listing` trap.
        scroller.getStyleClass().add("es-dm-transcript");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        DirectView.Composer composer = new DirectView.Composer();
        // ⚠ Armed, or it renders disabled — which is a real state, and not the one worth checking.
        composer.armFor(null, "convo", message -> {});

        VBox column = new VBox(scroller, new Separator(), composer.node());
        // ⚠ ON A PANEL, NOT ON THE DESK — and the first version of this harness got it wrong.
        // `es-scene-ground` paints `-es-void`, and in the running client this transcript is inside a
        // window whose body is `-es-panel`. On the dark palettes the two are within a shade of each
        // other so the mistake is invisible; on uOS Classic they are #A8A8A8 and #E4E4E4, so the
        // neutral bubble was being judged against a ground it never sits on. That is the difference
        // between checking whether the bubble is visible and checking nothing at all — and Classic is
        // the palette that catches this class of bug, which is exactly why it must be right here.
        column.getStyleClass().add("es-panel");
        if (rounded) {
            column.getStyleClass().add("es-rounded");
        }
        return column;
    }

    private static Instant at(int minutes) {
        return Instant.parse("2026-08-06T14:00:00Z").plusSeconds(minutes * 60L);
    }

    private static void shoot(ThemeManager themes, Region panel, Path to, java.util.function.Consumer<Region> after)
            throws Exception {
        int w = 640;
        int h = 520;
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, w, h);
        themes.adopt(scene);
        // Twice: the first pass resolves the stylesheet, the second lays out against the resolved
        // sizes. A single pass photographs a half-measured panel.
        scene.getRoot().applyCss();
        host.layout();
        scene.getRoot().applyCss();
        host.layout();
        if (after != null) {
            after.accept(panel);
            scene.getRoot().applyCss();
            host.layout();
            // ⚠ Reported AFTER the relayout, and only there. Read in the consumer it is the height
            // the box had BEFORE the new text — which is the exact staleness the row-count bug was,
            // so a check taken at that moment would agree with the defect it is meant to catch.
            javafx.scene.control.TextArea input = (javafx.scene.control.TextArea) panel.lookup(".es-dm-input");
            System.out.printf(
                    "    composer: prefRowCount=%d, height=%.0fpx%n", input.getPrefRowCount(), input.getHeight());
        }
        WritableImage image = scene.snapshot(new WritableImage(w, h));
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                png.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(png, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }
}
