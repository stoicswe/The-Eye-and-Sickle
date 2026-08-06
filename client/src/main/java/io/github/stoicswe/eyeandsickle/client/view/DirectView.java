package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.bsky.BlueskyChat;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * DIRECT — the player's own Bluesky conversations, inside COMS.
 *
 * <h2>⚠ THESE ARE NOT THE ENGINE'S MESSAGES AND SHARE NO TYPE WITH THEM</h2>
 *
 * Everything here was written by other people on somebody else's service. The INBOX tab beside it is
 * engine-authored, lives in the save, and carries entitlements. Nothing on this tab is ever written
 * to a save — the cache dies with the window, exactly as a mail client's does — because a list whose
 * entries can grant items must never accept text a stranger typed. That is <b>I14</b> at its
 * smallest scale, and the tab strip is the seam that makes it visible.
 *
 * <h2>⚠ NEVER ON THE FX THREAD</h2>
 *
 * Every fetch runs on a virtual thread and hands its result back through {@code Platform.runLater}.
 * Reading a conversation history is a network round trip to somebody else's server; doing it inline
 * freezes the whole deck for as long as their PDS takes to answer, which is not a number this client
 * gets to bound. Same rule {@code HttpStockFeed} already follows.
 *
 * <h2>⚠ Requests are shown, and shown as requests</h2>
 *
 * Bluesky splits conversations into <b>accepted</b> and <b>request</b>, which is its own consent
 * model — people who have written to the player and are waiting to be allowed. A client that listed
 * only the accepted ones would hide every first approach behind a setting the player never opened,
 * so both are here and the pending ones say so.
 */
public final class DirectView {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(DirectView.class.getName());

    private DirectView() {}

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    /** How many conversations and how much history to pull. The lexicon's own ceiling is 100. */
    private static final int CONVO_LIMIT = 50;

    private static final int HISTORY_LIMIT = 100;

    /**
     * @param chat a signed-in client, or {@code null} when no account is connected
     * @param handle the connected handle, for the header
     */
    public static Region create(BlueskyChat chat, String handle, int syncSeconds) {
        VBox convoList = new VBox(1);
        VBox transcript = new VBox(UiTokens.SPACE_2);
        transcript.getStyleClass().add("es-body-pad");

        Label status = Views.secondary(Views.t("ui.direct.loading", "Syncing conversations…"));
        transcript.getChildren().add(status);

        Region listPane = Views.scrollable(convoList);
        listPane.setMinWidth(190);
        listPane.setPrefWidth(215);
        listPane.setMaxWidth(240);

        Region readerPane = Views.scrollable(transcript);
        // ⚠ Zero, or the row demands both columns before distributing anything and the transcript
        // runs off the panel — the same failure CommsView and AnonShareView both record.
        readerPane.setMinWidth(0);

        HBox split = new HBox(listPane, new Separator(Orientation.VERTICAL), readerPane);
        HBox.setHgrow(readerPane, Priority.ALWAYS);
        split.setFillHeight(true);

        // ⚠ A flag rather than a method on the chat client: "is a sync running" is this pane's own
        // state, and the client is shared with whatever else asks it questions.
        boolean[] syncing = {true};

        VBox root = new VBox();
        root.getChildren().addAll(attribution(() -> syncing[0], root), split);
        VBox.setVgrow(split, Priority.ALWAYS);

        if (state(chat) == State.NO_ACCOUNT) {
            transcript.getChildren()
                    .setAll(Views.wrapped(Views.t(
                            "ui.direct.not-connected",
                            "No Bluesky account is connected. Settings → Bluesky.")));
            return root;
        }

        // ⚠ A VIRTUAL thread, and the result is handed back through runLater. Fetching inline would
        // freeze the deck for however long somebody else's PDS takes — a number this client does not
        // get to bound.
        Thread.ofVirtual().start(() -> {
            // ⚠ SIGN IN FIRST, ON THIS THREAD, AND SHOW WHAT WENT WRONG. `ensureSignedIn` is
            // idempotent and returns the reason — including the one that distinguishes an app
            // password without the direct-messages box from a wrong password. The previous version
            // started sign-in elsewhere and discarded that sentence, so the most useful diagnostic
            // in the whole feature could never reach a screen.
            LOG.info("comms: DIRECT opening, signing in");
            var failure = chat.ensureSignedIn();
            if (failure.isPresent()) {
                LOG.log(java.util.logging.Level.WARNING, "comms: DIRECT cannot sign in: {0}", failure.get());
                Platform.runLater(() -> transcript.getChildren().setAll(Views.wrapped(failure.get())));
                return;
            }
            List<BlueskyChat.Convo> convos = chat.conversations(CONVO_LIMIT);
            // ⚠ The FIRST getLog establishes the cursor and is therefore history, not news. Calling
            // it here — before any polling — is what stops the very first poll reporting the
            // player's entire correspondence as new and chiming once per message.
            chat.changedSince();
            Platform.runLater(() -> {
                syncing[0] = false;
                if (convos.isEmpty()) {
                    // ⚠ THE REASON, when there is one. "No conversations on this account, or Bluesky
                    // could not be reached" describes an empty inbox AND a refused credential in one
                    // sentence, which is no help for either — and the refused credential is the case
                    // somebody can actually fix. An app password without direct-message access signs
                    // in perfectly and fails here, so this is exactly where that has to be said.
                    String why = chat.lastError();
                    transcript.getChildren()
                            .setAll(Views.wrapped(
                                    why.isBlank()
                                            ? Views.t(
                                                    "ui.direct.empty",
                                                    "No conversations on this account yet.")
                                            : why));
                    return;
                }
                transcript.getChildren().setAll(Views.secondary(Views.t("ui.direct.pick", "Pick a conversation.")));
                String[] selected = {""};
                Runnable[] paint = new Runnable[1];
                paint[0] = () -> {
                    convoList.getChildren().clear();
                    for (BlueskyChat.Convo convo : convos) {
                        convoList
                                .getChildren()
                                .add(row(chat, convo, selected, paint, transcript));
                    }
                };
                paint[0].run();
                startPolling(chat, syncing, convos, selected, paint, convoList, transcript, syncSeconds);
            });
        });
        return root;
    }

