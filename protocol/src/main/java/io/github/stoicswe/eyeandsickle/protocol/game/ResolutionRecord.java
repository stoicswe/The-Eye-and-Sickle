package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * What a breach attempt leaves behind for the rest of the economy to read.
 *
 * <p>Exactly the four fields {@code docs/design/05-hacking-minigame.md} §2 specifies —
 * {@code {puzzleClass, difficultyTier, liveOrDormant, outcome}} — and the glossary names it
 * {@code resolutionRecord}. §2 is explicit that this is the stable API: "Everything the economy needs
 * is in that record. Build it early even if the puzzle content is still churning." The puzzle's
 * <em>content</em> is a proposal; this shape is what the proposal was written around.
 *
 * <p>Persisted server-side as {@code breach_resolutions} ({@code docs/architecture/06-data-model.md}
 * §2), which adds the player and a timestamp. Those are storage concerns; the record itself describes
 * one attempt, not who made it.
 *
 * <h2>Tier-gated, never count-gated</h2>
 *
 * Two systems read these records, and both read them the same way:
 *
 * <ul>
 *   <li><strong>Proof-of-skill unlocks</strong> ({@code docs/design/02-unlock-gates.md} §2.4,
 *       Invariant I7) — the automation tool for a puzzle class unlocks on having solved that class at
 *       or above a threshold tier against a <em>live</em> target. Never "solve it N times".
 *       Count-gating rewards patience and invites farming the weakest target available; tier-gating
 *       rewards competence.
 *   <li><strong>The bot-salvage guard</strong> ({@code docs/design/10-botnets.md}, Invariant I13) —
 *       partial-progress salvage from a lost bot is gated on the engagement tier, for the same
 *       anti-farming reason.
 * </ul>
 *
 * So: counting these records is always the wrong query. If code anywhere ever reaches for
 * {@code count(*)} over them, that is the exploit arriving.
 *
 * <h2>Why there is no {@code isProofOfSkillEligible()} here</h2>
 *
 * It is tempting — {@code outcome == BREACHED && liveOrDormant == LIVE} is right there, and it would
 * let the client grey out an unlock without a round trip. It is also the first half of a gate check
 * living in the module whose charter forbids gate evaluation, and the second half (the per-class
 * threshold tier) would follow within a release, "just so the tooltip is accurate". The client asks
 * the server what is unlocked. That is the whole discipline (Invariant I14).
 *
 * @param puzzleClass which kind of puzzle was solved, failed or abandoned
 * @param difficultyTier how hard it was, on the shared scale both gate systems read
 * @param liveOrDormant whether the target was defended; only {@link TargetState#LIVE} can earn
 *     proof-of-skill credit
 * @param outcome how it ended
 */
public record ResolutionRecord(
        PuzzleClass puzzleClass, DifficultyTier difficultyTier, TargetState liveOrDormant, BreachOutcome outcome) {

    public ResolutionRecord {
        Objects.requireNonNull(puzzleClass, "puzzleClass");
        Objects.requireNonNull(difficultyTier, "difficultyTier");
        Objects.requireNonNull(liveOrDormant, "liveOrDormant");
        Objects.requireNonNull(outcome, "outcome");

        // No cross-field validation, deliberately. A failed attempt against a dormant target is a
        // perfectly valid record: this is a log of what happened, not a certificate of achievement.
        // Rejecting the boring cases would mean the server could only persist wins, and the trace
        // history that makes an audit or an anti-cheat review possible would be missing exactly the
        // rows an investigator wants.
    }
}
