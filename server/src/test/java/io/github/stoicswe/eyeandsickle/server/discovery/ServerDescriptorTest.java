package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ServerDescriptor} value type.
 *
 * <p>A descriptor is the one object the discovery layer trusts, and it exists only after verification.
 * These tests pin the two things that make it safe to hold: it is an immutable snapshot (a caller cannot
 * reach in and mutate the transport key bytes after the fact), and its transport-key validity window is
 * a closed-open interval evaluated against an injected instant, never a wall clock.
 */
class ServerDescriptorTest {

    private static final byte[] KEY = X25519KeyExchange.encodePublicKey(
            X25519KeyExchange.generateKeyPair().getPublic());

    private static ServerDescriptor descriptor(
            long sequence, Instant notBefore, Instant notAfter, List<String> capabilities) {
        return new ServerDescriptor(
                "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa",
                "https://home.example.org",
                KEY,
                "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa#transport-1",
                notBefore,
                notAfter,
                sequence,
                capabilities,
                null,
                "{\"descriptor\":{}}");
    }

    @Nested
    @DisplayName("construction and immutability")
    class Construction {

        @Test
        @DisplayName("the transport key is defensively copied in, so a later mutation of the source cannot alter it")
        void copiesKeyIn() {
            byte[] source = KEY.clone();
            ServerDescriptor descriptor = new ServerDescriptor(
                    "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa",
                    "https://home.example.org",
                    source,
                    null,
                    null,
                    null,
                    3,
                    List.of(),
                    null,
                    "{\"descriptor\":{}}");

            // A held descriptor whose bytes could be rewritten by whoever passed them in would let the
            // transport key drift away from the one the signature covered.
            source[0] ^= 0x7F;
            assertThat(descriptor.transportPublicKey()).isEqualTo(KEY).isNotEqualTo(source);
        }

        @Test
        @DisplayName("the accessor hands back a copy, so a caller cannot mutate the descriptor's key")
        void copiesKeyOut() {
            ServerDescriptor descriptor = descriptor(1, null, null, List.of());

            byte[] leaked = descriptor.transportPublicKey();
            leaked[0] ^= 0x7F;

            assertThat(descriptor.transportPublicKey()).isEqualTo(KEY).isNotEqualTo(leaked);
        }

        @Test
        @DisplayName("null capabilities become an empty list, and the list is an unmodifiable copy")
        void capabilitiesAreCopiedAndDefaulted() {
            assertThat(descriptor(1, null, null, null).capabilities()).isEmpty();

            List<String> source = new ArrayList<>(List.of("federation"));
            ServerDescriptor descriptor = descriptor(1, null, null, source);
            source.add("validator"); // must not leak into the descriptor

            assertThat(descriptor.capabilities()).containsExactly("federation");
            assertThatThrownBy(() -> descriptor.capabilities().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("a negative sequence is refused at construction (the schema forbids it too)")
        void negativeSequenceRejected() {
            // Catching it here means a caller building a descriptor by hand fails at construction rather
            // than at the INSERT, where the CHECK constraint would otherwise bite.
            assertThatThrownBy(() -> descriptor(-1, null, null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sequenceNumber");
        }

        @Test
        @DisplayName("the required fields reject null")
        void requiredFieldsRejectNull() {
            assertThatThrownBy(() ->
                            new ServerDescriptor(null, "https://x", KEY, null, null, null, 1, List.of(), null, "{}"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ServerDescriptor(
                            "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa", null, KEY, null, null, null, 1, List.of(), null, "{}"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ServerDescriptor(
                            "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa",
                            "https://x",
                            null,
                            null,
                            null,
                            null,
                            1,
                            List.of(),
                            null,
                            "{}"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ServerDescriptor(
                            "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa",
                            "https://x",
                            KEY,
                            null,
                            null,
                            null,
                            1,
                            List.of(),
                            null,
                            null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("declares")
    class Declares {

        @Test
        @DisplayName("reports a declared capability and denies an undeclared one")
        void declares() {
            ServerDescriptor descriptor = descriptor(1, null, null, List.of(ServerDescriptor.CAPABILITY_FEDERATION));

            assertThat(descriptor.declares(ServerDescriptor.CAPABILITY_FEDERATION))
                    .isTrue();
            // Advisory only — declaring "validator" is never a grant of authority, but it must at least
            // be reported honestly.
            assertThat(descriptor.declares(ServerDescriptor.CAPABILITY_VALIDATOR))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("transportKeyValidAt — a closed-open [notBefore, notAfter) window")
    class KeyWindow {

        private static final Instant NB = Instant.parse("2026-07-24T00:00:00Z");
        private static final Instant NA = Instant.parse("2026-07-31T00:00:00Z");

        @Test
        @DisplayName("an unbounded key is always valid")
        void unboundedIsAlwaysValid() {
            ServerDescriptor descriptor = descriptor(1, null, null, List.of());
            assertThat(descriptor.transportKeyValidAt(Instant.EPOCH)).isTrue();
            assertThat(descriptor.transportKeyValidAt(Instant.parse("3000-01-01T00:00:00Z")))
                    .isTrue();
        }

        @Test
        @DisplayName("before notBefore is not valid; exactly at notBefore is valid")
        void notBeforeBoundary() {
            ServerDescriptor descriptor = descriptor(1, NB, NA, List.of());
            assertThat(descriptor.transportKeyValidAt(NB.minusSeconds(1))).isFalse();
            assertThat(descriptor.transportKeyValidAt(NB)).isTrue();
        }

        @Test
        @DisplayName("exactly at notAfter is NOT valid; the instant before it is")
        void notAfterBoundary() {
            ServerDescriptor descriptor = descriptor(1, NB, NA, List.of());
            assertThat(descriptor.transportKeyValidAt(NA.minusSeconds(1))).isTrue();
            // The window is half-open on the right: a key is dead the instant it expires, not one tick
            // later, so an expired-at-exactly-now key cannot be dialled.
            assertThat(descriptor.transportKeyValidAt(NA)).isFalse();
            assertThat(descriptor.transportKeyValidAt(NA.plusSeconds(1))).isFalse();
        }

        @Test
        @DisplayName("only notAfter set: valid until it, unbounded before")
        void onlyNotAfter() {
            ServerDescriptor descriptor = descriptor(1, null, NA, List.of());
            assertThat(descriptor.transportKeyValidAt(Instant.EPOCH)).isTrue();
            assertThat(descriptor.transportKeyValidAt(NA)).isFalse();
        }

        @Test
        @DisplayName("now is required")
        void nowRequired() {
            ServerDescriptor descriptor = descriptor(1, NB, NA, List.of());
            assertThatThrownBy(() -> descriptor.transportKeyValidAt(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("transportKey() decodes the stored bytes, and re-encoding round-trips them")
    void transportKeyDecodes() {
        ServerDescriptor descriptor = descriptor(1, null, null, List.of());
        // Assert the round-trip rather than the algorithm name: an XDH key's getAlgorithm() is
        // JDK-dependent ("X25519" vs "XDH"), the same family/curve-name trap Ed25519 keys have.
        assertThat(descriptor.transportKey()).isNotNull();
        assertThat(X25519KeyExchange.encodePublicKey(descriptor.transportKey())).isEqualTo(KEY);
    }
}
