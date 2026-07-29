package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/**
 * Settings → Credits. The people who made it.
 *
 * <h2>Why this is its own page and not a line in About</h2>
 *
 * About answers "what is this machine", in the fiction's own voice — uOS, a FreeBSD-derived kernel,
 * a cycle count. Credits answers "who are these people", out of the fiction entirely. Folding real
 * names into the spec sheet would put them in the same list as an invented kernel version, which is
 * the one context where a real person's name reads as set dressing.
 *
 * <h2>⚠ Portraits are looked up, not required</h2>
 *
 * Each entry looks for {@code ui/credits/<slug>.png} on the classpath and falls back to an initialled
 * ring when it is absent. So a photograph is added by <b>dropping a file in</b> — no code change, no
 * rebuild of this class, and nothing to remember to wire up. The fallback is a dashed outline for the
 * same reason {@code MainMenuView}'s empty slot is: a placeholder that looks finished never gets
 * replaced.
 *
 * <h2>Nothing here is a link</h2>
 *
 * The handles are printed, not clickable. Opening a browser is an outward-facing action the client
 * has never taken, and a settings panel is a poor place for the first one — a mis-click would throw
 * the player out of a full-screen game into a web browser. The butterfly says what kind of handle it
 * is; the player types it wherever they already read Bluesky.
 */
final class Credits {

    /**
     * The networks a handle can be on, and the mark that says which.
     *
     * <h2>⚠ These are NOT the official logo files, and must not be mistaken for them</h2>
     *
     * Both paths were authored in this repository, drawn to each mark's silhouette so a reader knows
     * what kind of handle follows — in a client that bundles no third-party artwork and downloads
     * nothing at run time. If the official assets are ever wanted they replace these two constants
     * and nothing else about the code changes.
     *
     * <p>Both are drawn in a 24×24 box and scaled by {@link UiTokens#SOCIAL_MARK}. The butterfly is
     * symmetric about x=12, so an edit to one half has to be mirrored in the other. The play mark is
     * a rounded plate with the triangle as a <b>hole</b> rather than a second filled shape, which is
     * why it needs {@link FillRule#EVEN_ODD} — under the default non-zero rule the triangle fills in
     * and the mark becomes a solid lozenge.
     *
     * <p>§9's ban on rounded corners is not in play here. That rule governs the interface's own
     * geometry — panels, cells, meters — and this is somebody else's mark quoted inside it, drawn as
     * a path rather than as a {@code -fx-background-radius} the contract test could even see.
     */
    private enum Network {
        BLUESKY("M12 7"
                + "C10.5 4.2 6.5 1.5 3.8 2.2 C1.2 2.9 1.1 6.4 2.6 9.1 C3.6 10.9 5.2 12.2 6.9 12.9 "
                + "C5.1 13.4 3.7 14.6 3.6 16.2 C3.5 18.6 6.1 20.4 8.6 19.6 "
                + "C10.6 19.0 11.7 16.6 12 14.4 C12.3 16.6 13.4 19.0 15.4 19.6 "
                + "C17.9 20.4 20.5 18.6 20.4 16.2 C20.3 14.6 18.9 13.4 17.1 12.9 "
                + "C18.8 12.2 20.4 10.9 21.4 9.1 C22.9 6.4 22.8 2.9 20.2 2.2 C17.5 1.5 13.5 4.2 12 7 Z",
                FillRule.NON_ZERO,
                "Bluesky"),

        YOUTUBE("M5 5 H19 A4 4 0 0 1 23 9 V15 A4 4 0 0 1 19 19 H5 A4 4 0 0 1 1 15 V9 A4 4 0 0 1 5 5 Z"
                + "M10 8.6 V15.4 L16 12 Z",
                FillRule.EVEN_ODD,
                "YouTube");

        private final String path;
        private final FillRule fill;
        private final String spokenName;

        Network(String path, FillRule fill, String spokenName) {
            this.path = path;
            this.fill = fill;
            this.spokenName = spokenName;
        }
    }

    /** Where a portrait goes when there is one. See the class comment. */
    private static final String PORTRAITS = "/io/github/stoicswe/eyeandsickle/client/ui/credits/";

    /**
     * Extensions tried, in order, for {@code <slug>.<ext>}.
     *
     * <p>⚠ More than just {@code .png} because the person dropping a photograph in is not
     * necessarily the person who wrote this, and a photo that silently does not appear because it
     * came off a phone as {@code .jpg} is a bug with no error message. JavaFX decodes all four
     * natively, so accepting them costs nothing. PNG first: it is what a screenshot or an exported
     * avatar usually is, and the only one of the four with alpha.
     */
    private static final List<String> PORTRAIT_TYPES = List.of(".png", ".jpg", ".jpeg", ".gif");

    /**
     * One person.
     *
     * @param slug the portrait's file name, without the extension
     * @param handle their handle, or null when they have not given one
     * @param network which service {@code handle} is on; ignored when the handle is null
     */
    private record Person(String name, String role, String handle, Network network, String slug) {}

    /**
     * ⚠ Order is contribution, not alphabet, and it is hand-held rather than sorted. A credits list
     * that re-sorts itself is a credits list that can silently reorder the people in it.
     */
    private static final List<Person> PEOPLE = List.of(
            new Person("Nathaniel Knudsen", "Developer",
                    "@stoicswe.com", Network.BLUESKY, "nathaniel-knudsen"),
            new Person("Ben Havens", "Musician",
                    "@isotop3.com", Network.BLUESKY, "ben-havens"),
            new Person("Sham Tomaselli", "Artist",
                    "@shamcube", Network.YOUTUBE, "sham-tomaselli"));

