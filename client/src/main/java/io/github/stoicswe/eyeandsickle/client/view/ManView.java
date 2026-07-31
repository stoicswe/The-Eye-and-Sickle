package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.teaching.ManCommands;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.teaching.TermPage;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The manual and the term index — {@code docs/client/04-terminology-and-education.md} §4.6.
 *
 * <h2>The status filter is the strongest honesty statement the client can make</h2>
 *
 * §4.6 asks for a filter on {@code game} / {@code real, simplified} / {@code real}, and the reasoning
 * is worth restating: <em>a player who wants to know exactly what this game made up is entitled to a
 * one-click answer, and being able to give one is the strongest possible statement that the labelling
 * is honest.</em> A game that could not answer that question would be asking to be trusted about
 * everything else on no evidence.
 *
 * <h2>This window works at every teaching level, including off</h2>
 *
 * {@code docs/client/00-client-overview.md} §5.2: definitions are never destroyed, only quieted. The
 * level decides whether explanations arrive unbidden. It never decides whether they exist.
 */
public final class ManView {

    private ManView() {}

    public static Region create(TermDatabase terms) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        // ---- index side
        TextField search = new TextField();
        search.setPromptText("apropos — search by description, not name");
        search.setAccessibleText("Search the manual by description");

        ChoiceBox<String> statusFilter = new ChoiceBox<>();
        statusFilter.getItems().addAll("everything", "real", "real, simplified", "game");
        statusFilter.setValue("everything");
        statusFilter.setAccessibleText("Filter pages by how real they are");

        ListView<TermPage> index = new ListView<>();
        index.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(TermPage page, boolean empty) {
                super.updateItem(page, empty);
                if (empty || page == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                // Never colour alone: the status is spelled out, not merely tinted. A player who
                // cannot distinguish the hues still gets the whole answer (docs/client/07 §5.2).
                setText(page.reference() + "  —  " + page.gloss());
                setAccessibleText(page.nameLine() + ". Status: " + page.status().label());
                getStyleClass().removeAll("es-state-pending", "es-state-unreachable");
                if (page.status() == TermPage.Status.GAME) {
                    getStyleClass().add("es-state-unreachable");
                } else if (page.status() == TermPage.Status.REAL_SIMPLIFIED) {
                    getStyleClass().add("es-state-pending");
                }
            }
        });

        Runnable refilter = () -> {
            String query = search.getText();
            List<TermPage> base = query == null || query.isBlank() ? terms.pages() : terms.apropos(query, true);
            String wanted = statusFilter.getValue();
            List<TermPage> filtered = "everything".equals(wanted)
                    ? base
                    : base.stream()
                            .filter(p -> p.status().label().equals(wanted))
                            .toList();
            index.getItems().setAll(filtered);
        };
        search.textProperty().addListener((o, was, now) -> refilter.run());
        statusFilter.valueProperty().addListener((o, was, now) -> refilter.run());

        Label counts = new Label();
        counts.getStyleClass().add("es-text-secondary");
        counts.setWrapText(true);
        counts.setText(terms.size() + " pages · "
                + terms.withStatus(TermPage.Status.REAL).size()
                + " real · " + terms.withStatus(TermPage.Status.REAL_SIMPLIFIED).size()
                + " simplified · " + terms.withStatus(TermPage.Status.GAME).size() + " ours");

        VBox left = new VBox(8, search, statusFilter, index, counts);
        left.setPadding(new Insets(0, 8, 0, 0));
        VBox.setVgrow(index, Priority.ALWAYS);

        // ---- page side
        TextArea page = new TextArea();
        page.setEditable(false);
        page.getStyleClass().add("es-terminal");
        page.setWrapText(false);
        page.setAccessibleText("Manual page contents");

        Label honesty = new Label();
        honesty.setWrapText(true);
        honesty.setPadding(new Insets(6, 0, 0, 0));

        index.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now == null) {
                page.clear();
                honesty.setText("");
                return;
            }
            page.setText(String.join("\n", ManCommands.render(now)));
            page.positionCaret(0);
            honesty.setText(now.status().label() + " — " + now.status().explanation());
            honesty.getStyleClass().removeAll("es-state-pending", "es-state-unreachable");
            if (now.status() == TermPage.Status.GAME) {
                honesty.getStyleClass().add("es-state-unreachable");
            } else if (now.status() == TermPage.Status.REAL_SIMPLIFIED) {
                honesty.getStyleClass().add("es-state-pending");
            }
        });

        VBox right = new VBox(0, page, honesty);
        VBox.setVgrow(page, Priority.ALWAYS);

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.38);

        Label heading = new Label("MANUAL — man · apropos");
        heading.getStyleClass().add("es-panel-title");
        Label note = new Label("Every page here is shaped like a real manual page, in the real section order. "
                + "Read a few hundred of these and a real one will hold no surprises.");
        note.setWrapText(true);
        note.getStyleClass().add("es-text-secondary");

        VBox top = new VBox(4, heading, note);
        top.setPadding(new Insets(0, 0, 8, 0));

        root.setTop(top);
        root.setCenter(split);

        refilter.run();
        if (!index.getItems().isEmpty()) {
            index.getSelectionModel().selectFirst();
        }

        if (!terms.problems().isEmpty()) {
            // Loud rather than silent. A broken cross-reference is a dead end at exactly the moment
            // somebody was curious enough to follow it.
            Label problems = new Label("Manual problems: " + String.join("; ", terms.problems()));
            problems.setWrapText(true);
            problems.getStyleClass().add("es-state-refused");
            root.setBottom(problems);
        }
        return root;
    }

    /** A compact reader for embedding, used by the docked layout's tab. */
    public static Region compact(TermDatabase terms) {
        return create(terms);
    }

    /** The gloss bar's one-line form, for hover. Tier 1 of the three tiers (§4.2). */
    public static HBox glossBar(TermPage page) {
        Label term = new Label(page.name());
        term.getStyleClass().addAll("es-mono", "es-panel-title");
        Label gloss = new Label(page.gloss());
        gloss.setWrapText(true);
        Label status = new Label("[" + page.status().label() + "]");
        status.getStyleClass().add("es-text-secondary");
        HBox bar = new HBox(8, term, gloss, status);
        bar.getStyleClass().add("es-strip");
        bar.setAccessibleText(page.nameLine() + ". " + page.status().explanation());
        return bar;
    }
}
