package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.SocialMark;
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
     * ⚠ The network marks moved to {@code ui/widgets/SocialMark} on 2026-08-06.
     *
     * <p>They were a private enum here until COMS' DIRECT tab needed the Bluesky one too. Copying
     * the path would have made this class's own promise false — that swapping in the official assets
     * is a two-constant edit — and a drifted copy of somebody else's mark is a worse failure than a
     * missing one, because nobody would notice it had happened.
     */

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
    private record Person(String name, String role, String handle, SocialMark network, String slug) {}

    /**
     * ⚠ Order is contribution, not alphabet, and it is hand-held rather than sorted. A credits list
     * that re-sorts itself is a credits list that can silently reorder the people in it.
     */
    private static final List<Person> PEOPLE = List.of(
            new Person("Nathaniel Knudsen", "Developer", "@stoicswe.com", SocialMark.BLUESKY, "nathaniel-knudsen"),
            new Person("Ben Havens", "Musician", "@isotop3.com", SocialMark.BLUESKY, "ben-havens"),
            new Person("Sham Tomaselli", "Artist", "@shamcube", SocialMark.YOUTUBE, "sham-tomaselli"));

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
                + (person.handle() == null ? "" : ", on " + person.network().spokenName() + " as " + person.handle()));
        return row;
    }

    /** The handle, with the mark that says which network it is on. */
    private static Region handle(String text, SocialMark network) {
Region frame = network.node(UiTokens.SOCIAL_MARK, "es-credit-mark");

        Label label = new Label(text);
        label.getStyleClass().add("es-credit-handle");

        HBox row = new HBox(UiTokens.SPACE_2, frame, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

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
