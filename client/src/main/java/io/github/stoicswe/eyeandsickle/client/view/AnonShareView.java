package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.SharesSnapshot;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * ANONSHARE — Anonymous Shares Inc.
 *
 * <h2>⚠ The feed's honesty is the first thing on the page</h2>
 *
 * A simulated price presented as a real one is the only harm this tab could cause outside the game:
 * somebody could act on it believing it was the market. So {@code feedIsLive} is rendered at the top,
 * always, and the offline feed says "simulated — not real market data" in its own words rather than
 * being described by the panel. There is no state in which this screen shows a price without saying
 * where it came from.
 *
 * <h2>⚠ Symbols are real; names are not</h2>
 *
 * A player looking up {@code AAPL} gets what {@code AAPL} tracks — that is the point of using the
 * real market. The name is aliased, because the game is not about real companies and a darknet
 * brokerage in a surveillance dystopia is not a place to put somebody's trademark.
 *
 * <h2>⚠ Slow on purpose</h2>
 *
 * The repaint is on the player's own refresh setting, not the Shadow Market's one-second clock. A
 * share price moves on a scale of minutes, and every refresh spends part of a free-tier allowance
 * the player is paying for out of their own quota.
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
        HBox masthead = Ui.row(UiTokens.SPACE_3, wordmark, tagline);
        masthead.setAlignment(Pos.BASELINE_LEFT);
        masthead.getStyleClass().add("es-market-masthead");

        // ⚠ The provenance line, above everything. See the class note.
        Label feed = Ui.micro("");
        feed.setWrapText(true);

        TextField search = new TextField();
        search.setPromptText("Search by symbol, name or sector");
        HBox.setHgrow(search, Priority.ALWAYS);
        Label session_ = Ui.micro("");
        HBox nav = Ui.row(UiTokens.SPACE_3, search, session_);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getStyleClass().add("es-market-nav");

        String[] symbol = {"AAPL"};
        VBox quote = new VBox(UiTokens.SPACE_2);
        quote.getStyleClass().add("es-market-card");
        VBox results = new VBox(1);
        VBox holdings = new VBox(UiTokens.SPACE_1);
        VBox portfolios = new VBox(UiTokens.SPACE_1);

        TextField amount = new TextField("1");
        amount.setPrefWidth(64);

        Runnable[] repaint = new Runnable[1];
        repaint[0] = () -> {
            if (session instanceof LocalGameSession local) {
                local.setShareQuery(search.getText() == null ? "" : search.getText().trim());
            }
            SharesSnapshot snapshot = session.shares(symbol[0]);
            if (snapshot == null) {
                // ⚠ Says why rather than rendering an empty shell. A blank brokerage reads as a
                // panel that failed to load.
                feed.setText("AnonShare runs against your own client. It is not available in this session.");
                return;
            }
            feed.setText(snapshot.feedIsLive()
                    ? "Live prices — " + snapshot.feedLabel() + ". $1 = 1 EC."
                    // ⚠ NO WARNING GLYPH. U+26A0 is in neither bundled face and GlyphCoverageTest
                    // rejects it — it caught this line. The emphasis comes from the word and from
                    // the colour, which is what §4.4 wants anyway: state that survives greyscale and
                    // reaches a screen reader.
                    : "NOT REAL PRICES — " + snapshot.feedLabel()
                            + ". Nothing here reflects a real market price. "
                            + "Add your own API key in Settings → AnonShare for live quotes. $1 = 1 EC.");
            feed.getStyleClass().removeAll("es-shmark-promised", "es-shmark-inhand");
            feed.getStyleClass().add(snapshot.feedIsLive() ? "es-shmark-inhand" : "es-shmark-promised");
            session_.setText(sessionText(snapshot));
            paintQuote(quote, session, snapshot, amount, result, repaint);
            paintResults(results, snapshot, symbol, repaint);
            paintHoldings(holdings, session, snapshot, result, repaint);
            paintPortfolios(portfolios, session, snapshot, symbol, result, repaint);
        };

        search.textProperty().addListener((o, was, now) -> repaint[0].run());
        repaint[0].run();
        session.onChange(s -> repaint[0].run());

        // ⚠ The PLAYER'S cadence, not a fixed one — and Pulse.every, because a price is data. A share
        // price moves on a scale of minutes and every refresh spends part of an allowance they are
        // paying for out of their own free-tier quota.
        AutoCloseable clock = Pulse.shared()
                .every(Math.max(1, refreshSeconds) * 1000.0d, () -> repaint[0].run());

        VBox left = new VBox(UiTokens.SPACE_3, quote, heading("YOUR HOLDINGS"), holdings);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setPrefWidth(0);
        VBox right = new VBox(UiTokens.SPACE_3, heading("LISTINGS"), results, heading("PORTFOLIOS"), portfolios);
        right.setMinWidth(300);
        right.getStyleClass().add("es-shmark-book");

        HBox floor = Ui.row(UiTokens.SPACE_3, left, right);
        floor.setAlignment(Pos.TOP_LEFT);

        page.getChildren().addAll(masthead, feed, nav, floor, result);

        VBox holder = new VBox(page);
        holder.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(holder);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-market-scroll");
        Views.releaseOnDetach(scroll, clock);
        return scroll;
    }

    private static Label heading(String text) {
        Label label = Ui.label(text);
        label.getStyleClass().addAll("es-panel-title", "es-market-section");
        return label;
    }

    /**
     * ⚠ Rendered in the PLAYER'S timezone. The session is New York's — that is a fact about the
     * exchange — but the instant is formatted locally, so a player in Berlin is told 15:30 and one in
     * Tokyo 23:30, and both are right.
     */
    private static String sessionText(SharesSnapshot snapshot) {
        Duration left = snapshot.untilPhaseChange();
        String when = LOCAL_CLOCK.format(snapshot.phaseChangesAt());
        return switch (snapshot.marketPhase()) {
            case "OPEN" -> "market open · closes " + when;
            case "PRE" -> "opens " + when + " (" + left.toHours() + "h " + left.toMinutesPart() + "m)";
            case "POST" -> "closed · opens " + when;
            default -> "closed · opens " + when;
        };
    }

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

        // ⚠ "Pays nothing" is stated, not omitted. A growth company paying no dividend is a real and
        // interesting property of the share, and a blank line reads as missing data.
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
        // ⚠ Disabled rather than hidden when the market is shut, with the reason beside it. A missing
        // button is indistinguishable from a broken panel.
        Label closed = Ui.micro(snapshot.tradable() ? "" : "market shut — you cannot trade now");
        closed.getStyleClass().add("es-shmark-promised");

        box.getChildren()
                .addAll(
                        Ui.row(UiTokens.SPACE_2, name, Ui.spacer(), ticker),
                        Ui.row(UiTokens.SPACE_3, price, change),
                        yield,
                        Ui.row(UiTokens.SPACE_2, Ui.micro("shares"), amount, buy, closed));
    }

    private static int parse(TextField field) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException malformed) {
            return 1;
        }
    }

    private static void paintResults(VBox box, SharesSnapshot snapshot, String[] symbol, Runnable[] repaint) {
        box.getChildren().clear();
        if (snapshot.results().isEmpty()) {
            box.getChildren().add(Ui.micro("Nothing matches that."));
            return;
        }
        for (var hit : snapshot.results()) {
            Label sym = Ui.micro(hit.symbol());
            sym.setMinWidth(52);
            Label named = Ui.micro(hit.displayName());
            named.setMinWidth(150);
            Label px = Ui.micro(Ethecoin.formatApprox(hit.priceWei(), 2));
            px.getStyleClass().add(hit.changePercent() >= 0 ? "es-shmark-up" : "es-shmark-down");
            HBox row = Ui.row(UiTokens.SPACE_2, sym, named, px);
            row.getStyleClass().add("es-shmark-listing");
            row.setOnMouseClicked(event -> {
                symbol[0] = hit.symbol();
                repaint[0].run();
            });
            row.setAccessibleText(hit.displayName() + ", " + hit.symbol() + ". Select to quote it.");
            box.getChildren().add(row);
        }
    }

    private static void paintHoldings(
            VBox box, GameSession session, SharesSnapshot snapshot, Label result, Runnable[] repaint) {
        box.getChildren().clear();
        if (snapshot.holdings().isEmpty()) {
            box.getChildren().add(Ui.micro("Nothing held. Buying happens while the market is open."));
            return;
        }
        for (var holding : snapshot.holdings()) {
            Label what = Ui.small(holding.shares() + " × " + holding.displayName());
            Label worth = Ui.micro(Ethecoin.formatApprox(holding.valueWei(), 2));
            // ⚠ Signed against THIS parcel's own cost, not an averaged book — two buys at different
            // prices are two positions with two different answers to "am I up on this".
            java.math.BigInteger gain = holding.gainWei();
            Label pnl = Ui.micro((gain.signum() >= 0 ? "+" : "−") + Ethecoin.formatApprox(gain.abs(), 2));
            pnl.getStyleClass().add(gain.signum() >= 0 ? "es-shmark-up" : "es-shmark-down");

            Button sell = new Button("Sell");
            sell.getStyleClass().add("es-shmark-cancel");
            sell.setDisable(!snapshot.tradable());
            sell.setOnAction(event -> {
                GameSession.Outcome outcome = session.sellShares(holding.holdingId(), holding.shares());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint[0].run();
            });
            HBox row = Ui.row(UiTokens.SPACE_3, what, worth, pnl, Ui.spacer(), sell);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        }
    }

    private static void paintPortfolios(
            VBox box,
            GameSession session,
            SharesSnapshot snapshot,
            String[] symbol,
            Label result,
            Runnable[] repaint) {
        box.getChildren().clear();
        for (var portfolio : snapshot.portfolios()) {
            Label name = Ui.small(portfolio.name());
            Label worth = Ui.micro(Ethecoin.formatApprox(portfolio.valueWei(), 2));
            Button watch = new Button("+ " + symbol[0]);
            watch.getStyleClass().add("es-shmark-cancel");
            watch.setOnAction(event -> {
                GameSession.Outcome outcome = session.watchSymbol(portfolio.portfolioId(), symbol[0], true);
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint[0].run();
            });
            box.getChildren().add(Ui.row(UiTokens.SPACE_2, name, worth, Ui.spacer(), watch));
            if (!portfolio.watching().isEmpty()) {
                box.getChildren().add(Ui.micro("   watching " + String.join(", ", portfolio.watching())));
            }
        }
        TextField named = new TextField();
        named.setPromptText("New portfolio");
        named.setOnAction(event -> {
            GameSession.Outcome outcome = session.createPortfolio(named.getText());
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            named.clear();
            repaint[0].run();
        });
        box.getChildren().add(named);
    }
}
