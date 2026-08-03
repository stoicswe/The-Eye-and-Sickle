package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the online session's shape, and for the docked layout's coverage promise.
 *
 * <p>Neither needs a JavaFX toolkit, which is why they run here rather than being left untested until
 * somebody has a display.
 */
class RemoteGameSessionTest {

    private static RemoteGameSession session() {
        return new RemoteGameSession(URI.create("https://home.example"));
    }

    @Nested
    @DisplayName("the displayed identity")
    class Identity {

        @Test
        @DisplayName("is 'not signed in' before sign-in, never a borrowed solo handle")
        void signedOutSaysSo() {
            // The regression: connectOnline used to pass profile.settings().soloHandle, so the
            // OFFLINE character's name was displayed as the online identity forever — including in
            // the command-strip prompt. architecture/10 §2.
            assertThat(session().handle()).isEqualTo(RemoteGameSession.NOT_SIGNED_IN);
        }

        @Test
        @DisplayName("shows a verified handle when there is one")
        void verifiedHandleIsShown() {
            RemoteGameSession session = session();
            session.identify(new RemoteGameSession.SignedIn("did:plc:abc123", "operator.example", true));

            assertThat(session.handle()).isEqualTo("operator.example");
        }

        @Test
        @DisplayName("falls back to the DID rather than showing an UNVERIFIED handle")
        void unverifiedHandleIsNotShown() {
            // alsoKnownAs is self-asserted, so anyone can claim any handle in their own DID document.
            // A DID nobody can read is a smaller failure than a name somebody else asserted —
            // architecture/10 §4.1, and design/12 is why a display name here is evidence.
            RemoteGameSession session = session();
            session.identify(new RemoteGameSession.SignedIn("did:plc:abc123", "a-rivals.handle", false));

            assertThat(session.handle()).isEqualTo("did:plc:abc123");
            assertThat(session.handle()).doesNotContain("a-rivals");
        }

        @Test
        @DisplayName("falls back to the DID when no handle resolved at all")
        void noHandleFallsBackToDid() {
            RemoteGameSession session = session();
            session.identify(new RemoteGameSession.SignedIn("did:plc:abc123", null, true));

            assertThat(session.handle()).isEqualTo("did:plc:abc123");
        }
    }

    @Nested
    @DisplayName("a disconnected online session")
    class Disconnected {

        @Test
        @DisplayName("reports unreachable, never refused")
        void unreachableIsNotRefused() {
            // The distinction is the point. `1` claims a rule considered the request and declined
            // it; `69` says it never arrived. Collapsing them would be a lie about where the
            // decision came from — docs/client/01 §9.4.
            GameSession.Outcome outcome = session().allocateSelfMining(40);

            assertThat(outcome.status()).isEqualTo(GameSession.Outcome.UNAVAILABLE);
            assertThat(outcome.status()).isNotEqualTo(GameSession.Outcome.REFUSED);
            assertThat(outcome.message()).contains("Not connected");
        }

        @Test
        @DisplayName("every intent is unreachable while there is no transport")
        void everyIntentIsHonest() {
            RemoteGameSession s = session();
            assertThat(s.scan("quick").status()).isEqualTo(GameSession.Outcome.UNAVAILABLE);
            assertThat(s.collect().status()).isEqualTo(GameSession.Outcome.UNAVAILABLE);
            assertThat(s.arm("firewall", 1).status()).isEqualTo(GameSession.Outcome.UNAVAILABLE);
            assertThat(s.purchase("anything").status()).isEqualTo(GameSession.Outcome.UNAVAILABLE);
            assertThat(s.moveItem("x", StorageTier.VAULT).status()).isEqualTo(GameSession.Outcome.UNAVAILABLE);
        }

        @Test
        @DisplayName("reads return a last-known value rather than null or a crash")
        void readsNeverReturnNull() {
            // A HUD that empties when the network hiccups removes information from a player
            // mid-decision. Stale-but-marked beats blank.
            RemoteGameSession s = session();
            assertThat(s.computeBudget()).isNotNull();
            assertThat(s.balance()).isNotNull();
            assertThat(s.items(StorageTier.VAULT)).isNotNull();
            assertThat(s.ledger(10)).isNotNull();
            assertThat(s.knownNodes()).isNotNull();
            assertThat(s.connected()).isFalse();
        }