    /**
     * Asks Bluesky for changes on the player's own cadence, forever, until the pane is closed.
     *
     * <h2>⚠ {@code getLog}, NOT a re-list — this is what the endpoint is for</h2>
     *
     * It returns a cursor and only what has <em>changed</em> since it. Re-running {@code listConvos}
     * plus a {@code getMessages} per conversation every minute would spend a large multiple of the
     * player's own allowance to discover, almost always, that nothing happened. Bluesky publishes
     * <b>5,000 points an hour</b> and warns that clients polling every few seconds consume it.
     *
     * <h2>⚠ It picks up what the player SENT, too</h2>
     *
     * {@code logCreateMessage} fires for every message in a conversation the account is in, whoever
     * wrote it — so a reply typed on a phone appears here on the next poll. A design that watched only
     * for incoming mail would leave this client permanently out of step with the player's own devices.
     *
     * <h2>⚠ `Pulse.every` — DATA, not `animate`</h2>
     *
     * Under Reduce motion a decorative subscription never fires, so an {@code animate} poll would
     * mean a player who uses that setting never receives another message — the accessibility path
     * getting the broken behaviour, which is the failure the market carousel already records.
     */
    private static void startPolling(
            BlueskyChat chat,
            boolean[] syncing,
            List<BlueskyChat.Convo> convos,
            String[] selected,
            Runnable[] paint,
            VBox convoList,
            VBox transcript,
            int syncSeconds) {
        int period = Math.max(MIN_SYNC_SECONDS, syncSeconds) * 1000;
        AutoCloseable clock = io.github.stoicswe.eyeandsickle.client.ui.Pulse.shared()
                .every(period, () -> {
                    if (syncing[0]) {
                        // ⚠ Never overlap. A slow answer must not have a second poll started on top
                        // of it — two in flight double the cost and can deliver out of order.
                        return;
                    }
                    syncing[0] = true;
                    Thread.ofVirtual().start(() -> {
                        // ⚠ At FINE: this fires every minute forever, and an INFO line per poll would
                        // bury everything else in the client log within an hour.
                        LOG.fine("comms: polling Bluesky for changes");
                        var touched = chat.changedSince();
                        List<BlueskyChat.Convo> fresh =
                                touched.isEmpty() ? List.of() : chat.conversations(CONVO_LIMIT);
                        Platform.runLater(() -> {
                            syncing[0] = false;
                            if (touched.isEmpty()) {
                                return;
                            }
                            // ⚠ The chime rides on a CHANGE the log reported, not on the list being
                            // re-fetched — so a poll that found nothing is silent, which is almost
                            // every poll.
                            LOG.log(
                                    java.util.logging.Level.INFO,
                                    "comms: {0} conversation(s) changed",
                                    touched.size());
                            io.github.stoicswe.eyeandsickle.client.sound.Sfx.message();
                            if (!fresh.isEmpty()) {
                                convos.clear();
                                convos.addAll(fresh);
                                paint[0].run();
                            }
                            // ⚠ The open conversation is refreshed in place, so a message arriving
                            // in the one being read appears without the player clicking away and
                            // back. Anything else and the transcript is stale exactly when somebody
                            // is looking at it.
                            if (!selected[0].isBlank() && touched.contains(selected[0])) {
                                String open = selected[0];
                                convos.stream()
                                        .filter(c -> c.id().equals(open))
                                        .findFirst()
                                        .ifPresent(convo -> Thread.ofVirtual().start(() -> {
                                            var history = chat.history(open, HISTORY_LIMIT);
                                            Platform.runLater(() -> showHistory(chat, convo, history, transcript));
                                        }));
                            }
                        });
                    });
                });
        Views.releaseOnDetach(convoList, clock);
    }

