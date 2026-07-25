package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The allowlist configuration binding. The load-bearing property is <em>closed by default</em>: an unset
 * {@code enabled} is enforcement ON, never "no config, let everyone in". Seed DIDs are parsed defensively
 * and a malformed one fails loudly rather than being silently dropped.
 */
class AllowlistPropertiesTest {

    private static final String DID_A = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DID_B = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";

    @Nested
    @DisplayName("closed by default")
    class ClosedByDefault {

        @Test
        @DisplayName("an unset 'enabled' is treated as enforced")
        void unsetEnabledIsEnforced() {
            // The dangerous misreading — an unset value silently opening the server — is exactly what the
            // boxed Boolean defaulting to true rules out.
            assertThat(new AllowlistProperties(null, null).isEnforced()).isTrue();
        }

        @Test
        @DisplayName("an unset DID list is empty, so a fresh server admits nobody")
        void unsetDidsIsEmpty() {
            assertThat(new AllowlistProperties(null, null).parsedDids()).isEmpty();
        }

        @Test
        @DisplayName("enforcement can be explicitly turned off — a chosen openness")
        void explicitlyOpen() {
            assertThat(new AllowlistProperties(false, List.of()).isEnforced()).isFalse();
        }

        @Test
        @DisplayName("explicitly enabled is enforced")
        void explicitlyEnabled() {
            assertThat(new AllowlistProperties(true, List.of()).isEnforced()).isTrue();
        }
    }

    @Nested
    @DisplayName("seed parsing")
    class SeedParsing {

        @Test
        @DisplayName("a plain list of DIDs is parsed one per entry")
        void plainList() {
            assertThat(new AllowlistProperties(true, List.of(DID_A, DID_B)).parsedDids())
                    .containsExactly(Did.of(DID_A), Did.of(DID_B));
        }

        @Test
        @DisplayName("a single comma-joined value — the environment-variable shape — is split")
        void commaJoinedSingleValue() {
            // A single comma-separated env var binds as one list element; parsedDids splits it defensively
            // so it still yields one Did per identity.
            assertThat(new AllowlistProperties(true, List.of(DID_A + "," + DID_B)).parsedDids())
                    .containsExactly(Did.of(DID_A), Did.of(DID_B));
        }

        @Test
        @DisplayName("surrounding whitespace and empty pieces are ignored")
        void whitespaceAndEmpties() {
            assertThat(new AllowlistProperties(true, List.of("  " + DID_A + " , , " + DID_B + "  ")).parsedDids())
                    .containsExactly(Did.of(DID_A), Did.of(DID_B));
        }

        @Test
        @DisplayName("a malformed seed DID stops startup rather than being silently dropped")
        void malformedSeedFailsLoud() {
            // A typo in the one list that decides who may play is worth stopping startup over.
            assertThatThrownBy(() -> new AllowlistProperties(true, List.of("not-a-did")).parsedDids())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a null element is rejected at binding — the list is defensively copied")
        void nullElementRejected() {
            // The record copies the incoming list with List.copyOf, which refuses null elements, so a
            // malformed binding cannot smuggle a null seed past construction.
            assertThatThrownBy(() -> new AllowlistProperties(true, Arrays.asList(DID_A, null, DID_B)))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
