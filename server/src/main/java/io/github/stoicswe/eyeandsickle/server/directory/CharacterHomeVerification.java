package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of verifying a published character-home record: either an accepted {@link
 * CharacterHomeRecord}, or a {@link CharacterHomeFault} naming why it was refused.
 *
 * <p>Exactly one side is populated. This is the boundary between untrusted bytes and a trusted value: a
 * caller that holds a {@code CharacterHomeRecord} out of here knows the home server's signature was
 * checked, because the only way to obtain one is through an {@link #accepted(CharacterHomeRecord)}
 * verdict. Mirrors {@code DescriptorVerification} in the discovery slice.
 *
 * @param record the verified record, present iff {@link #isAccepted()}
 * @param fault the reason for refusal, present iff not accepted
 * @param detail a human-readable elaboration for logs; never trusted, never parsed
 */
public record CharacterHomeVerification(CharacterHomeRecord record, CharacterHomeFault fault, String detail) {

    /**
     * @param record the verified record
     * @return an accepting verdict
     */
    public static CharacterHomeVerification accepted(CharacterHomeRecord record) {
        return new CharacterHomeVerification(Objects.requireNonNull(record, "record"), null, null);
    }

    /**
     * @param fault the classification
     * @param detail a message for the operator log
     * @return a refusing verdict
     */
    public static CharacterHomeVerification rejected(CharacterHomeFault fault, String detail) {
        return new CharacterHomeVerification(null, Objects.requireNonNull(fault, "fault"), detail);
    }

    /** @return whether the record was accepted */
    public boolean isAccepted() {
        return record != null;
    }

    /** @return the verified record if accepted, else empty */
    public Optional<CharacterHomeRecord> asRecord() {
        return Optional.ofNullable(record);
    }
}
