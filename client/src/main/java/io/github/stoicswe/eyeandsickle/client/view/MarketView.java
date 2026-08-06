package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.Durability;
import io.github.stoicswe.eyeandsickle.engine.rules.Archives;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.MarketWindow;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/**
 * MARKET — the storefront.
 *
 * <h2>What changed, and why it is not just decoration</h2>
 *
 * This was a panel that explained the <em>gate taxonomy</em>: four boxes describing what each unlock
 * gate means, a table of price bands, and the offerings underneath as a flat list. That is a good
 * explanation and a poor shop. A player arrives wanting to know <b>what can I afford, what is worth
 * buying today, and what is this going to cost me</b> — and the answer to all three was below the
 * fold, behind a lecture.
 *
 * <p>So the order is inverted: the balance and today's deals first, the shelf second, and the gate
 * explanations kept but demoted to where they belong — attached to the items they actually block.
 * ⚠ <b>The explanations are not deleted.</b> A gated item that simply showed no price would read as
 * a bug; the whole point of {@code docs/design/02}'s taxonomy is that a refusal is legible.
 *
 * <h2>⚠ Prices come from {@link GameSession#market()}, never from the catalogue</h2>
 *
 * The catalogue supplies names, descriptions and the gate. What something <em>costs today</em> is a
 * rules answer, and the rules run on a server for LAN and federated play. A card that priced itself
 * from {@code Offering.priceWei} would show the undiscounted number beside a "20% OFF" flash — the
 * shop contradicting itself on the same card.
 *
 * <h2>⚠ On a clock, because the window expires</h2>
 *
 * The countdown is wall-clock text, so it is on {@link Pulse#every} — the same split the file
 * manager's transfer bar makes. Nothing about a save changes as a deal ages, so an {@code onChange}
 * listener would leave "2 days left" on screen until the player bought something.
 */
public final class MarketView {

    private MarketView() {}

    /**
     * How long a featured offer holds before the carousel advances itself.
     *
     * <p>⚠ Long. Six seconds is comfortably more than it takes to read a name, a price and a saving,
     * and a carousel that moves before a reader has finished is one they fight rather than watch.
     * It is also moot under Reduce motion, where it never advances at all.
     */
    private static final double CAROUSEL_DWELL_MS = 6000;

    /**
     * @param session the session, for prices and purchases
     * @return the storefront
     */
    public static Region create(GameSession session) {
        return create(session, 60);
    }

    /**
     * @param refreshSeconds how often AnonShare asks for a price — the player's own setting
     */
    public static Region create(GameSession session, int refreshSeconds) {
        // ⚠ THREE MARKETS, three tabs, and they are different KINDS of market rather than three
        // views of one. GoH sells at a price somebody else set; ShMark is where what players already
        // hold changes hands at whatever two people agree to; AnonShare tracks an outside world the
        // game does not control at all. Folding them into one page would put a storefront's
        // certainties beside a market's risks beside a real exchange, with nothing saying which was
        // which — and the last of those is the one a player could act on outside the game.
        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane();
        tabs.getStyleClass().add("es-market-tabs");
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);

        javafx.scene.control.Tab storefront = new javafx.scene.control.Tab("GoH", storefront(session));
        javafx.scene.control.Tab shadow = new javafx.scene.control.Tab("ShMark", ShadowMarketView.create(session));
        // ⚠ AnonShare repaints on the PLAYER'S cadence, not the deck's one-second clock. A share
        // price moves on a scale of minutes, and every refresh spends part of a free-tier allowance
        // the player pays for out of their own quota.
        javafx.scene.control.Tab shares = new javafx.scene.control.Tab(
                "AnonShare", AnonShareView.create(session, refreshSeconds));
        tabs.getTabs().addAll(storefront, shadow, shares);

