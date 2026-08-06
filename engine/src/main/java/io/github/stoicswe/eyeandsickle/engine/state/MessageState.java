package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;
import java.util.UUID;

/**
 * One message in the rig's inbox.
 *
 * <h2>⚠ THIS IS THE GAME TALKING TO THE PLAYER, AND NOTHING ELSE</h2>
 *
 * Every message here is written by the engine: a vendor making contact, an event worth a sentence, a
 * notice the player should be able to find again later. <b>Player-to-player conversation is not this
 * type and must never become it</b> — that lives on Bluesky's DM service, is reached through the
 * player's own account, and never touches a save file. The two share a window and share nothing else.
 *
 * <p>The reason is <b>I14</b>. Anything in this class is state the engine authored and the engine
 * trusts; a message that arrived from another player is state somebody else authored, and treating
 * the two as one type is how a forged message ends up granting something. {@code offerItemType} is
 * the sharp edge: it is a licence to receive an item for nothing, so it may only ever be set by the
 * rules and never by anything a person can write.
 *
 * <h2>Why the inbox is in the SAVE rather than derived</h2>
 *
 * Almost everything else in this game is a pure function of state and a clock — the market's
 * listings, the mempool, the chain. A message is not: it is a thing that <em>happened at a moment</em>
 * and whose read/unread state is the player's own. Deriving it would mean recomputing whether the
 * player had been contacted, which would either re-announce every notice on every load or lose them.
 */
public final class MessageState {

    public String messageId = UUID.randomUUID().toString();

    /** Who it is from, as displayed. A name in the fiction, never a handle from outside the game. */
    public String from = "";

    public String subject = "";

    public String body = "";

    public Instant receivedAt = Instant.EPOCH;

    public boolean read = false;

    /**
     * A catalogue id this message entitles the player to download, or {@code ""} for most messages.
     *
     * <h2>⚠ A LICENCE TO RECEIVE SOMETHING FOR NOTHING. Treat every write to it as a grant.</h2>
     *
     * This is how the TOR module reaches the player: the vendor's notice carries the download, the
     * player claims it, and the ordinary download-and-install path takes over from there. That makes
     * it the one field in the inbox with an economic consequence, so:
     *
     * <ul>
     *   <li>It is set <b>only</b> by engine rules ({@code rules/BlackMarket}), never from any input.
     *   <li>It is cleared when claimed, so a message cannot be redeemed twice.
     *   <li>Nothing that originates outside the game may ever populate it — see this class's note on
     *       why a Bluesky DM is not a {@code MessageState}.
     * </ul>
     */
    public String offerItemType = "";

    /** Whether {@link #offerItemType} has already been taken. Kept so the message still reads sensibly. */
    public boolean offerClaimed = false;

    /**
     * A stable key for messages the rules must not send twice.
     *
     * <p>The black-market notice is delivered when standing and heat first cross their thresholds,
     * and those values move around — they can cross, fall back and cross again within a session.
     * Keying on the reason rather than counting deliveries is what makes "send this once, ever"
     * answerable from the save alone, with no separate flag to fall out of step with the inbox.
     */
    public String kind = "";
}
