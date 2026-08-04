package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.ShadowCandle;
import io.github.stoicswe.eyeandsickle.protocol.game.ShadowLevel;
import io.github.stoicswe.eyeandsickle.protocol.game.ShadowOrder;
import io.github.stoicswe.eyeandsickle.protocol.game.ShadowSnapshot;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * SHADOW MARKET — the darknet secondary market, as a trading desk.
 *
 * <h2>What this is for</h2>
 *
 * The storefront sells you things at a price somebody else set. This is where the things players
 * <em>already have</em> change hands, so the price is whatever two people will agree to — and in
 * solo the other side is simulated, with counterparties who have reputations and compete on price.
 *
 * <h2>⚠ The cheapest offer is the riskiest, and the panel must never hide that</h2>
 *
 * A trusted seller asks a premium for the certainty; a shady one undercuts. Because the book sorts
 * by price, the top of the asks is systematically the worst-rated counterparty — so every row
 * carries its standing beside its price. A book that showed prices alone would render the one
 * reading of it that is wrong.
 *
 * <h2>⚠ Two clocks, and mixing them up freezes the chart</h2>
 *
 * Prices move because wall time passed, and nothing about the save changes as they do — so the
 * repaint is on {@link Pulse#every}, exactly as the file manager's transfer bar is. An
 * {@code onChange} listener would leave the candles exactly where they were when the panel opened.
 * The player's own orders change on {@code onChange} as well, because those are save state.
 */
public final class ShadowMarketView {

    private ShadowMarketView() {}

    /**
     * How often the chart repaints.
     *
     * <p>⚠ Not the print interval. The market prints every 2–8 seconds and the panel repaints faster
     * than that, so a new print appears promptly rather than up to a second late. Tying the two
     * together would make the chart's smoothness a property of the simulation's granularity.
     */
    private static final double REPAINT_MS = 1000;

    private static final int CANDLES = 60;

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * @param session where the market comes from
     * @return the trading panel
     */
    public static Region create(GameSession session) {
        List<String> listings = session.shadowListings();

        VBox page = new VBox(UiTokens.SPACE_3);
        page.getStyleClass().add("es-shmark");
        page.setMaxWidth(UiTokens.MARKET_CONTENT_WIDTH);

        Label result = new Label();
        result.setWrapText(true);

        if (listings.isEmpty()) {
            // ⚠ Says WHY. An empty trading screen reads as a panel that failed to load, and online
            // this is the honest answer until the federation transport lands (W-9).
            page.getChildren()
                    .addAll(masthead(), Views.wrapped("No listings. On a server this market carries real "
                            + "trades from across the federation, and that transport is not built yet."));
            return centred(page);
        }

        Palette palette = new Palette();
        // ⚠ The last snapshot, kept so the CHART can be redrawn without re-fetching the market. The
        // canvas has to be redrawn on layout (see below) and re-running the whole repaint there
        // would rebuild the book's children, which dirties layout, which fires the listener again —
        // a layout loop.
        ShadowSnapshot[] last = new ShadowSnapshot[1];
        String[] listing = {listings.getFirst()};
        String[] interval = {"M5"};

        // ⚠ A MenuButton with a Menu per category, not a flat ComboBox. A trading screen picks ONE
        // instrument out of a catalogue that is already organised — defence, recon, stealth — and a
        // flat list makes the player learn the order rather than read it. The client already drills
        // down this way in the terminal's command menu, so this is the same idiom rather than a new
        // one. Categories come off `Offering.category()`, i.e. the first tag, so the picker and the
        // storefront's search cannot file one item in two places.
        javafx.scene.control.MenuButton picker = new javafx.scene.control.MenuButton();
        picker.getStyleClass().add("es-shmark-picker");

        Label price = new Label();
        price.getStyleClass().addAll("es-numeric", "es-ethecoin", "es-shmark-price");
        Label change = Ui.small("");
        Label spread = Ui.micro("");

        HBox ticker = Ui.row(UiTokens.SPACE_3, picker, price, change, Ui.spacer(), spread);
        ticker.setAlignment(Pos.CENTER_LEFT);
        ticker.getStyleClass().add("es-shmark-ticker");

        HBox intervals = new HBox(UiTokens.SPACE_1);
        Canvas chart = new Canvas(600, 260);
        VBox book = new VBox(1);
        VBox tape = new VBox(1);
        VBox orders = new VBox(UiTokens.SPACE_1);

        TextField limit = new TextField();
        limit.setPromptText("Limit price");
        TextField quantity = new TextField("1");
        quantity.setPromptText("Qty");
        quantity.setPrefWidth(56);

        Runnable[] repaint = new Runnable[1];
        repaint[0] = () -> {
            ShadowSnapshot snapshot = session.shadowMarket(listing[0], interval[0], CANDLES);
            price.setText(Ethecoin.formatApprox(snapshot.mid(), 4));
            price.setTooltip(new javafx.scene.control.Tooltip(Ethecoin.format(snapshot.mid())));
            change.setText(String.format(Locale.ROOT, "%+.2f%%", snapshot.changePercent()));
            change.getStyleClass().removeAll("es-shmark-up", "es-shmark-down");
            change.getStyleClass().add(snapshot.changePercent() >= 0 ? "es-shmark-up" : "es-shmark-down");
            spread.setText("spread " + Ethecoin.formatApprox(snapshot.spread(), 4)
                    + "   ·   you hold " + snapshot.holdings());
            last[0] = snapshot;
            drawCandles(chart, snapshot.candles(), palette);
            paintBook(book, snapshot);
            paintTape(tape, snapshot);
            paintOrders(orders, session, snapshot.openOrders(), result, repaint);
        };

        for (String id : new String[] {"M1", "M5", "M15", "H1"}) {
            Button button = new Button(id.substring(1).toLowerCase(Locale.ROOT) + (id.startsWith("H") ? "h" : "m"));
            button.getStyleClass().add("es-shmark-interval");
            button.setOnAction(event -> {
                interval[0] = id;
                repaint[0].run();
            });
            intervals.getChildren().add(button);
        }
        buildPicker(picker, session, listings, listing, repaint);

        // ⚠ YOUR ORDERS lives UNDER THE CHART, in the same column, not at the foot of the page.
        // Below everything it sat past the fold on any normal window — so the one part of this
        // screen that is about the player's own money was the part they could not see, while the
        // chart column carried a block of empty space the same size. It is also where it belongs:
        // an order is a line on this instrument, and the chart is what the player is reading to
        // decide whether to pull it.
        VBox chartColumn = new VBox(UiTokens.SPACE_2, intervals, chart, heading("YOUR ORDERS"), orders);
        chartColumn.getChildren().addAll(palette.nodes());
        // ⚠ THE CANVAS MUST BE REDRAWN ON LAYOUT, or its colours are whatever they were before CSS.
        //
        // The panel paints once during construction, when nothing is in a Scene yet and no
        // stylesheet has been adopted — so the palette probes resolve to Modena's default and every
        // candle comes out the same colour, up and down indistinguishable, with no error anywhere.
        // In the running game the next Pulse tick hides it a second later; in a synchronous render
        // it is permanent, which is how it was found. Layout runs after CSS, so this is the earliest
        // point the colours are real.
        //
        // ⚠ Only the CHART is redrawn here. Re-running the full repaint would rebuild the book and
        // the tape, dirtying layout and firing this listener again — drawing on a Canvas dirties
        // nothing, which is what makes this safe.
        chartColumn.layoutBoundsProperty().addListener((obs, was, now) -> {
            chart.setWidth(Math.max(120, now.getWidth() - UiTokens.SPACE_6 * 2));
            if (last[0] != null) {
                drawCandles(chart, last[0].candles(), palette);
            }
        });
        chartColumn.getStyleClass().add("es-shmark-chart");
        HBox.setHgrow(chartColumn, Priority.ALWAYS);
        chartColumn.setPrefWidth(0);

        VBox bookColumn = new VBox(UiTokens.SPACE_2, heading("ORDER BOOK"), book, heading("RECENT TRADES"), tape);
        bookColumn.getStyleClass().add("es-shmark-book");
        bookColumn.setMinWidth(272);

        HBox floor = Ui.row(UiTokens.SPACE_3, chartColumn, bookColumn);
        floor.setAlignment(Pos.TOP_LEFT);

        page.getChildren().addAll(masthead(), ticker, floor, result);

        repaint[0].run();
        session.onChange(s -> repaint[0].run());
        // ⚠ Pulse.every — DATA. Prices move because wall time passed and the save does not change as
        // they do, so onChange would leave the chart exactly where it was when the panel opened.
        AutoCloseable clock = Pulse.shared().every(REPAINT_MS, repaint[0]);
        Region scrolled = centred(page);

        // ⚠ The order form is an OVERLAY on the right edge, not a column in the row. In the row it
        // took width from the chart at every window size and was on screen whether or not the player
        // was trading; as a drawer it costs nothing until it is wanted. Same rule the download dock
        // and the balance delta already follow — nothing transient may occupy layout space.
        javafx.scene.layout.StackPane host = new javafx.scene.layout.StackPane(scrolled);
        host.getChildren().add(drawer(session, listing, limit, quantity, result, repaint));
        // ⚠ Clipped to itself, or the closed drawer paints outside the panel. It rests translated a
        // full form-width to the right, which is off this panel's edge by construction.
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(host.widthProperty());
        clip.heightProperty().bind(host.heightProperty());
        host.setClip(clip);

        Views.releaseOnDetach(host, clock);
        return host;
    }

    private static Region centred(VBox page) {
        VBox holder = new VBox(page);
        holder.setAlignment(Pos.TOP_CENTER);
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(holder);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-market-scroll");
        return scroll;
    }

    private static Region masthead() {
        Label wordmark = new Label("SHADOW MARKET");
        wordmark.getStyleClass().add("es-shmark-wordmark");
        Label tagline = Ui.micro("nobody here is bonded");
        tagline.getStyleClass().add("es-market-tagline");
        HBox row = Ui.row(UiTokens.SPACE_3, wordmark, tagline);
        row.setAlignment(Pos.BASELINE_LEFT);
        row.getStyleClass().add("es-market-masthead");
        return row;
    }

    private static Label heading(String text) {
        Label label = Ui.label(text);
        label.getStyleClass().addAll("es-panel-title", "es-market-section");
        return label;
    }

    // ── the chart ─────────────────────────────────────────────────────────────────────────────

    /**
     * Candles, drawn on a {@link Canvas}.
     *
     * <h2>⚠ A Canvas rather than sixty Regions per repaint</h2>
     *
     * §7.3's own guidance: start with nodes, move to a Canvas when profiling says so. Here it does
     * not need profiling — the chart repaints every second and a candle is three shapes, so nodes
     * would mean rebuilding ~180 of them a second for one panel. {@code NetCanvas} already
     * establishes the precedent in this client.
     *
     * <h2>⚠ Colours are READ FROM THE STYLESHEET, never literals</h2>
     *
     * §10 criterion 2 makes every colour a looked-up token, and a Canvas cannot look one up — so the
     * two candle colours are taken off styled probe nodes rather than hard-coded, which keeps them
     * following the palette. A literal green would be the {@code DiskLamp} trap: invisible on one
     * palette, glaring on another.
     */
    private static void drawCandles(Canvas canvas, List<ShadowCandle> candles, Palette palette) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.clearRect(0, 0, w, h);
        if (candles.isEmpty()) {
            return;
        }
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (ShadowCandle candle : candles) {
            lo = Math.min(lo, candle.low().doubleValue());
            hi = Math.max(hi, candle.high().doubleValue());
        }
        if (hi <= lo) {
            hi = lo + 1;
        }
        // A little headroom so the extremes are not drawn on the frame.
        double pad = (hi - lo) * 0.08;
        lo -= pad;
        hi += pad;

        Color up = palette.up();
        Color down = palette.down();
        Color rule = palette.grid();

        g.setStroke(rule);
        g.setLineWidth(1);
        for (int i = 1; i < 4; i++) {
            double y = Math.round(h * i / 4.0) + 0.5;
            g.strokeLine(0, y, w, y);
        }

        double slot = w / candles.size();
        double body = Math.max(1, slot * 0.62);
        for (int i = 0; i < candles.size(); i++) {
            ShadowCandle candle = candles.get(i);
            double cx = slot * i + slot / 2;
            Color colour = candle.up() ? up : down;
            g.setStroke(colour);
            g.setFill(colour);
            double yHigh = y(candle.high().doubleValue(), lo, hi, h);
            double yLow = y(candle.low().doubleValue(), lo, hi, h);
            double yOpen = y(candle.open().doubleValue(), lo, hi, h);
            double yClose = y(candle.close().doubleValue(), lo, hi, h);
            g.strokeLine(Math.round(cx) + 0.5, yHigh, Math.round(cx) + 0.5, yLow);
            double top = Math.min(yOpen, yClose);
            // ⚠ Floored at one pixel. A doji — open equal to close — is a real and common candle, and
            // a zero-height body draws nothing at all, so the chart would develop gaps that look like
            // missing data rather than like a flat print.
            double height = Math.max(1, Math.abs(yClose - yOpen));
            g.fillRect(Math.round(cx - body / 2), Math.round(top), Math.round(body), Math.round(height));
        }
    }

    private static double y(double value, double lo, double hi, double h) {
        return h - (value - lo) / (hi - lo) * h;
    }

    /**
     * Three invisible labels whose text fills ARE the chart's palette.
     *
     * <h2>⚠ THE PROBE MUST LIVE IN THE REAL SCENE, and a detached one silently fails</h2>
     *
     * A {@code Canvas} paints with {@code Color} objects and cannot resolve a looked-up value, so the
     * chart's colours have to come off a styled node — §10 criterion 2 forbids the hex literals that
     * would otherwise be needed, and a literal green would be the {@code DiskLamp} trap: invisible on
     * one palette and glaring on another.
     *
     * <p>The first version built a {@code Label}, wrapped it in a throwaway {@code Scene} and called
     * {@code applyCss()}. That scene <b>carries no stylesheet</b>, so nothing resolved, every probe
     * returned Modena's default fill, and the chart rendered every candle in one colour — up and
     * down indistinguishable, with no error anywhere. Found by rendering it. These sit in the panel
     * instead: invisible, unmanaged, and therefore styled by whichever palette is live, so the chart
     * also follows a theme change for free.
     */
    private static final class Palette {
        private final Label up = swatch("es-shmark-up");
        private final Label down = swatch("es-shmark-down");
        private final Label grid = swatch("es-shmark-grid");

        private static Label swatch(String styleClass) {
            Label label = new Label();
            label.getStyleClass().add(styleClass);
            // ⚠ Unmanaged AND invisible. Managed, it would reserve a row in the layout; visible, it
            // would be an empty label in the corner of the panel.
            label.setVisible(false);
            label.setManaged(false);
            return label;
        }

        List<Label> nodes() {
            return List.of(up, down, grid);
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

        private static Color read(Label label) {
            // applyCss so the fill is resolved even on the very first paint, before the scene has
            // had a pass of its own.
            label.applyCss();
            return label.getTextFill() instanceof Color colour ? colour : Color.GRAY;
        }
    }

    // ── the book, the tape, the orders ────────────────────────────────────────────────────────

    private static void paintBook(VBox box, ShadowSnapshot snapshot) {
        box.getChildren().clear();
        List<ShadowLevel> asks = snapshot.asks();
        // ⚠ Asks are drawn HIGHEST first so the two sides meet in the middle at the spread, which is
        // how every book a player has seen is laid out. Best-first here would put the touch at the
        // top of the screen and the spread at the two outer edges.
        for (int i = asks.size() - 1; i >= 0; i--) {
            box.getChildren().add(level(asks.get(i), false));
        }
        Label mid = Ui.micro(Ethecoin.formatApprox(snapshot.mid(), 4) + "  ·  spread "
                + Ethecoin.formatApprox(snapshot.spread(), 4));
        mid.getStyleClass().add("es-shmark-mid");
        box.getChildren().add(mid);
        for (ShadowLevel bid : snapshot.bids()) {
            box.getChildren().add(level(bid, true));
        }
    }

    private static Region level(ShadowLevel level, boolean bid) {
        Label price = Ui.micro(Ethecoin.formatApprox(level.price(), 4));
        price.getStyleClass().add(bid ? "es-shmark-up" : "es-shmark-down");
        price.setMinWidth(76);
        Label size = Ui.micro("×" + level.size());
        size.setMinWidth(28);
        // ⚠ The STANDING travels with the price, because it is part of the price. A row showing the
        // number alone makes the best row simply the best row, which is the one reading of this book
        // that is wrong: the cheapest ask is cheapest because nobody vouches for it.
        Label who = Ui.micro(level.handle());
        who.setMinWidth(74);
        // ⚠ Two labels, not one string. Joined, the standing is what gets clipped when the column is
        // tight — and the standing is the half of this row that prices the counterparty, so losing it
        // leaves a book where the best row is simply the best row.
        Label standing = Ui.micro(level.standing());
        standing.getStyleClass().add("es-shmark-" + level.standing());
        HBox row = Ui.row(UiTokens.SPACE_2, price, size, who, standing);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setAccessibleText((bid ? "bid " : "ask ") + Ethecoin.format(level.price()) + ", " + level.size()
                + " from " + level.handle() + ", " + level.standing() + ", fills "
                + level.fillPercent() + " percent of the time");
        javafx.scene.control.Tooltip.install(
                row, new javafx.scene.control.Tooltip(level.handle() + " — " + level.standing()
                        + ", delivers " + level.fillPercent() + "% of the time"));
        return row;
    }

    private static void paintTape(VBox box, ShadowSnapshot snapshot) {
        box.getChildren().clear();
        for (var print : snapshot.tape()) {
            Label row = Ui.micro(CLOCK.format(print.at()) + "   "
                    + Ethecoin.formatApprox(print.price(), 4) + "   ×" + print.size());
            row.getStyleClass().add(print.buyerTaker() ? "es-shmark-up" : "es-shmark-down");
            box.getChildren().add(row);
        }
    }

    private static void paintOrders(
            VBox box, GameSession session, List<ShadowOrder> orders, Label result, Runnable[] repaint) {
        box.getChildren().clear();
        if (orders.isEmpty()) {
            // ⚠ Names the mechanic rather than the absence. "Nothing resting" is true and teaches
            // nobody what resting means, and this is the one place a player learns that an order
            // outlives the window it was placed in.
            box.getChildren().add(Ui.micro("Nothing resting. An order you place waits here until the "
                    + "market reaches it — including while this window is shut."));
            return;
        }
        for (ShadowOrder order : orders) {
            Label what = Ui.small((order.buy() ? "BUY  " : "SELL ") + order.displayName()
                    + "   " + Ethecoin.formatApprox(order.limitPrice(), 4)
                    + "   ×" + order.quantity());
            what.getStyleClass().add(order.buy() ? "es-shmark-up" : "es-shmark-down");
            // ⚠ The escrow is SHOWN. It is money the player cannot spend, and a balance that quietly
            // excludes it reads as a balance that is wrong.
            Label escrow = Ui.micro(order.escrowWei().signum() > 0
                    ? "holding " + Ethecoin.formatApprox(order.escrowWei(), 4)
                    : "reserved from your storage");
            // ⚠ "Cancel", not "Withdraw". Withdraw is the correct term for pulling a resting order
            // and it is not the word anyone reads on a trading screen — this view is modelled on one
            // a player has actually used, and there the button says Cancel.
            Button cancel = new Button("Cancel");
            cancel.getStyleClass().add("es-shmark-cancel");
            cancel.setAccessibleText("Cancel the " + (order.buy() ? "buy" : "sell") + " order for "
                    + order.displayName() + ". The escrow comes back in full.");
            cancel.setOnAction(event -> {
                GameSession.Outcome outcome = session.cancelShadowOrder(order.orderId());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint[0].run();
            });
            HBox row = Ui.row(UiTokens.SPACE_3, what, escrow, Ui.spacer(), cancel);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        }
    }

    /**
     * The BUY / SELL drawer — a tab on the right edge that opens on hover.
     *
     * <h2>⚠ It STEPS, and it is on {@code Pulse.every}</h2>
     *
     * §5 permits no easing anywhere and {@code UiContractTest} rations {@code AnimationTimer} to two
     * files by name, so the slide is {@link UiTokens#REVEAL_STEPS} whole jumps on the shared clock —
     * the same ladder {@code SizeReadout}, {@code BalanceDelta} and {@code Motion} use.
     *
     * <p>⚠ {@code every}, <b>not</b> {@code animate}. Under Reduce motion a decorative subscription
     * never fires, so an {@code animate} drawer would be one that <em>cannot open</em> — the control
     * broken on exactly the accessibility path, which is the failure the carousel already recorded.
     * The clock runs in both modes and only the ramp is conditional: with motion suppressed the
     * drawer snaps open in one step, which is §5's "static final state" rather than a feature
     * withdrawn.
     *
     * <h2>⚠ Hover on the DRAWER, not on the tab</h2>
     *
     * Hovering the tab opens it; the pointer then has to travel across the form, which is part of
     * the drawer — so the drawer stays hovered and stays open. Keying on the tab alone would close
     * it the instant the player moved towards the thing they opened it for.
     */
    private static Region drawer(
            GameSession session,
            String[] listing,
            TextField limit,
            TextField quantity,
            Label result,
            Runnable[] repaint) {

        Region form = orderForm(session, listing, limit, quantity, result, repaint);

        Label caption = new Label("BUY / SELL");
        caption.getStyleClass().add("es-shmark-tab-label");
        // ⚠ Rotated inside a FIXED-SIZE StackPane. A rotation is a transform and does not change the
        // node's layout bounds, so a rotated label in a sizing container reserves its horizontal
        // width and the column comes out as wide as the text is long. The holder fixes the width and
        // the label turns inside it.
        // ⚠ WRAPPED IN A GROUP, and without it the caption renders as a rotated ellipsis. A
        // StackPane resizes a resizable child to fit itself, so the Label was squeezed to the
        // handle's 22px width and truncated to "..." — and only THEN rotated, so what reached the
        // screen was a vertical row of three dots. A Group does not resize its children, so the
        // label keeps its natural width and the rotation turns the whole phrase. Found by rendering;
        // it compiles, and at a glance the handle just looks like it has a texture on it.
        javafx.scene.layout.StackPane tab = new javafx.scene.layout.StackPane(new javafx.scene.Group(caption));
        caption.setRotate(-90);
        tab.getStyleClass().add("es-shmark-tab");
        tab.setMinWidth(UiTokens.SHMARK_TAB_WIDTH);
        tab.setPrefWidth(UiTokens.SHMARK_TAB_WIDTH);
        tab.setMaxWidth(UiTokens.SHMARK_TAB_WIDTH);
        tab.setMinHeight(UiTokens.SHMARK_TAB_HEIGHT);
        tab.setPrefHeight(UiTokens.SHMARK_TAB_HEIGHT);
        tab.setMaxHeight(UiTokens.SHMARK_TAB_HEIGHT);
        tab.setAccessibleText("Buy and sell. Opens when the pointer is over it.");
        javafx.scene.control.Tooltip.install(tab, new javafx.scene.control.Tooltip("Buy / sell"));

        HBox drawer = new HBox(tab, form);
        drawer.setAlignment(Pos.CENTER_RIGHT);
        drawer.getStyleClass().add("es-shmark-drawer");
        // ⚠ USE_PREF_SIZE both ways. A StackPane RESIZES a resizable child to fill it, so without
        // this the drawer would be a transparent full-panel pane swallowing every click meant for
        // the chart.
        drawer.setMaxWidth(Region.USE_PREF_SIZE);
        drawer.setMaxHeight(Region.USE_PREF_SIZE);
        javafx.scene.layout.StackPane.setAlignment(drawer, Pos.CENTER_RIGHT);

        double[] shown = {0};
        boolean[] wanted = {false};
        drawer.hoverProperty().addListener((obs, was, now) -> wanted[0] = now);
        // Keyboard users get it too: focus anywhere inside the form holds it open.
        form.focusWithinProperty().addListener((obs, was, now) -> wanted[0] = now);

        Runnable place = () -> drawer.setTranslateX(form.getWidth() * (1 - shown[0]));
        form.widthProperty().addListener((obs, was, now) -> place.run());

        AutoCloseable slide = Pulse.shared().every(UiTokens.REVEAL_MS / UiTokens.REVEAL_STEPS, () -> {
            double target = wanted[0] ? 1 : 0;
            if (shown[0] == target) {
                return;
            }
            // ⚠ Reduce motion goes straight there. Stepping would be the same motion at the same
            // speed, which is the thing the setting exists to stop.
            double step = Pulse.shared().reducedMotion() ? 1 : 1.0d / UiTokens.REVEAL_STEPS;
            shown[0] = target > shown[0] ? Math.min(target, shown[0] + step) : Math.max(target, shown[0] - step);
            place.run();
        });
        Views.releaseOnDetach(drawer, slide);
        place.run();
        return drawer;
    }

    /**
     * The instrument picker, grouped by category.
     *
     * <p>⚠ Built once from the listings rather than rebuilt on every repaint — the panel repaints
     * every second, and a menu rebuilt under an open popup closes it mid-click.
     */
    private static void buildPicker(
            javafx.scene.control.MenuButton picker,
            GameSession session,
            List<String> listings,
            String[] listing,
            Runnable[] repaint) {
        java.util.Map<String, javafx.scene.control.Menu> categories = new java.util.LinkedHashMap<>();
        for (String id : listings) {
            var offering = io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(id);
            String category = offering.map(o -> o.category()).orElse("other");
            String name = offering.map(o -> o.name()).orElse(id);
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(name);
            item.setOnAction(event -> {
                listing[0] = id;
                picker.setText(name);
                repaint[0].run();
            });
            categories
                    .computeIfAbsent(category, key -> {
                        javafx.scene.control.Menu menu =
                                new javafx.scene.control.Menu(key.toUpperCase(Locale.ROOT));
                        picker.getItems().add(menu);
                        return menu;
                    })
                    .getItems()
                    .add(item);
        }
        picker.setText(io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(listing[0])
                .map(o -> o.name())
                .orElse(listing[0]));
    }

    private static Region orderForm(
            GameSession session,
            String[] listing,
            TextField limit,
            TextField quantity,
            Label result,
            Runnable[] repaint) {
        VBox form = new VBox(UiTokens.SPACE_2);
        form.getStyleClass().addAll("es-market-card", "es-shmark-form");
        form.setMinWidth(210);
        form.setMaxWidth(210);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        Button buy = new Button("Buy");
        buy.getStyleClass().addAll("es-market-buy", "es-shmark-buy");
        Button sell = new Button("Sell");
        sell.getStyleClass().addAll("es-market-buy", "es-shmark-sell");

        Runnable[] send = new Runnable[2];
        send[0] = () -> submit(session, listing[0], true, limit, quantity, result, repaint);
        send[1] = () -> submit(session, listing[0], false, limit, quantity, result, repaint);
        buy.setOnAction(event -> send[0].run());
        sell.setOnAction(event -> send[1].run());

        form.getChildren()
                .addAll(
                        heading("ORDER"),
                        Ui.micro("Limit price"),
                        limit,
                        Ui.micro("Quantity"),
                        quantity,
                        Ui.row(UiTokens.SPACE_2, buy, sell),
                        Views.wrapped("A limit rests until the market reaches it. A buy holds the "
                                + "ethecoin now; a sell reserves the copy."));
        return form;
    }

    private static void submit(
            GameSession session,
            String itemType,
            boolean buy,
            TextField limit,
            TextField quantity,
            Label result,
            Runnable[] repaint) {
        BigInteger price;
        try {
            // ⚠ Ethecoin.ofDecimal, never Double.parseDouble. A double holds ~16 digits and ethecoin
            // divides to 18 — the exact trap `send` recorded when it was the one place a player typed
            // an amount, and this is now the second.
            price = Ethecoin.ofDecimal(limit.getText().trim()).wei();
        } catch (RuntimeException malformed) {
            result.setText("that is not a price.");
            Views.styleByOutcome(result, GameSession.Outcome.refused("that is not a price."));
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(quantity.getText().trim());
        } catch (NumberFormatException malformed) {
            qty = 1;
        }
        GameSession.Outcome outcome = session.placeShadowOrder(itemType, buy, price, qty, "");
        result.setText(outcome.message());
        Views.styleByOutcome(result, outcome);
        repaint[0].run();
    }
}
