package io.github.stoicswe.eyeandsickle.client.presence;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Discord IPC transport — a local pipe, and no new dependency.
 *
 * <h2>⚠ THIS OPENS NO NETWORK SOCKET. It is a pipe to a program already running on this machine.</h2>
 *
 * Discord's rich-presence protocol is local IPC to the Discord desktop client, which then does the
 * talking. On Windows that is a named pipe, {@code \\.\pipe\discord-ipc-N}; everywhere else it is a
 * Unix domain socket under the runtime or temp directory. Both are reachable from the JDK alone —
 * {@link SocketChannel#open(java.net.ProtocolFamily)} with {@link StandardProtocolFamily#UNIX} has
 * been there since 16, and a named pipe is a {@link RandomAccessFile}.
 *
 * <p>That matters more here than it would in most projects. A third-party RPC library would mean
 * widening {@code client/pom.xml}'s enforcer, adding a jar to all five platform uber jars, and
 * adding supply-chain surface to a repo that publishes unsigned executables — all to send about two
 * hundred bytes down a pipe. Jackson 3 is already on the client classpath, so the JSON is free too.
 *
 * <h2>⚠ Framing: an 8-byte LITTLE-ENDIAN header, then UTF-8 JSON</h2>
 *
 * {@code int32 opcode}, {@code int32 length}, payload. Little-endian is not the network order a
 * reader would assume from the shape of it, and getting it wrong produces a connection that opens,
 * accepts a handshake and is then silently ignored — so {@link #frame} and {@link #readFrame} are
 * package-private and round-tripped by {@code DiscordIpcTest} rather than trusted.
 *
 * <h2>⚠ EVERY REPLY IS DRAINED, or the pipe fills and the feature dies quietly</h2>
 *
 * Discord answers the handshake with a {@code READY} dispatch and answers every command with a
 * result frame. Nothing here wants those, but something has to read them: an unread pipe buffer
 * fills, and the next write blocks forever. So {@link #send} reads its own reply and discards it.
 *
 * <h2>⚠ Blocking reads, on ONE dedicated virtual thread, closed from outside</h2>
 *
 * There is no read timeout on a Unix domain {@code SocketChannel}. The honest description of the
 * failure mode is: if Discord accepts a connection and then never replies, one virtual thread parks
 * forever and rich presence stops updating — nothing else is affected, because
 * {@link RichPresence} owns that thread and touches nothing the game needs. It is not a deadlock the
 * player can reach: closing the channel from another thread unblocks a parked read with
 * {@code AsynchronousCloseException}, which is exactly what {@link #close} does on shutdown and on
 * the toggle being switched off.
 *
 * <h2>⚠ Failure is silent by design</h2>
 *
 * Discord not being installed is the normal case, not an error, and it is indistinguishable from
 * Discord not being open. Neither is anything the player can act on from inside a game, so nothing
 * here ever raises a dialog and nothing logs above {@code FINE}. The Settings panel says whether a
 * connection was made, which is the one place that answer is useful.
 */
final class DiscordIpc implements Transport {

    private static final Logger LOG = Logger.getLogger(DiscordIpc.class.getName());

    static final int OP_HANDSHAKE = 0;
    static final int OP_FRAME = 1;
    static final int OP_CLOSE = 2;

    /** The header is two little-endian ints. */
    static final int HEADER_BYTES = 8;

    /**
     * How many {@code discord-ipc-N} slots to try.
     *
     * <p>Discord's own clients use 0 through 9 — several may be live at once when stable, PTB and
     * Canary are all installed, and the first one that answers is as good as any other.
     */
    static final int SLOTS = 10;

    /**
     * ⚠ A ceiling on a length field we did not write.
     *
     * <p>The header says how many bytes to allocate, and it arrives from another process. A garbled
     * or hostile value would otherwise be an {@code OutOfMemoryError} in the client, so a frame
     * larger than this is treated as a protocol error and drops the connection. Discord's replies
     * are a few hundred bytes; this is generous by three orders of magnitude and still bounded.
     */
    static final int MAX_FRAME_BYTES = 64 * 1024;

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** One end of the pipe, whichever kind it turned out to be. */
    private interface Duct extends Closeable {
        void write(byte[] bytes) throws IOException;

        void readFully(byte[] into) throws IOException;
    }

    private final Duct duct;

    private DiscordIpc(Duct duct) {
        this.duct = duct;
    }

    // ── connecting ─────────────────────────────────────────────────────────────────────────────

    /**
     * Opens the first pipe that answers and completes the handshake.
     *
     * @return the connection, or {@code null} when Discord is not running. ⚠ Null rather than an
     *     exception: not finding Discord is the ordinary case and an exception would make the
     *     ordinary case cost a stack trace on every retry.
     */
    static DiscordIpc connect(String applicationId) {
        for (String candidate : candidates()) {
            Duct duct = open(candidate);
            if (duct == null) {
                continue;
            }
            DiscordIpc ipc = new DiscordIpc(duct);
            try {
                // §handshake: {"v":1,"client_id":"…"}. The reply is the READY dispatch, discarded.
                ipc.write(OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + escape(applicationId) + "\"}");
                ipc.readFrame();
                LOG.log(Level.FINE, "discord ipc connected");
                return ipc;
            } catch (IOException | RuntimeException refused) {
                // ⚠ The message, never the path. A socket path under $TMPDIR carries the OS user
                // name on several platforms, and this log is what ends up in a bug report.
                LOG.log(Level.FINE, "discord ipc handshake failed: {0}", refused.getClass().getSimpleName());
                ipc.closeQuietly();
            }
        }
        return null;
    }

    /**
     * Every pipe name worth trying, in order.
     *
     * <h2>⚠ A FIXED list built from environment variables, never a directory walk</h2>
     *
     * Listing a temp directory to find sockets would mean reading the names of every other
     * program's IPC endpoints — which is the fingerprinting {@code docs/client/02} §2.9 says this
     * client never does. Composing exact names from {@code $XDG_RUNTIME_DIR} and {@code $TMPDIR}
     * asks about one file at a time and learns nothing about anything else in the directory.
     */
    static List<String> candidates() {
        List<String> names = new ArrayList<>();
        if (WINDOWS) {
            for (int slot = 0; slot < SLOTS; slot++) {
                names.add("\\\\.\\pipe\\discord-ipc-" + slot);
            }
            return names;
        }
        List<String> roots = new ArrayList<>();
        for (String variable : List.of("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                roots.add(value);
            }
        }
        roots.add("/tmp");
        // Flatpak and Snap put the socket one directory further down. Both are how a large share of
        // Linux players have Discord installed, so leaving them out means "works on Windows and
        // macOS" wearing a cross-platform label.
        List<String> nests = List.of(
                "", "app/com.discordapp.Discord/", "app/com.discordapp.DiscordCanary/", "snap.discord/");
        for (String root : roots) {
            for (String nest : nests) {
                for (int slot = 0; slot < SLOTS; slot++) {
                    names.add(root + (root.endsWith("/") ? "" : "/") + nest + "discord-ipc-" + slot);
                }
            }
        }
        return names;
    }

    /** Opens one candidate, or {@code null} if nothing is listening there. */
    private static Duct open(String name) {
        try {
            if (WINDOWS) {
                // ⚠ A named pipe is opened as a FILE on Windows. There is no JDK API for one, and
                // RandomAccessFile in "rw" is the long-established way; it fails fast with
                // FileNotFoundException when nothing is listening, which is the common case.
                RandomAccessFile pipe = new RandomAccessFile(name, "rw");
                return new Duct() {
                    @Override
                    public void write(byte[] bytes) throws IOException {
                        pipe.write(bytes);
                    }

                    @Override
                    public void readFully(byte[] into) throws IOException {
                        pipe.readFully(into);
                    }

                    @Override
                    public void close() throws IOException {
                        pipe.close();
                    }
                };
            }
            Path path = Path.of(name);
            // Asked before connecting purely to keep the common case cheap: there are up to 200
            // candidates on Linux and an exists() check is far less than a connect attempt.
            if (!Files.exists(path)) {
                return null;
            }
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(path));
            return new Duct() {
                @Override
                public void write(byte[] bytes) throws IOException {
                    ByteBuffer out = ByteBuffer.wrap(bytes);
                    while (out.hasRemaining()) {
                        channel.write(out);
                    }
                }

                @Override
                public void readFully(byte[] into) throws IOException {
                    ByteBuffer in = ByteBuffer.wrap(into);
                    while (in.hasRemaining()) {
                        if (channel.read(in) < 0) {
                            throw new EOFException("discord closed the pipe");
                        }
                    }
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };
        } catch (IOException | RuntimeException notThere) {
            return null;
        }
    }

    // ── framing ────────────────────────────────────────────────────────────────────────────────

    /**
     * Encodes one frame.
     *
     * <p>⚠ {@link ByteOrder#LITTLE_ENDIAN} explicitly. {@code ByteBuffer} defaults to big-endian,
     * which is the wrong order here and produces a length of roughly sixteen million for a
     * two-hundred-byte payload — so Discord waits for bytes that never come and the client looks
     * connected and mute.
     */
    static byte[] frame(int opcode, String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(HEADER_BYTES + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(opcode)
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    /** The payload length a header declares. Package-private so the round trip is testable. */
    static int lengthOf(byte[] header) {
        return ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
    }

    /** The opcode a header declares. */
    static int opcodeOf(byte[] header) {
        return ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(0);
    }

    /**
     * Refuses a length this side did not choose.
     *
     * <p>⚠ Extracted so it is reachable from a test without a live pipe. The value comes off the wire
     * from another process and is fed straight to {@code new byte[n]} — a garbled or hostile header
     * would otherwise be an {@code OutOfMemoryError} inside the client, and a negative one an
     * exception naming an array size rather than a protocol fault.
     */
    static int requireSaneLength(int length) throws IOException {
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("discord ipc frame length out of range: " + length);
        }
        return length;
    }

    private void write(int opcode, String json) throws IOException {
        duct.write(frame(opcode, json));
    }

    /** Reads one whole frame and returns its payload. */
    private String readFrame() throws IOException {
        byte[] header = new byte[HEADER_BYTES];
        duct.readFully(header);
        int length = requireSaneLength(lengthOf(header));
        byte[] payload = new byte[length];
        duct.readFully(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }

    // ── using it ───────────────────────────────────────────────────────────────────────────────

    /**
     * Sends one command and drains its reply.
     *
     * @throws IOException when the pipe has gone. ⚠ Thrown rather than swallowed, because the caller
     *     needs to know to drop the connection — swallowing here would leave {@link RichPresence}
     *     writing into a dead pipe forever and reporting itself as connected.
     */
    @Override
    public void send(String json) throws IOException {
        write(OP_FRAME, json);
        readFrame();
    }

    @Override
    public void close() throws IOException {
        try {
            write(OP_CLOSE, "{}");
        } catch (IOException alreadyGone) {
            // Nothing to do and nothing to say: we are closing either way.
            LOG.log(Level.FINEST, "discord ipc close frame not delivered");
        }
        duct.close();
    }

    private void closeQuietly() {
        try {
            duct.close();
        } catch (IOException ignored) {
            LOG.log(Level.FINEST, "discord ipc close failed");
        }
    }

    /**
     * Escapes a string for the one place this file builds JSON by hand.
     *
     * <p>⚠ The handshake is written as a literal because it predates having a mapper in scope and is
     * two fields wide — but the application id comes from a system property, so it is still
     * untrusted input in the sense that matters: a quote in it would produce a malformed handshake
     * that fails in a way nobody could read. Everything else on this transport is serialised by
     * Jackson in {@link RichPresence}.
     */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c >= ' ' && c < 0x7f) {
                out.append(c);
            }
            // Anything else is dropped. An application id is 18-19 decimal digits; a control
            // character or a stray Unicode point in one is a typo, not something to transmit.
        }
        return out.toString();
    }
}
