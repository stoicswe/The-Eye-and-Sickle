package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The persisted {@code resolutionRecord} from {@code docs/design/05-hacking-minigame.md} §2 — what a
 * breach attempt leaves behind for the rest of the economy to read.
 *
 * <p>The first four fields are exactly {@link
 * io.github.stoicswe.eyeandsickle.protocol.game.ResolutionRecord}'s, in the save's string-and-int
 * dialect. The rest is local: a timestamp, telemetry, and one field ({@link #classesCleared}) that
 * exists because a multi-layer attempt has more than one class and the wire record has room for one.
 *
 * <h2>⚠ Counting these rows is always the wrong query</h2>
 *
 * Both systems that read them — proof-of-skill unlocks ({@code docs/design/02-unlock-gates.md} §2.4,
 * Invariant I7) and the bot-salvage guard ({@code docs/design/10-botnets.md} §1a, Invariant I13) —
 * ask "highest tier solved against a live target", never "how many". {@code ResolutionRecord}'s own
 * javadoc puts it plainly: if code anywhere reaches for a count over these, that is the exploit
 * arriving. {@link io.github.stoicswe.eyeandsickle.engine.rules.SalvageRules} reads
 * {@link #difficultyTier} and nothing else, deliberately.
 *
 * <p>Failed and aborted attempts are recorded too. This is a log of what happened, not a
 * certificate of achievement, and a history that contains only wins is missing exactly the rows an
 * investigation would want.
 */
public final class ResolutionState {

    public String resolutionId = UUID.randomUUID().toString();

    /**
     * {@code PuzzleClass.name()} of the layer where the attempt <em>ended</em>.
     *
     * <p>On a breach that succeeded that is the deepest layer; on one that failed or was abandoned
     * it is the layer that stopped the player. One record, one class, because that is the shape §2
     * fixed and the shape the server persists.
     *
     * <p>⚠ A multi-class attempt therefore names one class here and lists the rest in
     * {@link #classesCleared}. See {@code docs/design/16-breach-implementation.md} §7 (BR-1): a
     * proof-of-skill implementation that reads only this field will under-credit a player who
     * cleared three classes in one attempt, and the data it needs is in the other field rather than
     * in extra rows — extra rows would be a countable artefact, which is the thing I7 forbids.
     */
    public String puzzleClass = "ENUMERATION";

    public int difficultyTier = 1;

    /**
     * Which target this attempt was against — {@code "node:<address>"} or {@code "crack:<minerId>"}.
     *
     * <p>Local, and not part of the wire record: {@code ResolutionRecord} is deliberately
     * target-agnostic, because a server that persisted who you hit alongside what you solved would be
     * publishing a target list nobody asked for.
     *
     * <p>It exists so {@code NetRules.reconcileFootholds} can find out which hosts a breach actually
     * took, without the breach engine learning that a network topology exists. That is the whole
     * seam: {@code BreachRules} writes one field and knows nothing else about it, and the network
     * rules read this list on resume and after every attempt, granting a foothold and the host's
     * one-time payout for each {@code BREACHED} row. Idempotent by construction — a looted host is
     * never looted twice — so replaying the whole list on every load is correct rather than merely
     * cheap.
     *
     * <p>Empty on rows written before this field existed. Those attempts predate the topology
     * entirely, so there is nothing for them to have taken.
     */
    public String targetId = "";

    /** {@code TargetState.name()}. Only {@code LIVE} can earn proof-of-skill credit ({@code 02} §2.4). */
    public String liveOrDormant = "DORMANT";

    /** {@code BreachOutcome.name()} — {@code BREACHED}, {@code FAILED} or {@code ABORTED}. */
    public String outcome = "ABORTED";

    /**
     * When it resolved.
     *
     * <p>⚠ Set from the session clock, never {@code Instant.now()}. The engine reading the wall
     * clock behind its caller's back is the failure {@code ComputeRules.spend} carries a warning
     * about and {@code RunningTask} shipped once: invisible in production, where the two clocks
     * agree, and wrong under every test.
     */
    public Instant at = Instant.EPOCH;

    /**
     * Total probes and loud-tool volleys across the whole attempt.
     *
     * <p>Local telemetry, not part of the wire record. This is the denominator of <b>P-3</b>
     * ({@code docs/design/05-hacking-minigame.md} §6) — "how much does manual play beat bot play",
     * the number behind Invariant I10, which §4 made answerable by denominating the gap in probes
     * rather than seconds. The Traversal decoy step is built so a fixed heuristic must extract at
     * random among K candidates while a reader gets it in one; the difference shows up here.
     */
    public int probesUsed = 0;

    /**
     * Every class the player actually cleared in this attempt, oldest layer first.
     *
     * <p>Local telemetry alongside {@link #probesUsed}, and the answer to the gap {@link
     * #puzzleClass} leaves. Empty on a failed or aborted attempt that cleared nothing.
     */
    public List<String> classesCleared = new ArrayList<>();

    public ResolutionState() {}
}
