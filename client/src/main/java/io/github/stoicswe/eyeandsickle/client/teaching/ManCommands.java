package io.github.stoicswe.eyeandsickle.client.teaching;

import io.github.stoicswe.eyeandsickle.client.shell.Command;
import io.github.stoicswe.eyeandsickle.client.shell.ExitStatus;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code man}, {@code whatis} and {@code apropos} — the three commands that make everything else
 * self-documenting.
 *
 * <p>{@code docs/client/00-client-overview.md} §5.2: <b>definitions are never destroyed, only
 * quieted.</b> These work at every teaching level, including {@code off}. The level governs whether
 * definitions arrive unbidden; it never governs whether they can be asked for.
 */
public final class ManCommands {

    private ManCommands() {}

    public static void register(Shell.CommandRegistry registry, TermDatabase terms) {
        registry.add(new ManCommand(terms));
        registry.add(new AproposCommand(terms, "apropos", false));
        registry.add(new AproposCommand(terms, "whatis", true));
    }

    /**
     * Renders a page in real man-page shape.
     *
     * <p>Public because the {@code man} window renders the same text the {@code man} command prints.
     * One renderer, two surfaces — the alternative is two formatters that agree until somebody
     * improves one of them.
     */
    public static List<String> render(TermPage page) {
        List<String> out = new ArrayList<>();
        String header = page.reference().toUpperCase(java.util.Locale.ROOT);
        out.add(header + "    " + page.sectionMeaning() + "    " + header);
        out.add("");
        out.add("NAME");
        out.add("       " + page.nameLine());
        out.add("");

        for (Map.Entry<String, String> section : page.orderedBody().entrySet()) {
            // Game-added sections are marked, so a player who later opens a real man page and finds
            // no REAL-WORLD COUNTERPART does not conclude their memory is faulty (§4.3.1).
            out.add(section.getKey() + (TermPage.isGameAdded(section.getKey()) ? "    [added by this game]" : ""));
            if ("REAL-WORLD COUNTERPART".equals(section.getKey())) {
                out.add("       status: " + page.status().label() + " — "
                        + page.status().explanation());
                out.add("");
            }
            for (String line : section.getValue().split("\n")) {
                out.add(line.isBlank() ? "" : (line.startsWith("       ") ? line : "       " + line));
            }
            out.add("");
        }

        if (!page.seeAlso().isEmpty()) {
            out.add("SEE ALSO");
            out.add("       " + String.join(", ", page.seeAlso()));
            out.add("");
        }
        if (!page.reading().isEmpty()) {
            out.add("FURTHER READING    [added by this game]");
            for (String citation : page.reading()) {
                out.add("       " + citation);
            }
            out.add("");
        }
        return out;
    }

    private record ManCommand(TermDatabase terms) implements Command {

        @Override
        public String name() {
            return "man";
        }

        @Override
        public String synopsis() {
            return "Open the manual for a term. `man 7 compute` picks a section.";
        }

        @Override
        public boolean hasSideEffect() {
            return false;
        }

        @Override
        public Output run(Invocation invocation) {
            String query = String.join(" ", invocation.stage().arguments());
            if (query.isBlank()) {
                List<String> out = new ArrayList<>();
                out.add("What are you looking for? Try:");
                out.add("");
                for (int section : new int[] {1, 5, 7, 8}) {
                    List<TermPage> inSection = terms.inSection(section);
                    if (inSection.isEmpty()) {
                        continue;
                    }
                    out.add("  section " + section + " — "
                            + inSection.getFirst().sectionMeaning());
                    for (TermPage page : inSection) {
                        out.add("      " + page.id());
                    }
                }
                out.add("");
                out.add("`apropos <text>` searches by description when you do not know the name.");
                return Output.ok(out);
            }
            return terms.find(query)
                    .map(page -> Output.ok(render(page)))
                    .orElseGet(() -> new Output(
                            List.of(
                                    "No manual entry for " + query,
                                    "",
                                    "Try `apropos " + query + "` to search by description instead."),
                            ExitStatus.REFUSED));
        }
    }

    private record AproposCommand(TermDatabase terms, String name, boolean exact) implements Command {

        @Override
        public String name() {
            return name;
        }

        @Override
        public String synopsis() {
            return exact
                    ? "Print the one-line description of a page you can name."
                    : "Search every page's description. --all searches the full text.";
        }

        @Override
        public boolean hasSideEffect() {
            return false;
        }

        @Override
        public Output run(Invocation invocation) {
            String query = String.join(" ", invocation.stage().arguments());
            if (query.isBlank()) {
                return Output.usage(name + " <text>");
            }
            List<TermPage> hits = exact
                    ? terms.whatis(query)
                    : terms.apropos(query, invocation.stage().hasFlag("all"));
            if (hits.isEmpty()) {
                return new Output(List.of(name + ": nothing appropriate."), ExitStatus.REFUSED);
            }
            List<String> out = new ArrayList<>();
            for (TermPage page : hits) {
                out.add(pad(page.reference(), 24) + "- " + page.gloss());
            }
            return Output.ok(out);
        }

        private static String pad(String s, int width) {
            return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
        }
    }
}
