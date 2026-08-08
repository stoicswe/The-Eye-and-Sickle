package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the machines and the people on them are called.
 *
 * <p>The two properties worth defending here are <b>stability</b> — a machine has one name forever,
 * with nothing stored to make that true — and <b>distinctness</b>, both in the sense that no two
 * machines share a name and in the sense that the names do not walk the pool in step with the host
 * index, which is the failure the scheme this replaced actually had.
 */
class NpcNamesTest {

    /** The generator's own address scheme, restated so a change to it fails here too. */
    private static String address(int server, int index) {
        return TopologyGenerator.address(server, index);
    }

    @Nested
    @DisplayName("the pools")
    class Pools {

        /**
         * ⚠ A name must be usable as a host label, because that is what it is.
         *
         * <p>{@code Hostname.problem} enforces RFC 1123 — letters, digits and hyphens, no leading or
         * trailing hyphen. A surname carrying a space or an apostrophe would produce a machine name
         * this game's own validator rejects, and it would do it for one entry in a hundred and fifty.
         */
        @Test
        @DisplayName("every entry is lowercase letters only, so every generated name is a legal host label")
        void everyEntryIsALegalLabel() {
            Pattern legal = Pattern.compile("[a-z]+");
            for (String word : all()) {
                assertThat(word).as("%s", word).matches(legal);
            }
        }

        /**
         * ⚠ Exactly one hyphen, and this is the rule a hyphenated surname would break.
         *
         * <p>The separator is a hyphen, so {@code berners-lee} or {@code goeppert-mayer} in the
         * pioneer pool would yield {@code bold-berners-lee} — a name nothing can split back into the
         * two halves it was built from. Nothing in the game splits one today; the point is that the
         * format stops being a format the moment one entry does this, and that entry would look
         * perfectly reasonable in a diff.
         */
        @Test
        @DisplayName("a machine name has exactly one hyphen, so it can always be read as adjective-pioneer")
        void oneHyphenOnly() {
            for (String adjective : NpcNames.adjectives()) {
                for (String pioneer : NpcNames.pioneers()) {
                    String name = adjective + "-" + pioneer;
                    assertThat(name.chars().filter(c -> c == '-').count())
                            .as("%s", name)
                            .isEqualTo(1L);
                }
            }
        }

        /**
         * ⚠ A LAYOUT contract, and the only reason two Norwegian names are not in the pool.
         *
         * <p>The network map's address line is {@code NET_NODE_COLS} = 18 cells and carries the
         * selection gutter (1), the widest address this scheme produces (9, {@code 10.6.0.51} at the
         * published cap of fifty machines a server), a separator (1) and then the account name. Seven
         * left. {@code NetCanvas} clips rather than wraps, so an eighth character comes off the end of
         * the name silently, on the surface the player reads most. {@code ragnhild} and
         * {@code torbjorn} were dropped for this and nothing else.
         *
         * <p>⚠ The bound is asserted as a literal 7 rather than derived from {@code UiTokens}: that
         * constant is in the client and this is the engine, and the enforcer forbids the dependency.
         * A comment in both places is the honest version of a coupling the build cannot express.
         */
        @Test
        @DisplayName("no operator name exceeds seven characters, because the map's address line has seven")
        void operatorsFitTheAddressLine() {
            for (String name : NpcNames.operators()) {
                assertThat(name).as("%s", name).hasSizeLessThanOrEqualTo(7);
            }
        }

        @Test
        @DisplayName("no pool repeats an entry, so nothing is twice as likely as its neighbours")
        void noDuplicates() {
            assertThat(new HashSet<>(NpcNames.operators())).hasSameSizeAs(NpcNames.operators());
            assertThat(new HashSet<>(NpcNames.adjectives())).hasSameSizeAs(NpcNames.adjectives());
            assertThat(new HashSet<>(NpcNames.pioneers())).hasSameSizeAs(NpcNames.pioneers());
        }

        private List<String> all() {
            List<String> out = new ArrayList<>(NpcNames.operators());
            out.addAll(NpcNames.adjectives());
            out.addAll(NpcNames.pioneers());
            return out;
        }
    }

    @Nested
    @DisplayName("stability")
    class Stability {

