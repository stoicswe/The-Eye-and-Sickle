package io.github.stoicswe.eyeandsickle.client.teaching;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every manual page the client ships, indexed for {@code man}, {@code whatis} and {@code apropos}.
 *
 * <h2>Loaded from an explicit index, not by scanning</h2>
 *
 * Classpath directory scanning works from a target directory and quietly stops working from inside a
 * jar. An index file makes the shipped set explicit, reviewable in a diff, and identical in both
 * layouts — and a page that is not in the index is a page nobody proof-read, which is the failure
 * {@code docs/client/04} §4.10's coverage checks exist to catch.
 *
 * <h2>Definitions are never destroyed, only quieted</h2>
 *
 * {@code docs/client/00-client-overview.md} §5.2, restated by §4.6: at <em>every</em> teaching level,
 * including {@code off}, {@code man &lt;term&gt;} resolves and the manual works. The teaching level
 * governs whether definitions come to the player unbidden; it never governs whether they exist.
 */
public final class TermDatabase {

    private static final String ROOT = "/io/github/stoicswe/eyeandsickle/client/terms/";

    private final Map<String, TermPage> byReference = new LinkedHashMap<>();
    private final Map<String, TermPage> byLookup = new LinkedHashMap<>();
    private final List<TermPage> pages = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    private TermDatabase() {}

    /** Loads the shipped English pages. */
    public static TermDatabase load() {
        return load("en");
    }

    public static TermDatabase load(String locale) {
        TermDatabase db = new TermDatabase();
        String indexPath = ROOT + locale + "/index.txt";
        try (InputStream in = TermDatabase.class.getResourceAsStream(indexPath)) {
            if (in == null) {
                db.problems.add("No term index at " + indexPath);
                return db;
            }
            String index = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : index.split("\n")) {
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                db.loadPage(ROOT + locale + "/" + entry, entry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the term index", e);
        }
        db.pages.sort((a, b) -> Collator.getInstance().compare(a.name(), b.name()));
        db.checkCrossReferences();
        return db;
    }

    private void loadPage(String path, String origin) {
        try (InputStream in = TermDatabase.class.getResourceAsStream(path)) {
            if (in == null) {
                problems.add("Listed in the index but missing: " + origin);
                return;
            }
            TermPage page = TermParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), origin);
            TermPage clash = byReference.put(page.reference(), page);
            if (clash != null) {
                problems.add("Two pages claim " + page.reference());
            }
            pages.add(page);
            byLookup.put(page.id().toLowerCase(Locale.ROOT), page);
            for (String alias : page.aliases()) {
                byLookup.putIfAbsent(alias.toLowerCase(Locale.ROOT), page);
            }
        } catch (IOException e) {
            problems.add(origin + ": " + e.getMessage());
        } catch (TermParser.TermFormatException e) {
            problems.add(e.getMessage());
        }
    }

    /**
     * Checks that every {@code seeAlso} reference resolves.
     *
     * <p>{@code docs/client/04} §4.10 makes this a CI check for a reason a player would feel: a
     * {@code SEE ALSO} entry that goes nowhere is a dead end at exactly the moment somebody is
     * curious enough to follow it.
     */
    private void checkCrossReferences() {
        for (TermPage page : pages) {
            for (String ref : page.seeAlso()) {
                if (!byReference.containsKey(ref)) {
                    problems.add(page.reference() + " points at " + ref + ", which does not exist");
                }
            }
        }
    }

    /** Anything wrong with the shipped set. Empty is the only acceptable state at release. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    public List<TermPage> pages() {
        return List.copyOf(pages);
    }

    public int size() {
        return pages.size();
    }

    /**
     * Resolves a term the way {@code man} does: by name, or by name and section.
     *
     * @param query {@code compute}, {@code compute(7)}, or {@code 7 compute}
     */
    public Optional<TermPage> find(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);

        // `man 7 compute` — the form that disambiguates, and the reason section numbers are written.
        if (q.matches("\\d+\\s+\\S+")) {
            String[] parts = q.split("\\s+");
            return Optional.ofNullable(byReference.get(parts[1] + "(" + parts[0] + ")"));
        }
        if (q.matches("\\S+\\(\\d+\\)")) {
            return Optional.ofNullable(byReference.get(q));
        }
        return Optional.ofNullable(byLookup.get(q));
    }

    /** Every page in a section, for the index. */
    public List<TermPage> inSection(int section) {
        return pages.stream().filter(p -> p.section() == section).toList();
    }

    /** Every page with a given honesty status — the filter §4.6 requires. */
    public List<TermPage> withStatus(TermPage.Status status) {
        return pages.stream().filter(p -> p.status() == status).toList();
    }

    /**
     * {@code apropos} semantics: searches the {@code NAME} line, which is what the real one does.
     *
     * <p>This is the command that makes the manual usable when you do not know what you are looking
     * for — and it is genuinely under-used in real life, which is reason enough to put it in front of
     * a player who has just discovered they need it.
     *
     * @param all when true, extends the search to the whole page ({@code --all})
     */
    public List<TermPage> apropos(String text, boolean all) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String needle = text.trim().toLowerCase(Locale.ROOT);
        List<TermPage> out = new ArrayList<>();
        for (TermPage page : pages) {
            boolean hit = page.nameLine().toLowerCase(Locale.ROOT).contains(needle)
                    || page.id().contains(needle)
                    || page.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(needle));
            if (!hit && all) {
                hit = page.body().values().stream()
                        .anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(needle));
            }
            if (hit) {
                out.add(page);
            }
        }
        return out;
    }

    /** {@code whatis}: matches whole names and prints the one-line description. */
    public List<TermPage> whatis(String name) {
        return find(name).map(List::of).orElse(List.of());
    }
}
