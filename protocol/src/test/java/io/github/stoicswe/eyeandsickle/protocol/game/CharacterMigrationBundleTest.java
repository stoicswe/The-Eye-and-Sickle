package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The untrusted, verifiable migration bundle (Option C, 09 §6). Two properties matter: it carries
 * <em>only</em> portable state (a DID and per-item chains — never economy, §3), and it is a
 * deep-immutable value so a courier cannot mutate a chain out from under a destination mid-verification.
 */
class CharacterMigrationBundleTest {

    private static final UUID CHARACTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final CharacterRef SOURCE = CharacterRef.of(CHARACTER_ID, 2);
    private static final String ACCOUNT_DID = "did:plc:account00000000000000";
    private static final String HOME_DID = "did:plc:homeserver0000000000";

    private static CharacterMigrationBundle bundle(List<ItemChain> chains) {
        return new CharacterMigrationBundle(ACCOUNT_DID, SOURCE, HOME_DID, 7L, chains);
    }

    @Test
    @DisplayName("carries the DID, source reference, home binding and item chains")
    void holdsPortableState() {
        ItemChain chain = new ItemChain(ITEM_ID, List.of("{env0}", "{env1}"));
        CharacterMigrationBundle b = bundle(List.of(chain));

        assertThat(b.accountDid()).isEqualTo(ACCOUNT_DID);
        assertThat(b.sourceCharacter()).isEqualTo(SOURCE);
        assertThat(b.sourceHomeServerDid()).isEqualTo(HOME_DID);
        assertThat(b.homeSequence()).isEqualTo(7L);
        assertThat(b.itemChains()).containsExactly(chain);
        assertThat(chain.envelopes()).containsExactly("{env0}", "{env1}");
    }

    @Test
    @DisplayName("a character with no items still migrates (empty inventory is allowed)")
    void allowsEmptyInventory() {
        assertThat(bundle(List.of()).itemChains()).isEmpty();
    }

    @Test
    @DisplayName("the required identity fields must be present and non-blank")
    void rejectsMissingIdentity() {
        assertThatThrownBy(() -> new CharacterMigrationBundle(null, SOURCE, HOME_DID, 0L, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CharacterMigrationBundle(" ", SOURCE, HOME_DID, 0L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CharacterMigrationBundle(ACCOUNT_DID, null, HOME_DID, 0L, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CharacterMigrationBundle(ACCOUNT_DID, SOURCE, " ", 0L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a home sequence is never negative — it is a monotonic directory value (§4)")
    void rejectsNegativeSequence() {
        assertThatThrownBy(() -> new CharacterMigrationBundle(ACCOUNT_DID, SOURCE, HOME_DID, -1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an item chain must carry at least its genesis record")
    void rejectsEmptyChain() {
        assertThatThrownBy(() -> new ItemChain(ITEM_ID, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemChain(null, List.of("{env}"))).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("it is a deep-immutable value — mutating the source lists does not reach inside")
    void isDeeplyImmutable() {
        List<String> envelopes = new ArrayList<>(List.of("{env0}"));
        ItemChain chain = new ItemChain(ITEM_ID, envelopes);
        List<ItemChain> chains = new ArrayList<>(List.of(chain));
        CharacterMigrationBundle b = bundle(chains);

        envelopes.add("{tampered}");
        chains.clear();

        assertThat(b.itemChains()).containsExactly(chain);
        assertThat(b.itemChains().getFirst().envelopes()).containsExactly("{env0}");
        assertThatThrownBy(() -> b.itemChains().add(chain)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> b.itemChains().getFirst().envelopes().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("it structurally cannot carry economy — there is no field to smuggle it in (§3)")
    void carriesNoEconomy() {
        // A compile-time guarantee expressed as a test: the only components are identity + item chains.
        // If a future edit adds an ethecoin/heat/faction field to the untrusted bundle, this record's
        // component list changes and this test is the reminder that doing so breaks Invariant I14.
        assertThat(CharacterMigrationBundle.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactlyInAnyOrder(
                        "accountDid", "sourceCharacter", "sourceHomeServerDid", "homeSequence", "itemChains");
    }
}