        /**
         * The whole reason nothing is stored at generation: the address <em>is</em> the record.
         *
         * <p>{@code VirtualFs} leans on this — a machine's generated home directory is stable across
         * visits, which is what makes {@code docs/design/04} §3.1's "was this here before?"
         * answerable at all.
         */
        @Test
        @DisplayName("the same address always yields the same operator")
        void operatorsAreDerived() {
            for (int server = 0; server < 7; server++) {
                for (int index = 0; index < 50; index++) {
                    String at = address(server, index);
                    assertThat(NpcNames.operator(at)).isEqualTo(NpcNames.operator(at));
                }
            }
        }

        @Test
        @DisplayName("a machine name is derived too, when nothing is in the way")
        void machineNamesAreDerived() {
            String at = address(3, 11);
            assertThat(NpcNames.machine(at, Set.of())).isEqualTo(NpcNames.machine(at, Set.of()));
        }
    }

    @Nested
    @DisplayName("distinctness")
    class Distinctness {

        /**
         * ⚠ THE MEASURED REGRESSION. This is what the eight-name array in {@code VirtualFs} did.
         *
         * <p>It indexed on {@code address.hashCode()}, which is {@code 31·h + c}, so consecutive
         * addresses landed a fixed distance apart and the modulo walked the pool in order. Measured
         * on the real address scheme before the fix, the first ten machines of a server were
         * {@code wren dana kai morgan riley sasha toma ves morgan riley} — the pool, in its
         * declaration order. Every server produced the same rotation at a different offset, so the
         * "random" operator name was the host index in disguise.
         *
         * <h2>⚠ The run must be a DECADE, and two earlier versions of this test were worthless</h2>
         *
         * {@code String.hashCode} is {@code 31·h + c}, so the march happens if and only if two
         * addresses differ by exactly one in their final character. That is a narrower condition than
         * "consecutive host indices", and both earlier attempts missed it — each passed against a
         * deliberately reinstated {@code hashCode}, which is the only reason either was caught.
         *
         * <ul>
         *   <li>Starting at index 0 runs {@code 10.s.0.2 … 10.s.0.9} and then crosses to
         *       {@code 10.s.0.10}, a character longer, which hashes nowhere near its predecessor. The
         *       march broke on its own at the eighth machine.
         *   <li>Starting at index 8 for sixty-six same-length addresses looked like the fix and was
         *       not: {@code 10.s.0.19 → 10.s.0.20} changes two characters, so the delta is
         *       {@code 31 − 9 = 22} rather than 1 and the march breaks at every decade boundary.
         * </ul>
         *
         * <p>So the run is one decade — {@code 10.s.0.10 … 10.s.0.19}, ten addresses whose last
         * character increments by one — and it is checked over several decades on several servers so
         * that passing is not luck. Measured: {@code hashCode} marches perfectly through every one of
         * them ({@code 46,47,48,…}); FNV-1a does not march past a single step in any.
         */
        @Test
        @DisplayName("addresses one apart do not land one apart in the operator pool")
        void operatorsDoNotWalkInLockstep() {
            List<String> pool = NpcNames.operators();
            for (int server = 0; server < 7; server++) {
                for (int decade = 8; decade <= 38; decade += 10) {
                    List<String> run = new ArrayList<>();
                    for (int index = decade; index < decade + 10; index++) {
                        run.add(NpcNames.operator(address(server, index)));
                    }
                    boolean marching = true;
                    for (int i = 1; i < run.size() && marching; i++) {
                        int previous = pool.indexOf(run.get(i - 1));
                        marching = pool.indexOf(run.get(i)) == (previous + 1) % pool.size();
                    }
                    assertThat(marching)
                            .as("server %d from %s walked the pool in order: %s", server, address(server, decade), run)
                            .isFalse();
                }
            }
        }

        /**
         * ⚠ Two machines called {@code bold-turing} on one map is the failure this guards.
         *
         * <p>Worse than a dull name: the map, the host list, the shell prompt and the recon file all
         * key a machine by what it is called, so a duplicate makes two hosts indistinguishable on the
         * surfaces a player uses to tell them apart. The pool is ~14,000 combinations against a world
         * of a few hundred machines, so the birthday bound makes a collision <em>expected</em> rather
         * than unlikely — the de-collision is load-bearing, not a belt-and-braces guard.
         */
        @Test
        @DisplayName("no two machines in a maxed-out world share a name")
        void machineNamesAreUniqueAcrossAWorld() {
            Set<String> taken = new LinkedHashSet<>();
            int assigned = 0;
            for (int server = 0; server < 7; server++) {
                for (int index = 0; index < 50; index++) {
                    taken.add(NpcNames.machine(address(server, index), taken));
                    assigned++;
                }
            }
            assertThat(taken).as("every name distinct").hasSize(assigned);
        }

