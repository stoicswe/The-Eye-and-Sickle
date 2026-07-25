package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;

/**
 * The single-server default {@link RecognizedCharacterCount}: it counts only this server's own active
 * characters for an account ({@code docs/architecture/09-player-state-portability.md} §2).
 *
 * <h2>The honest local answer, and its documented limit</h2>
 *
 * A server that does not consult the federation directory can only see the characters it hosts itself, so
 * that is exactly what this counts — DID-bound characters in the {@code active} state, on this server.
 * For a private, non-federating home server (the single-player or friends deployment) this <em>is</em>
 * the whole recognized set, and the cap it enforces is exact.
 *
 * <p>For a federating server it is the safe floor: it can never <em>under</em>-count this server's own
 * characters, so it will not let a single server hand one account more than {@code maxCharacters}
 * characters of its own. What it cannot see is characters the same account holds on <em>other</em>
 * servers — the directory-backed implementation (09 §4, the discovery slice) closes that gap and
 * supersedes this bean via {@code @ConditionalOnMissingBean}.
 */
public final class LocalRecognizedCharacterCount implements RecognizedCharacterCount {

    private final PlayerRepository players;

    /**
     * @param players the character table this server's count is read from
     */
    public LocalRecognizedCharacterCount(PlayerRepository players) {
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public int countRecognized(Did accountDid) {
        Objects.requireNonNull(accountDid, "accountDid");
        // count(*) of active rows fits an int comfortably — an account is capped at a handful of
        // characters — and Math.toIntExact turns a wildly-out-of-range value into a loud failure rather
        // than a silent wrap that could defeat the cap.
        return Math.toIntExact(players.countActiveCharacters(accountDid));
    }
}
