package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.bsky.BlueskyChat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ⚠ <b>THE REGRESSION: the DIRECT tab said "no account connected" for a connected account.</b>
 *
 * <h2>What happened</h2>
 *
 * Sign-in is a network round trip, so it cannot run on the FX thread. It was started on a virtual
 * thread and {@code DirectView.create} was called in the very next statement — where it asked
 * {@code chat.signedIn()} to decide what to render. In that instant the answer is <b>false</b>, not
 * sometimes but <em>every</em> time, so the pane rendered the not-connected message and never looked
 * again. A correct handle, a correct app password with DM access, and a tab insisting there was no
 * account.
 *
 * <h2>Why this test exists in this shape</h2>
 *
 * The rule lived inside a method that builds JavaFX nodes, so the only way to check it was to launch
 * the client and look — and looking is what found it, after it shipped. {@code DirectView.state} is
 * now a pure four-line function for the same reason {@code SecurityCenterView.latestOf} and
 * {@code Anchoring.horizontal} are, and this runs with no toolkit.
 *
 * <p>⚠ Verified against the broken version before being trusted: with {@code signedIn()} back in the
 * condition, {@link #aConnectedAccountIsNeverReportedAsMissing} fails.
 */
class DirectViewTest {

    /**
     * ⚠ The one that fails against the old code.
     *
     * <p>A client holding credentials but no session yet — which is <b>every</b> client at the
     * moment the pane is built — must read as CONNECTING, never as NO_ACCOUNT.
     */
    @Test
    @DisplayName("a configured account that has not signed in yet is CONNECTING, not missing")
    void aConnectedAccountIsNeverReportedAsMissing() {
        BlueskyChat chat = new BlueskyChat(null);
        chat.credentials("stoicswe.com", "app-password-not-real");

        assertThat(chat.signedIn())
                .as("the precondition: sign-in has not happened yet, and cannot have")
                .isFalse();
        assertThat(DirectView.state(chat))
                .as("this is the exact state the pane is built in, and calling it NO_ACCOUNT is the "
                        + "bug — the message is permanent because nothing re-checks it")
                .isEqualTo(DirectView.State.CONNECTING);
    }

    /** ⚠ Only a null client means there is genuinely nothing to show. */
    @Test
    @DisplayName("no client at all is the only NO_ACCOUNT")
    void noClientMeansNoAccount() {
        assertThat(DirectView.state(null)).isEqualTo(DirectView.State.NO_ACCOUNT);
    }

    /**
     * ⚠ And a client with no credentials is STILL not "no account" from the pane's point of view.
     *
     * <p>{@code EyeAndSickleClient.blueskyPane} is the only place that can answer whether an account
     * exists — it is the one that looked in the settings and the credential store — and it says so by
     * returning null. Anything else deciding the same question from a different signal is how the two
     * answers come apart.
     */
    @Test
    @DisplayName("the pane never second-guesses whether an account exists")
    void theQuestionIsAnsweredInOnePlace() {
        assertThat(DirectView.state(new BlueskyChat(null))).isEqualTo(DirectView.State.CONNECTING);
    }

    /**
     * The preview that rides in the notice stack.
     *
     * <p>⚠ A notice is <b>one line</b>. A message pasted with line breaks in it would make the stack
     * jump in height, and one long enough to be the message itself defeats the point of a preview.
     */
    @Nested
    @DisplayName("the notification preview")
    class Snippets {

        @Test
        @DisplayName("a short message is shown as it is")
        void shortIsUntouched() {
            assertThat(DirectView.snippet("on my way")).isEqualTo("on my way");
        }

        /** ⚠ Flattened, never left with the breaks in. */
        @Test
        @DisplayName("line breaks are flattened into one line")
        void flattened() {
            assertThat(DirectView.snippet("first\n\nsecond   third")).isEqualTo("first second third");
        }

        /** ⚠ Cut on a word boundary — a preview ending mid-word reads as corrupted text. */
        @Test
        @DisplayName("a long message is cut on a word boundary and marked")
        void longIsCut() {
            String long1 = "the vault estimate on that estate box was completely wrong and I want "
                    + "another look before anybody else goes near it";
            String cut = DirectView.snippet(long1);

            assertThat(cut).endsWith("…").doesNotContain("\n");
            assertThat(cut.length()).isLessThan(long1.length());

            String kept = cut.substring(0, cut.length() - 1);
            assertThat(long1).as("the preview is a real prefix of the message").startsWith(kept);
            // ⚠ THE actual word-boundary property: the message has a SPACE where the cut was made.
            // Asserting the preview does not end mid-word is not the same thing and is trivially
            // true of every cut, word boundary or not — the first version of this check did that
            // and would have passed against a blind substring.
            assertThat(long1.charAt(kept.length()))
                    .as("cut between words, not through one")
                    .isEqualTo(' ');
        }

        /**
         * ⚠ A deleted or attachment-only message has no text at all, and an empty preview is
         * indistinguishable from a broken notification.
         */
        @Test
        @DisplayName("an empty message still says something")
        void emptySaysSomething() {
            assertThat(DirectView.snippet("")).isNotBlank();
            assertThat(DirectView.snippet(null)).isNotBlank();
            assertThat(DirectView.snippet("   ")).isNotBlank();
        }
    }

    /**
     * The composer's growth rule.
     *
     * <h2>⚠ Only the fallback is checkable without a toolkit, and it is the half that matters here</h2>
     *
     * The wrapped measurement needs a real {@link javafx.scene.text.Font}, which needs the graphics
     * toolkit — and this repo keeps toolkit-dependent checks to a single file, verifying the rest by
     * render. What IS checkable is the contract when there is nothing to measure against: before the
     * first layout there is no width, and the answer must still be sane rather than zero or a crash.
     *
     * <p>⚠ It must <b>undercount</b> in that state, never overcount. A box one row short for a single
     * frame corrects itself on the next width change; one that opened six rows tall on an empty
     * conversation would look broken and stay that way.
     */
    @Nested
    @DisplayName("how tall the composer gets")
    class Rows {

        @Test
        @DisplayName("with no width to wrap into it counts hard line breaks")
        void fallsBackToHardLines() {
            assertThat(DirectView.Composer.rowsFor("one line", -1, null)).isEqualTo(1);
            assertThat(DirectView.Composer.rowsFor("one\ntwo", -1, null)).isEqualTo(2);
            assertThat(DirectView.Composer.rowsFor("one\ntwo\nthree", 0, null)).isEqualTo(3);
        }

        /** ⚠ An empty box is one row, never zero — a zero-row TextArea has no height at all. */
        @Test
        @DisplayName("an empty box is one row")
        void emptyIsOneRow() {
            assertThat(DirectView.Composer.rowsFor("", 400, null)).isEqualTo(1);
            assertThat(DirectView.Composer.rowsFor(null, 400, null)).isEqualTo(1);
        }
    }
}
