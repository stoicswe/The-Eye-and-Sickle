package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a node cell says once the identity rung has been paid for.
 *
 * <p>The map is a character grid, so its text <em>is</em> its rendering — these assertions look at
 * exactly what reaches the screen, which is why this can be checked without starting the toolkit.
 * The one thing they must all protect is the column: every line of a cell is
 * {@code NET_NODE_COLS} wide, and a line that is not shears every column to its right.
 */
class NetCanvasIdentityTest {

    private static Sighting named(String address, String machine, String operator) {
        return new Sighting(
                address,
                machine,
                "srv-home",
                HostKind.TERMINAL,
                DifficultyTier.of(2),
                SignalStrength.MODERATE,
                1, // hopsFromRig
                1, // hopsFromVantage
                false, // self
                false, // vantage
                false, // foothold
                false, // patched
                false, // looted
                false, // honeypotSuspected
                false, // hostsDeployedMiner
                false, // documentAvailable
                "", // bridgePeerServerName
                false, // reported
                operator);
    }

    private static String[] cell(Sighting sighting) {
        return NetCanvas.cellText(sighting, false, false).split("\n", -1);
    }

    @Test
    @DisplayName("an unnamed machine shows its address and a blank line, exactly as before")
    void unnamedIsUnchanged() {
        String[] lines = cell(named("10.0.0.7", "", ""));
        assertThat(lines).hasSize(UiTokens.NET_NODE_LINES);
        assertThat(lines[3]).isEqualTo(" 10.0.0.7         ");
        assertThat(lines[4]).as("the name line is reserved, not populated").isBlank();
    }

    /**
     * ⚠ The slot is reserved whether or not there is a name in it. A box that grew a line when a scan
     * came back would re-flow the whole map underneath the player.
     */
    @Test
    @DisplayName("naming a machine does not change the cell's height or width")
    void namingChangesNoGeometry() {
        String[] blank = cell(named("10.0.0.7", "", ""));
        String[] named = cell(named("10.0.0.7", "sultry-adleman", "sasha"));
        assertThat(named).hasSameSizeAs(blank);
        for (int i = 0; i < named.length; i++) {
            assertThat(named[i]).as("line %d", i).hasSize(UiTokens.NET_NODE_COLS);
            assertThat(blank[i]).as("line %d", i).hasSize(UiTokens.NET_NODE_COLS);
        }
    }

    @Test
    @DisplayName("the operator rides on the address line and the machine name takes its own")
    void bothHalvesAreShown() {
        String[] lines = cell(named("10.0.0.7", "sultry-adleman", "sasha"));
        assertThat(lines[3]).contains("10.0.0.7").contains("sasha");
        assertThat(lines[4]).contains("sultry-adleman");
    }

    /**
     * ⚠ THE WIDTH BUDGET, at its worst case, which is why operator names are capped at seven.
     *
     * <p>One column for the selection gutter, nine for the widest address the generator can produce
     * ({@code 10.6.0.51} at the published cap of fifty machines a server), one separator, seven for
     * the account: eighteen exactly. An eighth character in the pool would be clipped off the end
     * silently. {@code NpcNamesTest.operatorsFitTheAddressLine} holds the other half of this.
     */
    @Test
    @DisplayName("the widest address and the longest allowed operator still fit the line")
    void theWidestCaseFits() {
        String[] lines = cell(named("10.6.0.51", "wandering-chandrasekhar", "solveig"));
        assertThat(lines[3])
                .hasSize(UiTokens.NET_NODE_COLS)
                .contains("10.6.0.51")
                .endsWith("solveig");
    }

    /**
     * A long name is clipped rather than wrapped, and that is the accepted cost of an 18-column box:
     * about 2.5% of the pool's combinations overrun. The unclipped name is on the tooltip, in the host
     * list and in the RECON file, so nothing is unreachable — what must not happen is the cell growing
     * to fit it and shearing the column.
     */
    @Test
    @DisplayName("an over-long name is clipped, never wrapped")
    void longNamesClip() {
        String[] lines = cell(named("10.0.0.7", "wandering-chandrasekhar", ""));
        assertThat(lines[4]).hasSize(UiTokens.NET_NODE_COLS).startsWith(" wandering-chandra");
    }
}