        /**
         * The walk itself, driven deterministically rather than by hoping a world collides.
         *
         * <h2>⚠ This deliberately does NOT count collisions in a generated world</h2>
         *
         * That was the first version and it was fragile in a way that would have wasted somebody's
         * afternoon. Collisions over 350 machines are a birthday-bound accident of the pool sizes: at
         * 88 adjectives the count was four, and widening the pool to 184 dropped it to one. A test
         * asserting "at least one" therefore passes today and fails the day somebody adds names — a
         * red build reporting a defect that is not there, on a change that made things better.
         *
         * <p>So the collision is <b>constructed</b>. Blocking a machine's preferred name, and then
         * the next twenty it would try, forces the loop through its adjective walk and out the far
         * side, and asserts the two things the loop actually promises: it never returns a blocked
         * name, and it still returns a well-formed one.
         */
        @Test
        @DisplayName("blocked names are walked past, and what comes out is still adjective-pioneer")
        void theWalkFindsAFreeName() {
            String at = address(2, 17);
            Set<String> blocked = new LinkedHashSet<>();
            for (int i = 0; i < 20; i++) {
                String next = NpcNames.machine(at, blocked);
                assertThat(next).as("round %d", i).isNotIn(blocked);
                assertThat(next).as("round %d", i).matches("[a-z]+-[a-z]+");
                assertThat(NpcNames.adjectives()).contains(next.substring(0, next.indexOf('-')));
                assertThat(NpcNames.pioneers()).contains(next.substring(next.indexOf('-') + 1));
                blocked.add(next);
            }
            assertThat(blocked).as("twenty distinct names").hasSize(20);
        }

        @Test
        @DisplayName("a taken name is never handed out again")
        void neverReturnsATakenName() {
            String at = address(0, 0);
            String first = NpcNames.machine(at, Set.of());
            assertThat(NpcNames.machine(at, Set.of(first))).isNotEqualTo(first);
        }
    }

    @Nested
    @DisplayName("server names")
    class Servers {

        /**
         * ⚠ A server name is a host label too, and it reaches farther than a machine's.
         *
         * <p>It is what a bridge <em>advertises</em> ({@code docs/design/17} §3.1) and what the map's
         * tab strip reads, so it crosses the wire as {@code Sighting.bridgePeerServerName} and is
         * rendered as chrome. The same RFC 1123 vocabulary applies for the same reason.
         */
        @Test
        @DisplayName("every character is lowercase letters only, 3 to 12 long")
        void charactersAreLegal() {
            Pattern legal = Pattern.compile("[a-z]{3,12}");
            for (String word : NpcNames.characters()) {
                assertThat(word).as("%s", word).matches(legal);
            }
        }

        @Test
        @DisplayName("no character repeats, so nothing is twice as likely as its neighbours")
        void noDuplicates() {
            assertThat(new HashSet<>(NpcNames.characters()))
                    .as("distinct characters")
                    .hasSize(NpcNames.characters().size());
        }

        /**
         * ⚠ THE THREE POOLS MUST NOT OVERLAP, and the reasons differ for each pair.
         *
         * <p>Against {@code PIONEERS}: those are <b>real people</b>, and a name in both pools reads as
         * the real one wherever it appears. Resident Evil Village's Karl Heisenberg was harvested and
         * dropped by exactly this check — {@code wicked-heisenberg} on a server would read as Werner.
         *
         * <p>Against {@code OPERATORS}: an account name and a server name are different namespaces and
         * would not actually be ambiguous, but a player who has just met an operator called
         * {@code magnus} and then finds {@code roguish-magnus} will reasonably think the two are
         * connected. Seven names were dropped for this.
         *
         * <p>Against {@code ADJECTIVES}: {@code wicked-wicked} needs no further argument.
         */
        @Test
        @DisplayName("⚠ no character is also a pioneer, an operator or an adjective")
        void poolsDoNotOverlap() {
            Set<String> characters = new HashSet<>(NpcNames.characters());
            assertThat(characters).as("vs pioneers").doesNotContainAnyElementsOf(NpcNames.pioneers());
            assertThat(characters).as("vs operators").doesNotContainAnyElementsOf(NpcNames.operators());
            assertThat(characters).as("vs adjectives").doesNotContainAnyElementsOf(NpcNames.adjectives());
        }

