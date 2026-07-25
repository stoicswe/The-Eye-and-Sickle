package io.github.stoicswe.eyeandsickle.server.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default {@link PlayerSessionStore}: sessions in a concurrent map, keyed by token.
 *
 * <h2>Fit for the deployment shape</h2>
 *
 * A home server is one process serving an allowlist-bounded population ({@code
 * docs/architecture/01-tech-stack.md} §2), so an in-memory map is the right amount of machinery: no
 * serialization, no external dependency, and a lookup that is a hash probe. The cost is that sessions
 * do not survive a restart and are not shared across instances — both acceptable here, and both the
 * reason {@link PlayerSessionStore} is an interface rather than this being the only option.
 *
 * <h2>Token generation</h2>
 *
 * Tokens are 256 bits from {@link SecureRandom}, URL-safe Base64 without padding. That is far beyond
 * guessing range and carries no structure — nothing about the player's DID or id is derivable from a
 * token, so a leaked token discloses only itself. The randomness source is a single shared
 * {@code SecureRandom}, which is thread-safe.
 *
 * <h2>Time comes from a {@link Clock}</h2>
 *
 * Expiry is computed against an injected clock, not {@code Instant.now()}, so a test can issue a
 * session and then step past its expiry deterministically rather than sleeping. The same clock decides
 * issue time and resolve-time expiry, so the two can never disagree about "now".
 */
@org.springframework.stereotype.Component
public final class InMemoryPlayerSessionStore implements PlayerSessionStore {

    private static final int TOKEN_BYTES = 32;

    private final ConcurrentHashMap<String, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Clock clock;

    /**
     * @param clock the source of "now" for issue and expiry decisions
     */
    public InMemoryPlayerSessionStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PlayerSession create(Player player, Duration ttl) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("session ttl must be positive, was " + ttl);
        }
        // A session authenticates a specific DID, so a player with no DID (local-only solo play) cannot
        // hold one. Catching it here turns a would-be NullPointerException in the token principal into a
        // clear statement of the rule.
        if (player.did() == null) {
            throw new IllegalArgumentException(
                    "Cannot open a session for a player with no DID; sign-in sessions are for authenticated identities");
        }
        Instant now = clock.instant();
        // player.did() is non-null here (checked above), so characterDid() is non-null too — the session
        // carries the selected character as its actor alongside the account identity (09 §9).
        PlayerSession session = new PlayerSession(
                newToken(),
                player.playerId(),
                player.did(),
                player.characterDid(),
                player.handle(),
                now,
                now.plus(ttl));
        // Astronomically unlikely, but a collision would silently hijack a session, so refuse rather
        // than overwrite. putIfAbsent makes the check-and-insert atomic.
        if (sessions.putIfAbsent(session.token(), session) != null) {
            throw new IllegalStateException("token collision; refusing to overwrite an existing session");
        }
        return session;
    }

    @Override
    public Optional<PlayerSession> resolve(String token) {
        if (token == null) {
            return Optional.empty();
        }
        PlayerSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(clock.instant())) {
            // Evict on read so an abandoned expired session does not linger forever. remove(key, value)
            // is conditional, so a session refreshed under us is not dropped by mistake.
            sessions.remove(token, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return tokenEncoder.encodeToString(bytes);
    }
}
