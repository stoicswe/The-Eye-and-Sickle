package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The port scanner — pick what you want to know about a machine, and pay for how loudly you asked.
 *
 * <h2>⚠ Not in the rail, and that is deliberate</h2>
 *
 * Every window in {@code WindowSpec} is a tool the player owns and can open at any time. A port scan
 * is not a place, it is an act performed <em>against a specific machine</em> — opening it with no
 * target would be a window asking "scan what?". So it is reached the way the act is reached: right-
 * click the machine on the map or in the network list. Adding it to the catalogue would put a tool in
 * the rail that is broken every time it is opened from there.
 *
 * <h2>The panel is a price list, and that is the whole design</h2>
 *
 * Seven rows, one per thing you could learn, each showing what it costs in <b>cycles</b>, in
 * <b>seconds</b>, and in <b>the chance the target notices</b>. The third column is the one that makes
 * this a decision rather than arithmetic, so it is shown before committing rather than discovered
 * afterwards — an interface that revealed the risk after the fact would be offering a gamble without
 * saying it was one.
 *
 * <p>⚠ Rows are cumulative. Choosing the sixth answers the first five too, because a scan that
 * reached that far necessarily passed through them. The panel says so rather than letting a player
 * pay twice for something they already have.
 */
public final class PortScanView {

    private PortScanView() {}

    /**
     * Builds the panel for one machine.
     *
     * @param report where a refusal or a confirmation is written — the rules' own words
     */
    public static Region create(GameSession session, String address, java.util.function.Consumer<String> report) {
        VBox root = new VBox(UiTokens.SPACE_3);
        root.getStyleClass().addAll("es-portscan", "es-body-pad");
        root.setMinWidth(700);

        Label title = new Label("PORT SCAN");
        title.getStyleClass().add("es-panel-title");
        Label target = new Label(address);
        target.getStyleClass().addAll("es-portscan-target", "es-mono");

        Label lead = new Label("Choose the deepest thing you want to know. Everything above it comes "
                + "back with it — a scan that reached that far already passed through the rest. "
                + "Going deeper costs cycles, takes longer, and makes it more likely the machine "
                + "notices you looking.");
        lead.setWrapText(true);
        lead.getStyleClass().add("es-portscan-lead");

        VBox ladder = new VBox(UiTokens.SPACE_1);
        VBox findings = new VBox(UiTokens.SPACE_1);
        Runnable[] repaint = new Runnable[1];

        repaint[0] = () -> {
            ladder.getChildren().clear();
            ladder.getChildren().add(header());
            for (PortScanTarget rung : PortScanTarget.values()) {
                ladder.getChildren().add(row(session, address, rung, report, repaint[0]));
            }
            paintFindings(findings, session.portScanReport(address).orElse(null));
        };
        repaint[0].run();

        root.getChildren().addAll(title, target, lead, ladder, heading("WHAT THE LAST SCAN FOUND"), findings);

        // ⚠ Two refreshes, and the second is not optional. A scan is a task with a deadline, so
        // nothing about the save changes while it runs — session.onChange does not fire again until
        // it settles, and the panel would sit showing a stale report with no sign anything was
        // happening. Same lesson the file manager's transfer bar had to learn.
        AutoCloseable onSession = session.onChange(s -> repaint[0].run());
        AutoCloseable clock = Pulse.shared().every(1_000, repaint[0]);
        Views.releaseOnDetach(root, onSession, clock);
        return Views.scrollable(root);
    }

