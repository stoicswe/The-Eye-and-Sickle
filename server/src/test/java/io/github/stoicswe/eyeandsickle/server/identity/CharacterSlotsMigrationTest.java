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
class CharacterSlotsMigrationTest {

    private static final String V3 = "db/migration/core/V3__character_slots.sql";
    private static final String SQL = read(V3);

    @Test
    @DisplayName("the status CHECK lists exactly the CharacterStatus db spellings")
    void statusVocabularyMatches() {
        Set<String> expected =
                Arrays.stream(CharacterStatus.values()).map(CharacterStatus::dbValue).collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(inList("status")).isEqualTo(expected);
    }

    @Test
    @DisplayName("uniqueness moves from did to (did, slot) — the whole point of 09 §8")
    void uniquenessMoved() {
        // The single-character-per-DID constraint is dropped...
        assertThat(stripComments(SQL)).contains("DROP CONSTRAINT uq_players_did");
        // ...and replaced by one row per slot per account.
        assertThat(stripComments(SQL)).contains("UNIQUE (did, slot)");
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
        assertThat(body).containsPattern("ADD COLUMN\\s+slot\\s+smallint");
        assertThat(body).containsPattern("ADD COLUMN\\s+status\\s+text\\s+NOT NULL DEFAULT 'active'");
    }

    @Test
    @DisplayName("existing DID-bound rows are backfilled to slot 1 before the pairing CHECK is added")
    void backfillsExistingRows() {
        String body = stripComments(SQL);
        int backfill = body.indexOf("UPDATE players SET slot = 1");
        int pairing = body.indexOf("(did IS NULL) = (slot IS NULL)");
        assertThat(backfill).as("backfill present").isNotNegative();
        assertThat(pairing).as("pairing CHECK present").isNotNegative();
        assertThat(backfill).as("backfill runs before the pairing CHECK is added").isLessThan(pairing);
    }

    // ------------------------------------------------------------------ helpers

    private static Set<String> inList(String column) {
        Matcher matcher = Pattern.compile(Pattern.quote(column) + "\\s+IN\\s*\\(([^)]*)\\)")
                .matcher(stripComments(SQL));
        assertThat(matcher.find()).as("%s should be constrained by a CHECK ... IN (...)", column).isTrue();
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
        try (InputStream in = CharacterSlotsMigrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("%s must be on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
