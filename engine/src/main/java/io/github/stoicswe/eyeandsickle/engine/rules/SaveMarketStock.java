package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;

/**
 * The single-player {@link MarketStock.Held} — the shelf, kept in the character's own save.
 *
 * <h2>⚠ Why per-character is correct HERE and wrong on a server</h2>
 *
 * Stock is world state. In solo the world has exactly one player, so per-character and per-world are
 * the same set and there is nothing to share. On a server they diverge immediately: every player
 * draws from one shelf, and the whole point of a limited item is that somebody else can get there
 * first. A server therefore backs this with its own table rather than reusing this class — see
 * {@code MarketStock.Held} and {@code W-7}.
 *
 * <p>⚠ <b>This is not a security boundary and must not be mistaken for one.</b> A solo save is the
 * player's own file and they may edit the counts; nothing downstream trusts them, because a solo
 * character can never federate (the quarantine rule). The same class on a server would be a client
 * deciding its own stock, which is Invariant I14 — which is precisely why the server does not use it.
 */
public final class SaveMarketStock implements MarketStock.Held {

    private final GameSave save;

    public SaveMarketStock(GameSave save) {
        this.save = save;
    }

    @Override
    public int taken(String offeringId, long day) {
        return save.marketTaken.getOrDefault(key(offeringId, day), 0);
    }

    @Override
    public void take(String offeringId, long day) {
        // ⚠ Prunes every other day in the same pass. Keys are day-scoped and never read again once
        // the shelf restocks, so without this a save played daily for a year carries a few thousand
        // dead entries — not a size problem, but a save file a human can no longer read, and this
        // one is meant to be readable.
        String suffix = "@" + day;
        save.marketTaken.keySet().removeIf(existing -> !existing.endsWith(suffix));
        save.marketTaken.merge(key(offeringId, day), 1, Integer::sum);
    }

    private static String key(String offeringId, long day) {
        return offeringId + "@" + day;
    }
}
