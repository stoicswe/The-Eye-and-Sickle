package io.github.stoicswe.eyeandsickle.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The ABOUT tab's figures.
 *
 * <p>⚠ These tests assert <b>shape and honesty</b>, not values. The whole class reads the machine it
 * is running on, so asserting "64 GB" would pass here and fail on every other developer's laptop and
 * on all three CI runners. What is worth pinning is that nothing is blank, nothing leaks a build
 * placeholder onto the screen, and every lookup degrades to a stated {@code UNAVAILABLE} rather than
 * throwing — an About panel must never be the thing that takes a window down.
 *
 * <p>Runs headless: nothing here starts the toolkit. {@code graphics()} is the one readout that
 * touches JavaFX, and it is written to answer without a running toolkit precisely so this test can
 * call it.
 */
class SystemReportTest {

    @Nested
    @DisplayName("the rows")
    class Rows {

        @Test
        @DisplayName("are the six the panel promises, in reading order")
        void shape() {
            assertThat(SystemReport.rows())
                    .containsExactly(
                            entryKey("CLIENT"),
                            entryKey("BUILD"),
                            entryKey("RUNTIME"),
                            entryKey("HOST OS"),
                            entryKey("CPU"),
                            entryKey("GPU"),
                            entryKey("MEMORY"))
                    .isNotEmpty();
        }

        @Test
        @DisplayName("are never blank — a missing figure says so")
        void nothingIsBlank() {
            for (Map.Entry<String, String> row : SystemReport.rows().entrySet()) {
                assertThat(row.getValue()).as("value for %s", row.getKey()).isNotBlank();
            }
        }

        /**
         * ⚠ The failure this catches is a build one, not a code one: an unfiltered
         * {@code build.properties} would put the literal {@code ${client.version}} on screen. The
         * client version guards against it explicitly, and this asserts no row anywhere does it.
         */
        @Test
        @DisplayName("never show an unresolved build placeholder")
        void noPlaceholderLeaks() {
            for (Map.Entry<String, String> row : SystemReport.rows().entrySet()) {
                assertThat(row.getValue()).as("value for %s", row.getKey()).doesNotContain("${");
            }
        }

        private static Map.Entry<String, String> entryKey(String key) {
            return org.assertj.core.api.Assertions.entry(key, SystemReport.rows().get(key));
        }
    }

    @Nested
    @DisplayName("architecture")
    class Architecture {

        @Test
        @DisplayName("names the two the client actually ships for")
        void known() {
            assertThat(SystemReport.archName("aarch64")).isEqualTo("ARM 64-BIT");
            assertThat(SystemReport.archName("arm64")).isEqualTo("ARM 64-BIT");
            assertThat(SystemReport.archName("amd64")).isEqualTo("X86 64-BIT");
            assertThat(SystemReport.archName("x86_64")).isEqualTo("X86 64-BIT");
        }

        /** Case matters here: {@code os.arch} is lowercase on every platform but nothing enforces it. */
        @Test
        @DisplayName("is case-insensitive, and says so rather than guessing when it does not know")
        void unknown() {
            assertThat(SystemReport.archName("AArch64")).isEqualTo("ARM 64-BIT");
            assertThat(SystemReport.archName("sparcv9")).isEqualTo("UNRECOGNISED");
        }
    }

    @Nested
    @DisplayName("memory")
    class Memory {

        /**
         * ⚠ 1024-based, and this is the assertion that pins it. A decimal conversion reports a 64 GB
         * machine as 68.7 GB — precisely wrong, on the one line a player is most likely to check
         * against a number they already know.
         */
        @Test
        @DisplayName("is gibibytes, not gigabytes")
        void binary() {
            assertThat(SystemReport.gigabytes(68719476736L)).isEqualTo("64 GB");
            assertThat(SystemReport.gigabytes(17179869184L)).isEqualTo("16 GB");
        }

        @Test
        @DisplayName("keeps a decimal below 1 GB rather than rounding to zero")
        void small() {
            assertThat(SystemReport.gigabytes(536870912L)).isEqualTo("0.5 GB");
        }

        @Test
        @DisplayName("reports nothing as unavailable rather than as 0 GB")
        void none() {
            assertThat(SystemReport.gigabytes(0)).isEqualTo(SystemReport.UNKNOWN);
            assertThat(SystemReport.gigabytes(-1)).isEqualTo(SystemReport.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("the host lookups")
    class Host {

        /**
         * The point of the class comment made executable: every one of these reaches for something
         * optional — a JDK extension, an internal JavaFX class, a filtered resource — and none of
         * them may throw.
         */
        @Test
        @DisplayName("never throw, whatever the JVM refuses")
        void degradeQuietly() {
            assertThat(SystemReport.clientVersion()).isNotBlank();
            assertThat(SystemReport.operatingSystem()).isNotBlank();
            assertThat(SystemReport.processor()).isNotBlank();
            assertThat(SystemReport.graphics()).isNotBlank();
            assertThat(SystemReport.memory()).isNotBlank();
            assertThat(SystemReport.runtime()).isNotBlank();
        }

        @Test
        @DisplayName("count cores from the JVM's own view of the machine")
        void cores() {
            assertThat(SystemReport.processor())
                    .startsWith(String.valueOf(Runtime.getRuntime().availableProcessors()));
        }

        /**
         * ⚠ This test was written asserting {@code SOFTWARE} — on the reasoning that a test JVM has
         * no toolkit, so there can be no pipeline to name — and it failed with
         * {@code OPENGL · HARDWARE}. {@code Platform.isSupported} does not merely <em>query</em> the
         * graphics pipeline, it <b>initialises</b> it. So the reading is real even here, and the
         * assertion is on the contract rather than on a value: whatever else it says, the line
         * always ends by telling the player whether they are accelerated.
         *
         * <p>The side effect is harmless in the client, where the panel is built on the FX thread
         * long after startup. It is worth knowing before calling this from anywhere colder.
         */
        @Test
        @DisplayName("always end by saying hardware or software, toolkit or not")
        void graphicsAlwaysStatesAcceleration() {
            assertThat(SystemReport.graphics()).matches(".*(HARDWARE|SOFTWARE)$");
        }
    }
}
