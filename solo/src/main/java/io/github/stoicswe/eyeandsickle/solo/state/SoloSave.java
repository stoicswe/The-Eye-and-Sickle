package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The root of a single-player save.
 *
 * <p>This is a plain mutable tree on purpose. It is the <em>serialization</em> shape, not the domain
 * model the UI sees: the engine reads it, applies rules, and hands the client immutable {@code
 * protocol} value types. Keeping the two apart means the save format can gain a field without every
 * screen in the client learning about it, and it means Jackson never has to be taught how to
 * construct a {@link io.github.stoicswe.eyeandsickle.protocol.game.Cycles}.
 *
 * <h2>This file is player-controlled infrastructure, and that is the whole point</h2>
 *
 * A player can open this file in a text editor and give themselves a thousand ethecoin. That is not a
 * vulnerability to be closed — it is a single-player game, the only person affected is the person
 * doing it, and every anti-tamper measure available here is theatre against an attacker who owns the
 * machine.
 *
 * <p>What matters is that this can never become <em>someone else's</em> problem. {@link #federable}
 * is permanently {@code false} for a locally-created character, and the client refuses to submit a
 * save-derived item to any federated server. Invariant I14 is preserved not by pretending this file
 * is trustworthy but by ensuring nothing downstream ever trusts it.
 */
public final class SoloSave {

    /**
     * Bumped whenever the shape changes incompatibly. A save from the future is refused rather than
     * silently half-read — see {@code SaveStore}.
     */
    public static final int CURRENT_FORMAT = 1;

    public int format = CURRENT_FORMAT;

    /** Stable id for this character. Not a DID: a solo character has no cryptographic identity. */
    public String characterId = UUID.randomUUID().toString();

    public String handle = "operator";
    public String faction = "NONE";

    /**
     * Always false for a save created locally, and the client must never offer to change it.
     *
     * <p>Kept as an explicit field rather than an implicit rule so that the refusal is greppable and
     * so a future migration path (a real DID-binding step, per {@code docs/architecture/02} §4) has
     * somewhere honest to write its answer.
     */
    public boolean federable = false;

    public Instant createdAt = Instant.now();
    public Instant lastPlayedAt = Instant.now();

    /** Total seconds of wall-clock play. Drives nothing mechanical; shown in {@code identity}. */
    public long playedSeconds = 0L;

    public RigState rig = new RigState();
    public long ethecoinMinorUnits = 0L;

    /** Long-horizon Eye attention. Distinct from noise, which decays and is not persisted. */
    public int personalHeat = 0;

    public int factionReputationEye = 0;
    public int factionReputationSickle = 0;

    public List<ItemState> items = new ArrayList<>();
    public List<LedgerEntryState> ledger = new ArrayList<>();
    public List<NodeState> knownNodes = new ArrayList<>();

    /**
     * The player's own filing of what they have discovered — see {@link FolderState}.
     *
     * <p>Sits beside {@link #knownNodes} rather than inside {@link #topology} on purpose. A folder is
     * not part of the world; it is an annotation <em>over</em> the world, it survives independently of
     * whether a topology has been generated yet, and putting it under a field that is null on an old
     * save would make the whole feature inaccessible for exactly the characters most likely to have
     * a long list of machines to file.
     */
    public List<FolderState> netFolders = new ArrayList<>();

    public List<DefenseState> defenses = new ArrayList<>();

    /**
     * Work with a wall-clock duration that is currently running.
     *
     * <p>Persisted, so a six-minute Thorough Scan survives quitting — see {@link TaskState}. A task
     * whose end has passed while the game was closed completes on the first tick after load, which
     * is the same catch-up path deployed-miner buffers already take.
     */
    public List<TaskState> tasks = new ArrayList<>();
    public List<String> schematics = new ArrayList<>();

    /** Terminal history, so `history` and Ctrl-R survive a restart the way a real shell's does. */
    public List<String> commandHistory = new ArrayList<>();

    /**
     * The rig's log, newest last.
     *
     * <p>Persisted, so `log` after a restart shows what happened before it — which is what a real
     * journal does and what makes the log usable for the thing it exists for: working out what
     * happened while you were not watching.
     *
     * <p>Capped at {@link #LOG_CAPACITY}. An uncapped log in a save file that is rewritten every
     * thirty seconds is an unbounded write amplification bug waiting for a long session.
     */
    public List<RigEvent> log = new ArrayList<>();

