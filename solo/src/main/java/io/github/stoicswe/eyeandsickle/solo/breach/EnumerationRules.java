package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.PortSlotState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Enumeration class: map a node's open ports and services before you can act.
 *
 * <h2>The failure mode this class punishes is acting before you know the shape</h2>
 *
 * {@code declare} costs 2 attention — the price of an ordinary probe. Being wrong costs a strike on
 * top, and the response says only <em>how many</em> slots were wrong and never which. So guessing
 * the set is cheap to attempt and expensive to get wrong, and it teaches nothing when it fails,
 * which is the shape that makes reading the board the dominant strategy rather than a stylistic
 * preference.
 *
 * <h2>The three reads, priced apart</h2>
 *
 * A sweep is 1 attention and returns a <em>count</em> for a band. A probe is 2 and returns one slot
 * exactly. A banner grab is 6, returns three slots, and can set off a canary. That is {@code
 * docs/design/05-hacking-minigame.md} §4's cost table with the class's own content poured into it:
 * the cheap action buys shape, the middling one buys certainty, and the loud one buys speed at the
 * risk of being noticed. A player who sweeps first and probes into the interesting bands spends far
 * less than one who probes left to right.
 *
 * <h2>The human read (D-8)</h2>
 *
 * The banner constrains where open ports can be. It is stated in {@code
 * docs/design/16-breach-implementation.md} §3 for the designer and nowhere the player can look it
 * up, so a player learns it by playing and a fixed heuristic never learns it at all. That is §3.2's
 * "cannot use the 'intuition' shortcuts a human gets from reading flavor data" made mechanical —
 * the whole content of Invariant I10 for this class.
 */
public final class EnumerationRules {

    private EnumerationRules() {}

    /**
     * Resolves one Enumeration action.
     *
     * <p>{@code bypass} is not here: it behaves identically in all three classes and is handled once
     * in {@link BreachRules}.
     */
    static Move act(LayerState layer, String actionId, String argument, Rng rng) {
        return switch (actionId) {
            case "sweep" -> sweep(layer, argument);
            case "probe" -> probe(layer, argument);
            case "banner" -> banner(layer, argument);
            case "sidechannel" -> sideChannel(layer, rng);
            case "mark" -> mark(layer, argument);
            case "declare" -> declare(layer);
            default -> Move.of("nothing happened");
        };
    }

    /**
     * Counts the open ports in a band without naming them.
     *
     * <p>The count-not-which gap is the entire reason a sweep is worth 1 against a probe's 2. "Two
     * open in slots 4 to 7" narrows four slots at half the price of resolving one of them, and it is
     * only useful to a player willing to reason from it — which is precisely the read the class is
     * about.
     */
    private static Move sweep(LayerState layer, String argument) {
        int band = parseInt(argument, -1);
        int bands = bandCount(layer);
        if (band < 0 || band >= bands) {
            return Move.of("no such band: " + argument);
        }
        int from = band * layer.bandSize;
        int to = Math.min(layer.slots, from + layer.bandSize) - 1;

        int open = 0;
        for (int i = from; i <= to; i++) {
            if ("OPEN".equals(layer.ports.get(i).truth)) {
                open++;
            }
        }
        layer.readingRanges.add(new int[] {from, to, open});
        return Move.of(open + " open in " + pad(from) + "-" + pad(to));
    }

    /** Resolves one slot exactly. The default move, at the default price. */
    private static Move probe(LayerState layer, String argument) {
        int index = parseInt(argument, -1);
        if (index < 0 || index >= layer.slots) {
            return Move.of("no such slot: " + argument);
        }
        PortSlotState slot = layer.ports.get(index);
        reveal(slot);
        return Move.of(describe(slot));
    }

    /**
     * Grabs a banner: reveals a slot and both its neighbours, loudly.
     *
     * <p>Three slots for 6 attention is worse than three probes at 2 each — the loud tool is not
     * <em>efficient</em>, it is fast, and {@code docs/design/05-hacking-minigame.md} §4 prices it as
     * "power bought with exposure". What you actually buy is contiguity: three adjacent slots at
     * once is what resolves a banner rule, and no number of separate probes gives you the same shape
     * in one move.
     *
     * <p>⚠ A filtered slot is the canary. {@code docs/design/09-defense-and-hardening.md} §2 makes a
     * Canary Token something "nothing legitimate ever opens", so touching one loudly is unambiguous
     * evidence of an intruder — which is why it strikes here and a quiet probe of the same slot does
     * not.
     */
    private static Move banner(LayerState layer, String argument) {
        int index = parseInt(argument, -1);
        if (index < 0 || index >= layer.slots) {
            return Move.of("no such slot: " + argument);
        }
        List<String> revealed = new ArrayList<>();
        for (int i = index - 1; i <= index + 1; i++) {
            if (i < 0 || i >= layer.slots) {
                continue;
            }
            PortSlotState slot = layer.ports.get(i);
            reveal(slot);
            revealed.add(describe(slot));
        }
        String line = String.join("; ", revealed);
        if ("FILTERED".equals(layer.ports.get(index).truth)) {
            return Move.canary(line + " - CANARY", "a canary token tagged your handle on " + pad(index), 0);
        }
        return Move.of(line);
    }