        // ⚠ TOR MARKNET IS PRESENT ONLY IF THE RIG HOLDS THE MODULE, and it is added/removed rather
        // than disabled. `docs/design/02` §2.5 gates a VENDOR's reachability, not an item's
        // ownership — and a tab the player can see but not open is an advertisement for content
        // they have no route to, which is the opposite of what a heat-state gate is for. Somebody
        // who has not been noticed should not know there is a board.
        //
        // ⚠ Rechecked on session change, because the module arrives DURING play: the notice lands in
        // COMS on the tick that standing and heat cross, and the tab has to appear when the module
        // installs rather than on the next restart.
        Runnable[] syncMarknet = new Runnable[1];
        syncMarknet[0] = () -> {
            boolean unlocked = holdsTorModule(session);
            boolean shown = tabs.getTabs().stream().anyMatch(t -> MARKNET.equals(t.getText()));
            if (unlocked && !shown) {
                tabs.getTabs().add(new javafx.scene.control.Tab(MARKNET, TorMarknetView.create(session)));
            } else if (!unlocked && shown) {
                tabs.getTabs().removeIf(t -> MARKNET.equals(t.getText()));
            }
        };
        syncMarknet[0].run();
        AutoCloseable onSession = session.onChange(s -> syncMarknet[0].run());
        Views.releaseOnDetach(tabs, onSession);
        return tabs;
    }

    /** The tab's label, and the key the sync above matches on. */
    private static final String MARKNET = "TOR Marknet";

    /**
     * Whether this rig holds the onion router.
     *
     * <h2>⚠ Ownership of an ITEM, not a re-check of the thresholds</h2>
     *
     * Reading standing and heat here instead would make the board vanish the moment the player went
     * cold — and {@code docs/design/02} §2.5 is explicit that "going cold does not confiscate what
     * you bought". The introduction, once made, has been made; the module is an ordinary item after
     * that, and the thresholds are consumed exactly once, by {@code rules/BlackMarket}.
     */
    private static boolean holdsTorModule(GameSession session) {
        for (io.github.stoicswe.eyeandsickle.protocol.game.StorageTier tier :
                io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.values()) {
            for (GameSession.InventoryItem item : session.items(tier)) {
                if (io.github.stoicswe.eyeandsickle.engine.Catalogue.TOR_MODULE.equals(item.itemType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Region storefront(GameSession session) {
        VBox page = new VBox(UiTokens.SPACE_4);
        page.getStyleClass().add("es-market");

        Label result = new Label();
        result.setWrapText(true);

        // ── the masthead ─────────────────────────────────────────────────────────────────────────
        //
        // ⚠ ONE nav row, not two. A real storefront has an account-level bar above the shop's own
        // (STORE / LIBRARY / COMMUNITY / you), and this deliberately does not: the deck already
        // supplies window chrome, a rail and a top strip carrying the player's identity and balance,
        // so a second account bar inside one window would be the same navigation twice — the part of
        // the reference layout that was explicitly struck out.
        Label wordmark = new Label("GROUP OF HACKS");
        wordmark.getStyleClass().add("es-market-wordmark");
        Label tagline = Ui.micro("software, mostly legitimate");
        tagline.getStyleClass().add("es-market-tagline");

        Label balance = new Label();
        balance.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-market-balance");
        Label countdown = Ui.micro("");
        Label restock = Ui.micro("");

        HBox masthead = Ui.row(UiTokens.SPACE_3, wordmark, tagline, Ui.spacer(), balance);
        masthead.setAlignment(Pos.BASELINE_LEFT);
        masthead.getStyleClass().add("es-market-masthead");

        TextField search = new TextField();
        search.setPromptText("Search the store");
        search.getStyleClass().add("es-market-search");
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox nav = Ui.row(UiTokens.SPACE_3, search, countdown, restock);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getStyleClass().add("es-market-nav");

        VBox deals = new VBox(UiTokens.SPACE_3);
        VBox shelf = new VBox(UiTokens.SPACE_4);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            MarketWindow window = session.market();
            setBalance(balance, session);
            String query = search.getText() == null ? "" : search.getText().trim();
            // ⚠ The deals strip is NOT filtered by the search. A search is "show me what matches";
            // the strip answers "what is worth buying today", and hiding an offer because it did not
            // match a word the player typed would make the shop look like it had no offers.
            deals.getChildren().setAll(dealStrip(session, window, result, refresh));
            shelf.getChildren().setAll(shelfSections(session, window, query, result, refresh));
            countdown.setText(countdownText(window));
            restock.setText(restockText(window));
        };
        refresh[0].run();
        session.onChange(s -> refresh[0].run());
        search.textProperty().addListener((observable, was, now) -> refresh[0].run());

        // ⚠ Pulse.every — data. Both captions are elapsed-time text and would otherwise freeze at
        // whatever they said when the panel opened.
        AutoCloseable clock = Pulse.shared().every(1000, () -> {
            MarketWindow window = session.market();
            countdown.setText(countdownText(window));
            restock.setText(restockText(window));
        });

        page.getChildren().addAll(masthead, nav, deals, shelf, result);

        // ⚠ setFitToWidth, or the content is handed its PREFERRED width and nothing wraps — a
        // horizontal scrollbar appears instead of the cards reflowing. Same trap PackageView records.
        // ⚠ A fixed content column, CENTRED — the masthead, the search bar, the offers and the shelf
        // all take one measure. ⚠ The cap goes on the PAGE and the centring on a holder around it: a
        // ScrollPane with setFitToWidth resizes its content to the viewport and has no alignment for
        // content narrower than that, so capping the page alone would pin the whole shop to the left
        // edge of a wide window.
        page.setMaxWidth(UiTokens.MARKET_CONTENT_WIDTH);
        VBox holder = new VBox(page);
        holder.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroll = new ScrollPane(holder);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-market-scroll");
        Views.releaseOnDetach(scroll, clock);

        // ⚠ The dock is LAID OVER the page, never added to it. A panel in the page flow would
        // appear above or below the fold depending on where the player had scrolled, so the one
        // confirmation that a purchase worked would be invisible about half the time — and it would
        // move the shelf every time a download started. See DownloadDock.
        StackPane framed = new StackPane(scroll, DownloadDock.create(session));
        framed.getStyleClass().add("es-market-frame");
        return framed;
    }

    /**
     * ⚠ TWO decimals, with the exact figure one hover away — the top strip's licensed exception, not
     * a second one.
     *
     * <p>{@code CLAUDE.md}'s rule is that a held amount is never rounded, because a rounded amount
     * somebody holds is a lie they cannot detect. The strip earned an exception by keeping the exact
     * value reachable on hover and in {@code accessibleText}, and the sharpened rule is exactly that:
     * <b>a held amount may be abbreviated only where the exact figure is one hover away</b>. So this
     * does both, and the LEDGER remains exact.
     */
    private static void setBalance(Label label, GameSession session) {
        java.math.BigInteger wei = session.balance().wei();
        // ⚠ formatApprox appends the unit itself — a second " EC" here rendered "500 EC EC".
        label.setText(Ethecoin.formatApprox(wei, 2));
        label.setTooltip(new javafx.scene.control.Tooltip(Ethecoin.format(wei)));
        label.setAccessibleText("balance " + Ethecoin.format(wei));
    }

    // ------------------------------------------------------------------ today's deals

    /**
     * TODAY'S OFFERS — one featured deal at a time, with arrows to flip through them.
     *
     * <h2>⚠ One at a time, not three across, and that is a content decision rather than a style one</h2>
     *
     * {@code MarketDeals.DEALS_PER_WINDOW} is three and the catalogue has six purchasable items, so a
     * three-across strip already showed <em>every</em> offer at once — there was nothing to flip
     * through, and arrows on it would have been controls that changed nothing. One large card is a
     * carousel that has somewhere to go, and it gives each deal room for its description and price
     * instead of a column of thirty characters.
     *
     * <h2>⚠ It WRAPS, and it does not slide</h2>
     *
     * Past the last offer is the first one — that is the "rotating" part. What it deliberately does
     * not do is animate between them: §5 permits no easing anywhere and {@code UiContractTest} rations
     * {@code AnimationTimer} to two files by name, so a sliding carousel is build-blocking here. A
     * page swap is instantaneous, which is also simply better for reading a price.
     *
     * <h2>⚠ Auto-advance is MOTION, so it is on {@code Pulse.animate} and pauses on hover</h2>
     *
     * Under Reduce motion the carousel holds still and the arrows are the only way through it —
     * which is the correct reading of WCAG 2.2.2 for auto-updating content, not a degraded one. It
     * also pauses while the pointer is over the card or a control has focus, because advancing out
     * from under somebody who is reading is the failure the guideline is about.
     */
    private static List<Region> dealStrip(
            GameSession session, MarketWindow window, Label result, Runnable[] refresh) {
        if (window.deals().isEmpty() && window.bundle().isEmpty()) {
            // ⚠ Says WHY rather than rendering nothing. An empty strip on a shop reads as a panel
            // that failed to load; "no offers" is a fact, and online it is the honest one until the
            // transport lands.
            VBox empty = new VBox(Ui.micro("No offers on this shelf."));
            empty.getStyleClass().add("es-market-card");
            return List.of(empty);
        }

        List<MarketWindow.Deal> offers = window.deals().stream()
                .filter(deal -> Catalogue.byId(deal.offeringId()).isPresent())
                .toList();

        // ⚠ The bundle sits BELOW the carousel with a BAND of clear space either side of it. It is a
        // different kind of offer from the ones above and the shelf below — one price for several
        // things — and stacked flush against them all three read as one undifferentiated column of
        // cards. The band is the only thing saying "this is a different question".
        List<Region> out = new ArrayList<>();
        if (!offers.isEmpty()) {
            out.add(carousel(session, window, offers, result, refresh));
        }
        window.bundle().flatMap(bundle -> bundleCard(session, bundle, result, refresh)).ifPresent(card -> {
            out.add(band());
            out.add(card);
            out.add(band());
        });
        return out;
    }

    /**
     * Clear space, above and below the bundle.
     *
     * <p>⚠ An explicit node rather than more spacing on the container. The gap belongs to the bundle,
     * not to every gap in the deals block — widening the VBox's spacing would push the carousel away
     * from the masthead too, which is a different relationship and already right.
     */
    private static Region band() {
        Region gap = new Region();
        gap.setMinHeight(UiTokens.MARKET_BAND_GAP);
        gap.setPrefHeight(UiTokens.MARKET_BAND_GAP);
        return gap;
    }

    private static Region carousel(
            GameSession session,
            MarketWindow window,
            List<MarketWindow.Deal> offers,
            Label result,
            Runnable[] refresh) {

        VBox frame = new VBox(UiTokens.SPACE_2);
        frame.getStyleClass().add("es-market-carousel");
        // ⚠ No width of its own — it takes the page's content column, like everything else on the
        // page. It used to carry its own cap, from when the page was as wide as the window and a
        // full-width hero was a band across the screen; with a fixed column that cap would leave a
        // dead strip inside the column instead, which is the same complaint one level in.

        Label heading = heading("TODAY'S OFFERS");
        heading.getStyleClass().add("es-market-section");

        // ⚠ int[] rather than a field: this whole view is static factories, and the index has to
        // survive the lambdas below without becoming shared state between two open MARKET windows.
        int[] index = {0};
        StackPane stage = new StackPane();
        stage.getStyleClass().add("es-market-stage");
        HBox dots = new HBox(UiTokens.SPACE_2);
        dots.setAlignment(Pos.CENTER);
        dots.getStyleClass().add("es-market-dots");

        Button previous = arrow("←", "Previous offer");
        Button next = arrow("→", "Next offer");
        Label position = Ui.micro("");

        Runnable show = () -> {
            MarketWindow.Deal deal = offers.get(Math.floorMod(index[0], offers.size()));
            Catalogue.Offering offering = Catalogue.byId(deal.offeringId()).orElseThrow();
            stage.getChildren().setAll(dealCard(session, offering, deal, window, result, refresh));
            position.setText((Math.floorMod(index[0], offers.size()) + 1) + " / " + offers.size());

            dots.getChildren().clear();
            for (int i = 0; i < offers.size(); i++) {
                Region dot = new Region();
                // ⚠ DRAWN, not a glyph. GlyphCoverageTest fails the build on any codepoint missing
                // from the bundled faces, and it has already rejected four block elements and the
                // warning sign. A styled Region cannot be uncovered.
                dot.getStyleClass().add("es-market-dot");
                if (i == Math.floorMod(index[0], offers.size())) {
                    dot.getStyleClass().add("es-market-dot-on");
                }
                dots.getChildren().add(dot);
            }
            // ⚠ Announced as a whole, because a screen reader lands on the card and not on the dots.
            // Without this the only signal that there are two more offers is a visual one.
            stage.setAccessibleText("Offer " + (Math.floorMod(index[0], offers.size()) + 1) + " of "
                    + offers.size() + ": " + offering.name());
        };
        // ⚠ floorMod, not %. Java's remainder keeps the sign, so stepping back from the first offer
        // would index -1 and throw — the wrap is the whole point of the control.
        previous.setOnAction(event -> {
            index[0] = Math.floorMod(index[0] - 1, offers.size());
            show.run();
        });
        next.setOnAction(event -> {
            index[0] = Math.floorMod(index[0] + 1, offers.size());
            show.run();
        });
        // ⚠ Disabled when there is nothing to flip to. An arrow that visibly does nothing is worse
        // than no arrow — the player concludes the control is broken rather than that they have seen
        // everything.
        boolean single = offers.size() < 2;
        previous.setDisable(single);
        next.setDisable(single);
        dots.setVisible(!single);
        dots.setManaged(!single);

        show.run();

        HBox controls = Ui.row(UiTokens.SPACE_3, heading, Ui.spacer(), position, previous, next);
        controls.setAlignment(Pos.CENTER_LEFT);

        // ⚠ Pulse.ANIMATE, not every. Auto-advance is motion: under Reduce motion this subscription
        // never fires and the carousel holds on whichever offer it is showing, which is WCAG 2.2.2's
        // "stop" satisfied structurally rather than by a second control nobody would find.
        boolean[] paused = {false};
        // ⚠ `settled` SKIPS THE FIRST INVOCATION, and without it the carousel opens on offer 2.
        //
        // Pulse.animate runs its action ONCE IMMEDIATELY — documented, and right for its usual
        // caller, which is a widget that would otherwise be blank until its first tick. This action
        // is an ADVANCE rather than a paint, so that immediate call steps the carousel before anybody
        // has seen it. ⚠ Worse under Reduce motion: the immediate call still happens and the periodic
        // one never does, so the shelf opens on the second offer and stays there — the first offer
        // reachable only by pressing an arrow, on the accessibility path.
        boolean[] settled = {false};
        AutoCloseable rotate = Pulse.shared().animate(CAROUSEL_DWELL_MS, () -> {
            if (!settled[0]) {
                settled[0] = true;
                return;
            }
            // ⚠ The SECOND settling path: Pulse.setReducedMotion(true) fires every decorative
            // subscription once, so a widget suppressed mid-animation paints its final state. For an
            // advance that is one more step — turning Reduce motion on would skip the player forward
            // a card. The flag is set before the loop fires, so asking here catches it.
            if (Pulse.shared().reducedMotion()) {
                return;
            }
            if (paused[0] || offers.size() < 2) {
                return;
            }
            index[0] = Math.floorMod(index[0] + 1, offers.size());
            show.run();
        });
        // ⚠ Pauses while somebody is reading it. Advancing out from under a pointer is the failure
        // 2.2.2 is actually about, and hover is the cheapest honest signal that a card is being read.
        frame.hoverProperty().addListener((observable, was, now) -> paused[0] = now);
        previous.focusedProperty().addListener((observable, was, now) -> paused[0] = now);
        next.focusedProperty().addListener((observable, was, now) -> paused[0] = now);
        Views.releaseOnDetach(frame, rotate);

        frame.getChildren().addAll(controls, stage, dots);
        return frame;
    }

    /**
     * ⚠ {@code ←} and {@code →} (U+2190/U+2192) are safe: {@code NetCanvas} already renders both, so
     * {@code GlyphCoverageTest} has proved them present in the bundled faces. A guillemet or a
     * triangle would have needed checking first.
     */
    private static Button arrow(String glyph, String description) {
        Button button = new Button(glyph);
        button.getStyleClass().add("es-market-arrow");
        button.setAccessibleText(description);
        button.setTooltip(new javafx.scene.control.Tooltip(description));
        return button;
    }

    /**
     * The featured card — one offer, given room.
     *
     * <p>⚠ Wider and taller than a shelf card because it is alone on the stage. The strip version had
     * to fit three across and lost the description to a thirty-character column; a hero card that did
     * the same would be a carousel showing one cramped card instead of three.
     */
    private static Region dealCard(
            GameSession session,
            Catalogue.Offering offering,
            MarketWindow.Deal deal,
            MarketWindow window,
            Label result,
            Runnable[] refresh) {
        VBox card = new VBox(UiTokens.SPACE_3);
        // ⚠ NO `es-panel`. `.es-market-card` supplies the LIFTED ground now, and `.es-panel` would
        // set the window body's own colour — two single-class rules, so the winner is whichever the
        // stylesheet declares later. That is not a thing to depend on when the answer decides
        // whether a card is visible at all.
        card.getStyleClass().addAll("es-market-card", "es-market-deal", "es-market-hero");

        Label flash = new Label(deal.percentOff() + "% OFF");
        flash.getStyleClass().add("es-market-flash");

        Label name = new Label(offering.name());
        name.getStyleClass().addAll("es-panel-title", "es-market-hero-name");

        // ⚠ The old price is struck through and KEPT on screen. A sale price with nothing to compare
        // it against is just a price — the saving is the entire information a deal carries.
        Region was = struck(Ethecoin.format(deal.fullPriceWei()));
        Label now = new Label(Ethecoin.format(deal.priceWei()));
        now.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-market-now", "es-market-hero-price");
        Label saving = Ui.micro("save " + Ethecoin.format(deal.fullPriceWei().subtract(deal.priceWei())));
        saving.getStyleClass().add("es-market-saving");

        HBox prices = Ui.row(UiTokens.SPACE_3, now, was, saving);
        prices.setAlignment(Pos.BASELINE_LEFT);

        Label blurb = Views.wrapped(offering.description());
        blurb.getStyleClass().add("es-text-secondary");

        HBox footer = Ui.row(
                UiTokens.SPACE_3,
                buy(session, offering, window, result, refresh),
                cycles(offering),
                Ui.spacer(),
                stockLine(window, offering));
        footer.setAlignment(Pos.CENTER_LEFT);

        card.getChildren()
                .addAll(Ui.row(UiTokens.SPACE_2, flash, Ui.spacer(), durabilityChip(offering)), name, blurb, prices, footer);
        return card;
    }

    private static Optional<Region> bundleCard(
            GameSession session, MarketWindow.Bundle bundle, Label result, Runnable[] refresh) {
        List<Catalogue.Offering> items = bundle.offeringIds().stream()
                .map(Catalogue::byId)
                .flatMap(Optional::stream)
                .toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        VBox card = new VBox(UiTokens.SPACE_2);
        card.getStyleClass().addAll("es-market-card", "es-market-bundle");
        // ⚠ Shrink-wrapped VERTICALLY, and it still needs saying in a VBox. The deals block is a
        // VBox, which does not stretch children to fill spare height — but the band spacers around
        // it are Regions with no maximum, so a future Vgrow anywhere in this column would hand the
        // bundle the slack instead. The card's height is its content's; nothing else may set it.
        card.setMaxHeight(Region.USE_PREF_SIZE);

        Label flash = new Label("BUNDLE  ·  " + bundle.percentOff() + "% OFF");
        flash.getStyleClass().add("es-market-flash");

        VBox contents = new VBox(2);
        for (Catalogue.Offering item : items) {
            contents.getChildren()
                    .add(Ui.micro("· " + item.name() + "   " + Ethecoin.format(item.priceWei())));
        }

        Label now = new Label(Ethecoin.format(bundle.priceWei()));
        now.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-market-now");
        Region was = struck(Ethecoin.format(bundle.fullPriceWei()));

        // ⚠ ONE action, at the bundle price — `session.purchaseBundle()`, never a loop over
        // `purchase()`. A loop charges retail per item and silently discards the discount the card
        // is advertising, which is the shop contradicting itself in the ledger.
        Button buy = new Button("Purchase Bundle");
        buy.getStyleClass().addAll("es-market-buy", "es-market-bundle-buy");
        buy.setOnAction(event -> {
            GameSession.Outcome outcome = session.purchaseBundle();
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            refresh[0].run();
        });

        Label caveat = Views.wrapped("Ships as one " + Archives.SUFFIX
                + " — unpack it in Downloads and the packages are inside.");
        caveat.getStyleClass().add("es-text-secondary");

        card.getChildren()
                .addAll(flash, contents, Ui.row(UiTokens.SPACE_2, now, was), caveat, buy);
        return Optional.of(card);
    }

    // ------------------------------------------------------------------ the shelf

    private static List<Region> shelfSections(
            GameSession session, MarketWindow window, String query, Label result, Runnable[] refresh) {
        List<Region> out = new ArrayList<>();

        out.add(section(
                "CONSUMABLES",
                "Spent when used, and bought again. Where a sale is worth waiting for.",
                Catalogue.offerings().stream()
                        .filter(Catalogue.Offering::purchasable)
                        .filter(offering -> offering.durability() == Durability.CONSUMABLE)
                        .filter(offering -> offering.matches(query))
                        .toList(),
                session,
                window,
                result,
                refresh));

        out.add(section(
                "TOOLS & UPGRADES",
                "Bought once and kept. Ethecoin buys breadth here, never a ceiling.",
                Catalogue.offerings().stream()
                        .filter(Catalogue.Offering::purchasable)
                        .filter(offering -> offering.durability() == Durability.PERMANENT)
                        .filter(offering -> offering.matches(query))
                        .toList(),
                session,
                window,
                result,
                refresh));

        // ⚠ SCHEMATIC-GATED ITEMS ARE NOT LISTED HERE AT ALL any more (2026-08-04). They used to
        // appear under NOT FOR SALE with their gate spelled out, which was honest while a schematic
        // was a key you presented to the shop. It is a blueprint now — you build the item in the
        // Assembl Compiler — so the shop has nothing to say about them beyond where to go, and a
        // priced-looking card for something the shop will never sell is worse than no card.
        //
        // ⚠ The OTHER gates still appear. Proof-of-skill, reputation and zero-day items are still
        // things the shop refuses to sell, and `design/02`'s taxonomy exists precisely so a refusal
        // is legible — deleting those would turn a legible gate into a missing item.
        List<Catalogue.Offering> gated = Catalogue.offerings().stream()
                .filter(offering -> !offering.purchasable())
                .filter(offering -> offering.gate() != UnlockGate.SCHEMATIC)
                .filter(offering -> offering.matches(query))
                .toList();
        if (!gated.isEmpty()) {
            out.add(section(
                    "NOT FOR SALE",
                    "These exist and are not purchasable at any price. What it takes is on each card — "
                            + "that is the point of the gate, not an oversight in the shop.",
                    gated,
                    session,
                    window,
                    result,
                    refresh));
        }
        boolean anySchematic = Catalogue.offerings().stream()
                .anyMatch(offering -> offering.gate() == UnlockGate.SCHEMATIC);
        if (anySchematic) {
            VBox pointer = new VBox(UiTokens.SPACE_2);
            pointer.getChildren()
                    .addAll(
                            heading("BUILT, NOT BOUGHT"),
                            Views.wrapped("Anything a schematic gates is made in the Assembl Compiler "
                                    + "from a blueprint you hold. It is not stocked here at any price — "
                                    + "there is no amount of ethecoin that produces one."));
            out.add(pointer);
        }
        return out;
    }

    private static Region section(
            String title,
            String explanation,
            List<Catalogue.Offering> offerings,
            GameSession session,
            MarketWindow window,
            Label result,
            Runnable[] refresh) {
        VBox box = new VBox(UiTokens.SPACE_3);
        Label heading = heading(title);
        heading.getStyleClass().add("es-market-section");
        Label blurb = Views.wrapped(explanation);
        blurb.getStyleClass().add("es-text-secondary");

        // ⚠ TilePane, not FlowPane, and that is what makes it read as a shelf. A FlowPane gives each
        // card its own preferred height and centres it in whatever the tallest card in the row
        // turned out to be, so a long description leaves its neighbours floating with their Buy
        // buttons at three different heights — a styled list. A TilePane sizes EVERY tile to the
        // largest, and a VBox's maximum is unbounded (a Control's would not be — the Vgrow trap),
        // so each card fills its tile and the spacer inside puts every price and every Buy on the
        // same line across the whole shelf.
        TilePane grid = new TilePane(UiTokens.SPACE_3, UiTokens.SPACE_3);
        grid.setPrefColumns(1);
        for (Catalogue.Offering offering : offerings) {
            grid.getChildren().add(shelfCard(session, offering, window, result, refresh));
        }
        box.getChildren().addAll(heading, blurb);
        // ⚠ An empty section says so rather than disappearing. A heading that vanishes when a search
        // matches nothing makes the shop look like it lost a category, and a player cannot tell that
        // from "your search excluded it".
        box.getChildren().add(offerings.isEmpty() ? Ui.micro("Nothing here matches that search.") : grid);
        return box;
    }

    private static Region shelfCard(
            GameSession session,
            Catalogue.Offering offering,
            MarketWindow window,
            Label result,
            Runnable[] refresh) {
        VBox card = new VBox(UiTokens.SPACE_2);
        card.getStyleClass().add("es-market-card");
        card.setPrefWidth(UiTokens.MARKET_CARD_WIDTH);

        Label name = new Label(offering.name());
        name.getStyleClass().add("es-panel-title");
        Label blurb = Views.wrapped(offering.description());
        blurb.getStyleClass().add("es-text-secondary");

        card.getChildren().addAll(Ui.row(UiTokens.SPACE_2, name, Ui.spacer(), durabilityChip(offering)), blurb);

        // ⚠ Pushes everything below it to the foot of the TILE, not of the content. This is the
        // half that makes uniform tiles worth having: without it a short description leaves its Buy
        // button floating in the middle of an otherwise full-height card.
        Region gap = Ui.vspacer();
        card.getChildren().add(gap);
        VBox.setVgrow(gap, Priority.ALWAYS);

        if (offering.purchasable()) {
            Optional<MarketWindow.Deal> deal = window.dealFor(offering.id());
            Label price = new Label(Ethecoin.format(
                    deal.map(MarketWindow.Deal::priceWei).orElseGet(offering::priceWei)));
            price.getStyleClass().addAll("es-numeric", "es-ethecoin");
            HBox prices = Ui.row(UiTokens.SPACE_2, price);
            deal.ifPresent(d -> {
                Region was = struck(Ethecoin.format(d.fullPriceWei()));
                Label flash = new Label(d.percentOff() + "% OFF");
                flash.getStyleClass().add("es-market-flash");
                prices.getChildren().addAll(was, flash);
            });
            prices.setAlignment(Pos.BASELINE_LEFT);
            card.getChildren().addAll(prices, cycles(offering), stockLine(window, offering), buy(session, offering, window, result, refresh));
        } else {
            Label gate = new Label(gateLabel(offering.gate()));
            gate.getStyleClass().add("es-state-unreachable");
            Label why = Views.wrapped(offering.gateRequirement());
            why.getStyleClass().add("es-text-secondary");
            card.getChildren().addAll(gate, why, cycles(offering));
        }
        return card;
    }

    // ------------------------------------------------------------------ bits

    /**
     * The old price, with a line drawn through it.
     *
     * <h2>⚠ `-fx-strikethrough` DOES NOT WORK ON A LABEL, and it fails silently</h2>
     *
     * It is a property of {@link javafx.scene.text.Text}, not of {@code Labeled} — JavaFX gives
     * {@code Labeled} {@code -fx-underline} and no strikethrough — and JavaFX <b>drops a property it
     * does not recognise without failing</b>, exactly as it drops an unknown looked-up colour (the
     * {@code -es-accent} trap). So {@code .es-market-was { -fx-strikethrough: true; }} was declared,
     * applied, and never drew anything: the shop showed two prices side by side with nothing saying
     * which one was cancelled. Confirmed by magnifying a render — it is invisible in review because
     * the stylesheet reads exactly right.
     *
     * <h2>Why a drawn rule rather than a {@code Text} node</h2>
     *
     * A {@code Text} would support the property, and it would also step outside everything that
     * makes text here measurable: it colours with {@code -fx-fill} rather than {@code -fx-text-fill},
     * so {@code ContrastTest} — which measures text tokens against the panel grounds in all six
     * palettes — would stop seeing this price. A drawn {@code Region} keeps the {@code Label} and
     * cannot be silently dropped, which is the same reasoning behind the carousel's drawn dots and
     * the flash overlay's drawn warning mark.
     *
     * <h2>⚠ The baseline is delegated, or the price floats</h2>
     *
     * A {@code Region}'s {@code getBaselineOffset()} is {@code BASELINE_OFFSET_SAME_AS_HEIGHT}, so in
     * the {@code BASELINE_LEFT} rows these sit in, a bare {@code StackPane} would align its <em>bottom
     * edge</em> to the row's baseline and ride up above the price beside it.
     */
    private static Region struck(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-market-was");

        Region rule = new Region();
        rule.getStyleClass().add("es-market-was-rule");
        rule.setMaxHeight(UiTokens.HAIR);
        rule.setMinHeight(UiTokens.HAIR);
        rule.setMouseTransparent(true);
        // ⚠ Bound to the LABEL's width, not left to the StackPane. A StackPane stretches a resizable
        // child to fill it, so an unbound rule would be as wide as whatever else lands in the stack
        // — and `maxWidth` alone would let it collapse to nothing, since a Region's preferred width
        // is zero.
        rule.maxWidthProperty().bind(label.widthProperty());
        rule.minWidthProperty().bind(label.widthProperty());

        StackPane box = new StackPane(label, rule) {
            @Override
            public double getBaselineOffset() {
                return label.getBaselineOffset();
            }
        };
        // ⚠ Struck through the DIGITS, not through the middle of the box. A label's box carries the
        // descender space, so its centre sits below the middle of a row of figures — measured at
        // about an eighth of the font size, which is small and reads as a line that has slipped.
        // Derived from the applied font so it follows a text-size change rather than being pinned to
        // whatever the font happened to be when this was written.
        rule.translateYProperty()
                .bind(javafx.beans.binding.Bindings.createDoubleBinding(
                        () -> -label.getFont().getSize() * STRIKE_RISE, label.fontProperty()));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * How far above a label's box centre its digits are, as a fraction of the font size.
     *
     * <p>Not in {@code UiTokens}: it is not a spacing or a size but a property of how a typeface sits
     * in its box, and §2.3's scale is closed and about layout.
     */
    private static final double STRIKE_RISE = 0.125;

    /** A section or panel heading — the deck's one spelling, `es-panel-title`. */
    private static Label heading(String text) {
        Label label = Ui.label(text);
        label.getStyleClass().add("es-panel-title");
        return label;
    }

    private static Region cycles(Catalogue.Offering offering) {
        // ⚠ Shown on the card, beside the price. Compute is the master scarcity and a standing cycle
        // cost is the half of a purchase a player forgets — a card that quoted only ethecoin would
        // hide the more binding number.
        return offering.equippedCycles() > 0
                ? Ui.micro(offering.equippedCycles() + (offering.equippedCycles() == 1 ? " cycle" : " cycles") + " while armed")
                : Ui.micro("holds no cycles");
    }

    private static Region durabilityChip(Catalogue.Offering offering) {
        if (!offering.purchasable()) {
            return Ui.micro("");
        }
        Label chip = Ui.micro(offering.durability() == Durability.CONSUMABLE ? "CONSUMABLE" : "PERMANENT");
        chip.getStyleClass().add("es-market-chip");
        return chip;
    }

    private static Region buy(
            GameSession session,
            Catalogue.Offering offering,
            MarketWindow window,
            Label result,
            Runnable[] refresh) {
        boolean soldOut = !window.inStock(offering.id());
        Button buy = new Button(soldOut ? "Sold out" : "Buy");
        buy.getStyleClass().add("es-market-buy");
        // ⚠ Disabled rather than hidden. A missing button is indistinguishable from a broken card;
        // a disabled one labelled "Sold out" says what happened and that it will come back.
        buy.setDisable(soldOut);
        buy.setOnAction(event -> {
            GameSession.Outcome outcome = session.purchase(offering.id());
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            refresh[0].run();
        });
        return buy;
    }

    private static String gateLabel(UnlockGate gate) {
        return gate.name().toLowerCase(Locale.ROOT).replace('_', '-') + " gate";
    }

    /**
     * ⚠ ABSENT means "not stocked", which is not zero. A gated item is never for sale; a sold-out one
     * is back tomorrow. Rendering both as "0 left" tells a player to wait for something that is never
     * coming.
     */
    private static Region stockLine(MarketWindow window, Catalogue.Offering offering) {
        return window.stockFor(offering.id())
                .map(left -> left <= 0
                        ? Ui.micro("sold out — restocks daily")
                        : Ui.micro(left + " in stock"))
                .map(Region.class::cast)
                .orElseGet(() -> Ui.micro(""));
    }

    private static String restockText(MarketWindow window) {
        if (window.stock().isEmpty()) {
            return "";
        }
        Duration left = window.untilRestock();
        long hours = left.toHours();
        return hours > 0 ? "restock in " + hours + "h" : "restock in " + left.toMinutesPart() + "m";
    }

    private static String countdownText(MarketWindow window) {
        if (window.deals().isEmpty() && window.bundle().isEmpty()) {
            return "";
        }
        Duration left = window.remaining();
        if (left.isZero()) {
            return "offers changing";
        }
        long days = left.toDays();
        long hours = left.toHoursPart();
        long minutes = left.toMinutesPart();
        // ⚠ Coarse while it is far off, precise as it closes. "2d 4h" is what a player acts on three
        // days out; "14m" is what they act on at the end, and showing seconds throughout would be a
        // clock rather than a deadline.
        String remaining = days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        return "offers change in " + remaining;
    }

    /** ⚠ Package-private for the snapshot harness; nothing else should price an item itself. */
    static BigInteger shownPrice(Catalogue.Offering offering, MarketWindow window) {
        return window.dealFor(offering.id())
                .map(MarketWindow.Deal::priceWei)
                .orElseGet(offering::priceWei);
    }
}
