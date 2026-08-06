package io.github.stoicswe.eyeandsickle.client.ui.markdown;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits markdown into styled runs, for both the highlighter and the reading view.
 *
 * <h2>⚠ EVERY RUN IS THE SAME MONOSPACE FACE AND SIZE, and that is the whole design</h2>
 *
 * The editor draws highlighted text by laying a {@code TextFlow} of these runs <em>exactly over</em>
 * the {@code TextArea} the player types into. That alignment is only free while every glyph occupies
 * the same cell — which is the same guarantee {@code CoreCage} and {@code AsciiCanvas} already lean
 * on ("because the face is monospace, overlaid Labels align"). So a run may change <b>colour</b> and
 * it may go <b>bold</b>, and it may never change size, face or spacing. A heading rendered larger in
 * the editor would shift every character after it and the caret would stop landing under the
 * pointer — silently, and only on lines containing markup.
 *
 * <p>⚠ The <b>reading</b> view has no such constraint and is free to change size, because nothing is
 * overlaid on it. That is why {@link Kind} names what a run <em>is</em> rather than how it looks:
 * one parse, two renderings, and no second parser to disagree with this one.
 *
 * <h2>What this is not</h2>
 *
 * Not a CommonMark implementation. It handles what somebody writing notes actually types — headings,
 * emphasis, code, lists, quotes, rules, links — and treats anything it does not recognise as plain
 * text rather than guessing. A note that renders slightly plainly is fine; a parser that mangles
 * what somebody wrote down is not, because this is the one window whose contents the game cannot
 * regenerate.
 */
public final class MarkdownSpans {

    private MarkdownSpans() {}

    /** What a run of text is. The editor maps these to colours; the reading view maps them to type. */
    public enum Kind {
        TEXT,
        /** The {@code #} characters and the heading text with them. */
        HEADING,
        /** The {@code **} or {@code __} markers and what they wrap. */
        STRONG,
        EMPHASIS,
        /** Inline {@code `code`} and fenced blocks alike. */
        CODE,
        /** A {@code -}, {@code *} or {@code 1.} at the head of a list item. */
        MARKER,
        QUOTE,
        LINK,
        /** A {@code ---} rule. */
        RULE
    }

    /**
     * One styled run.
     *
     * @param level the heading level 1–6, or 0. Only meaningful for {@link Kind#HEADING}.
     */
    public record Span(String text, Kind kind, int level) {
        public Span(String text, Kind kind) {
            this(text, kind, 0);
        }
    }

    /** One source line, already classified, with its runs. */
    public record Line(List<Span> spans, Kind block, int level) {}

    /**
     * Parses the whole document, line by line.
     *
     * <p>⚠ Line-based on purpose. The editor overlays this on a {@code TextArea} whose layout is
     * line-based, so a parser that merged lines into paragraphs would produce runs that no longer
     * correspond to what is on screen. Paragraph joining is the reading view's business and it does
     * it from these lines.
     */
    public static List<Line> parse(String markdown) {
        List<Line> out = new ArrayList<>();
        if (markdown == null) {
            return out;
        }
        boolean inFence = false;
        for (String raw : markdown.split("\n", -1)) {
            String line = raw;
            String trimmed = line.strip();

            if (trimmed.startsWith("```")) {
                // ⚠ The fence line itself is CODE, and the flag flips AFTER classifying it — so both
                // the opening and closing fence render as code rather than the closing one falling
                // out and being highlighted as prose.
                inFence = !inFence;
                out.add(new Line(List.of(new Span(line, Kind.CODE)), Kind.CODE, 0));
                continue;
            }
            if (inFence) {
                out.add(new Line(List.of(new Span(line, Kind.CODE)), Kind.CODE, 0));
                continue;
            }
            if (!trimmed.isEmpty() && trimmed.chars().allMatch(c -> c == '-') && trimmed.length() >= 3) {
                out.add(new Line(List.of(new Span(line, Kind.RULE)), Kind.RULE, 0));
                continue;
            }
            int hashes = 0;
            while (hashes < line.length() && line.charAt(hashes) == '#') {
                hashes++;
            }
            if (hashes >= 1 && hashes <= 6 && hashes < line.length() && line.charAt(hashes) == ' ') {
                out.add(new Line(List.of(new Span(line, Kind.HEADING, hashes)), Kind.HEADING, hashes));
                continue;
            }
            if (trimmed.startsWith("> ") || trimmed.equals(">")) {
                out.add(new Line(inline(line), Kind.QUOTE, 0));
                continue;
            }

            String marker = listMarker(line);
            if (!marker.isEmpty()) {
                List<Span> spans = new ArrayList<>();
                spans.add(new Span(marker, Kind.MARKER));
                spans.addAll(inline(line.substring(marker.length())));
                out.add(new Line(spans, Kind.TEXT, 0));
                continue;
            }
            out.add(new Line(inline(line), Kind.TEXT, 0));
        }
        return out;
    }

