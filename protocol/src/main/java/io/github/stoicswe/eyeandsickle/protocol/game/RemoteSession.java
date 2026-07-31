package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;

/**
 * An open shell session on a machine.
 *
 * <h2>⚠ A session is NOT the vantage, and merging them breaks the reach model</h2>
 *
 * The vantage is the single point a sweep measures hop distance from, and
 * {@code docs/design/07-recon-tools.md} §2 makes that reach a hard ceiling that no purchase moves
 * (Invariant <b>I2</b>). A session is just a shell you have open on a machine you already hold. You
 * may hold many at once; you have exactly one vantage, and moving it stays the deliberate,
 * separate act it has always been.
 *
 * <p>If a session ever became a vantage, reach would silently multiply by the number of windows a
 * player had open — which is the ceiling being sold for the price of a click.
 *
 * @param address the machine
 * @param label its name, when one is known
 * @param cwd the working directory this session is in — persisted, so a reopened window resumes
 * @param openedAt when it was opened
 * @param cycles compute this session is holding for as long as it stays open
 * @param self whether this is the player's own rig, which is always reachable and never a foothold
 */
public record RemoteSession(String address, String label, String cwd, Instant openedAt, long cycles, boolean self) {

    /** What the session's prompt and window title show. Falls back to the address, never to blank. */
    public String displayName() {
        return label == null || label.isBlank() ? address : label;
    }
}
