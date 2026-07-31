package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Sparkline;
import io.github.stoicswe.eyeandsickle.protocol.game.RigProcess;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The history strip above the process table: what this rig has been doing for the last two minutes.
 *
 * <h2>Why a table needs a chart above it</h2>
 *
 * A process table answers "what is running now". It cannot answer "was that always there", and that
 * second question is the one {@code docs/design/04-mining.md} §3.1's manual audit actually turns on —
 * a parasite that has been drawing cycles for an hour looks exactly like a tool the player started a
 * moment ago, in a snapshot. A history makes the shape of the last two minutes visible, so a step
 * change reads as a step change.
 *
 * <h2>⚠ Sampled on the table's own beat, never on a clock of its own</h2>
 *
 * The engine advances every process figure on a five-second step ({@code Vitals.INTERVAL_SECONDS}),
 * the table repaints on that step, and these charts are pushed from the same callback. Two
 * five-second timers started a moment apart would put one spike at two different places, and a chart
 * that disagrees with the table under it is worse than no chart — the player is being asked to
 * compare them.
 *
 * <h2>Two series where a metric has two directions</h2>
 *
 * Disk and network are read-and-write and in-and-out, and collapsing either into one number hides the
 * thing worth seeing: a process that reads a lot and writes nothing is doing something quite
 * different from one that does the reverse. CPU and memory have one direction each and get one chart.
 *
 * <h2>⚠ Rates, not totals</h2>
 *
 * The engine's byte and packet counters are <b>monotonic</b> — they only ever climb, which is what
 * makes them a real accounting of what a process has done. Charting them directly would draw a line
 * that rises forever and flattens to uselessness within a minute. So this differences consecutive
 * samples and charts the <em>rate</em>, which is what an activity monitor shows and what a player is
 * actually looking for.
 */
public final class RigHistory extends VBox {

    /** The per-tab charts. A tab with no history of its own shows nothing. */
    private final Map<RigTab, HBox> rows = new EnumMap<>(RigTab.class);

    private final Sparkline cpu = new Sparkline("CPU");
    private final Sparkline memory = new Sparkline("MEMORY");
    private final Sparkline diskRead = new Sparkline("READ");
    private final Sparkline diskWrite = new Sparkline("WRITTEN");
    private final Sparkline netIn = new Sparkline("RCVD");
    private final Sparkline netOut = new Sparkline("SENT");

    /**
     * Ceilings for the charts that have no natural one.
     *
     * <p>⚠ A fixed ceiling rather than an auto-scale, deliberately. An auto-scaling chart redraws its
     * own axis as the data moves, so a flat line and a spike look identical — the shape a player is
     * reading is destroyed by the very thing they are reading it for. These are set high enough that
     * an ordinary rig sits in the lower half and a genuine spike has somewhere to go.
     */
    private static final double MEMORY_CEILING_BYTES = 8L * 1024 * 1024 * 1024;

    private static final double DISK_CEILING_BYTES_PER_SECOND = 40.0d * 1024 * 1024;

    private static final double NET_CEILING_BYTES_PER_SECOND = 8.0d * 1024 * 1024;

    /** The previous sample's totals, for differencing the monotonic counters. */
    private long lastRead = -1;

    private long lastWritten = -1;
    private long lastRcvd = -1;
    private long lastSent = -1;

    public RigHistory() {
        super(UiTokens.SPACE_2);
        getStyleClass().add("es-rig-history");

        rows.put(RigTab.CPU, strip(cpu));
        rows.put(RigTab.MEMORY, strip(memory));
        rows.put(RigTab.DISK, strip(diskRead, diskWrite));
        rows.put(RigTab.NETWORK, strip(netIn, netOut));
        getChildren().addAll(rows.values());

        show(RigTab.OVERVIEW);
    }

    private static HBox strip(Sparkline... charts) {
        HBox row = new HBox(UiTokens.SPACE_6, charts);
        row.setAlignment(Pos.BOTTOM_LEFT);
        return row;
    }

    /** Shows the chart belonging to {@code tab} and hides the rest. */
    public void show(RigTab tab) {
        boolean any = false;
        for (Map.Entry<RigTab, HBox> entry : rows.entrySet()) {
            boolean on = entry.getKey() == tab;
            entry.getValue().setVisible(on);
            entry.getValue().setManaged(on);
            any |= on;
        }
        // Overview draws its own instrumentation and does not want a second copy of it, so the strip
        // takes no space at all there rather than sitting empty.
        setVisible(any);
        setManaged(any);
    }

    /**
     * Adds one sample from the whole process table.
     *
     * <p>Totals across every row, because that is what the machine is doing — a per-process history
     * would be thirty charts and would answer a question nobody asked. The table underneath is where
     * a player finds out <em>which</em> process moved.
     */
    public void sample(List<RigProcess> processes) {
        double cpuTotal = 0;
        long memoryTotal = 0;
        long read = 0;
        long written = 0;
        long rcvd = 0;
        long sent = 0;
        for (RigProcess p : processes) {
            cpuTotal += p.cpuPercent();
            memoryTotal += p.memoryBytes();
            read += p.bytesRead();
            written += p.bytesWritten();
            rcvd += p.rcvdBytes();
            sent += p.sentBytes();
        }

        cpu.push(Math.min(1.0d, cpuTotal / 100.0d), String.format(Locale.ROOT, "%.1f%%", cpuTotal));
        memory.push(Math.min(1.0d, memoryTotal / MEMORY_CEILING_BYTES), RigProcess.bytesText(memoryTotal));

        pushRate(diskRead, read, lastRead, DISK_CEILING_BYTES_PER_SECOND);
        pushRate(diskWrite, written, lastWritten, DISK_CEILING_BYTES_PER_SECOND);
        pushRate(netIn, rcvd, lastRcvd, NET_CEILING_BYTES_PER_SECOND);
        pushRate(netOut, sent, lastSent, NET_CEILING_BYTES_PER_SECOND);

        lastRead = read;
        lastWritten = written;
        lastRcvd = rcvd;
        lastSent = sent;
    }

    /**
     * Charts the change since the last sample, as a per-second rate.
     *
     * <p>⚠ The <b>first</b> sample charts zero rather than the whole accumulated total. A counter
     * that has been climbing since the character was created would otherwise draw one enormous spike
     * the moment the panel opened, pin the chart's ceiling, and flatten every real reading after it.
     *
     * <p>A negative difference also charts zero. It means the counters were reset — a process was
     * killed, or the rig was rebuilt — and a monotonic counter going backwards is a change in what is
     * being counted rather than a rate.
     */
    private void pushRate(Sparkline chart, long now, long previous, double ceiling) {
        double perSecond = previous < 0 || now < previous ? 0.0d : (now - previous) / (double) SAMPLE_SECONDS;
        chart.push(Math.min(1.0d, perSecond / ceiling), RigProcess.bytesText((long) perSecond) + "/s");
    }

    /** The engine's own step, matching {@code Vitals.INTERVAL_SECONDS}. */
    private static final double SAMPLE_SECONDS = 5.0d;
}
