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

    public static void main(String[] args) {
        Application.launch(EyeAndSickleClient.class, args);
    }
}
