package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
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
 * One row per thing you could learn, each showing what it costs in <b>cycles</b>, in <b>seconds</b>,
 * and in <b>the chance the target notices</b>. The third column is the one that makes this a decision
 * rather than arithmetic, so it is shown before committing rather than discovered afterwards — an
 * interface that revealed the risk after the fact would be offering a gamble without saying it was
 * one.
 *
 * <p>⚠ Rows are cumulative. Choosing the sixth answers the first five too, because a scan that
 * reached that far necessarily passed through them. The panel says so rather than letting a player
 * pay twice for something they already have.
 *
 * <h2>⚠ THE LADDER IS NOT THE SAME ON EVERY MACHINE, and this panel drew it as though it were</h2>
 *
 * {@code PortScanTarget.appliesTo} has said since 2026-08-07 that a bridge's findings are
 * {@code IDENTITY}, {@code FIREWALL}, {@code OS_VERSION}, {@code PEERS} and {@code MONITORED}, and
 * that everything else keeps the calibrated eight and gains nothing ({@code docs/design/17} §3.2).
 * The engine has honoured that on both sides — {@code PortScanRules.settle} answers {@code -1} for a
 * rung the machine has no such thing for, and {@code NodeReports.known} counts only the applicable
 * ones — while this panel walked {@code values()} blind. So <b>every ordinary desktop offered "Peers"
 * and "Monitoring"</b>: two rows a player could pay 9 and 11 cycles for, wait 45 and 60 seconds for,
 * take a 15% and 19% detection risk for, and get nothing back from. Nothing failed and every figure
 * rendered — the panel was quoting a real price for an answer that does not exist.
 *
 * <h2>⚠ THE KIND IS THE PLAYER'S, NEVER GROUND TRUTH</h2>
 *
 * {@link #rungsFor} reads {@link Sighting#kind()}, which is {@link HostKind#UNKNOWN} until something
 * has typed the machine — a sweep sells existence and adjacency, the 15 EC Passive Sniffer sells
 * identity ({@code docs/design/07} §1). Filtering on the topology's own kind instead would put the
 * two bridge rows on an unidentified bridge and <b>nowhere else</b>, which hands the sniffer's whole
 * product to anyone who right-clicks a machine. An unidentified bridge therefore shows the ordinary
 * eight, and asking what is on its far side costs identifying it first.
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

        Label title = new Label(Views.t("ui.port-scan.port-scan", "PORT SCAN"));
        title.getStyleClass().add("es-panel-title");
        Label target = new Label(address);
        target.getStyleClass().addAll("es-portscan-target", "es-mono");

        Label lead = new Label(Views.t(
                "ui.port-scan.choose-the-deepest-thing",
                "Choose the deepest thing you want to know. Everything above it comes "
                        + "back with it — a scan that reached that far already passed through the rest. "
                        + "Going deeper costs cycles, takes longer, and makes it more likely the machine "
                        + "notices you looking."));
        lead.setWrapText(true);
        lead.getStyleClass().add("es-portscan-lead");

        VBox ladder = new VBox(UiTokens.SPACE_1);
        VBox findings = new VBox(UiTokens.SPACE_1);
        Runnable[] repaint = new Runnable[1];

        repaint[0] = () -> {
            // ⚠ Read ONCE per repaint and used for both halves, so the ladder and the findings block
            // cannot come to different conclusions about what this machine is. This panel repaints on
            // a one-second clock, and a second read is a second answer.
            List<PortScanTarget> rungs = rungsFor(session.net(), address);
            ladder.getChildren().clear();
            ladder.getChildren().add(header());
            for (PortScanTarget rung : rungs) {
                ladder.getChildren().add(row(session, address, rung, report, repaint[0]));
            }
            paintFindings(findings, session.portScanReport(address).orElse(null), rungs);
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

    /**
     * The rungs this machine actually has, in ladder order.
     *
     * <h2>⚠ Pure, package-private, and takes the map rather than the session — on purpose</h2>
     *
     * The same seam {@code SecurityCenterView.latestOf} and {@code Anchoring.horizontal} exist for,
     * and for the same reason: the rule that shipped wrong was one living inside a method that builds
     * nodes, so the only way to check it was to run the client and look. Taking a {@link NetMap}
     * means a test hand-builds two sightings and needs no session, no save and no toolkit.
     *
     * <p>⚠ <b>A machine with no sighting is {@link HostKind#UNKNOWN}, not an error.</b> Map visibility
     * keys on {@code knownNodes} and port scanning keys on {@code host.discovered} — two notions of
     * "found" that agree only because a sweep sets both — so the honest reading of an absent sighting
     * is "nobody has typed this", which is what {@code UNKNOWN} means and what gives the ordinary
     * eight.
     */
    static List<PortScanTarget> rungsFor(NetMap net, String address) {
        HostKind kind = net == null
                ? HostKind.UNKNOWN
                : net.at(address).map(Sighting::kind).orElse(HostKind.UNKNOWN);
        List<PortScanTarget> rungs = new ArrayList<>();
        for (PortScanTarget rung : PortScanTarget.values()) {
            if (rung.appliesTo(kind)) {
                rungs.add(rung);
            }
        }
        return List.copyOf(rungs);
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
        // ⚠ WRAPS, because one rung's caption does not fit the column and JavaFX ellipsises rather
        // than complaining: PEERS rendered as "how many machines are on the far side, and what t...",
        // cut mid-word on the half of the caption that says what the finding actually gives you. It
        // survived because that row used to appear on every machine, where it was one truncated line
        // among eight; it is now one of five on a bridge and nowhere else.
        detail.setWrapText(true);

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
     *
     * <h2>⚠ It walks the SAME rung list the ladder above it does</h2>
     *
     * Two hand-written lists would drift the first time a rung moved, and the drift is silent in both
     * directions: a row here for a rung the ladder does not offer is a line that says "not scanned
     * for" forever about something nobody can scan for, and a rung the ladder offers with no row here
     * is a scan the player pays for and never sees the answer to. That second one was live — the two
     * bridge findings have been reachable and unrenderable since they landed.
     */
    private static void paintFindings(VBox into, PortScanReport report, List<PortScanTarget> rungs) {
        into.getChildren().clear();
        if (report == null) {
            into.getChildren().add(Ui.micro("Nothing yet. A scan's findings appear here when it finishes."));
            return;
        }
        if (report.blocked()) {
            Label blocked = new Label(Views.t("ui.port-scan.refused", "REFUSED — " + report.note()));
            blocked.setWrapText(true);
            blocked.getStyleClass().addAll("es-mono", "es-portscan-risk-high");
            into.getChildren().add(blocked);
            return;
        }

        List<String> lines = new ArrayList<>();
        for (PortScanTarget rung : rungs) {
            lines.add(line(shortName(rung), report.knows(rung) ? valueOf(report, rung) : null));
        }

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

    /**
     * What a finding is called in the findings block.
     *
     * <h2>⚠ Short, lowercase, and NOT {@link PortScanTarget#label()}</h2>
     *
     * The ladder above is a price list and names each rung as a question ("Medium-risk vault"); this
     * is a readout in a 14-character label column, and the labels do not fit it. Two vocabularies for
     * one ladder is a cost — but the alternative is either a ragged column or a rung named "Peers" in
     * a block every other line of which is lowercase.
     *
     * <p>⚠ An exhaustive switch, deliberately. A map or a default arm would file a rung added
     * tomorrow under a blank name; this way the compiler names the omission at the point somebody
     * adds one, which is the one place it is legible. Same reason {@code RigTab.columns()} is one.
     */
    private static String shortName(PortScanTarget rung) {
        return switch (rung) {
            case IDENTITY -> "identity";
            case FIREWALL -> "firewall";
            case OS_VERSION -> "os";
            case CYCLE_CAPABILITY -> "capability";
            case CYCLE_LOAD -> "load";
            case DOWNLOADS -> "downloads";
            case VAULT_HIGH -> "hot vault";
            case VAULT_MEDIUM -> "mid vault";
            case PEERS -> "peers";
            case MONITORED -> "monitoring";
        };
    }

    /**
     * The finding itself, in words. Only ever called for a rung {@code report.knows}.
     *
     * <p>⚠ The vault line is a <b>RANGE, never a count</b>. The middle tier is not readable from
     * outside at any depth — that is what {@code docs/design/01} §6 buys with the tier — so this
     * reports the band the scan could narrow it to. Repeat deep scans tighten it and never close it.
     *
     * <p>⚠ {@code MONITORED} says <b>whether</b> and nothing else — never whose, never what tier.
     * That restraint is the entire reason the finding is allowed to exist: a tier-1 MonJob's value is
     * that the intruder does not learn they were seen, so naming the watcher here would make tier 1
     * worthless and nobody would ever place one ({@code docs/design/17} §4.4).
     */
    private static String valueOf(PortScanReport report, PortScanTarget rung) {
        return switch (rung) {
            case IDENTITY -> identityOf(report);
            case FIREWALL -> "tier " + report.firewallTier();
            case OS_VERSION -> report.osName();
            case CYCLE_CAPABILITY -> report.cyclesTotal() + " cycles";
            case CYCLE_LOAD -> report.cyclesUsed() + " used · " + report.cyclesFree() + " free   (a snapshot)";
            case DOWNLOADS -> String.format(Locale.ROOT, "%.1f MB", report.downloadsBytes() / 1_000_000.0d);
            case VAULT_HIGH -> report.vaultHighCount() + " items";
            case VAULT_MEDIUM -> report.vaultMediumLow() + "–" + report.vaultMediumHigh() + " items  (estimate)";
            case PEERS -> peersOf(report);
            case MONITORED -> report.monitored() == 1 ? "watched" : "nothing watching";
        };
    }

    /**
     * The peer finding: how much is through there, and what it is called.
     *
     * <p>⚠ A count and a name, and nothing that could be acted on — no addresses, no kinds, no tiers,
     * no values. The far side is still outside the hop ceiling and still needs a breach, a foothold
     * and a {@code connect}, which is what keeps this clear of Invariant <b>I2</b>
     * ({@code docs/design/17} §3.1).
     *
     * <p>⚠ The server name may legitimately be blank — a bridge whose link goes nowhere — so it is
     * appended rather than interpolated, or the row would read {@code "7 machines · "}.
     */
    static String peersOf(PortScanReport report) {
        String count = report.peerCount() + (report.peerCount() == 1 ? " machine" : " machines");
        String where = report.peerServerName() == null ? "" : report.peerServerName();
        return where.isBlank() ? count : count + " · " + where;
    }

    /**
     * The identity finding as one string: {@code operator@machine}.
     *
     * <p>⚠ Either half may be missing and the row still has to read. A gateway has a name and no
     * account worth speaking of, so {@code PortScanReport.knows} counts the rung as answered when
     * <em>either</em> is present — and a naive {@code operator + "@" + name} would render a bare
     * {@code @bold-turing} or {@code dana@} for a scan that came back with everything there was.
     *
     * <p>⚠ The two halves are ONE finding and share one row, because that is what the rung sells.
     * Splitting them into "name" and "operator" would put two "— not scanned for" lines on every
     * unscanned machine for a single unpaid rung, which reads as two gaps rather than one. The order
     * is {@code operator@machine} — the one {@code Hostname.prompt} teaches and argues for: who you
     * are, then where you are.
     */
    static String identityOf(PortScanReport report) {
        String who = report.operatorName() == null ? "" : report.operatorName();
        String where = report.hostName() == null ? "" : report.hostName();
        if (who.isEmpty()) {
            return where;
        }
        return where.isEmpty() ? who : who + "@" + where;
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
