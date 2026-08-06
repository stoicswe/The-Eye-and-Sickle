package io.github.stoicswe.eyeandsickle.client.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠ <b>No credential is ever written to the profile.</b>
 *
 * <h2>Why this is a test and not a comment</h2>
 *
 * {@code settings.json} is a plain file in the profile directory. A credential in it is a credential
 * in every backup, every screen share, every bug report and every "here is my config" paste — and the
 * player has no way to know it happened, because nothing on screen is different. The app password for
 * a connected Bluesky account goes to the platform's own store instead
 * ({@code client/credentials/SecretStore}); only the <b>handle</b>, which is public, is written here.
 *
 * <p>The failure this guards against is not malice, it is convenience: adding
 * {@code public String blueskyAppPassword} to {@code Settings} is a one-line change that makes the
 * feature work on a machine with no keyring, and it would pass every other test in this repository.
 *
 * <p>⚠ It scans names rather than values because a value is only wrong at run time, on somebody's
 * real machine, where nobody is looking. The name is wrong at compile time, here.
 */
class NoCredentialsInSettingsTest {

    /**
     * Words that name a secret.
     *
     * <p>⚠ {@code token} is on the list and is the one most likely to arrive innocently — an OAuth
     * access token or a service-auth JWT is exactly as sensitive as a password and reads as
     * infrastructure rather than as a credential.
     */
    private static final List<String> FORBIDDEN =
            List.of("password", "passwd", "secret", "token", "credential", "apikey", "privatekey");

    /**
     * ⚠ Named exceptions, and each one has to earn its place.
     *
     * <p>{@code stockApiKey} predates this test: it is the player's own AnonShare provider key, and
     * moving it into the credential store is worth doing but is a separate change with its own
     * migration — a quote-feed key is lower stakes than an account credential, because it buys
     * read-only access to public prices and nothing else. Recorded here rather than silently matched
     * by a loose pattern, so it is a decision somebody made and not a gap.
     */
    private static final List<String> KNOWN = List.of("stockapikey");

    @Test
    @DisplayName("no field in Settings is named like a credential")
    void noCredentialFieldsInSettings() {
        for (Field field : ClientProfile.Settings.class.getFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (KNOWN.contains(name)) {
                continue;
            }
            for (String forbidden : FORBIDDEN) {
                assertThat(name)
                        .as(
                                "ClientProfile.Settings.%s reads like a credential. settings.json is a "
                                        + "plain file — put it in client/credentials/SecretStore instead, or "
                                        + "add it to KNOWN with a reason if it genuinely is not one.",
                                field.getName())
                        .doesNotContain(forbidden);
            }
        }
    }

    /**
     * ⚠ The same rule for the per-character appearance block, which is also serialised to disk.
     *
     * <p>Two classes are written to the profile and a check on one of them is a check with a hole in
     * it.
     */
    @Test
    @DisplayName("nor in VisualSettings")
    void noCredentialFieldsInAppearance() {
        for (Field field : VisualSettings.class.getFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN) {
                assertThat(name)
                        .as("VisualSettings.%s reads like a credential", field.getName())
                        .doesNotContain(forbidden);
            }
        }
    }

    /**
     * ⚠ And the handle IS expected to be here — this is the positive half.
     *
     * <p>Without it, deleting {@code blueskyHandle} would leave the test above passing happily while
     * the feature silently lost the ability to tell whether an account was connected.
     */
    @Test
    @DisplayName("the handle is stored, because a handle is public and the password is not")
    void theHandleIsStored() throws NoSuchFieldException {
        assertThat(ClientProfile.Settings.class.getField("blueskyHandle").getType())
                .isEqualTo(String.class);
    }
}
