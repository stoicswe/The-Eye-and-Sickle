package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The machine the player has picked is unmistakable on the graph, and costs the picture nothing.
 *
 * <h2>Why "costs nothing" is asserted as hard as "is visible"</h2>
 *
 * The map is a character-cell texture whose every column is a fixed width, and the two obvious ways
 * to mark a cell — prepend a marker, or widen the frame — both shear everything to their right. The
 * marks chosen here are a one-for-one character swap (the frame) and a replacement of a blank the
 * address line already had (the bar), so the grid is byte-for-byte the same size selected or
 * not. That is the property worth a test; "did it get brighter" is a stylesheet question.
 */
class NetSelectionTest {

    private static String frame(NetMap map, String selected) {
        return NetCanvas.frame(map, UiTokens.NET_MAX_ROWS, 0, selected);
    }

    @Test
    @DisplayName("selecting a machine changes no line's width and no line's count")
    void geometryIsUntouched() {
        NetMap map = NetFixtures.twoHops();
        List<String> plain = NetCanvas.paint(map, UiTokens.NET_MAX_ROWS, 0).lines();
        List<String> picked = NetCanvas.paint(map, UiTokens.NET_MAX_ROWS, 0, "10.0.0.17").lines();

        assertThat(picked).hasSameSizeAs(plain);
        for (int line = 0; line < plain.size(); line++) {
            assertThat(picked.get(line).length())
                    .as("line %d keeps its width", line)
                    .isEqualTo(plain.get(line).length());
        }
    }

    @Test
    @DisplayName("the selected machine's address carries a gutter bar, and no other address does")
    void exactlyOneBar() {
        String plain = frame(NetFixtures.twoHops(), "");
        String drawn = frame(NetFixtures.twoHops(), "10.0.0.17");

        assertThat(drawn).contains("▌10.0.0.17");
        // ⚠ A bar rather than the obvious `→`, and this assertion is why. The map already draws an
        // arrowhead at the destination end of every forward edge — a two-hop fixture has nine of
        // them — so a selection marked with one would be invisible in a crowd of its own glyph. The
        // bar appears nowhere else on this surface, which is the property being defended here.
        assertThat(plain.chars().filter(c -> c == '▌').count()).isZero();
        assertThat(drawn.chars().filter(c -> c == '▌').count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a selected machine is double-framed, and it is the only double frame on the map")
    void doubleFrame() {
        String plain = frame(NetFixtures.twoHops(), "");
        String picked = frame(NetFixtures.twoHops(), "10.0.0.17");

        assertThat(plain).doesNotContain("╔");
        assertThat(picked.chars().filter(c -> c == '╔').count()).isEqualTo(1);
        assertThat(picked.chars().filter(c -> c == '╝').count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the vantage keeps its heavy frame when it is the selection — and still says so")
    void vantageOutranksSelection() {
        String picked = frame(NetFixtures.twoHops(), "10.0.0.1");

        // ⚠ The precedence that matters. Where the player is standing is a fact about the WHOLE map
        // — every hop count on it is measured from there — while a selection is a transient
        // intention. A mark that could hide the frame of reference would cost more than it bought.
        assertThat(picked).contains("┏").doesNotContain("╔");
        // And selection is still unmistakable, because the bar is unconditional.
        assertThat(picked).contains("▌10.0.0.1");
    }

    @Test
    @DisplayName("an address that is not on the map marks nothing and does not throw")
    void staleSelection() {
        // A selection outlives the sighting that produced it by a repaint or two — a machine can go
        // out of view between the click and the frame. Throwing here would crash the panel on the
        // frame after that happens.
        String drawn = frame(NetFixtures.twoHops(), "10.9.9.9");
        assertThat(drawn).isEqualTo(frame(NetFixtures.twoHops(), ""));
    }

    @Test
    @DisplayName("a bridge stub is never marked — there is nothing behind it to act on")
    void stubsAreNotSelectable() {
        // A stub carries a peer server's NAME and no address the player has been sold, so it has
        // nothing CONNECT or a breach could take. It is drawn unframed for the same reason.
        assertThat(NetCanvas.paint(NetFixtures.twoHops(), UiTokens.NET_MAX_ROWS, 0, "10.0.0.12")
                        .pieces()
                        .stream()
                        .filter(NetCanvas.Piece::stub)
                        .filter(NetCanvas.Piece::selected))
                .isEmpty();
    }

    @Test
    @DisplayName("exactly one piece reports itself selected, and it is the one asked for")
    void oneSelectedPiece() {
        List<NetCanvas.Piece> selected =
                NetCanvas.paint(NetFixtures.twoHops(), UiTokens.NET_MAX_ROWS, 0, "10.0.0.9").pieces()
                        .stream()
                        .filter(NetCanvas.Piece::selected)
                        .toList();
        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().address()).isEqualTo("10.0.0.9");
    }
}
