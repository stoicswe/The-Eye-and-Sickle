package io.github.stoicswe.eyeandsickle.client.teaching;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One manual page, parsed from a term file.
 *
 * <h2>The shape is a real man page, and that is the whole educational bet</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §4.3.1 fixes the section set against
 * {@code man-pages(7)}: {@code NAME}, {@code SYNOPSIS}, {@code DESCRIPTION}, {@code OPTIONS},
 * {@code EXIT STATUS}, {@code SEE ALSO} are real sections in the real order. Two are game additions
 * — {@code REAL-WORLD COUNTERPART} and {@code FURTHER READING} — and they are <em>visually marked as
 * such</em>, because a player who later opens a real man page and finds no counterpart section should
 * not conclude their memory is faulty.
 *
 * <p>By the time somebody has played for a while they will have read several hundred of these. When
 * they open a real one, they will already know where to look. That is the cheapest transferable skill
 * in the client and it costs nothing but keeping the shape honest.
 *
 * <h2>{@code NAME} is not stored</h2>
 *
 * It is derived from {@link #name()} and {@link #gloss()}, and {@code SEE ALSO} from {@link #seeAlso()}.
 * §4.3.1 requires the {@code NAME} line and the gloss bar to be the same string — one source, two
 * surfaces — and the only way to guarantee that is to have one source.
 */
public record TermPage(
        String id,
        int section,
        String name,
        String canonical,
        String gloss,
        Status status,
        List<String> aliases,
        String glossary,
        List<String> seeAlso,
        List<String> reading,
        String notes,
        int revision,
        Map<String, String> body) {

    public TermPage {
        aliases = List.copyOf(aliases);
        seeAlso = List.copyOf(seeAlso);
        reading = List.copyOf(reading);
        body = Map.copyOf(body);
    }

    /**
     * The honesty field.
     *
     * <p>{@code docs/client/04} §4.7 makes the labelling mechanical rather than aspirational, and the
     * {@code man} window offers a filter on it: a player who wants to know exactly what this game made
     * up is entitled to a one-click answer. Being able to give one is the strongest possible statement
     * that the labelling is honest.
     */
    public enum Status {
        REAL("real", "This is real. It works this way outside the game too."),
        REAL_SIMPLIFIED("real, simplified", "Real, but narrower here than outside. See CAVEATS."),
        GAME("game", "This one is ours. There is no real counterpart.");

        private final String label;
        private final String explanation;

        Status(String label, String explanation) {
            this.label = label;
            this.explanation = explanation;
        }

        public String label() {
            return label;
        }

        public String explanation() {
            return explanation;
        }

        public static Status parse(String raw) {
            String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "real" -> REAL;
                case "real, simplified", "real,simplified" -> REAL_SIMPLIFIED;
                case "game" -> GAME;
                default -> throw new IllegalArgumentException(
                        "Unknown status '" + raw + "' — must be real, 'real, simplified' or game");
            };
        }
    }

    /** How a page is addressed: {@code compute(7)}. */
    public String reference() {
        return id + "(" + section + ")";
    }

    /** The {@code NAME} line, built from one source so it cannot disagree with the gloss bar. */
    public String nameLine() {
        return name + " — " + gloss;
    }

    public Optional<String> bodySection(String heading) {
        return Optional.ofNullable(body.get(heading));
    }

    /** Whether a section was added by this game rather than inherited from {@code man-pages(7)}. */
    public static boolean isGameAdded(String heading) {
        return "REAL-WORLD COUNTERPART".equals(heading) || "FURTHER READING".equals(heading);
    }

    /**
     * The section order a real man page uses.
     *
     * <p>Fixed rather than file-order, so a page whose author wrote the sections out of sequence still
     * renders in the order a reader's eye expects — which is the order every real page uses.
     */
    public static final List<String> SECTION_ORDER = List.of(
            "SYNOPSIS", "DESCRIPTION", "OPTIONS", "EXIT STATUS", "REAL-WORLD COUNTERPART", "CAVEATS");

    /** Body sections in canonical order, skipping any this page does not carry. */
    public Map<String, String> orderedBody() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String heading : SECTION_ORDER) {
            String content = body.get(heading);
            if (content != null && !content.isBlank()) {
                out.put(heading, content);
            }
        }
        return out;
    }

    /** What the section number means, so the numbering teaches rather than decorates. */
    public String sectionMeaning() {
        return switch (section) {
            case 1 -> "commands you run";
            case 5 -> "record and file formats";
            case 6 -> "games";
            case 7 -> "concepts";
            case 8 -> "maintaining your own rig";
            default -> "section " + section;
        };
    }
}
