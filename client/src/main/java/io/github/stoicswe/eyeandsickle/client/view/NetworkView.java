package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;

/**
 * NETWORK — the map, what has been learned about a target, and the way in.
 *
 * <h2>Why these three are one tool</h2>
 *
 * They are three views of the same subject: a machine out there. The map says where it is, recon
 * says what is known about it, and the breach is what you do about it. Three separate windows made
 * the player assemble that relationship themselves every time.
 *
 * <h2>⚠ THIS COSTS SIMULTANEITY, AND {@code docs/client/05} §44 ARGUES AGAINST IT</h2>
 *
 * That section is explicit: a breach is meant to span windows <em>the way a real operator's desk
 * does</em> — map for traversal, terminal for the active layer, recon for the logs the human-read
 * steps depend on — and the puzzle's anti-bot property (<b>I10</b>) is precisely that a human
 * cross-references material a fixed heuristic cannot. <em>"Cross-referencing two documents is a
 * simultaneity problem. A tabbed shell makes it a memory problem instead, which is a different and
 * worse game."</em>
 *
 * <p>Tabs here mean the map and the breach cannot be on screen at once.
 *
 * <p>⚠ <b>THIS COMMENT USED TO SAY "nothing breaks today, because the minigame is deliberately not
 * built". THAT IS NO LONGER TRUE (2026-08-07).</b> {@code design/05} reads <em>Decided
 * 2026-07-26</em>, {@code design/16} is <em>"The Breach, As Built"</em>, and there are two puzzle
 * classes, nine classes in {@code engine/breach/} and a {@link BreachView} playing it. The claim came
 * from a stale line in {@code CLAUDE.md}, now corrected. <b>So the cost is being paid right now
 * rather than deferred</b>, and the test §44 names is answerable today: the moment a layer requires
 * reading a recon log while looking at the board, a tab is the wrong container and the breach has to
 * come back out. Logged as <b>UI-8</b> in {@code docs/design/15-open-questions.md}.
 *
 * <p>⚠ Note the tension with the node menu's one-gesture breach, which deliberately makes this tab
 * <em>easier</em> to reach from the map. That is a bet on the tab; if UI-8 resolves the other way, it
 * is the thing to revisit.
 */
public final class NetworkView {

    private NetworkView() {}

    /**
     * @param session the session
     * @param arming the breach arming state, shared with the deck
     * @param nodeActions the map's node menu wiring
     * @param terms the manual, for the breach's teaching layer
     * @param profile appearance, for the breach
     * @return the tabbed network tool
     */
    public static Region create(
            GameSession session,
            BreachArming arming,
            NetMapView.NodeActions nodeActions,
            TermDatabase terms,
            ClientProfile profile) {

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("es-market-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab breach = new Tab("BREACH", BreachView.create(session, terms, profile, arming));

        // ⚠ MAP first. It is the only one of the three that answers "what is out there at all", so
        // it is where somebody with no target starts — and the other two are about a target.
        tabs.getTabs()
                .addAll(
                        new Tab("MAP", NetMapView.create(session, arming, nodeActions)),
                        new Tab("RECON", ReconView.create(session, nodeActions::info)),
                        breach,
                        // ⚠ BOTNET last, and the order is the operational sequence rather than an
                        // alphabet: find a machine, study it, get in, and then what you left running
                        // on it. A bot is the residue of the three tabs to its left.
                        new Tab("BOTNET", MoreViews.botnet(session)));

        // ⚠ REGISTERED HERE BECAUSE THIS IS THE ONLY PLACE THAT HOLDS THE TAB.
        //
        // Choosing Breach on the map's node menu has to land the player on this tab — the window
        // opens on MAP, so without this the menu armed a target, raised a window already in front of
        // them, and left the breach they asked for one tab away with nothing saying so.
        //
        // ⚠ Registered on every build, not once. DeskManager calls the window factory afresh each
        // time the window opens, so a selector captured at startup would point at a TabPane belonging
        // to a window that has since been closed.
        arming.setBreachFocus(() -> tabs.getSelectionModel().select(breach));
        return tabs;
    }
}
