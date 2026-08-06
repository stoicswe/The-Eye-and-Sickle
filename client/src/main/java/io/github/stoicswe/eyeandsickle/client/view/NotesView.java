package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.markdown.MarkdownSpans;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * NOTES — a markdown notebook for lore, addresses and whatever else is worth writing down.
 *
 * <h2>The shape</h2>
 *
 * An explorer tree on the left and the note on the right, which is the arrangement of every editor a
 * player already knows. Folders nest; a note is markdown and nothing else.
 *
 * <h2>⚠ THE HIGHLIGHT IS AN OVERLAY, AND IT ALIGNS ONLY BECAUSE EVERYTHING IS MONOSPACE</h2>
 *
 * JavaFX has no rich-text control — {@code TextArea} is one font, one colour, and there is no third
 * option in the toolkit. So the editor is a {@code TextArea} whose own glyphs are drawn
 * <b>transparent</b>, with a {@code TextFlow} of coloured runs laid exactly over it. Both hold the
 * same characters in the same monospace face at the same size, so run <em>n</em> of the overlay sits
 * on character <em>n</em> of the source. That is the same guarantee {@code CoreCage} and
 * {@code AsciiCanvas} already lean on.
 *
 * <p>⚠ <b>A run may change colour and weight. It may never change SIZE.</b> Rendering a heading
 * larger in the editor shifts every character after it, and the caret stops landing where the
 * pointer is — silently, and only on lines that contain markup. Bigger headings are the READING
 * view's business, where nothing is overlaid on anything.
 *
 * <p>⚠ <b>The {@code TextArea} must not scroll itself.</b> It owns its viewport and there is no way
 * to read or drive its scroll offset, so an overlay inside a scrolling {@code TextArea} drifts apart
 * from the text the moment the player scrolls. Instead the area is grown to its full content height
 * and the whole stack goes in one {@code ScrollPane} — one viewport, one offset, nothing to keep in
 * step.
 *
 * <p>⚠ <b>The caret is coloured explicitly.</b> {@code -fx-text-fill: transparent} takes the caret
 * with it in Modena's skin, which leaves a player typing into what looks like a dead panel. The
 * stylesheet sets {@code -fx-highlight-text-fill} and a caret colour separately for this reason.
 */
public final class NotesView {

    private NotesView() {}

    /** How often an edited note is written back. See {@code writeNote} on why this is cheap. */
    private static final int AUTOSAVE_MS = 1200;

    /** The fewest rows the editor shows, so a one-line note is still a page to write on. */
    private static final int MIN_ROWS = 24;

