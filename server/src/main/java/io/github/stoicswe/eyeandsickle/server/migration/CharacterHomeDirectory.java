package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import java.util.UUID;

/**
 * The narrow seam onto the signed <strong>character directory</strong> (Option E,
 * {@code docs/architecture/09-player-state-portability.md} §4) that this migration slice depends on.
 *
 * <h2>Why this is an interface owned here, not a call into the directory slice</h2>
 *
 * The directory — {@code DID -> [{characterId, slot, homeServerDid, homeEndpoint}]}, each entry a signed,
 * monotonic-sequence record (§4) — is built by another slice, concurrently. Rather than reach into it,
 * this slice declares the two directory operations migration needs and depends only on them; the
 * directory slice contributes the real, persistent, cross-federation implementation and supersedes the
 * default {@link InMemoryCharacterHomeDirectory} via {@code @ConditionalOnMissingBean}. That keeps the
 * two features decoupled and honours "no single arbiter" (Invariant I15): this seam observes and advances
 * a gossiped binding, it does not adjudicate one.
 *
 * <h2>The one property migration relies on: monotonicity (§6.1)</h2>
 *
 * A character's home binding only ever advances. Reading {@link #currentSequence} tells an export which
 * sequence to stamp on the bundle; {@link #advanceHomeToLocal} moves the binding to this server at a
 * strictly greater sequence and <strong>refuses anything that does not advance</strong>. That single
 * refusal is the whole of "no rollback / no fork": a replayed or stale bundle cannot re-home a character
 * that has already moved on.
 */
public interface CharacterHomeDirectory {

    /**
     * The sequence the directory currently recognizes for a character homed on this server — what an
     * export stamps onto the bundle so the destination knows what it must advance past.
     *
     * @param accountDid the account the character belongs to
     * @param character the character, referenced at this (source) home
     * @return the current recognized home-binding sequence, or {@code 0} if the directory holds none yet
     */
    long currentSequence(Did accountDid, CharacterRef character);

    /**
     * Advances a character's home binding to <em>this</em> server, at a sequence strictly greater than the
     * one the migration bundle presented — the destination-side step of a move (§4).
     *
     * <p>This is where "no rollback / no fork" is enforced (§6.1): if the presented sequence is not newer
     * than the sequence the directory already recognizes, the binding does not move and a {@link
     * StaleHomeSequenceException} is thrown. On success the character is now recognized as homed here, at
     * the returned (advanced) sequence, under its <em>new</em> id.
     *
     * @param accountDid the account the character belongs to
     * @param sourceCharacter the character as named at its source home (the bundle's reference)
     * @param newCharacterId the fresh character id minted at this destination home
     * @param presentedSequence the source home-binding sequence the bundle carried
     * @return the new, advanced sequence now bound to this server
     * @throws StaleHomeSequenceException if {@code presentedSequence} does not strictly advance the
     *     recognized binding — a replay or a rollback
     */
    long advanceHomeToLocal(Did accountDid, CharacterRef sourceCharacter, UUID newCharacterId, long presentedSequence);
}
