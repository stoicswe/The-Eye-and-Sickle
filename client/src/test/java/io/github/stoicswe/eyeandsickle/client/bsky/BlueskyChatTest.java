package io.github.stoicswe.eyeandsickle.client.bsky;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Bluesky chat wrapper.
 *
 * <h2>What is checkable without a network, and what is not</h2>
 *
 * Nothing here signs in — that needs somebody's real account, and a test that quietly opened a
 * connection to a developer's Bluesky would be the failure {@code DiscordIpcTest} already refuses by
 * never calling {@code connect}. What IS checkable is everything the wire shape forced, and those
 * are the parts that were surprising: a sender is only a DID, a deleted message has no text at all,
 * and requests are a separate bucket from accepted conversations.
 *
 * <p>⚠ Three of these were verified against the published lexicons rather than remembered, and each
 * is a place a plausible implementation is silently wrong — raw DIDs beside every line, blank lines
 * where deletions were, and every first approach invisible.
 */
class BlueskyChatTest {

    private static BlueskyChat.Member member(String did, String handle, String display) {
        return new BlueskyChat.Member(did, handle, display);
    }

    @Nested
    @DisplayName("conversations")
    class Convos {

        /** ⚠ A group is simply more than two members — the lexicon has no separate type for one. */
        @Test
        @DisplayName("a group is a convo with more than two members")
        void groups() {
            var pair = new BlueskyChat.Convo(
                    "c1", List.of(member("did:me", "me", ""), member("did:a", "a", "")), 0, "", "", false);
            var group = new BlueskyChat.Convo(
                    "c2",
                    List.of(member("did:me", "me", ""), member("did:a", "a", ""), member("did:b", "b", "")),
                    0,
                    "",
                    "",
                    false);

            assertThat(pair.group()).isFalse();
            assertThat(group.group()).isTrue();
        }

        /** The player is never listed as the other party in their own conversation. */
        @Test
        @DisplayName("the title names the other people, not you")
        void titleExcludesSelf() {
            var convo = new BlueskyChat.Convo(
                    "c1",
                    List.of(member("did:me", "me", "Me"), member("did:a", "kyrell", "Kyrell")),
                    0,
                    "",
                    "",
                    false);
            assertThat(convo.title("did:me")).isEqualTo("Kyrell");
        }

        @Test
        @DisplayName("a group title counts the others and names them")
        void groupTitle() {
            var convo = new BlueskyChat.Convo(
                    "c1",
                    List.of(
                            member("did:me", "me", "Me"),
                            member("did:a", "a", "Ana"),
                            member("did:b", "b", "Bo")),
                    0,
                    "",
                    "",
                    false);
            assertThat(convo.title("did:me")).contains("2 people").contains("Ana").contains("Bo");
        }

        /**
         * ⚠ A display name is optional on the wire and plenty of accounts have none.
         *
         * <p>Falling through to the handle with an {@code @} is what stops those rows rendering as
         * blanks — which in a conversation list is indistinguishable from a failed load.
         */
        @Test
        @DisplayName("an account with no display name falls back to its handle")
        void handleFallback() {
            assertThat(member("did:a", "kyrell.bsky.social", "").name()).isEqualTo("@kyrell.bsky.social");
            assertThat(member("did:a", "kyrell.bsky.social", null).name()).isEqualTo("@kyrell.bsky.social");
            assertThat(member("did:a", "kyrell.bsky.social", "Kyrell").name()).isEqualTo("Kyrell");
        }

        /** A convo with nobody else in it is degenerate but real; it must not render as blank. */
        @Test
        @DisplayName("a conversation with only you says so")
        void selfOnly() {
            var convo = new BlueskyChat.Convo("c1", List.of(member("did:me", "me", "Me")), 0, "", "", false);
            assertThat(convo.title("did:me")).isNotBlank();
        }
    }

    @Nested
    @DisplayName("⚠ the wire shapes that a plausible implementation gets wrong")
    class WireShape {

        private static String source() throws IOException {
            Path path = Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client/bsky/BlueskyChat.java");
            Assumptions.assumeTrue(Files.exists(path), "source not on this classpath layout");
            return Files.readString(path);
        }

