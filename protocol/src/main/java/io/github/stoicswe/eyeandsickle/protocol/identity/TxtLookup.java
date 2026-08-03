package io.github.stoicswe.eyeandsickle.protocol.identity;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

/**
 * Looks up DNS {@code TXT} records.
 *
 * <h2>Why this is an interface</h2>
 *
 * Handle resolution has two methods and the DNS one <strong>wins on conflict</strong>
 * (<a href="https://atproto.com/specs/handle">handle spec</a>), so it cannot be skipped — most real
 * Bluesky handles resolve this way. But a resolver that can only be exercised by owning a domain is a
 * resolver nobody tests, and the interesting cases here (several TXT records, one of them a
 * {@code did=}, junk in the others) are exactly the ones a live lookup will not reliably reproduce.
 *
 * <p>⚠ The JDK has no DNS API outside JNDI, so {@link #system()} needs the {@code java.naming}
 * module. That matters for the jpackage/jlink image — if {@code java.naming} is not linked in, this
 * fails at run time with a {@code NoClassDefFoundError} and handle resolution silently degrades to
 * the HTTPS method alone, which resolves a different (smaller) set of handles. Keep it in the module
 * list.
 */
@FunctionalInterface
public interface TxtLookup {

    /**
     * @param name the fully-qualified name to query
     * @return every TXT value at that name, verbatim and unfiltered; empty if the name does not exist
     */
    List<String> txt(String name);

    /** A lookup that always answers nothing — for tests, and for a build with no {@code java.naming}. */
    static TxtLookup none() {
        return name -> List.of();
    }

    /**
     * Whether the JNDI DNS provider can actually be constructed on this runtime.
     *
     * <h2>⚠ Why this is separate from {@link #txt}</h2>
     *
     * {@code txt()} answers {@code List.of()} for both "this name has no TXT records" and "there is
     * no DNS subsystem here", and it has to: the first is a normal answer that must fall through to
     * the HTTPS method, and failing the whole resolution over the second would lock out every handle
     * that resolves over HTTPS perfectly well.
     *
     * <p>But that means a runtime without {@code java.naming} <strong>silently</strong> resolves a
     * smaller set of handles than one with it, and DNS is the method the spec says wins. This method
     * exists so the layer that owns a logger can say so at startup, once, instead of the difference
     * being invisible.
     *
     * <p>⚠ Measured 2026-08-02: jpackage's non-modular mode links 51 JDK modules and
     * {@code java.naming} <em>is</em> among them, so the shipped image is fine today. This guards the
     * day somebody adds {@code --add-modules} to trim the ~135 MB image — which
     * {@code CLAUDE.md} already names as wanted.
     *
     * @return true if DNS lookups are possible
     */
    static boolean systemAvailable() {
        try {
            Class.forName("com.sun.jndi.dns.DnsContextFactory");
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /** The real thing, over JNDI's DNS provider. */
    static TxtLookup system() {
        return name -> {
            Hashtable<String, String> environment = new Hashtable<>();
            environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            // Without these, a dead resolver hangs sign-in. JNDI's defaults are minutes long.
            environment.put("com.sun.jndi.dns.timeout.initial", "3000");
            environment.put("com.sun.jndi.dns.timeout.retries", "2");
            InitialDirContext context = null;
            try {
                context = new InitialDirContext(environment);
                Attributes attributes = context.getAttributes("dns:/" + name, new String[] {"TXT"});
                Attribute txt = attributes.get("TXT");
                if (txt == null) {
                    return List.of();
                }
                List<String> values = new ArrayList<>();
                for (int i = 0; i < txt.size(); i++) {
                    Object value = txt.get(i);
                    if (value != null) {
                        // JNDI hands back long TXT values quoted and split into chunks. Strip the
                        // quotes and rejoin, or a did= that happened to exceed 255 bytes reads as a
                        // record that is not a DID at all.
                        values.add(value.toString().replace("\" \"", "").replace("\"", ""));
                    }
                }
                return List.copyOf(values);
            } catch (NameNotFoundException absent) {
                // NXDOMAIN is a legitimate answer meaning "no DNS method here", not a failure. The
                // caller falls through to the HTTPS method.
                return List.of();
            } catch (NamingException | RuntimeException failure) {
                // Includes NoClassDefFoundError's runtime cousins and a missing DNS provider. A
                // broken resolver must not be reported as a verified absence, but it also must not
                // take down sign-in for handles that resolve over HTTPS — so: empty, and the caller
                // decides.
                return List.of();
            } finally {
                if (context != null) {
                    try {
                        context.close();
                    } catch (NamingException ignored) {
                        // Closing a directory context that already failed is not itself a failure.
                    }
                }
            }
        };
    }
}
