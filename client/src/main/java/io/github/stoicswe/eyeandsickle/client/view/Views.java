package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands;
import io.github.stoicswe.eyeandsickle.client.shell.Command;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.window.WindowRegistry;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The remaining tool windows.
 *
 * <h2>A note on depth</h2>
 *
 * The rig monitor and the terminal are built out fully because they are the two surfaces the design
 * documents specify in detail and the two a player uses constantly. The windows here are real — each
 * binds to the session, refreshes on change, and uses the same token vocabulary — but several of them
 * render systems that are still <b>[PROPOSAL]</b> in the design (the breach minigame, bots, comms) or
 * still stubbed on the server (gated offerings, W-3). Where that is true, the window says so on its
 * face rather than presenting an empty table that looks like a bug.
 *
 * <p>That is a deliberate choice about honesty: a window that admits it is waiting on a design
 * decision is more useful to the next person than one that fakes a feature.
 */
public final class Views {

    private Views() {}

    // ------------------------------------------------------------------ audit

    /**
     * Three views of one machine — processes, connections, storage.
     *
     * <p>This window is the game's central investigation. {@code docs/design/04-mining.md} §3.1
     * requires that a careful player can find a rootkit-wrapped miner by noticing that two of these
     * disagree, which is why they are shown together and why the data behind them is the same data
     * {@code ps}, {@code ss} and {@code df} print in the terminal — one source, two surfaces.
     */
    public static Region audit(GameSession session, Shell shell) {
        VBox root = panel("AUDIT — ps · netstat · df");
        Label hint = wrapped(
                "Three views of your own rig. They should agree. When they do not, something is "
                        + "hiding — a connection with no owning process, or storage that grew while "
                        + "nothing was running. That discrepancy is the game.");
        VBox output = new VBox(2);
        ScrollPane scroll = new ScrollPane(output);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Runnable refresh = () -> {
            output.getChildren().clear();
            for (String verb : new String[] {"ps", "ss", "df"}) {
                Label heading = new Label("$ " + verb);
                heading.getStyleClass().addAll("es-mono", "es-panel-title");
                output.getChildren().add(heading);
                for (String line : shell.run(verb).lines()) {
                    Label l = new Label(line);
                    l.getStyleClass().add("es-mono");
                    output.getChildren().add(l);
                }
                output.getChildren().add(new Separator());
            }
        };
        refresh.run();
        session.onChange(s -> refresh.run());

        root.getChildren().addAll(hint, scanControls(session), new Separator(), scroll);
        return root;
    }

    /**
     * Scan controls, with each tier's published cost on its own button.
     *
     * <p>Pillar <b>C1</b>: "a tool's cost is shown where the tool is used, not in a shop" — priced at
     * the moment of commitment. Before this, scanning was reachable only by typing {@code scan
     * --thorough}, which made a core action invisible to anyone who had not read the manual.
     *
     * <p>The buttons print the cost and do not compute a verdict. Whether the rig can afford it is
     * the session's answer, not the client's (pillar C4) — so an unaffordable tier is refused with a
     * reason rather than greyed out with none. A disabled button that will not say why is the
     * least helpful control there is.
     */
    private static Region scanControls(GameSession session) {
        Label heading = new Label("SCAN — search your own rig for what routine listings miss");
        heading.getStyleClass().add("es-panel-title");
        heading.setWrapText(true);

        Label result = new Label();
        result.setWrapText(true);

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        record Tier(String flag, String label, long cycles, String seconds) {}
        for (Tier t : List.of(
                new Tier("quick", "Quick", 5, "30s"),
                new Tier("full", "Full", 15, "2m"),
                new Tier("thorough", "Thorough", 35, "6m"))) {
            Button b = new Button(t.label() + "  ·  " + t.cycles() + " cycles  ·  " + t.seconds());
            b.setMinHeight(30);
            b.setTooltip(new javafx.scene.control.Tooltip(
                    "scan --" + t.flag() + "\n\nWhat a more expensive tier buys is signal strength, "
                            + "not certainty. The cycles come back on the Thermal Budget curve."));
            b.setAccessibleText("Run a " + t.label() + " scan, costing " + t.cycles() + " cycles");
            b.setOnAction(e -> {
                GameSession.Outcome outcome = session.scan(t.flag());
                result.setText(outcome.message());
                styleByOutcome(result, outcome);
            });
            row.getChildren().add(b);
        }

        Label note = secondary("Scanning your own rig never generates heat. Cycles spent here return "
                + "slowly, and more slowly the busier the rig already was.");
        return new VBox(6, heading, row, result, note);
    }

