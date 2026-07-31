package io.github.stoicswe.eyeandsickle.solo.proc;

import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.util.ArrayList;
import java.util.List;

/**
 * How a parasite hides in the process table.
 *
 * <h2>Every disguise must be findable by reading, and none by clicking</h2>
 *
 * {@code docs/design/04-mining.md} §3.1 gives manual audit its whole job: the discrepancy is always
 * present in the data. That is a promise in two directions and both halves are load-bearing.
 *
 * <ul>
 *   <li><b>Always present.</b> Every disguise below leaves a tell that is <em>in the table</em>, on
 *       the row, visible without any tool. A parasite the player could not have found by looking is
 *       not hiding, it is cheating, and it would teach them that reading the table is pointless.
 *   <li><b>A discrepancy, not a marker.</b> The tell is never a field that means "this one". It is
 *       always a <em>relationship</em> being wrong: a name that does not match any real daemon, a
 *       user no other row uses, a CPU figure that does not match its own accumulated CPU time, two
 *       rows claiming to be one tool. Reading a relationship is the skill; spotting a marker is not.
 * </ul>
 *
 * <p>⚠ None of these is subtle enough to need a guide, and that is deliberate. The fun is in
 * <em>noticing</em>, which is a two-second act once you know what to look at; making it a
 * ten-minute act would turn the audit into a chore and push players back onto buying scans, which is
 * the opposite of what §3.1 wants. Hard enough that a glance misses it, easy enough that a look
 * finds it.
 *
 * <h2>They are all one of two shapes</h2>
 *
 * A disguise either <b>borrows a name that should be unique</b> or <b>claims a resource story that
 * does not add up</b>. The first is caught by comparing rows to each other, the second by comparing a
 * row to itself. Teaching both is why there are five rather than one.
 */
public enum Disguise {

    /**
     * <b>Tool twin.</b> Wears the exact name of a tool the player runs, so the table shows two rows
     * called {@code scan --full}.
     *
     * <p>The tell is the duplicate itself, and it is the loudest one here — but only while the real
     * tool is also running. A patient player who kills their own tool and watches one row survive has
     * done a real diagnostic, which is the whole reason this disguise is worth having.
     */
    TOOL_TWIN,

    /**
     * <b>System mimic.</b> A plausible-looking daemon name, running as an account <em>nothing else on
     * the machine uses</em>.
     *
     * <p>Every real service process in the table runs as {@code root} or as an underscore-prefixed
     * service account that appears on more than one row. This one runs as something that appears
     * exactly once. Reading down the USER column finds it in about a second, which is why the name
     * itself is chosen to look completely ordinary.
     */
    SYSTEM_MIMIC,

    /**
     * <b>Typosquat.</b> A real daemon's name with one character wrong — {@code syspolicvd} for
     * {@code syspolicyd}, {@code kerne1_task} for {@code kernel_task}.
     *
     * <p>The tell is that the real one is <em>also in the table</em>, so the two sit near each other
     * once the list is sorted by name. This is the hardest of the five and the most satisfying; it is
     * also the one that teaches something true, because typosquatting a system binary is a real
     * technique and {@code docs/education/08-detection-and-defence.md} has the page for it.
     */
    TYPOSQUAT,

    /**
     * <b>Resource hog.</b> No name games at all — it simply sits near the top of the CPU or memory
     * column and stays there.
     *
     * <p>Deliberately the easiest, because it is the one a new player finds first and it is what
     * teaches them that the table is worth sorting. The tell is that nothing the player started
     * accounts for it.
     */
    RESOURCE_HOG,

    /**
     * <b>Stopped clock.</b> Claims heavy CPU while its accumulated CPU time barely moves.
     *
     * <p>A real process burning 20% of a machine banks processor time at a rate anyone can see by
     * looking twice. This one does not, because it has only ever <em>claimed</em> to be busy. The
     * tell needs two readings a few seconds apart, which makes it the one disguise that rewards
     * watching rather than scanning — and the reason the table's numbers had to be stable enough to
     * compare in the first place.
     */
    STOPPED_CLOCK;

    /** What a save wrote before disguises existed, and the plainest of the five. */
    public static Disguise of(String stored) {
        if (stored == null || stored.isBlank()) {
            return RESOURCE_HOG;
        }
        try {
            return valueOf(stored.trim());
        } catch (IllegalArgumentException unknown) {
            // A hand-edited save naming a disguise this build does not have. The visible one is the
            // honest fallback: a parasite that failed to put its costume on is still a parasite.
            return RESOURCE_HOG;
        }
    }

