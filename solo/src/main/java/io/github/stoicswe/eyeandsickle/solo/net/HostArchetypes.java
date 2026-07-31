package io.github.stoicswe.eyeandsickle.solo.net;

import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import java.util.List;
import java.util.Locale;

/**
 * What each kind of machine <em>is</em>: how loud it is, whether it can carry a story fragment,
 * whether it is worth money, and what its network is called.
 *
 * <h2>Six archetypes, and each one exists because something needed it</h2>
 *
 * <ul>
 *   <li><b>{@code TERMINAL}</b> — a citizen's or clerk's desktop, quiet, lowest tier for its depth,
 *       loot only. This is the brief's "low-level NPC: easy to hack, no story content, a source of
 *       basic resources for buying early tools", and it is what the home floor guarantees a new
 *       player finds.
 *   <li><b>{@code RELAY}</b> — routing hardware. Loud, mid tier, cheap, and usually the best
 *       <em>vantage</em> on its server because the intra-server tree makes it a hub more often than
 *       not. It is the archetype that pays in position rather than in money.
 *   <li><b>{@code STORE}</b> — a file store; the ordinary document carrier.
 *   <li><b>{@code SENTRY}</b> — {@code docs/design/14} §4's "new defended infrastructure appearing on
 *       the graph". Highest tier for its depth, {@code defended} and {@code canaries} skewing high,
 *       and the other document carrier. A deep {@code SENTRY} is the game's set piece.
 *   <li><b>{@code GATEWAY}</b> — exactly one per server, host index 0, always loud. No loot and no
 *       documents: it is a signpost, and paying for it would make the cheapest thing on any server
 *       also a reward.
 *   <li><b>{@code BRIDGE}</b> — the cross-server link, loud, and the only host that advertises
 *       anything about the network on the far side. It pays loot at its tier but carries no document,
 *       because a fragment on the one host a player is guaranteed to visit would make the flavour
 *       layer compulsory — which decision N-4 forbids.
 * </ul>
 *
 * <h2>Signal is derived, never drawn</h2>
 *
 * {@code docs/design/04-mining.md} §2.1 publishes {@code Signal strength: Low / Moderate / High} for
 * miner tiers, and §3.3 makes it the thing a scan is buying. {@link #signalOf} generalises that from
 * miners to hosts, deterministically: infrastructure is chatty, stores and sentries are middling, a
 * desktop is quiet. There is deliberately no draw and deliberately no per-host {@code noise} field —
 * noise is a player-attribution scalar ({@code docs/design/01-core-resources.md} §3.2, "noise is what
 * reaches other machines"), and giving a machine one would quietly redefine the word for every other
 * system that reads it.
 */
public final class HostArchetypes {

    private HostArchetypes() {}

    /**
     * Names for the generated servers, indexed by position in the graph.
     *
     * <p>The first is always the player's home. They are short, lowercase, infrastructural, and
     * commit nothing narrative — decision N-4 keeps the world's story in recoverable fragments rather
     * than in the furniture, and a server called something evocative would be a narrative decision
     * made in code, which is what {@code CLAUDE.md} asks not to happen.
     *
     * <p>Fixed and index-addressed rather than shuffled, because shuffling would need a draw and the
     * generation sequence has no slot for one. The consequence is that the <em>set</em> of names is
     * the same on every seed while the shape, sizes and depths are not — which is the right trade:
     * nobody replays a world for its place names.
     */
    private static final List<String> SERVER_NAMES = List.of(
            "home-relay", "south-exchange", "north-yard", "west-depot", "east-annex", "lower-works", "outer-span");

    /** The name for the server at {@code index}, wrapping if the pool is ever outgrown. */
    public static String serverName(int index) {
        int i = Math.max(0, index);
        return i < SERVER_NAMES.size()
                ? SERVER_NAMES.get(i)
                : SERVER_NAMES.get(i % SERVER_NAMES.size()) + "-" + (i / SERVER_NAMES.size() + 1);
    }

    public static String serverId(int index) {
        return "srv-" + Math.max(0, index);
    }

