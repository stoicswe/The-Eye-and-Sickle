package io.github.stoicswe.eyeandsickle.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * What the client can truthfully say about itself and the machine it is running on.
 *
 * <p>Feeds the rig monitor's ABOUT tab. Every figure here is read from inside this JVM.
 *
 * <h2>⚠ Nothing in this class starts a process or opens a host file</h2>
 *
 * That is a deliberate line, and it is the reason two of these readouts are less specific than an
 * operating system's own About box. The client has never spawned a subprocess and reads exactly one
 * file it did not write ({@code view/AvatarChooser}, under three conditions spelled out there). A
 * system-information panel is a weak reason to change either fact, so:
 *
 * <ul>
 *   <li><b>The CPU is reported by core count and architecture, not by marketing name.</b> There is
 *       no JVM API for the brand string. Getting {@code Apple M4 Max} means {@code sysctl} on
 *       macOS, {@code /proc/cpuinfo} on Linux and a WMI query on Windows — three platform paths,
 *       one of which is a process spawn.
 *   <li><b>The GPU is reported as the render pipeline, not as the adapter.</b> Measured against
 *       JavaFX 26: {@code GraphicsPipeline.getDeviceDetails()} returns context pointers and no
 *       adapter name, {@code GLFactory} is package-private and prints its driver information to
 *       stdout rather than returning it, and nothing public in {@code javafx.*} exposes
 *       {@code GL_RENDERER}. What the pipeline <em>does</em> answer is the question a player with a
 *       stuttering deck actually has — whether they are on hardware or have silently fallen back to
 *       software rendering.
 * </ul>
 *
 * <p>If either is ever wanted literally, it is a new capability with a per-platform implementation
 * and a documented reason, not a quiet edit to this file.
 *
 * <h2>Everything here degrades rather than throws</h2>
 *
 * An About panel is the last place that should be able to take a window down, and half of what it
 * reads is optional at runtime: {@code com.sun.management} is a JDK extension, the prism pipeline is
 * an internal class reachable only on a classpath launch, and {@code build.properties} is absent if
 * someone runs the classes without Maven having filtered them. Each lookup answers {@link #UNKNOWN}
 * instead of failing.
 */
public final class SystemReport {

    /** What a readout says when the JVM will not tell us. Never a blank and never a guess. */
    public static final String UNKNOWN = "UNAVAILABLE";

    private static final String BUILD_PROPERTIES = "/io/github/stoicswe/eyeandsickle/client/build.properties";

    private SystemReport() {}

    /**
     * The rows of the ABOUT tab, in reading order, ready to print.
     *
     * <p>A {@link LinkedHashMap} because the order is part of the design — identity first, then the
     * machine — and a view that had to re-impose an order would be free to disagree with this one.
     */
    public static Map<String, String> rows() {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("CLIENT", "EAS uOS CLIENT " + clientVersion());
        rows.put("BUILD", architecture());
        rows.put("RUNTIME", runtime());
        rows.put("HOST OS", operatingSystem());
        rows.put("CPU", processor());
        rows.put("GPU", graphics());
        rows.put("MEMORY", memory());
        return rows;
    }

    // ── the client ───────────────────────────────────────────────────────────────────────────

    /**
     * The version Maven filtered into {@code build.properties}.
     *
     * <p>⚠ Not {@code Package.getImplementationVersion()}, which was the first attempt: it reads the
     * jar manifest, and the client runs from loose classes in an IDE, from a shaded jar, and from a
     * jpackage image — the manifest is present for one of those three. A filtered resource is
     * present for all three because it is a resource.
     */
    public static String clientVersion() {
        try (InputStream in = SystemReport.class.getResourceAsStream(BUILD_PROPERTIES)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").trim();
            // An unfiltered copy still holds the literal placeholder. Reporting that verbatim would
            // put "${client.version}" on screen, which reads as a bug in the game rather than as a
            // build that skipped a step.
            return version.isEmpty() || version.startsWith("${") ? UNKNOWN : version;
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    /**
     * The architecture this copy of the client was packaged for.
     *
     * <p>It is genuinely a <em>build</em> property and not merely a host one: the client ships one
     * jar per platform because JavaFX's natives are per-architecture, so a mismatch here is the
     * failure mode CI's {@code file}-on-{@code glass} check exists to catch. Reported in the JVM's
     * own vocabulary with the familiar name beside it, because {@code aarch64} and {@code amd64} are
     * what the machine says and neither is what a player would call it.
     */
    public static String architecture() {
        String arch = property("os.arch");
        return arch.equals(UNKNOWN) ? UNKNOWN : Ascii.upper(arch) + " (" + archName(arch) + ")";
    }

    static String archName(String arch) {
        return switch (arch.toLowerCase(Locale.ROOT)) {
            case "aarch64", "arm64" -> "ARM 64-BIT";
            case "amd64", "x86_64" -> "X86 64-BIT";
            case "x86", "i386", "i586", "i686" -> "X86 32-BIT";
            default -> "UNRECOGNISED";
        };
    }

    public static String runtime() {
        String java = property("java.vm.name") + " " + property("java.version");
        String fx = System.getProperty("javafx.runtime.version");
        return Ascii.upper(fx == null || fx.isBlank() ? java : java + " · JAVAFX " + fx);
    }

    // ── the host ─────────────────────────────────────────────────────────────────────────────

    public static String operatingSystem() {
        String name = property("os.name");
        String version = property("os.version");
        return Ascii.upper(version.equals(UNKNOWN) ? name : name + " " + version);
    }

    /**
     * Cores and architecture.
     *
     * <p>{@code availableProcessors()} is what the JVM has been <em>given</em>, which is the honest
     * number: inside a container or under an affinity mask it is smaller than the chip's, and the
     * smaller one is the one the client's own scheduling lives with.
     */
    public static String processor() {
        int cores = Runtime.getRuntime().availableProcessors();
        String arch = property("os.arch");
        return cores + (cores == 1 ? " CORE" : " CORES")
                + (arch.equals(UNKNOWN) ? "" : " · " + Ascii.upper(arch));
    }

    /**
     * The render pipeline, and whether it is hardware.
     *
     * <p>Two sources, deliberately in this order. {@link javafx.application.ConditionalFeature} is
     * public API and always answers; the prism pipeline class is internal and answers only on a
     * classpath launch, which is the one this client requires but not the one {@code javafx:run}
     * uses. So the supported half is computed first and the specific half enriches it when it can.
     *
     * <p>⚠ The reflection catches {@link Throwable}, not {@link Exception}. On a module-path launch
     * the failure is an {@code IllegalAccessError} — an Error, not an Exception — and catching the
     * narrower type would take the whole panel down on exactly the launch mode this is defending
     * against.
     */
    public static String graphics() {
        String acceleration = hardwareAccelerated() ? "HARDWARE" : "SOFTWARE";
        String pipeline = pipelineName();
        return pipeline == null ? acceleration : pipeline + " · " + acceleration;
    }

    private static boolean hardwareAccelerated() {
        try {
            return javafx.application.Platform.isSupported(
                    javafx.application.ConditionalFeature.SCENE3D);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String pipelineName() {
        try {
            Class<?> type = Class.forName("com.sun.prism.GraphicsPipeline");
            Object pipeline = type.getMethod("getPipeline").invoke(null);
            if (pipeline == null) {
                return null;
            }
            return switch (pipeline.getClass().getSimpleName()) {
                case "ES2Pipeline" -> "OPENGL";
                case "D3DPipeline" -> "DIRECT3D";
                case "MTLPipeline" -> "METAL";
                case "SWPipeline" -> "SOFTWARE RASTER";
                default -> null;
            };
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Physical memory installed in the machine.
     *
     * <p>⚠ Not {@code Runtime.maxMemory()}, which is the heap ceiling — a figure in the low hundreds
     * of megabytes on a 64 GB machine. Under a heading that says MEMORY on a panel that says HOST,
     * printing the heap would be a wrong answer rather than a partial one.
     *
     * <p>The cast is to {@code com.sun.management.OperatingSystemMXBean}, an exported interface, and
     * not to the implementation class: {@code jdk.management} does not export its internals, so
     * reflecting on the object's own class throws {@code InaccessibleObjectException}. Measured on
     * OpenJ9, where the implementation is IBM's.
     */
    public static String memory() {
        try {
            var bean = (com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return gigabytes(bean.getTotalMemorySize());
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /**
     * Bytes as whole gibibytes.
     *
     * <p>1024-based and labelled {@code GB}, matching what the machine's own memory sizing means —
     * RAM is manufactured in powers of two, so a decimal conversion would report a 64 GB machine as
     * 68.7 and be precisely wrong.
     */
    static String gigabytes(long bytes) {
        if (bytes <= 0) {
            return UNKNOWN;
        }
        double gb = bytes / 1073741824.0d;
        return gb < 1 ? String.format(Locale.ROOT, "%.1f GB", gb) : Math.round(gb) + " GB";
    }

    private static String property(String key) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    /** {@link Locale#ROOT} uppercase, for the same reason {@code ui/Ui} spells it out. */
    private static final class Ascii {
        private Ascii() {}

        static String upper(String text) {
            return text.toUpperCase(Locale.ROOT);
        }
    }
}