        /**
         * ⚠ {@code chat.bsky.*} is proxied THROUGH the PDS and needs the header.
         *
         * <p>Without it the PDS answers "unknown method", which reads as the endpoint not existing
         * rather than as a missing header — and the obvious next move is to go looking for the wrong
         * hostname.
         */
        @Test
        @DisplayName("chat calls carry the atproto-proxy header, with the chat service DID")
        void proxyHeader() throws IOException {
            String source = source();
            assertThat(source).contains("atproto-proxy");
            assertThat(source).contains("did:web:api.bsky.chat#bsky_chat");
        }

        /**
         * ⚠ BOTH buckets. {@code status=request} is Bluesky's own consent model, and fetching only
         * the accepted conversations hides every first approach behind a setting nobody opened.
         */
        @Test
        @DisplayName("both accepted and requested conversations are fetched")
        void bothBuckets() throws IOException {
            String source = source();
            assertThat(source).contains("\"accepted\"");
            assertThat(source).contains("\"request\"");
        }

        /**
         * ⚠ Redirects are refused because the {@code Authorization} header would be replayed to
         * whatever host the redirect names. Same reasoning {@code HttpStockFeed} records for its key.
         */
        @Test
        @DisplayName("redirects are never followed, because the token would follow them")
        void noRedirects() throws IOException {
            assertThat(source()).contains("Redirect.NEVER");
        }

        /**
         * ⚠ THE OUTPUT IS SOMEBODY'S CONVERSATION. Nothing about a request may be logged beyond the
         * endpoint and the status — this client captures its own log and invites the player to send
         * it in.
         */
        /**
         * ⚠ <b>BOTH FILES, and widening this was not optional.</b>
         *
         * <p>{@code PdsDirectory} was split out of the sign-in path and logs on its own. A guard that
         * scanned only the file it was written against would have gone on passing while the new one
         * grew a log line carrying a URL — which is the failure mode this whole test exists to stop,
         * arriving through the door the refactor opened.
         */
        private static String sensitiveSources() throws IOException {
            return source() + "\n" + read("PdsDirectory.java");
        }

        private static String read(String name) throws IOException {
            Path path = Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client/bsky/" + name);
            Assumptions.assumeTrue(Files.exists(path), "source not on this classpath layout");
            return Files.readString(path);
        }

        @Test
        @DisplayName("no log line carries a body, a URL or a token")
        void nothingSensitiveIsLogged() throws IOException {
            for (String line : sensitiveSources().split("\n")) {
                String code = line.strip();
                if (code.startsWith("*") || code.startsWith("//") || !code.contains("LOG.")) {
                    continue;
                }
                assertThat(code)
                        .as("a log line that carries the conversation: %s", code)
                        .doesNotContain("body")
                        .doesNotContain("url")
                        .doesNotContain("accessJwt")
                        .doesNotContain("appPassword");
            }
        }

        /**
         * ⚠ The limit is clamped to the lexicon's own 1–100. Asking for more is an ERROR from the
         * API rather than a bigger page, so an unclamped caller gets nothing at all.
         */
        @Test
        @DisplayName("the page size is clamped to what the lexicon allows")
        void limitIsClamped() throws IOException {
            assertThat(source()).contains("Math.max(1, Math.min(100, limit))");
        }
    }

    @Nested
    @DisplayName("⚠ sign-in is asynchronous and its reason must reach the screen")
    class SignIn {

        /**
         * ⚠ <b>THE REGRESSION. The pane said "no account connected" for a connected account.</b>
         *
         * <p>Sign-in is a network round trip, so it ran on a virtual thread; the view was built in
         * the next statement and asked {@code signedIn()} in that same instant. It was false every
         * time — not sometimes, <em>every</em> time — so the DIRECT tab rendered the
         * not-connected message permanently, for a correct handle and a correct app password.
         *
         * <p>The fix is that only a <b>null client</b> means "no account", and the view calls
         * {@code ensureSignedIn} on its own background thread. This asserts the property that makes
         * that safe: a client that has been handed credentials is <b>not</b> signed in yet, and must
         * not be mistaken for one that has no account.
         */
        @Test
        @DisplayName("a client with credentials is not yet signed in, and that is not 'no account'")
        void credentialsAreNotASession() {
            var chat = new BlueskyChat(null);
            assertThat(chat.signedIn())
                    .as("nothing has talked to the network yet")
                    .isFalse();

            chat.credentials("someone.bsky.social", "app-pass-not-real");
            assertThat(chat.signedIn())
                    .as("handing over credentials is not a session — the view must NOT read this "
                            + "to decide whether an account is connected")
                    .isFalse();
        }

