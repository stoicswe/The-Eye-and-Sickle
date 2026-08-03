package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.DidResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.HandleResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.HardenedHttpClient;
import io.github.stoicswe.eyeandsickle.protocol.identity.TxtLookup;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the identity slice's default seam implementations.
 *
 * <p>{@link CharacterProperties} and {@link IdentityProperties} are {@code @ConfigurationProperties}
 * records, registered by the application's {@code @ConfigurationPropertiesScan}, so they need no wiring
 * here. What does is the recognized-character-count seam
 * ({@code docs/architecture/09-player-state-portability.md} §2): the identity slice must ship a working
 * default without assuming the discovery slice's federation directory exists yet.
 */
@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

    /**
     * The default {@link RecognizedCharacterCount}: counts only this server's own active characters
     * (09 §2). Correct and exact for a single, non-federating home server, and the honest floor for a
     * federating one until the discovery slice contributes a directory-backed count. When it does,
     * {@code @ConditionalOnMissingBean} steps this default aside — {@link CharacterService} sees only the
     * one bean either way. A wiring seam, reported in {@code undecidedByDocs}.
     *
     * @param players the character table the local count reads from
     * @return the single-server default count
     */
    @Bean
    @ConditionalOnMissingBean
    RecognizedCharacterCount localRecognizedCharacterCount(PlayerRepository players) {
        return new LocalRecognizedCharacterCount(players);
    }

    /**
     * The default {@link VerifiedHandleDirectory}: verifies nothing, and reports that it cannot
     * ({@code docs/architecture/10-oauth-and-did-resolution.md} §4.1).
     *
     * <p>⚠ Off by default <strong>on purpose</strong>, and not because the resolver is unfinished.
     * Turning it on makes every sign-in do a DNS lookup and up to two HTTPS fetches, and until
     * {@code 10} §7 stage 5 lands there is no real identity provider for it to correct — the shipped
     * provider is {@link DevAtProtoIdentityProvider}, which is itself disabled by default. Enabling
     * handle verification over a provider that trusts a claimed DID would verify the handle of an
     * identity nobody authenticated, which is a check that looks like security and is not.
     *
     * <p>A self-hoster turns it on with {@code eyeandsickle.identity.handle-resolution.enabled=true},
     * and {@code @ConditionalOnMissingBean} steps this default aside.
     *
     * @return the no-op directory
     */
    @Bean
    @ConditionalOnMissingBean
    VerifiedHandleDirectory unresolvedHandleDirectory() {
        return VerifiedHandleDirectory.unresolved();
    }

    /**
     * Everything that reaches the network to answer an identity question, wired as one unit.
     *
     * <h2>⚠ Why one switch and not two</h2>
     *
     * Handle verification and DID→key resolution look like separate features, and operationally they
     * are one: "may this server make outbound identity lookups?" Splitting them would give an
     * operator two ways to half-configure a server, and they share the same
     * {@link HardenedHttpClient} and the same {@link DidResolver} cache anyway.
     *
     * <h2>⚠ Why ONE {@link DidResolver} bean rather than one per consumer</h2>
     *
     * The TTL cache is the point. Two resolvers means two caches, which means signing in and then
     * verifying an item from the same DID hits {@code plc.directory} twice — and doubles this
     * server's contribution to the load on a directory that is a single point of failure for sign-in
     * across the whole federation.
     *
     * <h2>⚠ Why the client is constructed here, not injected</h2>
     *
     * The SSRF rules in {@code 10} §4.3 are properties of {@link HardenedHttpClient} specifically. A
     * bean somebody could substitute is a bean somebody will eventually substitute with a plain
     * {@code HttpClient} to get through a corporate proxy, and the guard would leave with it.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "eyeandsickle.identity.resolution", name = "enabled", havingValue = "true")
    static class IdentityResolutionConfiguration {

        @Bean
        DidResolver didResolver() {
            return new DidResolver(
                    new HardenedHttpClient(),
                    DidResolver.DEFAULT_PLC_DIRECTORY,
                    DidResolver.DEFAULT_TTL,
                    DidResolver.DEFAULT_MAX_ENTRIES,
                    Instant::now);
        }

        @Bean
        VerifiedHandleDirectory atprotoHandleDirectory(DidResolver dids) {
            // ⚠ Said out loud, once, at startup. Without java.naming there is no DNS, and handle
            // resolution silently falls back to the HTTPS method alone — which resolves a SMALLER set
            // of handles, and DNS is the method the spec says wins on conflict. A shorter list of
            // resolvable handles looks exactly like a shorter list of resolvable handles.
            if (!TxtLookup.systemAvailable()) {
                LoggerFactory.getLogger(IdentityResolutionConfiguration.class)
                        .warn("No DNS provider on this runtime (java.naming is absent): atproto handles will be "
                                + "resolved over HTTPS only. Handles published solely via DNS TXT will not resolve. "
                                + "See protocol identity TxtLookup.");
            }
            return new AtprotoHandleDirectory(new HandleResolver(new HardenedHttpClient(), dids, TxtLookup.system()));
        }

        /**
         * Registers BouncyCastle, once, and reports whether it achieved anything.
         *
         * <h2>⚠ Registration alone does NOT make secp256k1 work</h2>
         *
         * Measured 2026-08-02 on OpenJDK 26 with BC registered: {@code Signature.getInstance(
         * "SHA256withECDSA")} is still answered by <strong>SunEC</strong>, which accepts a secp256k1
         * key at {@code initVerify} and refuses only at {@code verify()} — so the JCA never falls
         * through to BC. The provider has to be named explicitly, which
         * {@code MultibaseKey} does by probing each registered provider with a real sign-and-verify
         * round trip and remembering which one worked.
         *
         * <p>So this bean does two things: adds the provider, then <strong>checks the outcome</strong>.
         * A registration that silently failed to help would surface as "signature does not verify" on
         * a player's first sign-in, which names the wrong problem entirely.
         */
        @Bean
        SecpProviderStatus bouncyCastleProvider() {
            if (java.security.Security.getProvider("BC") == null) {
                java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            }
            boolean usable = io.github.stoicswe.eyeandsickle.protocol.identity.MultibaseKey.secp256k1Available();
            org.slf4j.Logger log = LoggerFactory.getLogger(IdentityResolutionConfiguration.class);
            if (usable) {
                log.info(
                        "secp256k1 verification is available; service-auth tokens from did:plc accounts can be checked");
            } else {
                // ⚠ Not a warning to skim past: MOST did:plc accounts sign with secp256k1, so without
                // it this server can verify almost nobody. Better said loudly at boot than discovered
                // per-player.
                log.error("secp256k1 verification is NOT available even with BouncyCastle on the classpath. "
                        + "Most AT Protocol accounts sign with this curve, so service-auth sign-in will fail "
                        + "for them. See docs/architecture/10-oauth-and-did-resolution.md section 5.1.");
            }
            return new SecpProviderStatus(usable);
        }

        /** Whether secp256k1 can be verified here — a bean so startup ordering is explicit. */
        public record SecpProviderStatus(boolean secp256k1Available) {}

        @Bean
        ServiceAuthReplayGuard serviceAuthReplayGuard() {
            return new ServiceAuthReplayGuard(Instant::now);
        }

        /**
         * The production identity provider (<b>W-6</b>).
         *
         * <p>⚠ Depends on {@code SecpProviderStatus} purely for <strong>ordering</strong>: the
         * provider must be registered before {@code MultibaseKey} probes for a recipe, and the probe
         * result is cached for the life of the process. Registering BC afterwards would leave the
         * cached answer saying secp256k1 is unavailable for as long as the server runs.
         */
        @Bean
        @ConditionalOnMissingBean(AtProtoIdentityProvider.class)
        AtProtoIdentityProvider serviceAuthIdentityProvider(
                DidResolver dids,
                ServiceAuthReplayGuard replays,
                io.github.stoicswe.eyeandsickle.server.items.ServerSigningProperties signing,
                SecpProviderStatus providerOrder) {
            return new ServiceAuthIdentityProvider(new ServiceAuthVerifier(dids, signing.did(), replays, Instant::now));
        }

        /**
         * <b>W-1</b>'s server half. Overrides the {@code unresolved()} default in
         * {@code ServerIntegrationConfiguration}, which carries the matching negative condition on
         * this same property so the choice is deterministic rather than dependent on which
         * {@code @Configuration} Spring happens to parse first.
         */
        @Bean
        io.github.stoicswe.eyeandsickle.server.items.DidPublicKeyResolver atprotoDidPublicKeyResolver(
                DidResolver dids) {
            return new AtprotoDidPublicKeyResolver(dids);
        }
    }
}
