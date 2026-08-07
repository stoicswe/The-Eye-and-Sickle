package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.protocol.game.NodeReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The intelligence file: what has been learned about each machine, and when.
 *
 * <h2>Merging, not replacing</h2>
 *
 * A scan answers everything down to its depth and nothing below it. Overwriting the whole report with
 * each result would throw away a deep scan's vault estimate the next time the player ran a cheap
 * firewall check — so each finding is written only when the scan actually reached it, and carries the
 * instant it was learned.
 *
 * <p>⚠ {@code PortScanReport.knows} is the test, not the field's value. A report is allowed to carry
 * {@code -1} for something it did not look at, and treating that as a finding would record "no items
 * in the vault" for a scan that never opened it.
 */
public final class NodeReports {

    private NodeReports() {}

    /** The stored file for a machine, if one exists. */
    public static Optional<NodeReportState> find(GameSave save, String address) {
        if (save == null || address == null) {
            return Optional.empty();
        }
        return save.nodeReports.stream()
                .filter(report -> address.equals(report.address))
                .findFirst();
    }

    /** Whether anything at all is on file for this machine — what the list's {@code [i]} asks. */
    public static boolean any(GameSave save, String address) {
        return find(save, address).filter(NodeReportState::any).isPresent();
    }

    /**
     * Folds a completed scan into the machine's file.
     *
     * <p>⚠ A <b>blocked</b> scan still counts as a scan and still bumps the detection count — it
     * learned nothing, which is itself worth recording, and a machine that keeps cutting you off is
     * exactly the intelligence a player wants before spending a breach on it. What it must not do is
     * write findings, because it has none.
     */
    public static NodeReportState merge(GameSave save, PortScanReport scan, Instant now) {
        NodeReportState report = find(save, scan.address()).orElseGet(() -> {
            NodeReportState fresh = new NodeReportState();
            fresh.address = scan.address();
            fresh.createdAt = now;
            save.nodeReports.add(fresh);
            return fresh;
        });
        report.updatedAt = now;
        report.scans++;
        if (scan.detected()) {
            report.detections++;
        }
        if (scan.blocked()) {
            return report;
        }

        if (scan.knows(PortScanTarget.IDENTITY)) {
            // ⚠ Write-once — see NodeReportState#hostName. Every other finding below REFRESHES; this
            // one must not, or a rescan after a name-pool edit silently renames a machine the player
            // has been working with. `learnedAt` is stamped only on the first establishment too, so
            // the age shown beside the name is the age of the discovery rather than of the last scan.
            if (report.hostName.isBlank() && report.operatorName.isBlank()) {
                report.hostName = scan.hostName();
                report.operatorName = scan.operatorName();
                report.learnedAt.put(PortScanTarget.IDENTITY.name(), now);
            }
        }
        if (scan.knows(PortScanTarget.FIREWALL)) {
            report.firewallTier = scan.firewallTier();
            report.learnedAt.put(PortScanTarget.FIREWALL.name(), now);
        }
        if (scan.knows(PortScanTarget.OS_VERSION)) {
            report.osName = scan.osName();
            report.learnedAt.put(PortScanTarget.OS_VERSION.name(), now);
        }
        if (scan.knows(PortScanTarget.CYCLE_CAPABILITY)) {
            report.cyclesTotal = scan.cyclesTotal();
            report.learnedAt.put(PortScanTarget.CYCLE_CAPABILITY.name(), now);
        }
        if (scan.knows(PortScanTarget.CYCLE_LOAD)) {
            report.cyclesUsed = scan.cyclesUsed();
            report.learnedAt.put(PortScanTarget.CYCLE_LOAD.name(), now);
        }
        if (scan.knows(PortScanTarget.DOWNLOADS)) {
            report.downloadsBytes = scan.downloadsBytes();
            report.learnedAt.put(PortScanTarget.DOWNLOADS.name(), now);
        }
        if (scan.knows(PortScanTarget.VAULT_HIGH)) {
            report.vaultHighCount = scan.vaultHighCount();
            report.learnedAt.put(PortScanTarget.VAULT_HIGH.name(), now);
        }
        if (scan.knows(PortScanTarget.VAULT_MEDIUM)) {
            report.vaultMediumEstimate = scan.vaultMediumEstimate();
            report.vaultMediumError = scan.vaultMediumError();
            report.learnedAt.put(PortScanTarget.VAULT_MEDIUM.name(), now);
        }
        return report;
    }

