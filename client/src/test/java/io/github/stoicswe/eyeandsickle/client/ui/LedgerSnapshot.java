package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.Views;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javax.imageio.ImageIO;

/**
 * Renders the LEDGER window's three tabs, and the {@code SYNCHRONIZING} panel, to PNGs.
 *
 * <h2>Why the ledger gets a snapshot of its own</h2>
 *
 * {@link DeckSnapshot} opens four windows at desk size and proves the deck lays out. It cannot prove
 * anything about this window, because the three things added on 2026-07-29 are exactly the three
 * kinds of thing a text assertion cannot see: a <b>ten-column table</b> whose headers only fit if the
 * preferred widths sum to less than the panel, a <b>discrete meter</b> replaying a fill, and a
 * <b>summary block</b> whose lines are chosen by branches most loads do not take.
 *
 * <p>It also needs a save that is not a fresh character. The interesting states here — a chain that
 * ran for eight hours without the client, a contributor history with both paid and unpaid rows —
 * only exist after a session has been played and closed, so this builds one rather than snapshotting
 * the empty case and calling it verified.
 *
 * <pre>{@code
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.LedgerSnapshot \
 *     -Dexec.args="/tmp/ledger 1180 900"
 * }</pre>
 */
public final class LedgerSnapshot {

    private LedgerSnapshot() {}

