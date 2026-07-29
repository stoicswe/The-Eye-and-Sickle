package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.teaching.GlossBar;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.ActivityList;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.CoreCage;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.CycleGrid;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Greeble;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Note;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.ProcessTableView;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.SweepPanel;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The allocation pane — where the rig's compute is, cell by cell.
 *
 * <h2>Compute is countable, not a percentage</h2>
 *
 * This view used to be a {@link javafx.scene.control.ProgressBar} and a list of consumers. It is now
 * the 100-cell grid {@code docs/design/ui-design-language.md} §4 calls the signature component, and
 * the change is not cosmetic. A bar at 97% says the rig is nearly full; a hundred cells with three
 * hollow ones says <em>you have three</em>, which is the number the player's next decision actually
 * turns on. {@code docs/design/01-core-resources.md} §1.2 makes cycles an integer resource — one
 * cell is one cycle, at every rig size, so the grid grows as the rig does rather than rescaling.
 *
 * <h2>The rig readout must add up, and must show it when it does not</h2>
 *
 * {@code docs/design/04-mining.md} §3.1 makes catching a hidden miner a matter of noticing that the
 * numbers do not reconcile. That is only a skill if the numbers normally do — so nothing here is
 * rounded, smoothed or bucketed, and {@link ComputeBudget#unaccountedFor()} gets its own slice in
 * the grid rather than a line of text underneath it. Cycles being spent by something the rig cannot
 * name are drawn as blinking alarm cells, and the note beside them says what it is costing.
 *
 * <p>The slice is never synthesised. If the figure is zero the slice does not exist, so its
 * appearance always means something is wrong.
 */
public final class RigMonitorView {

    /**
     * Display order for the legend and the grid.
     *
     * <p>Income first, then overhead, then what is not currently yours. §2.1 wants the palette to
     * encode income versus overhead so a panel is readable before it is read; ordering the slices
     * the same way means the amber cells cluster at the top-left of the grid and a rig that has
     * quietly become all overhead looks wrong from across the room.
     */
    private static final List<ComputeConsumer> ORDER = List.of(
            ComputeConsumer.SELF_MINING,
            ComputeConsumer.CONTROL_CHANNEL,
            ComputeConsumer.BOT_FRAME,
            ComputeConsumer.DEPLOYED_MINER,
            ComputeConsumer.DEFENSIVE_ARRAY,
            ComputeConsumer.ACTIVE_TOOL,
            ComputeConsumer.RELAY_HOP);

    private RigMonitorView() {}

    public static Region create(GameSession session) {
        return create(session, null, null);
    }

    /**
     * With a term database attached, the headings gloss themselves on hover and on focus.
     *
     * <p>Tier 1 of the teaching layer, reaching the surface a player looks at most. COMPUTE is on
     * screen at all times, so it is the word most worth explaining without being asked.
     */
    public static Region create(GameSession session, TermDatabase terms, ClientProfile profile) {
        VBox root = new VBox(UiTokens.SPACE_6);
        root.getStyleClass().add("es-body-pad");

        Label claimed = new Label("0");
        claimed.getStyleClass().add("es-display");
        Label claimedUnit = Ui.label("/ 100 cycles claimed");
        claimedUnit.getStyleClass().add("es-display-unit");
        if (terms != null && profile != null) {
            GlossBar.attach(claimedUnit, "compute", terms, profile);
        }

        KeyValue free = KeyValue.of("Free", "0").live();
        KeyValue recovering = KeyValue.of("Recovering", "0");
        FlowPane head = new FlowPane(UiTokens.SPACE_6, UiTokens.SPACE_2);
        head.setAlignment(Pos.BASELINE_LEFT);
        head.getChildren().addAll(Ui.row(UiTokens.SPACE_2, claimed, claimedUnit), free, recovering);

        Greeble greeble = new Greeble(82);
        CycleGrid grid = new CycleGrid();
        // The cutaway sits in the space the capped cell field leaves beside it. It is a second view
        // of the same number — a post is amber for exactly as long as its bank is self-mining.
        CoreCage cage = new CoreCage();
        grid.setAside(cage);

        KeyValue rate = KeyValue.of("Return rate", "0.0000 EC/S").live();
        KeyValue hourly = KeyValue.of("Projected", "0.00 EC/HR").live();
        Label held = Ui.micro("");
        held.getStyleClass().add("es-buffer-text");
        SweepPanel working = new SweepPanel(Ui.row(UiTokens.SPACE_5, rate, Ui.spacer(), hourly), held);

        // The Activity Monitor half of this panel: what the rig is doing, with a countdown.
        // Sits between the allocation grid and the income summary because that is the reading
        // order of the question — where are my cycles, what are they doing, what is it earning.
        ActivityList activity = new ActivityList(session::tasks);

        VBox notes = new VBox(UiTokens.SPACE_2);

        // ---------------------------------------------------------------- tabs
        //
        // Six views, five of one machine and one of the other. Overview is the panel that was
        // already here; the next four are the process table, which is what makes docs/design/04
        // §3.1's "manual audit" an act rather than a sentence; ABOUT steps out of the fiction and
        // reports the player's real hardware (see RigTab.ABOUT and SystemReport).
        RigTab[] tab = {RigTab.OVERVIEW};
        ProcessTableView table = new ProcessTableView();
        VBox overview = new VBox(UiTokens.SPACE_6, greeble, grid, activity, working, notes);
        VBox.setVgrow(grid, Priority.SOMETIMES);

        Label tableNote = Ui.small(
                "Everything running on this rig. Nothing here is labelled hostile — right-click a row "
                        + "to kill it, sort a column to compare. A process that is not yours has to "
                        + "look like one that is, and looking like one is not the same as being one.");
        tableNote.setWrapText(true);

        // The history strip sits between the tabs and the table: a table answers "what is running
        // now" and cannot answer "was that always there", which is the question the manual audit
        // actually turns on. See RigHistory.
        RigHistory history = new RigHistory();
        table.setOnSample(history::sample);

        VBox tableSide = new VBox(UiTokens.SPACE_3, history, tableNote, table);
        VBox.setVgrow(tableSide, Priority.ALWAYS);

        // Built once. Nothing on it changes while the client is running, so it is deliberately
        // absent from `refresh` below — a panel that re-read the host's memory every session tick
        // would be doing work to print the same number.
        Region about = RigAbout.create();

        HBox tabs = Ui.row(UiTokens.SPACE_3);
        tabs.getStyleClass().add("es-breach-picker");
        List<BreachView.Chip> tabChips = new ArrayList<>();
        Runnable[] applyTab = new Runnable[1];
        for (RigTab value : RigTab.values()) {
            BreachView.Chip chip = new BreachView.Chip(value.control(RigTab.OVERVIEW), "es-breach-chip-quiet");
            chip.setAccessibleText("Show the " + value.label().toLowerCase(Locale.ROOT) + " view of the rig.");
            chip.onInvoke(() -> {
                tab[0] = value;
                applyTab[0].run();
            });
            tabChips.add(chip);
            tabs.getChildren().add(chip);
        }

        applyTab[0] = () -> {
            for (int i = 0; i < tabChips.size(); i++) {
                RigTab value = RigTab.values()[i];
                BreachView.Chip chip = tabChips.get(i);
                chip.setText(Ui.upper(value.control(tab[0])));
                chip.getStyleClass().remove("es-breach-chip-loud");
                if (value == tab[0]) {
                    chip.getStyleClass().add("es-breach-chip-loud");
                }
            }
            // ⚠ Asked positively, one question per panel. This was `isOverview()` and `!isOverview()`
            // while there were only two kinds of tab, and a third kind turns the negation into a
            // silent bug: ABOUT is not the overview, so the process table would have rendered under
            // the mascot. See RigTab.isTable.
            visible(overview, tab[0].isOverview());
            visible(tableSide, tab[0].isTable());
            visible(about, tab[0] == RigTab.ABOUT);
            history.show(tab[0]);
            if (tab[0].isTable()) {
                table.setColumns(tab[0].columns());
            }
        };

        // ⚠ No inline strip. A kill or a restart is answered by the rig's own log — the rules write
        // a line for both the success and the refusal — and the notification system carries it from
        // there. Success matters as much as failure here: killing a row makes it vanish from a table
        // of three dozen similar rows, and a player who is not certain they clicked the right one
        // needs the engine to say what went.
        table.bind(session::processes);
        table.setOnKill(process -> session.killProcess(process.processId()));
        table.setOnRestart(process -> session.restartProcess(process.processId()));

        root.getChildren().addAll(head, tabs, overview, tableSide, about);

        Runnable refresh = () -> {
            ComputeBudget budget = session.computeBudget();
            long total = budget.total().cycles();
            long available = budget.available().cycles();

            claimed.setText(String.valueOf(total - available));
            claimedUnit.setText(Ui.upper("/ " + total + " cycles claimed"));
            free.set(String.valueOf(available));
            recovering.set(String.valueOf(budget.recovering().cycles()));

            grid.show(slices(budget, session.miningChain()));
            cage.show(session.mining().selfMiningCycles(), total, session.personalHeat());
            activity.refresh();

            RigStatus status = RigStatus.of(session);
            rate.set(status.incomePerSecond() + " EC/S");
            hourly.set(status.incomePerHour() + " EC/HR");
            // The sweep runs only while cycles are genuinely returning. §4 restricts the component
            // to work actually in progress, and a permanently-sweeping panel is a spinner.
            working.setWorking(budget.recovering().cycles() > 0);
            held.setText(Ui.upper(budget.recovering().cycles() + " cycles held · rig at "
                    + Math.round(status.load() * 100) + "% load"));

            notes.getChildren().setAll(notesFor(session, budget, status));

            // ⚠ Deliberately NOT read here. The table runs its own five-second clock (see
            // ProcessTableView.bind) because the figures advance whether or not the game does — an
            // idle rig fires no session change at all, and a table that froze the moment the player
            // stopped doing anything would be stale exactly when they were reading it.
        };

        applyTab[0].run();
        refresh.run();
        session.onChange(s -> refresh.run());

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }

    /** Shows or hides a node and takes it out of the layout with it. */
    private static void visible(javafx.scene.Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }

    /**
     * Turns the budget into grid slices.
     *
     * <p>Recovering cycles are pulled out of their consumer and pooled, because "returning under the
     * Thermal Budget curve" is a state the player can act on and the consumer they came from is not.
     * Everything else keeps its owner.
     */
    static List<CycleGrid.Slice> slices(ComputeBudget budget, MiningSnapshot mining) {
        Map<ComputeConsumer, Long> active = new EnumMap<>(ComputeConsumer.class);
        long recovering = 0;
        for (ComputeAllocation allocation : budget.allocations()) {
            if (allocation.isRecovering()) {
                recovering += allocation.cycles().cycles();
            } else {
                active.merge(allocation.consumer(), allocation.cycles().cycles(), Long::sum);
            }
        }

        List<CycleGrid.Slice> slices = new ArrayList<>();
        for (ComputeConsumer consumer : ORDER) {
            long cycles = active.getOrDefault(consumer, 0L);
            if (cycles > 0) {
                slices.add(new CycleGrid.Slice(
                        owner(consumer), (int) cycles, label(consumer), detail(consumer, mining)));
            }
        }
        if (recovering > 0) {
            slices.add(new CycleGrid.Slice(
                    CycleGrid.Owner.RECOVERING, (int) recovering, "Thermal recovery", "returning"));
        }

        // Capacity that is gone with nothing to attribute it to.
        //
        // ⚠ Drawn as HIDDEN — dark, inert, and absent from the legend — rather than as the blinking
        // UNKNOWN alarm it used to be. The rig only reports a parasite it has AUDITED
        // (ComputeRules.snapshot omits the rest), so this gap is, in the fiction, capacity nobody
        // knows is missing. Painting it in alarm red with a label reading "Unaccounted" was the
        // readout announcing a theft the player has not detected, which hands them free the answer
        // docs/design/04-mining.md §3.2 sells the whole scan ladder for.
        //
        // What survives is §3.1's actual mechanic: claimed + recovering + free comes to less than
        // the "/ 100 cycles" the headline states, and there is a hole in the grid where the
        // difference is. A player who counts finds it. Nobody is told.
        long unknown = budget.unaccountedFor().cycles();
        if (unknown > 0) {
            slices.add(new CycleGrid.Slice(CycleGrid.Owner.HIDDEN, (int) unknown, "", ""));
        }

        long available = budget.available().cycles();
        if (available > 0) {
            slices.add(new CycleGrid.Slice(
                    CycleGrid.Owner.FREE, (int) available, "Unallocated", "free"));
        }
        return slices;
    }

    private static CycleGrid.Owner owner(ComputeConsumer consumer) {
        return switch (consumer) {
            case SELF_MINING -> CycleGrid.Owner.SELF_MINING;
            case CONTROL_CHANNEL -> CycleGrid.Owner.CONTROL_CHANNEL;
            case BOT_FRAME -> CycleGrid.Owner.BOT_FRAME;
            // I6: a deployed miner consumes the HOST's compute. When one of these shows up in your
            // own budget you are the host, which is a hostile state, not an income line.
            case DEPLOYED_MINER -> CycleGrid.Owner.UNKNOWN;
            case DEFENSIVE_ARRAY -> CycleGrid.Owner.DETECTION;
            case ACTIVE_TOOL -> CycleGrid.Owner.ACTIVE_TOOL;
            case RELAY_HOP -> CycleGrid.Owner.RELAY_HOP;
            // Shares ACTIVE_TOOL's cell colour: from the grid's point of view a held shell is the
            // player's own work reaching outward, which is what that owner already means. It keeps
            // its own LABEL below, so the readout still names it — the grid is a shape, the list is
            // the place a player finds out what is holding what.
            case SHELL_SESSION -> CycleGrid.Owner.ACTIVE_TOOL;
        };
    }

    private static String label(ComputeConsumer consumer) {
        return switch (consumer) {
            case SELF_MINING -> "Self-mining";
            case CONTROL_CHANNEL -> "Control channels";
            case BOT_FRAME -> "Bot frames";
            case DEPLOYED_MINER -> "Foreign miner";
            case DEFENSIVE_ARRAY -> "Defensive array";
            case ACTIVE_TOOL -> "Equipped tools";
            case SHELL_SESSION -> "Shell sessions";
            case RELAY_HOP -> "Relay hops";
        };
    }

    /**
     * The right-hand note on an allocation row.
     *
     * <p>⚠ The mining figure is the engine's <b>expectation</b>, read off the port rather than
     * multiplied out here. Self-mining is a Poisson process since 2026-07-27 and its rate depends on
     * the mode — a solo miner keeps the fee a pooled one pays — so a view that did its own
     * arithmetic would print the wrong number for half the players and look authoritative doing it.
     */
    private static String detail(ComputeConsumer consumer, MiningSnapshot mining) {
        if (consumer == ComputeConsumer.SELF_MINING) {
            return String.format(Locale.ROOT, "~%.1f EC/hr %s",
                    mining.expectedMinorUnitsPerHour() / 100.0d,
                    mining.mode() == MiningMode.SOLO ? "solo" : "pooled");
        }
        return consumer == ComputeConsumer.DEPLOYED_MINER ? "on your rig" : "held";
    }

    /**
     * The notes under the grid.
     *
     * <p>§6: consequence, not condition, and §2.1 allows at most two alarms on a screen — so the two
     * loss states below are mutually exclusive with everything else and are emitted first. Nothing
     * here restates a figure the grid already shows; a note that could be a {@link KeyValue} should
     * be one.
     */
    private static List<Region> notesFor(GameSession session, ComputeBudget budget, RigStatus status) {
        List<Region> notes = new ArrayList<>();

        // ⚠ THERE IS DELIBERATELY NO NOTE FOR AN UNRECONCILED LEDGER.
        //
        // There was one, and it read "N cycles are being spent by something not in this list —
        // `scan --full` is the only thing that will name it". It was accurate, well-meant, and it
        // gave away the entire audit ladder: a player who has run no scan was told both that they
        // were being robbed and exactly how much for. docs/design/04-mining.md §3.1 asks the player
        // to NOTICE that the numbers do not add up; a note that points at the gap is not noticing,
        // it is being told, and once the game tells you there is nothing left for §3.2 to sell.
        //
        // The gap is still there, in the headline arithmetic and as a hole in the grid. What is gone
        // is the commentary. A parasite the player HAS audited gets a note below — naming something
        // already known is consequence, not revelation.

        long foreign = 0L;
        for (var allocation : budget.allocations()) {
            if (allocation.consumer() == ComputeConsumer.DEPLOYED_MINER && !allocation.isRecovering()) {
                foreign += allocation.cycles().cycles();
            }
        }
        if (foreign > 0) {
            notes.add(Note.loss(
                    foreign + (foreign == 1 ? " cycle is" : " cycles are") + " running somebody else's work.",
                    "You have found it; it is still there. `crack` takes its buffer and the cycles "
                            + "back, and cracking on your own rig costs no heat."));
        }

        if (status.bufferCapMinorUnits() > 0 && status.bufferFill() >= 1.0) {
            notes.add(Note.loss(
                    "Deployed buffers are full.",
                    "Everything they mine from here is discarded until you collect."));
        }

        long available = budget.available().cycles();
        if (available < 35 && notes.size() < 2) {
            notes.add(Note.consequence(
                    available + (available == 1 ? " cycle free." : " cycles free."),
                    "A thorough scan needs 35. Pull cycles off self-mining to run one, and the "
                            + "block in progress is forfeit."));
        }

        if (status.selfMiningCycles() == 0 && notes.size() < 3) {
            notes.add(Note.consequence(
                    "Nothing is self-mining.",
                    "Self-mining is the income floor: immune to seizure, zero heat, and the only "
                            + "earning that costs you nothing but cycles. It stops the moment you close "
                            + "the client."));
        }
        return notes;
    }
}