    /** Roughly a long session's worth. Old entries are dropped from the front. */
    public static final int LOG_CAPACITY = 500;

    // ------------------------------------------------------------------ the breach (design/05)

    /**
     * Seeded, persisted PRNG state — see {@link io.github.stoicswe.eyeandsickle.solo.breach.Rng} and
     * {@code docs/design/16-breach-implementation.md} §2.
     *
     * <p>Persisted because a draw that is not persisted is a draw the player can reroll by
     * reloading, and both things this engine draws — a breach board and a scan's false positive —
     * would become advisory if they were rerollable. The default is splitmix64's own golden-ratio
     * constant so that a save written before this field existed still has a usable, non-degenerate
     * seed rather than zero.
     *
     * <p>{@code SoloGame.newCharacter} overwrites it with {@code Rng.derive(characterId, now)}.
     */
    public long rngSeed = 0x9E3779B97F4A7C15L;

    /**
     * The breach in progress, or null.
     *
     * <p>Turn-based, so it needs no settlement — {@code docs/design/05-hacking-minigame.md} §4
     * removed the wall clock from the breach entirely. Nothing here has a deadline, so nothing here
     * can complete while the game is closed, so {@code resume()} and {@code tick()} have no work to
     * do on it. Contrast {@link #tasks}, two fields up, which exists for exactly the opposite case.
     */
    public BreachState activeBreach;

    /**
     * One row per breach attempt, oldest first — the persisted {@code resolutionRecord} from
     * {@code docs/design/05-hacking-minigame.md} §2.
     *
     * <p>⚠ <b>Never counted.</b> Both readers ask for the highest tier solved against a live target
     * — proof-of-skill ({@code 02} §2.4, Invariant I7) and the salvage guard ({@code 10} §1a,
     * Invariant I13). A count over this list rewards farming the softest target available, which is
     * the exact failure the gate rule exists to prevent; {@code ResolutionRecord}'s javadoc calls
     * reaching for one "the exploit arriving".
     */
    public List<ResolutionState> resolutions = new ArrayList<>();

    // ------------------------------------------------------------------ the network (design/17)

    /**
     * The generated world: virtual servers, their machines, and the links between them.
     *
     * <p>Written once by {@code TopologyGenerator.generate} and never regenerated — {@code NetRules}
     * treats a non-null value as final, and {@code generate} returns immediately when it finds one.
     *
     * <p>⚠ <b>A save written before this field existed is backfilled on load, in
     * {@code SoloGame.open}, and that is not the same thing as regenerating.</b> This javadoc used to
     * say the opposite — that an old character "keeps working with an empty map" — and the sentence
     * was wrong in the only way that matters: a null topology is not a small world, it is <em>no</em>
     * world, so {@code NetRules.view} returns {@link
     * io.github.stoicswe.eyeandsickle.protocol.game.NetMap#empty()} and {@code beginSweep} refuses
     * every sweep at every tier, forever, with no wording that could tell the player why. That is not
     * a character that keeps working; it is one whose entire network half is permanently dead.
     * Backfilling costs nothing (the world is rolled from the save's own persisted seed) and is the
     * only reading under which the pre-topology character can ever reach the feature.
     *
     * <p>⚠ {@link #CURRENT_FORMAT} is deliberately <b>not</b> bumped for this. {@code SaveStore}
     * refuses only saves whose format is <em>greater</em> than the build's, and Jackson leaves a
     * missing field at its initialiser — so a bump would refuse nothing and protect nothing, while
     * costing every existing save a compatibility scare.
     *
     * <p>⚠ It is by far the largest thing in this file — up to 350 hosts, rewritten on every
     * autosave. See {@link TopologyState}'s note on why nothing derived is cached inside it.
     */
    public TopologyState topology;

    /**
     * Generic schematic contribution material — {@code docs/design/02-unlock-gates.md} §2.2 and
     * {@code docs/design/10-botnets.md} §1a.
     *
     * <p>Partial progress toward schematic unlocks, gated on engagement tier (Invariant I13) so it
     * sets pace and never reach. See {@link io.github.stoicswe.eyeandsickle.solo.rules.SalvageRules}.
     */
    public int schematicMaterial = 0;
}
