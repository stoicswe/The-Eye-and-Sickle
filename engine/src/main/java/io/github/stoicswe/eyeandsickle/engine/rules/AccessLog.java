package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.state.AccessEntry;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The rig's record of who has been on it, and the intruder's ability to edit that record.
 *
 * <h2>⚠ [PROPOSAL] — the mechanic, not just the plumbing</h2>
 *
 * This adds a <b>counter-forensics loop</b> the design documents do not have: an intruder copies
 * something, the theft is logged with the address they came from, and the intruder may then wipe
 * that address before leaving. The victim's investigation is therefore against an adversary who gets
 * to edit the evidence. Logged in {@code docs/design/15-open-questions.md}; it belongs beside
 * {@code docs/design/12-identity-and-social.md}'s evidence model, which it is a small sibling of.
 *
 * <h2>⚠ Nothing here can fire in single player, and that is correct rather than incomplete</h2>
 *
 * Every writer is a <em>remote actor</em>, and solo has none. A solo player's log is empty for the
 * life of the character. It still exists, is still listed in {@code /var/log}, and is still readable
 * — because a log that materialised only once something had happened would be a log nobody had
 * learned to check, and the habit is the point. The rules are written and tested now so that the day
 * multiplayer lands (<b>CL-8</b>) no engine change is needed.
 *
 * <h2>⚠ The tier still decides what can be taken — §6 is not bypassed</h2>
 *
 * An upgrade is visible inside an application bundle, but {@link #canTake} answers from the item's
 * <b>storage tier</b>, exactly as {@code docs/design/01-core-resources.md} §6 says. A vault item is
 * not takeable however deep an intruder navigates. Without that rule the Applications folder would
 * be a fourth exposure surface that routed around the vault, and the vault being genuinely safe is
 * what the whole risk economy is priced against (Invariant <b>I12</b>).
 */
public final class AccessLog {

    private AccessLog() {}

    /** Newest last, the way a log reads. */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    /** What a wiped address is rendered as. Visible, so the hole is the evidence. */
    public static final String REDACTED = "-.-.-.-";

    /**
     * Whether a remote actor may copy an item off this rig.
     *
     * <p>The tier's answer and nothing else (§6): the vault is never exposed, standard storage is
     * exposed while the owner is online, and the high-hackable zone is always exposed. The
     * {@code ownerOnline} argument is what makes the middle tier mean anything — pass it honestly.
     */
    public static boolean canTake(ItemState item, boolean ownerOnline) {
        if (item == null) {
            return false;
        }
        return switch (item.tier) {
            case "VAULT" -> false;
            case "STANDARD_STORAGE" -> ownerOnline;
            case "HIGH_HACKABLE_ZONE" -> true;
            // An unknown tier from a hand-edited save is treated as the safe answer. Guessing
            // "exposed" would make a corrupted field into a theft.
            default -> false;
        };
    }

    /**
     * Records an access. Appends; never rewrites.
     *
     * @param fromAddress the intruder's address — the thing the victim needs in order to retaliate,
     *     and the thing {@link #redact} exists to remove
     */
    public static AccessEntry record(GameSave save, String fromAddress, String action, String path, Instant now) {
        AccessEntry entry = new AccessEntry();
        entry.at = now;
        entry.fromAddress = fromAddress == null ? "" : fromAddress.trim();
        entry.action = action == null ? "" : action;
        entry.path = path == null ? "" : path;
        entry.sequence = nextSequence(save);
        save.remoteAccessLog.add(entry);
        return entry;
    }

    /**
     * Wipes one address from the log — what an intruder does on the way out.
     *
     * <h2>⚠ It blanks the address; it does NOT delete the line</h2>
     *
     * A deletion would leave nothing to notice, and noticing is the entire mechanic. What survives a
     * wipe is the timestamp, the action, the path and the sequence number — so the victim can still
     * see <em>that</em> they were robbed, <em>when</em>, and <em>of what</em>. What they lose is the
     * one field that would let them hit back.
     *
     * <p>Deleting rows outright is the obvious "improvement" and it is the wrong one: it converts a
     * legible crime into a missing file, and a player who cannot tell they were robbed has no reason
     * to investigate anything.
     *
     * @return how many lines were wiped
     */
    public static int redact(GameSave save, String fromAddress) {
        String wanted = fromAddress == null ? "" : fromAddress.trim();
        if (save == null || wanted.isEmpty()) {
            return 0;
        }
        int wiped = 0;
        for (AccessEntry entry : save.remoteAccessLog) {
            if (wanted.equals(entry.fromAddress)) {
                entry.fromAddress = "";
                wiped++;
            }
        }
        return wiped;
    }

    /** How many lines have had their address wiped — the count of things you cannot hit back at. */
    public static long gaps(GameSave save) {
        return save == null
                ? 0
                : save.remoteAccessLog.stream()
                        .filter(e -> e.fromAddress.isBlank())
                        .count();
    }

    /** Distinct addresses still in the log — everyone who did not clean up after themselves. */
    public static List<String> attackers(GameSave save) {
        if (save == null) {
            return List.of();
        }
        return save.remoteAccessLog.stream()
                .map(e -> e.fromAddress)
                .filter(a -> !a.isBlank())
                .distinct()
                .toList();
    }

    /**
     * The log as the player reads it — one line per entry, oldest first.
     *
     * <p>⚠ The sequence number is printed. It is what makes a partial wipe visible as a wipe rather
     * than as a quiet day: the numbers still run 1, 2, 3 and the addresses do not.
     */
    public static List<String> render(GameSave save) {
        List<String> out = new ArrayList<>();
        out.add("# " + VirtualFs.ACCESS_LOG + " — every remote access to this rig.");
        out.add("# seq  when                  from             action  path");
        if (save == null || save.remoteAccessLog.isEmpty()) {
            out.add("");
            out.add("(nothing. No one has been on this machine but you.)");
            return List.copyOf(out);
        }
        for (AccessEntry entry : save.remoteAccessLog) {
            out.add(String.format(
                    java.util.Locale.ROOT,
                    "%-5d %s  %-16s %-7s %s",
                    entry.sequence,
                    STAMP.format(entry.at),
                    entry.fromAddress.isBlank() ? REDACTED : entry.fromAddress,
                    entry.action,
                    entry.path));
        }
        long gaps = gaps(save);
        if (gaps > 0) {
            out.add("");
            // Stated plainly. The player is entitled to know the log was edited, because that fact
            // is itself evidence — somebody who wipes an address knew there was one to wipe.
            // ⚠ ASCII only in EMITTED text. The warning sign U+26A0 is in neither bundled face —
            // GlyphCoverageTest catches it — so it would be drawn by a host-OS fallback, on a line
            // whose whole job is to be noticed.
            out.add("!! " + gaps + " " + (gaps == 1 ? "entry has" : "entries have")
                    + " had the source address removed. Something was here and cleaned up after "
                    + "itself; the sequence numbers are intact, so nothing was deleted outright.");
        }
        return List.copyOf(out);
    }

    public static int size(GameSave save) {
        return save == null || save.remoteAccessLog == null ? 0 : save.remoteAccessLog.size();
    }

    private static long nextSequence(GameSave save) {
        return save.remoteAccessLog.stream().mapToLong(e -> e.sequence).max().orElse(0L) + 1L;
    }
}
