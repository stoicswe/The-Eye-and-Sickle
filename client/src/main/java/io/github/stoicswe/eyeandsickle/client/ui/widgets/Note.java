package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * One sentence of consequence.
 *
 * <h2>Consequence, not condition</h2>
 *
 * {@code docs/design/ui-design-language.md} §4 defines the component as "one sentence of
 * consequence, not description", and §6 gives the test case:
 *
 * <blockquote>
 * "KX-0155 has paid out nothing for 31 hours. The channel still bills 3 cycles." beats "Miner
 * status: anomalous."
 * </blockquote>
 *
 * <p>The second one is a status field wearing a sentence. The first tells the player what it is
 * costing them and lets them decide. A note that could be replaced by a {@link KeyValue} without
 * losing anything should be a {@link KeyValue}.
 *
 * <h2>Two registers, and the second one is rationed</h2>
 *
 * {@link #consequence} is the ordinary form. {@link #loss} is for loss and hostile state only, and
 * §2.1 permits at most two of those on a screen — so it is a deliberate call each time, not a
 * severity field. Neither is a validation error: "you typed that wrong" is the terminal's job, and
 * §2.1 says outright that alarm is "never a normal validation error".
 */
public final class Note extends HBox {

    private Note(String lead, String rest, boolean loss) {
        getStyleClass().add("es-note");
        if (loss) {
            getStyleClass().add("es-note-bad");
        }

        // A TextFlow rather than two Labels, so the lead clause and the rest wrap as one paragraph.
        // Two labels in an HBox would break between them at exactly the wrong place — after the
        // clause naming the thing and before the clause explaining what it costs.
        Text leadText = new Text(lead == null ? "" : lead);
        leadText.getStyleClass().add("es-note-lead");
        Text restText = new Text(rest == null ? "" : (lead == null || lead.isBlank() ? "" : " ") + rest);
        restText.getStyleClass().add("es-note-text");

        TextFlow flow = new TextFlow(leadText, restText);
        flow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(flow, javafx.scene.layout.Priority.ALWAYS);
        setSpacing(UiTokens.SPACE_2);
        getChildren().add(flow);
    }

    /**
     * @param lead the clause naming the thing, in amber
     * @param rest what it costs the player, in sentence case
     */
    public static Note consequence(String lead, String rest) {
        return new Note(lead, rest, false);
    }

    /** Loss or hostile state. Count these — §2.1 allows two per screen. */
    public static Note loss(String lead, String rest) {
        return new Note(lead, rest, true);
    }

    /**
     * An empty state.
     *
     * <p>§6: "Empty states are an instruction, not a mood piece." So the text should say what to do,
     * not that there is nothing here — the player can see that.
     */
    public static Label empty(String instruction) {
        Label label = new Label(instruction);
        label.getStyleClass().add("es-small");
        label.setWrapText(true);
        return label;
    }
}
