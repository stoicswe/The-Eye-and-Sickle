package io.github.stoicswe.eyeandsickle.server.federation;

import java.util.UUID;

/** Thrown when adjudication or a status query names a duel this server never opened. */
public class DuelNotFoundException extends RuntimeException {

    public DuelNotFoundException(UUID duelId) {
        super("No duel " + duelId + " on this server");
    }
}
