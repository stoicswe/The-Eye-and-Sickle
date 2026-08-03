package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Refuses a service-auth token that has already been used.
 *
 * <h2>Why the other checks are not enough</h2>
 *
 * A verified signature, a correct audience and an unexpired {@code exp} all still permit the same
 * token to be presented twice — so anyone who can observe one in flight can sign in as its owner
 * until it expires. The {@code jti} exists for exactly this, and the spec says receiving services
 * should track it.
 *
 * <h2>⚠ In memory, and honest about what that means</h2>
 *
 * This is a bounded in-memory set, not a table. Two consequences, both acceptable for what a
 * service-auth token is and neither of which should be discovered later:
 *
 * <ul>
 *   <li><strong>It does not survive a restart.</strong> A token in flight across a restart could be
 *       replayed once. The window is the token's remaining lifetime — at most
 *       {@link ServiceAuthVerifier#MAX_LIFETIME} — and a restart is not attacker-triggerable.
 *   <li><strong>It is per-process.</strong> A horizontally-scaled deployment would need a shared
 *       store, and until one exists a token could be replayed once per instance. ⚠ Worth revisiting
 *       the day this server runs behind a load balancer; today it is one process per home server.
 * </ul>
 *
 * <p>A database table would fix both and costs a write on the sign-in path. It is the right change
 * when either condition above stops holding, and the wrong one before that.
 */
public class ServiceAuthReplayGuard {

    /**
     * ⚠ A bound, because the key is attacker-supplied. Entries expire on their own, but a flood of
     * forged tokens must not be able to grow this without limit while they do.
     */
    static final int MAX_ENTRIES = 100_000;

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public ServiceAuthReplayGuard(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /**
     * Claims a token id, if it has not been claimed already.
     *
     * @param id the issuer-scoped {@code jti}
     * @param expiresAt when the token stops being valid, and so when its id can be forgotten
     * @return true if this is the first use
     */
    public boolean claim(String id, Instant expiresAt) {
        Instant now = clock.get();
        if (seen.size() >= MAX_ENTRIES) {
            // Sweep the expired before resorting to anything blunter. Under normal load this keeps
            // the map small on its own, because every entry is short-lived by construction.
            seen.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        }
        if (seen.size() >= MAX_ENTRIES) {
            // ⚠ Still full after a sweep means live, unexpired ids are filling it — so this is a
            // flood. Refusing is the safe direction: it fails sign-in rather than silently disabling
            // replay protection, which is what clearing the map would do.
            throw new SignInUnavailableException("too many sign-ins in flight; try again shortly");
        }
        // putIfAbsent is the atomic part: two concurrent presentations of one token must not both win.
        return seen.putIfAbsent(id, expiresAt) == null;
    }
}
