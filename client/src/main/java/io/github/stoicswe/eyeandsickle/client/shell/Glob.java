package io.github.stoicswe.eyeandsickle.client.shell;

/**
 * Glob matching over the virtual namespace — {@code docs/client/04} §3.8.
 *
 * <h2>This is a glob, not a regular expression, and the difference is taught by containing both</h2>
 *
 * Here {@code *} means "any run of characters". In {@code grep}'s pattern it means "zero or more of
 * the thing before it", and "any characters" is {@code .*}. Beginners conflate these constantly, and
 * because this shell has globs in path position and regexes in {@code grep} position, the confusion
 * <em>will</em> arise in play — which is exactly when it is worth something. {@code glob(7)} and
 * {@code grep(1)} each answer it with the other in {@code SEE ALSO}.
 *
 * <p>Supported: {@code *}, {@code ?}, {@code [abc]}, {@code [a-z]}, {@code [!abc]}. Deliberately not
 * supported: {@code **}, which real shells gate behind {@code globstar} and which would let a pattern
 * descend the namespace in a way no game surface needs.
 */
public final class Glob {

    private Glob() {}

    /** Whether {@code text} matches {@code pattern}. Matching is case-insensitive, per §3.3. */
    public static boolean matches(String pattern, String text) {
        return match(pattern, 0, text, 0);
    }

    public static boolean isGlob(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0 || s.indexOf('[') >= 0;
    }

    private static boolean match(String p, int pi, String t, int ti) {
        while (pi < p.length()) {
            char pc = p.charAt(pi);
            switch (pc) {
                case '*' -> {
                    // Collapse a run of stars; ** is not special here, it is just *.
                    while (pi < p.length() && p.charAt(pi) == '*') {
                        pi++;
                    }
                    if (pi == p.length()) {
                        return true;
                    }
                    for (int skip = ti; skip <= t.length(); skip++) {
                        if (match(p, pi, t, skip)) {
                            return true;
                        }
                    }
                    return false;
                }
                case '?' -> {
                    if (ti >= t.length()) {
                        return false;
                    }
                    pi++;
                    ti++;
                }
                case '[' -> {
                    if (ti >= t.length()) {
                        return false;
                    }
                    int close = p.indexOf(']', pi + 1);
                    if (close < 0) {
                        // An unterminated class is a literal bracket, which is what real shells do
                        // rather than erroring — the pattern simply matches nothing useful.
                        if (Character.toLowerCase(t.charAt(ti)) != '[') {
                            return false;
                        }
                        pi++;
                        ti++;
                        break;
                    }
                    String set = p.substring(pi + 1, close);
                    boolean negated = set.startsWith("!");
                    if (negated) {
                        set = set.substring(1);
                    }
                    boolean hit = inSet(set, Character.toLowerCase(t.charAt(ti)));
                    if (hit == negated) {
                        return false;
                    }
                    pi = close + 1;
                    ti++;
                }
                default -> {
                    if (ti >= t.length() || Character.toLowerCase(pc) != Character.toLowerCase(t.charAt(ti))) {
                        return false;
                    }
                    pi++;
                    ti++;
                }
            }
        }
        return ti == t.length();
    }

    private static boolean inSet(String set, char c) {
        for (int i = 0; i < set.length(); i++) {
            if (i + 2 < set.length() && set.charAt(i + 1) == '-') {
                char lo = Character.toLowerCase(set.charAt(i));
                char hi = Character.toLowerCase(set.charAt(i + 2));
                if (c >= lo && c <= hi) {
                    return true;
                }
                i += 2;
            } else if (Character.toLowerCase(set.charAt(i)) == c) {
                return true;
            }
        }
        return false;
    }
}
