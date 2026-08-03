package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;

/**
 * One open shell session, persisted so a reopened game finds the same windows and the same
 * working directories.
 *
 * <h2>⚠ The allocation id is the link, and it is why a session cannot leak compute</h2>
 *
 * Opening a session reserves cycles and records the resulting allocation here. Closing it releases
 * <em>that</em> allocation by id. Without the id the release would have to match on consumer and
 * label, and two sessions on hosts with the same label would release each other's — leaving one
 * session holding cycles nothing would ever give back, which on a rig of 100 is a permanent tax the
 * player cannot see the cause of.
 */
public final class SessionState {

    /** The machine. Empty is not valid; the player's own rig has its own address. */
    public String address = "";

    /** Where this session is, in the machine's filesystem. Persisted so a reopen resumes. */
    public String cwd = "/";

    public Instant openedAt = Instant.now();

    /** The compute reservation this session holds. See the class comment. */
    public String allocationId = "";

    /** What the session cost to open, so a readout need not re-derive it from Balance. */
    public long cycles = 0L;
}
