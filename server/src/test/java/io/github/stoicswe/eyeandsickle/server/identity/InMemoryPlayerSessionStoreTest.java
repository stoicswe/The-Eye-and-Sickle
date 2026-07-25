package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The in-memory session store, with time supplied by a {@link MutableClock} so expiry is crossed by
 * moving the clock rather than sleeping. The interesting behaviour is at the edges: expiry is inclusive,
 * an expired token resolves exactly as a missing one, a player with no DID cannot hold a session, and
 * tokens are high-entropy and structureless.
 */
class InMemoryPlayerSessionStoreTest {

    private static final Instant T0 = Instant.parse("2026-07-24T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);
    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");

    private static Player player(Did did) {
        // A DID-bound character has a slot; only DID-bound characters ever hold a session.
        Integer slot = did == null ? null : 1;
        return new Player(
                UUID.randomUUID(),
                did,
                slot,
                "alice.bsky.social",
                CharacterStatus.ACTIVE,
                Faction.NONE,
                Heat.ZERO,
                Ethecoin.ZERO,
                T0,
                T0,
                0);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("a session begins now and expires exactly one TTL later, off the injected clock")
        void issuesBoundedSession() {
            MutableClock clock = MutableClock.at(T0);
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(clock);
            Player player = player(DID);

            PlayerSession session = store.create(player, TTL);

            assertThat(session.playerId()).isEqualTo(player.playerId());
            assertThat(session.did()).isEqualTo(DID);
            // The session carries the selected character as the actor game state keys on (09 §9).
            assertThat(session.characterDid()).isEqualTo(player.characterDid());
            assertThat(session.characterDid().accountDid()).isEqualTo(DID.value());
            assertThat(session.characterDid().slot()).isEqualTo(1);
            assertThat(session.handle()).isEqualTo("alice.bsky.social");
            assertThat(session.issuedAt()).isEqualTo(T0);
            assertThat(session.expiresAt()).isEqualTo(T0.plus(TTL));
        }

        @Test
        @DisplayName("a player with no DID cannot open a session — sessions are for authenticated identities")
        void noDidNoSession() {
            // A session authenticates a specific DID; a local-only player has none. Catching it here turns
            // a would-be NPE in the token principal into a clear statement of the rule.
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            assertThatThrownBy(() -> store.create(player(null), TTL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no DID");
        }

        @Test
        @DisplayName("a non-positive TTL is refused")
        void nonPositiveTtl() {
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            assertThatThrownBy(() -> store.create(player(DID), Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.create(player(DID), Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null player or TTL is a programming error")
        void nullArguments() {
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            assertThatThrownBy(() -> store.create(null, TTL)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> store.create(player(DID), null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("a token resolves to its session while unexpired")
        void resolvesWhileValid() {
            MutableClock clock = MutableClock.at(T0);
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(clock);
            PlayerSession session = store.create(player(DID), TTL);

            clock.advance(Duration.ofMinutes(59));
            assertThat(store.resolve(session.token())).contains(session);
        }

        @Test
        @DisplayName("at the expiry instant the token resolves to empty — the boundary is inclusive")
        void emptyAtExpiry() {
            // Defends the exact boundary: a session is not valid AT its expiry, so resolving one instant
            // early works and resolving on the instant does not.
            MutableClock clock = MutableClock.at(T0);
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(clock);
            PlayerSession session = store.create(player(DID), TTL);

            clock.set(T0.plus(TTL));
            assertThat(store.resolve(session.token())).isEmpty();
        }

        @Test
        @DisplayName("an expired token resolves exactly as a missing one, and stays empty on re-resolve")
        void expiredIsIndistinguishableFromMissing() {
            MutableClock clock = MutableClock.at(T0);
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(clock);
            PlayerSession session = store.create(player(DID), TTL);

            clock.set(T0.plus(TTL).plus(Duration.ofHours(1)));
            assertThat(store.resolve(session.token())).isEmpty();
            // Evicted on read; a second resolve is still empty (and would be even if the clock rewound,
            // because the entry is gone).
            assertThat(store.resolve(session.token())).isEmpty();
        }

        @Test
        @DisplayName("a null or unknown token resolves to empty, never an exception")
        void nullOrUnknownToken() {
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            assertThat(store.resolve(null)).isEmpty();
            assertThat(store.resolve("never-issued")).isEmpty();
        }
    }

    @Nested
    @DisplayName("invalidate")
    class Invalidate {

        @Test
        @DisplayName("invalidating a live token ends the session immediately")
        void endsSession() {
            // Sign-out and Ghost Protocol need immediate revocation, which is why the token is a handle
            // looked up here rather than a self-contained claim.
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            PlayerSession session = store.create(player(DID), TTL);

            store.invalidate(session.token());
            assertThat(store.resolve(session.token())).isEmpty();
        }

        @Test
        @DisplayName("invalidating an unknown or null token is a harmless no-op")
        void idempotent() {
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            store.invalidate("never-issued");
            store.invalidate(null);
            // Sign-out must not fail just because the session already ended.
        }
    }

    @Nested
    @DisplayName("tokens")
    class Tokens {

        @Test
        @DisplayName("a token is 256 bits of URL-safe Base64 with no padding and no structure")
        void tokenShape() {
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            String token = store.create(player(DID), TTL).token();

            // 32 bytes -> 43 unpadded Base64url characters. No '=', '+' or '/', and nothing derivable from
            // the player's identity — a leaked token discloses only itself.
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        }

        @Test
        @DisplayName("every issued token is distinct")
        void tokensAreDistinct() {
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            String a = store.create(player(DID), TTL).token();
            String b = store.create(player(DID), TTL).token();
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("two sessions for the same player coexist under different tokens")
        void multipleSessionsPerPlayer() {
            InMemoryPlayerSessionStore store = new InMemoryPlayerSessionStore(MutableClock.at(T0));
            Player player = player(DID);
            PlayerSession first = store.create(player, TTL);
            PlayerSession second = store.create(player, TTL);

            Optional<PlayerSession> resolvedFirst = store.resolve(first.token());
            Optional<PlayerSession> resolvedSecond = store.resolve(second.token());
            assertThat(resolvedFirst).contains(first);
            assertThat(resolvedSecond).contains(second);
        }
    }
}
