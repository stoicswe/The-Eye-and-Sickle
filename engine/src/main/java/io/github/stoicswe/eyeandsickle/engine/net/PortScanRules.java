package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanReport;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Looking at somebody else's machine from outside, and being looked back at.
 *
 * <h2>⚠ This is NOT the audit scan, and the two must not be confused</h2>
 *
 * {@code ScanRules} audits <em>your own rig</em> for parasites: it is inward, silent, and generates
 * no heat, because looking at your own machine is not an act against anybody
 * ({@code docs/design/04-mining.md} §3.2, Invariant <b>I9</b>). A port scan is the opposite in every
 * one of those respects — it is outward, it touches a machine you do not hold, and it is <b>loud</b>.
 * Sharing a cost ladder between the two would say they were the same kind of act.
 *
 * <h2>The decision this exists to create</h2>
 *
 * A scan that always told you everything would have no decision in it, so depth costs three things at
 * once: <b>cycles</b>, <b>time</b>, and <b>the chance the target notices</b>. Naming what you want to
 * know sets all three ({@link PortScanTarget}), which is why the player picks a question rather than
 * a tier — you are buying an answer and paying for how loudly you had to ask for it.
 *
 * <p>Being noticed is not merely a failed scan. The target gets to respond, and the response is
 * {@code Reprisal}: refuse the scan outright, or come back at you. That is what makes the deepest
 * scan a genuine gamble rather than a strictly-correct default.
 *
 * <h2>⚠ Findings are DERIVED from the host, never rolled at scan time</h2>
 *
 * Two scans of the same unchanged machine must agree, or the readout is noise dressed as
 * intelligence — and "was this here before?" is a question this game asks the player to answer
 * constantly. What <em>is</em> rolled is whether you were detected, which is a fact about this
 * attempt rather than about the machine.
 */
public final class PortScanRules {

    private PortScanRules() {}

    /** The task kind, so the activity readout and the settle path can recognise one. */
    public static final String KIND = "portscan";

    /** Why a scan could not start. */
    public enum Refusal {
        /** No such machine has been found by a sweep. */
        UNKNOWN_HOST,

        /** It is your own rig. The audit window is the tool for that, and it is free and silent. */
        YOUR_OWN_RIG,

        /** Not enough free cycles. */
        NOT_ENOUGH_CYCLES,

        /** One is already running against this machine. */
        ALREADY_RUNNING
    }

    /** The commissioned scan, or the reason there is none. */
    public record Started(TaskState task, Refusal refusal, long cycles, Duration duration, int riskPercent) {

        public boolean succeeded() {
            return task != null;
        }
    }

    // ── what a depth costs ─────────────────────────────────────────────────────────────────────

    /**
     * ⚠ The floors below apply to {@link PortScanTarget#IDENTITY} and to nothing else, by arithmetic.
     *
     * <p>Every cost here is keyed on {@link PortScanTarget#steps()} — rungs above the cheapest — so
     * that adding {@code IDENTITY} to the bottom of the ladder left the seven calibrated rungs at
     * exactly the prices they already had. {@code PortScanRulesTest} asserts those seven figures
     * literally, because "unchanged" is the whole claim and a formula that merely looks equivalent is
     * not evidence.
     *
     * <p>{@code steps == 0} would otherwise make the cheapest rung take no time and make no noise,
     * which is not "cheap" but "free" — and a free rung is one every player runs on every machine
     * without a decision, which is what the ladder exists to avoid. For {@code steps ≥ 1} both
     * multipliers already exceed their floor, so neither floor can ever move a rung above the bottom.
     */
    static final long IDENTITY_SECONDS = 8L;

    static final long IDENTITY_NOISE = 1L;

    /**
     * Cycles for a scan of this depth.
     *
     * <p>Deliberately in the same range as the audit ladder's 5–35 ({@code Balance.SCAN_*_CYCLES}) so
     * that a player who has learned what a scan feels like to pay for is not learning a second scale.
     * The rungs are closer together because there are eight of them rather than three.
     */
    public static long cyclesFor(PortScanTarget target) {
        return 3L + 2L * steps(target);
    }

    /** How long it takes. Roughly fifteen seconds a rung, so the deepest is a touch under two minutes. */
    public static Duration durationFor(PortScanTarget target) {
        return Duration.ofSeconds(Math.max(IDENTITY_SECONDS, 15L * steps(target)));
    }

