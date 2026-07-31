package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.shell.Command;
import io.github.stoicswe.eyeandsickle.client.shell.ExitStatus;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * The command palette — {@code Shortcut+K}, from {@code docs/client/00-client-overview.md} §6.3.
 *
 * <h2>The missing middle layer</h2>
 *
 * The client had two ways to do things and a gap between them: click a button on a tool window, or
 * type a command with correct syntax. That gap is a real barrier — it means discovering what the game
 * can do requires either finding the right window or already knowing the verb.
 *
 * <p>The palette closes it. Every command in the catalogue, searchable by name <em>and by what it
 * does</em>, run by pressing Enter. No syntax to remember, no window to find. It is also how a player
 * learns the vocabulary without being taught it: the verb is right there beside the description, so
 * the twentieth time somebody palettes their way to "scan" they already know they could have typed
 * it.
 *
 * <h2>Why this is not just a menu</h2>
 *
 * {@code docs/client/00} §7 is explicit that the Unix syntax is "a vocabulary and interaction idiom,
 * not an execution surface", and that the palette "dispatches to a fixed, enumerated set of game
 * commands". So this runs through the same {@link Shell} the terminal does — same parser, same closed
 * AST, same exit statuses. It is a different way in, not a different engine, and a command that is
 * refused here is refused for the same reason and with the same number.
 *
 * <h2>Commands that need arguments</h2>
 *
 * The palette pre-fills the verb and leaves the caret after it, rather than refusing to offer
 * anything that takes an argument. A player who picks {@code mv} gets {@code mv } waiting for them
 * with the synopsis on screen — which teaches the shape of the command at the moment they need it,
 * instead of hiding it.
 */
public final class CommandPalette {

    private CommandPalette() {}

    /** Opens the palette over {@code owner}. Returns immediately; the palette is modal to its window. */
    public static void show(Window owner, Shell shell, Runnable afterRun) {
        Stage stage = new Stage(StageStyle.UTILITY);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Run a command");

        TextField search = new TextField();
        search.setPromptText("what do you want to do?");
        search.getStyleClass().add("es-mono");
        search.setAccessibleText("Search commands by name or description");

        ListView<Entry> results = new ListView<>();
        results.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(entry.display());
                // The whole entry goes to the accessible name: a screen reader user gets the verb
                // AND what it does, which is the same thing a sighted user gets from the row.
                setAccessibleText(
                        entry.command().name() + ". " + entry.command().synopsis());
            }
        });

        Label output = new Label();
        output.setWrapText(true);
        output.getStyleClass().add("es-mono");

        Label hint = new Label(Views.t("ui.command-palette.choose-enter-run-esc", "↑↓ choose · Enter run · Esc close"));
        hint.getStyleClass().add("es-text-secondary");

        VBox root = new VBox(8, search, results, output, hint);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("es-panel");
        VBox.setVgrow(results, Priority.ALWAYS);

        List<Entry> all = entries(shell);
        Runnable refilter = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            List<Entry> matched = new ArrayList<>();
            for (Entry e : all) {
                // Matching on the synopsis as well as the name is the point: a player who does not
                // know a verb exists can still find it by describing what they want.
                if (q.isEmpty()
                        || e.command().name().contains(q)
                        || e.command().synopsis().toLowerCase(Locale.ROOT).contains(q)) {
                    matched.add(e);
                }
            }
            results.getItems().setAll(matched);
            if (!matched.isEmpty()) {
                results.getSelectionModel().selectFirst();
            }
        };
        search.textProperty().addListener((o, was, now) -> refilter.run());
        refilter.run();

        Runnable runSelected = () -> {
            Entry chosen = results.getSelectionModel().getSelectedItem();
            if (chosen == null) {
                return;
            }
            String typed = search.getText() == null ? "" : search.getText().trim();
            // If the player typed something that is not just a search term, honour it verbatim —
            // the palette must never silently run a different command from the one on screen.
            String line = typed.startsWith(chosen.command().name())
                    ? typed
                    : chosen.command().name();

            Shell.Result result = shell.run(line);
            output.setText(
                    result.lines().isEmpty()
                            ? "$? = " + result.status() + " (" + ExitStatus.name(result.status()) + ")"
                            : String.join("\n", result.lines()));
            output.getStyleClass().removeAll("es-state-refused", "es-state-unreachable");
            if (result.status() == ExitStatus.UNAVAILABLE || result.status() == ExitStatus.TEMPFAIL) {
                output.getStyleClass().add("es-state-unreachable");
            } else if (result.status() != ExitStatus.OK) {
                output.getStyleClass().add("es-state-refused");
            }
            if (afterRun != null) {
                afterRun.run();
            }
            if (result.succeeded() && result.lines().isEmpty()) {
                stage.close();
            }
        };

        search.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> {
                    runSelected.run();
                    event.consume();
                }
                case ESCAPE -> {
                    stage.close();
                    event.consume();
                }
                case DOWN -> {
                    results.getSelectionModel().selectNext();
                    event.consume();
                }
                case UP -> {
                    results.getSelectionModel().selectPrevious();
                    event.consume();
                }
                case TAB -> {
                    // Fill the verb in and leave the caret after it, so a command that needs an
                    // argument teaches its own shape rather than being unavailable here.
                    Entry chosen = results.getSelectionModel().getSelectedItem();
                    if (chosen != null) {
                        search.setText(chosen.command().name() + " ");
                        search.positionCaret(search.getText().length());
                    }
                    event.consume();
                }
                default -> {}
            }
        });
        results.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                runSelected.run();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            }
        });

        Scene scene = new Scene(root, 620, 460);
        // Inherit the owner's theme so the palette is not the one surface in the client wearing a
        // different skin.
        if (owner instanceof Stage ownerStage && ownerStage.getScene() != null) {
            scene.getStylesheets().addAll(ownerStage.getScene().getStylesheets());
            scene.getRoot()
                    .getStyleClass()
                    .addAll(ownerStage.getScene().getRoot().getStyleClass());
        }
        stage.setScene(scene);
        stage.show();
        search.requestFocus();
    }

    /** One row: the command, plus the text the row shows. */
    private record Entry(Command command) {
        String display() {
            String name = command.name() + "(" + command.section() + ")";
            String pad = name.length() >= 20 ? " " : " ".repeat(20 - name.length());
            return name + pad + command.synopsis();
        }
    }

    private static List<Entry> entries(Shell shell) {
        List<Entry> out = new ArrayList<>();
        for (Command command : shell.registry().commands()) {
            out.add(new Entry(command));
        }
        out.sort((a, b) -> a.command().name().compareTo(b.command().name()));
        return out;
    }
}