    // ------------------------------------------------------------------ mining

    /** Self-mining allocation and deployed-miner collection. */
    public static Region mining(GameSession session) {
        VBox root = panel("MINING");

        Label explanation = wrapped(
                "Self-mining is the floor: safe, silent, generates no heat, and cannot be seized — "
                        + "but it only earns while the client is open. Deployed miners are the only "
                        + "offline income, and their buffer caps, so time away is worth something "
                        + "but not proportionally.");

        Label current = new Label();
        current.getStyleClass().addAll("es-numeric", "es-compute");

        Slider allocation = new Slider(0, 100, 0);
        allocation.setShowTickMarks(true);
        allocation.setShowTickLabels(true);
        allocation.setMajorTickUnit(25);
        allocation.setBlockIncrement(5);

        Label projection = new Label();
        projection.getStyleClass().add("es-text-secondary");

        Button apply = new Button("Allocate");
        Button collect = new Button("Collect deployed yield");
        Label result = new Label();
        result.setWrapText(true);

        Runnable refresh = () -> {
            long total = session.computeBudget().total().cycles();
            allocation.setMax(total);
            // Ethecoin.toString() is a record's — "Ethecoin[minorUnits=0]". Formatting the amount is
            // not cosmetic here: the rig monitor shows the same balance two panels away, and
            // docs/design/04 §3.1 makes noticing that two readouts disagree the way a player catches
            // a hidden miner. Two DIFFERENT-LOOKING renderings of the same number destroy that.
            current.setText(String.format(
                    Locale.ROOT, "%.2f EC", session.balance().minorUnits() / 100.0d));
            long chosen = (long) allocation.getValue();
            // The published rate, and no verdict: the player does the arithmetic (pillar C4).
            projection.setText(chosen + " cycles × 0.4 EC per cycle-hour = "
                    + String.format(Locale.ROOT, "%.1f", chosen * 0.4) + " EC/hr while the client is open");
        };
        allocation.valueProperty().addListener((o, was, now) -> refresh.run());

        // The slider starts where the rig actually is, not at zero. A control that reads 0 while the
        // monitor beside it reads 30 is the same disagreement described above, and it also means the
        // first thing "Allocate" does is silently release every committed cycle.
        allocation.setValue(session.mining().selfMiningCycles());

        apply.setOnAction(e -> {
            GameSession.Outcome outcome = session.allocateSelfMining((long) allocation.getValue());
            result.setText(outcome.message());
            styleByOutcome(result, outcome);
        });
        collect.setOnAction(e -> {
            GameSession.Outcome outcome = session.collect();
            result.setText(outcome.message());
            styleByOutcome(result, outcome);
        });

        refresh.run();
        session.onChange(s -> refresh.run());

        root.getChildren()
                .addAll(explanation, new Separator(), new Label("BALANCE"), current,
                        new Label("SELF-MINING ALLOCATION"), allocation, projection,
                        new HBox(8, apply, collect), result);
        return root;
    }

    // ------------------------------------------------------------------ storage

