package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Something the client asks the server to do.
 *
 * <h2>⚠ SEALED, and that is a security property rather than a style choice</h2>
 *
 * An open hierarchy means a variant the server does not know how to refuse. The safe default for an
 * unrecognised intent is refusal, and only a closed set makes "every variant is handled" something a
 * compiler can check — a {@code switch} over a sealed type fails to build when a case is missing,
 * where an {@code instanceof} chain silently falls through to whatever the last branch was.
 *
 * <h2>⚠ Intent, never outcome</h2>
 *
 * A variant says what the player <em>wants</em>. It never carries a result, a new balance, or a
 * computed cost — those are the server's to decide, and a client that could state them would be
 * authoritative over exactly what <b>I14</b> says it must not be. If a field here would let a
 * cheating client gain something by lying about it, the field belongs on the server's side of the
 * call.
 *
 * <p>⚠ Note what is absent: there is no {@code SetBalance}, no {@code GrantItem}, no
 * {@code CompleteBreach}. The verbs are the ones a player performs, not the state changes they cause.
 */
public sealed interface GameIntent {

    /**
     * Allocate cycles to self-mining, or release them.
     *
     * <p>⚠ The server decides whether the rig has them. {@code cycles} is a request, and a client
     * asking for more than it holds gets a refusal rather than the cycles.
     *
     * @param cycles the target allocation, not a delta — ⚠ absolute so a dropped or duplicated
     *     request cannot accumulate, which a delta would
     */
    record AllocateSelfMining(long cycles) implements GameIntent {}

    /**
     * Switch mining between solo and a pool.
     *
     * @param mode the requested mode
     * @param pool the pool, when {@code mode} needs one
     */
    record SetMiningMode(MiningMode mode, MiningPool pool) implements GameIntent {}
}
