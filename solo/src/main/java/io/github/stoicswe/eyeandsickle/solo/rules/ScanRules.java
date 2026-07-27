package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.DefenseState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TaskState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a scan finds, what it gets wrong, and what a Detection Array buys.
 *
 * <h2>Two decisions the design docs made in prose and nothing implemented</h2>
 *
 * <b>Scans can be wrong</b> — {@code docs/design/04-mining.md} §3.2a, decided 2026-07-26, closing
 * DF-5: "A scan result is evidence, not a verdict. Every tier can produce a false positive — a hit
 * on something innocent — and the cheaper tiers do it more often. Signal quality, not just
 * sensitivity, is what a more expensive tier buys."
 *
 * <p><b>The Detection Array buys precision, not sensitivity</b> — {@code
 * docs/design/09-defense-and-hardening.md} §2, decided the same day, closing OQ-6: "it improves the
 * quality of the signal rather than the chance of a hit. Standing compute buys a lower
 * false-positive rate on your own rig, so a Quick Scan on a well-instrumented rig lies to you less
 * often than a Quick Scan on a bare one." The two axes are kept apart here on purpose — {@link
 * #sensitiveTo} does not consult the Array at all, which is what makes the Array non-redundant "by
 * construction rather than by tuning".
 *
 * <h2>Why §3.2a exists at all, and why it must not be softened</h2>
 *
 * {@code docs/education/08-detection-and-defence.md} teaches {@code false-positive(7)}, {@code
 * base-rate-fallacy(7)} and {@code alert-fatigue(7)} — three of the curriculum's strongest pages, all
 * resting on the fact that real detectors mostly fire on innocent things. Before §3.2a the scan
 * tiers implied that and never delivered it, so <b>the game contradicted its own manual</b>, which
 * {@code CLAUDE.md} treats as worse than teaching nothing.
 *
 * <p>That is also why a false positive here always names <b>a real, innocent thing on the rig</b>
 * rather than an invented ghost. §3.2a: "a scan hit is now a lead to corroborate against the compute
 * ledger — exactly the cross-referencing §3.1 calls the game's second-strongest tutorial vector —
 * instead of an answer that makes investigation pointless." A player who checks the named process
 * against their own allocation list can rule it out; a player who acts on the headline cannot.
 *
 * <h2>The finding is rolled at scan start, and frozen</h2>
 *
 * {@link TaskState#outcome} has existed since scans became real work and was written by nobody. It
 * is written here, at the moment the scan is commissioned, so that a scan finishing while the game
 * is closed reports what it would have reported in session. Rolling at completion instead would mean
 * a six-minute scan quietly re-rolled its answer depending on whether the player watched — and under
 * the persisted RNG that is also a reroll a player could force by quitting.
 */
public final class ScanRules {

    private ScanRules() {}

    /** The armed Detection Array's tier, or 0 — {@code docs/design/09-defense-and-hardening.md} §1. */
    public static int detectionArrayTier(SoloSave save) {
        int best = 0;
        for (DefenseState defense : save.defenses) {
            if (defense.kind != null && defense.kind.startsWith("detection-array")) {
                best = Math.max(best, Math.max(1, Math.min(3, defense.tier)));
            }
        }
        return best;
    }

    /**
     * The chance this scan reports something innocent as a hit.
     *
     * <p>The Array multiplies rather than subtracts. A subtraction would let a T3 Array drive the
     * Thorough Scan's 4% to zero and turn one defence into a perfect detector, which removes the
     * doubt the detection system exists to create — the same argument {@code
     * docs/design/07-recon-tools.md} §2 makes when it requires the Honeypot Detector to have a
     * false-negative rate. Multiplying preserves the tier ordering and can never reach certainty.
     */
    public static double falsePositiveRate(String tier, int arrayTier) {
        double base = switch (normalise(tier)) {
            case "QUICK" -> Balance.SCAN_FALSE_POSITIVE_QUICK;
            case "THOROUGH" -> Balance.SCAN_FALSE_POSITIVE_THOROUGH;
            default -> Balance.SCAN_FALSE_POSITIVE_FULL;
        };
        double precision = switch (Math.max(0, Math.min(3, arrayTier))) {
            case 1 -> Balance.DETECTION_ARRAY_PRECISION_T1;
            case 2 -> Balance.DETECTION_ARRAY_PRECISION_T2;
            case 3 -> Balance.DETECTION_ARRAY_PRECISION_T3;
            default -> 1.0d;
        };
        return base * precision;
    }

    /**
     * Whether this tier can see this miner at all — the <em>sensitivity</em> axis.
     *
     * <p>Straight from {@code docs/design/04-mining.md} §3.2's Finds column: Quick sees "unhidden
     * T2-T3 miners only", Full sees "all unhidden miners; some rootkit-wrapped", Thorough sees "all
     * miners, including rootkit-wrapped".
     *
     * <p>⚠ The Detection Array is deliberately not a parameter. Scans buy sensitivity, the Array buys
     * precision, and OQ-6's resolution is exactly that they are different axes — a version of this
     * method that took the Array would re-merge them and re-open the question.
     *
     * <p>Quick's "T2-T3" is read as tier 2 or above rather than as tier 2 and 3 exactly: a tier-4
     * miner that a Quick Scan could not see would make the cheap scan better at finding weak threats
     * than strong ones, which no reading of the table supports.
     */
    public static boolean sensitiveTo(String tier, MinerState miner, Rng rng) {
        return switch (normalise(tier)) {
            case "QUICK" -> !miner.rootkitWrapped && miner.tier >= 2;
            case "THOROUGH" -> true;
            default -> !miner.rootkitWrapped || rng.nextDouble() < Balance.SCAN_ROOTKIT_SENSITIVITY_FULL;
        };
    }

    /**
     * Rolls this scan's finding now and returns the line to freeze into {@link TaskState#outcome}.
     *
     * <p>⚠ The caller must {@link Rng#commit} after this returns, or the next load rerolls the
     * scan — and a rerollable scan result is an advisory one, which is precisely what §3.2a decided
     * against.
     *
     * <p>Both halves are always drawn, in a fixed order, whether or not they end up in the line. A
     * generator whose consumption depends on what it produced makes the stream shape depend on the
     * code path, and then a replay from a stored seed stops being a replay.
     */
    public static Finding roll(SoloSave save, String tier, Rng rng) {
        List<String> hits = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (MinerState miner : save.rig.foreignMiners) {
            if (sensitiveTo(tier, miner, rng)) {
                hits.add(miner.label.isEmpty() ? "an unregistered process" : miner.label);
                // Carried by id, not by label. Two parasites can share a name — the counter-hack
                // plants every one of them as "unregistered process" — and marking by label would
                // reveal both when the scan only saw one.
                found.add(miner.minerId);
            }
        }
        boolean falsePositive = rng.nextDouble() < falsePositiveRate(tier, detectionArrayTier(save));
        String innocent = innocentSuspect(save, rng);

        StringBuilder line = new StringBuilder();
        if (hits.isEmpty()) {
            line.append("No foreign miner matched this tier's signature.");
        } else {
            line.append(hits.size())
                    .append(hits.size() == 1 ? " foreign miner found: " : " foreign miners found: ")
                    .append(String.join(", ", hits))
                    .append('.');
        }
        if (falsePositive && !innocent.isEmpty()) {
            // Named, not hinted. §3.2a wants a lead the player can corroborate against the compute
            // ledger — which requires the lead to point at something the ledger actually lists.
            line.append(" Also flagged: ")
                    .append(innocent)
                    .append(". Corroborate before you act on it.");
        }
        if (hits.isEmpty() && !falsePositive) {
            line.append(" Manual audit still sees things a scan does not.");
        }
        return new Finding(line.toString(), List.copyOf(found));
    }

    /**
     * A scan's decided result: the sentence the player reads, and which parasites it actually named.
     *
     * <h2>Why the ids travel separately from the prose</h2>
     *
     * The sentence is what the log prints; the ids are what {@code MinerState.discovered} is set from,
     * and setting that flag is what lets the rig monitor attribute the stolen cycles at last. Parsing
     * the ids back out of the sentence would work today and would break the first time somebody
     * reworded it — and the failure would be silent, leaving a scan that reports a find and reveals
     * nothing.
     *
     * <p>⚠ <b>Both halves are frozen onto the task at commission and applied at settlement.</b> A scan
     * that finished while the game was closed therefore reveals exactly what it would have revealed in
     * session. Rolling at completion would make the answer depend on whether the player was watching,
     * and under the persisted RNG it would also be a reroll they could force by quitting.
     *
     * @param line the player-facing sentence, already including any false positive
     * @param foundMinerIds the parasites this scan genuinely saw; empty is a clean result and not a
     *     guarantee
     */
    public record Finding(String line, List<String> foundMinerIds) {

        public Finding {
            line = line == null ? "" : line;
            foundMinerIds = foundMinerIds == null ? List.of() : List.copyOf(foundMinerIds);
        }
    }

    /**
     * A real, innocent thing on this rig for a false positive to name.
     *
     * <p>Drawn from the compute ledger and the armed defences, because those are exactly the lists a
     * player can check it against. If the rig is genuinely bare there is nothing honest to name and
     * the scan simply does not raise one — an invented process name would be uncorroborable, which
     * would make the lesson "scan hits are noise" rather than "scan hits are leads".
     */
    private static String innocentSuspect(SoloSave save, Rng rng) {
        List<String> candidates = new ArrayList<>();
        for (AllocationState allocation : save.rig.allocations) {
            // A foreign miner's own allocation is not innocent, and naming it would be a true
            // positive wearing a false one's clothes.
            if (!"DEPLOYED_MINER".equals(allocation.consumer) && !allocation.label.isBlank()) {
                candidates.add(allocation.label + " (" + allocation.cycles + "C, " + describe(allocation) + ")");
            }
        }
        if (save.rig.selfMiningCycles > 0) {
            candidates.add("self-mining (" + save.rig.selfMiningCycles + "C, yours)");
        }
        for (DefenseState defense : save.defenses) {
            candidates.add(defense.kind + " (" + defense.reservedCycles + "C, armed by you)");
        }
        if (candidates.isEmpty()) {
            return "";
        }
        return rng.pick(candidates);
    }

    private static String describe(AllocationState allocation) {
        return "RECOVERING".equals(allocation.state) ? "recovering" : "held";
    }

    /**
     * What {@code settleTasks} prints when a scan completes.
     *
     * <p>Falls back for saves written before findings were captured. The fallback says plainly that
     * it has nothing rather than inventing a clean bill of health: a confident "nothing found" the
     * engine did not actually establish is a lie the player would reasonably act on, which is the
     * failure the previous stub's own comment was written to avoid.
     */
    public static String finding(TaskState task) {
        if (task != null && task.outcome != null && !task.outcome.isBlank()) {
            return task.outcome;
        }
        return "This scan predates finding capture and has no result to report; run another.";
    }

    private static String normalise(String tier) {
        return tier == null ? "FULL" : tier.trim().toUpperCase(Locale.ROOT);
    }
}
