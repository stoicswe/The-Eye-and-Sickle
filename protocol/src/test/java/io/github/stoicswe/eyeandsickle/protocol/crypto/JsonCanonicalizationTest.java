package io.github.stoicswe.eyeandsickle.protocol.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Conformance tests for {@link JsonCanonicalization} against the RFC 8785 published examples.
 *
 * <h2>Why this file is worth its length</h2>
 *
 * Canonicalization is the only step in the provenance stack whose correctness is not self-evident
 * from a passing round trip. Sign-then-verify on one machine passes even if the canonicalizer is
 * wrong, as long as it is <em>consistently</em> wrong — the bug only appears when a second
 * implementation shows up.
 *
 * <p>In a federation that is the worst possible failure mode. If a Java home server and, say, a Go
 * one canonicalize {@code 1E30} differently, every item minted on one fails verification on the
 * other. Nothing reports "canonicalization mismatch": the verifier reports an invalid signature, the
 * item's chain is not recognized ({@code docs/architecture/03-server-and-federation.md} §4), and the
 * player looks exactly like someone passing off fabricated loot. Whole servers would be
 * reputation-slashed over a floating-point formatting difference.
 *
 * <p>So the tests below are copied from the specification rather than derived from this
 * implementation's behaviour. They are what says "correct", independent of what the library happens
 * to do. Anything that documents a <em>deviation</em> from RFC 8785 rather than conformance to it
 * lives in {@link DocumentedDeviations} and says so loudly.
 *
 * <p>{@code docs/architecture/04-item-provenance.md} §4 fixes JCS as the scheme.
 */
class JsonCanonicalizationTest {

    /**
     * RFC 8785 §3.2.2, the input half of the specification's worked example. Every backslash is
     * doubled because Java's escapes must not eat the JSON ones: what the canonicalizer has to
     * receive is the six-character sequence backslash-u-0-0-0-a, not an actual newline. Getting that
     * wrong quietly changes the test into a different, easier one.
     */
    private static final String RFC8785_SAMPLE = """
            {
              "numbers": [333333333.33333329, 1E30, 4.50,
                          2e-3, 0.000000000000000000000000001],
              "string": "\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\"\\/",
              "literals": [null, true, false]
            }
            """;

    /**
     * RFC 8785 §3.2.4: the exact UTF-8 bytes the sample above must canonicalize to, transcribed with
     * the specification's line grouping so the two can be diffed by eye. Asserting on bytes rather
     * than on a Java string is deliberate — bytes are what gets signed.
     */
    private static final byte[] RFC8785_SAMPLE_CANONICAL_BYTES = HexFormat.of().parseHex("""
                    7b 22 6c 69 74 65 72 61 6c 73 22 3a 5b 6e 75 6c 6c 2c 74 72
                    75 65 2c 66 61 6c 73 65 5d 2c 22 6e 75 6d 62 65 72 73 22 3a
                    5b 33 33 33 33 33 33 33 33 33 2e 33 33 33 33 33 33 33 2c 31
                    65 2b 33 30 2c 34 2e 35 2c 30 2e 30 30 32 2c 31 65 2d 32 37
                    5d 2c 22 73 74 72 69 6e 67 22 3a 22 e2 82 ac 24 5c 75 30 30
                    30 66 5c 6e 41 27 42 5c 22 5c 5c 5c 5c 5c 22 2f 22 7d
                    """.replaceAll("\\s", ""));

    /** RFC 8785 §3.2.3's property-sorting test data. */
    private static final String RFC8785_SORTING_SAMPLE = """
            {
              "\\u20ac": "Euro Sign",
              "\\r": "Carriage Return",
              "\\ufb33": "Hebrew Letter Dalet With Dagesh",
              "1": "One",
              "\\ud83d\\ude00": "Emoji: Grinning Face",
              "\\u0080": "Control",
              "\\u00f6": "Latin Small Letter O With Diaeresis"
            }
            """;