    /**
     * The Side-Channel Reader: reads without entering.
     *
     * <p>Zero attention — {@code docs/design/06-intrusion-tools.md} §2 calls that "its whole
     * identity", and §4 of {@code 05} makes it the only action in the game that costs nothing from
     * the bar. Its price is paid elsewhere entirely: 14 permanent cycles while equipped, the highest
     * compute cost of any intrusion tool, behind a late schematic gate.
     *
     * <p>What it buys is the total open count and one filtered slot. The total is the strongest
     * single fact in this class — it tells the player exactly when to stop looking, which is where
     * most attention gets wasted. Once per layer, because a free action with no limit is not a tool,
     * it is a solution.
     */
    private static Move sideChannel(LayerState layer, Rng rng) {
        if (layer.knownOpenTotal >= 0) {
            return Move.of("the side channel has already given up everything it holds on this layer");
        }
        int open = 0;
        List<PortSlotState> filtered = new ArrayList<>();
        for (PortSlotState slot : layer.ports) {
            if ("OPEN".equals(slot.truth)) {
                open++;
            } else if ("FILTERED".equals(slot.truth) && !slot.revealed) {
                filtered.add(slot);
            }
        }
        layer.knownOpenTotal = open;
        String extra = "";
        if (!filtered.isEmpty()) {
            PortSlotState pick = rng.pick(filtered);
            reveal(pick);
            extra = "; " + describe(pick);
        }
        return Move.of(open + " ports open in total" + extra);
    }

    /** Toggles a slot in the set being composed. Bookkeeping: it is the player's own notepad. */
    private static Move mark(LayerState layer, String argument) {
        int index = parseInt(argument, -1);
        if (index < 0 || index >= layer.slots) {
            return Move.bookkeeping("no such slot: " + argument);
        }
        if (layer.declared.remove(Integer.valueOf(index))) {
            return Move.bookkeeping("unmarked " + pad(index));
        }
        layer.declared.add(index);
        layer.declared.sort(Integer::compareTo);
        return Move.bookkeeping("marked " + pad(index));
    }

    /**
     * Submits the composed set.
     *
     * <p>⚠ The failure response gives a count and never a location. Naming the wrong slots would turn
     * {@code declare} into the cheapest probe in the class — two attention for a reading on every
     * slot at once — and the player would never sweep again. The count is enough to tell you that you
     * were close and not enough to tell you where, which is what keeps the reading in the reading.
     */
    private static Move declare(LayerState layer) {
        List<Integer> truth = new ArrayList<>();
        for (PortSlotState slot : layer.ports) {
            if ("OPEN".equals(slot.truth)) {
                truth.add(slot.index);
            }
        }
        int wrong = 0;
        for (int index : layer.declared) {
            if (!truth.contains(index)) {
                wrong++;
            }
        }
        for (int index : truth) {
            if (!layer.declared.contains(index)) {
                wrong++;
            }
        }
        if (wrong == 0) {
            return Move.cleared("map accepted: " + truth.size() + " open ports, exactly");
        }
        return Move.strike(wrong + (wrong == 1 ? " slot is wrong" : " slots are wrong"));
    }

    // ------------------------------------------------------------------ helpers

    static int bandCount(LayerState layer) {
        int size = Math.max(1, layer.bandSize);
        return (layer.slots + size - 1) / size;
    }

    private static void reveal(PortSlotState slot) {
        slot.revealed = true;
        if (!"OPEN".equals(slot.truth)) {
            // A service name on a slot that is not open would be a fact about a thing that is not
            // listening. Cleared rather than left set, so the snapshot cannot publish one.
            slot.service = "";
        }
    }

    private static String describe(PortSlotState slot) {
        String state = slot.truth;
        if ("OPEN".equals(state) && !slot.service.isEmpty()) {
            return pad(slot.index) + " OPEN - " + slot.service;
        }
        return pad(slot.index) + " " + state;
    }

    /** Two-digit slot labels, so a column of them lines up in a character grid. */
    static String pad(int index) {
        return String.format(Locale.ROOT, "%02d", index);
    }

    static int parseInt(String argument, int fallback) {
        try {
            return Integer.parseInt(argument == null ? "" : argument.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