    /**
     * How loud it is, in cycle-equivalents on the noise meter.
     *
     * <p>⚠ Not {@link #cyclesFor}. How much of your own machine a job occupies and how much racket it
     * makes on somebody else's are different quantities — {@code TaskState.noiseCycles} carries that
     * separation, and a port scan is the clearest case in the game of a job that is cheap to run and
     * impossible to do quietly.
     */
    public static long noiseFor(PortScanTarget target) {
        return Math.max(IDENTITY_NOISE, 2L * steps(target));
    }

    /**
     * The chance the target notices, in percent.
     *
     * <p>Depth is the player's contribution and the firewall is the machine's. A tier-3 firewall on a
     * deep scan is a coin flip, which is the point at which "just run the deepest one" stops being
     * free advice.
     *
     * <p>⚠ Never zero and never certain. A floor keeps the shallowest scan from being a way to farm
     * information at no risk; a ceiling keeps the deepest from being pointless against a hard target,
     * which would just remove the option rather than price it.
     */
    public static int riskPercent(HostState host, PortScanTarget target) {
        int firewall = host == null ? 0 : Math.max(0, Math.min(3, host.firewallTier));
        int risk = 3 + 4 * steps(target) + 6 * firewall;
        return Math.max(3, Math.min(70, risk));
    }

    private static int depth(PortScanTarget target) {
        return target == null ? 1 : target.depth();
    }

    /** Rungs above the cheapest. See {@link #IDENTITY_SECONDS} for why every cost is keyed on this. */
    private static int steps(PortScanTarget target) {
        return target == null ? 0 : target.steps();
    }

    // ── running one ────────────────────────────────────────────────────────────────────────────

    /**
     * Commissions a scan.
     *
     * <p>⚠ The cycles are <b>held</b> for the task's duration and recover afterwards, exactly as the
     * audit ladder does (UI-6). A scan whose cost came back while it was still running would make the
     * deepest one nearly free, which is the half of the price that actually bites.
     *
     * @param now the session clock — never {@code Instant.now()}; see {@code TaskState}
     */
    public static Started begin(GameSave save, String address, PortScanTarget target, Instant now) {
        if (save == null || address == null || address.isBlank()) {
            return new Started(null, Refusal.UNKNOWN_HOST, 0L, Duration.ZERO, 0);
        }
        // ⚠ SELF is the rig itself. Scanning your own machine from outside is not the tool for it —
        // the audit window reads it directly, for free and in silence, because looking at your own
        // machine is not an act against anybody (I9).
        if (hostAt(save, address).map(host -> "SELF".equals(host.kind)).orElse(false)) {
            return new Started(null, Refusal.YOUR_OWN_RIG, 0L, Duration.ZERO, 0);
        }
        // ⚠ DISCOVERED, not merely present in the topology. The refusal already promised "no machine
        // that a sweep has found", and checking the topology instead let a player scan a machine they
        // have never seen — every host in the world exists there, discovered or not, so the check was
        // a check on nothing. Caught by a test that could not assert on the sighting of a machine the
        // map had never heard of.
        Optional<HostState> host = hostAt(save, address).filter(found -> found.discovered);
        if (host.isEmpty()) {
            return new Started(null, Refusal.UNKNOWN_HOST, 0L, Duration.ZERO, 0);
        }
        if (running(save, address).isPresent()) {
            return new Started(null, Refusal.ALREADY_RUNNING, 0L, Duration.ZERO, 0);
        }

        long cycles = cyclesFor(target);
        Duration duration = durationFor(target);
        int risk = riskPercent(host.get(), target);
        // ⚠ RESERVED, not spent — the same hold-then-recover the audit ladder uses (UI-6). A scan
        // whose cycles came back while it was still running would make the deepest one nearly free,
        // and the hold is the half of the price that actually bites on a lean rig.
        var allocation = io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules.reserve(
                save.rig,
                io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer.ACTIVE_TOOL,
                "port scan " + address,
                cycles);
        if (allocation == null) {
            return new Started(null, Refusal.NOT_ENOUGH_CYCLES, cycles, duration, risk);
        }
        allocation.startedAt = now;

        TaskState task = new TaskState(
                KIND,
                "port scan " + address + " (" + target.label().toLowerCase(java.util.Locale.ROOT) + ")",
                allocation.allocationId,
                cycles,
                now,
                now.plus(duration));
        task.noiseCycles = noiseFor(target);
        // Address and target ride on the task: the tick that settles it minutes later has no other
        // way to know what was asked for, and the answer must not depend on what the player has
        // selected in a window by then.
        task.outcome = address + " " + target.name();
        save.tasks.add(task);
        // ⚠ Announced HERE, in the rules, rather than by whichever surface started it. A scan can be
        // commissioned from the window, the node menu or the shell, and a notice written at the call
        // site would exist in some of those and not others — with no way to tell which from the log.
        io.github.stoicswe.eyeandsickle.engine.rules.EventLog.notice(
                save,
                "net",
                "port scan of " + address + " started: " + target.label().toLowerCase(java.util.Locale.ROOT)
                        + ", " + cycles + " cycles held, about " + duration.toSeconds()
                        + "s to go. " + risk + "% chance it notices.",
                now);
        return new Started(task, null, cycles, duration, risk);
    }