    /**
     * The leading bullet or number of a list item, INCLUDING its indent and trailing space.
     *
     * <p>Returned as the literal prefix rather than a boolean so the caller can emit it as its own
     * run and keep the character count identical to the source — which is what the overlay needs.
     */
    static String listMarker(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        if (i >= line.length()) {
            return "";
        }
        char c = line.charAt(i);
        if ((c == '-' || c == '*' || c == '+') && i + 1 < line.length() && line.charAt(i + 1) == ' ') {
            return line.substring(0, i + 2);
        }
        int digits = i;
        while (digits < line.length() && Character.isDigit(line.charAt(digits))) {
            digits++;
        }
        if (digits > i && digits + 1 < line.length() && line.charAt(digits) == '.' && line.charAt(digits + 1) == ' ') {
            return line.substring(0, digits + 2);
        }
        return "";
    }

    /**
     * Inline markup within one line: code, strong, emphasis, links.
     *
     * <h2>⚠ THE MARKERS ARE KEPT IN THE RUNS, and the editor would break without them</h2>
     *
     * {@code **bold**} yields one run holding all eight characters, not a four-character run with the
     * asterisks dropped. The overlay's alignment is character-for-character with the source the
     * player is typing, so a parse that removed anything would shift every glyph after it. The
     * reading view strips them at render time instead, where nothing is aligned to anything.
     */
    static List<Span> inline(String line) {
        List<Span> spans = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            int close;
            if (c == '`' && (close = line.indexOf('`', i + 1)) > i) {
                flush(spans, plain);
                spans.add(new Span(line.substring(i, close + 1), Kind.CODE));
                i = close + 1;
                continue;
            }
            if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                close = line.indexOf("**", i + 2);
                if (close > i) {
                    flush(spans, plain);
                    spans.add(new Span(line.substring(i, close + 2), Kind.STRONG));
                    i = close + 2;
                    continue;
                }
            }
            if ((c == '*' || c == '_') && i + 1 < line.length() && line.charAt(i + 1) != c) {
                close = line.indexOf(c, i + 1);
                if (close > i) {
                    flush(spans, plain);
                    spans.add(new Span(line.substring(i, close + 1), Kind.EMPHASIS));
                    i = close + 1;
                    continue;
                }
            }
            if (c == '[') {
                int endText = line.indexOf(']', i + 1);
                if (endText > i && endText + 1 < line.length() && line.charAt(endText + 1) == '(') {
                    int endUrl = line.indexOf(')', endText + 2);
                    if (endUrl > endText) {
                        flush(spans, plain);
                        spans.add(new Span(line.substring(i, endUrl + 1), Kind.LINK));
                        i = endUrl + 1;
                        continue;
                    }
                }
            }
            plain.append(c);
            i++;
        }
        flush(spans, plain);
        return spans;
    }

    private static void flush(List<Span> spans, StringBuilder plain) {
        if (plain.length() > 0) {
            spans.add(new Span(plain.toString(), Kind.TEXT));
            plain.setLength(0);
        }
    }

    /**
     * The visible text of a run, with its markers removed — for the READING view only.
     *
     * <p>⚠ Never call this from the editor overlay. Dropping characters there desynchronises the
     * highlight from the text underneath it, which is the one failure this whole class is arranged
     * to prevent.
     */
    public static String stripped(Span span) {
        String t = span.text();
        return switch (span.kind()) {
            case STRONG -> trimMarkers(t, 2);
            case EMPHASIS, CODE -> trimMarkers(t, 1);
            case LINK -> {
                int end = t.indexOf(']');
                yield end > 1 ? t.substring(1, end) : t;
            }
            default -> t;
        };
    }

    private static String trimMarkers(String text, int width) {
        return text.length() >= width * 2 ? text.substring(width, text.length() - width) : text;
    }
}