    /**
     * RFC 8785 Appendix B, the "JSON Representation" column. Every entry is the shortest form that
     * round-trips its IEEE 754 double, so each must be a fixed point of canonicalization. NaN and
     * Infinity are omitted because they are not JSON (§3.2.2.3) and are tested as rejections.
     */
    private static final String[] RFC8785_APPENDIX_B = {
        "0",
        "5e-324",
        "-5e-324",
        "1.7976931348623157e+308",
        "-1.7976931348623157e+308",
        "9007199254740992",
        "-9007199254740992",
        "295147905179352830000",
        "9.999999999999997e+22",
        "1e+23",
        "1.0000000000000001e+23",
        "999999999999999700000",
        "999999999999999900000",
        "1e+21",
        "9.999999999999997e-7",
        "0.000001",
        "333333333.3333332",
        "333333333.33333325",
        "333333333.3333333",
        "333333333.3333334",
        "333333333.33333343",
        "-0.0000033333333333333333",
        "1424953923781206.2",
    };

    /**
     * U+0080, a C1 control character. Built from its code point rather than written into a literal
     * because it renders as nothing at all: an invisible character in an expected value is how a
     * test ends up asserting something nobody can read.
     */
    private static final String C1_CONTROL = Character.toString(0x80);

    @Nested
    @DisplayName("RFC 8785 worked example")
    class WorkedExample {

        @Test
        @DisplayName("§3.2.4 — the sample canonicalizes to the specification's exact bytes")
        void producesTheSpecifiedBytes() {
            assertThat(JsonCanonicalization.canonicalize(RFC8785_SAMPLE)).isEqualTo(RFC8785_SAMPLE_CANONICAL_BYTES);
        }

        @Test
        @DisplayName("§3.2.3 — the string form matches too, and shows what changed")
        void producesTheSpecifiedString() {
            // Spelled out so a failure is readable: properties reordered, numbers reformatted,
            // whitespace gone, escapes normalized, the escaped solidus unescaped.
            String expected = new String(RFC8785_SAMPLE_CANONICAL_BYTES, StandardCharsets.UTF_8);

            assertThat(JsonCanonicalization.canonicalizeToString(RFC8785_SAMPLE))
                    .isEqualTo(expected);
            assertThat(expected).startsWith("{\"literals\":[null,true,false],\"numbers\":[");
        }
    }

    @Nested
    @DisplayName("property sorting")
    class PropertySorting {

        @Test
        @DisplayName("§3.2.3 — the specification's sorting test data sorts as published")
        void sortingTestData() {
            assertThat(JsonCanonicalization.canonicalizeToString(RFC8785_SORTING_SAMPLE))
                    .isEqualTo("{\"\\r\":\"Carriage Return\","
                            + "\"1\":\"One\","
                            + "\"" + C1_CONTROL + "\":\"Control\","
                            + "\"ö\":\"Latin Small Letter O With Diaeresis\","
                            + "\"€\":\"Euro Sign\","
                            + "\"😀\":\"Emoji: Grinning Face\","
                            + "\"דּ\":\"Hebrew Letter Dalet With Dagesh\"}");
        }

        @Test
        @DisplayName("sorting is by UTF-16 code unit, which is not code point order")
        void sortingIsByUtf16CodeUnit() {
            String canonical = JsonCanonicalization.canonicalizeToString(RFC8785_SORTING_SAMPLE);

            // The single most valuable line in this file. U+1F600 (the emoji) is a LARGER code point
            // than U+FB33, so an implementation sorting by code point — or by UTF-8 bytes, which
            // gives the same order — puts Hebrew first and produces different signing bytes for an
            // identical document. §3.2.3 requires UTF-16 code units, where the emoji's leading
            // surrogate 0xD83D sorts before 0xFB33.
            assertThat("😀".codePointAt(0)).isGreaterThan("דּ".codePointAt(0));
            assertThat(canonical.indexOf("Emoji")).isLessThan(canonical.indexOf("Hebrew"));
        }

        @Test
        @DisplayName("§3.2.3 — shorter names precede longer ones with the same prefix")
        void prefixOrdering() {
            // The RFC's plain-English statement of the rule: "", "a", "aa", "ab".
            assertThat(JsonCanonicalization.canonicalizeToString("{\"ab\":4,\"aa\":3,\"a\":2,\"\":1}"))
                    .isEqualTo("{\"\":1,\"a\":2,\"aa\":3,\"ab\":4}");
        }

        @Test
        @DisplayName("child objects are sorted recursively, including inside arrays")
        void sortingRecurses() {
            String nested = "{\"b\":{\"d\":1,\"c\":2},\"a\":[{\"z\":1,\"y\":2},3]}";

            assertThat(JsonCanonicalization.canonicalizeToString(nested))
                    .isEqualTo("{\"a\":[{\"y\":2,\"z\":1},3],\"b\":{\"c\":2,\"d\":1}}");
        }

