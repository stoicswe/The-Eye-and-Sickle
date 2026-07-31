package io.github.stoicswe.eyeandsickle.client.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Text the player reads, looked up by key, with English underneath.
 *
 * <h2>⚠ What is translatable here, and what must never be</h2>
 *
 * This is the line the whole design turns on, and getting it wrong would damage the thing the client
 * exists for. <b>Command names, flag names and page names are code, not text.</b> {@code ls} is
 * {@code ls} in every locale; {@code --verbose} is {@code --verbose}; {@code grep(1)} is
 * {@code grep(1)}. Three reasons, in increasing order of importance:
 *
 * <ol>
 *   <li>The parser is a closed AST over an enumerated registry. A localised verb is a verb the
 *       parser does not have.
 *   <li>Real Unix does not localise them either, so translating them would be inventing a behaviour
 *       and presenting it as the real one.
 *   <li>Pillar <b>C6</b> and the whole of {@code docs/education/} sell <em>transferable</em> skill —
 *       a player who learns {@code grep -v} here can use it tonight. A localised flag takes that
 *       away from precisely the players a translation is for, which makes it worse than no
 *       translation at all.
 * </ol>
 *
 * <p>So: <b>structure is code, prose is text.</b> A command declares which flags it has and what
 * shape they are; this supplies the sentence describing them.
 *
 * <h2>⚠ Fallback is per KEY, not per file</h2>
 *
 * A partial translation is the normal state of a translation — someone renders forty strings and the
 * next forty arrive a month later. Falling back per file would mean a bundle with one missing key
 * drops the player into English wholesale; falling back per key means they get their language for
 * everything that has been done and English for the rest, which is what every real localised program
 * does and the only behaviour that lets a translation ship incrementally.
 *
 * <p>⚠ A key with no entry <b>anywhere</b> returns the key itself rather than blank or null. A blank
 * label is invisible and a null is a crash; a key on screen is ugly, obviously wrong, and tells
 * whoever sees it exactly which entry to add.
 */
public final class Messages {

    /** The language every bundle falls back to. English is the source language, not a preference. */
    public static final String FALLBACK = "en";

    private static final String ROOT = "/io/github/stoicswe/eyeandsickle/client/i18n/";

    private final String language;
    private final Map<String, String> strings = new LinkedHashMap<>();
    private final List<String> problems = new java.util.ArrayList<>();

    private Messages(String language) {
        this.language = language;
    }

    /**
     * Loads a bundle for {@code language}, with English underneath it.
     *
     * <p>⚠ English is loaded FIRST and the requested language written over it, so a key the
     * translation has not reached keeps its English text rather than disappearing. Loading them the
     * other way round would silently prefer the fallback for every key both files contain.
     */
    public static Messages load(String bundle, String language) {
        String wanted = language == null || language.isBlank()
                ? FALLBACK
                : language.trim().toLowerCase(Locale.ROOT);
        Messages messages = new Messages(wanted);
        messages.read(bundle, FALLBACK, true);
        if (!wanted.equals(FALLBACK)) {
            messages.read(bundle, wanted, false);
        }
        return messages;
    }

    /** The shipped English bundle. */
    public static Messages load(String bundle) {
        return load(bundle, FALLBACK);
    }

    private void read(String bundle, String language, boolean required) {
        String path = ROOT + bundle + "_" + language + ".properties";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in == null) {
                if (required) {
                    // The English bundle missing is a packaging fault, not a missing translation.
                    problems.add("no bundle at " + path);
                }
                return;
            }
            Properties properties = new Properties();
            // ⚠ Read as UTF-8 explicitly. Properties.load(InputStream) is ISO-8859-1 by definition,
            // which mangles every accented character in exactly the files a translation puts them in.
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (value != null && !value.isBlank()) {
                    // ⚠ Blank values do NOT overwrite English. A translator leaving a key empty means
                    // "not done yet", and taking it literally would blank the label.
                    strings.put(key, value);
                }
            }
        } catch (IOException e) {
            problems.add("could not read " + path + ": " + e.getMessage());
        }
    }

    /** The text for {@code key}, or the key itself when nothing anywhere defines it. */
    public String get(String key) {
        if (key == null) {
            return "";
        }
        return strings.getOrDefault(key, key);
    }

    /** The text for {@code key}, or {@code fallback} — for a caller that has a sensible default. */
    public String get(String key, String fallback) {
        return strings.getOrDefault(key, fallback);
    }

    /** Whether this bundle actually defines the key, as opposed to echoing it back. */
    public boolean has(String key) {
        return key != null && strings.containsKey(key);
    }

    /** The language asked for. Not necessarily the language every string came back in. */
    public String language() {
        return language;
    }

    /** Anything wrong with the bundles, for the diagnostics surface rather than for a crash. */
    public List<String> problems() {
        return List.copyOf(problems);
    }
}
