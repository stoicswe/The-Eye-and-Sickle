package io.github.stoicswe.eyeandsickle.server.persistence;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reading and writing {@code jsonb} columns.
 *
 * <h2>⚠ Every JSON parameter needs {@code FORMAT JSON} in the SQL — and {@code CAST} IS WRONG ⚠</h2>
 *
 * This is the single most common way to get a runtime failure out of this schema, so it is stated
 * first. A {@code String} parameter arrives as {@code varchar}, and {@code varchar} is not
 * {@code JSON}. The SQL standard spelling that <em>parses</em> the text is a {@code FORMAT JSON}
 * qualifier on the value:
 *
 * {@snippet lang = java:
 * jdbcClient
 *         .sql("""
 *              INSERT INTO items (item_id, item_type, item_attrs, holder_did, storage_tier)
 *              VALUES (:itemId, :itemType, :attrs FORMAT JSON, :holderDid, :tier)
 *              """)
 *         .param("attrs", Jsonb.writeObject(itemAttrs))
 *         // ...
 *         .update();
 *}
 *
 * <p>⚠ <strong>A cast is not a synonym for it, and the difference is silent.</strong> This used to be
 * PostgreSQL's {@code :attrs::jsonb}, and H2 accepts that spelling without complaint — it just means
 * something else. {@code CAST('{"a":1}' AS JSON)} produces the JSON <em>string</em>
 * {@code "{\"a\":1}"}, one scalar whose content happens to look like an object, rather than the
 * object. Nothing fails at the cast. What fails is the column's shape constraint, several frames
 * later, reporting {@code ck_items_attrs_object} against a document the caller is looking at and can
 * see is an object. Measured on H2 2.3.232; forty rows in the integration suite failed this way
 * during the port.
 *
 * <p>The alternative — loosening the column to {@code varchar} so any string binds — would remove the
 * one check that caught this, on the surface where a signed document is stored.
 *
 * <h2>What belongs in jsonb, and what does not</h2>
 *
 * {@code docs/architecture/06-data-model.md} §4: relational for the economy, document for the
 * genuinely document-shaped data — signed provenance payloads and envelopes, item attrs, installed
 * rig modules, quorum sampling records. Anything you would want to filter, join, or constrain belongs
 * in a column. A balance in jsonb is a balance no CHECK constraint can defend, and on an authoritative
 * server the database is the last line of defence.
 *
 * <h2>Signed documents are stored verbatim</h2>
 *
 * A provenance envelope's signature covers exact bytes ({@code docs/architecture/04-item-provenance.md}
 * §1). Parsing it and re-serializing it through this class before storage would risk changing those
 * bytes — key order, number rendering, whether a null field is emitted — and the symptom would be a
 * federation that suddenly cannot verify its own records. Store what arrived: pass the received JSON
 * text straight to the parameter, and use {@link Row#json(String)} to read it back untouched. Use the
 * parse helpers here for documents this server owns, never for documents it merely holds.
 *
 * <h2>Size</h2>
 *
 * Writes are capped at {@link #MAX_BYTES}. That is a denial-of-service bound, not a balance value —
 * the same reasoning that puts a 1 MiB field cap in the protocol's wire format ({@code
 * docs/design/15-open-questions.md} P-15) — and it exists because jsonb is the one place in this
 * schema where an attacker-influenced value has no natural length.
 */
public final class Jsonb {

    /** Thread-safe once built, so one instance serves the whole process. */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** The empty object, matching the {@code DEFAULT JSON '{}'} the schema uses. */
    public static final String EMPTY_OBJECT = "{}";

    /** The empty array, matching the {@code DEFAULT JSON '[]'} the schema uses. */
    public static final String EMPTY_ARRAY = "[]";

    /**
     * The qualifier every JSON parameter needs. Provided as a constant so it can be concatenated into
     * a generated fragment without re-deriving the spelling, and so it is greppable.
     *
     * <p>⚠ Leading space included, deliberately: it follows a placeholder rather than binding to it
     * as {@code ::jsonb} did, so {@code ":attrs" + CAST} has to produce {@code :attrs FORMAT JSON}.
     */
    public static final String CAST = " FORMAT JSON";

    /** Upper bound on a written document, in UTF-8 bytes. A DoS bound, not a game rule. */
    public static final int MAX_BYTES = 1 << 20;

    private Jsonb() {}

    // ------------------------------------------------------------------ write

