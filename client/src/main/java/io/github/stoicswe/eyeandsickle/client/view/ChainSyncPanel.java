package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.CellMeter;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainSync;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.time.Duration;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * {@code SYNCHRONIZING} — the chain catching up on what it did while the client was closed.
 *
 * <h2>Why a load needs a screen at all</h2>
 *
 * The chain runs whether or not this client does ({@code docs/design/04-mining.md} §1.3d), so a
 * character opened after four days away arrives at a height several hundred blocks past the one they
 * left. Applying that silently would replace one wrong impression with another: a player who left at
 * 4 412 and opened at 4 463 with no explanation has no way to tell a chain that ran without them from
 * a save that had been tampered with — and §3.1 spends the whole game training them to treat exactly
 * that kind of unexplained jump as evidence.
 *
 * <p>So the fill is <b>shown</b>. What it reports is what actually happened: how many blocks, over
 * what span, how many of them the rig was still hashing for, how many retargets closed, and how many
 * of the player's own transactions were mined while they were gone.
 *
 * <h2>⚠ The replay is theatre over work that is already finished, and the timing says so</h2>
 *
 * {@code ChainRules.sync} ran to completion inside {@code resume()} before this panel existed. The
 * meter is therefore paced by a <b>fixed step count over a fixed duration</b> rather than by blocks:
 * 51 blocks and 5 100 blocks take the same {@link #STEPS} steps, because the honest thing a progress
 * bar can report here is "how far through showing you this am I", and pretending to be watching work
 * happen would be inventing a wait the player never had.
 *
 * <h2>⚠ Three design-language constraints, each of which the obvious implementation violates</h2>
 *
 * <ul>
 *   <li><b>{@code Pulse.animate}, never {@code AnimationTimer}.</b> §5.1 rations continuous ramps to
 *       two files by name and {@code UiContractTest} asserts it. {@code animate} is also the right
 *       <em>kind</em>: this is decoration over a completed fact, so under reduced motion it fires
 *       exactly once — and {@link #render} reads {@link Pulse#reducedMotion()} and paints the
 *       finished state on that single call, rather than freezing a bar at 2%.
 *   <li><b>A {@link CellMeter}, never a continuous bar.</b> §4, and this is a measurement, so §9.3's
 *       rounded-corner opt-in must not reach it either — a cell with a soft corner reads as a
 *       smaller cell, and discrete meters exist to be counted.
 *   <li><b>Its own strip, not a modal.</b> The deck draws its own window manager and has no modal
 *       layer; more to the point, a player who came back to check one figure should not have to
 *       dismiss a dialog to reach it. The panel sits at the top of the CHAIN tab and collapses to
 *       nothing when there is nothing to report.
 * </ul>
 */
public final class ChainSyncPanel {

    private ChainSyncPanel() {}

    /**
     * How many steps the replay takes.
     *
     * <p>Matched to the meter's cell count so every step lights exactly one cell — a step count that
     * did not divide evenly would light two cells on some ticks and none on others, which reads as a
     * stutter rather than as a fill.
     */
    public static final int STEPS = 32;

    /** How long the whole replay runs, whatever it covers. */
    public static final double REPLAY_MS = 1800;

    /**
     * Builds the panel, or an empty one when the chain had nothing to catch up.
     *
     * @param sync what the load filled in
     * @param onDone run when the replay finishes — the LEDGER window refreshes its cards there, so
     *     the block strip behind this panel is not repainted mid-fill under the player's eyes
     * @return the panel and its subscription, which the caller must close on detach
     */
    public static Built build(ChainSync sync, Runnable onDone) {
        VBox root = new VBox(UiTokens.SPACE_3);
        root.getStyleClass().add("es-sync");
        if (!sync.any()) {
            // Unmanaged as well as invisible: a hidden-but-managed box claims a row of the column's
            // height on the overwhelming majority of loads, where there is nothing to synchronise.
            root.setVisible(false);
            root.setManaged(false);
            return new Built(root, () -> {});
        }

        Label title = new Label(Views.t("ui.chain-sync-panel.synchronizing", "SYNCHRONIZING"));
        title.getStyleClass().addAll("es-sync-title", "es-mono");

        Label heights = new Label();
        heights.getStyleClass().addAll("es-sync-heights", "es-numeric");

        CellMeter meter = new CellMeter(STEPS);

        Label caption = new Label();
        caption.getStyleClass().addAll("es-sync-caption", "es-mono");
        caption.setWrapText(true);

        VBox summary = new VBox(UiTokens.SPACE_2);
        summary.getStyleClass().add("es-sync-summary");
        summary.setVisible(false);
        summary.setManaged(false);

        root.getChildren().addAll(title, heights, meter, caption, summary);

        int[] step = {0};
        boolean[] finished = {false};
        Runnable[] release = {() -> {}};
        Runnable render = () -> {
            // ⚠ Reduced motion gets the finished state on the single call animate() makes, not a
            // meter stuck at one cell. Suppressing the animation must never suppress the report.
            int at = Pulse.shared().reducedMotion() ? STEPS : Math.min(STEPS, step[0]++);
            meter.set(at);
            heights.setText(String.format(Locale.ROOT, "%,d → %,d", sync.fromHeight(), sync.heightAt(at, STEPS)));
            caption.setText(caption(sync, at));
            if (at >= STEPS && !finished[0]) {
                finished[0] = true;
                fill(summary, sync);
                summary.setVisible(true);
                summary.setManaged(true);
                release[0].run();
                if (onDone != null) {
                    onDone.run();
                }
            }
        };

        AutoCloseable handle = Pulse.shared().animate(REPLAY_MS / STEPS, render);
        release[0] = () -> {
            try {
                handle.close();
            } catch (Exception impossible) {
                // Pulse's handle only removes a list entry. Nothing here can fail, and a sync
                // report is not a reason to take the window down if something one day does.
            }
        };
        return new Built(root, release[0]);
    }

    /** The line under the meter: what is being replayed, then what it came to. */
    private static String caption(ChainSync sync, int at) {
        if (at < STEPS) {
            return String.format(
                    Locale.ROOT,
                    "%,d blocks over %s · replaying the chain's own record",
                    sync.blocks(),
                    human(Duration.ofSeconds(sync.awaySeconds())));
        }
        return String.format(
                Locale.ROOT,
                "%,d blocks over %s · difficulty %.2f → %.2f · %d retarget%s",
                sync.blocks(),
                human(Duration.ofSeconds(sync.awaySeconds())),
                sync.difficultyBefore(),
                sync.difficultyAfter(),
                sync.retargets(),
                sync.retargets() == 1 ? "" : "s");
    }

    /**
     * The report the replay resolves into.
     *
     * <h2>⚠ The spin-down cap is stated whenever it bit, and stating it is not optional</h2>
     *
     * A player away a week and paid for four hours has no way to distinguish that from a bug. This is
     * the same reasoning that made the old resume log say "self-mining earned nothing while away: it
     * is online-only" in as many words — silent behaviour and broken behaviour look identical from
     * outside, and the invariant is the more surprising of the two.
     */
    private static void fill(VBox summary, ChainSync sync) {
        summary.getChildren().clear();
        if (sync.blocksWon() > 0) {
            summary.getChildren()
                    .add(line(
                            sync.blocksWon() == 1
                                    ? "1 block is yours — found after logout, before the rig spun down."
                                    : sync.blocksWon() + " blocks are yours — found after logout, before the "
                                            + "rig spun down.",
                            "es-sync-win"));
        }
        if (sync.poolBlocks() > 0) {
            summary.getChildren()
                    .add(line(
                            "Your pool found " + sync.poolBlocks()
                                    + (sync.poolBlocks() == 1 ? " block" : " blocks")
                                    + " while your rig was still contributing.",
                            "es-sync-note"));
        }
        if (sync.creditedWei().signum() > 0) {
            summary.getChildren()
                    .add(line(
                            Ethecoin.format(sync.creditedWei()) + " settled — subsidy and fees together.",
                            "es-sync-win"));
        }
        if (sync.transactionsConfirmed() > 0) {
            // ⚠ Not income, and the wording keeps that clear. The value moved when the row was
            // written; confirmation only stamps it with the height that carried it. A transaction
            // left unconfirmed across a four-day absence would be the lie.
            summary.getChildren()
                    .add(line(
                            sync.transactionsConfirmed() == 1
                                    ? "1 of your transactions was mined while you were away."
                                    : sync.transactionsConfirmed()
                                            + " of your transactions were mined while you were away.",
                            "es-sync-note"));
        }
        if (sync.capped()) {
            summary.getChildren()
                    .add(line(
                            "Your rig ran for " + human(Duration.ofSeconds(sync.minedSeconds()))
                                    + " after logout and then stopped. The other "
                                    + String.format(Locale.ROOT, "%,d", sync.uncontestedBlocks())
                                    + " blocks were mined without it.",
                            "es-sync-note"));
        }
        if (sync.truncated()) {
            summary.getChildren()
                    .add(line(
                            "The fill stopped at its block limit — the chain is still behind. It will "
                                    + "continue catching up on the next load.",
                            "es-sync-warn"));
        }
        if (summary.getChildren().isEmpty()) {
            summary.getChildren()
                    .add(line("Nothing of yours was in any of it. The chain simply kept going.", "es-sync-note"));
        }
    }

    private static Label line(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll("es-mono", styleClass);
        return label;
    }

    /** A duration a person would say out loud. Hours and minutes; days once there are days. */
    static String human(Duration span) {
        long minutes = Math.max(0, span.toMinutes());
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            long rest = minutes % 60;
            return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
        }
        long days = hours / 24;
        long rest = hours % 24;
        return rest == 0 ? days + "d" : days + "d " + rest + "h";
    }

    /**
     * The panel and the one thing the caller has to remember.
     *
     * <p>{@code release} is idempotent and is also called by the panel itself when the replay
     * finishes, so a window closed mid-fill and a window closed after it are the same path.
     */
    public record Built(VBox node, Runnable release) {}

    /** A heading row for the panel, matching the strip headings around it. */
    static HBox heading(String text) {
        Label label = new Label(Ui.upper(text));
        label.getStyleClass().add("es-panel-title");
        HBox row = new HBox(UiTokens.SPACE_3, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
