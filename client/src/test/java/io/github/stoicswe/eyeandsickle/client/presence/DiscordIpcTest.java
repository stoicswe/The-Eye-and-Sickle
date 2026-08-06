package io.github.stoicswe.eyeandsickle.client.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The wire format, without a Discord on the machine.
 *
 * <h2>Why the framing is worth a test at all</h2>
 *
 * It is eight bytes and it has exactly one interesting property, which is that it is
 * <b>little-endian</b> — the order nobody guesses for a protocol header. Getting it wrong does not
 * fail: the connection opens, the handshake is accepted, and Discord then sits waiting for a payload
 * sixteen million bytes long that is never coming. The client reports itself as connected and says
 * nothing, forever, on a machine where the feature looks installed.
 */
@DisplayName("discord ipc framing")
class DiscordIpcTest {

    @Nested
    @DisplayName("frames")
    class Frames {

        @Test
        @DisplayName("⚠ the header is two LITTLE-endian ints")
        void headerIsLittleEndian() {
            byte[] framed = DiscordIpc.frame(DiscordIpc.OP_FRAME, "{}");

            // Opcode 1 as little-endian is 01 00 00 00. Asserted byte by byte rather than through
            // the reader, because a reader with the same mistake would agree with it.
            assertThat(Arrays.copyOfRange(framed, 0, 8)).containsExactly(1, 0, 0, 0, 2, 0, 0, 0);
        }

        @Test
        @DisplayName("round-trips opcode and length")
        void roundTrips() {
            byte[] framed = DiscordIpc.frame(DiscordIpc.OP_HANDSHAKE, "{\"v\":1}");
            byte[] header = Arrays.copyOfRange(framed, 0, DiscordIpc.HEADER_BYTES);

            assertThat(DiscordIpc.opcodeOf(header)).isEqualTo(DiscordIpc.OP_HANDSHAKE);
            assertThat(DiscordIpc.lengthOf(header)).isEqualTo(7);
            assertThat(framed).hasSize(DiscordIpc.HEADER_BYTES + 7);
        }

        @Test
        @DisplayName("⚠ the length is BYTES, not characters")
        void lengthCountsBytes() {
            // A payload is UTF-8, so a multi-byte character makes the byte count exceed the string
            // length. Declaring the string length would truncate the frame by exactly the number of
            // non-ASCII characters in it — a bug that would never appear in English testing.
            String payload = "{\"a\":\"éé\"}";
            byte[] framed = DiscordIpc.frame(DiscordIpc.OP_FRAME, payload);
            byte[] header = Arrays.copyOfRange(framed, 0, DiscordIpc.HEADER_BYTES);

            int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
            assertThat(bytes).isGreaterThan(payload.length());
            assertThat(DiscordIpc.lengthOf(header)).isEqualTo(bytes);
            assertThat(framed).hasSize(DiscordIpc.HEADER_BYTES + bytes);
        }
    }

    @Nested
    @DisplayName("the length guard")
    class LengthGuard {

        @Test
        @DisplayName("⚠ refuses a length another process chose for us")
        void refusesAbsurdLengths() throws IOException {
            // The value is read off the wire and handed to `new byte[n]`. Unbounded, a garbled
            // header is an OutOfMemoryError inside the client rather than a dropped connection.
            assertThat(DiscordIpc.requireSaneLength(64)).isEqualTo(64);

            assertThatThrownBy(() -> DiscordIpc.requireSaneLength(Integer.MAX_VALUE))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> DiscordIpc.requireSaneLength(-1)).isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("where it looks")
    class Candidates {

        @Test
        @DisplayName("⚠ a fixed list of exact names, never a directory listing")
        void candidatesAreComposedNotDiscovered() {
            List<String> names = DiscordIpc.candidates();

            assertThat(names).isNotEmpty();
            // Every name is composed from an environment variable and a slot number. The property
            // that matters is that nothing here enumerates a directory: listing $TMPDIR would mean
            // reading the names of every other program's IPC endpoints, which is precisely the
            // fingerprinting docs/client/02 §2.9 says this client never does.
            assertThat(names).allSatisfy(name -> assertThat(name).contains("discord-ipc-"));
            assertThat(names).allSatisfy(name -> {
                String slot = name.substring(name.lastIndexOf('-') + 1);
                assertThat(Integer.parseInt(slot)).isBetween(0, DiscordIpc.SLOTS - 1);
            });
        }

        // ⚠ THERE IS DELIBERATELY NO TEST THAT CALLS connect().
        //
        // It would be the obvious one to write — "with no Discord running it answers null" — and it
        // is untestable here for the reason that makes it dangerous: on a machine where Discord IS
        // running it would not answer null, it would open a real pipe to the developer's own client
        // and hand it a handshake. A test whose behaviour depends on what the person running it has
        // open is a flake, and one that reaches out of the build into a live application is worse
        // than a flake. The null-on-absence contract is exercised through Transport instead, which
        // is what the seam is for.
    }
}
