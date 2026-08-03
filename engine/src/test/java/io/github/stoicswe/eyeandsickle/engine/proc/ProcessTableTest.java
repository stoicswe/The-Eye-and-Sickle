package io.github.stoicswe.eyeandsickle.engine.proc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.RigProcess;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.MinerState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The process table, and the promise it makes.
 *
 * <h2>The promise, in one sentence</h2>
 *
 * {@code docs/design/04-mining.md} §3.1: <em>the discrepancy is always present in the data</em>. That
 * cuts both ways and both halves are asserted here — a parasite must be <b>findable</b> by reading
 * the table, and it must be findable <b>only</b> by reading it. A test that could tell a parasite from
 * an honest row by looking at anything except the row's own printed fields would be describing a
 * feature that has already failed.
 */
class ProcessTableTest {

    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    private static GameSave character() {
        return GameEngine.newCharacter("operator", T0);
    }

    private static RigProcess parasiteRow(GameSave save, Instant now) {
        MinerState miner = save.rig.foreignMiners.getFirst();
        return ProcessTable.of(save, now).stream()
                .filter(p -> p.processId().equals("miner:" + miner.minerId))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("the table")
    class Shape {

        @Test
        @DisplayName("the haystack is there: a rig with nothing running still lists its daemons")
        void daemonsAlways() {
            GameSave save = character();
            List<RigProcess> table = ProcessTable.of(save, T0);
            // Without these there is nothing to hide among, and every row the player did not start
            // is the parasite — which is an audit that takes one glance, forever.
            assertThat(table).hasSizeGreaterThan(SystemProcesses.all().size());
            assertThat(table).anySatisfy(p -> assertThat(p.name()).isEqualTo("init"));
        }

        @Test
        @DisplayName("system rows are restartable and never killable")
        void systemRowsCannotBeKilled() {
            for (RigProcess process : ProcessTable.of(character(), T0)) {
                if (process.processId().startsWith("sys:")) {
                    assertThat(process.killable()).as(process.name()).isFalse();
                    assertThat(process.restartable()).as(process.name()).isTrue();
                }
            }
        }

        @Test
        @DisplayName("the table is FreeBSD-shaped: a bracketed kernel, a pid 1, real service accounts")
        void freeBsdShape() {
            List<RigProcess> table = ProcessTable.of(character(), T0);
            // Kernel threads are bracketed — a shape a player reads before the letters, and the
            // thing that makes "which of these are the kernel's" answerable at a glance.
            assertThat(table).anySatisfy(p -> assertThat(p.name()).isEqualTo("[pagedaemon]"));
            assertThat(table).anySatisfy(p -> assertThat(p.name()).isEqualTo("[g_up]"));
            assertThat(table)
                    .anySatisfy(p -> assertThat(p.name()).isEqualTo("init").isNotNull());
            // Real service accounts, and each of them on more than one row or clearly a system one.
            assertThat(table).anySatisfy(p -> assertThat(p.user()).isEqualTo("unbound"));
            assertThat(table).anySatisfy(p -> assertThat(p.user()).isEqualTo("_dhcp"));
        }

        @Test
        @DisplayName("a kernel thread does no disk or network I/O, because the real ones do not")
        void kernelThreadsHaveNoIo() {
            for (RigProcess process : ProcessTable.of(character(), T0.plusSeconds(9_000))) {
                if (!process.name().startsWith("[")) {
                    continue;
                }
                // Load-bearing as well as true: a parasite claiming to be a bracketed kernel thread
                // would have to show zero traffic to fit in, and it cannot — it is talking to
                // whoever planted it.
                assertThat(process.bytesRead()).as(process.name()).isZero();
                assertThat(process.sentBytes()).as(process.name()).isZero();
            }
        }

        @Test
        @DisplayName("the drifting figures hold still between repaints, so two rows can be compared")
        void stableFigures() {
            GameSave save = character();
            // ⚠ Not cosmetic. Two of the five disguises are caught by comparing figures, and numbers
            // that changed on every repaint would make both impossible and teach the player that the
            // table is noise.
            assertThat(ProcessTable.of(save, T0)).isEqualTo(ProcessTable.of(save, T0));

            RigProcess first = parasiteRow(save, T0);
            RigProcess again = parasiteRow(save, T0.plusMillis(400));
            assertThat(again.cpuPercent()).isEqualTo(first.cpuPercent());
            assertThat(again.memoryBytes()).isEqualTo(first.memoryBytes());
            assertThat(again.sentBytes()).isEqualTo(first.sentBytes());
        }

        @Test
        @DisplayName("CPU TIME is the one figure that does NOT hold still — it accumulates")
        void cpuTimeAccumulates() {
            GameSave save = character();
            // The exception, and it is the whole basis of the STOPPED_CLOCK tell: an honest process
            // banks processor time as the clock runs, so a row that does not is visibly claiming to
            // have been busy for longer than it has. Bucketing this one would delete that disguise.
            save.rig.foreignMiners.getFirst().disguise = Disguise.RESOURCE_HOG.name();
            assertThat(parasiteRow(save, T0.plusSeconds(600)).cpuTime())
                    .isGreaterThan(parasiteRow(save, T0.plusSeconds(60)).cpuTime());
        }

        @Test
        @DisplayName("gauges wander, and they wander around a resting level rather than teleporting")
        void gaugesWander() {
            GameSave save = character();
            List<Double> readings = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) {
                readings.add(ProcessTable.of(save, T0.plusSeconds(i * Vitals.INTERVAL_SECONDS)).stream()
                        .filter(p -> p.name().equals("devd"))
                        .findFirst()
                        .orElseThrow()
                        .cpuPercent());
            }
            // It moves.
            assertThat(readings.stream().distinct()).hasSizeGreaterThan(10);
            // And it stays in a band. White noise would be uniformly spread across the range every
            // tick, which reads as a slot machine rather than as a computer; a smoothed walk keeps a
            // resting level and strays from it.
            double lowest =
                    readings.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            double highest =
                    readings.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            assertThat(highest).isLessThan(lowest * 4.0d + 1.0d);
        }

        @Test
        @DisplayName("counters only ever go up — a byte total that fell would say the table is fake")
        void countersAreMonotonic() {
            GameSave save = character();
            long written = -1;
            long sent = -1;
            java.time.Duration cpuTime = java.time.Duration.ofSeconds(-1);
            for (int i = 0; i < 200; i++) {
                RigProcess row = ProcessTable.of(save, T0.plusSeconds(i * Vitals.INTERVAL_SECONDS)).stream()
                        .filter(p -> p.name().equals("netd"))
                        .findFirst()
                        .orElseThrow();
                assertThat(row.bytesWritten()).as("written at step %d", i).isGreaterThanOrEqualTo(written);
                assertThat(row.sentBytes()).as("sent at step %d", i).isGreaterThanOrEqualTo(sent);
                assertThat(row.cpuTime()).as("cpu time at step %d", i).isGreaterThanOrEqualTo(cpuTime);
                written = row.bytesWritten();
                sent = row.sentBytes();
                cpuTime = row.cpuTime();
            }
            assertThat(written).isPositive();
        }

        @Test
        @DisplayName("a thread count holds for a while rather than fidgeting every tick")
        void threadsChangeRarely() {
            GameSave save = character();
            List<Integer> readings = new java.util.ArrayList<>();
            for (int i = 0; i < 24; i++) {
                readings.add(ProcessTable.of(save, T0.plusSeconds(i * Vitals.INTERVAL_SECONDS)).stream()
                        .filter(p -> p.name().equals("sshd"))
                        .findFirst()
                        .orElseThrow()
                        .threads());
            }
            // Two minutes of readings: a handful of distinct values, not twenty-four. Threads do not
            // wander every five seconds on a real machine — they sit still and then a worker starts.
            assertThat(readings.stream().distinct()).hasSizeLessThanOrEqualTo(4);
        }

        @Test
        @DisplayName("sorting by a moving column genuinely re-orders as the figures change")
        void rowsMoveWhenSorted() {
            GameSave save = character();
            java.util.function.Function<Instant, List<String>> byCpu = at -> ProcessTable.of(save, at).stream()
                    .sorted(java.util.Comparator.comparingDouble(RigProcess::cpuPercent)
                            .reversed()
                            .thenComparingInt(RigProcess::pid))
                    .map(RigProcess::name)
                    .toList();
            // What the player asked for: rows jump. Over a couple of minutes a %CPU ordering has to
            // actually change, or the wander is too small to be worth drawing.
            List<String> first = byCpu.apply(T0);
            boolean moved = false;
            for (int i = 1; i <= 24 && !moved; i++) {
                moved = !byCpu.apply(T0.plusSeconds(i * Vitals.INTERVAL_SECONDS))
                        .equals(first);
            }
            assertThat(moved).isTrue();
        }

        @Test
        @DisplayName("reading the table draws nothing from the persisted generator")
        void neverTouchesTheRng() {
            GameSave save = character();
            long seedBefore = save.rngSeed;
            for (int i = 0; i < 20; i++) {
                ProcessTable.of(save, T0.plusSeconds(i * 7L));
            }
            // The RNG is persisted and every draw from it is a commitment. Decorating a readout from
            // it would let opening a window change a breach board.
            assertThat(save.rngSeed).isEqualTo(seedBefore);
        }

        @Test
        @DisplayName("a running tool appears once, not twice")
        void noDoubleCount() {
            GameSave save = character();
            GameEngine game = null;
            // The task and the allocation paying for it are the same thing to a player; listing both
            // says the rig is doing two things.
            save.rig.allocations.clear();
            save.rig.foreignMiners.clear();
            var allocation = io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules.reserve(
                    save.rig,
                    io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer.ACTIVE_TOOL,
                    "scan --full",
                    15L);
            var task = new io.github.stoicswe.eyeandsickle.engine.state.TaskState(
                    "scan", "scan --full", allocation.allocationId, 15L, T0, T0.plusSeconds(120));
            save.tasks.add(task);

            assertThat(ProcessTable.of(save, T0))
                    .filteredOn(p -> p.name().equals("scan --full"))
                    .hasSize(1);
            assertThat(game).isNull();
        }
    }

