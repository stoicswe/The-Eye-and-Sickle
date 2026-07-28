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
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.PoolScheme;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.ArrayList;
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
import javafx.scene.control.TextField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
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

    /**
     * The chain, the rig's place in it, and the two choices a miner actually makes.
     *
     * <h2>⚠ Every number here is read off the port, and none is computed locally</h2>
     *
     * This panel used to print {@code cycles × 0.4 EC per cycle-hour}. That was a third copy of a
     * balance rate living in a view class, and it silently became wrong on 2026-07-27 when
     * self-mining became a Poisson process whose rate depends on the mode and the pool's fee: it
     * kept printing 40 EC/hr to solo miners earning 40.8 and to SMALL HOURS miners earning 40.6.
     * The engine publishes an expectation; this draws it. See {@code RigStatus} for the same note.
     *
     * <h2>⚠ There is no progress bar and there must never be one</h2>
     *
     * Mining is memoryless — every hash is an independent trial against the same target — so there
     * is nothing to be partway through. A bar would teach the gambler's fallacy in the one place
     * players reliably hold it, and would make them hold cycles on mining to protect progress that
     * does not exist. The panel shows an expected interval and the honest odds instead, and says so
     * in words when solo.
     */
    public static Region mining(GameSession session) {
        VBox root = panel("MINING");

        Label chain = new Label();
        chain.getStyleClass().add("es-mono");
        Label rigLine = new Label();
        rigLine.getStyleClass().add("es-mono");

        Label modeNote = wrapped("");
        ToggleGroup modes = new ToggleGroup();
        ToggleButton pooled = new ToggleButton("POOLED");
        ToggleButton solo = new ToggleButton("SOLO");
        pooled.setToggleGroup(modes);
        solo.setToggleGroup(modes);

        VBox poolList = new VBox(6);
        Label poolHeading = new Label("POOL");
        poolHeading.getStyleClass().add("es-panel-title");

        Slider allocation = new Slider(0, 100, 0);
        allocation.setShowTickMarks(true);
        allocation.setShowTickLabels(true);
        allocation.setMajorTickUnit(25);
        allocation.setBlockIncrement(5);

        Label projection = new Label();
        projection.getStyleClass().add("es-text-secondary");
        Label odds = new Label();
        odds.getStyleClass().add("es-text-secondary");
        odds.setWrapText(true);

        // The uncommitted request, priced, on its own line and only when it differs. See refresh.
        Label preview = new Label();
        preview.getStyleClass().add("es-text-secondary");
        preview.setWrapText(true);
        preview.setVisible(false);
        preview.setManaged(false);

        Label current = new Label();
        current.getStyleClass().addAll("es-numeric", "es-compute");

        Button apply = new Button("Allocate");
        Button collect = new Button("Collect deployed yield");
        Label result = new Label();
        result.setWrapText(true);

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                MiningSnapshot m = session.miningChain();
                boolean isSolo = m.mode() == MiningMode.SOLO;

                allocation.setMax(Math.max(1, session.computeBudget().total().cycles()));
                // Ethecoin.toString() is a record's. Formatting the amount is not cosmetic: the rig
                // monitor shows the same balance two panels away, and docs/design/04 §3.1 makes
                // noticing that two readouts disagree the way a player catches a hidden miner. Two
                // different-looking renderings of one number destroy that.
                current.setText(String.format(
                        Locale.ROOT, "%.2f EC", session.balance().minorUnits() / 100.0d));

                chain.setText(String.format(Locale.ROOT,
                        "height %d   difficulty %.2f   retarget in %d blocks   network %s",
                        m.height(), m.difficulty(), m.blocksUntilRetarget(), rate(m.networkHashrate())));

                // ⚠ COMMITTED cycles drive every figure here; the slider only previews.
                //
                // The slider is a request and m.cycles() is what the rig is actually doing, and they
                // differ for as long as the player is dragging. Mixing them produced a panel that
                // disagreed with itself — "94 cycles" beside "0.00 EC/hr" — which is exactly the kind
                // of readout disagreement docs/design/04 §3.1 trains players to read as evidence of
                // an intruder. So the committed state is stated, and a pending change is stated as
                // pending, on its own line, priced by the ENGINE rather than scaled here.
                long chosen = (long) allocation.getValue();
                if (m.cycles() <= 0) {
                    rigLine.setText("this rig is not mining");
                } else {
                    rigLine.setText(String.format(Locale.ROOT,
                            "this rig %s from %d cycles — %.2f%% of the chain",
                            rate(m.hashrate()), m.cycles(),
                            100.0d * m.hashrate() / Math.max(1L, m.networkHashrate())));
                }

                if (m.cycles() <= 0) {
                    projection.setText("Not mining. Commit cycles and press Allocate — they earn "
                            + "only while the client is open.");
                    odds.setText("");
                } else {
                    // Labelled an expectation rather than a rate, because for solo it is one draw in
                    // four hours and calling that "40 EC/hr" would be the most misleading true
                    // sentence on the panel.
                    projection.setText(String.format(Locale.ROOT,
                            "%.2f EC per %s, about one every %s  →  %.2f EC/hr expected",
                            m.payoutMinorUnits() / 100.0d,
                            // The payout EVENT differs by scheme: a block, a share, or a cut of a
                            // block the pool found. One word for all three would undo the
                            // distinction mining-pool(7) exists to teach.
                            isSolo ? "block"
                                    : m.pool() != null && m.pool().scheme() == PoolScheme.PPS
                                            ? "share"
                                            : "payout",
                            humanSeconds(m.expectedPayoutSeconds()),
                            m.expectedMinorUnitsPerHour() / 100.0d));
                    String pending = m.pendingMinorUnits() > 0
                            ? String.format(Locale.ROOT, "   %.2f EC held by the pool, paid in %ds",
                                    m.pendingMinorUnits() / 100.0d, m.secondsUntilSettle())
                            : "";
                    odds.setText(String.format(Locale.ROOT,
                            "%.0f%% chance of at least one in the next hour, %.0f%% in eight.%s",
                            100 * m.chanceWithin(3600), 100 * m.chanceWithin(8 * 3600), pending));
                }

                if (chosen == m.cycles()) {
                    preview.setText("");
                    preview.setVisible(false);
                    preview.setManaged(false);
                } else {
                    preview.setVisible(true);
                    preview.setManaged(true);
                    preview.setText(chosen <= 0
                            ? "Allocate would STOP mining."
                            : String.format(Locale.ROOT,
                                    "Allocate would commit %d cycles → %.2f EC/hr expected.",
                                    chosen, session.miningRateFor(chosen) / 100.0d));
                }

                if (isSolo) {
                    solo.setSelected(true);
                    modeNote.setText("Racing the whole chain for a whole block. No fee, and no floor "
                            + "— most hours pay nothing. Silent: the work is local and nothing leaves "
                            + "the rig until a block is found. A long gap does not make the next "
                            + "block likelier: every hash is an independent try against the same "
                            + "target, so nothing accumulates and nothing is owed.");
                } else {
                    pooled.setSelected(true);
                    modeNote.setText("Mining with a pool. Steady income, less the pool's fee. Only "
                            + "the fee changes what you earn — a pool's scheme and its size change "
                            + "only how lumpily you earn it. A pooled rig is faintly audible: it "
                            + "holds a connection to the pool and pushes a share up it on a timer. "
                            + "No heat, and nothing can seize it.");
                }
                poolHeading.setVisible(!isSolo);
                poolHeading.setManaged(!isSolo);
                poolList.setVisible(!isSolo);
                poolList.setManaged(!isSolo);

                poolList.getChildren().clear();
                if (!isSolo) {
                    String joined = m.pool() == null ? "" : m.pool().id();
                    for (MiningPool pool : session.pools()) {
                        poolList.getChildren().add(poolRow(session, pool, joined, m, result));
                    }
                }
            }
        };

        modes.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) {
                // A ToggleGroup lets the selected button be clicked off. Mining always has a mode,
                // so refuse the empty state rather than leaving the panel describing nothing.
                if (was != null) {
                    was.setSelected(true);
                }
                return;
            }
            MiningMode wanted = now == solo ? MiningMode.SOLO : MiningMode.POOLED;
            if (session.miningChain().mode() != wanted) {
                GameSession.Outcome outcome = session.setMiningMode(wanted);
                result.setText(outcome.message());
                styleByOutcome(result, outcome);
            }
            refresh.run();
        });
        allocation.valueProperty().addListener((o, was, now) -> refresh.run());

        // The slider starts where the rig actually is, not at zero. A control reading 0 while the
        // monitor beside it reads 30 is the same disagreement described above, and it also means the
        // first thing "Allocate" does is silently release every committed cycle.
        allocation.setValue(session.mining().selfMiningCycles());

        apply.setOnAction(e -> {
            GameSession.Outcome outcome = session.allocateSelfMining((long) allocation.getValue());
            result.setText(outcome.message());
            styleByOutcome(result, outcome);
            refresh.run();
        });
        collect.setOnAction(e -> {
            GameSession.Outcome outcome = session.collect();
            result.setText(outcome.message());
            styleByOutcome(result, outcome);
        });

        refresh.run();
        session.onChange(s -> refresh.run());

        root.getChildren().addAll(
                wrapped("Self-mining is the floor: safe, silent, generates no heat, and cannot be "
                        + "seized — but it only earns while the client is open. Deployed miners are "
                        + "the only offline income, and their buffer caps."),
                new Separator(),
                new Label("CHAIN"), chain, rigLine,
                new Separator(),
                new Label("BALANCE"), current,
                new Label("SELF-MINING ALLOCATION"), allocation, projection, odds, preview,
                new Separator(),
                new Label("PAYOUT"), new HBox(8, pooled, solo), modeNote,
                poolHeading, poolList,
                new Separator(),
                new HBox(8, apply, collect), result);
        return scrollable(root);
    }

    /**
     * One pool, as a selectable row.
     *
     * <p>Shows fee and interval side by side deliberately. They are the two axes of the choice and
     * they pull against each other — the cheapest pool on the list pays least often — so a row that
     * showed only the fee would read as a ladder with an obvious top.
     */
    private static Region poolRow(
            GameSession session, MiningPool pool, String joinedId, MiningSnapshot m, Label result) {
        boolean joined = pool.id().equals(joinedId);

        Label name = Ui.value(pool.name());
        Label scheme = Ui.micro(pool.scheme().name());
        scheme.getStyleClass().add("es-legend-sub");
        HBox title = Ui.row(UiTokens.SPACE_5, name, Ui.spacer(), scheme);

        // The interval is the pool's, not this rig's current one — the rig may be on another pool.
        double interval = pool.scheme() == PoolScheme.PPLNS
                ? m.difficulty() <= 0 ? 0 : 600.0d / Math.max(0.0001d, pool.networkShare())
                : pool.shareSeconds();

        FlowPane facts = new FlowPane(UiTokens.SPACE_5, UiTokens.SPACE_2);
        facts.setAlignment(Pos.BASELINE_LEFT);
        facts.getChildren().addAll(
                KeyValue.of("Fee", pool.feeText()),
                KeyValue.of("Chain", pool.shareText()),
                KeyValue.of("Pays", "every " + humanSeconds(interval)));

        VBox box = new VBox(UiTokens.SPACE_2, title, facts, secondary(pool.blurb()));
        if (!pool.caution().isBlank()) {
            // Not a warning glyph: U+26A0 is in neither bundled font and GlyphCoverageTest fails
            // the build on it. A host fallback would be a different shape and a different advance
            // width per platform. The word carries it, and docs/client/07 §5.2 wants it to anyway —
            // a mark alone is a private code.
            Label caution = wrapped("Note — " + pool.caution());
            caution.getStyleClass().add("es-text-secondary");
            box.getChildren().add(caution);
        }
        box.getStyleClass().add("es-row");
        if (joined) {
            box.getStyleClass().add("es-row-armed");
        }
        // ⚠ A Region is picked where its background PAINTS, and `.es-row` paints one only on :hover
        // — so at rest the padding and the gaps between the title, the facts and the blurb are
        // holes. A click on a word selected the row; a click two pixels below it did nothing. This
        // same bug has now been fixed on five surfaces.
        box.setPickOnBounds(true);
        Cursors.shared().clickable(box);
        box.setAccessibleText((joined ? "Joined. " : "") + pool.name() + ", " + pool.scheme()
                + ", fee " + pool.feeText() + ", " + pool.shareText() + " of the chain, pays every "
                + humanSeconds(interval) + ". " + pool.blurb()
                + (pool.caution().isBlank() ? "" : " Caution: " + pool.caution()));
        box.setOnMouseClicked(event -> {
            event.consume();
            GameSession.Outcome outcome = session.setMiningPool(pool.id());
            result.setText(outcome.message());
            styleByOutcome(result, outcome);
        });
        return box;
    }

    /** A hashrate, in the units a mining readout uses. */
    private static String rate(long perSecond) {
        String[] units = {"H/s", "kH/s", "MH/s", "GH/s", "TH/s"};
        double value = perSecond;
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }

    private static String humanSeconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return "never";
        }
        long total = Math.round(seconds);
        if (total < 90) {
            return total + "s";
        }
        if (total < 5400) {
            return Math.round(total / 60.0d) + "m";
        }
        return String.format(Locale.ROOT, "%.1fh", total / 3600.0d);
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
    /**
     * Money, in two views: the chain everyone shares, and the ledger that is yours.
     *
     * <h2>Two tabs, because they answer different questions at different scopes</h2>
     *
     * {@link LedgerTab#CHAIN} is the explorer — height, difficulty, the mempool, recent blocks. {@link
     * LedgerTab#LEDGER} is the audit trail for one balance. Stacked in one column the explorer pushed
     * the transaction table below the fold, so the readout a player opens this window to check was the
     * one they had to scroll for.
     *
     * <h2>⚠ Two clocks, because half of this panel is time-derived and the session is not a clock</h2>
     *
     * A block lands every fourteen minutes, so {@code session.onChange} fires <b>about four times an
     * hour</b> — measured: eight fires in ninety minutes on an idle rig. Everything here that reads
     * "22m ago" is derived from the wall clock rather than from game state, so a panel repainted only
     * on data change freezes every age between blocks and then jumps them all fourteen minutes at
     * once when one lands. Reported as "these blocks never update and are not counting down", which
     * is exactly what it looks like from outside.
     *
     * <p>So there are two refreshes and they do different work. {@code refreshData} rebuilds
     * structure and runs on session change. {@code refreshClock} runs every second and touches only
     * the time-derived text. Rebuilding the cards and the table every second instead would fight the
     * player's own scroll position and selection — which is why the table gets {@code refresh()}
     * (re-render the cells, keep the items) rather than {@code setAll}.
     *
     * <p>This is the same lesson {@code RigMonitorView} already carries for the process table: the
     * figures advance whether or not the game does, and a readout that froze the moment the player
     * stopped doing anything would be stale exactly when they were reading it.
     *
     * <p>⚠ The address and balance sit <b>above</b> the tabs, not inside one. They are the window's
     * subject rather than one view of it: the address is what a player scans a block's transactions
     * for, and the balance is what the transaction table reconciles against. Behind a tab, checking
     * one would mean switching away from the other — and {@code docs/design/04-mining.md} §3.1's audit
     * is exactly the act of holding both at once.
     *
     * <h2>Ethereum's shapes, this chain's mechanics</h2>
     *
     * Addresses are {@code 0x} + 40 hex and hashes {@code 0x} + 64, and the block cards carry
     * pre-Merge Ethereum's header fields — which was itself a proof-of-work chain, so nothing here is
     * borrowed dishonestly. Gas is real arithmetic: every transaction on this chain is a plain value
     * transfer at 21 000 gas, so a block's fill bar is its transaction count and nothing else.
     *
     * <h2>⚠ One list, two renderings</h2>
     *
     * The transaction table below is {@code session.chainTransactions()}, which is the same ledger
     * {@code ledger(1)} prints — same amounts, same moments, same running balance. That is not a
     * convenience: {@code docs/design/04-mining.md} §3.1 makes "add these up and compare against the
     * balance" the way a player catches a hidden miner, so two surfaces that could disagree would
     * turn the game's central investigation into a false-positive generator.
     */
    public static Region ledger(GameSession session) {
        VBox root = panel("LEDGER");

        Label address = new Label();
        address.getStyleClass().add("es-mono");
        Label balance = new Label();
        balance.getStyleClass().addAll("es-numeric", "es-compute");
        Label chainLine = new Label();
        chainLine.getStyleClass().addAll("es-mono", "es-text-secondary");

        HBox upcoming = new HBox(UiTokens.SPACE_3);
        upcoming.setAlignment(Pos.CENTER_LEFT);
        Label mempoolLine = new Label();
        mempoolLine.getStyleClass().addAll("es-mono", "es-text-secondary");
        mempoolLine.setWrapText(true);

        HBox blocks = new HBox(UiTokens.SPACE_3);
        blocks.setAlignment(Pos.CENTER_LEFT);
        ScrollPane blockStrip = new ScrollPane(blocks);
        blockStrip.setFitToHeight(true);
        blockStrip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        blockStrip.setMinHeight(150);
        blockStrip.setPrefHeight(150);
        blockStrip.getStyleClass().add("es-block-strip");

        Label detail = new Label();
        detail.getStyleClass().add("es-mono");
        detail.setWrapText(true);
        detail.setVisible(false);
        detail.setManaged(false);

        TableView<ChainTransaction> table = new TableView<>();
        table.setPlaceholder(new Label("No transactions yet. Mine something."));

        TableColumn<ChainTransaction, String> hash = new TableColumn<>("Tx hash");
        hash.setCellValueFactory(c -> text(c.getValue().shortHash()));
        hash.setPrefWidth(140);

        TableColumn<ChainTransaction, String> block = new TableColumn<>("Block");
        // ⚠ A dash, not a zero. A pool payout never touched the chain — the pool paid it out of its
        // own balance — and printing a block number would claim a miner mined it.
        block.setCellValueFactory(c -> text(c.getValue().blockNumber() < 0
                ? "—" : String.valueOf(c.getValue().blockNumber())));
        block.setPrefWidth(80);

        TableColumn<ChainTransaction, String> when = new TableColumn<>("Age");
        when.setCellValueFactory(c -> text(age(c.getValue().at())));
        when.setPrefWidth(80);

        TableColumn<ChainTransaction, String> from = new TableColumn<>("From");
        from.setCellValueFactory(c -> text(c.getValue().coinbase()
                ? "coinbase" : ChainBlock.shorten(c.getValue().from())));
        from.setPrefWidth(130);

        TableColumn<ChainTransaction, String> to = new TableColumn<>("To");
        to.setCellValueFactory(c -> text(ChainBlock.shorten(c.getValue().to())));
        to.setPrefWidth(130);

        TableColumn<ChainTransaction, String> value = new TableColumn<>("Value");
        value.setCellValueFactory(c -> text((c.getValue().incoming() ? "+" : "−")
                + money(c.getValue().valueMinorUnits())));
        value.setPrefWidth(110);

        TableColumn<ChainTransaction, String> after = new TableColumn<>("Balance after");
        after.setCellValueFactory(c -> text(money(c.getValue().balanceAfterMinorUnits())));
        after.setPrefWidth(120);

        TableColumn<ChainTransaction, String> what = new TableColumn<>("What");
        what.setCellValueFactory(c -> text(c.getValue().description()));
        what.setPrefWidth(280);

        table.getColumns().addAll(List.of(hash, block, when, from, to, value, after, what));
        VBox.setVgrow(table, Priority.ALWAYS);
        // The table is the whole point of its tab, so it takes the height rather than sitting at its
        // preferred size with dead space under it.
        table.setMinHeight(320);

        // Time-derived text, re-rendered on the one-second clock rather than on a data change. Each
        // entry redraws one label from the wall clock; the list is rebuilt whenever the cards are.
        List<Runnable> ticking = new ArrayList<>();

        // Two panes, one strip. Same bracket-selected chips the rig monitor draws (§4.4) — two tab
        // strips in one deck indicating selection differently would be two conventions to learn.
        LedgerTab[] tab = {LedgerTab.CHAIN};
        VBox chainPane = new VBox(UiTokens.SPACE_3);
        VBox ledgerPane = new VBox(UiTokens.SPACE_3);
        VBox.setVgrow(ledgerPane, Priority.ALWAYS);

        HBox tabs = Ui.row(UiTokens.SPACE_3);
        tabs.getStyleClass().add("es-breach-picker");
        List<BreachView.Chip> tabChips = new ArrayList<>();
        Runnable[] applyTab = new Runnable[1];
        for (LedgerTab which : LedgerTab.values()) {
            BreachView.Chip chip = new BreachView.Chip(which.control(LedgerTab.CHAIN), "es-breach-chip-quiet");
            chip.setAccessibleText(which == LedgerTab.CHAIN
                    ? "Show the chain: height, difficulty, the mempool and recent blocks."
                    : "Show your own transactions, newest first.");
            chip.onInvoke(() -> {
                tab[0] = which;
                applyTab[0].run();
            });
            tabChips.add(chip);
            tabs.getChildren().add(chip);
        }
        applyTab[0] = () -> {
            for (int i = 0; i < tabChips.size(); i++) {
                LedgerTab which = LedgerTab.values()[i];
                BreachView.Chip chip = tabChips.get(i);
                chip.setText(Ui.upper(which.control(tab[0])));
                chip.getStyleClass().remove("es-breach-chip-loud");
                if (which == tab[0]) {
                    chip.getStyleClass().add("es-breach-chip-loud");
                }
            }
            tabVisible(chainPane, tab[0] == LedgerTab.CHAIN);
            tabVisible(ledgerPane, tab[0] == LedgerTab.LEDGER);
        };

        Runnable refreshData = () -> {
            MiningSnapshot m = session.miningChain();
            address.setText(session.chainAddress());
            balance.setText(String.format(
                    Locale.ROOT, "%.2f EC", session.balance().minorUnits() / 100.0d));
            chainLine.setText(String.format(Locale.ROOT,
                    "height %d   difficulty %.2f   a block every ~%d min   retarget in %d",
                    m.height(), m.difficulty(),
                    Math.round(Balance.CHAIN_TARGET_BLOCK_SECONDS / 60.0d), m.blocksUntilRetarget()));

            ChainMempool pool = session.mempool();
            upcoming.getChildren().clear();
            for (ChainMempool.ProjectedBlock p : pool.projected()) {
                upcoming.getChildren().add(projectedCard(p, pool));
            }

            blocks.getChildren().clear();
            ticking.clear();
            for (ChainBlock b : session.chainBlocks()) {
                blocks.getChildren().add(blockCard(session, b, detail, ticking));
            }
            if (blocks.getChildren().isEmpty()) {
                blocks.getChildren().add(secondary("No blocks yet — the chain mints one every "
                        + Math.round(Balance.CHAIN_TARGET_BLOCK_SECONDS / 60.0d) + " minutes."));
            }
            table.getItems().setAll(session.chainTransactions(500));
        };
        refreshData.run();

        chainPane.getChildren().addAll(
                new Label("CHAIN"), chainLine,
                new Label("MEMPOOL — NEXT BLOCKS"), mempoolLine, upcoming,
                new Label("RECENT BLOCKS"), blockStrip, detail);

        ledgerPane.getChildren().addAll(
                wrapped("Entries are added and never edited. Each row carries the balance after it, "
                        + "so the log reconciles without replaying it. A dash in the block column "
                        + "means the transaction never touched the chain."),
                table);

        Runnable refreshClock = () -> {
            ChainMempool pool = session.mempool();
            // ⚠ "on average", never a countdown. Blocks arrive on a Poisson schedule, so there is no
            // moment to count down to — printing one would be the same lie a mining progress bar is,
            // one step removed. The elapsed figure beside it is a fact, and a player comparing the two
            // is reading the distribution rather than a timer. Both move every second now, which is
            // what makes the comparison legible at all.
            mempoolLine.setText(String.format(Locale.ROOT,
                    "%d waiting from you · a block every ~%d min on average · last one %s ago · "
                            + "cheapest slot going for %.0f",
                    pool.yoursPending(),
                    Math.round(pool.expectedNextBlockSeconds() / 60),
                    humanSeconds(pool.secondsSinceLastBlock()),
                    pool.lowFeeRate()));
            for (Runnable age : ticking) {
                age.run();
            }
            // Re-render the cells without replacing the items, so the Age column advances while the
            // player's scroll position and selection survive. setAll here would fight them every
            // second, which is worse than a stale column.
            table.refresh();
        };

        refreshClock.run();
        applyTab[0].run();

        // ⚠ Pulse.every, not animate: this is not decoration, so it must survive reduced motion. A
        // player who turned animation off would otherwise be the one player whose block ages froze.
        AutoCloseable onSession = session.onChange(s -> refreshData.run());
        AutoCloseable clock = Pulse.shared().every(1_000, refreshClock);

        root.getChildren().addAll(
                new Label("YOUR ADDRESS"), address, balance,
                new Separator(),
                tabs,
                chainPane, ledgerPane);
        Region scrolled = scrollable(root);
        releaseOnDetach(root, onSession, clock);
        return scrolled;
    }

    /**
     * Stops a panel's clock and subscriptions when it leaves the scene.
     *
     * <p>⚠ Load-bearing rather than tidy. A one-second timer left running against a closed window
     * repaints a detached scene graph forever, and every re-open starts another — so the tenth time a
     * player opens the ledger the machine is doing ten times the work for one visible panel.
     */
    private static void releaseOnDetach(Region root, AutoCloseable... handles) {
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
            for (AutoCloseable handle : handles) {
                try {
                    handle.close();
                } catch (Exception ignored) {
                    // Nothing to recover: the panel is already gone and a failed unsubscribe is not
                    // something the player can act on.
                }
            }
        });
    }

    /** Shows or hides a tab's pane and takes it out of the layout with it. */
    private static void tabVisible(javafx.scene.Node node, boolean show) {
        node.setVisible(show);
        // ⚠ Managed as well as visible. A hidden-but-managed pane still claims its height, so the
        // inactive tab would leave a block of empty space above or below the active one.
        node.setManaged(show);
    }

    /**
     * One projected block: what the next one would hold if it were mined right now.
     *
     * <p>⚠ Styled apart from a mined block and labelled with a {@code ~}, because it has not happened.
     * A projection that looked like a block would be a promise the chain cannot make — more
     * transactions arrive meanwhile and a miner includes whatever it likes.
     */
    private static Region projectedCard(ChainMempool.ProjectedBlock p, ChainMempool pool) {
        Label head = Ui.value(p.index() == 0 ? "next" : "+" + (p.index() + 1));
        Label fill = new Label(cells(p.fullness(), 10));
        fill.getStyleClass().add("es-block-fill");

        VBox card = new VBox(UiTokens.SPACE_1,
                head,
                Ui.small(p.transactions() + " txs"),
                fill,
                Ui.micro(p.yours() == 0 ? "none yours" : p.yours() + " yours"),
                Ui.micro("~" + humanSeconds(p.expectedSeconds(pool.expectedNextBlockSeconds()))),
                Ui.micro("fees " + money(p.feesMinorUnits())));
        card.getStyleClass().addAll("es-block", "es-block-projected");
        if (p.yours() > 0) {
            card.getStyleClass().add("es-block-yours");
        }
        card.setMinWidth(112);
        card.setPickOnBounds(true);
        card.setAccessibleText("Projected block " + (p.index() + 1) + ": " + p.transactions()
                + " transactions, " + p.yours() + " of them yours, roughly "
                + humanSeconds(p.expectedSeconds(pool.expectedNextBlockSeconds()))
                + " away on average. Not a schedule — blocks arrive at random intervals.");
        return card;
    }

    /**
     * One block, as an explorer card.
     *
     * <p>The fill bar is gas used against the block's limit — cells rather than a smooth bar, because
     * {@code docs/design/ui-design-language.md} §4 forbids a continuous one for the same reason the
     * cycle grid is countable: a smooth bar implies a precision the model does not have.
     */
    private static Region blockCard(
            GameSession session, ChainBlock b, Label detail, List<Runnable> ticking) {
        Label height = Ui.value("#" + b.number());
        Label who = Ui.micro(b.minerLabel());
        who.getStyleClass().add("es-legend-sub");

        Label fill = new Label(cells(b.fullness(), 10));
        fill.getStyleClass().add("es-block-fill");

        // Registered on the panel's one-second clock. A block's age is the only thing on this card
        // that changes without the chain changing, and it is the first thing a player checks.
        Label when = Ui.micro(age(b.timestamp()) + " ago");
        ticking.add(() -> when.setText(age(b.timestamp()) + " ago"));

        VBox card = new VBox(UiTokens.SPACE_1,
                height,
                Ui.small(b.transactions() + " txs"),
                fill,
                Ui.micro(String.format(Locale.ROOT, "%.1f KB", b.sizeBytes() / 1024.0d)),
                when,
                who);
        card.getStyleClass().add("es-block");
        if (b.yours()) {
            // The one thing a player scans this strip for. Amber is reserved for income elsewhere in
            // the deck and a block you mined is exactly that.
            card.getStyleClass().add("es-block-yours");
        }
        card.setMinWidth(112);
        card.setPickOnBounds(true);
        Cursors.shared().clickable(card);
        card.setAccessibleText("Block " + b.number() + ", mined by " + b.minerLabel()
                + (b.yours() ? " — yours" : "") + ", " + b.transactions() + " transactions, "
                + age(b.timestamp()) + " ago. Select for the full header.");
        card.setOnMouseClicked(event -> {
            event.consume();
            detail.setVisible(true);
            detail.setManaged(true);
            // Fetched with its body, which is derived on demand rather than carried on every card —
            // a strip of 24 cards would otherwise build 24 full transaction lists to draw 24 headers.
            ChainBlock full = session.chainBlock(b.number());
            detail.setText(blockDetail(full == null ? b : full));
        });
        return card;
    }

    /**
     * A block's full header and every transaction in it.
     *
     * <p>⚠ The player's own rows are marked. In a body of up to two hundred transactions the one that
     * belongs to the reader is the only one they are looking for, and an explorer that made them
     * match hex strings by eye would be technically complete and practically useless.
     */
    private static String blockDetail(ChainBlock b) {
        List<String> out = new ArrayList<>(List.of(
                "number        " + b.number(),
                "hash          " + b.hash(),
                "parentHash    " + b.parentHash(),
                "timestamp     " + b.timestamp(),
                "miner         " + b.minerAddress() + "  (" + b.minerLabel() + ")",
                "difficulty    " + String.format(Locale.ROOT, "%.2f", b.difficulty()),
                "nonce         " + b.nonce(),
                "transactions  " + b.transactions(),
                "gasUsed       " + b.gasUsed() + " / " + b.gasLimit()
                        + String.format(Locale.ROOT, "  (%.1f%%)", b.fullness() * 100),
                "size          " + b.sizeBytes() + " bytes",
                "reward        " + money(b.rewardMinorUnits()) + " subsidy + "
                        + money(b.feesMinorUnits()) + " fees = " + money(b.minerTakeMinorUnits()),
                "",
                "  " + pad("#", 4) + pad("hash", 16) + pad("from", 16) + pad("to", 16)
                        + pad("value", 13) + pad("fee", 9) + "gas price"));
        int index = 0;
        for (ChainTransaction tx : b.body()) {
            out.add((tx.yours() ? "> " : "  ")
                    + pad(String.valueOf(index++), 4)
                    + pad(ChainBlock.shorten(tx.hash()), 16)
                    + pad(tx.coinbase() ? "coinbase" : ChainBlock.shorten(tx.from()), 16)
                    + pad(ChainBlock.shorten(tx.to()), 16)
                    + pad(money(tx.valueMinorUnits()), 13)
                    + pad(tx.coinbase() ? "—" : money(tx.feeMinorUnits()), 9)
                    + (tx.coinbase() ? "—" : String.format(Locale.ROOT, "%.1f", tx.gasPriceMinorUnits())));
        }
        if (b.body().isEmpty()) {
            out.add("  (select a block to load its transactions)");
        }
        return String.join("\n", out);
    }

    /** Left-aligned in a fixed column, so the body reads as a table in a monospaced label. */
    private static String pad(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() >= width) {
            return v.substring(0, Math.max(0, width - 1)) + " ";
        }
        return v + " ".repeat(width - v.length());
    }

    /** A discrete fill bar. Cells, never a continuous one — §4 of the design language. */
    private static String cells(double fraction, int width) {
        int on = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "\u2588".repeat(on) + "\u2591".repeat(Math.max(0, width - on));
    }

    /** How long ago, in the units an explorer uses. */
    private static String age(java.time.Instant at) {
        if (at == null) {
            return "—";
        }
        long seconds = java.time.Duration.between(at, java.time.Instant.now()).toSeconds();
        if (seconds < 0) {
            return "just now";
        }
        if (seconds < 90) {
            return seconds + "s";
        }
        if (seconds < 5400) {
            return (seconds / 60) + "m";
        }
        if (seconds < 172800) {
            return (seconds / 3600) + "h";
        }
        return (seconds / 86400) + "d";
    }

    private static javafx.beans.property.SimpleStringProperty text(String value) {
        return new javafx.beans.property.SimpleStringProperty(value);
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
        return scrollable(root);
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
        return scrollable(root);
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
                Button b = new Button((open ? "• " : "· ") + spec.title());
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
        root.getChildren().addAll(secondary("• open   · closed"), scroll);
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
        return settings(profile, themes, onDeskSettingsChanged, null);
    }

    /**
     * @param onRename applies a new operator name, or null when there is no live character —
     *     the menu opens this panel before a session exists, and a rename control that silently
     *     did nothing would be worse than one that says what it will affect
     */
    public static Region settings(
            ClientProfile profile,
            ThemeManager themes,
            Runnable onDeskSettingsChanged,
            java.util.function.Consumer<String> onRename) {
        VBox root = panel("SETTINGS");

        TextField handle = new TextField(profile.settings().soloHandle);
        handle.setPromptText("operator");
        Label handleResult = new Label();
        handleResult.setWrapText(true);
        Button applyHandle = new Button("Set name");
        Runnable rename = () -> {
            String wanted = handle.getText() == null ? "" : handle.getText().trim();
            String problem = validateHandle(wanted);
            if (problem != null) {
                handleResult.setText(problem);
                styleByOutcome(handleResult, GameSession.Outcome.refused(problem));
                return;
            }
            profile.settings().soloHandle = wanted;
            profile.save();
            if (onRename != null) {
                onRename.accept(wanted);
                handleResult.setText("Renamed. The strip and the log both show it now.");
            } else {
                handleResult.setText("Saved. It applies to the next character you start.");
            }
            styleByOutcome(handleResult, GameSession.Outcome.ok());
        };
        applyHandle.setOnAction(e -> rename.run());
        handle.setOnAction(e -> rename.run());

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

        // The desk wallpaper. Three states rather than a checkbox, because "I want the texture but
        // not the movement" is a real preference and WCAG 2.2.2 requires the pause to exist at all.
        ChoiceBox<io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode> wallpaper =
                new ChoiceBox<>();
        wallpaper.getItems()
                .addAll(io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode.selectable());
        wallpaper.setValue(io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode
                .byId(profile.settings().wallpaper)
                .orElse(io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode.DRIFT));
        wallpaper.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode m) {
                return m == null ? "" : m.label();
            }

            @Override
            public io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode fromString(String s) {
                return io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode.DRIFT;
            }
        });
        wallpaper.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                profile.settings().wallpaper = now.id();
                profile.save();
                onDeskSettingsChanged.run();
            }
        });

        CheckBox scanlines = new CheckBox("CRT scanlines");
        scanlines.setSelected(profile.settings().crtScanlines);
        scanlines.selectedProperty().addListener((o, was, now) -> {
            profile.settings().crtScanlines = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        CheckBox aberration = new CheckBox("Chromatic aberration");
        aberration.setSelected(profile.settings().crtAberration);
        aberration.selectedProperty().addListener((o, was, now) -> {
            profile.settings().crtAberration = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        // A slider rather than a checkbox: curvature is the one artefact with a useful middle. A
        // trace of rim aberration reads as glass; a lot of it reads as a cheap filter, and where the
        // line falls between those is taste, which is exactly what a slider is for.
        Slider curvature = new Slider(0, 100, profile.settings().crtCurvature);
        curvature.setShowTickMarks(true);
        curvature.setMajorTickUnit(25);
        curvature.setBlockIncrement(5);
        Label curvatureValue = io.github.stoicswe.eyeandsickle.client.ui.Ui.micro(
                profile.settings().crtCurvature + "%");
        curvature.valueProperty().addListener((o, was, now) -> {
            profile.settings().crtCurvature = (int) Math.round(now.doubleValue());
            curvatureValue.setText(profile.settings().crtCurvature + "%");
            profile.save();
            onDeskSettingsChanged.run();
        });

        CheckBox glitch = new CheckBox("Signal glitch");
        glitch.setSelected(profile.settings().crtGlitch);
        glitch.selectedProperty().addListener((o, was, now) -> {
            profile.settings().crtGlitch = now;
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

        CheckBox notify = new CheckBox("Show slide-in notices");
        notify.setSelected(profile.settings().notificationsEnabled);
        notify.selectedProperty().addListener((o, was, now) -> {
            profile.settings().notificationsEnabled = now;
            profile.save();
        });

        // The same numbers `log -p` takes, and the same backwards RFC 5424 ordering — a player who
        // learns it here has learned journalctl. Labelled with the consequence, not the number, but
        // the number is shown too so the transfer is visible.
        ChoiceBox<Integer> severity = new ChoiceBox<>();
        severity.getItems().addAll(3, 4, 5, 6, 7);
        severity.setValue(profile.settings().notifyMinSeverity);
        severity.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer level) {
                if (level == null) {
                    return "";
                }
                return switch (level) {
                    case 3 -> "3 · errors only";
                    case 4 -> "4 · warnings and worse";
                    case 5 -> "5 · notices and worse  (default)";
                    case 6 -> "6 · everything except debug";
                    default -> "7 · everything";
                };
            }

            @Override
            public Integer fromString(String s) {
                return 5;
            }
        });
        severity.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                profile.settings().notifyMinSeverity = now;
                profile.save();
            }
        });

        VBox facilities = new VBox(2);
        for (String facility : List.of("mining", "defense", "scan", "compute", "storage", "rig", "desk")) {
            CheckBox box = new CheckBox(facility);
            box.setSelected(!profile.settings().mutedFacilities.contains(facility));
            box.selectedProperty().addListener((o, was, now) -> {
                if (now) {
                    profile.settings().mutedFacilities.remove(facility);
                } else if (!profile.settings().mutedFacilities.contains(facility)) {
                    profile.settings().mutedFacilities.add(facility);
                }
                profile.save();
            });
            facilities.getChildren().add(box);
        }

        CheckBox reducedMotion = new CheckBox("Reduce motion");
        reducedMotion.setSelected(themes.reducedMotion());
        reducedMotion.selectedProperty().addListener((o, was, now) -> {
            themes.setReducedMotionOverride(now);
            profile.save();
        });

        root.getChildren()
                .addAll(
                        new Label("OPERATOR"),
                        new HBox(8, handle, applyHandle),
                        wrapped(onRename != null
                                ? "Your handle, shown on the strip with its bytes underneath. Solo "
                                        + "only: online, a handle is not yours to choose — identity "
                                        + "comes from an AT Proto DID and the server owns it."
                                : "Sets the handle for the next character you start. Renaming a "
                                        + "character you are already playing is done from inside "
                                        + "the game."),
                        handleResult,
                        new Separator(),
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
                        new Label("SCREEN"),
                        new Label("WALLPAPER"),
                        wallpaper,
                        wrapped("Machine texture behind every window — the same alphabet as the greeble "
                                + "strips, drawn far dimmer and never in amber. \"Still\" keeps the "
                                + "texture and stops the movement. Turning on Reduce motion below stops "
                                + "it too, without changing this setting."),
                        scanlines,
                        aberration,
                        glitch,
                        new Label("EDGE CURVATURE"),
                        new HBox(8, curvature, curvatureValue),
                        wrapped("Screen artefacts, all three off by default. Scanlines lay a dark band "
                                + "across every other row of pixels and drift slowly, with a refresh bar "
                                + "rolling down the screen — that is what makes them read as a tube "
                                + "rather than as a texture. They cost real contrast on body text, which "
                                + "is a trade to make deliberately rather than one the client makes for "
                                + "you. Aberration separates the wallpaper into red and cyan a pixel "
                                + "either side; it is not applied to the whole screen, which would cost "
                                + "more per frame than the effect is worth. Signal glitch tears short "
                                + "fragments off the edges of windows and the elements inside them, so a "
                                + "busy desk breaks up more than an empty one. Reduce motion stops every "
                                + "moving part and leaves the still ones drawn."),
                        wrapped("Edge curvature raises the red/cyan separation towards the rim and the "
                                + "corners, the way curved glass does — zero in the middle, worst at the "
                                + "corners. It does NOT bend the interface: warping the picture would "
                                + "need a shader we do not have, and faking it would put every click "
                                + "somewhere other than where you see the control. Text stays straight."),
                        new Separator(),
                        new Label("NOTICES"),
                        notify,
                        wrapped("A notice repeats something the rig already logged — nothing here is "
                                + "the only place a message exists, and the log window keeps all of "
                                + "it. Ignoring every notice costs you nothing."),
                        new Label("SEVERITY FLOOR"),
                        severity,
                        wrapped("These are RFC 5424 levels, and the numbering runs backwards on "
                                + "purpose: 0 is Emergency and 7 is Debug, so a LOWER number is a "
                                + "stricter filter. It is the same number `log -p` takes — set 4 "
                                + "here, type `log -p 4`, and you will see the same set. That habit "
                                + "works on any Linux machine you ever touch."),
                        new Label("SUBSYSTEMS"),
                        facilities,
                        wrapped("Unchecked subsystems stay silent. These are the rig's own facility "
                                + "names, so anything you mute here is still findable with "
                                + "`log | grep <name>`."),
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
        return scrollable(root);
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
        return scrollable(root);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Wraps a panel so it scrolls when the window is smaller than its contents.
     *
     * <p>Applied to every tool that does not already manage its own scrolling. The deck lets a
     * player size a window to anything above 240×120, so any panel without this simply clips —
     * and clipped content is silently missing rather than visibly cut off, which is the worst of
     * both. {@code docs/client/07-accessibility.md} also needs it: a player at 200% OS text scale
     * hits the bottom of the settings panel long before anyone testing at 100% does.
     *
     * <p>{@code setFitToWidth} matters as much as the scrolling: without it a ScrollPane gives its
     * content the content's own preferred width, so every wrapped label stops wrapping and the
     * panel grows a horizontal scrollbar instead of reflowing.
     */
    private static Region scrollable(Region content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-scroll");
        // Vertical only. A deck panel reflows to its width; a horizontal bar here would mean the
        // content refused to, which is a layout bug rather than something to scroll past.
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /**
     * Whether a handle is usable, or why not.
     *
     * <p>Restricted to printable ASCII because the strip prints the name's <b>bytes</b> beneath it,
     * and a name whose hex ran to three pairs per glyph would make that readout unreadable rather
     * than instructive. The length cap is the same reasoning: the strip elides past ten bytes, and
     * a name that is always elided is a name the player never actually sees.
     *
     * @return null when the handle is fine
     */
    private static String validateHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            return "A handle cannot be blank.";
        }
        if (handle.length() > 24) {
            return "Too long — 24 characters at most.";
        }
        for (char c : handle.toCharArray()) {
            if (c < 0x20 || c > 0x7E) {
                return "Printable ASCII only: the strip shows this name as bytes, and a "
                        + "multi-byte character would print several pairs for one glyph.";
            }
        }
        return null;
    }

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
