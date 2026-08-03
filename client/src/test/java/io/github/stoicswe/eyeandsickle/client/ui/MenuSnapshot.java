package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.CharacterSlots;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.MainMenuView;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;

/**
 * Renders the login screen to a PNG, without opening a window.
 *
 * <p>{@link DeckSnapshot}'s rationale, applied to the one screen it does not cover. The menu
 * ({@code ui-design-language.md} §3.1) is the client's only centred layout, and everything it gets
 * right or wrong is positional: whether the faces are a row or a stack, whether the selected ring is
 * distinguishable from the resting one, whether the empty and damaged rings read differently at a
 * glance. No text assertion sees any of that.
 *
 * <p>Both states are rendered because they share almost no nodes. The first-run screen — three empty
 * slots and a handle field — is what every player sees first, and a populated render says nothing
 * about it.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.MenuSnapshot \
 *     -Dexec.args="/tmp/out 980 760"
 * }</pre>
 */
public final class MenuSnapshot {

    private MenuSnapshot() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        double width = args.length > 1 ? Double.parseDouble(args[1]) : 980;
        double height = args.length > 2 ? Double.parseDouble(args[2]) : 760;
        out.toFile().mkdirs();

        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                // Every palette for the populated screen; the default only for the first run. What
                // separates the two states is LAYOUT, which is palette-independent — rendering the
                // empty one five more times would prove nothing new.
                render(out, width, height, true, ThemeId.selectable());
                render(out, width, height, false, List.of(ThemeId.DECK));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void render(Path out, double width, double height, boolean populated, List<ThemeId> palettes)
            throws Exception {

        String tag = populated ? "menu" : "menu-firstrun";
        Path profileDir = out.resolve(tag + "-profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        // Otherwise the familiarity prompt (CL-4) opens a modal over the render on the first run.
        profile.settings().askedFamiliarity = true;
        ThemeManager themes = new ThemeManager(profile);

        CharacterSlots slots = new CharacterSlots(profile);
        if (populated) {
            // Two characters and one empty slot, so one render shows every face state there is.
            SoloGame.open(
                            new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(slots.saveFile(1)),
                            "halflight",
                            Clock.systemUTC())
                    .persist();
            SoloGame.open(
                            new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(slots.saveFile(2)),
                            "kestrel",
                            Clock.systemUTC())
                    .persist();
        }

        MainMenuView.Actions actions = new MainMenuView.Actions() {
            @Override
            public void playSolo(int slot, String handleIfNew) {}

            @Override
            public void setUpNewCharacter(int slot, String suggestedHandle) {}

            @Override
            public void addOnlineAccount() {
                // A render harness has no browser and no keychain; the panel is photographed by its
                // own snapshot, not by opening it from here.
            }

            @Override
            public void connectOnline(String serverAddress) {}

            @Override
            public void openSettings() {}

            @Override
            public void quit() {}
        };

        Scene scene = new Scene(MainMenuView.create(profile, themes, slots, actions), width, height);
        themes.adopt(scene);
        for (ThemeId id : palettes) {
            themes.select(id);
            // Two passes, as DeckSnapshot needs: the first resolves CSS and gives the faces a size,
            // the second lays the row out against it.
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            WritableImage image = scene.snapshot(null);
            write(image, out.resolve(tag + "-" + id.id() + ".png").toFile());
            System.out.println(
                    "wrote " + tag + "-" + id.id() + ".png  " + (int) image.getWidth() + "x" + (int) image.getHeight());
        }
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
