package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The join gate. Closed by default: with enforcement on, only a DID with an active allowlist entry may
 * join, and an empty allowlist therefore admits nobody. With enforcement explicitly off, any
 * already-authenticated DID may join and the table is not even consulted.
 */
class AllowlistPolicyTest {

    private static final Did ALLOWED = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Did STRANGER = Did.of("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb");

    private static AllowlistProperties enforced() {
        return new AllowlistProperties(true, List.of());
    }

    private static AllowlistProperties open() {
        return new AllowlistProperties(false, List.of());
    }

    @Test
    @DisplayName("with enforcement on, a listed DID is permitted")
    void listedDidPermitted() {
        FakeAllowlistRepository allowlist = new FakeAllowlistRepository().allow(ALLOWED);
        AllowlistPolicy policy = new AllowlistPolicy(allowlist, enforced());

        assertThat(policy.permits(ALLOWED)).isTrue();
    }

    @Test
    @DisplayName("with enforcement on, an unlisted DID is refused")
    void unlistedDidRefused() {
        // Defends the core posture: authenticated is not the same as authorized to join.
        FakeAllowlistRepository allowlist = new FakeAllowlistRepository().allow(ALLOWED);
        AllowlistPolicy policy = new AllowlistPolicy(allowlist, enforced());

        assertThat(policy.permits(STRANGER)).isFalse();
    }

    @Test
    @DisplayName("closed by default: an empty allowlist admits nobody")
    void emptyAllowlistAdmitsNobody() {
        // A freshly-installed server holds real player state, so the safe default is private. An empty
        // table under the default (enforced) properties refuses every DID.
        AllowlistPolicy policy =
                new AllowlistPolicy(new FakeAllowlistRepository(), new AllowlistProperties(null, null));

        assertThat(policy.permits(ALLOWED)).isFalse();
    }

    @Test
    @DisplayName("with enforcement off, any authenticated DID is permitted and the table is not consulted")
    void openServerSkipsTheTable() {
        // Explicit operator opt-out: running open is a chosen openness, and it short-circuits before the
        // allowlist lookup entirely.
        FakeAllowlistRepository allowlist = new FakeAllowlistRepository();
        AllowlistPolicy policy = new AllowlistPolicy(allowlist, open());

        assertThat(policy.permits(STRANGER)).isTrue();
        assertThat(allowlist.wasQueried())
                .as("an open server must not even read the allowlist")
                .isFalse();
    }

    @Test
    @DisplayName("permits(null) is a programming error")
    void nullDidRejected() {
        AllowlistPolicy policy = new AllowlistPolicy(new FakeAllowlistRepository(), enforced());
        assertThatThrownBy(() -> policy.permits(null)).isInstanceOf(NullPointerException.class);
    }
}
