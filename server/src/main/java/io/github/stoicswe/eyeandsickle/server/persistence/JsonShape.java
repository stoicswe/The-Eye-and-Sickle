package io.github.stoicswe.eyeandsickle.server.persistence;

/**
 * The JSON shape checks the schema's CHECK constraints call, bound as H2 aliases.
 *
 * <h2>⚠ "object" and "array" are DIFFERENT constraints and must never be collapsed</h2>
 *
 * These replace Postgres's {@code jsonb_typeof(x) = 'object'} and {@code = 'array'}. During the port
 * a single regex briefly rewrote <em>both</em> into the object check — which would have silently
 * inverted six constraints, accepting an object where the schema requires an array
 * ({@code provenance_records.signatures}, {@code duels.participants}, the sampled committee, the
 * signature set). Nothing would have failed at migration time; it would have failed later, on real
 * data, as a constraint that let the wrong thing in.
 *
 * <p>Two separate methods, named for what they assert, so that mistake cannot be made silently again.
 *
 * <h2>⚠ The parameter is {@code String}, and {@code Object} does NOT work</h2>
 *
 * H2 hands a {@code JSON} column to an alias as text. Declaring the parameter as {@code Object} —
 * which looks like the defensive choice — fails at <em>constraint evaluation</em> with
 * "Data conversion error converting JSON to JAVA_OBJECT". ⚠ That surfaces as SQLState <b>23514</b>
 * ("check constraint invalid"), <em>not</em> 23513 ("violation"): the constraint did not reject the
 * row, it failed to run. A check that cannot execute is a check that is not protecting anything, and
 * the two states are one digit apart in the error code.
 *
 * <h2>Why not parse properly</h2>
 *
 * H2 already validates that the column holds well-formed JSON — that is what its {@code JSON} type
 * does. What is left is a one-character question: is the top level {@code {} } or {@code []}? A JSON
 * parser here would add a dependency to the persistence layer and a per-row parse to every write, to
 * answer something the first non-whitespace byte already answers.
 */
public final class JsonShape {

    private JsonShape() {}

    /** ⚠ Called from SQL. Referenced by name in the migration — renaming it breaks the schema. */
    public static boolean isJsonObject(String json) {
        return firstToken(json) == '{';
    }

    /** ⚠ Called from SQL. See {@link #isJsonObject}. */
    public static boolean isJsonArray(String json) {
        return firstToken(json) == '[';
    }

    /**
     * How many elements a JSON array holds.
     *
     * <p>⚠ Replaces {@code jsonb_array_length}, and the constraints using it are real rules, not
     * hygiene: a duel must have at least two participants, its committee size must equal the number
     * of validators actually sampled, and a provenance record must carry at least one signature. An
     * unsigned provenance record is an item with no history.
     *
     * <p>Counts top-level commas outside strings — enough for the {@code >= n} comparisons the schema
     * makes, and it avoids parsing on every write. ⚠ Returns 0 for a non-array, so a shape check must
     * be paired with it; the schema already does that with {@code is_json_array(x) AND
     * json_array_length(x) >= n}.
     */
    public static int jsonArrayLength(String json) {
        if (json == null) {
            return 0;
        }
        String text = json;
        int start = text.indexOf('[');
        if (start < 0) {
            return 0;
        }
        int depth = 0, count = 0;
        boolean inString = false, escaped = false, sawValue = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    sawValue = true;
                }
                case '[', '{' -> {
                    depth++;
                    if (depth > 1) sawValue = true;
                }
                case ']', '}' -> {
                    depth--;
                    if (depth == 0) {
                        return sawValue ? count + 1 : 0;
                    }
                }
                case ',' -> {
                    if (depth == 1) count++;
                }
                default -> {
                    if (!Character.isWhitespace(c)) sawValue = true;
                }
            }
        }
        return 0;
    }

    /**
     * The first non-whitespace character, or {@code 0} for absent.
     *
     * <p>⚠ NULL returns {@code 0}, which fails both checks — but every constraint using these is
     * written either on a {@code NOT NULL} column or as {@code x IS NULL OR is_json_...(x)}, so a
     * nullable column keeps accepting null. Deciding nullability here instead would override the
     * schema's own choice per column.
     *
     * <p>H2 hands a {@code JSON} column to a Java alias as bytes; a literal arrives as a String.
     * Both are handled because both occur.
     */
    private static char firstToken(String text) {
        if (text == null) {
            return 0;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return 0;
    }
}
