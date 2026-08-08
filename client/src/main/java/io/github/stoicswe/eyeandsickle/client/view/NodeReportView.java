package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.NodeReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The intelligence file on one machine — everything learned about it, and how old each part is.
 *
 * <h2>⚠ Every line carries its own age, and that is the whole reason this is worth persisting</h2>
 *
 * A report was session-only at first, because the cycle-load line is a <b>snapshot</b> and a stored
 * one would hand a returning player last Tuesday's figure with today's confidence. Dating each
 * finding individually answers that instead of throwing the intelligence away: a firewall reading
 * from this morning and a vault estimate from last week appear as exactly that.
 *
 * <p>One timestamp for the file would not do it. A cheap firewall re-check would touch
 * {@code updatedAt} and make every older finding look re-measured — stale intelligence wearing a
 * fresh date, which is worse than no intelligence at all.
 *
 * <h2>Absent is said, never rendered as zero</h2>
 *
 * A rung nobody has paid for prints as "not scanned for". A panel that showed {@code 0} would be
 * telling the player there is nothing in a vault it has never opened, about a machine they are
 * deciding whether to rob.
 */
public final class NodeReportView {

    private NodeReportView() {}

    /** Builds the panel for one machine. Repaints itself, so a scan finishing fills it in. */
    public static Region create(GameSession session, String address) {
        VBox root = new VBox(UiTokens.SPACE_3);
        root.getStyleClass().addAll("es-report", "es-body-pad");
        root.setMinWidth(620);

        Label title = new Label(Views.t("ui.node-report.node-report", "NODE REPORT"));
        title.getStyleClass().add("es-panel-title");
        Label target = new Label(address);
        target.getStyleClass().addAll("es-report-target", "es-mono");

        VBox body = new VBox(UiTokens.SPACE_1);
        Runnable repaint = () -> paint(body, session.nodeReport(address).orElse(null), session.now());
        repaint.run();

        root.getChildren().addAll(title, target, body);

        // ⚠ On the clock as well as on session change. Every line here is an age, and an age is
        // derived from the wall clock rather than from game state — a panel repainted only on data
        // change would freeze every "4m ago" until something unrelated happened to the save.
        AutoCloseable onSession = session.onChange(s -> repaint.run());
        AutoCloseable clock = Pulse.shared().every(1_000, repaint);
        Views.releaseOnDetach(root, onSession, clock);
        return Views.scrollable(root);
    }

    private static void paint(VBox into, NodeReport report, Instant now) {
        into.getChildren().clear();
        if (report == null || !report.any()) {
            into.getChildren().add(Ui.micro("Nothing on file. Port-scan this machine and the findings collect here."));
            return;
        }

        into.getChildren().add(micro("opened     " + stamp(report.createdAt())));
        into.getChildren()
                .add(micro("updated    " + stamp(report.updatedAt()) + "   (" + age(report.updatedAt(), now) + ")"));
        into.getChildren()
                .add(micro("scans      " + report.scans()
                        + (report.detections() > 0
                                ? "   ·  noticed you " + report.detections()
                                        + (report.detections() == 1 ? " time" : " times")
                                : "   ·  never noticed you")));
        into.getChildren().add(micro("complete   " + report.known() + " of " + report.total() + " findings"));
        into.getChildren().add(new Label(" "));

        // ⚠ First, because it is the cheapest rung and because it is the row a player reads to know
        // WHICH machine this file is about. Both halves share one row: they are one finding, and one
        // unpaid rung should read as one gap rather than two.
        into.getChildren().add(finding(report, PortScanTarget.IDENTITY, now, identityOf(report)));
        into.getChildren()
                .add(finding(
                        report,
                        PortScanTarget.FIREWALL,
                        now,
                        report.firewallTier() < 0 ? null : "tier " + report.firewallTier()));
        into.getChildren()
                .add(finding(
                        report, PortScanTarget.OS_VERSION, now, report.osName().isBlank() ? null : report.osName()));
        into.getChildren()
                .add(finding(
                        report,
                        PortScanTarget.CYCLE_CAPABILITY,
                        now,
                        report.cyclesTotal() < 0 ? null : report.cyclesTotal() + " cycles"));
        // ⚠ The one whose age matters most. It was true at the instant it was taken and is a guess
        // now, which is exactly what the age beside it is for.
        into.getChildren()
                .add(finding(
                        report,
                        PortScanTarget.CYCLE_LOAD,
                        now,
                        report.cyclesUsed() < 0
                                ? null
                                : report.cyclesUsed() + " used · " + report.cyclesFree() + " free"));
        into.getChildren()
                .add(finding(
                        report,
                        PortScanTarget.DOWNLOADS,
                        now,
                        report.downloadsBytes() < 0
                                ? null
                                : String.format(Locale.ROOT, "%.1f MB", report.downloadsBytes() / 1_000_000.0d)));
        into.getChildren()
                .add(finding(
                        report,
                        PortScanTarget.VAULT_HIGH,
                        now,
                        report.vaultHighCount() < 0 ? null : report.vaultHighCount() + " items"));
        into.getChildren()
                .add(finding(
                        report,
                        PortScanTarget.VAULT_MEDIUM,
                        now,
                        report.vaultMediumEstimate() < 0
                                ? null
                                : report.vaultMediumLow() + "-" + report.vaultMediumHigh() + " items  (estimate)"));
    }

    /**
     * The identity finding as one string: {@code operator@machine}, or null when nobody has looked.
     *
     * <p>⚠ Either half may legitimately be missing — a gateway has a name and no account worth
     * speaking of — so a bare {@code who + "@" + where} would render {@code @bold-turing} for a scan
     * that came back with everything there was to have. Same rule and same shape as
     * {@code PortScanView.identityOf}; the two are separate because one reads a live
     * {@code PortScanReport} and the other the stored {@code NodeReport}, and merging them would mean
     * one of the two panels taking a dependency on the other's record type.
     */
    static String identityOf(NodeReport report) {
        String who = report.operatorName() == null ? "" : report.operatorName();
        String where = report.label() == null ? "" : report.label();
        if (who.isEmpty() && where.isEmpty()) {
            return null;
        }
        if (who.isEmpty()) {
            return where;
        }
        return where.isEmpty() ? who : who + "@" + where;
    }

    /** One finding, its value and its age — or a plain statement that nobody has looked. */
    private static Label finding(NodeReport report, PortScanTarget rung, Instant now, String value) {
        String name = (rung.label() + "                    ").substring(0, 20);
        if (value == null) {
            Label absent = new Label(name + "— not scanned for");
            absent.getStyleClass().addAll("es-mono", "es-report-absent");
            return absent;
        }
        Instant when = report.when(rung);
        Label label = new Label(name + value + (when == null ? "" : "   (" + age(when, now) + ")"));
        label.getStyleClass().addAll("es-mono", "es-report-finding");
        return label;
    }

    private static Label micro(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("es-mono", "es-report-meta");
        return label;
    }

    private static String stamp(Instant at) {
        return at == null || at.equals(Instant.EPOCH)
                ? "—"
                : java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(at);
    }

    /** How long ago, in the units a person would use. */
    static String age(Instant at, Instant now) {
        if (at == null || now == null) {
            return "—";
        }
        long seconds = Math.max(0, Duration.between(at, now).getSeconds());
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m ago";
        }
        if (seconds < 86_400) {
            return seconds / 3600 + "h ago";
        }
        return seconds / 86_400 + "d ago";
    }
}
