package io.github.stoicswe.eyeandsickle.solo.breach;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Logic-class fact, stored as {@code CODE|prose} so it is both readable and checkable.
 *
 * <h2>Why facts are not plain sentences</h2>
 *
 * A quiet read costs 1 attention and {@code docs/design/05-hacking-minigame.md} §4 calls it "the
 * patient baseline" — the action the whole loud-versus-patient trade is measured against. If a fact
 * were only prose, {@code LogicRules.recount} could not apply it, so {@code KEYSPACE 4096 -> 37}
 * would not move when the player listened. A player watching the one instrument they have sit still
 * after they paid for information concludes, correctly, that listening does nothing — and then never
 * listens again, which collapses the patient half of the trade and with it the reason the loud tools
 * have a cost at all.
 *
 * <p>So each card carries a machine form as well as its sentence. The prose is what the player
 * reads; the code is what the candidate filter applies; and because both come from the same card,
 * they cannot disagree. {@code BreachSnapshots} publishes {@link #prose} only — the code is
 * scaffolding, not content.
 *
 * <h2>The separator</h2>
 *
 * {@code |} is not in the Logic alphabet ({@code BoardFactory.LOGIC_ALPHABET}) and cannot be, since
 * that set is fixed and enumerated. So no symbol a card names can ever be mistaken for the
 * separator, and no escaping is required.
 */
public final class Facts {

    private Facts() {}

    private static final String SEP = "|";

    /** {@code DISTINCT:n} — the code uses exactly n distinct symbols. */
    public static String distinct(int n) {
        return encode("DISTINCT:" + n, "the code uses exactly " + n + (n == 1 ? " distinct symbol" : " distinct symbols"));
    }

    /** {@code NOREPEAT} / {@code REPEAT} — whether any symbol appears more than once. */
    public static String repeats(boolean anyRepeat) {
        return anyRepeat
                ? encode("REPEAT", "at least one symbol appears more than once")
                : encode("NOREPEAT", "no symbol repeats");
    }

    /** {@code COUNT:s:n} — symbol s appears exactly n times. */
    public static String count(String symbol, int times) {
        return encode(
                "COUNT:" + symbol + ":" + times,
                symbol + " appears exactly " + times + (times == 1 ? " time" : " times"));
    }

    /** {@code ABSENT:s} — symbol s does not appear. */
    public static String absent(String symbol) {
        return encode("ABSENT:" + symbol, symbol + " does not appear at all");
    }

    /** {@code NOTAT:p:s} — the symbol at zero-based position p is not s. Prose is 1-based. */
    public static String notAt(int position, String symbol) {
        return encode("NOTAT:" + position + ":" + symbol, "position " + (position + 1) + " is not " + symbol);
    }

    /** {@code ONLY:a b c} — the exact set of symbols in use. What the Credential Harvester buys. */
    public static String only(List<String> symbols) {
        return encode("ONLY:" + String.join(" ", symbols), "the code uses only: " + String.join(" ", symbols));
    }

    private static String encode(String code, String prose) {
        return code + SEP + prose;
    }

    /** The sentence the player reads. Safe on a card with no code, so old saves still render. */
    public static String prose(String card) {
        int at = card.indexOf(SEP);
        return at < 0 ? card : card.substring(at + 1);
    }

    /** The machine form, or {@code ""} on a card that has none. */
    public static String code(String card) {
        int at = card.indexOf(SEP);
        return at < 0 ? "" : card.substring(0, at);
    }

    /**
     * Whether {@code candidate} satisfies this card.
     *
     * <p>An unrecognised or absent code returns {@code true} — a fact the filter cannot understand
     * must not silently eliminate candidates, because the failure would look exactly like a
     * correctly narrowed keyspace and would make the readout lie in the direction the player most
     * wants to believe.
     */
    public static boolean matches(String card, List<String> candidate) {
        String code = code(card);
        String[] parts = code.split(":");
        Set<String> distinct = new LinkedHashSet<>(candidate);
        return switch (parts[0]) {
            case "DISTINCT" -> distinct.size() == intAt(parts, 1);
            case "NOREPEAT" -> distinct.size() == candidate.size();
            case "REPEAT" -> distinct.size() < candidate.size();
            case "COUNT" -> parts.length > 2 && occurrences(candidate, parts[1]) == intAt(parts, 2);
            case "ABSENT" -> parts.length > 1 && occurrences(candidate, parts[1]) == 0;
            case "NOTAT" -> {
                int position = intAt(parts, 1);
                yield parts.length <= 2
                        || position < 0
                        || position >= candidate.size()
                        || !candidate.get(position).equals(parts[2]);
            }
            case "ONLY" -> parts.length > 1 && distinct.equals(new LinkedHashSet<>(List.of(parts[1].split(" "))));
            default -> true;
        };
    }

    private static int occurrences(List<String> candidate, String symbol) {
        int n = 0;
        for (String s : candidate) {
            if (s.equals(symbol)) {
                n++;
            }
        }
        return n;
    }

    private static int intAt(String[] parts, int index) {
        if (index >= parts.length) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
