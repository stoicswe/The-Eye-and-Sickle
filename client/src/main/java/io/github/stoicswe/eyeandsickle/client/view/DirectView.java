package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.bsky.BlueskyChat;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
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

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(DirectView.class.getName());

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
    public static Region create(BlueskyChat chat, String handle, int syncSeconds, Alerts alerts) {
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

        // ⚠ THE COMPOSER SITS OUTSIDE THE SCROLLING TRANSCRIPT, and that is not only so it stays put.
        // The poll rebuilds `transcript` whenever the open conversation changes, and a TextField
        // inside a container that is repainted on a clock is torn down MID-KEYSTROKE — UI-7, which
        // `ReconView` records the hard way. Keeping it in a sibling makes the rule structural rather
        // than something the refresh path has to remember.
        Composer composer = new Composer();
        VBox readerColumn = new VBox(readerPane, composer.node());
        // ⚠ Vgrow belongs on the child of the VBOX. Setting it on a child of the HBox is accepted and
        // silently does nothing — the Security Center shipped exactly that and stopped short of the
        // window's bottom edge.
        VBox.setVgrow(readerPane, Priority.ALWAYS);
        // ⚠ Zero, or the row demands both columns before distributing anything and the transcript
        // runs off the panel — the same failure CommsView and AnonShareView both record.
        readerColumn.setMinWidth(0);

        HBox split = new HBox(listPane, new Separator(Orientation.VERTICAL), readerColumn);
        HBox.setHgrow(readerColumn, Priority.ALWAYS);
        split.setFillHeight(true);

        // ⚠ LAY OUT BEFORE SCROLLING. A ScrollPane clamps vvalue against a content height it does not
        // know until the new bubbles have been measured, so setting it in the same frame they are
        // added scrolls to the end of the OLD content — the newest message, which is the only one
        // anybody is looking for, lands just off the bottom. `AttentionLedger` records the same fix.
        Runnable scrollToEnd = () -> scrollToEnd((ScrollPane) readerPane);

        // ⚠ A flag rather than a method on the chat client: "is a sync running" is this pane's own
        // state, and the client is shared with whatever else asks it questions.
        boolean[] syncing = {true};

        VBox root = new VBox();
        root.getChildren().addAll(attribution(() -> syncing[0], root), split);
        VBox.setVgrow(split, Priority.ALWAYS);

        if (state(chat) == State.NO_ACCOUNT) {
            transcript
                    .getChildren()
                    .setAll(Views.wrapped(Views.t(
                            "ui.direct.not-connected", "No Bluesky account is connected. Settings → Bluesky.")));
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
            LOG.info("comms: ALO Messenger opening, signing in");
            var failure = chat.ensureSignedIn();
            if (failure.isPresent()) {
                LOG.log(java.util.logging.Level.WARNING, "comms: ALO Messenger cannot sign in: {0}", failure.get());
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
                    transcript
                            .getChildren()
                            .setAll(Views.wrapped(
                                    why.isBlank()
                                            ? Views.t("ui.direct.empty", "No conversations on this account yet.")
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
                                .add(row(chat, convo, selected, paint, transcript, composer, scrollToEnd));
                    }
                };
                paint[0].run();
                startPolling(
                        chat,
                        syncing,
                        convos,
                        selected,
                        paint,
                        convoList,
                        transcript,
                        syncSeconds,
                        scrollToEnd,
                        alerts);
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
            int syncSeconds,
            Runnable scrollToEnd,
            Alerts alerts) {
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
                        List<BlueskyChat.Convo> fresh = touched.isEmpty() ? List.of() : chat.conversations(CONVO_LIMIT);
                        Platform.runLater(() -> {
                            syncing[0] = false;
                            if (touched.isEmpty()) {
                                return;
                            }
                            // ⚠ The chime rides on a CHANGE the log reported, not on the list being
                            // re-fetched — so a poll that found nothing is silent, which is almost
                            // every poll.
                            LOG.log(java.util.logging.Level.INFO, "comms: {0} conversation(s) changed", touched.size());
                            announce(chat, alerts, touched, fresh, selected[0]);
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
                                            Platform.runLater(() -> {
                                                showHistory(chat, convo, history, transcript);
                                                scrollToEnd.run();
                                            });
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
            VBox transcript,
            Composer composer,
            Runnable scrollToEnd) {
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
            // ⚠ Armed on selection, and the sink appends the SERVER's copy of the message rather than
            // re-fetching the whole history — `sendMessage` returns the messageView it recorded, so
            // the id and the timestamp are the real ones and it costs no second round trip.
            composer.armFor(chat, convo.id(), sent -> {
                transcript.getChildren().add(bubble(chat.selfDid(), names(convo), sent));
                scrollToEnd.run();
            });
            Thread.ofVirtual().start(() -> {
                List<BlueskyChat.Message> history = chat.history(convo.id(), HISTORY_LIMIT);
                Platform.runLater(() -> {
                    showHistory(chat, convo, history, transcript);
                    scrollToEnd.run();
                });
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
            transcript
                    .getChildren()
                    .add(Views.secondary(Views.t("ui.direct.no-history", "No messages yet — say something.")));
            return;
        }

        for (BlueskyChat.Message message : history) {
            transcript.getChildren().add(bubble(chat.selfDid(), names(convo), message));
        }
    }

    /**
     * Puts the transcript at the newest message.
     *
     * <h2>⚠ OPENING A CONVERSATION MUST LAND AT THE END, NOT THE TOP</h2>
     *
     * A chat is read downwards and the interesting message is the last one. Landing at the top means
     * every conversation opens on something said weeks ago and the player has to scroll to find out
     * what they were notified about.
     *
     * <h2>⚠ LAY OUT THE SCROLL PANE, NOT JUST ITS CONTENT — and that distinction is the whole bug</h2>
     *
     * {@code vvalue} is clamped against the pane's own idea of how tall its content is, and the pane
     * only learns that during <b>its</b> layout pass. Laying out the transcript alone updates the
     * bubbles and leaves the pane still measuring the previous conversation, so opening a long
     * history after a short one lands part-way down — which reads as scrolling to a random place
     * rather than as a stale measurement, and gets worse the more the two lengths differ.
     *
     * <p>⚠ Synchronous, never {@code Platform.runLater}: a deferred call is a hope that one layout
     * pass has happened, it fires too early on a slow first paint, and it never runs at all inside a
     * synchronous {@code Scene.snapshot} — so a render harness would photograph a transcript sitting
     * at the top and report the behaviour as absent.
     *
     * <p>Package-private so a render can drive the real thing rather than a copy of it.
     */
    static void scrollToEnd(ScrollPane pane) {
        pane.applyCss();
        pane.layout();
        pane.setVvalue(1.0d);
    }

    /**
     * What the deck can do about an arriving message. Supplied by {@code EyeAndSickleClient}.
     *
     * <p>⚠ An interface rather than a {@code DeckShell} handle: this view has never known what a deck
     * is, and taking one would let it reach the window manager, the rail and every session.
     */
    public interface Alerts {

        /** Whether the COMS window is the focused one right now. */
        boolean commsFocused();

        /** Slides a preview into the notice stack. */
        void preview(String who, String snippet);

        /** A no-op set, for a pane built without a deck around it. */
        Alerts NONE = new Alerts() {
            @Override
            public boolean commsFocused() {
                return false;
            }

            @Override
            public void preview(String who, String snippet) {}
        };
    }

    /**
     * Chimes and previews an arriving message — unless the player is already looking at it.
     *
     * <h2>⚠ THE SUPPRESSION IS NARROW ON PURPOSE: FOCUSED <em>AND</em> OPEN</h2>
     *
     * Silence is only correct when the message is genuinely on screen, which needs both halves. COMS
     * focused with a <em>different</em> conversation open is exactly the case where a preview is most
     * useful — somebody is in the app and would otherwise miss it — and the right conversation open
     * behind another window is a message the player cannot see. Either half alone gets one of those
     * two backwards.
     *
     * <h2>⚠ ONE CHIME PER POLL, NOT PER CONVERSATION</h2>
     *
     * A poll can report several at once — the first one after a long absence usually does — and a
     * chime each would be a burst of identical sounds. The previews still stack; only the sound is
     * collapsed.
     *
     * <h2>⚠ NOTHING IS ANNOUNCED FOR THE PLAYER'S OWN MESSAGES</h2>
     *
     * {@code logCreateMessage} fires for every message in a conversation the account is in, whoever
     * wrote it — that is what keeps this client in step with the player's phone, and it means a reply
     * they typed elsewhere arrives here as a change. Chiming for it would be the app notifying
     * somebody about themselves.
     */
    private static void announce(
            BlueskyChat chat,
            Alerts alerts,
            java.util.Set<String> touched,
            List<BlueskyChat.Convo> fresh,
            String openConvoId) {
        boolean focused = alerts.commsFocused();
        boolean announced = false;
        for (BlueskyChat.Convo convo : fresh) {
            if (!touched.contains(convo.id())) {
                continue;
            }
            if (convo.lastSenderDid().equals(chat.selfDid())) {
                continue;
            }
            if (focused && convo.id().equals(openConvoId)) {
                // On screen already. The transcript refreshes in place either way.
                continue;
            }
            alerts.preview(convo.title(chat.selfDid()), snippet(convo.lastMessage()));
            announced = true;
        }
        if (announced) {
            io.github.stoicswe.eyeandsickle.client.sound.Audio.shared()
                    .play(io.github.stoicswe.eyeandsickle.client.sound.Sfx.MESSAGE);
        }
    }

    /**
     * A preview short enough for a toast.
     *
     * <p>⚠ Cut on a word boundary where there is one, and newlines flattened — a notice is one line,
     * and a message pasted with line breaks in it would otherwise make the stack jump in height.
     */
    static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return Views.t("ui.direct.snippet-empty", "(no preview)");
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.length() <= SNIPPET) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', SNIPPET);
        return flat.substring(0, cut > SNIPPET / 2 ? cut : SNIPPET).strip() + "…";
    }

    /** Long enough to tell one message from another, short enough not to be the message. */
    private static final int SNIPPET = 60;

    /**
     * ⚠ A sender is only a DID on the wire. The name lives in the convo's members, so it has to be
     * resolved by matching — without this every line is prefixed with {@code did:plc:…}.
     */
    private static Map<String, String> names(BlueskyChat.Convo convo) {
        return convo.members().stream()
                .collect(java.util.stream.Collectors.toMap(
                        BlueskyChat.Member::did, BlueskyChat.Member::name, (a, b) -> a));
    }

    /**
     * One message, as a bubble on its own side of the transcript.
     *
     * <h2>⚠ ALIGNMENT IS THE PRIMARY CUE AND COLOUR IS THE SECOND (§4.4)</h2>
     *
     * The fill says whose it is and so does the side, and the sender's name stays on every bubble on
     * top of that. Anyone who cannot separate the two fills — greyscale, a colour vision difference,
     * a screenshot through a lossy codec — still reads the conversation correctly. That redundancy is
     * what makes spending the accent here defensible at all.
     *
     * <h2>⚠ THE BUBBLE IS WRAPPED IN AN {@code HBox}, WHICH IS WHAT MAKES IT SHRINK-WRAP</h2>
     *
     * A {@code VBox} stretches its children to the full width, so a bubble added straight to the
     * transcript would be a full-width band whatever its alignment said — the colour would read as a
     * section background rather than as a message. The row is what gives it a side to sit on, and
     * {@code USE_PREF_SIZE} on the bubble is what stops the row stretching it back out again.
     */
    static Region bubble(String selfDid, Map<String, String> names, BlueskyChat.Message message) {
        boolean mine = message.senderDid().equals(selfDid);
        String who =
                mine ? Views.t("ui.direct.you", "you") : names.getOrDefault(message.senderDid(), message.senderDid());

        Label meta = new Label(who + "  ·  " + WHEN.format(message.sentAt()));
        meta.getStyleClass().add("es-dm-meta");

        // ⚠ A deleted message has NO text on the wire. Rendering it as an empty line is
        // indistinguishable from a bug, so it says what it is.
        Label body = new Label(message.deleted() ? Views.t("ui.direct.deleted", "(message deleted)") : message.text());
        body.setWrapText(true);
        body.getStyleClass().add("es-dm-text");

        VBox bubble = new VBox(1, meta, body);
        bubble.getStyleClass().addAll("es-dm-bubble", mine ? "es-dm-mine" : "es-dm-them");
        // ⚠ A ceiling, not a width. A bubble that ran the full width of a maximised window would be
        // unreadable prose and would stop looking like a message; one with a fixed width would leave
        // "ok" sitting in a large empty box.
        bubble.setMaxWidth(BUBBLE_MAX_WIDTH);

        HBox row = new HBox(bubble);
        row.setAlignment(mine ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
        // ⚠ Without this the HBox grows the bubble to the row's height — alignment says where a child
        // sits, `fillHeight` says whether it was handed a height to sit in. The rig monitor's core
        // cutaway records the same trap.
        row.setFillHeight(false);
        // ⚠ ONE accessibleText on the ROW, because a reader announcing "you", the timestamp and the
        // message as three separate nodes turns a conversation into a list of fragments.
        row.setAccessibleText(who + ", " + (message.deleted() ? "message deleted" : message.text()));
        return row;
    }

    /** Prose stops being readable past about this, and a message is prose. */
    private static final double BUBBLE_MAX_WIDTH = 420;

    /**
     * The composer — where the player writes a reply.
     *
     * <h2>⚠ IT LIVES OUTSIDE THE TRANSCRIPT AND THAT IS STRUCTURAL, NOT COSMETIC</h2>
     *
     * The transcript is rebuilt whenever the poll reports a change in the open conversation. A
     * {@code TextField} inside a container that is repainted on a clock is destroyed <b>mid-keystroke</b>
     * — that is <b>UI-7</b>, which {@code ReconView} records from having shipped it. Keeping the field
     * in a sibling node means the refresh path cannot take it away by accident, rather than having to
     * remember not to.
     *
     * <h2>⚠ THE TEXT IS CLEARED ON SUCCESS AND ONLY ON SUCCESS</h2>
     *
     * A failed send must leave what the player wrote exactly where it is. Clearing on the attempt
     * loses somebody's words to a network error they did not cause and cannot retry, which is the
     * least forgivable thing a message box can do.
     */
    static final class Composer {

        /**
         * ⚠ A {@link TextArea}, not a {@code TextField}, and none of the three things asked for is
         * possible on the latter: it is single-line by construction, it cannot wrap, and it has no
         * notion of growing. The cost is that Enter has to be taken back from it (see the filter
         * below), because a text area's default behaviour is to insert a newline.
         */
        private final TextArea field = new TextArea();

        /**
         * ⚠ An envelope and no text — so the LABEL has to live somewhere else, and both places are
         * required. {@code accessibleText} because a screen reader cannot see a drawn shape, and a
         * tooltip because neither can somebody who has not met this mark before. A button whose only
         * content is a picture is a button that has to say what it does twice over.
         */
        private final Button send = new Button();

        private final Label problem = new Label();
        private final VBox root;

        private BlueskyChat chat;
        private String convoId = "";
        private java.util.function.Consumer<BlueskyChat.Message> sink = message -> {};

        /** ⚠ Never two sends in flight: a double-tap would post the message twice. */
        private boolean sending;

        Composer() {
            field.setPromptText(Views.t("ui.direct.compose", "Write a message…"));
            field.getStyleClass().add("es-dm-input");
            field.setWrapText(true);
            field.setPrefRowCount(1);
            HBox.setHgrow(field, Priority.ALWAYS);
            // ⚠ Zero, or the field's computed preferred width holds the row open and the Send button
            // is pushed off the panel — the same failure AnonShare's nav row records, where JavaFX
            // ellipsised a control whose whole meaning is its word.
            field.setMinWidth(0);
            // ⚠ The height must come from prefRowCount and nothing else. A Control's own maximum is
            // not the unbounded value a Pane reports, but an HBox still fills a resizable child to
            // the row height — so without pinning both bounds to the preferred size the box would be
            // as tall as the Send button and the row growth would do nothing visible.
            field.setMinHeight(Region.USE_PREF_SIZE);
            field.setMaxHeight(Region.USE_PREF_SIZE);

            // ⚠ ENTER SENDS, SHIFT+ENTER INSERTS A NEWLINE — and it has to be an event FILTER.
            // A TextArea's own skin handles Enter and inserts the newline; a normal handler runs
            // after it, so the message would be sent AND a blank line left behind in a box that had
            // just been cleared. The filter runs first and consumes it, which is what takes the key
            // back. Shift+Enter is deliberately not consumed — it falls through to exactly that
            // default behaviour rather than re-implementing insertion by hand.
            field.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() != javafx.scene.input.KeyCode.ENTER) {
                    return;
                }
                if (event.isShiftDown()) {
                    return;
                }
                event.consume();
                submit();
            });
            // ⚠ Grow on every change, and on WIDTH too: the box wraps, so how many lines a message
            // occupies is a function of the column it is in. Growing only on text change leaves a
            // three-line message in a one-line box after somebody narrows the window.
            field.textProperty().addListener((observable, old, now) -> fitToContent());
            field.widthProperty().addListener((observable, old, now) -> fitToContent());

            send.setGraphic(io.github.stoicswe.eyeandsickle.client.ui.widgets.MailMark.node(MARK_SIZE, MARK_STROKE));
            send.getStyleClass().add("es-dm-send");
            // ⚠ BOTH, and neither is decoration. The mark is the entire control, so this is the only
            // place the word "Send" exists — and the tooltip names the key as well, because Enter is
            // how anybody will actually send and an icon cannot say so.
            send.setAccessibleText(Views.t("ui.direct.send", "Send"));
            send.setTooltip(new javafx.scene.control.Tooltip(Views.t("ui.direct.send-hint", "Send  ·  Enter")));
            send.setOnAction(e -> submit());
            send.setDefaultButton(false);

            problem.getStyleClass().add("es-comms-offer");
            problem.setWrapText(true);
            // ⚠ An empty label with padding is a permanent gap. Unmanaged when it has nothing to say,
            // so the composer is one row until something actually goes wrong.
            problem.managedProperty().bind(problem.textProperty().isNotEmpty());
            problem.visibleProperty().bind(problem.textProperty().isNotEmpty());

            HBox row = new HBox(UiTokens.SPACE_2, field, send);
            // ⚠ BOTTOM, not centre. The box grows upward as somebody types, and a centred button
            // drifts up the row with it — the one control on the panel that should not move.
            row.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
            row.setFillHeight(false);
            root = new VBox(UiTokens.SPACE_1, problem, row);
            root.getStyleClass().add("es-dm-composer");
            disarm();
        }

        Region node() {
            return root;
        }

        /**
         * Grows the box to fit what has been typed, up to {@link #MAX_ROWS}, then lets it scroll.
         *
         * <h2>⚠ THE LINE COUNT IS MEASURED, NOT COUNTED</h2>
         *
         * The box wraps, so the number of lines a message occupies is not the number of {@code \n} in
         * it — a single long sentence is one logical line and five visual ones. Counting newlines
         * gives a box that stays one row tall while the text scrolls invisibly inside it, which is
         * the failure mode nobody notices until they have typed a paragraph. So the height comes from
         * the laid-out {@code .text} node, divided by one line's height.
         *
         * <p>⚠ It degrades to counting newlines rather than throwing. Before the first CSS pass the
         * lookup returns null and the font has no size, and this runs from a text listener that can
         * fire at any point in a window's life. A box that is briefly one row short is a much smaller
         * problem than a listener that throws on every keystroke.
         */
        private void fitToContent() {
            int rows = rowsFor(field.getText(), field.getWidth() - TEXT_INSET, field.getFont());
            field.setPrefRowCount(Math.max(1, Math.min(MAX_ROWS, rows)));
        }

        /**
         * How many lines {@code text} occupies when wrapped into {@code wrapWidth}.
         *
         * <h2>⚠ THE FIRST VERSION READ THE CONTROL'S OWN LAID-OUT TEXT NODE, AND IT NEVER GREW</h2>
         *
         * That looks like the obvious implementation: look up {@code .text} inside the TextArea and
         * divide its height by a line. It reports <b>one row for a 351-character message</b>, because
         * it runs from a text listener — the skin has not re-laid-out the new string yet, so the node
         * still carries the <em>previous</em> height. Measured, and it is not intermittent: it is
         * always exactly one layout pass stale, so the box grows one line late forever and appears
         * simply not to work.
         *
         * <p>⚠ Deferring it with {@code Platform.runLater} is the other tempting fix and is worse:
         * <b>no queued runnable executes during a synchronous {@code Scene.snapshot}</b>, so every
         * render harness would photograph a one-line box and report the feature as absent — the
         * failure mode this repo keeps rediscovering.
         *
         * <p>So the text is measured <b>directly</b>, with a standalone {@code Text} node that owns
         * its own wrapping. It depends on nothing but its three arguments, computes without a Scene,
         * and is therefore a pure function this can be tested as.
         *
         * @param wrapWidth the width available to the text; a non-positive value means not yet laid
         *     out, and the answer falls back to counting hard line breaks
         */
        static int rowsFor(String text, double wrapWidth, javafx.scene.text.Font font) {
            if (text == null || text.isEmpty()) {
                return 1;
            }
            int hardLines = (int) text.chars().filter(c -> c == '\n').count() + 1;
            if (font == null || wrapWidth <= 0) {
                // Before the first layout there is no width to wrap into. Undercounts a wrapped
                // message, never overcounts, and the next width change corrects it.
                return hardLines;
            }
            javafx.scene.text.Text probe = new javafx.scene.text.Text(text);
            probe.setFont(font);
            probe.setWrappingWidth(wrapWidth);
            double height = probe.getLayoutBounds().getHeight();

            javafx.scene.text.Text line = new javafx.scene.text.Text("X");
            line.setFont(font);
            double lineHeight = line.getLayoutBounds().getHeight();
            if (lineHeight <= 0) {
                return hardLines;
            }
            // ⚠ round, not ceil: the wrapped node's height is already a whole number of lines, and
            // ceil turns a rounding residue of a fraction of a pixel into a phantom extra row.
            return Math.max(hardLines, (int) Math.round(height / lineHeight));
        }

        /**
         * What the box's width loses to its own frame before the text starts.
         *
         * <p>Border, content padding and the room the vertical scrollbar takes once the message is
         * past {@link #MAX_ROWS}. ⚠ Erring generous is the safe direction: over-reserving wraps a
         * line slightly early, under-reserving reports fewer rows than are really there and clips the
         * last one.
         */
        private static final double TEXT_INSET = 22;

        /**
         * The envelope's size and hairline.
         *
         * <p>⚠ Sized against the composer's own text rather than picked: an icon noticeably larger
         * than the line it sits beside stops reading as a control and starts reading as an
         * illustration. 14px matches the box's line height closely enough to sit level with it.
         */
        private static final double MARK_SIZE = 14;

        private static final double MARK_STROKE = 1.2;

        /**
         * How far the box grows before it starts scrolling.
         *
         * <p>Six lines is enough for a real paragraph while still leaving the transcript the majority
         * of the panel — a composer that grew without limit would push the conversation it is a reply
         * to off the top of the window.
         */
        private static final int MAX_ROWS = 6;

        /** Points the composer at a conversation. Called when a row is picked. */
        void armFor(BlueskyChat chat, String convoId, java.util.function.Consumer<BlueskyChat.Message> sink) {
            this.chat = chat;
            this.convoId = convoId;
            this.sink = sink;
            problem.setText("");
            field.setDisable(false);
            send.setDisable(false);
        }

        /**
         * ⚠ Disabled until a conversation is picked, rather than hidden.
         *
         * <p>A composer that appears only after a click is a feature the player has to discover;
         * one that is visibly there and inert says "pick somebody first" without a sentence.
         */
        void disarm() {
            field.setDisable(true);
            send.setDisable(true);
        }

        private void submit() {
            String text = field.getText();
            if (sending || chat == null || convoId.isBlank() || text == null || text.isBlank()) {
                return;
            }
            sending = true;
            field.setDisable(true);
            send.setDisable(true);
            problem.setText("");
            String convo = convoId;
            // ⚠ A VIRTUAL thread. Posting inline freezes the deck for however long somebody else's
            // PDS takes to answer, which is not a number this client gets to bound.
            Thread.ofVirtual().start(() -> {
                var sent = chat.send(convo, text);
                String why = chat.lastError();
                Platform.runLater(() -> {
                    sending = false;
                    field.setDisable(false);
                    send.setDisable(false);
                    if (sent.isPresent()) {
                        field.clear();
                        sink.accept(sent.get());
                    } else {
                        LOG.warning("comms: a message could not be sent");
                        problem.setText(
                                why.isBlank() ? Views.t("ui.direct.send-failed", "That message did not send.") : why);
                    }
                    // ⚠ Focus goes back to the field either way — after a failure the player is about
                    // to try again, and after a success they are usually still typing.
                    field.requestFocus();
                });
            });
        }
    }
}
