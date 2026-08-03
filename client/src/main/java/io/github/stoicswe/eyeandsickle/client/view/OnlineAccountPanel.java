package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.oauth.OauthException;
import io.github.stoicswe.eyeandsickle.client.oauth.SignInFlow;
import io.github.stoicswe.eyeandsickle.client.oauth.TokenStore;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * "Add an online account" — the sign-in panel on the login screen.
 *
 * <h2>⚠ THERE IS NO PASSWORD FIELD, AND THAT IS THE DESIGN</h2>
 *
 * AT Protocol OAuth exists so that a third-party application <strong>never sees the account
 * password</strong>. The player authenticates on their own provider's page, in their own browser, and
 * this client receives a token bound to a key it generated. A password box here would mean one of two
 * things, and both are worse:
 *
 * <ul>
 *   <li><strong>App passwords</strong> via {@code com.atproto.server.createSession} — a legacy path
 *       that grants App-Password-equivalent breadth over the real social account (post, delete,
 *       follow), which {@code docs/architecture/02-identity-and-auth.md} §3 forbids: this game takes
 *       identity and nothing else.
 *   <li><strong>The real password</strong> — teaching players to type their Bluesky credentials into
 *       a game is the exact shape of the attack the whole flow is built to make unnecessary. A player
 *       who learns the habit here will use it on a client that is not this one.
 * </ul>
 *
 * <p>So the panel says so, in the interface, rather than leaving the absence to be read as a missing
 * feature. ⚠ Two fields, not three: the <strong>handle</strong> is the username, and the
 * <strong>server</strong> is where to look if the handle cannot be resolved.
 *
 * <h2>⚠ This opens the player's browser, which this client has never done</h2>
 *
 * {@code CLAUDE.md} records that handles in the Credits screen are "printed, not clickable — opening
 * a browser would throw the player out of a full-screen game". That reasoning was about a
 * <em>gratuitous</em> browser open; here the redirect is the protocol, and there is no alternative
 * that does not involve a password box. The panel warns before it happens rather than after.
 */
public final class OnlineAccountPanel {

    private OnlineAccountPanel() {}

    /** What the panel needs from the client around it. */
    public interface Host {

        /** Runs the blocking flow off the FX thread and reports back on it. */
        void signIn(String handle, String server, Consumer<SignInFlow.Identity> onDone, Consumer<Exception> onError);

        /** The store in force, so the panel can state which one honestly. */
        TokenStore store();
    }

    /**
     * Builds the panel.
     *
     * @param host the surrounding client
     * @param onSignedIn called on the FX thread when a sign-in completes
     * @return the panel node
     */
    public static VBox build(Host host, Consumer<SignInFlow.Identity> onSignedIn) {
        TextField handle = new TextField();
        handle.setPromptText("alice.bsky.social");
        handle.setPrefColumnCount(22);

        TextField server = new TextField(SignInFlow.DEFAULT_PDS);
        server.setPromptText(SignInFlow.DEFAULT_PDS);
        server.setPrefColumnCount(18);

        Label status = new Label();
        status.setWrapText(true);
        status.setMaxWidth(440);
        status.getStyleClass().add("es-small");

        Button signIn = new Button(Views.t("ui.online-account.sign-in", "Sign in"));
        signIn.getStyleClass().add("es-focusable");

        Runnable start = () -> {
            signIn.setDisable(true);
            status.setText(Views.t(
                    "ui.online-account.opening-browser",
                    "Opening your browser. Sign in there, then come back — this window is waiting."));
            host.signIn(
                    handle.getText(),
                    server.getText(),
                    identity -> {
                        signIn.setDisable(false);
                        status.setText(Views.t("ui.online-account.signed-in-as", "Signed in as ")
                                + (identity.handle() != null ? identity.handle() : identity.did()));
                        onSignedIn.accept(identity);
                    },
                    error -> {
                        signIn.setDisable(false);
                        status.setText(explain(error));
                    });
        };
        signIn.setOnAction(event -> start.run());
        handle.setOnAction(event -> start.run());

        Label handleLabel = new Label(Views.t("ui.online-account.handle", "HANDLE"));
        handleLabel.getStyleClass().add("es-quiet");
        Label serverLabel = new Label(Views.t("ui.online-account.server", "SERVER"));
        serverLabel.getStyleClass().add("es-quiet");

        VBox handleBox = new VBox(4, handleLabel, handle);
        VBox serverBox = new VBox(4, serverLabel, server);
        HBox fields = new HBox(12, handleBox, serverBox);
        fields.setAlignment(Pos.BOTTOM_LEFT);

        Label noPassword = new Label(Views.t(
                "ui.online-account.no-password-here",
                "No password is asked for here, and never will be. You sign in on your provider's own "
                        + "page in your browser; this client only ever receives a token. Anything that "
                        + "asks for your Bluesky password outside your browser is not to be trusted — "
                        + "including a future version of this screen."));
        noPassword.setWrapText(true);
        noPassword.setMaxWidth(440);
        noPassword.getStyleClass().add("es-small");

        Label whatItIs = new Label(Views.t(
                "ui.online-account.identity-only",
                "The account proves who you are and nothing else. This game never reads or writes "
                        + "your posts, your follows or your feed, and asks for no permission to."));
        whatItIs.setWrapText(true);
        whatItIs.setMaxWidth(440);
        whatItIs.getStyleClass().add("es-small");

        VBox panel = new VBox(
                10,
                sectionLabel(Views.t("ui.online-account.add-an-online-account", "ADD AN ONLINE ACCOUNT")),
                fields,
                signIn,
                noPassword,
                whatItIs,
                storageNote(host.store()),
                status);
        panel.getStyleClass().addAll("es-files", "es-body-pad", "es-files-dialog");
        return panel;
    }

