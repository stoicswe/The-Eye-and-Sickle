package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.DidDocument;
import io.github.stoicswe.eyeandsickle.protocol.identity.DidResolver;
import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException;
import io.github.stoicswe.eyeandsickle.protocol.identity.MultibaseKey;
import io.github.stoicswe.eyeandsickle.server.items.DidPublicKeyResolver;
import java.security.PublicKey;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real {@link DidPublicKeyResolver} — <b>W-1</b>'s server half.
 *
 * <p>Turns a provenance {@code kid} ({@code did:plc:xxxx#key1}) into the public key it names, by
 * resolving the DID document and decoding the named verification method's
 * {@code publicKeyMultibase}.
 *
 * <h2>⚠ It resolves ANY named method, not just {@code #atproto}</h2>
 *
 * {@link DidDocument#atprotoSigningKey()} deliberately looks only for the {@code #atproto} fragment,
 * because a service-auth JWT must be checked against <em>that</em> key and no other — otherwise a
 * document carrying two keys lets its owner nominate whichever one they have a signature for.
 *
 * <p>Provenance is a different question. A {@code kid} there is a full fragment chosen by the
 * <em>signer</em> and recorded in the signed record, so the record itself says which key it wants;
 * the answer is "the method with this id, or nothing". Refusing anything but {@code #atproto} here
 * would make every provenance record signed with a rotation or per-purpose key unverifiable.
 *
 * <h2>⚠ Returning null is the contract, and it is the safe direction</h2>
 *
 * An unresolvable {@code kid} means the chain is <em>not recognized</em>, which is the conservative
 * outcome and not a security hole ({@code protocol.provenance.SigningKeyDirectory}). So every failure
 * here — no such DID, no such method, a curve this JVM cannot verify with, a network outage —
 * collapses to {@code null}. ⚠ The cost of that is real and worth stating: a federation-wide DID
 * directory outage is indistinguishable, from the outside, from a wave of unrecognized items.
 */
public class AtprotoDidPublicKeyResolver implements DidPublicKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(AtprotoDidPublicKeyResolver.class);

    private final DidResolver dids;

    public AtprotoDidPublicKeyResolver(DidResolver dids) {
        this.dids = Objects.requireNonNull(dids, "dids");
    }

    @Override
    public PublicKey resolve(String kid) {
        if (kid == null || kid.isBlank()) {
            return null;
        }
        int fragment = kid.indexOf('#');
        if (fragment < 0) {
            // A kid without a fragment names a DID, not a key. Resolving it to "the first method"
            // would silently pick a key the signer never nominated.
            log.debug("kid '{}' names no key fragment", kid);
            return null;
        }
        String did = kid.substring(0, fragment);
        try {
            DidDocument document = dids.resolve(did);
            String multibase = document.verificationMethods().stream()
                    .filter(method -> kid.equals(method.id()))
                    .map(DidDocument.VerificationMethod::publicKeyMultibase)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (multibase == null) {
                log.debug("DID document for {} declares no verification method '{}'", did, kid);
                return null;
            }
            return MultibaseKey.decode(multibase).key();
        } catch (IdentityResolutionException unresolvable) {
            log.info("cannot resolve key '{}': {}", kid, unresolvable.getMessage());
            return null;
        } catch (RuntimeException unexpected) {
            log.warn("unexpected failure resolving key '{}'", kid, unexpected);
            return null;
        }
    }
}
