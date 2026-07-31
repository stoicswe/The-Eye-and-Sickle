package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.SystemReport;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.Map;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The rig monitor's ABOUT tab: the mascot, and what the client can truthfully say about the machine.
 *
 * <h2>Mr. Monitor is the one hand-drawn thing in the client, and that is the point</h2>
 *
 * Every other mark on screen is drawn by the toolkit from a token — a rectangle, a hairline, a cell.
 * uOS's mascot is a picture a person drew, and putting it here rather than on the splash or the
 * login screen is deliberate: those two are the rig's <em>firmware</em> and its <em>login</em>, two
 * fictions that predate the operating system. The About panel is the only surface in the client
 * where uOS is talking about itself, which is the one place a mascot belongs.
 *
 * <h2>⚠ Drawn at a fixed width with the ratio preserved, never at a fixed box</h2>
 *
 * Setting both {@code fitWidth} and {@code fitHeight} on an {@code ImageView} stretches the image to
 * fill them, and a stretched drawing is worse than a small one. One dimension is set,
 * {@code preserveRatio} does the other, and {@code UiTokens.MASCOT_WIDTH} is the only number
 * involved — sizes live in tokens (CLAUDE.md), including this one.
 *
 * <h2>Nothing here is game state</h2>
 *
 * The panel takes no {@code GameSession} and holds no timer. It is built once and never refreshed:
 * the client's version does not change while it is running, and neither does the host's memory. That
 * makes this the second window surface after {@code calc} that required checking no invariant —
 * {@code I14} is about state a cheater would forge, and how much RAM the player has is not the
 * server's opinion.
 */
final class RigAbout {

    /**
     * Where the mascot lives on the classpath.
     *
     * <p>⚠ An absolute resource path, because this class is in {@code view} and the picture sits in
     * {@code ui} beside the stylesheets. A relative name would resolve against {@code view} and
     * return null — and a null {@code Image} source is a runtime exception, not a blank panel.
     */
    private static final String MASCOT = "/io/github/stoicswe/eyeandsickle/client/ui/mascot.png";

    private RigAbout() {}

    static Region create() {
        VBox root = new VBox(UiTokens.SPACE_6);

        VBox portrait = new VBox(UiTokens.SPACE_3);
        portrait.setAlignment(Pos.CENTER_LEFT);
        Region mascot = mascot();
        if (mascot != null) {
            portrait.getChildren().add(mascot);
        }

        Label name = Ui.label("Mr. Monitor");
        Label caption = Ui.micro("The uOS mascot. Drawn by hand, by a friend of the house.");
        caption.setWrapText(true);
        portrait.getChildren().addAll(name, caption);

        root.getChildren().addAll(portrait, Ui.hairline(UiTokens.ABOUT_RULE_WIDTH, false), specification(), footnote());
        return root;
    }

    /**
     * The picture, or nothing at all.
     *
     * <p>⚠ Returns null rather than a placeholder when the resource is missing. The alternative — a
     * grey box the size of the mascot — is indistinguishable from a mascot that failed to decode,
     * and this panel exists partly to be looked at when something is wrong. An absent picture with
     * the specification still readable underneath is the honest degradation.
     */
    private static Region mascot() {
        var stream = RigAbout.class.getResourceAsStream(MASCOT);
        if (stream == null) {
            return null;
        }
        Image image = new Image(stream);
        if (image.isError()) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(UiTokens.MASCOT_WIDTH);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setAccessibleText("Mr. Monitor, the uOS mascot: a smiling cathode-ray monitor "
                + "standing on two booted feet, one gloved hand raised.");

        // ⚠ NO PLATE BEHIND IT. One was built and rejected on sight: the drawing is black ink on
        // white, so on the deck's ground the outlines sink into the dark and the gloves and shoes
        // lose their edges — a paper-white panel behind it restores exactly what the artist drew.
        // It also puts the only light surface in the client on this tab, and that was the verdict.
        // The transparent PNG over the panel is the intended look; do not "fix" the contrast.
        //
        // The wrapper stays regardless: an ImageView is not a Region, and the layout measures one.
        VBox frame = new VBox(view);
        frame.setAlignment(Pos.CENTER_LEFT);
        return frame;
    }

    /**
     * {@code KEY   VALUE}, aligned.
     *
     * <p>A {@link GridPane} rather than a stack of {@code KeyValue} rows, and the difference is
     * visible: {@code KeyValue} is an {@code HBox} sized to its own content, so seven of them stack
     * with seven different gutters and the values form a ragged left edge. A specification sheet is
     * read down the value column. The style classes are {@code KeyValue}'s own, so the two still
     * look like the same component.
     */
    private static Region specification() {
        GridPane grid = new GridPane();
        grid.setHgap(UiTokens.SPACE_6);
        grid.setVgap(UiTokens.SPACE_3);

        ColumnConstraints keys = new ColumnConstraints();
        keys.setMinWidth(UiTokens.ABOUT_KEY_WIDTH);
        keys.setHalignment(HPos.LEFT);
        grid.getColumnConstraints().add(keys);

        int row = 0;
        for (Map.Entry<String, String> entry : SystemReport.rows().entrySet()) {
            Label key = Ui.label(entry.getKey());
            key.getStyleClass().add("es-kv-key");
            Label value = Ui.value(entry.getValue());
            // Not es-value-live. §2.1: amber claims the figure is earning or doing work, and a
            // version string is neither. An About panel entirely in the accent colour would turn
            // the one accent the language has into decoration.
            grid.add(key, 0, row);
            grid.add(value, 1, row);
            row++;
        }
        return grid;
    }

    /**
     * The one line that says what the panel is not.
     *
     * <p>⚠ Present because two of the readouts above are deliberately less specific than an
     * operating system's own About box, and an unexplained {@code 16 CORES · AARCH64} where a player
     * expected {@code Apple M4 Max} reads as the game failing to detect their hardware. Saying the
     * client does not look costs one line and converts a apparent bug into a stated boundary. See
     * {@code SystemReport} for the argument.
     */
    private static Region footnote() {
        Label note = Ui.micro("Read from inside this process only — the client starts no helper "
                + "programs and opens no files of yours to fill this panel in, which is why the "
                + "processor and graphics lines name what they can rather than what a system "
                + "profiler would.");
        note.setWrapText(true);
        note.setMaxWidth(UiTokens.ABOUT_RULE_WIDTH);
        return note;
    }
}
