package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.BreachState;
import io.github.stoicswe.eyeandsickle.solo.state.LatticeNodeState;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.PortSlotState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generates every layer of a breach, once, at the moment the attempt opens.
 *
 * <h2>Generate once, persist, replay nothing (D-4)</h2>
 *
 * All layers are built up front and written into the save, including the ones the player has not
 * reached. The alternative — generating each layer as it becomes active — is worse in a way that is
 * not obvious until it bites: a layer generated on the turn the player reaches it can be rerolled by
 * quitting before that turn, so a player who does not like the board they were dealt reloads until
 * they get one they do. Generating everything at {@code begin} makes the whole attempt a single
 * committed draw, and {@link Rng#commit} is what makes the commitment stick.
 *
 * <h2>The board never depends on the loadout</h2>
 *
 * Nothing here reads the player's items, and that is deliberate rather than incidental. If a board
 * were generated knowing what the player brought, recon would be pointless — {@code
 * docs/design/07-recon-tools.md} §4 says recon's "entire value is reducing variance on the
 * <em>next</em> action", which requires the next action to have been decided before the tools were
 * chosen. Tools change what the player can <em>do</em> to a board, never what the board is.
 *
 * <h2>Defences shape the budget, not the board</h2>
 *
 * A Firewall's "flat difficulty increase" ({@code docs/design/09-defense-and-hardening.md} §1) is
 * applied here as attention taken off every layer, floored so no layer can be made unwinnable. A
 * Tarpit is <em>not</em> applied here at all: it surcharges each action at spend time, which is what
 * "slows every intruder action" means once §4 removed the clock. See {@link BreachRules} for that
 * half.
 */
public final class BoardFactory {

    private BoardFactory() {}

    /**
     * Builds every layer of {@code breach} from its tier and defence profile.
     *
     * <p>⚠ The caller must {@link Rng#commit} after this returns. A generated-and-uncommitted board
     * is a board the next load will generate differently.
     *
     * <p>Takes no {@code SoloSave}: see the class note on why generation must not be able to see the
     * player's loadout. A parameter that exists only to be ignored is an invitation to reach through
     * it later.
     */
    public static void build(BreachState breach, Rng rng) {
        int tier = breach.difficultyTier;
        List<String> classes = Balance.breachClasses(tier);
        int layers = Math.min(classes.size(), Balance.breachLayers(tier));

        for (int i = 0; i < layers; i++) {
            String puzzleClass = classes.get(i);
            LayerState layer = switch (puzzleClass) {
                case "LOGIC" -> logic(i, tier, rng);
                case "TRAVERSAL" -> traversal(i, tier, rng);
                default -> enumeration(i, tier, rng);
            };
            layer.budget = budgetFor(tier, breach.targetFirewallTier);
            layer.strikeLimit = Balance.breachStrikeLimit(tier);
            layer.state = i == 0 ? "ACTIVE" : "PENDING";
            breach.layers.add(layer);
        }
        // The banner is the target's role, and the player learns it the moment the attempt opens
        // rather than by probing for it — docs/design/05 §3.2's human read is about what the role
        // *implies*, not about discovering that the target has one.
        for (LayerState layer : breach.layers) {
            if ("ENUMERATION".equals(layer.puzzleClass) && breach.targetRole.isEmpty()) {
                breach.targetRole = layer.banner;
            }
        }
    }

    /**
     * A layer's attention budget after the Firewall penalty, floored.
     *
     * <p>The floor is not defensive programming; it is a design rule. A budget driven low enough
     * that no sequence of legal moves clears the layer is the game deciding, which is the one
     * reading {@code docs/design/05-hacking-minigame.md} §1 constraint 4 forbids outright.
     */
    static int budgetFor(int tier, int firewallTier) {
        int penalty = Balance.FIREWALL_BUDGET_PENALTY_PER_TIER * Math.max(0, firewallTier);
        return Math.max(Balance.BREACH_ATTENTION_FLOOR, Balance.breachAttention(tier) - penalty);
    }

    // ================================================================== ENUMERATION

    /**
     * The four service banners and the generation rule each one imposes.
     *
     * <p>This is Enumeration's human-read step (D-8, satisfying {@code
     * docs/design/05-hacking-minigame.md} §3.2's requirement that every class have one). The rule is
     * printed nowhere the player can read it: they learn it by playing, and a fixed heuristic never
     * learns it at all, which is §3.2(d) — "cannot use the 'intuition' shortcuts a human gets from
     * reading flavor data" — made mechanical rather than asserted.
     *
     * <p>The rules are stated for the designer in {@code docs/design/16-breach-implementation.md} §3.
     * ⚠ Do not put them in a term page, a tooltip or a {@code man} entry. The moment the rule is
     * published the read becomes a lookup and the class loses the thing that distinguishes it.
     */
    private static final List<String> BANNERS =
            List.of("EDGE RELAY", "STORAGE ARRAY", "AUTH BROKER", "MEDIA CACHE");

    /**
     * Service names for open slots.
     *
     * <p>⚠ Real service names, deliberately <em>not</em> paired with real port numbers. A slot index
     * on this board is a slot index and nothing else — the game never claims that slot 22 is ssh,
     * because that would be a factual assertion about the world, and {@code CLAUDE.md} is explicit
     * that a wrong mapping teaches something false, which is worse than teaching nothing. If the
     * curriculum ever wants to teach well-known ports it does so in {@code docs/education/05}, with
     * a source and a date, and this table follows it rather than inventing it.
     */
    private static final List<String> SERVICES =
            List.of("ssh", "http", "https", "smtp", "dns", "ntp", "rsync", "ldap", "redis", "postgres");

    static LayerState enumeration(int index, int tier, Rng rng) {
        LayerState layer = new LayerState();
        layer.index = index;
        layer.puzzleClass = "ENUMERATION";
        layer.slots = Balance.BREACH_ENUM_SLOTS_BASE + Balance.BREACH_ENUM_SLOTS_PER_TIER * (tier - 1);
        layer.bandSize = Balance.BREACH_ENUM_BAND_SIZE;

        String banner = rng.pick(BANNERS);
        layer.banner = banner;
        layer.bannerNote = bannerNote(banner);

        int openCount = 2 + (tier + 1) / 2;
        int filteredCount = tier;
        List<Integer> open = openSet(banner, layer.slots, layer.bandSize, openCount, rng);

        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < layer.slots; i++) {
            if (!open.contains(i)) {
                rest.add(i);
            }
        }
        List<Integer> filtered = new ArrayList<>();
        take(rest, Math.min(filteredCount, rest.size()), false, filtered, rng);

        List<String> services = new ArrayList<>(SERVICES);
        rng.shuffle(services);
        int nextService = 0;
        for (int i = 0; i < layer.slots; i++) {
            PortSlotState slot = new PortSlotState();
            slot.index = i;
            if (open.contains(i)) {
                slot.truth = "OPEN";
                slot.service = services.get(nextService++ % services.size());
            } else if (filtered.contains(i)) {
                slot.truth = "FILTERED";
            } else {
                slot.truth = "CLOSED";
            }
            layer.ports.add(slot);
        }
        return layer;
    }

    /**
     * Places the open ports so the banner's rule holds.
     *
     * <p>Constructive rather than rejection-sampled. Rejection sampling would consume a variable
     * number of draws depending on how lucky it got, which makes the RNG stream depend on the values
     * it produced — and a stream whose shape can change under refactoring is not the reproducibility
     * guarantee {@link Rng} exists to give.
     */
    private static List<Integer> openSet(String banner, int slots, int bandSize, int openCount, Rng rng) {
        List<Integer> open = new ArrayList<>();
        int bands = (slots + bandSize - 1) / bandSize;
        int lastBandStart = (bands - 1) * bandSize;

        switch (banner) {
            case "EDGE RELAY" -> {
                // At least one open in the last band; never any in band 0. A relay's exposed
                // surface is at the far end of its slot range, and its front door is not a port.
                take(range(lastBandStart, slots), 1, false, open, rng);
                take(range(bandSize, slots), openCount, false, open, rng);
            }
            case "STORAGE ARRAY" -> {
                // No open port in band 0, and exactly one adjacent pair somewhere — a data plane
                // and its replica sitting next to each other.
                List<Integer> pairStarts = range(bandSize, slots - 1);
                int start = pairStarts.isEmpty() ? bandSize : rng.pick(pairStarts);
                open.add(start);
                open.add(start + 1);
                take(range(bandSize, slots), openCount, true, open, rng);
            }
            case "AUTH BROKER" -> {
                // Exactly one open port in band 1, and no two open ports adjacent anywhere: a broker
                // presents one door and keeps everything else apart.
                take(range(bandSize, Math.min(bandSize * 2, slots)), 1, false, open, rng);
                List<Integer> outsideBandOne = new ArrayList<>();
                for (int i = 0; i < slots; i++) {
                    if (i < bandSize || i >= bandSize * 2) {
                        outsideBandOne.add(i);
                    }
                }
                take(outsideBandOne, openCount, true, open, rng);
            }
            default -> {
                // MEDIA CACHE: every open port sits on an even slot. Evens are never adjacent, so
                // the no-adjacency property comes free and is not the tell.
                List<Integer> evens = new ArrayList<>();
                for (int i = 0; i < slots; i += 2) {
                    evens.add(i);
                }
                take(evens, openCount, false, open, rng);
            }
        }
        return open;
    }

    /**
     * One line of role flavour that gestures at the rule without stating it.
     *
     * <p>The line has to be readable as atmosphere by someone who has not worked the rule out, and
     * as a hint by someone who has started to. A line that states the rule removes the read; a line
     * with no relationship to it makes the banner decoration.
     */
    private static String bannerNote(String banner) {
        return switch (banner) {
            case "EDGE RELAY" -> "Traffic terminates here on its way somewhere else. Nothing listens at the front.";
            case "STORAGE ARRAY" -> "Bulk storage. Whatever it serves, it serves twice.";
            case "AUTH BROKER" -> "It answers one question for everyone, and keeps its answers well apart.";
            default -> "A cache. Regular as a metronome, and about as imaginative.";
        };
    }

    // ================================================================== LOGIC

    /**
     * The Logic alphabet: ten ASCII symbols, of which a board uses a prefix.
     *
     * <p>All ASCII, therefore unconditionally present in both bundled faces — {@code
     * GlyphCoverageTest} scans this module's literals as well as the client's, and a symbol the
     * bundled font lacks would be drawn by a host-OS fallback with its own advance width, which
     * breaks character-cell layout differently on every platform.
     *
     * <p>⚠ {@code #} is deliberately absent. {@code UiContractTest.noHexInJava} fails the build on
     * {@code #} followed by three to eight hex characters anywhere under the client's {@code ui} or
     * {@code view} packages, and an alphabet containing {@code #} is one rendering away from
     * producing exactly that in a source literal.
     */
    static final List<String> LOGIC_ALPHABET =
            List.of("@", "$", "%", "&", "*", "+", "=", "~", "?", "!");

    static LayerState logic(int index, int tier, Rng rng) {
        LayerState layer = new LayerState();
        layer.index = index;
        layer.puzzleClass = "LOGIC";

        int length = Balance.BREACH_LOGIC_LENGTH_BASE + tier / 2;
        int alphabetSize = Math.min(LOGIC_ALPHABET.size(), Balance.BREACH_LOGIC_ALPHABET_BASE + tier);
        layer.alphabet = new ArrayList<>(LOGIC_ALPHABET.subList(0, alphabetSize));

        for (int i = 0; i < length; i++) {
            layer.secret.add(layer.alphabet.get(rng.nextInt(alphabetSize)));
            layer.known.add("");
            layer.draft.add("");
        }

        // Always salted from tier 3 up, and a coin-weighted chance below it. Both halves matter:
        // docs/design/06 §2 wants the Rainbow Table "devastating against lazy targets, useless
        // against prepared ones", and a low tier that is never salted makes it unconditional on
        // exactly the targets a new owner can reach.
        layer.salted = tier >= Balance.BREACH_LOGIC_ALWAYS_SALTED_TIER
                || rng.nextDouble() < Balance.BREACH_LOGIC_SALT_CHANCE;

        layer.keyspace = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.pow(alphabetSize, length)));
        layer.candidatesRemaining = layer.keyspace;
        layer.factDeck = facts(layer, rng);
        return layer;
    }

    /**
     * The {@code listen} deck: statements that are true of this particular secret.
     *
     * <p>Every card is checked against the secret as it is written, so a quiet read can never
     * mislead. That is not politeness — {@code docs/design/05-hacking-minigame.md} §4 prices a quiet
     * read at 1 attention as "the patient baseline", and a baseline that sometimes lies is not a
     * baseline, it is a second kind of gamble. The Fuzzer is where impatience is punished; this is
     * where patience is paid.
     */
    private static List<String> facts(LayerState layer, Rng rng) {
        List<String> secret = layer.secret;
        List<String> deck = new ArrayList<>();

        Set<String> distinct = new LinkedHashSet<>(secret);
        deck.add(Facts.distinct(distinct.size()));
        deck.add(Facts.repeats(distinct.size() != secret.size()));

        String present = rng.pick(new ArrayList<>(distinct));
        int count = (int) secret.stream().filter(present::equals).count();
        deck.add(Facts.count(present, count));

        List<String> absent = new ArrayList<>();
        for (String symbol : layer.alphabet) {
            if (!distinct.contains(symbol)) {
                absent.add(symbol);
            }
        }
        if (!absent.isEmpty()) {
            deck.add(Facts.absent(rng.pick(absent)));
        }

        // Two negative positional facts. Negatives rather than positives: a card that named a
        // position outright would do the Rainbow Table's job for 1 attention, and the Table is
        // schematic-gated (docs/design/06 §1) precisely because that capability is not cheap.
        for (int i = 0; i < 2; i++) {
            int position = rng.nextInt(secret.size());
            List<String> wrong = new ArrayList<>(layer.alphabet);
            wrong.remove(secret.get(position));
            if (!wrong.isEmpty()) {
                deck.add(Facts.notAt(position, rng.pick(wrong)));
            }
        }

        rng.shuffle(deck);
        return deck;
    }

    // ================================================================== TRAVERSAL

    private static final List<String> HOST_PREFIXES =
            List.of("gw", "db", "fs", "mx", "ns", "rt", "ap", "lb");

    private static final List<String> LATTICE_SERVICES =
            List.of("svc-ledger", "svc-media", "svc-auth", "svc-index", "svc-relay", "svc-queue");

    private static final List<String> LOG_VERBS =
            List.of("wrote", "flushed", "rotated", "replicated");

    static LayerState traversal(int index, int tier, Rng rng) {
        LayerState layer = new LayerState();
        layer.index = index;
        layer.puzzleClass = "TRAVERSAL";
        layer.ranks = Balance.BREACH_TRAVERSAL_RANKS_BASE + tier / 2;
        layer.objectiveRank = layer.ranks - 1;
        int objectives = Balance.BREACH_TRAVERSAL_OBJECTIVES_BASE + tier / 2;

        List<List<LatticeNodeState>> byRank = new ArrayList<>();
        for (int rank = 0; rank < layer.ranks; rank++) {
            int width;
            if (rank == 0) {
                // One entry point. The lattice is a fan-out from where the player already is, not a
                // choice of front doors — a breach starts inside, having already got through.
                width = 1;
            } else if (rank == layer.objectiveRank) {
                width = objectives;
            } else {
                width = Balance.BREACH_TRAVERSAL_WIDTH_MIN
                        + rng.nextInt(Balance.BREACH_TRAVERSAL_WIDTH_MAX - Balance.BREACH_TRAVERSAL_WIDTH_MIN + 1);
            }
            List<LatticeNodeState> row = new ArrayList<>();
            for (int i = 0; i < width; i++) {
                LatticeNodeState node = new LatticeNodeState();
                node.id = "n" + rank + "-" + i;
                node.rank = rank;
                node.index = i;
                node.label = rng.pick(HOST_PREFIXES) + "-" + String.format(Locale.ROOT, "%02d", rng.nextInt(100));
                node.objectiveCandidate = rank == layer.objectiveRank;
                row.add(node);
            }
            byRank.add(row);
        }

        wire(byRank, layer, rng);
        traps(byRank, layer, tier, rng);
        logs(byRank, layer, objectives, rng);

        for (List<LatticeNodeState> row : byRank) {
            layer.nodes.addAll(row);
        }

        // The entry node is where the player already stands: visited, visible, and its immediate
        // exits named. Everything beyond is dark until it is paid for.
        LatticeNodeState entry = byRank.getFirst().getFirst();
        entry.visited = true;
        entry.visible = true;
        layer.currentNodeId = entry.id;
        for (LatticeNodeState node : layer.nodes) {
            if (entry.exits.contains(node.id)) {
                node.visible = true;
            }
        }
        return layer;
    }

    /**
     * Forward edges only, every node reachable, with occasional rank-skipping shortcuts.
     *
     * <h2>⚠ The last hop fans out to every candidate, and that is load-bearing</h2>
     *
     * Every node on the penultimate rank exits to <em>all</em> objective candidates. Without it, a
     * player who navigates correctly can arrive at a junction from which the true objective is not
     * reachable — so the log cross-reference returns "none of these", and the layer becomes a
     * backtracking exercise instead of the read {@code docs/design/05-hacking-minigame.md} §3.2
     * specifies. Measured: a tier-5 board generated that case on the first seed tried.
     *
     * <p>It also keeps <b>P-3</b> honest. D-9's arithmetic is "a fixed heuristic must extract at
     * random among K candidates, expected {@code (K+1)/2} attempts, while a reader gets it in one".
     * That comparison only holds if both are choosing from the same K. With a partial fan-out, both
     * players sometimes face a set that does not contain the answer at all, and the measurement
     * stops measuring the thing it was built to measure.
     *
     * <p>The effect on play is a clean split: the ranks before the last are the pathfinding, and the
     * last hop is the read. Nothing is lost, because the decoys were never meant to be a maze.
     */
    private static void wire(List<List<LatticeNodeState>> byRank, LayerState layer, Rng rng) {
        for (int rank = 0; rank < layer.ranks - 1; rank++) {
            List<LatticeNodeState> next = byRank.get(rank + 1);
            boolean intoObjective = rank + 1 == layer.objectiveRank;
            for (LatticeNodeState node : byRank.get(rank)) {
                if (intoObjective) {
                    for (LatticeNodeState candidate : next) {
                        node.exits.add(candidate.id);
                    }
                    continue;
                }
                int outDegree = 1 + rng.nextInt(Math.min(3, next.size()));
                List<LatticeNodeState> shuffled = new ArrayList<>(next);
                rng.shuffle(shuffled);
                for (int i = 0; i < outDegree; i++) {
                    node.exits.add(shuffled.get(i).id);
                }
                // An occasional diagonal skip, never into the objective rank. A shortcut past the
                // final rank's decoys would let a player reach an extract without ever standing
                // where the logs are readable, which is the one step docs/design/05 §3.2 requires.
                if (rank + 2 < layer.objectiveRank && rng.nextInt(4) == 0) {
                    node.exits.add(rng.pick(byRank.get(rank + 2)).id);
                }
            }
            // Nothing may be stranded: an unreachable node is a rendered lie, and on the objective
            // rank it could be the objective itself, which would make the layer unwinnable.
            for (LatticeNodeState target : next) {
                boolean reachable = byRank.get(rank).stream().anyMatch(n -> n.exits.contains(target.id));
                if (!reachable) {
                    rng.pick(byRank.get(rank)).exits.add(target.id);
                }
            }
        }
    }

    /** Canary-trapped decoys and tarpit nodes, never on the entry rank. */
    private static void traps(List<List<LatticeNodeState>> byRank, LayerState layer, int tier, Rng rng) {
        List<LatticeNodeState> candidates = new ArrayList<>();
        for (int rank = 1; rank < layer.ranks; rank++) {
            candidates.addAll(byRank.get(rank));
        }
        rng.shuffle(candidates);

        int trapped = Math.min(Math.max(0, tier - 1), candidates.size());
        for (int i = 0; i < trapped; i++) {
            candidates.get(i).trapped = true;
        }
        // Tarpit nodes charge extra on entry — docs/design/09 §1's "slows every intruder action"
        // expressed per-node, since docs/design/05 §4 left no clock for "slow" to mean anything else.
        int tarpits = Math.min(Math.max(0, tier - 2), candidates.size());
        for (int i = candidates.size() - tarpits; i < candidates.size(); i++) {
            candidates.get(i).stepCost = Balance.BREACH_TRAVERSAL_TARPIT_STEP_COST;
        }
    }

    /**
     * The manifest and the candidates' recovered logs — the human read, verbatim from {@code
     * docs/design/05-hacking-minigame.md} §3.2.
     *
     * <p>The manifest names one service and one time. Every objective candidate carries a log
     * fragment naming a service and a time. <b>Exactly one candidate matches both.</b> The others
     * match one field or neither, which is what makes reading beat guessing: a player who
     * cross-references gets it in one, and a fixed heuristic that cannot read has to extract at
     * random among K candidates.
     *
     * <p>⚠ That difference <em>is</em> P-3 ({@code 05} §6, the number behind Invariant I10). It is
     * built to be measurable and must not be tuned away — see
     * {@code docs/design/16-breach-implementation.md} §5.
     */
    private static void logs(
            List<List<LatticeNodeState>> byRank, LayerState layer, int objectives, Rng rng) {
        List<LatticeNodeState> candidates = byRank.get(layer.objectiveRank);

        String trueService = rng.pick(LATTICE_SERVICES);
        int hour = rng.nextInt(24);
        int minute = rng.nextInt(60);
        String trueTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute);

        int objectiveIndex = rng.nextInt(candidates.size());
        layer.objectiveNodeId = candidates.get(objectiveIndex).id;

        List<String> otherServices = new ArrayList<>(LATTICE_SERVICES);
        otherServices.remove(trueService);

        for (int i = 0; i < candidates.size(); i++) {
            LatticeNodeState node = candidates.get(i);
            String service;
            String time;
            if (i == objectiveIndex) {
                service = trueService;
                time = trueTime;
            } else if (i % 2 == 0) {
                // Matches the service, not the time. A single-field match is the trap: it is exactly
                // what a reader who skims one column of the manifest would accept.
                service = trueService;
                time = String.format(Locale.ROOT, "%02d:%02d", (hour + 1 + rng.nextInt(6)) % 24, rng.nextInt(60));
            } else {
                service = rng.pick(otherServices);
                time = i % 4 == 1 ? trueTime : String.format(Locale.ROOT, "%02d:%02d", rng.nextInt(24), rng.nextInt(60));
            }
            node.hint = time + " " + service + " " + rng.pick(LOG_VERBS) + " " + (1 + rng.nextInt(9)) + " blocks";
        }

        layer.manifest.add("last write " + trueTime + " on " + trueService);
        layer.manifest.add("objective rank holds " + objectives + " candidates; one of them wrote it");
        layer.manifest.add("listen at a candidate to recover its log; the manifest is the other half");
    }

    // ================================================================== helpers

    /** {@code [from, toExclusive)} as a list, clamped to non-negative. */
    private static List<Integer> range(int from, int toExclusive) {
        List<Integer> out = new ArrayList<>();
        for (int i = Math.max(0, from); i < toExclusive; i++) {
            out.add(i);
        }
        return out;
    }

    /**
     * Fills {@code into} up to {@code target} entries from {@code pool}, shuffled.
     *
     * <p>{@code noAdjacent} rejects any slot touching one already chosen, which is how the AUTH
     * BROKER and STORAGE ARRAY rules are enforced constructively. If the pool runs out before the
     * target is met the set is simply smaller — a board with one fewer open port is still a valid
     * board, whereas retrying until it fits would make the draw count depend on the draws.
     */
    private static void take(List<Integer> pool, int target, boolean noAdjacent, List<Integer> into, Rng rng) {
        List<Integer> shuffled = new ArrayList<>(pool);
        rng.shuffle(shuffled);
        for (int candidate : shuffled) {
            if (into.size() >= target) {
                return;
            }
            if (into.contains(candidate)) {
                continue;
            }
            if (noAdjacent && (into.contains(candidate - 1) || into.contains(candidate + 1))) {
                continue;
            }
            into.add(candidate);
        }
    }
}