    public static Region create(GameSession session) {
        String[] selected = {""};
        Set<String> expanded = new HashSet<>();
        Runnable[] repaintTree = new Runnable[1];
        Runnable[] openNote = new Runnable[1];

        VBox tree = new VBox(1);
        VBox editorHolder = new VBox();
        VBox.setVgrow(editorHolder, Priority.ALWAYS);

        // ── the explorer ─────────────────────────────────────────────────────────────────────
        Label heading = new Label(Views.t("ui.notes.explorer", "EXPLORER"));
        heading.getStyleClass().add("es-notes-heading");

        Button newNote = new Button(Views.t("ui.notes.new-note", "+ Note"));
        Button newFolder = new Button(Views.t("ui.notes.new-folder", "+ Folder"));
        for (Button b : List.of(newNote, newFolder)) {
            b.getStyleClass().add("es-notes-toolbutton");
        }
        HBox toolbar = new HBox(UiTokens.SPACE_2, newNote, newFolder);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label result = new Label();
        result.setWrapText(true);

        // ⚠ New entries land inside the SELECTED FOLDER, or beside the selected note — which is what
        // every file manager does and what the player means by "here". Falling back to the root
        // would make a folder something you can create in and never create into.
        java.util.function.Supplier<String> destination = () -> {
            GameSession.Note at = find(session, selected[0]);
            if (at == null) {
                return "";
            }
            return at.folder() ? at.noteId() : at.parentId();
        };

        newNote.setOnAction(e -> {
            GameSession.Outcome made = session.createNote(destination.get(), "untitled", false);
            Views.styleByOutcome(result, made);
            if (made.succeeded()) {
                // ⚠ createNote returns the new id in its message, so the tree can select what was
                // just made. Anything else means hunting for it in a list that just reordered.
                selected[0] = made.message();
                result.setText("");
            }
            repaintTree[0].run();
            openNote[0].run();
        });
        newFolder.setOnAction(e -> {
            GameSession.Outcome made = session.createNote(destination.get(), "new folder", true);
            Views.styleByOutcome(result, made);
            if (made.succeeded()) {
                expanded.add(made.message());
                result.setText("");
            }
            repaintTree[0].run();
        });

        repaintTree[0] = () -> {
            tree.getChildren().clear();
            List<GameSession.Note> all = session.notes();
            if (all.isEmpty()) {
                Label empty = Views.secondary(Views.t(
                        "ui.notes.empty", "No notes yet. Everything you write here stays with this character."));
                empty.setWrapText(true);
                tree.getChildren().add(empty);
                return;
            }
            addRows(session, tree, all, "", 0, selected, expanded, repaintTree, openNote, result);
        };

        openNote[0] = () -> {
            GameSession.Note note = find(session, selected[0]);
            if (note == null || note.folder()) {
                editorHolder.getChildren().setAll(Views.secondary(Views.t(
                        "ui.notes.no-selection", "Select a note, or make one.")));
                return;
            }
            editorHolder.getChildren().setAll(editor(session, note, repaintTree, result));
        };

        // ⚠ Opens on a note rather than on "select something". A notebook whose first frame is an
        // instruction is one more click before the thing the window is for, and every editor a
        // player has used opens on a file. The FIRST non-folder in tree order, so it is the one at
        // the top of the list they are looking at rather than whichever the save happened to store
        // first.
        session.notes().stream()
                .filter(n -> !n.folder())
                .sorted(java.util.Comparator.comparing(n -> n.name().toLowerCase(java.util.Locale.ROOT)))
                .findFirst()
                .ifPresent(n -> selected[0] = n.noteId());

        repaintTree[0].run();
        openNote[0].run();

        VBox side = new VBox(UiTokens.SPACE_2, heading, toolbar, Views.scrollable(tree));
        side.getStyleClass().add("es-notes-side");
        VBox.setVgrow(side.getChildren().get(2), Priority.ALWAYS);
        // ⚠ The width goes on the SIDE column itself. Setting it on the inner tree does nothing —
        // an HBox distributes to its own children and a ScrollPane's minimum is unrelated to what it
        // contains. CommsView records the same trap and the crushed column it produced.
        side.setMinWidth(170);
        side.setPrefWidth(200);
        side.setMaxWidth(240);

        VBox right = new VBox(UiTokens.SPACE_2, editorHolder, result);
        right.getStyleClass().add("es-body-pad");
        right.setMinWidth(0);
        VBox.setVgrow(editorHolder, Priority.ALWAYS);

        HBox split = new HBox(side, right);
        HBox.setHgrow(right, Priority.ALWAYS);
        split.setFillHeight(true);

        VBox root = new VBox(split);
        root.getStyleClass().add("es-notes");
        VBox.setVgrow(split, Priority.ALWAYS);

        AutoCloseable onSession = session.onChange(s -> repaintTree[0].run());
        Views.releaseOnDetach(root, onSession);
        return root;
    }

    // ── the tree ─────────────────────────────────────────────────────────────────────────────

    private static void addRows(
            GameSession session,
            VBox into,
            List<GameSession.Note> all,
            String parentId,
            int depth,
            String[] selected,
            Set<String> expanded,
            Runnable[] repaintTree,
            Runnable[] openNote,
            Label result) {
        // ⚠ Folders first, then A–Z — the convention every file manager uses, and the same order the
        // rules sort in. Sorting by edit time instead would move the row being worked in.
        List<GameSession.Note> children = all.stream()
                .filter(n -> parentId.equals(n.parentId()))
                .sorted(java.util.Comparator.comparing((GameSession.Note n) -> !n.folder())
                        .thenComparing(n -> n.name().toLowerCase(java.util.Locale.ROOT)))
                .toList();

        for (GameSession.Note note : children) {
            into.getChildren()
                    .add(row(session, note, depth, selected, expanded, repaintTree, openNote, result));
            if (note.folder() && expanded.contains(note.noteId())) {
                addRows(
                        session,
                        into,
                        all,
                        note.noteId(),
                        depth + 1,
                        selected,
                        expanded,
                        repaintTree,
                        openNote,
                        result);
            }
        }
    }