    /**
     * The floor on the poll interval, whatever the player sets.
     *
     * <p>⚠ This is somebody else's service and the player's own allowance. Bluesky's docs warn that
     * clients polling every few seconds consume it, so the slider cannot be dragged into doing that.
     */
    public static final int MIN_SYNC_SECONDS = 15;

    /**
     * "Powered by Bluesky", with the butterfly, across the top of the tab.
     *
     * <h2>Why it is here at all</h2>
     *
     * Everything below it is somebody else's service and somebody else's data, reached through the
     * player's own account. A tab inside a game window that silently showed real conversations would
     * leave a reasonable person unsure whose messages these are and where they came from — saying so
     * is both the courteous thing and the honest one.
     *
     * <h2>⚠ The mark is drawn HERE, not fetched, and it is not the official logo</h2>
     *
     * {@code ui/widgets/SocialMark} owns the path — authored in this repository, shared with the
     * credits page rather than copied, because this client <b>bundles no third-party artwork and
     * downloads nothing at run time</b>. §9's ban on icon sets is not in play: this is one quoted
     * mark drawn as a path, not an icon vocabulary.
     *
     * <h2>⚠ Quiet, and on the NEUTRAL ramp</h2>
     *
     * §2.1 spends amber on cycles doing work and rations alarm to loss; an attribution is neither,
     * and colouring it in Bluesky's own blue would be the semantic colour system §2.1 bans arriving
     * through the back door — a blue that means nothing sitting beside {@code gain}, {@code warn} and
     * {@code alarm} that all mean something. It takes {@code -es-dim-1}, which {@code ContrastTest}
     * measures in all eight palettes and which inverts correctly on uOS Classic.
     */
    private static Region attribution(java.util.function.BooleanSupplier syncing, VBox owner) {
        Region mark = io.github.stoicswe.eyeandsickle.client.ui.widgets.SocialMark.BLUESKY.node(
                UiTokens.SOCIAL_MARK, "es-attribution-mark");
        // ⚠ The mark turns only while a sync is actually in flight — it is a progress indicator, not
        // decoration, which is what earns a spring a place at all given §5. Released with the pane,
        // because a Pulse subscription outlives the node that made it.
        Views.releaseOnDetach(owner, io.github.stoicswe.eyeandsickle.client.ui.widgets.SyncSpin.spin(mark, syncing));

        Label label = new Label(Views.t("ui.direct.powered-by", "Powered by Bluesky"));
        label.getStyleClass().add("es-attribution");

        HBox row = new HBox(UiTokens.SPACE_2, mark, label);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("es-attribution-row");
        // ⚠ One accessible label on the ROW, and the children are hidden from the tree. A screen
        // reader cannot see a butterfly, and left alone it would announce an unlabelled graphic
        // followed by the text — the same reasoning Credits records for its handles.
        row.setAccessibleText(Views.t("ui.direct.powered-by", "Powered by Bluesky"));
        mark.setAccessibleText("");
        label.setAccessibleText("");
        return row;
    }

    /** What the pane should show before any network call has finished. */
    enum State {
        /** There is genuinely no account: no handle, or no credential in the OS store. */
        NO_ACCOUNT,
        /** An account is configured. Sign-in and the first fetch happen on a background thread. */
        CONNECTING
    }

    /**
     * Which of the two it is — <b>pure, and package-private so it can be tested without a toolkit</b>.
     *
     * <h2>⚠ THIS IS THE BUG. `signedIn()` MUST NOT BE PART OF THE ANSWER.</h2>
     *
     * Sign-in is a network round trip and cannot run on the FX thread, so it happens on the
     * background thread {@code create} starts. {@code create} runs in the instant <em>before</em>
     * that — so asking {@code signedIn()} here returns false for a perfectly good account, every
     * time, and the pane renders "no account connected" permanently. That is exactly what shipped:
     * a connected handle, a correct app password with DM access, and a tab that said there was no
     * account.
     *
     * <p>It is a seam for the reason {@code SecurityCenterView.latestOf} and
     * {@code Anchoring.horizontal} are: the rule lived inside a method that needed a live scene, so
     * the only way to check it was to run the client and look. Extracted, it is four lines and a
     * test that fails against the old version.
     *
     * <p>⚠ <b>Only a null client means "no account."</b> {@code EyeAndSickleClient.blueskyPane}
     * returns null when there is no handle or no credential in the store, which is the one place
     * that question can actually be answered.
     */
    static State state(BlueskyChat chat) {
        return chat == null ? State.NO_ACCOUNT : State.CONNECTING;
    }

