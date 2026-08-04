package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.server.items.ServerSigningProperties;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Says which DID this server is, so a client can mint a token bound to it.
 *
 * <h2>⚠ This is self-asserted, and the client's comment says so too</h2>
 *
 * A service-auth token is bound to an audience, so a client must know this server's DID before it can
 * mint one — and today the only place to get it is here, from the server itself. A hostile server
 * could therefore name a <em>third party's</em> DID and collect a token minted for them.
 *
 * <p>It gains nothing directly: {@link ServiceAuthVerifier} checks {@code aud} against the receiving
 * server's own DID, so a token for {@code did:web:a} is refused at {@code did:web:b}. The residual is
 * that a hostile server can induce a client to mint a token for a third party and relay it there.
 * ⚠ Closing it needs the DID to reach the client from somewhere this server does not control — the
 * signed descriptor of {@code docs/architecture/08} or a discovery list ({@code 11}). Recorded, not
 * papered over.
 *
 * <p>Unauthenticated on purpose: it is a public fact about a public endpoint, and requiring auth to
 * learn who you are talking to is a chicken-and-egg with no upside.
 */
@Tag(name = "identity")
@RestController
@RequestMapping("/api/server")
public class ServerIdentityController {

    private final ServerSigningProperties signing;

    ServerIdentityController(ServerSigningProperties signing) {
        this.signing = Objects.requireNonNull(signing, "signing");
    }

    /**
     * @param did this server's DID — what a service-auth token's {@code aud} must be
     */
    public record ServerIdentity(String did) {}

    @GetMapping
    public ServerIdentity identity() {
        return new ServerIdentity(signing.did());
    }
}
