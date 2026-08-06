package io.github.stoicswe.eyeandsickle.client.credentials;

import java.util.Optional;

/**
 * The operating system's own credential store — Keychain, Credential Manager, Secret Service.
 *
 * <h2>⚠ THERE IS NO PLAINTEXT FALLBACK, AND THERE MUST NEVER BE ONE</h2>
 *
 * The only secret this client will ever hold is a Bluesky <b>app password</b>: the player's own
 * credential, on their own account, which they typed in. If no store is available on the machine,
 * {@link #available()} answers false and the feature that wanted it is <b>off</b> — it does not
 * degrade to a file in the profile directory. A credential written to {@code settings.json} is a
 * credential in every backup, every screen share and every bug report, and the player would have no
 * way to know it happened. "Fail closed" is the whole design.
 *
 * <h2>⚠ THE SECRET GOES OVER stdin, NEVER IN argv</h2>
 *
 * Every implementation here drives a platform tool as a subprocess, and a process's arguments are
 * <b>world-readable</b> — {@code ps} on any Unix shows them to any user on the machine. macOS's own
 * {@code security} tool says so in its usage text: <i>"Use of the -p or -w options is insecure.
 * Specify -w as the last option to be prompted."</i> So the argument list carries the service and the
 * account, and the secret is written to the child's standard input. {@code SecretStoreTest} asserts
 * that no command line built here ever contains the secret, because this is the one property whose
 * failure is invisible in testing and permanent in production.
 *
 * <h2>⚠ This is the FIRST subprocess this client has ever spawned</h2>
 *
 * {@code SystemReport} records the previous position — "starts no process and opens no host file" —
 * and that austerity cost it real functionality: the ABOUT tab reports a core count instead of a CPU
 * name precisely because reading one would have meant {@code sysctl}. That rule is amended here,
 * narrowly and on explicit direction, because the alternative is worse: the platform stores are
 * reachable only through native APIs or their own command-line tools, and a hand-written FFM binding
 * to three different C APIs is far more code, far more risk, and untestable on any machine but the
 * one it was written on.
 *
 * <p>The narrowing that keeps the amendment honest: only this package spawns anything, the executable
 * is a fixed absolute-or-PATH name never composed from input, and nothing a player types ever reaches
 * an argument.
 *
 * <h2>⚠ Nothing here is ever logged</h2>
 *
 * The client captures its own log at {@code ALL} and offers it to the player to send in. An
 * implementation may log that a store was reached and what a command exited with; it may never log a
 * secret, and it may never log the command's <b>output</b>, because the output of a lookup <i>is</i>
 * the secret.
 */
public interface SecretStore {

    /**
     * The service name every item is filed under. One namespace for the whole client.
     *
     * <p>Reverse-DNS because that is what every platform store expects and what makes an item
     * recognisable in Keychain Access or {@code seahorse} — a player should be able to find and
     * delete this without the game's help.
     */
    String SERVICE = "io.github.stoicswe.eyeandsickle";

    /** A human-readable label, for the stores that show one. */
    String LABEL = "The Eye and Sickle";

    /** Whether this machine has a working store. False means the feature that wanted it is off. */
    boolean available();

    /**
     * Files a secret under {@code account}, replacing any previous one.
     *
     * @return whether it was stored. False means the caller must treat the secret as not kept —
     *     never as "probably fine".
     */
    boolean store(String account, String secret);

    /** Reads it back, or empty if there is none. Empty is not an error. */
    Optional<String> lookup(String account);

    /** Removes it. Returns true if something was there, false if nothing was. */
    boolean forget(String account);

    /** What to call this store on screen, so a refusal can name what is missing. */
    String describe();
}
