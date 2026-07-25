package io.github.stoicswe.eyeandsickle.client;

import static org.assertj.core.api.Assertions.assertThat;

import atlantafx.base.theme.PrimerDark;
import io.github.stoicswe.eyeandsickle.client.window.ToolWindow;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that do not need a display.
 *
 * <p>Nothing here starts the JavaFX toolkit. Headless UI testing needs Monocle or TestFX and a
 * decision about how much of it is worth maintaining; until then, these assertions cover the wiring
 * that actually breaks in practice — a theme stylesheet that fails to resolve from the jar, and a
 * shared-module type that turns out not to be on the client's classpath.
 */
class ClientScaffoldTest {

    @Test
    @DisplayName("the AtlantaFX theme stylesheet resolves from the packaged jar")
    void themeStylesheetResolves() {
        // A theme that silently fails to load leaves the client looking like a 2005 Swing app, and
        // it fails at runtime on a user's machine rather than here.
        String stylesheet = new PrimerDark().getUserAgentStylesheet();
        assertThat(stylesheet).isNotBlank();
        assertThat(new PrimerDark().isDarkMode()).isTrue();
    }

    @Test
    @DisplayName("the rig monitor is defined and floats above other tools")
    void rigMonitorIsAlwaysOnTop() {
        ToolWindow rigMonitor = ToolWindow.rigMonitor();

        assertThat(rigMonitor.id()).isEqualTo("rig-monitor");
        assertThat(rigMonitor.title()).contains("Rig Monitor");
        assertThat(rigMonitor.alwaysOnTop())
                .as("the compute ledger is the game's most important HUD element "
                        + "(docs/design/01-core-resources.md §1.4)")
                .isTrue();
    }

    @Test
    @DisplayName("shared protocol types are on the client classpath")
    void protocolTypesAreVisible() {
        assertThat(StorageTier.values()).contains(StorageTier.VAULT, StorageTier.HIGH_HACKABLE_ZONE);
    }

    @Test
    @DisplayName("no server code is reachable from the client")
    void serverIsNotOnTheClasspath() {
        // Invariant I14, checked rather than assumed. The enforcer rule in client/pom.xml blocks the
        // dependency at build time; this catches a shaded or transitively-vendored copy sneaking in.
        assertThat(canLoad("io.github.stoicswe.eyeandsickle.server.EyeAndSickleServerApplication"))
                .as("the client must never be able to reach server code")
                .isFalse();
        assertThat(canLoad("org.springframework.boot.SpringApplication")).isFalse();
    }

    private static boolean canLoad(String className) {
        try {
            Class.forName(className, false, ClientScaffoldTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
