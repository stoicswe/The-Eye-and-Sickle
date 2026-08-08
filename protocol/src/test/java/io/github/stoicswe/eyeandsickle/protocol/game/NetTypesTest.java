package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The network-view vocabulary: {@link HostKind}, {@link SignalStrength}, {@link ServerRef}, {@link
 * Sighting}, {@link NetLink}, {@link NetMap}, {@link SweepReport} and {@link NetDocument}.
 *
 * <p>These eight types describe <em>what the player has found out</em> about the network, and almost every
 * interesting property of them is an absence. There is no detection roll on the wire, no ground-truth
 * defence flag, no per-server host count, and no instance at all for a machine the player has not detected
 * — each of those omissions is load-bearing, and each is the kind of thing a later contributor would add
 * back in good faith to make a renderer's life easier. So a large share of what follows tests what these
 * records refuse to carry, in the same spirit as {@link BreachResolutionTest}'s check that a resolution
 * answers what happened and never what it unlocks.
 *
 * <p>The two rules the whole vocabulary hangs off, both from the design decisions this feature was built
 * against: <strong>schematics buy reach, ethecoin buys sensitivity</strong> ({@code
 * docs/design/07-recon-tools.md} §2, Invariant I2), and <strong>an undiscovered machine leaves no
 * trace</strong>.
 */
class NetTypesTest {

    private static final Instant WHEN = Instant.parse("2026-07-27T09:00:00Z");

    private static final ServerRef HOME = new ServerRef("srv-0", "home-relay", 0, true);

    /**
     * The state a sweep alone leaves a machine in: detected, at one hop, type not established. Every
     * {@link Sighting} in these tests is built from this so the assertions read as the one field they are
     * actually about.
     */
    private static Sighting contact(String address) {
        return new Sighting(
                address,
                address,
                HOME.serverId(),
                HostKind.UNKNOWN,
                null,
                SignalStrength.LOW,
                1,
                false,
                false,
                false,
                false,
                false,
                false,
                "");
    }

    /** Every record in the network vocabulary. Enums are listed separately where they matter. */
    private static final List<Class<?>> NET_RECORDS =
            List.of(ServerRef.class, Sighting.class, NetLink.class, NetMap.class, SweepReport.class, NetDocument.class);

    private static List<RecordComponent> componentsOf(Class<?> type) {
        return Arrays.asList(type.getRecordComponents());
    }

    @Nested
    @DisplayName("what a machine is")
    class Kinds {

        @Test
        @DisplayName("is a closed set of eight, and UNKNOWN is one of them")
        void closedSet() {
            assertThat(Arrays.stream(HostKind.values()).map(Enum::name).toList())
                    .containsExactly("UNKNOWN", "TERMINAL", "RELAY", "STORE", "SENTRY", "BRIDGE", "GATEWAY", "SELF");
        }

        @Test
        @DisplayName("UNKNOWN is not a placeholder for 'undiscovered' — it is what a sweep is licensed to say")
        void unknownIsAState() {
            // The two are constantly confused and the confusion is expensive: an undiscovered machine has
            // no Sighting at all, whereas UNKNOWN is a machine the sweep DID find and whose type it may
            // not name. Upgrading UNKNOWN to a real kind is what the 15 EC Passive Sniffer sells
            // (docs/design/07-recon-tools.md §1); if a sweep could set the kind, the tool would be
            // deleted at the point of rendering.
            assertThat(contact("10.0.0.4").kind()).isEqualTo(HostKind.UNKNOWN);
            assertThat(HostKind.valueOf("UNKNOWN")).isNotNull();
        }

        @Test
        @DisplayName("the player's own rig has a kind, so the map has exactly one node shape")
        void selfIsAKind() {
            // SELF sits in the same enum rather than in a two-case union because the rig is drawn on the
            // same graph, occupies one cell like everything else, and is the default vantage. A separate
            // type would push that union through every layout pass and every renderer, forever.
            assertThat(HostKind.SELF).isNotNull();
        }
    }

    @Nested
    @DisplayName("how loud a machine is")
    class Signals {

        @Test
        @DisplayName("is docs/design/04-mining.md §2.1's three established words, and no fourth")
        void closedSet() {
            // Reusing miner signal strength for whole machines is deliberate: a player who has learned
            // that a bigger miner is easier to find already knows what a HIGH host means. A second,
            // parallel scale would have taught the same lesson twice in two dialects.
            assertThat(Arrays.stream(SignalStrength.values()).map(Enum::name).toList())
                    .containsExactly("LOW", "MODERATE", "HIGH");
        }

        @Test
        @DisplayName("orders quiet to loud, because that is the direction sensitivity runs in")
        void ordersQuietToLoud() {
            assertThat(SignalStrength.LOW.ordinal()).isLessThan(SignalStrength.MODERATE.ordinal());
            assertThat(SignalStrength.MODERATE.ordinal()).isLessThan(SignalStrength.HIGH.ordinal());
        }

