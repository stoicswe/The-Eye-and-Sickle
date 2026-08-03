package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.oauth.SignInFlow;
import io.github.stoicswe.eyeandsickle.client.oauth.TokenStore;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.OnlineAccountPanel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Renders the "Add an online account" panel.
 *
 * <p>⚠ It compiles and its tests pass; neither says anything about whether it <em>lays out</em>. This
 * panel is mostly wrapped explanatory prose in a popup with no enclosing width, which is exactly the
 * shape that renders as one line running off the edge, or as a column two characters wide. Rendering
 * is the only way to know.
 *
 * <p>Both storage modes are photographed, because the panel says something different in each and the
 * fallback's line is nearly three times longer.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.OnlineAccountSnapshot \
 *     -Dexec.args="/tmp/out"
 * }</pre>
 */
public final class OnlineAccountSnapshot extends Application {

    private static Path out = Path.of("target/snapshots");

    public static void main(String[] args) {
        if (args.length > 0) {
            out = Path.of(args[0]);
        }
        out.toFile().mkdirs();
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        ClientProfile profile = ClientProfile.discover();
        ThemeManager themes = new ThemeManager(profile);

        render(themes, "online-account-keychain", store("macOS Keychain", true));
        render(themes, "online-account-file", store("encrypted file (no system keychain available)", false));
        Platform.exit();
    }

    private static void render(ThemeManager themes, String tag, TokenStore store) throws Exception {
        VBox panel = OnlineAccountPanel.build(
                new OnlineAccountPanel.Host() {
                    @Override
                    public void signIn(
                            String handle,
                            String server,
                            Consumer<SignInFlow.Identity> onDone,
                            Consumer<Exception> onError) {
                        // Nothing is signed in from a render harness.
                    }

                    @Override
                    public TokenStore store() {
                        return store;
                    }
                },
                identity -> {});

        // The popup has no enclosing width, so the harness gives it one — the same thing the login
        // screen does. Sized generously on purpose: if the prose still overflows here, it overflows.
        StackPane root = new StackPane(panel);
        root.getStyleClass().add("es-root");
        Scene scene = new Scene(root, 560, 620);
        themes.adopt(scene);

        for (ThemeId id : List.of(ThemeId.values())) {
            themes.select(id);
            // Two passes, as the other snapshots need: the first resolves CSS and sizes the labels,
            // the second wraps them against that size.
            root.applyCss();
            root.layout();
            root.applyCss();
            root.layout();

            WritableImage image = scene.snapshot(null);
            write(image, out.resolve(tag + "-" + id.id() + ".png").toFile());
            System.out.println("wrote " + tag + "-" + id.id() + ".png  " + (int) image.getWidth() + "x"
                    + (int) image.getHeight() + "  panelHeight=" + (int) panel.getHeight());
        }
    }

    private static TokenStore store(String description, boolean secured) {
        return new TokenStore() {
            @Override
            public Credentials load() {
                return null;
            }

            @Override
            public void save(Credentials credentials) {}

            @Override
            public void clear() {}

            @Override
            public boolean isPlatformSecured() {
                return secured;
            }

            @Override
            public String describe() {
                return description;
            }
        };
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
