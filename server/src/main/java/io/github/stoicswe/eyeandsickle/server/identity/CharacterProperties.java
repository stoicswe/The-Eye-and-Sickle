package io.github.stoicswe.eyeandsickle.server.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How many characters an account may hold, bound from {@code eyeandsickle.characters}.
 *
 * <h2>A product knob, not a balance value (09 §1)</h2>
 *
 * {@code docs/architecture/09-player-state-portability.md} §1 fixes the default at three — "An account
 * may hold up to {@code EYEANDSICKLE_MAX_CHARACTERS} characters — default 3" — and is explicit that this
 * "is a product knob (configurable), not a balance value." So it lives here, in one bound and tunable
 * place, rather than being scattered as a constant, and a self-hoster may raise or lower it freely
 * without touching the economy calibration.
 *
 * <h2>Online-only, and only ever soft (09 §1-§2, Invariant I15)</h2>
 *
 * The cap is a property of <em>online, DID-bound</em> play. A local, DID-less character is exempt
 * entirely — no slot, no cap. And even for online play the cap is <strong>soft</strong>: there is no
 * global account table (I15 forbids a single arbiter), so it is enforced by honest servers consulting a
 * signed character directory, backed federation-wide by non-recognition of any excess. This class
 * supplies the number; {@link CharacterService} enforces it against a {@link RecognizedCharacterCount}
 * that a directory-aware deployment can widen from "this server's rows" to "the whole federation".
 *
 * @param maxCharacters the most characters one account may hold in online play; defaults to 3, and must
 *     be at least 1 and no more than {@link Player#MAX_SLOT} (a character above the top slot number could
 *     never be assigned a slot)
 */
@ConfigurationProperties(prefix = "eyeandsickle.characters")
public record CharacterProperties(@DefaultValue("3") int maxCharacters) {

    public CharacterProperties {
        if (maxCharacters < 1) {
            throw new IllegalArgumentException(
                    "eyeandsickle.characters.max-characters must be at least 1, was " + maxCharacters);
        }
        if (maxCharacters > Player.MAX_SLOT) {
            throw new IllegalArgumentException("eyeandsickle.characters.max-characters cannot exceed the structural "
                    + "slot bound of " + Player.MAX_SLOT + " (there would be no slot to assign the extra character), was "
                    + maxCharacters);
        }
    }
}
