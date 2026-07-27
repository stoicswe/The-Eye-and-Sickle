package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.ProbeState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Logic class: reconstruct a lock's rule from probe responses.
 *
 * <h2>Mastermind, and the classic bug it is built around avoiding</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §3.1 names the family — "deductive reasoning
 * (Mastermind-family)". The feedback rule is exact:
 *
 * <pre>
 *   exact   = |{ i : guess[i] == secret[i] }|
 *   matched = sum over symbols s of min( count(guess, s), count(secret, s) )
 *   partial = matched - exact
 * </pre>
 *
 * The bug this avoids is counting a repeated symbol more than once — scoring {@code @@$} against
 * {@code @$$} as two partials for the {@code $}, say. It looks harmless and it is not: the feedback
 * stops being consistent with itself, so a player deducing correctly reaches a contradiction, and
 * the class silently converts from reasoning into guessing. {@link #feedback} is written against the
 * multiset-minimum form for that reason and is covered by a test with repeats in both operands.
 *
 * <h2>Being wrong on purpose costs a strike</h2>
 *
 * A guess that is <em>provably impossible</em> given every earlier response is marked inconsistent
 * and costs a strike on top of its attention. That single rule is what makes this deduction rather
 * than enumeration of the keyspace: without it, the optimal play at every tier is to fire ordinary
 * probes at 2 attention until the budget runs out and hope, and the "reasoning" in §3.1 would be
 * optional flavour.
 *
 * <p>Note what it is not: it does not punish a guess that turns out to be wrong. Any consistent
 * guess is a legitimate deduction that happened to lose. It punishes a guess the player already had
 * the information to rule out.
 *
 * <h2>{@code KEYSPACE 4096 -> 37} is the class's diegetic soul</h2>
 *
 * {@link #candidatesRemaining} is a real number, recomputed from the real responses. It is the thing
 * that shows a player that deduction is doing something guessing would not, in the one place where
 * that is otherwise invisible, and it is why the readout exists at all rather than a progress bar.
 */
public final class LogicRules {

    private LogicRules() {}

    /**
     * A ceiling on the keyspace walk.
     *
     * <p>The largest board this game generates is 10 symbols in 5 positions — 100 000 candidates,
     * walked once per probe, which is a few milliseconds in a turn-based system. The guard is here
     * so that a hand-edited save claiming a twelve-position code makes the readout say "unknown"
     * rather than freezing the client, and {@code -1} is how the board publishes "not computed".
     */
    private static final int MAX_ENUMERATED_KEYSPACE = 400_000;

    static Move act(LayerState layer, String actionId, String argument, Rng rng) {
        return switch (actionId) {
            case "set" -> set(layer, argument);
            case "listen" -> listen(layer);
            case "probe" -> probe(layer);
            case "volley" -> volley(layer, rng);
            case "rainbow" -> rainbow(layer, rng);
            case "harvest" -> harvest(layer);
            default -> Move.of("nothing happened");
        };
    }

    /**
     * Composes the draft. Bookkeeping: arranging your own guess is not a move against the target.
     *
     * <p>Positions are 1-based here and in every fact the {@code listen} deck produces, because they
     * are 1-based in the fiction the player is reading. The conversion happens once, at this
     * boundary, rather than in each caller.
     */
    private static Move set(LayerState layer, String argument) {
        String[] parts = (argument == null ? "" : argument.trim()).split(":", 2);
        int position = EnumerationRules.parseInt(parts[0], 0) - 1;
        if (position < 0 || position >= layer.draft.size()) {
            return Move.bookkeeping("no such position: " + argument);
        }
        String symbol = parts.length > 1 ? parts[1].trim() : "";
        if (!symbol.isEmpty() && !layer.alphabet.contains(symbol)) {
            return Move.bookkeeping(symbol + " is not in this lock's alphabet");
        }
        layer.draft.set(position, symbol);
        return Move.bookkeeping("position " + (position + 1) + " set to " + (symbol.isEmpty() ? "blank" : symbol));
    }

    /**
     * A quiet read: draws one true statement about the code.
     *
     * <p>Every card was checked against the secret when the deck was built, so the patient baseline
     * never lies. See {@code BoardFactory#facts} for why that is a rule rather than a courtesy.
     */
    private static Move listen(LayerState layer) {
        if (layer.factDeck.isEmpty()) {
            return Move.of("nothing more to overhear on this layer");
        }
        String fact = layer.factDeck.removeFirst();
        layer.facts.add(fact);
        // Recounted, so the keyspace readout actually moves when the player pays for information.
        // See Facts' class note: a quiet read that leaves the one instrument unchanged teaches the
        // player that listening does nothing, which is how the patient half of docs/design/05 §4's
        // trade dies.
        recount(layer);
        return Move.of(Facts.prose(fact));
    }

    /** Submits the draft as an ordinary probe — the default move at the default price. */
    private static Move probe(LayerState layer) {
        List<String> guess = List.copyOf(layer.draft);
        if (guess.size() != layer.secret.size() || guess.contains("")) {
            return Move.of("the draft is incomplete; nothing was sent");
        }
        int[] score = feedback(guess, layer.secret);
        boolean inconsistent = !consistentWithHistory(guess, layer.probes);

        ProbeState state = new ProbeState();
        state.sequence = layer.probes.size() + 1;
        state.guess = new ArrayList<>(guess);
        state.exact = score[0];
        state.partial = score[1];
        state.inconsistent = inconsistent;
        layer.probes.add(state);
        recount(layer);

        String line = score[0] + " exact, " + score[1] + " partial";
        if (score[0] == layer.secret.size()) {
            return Move.cleared("the lock opens: " + String.join(" ", guess));
        }
        if (inconsistent) {
            // The response is still honest and still narrows the field — the strike is for having
            // spent a move on something the earlier responses had already ruled out.
            return Move.strike(line + " - INCONSISTENT with what you already knew");
        }
        return Move.of(line);
    }

    /**
     * A Fuzzer volley: four engine-generated guesses, exact counts only.
     *
     * <p>{@code docs/design/06-intrusion-tools.md} §2 calls the Fuzzer "the entry-level 'I don't know
     * the rule, so I'll hammer it' tool", with "moderate noise as the cost of impatience". Four
     * guesses for 6 attention is breadth at three times a probe's price, and the breadth is paid for
     * in <em>quality</em>: no partials, so each response constrains far less than an ordinary
     * probe's would.
     *
     * <p>The exact counts are real and do feed the consistency filter, so a volley is never wasted
     * — it is simply the wrong tool for a player who is close.
     *
     * <p>From tier 4 the hammer sets off alarms by itself, which is where {@code
     * docs/design/05-hacking-minigame.md} §3.2's "a defended/high-tier node can be built to defeat a
     * fixed strategy" stops being a claim and becomes a rule: at the top tiers, brute force does not
     * scale, because it is loud.
     */
    private static Move volley(LayerState layer, Rng rng) {
        List<String> exacts = new ArrayList<>();
        boolean solved = false;
        String winner = "";
        for (int i = 0; i < Balance.BREACH_LOGIC_VOLLEY_SIZE; i++) {
            List<String> guess = new ArrayList<>();
            for (int p = 0; p < layer.secret.size(); p++) {
                guess.add(layer.alphabet.get(rng.nextInt(layer.alphabet.size())));
            }
            int[] score = feedback(guess, layer.secret);

            ProbeState state = new ProbeState();
            state.sequence = layer.probes.size() + 1;
            state.guess = guess;
            state.exact = score[0];
            state.partial = -1;
            state.volley = true;
            layer.probes.add(state);
            exacts.add(String.valueOf(score[0]));

            if (score[0] == layer.secret.size()) {
                solved = true;
                winner = String.join(" ", guess);
            }
        }
        recount(layer);

        if (solved) {
            return Move.cleared("the fuzzer got lucky: " + winner);
        }
        String line = "volley: exact " + String.join(", ", exacts) + " (no partials)";
        if (layer.index >= 0 && layer.strikeLimit > 0 && volleyIsLoud(layer)) {
            return Move.strike(line + " - the noise woke something");
        }
        return Move.of(line);
    }

    /**
     * Whether a volley trips an alarm on its own.
     *
     * <p>Read off the board's own difficulty rather than a stored tier: a strike limit of 2 is what
     * {@code Balance.breachStrikeLimit} produces at tiers 4 and 5, which is exactly the range
     * {@code Balance.BREACH_LOGIC_VOLLEY_ALARM_TIER} names. Deriving it keeps the two tables from
     * having to be consulted together at run time, and a board loaded from an old save carries its
     * own difficulty with it.
     */
    private static boolean volleyIsLoud(LayerState layer) {
        return layer.strikeLimit <= Balance.breachStrikeLimit(Balance.BREACH_LOGIC_VOLLEY_ALARM_TIER);
    }

    /**
     * The Rainbow Table: two positions, or nothing at all.
     *
     * <p>{@code docs/design/06-intrusion-tools.md} §2 makes this "hard-countered by salting, by
     * design ... devastating against lazy targets, useless against prepared ones, so it rewards
     * recon (know before you buy the attempt)". Against a salted code it costs zero attention and
     * says so, because a tool that silently did nothing would be indistinguishable from a bug and
     * would teach the player to distrust the readout instead of the target.
     *
     * <p>Two positions rather than the whole code: a full reveal is the Overflow Kit's job, and the
     * Kit is proof-of-skill-gated ({@code 02} §2.4) precisely so that an ethecoin-plus-schematic item
     * cannot do it.
     */
    private static Move rainbow(LayerState layer, Rng rng) {
        if (layer.salted) {
            return Move.refunded("salted - the table is useless here");
        }
        List<Integer> unknown = new ArrayList<>();
        for (int i = 0; i < layer.known.size(); i++) {
            if (layer.known.get(i).isEmpty()) {
                unknown.add(i);
            }
        }
        if (unknown.isEmpty()) {
            return Move.of("every position this table can reach is already known");
        }
        List<String> revealed = new ArrayList<>();
        for (int i = 0; i < Balance.BREACH_RAINBOW_REVEALS && !unknown.isEmpty(); i++) {
            int position = unknown.remove(rng.nextInt(unknown.size()));
            layer.known.set(position, layer.secret.get(position));
            revealed.add("position " + (position + 1) + " is " + layer.secret.get(position));
        }
        recount(layer);
        return Move.of(String.join("; ", revealed));
    }

    /**
     * The Credential Harvester: which symbols appear at all.
     *
     * <p>{@code docs/design/06-intrusion-tools.md} §1 gives its function as stealing credentials
     * "usable on linked nodes", and §2 as "harvested creds open linked nodes without re-solving the
     * rule". Inside a layer that becomes the deduction step it skips: knowing the symbol set turns
     * an alphabet-of-ten problem into an alphabet-of-three one without saying where anything goes.
     */
    private static Move harvest(LayerState layer) {
        Set<String> distinct = new LinkedHashSet<>(layer.secret);
        List<String> ordered = new ArrayList<>();
        for (String symbol : layer.alphabet) {
            if (distinct.contains(symbol)) {
                ordered.add(symbol);
            }
        }
        String fact = Facts.only(ordered);
        if (layer.facts.contains(fact)) {
            return Move.of("the harvester has nothing new on this lock");
        }
        layer.facts.add(fact);
        recount(layer);
        return Move.of(Facts.prose(fact));
    }

    // ------------------------------------------------------------------ the arithmetic

    /**
     * Mastermind feedback: {@code {exact, partial}}.
     *
     * <p>Each symbol is matched at most once across the two operands — that is the multiset minimum
     * in the class note, and getting it wrong is the classic bug that makes the feedback inconsistent
     * with itself. Symmetric in its arguments, which is what lets {@link #consistentWithHistory}
     * test a candidate against a past probe without needing to know which was the secret.
     */
    public static int[] feedback(List<String> guess, List<String> secret) {
        int exact = 0;
        for (int i = 0; i < Math.min(guess.size(), secret.size()); i++) {
            if (guess.get(i).equals(secret.get(i))) {
                exact++;
            }
        }
        Map<String, Integer> guessCounts = counts(guess);
        Map<String, Integer> secretCounts = counts(secret);
        int matched = 0;
        for (Map.Entry<String, Integer> entry : guessCounts.entrySet()) {
            matched += Math.min(entry.getValue(), secretCounts.getOrDefault(entry.getKey(), 0));
        }
        return new int[] {exact, matched - exact};
    }

    private static Map<String, Integer> counts(List<String> symbols) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String symbol : symbols) {
            out.merge(symbol, 1, Integer::sum);
        }
        return out;
    }

    /**
     * Whether {@code candidate} could still be the secret given every earlier response.
     *
     * <p>No enumeration needed: if the candidate were the secret, each past probe would have scored
     * exactly {@code feedback(pastGuess, candidate)}, so the test is a direct comparison against what
     * that probe actually scored. Volley rows carry no partial, so only their exact count is
     * compared — and an inconsistent row is skipped entirely, because it was already established as
     * impossible and would otherwise rule out every candidate including the true one.
     */
    public static boolean consistentWithHistory(List<String> candidate, List<ProbeState> history) {
        for (ProbeState past : history) {
            if (past.inconsistent) {
                continue;
            }
            int[] score = feedback(past.guess, candidate);
            if (score[0] != past.exact) {
                return false;
            }
            if (!past.volley && score[1] != past.partial) {
                return false;
            }
        }
        return true;
    }

    /**
     * Recomputes {@code candidatesRemaining} by walking the keyspace.
     *
     * <p>From scratch each time rather than incrementally, because the candidate <em>set</em> is far
     * too large to persist in a save file and a stale count is worse than a recomputed one: this
     * number is the player's evidence that deduction is working, and a readout that drifted from the
     * real board would be teaching them to distrust the only instrument they have.
     *
     * <p>Revealed positions count as constraints too, which is why the Rainbow Table's two positions
     * visibly collapse the number.
     */
    static void recount(LayerState layer) {
        int length = layer.secret.size();
        int alphabet = layer.alphabet.size();
        if (length == 0 || alphabet == 0) {
            layer.candidatesRemaining = 0;
            return;
        }
        long total = 1L;
        for (int i = 0; i < length; i++) {
            total *= alphabet;
            if (total > MAX_ENUMERATED_KEYSPACE) {
                layer.candidatesRemaining = -1;
                return;
            }
        }
        int remaining = 0;
        List<String> candidate = new ArrayList<>(java.util.Collections.nCopies(length, ""));
        for (long index = 0; index < total; index++) {
            long rest = index;
            for (int position = 0; position < length; position++) {
                candidate.set(position, layer.alphabet.get((int) (rest % alphabet)));
                rest /= alphabet;
            }
            if (!matchesKnown(candidate, layer.known)) {
                continue;
            }
            if (!matchesFacts(candidate, layer.facts)) {
                continue;
            }
            if (consistentWithHistory(candidate, layer.probes)) {
                remaining++;
            }
        }
        layer.candidatesRemaining = remaining;
    }

    private static boolean matchesKnown(List<String> candidate, List<String> known) {
        for (int i = 0; i < known.size() && i < candidate.size(); i++) {
            if (!known.get(i).isEmpty() && !known.get(i).equals(candidate.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Drawn facts are constraints, not decoration — see {@link Facts}'s class note. */
    private static boolean matchesFacts(List<String> candidate, List<String> facts) {
        for (String fact : facts) {
            if (!Facts.matches(fact, candidate)) {
                return false;
            }
        }
        return true;
    }
}