    /** A scan already running against this machine, if there is one. */
    public static Optional<TaskState> running(GameSave save, String address) {
        if (save == null) {
            return Optional.empty();
        }
        return save.tasks.stream()
                .filter(task -> KIND.equals(task.kind))
                .filter(task -> address.equals(addressOf(task)))
                .findFirst();
    }

    public static String addressOf(TaskState task) {
        String[] parts = String.valueOf(task == null ? "" : task.outcome).split(" ");
        return parts.length > 0 ? parts[0] : "";
    }

    /** What the scan was asked to find out. Falls back to the shallowest rung on an old task. */
    public static PortScanTarget targetOf(TaskState task) {
        String[] parts = String.valueOf(task == null ? "" : task.outcome).split(" ");
        if (parts.length < 2) {
            return PortScanTarget.FIREWALL;
        }
        try {
            return PortScanTarget.valueOf(parts[1]);
        } catch (IllegalArgumentException unknown) {
            return PortScanTarget.FIREWALL;
        }
    }

    /**
     * Settles a finished scan: rolls detection, then reads off whatever the depth reached.
     *
     * <p>⚠ Detection is rolled <b>here</b>, at settlement, not at commission. A roll at the start
     * would be knowable before the scan finished — and, more to the point, would let a player learn
     * the outcome and quit without saving. {@code Rng}'s whole discipline is that a draw is committed
     * the moment it is made.
     */
    public static PortScanReport settle(GameSave save, TaskState task, Rng rng, Instant now) {
        String address = addressOf(task);
        PortScanTarget target = targetOf(task);
        Optional<HostState> found = hostAt(save, address);
        if (found.isEmpty()) {
            return PortScanReport.refused(address, target, now, "the machine is no longer reachable from here.");
        }
        HostState host = found.get();

        // ⚠ Drawn unconditionally and before anything branches on it, so the RNG stream does not
        // depend on the outcome — Rng's contract about a stored seed not being a replay.
        boolean detected = rng.nextInt(100) < riskPercent(host, target);
        NodeState node = nodeAt(save, address).orElse(null);
        int depth = depth(target);

        // A detected scan against a defended machine is refused outright; against an undefended one
        // the owner notices and does nothing but remember it. The reprisal itself is somebody else's
        // job — this class reports, it does not retaliate.
        boolean blocked = detected && host.defended;
        if (blocked) {
            return PortScanReport.refused(
                    address,
                    target,
                    now,
                    "the scan was seen and cut off. Whatever answered is defended, and it now knows "
                            + "your vantage.");
        }

        int deepScans = node == null ? 0 : node.deepScans;
        boolean bridge = HostKind.BRIDGE.name().equals(host.kind);
        return new PortScanReport(
                address,
                target,
                now,
                detected,
                false,
                depth >= 1 ? host.label : "",
                depth >= 1 ? VirtualFs.hostUser(host) : "",
                depth >= 2 ? Math.max(0, Math.min(3, host.firewallTier)) : -1,
                depth >= 3 ? osOf(host) : "",
                depth >= 4 ? capabilityOf(host) : -1L,
                depth >= 5 ? loadOf(host, now) : -1L,
                depth >= 6 ? downloadsOf(host) : -1L,
                depth >= 7 ? vaultHighOf(host) : -1,
                depth >= 8 ? vaultMediumOf(host) : -1,
                depth >= 8 ? vaultMediumErrorOf(host, deepScans) : 0,
                // ⚠ GATED ON APPLICABILITY AS WELL AS DEPTH. A desktop scanned to depth 4 must not
                // report a peer count of zero — zero is a finding ("this bridge goes nowhere") and
                // would be a false one. -1 is "there is no such thing here", which is what
                // PortScanTarget.appliesTo says and what `knows` reads.
                bridge && depth >= 4 ? peerCountOf(save, host) : -1,
                bridge && depth >= 4 ? peerServerNameOf(save, host) : "",
                bridge && depth >= 5 ? monitoringOf(save, host) : -1,
                detected
                        ? "The scan completed, and the target noticed. Expect an answer."
                        : "Clean — nothing on the far side reacted.");
    }

