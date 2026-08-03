package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.CharacterSlots;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The main menu — a login screen.
 *
 * <h2>Why a menu at all, when the client used to open straight into a game</h2>
 *
 * Because there are now three characters and two modes, and the moment a player has more than one of
 * anything, "which one?" is a question the software has to ask rather than assume. It is also the one
 * place in the client where stopping the player is <em>free</em> — they are not mid-breach, nothing is
 * on a timer, and nothing is at stake — which makes it the right place for the two questions that are
 * awkward everywhere else: which character, and how much explanation do you want.
 *
 * <h2>macOS's user picker over GDM's furniture ({@code ui-design-language.md} §3.1)</h2>
 *
 * A row of round faces with a name under each, and the system controls in a bottom bar. Both halves
 * say the same thing, which is the thing this screen is for: <b>the question is "who", and everything
 * else is chrome.</b> §3's tiling rule does not reach here — see §3.1 for why, and for what the
 * stacked column of slot cards this replaced was getting wrong.
 *
 * <p>⚠ The pictures are the ones the operators actually have, generated silhouettes included — which
 * is what makes them worth showing. Three characters called {@code kyy}, {@code kyyr} and
 * {@code kyyrell} are indistinguishable in a list and instantly different as faces.
 *
 * <h2>CL-4 / T-2, answered here</h2>
 *
 * The teaching layer defaults to {@code explain}, which is right for the audience the education goal
 * targets and wrong for a player who already knows Unix. The open question asked for a first-run
 * familiarity prompt and worried about the onboarding cost. On this screen there is no onboarding
 * cost: the player is already stopped, choosing something. It is asked once, it is answerable in one
 * click, and {@code teach} changes it later at any time.
 *
 * <h2>What the home-server face does and does not claim</h2>
 *
 * It says, in as many words, that the transport is not built (<b>CL-8</b>) — before taking an address
 * rather than after. A field that accepts input and then fails teaches the player to distrust the
 * address they typed, which is a worse outcome than the missing feature.
 */
public final class MainMenuView {

    private MainMenuView() {}

    /** Diameter of a face, in points. Large enough that a 96px avatar is not upscaled. */
    private static final double FACE = 84;

    /** What the menu can ask the application to do. */
    public interface Actions {

        /** Start or resume a solo character in the given slot, with this handle if it is new. */
        void playSolo(int slot, String handleIfNew);

        /**
         * Opens the setup assistant for an empty slot.
         *
         * <p>Separate from {@link #playSolo} on purpose: creating a character and resuming one are
         * different acts, and only the first has anything to ask.
         */
        void setUpNewCharacter(int slot, String suggestedHandle);

        /** Connect to a home server. Currently reports why it cannot — see CL-8. */
        void connectOnline(String serverAddress);

        /**
         * Opens the AT Protocol sign-in panel.
         *
         * <p>⚠ Separate from {@link #connectOnline} because they are different acts and the order
         * matters: an account proves who you are, a home server is where you play. Signing in first
         * is what lets the client offer servers rather than demand an address — see
         * {@code ServerFinder}.
         */
        void addOnlineAccount();

        void openSettings();

        void quit();
    }

