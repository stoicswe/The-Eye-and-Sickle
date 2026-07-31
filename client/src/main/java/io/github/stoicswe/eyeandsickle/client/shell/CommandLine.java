package io.github.stoicswe.eyeandsickle.client.shell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One parsed command line: a pipeline of stages, each with a verb, flags and arguments.
 *
 * <h2>A closed AST is the security boundary, not a parsing convenience</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.1 is unambiguous: <em>the client never
 * executes a host command, reads a host path, or touches a host process.</em> The parser produces
 * this type and nothing else — there is no node that can carry an arbitrary string to an executor, no
 * fallthrough for an unrecognised verb (that is exit {@code 127}), and no escape hatch added later
 * "just for debugging".
 *
 * <p>The things a real shell would parse and this deliberately refuses — {@code >}, {@code >>},
 * {@code <}, {@code ;}, {@code &&}, {@code ||}, {@code &}, backticks, {@code $( )} — are rejected with
 * a <em>specific</em> message naming what was found. A generic syntax error invites the player to
 * keep guessing at a capability that does not exist; a message that says "redirection is not
 * available — this is a game surface, not a shell" teaches the boundary instead.
 */
public record CommandLine(List<Stage> stages, String raw) {

    public CommandLine {
        stages = List.copyOf(stages);
    }

    public Stage first() {
        return stages.getFirst();
    }

    public boolean isPipeline() {
        return stages.size() > 1;
    }

    /** One stage of a pipeline: a verb plus what it was given. */
    public record Stage(String verb, List<String> arguments, Map<String, String> flags, String raw) {

        public Stage {
            arguments = List.copyOf(arguments);
            flags = Map.copyOf(flags);
        }

        public boolean hasFlag(String name) {
            return flags.containsKey(name);
        }

        public Optional<String> flag(String name) {
            return Optional.ofNullable(flags.get(name));
        }

        /** The first positional argument, if any. */
        public Optional<String> argument(int index) {
            return index < arguments.size() ? Optional.of(arguments.get(index)) : Optional.empty();
        }

        /** True when any of the five universal flags asks for help rather than execution. */
        public boolean wantsHelp() {
            return hasFlag("h") || hasFlag("help");
        }

        public boolean wantsExplanation() {
            return hasFlag("explain");
        }

        public boolean isDryRun() {
            return hasFlag("n") || hasFlag("dry-run");
        }

        public boolean isVerbose() {
            return hasFlag("v") || hasFlag("verbose");
        }
    }

    /** Thrown when a line cannot be parsed. Carries the message the terminal should print. */
    public static final class ParseException extends RuntimeException {
        private final int status;

        public ParseException(String message) {
            this(message, ExitStatus.USAGE);
        }

        public ParseException(String message, int status) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    // ------------------------------------------------------------------ parsing

    /** Syntax this shell recognises only in order to refuse it, with the message it refuses with. */
    private static final Map<String, String> REFUSED_SYNTAX = new LinkedHashMap<>();

    static {
        REFUSED_SYNTAX.put(">>", "Redirection is not available — this is a game surface, not a shell. See shell(7).");
        REFUSED_SYNTAX.put(">", "Redirection is not available — this is a game surface, not a shell. See shell(7).");
        REFUSED_SYNTAX.put("<", "Redirection is not available — this is a game surface, not a shell. See shell(7).");
        REFUSED_SYNTAX.put(
                "&&", "Command chaining is not available here. Run the commands one at a time. See shell(7).");
        REFUSED_SYNTAX.put(
                "||", "Command chaining is not available here. Run the commands one at a time. See shell(7).");
        REFUSED_SYNTAX.put(
                ";", "Command chaining is not available here. Run the commands one at a time. See shell(7).");
        REFUSED_SYNTAX.put("$(", "Command substitution is not available here. See shell(7).");
        REFUSED_SYNTAX.put("`", "Command substitution is not available here. See shell(7).");
        REFUSED_SYNTAX.put(
                "&", "Background execution is not available here — bots are the game's version. See jobs(1).");
    }