    // ── the findings, all derived ──────────────────────────────────────────────────────────────

    /**
     * The OS a machine reports.
     *
     * <p>Derived from the host's kind and a digest of its address, so a rescan agrees with itself and
     * two machines of the same kind are not identical. The names are the real families a stack
     * fingerprint distinguishes, which is the fact worth carrying: an OS is identifiable from the
     * outside because implementations of the same protocol differ in ways nobody standardised.
     */
    public static String osOf(HostState host) {
        String[] families = {
            "uOS 14.2 (FreeBSD-derived)",
            "Debian 12 · Linux 6.1",
            "uOS 12.6 (FreeBSD-derived)",
            "Alpine 3.19 · Linux 6.6",
            "Windows Server 2022",
            "OpenBSD 7.4",
        };
        return families[(int) Math.floorMod(mix(host.address), (long) families.length)] + minorOf(host);
    }

    private static String minorOf(HostState host) {
        long patch = Math.floorMod(mix(host.address + ":patch"), 12L);
        return host.patched ? "  (patched, +" + (patch + 1) + ")" : "";
    }

    /** How big the machine is, in cycles. Scales with its tier, which is what a tier means. */
    public static long capabilityOf(HostState host) {
        long base = 40L + 30L * Math.max(1, Math.min(5, host.tier));
        return base + Math.floorMod(mix(host.address + ":cap"), 20L);
    }

    /**
     * What was busy at the moment of the snapshot.
     *
     * <p>⚠ A function of the host and the <b>minute</b>, so it moves between scans without being
     * random. A load that never changed would make the "snapshot, stale the moment it is taken"
     * warning a lie; one that was drawn fresh every time would make two scans a minute apart disagree
     * wildly and teach the player the readout is worthless.
     */
    public static long loadOf(HostState host, Instant now) {
        long total = capabilityOf(host);
        long minute = now.getEpochSecond() / 60L;
        long swing = Math.floorMod(mix(host.address + ":load:" + minute), 45L);
        return Math.min(total, total * (25L + swing) / 100L);
    }

    /** How much is sitting in the target's download folder. */
    public static long downloadsOf(HostState host) {
        return 12_000_000L + Math.floorMod(mix(host.address + ":dl"), 900L) * 1_000_000L;
    }

    /** Items in the exposed tier. Countable, because the hot zone is exposed by construction. */
    public static int vaultHighOf(HostState host) {
        return (int) Math.floorMod(mix(host.address + ":hot"), 6L);
    }

    /** The midpoint of the middle tier's contents. Never reported without {@link #vaultMediumErrorOf}. */
    public static int vaultMediumOf(HostState host) {
        return 2 + (int) Math.floorMod(mix(host.address + ":mid"), 11L);
    }

    /**
     * How wide the band around that midpoint is.
     *
     * <h2>⚠ It NARROWS with repeated scans and never closes</h2>
     *
     * More samples of the same machine make a better estimate, which is both true of real measurement
     * and the mechanic that makes rescanning worth its detection risk. What it never does is reach
     * zero: the middle tier is not readable from outside, and a band that closed would hand the
     * player a count and make {@code docs/design/01-core-resources.md} §6's tier a formality.
     *
     * <p>A harder firewall keeps the estimate vaguer, which is the machine's contribution to the same
     * number the player's persistence is fighting.
     */
    public static int vaultMediumErrorOf(HostState host, int previousDeepScans) {
        int firewall = Math.max(0, Math.min(3, host.firewallTier));
        int band = 4 + firewall - Math.max(0, previousDeepScans);
        return Math.max(1, band);
    }