    private static Region header() {
        HBox row = Ui.row(
                UiTokens.SPACE_3,
                cell(Ui.micro("WHAT YOU LEARN"), 300),
                cell(Ui.micro("CYCLES"), 70),
                cell(Ui.micro("TIME"), 70),
                cell(Ui.micro("IT NOTICES"), 90));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * One rung.
     *
     * <p>⚠ The risk figure is styled by band rather than printed bare. A player reading "31%" has to
     * do the work of deciding whether that is a lot; a row that steps from micro-grey through body
     * text to the warning hue has already told them, and the number is still there for anyone who
     * wants it (§4.4 — never colour alone).
     */
    private static Region row(
            GameSession session,
            String address,
            PortScanTarget rung,
            java.util.function.Consumer<String> report,
            Runnable repaint) {
        var quote = session.portScanQuote(address, rung);

        Label what = new Label(rung.label());
        what.getStyleClass().addAll("es-mono", "es-portscan-what");
        Label detail = Ui.micro(rung.detail());

        Label risk = new Label(quote.riskPercent() + "%");
        risk.getStyleClass().addAll("es-mono", riskClass(quote.riskPercent()));

        BreachView.Chip run = new BreachView.Chip("Scan", "es-breach-chip-quiet");
        run.setDisable(!quote.affordable());
        run.setAccessibleText("Scan " + address + " for " + rung.label().toLowerCase(Locale.ROOT)
                + ". " + quote.cycles() + " cycles, about " + quote.seconds() + " seconds, "
                + quote.riskPercent() + " percent chance the machine notices and answers.");
        run.onInvoke(() -> {
            report.accept(session.portScan(address, rung).message());
            repaint.run();
        });

        HBox row = Ui.row(
                UiTokens.SPACE_3,
                cell(new VBox(what, detail), 300),
                cell(new Label(String.valueOf(quote.cycles())), 70),
                cell(new Label(quote.seconds() + "s"), 70),
                cell(risk, 90),
                run);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("es-portscan-row");
        return row;
    }

    /** Three bands. Under a tenth is background noise; over a third is a real bet. */
    private static String riskClass(int percent) {
        if (percent < 12) {
            return "es-portscan-risk-low";
        }
        return percent < 34 ? "es-portscan-risk-mid" : "es-portscan-risk-high";
    }

    /**
     * What the last scan came back with.
     *
     * <h2>⚠ Anything the scan did not reach says so, rather than printing a zero</h2>
     *
     * A panel that rendered "high-risk vault: 0" for a scan that never looked has told the player
     * something false about a machine they are deciding whether to rob. {@code PortScanReport.knows}
     * is the question, and every row asks it.
     */
    private static void paintFindings(VBox into, PortScanReport report) {
        into.getChildren().clear();
        if (report == null) {
            into.getChildren().add(Ui.micro("Nothing yet. A scan's findings appear here when it finishes."));
            return;
        }
        if (report.blocked()) {
            Label blocked = new Label("REFUSED — " + report.note());
            blocked.setWrapText(true);
            blocked.getStyleClass().addAll("es-mono", "es-portscan-risk-high");
            into.getChildren().add(blocked);
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add(line("firewall", report.knows(PortScanTarget.FIREWALL) ? "tier " + report.firewallTier() : null));
        lines.add(line("os", report.knows(PortScanTarget.OS_VERSION) ? report.osName() : null));
        lines.add(line(
                "capability", report.knows(PortScanTarget.CYCLE_CAPABILITY) ? report.cyclesTotal() + " cycles" : null));
        lines.add(line(
                "load",
                report.knows(PortScanTarget.CYCLE_LOAD)
                        ? report.cyclesUsed() + " used · " + report.cyclesFree() + " free   (a snapshot)"
                        : null));
        lines.add(line(
                "downloads",
                report.knows(PortScanTarget.DOWNLOADS)
                        ? String.format(Locale.ROOT, "%.1f MB", report.downloadsBytes() / 1_000_000.0d)
                        : null));
        lines.add(
                line("hot vault", report.knows(PortScanTarget.VAULT_HIGH) ? report.vaultHighCount() + " items" : null));
        // ⚠ A RANGE, never a count. The middle tier is not readable from outside at any depth —
        // that is what docs/design/01 §6 buys with the tier — so the panel reports the band the scan
        // could narrow it to. Repeat deep scans tighten it and never close it.
        lines.add(line(
                "mid vault",
                report.knows(PortScanTarget.VAULT_MEDIUM)
                        ? report.vaultMediumLow() + "–" + report.vaultMediumHigh() + " items  (estimate)"
                        : null));

        for (String line : lines) {
            Label label = new Label(line);
            label.getStyleClass().addAll("es-mono", "es-portscan-finding");
            into.getChildren().add(label);
        }
        // ⚠ NOT a warning glyph. U+26A0 is in neither bundled face, so it would fall back to a host
        // font — different shape and different advance width per platform, which breaks the
        // character-cell alignment every readout in this client is laid out on. GlyphCoverageTest
        // fails the build on it, which is how this line got written twice. The word does the work.
        Label note = new Label(report.detected() ? "NOTICED — " + report.note() : report.note());
        note.setWrapText(true);
        note.getStyleClass().addAll("es-mono", report.detected() ? "es-portscan-risk-high" : "es-portscan-risk-low");
        into.getChildren().add(note);
    }

    /** One finding, or the honest absence of one. */
    private static String line(String name, String value) {
        return (name + "              ").substring(0, 14) + (value == null ? "— not scanned for" : value);
    }

    private static Region cell(javafx.scene.Node content, double width) {
        VBox box = new VBox(content);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        return box;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-package-heading");
        return label;
    }
}
