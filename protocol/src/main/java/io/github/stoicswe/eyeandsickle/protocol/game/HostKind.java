package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * What a machine is, as far as recon has established.
 *
 * <p>{@link #UNKNOWN} is the honest default and the most important constant here. A network sweep sells
 * <em>existence and adjacency</em>; it does not sell identity. {@code docs/design/07-recon-tools.md} §1
 * prices identity separately — the Passive Sniffer "reveals adjacent node types without touching them"
 * for 15 EC — so a sweep that named types would delete a purchased tool at the point of rendering, which
 * is {@code docs/design/02-unlock-gates.md} §5's pricing check failing rather than a cosmetic shortcut.
 *
 * <p>Four recon products, four gates, no overlap: the sweep sells <em>that something is there</em>, the
 * Passive Sniffer sells <em>what it is</em> (this enum leaving {@link #UNKNOWN}), the Traffic Analyzer
 * sells <em>live versus dormant</em> ({@link TargetState}), and the Honeypot Detector sells <em>traps,
 * with residual doubt</em> ({@link Sighting#honeypotSuspected()}). If any one of them started answering
 * another's question, the ladder {@code 07} §3 describes would collapse into its cheapest rung.
 *
 * <h2>Two of these are structural rather than observed</h2>
 *
 * {@link #GATEWAY} and {@link #BRIDGE} are decided by a machine's <em>position</em> in the generated
 * world — a server has exactly one entry host, and a cross-server link needs a host at each end — where
 * the rest describe what a machine is for. They still reach the player through the same
 * {@link #UNKNOWN}-until-identified path as everything else: a bridge the player has not typed is a
 * contact like any other, and only once it is identified does it advertise the server on its far side.
 *
 * <h2>Why {@link #SELF} is in the same enum</h2>
 *
 * The player's own rig is drawn on the same graph as everything else, is the default vantage, and is the
 * one node that is never a target. Giving it a separate type would mean every renderer and every layout
 * pass carried a two-case union for a node that occupies one cell like the rest; giving it a kind means
 * the map's node cell has exactly one shape. It is deliberately <em>not</em> a value the generator can
 * roll — nothing but the player's rig may ever carry it.
 */
public enum HostKind {

    /**
     * Detected, but not typed. The state a sweep leaves every machine in, and the state the Passive
     * Sniffer is sold to change ({@code docs/design/07-recon-tools.md} §1).
     *
     * <p>It is not the same as undiscovered. An undiscovered machine has no {@link Sighting} at all —
     * see {@link NetMap}. This is "something is there and I do not know what", which is the honest
     * reading and a legitimate thing to draw.
     */
    UNKNOWN,

    /** A citizen's or clerk's desktop. The bread-and-butter low-level machine, and the early game's income. */
    TERMINAL,

    /** Routing hardware. Well connected and low value — worth taking as a vantage rather than for the loot. */
    RELAY,

    /** A file store. The kind that can be carrying something worth reading as well as something worth taking. */
    STORE,

    /**
     * Defended infrastructure. The shape {@code docs/design/14-world-and-narrative.md} §4 ([PROPOSAL])
     * describes as "new defended infrastructure appearing on the graph" as the world escalates.
     */
    SENTRY,

    /** The cross-server link. A real, traversable machine that advertises the server on its far side. */
    BRIDGE,

    /** A server's entry host. Exactly one per server: a signpost, not a prize. */
    GATEWAY,

    /** The player's own rig. Never a target, never generated, and the default vantage. */
    SELF
}
