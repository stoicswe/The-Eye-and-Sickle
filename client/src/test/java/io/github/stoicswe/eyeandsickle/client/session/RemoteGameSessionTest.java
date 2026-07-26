package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.window.DockedShell;
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
        return new RemoteGameSession(URI.create("https://home.example"), "operator");
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
    @DisplayName("the docked layout loses nothing")
    class Docked {

        @Test
        @DisplayName("every tool in the catalogue is reachable in single-window mode")
        void everyWindowIsReachable() {
            // docs/client/07 §2.3 makes this a contract: the docked layout is a mode, not a
            // fallback, and no functionality or information may be lost in it. Asserting it beats a
            // reviewer noticing that one window never got a tab.
            assertThat(DockedShell.reachable()).containsExactlyInAnyOrder(WindowSpec.values());
        }

        @Test
        @DisplayName("three columns is the ceiling, and the reason is arithmetic")
        void columnCeiling() {
            // A fourth column on a 1440px window gives each pane 340px, below every tool's minimum.
            assertThat(DockedShell.MAX_COLUMNS).isEqualTo(3);
            double narrowest = 1440 - DockedShell.RAIL_WIDTH;
            assertThat(narrowest / (DockedShell.MAX_COLUMNS + 1))
                    .as("a fourth column would be below the smallest minimum width")
                    .isLessThan(WindowSpec.SWITCHER.minWidth() + 100);
        }
    }
}