        @Test
        @DisplayName("the same server id always yields the same name")
        void namesAreDerived() {
            for (int i = 0; i < 12; i++) {
                String id = HostArchetypes.serverId(i);
                assertThat(NpcNames.server(id, Set.of())).isEqualTo(NpcNames.server(id, Set.of()));
            }
        }

        /**
         * ⚠ The fixed list this replaced was the SAME SEVEN PLACES ON EVERY SEED — {@code home-relay},
         * {@code south-exchange}, {@code north-yard} — because the generation sequence has no draw slot
         * for a server name. Two players comparing worlds found identical place names on different
         * shapes. Hashing the id costs no draws and gives a different set per world.
         */
        @Test
        @DisplayName("no two servers in a world share a name")
        void namesAreUniqueAcrossAWorld() {
            Set<String> taken = new LinkedHashSet<>();
            for (int i = 0; i < Balance.NET_SERVERS_MAX; i++) {
                String name = NpcNames.server(HostArchetypes.serverId(i), taken);
                assertThat(taken).as("server %d", i).doesNotContain(name);
                assertThat(name).as("server %d", i).matches("[a-z]+-[a-z]+");
                taken.add(name);
            }
        }

        @Test
        @DisplayName("blocked names are walked past, and what comes out is still adjective-character")
        void theWalkFindsAFreeName() {
            Set<String> blocked = new LinkedHashSet<>();
            for (int i = 0; i < 20; i++) {
                String next = NpcNames.server("srv-3", blocked);
                assertThat(next).as("round %d", i).isNotIn(blocked);
                assertThat(NpcNames.adjectives()).contains(next.substring(0, next.indexOf('-')));
                assertThat(NpcNames.characters()).contains(next.substring(next.indexOf('-') + 1));
                blocked.add(next);
            }
            assertThat(blocked).hasSize(20);
        }

        /**
         * ⚠ {@code looksLikeServer} is what {@code TopologyGenerator.relabelLegacy} branches on, so a
         * false positive renames nothing and a false negative renames something twice. It asks "is
         * this one of MINE" rather than "is this the old format" — the same call {@code looksGenerated}
         * records, and for the same reason: testing for the old format needs a copy of a scheme that
         * no longer exists, kept in step by hand.
         */
        @Test
        @DisplayName("⚠ recognises its own names and not the fixed list it replaced")
        void recognisesItsOwn() {
            assertThat(NpcNames.looksLikeServer(NpcNames.server("srv-0", Set.of()))).isTrue();
            for (String legacy : List.of("home-relay", "south-exchange", "north-yard", "outer-span")) {
                assertThat(NpcNames.looksLikeServer(legacy)).as("%s", legacy).isFalse();
            }
            assertThat(NpcNames.looksLikeServer("some-thing")).isFalse();
            assertThat(NpcNames.looksLikeServer(null)).isFalse();
            assertThat(NpcNames.looksLikeServer("wicked")).isFalse();
        }

        /**
         * ⚠ A machine name and a server name must never be the same string.
         *
         * <p>They share the adjective pool, so the only thing separating them is the noun — which is
         * exactly what {@link #poolsDoNotOverlap} defends. This is the consequence, asserted on the
         * finished names rather than on the pools, because that is the form a player sees.
         */
        @Test
        @DisplayName("a server name is never also a machine name")
        void serversAndMachinesNeverCollide() {
            Set<String> machines = new HashSet<>();
            for (int server = 0; server < 4; server++) {
                for (int host = 0; host < 40; host++) {
                    machines.add(NpcNames.machine(TopologyGenerator.address(server, host), Set.of()));
                }
            }
            for (int i = 0; i < Balance.NET_SERVERS_MAX; i++) {
                assertThat(NpcNames.server(HostArchetypes.serverId(i), Set.of()))
                        .as("server %d", i)
                        .isNotIn(machines);
            }
        }
    }
}