    @Nested
    @DisplayName("a parasite is findable, and only by reading")
    class Hiding {

        @Test
        @DisplayName("nothing on the row says it is hostile")
        void noTellTaleField() {
            GameSave save = character();
            RigProcess parasite = parasiteRow(save, T0);
            RigProcess honest = ProcessTable.of(save, T0).stream()
                    .filter(p -> p.processId().startsWith("sys:"))
                    .findFirst()
                    .orElseThrow();

            // ⚠ The core guarantee. Every capability field a client could branch on reads the same
            // for a parasite as for an ordinary user process; there is no `rogue` component on
            // RigProcess and there must never be one.
            assertThat(parasite.killable()).isTrue();
            assertThat(parasite.restartable()).isFalse();
            assertThat(honest.restartable()).isTrue();
        }

        @Test
        @DisplayName("it is on the table even though the compute ledger cannot see it")
        void visibleHereAndNowhereElse() {
            GameSave save = character();
            assertThat(save.rig.foreignMiners.getFirst().discovered).isFalse();

            // The grid comes up short and says nothing (ComputeRules.snapshot omits it). This table
            // is the one place an unaudited parasite is visible at all — in costume.
            assertThat(io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules.snapshot(save)
                            .reconciles())
                    .isFalse();
            assertThat(parasiteRow(save, T0)).isNotNull();
        }

