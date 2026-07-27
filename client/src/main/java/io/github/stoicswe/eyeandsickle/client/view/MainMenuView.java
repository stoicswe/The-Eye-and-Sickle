package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.CharacterSlots;
import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The main menu — where the game starts.
 *
 * <h2>Why a menu at all, when the client used to open straight into a game</h2>
 *
 * Because there are now three characters and two modes, and the moment a player has more than one of
 * anything, "which one?" is a question the software has to ask rather than assume. It is also the one
 * place in the client where stopping the player is <em>free</em> — they are not mid-breach, nothing is
 * on a timer, and nothing is at stake — which makes it the right place for the two questions that are
 * awkward everywhere else: which character, and how much explanation do you want.
 *
 * <h2>CL-4 / T-2, answered here</h2>
 *
 * The teaching layer defaults to {@code explain}, which is right for the audience the education goal
 * targets and wrong for a player who already knows Unix. The open question asked for a first-run
 * familiarity prompt and worried about the onboarding cost. On this screen there is no onboarding
 * cost: the player is already stopped, choosing something. It is asked once, it is answerable in one
 * click, and {@code teach} changes it later at any time.
 *
 * <h2>What the online section does and does not claim</h2>
 *
 * It lists home servers the player has named and says, in as many words, that it cannot list their
 * characters yet because the transport is not built (<b>CL-8</b>). Showing an empty character list
 * would read as "you have none", which is a different and false statement.
 */
public final class MainMenuView {

    private MainMenuView() {}

    /** What the menu can ask the application to do. */
    public interface Actions {

        /** Start or resume a solo character in the given slot, with this handle if it is new. */
        void playSolo(int slot, String handleIfNew);

        /** Connect to a home server. Currently reports why it cannot — see CL-8. */
        void connectOnline(String serverAddress);

        void openSettings();

        void quit();
    }

    public static Region create(
            ClientProfile profile, ThemeManager themes, CharacterSlots slots, Actions actions) {

        BorderPane root = new BorderPane();
        root.getStyleClass().add("es-splash");

        // ---------------------------------------------------------------- title
        Label title = new Label("THE EYE AND SICKLE");
        title.getStyleClass().add("es-splash-title");
        Label subtitle = new Label("An operator's console. Single player by default; nothing here needs a network.");
        subtitle.getStyleClass().add("es-splash-subtitle");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(48, 24, 24, 24));
        root.setTop(header);

        // ---------------------------------------------------------------- body
        VBox body = new VBox(18);
        body.setPadding(new Insets(0, 48, 24, 48));
        body.setMaxWidth(860);

        body.getChildren().add(sectionLabel("SOLO — on this machine"));
        body.getChildren().add(soloSection(profile, slots, actions));

        body.getChildren().add(new Separator());
        body.getChildren().add(sectionLabel("ONLINE — on a home server"));
        body.getChildren().add(onlineSection(profile, actions));

        ScrollPane scroll = new ScrollPane(centred(body));
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        root.setCenter(scroll);

        // ---------------------------------------------------------------- footer
        Button settings = menuButton("Settings", actions::openSettings);
        Button quit = menuButton("Quit", actions::quit);
        Label profileNote = new Label("Profile: " + profile.directory());
        profileNote.getStyleClass().add("es-text-secondary");
        // ⚠ Must be the thing that gives way when the footer is short of room, and it was not.
        // It carries the profile path — ~70 characters on macOS — and as a wrapping Label it held
        // its width while the two Buttons beside it shrank to their ellipsis, so a freshly-launched
        // client with no saved window geometry showed "..." where "Settings" and "Quit" should be.
        // A truncated PATH still tells the player where to look; a truncated BUTTON tells them
        // nothing and cannot be guessed. minWidth(0) lets it shrink, and the tooltip keeps the whole
        // path reachable at any size.
        profileNote.setWrapText(false);
        profileNote.setMinWidth(0);
        profileNote.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        HBox.setHgrow(profileNote, Priority.SOMETIMES);
        Tooltip.install(profileNote, new Tooltip(profile.directory().toString()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, profileNote, spacer, settings, quit);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(16, 48, 32, 48));
        root.setBottom(footer);

