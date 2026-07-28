package io.github.stoicswe.eyeandsickle.client;

import javafx.application.Application;

/**
 * <strong>The only way to start this client.</strong>
 *
 * <h2>Why a second class instead of a {@code main} on the application</h2>
 *
 * When the main class of a launch <em>extends</em> {@link Application}, the JVM's own launcher tries
 * to start the JavaFX toolkit <em>before</em> {@code main} runs, and it looks for the JavaFX runtime
 * on the <b>module path</b>. Running from the classpath — which is what {@code java -jar} does, and
 * what an IDE does by default — then fails with the notoriously unhelpful:
 *
 * <pre>
 *   Error: JavaFX runtime components are missing, and are required to run this application
 * </pre>
 *
 * That message names the wrong problem. The runtime is present; the launcher simply refused to look
 * for it on the classpath. A launcher class that does <em>not</em> extend {@code Application}
 * sidesteps the check entirely, because by the time {@link #main} runs the classpath is already
 * established and {@link Application#launch} can find everything.
 *
 * <h2>Why {@code EyeAndSickleClient} has no {@code main} of its own</h2>
 *
 * It used to, and that was the bug. An IDE puts a run arrow beside every {@code main} it finds, so a
 * {@code main} on the application class is an invitation to launch the one way that cannot work —
 * and the error it produces sends people looking for a missing dependency that is not missing. The
 * method is gone rather than deprecated, so the wrong entry point is not merely discouraged, it does
 * not exist.
 *
 * <p>See also {@code .run/} in the repository root, which ships IntelliJ run configurations pointing
 * here, and the "Running from an IDE" note in {@code CLAUDE.md}.
 *
 * <h2>One flag differs by launch mode, and it is easy to get backwards</h2>
 *
 * JavaFX loads native libraries through {@code System::load}, which JDK 24+ reports as a restricted
 * call. Which module you grant depends on how the app was started:
 *
 * <ul>
 *   <li><b>module path</b> — what {@code mvn javafx:run} does — {@code --enable-native-access=javafx.graphics}
 *   <li><b>classpath</b> — what an IDE does by default — {@code --enable-native-access=ALL-UNNAMED}
 * </ul>
 *
 * Using the module form from the classpath prints {@code WARNING: Unknown module: javafx.graphics}
 * and grants nothing, so the warning it was meant to silence appears anyway. Verified on JDK 25 with
 * JavaFX 26.0.2. The two settings are correct for their own launch mode and must not be reconciled.
 */
public final class Launcher {

    private Launcher() {}

    /**
     * What the desktop should call this program.
     *
     * <p>Not the window title — {@code EyeAndSickleClient} sets that, and it is the game's name.
     * This is the <em>application</em> name: the one in the macOS menu bar, in a dock tooltip, and
     * in a Linux taskbar group. Without it the answer is "java", which is the name of the runtime
     * rather than of anything the player installed.
     */
    public static final String APP_NAME = "EAS uOS Client";

    public static void main(String[] args) {
        nameTheApplication();
        Application.launch(EyeAndSickleClient.class, args);
    }

    /**
     * Tells the desktop what this program is called, before any toolkit reads it.
     *
     * <h2>⚠ Before {@code Application.launch}, not after</h2>
     *
     * Both properties are read once, when the platform toolkit initialises. Setting them afterwards
     * is accepted, has no effect, and reports nothing — so the window comes up named "java" and
     * there is no error to chase. That is why this sits in {@code main} rather than in
     * {@code start}.
     *
     * <h2>Three platforms, three different answers, and only two of them are ours to give</h2>
     *
     * <table border="1">
     *   <caption>What names an application, per platform</caption>
     *   <tr><th>Platform</th><th>Reads</th><th>Set by</th></tr>
     *   <tr>
     *     <td><b>macOS</b></td>
     *     <td>Menu bar, dock tooltip, force-quit list</td>
     *     <td>{@code apple.awt.application.name} here, plus {@code -Xdock:name} in
     *         {@code client/pom.xml} and {@code .run/}</td>
     *   </tr>
     *   <tr>
     *     <td><b>Linux</b></td>
     *     <td>Taskbar label, window grouping, {@code WM_CLASS}</td>
     *     <td>{@code glass.appName} here, and the Stage title — which window managers fall back to
     *         and which several prefer outright</td>
     *   </tr>
     *   <tr>
     *     <td><b>Windows</b></td>
     *     <td>Taskbar button label</td>
     *     <td><b>The Stage title, and only the Stage title.</b></td>
     *   </tr>
     * </table>
     *
     * <h2>⚠ The Stage title is doing most of the work, and that is why it is the app name</h2>
     *
     * This deck is undecorated (§0), so the title is <em>invisible inside the game</em> — the only
     * thing that ever reads it is the OS window list. Windows in particular has no in-JVM way to
     * name an application at all: its taskbar groups by the executable and labels by the window
     * title. So the title is set to {@link #APP_NAME} rather than to the game's name, because a
     * title nobody can see is worth more as the label every platform agrees to read.
     *
     * <h2>⚠ None of this renames the PROCESS</h2>
     *
     * {@code ps}, Activity Monitor and the Windows Details tab will still say {@code java}, because
     * that is genuinely the executable running. Renaming it needs a native launcher — a
     * {@code jpackage} app image — and {@code jpackage} cannot cross-compile, so it would want one
     * build machine per platform. {@code client/pom.xml}'s closing comment covers why that is not
     * wired up. What this fixes is every place the desktop asks the <em>application</em> its name.
     */
    static void nameTheApplication() {
        // Both are harmless on platforms that ignore them, which is why neither is guarded by an
        // os.name check — a branch here would be three code paths to keep correct in exchange for
        // skipping two setProperty calls.
        System.setProperty("apple.awt.application.name", APP_NAME);
        System.setProperty("glass.appName", APP_NAME);
    }
}
