package io.github.stoicswe.eyeandsickle.client.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The languages this client actually ships.
 *
 * <h2>⚠ An explicit list, not a directory scan</h2>
 *
 * Scanning the classpath for {@code *_??.properties} works from {@code target/classes} and quietly
 * stops working from inside a jar — the same trap {@code TermDatabase} documents for the manual, and
 * the client ships as a jar three different ways. A scan would also offer a language the moment one
 * file existed for it, so a translator committing a single page would put a half-empty language in
 * front of every player.
 *
 * <p>So a language appears here when somebody decides it is ready. Adding one is an entry plus the
 * files; the README's "Adding a translation" section is the procedure.
 *
 * <h2>⚠ The name is the ENDONYM, and is never translated</h2>
 *
 * The picker reads {@code English · Deutsch · 日本語}, not {@code English · German · Japanese}. A
 * player who has landed in a language they cannot read needs to find their own on the list, and
 * their own is the only one they are certain to recognise — so the list must look the same whatever
 * language the client is currently in. This is the one string in the client that is deliberately
 * <b>identical in every locale</b>.
 *
 * <h2>⚠ The tag is what everything else keys on</h2>
 *
 * {@link #tag} is the suffix on a bundle ({@code commands_de.properties}) and the directory under
 * {@code terms/} ({@code terms/de/}). It is structure, so it is lowercase ASCII and never localised.
 */
public enum Language {

    /** The source language. Everything falls back to this, per key and per manual page. */
    ENGLISH("en", "English");

    private final String tag;
    private final String endonym;

    Language(String tag, String endonym) {
        this.tag = tag;
        this.endonym = endonym;
    }

    /** The bundle suffix and the {@code terms/} directory name — {@code en}, {@code de}, {@code ja}. */
    public String tag() {
        return tag;
    }

    /** What this language calls itself. Shown in the picker, identical in every locale. */
    public String endonym() {
        return endonym;
    }

    /** The language everything falls back to. */
    public static Language fallback() {
        return ENGLISH;
    }

    /** Every shipped language, in declaration order. English first, then by endonym. */
    public static List<Language> shipped() {
        return List.of(values());
    }

    /**
     * The language for a stored tag.
     *
     * <p>⚠ Returns empty rather than throwing for an unknown tag, because this reads a settings file
     * a player can edit and a save that names a language we removed must still open. The caller
     * falls back to English, which is what every missing translation does anyway.
     */
    public static Optional<Language> ofTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        String wanted = tag.trim().toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.tag.equals(wanted)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * The language to start in when the player has never chosen one.
     *
     * <p>⚠ Takes the host's language when we ship it, English otherwise. Defaulting to English
     * outright would mean a player whose OS is in a language we support still has to go and find the
     * setting; guessing beyond an exact tag match would put them in a language they did not ask for
     * on the strength of a region code.
     */
    public static Language hostDefault() {
        return ofTag(Locale.getDefault().getLanguage()).orElse(ENGLISH);
    }
}
