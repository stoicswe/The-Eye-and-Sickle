package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Renders the storefront. Run by hand — the convention for anything that starts the toolkit. */
public final class MarketSnapshot {
    private MarketSnapshot() {}

    /** A clock that can be wound forward, so a progress bar has somewhere to have got to. */
    private static final class Winding extends Clock {
        private Instant at;

        Winding(Instant at) {
            this.at = at;
        }

        void wind(java.time.Duration by) {
            at = at.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return at;
        }
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        double width = args.length > 1 ? Double.parseDouble(args[1]) : 1180;
        double height = args.length > 2 ? Double.parseDouble(args[2]) : 1400;
        out.toFile().mkdirs();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try { render(out, width, height); } catch (Exception e) { e.printStackTrace(); }
            finally { done.countDown(); }
        });
        done.await();
        Platform.exit();
    }

    private static void render(Path out, double width, double height) throws Exception {
        Path dir = out.resolve("market-profile");
        dir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(dir);
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        // ⚠ uOS Classic is the palette that catches a colour bug — the only light one, where the
        // ramp runs the other way. -Dmarket.theme=classic.
        String theme = System.getProperty("market.theme");
        if (theme != null) {
            profile.appearance().themeId = theme;
        }
        ThemeManager themes = new ThemeManager(profile);

        // ⚠ A WINDING clock, not a fixed one. A transfer's progress is (now - startedAt) / duration,
        // so under a fixed clock every bar is photographed at exactly 0% — the one value that is
        // indistinguishable from the download never having started. -Dmarket.elapsed=<seconds>.
        Winding clock = new Winding(Instant.parse("2026-08-04T12:00:00Z"));
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")), "halflight", clock);
        game.credit(Balance.ec("500"), "TEST", "seed");
        LocalGameSession session = new LocalGameSession(game);

        // ⚠ Resting orders have to be PLACED, not drawn — YOUR ORDERS renders from real state, so a
        // render with none photographs the empty case and reports the Cancel buttons as absent.
        int resting = Integer.getInteger("market.orders", 0);
        if (resting > 0) {
            game.credit(io.github.stoicswe.eyeandsickle.engine.Balance.ec("5000"), "TEST", "seed");
            var listings = session.shadowListings();
            for (int i = 0; i < resting && i < listings.size(); i++) {
                String id = listings.get(i);
                // Bid well under the market so it rests rather than filling on the next tick.
                java.math.BigInteger mid = session.shadowMarket(id, "M5", 2).mid();
                System.out.println(id + ": "
                        + session.placeShadowOrder(
                                        id, true, mid.divide(java.math.BigInteger.valueOf(2)), 1, "")
                                .message());
            }
        }

        // ⚠ The dock hides itself when nothing is owed, so a render with an empty queue photographs
        // the one state that is indistinguishable from the feature being absent. -Dmarket.queue=N
        // buys N things first. Set -Dmarket.bundle=1 to buy today's bundle as well.
        // ⚠ The BUNDLE first. A bundle is all-or-nothing and refuses if any member is already
        // queued, so buying singles first makes the bundle unbuyable — the harness would render the
        // refusal rather than the feature, and the refusal is correct, which is worse.
        if (Boolean.getBoolean("market.bundle")) {
            System.out.println("bundle: " + session.purchaseBundle().message());
        }
        int queued = Integer.getInteger("market.queue", 0);
        if (queued > 0) {
            session.market().deals().stream()
                    .map(deal -> deal.offeringId())
                    .limit(queued)
                    .forEach(id -> System.out.println(id + ": " + session.purchase(id).message()));
        }

        // ⚠ NO second ScrollPane. The view brings its own, inside a StackPane that carries the
        // download dock — wrapping it again hands that StackPane its PREFERRED height, so
        // "bottom-centre" lands a page and a half below the viewport and the dock is photographed
        // as absent. A harness that reports a feature missing when it is present is worse than none.
        // Let the active download actually get somewhere before the shutter opens.
        clock.wind(java.time.Duration.ofSeconds(Integer.getInteger("market.elapsed", 0)));
        game.tick();

        Region market = MarketView.create(session);
        StackPane host = new StackPane(market);
        host.getStyleClass().add("es-panel-body");
        Scene scene = new Scene(host, width, height);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();

        // ⚠ Fires the REAL toggle rather than exposing a back door for the harness. A render flag
        // that built the panel expanded would photograph a state the control cannot actually
        // produce, which is the failure mode a render harness exists to rule out.
        // ⚠ applyCss() FIRST — lookup matches on style class and finds nothing before it, so this
        // reports "no toggle" on a scene that certainly has one.
        // ⚠ The market is two tabs now, and a Scene renders what it last laid out — toggling tab
        // selection between two synchronous snapshots yields two identical images (the LedgerSnapshot
        // trap). -Dmarket.tab=1 selects ShMark and the harness is run twice.
        if (Integer.getInteger("market.tab", 0) != 0) {
            javafx.scene.Node tabs = scene.getRoot().lookup(".es-market-tabs");
            if (tabs instanceof javafx.scene.control.TabPane pane) {
                pane.getSelectionModel().select(Integer.getInteger("market.tab", 0));
                scene.getRoot().applyCss();
                host.layout();
            }
        }
        // ⚠ The drawer opens on HOVER and steps on a Pulse — neither of which a synchronous render
        // produces, so a single-pass render photographs the one state indistinguishable from the
        // feature being absent. -Dmarket.drawer=true drives it open directly.
        if (Boolean.getBoolean("market.drawer")) {
            javafx.scene.Node drawer = scene.getRoot().lookup(".es-shmark-drawer");
            if (drawer != null) {
                drawer.setTranslateX(0);
                host.layout();
            }
        }
        if (Boolean.getBoolean("market.dockExpanded")) {
            javafx.scene.Node toggle = scene.getRoot().lookup(".es-market-dock-toggle");
            if (toggle instanceof javafx.scene.control.Button button) {
                button.fire();
                scene.getRoot().applyCss();
                host.layout();
            } else {
                System.out.println("no dock toggle on screen -- is anything queued?");
            }
        }

        WritableImage image = scene.snapshot(new WritableImage((int) width, (int) height));
        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < (int) image.getHeight(); y++)
            for (int x = 0; x < (int) image.getWidth(); x++) png.setRGB(x, y, pixels.getArgb(x, y));
        ImageIO.write(png, "png", new File(out.resolve("market.png").toString()));
        System.out.println("wrote " + out.resolve("market.png"));
    }
}
