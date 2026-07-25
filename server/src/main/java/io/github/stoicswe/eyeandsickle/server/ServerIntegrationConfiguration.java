package io.github.stoicswe.eyeandsickle.server;

import io.github.stoicswe.eyeandsickle.server.discovery.LocalDescriptorSource;
import io.github.stoicswe.eyeandsickle.server.discovery.PeerKeyResolver;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GatedOfferingCatalog;
import io.github.stoicswe.eyeandsickle.server.items.DidPublicKeyResolver;
import io.github.stoicswe.eyeandsickle.server.items.ServerRecognition;
import io.github.stoicswe.eyeandsickle.server.items.ServerSigningIdentity;
import io.github.stoicswe.eyeandsickle.server.items.ServerSigningKeyLoader;
import io.github.stoicswe.eyeandsickle.server.items.ServerSigningProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The cross-slice wiring the six system slices were each building toward but none could finish.
 *
 * <p>Each slice, built in isolation, abstracted the capabilities it needed from its neighbours behind
 * a narrow interface — a DID-to-key resolver, the server's signing identity, a recognition check —
 * and shipped a safe default factory for each, expecting an integration step to register them. That
 * step is here. Every bean is {@code @ConditionalOnMissingBean}, so when a slice later contributes a
 * real implementation (a live AT Protocol DID resolver, say), it supersedes the default without a
 * change to this file.
 *
 * <h2>What is real and what is a stub, stated plainly</h2>
 *
 * <ul>
 *   <li><strong>Real:</strong> the server's own Ed25519 signing identity, loaded from configuration.
 *       With it the server signs and verifies its <em>own</em> items offline — the whole local
 *       provenance path works.
 *   <li><strong>Stub (safe-failure):</strong> resolution of <em>external</em> DIDs to public keys.
 *       Doing it for real means a network client that resolves {@code did:plc} via a directory and
 *       {@code did:web} over HTTPS ({@code docs/architecture/02-identity-and-auth.md} §5) — a
 *       substantial component the identity slice owns and had not reached. Until then these resolvers
 *       return {@code null}, so a signature from another server is simply <em>unverifiable</em>, which
 *       means the item is not recognized ({@code docs/architecture/04-item-provenance.md} §7). That is
 *       the correct default: refusing to recognize is safe, whereas guessing a key is not. Tracked as
 *       a wiring seam in {@code docs/design/15-open-questions.md}.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class ServerIntegrationConfiguration {

    /**
     * The server's own provenance signing identity, loaded from {@code eyeandsickle.items.signing.*}.
     *
     * <p>If no key is configured the loader returns a "missing" identity that cannot sign — the
     * server can still verify inbound chains but mints nothing, which is the right behaviour for a
     * read-only or not-yet-provisioned node. A configured key gives full local signing. {@code
     * ServerSigningIdentity extends ProvenanceSigner}, so this one bean satisfies both injection
     * points.
     *
     * @param properties the configured signing key material
     * @return the loaded (or deliberately inert) signing identity
     */
    @Bean
    @ConditionalOnMissingBean
    ServerSigningIdentity serverSigningIdentity(ServerSigningProperties properties) {
        return ServerSigningKeyLoader.load(properties);
    }

    /**
     * Recognizes this server's own DID as an authorized issuer, and — until federation-wide
     * recognition is wired — no other. An item this server minted verifies against itself; a foreign
     * item's issuer is not yet recognized, the conservative default.
     *
     * @param properties supplies this server's DID
     * @return a self-only recognition check
     */
    @Bean
    @ConditionalOnMissingBean
    ServerRecognition serverRecognition(ServerSigningProperties properties) {
        return ServerRecognition.selfOnly(properties.did());
    }

    /**
     * Resolves an external DID/kid to a verification key. Stubbed to resolve nothing pending the
     * identity slice's network resolver; see the class note on why {@code null} is the safe default.
     *
     * @return a resolver that resolves nothing
     */
    @Bean
    @ConditionalOnMissingBean
    DidPublicKeyResolver didPublicKeyResolver() {
        return DidPublicKeyResolver.unresolved();
    }

    /**
     * Resolves a peer server's descriptor-signing key. Stubbed alongside {@link
     * #didPublicKeyResolver()} — a peer descriptor whose key cannot be resolved is unverifiable and
     * therefore rejected, which is how discovery stays safe against forged descriptors.
     *
     * @return a resolver that resolves nothing
     */
    @Bean
    @ConditionalOnMissingBean
    PeerKeyResolver peerKeyResolver() {
        return PeerKeyResolver.empty();
    }

    /**
     * This server's own gossip descriptor. Empty until the discovery slice's descriptor builder is
     * wired to the signing identity; an empty source means the server discovers peers but advertises
     * nothing about itself yet.
     *
     * @return a source that advertises no local descriptor
     */
    @Bean
    @ConditionalOnMissingBean
    LocalDescriptorSource localDescriptorSource() {
        return LocalDescriptorSource.none();
    }

    /**
     * The catalogue of gated offerings. Empty by default: offerings are game content
     * ({@code docs/design/02-unlock-gates.md}), not code, and inventing them here would be exactly the
     * kind of undocumented mechanic {@code CLAUDE.md} forbids. A content-backed catalogue supersedes
     * this via {@code @ConditionalOnMissingBean}.
     *
     * @return an empty catalogue
     */
    @Bean
    @ConditionalOnMissingBean
    GatedOfferingCatalog gatedOfferingCatalog() {
        return GatedOfferingCatalog.empty();
    }
}