    private static final Instant T0 = Instant.parse("2026-07-28T09:00:00Z");

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        double width = args.length > 1 ? Double.parseDouble(args[1]) : 1180;
        double height = args.length > 2 ? Double.parseDouble(args[2]) : 900;
        outputDir.toFile().mkdirs();

        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                render(outputDir, width, height);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void render(Path outputDir, double width, double height) throws Exception {
        Path profileDir = outputDir.resolve("profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);
        // See DeckSnapshot: set on the PROFILE before ThemeManager exists, or the OS preference
        // silently overwrites it and every panel stays clipped to zero width by Motion.reveal.
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        ThemeManager themes = new ThemeManager(profile);

        Path save = profileDir.resolve("save.json");

        // ── A played session, so CONTRIBUTOR has history to show ──────────────────────────────
        // Pooled, because the default pool is pay-per-share and that is the row whose "your cut" is
        // legitimately zero — the one case a reviewer most needs to see rendered, since it is
        // indistinguishable from a bug in a screenshot nobody took.
        // ⚠ All THREE payout schemes, because the credit column renders differently under each and a
        // fixture on the default pool alone would verify one of them. A default character is on
        // pay-per-share, whose rows correctly credit nothing from the block — so a snapshot of only
        // that is a snapshot of the case most likely to look broken, with nothing to compare it to.
        Ticking clock = new Ticking(T0);
        SoloGame played = SoloGame.open(new SaveStore(save), "halflight", clock);
        played.allocateSelfMining(60);
        for (int hour = 0; hour < 14; hour++) {
            clock.advance(Duration.ofHours(1));
            played.tick();
        }
        // PPLNS: paid out of blocks the pool actually won, so these rows carry a real cut.
        played.setPool("meridian");
        played.setPool("glass-teeth");
        for (int hour = 0; hour < 14; hour++) {
            clock.advance(Duration.ofHours(1));
            played.tick();
        }
        // Solo: the whole block, and the rows marked YOUR RIG.
        played.setMiningMode(MiningMode.SOLO);
        for (int hour = 0; hour < 40; hour++) {
            clock.advance(Duration.ofHours(1));
            played.tick();
        }
        played.setMiningMode(MiningMode.POOLED);
        // A pending transaction, so the mempool strip and the "confirmed while away" line both have
        // something to report after the absence.
        played.debit(
                Balance.ec("2.5"),
                "TRANSFER",
                "Sent to an address",
                io.github.stoicswe.eyeandsickle.protocol.game.FeeTier.PRIORITY,
                io.github.stoicswe.eyeandsickle.solo.rules.ChainExplorer.address("someone"));
        played.persist();

        // ── Eight hours away. The rig runs for four of them and then spins down (I5). ──────────
        // ⚠ The SAME winding clock, not Clock.fixed. The shot of a block's transaction list needs a
        // block near the tip that actually carries one of the player's rows, and that means being
        // able to send after the sync and then tick until it confirms — which a fixed clock cannot
        // do. The 8-hour jump is the absence; the ticks afterwards are the session.
        clock.advance(Duration.ofHours(8));
        SoloGame reopened = SoloGame.open(new SaveStore(save), "halflight", clock);
        // ⚠ A win planted directly in the strip's window, because the point of the shot is the
        // AMBER pill and a 3.5% rig wins about one block in twenty-eight — a fixture that relied on
        // the draw would render the thing under test about as often as not. blocksWon is exactly
        // what ChainExplorer.header reads to mark a block as the player's, so this is the real path
        // and not a stub.
        reopened.state().chain.blocksWon.add(reopened.state().chain.height - 2);
        // Two of the player's own rows, confirmed into a block near the tip.
        reopened.debit(
                Balance.ec("12.5"),
                "TRANSFER",
                "Sent to a broker",
                io.github.stoicswe.eyeandsickle.protocol.game.FeeTier.PRIORITY,
                io.github.stoicswe.eyeandsickle.solo.rules.ChainExplorer.address("broker"));
        reopened.debit(
                Balance.ec("4"),
                "MARKET",
                "Bought Canary Token",
                io.github.stoicswe.eyeandsickle.protocol.game.FeeTier.PRIORITY,
                io.github.stoicswe.eyeandsickle.solo.rules.ChainExplorer.address("vendor"));
        for (int i = 0; i < 40 && reopened.state().chain.mempool.size() > 0; i++) {
            clock.advance(Duration.ofMinutes(5));
            reopened.tick();
        }
        // The height carrying the player's own confirmed transaction, for the detail shot.
        long mine = reopened.state().ledger.stream()
                .filter(e -> e.blockNumber > 0)
                .mapToLong(e -> e.blockNumber)
                .max()
                .orElse(reopened.state().chain.height - 2);
        System.out.println("player's transaction is in block " + mine);
        LocalGameSession session = new LocalGameSession(reopened);
        // ⚠ One transaction left DELIBERATELY PENDING and un-ticked, so the shot shows the YOUR
        // PENDING strip with its boost chip. Everything above it was drained on purpose; a snapshot
        // of an empty queue verifies nothing about the control that lives on a queued row.
        reopened.debit(
                Balance.ec("3"),
                "MARKET",
                "Bought Noise Damper",
                io.github.stoicswe.eyeandsickle.protocol.game.FeeTier.ECONOMY,
                io.github.stoicswe.eyeandsickle.solo.rules.ChainExplorer.address("vendor"));
        System.out.println("sync: " + reopened.chainSync());
        System.out.println("contributions: " + reopened.contributions(8).size());

        // ⚠ A FRESH view and Scene per tab, rather than one scene clicked through three times.
        //
        // Measured: `Scene.snapshot` picks up a CSS change but NOT a plain `setVisible` toggle made
        // between two synchronous snapshots of the same Scene — pushing a visibility change into the
        // render tree needs a real pulse, and nothing in a headless run fires one. The three PNGs
        // came out byte-identical while the chip labels proved the tab state really had changed,
        // which is the worst possible failure for a verification tool: it reports success and shows
        // the wrong screen. Building each tab's scene from scratch means every shot is a first
        // render, which is also what a player actually gets when they open the window.
        for (LedgerShot shot : LedgerShot.values()) {
            Region ledger = (Region) Views.ledger(session);
            // ⚠ The miner pill is a CHIP by default and a PILL only under `.es-rounded` — §9's radius
            // ban is unamended, so the shape is gated on the same class the window opt-in uses. Both
            // states are shot, because "it rounds" and "it does not round when it should not" are two
            // different claims and the second is the one a contract test cannot make about pixels.
            if (shot.rounded) {
                ledger.getStyleClass().add("es-rounded");
            }
            // The window's own panel background, so the shot shows the panel rather than a
            // transparent rectangle over the scene ground.
            StackPane host = new StackPane(ledger);
            host.getStyleClass().add("es-panel-body");
            Scene scene = new Scene(host, width, height);
            themes.adopt(scene);
            // ⚠ CSS first, then the click. `lookupAll` matches on style class and finds NOTHING on a
            // scene whose CSS has never been applied, so clicking before this throws "no tab chip
            // named LEDGER" on a strip that is plainly there.
            scene.getRoot().applyCss();
            host.layout();
            if (shot.chip != null) {
                click(host, shot.chip);
            } else {
                // Select a card, so the shot shows the selected state rather than the resting one.
                selectBlock(host, mine);
            }
            // Second pass, same reason as DeckSnapshot: the first sized the panels, this lays them
            // out against widths that are finally known.
            scene.getRoot().applyCss();
            host.layout();
            shoot(scene, outputDir.resolve("ledger-" + shot.file + ".png"), width, height);
        }
    }

    /**
     * The states worth a picture, in the order a player would reach them.
     *
     * <p>⚠ {@link #CHAIN_REOPENED} is the same tab as {@link #CHAIN} and is not redundant: the
     * SYNCHRONIZING panel is announced <b>once per session</b>, so the pair is the check that closing
     * and reopening the window does not replay a fill that finished an hour ago. It has to come last,
     * because it is only meaningful after something has already taken the report.
     */
    private enum LedgerShot {
        CHAIN(null, "chain", false),
        LEDGER("LEDGER", "ledger", false),
        CONTRIBUTOR("CONTRIBUTOR", "contributor", false),
        CHAIN_REOPENED(null, "chain-reopened", false),
        CHAIN_ROUNDED(null, "chain-rounded", true);

        private final String chip;
        private final String file;
        private final boolean rounded;

        LedgerShot(String chip, String file, boolean rounded) {
            this.chip = chip;
            this.file = file;
            this.rounded = rounded;
        }
    }

    /**
     * Clicks a card in the RECENT BLOCKS strip so a shot shows a live selection.
     *
     * <p>⚠ Prefers the block carrying the player's OWN transaction when it is on screen. A shot of a
     * block full of derived network traffic verifies the table and says nothing about the one thing
     * the detail view was rebuilt for, which is finding your own rows in two hundred.
     */
    private static void selectBlock(Region root, long preferred) {
        for (Node node : root.lookupAll(".es-block")) {
            if (node.getUserData() instanceof Long h && h == preferred) {
                fire(node);
                return;
            }
        }
        int seen = 0;
        for (Node node : root.lookupAll(".es-block")) {
            if (node.getUserData() instanceof Long && ++seen == 3) {
                fire(node);
                return;
            }
        }
    }

    private static void fire(Node node) {
        node.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null));
    }

    /** Fires the tab chip whose text names {@code tab}. */
    private static void click(Region root, String tab) {
        for (Node node : Set.copyOf(root.lookupAll(".es-breach-chip"))) {
            if (node instanceof Label label && label.getText().contains(tab)) {
                label.fireEvent(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        0,
                        0,
                        0,
                        0,
                        javafx.scene.input.MouseButton.PRIMARY,
                        1,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        true,
                        false,
                        false,
                        null));
                return;
            }
        }
        throw new IllegalStateException("no tab chip named " + tab);
    }

    private static void shoot(Scene scene, Path to, double width, double height) throws Exception {
        // Scene.snapshot takes only a target image — SnapshotParameters is Node's overload.
        WritableImage image = scene.snapshot(new WritableImage((int) width, (int) height));
        BufferedImage out =
                new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                out.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(out, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }

    /** A clock the harness moves by hand — the solo module's TestClock is not on this classpath. */
    private static final class Ticking extends Clock {

        private Instant at;

        Ticking(Instant at) {
            this.at = at;
        }

        void advance(Duration by) {
            at = at.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return at;
        }
    }
}