        @Test
        @DisplayName("array order is preserved — only object keys sort")
        void arrayOrderIsPreserved() {
            // An array is an ordered sequence and its order is data. Sorting it would change the
            // meaning of, say, a chain of prevRecordHash values, not merely its spelling.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":[3,1,2,\"z\",\"b\"]}"))
                    .isEqualTo("{\"a\":[3,1,2,\"z\",\"b\"]}");
        }

        @Test
        @DisplayName("key order and whitespace in the input do not reach the output")
        void inputFormattingIsIrrelevant() {
            // The property everything else depends on: two servers whose JSON libraries emit keys in
            // different orders must still sign the same bytes.
            String a = "{\"b\":2,\"a\":1,\"c\":{\"z\":true,\"y\":null}}";
            String b = "{  \"c\" : { \"y\" : null, \"z\" : true } ,\n \"a\":1,\t\"b\":2 }";

            assertThat(JsonCanonicalization.canonicalize(a)).isEqualTo(JsonCanonicalization.canonicalize(b));
        }
    }

    @Nested
    @DisplayName("number serialization")
    class Numbers {

        @Test
        @DisplayName("§3.2.2 — the worked example's number forms")
        void workedExampleNumbers() {
            assertThat(JsonCanonicalization.canonicalizeToString(
                            "{\"n\":[333333333.33333329,1E30,4.50,2e-3,0.000000000000000000000000001]}"))
                    .isEqualTo("{\"n\":[333333333.3333333,1e+30,4.5,0.002,1e-27]}");
        }

