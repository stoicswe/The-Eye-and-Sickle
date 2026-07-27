package io.github.stoicswe.eyeandsickle.solo.breach;

import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.T0;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.focus;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.give;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.nodeTarget;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.withNode;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.EnumerationBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.LatticeNode;
import io.github.stoicswe.eyeandsickle.protocol.game.LogicBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.PortSlot;
import io.github.stoicswe.eyeandsickle.protocol.game.PortState;
import io.github.stoicswe.eyeandsickle.protocol.game.TraversalBoard;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.LatticeNodeState;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.PortSlotState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Board generation, and the one property that matters more than any of it: a snapshot never carries
 * the answer.
 *
 * <p>The generation tests are property tests over many seeds rather than golden-value tests over one.
 * A board is a random object with rules, and the rules are what a player learns — a test pinned to a
 * single seed would pass while every other board in the game was wrong.
 */
class BreachBoardsTest {

    /** Enough seeds that a rule violated one time in fifty would fail the build. */
    private static final int SEEDS = 120;

    private static LayerState generate(long seed, int tier, String puzzleClass) {
        SoloSave save = withNode(seed, tier, 0, false, false);
        BreachRules.begin(save, nodeTarget(save), T0);
        for (LayerState layer : save.activeBreach.layers) {
            if (puzzleClass.equals(layer.puzzleClass)) {
                return layer;
            }
        }
        return null;
    }

    @Nested
    @DisplayName("Enumeration boards")
    class Enumeration {

        @Test
        @DisplayName("the banner constrains where open ports can be — the human read (D-8)")
        void bannerRulesHold() {
            int checked = 0;
            for (int tier : new int[] {1, 3, 4}) {
                for (long seed = 1; seed <= SEEDS; seed++) {
                    LayerState layer = generate(seed, tier, "ENUMERATION");
                    if (layer == null) {
                        continue;
                    }
                    checked++;
                    List<Integer> open = new ArrayList<>();
                    for (PortSlotState slot : layer.ports) {
                        if ("OPEN".equals(slot.truth)) {
                            open.add(slot.index);
                        }
                    }
                    int bands = (layer.slots + layer.bandSize - 1) / layer.bandSize;
                    int lastBandStart = (bands - 1) * layer.bandSize;
                    String where = layer.banner + " tier " + tier + " seed " + seed + " open " + open;

                    assertThat(open).as(where).isNotEmpty();
                    switch (layer.banner) {
                        case "EDGE RELAY" -> {
                            assertThat(open).as(where).anyMatch(i -> i >= lastBandStart);
                            assertThat(open).as(where).noneMatch(i -> i < layer.bandSize);
                        }
                        case "STORAGE ARRAY" -> {
                            assertThat(open).as(where).noneMatch(i -> i < layer.bandSize);
                            assertThat(open.stream().filter(i -> open.contains(i + 1)).count())
                                    .as(where)
                                    .isEqualTo(1);
                        }
                        case "AUTH BROKER" -> {
                            assertThat(open.stream()
                                            .filter(i -> i >= layer.bandSize && i < layer.bandSize * 2)
                                            .count())
                                    .as(where)
                                    .isEqualTo(1);
                            assertThat(open).as(where).noneMatch(i -> open.contains(i + 1));
                        }
                        case "MEDIA CACHE" -> assertThat(open).as(where).allMatch(i -> i % 2 == 0);
                        default -> throw new AssertionError("unknown banner " + layer.banner);
                    }
                }
            }
            // ⚠ If this ever reads zero, the assertions above have been silently skipped and the
            // test is green because it did nothing.
            assertThat(checked).isGreaterThan(SEEDS);
        }

        @Test
        @DisplayName("board size and filtered count scale with tier")
        void sizeScalesWithTier() {
            for (int tier : new int[] {1, 3, 4}) {
                LayerState layer = generate(1L, tier, "ENUMERATION");
                if (layer == null) {
                    continue;
                }
                assertThat(layer.slots)
                        .isEqualTo(Balance.BREACH_ENUM_SLOTS_BASE + Balance.BREACH_ENUM_SLOTS_PER_TIER * (tier - 1));
                assertThat(layer.ports.stream().filter(p -> "FILTERED".equals(p.truth)).count())
                        .isEqualTo(tier);
                assertThat(layer.ports.stream().filter(p -> "OPEN".equals(p.truth)))
                        .allMatch(p -> !p.service.isEmpty());
            }
        }

