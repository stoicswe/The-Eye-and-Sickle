package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The capacity-upgrade log.
 *
 * <h2>⚠ Composed without a toolkit, which is why this is testable at all</h2>
 *
 * {@code compose} builds a list of strings and {@code start} is what needs a {@code Pulse}. Only the
 * second half touches the toolkit, so the half worth checking — <b>that the log tells the truth about
 * the rig</b> — can be asserted in an ordinary unit test. {@code NodeMenuTest} is the only file in
 * this client that starts JavaFX, and this one deliberately does not join it.
 */
class RebootSequenceTest {

    private static List<String> log(long from, long to, String name) {
        // ⚠ Reflection on the private constructor + compose, rather than play(), because play()
        // calls start() which needs a live Pulse. What is under test is the SCRIPT.
        try {
            var ctor = RebootSequence.class.getDeclaredConstructor(Runnable.class);
            ctor.setAccessible(true);
            RebootSequence sequence = ctor.newInstance((Runnable) () -> {});
            var compose = RebootSequence.class.getDeclaredMethod("compose", long.class, long.class, String.class);
            compose.setAccessible(true);
            compose.invoke(sequence, from, to, name);
            return sequence.scriptText();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the sequence's shape changed", e);
        }
    }

    /**
     * ⚠ <b>The numbers are the real ones.</b>
     *
     * <p>A log that said 32 while the rules moved the rig to 48 would be the client inventing state,
     * which pillar <b>C4</b> forbids everywhere else in this codebase. It is easy to get wrong here
     * precisely because the screen looks like set dressing.
     */
    @Test
    @DisplayName("the log states the ceilings the rules actually moved between")
    void theNumbersAreReal() {
        List<String> lines = log(32, 48, "Capacity Lattice — 48C");
        String all = String.join("\n", lines);

        assertThat(all).contains("32C -> 48C");
        assertThat(all).as("and the delta, so the player does not have to subtract").contains("+16C");
        assertThat(all).as("named, so the log says what was installed").contains("Capacity Lattice — 48C");
    }

    @Test
    @DisplayName("every rung produces a log with its own figures")
    void everyRung() {
        assertThat(String.join("\n", log(24, 32, "Capacity Board — 32C"))).contains("24C -> 32C", "+8C");
        assertThat(String.join("\n", log(48, 64, "Capacity Lattice — 64C"))).contains("48C -> 64C", "+16C");
    }

    /**
     * ⚠ The one warning in the log has to be there, because it is TRUE and expensive to discover.
     *
     * <p>The upgrade releases every allocation, so a rig that was mining stops mining. A player who
     * learns that from a flat income graph an hour later has been misled by omission.
     */
    @Test
    @DisplayName("it says the restart releases allocations")
    void itWarnsAboutAllocations() {
        String all = String.join("\n", log(24, 32, "Capacity Board — 32C"));
        assertThat(all).contains("restart");
        assertThat(all).contains("Releasing compute allocations");
    }

    /** A log nobody can read is not a log. Bounded above too — this is theatre, not a document. */
    @Test
    @DisplayName("the log is a readable length")
    void readableLength() {
        List<String> lines = log(24, 32, "Capacity Board — 32C");
        assertThat(lines).hasSizeBetween(12, 40);
    }
}
