package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.bsky.BlueskyChat;
import org.junit.jupiter.api.DisplayName;
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
}
