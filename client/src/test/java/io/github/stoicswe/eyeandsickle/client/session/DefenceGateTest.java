package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * You have to own a defence to arm it.
 *
 * <h2>⚠ The gates were published and unenforced, which is the worst of the three states</h2>
 *
 * {@code docs/design/09} §1 has carried a gate and a price for every defence since the design
 * sessions, and until 2026-08-06 {@code LocalGameSession.armIntent} checked <em>compute and nothing
 * else</em>. A brand-new character could arm a T3 firewall, a Detection Array and the Auto-Counter
 * Daemon without holding any of them — so the unlock ladder existed in the documents, in the
 * catalogue and in the shop, and did not exist in the game. Invariants <b>I2</b> and <b>I3</b> both
 * rest on that ladder.
 *
 * <p>A published-but-unenforced rule is worse than a missing one: everything reads correctly, the
 * shop sells items nobody needs to buy, and the defect is invisible to anyone who has not tried to
 * arm something they never bought.
 */
class DefenceGateTest {

    private static final Instant T0 = Instant.parse("2026-08-06T12:00:00Z");

    private static LocalGameSession session(Path dir) {
        return new LocalGameSession(GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC)));
    }

    private static void grant(LocalGameSession session, String offeringId) {
        Catalogue.byId(offeringId).ifPresent(offering -> {
            ItemState item = new ItemState();
            item.itemType = offering.id();
            item.displayName = offering.name();
            item.tier = StorageTier.VAULT.name();
            session.game().state().items.add(item);
        });
    }

    @Nested
    @DisplayName("arming requires owning")
    class Ownership {

        @Test
        @DisplayName("a rig cannot arm a defence it does not have")
        void refusesWhatIsNotHeld(@TempDir Path dir) {
            LocalGameSession session = session(dir);

            assertThat(session.arm("tarpit", 1).succeeded())
                    .as("a fresh rig has no tarpit and must not be able to arm one")
                    .isFalse();
            assertThat(session.defenses()).isEmpty();
        }

        @Test
        @DisplayName("and can once it does")
        void allowsWhatIsHeld(@TempDir Path dir) {
            LocalGameSession session = session(dir);
            grant(session, "tarpit");

            assertThat(session.arm("tarpit", 1).succeeded()).isTrue();
            assertThat(session.defenses()).hasSize(1);
        }

        /**
         * ⚠ Per TIER, not per kind — the whole point of splitting the array's ladder across two gates.
         *
         * <p>Owning T1 must not arm T2. If the check were on the kind alone, buying the cheapest rung
         * of a ladder would unlock every rung above it, including the schematic-gated top — which is
         * precisely the I2 hole the split gate was created to avoid.
         */
        @Test
        @DisplayName("owning one rung of a ladder does not arm the rungs above it")
        void aRungIsNotTheLadder(@TempDir Path dir) {
            LocalGameSession session = session(dir);
            grant(session, "detection-array-t1");

            assertThat(session.arm("detection-array", 2).succeeded())
                    .as("T1 in the vault must not arm T2")
                    .isFalse();
            assertThat(session.arm("detection-array", 3).succeeded())
                    .as("and certainly not the schematic-gated top")
                    .isFalse();
            assertThat(session.arm("detection-array", 1).succeeded()).isTrue();
        }

        /**
         * ⚠ The refusal names the GATE, because "you don't have that" is true and useless.
         *
         * <p>{@code docs/design/02} §1's premise is that a gate is legible. A player refused the
         * Auto-Counter Daemon needs to learn that no amount of money will help; one refused a
         * Detection Array T2 needs to learn that money is exactly what will. Same refusal wording for
         * both would send half of them to the wrong place.
         */
        @Test
        @DisplayName("the refusal says how the thing is obtained, and the answers differ by gate")
        void theRefusalNamesTheGate(@TempDir Path dir) {
            LocalGameSession session = session(dir);

            assertThat(session.arm("detection-array", 2).message())
                    .as("an ethecoin gate points at the shop")
                    .contains("market");
            assertThat(session.arm("auto-counter-daemon", 1).message())
                    .as("a schematic gate says money is not the answer")
                    .contains("schematic");
            assertThat(session.arm("honeypot-stash", 1).message())
                    .as("a reputation gate says standing is")
                    .contains("standing");
        }
    }

    @Nested
    @DisplayName("the starting rig")
    class Starting {

        /**
         * ⚠ Without this a new player's first visit to the FIREWALL panel is ten refusals, and the
         * reasonable conclusion is that the tool is broken rather than that the ladder is locked.
         */
        @Test
        @DisplayName("a new character is issued one defence, and can arm it immediately")
        void issuedOneDefence(@TempDir Path dir) {
            LocalGameSession session = session(dir);

            assertThat(session.items(StorageTier.VAULT))
                    .as("the starting defence is in the vault, not the high-risk zone")
                    .anyMatch(item -> Catalogue.STARTING_DEFENCE.equals(item.itemType()));
            assertThat(session.arm("firewall", 1).succeeded()).isTrue();
        }

        /** ⚠ Issued, not exempt: everything above tier one is still behind its gate. */
        @Test
        @DisplayName("being issued T1 unlocks nothing else on the ladder")
        void theGrantIsNotAKey(@TempDir Path dir) {
            LocalGameSession session = session(dir);

            assertThat(session.arm("firewall", 2).succeeded()).isFalse();
            assertThat(session.arm("firewall", 3).succeeded()).isFalse();
        }
    }
}
