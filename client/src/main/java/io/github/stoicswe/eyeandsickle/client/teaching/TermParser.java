package io.github.stoicswe.eyeandsickle.client.teaching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a term file.
 *
 * <h2>Why this is a hundred lines instead of a dependency</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §4.8.2 is explicit: <em>no YAML library, no
 * Markdown library, no new dependency — the parser is a hundred lines and the format is stable
 * because the key set is closed.</em> A closed key set is the thing that makes that true. YAML would
 * accept anything and defer every error to the moment a page renders wrong in front of a player.
 *
 * <h2>Unknown keys and unknown sections fail, they do not drop</h2>
 *
 * §4.8.2 again: <em>an unrecognised section name fails the build rather than rendering — silent-drop
 * is how content quietly goes missing.</em> A typo'd {@code ## DESCRPTION} that silently vanished
 * would leave a page that looks finished and teaches nothing, and nobody would find it until a player
 * did.
 */
public final class TermParser {

    private TermParser() {}

    /** Every key the header may contain. Anything else is an error, not a warning. */
    private static final List<String> KNOWN_KEYS = List.of(
            "id",
            "section",
            "name",
            "canonical",
            "gloss",
            "status",
            "aliases",
            "glossary",
            "seeAlso",
            "reading",
            "notes",
            "revision");

    /** Every body heading a page may carry — the real man sections plus the two game additions. */
    private static final List<String> KNOWN_SECTIONS =
            List.of("SYNOPSIS", "DESCRIPTION", "OPTIONS", "EXIT STATUS", "REAL-WORLD COUNTERPART", "CAVEATS");

    /** Maximum gloss length, from §4.8.2. A gloss that needs more than this is two glosses. */
    public static final int MAX_GLOSS = 72;

    public static TermPage parse(String source, String origin) {
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        Map<String, String> header = new LinkedHashMap<>();
        Map<String, String> body = new LinkedHashMap<>();

        int i = 0;
        while (i < lines.length && lines[i].isBlank()) {
            i++;
        }
        if (i >= lines.length || !lines[i].trim().equals("---")) {
            throw new TermFormatException(origin + ": must start with a `---` header block");
        }
        i++;

        String lastKey = null;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().equals("---")) {
                i++;
                break;
            }
            if (line.isBlank()) {
                continue;
            }
            // A continuation line: indented, and belonging to the key above it. Needed because
            // seeAlso and reading lists get long and a 200-character line is unreviewable.
            if ((line.startsWith(" ") || line.startsWith("\t")) && lastKey != null) {
                header.merge(lastKey, " " + line.trim(), String::concat);
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                throw new TermFormatException(origin + ": header line is not `key: value` — " + line);
            }
            String key = line.substring(0, colon).trim();
            if (!KNOWN_KEYS.contains(key)) {
                throw new TermFormatException(origin + ": unknown header key `" + key
                        + "`. The key set is closed — see docs/client/04 §4.8.2.");
            }
            header.put(key, line.substring(colon + 1).trim());
            lastKey = key;
        }

        String currentSection = null;
        StringBuilder current = new StringBuilder();
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("## ")) {
                if (currentSection != null) {
                    body.put(currentSection, current.toString().strip());
                }
                currentSection = line.substring(3).trim();
                if (!KNOWN_SECTIONS.contains(currentSection)) {
                    throw new TermFormatException(origin + ": unknown body section `" + currentSection
                            + "`. Silent-drop is how content quietly goes missing — see §4.8.2.");
                }
                current = new StringBuilder();
            } else if (currentSection != null) {
                current.append(line).append('\n');
            }
        }
        if (currentSection != null) {
            body.put(currentSection, current.toString().strip());
        }

        return build(header, body, origin);
    }

    private static TermPage build(Map<String, String> header, Map<String, String> body, String origin) {
        require(header, "id", origin);
        require(header, "section", origin);
        require(header, "name", origin);
        require(header, "gloss", origin);
        require(header, "status", origin);
        require(header, "seeAlso", origin);
        require(header, "revision", origin);

        String id = header.get("id");
        if (!id.equals(id.toLowerCase(Locale.ROOT)) || id.contains(" ")) {
            throw new TermFormatException(origin + ": id must be lowercase and hyphenated — got `" + id + "`");
        }

        int section;
        try {
            section = Integer.parseInt(header.get("section").trim());
        } catch (NumberFormatException e) {
            throw new TermFormatException(origin + ": section must be a number");
        }
        if (!List.of(1, 5, 6, 7, 8).contains(section)) {
            throw new TermFormatException(origin + ": section must be one of 1, 5, 6, 7, 8 — got " + section);
        }

        String gloss = header.get("gloss");
        if (gloss.length() > MAX_GLOSS) {
            throw new TermFormatException(
                    origin + ": gloss is " + gloss.length() + " characters, limit is " + MAX_GLOSS);
        }

        TermPage.Status status;
        try {
            status = TermPage.Status.parse(header.get("status"));
        } catch (IllegalArgumentException e) {
            throw new TermFormatException(origin + ": " + e.getMessage());
        }

        // §4.3.1: a section-7 concept page has NO SYNOPSIS, exactly as real section-7 pages usually
        // do not. The absence teaches the section system, so it is enforced rather than trusted.
        if (section == 7 && body.containsKey("SYNOPSIS")) {
            throw new TermFormatException(origin + ": a section-7 concept page must not have a SYNOPSIS "
                    + "— the absence is what teaches the section system (§4.3.1)");
        }
        if (!body.containsKey("DESCRIPTION")) {
            throw new TermFormatException(origin + ": every page needs a DESCRIPTION");
        }
        if (!body.containsKey("REAL-WORLD COUNTERPART")) {
            throw new TermFormatException(origin + ": every page needs a REAL-WORLD COUNTERPART — "
                    + "the honesty rule is not optional (§4.7)");
        }
        // Mandatory on every simplified page, because the caveat IS the simplification's disclosure.
        if (status == TermPage.Status.REAL_SIMPLIFIED && !body.containsKey("CAVEATS")) {
            throw new TermFormatException(
                    origin + ": a `real, simplified` page must carry CAVEATS " + "naming what was simplified (§4.3.1)");
        }

        return new TermPage(
                id,
                section,
                header.get("name"),
                header.getOrDefault("canonical", header.get("name")),
                gloss,
                status,
                splitList(header.get("aliases")),
                header.getOrDefault("glossary", ""),
                splitList(header.get("seeAlso")),
                splitPipe(header.get("reading")),
                header.getOrDefault("notes", ""),
                Integer.parseInt(header.get("revision").trim()),
                body);
    }

    private static void require(Map<String, String> header, String key, String origin) {
        if (!header.containsKey(key) || header.get(key).isBlank()) {
            throw new TermFormatException(origin + ": missing required header key `" + key + "`");
        }
    }

    private static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** {@code reading} is pipe-separated because citations contain commas. */
    private static List<String> splitPipe(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Thrown when a term file cannot be used. Never swallowed — see the class comment. */
    public static final class TermFormatException extends RuntimeException {
        public TermFormatException(String message) {
            super(message);
        }
    }
}