        /**
         * ⚠ With no credentials at all, {@code ensureSignedIn} refuses <b>without touching the
         * network</b> and says the one thing that is actually true.
         */
        @Test
        @DisplayName("no credentials refuses locally, and says so")
        void noCredentialsRefusesOffline() {
            var chat = new BlueskyChat(null);
            var failure = chat.ensureSignedIn();
            assertThat(failure).isPresent();
            assertThat(failure.orElseThrow()).contains("Settings");
        }

        /**
         * ⚠ Re-entering credentials CLEARS any existing session.
         *
         * <p>A player whose first app password lacked the direct-messages box fixes it and
         * reconnects; without this they would be left holding a token minted from the old one, and
         * the fix would appear not to work.
         */
        @Test
        @DisplayName("new credentials drop the old session")
        void reconnectingClearsTheSession() {
            var chat = new BlueskyChat(null);
            chat.credentials("a.bsky.social", "first");
            chat.credentials("a.bsky.social", "second");
            assertThat(chat.signedIn()).isFalse();
            assertThat(chat.selfDid()).isEmpty();
        }

        /**
         * ⚠ The DIAGNOSTIC that used to be discarded.
         *
         * <p>{@code ensureSignedIn} returns a sentence rather than a boolean precisely so the caller
         * cannot throw the reason away — which is what the previous wiring did, making the
         * {@code Bad token scope} message unreachable. A boolean return would let that happen again.
         */
        @Test
        @DisplayName("it reports a reason, not merely a failure")
        void itReturnsAReason() throws Exception {
            var method = BlueskyChat.class.getMethod("ensureSignedIn");
            assertThat(method.getReturnType())
                    .as("a boolean here is how the reason gets discarded")
                    .isEqualTo(java.util.Optional.class);
        }
    }

    @Nested
    @DisplayName("⚠ a refused chat call must not read as an empty inbox")
    class Diagnostics {

        /**
         * ⚠ <b>THE BUG: the scope error was handled on the wrong request.</b>
         *
         * <p>{@code com.atproto.server.createSession} succeeds with <b>any</b> valid app password,
         * whether or not it was created with direct-message access. The scope is checked when a
         * {@code chat.bsky.*} method is called — so a password without the box ticked signs in
         * perfectly and then fails every conversation fetch with {@code Bad token scope}. The
         * friendly message for that lived on the sign-in path, where it could never fire, and the
         * pane fell through to <i>"No conversations on this account, or Bluesky could not be
         * reached"</i>: one sentence covering both "you have no messages" and "your credential is
         * wrong", which is no help for either.
         */
        @Test
        @DisplayName("the scope failure is described on the CHAT path, where it actually happens")
        void scopeIsDescribedWhereItOccurs() throws Exception {
            var method = BlueskyChat.class.getDeclaredMethod(
                    "describeChatFailure", String.class, int.class, String.class);
            method.setAccessible(true);

            String scope = (String) method.invoke(null, "chat.bsky.convo.listConvos", 400, "BadTokenScope");
            assertThat(scope)
                    .as("it has to name the fix, because the symptom looks like an empty inbox")
                    .contains("direct-messages")
                    .contains("app password");

            String other = (String) method.invoke(null, "chat.bsky.convo.listConvos", 500, "");
            assertThat(other)
                    .as("and an unrelated failure must NOT claim the app password is wrong")
                    .doesNotContain("app password");
        }

        /** ⚠ A rate limit is temporary and must not send somebody to re-make a working credential. */
        @Test
        @DisplayName("429 says it will retry, not that the credential is bad")
        void rateLimitIsNotACredentialProblem() throws Exception {
            var method = BlueskyChat.class.getDeclaredMethod(
                    "describeChatFailure", String.class, int.class, String.class);
            method.setAccessible(true);
            String message = (String) method.invoke(null, "chat.bsky.convo.getLog", 429, "RateLimitExceeded");
            assertThat(message).contains("rate-limit").doesNotContain("app password");
        }

        /**
         * ⚠ A failure must be RECORDED, not merely logged.
         *
         * <p>A log line the player has to go and find is not a diagnostic — the pane is where they
         * already are. {@code lastError} is what carries it there, and a client with no error must
         * report none rather than a stale one.
         */
        @Test
        @DisplayName("a fresh client has no error to report")
        void noErrorWhenNothingFailed() {
            assertThat(new BlueskyChat(null).lastError()).isEmpty();
        }
    }

