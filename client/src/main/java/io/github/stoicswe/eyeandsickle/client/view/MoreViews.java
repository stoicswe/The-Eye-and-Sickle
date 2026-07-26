package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The five windows that were placeholders: market, map, recon, botnet and comms.
 *
 * <h2>What "built out" means when the system underneath is still a proposal</h2>
 *
 * {@code docs/design/05} (the breach minigame), {@code 10} (bots) and {@code 12}/{@code 14} (social,
 * narrative) are marked <b>[PROPOSAL]</b>, and {@code CLAUDE.md} asks that proposals be surfaced
 * rather than hard-committed in code. So these windows now render everything that <em>is</em> decided
 * — the gate taxonomy, the price bands, the recon cost model, the invariants that bound each system —
 * and name the specific undecided question where one blocks.
 *
 * <p>That is more useful than either extreme. An empty pane teaches nothing; a fabricated one commits
 * the design by accident.
 */
public final class MoreViews {

    private MoreViews() {}

    // ------------------------------------------------------------------ market

    /**
     * What ethecoin can and cannot buy.
     *
     * <p>The catalogue itself is content the server does not have either ({@code W-3}), so this
     * renders the <em>gate taxonomy</em> from {@code docs/design/02-unlock-gates.md} and the price
     * bands from {@code 03} §2 — which is the part a player most needs and the part that is decided.
     */
    public static Region market(GameSession session) {
        VBox root = panel("MARKET — a package manager");

        Label invariant = wrapped(
                "Ethecoin buys breadth, never a ceiling. Consumables, replacements and horizontal "
                        + "options are for sale. Capacity is not: you cannot buy compute, you cannot "
                        + "buy vault space, and you cannot buy your way past a proof-of-skill gate.");

        VBox gates = new VBox(10);
        for (UnlockGate gate : UnlockGate.values()) {
            VBox box = new VBox(3);
            box.getStyleClass().add("es-panel");
            Label name = new Label(gateName(gate));
            name.getStyleClass().add("es-panel-title");
            box.getChildren().addAll(name, secondary(gateExplanation(gate)));
            gates.getChildren().add(box);
        }

        VBox bands = new VBox(3);
        bands.getChildren().addAll(
                new Label("PRICE BANDS"),
                mono("consumables            5 – 15 EC"),
                mono("mid-tier tools        40 – 60 EC"),
                mono("top purchasable          ~200 EC"),
                mono("black-market zero-day    400+ EC"),
                secondary("Zero-days are never reliably purchasable or farmable. A price band is not "
                        + "an offer."));

        Label balance = new Label();
        balance.getStyleClass().addAll("es-numeric", "es-ethecoin");
        Runnable refresh = () -> balance.setText("balance: " + session.balance());
        refresh.run();
        session.onChange(s -> refresh.run());

        // The actual offerings. SOLO-3 closed 2026-07-25 — see solo/Catalogue.
        VBox listing = new VBox(8);
        Label result = new Label();
        result.setWrapText(true);
        for (var o : io.github.stoicswe.eyeandsickle.solo.Catalogue.offerings()) {
            VBox card = new VBox(4);
            card.getStyleClass().add("es-panel");
            Label name = new Label(o.name());
            name.getStyleClass().add("es-panel-title");
            Label desc = wrapped(o.description());
            Label terms = new Label(o.purchasable()
                    ? money(o.priceMinorUnits()) + (o.equippedCycles() > 0
                            ? "   ·   " + o.equippedCycles() + " cycles while armed" : "")
                    : o.gate().name().toLowerCase(Locale.ROOT).replace('_', '-') + " gate");
            terms.getStyleClass().add(o.purchasable() ? "es-ethecoin" : "es-state-unreachable");
            card.getChildren().addAll(name, desc, terms);
            if (!o.purchasable()) {
                card.getChildren().add(secondary(o.gateRequirement()));
            }
            Button buy = new Button(o.purchasable() ? "Buy" : "Why can't I have this?");
            buy.setOnAction(e -> {
                GameSession.Outcome outcome = session.purchase(o.id());
                result.setText(outcome.message());
                styleByOutcome(result, outcome);
            });
            card.getChildren().add(buy);
            listing.getChildren().add(card);
        }

        ScrollPane scroll = new ScrollPane(new VBox(12, invariant, balance, new Separator(),
                new Label("OFFERINGS"), listing, result, new Separator(),
                new Label("THE FIVE GATES"), gates, new Separator(), bands));
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);
        return root;
    }

    private static String gateName(UnlockGate gate) {
        return switch (gate) {
            case ETHECOIN -> "Ethecoin gate — buy it";
            case SCHEMATIC -> "Schematic gate — find or earn it";
            case REPUTATION -> "Reputation gate — be trusted enough";
            case PROOF_OF_SKILL -> "Proof-of-skill gate — demonstrate it";
            case HEAT_STATE -> "Heat-state gate — be cold enough, or hot enough";
        };
    }

    private static String gateExplanation(UnlockGate gate) {
        return switch (gate) {
            case ETHECOIN -> "Consumables, replacements, horizontal options. Never a permanent ceiling.";
            case SCHEMATIC -> "Permanent capability. Found or earned, never bought — this is what stops "
                    + "money from becoming progress.";
            case REPUTATION -> "Things that would distort the economy if they were simply free.";
            case PROOF_OF_SKILL -> "Automation shortcuts. Tier-gated, never count-gated: doing an easy "
                    + "thing a hundred times proves nothing.";
            case HEAT_STATE -> "Access that swings both ways. Some contacts only deal with you while you "
                    + "are cold; some only once you are notorious.";
        };
    }

    // ------------------------------------------------------------------ map

    /** The network graph — what is decided about targeting, and what is not. */
    public static Region map(GameSession session) {
        VBox root = panel("NETWORK MAP — traceroute");

        TableView<GameSession.KnownNode> table = new TableView<>();
        TableColumn<GameSession.KnownNode, String> address = new TableColumn<>("Address");
        address.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().address()));
        address.setPrefWidth(180);
        TableColumn<GameSession.KnownNode, String> tier = new TableColumn<>("Tier");
        tier.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("T" + c.getValue().tier()));
        tier.setPrefWidth(70);
        TableColumn<GameSession.KnownNode, String> recon = new TableColumn<>("Recon");
        recon.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().reconLevel())));
        recon.setPrefWidth(80);
        TableColumn<GameSession.KnownNode, String> note = new TableColumn<>("Note");
        note.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().hostsForeignMiner() ? "foreign miner present" : ""));
        note.setPrefWidth(240);
        table.getColumns().addAll(List.of(address, tier, recon, note));
        table.setPlaceholder(new Label("Nothing discovered yet. Recon costs compute and ethecoin, "
                + "which is why this list starts empty rather than full."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> table.getItems().setAll(session.knownNodes());
        refresh.run();
        session.onChange(s -> refresh.run());

        root.getChildren()
                .addAll(
                        wrapped("Only machines you have discovered appear here, and the same rule governs "
                                + "`ls /net/` and tab completion. A map that showed unscanned nodes "
                                + "would be giving away what recon is priced to sell."),
                        table,
                        openQuestion("Breach targeting is not built: the hacking minigame is the "
                                + "largest [PROPOSAL] in the design (docs/design/05), and its puzzle "
                                + "classes, trace model and difficulty tiers are all still open. "
                                + "Committing an interface to it now would decide it by accident."));
        return scrollable(root);
    }

    // ------------------------------------------------------------------ recon

    /** What recon costs and what it buys — the decided part of a proposed system. */
    public static Region recon(GameSession session) {
        VBox root = panel("RECON — less");
        root.getChildren()
                .addAll(
                        wrapped("Recon is a paid service. What you learn about a machine costs compute "
                                + "and ethecoin, and that price is the reason the network map starts "
                                + "empty."),
                        new Separator(),
                        new Label("THE COST MODEL"),
                        mono("port sweep         15 – 45 EC   3 – 8 compute"),
                        secondary("Each tool raises what you know about one node. Knowledge never "
                                + "decreases, so the spend is permanent progress on that target."),
                        new Separator(),
                        new Label("WHAT THIS TEACHES"),
                        wrapped("A port sweep asks a machine which services are listening. That is a "
                                + "real thing with a real tool — `nmap` — and the game's version costs "
                                + "what it costs because scanning is noisy in reality too. See "
                                + "`man port-sweep` when the page ships."),
                        openQuestion("Recon tool output is not rendered yet: the individual tools in "
                                + "docs/design/07 depend on the minigame's target model, which is "
                                + "still open. The cost model above is decided and is what a player "
                                + "needs to budget."));
        return scrollable(root);
    }

    // ------------------------------------------------------------------ botnet

    /** Bots — the invariants that bound them, which are decided even though the system is not. */
    public static Region botnet(GameSession session) {
        VBox root = panel("BOTNET — jobs / systemctl");
        root.getChildren()
                .addAll(
                        wrapped("A frame is a blueprint. An instance is a running bot built from one. "
                                + "Instances cost ethecoin and hold compute while they run; frames are "
                                + "gated and are never lost."),
                        new Separator(),
                        new Label("THE RULES THAT ARE DECIDED"),
                        bullet("Bots assist, they never substitute. A bot never solves the puzzle for "
                                + "you — if one could, the game would be watching itself play."),
                        bullet("Losing a bot destroys the instance and anything socketed into it. It "
                                + "never destroys the frame, so a loss is a setback and not a deletion."),
                        bullet("Every running instance holds compute permanently, so a botnet is a "
                                + "standing claim on the same budget everything else draws from."),
                        bullet("Running more bots shrinks the window you have to respond to each event. "
                                + "Scale costs attention, not just cycles."),
                        openQuestion("Frames, instances and the backlog timer are not built: "
                                + "docs/design/10 is [PROPOSAL] and its central number — how much "
                                + "manual play beats bot play — is explicitly unmeasurable until the "
                                + "minigame exists (P-3). That number is what Invariant I10 rests on."));
        return scrollable(root);
    }

    // ------------------------------------------------------------------ comms

    /** The social layer, and the one thing the vision doc forbids putting here. */
    public static Region comms(GameSession session) {
        VBox root = panel("COMMS — mail / who");
        root.getChildren()
                .addAll(
                        wrapped("Other operators, recovered messages, and the evidence trail that "
                                + "decides who is informing on whom. This is deliberately separate "
                                + "from `identity`: that window is who you are, this one is who else "
                                + "is out there."),
                        new Separator(),
                        new Label("WHAT THIS WINDOW WILL NOT BE"),
                        wrapped("Not a chat window. The vision document is explicit that story arrives "
                                + "as recovered logs and records, and that there are no companion "
                                + "characters — a general chat surface is the easiest place in the "
                                + "whole client for that to leak."),
                        openQuestion("The informant system, the evidence threshold and the mass-vote "
                                + "override are [PROPOSAL] (docs/design/12), and all three are "
                                + "multiplayer mechanics with no meaning in a solo game. This window "
                                + "stays empty offline because there is genuinely nobody there."));
        return scrollable(root);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Wraps a panel so it scrolls when the window is smaller than its contents.
     *
     * <p>Applied to every tool that does not already manage its own scrolling. The deck lets a
     * player size a window to anything above 240×120, so any panel without this simply clips —
     * and clipped content is silently missing rather than visibly cut off, which is the worst of
     * both. {@code docs/client/07-accessibility.md} also needs it: a player at 200% OS text scale
     * hits the bottom of the settings panel long before anyone testing at 100% does.
     *
     * <p>{@code setFitToWidth} matters as much as the scrolling: without it a ScrollPane gives its
     * content the content's own preferred width, so every wrapped label stops wrapping and the
     * panel grows a horizontal scrollbar instead of reflowing.
     */
    static Region scrollable(Region content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-scroll");
        // Vertical only. A deck panel reflows to its width; a horizontal bar here would mean the
        // content refused to, which is a layout bug rather than something to scroll past.
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private static VBox panel(String title) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        Label heading = new Label(title);
        heading.getStyleClass().add("es-panel-title");
        root.getChildren().add(heading);
        return root;
    }

    private static Label wrapped(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    private static Label secondary(String text) {
        Label l = wrapped(text);
        l.getStyleClass().add("es-text-secondary");
        return l;
    }

    private static Label mono(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("es-mono");
        return l;
    }

    private static String money(long minorUnits) {
        return String.format(Locale.ROOT, "%d.%02d EC", minorUnits / 100, Math.abs(minorUnits % 100));
    }

    private static void styleByOutcome(Label label, GameSession.Outcome outcome) {
        label.getStyleClass().removeAll("es-state-refused", "es-state-unreachable");
        if (outcome.status() == GameSession.Outcome.NOPERM
                || outcome.status() == GameSession.Outcome.UNAVAILABLE) {
            // A gate is not a refusal: 77 says "you may have this, but not yet, and here is why".
            label.getStyleClass().add("es-state-unreachable");
        } else if (!outcome.succeeded()) {
            label.getStyleClass().add("es-state-refused");
        }
    }

    private static Label bullet(String text) {
        Label l = wrapped("·  " + text);
        l.setPadding(new Insets(0, 0, 4, 8));
        return l;
    }

    /** A named open question, marked so it reads as a decision pending rather than a missing feature. */
    private static Region openQuestion(String text) {
        VBox box = new VBox(4);
        box.getStyleClass().add("es-panel");
        Label heading = new Label("STILL OPEN");
        heading.getStyleClass().addAll("es-panel-title", "es-state-unreachable");
        Label body = wrapped(text);
        body.getStyleClass().add("es-text-secondary");
        box.getChildren().addAll(heading, body);
        return box;
    }
}