    private static Region row(
            BlueskyChat chat,
            BlueskyChat.Convo convo,
            String[] selected,
            Runnable[] paint,
            VBox transcript) {
        VBox box = new VBox(1);
        box.getStyleClass().add("es-comms-row");
        if (convo.id().equals(selected[0])) {
            box.getStyleClass().add("es-comms-row-on");
        }

        Label title = new Label(convo.title(chat.selfDid()));
        // Unread is weight, the same as the engine inbox's — never a colour (§2.1).
        title.getStyleClass().add(convo.unreadCount() > 0 ? "es-comms-subject-unread" : "es-comms-subject");
        title.setWrapText(true);

        // ⚠ A GROUP says so, because "3 people" in the title is easy to miss and the difference
        // changes what somebody is willing to type.
        String meta = convo.group() ? Views.t("ui.direct.group", "group") : "";
        if (convo.request()) {
            // ⚠ Bluesky's own consent state, surfaced. A pending request that looked like an
            // ordinary conversation would leave the player wondering why replies went nowhere.
            meta = meta.isEmpty() ? Views.t("ui.direct.request", "request") : meta + " · request";
        }
        if (convo.unreadCount() > 0) {
            meta = meta.isEmpty() ? convo.unreadCount() + " unread" : meta + " · " + convo.unreadCount() + " unread";
        }
        box.getChildren().add(title);
        if (!meta.isEmpty()) {
            Label metaLabel = new Label(meta);
            metaLabel.getStyleClass().add(convo.request() ? "es-comms-offer" : "es-comms-meta");
            box.getChildren().add(metaLabel);
        }
        if (!convo.lastMessage().isBlank()) {
            Label preview = new Label(convo.lastMessage());
            preview.getStyleClass().add("es-comms-meta");
            preview.setWrapText(true);
            box.getChildren().add(preview);
        }

        box.setOnMouseClicked(e -> {
            selected[0] = convo.id();
            paint[0].run();
            transcript.getChildren().setAll(Views.secondary(Views.t("ui.direct.loading-history", "Loading…")));
            Thread.ofVirtual().start(() -> {
                List<BlueskyChat.Message> history = chat.history(convo.id(), HISTORY_LIMIT);
                Platform.runLater(() -> showHistory(chat, convo, history, transcript));
            });
        });
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(box);
        box.setAccessibleText(convo.title(chat.selfDid()) + (convo.request() ? ", message request" : ""));
        return box;
    }

    private static void showHistory(
            BlueskyChat chat, BlueskyChat.Convo convo, List<BlueskyChat.Message> history, VBox transcript) {
        transcript.getChildren().clear();

        Label heading = new Label(convo.title(chat.selfDid()));
        heading.getStyleClass().add("es-comms-read-subject");
        heading.setWrapText(true);
        transcript.getChildren().addAll(heading, new Separator());

        if (history.isEmpty()) {
            transcript.getChildren()
                    .add(Views.secondary(Views.t("ui.direct.no-history", "No messages, or none could be read.")));
            return;
        }

        // ⚠ A sender is only a DID on the wire. The name lives in the convo's members, so it has to
        // be resolved by matching — without this every line is prefixed with `did:plc:…`.
        Map<String, String> names = convo.members().stream()
                .collect(java.util.stream.Collectors.toMap(
                        BlueskyChat.Member::did, BlueskyChat.Member::name, (a, b) -> a));

        for (BlueskyChat.Message message : history) {
            boolean mine = message.senderDid().equals(chat.selfDid());
            String who = mine
                    ? Views.t("ui.direct.you", "you")
                    : names.getOrDefault(message.senderDid(), message.senderDid());

            Label meta = new Label(who + "  ·  " + WHEN.format(message.sentAt()));
            meta.getStyleClass().add("es-comms-meta");

            // ⚠ A deleted message has NO text on the wire. Rendering it as an empty line is
            // indistinguishable from a bug, so it says what it is.
            Label body = new Label(
                    message.deleted() ? Views.t("ui.direct.deleted", "(message deleted)") : message.text());
            body.setWrapText(true);
            body.getStyleClass().add(message.deleted() ? "es-comms-meta" : "es-comms-body");

            VBox line = new VBox(1, meta, body);
            transcript.getChildren().add(line);
        }
    }
}
