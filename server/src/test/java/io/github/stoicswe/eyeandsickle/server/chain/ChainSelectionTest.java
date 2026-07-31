package io.github.stoicswe.eyeandsickle.server.chain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.ChainHead;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The chain-selection rule, which is the whole of consensus that can be tested without a network.
 *
 * <h2>Why every one of these is a security test</h2>
 *
 * Each case below is an attack that works if the rule is written the obvious way: count blocks and take
 * the biggest number. Getting this wrong does not produce a crash — it produces a server that quietly
 * adopts somebody else's ledger, which is Invariant <b>I15</b> failing in the one direction nobody
 * notices.
 */
class ChainSelectionTest {

    private static final String GENESIS = "0x" + "a1".repeat(32);

    private static ChainHead head(long height, double work) {
        return new ChainHead(height, "0x" + "bb".repeat(32), work, GENESIS);
    }

    @Test
    @DisplayName("⚠ more work wins, even when it is fewer blocks")
    void workBeatsHeight() {
        ChainHead honest = head(100, 5_000);
        ChainHead taller = new ChainHead(400, "0x" + "cc".repeat(32), 2_000, GENESIS);

        // The attack the rule exists to stop: four times the blocks at a fraction of the difficulty.
        // A height comparison adopts it, and the attacker rewrote history without doing the work.
        assertThat(ChainSelection.better(honest, List.of(taller))).isEmpty();
    }

    @Test
    @DisplayName("a genuinely heavier chain is adopted")
    void heavierWins() {
        ChainHead local = head(100, 5_000);
        ChainHead heavier = head(101, 5_100);
        assertThat(ChainSelection.better(local, List.of(heavier))).contains(heavier);
    }

    @Test
    @DisplayName("⚠ a chain with a different genesis is never adopted, however heavy")
    void foreignGenesisIsRefused() {
        ChainHead local = head(100, 5_000);
        ChainHead stranger = new ChainHead(9_000, "0x" + "dd".repeat(32), 1e12, "0x" + "ff".repeat(32));

        // Not a longer chain — a different currency. Adopting it would not be a reorganisation; it
        // would migrate every balance this server holds onto somebody else's ledger.
        assertThat(ChainSelection.better(local, List.of(stranger))).isEmpty();
    }

    @Test
    @DisplayName("ties go to the incumbent, so history does not depend on who answered first")
    void tiesKeepTheLocalChain() {
        ChainHead local = head(100, 5_000);
        assertThat(ChainSelection.better(local, List.of(head(100, 5_000)))).isEmpty();
    }

    @Test
    @DisplayName("the heaviest of several candidates is chosen")
    void picksTheHeaviest() {
        ChainHead local = head(100, 5_000);
        ChainHead best = head(140, 9_000);
        assertThat(ChainSelection.better(local, List.of(head(120, 6_000), best, head(130, 8_000))))
                .contains(best);
    }

    @Test
    @DisplayName("a server with no chain joins any it is offered rather than starting its own")
    void anEmptyServerJoins() {
        ChainHead theirs = head(50, 1_000);
        assertThat(ChainSelection.better(null, List.of(theirs))).contains(theirs);
        assertThat(ChainSelection.shouldMintGenesis(null, List.of(theirs))).isFalse();
    }

    @Test
    @DisplayName("⚠ genesis is minted only when nobody has a chain at all")
    void genesisOnlyWhenAlone() {
        // A server that minted a genesis while a peer already had a chain would fork the federation on
        // startup, and both halves would be certain they were right.
        assertThat(ChainSelection.shouldMintGenesis(null, List.of())).isTrue();
        assertThat(ChainSelection.shouldMintGenesis(null, null)).isTrue();
        assertThat(ChainSelection.shouldMintGenesis(head(1, 10), List.of())).isFalse();
    }

    @Test
    @DisplayName("nulls and nonsense are ignored rather than crashing the bootstrap")
    void junkIsSurvivable() {
        ChainHead local = head(100, 5_000);
        assertThat(ChainSelection.better(local, java.util.Arrays.asList(null, null)))
                .isEmpty();
        assertThat(ChainSelection.better(null, java.util.Arrays.asList((ChainHead) null)))
                .isEmpty();
        // A head is a claim from a stranger. Refusing to start because one was malformed would make a
        // server's availability depend on its worst-behaved peer.
        assertThat(ChainSelection.shouldMintGenesis(null, java.util.Arrays.asList((ChainHead) null)))
                .isTrue();
    }
}
