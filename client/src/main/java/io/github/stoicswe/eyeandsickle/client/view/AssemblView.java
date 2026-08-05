package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * ASSEMBL COMPILER — a schematic is a blueprint, not a receipt.
 *
 * <h2>What changed, and why the storefront lost something</h2>
 *
 * A schematic used to be a <em>gate</em>: the shop would not sell you the item until you held one.
 * That made the schematic a key, and a key is a thing you carry rather than a thing you use. It is a
 * blueprint now — you hold it, this tool reads it, and the item is <b>made</b> from resources you
 * have. So the storefront stops offering schematic-gated items entirely: they are not for sale at
 * any price, because they are not sold at all.
 *
 * <p>⚠ <b>Invariant I3 survives, and it matters that it does.</b> Every item still sits behind
 * exactly one gate — the schematic. What moved is what holding one <em>lets you do</em>: it was
 * permission to buy and it is now the ability to build. ⚠ <b>I2 also survives, and more comfortably
 * than before</b>: there is now no path at all from ethecoin to a schematic-gated item, where
 * previously there was a priced one waiting behind a check.
 *
 * <h2>⚠ THE MECHANICS ARE DELIBERATELY NOT DESIGNED HERE</h2>
 *
 * What a compile costs, how long it takes, what resources it consumes, whether it can fail, and
 * whether a schematic is consumed — none of that is decided, and this tool does not decide it. It
 * lists what a player holds and what each blueprint would produce, and says plainly that the
 * compiler is not wired up. That is the honest state: a window that pretended to compile, or that
 * invented a cost, would put a rule in the code that the design has not made — which
 * {@code CLAUDE.md}'s working agreement forbids in as many words.
 *
 * <p>Recorded as <b>AS-1</b> in {@code docs/design/15-open-questions.md}.
 */
public final class AssemblView {

    private AssemblView() {}

    /**
     * @param session the session, for what the player holds
     * @return the compiler window
     */
    public static Region create(GameSession session) {
        VBox page = new VBox(UiTokens.SPACE_4);
        page.getStyleClass().add("es-market");

        Label wordmark = new Label("ASSEMBL COMPILER");
        wordmark.getStyleClass().add("es-market-wordmark");
        Label tagline = Ui.micro("a schematic is a blueprint");
        tagline.getStyleClass().add("es-market-tagline");
        HBox masthead = Ui.row(UiTokens.SPACE_3, wordmark, tagline);
        masthead.setAlignment(Pos.BASELINE_LEFT);
        masthead.getStyleClass().add("es-market-masthead");

        // ⚠ States the unbuilt part first, not last. A tool that listed blueprints and hid the fact
        // that nothing can be compiled would read as broken the moment somebody clicked; saying it
        // up front makes the window an honest preview instead.
        Label status = Views.wrapped(
                "The compiler is not wired up yet. What a build costs, how long it takes, what it "
                        + "consumes and whether a schematic survives it are open questions — see AS-1. "
                        + "What is settled is the shape: a schematic you hold is a blueprint here, and "
                        + "the storefront no longer sells anything a schematic gates.");
        status.getStyleClass().add("es-shmark-promised");

        VBox blueprints = new VBox(UiTokens.SPACE_3);
        blueprints.getChildren().add(heading("BLUEPRINTS"));

        // ⚠ Reads the CATALOGUE for what is schematic-gated, and the session for what is held. The
        // two are different questions and neither is derivable from the other: the catalogue knows
        // what exists, the save knows what this character has.
        var gated = Catalogue.offerings().stream()
                .filter(offering -> offering.gate() == UnlockGate.SCHEMATIC)
                .toList();
        if (gated.isEmpty()) {
            blueprints.getChildren().add(Ui.micro("Nothing in the catalogue is schematic-gated."));
        }
        for (Catalogue.Offering offering : gated) {
            VBox card = new VBox(UiTokens.SPACE_2);
            card.getStyleClass().add("es-market-card");
            card.setMaxWidth(UiTokens.MARKET_CONTENT_WIDTH);

            Label name = new Label(offering.name());
            name.getStyleClass().add("es-panel-title");
            Label blurb = Views.wrapped(offering.description());
            blurb.getStyleClass().add("es-text-secondary");
            // ⚠ The gate's own words. `design/02`'s taxonomy exists so a refusal is legible, and the
            // requirement text is where that legibility lives — restating it here would be a second
            // copy that drifts.
            Label needs = Ui.micro(offering.gateRequirement());
            needs.setWrapText(true);

            card.getChildren().addAll(name, blurb, needs);
            blueprints.getChildren().add(card);
        }

        page.getChildren().addAll(masthead, status, blueprints);

        VBox holder = new VBox(page);
        holder.setAlignment(Pos.TOP_CENTER);
        page.setMaxWidth(UiTokens.MARKET_CONTENT_WIDTH);
        ScrollPane scroll = new ScrollPane(holder);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("es-market-scroll");
        return scroll;
    }

    private static Label heading(String text) {
        Label label = Ui.label(text);
        label.getStyleClass().addAll("es-panel-title", "es-market-section");
        return label;
    }
}
