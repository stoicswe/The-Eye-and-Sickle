package io.github.stoicswe.eyeandsickle.solo.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.FsKind;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TopologyState;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Transfer timing, and the one claim it exists to make.
 *
 * <p>The load-bearing test is {@link Link#remoteUploadIsTheCeiling()}. Everything else here is
 * refusal handling; that one is the reason the two link constants are different numbers.
 */
class TransferRulesTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    private static SoloSave connected() {
        SoloSave save = new SoloSave();
        save.rig.totalCycles = 100;
        save.topology = new TopologyState();

        HostState self = new HostState();
        self.address = "10.0.0.1";
        self.kind = "SELF";
        self.discovered = true;
        self.foothold = true;

        HostState target = new HostState();
        target.address = "10.0.0.9";
        target.kind = "TERMINAL";
        target.discovered = true;
        target.foothold = true;

        save.topology.hosts.add(self);
        save.topology.hosts.add(target);
        save.topology.vantageAddress = "10.0.0.1";
        SessionRules.open(save, "10.0.0.9", NOW);
        return save;
    }

    private static FsEntry file(String name, long bytes, FsKind kind, boolean readable) {
        return new FsEntry(name, "/home/dana/" + name, kind, bytes, "-rw-r--r--", "dana", "dana", NOW, readable);
    }

    @Nested
    @DisplayName("⚠ the link")
    class Link {

        @Test
        @DisplayName("the REMOTE END'S UPLOAD is the ceiling, not your download")
        void remoteUploadIsTheCeiling() {
            // The whole point of having two constants. A Gigabit downlink against a 150 Mbit uplink
            // moves 18.75 MB/s — and raising the downlink would change nothing, which is what makes
            // this worth teaching rather than merely modelling.
            assertThat(Balance.downloadBytesPerSecond())
                    .isEqualTo(Balance.LINK_UP_BITS / 8L)
                    .isEqualTo(18_750_000L);
            assertThat(Balance.LINK_DOWN_BITS).isGreaterThan(Balance.LINK_UP_BITS);
        }

        @Test
        @DisplayName("a 200 MB upgrade takes about eleven seconds")
        void aRealUpgradeTakesARealTime() {
            // Long enough to be a decision, short enough not to be a wait. If this drifts far from
            // ten seconds, either the link constants or the package sizes have moved and the other
            // needs re-checking — they are calibrated against each other.
            assertThat(Balance.transferTime(200_000_000L).toSeconds()).isBetween(10L, 12L);
        }

        @Test
        @DisplayName("a few-kilobyte fragment is effectively instant, and says so honestly")
        void smallFilesAreInstant() {
            // No artificial minimum beyond the handshake. A 4 kB document really does arrive
            // immediately on this link, and padding it out to look busy would be a lie told by a
            // progress bar.
            assertThat(Balance.transferTime(4_096L).toMillis())
                    .isBetween(Balance.TRANSFER_SETUP_MS, Balance.TRANSFER_SETUP_MS + 5);
        }

        @Test
        @DisplayName("nothing is ever zero-length in time — the handshake is real")
        void setupIsAlwaysPaid() {
            assertThat(Balance.transferTime(0L).toMillis()).isEqualTo(Balance.TRANSFER_SETUP_MS);
            assertThat(Balance.transferTime(-5L).toMillis()).isEqualTo(Balance.TRANSFER_SETUP_MS);
        }
    }

    @Nested
    @DisplayName("what transfers")
    class Transferable {

        @Test
        @DisplayName("fragments, wallets, upgrades and schematics — and nothing else")
        void narrowByDesign() {
            assertThat(TransferRules.transferable(file("doc.txt", 4_000, FsKind.DOCUMENT, true)))
                    .isTrue();
            assertThat(TransferRules.transferable(file("wallet.dat", 900, FsKind.LOOT, true)))
                    .isTrue();
            assertThat(TransferRules.transferable(file("a.pkg", 90_000_000, FsKind.FILE, true)))
                    .isTrue();
            assertThat(TransferRules.transferable(file("b.schematic", 4_000_000, FsKind.FILE, true)))
                    .isTrue();

            // Scenery. A download that yields nothing teaches a player that downloads yield nothing.
            assertThat(TransferRules.transferable(file("syslog", 40_000, FsKind.FILE, true)))
                    .isFalse();
            assertThat(TransferRules.transferable(
                            new FsEntry("etc", "/etc", FsKind.DIRECTORY, 0, "d", "r", "r", NOW, true)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("no session means nothing to pull over, and the refusal says to mount it")
        void needsAConnection() {
            SoloSave save = connected();
            SessionRules.close(save, "10.0.0.9");
            assertThat(TransferRules.begin(
                                    save,
                                    "10.0.0.9",
                                    file("a.pkg", 90_000_000, FsKind.FILE, true),
                                    "/home/op/Downloads",
                                    NOW)
                            .refusal())
                    .isEqualTo(TransferRules.Refusal.NOT_CONNECTED);
        }

        @Test
        @DisplayName("an unreadable file is refused — the rules' verdict, not the view's")
        void needsReadability() {
            assertThat(TransferRules.begin(
                                    connected(),
                                    "10.0.0.9",
                                    file("a.pkg", 90_000_000, FsKind.FILE, false),
                                    "/home/op/Downloads",
                                    NOW)
                            .refusal())
                    .isEqualTo(TransferRules.Refusal.NOT_READABLE);
        }

        @Test
        @DisplayName("asking twice for the same file does not start two transfers")
        void noDoubleStart() {
            SoloSave save = connected();
            FsEntry entry = file("a.pkg", 90_000_000, FsKind.FILE, true);
            assertThat(TransferRules.begin(save, "10.0.0.9", entry, "/home/op/Downloads", NOW)
                            .succeeded())
                    .isTrue();
            assertThat(TransferRules.begin(save, "10.0.0.9", entry, "/home/op/Downloads", NOW)
                            .refusal())
                    .isEqualTo(TransferRules.Refusal.ALREADY_RUNNING);
            assertThat(TransferRules.inFlight(save)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("a transfer is a task")
    class AsATask {

        @Test
        @DisplayName("it lands in save.tasks, so it survives the window being closed")
        void isPersisted() {
            // The reason it is a task rather than an animation: closing the file manager must not
            // cancel a download, and reopening must show it still running.
            SoloSave save = connected();
            TransferRules.begin(
                    save, "10.0.0.9", file("a.pkg", 90_000_000, FsKind.FILE, true), "/home/op/Downloads", NOW);

            assertThat(save.tasks).anyMatch(task -> TransferRules.KIND.equals(task.kind));
            var task = TransferRules.inFlight(save).getFirst();
            assertThat(TransferRules.addressOf(task)).isEqualTo("10.0.0.9");
            assertThat(TransferRules.pathOf(task)).isEqualTo("/home/dana/a.pkg");
            assertThat(TransferRules.bytesOf(task)).isEqualTo(90_000_000L);
        }

        @Test
        @DisplayName("it holds no compute, because moving bytes is I/O and not arithmetic")
        void costsNoCycles() {
            SoloSave save = connected();
            long before = save.rig.allocations.size();
            TransferRules.begin(
                    save, "10.0.0.9", file("a.pkg", 90_000_000, FsKind.FILE, true), "/home/op/Downloads", NOW);
            assertThat(save.rig.allocations).hasSize((int) before);
            assertThat(TransferRules.inFlight(save).getFirst().cycles).isZero();
        }

        @Test
        @DisplayName("progress runs off the two timestamps and reaches 1 at the deadline")
        void progressIsDerived() {
            SoloSave save = connected();
            var started = TransferRules.begin(
                    save, "10.0.0.9", file("a.pkg", 90_000_000, FsKind.FILE, true), "/home/op/Downloads", NOW);

            assertThat(started.task().progressAt(NOW)).isZero();
            assertThat(started.task().progressAt(NOW.plus(started.duration()))).isEqualTo(1.0d);
            assertThat(started.task().isFinishedAt(NOW.plus(started.duration())))
                    .isTrue();
        }
    }
}
