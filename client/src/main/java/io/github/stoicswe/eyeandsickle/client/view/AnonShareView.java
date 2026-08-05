package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.SharesSnapshot;
import java.math.BigInteger;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * ANONSHARE — Anonymous Shares Inc., laid out like a broker rather than like a list.
 *
 * <h2>Four sub-tabs, in the order a holder asks the questions</h2>
 *
 * <b>Overview</b> is the desk: account summary down the left, portfolio value and its chart across
 * the top, positions beneath, and the instrument you are quoting to one side — the arrangement every
 * broker has converged on. <b>Listings</b> is the browsable universe. <b>Watching</b> is the
 * player's own watchlists. <b>History</b> is what they have actually done.
 *
 * <p>⚠ <b>Only OVERVIEW carries live prices</b>, which is what makes the other three cheap enough to
 * keep painted while they are off screen. Listings deliberately draws none — see
 * {@code paintListings} — and History is recorded fact that no refresh can change.
 *
 * <p>⚠ None of the reference's styling is reproduced. §9 makes drop shadows, blur and gradients
 * build-blocking and {@code UiContractTest} fails on them; §2.1 bans a semantic colour system beyond
 * the tokens that already exist. The hierarchy is carried by type size and position, and the chart is
 * a plain stroked line rather than a gradient fill.
 *
 * <h2>⚠ The chart is RECORDED history, not a derived series</h2>
 *
 * Everything else in this game that draws a line is seekable noise. A real quote is not: nobody can
 * ask what a stock cost an hour ago without having written it down an hour ago. So the line here is
 * only as long as the character has been playing with holdings, and it is honest about that — an
 * empty chart says so rather than drawing a flat line through one point.
 *
 * <h2>⚠ Two refresh cadences</h2>
 *
 * Held and watched symbols refresh at the player's chosen interval; everything else once a day. A
 * free API tier is a few hundred calls and the catalogue is a couple of hundred symbols before the
 * player has discovered any, so refreshing all of them at the fast rate would spend the day's
 * allowance in minutes on prices nobody is watching.
 */
public final class AnonShareView {

    private AnonShareView() {}