        @Test
        @DisplayName("trailing zeros and exponent spelling are normalized")
        void spellingIsNormalized() {
            // Three spellings of 10^30, one canonical form; 0.1 is already canonical and must be
            // left alone. Without this a payload that made a round trip through a different JSON
            // library — or through a different setting of the same one — would stop verifying.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":1.0}")).isEqualTo("{\"a\":1}");
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":1E30}"))
                    .isEqualTo("{\"a\":1e+30}");
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":1e30}"))
                    .isEqualTo("{\"a\":1e+30}");
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":1.0E+30}"))
                    .isEqualTo("{\"a\":1e+30}");
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":0.1}")).isEqualTo("{\"a\":0.1}");
        }

        @Test
        @DisplayName("negative zero canonicalizes to zero")
        void negativeZeroCollapses() {
            // IEEE 754 has two zeros; JSON has one. Anything that tried to carry sign-of-zero as
            // meaning — a signed delta, say — would lose it here, silently, after signing.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":-0}")).isEqualTo("{\"a\":0}");
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":-0.0}"))
                    .isEqualTo("{\"a\":0}");
        }

        @Test
        @DisplayName("Appendix B — every published serialization is a fixed point")
        void appendixBFormsAreStable() {
            for (String form : RFC8785_APPENDIX_B) {
                assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":" + form + "}"))
                        .as("Appendix B form %s was reformatted", form)
                        .isEqualTo("{\"a\":" + form + "}");
            }
        }

        @Test
        @DisplayName("Appendix B note 4 — an exact tie rounds to even")
        void roundsHalfToEven() {
            // 1424953923781206.25 is exactly representable; the shortest round-tripping form is
            // ...206.2, not ...206.3. Getting the tie-break wrong is the classic way two number
            // serializers disagree on one value in a million and nobody notices for a year.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":1424953923781206.25}"))
                    .isEqualTo("{\"a\":1424953923781206.2}");
        }

        @Test
        @DisplayName("an int64 does not survive canonicalization")
        void largeIntegersLosePrecision() {
            // Not a bug — JSON numbers are IEEE 754 doubles (§3.2.2.3), so anything beyond 2^53 is
            // rounded. It matters because itemAttrs carries arbitrary game values
            // (docs/architecture/04-item-provenance.md §2): a 64-bit id or an ethecoin amount in
            // minor units put there as a number would be silently altered before signing. RFC 8785
            // Appendix D's answer is to carry such values as strings.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"a\":9223372036854775807}"))
                    .isEqualTo("{\"a\":9223372036854776000}");
        }

        @Test
        @DisplayName("§3.2.2.3 — NaN and Infinity are refused")
        void nonFiniteValuesAreRefused() {
            // Attack (or, more often, accident): a serializer that emits NaN for a divide-by-zero
            // stat. It is not JSON, and a canonicalizer that guessed a value for it would let two
            // implementations guess differently.
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"a\":NaN}"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"a\":Infinity}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("string escaping")
    class Strings {

        @Test
        @DisplayName("§3.2.2.2 — the five predefined escapes are short, the rest are \\uhhhh")
        void controlCharacterEscaping() {
            // Exactly five code points get a short escape: U+0008, U+0009, U+000A, U+000C and
            // U+000D. Everything else below U+0020 gets the six-character lowercase-hex form.
            // An implementation that spelled U+0008 the long way would produce different bytes
            // for a string that is, to every reader, the same string.
            String input = "{\"s\":\"\\u0000\\u0001\\u0008\\u0009\\u000a\\u000b\\u000c\\u000d\\u001f\"}";

            assertThat(JsonCanonicalization.canonicalizeToString(input))
                    .isEqualTo("{\"s\":\"\\u0000\\u0001\\b\\t\\n\\u000b\\f\\r\\u001f\"}");
        }

        @Test
        @DisplayName("§3.2.2.2 — hex in \\uhhhh escapes is lowercase")
        void escapeHexIsLowercase() {
            // Uppercase hex in, lowercase hex out. Case is not cosmetic here: F and f are two
            // different bytes, and therefore two different signatures over the same string.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"s\":\"\\u000F\"}"))
                    .isEqualTo("{\"s\":\"\\u000f\"}");
        }

        @Test
        @DisplayName("§3.2.2.2 — only quote and backslash are escaped above U+001F")
        void quoteAndBackslashOnly() {
            assertThat(JsonCanonicalization.canonicalizeToString("{\"s\":\"a\\\"b\\\\c\"}"))
                    .isEqualTo("{\"s\":\"a\\\"b\\\\c\"}");
            // The solidus MAY be escaped in JSON input but MUST NOT be in canonical output.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"s\":\"a\\/b\"}"))
                    .isEqualTo("{\"s\":\"a/b\"}");
        }

        @Test
        @DisplayName("U+0080 is a control character but not an escaped one")
        void nonAsciiControlsAreNotEscaped() {
            // The boundary the specification actually draws is U+0000..U+001F, not "is a control
            // character". U+0080 is C1 control PAD and is emitted verbatim. An implementation that
            // escaped it would be readable, correct-looking, and produce non-matching signatures.
            assertThat(JsonCanonicalization.canonicalizeToString("{\"s\":\"\\u0080\"}"))
                    .isEqualTo("{\"s\":\"" + C1_CONTROL + "\"}");
        }

        @Test
        @DisplayName("non-BMP characters become real UTF-8, not escaped surrogates")
        void nonBmpCharactersAreEncodedAsUtf8() {
            // Where naive implementations break. The input carries U+1F600 as a surrogate pair, but
            // it is one code point, and §3.2.4 requires UTF-8 — so the output must be the four bytes
            // f0 9f 98 80. Two plausible wrong answers: re-emitting the two escape sequences
            // unchanged, or encoding each surrogate separately as three bytes (CESU-8, which is what
            // a UTF-16-shaped encoder does if nobody stops it). All three round-trip happily within
            // one implementation; only one of them verifies against everybody else's.
            byte[] canonical = JsonCanonicalization.canonicalize("{\"s\":\"\\ud83d\\ude00\"}");

            assertThat(canonical).isEqualTo("{\"s\":\"😀\"}".getBytes(StandardCharsets.UTF_8));
            assertThat(HexFormat.of().formatHex(canonical)).contains("f09f9880");
        }

        @Test
        @DisplayName("a non-BMP property name sorts and survives the round trip")
        void nonBmpPropertyName() {
            assertThat(JsonCanonicalization.canonicalizeToString("{\"\\ud83d\\ude00\":1,\"a\":2}"))
                    .isEqualTo("{\"a\":2,\"😀\":1}");
        }
    }

    @Nested
    @DisplayName("well-formedness")
    class WellFormedness {

        @Test
        @DisplayName("malformed JSON is a caller error, not an I/O error")
        void malformedInputIsRejected() {
            // The underlying library reports parse failures as IOException. Surfacing that unchanged
            // would push every caller into a try/catch for a failure that is never about I/O.
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{oops}"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"a\":1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("trailing content after the document is rejected")
        void trailingContentIsRejected() {
            // Attack: append a second document and hope the verifier canonicalizes the first while
            // the application reads the second. This is the JSON equivalent of request smuggling.
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"a\":1} {\"a\":2}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("duplicate property names are rejected (I-JSON)")
        void duplicatePropertiesAreRejected() {
            // Attack: {"power":42,"power":9001}. Parsers disagree about which wins — first, last, or
            // both — so a duplicate key is a document that means different things to different
            // readers while presenting one signature. RFC 8785 §3.1 requires I-JSON, which forbids it.
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"a\":1,\"a\":2}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("algebraic properties")
    class AlgebraicProperties {

        @Test
        @DisplayName("canonicalizing twice equals canonicalizing once")
        void isIdempotent() {
            // Required because canonical output is itself valid JSON (RFC 8785 Appendix C) and may be
            // stored, forwarded and re-canonicalized on the way to a verifier. If the operation were
            // not idempotent, a record would verify or not depending on how many hops it took.
            byte[] once = JsonCanonicalization.canonicalize(RFC8785_SAMPLE);
            byte[] twice = JsonCanonicalization.canonicalize(new String(once, StandardCharsets.UTF_8));

            assertThat(twice).isEqualTo(once);
        }

        @Test
        @DisplayName("the byte[] overload agrees with the String overload")
        void overloadsAgree() {
            for (String input : new String[] {RFC8785_SAMPLE, RFC8785_SORTING_SAMPLE, "{\"a\":[1,2,3]}"}) {
                assertThat(JsonCanonicalization.canonicalize(input.getBytes(StandardCharsets.UTF_8)))
                        .as("overloads diverged for %s", input)
                        .isEqualTo(JsonCanonicalization.canonicalize(input));
            }
        }
    }

    @Nested
    @DisplayName("documented deviations — current behaviour, not endorsed behaviour")
    class DocumentedDeviations {

        @Test
        @DisplayName("both overloads report malformed input the same way")
        void bothOverloadsShareOneErrorContract() {
            // Regression guard. These once differed — the String overload threw
            // IllegalArgumentException and the byte[] overload UncheckedIOException for the identical
            // failure — so a caller who read one overload's contract was unprotected on the other.
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{oops}"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{oops}".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a lone surrogate is rejected — it would otherwise collide two signing inputs")
        void loneSurrogatesAreRejected() {
            // RFC 8785 §3.2.2.2 requires stopping with an error on invalid Unicode. The bundled
            // canonicalizer does not, and Java's UTF-8 encoder then substitutes '?' for the lone
            // surrogate — so `{"s":"\ud800"}` and `{"s":"\udbff"}` used to canonicalize to the SAME
            // bytes. One signature covering two distinct payloads is a forgery primitive, not a
            // conformance footnote, which is why JsonCanonicalization now enforces this itself.
            //
            // Our own producers never emit invalid Unicode; a federated peer's payload is untrusted
            // input, and a verifier is exactly where untrusted input arrives.
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"s\":\"\\ud800\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("surrogate");
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"s\":\"\\udbff\"}"))
                    .isInstanceOf(IllegalArgumentException.class);
            // An unpaired LOW surrogate is equally invalid and equally rejected.
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("{\"s\":\"\\udc00\"}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a well-formed surrogate pair still round-trips")
        void validSurrogatePairsSurvive() {
            // The rejection above must not become a ban on astral-plane characters: emoji and other
            // non-BMP text are legitimate and travel as a properly paired surrogate couple.
            String canonical = JsonCanonicalization.canonicalizeToString("{\"s\":\"\\ud83d\\ude80\"}");
            assertThat(canonical).isEqualTo("{\"s\":\"🚀\"}");
            assertThat(JsonCanonicalization.canonicalize("{\"s\":\"🚀\"}"))
                    .as("escaped and literal spellings of the same character must agree")
                    .isEqualTo(JsonCanonicalization.canonicalize("{\"s\":\"\\ud83d\\ude80\"}"));
        }

        @Test
        @DisplayName("LIMITATION: only objects and arrays are accepted at the top level")
        void topLevelScalarsAreRejected() {
            // JCS is defined over any JSON value, but the bundled canonicalizer requires a composite
            // at the top. Harmless for us — every signed payload is an object
            // (docs/architecture/04-item-provenance.md §2) — and worth pinning so nobody discovers it
            // by signing a bare string in a hurry.
            assertThat(JsonCanonicalization.canonicalizeToString("[2,1]")).isEqualTo("[2,1]");
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("\"a string\""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JsonCanonicalization.canonicalize("null"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
