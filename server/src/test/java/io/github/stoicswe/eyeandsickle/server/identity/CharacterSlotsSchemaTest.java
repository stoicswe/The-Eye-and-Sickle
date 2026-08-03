package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reads the V3 migration SQL off the classpath and checks it against the Java side — no database, so it
 * runs in the default {@code mvn verify}. It is the character/slot counterpart to
 * {@code persistence/SchemaVocabularyTest}: the {@code ck_players_status} vocabulary and
 * {@link CharacterStatus} are two spellings of one decision, and nothing but a check like this keeps them
 * equal. It also pins the load-bearing structural facts of 09 §8 — the unique constraint moving from
 * {@code did} to {@code (did, slot)}, and the did/slot pairing and slot-bound CHECKs.
 */
class CharacterSlotsSchemaTest {

    private static final String CORE = "db/migration/core/V2__core_schema.sql";
    private static final String SQL = read(CORE);

    @Test
    @DisplayName("the status CHECK lists exactly the CharacterStatus db spellings")
    void statusVocabularyMatches() {
        Set<String> expected = Arrays.stream(CharacterStatus.values())
                .map(CharacterStatus::dbValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(inList("status")).isEqualTo(expected);
    }

    @Test
    @DisplayName("uniqueness moves from did to (did, slot) — the whole point of 09 §8")
    void uniquenessMoved() {
        // ⚠ One row per SLOT per account, and no constraint on the DID alone. Asserted both ways:
        // the presence of the composite key, and the absence of the single-column one it replaced —
        // because a schema that grew `uq_players_did` back would silently reimpose one character per
        // account, and the composite key would still be there to make this look fine.
        assertThat(stripComments(SQL)).contains("UNIQUE (did, slot)");
        assertThat(stripComments(SQL)).doesNotContain("uq_players_did ");
    }

    @Test
    @DisplayName("the did/slot pairing and slot-bound CHECKs are present")
    void structuralChecksPresent() {
        String body = stripComments(SQL);
        // A DID-bound character has a slot; a local one has neither (09 §1).
        assertThat(body).contains("(did IS NULL) = (slot IS NULL)");
        // A generous structural bound, not the product cap.
        assertThat(body).contains("slot BETWEEN 1 AND 16");
    }

    @Test
    @DisplayName("slot is a nullable smallint and status is a not-null text defaulting to active")
    void columnShapes() {
        String body = stripComments(SQL);
        assertThat(body).containsPattern("slot\\s+smallint\\s+NULL");
        assertThat(body).containsPattern("status\\s+text\\s+NOT NULL DEFAULT 'active'");
    }


    // ------------------------------------------------------------------ helpers

    private static Set<String> inList(String column) {
        Matcher matcher = Pattern.compile(Pattern.quote(column) + "\\s+IN\\s*\\(([^)]*)\\)")
                .matcher(stripComments(SQL));
        assertThat(matcher.find())
                .as("%s should be constrained by a CHECK ... IN (...)", column)
                .isTrue();
        Set<String> values = new LinkedHashSet<>();
        for (String literal : matcher.group(1).split(",")) {
            String trimmed = literal.strip();
            if (!trimmed.isEmpty()) {
                assertThat(trimmed).startsWith("'").endsWith("'");
                values.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return values;
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("--[^\\n]*", "");
    }

    private static String read(String resource) {
        try (InputStream in = CharacterSlotsSchemaTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
