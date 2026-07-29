package io.github.stoicswe.eyeandsickle.solo.net;

import io.github.stoicswe.eyeandsickle.protocol.game.NodeReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import io.github.stoicswe.eyeandsickle.solo.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
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
    public static Optional<NodeReportState> find(SoloSave save, String address) {
        if (save == null || address == null) {
            return Optional.empty();
        }
        return save.nodeReports.stream()
                .filter(report -> address.equals(report.address))
                .findFirst();
    }

    /** Whether anything at all is on file for this machine — what the list's {@code [i]} asks. */
    public static boolean any(SoloSave save, String address) {
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
    public static NodeReportState merge(SoloSave save, PortScanReport scan, Instant now) {
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

    /** One machine's file, rendered for the interface. */
    public static NodeReport read(SoloSave save, NodeReportState state) {
        String label = save.knownNodes.stream()
                .filter(node -> state.address.equals(node.address))
                .map(node -> node.label)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("");
        return new NodeReport(
                state.address,
                label,
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
    public static boolean rename(SoloSave save, String address, String alias) {
        return find(save, address).map(report -> {
            report.alias = alias == null ? "" : alias.trim();
            return true;
        }).orElse(false);
    }

    /**
     * Replaces a machine's tags.
     *
     * <p>Lowercased and de-duplicated on the way in, so {@code Bank}, {@code bank} and {@code BANK}
     * are one tag rather than three that a search has to guess between. Blank entries are dropped
     * rather than stored, because a tag nobody can type is a tag nobody can search.
     */
    public static boolean retag(SoloSave save, String address, List<String> tags) {
        return find(save, address).map(report -> {
            java.util.LinkedHashSet<String> clean = new java.util.LinkedHashSet<>();
            for (String tag : tags == null ? List.<String>of() : tags) {
                String trimmed = tag == null ? "" : tag.trim().toLowerCase(java.util.Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    clean.add(trimmed);
                }
            }
            report.tags = new ArrayList<>(clean);
            return true;
        }).orElse(false);
    }

    /** Every file, most recently updated first — which is the order a player looks for one in. */
    public static List<NodeReport> all(SoloSave save) {
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
    public static Optional<NodeReport> at(SoloSave save, String address) {
        return find(save, address).map(state -> read(save, state));
    }
}