        @Test
        @DisplayName("carries no probability — a sweep's odds against a signal are a balance value")
        void carriesNoOdds() {
            // package-info's litmus test: if a constant here changed, would a player gain something? A
            // detection probability plainly would, so the enum holds three names and nothing else.
            // `isSynthetic` skips the compiler-generated `$values` every enum carries; without it this
            // assertion fails on a property of javac rather than on anything anyone wrote.
            List<String> accessors = Arrays.stream(SignalStrength.class.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic())
                    .map(Method::getName)
                    .filter(name -> !name.equals("values") && !name.equals("valueOf"))
                    .toList();
            assertThat(accessors).isEmpty();
        }
    }

    @Nested
    @DisplayName("a server")
    class Servers {

        @Test
        @DisplayName("carries the depth the danger tables are indexed by")
        void carriesDepth() {
            assertThat(HOME.depthFromHome()).isZero();
            assertThat(HOME.home()).isTrue();
            assertThat(new ServerRef("srv-3", "south-exchange", 2, false).depthFromHome())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("blanks a missing name rather than throwing at a renderer")
        void normalisesNulls() {
            ServerRef anonymous = new ServerRef(null, null, 0, false);
            assertThat(anonymous.serverId()).isEmpty();
            assertThat(anonymous.name()).isEmpty();
        }

        @Test
        @DisplayName("rejects a negative depth, because both ways it could fail are silent")
        void rejectsNegativeDepth() {
            // Depth indexes the generation tables. A negative one either clamps into the safest row — a
            // deep server that generates like home — or walks off the front of the table. Neither
            // announces itself, and the first is a live exploit rather than a crash.
            assertThatThrownBy(() -> new ServerRef("srv", "x", -1, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("depth");
        }

        @Test
        @DisplayName("does not publish how many machines it holds")
        void noHostCount() {
            // A per-server population is an aggregate about machines the player has not discovered, and
            // it would be a permanent one rather than a single sweep's reading. SweepReport.inRange is
            // the only such number the vocabulary permits, and it describes the instrument.
            assertThat(componentsOf(ServerRef.class).stream()
                            .map(RecordComponent::getName)
                            .toList())
                    .containsExactly("serverId", "name", "depthFromHome", "home");
        }
    }

    @Nested
    @DisplayName("a sighting")
    class Sightings {

        @Test
        // ⚠ The display name deliberately states NO COUNT. It read "the fourteen fields" while the
        // list held sixteen — a number in a title drifts silently, because nothing asserts it, and a
        // reader who trusts it miscounts the very thing the test exists to pin. The list below is the
        // contract; its length is not a separate claim. Same lesson `ScreenArtefactTest` records.
        @DisplayName("carries exactly the fields recon can establish, and nothing else")
        void carriesTheContract() {
            // Locked by name, not just by count. Every addition to this list is a claim about a machine
            // that some tool has to have been sold to the player to justify, so it should be a
            // deliberate edit to a test rather than a field that arrives with a renderer's convenience.
            assertThat(componentsOf(Sighting.class).stream()
                            .map(RecordComponent::getName)
                            .toList())
                    .containsExactly(
                            "address",
                            "label",
                            "serverId",
                            "kind",
                            "tier",
                            "signal",
                            // ⚠ Added 2026-08-07 alongside `self`, and it clears the bar the same
                            // way: it is not recon output. It is a distance over links the player can
                            // already see, from their own machine, and it leaks nothing a sweep has
                            // not already published — undiscovered hosts do not appear at all.
                            //
                            // ⚠ It exists because the MAP needs a different question answered from
                            // the one the ceiling is measured in. Drawn on `hopsFromVantage`, moving
                            // the vantage re-rooted the whole graph and demoted the player's own rig
                            // out of column zero. Both distances are real; neither derives the other.
                            "hopsFromRig",
                            "hopsFromVantage",
                            // ⚠ Added 2026-08-07, and it clears this list's bar in a way no other
                            // field on it does: it is not recon output at all. Every other entry has
                            // to name something a tool was sold to the player to establish — `self`
                            // needs no tool, because knowing which machine is your own is not a
                            // finding and cannot be withheld. It leaks nothing: the player's own
                            // address is on the command strip.
                            //
                            // ⚠ It exists because its ABSENCE was the bug. The rules computed it and
                            // never published it, so five views reached for `vantage` to mean "mine"
                            // — correct until the vantage moved, at which point the node menu
                            // offered to breach the player's own rig and hid Breach on the machine
                            // they were standing on.
                            "self",
                            "vantage",
                            "foothold",
                            // ⚠ Added 2026-07-27, and it clears the bar this list guards. The rule
                            // is that a field must name something a tool was sold to the player to
                            // learn — but `patched` is not an OBSERVATION of a machine, it is the
                            // player's own relationship to one, exactly like `foothold` and
                            // `looted` beside it. You know you are locked out of a host because you
                            // were inside it; no recon sells that. Nothing sets it true yet: the
                            // patch mechanic is proposed in docs/design/15, not decided.
                            "patched",
                            "looted",
                            "honeypotSuspected",
                            "hostsDeployedMiner",
                            "documentAvailable",
                            "bridgePeerServerName",
                            // ⚠ Added 2026-07-29, and it clears the same bar `patched` does. It is
                            // not an observation of the machine — it says nothing about what is on
                            // the far side — it is the player's own relationship to one: whether
                            // they have a file open on it. The list marks it `[i]` so a player can
                            // see at a glance which machines they have already worked on, and no
                            // recon had to be sold to establish that they own their own notes.
                            //
                            // ⚠ It is deliberately BOOLEAN rather than a completeness figure. How
                            // much is in the file is the report's own first line; a column trying to
                            // carry that would need seven states in a space that has room for three
                            // characters, and would be read as none of them.
                            "reported",
                            // ⚠ Added 2026-08-07, and it clears the bar by being PAID FOR rather
                            // than by being the player's own relationship to the machine. The
                            // operator's account name is `PortScanTarget.IDENTITY`'s product — the
                            // cheapest rung on the port-scan ladder — or it comes from having
                            // breached the host, where the account is simply in the prompt.
                            //
                            // ⚠ `label` beside it changed MEANING on the same day without changing
                            // its name, which is the more dangerous half of this edit and is why it
                            // is recorded here rather than only in the record. It used to be ground
                            // truth copied off the host by the sweep, so every machine on the map
                            // arrived already named; it is now the same finding as this one and is
                            // empty until that finding has been established. A field list cannot
                            // catch a field that keeps its name and stops being free —
                            // `identityIsNotFree` below is what actually guards it.
                            "operatorName");
        }

        @Test
        @DisplayName("defaults the operator to empty, so a producer that says nothing claims nothing")
        void operatorDefaultsToEmpty() {
            // ⚠ This is NOT the guard that a sweep leaves a machine unnamed. That rule lives in
            // NetRules and is tested there (`NetRulesTest.aSweepDoesNotNameTheMachine`) — the lowest
            // level the behaviour is visible at. A first attempt asserted it here against `contact()`
            // and was meaningless: that fixture passes the address in as the label, so it was
            // measuring the fixture's own convention rather than any rule. What this file can
            // honestly check is the record's default.
            assertThat(contact("10.0.0.4").operatorName()).isEmpty();
        }

        @Test
        @DisplayName("carries no ground truth: no defence flag, no firewall tier, no detection roll, no honeypot")
        void carriesNoGroundTruth() {
            // Four separate leaks, each with its own consequence:
            //   defended / firewallTier — these reach the player through BreachTarget, which already
            //     documents itself as recon output rather than truth. A second copy on this record would
            //     answer the same question twice on one screen and the two would drift.
            //   detectRoll — the value a sweep is compared against. On the wire, a client could compute
            //     exactly what a better sweep would find, which is the purchase the upgraded sweeps sell.
            //   honeypot — 07 §2 requires the Honeypot Detector to keep a false-negative rate, since "a
            //     perfect detector removes the fear the traps exist to create". honeypotSuspected is
            //     named a suspicion so nobody later cleans it up into a finding.
            List<String> names = componentsOf(Sighting.class).stream()
                    .map(RecordComponent::getName)
                    .toList();

            assertThat(names)
                    .as("ground truth belongs to the authoritative rules, never to a player-knowledge record")
                    .doesNotContain("defended", "firewallTier", "firewall", "detectRoll", "honeypot", "discovered");
        }

        @Test
        @DisplayName("has no discovered flag — absence is how 'not found' is encoded")
        void absenceIsTheEncoding() {
            // A boolean would have put the machine's address, server and hop count on the wire and then
            // asked every renderer, forever, to remember not to draw them. Absence cannot leak because
            // there is nothing to leak from.
            assertThat(componentsOf(Sighting.class).stream()
                            .map(RecordComponent::getName)
                            .toList())
                    .doesNotContain("discovered", "detected", "visible");
        }

        @Test
        @DisplayName("defaults the two readings that claim least")
        void defaultsClaimLeast() {
            Sighting silent =
                    new Sighting(null, null, null, null, null, null, 0, false, false, false, false, false, false, null);

            assertThat(silent.address()).isEmpty();
            assertThat(silent.label()).isEmpty();
            assertThat(silent.serverId()).isEmpty();
            assertThat(silent.bridgePeerServerName()).isEmpty();
            // UNKNOWN invents no type the player never bought; LOW does not make an unstated machine look
            // easier to find than it is.
            assertThat(silent.kind()).isEqualTo(HostKind.UNKNOWN);
            assertThat(silent.signal()).isEqualTo(SignalStrength.LOW);
        }

        @Test
        @DisplayName("leaves an unassessed tier null rather than inventing the most dangerous default")
        void tierIsNotDefaulted() {
            // DifficultyTier's scale starts at 1 and has no "unknown" member, so any default would be a
            // difficulty claim about a machine nobody has assessed — and the cheapest one to reach for
            // (tier 1) is the claim most likely to get a player killed. The player's own rig has no
            // difficulty either, so null is a real reading and not merely a missing one.
            assertThat(contact("10.0.0.9").tier()).isNull();

            Sighting assessed = new Sighting(
                    "10.0.0.9",
                    "10.0.0.9",
                    "srv-0",
                    HostKind.TERMINAL,
                    DifficultyTier.of(2),
                    SignalStrength.LOW,
                    1,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "");
            assertThat(assessed.tier()).isEqualTo(DifficultyTier.of(2));
        }

        @Test
        @DisplayName("measures hops from the vantage, and the vantage is at zero")
        void hopsAreFromTheVantage() {
            // Distance from wherever the player is operating, not from their rig — which is what makes a
            // one-hop horizon survivable across a whole world. Breach a machine, take a foothold, connect
            // to it, and everything is renumbered from there: position substitutes for reach, and reach
            // is the thing ethecoin may not buy (Invariant I2).
            Sighting here = new Sighting(
                    "10.0.0.1",
                    "localhost",
                    "srv-0",
                    HostKind.SELF,
                    null,
                    SignalStrength.LOW,
                    0,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    "");
            assertThat(here.hopsFromVantage()).isZero();
            assertThat(here.vantage()).isTrue();

            assertThatThrownBy(() -> new Sighting(
                            "10.0.0.4",
                            "x",
                            "srv-0",
                            HostKind.UNKNOWN,
                            null,
                            SignalStrength.LOW,
                            -1,
                            false,
                            false,
                            false,
                            false,
                            false,
                            false,
                            ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("hops");
        }

        @Test
        @DisplayName("only a bridge may name the network on its far side")
        void onlyABridgeNamesAPeer() {
            // A bridge advertising the network it links to is what a bridge is for, and the server NAME
            // is the whole of what it may advertise — never an address, never a host count. On anything
            // else it is a fact the player has no instrument for, and it is how the far side's topology
            // starts crossing a boundary it must not cross.
            assertThatCode(() -> new Sighting(
                            "10.1.0.9",
                            "gate",
                            "srv-1",
                            HostKind.BRIDGE,
                            DifficultyTier.of(3),
                            SignalStrength.HIGH,
                            2,
                            false,
                            false,
                            false,
                            false,
                            false,
                            false,
                            "north-yard"))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> new Sighting(
                            "10.0.0.7",
                            "desk",
                            "srv-0",
                            HostKind.TERMINAL,
                            DifficultyTier.of(1),
                            SignalStrength.LOW,
                            1,
                            false,
                            false,
                            false,
                            false,
                            false,
                            false,
                            "north-yard"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BRIDGE");
        }

        @Test
        @DisplayName("a bridge with nothing to advertise yet is legal")
        void anUnadvertisedBridgeIsLegal() {
            // The rule is one-directional on purpose: a peer name implies a bridge, a bridge does not
            // imply a peer name. A bridge the player has typed but whose far side has not been published
            // is a real state, and rejecting it would force a producer to invent a server name.
            assertThatCode(() -> new Sighting(
                            "10.1.0.9",
                            "gate",
                            "srv-1",
                            HostKind.BRIDGE,
                            DifficultyTier.of(3),
                            SignalStrength.HIGH,
                            1,
                            false,
                            true,
                            false,
                            false,
                            false,
                            false,
                            ""))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("a link")
    class Links {

        @Test
        @DisplayName("rejects a blank end rather than normalising it")
        void rejectsBlankEnds() {
            // The one place in this vocabulary where blank is rejected instead of blanked. An address is
            // the graph's identity, so an edge to "" draws a stub that reads as a link to somewhere the
            // player cannot reach yet — the single most misleading thing this map could show, given that
            // "is there more out there" is exactly the question the player is trying to answer.
            assertThatThrownBy(() -> new NetLink(null, "10.0.0.2", false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NetLink("", "10.0.0.2", false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NetLink("  ", "10.0.0.2", false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NetLink("10.0.0.1", null, false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NetLink("10.0.0.1", "", false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NetLink("10.0.0.1", "\t", false)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a self-loop, which would inflate every neighbour count by one")
        void rejectsSelfLoops() {
            assertThatThrownBy(() -> new NetLink("10.0.0.1", "10.0.0.1", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("self-loop");
        }

        @Test
        @DisplayName("has two ends and no direction, so the two orientations are different values")
        void hasNoDirection() {
            // Not a defect: producers may emit an edge once or in both orientations, and consumers must
            // read no meaning into which end came first. Canonicalising here would make the record claim
            // an ordering the graph does not use — the layout decides forward-versus-lateral from the
            // BFS layering, never from the record.
            assertThat(new NetLink("10.0.0.1", "10.0.0.2", false))
                    .isNotEqualTo(new NetLink("10.0.0.2", "10.0.0.1", false));
            assertThat(new NetLink("10.0.0.1", "10.0.0.2", false))
                    .isEqualTo(new NetLink("10.0.0.1", "10.0.0.2", false))
                    .hasSameHashCodeAs(new NetLink("10.0.0.1", "10.0.0.2", false));
        }

        @Test
        @DisplayName("marks a cross-server hop, because that is the move that changes the danger")
        void marksBridges() {
            assertThat(new NetLink("10.0.0.9", "10.1.0.2", true).bridge()).isTrue();
            assertThat(new NetLink("10.0.0.1", "10.0.0.2", false).bridge()).isFalse();
        }
    }

    @Nested
    @DisplayName("the map")
    class Maps {

        @Test
        @DisplayName("empty() is a truthful answer, not a plausible one")
        void emptyIsHonest() {
            // The shape RemoteGameSession returns: it has no transport, no OAuth flow and no reconnect
            // loop today (CL-8), so it must be able to say "nothing" rather than fabricate a network.
            NetMap empty = NetMap.empty();

            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.sightings()).isEmpty();
            assertThat(empty.links()).isEmpty();
            assertThat(empty.knownServers()).isEmpty();
            assertThat(empty.vantageAddress()).isEmpty();
            assertThat(empty.hopCeiling()).isEqualTo(1);
            // Home is the truthful default for a world nobody has left: a player with no map has not
            // crossed a bridge.
            assertThat(empty.currentServer().home()).isTrue();
            assertThat(empty.currentServer().depthFromHome()).isZero();
        }

        @Test
        @DisplayName("a hop ceiling below one is an instrument that has been sold and does not work")
        void rejectsAZeroCeiling() {
            // A ceiling of 0 is not "sees nothing" — it is a view that can never hold anything but the
            // vantage itself. The floor lives here because it is the domain of the field rather than a
            // balance value: what the ceiling IS, and what moves it, arrives already computed from the
            // authoritative side.
            assertThatThrownBy(() -> new NetMap(HOME, "10.0.0.1", 0, List.of(), List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ceiling");
            assertThatThrownBy(() -> new NetMap(HOME, "10.0.0.1", -2, List.of(), List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the ceiling is transmitted, never derived — there is no code path from a purchase to it")
        void ceilingIsTransmitted() {
            // Invariant I2 satisfied structurally rather than by review: the client is told how far it
            // can see and has no way to work the number out. docs/design/07-recon-tools.md §2 makes the
            // Topology Mapper a ceiling (1 hop -> 2) and therefore schematic-gated; sensitivity is what
            // ethecoin buys, and no amount of it moves this field.
            NetMap withMapper = new NetMap(HOME, "10.0.0.1", 2, List.of(HOME), List.of(), List.of());
            assertThat(withMapper.hopCeiling()).isEqualTo(2);

            List<String> derivers = Arrays.stream(NetMap.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(name -> {
                        String lower = name.toLowerCase(Locale.ROOT);
                        // No bare "owns" here: `knownServers` contains it, which is the sort of accidental
                        // match that makes a charter test look like it is failing on principle.
                        return lower.contains("ceilingfor")
                                || lower.contains("computeceiling")
                                || lower.contains("schematic")
                                || lower.contains("afford");
                    })
                    .toList();
            assertThat(derivers)
                    .as("what raises the ceiling is the authoritative side's decision (Invariant I14)")
                    .isEmpty();
        }

        @Test
        @DisplayName("copies its lists, so a producer that keeps mutating cannot rewrite a map already handed out")
        void copiesItsLists() {
            List<Sighting> mutable = new ArrayList<>();
            mutable.add(contact("10.0.0.4"));
            NetMap map = new NetMap(HOME, "10.0.0.1", 1, List.of(HOME), mutable, List.of());

            mutable.add(contact("10.0.0.6"));

            assertThat(map.sightings()).hasSize(1);
            assertThatThrownBy(() -> map.sightings().add(contact("10.0.0.9")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("treats a null list as nothing known, and a null entry as a producer bug")
        void nullsAreNotEntries() {
            NetMap blank = new NetMap(null, null, 1, null, null, null);
            assertThat(blank.knownServers()).isEmpty();
            assertThat(blank.sightings()).isEmpty();
            assertThat(blank.links()).isEmpty();
            assertThat(blank.currentServer()).isNotNull();

            // List.copyOf rejects null elements outright, which is why "unknown" is "" everywhere in this
            // vocabulary and never a null entry: a null sighting has no address to key anything off and
            // would fail at whichever renderer touched it first, arbitrarily far from the producer.
            List<Sighting> withNull = new ArrayList<>();
            withNull.add(null);
            assertThatThrownBy(() -> new NetMap(HOME, "10.0.0.1", 1, List.of(), withNull, List.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("cannot tell 'no such machine' from 'not discovered yet', deliberately")
        void lookupIsNotAProbe() {
            // A lookup that distinguished the two would be a free probe: type an address, learn whether
            // something is there. That is the sweep's entire product, given away at the cost of a
            // keystroke.
            NetMap map = new NetMap(HOME, "10.0.0.1", 1, List.of(HOME), List.of(contact("10.0.0.4")), List.of());

            assertThat(map.at("10.0.0.4")).contains(contact("10.0.0.4"));
            assertThat(map.at("10.0.0.6")).isEmpty(); // exists in the world, not yet detected
            assertThat(map.at("240.0.0.1")).isEmpty(); // no such machine anywhere
            assertThat(map.at(null)).isEmpty();
            assertThat(map.at("")).isEmpty();
        }

        @Test
        @DisplayName("isEmpty asks whether there is anything to draw")
        void isEmptyIsAboutSightings() {
            // A view must render this as an instruction rather than as a blank panel: a player looking at
            // an empty network needs telling that sweeping is what fills it.
            assertThat(new NetMap(HOME, "10.0.0.1", 1, List.of(HOME), List.of(), List.of()).isEmpty())
                    .isTrue();
            assertThat(new NetMap(HOME, "10.0.0.1", 1, List.of(HOME), List.of(contact("10.0.0.4")), List.of())
                            .isEmpty())
                    .isFalse();
        }

        @Test
        @DisplayName("does not police whether a link's endpoints are present — that is a producer contract")
        void doesNotPoliceDanglingLinks() {
            // Deliberate, and worth locking down so nobody later "fixes" it. An edge cannot see the map
            // it belongs to, so enforcing it here would mean validating the whole graph inside a
            // constructor a renderer calls on the repaint path. Every consumer of this record is a
            // renderer, and a renderer that throws mid-repaint is a worse answer than one bad edge.
            assertThatCode(() -> new NetMap(
                            HOME,
                            "10.0.0.1",
                            1,
                            List.of(HOME),
                            List.of(contact("10.0.0.4")),
                            List.of(new NetLink("10.0.0.4", "10.0.0.99", false))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("a sweep report")
    class Sweeps {

        private SweepReport report(int inRange, int found, List<String> addresses) {
            return new SweepReport("net-sweep", "10.0.0.1", inRange, found, addresses, false, "");
        }

        @Test
        @DisplayName("reports the instrument's sensitivity, not the network's contents")
        void inRangeIsTheOnlyAggregate() {
            // "Nine were in range and I found four" is a player learning what their own sweep is worth —
            // docs/design/04-mining.md §3.2a's "signal quality, not just sensitivity, is what a more
            // expensive tier buys". It carries no address, no type, no tier and no value, so there is
            // nothing in it to act on except buying a better instrument or standing somewhere else.
            SweepReport partial = report(9, 4, List.of("10.0.0.2", "10.0.0.4", "10.0.0.6", "10.0.0.9"));

            assertThat(partial.inRange()).isEqualTo(9);
            assertThat(partial.found()).isEqualTo(4);
            assertThat(partial.foundAddresses()).hasSize(4);
        }

        @Test
        @DisplayName("cannot claim to have detected more machines than it considered")
        void foundNeverExceedsInRange() {
            // The one arithmetic error that would actively mislead rather than merely confuse: the player
            // reads these two numbers as a fraction and decides whether to buy a better sweep from it, so
            // found > inRange tells them their instrument is better than perfect.
            assertThatThrownBy(() -> report(3, 4, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("considered");
        }

        @Test
        @DisplayName("rejects negative counts")
        void rejectsNegativeCounts() {
            assertThatThrownBy(() -> report(-1, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> report(5, -1, List.of())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("may name fewer machines than it counted — disclosing less is always legal")
        void mayDiscloseLessThanItCounted() {
            // Same reasoning BreachSnapshot uses for an activeLayer past the end of its layer list: a
            // producer that counts more than it names is holding something back, which is a move this
            // vocabulary must always permit. The reverse — naming more than it counted — is caught by the
            // found > inRange rule above.
            assertThatCode(() -> report(9, 4, List.of("10.0.0.2"))).doesNotThrowAnyException();
            assertThatCode(() -> report(9, 4, List.of())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a nothing-new sweep is a legal and expected report")
        void nothingNewIsALegalReport() {
            // The case the whole discovery model turns on. Detection is settled when the world is
            // generated, so the same tier from the same position returns the same machines forever and
            // re-running a sweep is not a re-roll. `note` is where the producer says so in the player's
            // language — the prose belongs to the side that knows why the sweep came back empty, never
            // to the renderer.
            SweepReport nothingNew = new SweepReport(
                    "net-sweep-wide",
                    "10.0.0.1",
                    9,
                    0,
                    List.of(),
                    false,
                    "Nothing at this sensitivity that you have not already seen.");

            assertThat(nothingNew.found()).isZero();
            assertThat(nothingNew.inRange()).isEqualTo(9);
            assertThat(nothingNew.note()).isNotEmpty();
        }

        @Test
        @DisplayName("says something hit back, never what that cost")
        void counterHackCarriesNoHeatFigure() {
            // How much heat a counter-hack costs is a balance value and arrives through the same readouts
            // as every other heat change. What IS structural: being hit back leaves a foreign miner on
            // the player's own rig, and cracking one of those generates no heat on any outcome (Invariant
            // I9, docs/design/04-mining.md §5.1) — so the punishment hands the player the safest teaching
            // target in the game.
            assertThat(componentsOf(SweepReport.class).stream()
                            .map(RecordComponent::getName)
                            .toList())
                    .containsExactly(
                            "sweepToolId",
                            "vantageAddress",
                            "inRange",
                            "found",
                            "foundAddresses",
                            "counterHacked",
                            "note")
                    .doesNotContain("heat", "heatDelta", "penalty");
        }

        @Test
        @DisplayName("blanks its strings and copies its addresses")
        void normalisesAndCopies() {
            SweepReport blank = new SweepReport(null, null, 0, 0, null, false, null);
            assertThat(blank.sweepToolId()).isEmpty();
            assertThat(blank.vantageAddress()).isEmpty();
            assertThat(blank.note()).isEmpty();
            assertThat(blank.foundAddresses()).isEmpty();

            List<String> mutable = new ArrayList<>(List.of("10.0.0.4"));
            SweepReport copied = report(2, 1, mutable);
            mutable.add("10.0.0.6");
            assertThat(copied.foundAddresses()).containsExactly("10.0.0.4");
        }
    }

    @Nested
    @DisplayName("a recovered document")
    class Documents {

        @Test
        @DisplayName("carries no prose — rules never do")
        void carriesNoBody() {
            // A paragraph in a rules module is a paragraph that has to be translated, versioned and
            // regression-tested alongside balance values it has nothing to do with. The body is a client
            // resource keyed by documentId, and a client with no file for an id renders an unreadable
            // fragment — a valid, entirely in-fiction outcome rather than an error state.
            assertThat(componentsOf(NetDocument.class).stream()
                            .map(RecordComponent::getName)
                            .toList())
                    .containsExactly("documentId", "title", "recoveredFrom", "recoveredAt", "schematicMaterial")
                    .doesNotContain("body", "text", "prose", "content");
        }

        @Test
        @DisplayName("requires a timestamp, because the recovered set is ordered")
        void requiresATimestamp() {
            // The one field that is required rather than blanked. A document is an entry in an ordered
            // record of what the player has recovered, oldest first, and an undated entry would sort
            // wherever the comparator happened to put it.
            assertThatThrownBy(() -> new NetDocument("doc.audit", "AUDIT FINDING 14-C", "10.2.0.7", null, 0))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("recoveredAt");
        }

        @Test
        @DisplayName("takes its timestamp as an input and never reads a clock")
        void timestampIsAnInput() {
            // ArchitectureRulesTest bans the ambient-clock idioms module-wide; this asserts the positive
            // half — the caller chooses the instant, so the same logical record serializes identically on
            // two machines.
            NetDocument doc = new NetDocument("doc.log", "OPERATOR LOG, RECOVERED", "10.3.0.4", WHEN, 1);
            assertThat(doc.recoveredAt()).isEqualTo(WHEN);
        }

        @Test
        @DisplayName("reports an award of zero for a document off a machine that did not earn one")
        void zeroMaterialIsLegitimate() {
            // Invariant I13 gates salvage on engagement tier. docs/design/10-botnets.md §1a gives the
            // exploit in its original costume — "the optimal play is to build the cheapest junk bot and
            // feed it to a loss" — and it exists here in different clothes: find a deep-but-trivial
            // machine and farm it. A deep-but-easy host yields flavour and nothing else, so 0 is a
            // correct answer rather than a missing one. The threshold itself is a balance value and is
            // not in this module.
            NetDocument flavourOnly = new NetDocument("doc.letter", "LETTER, UNSENT", "10.1.0.6", WHEN, 0);
            assertThat(flavourOnly.schematicMaterial()).isZero();
        }

        @Test
        @DisplayName("rejects a negative award — material is granted, never confiscated")
        void rejectsNegativeMaterial() {
            assertThatThrownBy(() -> new NetDocument("doc.memo", "MEMO", "10.1.0.6", WHEN, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("schematicMaterial");
        }

        @Test
        @DisplayName("blanks its identifying strings rather than throwing at a readout")
        void normalisesNulls() {
            NetDocument anonymous = new NetDocument(null, null, null, WHEN, 0);
            assertThat(anonymous.documentId()).isEmpty();
            assertThat(anonymous.title()).isEmpty();
            assertThat(anonymous.recoveredFrom()).isEmpty();
        }
    }

    @Nested
    @DisplayName("the charter, across the whole network vocabulary")
    class Charter {

        @Test
        @DisplayName("holds no floating-point field — every one of them would be a balance value")
        void noBalanceValues() {
            // A crisp mechanical form of package-info's litmus test. Every number this feature keeps out
            // of the wire is a double: the detection roll fixed at world generation, the per-tier
            // sensitivity, the hop factor, the counter-hack chance, the spawn probabilities. If one of
            // them ever arrives, it arrives as a double, so banning the type here catches the whole
            // class at once rather than one field name at a time.
            for (Class<?> type : NET_RECORDS) {
                assertThat(componentsOf(type))
                        .as("%s must carry no probability, roll, chance or curve", type.getSimpleName())
                        .noneMatch(
                                component -> component.getType() == double.class || component.getType() == float.class);
            }
        }

        @Test
        @DisplayName("counts nothing it has not shown the player")
        void noPopulationCounts() {
            // SweepReport.inRange is the single permitted aggregate about undetected machines, and it is
            // permitted because it describes the instrument rather than the network. Anything shaped like
            // "how many are out there" — a per-server population, a remaining count, a nearby hint — is
            // the horizon leaking permanently rather than as one sweep's reading.
            for (Class<?> type : NET_RECORDS) {
                List<String> shaped = componentsOf(type).stream()
                        .map(RecordComponent::getName)
                        .filter(name -> {
                            String lower = name.toLowerCase(Locale.ROOT);
                            // `endsWith` for the two generic words, `contains` only for the ones that
                            // cannot appear innocently. A bare contains("count") matches `counterHacked`,
                            // which is a boolean about the player and not a census of anything.
                            return lower.endsWith("count")
                                    || lower.endsWith("counts")
                                    || lower.endsWith("total")
                                    || lower.endsWith("totals")
                                    || lower.contains("population")
                                    || lower.contains("nearby")
                                    || lower.contains("remaining")
                                    || lower.contains("undiscovered")
                                    || lower.contains("hidden");
                        })
                        .toList();
                assertThat(shaped)
                        .as("%s must not publish a count of what the player has not found", type.getSimpleName())
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("answers what is known, never what it permits")
        void noRuleShapedMethods() {
            // The same name check ResolutionRecord gets, applied to the type most likely to grow one:
            // a `canReach()` or `isAffordable()` on NetMap would be half a gate check living in the
            // module that forbids gate checks, and the threshold would follow within a release.
            List<Class<?>> everything = new ArrayList<>(NET_RECORDS);
            everything.add(HostKind.class);
            everything.add(SignalStrength.class);

            for (Class<?> type : everything) {
                List<String> ruleShaped = Arrays.stream(type.getDeclaredMethods())
                        .map(Method::getName)
                        .filter(name -> {
                            String lower = name.toLowerCase(Locale.ROOT);
                            return lower.contains("eligib")
                                    || lower.contains("unlock")
                                    || lower.contains("afford")
                                    || lower.contains("detects")
                                    || lower.contains("threshold")
                                    || lower.contains("chance")
                                    || lower.contains("probab")
                                    || lower.contains("price")
                                    || lower.contains("qualif");
                        })
                        .toList();
                assertThat(ruleShaped)
                        .as("gate evaluation and balance are the authoritative side's job (Invariant I14)")
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("every record is a value: equal contents, equal record")
        void valueEquality() {
            // These cross a session boundary and are compared by views deciding whether to repaint. A
            // record with identity semantics would repaint on every tick and, worse, would make a
            // headless test of a renderer's output impossible to write.
            ServerRef sameHome = new ServerRef("srv-0", "home-relay", 0, true);
            assertThat(HOME).isEqualTo(sameHome).hasSameHashCodeAs(sameHome);
            assertThat(contact("10.0.0.4")).isEqualTo(contact("10.0.0.4")).hasSameHashCodeAs(contact("10.0.0.4"));
            assertThat(NetMap.empty()).isEqualTo(NetMap.empty());
            assertThat(new NetDocument("doc.index", "INDEX OF INDEXES", "10.4.0.2", WHEN, 1))
                    .isEqualTo(new NetDocument("doc.index", "INDEX OF INDEXES", "10.4.0.2", WHEN, 1));
        }
    }
}
