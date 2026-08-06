package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * TOR Marknet — the darknet board, reachable only through the onion router.
 *
 * <h2>⚠ A VENDOR, not a gate on the goods</h2>
 *
 * {@code docs/design/02-unlock-gates.md} §2.5 keeps these separate: the heat-state gate governs
 * "vendor and contact <em>access</em>. Never ownership." Everything listed here keeps its own single
 * gate — the Honeypot Stash is reputation-gated on this board exactly as it would be anywhere else,
 * for §2.3's reason that decoy infrastructure distorts raids if freely bought. What the module buys
 * is the ability to <em>see the shelf</em>. Invariant <b>I3</b> is untouched because reaching a
 * vendor and being allowed to buy from them are two different checks, and this window performs only
 * the first.
 *
 * <h2>⚠ What this board is NOT</h2>
 *
 * It is not a second storefront with better prices — that would make finding it an economic reward
 * and turn a heat gate into a discount, which is the {@code MarketDeals} arbitrage failure wearing a
 * different hat. It is the same catalogue, filtered to the stock GoH will not put on a public shelf.
 *
 * <p>⚠ It is also not the Shadow Market. ShMark is player-to-player at whatever two people agree to;
 * this is a vendor with a fixed list. Both are "the dodgy one" in a player's head, and the wordmarks
 * are what keep them apart.
 *
 * <h2>On the real Tor</h2>
 *
 * The fiction here is that an onion router resolves addresses ordinary lookups will not — which is
 * what onion services actually are. It should not be read as "Tor is for crime": its largest real
 * user groups are journalists, whistleblowers and people under censorship. If this window ever gains
 * a {@code terms/} page, that is the fact the page carries, and {@code docs/education} is where it
 * would be checked before it shipped.
 */
public final class TorMarknetView {

    private TorMarknetView() {}

    public static Region create(GameSession session) {
        VBox page = new VBox(UiTokens.SPACE_4);
        page.getStyleClass().add("es-market");
        page.setMaxWidth(UiTokens.MARKET_CONTENT_WIDTH);

        Label wordmark = new Label("TOR MARKNET");
        // ⚠ Not amber (GoH — a shop, income) and not alarm (ShMark — hostile, your money can simply
        // not come back). This is a vendor with a fixed list who happens to be hard to reach, so it
        // takes the neutral bright token. Colouring it would claim the game had an opinion the
        // mechanics do not support.
        wordmark.getStyleClass().add("es-marknet-wordmark");

        Label blurb = Views.wrapped(Views.t(
                "ui.marknet.blurb",
                "Stock that nobody will put on a public shelf. The board does not care who you are, "
                        + "which is not the same as it being safe — every gate on every item here is "
                        + "the gate it has everywhere else."));

        Label result = new Label();
        result.setWrapText(true);

        VBox shelf = new VBox(UiTokens.SPACE_3);

        // ⚠ Reputation-gated stock only, and derived from the CATALOGUE rather than listed here. A
        // hand-kept list is a second answer to "what is on this board", and the day somebody added a
        // reputation-gated item without touching this file it would be unreachable — a gate nobody
        // can pass because nothing displays it.
        Catalogue.offerings().stream()
                .filter(o -> o.gate() == UnlockGate.REPUTATION)
                .forEach(o -> shelf.getChildren().add(card(session, o, result)));

        if (shelf.getChildren().isEmpty()) {
            shelf.getChildren()
                    .add(Views.secondary(Views.t("ui.marknet.empty", "The board is quiet tonight.")));
        }

        page.getChildren().addAll(wordmark, blurb, new Separator(), shelf, result);
        VBox holder = new VBox(page);
        holder.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        return Views.scrollable(holder);
    }

    private static Region card(GameSession session, Catalogue.Offering offering, Label result) {
        VBox card = new VBox(UiTokens.SPACE_2);
        card.getStyleClass().add("es-market-card");

        Label name = new Label(offering.name());
        name.getStyleClass().add("es-market-name");

        Label what = Views.wrapped(offering.description());

        // ⚠ The gate's own sentence, not a price. A reputation-gated item has no price by
        // construction — CatalogueTest holds that nothing off the ethecoin gate carries one — so a
        // "0 EC" here would read as free, which is the exact misreading the gate exists to prevent.
        Label gate = Views.secondary(offering.gateRequirement());

        Button acquire = new Button(Views.t("ui.marknet.acquire", "Acquire"));
        acquire.setOnAction(e -> {
            GameSession.Outcome outcome = session.purchase(offering.id());
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
        });

        HBox foot = new HBox(UiTokens.SPACE_3, acquire);
        foot.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label cycles = Views.secondary(offering.equippedCycles() + "c " + Views.t("ui.marknet.while-armed", "while armed"));
        foot.getChildren().addAll(spacer, cycles);

        card.getChildren().addAll(name, what, gate, foot);
        return card;
    }
}