    /**
     * Parses a line into a pipeline.
     *
     * @throws ParseException with a message written to be read by the player
     */
    public static CommandLine parse(String line) {
        if (line == null || line.isBlank()) {
            throw new ParseException("");
        }
        String trimmed = line.trim();

        for (Map.Entry<String, String> refused : REFUSED_SYNTAX.entrySet()) {
            if (containsOutsideQuotes(trimmed, refused.getKey())) {
                throw new ParseException(refused.getValue());
            }
        }

        List<Stage> stages = new ArrayList<>();
        for (String segment : splitOutsideQuotes(trimmed, '|')) {
            String piece = segment.trim();
            if (piece.isEmpty()) {
                throw new ParseException("Empty pipeline stage — a `|` needs a command on both sides.");
            }
            stages.add(parseStage(piece));
        }
        return new CommandLine(stages, trimmed);
    }

    private static Stage parseStage(String segment) {
        List<String> words = tokenize(segment);
        if (words.isEmpty()) {
            throw new ParseException("Empty command.");
        }
        // Case-insensitive matching, per §3.3. A real shell is case-sensitive and shell(7)'s CAVEATS
        // says so; we are forgiving because typing accurately under a trace timer is not the skill
        // this game is testing (pillar C5).
        String verb = words.getFirst().toLowerCase(java.util.Locale.ROOT);

        List<String> arguments = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();
        boolean endOfOptions = false;

        for (int i = 1; i < words.size(); i++) {
            String word = words.get(i);
            if (endOfOptions || !word.startsWith("-") || word.equals("-")) {
                arguments.add(word);
                continue;
            }
            if (word.equals("--")) {
                // Needed for real reasons here: a node address or a handle may begin with `-`.
                endOfOptions = true;
                continue;
            }
            if (word.startsWith("--")) {
                String body = word.substring(2);
                int eq = body.indexOf('=');
                if (eq >= 0) {
                    flags.put(body.substring(0, eq).toLowerCase(java.util.Locale.ROOT), body.substring(eq + 1));
                } else if (i + 1 < words.size() && !words.get(i + 1).startsWith("-")) {
                    flags.put(body.toLowerCase(java.util.Locale.ROOT), words.get(++i));
                } else {
                    flags.put(body.toLowerCase(java.util.Locale.ROOT), "");
                }
            } else {
                // Short flags cluster: -vn is -v -n, which is POSIX guideline 5 and worth teaching.
                String cluster = word.substring(1);
                for (int c = 0; c < cluster.length(); c++) {
                    String name = String.valueOf(Character.toLowerCase(cluster.charAt(c)));
                    boolean last = c == cluster.length() - 1;
                    if (last && i + 1 < words.size() && !words.get(i + 1).startsWith("-") && takesValue(name)) {
                        flags.put(name, words.get(++i));
                    } else {
                        flags.put(name, "");
                    }
                }
            }
        }
        return new Stage(verb, arguments, flags, segment);
    }

    /** Short flags that consume the next word. Kept tiny and explicit rather than inferred. */
    private static boolean takesValue(String shortFlag) {
        return switch (shortFlag) {
            case "n", "k", "f", "t" -> true;
            default -> false;
        };
    }

    /**
     * Splits a line into words, honouring quotes.
     *
     * <p>Both quote characters are fully literal here, which is the largest deliberate divergence in
     * the whole surface: a real shell interpolates inside double quotes. There is nothing to
     * interpolate — §3.1 rule 4 forbids environment expansion, partly so the player's OS username
     * never leaks into a screenshot — and {@code quoting(7)}'s CAVEATS states the real behaviour in
     * full so nothing false is learned.
     */
    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean any = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
                any = true;
            } else if (c == '\'' || c == '"') {
                quote = c;
                any = true;
            } else if (Character.isWhitespace(c)) {
                if (any) {
                    out.add(current.toString());
                    current.setLength(0);
                    any = false;
                }
            } else {
                current.append(c);
                any = true;
            }
        }
        if (quote != 0) {
            throw new ParseException("Unclosed " + (quote == '\'' ? "single" : "double") + " quote.");
        }
        if (any) {
            out.add(current.toString());
        }
        return out;
    }

    private static boolean containsOutsideQuotes(String s, String needle) {
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (s.startsWith(needle, i)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitOutsideQuotes(String s, char delimiter) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                current.append(c);
            } else if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
            } else if (c == delimiter) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }
}
