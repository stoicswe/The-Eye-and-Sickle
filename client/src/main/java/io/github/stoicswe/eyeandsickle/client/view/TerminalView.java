package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.shell.ExitStatus;
import io.github.stoicswe.eyeandsickle.client.shell.LocalCatalogue;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

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
 * <h2>⚠ It is the same surface as a machine shell, deliberately (2026-07-30)</h2>
 *
 * This was a {@code TextArea} with a prompt underneath, while a shell opened on a foreign machine
 * was a styled transcript with a right-click command menu. Two terminals that behave differently
 * teach a player that the difference matters, and it does not: the only real difference between them
 * is <em>which machine</em> the verbs act on. So this now shares the node shell's markup, its
 * scrollback trimming and its menu — {@code NodeShellView.buildMenu} is parameterised over the
 * catalogue for exactly this reason.
 *
 * <p>⚠ <b>Nothing was traded away for the restyle.</b> Tab completion, {@code Ctrl-R} reverse search,
 * the {@code $?} status line with its severity styling, and the banner that states the security
 * boundary are all things the node shell has never had and this one always did. A restyle that
 * quietly dropped them would be the regression this class exists to avoid — the node shell gained a
 * look here, it did not gain a veto.
 *
 * <p>⚠ The catalogue is <b>generated from the registry</b> ({@link LocalCatalogue}), not written
 * beside it. A hand-kept list of local commands would be free to offer a verb the shell no longer
 * has, and the whole reason the node shell's menu is trustworthy is that its list and its parser are
 * one list.
 *
 * <h2>The keys are real readline keys, and {@code $?} is always shown</h2>
 *
 * {@code Up}/{@code Down} walk history and {@code Ctrl-R} searches it, because both are what the
 * underlying editing library gives every real shell. A player who learns {@code Ctrl-R} here can use
 * it tonight in {@code bash}, {@code psql} and {@code python} — the cheapest transferable skill in
 * the client. The status line carries the last exit status by number and name: §3.5 makes {@code 1}
 * (refused) and {@code 69} (unreachable) different numbers precisely so they cannot collapse into
 * one message, and showing the number is what makes that distinction visible rather than merely
 * implemented.
 */
public final class TerminalView {

    private TerminalView() {}

    /** How many lines of scrollback the terminal keeps. Beyond this the window is a memory leak. */
    private static final int SCROLLBACK = 500;

