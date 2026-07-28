package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.FsKind;
import io.github.stoicswe.eyeandsickle.protocol.game.RemoteSession;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The file manager — the rig's filesystem, and every machine mounted onto it.
 *
 * <h2>Nautilus's shape, this game's materials</h2>
 *
 * The layout is GNOME Files': a places sidebar on the left, a path bar across the top with back,
 * forward and up, and a detail list in the middle. That is not imitation for its own sake — it is the
 * arrangement an Ubuntu user already knows, and this window's whole purpose is that what a player
 * learns here transfers to a real machine. What it does <em>not</em> borrow is the chrome: no rounded
 * corners, no shadows, no icon grid of coloured folder pictures. The glyph vocabulary is the one the
 * rest of this client already uses, because a player who has learned to read the map should not have
 * to learn a second alphabet ({@code docs/design/ui-design-language.md} §9).
 *
 * <h2>⚠ A MOUNT IS A SESSION, and there is deliberately no second concept</h2>
 *
 * "Connect to Server" opens a shell session, and the machine appears in the sidebar. Unmounting
 * closes it. There is no separate mount state, no second compute cost and no second gate, and that is
 * the point: in Ubuntu, mounting a remote share <em>is</em> holding a connection open, and modelling
 * it twice here would give the game two lists of "machines I am attached to" that would eventually
 * disagree. Open a shell from the map and it is mounted here; mount it here and the shell is
 * available. One fact, two windows.
 *
 * <p>The consequence worth stating: a mount costs {@code Balance.SESSION_CYCLES} and needs a
 * foothold, exactly as a shell does. You cannot browse a machine you have not broken into — you can
 * see its shape and open nothing, which is also what a real port scan tells you.
 *
 * <h2>Readability is the rules' answer, rendered</h2>
 *
 * {@link FsEntry#readable} is set by the engine (I14). This window draws unreadable entries dimmed
 * and refuses to open them, but it never <em>decides</em> that — a file manager is the surface where
 * that mistake is easiest to make, because a path in a tree looks like something you could simply
 * open.
 */
public final class FileManagerView {

    private FileManagerView() {}

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

    /** Where a machine is, and where in it we are. The whole navigation state, in one value. */
    private record Place(String address, String path) {

        Place at(String next) {
            return new Place(address, next);
        }

        boolean rig() {
            return address == null || address.isBlank();
        }
    }

    public static Region create(GameSession session) {
        VBox root = new VBox(UiTokens.SPACE_3);
        root.getStyleClass().addAll("es-files", "es-body-pad");

        // ⚠ Through VirtualFs.home, never a literal. The root moved from /home to /Users on
        // 2026-07-28 and a hard-coded path here opened the window on a directory that no longer
        // existed — which renders as an empty folder rather than as an error, so nothing complains.
        Place[] here = {new Place("", VirtualFs.home(session.handle()))};
        Deque<Place> back = new ArrayDeque<>();
        Deque<Place> forward = new ArrayDeque<>();
        Runnable[] repaint = new Runnable[1];

        // ---------------------------------------------------------------- navigation
        java.util.function.Consumer<Place> go = target -> {
            back.push(here[0]);
            forward.clear();
            here[0] = target;
            // ⚠ Recorded HERE and not in the repaint. This is the one place a player chose to go
            // somewhere; a repaint happens for a dozen reasons that are not choices, and recording
            // there would fill Recents with the machinery instead of the history.
            session.noteAccess(target.address(), target.path());
            repaint[0].run();
        };

        BreachView.Chip backKey = key("<");
        BreachView.Chip forwardKey = key(">");
        BreachView.Chip upKey = key("^");
        backKey.setAccessibleText("Back to the previous folder.");
        forwardKey.setAccessibleText("Forward.");
        upKey.setAccessibleText("Up one level.");
        backKey.onInvoke(() -> {
            if (!back.isEmpty()) {
                forward.push(here[0]);
                here[0] = back.pop();
                repaint[0].run();
            }
        });
        forwardKey.onInvoke(() -> {
            if (!forward.isEmpty()) {
                back.push(here[0]);
                here[0] = forward.pop();
                repaint[0].run();
            }
        });
        upKey.onInvoke(() -> go.accept(here[0].at(VirtualFs.parentOf(here[0].path()))));

        HBox crumbs = new HBox(UiTokens.SPACE_1);
        crumbs.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(crumbs, Priority.ALWAYS);

        // Nautilus hides dotfiles by default and Ctrl+H reveals them, and copying that is worth a
        // control: the convention that a leading dot means "hidden" is real, is pure filesystem
        // convention rather than a permission, and is the kind of thing a player only ever finds out
        // by having been shown it once. The chip says which state it is in rather than what pressing
        // it would do — §4.4's rule that a state is never carried by appearance alone.
        boolean[] showHidden = {false};
        BreachView.Chip hiddenKey = key("Hidden: off");
        hiddenKey.setAccessibleText(
                "Show entries whose name starts with a dot. Ctrl+H does this in GNOME Files.");
        Tooltip.install(hiddenKey, tip("A leading dot means hidden — a convention, not a permission. "
                + "GNOME Files binds this to Ctrl+H, and `ls -a` is the same idea in a shell."));
        hiddenKey.onInvoke(() -> {
            showHidden[0] = !showHidden[0];
            hiddenKey.setText(showHidden[0] ? "HIDDEN: ON" : "HIDDEN: OFF");
            repaint[0].run();
        });

        HBox toolbar = Ui.row(UiTokens.SPACE_2, backKey, forwardKey, upKey, crumbs, hiddenKey);

        // ---------------------------------------------------------------- sidebar
        VBox sidebar = new VBox(UiTokens.SPACE_1);
        sidebar.getStyleClass().add("es-files-sidebar");
        sidebar.setMinWidth(190);
        sidebar.setPrefWidth(190);
        sidebar.setMaxWidth(190);

        // ---------------------------------------------------------------- listing
        VBox rows = new VBox();
        rows.getStyleClass().add("es-files-list");
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        HBox.setHgrow(scroll, Priority.ALWAYS);

        HBox body = new HBox(UiTokens.SPACE_3, sidebar, scroll);
        VBox.setVgrow(body, Priority.ALWAYS);

        // ---------------------------------------------------------------- transfers
        //
        // ⚠ Driven by the SESSION's task list, not by a timer this window owns. The transfer is a
        // task in the save, so closing this window does not cancel it and reopening shows it still
        // running — which is what a download should do and what a window-local animation could not.
        VBox transfers = new VBox(UiTokens.SPACE_1);
        transfers.getStyleClass().add("es-files-transfers");

        Label status = Ui.small("");
        Label refusal = new Label("");
        refusal.getStyleClass().add("es-files-refusal");
        refusal.setWrapText(true);

        root.getChildren().addAll(toolbar, body, transfers, status, refusal);

        // ---------------------------------------------------------------- painting
        repaint[0] = () -> {
            backKey.setDisable(back.isEmpty());
            forwardKey.setDisable(forward.isEmpty());
            upKey.setDisable(here[0].path().equals("/"));

            paintCrumbs(crumbs, here[0], target -> go.accept(target));
            paintSidebar(sidebar, session, here[0], go, repaint[0], refusal);

            List<FsEntry> all = session.list(here[0].address(), here[0].path());
            List<FsEntry> entries = all.stream()
                    .filter(e -> showHidden[0] || !e.name().startsWith("."))
                    .toList();
            paintRows(rows, session, entries, here[0], go, refusal);

            paintTransfers(transfers, session);

            long dirs = entries.stream().filter(FsEntry::directory).count();
            long unreadable = entries.stream().filter(e -> !e.readable()).count();
            long hidden = all.size() - entries.size();
            String where = here[0].rig() ? "this rig" : here[0].address();
            // The hidden count is stated rather than silently omitted. A listing that quietly showed
            // fewer things than are there is the one way this window could mislead about a machine.
            status.setText(entries.size() + " items on " + where
                    + "  ·  " + dirs + " folders"
                    + (hidden > 0 ? "  ·  " + hidden + " hidden" : "")
                    + (unreadable > 0 ? "  ·  " + unreadable + " you cannot read from here" : ""));
        };

        repaint[0].run();
        AutoCloseable subscription = session.onChange(s -> repaint[0].run());
        closeOnDetach(root, subscription);
        return root;
    }

    // ------------------------------------------------------------------ the path bar

    /**
     * The path as clickable segments.
     *
     * <p>Nautilus's breadcrumbs, and worth copying because they answer two questions at once: where
     * am I, and how do I get back to any point above me. A text field showing the path answers only
     * the first, and a text field a player can <em>type</em> into would be a place to type a path
     * that goes somewhere — which is the one thing {@code docs/client/04} §3.1 rule 3 forbids.
     */
    private static void paintCrumbs(
            HBox crumbs, Place here, java.util.function.Consumer<Place> go) {
        crumbs.getChildren().clear();

        Label machine = new Label(here.rig() ? "this rig" : here.address());
        machine.getStyleClass().add("es-files-crumb-root");
        crumbs.getChildren().add(machine);

        String path = VirtualFs.normalise(here.path());
        StringBuilder walked = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            walked.append('/').append(segment);
            String target = walked.toString();
            Label sep = new Label("/");
            sep.getStyleClass().add("es-files-crumb-sep");
            Label crumb = new Label(segment);
            crumb.getStyleClass().add("es-files-crumb");
            Cursors.shared().clickable(crumb);
            crumb.setOnMouseClicked(event -> go.accept(here.at(target)));
            crumbs.getChildren().addAll(sep, crumb);
        }
    }

    // ------------------------------------------------------------------ the sidebar

    /**
     * The places sidebar.
     *
     * <h2>macOS Finder's arrangement, this game's places</h2>
     *
     * Four sections in Finder's own order — <b>Recents</b>, <b>Shared</b>, <b>Favorites</b>,
     * <b>Locations</b> — because that grouping answers three different questions and a single flat
     * list answers one. What each section <em>contains</em> is this game's, and two of them earn
     * their names by meaning something here rather than by looking familiar:
     *
     * <ul>
     *   <li><b>Shared</b> — machines you hold a foothold on but have not mounted. Finder's Shared
     *       section is "computers you could connect to", which is exactly what these are. Clicking
     *       one mounts it, which is what makes this better than the popup it replaces.
     *   <li><b>Locations</b> — this machine and, under it, everything currently mounted. Finder puts
     *       Network here and so do we.
     * </ul>
     *
     * <p>⚠ <b>iCloud Drive and AirDrop are deliberately absent</b>, and for different reasons.
     * iCloud was excluded by request. AirDrop has no analogue at all — there is no peer-to-peer
     * transfer in this game — and a sidebar entry that does nothing is worse than a missing one,
     * because it teaches a player that entries in this list may be decorative.
     *
     * <p>⚠ <b>The storage tiers are NOT here.</b> They moved into {@code ~/.VaultStore}, which is
     * hidden. A {@code Vault} entry in the sidebar of a machine an intruder is standing on is a
     * signpost to the one place that is supposed to be safe.
     */
    private static void paintSidebar(
            VBox sidebar,
            GameSession session,
            Place here,
            java.util.function.Consumer<Place> go,
            Runnable repaint,
            Label refusal) {
        sidebar.getChildren().clear();
        String user = session.handle();
        String home = VirtualFs.home(user);

        // ── Recents ──────────────────────────────────────────────────────────────────────────
        //
        // ⚠ A PLACE, not a list bolted to the sidebar. GNOME really does keep this on disk
        // (~/.local/share/recently-used), so it is a directory you navigate into — which means the
        // shell reaches it too, and which means an intruder standing in it can read what the owner
        // has been doing. That last part is a feature of the fiction rather than a leak in it.
        sidebar.getChildren().add(Ui.label("Recents"));
        Place recentsPlace = new Place("",
                home + "/" + io.github.stoicswe.eyeandsickle.solo.fs.Recents.DIR);
        place(sidebar, "Recents", recentsPlace, here, go);

        // ── Favorites ────────────────────────────────────────────────────────────────────────
        sidebar.getChildren().add(Ui.label("Favorites"));
        // ⚠ Applications is /Applications, not ~/Applications — macOS keeps programs at the root
        // and so does uOS. Everything else in Favorites is a place in the player's home.
        place(sidebar, "Applications", new Place("", VirtualFs.APPLICATIONS), here, go);
        for (String folder : VirtualFs.homeFolders()) {
            place(sidebar, folder, new Place("", home + "/" + folder), here, go);
        }

        // ── Locations ────────────────────────────────────────────────────────────────────────
        sidebar.getChildren().add(Ui.label("Locations"));
        // The handle, then the machine — Finder's own order, and the same who-then-where the
        // command strip's prompt uses.
        place(sidebar, user, new Place("", home), here, go);
        place(sidebar, "Filesystem", new Place("", "/"), here, go);
        // The base system gets its own entry, because it is the one place in this tree that behaves
        // differently — you can look at all of it and open none of it.
        place(sidebar, "System", new Place("", VirtualFs.SYSTEM), here, go);

        // ── Network ──────────────────────────────────────────────────────────────────────────
        //
        // ⚠ Every machine that has been BREACHED, connected or not — which is what makes this a
        // network view rather than a connection list. A machine you hold is somewhere you can go;
        // whether you are currently there is a second fact, carried by the eject control.
        //
        // This replaced a separate "Shared" section. Two lists, one of machines you could connect
        // to and one of machines you had, made the player track a distinction the eject button
        // already expresses in place.
        sidebar.getChildren().add(Ui.label("Network"));
        List<String> mounted = session.sessions().stream().map(RemoteSession::address).toList();
        List<Sighting> breached = session.net().sightings().stream()
                .filter(Sighting::foothold)
                .filter(sighting -> !sighting.vantage())
                .toList();
        if (breached.isEmpty()) {
            Label none = Ui.small("Nothing breached yet. A foothold is what puts a machine here.");
            none.setWrapText(true);
            sidebar.getChildren().add(none);
        }
        for (Sighting sighting : breached) {
            boolean connected = mounted.contains(sighting.address());
            sidebar.getChildren().add(networkRow(
                    session, sighting, connected, here, go, repaint, refusal));
        }

        place(sidebar, "Trash", new Place("", home + "/.Trash"), here, go);
    }

    /**
     * One machine on the network: its name, and an eject control when it is connected.
     *
     * <h2>⚠ Ejecting disconnects. It does not stop anything running.</h2>
     *
     * Deployed miners keep mining, bots keep working and a foothold stays a foothold — none of that
     * needs a shell open, and none of it is affected. What ejecting buys is <b>quiet</b>: a held
     * session is outward traffic and is the loudest thing short of a sweep
     * ({@code solo/rules/NoiseRules}), so closing one you are not using lowers the noise floor and
     * hands back the cycles it was holding. The tooltip says this in as many words, because a player
     * who thinks ejecting kills their miners will never eject, and will be permanently louder than
     * they need to be.
     */
    private static Region networkRow(
            GameSession session,
            Sighting sighting,
            boolean connected,
            Place here,
            java.util.function.Consumer<Place> go,
            Runnable repaint,
            Label refusal) {

        HBox row = new HBox(UiTokens.SPACE_2);
        row.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(sighting.label().isBlank()
                ? sighting.address() : sighting.label());
        name.getStyleClass().add(here.address().equals(sighting.address())
                ? "es-files-place-on" : "es-files-place");
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);
        Cursors.shared().clickable(name);
        Tooltip.install(name, tip(sighting.address() + "\n"
                + (connected
                        ? "Connected. Its filesystem is browsable; anything you pull comes over its "
                                + "uplink, not your downlink."
                        : "Breached but not connected. Opening it costs cycles and makes noise for "
                                + "as long as it stays open.")));
        name.setOnMouseClicked(event -> {
            if (connected) {
                go.accept(new Place(sighting.address(), "/"));
                return;
            }
            GameSession.Outcome outcome = session.openSession(sighting.address());
            refusal.setText(outcome.succeeded() ? "" : outcome.message());
            if (outcome.succeeded()) {
                go.accept(new Place(sighting.address(), "/"));
            }
            repaint.run();
        });
        row.getChildren().add(name);

        if (connected) {
            // The eject glyph is a triangle-over-bar on a Mac and is in neither bundled face, so
            // this is the word. GlyphCoverageTest would have caught it; saying EJECT is also
            // unambiguous to a screen reader, which a glyph is not.
            BreachView.Chip eject = key("Eject");
            eject.setAccessibleText("Disconnect from " + sighting.address()
                    + ". Miners and bots keep running; this only stops the connection.");
            Tooltip.install(eject, tip("Disconnect from " + sighting.address() + ".\n\n"
                    + "Note: this does NOT stop deployed miners, bots, or anything else running "
                    + "there, "
                    + "and it does not give up the foothold. What it stops is the connection — "
                    + "which hands back the cycles it was holding and lowers your noise, because a "
                    + "held session is the loudest thing you can do short of a sweep."));
            eject.onInvoke(() -> {
                GameSession.Outcome outcome = session.closeSession(sighting.address());
                refusal.setText(outcome.succeeded() ? "" : outcome.message());
                repaint.run();
            });
            row.getChildren().add(eject);
        }
        return row;
    }

    /**
     * One sidebar entry.
     *
     * <p>Highlighted when it is where you are, compared on the whole {@link Place} — machine and
     * path together. Comparing on path alone would light up {@code /etc} in the sidebar while the
     * player was looking at some other machine's {@code /etc}, which is the single most confusing
     * thing a two-machine file manager can do.
     */
    private static void place(
            VBox sidebar, String name, Place target,
            Place here, java.util.function.Consumer<Place> go) {
        Label row = new Label(name);
        row.getStyleClass().add(here.equals(target) ? "es-files-place-on" : "es-files-place");
        Cursors.shared().clickable(row);
        row.setOnMouseClicked(event -> go.accept(target));
        sidebar.getChildren().add(row);
    }

    /**
     * The in-flight transfers, one discrete cell meter each.
     *
     * <h2>Cells, not a smooth bar</h2>
     *
     * {@code docs/design/ui-design-language.md} §4 bans continuous bars in this client for a reason
     * that applies here as well as to the cycle grid: a smooth fill implies a precision the model
     * does not have. A transfer's progress is computed from two timestamps, so twenty cells is an
     * honest resolution and a gradient is not.
     *
     * <p>The caption names the ceiling — the remote end's upload — because that is the number that
     * explains why the bar is moving at the speed it is, and it is the thing worth learning.
     */
    private static void paintTransfers(VBox box, GameSession session) {
        box.getChildren().clear();
        List<GameSession.RunningTask> running = session.transfers();
        box.setVisible(!running.isEmpty());
        box.setManaged(!running.isEmpty());
        for (GameSession.RunningTask task : running) {
            HBox row = new HBox(UiTokens.SPACE_3);
            row.setAlignment(Pos.CENTER_LEFT);

            Label label = Ui.small(task.label());
            label.setMinWidth(240);

            HBox cells = new HBox(1);
            cells.setAlignment(Pos.CENTER_LEFT);
            int filled = (int) Math.round(task.progress() * TRANSFER_CELLS);
            for (int i = 0; i < TRANSFER_CELLS; i++) {
                cells.getChildren().add(Ui.block(
                        UiTokens.METER_BAR_WIDTH, UiTokens.METER_BAR_HEIGHT,
                        i < filled ? "es-files-cell-on" : "es-files-cell-off"));
            }

            Label figures = Ui.small(Math.round(task.progress() * 100) + "%  ·  "
                    + task.remaining().toSeconds() + "s left  ·  ceiling "
                    + (io.github.stoicswe.eyeandsickle.solo.Balance.LINK_UP_BITS / 1_000_000L)
                    + " Mbit/s up");
            row.getChildren().addAll(label, cells, figures);
            box.getChildren().add(row);
        }
    }

    /** Twenty cells. Enough resolution to read as motion, few enough to stay honest about it. */
    private static final int TRANSFER_CELLS = 20;

    // ------------------------------------------------------------------ the listing

    private static void paintRows(
            VBox rows,
            GameSession session,
            List<FsEntry> entries,
            Place here,
            java.util.function.Consumer<Place> go,
            Label refusal) {
        rows.getChildren().clear();
        rows.getChildren().add(header());

        if (entries.isEmpty()) {
            Label empty = Ui.small(here.rig()
                    ? "Nothing here."
                    : "Nothing readable here. A machine you do not hold shows its shape and opens "
                            + "nothing — which is also what a port scan really tells you.");
            empty.setWrapText(true);
            empty.getStyleClass().add("es-files-empty");
            rows.getChildren().add(empty);
            return;
        }

        for (FsEntry entry : entries) {
            rows.getChildren().add(row(session, entry, here, go, refusal));
        }
    }

    private static HBox header() {
        HBox head = new HBox(UiTokens.SPACE_3);
        head.getStyleClass().add("es-files-head");
        head.setAlignment(Pos.CENTER_LEFT);
        head.getChildren().addAll(
                column(Ui.label("Name"), 300),
                column(Ui.label("Size"), 90),
                column(Ui.label("Type"), 110),
                column(Ui.label("Modified"), 170));
        return head;
    }

    private static HBox row(
            GameSession session,
            FsEntry entry,
            Place here,
            java.util.function.Consumer<Place> go,
            Label refusal) {

        HBox row = new HBox(UiTokens.SPACE_3);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add(entry.readable() ? "es-files-row" : "es-files-row-locked");

        // ⚠ ls -F's own trailing marker, shared with the node shell — see NodeCommands.marker.
        // Block-element icons were tried first and GlyphCoverageTest rejected four of them: they
        // are in neither bundled face, so they would have been drawn by a host-OS fallback with its
        // own advance width, shearing this column differently on every platform. The markers that
        // replaced them are better anyway, because `/` and `*` are real and transfer.
        Label name = new Label(entry.name()
                + io.github.stoicswe.eyeandsickle.client.shell.NodeCommands.marker(entry));
        name.getStyleClass().add("es-files-name");
        Label size = new Label(entry.directory() ? "—" : human(entry.sizeBytes()));
        size.getStyleClass().add("es-files-cell");
        Label type = new Label(typeName(entry));
        type.getStyleClass().add("es-files-cell");
        Label modified = new Label(STAMP.format(entry.modifiedAt()));
        modified.getStyleClass().add("es-files-cell");

        row.getChildren().addAll(
                column(name, 300), column(size, 90), column(type, 110), column(modified, 170));

        Tooltip.install(row, tip(entry.path() + "\n" + entry.mode() + "  " + entry.owner() + ":"
                + entry.group()
                + (entry.readable() ? "" : "\n\nYou cannot read this from here — you do not hold "
                        + "this machine.")));
        Cursors.shared().clickable(row);

        row.setOnMouseClicked(event -> {
            if (event.getClickCount() < 2) {
                return;
            }
            open(session, entry, here, go, refusal);
        });

        ContextMenu menu = new ContextMenu();
        MenuItem openItem = new MenuItem("Open");
        openItem.setOnAction(event -> open(session, entry, here, go, refusal));
        // macOS's own name for it, and macOS's own idea: a panel about the thing rather than the
        // thing. It is the entry that works on a DIRECTORY, where there are no contents to show and
        // a great deal to say — which is where most of this filesystem's teaching actually is.
        MenuItem getInfo = new MenuItem("Get info");
        getInfo.setOnAction(event -> showInfo(session, entry, here));
        menu.getItems().addAll(openItem, getInfo);
        if (!here.rig() && !entry.directory()) {
            // ⚠ A SUBMENU of destinations, not a bare Download. macOS's Save-as sheet is the model:
            // the question "where does this go" is asked once, at the moment of choosing, rather
            // than answered silently and discovered later in a folder the player did not pick.
            // The list is the port's, so it cannot offer a folder the rig does not have.
            Menu take = new Menu("Download to");
            for (String destination : session.downloadDestinations()) {
                MenuItem into = new MenuItem(VirtualFs.nameOf(destination));
                into.setOnAction(event -> {
                    GameSession.Outcome outcome =
                            session.download(here.address(), entry, destination);
                    refusal.setText(outcome.message());
                });
                take.getItems().add(into);
            }
            menu.getItems().addAll(new SeparatorMenuItem(), take);
        }

        // On your own rig, a package installs or sells. Both consume it, and the menu says which is
        // which rather than making the player guess from a single ambiguous "Use".
        if (here.rig() && entry.name().endsWith(".upg")) {
            MenuItem install = new MenuItem("Install");
            install.setOnAction(event ->
                    refusal.setText(session.install(entry.path()).message()));
            MenuItem sell = new MenuItem("Sell on the secondary market");
            sell.setOnAction(event -> refusal.setText(session.sell(entry.path()).message()));
            menu.getItems().addAll(new SeparatorMenuItem(), install, sell);
        }
        row.setOnContextMenuRequested(event -> {
            menu.show(row, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return row;
    }

    /**
     * What a double-click does.
     *
     * <p>A directory is entered. Anything else is described rather than opened, and the refusal for
     * an unreadable entry is the rules' verdict shown in words — never a silent no-op, which reads as
     * the window being broken rather than the machine being closed to you.
     */
    private static void open(
            GameSession session,
            FsEntry entry,
            Place here,
            java.util.function.Consumer<Place> go,
            Label refusal) {
        // ⚠ A DIRECTORY IS ENTERED. First, before anything else, unconditionally.
        //
        // This used to sit below a `read` call, and the result was that double-clicking /System/bin
        // opened an info panel describing a folder as a stripped x86-64 binary. Opening a folder is
        // not a question about permissions or contents — it is navigation, and it is what a
        // double-click means everywhere a person has ever double-clicked anything.
        if (entry.directory()) {
            refusal.setText("");
            go.accept(here.at(entry.path()));
            return;
        }
        // ⚠ For a FILE, the rules are asked before the readable flag. That bit is one bit and there
        // is more than one reason a file will not open — /System/etc/master.passwd is mode 0600 on
        // your own machine, and telling its owner to "breach it first" is nonsense.
        List<String> early = session.read(here.address(), entry.path());
        if (!early.isEmpty()) {
            showFile(session, entry, early);
            refusal.setText("");
            return;
        }
        if (!entry.readable()) {
            refusal.setText(entry.name() + ": you do not hold this machine. Breach it first.");
            return;
        }
        if (here.rig() && entry.name().endsWith(".upg")) {
            // Double-click installs, which is what double-clicking an installer does everywhere.
            // Selling is a deliberate right-click, because it is the irreversible one.
            refusal.setText(session.install(entry.path()).message());
            return;
        }
        refusal.setText(switch (entry.kind()) {
            case DOCUMENT -> entry.name() + " — a recovered fragment. Right-click to recover it; "
                    + "it opens in the recon window.";
            case LOOT -> entry.name() + " — an ethecoin cache. Right-click to take it.";
            default -> entry.name() + " — " + human(entry.sizeBytes()) + ", " + entry.mode()
                    + ". Nothing in this file is modelled.";
        });
    }

    /**
     * The "Get info" panel: what this thing is, in the machine's terms and then in plain ones.
     *
     * <h2>Facts first, teaching second, and both from the right place</h2>
     *
     * The header is built from the {@link FsEntry} the listing already holds — path, kind, size,
     * mode, owner, modified — because those are facts the rules published and this window is
     * rendering. Everything below it comes from {@link GameSession#info}, because "what is
     * {@code /System/bin} for" is a question about the game's world and not about a widget.
     *
     * <p>⚠ This works on directories, and that is the point of having it. A folder cannot be opened
     * <em>into</em> a text view, so before this existed the only way to find out what
     * {@code /System/rescue} was for was to not find out.
     */
    private static void showInfo(GameSession session, FsEntry entry, Place here) {
        List<String> lines = new ArrayList<>();
        lines.add(entry.path());
        lines.add("");
        lines.add("Kind      " + typeName(entry));
        lines.add("Size      " + (entry.directory() ? "--" : human(entry.sizeBytes())));
        lines.add("Mode      " + entry.mode() + "   " + entry.owner() + ":" + entry.group());
        lines.add("Modified  " + STAMP.format(entry.modifiedAt()));
        lines.add("Readable  " + (entry.readable() ? "yes" : "no"));

        List<String> note = session.info(here.address(), entry.path());
        if (!note.isEmpty()) {
            lines.add("");
            lines.addAll(note);
        }
        showFile(session, entry, lines);
    }

    /**
     * A read-only viewer for a file that has real contents.
     *
     * <p>Monospace and unwrapped, because the one file this currently opens is a log with columns in
     * it, and a wrapped log is a log you cannot read down. Read-only with no edit control at all:
     * the intruder's ability to alter this record is a thing that happens on <em>their</em> side, in
     * the rules, and giving the victim a text editor over their own evidence would make the mechanic
     * meaningless from both directions.
     */
    private static void showFile(GameSession session, FsEntry entry, List<String> lines) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        VBox panel = new VBox(UiTokens.SPACE_2);
        panel.getStyleClass().addAll("es-files", "es-body-pad", "es-files-dialog");
        panel.setMinWidth(680);
        panel.setMaxHeight(520);

        panel.getChildren().add(Ui.label(entry.path()));
        VBox body = new VBox(UiTokens.SPACE_1);
        for (String line : lines) {
            Label row = new Label(line);
            row.getStyleClass().add("es-files-fileline");
            body.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        panel.getChildren().add(scroll);

        BreachView.Chip close = key("Close");
        close.onInvoke(popup::hide);
        panel.getChildren().add(close);

        popup.getContent().add(panel);
        javafx.stage.Window window = javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing).findFirst().orElse(null);
        if (window != null) {
            popup.show(window,
                    window.getX() + (window.getWidth() - 680) / 2,
                    window.getY() + 90);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String typeName(FsEntry entry) {
        return switch (entry.kind()) {
            case DIRECTORY -> "Folder";
            case MOUNT -> "Mount";
            case EXECUTABLE -> "Program";
            case SYMLINK -> "Link";
            case DOCUMENT -> "Fragment";
            case LOOT -> "Wallet";
            case FILE -> "File";
        };
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f kB", bytes / 1024.0d);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0d * 1024.0d));
    }

    private static boolean isOwnRig(GameSession session, String address) {
        return session.net().at(address)
                .map(s -> s.kind() == io.github.stoicswe.eyeandsickle.protocol.game.HostKind.SELF)
                .orElse(false);
    }

    private static Region column(Node content, double width) {
        HBox cell = new HBox(content);
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    private static BreachView.Chip key(String text) {
        return new BreachView.Chip(text, "es-files-action");
    }

    private static Tooltip tip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setWrapText(true);
        tip.setMaxWidth(320);
        tip.setShowDelay(javafx.util.Duration.millis(220));
        tip.setShowDuration(javafx.util.Duration.seconds(30));
        return tip;
    }

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
                // Nothing this panel can do about a registry that will not forget a listener, and
                // throwing out of a scene listener would take the whole close with it.
            }
        });
    }
}
