package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.widgets.ProcessTableView.Column;
import io.github.stoicswe.eyeandsickle.protocol.game.RigProcess;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The rig monitor's six views, and the columns each one shows.
 *
 * <h2>Why five tabs and not one wide table</h2>
 *
 * Activity Monitor's split is not arbitrary and copying it is not imitation: <b>each tab is a
 * question</b>. "What is eating the processor" and "what is holding the memory" have different answers
 * and different columns, and a single table wide enough for both is a table nobody scrolls sideways to
 * read. Splitting it also makes the tabs a diagnostic instrument in their own right — a parasite that
 * hides well on CPU is conspicuous on NETWORK, and the player who thinks to look is rewarded for it.
 *
 * <h2>The order is fixed and it is the order of escalation</h2>
 *
 * Overview first, because it is the panel that was already there and the one a player opens by
 * reflex. Then CPU, MEMORY, DISK, NETWORK — cheapest signal to most specific. A player who suspects
 * something walks rightwards. {@link #ABOUT} is appended after that ladder rather than inserted into
 * it, for the reason given on the constant.
 *
 * <h2>⚠ Every column is a fact the process genuinely reports</h2>
 *
 * There is no column that means "suspicious", no score, and no derived verdict. The tells this table
 * carries are <em>relationships</em> — a CPU figure against its own accumulated CPU time, a user
 * against every other user in the list, a name against the daemon one row below it — and a column that
 * did the comparing would remove the only skill involved. See {@code solo/proc/Disguise}.
 */
public enum RigTab {

    /** The panel that was already here: the grid, the cage, the activity list, the notes. */
    OVERVIEW("OVERVIEW"),

    /**
     * The processor.
     *
     * <p>⚠ {@code CPU TIME} sits immediately beside {@code % CPU}, and that adjacency is the mechanic.
     * A row claiming a fifth of the machine with three seconds of accumulated time has not been busy
     * as long as it says — which is the {@code STOPPED_CLOCK} disguise, findable in one glance across
     * two columns and in none at all if they are on different tabs.
     */
    CPU("CPU"),

    /** Resident memory, threads, ports. Where a resource hog is loudest. */
    MEMORY("MEMORY"),

    /** Bytes read and written. A daemon reads a lot; a tool writes a little. */
    DISK("DISK"),

    /**
     * Packets and bytes, in and out.
     *
     * <p>⚠ The most decisive tab in the table, and the reason is a rule rather than a tuning choice:
     * only work that <em>reaches other machines</em> has traffic ({@code NoiseRules}), so a local
     * audit or a self-mining process with packets on it is not what it claims to be. A parasite is
     * talking to whoever planted it and cannot not be.
     */
    NETWORK("NETWORK"),

    /**
     * The machine the game is running on, rather than the machine in the game.
     *
     * <p>Last on purpose, and outside the escalation the four table tabs form. Everything to its
     * left answers a question about the <em>fictional</em> rig — what is eating its cycles, what is
     * talking to the network — and reads state the engine owns. This one steps out of the fiction
     * and reports the player's real hardware, so it sits after the ladder rather than in it. A
     * player walking rightwards through the diagnostic tabs reaches the end of the diagnosis before
     * they reach the colophon.
     *
     * <p>It draws no table and takes no {@code GameSession}: nothing on it is game state, so there
     * is nothing here for {@code I14} to have an opinion about.
     */
    ABOUT("ABOUT");

    private final String label;

    RigTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isOverview() {
        return this == OVERVIEW;
    }

    /**
     * Whether this tab draws the process table.
     *
     * <p>⚠ Exists because {@code !isOverview()} used to mean "table", and adding a third kind of tab
     * made that false in a way nothing would have reported: ABOUT would have rendered the process
     * table underneath the mascot. Ask what a tab <em>is</em>, not what it is not.
     */
    public boolean isTable() {
        return this != OVERVIEW && this != ABOUT;
    }

    /** Brackets, not colour — §4.4, and it survives greyscale and a screen reader. */
    public String control(RigTab active) {
        return this == active ? "[ " + label + " ]" : "  " + label + "  ";
    }

    // ================================================================== columns

    /**
     * The columns this tab shows.
     *
     * <p>Widths are character cells and are chosen so the widest realistic value fits: a process name
     * has to hold {@code provenanced} and a tool label has to hold {@code scan --thorough}, or the
     * clip in {@code ProcessTableView.pad} eats the end of exactly the string a player is comparing.
     */
    public List<Column> columns() {
        return switch (this) {
            // Overview and About draw no table, but returning the CPU set rather than an empty list
            // means the widget is never asked to render zero columns — and a tab switch back from
            // CPU finds the sort it left, because the column list is the same object.
            case OVERVIEW, ABOUT, CPU -> List.of(
                    processColumn(),
                    number("% CPU", 9, p -> String.format(Locale.ROOT, "%.1f", p.cpuPercent()),
                            Comparator.comparingDouble(RigProcess::cpuPercent)),
                    number("CPU TIME", 12, RigProcess::cpuTimeText,
                            Comparator.comparing(RigProcess::cpuTime)),
                    number("THREADS", 9, p -> String.valueOf(p.threads()),
                            Comparator.comparingInt(RigProcess::threads)),
                    number("IDLE WK", 9, p -> String.valueOf(p.idleWakeups()),
                            Comparator.comparingInt(RigProcess::idleWakeups)),
                    number("CYCLES", 8, p -> p.cycles() > 0 ? String.valueOf(p.cycles()) : "--",
                            Comparator.comparingLong(RigProcess::cycles)),
                    pid(),
                    user());

            case MEMORY -> List.of(
                    processColumn(),
                    number("MEMORY", 12, RigProcess::memoryText,
                            Comparator.comparingLong(RigProcess::memoryBytes)),
                    number("THREADS", 9, p -> String.valueOf(p.threads()),
                            Comparator.comparingInt(RigProcess::threads)),
                    number("PORTS", 8, p -> String.valueOf(p.ports()),
                            Comparator.comparingInt(RigProcess::ports)),
                    number("CYCLES", 8, p -> p.cycles() > 0 ? String.valueOf(p.cycles()) : "--",
                            Comparator.comparingLong(RigProcess::cycles)),
                    pid(),
                    user());

            case DISK -> List.of(
                    processColumn(),
                    number("WRITTEN", 12, p -> RigProcess.bytesText(p.bytesWritten()),
                            Comparator.comparingLong(RigProcess::bytesWritten)),
                    number("READ", 12, p -> RigProcess.bytesText(p.bytesRead()),
                            Comparator.comparingLong(RigProcess::bytesRead)),
                    pid(),
                    user());

            case NETWORK -> List.of(
                    processColumn(),
                    number("SENT", 11, p -> RigProcess.bytesText(p.sentBytes()),
                            Comparator.comparingLong(RigProcess::sentBytes)),
                    number("RCVD", 11, p -> RigProcess.bytesText(p.rcvdBytes()),
                            Comparator.comparingLong(RigProcess::rcvdBytes)),
                    number("PKT OUT", 10, p -> String.valueOf(p.sentPackets()),
                            Comparator.comparingLong(RigProcess::sentPackets)),
                    number("PKT IN", 10, p -> String.valueOf(p.rcvdPackets()),
                            Comparator.comparingLong(RigProcess::rcvdPackets)),
                    pid(),
                    user());
        };
    }

    /**
     * The process name, on every tab and always first.
     *
     * <p>Sorted case-insensitively, which is what puts a typosquat beside the daemon it is imitating
     * rather than in a separate block of capitals — and that adjacency is the entire tell.
     */
    private static Column processColumn() {
        return new Column("PROCESS", 22, false, RigProcess::name,
                Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT)));
    }

    /**
     * The pid, on every tab.
     *
     * <p>⚠ Carries a tell of its own and it comes free: every daemon in the table started at boot and
     * has a pid under 200, so anything claiming to be a system process with a five-figure pid started
     * long after the machine did.
     */
    private static Column pid() {
        return new Column("PID", 8, true, p -> String.valueOf(p.pid()),
                Comparator.comparingInt(RigProcess::pid));
    }

    /**
     * The account, on every tab and always last.
     *
     * <p>⚠ The single most useful column in the table and the reason it is never dropped from a tab.
     * Real service accounts appear on more than one row; a mimic's appears exactly once, and reading
     * down this column finds it in about a second.
     */
    private static Column user() {
        return new Column("USER", 16, false, RigProcess::user,
                Comparator.comparing(p -> p.user().toLowerCase(Locale.ROOT)));
    }

    private static Column number(
            String title,
            int width,
            java.util.function.Function<RigProcess, String> text,
            Comparator<RigProcess> order) {
        return new Column(title, width, true, text, order);
    }
}
