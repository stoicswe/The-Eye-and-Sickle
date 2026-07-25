package io.github.stoicswe.eyeandsickle.server.discovery;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of verifying a self-descriptor: either an accepted {@link ServerDescriptor}, or a
 * {@link DescriptorFault} naming why it was refused.
 *
 * <p>Exactly one side is populated. This is the boundary between untrusted bytes and a trusted value:
 * a caller that holds a {@code ServerDescriptor} knows the signature was checked, because the only way
 * to obtain one is through an {@link #accepted(ServerDescriptor)} verdict.
 *
 * @param descriptor the verified descriptor, present iff {@link #isAccepted()}
 * @param fault the reason for refusal, present iff not accepted
 * @param detail a human-readable elaboration for logs; never trusted, never parsed
 */
public record DescriptorVerification(ServerDescriptor descriptor, DescriptorFault fault, String detail) {

    /**
     * @param descriptor the verified descriptor
     * @return an accepting verdict
     */
    public static DescriptorVerification accepted(ServerDescriptor descriptor) {
        return new DescriptorVerification(Objects.requireNonNull(descriptor, "descriptor"), null, null);
    }

    /**
     * @param fault the classification
     * @param detail a message for the operator log
     * @return a refusing verdict
     */
    public static DescriptorVerification rejected(DescriptorFault fault, String detail) {
        return new DescriptorVerification(null, Objects.requireNonNull(fault, "fault"), detail);
    }

    /** @return whether the descriptor was accepted */
    public boolean isAccepted() {
        return descriptor != null;
    }

    /** @return the verified descriptor if accepted, else empty */
    public Optional<ServerDescriptor> asDescriptor() {
        return Optional.ofNullable(descriptor);
    }
}
