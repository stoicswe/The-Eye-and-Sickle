package io.github.stoicswe.eyeandsickle.client.shell;

/**
 * The exit statuses this shell can produce, from {@code docs/client/04-terminology-and-education.md}
 * §3.5.
 *
 * <h2>Three of these are borrowed from a real header, and that is the point</h2>
 *
 * {@code 69}, {@code 75} and {@code 77} are {@code EX_UNAVAILABLE}, {@code EX_TEMPFAIL} and
 * {@code EX_NOPERM} from {@code sysexits.h} — a real file shipped on BSD and macOS systems, with
 * exactly these numbers. {@code 126}, {@code 127} and {@code 128+N} are shell conventions with
 * exactly these meanings. A player who learns them here has learned them.
 *
 * <h2>Why 1 and 69 must never merge</h2>
 *
 * {@code 1} means the request arrived and a rule declined it. {@code 69} means it never arrived.
 * {@code docs/client/01-visual-language.md} §9.4 requires those two to stay distinguishable, and
 * giving them different numbers makes that structural rather than a matter of copywriting discipline
 * — which is Invariant I14 rendered as an integer.
 */
public final class ExitStatus {

    private ExitStatus() {}

    public static final int OK = 0;
    public static final int REFUSED = 1;
    public static final int USAGE = 2;
    public static final int UNAVAILABLE = 69;
    public static final int TEMPFAIL = 75;
    public static final int NOPERM = 77;
    public static final int CANNOT_FIELD = 126;
    public static final int NO_SUCH_COMMAND = 127;
    public static final int ABORTED = 130;

    /** A short name for the status line, so `$?` is legible without a lookup. */
    public static String name(int status) {
        return switch (status) {
            case OK -> "ok";
            case REFUSED -> "refused";
            case USAGE -> "usage";
            case UNAVAILABLE -> "EX_UNAVAILABLE";
            case TEMPFAIL -> "EX_TEMPFAIL";
            case NOPERM -> "EX_NOPERM";
            case CANNOT_FIELD -> "cannot field";
            case NO_SUCH_COMMAND -> "no such command";
            case ABORTED -> "aborted (128+SIGINT)";
            default -> "status " + status;
        };
    }
}
