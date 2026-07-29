package io.github.stoicswe.eyeandsickle.client.events;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One event, in the <b>CloudEvents v1.0.2</b> envelope.
 *
 * <h2>Why a specification instead of a record of our own</h2>
 *
 * An event bus grows an ad-hoc envelope by default — whatever the first three publishers happened to
 * need — and by the time it matters that every event has a stable identity, a source and a timestamp,
 * there are forty publishers and no way to add them. CloudEvents has already made those decisions,
 * they are the right ones, and the format is understood by things outside this codebase. Conforming
 * costs one class; not conforming costs the ability to ever export the stream.
 *
 * <h2>The four REQUIRED attributes, and what each is for</h2>
 *
 * <ul>
 *   <li>{@link #id} — unique <em>per source</em>. The spec's own rule: producers MUST ensure
 *       {@code source + id} is unique for each distinct event. It is what makes a duplicate
 *       recognisable as a redelivery rather than as a second thing happening.
 *   <li>{@link #source} — a URI-reference naming <em>what produced this</em>. Not what it is about;
 *       {@link #subject} is that.
 *   <li>{@link #specversion} — {@code "1.0"}, fixed. Present so a reader never has to guess which
 *       revision of the rules an event was written under.
 *   <li>{@link #type} — reverse-DNS, describing what happened. The one attribute a subscriber
 *       usually filters on.
 * </ul>
 *
 * <h2>⚠ The validation here is the spec's, and it is enforced rather than documented</h2>
 *
 * Every rule below cites the section it comes from. They are checked in the compact constructor
 * because an envelope that is only conformant when everybody remembers the rules is not a conformant
 * envelope — and the one thing worse than no standard is a stream that claims to follow one.
 *
 * @param id §3.1.1 — REQUIRED, non-empty. Unique together with {@link #source}
 * @param source §3.1.1 — REQUIRED, non-empty URI-reference
 * @param specversion §3.1.1 — REQUIRED. Always {@link #SPEC_VERSION}
 * @param type §3.1.1 — REQUIRED, non-empty. SHOULD be prefixed with a reverse-DNS name
 * @param datacontenttype §3.1.2 — OPTIONAL, RFC 2046 media type
 * @param dataschema §3.1.2 — OPTIONAL, URI
 * @param subject §3.1.2 — OPTIONAL. <b>Non-empty if present</b>: what the event is <em>about</em>,
 *     within the source. A machine's address, a block height, a window id
 * @param time §3.1.2 — OPTIONAL, RFC 3339. {@link Instant} is exactly that
 * @param extensions §4 — extension context attributes. Names obey the same rules as the core ones
 * @param data §3 — the payload. Kept as a map so the log can render it without a serialiser
 */
public record CloudEvent(
        String id,
        URI source,
        String specversion,
        String type,
        String datacontenttype,
        URI dataschema,
        String subject,
        Instant time,
        Map<String, String> extensions,
        Map<String, String> data) {

    /** The version of the specification this envelope is written to. §3.1.1. */
    public static final String SPEC_VERSION = "1.0";

    /**
     * The reverse-DNS prefix every {@link #type} in this game carries.
     *
     * <p>§3.1.1 says a type SHOULD be prefixed with a reverse-DNS name owned by the producer. One
     * constant rather than a literal per publisher, so a subscriber can filter on the prefix and a
     * typo cannot invent a second namespace.
     */
    public static final String NAMESPACE = "io.github.stoicswe.eyeandsickle";

    /**
     * ⚠ §4.1 — extension attribute names MUST consist of lowercase {@code a–z} and {@code 0–9} only.
     *
     * <p>Not a style preference. The spec allows a transport to carry attributes as HTTP headers or
     * as AMQP application properties, and those have their own casing and character rules — the
     * lowercase-alphanumeric intersection is what survives all of them unchanged.
     */
    private static final java.util.regex.Pattern ATTRIBUTE_NAME =
            java.util.regex.Pattern.compile("[a-z0-9]+");

    /** §4.1 — SHOULD NOT exceed 20 characters. Enforced, because a "should" nobody checks is a no-op. */
    private static final int MAX_ATTRIBUTE_NAME = 20;

    /**
     * ⚠ §4.1 — an extension may not shadow a core attribute name.
     *
     * <p>An extension called {@code type} would be silently unreachable on any transport that maps
     * attributes into a flat namespace, which is most of them, and the reader would see the core
     * attribute while the producer believed they had sent theirs.
     */
    private static final Set<String> RESERVED = Set.of(
            "id", "source", "specversion", "type",
            "datacontenttype", "dataschema", "subject", "time", "data");

    public CloudEvent {
        require(id, "id");
        require(type, "type");
        require(specversion, "specversion");
        if (source == null || source.toString().isBlank()) {
            // §3.1.1: REQUIRED, non-empty URI-reference. A relative reference is legal and is what
            // this client uses — /client/netmap says where in the app without inventing a hostname.
            throw new IllegalArgumentException("CloudEvents §3.1.1: source is required and non-empty");
        }
        if (!SPEC_VERSION.equals(specversion)) {
            throw new IllegalArgumentException(
                    "CloudEvents §3.1.1: specversion must be " + SPEC_VERSION + ", was " + specversion);
        }
        if (subject != null && subject.isBlank()) {
            // §3.1.2: an OPTIONAL attribute, if present, MUST NOT be empty. Absent and empty are
            // different statements, and a blank subject claims there is one while naming nothing.
            throw new IllegalArgumentException("CloudEvents §3.1.2: subject must be absent or non-empty");
        }
        if (datacontenttype != null && datacontenttype.isBlank()) {
            throw new IllegalArgumentException(
                    "CloudEvents §3.1.2: datacontenttype must be absent or non-empty");
        }
        extensions = validated(extensions);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /** Checks the extension names against §4.1 and returns an unmodifiable copy. */
    private static Map<String, String> validated(Map<String, String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (var entry : extensions.entrySet()) {
            String name = entry.getKey();
            if (name == null || !ATTRIBUTE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(
                        "CloudEvents §4.1: extension names are lowercase a-z and 0-9 only, was " + name);
            }
            if (name.length() > MAX_ATTRIBUTE_NAME) {
                throw new IllegalArgumentException(
                        "CloudEvents §4.1: extension names should not exceed " + MAX_ATTRIBUTE_NAME
                                + " characters, was " + name);
            }
            if (RESERVED.contains(name)) {
                throw new IllegalArgumentException(
                        "CloudEvents §4.1: extension may not shadow the core attribute " + name);
            }
            copy.put(name, entry.getValue() == null ? "" : entry.getValue());
        }
        return Map.copyOf(copy);
    }

    private static void require(String value, String attribute) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "CloudEvents §3.1.1: " + attribute + " is required and non-empty");
        }
    }

    // ── building one ───────────────────────────────────────────────────────────────────────────

    /**
     * The ordinary constructor: a type, where it came from, what it is about, and its payload.
     *
     * <p>⚠ {@link #id} is a random UUID and {@link #time} is the wall clock, both filled here rather
     * than by the caller. Every publisher getting those right is forty chances to get them wrong, and
     * the spec's uniqueness rule is the one a hand-written id breaks first.
     *
     * <p>⚠ The time is deliberately {@code Instant.now()} and NOT the session clock, which is the
     * opposite of the rule everywhere else in this codebase. An event log is a record of when
     * something was <em>observed by the process</em>, for debugging — a stream stamped with a test
     * clock would put a developer's afternoon in 2026 while the exception they are chasing happened
     * at 14:02. Game deadlines still come from the session clock; nothing here is a deadline.
     */
    public static CloudEvent of(String type, String source, String subject, Map<String, String> data) {
        return new CloudEvent(
                UUID.randomUUID().toString(),
                URI.create(source),
                SPEC_VERSION,
                type,
                data == null || data.isEmpty() ? null : "application/json",
                null,
                subject,
                Instant.now(),
                Map.of(),
                data == null ? Map.of() : data);
    }

    /** The same, with no payload. */
    public static CloudEvent of(String type, String source, String subject) {
        return of(type, source, subject, Map.of());
    }

    /**
     * A copy carrying one more extension attribute.
     *
     * <p>⚠ The name is <b>rejected, not lowercased</b>, when it breaks §4.1. Coercing it looks kinder
     * and is worse: a publisher writing {@code retryCount} would silently get {@code retrycount}, so
     * the key they read back is not the key they wrote and the mismatch surfaces at the far end of the
     * stream instead of at the call that caused it.
     */
    public CloudEvent with(String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(extensions);
        next.put(name, value);
        return new CloudEvent(id, source, specversion, type, datacontenttype, dataschema,
                subject, time, next, data);
    }

    // ── reading one ────────────────────────────────────────────────────────────────────────────

    /** The type without its reverse-DNS prefix — what a log column shows. */
    public String shortType() {
        return type.startsWith(NAMESPACE + ".") ? type.substring(NAMESPACE.length() + 1) : type;
    }

    /** Whether this event's type sits under {@code prefix}, for a subscriber that filters. */
    public boolean isA(String prefix) {
        return prefix != null && (type.equals(prefix) || type.startsWith(prefix + "."));
    }

    /** The payload as {@code key=value} pairs, in declaration order. Empty when there is none. */
    public String payload() {
        if (data.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (var entry : data.entrySet()) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }
}