    private static final DateTimeFormatter LOCAL_CLOCK =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    /**
     * @param session where prices and holdings come from
     * @param refreshSeconds the player's setting
     * @return the panel
     */
    public static Region create(GameSession session, int refreshSeconds) {
        VBox page = new VBox(UiTokens.SPACE_3);
        page.getStyleClass().add("es-anon");
        page.setMaxWidth(UiTokens.MARKET_CONTENT_WIDTH);

        Label result = new Label();
        result.setWrapText(true);

        Label wordmark = new Label("ANONSHARE");
        wordmark.getStyleClass().add("es-anon-wordmark");
        Label tagline = Ui.micro("Anonymous Shares Inc.");
        tagline.getStyleClass().add("es-market-tagline");
        Label feed = Ui.micro("");
        feed.setWrapText(true);
        HBox masthead = Ui.row(UiTokens.SPACE_3, wordmark, tagline);
        masthead.setAlignment(Pos.BASELINE_LEFT);
        masthead.getStyleClass().add("es-market-masthead");

        String[] symbol = {"AAPL"};
        // ⚠ int[] rather than a field: this view is static factories and the range has to survive
        // the repaint closure without becoming shared state between two open MARKET windows.
        int[] rangeDays = {1};

        // ── the account column ────────────────────────────────────────────────────────────────
        VBox account = new VBox(UiTokens.SPACE_2);
        account.getStyleClass().add("es-anon-account");
        account.setMinWidth(UiTokens.ANON_ACCOUNT_WIDTH);
        account.setPrefWidth(UiTokens.ANON_ACCOUNT_WIDTH);
        account.setMaxWidth(UiTokens.ANON_ACCOUNT_WIDTH);

        // ── the chart ─────────────────────────────────────────────────────────────────────────
        Label totalValue = new Label();
        totalValue.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-anon-total");
        Label totalChange = Ui.small("");
        Canvas chart = new Canvas(600, 200);
        Palette palette = new Palette();
        SharesSnapshot[] last = new SharesSnapshot[1];
        // ⚠ The countdown is on its OWN one-second clock, not on the price repaint. The refresh
        // interval can be ten minutes; a timer that only updated when the price did would sit on
        // "10m" for ten minutes and then jump, which is not a countdown.
        Label refreshIn = Ui.micro("");
        // ⚠ -1 means the pointer is off the chart. A hovered index of 0 is a real point.
        int[] hoverIndex = {-1};
        // ⚠ It FOLLOWS THE POINTER and is not a cell in the header row. It used to sit beside the
        // portfolio total, where it cost the row a variable amount of width for as long as it showed
        // — so the total, the change and the countdown all ellipsised together ("1705....",
        // "-455.39 EC on ...", "refreshin..."). Same rule as the balance delta in the top strip:
        // nothing transient may occupy space in a row of readouts.
        Label hoverReadout = Ui.micro("");
        Pane chartPlot = plot(chart, hoverReadout);

        HBox ranges = new HBox(UiTokens.SPACE_1);
        VBox positions = new VBox(1);
        VBox quote = new VBox(UiTokens.SPACE_2);
        quote.getStyleClass().add("es-market-card");

        TextField search = new TextField();
        search.setPromptText("Symbol, name or sector");
        HBox.setHgrow(search, Priority.ALWAYS);
        // ⚠ A TextField's default minimum is generous, and a grown field that cannot shrink pushes
        // the session readout off the row: "market open · closes 16:00" rendered as "market open ·".
        search.setMinWidth(120);
        Label sessionLabel = Ui.micro("");
        // ⚠ USE_PREF_SIZE, or the grown search field squeezes it and JavaFX ellipsises what is left:
        // "opens 09:30" became "opens 09". Same family as the Security Center's truncated verdict.
        sessionLabel.setMinWidth(Region.USE_PREF_SIZE);
        VBox results = new VBox(1);
        // ⚠ Two lists, not one. LISTINGS is the browsable universe with its own search; the overlay
        // is what the OVERVIEW's query matched, including symbols discovered from the provider.
        // Sharing one node would have the listing empty itself the moment somebody typed on the
        // other tab, on a tab they were not looking at.
        VBox listings = new VBox(1);
        VBox history = new VBox(1);
        VBox watching = new VBox(UiTokens.SPACE_2);

        // ⚠ Which watchlist is OPEN, and which of its symbols is charted. Blank means the list of
        // lists — one piece of state, so "am I drilled in" cannot disagree with "into what".
        String[] openList = {""};
        String[] watchPick = {""};

        // ── the stock-detail overlay ──────────────────────────────────────────────────────────
        //
        // ⚠ Selecting a share opens THIS and does not touch the chart. The chart is the account's
        // value over time; repointing it at whatever the player last clicked would answer a question
        // nobody asked and lose the one thing the panel is for.
        boolean[] detailOpen = {false};
        VBox detailCard = new VBox(UiTokens.SPACE_2);
        detailCard.getStyleClass().addAll("es-market-card", "es-anon-detail");
        detailCard.setMaxWidth(UiTokens.ANON_DETAIL_WIDTH);
        detailCard.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane scrim = new StackPane(detailCard);
        scrim.getStyleClass().add("es-scrim");
        scrim.setVisible(false);
        scrim.setManaged(false);

        TextField amount = new TextField("1");
        amount.setPrefWidth(60);
        TextField detailAmount = new TextField("1");
        detailAmount.setPrefWidth(60);

        // ⚠ The watchlist chart is built ONCE and re-parented, never rebuilt per repaint. A Canvas
        // rebuilt on the clock loses its width, its hover state and its layout listener every second
        // — the same defect the security mark's step counter had.
        Canvas watchChart = new Canvas(600, 200);
        Label watchReadout = Ui.micro("");
        int[] watchHover = {-1};
        Pane watchPlot = plot(watchChart, watchReadout);
        Label watchTitle = new Label();
        watchTitle.getStyleClass().addAll("es-panel-title", "es-market-hero-name");
        Label watchPrice = new Label();
        watchPrice.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-shmark-price");
        VBox watchColumn = new VBox(UiTokens.SPACE_2, watchTitle, watchPrice, watchPlot);
        HBox.setHgrow(watchColumn, Priority.ALWAYS);
        watchColumn.setPrefWidth(0);
        // ⚠ Zero minimum, or the Canvas's own width becomes the column's floor and the symbol column
        // beside it is pushed off the panel. Same trap as the account row's quote card.
        watchColumn.setMinWidth(0);
        watchColumn.layoutBoundsProperty().addListener((obs, was, now) ->
                watchChart.setWidth(Math.max(160, now.getWidth())));

        Runnable[] relist = new Runnable[1];
        Runnable[] repaint = new Runnable[1];
        repaint[0] = () -> {
            if (session instanceof LocalGameSession local) {
                local.setShareQuery(search.getText() == null ? "" : search.getText().trim());
            }
            SharesSnapshot snapshot = session.shares(symbol[0]);
            if (snapshot == null) {
                feed.setText("AnonShare runs against your own client. It is not available in this session.");
                return;
            }
            last[0] = snapshot;
            feed.setText(snapshot.feedIsLive()
                    ? "Live — " + snapshot.feedLabel() + ". $1 = 1 EC."
                    : "NOT REAL PRICES — " + snapshot.feedLabel()
                            + ". Add your own API key in Settings → AnonShare for live quotes. $1 = 1 EC.");
            feed.getStyleClass().removeAll("es-shmark-promised", "es-shmark-inhand");
            feed.getStyleClass().add(snapshot.feedIsLive() ? "es-shmark-inhand" : "es-shmark-promised");
            sessionLabel.setText(sessionText(snapshot));

            totalValue.setText(Ethecoin.formatApprox(snapshot.portfolioValueWei(), 2));
            BigInteger gain = snapshot.portfolioValueWei().subtract(snapshot.portfolioCostWei());
            totalChange.setText((gain.signum() >= 0 ? "+" : "-")
                    + Ethecoin.formatApprox(gain.abs(), 2) + " on cost");
            totalChange.getStyleClass().removeAll("es-shmark-up", "es-shmark-down");
            totalChange.getStyleClass().add(gain.signum() >= 0 ? "es-shmark-up" : "es-shmark-down");

            paintAccount(account, snapshot);
            drawValue(chart, snapshot.valueHistory(), rangeDays[0], snapshot.asOf(), palette, hoverIndex[0]);
            refreshIn.setText(refreshText(snapshot));
            paintPositions(positions, session, snapshot, symbol, result, repaint);
            paintQuote(quote, session, snapshot, amount, result, repaint);
            paintResults(results, snapshot, symbol, repaint, search, session, result, detailOpen);
            paintHistory(history, snapshot);
            paintWatching(watching, session, snapshot, openList, watchPick, result, repaint,
                    watchColumn, watchTitle, watchPrice);
            if (relist[0] != null) {
                relist[0].run();
            }
            drawValue(watchChart, seriesFor(snapshot, watchPick[0]), 0, snapshot.asOf(), palette, watchHover[0]);
            scrim.setVisible(detailOpen[0]);
            if (detailOpen[0]) {
                paintDetail(detailCard, session, snapshot, detailAmount, result, repaint, detailOpen);
            }
        };

        // ⚠ Hover on the watchlist chart reads its OWN series, not the account's. Two charts, two
        // hover indices — sharing one would move the marker on the panel the player is not looking at.
        watchChart.setOnMouseMoved(event -> {
            List<SharesSnapshot.Point> points = last[0] == null ? List.of() : seriesFor(last[0], watchPick[0]);
            if (points.size() < 2 || watchChart.getWidth() <= 0) {
                return;
            }
            double plotWidth = Math.max(1, watchChart.getWidth() - UiTokens.ANON_AXIS_GUTTER);
            int index = (int) Math.round(
                    (event.getX() - UiTokens.ANON_AXIS_GUTTER) / plotWidth * (points.size() - 1));
            watchHover[0] = Math.max(0, Math.min(points.size() - 1, index));
            SharesSnapshot.Point point = points.get(watchHover[0]);
            place(watchReadout, watchPlot, event.getX(), event.getY(),
                    Ethecoin.formatApprox(point.wei(), 2) + "   " + LOCAL_CLOCK.format(point.at()));
            drawValue(watchChart, points, 0, last[0].asOf(), palette, watchHover[0]);
        });
        watchChart.setOnMouseExited(event -> {
            watchHover[0] = -1;
            watchReadout.setVisible(false);
            if (last[0] != null) {
                drawValue(watchChart, seriesFor(last[0], watchPick[0]), 0, last[0].asOf(), palette, -1);
            }
        });
        // ⚠ Clicking the scrim closes; clicking the card does not. Without the target check the
        // overlay dismisses itself the moment anybody reaches for the Buy button inside it.
        scrim.setOnMouseClicked(event -> {
            if (event.getTarget() == scrim) {
                detailOpen[0] = false;
                repaint[0].run();
            }
        });

        for (int[] option : new int[][] {{1, 0}, {7, 0}, {30, 0}, {0, 0}}) {
            int days = option[0];
            Button button = new Button(days == 0 ? "ALL" : days == 1 ? "1D" : days + "D");
            button.getStyleClass().add("es-shmark-interval");
            button.setOnAction(event -> {
                rangeDays[0] = days;
                repaint[0].run();
            });
            ranges.getChildren().add(button);
        }

        search.textProperty().addListener((o, was, now) -> {
            repaint[0].run();
            // ⚠ Only when nothing local matched. A lookup is a call against the player's own
            // allowance, and firing on every keystroke would spend a free tier's whole day on the
            // way to typing four letters. SymbolLookup also refuses anything that does not look like
            // a ticker and remembers what it has already asked, including the misses.
            String typed = now == null ? "" : now.trim();
            if (!typed.isEmpty()
                    && io.github.stoicswe.eyeandsickle.engine.stocks.Tickers.search(typed).isEmpty()) {
                session.discoverSymbol(typed);
            }
        });
        repaint[0].run();
        session.onChange(s -> repaint[0].run());

        // ⚠ The PLAYER'S cadence. A share price moves on a scale of minutes and every refresh spends
        // part of an allowance they pay for; this is data, so Pulse.every rather than animate.
        AutoCloseable clock =
                Pulse.shared().every(Math.max(1, refreshSeconds) * 1000.0d, () -> repaint[0].run());

        // ⚠ THE CANVAS IS REDRAWN ON LAYOUT, or its colours are whatever they were before CSS. The
        // panel paints once during construction, when nothing is in a Scene and no stylesheet has
        // been adopted — the probes resolve to Modena's defaults and the line comes out grey. Only
        // the chart is redrawn here: re-running the repaint would rebuild the positions list, dirty
        // layout and fire this again. Drawing on a Canvas dirties nothing.
        // ⚠ Hover reads the value AT A RECORDED POINT, never an interpolation between two. The
        // series is what was actually sampled; drawing a number for an instant nothing was recorded
        // at would be inventing a price, on the one panel whose subject is what a price was.
        chart.setOnMouseMoved(event -> {
            List<SharesSnapshot.Point> points = visible(last[0], rangeDays[0]);
            if (points.size() < 2 || chart.getWidth() <= 0) {
                return;
            }
            // ⚠ Measured from the PLOT's left edge, not the canvas's. Ignoring the axis gutter makes
            // the marker sit a fixed distance right of the pointer, which reads as the chart being
            // out by a few samples.
            double plotWidth = Math.max(1, chart.getWidth() - UiTokens.ANON_AXIS_GUTTER);
            int index = (int) Math.round(
                    (event.getX() - UiTokens.ANON_AXIS_GUTTER) / plotWidth * (points.size() - 1));
            hoverIndex[0] = Math.max(0, Math.min(points.size() - 1, index));
            SharesSnapshot.Point point = points.get(hoverIndex[0]);
            place(hoverReadout, chartPlot, event.getX(), event.getY(),
                    Ethecoin.formatApprox(point.wei(), 2) + "   " + LOCAL_CLOCK.format(point.at()));
            drawValue(chart, last[0].valueHistory(), rangeDays[0], last[0].asOf(), palette, hoverIndex[0]);
        });
        chart.setOnMouseExited(event -> {
            hoverIndex[0] = -1;
            hoverReadout.setVisible(false);
            if (last[0] != null) {
                drawValue(chart, last[0].valueHistory(), rangeDays[0], last[0].asOf(), palette, -1);
            }
        });

        // ⚠ Its own Pulse, at one second. The price repaint runs at the player's interval — which can
        // be ten minutes — so a countdown driven by it would hold one number and then jump.
        AutoCloseable countdown = Pulse.shared().every(1000, () -> {
            if (last[0] != null) {
                refreshIn.setText(refreshText(session.shares(symbol[0])));
            }
        });

        // ⚠ The countdown moves DOWN beside the range buttons. Three variable-width readouts on one
        // row is what made them fight for it; the total and its change are the pair that belong
        // together, and the countdown is about the feed rather than about the money.
        HBox rangeRow = Ui.row(UiTokens.SPACE_3, ranges, Ui.spacer(), refreshIn);
        rangeRow.setAlignment(Pos.CENTER_LEFT);
        VBox chartColumn = new VBox(UiTokens.SPACE_2,
                Ui.row(UiTokens.SPACE_3, totalValue, totalChange),
                rangeRow, chartPlot);
        chartColumn.getChildren().addAll(palette.nodes());
        chartColumn.layoutBoundsProperty().addListener((obs, was, now) -> {
            chart.setWidth(Math.max(160, now.getWidth() - UiTokens.SPACE_6));
            if (last[0] != null) {
                drawValue(chart, last[0].valueHistory(), rangeDays[0], last[0].asOf(), palette, hoverIndex[0]);
            }
        });
        HBox.setHgrow(chartColumn, Priority.ALWAYS);
        chartColumn.setPrefWidth(0);

        VBox main = new VBox(UiTokens.SPACE_3, chartColumn, heading("POSITIONS"), positions);
        HBox.setHgrow(main, Priority.ALWAYS);
        main.setPrefWidth(0);
        // ⚠ MINIMUM zero, and without it the quote card is CLIPPED. A Canvas is not resizable, so it
        // contributes its whole current width to the column's computed minimum — and an HBox
        // satisfies minimums before it distributes anything, so the row demanded more than the
        // content column has and the last child ran off the edge. The chart follows the column's
        // width from a layout listener, so nothing is lost by letting the column shrink.
        main.setMinWidth(0);

        VBox side = new VBox(UiTokens.SPACE_3, quote);
        side.getStyleClass().add("es-shmark-book");
        side.setMinWidth(UiTokens.ANON_SIDE_WIDTH);
        side.setMaxWidth(UiTokens.ANON_SIDE_WIDTH);

        HBox floor = Ui.row(UiTokens.SPACE_3, account, main, side);
        floor.setAlignment(Pos.TOP_LEFT);

        // ── the search overlay ────────────────────────────────────────────────────────────────
        //
        // ⚠ It OVERLAYS the account and the chart rather than sitting in the flow. Results in the
        // column would push the portfolio total and its chart down the moment somebody typed a
        // letter, so the two things a holder came to look at would move every keystroke.
        //
        // ⚠ It STEPS, on Pulse.every. §5 permits no easing and UiContractTest rations AnimationTimer
        // to two files by name — and `every` rather than `animate` because a decorative subscription
        // never fires under Reduce motion, which would leave the overlay unable to open at all. The
        // clock runs in both modes; only the ramp is conditional.
        // ⚠ The results SCROLL inside the overlay. A query matching thirty symbols would otherwise
        // run past the fixed height and be clipped with nothing saying there was more.
        ScrollPane resultScroll = new ScrollPane(results);
        resultScroll.setFitToWidth(true);
        resultScroll.getStyleClass().add("es-market-scroll");
        VBox.setVgrow(resultScroll, Priority.ALWAYS);
        VBox overlay = new VBox(UiTokens.SPACE_1, heading("MATCHES"), resultScroll);
        overlay.getStyleClass().add("es-anon-overlay");
        overlay.setMaxHeight(UiTokens.ANON_OVERLAY_HEIGHT);
        overlay.setPrefHeight(UiTokens.ANON_OVERLAY_HEIGHT);
        overlay.setVisible(false);
        // ⚠ MANAGED, and unmanaged was a real bug. In a StackPane children are layered, so a managed
        // child costs its siblings no space — which is the whole reason this is a StackPane. Setting
        // it unmanaged meant the parent never resized it, so `prefHeight` was a request to a layout
        // pass that would never run: the node had no size, the background had nothing to paint, and
        // the results rendered as bare text straight over the account column and the chart. Same
        // family as SyncBanner's trap, from the other side.
        overlay.setManaged(true);

        javafx.scene.layout.StackPane host = new javafx.scene.layout.StackPane(floor, overlay);
        javafx.scene.layout.StackPane.setAlignment(overlay, Pos.TOP_LEFT);
        // ⚠ Clipped, or the closed overlay paints above the search bar and over the nav row. It
        // rests translated a full height upward, which is off this panel's top edge by construction.
        javafx.scene.shape.Rectangle overlayClip = new javafx.scene.shape.Rectangle();
        overlayClip.widthProperty().bind(host.widthProperty());
        overlayClip.heightProperty().bind(host.heightProperty());
        host.setClip(overlayClip);

        double[] shown = {0};
        AutoCloseable slide = Pulse.shared().every(UiTokens.REVEAL_MS / UiTokens.REVEAL_STEPS, () -> {
            boolean wanted = search.getText() != null && !search.getText().trim().isEmpty();
            double target = wanted ? 1 : 0;
            if (shown[0] == target) {
                return;
            }
            double step = Pulse.shared().reducedMotion() ? 1 : 1.0d / UiTokens.REVEAL_STEPS;
            shown[0] = target > shown[0] ? Math.min(target, shown[0] + step) : Math.max(target, shown[0] - step);
            boolean open = shown[0] > 0;
            overlay.setVisible(open);
            overlay.setTranslateY(-UiTokens.ANON_OVERLAY_HEIGHT * (1 - shown[0]));
        });

        HBox nav = Ui.row(UiTokens.SPACE_3, search, sessionLabel);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getStyleClass().add("es-market-nav");

        // ── the four sub-tabs ─────────────────────────────────────────────────────────────────
        //
        // ⚠ They answer four different questions and they are ordered the way a holder asks them:
        // what am I worth (OVERVIEW), what else is there (LISTINGS), what am I following (WATCHING),
        // what have I done (HISTORY). Only the first carries live prices — which is what makes the
        // other three cheap enough to keep painted while they are off screen.
        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane();
        tabs.getStyleClass().add("es-market-tabs");
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox overview = new VBox(UiTokens.SPACE_3, nav, host);
        overview.getStyleClass().add("es-anon-tab");
        javafx.scene.control.Tab overviewTab =
                new javafx.scene.control.Tab("Overview", Views.scrollable(overview));

        TextField listingSearch = new TextField();
        listingSearch.setPromptText("Symbol, name or sector");
        Label listingCount = Ui.micro("");
        // ⚠ LISTINGS is repainted on its own query, not on the price clock. It draws no prices, so
        // there is nothing on it a refresh could change — and five hundred rows rebuilt every
        // repaint is work with no observer while the player is on another tab.
        relist[0] = () -> paintListings(
                listings, listingCount, listingSearch.getText(), symbol, repaint,
                session, last[0], result, detailOpen);
        // ⚠ Its own query, and it never touches the session's. Two search boxes writing one piece of
        // state means whichever repainted last decides what the OTHER tab is showing. This one
        // filters what is already known; the overview's is the one that spends a lookup.
        listingSearch.textProperty().addListener((o, was, now) -> {
            relist[0].run();
            String typed = now == null ? "" : now.trim();
            if (!typed.isEmpty()
                    && io.github.stoicswe.eyeandsickle.engine.stocks.Tickers.search(typed).isEmpty()) {
                session.discoverSymbol(typed);
            }
        });
        relist[0].run();
        VBox listingPage = new VBox(UiTokens.SPACE_2,
                Ui.row(UiTokens.SPACE_3, listingSearch, listingCount), listings);
        listingPage.getStyleClass().add("es-anon-tab");
        HBox.setHgrow(listingSearch, Priority.ALWAYS);
        javafx.scene.control.Tab listingTab =
                new javafx.scene.control.Tab("Listings", Views.scrollable(listingPage));

        VBox watchPage = new VBox(UiTokens.SPACE_2, watching);
        watchPage.getStyleClass().add("es-anon-tab");
        javafx.scene.control.Tab watchTab =
                new javafx.scene.control.Tab("Watching", Views.scrollable(watchPage));

        VBox historyPage = new VBox(UiTokens.SPACE_2, history);
        historyPage.getStyleClass().add("es-anon-tab");
        javafx.scene.control.Tab historyTab =
                new javafx.scene.control.Tab("History", Views.scrollable(historyPage));

        tabs.getTabs().addAll(overviewTab, listingTab, watchTab, historyTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        page.getChildren().addAll(masthead, feed, tabs, result);
        Views.releaseOnDetach(page, slide);
        Views.releaseOnDetach(page, clock);
        Views.releaseOnDetach(page, countdown);

        // ⚠ No outer ScrollPane. Each tab scrolls its own body — an outer one would scroll the tab
        // strip itself off the top, and LISTINGS is five hundred rows.
        VBox holder = new VBox(page);
        holder.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(page, Priority.ALWAYS);

        // ⚠ The overlay covers the WHOLE panel, tabs included, which is what makes it a popup rather
        // than a fifth tab. It is the last child of a StackPane, so it paints above everything and
        // swallows the clicks underneath — which is the point: a modal that can be clicked through
        // is a decoration.
        StackPane root = new StackPane(holder, scrim);
        scrim.setManaged(true);
        return root;
    }

    /** Whatever series the panel has for one symbol, empty when it is not tracked. */
    private static List<SharesSnapshot.Point> seriesFor(SharesSnapshot snapshot, String symbol) {
        if (snapshot == null || symbol == null || symbol.isBlank()) {
            return List.of();
        }
        return snapshot.tracked().stream()
                .filter(each -> each.symbol().equals(symbol))
                .findFirst()
                .map(SharesSnapshot.Tracked::history)
                .orElse(List.of());
    }

    /**
     * A canvas with a readout that follows the pointer.
     *
     * <p>⚠ A plain {@code Pane}, because it does not resize an UNMANAGED child and a {@code Canvas}
     * is not resizable at all — so the canvas keeps whatever width the layout listener gave it and
     * the readout keeps whatever {@code autosize} measured. In a {@code StackPane} both would be
     * stretched to fill and the readout would become a full-width band.
     */
    private static Pane plot(Canvas canvas, Label readout) {
        readout.getStyleClass().add("es-anon-hover");
        readout.setVisible(false);
        readout.setManaged(false);
        Pane holder = new Pane(canvas, readout);
        holder.setMinHeight(canvas.getHeight());
        holder.setPrefHeight(canvas.getHeight());
        return holder;
    }

    /**
     * Puts the readout beside the pointer, inside the plot.
     *
     * <p>⚠ {@code autosize()} is required and easy to miss: an unmanaged node is never resized by
     * its parent, so a Label that has never been sized is zero wide and the box paints nothing.
     * {@code applyCss()} first, or the padding and font it is measured against are not yet its own.
     *
     * <p>⚠ Clamped to the plot on both axes. Near the right edge an unclamped box hangs off the
     * panel; near the top it is placed above the pointer at a negative y and disappears.
     */
    private static void place(Label readout, Pane holder, double x, double y, String text) {
        readout.setText(text);
        readout.applyCss();
        readout.autosize();
        double w = readout.getWidth();
        double h = readout.getHeight();
        readout.setLayoutX(Math.max(0, Math.min(Math.max(0, holder.getWidth() - w), x + UiTokens.ANON_HOVER_OFFSET)));
        readout.setLayoutY(Math.max(0, Math.min(Math.max(0, holder.getHeight() - h), y - h - UiTokens.ANON_HOVER_OFFSET)));
        readout.setVisible(true);
    }

    private static Label heading(String text) {
        Label label = Ui.label(text);
        label.getStyleClass().addAll("es-panel-title", "es-market-section");
        return label;
    }

    private static String sessionText(SharesSnapshot snapshot) {
        Duration left = snapshot.untilPhaseChange();
        String when = LOCAL_CLOCK.format(snapshot.phaseChangesAt());
        return switch (snapshot.marketPhase()) {
            case "OPEN" -> "market open · closes " + when;
            case "PRE" -> "opens " + when + " (" + left.toHours() + "h " + left.toMinutesPart() + "m)";
            default -> "closed · opens " + when;
        };
    }

    // ── the account column ────────────────────────────────────────────────────────────────────

    private static void paintAccount(VBox box, SharesSnapshot snapshot) {
        box.getChildren().clear();
        box.getChildren().add(heading("ACCOUNT"));
        BigInteger gain = snapshot.portfolioValueWei().subtract(snapshot.portfolioCostWei());
        box.getChildren()
                .addAll(
                        figure("Holdings", snapshot.portfolioValueWei(), null),
                        figure("Cost basis", snapshot.portfolioCostWei(), null),
                        // ⚠ Signed and coloured. Unrealised is the one figure a holder looks for, and
                        // an unsigned number here makes them do the subtraction themselves.
                        figure("Unrealised", gain, gain.signum() >= 0 ? "es-shmark-up" : "es-shmark-down"),
                        figure("Free ethecoin", snapshot.cashWei(), null),
                        figure("Dividends paid", snapshot.dividendsWei(), null));
    }

    private static Region figure(String label, BigInteger wei, String styleClass) {
        Label name = Ui.micro(label);
        Label value = new Label(Ethecoin.formatApprox(wei.abs(), 2));
        value.getStyleClass().addAll("es-numeric", "es-ethecoin");
        if (styleClass != null) {
            value.getStyleClass().add(styleClass);
            value.setText((wei.signum() >= 0 ? "+" : "-") + Ethecoin.formatApprox(wei.abs(), 2));
        }
        // ⚠ The exact figure on hover, the short one on screen — the top strip's licensed exception,
        // not a second one. A held amount may be abbreviated only where the exact one is a hover away.
        value.setTooltip(new javafx.scene.control.Tooltip(Ethecoin.format(wei)));
        VBox cell = new VBox(name, value);
        return cell;
    }

    // ── the value chart ───────────────────────────────────────────────────────────────────────

    /**
     * A stroked line through the recorded value history.
     *
     * <p>⚠ Says so when there is nothing to draw. One point is not a line, and a flat stroke through
     * a single sample claims a history the character does not have — a player would read it as
     * "nothing has happened" rather than "nothing has been recorded yet".
     */
    /** The points inside the chosen range, which is what both the drawing and the hover index over. */
    private static List<SharesSnapshot.Point> visible(SharesSnapshot snapshot, int days) {
        if (snapshot == null) {
            return List.of();
        }
        return days <= 0
                ? snapshot.valueHistory()
                : snapshot.valueHistory().stream()
                        .filter(point -> Duration.between(point.at(), snapshot.asOf()).toDays() < days)
                        .toList();
    }

    /** How long until the next fetch, or nothing when the feed does not fetch. */
    private static String refreshText(SharesSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return snapshot.untilRefresh()
                .map(left -> {
                    long seconds = left.toSeconds();
                    // ⚠ Says "refreshing" rather than "0s". A quote whose window has expired is
                    // being fetched on a background thread, and a frozen zero reads as a stall.
                    if (seconds <= 0) {
                        return "refreshing...";
                    }
                    return seconds >= 3600
                            ? "next price in " + left.toHours() + "h " + left.toMinutesPart() + "m"
                            : seconds >= 60
                                    ? "next price in " + left.toMinutesPart() + "m " + left.toSecondsPart() + "s"
                                    : "next price in " + seconds + "s";
                })
                // ⚠ Empty for a derived feed. A simulated price is a continuous function of the
                // clock and is never refreshed; a countdown there would count down to nothing.
                .orElse("");
    }

    private static void drawValue(
            Canvas canvas,
            List<SharesSnapshot.Point> history,
            int days,
            java.time.Instant now,
            Palette palette,
            int hover) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.clearRect(0, 0, w, h);

        // ⚠ The PLOT is inset from the canvas, and the axes live in the gutters. Drawing labels over
        // the plot would put text on top of the line at exactly the values a reader is trying to
        // compare — which is the one place a chart must stay clean.
        double left = UiTokens.ANON_AXIS_GUTTER;
        double bottom = UiTokens.ANON_AXIS_BASELINE;
        double plotW = Math.max(1, w - left);
        double plotH = Math.max(1, h - bottom);

        List<SharesSnapshot.Point> points = days <= 0
                ? history
                : history.stream()
                        .filter(point -> Duration.between(point.at(), now).toDays() < days)
                        .toList();
        g.setFont(palette.font());
        if (points.size() < 2) {
            // ⚠ Says so rather than drawing empty axes. A grid with no line reads as a chart that
            // failed; this reads as a chart with nothing to show yet, which is the truth — the
            // series is recorded, so a new character genuinely has no history.
            g.setFill(palette.grid());
            g.fillText("no history recorded yet", left + 4, plotH / 2);
            return;
        }

        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (SharesSnapshot.Point point : points) {
            double v = point.wei().doubleValue();
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (hi <= lo) {
            hi = lo + 1;
        }
        double pad = (hi - lo) * 0.10;
        lo -= pad;
        hi += pad;

        // ⚠ Six characters is the budget the gutter affords at ANON_AXIS_TEXT_SIZE, so the whole
        // part decides how much of the fraction there is room for. A rule that only looked at the
        // span would run a four-digit portfolio off the left edge.
        double spanEc = (hi - lo) / 1e18;
        long whole = (long) Math.min(1e18, Math.max(1, Math.abs(hi) / 1e18));
        int wanted = spanEc >= 100 ? 0 : spanEc >= 10 ? 1 : 2;
        int decimals = Math.max(0, Math.min(wanted, 6 - String.valueOf(whole).length()));

        // ── the axes ──────────────────────────────────────────────────────────────────────────
        //
        // ⚠ Values on the LEFT and times along the BOTTOM, which is the arrangement every chart a
        // player has read uses. Four bands, because the point of a gridline is to be counted against
        // and more than about five stops being countable — the same reasoning the cycle grid follows.
        g.setStroke(palette.grid());
        g.setFill(palette.axis());
        g.setLineWidth(1);
        for (int i = 0; i <= 4; i++) {
            double y = Math.round(plotH * i / 4.0) + 0.5;
            g.strokeLine(left, y, w, y);
            // Top row is the high, bottom the low.
            double value = hi - (hi - lo) * i / 4.0;
            // ⚠ THROUGH BigDecimal, never `(long) value`. These are WEI — a portfolio of 2742 EC is
            // 2.7e21 — and a long tops out at 9.22 EC, so the cast saturates at Long.MAX_VALUE and
            // every label on the axis rendered as "9 EC". Exactly the overflow the currency's own
            // notes warn about, and five identical labels is what it looks like from outside.
            //
            // ⚠ The PRECISION follows the span, and a fixed one is wrong in both directions. At zero
            // decimals a watchlist chart of a share moving between 140.3 and 141.1 labelled every
            // gridline "141 EC" — five identical labels again, from the other cause. At two, a
            // portfolio in the thousands does not fit the gutter.
            //
            // ⚠ The UNIT is on the top label only. It is the axis's annotation rather than a figure
            // the player holds, and repeating it on all five costs three characters a row in a
            // gutter that is already the constraint deciding the precision. The exact amounts are
            // everywhere else on this panel.
            java.math.BigInteger labelWei = new java.math.BigDecimal(value).toBigInteger();
            String text = new java.math.BigDecimal(labelWei)
                    .movePointLeft(18)
                    .setScale(decimals, java.math.RoundingMode.HALF_UP)
                    .toPlainString();
            g.fillText(
                    i == 0 ? text + " EC" : text,
                    0,
                    Math.min(plotH, y + UiTokens.ANON_AXIS_TEXT_RISE));
        }
        // ⚠ Three time labels, not one per point. A tick per sample would be 240 overlapping strings;
        // start, middle and end is what a reader actually uses to place the line in time.
        for (int i = 0; i <= 2; i++) {
            SharesSnapshot.Point point = points.get(i * (points.size() - 1) / 2);
            double x = left + plotW * i / 2.0;
            String label = LOCAL_CLOCK.format(point.at());
            // ⚠ The last label is pulled back inside the plot, or it renders half off the canvas.
            // Derived from the text metric rather than a fixed nudge, so it survives a change to
            // ANON_AXIS_TEXT_SIZE — it did not, and the last label drifted when the size went up.
            double advance = label.length() * UiTokens.ANON_AXIS_TEXT_SIZE * 0.62d;
            double textX = i == 2 ? x - advance : i == 0 ? x : x - advance / 2;
            g.fillText(label, textX, h - 2);
        }

        boolean up = points.getLast().wei().compareTo(points.getFirst().wei()) >= 0;
        g.setStroke(up ? palette.up() : palette.down());
        g.setLineWidth(1.5);
        g.beginPath();
        for (int i = 0; i < points.size(); i++) {
            double x = left + plotW * i / (double) (points.size() - 1);
            double y = plotH - (points.get(i).wei().doubleValue() - lo) / (hi - lo) * plotH;
            if (i == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();

        // ── the hover crosshair ───────────────────────────────────────────────────────────────
        //
        // ⚠ Drawn at the POINT, not at the pointer. Snapping the marker to the nearest recorded
        // sample is what stops the readout and the dot disagreeing — a marker that followed the
        // mouse would sit between two points while the number named one of them.
        if (hover >= 0 && hover < points.size()) {
            double x = left + plotW * hover / (double) (points.size() - 1);
            double y = plotH - (points.get(hover).wei().doubleValue() - lo) / (hi - lo) * plotH;
            g.setStroke(palette.grid());
            g.setLineWidth(1);
            g.strokeLine(Math.round(x) + 0.5, 0, Math.round(x) + 0.5, plotH);
            g.setFill(up ? palette.up() : palette.down());
            g.fillOval(x - 3, y - 3, 6, 6);
        }
    }

    /**
     * Chart colours, read off invisible probes IN THE LIVE SCENE.
     *
     * <p>⚠ A {@code Canvas} cannot resolve a looked-up colour and §10 criterion 2 forbids the hex
     * literals that would replace one. ⚠ The probes must be in the real scene: a throwaway
     * {@code Scene} carries no stylesheet, so every probe returns Modena's default and the whole
     * chart renders in one colour with no error anywhere. That exact bug shipped on the Shadow
     * Market's candles.
     */
    private static final class Palette {
        private final Label up = swatch("es-shmark-up");
        private final Label down = swatch("es-shmark-down");
        private final Label grid = swatch("es-shmark-grid");
        // ⚠ NOT the gridline colour. -es-rule is a hairline token and ContrastTest exempts it from
        // the 3:1 floor precisely because a border held to a text threshold becomes a stripe — so
        // labels drawn in it are legal and barely legible. Lines in `grid`, numbers in `axis`.
        private final Label axis = swatch("es-shmark-axis");

        private static Label swatch(String styleClass) {
            Label label = new Label();
            label.getStyleClass().add(styleClass);
            label.setVisible(false);
            label.setManaged(false);
            return label;
        }

        List<Label> nodes() {
            return List.of(up, down, grid, axis);
        }

        Color up() {
            return read(up);
        }

        Color down() {
            return read(down);
        }

        Color grid() {
            return read(grid);
        }

        Color axis() {
            return read(axis);
        }

        /**
         * ⚠ Read off a styled probe, not constructed. A Canvas draws with whatever font it is given,
         * and a default one is the host's — different metrics on every platform, in a client whose
         * whole look is a character grid.
         */
        javafx.scene.text.Font font() {
            grid.applyCss();
            return javafx.scene.text.Font.font(
                    grid.getFont().getFamily(), UiTokens.ANON_AXIS_TEXT_SIZE);
        }

        private static Color read(Label label) {
            label.applyCss();
            return label.getTextFill() instanceof Color colour ? colour : Color.GRAY;
        }
    }

    // ── positions ─────────────────────────────────────────────────────────────────────────────

    /**
     * One row per SYMBOL, not one per purchase.
     *
     * <p>⚠ Two buys of the same company used to render as two rows, which made the panel read as a
     * ledger of transactions rather than as a portfolio. The lots still exist underneath — the cost
     * basis is per-lot and always was — and selling from here takes the oldest first, which is what a
     * broker does when you do not name one.
     */
    private static void paintPositions(
            VBox box,
            GameSession session,
            SharesSnapshot snapshot,
            String[] symbol,
            Label result,
            Runnable[] repaint) {
        box.getChildren().clear();
        if (snapshot.positions().isEmpty()) {
            box.getChildren().add(Ui.micro("Nothing held. Buying happens while the market is open."));
            return;
        }
        HBox header = Ui.row(UiTokens.SPACE_2,
                column(Ui.micro("SYMBOL"), 62),
                column(Ui.micro("QTY"), 44),
                column(Ui.micro("LAST"), 82),
                column(Ui.micro("VALUE"), 92),
                column(Ui.micro("P&L"), 92));
        box.getChildren().add(header);

        for (var position : snapshot.positions()) {
            Label sym = Ui.small(position.symbol());
            Label qty = Ui.small(String.valueOf(position.shares()));
            Label lastPrice = Ui.small(Ethecoin.formatApprox(position.priceWei(), 2));
            lastPrice.getStyleClass().add(position.changePercent() >= 0 ? "es-shmark-up" : "es-shmark-down");
            Label value = Ui.small(Ethecoin.formatApprox(position.valueWei(), 2));
            BigInteger gain = position.gainWei();
            Label pnl = Ui.small((gain.signum() >= 0 ? "+" : "-") + Ethecoin.formatApprox(gain.abs(), 2));
            pnl.getStyleClass().add(gain.signum() >= 0 ? "es-shmark-up" : "es-shmark-down");

            Button sell = new Button("Sell");
            sell.getStyleClass().add("es-shmark-cancel");
            sell.setDisable(!snapshot.tradable());
            sell.setOnAction(event -> {
                GameSession.Outcome outcome = session.sellPosition(position.symbol(), position.shares());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint[0].run();
            });

            HBox row = Ui.row(UiTokens.SPACE_2,
                    column(sym, 62), column(qty, 44), column(lastPrice, 82),
                    column(value, 92), column(pnl, 92), Ui.spacer(), sell);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("es-shmark-listing");
            row.setOnMouseClicked(event -> {
                symbol[0] = position.symbol();
                repaint[0].run();
            });
            row.setAccessibleText(position.shares() + " " + position.displayName() + ", worth "
                    + Ethecoin.format(position.valueWei()) + ". Select to quote it.");
            box.getChildren().add(row);
        }
    }

    private static Region column(Label label, double width) {
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    // ── watchlists ────────────────────────────────────────────────────────────────────────────

    /**
     * The watchlists, and one drilled into.
     *
     * <h2>Two levels, one piece of state</h2>
     *
     * Blank {@code openList} is the collection; anything else is that list open, with a chart of
     * whichever symbol is picked and the rest of the list beside it. ⚠ A back button, never the tab
     * strip: the tabs are four different subjects and using one of them as "back" would take the
     * player somewhere they did not ask to go.
     *
     * <p>⚠ What is on a watchlist decides how often it is refreshed AND whether it has a recorded
     * series at all — a watched symbol is sampled exactly like a held one. So this list is not
     * decoration; it is where the player's API allowance goes, and the panel says so.
     */
    private static void paintWatching(
            VBox box,
            GameSession session,
            SharesSnapshot snapshot,
            String[] openList,
            String[] watchPick,
            Label result,
            Runnable[] repaint,
            VBox watchColumn,
            Label watchTitle,
            Label watchPrice) {
        box.getChildren().clear();

        var open = snapshot.portfolios().stream()
                .filter(portfolio -> portfolio.portfolioId().equals(openList[0]))
                .findFirst();
        if (open.isEmpty()) {
            // ⚠ Reset when the open list has gone. A deleted watchlist would otherwise leave the
            // panel drilled into nothing, showing a back button and an empty chart forever.
            openList[0] = "";
            paintWatchlistIndex(box, session, snapshot, openList, watchPick, result, repaint);
            return;
        }

        var portfolio = open.get();
        if (!portfolio.watching().contains(watchPick[0])) {
            watchPick[0] = portfolio.watching().isEmpty() ? "" : portfolio.watching().getFirst();
        }

        Button back = new Button("< Watchlists");
        back.getStyleClass().add("es-shmark-cancel");
        back.setOnAction(event -> {
            openList[0] = "";
            repaint[0].run();
        });
        Label named = new Label(portfolio.name());
        named.getStyleClass().addAll("es-panel-title", "es-market-section");
        HBox head = Ui.row(UiTokens.SPACE_3, back, named);
        head.setAlignment(Pos.CENTER_LEFT);

        var picked = snapshot.tracked().stream()
                .filter(each -> each.symbol().equals(watchPick[0]))
                .findFirst();
        watchTitle.setText(picked.map(SharesSnapshot.Tracked::displayName).orElse("Nothing on this list"));
        watchPrice.setText(picked.map(each -> Ethecoin.formatApprox(each.priceWei(), 2)).orElse(""));
        watchPrice.getStyleClass().removeAll("es-shmark-up", "es-shmark-down");
        picked.ifPresent(each ->
                watchPrice.getStyleClass().add(each.changePercent() >= 0 ? "es-shmark-up" : "es-shmark-down"));

        VBox members = new VBox(1);
        members.setMinWidth(UiTokens.ANON_WATCH_WIDTH);
        members.setMaxWidth(UiTokens.ANON_WATCH_WIDTH);
        members.getStyleClass().add("es-shmark-book");
        members.getChildren().add(Ui.micro("ON THIS LIST"));
        if (portfolio.watching().isEmpty()) {
            members.getChildren()
                    .add(Ui.micro("Nothing yet. Right-click a share in LISTINGS to add one."));
        }
        for (String each : portfolio.watching()) {
            var quote = snapshot.tracked().stream()
                    .filter(tracked -> tracked.symbol().equals(each))
                    .findFirst();
            Label sym = Ui.small(each);
            sym.setMinWidth(58);
            sym.setPrefWidth(58);
            Label price = Ui.small(quote.map(q -> Ethecoin.formatApprox(q.priceWei(), 2)).orElse("--"));
            price.getStyleClass()
                    .add(quote.map(q -> q.changePercent() >= 0).orElse(true) ? "es-shmark-up" : "es-shmark-down");
            HBox row = Ui.row(UiTokens.SPACE_2, sym, price);
            row.getStyleClass().add("es-shmark-listing");
            if (each.equals(watchPick[0])) {
                row.getStyleClass().add("es-shmark-mine");
            }
            row.setOnMouseClicked(event -> {
                watchPick[0] = each;
                repaint[0].run();
            });
            Button drop = new Button("x");
            drop.getStyleClass().add("es-shmark-cancel");
            drop.setOnAction(event -> {
                GameSession.Outcome outcome = session.watchSymbol(portfolio.portfolioId(), each, false);
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint[0].run();
            });
            row.getChildren().addAll(Ui.spacer(), drop);
            members.getChildren().add(row);
        }

        HBox floor = Ui.row(UiTokens.SPACE_3, watchColumn, members);
        floor.setAlignment(Pos.TOP_LEFT);
        box.getChildren().addAll(head, floor);
    }

    private static void paintWatchlistIndex(
            VBox box,
            GameSession session,
            SharesSnapshot snapshot,
            String[] openList,
            String[] watchPick,
            Label result,
            Runnable[] repaint) {
        Label why = Ui.micro("Held and watched symbols refresh at your chosen rate and are the only "
                + "ones whose price history is recorded. Everything else updates once a day, so a "
                + "watchlist is where your API allowance goes.");
        // ⚠ Wrapped, or JavaFX ellipsises it and the half that explains the cost is the half that
        // gets cut. A caption that says why a control exists is not decoration.
        why.setWrapText(true);
        why.setMaxWidth(UiTokens.MARKET_CONTENT_WIDTH);
        box.getChildren().add(why);
        if (snapshot.portfolios().isEmpty()) {
            box.getChildren().add(Ui.micro("No watchlists yet. Name one below, or right-click a share."));
        }
        for (var portfolio : snapshot.portfolios()) {
            Label named = Ui.small(portfolio.name());
            named.setMinWidth(180);
            named.setPrefWidth(180);
            int count = portfolio.watching().size();
            Label size = Ui.micro(count + (count == 1 ? " symbol" : " symbols"));
            size.setMinWidth(90);
            size.setPrefWidth(90);
            Button remove = new Button("Delete");
            remove.getStyleClass().add("es-shmark-cancel");
            remove.setOnAction(event -> {
                GameSession.Outcome outcome = session.deletePortfolio(portfolio.portfolioId());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint[0].run();
            });
            HBox row = Ui.row(UiTokens.SPACE_2, named, size, Ui.spacer(), remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().addAll("es-shmark-listing", "es-anon-watchlist");
            row.setAccessibleText(portfolio.name() + ", " + count + " symbols. Select to open it.");
            row.setOnMouseClicked(event -> {
                openList[0] = portfolio.portfolioId();
                watchPick[0] = portfolio.watching().isEmpty() ? "" : portfolio.watching().getFirst();
                repaint[0].run();
            });
            box.getChildren().add(row);
        }
        TextField named = new TextField();
        named.setPromptText("New watchlist");
        named.setOnAction(event -> {
            GameSession.Outcome outcome = session.createPortfolio(named.getText());
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            named.clear();
            repaint[0].run();
        });
        box.getChildren().add(named);
    }

    // ── the quote and the ticket ──────────────────────────────────────────────────────────────

    private static void paintQuote(
            VBox box,
            GameSession session,
            SharesSnapshot snapshot,
            TextField amount,
            Label result,
            Runnable[] repaint) {
        box.getChildren().clear();

        Label name = new Label(snapshot.displayName());
        name.getStyleClass().addAll("es-panel-title", "es-market-hero-name");
        Label ticker = Ui.micro(snapshot.symbol() + "  ·  " + snapshot.sector());

        Label price = new Label(Ethecoin.formatApprox(snapshot.priceWei(), 2));
        price.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-shmark-price");
        price.setTooltip(new javafx.scene.control.Tooltip(Ethecoin.format(snapshot.priceWei())));
        Label change = Ui.small(String.format(Locale.ROOT, "%+.2f%%", snapshot.changePercent()));
        change.getStyleClass().add(snapshot.changePercent() >= 0 ? "es-shmark-up" : "es-shmark-down");

        Label yield = Ui.micro(snapshot.annualYieldBp() > 0
                ? String.format(Locale.ROOT, "pays %.2f%% a year, quarterly", snapshot.annualYieldBp() / 100.0d)
                : "pays no dividend");

        Button buy = new Button("Buy");
        buy.getStyleClass().add("es-market-buy");
        buy.setDisable(!snapshot.tradable());
        buy.setOnAction(event -> {
            GameSession.Outcome outcome = session.buyShares(snapshot.symbol(), parse(amount));
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            repaint[0].run();
        });
        Label closed = Ui.micro(snapshot.tradable() ? "" : "market shut");
        closed.getStyleClass().add("es-shmark-promised");

        box.getChildren()
                .addAll(name, ticker, Ui.row(UiTokens.SPACE_3, price, change), yield,
                        Ui.row(UiTokens.SPACE_2, Ui.micro("qty"), amount, buy), closed);
    }

    private static int parse(TextField field) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException malformed) {
            return 1;
        }
    }

    private static void paintResults(
            VBox box,
            SharesSnapshot snapshot,
            String[] symbol,
            Runnable[] repaint,
            TextField search,
            GameSession session,
            Label result,
            boolean[] detailOpen) {
        box.getChildren().clear();
        if (snapshot.results().isEmpty()) {
            // ⚠ Says the lookup is happening. "Nothing matches that" alone would be wrong for the
            // second or so a provider search takes, and a player would conclude the symbol does not
            // exist rather than that the answer has not arrived.
            box.getChildren()
                    .add(Ui.micro("Nothing known by that name. If it is a real ticker, looking it up..."));
            return;
        }
        for (var hit : snapshot.results()) {
            Label sym = Ui.micro(hit.symbol());
            sym.setMinWidth(58);
            Label named = Ui.micro(hit.displayName());
            named.setMinWidth(190);
            Label price = Ui.micro(Ethecoin.formatApprox(hit.priceWei(), 2));
            price.getStyleClass().add(hit.changePercent() >= 0 ? "es-shmark-up" : "es-shmark-down");
            HBox row = Ui.row(UiTokens.SPACE_2, sym, named, price);
            row.getStyleClass().add("es-shmark-listing");
            row.setOnMouseClicked(event -> {
                if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                symbol[0] = hit.symbol();
                detailOpen[0] = true;
                // ⚠ Clearing the box is what closes the overlay — the slide watches the query, so
                // there is one piece of state deciding whether it is open and nothing that can
                // disagree with it.
                search.clear();
                repaint[0].run();
            });
            menu(row, session, snapshot, hit.symbol(), symbol, result, repaint, detailOpen);
            box.getChildren().add(row);
        }
    }

    // ── the right-click menu ──────────────────────────────────────────────────────────────────

    /**
     * Buy, or file under a watchlist, without leaving the list.
     *
     * <p>⚠ Anchored to the WINDOW, never to the row. This panel repaints on a clock and a menu shown
     * against a node that a repaint has since detached throws
     * {@code "The owner node needs to be associated with a window"} on the FX thread — the exact
     * failure the network map's node menu records. Screen coordinates make it identical on screen.
     *
     * <p>⚠ <b>New watchlist…</b> is offered whether or not any exist. A player with none would
     * otherwise be shown a dead "Add to watchlist" submenu and no way to fix it, which reads as the
     * feature being broken rather than as an empty collection.
     */
    private static void menu(
            HBox row,
            GameSession session,
            SharesSnapshot snapshot,
            String sym,
            String[] symbol,
            Label result,
            Runnable[] repaint,
            boolean[] detailOpen) {
        row.setOnContextMenuRequested(event -> {
            ContextMenu menu = new ContextMenu();

            MenuItem details = new MenuItem("Details");
            details.setOnAction(e -> {
                symbol[0] = sym;
                detailOpen[0] = true;
                repaint[0].run();
            });

            MenuItem buy = new MenuItem("Buy 1 " + sym);
            // ⚠ Disabled rather than absent when the market is shut. An entry that vanishes reads as
            // the game not offering it at all; one that is greyed says the door is closed for now.
            buy.setDisable(!snapshot.tradable());
            buy.setOnAction(e -> {
                GameSession.Outcome outcome = session.buyShares(sym, 1);
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint[0].run();
            });

            Menu add = new Menu("Add to watchlist");
            for (var portfolio : snapshot.portfolios()) {
                MenuItem entry = new MenuItem(portfolio.name());
                entry.setDisable(portfolio.watching().contains(sym));
                entry.setOnAction(e -> {
                    GameSession.Outcome outcome = session.watchSymbol(portfolio.portfolioId(), sym, true);
                    result.setText(outcome.message());
                    Views.styleByOutcome(result, outcome);
                    repaint[0].run();
                });
                add.getItems().add(entry);
            }
            add.setDisable(snapshot.portfolios().isEmpty());

            MenuItem create = new MenuItem("New watchlist...");
            create.setOnAction(e -> {
                prompt(row, "Name the watchlist").ifPresent(name -> {
                    GameSession.Outcome made = session.createPortfolio(name);
                    result.setText(made.message());
                    Views.styleByOutcome(result, made);
                    if (made.status() == GameSession.Outcome.OK) {
                        // ⚠ Re-read the snapshot rather than trusting the one this menu was built
                        // from: the list did not exist when the menu opened, so its id is not in
                        // there. Reaching for the stale copy files the symbol nowhere, silently.
                        session.shares(symbol[0]).portfolios().stream()
                                .filter(each -> each.name().equals(name))
                                .findFirst()
                                .ifPresent(each -> session.watchSymbol(each.portfolioId(), sym, true));
                    }
                    repaint[0].run();
                });
            });

            menu.getItems().addAll(details, buy, new javafx.scene.control.SeparatorMenuItem(), add, create);
            menu.show(row.getScene().getWindow(), event.getScreenX(), event.getScreenY());
        });
    }

    /**
     * A one-field prompt, themed.
     *
     * <p>⚠ A {@code Dialog} builds its own {@code Scene}, and a scene inherits no stylesheet — so
     * without this it paints Modena white in the middle of a dark deck. Same family as the unstyled
     * {@code ScrollPane} viewport.
     */
    private static java.util.Optional<String> prompt(javafx.scene.Node anchor, String question) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(null);
        dialog.setTitle(question);
        dialog.setContentText(question);
        if (anchor.getScene() != null) {
            dialog.initOwner(anchor.getScene().getWindow());
            dialog.getDialogPane().getStylesheets().addAll(anchor.getScene().getStylesheets());
            dialog.getDialogPane().getStyleClass().add("es-panel");
        }
        return dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty());
    }

    // ── the stock-detail overlay ──────────────────────────────────────────────────────────────

    /**
     * What one share is, on top of whatever the player was looking at.
     *
     * <p>⚠ It reads the snapshot's TOP-LEVEL quote, which is the symbol the panel last asked for —
     * so opening the overlay is one fetch of one symbol rather than a fetch of everything on screen.
     * That is also why it cannot be built from a search result: a result carries a price and nothing
     * else, and the sector, the yield and the holding are the part worth opening a panel for.
     */
    private static void paintDetail(
            VBox card,
            GameSession session,
            SharesSnapshot snapshot,
            TextField amount,
            Label result,
            Runnable[] repaint,
            boolean[] open) {
        card.getChildren().clear();

        Label name = new Label(snapshot.displayName());
        name.getStyleClass().addAll("es-panel-title", "es-market-hero-name");
        name.setWrapText(true);
        Button close = new Button("Close");
        close.getStyleClass().add("es-shmark-cancel");
        close.setOnAction(event -> {
            open[0] = false;
            repaint[0].run();
        });
        HBox head = Ui.row(UiTokens.SPACE_3, name, Ui.spacer(), close);
        head.setAlignment(Pos.CENTER_LEFT);

        Label price = new Label(Ethecoin.formatApprox(snapshot.priceWei(), 2));
        price.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-shmark-price");
        price.setTooltip(new javafx.scene.control.Tooltip(Ethecoin.format(snapshot.priceWei())));
        Label change = Ui.small(String.format(Locale.ROOT, "%+.2f%%", snapshot.changePercent()));
        change.getStyleClass().add(snapshot.changePercent() >= 0 ? "es-shmark-up" : "es-shmark-down");

        int held = snapshot.positions().stream()
                .filter(position -> position.symbol().equals(snapshot.symbol()))
                .mapToInt(SharesSnapshot.Position::shares)
                .sum();

        Button buy = new Button("Buy");
        buy.getStyleClass().add("es-market-buy");
        buy.setDisable(!snapshot.tradable());
        buy.setOnAction(event -> {
            GameSession.Outcome outcome = session.buyShares(snapshot.symbol(), parse(amount));
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            repaint[0].run();
        });

        card.getChildren()
                .addAll(head,
                        Ui.micro(snapshot.symbol() + "  ·  " + snapshot.sector()),
                        Ui.row(UiTokens.SPACE_3, price, change),
                        Ui.micro(snapshot.annualYieldBp() > 0
                                ? String.format(Locale.ROOT,
                                        "pays %.2f%% a year, quarterly", snapshot.annualYieldBp() / 100.0d)
                                : "pays no dividend"),
                        Ui.micro(held > 0
                                ? "you hold " + held + (held == 1 ? " share" : " shares")
                                : "you hold none of this"),
                        Ui.micro(snapshot.feedIsLive()
                                ? "priced by " + snapshot.feedLabel()
                                : "NOT A REAL PRICE — " + snapshot.feedLabel()),
                        Ui.row(UiTokens.SPACE_2, Ui.micro("qty"), amount, buy));
        if (!snapshot.tradable()) {
            Label shut = Ui.micro("market shut");
            shut.getStyleClass().add("es-shmark-promised");
            card.getChildren().add(shut);
        }
    }

    /** How many rows LISTINGS will draw. ⚠ A cap, and it must SAY when it has cut the list. */
    private static final int LISTINGS_LIMIT = 500;

    /**
     * The browsable universe.
     *
     * <p>⚠ It draws NO PRICES, and that is the two-cadence design showing through rather than a gap.
     * Held and watched symbols refresh at the player's rate and everything else once a day, so most
     * rows here have never been fetched — a price column would print a zero or yesterday's number
     * for the majority of the list, on the one panel whose subject is what a price is. Selecting a
     * row quotes it on OVERVIEW, which fetches, which is where a real number belongs.
     *
     * <p>⚠ Filtered against {@code known()}, so a symbol the provider search discovered appears here
     * afterwards — that is how the universe grows past what shipped.
     */
    private static void paintListings(
            VBox box,
            Label count,
            String query,
            String[] symbol,
            Runnable[] repaint,
            GameSession session,
            SharesSnapshot snapshot,
            Label result,
            boolean[] detailOpen) {
        box.getChildren().clear();
        var matches = io.github.stoicswe.eyeandsickle.engine.stocks.Tickers.search(query);
        count.setText(matches.size() > LISTINGS_LIMIT
                ? matches.size() + " known — showing the first " + LISTINGS_LIMIT
                : matches.size() + (matches.size() == 1 ? " listing" : " listings"));
        if (matches.isEmpty()) {
            box.getChildren()
                    .add(Ui.micro("Nothing known by that name. If it is a real ticker, looking it up..."));
            return;
        }
        for (var listing : matches.stream().limit(LISTINGS_LIMIT).toList()) {
            // ⚠ column(), not setMinWidth. A minimum alone leaves the label's COMPUTED preferred
            // width in charge, so one long invented name widens its own cell and pushes the sector
            // out of line for that row only — the table looks like it has lost a column.
            HBox row = Ui.row(UiTokens.SPACE_2,
                    column(Ui.small(listing.symbol()), 72),
                    column(Ui.small(listing.displayName()), 300),
                    Ui.micro(listing.sector()));
            // ⚠ Its own class as well as the shared one. Positions, search hits and listings all
            // carry .es-shmark-listing, so a lookup for one finds whichever the scene graph reaches
            // first — the render harness clicked a POSITIONS row believing it was a listing.
            row.getStyleClass().addAll("es-shmark-listing", "es-anon-listing");
            row.setAccessibleText(
                    listing.displayName() + ", " + listing.symbol() + ". Select for details.");
            row.setOnMouseClicked(event -> {
                if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                symbol[0] = listing.symbol();
                detailOpen[0] = true;
                repaint[0].run();
            });
            if (snapshot != null) {
                menu(row, session, snapshot, listing.symbol(), symbol, result, repaint, detailOpen);
            }
            box.getChildren().add(row);
        }
    }

    // ── history ───────────────────────────────────────────────────────────────────────────────

    private static final DateTimeFormatter TRADE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /**
     * Every buy and sell, newest first.
     *
     * <p>⚠ The commission has its OWN column rather than being folded into the price. They are two
     * different charges and only one of them is the market's — a history that merged them could not
     * answer why a round trip at an unchanged price lost money, which is the single most important
     * thing this tab has to be able to explain.
     *
     * <p>⚠ Realised gain is a SELL's figure. A buy realises nothing, so the cell is a dash rather
     * than a zero: zero means broke even and there is a real difference.
     */
    private static void paintHistory(VBox box, SharesSnapshot snapshot) {
        box.getChildren().clear();
        if (snapshot.trades().isEmpty()) {
            box.getChildren().add(Ui.micro("Nothing traded yet."));
            return;
        }
        box.getChildren()
                .add(Ui.row(UiTokens.SPACE_2,
                        column(Ui.micro("WHEN"), 128),
                        column(Ui.micro("SIDE"), 44),
                        column(Ui.micro("SYMBOL"), 62),
                        column(Ui.micro("QTY"), 44),
                        column(Ui.micro("PRICE"), 82),
                        column(Ui.micro("VALUE"), 92),
                        column(Ui.micro("FEE"), 72),
                        column(Ui.micro("REALISED"), 92)));

        for (var trade : snapshot.trades()) {
            // ⚠ The SIDE is not coloured. Up/down here mean gain and loss everywhere else in this
            // client, and a red BUY beside a green SELL says buying was the mistake — which is not a
            // claim a transaction log gets to make. Colour is spent on REALISED, where it is a fact.
            Label side = Ui.small(trade.buy() ? "BUY" : "SELL");
            Label realised = Ui.small(trade.buy()
                    ? "—"
                    : (trade.realisedWei().signum() >= 0 ? "+" : "-")
                            + Ethecoin.formatApprox(trade.realisedWei().abs(), 2));
            if (!trade.buy()) {
                realised.getStyleClass().add(trade.realisedWei().signum() >= 0 ? "es-shmark-up" : "es-shmark-down");
            }
            HBox row = Ui.row(UiTokens.SPACE_2,
                    column(Ui.small(TRADE_STAMP.format(trade.at())), 128),
                    column(side, 44),
                    column(Ui.small(trade.symbol()), 62),
                    column(Ui.small(String.valueOf(trade.shares())), 44),
                    column(Ui.small(Ethecoin.formatApprox(trade.pricePerShareWei(), 2)), 82),
                    column(Ui.small(Ethecoin.formatApprox(trade.considerationWei(), 2)), 92),
                    column(Ui.small(Ethecoin.formatApprox(trade.commissionWei(), 2)), 72),
                    column(realised, 92));
            row.getStyleClass().add("es-shmark-listing");
            row.setAccessibleText((trade.buy() ? "Bought " : "Sold ") + trade.shares() + " "
                    + trade.displayName() + " at " + Ethecoin.format(trade.pricePerShareWei())
                    + " on " + TRADE_STAMP.format(trade.at()));
            box.getChildren().add(row);
        }
    }
}