    private static Region row(
            GameSession session,
            GameSession.Note note,
            int depth,
            String[] selected,
            Set<String> expanded,
            Runnable[] repaintTree,
            Runnable[] openNote,
            Label result) {
        // ⚠ The disclosure arrow and the kind marker are TEXT from the bundled face, never an icon
        // set: §9 bans icon sets and GlyphCoverageTest fails the build on a codepoint neither
        // bundled font carries. `>` and `v` are ASCII, which is the one range that is certainly safe.
        String twist = note.folder() ? (expanded.contains(note.noteId()) ? "v " : "> ") : "  ";
        Label label = new Label(twist + note.name());
        label.getStyleClass().add(note.folder() ? "es-notes-folder" : "es-notes-note");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPadding(new javafx.geometry.Insets(3, 6, 3, 6 + depth * 12));

        HBox box = new HBox(label);
        HBox.setHgrow(label, Priority.ALWAYS);
        box.getStyleClass().add("es-notes-row");
        if (note.noteId().equals(selected[0])) {
            box.getStyleClass().add("es-notes-row-on");
        }
        box.setOnMouseClicked(e -> {
            selected[0] = note.noteId();
            if (note.folder()) {
                if (!expanded.remove(note.noteId())) {
                    expanded.add(note.noteId());
                }
            }
            repaintTree[0].run();
            openNote[0].run();
        });

        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem rename = new javafx.scene.control.MenuItem(Views.t("ui.notes.rename", "Rename"));
        rename.setOnAction(e -> {
            selected[0] = note.noteId();
            repaintTree[0].run();
            renameInPlace(box, session, note, repaintTree, openNote);
        });
        javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem(Views.t("ui.notes.delete", "Delete"));
        delete.setOnAction(e -> {
            // ⚠ A folder delete takes everything inside it, so it ASKS. The rules are recursive by
            // necessity — an orphaned note is invisible in the tree, still in the save, and still
            // counting against the limit — which makes the confirmation the only thing standing
            // between a mis-click and losing a subtree.
            if (note.folder() && !confirm(box, note.name())) {
                return;
            }
            GameSession.Outcome out = session.deleteNote(note.noteId());
            Views.styleByOutcome(result, out);
            result.setText(out.message());
            if (note.noteId().equals(selected[0])) {
                selected[0] = "";
            }
            repaintTree[0].run();
            openNote[0].run();
        });
        menu.getItems().addAll(rename, delete);
        // ⚠ Anchored to the WINDOW, never to the row. The handler repaints the tree first, which
        // detaches the node the pointer was over — and JavaFX throws "the owner node needs to be
        // associated with a window" on every right-click. NetMapView records the same failure.
        box.setOnContextMenuRequested(e -> {
            javafx.stage.Window window = box.getScene() == null ? null : box.getScene().getWindow();
            if (window != null) {
                menu.show(window, e.getScreenX(), e.getScreenY());
            }
        });

        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(box);
        box.setAccessibleText((note.folder() ? "Folder " : "Note ") + note.name());
        return box;
    }

    /**
     * Renames from a small dialog.
     *
     * <p>⚠ The dialog builds its OWN Scene and inherits no stylesheet — it paints Modena white over
     * a dark deck unless the owner's sheets are copied onto it. {@code ShadowMarketView} records the
     * same trap, and it is the same family as the unstyled {@code ScrollPane} viewport.
     *
     * <p>⚠ The title field in the editor is the other way to rename, and it commits on focus loss.
     * This one exists for folders, which have no editor.
     */
    private static void renameInPlace(
            Region owner, GameSession session, GameSession.Note note, Runnable[] repaintTree, Runnable[] openNote) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(note.name());
        dialog.setTitle(Views.t("ui.notes.rename", "Rename"));
        dialog.setHeaderText(null);
        dialog.setContentText(Views.t("ui.notes.name", "Name"));
        if (owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialog.showAndWait().ifPresent(name -> {
            session.renameNote(note.noteId(), name);
            repaintTree[0].run();
            openNote[0].run();
        });
    }

