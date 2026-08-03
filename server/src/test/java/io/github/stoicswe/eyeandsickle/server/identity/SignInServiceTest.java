package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The sign-in flow. The order — authenticate, then gate, then enumerate the account's characters — <em>is</em>
 * the security property, so most of these tests assert that order holds under failure: the allowlist is
 * never consulted for an unauthenticated caller. Sign-in now yields an <em>account</em> and its playable
 * characters (09 §1), never a single auto-created player, and never a play token — that is minted per
 * character on selection.
 */
class SignInServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

    private static final Did REAL = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Did ATTACKER = Did.of("did:plc:eeeeeeeeeeeeeeeeeeeeeeee");

    private static SignInCredentials credentials(String claimedDid) {
        return new SignInCredentials("alice.bsky.social", claimedDid, null, null, null);
    }

    private static Player active(Did did, int slot, String handle) {
        return FakePlayerRepository.active(UUID.randomUUID(), did, slot, handle, NOW);
    }

    private static Player retired(Did did, int slot) {
        return new Player(
                UUID.randomUUID(),
                did,
                slot,
                "old",
                CharacterStatus.RETIRED,
                Faction.NONE,
                Heat.ZERO,
                Ethecoin.ZERO,
                NOW,
                NOW,
                0);
    }

    /** Wires a sign-in service around a configurable provider and a controllable allowlist. */
    private record Harness(
            SignInService service,
            FakeAtProtoIdentityProvider provider,
            FakeAllowlistRepository allowlist,
            FakePlayerRepository players) {}

    private static Harness harness(FakeAtProtoIdentityProvider provider, FakeAllowlistRepository allowlist) {
        // The no-op directory is what the server actually ships with, so the default harness is the
        // shipped configuration rather than a convenient one.
        return harness(provider, allowlist, VerifiedHandleDirectory.unresolved());
    }

    private static Harness harness(
            FakeAtProtoIdentityProvider provider, FakeAllowlistRepository allowlist, VerifiedHandleDirectory handles) {
        FakePlayerRepository players = new FakePlayerRepository();
        AllowlistPolicy policy = new AllowlistPolicy(allowlist, new AllowlistProperties(true, List.of()));
        SignInService service = new SignInService(provider, policy, players, handles);
        return new Harness(service, provider, allowlist, players);
    }

    /** A directory that verifies, and answers with whatever it was given. */
    private static VerifiedHandleDirectory verifying(String handle) {
        return did -> handle;
    }

    @Nested
    @DisplayName("the displayed handle")
    class Handles {

        @Test
        @DisplayName("with NO directory wired, the provider's handle is kept — the shipped state")
        void unwiredKeepsProviderHandle() {
            // canVerify() == false means "nobody has wired a resolver", which is not the same as
            // "nothing verified". Dropping the handle here would blank the display name on every
            // server that has not opted into resolution yet.
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "alice.bsky.social")),
                    new FakeAllowlistRepository().allow(REAL));

            assertThat(h.service().signIn(credentials(null)).handle()).isEqualTo("alice.bsky.social");
        }

        @Test
        @DisplayName("a verified handle REPLACES the provider's — handles are re-claimable")
        void verifiedHandleWins() {
            // The provider's handle can be stale: handles are released and re-claimed, so a cached
            // one eventually names a different person. Refreshing at sign-in is where that is fixed.
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "stale.bsky.social")),
                    new FakeAllowlistRepository().allow(REAL),
                    verifying("current.bsky.social"));

            assertThat(h.service().signIn(credentials(null)).handle()).isEqualTo("current.bsky.social");
        }

        @Test
        @DisplayName("⚠ when verification runs and FAILS, the handle is dropped — never fallen back to")
        void unverifiedHandleIsDroppedNotFallenBackTo() {
            // This is the assertion that decides whether the whole bidirectional check is real. An
            // attacker whose DID document claims at://a-rivals.handle fails verification; if sign-in
            // then fell back to the provider's unverified handle, they would be displayed as the
            // rival anyway and the check would be decorative. architecture/10 §4.1.
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "a-rivals.handle")),
                    new FakeAllowlistRepository().allow(REAL),
                    verifying(null));

            AccountSession account = h.service().signIn(credentials(null));

            assertThat(account.handle()).isNull();
            assertThat(account.did()).isEqualTo(REAL);
        }

        @Test
        @DisplayName("a failed handle check never fails the sign-in itself")
        void handleFailureDoesNotBlockSignIn() {
            // A handle is a display name; a DID is the identity. Locking somebody out of a game they
            // own characters in because their DNS is briefly wrong would be the worse failure.
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "alice.bsky.social")),
                    new FakeAllowlistRepository().allow(REAL),
                    verifying(null));

            assertThat(h.service().signIn(credentials(null))).isNotNull();
        }
    }

    @Nested
    @DisplayName("the happy path")
    class HappyPath {

        @Test
        @DisplayName("a brand-new account signs in with an empty roster — nothing is auto-created")
        void newAccountHasNoCharacters() {
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "alice.bsky.social")),
                    new FakeAllowlistRepository().allow(REAL));

            AccountSession account = h.service().signIn(credentials(null));

            assertThat(account.did()).isEqualTo(REAL);
            assertThat(account.handle()).isEqualTo("alice.bsky.social");
            assertThat(account.characters()).as("sign-in creates no character").isEmpty();
            // And nothing was written: the roster is read-only.
            assertThat(h.players().createCalls()).isEmpty();
        }

        @Test
        @DisplayName("an account's existing characters are listed for selection")
        void listsExistingCharacters() {
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "alice.bsky.social")),
                    new FakeAllowlistRepository().allow(REAL));
            h.players().put(active(REAL, 1, "alice.bsky.social")).put(active(REAL, 2, "alice.bsky.social"));

            AccountSession account = h.service().signIn(credentials(null));

            assertThat(account.characters()).hasSize(2);
            assertThat(account.characters()).allSatisfy(c -> assertThat(c.did()).isEqualTo(REAL));
        }

        @Test
        @DisplayName("only playable (active) characters are offered — migrated/retired shells are not choices")
        void excludesTerminalCharacters() {
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "alice.bsky.social")),
                    new FakeAllowlistRepository().allow(REAL));
            h.players().put(active(REAL, 1, "alice.bsky.social")).put(retired(REAL, 2));

            AccountSession account = h.service().signIn(credentials(null));

            assertThat(account.characters()).singleElement().satisfies(c -> {
                assertThat(c.slot()).isEqualTo(1);
                assertThat(c.status()).isEqualTo(CharacterStatus.ACTIVE);
            });
        }

        @Test
        @DisplayName("the account uses the PROVEN DID, never the one the client claimed")
        void usesResolvedNotClaimed() {
            // The type boundary in action: the client claims an attacker DID, the provider proves a
            // different real one, and everything downstream keys off the proven identity.
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, null)),
                    new FakeAllowlistRepository().allow(REAL));

            AccountSession account = h.service().signIn(credentials(ATTACKER.value()));

            assertThat(account.did()).isEqualTo(REAL);
        }

        @Test
        @DisplayName("a changed handle is reflected without disturbing the account — the DID is the key")
        void handleChangeKeepsAccount() {
            // The handle is display-only and may change (docs/architecture/02 §5). A returning sign-in with
            // a new handle reports the new handle and still resolves the same account's characters — the DID
            // is what maps to the account, not the handle.
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, "old.handle")),
                    new FakeAllowlistRepository().allow(REAL));
            h.players().put(active(REAL, 1, "old.handle"));

            AccountSession first = h.service().signIn(credentials(null));
            h.provider().nowReturns(new ResolvedIdentity(REAL, "new.handle"));
            AccountSession second = h.service().signIn(credentials(null));

            assertThat(first.did()).isEqualTo(second.did());
            assertThat(second.handle()).isEqualTo("new.handle");
            assertThat(second.characters()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("the gate")
    class Gate {

        @Test
        @DisplayName("an authenticated-but-unlisted DID is denied")
        void deniedIsRefused() {
            // "We believe you, and the answer is still no."
            Harness h = harness(
                    FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, null)),
                    new FakeAllowlistRepository()); // REAL not allowed

            assertThatThrownBy(() -> h.service().signIn(credentials(null))).isInstanceOf(SignInDeniedException.class);

            assertThat(h.allowlist().wasQueried())
                    .as("the gate runs, after authentication")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("ordering under failure")
    class OrderingUnderFailure {

        @Test
        @DisplayName("if authentication fails, the allowlist is never consulted")
        void authFailureNeverTouchesAllowlist() {
            Harness h = harness(
                    FakeAtProtoIdentityProvider.failing(new SignInException("could not verify")),
                    new FakeAllowlistRepository().allow(REAL));

            assertThatThrownBy(() -> h.service().signIn(credentials(REAL.value())))
                    .isInstanceOf(SignInException.class);

            assertThat(h.allowlist().wasQueried())
                    .as("the allowlist must not be read for an unauthenticated caller")
                    .isFalse();
        }

        @Test
        @DisplayName("an unavailable provider propagates without touching the gate")
        void unavailablePropagates() {
            Harness h = harness(
                    FakeAtProtoIdentityProvider.failing(new SignInUnavailableException("no provider wired")),
                    new FakeAllowlistRepository().allow(REAL));

            assertThatThrownBy(() -> h.service().signIn(credentials(REAL.value())))
                    .isInstanceOf(SignInUnavailableException.class);

            assertThat(h.allowlist().wasQueried()).isFalse();
        }
    }

    @Test
    @DisplayName("null credentials are a programming error")
    void nullCredentials() {
        Harness h = harness(
                FakeAtProtoIdentityProvider.returning(new ResolvedIdentity(REAL, null)),
                new FakeAllowlistRepository().allow(REAL));
        assertThatThrownBy(() -> h.service().signIn(null)).isInstanceOf(NullPointerException.class);
    }
}
