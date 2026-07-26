package io.github.stoicswe.eyeandsickle.client.teaching;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * Tier 1 of the teaching layer — the gloss that arrives without being asked for.
 *
 * <h2>CL-10, closed</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §4.1 specifies three tiers: hover or focus for a
 * one-line gloss, a keypress for the full page, citations below that. Tiers 2 and 3 shipped with the
 * manual; this is the missing first one, and it is the one that makes teaching <em>ambient</em> rather
 * than looked-up. A player who has to know a word exists before they can ask about it learns nothing
 * they did not already suspect.
 *
 * <h2>The four "never" rules, made mechanical (§4.7)</h2>
 *
 * <ul>
 *   <li><b>Never blocks.</b> A tooltip, not a modal or a stealing popover — it cannot swallow a
 *       keystroke during a breach.
 *   <li><b>Never moves anything.</b> Attaching a tooltip does not reflow the node it decorates, so a
 *       gloss appearing cannot shift the control a player was about to click.
 *   <li><b>Never fires at {@code off}.</b> The teaching level is checked at attach time and at show
 *       time, so turning it off mid-session takes effect immediately.
 *   <li><b>Never invents.</b> If there is no page for the term, there is no gloss. A best-effort
 *       guess would be exactly the wrong-mapping failure the whole education doc set exists to
 *       prevent.
 * </ul>
 *
 * <h2>Hover OR focus, because hover alone excludes people</h2>
 *
 * {@code docs/client/07} §3.6 (WCAG SC 1.4.13) requires hover-triggered content to be reachable by
 * keyboard. JavaFX shows a {@code Tooltip} on hover only, so this also sets the accessible text —
 * which is what a screen reader announces on focus — giving the keyboard path the same content by a
 * different route.
 */
public final class GlossBar {

    private GlossBar() {}

    /** Teaching levels, from {@code docs/client/04} §4.5. */
    public static final String LEVEL_EXPLAIN = "explain";

    public static final String LEVEL_TERMS = "terms";
    public static final String LEVEL_OFF = "off";

    /**
     * Attaches a gloss to a node for the named term.
     *
     * <p>Silently does nothing when the term has no page. That is deliberate: a caller decorating a
     * whole table of labels should not have to know which of them are terms, and a missing page must
     * never produce an invented definition.
     *
     * @return true if a gloss was attached
     */
    public static boolean attach(Node node, String term, TermDatabase terms, ClientProfile profile) {
        if (node == null || term == null || term.isBlank()) {
            return false;
        }
        Optional<TermPage> found = terms.find(term.toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            return false;
        }
        TermPage page = found.get();

        // The accessible name carries the gloss regardless of level, because a screen reader user
        // navigating by focus has no other way to reach tier 1 — and because an accessible name is
        // not "teaching arriving unbidden", it is the label the control always had.
        node.setAccessibleText(page.nameLine());

        String level = profile.settings().teachingLevel;
        if (LEVEL_OFF.equals(level)) {
            return false;
        }

        Tooltip tip = new Tooltip(glossText(page, level));
        // Long enough not to fire while a cursor crosses the screen, short enough to feel like an
        // answer rather than a delay.
        tip.setShowDelay(Duration.millis(450));
        tip.setHideDelay(Duration.millis(120));
        tip.setShowDuration(Duration.seconds(20));
        tip.setWrapText(true);
        tip.setMaxWidth(360);
        tip.getStyleClass().add("es-gloss");
        Tooltip.install(node, tip);
        return true;
    }

    /**
     * What the gloss says at each level.
     *
     * <p>{@code terms} shows the term and its status and nothing else — enough to know a thing has a
     * name and whether the game made it up, without the explanation a Unix-literate player does not
     * need. {@code explain} adds the sentence.
     */
    static String glossText(TermPage page, String level) {
        if (LEVEL_TERMS.equals(level)) {
            return page.name() + "  [" + page.status().label() + "]\n\nman " + page.id();
        }
        return page.name() + " — " + page.gloss()
                + "\n\n" + page.status().explanation()
                + "\n\nman " + page.id() + "  for the full page";
    }

    /**
     * A label that glosses itself.
     *
     * <p>The common case by far, and having it in one place means a view adds teaching by using a
     * different factory rather than by remembering a two-line incantation.
     */
    public static Label label(String text, String term, TermDatabase terms, ClientProfile profile) {
        Label label = new Label(text);
        attach(label, term, terms, profile);
        return label;
    }
}
