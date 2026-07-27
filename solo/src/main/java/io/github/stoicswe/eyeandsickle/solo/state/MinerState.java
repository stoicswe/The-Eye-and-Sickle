package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.UUID;

/**
 * A miner the player has deployed onto someone else's machine.
 *
 * <p>Two invariants live in these fields. The miner's work is charged to the <em>host</em>, never the
 * deployer (I6) — so {@link #hostCycles} is not subtracted from the player's rig, while the control
 * channel is. And it is the only source of offline income (I5), which is why {@link #bufferedMinorUnits}
 * accrues while the player is away and why it stops dead at the cap.
 */
public final class MinerState {

    public String minerId = UUID.randomUUID().toString();

    /** Cycles consumed on the host machine. Invariant I6: this is not the deployer's cost. */
    public long hostCycles = 8L;

    public Instant deployedAt = Instant.now();

    /** When yield was last swept into the buffer. Drives offline accrual on load. */
    public Instant lastAccruedAt = Instant.now();

    /** Yield sitting on the host, waiting to be collected. Capped — see {@code Balance}. */
    public long bufferedMinorUnits = 0L;

    /** Hidden from routine listings but not from a manual audit ({@code docs/design/09}). */
    public boolean rootkitWrapped = false;

    /**
     * Whether an audit has actually <b>named</b> this process — the flag the rig readout is gated on.
     *
     * <h2>⚠ Until this is true the rig monitor may not attribute a single cycle to it</h2>
     *
     * The compute grid gets its slices from {@code ComputeRules.snapshot}, which omits an undiscovered
     * parasite's allocation entirely. The cycles are still gone — the rig has less to give, work takes
     * longer, and a command that needs more than is left is refused — but nothing on screen says
     * <em>why</em>, because nothing in the fiction knows. A readout that labelled the theft the moment
     * it happened would hand the player the answer to the question the whole audit ladder in
     * {@code docs/design/04-mining.md} §3.2 exists to sell, at a price of zero.
     *
     * <p>What is left visible is the arithmetic, and that is the point rather than a consolation.
     * {@code §3.1} makes noticing that the numbers do not reconcile "the game's second-strongest
     * tutorial vector": claimed plus free plus recovering comes to less than the rig's ceiling, and
     * the missing cells are drawn dark and unlabelled. A player who adds up sees it; a player who
     * glances does not; nobody is told.
     *
     * <p>False on a save written before this field existed, which is the honest default — a parasite
     * from an older build has not been audited by this build's rules either.
     */
    public boolean discovered = false;

    /**
     * How this parasite hides in the process table — see {@code net.Disguise}.
     *
     * <h2>Chosen once, at plant time, and never re-rolled</h2>
     *
     * ⚠ A disguise that changed between repaints would be unfindable by construction: the player
     * would compare two readings of the same table, see two different lies, and correctly conclude
     * the table is noise. It is drawn from the persisted RNG when the miner is planted and then it is
     * a fact about that miner, like its tier.
     *
     * <p>Empty on a save written before disguises existed, which {@code Disguise.of} reads as the
     * plainest one — a parasite from an older build hides badly rather than not at all.
     */
    public String disguise = "";

    /**
     * The name it wears in the process table, decided with the disguise.
     *
     * <p>Stored rather than derived because two of the disguises copy something that can change: a
     * tool twin copies a tool the player was running <em>at the time</em>, and the player may never
     * run it again. Re-deriving would make the parasite rename itself the moment its cover story
     * stopped being true, which is the one thing a hidden process must never do.
     */
    public String disguiseName = "";

    /** The account it claims to run under. Part of the tell on two of the disguises. */
    public String disguiseUser = "";

    // ------------------------------------------------------------------ the crack (design/04 §5.1)

    /**
     * How hard this miner is to crack, on the shared 1–5 scale.
     *
     * <p>{@code docs/design/04-mining.md} §5.1: "Difficulty scales with miner tier, raised further
     * by Rootkit Wrapper (which gives that item a defensive-denial role)." Both halves are read by
     * {@code Targets.available}: the wrapper adds a tier, capped at the top of the scale.
     */
    public int tier = 1;

    /** What the readout calls it. Already in the operator's vocabulary before the crack starts. */
    public String label = "";

    /**
     * The {@code DEPLOYED_MINER} allocation this miner steals through, when it is running on the
     * player's own rig.
     *
     * <p>Empty for a miner the player deployed elsewhere — that one costs the <em>host</em>, and
     * the deployer pays a separate {@code CONTROL_CHANNEL} reservation instead (Invariant I6, and
     * the two must never be summed into one number).
     *
     * <p>Cracking or killing a foreign miner releases this allocation, which is what
     * {@code docs/design/04-mining.md} §5's "compute reclaimed" column means in the ledger.
     */
    public String allocationId = "";

    /**
     * Who planted it.
     *
     * <p>Load-bearing on a <em>failed</em> crack: {@code docs/design/04-mining.md} §5.1's dead-man
     * switch "flushes the buffer to the deployer immediately and the miner self-destructs", and
     * "the deployer is alerted with the host's handle attached — feeding bounty/retaliation
     * options". Without a deployer there is nobody for the buffer to go to and nobody to learn your
     * handle, and §5.1 is explicit that without that consequence "cracking would strictly dominate
     * killing".
     */
    public String deployerHandle = "";
}
