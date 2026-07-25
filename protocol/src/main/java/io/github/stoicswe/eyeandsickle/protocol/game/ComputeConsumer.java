package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * What a rig's cycles are going to.
 *
 * <p>From the consumer table in {@code docs/design/01-core-resources.md} §1.1. This is a wire type
 * rather than a server-internal detail for one reason: §1.4 makes the <em>by-consumer</em> breakdown
 * a mandatory, always-visible HUD element — "the compute ledger is the game's most important HUD
 * element" — and a client cannot render a breakdown whose categories it cannot name.
 *
 * <p>The categories are here; the <em>amounts</em> never are. How many cycles a control channel
 * reserves, what a scan costs, how fast spent cycles come back — all balance values, all server-side.
 * The client is told the numbers, it does not know them.
 *
 * <p>Two consumption models coexist in this list, and the distinction is worth keeping in mind when
 * reading a {@link ComputeAllocation}: some consumers hold a <em>reservation</em> for as long as the
 * thing they power runs, others charge <em>per use</em> and then sit in {@link
 * ComputeAllocation.State#RECOVERING} while the Thermal Budget returns the cycles (§1.3).
 *
 * <p>These constants correspond to the {@code compute_allocations.consumer_type} enum proposed in
 * {@code docs/architecture/06-data-model.md} §2.
 */
public enum ComputeConsumer {

    /**
     * An active tool: per-use, or reserved while equipped ({@code docs/design/06-intrusion-tools.md},
     * {@code docs/design/07-recon-tools.md}).
     */
    ACTIVE_TOOL,

    /**
     * A bot frame. Permanent reservation while the bot is running ({@code
     * docs/design/10-botnets.md}) — which is what makes botnet size a compute decision rather than a
     * free multiplier.
     */
    BOT_FRAME,

    /**
     * Self-mining on the player's own rig ({@code docs/design/04-mining.md} §1): whatever the player
     * chooses to allocate.
     *
     * <p>This is the income floor, and it is structurally immune to detection and seizure (Invariant
     * I4). Its <em>entire</em> cost is the compute it occupies and the slower rate at which that
     * compute returns — so this row of the HUD is the one that shows the player what safety costs.
     */
    SELF_MINING,

    /**
     * The control channel the <em>deployer</em> holds open for each live deployed miner ({@code
     * docs/design/04-mining.md} §2). A permanent reservation on the deployer's own rig while the
     * miner runs.
     *
     * <p>This is the deployer's half of Invariant I6. The miner's draw on the <em>host</em> is
     * {@link #DEPLOYED_MINER}, charged to a different rig entirely, and the two must never be summed
     * into one number. The self-correcting network cap (§2.2) is nothing but this reservation
     * accumulating until the deployer is defenceless — so do not let anything make it cheap.
     */
    CONTROL_CHANNEL,

    /**
     * A foreign deployed miner running on <em>this</em> rig and stealing its cycles.
     *
     * <p><strong>[PROPOSAL] — needs a design ruling.</strong> §1.1's table lists the consumers on a
     * player's own rig and does not name this one, yet Invariant I6 ("a deployed miner consumes the
     * host's compute, not the deployer's") requires the host's ledger to be able to attribute those
     * stolen cycles, and {@code docs/architecture/06-data-model.md} §1 constraint 4 requires
     * <em>every</em> allocation to be attributable so manual-audit gameplay works against real data.
     * Without a name for it, a discovered parasite is unrepresentable. Recorded in {@code
     * docs/design/15-open-questions.md} if adopted.
     *
     * <p>Note what naming it does <em>not</em> do: a rootkit-wrapped miner ({@code
     * docs/design/09-defense-and-hardening.md}) is hidden precisely by <em>not</em> appearing in the
     * host's readout at all. What the server chooses to disclose is the server's decision; see
     * {@link ComputeBudget#unaccountedFor()} for the signal that remains when it discloses nothing.
     */
    DEPLOYED_MINER,

    /**
     * A defensive detection sweep or array. Permanent reservation while armed ({@code
     * docs/design/09-defense-and-hardening.md}). Defending your own rig never generates heat
     * (Invariant I9); it costs cycles instead, and this is where that shows up.
     */
    DEFENSIVE_ARRAY,

    /**
     * One hop of a relay chain, per session ({@code docs/design/08-stealth-and-noise.md}) — the
     * compute half of anonymity's price, the ethecoin half being the per-hop fee.
     */
    RELAY_HOP
}
