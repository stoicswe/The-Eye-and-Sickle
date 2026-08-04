package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.Avatar;
import io.github.stoicswe.eyeandsickle.client.ui.Png;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.io.File;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Picking a picture: the system file dialog, then crop and zoom.
 *
 * <h2>⚠ This is the ONLY place the client reads a host file it did not write</h2>
 *
 * {@code docs/client/00} §4.5 makes the profile directory the only host filesystem this client
 * touches, and §7 makes that a security boundary rather than a scope decision. This crosses it once,
 * deliberately, and under three conditions that keep the rule intact:
 *
 * <ul>
 *   <li><b>The player picks the file in their own operating system's dialog.</b> Nothing here builds
 *       a path, and nothing a player types anywhere in the game reaches this code.
 *   <li><b>It is read once.</b> What is kept is the pixels; the path is discarded and never stored.
 *       A saved path would mean the game reading an arbitrary host location on every launch, which
 *       is precisely what §7 forbids.
 *   <li><b>Failure is silent and harmless.</b> An unreadable or absurd file yields no picture and no
 *       exception — a chooser that could throw would let a malformed image end a session.
 * </ul>
 *
 * <h2>Crop and zoom, not "fit"</h2>
 *
 * The result is square and small, and automatic fitting would put a letterboxed rectangle inside it
 * — which looks like a bug rather than a policy. Letting the player choose what is in frame is both
 * better and less code than any heuristic that tries to find a face.
 */
public final class AvatarChooser {

    /** ⚠ JUL — captured by {@code log/ClientLog} for the CLIENT LOGS tab. */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(AvatarChooser.class.getName());

    private AvatarChooser() {}

    /** Side of the crop viewport on screen. Larger than the stored image, so cropping is precise. */
    private static final double VIEWPORT = 260;

