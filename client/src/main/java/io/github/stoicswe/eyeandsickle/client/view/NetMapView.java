package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.netmap.NetGraph;
import io.github.stoicswe.eyeandsickle.client.ui.netmap.NetLegend;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Note;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetFolder;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The map window — the network in two views, and every network action reachable from either.
 *
 * <h2>Two views, one map, one toggle</h2>
 *
 * The brief asks for both surfaces because they answer different questions and neither answers the
 * other's. The <b>graph</b> shows shape: what is adjacent to what, how far from the vantage, where a
 * bridge leaves the server. The <b>list</b> shows everything: a generated server carries up to fifty
 * machines, and there is a density at which a picture stops being readable and a table does not.
 * They are fed from a single {@link NetMap} read per refresh — one call, one instance, handed to
 * both — so it is not possible for the two to disagree about what has been discovered.
 *
 * <h2>The server strip never scrolls away</h2>
 *
 * "The graph always shows the server the player is currently connected to" is a requirement, so it
 * is met structurally rather than by remembering to put it somewhere. The strip and the controls are
 * fixed chrome inside the panel and only the data area scrolls — the same reasoning that made the
 * deck's compute readout a cell in the top strip rather than a widget on the desk: chrome has no
 * z-order to lose and nothing to hide behind.
 *
 * <p>⚠ The data area scrolls <b>horizontally</b>, which is the one place this panel departs from the
 * house rule that a deck panel reflows to its width. Both views are character-cell textures: a
 * fourteen-column node cell and a fixed-width table cannot reflow without ceasing to line up, and a
 * table whose columns move is a table a player cannot read down. So the scroll is on the data area
 * only, and the panel around it still reflows.
 *
 * <h2>Bound to the port, and to nothing else</h2>
 *
 * Every number, name and state on this panel arrives through {@link GameSession}. The view never
 * asks the solo engine anything and holds no rule of its own — it does not decide whether a sweep is
 * affordable, whether a foothold exists, or what a node's type is. When an action is refused, the
 * refusal printed is the one the rules gave. That is Invariant <b>I14</b> at the panel where it
 * would be easiest to break, and the consequence worth stating is the same one the breach window
 * has: this window works unchanged against a home server, because it cannot tell the difference.
 *
 * <h2>The subscription is closed</h2>
 *
 * {@code MoreViews} discards the {@link AutoCloseable} that {@code onChange} returns, in five
 * windows, so a closed panel keeps being called back for the life of the session. This one holds the
 * handle and closes it — together with the graph's animation subscription — when the node leaves the
 * scene, which is what {@code DeskManager.close} does to it. Minimising only sets a window
 * invisible and leaves it in the desk, so a minimised map keeps updating, which is correct: it is
 * still open.
 */
public final class NetMapView {

    private NetMapView() {}

    /**
     * Which surface is showing.
     *
     * <p>The toggle is this enum and nothing else — there is no second flag, no visibility check
     * used as state and no "which was I showing" derived from the scene graph. That is what makes
     * the toggle testable without a toolkit, and it is why {@link #apply} is the only place either
     * view's visibility is set.
     */
    enum Display {
        GRAPH,
        LIST,
        FOLDERS;

        Display toggled() {
            return values()[(ordinal() + 1) % values().length];
        }

        boolean showsGraph() {
            return this == GRAPH;
        }

        boolean showsList() {
            return this == LIST;
        }

        boolean showsFolders() {
            return this == FOLDERS;
        }

        /**
         * How this view's control reads when {@code active} is the one showing.
         *
         * <p>Brackets, not colour. §4.4's rule for the graph — weight first, the grey ramp second —
         * applies to its chrome too, and a toggle whose only signal is a text fill is invisible in a
         * greyscale capture and to anyone reading the panel through a screen reader.
         */
        String control(Display active) {
            return this == active ? "[ " + name() + " ]" : "  " + name() + "  ";
        }
    }

    /**
     * Builds the window.
     *
     * <p>{@code EyeAndSickleClient} calls this once per open, so everything below is per-instance
     * state; nothing here is static and nothing survives a close.
     */
    public static Region create(GameSession session) {
        return create(session, new BreachArming());
    }

