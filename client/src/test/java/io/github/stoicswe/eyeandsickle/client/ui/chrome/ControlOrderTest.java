package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The desk-window control order.
 *
 * <p>No toolkit anywhere — the decision is a pure function of the setting and the platform, which is
 * why {@link ControlOrder#closeFirst} takes the platform as an argument rather than reading
 * {@code os.name} itself. A rule that asks the environment what it is cannot be tested as the other
 * environment.
 */
class ControlOrderTest {

    @Nested
    @DisplayName("resolving the setting")
    class Resolving {

        @Test
        @DisplayName("`system` follows the platform, and the other two override it")
        void systemFollowsThePlatform() {
            // The default, because it is the arrangement the player's hand already knows.
            assertThat(ControlOrder.SYSTEM.closeFirst(true)).isTrue();
            assertThat(ControlOrder.SYSTEM.closeFirst(false)).isFalse();

            // An explicit choice means the same thing on both platforms — that is what makes it a
            // choice rather than a second way of spelling "system".
            assertThat(ControlOrder.MACOS.closeFirst(true)).isTrue();
            assertThat(ControlOrder.MACOS.closeFirst(false)).isTrue();
            assertThat(ControlOrder.WINDOWS.closeFirst(true)).isFalse();
            assertThat(ControlOrder.WINDOWS.closeFirst(false)).isFalse();
        }

        @Test
        @DisplayName("an unknown or missing id falls back rather than throwing")
        void unknownIdFallsBack() {
            // A settings file outlives the build that wrote it. Losing every preference to one
            // stale string is the failure ClientProfile's own reader is written to avoid.
            assertThat(ControlOrder.resolve("nonsense")).isEqualTo(ControlOrder.SYSTEM);
            assertThat(ControlOrder.resolve(null)).isEqualTo(ControlOrder.SYSTEM);
            assertThat(ControlOrder.resolve("")).isEqualTo(ControlOrder.SYSTEM);
        }

        @Test
        @DisplayName("every option round-trips through its stored id")
        void idsRoundTrip() {
            for (ControlOrder order : ControlOrder.selectable()) {
                assertThat(ControlOrder.byId(order.id())).contains(order);
                assertThat(order.label()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("⚠ what it must NOT do")
    class Boundaries {

        @Test
        @DisplayName("it carries no notion of a side — only of an order")
        void orderNotSide() {
            // The side is a platform convention the outer window follows unconditionally, and two
            // players can disagree about whether close comes first without disagreeing about which
            // corner it lives in. If this enum ever grows a `left`/`right`, that separation is gone.
            for (ControlOrder order : ControlOrder.values()) {
                assertThat(order.name().toLowerCase(java.util.Locale.ROOT))
                        .doesNotContain("left")
                        .doesNotContain("right");
            }
        }

        @Test
        @DisplayName("the default is `system`, so an untouched profile matches the player's desktop")
        void defaultIsSystem() {
            assertThat(new io.github.stoicswe.eyeandsickle.client.profile.ClientProfile.Settings()
                            .subwindowControlOrder)
                    .isEqualTo(ControlOrder.SYSTEM.id());
        }
    }
}
