package io.github.stoicswe.eyeandsickle.server.identity;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Identity, session and operator-authentication configuration, bound from {@code eyeandsickle.identity}.
 *
 * <h2>Everything here is operational or explicitly undecided — never an invented game rule</h2>
 *
 * The operator credentials, the development sign-in switch and the session lifetime are
 * <em>operational</em> knobs: they decide how the server is administered, not how the game is balanced,
 * so they carry safe defaults and a self-hoster tunes them freely. The one balance-adjacent value,
 * {@link #factionAbandonmentHeatSpike()}, deliberately has <strong>no default</strong> — see its
 * accessor. This is the single configuration-properties class the identity slice adds, per
 * {@code CLAUDE.md}: a calibrated number lives in one bound, tunable place, never scattered across
 * constants.
 *
 * @param operator credentials for the operator endpoints; unset means those endpoints admit no one
 * @param devSignin the development sign-in switch; off by default
 * @param sessionTtl how long a sign-in session stays valid
 * @param factionAbandonmentHeatSpike the heat added when a player abandons their faction — a balance
 *     value this slice must not invent (see the accessor)
 */
@ConfigurationProperties(prefix = "eyeandsickle.identity")
public record IdentityProperties(
        @DefaultValue Operator operator,
        @DefaultValue DevSignin devSignin,
        @DefaultValue("PT24H") Duration sessionTtl,
        BigDecimal factionAbandonmentHeatSpike) {

    public IdentityProperties {
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "eyeandsickle.identity.session-ttl must be a positive duration, was " + sessionTtl);
        }
        if (factionAbandonmentHeatSpike != null && factionAbandonmentHeatSpike.signum() < 0) {
            throw new IllegalArgumentException(
                    "eyeandsickle.identity.faction-abandonment-heat-spike is a heat magnitude and cannot be negative, was "
                            + factionAbandonmentHeatSpike);
        }
    }

    /**
     * The heat spike applied when a player abandons a side ({@code docs/design/01-core-resources.md}
     * §5), or {@code null} if the operator has not configured one.
     *
     * <p><strong>[PROPOSAL] — the magnitude is undecided and this slice must not invent it.</strong>
     * The design says abandoning a faction "spikes heat temporarily" but never fixes by how much, and a
     * heat magnitude is a balance value calibrated as a set with the rest of the economy
     * ({@code docs/design/03-economy.md}). So there is no default: an operator who wants the transition
     * to apply a spike must supply the number, and {@link FactionService} refuses to fabricate one when
     * it is absent. Logged in {@code docs/design/15-open-questions.md} for a design ruling.
     *
     * @return the configured spike, or {@code null} if unset
     */
    @Override
    public BigDecimal factionAbandonmentHeatSpike() {
        return factionAbandonmentHeatSpike;
    }

    /**
     * Credentials for the operator endpoints (allowlist management).
     *
     * <p>Operator access is separate from player access on purpose: allowlist management is
     * administration, not play, so it is gated by an operator credential (HTTP Basic) rather than a
     * player session. When {@code username} or {@code password} is unset, no operator user is
     * registered and the operator endpoints admit no one — locked by default, the same posture as the
     * allowlist itself.
     *
     * <p>{@code did} is the identity revocations are attributed to. The schema requires a revocation to
     * name an actor ({@code ck_allowlist_entries_revoked_pair}), and the operator authenticates by
     * password rather than by DID, so this supplies the DID that a soft-revoke records. It must be
     * configured for revocation to be possible.
     *
     * @param username the operator login, or {@code null} to leave operator access disabled
     * @param password the operator secret (supply via environment, never committed), or {@code null}
     * @param did the DID operator actions are attributed to, or {@code null}
     */
    public record Operator(String username, String password, String did) {

        /**
         * @return whether a usable operator credential is configured
         */
        public boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }

        /**
         * @return the operator's attribution DID, validated, or {@code null} if unset
         * @throws IllegalArgumentException if a configured value is not a well-shaped DID
         */
        public Did parsedDid() {
            return Did.ofNullable(did == null || did.isBlank() ? null : did);
        }
    }

    /**
     * The development sign-in switch.
     *
     * <p>When enabled, {@link DevAtProtoIdentityProvider} accepts a DID the client simply claims,
     * bypassing the real OAuth handshake. That is an impersonation shortcut for local development and
     * testing only, which is why it is <strong>off by default</strong> and must stay off on any server
     * that faces real players.
     *
     * @param enabled whether development sign-in is permitted; defaults to {@code false}
     */
    public record DevSignin(@DefaultValue("false") boolean enabled) {}
}