    /**
     * Opens the system file dialog, then the cropper.
     *
     * @param onChosen receives the base64 PNG, or is not called at all if the player backs out
     */
    public static void choose(Window owner, String handle, Consumer<String> onChosen) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a picture");
        // The formats JavaFX can actually decode. Offering more would produce a file dialog that
        // accepts something the next step silently rejects.
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));

        File file;
        try {
            file = chooser.showOpenDialog(owner);
        } catch (RuntimeException dialogFailed) {
            // A file dialog that will not open is the desktop's problem, not something to end a
            // session over. ⚠ Silent ON SCREEN, not silent in the log — "I clicked it and nothing
            // happened" is unanswerable without this line, and it is the whole reason the CLIENT
            // LOGS tab exists.
            LOG.log(java.util.logging.Level.WARNING, "the file dialog would not open", dialogFailed);
            return;
        }
        if (file == null) {
            return;
        }
        Image image;
        try {
            image = new Image(file.toURI().toString(), false);
        } catch (RuntimeException unreadable) {
            LOG.log(java.util.logging.Level.WARNING, "could not read the chosen picture " + file, unreadable);
            return;
        }
        if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            LOG.log(java.util.logging.Level.WARNING, "the chosen picture is not a usable image: {0}", file);
            return;
        }
        showCropper(owner, image, handle, onChosen);
    }

    /**
     * The crop dialog: drag to move, slider to zoom, and a live square preview.
     *
     * <p>The viewport is clipped to the same square the picture will be stored as, so what the
     * player sees during the crop is what the crop produces. A preview that differed from the result
     * by even the aspect ratio would make every choice a guess.
     */
    private static void showCropper(Window owner, Image image, String handle, Consumer<String> onChosen) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(false);

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);

        StackPane viewport = new StackPane(view);
        viewport.getStyleClass().add("es-crop-viewport");
        viewport.setMinSize(VIEWPORT, VIEWPORT);
        viewport.setPrefSize(VIEWPORT, VIEWPORT);
        viewport.setMaxSize(VIEWPORT, VIEWPORT);
        Rectangle clip = new Rectangle(VIEWPORT, VIEWPORT);
        viewport.setClip(clip);

        // Zoom is a multiplier on the size that would just FILL the viewport, so 1.0 is always a
        // sensible starting frame whatever shape the original is — a slider in absolute pixels would
        // start uselessly small for a photograph and uselessly large for an icon.
        double fill = Math.max(VIEWPORT / image.getWidth(), VIEWPORT / image.getHeight());
        Slider zoom = new Slider(1.0, 4.0, 1.0);
        zoom.setPrefWidth(VIEWPORT);

        double[] offset = {0, 0};
        double[] dragFrom = {0, 0};

        Runnable apply = () -> {
            view.setFitWidth(image.getWidth() * fill * zoom.getValue());
            view.setTranslateX(offset[0]);
            view.setTranslateY(offset[1]);
        };
        zoom.valueProperty().addListener((o, was, now) -> apply.run());
        viewport.setOnMousePressed(event -> {
            dragFrom[0] = event.getSceneX() - offset[0];
            dragFrom[1] = event.getSceneY() - offset[1];
        });
        viewport.setOnMouseDragged(event -> {
            offset[0] = event.getSceneX() - dragFrom[0];
            offset[1] = event.getSceneY() - dragFrom[1];
            apply.run();
        });
        apply.run();

        BreachView.Chip save = new BreachView.Chip("Use this", "es-files-action");
        BreachView.Chip cancel = new BreachView.Chip("Cancel", "es-files-action");
        save.onInvoke(() -> {
            String encoded = crop(viewport);
            popup.hide();
            if (encoded != null) {
                onChosen.accept(encoded);
            }
        });
        cancel.onInvoke(popup::hide);

        Label hint = Ui.small(Views.t(
                "ui.avatar-chooser.drag-the-picture-to",
                "Drag the picture to move it. The square is exactly what gets saved."));
        hint.setWrapText(true);
        hint.setMaxWidth(VIEWPORT);

        VBox panel = new VBox(
                UiTokens.SPACE_3,
                Ui.label(Views.t("ui.avatar-chooser.crop", "Crop")),
                viewport,
                zoom,
                hint,
                Ui.row(UiTokens.SPACE_3, save, cancel));
        panel.getStyleClass().addAll("es-files", "es-body-pad", "es-files-dialog");
        panel.setAlignment(Pos.CENTER_LEFT);

        popup.getContent().add(panel);
        if (owner != null) {
            popup.show(owner, owner.getX() + (owner.getWidth() - VIEWPORT) / 2 - 20, owner.getY() + 100);
        }
    }

    /**
     * Snapshots the viewport and re-samples it to the stored size.
     *
     * <p>⚠ Snapshot rather than arithmetic over the source image. The player positioned the picture
     * by eye against this exact square, and reconstructing the same crop from zoom and offset
     * numbers is a second implementation of the framing they can see — one that would drift the
     * first time the viewport's padding changed. What is on screen is the answer.
     *
     * @return the base64 PNG, or null if the snapshot produced nothing usable
     */
    private static String crop(Region viewport) {
        WritableImage shot;
        try {
            shot = viewport.snapshot(null, null);
        } catch (RuntimeException snapshotFailed) {
            return null;
        }
        if (shot.getWidth() <= 0 || shot.getHeight() <= 0) {
            return null;
        }
        PixelReader reader = shot.getPixelReader();
        if (reader == null) {
            return null;
        }
        int size = Avatar.SIZE;
        int[] argb = new int[size * size];
        // Nearest-neighbour down-sample. A box filter would be smoother and this is a ninety-six
        // pixel square shown at forty — the difference is not visible, and the loop that is easy to
        // read is worth more than the one that is slightly prettier.
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sx = (int) Math.min(shot.getWidth() - 1, x * shot.getWidth() / size);
                int sy = (int) Math.min(shot.getHeight() - 1, y * shot.getHeight() / size);
                argb[y * size + x] = reader.getArgb(sx, sy);
            }
        }
        return Avatar.encode(Png.encode(argb, size, size));
    }

    /** A small square view of whatever picture the operator currently has. */
    public static ImageView thumbnail(String base64, String handle, double size) {
        ImageView view = new ImageView(Avatar.image(base64, handle));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.getStyleClass().add("es-avatar");
        return view;
    }

    /** The picture beside the name, as the Operator page shows it. */
    public static HBox row(String base64, String handle, double size) {
        HBox box = new HBox(UiTokens.SPACE_3, thumbnail(base64, handle, size));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
