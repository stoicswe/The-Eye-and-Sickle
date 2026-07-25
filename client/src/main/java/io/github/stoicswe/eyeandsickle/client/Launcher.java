package io.github.stoicswe.eyeandsickle.client;

/**
 * Plain entry point that delegates to the JavaFX application.
 *
 * <p>This class exists for one specific reason and it is not stylistic. When the main class of an
 * executable jar extends {@link javafx.application.Application}, the JVM launcher tries to start the
 * JavaFX toolkit <em>before</em> {@code main} runs, and it looks for the JavaFX runtime on the module
 * path. Running from the classpath — which is how a plain {@code java -jar} launch works — then fails
 * with the notoriously unhelpful "JavaFX runtime components are missing, and are required to run this
 * application".
 *
 * <p>A launcher that does <em>not</em> extend {@code Application} sidesteps that check entirely: the
 * toolkit starts from inside {@code main}, by which point the classpath is already established.
 *
 * @see EyeAndSickleClient
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        EyeAndSickleClient.main(args);
    }
}
