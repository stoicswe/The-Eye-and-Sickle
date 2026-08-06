package io.github.stoicswe.eyeandsickle.client.credentials;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Picks the credential store this machine has, or reports that it has none.
 *
 * <h2>⚠ "NONE" IS A SUPPORTED ANSWER AND MUST NEVER BECOME A FILE</h2>
 *
 * A machine with no agent — a bare Linux box, a locked-down image, a Windows build without the WinRT
 * assembly — gets {@link #none()}, and whatever wanted a credential is simply off. It does not fall
 * back to the profile directory. A credential in {@code settings.json} is a credential in every
 * backup, screen share and bug report, and the player would have no way to know it had happened.
 */
public final class SecretStores {

    private SecretStores() {}

    private static final Logger LOG = Logger.getLogger(SecretStores.class.getName());

    /** Cached, because probing Windows runs a PowerShell process and Linux runs {@code secret-tool}. */
    private static volatile SecretStore resolved;

    /**
     * The store for this machine.
     *
     * <p>⚠ Each candidate's {@code available()} answers for its own platform, so the order here is
     * not load-bearing — but it is written most-specific-first anyway, because a list whose
     * correctness depends on nobody reordering it is a list somebody will reorder.
     */
    public static SecretStore forThisMachine() {
        SecretStore cached = resolved;
        if (cached != null) {
            return cached;
        }
        synchronized (SecretStores.class) {
            if (resolved == null) {
                resolved = probe();
            }
            return resolved;
        }
    }

    private static SecretStore probe() {
        for (SecretStore candidate : List.of(new MacKeychain(), new WindowsCredentialVault(), new SecretServiceStore())) {
            try {
                if (candidate.available()) {
                    LOG.log(Level.INFO, "credential store: {0}", candidate.describe());
                    return candidate;
                }
            } catch (RuntimeException broken) {
                // ⚠ A probe must never take the client down. Spawning a process can fail in ways
                // that are specific to one machine's security policy, and the correct outcome is
                // "this store is not available here" rather than a crash on startup.
                LOG.log(Level.FINE, "credential store probe failed: " + candidate.describe(), broken);
            }
        }
        LOG.info("no OS credential store on this machine; features needing one are unavailable");
        return none();
    }

    /**
     * A store that holds nothing and says so.
     *
     * <p>⚠ {@link SecretStore#store} returns <b>false</b> rather than pretending. A caller that
     * treated a silent success as "kept" would tell the player their credential was saved and lose
     * it on exit.
     */
    public static SecretStore none() {
        return new SecretStore() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public boolean store(String account, String secret) {
                return false;
            }

            @Override
            public Optional<String> lookup(String account) {
                return Optional.empty();
            }

            @Override
            public boolean forget(String account) {
                return false;
            }

            @Override
            public String describe() {
                return "no credential store";
            }
        };
    }

    /** Test seam — {@code SecretStores} is a process-wide cache and a test must be able to clear it. */
    static void forget() {
        resolved = null;
    }
}
