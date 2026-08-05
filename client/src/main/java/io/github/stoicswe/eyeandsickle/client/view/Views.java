package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeId;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.protocol.game.BlockContribution;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
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
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
        Label hint = wrapped(t(
                "ui.views.three-views-of-your",
                "Three views of your own rig. They should agree. When they do not, something is "
                        + "hiding — a connection with no owning process, or storage that grew while "
                        + "nothing was running. That discrepancy is the game."));
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
        Label heading = new Label(
                t("ui.views.scan-search-your-own", "SCAN — search your own rig for what routine listings miss"));
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
        Label poolHeading = new Label(t("ui.views.pool", "POOL"));
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
                current.setText(session.balance().toString());

                chain.setText(String.format(
                        Locale.ROOT,
                        "height %d   difficulty %.2f   retarget in %d blocks   network %s",
                        m.height(),
                        m.difficulty(),
                        m.blocksUntilRetarget(),
                        rate(m.networkHashrate())));

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
                    rigLine.setText(String.format(
                            Locale.ROOT,
                            "this rig %s from %d cycles — %.2f%% of the chain",
                            rate(m.hashrate()),
                            m.cycles(),
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
                    projection.setText(String.format(
                            Locale.ROOT,
                            "%s per %s, about one every %s  →  %s/hr expected",
                            // ⚠ Both are EXPECTATIONS — a long-run payout and a long-run rate, both
                            // derived through the network hashrate, which is a double. Printed exact
                            // they read `0.333333333333333361 EC per share`. The pending figure below
                            // is NOT approximated: that is money the pool actually owes.
                            Ethecoin.formatApprox(m.payoutWei(), 4),
                            // The payout EVENT differs by scheme: a block, a share, or a cut of a
                            // block the pool found. One word for all three would undo the
                            // distinction mining-pool(7) exists to teach.
                            isSolo
                                    ? "block"
                                    : m.pool() != null && m.pool().scheme() == PoolScheme.PPS ? "share" : "payout",
                            humanSeconds(m.expectedPayoutSeconds()),
                            Ethecoin.formatApprox(m.expectedWeiPerHour(), 4)));
                    String pending = m.pendingWei().signum() > 0
                            ? String.format(
                                    Locale.ROOT,
                                    "   %s held by the pool, paid in %ds",
                                    Ethecoin.format(m.pendingWei()),
                                    m.secondsUntilSettle())
                            : "";
                    odds.setText(String.format(
                            Locale.ROOT,
                            "%.0f%% chance of at least one in the next hour, %.0f%% in eight.%s",
                            100 * m.chanceWithin(3600),
                            100 * m.chanceWithin(8 * 3600),
                            pending));
                }

                if (chosen == m.cycles()) {
                    preview.setText("");
                    preview.setVisible(false);
                    preview.setManaged(false);
                } else {
                    preview.setVisible(true);
                    preview.setManaged(true);
                    preview.setText(
                            chosen <= 0
                                    ? "Allocate would STOP mining."
                                    : String.format(
                                            Locale.ROOT,
                                            "Allocate would commit %d cycles → %s/hr expected.",
                                            chosen,
                                            Ethecoin.formatApprox(session.miningRateFor(chosen), 4)));
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

        root.getChildren()
                .addAll(
                        wrapped(t(
                                "ui.views.self-mining-is-the",
                                "Self-mining is the floor: safe, silent, generates no heat, and cannot be "
                                        + "seized — but it only earns while the client is open. Deployed miners are "
                                        + "the only offline income, and their buffer caps.")),
                        new Separator(),
                        new Label(t("ui.views.chain", "CHAIN")),
                        chain,
                        rigLine,
                        new Separator(),
                        new Label(t("ui.views.balance", "BALANCE")),
                        current,
                        new Label(t("ui.views.self-mining-allocation", "SELF-MINING ALLOCATION")),
                        allocation,
                        projection,
                        odds,
                        preview,
                        new Separator(),
                        new Label(t("ui.views.payout", "PAYOUT")),
                        new HBox(8, pooled, solo),
                        modeNote,
                        poolHeading,
                        poolList,
                        new Separator(),
                        new HBox(8, apply, collect),
                        result);
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
        facts.getChildren()
                .addAll(
                        KeyValue.of("Fee", pool.feeText()),
                        KeyValue.of("Chain", pool.shareText()),
                        KeyValue.of("Pays", "every " + humanSeconds(interval)));

        VBox box = new VBox(UiTokens.SPACE_2, title, facts, secondary(pool.blurb()));
        if (!pool.caution().isBlank()) {
            // Not a warning glyph: U+26A0 is in neither bundled font and GlyphCoverageTest fails
            // the build on it. A host fallback would be a different shape and a different advance
            // width per platform. The word carries it, and docs/client/07 §5.2 wants it to anyway —
            // a mark alone is a private code.
            Label caution = wrapped(t("ui.views.note", "Note — " + pool.caution()));
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

        Label atRisk = new Label();
        atRisk.getStyleClass().addAll("es-mono", "es-text-secondary");
        atRisk.setWrapText(true);

        // Which slot is selected, by item id. Selection replaces the per-row button cluster: three
        // buttons on every one of up to 86 rows is 258 controls to tab through, and §5.4's decision
        // is about one item at a time.
        String[] selected = {null};

        VBox tiers = new VBox(UiTokens.SPACE_5);
        boolean[] grid = {true};

        HBox moves = Ui.row(UiTokens.SPACE_3);
        moves.setAlignment(Pos.CENTER_LEFT);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            tiers.getChildren().clear();
            int exposedOnline = session.items(StorageTier.STANDARD_STORAGE).size();
            int exposedAlways = session.items(StorageTier.HIGH_HACKABLE_ZONE).size();
            int safe = session.items(StorageTier.VAULT).size();

            // ⚠ Counts only. `docs/client/06` §5.2 permits an `est. N EC to replace` figure under
            // three conditions, one of which is that it counts only EC-gated items — and the client
            // is not told an item's gate or its price. A fabricated total on the one screen whose
            // job is to say what a raid would cost is worse than no total, so it is absent rather
            // than approximated. Tracked with RI-10.
            atRisk.setText("AT RISK NOW   " + (exposedOnline + exposedAlways) + " items"
                    + "   ·   exposed while online " + exposedOnline
                    + "   ·   always exposed " + exposedAlways
                    + "   ·   safe in vault " + safe);

            for (StorageTier tier : StorageTier.values()) {
                tiers.getChildren().add(tierSection(session, tier, grid[0], selected, refresh[0], result));
            }

            moves.getChildren().clear();
            GameSession.InventoryItem picked = findItem(session, selected[0]);
            if (picked == null) {
                moves.getChildren().add(secondary("Select a slot to move what is in it."));
            } else {
                moves.getChildren().add(Ui.small(picked.displayName() + "  →"));
                // ⚠ The FULL id here, not the short one. This is the inspect surface — it is what
                // `verify <id>` takes, and a truncated identifier that looks copyable and is not is
                // worse than none.
                Label pickedId = Ui.micro(picked.itemId());
                pickedId.getStyleClass().add("es-slot-id");
                moves.getChildren().add(pickedId);
                for (StorageTier target : StorageTier.values()) {
                    if (target == picked.tier()) {
                        continue;
                    }
                    Button move = new Button(Ui.upper(shortTier(target)));
                    move.setMinHeight(26);
                    move.setTooltip(new javafx.scene.control.Tooltip(
                            "mv \"" + picked.displayName() + "\" " + shortTier(target) + "\n\n" + exposureOf(target)));
                    move.setAccessibleText(
                            "Move " + picked.displayName() + " to " + shortTier(target) + ". " + exposureOf(target));
                    move.setOnAction(e -> {
                        GameSession.Outcome outcome = session.moveItem(picked.itemId(), target);
                        result.setText(outcome.message());
                        styleByOutcome(result, outcome);
                        refresh[0].run();
                    });
                    moves.getChildren().add(move);
                }
            }
        };

        HBox modes = Ui.row(UiTokens.SPACE_3);
        modes.getStyleClass().add("es-breach-picker");
        BreachView.Chip gridChip = new BreachView.Chip("[ GRID ]", "es-breach-chip-quiet");
        BreachView.Chip rowChip = new BreachView.Chip("  ROWS  ", "es-breach-chip-quiet");
        Runnable applyMode = () -> {
            gridChip.setText(grid[0] ? "[ GRID ]" : "  GRID  ");
            rowChip.setText(grid[0] ? "  ROWS  " : "[ ROWS ]");
            gridChip.getStyleClass().remove("es-breach-chip-loud");
            rowChip.getStyleClass().remove("es-breach-chip-loud");
            (grid[0] ? gridChip : rowChip).getStyleClass().add("es-breach-chip-loud");
            refresh[0].run();
        };
        gridChip.onInvoke(() -> {
            grid[0] = true;
            applyMode.run();
        });
        rowChip.onInvoke(() -> {
            grid[0] = false;
            applyMode.run();
        });
        gridChip.setAccessibleText("Show storage as a grid of slots.");
        rowChip.setAccessibleText("Show storage as rows, one item per line.");
        modes.getChildren().addAll(gridChip, rowChip);

        applyMode.run();
        session.onChange(s -> refresh[0].run());

        ScrollPane scroll = new ScrollPane(tiers);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren()
                .addAll(
                        atRisk,
                        new Separator(),
                        wrapped(t(
                                "ui.views.moving-something-changes-what",
                                "Moving something changes what can happen to it. That risk change is the "
                                        + "decision — each mount says what it means, and `mv <item> <tier>` does the "
                                        + "same thing from the terminal.")),
                        modes,
                        scroll,
                        moves,
                        result);
        return root;
    }

    /**
     * One mount, as a grid of slots or as rows.
     *
     * <h2>Why the grid draws EMPTY slots, and why that is the whole point</h2>
     *
     * {@code docs/design/01-core-resources.md} §6 makes storage a strict capacity/exposure trade and
     * Invariant I12 makes vault capacity the scarce half of it. A list of what you own cannot show
     * scarcity — six items in a six-slot vault and six in a sixty-slot zone render identically. A
     * grid of {@code n} filled cells against {@code capacity} cells shows "two slots left" without
     * anyone having to read a number, which is the question the vault actually poses.
     *
     * <p>⚠ Over-capacity is drawn, never clamped. Nothing enforces these numbers yet
     * ({@code Balance.storageCapacity}), so a tier can hold more than it has slots for; the extra
     * cells are marked rather than hidden, because a grid that dropped items to make its own
     * arithmetic work would be lying about what the player owns.
     *
     * <p>Rows remain available because {@code docs/client/06} §7.2 is right that a grid puts every
     * value at a different x-coordinate — that argument governs the <em>inventory</em>, which is
     * sorted and compared on three costs at once. This window is sorted by exposure and compares
     * nothing, so the grid is the better default here and the toggle keeps §7.2's case reachable.
     */
    private static Region tierSection(
            GameSession session, StorageTier tier, boolean grid, String[] selected, Runnable refresh, Label result) {
        var items = session.items(tier);
        int capacity = session.storageCapacity(tier);

        VBox box = new VBox(UiTokens.SPACE_3);
        box.getStyleClass().add("es-panel");
        // ⚠ The whole mount is the drop target, not each empty slot. Drag events bubble, so a drop
        // anywhere in the section lands — including on a filled slot, on the heading, or on the gap
        // below the last row. Making the player hit one 104px cell to change an item's exposure
        // would be a dexterity test in front of a risk decision.
        itemDropTarget(box, tier, session, result, refresh);

        Label heading = new Label(mountOf(tier) + "   " + items.size() + " / " + capacity);
        heading.getStyleClass().addAll("es-mono", "es-panel-title");
        // ⚠ §5.3: the state that matters is the one the window can never be showing, because the
        // player is online whenever they are looking at it. So it is stated in words instead.
        Label offline = Ui.micro("when you log off: " + offlineFateOf(tier));
        box.getChildren().addAll(heading, secondary(exposureOf(tier)), offline);

        if (grid) {
            FlowPane slots = new FlowPane(UiTokens.SPACE_1, UiTokens.SPACE_1);
            for (GameSession.InventoryItem item : items) {
                slots.getChildren().add(slot(item, selected, refresh));
            }
            for (int i = items.size(); i < capacity; i++) {
                slots.getChildren().add(slot(null, selected, refresh));
            }
            if (items.isEmpty() && capacity == 0) {
                slots.getChildren().add(secondary("(no capacity reported)"));
            }
            box.getChildren().add(slots);
        } else {
            if (items.isEmpty()) {
                box.getChildren().add(secondary("(empty)"));
            }
            for (GameSession.InventoryItem item : items) {
                Label row = new Label(
                        item.displayName() + (item.equipped() ? "  [equipped]" : "") + "   " + shortId(item.itemId()));
                row.getStyleClass().add("es-mono");
                row.setTooltip(new javafx.scene.control.Tooltip(item.itemId()));
                if (item.itemId().equals(selected[0])) {
                    row.getStyleClass().add("es-slot-selected");
                }
                row.setOnMouseClicked(e -> {
                    selected[0] = item.itemId();
                    refresh.run();
                });
                // Rows drag too. The mechanic belongs to the item, not to the presentation, and a
                // player who switched to ROWS has not asked to give up dragging.
                draggableItem(row, item);
                box.getChildren().add(row);
            }
        }
        return box;
    }

    /**
     * The clipboard type an in-flight item drag carries.
     *
     * <h2>⚠ A {@link javafx.scene.input.DataFormat} is a process-wide singleton and constructing one
     * twice throws</h2>
     *
     * {@code new DataFormat(mime)} registers the mime type globally and raises
     * {@code IllegalArgumentException} if that type already exists — so a naive {@code static final
     * DataFormat} in a class that is loaded once is fine, and the same line inside a method, or in a
     * class reloaded by a test harness, is a crash on the second call. Looking it up first makes it
     * idempotent.
     *
     * <p>A custom type rather than {@code DataFormat.PLAIN_TEXT}, so an item drag cannot be
     * satisfied by any text dragged in from another application — a drop handler that accepted a
     * stray string would move whatever item id it happened to parse.
     */
    private static final javafx.scene.input.DataFormat STORAGE_ITEM =
            javafx.scene.input.DataFormat.lookupMimeType("application/x-eyeandsickle-item") != null
                    ? javafx.scene.input.DataFormat.lookupMimeType("application/x-eyeandsickle-item")
                    : new javafx.scene.input.DataFormat("application/x-eyeandsickle-item");

    /**
     * Makes a node draggable as an item.
     *
     * <p>The dragboard carries the item <b>id</b> and nothing else. The destination looks the item
     * up through the session to find out what tier it is in — carrying the tier on the clipboard
     * would be a second copy of a fact the session already owns, and a stale one the moment anything
     * else moved the item.
     */
    private static void draggableItem(Region node, GameSession.InventoryItem item) {
        node.setOnDragDetected(e -> {
            javafx.scene.input.Dragboard board = node.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.put(STORAGE_ITEM, item.itemId());
            board.setContent(content);
            // The cell itself as the drag image, so the thing under the cursor is the thing being
            // moved. A default drag view is the OS's generic document icon, which on a deck that
            // draws its own pointer is the one place the host would show through.
            board.setDragView(node.snapshot(null, null));
            e.consume();
        });
    }

    /**
     * Makes a node accept an item drop into {@code tier}.
     *
     * <h2>⚠ The refresh is deferred, and it has to be</h2>
     *
     * A successful drop rebuilds the whole storage panel — {@code refresh} clears the tier boxes and
     * builds new ones — which destroys the node the drag gesture is still running on. Doing that
     * inside the drop handler detaches the source before {@code DRAG_DONE} is delivered, and the
     * gesture ends on a node that is no longer in a scene. {@code runLater} lets the gesture finish
     * first and then repaints.
     *
     * <p>⚠ The same-tier case is <b>refused rather than accepted-and-ignored</b>. A target that
     * accepts a transfer mode gets the drop cursor, so accepting a move that will not happen tells
     * the player it will. Dropping an item on the mount it is already in is a no-op that should look
     * like one.
     */
    private static void itemDropTarget(
            Region node, StorageTier tier, GameSession session, Label result, Runnable refresh) {
        node.setPickOnBounds(true);
        node.setOnDragOver(e -> {
            if (acceptsDrop(e, tier, session)) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            e.consume();
        });
        node.setOnDragEntered(e -> {
            if (acceptsDrop(e, tier, session) && !node.getStyleClass().contains("es-slot-drop")) {
                node.getStyleClass().add("es-slot-drop");
            }
            e.consume();
        });
        node.setOnDragExited(e -> {
            node.getStyleClass().remove("es-slot-drop");
            e.consume();
        });
        node.setOnDragDropped(e -> {
            node.getStyleClass().remove("es-slot-drop");
            String itemId = (String) e.getDragboard().getContent(STORAGE_ITEM);
            if (itemId == null) {
                e.setDropCompleted(false);
                e.consume();
                return;
            }
            GameSession.Outcome outcome = session.moveItem(itemId, tier);
            result.setText(outcome.message());
            styleByOutcome(result, outcome);
            // ⚠ Reports the ENGINE's answer, not "a drop happened". A refused move — a full mount,
            // once capacity is enforced — must end the gesture as a failure, or the drag animates
            // home while the message says it did not move and the two surfaces disagree.
            e.setDropCompleted(outcome.succeeded());
            e.consume();
            javafx.application.Platform.runLater(refresh);
        });
    }

    /** Whether this drag is an item that is not already in {@code tier}. */
    private static boolean acceptsDrop(javafx.scene.input.DragEvent e, StorageTier tier, GameSession session) {
        if (!e.getDragboard().hasContent(STORAGE_ITEM)) {
            return false;
        }
        GameSession.InventoryItem dragged =
                findItem(session, (String) e.getDragboard().getContent(STORAGE_ITEM));
        return dragged != null && dragged.tier() != tier;
    }

    /** One slot. Filled slots carry a name and are selectable; empty ones are drawn and inert. */
    private static Region slot(GameSession.InventoryItem item, String[] selected, Runnable refresh) {
        VBox cell = new VBox(1);
        cell.getStyleClass().add("es-slot");
        cell.setMinSize(104, 46);
        cell.setPrefSize(104, 46);
        cell.setMaxSize(104, 46);

        if (item == null) {
            cell.getStyleClass().add("es-slot-empty");
            cell.setAccessibleText("Empty slot.");
            return cell;
        }

        Label name = new Label(item.displayName());
        name.getStyleClass().add("es-slot-name");
        name.setWrapText(true);
        // Clipped rather than ellipsised: a cell is 104px of mono and an ellipsis costs a character
        // of a name that is already short. Two lines is what fits.
        name.setMaxHeight(30);
        cell.getChildren().add(name);
        if (item.equipped()) {
            // A marker, not a colour — §4.4 wants state to survive greyscale and a screen reader.
            cell.getChildren().add(Ui.micro("[eq]"));
        }
        // ⚠ THE ID IS ON THE TILE, because items stopped being one-per-type (2026-08-04). Two
        // Tarpits are two things — different builds, different tiers, different histories — and a
        // grid of identical names with no way to tell which is which makes every decision about
        // them a guess. Six characters is what fits and what a player types; the full id is on the
        // tooltip and in `verify`.
        Label id = Ui.micro(shortId(item.itemId()));
        id.getStyleClass().add("es-slot-id");
        cell.getChildren().add(id);
        // ⚠ Tooltip.install, not setTooltip — a slot is a VBox and only a Control carries a tooltip
        // property. The static form is the one that works on any node.
        javafx.scene.control.Tooltip.install(
                cell, new javafx.scene.control.Tooltip(item.displayName() + "\n" + item.itemId()));
        cell.getStyleClass().add("es-slot-filled");
        if (item.itemId().equals(selected[0])) {
            cell.getStyleClass().add("es-slot-selected");
        }
        cell.setPickOnBounds(true);
        cell.setOnMouseClicked(e -> {
            // ⚠ isStillSincePress, or a drag ALSO toggles the selection. A press-move-release that
            // started a drag still delivers MOUSE_CLICKED to the source on release, so without this
            // every successful drag left the panel with a stale selection pointing at an item that
            // had just moved somewhere else.
            if (!e.isStillSincePress()) {
                return;
            }
            selected[0] = item.itemId().equals(selected[0]) ? null : item.itemId();
            refresh.run();
        });
        draggableItem(cell, item);
        cell.setAccessibleText(item.displayName()
                + (item.equipped() ? ", equipped" : "")
                + ", id " + shortId(item.itemId())
                + ". Select to move it, or drag it to another mount.");
        return cell;
    }

    /**
     * The first six characters of an item id.
     *
     * <p>⚠ Six, matching {@code Repac.boughtPackageName}'s tag, so a package in Downloads and the
     * item it installs as read the same length — a player learning to tell two copies apart learns
     * one habit rather than two. Never used where the id is meant to be copied: a truncated
     * identifier that looks copyable and is not is worse than showing none.
     */
    static String shortId(String itemId) {
        return itemId == null || itemId.length() < 6 ? String.valueOf(itemId) : itemId.substring(0, 6);
    }

    private static GameSession.InventoryItem findItem(GameSession session, String itemId) {
        if (itemId == null) {
            return null;
        }
        for (StorageTier tier : StorageTier.values()) {
            for (GameSession.InventoryItem item : session.items(tier)) {
                if (item.itemId().equals(itemId)) {
                    return item;
                }
            }
        }
        return null;
    }

    private static String mountOf(StorageTier tier) {
        return switch (tier) {
            case VAULT -> "/rig/storage/vault";
            case STANDARD_STORAGE -> "/rig/storage/standard";
            case HIGH_HACKABLE_ZONE -> "/rig/storage/high";
        };
    }

    /** {@code docs/client/06} §5.3 — the consequence the player cannot be looking at. */
    private static String offlineFateOf(StorageTier tier) {
        return switch (tier) {
            case VAULT -> "safe";
            case STANDARD_STORAGE -> "safe";
            case HIGH_HACKABLE_ZONE -> "raidable";
        };
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
    /**
     * LEDGER — the money tool: where it comes from and what happened to it.
     *
     * <h2>Why mining is in here</h2>
     *
     * They are two halves of one subject. Mining is how ethecoin arrives; the ledger is the record of
     * everything that then happened to it — and the CONTRIBUTOR tab was already the seam between
     * them, listing every block this rig put hashrate into. Two windows made a player open one to
     * find out why the other's number moved.
     */
    public static Region ledger(GameSession session) {
        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane();
        tabs.getStyleClass().add("es-market-tabs");
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
        // ⚠ LEDGER first (2026-08-04). It was MINING, on the reasoning that the cause should precede
        // the effect — but the window is named for the ledger, and a tool whose first tab is not the
        // thing on its title bar makes a player wonder whether they opened the right one. The record
        // is also what a player comes here to read; changing the allocation is the rarer act.
        tabs.getTabs()
                .addAll(
                        new javafx.scene.control.Tab("LEDGER", ledgerPage(session)),
                        new javafx.scene.control.Tab("MINING", mining(session)));
        return tabs;
    }

    private static Region ledgerPage(GameSession session) {
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

        Label queueHeading = new Label(t("ui.views.your-pending", "YOUR PENDING"));
        VBox queue = new VBox(UiTokens.SPACE_1);

        HBox blocks = new HBox(UiTokens.SPACE_3);
        blocks.setAlignment(Pos.CENTER_LEFT);
        ScrollPane blockStrip = new ScrollPane(blocks);
        blockStrip.setFitToHeight(true);
        blockStrip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        blockStrip.setMinHeight(150);
        blockStrip.setPrefHeight(150);
        blockStrip.getStyleClass().add("es-block-strip");

        // ⚠ A COLUMN OF LABELS, not one Label of text — because the player's own rows have to be
        // findable in it. A block carries up to 200 transactions and theirs might be row 137; a
        // two-character ">" gutter in a wall of monospace is not a marker, it is a needle. Per-row
        // styling needs per-row nodes.
        VBox detail = new VBox();
        detail.getStyleClass().add("es-block-detail");
        detail.setVisible(false);
        detail.setManaged(false);

        TableView<ChainTransaction> table = new TableView<>();
        table.setPlaceholder(new Label(t("ui.views.no-transactions-yet-mine", "No transactions yet. Mine something.")));

        TableColumn<ChainTransaction, String> hash = new TableColumn<>("Tx hash");
        hash.setCellValueFactory(c -> text(c.getValue().shortHash()));
        hash.setPrefWidth(140);

        TableColumn<ChainTransaction, String> block = new TableColumn<>("Block");
        // ⚠ A dash, not a zero. A pool payout never touched the chain — the pool paid it out of its
        // own balance — and printing a block number would claim a miner mined it.
        block.setCellValueFactory(c -> text(
                c.getValue().blockNumber() < 0
                        ? "—"
                        : String.valueOf(c.getValue().blockNumber())));
        block.setPrefWidth(80);

        TableColumn<ChainTransaction, String> when = new TableColumn<>("Age");
        when.setCellValueFactory(c -> text(age(c.getValue().at())));
        when.setPrefWidth(80);

        // ⚠ The pool's NAME where there is one. A pooled payout is the row a player most needs to
        // recognise, and rendering its sender as 0x8f3c…a219 made it the least recognisable thing in
        // the table. The address is still what is on the chain and the explorer still shows it — the
        // name is carried beside it, never instead of it, which is what keeps §3.1's audit possible.
        TableColumn<ChainTransaction, String> from = new TableColumn<>("From");
        from.setCellValueFactory(c -> text(
                c.getValue().coinbase()
                        ? "coinbase"
                        : party(
                                c.getValue().from(),
                                c.getValue().counterpartyLabel(),
                                c.getValue().incoming())));
        from.setPrefWidth(150);

        TableColumn<ChainTransaction, String> to = new TableColumn<>("To");
        to.setCellValueFactory(c -> text(party(
                c.getValue().to(),
                c.getValue().counterpartyLabel(),
                !c.getValue().incoming())));
        to.setPrefWidth(150);

        TableColumn<ChainTransaction, String> value = new TableColumn<>("Value");
        value.setCellValueFactory(c -> text((c.getValue().incoming() ? "+" : "−")
                + Ethecoin.format(c.getValue().valueWei())));
        value.setPrefWidth(110);

        TableColumn<ChainTransaction, String> after = new TableColumn<>("Balance after");
        after.setCellValueFactory(c -> text(Ethecoin.format(c.getValue().balanceAfterWei())));
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

        // Which block the detail panel is showing, as a HEIGHT. See blockCard: the strip is rebuilt
        // on every chain advance, so a selection held on a node would not survive one. -1 is "none",
        // which is the state before the player has clicked anything.
        long[] selectedBlock = {-1L};
        Runnable[] markSelected = {() -> markSelectedBlock(blocks, selectedBlock[0])};

        // Two panes, one strip. Same bracket-selected chips the rig monitor draws (§4.4) — two tab
        // strips in one deck indicating selection differently would be two conventions to learn.
        TableView<BlockContribution> contributions = contributionTable();

        LedgerTab[] tab = {LedgerTab.CHAIN};
        VBox chainPane = new VBox(UiTokens.SPACE_3);
        VBox ledgerPane = new VBox(UiTokens.SPACE_3);
        VBox contributorPane = new VBox(UiTokens.SPACE_3);
        VBox.setVgrow(ledgerPane, Priority.ALWAYS);
        VBox.setVgrow(contributorPane, Priority.ALWAYS);

        HBox tabs = Ui.row(UiTokens.SPACE_3);
        tabs.getStyleClass().add("es-breach-picker");
        List<BreachView.Chip> tabChips = new ArrayList<>();
        Runnable[] applyTab = new Runnable[1];
        for (LedgerTab which : LedgerTab.values()) {
            BreachView.Chip chip = new BreachView.Chip(which.control(LedgerTab.CHAIN), "es-breach-chip-quiet");
            // ⚠ The enum's own answer, not a ternary here. A two-branch conditional silently gave a
            // third tab the second one's description, and nothing would have reported it — the chip
            // renders, reads out wrong, and only a screen-reader user ever finds out.
            chip.setAccessibleText(which.description());
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
            tabVisible(contributorPane, tab[0] == LedgerTab.CONTRIBUTOR);
        };

        Runnable refreshData = () -> {
            // ⚠ Cleared FIRST. The projection cards and the pending rows register countdowns into
            // this list as they are built, so a clear() further down — where it used to sit, when
            // only the block cards ticked — would silently unsubscribe everything built above it and
            // leave a strip of frozen ETAs beside correctly-ticking block ages.
            ticking.clear();

            MiningSnapshot m = session.miningChain();
            address.setText(session.chainAddress());
            balance.setText(session.balance().toString());
            chainLine.setText(String.format(
                    Locale.ROOT,
                    "height %d   difficulty %.2f   a block every ~%d min   retarget in %d",
                    m.height(),
                    m.difficulty(),
                    Math.round(Balance.CHAIN_TARGET_BLOCK_SECONDS / 60.0d),
                    m.blocksUntilRetarget()));

            ChainMempool pool = session.mempool();
            upcoming.getChildren().clear();
            for (ChainMempool.ProjectedBlock p : pool.projected()) {
                upcoming.getChildren().add(projectedCard(p, pool, ticking));
            }

            queue.getChildren().clear();
            for (ChainMempool.Queued q : pool.queued()) {
                queue.getChildren().add(queuedRow(session, q, pool, ticking));
            }
            // Hidden AND unmanaged when empty, or the heading and an empty box would claim a row's
            // worth of the strip's height on the overwhelming majority of frames, where a player has
            // nothing waiting at all.
            boolean anyQueued = !queue.getChildren().isEmpty();
            tabVisible(queueHeading, anyQueued);
            tabVisible(queue, anyQueued);

            blocks.getChildren().clear();
            for (ChainBlock b : session.chainBlocks()) {
                blocks.getChildren().add(blockCard(session, b, detail, ticking, selectedBlock, markSelected[0]));
            }
            if (blocks.getChildren().isEmpty()) {
                blocks.getChildren()
                        .add(secondary("No blocks yet — the chain mints one every "
                                + Math.round(Balance.CHAIN_TARGET_BLOCK_SECONDS / 60.0d) + " minutes."));
            }
            // ⚠ Re-marked after every rebuild. The row is torn down whenever the chain advances, so
            // a selection painted only on click vanishes on the next block while the detail panel
            // below goes on showing that block's header — a card and a readout disagreeing about
            // which block is being looked at.
            markSelected[0].run();
            table.getItems().setAll(session.chainTransactions(500));
            contributions.getItems().setAll(session.contributions(512));
        };
        refreshData.run();

        // ⚠ The SYNCHRONIZING panel is NOT here any more (2026-08-02). It drops out from under the
        // balance cell on load instead — DeckShell.showChainSync. The report is about a number that
        // is on screen at all times, and putting it on a tab of a window nobody had to open meant a
        // player only saw it by chance. ChainSyncPanel itself is unchanged and still builds it.
        chainPane
                .getChildren()
                .addAll(
                        new Label(t("ui.views.chain-2", "CHAIN")),
                        chainLine,
                        new Label(t("ui.views.mempool-next-blocks", "MEMPOOL — NEXT BLOCKS")),
                        mempoolLine,
                        upcoming,
                        queueHeading,
                        queue,
                        new Label(t("ui.views.recent-blocks", "RECENT BLOCKS")),
                        blockStrip,
                        detail);

        ledgerPane
                .getChildren()
                .addAll(
                        wrapped(t(
                                "ui.views.entries-are-added-and",
                                "Entries are added and never edited. Each row carries the balance after it, "
                                        + "so the log reconciles without replaying it. A dash in the block column "
                                        + "means the transaction never touched the chain.")),
                        table);

        contributorPane
                .getChildren()
                .addAll(
                        wrapped(t(
                                "ui.views.every-block-your-rig",
                                "Every block your rig put hashrate into — the ones you mined outright, and "
                                        + "the ones your pool found while you were contributing. SHARE is what "
                                        + "fraction of the whole chain you were at the time, which is exactly the "
                                        + "chance each of those blocks had of being yours. COINBASE is newly minted; "
                                        + "FEES were paid by the senders in the block. YOUR CUT is what reached you: "
                                        + "the whole reward when solo, a share of it under PPLNS, and nothing under "
                                        + "pay-per-share — which buys your shares instead and is not dividing the "
                                        + "block up at all.")),
                        contributions);

        Runnable refreshClock = () -> {
            ChainMempool pool = session.mempool();
            // The mean interval is still published as a mean — it is what the ETA beside it is
            // derived from, and dropping it would leave a countdown with nothing to be an estimate
            // *of*. What changed on 2026-07-27 is that the estimate is now stated as well as the
            // average, and states plainly when it has been overtaken rather than saying "overdue".
            String next = pool.projected().isEmpty()
                    ? "—"
                    : etaPhrase(pool.projected().getFirst(), pool.expectedNextBlockSeconds());
            mempoolLine.setText(String.format(
                    Locale.ROOT,
                    "%d waiting from you · next block %s · a block every ~%d min on average · "
                            + "last one %s ago · cheapest slot going for %s",
                    pool.yoursPending(),
                    next,
                    Math.round(pool.expectedNextBlockSeconds() / 60),
                    // ⚠ Not humanSeconds() bare. It answers "never" at or below zero, which is right
                    // for an expected *wait* of infinity and nonsense for an elapsed time — a block
                    // found this very second printed "last one never ago". Caught by rendering it.
                    pool.secondsSinceLastBlock() <= 0 ? "0s" : humanSeconds(pool.secondsSinceLastBlock()),
                    Ethecoin.format(pool.lowFeeWei())));
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

        root.getChildren()
                .addAll(
                        new Label(t("ui.views.your-address", "YOUR ADDRESS")),
                        address,
                        balance,
                        new Separator(),
                        tabs,
                        chainPane,
                        ledgerPane,
                        contributorPane);
        Region scrolled = scrollable(root);
        releaseOnDetach(root, onSession, clock);
        return scrolled;
    }

    /**
     * CONTRIBUTOR — every block this rig's hashrate went into.
     *
     * <h2>⚠ SHARE is the column the whole tab exists for</h2>
     *
     * It is the rig's fraction of the chain when the block was found, which is <b>exactly</b> the
     * probability {@code ChainRules.drawWinner} rolled against. Over enough solo rows the proportion
     * that came back marked YOUR RIG should converge on it — so the tab is not a trophy cabinet, it
     * is the one surface where a player can check the game's own claim about how mining works against
     * what the game actually did. {@code docs/education/07} teaches the arithmetic and had nowhere to
     * point.
     *
     * <h2>⚠ COINBASE and FEES are two columns and must not become one</h2>
     *
     * They are one credit in the ledger and two different things on the chain: the subsidy is
     * <b>minted</b> — those coins did not exist before this block — and the fees were <b>paid by the
     * senders</b> of the transactions in it. {@code proof-of-work(7)} teaches that split, and a single
     * "reward" total is precisely the readout that hides it.
     *
     * <h2>⚠ A zero in YOUR CUT is correct under pay-per-share, and is styled as information</h2>
     *
     * A share pool does not divide up the blocks it finds — it pays a fixed price per accepted share
     * out of its own balance, which is the entire product a PPS miner buys. So a PPS row carries a
     * real hashrate and no cut, rendered in micro grey rather than the alarm colour: it is the
     * expected reading, not a fault. It is also the only place in the client where the difference
     * between the two pool schemes is visible at all.
     */
    private static TableView<BlockContribution> contributionTable() {
        TableView<BlockContribution> table = new TableView<>();
        table.setPlaceholder(new Label(t(
                "ui.views.no-blocks-yet-commit",
                "No blocks yet. Commit cycles to mining and this fills as the chain finds them.")));

        TableColumn<BlockContribution, String> height = new TableColumn<>("Block");
        height.setCellValueFactory(
                c -> text(String.format(Locale.ROOT, "%,d", c.getValue().height())));
        height.setPrefWidth(90);

        TableColumn<BlockContribution, String> when = new TableColumn<>("Age");
        when.setCellValueFactory(c -> text(age(c.getValue().at())));
        when.setPrefWidth(80);

        TableColumn<BlockContribution, String> miner = new TableColumn<>("Mined by");
        miner.setCellValueFactory(c -> text(c.getValue().minerLabel()));
        miner.setPrefWidth(150);

        // The scheme, because it is what explains the cut column. SOLO / PPS / PPLNS are the names
        // the pool picker and the manual already use — a fourth vocabulary here would be a fourth
        // thing to learn for the same three facts.
        TableColumn<BlockContribution, String> scheme = new TableColumn<>("Paid as");
        scheme.setCellValueFactory(c -> text(c.getValue().scheme()));
        scheme.setPrefWidth(80);

        TableColumn<BlockContribution, String> rate = new TableColumn<>("Your hashrate");
        rate.setCellValueFactory(c -> text(hashrate(c.getValue().hashrate())));
        rate.setPrefWidth(120);

        TableColumn<BlockContribution, String> share = new TableColumn<>("Share");
        share.setCellValueFactory(
                c -> text(String.format(Locale.ROOT, "%.2f%%", c.getValue().networkShare() * 100)));
        share.setPrefWidth(80);

        TableColumn<BlockContribution, String> transactions = new TableColumn<>("TXNs");
        transactions.setCellValueFactory(c -> text(String.valueOf(c.getValue().transactions())));
        transactions.setPrefWidth(70);

        TableColumn<BlockContribution, String> coinbase = new TableColumn<>("Coinbase");
        coinbase.setCellValueFactory(c -> text(Ethecoin.format(c.getValue().subsidyWei())));
        coinbase.setPrefWidth(110);

        TableColumn<BlockContribution, String> fees = new TableColumn<>("Fees");
        fees.setCellValueFactory(c -> text(Ethecoin.format(c.getValue().feesWei())));
        fees.setPrefWidth(110);

        // ⚠ "per share", not "0.00 EC", under pay-per-share.
        //
        // A PPS pool does not divide up the blocks it finds — it buys accepted shares out of its own
        // balance — so nothing from this block reached the player and the honest figure is genuinely
        // zero. Rendered as a zero it read as a broken column: every row of a default character's
        // tab is PPS, so the first thing a new player would see here is ten zeroes in the one column
        // labelled with their own money. Naming the mechanism instead answers the question the zero
        // raises. ⚠ Keyed off the SCHEME and not off paid(): a PPLNS block whose cut rounded to zero
        // really did pay nothing out of a block that was being divided, and must still say 0.00.
        TableColumn<BlockContribution, String> cut = new TableColumn<>("Your cut");
        cut.setCellValueFactory(c -> text(
                "PPS".equals(c.getValue().scheme())
                        ? "per share"
                        : Ethecoin.format(c.getValue().creditedWei())));
        cut.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().removeAll("es-contrib-paid", "es-contrib-unpaid");
                BlockContribution row =
                        empty || getIndex() >= getTableView().getItems().size()
                                ? null
                                : getTableView().getItems().get(getIndex());
                if (row != null) {
                    getStyleClass().add(row.paid() ? "es-contrib-paid" : "es-contrib-unpaid");
                }
            }
        });
        cut.setPrefWidth(120);

        table.getColumns().addAll(List.of(height, when, miner, scheme, rate, share, transactions, coinbase, fees, cut));
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(320);
        return table;
    }

    /**
     * A hashrate in the units a miner reads.
     *
     * <p>⚠ SI prefixes on powers of ten, not of two. A hashrate is a <em>rate</em> — hashes per
     * second — and rates take SI prefixes; the binary ones belong to storage. Every real pool
     * dashboard quotes MH/s meaning 10⁶, so using 2²⁰ here would put this client 4.9% out of step
     * with the arithmetic {@code docs/education/07} checks against a live explorer.
     */
    private static String hashrate(long hashesPerSecond) {
        if (hashesPerSecond <= 0) {
            return "—";
        }
        String[] units = {"H/s", "kH/s", "MH/s", "GH/s", "TH/s", "PH/s"};
        double value = hashesPerSecond;
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return String.format(Locale.ROOT, value >= 100 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    /**
     * WINDOW — how big the deck is, how large it is drawn, and whether it takes the screen.
     *
     * <h2>Why this section exists</h2>
     *
     * The deck is one undecorated Stage (§0), so there is no OS chrome and therefore no OS resize
     * handle. Drawing our own window controls bought the look and quietly took away a thing every
     * other window on the machine can do; these three settings are the half of that trade that had
     * not been paid.
     *
     * <h2>⚠ Size and scale are not independent, and the panel has to say so</h2>
     *
     * The deck is laid out at {@code physical / scale}, so raising the scale shrinks the room. At
     * 200% a 1280×800 window gives the deck 640×400 — under the supported floor, with the rail and
     * half the strip clipped. Rather than let a player select that and discover it, the size list is
     * <b>rebuilt</b> whenever the scale changes and offers only what still holds. What was dropped
     * is named, because a list that silently got shorter reads as a bug.
     *
     * @param onWindowChanged null when there is no Stage (the snapshot harness), in which case the
     *     whole section is omitted rather than shown saving values nothing applies
     */
    private static VBox windowSection(ClientProfile profile, Runnable onWindowChanged, Runnable[] publishRebuild) {
        VBox section = new VBox(UiTokens.SPACE_3);
        if (onWindowChanged == null) {
            section.setVisible(false);
            section.setManaged(false);
            return section;
        }

        ChoiceBox<io.github.stoicswe.eyeandsickle.client.ui.WindowSize> size = new ChoiceBox<>();
        size.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(io.github.stoicswe.eyeandsickle.client.ui.WindowSize value) {
                return value == null ? "" : value.label();
            }

            @Override
            public io.github.stoicswe.eyeandsickle.client.ui.WindowSize fromString(String s) {
                return io.github.stoicswe.eyeandsickle.client.ui.WindowSize.HD_1280;
            }
        });

        javafx.geometry.Rectangle2D usable = javafx.stage.Screen.getPrimary().getVisualBounds();

        ChoiceBox<Integer> scale = new ChoiceBox<>();
        // ⚠ Filtered by the DISPLAY, not by the preset list. The Stage minimum is
        // `floor × factor`, so on a 1080p panel 200% needs a 1720 × 1120 window that the screen
        // cannot give — and every preset then fails too, which is how the size list ended up
        // offering 1280 × 800 at a scale where 1280 × 800 is unusable. Removing the scale removes
        // the whole degenerate branch instead of papering over it downstream.
        int casingMargin = io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.byId(profile.appearance().bezel)
                .orElse(io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.OFF)
                .margin();
        for (int percent : io.github.stoicswe.eyeandsickle.client.ui.UiScale.PERCENTAGES) {
            double factor = percent / 100.0d;
            // Offerable if the SMALLEST viewport, plus its casing, still fits the display at this
            // scale. Anything stricter would hide a scale that some preset could have used.
            boolean roomOnScreen = io.github.stoicswe.eyeandsickle.client.ui.WindowSize.HD_1280.fitsOnScreen(
                    usable.getWidth(), usable.getHeight(), factor, casingMargin);
            if (roomOnScreen || percent == io.github.stoicswe.eyeandsickle.client.ui.UiScale.DEFAULT_PERCENT) {
                // 100% is always offered. A display too small for even that is below the supported
                // floor outright, and an empty control would leave the player nothing to change.
                scale.getItems().add(percent);
            }
        }
        scale.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer percent) {
                if (percent == null) {
                    return "";
                }
                return percent + "%"
                        + (percent == io.github.stoicswe.eyeandsickle.client.ui.UiScale.DEFAULT_PERCENT
                                ? "  (default)"
                                : "");
            }

            @Override
            public Integer fromString(String s) {
                return io.github.stoicswe.eyeandsickle.client.ui.UiScale.DEFAULT_PERCENT;
            }
        });
        int wantedScale = io.github.stoicswe.eyeandsickle.client.ui.UiScale.sanitise(profile.settings().uiScalePercent);
        // A profile carried over from a larger display can name a scale this screen cannot hold.
        scale.setValue(
                scale.getItems().contains(wantedScale)
                        ? wantedScale
                        : io.github.stoicswe.eyeandsickle.client.ui.UiScale.DEFAULT_PERCENT);

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch fullScreen =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(t("ui.views.full-screen", "Full screen"));
        fullScreen.setSelected(profile.settings().fullScreen);

        Label note = new Label();
        note.setWrapText(true);
        note.getStyleClass().add("es-text-secondary");

        // ⚠ A latch, not a flag on the listener. Rebuilding the item list fires valueProperty twice
        // — once to null on clear, once on re-selection — and without this the second fire persists
        // the value the rebuild just chose as though the player had picked it, and calls back into
        // the Stage mid-layout.
        boolean[] rebuilding = {false};

        Runnable refill = () -> {
            rebuilding[0] = true;
            try {
                double factor = scale.getValue() / 100.0d;

                var offered = new ArrayList<io.github.stoicswe.eyeandsickle.client.ui.WindowSize>();
                var fitsScreen = new ArrayList<io.github.stoicswe.eyeandsickle.client.ui.WindowSize>();
                var tooBig = new ArrayList<String>();
                var tooScaled = new ArrayList<String>();
                int margin = io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.byId(profile.appearance().bezel)
                        .orElse(io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.OFF)
                        .margin();
                for (var candidate : io.github.stoicswe.eyeandsickle.client.ui.WindowSize.selectable()) {
                    // ⚠ One rule now, where there were two. The viewport always gets the full
                    // resolution in layout units, so nothing is "too small once divided" any more —
                    // the only question is whether viewport + casing, scaled, fits the display.
                    if (!candidate.fitsOnScreen(usable.getWidth(), usable.getHeight(), factor, margin)) {
                        if (candidate.fitsOnScreen(usable.getWidth(), usable.getHeight(), 1.0d, 0)) {
                            tooScaled.add(candidate.label());
                        } else {
                            tooBig.add(candidate.label());
                        }
                        continue;
                    }
                    fitsScreen.add(candidate);
                    offered.add(candidate);
                }
                // Never an empty list — the player is looking at this control and has to be able to
                // move it. Falling back to what FITS rather than to a fixed preset, because the
                // Stage clamps a too-small window up to `floor × factor` anyway; offering the
                // largest thing the screen holds is the closest the machine can actually get.
                if (offered.isEmpty()) {
                    offered.addAll(
                            fitsScreen.isEmpty()
                                    ? java.util.List.of(io.github.stoicswe.eyeandsickle.client.ui.WindowSize.HD_1280)
                                    : java.util.List.of(fitsScreen.getLast()));
                }

                var wanted = io.github.stoicswe.eyeandsickle.client.ui.WindowSize.byId(profile.settings().windowSize)
                        .filter(offered::contains)
                        .orElse(offered.getLast());
                size.getItems().setAll(offered);
                size.setValue(wanted);
                profile.settings().windowSize = wanted.id();

                StringBuilder text = new StringBuilder();
                text.append("The viewport is ")
                        .append(wanted.label())
                        .append("; the window it needs is ")
                        .append(Math.round((wanted.width() + 2 * margin) * factor))
                        .append(" × ")
                        .append(Math.round((wanted.height() + 2 * margin) * factor))
                        .append(margin > 0 ? " with the casing." : ".");
                if (!tooScaled.isEmpty()) {
                    text.append("  Hidden at this scale and casing, the window would not fit: ")
                            .append(String.join(", ", tooScaled))
                            .append('.');
                }
                if (!tooBig.isEmpty()) {
                    text.append("  Larger than your screen: ")
                            .append(String.join(", ", tooBig))
                            .append('.');
                }
                note.setText(text.toString());
            } finally {
                rebuilding[0] = false;
            }
        };

        size.valueProperty().addListener((o, was, now) -> {
            if (rebuilding[0] || now == null) {
                return;
            }
            profile.settings().windowSize = now.id();
            profile.save();
            onWindowChanged.run();
            refill.run();
        });

        scale.valueProperty().addListener((o, was, now) -> {
            if (now == null) {
                return;
            }
            profile.settings().uiScalePercent = now;
            profile.save();
            // Refill BEFORE applying: raising the scale can invalidate the selected size, and
            // applying first would put the Stage at a size the list is about to stop offering.
            refill.run();
            onWindowChanged.run();
        });

        fullScreen.selectedProperty().addListener((o, was, now) -> {
            profile.settings().fullScreen = now;
            profile.save();
            onWindowChanged.run();
            // The size control does nothing while the screen is full, so it says so by being
            // disabled rather than by accepting a change that has no visible effect.
            size.setDisable(now);
        });
        size.setDisable(fullScreen.isSelected());

        refill.run();
        // ⚠ Refill AND re-apply. A casing change resizes the window, so the Stage has to be told;
        // refilling alone would leave the list correct and the window the wrong size.
        publishRebuild[0] = () -> {
            refill.run();
            onWindowChanged.run();
        };

        section.getChildren()
                .addAll(
                        new Label(t("ui.views.window", "WINDOW")),
                        size,
                        wrapped(t(
                                "ui.views.the-deck-draws-its",
                                "The deck draws its own window chrome, so your desktop gives it no resize "
                                        + "handle — this is where the size lives. Dragging the top strip still moves "
                                        + "it, double-clicking the strip still maximises it, and neither is affected "
                                        + "by what is set here.")),
                        new Label(t("ui.views.ui-scale", "UI SCALE")),
                        scale,
                        note,
                        wrapped(t(
                                "ui.views.scales-the-whole-interface",
                                "Scales the whole interface, not the font: every hairline, cell meter and "
                                        + "character grid keeps its exact proportions, because the deck is drawn "
                                        + "through one transform rather than restyled. Larger scale means less "
                                        + "room, so sizes that would take the layout under its supported minimum "
                                        + "stop being offered.")),
                        fullScreen,
                        wrapped(t(
                                "ui.views.off-by-default-escape",
                                "Off by default. Escape still opens the pause menu in full screen — the "
                                        + "usual \"press Escape to leave full screen\" behaviour is turned off here, "
                                        + "because it would swallow the key the game already uses. Turn this off "
                                        + "again to get the window back.")),
                        new Separator());
        return section;
    }

    /**
     * Stops a panel's clock and subscriptions when it leaves the scene.
     *
     * <p>⚠ Load-bearing rather than tidy. A one-second timer left running against a closed window
     * repaints a detached scene graph forever, and every re-open starts another — so the tenth time a
     * player opens the ledger the machine is doing ten times the work for one visible panel.
     */
    static void releaseOnDetach(Region root, AutoCloseable... handles) {
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
     *
     * <p>The countdown is registered into {@code ticking} and redrawn on the panel's one-second
     * clock. It is an <em>estimate</em>, not a deadline: past its instant the card says how far into
     * the distribution the wait has got instead of claiming the block is overdue. See
     * {@link #etaPhrase} and {@code ChainMempool}'s type comment.
     */
    private static Region projectedCard(ChainMempool.ProjectedBlock p, ChainMempool pool, List<Runnable> ticking) {
        Label head = Ui.value(p.index() == 0 ? "next" : "+" + (p.index() + 1));
        Label fill = new Label(cells(p.fullness(), 10));
        fill.getStyleClass().add("es-block-fill");

        Label eta = Ui.micro("");
        double mean = pool.expectedNextBlockSeconds();

        VBox card = new VBox(
                UiTokens.SPACE_1,
                head,
                Ui.small(p.transactions() + " txs"),
                fill,
                Ui.micro(p.yours() == 0 ? "none yours" : p.yours() + " yours"),
                eta,
                Ui.micro("fees " + Ethecoin.format(p.feesWei())));
        card.getStyleClass().addAll("es-block", "es-block-projected");
        if (p.yours() > 0) {
            card.getStyleClass().add("es-block-yours");
        }
        card.setMinWidth(112);
        card.setPickOnBounds(true);

        Runnable retime = () -> {
            long left = remaining(p.etaAt());
            eta.setText(left > 0 ? "~" + Ui.clock(left) : "running long");
            // ⚠ Two classes, not one. theme.css has a late `.label { -fx-text-fill: -es-text; }` that
            // a single-class selector cannot beat at equal specificity — the trap CLAUDE.md records,
            // where every property except the text fill applies and the miss is invisible.
            eta.getStyleClass().remove("es-block-late");
            if (left <= 0) {
                eta.getStyleClass().add("es-block-late");
            }
            card.setAccessibleText("Projected block " + (p.index() + 1) + ": " + p.transactions()
                    + " transactions, " + p.yours() + " of them yours, "
                    + etaPhrase(p, mean)
                    + ". An estimate, not a schedule — blocks arrive at random intervals.");
        };
        retime.run();
        ticking.add(retime);
        return card;
    }

    /**
     * One of the player's own transactions, waiting, with the block it currently projects into.
     *
     * <p>Its ETA is its projected block's, because that is what it is actually waiting for. A
     * transaction the queue pushes past the third projection says so rather than borrowing the last
     * card's instant — an under-priced transaction behind a deep backlog genuinely has no estimate
     * the three-block window can give, and inventing one is the failure mode {@code FeeTier}'s
     * "every tier gets in eventually" is most likely to be misread as.
     */
    private static Region queuedRow(
            GameSession session, ChainMempool.Queued q, ChainMempool pool, List<Runnable> ticking) {
        Label eta = Ui.small("");
        double mean = pool.expectedNextBlockSeconds();

        // ⚠ Named against the projection depth rather than a literal "+3". The strip projects 3–5
        // blocks now, so a hard-coded three said "past +3" while a fourth and fifth card sat
        // visibly beside it.
        Label where = Ui.micro(
                q.beyondProjection()
                        ? "past +" + Math.max(1, pool.projected().size())
                        : q.projectedIndex() == 0 ? "next block" : "block +" + (q.projectedIndex() + 1));

        HBox row = Ui.row(
                UiTokens.SPACE_3,
                Ui.small(q.tx().shortHash()),
                Ui.small(Ethecoin.format(q.tx().valueWei())),
                where,
                eta,
                Ui.micro("fee " + Ethecoin.format(q.tx().feeWei())),
                boostChip(session, q),
                Ui.micro(q.tx().description()));
        row.setAlignment(Pos.CENTER_LEFT);

        Runnable retime = () -> {
            if (q.beyondProjection()) {
                eta.setText("no estimate — outbid by the queue");
                row.setAccessibleText("Transaction " + q.tx().shortHash() + ", " + Ethecoin.format(q.tx().valueWei())
                        + ", waiting further out than the projections reach. "
                        + "It is being outbid; it will confirm, but not in the next three blocks.");
                return;
            }
            long left = remaining(q.etaAt());
            eta.setText(left > 0 ? "~" + Ui.clock(left) : "running long");
            eta.getStyleClass().remove("es-block-late");
            if (left <= 0) {
                eta.getStyleClass().add("es-block-late");
            }
            // Safe by construction: a projectedIndex of 0..n-1 is only ever assigned by walking the
            // projection list, and anything the queue could not place is -1 and returned above.
            ChainMempool.ProjectedBlock into = pool.projected().get(q.projectedIndex());
            row.setAccessibleText("Transaction " + q.tx().shortHash() + ", "
                    + Ethecoin.format(q.tx().valueWei()) + ", projected into "
                    + (q.projectedIndex() == 0 ? "the next block" : "block plus " + (q.projectedIndex() + 1))
                    + ", " + etaPhrase(into, mean) + ".");
        };
        retime.run();
        ticking.add(retime);
        row.getStyleClass().add("es-queued-row");
        return row;
    }

    /**
     * BOOST — raise a waiting transaction's fee, and jump the queue.
     *
     * <h2>Why this control exists on the row and not in a dialog</h2>
     *
     * The decision it supports is "this is going to take three more blocks and I do not want to
     * wait", and the evidence for that decision — the projected block, the countdown, the fee already
     * paid — is on this row. Moving the action away from the evidence would make the player carry the
     * comparison in their head to somewhere it could not be checked.
     *
     * <h2>⚠ It names the DIFFERENCE, not the new fee</h2>
     *
     * The original fee was debited when the transaction was broadcast, so a boost costs the gap. A
     * chip reading "0.30 EC" beside a row already showing "fee 0.08 EC" would read as the total and
     * would over-state the price by the amount already paid.
     *
     * <p>Absent entirely on a transaction already at the top tier — there is nothing above priority
     * to bid, and a disabled control that never becomes enabled is furniture.
     */
    private static javafx.scene.Node boostChip(GameSession session, ChainMempool.Queued q) {
        FeeTier next = null;
        java.math.BigInteger paying = q.tx().feeWei();
        for (FeeTier tier : FeeTier.values()) {
            java.math.BigInteger cost = Balance.feeFor(tier);
            if (cost.compareTo(paying) > 0 && (next == null || cost.compareTo(Balance.feeFor(next)) < 0)) {
                next = tier;
            }
        }
        if (next == null) {
            return Ui.micro("top of the queue");
        }
        FeeTier target = next;
        java.math.BigInteger difference = Balance.feeFor(target).subtract(paying);
        BreachView.Chip chip = new BreachView.Chip("boost +" + Ethecoin.format(difference), "es-breach-chip-quiet");
        chip.setAccessibleText("Raise this transaction's fee to " + target.label()
                + " for " + Ethecoin.format(difference) + " more. Miners sort by fee rate, so it moves up the "
                + "queue: " + target.promise() + ". This is replace-by-fee.");
        javafx.scene.control.Tooltip boost = new javafx.scene.control.Tooltip(
                "Replace-by-fee. A transaction waiting in the mempool is not committed to anything — "
                        + "offer more and miners, who sort by fee rate, prefer the better offer. You "
                        + "pay the difference, because the first fee is already spent.\n"
                        + target.label() + ": " + target.promise() + ".");
        boost.setWrapText(true);
        boost.setMaxWidth(360);
        javafx.scene.control.Tooltip.install(chip, boost);
        chip.onInvoke(() -> session.boostFee(q.tx().hash(), target));
        return chip;
    }

    /** Whole seconds from now until {@code at}; zero or negative once the estimate is overtaken. */
    private static long remaining(java.time.Instant at) {
        return at == null
                ? 0L
                : java.time.Duration.between(java.time.Instant.now(), at).toSeconds();
    }

    /**
     * A projection's ETA in words — the countdown while it holds, the distribution once it does not.
     *
     * <h2>⚠ "running long" is a fact about the wait, and "overdue" would be a lie about the chain</h2>
     *
     * A block's arrival is memoryless: a chain that has gone an hour without one is not owed one, and
     * an exponential wait exceeds its own mean about 37% of the time. So an estimate being overtaken
     * is the <em>ordinary</em> case rather than a fault, and what a player should read off it is
     * where in the distribution they have landed — which is what
     * {@link ChainMempool.ProjectedBlock#waitPercentile} answers exactly, being the Erlang CDF and
     * not an approximation.
     *
     * <p>⚠ Elapsed is reconstructed from the ETA rather than read from
     * {@code pool.secondsSinceLastBlock()}, deliberately. That field is stamped when the snapshot is
     * built and would not advance between rebuilds, so a card past its estimate would freeze its
     * percentile at whatever it was when the strip was last constructed — the same class of bug as
     * the frozen block ages, one field along. The ETA instant plus the wall clock gives it live.
     */
    private static String etaPhrase(ChainMempool.ProjectedBlock p, double meanBlockSeconds) {
        long left = remaining(p.etaAt());
        if (left > 0) {
            return "~" + Ui.clock(left) + " away";
        }
        double elapsed = p.expectedSeconds(meanBlockSeconds) - left;
        long percentile = Math.round(p.waitPercentile(elapsed, meanBlockSeconds) * 100);
        return "running long — longer than " + percentile + "% of waits";
    }

    /**
     * One block, as an explorer card.
     *
     * <p>The fill bar is gas used against the block's limit — cells rather than a smooth bar, because
     * {@code docs/design/ui-design-language.md} §4 forbids a continuous one for the same reason the
     * cycle grid is countable: a smooth bar implies a precision the model does not have.
     */
    /**
     * One block in the RECENT BLOCKS strip.
     *
     * <h2>⚠ Selection lives OUTSIDE the card, keyed by height</h2>
     *
     * The strip is torn down and rebuilt whenever the chain advances ({@code refreshData} clears the
     * row and re-adds every card), so selection held on a card would be destroyed roughly every
     * fourteen minutes — and, before this, was not held anywhere at all: clicking rewrote the detail
     * text and marked nothing, so the panel showed a header with no indication of which of
     * twenty-four cards it belonged to. Reported as the selection not moving, which is what having no
     * selection indicator looks like from outside.
     *
     * <p>So the selected <em>height</em> is the state and {@code markSelected} repaints the row from
     * it. A height survives a rebuild; a node does not.
     *
     * @param selected one-element holder for the selected height, shared with the panel
     * @param markSelected repaints the whole row's selected state — called on click and after every
     *     rebuild, so a selection outlives the block that pushed the strip along
     */
    private static Region blockCard(
            GameSession session,
            ChainBlock b,
            VBox detail,
            List<Runnable> ticking,
            long[] selected,
            Runnable markSelected) {
        Label height = Ui.value("#" + b.number());

        // ⚠ A pill, and §9's radius ban was amended for it (2026-07-29) — see `.es-pill` in
        // theme.css. A miner is the one field on this card that is a NAME rather than a number, and
        // a bare grey line of type read as another measurement in a stack of four. The rig's own
        // pill takes the accent, which is the same claim `.es-block-yours` already makes about the
        // card's border: amber is income, and a block you mined is income.
        Label who = Ui.micro(b.minerLabel());
        who.getStyleClass().addAll("es-pill", "es-miner-pill");
        if (b.yours()) {
            who.getStyleClass().add("es-miner-pill-yours");
        }

        Label fill = new Label(cells(b.fullness(), 10));
        fill.getStyleClass().add("es-block-fill");

        // Registered on the panel's one-second clock. A block's age is the only thing on this card
        // that changes without the chain changing, and it is the first thing a player checks.
        Label when = Ui.micro(age(b.timestamp()) + " ago");
        ticking.add(() -> when.setText(age(b.timestamp()) + " ago"));

        VBox card = new VBox(
                UiTokens.SPACE_1,
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
        // What markSelected matches on. The height, not the node — the row is rebuilt every time the
        // chain advances and a node identity does not survive that.
        card.setUserData(b.number());
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
            paintBlockDetail(detail, full == null ? b : full);
            selected[0] = b.number();
            markSelected.run();
        });
        return card;
    }

    /**
     * Paints the strip's selected card, from the selected height.
     *
     * <p>⚠ Walks <b>every</b> card and clears first. Toggling only the two cards involved works right
     * up until the row is rebuilt underneath — the old node is gone and the new one for the same
     * height was never told, so the mark silently disappears on the next block. Clearing the row and
     * re-marking from the one piece of state is the version that cannot drift.
     *
     * <p>A height that has scrolled out of the strip's window simply marks nothing, which is correct:
     * the detail below still shows that block, and the card for it is no longer on screen to mark.
     */
    private static void markSelectedBlock(HBox strip, long selected) {
        for (javafx.scene.Node node : strip.getChildren()) {
            node.getStyleClass().remove("es-block-selected");
            if (node.getUserData() instanceof Long height && height == selected) {
                node.getStyleClass().add("es-block-selected");
            }
        }
    }

    /**
     * A block's full header and every transaction in it.
     *
     * <p>⚠ The player's own rows are marked. In a body of up to two hundred transactions the one that
     * belongs to the reader is the only one they are looking for, and an explorer that made them
     * match hex strings by eye would be technically complete and practically useless.
     */
    /**
     * A block's header and every transaction in it, with the player's own rows made findable.
     *
     * <h2>⚠ The player's rows are the point of opening a block, and they used to be a "&gt;"</h2>
     *
     * A block carries up to 200 transactions and the two the player cares about might be rows 137
     * and 138. The marker was a two-character gutter in a wall of identical monospace, which is not a
     * marker — it is a needle. Their rows now take the income accent, carry {@code YOU} in a column
     * of their own, and print the description the ledger has for them, which the derived network
     * traffic does not have and cannot have.
     *
     * <p>⚠ The accent is earned rather than borrowed: §2.1 reserves amber for the player's own money,
     * and these rows are literally that. Nothing else in the list is coloured, so the budget is spent
     * once.
     */
    private static void paintBlockDetail(VBox into, ChainBlock b) {
        into.getChildren().clear();
        for (String line : List.of(
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
                "reward        " + Ethecoin.format(b.rewardWei()) + " subsidy + " + Ethecoin.format(b.feesWei())
                        + " fees = " + Ethecoin.format(b.minerTakeWei()))) {
            into.getChildren().add(detailLine(line, false));
        }

        int mine = 0;
        for (ChainTransaction tx : b.body()) {
            if (tx.yours()) {
                mine++;
            }
        }
        into.getChildren().add(detailLine("", false));
        into.getChildren()
                .add(detailLine(
                        mine == 0
                                ? "  nothing of yours is in this block"
                                : "  " + mine + (mine == 1 ? " row here is yours" : " rows here are yours")
                                        + " — marked YOU below",
                        mine > 0));
        into.getChildren()
                .add(detailLine(
                        "  " + pad("#", 4) + pad("who", 5) + pad("hash", 16) + pad("from", 16) + pad("to", 16)
                                + pad("value", 13) + pad("fee", 9) + "gas price",
                        false));

        int index = 0;
        for (ChainTransaction tx : b.body()) {
            String line = (tx.yours() ? "> " : "  ")
                    + pad(String.valueOf(index++), 4)
                    // ⚠ Its own COLUMN, not a prefix. A leading marker shifts everything after it
                    // and breaks the character-cell alignment the whole table is read down.
                    + pad(tx.yours() ? "YOU" : "", 5)
                    + pad(ChainBlock.shorten(tx.hash()), 16)
                    + pad(tx.coinbase() ? "coinbase" : ChainBlock.shorten(tx.from()), 16)
                    + pad(ChainBlock.shorten(tx.to()), 16)
                    + pad(Ethecoin.format(tx.valueWei()), 13)
                    + pad(tx.coinbase() ? "—" : Ethecoin.format(tx.feeWei()), 9)
                    // ⚠ The gas price column is gone. It was fee-per-million-gas, which at wei
                    // scale prints eighteen digits in a character-cell table — and the fee beside it
                    // already answers the question the column was for, in the unit the player pays.
                    + "";
            // The description is the one field the derived network traffic does not have — it is
            // read off the ledger row — so it doubles as proof that this row is really the player's.
            if (tx.yours() && !tx.description().isBlank()) {
                line = line + "   " + tx.description();
            }
            into.getChildren().add(detailLine(line, tx.yours()));
        }
        if (b.body().isEmpty()) {
            into.getChildren().add(detailLine("  (select a block to load its transactions)", false));
        }
    }

    /** One line of block detail. Two classes on the accented ones — the late `.label` fill rule. */
    private static Label detailLine(String text, boolean yours) {
        Label label = new Label(text);
        label.getStyleClass().add("es-mono");
        if (yours) {
            label.getStyleClass().add("es-block-detail-yours");
        }
        return label;
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

    /**
     * One end of a transfer: the counterparty's name if it has a verified one, else its address.
     *
     * <p>⚠ The label belongs to the <b>counterparty</b>, so it applies to whichever end that is —
     * the sender of an incoming transfer and the recipient of an outgoing one. Applying it to both
     * columns would put the pool's name on the player's own address on every payout row.
     */
    private static String party(String address, String label, boolean isCounterparty) {
        if (isCounterparty && label != null && !label.isBlank()) {
            return label;
        }
        return ChainBlock.shorten(address);
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
        Label note = wrapped(t(
                "ui.views.every-armed-defence-holds",
                "Every armed defence holds compute for as long as it stays armed. A fully paranoid "
                        + "loadout costs more than a starting rig has — that is the decision, not a "
                        + "shortfall. Defending your own rig never generates heat."));

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

        Label legalNote = wrapped(t(
                "ui.views.note-on-the-auto",
                "Note on the Auto-Counter Daemon: in this fiction it fires back. In the real world "
                        + "that is a crime in most jurisdictions, and being attacked first does not "
                        + "change that. See hack-back(7)."));
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
            body.getChildren()
                    .addAll(
                            field("handle", session.handle()),
                            field("mode", session.mode().label()),
                            field("heat", String.valueOf(session.personalHeat())),
                            field("balance", session.balance().toString()));
            Label explanation = wrapped(session.mode().explanation());
            body.getChildren().add(explanation);
            if (session.mode() == io.github.stoicswe.eyeandsickle.client.session.SessionMode.SOLO) {
                Label solo = wrapped(t(
                        "ui.views.this-character-is-local",
                        "This character is local to this machine. It has no DID and no cryptographic "
                                + "identity, and it cannot be carried into a federated server — going "
                                + "online means creating a character there. That boundary is what keeps "
                                + "a file you can edit from ever becoming someone else's problem."));
                solo.getStyleClass().add("es-text-secondary");
                body.getChildren().add(solo);
            }
        };
        refresh.run();
        session.onChange(s -> refresh.run());

        root.getChildren().add(body);
        return scrollable(root);
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
        return settings(profile, themes, onDeskSettingsChanged, null, null);
    }

    public static Region settings(
            ClientProfile profile,
            ThemeManager themes,
            Runnable onDeskSettingsChanged,
            java.util.function.Consumer<String> onRename) {
        return settings(profile, themes, onDeskSettingsChanged, onRename, null);
    }

    /**
     * @param onRename applies a new operator name, or null when there is no live character —
     *     the menu opens this panel before a session exists, and a rename control that silently
     *     did nothing would be worse than one that says what it will affect
     * @param onWindowChanged re-applies window size, UI scale and full screen to the Stage, or null
     *     where there is no Stage to apply them to (the snapshot harness). Null hides those
     *     controls rather than showing three that save a value nothing reads.
     */
    public static Region settings(
            ClientProfile profile,
            ThemeManager themes,
            Runnable onDeskSettingsChanged,
            java.util.function.Consumer<String> onRename,
            Runnable onWindowChanged) {
        return settings(profile, themes, onDeskSettingsChanged, onRename, onWindowChanged, null);
    }

    /**
     * The whole panel, with a live session where there is one.
     *
     * @param session the running character, or {@code null} from the main menu. The picture and the
     *     About page both need it; everything else is a client preference and does not
     */
    public static Region settings(
            ClientProfile profile,
            ThemeManager themes,
            Runnable onDeskSettingsChanged,
            java.util.function.Consumer<String> onRename,
            Runnable onWindowChanged,
            GameSession session) {
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

        // ---- the rig's own name
        //
        // A CLIENT setting, not game state: nothing in the rules reads it, no gate depends on it,
        // and no ledger entry records it. It sits beside the handle because the two are the two
        // halves of the same string — the prompt reads `handle@hostname.local:~$` — and separating
        // them into different sections would make the pairing something a player has to discover.
        TextField hostname = new TextField(
                io.github.stoicswe.eyeandsickle.client.profile.Hostname.sanitise(profile.settings().rigHostname));
        hostname.setPromptText(io.github.stoicswe.eyeandsickle.client.profile.Hostname.DEFAULT);
        Label hostnameResult = new Label();
        hostnameResult.setWrapText(true);
        Button applyHostname = new Button("Set hostname");
        Runnable setHostname = () -> {
            String wanted = hostname.getText() == null ? "" : hostname.getText().trim();
            String problem = io.github.stoicswe.eyeandsickle.client.profile.Hostname.problem(wanted);
            if (problem != null) {
                hostnameResult.setText(problem);
                styleByOutcome(hostnameResult, GameSession.Outcome.refused(problem));
                return;
            }
            String normalised = io.github.stoicswe.eyeandsickle.client.profile.Hostname.sanitise(wanted);
            profile.settings().rigHostname = normalised;
            profile.save();
            // Written back into the field, so a player who typed `RIG.local` sees what was actually
            // stored rather than being left to assume their capitals survived.
            hostname.setText(normalised);
            hostnameResult.setText("The prompt now reads "
                    + io.github.stoicswe.eyeandsickle.client.profile.Hostname.prompt(
                            profile.settings().soloHandle, normalised));
            styleByOutcome(hostnameResult, GameSession.Outcome.ok());
            if (onDeskSettingsChanged != null) {
                onDeskSettingsChanged.run();
            }
        };
        applyHostname.setOnAction(e -> setHostname.run());
        hostname.setOnAction(e -> setHostname.run());

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
        // ⚠ Declared before the rounded switch exists because the picker is built first and the
        // switch has to follow it — a liquid palette rounds windows on its own (ThemeId.roundsCorners),
        // so changing the theme with this page open leaves that switch describing the wrong deck.
        // The same holder pattern the focus-ring swatches below already use.
        Runnable[] syncRoundedSwitch = new Runnable[1];
        theme.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                themes.select(now);
                profile.save();
                if (syncRoundedSwitch[0] != null) {
                    syncRoundedSwitch[0].run();
                }
            }
        });

        // ── Language ──────────────────────────────────────────────────────────────────────────
        // ⚠ The list reads "English · Deutsch · 日本語" — every language named in ITSELF, never
        // translated. A player who has landed in a language they cannot read has to find their own
        // on this list, and their own is the only entry they are certain to recognise. It is the one
        // control in the client that looks identical in every locale, and that is the point.
        ChoiceBox<io.github.stoicswe.eyeandsickle.client.i18n.Language> language = new ChoiceBox<>();
        language.getItems().addAll(io.github.stoicswe.eyeandsickle.client.i18n.Language.shipped());
        language.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(io.github.stoicswe.eyeandsickle.client.i18n.Language value) {
                return value == null ? "" : value.endonym();
            }

            @Override
            public io.github.stoicswe.eyeandsickle.client.i18n.Language fromString(String text) {
                return null;
            }
        });
        language.setValue(
                io.github.stoicswe.eyeandsickle.client.i18n.Text.current().language());
        language.valueProperty().addListener((o, was, now) -> {
            if (now == null) {
                return;
            }
            profile.settings().language = now.tag();
            profile.save();
            io.github.stoicswe.eyeandsickle.client.i18n.Text.use(now);
        });

        ChoiceBox<String> teaching = new ChoiceBox<>();
        teaching.getItems().addAll("explain", "terms", "off");
        teaching.setValue(profile.settings().teachingLevel);
        teaching.valueProperty().addListener((o, was, now) -> {
            profile.settings().teachingLevel = now;
            profile.save();
        });

        // ⚠ §9's rejection list bans rounded corners, and that still describes the DEFAULT. This is
        // opt-in and off unless asked for, because it is the player's screen and a radius is a
        // matter of taste rather than of legibility. What it never rounds is a measurement — see
        // theme.css's block and UiContractTest.RoundedOptIn.
        // ⚠ §0.1, amended 2026-07-28. The one setting that contradicts §0 outright — that document
        // cancelled the Stage-per-tool model because "the entire aesthetic depends on the player
        // never seeing their own operating system", and this hands the OS its frame back. Offered
        // for the same reason §9.1 and §9.3 are: it is the player's machine. Off by default, so the
        // shipped game still looks like the game.
        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch nativeBorder =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.windows.nativeBorder", "Use the system window border"));
        nativeBorder.setSelected(profile.settings().nativeWindowBorder);
        nativeBorder.selectedProperty().addListener((o, was, now) -> {
            profile.settings().nativeWindowBorder = now;
            profile.save();
        });

        // ⚠ Order only, and desk windows only — see ui/chrome/ControlOrder. The outer window keeps
        // following the host OS: it sits beside the player's real windows and is judged against
        // them, and letting somebody put close where their OS puts zoom is the one arrangement
        // guaranteed to cost a session.
        ChoiceBox<io.github.stoicswe.eyeandsickle.client.ui.chrome.ControlOrder> controlOrder = new ChoiceBox<>();
        controlOrder.getItems().addAll(io.github.stoicswe.eyeandsickle.client.ui.chrome.ControlOrder.selectable());
        controlOrder.setValue(io.github.stoicswe.eyeandsickle.client.ui.chrome.ControlOrder.resolve(
                profile.appearance().subwindowControlOrder));
        controlOrder.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(io.github.stoicswe.eyeandsickle.client.ui.chrome.ControlOrder o) {
                return o == null ? "" : o.label();
            }

            @Override
            public io.github.stoicswe.eyeandsickle.client.ui.chrome.ControlOrder fromString(String s) {
                return io.github.stoicswe.eyeandsickle.client.ui.chrome.ControlOrder.SYSTEM;
            }
        });
        controlOrder.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                profile.appearance().subwindowControlOrder = now.id();
                profile.save();
                if (onDeskSettingsChanged != null) {
                    onDeskSettingsChanged.run();
                }
            }
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch rounded =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.windows.rounded", "Rounded window corners"));
        rounded.setSelected(profile.appearance().roundedWindows);
        // ⚠ THE SYNC BELOW DRIVES THIS SWITCH, AND WITHOUT THIS GUARD IT DESTROYS THE SETTING IT IS
        // DISPLAYING. Showing the effective state under a liquid palette means calling setSelected
        // (true), which fires this listener, which writes roundedWindows = true and saves — so a
        // player who tried the theme for ten seconds would find their square deck permanently
        // round, with nothing on screen having said so. The flag is what keeps "display the
        // effective state" from meaning "adopt it".
        boolean[] syncingRounded = {false};
        rounded.selectedProperty().addListener((o, was, now) -> {
            if (syncingRounded[0]) {
                return;
            }
            profile.appearance().roundedWindows = now;
            profile.save();
            if (onDeskSettingsChanged != null) {
                onDeskSettingsChanged.run();
            }
        });
        // ⚠ A LIQUID PALETTE ROUNDS WINDOWS ITSELF, so under one the switch is shown ON and disabled
        // rather than left alive and apparently broken. This is the failure the security centre's
        // verdict already recorded: a player cannot tell "your setting changed nothing" from "the
        // control is broken", and they assume the control. Reporting the EFFECTIVE state and saying
        // who decided it is the only honest option.
        //
        // ⚠ The stored setting is NOT overwritten — see ThemeId.roundsCorners. It is restored on
        // screen the moment a non-liquid palette is picked, which is why this reads the appearance
        // back rather than trusting what it last displayed.
        Label roundedForced = wrapped(t(
                "settings.windows.rounded.themed",
                "uOS Modern Liquid Abs rounds windows itself — glass with hard corners is not the "
                        + "material. Your own setting is remembered untouched and comes back as "
                        + "soon as you choose another look."));
        syncRoundedSwitch[0] = () -> {
            boolean themed = ThemeId.byId(profile.appearance().themeId)
                    .orElse(ThemeId.DECK)
                    .roundsCorners();
            syncingRounded[0] = true;
            try {
                rounded.setDisable(themed);
                rounded.setSelected(themed || profile.appearance().roundedWindows);
            } finally {
                syncingRounded[0] = false;
            }
            // ⚠ setManaged too, not setVisible alone: an invisible-but-managed Label holds its full
            // wrapped height, so the page would carry a paragraph of empty space under the switch on
            // every palette that is not a liquid one.
            roundedForced.setVisible(themed);
            roundedForced.setManaged(themed);
        };
        syncRoundedSwitch[0].run();

        // ── the focused-window outline (opt-in) ──────────────────────────────────────────────
        //
        // The deck already marks focus by lightening the strip and accenting the title, quietly on
        // purpose. This is for players for whom that is not enough — a low-contrast strip change is
        // exactly the cue that vanishes on a dim screen. Off by default; see ui/chrome/FocusRing.
        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch focusRing =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.desk.focusRing", "Outline the focused window"));
        focusRing.setSelected(profile.appearance().focusRing);

        HBox swatches = new HBox(UiTokens.SPACE_2);
        swatches.setAlignment(Pos.CENTER_LEFT);
        java.util.List<Region> chips = new java.util.ArrayList<>();
        Runnable[] markSelected = new Runnable[1];
        for (var ring : io.github.stoicswe.eyeandsickle.client.ui.chrome.FocusRing.selectable()) {
            Region chip = new Region();
            chip.getStyleClass().addAll("es-swatch", "es-swatch-" + ring.id(), "es-focusable");
            // ⚠ Named, not just coloured. A swatch row is the one control where colour IS the
            // content, so the label has to reach a screen reader and a tooltip — §4.4 and
            // docs/client/07 §5.2 both.
            chip.setAccessibleText(ring.label());
            javafx.scene.control.Tooltip.install(chip, new javafx.scene.control.Tooltip(ring.label()));
            io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(chip);
            chip.setOnMouseClicked(e -> {
                profile.appearance().focusRingColor = ring.id();
                profile.save();
                markSelected[0].run();
                if (onDeskSettingsChanged != null) {
                    onDeskSettingsChanged.run();
                }
            });
            chips.add(chip);
            swatches.getChildren().add(chip);
        }
        markSelected[0] = () -> {
            var chosen = io.github.stoicswe.eyeandsickle.client.ui.chrome.FocusRing.byId(
                    profile.appearance().focusRingColor);
            var all = io.github.stoicswe.eyeandsickle.client.ui.chrome.FocusRing.selectable();
            for (int i = 0; i < chips.size(); i++) {
                chips.get(i).getStyleClass().remove("es-swatch-on");
                if (all.get(i) == chosen) {
                    chips.get(i).getStyleClass().add("es-swatch-on");
                }
            }
        };
        markSelected[0].run();

        focusRing.selectedProperty().addListener((o, was, now) -> {
            profile.appearance().focusRing = now;
            profile.save();
            if (onDeskSettingsChanged != null) {
                onDeskSettingsChanged.run();
            }
        });
        // ⚠ The swatches stay ENABLED with the ring off. Greying them would make choosing a colour
        // impossible until the feature is already on, which is backwards: a player deciding whether
        // they want this wants to see what it would look like first.
        Label swatchNote =
                secondary("The first is your palette's own accent, so it follows the " + "theme. The rest are fixed.");
        swatchNote.setWrapText(true);

        // §11 question 1, shipped as a choice rather than settled by fiat. See DeskManager.
        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch freeDrag =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.desk.freeDrag", "Drag windows freely"));
        freeDrag.setSelected(profile.settings().freeDragWindows);
        freeDrag.selectedProperty().addListener((o, was, now) -> {
            profile.settings().freeDragWindows = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch bandwidthCap =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.desk.bandwidthCap", "Bandwidth limits open windows  [PROPOSAL]"));
        bandwidthCap.setSelected(profile.settings().bandwidthCapsWindows);
        bandwidthCap.selectedProperty().addListener((o, was, now) -> {
            profile.settings().bandwidthCapsWindows = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        // The desk wallpaper. Three states rather than a checkbox, because "I want the texture but
        // not the movement" is a real preference and WCAG 2.2.2 requires the pause to exist at all.
        ChoiceBox<io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode> wallpaper = new ChoiceBox<>();
        wallpaper.getItems().addAll(io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode.selectable());
        wallpaper.setValue(io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode.byId(profile.appearance().wallpaper)
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
                profile.appearance().wallpaper = now.id();
                profile.save();
                onDeskSettingsChanged.run();
            }
        });

        // Published by the WINDOW section below, so the CASING control can make it rebuild: the
        // casing sits outside the viewport, so choosing one changes which resolutions still fit.
        Runnable[] onWindowSizingChanged = {null};

        // §9 cut bezel twice and §9.1 kept it cut when four other artefacts were permitted.
        // Permitted since 2026-07-27 on explicit direction, under the same four conditions — and
        // "off by default, switchable off permanently" is the first of them.
        ChoiceBox<io.github.stoicswe.eyeandsickle.client.ui.BezelStyle> bezel = new ChoiceBox<>();
        bezel.getItems().addAll(io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.selectable());
        bezel.setValue(io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.byId(profile.appearance().bezel)
                .orElse(io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.OFF));
        Label bezelNote = wrapped(bezel.getValue().note());
        bezelNote.getStyleClass().add("es-text-secondary");
        bezel.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(io.github.stoicswe.eyeandsickle.client.ui.BezelStyle style) {
                return style == null ? "" : style.label();
            }

            @Override
            public io.github.stoicswe.eyeandsickle.client.ui.BezelStyle fromString(String s) {
                return io.github.stoicswe.eyeandsickle.client.ui.BezelStyle.OFF;
            }
        });
        bezel.valueProperty().addListener((o, was, now) -> {
            if (now != null) {
                profile.appearance().bezel = now.id();
                bezelNote.setText(now.note());
                profile.save();
                onDeskSettingsChanged.run();
                // ⚠ The casing sits OUTSIDE the viewport, so changing it changes the window size and
                // therefore which resolutions still fit. The WINDOW section rebuilds and re-applies,
                // or picking a chunky casing silently clamps the resolution the player chose.
                if (onWindowSizingChanged[0] != null) {
                    onWindowSizingChanged[0].run();
                }
            }
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch scanlines =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.screen.scanlines", "CRT scanlines"));
        scanlines.setSelected(profile.appearance().crtScanlines);
        scanlines.selectedProperty().addListener((o, was, now) -> {
            profile.appearance().crtScanlines = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch aberration =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.screen.aberration", "Chromatic aberration"));
        aberration.setSelected(profile.appearance().crtAberration);
        aberration.selectedProperty().addListener((o, was, now) -> {
            profile.appearance().crtAberration = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        // A slider rather than a checkbox: curvature is the one artefact with a useful middle. A
        // trace of rim aberration reads as glass; a lot of it reads as a cheap filter, and where the
        // line falls between those is taste, which is exactly what a slider is for.
        Slider curvature = new Slider(0, 100, profile.appearance().crtCurvature);
        curvature.setShowTickMarks(true);
        curvature.setMajorTickUnit(25);
        curvature.setBlockIncrement(5);
        Label curvatureValue =
                io.github.stoicswe.eyeandsickle.client.ui.Ui.micro(profile.appearance().crtCurvature + "%");
        curvature.valueProperty().addListener((o, was, now) -> {
            profile.appearance().crtCurvature = (int) Math.round(now.doubleValue());
            curvatureValue.setText(profile.appearance().crtCurvature + "%");
            profile.save();
            onDeskSettingsChanged.run();
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch glitch =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.screen.glitch", "Signal glitch"));
        glitch.setSelected(profile.appearance().crtGlitch);
        glitch.selectedProperty().addListener((o, was, now) -> {
            profile.appearance().crtGlitch = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch chromatic =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.screen.chromatic", "Shift the wallpaper's colours"));
        chromatic.setSelected(profile.appearance().wallpaperChromatic);
        chromatic.selectedProperty().addListener((o, was, now) -> {
            profile.appearance().wallpaperChromatic = now;
            profile.save();
            onDeskSettingsChanged.run();
        });

        ChoiceBox<io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin> cursor = new ChoiceBox<>();
        cursor.getItems().addAll(io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin.selectable());
        cursor.setValue(
                io.github.stoicswe.eyeandsickle.client.ui.cursors.CursorSkin.byId(profile.appearance().cursorSkin)
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
                profile.appearance().cursorSkin = now.id();
                profile.save();
                // Through the theme manager, because a pointer is drawn in the current palette's
                // colours and only the theme manager knows which stylesheets are live.
                themes.refreshCursors();
            }
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch notify =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.notices.show", "Show slide-in notices"));
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
            io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch box =
                    new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(facility);
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

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch reducedMotion =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(
                        t("settings.accessibility.reduceMotion", "Reduce motion"));
        reducedMotion.setSelected(themes.reducedMotion());
        reducedMotion.selectedProperty().addListener((o, was, now) -> {
            themes.setReducedMotionOverride(now);
            profile.save();
        });

        VBox window = windowSection(profile, onWindowChanged, onWindowSizingChanged);

        // ── the pages ─────────────────────────────────────────────────────────────────────────
        //
        // ⚠ Rebuilt from one long scroll into a sidebar and a detail pane on 2026-07-28, following
        // macOS System Settings. The old shape put thirteen headings in a single column, so finding
        // "wallpaper" meant scrolling past the pointer, the severity floor and every subsystem
        // checkbox — and there is no scroll position at which a player can see what the panel even
        // covers. A category list answers "what can I change" before "change it", which is the whole
        // reason that layout won.
        //
        // Categories are declared in ONE map, in display order, and the sidebar is built from it.
        // A second list of names beside the pages would be two things to keep in step.
        java.util.LinkedHashMap<String, javafx.scene.Node> pages = new java.util.LinkedHashMap<>();

        // ── the picture ───────────────────────────────────────────────────────────────────────
        //
        // ⚠ The one place this client reads a host file it did not write — see AvatarChooser for
        // the three conditions that keep §7's boundary intact. What is stored is the PIXELS; the
        // path the player picked is discarded and never persisted.
        VBox avatarBox = new VBox(6);
        Runnable[] refreshAvatar = new Runnable[1];
        BreachView.Chip pickPicture = new BreachView.Chip("Choose picture", "es-files-action");
        BreachView.Chip clearPicture = new BreachView.Chip("Use default", "es-files-action");
        refreshAvatar[0] = () -> {
            avatarBox
                    .getChildren()
                    .setAll(AvatarChooser.row(
                            session == null ? "" : session.avatar(),
                            session == null ? profile.settings().soloHandle : session.handle(),
                            52));
        };
        refreshAvatar[0].run();
        pickPicture.onInvoke(() -> {
            if (session == null) {
                return;
            }
            AvatarChooser.choose(
                    avatarBox.getScene() == null ? null : avatarBox.getScene().getWindow(),
                    session.handle(),
                    encoded -> {
                        session.setAvatar(encoded);
                        refreshAvatar[0].run();
                    });
        });
        clearPicture.onInvoke(() -> {
            if (session != null) {
                session.setAvatar("");
                refreshAvatar[0].run();
            }
        });

        pages.put(
                t("settings.cat.operator", "Operator"),
                settingsPage(
                        avatarBox,
                        Ui.row(8, pickPicture, clearPicture),
                        // \u26a0 A key per BRANCH, not one around the ternary. Wrapping the conditional
                        // would give both messages the same key, so a translation of either would
                        // replace both \u2014 and these two say opposite things, one that a picture can be
                        // set and one that it cannot yet.
                        wrapped(
                                session == null
                                        ? t(
                                                "settings.operator.picture.noCharacter",
                                                "A picture can be set once a character is loaded.")
                                        : t(
                                                "settings.operator.picture",
                                                "Opens your system's own file dialog, then lets you crop and zoom. The "
                                                        + "picture is stored with the character, not as a link to the file "
                                                        + "you picked \u2014 so it travels with the save, and the game never "
                                                        + "reads that location again. With none set you get a silhouette "
                                                        + "generated from your handle, breaking up under static.")),
                        new Separator(),
                        new HBox(8, handle, applyHandle),
                        // ⚠ A key per branch — see the picture caption above for why.
                        wrapped(
                                onRename != null
                                        ? t(
                                                "settings.operator.handle.rename",
                                                "Your handle, shown on the strip with its bytes underneath. Solo "
                                                        + "only: online, a handle is not yours to choose — identity "
                                                        + "comes from an AT Proto DID and the server owns it.")
                                        : t(
                                                "settings.operator.handle",
                                                "Sets the handle for the next character you start. Renaming a "
                                                        + "character you are already playing is done from inside "
                                                        + "the game.")),
                        handleResult,
                        new Separator(),
                        new HBox(8, hostname, applyHostname),
                        wrapped(t(
                                "settings.operator.hostname",
                                "What the rig calls itself. The prompt reads "
                                        + "`handle@hostname.local:~$` — who you are, then where you are, "
                                        + "which is the order every terminal and every SSH session uses. "
                                        + "`.local` is mDNS: the name a machine answers to on the network "
                                        + "it is plugged into with nobody having configured DNS, and your "
                                        + "own machine has one. Letters, digits and hyphens only, 63 "
                                        + "characters at most — DNS's rules, not this game's.")),
                        hostnameResult));

        pages.put(
                t("settings.cat.appearance", "Appearance"),
                settingsPage(
                        scopeNote(session),
                        theme,
                        wrapped(t(
                                "settings.appearance.themes",
                                "Every theme is the same deck with a different palette — one stylesheet "
                                        + "owns the layout, the hairlines and the motion, so no skin can hide or "
                                        + "soften a number. \"Deck — high visibility\" raises body text to WCAG "
                                        + "AAA and makes every hairline visible; it is an accessibility floor "
                                        + "rather than a style, and nothing else about the client changes."))));

        pages.put(
                t("settings.cat.windows", "Windows"),
                settingsPage(
                        scopeNote(session),
                        nativeBorder,
                        wrapped(t(
                                "settings.windows.nativeBorder.note",
                                "Gives the game window your system's own title bar and buttons "
                                        + "instead of the ones it draws itself. Takes effect the next time "
                                        + "you start the game — a window's frame is fixed before it first "
                                        + "appears and cannot be swapped while it is open. With it on, the "
                                        + "game stops drawing its own window buttons and the top strip stops "
                                        + "acting as a drag handle, because your title bar already does both.")),
                        new Separator(),
                        rounded,
                        roundedForced,
                        wrapped(t(
                                "settings.windows.rounded.note",
                                "Off by default, and deliberately: this deck is drawn in hard edges "
                                        + "and hairlines, and softening them is the first step toward looking "
                                        + "like an ordinary dark-mode developer tool. It rounds windows only "
                                        + "— never a meter cell or the cycle grid, because a cell with a soft "
                                        + "corner reads as a smaller cell and those are meant to be counted.")),
                        wrapped(t(
                                "settings.windows.rounded.scope",
                                "Applies to everything at once — the game window and every window "
                                        + "on the desk — and takes effect immediately. On Linux without a "
                                        + "compositing window manager the corners may come out black rather "
                                        + "than transparent; that is the one place this depends on your "
                                        + "desktop rather than on the game.")),
                        new Separator(),
                        Ui.label(t("settings.windows.buttonOrder", "Window buttons, inside the game")),
                        controlOrder,
                        wrapped(t(
                                "settings.windows.buttonOrder.note",
                                "Which order the buttons on a tool window sit in. It does not move them to "
                                        + "the other side — that follows your system and stays there — and it does "
                                        + "not touch the game's own window, which sits next to your real ones and "
                                        + "should behave like them. Takes effect immediately, on windows that are "
                                        + "already open.")),
                        new Separator(),
                        window));

        pages.put(
                t("settings.cat.desk", "Desk"),
                settingsPage(
                        focusRing,
                        wrapped(t(
                                "settings.desk.focusRing.note",
                                "The deck already marks the focused window by lightening its strip and "
                                        + "accenting its title. This adds an outline as well, for when that is not "
                                        + "enough to find at a glance. Off by default; takes effect immediately, on "
                                        + "windows that are already open.")),
                        swatches,
                        swatchNote,
                        new Separator(),
                        freeDrag,
                        wrapped(t(
                                "settings.desk.freeDrag.note",
                                "Off: windows snap to a grid, and tile when dragged against an edge of "
                                        + "the desk — a side fills that half, a corner that quarter. On: they go "
                                        + "exactly where you put them.")),
                        new Separator(),
                        bandwidthCap,
                        wrapped(t(
                                "settings.desk.bandwidthCap.note",
                                "Off by default, and this one is not calibrated. The idea is that screen "
                                        + "space is attention: Bandwidth caps how many engagements run at once, so "
                                        + "it should cap how many tools you can have open. A starting rig has 1 "
                                        + "Bandwidth, so the budget below adds six always-free windows — the "
                                        + "monitor, terminal, log, manual, settings and switcher — to it. That "
                                        + "arithmetic is invented, which is why this is opt-in."))));

        pages.put(
                t("settings.cat.screen", "Screen"),
                settingsPage(
                        scopeNote(session),
                        Ui.label(t("settings.screen.casing", "Casing")),
                        bezel,
                        bezelNote,
                        wrapped(t(
                                "settings.screen.casing.note",
                                "The machine around the screen. Off by default. It is drawn "
                                        + "OUTSIDE the viewport \u2014 the resolution you pick under Windows "
                                        + "is the screen's, and the casing is added beyond it, so the "
                                        + "window grows rather than the deck shrinking. It never covers "
                                        + "anything you have to read, and nothing about it moves, so it "
                                        + "costs no frames and is unaffected by Reduce motion. Pairs with "
                                        + "the Cyberdeck palette, but every theme draws it.")),
                        new Separator(),
                        Ui.label(t("settings.screen.wallpaper", "Wallpaper")),
                        wallpaper,
                        chromatic,
                        wrapped(t(
                                "settings.screen.chromatic.note",
                                "Pulls the wallpaper's colour channels apart and back on a slow "
                                        + "cycle. On the ring it fringes the tears; on the character "
                                        + "texture it separates the field itself. It holds still in "
                                        + "the paused wallpaper modes, because a shift that kept "
                                        + "moving there would be motion you had already stopped.")),
                        wrapped(t(
                                "settings.screen.wallpaper.note",
                                "Machine texture behind every window — the same alphabet as the greeble "
                                        + "strips, drawn far dimmer and never in amber. \"Still\" keeps the "
                                        + "texture and stops the movement. Turning on Reduce motion under "
                                        + "Accessibility stops it too, without changing this setting.")),
                        new Separator(),
                        Ui.label(t("settings.screen.artefacts", "Artefacts")),
                        scanlines,
                        aberration,
                        glitch,
                        new HBox(8, curvature, curvatureValue),
                        wrapped(t(
                                "settings.screen.artefacts.note",
                                "Screen artefacts, all three off by default. Scanlines lay a dark band "
                                        + "across every other row of pixels and drift slowly, with a refresh bar "
                                        + "rolling down the screen — that is what makes them read as a tube "
                                        + "rather than as a texture. They cost real contrast on body text, which "
                                        + "is a trade to make deliberately rather than one the client makes for "
                                        + "you. Aberration separates the wallpaper into red and cyan a pixel "
                                        + "either side; it is not applied to the whole screen, which would cost "
                                        + "more per frame than the effect is worth. Signal glitch tears short "
                                        + "fragments off the edges of windows and the elements inside them, so a "
                                        + "busy desk breaks up more than an empty one. Reduce motion stops every "
                                        + "moving part and leaves the still ones drawn.")),
                        wrapped(t(
                                "settings.screen.curvature.note",
                                "Edge curvature raises the red/cyan separation towards the rim and the "
                                        + "corners, the way curved glass does — zero in the middle, worst at the "
                                        + "corners. It does NOT bend the interface: warping the picture would "
                                        + "need a shader we do not have, and faking it would put every click "
                                        + "somewhere other than where you see the control. Text stays straight."))));

        pages.put(
                t("settings.cat.notices", "Notices"),
                settingsPage(
                        notify,
                        wrapped(t(
                                "settings.notices.note",
                                "A notice repeats something the rig already logged — nothing here is "
                                        + "the only place a message exists, and the log window keeps all of "
                                        + "it. Ignoring every notice costs you nothing.")),
                        new Separator(),
                        Ui.label(t("settings.notices.severity", "Severity floor")),
                        severity,
                        wrapped(t(
                                "settings.notices.severity.note",
                                "These are RFC 5424 levels, and the numbering runs backwards on "
                                        + "purpose: 0 is Emergency and 7 is Debug, so a LOWER number is a "
                                        + "stricter filter. It is the same number `log -p` takes — set 4 "
                                        + "here, type `log -p 4`, and you will see the same set. That habit "
                                        + "works on any Linux machine you ever touch.")),
                        new Separator(),
                        Ui.label(t("settings.notices.subsystems", "Subsystems")),
                        facilities,
                        wrapped(t(
                                "settings.notices.subsystems.note",
                                "Unchecked subsystems stay silent. These are the rig's own facility "
                                        + "names, so anything you mute here is still findable with "
                                        + "`log | grep <name>`."))));

        pages.put(
                t("settings.cat.teaching", "Teaching"),
                settingsPage(
                        teaching,
                        wrapped(t(
                                "settings.teaching.note",
                                "`explain` shows a plain-language line with each term; `terms` shows the "
                                        + "term only; `off` shows neither. The manual stays available at any "
                                        + "level — try `man compute`."))));

        // ⚠ Pointer and Motion live together under Accessibility, which is where macOS puts them
        // and where a player looking for either will look. Both are also genuine accessibility
        // controls rather than decoration: the system pointer default is a floor (docs/client/07),
        // and Reduce motion follows the OS preference unless overridden.
        pages.put(
                t("settings.cat.accessibility", "Accessibility"),
                settingsPage(
                        Ui.label(t("settings.accessibility.pointer", "Pointer")),
                        cursor,
                        scopeNote(session),
                        wrapped(t(
                                "settings.accessibility.pointer.note",
                                "The pointer is the last piece of your operating system left on "
                                        + "screen, so the deck can draw its own — in whatever colour the "
                                        + "current theme means by \"live\". \"System pointer\" leaves yours "
                                        + "alone, and that is the default on purpose: your OS has already "
                                        + "tuned it for your display and your eyesight. The text I-beam is "
                                        + "never replaced under any skin, because its shape tells you which "
                                        + "two characters the caret will land between.")),
                        new Separator(),
                        Ui.label(t("settings.accessibility.motion", "Motion")),
                        reducedMotion,
                        wrapped(t(
                                "settings.accessibility.motion.note",
                                "Follows your system setting unless you change it here. Suppresses the "
                                        + "panel wipe, the caret blink, the greeble and the sweep bar; readouts "
                                        + "keep updating, because that is information, not animation."))));

        // ── AnonShare ────────────────────────────────────────────────────────────────────────
        //
        // ⚠ MACHINE-WIDE, beside Language and text size, not per character. A key is a credential
        // about this installation; asking a player to paste it again for every new character would
        // be the same mistake per-character accessibility settings would have been.
        javafx.scene.control.ComboBox<io.github.stoicswe.eyeandsickle.engine.stocks.StockProvider> provider =
                new javafx.scene.control.ComboBox<>();
        provider.getItems().addAll(io.github.stoicswe.eyeandsickle.engine.stocks.StockProvider.values());
        provider.getSelectionModel()
                .select(io.github.stoicswe.eyeandsickle.engine.stocks.StockProvider.parse(
                        profile.settings().stockProvider));
        Label providerLimits = Ui.micro("");
        Runnable describeProvider = () -> {
            var chosen = provider.getSelectionModel().getSelectedItem();
            // ⚠ The DATE travels with the figure. Rate limits go stale, and a number in this repo
            // read as current fact two years from now is worse than no number.
            providerLimits.setText(chosen.limits() + "  ·  checked "
                    + io.github.stoicswe.eyeandsickle.engine.stocks.StockProvider.LIMITS_CHECKED
                    + "  ·  key and terms: " + chosen.signupUrl());
        };
        describeProvider.run();
        provider.setOnAction(event -> {
            profile.settings().stockProvider =
                    provider.getSelectionModel().getSelectedItem().name();
            describeProvider.run();
            profile.save();
        });

        javafx.scene.control.PasswordField apiKey = new javafx.scene.control.PasswordField();
        apiKey.setText(profile.settings().stockApiKey);
        apiKey.setPromptText(t("settings.anon.key.prompt", "Your API key — blank uses simulated prices"));
        // ⚠ A PasswordField, so a key does not sit in plain view during a screen share. It is not
        // encrypted at rest and does not pretend to be — it is in a file on the player's own machine
        // beside a save they can edit freely — but shoulder-surfing is a real and cheap thing to stop.
        apiKey.textProperty().addListener((o, was, now) -> {
            profile.settings().stockApiKey = now == null ? "" : now.trim();
            profile.save();
        });

        Slider refresh = new Slider(
                io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.Settings.STOCK_REFRESH_MIN,
                io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.Settings.STOCK_REFRESH_MAX,
                profile.settings().stockRefreshSeconds);
        refresh.setShowTickMarks(true);
        refresh.setMajorTickUnit(120);
        refresh.setBlockIncrement(15);
        Label refreshValue = Ui.micro("");
        Runnable describeRefresh = () -> {
            int seconds = profile.settings().stockRefreshSeconds;
            // ⚠ Says what it COSTS, not just what it is. The number a player is really choosing is
            // how much of their own free-tier allowance the panel spends, and calls-per-day is the
            // figure the provider's limit is quoted in.
            long perDay = 86400L / Math.max(1, seconds);
            refreshValue.setText(
                    seconds < 60
                            ? seconds + "s  ·  up to " + perDay + " calls a day per symbol"
                            : (seconds / 60) + "m " + (seconds % 60) + "s  ·  up to " + perDay
                                    + " calls a day per symbol");
        };
        describeRefresh.run();
        refresh.valueProperty().addListener((o, was, now) -> {
            profile.settings().stockRefreshSeconds = (int) Math.round(now.doubleValue());
            describeRefresh.run();
            profile.save();
        });

        pages.put(
                t("settings.cat.anonshare", "AnonShare"),
                settingsPage(
                        Ui.label(t("settings.anon.provider", "Quote provider")),
                        provider,
                        providerLimits,
                        wrapped(t(
                                "settings.anon.key.note",
                                "AnonShare uses YOUR OWN key. Nothing ships with this game and nothing "
                                        + "is shared between players, so the allowance is yours alone and the "
                                        + "provider's terms are between you and them — read them at the link "
                                        + "above before you sign up.")),
                        new Separator(),
                        Ui.label(t("settings.anon.key", "API key")),
                        apiKey,
                        wrapped(t(
                                "settings.anon.key.storage",
                                "Kept in your settings file on this machine, unencrypted — the same file "
                                        + "as everything else here. It is never logged and never sent anywhere "
                                        + "except to the provider it belongs to. Leave it blank and the panel "
                                        + "runs on simulated prices, clearly marked as such.")),
                        new Separator(),
                        Ui.label(t("settings.anon.refresh", "How often to fetch a price")),
                        refresh,
                        refreshValue,
                        wrapped(t(
                                "settings.anon.refresh.note",
                                "Slow on purpose. A share price moves on a scale of minutes, and every "
                                        + "refresh spends part of the daily allowance you are paying for. The "
                                        + "market window reads this when you open it."))));

        pages.put(
                t("settings.cat.language", "Language"),
                settingsPage(
                        Ui.label(t("settings.language.label", "Interface language")),
                        language,
                        wrapped(t(
                                "settings.language.endonyms",
                                "Every language is listed in its own name, so you can find yours "
                                        + "whichever one the game is currently in.")),
                        new Separator(),
                        wrapped(t(
                                "settings.language.scope",
                                "Windows you open from now on are in the new language; ones already "
                                        + "on screen keep the text they were built with until you close and "
                                        + "reopen them.")),
                        new Separator(),
                        wrapped(t(
                                "settings.language.structure",
                                "Command names and their options are never translated \u2014 `grep -v` "
                                        + "is `grep -v` everywhere, because that is what it is called on a "
                                        + "real machine and carrying that knowledge out of the game is the "
                                        + "point. What each one MEANS is translated, and so is the manual. A "
                                        + "page nobody has translated yet is shown in English rather than "
                                        + "left out."))));

        pages.put(t("settings.cat.about", "About"), settingsPage(about(profile, session)));

        // Beneath About, and out of the fiction entirely — see Credits' class comment for why the
        // real people are not a section of the spec sheet.
        pages.put(
                t("settings.cat.credits", "Credits"),
                settingsPage(
                        Credits.page(),
                        new Separator(),
                        wrapped(t(
                                "settings.credits.note",
                                "Handles are printed rather than linked: opening a browser would throw you "
                                        + "out of the game, and this client has never opened one."))));

        root.getChildren().add(settingsBody(pages));
        // ⚠ FILLING, not plain scrollable, and this was a real bug rather than a refinement. A
        // ScrollPane hands its content the content's OWN preferred height, so `root` was exactly as
        // tall as the selected category happened to be — which made every Vgrow inside it a no-op.
        // The visible symptom was not the pages: it was the sidebar's divider stopping partway down
        // the window with dead space under it, so a short category looked like the panel had ended
        // early. See scrollable(Region, boolean).
        return scrollable(root, true);
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
    static Region scrollable(Region content) {
        return scrollable(content, false);
    }

    /**
     * @param fillHeight whether the content should be stretched to the viewport when it is shorter
     *     than the window
     *     <p>⚠ Off for most panels and ON for anything whose own children use {@code Vgrow}. A
     *     ScrollPane gives its content the content's <b>preferred</b> height, so a panel that
     *     contains a full-height divider, a sidebar or a pane meant to reach the bottom gets none of
     *     it: every grow constraint inside is measured against a box that already stopped at the
     *     content. Settings had exactly this — the category divider ended halfway down the window
     *     and the space beneath it read as the panel having ended.
     *     <p>It is not the default because stretching a short panel is only right when something
     *     inside wants the room. Elsewhere it would hand a three-line panel the whole window and
     *     move nothing, or worse, stretch a control that grows badly.
     *     <p>Note this never <em>shrinks</em> anything: past the viewport height the content keeps
     *     its own size and the pane scrolls as before.
     */
    private static Region scrollable(Region content, boolean fillHeight) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(fillHeight);
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
    /**
     * About — what this machine is, in the shape a real one answers that question.
     *
     * <h2>Real facts, in a real order</h2>
     *
     * Every line is read from the session. None of it is decoration: the cycle count is the compute
     * budget the rig monitor draws, the tier capacities are the ones the storage rules enforce, the
     * uptime is the character's own. A spec sheet with invented numbers on it would be the one
     * screen in the game that lies for atmosphere, and this game's whole teaching posture is that it
     * does not.
     *
     * <p>⚠ Compute is stated in <b>cycles</b> and never converted to gigahertz. A rig's capacity is
     * a count of concurrent work, not a clock speed ({@code docs/design/01} §1.1), and dressing it
     * up as MHz would teach the one thing about this resource that is false.
     */
    private static VBox about(ClientProfile profile, GameSession session) {
        VBox box = new VBox(4);
        if (session == null) {
            box.getChildren().add(secondary("No character loaded."));
            box.getChildren()
                    .add(wrapped(t("ui.views.profile-directory", "Profile directory: " + profile.directory())));
            return box;
        }
        var capacity = session.capacity();
        long total = session.computeBudget().total().cycles();
        long uptime = session.uptimeSeconds();

        box.getChildren()
                .addAll(
                        Ui.label(t("ui.views.uos", "uOS")),
                        spec("System", "uOS 15.0-RELEASE"),
                        spec("Kernel", "FreeBSD-derived, GENERIC"),
                        spec(
                                "Hostname",
                                io.github.stoicswe.eyeandsickle.client.profile.Hostname.qualified(
                                        profile.settings().rigHostname)),
                        spec("Operator", session.handle()),
                        spec("Mode", session.mode().label()),
                        new Separator(),
                        Ui.label(t("ui.views.hardware", "Hardware")),
                        // Cycles, never gigahertz. See the class comment.
                        spec("Compute", total + " cycles"),
                        spec("Memory buffer", capacity.memoryBuffer() + " units"),
                        spec("Bandwidth", capacity.bandwidth() + " concurrent"),
                        spec("Thermal budget", capacity.thermalBudget() + " units"),
                        new Separator(),
                        Ui.label(t("ui.views.storage", "Storage")),
                        spec("Vault", tier(session, io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT)),
                        spec(
                                "Standard",
                                tier(
                                        session,
                                        io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.STANDARD_STORAGE)),
                        spec(
                                "Hot zone",
                                tier(
                                        session,
                                        io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.HIGH_HACKABLE_ZONE)),
                        new Separator(),
                        Ui.label(t("ui.views.this-character", "This character")),
                        spec("Uptime", Ui.clock(uptime)),
                        // The local Ethecoin.format() formatter, which is now the same string Ethecoin's own toString
                        // produces. This used to carry a warning that the record's generated toString leaked
                        // "Ethecoin[wei=0]" onto the screen — true at the time, and the reason the
                        // type now renders itself. See Ethecoin#toString for what it cost to find out.
                        spec("Balance", Ethecoin.format(session.balance().wei())),
                        new Separator(),
                        wrapped(t("ui.views.profile-directory-2", "Profile directory: " + profile.directory())),
                        wrapped(t(
                                "ui.views.everything-this-client-writes",
                                "Everything this client writes lives in that one directory — settings, "
                                        + "window positions and the save. It is the only place on your machine the "
                                        + "game touches, which is what lets the terminal look like a shell without "
                                        + "being one.")));
        return box;
    }

    /** {@code KEY   value} — the readout shape the rest of the client uses. */
    private static HBox spec(String key, String value) {
        Label name = Ui.label(key);
        name.setMinWidth(130);
        HBox row = new HBox(8, name, Ui.value(value));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String tier(GameSession session, io.github.stoicswe.eyeandsickle.protocol.game.StorageTier t) {
        return session.items(t).size() + " of " + session.storageCapacity(t) + " slots";
    }

    /**
     * One settings page: a column of controls and the prose that explains them.
     *
     * <p>Prose stays <em>with</em> its control rather than being collected at the bottom. Several of
     * these settings have consequences a player cannot see from the checkbox — scanlines cost real
     * contrast, the Bandwidth cap is uncalibrated, the native border needs a restart — and a note
     * two scrolls away from the thing it is about is a note nobody reads before deciding.
     */
    private static VBox settingsPage(javafx.scene.Node... children) {
        VBox page = new VBox(10);
        page.getStyleClass().add("es-settings-page");
        page.getChildren().addAll(children);
        return page;
    }

    /**
     * The sidebar and the detail pane — macOS System Settings' arrangement.
     *
     * <h2>Why a category list beat one long column</h2>
     *
     * The old panel put thirteen headings in a single scroll, so finding "wallpaper" meant going
     * past the pointer, the severity floor and every subsystem checkbox — and there was no scroll
     * position from which a player could see what the panel even <em>covered</em>. A sidebar answers
     * "what can I change" before "change it", which is the question somebody opening Settings is
     * actually asking.
     *
     * <p>⚠ The categories come from the page map and nothing else. A hand-written list of names
     * beside a map of pages is two things to keep in step, and the failure is a sidebar entry that
     * selects nothing.
     *
     * <p>The search field filters the sidebar by name. Deliberately not a full-text search over the
     * prose: this panel's help text is long and argumentative, so matching on it would return every
     * category for words like "default" or "window" — a filter that never narrows is worse than no
     * filter, because it looks like it is working.
     */
    private static Region settingsBody(java.util.LinkedHashMap<String, javafx.scene.Node> pages) {
        VBox sidebar = new VBox(2);
        sidebar.getStyleClass().add("es-settings-sidebar");
        sidebar.setMinWidth(170);
        sidebar.setPrefWidth(170);
        sidebar.setMaxWidth(170);

        ScrollPane detail = new ScrollPane();
        detail.setFitToWidth(true);
        detail.getStyleClass().add("es-settings-detail");
        HBox.setHgrow(detail, Priority.ALWAYS);
        // ⚠ An explicit MAX height, not just Hgrow/Vgrow.
        //
        // A layout constraint only grows a child up to its maximum, and for a Control the computed
        // maximum is not the unbounded value a Pane reports — so the detail pane stopped at its
        // preferred height and the category's content sat in the top third of the window with dead
        // space under it. Vgrow was set and looked correct; the clamp was one level down.
        detail.setMaxHeight(Double.MAX_VALUE);
        // And the PAGE fills the pane, not just the pane the window. Without this the category's
        // column is only as tall as its own text, so a short category leaves the right-hand side
        // ending partway down while the sidebar beside it runs to the bottom. Ignored, correctly,
        // whenever a category is taller than the window — that one scrolls as before.
        detail.setFitToHeight(true);

        TextField search = new TextField();
        search.setPromptText(t("settings.search", "Search"));
        search.getStyleClass().add("es-settings-search");

        String[] selected = {pages.keySet().iterator().next()};
        Runnable[] rebuild = new Runnable[1];

        rebuild[0] = () -> {
            String needle =
                    search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            sidebar.getChildren().clear();
            sidebar.getChildren().add(search);
            for (String name : pages.keySet()) {
                if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                Label row = new Label(name);
                row.getStyleClass().add(name.equals(selected[0]) ? "es-settings-row-on" : "es-settings-row");
                row.setMaxWidth(Double.MAX_VALUE);
                io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared()
                        .clickable(row);
                row.setOnMouseClicked(event -> {
                    selected[0] = name;
                    rebuild[0].run();
                });
                sidebar.getChildren().add(row);
            }
            detail.setContent(pages.get(selected[0]));
        };
        search.textProperty().addListener((o, was, now) -> rebuild[0].run());
        rebuild[0].run();

        HBox body = new HBox(10, sidebar, detail);
        body.setFillHeight(true);
        body.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(body, Priority.ALWAYS);
        // The sidebar's hairline is the divider between the two halves, so it has to run the full
        // height of the panel — a rule that stops where the last category happens to fall reads as
        // the panel having ended there.
        sidebar.setMaxHeight(Double.MAX_VALUE);
        return body;
    }

    /**
     * Who the appearance settings on this page belong to.
     *
     * <h2>Why this line has to exist</h2>
     *
     * Appearance became per character on 2026-07-28, and the Settings window is reached from two
     * places that look identical: the login screen, where it edits the machine's look, and the deck,
     * where it edits the loaded character's. Without a sentence saying which, a player who re-themes
     * from the menu and then finds their character unchanged has been told nothing at all — and the
     * conclusion they will draw is that the setting is broken, not that it is scoped.
     *
     * <p>⚠ Not every control on these pages is per character. The system window border, the window
     * size, text size and reduce-motion are machine-wide, and each says so in its own note. This
     * line is deliberately about the ones that moved.
     */
    private static Label scopeNote(GameSession session) {
        Label note = wrapped(
                session == null
                        ? "These settings belong to this machine — the menu, and the next character you "
                                + "create starts from them. Load a character and this page edits that "
                                + "character's look instead."
                        : "These settings belong to " + session.handle() + ". Each character keeps its own "
                                + "look, so changing them here leaves your other characters alone.");
        note.getStyleClass().add("es-settings-scope");
        return note;
    }

    /**
     * Package-private so {@link SetupWizardView} validates a handle with THIS rule rather than a
     * second one that drifts from it. A setup assistant that accepted a name the Operator page
     * would later reject is worse than having no assistant.
     */
    static String validateHandle(String handle) {
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

    static VBox panel(String title) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        Label heading = new Label(title);
        heading.getStyleClass().add("es-panel-title");
        root.getChildren().add(heading);
        return root;
    }

    /**
     * A translatable interface string: the key, and the English the code already carries.
     *
     * <h2>⚠ English stays HERE, in the source, and is the fallback</h2>
     *
     * The alternative — moving these sentences into {@code ui_en.properties} and leaving a bare key
     * at the call site — was rejected. Half of them explain <em>why</em> a setting is off by default,
     * and that reasoning belongs where somebody changing the setting will read it; a file of
     * disembodied prose keyed {@code settings.desk.freeDrag} is documentation nobody maintains. It
     * would also make every one of these a two-file edit, which is how English and the thing it
     * describes drift apart.
     *
     * <p>So {@code ui} is an <b>overlay</b> bundle ({@code Messages.overlay}): there is no
     * {@code ui_en.properties}, a translation supplies only what it has translated, and anything it
     * has not reached keeps the English written right here.
     *
     * <p>⚠ Resolved at <b>call time</b>, never cached — {@code Text.current()} changes when the
     * player changes the setting, and these are built afresh every time the panel is opened.
     */
    static String t(String key, String english) {
        return io.github.stoicswe.eyeandsickle.client.i18n.Text.current().ui(key, english);
    }

    static Label wrapped(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    static Label secondary(String text) {
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

    static void styleByOutcome(Label label, GameSession.Outcome outcome) {
        label.getStyleClass().removeAll("es-state-refused", "es-state-unreachable");
        if (outcome.status() == GameSession.Outcome.UNAVAILABLE || outcome.status() == GameSession.Outcome.TEMPFAIL) {
            label.getStyleClass().add("es-state-unreachable");
        } else if (!outcome.succeeded()) {
            label.getStyleClass().add("es-state-refused");
        }
    }
}
