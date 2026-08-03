package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;

/**
 * One line of {@code /var/log/remote-access.log} — somebody else touching this rig.
 *
 * <h2>Why the address is stored as a field and not baked into the message</h2>
 *
 * Because the intruder can <b>remove their own lines</b> before they leave, and a redaction that had
 * to parse prose to find an address would be a redaction that sometimes missed one. Structured here,
 * tampered structurally, rendered as text only at the last moment — which is also the only way the
 * player's side can count what is missing (see {@code AccessLog.gaps}).
 */
public final class AccessEntry {

    public Instant at = Instant.now();

    /**
     * The address the intruder came from, or empty once they have wiped it.
     *
     * <p>⚠ Empty is a <b>meaningful</b> value and not an absence. A blanked address is the tell: the
     * line is still there, the sequence number still counts, and what is gone is the one field that
     * would let the victim retaliate. A redaction that deleted the whole row would leave nothing to
     * notice, and noticing is the mechanic.
     */
    public String fromAddress = "";

    /** What they did — {@code read}, {@code copy}. */
    public String action = "";

    /** What they did it to. */
    public String path = "";

    /** Monotonic within a save, so a hole in the sequence is visible after a partial wipe. */
    public long sequence = 0L;
}
