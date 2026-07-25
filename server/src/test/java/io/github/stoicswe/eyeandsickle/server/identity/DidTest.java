package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The DID value object. Its whole job is to reject at construction anything the database's {@code is_did}
 * CHECK would reject three layers down, so most of these tests are refusals.
 *
 * <p>The class javadoc says this test "pins the shared shape" so the Java regex and the SQL regex cannot
 * drift silently. The shape both enforce is {@code ^did:[a-z0-9]+:[A-Za-z0-9._%:-]+$} with a 512-char
 * bound ({@code docs/architecture/02-identity-and-auth.md} §1, {@code V2__core_schema.sql}
 * {@code is_did}).
 */
class DidTest {

    @Nested
    @DisplayName("accepts well-shaped DIDs")
    class Accepts {

        @Test
        @DisplayName("the plc method the tests use throughout")
        void plc() {
            assertThat(Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa").value())
                    .isEqualTo("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
        }

        @Test
        @DisplayName("is not pinned to one method — did:web is a legitimate AT Proto identity")
        void methodAgnostic() {
            // The regex deliberately does not anchor to did:plc, because pinning the method would reject
            // a real identity (docs/architecture/02 §1).
            assertThatCode(() -> Did.of("did:web:example.test")).doesNotThrowAnyException();
            assertThatCode(() -> Did.of("did:key:z6MkfooBAR")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the method-specific id may itself contain colons and the punctuation the schema allows")
        void richIdentifier() {
            assertThatCode(() -> Did.of("did:web:example.test:8080:user_1-2.3%4"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exactly 512 characters is allowed — the bound is inclusive")
        void atTheLengthBound() {
            String did = "did:plc:" + "a".repeat(512 - "did:plc:".length());
            assertThat(did).hasSize(512);
            assertThatCode(() -> Did.of(did)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("refuses malformed DIDs")
    class Refuses {

        @Test
        @DisplayName("null is a programming error, not a bad DID")
        void nullValue() {
            // Defends against a null flowing in as if it were an absent-but-legitimate identity.
            assertThatThrownBy(() -> Did.of(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a bare handle or URL is not a DID")
        void notADid() {
            // Defends the allowlist and player lookups from a plausible-looking string that would match
            // nothing.
            assertThatThrownBy(() -> Did.of("alice.bsky.social")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Did.of("https://example.test/user")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Did.of("")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the method segment must be lowercase, matching the SQL regex")
        void uppercaseMethodRejected() {
            // is_did uses [a-z0-9]+ for the method; an uppercase method would pass here but fail the DB,
            // which is exactly the drift this validation exists to prevent.
            assertThatThrownBy(() -> Did.of("did:PLC:aaaaaaaaaaaa")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a missing method-specific id is refused")
        void emptyIdentifier() {
            assertThatThrownBy(() -> Did.of("did:plc:")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Did.of("did:plc")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("513 characters is over the bound and refused before the regex even runs")
        void overTheLengthBound() {
            // A DoS bound, matching length(value) <= 512 in the schema. Rejected with a length-specific
            // message rather than a generic shape failure.
            String did = "did:plc:" + "a".repeat(512 - "did:plc:".length() + 1);
            assertThat(did).hasSize(513);
            assertThatThrownBy(() -> Did.of(did))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("512");
        }

        @Test
        @DisplayName("a space anywhere breaks the shape")
        void whitespaceRejected() {
            assertThatThrownBy(() -> Did.of("did:plc:aaa bbb")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Did.of(" did:plc:aaaa")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("optional parsing")
    class OptionalParsing {

        @Test
        @DisplayName("ofNullable keeps null as null — players.did is nullable for local-only solo play")
        void nullPassesThrough() {
            assertThat(Did.ofNullable(null)).isNull();
        }

        @Test
        @DisplayName("ofNullable still validates a non-null value")
        void nonNullStillValidated() {
            assertThat(Did.ofNullable("did:plc:aaaaaaaaaaaa")).isEqualTo(Did.of("did:plc:aaaaaaaaaaaa"));
            assertThatThrownBy(() -> Did.ofNullable("nope")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("toString is the bare value, so a DID logs and concatenates as itself")
    void toStringIsThePlainValue() {
        // Not Did[value=...]; a DID appears in log lines and exception messages verbatim.
        assertThat(Did.of("did:plc:aaaaaaaaaaaa")).hasToString("did:plc:aaaaaaaaaaaa");
    }

    @Test
    @DisplayName("value equality — two DIDs with the same string are the same identity")
    void valueEquality() {
        assertThat(Did.of("did:plc:aaaaaaaaaaaa")).isEqualTo(Did.of("did:plc:aaaaaaaaaaaa"));
        assertThat(Did.of("did:plc:aaaaaaaaaaaa")).isNotEqualTo(Did.of("did:plc:bbbbbbbbbbbb"));
    }
}
