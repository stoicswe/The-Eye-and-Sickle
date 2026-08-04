package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Where a client presents its proof of identity — the last piece of Option C's loop.
 *
 * <h2>⚠ The request body carries a PROOF, not a claim</h2>
 *
 * The only field that matters is a service-auth JWT. {@link SignInService} runs it through
 * {@link ServiceAuthVerifier}, which checks the signature against a DID document <em>this server</em>
 * resolves — so nothing here is trusted because the client asserted it.
 *
 * <p>⚠ {@code claimedDid} is deliberately not accepted on this endpoint. It exists on
 * {@link SignInCredentials} for {@link DevAtProtoIdentityProvider}, which is disabled by default, and
 * accepting it here would put the one unverified field in the game's entire identity path on a public
 * HTTP surface.
 *
 * <h2>What comes back</h2>
 *
 * The character-select payload: who you are, and which characters you may play. Not a play session —
 * that is minted per character ({@code CharacterService.selectCharacter}), per
 * {@code docs/architecture/09-player-state-portability.md} §1–§2.
 */
@Tag(name = "identity")
@RestController
@RequestMapping("/api/sign-in")
public class SignInController {

    private final SignInService signIn;

    SignInController(SignInService signIn) {
        this.signIn = Objects.requireNonNull(signIn, "signIn");
    }

    /**
     * What a client sends.
     *
     * @param serviceAuthToken an AT Protocol inter-service auth JWT whose {@code aud} is this server
     */
    public record SignInRequest(String serviceAuthToken) {}

    /**
     * The character-select payload.
     *
     * @param did the authenticated account
     * @param handle its verified display handle, or null when none verified
     * @param characters the playable characters
     */
    public record SignInResponse(String did, String handle, List<CharacterSummary> characters) {}

    /**
     * Authenticates a client and returns its account.
     *
     * @param request the presented proof
     * @return the account and its characters (200); 401 if the proof does not check out; 403 if the
     *     DID is real but not allow-listed ({@code docs/architecture/03} §1, closed by default)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public SignInResponse signIn(@RequestBody SignInRequest request) {
        AccountSession account = signIn.signIn(new SignInCredentials(
                null, null, null, null, null, request == null ? null : request.serviceAuthToken()));
        return new SignInResponse(
                account.did().value(),
                account.handle(),
                account.characters().stream().map(CharacterSummary::from).toList());
    }
}