    // ── lookups ────────────────────────────────────────────────────────────────────────────────

    // ── bridge findings ────────────────────────────────────────────────────────────────────────

    /**
     * How many machines sit on the far side of this bridge.
     *
     * <h2>⚠ A COUNT, AND NOTHING THAT COULD BE ACTED ON</h2>
     *
     * No addresses, no kinds, no tiers, no values. The far side is still outside the hop ceiling and
     * still needs a breach, a foothold and a {@code connect} to reach — so this is a property of a
     * machine already in the player's files, like its firewall tier, rather than a window past it.
     * That is what keeps it clear of Invariant <b>I2</b>; see {@code docs/design/17} §3.1.
     *
     * <p>⚠ Counted over the peer's whole <b>server</b>, not over the peer host's links. The question a
     * player is asking is "how much is through there", and a bridge that happened to link to a
     * sparsely-connected machine would otherwise report a small number about a large server.
     */
    private static int peerCountOf(GameSave save, HostState bridge) {
        if (save.topology == null || bridge.bridgePeer == null || bridge.bridgePeer.isBlank()) {
            return 0;
        }
        String peerServer = save.topology.hosts.stream()
                .filter(host -> bridge.bridgePeer.equals(host.address))
                .map(host -> host.serverId)
                .findFirst()
                .orElse("");
        if (peerServer.isBlank()) {
            return 0;
        }
        return (int) save.topology.hosts.stream()
                .filter(host -> peerServer.equals(host.serverId))
                .count();
    }

    /** What the server on the far side is called, or blank when the link goes nowhere. */
    private static String peerServerNameOf(GameSave save, HostState bridge) {
        if (save.topology == null || bridge.bridgePeer == null || bridge.bridgePeer.isBlank()) {
            return "";
        }
        return save.topology.hosts.stream()
                .filter(host -> bridge.bridgePeer.equals(host.address))
                .findFirst()
                .flatMap(peer -> save.topology.servers.stream()
                        .filter(server -> server.serverId.equals(peer.serverId))
                        .findFirst())
                .map(server -> server.name)
                .orElse("");
    }

    /**
     * Whether anything is watching this bridge — {@code 1} yes, {@code 0} no.
     *
     * <h2>⚠ "MONITORED", AND NOTHING ELSE. Never whose, never what tier.</h2>
     *
     * That restraint is the whole reason this finding is allowed to exist. A tier-1 MonJob's value is
     * that the intruder does not learn they were seen; reporting the tier here would make tier 1
     * worthless and nobody would ever place one. What the player buys is the knowledge that crossing
     * <em>would</em> be seen — which makes distance-risk a decision rather than a blind tax, and makes
     * a MonJob deter even when it never fires. {@code docs/design/17} §4.4, MJ-4.
     *
     * <p>⚠ One rule for NPC and player MonJobs. Only the NPC half is derivable today
     * ({@link MonJobs}); when player-placed jobs land in the save, they are OR-ed in here and nowhere
     * else, so the finding cannot come to mean two different things.
     */
    private static int monitoringOf(GameSave save, HostState bridge) {
        return MonJobs.watched(bridge, depthOf(save, bridge)) ? 1 : 0;
    }

    /** The bridge's server's distance from home, which is what MonJob density scales on. */
    private static int depthOf(GameSave save, HostState host) {
        if (save.topology == null || save.topology.servers == null) {
            return 0;
        }
        return save.topology.servers.stream()
                .filter(server -> server.serverId.equals(host.serverId))
                .findFirst()
                .map(server -> server.depthFromHome)
                .orElse(0);
    }

    private static Optional<HostState> hostAt(GameSave save, String address) {
        if (save.topology == null) {
            return Optional.empty();
        }
        return save.topology.hosts.stream()
                .filter(host -> address.equals(host.address))
                .findFirst();
    }

    private static Optional<NodeState> nodeAt(GameSave save, String address) {
        return save.knownNodes.stream()
                .filter(node -> address.equals(node.address))
                .findFirst();
    }

    /** splitmix64 over a string. Same mixing the rest of the derived world uses. */
    private static long mix(String of) {
        long z = String.valueOf(of).hashCode() * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return Math.abs(z ^ (z >>> 31));
    }
}