        // ---------------------------------------------------------------- CL-4 / T-2
        if (!profile.settings().askedFamiliarity) {
            javafx.application.Platform.runLater(() -> askFamiliarity(profile));
        }
        return root;
    }

    // ------------------------------------------------------------------ solo

    private static Region soloSection(ClientProfile profile, CharacterSlots slots, Actions actions) {
        VBox column = new VBox(10);
        for (CharacterSlots.Slot slot : slots.soloSlots()) {
            column.getChildren().add(slotCard(profile, slots, slot, actions));
        }
        return column;
    }

    private static Region slotCard(
            ClientProfile profile, CharacterSlots slots, CharacterSlots.Slot slot, Actions actions) {

        VBox card = new VBox(6);
        card.getStyleClass().add("es-slot-card");

        Label heading = new Label("SLOT " + slot.index());
        heading.getStyleClass().add("es-panel-title");

        Label summary = new Label(slot.summary());
        if (!slot.occupied() && !slot.unreadable()) {
            summary.getStyleClass().add("es-slot-empty");
        }
        if (slot.unreadable()) {
            // Shown, not hidden. A slot that silently reads as empty invites the player to overwrite
            // the character they were trying to recover.
            summary.getStyleClass().add("es-state-refused");
            summary.setWrapText(true);
        }

        Label detail = new Label(slot.detail());
        detail.getStyleClass().add("es-text-secondary");

        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_LEFT);

        if (slot.occupied()) {
            Button play = menuButton("Continue", () -> actions.playSolo(slot.index(), null));
            play.setDefaultButton(slot.index() == profile.settings().lastSoloSlot);
            Button delete = new Button("Delete");
            delete.setOnAction(e -> {
                // Deleting a character is irreversible and there is no undo, so it asks — and the
                // confirmation names the handle, because "are you sure?" with no subject is how
                // people delete the wrong thing.
                Alert confirm = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Delete " + slot.handle() + " in slot " + slot.index() + "? This cannot be undone.",
                        ButtonType.CANCEL,
                        ButtonType.OK);
                confirm.setHeaderText("Delete this character");
                confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                    slots.delete(slot.index());
                    Alert done = new Alert(Alert.AlertType.INFORMATION,
                            "Slot " + slot.index() + " is empty. Reopen the menu to start a new character there.");
                    done.setHeaderText(null);
                    done.showAndWait();
                });
            });
            controls.getChildren().addAll(play, delete);
        } else if (slot.unreadable()) {
            Label cannot = new Label("This slot will not be overwritten automatically.");
            cannot.getStyleClass().add("es-text-secondary");
            controls.getChildren().add(cannot);
        } else {
            TextField handle = new TextField();
            handle.setPromptText("handle");
            handle.setPrefWidth(180);
            handle.setAccessibleText("Handle for a new character in slot " + slot.index());
            Button start = menuButton("New character", () -> {
                String chosen = handle.getText() == null || handle.getText().isBlank()
                        ? "operator"
                        : handle.getText().trim();
                actions.playSolo(slot.index(), chosen);
            });
            controls.getChildren().addAll(handle, start);
        }

        card.getChildren().addAll(heading, summary);
        if (!slot.detail().isEmpty()) {
            card.getChildren().add(detail);
        }
        card.getChildren().add(controls);
        card.setAccessibleText("Solo slot " + slot.index() + ". " + slot.summary() + ". " + slot.detail());
        return card;
    }

    // ------------------------------------------------------------------ online

    private static Region onlineSection(ClientProfile profile, Actions actions) {
        VBox box = new VBox(10);
        box.getStyleClass().add("es-slot-card");

        Label explanation = new Label(
                "Online play runs against a home server — someone's self-hosted machine, which owns "
                        + "the game state. Losses there are real. A solo character cannot be carried "
                        + "across: going online means creating a character on that server.");
        explanation.setWrapText(true);

        TextField address = new TextField();
        address.setPromptText("https://home.example");
        address.setAccessibleText("Home server address");
        List<String> known = profile.settings().knownServers;
        if (!known.isEmpty()) {
            address.setText(known.getFirst());
        }

        Button connect = menuButton("Connect", () -> actions.connectOnline(address.getText()));

        HBox row = new HBox(8, address, connect);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(address, Priority.ALWAYS);

        // Honest about the state of things rather than presenting a dead form.
        Label status = new Label(
                "Not available yet. The client has the session shape for online play and no transport "
                        + "behind it — no REST client and no AT Protocol sign-in (CL-8). Connecting "
                        + "will tell you exactly that rather than hanging.");
        status.setWrapText(true);
        status.getStyleClass().add("es-state-unreachable");

        box.getChildren().addAll(explanation, row, status);
        return box;
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
        ask.setContentText(
                "This game uses real command names and teaches what they actually do.\n\n"
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
        l.getStyleClass().add("es-panel-title");
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

    private static Region centred(Region content) {
        StackPane pane = new StackPane(content);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.setStyle("-fx-background-color: transparent;");
        return pane;
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
        Label label = new Label("Theme");
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
            public void connectOnline(String serverAddress) {
                connect.accept(serverAddress);
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
