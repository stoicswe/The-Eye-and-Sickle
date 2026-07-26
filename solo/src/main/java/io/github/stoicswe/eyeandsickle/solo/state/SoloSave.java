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
}
