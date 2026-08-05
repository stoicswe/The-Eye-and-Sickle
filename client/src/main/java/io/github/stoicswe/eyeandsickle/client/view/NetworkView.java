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
 * <p>Tabs here mean the map and the breach cannot be on screen at once. Nothing breaks today,
 * because the minigame in {@code design/05} is a {@code [PROPOSAL]} and is deliberately not built —
 * so this is a real cost that is currently unpaid. ⚠ <b>If the breach puzzle is built, the breach
 * probably has to come back out of this window</b>, and the cross-referencing steps are the test:
 * the moment a layer requires reading a recon log while looking at the board, a tab is the wrong
 * container. Logged as <b>UI-8</b> in {@code docs/design/15-open-questions.md}.
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

        // ⚠ MAP first. It is the only one of the three that answers "what is out there at all", so
        // it is where somebody with no target starts — and the other two are about a target.
        tabs.getTabs()
                .addAll(
                        new Tab("MAP", NetMapView.create(session, arming, nodeActions)),
                        new Tab("RECON", ReconView.create(session, nodeActions::info)),
                        new Tab("BREACH", BreachView.create(session, terms, profile, arming)),
                        // ⚠ BOTNET last, and the order is the operational sequence rather than an
                        // alphabet: find a machine, study it, get in, and then what you left running
                        // on it. A bot is the residue of the three tabs to its left.
                        new Tab("BOTNET", MoreViews.botnet(session)));
        return tabs;
    }

}