    /** The three tiers as mount points, and what each exposure actually means. */
    public static Region storage(GameSession session) {
        VBox root = panel("STORAGE — three mounts, three exposures");

        Label result = new Label();
        result.setWrapText(true);

        VBox tiers = new VBox(12);
        Runnable refresh = () -> {
            tiers.getChildren().clear();
            addTier(tiers, session, StorageTier.VAULT, "/rig/storage/vault",
                    "Safe. Encrypted; not reachable even while you are online.", result);
            addTier(tiers, session, StorageTier.STANDARD_STORAGE, "/rig/storage/standard",
                    "Exposed while you are online. Fine for things you are using.", result);
            addTier(tiers, session, StorageTier.HIGH_HACKABLE_ZONE, "/rig/storage/high",
                    "Always exposed, online or not. Anything left here can be taken.", result);
        };
        refresh.run();
        session.onChange(s -> refresh.run());

        ScrollPane scroll = new ScrollPane(tiers);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(
                wrapped("Moving something changes what can happen to it. That risk change is the "
                        + "decision — the buttons say what each tier means, and `mv <item> <tier>` "
                        + "does the same thing from the terminal."),
                scroll,
                result);
        return root;
    }

    private static void addTier(
            VBox parent, GameSession session, StorageTier tier, String mount, String exposure, Label result) {
        VBox box = new VBox(4);
        box.getStyleClass().add("es-panel");
        Label heading = new Label(mount);
        heading.getStyleClass().addAll("es-mono", "es-panel-title");
        Label note = wrapped(exposure);
        box.getChildren().addAll(heading, note);

        var items = session.items(tier);
        if (items.isEmpty()) {
            box.getChildren().add(secondary("(empty)"));
        }
        for (GameSession.InventoryItem item : items) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(item.displayName() + (item.equipped() ? "  [equipped]" : ""));
            name.getStyleClass().add("es-mono");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(name, spacer);

            // One button per OTHER tier. `mv` was terminal-only, which made the risk decision the
            // storage window exists to present unreachable from the window itself (pillar C1).
            for (StorageTier target : StorageTier.values()) {
                if (target == tier) {
                    continue;
                }
                Button move = new Button("→ " + shortTier(target));
                move.setMinHeight(26);
                move.setTooltip(new javafx.scene.control.Tooltip(
                        "mv \"" + item.displayName() + "\" " + shortTier(target)
                                + "\n\n" + exposureOf(target)));
                move.setAccessibleText("Move " + item.displayName() + " to " + shortTier(target)
                        + ". " + exposureOf(target));
                move.setOnAction(e -> {
                    GameSession.Outcome outcome = session.moveItem(item.itemId(), target);
                    result.setText(outcome.message());
                    styleByOutcome(result, outcome);
                });
                row.getChildren().add(move);
            }
            box.getChildren().add(row);
        }
        parent.getChildren().add(box);
    }

    private static String shortTier(StorageTier tier) {
        return switch (tier) {
            case VAULT -> "vault";
            case STANDARD_STORAGE -> "standard";
            case HIGH_HACKABLE_ZONE -> "high";
        };
    }

    /** The consequence of a move, stated on the control that performs it. */
    private static String exposureOf(StorageTier tier) {
        return switch (tier) {
            case VAULT -> "Safe: unreachable online or off.";
            case STANDARD_STORAGE -> "Exposed while you are online.";
            case HIGH_HACKABLE_ZONE -> "Always exposed. Anything left here can be taken.";
        };
    }

    // ------------------------------------------------------------------ ledger

    /** Every movement of ethecoin, newest first — an append-only record the player can audit. */
    public static Region ledger(GameSession session) {
        VBox root = panel("LEDGER");
        TableView<GameSession.LedgerRow> table = new TableView<>();

        TableColumn<GameSession.LedgerRow, String> when = new TableColumn<>("When");
        when.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().at().toString()));
        when.setPrefWidth(200);

        TableColumn<GameSession.LedgerRow, String> delta = new TableColumn<>("Delta");
        delta.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                (c.getValue().deltaMinorUnits() >= 0 ? "+" : "") + money(c.getValue().deltaMinorUnits())));
        delta.setPrefWidth(110);

        TableColumn<GameSession.LedgerRow, String> balance = new TableColumn<>("Balance after");
        balance.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                money(c.getValue().balanceAfterMinorUnits())));
        balance.setPrefWidth(130);

        TableColumn<GameSession.LedgerRow, String> what = new TableColumn<>("What");
        what.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().description()));
        what.setPrefWidth(320);

        table.getColumns().addAll(List.of(when, delta, balance, what));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> table.getItems().setAll(session.ledger(500));
        refresh.run();
        session.onChange(s -> refresh.run());

        root.getChildren()
                .addAll(wrapped("Entries are added and never edited. Each row carries the balance after it, "
                                + "so the log reconciles without replaying it."),
                        table);
        return root;
    }

    // ------------------------------------------------------------------ defense

    /** Arming defences, and the compute budget that forces a choice between them. */
    public static Region defense(GameSession session) {
        VBox root = panel("DEFENSE");
        Label note = wrapped(
                "Every armed defence holds compute for as long as it stays armed. A fully paranoid "
                        + "loadout costs more than a starting rig has — that is the decision, not a "
                        + "shortfall. Defending your own rig never generates heat.");

        VBox buttons = new VBox(6);
        Label result = new Label();
        result.setWrapText(true);

        record Def(String kind, int tier, String label, long cycles) {}
        List<Def> catalogue = List.of(
                new Def("firewall", 1, "Firewall T1", 5),
                new Def("firewall", 3, "Firewall T3", 15),
                new Def("canary", 1, "Canary Token", 1),
                new Def("tarpit", 1, "Tarpit", 8),
                new Def("honeypot-stash", 1, "Honeypot Stash", 12),
                new Def("detection-array", 1, "Detection Array T1", 6),
                new Def("detection-array", 3, "Detection Array T3", 25),
                new Def("auto-counter-daemon", 1, "Auto-Counter Daemon", 18));

        for (Def def : catalogue) {
            Button b = new Button(def.label() + "  —  " + def.cycles() + " cycles while armed");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> {
                GameSession.Outcome outcome = session.arm(def.kind(), def.tier());
                result.setText(outcome.message());
                styleByOutcome(result, outcome);
            });
            buttons.getChildren().add(b);
        }

        Label legalNote = wrapped(
                "Note on the Auto-Counter Daemon: in this fiction it fires back. In the real world "
                        + "that is a crime in most jurisdictions, and being attacked first does not "
                        + "change that. See hack-back(7).");
        legalNote.getStyleClass().add("es-state-unreachable");

        root.getChildren().addAll(note, new Separator(), buttons, result, new Separator(), legalNote);
        return root;
    }

    // ------------------------------------------------------------------ identity

    /** Who you are, and — more importantly here — which kind of game you are in. */
    public static Region identity(GameSession session) {
        VBox root = panel("IDENTITY — whoami");
        VBox body = new VBox(6);

        Runnable refresh = () -> {
            body.getChildren().clear();
            body.getChildren().addAll(
                    field("handle", session.handle()),
                    field("mode", session.mode().label()),
                    field("heat", String.valueOf(session.personalHeat())),
                    field("balance", session.balance().toString()));
            Label explanation = wrapped(session.mode().explanation());
            body.getChildren().add(explanation);
            if (session.mode() == io.github.stoicswe.eyeandsickle.client.session.SessionMode.SOLO) {
                Label solo = wrapped(
                        "This character is local to this machine. It has no DID and no cryptographic "
                                + "identity, and it cannot be carried into a federated server — going "
                                + "online means creating a character there. That boundary is what keeps "
                                + "a file you can edit from ever becoming someone else's problem.");
                solo.getStyleClass().add("es-text-secondary");
                body.getChildren().add(solo);
            }
        };
        refresh.run();
        session.onChange(s -> refresh.run());

        root.getChildren().add(body);
        return root;
    }

    // ------------------------------------------------------------------ switcher

    /** The answer to losing a window behind another ({@code docs/client/05} §3.4). */
    public static Region switcher(WindowRegistry registry) {
        VBox root = panel("WINDOWS");
        VBox list = new VBox(4);

        Runnable refresh = () -> {
            list.getChildren().clear();
            for (WindowSpec spec : WindowSpec.values()) {
                if (spec == WindowSpec.SWITCHER) {
                    continue;
                }
                boolean open = registry.isOpen(spec);
                Button b = new Button((open ? "● " : "○ ") + spec.title());
                b.setMaxWidth(Double.MAX_VALUE);
                b.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                b.setTooltip(new javafx.scene.control.Tooltip(spec.title() + " — " + spec.unixAnalogue()));
                b.setOnAction(e -> registry.open(spec));
                list.getChildren().add(b);
            }
        };
        refresh.run();
        registry.openWindows().addListener((javafx.collections.ListChangeListener<WindowSpec>) c -> refresh.run());

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(secondary("● open   ○ closed"), scroll);
        return root;
    }

    // ------------------------------------------------------------------ settings

    /**
     * Theme, teaching level, desk behaviour, motion — everything {@code docs/client/00} §4.5 says
     * persists.
     *
     * @param onDeskSettingsChanged re-applies the desk options to the live shell. Passed in rather
     *     than reached for, because this view is also opened from the main menu where no desk
     *     exists yet — and a settings panel that silently does nothing in one of the two places it
     *     appears is worse than one that cannot be opened there.
     */
    public static Region settings(ClientProfile profile, ThemeManager themes, Runnable onDeskSettingsChanged) {
        VBox root = panel("SETTINGS");

        ChoiceBox<ThemeId> theme = new ChoiceBox<>();
        theme.getItems().addAll(ThemeId.selectable());
        theme.setValue(themes.current());
        theme.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ThemeId id) {
                return id == null ? "" : id.label();
            }

            @Override
            public ThemeId fromString(String s) {
                return ThemeId.DECK;
            }
        });
        theme.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                themes.select(now);
                profile.save();
            }
        });

        ChoiceBox<String> teaching = new ChoiceBox<>();
        teaching.getItems().addAll("explain", "terms", "off");
        teaching.setValue(profile.settings().teachingLevel);
        teaching.valueProperty().addListener((o, was, now) -> {
            profile.settings().teachingLevel = now;
            profile.save();
        });

        // §11 question 1, shipped as a choice rather than settled by fiat. See DeskManager.
        CheckBox freeDrag = new CheckBox("Drag windows freely");
        freeDrag.setSelected(profile.settings().freeDragWindows);
        freeDrag.selectedProperty().addListener((o, was, now) -> {
            profile.settings().freeDragWindows = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        CheckBox bandwidthCap = new CheckBox("Bandwidth limits open windows  [PROPOSAL]");
        bandwidthCap.setSelected(profile.settings().bandwidthCapsWindows);
        bandwidthCap.selectedProperty().addListener((o, was, now) -> {
            profile.settings().bandwidthCapsWindows = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        ChoiceBox<io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin> cursor = new ChoiceBox<>();
        cursor.getItems().addAll(io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin.selectable());
        cursor.setValue(io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin
                .byId(profile.settings().cursorSkin)
                .orElse(io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin.SYSTEM));
        cursor.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin skin) {
                return skin == null ? "" : skin.label();
            }

            @Override
            public io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin fromString(String s) {
                return io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin.SYSTEM;
            }
        });
        cursor.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                profile.settings().cursorSkin = now.id();
                profile.save();
                // Through the theme manager, because a pointer is drawn in the current palette's
                // colours and only the theme manager knows which stylesheets are live.
                themes.refreshCursors();
            }
        });

        CheckBox reducedMotion = new CheckBox("Reduce motion");
        reducedMotion.setSelected(themes.reducedMotion());
        reducedMotion.selectedProperty().addListener((o, was, now) -> {
            themes.setReducedMotionOverride(now);
            profile.save();
        });

        root.getChildren()
                .addAll(
                        new Label("APPEARANCE"),
                        theme,
                        wrapped("Every theme is the same deck with a different palette — one stylesheet "
                                + "owns the layout, the hairlines and the motion, so no skin can hide or "
                                + "soften a number. \"Deck — high visibility\" raises body text to WCAG "
                                + "AAA and makes every hairline visible; it is an accessibility floor "
                                + "rather than a style, and nothing else about the client changes."),
                        new Separator(),
                        new Label("TEACHING"),
                        teaching,
                        wrapped("`explain` shows a plain-language line with each term; `terms` shows the "
                                + "term only; `off` shows neither. The manual stays available at any "
                                + "level — try `man compute`."),
                        new Separator(),
                        new Label("DESK"),
                        freeDrag,
                        wrapped("Off: windows snap to a grid, and tile when dragged against an edge of "
                                + "the desk — a side fills that half, a corner that quarter. On: they go "
                                + "exactly where you put them."),
                        bandwidthCap,
                        wrapped("Off by default, and this one is not calibrated. The idea is that screen "
                                + "space is attention: Bandwidth caps how many engagements run at once, so "
                                + "it should cap how many tools you can have open. A starting rig has 1 "
                                + "Bandwidth, so the budget below adds six always-free windows — the "
                                + "monitor, terminal, log, manual, settings and switcher — to it. That "
                                + "arithmetic is invented, which is why this is opt-in."),
                        new Separator(),
                        new Label("POINTER"),
                        cursor,
                        wrapped("The pointer is the last piece of your operating system left on "
                                + "screen, so the deck can draw its own — in whatever colour the "
                                + "current theme means by \"live\". \"System pointer\" leaves yours "
                                + "alone, and that is the default on purpose: your OS has already "
                                + "tuned it for your display and your eyesight. The text I-beam is "
                                + "never replaced under any skin, because its shape tells you which "
                                + "two characters the caret will land between."),
                        new Separator(),
                        new Label("MOTION"),
                        reducedMotion,
                        wrapped("Follows your system setting unless you change it here. Suppresses the "
                                + "panel wipe, the caret blink, the greeble and the sweep bar; readouts "
                                + "keep updating, because that is information, not animation."),
                        new Separator(),
                        secondary("Profile directory: " + profile.directory()));
        return root;
    }

    // ------------------------------------------------------------------ still-proposal windows

    /**
     * Windows whose underlying system is still a design proposal.
     *
     * <p>Rather than render an empty table that reads as a bug, these say what they are waiting on
     * and point at the document. {@code docs/design/05} (the breach minigame), {@code 10} (bots) and
     * {@code 14} (narrative) are all explicitly <b>[PROPOSAL]</b>, and {@code CLAUDE.md} asks that
     * proposals be surfaced rather than hard-committed in code.
     */
    public static Region proposalPlaceholder(WindowSpec spec, String system, String doc, String why) {
        VBox root = panel(spec.title().toUpperCase(Locale.ROOT) + " — " + spec.unixAnalogue());
        root.getChildren()
                .addAll(
                        wrapped(why),
                        new Separator(),
                        secondary("This window renders " + system + ", which is still marked [PROPOSAL] in "
                                + doc + ". It is not built out yet because committing an interface to an "
                                + "undecided system is how a proposal quietly becomes a decision."),
                        secondary("The window, its id, size, accelerator and place in the switcher are real "
                                + "and match the catalogue in docs/client/05 §2.1."));
        return root;
    }

    // ------------------------------------------------------------------ helpers

    private static VBox panel(String title) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        Label heading = new Label(title);
        heading.getStyleClass().add("es-panel-title");
        root.getChildren().add(heading);
        return root;
    }

    private static Label wrapped(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    private static Label secondary(String text) {
        Label l = wrapped(text);
        l.getStyleClass().add("es-text-secondary");
        return l;
    }

    private static HBox field(String name, String value) {
        Label n = new Label(name);
        n.getStyleClass().addAll("es-mono", "es-text-secondary");
        n.setMinWidth(90);
        Label v = new Label(value);
        v.getStyleClass().add("es-mono");
        return new HBox(8, n, v);
    }

    private static void styleByOutcome(Label label, GameSession.Outcome outcome) {
        label.getStyleClass().removeAll("es-state-refused", "es-state-unreachable");
        if (outcome.status() == GameSession.Outcome.UNAVAILABLE
                || outcome.status() == GameSession.Outcome.TEMPFAIL) {
            label.getStyleClass().add("es-state-unreachable");
        } else if (!outcome.succeeded()) {
            label.getStyleClass().add("es-state-refused");
        }
    }

    private static String money(long minorUnits) {
        return String.format(Locale.ROOT, "%d.%02d EC", minorUnits / 100, Math.abs(minorUnits % 100));
    }
}