    /**
     * The login screen.
     *
     * <h2>macOS's user picker over GDM's furniture</h2>
     *
     * A row of round faces with a name under each, the way macOS asks who you are — and a top band
     * carrying the machine's identity with the system controls parked bottom-right, the way GNOME's
     * greeter does. Both are the same idea: <b>the question is "who", and everything else is
     * chrome.</b>
     *
     * <p>It replaced a stacked column of slot cards, each carrying a handle, a balance, a cycle
     * count, an hours-played line and two buttons. That is a save-management screen, and it asked
     * the player to read six numbers before they could start playing. A face and a name is the whole
     * question; the numbers moved under the selected face, where they answer "is this the one I
     * meant" rather than "which of these exists".
     *
     * <p>⚠ The pictures are the ones the operators actually have, generated silhouettes included —
     * which is what makes them worth showing. Three characters called {@code kyy}, {@code kyyr} and
     * {@code kyyrell} are indistinguishable in a list and instantly different as faces.
     */
    public static Region create(ClientProfile profile, ThemeManager themes, CharacterSlots slots, Actions actions) {

        BorderPane root = new BorderPane();
        root.getStyleClass().add("es-splash");

        // ── the top band ──────────────────────────────────────────────────────────────────────
        //
        // GDM puts the clock and the machine up here. So does this: the game's name, and the
        // profile the client is running out of, which is the closest thing it has to a hostname.
        Label title = new Label(Views.t("ui.main-menu.the-eye-and-sickle", "THE EYE AND SICKLE"));
        title.getStyleClass().add("es-splash-title");
        Label subtitle = new Label(Views.t("ui.main-menu.an-operator-s-console", "An operator's console"));
        subtitle.getStyleClass().add("es-splash-subtitle");

        VBox header = new VBox(4, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(56, 24, 8, 24));
        installWindowDrag(header, profile);
        root.setTop(header);

        // ── the faces ─────────────────────────────────────────────────────────────────────────
        //
        // ⚠ Built ONCE and restyled, never rebuilt. Selection follows keyboard focus here, and a row
        // that replaces its children on every change destroys the node that just gained focus — the
        // first arrow key would move the highlight and then drop the player out of the picker
        // entirely. Rebuilding also makes the picker mouse-only in practice, which is a regression
        // from the list of per-slot buttons this screen replaced.
        HBox faces = new HBox(34);
        faces.setAlignment(Pos.CENTER);

        VBox detail = new VBox(10);
        detail.setAlignment(Pos.CENTER);
        detail.setMinHeight(150);

        int[] chosen = {firstOccupied(slots)};
        java.util.List<Face> plates = new java.util.ArrayList<>();
        Runnable[] showDetail = new Runnable[1];
        Runnable[] rebuildFaces = new Runnable[1];

        showDetail[0] = () -> {
            for (Face plate : plates) {
                plate.setSelected(plate.slot == chosen[0]);
            }
            detail.getChildren().clear();
            slots.soloSlots().stream()
                    .filter(candidate -> candidate.index() == chosen[0])
                    .findFirst()
                    .ifPresent(slot -> detail.getChildren().add(signIn(slots, slot, actions, rebuildFaces[0])));
        };

        // Only a deletion runs this: it changes what a face IS, not merely which one is lit, so the
        // row genuinely has to be built again. It is also the one moment when losing focus costs
        // nothing — a modal confirmation has just closed and focus is being restored regardless.
        rebuildFaces[0] = () -> {
            plates.clear();
            faces.getChildren().clear();
            for (CharacterSlots.Slot slot : slots.soloSlots()) {
                Face plate = face(slot, () -> {
                    chosen[0] = slot.index();
                    showDetail[0].run();
                });
                plates.add(plate);
                faces.getChildren().add(plate.box);
            }
            faces.getChildren().add(otherFace(profile, actions).box);
            showDetail[0].run();
        };
        rebuildFaces[0].run();

        VBox centre = new VBox(26, faces, detail);
        centre.setAlignment(Pos.CENTER);
        centre.setPadding(new Insets(20, 40, 20, 40));
        root.setCenter(centre);

        // ── the bottom bar ────────────────────────────────────────────────────────────────────
        //
        // Where a greeter puts its power controls. ⚠ The profile path is the thing that gives way
        // when the bar is short of room, and it has to be said explicitly: an HBox shrinks whatever
        // it can, and a Button's minimum is the width of an ellipsis. A truncated PATH still says
        // where to look; a truncated BUTTON cannot be guessed. menuButton() pins the buttons at their
        // preferred width, so this is the only child left that can give.
        Button settings = menuButton("Settings", actions::openSettings);
        Button quit = menuButton("Quit", actions::quit);
        Label profileNote = new Label(profile.directory().toString());
        profileNote.getStyleClass().add("es-small");
        profileNote.setWrapText(false);
        profileNote.setMinWidth(0);
        profileNote.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        HBox.setHgrow(profileNote, Priority.SOMETIMES);
        Tooltip.install(profileNote, new Tooltip(profile.directory().toString()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, profileNote, spacer, settings, quit);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(16, 40, 28, 40));
        root.setBottom(footer);

        // ⚠ The setup assistant asks this now, properly, on a pane of its own — so it must NOT
        // also fire here on a fresh install, where it would pop over the login screen and then be
        // asked again two minutes later. It survives for one case only: a profile that already has
        // characters and has never been asked, i.e. a save made before the assistant existed. Those
        // players would otherwise never see the question unless they created another character.
        boolean anyCharacters = slots.soloSlots().stream().anyMatch(CharacterSlots.Slot::occupied);
        if (!profile.settings().askedFamiliarity && anyCharacters) {
            javafx.application.Platform.runLater(() -> askFamiliarity(profile));
        }
        return root;
    }

