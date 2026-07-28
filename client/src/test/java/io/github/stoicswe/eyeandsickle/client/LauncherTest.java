package io.github.stoicswe.eyeandsickle.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The application's name, on the three platforms that ask for it differently.
 *
 * <p>No toolkit is started — {@link Launcher#nameTheApplication()} is deliberately separate from
 * {@code Application.launch} so it can be called and checked on its own, which is also why it runs
 * before the launch rather than inside {@code start}.
 */
class LauncherTest {

    @Test
    @DisplayName("both platform naming properties are set, and to the same name")
    void bothPropertiesAreSet() {
        // macOS reads the first, Linux's window manager the second. Neither is guarded by an
        // os.name check: both are harmless where ignored, and a branch would be three code paths to
        // keep correct in exchange for skipping two setProperty calls.
        Launcher.nameTheApplication();

        assertThat(System.getProperty("apple.awt.application.name")).isEqualTo(Launcher.APP_NAME);
        assertThat(System.getProperty("glass.appName")).isEqualTo(Launcher.APP_NAME);
    }

    @Test
    @DisplayName("the name is the one the player was promised")
    void theName() {
        assertThat(Launcher.APP_NAME).isEqualTo("EAS uOS Client");
    }

    @Test
    @DisplayName("⚠ macOS's dock flag is in the run configurations, since no property can set it")
    void dockFlagIsWiredUp() throws Exception {
        // -Xdock:name is a JVM flag, not a system property, so it cannot be set from inside main.
        // If it falls out of the POM the macOS dock silently goes back to saying "java", with
        // nothing at runtime to notice — which is exactly the kind of thing a test should hold.
        String pom = java.nio.file.Files.readString(java.nio.file.Path.of("pom.xml"));
        assertThat(pom)
                .as("javafx:run must pass -Xdock:name")
                .contains("-Xdock:name=" + Launcher.APP_NAME);
    }
}
