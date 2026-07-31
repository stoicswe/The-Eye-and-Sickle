package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.SetupWizardView;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javax.imageio.ImageIO;

/**
 * Renders every pane of the setup assistant, without opening a window.
 *
 * <p>{@link DeckSnapshot}'s rationale. This screen is seven layouts sharing one frame, and what it
 * gets right or wrong is almost entirely positional: whether a pane centres, whether the step marks
 * stay put as Back appears, whether the palette swatches read as six different palettes while
 * rendered under one. No text assertion sees any of that.
 *
 * <p>⚠ It walks the panes by clicking Continue, exactly as a player does, rather than by reaching
 * into the view. A harness with its own navigation would render seven screens the product cannot
 * actually reach.
 */
public final class SetupWizardSnapshot {

    private SetupWizardSnapshot() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        double width = args.length > 1 ? Double.parseDouble(args[1]) : 980;
        double height = args.length > 2 ? Double.parseDouble(args[2]) : 760;
        out.toFile().mkdirs();

        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                render(out, width, height);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void render(Path out, double width, double height) throws Exception {
        Path dir = out.resolve("setup-profile");
        dir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(dir);
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        ThemeManager themes = new ThemeManager(profile);

        SetupWizardView.Actions actions = new SetupWizardView.Actions() {
            @Override
            public void applyPreview() {}

            @Override
            public void begin(int slot, String handle, String avatarPng) {}

            @Override
            public void cancel() {}
        };

        Region root = SetupWizardView.create(profile, themes, 1, "halflight", actions);
        Scene scene = new Scene(root, width, height);
        themes.adopt(scene);
        themes.select(ThemeId.DECK);

        for (int page = 1; page <= 7; page++) {
            settle(scene);
            write(scene.snapshot(null), out.resolve("setup-" + page + ".png").toFile());
            System.out.println("wrote setup-" + page + ".png");
            if (page < 7) {
                click(root, "es-setup-go");
            }
        }
    }

    /** Fires the Continue chip the way a mouse would, so the harness walks the real navigation. */
    private static void click(Region root, String styleClass) {
        root.lookupAll("." + styleClass).stream()
                .findFirst()
                .ifPresent(node -> node.fireEvent(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        0,
                        0,
                        0,
                        0,
                        javafx.scene.input.MouseButton.PRIMARY,
                        1,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        true,
                        false,
                        false,
                        null)));
    }

    private static void settle(Scene scene) {
        // Two passes, as DeckSnapshot needs: the first resolves CSS and sizes the pane, the second
        // centres it against a frame whose size is finally known.
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }

    private static void write(WritableImage image, File file) throws Exception {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", file);
    }
}