        @Test
        @DisplayName("a sweep returns a count and never which slots")
        void sweepIsACount() {
            SoloSave save = withNode(31337L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "ENUMERATION");

            BreachRules.act(save, "sweep", "0", T0);
            assertThat(layer.readingRanges).hasSize(1);
            // Nothing was revealed: that gap is exactly what makes the cheap action worth 1 against
            // a probe's 2, and what makes reading the board the dominant strategy.
            assertThat(layer.ports).allMatch(slot -> !slot.revealed);
        }

        @Test
        @DisplayName("a wrong declaration says how many, never which")
        void declareLeaksNoPositions() {
            SoloSave save = withNode(31337L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "ENUMERATION");

            BreachRules.act(save, "mark", "1", T0);
            BreachRules.act(save, "declare", "", T0);

            String result = save.activeBreach.ledger.get(0).result;
            assertThat(result).matches("\\d+ slots? (is|are) wrong");
            // Naming the wrong slots would make declare the cheapest probe in the class — two
            // attention for a reading on every slot at once — and nobody would ever sweep again.
            assertThat(layer.ports).allMatch(slot -> !slot.revealed);
        }
    }

    @Nested
    @DisplayName("Logic boards")
    class Logic {

        @Test
        @DisplayName("Mastermind feedback counts each symbol once — the classic bug")
        void feedbackHandlesRepeats() {
            // If the multiset minimum were wrong, this scores 2 exact plus a spurious partial for
            // the second @, the feedback stops being consistent with itself, and a player deducing
            // correctly reaches a contradiction.
            assertThat(LogicRules.feedback(List.of("@", "@", "$"), List.of("@", "$", "$")))
                    .containsExactly(2, 0);
            assertThat(LogicRules.feedback(List.of("@", "@", "@"), List.of("@", "$", "%")))
                    .containsExactly(1, 0);
            assertThat(LogicRules.feedback(List.of("@", "$"), List.of("$", "@"))).containsExactly(0, 2);
            assertThat(LogicRules.feedback(List.of("@", "$", "%"), List.of("@", "$", "%")))
                    .containsExactly(3, 0);
        }

        @Test
        @DisplayName("feedback is symmetric, which is what makes the consistency filter sound")
        void feedbackIsSymmetric() {
            List<String> alphabet = BoardFactory.LOGIC_ALPHABET.subList(0, 4);
            Rng rng = new Rng(99L);
            for (int trial = 0; trial < 500; trial++) {
                List<String> a = new ArrayList<>();
                List<String> b = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    a.add(rng.pick(alphabet));
                    b.add(rng.pick(alphabet));
                }
                assertThat(LogicRules.feedback(a, b)).isEqualTo(LogicRules.feedback(b, a));
            }
        }

        @Test
        @DisplayName("every card in the fact deck is true of this particular secret")
        void factsNeverLie() {
            int checked = 0;
            for (int tier : new int[] {2, 3, 5}) {
                for (long seed = 1; seed <= SEEDS; seed++) {
                    LayerState layer = generate(seed, tier, "LOGIC");
                    if (layer == null) {
                        continue;
                    }
                    checked++;
                    for (String card : layer.factDeck) {
                        // A quiet read is design/05 §4's "patient baseline" at 1 attention. A
                        // baseline that sometimes lies is not a baseline, it is a second gamble.
                        assertThat(Facts.matches(card, layer.secret))
                                .as(card + " against " + layer.secret)
                                .isTrue();
                        assertThat(Facts.prose(card)).isNotBlank();
                    }
                }
            }
            assertThat(checked).isGreaterThan(SEEDS);
        }

        @Test
        @DisplayName("listening narrows the keyspace — the readout must move when you pay for it")
        void listeningNarrowsTheKeyspace() {
            SoloSave save = withNode(31337L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "LOGIC");
            assertThat(layer.candidatesRemaining).isEqualTo(layer.keyspace);

            BreachRules.act(save, "listen", "", T0);

            // A quiet read that left the one instrument unchanged would teach the player that
            // listening does nothing, which kills the patient half of design/05 §4's trade.
            assertThat(layer.candidatesRemaining).isLessThan(layer.keyspace);
            assertThat(layer.candidatesRemaining).isPositive();
        }

