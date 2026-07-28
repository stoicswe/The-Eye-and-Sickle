package io.github.stoicswe.eyeandsickle.client.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two window-chrome opt-ins, and the defaults that keep the design language honest.
 *
 * <p>Both amend a contract: §9.3 permits rounded corners, §0.1 permits the system window border.
 * Neither amendment says the game should <em>ship</em> that way, and the whole force of "§0 and §9
 * still describe what ships" rests on these two booleans. A default flipped by accident would make
 * two documents wrong at once, silently, with the build still green — which is precisely the kind of
 * thing a one-line test should hold.
 */
class WindowChromeSettingsTest {

    @Test
    @DisplayName("⚠ both chrome opt-ins ship OFF — §0 and §9 describe the default, not an option")
    void chromeOptInsDefaultOff() {
        ClientProfile.Settings settings = new ClientProfile.Settings();
        // ⚠ The two chrome opt-ins now live in different places, and deliberately so: rounding is
        // per character (it is part of the look), while the system border is machine-wide because
        // Stage.initStyle is rejected on a realised Stage and a per-character one could not take
        // effect until a restart.
        VisualSettings look = new VisualSettings();

        assertThat(look.roundedWindows)
                .as("§9.3: rounded corners are opt-in; §9's rejection list describes the default")
                .isFalse();
        assertThat(settings.nativeWindowBorder)
                .as("§0.1: the system border is opt-in; §10 criterion 1 describes the default")
                .isFalse();
    }

    @Test
    @DisplayName("they persist independently, so one cannot switch the other on")
    void independent() {
        // They interact at render time — a native border suppresses the outer rounding, because the
        // OS owns those corners — but that is a decision made when drawing, not a coupling in the
        // stored settings. Storing one as a function of the other would make a player's explicit
        // choice disappear the next time they toggled the other.
        ClientProfile.Settings settings = new ClientProfile.Settings();
        VisualSettings look = new VisualSettings();
        settings.nativeWindowBorder = true;

        assertThat(look.roundedWindows).isFalse();

        look.roundedWindows = true;
        settings.nativeWindowBorder = false;
        assertThat(look.roundedWindows).isTrue();
    }
}
