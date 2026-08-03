package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Shape, size and malformed-input handling for {@code jsonb} values. */
class JsonbTest {

    /** A record of the kind that ends up in {@code memo} or {@code self_descriptor}. */
    record Descriptor(String endpoint, long sequenceNumber) {}

    @Test
    @DisplayName("a map round-trips")
    void objectsRoundTrip() {
        Map<String, Object> attrs = Map.of("power", 42, "durability", "0.87");

        Map<String, Object> readBack = Jsonb.readObject(Jsonb.writeObject(attrs));

        assertThat(readBack).containsEntry("power", 42).containsEntry("durability", "0.87");
    }

    @Test
    @DisplayName("a record serializes to an object and binds back")
    void recordsRoundTrip() {
        Descriptor descriptor = new Descriptor("https://peer.example.test", 7);

        String json = Jsonb.writeObject(descriptor);

        assertThat(Jsonb.read(json, Descriptor.class)).isEqualTo(descriptor);
    }

    @Test
    @DisplayName("a list round-trips as an array")
    void arraysRoundTrip() {
        List<Map<String, String>> signatures = List.of(
                Map.of("alg", "EdDSA", "kid", "did:plc:x#key1"), Map.of("alg", "EdDSA", "kid", "did:plc:y#key1"));

        List<Object> readBack = Jsonb.readArray(Jsonb.writeArray(signatures));

        assertThat(readBack).hasSize(2);
    }

    @Test
    @DisplayName("an object-shaped column refuses an array, and vice versa")
    void shapeIsCheckedBeforeTheDatabaseSeesIt() {
        // The schema constrains these columns with jsonb_typeof(col) = 'object'. Catching the wrong
        // shape here names the mistake; catching it at the database names the constraint.
        assertThatThrownBy(() -> Jsonb.writeObject(List.of(1, 2, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON object");

        assertThatThrownBy(() -> Jsonb.writeArray(Map.of("a", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON array");

        assertThatThrownBy(() -> Jsonb.writeObject("just a string")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Jsonb.writeObject(42)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null is refused rather than written as the JSON literal null")
    void nullIsNotADocument() {
        // 'null' is valid jsonb but fails jsonb_typeof(col) = 'object', so it would be a constraint
        // violation on insert — and a NOT NULL column whose value is the string "null" is worse.
        assertThatThrownBy(() -> Jsonb.writeObject(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Jsonb.writeArray(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Jsonb.readObject(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("malformed stored JSON fails with a bounded excerpt, not a dumped blob")
    void malformedJsonIsRejected() {
        assertThatThrownBy(() -> Jsonb.readObject("{not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("well-formed");

        assertThatThrownBy(() -> Jsonb.readObject("[1,2,3]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected a JSON object");

        assertThatThrownBy(() -> Jsonb.readArray("{\"a\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected a JSON array");
    }

    @Test
    @DisplayName("a huge document never lands whole in an exception message")
    void errorMessagesAreBounded() {
        String oversized = "\"" + "x".repeat(4_000) + "\"";

        assertThatThrownBy(() -> Jsonb.readObject(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(thrown -> assertThat(thrown.getMessage()).hasSizeLessThan(300));
    }

    @Test
    @DisplayName("a document over the size cap is refused")
    void oversizedDocumentsAreRefused() {
        // A denial-of-service bound, not a game rule: jsonb is the one place in this schema where an
        // attacker-influenced value has no natural length.
        Map<String, Object> huge = Map.of("blob", "x".repeat(Jsonb.MAX_BYTES + 1));

        assertThatThrownBy(() -> Jsonb.writeObject(huge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cap");
    }

    @Test
    @DisplayName("a document exactly at the cap is accepted")
    void theCapIsInclusive() {
        // Boundary: MAX_BYTES is the largest acceptable size, not the first rejected one.
        int overhead = Jsonb.writeObject(Map.of("blob", "")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        Map<String, Object> atLimit = Map.of("blob", "x".repeat(Jsonb.MAX_BYTES - overhead));

        assertThat(Jsonb.writeObject(atLimit)).hasSize(Jsonb.MAX_BYTES);
    }

    @Test
    @DisplayName("a NULL value inside a map is preserved, because jsonb can hold one")
    void nullsInsideDocumentsSurvive() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("prevRecordHash", null);
        withNull.put("chainDepth", 0);

        Map<String, Object> readBack = Jsonb.readObject(Jsonb.writeObject(withNull));

        // A genesis provenance payload carries an explicit null prevRecordHash, and dropping the key
        // would change the canonical bytes a signature covers (docs/architecture/04 §2).
        assertThat(readBack).containsKey("prevRecordHash");
        assertThat(readBack.get("prevRecordHash")).isNull();
    }

    @Test
    @DisplayName("the empty-document constants match the schema defaults")
    void emptyConstantsMatchTheSchema() {
        assertThat(Jsonb.EMPTY_OBJECT).isEqualTo("{}");
        assertThat(Jsonb.EMPTY_ARRAY).isEqualTo("[]");
        assertThat(Jsonb.CAST).isEqualTo(" FORMAT JSON");
    }

    @Test
    @DisplayName("reading a jsonb column goes through Row so a missing column is still loud")
    void columnHelpersDelegateToRow() {
        Row row = new Row(FakeResultSet.singleColumn("item_attrs", "{\"power\":42}"), "Item");

        assertThat(Jsonb.objectColumn(row, "item_attrs")).containsEntry("power", 42);
        assertThatThrownBy(() -> Jsonb.objectColumn(row, "attrs")).isInstanceOf(RowMappingException.class);
    }

    @Test
    @DisplayName("binding a stored document to the wrong type fails with the type named")
    void bindingFailuresNameTheType() {
        assertThatThrownBy(() -> Jsonb.read("{\"endpoint\":42,\"sequenceNumber\":\"nope\"}", Descriptor.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Descriptor");
    }
}
