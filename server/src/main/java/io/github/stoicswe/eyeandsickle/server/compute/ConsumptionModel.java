package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;

/**
 * How a consumer's cycles behave over time — the distinction {@code
 * docs/design/01-core-resources.md} §1.1 draws between the two consumption models that share one
 * ledger.
 *
 * <p>The compute table lists both kinds without labelling them, and the difference decides which
 * lifecycle operation is legal for a given allocation:
 *
 * <ul>
 *   <li>a {@link #RESERVATION} is held for as long as the thing it powers runs and is handed back
 *       whole the instant that thing stops — {@link ComputeLedgerService#release}. It never enters the
 *       recovering state, because nothing was <em>spent</em>; the cycles were merely occupied.
 *   <li>a {@link #PER_USE} charge is spent on a discrete action and then sits in {@link
 *       io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation.State#RECOVERING} while the
 *       Thermal Budget returns it on a curve (§1.3) — {@link ComputeLedgerService#spend}.
 * </ul>
 *
 * <p>Getting this classification wrong is not cosmetic: letting a bot frame "recover" would hand its
 * reserved cycles back while the bot is still running, and letting a scan be "released" would skip the
 * Thermal Budget penalty that gives scanning a real opportunity cost ({@code
 * docs/design/04-mining.md} §3.2). The classification is therefore centralised here rather than
 * re-decided at each call site.
 */
enum ConsumptionModel {

    /** Held while the powered thing runs; returned whole on release. Never recovers. */
    RESERVATION,

    /** Spent on a discrete action; returned over time on the Thermal Budget curve. */
    PER_USE;

    /**
     * The model that governs a given consumer.
     *
     * <p>The mapping follows the two models named in {@code docs/design/01-core-resources.md} §1.1.
     * {@code SELF_MINING}, {@code BOT_FRAME}, {@code CONTROL_CHANNEL}, {@code DEFENSIVE_ARRAY} and the
     * host-side {@code DEPLOYED_MINER} are all "permanent reservation while running". {@code
     * ACTIVE_TOOL} and {@code RELAY_HOP} are the per-use charges — a tool used per-action, a relay hop
     * paid per session — that recover.
     *
     * <p>The switch is exhaustive so a new consumer cannot be added to the protocol enum without a
     * compile error here forcing an explicit decision about how its cycles behave.
     *
     * @param consumer the consumer
     * @return its consumption model
     */
    static ConsumptionModel of(ComputeConsumer consumer) {
        return switch (consumer) {
            // ⚠ SHELL_SESSION is a RESERVATION and specifically NOT per-use, which is the same call
            // SessionRules.close makes on the solo side and has to stay the same on both. Recovery is
            // the price of having driven the silicon hard (§1.3); an idle shell has driven nothing, so
            // charging recovery on close would mean shutting a window opened by mistake cost real
            // capacity for real minutes — which teaches players to leave sessions open, the exact
            // opposite of what the hold is for.
            case SELF_MINING, BOT_FRAME, CONTROL_CHANNEL, DEFENSIVE_ARRAY, DEPLOYED_MINER,
                    SHELL_SESSION -> RESERVATION;
            // ACTIVE_TOOL covers the scans of docs/design/04 §3.2; RELAY_HOP is paid per session and
            // then recovers. Both are the discrete-action charges §1.3 is written about.
            case ACTIVE_TOOL, RELAY_HOP -> PER_USE;
        };
    }
}
