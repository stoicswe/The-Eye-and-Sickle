package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.shell.BuiltinCommands;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.client.view.*;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Throwaway: does every tool window still BUILD and LAY OUT? */
public final class BuildAllProbe {
    private BuildAllProbe() {}

    public static void main(String[] a) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try { run(Path.of(a[0])); } catch (Exception e) { e.printStackTrace(); } finally { done.countDown(); }
        });
        done.await();
        Platform.exit();
    }

    private static void run(Path out) throws Exception {
        Path dir = out.resolve("buildall");
        dir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(dir);
        ThemeManager themes = new ThemeManager(profile);
        SoloGame game = SoloGame.open(new SaveStore(dir.resolve("s.json")), "halflight", Clock.systemUTC());
        LocalGameSession session = new LocalGameSession(game);
        TermDatabase terms = TermDatabase.load();
        Shell shell = new Shell(session, BuiltinCommands.registry());
        BreachArming arming = new BreachArming();

        for (WindowSpec spec : WindowSpec.values()) {
            try {
                javafx.scene.Node node = switch (spec) {
                    case RIG_MONITOR -> RigMonitorView.create(session, terms, profile);
                    case TERMINAL -> TerminalView.create(shell);
                    case BREACH -> BreachView.create(session, terms, profile, arming);
                    case NETMAP -> NetMapView.create(session, arming);
                    case AUDIT -> Views.audit(session, shell);
                    case MINING -> Views.mining(session);
                    case STORAGE -> Views.storage(session);
                    case LEDGER -> Views.ledger(session);
                    case DEFENSE -> Views.defense(session);
                    case IDENTITY -> Views.identity(session);
                    case MAN -> ManView.create(terms);
                    case LOG -> LogView.create(session);
                    case MARKET -> MoreViews.market(session);
                    case MAP -> MoreViews.map(session);
                    case RECON -> MoreViews.recon(session);
                    case BOTNET -> MoreViews.botnet(session);
                    case COMMS -> MoreViews.comms(session);
                    default -> new StackPane();
                };
                StackPane host = new StackPane(node);
                Scene sc = new Scene(host, 1100, 720);
                themes.adopt(sc);
                sc.getRoot().applyCss();
                host.layout();
                System.out.printf("OK    %-12s %s%n", spec, node.getClass().getSimpleName());
            } catch (Throwable t) {
                System.out.printf("FAIL  %-12s %s: %s%n", spec,
                        t.getClass().getSimpleName(), String.valueOf(t.getMessage()).split("\n")[0]);
            }
        }

        // And the engine side both tools depend on.
        System.out.println("--- engine ---");
        System.out.println("knownNodes: " + session.knownNodes().size());
        System.out.println("scan(quick): " + session.scan("quick").message());
        System.out.println("knownNodes after scan: " + session.knownNodes().size());
    }
}