    private Credits() {}

    static Region page() {
        VBox box = new VBox(UiTokens.SPACE_6);
        box.getStyleClass().add("es-credits");
        for (Person person : PEOPLE) {
            box.getChildren().add(entry(person));
        }
        return box;
    }

    private static Region entry(Person person) {
        Label name = new Label(person.name());
        name.getStyleClass().add("es-credit-name");

        Label role = new Label(Ui.upper(person.role()));
        role.getStyleClass().add("es-credit-role");

        VBox lines = new VBox(UiTokens.SPACE_1, name, role);
        if (person.handle() != null) {
            lines.getChildren().add(handle(person.handle(), person.network()));
        }
        lines.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(UiTokens.SPACE_5, portrait(person), lines);
        row.setAlignment(Pos.CENTER_LEFT);
        // The whole row reads as one person to a screen reader, rather than as a picture followed by
        // three unrelated fragments. ⚠ The network is SPOKEN here and nowhere else on screen: sighted
        // readers get it from the mark, and a screen reader cannot see a butterfly.
        row.setAccessibleText(person.name() + ", " + person.role()
                + (person.handle() == null
                        ? ""
                        : ", on " + person.network().spokenName + " as " + person.handle()));
        return row;
    }

    /** The handle, with the mark that says which network it is on. */
    private static Region handle(String text, Network network) {
        SVGPath mark = new SVGPath();
        mark.setContent(network.path);
        mark.setFillRule(network.fill);
        mark.getStyleClass().add("es-credit-mark");
        // ⚠ A scale transform, not a resize: an SVGPath has no width to set, and letting the layout
        // stretch it would distort a symmetric mark asymmetrically. The factor is derived from the
        // authoring box so the token stays the only number anyone edits.
        double scale = UiTokens.SOCIAL_MARK / MARK_BOX;
        mark.setScaleX(scale);
        mark.setScaleY(scale);
        // ⚠ Wrapped, and the wrapper is sized explicitly. scaleX/scaleY are applied AFTER layout, so
        // the path still asks the row for its full 24px and the label would sit a wing's width too
        // far right. The StackPane reserves the drawn size instead of the authored one.
        StackPane frame = new StackPane(mark);
        frame.setMinSize(UiTokens.SOCIAL_MARK, UiTokens.SOCIAL_MARK);
        frame.setPrefSize(UiTokens.SOCIAL_MARK, UiTokens.SOCIAL_MARK);
        frame.setMaxSize(UiTokens.SOCIAL_MARK, UiTokens.SOCIAL_MARK);

        Label label = new Label(text);
        label.getStyleClass().add("es-credit-handle");

        HBox row = new HBox(UiTokens.SPACE_2, frame, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** The box both marks are drawn in. Not a token: it describes the paths, not the layout. */
    private static final double MARK_BOX = 24;

    /**
     * A photograph if one has been dropped in, otherwise initials in a dashed ring.
     *
     * <p>⚠ The picture is clipped by a {@link Circle} rather than given a corner radius. Two reasons,
     * and {@code MainMenuView.face} learned both first: §9 permits a non-zero radius only under
     * {@code .es-rounded}, and an {@code ImageView} has no background for a radius to round anyway.
     * Geometry on this deck is a clip.
     */
    private static Region portrait(Person person) {
        double size = UiTokens.CREDIT_FACE;
        for (String type : PORTRAIT_TYPES) {
            var stream = Credits.class.getResourceAsStream(PORTRAITS + person.slug() + type);
            if (stream == null) {
                continue;
            }
            Image image = new Image(stream);
            if (image.isError()) {
                continue;
            }
            ImageView view = new ImageView(image);
            // ⚠ Filled to a square and NOT ratio-preserved, which is the opposite of the mascot's
            // rule one file over — and for the opposite reason. The mascot is a drawing whose shape
            // is the artwork; this is a portrait behind a circular window, and preserving the ratio
            // of a 4:3 photo would letterbox it inside the circle with two slivers of panel showing
            // through. Photographs get cropped by the clip, which is what a round avatar is.
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(false);
            view.setSmooth(true);
            view.setClip(new Circle(size / 2, size / 2, size / 2));
            view.setAccessibleText(person.name());
            return frame(view, size);
        }

        Circle ring = new Circle(size / 2);
        ring.getStyleClass().add("es-credit-ring");
        Label initials = new Label(initials(person.name()));
        initials.getStyleClass().add("es-credit-initials");
        return frame(new StackPane(ring, initials), size);
    }

    /** Fixes the portrait's footprint so the three rows line up whether or not a photo exists. */
    private static Region frame(Node content, double size) {
        StackPane frame = new StackPane(content);
        frame.setMinSize(size, size);
        frame.setPrefSize(size, size);
        frame.setMaxSize(size, size);
        return frame;
    }

    /**
     * First letters of the first and last words.
     *
     * <p>Defensive about the shape of a name rather than assuming two words: people have one name,
     * and people have four. {@link Locale#ROOT} for the same reason {@code ui/Ui} spells it out.
     */
    static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return "";
        }
        String first = words[0].substring(0, 1);
        String last = words.length > 1 ? words[words.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
