package io.github.stoicswe.eyeandsickle.client.presence;

import java.io.Closeable;
import java.io.IOException;

/**
 * Somewhere to put a command frame.
 *
 * <p>Exists so {@link RichPresence} can be driven without a Discord client on the machine. That is
 * not a testing convenience bolted on afterwards — it is what makes {@code PresenceLeakTest}
 * possible at all, and that test is the mechanical half of this feature's privacy claim. A presence
 * layer that could only be exercised by looking at somebody's Discord window would have its most
 * important property checked by eye, once, on one machine.
 */
interface Transport extends Closeable {

    /** Sends one command and drains its reply. Throws when the far end has gone. */
    void send(String json) throws IOException;
}