    /** Matches the login screen's own section headings. */
    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-quiet");
        return label;
    }

    /**
     * States where the credentials will live, in the interface.
     *
     * <p>⚠ Not decoration. The encrypted-file fallback keeps its key beside the data, so against a
     * local attacker it is obfuscation rather than protection — and a player told nothing cannot
     * account for that. {@code TokenStore.isPlatformSecured()} exists precisely so this line can be
     * true in both modes instead of claiming the stronger one always.
     */
    private static Label storageNote(TokenStore store) {
        String text = store.isPlatformSecured()
                ? Views.t("ui.online-account.stored-in", "Sign-in is kept in ") + store.describe() + "."
                : Views.t(
                        "ui.online-account.stored-in-file",
                        "No system keychain is available here, so the sign-in is kept in an encrypted "
                                + "file whose key sits beside it. That protects a backup or a copied "
                                + "profile; it does not protect against other software running as you.");
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(440);
        label.getStyleClass().add("es-small");
        return label;
    }

    /**
     * Turns a failure into something a player can act on.
     *
     * <p>⚠ The kinds are kept apart deliberately — {@code docs/client/01-visual-language.md} §9.4
     * requires that "refused" and "unreachable" never collapse into one message, and sign-in is where
     * a player is least able to guess which happened.
     */
    static String explain(Exception error) {
        if (error instanceof OauthException oauth) {
            return switch (oauth.kind()) {
                case DENIED -> Views.t("ui.online-account.err-denied", "Sign-in was refused: ") + oauth.getMessage();
                case UNAVAILABLE ->
                    Views.t(
                                    "ui.online-account.err-unavailable",
                                    "Could not reach the sign-in service. Nothing is wrong with your account: ")
                            + oauth.getMessage();
                case PROTOCOL ->
                    Views.t(
                                    "ui.online-account.err-protocol",
                                    "The server answered in a way this client cannot accept, so the sign-in was "
                                            + "stopped rather than trusted: ")
                            + oauth.getMessage();
                case STORAGE ->
                    Views.t(
                                    "ui.online-account.err-storage",
                                    "Your saved sign-in could not be read. Signing in again will replace it: ")
                            + oauth.getMessage();
                case ABANDONED ->
                    Views.t(
                            "ui.online-account.err-abandoned",
                            "Sign-in was not finished in the browser. You can start again whenever you like.");
            };
        }
        return Views.t("ui.online-account.err-unexpected", "Sign-in failed: ") + error.getMessage();
    }

    /** Runs {@code work} off the FX thread and delivers the outcome back on it. */
    public static <T> void offThread(
            java.util.concurrent.Callable<T> work, Consumer<T> onDone, Consumer<Exception> onError) {
        // ⚠ The flow blocks for as long as the player takes in the browser — up to five minutes. On
        // the FX thread that is a frozen window with no way to cancel, which reads as a crash.
        Thread.ofVirtual().start(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onDone.accept(result));
            } catch (Exception failure) {
                Platform.runLater(() -> onError.accept(failure));
            }
        });
    }
}