    /**
     * Serializes a value that must be a JSON <em>object</em>.
     *
     * <p>The shape check is not pedantry: the schema constrains these columns with {@code
     * jsonb_typeof(col) = 'object'}, so a value that serializes to an array or a scalar is a
     * constraint violation discovered at the database instead of at the call site, with a message
     * that names the constraint rather than the mistake.
     *
     * @param value a map or a record; must not be {@code null}
     * @return JSON text to bind to a {@code :param FORMAT JSON} placeholder
     * @throws IllegalArgumentException if the value does not serialize to an object, or exceeds
     *     {@link #MAX_BYTES}
     */
    public static String writeObject(Object value) {
        Objects.requireNonNull(value, "value");
        String json = serialize(value);
        if (!isShape(json, '{')) {
            throw new IllegalArgumentException("This column requires a JSON object; "
                    + value.getClass().getName() + " serialized to " + preview(json));
        }
        return json;
    }

    /**
     * Serializes a value that must be a JSON <em>array</em> — a signature list, a sampled committee.
     *
     * @param value a list or an array; must not be {@code null}
     * @return JSON text to bind to a {@code :param FORMAT JSON} placeholder
     * @throws IllegalArgumentException if the value does not serialize to an array, or exceeds
     *     {@link #MAX_BYTES}
     */
    public static String writeArray(Object value) {
        Objects.requireNonNull(value, "value");
        String json = serialize(value);
        if (!isShape(json, '[')) {
            throw new IllegalArgumentException("This column requires a JSON array; "
                    + value.getClass().getName() + " serialized to " + preview(json));
        }
        return json;
    }

    // ------------------------------------------------------------------ read

    /**
     * Parses a JSON object into a map.
     *
     * @param json the document, as read from a jsonb column
     * @return the parsed object
     * @throws IllegalArgumentException if the document is malformed or is not an object
     */
    public static Map<String, Object> readObject(String json) {
        Objects.requireNonNull(json, "json");
        Object parsed = parse(json);
        if (!(parsed instanceof Map<?, ?> object)) {
            throw new IllegalArgumentException("Expected a JSON object, got " + preview(json));
        }
        return asObject(object);
    }

    /**
     * Parses a JSON array into a list.
     *
     * @param json the document, as read from a jsonb column
     * @return the parsed array
     * @throws IllegalArgumentException if the document is malformed or is not an array
     */
    public static List<Object> readArray(String json) {
        Objects.requireNonNull(json, "json");
        Object parsed = parse(json);
        if (!(parsed instanceof List<?> array)) {
            throw new IllegalArgumentException("Expected a JSON array, got " + preview(json));
        }
        return asArray(array);
    }

    /**
     * Binds a JSON object into a record or bean this server owns.
     *
     * <p>Never use this for a document whose signature must still verify — see the class note on
     * storing signed documents verbatim.
     *
     * @param <T> the target type
     * @param json the document, as read from a jsonb column
     * @param type the target type
     * @return the bound value
     * @throws IllegalArgumentException if the document does not bind
     */
    public static <T> T read(String json, Class<T> type) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(type, "type");
        try {
            return MAPPER.readValue(json, type);
        } catch (RuntimeException e) {
            // Jackson 3 reports every parse and binding failure as an unchecked exception, and the
            // caller's response to all of them is the same: this stored document is not what this
            // code expects. The cause carries the detail into the operator's log.
            throw new IllegalArgumentException("Stored jsonb does not bind to " + type.getName(), e);
        }
    }

    /**
     * Reads a required jsonb column as a map — the common case.
     *
     * @param row the row being mapped
     * @param column the column label
     * @return the parsed object
     */
    public static Map<String, Object> objectColumn(Row row, String column) {
        Objects.requireNonNull(row, "row");
        return readObject(row.json(column));
    }

    /**
     * Reads a required jsonb column as a list — signature blocks, sampled validators.
     *
     * @param row the row being mapped
     * @param column the column label
     * @return the parsed array
     */
    public static List<Object> arrayColumn(Row row, String column) {
        Objects.requireNonNull(row, "row");
        return readArray(row.json(column));
    }

    // ------------------------------------------------------------------ internals

    private static String serialize(Object value) {
        String json;
        try {
            json = MAPPER.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Cannot serialize " + value.getClass().getName() + " to jsonb", e);
        }
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "jsonb document is " + bytes + " bytes, over the " + MAX_BYTES + "-byte cap");
        }
        return json;
    }

    private static Object parse(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not well-formed JSON: " + preview(json), e);
        }
    }

    /**
     * Whether the serialized form is an object or an array, ignoring leading whitespace. Cheaper and
     * more direct than re-parsing: Jackson's output for a map or a list is unambiguous at the first
     * non-whitespace character.
     */
    private static boolean isShape(String json, char opener) {
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == opener;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Map<?, ?> parsed) {
        // JSON object keys are always strings, so this cast cannot fail for anything Jackson parsed.
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asArray(List<?> parsed) {
        return (List<Object>) parsed;
    }

    /** A bounded excerpt, so a 1 MiB blob never lands whole in an exception message or a log line. */
    private static String preview(String json) {
        String trimmed = json.strip();
        return trimmed.length() <= 80 ? "'" + trimmed + "'" : "'" + trimmed.substring(0, 80) + "…'";
    }
}