    /**
     * A machine's name: {@code <server name>-<two-digit index>}.
     *
     * <p>⚠ It must not encode the host's kind. A label like {@code sentry-04} would name the type at
     * the moment a sweep discovered the machine, and naming types is the 15 EC Passive Sniffer's
     * entire published function ({@code docs/design/07-recon-tools.md} §1). Four recon products, four
     * gates, no overlap — and the sweep's product is existence and adjacency.
     */
    public static String hostLabel(String serverName, int index) {
        return serverName + "-" + String.format(Locale.ROOT, "%02d", Math.max(0, index));
    }

    /**
     * How loud this kind of machine is, before any miner running on it.
     *
     * @return a {@code SignalStrength.name()}
     */
    public static String baseSignal(String hostKind) {
        return switch (kindOrUnknown(hostKind)) {
            case GATEWAY, BRIDGE, RELAY -> SignalStrength.HIGH.name();
            case STORE, SENTRY -> SignalStrength.MODERATE.name();
            default -> SignalStrength.LOW.name();
        };
    }

    /**
     * The signal a sweep actually measures: the stored base, stepped up one level when the host is
     * carrying a deployed miner.
     *
     * <p>{@code docs/design/04-mining.md} §2.1 already says a bigger, more valuable miner is louder.
     * This generalises that to the machine underneath it, and the consequence is the correct one: a
     * host somebody is quietly earning from is easier to find than the same host idle. Capped at
     * {@code HIGH}, because there is nothing above it and a fourth level would need a row in §2.1.
     *
     * @param hostsMiner whether any miner is currently deployed on this host
     */
    public static SignalStrength signalOf(HostState host, boolean hostsMiner) {
        SignalStrength base = parseSignal(host == null ? null : host.signal);
        if (!hostsMiner) {
            return base;
        }
        return switch (base) {
            case LOW -> SignalStrength.MODERATE;
            default -> SignalStrength.HIGH;
        };
    }

    /**
     * Whether this kind of machine can carry a story fragment at all.
     *
     * <p>Stores and sentries only. A fragment on a {@code GATEWAY} or a {@code BRIDGE} would sit on
     * the one host a player crossing the network is guaranteed to touch, which would turn the
     * flavour layer into a checklist; a fragment on a {@code TERMINAL} would put the deep world's
     * documents on the shallowest archetype and undo the depth gradient the pull depends on.
     */
    public static boolean carriesDocuments(String hostKind) {
        HostKind kind = kindOrUnknown(hostKind);
        return kind == HostKind.STORE || kind == HostKind.SENTRY;
    }

    /**
     * Whether this kind of machine pays out at all.
     *
     * <p>Everything except the {@code GATEWAY}, which is a signpost. Its whole function is to be the
     * first thing found on a server — it is the loudest archetype and sits at host index 0 — and a
     * reward on the machine a sweep finds first would make the first sweep of every server also its
     * best one.
     */
    public static boolean carriesLoot(String hostKind) {
        return kindOrUnknown(hostKind) != HostKind.GATEWAY;
    }

    /**
     * Whether this kind sits one tier above its depth's roll.
     *
     * <p>Infrastructure is harder than the machines it serves: a gateway and a bridge are the two
     * hosts a player <em>must</em> get through to make progress across the world, so making them the
     * softest thing on their server would delete the cost of traversal. Clamped to the top of the
     * shared scale by the caller, and clamped again by the home floor, so home's gateway still lands
     * at tier 2 at worst.
     */
    public static boolean infrastructure(String hostKind) {
        HostKind kind = kindOrUnknown(hostKind);
        return kind == HostKind.GATEWAY || kind == HostKind.BRIDGE;
    }

    /** Parses a stored kind name, defaulting to {@code UNKNOWN} rather than throwing on a hand-edited save. */
    public static HostKind kindOrUnknown(String name) {
        if (name == null || name.isBlank()) {
            return HostKind.UNKNOWN;
        }
        try {
            return HostKind.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownConstant) {
            // A save that outlives the enum that wrote it must open. An unrecognised kind reads as
            // "recon has not established this", which is the honest default and the safe one.
            return HostKind.UNKNOWN;
        }
    }

    /** Parses a stored signal name, defaulting to {@code LOW} — the quietest, least generous reading. */
    public static SignalStrength parseSignal(String name) {
        if (name == null || name.isBlank()) {
            return SignalStrength.LOW;
        }
        try {
            return SignalStrength.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownConstant) {
            return SignalStrength.LOW;
        }
    }
}
