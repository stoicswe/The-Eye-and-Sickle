package io.github.stoicswe.eyeandsickle.client.credentials;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one platform tool, feeding it a secret on standard input.
 *
 * <h2>⚠ THE SECRET IS WRITTEN TO stdin AND NEVER APPEARS IN THE ARGUMENT LIST</h2>
 *
 * A process's arguments are world-readable: {@code ps} shows them to any user on the machine, and on
 * a shared or managed box that is every user. So {@link #run} takes the argument list and the secret
 * as <b>separate parameters</b> and there is no overload that takes one string — the shape is what
 * stops somebody interpolating a password into a command line, because there is nowhere to put it.
 *
 * <h2>⚠ NOTHING FROM THIS CLASS IS EVER LOGGED VERBATIM</h2>
 *
 * The output of a lookup <em>is</em> the secret, and this client captures its own log at {@code ALL}
 * and invites the player to send it in. So the log line carries the executable and the exit code and
 * nothing else — never the arguments (which hold the account name), never stdin, and above all never
 * stdout.
 *
 * <h2>⚠ Bounded, because a credential prompt can block forever</h2>
 *
 * These tools prompt when they cannot proceed — a locked keychain, a missing agent — and a prompt
 * that expects a terminal, given a pipe, may simply wait. On the JavaFX thread that is a frozen
 * client with no error. Every run is capped and the child is destroyed on expiry.
 */
final class ToolRunner {

    private ToolRunner() {}

    private static final Logger LOG = Logger.getLogger(ToolRunner.class.getName());

    /** How long any one credential operation may take before the child is killed. */
    static final int TIMEOUT_SECONDS = 10;

    /**
     * The most output a tool may produce.
     *
     * <p>An app password is tens of characters. A cap turns "the tool printed something enormous"
     * into a refusal rather than into heap pressure on a UI thread.
     */
    static final int OUTPUT_LIMIT = 64 * 1024;

    /**
     * What a run produced.
     *
     * @param exitCode the child's status, or -1 if it never started or was killed
     * @param output everything it wrote to stdout, trailing newline stripped. ⚠ For a lookup this
     *     IS the secret — never log it, never put it in an exception message.
     */
    record Result(int exitCode, String output) {
        boolean ok() {
            return exitCode == 0;
        }
    }

    /**
     * Runs {@code command}, writes {@code stdin} to it, and collects stdout.
     *
     * @param command the executable and its arguments. ⚠ Must never contain a secret.
     * @param stdin what to feed it, or {@code null} to feed nothing. This is where a secret goes.
     */
    static Result run(List<String> command, String stdin) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            // ⚠ stderr is DISCARDED rather than merged into stdout. These tools write "item not
            // found" and permission chatter to stderr, and merging it would put that text into the
            // value a lookup returns — so a missing item would come back as a "secret" that is an
            // error message. The exit code is what carries the failure.
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();

            try (OutputStream in = process.getOutputStream()) {
                if (stdin != null) {
                    in.write(stdin.getBytes(StandardCharsets.UTF_8));
                    in.flush();
                }
                // ⚠ Closing stdin is what makes a prompting tool proceed. Left open, `security`
                // waits for more input and the timeout below is the only thing that ends it.
            }

            String output = read(process.getInputStream());
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.log(Level.WARNING, "credential tool timed out: {0}", command.getFirst());
                return new Result(-1, "");
            }
            int exit = process.exitValue();
            // ⚠ The executable and the exit code. NOT the arguments — those carry the account name —
            // and above all not the output, which for a lookup is the secret itself.
            LOG.log(Level.FINE, "credential tool {0} exited {1}", new Object[] {command.getFirst(), exit});
            return new Result(exit, output);
        } catch (IOException missing) {
            // The tool is not installed, which is a normal state on a machine with no credential
            // agent — not an error to surface. The caller reports the store as unavailable.
            LOG.log(Level.FINE, "credential tool unavailable: {0}", command.getFirst());
            return new Result(-1, "");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Result(-1, "");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** Reads stdout, bounded, as UTF-8. */
    private static String read(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int n;
        while ((n = stream.read(buffer)) > 0) {
            total += n;
            if (total > OUTPUT_LIMIT) {
                break;
            }
            out.write(buffer, 0, n);
        }
        String text = out.toString(StandardCharsets.UTF_8);
        // ⚠ Only the trailing newline the tool adds. NOT strip() — an app password may legitimately
        // begin or end with a space, and silently trimming one produces a credential that is wrong
        // in a way nobody can see.
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("\r")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