        @Test
        @DisplayName("every disguise leaves a tell that is on the row")
        void everyDisguiseIsFindable() {
            for (Disguise disguise : Disguise.values()) {
                GameSave save = character();
                MinerState miner = save.rig.foreignMiners.getFirst();
                miner.disguise = disguise.name();
                // A twin needs something to copy; the others do not.
                miner.disguiseName = switch (disguise) {
                    case TOOL_TWIN -> "scan --full";
                    case SYSTEM_MIMIC -> "thermald";
                    case TYPOSQUAT -> "syspolicvd";
                    default -> "";
                };
                miner.disguiseUser = switch (disguise) {
                    case SYSTEM_MIMIC -> "_relay";
                    case TYPOSQUAT -> "root";
                    default -> "";
                };

                RigProcess row = parasiteRow(save, T0.plusSeconds(600));
                List<RigProcess> table = ProcessTable.of(save, T0.plusSeconds(600));

                switch (disguise) {
                    case SYSTEM_MIMIC ->
                        // The tell: the account appears exactly once in the whole table, while
                        // every real service account appears on more than one row.
                        assertThat(table)
                                .filteredOn(p -> p.user().equals(row.user()))
                                .hasSize(1);
                    case TYPOSQUAT ->
                        // The tell: the daemon it is imitating is in the same table, so sorting
                        // by name puts the two next to each other.
                        assertThat(table).anySatisfy(p -> assertThat(p.name()).isEqualTo("syspolicyd"));
                    case RESOURCE_HOG ->
                        // The tell: nothing the player started accounts for it.
                        assertThat(row.cpuPercent()).isGreaterThan(15.0d);
                    case STOPPED_CLOCK -> {
                        // The tell: a fifth of the machine, and seconds of accumulated time to show
                        // for it. Ten minutes of wall clock have passed in this fixture.
                        assertThat(row.cpuPercent()).isGreaterThan(15.0d);
                        assertThat(row.cpuTime()).isLessThan(Duration.ofSeconds(10));
                    }
                    case TOOL_TWIN -> assertThat(row.name()).isEqualTo("scan --full");
                }
            }
        }