    private static boolean confirm(Region owner, String name) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Delete \"" + name + "\" and everything inside it?",
                javafx.scene.control.ButtonType.CANCEL,
                javafx.scene.control.ButtonType.OK);
        alert.setHeaderText(null);
        if (owner.getScene() != null) {
            alert.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        return alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                == javafx.scene.control.ButtonType.OK;
    }

    // ── the editor ───────────────────────────────────────────────────────────────────────────

    private static Region editor(
            GameSession session, GameSession.Note note, Runnable[] repaintTree, Label result) {
        TextField title = new TextField(note.name());
        title.getStyleClass().add("es-notes-title");
        title.setOnAction(e -> {
            session.renameNote(note.noteId(), title.getText());
            repaintTree[0].run();
        });
        // ⚠ Committed on focus loss as well as on Enter. A player who renames and then clicks
        // straight into the body has renamed it, and losing that is the kind of small betrayal that
        // stops people trusting a notebook.
        title.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                session.renameNote(note.noteId(), title.getText());
                repaintTree[0].run();
            }
        });

        TextArea source = new TextArea(note.body());
        source.getStyleClass().add("es-notes-source");
        source.setWrapText(true);

        TextFlow overlay = new TextFlow();
        overlay.getStyleClass().add("es-notes-overlay");
        overlay.setMouseTransparent(true);

        // ⚠ THE OVERLAY IS BEHIND THE AREA IN Z-ORDER but drawn visible, because the area's own
        // glyphs are transparent. The other way round the TextFlow would swallow the caret and the
        // selection highlight, both of which the area draws.
        StackPane stack = new StackPane(overlay, source);
        stack.setAlignment(Pos.TOP_LEFT);

        Runnable rehighlight = () -> {
            overlay.getChildren().setAll(runs(source.getText()));
            // ⚠ The area is grown to its whole content so it never scrolls ITSELF. It owns its
            // viewport and exposes no scroll offset, so an overlay inside a scrolling TextArea
            // drifts the moment anybody scrolls. One outer ScrollPane, one offset, nothing to sync.
            // ⚠ Floored at MIN_ROWS, or a short note renders as a small box floating in an empty
            // panel and there is nowhere obvious to click to start typing. The +2 is slack so the
            // last line is never flush against the bottom edge as it is being written.
            int lines = Math.max(1, source.getText().split("\n", -1).length);
            source.setPrefRowCount(Math.max(MIN_ROWS, lines + 2));
        };
        rehighlight.run();
        source.textProperty().addListener((obs, was, now) -> rehighlight.run());

        ScrollPane scroll = new ScrollPane(stack);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label saved = Views.secondary("");
        // ⚠ Pulse.every — DATA. An autosave that only ran under `animate` would never fire for a
        // player who had turned Reduce motion on, and they would lose work for having used an
        // accessibility setting. The clock runs in both modes.
        AutoCloseable clock = Pulse.shared().every(AUTOSAVE_MS, () -> {
            GameSession.Outcome out = session.writeNote(note.noteId(), source.getText());
            if (out.succeeded() && !out.message().isBlank()) {
                saved.setText(out.message());
            }
        });

        VBox box = new VBox(UiTokens.SPACE_2, title, scroll, saved);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // ⚠ Written back on the way out as well as on the clock, or up to AUTOSAVE_MS of typing is
        // lost by closing the window — which is exactly when somebody has just finished a thought.
        Views.releaseOnDetach(box, clock, () -> session.writeNote(note.noteId(), source.getText()));
        return box;
    }

    /**
     * The coloured runs, character-for-character with the source.
     *
     * <p>⚠ Nothing here may add, drop or reorder a character. The overlay is aligned by counting
     * cells, so a run that rendered {@code **bold**} as {@code bold} would shift every glyph after
     * it on that line. {@code MarkdownSpans} keeps the markers in the run text for this reason.
     */
    private static List<Text> runs(String markdown) {
        List<Text> out = new ArrayList<>();
        List<MarkdownSpans.Line> lines = MarkdownSpans.parse(markdown);
        for (int i = 0; i < lines.size(); i++) {
            for (MarkdownSpans.Span span : lines.get(i).spans()) {
                Text text = new Text(span.text());
                text.getStyleClass().addAll("es-md", styleFor(span.kind()));
                out.add(text);
            }
            if (i < lines.size() - 1) {
                Text newline = new Text("\n");
                newline.getStyleClass().add("es-md");
                out.add(newline);
            }
        }
        return out;
    }

    private static String styleFor(MarkdownSpans.Kind kind) {
        return switch (kind) {
            case HEADING -> "es-md-heading";
            case STRONG -> "es-md-strong";
            case EMPHASIS -> "es-md-emphasis";
            case CODE -> "es-md-code";
            case MARKER -> "es-md-marker";
            case QUOTE -> "es-md-quote";
            case LINK -> "es-md-link";
            case RULE -> "es-md-rule";
            case TEXT -> "es-md-text";
        };
    }

    private static GameSession.Note find(GameSession session, String noteId) {
        if (noteId == null || noteId.isBlank()) {
            return null;
        }
        return session.notes().stream()
                .filter(n -> n.noteId().equals(noteId))
                .findFirst()
                .orElse(null);
    }
}
