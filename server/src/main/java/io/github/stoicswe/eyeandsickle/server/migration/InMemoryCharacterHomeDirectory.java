package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The default {@link CharacterHomeDirectory}: an in-JVM monotonic guard, a stand-in for the signed,
 * gossiped character directory (Option E, §4) another slice owns.
 *
 * <h2>What it guarantees, and what it does not</h2>
 *
 * It enforces the one property migration must not do without — <strong>monotonicity</strong> (§6.1). For
 * each character it remembers the highest home-binding sequence it has advanced to; a bundle presenting a
 * sequence below that recognized value is a rollback or a replay and is refused. The advance is atomic per
 * character, so two concurrent imports of the same bundle cannot both win.
 *
 * <p>What it is <em>not</em> is federation-wide or durable: it holds no signatures, and its memory resets
 * with the process. That is exactly what the real directory-backed implementation adds — a signed,
 * persistent, cross-server binding — and it supersedes this via {@code @ConditionalOnMissingBean} with no
 * change to the migration services. Reported as a wiring seam, in keeping with the slice's other defaults.
 */
class InMemoryCharacterHomeDirectory implements CharacterHomeDirectory {

    /** Highest recognized sequence per {@code accountDid | sourceCharacterId}. */
    private final ConcurrentMap<String, Long> highestByCharacter = new ConcurrentHashMap<>();

    @Override
    public long currentSequence(Did accountDid, CharacterRef character) {
        return highestByCharacter.getOrDefault(key(accountDid, character), 0L);
    }

    @Override
    public long advanceHomeToLocal(
            Did accountDid, CharacterRef sourceCharacter, UUID newCharacterId, long presentedSequence) {
        // compute() is atomic per key, so a concurrent second import of the same bundle sees the sequence
        // already advanced and is refused rather than double-homing the character. A thrown exception
        // leaves the mapping unchanged.
        long[] advancedHolder = new long[1];
        highestByCharacter.compute(key(accountDid, sourceCharacter), (k, known) -> {
            long recognized = known == null ? 0L : known;
            if (presentedSequence < recognized) {
                throw new StaleHomeSequenceException(sourceCharacter, presentedSequence, recognized);
            }
            long advanced = Math.max(recognized, presentedSequence) + 1;
            advancedHolder[0] = advanced;
            return advanced;
        });
        return advancedHolder[0];
    }

    private static String key(Did accountDid, CharacterRef character) {
        return accountDid.value() + "|" + character.characterId();
    }
}