    // ================================================================== assignment

    /**
     * Daemon names a mimic may borrow, and the ones a typosquat has to sit beside.
     *
     * <p>Every one of these also appears in {@link SystemProcesses}, which is what makes both of
     * those disguises findable: the mimic's name is plausible <em>because</em> the table is full of
     * names like it, and the typosquat's victim is one row away.
     */
    private static final List<String> MIMIC_NAMES =
            List.of("thermald", "cyclesd", "provenanced", "ledgerd", "vaultd", "attestd");

    /** Accounts that exist nowhere else in the table. The tell on {@link #SYSTEM_MIMIC}. */
    private static final List<String> ODD_USERS = List.of("_relay", "_sysupd", "_provisioner", "nobody4", "_ecmon");

    /**
     * Dresses a freshly planted parasite.
     *
     * <p>⚠ Draws exactly once and unconditionally, so the RNG stream advances by the same amount
     * whatever the outcome. {@code Rng}'s contract is that a generator whose consumption depends on
     * what it produced makes a replay from a stored seed stop being a replay — the same discipline
     * {@code TopologyGenerator} keeps and for the same reason.
     *
     * @param save read for the tools the player is running, so a twin has something to copy
     * @param miner mutated in place; the caller commits the RNG
     */
    public static void dress(SoloSave save, MinerState miner, Rng rng) {
        int roll = rng.nextInt(values().length);
        List<String> tools = runningToolNames(save);

        Disguise chosen = values()[roll];
        // A twin with nothing to copy is not a disguise, it is a process called "". Falling back
        // rather than re-rolling keeps the draw count fixed — see the contract above.
        if (chosen == TOOL_TWIN && tools.isEmpty()) {
            chosen = RESOURCE_HOG;
        }

        miner.disguise = chosen.name();
        miner.disguiseUser = switch (chosen) {
            case SYSTEM_MIMIC -> ODD_USERS.get(Math.floorMod(roll * 31 + miner.minerId.hashCode(), ODD_USERS.size()));
            // A typosquat impersonating a daemon has to claim the daemon's account too, or the USER
            // column alone would give it away and the name would never have to be read.
            case TYPOSQUAT -> "root";
            default -> "";
        };
        miner.disguiseName = switch (chosen) {
            case TOOL_TWIN -> tools.get(Math.floorMod(miner.minerId.hashCode(), tools.size()));
            case SYSTEM_MIMIC -> MIMIC_NAMES.get(Math.floorMod(miner.minerId.hashCode(), MIMIC_NAMES.size()));
            case TYPOSQUAT -> typo(SystemProcesses.squattableName(miner.minerId.hashCode()));
            default -> "";
        };
    }

    /**
     * One character off, and never a character that changes the word's shape.
     *
     * <p>Swapping a letter for a visually near neighbour — {@code y}→{@code v}, {@code l}→{@code 1},
     * {@code o}→{@code 0}, {@code m}→{@code rn} — is what makes this readable rather than obvious.
     * A random letter would produce {@code sysposicyd}, which anyone spots; these produce
     * {@code syspolicvd}, which you have to look at.
     */
    private static String typo(String name) {
        for (int i = name.length() - 1; i >= 0; i--) {
            char swap =
                    switch (name.charAt(i)) {
                        case 'y' -> 'v';
                        case 'l' -> '1';
                        case 'o' -> '0';
                        case 'i' -> 'j';
                        case 'e' -> 'c';
                        default -> 0;
                    };
            if (swap != 0) {
                return name.substring(0, i) + swap + name.substring(i + 1);
            }
        }
        // Nothing swappable — vanishingly unlikely against the real name list, and a doubled last
        // character is still a one-character difference rather than a giveaway.
        return name + name.charAt(name.length() - 1);
    }

    /** Names of tools the player is running right now, for a twin to copy. */
    private static List<String> runningToolNames(SoloSave save) {
        List<String> out = new ArrayList<>();
        if (save != null && save.tasks != null) {
            for (var task : save.tasks) {
                if (task.label != null && !task.label.isBlank()) {
                    out.add(task.label);
                }
            }
        }
        return out;
    }
}
