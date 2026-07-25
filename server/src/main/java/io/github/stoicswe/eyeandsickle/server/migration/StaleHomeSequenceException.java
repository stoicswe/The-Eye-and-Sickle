package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;

/**
 * A migration bundle presented a home-binding sequence that is not newer than the one the character
 * directory already recognizes — a rollback or a replay
 * ({@code docs/architecture/09-player-state-portability.md} §6.1, "No rollback / no fork").
 *
 * <h2>Why monotonicity is the whole anti-fork rule</h2>
 *
 * A character has exactly one authoritative home, and a home change advances the directory's monotonic
 * sequence (§4). Presenting an <em>older</em> bundle — the same bundle twice, or a stale copy from before
 * a previous move — is how an attacker would try to resurrect a character that has already moved on, or
 * fork it into two live homes. Refusing any sequence that does not strictly advance is exactly the
 * monotonicity the discovery descriptors enforce, applied to character homes.
 *
 * <p>Maps to {@code 409 Conflict}: the request is well-formed, but the character's home has already moved
 * past the state the bundle describes.
 */
public class StaleHomeSequenceException extends RuntimeException {

    /**
     * @param character the character whose home binding was presented stale
     * @param presentedSequence the sequence the bundle carried
     * @param recognizedSequence the sequence the directory already recognizes (which the presented one
     *     failed to exceed)
     */
    public StaleHomeSequenceException(CharacterRef character, long presentedSequence, long recognizedSequence) {
        super("Migration for character " + character.characterId() + " (slot " + character.slot()
                + ") presented home sequence " + presentedSequence + ", but the directory already recognizes "
                + recognizedSequence + "; a home binding only advances, so this is a rollback or a replay "
                + "(docs/architecture/09-player-state-portability.md §6.1).");
    }
}
