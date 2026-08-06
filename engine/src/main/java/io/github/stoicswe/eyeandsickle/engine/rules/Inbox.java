package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.MessageState;
import java.util.List;
import java.util.Optional;

/**
 * The rig's inbox: delivery, reading, and claiming what a message carries.
 *
 * <h2>⚠ This is the ENGINE'S inbox and holds nothing anybody else wrote</h2>
 *
 * Every message here was authored by the rules. Player-to-player conversation is Bluesky's, reached
 * through the player's own account, and never enters a save — see {@link MessageState}. The COMS
 * window shows both and that is a presentation decision; they are not the same list and must not
 * become one, because entries in <em>this</em> one are trusted enough to grant items.
 */
public final class Inbox {

    private Inbox() {}

    /**
     * The most messages a save keeps.
     *
     * <p>Trimmed from the FRONT, so the oldest goes first — the same rule the scan history, the
     * contributor record and the client log all follow. A bound is not optional: this is a list that
     * only ever grows, on a save that is written every thirty seconds.
     */
    public static final int LIMIT = 200;

    /**
     * Files a message, newest last.
     *
     * <p>⚠ <b>A message carrying an unclaimed offer is never trimmed away.</b> Trimming is a size
     * bound on history; an unclaimed offer is an entitlement, and dropping one would silently delete
     * something the player was given and had not collected. At 200 messages, with offers being
     * something the rules issue a handful of times per character, the exemption cannot itself become
     * the leak.
     */
    public static void deliver(GameSave save, MessageState message) {
        if (save == null || message == null) {
            return;
        }
        save.messages.add(message);
        while (save.messages.size() > LIMIT) {
            int victim = -1;
            for (int i = 0; i < save.messages.size(); i++) {
                MessageState candidate = save.messages.get(i);
                if (candidate.offerItemType.isBlank() || candidate.offerClaimed) {
                    victim = i;
                    break;
                }
            }
            if (victim < 0) {
                // Every message is holding an unclaimed offer. Refuse to drop one rather than
                // destroy an entitlement to stay under a display limit.
                break;
            }
            save.messages.remove(victim);
        }
    }

    /** Newest first — the order a mail client shows and the order the COMS list renders. */
    public static List<MessageState> newestFirst(GameSave save) {
        if (save == null) {
            return List.of();
        }
        return save.messages.stream()
                .sorted((a, b) -> b.receivedAt.compareTo(a.receivedAt))
                .toList();
    }

    /** How many are unread — what the COMS chip and the notification count show. */
    public static int unread(GameSave save) {
        if (save == null) {
            return 0;
        }
        return (int) save.messages.stream().filter(m -> !m.read).count();
    }

    public static Optional<MessageState> byId(GameSave save, String messageId) {
        if (save == null || messageId == null) {
            return Optional.empty();
        }
        return save.messages.stream()
                .filter(m -> m.messageId.equals(messageId))
                .findFirst();
    }

    /**
     * Marks one message read.
     *
     * @return true if the message existed and was previously unread — which is what tells the caller
     *     whether anything actually changed, so opening an already-read message does not persist the
     *     save or fire a change event.
     */
    public static boolean markRead(GameSave save, String messageId) {
        MessageState m = byId(save, messageId).orElse(null);
        if (m == null || m.read) {
            return false;
        }
        m.read = true;
        return true;
    }

    /**
     * Takes the item a message was carrying, once.
     *
     * <h2>⚠ Clearing the offer is what makes it unrepeatable, and it happens HERE</h2>
     *
     * The caller then does the ordinary thing with the item — a download order, exactly as a purchase
     * would create. Marking the offer claimed before that rather than after is deliberate: a failure
     * downstream loses the item, and a failure the other way round mints one per retry. Losing an
     * entitlement is recoverable by a support conversation; an item printer is not.
     *
     * @return the catalogue id to grant, or empty if there is nothing to claim
     */
    public static Optional<String> claim(GameSave save, String messageId) {
        MessageState m = byId(save, messageId).orElse(null);
        if (m == null || m.offerItemType.isBlank() || m.offerClaimed) {
            return Optional.empty();
        }
        m.offerClaimed = true;
        m.read = true;
        return Optional.of(m.offerItemType);
    }
}
