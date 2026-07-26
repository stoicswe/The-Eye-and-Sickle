package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.shell.ExitStatus;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The terminal — a game surface that looks exactly like a shell and is not one.
 *
 * <h2>What this cannot do, by construction</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.1 states it as a security boundary rather
 * than a scope decision: the client never executes a host command, reads a host path, or touches a
 * host process. Everything typed here goes to {@link Shell}, which parses into a closed AST over an
 * enumerated registry. There is no fallthrough — an unrecognised verb is exit {@code 127} — and
 * there is no escape hatch added later "just for debugging".
 *
 * <h2>The keys are real readline keys</h2>
 *
 * {@code Up}/{@code Down} walk history and {@code Ctrl-R} searches it, because both are what the
 * underlying editing library gives every real shell. A player who learns {@code Ctrl-R} here can use
 * it tonight in {@code bash}, {@code psql} and {@code python} — which is the cheapest transferable
 * skill in the whole client.
 *
 * <h2>{@code $?} is shown, always</h2>
 *
 * The status line carries the last exit status by number and name. §3.5 makes {@code 1} (refused) and
 * {@code 69} (unreachable) different numbers precisely so they cannot collapse into one message, and
 * showing the number is what makes that distinction visible rather than merely implemented.
 */
public final class TerminalView {

    private TerminalView() {}

    public static Region create(Shell shell) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("es-panel");

        TextArea transcript = new TextArea();
        transcript.setEditable(false);
        transcript.setWrapText(false);
        transcript.getStyleClass().add("es-terminal");
        transcript.setText(banner(shell));

        TextField input = new TextField();
        input.getStyleClass().add("es-mono");
        input.setPromptText("type a command — try `help`, `ps`, or `ps | grep miner`");

        Label prompt = new Label("$");
        prompt.getStyleClass().add("es-mono");

        HBox inputRow = new HBox(8, prompt, input);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(8, 0, 4, 0));
        HBox.setHgrow(input, Priority.ALWAYS);

        Label status = new Label("$? = 0 (ok)");
        status.getStyleClass().addAll("es-mono", "es-text-secondary");
        HBox statusBar = new HBox(status);
        statusBar.getStyleClass().add("es-strip");

        root.setCenter(transcript);
        root.setBottom(new javafx.scene.layout.VBox(inputRow, statusBar));

        // History navigation, and the reverse search that most people never discover.
        List<String> history = new ArrayList<>(shell.history());
        int[] cursor = {history.size()};
        boolean[] searching = {false};
        StringBuilder searchTerm = new StringBuilder();

        input.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> {
                    String line = input.getText();
                    if (line.isBlank()) {
                        return;
                    }
                    Shell.Result result = shell.run(line);
                    StringBuilder sb = new StringBuilder(transcript.getText());
                    sb.append("\n$ ").append(line).append('\n');
                    for (String out : result.lines()) {
                        sb.append(out).append('\n');
                    }
                    transcript.setText(sb.toString());
                    transcript.positionCaret(transcript.getText().length());

                    status.setText("$? = " + result.status() + " (" + ExitStatus.name(result.status()) + ")");
                    status.getStyleClass().removeAll("es-state-refused", "es-state-unreachable");
                    if (result.status() == ExitStatus.UNAVAILABLE || result.status() == ExitStatus.TEMPFAIL) {
                        status.getStyleClass().add("es-state-unreachable");
                    } else if (result.status() != ExitStatus.OK) {
                        status.getStyleClass().add("es-state-refused");
                    }

                    history.add(line);
                    cursor[0] = history.size();
                    searching[0] = false;
                    searchTerm.setLength(0);
                    input.clear();
                    event.consume();
                }
                case UP -> {
                    if (cursor[0] > 0) {
                        cursor[0]--;
                        input.setText(history.get(cursor[0]));
                        input.positionCaret(input.getText().length());
                    }
                    event.consume();
                }
                case DOWN -> {
                    if (cursor[0] < history.size() - 1) {
                        cursor[0]++;
                        input.setText(history.get(cursor[0]));
                    } else {
                        cursor[0] = history.size();
                        input.clear();
                    }
                    event.consume();
                }
                case TAB -> {
                    // Completion never executes. It also never reveals a node the player has not
                    // discovered — see Namespace.
                    List<String> candidates = shell.complete(input.getText());
                    if (candidates.size() == 1) {
                        String only = candidates.getFirst();
                        String text = input.getText();
                        int lastSpace = text.lastIndexOf(' ');
                        input.setText(lastSpace < 0 ? only : text.substring(0, lastSpace + 1) + only);
                        input.positionCaret(input.getText().length());
                    } else if (!candidates.isEmpty()) {
                        transcript.setText(transcript.getText() + "\n"
                                + String.join("  ", candidates.subList(0, Math.min(12, candidates.size()))) + "\n");
                        transcript.positionCaret(transcript.getText().length());
                    }
                    event.consume();
                }
                case R -> {
                    if (event.isControlDown()) {
                        searching[0] = true;
                        prompt.setText("(reverse-i-search)");
                        event.consume();
                    }
                }
                default -> {
                    if (searching[0] && event.getText() != null && !event.getText().isEmpty()) {
                        searchTerm.append(event.getText());
                        for (int i = history.size() - 1; i >= 0; i--) {
                            if (history.get(i).contains(searchTerm.toString())) {
                                input.setText(history.get(i));
                                break;
                            }
                        }
                    }
                }
            }
        });

        return root;
    }

    private static String banner(Shell shell) {
        return """
               The Eye and Sickle — terminal

               This is a game surface, not a shell. It cannot run a program on your
               computer, read a file on your disk, or see a process you are running.
               It parses what you type into a fixed set of game commands, and that is
               all it can do.

               The command names are real ones. `ps`, `ss`, `df`, `ls`, `grep` and
               `man` do here what they do on a real Unix machine, on a smaller board.

                 help          what you can run
                 ps            what is holding your rig
                 ps | grep m   pipelines work, and only for reading
                 man <term>    the manual, offline, on this machine
               """
                + "\n mode          " + shell.session().mode().label() + " — "
                + shell.session().mode().explanation() + "\n";
    }
}
