package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.shell.NodeCommands;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.RemoteSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A shell on one machine.
 *
 * <h2>⚠ One window per session, and that is not a violation of WL-8</h2>
 *
 * {@code docs/client/05} §3.7 rules out a second window for the same tool, and its reason is
 * specific: <em>"two windows showing the same tool would each be a live view of the same session
 * state, and the player would have no way to tell which one they were reading."</em> Two shells on
 * two different machines are not two views of one state — they are two states, exactly as two
 * terminal windows on two servers are. The desk keys windows by an arbitrary string, so each session
 * takes {@code shell:<address>}, and the ambiguity WL-8 exists to prevent cannot arise: the window's
 * title bar is the machine.
 *
 * <h2>The right-click menu is built from the command catalogue, not written beside it</h2>
 *
 * {@link NodeCommands#byGroup()} supplies the menu and {@link NodeCommands#run} parses what it
 * produces. A flag that exists is offerable and a flag that is offered runs, because there is one
 * list. The version of this feature where the menu is hand-written is the version where a flag gets
 * renamed and the menu keeps inserting the old one for a year.
 *
 * <h2>Why a builder rather than just inserting the command</h2>
 *
 * Inserting {@code ls} and leaving the player to remember {@code -l} teaches nothing and saves
 * nothing. The builder shows every option the command has <em>with what it does</em>, which makes
 * the menu a place you find out that {@code -h} exists — and then the line it writes into the input
 * is a line the player could have typed, so the next time they type it. A menu that ran the command
 * directly would be a button; this is a way of learning the keyboard.
 */
public final class NodeShellView {

    private NodeShellView() {}

    /** How many lines of scrollback one session keeps. Beyond this the window is a memory leak. */
    private static final int SCROLLBACK = 500;

    /**
     * Builds a shell bound to one machine.
     *
     * @param address the machine, or blank for the player's own rig
     * @param onClosed run when the session ends from inside — {@code exit}, or a lost foothold — so
     *     the desk can take the window away rather than leaving a shell attached to nothing
     */
    public static Region create(GameSession session, String address, Runnable onClosed) {
        VBox root = new VBox(UiTokens.SPACE_3);
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
        input.setPromptText("ls -l");
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox line = new HBox(UiTokens.SPACE_2, prompt, input);
        line.setAlignment(Pos.CENTER_LEFT);

        Label hint = Ui.small("Right-click for a command menu that fills in the options. Up recalls.");

        root.getChildren().addAll(strip, scroll, line, hint);

        List<String> history = new ArrayList<>();
        int[] historyAt = {0};

        Runnable[] repaint = new Runnable[1];

        // ---------------------------------------------------------------- printing
        java.util.function.BiConsumer<String, String> print = (text, styleClass) -> {
            Label row = new Label(text);
            row.getStyleClass().add(styleClass);
            row.setWrapText(false);
            output.getChildren().add(row);
            // Trim from the front. A shell that grows without bound is a window that eventually
            // makes the whole deck stutter, and the oldest line is the one nobody is reading.
            while (output.getChildren().size() > SCROLLBACK) {
                output.getChildren().removeFirst();
            }
            scroll.setVvalue(1.0d);
        };

        // ---------------------------------------------------------------- running
        java.util.function.Consumer<String> submit = text -> {
            String typed = text == null ? "" : text.trim();
            if (typed.isEmpty()) {
                return;
            }
            history.add(typed);
            historyAt[0] = history.size();
            print.accept(promptText(session, address) + " " + typed, "es-nodeshell-echo");

            // `cd` is the one command that changes the SESSION rather than reading it, so it goes
            // through the port instead of through NodeCommands. Doing it in the command table would
            // have put a mutation of persisted state inside a formatter.
            if (typed.equals("cd") || typed.startsWith("cd ")) {
                String path = typed.length() > 2 ? typed.substring(3).trim() : "~";
                GameSession.Outcome moved = session.changeDirectory(
                        address, path.equals("~") ? homeOf(session, address) : path);
                if (moved.succeeded()) {
                    // `cd` is a deliberate move, so it belongs in Recents — the same rule the file
                    // manager's navigation follows.
                    session.noteAccess(address, cwdOf(session, address));
                } else {
                    print.accept(moved.message(), "es-nodeshell-error");
                }
                repaint[0].run();
                return;
            }

            NodeCommands.Result result = NodeCommands.run(session, address, cwdOf(session, address), typed);
            for (String out : result.lines()) {
                print.accept(out, "es-nodeshell-line");
            }
            if (result.closeSession()) {
                session.closeSession(address);
                if (onClosed != null) {
                    onClosed.run();
                }
                return;
            }
            repaint[0].run();
        };

        input.setOnAction(event -> {
            submit.accept(input.getText());
            input.clear();
        });
        input.setOnKeyPressed(event -> {
            // Up and Down walk the history, which is the first thing anybody tries in a terminal and
            // the first thing they are annoyed to find missing.
            if (event.getCode() == KeyCode.UP && historyAt[0] > 0) {
                historyAt[0]--;
                input.setText(history.get(historyAt[0]));
                input.positionCaret(input.getText().length());
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                historyAt[0] = Math.min(history.size(), historyAt[0] + 1);
                input.setText(historyAt[0] >= history.size() ? "" : history.get(historyAt[0]));
                input.positionCaret(input.getText().length());
                event.consume();
            }
        });

        // ---------------------------------------------------------------- the command menu
        ContextMenu menu = buildMenu(session, address, input);
        // Installed on the output area AND the input, because a player reaching for a right-click
        // aims at whichever of the two they were last looking at.
        output.setOnContextMenuRequested(event -> {
            menu.show(output, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        input.setOnContextMenuRequested(event -> {
            menu.show(input, Side.BOTTOM, 0, 0);
            event.consume();
        });
        scroll.setOnContextMenuRequested(event -> {
            menu.show(scroll, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        repaint[0] = () -> {
            Optional<RemoteSession> live = session.sessions().stream()
                    .filter(s -> s.address().equals(address))
                    .findFirst();
            if (live.isEmpty()) {
                // The rules dropped it — a lost foothold, most likely. Say so in the window rather
                // than leaving a shell that silently refuses everything.
                strip.setText(Ui.upper(address + " — session ended"));
                input.setDisable(true);
                return;
            }
            RemoteSession current = live.get();
            strip.setText(Ui.upper(current.displayName() + "  ·  " + current.address()
                    + "  ·  " + current.cwd() + "  ·  " + current.cycles() + "C HELD"));
            prompt.setText(promptText(session, address));
        };

        repaint[0].run();
        print.accept("Connected to " + address + ". `help` lists what runs here.", "es-nodeshell-line");

        AutoCloseable subscription = session.onChange(s -> repaint[0].run());
        closeOnDetach(root, subscription);
        return root;
    }

    // ------------------------------------------------------------------ the menu and the builder

    /**
     * The right-click menu: every command, grouped, each opening a builder for its options.
     *
     * <p>Commands with no options at all skip the builder and insert straight away — a dialog with
     * nothing in it but an OK button is a dialog that trains people to dismiss dialogs.
     */
    private static ContextMenu buildMenu(GameSession session, String address, TextField input) {
        ContextMenu menu = new ContextMenu();
        NodeCommands.byGroup().forEach((group, commands) -> {
            Menu submenu = new Menu(group);
            for (NodeCommands.NodeCommand command : commands) {
                MenuItem item = new MenuItem(command.name() + "  —  " + command.synopsis());
                item.setOnAction(event -> {
                    if (command.options().isEmpty() && command.arguments().isEmpty()) {
                        insert(input, command.name());
                        return;
                    }
                    showBuilder(session, address, command, input);
                });
                submenu.getItems().add(item);
            }
            menu.getItems().add(submenu);
        });
        return menu;
    }

    /**
     * The parameter builder: one control per option, one per argument, and a preview of the line.
     *
     * <h2>The preview is the teaching, and it updates as you click</h2>
     *
     * A builder that only produced a result would be a wizard. Showing the command assembling itself
     * — {@code ls} becoming {@code ls -l -h /var/log} as the boxes are ticked — is what connects the
     * menu to the keyboard, which is the entire justification for the feature over a row of buttons.
     * It writes into the input rather than running, so the last act is always the player's.
     */
    private static void showBuilder(
            GameSession session,
            String address,
            NodeCommands.NodeCommand command,
            TextField input) {

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        VBox panel = new VBox(UiTokens.SPACE_3);
        panel.getStyleClass().addAll("es-nodeshell", "es-body-pad", "es-nodeshell-builder");
        panel.setMinWidth(420);

        Label title = Ui.label(command.name());
        Label synopsis = Ui.small(command.synopsis());
        synopsis.setWrapText(true);

        Map<NodeCommands.CommandOption, javafx.scene.Node> controls = new LinkedHashMap<>();
        Map<NodeCommands.CommandArgument, ComboBox<String>> argumentFields = new LinkedHashMap<>();

        Label preview = new Label();
        preview.getStyleClass().add("es-nodeshell-preview");
        preview.setWrapText(true);

        Runnable refresh = () -> preview.setText(assemble(command, controls, argumentFields));

        VBox optionRows = new VBox(UiTokens.SPACE_2);
        for (NodeCommands.CommandOption option : command.options()) {
            HBox row = new HBox(UiTokens.SPACE_3);
            row.setAlignment(Pos.CENTER_LEFT);
            switch (option.kind()) {
                case FLAG -> {
                    CheckBox box = new CheckBox(option.name());
                    box.selectedProperty().addListener((o, was, now) -> refresh.run());
                    controls.put(option, box);
                    row.getChildren().add(box);
                }
                case CHOICE -> {
                    ComboBox<String> combo = new ComboBox<>();
                    // A blank first entry, so a choice option can be left OFF. Without it, opening
                    // the builder would silently add a flag the player never asked for.
                    combo.getItems().add("");
                    combo.getItems().addAll(option.choices());
                    combo.setValue("");
                    combo.valueProperty().addListener((o, was, now) -> refresh.run());
                    controls.put(option, combo);
                    row.getChildren().addAll(new Label(option.name()), combo);
                }
                case VALUE -> {
                    ComboBox<String> field = new ComboBox<>();
                    field.setEditable(true);
                    field.valueProperty().addListener((o, was, now) -> refresh.run());
                    controls.put(option, field);
                    row.getChildren().addAll(new Label(option.name()), field);
                }
            }
            Label help = Ui.small(option.help());
            help.setWrapText(true);
            row.getChildren().add(help);
            optionRows.getChildren().add(row);
        }

        for (NodeCommands.CommandArgument argument : command.arguments()) {
            ComboBox<String> field = new ComboBox<>();
            field.setEditable(true);
            field.setPrefWidth(200);
            if (argument.suggestPaths()) {
                // ⚠ Offered from the RULES' listing of the current directory, never from anything
                // this window remembers. A completion list is a disclosure: suggesting a path on a
                // machine the player has not broken into would be a free recon result.
                for (FsEntry entry : session.list(address, cwdOf(session, address))) {
                    field.getItems().add(entry.name());
                }
            }
            field.valueProperty().addListener((o, was, now) -> refresh.run());
            argumentFields.put(argument, field);
            HBox row = new HBox(UiTokens.SPACE_3);
            row.setAlignment(Pos.CENTER_LEFT);
            Label help = Ui.small(argument.help());
            help.setWrapText(true);
            row.getChildren().addAll(
                    new Label(argument.name() + (argument.required() ? " *" : "")), field, help);
            optionRows.getChildren().add(row);
        }

        BreachView.Chip insert = new BreachView.Chip("Insert", "es-nodeshell-action");
        BreachView.Chip cancel = new BreachView.Chip("Cancel", "es-nodeshell-action");
        insert.onInvoke(() -> {
            insert(input, assemble(command, controls, argumentFields));
            popup.hide();
        });
        cancel.onInvoke(popup::hide);

        refresh.run();
        panel.getChildren().addAll(
                title, synopsis, optionRows,
                Ui.label("Command"), preview,
                Ui.row(UiTokens.SPACE_3, insert, cancel));
        popup.getContent().add(panel);
        if (input.getScene() != null && input.getScene().getWindow() != null) {
            var bounds = input.localToScreen(input.getBoundsInLocal());
            popup.show(input, bounds.getMinX(), bounds.getMinY() - 40);
        }
    }

    /** The line the current control state means. One function, so the preview cannot lie. */
    private static String assemble(
            NodeCommands.NodeCommand command,
            Map<NodeCommands.CommandOption, javafx.scene.Node> controls,
            Map<NodeCommands.CommandArgument, ComboBox<String>> arguments) {
        StringBuilder out = new StringBuilder(command.name());
        controls.forEach((option, control) -> {
            switch (option.kind()) {
                case FLAG -> {
                    if (control instanceof CheckBox box && box.isSelected()) {
                        out.append(' ').append(option.name());
                    }
                }
                case CHOICE, VALUE -> {
                    if (control instanceof ComboBox<?> combo && combo.getValue() != null
                            && !String.valueOf(combo.getValue()).isBlank()) {
                        out.append(' ').append(option.name()).append('=').append(combo.getValue());
                    }
                }
            }
        });
        arguments.forEach((argument, field) -> {
            String value = field.getValue() == null ? "" : field.getValue().trim();
            if (!value.isBlank()) {
                // Quoted when it has to be, using the shell's own rule. A builder that emitted an
                // unquoted path with a space in it would produce a line the parser splits, which is
                // the one failure a player would blame on the game rather than on themselves.
                out.append(' ').append(value.contains(" ") ? "\"" + value + "\"" : value);
            }
        });
        return out.toString();
    }

    private static void insert(TextField input, String text) {
        input.setText(text);
        input.requestFocus();
        input.positionCaret(input.getText().length());
    }

    // ------------------------------------------------------------------ helpers

    private static String cwdOf(GameSession session, String address) {
        return session.sessions().stream()
                .filter(s -> s.address().equals(address))
                .map(RemoteSession::cwd)
                .findFirst()
                .orElse("/");
    }

    private static String homeOf(GameSession session, String address) {
        // `cd` with no argument goes home, which is what a real shell does. The home is derived from
        // where the rules put the session, so this cannot name a directory that is not there.
        String cwd = cwdOf(session, address);
        String users = io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.USERS + "/";
        return cwd.startsWith(users)
                ? users + cwd.substring(users.length()).split("/")[0]
                : "/";
    }

    /** {@code user@host:/path$} — the same shape and the same order as the deck's own prompt. */
    private static String promptText(GameSession session, String address) {
        String cwd = cwdOf(session, address);
        String users = io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs.USERS + "/";
        String user = cwd.startsWith(users) ? cwd.substring(users.length()).split("/")[0] : "root";
        return user + "@" + (address.isBlank() ? "rig" : address) + ":" + cwd + "$";
    }

    /** Releases the change subscription when the window leaves the desk. Same shape as the map's. */
    private static void closeOnDetach(Region root, AutoCloseable subscription) {
        boolean[] attached = {false};
        root.sceneProperty().addListener((observable, was, now) -> {
            if (now != null) {
                attached[0] = true;
                return;
            }
            if (!attached[0]) {
                return;
            }
            attached[0] = false;
            try {
                subscription.close();
            } catch (Exception ignored) {
                // Nothing this panel can do, and throwing out of a scene listener would take the
                // whole close with it.
            }
        });
    }
}