    /**
     * Builds the window, with a handle on the breach window's aim.
     *
     * <p>The map is where a player <em>finds</em> a machine and the breach window is where they
     * attack one, and until this existed the journey between the two was: read an address off the
     * graph, open another window, find the same address in a list of a dozen. {@code BREACH} closes
     * that — it arms the breach window at the selected machine and raises it, and the player's next
     * act is one deliberate press on a control that says what it will cost.
     *
     * <p>⚠ It arms; it does not attack. See {@link BreachArming}: a breach reserves compute for the
     * whole attempt and aborting is a sanctioned outcome rather than a refund, so the click that
     * chooses a target and the click that commits to one must not be the same click — least of all
     * on a graph, where the cells move whenever a sweep lands.
     */
    public static Region create(GameSession session, BreachArming arming) {
        VBox root = new VBox(UiTokens.SPACE_5);
        root.getStyleClass().addAll("es-netmap", "es-body-pad");

        // ---------------------------------------------------------------- chrome
        Label strip = new Label();
        strip.getStyleClass().add("es-netmap-server");

        Display[] display = {Display.GRAPH};
        String[] selected = {""};
        Runnable[] repaint = new Runnable[1];

        // Declared before either view is built, because NetGraph takes its node handler at
        // construction and there is no setter to add one afterwards. `repaint[0]` is not read until
        // something is clicked, by which time it is assigned.
        java.util.function.Consumer<String> select = address -> {
            selected[0] = address == null ? "" : address;
            repaint[0].run();
        };

        String[] selectedFolder = {""};

        NetHostList list = new NetHostList();
        list.setOnNode(select);
        NetGraph graph = new NetGraph(select);
        NetLegend legend = new NetLegend();
        NetFolderList folders = new NetFolderList();
        folders.setOnNode(select);
        folders.setOnFolder(folderId -> {
            // A second click on the folder already selected clears it. Without that there is no way
            // back to "no folder chosen" except picking a different one, and NEW FOLDER at the top
            // level would become unreachable the moment anything was selected.
            selectedFolder[0] = selectedFolder[0].equals(folderId) ? "" : folderId;
            repaint[0].run();
        });

        BreachView.Chip graphControl = control(Display.GRAPH.control(Display.GRAPH));
        BreachView.Chip listControl = control(Display.LIST.control(Display.GRAPH));
        BreachView.Chip folderControl = control(Display.FOLDERS.control(Display.GRAPH));
        graphControl.setAccessibleText("Show the network as a graph.");
        listControl.setAccessibleText("Show the network as a list of every discovered machine.");
        folderControl.setAccessibleText("Show how you have filed what you have found.");

        // One sweep control: a key and its three sensitivities, in ascending order. All three are
        // always offered. Hiding the two the player has not bought would be the client evaluating a
        // gate (docs/client/04 §3.4, I14) — and the refusal the rules give when an unowned tool is
        // run is a better teacher than an absent control, because it names what is missing.
        //
        // ⚠ These carry `es-netmap-action`, NOT the `es-netmap-control` the two view toggles use,
        // and the distinction is a bug fix rather than a flourish. A toggle has two states and paints
        // the inactive one in -es-dim-1; a sweep control has no such state and so sat permanently in
        // the colour this panel's own vocabulary uses for "not the one in force". Three buttons that
        // never brighten, in a row next to two that do, read as disabled — and were reported as
        // disabled. An action is not a toggle and must not borrow a toggle's off state.
        //
        // Each names its price, for the same reason `sweep -n` prints one: a control whose cost is
        // invisible until you press it is a control a cautious player does not press.
        BreachView.Chip sweepBase = action("BASE 2C");
        BreachView.Chip sweepWide = action("WIDE 5C");
        BreachView.Chip sweepDeep = action("DEEP 9C");
        sweepBase.setAccessibleText(
                "Run the base network sweep from the current vantage. Two cycles, about twenty "
                        + "seconds, and loud while it runs. It costs no ethecoin.");
        sweepWide.setAccessibleText(
                "Run the wide network sweep — the same reach, more sensitivity. Five cycles, about "
                        + "forty-five seconds, and louder. It costs no ethecoin.");
        sweepDeep.setAccessibleText(
                "Run the deep network sweep — the same reach, most sensitivity. Nine cycles, about "
                        + "ninety seconds, and loudest. It costs no ethecoin.");
        HBox sweepGroup = Ui.row(UiTokens.SPACE_2,
                Ui.label("Sweep"), sweepBase, sweepWide, sweepDeep);

        HBox controls = Ui.row(UiTokens.SPACE_3,
                graphControl, listControl, folderControl, Ui.spacer(), sweepGroup);

        // ---------------------------------------------------------------- selection
        KeyValue selection = KeyValue.of("Selected", "NONE");
        Label detail = Ui.small("");
        detail.setWrapText(true);
        BreachView.Chip connect = action("CONNECT");
        BreachView.Chip download = action("DOWNLOAD");
        BreachView.Chip breach = action("BREACH");
        BreachView.Chip fileHere = action("FILE HERE");
        BreachView.Chip unfile = action("UNFILE");
        connect.setAccessibleText("Move the vantage to the selected machine. Requires a foothold.");
        download.setAccessibleText("Recover a document from the selected machine.");
        breach.setAccessibleText(
                "Aim the breach window at the selected machine and open it. Nothing is spent until "
                        + "you start the breach there.");
        fileHere.setAccessibleText("Put the selected machine into the selected folder.");
        unfile.setAccessibleText("Take the selected machine out of whatever folder it is in.");
        HBox selectionRow = Ui.row(
                UiTokens.SPACE_3, selection, breach, connect, download, fileHere, unfile);

        // ---------------------------------------------------------------- filing
        //
        // Its own strip, shown only with the folder view. The alternative — folding these into the
        // row above — was tried on paper and puts six controls on one line, of which the meaning of
        // three depends on which of two selections is live. A player cannot read that.
        KeyValue folderSelection = KeyValue.of("Folder", "TOP LEVEL");
        TextField folderName = new TextField();
        folderName.setPromptText("folder name");
        folderName.setPrefColumnCount(18);
        folderName.setAccessibleText("Name for a new folder, or a new name for the selected one.");
        BreachView.Chip newFolder = action("NEW");
        BreachView.Chip renameFolder = action("RENAME");
        BreachView.Chip removeFolder = action("REMOVE");
        BreachView.Chip toTop = action("TO TOP");
        newFolder.setAccessibleText(
                "Make a folder with the typed name, inside the selected folder or at the top level.");
        renameFolder.setAccessibleText("Rename the selected folder to the typed name.");
        removeFolder.setAccessibleText(
                "Remove the selected folder. What was inside it moves up one level; nothing is lost.");
        toTop.setAccessibleText("Move the selected folder back out to the top level.");
        HBox folderRow = Ui.row(UiTokens.SPACE_3,
                folderSelection, folderName, newFolder, renameFolder, removeFolder, toTop);

        // ---------------------------------------------------------------- activity and notices
        Label activity = new Label();
        activity.getStyleClass().add("es-netmap-layer");

        VBox reader = new VBox(UiTokens.SPACE_2);
        Label readerTitle = Ui.label("");
        Label readerBody = Ui.small("");
        readerBody.setWrapText(true);
        reader.getChildren().addAll(readerTitle, readerBody);

        // ---------------------------------------------------------------- the data area
        //
        // Each view owns its own scroll. See the class comment: these are character-cell textures
        // and neither can reflow, so the scroll goes here and the panel around it still does.
        ScrollPane graphScroll = scroller(graph);
        ScrollPane listScroll = scroller(list);
        ScrollPane folderScroll = scroller(folders);
        StackPane area = new StackPane(graphScroll, listScroll, folderScroll);
        VBox.setVgrow(area, Priority.ALWAYS);

        root.getChildren().addAll(
                strip, controls, selectionRow, folderRow, detail, activity, area, legend, reader);

        // ---------------------------------------------------------------- wiring
        Runnable applyDisplay = () -> {
            graphControl.setText(Ui.upper(Display.GRAPH.control(display[0])));
            listControl.setText(Ui.upper(Display.LIST.control(display[0])));
            folderControl.setText(Ui.upper(Display.FOLDERS.control(display[0])));
            mark(graphControl, display[0].showsGraph());
            mark(listControl, display[0].showsList());
            mark(folderControl, display[0].showsFolders());
            visible(graphScroll, display[0].showsGraph());
            visible(listScroll, display[0].showsList());
            visible(folderScroll, display[0].showsFolders());
            // The legend names the graph's glyph vocabulary and nothing else, so it goes with it.
            visible(legend, display[0].showsGraph());
            // The filing strip is only meaningful beside the tree it acts on. Hidden rather than
            // disabled: a disabled control still asks to be understood, and there is nothing to
            // understand about REMOVE while a graph is on screen.
            visible(folderRow, display[0].showsFolders());
        };

        graphControl.onInvoke(() -> {
            display[0] = Display.GRAPH;
            applyDisplay.run();
        });
        listControl.onInvoke(() -> {
            display[0] = Display.LIST;
            applyDisplay.run();
        });
        folderControl.onInvoke(() -> {
            display[0] = Display.FOLDERS;
            applyDisplay.run();
        });

        // ⚠ Refusals are NOT printed on this panel any more, and the strip that used to hold them
        // is gone. Every intent below goes through the session, which writes a refusal to the rig's
        // log on the way back — and the notification system is "the log, filtered", so it surfaces
        // there as an error toast and stays permanently readable in the log window.
        //
        // The strip had three problems and the third is the one that mattered: it duplicated a
        // surface the client already has; it put the message at the top of a panel whose controls
        // may be at the bottom; and a refusal was the one class of message that never reached the
        // journal at all. A player could be told "not enough cycles", look away, and have no way to
        // find out what they had been told.
        java.util.function.Consumer<GameSession.Outcome> report = outcome -> {};

        sweepBase.onInvoke(() -> report.accept(session.sweep("")));
        sweepWide.onInvoke(() -> report.accept(session.sweep("--wide")));
        sweepDeep.onInvoke(() -> report.accept(session.sweep("--deep")));

        breach.onInvoke(() -> {
            if (selected[0].isBlank()) {
                return;
            }
            // The `node:` prefix is the rules' own target id (see Targets.available). Built here
            // rather than looked up because the map holds an address and the breach window holds
            // ids, and one of the two has to translate — but the SHAPE is the rules', which is why
            // the armed id is handed straight to beginBreach without a second translation.
            arming.arm("node:" + selected[0]);
            arming.open();
        });

        connect.onInvoke(() -> {
            if (selected[0].isBlank()) {
                return;
            }
            report.accept(session.connectTo(selected[0]));
        });
        download.onInvoke(() -> {
            if (selected[0].isBlank()) {
                return;
            }
            report.accept(session.download(selected[0]));
        });

        // ---------------------------------------------------------------- filing intents
        //
        // Every one of these hands the port a selection and prints whatever came back. None of them
        // checks whether the operation is legal first: whether a name collides, whether a folder can
        // hold another level, whether an address has been discovered are all rules questions, and a
        // view that pre-empted them would be a second implementation that eventually disagreed. The
        // only guards here are "is anything selected", which is a question about this panel.

        fileHere.onInvoke(() -> {
            // Both, for the reason spelled out at the visibility guard: a blank folder id means
            // unfile, so acting on a half-made selection here would do the opposite of the label.
            if (selected[0].isBlank() || selectedFolder[0].isBlank()) {
                return;
            }
            report.accept(session.fileNode(selected[0], selectedFolder[0]));
        });
        unfile.onInvoke(() -> {
            if (selected[0].isBlank()) {
                return;
            }
            report.accept(session.fileNode(selected[0], ""));
        });

        Runnable create = () -> {
            GameSession.Outcome outcome = session.createFolder(selectedFolder[0], folderName.getText());
            if (outcome.succeeded()) {
                folderName.clear();
            }
            report.accept(outcome);
        };
        newFolder.onInvoke(create);
        renameFolder.onInvoke(() -> {
            if (selectedFolder[0].isBlank()) {
                return;
            }
            GameSession.Outcome outcome = session.renameFolder(selectedFolder[0], folderName.getText());
            if (outcome.succeeded()) {
                folderName.clear();
            }
            report.accept(outcome);
        });
        removeFolder.onInvoke(() -> {
            if (selectedFolder[0].isBlank()) {
                return;
            }
            GameSession.Outcome outcome = session.removeFolder(selectedFolder[0]);
            if (outcome.succeeded()) {
                // The folder is gone, so the selection pointing at it is too. Leaving it would
                // arm RENAME and REMOVE against an id the rules would now refuse, and the refusal
                // would read as a bug rather than as the stale pointer it is.
                selectedFolder[0] = "";
            }
            report.accept(outcome);
        });
        toTop.onInvoke(() -> {
            if (selectedFolder[0].isBlank()) {
                return;
            }
            report.accept(session.moveFolder(selectedFolder[0], ""));
        });
        // Enter in the name field is the same action as NEW — the shape every dialog in this client
        // already has, and the one a player types without being told.
        folderName.setOnAction(event -> create.run());

        repaint[0] = () -> {
            NetMap map = session.net();

            // One read, one instance, both views. The two surfaces cannot disagree about what has
            // been discovered because there is nothing for them to disagree from.
            graph.setMap(map);
            graph.setSelected(selected[0]);
            list.setMap(map);
            list.setSelected(selected[0]);
            String header = NetText.serverStrip(map);
            strip.setText(header);
            // Read aloud, a run of padding is silence. The column gaps become sentence breaks so a
            // screen reader says four facts rather than one long number.
            strip.setAccessibleText(header.replaceAll("\\s{2,}", ". "));

            // The selection survives a refresh only while the map still carries it. A machine the
            // player selected and then lost sight of falls back to NONE rather than leaving two
            // controls pointing at an address the rules would now refuse.
            Optional<Sighting> chosen = selected[0].isBlank()
                    ? Optional.<Sighting>empty()
                    : map.at(selected[0]);
            selection.set(chosen.map(Sighting::address).orElse("NONE"));
            detail.setText(chosen.map(NetHostList::describe).orElse(
                    "Pick a machine in any view. All three select the same thing."));
            visible(connect, chosen.isPresent());
            visible(download, chosen.map(Sighting::documentAvailable).orElse(false));
            // Offered for anything but the player's own rig. Whether the machine can actually be
            // attempted — compute, an existing foothold, a gate — is the rules' answer, and this
            // panel does not pre-empt it (C4): the breach window shows the target with the rules'
            // own verdict beside it, which is a better teacher than a control that is simply absent.
            visible(breach, chosen.map(s -> !s.vantage()).orElse(false));

            // ---- filing
            //
            // The tree is read every repaint like everything else on this panel; a selection that
            // no longer names a live folder falls back to the top level rather than leaving three
            // controls armed against an id the rules would refuse.
            List<NetFolder> tree = session.folders();
            boolean folderLives = tree.stream().anyMatch(f -> f.folderId().equals(selectedFolder[0]));
            if (!folderLives) {
                selectedFolder[0] = "";
            }
            folders.setTree(tree, session.unfiledNodes());
            folders.setSelectedFolder(selectedFolder[0]);
            folders.setSelectedNode(selected[0]);

            String folderLabel = tree.stream()
                    .filter(f -> f.folderId().equals(selectedFolder[0]))
                    .map(NetFolder::path)
                    .findFirst()
                    .orElse("TOP LEVEL");
            folderSelection.set(folderLabel);
            // ⚠ FILE HERE needs BOTH selections, and the guard is real rather than cosmetic: a
            // blank folder id is how the port spells "unfile", so a FILE HERE offered with no
            // folder chosen would quietly do the opposite of what it says. UNFILE needs only the
            // machine, which is why the two are separate controls and not one toggle.
            visible(fileHere, chosen.isPresent() && !selectedFolder[0].isBlank());
            visible(unfile, chosen.isPresent());
            visible(renameFolder, !selectedFolder[0].isBlank());
            visible(removeFolder, !selectedFolder[0].isBlank());
            visible(toTop, !selectedFolder[0].isBlank());

            String work = sweepInProgress(session);
            activity.setText(work);
            visible(activity, !work.isEmpty());

            List<NetDocument> documents = session.documents();
            boolean any = !documents.isEmpty();
            visible(reader, any);
            if (any) {
                NetDocument latest = documents.getLast();
                readerTitle.setText(Ui.upper(latest.title()));
                readerBody.setText(String.join("\n", NetText.documentBody(latest.documentId())));
            }
        };

        applyDisplay.run();
        report.accept(null);
        repaint[0].run();

        AutoCloseable subscription = session.onChange(s -> repaint[0].run());
        closeOnDetach(root, subscription, graph);
        return root;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The running sweep, as one line, or empty when nothing is running.
     *
     * <p>Matched on the facility the rules stamp on the task rather than on a label, because a label
     * is prose and prose gets rewritten. The {@code sweep} fallback is there because the exact
     * facility string is the engine's to choose and this panel should not go blank if it chooses a
     * different one — an activity readout that silently shows nothing is worse than one that is
     * occasionally too generous about what it matches.
     */
    private static String sweepInProgress(GameSession session) {
        for (GameSession.RunningTask task : session.tasks()) {
            boolean mine = "net".equalsIgnoreCase(task.facility())
                    || names(task.id()).contains("sweep")
                    || names(task.label()).contains("sweep");
            if (!mine) {
                continue;
            }
            StringBuilder out = new StringBuilder(Ui.upper(task.label()));
            if (!task.indeterminate()) {
                out.append(" · ").append(Math.round(task.progress() * 100)).append("%");
                out.append(" · ").append(task.remaining().toSeconds()).append("S LEFT");
            }
            if (task.cycles() > 0) {
                out.append(" · ").append(task.cycles()).append(" CYCLES HELD");
            }
            return out.toString();
        }
        return "";
    }

    /** Lowercased and never null — a readout must not be able to throw out of a repaint. */
    private static String names(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /**
     * A control.
     *
     * <p>{@code BreachView.Chip} rather than a second Label-based control: its class comment asks
     * for exactly that, and the alternative is two hand-built keyboard routes that drift. The
     * {@code es-breach-chip} class it carries is a misnomer here and paints nothing — it is only
     * declared under {@code .es-cost-strip} — so the appearance comes from
     * {@code .es-netmap .es-netmap-control}, which the integrator adds with the rest of §4.9's
     * block.
     */
    private static BreachView.Chip control(String text) {
        return new BreachView.Chip(text, "es-netmap-control");
    }

    /**
     * A control that <em>does</em> something, as opposed to one that selects a view.
     *
     * <p>⚠ <b>The separate class is a bug fix and must not be collapsed back into
     * {@link #control}.</b> A view toggle has two states and paints its off state in {@code -es-dim-1};
     * an action has no off state, so putting one in the toggle's class parked it permanently in the
     * colour this panel uses to mean "not the one in force". Three sweep buttons that never brighten,
     * sitting next to two toggles that do, read as greyed out — and were reported as greyed out and
     * unpressable, despite always having been pressable. The stylesheet gives this class the ordinary
     * text fill plus a hover and a pressed state, so the affordance is carried by response to the
     * pointer rather than by the player guessing.
     */
    private static BreachView.Chip action(String text) {
        return new BreachView.Chip(text, "es-netmap-action");
    }

    /** Marks a control as the one currently in force. Paired with the bracket, never alone. */
    private static void mark(Node node, boolean on) {
        node.getStyleClass().remove("es-netmap-control-on");
        if (on) {
            node.getStyleClass().add("es-netmap-control-on");
        }
    }

    /**
     * A scroller for a character-cell texture.
     *
     * <p>{@code setFitToWidth(false)} is the whole point and is the opposite of what every other
     * panel in this client wants: fitting to width would squeeze a fixed-width grid and shear every
     * column in it. The horizontal bar appearing is the correct outcome, not a layout bug.
     */
    private static ScrollPane scroller(Region content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    /**
     * Releases the change subscription and the graph's animation when the panel leaves the scene.
     *
     * <p>⚠ Only on a transition <em>away</em> from a scene, and only after having been in one. A
     * node's scene is null before it is added as well as after it is removed, so acting on "scene is
     * null" alone would tear the panel down during its own construction.
     *
     * <p>{@code DeskManager.close} removes a window's frame from the desk, which is what makes the
     * scene go null; {@code setMinimized} only flips visibility and leaves the frame in place, so a
     * minimised window keeps its subscription — correct, because it is still open and its readouts
     * must be current the moment it is restored.
     */
    private static void closeOnDetach(Region root, AutoCloseable subscription, NetGraph graph) {
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
            graph.dispose();
            try {
                subscription.close();
            } catch (Exception ignored) {
                // A listener registry that refuses to forget a listener is not something this panel
                // can do anything about, and throwing out of a scene-graph listener would take the
                // whole close with it.
            }
        });
    }

    /**
     * Shows or hides a node and takes it out of the layout with it.
     *
     * <p>{@code setManaged} matters as much as {@code setVisible}: a merely invisible child still
     * claims its height, so a hidden legend would leave a band of empty panel above the reader for
     * as long as the list view was showing.
     */
    private static void visible(Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }
}
