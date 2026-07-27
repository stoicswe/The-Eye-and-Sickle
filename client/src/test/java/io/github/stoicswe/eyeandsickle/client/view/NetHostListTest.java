package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the host list — which is to say, for {@link NetText}, since that is where every
 * character the list and the terminal print is decided.
 *
 * <h2>Why these assert on exact strings</h2>
 *
 * The columns are a contract with the player, not an implementation detail: a fixed-width table is
 * only readable if the fields stay in the same place between refreshes, and it is only usable in a
 * pipeline if {@code cut -f} keeps selecting the same thing. An assertion on "contains the address"
 * would pass while the table sheared. So these compare whole lines.
 *
 * <h2>Why they construct no JavaFX nodes</h2>
 *
 * No test in this module starts the JavaFX toolkit, and making this the first one would put a
 * display dependency in the shared build for the sake of asserting on text that is already
 * available without one. {@link NetHostList#frame()} reads the labels on screen; everything it
 * would read comes from the functions exercised here.
 */
class NetHostListTest {

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static final ServerRef HOME = new ServerRef("s0", "home-relay", 0, true);
    private static final ServerRef SOUTH = new ServerRef("s1", "south-exchange", 1, false);

    private static Sighting self() {
        return new Sighting("10.0.0.1", "localhost", "s0", HostKind.SELF, null,
                SignalStrength.LOW, 0, true, true, false, false, false, false, "");
    }

    private static Sighting contact(String address, int hops, int tier) {
        return new Sighting(address, "", "s0", HostKind.UNKNOWN, DifficultyTier.of(tier),
                SignalStrength.LOW, hops, false, false, false, false, false, false, "");
    }

    private static NetMap map(Sighting... sightings) {
        return new NetMap(HOME, "10.0.0.1", 1, List.of(HOME, SOUTH), List.of(sightings), List.of());
    }

    // ── the table ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("the columns are a contract, not a layout preference")
    class Columns {

        @Test
        @DisplayName("the header is exactly the seven columns at the specified widths")
        void header() {
            assertThat(NetText.header(false)).isEqualTo(
                    "ADDRESS         SERVER          HOPS  KIND      TIER  STATE       NOTE");
        }

        @Test
        @DisplayName("a plain contact lands in those columns character for character")
        void plainRow() {
            NetMap map = map(contact("10.0.0.4", 1, 1));
            assertThat(NetText.rows(map, false)).containsExactly(
                    "10.0.0.4        home-relay      1     --------  T1    contact");
        }

        @Test
        @DisplayName("the SERVER column carries the server's name, never its id")
        void serverName() {
            // A player never sees a serverId anywhere else, and a table that printed one would be
            // asking them to learn a second identifier for a thing they already have a name for.
            assertThat(NetText.rows(map(contact("10.0.0.4", 1, 1)), false).getFirst())
                    .contains("home-relay")
                    .doesNotContain("s0 ");
        }

        @Test
        @DisplayName("-v adds SIGNAL and DEPTH and moves nothing else")
        void verboseColumns() {
            assertThat(NetText.header(true)).isEqualTo(
                    "ADDRESS         SERVER          HOPS  KIND      TIER  STATE       "
                            + "SIGNAL    DEPTH  NOTE");
            assertThat(NetText.header(true)).startsWith(
                    NetText.header(false).substring(0, NetText.header(false).length() - "NOTE".length()));
        }

        @Test
        @DisplayName("an over-long value is clipped, so nothing to its right moves")
        void clipping() {
            ServerRef long1 = new ServerRef("s0", "a-server-with-a-very-long-name", 0, true);
            NetMap map = new NetMap(long1, "10.0.0.1", 1, List.of(long1),
                    List.of(contact("10.0.0.4", 1, 1)), List.of());
            String row = NetText.rows(map, false).getFirst();
            // The KIND column still starts where the header says it does.
            assertThat(row.indexOf("--------"))
                    .isEqualTo(NetText.header(false).indexOf("KIND"));
        }
    }

    // ── what a row may and may not say ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("a row never says more than recon established")
    class Honesty {

        @Test
        @DisplayName("an unestablished type prints eight dashes and is never inferred")
        void unknownKind() {
            // Naming a machine's type is what the Passive Sniffer sells. A renderer that guessed it
            // from the tier, the signal or the address would delete a purchased tool at the point of
            // drawing (docs/design/07 §1, and design/02 §5's pricing check).
            String row = NetText.rows(map(contact("10.0.0.9", 1, 3)), false).getFirst();
            assertThat(row).contains(NetText.UNKNOWN_KIND);
            for (HostKind kind : HostKind.values()) {
                if (kind != HostKind.UNKNOWN) {
                    assertThat(row).doesNotContain(kind.name());
                }
            }
        }

        @Test
        @DisplayName("a machine with no tier prints -- rather than T0")
        void noTier() {
            // T0 would read as "tier zero, trivially easy". The player's own rig has no tier because
            // it is not a target, and that is a different statement from an easy one.
            assertThat(NetText.rows(map(self()), false).getFirst()).contains("--    vantage");
        }

        @Test
        @DisplayName("a suspected honeypot is punctuated as a suspicion")
        void trapIsAQuestion() {
            Sighting suspect = new Sighting("10.1.0.4", "", "s1", HostKind.SENTRY,
                    DifficultyTier.of(4), SignalStrength.MODERATE, 1,
                    false, false, false, true, false, false, "");
            assertThat(NetText.note(suspect)).contains("trap?");
        }

        @Test
        @DisplayName("nothing undiscovered is representable — an empty map has no rows at all")
        void nothingUndiscovered() {
            // No placeholder, no count, no "3 contacts nearby". A node the sweep did not detect is
            // absent from the map, and the renderer has nothing it could draw for it even if it
            // wanted to.
            assertThat(NetText.rows(NetMap.empty(), false)).isEmpty();
            assertThat(NetText.EMPTY).contains("sweep").contains("2 cycles");
        }
    }

    // ── the pipeline ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("the rows survive a pipeline")
    class Pipeline {

        @Test
        @DisplayName("a bridge row carries both BRIDGE and bridge, because grep is case-sensitive")
        void bridgeIsGreppableEitherWay() {
            Sighting bridge = new Sighting("10.1.0.9", "", "s1", HostKind.BRIDGE,
                    DifficultyTier.of(3), SignalStrength.HIGH, 2,
                    false, false, false, false, false, false, "north-yard");
            String row = NetText.row(map(bridge), bridge, false);
            assertThat(row).contains("BRIDGE").contains("bridge -> north-yard");
        }

        @Test
        @DisplayName("the note carries every flag that has no column of its own")
        void notes() {
            Sighting busy = new Sighting("10.1.0.5", "", "s1", HostKind.STORE,
                    DifficultyTier.of(3), SignalStrength.MODERATE, 1,
                    false, true, true, false, true, true, "");
            assertThat(NetText.note(busy)).isEqualTo("looted document miner");
        }
    }

    // ── ordering ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reading order")
    class Ordering {

        @Test
        @DisplayName("the vantage is first, then hop distance, then address")
        void order() {
            NetMap map = map(contact("10.0.0.9", 2, 1), contact("10.0.0.4", 1, 1), self());
            assertThat(NetText.ordered(map).stream().map(Sighting::address))
                    .containsExactly("10.0.0.1", "10.0.0.4", "10.0.0.9");
        }

        @Test
        @DisplayName("addresses sort by octet, so .9 comes before .10")
        void numericOctets() {
            // A lexicographic sort is the default here and is wrong in a way that stays invisible
            // until a server has ten hosts on it — which every generated server does.
            assertThat(NetText.compareAddresses("10.0.0.9", "10.0.0.10")).isNegative();
            assertThat(NetText.compareAddresses("10.0.0.10", "10.0.0.9")).isPositive();
            assertThat(NetText.compareAddresses("10.0.0.4", "10.0.0.4")).isZero();
        }
    }

    // ── the strip and the accessible text ────────────────────────────────────────────────────

    @Nested
    @DisplayName("the server strip and the spoken row")
    class Chrome {

        @Test
        @DisplayName("the strip names the server, the depth, what has been seen and the ceiling")
        void strip() {
            String strip = NetText.serverStrip(map(self(), contact("10.0.0.4", 1, 1)));
            assertThat(strip)
                    .contains("SERVER")
                    .contains("home-relay")
                    .contains("DEPTH 0 FROM HOME")
                    .contains("HOSTS SEEN 2")
                    .contains("CEILING 1 HOP");
        }

        @Test
        @DisplayName("a two-hop ceiling is plural, because a strip that reads 2 HOP is a typo on screen")
        void ceilingPlural() {
            NetMap two = new NetMap(HOME, "10.0.0.1", 2, List.of(HOME), List.of(), List.of());
            assertThat(NetText.serverStrip(two)).endsWith("CEILING 2 HOPS");
        }

        @Test
        @DisplayName("a row read aloud says the same facts, including that a type is not established")
        void spoken() {
            // docs/client/07 §5.2: meaning must not rest on appearance. Eight dashes read aloud are
            // eight dashes; the sentence has to say what they mean.
            assertThat(NetHostList.describe(contact("10.0.0.4", 1, 1)))
                    .isEqualTo("10.0.0.4, 1 hop away, type not established, tier 1, contact.");
            assertThat(NetHostList.describe(self())).contains("vantage");
        }
    }
}