    /**
     * Makes the top band drag the window.
     *
     * <p>⚠ Without this the menu cannot be moved at all. The Stage is undecorated (§0), and the drag
     * handle that solves this on the deck is the top strip — which does not exist yet on this screen.
     * A player who launches for the first time onto a badly-placed window has a title bar on every
     * other screen of the client and none on the first one.
     *
     * <p>Not installed when the OS is drawing the frame: its title bar already drags the window, and
     * a second handle inside the content fights it — press on the band and the window jumps by the
     * offset between the two.
     */
    private static void installWindowDrag(Region handle, ClientProfile profile) {
        if (profile.settings().nativeWindowBorder) {
            return;
        }
        double[] grab = {0, 0};
        handle.setOnMousePressed(event -> {
            javafx.stage.Window window =
                    handle.getScene() == null ? null : handle.getScene().getWindow();
            if (window != null) {
                grab[0] = event.getScreenX() - window.getX();
                grab[1] = event.getScreenY() - window.getY();
            }
        });
        handle.setOnMouseDragged(event -> {
            javafx.stage.Window window =
                    handle.getScene() == null ? null : handle.getScene().getWindow();
            if (window instanceof javafx.stage.Stage stage && !stage.isMaximized()) {
                stage.setX(event.getScreenX() - grab[0]);
                stage.setY(event.getScreenY() - grab[1]);
            }
        });
    }

    /** The slot a fresh launch lands on: the first with a character in it, else the first empty. */
    private static int firstOccupied(CharacterSlots slots) {
        return slots.soloSlots().stream()
                .filter(CharacterSlots.Slot::occupied)
                .map(CharacterSlots.Slot::index)
                .findFirst()
                .orElse(
                        slots.soloSlots().isEmpty()
                                ? 1
                                : slots.soloSlots().getFirst().index());
    }

    /**
     * One face in the picker: the ring, whatever it holds, and the name under it.
     *
     * <p>Kept as an object rather than a bare {@code Region} so selection can be a <b>restyle</b>
     * of live nodes. See the note in {@link #create} for why rebuilding is not an option.
     */
    private static final class Face {

        final VBox box;
        final int slot;
        private final javafx.scene.shape.Circle ring;
        private final Label caption;
        private final String restingRing;

        Face(int slot, javafx.scene.Node inner, String name, String restingRing) {
            this.slot = slot;
            this.restingRing = restingRing;

            ring = new javafx.scene.shape.Circle(FACE / 2 + 4);
            ring.getStyleClass().add(restingRing);

            StackPane plate = new StackPane(ring, inner);
            double outer = FACE + 8;
            plate.setMinSize(outer, outer);
            plate.setPrefSize(outer, outer);
            plate.setMaxSize(outer, outer);

            caption = new Label(name);
            caption.getStyleClass().add("es-face-name");

            box = new VBox(9, plate, caption);
            box.setAlignment(Pos.CENTER);
            box.getStyleClass().add("es-face");
            box.setFocusTraversable(true);
            io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(box);
        }

        void setSelected(boolean on) {
            // ⚠ remove-then-add, never setAll. A Label carries "label" in its own style-class list,
            // and clearing it takes the control's Modena skin down with it.
            ring.getStyleClass().removeAll("es-face-ring", "es-face-ring-empty", "es-face-ring-bad", "es-face-ring-on");
            ring.getStyleClass().add(on ? "es-face-ring-on" : restingRing);
            caption.getStyleClass().removeAll("es-face-name", "es-face-name-on");
            caption.getStyleClass().add(on ? "es-face-name-on" : "es-face-name");
        }

