package io.github.stoicswe.eyeandsickle.server.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operational bounds for the character directory — every one exists because the input is untrusted.
 *
 * <h2>Why these live in one bound properties class</h2>
 *
 * {@code CLAUDE.md} asks that a slice's tuning values live in one clearly-named properties class in the
 * slice's own package, rather than scattered as constants. None of these are economy numbers ({@code
 * docs/design/03-economy.md}); they are anti-abuse bounds a self-hoster may reasonably tune, kept
 * together so the directory's resource footprint is legible in one place and a hostile-federation test
 * can dial every bound down to a stress value. It mirrors {@code DiscoveryProperties} in the discovery
 * slice, which caps the same class of vectors for peer descriptors.
 *
 * <h2>Every bound here caps one denial-of-service vector</h2>
 *
 * A published home record and a resolution request both arrive from a server this one does not control
 * ({@code docs/architecture/03-server-and-federation.md} §1, {@code 09} §4). A record with no length is
 * a gigabyte of JSON; a directory that grows without limit from gossip is unbounded storage; a
 * resolution that returns everything is an amplification lever. Each field below caps one such vector.
 *
 * @param maxRecordBytes the largest published home record this server will parse, in UTF-8 bytes. A
 *     record has no natural length, so it needs an imposed one.
 * @param maxDirectorySize the largest number of home bindings this server will retain. A hard cap on how
 *     far the directory can grow; a new binding beyond it is refused, existing ones still advance.
 * @param maxHomesPerResolve the most bindings returned from a single resolution. An account holds at most
 *     a handful of characters, so this is a generous safety bound, not an expected limit.
 */
@ConfigurationProperties(prefix = "eyeandsickle.directory")
public record CharacterDirectoryProperties(
        Integer maxRecordBytes, Integer maxDirectorySize, Integer maxHomesPerResolve) {

    /**
     * Default record-size ceiling. A home record is a few hundred bytes of JSON in practice; the cap only
     * has to stop an abusive one, and 64&nbsp;KiB is far below {@code Jsonb.MAX_BYTES} while leaving ample
     * headroom.
     */
    public static final int DEFAULT_MAX_RECORD_BYTES = 64 * 1024;

    /** Default directory ceiling. A home server's federation is allowlist-bounded and small ({@code 06} §3). */
    public static final int DEFAULT_MAX_DIRECTORY_SIZE = 65_536;

    /** Default per-resolution binding cap. */
    public static final int DEFAULT_MAX_HOMES_PER_RESOLVE = 64;

    public CharacterDirectoryProperties {
        // Null-coalesce every field so an operator can set one knob without re-declaring the rest, and so
        // a federating server that sets none of this still gets a coherent config.
        maxRecordBytes = orDefault(maxRecordBytes, DEFAULT_MAX_RECORD_BYTES);
        maxDirectorySize = orDefault(maxDirectorySize, DEFAULT_MAX_DIRECTORY_SIZE);
        maxHomesPerResolve = orDefault(maxHomesPerResolve, DEFAULT_MAX_HOMES_PER_RESOLVE);

        requirePositive("max-record-bytes", maxRecordBytes);
        requirePositive("max-directory-size", maxDirectorySize);
        requirePositive("max-homes-per-resolve", maxHomesPerResolve);
    }

    private static Integer orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("eyeandsickle.directory." + name + " must be positive, was " + value);
        }
    }
}
