package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;

/** One thing the operator looked at. See {@code fs/Recents}. */
public final class RecentEntry {

    /** The absolute path, on this machine. */
    public String path = "";

    /** Whether it was a place or a file — drives the marker and what opening it does. */
    public boolean directory = false;

    public Instant at = Instant.now();
}