        /** Click, Enter or Space — and, for the slot faces, arriving by Tab. */
        void onPick(Runnable action, boolean alsoOnFocus) {
            box.setOnMouseClicked(event -> {
                box.requestFocus();
                action.run();
            });
            box.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ENTER
                        || event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                    action.run();
                    event.consume();
                }
            });
            if (alsoOnFocus) {
                // Selection follows focus, the way a radio group does — so Tab through the row and
                // the summary under it keeps up, with nothing extra to press. Gained only: focus
                // moving on to the "Continue" button below must not deselect the operator it starts.
                box.focusedProperty().addListener((property, was, now) -> {
                    if (Boolean.TRUE.equals(now)) {
                        action.run();
                    }
                });
            }
        }
    }

    /**
     * A character slot, as a face.
     *
     * <h2>The circle is a SHAPE, not a corner radius</h2>
     *
     * ⚠ {@code -fx-background-radius} would be two mistakes at once. The first is §9: a non-zero
     * radius is permitted only under {@code .es-rounded}, and {@code UiContractTest} fails the build
     * on one anywhere else. The second is that it would not work regardless — the picture is an
     * {@code ImageView}, and an image has no background for a radius to round. The ring is a
     * {@link javafx.scene.shape.Circle} behind the picture and the picture is clipped by another,
     * which is the same lesson the window corners taught: <b>geometry is a clip, not CSS.</b>
     */
    private static Face face(CharacterSlots.Slot slot, Runnable onPick) {
        javafx.scene.Node picture;
        if (slot.occupied()) {
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(
                    io.github.stoicswe.eyeandsickle.client.ui.Avatar.image(slot.avatarPng(), slot.handle()));
            view.setFitWidth(FACE);
            view.setFitHeight(FACE);
            view.setClip(new javafx.scene.shape.Circle(FACE / 2, FACE / 2, FACE / 2));
            picture = view;
        } else {
            Label glyph = new Label(slot.unreadable() ? "!" : "+");
            glyph.getStyleClass().add("es-face-glyph");
            picture = glyph;
        }

        // An empty slot is captioned by its NUMBER, not by the word "empty": on a first run all
        // three say the same thing, and three identical captions answer none of the question this
        // screen exists to ask. The glyph already says the slot is empty.
        String name = slot.occupied() ? slot.handle() : "Slot " + slot.index();
        // ⚠ A damaged save gets its own ring rather than the empty one. Otherwise the only thing
        // separating "nothing here" from "your character will not load" is one character of glyph,
        // and the difference matters most before the player has clicked anything.
        Face plate = new Face(
                slot.index(),
                picture,
                name,
                slot.occupied() ? "es-face-ring" : slot.unreadable() ? "es-face-ring-bad" : "es-face-ring-empty");
        plate.onPick(onPick, true);
        plate.box.setAccessibleText(
                slot.occupied()
                        ? "Character " + slot.handle() + " in slot " + slot.index()
                        : slot.unreadable()
                                ? "Slot " + slot.index() + ", damaged save"
                                : "Empty character slot " + slot.index());
        return plate;
    }

    /**
     * The last face in the row: a home server.
     *
     * <p>macOS calls this "Other…" and GDM calls it "Not listed?". Both mean the same thing — the
     * identity you want is not one of these — and putting online play <em>there</em>, rather than in
     * a section of its own, says exactly what it is: another way to be somebody, not another mode of
     * the game.
     *
     * <p>⚠ This one does <b>not</b> select on focus. It opens a popup, and a Tab that opens a dialog
     * is a trap: the player is trying to reach the buttons past it.
     */
    private static Face otherFace(ClientProfile profile, Actions actions) {
        Label glyph = new Label("//");
        glyph.getStyleClass().add("es-face-glyph");

        Face plate = new Face(0, glyph, "Home server", "es-face-ring-empty");
        plate.onPick(() -> showServerPrompt(profile, plate.box, actions), false);
        plate.box.setAccessibleText("Connect to a home server");
        return plate;
    }

    /**
     * What sits under the chosen face: the numbers, and the way in.
     *
     * <p>This is where a login screen puts the password field, and it does the same job — it is the
     * only control on the screen that <em>starts</em> anything, so it is the only one that needs to
     * be found. Everything above it answers "which"; this answers "go".
     */
    private static Region signIn(CharacterSlots slots, CharacterSlots.Slot slot, Actions actions, Runnable repaint) {

        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);

        if (slot.unreadable()) {
            // Shown as broken rather than hidden: a slot that silently reads as empty invites the
            // player to overwrite the thing they were trying to recover.
            Label problem = new Label(slot.summary());
            problem.getStyleClass().add("es-state-refused");
            problem.setWrapText(true);
            problem.setMaxWidth(520);
            box.getChildren().add(problem);
            return box;
        }

        if (slot.occupied()) {
            Label summary = new Label(slot.summary());
            summary.getStyleClass().add("es-small");
            Label detail = new Label(slot.detail());
            detail.getStyleClass().add("es-small");

            Button play = menuButton("Continue", () -> actions.playSolo(slot.index(), null));
            play.setDefaultButton(true);
            Button delete = menuButton("Delete", () -> {
                if (confirmDelete(slot)) {
                    slots.delete(slot.index());
                    repaint.run();
                }
            });
            HBox row = new HBox(10, play, delete);
            row.setAlignment(Pos.CENTER);
            box.getChildren().addAll(summary, detail, row);
            return box;
        }

        TextField handle = new TextField();
        handle.setPromptText("handle");
        handle.setPrefColumnCount(18);
        // The field is a HEAD START, not the decision: whatever is typed here arrives pre-filled on
        // the assistant's first question, where it can still be changed. Leaving it blank is fine —
        // the assistant asks for a handle properly and will not continue without one.
        Runnable start = () -> actions.setUpNewCharacter(
                slot.index(), handle.getText() == null ? "" : handle.getText().trim());
        Button create = menuButton("New character", start::run);
        handle.setOnAction(event -> start.run());
        HBox row = new HBox(10, handle, create);
        row.setAlignment(Pos.CENTER);

        Label note = new Label(Views.t(
                "ui.main-menu.a-new-operator-on",
                "A new operator on this machine. Set-up asks five short questions; "
                        + "nothing here needs a network."));
        note.getStyleClass().add("es-small");
        box.getChildren().addAll(row, note);
        return box;
    }

    /**
     * The home-server prompt, opened from the "Home server" face.
     *
     * <p>⚠ It says plainly that online play is not wired up (CL-8) rather than accepting an address
     * and failing later. A field that takes input and then reports {@code EX_UNAVAILABLE} teaches
     * the player to distrust the address they typed; a prompt that says so first does not.
     */
    private static void showServerPrompt(ClientProfile profile, javafx.scene.Node anchor, Actions actions) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        TextField address = new TextField(
                profile.settings().knownServers.isEmpty()
                        ? ""
                        : profile.settings().knownServers.getFirst());
        address.setPromptText("https://home.example");
        address.setPrefColumnCount(28);

        Button connect = menuButton("Connect", () -> actions.connectOnline(address.getText()));
        Label note = new Label(Views.t(
                "ui.main-menu.online-play-runs-against",
                "Online play runs against a home server — someone's self-hosted machine, which owns "
                        + "the game state. Losses there are real, and a solo character cannot be "
                        + "carried across.\n\nNot available yet: the client has the session shape and "
                        + "no transport behind it (CL-8). Connecting will tell you exactly that "
                        + "rather than hanging."));
        note.setWrapText(true);
        note.setMaxWidth(420);
        note.getStyleClass().add("es-small");

        Button addAccount = menuButton(Views.t("ui.main-menu.add-an-online-account", "Add an online account"), () -> {
            popup.hide();
            actions.addOnlineAccount();
        });
        Label accountNote = new Label(Views.t(
                "ui.main-menu.an-online-account-is",
                "An online account is your AT Protocol identity — the same one Bluesky uses. It is "
                        + "what a home server recognises you by, and signing in first lets the client "
                        + "look for servers instead of asking you to know one."));
        accountNote.setWrapText(true);
        accountNote.setMaxWidth(420);
        accountNote.getStyleClass().add("es-small");

        VBox panel = new VBox(
                10,
                sectionLabel("ONLINE ACCOUNT"),
                addAccount,
                accountNote,
                sectionLabel("HOME SERVER"),
                new HBox(10, address, connect),
                note);
        panel.getStyleClass().addAll("es-files", "es-body-pad", "es-files-dialog");
        popup.getContent().add(panel);
        if (anchor.getScene() != null && anchor.getScene().getWindow() != null) {
            var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
            popup.show(anchor, bounds.getMinX() - 180, bounds.getMaxY() + 8);
        }
    }

    // ------------------------------------------------------------------ deleting

    /**
     * Asks before destroying a character, and names it.
     *
     * <p>Deletion is irreversible and there is no undo, so it asks — and the question NAMES the
     * handle, because "are you sure?" with no subject is how people delete the wrong thing. On a
     * screen where three faces sit side by side and the selected one is distinguished only by a
     * ring, that matters more than it did on the old list.
     */
    private static boolean confirmDelete(CharacterSlots.Slot slot) {
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Delete " + slot.handle() + " in slot " + slot.index() + "? This cannot be undone.",
                ButtonType.CANCEL,
                ButtonType.OK);
        confirm.setHeaderText("Delete this character");
        return confirm.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    // ------------------------------------------------------------------ CL-4 / T-2

    /**
     * Asked once, on the first run, and never again.
     *
     * <p>The cost this question was worried about is an onboarding step. There is none here — the
     * player is already stopped on a menu deciding which character to play, and one more choice
     * costs them nothing. Both answers are honest about what they do, and {@code teach} changes it
     * later at any point.
     */
    private static void askFamiliarity(ClientProfile profile) {
        ButtonType newToThis = new ButtonType("Explain as I go");
        ButtonType knowUnix = new ButtonType("I know Unix");

        Alert ask = new Alert(Alert.AlertType.NONE);
        ask.setTitle("One question");
        ask.setHeaderText("How much should this explain?");
        ask.setContentText("This game uses real command names and teaches what they actually do.\n\n"
                + "  Explain as I go   a plain-language line the first time each term appears\n"
                + "  I know Unix       just the terms, no explanations\n\n"
                + "Either way the manual stays: `man <term>` works at any setting, and `teach`\n"
                + "changes this whenever you like.");
        ask.getButtonTypes().setAll(newToThis, knowUnix);
        ask.showAndWait().ifPresent(choice -> {
            profile.settings().teachingLevel = choice == knowUnix ? "terms" : "explain";
            profile.settings().askedFamiliarity = true;
            profile.save();
        });
    }

    // ------------------------------------------------------------------ helpers

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("es-label");
        return l;
    }

    private static Button menuButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("es-menu-button");
        // docs/client/07 §3.8 — WCAG SC 2.5.8 wants a 24x24 minimum target; menu buttons get more.
        b.setMinHeight(34);
        // ⚠ And never narrower than its own label. A Labeled's default minimum width is the width
        // of an ellipsis, so an HBox short of space silently renders a button as "...", which is
        // both unreadable and — since SC 2.5.8 is about the TARGET — smaller than the minimum it
        // was just given a height for. Height alone does not make a target.
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        return b;
    }

    /** A theme picker for the menu's settings dialog, so the look can be changed before playing. */
    public static Region quickThemePicker(ClientProfile profile, ThemeManager themes) {
        ChoiceBox<ThemeId> picker = new ChoiceBox<>();
        picker.getItems().addAll(ThemeId.selectable());
        picker.setValue(themes.current());
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ThemeId id) {
                return id == null ? "" : id.label();
            }

            @Override
            public ThemeId fromString(String s) {
                return ThemeId.DECK;
            }
        });
        picker.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                themes.select(now);
                profile.save();
            }
        });
        Label label = new Label(Views.t("ui.main-menu.theme", "Theme"));
        return new HBox(10, label, picker);
    }

    /** Wires a consumer as an {@link Actions} implementation, for tests and small callers. */
    public static Actions actions(
            java.util.function.BiConsumer<Integer, String> playSolo,
            Consumer<String> connect,
            Runnable settings,
            Runnable quit) {
        return new Actions() {
            @Override
            public void playSolo(int slot, String handleIfNew) {
                playSolo.accept(slot, handleIfNew);
            }

            @Override
            public void setUpNewCharacter(int slot, String suggestedHandle) {
                // Small callers get the old behaviour: create the character straight away. The
                // assistant is a screen the application owns, and a helper that took a callback for
                // it would be asking every caller to build one.
                playSolo.accept(slot, suggestedHandle);
            }

            @Override
            public void connectOnline(String serverAddress) {
                connect.accept(serverAddress);
            }

            @Override
            public void addOnlineAccount() {
                // The small-caller helper has no sign-in screen to open. Deliberately inert rather
                // than throwing: this overload exists for snapshots and tests, and a preview that
                // crashes on a button nobody meant to press is worse than one that does nothing.
            }

            @Override
            public void openSettings() {
                settings.run();
            }

            @Override
            public void quit() {
                quit.run();
            }
        };
    }
}
