package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.netmap.NetGraph;
import io.github.stoicswe.eyeandsickle.client.ui.netmap.NetLegend;
import io.github.stoicswe.eyeandsickle.client.ui.netmap.ServerTabs;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetFolder;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
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
 * <h2>The sweep ladder shows the rules' verdict, and does not compute one</h2>
 *
 * All three rungs are always <b>offered</b>. The two the player has not bought read as
 * {@code LOCKED}, dimmed, with a tooltip naming the tool and its price and stating what the tier
 * buys over the base — {@code docs/client/05} §5's rule that a gate is never a generic "locked".
 *
 * <p>⚠ That verdict arrives through {@link GameSession#sweepOptions()} and is <b>never</b> worked
 * out here. A panel that decided a sweep was locked by looking for an item id in the inventory would
 * be a second implementation of {@code NetRules.owns} living in a view, and the day the rule grows a
 * second condition the window is the one that lies. A rung the port does not mention is drawn as it
 * always was — offered, with the rules free to refuse it — because an absent verdict is not a locked
 * one. See {@link #paintSweepLadder}, which also records why the control is marked rather than
 * {@code setDisable}d.
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
    /**
     * What the map's right-click menu can do besides selecting.
     *
     * <p>An interface rather than two {@code Consumer}s because the two actions are the same
     * decision — "act on this machine" — and a view that took them separately would let a caller
     * wire one and forget the other, producing a menu with a dead entry.
     */
    public interface NodeActions {

        /** Open a shell on the machine. The rules refuse without a foothold. */
        void openShell(String address);

        /** Aim the breach window at it and raise it. Arms; never attacks. */
        void breach(String address);

        /**
         * Open the port scanner on it.
         *
         * <p>⚠ An action rather than a window in the catalogue: a scan is a thing done *to* a
         * machine, so a scanner opened with no target would be a window asking "scan what?". See
         * {@code PortScanView}.
         */
        void portScan(String address);

        /** Open the intelligence file on it. Disabled in the menu when there is nothing on file. */
        void info(String address);

        /** A no-op set, so a caller that has no desk still gets a working panel. */
        static NodeActions none() {
            return new NodeActions() {
                @Override
                public void openShell(String address) {}

                @Override
                public void breach(String address) {}

                @Override
                public void portScan(String address) {}

                @Override
                public void info(String address) {}
            };
        }
    }

    public static Region create(GameSession session, BreachArming arming) {
        return create(session, arming, NodeActions.none());
    }

    /**
     * Builds the window with a right-click menu on every machine.
     *
     * <p>The menu is where CONNECT and BREACH live now. Both were already reachable from the
     * selection row, and both stay there — this is a second route, not a replacement, because a
     * context menu is discoverable only by people who try right-clicking and a control strip is
     * discoverable by everybody.
     */
    public static Region create(GameSession session, BreachArming arming, NodeActions actions) {
        VBox root = new VBox(UiTokens.SPACE_5);
        root.getStyleClass().addAll("es-netmap", "es-body-pad");

        // ---------------------------------------------------------------- chrome
        Label strip = new Label();
        strip.getStyleClass().add("es-netmap-server");

        Display[] display = {Display.GRAPH};
        String[] selected = {""};
        Runnable[] repaint = new Runnable[1];

        // ── The server tab strip ─────────────────────────────────────────────────────────────────
        //
        // ⚠ SESSION-SCOPED AND NOT PERSISTED, the same call NM-1 made for the graph's expanded folds
        // and for the same reason: a sweep can change which servers exist at any moment, and a tab
        // restored from a save could name a server this character has never heard of.
        //
        // ⚠ REBUILT ON EVERY REPAINT rather than kept and patched. The strip is a pure function of
        // the map, so rebuilding it cannot drift from it — and the alternative, diffing chips against
        // the previous list, is the shape that leaves a stale tab on screen for a server a player has
        // just lost sight of.
        String[] openServer = {""};
        HBox tabStrip = Ui.row(UiTokens.SPACE_2);
        tabStrip.getStyleClass().add("es-netmap-tabs");
        tabStrip.setAlignment(Pos.BOTTOM_LEFT);
        java.util.function.Consumer<java.util.List<ServerTabs.Tab>> paintTabs = tabs -> {
            tabStrip.getChildren().clear();
            for (ServerTabs.Tab tab : tabs) {
                Label chip = new Label(Ui.upper(tab.label()));
                chip.getStyleClass().add("es-netmap-tab");
                if (tab.serverId().equals(openServer[0])) {
                    chip.getStyleClass().add("es-netmap-tab-open");
                }
                // ⚠ HOME IS MARKED, and it is marked by a WORD rather than by a colour. §4.4 — the
                // one tab that always exists and always means the same thing should say so to a
                // reader and in greyscale, not only to somebody who can see an accent.
                if (tab.home()) {
                    chip.getStyleClass().add("es-netmap-tab-home");
                }
                // ⚠ An unexplored tab is DIMMED, never hidden and never disabled. It is a server an
                // identified bridge has named and nothing more, which is a real thing to know and the
                // whole product of the bridge finding — and a disabled control still asks to be
                // understood, when the thing to understand is "go and cross that bridge".
                if (!tab.explored()) {
                    chip.getStyleClass().add("es-netmap-tab-unseen");
                }
                chip.setAccessibleText(tab.label()
                        + (tab.home() ? ", your own server" : "")
                        + (tab.current() ? ", where you are operating" : "")
                        + (tab.explored() ? ", " + tab.machines() + " machines found" : ", nothing found yet"));
                Tooltip.install(
                        chip,
                        new Tooltip(Ui.upper(tab.label()) + "\n"
                                + (tab.explored()
                                        ? tab.machines() + " machines found here."
                                        : "Named by a bridge. Nothing on it has been swept yet.")
                                + (tab.current() ? "\nYou are operating from this server." : "")));
                chip.setOnMouseClicked(event -> {
                    openServer[0] = tab.serverId();
                    // ⚠ The selection is dropped, not carried. It is an address on the server being
                    // left, and a selection row naming a machine that is not on the grid would leave
                    // CONNECT and BREACH pointing somewhere the player cannot see.
                    selected[0] = "";
                    repaint[0].run();
                });
                tabStrip.getChildren().add(chip);
            }
        };

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

        // ---------------------------------------------------------------- the node menu
        //
        // ⚠ Right-clicking SELECTS the machine first and then opens the menu. Without that the two
        // gestures disagree: the menu would act on whatever was selected before, while the pointer
        // is plainly over something else. Every entry then reads as being about the row under the
        // cursor, which is what a context menu means.
        javafx.scene.control.ContextMenu nodeMenu = new javafx.scene.control.ContextMenu();
        String[] menuTarget = {""};
        java.util.function.BiConsumer<String, javafx.scene.input.ContextMenuEvent> openMenu = (address, event) -> {
            // ⚠ THE WINDOW IS CAPTURED FIRST, and the menu is anchored to it rather than to
            // the node that fired the event.
            //
            // Selecting repaints, and repainting REBUILDS THE GRAPH — so by the time the
            // menu is shown, the label the player right-clicked has been detached from the
            // scene. Anchoring a popup to a node with no window throws
            // "The owner node needs to be associated with a window", which is a crash on
            // every right-click of a machine on the map. Screen coordinates are absolute, so
            // the menu lands in exactly the same place either way.
            //
            // Selecting still happens BEFORE the rebuild, for the reason above this block:
            // a menu that acted on the previously-selected machine while the pointer was
            // plainly over another one would be a context menu about the wrong context.
            javafx.scene.Node source = event.getSource() instanceof javafx.scene.Node node ? node : null;
            javafx.stage.Window window = source == null || source.getScene() == null
                    ? null
                    : source.getScene().getWindow();

            menuTarget[0] = address;
            select.accept(address);
            rebuildNodeMenu(nodeMenu, session, menuTarget[0], actions, arming, select);
            if (window == null) {
                // Nothing to anchor to — the panel is not on screen. Selecting has already
                // happened, which is the half of this that is still meaningful.
                return;
            }
            nodeMenu.show(window, event.getScreenX(), event.getScreenY());
        };
        graph.setOnNodeMenu(openMenu);
        list.setOnNodeMenu(openMenu);
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
        // always OFFERED. What changed on 2026-07-28 is that the two the player has not bought now
        // read as locked and say what they need — see the header note. What did not change is where
        // that answer comes from: `session.sweepOptions()` is the rules' verdict rendered as
        // received (C4), never a check this panel performs, and a rung the port does not mention is
        // drawn exactly as it was before rather than as locked.
        //
        // ⚠ These carry `es-netmap-action`, NOT the `es-netmap-control` the two view toggles use,
        // and the distinction is a bug fix rather than a flourish. A toggle has two states and paints
        // the inactive one in -es-dim-1; a sweep control has no such state and so sat permanently in
        // the colour this panel's own vocabulary uses for "not the one in force". Three buttons that
        // never brighten, in a row next to two that do, read as disabled — and were reported as
        // disabled. An action is not a toggle and must not borrow a toggle's off state.
        //
        // ⚠ `es-netmap-action-locked` is a THIRD state and not that same off state coming back. It
        // is asserted by the rules rather than by which control was clicked last, it always arrives
        // with the word LOCKED in the label and a tooltip naming the requirement, and it goes away
        // permanently the moment the tool is bought.
        //
        // Each names its price, for the same reason `sweep -n` prints one: a control whose cost is
        // invisible until you press it is a control a cautious player does not press.
        Map<String, BreachView.Chip> sweepChips = new LinkedHashMap<>();
        Map<String, Tooltip> sweepTips = new LinkedHashMap<>();
        HBox sweepGroup = Ui.row(UiTokens.SPACE_2, Ui.label(Views.t("ui.net-map.sweep", "Sweep")));
        for (String[] rung : new String[][] {{"", "BASE 2C"}, {"--wide", "WIDE 5C"}, {"--deep", "DEEP 9C"}}) {
            BreachView.Chip chip = action(rung[1]);
            // One Tooltip per chip, created once and re-texted on every repaint. Installing a fresh
            // one each time would stack them: Tooltip.install adds, it does not replace, and the
            // panel repaints on every session change.
            Tooltip tip = new Tooltip("");
            tip.setWrapText(true);
            tip.setMaxWidth(340);
            tip.setShowDelay(javafx.util.Duration.millis(220));
            tip.setShowDuration(javafx.util.Duration.seconds(30));
            Tooltip.install(chip, tip);
            sweepChips.put(rung[0], chip);
            sweepTips.put(rung[0], tip);
            sweepGroup.getChildren().add(chip);
        }
        BreachView.Chip sweepBase = sweepChips.get("");
        BreachView.Chip sweepWide = sweepChips.get("--wide");
        BreachView.Chip sweepDeep = sweepChips.get("--deep");

        HBox controls = Ui.row(UiTokens.SPACE_3, graphControl, listControl, folderControl, Ui.spacer(), sweepGroup);

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
        breach.setAccessibleText("Aim the breach window at the selected machine and open it. Nothing is spent until "
                + "you start the breach there.");
        fileHere.setAccessibleText("Put the selected machine into the selected folder.");
        unfile.setAccessibleText("Take the selected machine out of whatever folder it is in.");
        HBox selectionRow = Ui.row(UiTokens.SPACE_3, selection, breach, connect, download, fileHere, unfile);

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
        HBox folderRow =
                Ui.row(UiTokens.SPACE_3, folderSelection, folderName, newFolder, renameFolder, removeFolder, toTop);

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

        // ⚠ The legend is a COLUMN BESIDE the data area, not a strip under it, and that is a bug
        // fix rather than a rearrangement. Ten entries laid across the bottom of the panel did not
        // fit: the tail ran off the right edge, and because the horizontal scroll belongs to the
        // data area rather than to the panel, there was no way to reach what had been pushed out.
        // The entries that vanished were the dimmest states — the ones a player most needs named.
        //
        // A column has a bounded width and an unbounded run of entries, so an eleventh state now
        // costs vertical space this panel has instead of horizontal space it does not.
        HBox data = new HBox(UiTokens.SPACE_5, legend, area);
        data.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(area, Priority.ALWAYS);
        VBox.setVgrow(data, Priority.ALWAYS);

        // ⚠ THE TAB STRIP SITS ABOVE THE HEADER, and the order is the sketch's. A tab names the
        // place; the header describes the place the tab named — server, depth from home, hosts seen,
        // where the sweep runs from. Below the header it would read as a filter on the numbers rather
        // than as which of several places they are about.
        root.getChildren()
                .addAll(tabStrip, strip, controls, selectionRow, folderRow, detail, activity, data, reader);

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
            // ⚠ rearm, not arm. Pressing BREACH on the node already armed has to be heard, or the
            // breach panel never learns it should clear a finished attempt — see BreachArming.
            arming.rearm("node:" + selected[0]);
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
            NetMap world = session.net();

            // ── The server tabs ──────────────────────────────────────────────────────────────────
            //
            // ⚠ THE OPEN TAB IS CHOSEN HERE, NOT REMEMBERED BLINDLY. A tab is keyed on a server id
            // and the set of known servers grows as the player sweeps — so a remembered id can be one
            // the map has not heard of yet on a fresh load, and falling through to "show everything"
            // or to an empty grid would both read as the map having lost the world.
            java.util.List<ServerTabs.Tab> tabs = ServerTabs.of(world);
            boolean known = tabs.stream().anyMatch(tab -> tab.serverId().equals(openServer[0]));
            if (!known) {
                openServer[0] = ServerTabs.initial(world);
            }
            paintTabs.accept(tabs);

            // ⚠ Every surface below takes the FILTERED map, and that is what makes a tab a tab. The
            // graph, the list and the folder tree all showed the whole world before this, layered by
            // distance from the rig — truthful, and unreadable past the first bridge, because each
            // crossing adds a whole server's depth to the right-hand end of one grid.
            NetMap map = ServerTabs.filter(world, openServer[0]);

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
            Optional<Sighting> chosen = selected[0].isBlank() ? Optional.<Sighting>empty() : map.at(selected[0]);
            selection.set(chosen.map(Sighting::address).orElse("NONE"));
            detail.setText(chosen.map(NetHostList::describe)
                    .orElse("Pick a machine in any view. All three select the same thing."));
            visible(connect, chosen.isPresent());
            visible(download, chosen.map(Sighting::documentAvailable).orElse(false));
            // Offered for anything but the player's own rig. Whether the machine can actually be
            // attempted — compute, an existing foothold, a gate — is the rules' answer, and this
            // panel does not pre-empt it (C4): the breach window shows the target with the rules'
            // own verdict beside it, which is a better teacher than a control that is simply absent.
            // ⚠ Hidden on your OWN RIG, not on the vantage — see buildMenu. Keyed to the vantage,
            // the BREACH control vanished from whatever machine you were standing on and appeared
            // for your own.
            visible(breach, chosen.map(s -> !s.self()).orElse(false));

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

            // ---- the sweep ladder
            //
            // Re-read every repaint rather than once at construction, because buying the tool is
            // what changes the answer and the purchase happens in a different window. A control that
            // only learned its own state at open would stay locked until the map was closed and
            // reopened, which reads as the purchase not having worked.
            paintSweepLadder(session.sweepOptions(), sweepChips, sweepTips);

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

    /**
     * Rebuilds the node menu for one machine.
     *
     * <p>Rebuilt per open rather than kept and re-enabled, because what a machine offers depends on
     * its state — a machine you hold offers a shell, one you do not offers a breach — and a menu of
     * permanently-present greyed entries is a list of things the player cannot do. It is cheap: five
     * items, once per right-click.
     *
     * <p>⚠ The entries reflect the <b>rules'</b> view of the machine, taken from the map the rules
     * published, and none of them is hidden on a guess. {@code CONNECT} in particular is offered on
     * anything with a foothold and refused by the rules otherwise; the panel does not pre-empt the
     * refusal (C4), because the refusal names what is missing and an absent entry does not.
     */
    private static void rebuildNodeMenu(
            javafx.scene.control.ContextMenu menu,
            GameSession session,
            String address,
            NodeActions actions,
            BreachArming arming,
            java.util.function.Consumer<String> select) {
        menu.getItems().clear();
        Optional<Sighting> sighting = session.net().at(address);
        // ⚠ SELF IS THE PLAYER'S OWN RIG, NOT THE VANTAGE. This read `Sighting::vantage`, which is
        // right only while the vantage has never moved. Once it has, the machine you moved TO was
        // treated as yours — its menu hid Breach and Port scan — and your own rig stopped being
        // "self", so the menu cheerfully offered to breach and port-scan your own machine. That is
        // the failure CLAUDE.md already records once: "views that branched on it first told players
        // to 'breach' their own rig".
        boolean self = sighting.map(Sighting::self).orElse(false);
        boolean held = sighting.map(Sighting::foothold).orElse(false);
        boolean open = session.sessions().stream().anyMatch(s -> s.address().equals(address));

        javafx.scene.control.MenuItem header = new javafx.scene.control.MenuItem(
                Ui.upper(sighting.map(Sighting::address).orElse(address)));
        header.setDisable(true);
        menu.getItems().add(header);
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());

        javafx.scene.control.MenuItem shell =
                new javafx.scene.control.MenuItem(open ? "Raise the shell" : "Open a shell");
        shell.setOnAction(event -> actions.openShell(address));
        menu.getItems().add(shell);

        if (!self) {
            javafx.scene.control.MenuItem breach = new javafx.scene.control.MenuItem(held ? "Breach again" : "Breach");
            breach.setOnAction(event -> actions.breach(address));
            menu.getItems().add(breach);
        }

        // ⚠ Offered on ANY machine a sweep has found, held or not — a port scan is the thing you do
        // *before* you have a foothold, and gating it on one would put it behind the problem it
        // exists to help with.
        if (!self) {
            javafx.scene.control.MenuItem scan = new javafx.scene.control.MenuItem("Port scan…");
            scan.setOnAction(event -> actions.portScan(address));
            menu.getItems().add(scan);
        }

        // ⚠ Offered even with nothing on file, and DISABLED rather than absent. An entry that
        // vanished would make a player wonder whether they had mis-clicked; a disabled one says
        // there is such a thing as a report and this machine has none yet, which is the fact.
        javafx.scene.control.MenuItem info = new javafx.scene.control.MenuItem("Info");
        boolean filed = session.nodeReport(address).map(r -> r.any()).orElse(false);
        info.setDisable(!filed);
        info.setOnAction(event -> actions.info(address));
        menu.getItems().add(info);

        // ⚠ Named for the ACT, and the rig gets its own wording: "move" describes going out, and
        // coming back is the thing a player will look for by a different name.
        javafx.scene.control.MenuItem vantage =
                new javafx.scene.control.MenuItem(self ? "Sweep from this rig again" : "Move vantage here");
        // ⚠ Named for what it does, not "Connect". Moving the vantage changes where every future
        // sweep measures from (I2's ceiling), and calling it the same word as opening a shell is how
        // a player comes to believe that opening eight shells gave them eight vantages.
        vantage.setOnAction(event -> session.connectTo(address));
        // ⚠ THE RIG IS A LEGAL VANTAGE, AND THIS USED TO REFUSE IT. The condition was
        // `!held || self`, which read as "you cannot move the vantage to yourself" — true only while
        // `self` meant the vantage. Once it meant the player's own rig, it locked the player OUT of
        // returning: there was no way back to localhost from the map at all.
        //
        // ⚠ The rules never had this restriction. `NetRules.connect` reads
        // `if (!ownRig && !host.foothold) refuse` — your own rig has always been an accepted target.
        // This was the interface refusing something the engine allows, which is the worse direction
        // of the two to get wrong: the player cannot tell it is the menu rather than the game.
        //
        // What is genuinely nothing-to-do is moving the vantage to where it already is.
        boolean alreadyVantage = sighting.map(Sighting::vantage).orElse(false);
        vantage.setDisable(alreadyVantage || (!held && !self));
        menu.getItems().add(vantage);

        if (sighting.map(Sighting::documentAvailable).orElse(false)) {
            javafx.scene.control.MenuItem download = new javafx.scene.control.MenuItem("Download");
            download.setOnAction(event -> session.download(address));
            menu.getItems().add(download);
        }

        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem selectItem = new javafx.scene.control.MenuItem("Select only");
        selectItem.setOnAction(event -> select.accept(address));
        menu.getItems().add(selectItem);
    }

    // ------------------------------------------------------------------ the sweep ladder

    /** The marker a locked rung carries in its own label. Words, never colour alone (§4.4). */
    static final String LOCKED = "LOCKED";

    /** The style class a locked rung wears. See the block in {@code theme.css} for why it is third. */
    static final String LOCKED_CLASS = "es-netmap-action-locked";

    /**
     * What one sweep control should say and wear.
     *
     * <p>⚠ A record rather than three {@code setText} calls, and the split is what makes any of this
     * checkable: <b>no test in this module starts the JavaFX toolkit</b>, and a decision expressed
     * only as mutations of a {@code Label} cannot be asserted without one. Constructing a single
     * {@link Tooltip} is enough to fail — it is a {@code PopupControl}, so it needs a Window. So the
     * decision is pure and lives here, and {@link #paintSweepLadder} is the three lines that apply
     * it.
     */
    record RungRender(String label, boolean locked, String tooltip) {}

    /**
     * Turns the rules' verdict into what each control should show, keyed by sweep flag.
     *
     * <h2>⚠ A flag that is absent from the result is NOT locked</h2>
     *
     * {@link GameSession#sweepOptions()} returns an empty list when the rules cannot be reached, and
     * this returns an empty map for it. The caller leaves an unmentioned control exactly as it was —
     * offered, with the rules free to refuse it — because rendering "no verdict" as "locked" would
     * be the client asserting a gate nobody asserted, which {@code docs/client/05} §5 forbids in as
     * many words.
     */
    static Map<String, RungRender> renderLadder(List<GameSession.SweepOption> options) {
        GameSession.SweepOption base =
                options.stream().filter(o -> o.flag().isBlank()).findFirst().orElse(null);
        Map<String, RungRender> rendered = new LinkedHashMap<>();
        for (GameSession.SweepOption option : options) {
            String label = Ui.upper(rungName(option.flag())) + " " + option.cycles() + "C";
            rendered.put(
                    option.flag(),
                    new RungRender(
                            option.available() ? label : label + " " + LOCKED,
                            !option.available(),
                            sweepTooltip(option, base)));
        }
        return rendered;
    }

    /**
     * Paints the rules' verdict onto the sweep controls.
     *
     * <h2>Why the control is not {@code setDisable(true)}</h2>
     *
     * A disabled JavaFX node is skipped by picking, so it receives no hover and shows no tooltip —
     * which would remove the explanation at exactly the moment it is wanted, and the explanation is
     * the point. The rung is therefore marked, worded and tooltipped as locked, and pressing it
     * still asks the rules, which refuse in words and write that refusal to the log. Nothing is
     * spent by trying; a sweep reserves its compute inside {@code beginSweep}, which never runs.
     */
    private static void paintSweepLadder(
            List<GameSession.SweepOption> options, Map<String, BreachView.Chip> chips, Map<String, Tooltip> tips) {
        renderLadder(options).forEach((flag, rung) -> {
            BreachView.Chip chip = chips.get(flag);
            Tooltip tip = tips.get(flag);
            if (chip == null || tip == null) {
                return;
            }
            chip.setText(rung.label());
            chip.getStyleClass().remove(LOCKED_CLASS);
            if (rung.locked()) {
                chip.getStyleClass().add(LOCKED_CLASS);
            }
            tip.setText(rung.tooltip());
            chip.setAccessibleText(rung.tooltip().replace("\n\n", ". ").replace('\n', ' '));
        });
    }

    private static String rungName(String flag) {
        return flag == null || flag.isBlank() ? "base" : flag.replace("-", "");
    }

    /**
     * What a sweep control says when you hover it: what it needs, and what it buys over the base.
     *
     * <h2>This is not a hiding place for information</h2>
     *
     * {@code docs/design/ui-design-language.md} §3 bans "tooltips carrying information not shown
     * elsewhere", and every line here is somewhere else: the requirement and the price are the
     * market window's own card for the same offering, the cycle cost is printed on the control
     * itself, and the refusal the rules give if you press it anyway names the same tool. It is a
     * shortcut to information, exactly as the deck rail's tooltip is.
     *
     * <h2>The comparison is against the BASE rung, computed, not written</h2>
     *
     * The figures come out of the option the rules published rather than out of prose here, so
     * retuning {@code Balance.NET_SWEEP_*} cannot leave this window quietly quoting the old numbers
     * — which is the failure {@code CLAUDE.md} warns about when it says the economy values are
     * calibrated as a set.
     */
    private static String sweepTooltip(GameSession.SweepOption option, GameSession.SweepOption base) {
        StringBuilder out = new StringBuilder();
        out.append(Ui.upper(option.name()));
        if (!option.available()) {
            out.append(" — ").append(LOCKED);
        }
        out.append("\n\n");

        if (!option.available() && !option.requirement().isBlank()) {
            out.append("Needs ").append(option.requirement()).append(".\n\n");
        }

        if (base != null && option.flag().isBlank()) {
            out.append("The starting instrument. Everyone has it, and it is the floor every other "
                    + "sweep is measured against — it finds the loud machines within reach.\n\n");
        } else {
            // ⚠ The first sentence, every time, and it is the one that has to survive editing.
            // Invariant I2: ethecoin never buys a ceiling, and hop range IS the ceiling here. A
            // player who believes a better sweep reaches further will buy it for the wrong reason
            // and conclude the game lied to them.
            out.append("Same reach as the base sweep — one hop. No tier buys reach at any price; "
                    + "reach is the Topology Mapper's, and it is schematic-gated for that reason. "
                    + "What this buys is sensitivity: the chance of hearing a machine that is "
                    + "already within reach but too quiet for the base sweep.\n\n");
        }

        out.append(option.cycles())
                .append(" cycles held, about ")
                .append(option.seconds())
                .append("s, and loud while it runs.");
        if (base != null && !option.flag().isBlank()) {
            out.append("\n\nAgainst BASE: ")
                    .append(base.cycles())
                    .append(" cycles and about ")
                    .append(base.seconds())
                    .append("s at sensitivity ")
                    .append(base.sensitivity())
                    .append(", against ")
                    .append(option.cycles())
                    .append(" cycles and about ")
                    .append(option.seconds())
                    .append("s at sensitivity ")
                    .append(option.sensitivity())
                    .append(" here. Louder, too — the ladder is " + "loudness as well as sensitivity.");
        }
        out.append("\n\nA sweep never costs ethecoin. The tool does, once.");
        return out.toString();
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
