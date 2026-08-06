package io.github.stoicswe.eyeandsickle.client.bsky;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the player's own Bluesky direct messages — conversations, groups and history.
 *
 * <h2>⚠ NOTHING FROM HERE IS EVER WRITTEN TO A SAVE</h2>
 *
 * These are messages other people wrote, on somebody else's service, about whatever they like.
 * {@code GameSave.messages} is the <em>engine's</em> inbox — every entry is authored by the rules and
 * one of them carries {@code offerItemType}, an entitlement to an item. Merging the two lists is how
 * text a stranger typed ends up somewhere trusted enough to grant something, which is <b>I14</b> at
 * the smallest possible scale. So this class holds an in-memory cache and no more: close the client
 * and it is gone, exactly as a mail client's window is.
 *
 * <h2>⚠ The API shape, verified against the lexicons rather than remembered</h2>
 *
 * <ul>
 *   <li>Auth is {@code com.atproto.server.createSession} against the player's PDS, with an
 *       <b>app password</b>. ⚠ It must have <b>DM access</b> ticked or every chat call returns
 *       {@code Bad token scope} — the one failure that looks like a wrong password and is not.
 *   <li>Chat calls go to the PDS with {@code atproto-proxy: did:web:api.bsky.chat#bsky_chat}.
 *   <li>{@code listConvos} takes {@code status} = {@code accepted} | {@code request}. ⚠ <b>Requests
 *       are a separate bucket</b> — that is Bluesky's own consent model, and a client that fetched
 *       only the accepted ones would silently hide everybody trying to reach the player for the
 *       first time.
 *   <li>A {@code messageView}'s {@code sender} is <b>only a DID</b>. The display name lives in the
 *       convo's {@code members}, so a name has to be resolved by matching — a naive implementation
 *       shows raw DIDs beside every line.
 *   <li>{@code deletedMessageView} has <b>no {@code text} field</b>. It must render as deleted rather
 *       than as an empty line, which is indistinguishable from a bug.
 * </ul>
 *
 * <h2>⚠ Never blocks the FX thread, and never logs a URL</h2>
 *
 * The same two rules {@code HttpStockFeed} already follows. Every method here is called from a
 * background thread by {@code DirectView}; the URL carries no secret but the <b>headers do</b>, so
 * nothing about a request is logged beyond its endpoint name and status.
 */
public final class BlueskyChat {

    private static final Logger LOG = Logger.getLogger(BlueskyChat.class.getName());

    /** The service DID the PDS proxies chat calls to. Verified against docs.bsky.app. */
    private static final String CHAT_PROXY = "did:web:api.bsky.chat#bsky_chat";

    /**
     * Where an account is looked up when nothing else is known.
     *
     * <h2>⚠ THIS IS THE ENTRYWAY, AND IT IS NOT A PDS — treating it as one broke every chat call</h2>
     *
     * {@code bsky.social} fronts account and session methods for every Bluesky-hosted account, so
     * {@code createSession} succeeds here for anybody and the client looked signed in. It does not
     * hold the repository and it does not pipethrough {@code chat.bsky.*}, so every conversation
     * fetch came back <b>501 MethodNotImplemented</b> — which reads as "this API does not exist" and
     * actually meant "you are asking the wrong machine". See {@link PdsDirectory}.
     *
     * <p>So this is the <b>bootstrap host</b> only. The host requests are sent to is
     * {@link #chatHost()}, which is resolved per account at sign-in.
     */
    public static final String DEFAULT_PDS = "https://bsky.social";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // ⚠ NEVER follow redirects. The Authorization header would be replayed to whatever host
            // the redirect names — the same reasoning HttpStockFeed records for its API key.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** The bootstrap host — where a handle is resolved, and the fall-back if resolution fails. */
    private final String entry;

    /**
     * The host every request actually goes to. Starts as {@link #entry} and is replaced at sign-in
     * with the account's real PDS.
     *
     * <p>⚠ <b>Volatile, and it changes.</b> The field it replaced was final, which is what made the
     * wrong-host bug unfixable without this: there was one host and it was decided before anyone knew
     * whose account it was.
     */
    private volatile String pds;

    private volatile String accessJwt = "";
    private volatile String selfDid = "";

    private final PdsDirectory directory;

    public BlueskyChat(String pds) {
        this.entry = pds == null || pds.isBlank() ? DEFAULT_PDS : pds.strip();
        this.pds = this.entry;
        this.directory = new PdsDirectory(this.entry);
    }

    /**
     * The host chat calls are sent to — the account's own PDS once signed in.
     *
     * <p>Exposed so a test can assert the routing without a network, which is the whole fence around
     * the 501.
     */
    public String chatHost() {
        return pds;
    }

    /**
     * One conversation. {@code members} excludes nobody — a group is simply a convo with more of them.
     *
     * @param lastSenderDid who wrote {@code lastMessage}. ⚠ Needed because {@code logCreateMessage}
     *     fires for <b>every</b> message in a conversation the account is in, including ones the
     *     player sent from another device — and notifying somebody about their own message, with a
     *     chime, is the most annoying thing this feature could do.
     */
    public record Convo(
            String id,
            List<Member> members,
            int unreadCount,
            String lastMessage,
            String lastSenderDid,
            boolean request) {

        /** ⚠ A group is more than two members. There is no separate type for one. */
        public boolean group() {
            return members.size() > 2;
        }

        /** What to call it: the other person, or "n people" for a group. */
        public String title(String selfDid) {
            List<Member> others =
                    members.stream().filter(m -> !m.did().equals(selfDid)).toList();
            if (others.isEmpty()) {
                return "(just you)";
            }
            if (others.size() == 1) {
                return others.getFirst().name();
            }
            return others.size() + " people  ·  "
                    + String.join(", ", others.stream().map(Member::name).toList());
        }
    }

    /** @param displayName may be blank — plenty of accounts have only a handle. */
    public record Member(String did, String handle, String displayName) {
        public String name() {
            return displayName == null || displayName.isBlank() ? "@" + handle : displayName;
        }
    }

    /**
     * @param text the message, or {@code ""} for a deleted one
     * @param deleted whether this is a {@code deletedMessageView} — which carries no text at all and
     *     must be shown as removed rather than as an empty line
     */
    public record Message(String id, String senderDid, String text, Instant sentAt, boolean deleted) {}

    /**
     * Signs in with an app password.
     *
     * <p>⚠ The password is used <b>once</b> and never held: what is kept is the access JWT, which is
     * what every later call carries. ⚠ A {@code Bad token scope} here is not a wrong password — it is
     * an app password created without the direct-messages box ticked, and saying so is the difference
     * between a player fixing it in a minute and giving up.
     *
     * @return empty on success, or a sentence to show the player
     */
    public Optional<String> signIn(String handle, String appPassword) {
        // ⚠ RESOLVED BEFORE THE PASSWORD IS BUILT INTO A REQUEST, not after. A player who hosts their
        // own server must not have their credential posted to Bluesky first and corrected afterwards.
        // Falls back to the entryway, which is right for every Bluesky-hosted account.
        pds = directory.resolve(handle).orElse(entry);
        try {
            String body = JSON.writeValueAsString(Map.of("identifier", handle, "password", appPassword));
            HttpRequest request = HttpRequest.newBuilder(URI.create(pds + "/xrpc/com.atproto.server.createSession"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            // ⚠ LOGGED AT INFO, and the handle is included deliberately. It is the player's own
            // public name in their own log, and "which account did it even try" is the first
            // question anybody debugging this asks. The PASSWORD is never near a log line.
            LOG.log(Level.INFO, "bluesky: signing in as {0} at {1}", new Object[] {handle, pds});
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // ⚠ The status and the error CODE only. The body can echo the identifier and, on
                // some errors, the request — and this client offers its log to the player to send in.
                String code = errorCode(response.body());
                LOG.log(
                        Level.WARNING,
                        "bluesky: sign-in refused, status {0} error {1}",
                        new Object[] {response.statusCode(), code.isBlank() ? "(none)" : code});
                return Optional.of(describeSignInFailure(response.statusCode(), code));
            }
            JsonNode node = JSON.readTree(response.body());
            accessJwt = node.path("accessJwt").asText("");
            selfDid = node.path("did").asText("");
            if (accessJwt.isEmpty()) {
                // ⚠ Worded around the field NAME. `nothingSensitiveIsLogged` bans the substring
                // outright, and it is blunt on purpose — the same call this repo made when
                // `DidDocument.ServiceEndpoint` was renamed for the "no *Service" rule rather
                // than the rule being given its first exception. A guard with one carve-out is a
                // guard somebody adds a second one to.
                LOG.warning("bluesky: sign-in returned 200 with no session token");
                return Optional.of("Bluesky accepted the sign-in but sent no token.");
            }
            adoptDidDocument(node.path("didDoc"));
            // ⚠ The DID is a public identifier and is what every later call is scoped to, so it is
            // the one thing worth recording — but the TOKEN never is, at any level. ⚠ The HOST is
            // logged beside it deliberately: "signed in, and here is the machine that will be asked
            // for the messages" is the one line that would have made the 501 obvious in a day
            // rather than in three rounds of guessing.
            LOG.log(Level.INFO, "bluesky: signed in as {0}, chat host {1}", new Object[] {selfDid, pds});
            return Optional.empty();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "bluesky: sign-in could not reach " + pds, e);
            return Optional.of("Could not reach Bluesky (" + e.getClass().getSimpleName() + ").");
        }
    }

    /**
     * Takes the PDS endpoint out of the DID document {@code createSession} hands back.
     *
     * <h2>⚠ A SECOND CORRECTION, AND IT IS FREE</h2>
     *
     * {@code com.atproto.server.createSession} returns an optional {@code didDoc} — verified against
     * the lexicon — and this is exactly how {@code @atproto/api} re-points its own agent after
     * logging in. It costs no request, so it runs even when {@link PdsDirectory} already answered:
     * the directory's answer can be stale by a migration, and this one comes from the server that
     * just authenticated the account.
     *
     * <p>⚠ <b>It is not a substitute for resolving first.</b> On its own it means the password has
     * already been sent to the entryway by the time the real host is known, which is fine for a
     * Bluesky-hosted account and wrong for anybody else. Belt <em>and</em> braces, in that order.
     *
     * <p>⚠ Absent or unusable leaves the current host alone rather than clearing it. A blank host
     * would turn a working session into a stream of malformed URLs.
     *
     * <p>⚠ Package-private rather than private <b>so the routing can be checked without a network</b>
     * — the same seam {@code SecurityCenterView.latestOf} and {@code Anchoring.horizontal} exist for,
     * and for the same reason: the rule that shipped wrong here was one that could only be reached by
     * signing in to a real account and looking.
     */
    void adoptDidDocument(JsonNode didDoc) {
        PdsDirectory.pdsFromDidDocument(didDoc).ifPresent(host -> {
            if (!host.equals(pds)) {
                LOG.log(Level.INFO, "bluesky: the session''s DID document moves the chat host to {0}", host);
            }
            pds = host;
        });
    }

    /** The {@code error} code out of an XRPC error body, or {@code ""}. Never the message. */
    private static String errorCode(String body) {
        try {
            return JSON.readTree(body).path("error").asText("");
        } catch (Exception notJson) {
            // A proxy or a gateway answered instead of the PDS. The status still means something.
            return "";
        }
    }

    private String describeSignInFailure(int status, String error) {
        if (status == 401) {
            return "Bluesky refused that handle and app password.";
        }
        if (status == 429) {
            return "Bluesky is rate-limiting this account. Try again shortly.";
        }
        return "Bluesky refused the sign-in (" + status
                + (error.isBlank() ? "" : ", " + error) + ").";
    }

    /**
     * What a failed CHAT call means, in a sentence.
     *
     * <h2>⚠ THE SCOPE ERROR ARRIVES HERE, NOT AT SIGN-IN — and it was handled in the wrong place</h2>
     *
     * {@code com.atproto.server.createSession} succeeds with <b>any</b> valid app password, whether or
     * not it was created with direct-message access. The scope is only checked when a
     * {@code chat.bsky.*} method is called, so an app password without the box ticked signs in
     * perfectly and then fails every conversation fetch with {@code Bad token scope}.
     *
     * <p>The friendly message for that was originally on the sign-in path, where it could never fire.
     * The visible symptom was the pane saying <i>"No conversations on this account, or Bluesky could
     * not be reached"</i> — which is the one sentence that describes both "you have no messages" and
     * "your credential is wrong", and is therefore no help at all.
     */
    private static String describeChatFailure(String endpoint, int status, String error) {
        if (error.toLowerCase(java.util.Locale.ROOT).contains("scope")) {
            return "That app password does not allow direct messages. Make a new one in your "
                    + "Bluesky settings with the direct-messages box ticked, then reconnect in "
                    + "Settings → Bluesky.";
        }
        if (status == 401) {
            return "Bluesky rejected the session. Reconnect in Settings → Bluesky.";
        }
        if (status == 429) {
            return "Bluesky is rate-limiting this account. It will retry on the next sync.";
        }
        if (status == 501) {
            // ⚠ THE SHAPE OF THE BUG THIS CLASS SHIPPED WITH, kept as a sentence because it is not
            // self-evident and the raw status points the reader at the wrong thing entirely. 501 is
            // what a server answers for a method it does not implement AND is not forwarding — so it
            // is never a scope, a parameter or a header problem. It means the request went to a
            // machine that does not host this account. Measured: bsky.social is the entryway, not a
            // PDS, and answers 501 to every chat call.
            return "That request went to a server that does not host this account — the direct-message "
                    + "service could not be reached through it. Reconnect in Settings → Bluesky to "
                    + "look up the right one.";
        }
        return "Bluesky refused " + endpoint + " (" + status
                + (error.isBlank() ? "" : ", " + error) + ").";
    }

    /**
     * Remembers the credentials so {@link #ensureSignedIn} can use them off the FX thread.
     *
     * <p>⚠ The app password is held only until the first successful sign-in and then dropped — what
     * survives is the access token. Setting new credentials clears any existing session, so a player
     * who reconnects with a corrected app password is not left holding a token from the old one.
     */
    public void credentials(String handle, String appPassword) {
        synchronized (this) {
            this.handle = handle == null ? "" : handle;
            this.appPassword = appPassword == null ? "" : appPassword;
            this.accessJwt = "";
            this.selfDid = "";
            this.lastError = "";
        }
    }

    /**
     * Signs in if it has not already, exactly once, and reports what went wrong if it did.
     *
     * <h2>⚠ THIS EXISTS BECAUSE THE PANE USED TO ASK {@code signedIn()} SYNCHRONOUSLY AND LOSE</h2>
     *
     * Sign-in is a network round trip, so it cannot happen on the FX thread; it was therefore started
     * on a virtual thread and the view was built immediately afterwards. The view asked
     * {@code signedIn()} in that same instant, got <b>false</b> every time, and rendered "no account
     * connected" permanently — for an account that was connected, with a credential that was correct.
     * The state was being populated asynchronously and read synchronously.
     *
     * <p>⚠ It also swallowed the reason. {@code signIn} returns a sentence — including the one that
     * distinguishes {@code Bad token scope} from a wrong password — and the caller discarded it, so
     * the single most useful diagnostic in this class could never reach a screen.
     *
     * <p>So the view calls THIS, on its own background thread, and gets either a session or a
     * sentence. Idempotent and synchronized: several panes opening at once produce one sign-in.
     *
     * @return empty once signed in, or a sentence to show the player
     */
    public synchronized Optional<String> ensureSignedIn() {
        if (signedIn()) {
            return Optional.empty();
        }
        if (handle.isBlank() || appPassword.isBlank()) {
            return Optional.of("No Bluesky account is connected. Settings → Bluesky.");
        }
        Optional<String> failure = signIn(handle, appPassword);
        if (failure.isEmpty()) {
            // ⚠ Dropped the moment it is no longer needed. The token is what every later call uses,
            // and a password kept past its one use is a second copy for no benefit.
            appPassword = "";
        }
        // ⚠ Only SET on failure, never cleared on success. A chat call recorded later — the scope
        // error, which is the common case — must not be wiped by a sign-in that had already
        // succeeded and is idempotently returning early on the next poll.
        if (failure.isPresent()) {
            lastError = failure.get();
        }
        return failure;
    }

    /** The last sign-in failure, for a pane rebuilt after one. */
    public String lastError() {
        return lastError;
    }

    private volatile String handle = "";
    private volatile String appPassword = "";
    private volatile String lastError = "";

    public String selfDid() {
        return selfDid;
    }

    public boolean signedIn() {
        return !accessJwt.isEmpty();
    }

    /**
     * Every conversation, accepted and requested.
     *
     * <p>⚠ Both buckets, in that order. {@code status=request} is Bluesky's consent model — people
     * who have messaged the player and are waiting to be allowed — and fetching only the accepted
     * ones would hide every first approach behind a setting the player never sees.
     */
    public List<Convo> conversations(int limit) {
        // ⚠ Cleared first: a stale error from a previous poll would otherwise be reported beside a
        // perfectly good refresh, and a player would chase a problem that had already gone away.
        lastError = "";
        List<Convo> out = new ArrayList<>();
        List<Convo> accepted = convosWithStatus("accepted", limit, false);
        List<Convo> requests = convosWithStatus("request", limit, true);
        out.addAll(accepted);
        out.addAll(requests);
        // ⚠ COUNTS, never contents. "0 accepted, 0 requests" beside a 200 is the line that
        // distinguishes an empty account from a refused call, and it was the missing sentence that
        // made this whole feature undebuggable from a log the player can actually send in.
        LOG.log(
                Level.INFO,
                "bluesky: {0} accepted conversation(s), {1} request(s)",
                new Object[] {accepted.size(), requests.size()});
        return out;
    }

    private List<Convo> convosWithStatus(String status, int limit, boolean request) {
        JsonNode root = get("chat.bsky.convo.listConvos", Map.of("limit", String.valueOf(clamp(limit)), "status", status));
        if (root == null) {
            return List.of();
        }
        List<Convo> convos = new ArrayList<>();
        for (JsonNode node : root.path("convos")) {
            List<Member> members = new ArrayList<>();
            for (JsonNode member : node.path("members")) {
                members.add(new Member(
                        member.path("did").asText(""),
                        member.path("handle").asText(""),
                        member.path("displayName").asText("")));
            }
            convos.add(new Convo(
                    node.path("id").asText(""),
                    List.copyOf(members),
                    node.path("unreadCount").asInt(0),
                    // ⚠ A deleted or system last-message has no `text`, so this is empty rather than
                    // absent — the list shows a blank preview instead of the word "null".
                    node.path("lastMessage").path("text").asText(""),
                    node.path("lastMessage").path("sender").path("did").asText(""),
                    request));
        }
        return convos;
    }

    /**
     * One conversation's history, oldest first.
     *
     * <p>⚠ The API returns newest first; this reverses it, because a conversation is read downwards
     * and a transcript that ran backwards would be unreadable for the one thing it is for.
     */
    public List<Message> history(String convoId, int limit) {
        JsonNode root = get(
                "chat.bsky.convo.getMessages",
                new LinkedHashMap<>(Map.of("convoId", convoId, "limit", String.valueOf(clamp(limit)))));
        if (root == null) {
            return List.of();
        }
        List<Message> messages = readMessages(root);
        LOG.log(Level.FINE, "bluesky: {0} message(s) in a conversation", messages.size());
        return List.copyOf(messages);
    }

    /**
     * Parses one page of messages, oldest first.
     *
     * <p>⚠ Shared by {@link #history} and {@link #fullHistory} rather than written twice — the two
     * differ only in how many pages they ask for, and a second copy of this loop is a second place
     * for the deleted-message rule to be forgotten.
     *
     * <p>⚠ The API returns newest first; this reverses it, because a conversation is read downwards
     * and a transcript that ran backwards would be unreadable for the one thing it is for.
     */
    private static List<Message> readMessages(JsonNode root) {
        List<Message> messages = new ArrayList<>();
        for (JsonNode node : root.path("messages")) {
            String type = node.path("$type").asText("");
            // ⚠ A deletedMessageView has NO `text` field at all. Rendering it as an empty line is
            // indistinguishable from a bug, so it is marked and the view says "deleted".
            boolean deleted = type.contains("deletedMessage");
            messages.add(new Message(
                    node.path("id").asText(""),
                    node.path("sender").path("did").asText(""),
                    deleted ? "" : node.path("text").asText(""),
                    parseInstant(node.path("sentAt").asText("")),
                    deleted));
        }
        java.util.Collections.reverse(messages);
        return messages;
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (Exception malformed) {
            // A timestamp this client cannot parse is not worth losing the message over.
            return Instant.EPOCH;
        }
    }

    /**
     * Everything since the last call — Bluesky's own sync mechanism.
     *
     * <h2>⚠ THIS IS WHAT POLLING IS FOR, AND RE-LISTING IS NOT</h2>
     *
     * {@code chat.bsky.convo.getLog} returns a cursor and only what has <em>changed</em> since it.
     * Bluesky's own documentation is explicit that clients synchronise state this way, and the reason
     * is cost: re-running {@code listConvos} plus a {@code getMessages} per conversation every minute
     * spends a large multiple of the player's own budget to discover, almost always, that nothing
     * happened. Bluesky publishes <b>5,000 points an hour</b> and warns that third-party clients
     * polling every few seconds consume it.
     *
     * <p>⚠ <b>It covers what the player SENT as well as what they received.</b> {@code logCreateMessage}
     * fires for every message in a conversation the account is in, whoever wrote it — so a reply typed
     * on a phone shows up here on the next poll. A design that only watched for incoming mail would
     * leave the desktop client permanently out of step with the player's own other devices.
     *
     * <p>⚠ <b>The cursor is the whole mechanism and it must be kept.</b> Passing none asks for the
     * log from the beginning, which on a busy account is a large answer and, worse, would report every
     * historical message as new — chiming once per message in the player's entire history.
     *
     * @return the ids of conversations that changed, or empty when nothing did
     */
    public java.util.Set<String> changedSince() {
        Map<String, String> params = new LinkedHashMap<>();
        if (!logCursor.isBlank()) {
            params.put("cursor", logCursor);
        }
        JsonNode root = get("chat.bsky.convo.getLog", params);
        if (root == null) {
            return java.util.Set.of();
        }
        // ⚠ Advanced even when nothing interesting came back. A cursor that only moved on
        // "interesting" events would replay the uninteresting ones forever.
        String next = root.path("cursor").asText("");
        if (!next.isBlank()) {
            logCursor = next;
        }
        int entries = root.path("logs").size();
        java.util.Set<String> touched = new java.util.LinkedHashSet<>();
        for (JsonNode entry : root.path("logs")) {
            String type = entry.path("$type").asText("");
            // ⚠ Only the two that change what a transcript SHOWS. Reads, reactions, mutes and the
            // twenty-odd membership events are real log entries and none of them is a new message —
            // treating any of them as one would chime for somebody else opening a conversation.
            if (type.endsWith("#logCreateMessage") || type.endsWith("#logDeleteMessage")) {
                String convoId = entry.path("convoId").asText("");
                if (!convoId.isBlank()) {
                    touched.add(convoId);
                }
            }
        }
        // ⚠ Both numbers. "12 entries, 0 message changes" is a poll that correctly did nothing, and
        // is a very different line from "0 entries" — which means the cursor is not advancing.
        LOG.log(
                Level.FINE,
                "bluesky: getLog {0} entr(ies), {1} conversation(s) with new or deleted messages",
                new Object[] {entries, touched.size()});
        return touched;
    }

    /**
     * Whether a first sync has established a cursor.
     *
     * <p>⚠ The first {@code getLog} call reports the log from its beginning, so everything it returns
     * is <b>history rather than news</b>. The caller uses this to fill the transcript without playing
     * a chime per message in the player's entire correspondence.
     */
    public boolean primed() {
        return !logCursor.isBlank();
    }

    private volatile String logCursor = "";

    /**
     * A conversation's full history, paging until the server stops offering a cursor.
     *
     * <p>⚠ Bounded by {@link #HISTORY_PAGES}. "Sync all" on an account with years of messages is
     * otherwise an unbounded loop against somebody else's rate limit, and the honest failure is to
     * fetch a great deal and stop rather than to hang. Ten pages is a thousand messages.
     */
    public List<Message> fullHistory(String convoId) {
        List<Message> all = new ArrayList<>();
        String cursor = "";
        for (int page = 0; page < HISTORY_PAGES; page++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("convoId", convoId);
            params.put("limit", "100");
            if (!cursor.isBlank()) {
                params.put("cursor", cursor);
            }
            JsonNode root = get("chat.bsky.convo.getMessages", params);
            if (root == null) {
                break;
            }
            List<Message> page1 = readMessages(root);
            if (page1.isEmpty()) {
                break;
            }
            // ⚠ Each page arrives newest-first, and pages walk BACKWARDS in time — so the whole
            // history is assembled by prepending each older page, not by appending it.
            all.addAll(0, page1);
            cursor = root.path("cursor").asText("");
            if (cursor.isBlank()) {
                break;
            }
        }
        return List.copyOf(all);
    }

    /** How many pages of a conversation "sync all" will walk. See {@link #fullHistory}. */
    private static final int HISTORY_PAGES = 10;

    /** The lexicon's own bound. Asking for more is an error rather than a bigger page. */
    private static int clamp(int limit) {
        return Math.max(1, Math.min(100, limit));
    }

    /**
     * Sends a message to a conversation.
     *
     * <h2>⚠ THE ONLY THING THIS CLIENT WRITES TO SOMEBODY ELSE'S SERVICE</h2>
     *
     * Everything else here reads. This posts text the player typed to an account they chose, and
     * <b>nothing of the game's goes with it</b> — no handle, DID, balance, standing, item, machine
     * name or address. The request carries a conversation id and the message, and that is all. The
     * exhaustive outbound list in {@code docs/client/02} §2.9a stays true because what crosses the
     * wire is what the player wrote and nothing the game knows.
     *
     * <h2>⚠ THE LIMITS ARE GRAPHEMES AND BYTES, NEITHER OF WHICH IS {@code String.length()}</h2>
     *
     * The lexicon caps {@code text} at {@code maxLength} 10000 and {@code maxGraphemes} 1000, and in
     * AT Protocol those mean <b>UTF-8 bytes</b> and <b>grapheme clusters</b> respectively. Checking
     * {@code length()} passes a message the server then rejects: a string of family emoji is a handful
     * of graphemes and a great many chars and bytes, and one of accented Latin is fewer bytes than it
     * looks. Refusing here, before the round trip, is the difference between a message that will not
     * send and a message that vanishes with an unexplained 400.
     *
     * @return the message as the server recorded it, or empty with {@link #lastError()} set
     */
    public Optional<Message> send(String convoId, String text) {
        if (convoId == null || convoId.isBlank() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        // ⚠ Trailing whitespace is stripped, never the whole message reshaped. A player's own line
        // breaks and spacing are theirs.
        String message = text.strip();
        if (!withinLimits(message)) {
            lastError = "That message is too long for Bluesky.";
            return Optional.empty();
        }
        JsonNode root = post(
                "chat.bsky.convo.sendMessage",
                Map.of("convoId", convoId, "message", Map.of("text", message)));
        if (root == null) {
            // ⚠ `post` has already recorded WHY in lastError. Returning empty without it would put
            // the pane back to a silent failure, which is the defect this class keeps re-learning.
            return Optional.empty();
        }
        // ⚠ The RETURNED view is what gets rendered, never the string that was typed. The server
        // assigns the id and the timestamp, and a transcript built from the local copy would show a
        // message that does not match the one everybody else received.
        Message sent = new Message(
                root.path("id").asText(""),
                root.path("sender").path("did").asText(selfDid),
                root.path("text").asText(message),
                parseInstant(root.path("sentAt").asText("")),
                false);
        LOG.log(Level.INFO, "bluesky: a message was sent to a conversation");
        return Optional.of(sent);
    }

    /**
     * Whether a message fits what the lexicon will accept.
     *
     * <p>⚠ Package-private so it can be checked without a network — the grapheme rule is the kind
     * that is silently wrong for years because nobody on the team types emoji into a test.
     */
    static boolean withinLimits(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            return false;
        }
        // ⚠ CHARACTER instance, not word or sentence — a grapheme cluster is what the spec counts,
        // and it is what a person would call "one character" even when it is several code points.
        java.text.BreakIterator graphemes = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
        graphemes.setText(text);
        int count = 0;
        while (graphemes.next() != java.text.BreakIterator.DONE) {
            count++;
            if (count > MAX_MESSAGE_GRAPHEMES) {
                return false;
            }
        }
        return true;
    }

    /** {@code maxLength} on an AT Protocol string is UTF-8 bytes. */
    static final int MAX_MESSAGE_BYTES = 10_000;

    /** And {@code maxGraphemes} is grapheme clusters, which is the tighter of the two in practice. */
    static final int MAX_MESSAGE_GRAPHEMES = 1_000;

    /**
     * One authenticated, proxied POST.
     *
     * <p>⚠ Identical routing to {@link #get}: the account's own PDS plus {@code atproto-proxy}. A
     * procedure sent to the entryway fails exactly as a query does, with the same 501 that says
     * nothing about what is wrong.
     *
     * @return the parsed reply, or {@code null} with {@link #lastError()} set
     */
    private JsonNode post(String endpoint, Map<String, Object> payload) {
        if (!signedIn()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(pds + "/xrpc/" + endpoint))
                    .header("Authorization", "Bearer " + accessJwt)
                    .header("atproto-proxy", CHAT_PROXY)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String code = errorCode(response.body());
                LOG.log(
                        Level.WARNING,
                        "bluesky: {0} returned {1} error {2}",
                        new Object[] {endpoint, response.statusCode(), code.isBlank() ? "(none)" : code});
                lastError = describeChatFailure(endpoint, response.statusCode(), code);
                return null;
            }
            LOG.log(Level.FINE, "bluesky: {0} ok", endpoint);
            return JSON.readTree(response.body());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "bluesky: " + endpoint + " could not be reached", e);
            lastError = "Could not reach Bluesky (" + e.getClass().getSimpleName() + ").";
            return null;
        }
    }

    /**
     * One authenticated, proxied GET.
     *
     * <p>⚠ The {@code atproto-proxy} header is what routes a {@code chat.bsky.*} call from the
     * player's PDS to the chat service. Without it the PDS answers "unknown method" — which reads as
     * the endpoint not existing rather than as a missing header.
     *
     * <p>⚠ <b>And the header is only half of it: it has to be the right host.</b> The same 501 comes
     * back, with the header present and correct, if the request goes to the entryway instead of the
     * account's own PDS — because the entryway has no chat method to forward. {@link #chatHost()} is
     * resolved per account at sign-in and this is the one place it is used. See {@link PdsDirectory}.
     *
     * @return the parsed body, or {@code null} on any failure. Callers show what they have.
     */
    private JsonNode get(String endpoint, Map<String, String> params) {
        if (!signedIn()) {
            return null;
        }
        StringBuilder url = new StringBuilder(pds).append("/xrpc/").append(endpoint);
        char sep = '?';
        for (Map.Entry<String, String> param : params.entrySet()) {
            url.append(sep)
                    .append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8));
            sep = '&';
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .header("Authorization", "Bearer " + accessJwt)
                    .header("atproto-proxy", CHAT_PROXY)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // ⚠ The ENDPOINT, the status and the error CODE. Never the URL — it carries the convo
                // id — and never the body, which is the conversation.
                String code = errorCode(response.body());
                LOG.log(
                        Level.WARNING,
                        "bluesky: {0} returned {1} error {2}",
                        new Object[] {endpoint, response.statusCode(), code.isBlank() ? "(none)" : code});
                // ⚠ RECORDED so the pane can say WHY. Returning null and letting the caller render
                // "no conversations" is what made a scope failure indistinguishable from an empty
                // inbox — the same sentence for two completely different problems.
                lastError = describeChatFailure(endpoint, response.statusCode(), code);
                return null;
            }
            LOG.log(Level.FINE, "bluesky: {0} ok", endpoint);
            return JSON.readTree(response.body());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "bluesky: " + endpoint + " could not be reached", e);
            lastError = "Could not reach Bluesky (" + e.getClass().getSimpleName() + ").";
            return null;
        }
    }
}