    /**
     * Records what a machine calls itself and who runs it, without that counting as a scan.
     *
     * <h2>Why a breach establishes this and no other finding</h2>
     *
     * Standing on a machine, its name and its logged-in account are the two facts you cannot avoid
     * learning — they are in the prompt. Everything else on the file stays a scan's product: a
     * foothold does not tell you the vault estimate, and handing out the rest here would delete the
     * whole ladder for anyone who breaches first and asks questions later.
     *
     * <p>⚠ <b>Write-once, and this is the "retains the name from the first breach" guarantee.</b>
     * Called from {@code NetRules.reconcileFootholds}, which runs on every load and every breach
     * settlement, so it is reached many times per machine — the first one wins and the rest are
     * no-ops. See {@code NodeReportState#hostName} for why an identity is pinned where a measurement
     * is refreshed.
     *
     * <p>⚠ It creates the file if there is none, so a machine breached without ever being scanned
     * still has a name on record. But it deliberately does <b>not</b> bump {@code scans}: a file
     * whose only entry came from a break-in has had no scans, and reporting one would make the
     * detection ratio beside it a fraction of a number that never happened.
     *
     * @return whether anything was written
     */
    public static boolean establishIdentity(GameSave save, HostState host, Instant now) {
        if (save == null || host == null || host.address == null || host.address.isBlank()) {
            return false;
        }
        NodeReportState report = find(save, host.address).orElseGet(() -> {
            NodeReportState fresh = new NodeReportState();
            fresh.address = host.address;
            fresh.createdAt = now;
            save.nodeReports.add(fresh);
            return fresh;
        });
        if (!report.hostName.isBlank() || !report.operatorName.isBlank()) {
            return false;
        }
        report.hostName = host.label == null ? "" : host.label;
        report.operatorName = VirtualFs.hostUser(host);
        report.learnedAt.put(PortScanTarget.IDENTITY.name(), now);
        report.updatedAt = now;
        return true;
    }

    /**
     * How complete a machine's file is, {@code 0} to {@code 1}.
     *
     * <p>The fraction of {@link PortScanTarget}s that have ever been established, which is what
     * "how much do I know about this machine" means when the findings are the only things knowable.
     * Every finding counts the same: they are ordered by how hard they are to reach
     * ({@code PortScanTarget}'s depth), and that ordering is already priced into what a scan costs —
     * weighting them here as well would charge for the same difficulty twice.
     *
     * <p>⚠ <b>Staleness is deliberately not considered.</b> A finding that was true a week ago still
     * counts as known. The report already carries a per-finding {@code learnedAt} and shows its age,
     * so a player can see what has gone cold; making an old finding silently stop counting would move
     * the odds under them with nothing on screen changing.
     *
     * <p>A machine with no file at all is {@code 0}, which is the honest answer and the one that
     * makes an unscanned target behave as the default.
     */
    public static double known(GameSave save, String address) {
        return find(save, address)
                .map(state -> {
                    int found = 0;
                    for (PortScanTarget target : PortScanTarget.values()) {
                        if (state.learnedAt.containsKey(target.name())) {
                            found++;
                        }
                    }
                    return found / (double) PortScanTarget.values().length;
                })
                .orElse(0.0d);
    }

    /**
     * One machine's file, rendered for the interface.
     *
     * <p>⚠ The name comes off the <b>file</b>, not off {@code save.knownNodes}. It used to be read
     * from the discovered node, which a sweep filled in from ground truth — so every machine the
     * player had ever seen was already named and {@code PortScanTarget.IDENTITY} would have had
     * nothing left to sell. One stored answer, in the one place that applies the write-once rule.
     */
    public static NodeReport read(GameSave save, NodeReportState state) {
        return new NodeReport(
                state.address,
                state.hostName == null ? "" : state.hostName,
                state.operatorName == null ? "" : state.operatorName,
                state.alias == null ? "" : state.alias,
                List.copyOf(state.tags),
                state.createdAt,
                state.updatedAt,
                state.scans,
                state.detections,
                state.firewallTier,
                state.osName == null ? "" : state.osName,
                state.cyclesTotal,
                state.cyclesUsed,
                state.downloadsBytes,
                state.vaultHighCount,
                state.vaultMediumEstimate,
                state.vaultMediumError,
                java.util.Map.copyOf(state.learnedAt));
    }

    /**
     * Names a machine, or clears the name.
     *
     * <p>⚠ Only a machine with a file can be named. A name is a note about intelligence you hold, and
     * letting one be attached to a machine nobody has looked at would make the RECON list a bookmark
     * folder — a different feature, with the reports buried in it.
     */
    public static boolean rename(GameSave save, String address, String alias) {
        return find(save, address)
                .map(report -> {
                    report.alias = alias == null ? "" : alias.trim();
                    return true;
                })
                .orElse(false);
    }

    /**
     * Replaces a machine's tags.
     *
     * <p>Lowercased and de-duplicated on the way in, so {@code Bank}, {@code bank} and {@code BANK}
     * are one tag rather than three that a search has to guess between. Blank entries are dropped
     * rather than stored, because a tag nobody can type is a tag nobody can search.
     */
    public static boolean retag(GameSave save, String address, List<String> tags) {
        return find(save, address)
                .map(report -> {
                    java.util.LinkedHashSet<String> clean = new java.util.LinkedHashSet<>();
                    for (String tag : tags == null ? List.<String>of() : tags) {
                        String trimmed = tag == null ? "" : tag.trim().toLowerCase(java.util.Locale.ROOT);
                        if (!trimmed.isEmpty()) {
                            clean.add(trimmed);
                        }
                    }
                    report.tags = new ArrayList<>(clean);
                    return true;
                })
                .orElse(false);
    }

    /** Every file, most recently updated first — which is the order a player looks for one in. */
    public static List<NodeReport> all(GameSave save) {
        if (save == null) {
            return List.of();
        }
        List<NodeReport> out = new ArrayList<>();
        for (NodeReportState state : save.nodeReports) {
            out.add(read(save, state));
        }
        out.sort(Comparator.comparing(NodeReport::updatedAt).reversed());
        return out;
    }

    /** One machine's file, rendered, if there is one. */
    public static Optional<NodeReport> at(GameSave save, String address) {
        return find(save, address).map(state -> read(save, state));
    }
}
