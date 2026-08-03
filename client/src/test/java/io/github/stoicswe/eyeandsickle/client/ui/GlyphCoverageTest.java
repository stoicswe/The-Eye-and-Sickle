package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every character the client draws must exist in a bundled font.
 *
 * <h2>The failure this exists to stop</h2>
 *
 * {@code docs/design/ui-design-language.md} §2.2: "bundle the TTFs in {@code resources/fonts/}, do
 * not rely on system installs." The point of bundling is that the client looks the same on every
 * machine. A character that is <em>not</em> in the bundled font defeats that silently — the toolkit
 * substitutes a host font, and nothing anywhere reports it. On the developer's Mac it looks fine.
 *
 * <p>It is not only cosmetic. A substituted glyph brings its own advance width, so a
 * character-cell texture built from one renders at a different length per platform — and the
 * greeble strip, the cycle grid's neighbours and the boot log all assume a fixed cell.
 *
 * <p>This was found the hard way. Eleven codepoints the client emitted were in <b>neither</b>
 * bundled face, including the maximise control on every window title bar ({@code □}), the Shortcut
 * glyph in every key hint ({@code ⌘}), four of the six log severity glyphs, and most of the
 * greeble's alphabet.
 *
 * <h2>Martian Mono is much narrower than IBM Plex, and that has a rule attached</h2>
 *
 * Measured here: Martian maps ~638 codepoints, Plex ~1049. Martian has <b>none</b> of the block
 * elements or box-drawing range (U+2500–U+259F) that every texture in this client is built from. So
 * the rule is: <b>a glyph-based texture is pinned to IBM Plex; Martian is for uppercase Latin labels
 * only.</b> {@link Faces#texturesAreNotOnTheLabelFace()} checks the stylesheet actually does that.
 *
 * <h2>Why the cmap is parsed rather than asking JavaFX</h2>
 *
 * {@code Font.loadFont} succeeds whether or not a face contains the glyph you are about to draw
 * with it, and JavaFX exposes no per-codepoint coverage query. Walking the {@code cmap} is the only
 * way to answer the question — and it runs headless, which a font test has to.
 */
class GlyphCoverageTest {

    private static final Path FONTS = Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/fonts");

    private static final String PLEX = "IBMPlexMono-Regular.ttf";
    private static final String MARTIAN = "MartianMono-Medium.ttf";

    // ── cmap parsing ─────────────────────────────────────────────────────────────────────────

    /**
     * The set of Unicode codepoints a TrueType file maps to a real glyph.
     *
     * <p>Reads the {@code cmap} table, preferring a format-4 or format-12 Unicode subtable. Format 4
     * is the segmented BMP mapping every font here uses; the {@code idRangeOffset} branch is the
     * fiddly half and is where a naive parser silently returns an empty set.
     */
    private static Set<Integer> coverage(String fileName) throws IOException {
        byte[] bytes = Files.readAllBytes(FONTS.resolve(fileName));
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        int tableCount = Short.toUnsignedInt(buffer.getShort(4));
        int cmapOffset = -1;
        for (int i = 0; i < tableCount; i++) {
            int record = 12 + 16 * i;
            String tag = new String(bytes, record, 4, StandardCharsets.US_ASCII);
            if ("cmap".equals(tag)) {
                cmapOffset = buffer.getInt(record + 8);
            }
        }
        assertThat(cmapOffset).as("%s has a cmap table", fileName).isNotEqualTo(-1);

        int subtables = Short.toUnsignedInt(buffer.getShort(cmapOffset + 2));
        int best = -1;
        for (int i = 0; i < subtables; i++) {
            int record = cmapOffset + 4 + 8 * i;
            int platform = Short.toUnsignedInt(buffer.getShort(record));
            int encoding = Short.toUnsignedInt(buffer.getShort(record + 2));
            int offset = buffer.getInt(record + 4);
            boolean unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10));
            int format = Short.toUnsignedInt(buffer.getShort(cmapOffset + offset));
            if (unicode && (format == 4 || format == 12)) {
                best = cmapOffset + offset;
            }
        }
        assertThat(best).as("%s has a Unicode cmap subtable", fileName).isNotEqualTo(-1);

        Set<Integer> mapped = new HashSet<>();
        int format = Short.toUnsignedInt(buffer.getShort(best));
        if (format == 12) {
            int groups = buffer.getInt(best + 12);
            for (int i = 0; i < groups; i++) {
                int group = best + 16 + 12 * i;
                int start = buffer.getInt(group);
                int end = buffer.getInt(group + 4);
                for (int c = start; c <= end && c - start < 0x10000; c++) {
                    mapped.add(c);
                }
            }
            return mapped;
        }

        int segCountX2 = Short.toUnsignedInt(buffer.getShort(best + 6));
        int segments = segCountX2 / 2;
        int endsAt = best + 14;
        int startsAt = endsAt + segCountX2 + 2;
        int deltasAt = startsAt + segCountX2;
        int rangeOffsetsAt = deltasAt + segCountX2;

        for (int i = 0; i < segments; i++) {
            int end = Short.toUnsignedInt(buffer.getShort(endsAt + 2 * i));
            int start = Short.toUnsignedInt(buffer.getShort(startsAt + 2 * i));
            int delta = buffer.getShort(deltasAt + 2 * i);
            int rangeOffset = Short.toUnsignedInt(buffer.getShort(rangeOffsetsAt + 2 * i));
            for (int c = start; c <= end && c != 0xFFFF; c++) {
                int glyph;
                if (rangeOffset == 0) {
                    glyph = (c + delta) & 0xFFFF;
                } else {
                    int at = rangeOffsetsAt + 2 * i + rangeOffset + 2 * (c - start);
                    if (at + 2 > bytes.length) {
                        continue;
                    }
                    glyph = Short.toUnsignedInt(buffer.getShort(at));
                    if (glyph != 0) {
                        glyph = (glyph + delta) & 0xFFFF;
                    }
                }
                if (glyph != 0) {
                    mapped.add(c);
                }
            }
        }
        return mapped;
    }

    // ── source scanning ──────────────────────────────────────────────────────────────────────

    /** String and char literals only. Comments are stripped — they discuss the banned glyphs. */
    private static final Pattern LITERAL = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)'");

    /**
     * Every non-ASCII character the client or the solo engine can put on screen.
     *
     * <p>Solo is included because {@code RigEvent}'s severity glyphs live there and are rendered by
     * the client's log and notice surfaces — a module boundary is not a rendering boundary.
     */
    private static TreeMap<Integer, Set<String>> emittedCharacters() throws IOException {
        TreeMap<Integer, Set<String>> found = new TreeMap<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("../solo/src/main/java"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file :
                        files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file, StandardCharsets.UTF_8)
                            .replaceAll("(?s)/\\*.*?\\*/", "")
                            .replaceAll("(?m)//.*$", "");
                    Matcher matcher = LITERAL.matcher(source);
                    while (matcher.find()) {
                        for (char c : matcher.group().toCharArray()) {
                            if (c > 127) {
                                found.computeIfAbsent((int) c, k -> new HashSet<>())
                                        .add(file.getFileName().toString());
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    @Nested
    @DisplayName("§2.2 — the bundled fonts cover everything the client draws")
    class Coverage {

        @Test
        @DisplayName("every character the client emits is in IBM Plex Mono")
        void everyEmittedCharacterIsCovered() throws IOException {
            // IBM Plex is the body face and the one every glyph texture is pinned to, so it is the
            // face that has to cover everything. A character missing here is drawn by a host-OS
            // fallback: different shape, different advance width, different on every platform.
            Set<Integer> plex = coverage(PLEX);
            List<String> uncovered = new ArrayList<>();
            for (var entry : emittedCharacters().entrySet()) {
                if (!plex.contains(entry.getKey())) {
                    uncovered.add(String.format(
                            "U+%04X '%c' in %s",
                            entry.getKey(), (char) (int) entry.getKey(), String.join(", ", entry.getValue())));
                }
            }
            assertThat(uncovered)
                    .as("characters absent from the bundled font — see this class's comment")
                    .isEmpty();
        }

        @Test
        @DisplayName("the severity glyphs are in BOTH faces, since they land on either")
        void severityGlyphsAreUniversal() throws IOException {
            // Log lines render in the body face and notice glyphs inherit the root; both are Plex
            // today, but these are the six characters most likely to be moved to a label at some
            // point, and a glyph that only works in one face is a trap laid for that change.
            Set<Integer> plex = coverage(PLEX);
            Set<Integer> martian = coverage(MARTIAN);
            for (int severity = 0; severity <= 7; severity++) {
                String glyph = io.github.stoicswe.eyeandsickle.engine.state.RigEvent.glyph(severity);
                for (char c : glyph.toCharArray()) {
                    assertThat(plex)
                            .as("severity %d glyph '%c' in Plex", severity, c)
                            .contains((int) c);
                    assertThat(martian)
                            .as("severity %d glyph '%c' in Martian", severity, c)
                            .contains((int) c);
                }
            }
        }
    }

    @Nested
    @DisplayName("the two faces are not interchangeable")
    class Faces {

        @Test
        @DisplayName("Martian Mono has none of the box-drawing range, and that is why textures are pinned")
        void martianLacksBoxDrawing() throws IOException {
            // Measured, and the reason for the pinning rule rather than an assumption behind it.
            Set<Integer> martian = coverage(MARTIAN);
            Set<Integer> plex = coverage(PLEX);
            for (char c : new char[] {'█', '▌', '▐', '░', '▒', '▓', '╱', '╲', '▚'}) {
                assertThat(martian).as("Martian does NOT have '%c'", c).doesNotContain((int) c);
                assertThat(plex).as("Plex does have '%c'", c).contains((int) c);
            }
            assertThat(martian.size())
                    .as("Martian's coverage is materially narrower than Plex's")
                    .isLessThan(plex.size());
        }

        @Test
        @DisplayName("no glyph texture is styled with the label face")
        void texturesAreNotOnTheLabelFace() throws IOException {
            // The rule this enforces: Martian is for uppercase Latin labels only. Every one of these
            // classes draws box-drawing or block characters, and styling any of them with Martian
            // silently sends those characters to a host-OS fallback.
            String css =
                    Files.readString(Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/ui/theme.css"));
            for (String textureClass : List.of(".es-greeble", ".es-boot-logo", ".es-cage", ".es-substrate-field")) {
                String rule = ruleFor(css, textureClass);
                assertThat(rule)
                        .as("%s must be pinned to IBM Plex, not the label face", textureClass)
                        .contains("IBM Plex Mono")
                        .doesNotContain("Martian Mono");
            }
        }

        /** The declaration block for a selector, comments stripped. */
        private static String ruleFor(String css, String selector) {
            String body = css.replaceAll("(?s)/\\*.*?\\*/", "");
            int at = body.indexOf(selector + " {");
            if (at < 0) {
                at = body.indexOf(selector + "{");
            }
            assertThat(at).as("theme.css declares %s", selector).isNotEqualTo(-1);
            int close = body.indexOf('}', at);
            return body.substring(at, close < 0 ? body.length() : close);
        }
    }

    @Nested
    @DisplayName("the fonts are actually shipped")
    class Packaging {

        @Test
        @DisplayName("every bundled TTF is a real font with a parseable cmap")
        void allFontsParse() throws IOException {
            for (String file : List.of(
                    "MartianMono-Medium.ttf",
                    "MartianMono-Bold.ttf",
                    "IBMPlexMono-Light.ttf",
                    "IBMPlexMono-Regular.ttf",
                    "IBMPlexMono-Medium.ttf")) {
                assertThat(coverage(file)).as("%s maps codepoints", file).isNotEmpty();
                try (InputStream in =
                        getClass().getResourceAsStream("/io/github/stoicswe/eyeandsickle/client/fonts/" + file)) {
                    assertThat(in)
                            .as("%s is on the classpath, not just on disk", file)
                            .isNotNull();
                }
            }
        }
    }
}