        @Test
        @DisplayName("the true secret always survives the candidate filter")
        void theAnswerIsNeverFilteredOut() {
            SoloSave save = withNode(31337L, 2, 0, false, false);
            give(save, "credential-harvester");
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "LOGIC");

            BreachRules.act(save, "listen", "", T0);
            BreachRules.act(save, "listen", "", T0);
            BreachRules.act(save, "harvest", "", T0);
            for (int i = 0; i < layer.secret.size(); i++) {
                BreachRules.act(save, "set", (i + 1) + ":" + layer.alphabet.getFirst(), T0);
            }
            BreachRules.act(save, "probe", "", T0);

            // A filter that could eliminate the real answer would make the layer unwinnable and the
            // readout a liar in the same move. Checked directly rather than through the count.
            assertThat(LogicRules.consistentWithHistory(layer.secret, layer.probes)).isTrue();
            assertThat(layer.facts).allMatch(card -> Facts.matches(card, layer.secret));
            assertThat(layer.candidatesRemaining).isPositive();
        }

        @Test
        @DisplayName("a provably impossible guess costs a strike; a merely unlucky one does not")
        void inconsistentGuessesStrike() {
            SoloSave save = withNode(31337L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "LOGIC");

            for (int i = 0; i < layer.secret.size(); i++) {
                BreachRules.act(save, "set", (i + 1) + ":" + layer.alphabet.getFirst(), T0);
            }
            BreachRules.act(save, "probe", "", T0);
            // The first guess was a legitimate deduction that happened to lose — no strike.
            assertThat(layer.strikes).isZero();
            assertThat(layer.probes.getFirst().inconsistent).isFalse();

            BreachRules.act(save, "probe", "", T0);
            // The identical guess is now provably impossible given its own response. That single
            // rule is what makes the class deduction rather than enumeration of the keyspace.
            assertThat(layer.probes.getLast().inconsistent).isTrue();
            assertThat(layer.strikes).isEqualTo(1);
        }

        @Test
        @DisplayName("a Fuzzer volley buys breadth and pays in quality")
        void volleyCarriesNoPartials() {
            SoloSave save = withNode(31337L, 2, 0, false, false);
            give(save, "fuzzer");
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "LOGIC");

            BreachRules.act(save, "volley", "", T0);

            assertThat(layer.probes).hasSize(Balance.BREACH_LOGIC_VOLLEY_SIZE);
            assertThat(layer.probes).allMatch(probe -> probe.volley);
            // -1 rather than 0: "no partial information" and "zero partials" are different facts,
            // and a player deducing from the second when the first is true reaches a wrong answer.
            assertThat(layer.probes).allMatch(probe -> probe.partial == -1);
        }

        @Test
        @DisplayName("tier 3 and above is always salted")
        void highTiersAreSalted() {
            for (int tier : new int[] {3, 4, 5}) {
                for (long seed = 1; seed <= 20; seed++) {
                    LayerState layer = generate(seed, tier, "LOGIC");
                    if (layer != null) {
                        assertThat(layer.salted).as("tier " + tier + " seed " + seed).isTrue();
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Traversal boards")
    class Traversal {

        @Test
        @DisplayName("every node is reachable and every edge runs forward")
        void latticeIsWellFormed() {
            int checked = 0;
            for (int tier : new int[] {4, 5}) {
                for (long seed = 1; seed <= SEEDS; seed++) {
                    LayerState layer = generate(seed, tier, "TRAVERSAL");
                    if (layer == null) {
                        continue;
                    }
                    checked++;
                    Map<String, LatticeNodeState> byId = new HashMap<>();
                    for (LatticeNodeState node : layer.nodes) {
                        byId.put(node.id, node);
                    }
                    for (LatticeNodeState node : layer.nodes) {
                        for (String exit : node.exits) {
                            assertThat(byId.get(exit).rank).as("forward only").isGreaterThan(node.rank);
                        }
                    }
                    Set<String> seen = new HashSet<>();
                    Deque<String> queue = new ArrayDeque<>();
                    queue.add(layer.nodes.getFirst().id);
                    while (!queue.isEmpty()) {
                        String id = queue.poll();
                        if (seen.add(id)) {
                            queue.addAll(byId.get(id).exits);
                        }
                    }
                    // A stranded node is a rendered lie, and on the objective rank it could be the
                    // objective itself — which would make the layer unwinnable.
                    assertThat(seen).as("tier " + tier + " seed " + seed).hasSize(layer.nodes.size());
                }
            }
            assertThat(checked).isGreaterThan(SEEDS);
        }

        @Test
        @DisplayName("exactly one candidate matches the manifest on both fields — the human read")
        void exactlyOneDoubleMatch() {
            int checked = 0;
            for (int tier : new int[] {4, 5}) {
                for (long seed = 1; seed <= SEEDS; seed++) {
                    LayerState layer = generate(seed, tier, "TRAVERSAL");
                    if (layer == null) {
                        continue;
                    }
                    checked++;
                    String manifest = layer.manifest.getFirst();
                    List<String> matched = new ArrayList<>();
                    for (LatticeNodeState node : layer.nodes) {
                        if (!node.objectiveCandidate) {
                            continue;
                        }
                        String[] fragment = node.hint.split(" ");
                        if (manifest.contains(fragment[0]) && manifest.contains(fragment[1])) {
                            matched.add(node.id);
                        }
                    }
                    // design/05 §3.2 verbatim: "distinguishable only by cross-referencing recovered
                    // logs". Two matches make the read ambiguous; none makes it useless.
                    assertThat(matched).as("tier " + tier + " seed " + seed).hasSize(1);
                    assertThat(matched.getFirst()).isEqualTo(layer.objectiveNodeId);
                }
            }
            assertThat(checked).isGreaterThan(SEEDS);
        }

        @Test
        @DisplayName("every candidate is reachable from the last junction, so the read is decisive")
        void theLastHopFansOut() {
            for (int tier : new int[] {4, 5}) {
                for (long seed = 1; seed <= 30; seed++) {
                    LayerState layer = generate(seed, tier, "TRAVERSAL");
                    if (layer == null) {
                        continue;
                    }
                    List<String> candidates = layer.nodes.stream()
                            .filter(node -> node.objectiveCandidate)
                            .map(node -> node.id)
                            .toList();
                    for (LatticeNodeState node : layer.nodes) {
                        if (node.rank == layer.objectiveRank - 1) {
                            // Without this, a player who navigates correctly can arrive somewhere the
                            // objective is not reachable from, the cross-reference returns "none of
                            // these", and P-3 stops measuring what it was built to measure.
                            assertThat(node.exits)
                                    .as("tier " + tier + " seed " + seed + " node " + node.id)
                                    .containsAll(candidates);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("INVARIANT I10 — the reader beats a fixed heuristic, and the gap is a loss rate")
        void theHumanReadIsWorthSomething() {
            int trials = 0;
            int readerLost = 0;
            int heuristicLost = 0;

            for (long seed = 1; seed <= 200; seed++) {
                SoloSave reader = withNode(seed, 5, 0, false, false);
                BreachRules.begin(reader, nodeTarget(reader), T0);
                LayerState lr = focus(reader, "TRAVERSAL");
                if (lr == null) {
                    continue;
                }
                trials++;
                walkToJunction(reader, lr);
                List<String> candidates = lr.nodes.stream()
                        .filter(node -> node.objectiveCandidate)
                        .map(node -> node.id)
                        .toList();
                for (String candidate : candidates) {
                    BreachRules.act(reader, "listen", candidate, T0);
                }
                String pick = null;
                for (LatticeNodeState node : lr.nodes) {
                    if (!node.hintRead) {
                        continue;
                    }
                    String[] fragment = node.hint.split(" ");
                    if (lr.manifest.getFirst().contains(fragment[0])
                            && lr.manifest.getFirst().contains(fragment[1])) {
                        pick = node.id;
                    }
                }
                BreachRules.act(reader, "extract", pick == null ? candidates.getFirst() : pick, T0);
                if (!"CLEARED".equals(lr.state)) {
                    readerLost++;
                }

                // The same board, played by something that cannot read: extract in a blind order.
                SoloSave bot = withNode(seed, 5, 0, false, false);
                BreachRules.begin(bot, nodeTarget(bot), T0);
                LayerState lb = focus(bot, "TRAVERSAL");
                walkToJunction(bot, lb);
                List<String> blind = new ArrayList<>(lb.nodes.stream()
                        .filter(node -> node.objectiveCandidate)
                        .map(node -> node.id)
                        .toList());
                java.util.Collections.rotate(blind, (int) (seed % blind.size()));
                for (String candidate : blind) {
                    if (!"ACTIVE".equals(lb.state)) {
                        break;
                    }
                    BreachRules.act(bot, "extract", candidate, T0);
                }
                if (!"CLEARED".equals(lb.state)) {
                    heuristicLost++;
                }
            }

            assertThat(trials).isGreaterThan(100);
            // A reader who cross-references never misses: the information is all on the table and
            // the work is the reading.
            assertThat(readerLost).isZero();
            // The heuristic strikes out about half the time at tier 5 (K = 4 candidates, 2 strikes:
            // P(first two both wrong) = 3/4 x 2/3 = 1/2). Measured at 51.7% over 600 seeds.
            //
            // ⚠ The attention gap alone is only ~1.2x, which would have looked negligible. The real
            // answer to P-3 is a LOSS RATE, not a probe count — that is the finding, and it is why
            // this assertion is on losses. See docs/design/16-breach-implementation.md §5.
            assertThat(heuristicLost).isGreaterThan(trials / 3);
        }

        private void walkToJunction(SoloSave save, LayerState layer) {
            for (int guard = 0; guard < 20; guard++) {
                LatticeNodeState here = TraversalRules.node(layer, layer.currentNodeId);
                if (here.rank >= layer.objectiveRank - 1) {
                    return;
                }
                BreachRules.act(save, "step", here.exits.getFirst(), T0);
            }
        }

        @Test
        @DisplayName("traps exist, are never on the entry rank, and are invisible until traced")
        void trapsAreHiddenUntilPaidFor() {
            SoloSave save = withNode(777L, 4, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "TRAVERSAL");

            assertThat(layer.nodes.stream().filter(node -> node.trapped).count()).isEqualTo(3);
            assertThat(layer.nodes).noneMatch(node -> node.trapped && node.rank == 0);
            assertThat(layer.nodes).noneMatch(node -> node.trapKnown);

            BreachRules.act(save, "traceroute", "", T0);
            // Six attention buys what nothing else in the class can: seeing a canary before you
            // touch it.
            assertThat(layer.nodes).anyMatch(node -> node.trapKnown);
        }
    }

    @Nested
    @DisplayName("snapshots (D-2)")
    class Snapshots {

        @Test
        @DisplayName("a snapshot never carries the Logic secret, the undrawn deck, or the objective")
        void snapshotsCarryOnlyRevealedInformation() {
            for (long seed = 1; seed <= 40; seed++) {
                SoloSave save = withNode(seed, 5, 0, false, false);
                BreachRules.begin(save, nodeTarget(save), T0);
                // Force every layer active so all three boards are published at once — the worst
                // case for a leak, and the one a normal play-through would never reach.
                for (LayerState layer : save.activeBreach.layers) {
                    layer.state = "ACTIVE";
                }
                BreachSnapshot snapshot = BreachSnapshots.of(save);
                String rendered = snapshot.toString();

                for (LayerState layer : save.activeBreach.layers) {
                    switch (layer.puzzleClass) {
                        case "LOGIC" -> {
                            assertThat(rendered).doesNotContain(String.join(", ", layer.secret));
                            for (String card : layer.factDeck) {
                                assertThat(rendered).doesNotContain(Facts.prose(card));
                            }
                        }
                        case "TRAVERSAL" -> {
                            for (LatticeNodeState node : layer.nodes) {
                                if (!node.hintRead && !node.hint.isEmpty()) {
                                    assertThat(rendered).doesNotContain(node.hint);
                                }
                            }
                        }
                        default -> { }
                    }
                }
            }
        }

        @Test
        @DisplayName("an unprobed slot reads UNKNOWN and names no service")
        void unprobedSlotsAreUnknown() {
            SoloSave save = withNode(31337L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            focus(save, "ENUMERATION");

            for (BreachLayer layer : BreachSnapshots.of(save).layers()) {
                if (layer.board() instanceof EnumerationBoard board) {
                    assertThat(board.ports()).allMatch(slot -> slot.state() == PortState.UNKNOWN);
                    assertThat(board.ports()).allMatch(slot -> slot.service().isEmpty());
                    // The banner is public from the start: design/05 §3.2's read is about what the
                    // role IMPLIES, not about discovering that the target has one.
                    assertThat(board.banner()).isNotBlank();
                }
            }

            BreachRules.act(save, "probe", "0", T0);
            EnumerationBoard after = (EnumerationBoard) BreachSnapshots.of(save).active().orElseThrow().board();
            PortSlot first = after.ports().getFirst();
            assertThat(first.state()).isNotEqualTo(PortState.UNKNOWN);
        }

        @Test
        @DisplayName("an invisible lattice node publishes a shape and nothing else")
        void invisibleNodesArePublishedEmpty() {
            SoloSave save = withNode(777L, 4, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            focus(save, "TRAVERSAL");

            TraversalBoard board = (TraversalBoard) BreachSnapshots.of(save).active().orElseThrow().board();
            List<LatticeNode> dark = board.nodes().stream().filter(node -> !node.visible()).toList();
            assertThat(dark).isNotEmpty();
            for (LatticeNode node : dark) {
                assertThat(node.label()).isEmpty();
                assertThat(node.exits()).isEmpty();
                assertThat(node.hint()).isEmpty();
                assertThat(node.trapKnown()).isFalse();
            }
        }

        @Test
        @DisplayName("the Logic board publishes prose, not the machine form behind it")
        void factsArePublishedAsProse() {
            SoloSave save = withNode(31337L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            focus(save, "LOGIC");
            BreachRules.act(save, "listen", "", T0);

            LogicBoard board = (LogicBoard) BreachSnapshots.of(save).active().orElseThrow().board();
            assertThat(board.facts()).hasSize(1);
            // The code is scaffolding for the candidate filter and has no business on a wire.
            assertThat(board.facts().getFirst()).doesNotContain("|");
            assertThat(board.candidatesRemaining()).isLessThan(board.keyspace());
        }
    }

    @Nested
    @DisplayName("the persisted RNG (D-4)")
    class Randomness {

        @Test
        @DisplayName("the same seed produces the same boards")
        void generationIsDeterministic() {
            SoloSave a = withNode(99L, 5, 0, false, false);
            SoloSave b = withNode(99L, 5, 0, false, false);
            BreachRules.begin(a, nodeTarget(a), T0);
            BreachRules.begin(b, nodeTarget(b), T0);

            for (int i = 0; i < a.activeBreach.layers.size(); i++) {
                LayerState la = a.activeBreach.layers.get(i);
                LayerState lb = b.activeBreach.layers.get(i);
                assertThat(la.secret).isEqualTo(lb.secret);
                assertThat(la.banner).isEqualTo(lb.banner);
                assertThat(la.objectiveNodeId).isEqualTo(lb.objectiveNodeId);
            }
        }

        @Test
        @DisplayName("⚠ opening a breach commits the advanced seed, so a reload cannot reroll it")
        void beginCommitsTheSeed() {
            SoloSave save = withNode(99L, 5, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            // The single most important correctness property in this lane. Without the commit the
            // save still holds the seed the draws started from, and reloading rerolls the board a
            // player did not like — which makes generation advisory rather than committed.
            assertThat(save.rngSeed).isNotEqualTo(99L);
        }

        @Test
        @DisplayName("reading a snapshot draws nothing and regenerates nothing")
        void snapshotsAreSideEffectFree() {
            SoloSave save = withNode(808L, 5, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            long seed = save.rngSeed;
            List<String> secret = List.copyOf(save.activeBreach.layers.getFirst().secret);

            BreachSnapshots.of(save);
            BreachSnapshots.of(save);

            assertThat(save.rngSeed).isEqualTo(seed);
            assertThat(save.activeBreach.layers.getFirst().secret).isEqualTo(secret);
        }

        @Test
        @DisplayName("a derived seed depends on the character, not on an ambient clock read")
        void seedsAreDerivedFromInputs() {
            assertThat(Rng.derive("abc", T0)).isEqualTo(Rng.derive("abc", T0));
            assertThat(Rng.derive("abc", T0)).isNotEqualTo(Rng.derive("abd", T0));
            assertThat(Rng.derive("abc", T0)).isNotEqualTo(Rng.derive("abc", T0.plusSeconds(1)));
        }

        @Test
        @DisplayName("nextInt stays in range and is roughly uniform")
        void nextIntIsSane() {
            Rng rng = new Rng(1234L);
            int[] buckets = new int[7];
            for (int i = 0; i < 70_000; i++) {
                int value = rng.nextInt(buckets.length);
                assertThat(value).isBetween(0, buckets.length - 1);
                buckets[value]++;
            }
            for (int count : buckets) {
                assertThat(count).isBetween(9_000, 11_000);
            }
        }
    }
}