    public static Region create(Shell shell) {
        VBox root = new VBox(UiTokens.SPACE_3);
        // ⚠ The node shell's classes, not a parallel set. A second stylesheet block for "the same
        // thing but local" is how the two surfaces drift apart again.
        root.getStyleClass().addAll("es-nodeshell", "es-body-pad");

        Label strip = new Label();
        strip.getStyleClass().add("es-nodeshell-strip");

        VBox output = new VBox(UiTokens.SPACE_1);
        output.getStyleClass().add("es-nodeshell-output");
        ScrollPane scroll = new ScrollPane(output);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label prompt = new Label();
        prompt.getStyleClass().add("es-nodeshell-prompt");
        TextField input = new TextField();
        input.getStyleClass().add("es-nodeshell-input");
        input.setPromptText("help");
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox line = new HBox(UiTokens.SPACE_2, prompt, input);
        line.setAlignment(Pos.CENTER_LEFT);

        Label hint = Ui.small(Views.t(
                "ui.terminal.right-click-for-a",
                "Right-click for a command menu that fills in the options. "
                        + "Up recalls, Tab completes, Ctrl-R searches. Pipe with `|` — `ps | grep miner`."));

        root.getChildren().addAll(strip, scroll, line, hint);

        java.util.function.BiConsumer<String, String> print = (text, styleClass) -> {
            Label row = new Label(text);
            row.getStyleClass().add(styleClass);
            row.setWrapText(false);
            output.getChildren().add(row);
            // Trim from the front, as the node shell does: the oldest line is the one nobody is
            // reading, and a transcript that grows without bound makes the whole deck stutter.
            while (output.getChildren().size() > SCROLLBACK) {
                output.getChildren().removeFirst();
            }
            scroll.setVvalue(1.0d);
        };

        String base = shell.session().handle() + "@rig:~$";
        prompt.setText(base);

        Runnable repaint = () -> {
            int status = shell.lastStatus();
            strip.setText(Ui.upper(shell.session().handle() + "  ·  this rig  ·  $? = " + status + " ("
                    + ExitStatus.name(status) + ")"));
            // ⚠ The severity styling survives the restyle. §3.5 keeps "refused" and "unreachable"
            // as different numbers so they cannot collapse into one message; the strip carries the
            // number, and the colour is what makes the difference visible at a glance.
            strip.getStyleClass().removeAll("es-state-refused", "es-state-unreachable");
            if (status == ExitStatus.UNAVAILABLE || status == ExitStatus.TEMPFAIL) {
                strip.getStyleClass().add("es-state-unreachable");
            } else if (status != ExitStatus.OK) {
                strip.getStyleClass().add("es-state-refused");
            }
        };

        List<String> history = new ArrayList<>(shell.history());
        int[] cursor = {history.size()};
        boolean[] searching = {false};
        StringBuilder searchTerm = new StringBuilder();

        java.util.function.Consumer<String> submit = text -> {
            String typed = text == null ? "" : text.trim();
            if (typed.isEmpty()) {
                return;
            }
            print.accept(base + " " + typed, "es-nodeshell-echo");
            Shell.Result result = shell.run(typed);
            for (String out : result.lines()) {
                // ⚠ A failing command's output is marked as such. The strip carries the number, but a
                // player scrolling back through a transcript is reading the lines, not the strip —
                // and a refusal that looks like ordinary output is a refusal they miss.
                print.accept(out, result.succeeded() ? "es-nodeshell-line" : "es-nodeshell-error");
            }
            history.add(typed);
            cursor[0] = history.size();
            searching[0] = false;
            searchTerm.setLength(0);
            prompt.setText(base);
            repaint.run();
        };

        input.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> {
                    submit.accept(input.getText());
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
                        print.accept(
                                String.join("  ", candidates.subList(0, Math.min(12, candidates.size()))),
                                "es-nodeshell-line");
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
                    if (searching[0]
                            && event.getText() != null
                            && !event.getText().isEmpty()) {
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

        // ⚠ The address is blank — the player's own rig. Every builder path that would reach for a
        // machine's filesystem is keyed on it, and the local catalogue declares no path arguments,
        // so nothing asks; passing a fake address would make argument suggestions list somebody
        // else's files.
        ContextMenu menu =
                NodeShellView.buildMenu(shell.session(), "", input, LocalCatalogue.byGroup(shell.registry()));
        // Installed on all three, because a player reaching for a right-click aims at whichever of
        // them they were last looking at.
        output.setOnContextMenuRequested(event -> {
            menu.show(output, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        scroll.setOnContextMenuRequested(event -> {
            menu.show(scroll, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        input.setOnContextMenuRequested(event -> {
            menu.show(input, Side.BOTTOM, 0, 0);
            event.consume();
        });

        repaint.run();
        // The banner is the security boundary stated in the window rather than only in a doc. It is
        // the first thing in the transcript and it survives the restyle for that reason.
        for (String bannerLine : banner(shell)) {
            print.accept(bannerLine, "es-nodeshell-line");
        }
        return root;
    }

    private static List<String> banner(Shell shell) {
        return List.of(
                "The Eye and Sickle — terminal",
                "",
                "This is a game surface, not a shell. It cannot run a program on your",
                "computer, read a file on your disk, or see a process you are running.",
                "It parses what you type into a fixed set of game commands, and that is",
                "all it can do.",
                "",
                "The command names are real ones. `ps`, `ss`, `df`, `ls`, `grep` and",
                "`man` do here what they do on a real Unix machine, on a smaller board.",
                "",
                "  help          what you can run",
                "  ps            what is holding your rig",
                "  ps | grep m   pipelines work, and only for reading",
                "  man <term>    the manual, offline, on this machine",
                "",
                "  mode          " + shell.session().mode().label() + " — "
                        + shell.session().mode().explanation());
    }
}
