package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A machine the player has discovered.
 *
 * <p>Only discovered nodes exist here, and that rule is load-bearing rather than tidy: the virtual
 * namespace, tab completion and {@code ls /net/} are all built from this list, so a node that has not
 * been paid for cannot leak through any of them. {@code docs/client/04-terminology-and-education.md}
 * §3.2 and §3.6 both call this out as the least obvious way a client could accidentally hand the
 * player something recon is meant to sell them.
 */
public final class NodeState {

    public String address = "";
    public String label = "";

    /**
     * Which generated server this sits on — the link back to {@link HostState#serverId}.
     *
     * <p>Carried on the player's side as well as the truth side because the map has to name the
     * server a node is on without consulting ground truth: {@code NetRules.view} builds the whole
     * visible network from this list, and a lookup into the topology for a field the player has
     * already been told would be one accidental widening away from leaking the rest of the record.
     */
    public String serverId = "";

    /**
     * {@code HostKind.name()}, or {@code "UNKNOWN"} until a type-revealing tool has run.
     *
     * <p>⚠ <b>Never set by a sweep.</b> What a sweep sells is existence and adjacency; what the
     * 15 EC Passive Sniffer sells is identity ({@code docs/design/07-recon-tools.md} §1). A sweep
     * that named types would delete a purchased tool at the point of rendering, which is
     * {@code docs/design/02-unlock-gates.md} §5's pricing check failing — four recon products, four
     * gates, no overlap.
     */
    public String kind = "UNKNOWN";

    /** How much the player has learned. Recon raises it; it never decreases. */
    public int reconLevel = 0;

    /**
     * Which {@link FolderState} the player has filed this machine under, or {@code ""} for unfiled.
     *
     * <p>On the player's record rather than as a list of addresses on the folder, so a machine cannot
     * be in two folders and cannot be in a folder that no longer exists without the inconsistency
     * being one field. {@code FolderRules} unfiles a node whose folder has gone rather than leaving a
     * dangling id, which is what keeps the tree renderable from this list alone.
     *
     * <p>⚠ Mechanically inert. Nothing in the rules reads it — see {@link FolderState}.
     */
    public String folderId = "";

    public Instant discoveredAt = Instant.now();

    /** Difficulty tier for a breach attempt against this node. */
    public int tier = 1;

    /** Miners the player has deployed here. Each costs the deployer a control channel. */
    public List<MinerState> deployedMiners = new ArrayList<>();

    /** Foreign miners discovered on this node — the four-response decision in {@code design/04} §5. */
    public boolean hostsForeignMiner = false;

    // ---------------------------------------------------- defence profile (design/05 §2, design/09)

    /**
     * The target defence profile {@code docs/design/05-hacking-minigame.md} §2 instantiates a breach
     * with: "a node with a defense profile (firewall tier, tarpit, honeypot flag, canary tokens,
     * ...) drawn from {@code 09-defense-and-hardening.md}."
     *
     * <p>These are the node's <em>truth</em>. What the player is told about them is a recon
     * question, not a storage one — {@code Targets.available} publishes them as far as recon has
     * established, which today means the fields are shown once the node is known. When recon levels
     * become a real gate they narrow here, not in the renderer.
     */
    public int firewallTier = 0;

    /** Costs the intruder attention on every action ({@code 09} §1) rather than cutting the budget. */
    public boolean tarpit = false;

    /** Alerts the owner and tags the toucher's handle — the evidence path in {@code design/12}. */
    public boolean canaries = false;

    /**
     * Whether the player suspects a trap.
     *
     * <p>A <em>suspicion</em>, deliberately, and never a fact: {@code docs/design/07-recon-tools.md}
     * §2 requires the Honeypot Detector to have a false-negative rate, because "a perfect detector
     * removes the fear the traps exist to create". A clean reading is never a guarantee.
     */
    public boolean honeypotSuspected = false;

    /**
     * Whether this node is actually defended and active.
     *
     * <p>⚠ Not the same as what the player <em>knows</em>. Proof-of-skill credit requires a live or
     * defended target ({@code docs/design/02-unlock-gates.md} §2.4, Invariant I7), and
     * {@code docs/design/07-recon-tools.md} §1 makes distinguishing live from dormant the Traffic
     * Analyzer's entire published function. So a target counts as {@code LIVE} only when
     * {@link #trafficAnalyzed} has established it — an unexamined node is reported dormant, which is
     * the reading that cannot accidentally hand out an unlock.
     */
    public boolean defended = false;

    /** Whether the Traffic Analyzer has been run here ({@code docs/design/07-recon-tools.md} §2). */
    public boolean trafficAnalyzed = false;

    /**
     * How many times a scan has reached the deepest rung against this machine.
     *
     * <h2>⚠ The only thing about a port scan that is REMEMBERED, and it is why rescanning is worth it</h2>
     *
     * Every finding a port scan reports is derived from the host, so a rescan agrees with itself and
     * nothing needs storing. The exception is the medium-tier vault, which is only ever an
     * <em>estimate</em>: more samples of the same machine narrow the band, exactly as more
     * measurements of anything do. That is what turns a rescan from a pointless repeat into a trade —
     * a tighter number against another roll of the detection dice.
     *
     * <p>⚠ It narrows and never closes. The middle tier is not readable from outside at any depth,
     * which is what {@code docs/design/01-core-resources.md} §6 buys with the tier; a band that
     * reached zero would hand over a count and make the tier a formality.
     */
    public int deepScans = 0;
}
