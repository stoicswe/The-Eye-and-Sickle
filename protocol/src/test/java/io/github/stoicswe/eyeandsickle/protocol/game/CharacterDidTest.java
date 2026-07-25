package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The derived per-character identity (09 §9, Q-item-keying option 3). The load-bearing behaviours: the
 * exact {@code did:eyeandsickle:<slot>:<accountDid>} format, an unambiguous parse even though the account
 * DID itself contains colons, an exact round-trip in both directions, rejection of malformed inputs, and
 * the whole string staying DID-shaped so it needs no schema change.
 */
class CharacterDidTest {

    private static final String ACCOUNT = "did:plc:abcd1234";

    @Nested
    @DisplayName("of / build the string")
    class Build {

        @Test
        @DisplayName("builds the exact documented format, slot first")
        void exactFormat() {
            assertThat(CharacterDid.of(ACCOUNT, 2)).isEqualTo("did:eyeandsickle:2:did:plc:abcd1234");
        }

        @Test
        @DisplayName("the account DID is carried verbatim, colons and all")
        void accountDidVerbatim() {
            // The account DID has three colons of its own; slot-first framing means none of them confuse
            // the parse. did:web accounts (more colons) are the reason this matters.
            String webAccount = "did:web:home.example.com:user:alice";
            assertThat(CharacterDid.of(webAccount, 1)).isEqualTo("did:eyeandsickle:1:" + webAccount);
        }

        @Test
        @DisplayName("value() equals of() for the same parts")
        void valueEqualsOf() {
            assertThat(new CharacterDid(ACCOUNT, 3).value()).isEqualTo(CharacterDid.of(ACCOUNT, 3));
        }

        @Test
        @DisplayName("toString is the canonical string, not the record layout")
        void toStringIsValue() {
            assertThat(new CharacterDid(ACCOUNT, 2)).hasToString("did:eyeandsickle:2:did:plc:abcd1234");
        }

