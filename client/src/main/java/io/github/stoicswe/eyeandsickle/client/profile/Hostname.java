package io.github.stoicswe.eyeandsickle.client.profile;

import java.util.Locale;

/**
 * The rig's name on the network, and the shell prompt built from it.
 *
 * <h2>Why the prompt is {@code user@host} and not {@code host@user}</h2>
 *
 * The command strip used to read {@code rig@operator:~$}, which is backwards: everywhere a player
 * will ever meet this string — a terminal on their own machine, an SSH session, a screenshot in a
 * tutorial — it is <b>who</b> you are, then <b>where</b> you are. Reading it the other way round
 * teaches the wrong half of one of the most-seen strings in computing, and it teaches it silently,
 * because a prompt is furniture and nobody stops to parse furniture.
 *
 * <p>The {@code .local} suffix is mDNS — the name a machine answers to on the network it is
 * plugged into, without anybody having configured DNS. It is real, it is what a Mac or a Linux box
 * with Avahi actually calls itself, and it is one of the few pieces of naming a player can go and
 * verify on their own machine in ten seconds.
 *
 * <h2>The rules here are DNS's rules, not invented ones</h2>
 *
 * {@link #problem} enforces RFC 1123's preferred syntax for a host label: letters, digits and
 * hyphens; no leading or trailing hyphen; 63 characters at most; case-insensitive, so it is stored
 * lowercased. Those are the real constraints, which means a refusal here is teaching something
 * true rather than defending an implementation detail — and it is why underscores are refused even
 * though nothing in this client would break on one.
 *
 * <p>⚠ Not to be confused with the operator handle, which is validated separately and by different
 * rules ({@code Views.validateHandle}: printable ASCII, 24 characters, and it may contain anything
 * a person wants to be called). A handle is a name for a person and a hostname is a name for a
 * machine; they answer to different standards and merging the two validators would loosen one of
 * them.
 */
public final class Hostname {

    private Hostname() {}

    /** What a rig is called before anybody renames it. Kept from the old prompt on purpose. */
    public static final String DEFAULT = "rig";

    /** The mDNS suffix the prompt appends. Stored names never contain it — see {@link #sanitise}. */
    public static final String SUFFIX = ".local";

    /** RFC 1123's ceiling for a single label. */
    public static final int MAX_LENGTH = 63;

    /**
     * Why this is not a usable hostname, or null when it is.
     *
     * <p>Sentences rather than codes, because this reaches the player directly and a settings panel
     * that answers "invalid" has told them nothing they can act on.
     */
    public static String problem(String hostname) {
        String name = strip(hostname);
        if (name.isEmpty()) {
            return "A hostname cannot be blank.";
        }
        if (name.length() > MAX_LENGTH) {
            return "Too long — " + MAX_LENGTH + " characters at most. That is DNS's own limit on "
                    + "one label, not this game's.";
        }
        if (name.startsWith("-") || name.endsWith("-")) {
            return "A hostname cannot start or end with a hyphen.";
        }
        for (char c : name.toCharArray()) {
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
            if (!allowed) {
                return "Letters, digits and hyphens only. Those are the characters DNS allows in a "
                        + "host label — underscores included, which is why one is refused here even "
                        + "though nothing would break on it.";
            }
        }
        return null;
    }

    /**
     * The stored form of a typed name: lowercased, trimmed, with any {@code .local} taken off.
     *
     * <p>⚠ The suffix is stripped rather than kept, and that is the fix for the obvious bug: the
     * prompt appends {@code .local}, so a player who helpfully types {@code rig.local} would
     * otherwise be greeted by {@code rig.local.local} and reasonably conclude the field was broken.
     *
     * @return the normalised name, or {@link #DEFAULT} when there is nothing usable. Never null and
     *     never blank — a prompt with a hole in it is not a state any caller should have to handle
     */
    public static String sanitise(String hostname) {
        String name = strip(hostname);
        return problem(name) == null ? name.toLowerCase(Locale.ROOT) : DEFAULT;
    }

    /** The full network name, suffix included — what the prompt and any readout should show. */
    public static String qualified(String hostname) {
        return sanitise(hostname) + SUFFIX;
    }

    /**
     * The whole prompt: {@code operator@rig.local:~$}.
     *
     * <p>One function rather than a concatenation at each call site, so the command strip and
     * anything else that ever draws a prompt cannot come to disagree about the order of the two
     * names — which is the thing this class exists to have got right once.
     */
    public static String prompt(String handle, String hostname) {
        String who = handle == null || handle.isBlank() ? "operator" : handle.trim();
        return who + "@" + qualified(hostname) + ":~$";
    }

    private static String strip(String hostname) {
        String name = hostname == null ? "" : hostname.trim();
        // Case-insensitively, because a hostname is, and a player who typed `.LOCAL` meant `.local`.
        if (name.toLowerCase(Locale.ROOT).endsWith(SUFFIX)) {
            name = name.substring(0, name.length() - SUFFIX.length());
        }
        return name;
    }
}