        @Test
        @DisplayName("its mode says losses are real, which solo's does not")
        void modeIsDistinguishable() {
            assertThat(session().mode()).isEqualTo(SessionMode.ONLINE);
            assertThat(session().mode().explanation()).contains("real");
        }

        @Test
        @DisplayName("persist does nothing, because the server owns the state")
        void persistIsANoOp() {
            // The asymmetry with LocalGameSession is Invariant I14 showing through the port. It is
            // correct rather than an omission.
            session().persist();
        }
    }

    @Nested
    @DisplayName("the deck loses nothing")
    class Deck {

        @Test
        @DisplayName("every tool in the catalogue has a launcher entry on the rail")
        void everyWindowIsReachable() {
            // docs/client/07 §2.3 makes this a contract: no functionality or information may be
            // lost in the single-window layout. The deck's rail launcher is now the route, and
            // asserting it beats a reviewer noticing that one window never got a chip.
            //
            // The check is on the accelerator glyphs rather than on DeckShell itself, because
            // building a DeckShell needs a live toolkit and this suite runs headless. Every window
            // in the catalogue must produce a distinct, non-blank glyph — a duplicate would be two
            // tools sharing one rail entry, which is exactly the lost-tool failure above.
            java.util.Set<String> glyphs = new java.util.HashSet<>();
            for (WindowSpec spec : WindowSpec.values()) {
                String name = spec.combination().getName();
                String last = name.substring(name.lastIndexOf('+') + 1).trim();
                String glyph =
                        switch (last) {
                            case "Comma" -> ",";
                            case "Slash" -> "/";
                            default -> last.length() > 1 ? last.substring(0, 1) : last;
                        };
                assertThat(glyph).as("%s has a rail glyph", spec.id()).isNotBlank();
                assertThat(glyphs.add(glyph))
                        .as("%s duplicates the rail glyph %s", spec.id(), glyph)
                        .isTrue();
            }
            assertThat(glyphs).hasSize(WindowSpec.values().length);
        }

        @Test
        @DisplayName("windows snap to a grid by default, and free-drag is the opt-in")
        void snapIsTheDefault() {
            // ui-design-language.md §11 question 1 asked for both and left the choice open. Snapping
            // is the default because it reinforces the character-cell language and is what makes
            // edge-tiling reachable at all. Pinned here because a default that flips during a
            // refactor is invisible in review and obvious to every player.
            assertThat(new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.Settings().freeDragWindows)
                    .isFalse();
        }

        @Test
        @DisplayName("the Bandwidth window cap is off until it is calibrated")
        void bandwidthCapIsOptIn() {
            // §8 wants the desk to be a mechanic. UI-2 records why it ships off: a starting rig has
            // bandwidth 1, and the arithmetic turning that into a window budget is invented. A cap
            // that turns out to be wrong must not be discovered by a player who cannot open a map.
            assertThat(new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.Settings().bandwidthCapsWindows)
                    .isFalse();
        }

        @Test
        @DisplayName("the proposed cap always leaves the six reachless tools open")
        void capNeverLocksOutTheBasics() {
            // The rig monitor, terminal, log, manual, settings and switcher reach nothing, so
            // Bandwidth has no claim on them. A cap that could close the manual would make the
            // game unlearnable at exactly the moment the player needed to learn it.
            var starting = new GameSession.RigCapacity(1, 1, 1);
            assertThat(starting.proposedWindowCap()).isGreaterThan(GameSession.RigCapacity.FREE_WINDOWS);
            assertThat(new GameSession.RigCapacity(0, 1, 1).proposedWindowCap())
                    .as("a zero-bandwidth rig still gets one engagement window")
                    .isEqualTo(GameSession.RigCapacity.FREE_WINDOWS + 1);
        }
    }
}
