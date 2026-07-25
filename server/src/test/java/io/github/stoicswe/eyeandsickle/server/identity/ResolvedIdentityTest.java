package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ResolvedIdentity} is the type boundary that keeps "authenticated" from degrading into
 * "claimed": it is what a provider returns only after proving control of the DID. The DID is mandatory;
 * the handle rides along and may be absent.
 */
class ResolvedIdentityTest {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");

    @Test
    @DisplayName("a resolved identity must carry a DID")
    void didRequired() {
        // A ResolvedIdentity asserts authentication succeeded; one without a DID would be that assertion
        // with nothing behind it.
        assertThatThrownBy(() -> new ResolvedIdentity(null, "alice.bsky.social"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the handle may be null — the provider might resolve none")
    void handleOptional() {
        assertThatCode(() -> new ResolvedIdentity(DID, null)).doesNotThrowAnyException();
        assertThat(new ResolvedIdentity(DID, null).handle()).isNull();
    }

    @Test
    @DisplayName("it carries exactly the DID and handle it was given")
    void carriesValues() {
        ResolvedIdentity identity = new ResolvedIdentity(DID, "alice.bsky.social");
        assertThat(identity.did()).isEqualTo(DID);
        assertThat(identity.handle()).isEqualTo("alice.bsky.social");
    }
}