    @Nested
    @DisplayName("messages")
    class Messages {

        /**
         * ⚠ A {@code deletedMessageView} carries NO text field.
         *
         * <p>The record models that as a flag rather than as an empty string, because an empty line
         * in a transcript is indistinguishable from a message that failed to load — and the view
         * renders "(message deleted)" off this.
         */
        @Test
        @DisplayName("a deleted message is marked, not merely empty")
        void deletedIsMarked() {
            var deleted = new BlueskyChat.Message("m1", "did:a", "", Instant.EPOCH, true);
            var empty = new BlueskyChat.Message("m2", "did:a", "", Instant.EPOCH, false);

            assertThat(deleted.deleted()).isTrue();
            assertThat(empty.deleted())
                    .as("a genuinely empty message is not a deleted one")
                    .isFalse();
        }

        /**
         * ⚠ A sender is ONLY a DID on the wire — the name lives in the convo's members.
         *
         * <p>This pins the record's shape so nobody adds a {@code senderName} that the API never
         * sends and that would therefore always be blank.
         */
        @Test
        @DisplayName("a message carries a sender DID and nothing else about the sender")
        void senderIsADid() {
            var fields = java.util.Arrays.stream(BlueskyChat.Message.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList();
            assertThat(fields).containsExactly("id", "senderDid", "text", "sentAt", "deleted");
        }
    }

    /**
     * ⚠ <b>THE 501 REGRESSION — chat calls were being sent to the entryway.</b>
     *
     * <p>{@code bsky.social} accepts a sign-in for every Bluesky-hosted account and hosts none of
     * them. It answered {@code 501 MethodNotImplemented} to every {@code chat.bsky.*} call, which
     * reads as "this API does not exist" and meant "wrong machine". The host is per account and is
     * settled at sign-in; these assert that it actually moves.
     *
     * <p>⚠ Verified against the unfixed code before being trusted: with the host final and set from
     * the constructor, {@link #theHostMovesToTheAccountsOwnPds} fails.
     */
    @Nested
    @DisplayName("which server the messages are asked for")
    class Routing {

        private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

        /** The real document for the account this was found on. */
        private static final String SESSION_DID_DOC = """
                {
                  "id": "did:plc:zczf6tbnu4prqmdtj2hemgqu",
                  "service": [
                    {
                      "id": "#atproto_pds",
                      "type": "AtprotoPersonalDataServer",
                      "serviceEndpoint": "https://leccinum.us-west.host.bsky.network"
                    }
                  ]
                }
                """;

        /** ⚠ Before anyone knows whose account it is, the entryway is the only sane starting point. */
        @Test
        @DisplayName("it starts at the entryway")
        void itStartsAtTheEntryway() {
            assertThat(new BlueskyChat(null).chatHost()).isEqualTo(BlueskyChat.DEFAULT_PDS);
        }

        /** ⚠ The one that fails against the old code. */
        @Test
        @DisplayName("the session's DID document moves it to the account's own PDS")
        void theHostMovesToTheAccountsOwnPds() {
            var chat = new BlueskyChat(null);
            chat.adoptDidDocument(JSON.readTree(SESSION_DID_DOC));

            assertThat(chat.chatHost())
                    .as("asking the entryway for a conversation is the 501, and it is silent — "
                            + "sign-in succeeds and only the messages fail")
                    .isEqualTo("https://leccinum.us-west.host.bsky.network")
                    .isNotEqualTo(BlueskyChat.DEFAULT_PDS);
        }

        /**
         * ⚠ A session without a {@code didDoc} must leave a working host alone.
         *
         * <p>{@code didDoc} is optional in the lexicon, so clearing the host on its absence would
         * turn a perfectly good session into a stream of malformed URLs — a worse failure than the
         * one being fixed, and one that would only appear against certain servers.
         */
        @Test
        @DisplayName("a session with no DID document changes nothing")
        void noDocumentChangesNothing() {
            var chat = new BlueskyChat("https://pds.example");
            chat.adoptDidDocument(JSON.readTree("{}"));

            assertThat(chat.chatHost()).isEqualTo("https://pds.example");
        }
    }
}
