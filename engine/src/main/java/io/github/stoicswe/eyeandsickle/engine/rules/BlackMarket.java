package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.MessageState;
import java.time.Instant;

/**
 * When the people who run the darknet market decide you are worth talking to.
 *
 * <h2>The gate is on the VENDOR, not on the goods</h2>
 *
 * {@code docs/design/02-unlock-gates.md} §2.5 keeps these two apart: the heat-state gate governs
 * "vendor and contact <em>access</em>. Never ownership", and it names black-market brokers as the
 * case that runs the unusual direction — <em>"only reachable while hot: being hunted opens doors
 * that being clean does not."</em> So nothing here changes what any item's gate is. The Honeypot
 * Stash is reputation-gated before this fires and reputation-gated after; what changes is whether
 * the player can see the shelf it is on. Invariant <b>I3</b> is untouched because reaching a vendor
 * and being allowed to buy from them are two different checks.
 *
 * <h2>⚠ BOTH conditions, and the heat one is a FLOOR</h2>
 *
 * Standing alone would make this a reputation gate wearing a different hat. Heat alone would hand
 * the darknet to anybody careless. Together they describe the only person this fiction would open a
 * door for: somebody the factions rate <em>and</em> the Eye is already hunting. Note the direction on
 * heat — you need <b>at least</b> {@link Balance#BLACK_MARKET_MIN_HEAT}, which inverts every other
 * gate in the game and is exactly §2.5's point.
 *
 * <h2>⚠ Standing is the BETTER of the two factions, never their sum</h2>
 *
 * A committed Sickle operative and a committed Eye operative are each somebody worth knowing. Adding
 * the two would let a fence-sitter with middling standing on both sides qualify on the strength of
 * neither, which is the opposite of what the threshold is for — and it would quietly make the two
 * faction reputations a single pooled number, which {@code CLAUDE.md} and the glossary both forbid.
 */
public final class BlackMarket {

    private BlackMarket() {}

    /** The {@code MessageState.kind} of the notice, so it is never sent twice. */
    public static final String NOTICE_KIND = "black-market-contact";

    /** Who the notice appears to be from. Deliberately not a person. */
    private static final String SENDER = "unsigned relay";

    /**
     * Whether this character is somebody the market would approach.
     *
     * <p>Pure, so the condition can be tested without a save file, a clock or a tick — and so the
     * Settings/diagnostic surfaces can ask the same question the delivery path asks rather than
     * reimplementing the comparison beside it.
     */
    public static boolean noticed(GameSave save) {
        if (save == null) {
            return false;
        }
        int standing = Math.max(save.factionReputationEye, save.factionReputationSickle);
        return standing >= Balance.BLACK_MARKET_MIN_REPUTATION && save.personalHeat >= Balance.BLACK_MARKET_MIN_HEAT;
    }

    /** Whether the notice has already been sent, at any point in this character's life. */
    public static boolean alreadyContacted(GameSave save) {
        return save != null && save.messages.stream().anyMatch(m -> NOTICE_KIND.equals(m.kind));
    }

    /**
     * Sends the contact message if it is due, exactly once ever.
     *
     * <h2>⚠ Keyed on the MESSAGE, not on a flag</h2>
     *
     * Standing and heat both move, in both directions, and can cross their thresholds several times
     * in a session — so "have I sent this" cannot be answered by the condition. It is answered by
     * looking for the message, which means there is no separate boolean to fall out of step with the
     * inbox: delete the message and it can be sent again, which is the honest behaviour rather than a
     * player staring at a market they were told about and cannot find.
     *
     * <p>⚠ <b>Going cold does not take it back.</b> §2.5 is explicit that heat gates
     * <em>reachability</em> — "going cold does not confiscate what you bought". Once the introduction
     * has been made it has been made; the module is an ordinary item after that.
     *
     * @return the message if one was sent, or {@code null}
     */
    public static MessageState contactIfDue(GameSave save, Instant now) {
        if (!noticed(save) || alreadyContacted(save)) {
            return null;
        }
        MessageState m = new MessageState();
        m.kind = NOTICE_KIND;
        m.from = SENDER;
        m.subject = "you have been noticed";
        m.body =
                """
                Somebody vouched for you. That is not a compliment — it means enough of the \
                right people know your name, and enough of the wrong ones are looking for it.

                What is attached is an onion router. It does not hide you from the Eye. What it \
                does is reach addresses that ordinary lookups will not resolve, which is the only \
                way to see our board at all.

                Install it and the Marknet tab appears in your market. Do not ask who we are, and \
                do not go quiet on us — a name nobody is hunting is a name we stop recognising.
                """;
        // ⚠ The one field in the inbox with an economic consequence, and this is the only place in
        // the game that sets it. See MessageState.offerItemType.
        m.offerItemType = Catalogue.TOR_MODULE;
        m.receivedAt = now;
        Inbox.deliver(save, m);
        return m;
    }
}