        @Test
        @DisplayName("a pid gives it away for free: a daemon started at boot, this did not")
        void pidRange() {
            GameSave save = character();
            MinerState miner = save.rig.foreignMiners.getFirst();
            miner.disguise = Disguise.SYSTEM_MIMIC.name();
            miner.disguiseName = "thermald";
            miner.disguiseUser = "_relay";

            int highestDaemonPid = SystemProcesses.all().stream()
                    .mapToInt(SystemProcesses.Daemon::pid)
                    .max()
                    .orElseThrow();
            assertThat(parasiteRow(save, T0).pid()).isGreaterThan(highestDaemonPid);
        }

        @Test
        @DisplayName("it is talking to somebody, and a local tool is not")
        void trafficIsATell() {
            GameSave save = character();
            // A minute in, not at the instant it was planted: counters accumulate from zero, and a
            // process that has existed for no time has genuinely sent nothing. That is the honest
            // behaviour and it costs the disguise nothing — the first interval starts the clock.
            assertThat(parasiteRow(save, T0.plusSeconds(60)).sentBytes()).isPositive();
            // Self-mining reaches nothing (I4), so a "minerd" with packets on it is not the
            // player's — which is what makes the NETWORK tab decisive.
            save.rig.selfMiningCycles = 40L;
            assertThat(ProcessTable.of(save, T0))
                    .filteredOn(p -> p.processId().equals("alloc:self-mining"))
                    .allSatisfy(p -> assertThat(p.sentBytes()).isZero());
        }

        @Test
        @DisplayName("an audited parasite drops the costume rather than keeping it")
        void unmasked() {
            GameSave save = character();
            MinerState miner = save.rig.foreignMiners.getFirst();
            miner.disguise = Disguise.SYSTEM_MIMIC.name();
            miner.disguiseName = "thermald";
            miner.disguiseUser = "_relay";

            miner.discovered = true;
            RigProcess row = parasiteRow(save, T0);
            // Keeping it would mean this table contradicting the rig monitor, which by then names
            // the same process honestly.
            assertThat(row.name()).isNotEqualTo("thermald");
            assertThat(row.detail()).contains("somebody else's miner");
        }
    }

    @Nested
    @DisplayName("the disguise is chosen once")
    class Stability {

        @Test
        @DisplayName("a planted parasite keeps the same costume across every read")
        void neverRerolled() {
            GameSave save = character();
            MinerState miner = save.rig.foreignMiners.getFirst();
            String disguise = miner.disguise;
            String name = miner.disguiseName;

            for (int i = 0; i < 30; i++) {
                ProcessTable.of(save, T0.plusSeconds(i * 11L));
            }
            // ⚠ A disguise that changed between repaints would be unfindable by construction: the
            // player compares two readings of the same table, sees two different lies, and correctly
            // concludes the table is noise.
            assertThat(miner.disguise).isEqualTo(disguise);
            assertThat(miner.disguiseName).isEqualTo(name);
        }

        @Test
        @DisplayName("a save from before disguises existed hides badly rather than not at all")
        void oldSaves() {
            assertThat(Disguise.of("")).isEqualTo(Disguise.RESOURCE_HOG);
            assertThat(Disguise.of(null)).isEqualTo(Disguise.RESOURCE_HOG);
            assertThat(Disguise.of("A_DISGUISE_FROM_THE_FUTURE")).isEqualTo(Disguise.RESOURCE_HOG);
        }

        @Test
        @DisplayName("a typosquat always has a real daemon to sit beside")
        void squatsOnlyRealNames() {
            for (int seed = -50; seed < 50; seed++) {
                String victim = SystemProcesses.squattableName(seed);
                // Squatting a daemon the rig does not run would be an unfalsifiable name, which is
                // not a disguise — it is a riddle.
                assertThat(SystemProcesses.all())
                        .anySatisfy(d -> assertThat(d.name()).isEqualTo(victim));
            }
        }
    }
}
