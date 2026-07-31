package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.profile.Hostname;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.ui.Avatar;
import io.github.stoicswe.eyeandsickle.client.ui.GlowRing;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * The setup assistant — one question at a time, before the character exists.
 *
 * <h2>What this is for, and why it is not just the Settings window</h2>
 *
 * Every choice here is already in Settings, and a player can change all of them later. That is not a
 * redundancy — it is the point. A new player who is handed a deck with twenty tool windows has no
 * idea that the pointer is theirs to pick, that there are six palettes, or that the game will stop
 * explaining Unix if they ask it to. <b>Settings answers a question the player already has; a setup
 * assistant tells them the question exists.</b> The teaching level (CL-4 / T-2) is the clearest case:
 * it materially changes the game, its default is right for one audience and wrong for another, and it
 * used to be asked in a bare {@code Alert} on first run.
 *
 * <p>Modelled on macOS's Setup Assistant, down to the shape: one decision per pane, a large title, a
 * short paragraph of consequence, the control, and <b>Continue</b> in the bottom right. Nothing on a
 * pane competes with the thing the pane is asking.
 *
 * <h2>⚠ Global settings, per-character wizard — and how that is resolved</h2>
 *
 * Only two of these values belong to the character: the <b>handle</b> and the <b>picture</b>. Theme,
 * pointer, motion, text size, hostname and teaching level are all
 * <b>profile-global</b> — one per install, shared by every character. macOS has the same split and
 * dodges it by asking the long questions only on first boot.
 *
 * <p>This asks every time, because a player creating their second character is <em>also</em> a player
 * who might now want the high-visibility palette. What makes that safe is that <b>every global page
 * is seeded from the value that is already set</b>: pressing Continue through the whole wizard
 * changes nothing. The alternative — a short wizard for later characters — was rejected because it
 * hides the one screen that tells a player these options exist.
 *
 * <p>⚠ And because the globals are applied <em>live</em>, {@link #create} snapshots them on entry and
 * {@code Actions.cancel} puts them all back. A player who opens the wizard, tries three palettes and
 * backs out has not silently re-themed the character they were already playing.
 *
 * <h2>Live preview, because a palette cannot be chosen from its name</h2>
 *
 * Selecting a theme applies it to the wizard itself, immediately — "Phosphor" is not a decision
 * anybody can make from the word. The same for pointer, motion and text size. Each pane is therefore
 * both the question and the answer's consequence.
 *
 * <h2>⚠ The picture is chosen before there is anywhere to put it</h2>
 *
 * {@code session.setAvatar} needs a session, and there is no session until the character is created —
 * which happens when this screen finishes. So the encoded PNG rides out through
 * {@code Actions.begin(slot, handle, avatarPng)} and the client applies it once the save exists. The
 * generated silhouette shown on the way is the real one: {@link Avatar#placeholder} is derived from
 * the handle, so it is already <em>theirs</em> as soon as they have typed a name.
 */
public final class SetupWizardView {

    private SetupWizardView() {}

    /** What the assistant asks the application to do. */
    public interface Actions {

        /**
         * Re-applies the live-previewed globals to the visible scene.
         *
         * <p>Text size in particular cannot be done from here: the scaler lives on the Scene, and
         * this view does not own one.
         */
        void applyPreview();

        /**
         * Creates the character and starts the game.
         *
         * @param avatarPng base64, or empty for the generated silhouette
         */
        void begin(int slot, String handle, String avatarPng);

        /** Back to the login screen. The caller restores the snapshot — see {@link #create}. */
        void cancel();
    }

    /** The emblem on the first and last panes. Large enough to be a mark rather than an icon. */
    private static final double MARK_RADIUS = 34;

    /**
     * The teaching levels, stored id to the words the player actually clicked.
     *
     * <p>One map rather than two lists, so the pane that asks and the pane that summarises cannot
     * come to different names for the same choice (§6: one name per action, used everywhere).
     */
    private static final java.util.Map<String, String> TEACHING_LABELS = java.util.Map.of(
            "explain", "Explain as I go",
            "terms", "Just the terms",
            "off", "Neither");

    /** The picture shown while choosing one. Bigger than the strip's, so a crop can be judged. */
    private static final double PORTRAIT = 148;

    /** One palette swatch, including its padding. The grid's width is derived from it. */
    private static final double SWATCH_TILE = 132;

    /**
     * Builds the assistant.
     *
     * <p>⚠ The caller must snapshot the profile's global settings before calling this and restore
     * them in {@code Actions.cancel}. The snapshot is not taken here because the restore has to
     * outlive this view — by the time cancel runs, this node is being discarded.
     *
     * @param suggestedHandle what the player typed on the login screen, or empty
     */
    public static Region create(
            ClientProfile profile, ThemeManager themes, int slot, String suggestedHandle, Actions actions) {

        String[] handle = {suggestedHandle == null ? "" : suggestedHandle.trim()};
        String[] avatar = {""};
        int[] at = {0};

        List<Pane> panes = new ArrayList<>();
        Runnable[] refreshPortrait = new Runnable[1];

        panes.add(welcome());
        panes.add(identity(profile, handle));
        panes.add(picture(handle, avatar, refreshPortrait));
        panes.add(appearance(profile, themes));
        panes.add(accessibility(profile, themes, actions));
        panes.add(teaching(profile));
        panes.add(ready(profile, handle, avatar));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("es-setup");

        StackPane stage = new StackPane();
        stage.setAlignment(Pos.CENTER);
        // ⚠ An explicit maximum. A StackPane reports an unbounded one, but the Labels inside a pane
        // are Controls, and a Control's computed maximum is its preferred size — so a pane that
        // grew would still sit in the top third of the window. This has already cost a debugging
        // round once, in Settings.
        stage.setMaxHeight(Double.MAX_VALUE);

        // ⚠ NO ScrollPane here, and that was tried. Wrapping this StackPane in one made the panes
        // stop repainting when their children were swapped — the step marks advanced and the
        // previous pane stayed on screen. A ScrollPane renders its content through a skin-owned
        // viewport that needs a pulse to refresh, and a synchronous render (and, more importantly,
        // anything that swaps content without one) does not get that.
        //
        // The prose is pinned to its preferred height instead (see blurb/foot), so a pane that will
        // not fit clips its own footnote rather than ellipsising a paragraph to one line — and the
        // Continue button, which lives in the BorderPane's bottom, is never squeezed at all.
        BorderPane.setMargin(stage, new Insets(0, 40, 0, 40));
        root.setCenter(stage);

        HBox dots = new HBox(UiTokens.SPACE_2);
        dots.setAlignment(Pos.CENTER);
        List<Region> marks = new ArrayList<>();
        for (int i = 0; i < panes.size(); i++) {
            Region mark = new Region();
            mark.getStyleClass().add("es-setup-step");
            mark.setMinSize(7, 7);
            mark.setPrefSize(7, 7);
            mark.setMaxSize(7, 7);
            marks.add(mark);
            dots.getChildren().add(mark);
        }

        Label back = link("Back");
        Label cancel = link("Cancel");
        BreachView.Chip forward = new BreachView.Chip("Continue", "es-setup-go");

        Runnable[] show = new Runnable[1];
        show[0] = () -> {
            Pane pane = panes.get(at[0]);
            stage.getChildren().setAll(pane.node());
            for (int i = 0; i < marks.size(); i++) {
                Region mark = marks.get(i);
                mark.getStyleClass().removeAll("es-setup-step-on", "es-setup-step-done");
                if (i == at[0]) {
                    mark.getStyleClass().add("es-setup-step-on");
                } else if (i < at[0]) {
                    mark.getStyleClass().add("es-setup-step-done");
                }
            }
            back.setVisible(at[0] > 0);
            // ⚠ Visible AND managed. Left managed, an invisible Back still holds its width and the
            // step marks sit off-centre on the first pane only — the kind of one-pane misalignment
            // that reads as a rendering fault rather than as a layout bug.
            back.setManaged(at[0] > 0);
            forward.setText(Ui.upper(at[0] == panes.size() - 1 ? "Start" : "Continue"));
            pane.onShown();
        };

        Runnable advance = () -> {
            Pane pane = panes.get(at[0]);
            String problem = pane.problem();
            if (problem != null) {
                pane.complain(problem);
                return;
            }
            if (at[0] == panes.size() - 1) {
                // Asked and answered — the first-run Alert must not fire on top of the deck for
                // somebody who has just been through six panes of exactly this.
                profile.settings().askedFamiliarity = true;
                profile.settings().soloHandle = handle[0];
                profile.save();
                actions.begin(slot, handle[0], avatar[0]);
                return;
            }
            at[0]++;
            show[0].run();
        };

        back.setOnMouseClicked(event -> {
            if (at[0] > 0) {
                at[0]--;
                show[0].run();
            }
        });
        // ⚠ Cancel ASKS, and the same argument that keeps Escape unbound is why. By the time it is
        // reachable a player may have typed a handle and cropped a picture, and there is nothing to
        // recover it from. The question names what is lost rather than saying "are you sure?", which
        // is how people confirm the wrong thing.
        cancel.setOnMouseClicked(event -> {
            if (at[0] == 0 || confirmAbandon()) {
                actions.cancel();
            }
        });
        forward.onInvoke(advance);

        // ⚠ A StackPane, not an HBox with spacers. Laid out in a row, the marks centre in whatever
        // is LEFT after Back — so they jump sideways by Back's width between pane one and pane two,
        // which reads as the progress indicator being unable to hold still. Stacked, Back is drawn
        // over a row that is already the full width.
        HBox backRow = new HBox(back, Ui.spacer());
        backRow.setAlignment(Pos.CENTER_LEFT);
        StackPane top = new StackPane(dots, backRow);
        top.setPadding(new Insets(26, 40, 0, 40));
        root.setTop(top);

        HBox bottom = new HBox(UiTokens.SPACE_4, cancel, Ui.spacer(), forward);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(0, 40, 30, 40));
        HBox.setHgrow(bottom.getChildren().get(1), Priority.ALWAYS);
        root.setBottom(bottom);

        // ⚠ A HANDLER, not a filter. A filter runs in the capture phase, on the way DOWN to the
        // focused node — so Enter on a palette swatch, an option row, Back or Cancel was being eaten
        // before any of them saw it, and every one of their Enter branches was dead code. As a
        // bubbling handler it fires only when nothing nearer the target consumed the key, which is
        // exactly "Enter means Continue unless the focused thing has its own idea".
        //
        // Escape is deliberately NOT bound: on a screen whose job is a sequence of decisions, a key
        // that throws the sequence away is one keystroke from losing a picture somebody just
        // cropped. Cancel is a control they have to mean.
        root.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                advance.run();
                event.consume();
            }
        });

        show[0].run();
        return root;
    }

    // ------------------------------------------------------------------ the panes

    /**
     * One question.
     *
     * @param node the whole pane, already laid out
     * @param problem returns why Continue cannot proceed, or null
     * @param complain shows that reason
     * @param onShown run each time the pane comes back into view — focus, or a refresh
     */
    private record Pane(
            Node node,
            java.util.function.Supplier<String> blocker,
            java.util.function.Consumer<String> report,
            Runnable arrival) {

        /** @return why Continue cannot proceed, or null */
        String problem() {
            return blocker.get();
        }

        void complain(String text) {
            report.accept(text);
        }

        void onShown() {
            arrival.run();
        }
    }

    private static Pane still(Node node) {
        return new Pane(node, () -> null, text -> {}, () -> {});
    }

    private static Pane welcome() {
        GlowRing mark = new GlowRing(MARK_RADIUS);
        VBox body = column(
                mark,
                title("Welcome"),
                blurb("This rig is yours. Five short questions and it is set up the way you want "
                        + "it — what to call you, what it looks like, and how much it explains. "
                        + "Every one of them is in Settings afterwards, and none of them is "
                        + "permanent."));
        return still(body);
    }

    /** The handle, the hostname, and the prompt they add up to. */
    private static Pane identity(ClientProfile profile, String[] handle) {
        TextField name = new TextField(handle[0]);
        name.setPromptText("handle");
        name.setPrefColumnCount(16);
        name.getStyleClass().add("es-setup-field");

        TextField host = new TextField(profile.settings().rigHostname);
        host.setPrefColumnCount(16);
        host.getStyleClass().add("es-setup-field");

        Label preview = new Label();
        preview.getStyleClass().add("es-setup-preview");
        // ⚠ Hostname.sanitise falls back to the DEFAULT for anything invalid, silently. Without
        // this line a player who types "my rig" watches the preview say `rig` and is given no
        // reason — the field still holds what they typed, so it reads as the preview being broken
        // rather than as the name being refused.
        Label hostNote = new Label();
        hostNote.getStyleClass().add("es-setup-trouble");
        hostNote.setWrapText(true);
        hostNote.setMaxWidth(520);
        hostNote.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Label trouble = new Label();
        trouble.getStyleClass().add("es-setup-trouble");
        trouble.setWrapText(true);
        trouble.setMaxWidth(520);

        Runnable sync = () -> {
            handle[0] = name.getText() == null ? "" : name.getText().trim();
            // ⚠ Cleared on every edit. A refusal that outlives the thing it refused is worse than no
            // message: the player fixes the handle, the red line stays, and they have no way to
            // tell whether it is stale or whether their fix was rejected too.
            trouble.setText("");
            String wanted = Hostname.sanitise(host.getText());
            // The preview is what the shell will actually print, so it shows the SANITISED
            // hostname rather than the raw field: a player typing "My Rig" should see what the
            // machine will make of it while they are still typing, not after they commit.
            preview.setText(Hostname.prompt(
                    handle[0].isBlank() ? "operator" : handle[0], wanted.isBlank() ? Hostname.DEFAULT : wanted));
            profile.settings().rigHostname = wanted.isBlank() ? Hostname.DEFAULT : wanted;

            String raw = host.getText() == null ? "" : host.getText().trim();
            String refusal = raw.isBlank() ? null : Hostname.problem(raw);
            hostNote.setText(refusal == null ? "" : refusal + " Using `" + wanted + "` instead.");
            hostNote.setManaged(refusal != null);
        };
        name.textProperty().addListener((o, was, now) -> sync.run());
        host.textProperty().addListener((o, was, now) -> sync.run());
        // ⚠ A TextField CONSUMES Enter, so the root handler never sees it. Without these two lines
        // Enter in the handle field does nothing at all — on the one pane where a player is typing
        // and will certainly press it. Firing the field's action lets the event through as a
        // completed edit, which the root handler then treats as Continue.
        name.setOnAction(event -> event.consume());
        host.setOnAction(event -> event.consume());
        sync.run();

        VBox body = column(
                title("Who is at the keyboard?"),
                blurb("Your handle names you on screen and in every log line the rig writes. The "
                        + "rig's own name goes after it, the way it does in any terminal."),
                fields(name, host),
                hostNote,
                preview,
                foot("`" + Hostname.SUFFIX.substring(1) + "` is mDNS — the name a machine answers "
                        + "to on the network it is plugged into with nobody having configured DNS. "
                        + "Your own machine has one. Letters, digits and hyphens, 63 characters at "
                        + "most: DNS's rules, not this game's."),
                trouble);

        return new Pane(body, () -> Views.validateHandle(handle[0]), trouble::setText, name::requestFocus);
    }

    /** The picture. Shows the generated silhouette the moment there is a handle to derive it from. */
    private static Pane picture(String[] handle, String[] avatar, Runnable[] refresh) {
        ImageView portrait = new ImageView();
        portrait.setFitWidth(PORTRAIT);
        portrait.setFitHeight(PORTRAIT);
        portrait.setClip(new Circle(PORTRAIT / 2, PORTRAIT / 2, PORTRAIT / 2));

        Circle frame = new Circle(PORTRAIT / 2 + 4);
        frame.getStyleClass().add("es-face-ring");
        StackPane plate = new StackPane(frame, portrait);
        plate.setMinSize(PORTRAIT + 8, PORTRAIT + 8);
        plate.setPrefSize(PORTRAIT + 8, PORTRAIT + 8);
        plate.setMaxSize(PORTRAIT + 8, PORTRAIT + 8);

        Label state = new Label();
        state.getStyleClass().add("es-setup-foot");

        refresh[0] = () -> {
            portrait.setImage(Avatar.image(avatar[0], handle[0]));
            state.setText(avatar[0].isEmpty() ? "Generated from your handle." : "Your picture.");
        };

        BreachView.Chip choose = new BreachView.Chip("Choose a picture", "es-setup-action");
        BreachView.Chip clear = new BreachView.Chip("Use the generated one", "es-setup-action");
        choose.onInvoke(() -> AvatarChooser.choose(
                plate.getScene() == null ? null : plate.getScene().getWindow(), handle[0], encoded -> {
                    avatar[0] = encoded;
                    refresh[0].run();
                }));
        clear.onInvoke(() -> {
            avatar[0] = "";
            refresh[0].run();
        });

        VBox body = column(
                plate,
                state,
                title("Pick a face"),
                blurb("Your own file dialog, then crop and zoom. The picture is stored WITH the "
                        + "character rather than as a link to the file you picked, so it travels "
                        + "with the save and the game never reads that location again."),
                Ui.row(UiTokens.SPACE_3, choose, clear),
                foot("With none set you get a silhouette generated from your handle, breaking up "
                        + "under static. It is different for every handle, so it is already yours."));

        return new Pane(body, () -> null, text -> {}, refresh[0]);
    }

    /** The palette, chosen from six live previews rather than from six words. */
    private static Pane appearance(ClientProfile profile, ThemeManager themes) {
        javafx.scene.layout.TilePane grid = new javafx.scene.layout.TilePane();
        grid.setHgap(UiTokens.SPACE_4);
        grid.setVgap(UiTokens.SPACE_4);
        grid.setPrefColumns(3);
        grid.setAlignment(Pos.CENTER);
        grid.setTileAlignment(Pos.TOP_CENTER);
        // ⚠ Uniform cells, stated explicitly. A TilePane sizes its grid from the LARGEST child, and
        // the selected tile carries a border the others do not — so without this the two rows of
        // swatches sat on different column centres and the selection appeared to nudge the grid.
        grid.setPrefTileWidth(SWATCH_TILE);
        grid.setPrefTileHeight(114);
        // ⚠ prefColumns is a HINT a TilePane ignores when it has room, so the width is what actually
        // holds three columns — and it has to include the gaps. Stated too narrow it wraps to two
        // columns, too wide and it takes four: six swatches in a row of four and a row of two reads
        // as a wrapped list rather than as a grid.
        grid.setMaxWidth(3 * SWATCH_TILE + 2 * UiTokens.SPACE_4);

        List<Runnable> restyle = new ArrayList<>();
        for (ThemeId id : ThemeId.selectable()) {
            VBox tile = swatch(id);
            Runnable mark = () -> {
                boolean on = themes.current() == id;
                tile.getStyleClass().remove("es-setup-swatch-on");
                if (on) {
                    tile.getStyleClass().add("es-setup-swatch-on");
                }
            };
            restyle.add(mark);
            tile.setOnMouseClicked(event -> {
                themes.select(id);
                restyle.forEach(Runnable::run);
            });
            tile.setFocusTraversable(true);
            tile.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                    event.consume();
                    themes.select(id);
                    restyle.forEach(Runnable::run);
                }
            });
            Cursors.shared().clickable(tile);
            grid.getChildren().add(tile);
        }
        restyle.forEach(Runnable::run);

        VBox body = column(
                title("How should it look?"),
                blurb("Every palette is the same deck. One stylesheet owns the layout, the "
                        + "hairlines and the motion, so no skin can hide or soften a number."),
                grid,
                foot("\"Deck — high visibility\" raises body text to WCAG AAA and makes every "
                        + "hairline visible. It is an accessibility floor rather than a style, and "
                        + "nothing else about the client changes."));

        return new Pane(body, () -> null, text -> {}, () -> restyle.forEach(Runnable::run));
    }

    /** Pointer, motion and text size — the three that change whether the deck is usable at all. */
    private static Pane accessibility(ClientProfile profile, ThemeManager themes, Actions actions) {
        VBox pointer = choices(
                "POINTER",
                CursorSkin.selectable().stream().map(CursorSkin::label).toList(),
                Math.max(
                        0,
                        CursorSkin.selectable()
                                .indexOf(CursorSkin.byId(profile.appearance().cursorSkin)
                                        .orElse(CursorSkin.SYSTEM))),
                index -> {
                    profile.appearance().cursorSkin =
                            CursorSkin.selectable().get(index).id();
                    // Through the theme manager: a drawn pointer is painted in the current
                    // palette's colours, and only it knows which stylesheets are live.
                    themes.refreshCursors();
                });

        // ⚠ The client's OWN list, not a hand-picked three. A short list looks tidier and lies: a
        // player already running at 110% saw "100%" highlighted, because the seeding loop fell back
        // to index zero when the stored value was not one of the three offered. Every scale the
        // client supports is offered, so the highlighted row is always the truth.
        int[] scales = io.github.stoicswe.eyeandsickle.client.ui.UiScale.PERCENTAGES;
        List<String> sizes = new ArrayList<>();
        int chosenScale = 0;
        int running = io.github.stoicswe.eyeandsickle.client.ui.UiScale.sanitise(profile.settings().uiScalePercent);
        for (int i = 0; i < scales.length; i++) {
            sizes.add(scales[i] + "%");
            if (scales[i] == running) {
                chosenScale = i;
            }
        }
        // ⚠ A STRIP, not a column. Eight rows of numbers is 290 points of height on the densest
        // pane in the assistant, and it pushed the copy off the bottom at the supported minimum
        // window size. It also reads better: these are one ordered scale, where the other two
        // columns are lists of alternatives, and a row says "more of the same thing" the way a
        // stack does not.
        VBox text = strip("TEXT SIZE", sizes, chosenScale, index -> {
            profile.settings().uiScalePercent = scales[index];
            profile.save();
            actions.applyPreview();
        });

        VBox motion = choices(
                "MOTION",
                List.of("Follow my system", "Reduce motion", "Full motion"),
                profile.settings().reducedMotionOverride == null ? 0 : profile.settings().reducedMotionOverride ? 1 : 2,
                index -> {
                    themes.setReducedMotionOverride(index == 0 ? null : index == 1);
                    profile.save();
                });

        HBox row = new HBox(UiTokens.SPACE_6 * 2, pointer, motion);
        row.setAlignment(Pos.TOP_CENTER);

        VBox body = column(
                title("Anything that would help?"),
                blurb("The pointer and how much moves follow your system unless you say "
                        + "otherwise. Text size is the game's own scale, separate from your "
                        + "desktop's on purpose."),
                row,
                text,
                // ⚠ Short, and it names nothing the player has not seen. The first version listed
                // the panel wipe, the caret blink, the greeble and the sweep bar — four internal
                // names for parts of a deck that does not exist until this screen finishes, on the
                // one prose surface in the client with no teaching layer behind it.
                foot("The system pointer is the default because your computer has already tuned it "
                        + "for your display and your eyesight. Reduce motion stops things moving; "
                        + "readouts keep updating, because that is information, not animation."));

        return still(body);
    }

    /** CL-4 / T-2, asked properly. */
    private static Pane teaching(ClientProfile profile) {
        List<String> levels = List.of("explain", "terms", "off");
        VBox picker = choices(
                "EXPLANATIONS",
                levels.stream().map(TEACHING_LABELS::get).toList(),
                Math.max(0, levels.indexOf(profile.settings().teachingLevel)),
                index -> {
                    profile.settings().teachingLevel = levels.get(index);
                    profile.save();
                });
        picker.setAlignment(Pos.CENTER);

        VBox body = column(
                title("How much should this explain?"),
                blurb("This game uses real command names and teaches what they actually do. "
                        + "\"Explain as I go\" adds a plain-language line the first time each term "
                        + "appears. \"Just the terms\" shows the term alone. \"Neither\" shows "
                        + "the bare word, with nothing marking it as one the game would explain."),
                picker,
                foot("The manual stays at all three: `man <term>` works whatever you pick, and "
                        + "`teach` changes this from inside the game whenever you like."));

        return still(body);
    }

    /** The summary. Everything it lists is a thing the player chose. */
    private static Pane ready(ClientProfile profile, String[] handle, String[] avatar) {
        GlowRing mark = new GlowRing(MARK_RADIUS);
        javafx.scene.layout.GridPane lines = new javafx.scene.layout.GridPane();
        lines.setHgap(UiTokens.SPACE_4);
        lines.setVgap(UiTokens.SPACE_2);
        lines.setAlignment(Pos.CENTER);
        lines.setMaxWidth(Region.USE_PREF_SIZE);

        VBox body = column(
                mark,
                title("Ready"),
                blurb("Nothing here is locked in. Every one of these is in Settings, and the rig "
                        + "will not think less of you for changing your mind."),
                lines);

        return new Pane(body, () -> null, text -> {}, () -> {
            lines.getChildren().clear();
            // ⚠ LABELS, not stored ids. Reading the fields raw printed `deck-hc`, `block-invert`
            // and `explain` back at a player who had just clicked "Deck — high visibility",
            // "Block (inverted)" and "Explain as I go" — §6's "one name per action, used
            // everywhere", broken on the one pane whose whole job is to repeat their choices.
            String[][] rows = {
                {"OPERATOR", Hostname.prompt(handle[0], profile.settings().rigHostname)},
                {"PICTURE", avatar[0].isEmpty() ? "generated from your handle" : "the one you chose"},
                {
                    "PALETTE",
                    ThemeId.byId(profile.appearance().themeId)
                            .map(ThemeId::label)
                            .orElse(profile.appearance().themeId)
                },
                {
                    "POINTER",
                    CursorSkin.byId(profile.appearance().cursorSkin)
                            .map(CursorSkin::label)
                            .orElse(profile.appearance().cursorSkin)
                },
                {"TEXT SIZE", profile.settings().uiScalePercent + "%"},
                {
                    "MOTION",
                    profile.settings().reducedMotionOverride == null
                            ? "follows your system"
                            : profile.settings().reducedMotionOverride ? "reduced" : "full"
                },
                {
                    "EXPLANATIONS",
                    TEACHING_LABELS.getOrDefault(profile.settings().teachingLevel, profile.settings().teachingLevel)
                },
            };
            for (int i = 0; i < rows.length; i++) {
                Label key = Ui.label(rows[i][0]);
                javafx.scene.layout.GridPane.setHalignment(key, javafx.geometry.HPos.RIGHT);
                lines.addRow(i, key, summary(rows[i][1]));
            }
        });
    }

    // ------------------------------------------------------------------ parts

    private static VBox column(Node... children) {
        VBox box = new VBox(UiTokens.SPACE_6, children);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-setup-title");
        return label;
    }

    private static Label blurb(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-setup-blurb");
        label.setWrapText(true);
        label.setMaxWidth(560);
        // ⚠ A wrapping Label's MINIMUM height is one line, so a pane squeezed against a short window
        // shrinks it to one line and ellipsises the rest. At the supported minimum (860 × 560) the
        // accessibility pane's blurb rendered as "The pointer and motion follow your system unless
        // you say otherwise. Tex..." — the paragraph was there, the room was not, and nothing said
        // so. Pinning the minimum to the preferred height makes the pane overflow instead, which the
        // scroller below turns into something the player can actually reach.
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return label;
    }

    private static Label foot(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-setup-foot");
        label.setWrapText(true);
        label.setMaxWidth(560);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return label;
    }

    /**
     * Asks before throwing the assistant away.
     *
     * <p>Skipped entirely on the first pane, where nothing has been decided yet and a confirmation
     * would just be a door that sticks.
     */
    private static boolean confirmAbandon() {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "The handle, the picture and everything else you have chosen here will be "
                        + "discarded, and no character will be created.",
                javafx.scene.control.ButtonType.CANCEL,
                javafx.scene.control.ButtonType.OK);
        confirm.setHeaderText("Stop setting up?");
        return confirm.showAndWait()
                .filter(button -> button == javafx.scene.control.ButtonType.OK)
                .isPresent();
    }

    private static Label link(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("es-setup-link", "es-focusable");
        label.setFocusTraversable(true);
        Cursors.shared().clickable(label);
        label.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                event.consume();
                label.getOnMouseClicked().handle(null);
            }
        });
        return label;
    }

    /**
     * The two identity fields, on a grid.
     *
     * <p>⚠ Two centred rows, not a grid, was the first attempt — and because the rows have different
     * natural widths, each centred independently and the two fields started nine pixels apart. A
     * column that does not line up reads as a rendering fault, not as a layout choice.
     */
    private static javafx.scene.layout.GridPane fields(Node handle, Node hostname) {
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(UiTokens.SPACE_4);
        grid.setVgap(UiTokens.SPACE_3);
        grid.setAlignment(Pos.CENTER);
        grid.setMaxWidth(Region.USE_PREF_SIZE);

        Label first = Ui.label("HANDLE");
        Label second = Ui.label("RIG NAME");
        javafx.scene.layout.GridPane.setHalignment(first, javafx.geometry.HPos.RIGHT);
        javafx.scene.layout.GridPane.setHalignment(second, javafx.geometry.HPos.RIGHT);
        grid.addRow(0, first, handle);
        grid.addRow(1, second, hostname);
        return grid;
    }

    private static Label summary(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("es-setup-summary");
        return label;
    }

    /** The same exclusive choice, laid out along a row. For an ordered scale rather than a list. */
    private static VBox strip(
            String heading, List<String> options, int selected, java.util.function.IntConsumer onPick) {

        VBox built = choices(heading, options, selected, onPick);
        Label heading0 = (Label) built.getChildren().getFirst();
        HBox line = new HBox(UiTokens.SPACE_2);
        line.setAlignment(Pos.CENTER_LEFT);
        // ⚠ Copied out of the column rather than rebuilt, so the two layouts cannot come to
        // different selection behaviour. Everything after the heading is an option row.
        line.getChildren()
                .addAll(built.getChildren().subList(1, built.getChildren().size()));
        // ⚠ A style class, not setMinWidth. `.es-setup-choice` carries `-fx-min-width: 168` so a
        // column of options lines up, and CSS wins over a programmatic minimum on the next
        // applyCss — eight 168-point cells is 1,500 points of row, which shoved the whole pane off
        // the side of the window at the supported minimum size. The narrow variant undoes it in the
        // stylesheet, where the original constraint lives.
        for (javafx.scene.Node option : line.getChildren()) {
            option.getStyleClass().add("es-setup-choice-tight");
        }
        VBox box = new VBox(UiTokens.SPACE_2, heading0, line);
        box.setAlignment(Pos.TOP_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * A column of exclusive options, drawn as rows rather than as a {@code ChoiceBox}.
     *
     * <p>A setup pane is asking a question, and a collapsed dropdown hides the answers behind a
     * click. §9 bans hidden UI for the deck for the same reason; on a screen with one question and
     * a whole window to put it in there is no argument for a menu at all.
     */
    private static VBox choices(
            String heading, List<String> options, int selected, java.util.function.IntConsumer onPick) {

        VBox box = new VBox(UiTokens.SPACE_2);
        box.setAlignment(Pos.TOP_LEFT);
        box.getChildren().add(Ui.label(heading));

        List<Label> rows = new ArrayList<>();
        int[] current = {selected};
        Runnable restyle = () -> {
            for (int i = 0; i < rows.size(); i++) {
                Label row = rows.get(i);
                row.getStyleClass().remove("es-setup-choice-on");
                if (i == current[0]) {
                    row.getStyleClass().add("es-setup-choice-on");
                }
            }
        };

        for (int i = 0; i < options.size(); i++) {
            int index = i;
            Label row = new Label(options.get(i));
            row.getStyleClass().addAll("es-setup-choice", "es-focusable");
            row.setFocusTraversable(true);
            Cursors.shared().clickable(row);
            Runnable pick = () -> {
                current[0] = index;
                restyle.run();
                onPick.accept(index);
            };
            row.setOnMouseClicked(event -> pick.run());
            row.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                    event.consume();
                    pick.run();
                }
            });
            rows.add(row);
            box.getChildren().add(row);
        }
        restyle.run();
        return box;
    }

    /**
     * A palette as a small picture of the deck it produces.
     *
     * <p>⚠ The swatch's colours are literals in the stylesheet, one block per theme, and they have
     * to be: a tile is rendered under the palette that is <em>currently</em> live, so a looked-up
     * {@code -es-} token would paint all six tiles identically in whichever theme is on. This is the
     * one place in the client where a colour is deliberately not the current palette's, and §10
     * criterion 2 is satisfied the same way the power-on splash satisfies it — the colours are in
     * {@code theme.css}, they are simply not resolved from the palette.
     */
    private static VBox swatch(ThemeId id) {
        Region strip = bar("es-swatch-strip", 8);
        Region command = bar("es-swatch-strip", 6);

        Region accent = new Region();
        accent.getStyleClass().add("es-swatch-accent");
        accent.setMinSize(20, 3);
        accent.setPrefSize(20, 3);
        accent.setMaxSize(20, 3);

        StackPane left = new StackPane(accent);
        left.getStyleClass().add("es-swatch-pane");
        StackPane.setAlignment(accent, Pos.BOTTOM_LEFT);
        StackPane.setMargin(accent, new Insets(0, 0, 5, 5));
        HBox.setHgrow(left, Priority.ALWAYS);

        Region right = new Region();
        right.getStyleClass().add("es-swatch-pane");
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox panes = new HBox(1, left, right);
        VBox.setVgrow(panes, Priority.ALWAYS);
        // ⚠ Vgrow alone does nothing without this. A layout constraint grows a child only up to its
        // maximum, and an HBox's default maximum is its preferred size.
        panes.setMaxHeight(Double.MAX_VALUE);

        VBox screen = new VBox(1, strip, panes, command);
        screen.getStyleClass().add("es-swatch-screen");
        screen.setMinSize(116, 74);
        screen.setPrefSize(116, 74);
        screen.setMaxSize(116, 74);

        Label name = new Label(id.label());
        name.getStyleClass().add("es-setup-swatch-name");
        name.setWrapText(true);
        // ⚠ prefWidth, not just maxWidth. A wrapping Label computes its preferred width from the
        // unwrapped text, so a maximum alone leaves it one line long and ellipsised — which is how
        // "Deck — high visibility" first rendered as "Deck — high ...".
        name.setPrefWidth(124);
        name.setMaxWidth(124);
        name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        // Two lines' worth, always: one palette's name wraps and the rest do not, and a tile a line
        // shorter than its neighbours drags the row out of alignment.
        name.setMinHeight(28);

        VBox tile = new VBox(UiTokens.SPACE_2, screen, name);
        tile.setAlignment(Pos.TOP_CENTER);
        tile.getStyleClass().addAll("es-setup-swatch", "es-swatch-" + id.id(), "es-focusable");
        return tile;
    }

    private static Region bar(String styleClass, double height) {
        Region region = new Region();
        region.getStyleClass().add(styleClass);
        region.setMinHeight(height);
        region.setPrefHeight(height);
        region.setMaxHeight(height);
        return region;
    }
}