        @Test
        @DisplayName("the accessors expose the parts")
        void accessors() {
            CharacterDid cd = new CharacterDid(ACCOUNT, 2);
            assertThat(cd.accountDid()).isEqualTo(ACCOUNT);
            assertThat(cd.slot()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("of / validation")
    class BuildValidation {

        @Test
        @DisplayName("a blank or null account DID is refused — a local character has no character DID")
        void blankAccount() {
            assertThatThrownBy(() -> CharacterDid.of(null, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CharacterDid.of("", 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CharacterDid.of("   ", 1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("an account that is not DID-shaped is refused")
        void notDidShaped() {
            assertThatThrownBy(() -> CharacterDid.of("not-a-did", 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CharacterDid.of("plc:abc", 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CharacterDid.of("did:UPPER:abc", 1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a slot below 1 is not a slot")
        void nonPositiveSlot() {
            assertThatThrownBy(() -> CharacterDid.of(ACCOUNT, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CharacterDid.of(ACCOUNT, -1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CharacterDid(ACCOUNT, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a full value over the 512-char bound is refused")
        void overLength() {
            String hugeAccount = "did:plc:" + "a".repeat(600);
            assertThatThrownBy(() -> CharacterDid.of(hugeAccount, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("parse / round-trip")
    class RoundTrip {

        @Test
        @DisplayName("parse reconstructs the parts that of built")
        void parseReconstructs() {
            CharacterDid parsed =
                    CharacterDid.parse(CharacterDid.of(ACCOUNT, 2)).orElseThrow();
            assertThat(parsed.accountDid()).isEqualTo(ACCOUNT);
            assertThat(parsed.slot()).isEqualTo(2);
            assertThat(parsed).isEqualTo(new CharacterDid(ACCOUNT, 2));
        }

        @Test
        @DisplayName("build -> parse -> build reproduces the exact string")
        void stringRoundTrip() {
            String built = CharacterDid.of("did:web:home.example.com:user:alice", 7);
            CharacterDid parsed = CharacterDid.from(built);
            assertThat(CharacterDid.of(parsed.accountDid(), parsed.slot())).isEqualTo(built);
            assertThat(parsed.value()).isEqualTo(built);
        }

        @Test
        @DisplayName("two-digit slots survive the round-trip")
        void multiDigitSlot() {
            CharacterDid parsed = CharacterDid.from(CharacterDid.of(ACCOUNT, 13));
            assertThat(parsed.slot()).isEqualTo(13);
            assertThat(parsed.accountDid()).isEqualTo(ACCOUNT);
        }

        @Test
        @DisplayName("the account DID's own colons are all kept on the account side of the split")
        void colonsStayWithAccount() {
            String account = "did:web:a:b:c:d";
            CharacterDid parsed = CharacterDid.from(CharacterDid.of(account, 2));
            assertThat(parsed.accountDid()).isEqualTo(account);
            assertThat(parsed.slot()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("parse / malformed inputs return empty")
    class Malformed {

        @Test
        @DisplayName("null and empty")
        void nullAndEmpty() {
            assertThat(CharacterDid.parse(null)).isEmpty();
            assertThat(CharacterDid.parse("")).isEmpty();
        }

        @Test
        @DisplayName("a plain account DID is not a character DID")
        void plainAccountDid() {
            assertThat(CharacterDid.parse("did:plc:abcd1234")).isEmpty();
            assertThat(CharacterDid.parse("did:web:example.com")).isEmpty();
        }

        @Test
        @DisplayName("the wrong method is not a character DID")
        void wrongMethod() {
            assertThat(CharacterDid.parse("did:other:2:did:plc:abcd1234")).isEmpty();
        }

        @Test
        @DisplayName("a missing slot separator")
        void missingSeparator() {
            // Prefix present but nothing after the slot, or no colon between slot and account.
            assertThat(CharacterDid.parse("did:eyeandsickle:2")).isEmpty();
            assertThat(CharacterDid.parse("did:eyeandsickle:")).isEmpty();
        }

        @Test
        @DisplayName("an empty slot or an empty account DID")
        void emptySegments() {
            assertThat(CharacterDid.parse("did:eyeandsickle::did:plc:abcd1234")).isEmpty();
            assertThat(CharacterDid.parse("did:eyeandsickle:2:")).isEmpty();
        }

        @Test
        @DisplayName("a non-numeric slot")
        void nonNumericSlot() {
            assertThat(CharacterDid.parse("did:eyeandsickle:abc:did:plc:abcd1234"))
                    .isEmpty();
            assertThat(CharacterDid.parse("did:eyeandsickle:2a:did:plc:abcd1234"))
                    .isEmpty();
        }

        @Test
        @DisplayName("a non-canonical slot spelling — leading zero, sign, or whitespace")
        void nonCanonicalSlot() {
            // These parse to a number but of() would never emit them, so accepting them would break the
            // exact round-trip: two strings claiming one slot.
            assertThat(CharacterDid.parse("did:eyeandsickle:02:did:plc:abcd1234"))
                    .isEmpty();
            assertThat(CharacterDid.parse("did:eyeandsickle:+2:did:plc:abcd1234"))
                    .isEmpty();
            assertThat(CharacterDid.parse("did:eyeandsickle: 2:did:plc:abcd1234"))
                    .isEmpty();
        }

        @Test
        @DisplayName("slot zero and negative")
        void zeroAndNegativeSlot() {
            assertThat(CharacterDid.parse("did:eyeandsickle:0:did:plc:abcd1234"))
                    .isEmpty();
            // '-' is not a digit, so the whole slot token is non-numeric.
            assertThat(CharacterDid.parse("did:eyeandsickle:-1:did:plc:abcd1234"))
                    .isEmpty();
        }

        @Test
        @DisplayName("a slot too large for an int")
        void slotOverflow() {
            assertThat(CharacterDid.parse("did:eyeandsickle:99999999999999:did:plc:abcd1234"))
                    .isEmpty();
        }

        @Test
        @DisplayName("an account DID part that is not itself DID-shaped")
        void malformedAccountPart() {
            assertThat(CharacterDid.parse("did:eyeandsickle:2:not-a-did")).isEmpty();
        }

        @Test
        @DisplayName("from() throws on a malformed string")
        void fromThrows() {
            assertThatThrownBy(() -> CharacterDid.from("did:plc:abcd1234"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CharacterDid.from(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isCharacterDid")
    class IsCharacterDid {

        @Test
        @DisplayName("true for a well-formed character DID")
        void trueForCharacterDid() {
            assertThat(CharacterDid.isCharacterDid("did:eyeandsickle:2:did:plc:abcd1234"))
                    .isTrue();
            assertThat(CharacterDid.isCharacterDid(CharacterDid.of(ACCOUNT, 1))).isTrue();
        }

        @Test
        @DisplayName("false for a plain account DID, null, and malformed strings")
        void falseOtherwise() {
            assertThat(CharacterDid.isCharacterDid("did:plc:abcd1234")).isFalse();
            assertThat(CharacterDid.isCharacterDid(null)).isFalse();
            assertThat(CharacterDid.isCharacterDid("did:eyeandsickle:abc:did:plc:x"))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("stays DID-shaped so it needs no schema change")
    class DidShaped {

        /** The same shape the schema's {@code is_did} and the server's {@code Did} enforce. */
        private static final java.util.regex.Pattern IS_DID =
                java.util.regex.Pattern.compile("^did:[a-z0-9]+:[A-Za-z0-9._%:-]+$");

        @Test
        @DisplayName("the full character DID passes the is_did shape check")
        void passesIsDid() {
            assertThat(CharacterDid.of(ACCOUNT, 2)).matches(IS_DID.asMatchPredicate());
            assertThat(CharacterDid.of("did:web:home.example.com:user:alice", 16))
                    .matches(IS_DID.asMatchPredicate());
        }
    }
}
